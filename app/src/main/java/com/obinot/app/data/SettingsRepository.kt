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
        val API_KEY = stringPreferencesKey("api_key")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val THEME_MODE_KEY = intPreferencesKey("theme_mode")
        val RECORD_MODE_KEY = intPreferencesKey("record_mode")
        val AI_PROVIDER_KEY = intPreferencesKey("ai_provider") // 0 = Gemini, 1 = Groq, 2 = Dynamic

        // --- GLOBAL AI PREFERENCES ---
        val AI_LANGUAGE_KEY = stringPreferencesKey("ai_language")
        val AI_TASK_KEY = intPreferencesKey("ai_task") // 0: Tidy Up, 1: Summarize, 2: Analyze
        val AI_FORMAT_KEY = intPreferencesKey("ai_format") // 0: Paragraphs, 1: Bullets
        val BACKGROUND_RECORDING_KEY = booleanPreferencesKey("background_recording_enabled")

        // --- LIVE TRANSCRIPT (Accurate mode, beta) ---
        // Default OFF. Con esto apagado, Accurate NO arranca el SpeechRecognizer del
        // teléfono (que es el que falla en muchos dispositivos), y solo graba audio
        // para que la IA lo transcriba después.
        val LIVE_TRANSCRIPT_KEY = booleanPreferencesKey("live_transcript_enabled")

        // --- AUTO COMPRESSION ---
        // 0 = Off, 1 = Balanced (target 24 MB), 2 = Max (target 15 MB)
        val AUTO_COMPRESSION_MODE_KEY = intPreferencesKey("auto_compression_mode")

        // --- MIX COUNTER ---
        val MIX_COUNTER_KEY = intPreferencesKey("mix_counter")

        // --- NATIVE AUDIO PICKER (beta) ---
        val NATIVE_PICKER_KEY = booleanPreferencesKey("native_audio_picker_enabled")

        // --- COLOR STYLE ---
        // 0 = Tonal Spot, 1 = Vibrant, 2 = Expressive, 3 = Fruit Salad,
        // 4 = Neutral, 5 = Fidelity, 6 = Monochrome
        val COLOR_STYLE_KEY = intPreferencesKey("color_style")

        // --- APP LANGUAGE (per-app locale) ---
        // Valores: "device" (default, sigue el idioma del sistema), "en", "es".
        val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")

        // --- D1b: AUTO-PROCESS TRANSCRIPTIONS ---
        // Default ON para no romper el comportamiento previo.
        val AUTO_PROCESS_KEY = booleanPreferencesKey("auto_process_enabled")

        // --- F4b: READING FONT ---
        // 0 = Sans (default), 1 = Serif, 2 = Mono.
        val READING_FONT_KEY = intPreferencesKey("reading_font")

        // --- 2.1: AI CHAT ABOUT NOTE ---
        // Primera vez que el usuario abre ResultScreen, mostramos un popup
        // explicando que el botón de 3-puntos tiene long press para chat
        // directo. Después de verlo una vez, no se vuelve a mostrar.
        val AI_CHAT_TOOLTIP_SHOWN_KEY = booleanPreferencesKey("ai_chat_tooltip_shown")
    }

    val userNameFlow: Flow<String> = context.dataStore.data.map { it[USER_NAME_KEY] ?: "" }
    val geminiApiKeyFlow: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val groqApiKeyFlow: Flow<String> = context.dataStore.data.map { it[GROQ_API_KEY] ?: "" }
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
    // Default OFF: el picker nativo es beta y se opta explícitamente.
    val nativePickerFlow: Flow<Boolean> = context.dataStore.data.map { it[NATIVE_PICKER_KEY] ?: false }
    val aiChatTooltipShownFlow: Flow<Boolean> = context.dataStore.data.map { it[AI_CHAT_TOOLTIP_SHOWN_KEY] ?: false }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[USER_NAME_KEY] = name }
    }

    suspend fun saveGeminiApiKey(key: String) {
        context.dataStore.edit { it[API_KEY] = key }
    }

    suspend fun saveGroqApiKey(key: String) {
        context.dataStore.edit { it[GROQ_API_KEY] = key }
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

    suspend fun saveNativePicker(enabled: Boolean) {
        context.dataStore.edit { it[NATIVE_PICKER_KEY] = enabled }
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
}