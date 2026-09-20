package com.obinot.app.data

import kotlinx.coroutines.flow.Flow

class LabelRepository(private val labelDao: LabelDao) {

    val allLabels: Flow<List<LabelEntity>> = labelDao.getAllLabels()

    suspend fun getAllLabelsSync(): List<LabelEntity> = labelDao.getAllLabelsSync()

    suspend fun getLabel(name: String): LabelEntity? = labelDao.getLabel(name)

    /**
     * Garantiza que cada nombre en [names] exista en el catálogo.
     * Si no existe, se crea con el color default (gris).
     * Esto cubre el caso de: importar un backup con labels nuevos,
     * migrar desde la versión vieja, o cualquier label huérfano.
     */
    suspend fun ensureLabelsExist(names: List<String>) {
        val now = System.currentTimeMillis()
        names
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .forEach { name ->
            labelDao.insertIgnore(
                LabelEntity(
                    name = name,
                    colorHex = LabelEntity.DEFAULT_COLOR,
                    createdAt = now
                )
            )
        }
    }

    /** Crea un label nuevo con el color default. Si ya existe, lo ignora. */
    suspend fun createLabel(name: String, colorHex: String = LabelEntity.DEFAULT_COLOR) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
            labelDao.insertIgnore(
                LabelEntity(name = trimmed, colorHex = colorHex)
            )
    }

    /** Cambia el color de un label existente. */
    suspend fun updateColor(name: String, colorHex: String) {
        labelDao.updateColor(name, colorHex)
    }

    /**
     * Renombra un label en el catálogo.
     * IMPORTANTE: las notas que usen el nombre viejo seguirán usando ese string
     * hasta que el ViewModel las migre. Ese trabajo no se hace acá.
     */
    suspend fun renameLabel(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == oldName) return
            labelDao.renameLabel(oldName, trimmed)
    }

    /** Elimina un label del catálogo. */
    suspend fun deleteLabel(name: String) {
        labelDao.deleteLabel(name)
    }
}
