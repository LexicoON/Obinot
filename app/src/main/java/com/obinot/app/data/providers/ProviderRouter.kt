package com.obinot.app.data.providers

import android.util.Log
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

data class ProviderCandidate(
    val provider: AiProvider,
    val model: ProviderModel
) {
    val providerId: String get() = provider.id
    val modelId: String get() = model.id
}

/**
 * Router de providers con rotación de API keys y failover automático.
 *
 * RESPONSABILIDADES:
 *   1. Elegir (provider, model) candidatos por capability.
 *   2. Rotar entre las keys de un mismo provider cuando una tira 429.
 *   3. Hacer failover a otro provider cuando el actual se agota.
 *
 * ROTACIÓN DE KEYS:
 *   - Cada provider puede tener hasta N keys (configurable en UI).
 *   - La primera key de la lista es la "principal". Se intenta primero.
 *   - Si tira 429, se marca en cooldown por 60 min y se pasa a la siguiente.
 *   - Si TODAS las keys del provider están en cooldown, se pasa al siguiente
 *     provider (o se propaga el último error).
 *   - El cooldown vive en memoria (companion object del router) y se resetea
 *     al reiniciar la app. Esto es intencional: si el user reinició, asumimos
 *     que el rate limit del provider ya se liberó o cambió.
 *
 * COMPATIBILIDAD:
 *   Si un provider tiene una sola key (el caso de Release 1.x / 2.0 / 2.1),
 *   el comportamiento es idéntico al anterior: se usa esa única key, y si
 *   falla con un error retriable, se hace fallback al siguiente provider.
 */
class ProviderRouter(private val providers: List<AiProvider>) {

    /**
     * Devuelve los (provider, model) candidatos para [capability], en orden
     * de preferencia. Filtra providers que no soportan la capability y
     * providers que requieren API key y no la tienen configurada.
     */
    fun candidatesFor(
        capability: ProviderCapability,
        apiKeys: Map<String, List<String>>
    ): List<ProviderCandidate> {
        return providers
            .filter { it.supports(capability) }
            .filter { provider ->
                if (!provider.requiresApiKey) true
                else apiKeys[provider.id]?.any { it.isNotBlank() } == true
            }
            .flatMap { provider ->
                provider.modelsFor(capability).map { model ->
                    ProviderCandidate(provider, model)
                }
            }
    }

    /**
     * Reordena los candidatos para que los del [preferredProviderId] vayan
     * primero. Los demás mantienen su orden relativo original.
     */
    private fun orderCandidates(
        candidates: List<ProviderCandidate>,
        preferredProviderId: String?
    ): List<ProviderCandidate> {
        if (preferredProviderId == null) return candidates
        return candidates.sortedBy { if (it.providerId == preferredProviderId) 0 else 1 }
    }

    /**
     * Reordena las keys de un provider para intentar primero las que NO
     * están en cooldown. Las que sí lo están van al final, con la esperanza
     * de que el cooldown ya haya expirado. Si la lista queda vacía (provider
     * sin keys), devuelve la lista vacía.
     */
    private fun orderKeysByUsability(providerId: String, keys: List<String>): List<String> {
        if (keys.isEmpty()) return emptyList()
        val (usable, cooling) = keys.partition { !isOnCooldown(providerId, it) }
        return usable + cooling
    }

    /**
     * Ejecuta una tarea de texto con rotación de keys + fallback automático.
     *
     * Itera (provider, model) candidatos. Para cada uno, itera las keys del
     * provider. Si una key tira 429, se marca en cooldown y se pasa a la
     * siguiente. Si la operación devuelve texto válido, se limpia el cooldown
     * de esa key (éxito = key sana) y se devuelve. Si ninguna key del provider
     * funciona, se pasa al siguiente provider.
     *
     * Errores no retriables (400/401/403) se propagan inmediatamente.
     */
    suspend fun generateTextWithFallback(
        capability: ProviderCapability,
        apiKeys: Map<String, List<String>>,
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
            val keys = apiKeys[candidate.provider.id].orEmpty()
            val orderedKeys = orderKeysByUsability(candidate.provider.id, keys)

            for (key in orderedKeys) {
                try {
                    val result = candidate.provider.generateText(
                        apiKey = key,
                        model = candidate.model,
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        maxOutputTokens = maxOutputTokens
                    )
                    if (!result.isNullOrBlank()) {
                        clearCooldown(candidate.provider.id, key)
                        return result
                    }
                } catch (e: HttpException) {
                    if (e.code() == 429) {
                        markOnCooldown(candidate.provider.id, key)
                        lastError = e
                        continue
                    }
                    if (!isRetryable(e)) throw e
                    lastError = e
                    continue
                } catch (e: Exception) {
                    if (!isRetryable(e)) throw e
                    lastError = e
                    continue
                }
            }
        }

