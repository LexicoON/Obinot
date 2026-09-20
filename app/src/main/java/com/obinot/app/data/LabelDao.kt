package com.obinot.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {

    /** Todos los labels del catálogo, ordenados alfabéticamente. */
    @Query("SELECT * FROM labels ORDER BY name COLLATE NOCASE ASC")
    fun getAllLabels(): Flow<List<LabelEntity>>

    /** Versión suspend para uso puntual (no reactivo). */
    @Query("SELECT * FROM labels ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllLabelsSync(): List<LabelEntity>

    /** Busca un label por nombre exacto. Devuelve null si no existe. */
    @Query("SELECT * FROM labels WHERE name = :name LIMIT 1")
    suspend fun getLabel(name: String): LabelEntity?

    /**
     * Inserta un label. Si ya existe (mismo nombre), lo ignora silenciosamente.
     * Esto es lo que garantiza que "Trabajo" nunca se duplique en el catálogo.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(label: LabelEntity)

    /** Inserta o reemplaza. Útil cuando querés forzar el color. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(label: LabelEntity)

    /** Actualiza el color de un label existente. */
    @Query("UPDATE labels SET colorHex = :colorHex WHERE name = :name")
    suspend fun updateColor(name: String, colorHex: String)

    /**
     * Renombra un label. OJO: esto NO actualiza las notas que usan el label viejo.
     * Esa migración la hace el ViewModel (ver ronda de History).
     */
    @Query("UPDATE labels SET name = :newName WHERE name = :oldName")
    suspend fun renameLabel(oldName: String, newName: String)

    /** Elimina un label del catálogo. Las notas que lo usen quedarán con un string huérfano. */
    @Query("DELETE FROM labels WHERE name = :name")
    suspend fun deleteLabel(name: String)

    /** Cuenta cuántos labels hay en el catálogo. */
    @Query("SELECT COUNT(*) FROM labels")
    suspend fun count(): Int
}
