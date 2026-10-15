package com.obinot.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Frosted blur" en vivo, integrado en la app (no depende del blur de
 * ventana de Samsung/Google ni de CrossWindowBlur).
 *
 * Cómo funciona: el contenedor que queda DETRÁS ([frostedSource]) se graba
 * en un GraphicsLayer; la superficie esmerilada ([frostedSurface]) dibuja
 * ese layer desplazado a su posición, con un RenderEffect de blur, y encima
 * un tinte. Es un solo blur por superficie y solo donde se nota (barras
 * flotantes), así que el costo es bajo.
 *
 * RenderEffect requiere Android 12 (API 31). En API 24–30 o con el toggle
 * apagado se usa un fallback sin blur (tinte casi opaco / opaco): se ve
 * limpio y cuesta cero.
 */
val LocalBlurEnabled = compositionLocalOf { true }

val isLiveBlurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Stable
class FrostedBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var sourceOrigin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberFrostedBackdrop(): FrostedBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { FrostedBackdrop(layer) }
}

/** Marca el contenido que se verá borroso detrás de las superficies esmeriladas. */
fun Modifier.frostedSource(backdrop: FrostedBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.sourceOrigin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * Superficie de vidrio esmerilado. [tint] debería ser un color de superficie
 * con alpha (~0.55–0.7). Si [enabled] es false o el SO no soporta blur, cae a
 * [fallbackColor] sin ningún costo extra.
 */
@Composable
fun Modifier.frostedSurface(
    backdrop: FrostedBackdrop,
    shape: Shape,
    tint: Color,
    fallbackColor: Color,
    radius: Dp = 24.dp,
    enabled: Boolean = LocalBlurEnabled.current
): Modifier {
    if (!enabled) return this.clip(shape).background(fallbackColor)
    if (!isLiveBlurSupported) return this.clip(shape).background(fallbackColor.copy(alpha = 0.94f))

    var origin by remember { mutableStateOf(Offset.Zero) }
    val blurLayer = rememberGraphicsLayer()
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .clip(shape)
        .drawWithContent {
            val r = radius.toPx()
            blurLayer.renderEffect = BlurEffect(r, r, TileMode.Clamp)
            val dx = origin.x - backdrop.sourceOrigin.x
            val dy = origin.y - backdrop.sourceOrigin.y
            blurLayer.record {
                translate(left = -dx, top = -dy) { drawLayer(backdrop.layer) }
            }
            drawLayer(blurLayer)
            drawRect(tint)
            drawContent()
        }
}
