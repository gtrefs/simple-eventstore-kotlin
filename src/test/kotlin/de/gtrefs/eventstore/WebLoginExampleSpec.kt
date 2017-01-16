package de.gtrefs.eventstore

import com.winterbe.expekt.should
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.*

class WebLoginExampleSpec : Spek({
    describe("Web login"){
        val file: File = createTempFile("storage")
        on("user registration"){
            val store = EventStore(Storage.jsonFileStorage(file))
            val raimo = User("rradczewski", "password", Email("rradczewski@me.com"))
            val gregor = User("gtrefs", "password", Email("gtrefs@me.com"))
            val now = LocalDateTime.now()
            val events = listOf(
                    UserRegistered(now.minusDays(100), raimo),
                    UserRegistered(now.minusDays(80), gregor),
                    UserLoggedIn(now, raimo))

            events.forEach { store.storeEvent(it) }

            val registeredUsers: (List<DomainEvent>) -> List<UserRegistered> = { it.fold(emptyList(),
                    { s,d -> when(d){ is UserRegistered -> s + d else -> s }}
            )}

            val loggedInUsers: (List<DomainEvent>) -> List<UserLoggedIn> = { it.fold(emptyList(),
                    {s, d -> when(d){ is UserLoggedIn -> s + d else -> s}}
            )}

            it("should project user registration events"){
                val expectedEvents = listOf(events[0] as UserRegistered, events[1] as UserRegistered)
                val projectedEvents = store.project(registeredUsers).join()

                projectedEvents.should.equal(expectedEvents)
            }

            it("should project users which are logged in"){
                val users: List<User> = store.project(loggedInUsers).thenApply { it.map { it.user } }.join()

                users.should.have.size(1)
                users[0].should.equal(raimo)
            }
        }

        afterGroup {
            Files.deleteIfExists(file.toPath())
        }
    }
})

data class UserLoggedIn(val time: LocalDateTime, val user: User) : DomainEvent {
    override fun serialize(): SerializedDomainEvent =
            SerializedDomainEvent(UserLoggedIn::class.java.name,
                    emptyMap(),
                    mapOf("time" to time) + user.serialize())


    companion object : DomainEventFactory {
        @Suppress("UNCHECKED_CAST")
        override fun deserialize(event: SerializedDomainEvent): DomainEvent =
                UserLoggedIn(toLocalDateTime(event.payload["time"] as ArrayList<Int>), User.deserialize(event.payload))
    }
}

data class UserRegistered(val time: LocalDateTime, val user: User) : DomainEvent {
    override fun serialize(): SerializedDomainEvent = SerializedDomainEvent(
            UserRegistered::class.java.name,
            mapOf("time" to time),
            user.serialize()
    )

    companion object : DomainEventFactory {
        @Suppress("UNCHECKED_CAST")
        override fun deserialize(event: SerializedDomainEvent): DomainEvent {
            val time = toLocalDateTime(event.meta["time"] as ArrayList<Int>)
            return UserRegistered(time, User.deserialize(event.payload))
        }


    }
}

data class User(val name: String, val password: String, val email: Email){
    fun serialize(): Map<String, Any> = mapOf("name" to name, "password" to password, "email" to email.email)
    companion object {
        fun deserialize(data: Map<String, Any>) = User(data["name"] as String, data["password"] as String, Email(data["email"] as String))
    }
}

fun toLocalDateTime(t: ArrayList<Int>): LocalDateTime {
    return LocalDateTime.of(t[0], t[1], t[2], t[3], t[4], t[5], t[6])
}

data class Email(val email: String)
