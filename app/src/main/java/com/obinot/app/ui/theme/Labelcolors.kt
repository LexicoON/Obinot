package com.obinot.app.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.obinot.app.data.LabelEntity

/**
 * Resolución centralizada del color de un label.
 *
 * El valor [LabelEntity.DEFAULT_COLOR] (y null) ya NO significa "gris": significa
 * "usá el color del tema", que es como se veían los labels antes del update.
 * Así, cambiar el wallpaper o el estilo de paleta vuelve a repintar todos los
 * labels que no tienen un color explícito asignado.
 */
@Composable
fun resolveLabelColors(hex: String?, selected: Boolean = false): Pair<Color, Color> {
    if (selected) {
        return MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
    val isDynamic = hex.isNullOrBlank() || hex.equals(LabelEntity.DEFAULT_COLOR, ignoreCase = true)
    if (isDynamic) {
        return MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    val parsed = try {
        Color(AndroidColor.parseColor(hex))
    } catch (e: Exception) {
        return MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    val content = if (parsed.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
    return parsed to content
}

/** Solo el color de fondo, para los puntitos del drawer. */
@Composable
fun resolveLabelDotColor(hex: String?): Color = resolveLabelColors(hex).first

/** true si el label usa el color del tema (para marcar el swatch "Dynamic" en el picker). */
fun isDynamicLabelColor(hex: String?): Boolean =
    hex.isNullOrBlank() || hex.equals(LabelEntity.DEFAULT_COLOR, ignoreCase = true)