package com.obinot.app.data

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes = noteDao.getAllNotes()
    val trashedNotes = noteDao.getTrashedNotes()

    /** Búsqueda Full-Text. El `query` debe venir sanitizado (ver HistoryViewModel). */
    fun searchNotes(query: String) = noteDao.searchNotes(query)

    /** Flow de las últimas notas (sin la system note), para el carrusel de RecordScreen. */
    fun getRecentNotes(limit: Int = 16) = noteDao.getRecentNotes(limit)

    /** Nota sintética que guarda el catálogo manual de labels. */
    fun getSystemNote() = noteDao.getSystemNote()
    suspend fun getSystemNoteSync() = noteDao.getSystemNoteSync()

    /** Proyección liviana: solo los strings de label, sin cargar entidades completas. */
    fun getAllLabelStrings() = noteDao.getAllLabelStrings()

    suspend fun getAllNotesSync() = noteDao.getAllNotesSync()
    suspend fun deleteAllNotes() = noteDao.deleteAllNotes()
    suspend fun emptyTrash() = noteDao.emptyTrash()
    suspend fun insertNotes(notes: List<NoteEntity>) = noteDao.insertNotes(notes)

    suspend fun insert(note: NoteEntity) = noteDao.insertNote(note)
    suspend fun update(note: NoteEntity) = noteDao.updateNote(note)
    suspend fun deleteById(id: Int) = noteDao.deleteNoteById(id)
    suspend fun getNoteById(id: Int) = noteDao.getNoteById(id)

    suspend fun resetAllSummaries() = noteDao.resetAllSummaries()
}