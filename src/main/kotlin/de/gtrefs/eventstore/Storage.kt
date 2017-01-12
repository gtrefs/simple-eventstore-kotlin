package de.gtrefs.eventstore

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*

interface Storage {
    fun write(it: SerializableDomainEvent): Unit
    fun readAll(): List<SerializableDomainEvent>

    companion object {
        fun jsonFileStorage(file: File): JsonFileStorage = JsonFileStorage(file)
        fun inMemory(): InMemory = InMemory()
        fun devNull(): Storage = object : Storage {
            override fun readAll(): List<SerializableDomainEvent> = emptyList()
            override fun write(it: SerializableDomainEvent){}
        }
    }
}

class InMemory : Storage {
    var events:ArrayList<SerializableDomainEvent> = ArrayList()

    override fun readAll(): List<SerializableDomainEvent> {
        return events
    }

    override fun write(it: SerializableDomainEvent) {
        events.add(it)
    }

}


class JsonFileStorage(val file: File) : Storage {
    val mapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())

    override fun write(it: SerializableDomainEvent): Unit {
        val json = mapper.writeValueAsString(it) + System.lineSeparator()
        Files.write(file.toPath(), json.toByteArray(), StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)
    }

    override fun readAll(): List<SerializableDomainEvent> =
        file.readLines().map { mapper.readValue(it, SerializableDomainEvent::class.java) }

}