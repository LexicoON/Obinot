package com.obinot.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NoteEntity::class, LabelEntity::class, NoteFtsEntity::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun labelDao(): LabelDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN originalRawText TEXT DEFAULT NULL")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN highlightsInfo TEXT DEFAULT NULL")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS labels (
                        name TEXT NOT NULL PRIMARY KEY,
                        colorHex TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                )
                """.trimIndent()
                )
            }
        }

        /**
         * 7 → 8: agrega índices compuestos para la lista de notas, el trash, y el
         * lookup de la system note. Los nombres coinciden exactamente con la
         * convención de Room (`index_<tabla>_<cols>`), si no la validación de
         * schema falla al abrir la DB.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                    "`index_notes_isTrashed_isPinned_timestamp` " +
                    "ON `notes` (`isTrashed`, `isPinned`, `timestamp`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                    "`index_notes_isTrashed_timestamp` " +
                    "ON `notes` (`isTrashed`, `timestamp`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                    "`index_notes_title` " +
                    "ON `notes` (`title`)"
                )
            }
        }

        /**
         * 8 → 9: agrega FTS4 sobre title/rawText/summary.
         *
         * Los nombres de los triggers y la estructura del CREATE VIRTUAL TABLE
         * deben coincidir EXACTAMENTE con lo que Room genera automáticamente para
         * `NoteFtsEntity` (annotation @Fts4(contentEntity = NoteEntity::class)).
         *
         * Los 4 triggers son la convención de Room:
         *   - room_fts_content_sync_<ftsTable>_BEFORE_UPDATE
         *   - room_fts_content_sync_<ftsTable>_BEFORE_DELETE
         *   - room_fts_content_sync_<ftsTable>_AFTER_UPDATE
         *   - room_fts_content_sync_<ftsTable>_AFTER_INSERT
         *
         * Si renombrás algo acá, la validación de schema al abrir la DB va a
         * fallar con "Migration didn't properly handle: notes_fts".
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) Crear la tabla virtual FTS4 con external content.
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` " +
                    "USING FTS4(`title` TEXT NOT NULL, `rawText` TEXT NOT NULL, `summary` TEXT, " +
                    "content=`notes`)"
                )

                // 2) Triggers de sincronización (los 4 que Room genera).
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_UPDATE " +
                    "BEFORE UPDATE ON `notes` BEGIN " +
                    "DELETE FROM `notes_fts` WHERE `docid`=OLD.`rowid`; " +
                    "END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_DELETE " +
                    "BEFORE DELETE ON `notes` BEGIN " +
                    "DELETE FROM `notes_fts` WHERE `docid`=OLD.`rowid`; " +
                    "END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_UPDATE " +
                    "AFTER UPDATE ON `notes` BEGIN " +
                    "INSERT INTO `notes_fts`(`docid`, `title`, `rawText`, `summary`) " +
                    "VALUES (NEW.`rowid`, NEW.`title`, NEW.`rawText`, NEW.`summary`); " +
                    "END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_INSERT " +
                    "AFTER INSERT ON `notes` BEGIN " +
                    "INSERT INTO `notes_fts`(`docid`, `title`, `rawText`, `summary`) " +
                    "VALUES (NEW.`rowid`, NEW.`title`, NEW.`rawText`, NEW.`summary`); " +
                    "END"
                )

                // 3) Poblar el índice con las filas existentes. El comando 'rebuild'
                // lee desde la tabla content (notes) y reconstruye el índice FTS.
                db.execSQL("INSERT INTO `notes_fts`(`notes_fts`) VALUES('rebuild')")
            }
        }

        /**
         * 9 → 10: agrega `chatHistory` a la tabla notes.
         *
         * Guarda el historial del chat "Ask AI about this note" como JSON array
         * de {role, content}. Se persiste por nota, así el usuario ve la
         * conversación al reabrir. No afecta FTS (no está indexado).
         *
         * El .binot NO incluye este campo (exportNoteToBinot usa un data.json
         * explícito). El backup .obinotbak SÍ lo incluye (Moshi serializa la
         * NoteEntity completa).
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN chatHistory TEXT DEFAULT NULL")
            }
        }
    }
}