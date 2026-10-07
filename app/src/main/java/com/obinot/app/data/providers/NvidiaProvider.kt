package com.obinot.app.data.providers

import com.obinot.app.data.GroqChatRequest
import com.obinot.app.data.GroqChatResponse
import com.obinot.app.data.GroqMessage
import com.obinot.app.data.RetrofitClient
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Provider de NVIDIA NIM.
 *
 * NVIDIA NIM expone una API OpenAI-compatible en integrate.api.nvidia.com.
 * El formato de request/response es idéntico al de Groq, así que reusamos
 * los data classes GroqChatRequest / GroqChatResponse / GroqMessage.
 *
 * Ventajas: 40 RPM por modelo (no por cuenta), sin data training.
 * Desventajas: no tiene modelo de transcripción de audio. Solo texto.
 *
 * Modelos:
 *   - meta/llama-3.3-70b-instruct: calidad alta, largo contexto.
 *   - meta/llama-4-maverick-17b-128e-instruct: rápido, títulos y chat.
 *
 * IMPORTANTE: NVIDIA NIM solo se habilita en modo Full (beta). En modo
 * Standard el router no lo incluye. Eso lo maneja el SettingsRepository
 * (que no expone la key hasta que el user activa Full), no el provider.
 */
class NvidiaProvider : AiProvider() {

    override val id: String = PROVIDER_ID
    override val displayName: String = "NVIDIA NIM"
    override val requiresApiKey: Boolean = true
    override val supportsKeyRotation: Boolean = true

    override val models: List<ProviderModel> = listOf(
        ProviderModel(
            id = "meta/llama-3.2-90b-vision-instruct",
            displayName = "Llama 3.2 90B Vision",
            capabilities = setOf(
                ProviderCapability.QUALITY,
                ProviderCapability.LONG_CONTEXT
            )
        ),
        ProviderModel(
            id = "meta/llama-3.2-11b-vision-instruct",
            displayName = "Llama 3.2 11B Vision",
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
        val request = GroqChatRequest(
            model = model.id,
            messages = listOf(
                GroqMessage(role = "system", content = systemPrompt),
                GroqMessage(role = "user", content = userPrompt)
            )
        )
        val response = NvidiaRetrofitClient.service.generateContent("Bearer $apiKey", request)
        return response.choices?.firstOrNull()?.message?.content?.trim()
    }

    override suspend fun transcribe(
        apiKey: String,
        model: ProviderModel,
        audioFile: File
    ): String? {
        // NVIDIA NIM no ofrece transcripción de audio. Devolvemos null
        // (no tirar excepción) para que el router sepa que "este provider
        // no aplica a esta capability" en vez de "este provider falló".
        // En la práctica el router nunca va a llamar transcribe() acá
        // porque NvidiaProvider no declara la capability TRANSCRIPTION,
        // pero por las dudas.
        return null
    }

    companion object {
        const val PROVIDER_ID = "nvidia"
    }
}

/**
 * Retrofit service para NVIDIA NIM. OpenRouter-style: paths relativos
 * a /v1/, header Authorization estándar, body OpenAI-compatible.
 */
interface NvidiaApiService {
    @POST("chat/completions")
    suspend fun generateContent(
        @Header("Authorization") authHeader: String,
        @Body request: GroqChatRequest
    ): GroqChatResponse
}

/**
 * Cliente Retrofit dedicado a NVIDIA. Reusa el Moshi de RetrofitClient
 * (con KotlinJsonAdapterFactory) para no duplicar configuración.
 */
object NvidiaRetrofitClient {
    private const val BASE_URL = "https://integrate.api.nvidia.com/v1/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    val service: NvidiaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(RetrofitClient.moshi))
            .build()
            .create(NvidiaApiService::class.java)
    }
}