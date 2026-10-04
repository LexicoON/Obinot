package com.obinot.app

import android.app.Application
import androidx.room.Room
import com.obinot.app.data.AppDatabase
import com.obinot.app.data.LabelRepository
import com.obinot.app.data.NoteRepository
import com.obinot.app.data.SettingsRepository
import com.obinot.app.utils.AudioRecorderManager
import com.obinot.app.utils.CrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BinotApplication : Application() {

    lateinit var container: AppContainer

    // Scope de aplicación, vive lo que dura el proceso. Se usa para migraciones
    // one-time que no deben bloquear el arranque de la UI.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Instalar el handler de crashes lo antes posible. Si algo falla en
        // AppContainer (Room, DataStore, etc.), queremos que el usuario vea
        // una pantalla útil en vez de un cierre silencioso.
        CrashHandler(this).install()

        container = AppContainer(this)

        // Migración one-time del native picker: lo activa para usuarios que
        // vienen de versiones anteriores. Idempotente.
        applicationScope.launch {
            container.settingsRepository.migrateNativePickerIfNeeded()
        }
    }
}

class AppContainer(private val application: Application) {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(application, AppDatabase::class.java, "binot_db")
        .addMigrations(
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10
        )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }

    val noteRepository: NoteRepository by lazy {
        NoteRepository(database.noteDao())
    }

    val labelRepository: LabelRepository by lazy {
        LabelRepository(database.labelDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(application)
    }

    val audioRecorderManager: AudioRecorderManager by lazy {
        AudioRecorderManager(application)
    }
}