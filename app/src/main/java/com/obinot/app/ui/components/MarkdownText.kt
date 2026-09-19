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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

/**
 * Cache de assets a nivel de proceso.
 *
 * Los archivos de KaTeX y Mermaid son strings grandes (~1 MB total). Hoy se
 * leen del APK cada vez que se abre una nota, aunque la nota no tenga ni una
 * fórmula ni un diagrama. Este cache los lee UNA sola vez por proceso y los
 * reutiliza durante toda la vida del proceso.
 *
 * Thread-safe: doble-checked locking. La primera llamada desde múltiples
 * threads concurrentes dispara la carga una sola vez.
 */
private object MarkdownAssetCache {
    @Volatile private var katexCache: KaTeXAssets? = null
    @Volatile private var mermaidCache: MermaidAssets? = null
    private val katexLock = Any()
    private val mermaidLock = Any()

    fun getKaTeX(context: android.content.Context): KaTeXAssets {
        katexCache?.let { return it }
        synchronized(katexLock) {
            katexCache?.let { return it }
            val assets = KaTeXAssets(
                css = try { context.assets.open("katex/katex.min.css").bufferedReader().readText() } catch (e: Exception) { "" },
                js = try { context.assets.open("katex/katex.min.js").bufferedReader().readText() } catch (e: Exception) { "" },
                autoRender = try { context.assets.open("katex/auto-render.min.js").bufferedReader().readText() } catch (e: Exception) { "" },
                mhchem = try { context.assets.open("katex/mhchem.min.js").bufferedReader().readText() } catch (e: Exception) { "" }
            )
            katexCache = assets
            return assets
        }
    }

    fun getMermaid(context: android.content.Context): MermaidAssets {
        mermaidCache?.let { return it }
        synchronized(mermaidLock) {
            mermaidCache?.let { return it }
            val assets = MermaidAssets(
                js = try { context.assets.open("mermaid/mermaid.min.js").bufferedReader().readText() } catch (e: Exception) { "" }
            )
            mermaidCache = assets
            return assets
        }
    }
}

