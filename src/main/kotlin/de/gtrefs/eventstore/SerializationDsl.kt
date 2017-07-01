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
    internal var metaDescription: Container.(E) -> Unit = { }
    internal var payloadDescription: Container.(E) -> Unit = { }

    fun type(description: (E) -> String): Unit {
        typeDescription = { Optional.of(description(it)) }
    }

    fun  meta(description: Container.(E) -> Unit): Unit {
        metaDescription = description
    }

    fun  payload(description: Container.(E) -> Unit): Unit {
        payloadDescription = description
    }

    operator fun invoke(event: E): SerializedDomainEvent = Interpreter(this).run(event)
}

internal data class Interpreter<E: DomainEvent>(val serialization:Serialization<E>){
    internal fun run(event: E):SerializedDomainEvent = with(serialization) {
        val type = typeDescription(event).orElse(event.javaClass.name)
        val meta = computeParameters(event, fillWith = metaDescription)
        val payload = computeParameters(event, parametersByName(event), fillWith = payloadDescription)

        SerializedDomainEvent(type,meta,payload)
    }

    private fun computeParameters(event: E,
                                  default: Map<String, Any> = emptyMap(),
                                  fillWith: Container.(E) -> Unit): Map<String, Any> = with(Container()){
        fillWith(event)
        extractParameters(default)
    }

    private fun Container.extractParameters(default: Map<String, Any>): Map<String, Any> {
        return when (Pair(explicit.isEmpty(), exclude.isEmpty())) {
            Pair(true, true) -> default
            Pair(true, false) -> remove(from = default, keys = exclude)
            else -> explicit.toMap()
        }
    }

    private fun remove(from: Map<String, Any>, keys: ArrayList<String>) = from.filterKeys { it !in keys }

    private fun parametersByName(event: E): Map<String, Any> {
        val parameters = event.javaClass.kotlin.primaryConstructor?.parameters?.map { it.name } ?: emptyList()
        val properties = event.javaClass.kotlin.memberProperties

        return properties.filter { parameters.contains(it.name) }.map {
            it.name to it.get(event)!!
        }.toMap()
    }
}

class Container {
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
