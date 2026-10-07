package com.obinot.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val USER_NAME_KEY = stringPreferencesKey("user_name")

        // --- API KEYS (listas, separadas por \n) ---
        // A partir de 2.2 soportamos rotación: cada provider puede tener
        // hasta N keys. La primera es la "principal". Las listas se serializan
        // como strings con separator \n (las API keys no contienen \n).
        val GEMINI_API_KEYS = stringPreferencesKey("gemini_api_keys")
        val GROQ_API_KEYS = stringPreferencesKey("groq_api_keys")
        val NVIDIA_API_KEYS = stringPreferencesKey("nvidia_api_keys")

        // --- API KEYS (singular, retrocompat con <=2.1.1) ---
        // Se leen si las listas nuevas no existen. Se limpian cuando el user
        // guarda algo con la UI nueva (evita duplicaciones).
        val API_KEY = stringPreferencesKey("api_key")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")

        // --- KEY ROTATION (Release 2) ---
        val KEY_ROTATION_ENABLED = booleanPreferencesKey("key_rotation_enabled")
        // Alpha unlock: permite más de 3 keys por provider (hasta 6).
        val ALPHA_UNLOCKED = booleanPreferencesKey("alpha_unlocked")

        // --- GLOBAL AI PREFERENCES ---
        val THEME_MODE_KEY = intPreferencesKey("theme_mode")
        val RECORD_MODE_KEY = intPreferencesKey("record_mode")
        val AI_PROVIDER_KEY = intPreferencesKey("ai_provider")

        val AI_LANGUAGE_KEY = stringPreferencesKey("ai_language")
        val AI_TASK_KEY = intPreferencesKey("ai_task")
        val AI_FORMAT_KEY = intPreferencesKey("ai_format")
        val BACKGROUND_RECORDING_KEY = booleanPreferencesKey("background_recording_enabled")

        val LIVE_TRANSCRIPT_KEY = booleanPreferencesKey("live_transcript_enabled")

        val AUTO_COMPRESSION_MODE_KEY = intPreferencesKey("auto_compression_mode")

        val MIX_COUNTER_KEY = intPreferencesKey("mix_counter")

        val NATIVE_PICKER_KEY = booleanPreferencesKey("native_audio_picker_enabled")
        val NATIVE_PICKER_MIGRATED_KEY = booleanPreferencesKey("native_picker_migrated")

        val COLOR_STYLE_KEY = intPreferencesKey("color_style")

        val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")

        val AUTO_PROCESS_KEY = booleanPreferencesKey("auto_process_enabled")

        val READING_FONT_KEY = intPreferencesKey("reading_font")

        val AI_CHAT_TOOLTIP_SHOWN_KEY = booleanPreferencesKey("ai_chat_tooltip_shown")

        val BINOT_TAB_PERMISSION_PROMPTED_KEY = booleanPreferencesKey("binot_tab_permission_prompted")

        /** Separator para serializar listas de API keys en DataStore. */
        private const val KEY_LIST_SEPARATOR = "\n"

        /** Convierte una lista de keys a string para persistir. */
        fun joinKeys(keys: List<String>): String =
            keys.map { it.trim() }.filter { it.isNotBlank() }
                .joinToString(KEY_LIST_SEPARATOR)

        /** Convierte un string persistido a lista de keys. */
        fun splitKeys(stored: String?): List<String> =
            stored?.split(KEY_LIST_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
    }

    // ============================================================
    // API KEYS (nuevas, listas)
    // ============================================================

    /**
     * Lista completa de API keys de Gemini. Retrocompat: si la lista nueva
     * no existe pero la key singular vieja sí, la devuelve como lista de 1.
     */
    val geminiApiKeysFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val fromList = splitKeys(prefs[GEMINI_API_KEYS])
        if (fromList.isNotEmpty()) fromList
        else prefs[API_KEY]?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
    }

    val groqApiKeysFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val fromList = splitKeys(prefs[GROQ_API_KEYS])
        if (fromList.isNotEmpty()) fromList
        else prefs[GROQ_API_KEY]?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
    }

    val nvidiaApiKeysFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        splitKeys(prefs[NVIDIA_API_KEYS])
    }

    // --- Retrocompat: singular flows. Devuelven la primera key, o "". ---

    val geminiApiKeyFlow: Flow<String> = geminiApiKeysFlow.map { it.firstOrNull() ?: "" }
    val groqApiKeyFlow: Flow<String> = groqApiKeysFlow.map { it.firstOrNull() ?: "" }
    val nvidiaApiKeyFlow: Flow<String> = nvidiaApiKeysFlow.map { it.firstOrNull() ?: "" }

    // ============================================================
    // Otros flows
    // ============================================================

    val userNameFlow: Flow<String> = context.dataStore.data.map { it[USER_NAME_KEY] ?: "" }
    val themeModeFlow: Flow<Int> = context.dataStore.data.map { it[THEME_MODE_KEY] ?: 0 }
    val recordModeFlow: Flow<Int> = context.dataStore.data.map { it[RECORD_MODE_KEY] ?: 0 }
    val aiProviderFlow: Flow<Int> = context.dataStore.data.map { it[AI_PROVIDER_KEY] ?: 0 }
    val backgroundRecordingFlow: Flow<Boolean> = context.dataStore.data.map { it[BACKGROUND_RECORDING_KEY] ?: false }
    val liveTranscriptFlow: Flow<Boolean> = context.dataStore.data.map { it[LIVE_TRANSCRIPT_KEY] ?: false }

    val aiLanguageFlow: Flow<String> = context.dataStore.data.map { it[AI_LANGUAGE_KEY] ?: "English" }
    val aiTaskFlow: Flow<Int> = context.dataStore.data.map { it[AI_TASK_KEY] ?: 0 }
    val aiFormatFlow: Flow<Int> = context.dataStore.data.map { it[AI_FORMAT_KEY] ?: 0 }

    val autoCompressionModeFlow: Flow<Int> = context.dataStore.data.map { it[AUTO_COMPRESSION_MODE_KEY] ?: 1 }
    val mixCounterFlow: Flow<Int> = context.dataStore.data.map { it[MIX_COUNTER_KEY] ?: 0 }
    val colorStyleFlow: Flow<Int> = context.dataStore.data.map { it[COLOR_STYLE_KEY] ?: 0 }
    val appLanguageFlow: Flow<String> = context.dataStore.data.map { it[APP_LANGUAGE_KEY] ?: "device" }
    val autoProcessFlow: Flow<Boolean> = context.dataStore.data.map { it[AUTO_PROCESS_KEY] ?: true }
    val readingFontFlow: Flow<Int> = context.dataStore.data.map { it[READING_FONT_KEY] ?: 0 }

    // --- Key rotation flags ---
    val keyRotationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_ROTATION_ENABLED] ?: false }
    val alphaUnlockedFlow: Flow<Boolean> = context.dataStore.data.map { it[ALPHA_UNLOCKED] ?: false }

    /**
     * Native picker: default ON desde 2.2. Para usuarios que actualizan desde
     * versiones anteriores, forzamos la activación una sola vez vía
     * NATIVE_PICKER_MIGRATED_KEY. Si el usuario después lo desactiva
     * manualmente (saveNativePicker), el flag queda seteado y respetamos su
     * elección.
     */
    val nativePickerFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val migrated = prefs[NATIVE_PICKER_MIGRATED_KEY] ?: false
        if (!migrated) {
            true
        } else {
            prefs[NATIVE_PICKER_KEY] ?: true
        }
    }

    val aiChatTooltipShownFlow: Flow<Boolean> = context.dataStore.data.map { it[AI_CHAT_TOOLTIP_SHOWN_KEY] ?: false }
    val binotTabPermissionPromptedFlow: Flow<Boolean> = context.dataStore.data.map { it[BINOT_TAB_PERMISSION_PROMPTED_KEY] ?: false }

    // ============================================================
    // Setters — API keys (nuevos, listas)
    // ============================================================

    suspend fun saveGeminiApiKeys(keys: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[GEMINI_API_KEYS] = joinKeys(keys)
            // Limpiamos la key singular vieja para evitar duplicaciones
            // cuando el user guarda desde la UI nueva.
            prefs.remove(API_KEY)
        }
    }

    suspend fun saveGroqApiKeys(keys: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[GROQ_API_KEYS] = joinKeys(keys)
            prefs.remove(GROQ_API_KEY)
        }
    }

    suspend fun saveNvidiaApiKeys(keys: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[NVIDIA_API_KEYS] = joinKeys(keys)
        }
    }

    // --- Retrocompat: los setters viejos guardan lista de 1. ---

    suspend fun saveGeminiApiKey(key: String) = saveGeminiApiKeys(listOf(key))
    suspend fun saveGroqApiKey(key: String) = saveGroqApiKeys(listOf(key))

    // ============================================================
    // Setters — key rotation
    // ============================================================

    suspend fun saveKeyRotationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ROTATION_ENABLED] = enabled }
    }

    suspend fun saveAlphaUnlocked(enabled: Boolean) {
        context.dataStore.edit { it[ALPHA_UNLOCKED] = enabled }
    }

    // ============================================================
    // Setters — resto
    // ============================================================

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[USER_NAME_KEY] = name }
    }

    suspend fun saveThemeMode(mode: Int) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode }
    }

    suspend fun saveRecordMode(mode: Int) {
        context.dataStore.edit { it[RECORD_MODE_KEY] = mode }
    }

    suspend fun saveAiProvider(provider: Int) {
        context.dataStore.edit { it[AI_PROVIDER_KEY] = provider }
    }

    suspend fun saveAiLanguage(language: String) {
        context.dataStore.edit { it[AI_LANGUAGE_KEY] = language }
    }

    suspend fun saveAiTask(task: Int) {
        context.dataStore.edit { it[AI_TASK_KEY] = task }
    }

    suspend fun saveAiFormat(format: Int) {
        context.dataStore.edit { it[AI_FORMAT_KEY] = format }
    }

    suspend fun saveBackgroundRecording(enabled: Boolean) {
        context.dataStore.edit { it[BACKGROUND_RECORDING_KEY] = enabled }
    }

    suspend fun saveLiveTranscript(enabled: Boolean) {
        context.dataStore.edit { it[LIVE_TRANSCRIPT_KEY] = enabled }
    }

    suspend fun saveAutoCompressionMode(mode: Int) {
        context.dataStore.edit { it[AUTO_COMPRESSION_MODE_KEY] = mode.coerceIn(0, 2) }
    }

    /**
     * Persiste la elección del native picker y marca la migración como hecha.
     */
    suspend fun saveNativePicker(enabled: Boolean) {
        context.dataStore.edit {
            it[NATIVE_PICKER_KEY] = enabled
            it[NATIVE_PICKER_MIGRATED_KEY] = true
        }
    }

    /**
     * Migración one-time: activa el native picker para todos los usuarios
     * existentes. Idempotente.
     */
    suspend fun migrateNativePickerIfNeeded() {
        context.dataStore.edit { prefs ->
            val alreadyMigrated = prefs[NATIVE_PICKER_MIGRATED_KEY] ?: false
            if (!alreadyMigrated) {
                prefs[NATIVE_PICKER_KEY] = true
                prefs[NATIVE_PICKER_MIGRATED_KEY] = true
            }
        }
    }

    suspend fun saveColorStyle(style: Int) {
        context.dataStore.edit { it[COLOR_STYLE_KEY] = style.coerceIn(0, 6) }
    }

    suspend fun incrementMixCounter(): Int {
        var result = 0
        context.dataStore.edit { prefs ->
            result = ((prefs[MIX_COUNTER_KEY] ?: 0) + 1) % 1000
            prefs[MIX_COUNTER_KEY] = result
        }
        return result
    }

    suspend fun resetMixCounter() {
        context.dataStore.edit { it[MIX_COUNTER_KEY] = 0 }
    }

    suspend fun saveAppLanguage(lang: String) {
        context.dataStore.edit { it[APP_LANGUAGE_KEY] = lang }
    }

    suspend fun saveAutoProcess(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_PROCESS_KEY] = enabled }
    }

    suspend fun saveReadingFont(mode: Int) {
        context.dataStore.edit { it[READING_FONT_KEY] = mode.coerceIn(0, 2) }
    }

    suspend fun saveAiChatTooltipShown(shown: Boolean) {
        context.dataStore.edit { it[AI_CHAT_TOOLTIP_SHOWN_KEY] = shown }
    }

    suspend fun saveBinotTabPermissionPrompted(shown: Boolean) {
        context.dataStore.edit { it[BINOT_TAB_PERMISSION_PROMPTED_KEY] = shown }
    }
}