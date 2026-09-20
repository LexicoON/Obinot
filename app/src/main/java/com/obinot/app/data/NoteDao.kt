package com.obinot.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isTrashed = 0 ORDER BY isPinned DESC, timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY timestamp DESC")
    fun getTrashedNotes(): Flow<List<NoteEntity>>

    /**
     * Búsqueda Full-Text sobre title/rawText/summary.
     *
     * El `query` debe venir sanitizado (ver `sanitizeFtsQuery` en HistoryViewModel):
     * tokens separados por espacios, con `*` al final para prefix matching, sin
     * caracteres especiales de FTS. Si el query está vacío o mal formado, SQLite
     * puede fallar al parsearlo — por eso el sanitizado es responsabilidad del caller.
     *
     * El JOIN con `notes_fts` es sobre `docid` (que coincide con el rowid de `notes`).
     * La proyección `notes.*` es necesaria para que Room arme la NoteEntity completa.
     */
    @Query(
        "SELECT notes.* FROM notes " +
        "INNER JOIN notes_fts ON notes.id = notes_fts.docid " +
        "WHERE notes.isTrashed = 0 AND notes_fts MATCH :query " +
        "ORDER BY notes.isPinned DESC, notes.timestamp DESC"
    )
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    /**
     * Notas recientes para el carrusel de RecordScreen.
     *
     * Antes RecordViewModel hacía getAllNotesSync() cada 1.5s y filtraba/ordenaba
     * en memoria. Ahora la DB devuelve solo 16 filas y Room re-emite automáticamente
     * cuando la tabla cambia, así que no hace falta ningún polling.
     */
    @Query(
        "SELECT * FROM notes " +
        "WHERE isTrashed = 0 AND title != '[[BINOT_SYSTEM_LABELS]]' " +
        "ORDER BY timestamp DESC LIMIT :limit"
    )
    fun getRecentNotes(limit: Int = 16): Flow<List<NoteEntity>>

    /**
     * Nota sintética que almacena el catálogo de labels creados a mano.
     * Lookup puntual — usa el índice index_notes_title.
     */
    @Query("SELECT * FROM notes WHERE title = '[[BINOT_SYSTEM_LABELS]]' LIMIT 1")
    fun getSystemNote(): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE title = '[[BINOT_SYSTEM_LABELS]]' LIMIT 1")
    suspend fun getSystemNoteSync(): NoteEntity?

    /**
     * Proyección liviana: solo la columna label de notas no-trasheadas (excluyendo la
     * system note). Evita cargar rawText/summary/highlightsInfo de cada nota cuando
     * lo único que se necesita es el catálogo de labels.
     *
     * Cada fila es un string "label1|label2|label3"; el split se hace en Kotlin.
     */
    @Query(
        "SELECT label FROM notes " +
        "WHERE isTrashed = 0 AND label IS NOT NULL AND label != '' " +
        "AND title != '[[BINOT_SYSTEM_LABELS]]'"
    )
    fun getAllLabelStrings(): Flow<List<String>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0")
    suspend fun getAllNotesSync(): List<NoteEntity>

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun emptyTrash()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Int)

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Int): NoteEntity?

    @Query("UPDATE notes SET summary = null WHERE summary IS NOT NULL")
    suspend fun resetAllSummaries()
}