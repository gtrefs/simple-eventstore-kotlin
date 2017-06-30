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

    private var type: (E) -> Optional<String> = { empty() }
    private var initMeta: (ParameterContainer) -> (E) -> ParameterContainer = {{ _ -> it }}
    private var initPayload: (ParameterContainer) -> (E) -> ParameterContainer = {{ _ -> it }}

    fun type(type: (E) -> String): Unit {
        this.type = { Optional.of(type(it)) }
    }

    fun  meta(describe: ParameterContainer.(E) -> Unit): Unit {
        initMeta = { container -> { container.apply { describe(it) } } }
    }

    fun  payload(describe: ParameterContainer.(E) -> Unit): Unit {
        initPayload = { container -> { container.apply { describe(it) } } }
    }

    operator fun invoke(event: E): SerializedDomainEvent =
            SerializedDomainEvent(typeOf(event), metaOf(event), payloadOf(event))

    private fun typeOf(event: E) = type(event).orElse(event.javaClass.name)

    private fun metaOf(event: E) = parametersOf(event, init = initMeta)

    private fun payloadOf(event: E) = parametersOf(event, parametersByName(event), init = initPayload)

    private fun parametersByName(event: E): Map<String, Any> {
        val parameters = event.javaClass.kotlin.primaryConstructor?.parameters?.map { it.name } ?: emptyList()
        val properties = event.javaClass.kotlin.memberProperties

        return properties.filter { parameters.contains(it.name) }.map {
            it.name to it.get(event)!!
        }.toMap()
    }

    private fun parametersOf(event: E,
                             default: Map<String, Any> = emptyMap(),
                             init: ((ParameterContainer) -> (E) -> ParameterContainer)? = null): Map<String, Any> =
        with(initContainer(event, init)){
            when(Pair(explicit.isEmpty(), exclude.isEmpty())){
                Pair(true, true) -> default
                Pair(true, false) -> remove(from = default, keys = exclude)
                else -> explicit.toMap()
            }
        }

    private fun initContainer(event: E, init: ((ParameterContainer) -> (E) -> ParameterContainer)?) =
            ParameterContainer().apply {
                init?.let { it(this)(event) }
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
