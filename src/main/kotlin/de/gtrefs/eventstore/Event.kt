package de.gtrefs.eventstore

import kotlin.reflect.companionObjectInstance

interface DomainEvent {
    fun serialize(): SerializableDomainEvent
}

interface DomainEventFactory {
    fun deserialize(event: SerializableDomainEvent): DomainEvent
}

data class SerializableDomainEvent(val type: String, val meta: Map<String, Any>, val payload: Map<String, Any>){
    private val factory by lazy {
        Class.forName(type).kotlin.companionObjectInstance as DomainEventFactory
    }

    fun deserialize(): DomainEvent = factory.deserialize(this)
}
