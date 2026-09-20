package com.obinot.app.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        // Índice compuesto para la query principal (getAllNotes / getRecentNotes):
        // WHERE isTrashed = 0 ORDER BY isPinned DESC, timestamp DESC.
        // SQLite resuelve el WHERE + el ORDER BY en una sola pasada de índice.
        Index(value = ["isTrashed", "isPinned", "timestamp"], name = "index_notes_isTrashed_isPinned_timestamp"),

        // Índice para la query del trash: WHERE isTrashed = 1 ORDER BY timestamp DESC.
        // Sin este, SQLite reusaría el compuesto pero filtrando por isPinned primero.
        Index(value = ["isTrashed", "timestamp"], name = "index_notes_isTrashed_timestamp"),

        // Índice para el lookup puntual de la nota sintética [[BINOT_SYSTEM_LABELS]].
        // Se consulta desde el init de HistoryViewModel, ResultViewModel y varios CRUD de labels.
        Index(value = ["title"], name = "index_notes_title")
    ]
)
@Immutable
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val rawText: String,
    val summary: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val audioPath: String? = null,
    val label: String? = null,
    val isTrashed: Boolean = false,
    val originalRawText: String? = null,
    val highlightsInfo: String? = null // Format: JSON Array String -> [{"text":"highlighted_word", "note":"user_note"}]
)