data class KaTeXAssets(val css: String, val js: String, val autoRender: String, val mhchem: String) {
    val isReady get() = js.isNotEmpty() && mhchem.isNotEmpty()
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
// KaTeX WebView (block)
// ============================================================

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KaTeXWebView(
    mathContent: String,
    assets: KaTeXAssets,
    textColor: Color,
    fontFamily: FontFamily,
    heightCache: SnapshotStateMap<String, Int>,
    modifier: Modifier = Modifier
) {
    if (!assets.isReady) return

    val hexColor = String.format("#%06X", 0xFFFFFF and textColor.toArgb())
    val cssFont = when (fontFamily) {
        FontFamily.Serif -> "serif"
        FontFamily.Monospace -> "monospace"
        else -> "sans-serif"
    }

    val patchedCss = remember(assets.css) {
        assets.css.replace(Regex("""url\(['"]?(fonts/[^'"")]+)['"]?\)""")) { match ->
            "url('file:///android_asset/katex/${match.groupValues[1]}')"
        }
    }

    val htmlContent = remember(mathContent, hexColor, cssFont) {
        var html = mathContent
        html = html.replace(Regex("^### (.*)$", RegexOption.MULTILINE), "<h4>$1</h4>")
        html = html.replace(Regex("^## (.*)$", RegexOption.MULTILINE), "<h3>$1</h3>")
        html = html.replace(Regex("^# (.*)$", RegexOption.MULTILINE), "<h2>$1</h2>")
        html = html.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
        html = html.replace(Regex("^- (.*)$", RegexOption.MULTILINE), "<li>$1</li>")
        html = html.replace(Regex("^[0-9]+\\. (.*)$", RegexOption.MULTILINE), "<li>$1</li>")

        """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>
${patchedCss}
body {
    background-color: transparent;
    color: ${hexColor};
    font-family: ${cssFont};
    font-size: 16px;
    line-height: 1.6;
    margin: 0;
    padding: 8px 4px;
    word-wrap: break-word;
    overflow-x: hidden; 
    overflow-y: hidden;
}
.katex-display {
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 6px; 
    -webkit-overflow-scrolling: touch;
}
.katex-display::-webkit-scrollbar { height: 4px; }
.katex-display::-webkit-scrollbar-thumb { background: #88888888; border-radius: 4px; }
li { margin-bottom: 4px; }
</style>
</head>
<body>
<div id="math-content">${html}</div>
<script>${assets.js}</script>
<script>${assets.autoRender}</script>
<script>${assets.mhchem}</script>
<script>
document.addEventListener("DOMContentLoaded", function() {
    var el = document.getElementById('math-content');
    if (typeof renderMathInElement !== 'undefined') {
        renderMathInElement(el, {
            delimiters: [
                {left: "$$", right: "$$", display: true},
                {left: "\\[", right: "\\]", display: true},
                {left: "$", right: "$", display: false},
                {left: "\\(", right: "\\)", display: false}
            ],
            throwOnError: false
        });
    }
    if (window.ResizeObserver) {
        new ResizeObserver(function(entries) {
            var h = entries[0].target.getBoundingClientRect().height;
            if (window.HeightBridge) window.HeightBridge.onHeightReady(Math.ceil(h) + 30);
        }).observe(el);
    } else {
        setTimeout(function() {
            var h = el ? el.getBoundingClientRect().height : document.body.scrollHeight;
            if (window.HeightBridge) window.HeightBridge.onHeightReady(Math.ceil(h) + 30);
        }, 500);
    }
});
</script>
</body>
</html>""".trimIndent()
    }

    var isRendered by remember(htmlContent) { mutableStateOf(false) }
    var webViewHeightPx by remember(htmlContent) { mutableStateOf(heightCache[htmlContent] ?: -1) }

    val density = LocalDensity.current
    val targetHeightDp = remember(webViewHeightPx) {
        if (webViewHeightPx == -1) 60.dp
        else with(density) { webViewHeightPx.toDp() }.coerceAtLeast(1.dp)
    }

    val animatedHeight by animateDpAsState(
        targetValue = targetHeightDp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "katexHeight"
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
                    tag = ""
                }
            },
            update = { webView ->
                if (webView.tag != capturedHtmlContent) {
                    webView.tag = capturedHtmlContent
                    webView.loadDataWithBaseURL(
                        "file:///android_asset/katex/",
                        capturedHtmlContent,
                        "text/html",
                        "UTF-8",
                        null
                    )
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
}

// ============================================================
// Mermaid WebView
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
// Inline math — data & helpers
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
    val pattern = Regex("""\$\$([^$\n]+?)\$\$|(?<!\$)\$(?!\$)([^$\n]+?)\$(?!\$)""")
    val segments = mutableListOf<InlineSegment>()
    var cursor = 0

    for (match in pattern.findAll(line)) {
        val isDouble = match.groups[1] != null
        val content = if (isDouble) match.groups[1]!!.value else match.groups[2]!!.value

        val valid = if (isDouble) {
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
 * layout sin tener que esperar al WebView.
 *
 * Si la fórmula real es más ancha que el placeholder, el CSS interno la
 * escala con `transform: scale(...)` para encajar.
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
    val width = (units * 0.4f).coerceIn(0.3f, 20f)
    val extraHeight = when {
        latex.contains("\\frac") || latex.contains("\\dfrac") || latex.contains("\\tfrac") -> 0.5f
        latex.contains("\\sum") || latex.contains("\\int") || latex.contains("\\prod") -> 0.4f
        latex.contains("\\sqrt") -> 0.25f
        else -> 0f
    }
    val height = 1.15f + extraHeight
    return width to height
}

/**
 * Procesa links/bold/italic dentro de un segmento de texto plano.
 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineFormatted(text: String) {
    val pattern = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)|\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|_(.*?)_")
    var currentIndex = 0
    for (match in pattern.findAll(text)) {
        append(text.substring(currentIndex, match.range.first))
        when {
            match.groups[1] != null && match.groups[2] != null -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groups[1]!!.value)
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
    if (currentIndex < text.length) append(text.substring(currentIndex))
}

// ============================================================
// Inline math — WebView compacto
// ============================================================

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KaTeXInlineWebView(
    latex: String,
    assets: KaTeXAssets,
    textColor: Color,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    if (!assets.isReady) {
        // Fallback: sin assets, mostramos el LaTeX crudo con un estilo math.
        Text(
            text = latex,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = textColor,
            modifier = modifier
        )
        return
    }

    val hexColor = String.format("#%06X", 0xFFFFFF and textColor.toArgb())
    val cssFont = when (fontFamily) {
        FontFamily.Serif -> "serif"
        FontFamily.Monospace -> "monospace"
        else -> "sans-serif"
    }

    val patchedCss = remember(assets.css) {
        assets.css.replace(Regex("""url\(['"]?(fonts/[^'"")]+)['"]?\)""")) { match ->
            "url('file:///android_asset/katex/${match.groupValues[1]}')"
        }
    }

    val htmlContent = remember(latex, hexColor, cssFont, patchedCss) {
        """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>
${patchedCss}
html, body {
    background-color: transparent;
    color: ${hexColor};
    font-family: ${cssFont};
    font-size: 18px;
    margin: 0;
    padding: 0;
    overflow: hidden;
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
}
#math-content {
    display: inline-block;
    white-space: nowrap;
}
.katex { font-size: 1em !important; }
.katex-display { margin: 0 !important; }
</style>
</head>
<body>
<div id="math-content">${'$'}${'$'}$latex${'$'}${'$'}</div>
<script>${assets.js}</script>
<script>${assets.autoRender}</script>
<script>${assets.mhchem}</script>
<script>
document.addEventListener("DOMContentLoaded", function() {
    var el = document.getElementById('math-content');
    if (typeof renderMathInElement !== 'undefined') {
        renderMathInElement(el, {
            delimiters: [
                {left: "${'$'}${'$'}", right: "${'$'}${'$'}", display: false},
                {left: "${'$'}", right: "${'$'}", display: false}
            ],
            throwOnError: false
        });
    }
    var naturalWidth = el.scrollWidth;
    var viewportWidth = document.documentElement.clientWidth;
    if (naturalWidth > viewportWidth && viewportWidth > 0) {
        var scale = viewportWidth / naturalWidth;
        el.style.transformOrigin = 'left center';
        el.style.transform = 'scale(' + scale + ')';
    }
});
</script>
</body>
</html>""".trimIndent()
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
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
                tag = ""
            }
        },
        update = { webView ->
            if (webView.tag != htmlContent) {
                webView.tag = htmlContent
                webView.loadDataWithBaseURL(
                    "file:///android_asset/katex/",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
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
    fontFamily: FontFamily = FontFamily.SansSerif,
    linePositions: SnapshotStateMap<Int, Int>? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var katexAssets by remember { mutableStateOf(KaTeXAssets("", "", "", "")) }
    var mermaidAssets by remember { mutableStateOf(MermaidAssets("")) }

    // Analizamos el texto una sola vez para saber qué assets hacen falta.
    // Evitamos cargar ~1 MB de KaTeX + Mermaid cuando la nota es de texto simple.
    val needsKatex = remember(text) { text.contains('$') }
    val needsMermaid = remember(text) { text.contains("```mermaid", ignoreCase = true) }

    LaunchedEffect(needsKatex, needsMermaid) {
        withContext(Dispatchers.IO) {
            if (needsKatex) {
                katexAssets = MarkdownAssetCache.getKaTeX(context)
            }
            if (needsMermaid) {
                mermaidAssets = MarkdownAssetCache.getMermaid(context)
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

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        markdownItems.forEach { item ->
            val lineKey = item.lineKey()

            Box(
                modifier = Modifier.onGloballyPositioned { coords ->
                    linePositions?.set(lineKey, coords.positionInParent().y.toInt())
                }
            ) {
                when (item) {
                    is MarkdownItem.MathBlock -> {
                        KaTeXWebView(
                            mathContent = item.rawText,
                            assets = katexAssets,
                            textColor = MaterialTheme.colorScheme.onBackground,
                            fontFamily = fontFamily,
                            heightCache = webViewHeightCache,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    is MarkdownItem.MermaidBlock -> {
                        MermaidWebView(
                            mermaidContent = item.rawText,
                            assets = mermaidAssets,
                            isDarkTheme = isDarkTheme,
                            heightCache = webViewHeightCache,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
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
                                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = fontFamily),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
                                )
                            }
                            trimmedLine.startsWith("## ") -> {
                                Text(
                                    text = trimmedLine.removePrefix("## ").trim(),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = fontFamily),
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
                                )
                            }
                            trimmedLine.startsWith("### ") -> {
                                Text(
                                    text = trimmedLine.removePrefix("### ").trim(),
                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = fontFamily),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
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
                                    lineRegistry = lineRegistry,
                                    katexAssets = katexAssets
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
                                        katexAssets = katexAssets,
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
                                        katexAssets = katexAssets,
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
                                        katexAssets = katexAssets,
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
                                katexAssets = katexAssets,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
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
    lineRegistry: MutableMap<Int, LineLayoutInfo>,
    katexAssets: KaTeXAssets
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
            katexAssets = katexAssets,
            modifier = Modifier.weight(1f)
        )
    }
}

// ============================================================
// Basic line renderer (with link support + inline math dispatch)
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
    katexAssets: KaTeXAssets = KaTeXAssets("", "", "", ""),
    modifier: Modifier = Modifier
) {
    // Dispatch: si la línea tiene math inline, va por un path que usa
    // InlineTextContent para embeber los WebViews de KaTeX dentro del texto.
    // Si no, sigue el path original (más liviano).
    val hasInlineMath = remember(text) {
        Regex("""\$\$[^$\n]+?\$\$|(?<!\$)\$(?!\$)[^$\n]+?\$(?!\$)""").containsMatchIn(text)
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
            katexAssets = katexAssets,
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
// Inline math renderer
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
    katexAssets: KaTeXAssets,
    modifier: Modifier = Modifier
) {
    val segments = remember(text) { splitInlineMathSegments(text) }
    val textColor = MaterialTheme.colorScheme.onBackground

    val inlineContent = remember(segments, katexAssets, textColor) {
        buildMap {
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
                        KaTeXInlineWebView(
                            latex = seg.text,
                            assets = katexAssets,
                            textColor = textColor,
                            fontFamily = fontFamily
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
                    InlineSegmentKind.TEXT -> appendInlineFormatted(seg.text)
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