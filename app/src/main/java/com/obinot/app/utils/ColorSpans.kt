package com.obinot.app.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * Colores de texto por rol de la paleta Material (primary/secondary/tertiary/error).
 *
 * La IA los escribe como {c:primary}frase{/c}. Al guardar la nota la app
 * EXTRAE las etiquetas: el summary queda limpio (Markdown estándar, así el
 * export .md y los backups no cambian) y los colores se guardan aparte en
 * NoteEntity.colorSpans como JSON [{"text":"...","role":"primary"}].
 * El renderer pinta cada frase con el color del tema actual.
 */
object ColorSpans {

    data class Span(val text: String, val role: String)

    val ROLES = setOf("primary", "secondary", "tertiary", "error")

    private val TAG = Regex("\\{c:(primary|secondary|tertiary|error)\\}(.+?)\\{/c\\}", RegexOption.DOT_MATCHES_ALL)
    private val LEFTOVER = Regex("\\{/?c(:\\w+)?\\}")
    private val MD_MARKERS = Regex("[*_`]")

    const val PROMPT_HINT = """
OPTIONAL TEXT COLORS: to emphasize, wrap a few key phrases as {c:ROLE}phrase{/c}, with ROLE one of:
primary = key concepts, secondary = names / definitions, tertiary = numbers / dates / data, error = warnings / risks.
Rules: at most ~8 colored phrases per note, only short phrases (never whole paragraphs), never inside headings,
code, tables or math, and the phrase inside the tag must be plain text (no *, _ or ` inside). Everything else stays uncolored.
"""

    /** Devuelve (texto limpio, JSON de spans o null si no hubo colores). */
    fun extract(text: String): Pair<String, String?> {
        val spans = LinkedHashMap<String, String>()
        var clean = TAG.replace(text) { m ->
            val role = m.groupValues[1]
            val inner = m.groupValues[2]
            val needle = inner.replace(MD_MARKERS, "").trim()
            if (needle.length in 2..80 && !needle.contains('\n')) spans.putIfAbsent(needle, role)
            inner
        }
        clean = LEFTOVER.replace(clean, "")
        if (spans.isEmpty()) return clean to null
        val arr = JSONArray()
        spans.forEach { (t, r) -> arr.put(JSONObject().put("text", t).put("role", r)) }
        return clean to arr.toString()
    }

    fun parse(json: String?): List<Span> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull {
                val o = arr.getJSONObject(it)
                val t = o.optString("text"); val r = o.optString("role")
                if (t.isNotEmpty() && r in ROLES) Span(t, r) else null
            }
        } catch (_: Exception) { emptyList() }
    }
}
