package de.gtrefs.eventstore

import java.util.*
import java.util.Optional.empty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

fun <E: DomainEvent> serialize(event: E): SerializedDomainEvent = serialize<E>()(event)

fun <E : DomainEvent> serialize(init: Serialization<E>.() -> Unit = {}): Serialization<E> = Serialization<E>().apply {
    this.init()
}

class Serialization<E: DomainEvent> {

    internal var typeDescription: (E) -> Optional<String> = { empty() }
    internal var metaDescription: ParameterContainer.(E) -> Unit = { }
    internal var payloadDescription: ParameterContainer.(E) -> Unit = { }

    fun type(description: (E) -> String): Unit {
        typeDescription = { Optional.of(description(it)) }
    }

    fun  meta(description: ParameterContainer.(E) -> Unit): Unit {
        metaDescription = description
    }

    fun  payload(description: ParameterContainer.(E) -> Unit): Unit {
        payloadDescription = description
    }

    operator fun invoke(event: E): SerializedDomainEvent = Interpreter(this).run(event)
}

internal data class Interpreter<E: DomainEvent>(val serialization:Serialization<E>){
    internal fun run(event: E):SerializedDomainEvent = with(serialization) {
        val type = typeDescription(event).orElse(event.javaClass.name)
        val meta = parametersOf(event, description = metaDescription)
        val payload = parametersOf(event, parametersByName(event), description = payloadDescription)

        SerializedDomainEvent(type,meta,payload)
    }

    private fun parametersByName(event: E): Map<String, Any> {
        val parameters = event.javaClass.kotlin.primaryConstructor?.parameters?.map { it.name } ?: emptyList()
        val properties = event.javaClass.kotlin.memberProperties

        return properties.filter { parameters.contains(it.name) }.map {
            it.name to it.get(event)!!
        }.toMap()
    }

    private fun parametersOf(event: E,
                             default: Map<String, Any> = emptyMap(),
                             description: ParameterContainer.(E) -> Unit): Map<String, Any> =
            with(ParameterContainer()){
                description(event)
                when(Pair(explicit.isEmpty(), exclude.isEmpty())){
                    Pair(true, true) -> default
                    Pair(true, false) -> remove(from = default, keys = exclude)
                    else -> explicit.toMap()
                }
            }

    private fun remove(from: Map<String, Any>, keys: ArrayList<String>) = from.filterKeys { it !in keys }
}

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
