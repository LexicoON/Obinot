package com.obinot.app.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.ratex.RaTeXView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

// ============================================================
// Data types
// ============================================================

data class HighlightItem(
    val text: String,
    val note: String,
    val line: Int = -1,
    val start: Int = -1,
    val end: Int = -1
)

data class LineLayoutInfo(
    val layoutResult: TextLayoutResult,
    val boundsInWindow: Rect,
    val renderedText: String,
    val prefixLen: Int
)

sealed class MarkdownItem {
    data class NativeLine(val text: String, val lineIndex: Int) : MarkdownItem()
    data class MathBlock(val rawText: String, val startLineIndex: Int) : MarkdownItem()
    data class MermaidBlock(val rawText: String, val startLineIndex: Int) : MarkdownItem()
    data class CodeBlock(val code: String, val language: String?, val startLineIndex: Int) : MarkdownItem()
    data class Table(val rows: List<List<String>>, val startLineIndex: Int) : MarkdownItem()
}

private fun MarkdownItem.lineKey(): Int = when (this) {
    is MarkdownItem.NativeLine -> lineIndex
    is MarkdownItem.MathBlock -> startLineIndex
    is MarkdownItem.MermaidBlock -> startLineIndex
    is MarkdownItem.CodeBlock -> startLineIndex
    is MarkdownItem.Table -> startLineIndex
}

// ============================================================
// Selection resolution
// ============================================================

private fun resolveRectToPosition(
    rect: Rect,
    selectedText: String,
    registry: Map<Int, LineLayoutInfo>,
    rawLines: List<String>
): Triple<Int, Int, Int>? {
    if (selectedText.isBlank()) return null
    val anchor = Offset(rect.left, rect.center.y)
    val info = registry.values.firstOrNull { it.boundsInWindow.let { b -> anchor.y in b.top..b.bottom } }
        ?: registry.values.minByOrNull { lineInfo ->
            val b = lineInfo.boundsInWindow
            when {
                anchor.y < b.top -> b.top - anchor.y
                anchor.y > b.bottom -> anchor.y - b.bottom
                else -> 0f
            }
        }
        ?: return null

    val lineIndex = registry.entries.firstOrNull { it.value === info }?.key ?: return null
    val rawLine = rawLines.getOrNull(lineIndex) ?: return null

    val localX = (anchor.x - info.boundsInWindow.left).coerceIn(0f, info.boundsInWindow.width)
    val approxLocalOffset = info.layoutResult.getOffsetForPosition(
        Offset(localX, info.boundsInWindow.height / 2f)
    ).coerceIn(0, info.renderedText.length)
    val approxRawOffset = (approxLocalOffset + info.prefixLen).coerceIn(0, rawLine.length)

    val lowerLine = rawLine.lowercase()
    val lowerSel = selectedText.lowercase()
    var bestStart = -1
    var bestDist = Int.MAX_VALUE
    var searchFrom = 0
    while (true) {
        val idx = lowerLine.indexOf(lowerSel, searchFrom)
        if (idx == -1) break
        val dist = kotlin.math.abs(idx - approxRawOffset)
        if (dist < bestDist) {
            bestDist = dist
            bestStart = idx
        }
        searchFrom = idx + 1
    }
    if (bestStart == -1) return null
    return Triple(lineIndex, bestStart, bestStart + selectedText.length)
}

// ============================================================
// Assets
// ============================================================

private object MermaidAssetCache {
    @Volatile private var cache: MermaidAssets? = null
    private val lock = Any()

    fun getMermaid(context: android.content.Context): MermaidAssets {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val assets = MermaidAssets(
                js = try { context.assets.open("mermaid/mermaid.min.js").bufferedReader().readText() } catch (e: Exception) { "" }
            )
            cache = assets
            return assets
        }
    }
}

data class MermaidAssets(val js: String) {
    val isReady get() = js.isNotEmpty()
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val color1 = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val color2 = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)

    val brush = Brush.linearGradient(
        colors = listOf(color1, color2, color1),
        start = Offset(offsetX, 0f),
        end = Offset(offsetX + 400f, 400f)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
    )
}

// ============================================================
// RaTeX block math view
// ============================================================

@Composable
fun RaTeXBlockView(
    latex: String,
    textColor: Color,
    fontSizeDp: Float,
    onCopy: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(latex) {
                detectTapGestures(
                    onLongPress = { onCopy(latex) }
                )
            },
        factory = { ctx ->
            RaTeXView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                displayMode = true
                fontSize = fontSizeDp
                this.latex = latex
                color = textColor.toArgb()
            }
        },
        update = { view ->
            view.fontSize = fontSizeDp
            view.latex = latex
            view.color = textColor.toArgb()
        }
    )
}

// ============================================================
// Inline math — segment splitting & size estimation
// ============================================================

private enum class InlineSegmentKind { TEXT, MATH }

private data class InlineSegment(
    val kind: InlineSegmentKind,
    val text: String
)

/**
 * Divide una línea en segmentos alternando texto y math inline.
 *
 * Detecta `$...$` y `$$...$$` embebidos en texto. Aplica validación mínima
 * al dólar simple para evitar falsos positivos (precios como "$100").
 *
 * Los `$$...$$` se aceptan siempre. Los `$...$` deben cumplir:
 *   - No empezar ni terminar con espacio.
 *   - No contener doble espacio.
 *   - Longitud máxima de 80 caracteres.
 */
