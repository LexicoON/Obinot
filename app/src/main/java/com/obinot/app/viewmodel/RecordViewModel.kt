package com.obinot.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.obinot.app.data.NoteEntity
import com.obinot.app.data.NoteRepository
import com.obinot.app.data.SettingsRepository
import com.obinot.app.data.providers.ProviderCapability
import com.obinot.app.data.providers.ProviderRouter
import com.obinot.app.utils.AudioRecorderManager
import com.obinot.app.utils.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordViewModel(
    private val audioRecorderManager: AudioRecorderManager,
    private val repository: NoteRepository,
    private val geminiApiKey: String,
    private val groqApiKey: String,
    private val appContext: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /**
     * Router de providers. Se instancia una vez por ViewModel; los providers
     * en sí son stateless, así que no hay costo real más allá de la lista.
     */
    private val router: ProviderRouter = ProviderRouter.default()

    val isRecording: StateFlow<Boolean> = audioRecorderManager.isRecording
    val amplitude: StateFlow<Float> = audioRecorderManager.amplitude
    val recognizedText: StateFlow<String> = audioRecorderManager.recognizedText

    /**
     * Estado del toggle "Live Transcript" (Settings → Recording Mode → Accurate).
     * RecordScreen lo usa para decidir qué cartel mostrar durante la grabación:
     * - ON:  "Listening..." (hay recognizer corriendo)
     * - OFF: "Recording... (will transcribe with AI)" (solo se graba audio)
     */
    val liveTranscriptEnabled: StateFlow<Boolean> = settingsRepository.liveTranscriptFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()
    private var timerJob: Job? = null

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private var pendingAudioPath: String? = null

    /**
     * Notas recientes para el carrusel de RecordScreen.
     *
     * Antes: polling cada 1.5s con getAllNotesSync(). Eso corría un full table
     * scan continuo en gama baja y mantenía la DB caliente aunque la pantalla
     * no estuviera visible.
     *
     * Ahora: Flow reactivo directo de Room. Cuando la tabla cambia (insert/update/
     * delete), Room re-emite y `stateIn` propaga. Cuando no hay subscribers activos
     * por más de 5s, el upstream se cancela solo — cero CPU en background.
     */
    val recentNotes: StateFlow<List<NoteEntity>> = repository.getRecentNotes(16)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleRecording(isEmulator: Boolean, recordMode: Int) {
        if (isRecording.value) {
            pendingAudioPath = audioRecorderManager.stopRecording()
            stopTimer()
            _isPaused.value = false
            RecordingService.stop(appContext)
        } else {
            _isPaused.value = false
            pendingAudioPath = null
            // Leer el flag de live transcript para pasárselo al manager.
            // Fast (mode 0) siempre corre el recognizer; Accurate (mode 1) lo hace
            // solo si el usuario lo pidió.
            val liveTranscript = liveTranscriptEnabled.value
            audioRecorderManager.startRecording(
                isEmulator = isEmulator,
                mode = recordMode,
                liveTranscriptEnabled = liveTranscript
            )
            startTimer()
            maybeStartBackgroundService()
        }
    }

    fun stopRecordingInstant() {
        pendingAudioPath = audioRecorderManager.stopRecording()
        stopTimer()
        _isPaused.value = false
        _recordingSeconds.value = 0
        RecordingService.stop(appContext)
    }

    private fun maybeStartBackgroundService() {
        viewModelScope.launch {
            if (settingsRepository.backgroundRecordingFlow.first()) {
                RecordingService.start(appContext)
            }
        }
    }

    fun pauseRecording() {
        if (!isRecording.value || _isPaused.value) return
        _isPaused.value = true
        stopTimer()
        audioRecorderManager.pauseRecording()
    }

    fun resumeRecording() {
        if (!isRecording.value || !_isPaused.value) return
        _isPaused.value = false
        resumeTimer()
        audioRecorderManager.resumeRecording()
    }

    fun stopFromPaused(isEmulator: Boolean) {
        if (!_isPaused.value) return
        pendingAudioPath = audioRecorderManager.stopRecording()
        _isPaused.value = false
        _recordingSeconds.value = 0
        RecordingService.stop(appContext)
    }

    private fun startTimer() {
        _recordingSeconds.value = 0
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _recordingSeconds.value += 1
            }
        }
    }

    private fun resumeTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _recordingSeconds.value += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    suspend fun saveNote(recordMode: Int, provider: Int = 0): Boolean {
        delay(300)

        val path = pendingAudioPath

        // Modo Accurate (1): se guarda el audio y, si el live transcript estaba
        // activado y el recognizer capturó algo, se adjunta como transcripción
        // preliminar con un marcador. Si no hay texto, se guarda como
        // "Pending Transcription" y el ResultViewModel dispara la transcripción
        // con IA automáticamente al abrir la nota.
        //
        // Modo Fast (0): se guarda solo el texto del recognizer. Si está vacío,
        // no se guarda la nota.
        val text = if (recordMode == 1) {
            val phoneText = recognizedText.value.trim()
            if (phoneText.isNotEmpty()) {
                "${AudioRecorderManager.PHONE_TRANSCRIPTION_MARKER}\n$phoneText"
            } else {
                AudioRecorderManager.PENDING_TRANSCRIPTION
            }
        } else {
            recognizedText.value.trim()
        }

        if (recordMode == 0 && text.isEmpty()) {
            return false
        }

        val note = NoteEntity(
            title = "",
            rawText = text,
            summary = null,
            isPinned = false,
            audioPath = path
        )

        val id = withContext(Dispatchers.IO) { repository.insert(note).toInt() }

        // El título se genera con IA solo si es modo Fast (en Accurate el
        // texto aún no existe hasta que se transcriba). El router se encarga
        // de elegir el provider según el modo configurado.
        if (recordMode == 0) {
            generateTitleForNote(id, text, provider)
        }

        pendingAudioPath = null
        return true
    }

    /**
     * Genera un título corto (3-5 palabras) usando el router.
     *
     * En modo Dynamic los títulos van primero a Groq (Gemini se reserva
     * para audio grande y texto largo). En modos explícitos (0 o 1) solo
     * hay un provider con key configurada.
     *
     * Si el router no encuentra candidatos (ninguna key configurada), no
     * hace nada y la nota se queda con título vacío — la UI ya maneja ese
     * caso mostrando "Empty Note" o similar.
     */
    private fun generateTitleForNote(noteId: Int, text: String, provider: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiKeys = buildApiKeysMap(provider)
                if (apiKeys.isEmpty()) return@launch

                val systemPrompt = "You are a title generator. Output ONLY a 3-5 word title in the same language as the input. No quotes, no explanation."
                val userPrompt = "Text:\n${text.take(500)}"

                val aiTitle = router.generateTextWithFallback(
                    capability = ProviderCapability.TITLE,
                    apiKeys = apiKeys,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    // Dynamic: los títulos van a Groq para no gastar cuota de Gemini.
                    preferredProviderId = if (provider == 2 && apiKeys.containsKey("groq")) "groq" else null
                )

                if (!aiTitle.isNullOrBlank()) {
                    val savedNote = repository.getNoteById(noteId)
                    if (savedNote != null) {
                        repository.update(savedNote.copy(title = aiTitle))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Map de API keys según el modo (0 = Gemini, 1 = Groq, 2 = Dynamic).
     * Lee las listas completas desde settings para que la rotación de keys
     * funcione también al generar títulos.
     */
    private suspend fun buildApiKeysMap(provider: Int): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        val gemini = settingsRepository.geminiApiKeysFlow.first().filter { it.isNotBlank() }
            .ifEmpty { listOf(geminiApiKey).filter { it.isNotBlank() } }
        val groq = settingsRepository.groqApiKeysFlow.first().filter { it.isNotBlank() }
            .ifEmpty { listOf(groqApiKey).filter { it.isNotBlank() } }
        when (provider) {
            0 -> if (gemini.isNotEmpty()) map["gemini"] = gemini
            1 -> if (groq.isNotEmpty()) map["groq"] = groq
            else -> {
                if (gemini.isNotEmpty()) map["gemini"] = gemini
                if (groq.isNotEmpty()) map["groq"] = groq
            }
        }
        return map
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorderManager.stopRecording()
        stopTimer()
        _isPaused.value = false
        RecordingService.stop(appContext)
    }

    companion object {
        fun provideFactory(
            audioRecorderManager: AudioRecorderManager,
            repository: NoteRepository,
            geminiApiKey: String,
            groqApiKey: String,
            appContext: Context,
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RecordViewModel(audioRecorderManager, repository, geminiApiKey, groqApiKey, appContext, settingsRepository) as T
                }
            }
    }
}