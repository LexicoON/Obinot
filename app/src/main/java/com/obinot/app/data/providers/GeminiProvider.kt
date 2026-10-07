package com.obinot.app.data.providers

import android.util.Log
import com.obinot.app.data.Content
import com.obinot.app.data.FileData
import com.obinot.app.data.GenerateContentRequest
import com.obinot.app.data.GenerateContentResponse
import com.obinot.app.data.GeminiFile
import com.obinot.app.data.Part
import com.obinot.app.data.RetrofitClient
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException

/**
 * Provider de Google Gemini, blindado contra fallos transitorios.
 *
 * Capas de protección:
 *  1. Modelo único: gemini-3.5-flash-lite. Evita el 503 estructural que
 *     tenía gemini-3.8-flash (que está sobrecargado la mayor parte del día).
 *  2. Retry con backoff exponencial (1s, 2s, 4s, 8s) en 429/500/502/503/504.
 *  3. Upload con retry propio en errores de red (hasta 3 intentos).
 *  4. Polling adaptativo del file state: 2s → 4s → 8s, 280s máximo.
 *  5. Detección de File FAILED y de respuesta vacía como errores transitorios.
 *
 * Si todos los reintentos internos se agotan, la excepción llega al
 * ProviderRouter, que hace fallback automático a Groq (o NVIDIA en modo Full).
 *
 * Logs: con tag "GeminiProvider". Filtrable con:
 *   adb logcat -s GeminiProvider:D
 */
class GeminiProvider : AiProvider() {

    override val id: String = PROVIDER_ID
    override val displayName: String = "Google Gemini"
    override val requiresApiKey: Boolean = true
    override val supportsKeyRotation: Boolean = true

    override val models: List<ProviderModel> = listOf(
        ProviderModel(
            id = MODEL_FLASH_LITE,
            displayName = "Gemini Flash-Lite",
            capabilities = setOf(
                ProviderCapability.FAST,
                ProviderCapability.QUALITY,
                ProviderCapability.LONG_CONTEXT,
                ProviderCapability.TRANSCRIPTION,
                ProviderCapability.CHAT,
                ProviderCapability.TITLE
            )
        )
    )

    override suspend fun generateText(
        apiKey: String,
        model: ProviderModel,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int?
    ): String? = withContentRetries {
        val request = GenerateContentRequest(
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            contents = listOf(Content(parts = listOf(Part(text = userPrompt))))
        )
        val response = RetrofitClient.service.generateContent(
            model = model.id,
            apiKey = apiKey,
            request = request
        )
        extractTextOrThrow(response)
    }

