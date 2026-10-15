package com.obinot.app.data.providers

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ProviderCandidate(
    val provider: AiProvider,
    val model: ProviderModel
) {
    val providerId: String get() = provider.id
    val modelId: String get() = model.id
}

/**
 * Router de providers (Gemini + Groq) con rotación de keys y failover.
 *
 * ROTACIÓN DE KEYS (optimizada):
 *   - Round-robin: cada request empieza por una key distinta entre las
 *     usables, así la cuota se reparte en vez de agotar siempre la primera.
 *   - Cooldown escalonado por key: el primer 429 bloquea la key poco tiempo
 *     (respeta Retry-After si viene, si no 90 s); 429 consecutivos escalan
 *     (90 s → 5 min → 30 min → 2 h). Un éxito resetea el contador.
 *     Antes era un bloqueo fijo de 60 min, que desperdiciaba keys sanas
 *     tras un simple límite por minuto.
 *   - Las keys en cooldown van al final: si todo está bloqueado igual se
 *     intenta la que expire antes, en vez de fallar sin probar.
 *
 * PROTECCIÓN DE CUOTA:
 *   - Espaciado mínimo entre requests por provider (Gemini es el más
 *     sensible a ráfagas de RPM; títulos + procesado seguidos lo agotaban).
 *   - Un 429 nunca se reintenta sobre la misma key: se rota o se hace failover.
 *
 * El estado (cooldowns, round-robin, espaciado) vive en el companion para
 * que todos los ViewModels compartan la misma vista.
 */
