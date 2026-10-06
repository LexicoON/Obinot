package com.obinot.app.data

import kotlin.math.ln

/**
 * Chunking de texto + ranking BM25 en Kotlin.
 *
 * Se usa para armar el contexto del chat: en vez de mandar el rawText
 * entero (que puede disparar 413 Payload Too Large), se divide en chunks
 * y se mandan solo los top-N más relevantes al último mensaje del user.
 *
 * BM25 (Okapi) es la fórmula de ranking clásica:
 *
 *   score(q, d) = Σ IDF(qi) · [tf(qi, d) · (k1 + 1)] / [tf(qi, d) + k1 · (1 - b + b · |d| / avgdl)]
 *
 *   donde IDF(qi) = ln(1 + (N - df(qi) + 0.5) / (df(qi) + 0.5))
 *
 * Parámetros estándar: k1 = 1.2 (saturación de término), b = 0.75
 * (normalización por longitud). No los exponemos porque los defaults
 * funcionan bien para texto corto en idiomas naturales.
 *
 * Nota de alcance: esta implementación NO pretende reemplazar a un motor
 * de búsqueda. Es deliberadamente simple y suficiente para rankear 5-50
 * chunks de una sola nota contra una query de 3-15 palabras.
 */
object TextChunker {

    /** Un fragmento de texto con su rango de caracteres en el original. */
    data class Chunk(
        val text: String,
        val startChar: Int,
        val endChar: Int
    )

    private const val DEFAULT_CHUNK_SIZE = 800
    private const val DEFAULT_OVERLAP = 100
    private const val DEFAULT_TOP_N = 5

    private const val K1 = 1.2
    private const val B = 0.75

    /**
     * Divide [text] en chunks de ~[chunkSize] caracteres con [overlap]
     * de solape. El solape sirve para no cortar una oración justo en el
     * límite y perder contexto en el ranking.
     *
     * Casos borde:
     *   - Texto vacío → lista vacía.
     *   - Texto más corto que chunkSize → un solo chunk con todo.
     *   - overlap >= chunkSize → se fuerza a chunkSize/2 para evitar loop.
     */
    fun chunk(
        text: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        overlap: Int = DEFAULT_OVERLAP
    ): List<Chunk> {
        if (text.isEmpty()) return emptyList()
        if (text.length <= chunkSize) return listOf(Chunk(text, 0, text.length))

        // Guarda contra loop infinito si overlap >= chunkSize.
        val effectiveOverlap = overlap.coerceAtMost(chunkSize / 2)
        val chunks = mutableListOf<Chunk>()
        var start = 0
        while (start < text.length) {
            val end = (start + chunkSize).coerceAtMost(text.length)
            chunks.add(Chunk(text.substring(start, end), start, end))
            if (end >= text.length) break
            start = end - effectiveOverlap
        }
        return chunks
    }

    /**
     * Devuelve los top-[topN] chunks con mayor score BM25 contra [query].
     *
     * Si la query está vacía o no matchea ningún término, devuelve los
     * primeros [topN] chunks (fallback razonable: las primeras secciones
     * de la nota).
     *
     * El orden devuelto es el de relevancia descendente. El caller puede
     * reordenarlos por startChar si quiere preservar el orden del documento.
     */
    fun topChunksByBm25(
        chunks: List<Chunk>,
        query: String,
        topN: Int = DEFAULT_TOP_N
    ): List<Chunk> {
        if (chunks.isEmpty()) return emptyList()
        if (chunks.size <= topN) return chunks

        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return chunks.take(topN)

        val chunkTokens = chunks.map { tokenize(it.text) }
        val n = chunks.size.toDouble()
        val avgLen = chunkTokens.map { it.size }.average().takeIf { it > 0.0 } ?: 1.0

        // Document frequency por término: en cuántos chunks aparece.
        val docFreq = mutableMapOf<String, Int>()
        queryTerms.forEach { term ->
            var df = 0
            chunkTokens.forEach { tokens ->
                if (tokens.any { it == term }) df++
            }
            docFreq[term] = df
        }

        val scores = chunks.indices.map { i ->
            val tokens = chunkTokens[i]
            val docLen = tokens.size.toDouble()
            var score = 0.0
            queryTerms.forEach { term ->
                val tf = tokens.count { it == term }.toDouble()
                if (tf > 0.0) {
                    val df = (docFreq[term] ?: 0).toDouble()
                    val idf = ln(1.0 + (n - df + 0.5) / (df + 0.5))
                    val norm = 1.0 - B + B * (docLen / avgLen)
                    score += idf * (tf * (K1 + 1.0)) / (tf + K1 * norm)
                }
            }
            i to score
        }

        val ranked = scores
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(topN)
            .map { chunks[it.first] }

        // Fallback: si ningún chunk matcheó, devolvemos los primeros topN
        // (comportamiento previo al BM25: primeras secciones de la nota).
        return ranked.ifEmpty { chunks.take(topN) }
    }

    /**
     * Tokeniza texto en términos normalizados: lowercase + split por
     * cualquier cosa que no sea letra/número (Unicode-aware, así funciona
     * con acentos y caracteres no-latinos).
     *
     * Ej: "Hola, mundo! ¿Cómo estás?" → ["hola", "mundo", "cómo", "estás"]
     */
    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
}