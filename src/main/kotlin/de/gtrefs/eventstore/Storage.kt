package de.gtrefs.eventstore

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.experimental.future.future
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.CompletableFuture

interface Storage {
    fun write(it: SerializedDomainEvent): Unit
    fun readAll(): List<SerializedDomainEvent>
    fun readAllAsync(): CompletableFuture<List<SerializedDomainEvent>> = future { readAll() }

    companion object {
        fun jsonFileStorage(file: File): JsonFileStorage = JsonFileStorage(file)
        fun inMemory(): InMemory = InMemory()
        fun devNull(): Storage = object : Storage {
            override fun readAll(): List<SerializedDomainEvent> = emptyList()
            override fun write(it: SerializedDomainEvent){}
        }
    }
}

class InMemory : Storage {

    val events:ArrayList<SerializedDomainEvent> = ArrayList()

    override fun readAll(): List<SerializedDomainEvent> {
        return events
    }

    override fun write(it: SerializedDomainEvent) {
        events.add(it)
    }

}


class JsonFileStorage(val file: File) : Storage {
    val mapper = jacksonObjectMapper()
            .enableDefaultTyping(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT)
            .registerModule(JavaTimeModule())

    override fun write(it: SerializedDomainEvent): Unit {
        val json = mapper.writeValueAsString(it) + System.lineSeparator()
        Files.write(file.toPath(), json.toByteArray(), StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)
    }

    override fun readAll(): List<SerializedDomainEvent> =
        file.readLines().map { mapper.readValue(it, SerializedDomainEvent::class.java) }

}