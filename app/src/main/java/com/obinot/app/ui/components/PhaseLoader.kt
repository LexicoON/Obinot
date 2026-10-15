package com.obinot.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Estado de la tarea; cada fase tiene su propia animación pasiva. */
enum class LoadPhase { COMPRESSING, TRANSCRIBING, PROCESSING, CHATTING }

/**
 * Animación pasiva según la fase:
 *  - COMPRESSING: anillos que se contraen hacia el centro (se "aprieta" el audio).
 *  - TRANSCRIBING: barras de onda de audio.
 *  - PROCESSING: órbita de puntos con respiración (IA pensando).
 *  - CHATTING: tres puntos que rebotan en cascada.
 * Un único InfiniteTransition por instancia y solo Canvas: costo mínimo.
 */
@Composable
fun PhaseLoader(phase: LoadPhase, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val t = rememberInfiniteTransition(label = "phase_loader")
    val p by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(
                when (phase) {
                    LoadPhase.COMPRESSING -> 1400
                    LoadPhase.TRANSCRIBING -> 900
                    LoadPhase.PROCESSING -> 2200
                    LoadPhase.CHATTING -> 900
                },
                easing = LinearEasing
            ),
            RepeatMode.Restart
        ),
        label = "phase_p"
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2f
        when (phase) {
            LoadPhase.COMPRESSING -> {
                for (i in 0 until 3) {
                    val q = (p + i / 3f) % 1f
                    val radius = r * (1f - q)
                    drawCircle(
                        color = (if (i == 1) secondary else primary).copy(alpha = 0.15f + 0.65f * q),
                        radius = radius, center = c,
                        style = Stroke(width = r * 0.10f)
                    )
                }
                drawCircle(primary, radius = r * 0.12f, center = c)
            }
            LoadPhase.TRANSCRIBING -> {
                val bars = 5
                val w = this.size.width / (bars * 2f)
                for (i in 0 until bars) {
                    val h = this.size.height * (0.25f + 0.65f * (0.5f + 0.5f * sin(2 * PI * (p + i * 0.17f)).toFloat()))
                    drawRoundRect(
                        color = if (i % 2 == 0) primary else tertiary,
                        topLeft = Offset(w * (2 * i + 0.5f), c.y - h / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(w / 2f)
                    )
                }
            }
            LoadPhase.PROCESSING -> {
                val breathe = 0.85f + 0.15f * sin(2 * PI * p).toFloat()
                for (i in 0 until 6) {
                    val a = 2 * PI * (p + i / 6f)
                    val orbit = r * 0.72f * breathe
                    drawCircle(
                        color = (if (i % 2 == 0) primary else tertiary).copy(alpha = 0.35f + 0.65f * (i / 5f)),
                        radius = r * (0.07f + 0.05f * (i / 5f)),
                        center = Offset(c.x + orbit * cos(a).toFloat(), c.y + orbit * sin(a).toFloat())
                    )
                }
                drawCircle(secondary.copy(alpha = 0.25f), radius = r * 0.3f * breathe, center = c)
            }
            LoadPhase.CHATTING -> {
                for (i in 0 until 3) {
                    val q = (p + i * 0.18f) % 1f
                    val bounce = sin(PI * q).toFloat().coerceAtLeast(0f)
                    drawCircle(
                        color = primary.copy(alpha = 0.4f + 0.6f * bounce),
                        radius = r * 0.16f,
                        center = Offset(this.size.width * (0.2f + 0.3f * i), c.y - r * 0.35f * bounce)
                    )
                }
            }
        }
    }
}