class ProviderRouter(private val providers: List<AiProvider>) {

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
                provider.modelsFor(capability).map { model -> ProviderCandidate(provider, model) }
            }
    }

    /** Los del [preferredProviderId] primero; el resto conserva su orden. */
    private fun orderCandidates(
        candidates: List<ProviderCandidate>,
        preferredProviderId: String?
    ): List<ProviderCandidate> {
        if (preferredProviderId == null) return candidates
        return candidates.sortedBy { if (it.providerId == preferredProviderId) 0 else 1 }
    }

    /**
     * Keys usables primero, rotadas round-robin; las que están en cooldown
     * al final, ordenadas por la que expira antes.
     */
    private fun orderKeys(providerId: String, keys: List<String>): List<String> {
        val clean = keys.filter { it.isNotBlank() }
        if (clean.isEmpty()) return emptyList()
        val (usable, cooling) = clean.partition { !isOnCooldown(providerId, it) }
        val rotated = if (usable.size > 1) {
            val start = Math.floorMod(rrCounter(providerId).getAndIncrement(), usable.size)
            usable.drop(start) + usable.take(start)
        } else usable
        return rotated + cooling.sortedBy { cooldownExpiry(providerId, it) }
    }

    private suspend fun <T> runWithFallback(
        candidates: List<ProviderCandidate>,
        apiKeys: Map<String, List<String>>,
        call: suspend (ProviderCandidate, String) -> T?
    ): T? {
        var lastError: Exception? = null
        for (candidate in candidates) {
            val orderedKeys = orderKeys(candidate.providerId, apiKeys[candidate.providerId].orEmpty())
            for (key in orderedKeys) {
                try {
                    awaitSlot(candidate.providerId)
                    val result = call(candidate, key)
                    if (result != null && (result !is String || result.isNotBlank())) {
                        recordSuccess(candidate.providerId, key)
                        return result
                    }
                } catch (e: HttpException) {
                    if (e.code() == 429) {
                        recordRateLimit(candidate.providerId, key, retryAfterSeconds(e))
                        lastError = e
                        continue
                    }
                    if (!isRetryable(e)) throw e
                    lastError = e
                } catch (e: Exception) {
                    if (!isRetryable(e)) throw e
                    lastError = e
                }
            }
        }
        lastError?.let { throw it }
        return null
    }

    suspend fun generateTextWithFallback(
        capability: ProviderCapability,
        apiKeys: Map<String, List<String>>,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int? = null,
        preferredProviderId: String? = null
    ): String? = runWithFallback(
        orderCandidates(candidatesFor(capability, apiKeys), preferredProviderId),
        apiKeys
    ) { c, key ->
        c.provider.generateText(key, c.model, systemPrompt, userPrompt, maxOutputTokens)
    }

    suspend fun transcribeWithFallback(
        apiKeys: Map<String, List<String>>,
        audioFile: File,
        preferredProviderId: String? = null
    ): String? = runWithFallback(
        orderCandidates(candidatesFor(ProviderCapability.TRANSCRIPTION, apiKeys), preferredProviderId),
        apiKeys
    ) { c, key ->
        c.provider.transcribe(key, c.model, audioFile)
    }

    fun firstCandidateFor(
        capability: ProviderCapability,
        apiKeys: Map<String, List<String>>,
        preferredProviderId: String? = null
    ): ProviderCandidate? =
        orderCandidates(candidatesFor(capability, apiKeys), preferredProviderId).firstOrNull()

    fun usableKeyCount(providerId: String, keys: List<String>): Int =
        keys.count { !isOnCooldown(providerId, it) }

    private fun isRetryable(e: Exception): Boolean = when (e) {
        is HttpException -> e.code() in RETRYABLE_HTTP_CODES
        is SocketTimeoutException -> true
        is IOException -> true
        else -> false
    }

    private fun retryAfterSeconds(e: HttpException): Long? =
        e.response()?.headers()?.get("Retry-After")?.trim()?.toLongOrNull()

    companion object {
        private const val TAG = "ProviderRouter"

        /** 413: otro provider puede tener límites más laxos. 429: rotar. 5xx: infra. */
        private val RETRYABLE_HTTP_CODES = setOf(413, 429, 500, 502, 503, 504)

        private val COOLDOWN_STEPS_MS = longArrayOf(
            90_000L, 5L * 60_000L, 30L * 60_000L, 2L * 60L * 60_000L
        )
        private const val MAX_RETRY_AFTER_MS = 2L * 60L * 60_000L

        /** Espaciado mínimo entre requests por provider. */
        private val MIN_SPACING_MS = mapOf("gemini" to 3_500L, "groq" to 600L)

        private data class CooldownState(val expiry: Long, val strikes: Int)

        private val cooldowns = ConcurrentHashMap<String, CooldownState>()
        private val strikes = ConcurrentHashMap<String, Int>()
        private val rrCounters = ConcurrentHashMap<String, AtomicInteger>()
        private val slotLocks = ConcurrentHashMap<String, Mutex>()
        private val lastRequestAt = ConcurrentHashMap<String, Long>()

        private fun k(providerId: String, apiKey: String) = "$providerId::$apiKey"
        private fun rrCounter(providerId: String) =
            rrCounters.getOrPut(providerId) { AtomicInteger(0) }

        private suspend fun awaitSlot(providerId: String) {
            val spacing = MIN_SPACING_MS[providerId] ?: return
            slotLocks.getOrPut(providerId) { Mutex() }.withLock {
                val wait = (lastRequestAt[providerId] ?: 0L) + spacing - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                lastRequestAt[providerId] = System.currentTimeMillis()
            }
        }

        private fun cooldownExpiry(providerId: String, apiKey: String): Long =
            cooldowns[k(providerId, apiKey)]?.expiry ?: 0L

        fun isOnCooldown(providerId: String, apiKey: String): Boolean {
            val key = k(providerId, apiKey)
            val state = cooldowns[key] ?: return false
            if (System.currentTimeMillis() >= state.expiry) {
                cooldowns.remove(key)   // los strikes se conservan hasta un éxito
                return false
            }
            return true
        }

        /** Marca la key en cooldown escalonado (o según Retry-After). */
        fun recordRateLimit(providerId: String, apiKey: String, retryAfterSec: Long? = null) {
            val key = k(providerId, apiKey)
            val n = (strikes[key] ?: 0)
            strikes[key] = n + 1
            val stepMs = COOLDOWN_STEPS_MS[n.coerceAtMost(COOLDOWN_STEPS_MS.lastIndex)]
            val ms = if (retryAfterSec != null && retryAfterSec > 0)
                maxOf(retryAfterSec * 1000L, 5_000L).coerceAtMost(MAX_RETRY_AFTER_MS)
            else stepMs
            cooldowns[key] = CooldownState(System.currentTimeMillis() + ms, n + 1)
            Log.d(TAG, "Key $providerId/${apiKey.take(6)}… cooldown ${ms / 1000}s (strike ${n + 1})")
        }

        fun recordSuccess(providerId: String, apiKey: String) {
            val key = k(providerId, apiKey)
            cooldowns.remove(key)
            strikes.remove(key)
        }

        // Compat con llamadas anteriores.
        fun markOnCooldown(providerId: String, apiKey: String) = recordRateLimit(providerId, apiKey)
        fun clearCooldown(providerId: String, apiKey: String) = recordSuccess(providerId, apiKey)

        /** Providers finales: Gemini y Groq. */
        fun default(): ProviderRouter = ProviderRouter(listOf(GeminiProvider(), GroqProvider()))
    }
}
