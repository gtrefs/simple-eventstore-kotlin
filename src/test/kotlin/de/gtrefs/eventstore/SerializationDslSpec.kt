package de.gtrefs.eventstore

import com.winterbe.expekt.should
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.time.LocalDateTime
import java.time.ZoneOffset
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
