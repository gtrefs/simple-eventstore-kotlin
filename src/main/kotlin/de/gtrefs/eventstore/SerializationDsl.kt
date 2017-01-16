package de.gtrefs.eventstore

import java.util.*
import kotlin.reflect.memberProperties
import kotlin.reflect.primaryConstructor

fun <E: DomainEvent> serialize(event: E): SerializedDomainEvent = serialize<E>()(event)

fun <E: DomainEvent> serialize(): Serialization<E> = serialize<E>{}

fun <E : DomainEvent> serialize(init: Serialization<E>.() -> Unit): Serialization<E> {
    val serialization = Serialization<E>()
    serialization.init()
    return serialization
}

class Serialization<E: DomainEvent> {

    private var type: ((E) -> String)? = null
    private var initMeta: ((ParameterContainer) -> (E) -> ParameterContainer)? = null
    private var initPayload: ((ParameterContainer) -> (E) -> ParameterContainer)? = null

    fun type(type: (E) -> String): Unit {
        this.type = type
    }

    fun  meta(init: ParameterContainer.(E) -> Unit): Unit {
        initMeta = collect(init)
    }

    fun  payload(init: ParameterContainer.(E) -> Unit): Unit {
        initPayload = collect(init)
    }

    private fun collect(init: ParameterContainer.(E) -> Unit): (ParameterContainer) -> (E) -> ParameterContainer =
            {container -> {event ->
                container.init(event)
                container
            }}

    operator fun invoke(event: E): SerializedDomainEvent =
            SerializedDomainEvent(typeOf(event), metaOf(event), payloadOf(event))

    private fun typeOf(event: E) = this.type?.invoke(event) ?: event.javaClass.name

    private fun metaOf(event: E) = parametersOf(event, init = initMeta)

    private fun payloadOf(event: E) = parametersOf(event, parametersByName(event), initPayload)

    private fun parametersByName(event: E): Map<String, Any> {
        val parameters = event.javaClass.kotlin.primaryConstructor?.parameters?.map { it.name } ?: emptyList()
        val properties = event.javaClass.kotlin.memberProperties

        return properties.filter { parameters.contains(it.name) }.map {
            it.name to it.get(event)!!
        }.toMap()
    }

    private fun parametersOf(event: E,
                             default: Map<String, Any> = emptyMap(),
                             init: ((ParameterContainer) -> (E) -> ParameterContainer)? = null): Map<String, Any> {

        val container = initContainer(event, init)
        val explicit = container.explicit.toMap()
        val exclude = container.exclude

        return when(Pair(explicit.isEmpty(), exclude.isEmpty())){
            Pair(true, true) -> default
            Pair(true, false) -> remove(from = default, keys = exclude)
            else -> explicit
        }
    }

    private fun initContainer(event: E, init: ((ParameterContainer) -> (E) -> ParameterContainer)?): ParameterContainer {
        val container = ParameterContainer()
        init?.invoke(container)?.invoke(event)
        return container
    }

    private fun remove(from: Map<String, Any>, keys: ArrayList<String>) = from.filterKeys { it !in keys }

    class ParameterContainer {
        internal val explicit = arrayListOf<Pair<String, Any>>()
        internal val exclude = arrayListOf<String>()

        infix fun String.with(that: Any): Unit {
            if(this in exclude){
                throw IllegalArgumentException("Cannot add parameter with name '${this}'. It was excluded before.")
            }
            explicit += this to that
        }

        fun without(parameter: String) {
            if(explicit.any { it.first == parameter }){
                throw IllegalArgumentException("Cannot exclude parameter '$parameter'. It was explicitly added before.")
            }
            exclude += parameter
        }
    }
}
