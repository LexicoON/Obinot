package com.obinot.app.data.providers

/**
 * Capacidades que un modelo puede ofrecer.
 *
 * El ProviderRouter las usa para armar dinámicamente qué modelos son
 * candidatos para cada tarea. Si agregás un modelo nuevo a un provider,
 * simplemente declarás sus capabilities y el router lo empieza a considerar
 * sin tocar el resto del código.
 *
 * Mapeo típico task → capability:
 *   - Título de nota                → TITLE
 *   - Chat sobre la nota            → CHAT
 *   - Explicar término seleccionado → FAST (o QUALITY si querés más detalle)
 *   - Procesamiento de texto        → QUALITY
 *   - Transcripción de audio        → TRANSCRIPTION
 *   - Contexto muy largo            → LONG_CONTEXT
 */
enum class ProviderCapability {
    /** Modelos rápidos y baratos. Títulos, chat corto, explicaciones breves. */
    FAST,

    /** Modelos más grandes. Análisis, procesamiento de texto, resúmenes. */
    QUALITY,

    /** Ventana de contexto muy grande (audios/textos largos). */
    LONG_CONTEXT,

    /** Capacidad de transcribir audio a texto. */
    TRANSCRIPTION,

    /** Modelos aptos para chat conversacional multi-turno. */
    CHAT,

    /** Modelos aptos para generar títulos cortos de 3-5 palabras. */
    TITLE
}