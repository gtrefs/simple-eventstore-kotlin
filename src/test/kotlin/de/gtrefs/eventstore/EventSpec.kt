package de.gtrefs.eventstore

import com.winterbe.expekt.should
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import kotlin.reflect.companionObjectInstance

class EventSpec : Spek({
    describe("the event"){
        on("creation"){
            it("should fullfill the serialization contract"){
                val event = TestEvent("test")

                val restored = event.serialize().deserialize()

                restored.should.equal(event)
            }
            it("companion object should be of type DomainEventFactory"){
                val companion = TestEvent::class.companionObjectInstance
                companion.should.be.instanceof(DomainEventFactory::class.java)
            }

        }
    }
})

data class TestEvent(val name: String): DomainEvent {
    override fun serialize(): SerializedDomainEvent = SerializedDomainEvent(TestEvent::class.java.name,
            mapOf("name" to name),
            emptyMap())
    companion object Factory: DomainEventFactory {
        override fun deserialize(event: SerializedDomainEvent): TestEvent = TestEvent(event.meta["name"] as String)
    }
}