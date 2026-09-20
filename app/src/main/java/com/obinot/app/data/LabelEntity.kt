package com.obinot.app.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Catálogo global de labels. Cada label existe UNA SOLA VEZ acá, con su color asignado.
 * Las notas siguen guardando el nombre del label como string ("label1|label2"), pero el
 * color se resuelve consultando esta tabla por nombre. Así, si el usuario cambia el color
 * de "Trabajo", todas las notas que tengan esa label se actualizan al instante.
 */
@Immutable
@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val name: String,
    val colorHex: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Gris neutro para labels recién creados o importados sin color asignado. */
        const val DEFAULT_COLOR = "#BDBDBD"

        /**
         * Paleta minimalista: 5 colores base × 2 tonos = 10 opciones.
         * Organizada en pares (suave, intenso) para que la UI las muestre como "mismo color, dos tonos".
         */
        val PALETTE: List<String> = listOf(
            "#FF8A80", // Rojo suave
            "#E53935", // Rojo intenso
            "#FFB74D", // Naranja suave
            "#F57C00", // Naranja intenso
            "#AED581", // Verde suave
            "#43A047", // Verde intenso
            "#64B5F6", // Azul suave
            "#1E88E5", // Azul intenso
            "#BA68C8", // Violeta suave
            "#8E24AA", // Violeta intenso
        )

        /** Lista completa que incluye la opción por defecto al inicio (para el picker). */
        val FULL_PALETTE: List<String> = listOf(DEFAULT_COLOR) + PALETTE
    }
}