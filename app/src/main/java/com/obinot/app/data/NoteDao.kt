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
     * Búsqueda Full-Text sobre title/rawText/summary, devuelta por timestamp.
     *
     * FTS4 solo filtra candidatos (MATCH sobre índice invertido). El ranking
     * por relevancia NO se hace acá: lo hace HistoryViewModel después, con
     * BM25 en Kotlin (TextChunker.rankByBm25). Motivo: Room 2.7.0 no expone
     * `@Fts5`, así que no tenemos `bm25()` nativo de SQLite.
     *
     * El `query` debe venir sanitizado (ver `sanitizeFtsQuery` en HistoryViewModel).
     */
    @Query(
        "SELECT notes.* FROM notes " +
        "INNER JOIN notes_fts ON notes.id = notes_fts.docid " +
        "WHERE notes.isTrashed = 0 AND notes_fts MATCH :query " +
        "ORDER BY notes.isPinned DESC, notes.timestamp DESC"
    )
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes " +
        "WHERE isTrashed = 0 AND title != '[[BINOT_SYSTEM_LABELS]]' " +
        "ORDER BY timestamp DESC LIMIT :limit"
    )
    fun getRecentNotes(limit: Int = 16): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE title = '[[BINOT_SYSTEM_LABELS]]' LIMIT 1")
    fun getSystemNote(): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE title = '[[BINOT_SYSTEM_LABELS]]' LIMIT 1")
    suspend fun getSystemNoteSync(): NoteEntity?

    @Query(
        "SELECT label FROM notes " +
        "WHERE isTrashed = 0 AND label IS NOT NULL AND label != '' " +
        "AND title != '[[BINOT_SYSTEM_LABELS]]'"
    )
    fun getAllLabelStrings(): Flow<List<String>>

    @Query(
        "SELECT * FROM notes " +
        "WHERE isTrashed = 0 AND label LIKE '%' || :label || '%' " +
        "AND title != '[[BINOT_SYSTEM_LABELS]]'"
    )
    suspend fun getNotesWithLabelSync(label: String): List<NoteEntity>

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