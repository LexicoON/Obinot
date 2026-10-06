package com.obinot.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Barra de onda durante la grabación. Cinco barras que crecen con el volumen.
 *
 * El gradiente primary→tertiary→primary se desplaza horizontalmente combinando:
 *   1) Un ciclo pasivo lento (~4s) que mantiene el color en movimiento incluso
 *      sin input de audio. Esto evita que la barra se vea "congelada" cuando
 *      el micrófono no capta nada (silencio, sala tratada, etc.).
 *   2) El volumen animado actual, que desplaza el gradiente hasta ~35% del
 *      ancho del canvas. A más volumen, más rápido fluye el color.
 *
 * La combinación da la sensación de que la "energía" se mueve por las barras.
 * El uso de `animatedAmplitude` (spring) suaviza cualquier jitter del micrófono.
 */
@Composable
fun AudioWaveform(
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    // BOOST de sensibilidad: en modo Accurate el micrófono puede captar
    // señales débiles. Multiplicamos por 2.5 y clampeamos a 1f.
    val boostedAmplitude = (amplitude * 2.5f).coerceIn(0f, 1f)

    // Animatable en lugar de animateFloatAsState para que el valor se actualice
    // tan pronto como cambia la amplitud (sin retargeting lag).
    val animatedAmplitude = remember { Animatable(0f) }
    LaunchedEffect(boostedAmplitude) {
        animatedAmplitude.animateTo(
            boostedAmplitude,
            spring(stiffness = Spring.StiffnessMedium)
        )
    }

    // Fase pasiva: oscila 0f ↔ 1f en ~4 segundos, con easing lineal para que
    // el movimiento sea parejo y calmado. Se reinicia sola (RepeatMode.Reverse).
    val infinite = rememberInfiniteTransition(label = "waveform_passive")
    val passivePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveform_passive_phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f
        val amp = animatedAmplitude.value

        // 5 barras expresivas con multiplicadores de peso simétricos.
        val numBars = 5
        val barWidth = 36.dp.toPx()
        val gap = 12.dp.toPx()

        val totalWaveWidth = (numBars * barWidth) + ((numBars - 1) * gap)
        val startX = (canvasWidth - totalWaveWidth) / 2f

        // Shift del gradiente. Componentes:
        //   - passiveShift: siempre activo, lento, amplio (±20% del ancho).
        //   - volumeShift: proporcional al volumen animado, hasta 35% del ancho.
        // El resultado es una posición que "respira" con la voz sin marear.
        val passiveShift = (passivePhase - 0.5f) * canvasWidth * 0.4f
        val volumeShift = amp * canvasWidth * 0.35f
        val shift = passiveShift + volumeShift

        // Gradiente de 3 paradas. El ancho efectivo es 2x el canvas para que
        // las tres paradas (primary, tertiary, primary) se vean en la ventana
        // visible sin que el gradiente se corte abruptamente en los bordes.
        val gradient = Brush.linearGradient(
            colors = listOf(primary, tertiary, primary),
            start = Offset(shift - canvasWidth * 0.5f, 0f),
            end = Offset(shift + canvasWidth * 1.5f, 0f)
        )

        val weightMultipliers = listOf(0.5f, 0.9f, 1.0f, 0.8f, 0.6f)

        for (i in 0 until numBars) {
            val x = startX + (i * (barWidth + gap))
            val baseHeight = 16.dp.toPx()

            // Cada barra varía su altura según su índice y la amplitud. Esto
            // le da un aspecto más orgánico que 5 barras idénticas.
            val variation = if (amp > 0.05f) {
                (sin(i * 1.5f + amp * 10f) * 0.15f) + 0.85f
            } else {
                1f
            }

            val dynamicHeight = if (amp > 0f) {
                baseHeight + (amp * (canvasHeight - baseHeight) * weightMultipliers[i] * variation)
            } else {
                baseHeight
            }

            val finalHeight = dynamicHeight.coerceIn(baseHeight, canvasHeight)
            val yOffset = centerY - (finalHeight / 2f)

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, yOffset),
                size = Size(barWidth, finalHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}