private fun splitInlineMathSegments(line: String): List<InlineSegment> {
    val pattern = Regex("""\\\(([^)\n]+?)\\\)|\\\[([^\]\n]+?)\\\]|\$\$([^$\n]+?)\$\$|(?<!\$)\$(?!\$)([^$\n]+?)\$(?!\$)""")
    val segments = mutableListOf<InlineSegment>()
    var cursor = 0

    for (match in pattern.findAll(line)) {
        // 4 posibles grupos: (1) \(...\), (2) \[...\], (3) $$...$$, (4) $...$
        val parenInline = match.groups[1]
        val bracketInline = match.groups[2]
        val doubleDollar = match.groups[3]
        val singleDollar = match.groups[4]

        val content = when {
            parenInline != null -> parenInline.value
            bracketInline != null -> bracketInline.value
            doubleDollar != null -> doubleDollar.value
            singleDollar != null -> singleDollar.value
            else -> continue
        }

        // Validación: para los delimitadores no-$ (que son inequívocos, un
        // humano los escribió a propósito), aceptamos siempre que no estén
        // vacíos. Para $...$ mantenemos las heurísticas anti-falsos-positivos
        // porque "$100" es un precio, no math.
        val isExplicit = parenInline != null || bracketInline != null
        val valid = if (isExplicit || doubleDollar != null) {
            content.isNotBlank()
        } else {
            content.isNotBlank()
                && !content.startsWith(" ")
                && !content.endsWith(" ")
                && !content.contains("  ")
                && content.length <= 80
        }
        if (!valid) continue

        if (match.range.first > cursor) {
            val before = line.substring(cursor, match.range.first)
            if (before.isNotEmpty()) {
                segments.add(InlineSegment(InlineSegmentKind.TEXT, before))
            }
        }
        segments.add(InlineSegment(InlineSegmentKind.MATH, content))
        cursor = match.range.last + 1
    }

    if (cursor < line.length) {
        val after = line.substring(cursor)
        if (after.isNotEmpty()) {
            segments.add(InlineSegment(InlineSegmentKind.TEXT, after))
        }
    }

    return segments.ifEmpty { listOf(InlineSegment(InlineSegmentKind.TEXT, line)) }
}

/**
 * Estima el ancho y alto de una fórmula inline en `em`.
 *
 * Heurística basada en la cantidad de "unidades" del LaTeX. No es perfecta,
 * pero da un placeholder de tamaño razonable para que el `Text` calcule el
 * layout sin tener que esperar a que el `RaTeXView` se mida.
 */
private fun estimateInlineMathSize(latex: String): Pair<Float, Float> {
    var units = 0
    var i = 0
    while (i < latex.length) {
        val c = latex[i]
        when {
            c == '\\' -> {
                i++
                while (i < latex.length && latex[i].isLetter()) i++
                units += 1
            }
            c.isWhitespace() -> i++
            c == '{' || c == '}' -> i++
            else -> {
                units += 1
                i++
            }
        }
    }
    val width = (units * 0.90f).coerceIn(0.9f, 24f)
    val extraHeight = when {
        latex.contains("\\frac") || latex.contains("\\dfrac") || latex.contains("\\tfrac") -> 1.0f
        latex.contains("\\sum") || latex.contains("\\int") || latex.contains("\\prod") -> 0.7f
        latex.contains("\\sqrt") -> 0.5f
        else -> 0.2f
    }
    val height = 1.9f + extraHeight
    return width to height
}

