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

## Serialization DSL 

### Default behaviour
By default the constructor parameters of an event are assumed to be payload, meta information is empty and type is set to the full qualified name (FQN) of the underlying Java type.
```Kotlin
data class ColorChangedEvent(val timeStamp: Long, val oldColor: Color, val newColor: Color): DomainEvent {

    private val serialized by lazy {
        serialization(this)
    }

    override fun serialize(): SerializedDomainEvent = serialized

    // companion object left out
}

data class Color(val red: Byte, val green: Byte, val blue: Byte, val alpha: Byte)

val now = val now = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
val colorChanged = ColorChangedEvent(now, Color(120,120,120,100), Color(120,120,120,50))
val result = colorChanged.serialize()
```

The type of `result` is `de.gtrefs.eventstore.ColorChangedEvent`, property `meta` is empty and property `payload` is a map with three entries:

```Kotlin
result.payload == mapOf(
    "timeStamp" to now, 
    "oldColor" to Color(120,120,120,100), 
    "newColor" to Color(120,120,120,50)
)
```

### Instance access through `it`
Within the `meta` and `payload` directives, the instance which should be translated is accessible throught `it`.
```Kotlin
val serialization = serialize<ColorChangedEvent> {
    payload {
        "oldColor" with it.oldColor
        "newColor" with it.newColor
    }
}
```

### Exclution and explicit parameters

Manipulating the translation is possible in two mutual exclusive ways. First, parameters can be excluded from the translation. Second, key-value-pairs can be defined explicitly.
```Kotlin
// Excluding parameter timeStamp from the payload
val serialization = serialize<ColorChangedEvent>{
    payload {
        without("timeStamp")
    }
}

val serialized = serialization(colorChanged)

serialized.payload.keys == setOf("oldColor", "newColor")
```
Parameter `timeStamp` is removed from the translation. It is not in `meta` nor in `payload`. Property `payload` just contains `oldColor` and `newColor`.

A more imperative approach is to explicitly declare which key-value-pairs should be stored in the properties `meta` and `payload`.
```Kotlin
val serialization = serialize<ColorChangedEvent> {
    meta {
        "timeStamp" with it.timeStamp
    }
   payload {
        exclude("timeStamp")
   }
}
```
In this case `meta` contains `timeStamp` and `payload` contains the `oldColor` and `newColor`. The `with` directive is used to declare the new pair.

**Note**: Being explicit has a higher precedence than exclusion. This means, if there are explicitly declared key-value-pairs, these pairs are used for serialization and `without` is ignored. 
``` Kotlin
val serialization = serialize<ColorChangedEvent> {
    payload {
        without("timeStamp")
        "time" with it.timeStamp
    }
}

val serialized = serialization(colorChanged)

serialized.payload == mapOf("time" to now)
```
It is a `IllegalArgumentException` to exclude and explicitly declare values with the same key and vice versa.
```Kotlin
it("explicit parameters cannot be excluded"){
     val serialization = serialize<ColorChangedEvent>{
         payload {
             "timeStamp" with it.timeStamp
             without("timeStamp")
         }
     }

     assertFailsWith<IllegalArgumentException> {
         serialization(colorChanged)
     }
 }

 it("excluded parameters cannot be added"){
     val serialization = serialize<ColorChangedEvent>{
         payload {
             without("timeStamp")
             "timeStamp" with it.timeStamp
         }
     }

     assertFailsWith<IllegalArgumentException> {
         serialization(colorChanged)
     }
 }
``` 
