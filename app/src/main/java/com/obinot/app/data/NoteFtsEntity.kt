package com.obinot.app.data

import androidx.room.Entity
import androidx.room.Fts5

/**
 * Tabla virtual FTS5 que indexa title, rawText y summary de cada nota.
 *
 * Con `contentEntity = NoteEntity::class`, Room genera:
 *  - La tabla virtual `notes_fts` con `content="notes"`.
 *  - 4 triggers que mantienen el índice sincronizado automáticamente
 *    cuando se inserta, actualiza o borra una fila de `notes`.
 *
 * El `docid` de la FTS table coincide con el `id` (rowid) de `notes`, así
 * que los joins son O(1) sobre el índice.
 *
 * FTS5 vs FTS4: FTS5 es más rápido y trae la función `bm25()` nativa, que
 * usamos para ordenar los resultados de búsqueda por relevancia en vez de
 * por timestamp. Es la principal razón de la migración 10 → 11.
 *
 * Las columnas declaradas deben coincidir EXACTAMENTE con las de la
 * migración manual 10→11 (AppDatabase.MIGRATION_10_11). Si no coinciden,
 * la validación de schema de Room falla al abrir la DB.
 */
@Fts5(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    val title: String,
    val rawText: String,
    val summary: String
)