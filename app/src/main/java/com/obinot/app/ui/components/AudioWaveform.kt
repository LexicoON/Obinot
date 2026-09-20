package com.obinot.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun AudioWaveform(
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    // BOOST SENSITIVITAS: Dikali 2.5 biar mode accurate yang suaranya kecil tetep ngangkat, dilimit mentok di 1f
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

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f
        val amp = animatedAmplitude.value

        // 5 batang ekspresif
        val numBars = 5
        val barWidth = 36.dp.toPx()
        val gap = 12.dp.toPx()

        val totalWaveWidth = (numBars * barWidth) + ((numBars - 1) * gap)
        val startX = (canvasWidth - totalWaveWidth) / 2f

        val gradient = Brush.linearGradient(colors = listOf(primary, tertiary))

        val weightMultipliers = listOf(0.5f, 0.9f, 1.0f, 0.8f, 0.6f)

        for (i in 0 until numBars) {
            val x = startX + (i * (barWidth + gap))
            val baseHeight = 16.dp.toPx()

            // `sin(Float)` devuelve Float directo (overload desde Kotlin 1.5).
            // Antes había un .toFloat() redundante al final — eliminado.
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