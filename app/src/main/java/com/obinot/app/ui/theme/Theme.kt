package com.obinot.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle

private val ExpressiveShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(48.dp)
)

/**
 * Estilos de color disponibles. Cada uno genera una paleta distinta desde el mismo seed.
 *
 * - TONAL_SPOT: el default de Material 3, equilibrado.
 * - VIBRANT: colores más saturados y vivos.
 * - EXPRESSIVE: paleta expresiva de M3, con más contraste.
 * - FRUIT_SALAD: alta variedad de tonos, tres familias cromáticas distintas.
 * - NEUTRAL: paleta casi monocromática, minimalista.
 * - FIDELITY: mantiene fidelidad al color semilla, útil con wallpaper colors.
 * - MONOCHROME: solo grises, sin color.
 */
enum class ColorStyle(val label: String) {
    TONAL_SPOT("Tonal Spot"),
    VIBRANT("Vibrant"),
    EXPRESSIVE("Expressive"),
    FRUIT_SALAD("Fruit Salad"),
    NEUTRAL("Neutral"),
    FIDELITY("Fidelity"),
    MONOCHROME("Monochrome")
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BinotTheme(
    themeMode: Int, // 0 = System, 1 = Light, 2 = Dark, 3 = Amoled
    colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        1 -> false
        2, 3 -> true
        else -> isSystemDark
    }
    val isAmoled = themeMode == 3

    // Mapeo de nuestro enum al PaletteStyle de MaterialKolor.
    // Se usa FruitSalad en vez de Rainbow porque la variante "Rainbow" real de Google
    // tiene chroma 0 en el fondo (gris puro) y es la MENOS colorida de todas a propósito.
    // FruitSalad tiene tres familias cromáticas con alta variedad, que es lo que el
    // usuario espera al elegir "Fruit Salad".
    val paletteStyle = when (colorStyle) {
        ColorStyle.TONAL_SPOT -> PaletteStyle.TonalSpot
        ColorStyle.VIBRANT -> PaletteStyle.Vibrant
        ColorStyle.EXPRESSIVE -> PaletteStyle.Expressive
        ColorStyle.FRUIT_SALAD -> PaletteStyle.FruitSalad
        ColorStyle.NEUTRAL -> PaletteStyle.Neutral
        ColorStyle.FIDELITY -> PaletteStyle.Fidelity
        ColorStyle.MONOCHROME -> PaletteStyle.Monochrome
    }

    val context = LocalContext.current

    // Si el dispositivo soporta wallpaper colors, se usa el primary del sistema como
    // SEED para el estilo elegido. El estilo SIEMPRE se aplica; el wallpaper solo
    // decide el color base.
    val useWallpaper = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val seed = if (useWallpaper) {
        dynamicLightColorScheme(context).primary
    } else {
        PrimaryPurple
    }

    DynamicMaterialExpressiveTheme(
        seedColor = seed,
        motionScheme = MotionScheme.expressive(),
        isDark = isDark,
        isAmoled = isAmoled,
        style = paletteStyle,
        shapes = ExpressiveShapes,
        typography = Typography,
        content = content
    )
}