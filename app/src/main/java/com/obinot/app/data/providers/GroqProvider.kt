package com.obinot.app.data.providers

import com.obinot.app.data.GroqChatRequest
import com.obinot.app.data.GroqMessage
import com.obinot.app.data.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * Provider de Groq.
 *
 * Ventajas: velocidad excepcional (especialmente Whisper Large v3 Turbo
 * para transcripción). Free tier generoso.
 *
 * Desventajas: límite duro de 25 MB por archivo de audio (el caller tiene
 * que comprimir antes si excede). Sin soporte de LaTeX tan bueno como Gemini.
 *
 * Modelos:
 *   - openai/gpt-oss-120b: calidad alta, largo contexto.
 *   - openai/gpt-oss-20b: rápido y barato, títulos y chat.
 *   - whisper-large-v3-turbo: transcripción de audio.
 *
 * El orden importa. Ponemos el 120B primero para que las tareas de
 * procesamiento caigan ahí; el 20B queda para títulos y chat.
 */
class GroqProvider : AiProvider() {

    override val id: String = PROVIDER_ID
    override val displayName: String = "Groq AI"
    override val requiresApiKey: Boolean = true
    override val supportsKeyRotation: Boolean = true

    override val models: List<ProviderModel> = listOf(
        ProviderModel(
            id = "openai/gpt-oss-120b",
            displayName = "GPT-OSS 120B",
            capabilities = setOf(
                ProviderCapability.QUALITY,
                ProviderCapability.LONG_CONTEXT
            )
        ),
        ProviderModel(
            id = "openai/gpt-oss-20b",
            displayName = "GPT-OSS 20B",
            capabilities = setOf(
                ProviderCapability.FAST,
                ProviderCapability.TITLE,
                ProviderCapability.CHAT
            )
        ),
        ProviderModel(
            id = "whisper-large-v3-turbo",
            displayName = "Whisper Large v3 Turbo",
            capabilities = setOf(
                ProviderCapability.TRANSCRIPTION
            )
        )
    )

    override suspend fun generateText(
        apiKey: String,
        model: ProviderModel,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int?
    ): String? {
        val request = GroqChatRequest(
            model = model.id,
            messages = listOf(
                GroqMessage(role = "system", content = systemPrompt),
                GroqMessage(role = "user", content = userPrompt)
            )
        )
        val response = RetrofitClient.groqService.generateContent("Bearer $apiKey", request)
        return response.choices?.firstOrNull()?.message?.content?.trim()
    }

    override suspend fun transcribe(
        apiKey: String,
        model: ProviderModel,
        audioFile: File
    ): String? {
        // Groq tiene un límite duro de 25 MB. El caller (ViewModel) tiene
        // que comprimir antes. Acá solo chequeamos y fallamos temprano con
        // un mensaje claro, para que el router pueda hacer failover a Gemini.
        if (audioFile.length() > MAX_AUDIO_BYTES) {
            throw IllegalArgumentException(
                "Audio exceeds Groq's 25MB limit (${audioFile.length()} bytes). " +
                "Caller must compress before calling GroqProvider.transcribe."
            )
        }

        val requestFile = audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
        val modelBody = model.id.toRequestBody("text/plain".toMediaTypeOrNull())
        val formatBody = "json".toRequestBody("text/plain".toMediaTypeOrNull())

        val response = RetrofitClient.groqService.transcribeAudio(
            authHeader = "Bearer $apiKey",
            file = body,
            model = modelBody,
            responseFormat = formatBody
        )
        return response.text?.trim()
    }

    companion object {
        const val PROVIDER_ID = "groq"

        /** Límite duro de Groq: 25 MB. Dejamos un margen por headers. */
        private const val MAX_AUDIO_BYTES = 24L * 1024 * 1024
    }
}