package de.gtrefs.eventstore

import com.google.common.eventbus.EventBus
import com.google.common.eventbus.Subscribe
import com.google.common.io.Resources
import com.winterbe.expekt.should
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PipelinePhoneNumberSpec : Spek({
    describe("pipe and filter example from Implementing Domain Driven Design") {
        val file = Paths.get(Resources.getResource("phone-numbers.txt").toURI())
        val eventStore = EventStore(Storage.inMemory())
        val eventBus = EventBus()

        eventBus.register(PhoneNumberFinder(eventBus, text = "303"))
        eventBus.register(MatchedPhoneNumberCounter(eventBus))
        eventBus.register(PhoneNumberExecutive())
        eventBus.register(EventStoreListener(eventStore))

        on("filtering phone numbers for those which contain 303"){

            PhoneNumbersPublisher(eventBus).readPhoneNumbers(file)

            it("the event store should have 3 events"){
                eventStore.version().should.equal(Version(3))
            }

            it("87 phone numbers should be found"){
                val matchedPhoneNumbersEvents:List<MatchedPhoneNumbersCounted> = eventStore.project {
                    it.fold(listOf<MatchedPhoneNumbersCounted>(), { l,e -> when(e){
                        is MatchedPhoneNumbersCounted -> l + e
                        else -> l
                    }})
                }.join()

                matchedPhoneNumbersEvents.size.should.equal(1)
                matchedPhoneNumbersEvents[0].count.should.equal(74)
            }
        }
    }
})

class PhoneNumbersPublisher(val eventBus: EventBus){
    fun readPhoneNumbers(fileWithPhoneNumbers: Path){
        val numbers: List<String> = fileWithPhoneNumbers.toFile().readLines()
        eventBus.post(AllPhoneNumbersListed(numbers))
    }
}

data class AllPhoneNumbersListed(val numbers: List<String>) : DomainEvent {
    override fun serialize(): SerializableDomainEvent =
            SerializableDomainEvent(AllPhoneNumbersListed::class.java.name, payload = mapOf("numbers" to numbers))
    companion object : DomainEventFactory {
        override fun deserialize(event: SerializableDomainEvent): DomainEvent {
            @Suppress("UNCHECKED_CAST")
            return AllPhoneNumbersListed(numbers = event.payload["numbers"] as List<String>)
        }
    }
}

class PhoneNumberFinder(val eventBus: EventBus, val text: String){

    @Subscribe
    fun find(numbers: AllPhoneNumbersListed){
        val foundNumbers = numbers.numbers.filter { text in it }
        eventBus.post(PhoneNumbersMatched(foundNumbers))
    }
}

data class PhoneNumbersMatched(val foundNumbers: List<String>) : DomainEvent {
    override fun serialize(): SerializableDomainEvent {
        return SerializableDomainEvent(PhoneNumbersMatched::class.java.name, payload = mapOf("foundNumbers" to foundNumbers))
    }

    companion object : DomainEventFactory {
        @Suppress("UNCHECKED_CAST")
        override fun deserialize(event: SerializableDomainEvent): DomainEvent =
                PhoneNumbersMatched(event.payload["foundNumbers"] as List<String>)

    }

}

class MatchedPhoneNumberCounter(val eventBus: EventBus){
    @Subscribe
    fun count(numbers: PhoneNumbersMatched) = eventBus.post(MatchedPhoneNumbersCounted(numbers.foundNumbers.size))
}

data class MatchedPhoneNumbersCounted(val count: Int) : DomainEvent {
    override fun serialize(): SerializableDomainEvent {
        return SerializableDomainEvent(MatchedPhoneNumbersCounted::class.java.name, payload = mapOf("count" to count))
    }

    companion object : DomainEventFactory{
        override fun deserialize(event: SerializableDomainEvent): DomainEvent {
            return MatchedPhoneNumbersCounted(event.payload["count"] as Int)
        }
    }

}

class PhoneNumberExecutive(){
    @Subscribe
    fun print(count: MatchedPhoneNumbersCounted) =
            println("${count.count} phone numbers matched on ${DateTimeFormatter
                    .ofPattern("MMM d yyyy hh:mm a")
                    .format(LocalDateTime.now())}")
}

class EventStoreListener(val store: EventStore){
    @Subscribe
    fun save(event: DomainEvent) = store.storeEvent(event)
}
