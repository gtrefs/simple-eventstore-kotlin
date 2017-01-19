package de.gtrefs.eventstore

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import de.gtrefs.eventstore.Storage.Companion.inMemory
import de.gtrefs.eventstore.Storage.Companion.jsonFileStorage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.File
import java.nio.file.Files
import java.sql.Time
import java.time.LocalDateTime

class JsonFileStorageSpec : Spek ({
    describe("JsonFileStorage"){
        val file: File = createTempFile("storage")
        val storage = jsonFileStorage(file)
        val testEvent = TestEvent("test").serialize()
        val testEvents = listOf(TestEvent("test1").serialize(), TestEvent("test2").serialize())

        on("Writing") {


            it("should append event to it's file") {
                storage.write(testEvent)

                val storedTuples = storage.readAll()

                expect(storedTuples[0]).to.equal(testEvent)
            }

            it("should append multiple events to it's file"){
                truncate(file)
                testEvents.forEach { storage.write(it) }

                val storedTuples = storage.readAll()

                expect(storedTuples).to.equal(testEvents)
            }

        }

        on("Reading"){
            it("should read it's file"){
                val json = """{"type":"${TestEvent::class.java.name}", "meta":{"name": "file"}, "payload": {}}"""
                Files.write(file.toPath(), json.toByteArray())

                val tuples = storage.readAll()

                tuples.should.equal(listOf(TestEvent("file").serialize()))
            }

            it("should read async"){
                truncate(file)
                testEvents.forEach { storage.write(it)}

                val tuples = storage.readAllAsync().join()

                tuples.should.equal(testEvents)
            }

            it("should handle time properly"){
                truncate(file)
                val now = LocalDateTime.now()
                val time: TimeEvent = TimeEvent(now)

                storage.write(time.serialize())

                val serializedTimeEvent = storage.readAll()[0]
                serializedTimeEvent.payload["time"].should.equal(now)
            }
        }

        afterGroup {
            Files.deleteIfExists(file.toPath())
        }


    }
})

private fun truncate(file: File) {
    Files.write(file.toPath(), "".toByteArray())
}

class InMemorySpec : Spek({
    describe("In Memory Storage"){
        val storage = inMemory()

        it("should return a list of all saved events"){
            val events = listOf(TestEvent("first").serialize(), TestEvent("second").serialize())

            events.forEach { storage.write(it) }

            val loadedEvents = storage.readAll()

            loadedEvents.should.equal(events)
        }
    }
})