// ============================================================
// Mermaid WebView (unchanged)
// ============================================================

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidWebView(
    mermaidContent: String,
    assets: MermaidAssets,
    isDarkTheme: Boolean,
    heightCache: SnapshotStateMap<String, Int>,
    modifier: Modifier = Modifier
) {
    if (!assets.isReady) return

    val theme = if (isDarkTheme) "dark" else "default"

    val htmlContent = remember(mermaidContent, theme) {
        """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>
body {
    background-color: transparent;
    margin: 0;
    padding: 8px 4px;
    overflow-x: auto;
    overflow-y: hidden;
}
.mermaid-container {
    display: flex;
    justify-content: flex-start;
    min-width: min-content;
}
::-webkit-scrollbar { height: 4px; }
::-webkit-scrollbar-thumb { background: #88888888; border-radius: 4px; }
</style>
</head>
<body>
<div class="mermaid-container" id="mermaid-wrap">
<pre class="mermaid">${mermaidContent.trim().replace("<", "&lt;").replace(">", "&gt;")}</pre>
</div>
<script>${assets.js}</script>
<script>
document.addEventListener("DOMContentLoaded", function() {
    mermaid.initialize({
        startOnLoad: false,
        theme: '$theme',
        securityLevel: 'loose'
    });
    
    var el = document.querySelector('.mermaid');
    var wrap = document.getElementById('mermaid-wrap');
    var rawContent = el ? el.textContent.trim() : '(empty)';
    
    mermaid.run({
        nodes: [el],
        suppressErrors: false
    }).then(function() {
        if (window.ResizeObserver) {
            new ResizeObserver(function(entries) {
                var h = entries[0].target.getBoundingClientRect().height;
                if (window.HeightBridge) window.HeightBridge.onHeightReady(Math.ceil(h) + 40);
            }).observe(wrap);
        } else {
            setTimeout(function() {
                var h = wrap ? wrap.getBoundingClientRect().height : document.body.scrollHeight;
                if (window.HeightBridge) window.HeightBridge.onHeightReady(Math.ceil(h) + 40);
            }, 300);
        }
    }).catch(function(err) {
        if (window.ErrorBridge) window.ErrorBridge.onMermaidError(
            (err && err.message ? err.message : String(err)) + '\n\n--- RAW ---\n' + rawContent
        );
        if (window.HeightBridge) window.HeightBridge.onHeightReady(160);
    });
});
</script>
</body>
</html>""".trimIndent()
    }

    var isRendered by remember(htmlContent) { mutableStateOf(false) }
    var webViewHeightPx by remember(htmlContent) { mutableStateOf(heightCache[htmlContent] ?: -1) }
    var mermaidError by remember(htmlContent) { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val targetHeightDp = remember(webViewHeightPx) {
        if (webViewHeightPx == -1) 120.dp
        else with(density) { webViewHeightPx.toDp() }.coerceAtLeast(1.dp)
    }

    val animatedHeight by animateDpAsState(
        targetValue = targetHeightDp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "mermaidHeight"
    )

    val webViewAlpha by animateFloatAsState(
        targetValue = if (isRendered) 1f else 0.01f,
        animationSpec = tween(400),
        label = "webviewAlpha"
    )

    val capturedHtmlContent = htmlContent
    val capturedHeightCache = heightCache

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .alpha(webViewAlpha),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.defaultTextEncodingName = "utf-8"
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()

                    addJavascriptInterface(object : Any() {
                        @android.webkit.JavascriptInterface
                        fun onHeightReady(height: Int) {
                            Handler(Looper.getMainLooper()).post {
                                val d = ctx.resources.displayMetrics.density
                                val px = (height * d).toInt().coerceAtLeast(1)
                                capturedHeightCache[capturedHtmlContent] = px
                                webViewHeightPx = px
                                isRendered = true
                            }
                        }
                    }, "HeightBridge")
                    addJavascriptInterface(object : Any() {
                        @android.webkit.JavascriptInterface
                        fun onMermaidError(errorMsg: String) {
                            android.util.Log.e("MermaidDebug", "=== MERMAID ERROR ===\n$errorMsg")
                            Handler(Looper.getMainLooper()).post {
                                mermaidError = errorMsg
                                isRendered = true
                            }
                        }
                    }, "ErrorBridge")
                    tag = ""
                }
            },
            update = { webView ->
                if (webView.tag != capturedHtmlContent) {
                    webView.tag = capturedHtmlContent
                    webView.loadDataWithBaseURL(
                        "file:///android_asset/mermaid/",
                        capturedHtmlContent,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            onRelease = { webView ->
                try {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.clearHistory()
                    webView.removeAllViews()
                    webView.destroy()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        )

        AnimatedVisibility(
            visible = !isRendered,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            ShimmerBox(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp))
        }
    }

    mermaidError?.let { err ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF3A1A1A))
                .padding(10.dp)
        ) {
            Text("⚠ Mermaid Error", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(err, color = Color(0xFFFFAAAA), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// ============================================================
// Markdown parser
// ============================================================

private fun parseMarkdownItems(lines: List<String>): List<MarkdownItem> {
    val items = mutableListOf<MarkdownItem>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.lowercase().startsWith("```mermaid")) {
            val startIndex = i
            val content = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (content.isNotEmpty()) content.append("\n")
                content.append(lines[i])
                i++
            }
            if (i < lines.size) i++
            items.add(MarkdownItem.MermaidBlock(content.toString(), startIndex))
            continue
        }

        if (trimmed.startsWith("```")) {
            val startIndex = i
            val language = trimmed.removePrefix("```").trim().ifBlank { null }
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (code.isNotEmpty()) code.append("\n")
                code.append(lines[i])
                i++
            }
            if (i < lines.size) i++
            items.add(MarkdownItem.CodeBlock(code.toString(), language, startIndex))
            continue
        }

        if (trimmed.startsWith("$$")) {
            val startIndex = i
            if (trimmed.length > 2 && trimmed.endsWith("$$")) {
                items.add(MarkdownItem.MathBlock(line, startIndex))
                i++
                continue
            }
            val content = StringBuilder(line)
            i++
            while (i < lines.size) {
                content.append("\n").append(lines[i])
                if (lines[i].trim().endsWith("$$")) {
                    i++
                    break
                }
                i++
            }
            items.add(MarkdownItem.MathBlock(content.toString(), startIndex))
            continue
        }

        // LaTeX estándar: \[...\] como bloque (puede ocupar varias líneas).
        if (trimmed.startsWith("\\[")) {
            val startIndex = i
            if (trimmed.length > 4 && trimmed.endsWith("\\]")) {
                items.add(MarkdownItem.MathBlock(line, startIndex))
                i++
                continue
            }
            val content = StringBuilder(line)
            i++
            while (i < lines.size) {
                content.append("\n").append(lines[i])
                if (lines[i].trim().endsWith("\\]")) {
                    i++
                    break
                }
                i++
            }
            items.add(MarkdownItem.MathBlock(content.toString(), startIndex))
            continue
        }

        if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length > 1) {
            val startIndex = i
            val tableLines = mutableListOf<String>()
            while (i < lines.size) {
                val t = lines[i].trim()
                if (t.startsWith("|") && t.endsWith("|") && t.length > 1) {
                    tableLines.add(t)
                    i++
                } else break
            }
            val rows = tableLines
                .filterIndexed { idx, l ->
                    !(idx == 1 && l.replace(" ", "").matches(Regex("\\|[-:]+(\\|[-:]+)+\\|")))
                }
                .map { row ->
                    row.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
                }
            if (rows.isNotEmpty()) {
                items.add(MarkdownItem.Table(rows, startIndex))
            }
            continue
        }

        items.add(MarkdownItem.NativeLine(line, i))
        i++
    }

    return items
}

