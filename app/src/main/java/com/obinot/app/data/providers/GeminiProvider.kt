package com.obinot.app.data.providers

import com.obinot.app.data.Content
import com.obinot.app.data.FileData
import com.obinot.app.data.GenerateContentRequest
import com.obinot.app.data.Part
import com.obinot.app.data.RetrofitClient
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Provider de Google Gemini.
 *
 * Ventajas: ventana de contexto gigante (no tiene límite práctico de
 * tamaño de archivo para audio), buen soporte de LaTeX y Mermaid.
 *
 * Desventajas: más lento que Groq, cuota diaria limitada (1.000 req/día
 * para Flash-Lite en el free tier).
 *
 * Modelos:
 *   - gemini-3.8-flash: calidad alta, largo contexto, transcripción.
 *   - gemini-3.5-flash-lite: rápido y barato, ideal para títulos y chat.
 *
 * El orden de la lista importa: el router itera en orden. Ponemos primero
 * el Flash (calidad) porque la mayoría de las tareas que caen a Gemini son
 * de procesamiento/análisis, no de chat rápido.
 */
class GeminiProvider : AiProvider() {

    override val id: String = PROVIDER_ID
    override val displayName: String = "Google Gemini"
    override val requiresApiKey: Boolean = true
    override val supportsKeyRotation: Boolean = true

    override val models: List<ProviderModel> = listOf(
        ProviderModel(
            id = "gemini-3.8-flash",
            displayName = "Gemini Flash",
            capabilities = setOf(
                ProviderCapability.QUALITY,
                ProviderCapability.LONG_CONTEXT,
                ProviderCapability.TRANSCRIPTION
            )
        ),
        ProviderModel(
            id = "gemini-3.5-flash-lite",
            displayName = "Gemini Flash-Lite",
            capabilities = setOf(
                ProviderCapability.FAST,
                ProviderCapability.TITLE,
                ProviderCapability.CHAT
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
        val request = GenerateContentRequest(
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            contents = listOf(Content(parts = listOf(Part(text = userPrompt))))
        )
        val response = RetrofitClient.service.generateContent(
            model = model.id,
            apiKey = apiKey,
            request = request
        )
        return response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
    }

    override suspend fun transcribe(
        apiKey: String,
        model: ProviderModel,
        audioFile: File
    ): String? {
        val mimeType = "audio/mp4"

        // 1) Subir el archivo al server de Google.
        val requestBody = audioFile.asRequestBody(mimeType.toMediaTypeOrNull())
        val uploadResponse = RetrofitClient.service.uploadFile(
            apiKey = apiKey,
            contentLength = audioFile.length(),
            contentType = mimeType,
            mimeType = mimeType,
            fileBytes = requestBody
        )
        val uploaded = uploadResponse.file
            ?: throw IllegalStateException("Failed to upload file to Gemini server.")

        try {
            // 2) Esperar a que Google termine de procesar el archivo.
            var fileState = uploaded.state
            var attempts = 0
            while (fileState == "PROCESSING" && attempts < 60) {
                delay(3000)
                fileState = RetrofitClient.service.getFile(uploaded.name, apiKey).state
                attempts++
            }
            if (fileState != "ACTIVE") {
                throw IllegalStateException("File processing timeout or failed at Google server.")
            }

            // 3) Pedir la transcripción.
            val systemPrompt = TRANSCRIPTION_PROMPT
            val request = GenerateContentRequest(
                systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(
                                fileData = FileData(
                                    mimeType = mimeType,
                                    fileUri = uploaded.uri
                                )
                            )
                        )
                    )
                )
            )
            val response = RetrofitClient.service.generateContent(
                model = model.id,
                apiKey = apiKey,
                request = request
            )
            return response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        } finally {
            // 4) Limpiar el archivo remoto. Best-effort.
            try {
                RetrofitClient.service.deleteFile(uploaded.name, apiKey)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val PROVIDER_ID = "gemini"

        /**
         * Prompt de transcripción verbatim. Reusa el mismo que tenía el
         * ResultViewModel antes del refactor. Es específico de Gemini
         * porque Groq usa Whisper (que no acepta system prompt) y NVIDIA
         * no transcribe.
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