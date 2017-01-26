package de.gtrefs.eventstore

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import java.io.File

open class StorageBenchmarks {

    @BenchmarkMode(Mode.AverageTime)
    @Benchmark fun fill_in_memory_store_with_1_000_000_same_event(){
        val store: EventStore = EventStore(Storage.inMemory())
        val event = BenchmarkEvent("test")

        for(i in 1..1000000){
            store.storeEvent(event)
        }
    }

    @BenchmarkMode(Mode.AverageTime)
    @Benchmark fun fill_in_memory_store_with_1_000_000_equal_event_instances(){
        val store: EventStore = EventStore(Storage.inMemory())

        for(i in 1..1000000){
            store.storeEvent(BenchmarkEvent("test"))
        }
    }

    @BenchmarkMode(Mode.AverageTime)
    @Benchmark fun fill_in_memory_store_with_1_000_000_different_events(){
        val store: EventStore = EventStore(Storage.inMemory())

        for(i in 1..1000000){
            store.storeEvent(BenchmarkEvent(i.toString()))
        }
    }

    @BenchmarkMode(Mode.AverageTime)
    @Benchmark fun fill_json_store_with_1_000_000_same_event(){
        val file = File.createTempFile("store", "test")
        val store: EventStore = EventStore(Storage.jsonFileStorage(file))
        val event = BenchmarkEvent("test")

        for(i in 1..1000000){
            store.storeEvent(event)
        }

        file.delete()
    }


    @BenchmarkMode(Mode.AverageTime)
    @Benchmark fun fill_json_store_with_1_000_000_equal_event_instances(){
        val file = File.createTempFile("store", "test")
        val store: EventStore = EventStore(Storage.jsonFileStorage(file))

        for(i in 1..1000000){
            store.storeEvent(BenchmarkEvent("test"))
        }
    }


    @BenchmarkMode(Mode.AverageTime)
    @Benchmark fun fill_json_store_with_1_000_000_different_events(){
        val file = File.createTempFile("store", "test")
        val store: EventStore = EventStore(Storage.jsonFileStorage(file))

        for(i in 1..1000000){
            store.storeEvent(BenchmarkEvent(i.toString()))
        }
    }
}

data class BenchmarkEvent(val name: String): DomainEvent {
    private val serialized  by lazy {
        serialize(this)
    }
    override fun serialize(): SerializedDomainEvent = serialized
    companion object : DomainEventFactory {
        override fun deserialize(event: SerializedDomainEvent) = BenchmarkEvent(event.payload["name"] as String)
    }
}