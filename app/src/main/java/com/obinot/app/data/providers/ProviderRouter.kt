package com.obinot.app.data.providers

import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Candidato devuelto por el router: un provider + un modelo concreto
 * del provider que soporta la capability pedida.
 */
data class ProviderCandidate(
    val provider: AiProvider,
    val model: ProviderModel
) {
    val providerId: String get() = provider.id
    val modelId: String get() = model.id
}

/**
 * Router de providers. Centraliza la lógica de:
 *
 *   1) Qué (provider, model) son candidatos para una capability dada.
 *   2) En qué orden intentarlos (según el orden de `models` en cada provider
 *      y el orden de providers en el constructor).
 *   3) Failover automático: si un provider falla con 413/429/5xx, probar
 *      el siguiente sin que el caller se entere.
 *
 * El router es puro: no lee DataStore, no toca strings de recursos, no
 * navega. El caller le pasa un Map<providerId, apiKey> con las keys que
 * están disponibles en este momento.
 *
 * PREFERENCIA:
 *   Los métodos de ejecución aceptan un `preferredProviderId` opcional.
 *   Si viene seteado, los candidatos de ese provider se ordenan primero
 *   (manteniendo el orden interno de modelos). Esto es lo que usa el modo
 *   Standard para alternar providers y estirar cuotas, SIN perder el
 *   fallback: si el preferido falla con un error reintentable, el router
 *   sigue con los demás.
 *
 * ROTACIÓN DE KEYS (Release 2, ronda E):
 *   Por ahora el router recibe una sola key por provider. Cuando se agregue
 *   rotación, el Map pasa a ser Map<providerId, List<apiKey>> y la lógica
 *   de failover dentro del mismo provider se agrega acá. La firma pública
 *   (generateTextWithFallback, transcribeWithFallback) se mantiene igual
 *   para no romper a los callers.
 */
class ProviderRouter(private val providers: List<AiProvider>) {

    /**
     * Devuelve los (provider, model) candidatos para [capability], en orden
     * de preferencia. Filtra:
     *   - Providers que no soportan la capability.
     *   - Providers que requieren API key pero no la tienen configurada.
     *   - Providers que requieren API key pero la tienen en blanco.
     *
     * No filtra por longitud del prompt/audio. Eso lo decide el caller
     * antes de llamar (ej: elegir Gemini para audio grande).
     */
    fun candidatesFor(
        capability: ProviderCapability,
        apiKeys: Map<String, String>
    ): List<ProviderCandidate> {
        return providers
            .filter { it.supports(capability) }
            .filter { provider ->
                !provider.requiresApiKey || !apiKeys[provider.id].isNullOrBlank()
            }
            .flatMap { provider ->
                provider.modelsFor(capability).map { model ->
                    ProviderCandidate(provider, model)
                }
            }
    }

    /**
     * Reordena la lista de candidatos para que los del [preferredProviderId]
     * vayan primero. Los demás mantienen su orden relativo original.
     * Si [preferredProviderId] es null o no matchea ningún candidato, la
     * lista se devuelve intacta.
     */
    private fun orderCandidates(
        candidates: List<ProviderCandidate>,
        preferredProviderId: String?
    ): List<ProviderCandidate> {
        if (preferredProviderId == null) return candidates
        // sortedBy es estable: los elementos con la misma key preservan
        // su orden de aparición. Así, dentro del preferido y dentro del
        // resto, el orden original (Gemini → Groq → NVIDIA) se mantiene.
        return candidates.sortedBy { if (it.providerId == preferredProviderId) 0 else 1 }
    }

