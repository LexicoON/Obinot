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
// KaTeX WebView
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
// Markdown parser
// ============================================================

/**
 * Divide una línea en segmentos alternando texto y math inline.
 *
 * Soporta dos sintaxis:
 *   - `$$...$$` → display math (KaTeX lo renderiza en modo display).
 *   - `$...$`   → inline math (KaTeX lo renderiza en modo inline).
 *
 * Retorna `null` cuando la línea no tiene math inline con texto antes/después:
 *   - Sin math → el caller la trata como NativeLine.
 *   - Con un único `$$...$$` que cubre TODA la línea → el caller la trata como
 *     MathBlock puro (comportamiento previo intacto).
 *
 * Reglas de validación para `$...$` (evitan falsos positivos con precios como
 * "$100" o "$1.50 y $2"):
 *   - Debe cerrar con `$` en la misma línea.
 *   - No puede empezar ni terminar con espacio (convención LaTeX).
 *   - No puede contener doble espacio.
 *   - Longitud máxima de 80 caracteres.
 *
 * Los segmentos se devuelven con `lineIndex = -1`. El caller los re-inyecta
 * con el `lineIndex` real de la línea original.
 */
private fun splitInlineMath(line: String): List<MarkdownItem>? {
    // Regex unificado con dos alternativas:
    //   Grupo 1: contenido de $$...$$ (dólar doble).
    //   Grupo 2: contenido de $...$ (dólar simple).
    // `(?!\$)` evita que el `$` de un `$$` matchee como dólar simple.
    // `[^$\n]+?` impide que el contenido tenga otro `$` ni saltos de línea.
    val pattern = Regex("""\$\$(.+?)\$\$|\$(?!\$)([^$\n]+?)\$(?!\$)""")

    val segments = mutableListOf<MarkdownItem>()
    var cursor = 0

    for (match in pattern.findAll(line)) {
        val isDouble = match.groups[1] != null
        val content = if (isDouble) match.groups[1]!!.value else match.groups[2]!!.value

        // Validación: un dólar simple solo se acepta como math si cumple
        // las reglas mínimas. Los dólares dobles siempre se aceptan.
        val isValidSimple = !isDouble
            && content.isNotBlank()
            && !content.startsWith(" ")
            && !content.endsWith(" ")
            && content.length <= 80
            && !content.contains("  ")

        val isMath = isDouble || isValidSimple
        if (!isMath) continue

        // Texto entre el cursor y este match.
        if (match.range.first > cursor) {
            val before = line.substring(cursor, match.range.first)
            if (before.isNotBlank()) {
                segments.add(MarkdownItem.NativeLine(before, -1))
            }
        }

        val raw = if (isDouble) "$$$content$$" else "\$$content\$"
        segments.add(MarkdownItem.MathBlock(raw, -1))
        cursor = match.range.last + 1
    }

    // Sin math válido: dejar que el caller trate la línea entera como NativeLine.
    if (segments.none { it is MarkdownItem.MathBlock }) return null

    // Caso especial: la línea entera es un único `$$...$$` sin texto alrededor.
    // El caller tiene un bloque dedicado para math multilínea; dejamos que lo maneje.
    if (segments.size == 1 && segments[0] is MarkdownItem.MathBlock) {
        val mathItem = segments[0] as MarkdownItem.MathBlock
        if (mathItem.rawText.startsWith("$$") && mathItem.rawText.endsWith("$$")) {
            if (line.trim() == mathItem.rawText) return null
        }
    }

    // Texto después del último match.
    if (cursor < line.length) {
        val after = line.substring(cursor)
        if (after.isNotBlank()) {
            segments.add(MarkdownItem.NativeLine(after, -1))
        }
    }

    return segments.ifEmpty { null }
}

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

        // Inline math: divide la línea en segmentos texto/math. Si retorna
        // null (sin `$$...$$` con texto alrededor, o un solo `$$...$$` que
        // cubre toda la línea), el flujo cae al MathBlock multilínea o al
        // NativeLine de abajo.
        val inlineSegments = splitInlineMath(line)
        if (inlineSegments != null) {
            inlineSegments.forEach { segment ->
                when (segment) {
                    is MarkdownItem.NativeLine -> items.add(MarkdownItem.NativeLine(segment.text, i))
                    is MarkdownItem.MathBlock -> items.add(MarkdownItem.MathBlock(segment.rawText, i))
                    // No debería pasar: splitInlineMath solo devuelve estos dos tipos.
                    else -> items.add(segment)
                }
            }
            i++
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
//
// Usamos Column + verticalScroll en vez de LazyColumn a propósito:
// LazyColumn recicla items cuando salen del viewport, lo que destruye
// los WebViews de KaTeX y Mermaid. Al volver a scrollear hacia ellos,
// se reconstruyen desde cero (recargan HTML, re-parsean JS, re-renderizan
// SVG), lo que produce lag perceptible en cada pasada.
//
// Con Column, todos los items se componen una vez y se mantienen vivos.
// El costo es más memoria para notas con muchos diagramas, pero para el
// caso típico (5-20 items por nota) el trade-off es claramente favorable.
//
// `linePositions` es opcional: si el caller lo pasa, se van llenando
// los offsets Y de cada item para permitir scroll-to-line sin LazyListState.
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

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val css = try { context.assets.open("katex/katex.min.css").bufferedReader().readText() } catch (e: Exception) { "" }
            val js = try { context.assets.open("katex/katex.min.js").bufferedReader().readText() } catch (e: Exception) { "" }
            val ar = try { context.assets.open("katex/auto-render.min.js").bufferedReader().readText() } catch (e: Exception) { "" }
            val mhchem = try { context.assets.open("katex/mhchem.min.js").bufferedReader().readText() } catch (e: Exception) { "" }
            katexAssets = KaTeXAssets(css, js, ar, mhchem)

            val mermaidJs = try { context.assets.open("mermaid/mermaid.min.js").bufferedReader().readText() } catch (e: Exception) { "" }
            mermaidAssets = MermaidAssets(mermaidJs)
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
                    // positionInParent() en un Column con verticalScroll nos da
                    // la posición dentro del contenido del scroll, que es
                    // exactamente el offset al que hay que hacer animateScrollTo.
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
// Basic line renderer (with link support)
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