package de.gtrefs.eventstore

import com.winterbe.expekt.should
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.reflect.memberProperties
import kotlin.reflect.primaryConstructor
import kotlin.test.assertFailsWith

class SerializationDslSpec : Spek({
    describe("Serialization DSL"){
        on("description"){
            val test: TestEvent = TestEvent("test")
            val now = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
            val colorChanged = ColorChangedEvent(now, Color(120,120,120,100), Color(120,120,120,50))
            it("should default the type to the java class name"){
                val serialization = serialize<TestEvent>()

                val serialized = serialization(test)

                serialized.type.should.equal(TestEvent::class.java.name)
            }

            it("should provide means to define the type"){
                val serialization = serialize<TestEvent> {
                    type { "type" }
                }

                val serialized = serialization(test)

                serialized.type.should.equal("type")
            }

            it("should be type safe"){
                val serialization = serialize<TestEvent> {
                    type(TestEvent::name)
                }

                serialization(test).type.should.equal(test.name)
            }

            it("should provide means to define meta information"){
                val serialization = serialize<ColorChangedEvent> {
                    meta {
                        "timeStamp" with it.timeStamp
                    }
                }

                serialization(colorChanged).meta.should.equal(mapOf("timeStamp" to colorChanged.timeStamp))
            }

            it("should default meta to an empty map"){
                serialize<ColorChangedEvent>()(colorChanged).meta.should.equal(emptyMap())
            }

            it("should provide means to define the payload"){
                val serialization = serialize<ColorChangedEvent> {
                    payload {
                        "oldColor" with it.oldColor
                        "newColor" with it.newColor
                    }
                }

                val serialized = serialization(colorChanged)

                serialized.payload["oldColor"].should.equal(colorChanged.oldColor)
                serialized.payload["newColor"].should.equal(colorChanged.newColor)
            }

            it("should deserialize all constructor arguments as payload by default"){
                val serialization = serialize<ColorChangedEvent>()

                val serialized = serialization(colorChanged)

                serialized.payload["oldColor"].should.equal(colorChanged.oldColor)
                serialized.payload["newColor"].should.equal(colorChanged.newColor)
                serialized.payload["timeStamp"].should.equal(colorChanged.timeStamp)
            }

            it("should not serialize constructor arguments which are excluded"){
                val serialization = serialize<ColorChangedEvent>{
                    payload {
                       without("timeStamp")
                    }
                }

                val serialized = serialization(colorChanged)

                serialized.payload.keys == setOf("oldColor", "newColor")
            }

            it("should serialize empty constructor to empty payload"){
                val serialization = serialize<EmptyEvent>()

                val serialized = serialization(EmptyEvent())

                serialized.payload.should.equal(emptyMap())
            }

            it("should serialize Domain Event"){
                val processed = colorChanged.serialize().deserialize()

                processed.should.equal(colorChanged)
            }

            it("explicit parameters cannot be excluded"){
                val serialization = serialize<ColorChangedEvent>{
                    payload {
                        "timeStamp" with it.timeStamp
                        without("timeStamp")
                    }
                }

                assertFailsWith<IllegalArgumentException> {
                    serialization(colorChanged)
                }
            }

            it("excluded parameters cannot be added"){
                val serialization = serialize<ColorChangedEvent>{
                    payload {
                        without("timeStamp")
                        "timeStamp" with it.timeStamp
                    }
                }

                assertFailsWith<IllegalArgumentException> {
                    serialization(colorChanged)
                }
            }

        }
    }
})

fun <E: DomainEvent> serialize(): Serialization<E> = serialize {}

fun <E : DomainEvent> serialize(init: Serialization<E>.() -> Unit): Serialization<E> {
    val serialization = Serialization<E>()
    serialization.init()
    return serialization
}

class Serialization<E: DomainEvent> {

    private var type: ((E) -> String)? = null
    private var initMeta: ((ParameterContainer) -> (E) -> ParameterContainer)? = null
    private var initPayload: ((ParameterContainer) -> (E) -> ParameterContainer)? = null

    fun type(type: (E) -> String): Unit {
        this.type = type
    }

    fun  meta(init: ParameterContainer.(E) -> Unit): Unit {
        initMeta = collect(init)
    }

    fun  payload(init: ParameterContainer.(E) -> Unit): Unit {
        initPayload = collect(init)
    }

    private fun collect(init: ParameterContainer.(E) -> Unit): (ParameterContainer) -> (E) -> ParameterContainer =
        {container -> {event ->
            container.init(event)
            container
        }}

    operator fun invoke(event: E): SerializedDomainEvent =
            SerializedDomainEvent(typeOf(event), metaOf(event), payloadOf(event))

    private fun typeOf(event: E) = this.type?.invoke(event) ?: event.javaClass.name

    private fun metaOf(event: E) = parametersOf(event, init = initMeta)

    private fun payloadOf(event: E) = parametersOf(event, parametersByName(event), initPayload)

    private fun parametersByName(event: E): Map<String, Any> {
        val parameters = event.javaClass.kotlin.primaryConstructor?.parameters?.map { it.name } ?: emptyList()
        val properties = event.javaClass.kotlin.memberProperties

        return properties.filter { parameters.contains(it.name) }.map {
            it.name to it.get(event)!!
        }.toMap()
    }

    private fun parametersOf(event: E,
                             default: Map<String, Any> = emptyMap(),
                             init: ((ParameterContainer) -> (E) -> ParameterContainer)? = null): Map<String, Any> {

        val container = initContainer(event, init)
        val explicit = container.explicit.toMap()
        val exclude = container.exclude

        return when(Pair(explicit.isEmpty(), exclude.isEmpty())){
            Pair(true, true) -> default
            Pair(true, false) -> remove(from = default, keys = exclude)
            else -> explicit
        }
    }

    private fun initContainer(event: E, init: ((ParameterContainer) -> (E) -> ParameterContainer)?): ParameterContainer {
        val container = ParameterContainer()
        init?.invoke(container)?.invoke(event)
        return container
    }

    private fun remove(from: Map<String, Any>, keys: ArrayList<String>) = from.filterKeys { it !in keys }

    class ParameterContainer {
        internal val explicit = arrayListOf<Pair<String, Any>>()
        internal val exclude = arrayListOf<String>()

        infix fun String.with(that: Any): Unit {
            if(this in exclude){
                throw IllegalArgumentException("Cannot add parameter with name '${this}'. It was excluded before.")
            }
            explicit += this to that
        }

        fun without(parameter: String) {
            if(explicit.any { it.first == parameter }){
                throw IllegalArgumentException("Cannot exclude parameter '$parameter'. It was explicitly added before.")
            }
            exclude += parameter
        }
    }
}

private val serialization = serialize<ColorChangedEvent> {
    meta {
        "time" with it.timeStamp
    }

    payload {
        "oldColor" with it.oldColor
        "newColor" with it.newColor
    }
}

data class ColorChangedEvent(val timeStamp: Long, val oldColor: Color, val newColor: Color): DomainEvent {

    private val serialized by lazy {
        serialization(this)
    }

    override fun serialize(): SerializedDomainEvent = serialized

    companion object : DomainEventFactory {
        override fun deserialize(event: SerializedDomainEvent): DomainEvent =
            ColorChangedEvent(event.meta["time"] as Long, event.payload["oldColor"] as Color, event.payload["newColor"] as Color)
    }
}

data class Color(val red: Byte, val green: Byte, val blue: Byte, val alpha: Byte)

class EmptyEvent : DomainEvent {
    override fun serialize(): SerializedDomainEvent {
        throw NotImplementedError()
    }
}