    /**
     * Ejecuta una tarea de texto con failover automático.
     *
     * Itera los candidatos en orden. Para cada uno:
     *   - Si devuelve texto no vacío → devolver.
     *   - Si devuelve null/vacío → probar el siguiente (respuesta vacía
     *     puede ser transitoria).
     *   - Si tira HttpException con código reintentable (413/429/5xx) →
     *     probar el siguiente.
     *   - Si tira cualquier otra excepción → propagar (es un error real
     *     del input o de configuración, no vale la pena reintentar).
     *
     * [preferredProviderId] mueve los candidatos de ese provider al frente.
     *
     * Si se agotan los candidatos, tira la última excepción vista (o
     * devuelve null si nunca hubo excepción pero todos devolvieron vacío).
     */
    suspend fun generateTextWithFallback(
        capability: ProviderCapability,
        apiKeys: Map<String, String>,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int? = null,
        preferredProviderId: String? = null
    ): String? {
        val candidates = orderCandidates(
            candidatesFor(capability, apiKeys),
            preferredProviderId
        )
        var lastError: Exception? = null

        for (candidate in candidates) {
            val key = apiKeys[candidate.provider.id] ?: continue
            try {
                val result = candidate.provider.generateText(
                    apiKey = key,
                    model = candidate.model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    maxOutputTokens = maxOutputTokens
                )
                if (!result.isNullOrBlank()) return result
                // Respuesta vacía: probar el siguiente.
            } catch (e: Exception) {
                if (!isRetryable(e)) throw e
                lastError = e
            }
        }

        lastError?.let { throw it }
        return null
    }

    /**
     * Ejecuta una transcripción de audio con failover automático.
     *
     * Igual lógica que generateTextWithFallback pero para audio. El caller
     * ya se encargó de comprimir si hacía falta; acá solo se prueba cada
     * provider que soporte TRANSCRIPTION.
     *
     * [preferredProviderId] mueve los candidatos de ese provider al frente.
     *
     * Devuelve el texto transcrito o null si todos los candidatos devolvieron
     * vacío sin tirar excepción. Tira la última excepción vista si hubo
     * alguna.
     */
    suspend fun transcribeWithFallback(
        apiKeys: Map<String, String>,
        audioFile: File,
        preferredProviderId: String? = null
    ): String? {
        val candidates = orderCandidates(
            candidatesFor(ProviderCapability.TRANSCRIPTION, apiKeys),
            preferredProviderId
        )
        var lastError: Exception? = null

        for (candidate in candidates) {
            val key = apiKeys[candidate.provider.id] ?: continue
            try {
                val result = candidate.provider.transcribe(
                    apiKey = key,
                    model = candidate.model,
                    audioFile = audioFile
                )
                if (!result.isNullOrBlank()) return result
            } catch (e: Exception) {
                if (!isRetryable(e)) throw e
                lastError = e
            }
        }

        lastError?.let { throw it }
        return null
    }

    /**
     * Devuelve el primer candidato para [capability] sin ejecutar nada.
     * Útil para logs, telemetría, o para decidir el mensaje de "loading…"
     * antes de disparar la request (ej: saber si va a ir a Gemini o Groq
     * para mostrar el string correcto).
     */
    fun firstCandidateFor(
        capability: ProviderCapability,
        apiKeys: Map<String, String>,
        preferredProviderId: String? = null
    ): ProviderCandidate? =
        orderCandidates(candidatesFor(capability, apiKeys), preferredProviderId).firstOrNull()

    /**
     * Decide si una excepción amerita probar otro provider.
     *
     * Reintentables: problemas del provider o de la red que probablemente
     * no se repitan con otro provider.
     *   - 413: payload demasiado grande (otro provider puede tener límites más laxos).
     *   - 429: rate limit (otro provider tiene su propia cuota).
     *   - 5xx: el provider está caído, otro puede estar sano.
     *   - Timeout / IOException: falla de red transitoria.
     *
     * NO reintentables: culpa del input o de la config.
     *   - 400: request mal formado (mismo input fallaría en todos lados).
     *   - 401/403: key inválida o sin permisos (el user tiene que arreglarla).
     *   - Cualquier otra: no hay razón para pensar que otro provider ande mejor.
     */
    private fun isRetryable(e: Exception): Boolean {
        return when (e) {
            is HttpException -> e.code() in RETRYABLE_HTTP_CODES
            is SocketTimeoutException -> true
            is IOException -> true
            else -> false
        }
    }

    companion object {
        private val RETRYABLE_HTTP_CODES = setOf(413, 429, 500, 502, 503, 504)

        /**
         * Construye el router con los 3 providers del set final.
         *
         * El orden acá define la preferencia global cuando dos providers
         * tienen un modelo con la misma capability. Gemini primero (mejor
         * calidad general), Groq segundo (velocidad), NVIDIA tercero
         * (último recurso, solo en modo Full).
         */
        fun default(): ProviderRouter = ProviderRouter(
            listOf(
                GeminiProvider(),
                GroqProvider(),
                NvidiaProvider()
            )
        )
    }
}