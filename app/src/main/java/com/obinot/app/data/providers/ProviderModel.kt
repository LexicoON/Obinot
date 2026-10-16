package com.obinot.app.data.providers

/**
 * Un modelo concreto ofrecido por un provider.
 *
 * [id] es el identificador que se manda a la API (ej: "gemini-3.8-flash",
 * "openai/gpt-oss-120b", "meta/llama-3.3-70b-instruct").
 *
 * [displayName] es lo que se muestra en UI (Settings, logs). Si no se
 * especifica, cae al id.
 *
 * [capabilities] es el set de cosas que este modelo sabe hacer. El router
 * lo consulta para saber si un modelo es candidato para una tarea dada.
 *
 * El ORDEN en la lista `models` de cada provider define la preferencia:
 * el router itera en orden y prueba el primero que tenga la capability.
 */
data class ProviderModel(
    val id: String,
    val displayName: String = id,
    val capabilities: Set<ProviderCapability>
) {
    fun supports(capability: ProviderCapability): Boolean = capability in capabilities
}