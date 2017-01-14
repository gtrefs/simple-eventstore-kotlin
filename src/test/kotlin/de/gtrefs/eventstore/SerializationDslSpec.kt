package de.gtrefs.eventstore

import com.winterbe.expekt.should
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.time.LocalDateTime
import java.time.ZoneOffset

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
                        +("timeStamp" to it.timeStamp)
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
                        +("oldColor" to it.oldColor)
                        +("newColor" to it.newColor)
                    }
                }

                println(serialization(colorChanged))
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
        val payload = initPayload?.invoke(Payload())?.invoke(event)?.pairs?.toMap() ?: emptyMap()

        return SerializedDomainEvent(type, meta, payload)
    }

    class Payload : PairContainer()

    class Meta : PairContainer()

    abstract class PairContainer {
        val pairs = arrayListOf<Pair<String, Any>>()
        operator fun Pair<String, Any>.unaryPlus():Unit {
            pairs.add(this)
        }
    }
}

private val serialization = serialize<ColorChangedEvent> {
    meta {
        +("time" to it.timeStamp)
    }
    payload {
        +("oldColor" to it.oldColor)
        +("newColor" to it.newColor)
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
