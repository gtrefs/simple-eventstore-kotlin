package de.gtrefs.eventstore

import kotlin.reflect.full.companionObjectInstance

interface DomainEvent {
    fun serialize(): SerializedDomainEvent
}

interface DomainEventFactory {
    fun deserialize(event: SerializedDomainEvent): DomainEvent
}

data class SerializedDomainEvent(val type: String = "",
                                 val meta: Map<String, Any> = emptyMap(),
                                 val payload: Map<String, Any> = emptyMap()){
    private val factory by lazy {
        Class.forName(type).kotlin.companionObjectInstance as DomainEventFactory
    }

    fun deserialize(): DomainEvent = factory.deserialize(this)
}
