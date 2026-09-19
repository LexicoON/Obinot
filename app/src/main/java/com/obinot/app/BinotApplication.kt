package com.obinot.app

import android.app.Application
import androidx.room.Room
import com.obinot.app.data.AppDatabase
import com.obinot.app.data.LabelRepository
import com.obinot.app.data.NoteRepository
import com.obinot.app.data.SettingsRepository
import com.obinot.app.utils.AudioRecorderManager
import com.obinot.app.utils.CrashHandler

class BinotApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()

        // Instalar el handler de crashes lo antes posible. Si algo falla en
        // AppContainer (Room, DataStore, etc.), queremos que el usuario vea
        // una pantalla útil en vez de un cierre silencioso.
        CrashHandler(this).install()

        container = AppContainer(this)
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
            AppDatabase.MIGRATION_8_9
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