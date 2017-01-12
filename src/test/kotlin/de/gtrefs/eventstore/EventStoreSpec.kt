@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package de.gtrefs.eventstore

import com.winterbe.expekt.should
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import kotlin.test.assertFailsWith

class EventStoreSpec : Spek({
    describe("the eventstore") {

        on("storage") {
            val store = EventStore(Storage.inMemory())
            it("can read the events it writes") {
                val event = TestEvent("Hello")
                val secondEvent = TestEvent("Hello second")

                store.storeEvent(event)
                store.storeEvent(secondEvent)

                store.project({ it }).join().should.contain.all.elements(event, secondEvent)
            }
        }

        on("versioning"){
            val store = EventStore(Storage.inMemory())
            it("provides a version"){
                val version = store.version()

                version.should.equal(Version(0))
            }

            it("should increase the version with every stored event"){
                val before = store.version()

                store.storeEvent(TestEvent("Test"))

                val after = store.version()

                before.should.equal(after-1)
            }

            it("should refuse to store an event with lower version number"){
                val event = TestEvent("Hello")

                store.storeEvent(event)

                assertFailsWith<IllegalArgumentException>{
                    store.storeEvent(event, Version(0))
                }
            }
        }
    }
})


