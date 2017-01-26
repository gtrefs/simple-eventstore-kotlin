[![Dependency Status](https://www.versioneye.com/user/projects/5876d6b1fff5dc002f0e9cdb/badge.svg?style=flat-square)](https://www.versioneye.com/user/projects/5876d6b1fff5dc002f0e9cdb)
[![Build Status](https://travis-ci.org/gtrefs/simple-eventstore-kotlin.svg?branch=master)](https://travis-ci.org/gtrefs/simple-eventstore-kotlin)

# Simple Event Store - Kotlin fork

This event store is a clone of the original simple event store of [Raimo](https://github.com/rradczewski). His rationale is stated below.

## Rationale
>I needed a simple persistent storage mechanism for telegram bots with low traffic, but with a fast development pace. An eventstore captures events instead of state and lets me deduce state at runtime by folding over the events it has persisted before, which in turn enables me to build features on top of things that happened before that where recorded.

>Reading from the eventstore can happen asynchronously while writing only happens synchronously. Writing also takes into consideration an expected version of the store before writing (a very simple transaction mechanism).
> -- [Raimo](https://github.com/rradczewski/simple-eventstore)

## Differences
This event store is not FSA compliant. The backing storage stored Kotlin/Java flavored JSON instead.

Events are typed and inherit from `DomainEvent`.

## Usage
An `EventStore` is initialized with a `Storage` which is either based on memory or is backed by a file.

```Kotlin
val memory = EventStore(Storage.inMemory())
val persistent =  EventStore(Storage.jsonFileStorage(file))
```

## Events
An event is declared by implemeting the `DomainEvent` interface and adhering to a serialization contract.
```Kotlin
data class ColorChangedEvent(
  val timeStamp: Long,
  val oldColor: Color,
  val newColor: Color): DomainEvent {

    private val serialized by lazy {
        serialization(this)
    }

    override fun serialize(): SerializedDomainEvent = serialized

    companion object : DomainEventFactory {
        override fun deserialize(event: SerializedDomainEvent): DomainEvent =
            ColorChangedEvent(event.meta["time"] as Long,
             event.payload["oldColor"] as Color,
             event.payload["newColor"] as Color)
    }
}
```
The method `serialize` defines how an instance of this event is serialized to a `SerializedDomainEvent`. The companion object of an event class is required to implement interface `DomainEventFactory` which defines how a `SerializedDomainEvent` can be deserialized.

**Note**: This contract is a major drawback and planned to be replaced by a more non-intrusive deserialization DSL which provides convinient default cases.

## Projection state
> State is deduced at runtime by replaying all events in the store and folding them over a projection. A projection is a function (events: DomainEvent[]) => S.

```Kotlin
val loggedInUsers: (List<DomainEvent>) -> List<UserLoggedIn> = { it.fold(emptyList(),
        {s, d -> when(d){ is UserLoggedIn -> s + d else -> s}}
)}

store.project(registeredUsers).thenApply { println("$it.name is active.") }

// prints Raimo is active
```
