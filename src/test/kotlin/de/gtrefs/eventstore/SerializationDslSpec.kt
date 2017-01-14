package de.gtrefs.eventstore

import com.winterbe.expekt.should
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.memberProperties
import kotlin.reflect.primaryConstructor

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

            it("should serialize empty constructor to empty payload"){
                val serialization = serialize<EmptyEvent>()

                val serialized = serialization(EmptyEvent())

                serialized.payload.should.equal(emptyMap())
            }

            it("should serialize Domain Event"){
                val processed = colorChanged.serialize().deserialize()

                processed.should.equal(colorChanged)
            }

        }
    }
})

fun <E: DomainEvent> serialize(): (E) -> SerializedDomainEvent = serialize<E> {}

fun <E : DomainEvent> serialize(init: Serialization<E>.() -> Unit): (E) -> SerializedDomainEvent = {
    val serialization = Serialization<E>()
    serialization.init()
    serialization(it)
}

class Serialization<E: DomainEvent> {

    private var type: ((E) -> String)? = null
    private var initMeta: ((PairContainer) -> (E) -> PairContainer)? = null
    private var initPayload: ((PairContainer) -> (E) -> PairContainer)? = null

    fun type(type: (E) -> String): Unit {
        this.type = type
    }

    fun  meta(init: PairContainer.(E) -> Unit): Unit {
        initMeta = collectPairs(init)
    }

    fun  payload(init: PairContainer.(E) -> Unit): Unit {
        initPayload = collectPairs(init)
    }

    private fun collectPairs(init: PairContainer.(E) -> Unit): (PairContainer) -> (E) -> PairContainer =
        {container -> {event ->
            container.init(event)
            container
        }}

    operator fun invoke(event: E): SerializedDomainEvent {
        val type = this.type?.invoke(event) ?: event.javaClass.name
        val meta = initMeta?.invoke(Meta())?.invoke(event)?.pairs?.toMap() ?: emptyMap()
        val payload = initPayload?.invoke(Payload())?.invoke(event)?.pairs?.toMap() ?: pairConstructorParameters(event)

        return SerializedDomainEvent(type, meta, payload)
    }

    private fun pairConstructorParameters(event: E): Map<String, Any> {
        val parameters = event.javaClass.kotlin.primaryConstructor?.parameters?.map { it.name } ?: emptyList()
        val memberProperties = event.javaClass.kotlin.memberProperties

        return memberProperties.filter { parameters.contains(it.name) }.map {
            it.name to it.get(event)!!
        }.toMap()
    }

    class Payload : PairContainer()

    class Meta : PairContainer()

    abstract class PairContainer {
        val pairs = arrayListOf<Pair<String, Any>>()

        infix fun String.with(that: Any): Unit {
            pairs.add(this to that)
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
    override fun serialize(): SerializableDomainEvent {
        throw NotImplementedError()
    }
}