        lastError?.let { throw it }
        return null
    }

    /**
     * Igual que [generateTextWithFallback] pero para transcripción.
     */
    suspend fun transcribeWithFallback(
        apiKeys: Map<String, List<String>>,
        audioFile: File,
        preferredProviderId: String? = null
    ): String? {
        val candidates = orderCandidates(
            candidatesFor(ProviderCapability.TRANSCRIPTION, apiKeys),
            preferredProviderId
        )
        var lastError: Exception? = null

        for (candidate in candidates) {
            val keys = apiKeys[candidate.provider.id].orEmpty()
            val orderedKeys = orderKeysByUsability(candidate.provider.id, keys)

            for (key in orderedKeys) {
                try {
                    val result = candidate.provider.transcribe(
                        apiKey = key,
                        model = candidate.model,
                        audioFile = audioFile
                    )
                    if (!result.isNullOrBlank()) {
                        clearCooldown(candidate.provider.id, key)
                        return result
                    }
                } catch (e: HttpException) {
                    if (e.code() == 429) {
                        markOnCooldown(candidate.provider.id, key)
                        lastError = e
                        continue
                    }
                    if (!isRetryable(e)) throw e
                    lastError = e
                    continue
                } catch (e: Exception) {
                    if (!isRetryable(e)) throw e
                    lastError = e
                    continue
                }
            }
        }

        lastError?.let { throw it }
        return null
    }

    /**
     * Devuelve el primer candidato para [capability]. Útil para logs y para
     * decidir el mensaje de "loading…" antes de disparar la request.
     */
    fun firstCandidateFor(
        capability: ProviderCapability,
        apiKeys: Map<String, List<String>>,
        preferredProviderId: String? = null
    ): ProviderCandidate? =
        orderCandidates(candidatesFor(capability, apiKeys), preferredProviderId).firstOrNull()

    /**
     * Devuelve cuántas keys usables (no en cooldown) tiene un provider.
     * Útil para la UI de la ronda 9 (mostrar "2 de 3 keys disponibles").
     */
    fun usableKeyCount(providerId: String, keys: List<String>): Int =
        keys.count { !isOnCooldown(providerId, it) }

    /**
     * Decide si una excepción amerita probar otro provider / otra key.
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
        private const val TAG = "ProviderRouter"

        /**
         * Códigos HTTP reintentables.
         *   413: payload demasiado grande — otra key no ayuda pero otro
         *        provider puede tener límites más laxos.
         *   429: rate limit — se resuelve rotando a otra key o esperando.
         *   5xx: provider caído, otra key del mismo provider puede estar sana.
         */
        private val RETRYABLE_HTTP_CODES = setOf(413, 429, 500, 502, 503, 504)

        /** Cooldown de una key que tiró 429. */
        private const val KEY_COOLDOWN_MS = 60L * 60L * 1000L  // 60 min

        /**
         * Estado compartido de cooldowns entre todas las instancias del router.
         * Clave: "$providerId::$apiKey". Valor: timestamp de expiración.
         *
         * Se comparte vía companion para que múltiples ViewModels vean el
         * mismo estado (ej: si Gemini rate-limita desde ResultViewModel,
         * RecordViewModel también lo sabe).
         *
         * Vive en memoria. Se resetea al reiniciar la app, lo cual es
         * intencional: si el user reinició, el rate limit probablemente
         * ya cambió.
         */
        private val keyCooldowns = ConcurrentHashMap<String, Long>()

        private fun cooldownKey(providerId: String, apiKey: String): String =
            "$providerId::$apiKey"

        fun isOnCooldown(providerId: String, apiKey: String): Boolean {
            val expiry = keyCooldowns[cooldownKey(providerId, apiKey)] ?: return false
            if (System.currentTimeMillis() >= expiry) {
                keyCooldowns.remove(cooldownKey(providerId, apiKey))
                return false
            }
            return true
        }

        fun markOnCooldown(providerId: String, apiKey: String) {
            val expiry = System.currentTimeMillis() + KEY_COOLDOWN_MS
            keyCooldowns[cooldownKey(providerId, apiKey)] = expiry
            Log.d(TAG, "Key $providerId/${apiKey.take(6)}… on cooldown for 60 min")
        }

        fun clearCooldown(providerId: String, apiKey: String) {
            keyCooldowns.remove(cooldownKey(providerId, apiKey))
        }

        /**
         * Construye el router con los 3 providers del set final.
         * El orden define la preferencia global: Gemini → Groq → NVIDIA.
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