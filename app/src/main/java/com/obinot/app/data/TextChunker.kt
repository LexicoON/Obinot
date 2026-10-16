package com.obinot.app.data

import kotlin.math.ln

/**
 * Chunking de texto + ranking BM25 en Kotlin.
 *
 * Se usa para:
 *   1) Armar el contexto del chat (topChunksByBm25).
 *   2) Re-rankear los resultados de búsqueda de History (rankByBm25).
 *
 * BM25 (Okapi) es la fórmula de ranking clásica:
 *
 *   score(q, d) = Σ IDF(qi) · [tf(qi, d) · (k1 + 1)] / [tf(qi, d) + k1 · (1 - b + b · |d| / avgdl)]
 *
 * Parámetros estándar: k1 = 1.2 (saturación de término), b = 0.75
 * (normalización por longitud).
 */
object TextChunker {

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

    fun chunk(
        text: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        overlap: Int = DEFAULT_OVERLAP
    ): List<Chunk> {
        if (text.isEmpty()) return emptyList()
        if (text.length <= chunkSize) return listOf(Chunk(text, 0, text.length))

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

        return ranked.ifEmpty { chunks.take(topN) }
    }

    /**
     * Rankea una lista genérica de items por BM25 contra [query].
     *
     * [documentOf] extrae el texto buscable de cada item. El caller decide
     * qué campos concatenar (ej: title + summary + rawText).
     *
     * Devuelve los items ordenados por score descendente. Si la query está
     * vacía o no hay matches, devuelve la lista original sin tocar.
     *
     * Se usa en HistoryViewModel para re-rankear los resultados de FTS4
     * (que solo filtra por MATCH, sin scoring). El costo es O(N · T) donde
     * N = número de candidatos y T = términos de la query. Para búsquedas
     * típicas (N < 100, T < 5) es instantáneo.
     */
    fun <T> rankByBm25(
        items: List<T>,
        query: String,
        documentOf: (T) -> String
    ): List<T> {
        if (items.isEmpty()) return items
        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return items

        val itemTokens = items.map { tokenize(documentOf(it)) }
        val n = items.size.toDouble()
        val avgLen = itemTokens.map { it.size }.average().takeIf { it > 0.0 } ?: 1.0

        val docFreq = mutableMapOf<String, Int>()
        queryTerms.forEach { term ->
            var df = 0
            itemTokens.forEach { tokens ->
                if (tokens.any { it == term }) df++
            }
            docFreq[term] = df
        }

        val scored = items.mapIndexed { i, item ->
            val tokens = itemTokens[i]
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
            item to score
        }

        return scored.sortedByDescending { it.second }.map { it.first }
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
}