// ============================================================
// Main component
// ============================================================

@Composable
fun MarkdownText(
    text: String,
    scrollState: ScrollState,
    highlightsInfo: String? = null,
    onSavedHighlightClick: (text: String, note: String, line: Int, start: Int, end: Int) -> Unit = { _, _, _, _, _ -> },
    onResolveSelection: (resolver: (Rect, String) -> Triple<Int, Int, Int>?) -> Unit = {},
    highlightQuery: String = "",
    onCheckboxToggle: (Int) -> Unit = {},
    onMathCopy: (String) -> Unit = {},
    fontFamily: FontFamily = FontFamily.SansSerif,
    linePositions: SnapshotStateMap<Int, Int>? = null,
    enableScroll: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var mermaidAssets by remember { mutableStateOf(MermaidAssets("")) }

    val needsMermaid = remember(text) { text.contains("```mermaid", ignoreCase = true) }

    LaunchedEffect(needsMermaid) {
        withContext(Dispatchers.IO) {
            if (needsMermaid) {
                mermaidAssets = MermaidAssetCache.getMermaid(context)
            }
        }
    }

    val lines = remember(text) { text.split("\n") }
    val markdownItems = remember(text) { parseMarkdownItems(lines) }

    val savedHighlights = remember(highlightsInfo) {
        val list = mutableListOf<HighlightItem>()
        if (!highlightsInfo.isNullOrBlank() && highlightsInfo != "[]") {
            try {
                val array = JSONArray(highlightsInfo)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        HighlightItem(
                            text = obj.getString("text"),
                            note = obj.getString("note"),
                            line = obj.optInt("line", -1),
                            start = obj.optInt("start", -1),
                            end = obj.optInt("end", -1)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        list
    }

    val highlightBgColor = MaterialTheme.colorScheme.tertiaryContainer
    val highlightTextColor = MaterialTheme.colorScheme.onTertiaryContainer

    var webViewsReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        webViewsReady = true
    }

    val lineRegistry = remember { mutableMapOf<Int, LineLayoutInfo>() }
    val webViewHeightCache = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<String, Int>() }
    val isDarkTheme = isSystemInDarkTheme()

    DisposableEffect(Unit) {
        onDispose {
            lineRegistry.clear()
            webViewHeightCache.clear()
        }
    }

    LaunchedEffect(text) {
        onResolveSelection { rect, selectedText ->
            resolveRectToPosition(rect, selectedText, lineRegistry, lines)
        }
    }

    val columnModifier = if (enableScroll) {
        modifier.verticalScroll(scrollState).padding(horizontal = 12.dp)
    } else {
        modifier.padding(horizontal = 2.dp)
    }

    Column(modifier = columnModifier) {
        if (!compact) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        markdownItems.forEach { item ->
            val lineKey = item.lineKey()

            Box(
                modifier = Modifier.onGloballyPositioned { coords ->
                    linePositions?.set(lineKey, coords.positionInParent().y.toInt())
                }
            ) {
                when (item) {
                    is MarkdownItem.MathBlock -> {
                        val latexContent = item.rawText
                            .trim()
                            .removePrefix("$$").removeSuffix("$$")
                            .removePrefix("\\[").removeSuffix("\\]")
                            .trim()
                        RaTeXBlockView(
                            latex = latexContent,
                            textColor = MaterialTheme.colorScheme.onBackground,
                            fontSizeDp = 18f,
                            onCopy = { onMathCopy(latexContent) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    is MarkdownItem.MermaidBlock -> {
                        if (webViewsReady) {
                            MermaidWebView(
                                mermaidContent = item.rawText,
                                assets = mermaidAssets,
                                isDarkTheme = isDarkTheme,
                                heightCache = webViewHeightCache,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        } else {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(bottom = 16.dp)
                            )
                        }
                    }

                    is MarkdownItem.CodeBlock -> {
                        CodeBlockView(
                            code = item.code,
                            language = item.language,
                            fontFamily = fontFamily
                        )
                    }

                    is MarkdownItem.Table -> {
                        TableView(
                            rows = item.rows,
                            fontFamily = fontFamily
                        )
                    }

                    is MarkdownItem.NativeLine -> {
                        val lineIndex = item.lineIndex
                        val line = item.text
                        val indentSpaces = line.takeWhile { it == ' ' || it == '\t' }.length
                        val trimmedLine = line.trimStart()

                        val lineHighlights = remember(savedHighlights, lineIndex) {
                            savedHighlights.filter { it.line == lineIndex }
                        }
                        val legacyHighlights = remember(savedHighlights) {
                            savedHighlights.filter { it.start < 0 }
                        }

                        when {
                            trimmedLine.matches(Regex("^(---|\\*\\*\\*|___)$")) -> {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }

                            trimmedLine.startsWith("# ") -> {
                                Text(
                                    text = trimmedLine.removePrefix("# ").trim(),
                                    style = (if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.displaySmall).copy(fontFamily = fontFamily),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = if (compact) Modifier.padding(top = 8.dp, bottom = 4.dp) else Modifier.padding(top = 32.dp, bottom = 16.dp)
                                )
                            }
                            trimmedLine.startsWith("## ") -> {
                                Text(
                                    text = trimmedLine.removePrefix("## ").trim(),
                                    style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.headlineMedium).copy(fontFamily = fontFamily),
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = if (compact) Modifier.padding(top = 6.dp, bottom = 3.dp) else Modifier.padding(top = 24.dp, bottom = 12.dp)
                                )
                            }
                            trimmedLine.startsWith("### ") -> {
                                Text(
                                    text = trimmedLine.removePrefix("### ").trim(),
                                    style = (if (compact) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleLarge).copy(fontFamily = fontFamily),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = if (compact) Modifier.padding(top = 4.dp, bottom = 2.dp) else Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }

                            trimmedLine.startsWith("> ") || trimmedLine == ">" -> {
                                BlockQuoteLine(
                                    text = trimmedLine.removePrefix(">").trimStart(),
                                    lineIndex = lineIndex,
                                    highlightQuery = highlightQuery,
                                    lineHighlights = lineHighlights,
                                    legacyHighlights = legacyHighlights,
                                    onSavedHighlightClick = onSavedHighlightClick,
                                    highlightBgColor = highlightBgColor,
                                    highlightTextColor = highlightTextColor,
                                    fontFamily = fontFamily,
                                    lineRegistry = lineRegistry
                                )
                            }

                            trimmedLine.startsWith("- [ ]") ||
                            trimmedLine.startsWith("- [x]") ||
                            trimmedLine.startsWith("- [X]") -> {
                                val isChecked = trimmedLine.startsWith("- [x]") || trimmedLine.startsWith("- [X]")
                                val checklistContent = trimmedLine
                                    .removePrefix("- [ ]")
                                    .removePrefix("- [x]")
                                    .removePrefix("- [X]")
                                    .trimStart()
                                val paddingStart = 8.dp + (indentSpaces * 6).dp
                                Row(
                                    modifier = Modifier.padding(start = paddingStart, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { onCheckboxToggle(lineIndex) },
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    BasicMarkdownLine(
                                        text = checklistContent,
                                        lineIndex = lineIndex,
                                        prefixLen = 0,
                                        highlightQuery = highlightQuery,
                                        lineHighlights = lineHighlights,
                                        legacyHighlights = legacyHighlights,
                                        onSavedHighlightClick = onSavedHighlightClick,
                                        highlightBgColor = highlightBgColor,
                                        highlightTextColor = highlightTextColor,
                                        fontFamily = fontFamily,
                                        lineRegistry = lineRegistry,
                                        uriHandler = uriHandler,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                                val paddingStart = 16.dp + (indentSpaces * 6).dp
                                val prefixLen = indentSpaces + 2
                                Row(modifier = Modifier.padding(start = paddingStart, top = 8.dp, bottom = 8.dp)) {
                                    Text(
                                        text = if (indentSpaces > 0) "◦" else "•",
                                        modifier = Modifier.width(24.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    BasicMarkdownLine(
                                        text = trimmedLine.substring(2).trim(),
                                        lineIndex = lineIndex,
                                        prefixLen = prefixLen,
                                        highlightQuery = highlightQuery,
                                        lineHighlights = lineHighlights,
                                        legacyHighlights = legacyHighlights,
                                        onSavedHighlightClick = onSavedHighlightClick,
                                        highlightBgColor = highlightBgColor,
                                        highlightTextColor = highlightTextColor,
                                        fontFamily = fontFamily,
                                        lineRegistry = lineRegistry,
                                        uriHandler = uriHandler,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            trimmedLine.matches(Regex("^[0-9]+\\.\\s.*")) -> {
                                val dotIndex = trimmedLine.indexOf(".")
                                val number = trimmedLine.substring(0, dotIndex + 1)
                                val content = trimmedLine.substring(dotIndex + 1).trim()
                                val paddingStart = 16.dp + (indentSpaces * 6).dp
                                val contentStartInTrimmed = trimmedLine.indexOf(content, dotIndex + 1)
                                val prefixLen = indentSpaces + (if (contentStartInTrimmed >= 0) contentStartInTrimmed else dotIndex + 1)

                                Row(modifier = Modifier.padding(start = paddingStart, top = 8.dp, bottom = 8.dp)) {
                                    Text(
                                        text = number,
                                        modifier = Modifier.width(32.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    BasicMarkdownLine(
                                        text = content,
                                        lineIndex = lineIndex,
                                        prefixLen = prefixLen,
                                        highlightQuery = highlightQuery,
                                        lineHighlights = lineHighlights,
                                        legacyHighlights = legacyHighlights,
                                        onSavedHighlightClick = onSavedHighlightClick,
                                        highlightBgColor = highlightBgColor,
                                        highlightTextColor = highlightTextColor,
                                        fontFamily = fontFamily,
                                        lineRegistry = lineRegistry,
                                        uriHandler = uriHandler,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            trimmedLine.isBlank() -> Spacer(modifier = Modifier.height(16.dp))

                            else -> BasicMarkdownLine(
                                text = trimmedLine,
                                lineIndex = lineIndex,
                                prefixLen = indentSpaces,
                                highlightQuery = highlightQuery,
                                lineHighlights = lineHighlights,
                                legacyHighlights = legacyHighlights,
                                onSavedHighlightClick = onSavedHighlightClick,
                                highlightBgColor = highlightBgColor,
                                highlightTextColor = highlightTextColor,
                                fontFamily = fontFamily,
                                lineRegistry = lineRegistry,
                                uriHandler = uriHandler,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!compact) {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ============================================================
// Code block & Table renderers
// ============================================================

@Composable
private fun CodeBlockView(
    code: String,
    language: String?,
    fontFamily: FontFamily
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        if (!language.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = language.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
        Text(
            text = code,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 22.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )
    }
}

@Composable
private fun TableView(
    rows: List<List<String>>,
    fontFamily: FontFamily
) {
    if (rows.isEmpty()) return
    val columnCount = rows.maxOf { it.size }
    if (columnCount == 0) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        rows.forEachIndexed { rowIndex, row ->
            val isHeader = rowIndex == 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isHeader) MaterialTheme.colorScheme.surfaceVariant
                        else Color.Transparent
                    )
                    .padding(vertical = 10.dp)
            ) {
                repeat(columnCount) { colIndex ->
                    val cell = row.getOrNull(colIndex) ?: ""
                    Text(
                        text = cell,
                        style = if (isHeader)
                            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        else
                            MaterialTheme.typography.bodyMedium,
                        color = if (isHeader)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        maxLines = 3
                    )
                    if (colIndex < columnCount - 1) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }
                }
            }
            if (rowIndex < rows.size - 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun BlockQuoteLine(
    text: String,
    lineIndex: Int,
    highlightQuery: String,
    lineHighlights: List<HighlightItem>,
    legacyHighlights: List<HighlightItem>,
    onSavedHighlightClick: (text: String, note: String, line: Int, start: Int, end: Int) -> Unit,
    highlightBgColor: Color,
    highlightTextColor: Color,
    fontFamily: FontFamily,
    lineRegistry: MutableMap<Int, LineLayoutInfo>
) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .heightIn(min = 24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(12.dp))
        BasicMarkdownLine(
            text = text,
            lineIndex = lineIndex,
            prefixLen = 2,
            highlightQuery = highlightQuery,
            lineHighlights = lineHighlights,
            legacyHighlights = legacyHighlights,
            onSavedHighlightClick = onSavedHighlightClick,
            highlightBgColor = highlightBgColor,
            highlightTextColor = highlightTextColor,
            fontFamily = fontFamily,
            lineRegistry = lineRegistry,
            uriHandler = uriHandler,
            modifier = Modifier.weight(1f)
        )
    }
}

// ============================================================
// Basic line renderer — inline math via InlineTextContent
// ============================================================

@Composable
fun BasicMarkdownLine(
    text: String,
    lineIndex: Int,
    prefixLen: Int,
    highlightQuery: String,
    lineHighlights: List<HighlightItem>,
    legacyHighlights: List<HighlightItem>,
    onSavedHighlightClick: (text: String, note: String, line: Int, start: Int, end: Int) -> Unit,
    highlightBgColor: Color,
    highlightTextColor: Color,
    fontFamily: FontFamily,
    lineRegistry: MutableMap<Int, LineLayoutInfo>,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    modifier: Modifier = Modifier
) {
    val hasInlineMath = remember(text) {
        Regex("""\\\([^)\n]+?\\\)|\\\[[^\]\n]+?\\\]|\$\$[^$\n]+?\$\$|(?<!\$)\$(?!\$)[^$\n]+?\$(?!\$)""").containsMatchIn(text)
    }

    if (hasInlineMath) {
        InlineMathMarkdownLine(
            text = text,
            lineIndex = lineIndex,
            prefixLen = prefixLen,
            highlightQuery = highlightQuery,
            lineHighlights = lineHighlights,
            legacyHighlights = legacyHighlights,
            onSavedHighlightClick = onSavedHighlightClick,
            highlightBgColor = highlightBgColor,
            highlightTextColor = highlightTextColor,
            fontFamily = fontFamily,
            lineRegistry = lineRegistry,
            uriHandler = uriHandler,
            modifier = modifier
        )
        return
    }

    val annotatedString = remember(text, lineHighlights, legacyHighlights, highlightQuery, highlightBgColor, highlightTextColor) {
        buildAnnotatedString {
            val pattern = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)|\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|_(.*?)_")
            var currentIndex = 0
            val matches = pattern.findAll(text)

            for (match in matches) {
                append(text.substring(currentIndex, match.range.first))
                when {
                    match.groups[1] != null && match.groups[2] != null -> {
                        val linkText = match.groups[1]!!.value
                        val linkUrl = match.groups[2]!!.value
                        withLink(
                            LinkAnnotation.Url(
                                url = linkUrl,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = Color(0xFF64B5F6),
                                        textDecoration = TextDecoration.Underline
                                    )
                                ),
                                linkInteractionListener = {
                                    try { uriHandler.openUri(linkUrl) } catch (_: Exception) {}
                                }
                            )
                        ) {
                            append(linkText)
                        }
                    }
                    match.groups[3] != null -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(match.groups[3]!!.value)
                        }
                    }
                    match.groups[4] != null -> {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(match.groups[4]!!.value)
                        }
                    }
                    match.groups[5] != null -> {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(match.groups[5]!!.value)
                        }
                    }
                }
                currentIndex = match.range.last + 1
            }
            append(text.substring(currentIndex))

            val plainString = this.toAnnotatedString().text
            val plainLength = plainString.length

            lineHighlights.forEach { item ->
                val localStart = item.start - prefixLen
                val localEnd = item.end - prefixLen
                if (localStart in 0 until plainLength && localEnd in (localStart + 1)..plainLength) {
                    addStyle(
                        style = SpanStyle(background = highlightBgColor, color = highlightTextColor, fontWeight = FontWeight.SemiBold),
                        start = localStart,
                        end = localEnd
                    )
                    addStringAnnotation(
                        tag = "SAVED_HIGHLIGHT",
                        annotation = "${item.text}@@KEY@@${item.line}:${item.start}:${item.end}",
                        start = localStart,
                        end = localEnd
                    )
                }
            }

            val plainLower = plainString.lowercase()
            legacyHighlights.forEach { item ->
                val wordLower = item.text.lowercase()
                if (wordLower.isBlank()) return@forEach
                var startIndex = plainLower.indexOf(wordLower)
                while (startIndex >= 0) {
                    addStyle(
                        style = SpanStyle(background = highlightBgColor, color = highlightTextColor, fontWeight = FontWeight.SemiBold),
                        start = startIndex,
                        end = startIndex + wordLower.length
                    )
                    addStringAnnotation(
                        tag = "SAVED_HIGHLIGHT",
                        annotation = "${item.text}@@KEY@@legacy:${item.text}",
                        start = startIndex,
                        end = startIndex + wordLower.length
                    )
                    startIndex = plainLower.indexOf(wordLower, startIndex + 1)
                }
            }

            if (highlightQuery.isNotBlank()) {
                val queryLower = highlightQuery.lowercase()
                var startIndex = plainLower.indexOf(queryLower)
                while (startIndex >= 0) {
                    addStyle(
                        style = SpanStyle(background = Color.Yellow, color = Color.Black),
                        start = startIndex,
                        end = startIndex + queryLower.length
                    )
                    startIndex = plainLower.indexOf(queryLower, startIndex + 1)
                }
            }
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var windowBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(textLayoutResult, windowBounds, text) {
        val layout = textLayoutResult
        val bounds = windowBounds
        if (layout != null && bounds != null) {
            lineRegistry[lineIndex] = LineLayoutInfo(
                layoutResult = layout,
                boundsInWindow = bounds,
                renderedText = text,
                prefixLen = prefixLen
            )
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp, fontFamily = fontFamily),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                windowBounds = coordinates.boundsInWindow()
            }
            .pointerInput(annotatedString) {
                detectTapGestures { pos ->
                    textLayoutResult?.let { layoutResult ->
                        val offset = layoutResult.getOffsetForPosition(pos)
                        val noteByKey = mutableMapOf<String, String>()
                        lineHighlights.forEach { noteByKey["${it.line}:${it.start}:${it.end}"] = it.note }
                        legacyHighlights.forEach { noteByKey["legacy:${it.text}"] = it.note }
                        annotatedString.getStringAnnotations(tag = "SAVED_HIGHLIGHT", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                val parts = annotation.item.split("@@KEY@@")
                                val word = parts.getOrElse(0) { "" }
                                val key = parts.getOrNull(1) ?: "legacy:$word"
                                val note = noteByKey[key] ?: ""
                                if (key.startsWith("legacy:")) {
                                    onSavedHighlightClick(word, note, -1, -1, -1)
                                } else {
                                    val keyParts = key.split(":")
                                    val kLine = keyParts.getOrNull(0)?.toIntOrNull() ?: -1
                                    val kStart = keyParts.getOrNull(1)?.toIntOrNull() ?: -1
                                    val kEnd = keyParts.getOrNull(2)?.toIntOrNull() ?: -1
                                    onSavedHighlightClick(word, note, kLine, kStart, kEnd)
                                }
                            }
                    }
                }
            },
        onTextLayout = { textLayoutResult = it }
    )
}

// ============================================================
// Inline math line — InlineTextContent + RaTeXView (displayMode = false)
// ============================================================

@Composable
private fun InlineMathMarkdownLine(
    text: String,
    lineIndex: Int,
    prefixLen: Int,
    highlightQuery: String,
    lineHighlights: List<HighlightItem>,
    legacyHighlights: List<HighlightItem>,
    onSavedHighlightClick: (text: String, note: String, line: Int, start: Int, end: Int) -> Unit,
    highlightBgColor: Color,
    highlightTextColor: Color,
    fontFamily: FontFamily,
    lineRegistry: MutableMap<Int, LineLayoutInfo>,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    modifier: Modifier = Modifier
) {
    val segments = remember(text) { splitInlineMathSegments(text) }
    val textColor = MaterialTheme.colorScheme.onBackground

    val inlineContent = remember(segments, textColor) {
        buildMap<String, InlineTextContent> {
            segments.forEachIndexed { idx, seg ->
                if (seg.kind == InlineSegmentKind.MATH) {
                    val id = "inline_math_${lineIndex}_$idx"
                    val (wEm, hEm) = estimateInlineMathSize(seg.text)
                    put(id, InlineTextContent(
                        placeholder = Placeholder(
                            width = wEm.em,
                            height = hEm.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        )
                    ) { _ ->
                        RaTeXInlineView(
                            latex = seg.text,
                            textColor = textColor,
                            fontSizeDp = 18f
                        )
                    })
                }
            }
        }
    }

    val annotatedString = remember(segments, lineHighlights, legacyHighlights, highlightQuery, highlightBgColor, highlightTextColor, textColor) {
        buildAnnotatedString {
            segments.forEachIndexed { idx, seg ->
                when (seg.kind) {
                    InlineSegmentKind.TEXT -> {
                        val pattern = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)|\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|_(.*?)_")
                        var currentIndex = 0
                        val matches = pattern.findAll(seg.text)
                        for (match in matches) {
                            append(seg.text.substring(currentIndex, match.range.first))
                            when {
                                match.groups[1] != null && match.groups[2] != null -> {
                                    val linkText = match.groups[1]!!.value
                                    val linkUrl = match.groups[2]!!.value
                                    withLink(
                                        LinkAnnotation.Url(
                                            url = linkUrl,
                                            styles = TextLinkStyles(
                                                style = SpanStyle(
                                                    color = Color(0xFF64B5F6),
                                                    textDecoration = TextDecoration.Underline
                                                )
                                            ),
                                            linkInteractionListener = {
                                                try { uriHandler.openUri(linkUrl) } catch (_: Exception) {}
                                            }
                                        )
                                    ) {
                                        append(linkText)
                                    }
                                }
                                match.groups[3] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groups[3]!!.value) }
                                match.groups[4] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groups[4]!!.value) }
                                match.groups[5] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groups[5]!!.value) }
                            }
                            currentIndex = match.range.last + 1
                        }
                        append(seg.text.substring(currentIndex))
                    }
                    InlineSegmentKind.MATH -> {
                        val id = "inline_math_${lineIndex}_$idx"
                        appendInlineContent(id, alternateText = seg.text)
                    }
                }
            }

            val plainString = this.toAnnotatedString().text
            val plainLength = plainString.length

            lineHighlights.forEach { item ->
                val localStart = item.start - prefixLen
                val localEnd = item.end - prefixLen
                if (localStart in 0 until plainLength && localEnd in (localStart + 1)..plainLength) {
                    addStyle(
                        style = SpanStyle(background = highlightBgColor, color = highlightTextColor, fontWeight = FontWeight.SemiBold),
                        start = localStart,
                        end = localEnd
                    )
                }
            }
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var windowBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(textLayoutResult, windowBounds, text) {
        val layout = textLayoutResult
        val bounds = windowBounds
        if (layout != null && bounds != null) {
            lineRegistry[lineIndex] = LineLayoutInfo(
                layoutResult = layout,
                boundsInWindow = bounds,
                renderedText = text,
                prefixLen = prefixLen
            )
        }
    }

    Text(
        text = annotatedString,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp, fontFamily = fontFamily),
        color = textColor,
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                windowBounds = coordinates.boundsInWindow()
            },
        onTextLayout = { textLayoutResult = it }
    )
}

/**
 * RaTeX inline view con auto-sizing y centrado vertical.
 *
 * El AndroidView SIEMPRE se crea (si no, el onGloballyPositioned nunca
 * dispara y la fórmula queda invisible). La primera pasada se mide con
 * wrapContentSize (tamaño natural del RaTeXView), y una vez medido, se
 * le fija ese tamaño exacto y se centra dentro del Placeholder.
 */
@Composable
private fun RaTeXInlineView(
    latex: String,
    textColor: Color,
    fontSizeDp: Float
) {
    var measuredSize by remember(latex) { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val sizeModifier = if (measuredSize == IntSize.Zero) {
            Modifier
        } else {
            Modifier.size(
                width = with(density) { measuredSize.width.toDp() },
                height = with(density) { measuredSize.height.toDp() }
            )
        }

        AndroidView(
            modifier = sizeModifier.onGloballyPositioned { coords ->
                val size = coords.size
                if (size.width > 0 && size.height > 0 && size != measuredSize) {
                    measuredSize = size
                }
            },
            factory = { ctx ->
                RaTeXView(ctx).apply {
                    displayMode = false
                    this.fontSize = fontSizeDp
                    this.latex = latex
                    color = textColor.toArgb()
                }
            },
            update = { view ->
                view.latex = latex
                view.color = textColor.toArgb()
            }
        )
    }
}