    override suspend fun transcribe(
        apiKey: String,
        model: ProviderModel,
        audioFile: File
    ): String? {
        if (audioFile.length() == 0L) {
            throw IOException("Audio file is empty: ${audioFile.absolutePath}")
        }

        val mimeType = "audio/mp4"
        val uploaded = uploadWithRetry(apiKey, audioFile, mimeType)

        try {
            waitForFileActive(apiKey, uploaded.name)
            return withContentRetries {
                val request = GenerateContentRequest(
                    systemInstruction = Content(parts = listOf(Part(text = TRANSCRIPTION_PROMPT))),
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(fileData = FileData(mimeType = mimeType, fileUri = uploaded.uri))
                            )
                        )
                    )
                )
                val response = RetrofitClient.service.generateContent(
                    model = model.id,
                    apiKey = apiKey,
                    request = request
                )
                extractTextOrThrow(response)
            }
        } finally {
            deleteRemoteFile(apiKey, uploaded.name)
        }
    }

    // ============================================================
    // Internals
    // ============================================================

    /**
     * Extrae el texto de la respuesta de Gemini. Tira excepción si:
     *  - El body trae un error explícito (permanente, no retriable).
     *  - No hay candidates (transitorio, retriable).
     *  - El texto vino vacío/blank (transitorio, retriable).
     */
    private fun extractTextOrThrow(response: GenerateContentResponse): String {
        response.error?.let { err ->
            throw RuntimeException("Gemini API error: ${err.message ?: "unknown"}")
        }
        val candidates = response.candidates
        if (candidates.isNullOrEmpty()) {
            throw TransientApiException("Gemini returned no candidates")
        }
        val text = candidates.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?.trim()
        if (text.isNullOrBlank()) {
            throw TransientApiException("Gemini returned empty text")
        }
        return text
    }

    /**
     * Sube el archivo con retry en errores de red y 5xx. Hasta 3 intentos
     * (1 inicial + 2 retries), esperando 2s y 5s entre intentos.
     *
     * Reintentar el upload en errores de red es importante porque un
     * upload de 20MB puede tardar 30-60s en 4G y una caída de red
     * transitoria no debería mandar todo al fallback.
     */
    private suspend fun uploadWithRetry(
        apiKey: String,
        audioFile: File,
        mimeType: String
    ): GeminiFile {
        val delays = longArrayOf(2_000L, 5_000L)
        var attempt = 0
        while (true) {
            try {
                return performUpload(apiKey, audioFile, mimeType)
            } catch (e: HttpException) {
                if (e.code() !in RETRYABLE_HTTP_CODES || attempt >= delays.size) throw e
                Log.w(TAG, "Upload HTTP ${e.code()} on attempt ${attempt + 1}, retrying in ${delays[attempt]}ms")
                delay(delays[attempt])
                attempt++
            } catch (e: IOException) {
                if (attempt >= delays.size) throw e
                Log.w(TAG, "Upload network error on attempt ${attempt + 1}, retrying in ${delays[attempt]}ms", e)
                delay(delays[attempt])
                attempt++
            }
        }
    }

    private suspend fun performUpload(
        apiKey: String,
        audioFile: File,
        mimeType: String
    ): GeminiFile {
        val requestBody = audioFile.asRequestBody(mimeType.toMediaTypeOrNull())
        val uploadResponse = RetrofitClient.service.uploadFile(
            apiKey = apiKey,
            contentLength = audioFile.length(),
            contentType = mimeType,
            mimeType = mimeType,
            fileBytes = requestBody
        )
        return uploadResponse.file
            ?: throw IOException("Gemini upload returned no file reference (${uploadResponse.error?.message ?: "unknown"})")
    }

    /**
     * Polling adaptativo del estado del archivo hasta que esté ACTIVE.
     *
     * Intervalos: 20 polls × 2s + 20 polls × 4s + 20 polls × 8s.
     * Total máximo: 280s (~4:40). Si después de eso sigue PROCESSING,
     * tiramos excepción transitoria.
     *
     * Si el estado es FAILED o cualquier otro que no sea PROCESSING/ACTIVE,
     * tira TransientApiException para que el router haga fallback.
     *
     * El log solo emite cada 10 polls para no spamear.
     */
    private suspend fun waitForFileActive(apiKey: String, fileName: String) {
        val pollDelays = buildList {
            repeat(20) { add(2_000L) }
            repeat(20) { add(4_000L) }
            repeat(20) { add(8_000L) }
        }

        var lastState = "PROCESSING"
        pollDelays.forEachIndexed { index, delayMs ->
            delay(delayMs)
            lastState = RetrofitClient.service.getFile(fileName, apiKey).state
            when (lastState) {
                "ACTIVE" -> {
                    Log.d(TAG, "File became ACTIVE after ${index + 1} polls")
                    return
                }
                "PROCESSING" -> {
                    if ((index + 1) % 10 == 0) {
                        Log.d(TAG, "File still PROCESSING after ${index + 1} polls")
                    }
                }
                else -> {
                    throw TransientApiException("File processing ended with state: $lastState")
                }
            }
        }

        throw TransientApiException("File processing timed out after ${pollDelays.size} polls (last state: $lastState)")
    }

    /**
     * Delete best-effort del archivo remoto. No propaga errores: si falla,
     * Google eventualmente lo limpia solo.
     */
    private suspend fun deleteRemoteFile(apiKey: String, fileName: String) {
        try {
            RetrofitClient.service.deleteFile(fileName, apiKey)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to delete remote file $fileName (non-fatal)", e)
        }
    }

    /**
     * Retry con backoff exponencial para llamadas a generateContent.
     *
     * Delays: 1s, 2s, 4s, 8s. Total: 5 intentos.
     *
     * Reintenta en:
     *  - TransientApiException (empty candidates, empty text, etc.)
     *  - HttpException con código en RETRYABLE_HTTP_CODES.
     *
     * NO reintenta en 400/401/403/413 ni en errores de red puros: para esos,
     * el router hace fallback inmediato sin esperar 15s.
     */
    private suspend fun <T> withContentRetries(block: suspend () -> T): T {
        val delays = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L)
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: TransientApiException) {
                if (attempt >= delays.size) throw e
                Log.w(TAG, "Transient error on attempt ${attempt + 1}, retrying in ${delays[attempt]}ms: ${e.message}")
                delay(delays[attempt])
                attempt++
            } catch (e: HttpException) {
                if (e.code() !in RETRYABLE_HTTP_CODES || attempt >= delays.size) throw e
                Log.w(TAG, "HTTP ${e.code()} on attempt ${attempt + 1}, retrying in ${delays[attempt]}ms")
                delay(delays[attempt])
                attempt++
            }
        }
    }

    companion object {
        const val PROVIDER_ID = "gemini"

        private const val TAG = "GeminiProvider"
        private const val MODEL_FLASH_LITE = "gemini-3.5-flash-lite"

        /**
         * Códigos HTTP reintentables.
         *   429: rate limit — se resuelve esperando.
         *   500, 502, 503, 504: errores de infra de Google, típicamente transitorios.
         *   501 NO va: significa que pedimos algo que el server no implementa.
         */
        private val RETRYABLE_HTTP_CODES = setOf(429, 500, 502, 503, 504)

        /**
         * Prompt de transcripción verbatim. Reusa el mismo que tenía el
         * ResultViewModel antes del refactor de providers.
         */
        private val TRANSCRIPTION_PROMPT = """
            You are a highly accurate audio transcription AI. Your ONLY task is to transcribe the audio exactly word-for-word.

            CRITICAL STRICT RULES:
            1. NO HALLUCINATION: If the audio is silent, output exactly "[No speech detected]".
            2. VERBATIM TRANSCRIBE: Transcribe exactly what is spoken word-by-word, including informal words, repeated words, and natural speech flow.
            3. KEEP PUNCTUATION & CAPITALIZATION: You MUST add accurate punctuation (periods, commas, question marks) and use proper capitalization to make it readable.
            4. NO GRAMMAR CORRECTION: Absolutely DO NOT fix the speaker's grammatical errors or restructure their sentences.
            5. NO MARKDOWN & NO MATH FORMATTING: DO NOT add Markdown styling. DO NOT convert spoken math, numbers, or symbols into LaTeX format. Write them as plain text.
            6. Automatically detect and transcribe in the spoken language.
        """.trimIndent()
    }
}

/**
 * Excepción para errores transitorios que ameritan retry.
 *
 * Extiende IOException a propósito: el ProviderRouter trata IOException
 * como retryable, así que si nuestros reintentos internos se agotan, el
 * router igual hace fallback a otro provider en vez de propagar el error
 * crudo al user.
 */
private class TransientApiException(message: String) : IOException(message)