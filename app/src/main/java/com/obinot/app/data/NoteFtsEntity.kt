package com.obinot.app.data

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Tabla virtual FTS4 que indexa title, rawText y summary de cada nota.
 *
 * Con `contentEntity = NoteEntity::class`, Room genera:
 *  - La tabla virtual `notes_fts` con `content="notes"`.
 *  - 4 triggers que mantienen el índice sincronizado automáticamente
 *    cuando se inserta, actualiza o borra una fila de `notes`.
 *
 * El `docid` de la FTS table coincide con el `id` (rowid) de `notes`, así que
 * los joins son O(1) sobre el índice.
 *
 * Las columnas declaradas deben coincidir EXACTAMENTE con las de la migración
 * manual 8→9 (AppDatabase.MIGRATION_8_9). Si no coinciden, la validación de
 * schema de Room falla al abrir la DB.
 */
@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    val title: String,
    val rawText: String,
    val summary: String?
)