package com.obinot.app.data.providers

import java.io.File

/**
 * Contrato que todo provider de IA tiene que cumplir.
 *
 * Los providers son puros: no leen strings de recursos, no tocan DataStore,
 * no navegan. Solo reciben una api key y los datos, hacen la request, y
 * devuelven texto o tiran excepción.
 *
 * El manejo de errores de red queda afuera: Retrofit tira HttpException en
 * respuestas non-2xx y IOException en fallos de red. El router los captura
 * y decide si reintentar con otro provider.
 *
 * IMPORTANTE: el método transcribe() devuelve null cuando el provider
 * "legítimamente" no puede transcribir (ej: NVIDIA no tiene modelo de
 * transcripción). Distinto es tirar excepción, que significa "fallé al
 * intentar". El router usa esta distinción para decidir si vale la pena
 * probar otro provider con la misma capability.
 */
abstract class AiProvider {

    /** Identificador único, estable. Ej: "gemini", "groq", "nvidia". */
    abstract val id: String

    /** Nombre legible para la UI. Ej: "Google Gemini". */
    abstract val displayName: String

    /** true si el provider no funciona sin API key. */
    abstract val requiresApiKey: Boolean

    /**
     * true si el provider soporta múltiples API keys con failover.
     * La rotación en sí la implementa el router en Release 2; este flag
     * solo declara la intención. Los tres providers actuales la soportan.
     */
    abstract val supportsKeyRotation: Boolean

    /**
     * Lista de modelos, en orden de preferencia. El router itera en orden
     * y usa el primer modelo que tenga la capability pedida.
     */
    abstract val models: List<ProviderModel>

    /** Devuelve solo los modelos que ofrecen [capability], en orden. */
    fun modelsFor(capability: ProviderCapability): List<ProviderModel> =
        models.filter { it.supports(capability) }

    /** true si al menos un modelo del provider soporta [capability]. */
    fun supports(capability: ProviderCapability): Boolean =
        models.any { it.supports(capability) }

    /**
     * Ejecuta una tarea de texto (título, chat, explicación, procesamiento).
     *
     * [systemPrompt] y [userPrompt] los arma el caller. El provider solo
     * los mapea al formato de su API.
     *
     * [maxOutputTokens] es opcional. Si es null, el provider usa el default
     * de su API.
     *
     * Devuelve el texto plano de la respuesta, o null si la respuesta vino
     * vacía. Tira HttpException en errores HTTP (4xx/5xx) e IOException en
     * fallos de red.
     */
    abstract suspend fun generateText(
        apiKey: String,
        model: ProviderModel,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int? = null
    ): String?

    /**
     * Transcribe un archivo de audio.
     *
     * Devuelve el texto transcrito, o null si el provider no soporta
     * transcripción o la respuesta vino vacía.
     *
     * [audioFile] es el archivo local listo para subir. El caller se
     * encarga de comprimirlo si excede el límite del provider.
     *
     * Tira HttpException en errores HTTP (413 = muy grande, 429 = rate
     * limit, etc.) e IOException en fallos de red.
     */
    abstract suspend fun transcribe(
        apiKey: String,
        model: ProviderModel,
        audioFile: File
    ): String?
}