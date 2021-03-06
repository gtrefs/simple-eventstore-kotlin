package de.gtrefs.eventstore


class EventStore(val storage: Storage) {

    fun <S> project(projection: (List<DomainEvent>) -> S) = storage.readAllAsync()
                .thenApply { it.map { it.deserialize() } }
                .thenApply { projection(it) }

    fun storeEvent(event: DomainEvent, expectedVersion: Version) {
        val currentVersion = version()
        if(currentVersion != expectedVersion){
            throw IllegalArgumentException("Expected store version to be $expectedVersion, but was $currentVersion")
        }
        storeEvent(event)
    }

    fun storeEvent(event: DomainEvent): Unit = storage.write(event.serialize())

    fun version(): Version = Version(storage.readAll().size)
}

data class Version(val size: Int){
    infix operator fun  minus(i: Int): Version {
        return Version(size - i)
    }

    infix operator fun  plus(i: Int): Version {
        return Version(size + i)
    }
}
