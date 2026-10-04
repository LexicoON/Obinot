package com.obinot.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.obinot.app.R

/**
 * Idiomas soportados por la app (per-app locale). El código "device" sigue el
 * idioma del sistema; el resto fuerza un locale específico.
 *
 * [localizedNameRes] apunta al nombre del idioma traducido al idioma ACTUAL de
 * la app. Ej: si la app está en español, `localizedNameRes` de ENGLISH apunta
 * a "Inglés". Esto se logra porque strings.xml tiene las entries
 * `language_name_english`, `language_name_spanish`, `language_name_german` en
 * cada carpeta de recursos.
 *
 * [nativeName] es el nombre del idioma en su propio idioma. Nunca cambia.
 * [flagEmoji] es la bandera que representa al idioma (arbitraria para idiomas
 * con múltiples países; usamos la del país más asociado culturalmente).
 */
enum class AppLanguage(
    val code: String,
    @StringRes val localizedNameRes: Int,
    val nativeName: String,
    val flagEmoji: String
) {
    DEVICE(
        code = "device",
        localizedNameRes = R.string.settings_language_device,
        nativeName = "",
        flagEmoji = ""
    ),
    ENGLISH(
        code = "en",
        localizedNameRes = R.string.language_name_english,
        nativeName = "English",
        flagEmoji = "🇬🇧"
    ),
    SPANISH(
        code = "es",
        localizedNameRes = R.string.language_name_spanish,
        nativeName = "Español",
        flagEmoji = "🇦🇷"
    ),
    GERMAN(
        code = "de",
        localizedNameRes = R.string.language_name_german,
        nativeName = "Deutsch",
        flagEmoji = "🇩🇪"
    );

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code == code } ?: DEVICE
    }
}

/**
 * Label para mostrar en la UI del selector de idiomas.
 *
 * Reglas:
 *  - DEVICE: solo el nombre del idioma actual de la app ("Device language",
 *    "Idioma del dispositivo", etc.). No tiene bandera.
 *  - Otros: si el nombre traducido coincide con el nombre nativo, mostramos
 *    solo uno ("English 🇬🇧"). Si difiere, mostramos ambos separados por "/"
 *    ("Inglés/English 🇬🇧").
 */
@Composable
fun AppLanguage.displayLabel(): String {
    if (this == AppLanguage.DEVICE) {
        return stringResource(localizedNameRes)
    }
    val localized = stringResource(localizedNameRes)
    return if (localized.equals(nativeName, ignoreCase = true)) {
        "$nativeName $flagEmoji"
    } else {
        "$localized/$nativeName $flagEmoji"
    }
}