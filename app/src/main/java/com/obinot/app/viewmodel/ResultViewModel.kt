package com.obinot.app.viewmodel

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.obinot.app.R
import com.obinot.app.data.Content
import com.obinot.app.data.FileData
import com.obinot.app.data.GenerateContentRequest
import com.obinot.app.data.GeminiModels
import com.obinot.app.data.GroqChatRequest
import com.obinot.app.data.GroqMessage
import com.obinot.app.data.GroqModels
import com.obinot.app.data.LabelRepository
import com.obinot.app.data.NoteEntity
import com.obinot.app.data.NoteRepository
import com.obinot.app.data.Part
import com.obinot.app.data.RetrofitClient
import com.obinot.app.data.SettingsRepository
import com.obinot.app.utils.AudioCompressor
import com.obinot.app.utils.AudioRecorderManager
import com.obinot.app.utils.ImportExportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Un mensaje dentro de la conversación del chat sobre la nota.
 *
 * No se persiste a disco: el historial vive mientras el ModalBottomSheet
 * esté abierto. Al cerrar el sheet, el historial se descarta.
 */
data class ChatMessage(
    val role: String,   // "user" o "assistant"
    val content: String
)

class ResultViewModel(
    private val noteId: Int,
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val labelRepository: LabelRepository,
    private val appContext: Context
) : ViewModel() {

    private val _note = MutableStateFlow<NoteEntity?>(null)
    val note: StateFlow<NoteEntity?> = _note.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingMessage = MutableStateFlow("")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    val labelColors: StateFlow<Map<String, String>> = labelRepository.allLabels
        .map { labels -> labels.associate { it.name to it.colorHex } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allLabels: StateFlow<List<String>> = combine(
        noteRepository.getAllLabelStrings(),
        noteRepository.getSystemNote()
    ) { labelStrings, sysNote ->
        val customLabels = sysNote?.rawText
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val noteLabels = labelStrings.flatMap { raw ->
            raw.split("|").map { it.trim() }.filter { it.isNotBlank() }
        }
        (customLabels + noteLabels).distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Estado interno: true cuando el auto-procesamiento falló o está deshabilitado
     * y la nota quedó sin summary. Alimenta la visibilidad del chip "Analyze".
     */
    private val _processingFailed = MutableStateFlow(false)

    /**
     * true cuando hay que mostrar el chip "Analyze" arriba del contenido.
     * Combina:
     *  - La nota no tiene summary todavía
     *  - Tiene rawText válido (no pending, no marcador de transcripción del teléfono)
     *  - No hay un procesamiento en curso
     *  - O bien el toggle "Auto-process" está apagado, o bien el auto-procesamiento falló
     */
    val showAnalyzeChip: StateFlow<Boolean> = combine(
        _processingFailed,
        settingsRepository.autoProcessFlow,
        _note,
        _isLoading
    ) { failed, autoEnabled, currentNote, loading ->
        currentNote != null
            && currentNote.summary == null
            && currentNote.rawText.isNotBlank()
            && currentNote.rawText != AudioRecorderManager.PENDING_TRANSCRIPTION
            && !currentNote.rawText.startsWith(AudioRecorderManager.PHONE_TRANSCRIPTION_MARKER)
            && !loading
            && (!autoEnabled || failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        /**
     * Preferencia global de fuente de lectura. 0 = Sans, 1 = Serif, 2 = Mono.
     * Persistida en DataStore, sobrevive rotaciones y cierres de nota.
     */
    val readingFont: StateFlow<Int> = settingsRepository.readingFontFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _explainResult = MutableStateFlow<String?>(null)
    val explainResult: StateFlow<String?> = _explainResult.asStateFlow()

    private val _isExplaining = MutableStateFlow(false)
    val isExplaining: StateFlow<Boolean> = _isExplaining.asStateFlow()

    // ============================================================
    // AI Chat about this note (2.1)
    // ============================================================

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatSending = MutableStateFlow(false)
    val isChatSending: StateFlow<Boolean> = _isChatSending.asStateFlow()

    val aiChatTooltipShown: StateFlow<Boolean> = settingsRepository.aiChatTooltipShownFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    init {
        loadNote()
    }

    private fun loadNote() {
        viewModelScope.launch {
            val fetchedNote = noteRepository.getNoteById(noteId)
            _note.value = fetchedNote
            _processingFailed.value = false

            // Restaurar el historial del chat desde la nota persistida.
            if (fetchedNote != null) {
                _chatMessages.value = parseChatHistory(fetchedNote.chatHistory)
            }

            if (fetchedNote != null) {
                val rawText = fetchedNote.rawText
                val hasPhoneMarker = rawText.startsWith(AudioRecorderManager.PHONE_TRANSCRIPTION_MARKER)
                val isPending = rawText.isBlank() || rawText == AudioRecorderManager.PENDING_TRANSCRIPTION

                when {
                    hasPhoneMarker -> { /* nada — la UI maneja */ }
                    isPending && fetchedNote.audioPath != null -> transcribeAudio()
                    isPending && fetchedNote.audioPath == null -> {
                        _error.value = appContext.getString(R.string.error_audio_not_found_pending)
                    }
                    rawText.isNotBlank() -> checkAndTriggerAutoProcess(fetchedNote)
                }
            }
        }
    }

    private fun checkAndTriggerAutoProcess(noteToProcess: NoteEntity) {
        viewModelScope.launch {
            val lang = settingsRepository.aiLanguageFlow.first()
            val task = settingsRepository.aiTaskFlow.first()
            val format = settingsRepository.aiFormatFlow.first()
            val currentMeta = "<!--BINOT_META:${lang}_${task}_${format}-->"

            if (noteToProcess.summary == null) {
                val autoProcessEnabled = settingsRepository.autoProcessFlow.first()
                if (!autoProcessEnabled) {
                    // Auto-process apagado: no disparamos. Marcamos el estado
                    // para que el chip "Analyze" aparezca en la UI.
                    _processingFailed.value = true
                    return@launch
                }
                val providerForProcessing = settingsRepository.aiProviderFlow.first()
                processTextAuto(noteToProcess, lang, task, format, currentMeta, providerForProcessing)
            }
        }
    }

    /**
     * Dispara el procesamiento con IA de forma manual, ignorando el toggle
     * "Auto-process transcriptions". Se llama desde el chip "Analyze" del
     * ResultScreen. No hace nada si no hay texto que procesar, si ya hay un
     * summary, o si el procesamiento ya está en curso.
     */
    fun analyzeManually() {
        val currentNote = _note.value ?: return
        if (currentNote.summary != null) return
        if (currentNote.rawText.isBlank()) return
        if (currentNote.rawText == AudioRecorderManager.PENDING_TRANSCRIPTION) return
        if (currentNote.rawText.startsWith(AudioRecorderManager.PHONE_TRANSCRIPTION_MARKER)) return
        if (_isLoading.value) return

        viewModelScope.launch {
            val lang = settingsRepository.aiLanguageFlow.first()
            val task = settingsRepository.aiTaskFlow.first()
            val format = settingsRepository.aiFormatFlow.first()
            val currentMeta = "<!--BINOT_META:${lang}_${task}_${format}-->"
            val provider = settingsRepository.aiProviderFlow.first()
            processTextAuto(currentNote, lang, task, format, currentMeta, provider)
        }
    }

    fun saveReadingFont(mode: Int) {
        viewModelScope.launch { settingsRepository.saveReadingFont(mode) }
    }

    fun shareBinotFile(context: Context, onResult: (Uri?, String) -> Unit) {
        val currentNote = _note.value
        if (currentNote == null) {
            onResult(null, context.getString(R.string.error_note_empty))
            return
        }
        // Capturamos el mapa de colores ANTES de salir del hilo principal para
        // no leer un StateFlow desde Dispatchers.IO. Es un snapshot inmutable.
        val colorsSnapshot = labelColors.value
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _loadingMessage.value = context.getString(R.string.loading_generating_binot)
            val uri = ImportExportHelper.exportNoteToBinot(context, currentNote, colorsSnapshot)
            _isLoading.value = false

            if (uri != null) {
                launch(Dispatchers.Main) { onResult(uri, context.getString(R.string.error_file_ready)) }
            } else {
                launch(Dispatchers.Main) { onResult(null, context.getString(R.string.error_generate_binot_failed)) }
            }
        }
    }

    /**
     * Genera un archivo .md con el título + summary de la nota, y lo deja en
     * cache/shared_notes para ser compartido por FileProvider.
     *
     * Si la nota no tiene summary, exporta el rawText en su lugar.
     */
    fun exportMarkdownFile(context: Context, onResult: (Uri?, String) -> Unit) {
        val currentNote = _note.value
        if (currentNote == null) {
            onResult(null, context.getString(R.string.error_note_empty))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _loadingMessage.value = context.getString(R.string.loading_generating_binot)
            val uri = ImportExportHelper.exportNoteToMarkdown(context, currentNote)
            _isLoading.value = false

            if (uri != null) {
                launch(Dispatchers.Main) {
                    onResult(uri, context.getString(R.string.result_export_markdown_success))
                }
            } else {
                launch(Dispatchers.Main) {
                    onResult(null, context.getString(R.string.result_export_markdown_failed))
                }
            }
        }
    }

    /**
     * Genera el archivo .binot y lo guarda en la carpeta pública de Documentos
     * del usuario (API 29+). Devuelve el URI del archivo en cache listo para
     * ser compartido vía FileProvider. En API < 29 no se guarda en Documentos,
     * solo se comparte desde cache (comportamiento previo).
     */
    fun shareBinotToDocuments(context: Context, onResult: (Uri?, String) -> Unit) {
        val currentNote = _note.value
        if (currentNote == null) {
            onResult(null, context.getString(R.string.error_note_empty))
            return
        }
        val colorsSnapshot = labelColors.value
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _loadingMessage.value = context.getString(R.string.loading_generating_binot)

            // 1. Generamos el .binot en cache y obtenemos el FileProvider URI
            //    para compartir (funciona en todas las versiones de Android).
            val shareUri = ImportExportHelper.exportNoteToBinot(context, currentNote, colorsSnapshot)

            // 2. Si estamos en API 29+, copiamos el cache file a Documentos
            //    vía MediaStore. Este URI NO se comparte (el FileProvider URI
            //    es más confiable para intents de share).
            if (shareUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val safeName = "${currentNote.title.ifBlank { "Obinot_Note" }}.binot"
                ImportExportHelper.copyUriToDocuments(context, shareUri, safeName)
            }

            _isLoading.value = false
            launch(Dispatchers.Main) {
                if (shareUri != null) {
                    onResult(shareUri, context.getString(R.string.error_file_ready))
                } else {
                    onResult(null, context.getString(R.string.error_generate_binot_failed))
                }
            }
        }
    }

    /**
     * Escribe el archivo Markdown al [outputUri] dado (típicamente uno obtenido
     * vía SAF CreateDocument, que ya preguntó al usuario dónde guardar). No
     * dispara ningún share intent.
     */
    fun exportMarkdownToUri(context: Context, outputUri: Uri, onResult: (Boolean, String) -> Unit) {
        val currentNote = _note.value
        if (currentNote == null) {
            onResult(false, context.getString(R.string.error_note_empty))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val success = ImportExportHelper.exportNoteToMarkdownUri(context, currentNote, outputUri)
            launch(Dispatchers.Main) {
                if (success) {
                    onResult(true, context.getString(R.string.result_export_markdown_success))
                } else {
                    onResult(false, context.getString(R.string.result_export_markdown_failed))
                }
            }
        }
    }

    fun updateTitle(newTitle: String) {
        val currentNote = _note.value ?: return
        val updatedNote = currentNote.copy(title = newTitle, timestamp = System.currentTimeMillis())
        _note.value = updatedNote
        viewModelScope.launch { noteRepository.update(updatedNote) }
    }

    fun updateRawText(newRawText: String) {
        val currentNote = _note.value ?: return
        val updatedNote = currentNote.copy(
            rawText = newRawText,
            originalRawText = null,
            summary = null,
            timestamp = System.currentTimeMillis()
        )
        _note.value = updatedNote
        _processingFailed.value = false
        viewModelScope.launch {
            noteRepository.update(updatedNote)
            checkAndTriggerAutoProcess(updatedNote)
        }
    }

    fun reanalyzeWithAI() {
        val currentNote = _note.value ?: return
        val hasMarker = currentNote.rawText.startsWith(AudioRecorderManager.PHONE_TRANSCRIPTION_MARKER)
        val isPending = currentNote.rawText == AudioRecorderManager.PENDING_TRANSCRIPTION
        if (!hasMarker && !isPending) return

        if (currentNote.audioPath == null) {
            _error.value = appContext.getString(R.string.error_no_audio_to_reanalyze)
            return
        }

        val updated = currentNote.copy(
            rawText = AudioRecorderManager.PENDING_TRANSCRIPTION,
            summary = null,
            timestamp = System.currentTimeMillis()
        )
        _note.value = updated
        _processingFailed.value = false
        viewModelScope.launch {
            noteRepository.update(updated)
            transcribeAudio()
        }
    }

    fun replaceAudio(context: Context, newAudioUri: Uri, onResult: (Boolean) -> Unit) {
        val currentNote = _note.value
        if (currentNote == null) {
            onResult(false); return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val audioDir = File(context.filesDir, "audio_records").apply { mkdirs() }
                val newFile = File(audioDir, "RECORD_${System.currentTimeMillis()}.mp4")
                context.contentResolver.openInputStream(newAudioUri)?.use { input ->
                    newFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw Exception("Could not open selected file")

                currentNote.audioPath?.let { oldPath ->
                    try { File(oldPath).delete() } catch (_: Exception) {}
                }

                val updated = currentNote.copy(
                    audioPath = newFile.absolutePath,
                    rawText = AudioRecorderManager.PENDING_TRANSCRIPTION,
                    summary = null,
                    timestamp = System.currentTimeMillis()
                )
                _note.value = updated
                _processingFailed.value = false
                noteRepository.update(updated)

                launch(Dispatchers.Main) {
                    onResult(true)
                    transcribeAudio()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun toggleLabel(label: String) {
        val currentNote = _note.value ?: return
        val currentLabels = currentNote.label
            ?.split("|")?.map { it.trim() }?.filter { it.isNotBlank() }?.toMutableList()
            ?: mutableListOf()

        if (currentLabels.contains(label)) {
            currentLabels.remove(label)
        } else {
            currentLabels.add(label)
        }

        val newLabelString = if (currentLabels.isEmpty()) null else currentLabels.joinToString("|")
        val updatedNote = currentNote.copy(label = newLabelString, timestamp = System.currentTimeMillis())
        _note.value = updatedNote

        viewModelScope.launch(Dispatchers.IO) {
            noteRepository.update(updatedNote)
            if (label.isNotBlank()) {
                val sysNote = noteRepository.getSystemNoteSync()
                if (sysNote != null) {
                    val labels = sysNote.rawText.split("|").filter { it.isNotBlank() }.toMutableSet()
                    labels.add(label)
                    noteRepository.update(sysNote.copy(rawText = labels.joinToString("|")))
                } else {
                    noteRepository.insert(NoteEntity(title = "[[BINOT_SYSTEM_LABELS]]", rawText = label, summary = null))
                }
                labelRepository.createLabel(label)
            }
        }
    }

    fun restoreRawText() {
        val currentNote = _note.value ?: return
        if (currentNote.summary == null) return
        val updatedNote = currentNote.copy(summary = null, timestamp = System.currentTimeMillis())
        _note.value = updatedNote
        _processingFailed.value = false
        viewModelScope.launch { noteRepository.update(updatedNote) }
    }

    /**
     * Invierte el estado de un checkbox en el summary.
     *
     * El `lineIndex` corresponde a la línea dentro del **summary limpio** (sin
     * el meta tag BINOT_META), que es lo que `MarkdownText` ve. Reconstruimos
     * el summary completo con el meta tag preservado al final.
     */
    fun toggleCheckbox(lineIndex: Int) {
        val currentNote = _note.value ?: return
        val originalSummary = currentNote.summary ?: return

        val metaTag = Regex("<!--BINOT_META:.*?-->").find(originalSummary)?.value
        val cleanSummary = originalSummary
            .replace(Regex("<!--BINOT_META:.*?-->"), "")
            .trimEnd()

        val lines = cleanSummary.split("\n").toMutableList()
        if (lineIndex !in lines.indices) return

        val line = lines[lineIndex]
        val trimmed = line.trimStart()
        val indent = line.substring(0, line.length - trimmed.length)

        val newTrimmed = when {
            trimmed.startsWith("- [ ]") -> "- [x]" + trimmed.removePrefix("- [ ]")
            trimmed.startsWith("- [x]") -> "- [ ]" + trimmed.removePrefix("- [x]")
            trimmed.startsWith("- [X]") -> "- [ ]" + trimmed.removePrefix("- [X]")
            else -> return
        }
        lines[lineIndex] = indent + newTrimmed

        val newClean = lines.joinToString("\n")
        val newSummary = if (metaTag != null) "$newClean\n\n$metaTag" else newClean

        val updated = currentNote.copy(summary = newSummary, timestamp = System.currentTimeMillis())
        _note.value = updated
        viewModelScope.launch { noteRepository.update(updated) }
    }
    
    fun toggleAudio() {
        val path = _note.value?.audioPath ?: return
        val file = File(path)
        if (!file.exists()) {
            _error.value = appContext.getString(R.string.error_audio_not_found_pending)
            return
        }

        if (mediaPlayer == null) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    setOnCompletionListener {
                        _isPlaying.value = false
                        _playbackProgress.value = 0f
                        progressJob?.cancel()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = appContext.getString(R.string.processing_failed, e.message ?: "")
                mediaPlayer?.release()
                mediaPlayer = null
                return
            }
        }

        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            _isPlaying.value = false
            progressJob?.cancel()
        } else {
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressTracker()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (_isPlaying.value) {
                mediaPlayer?.let {
                    if (it.duration > 0) {
                        _playbackProgress.value = it.currentPosition.toFloat() / it.duration.toFloat()
                    }
                }
                delay(100)
            }
        }
    }

    fun seekAudio(progress: Float) {
        mediaPlayer?.let {
            val seekTo = (it.duration * progress).toInt()
            it.seekTo(seekTo)
            _playbackProgress.value = progress
        }
    }

    fun exportAudio(context: Context, uri: Uri, onResult: (String) -> Unit) {
        val path = _note.value?.audioPath
        if (path == null || !File(path).exists()) {
            onResult(context.getString(R.string.error_no_audio_to_reanalyze))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(path)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input -> input.copyTo(output) }
                }
                launch(Dispatchers.Main) { onResult(context.getString(R.string.error_file_ready)) }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onResult(context.getString(R.string.processing_failed, e.message ?: "")) }
            }
        }
    }

    // ============================================================
    // MIX ROUTING
    // ============================================================

    private enum class MixTask {
        SHORT_AUDIO, LONG_AUDIO, SHORT_TEXT, LONG_TEXT, TITLE, EXPLAIN, CHAT
    }

    private suspend fun pickProviderForMix(task: MixTask): Int {
        return when (task) {
            MixTask.LONG_AUDIO -> 0
            MixTask.SHORT_AUDIO -> 1
            MixTask.LONG_TEXT -> 0
            MixTask.SHORT_TEXT, MixTask.TITLE, MixTask.EXPLAIN, MixTask.CHAT -> {
                val counter = settingsRepository.incrementMixCounter()
                if (counter % 2 == 0) 0 else 1
            }
        }
    }

    // ============================================================
    // EXPLAIN
    // ============================================================

    fun explainText(selectedText: String, deviceLanguage: String) {
        _isExplaining.value = true
        _explainResult.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val provider = settingsRepository.aiProviderFlow.first()
                val geminiKey = settingsRepository.geminiApiKeyFlow.first()
                val groqKey = settingsRepository.groqApiKeyFlow.first()

                val effectiveProvider = if (provider == 2) {
                    pickProviderForMix(MixTask.EXPLAIN)
                } else provider

                val apiKey = if (effectiveProvider == 1) groqKey else geminiKey
                val targetLanguage = settingsRepository.aiLanguageFlow.first()

                if (apiKey.isBlank()) {
                    launch(Dispatchers.Main) {
                        _explainResult.value = appContext.getString(R.string.error_api_key_missing)
                        _isExplaining.value = false
                    }
                    return@launch
                }

                var systemPrompt = """
                    You are an expert encyclopedia. Explain the given term/sentence purely, briefly, and with high relevance. 
                    STRICT RULES YOU MUST OBEY:
                    1. Output language MUST follow: $targetLanguage.
                    2. NO conversational filler, pleasantries, or introductions.
                    3. Format nicely using Markdown. ABSOLUTELY NO BACKTICKS (`), EXCEPT if you need to generate a ```mermaid diagram.
                    4. CRITICAL: DO NOT generate tables under any circumstances.
                    5. STRICT MATH FORMATTING: Convert all mathematical formulas into valid LaTeX syntax using `${'$'}${'$'}` or `${'$'}`. NEVER translate math/chemistry formulas into spoken words.
                    6. NO MATH MARKDOWN & NO QUOTES: NEVER use Markdown asterisks (`**`, `*`) or underscores (`_`) INSIDE or immediately touching LaTeX blocks. 
                       - FATAL WRONG: `**${'$'}x=1${'$'}**` or `${'$'}**x=1**${'$'}`
                       - CORRECT: `${'$'}x=1${'$'}`
                       If you desperately need to bold a mathematical variable, YOU MUST use pure LaTeX: `${'$'}\mathbf{x}=1${'$'}`. NEVER wrap LaTeX blocks in quotes.
                """.trimIndent()

                if (effectiveProvider == 1) {
                    systemPrompt += """

                        [GROQ/LLAMA OVERRIDES]
                        7. STRICT MATH ISOLATION: Keep math symbols inside `${'$'}${'$'}` strictly in Latin/Greek/Numbers. DO NOT put Arabic, Chinese, Korean, or any non-Latin translations INSIDE the math block. Put translated text OUTSIDE.
                        8. MERMAID ALLOWED: You are ALLOWED and ENCOURAGED to use ` ```mermaid ` blocks for diagrams. Do not avoid backticks for diagrams.
                    """.trimIndent()
                }

                val userPrompt = "Term to explain: \"$selectedText\""

                val resultText = if (effectiveProvider == 1) {
                    val request = GroqChatRequest(
                        model = GroqModels.GPT_OSS_120B,
                        messages = listOf(
                            GroqMessage(role = "system", content = systemPrompt),
                            GroqMessage(role = "user", content = userPrompt)
                        )
                    )
                    RetrofitClient.groqService.generateContent("Bearer $apiKey", request).choices?.firstOrNull()?.message?.content
                } else {
                    val request = GenerateContentRequest(
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        contents = listOf(Content(parts = listOf(Part(text = userPrompt))))
                    )
                    RetrofitClient.service.generateContent(
                        model = GeminiModels.FLASH_LITE,
                        apiKey = apiKey,
                        request = request
                    ).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                }

                launch(Dispatchers.Main) {
                    _explainResult.value = resultText?.trim() ?: appContext.getString(R.string.error_explain_failed)
                    _isExplaining.value = false
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    _explainResult.value = handleExceptionError(e)
                    _isExplaining.value = false
                }
            }
        }
    }

    fun clearExplainResult() {
        _explainResult.value = null
    }

    // ============================================================
    // HIGHLIGHTS
    // ============================================================

    fun saveHighlightNote(highlightText: String, noteText: String, lineIndex: Int = -1, startIndex: Int = -1, endIndex: Int = -1) {
        val currentNote = _note.value ?: return
        val currentJson = currentNote.highlightsInfo ?: "[]"

        try {
            val jsonArray = JSONArray(currentJson)
            var found = false
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val sameSpot = startIndex >= 0 && obj.optInt("start", -1) == startIndex && obj.optInt("line", -1) == lineIndex
                val sameLegacyText = startIndex < 0 && obj.getString("text") == highlightText && obj.optInt("start", -1) < 0
                if (sameSpot || sameLegacyText) {
                    obj.put("note", noteText)
                    obj.put("text", highlightText)
                    if (startIndex >= 0) {
                        obj.put("line", lineIndex)
                        obj.put("start", startIndex)
                        obj.put("end", endIndex)
                    }
                    found = true
                    break
                }
            }
            if (!found) {
                val newObj = JSONObject().apply {
                    put("text", highlightText)
                    put("note", noteText)
                    if (startIndex >= 0) {
                        put("line", lineIndex)
                        put("start", startIndex)
                        put("end", endIndex)
                    }
                }
                jsonArray.put(newObj)
            }
            val updatedNote = currentNote.copy(highlightsInfo = jsonArray.toString(), timestamp = System.currentTimeMillis())
            _note.value = updatedNote
            viewModelScope.launch { noteRepository.update(updatedNote) }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun removeHighlight(highlightText: String, lineIndex: Int = -1, startIndex: Int = -1) {
        val currentNote = _note.value ?: return
        val currentJson = currentNote.highlightsInfo ?: return
        try {
            val jsonArray = JSONArray(currentJson)
            val newArray = JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val sameSpot = startIndex >= 0 && obj.optInt("start", -1) == startIndex && obj.optInt("line", -1) == lineIndex
                val sameLegacyText = startIndex < 0 && obj.getString("text") == highlightText && obj.optInt("start", -1) < 0
                if (!(sameSpot || sameLegacyText)) newArray.put(obj)
            }
            val updatedString = if (newArray.length() == 0) null else newArray.toString()
            val updatedNote = currentNote.copy(highlightsInfo = updatedString, timestamp = System.currentTimeMillis())
            _note.value = updatedNote
            viewModelScope.launch { noteRepository.update(updatedNote) }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ============================================================
    // TRANSCRIBE
    // ============================================================

    private fun transcribeAudio() {
        val currentNote = _note.value ?: return
        val audioPath = currentNote.audioPath ?: return

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val provider = settingsRepository.aiProviderFlow.first()
            val geminiKey = settingsRepository.geminiApiKeyFlow.first()
            val groqKey = settingsRepository.groqApiKeyFlow.first()

            if ((provider == 1 && groqKey.isBlank()) || (provider == 0 && geminiKey.isBlank()) || (provider == 2 && geminiKey.isBlank() && groqKey.isBlank())) {
                launch(Dispatchers.Main) {
                    _error.value = appContext.getString(R.string.error_api_key_required_transcribe)
                    _isLoading.value = false
                }
                return@launch
            }

            var remoteFileName: String? = null
            var compressedFile: File? = null
            try {
                val originalFile = File(audioPath)
                if (!originalFile.exists()) throw Exception(appContext.getString(R.string.error_audio_not_found_pending))

                var transcript: String? = null
                var fileToUpload = originalFile

                val compressionModeForRouting = settingsRepository.autoCompressionModeFlow.first()
                var effectiveProvider: Int = if (provider == 2) {
                    val fits = originalFile.length() <= 20 * 1024 * 1024
                    val compressible = compressionModeForRouting > 0 &&
                        AudioCompressor.calculateTargetBitrate(
                            getAudioDurationMs(originalFile),
                            if (compressionModeForRouting == 1) 24.0 else 15.0
                        ) != null
                    if (fits || compressible) 1 else 0
                } else provider

                if (effectiveProvider == 1 && originalFile.length() > 24 * 1024 * 1024) {
                    val compressionMode = settingsRepository.autoCompressionModeFlow.first()
                    if (compressionMode > 0) {
                        val targetSizeMB = if (compressionMode == 1) 24.0 else 15.0
                        val durationMs = getAudioDurationMs(originalFile)
                        val targetBitrate = AudioCompressor.calculateTargetBitrate(durationMs, targetSizeMB)
                        if (targetBitrate != null) {
                            launch(Dispatchers.Main) { _loadingMessage.value = appContext.getString(R.string.loading_compressing) }
                            val tempFile = File(appContext.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")
                            when (val result = AudioCompressor.compress(originalFile, tempFile, targetBitrate) { percent ->
                                launch(Dispatchers.Main) { _loadingMessage.value = appContext.getString(R.string.loading_compressing_pct, percent) }
                            }) {
                                is AudioCompressor.Result.Success -> {
                                    fileToUpload = result.outputFile
                                    compressedFile = result.outputFile
                                }
                                is AudioCompressor.Result.QualityTooLow -> {
                                    if (provider == 2) {
                                        effectiveProvider = 0
                                    } else {
                                        launch(Dispatchers.Main) {
                                            _error.value = appContext.getString(R.string.error_audio_too_long_groq)
                                            _isLoading.value = false
                                        }
                                        return@launch
                                    }
                                }
                                is AudioCompressor.Result.Failure -> {
                                    if (provider == 2) {
                                        effectiveProvider = 0
                                    } else {
                                        launch(Dispatchers.Main) {
                                            _error.value = appContext.getString(R.string.error_compression_failed)
                                            _isLoading.value = false
                                        }
                                        return@launch
                                    }
                                }
                            }
                        } else {
                            if (provider == 2) {
                                effectiveProvider = 0
                            } else {
                                launch(Dispatchers.Main) {
                                    _error.value = appContext.getString(R.string.error_audio_too_long_groq)
                                    _isLoading.value = false
                                }
                                return@launch
                            }
                        }
                    } else {
                        if (provider == 2) {
                            effectiveProvider = 0
                        } else {
                            launch(Dispatchers.Main) {
                                _error.value = appContext.getString(R.string.error_file_exceeds_groq)
                                _isLoading.value = false
                            }
                            return@launch
                        }
                    }
                }

                val apiKey = if (effectiveProvider == 1) groqKey else geminiKey

                if (effectiveProvider == 1) {
                    launch(Dispatchers.Main) { _loadingMessage.value = appContext.getString(R.string.loading_transcribing_groq) }

                    val requestFile = fileToUpload.asRequestBody("audio/mp4".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", fileToUpload.name, requestFile)
                    val model = GroqModels.WHISPER.toRequestBody("text/plain".toMediaTypeOrNull())
                    val format = "json".toRequestBody("text/plain".toMediaTypeOrNull())

                    val response = RetrofitClient.groqService.transcribeAudio("Bearer $apiKey", body, model, format)
                    transcript = response.text?.trim()
                } else {
                    launch(Dispatchers.Main) { _loadingMessage.value = appContext.getString(R.string.loading_uploading_google) }
                    val mimeType = "audio/mp4"
                    val requestBody = fileToUpload.asRequestBody(mimeType.toMediaTypeOrNull())
                    val uploadResponse = RetrofitClient.service.uploadFile(
                        apiKey = apiKey,
                        contentLength = fileToUpload.length(),
                        contentType = mimeType,
                        mimeType = mimeType,
                        fileBytes = requestBody
                    )
                    if (uploadResponse.file == null) throw Exception("Failed to upload file to Gemini server.")

                    val uploadedFileUri = uploadResponse.file.uri
                    remoteFileName = uploadResponse.file.name

                    launch(Dispatchers.Main) { _loadingMessage.value = appContext.getString(R.string.loading_gemini_processing) }

                    val systemPrompt = """
                        You are a highly accurate audio transcription AI. Your ONLY task is to transcribe the audio exactly word-for-word.
                        
                        CRITICAL STRICT RULES:
                        1. NO HALLUCINATION: If the audio is silent, output exactly "[No speech detected]".
                        2. VERBATIM TRANSCRIBE: Transcribe exactly what is spoken word-by-word, including informal words, repeated words, and natural speech flow.
                        3. KEEP PUNCTUATION & CAPITALIZATION: You MUST add accurate punctuation (periods, commas, question marks) and use proper capitalization to make it readable.
                        4. NO GRAMMAR CORRECTION: Absolutely DO NOT fix the speaker's grammatical errors or restructure their sentences.
                        5. NO MARKDOWN & NO MATH FORMATTING: DO NOT add Markdown styling. DO NOT convert spoken math, numbers, or symbols into LaTeX format. Write them as plain text.
                        6. Automatically detect and transcribe in the spoken language.
                    """.trimIndent()

                    val request = GenerateContentRequest(
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        contents = listOf(Content(parts = listOf(Part(fileData = FileData(mimeType = mimeType, fileUri = uploadedFileUri)))))
                    )

                    var fileState = uploadResponse.file.state
                    var attempts = 0
                    while (fileState == "PROCESSING" && attempts < 60) {
                        delay(3000)
                        fileState = RetrofitClient.service.getFile(remoteFileName, apiKey).state
                        attempts++
                    }
                    if (fileState != "ACTIVE") throw Exception("File processing timeout or failed at Google server.")

                    val response = RetrofitClient.service.generateContent(
                        model = GeminiModels.FLASH,
                        apiKey = apiKey,
                        request = request
                    )
                    transcript = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                }

                launch(Dispatchers.Main) {
                    if (transcript != null && !transcript.contains("[No speech detected]")) {
                        val updatedNote = currentNote.copy(rawText = transcript, timestamp = System.currentTimeMillis())
                        _note.value = updatedNote
                        noteRepository.update(updatedNote)

                        launch(Dispatchers.IO) {
                            var noteWithTitle = updatedNote
                            try {
                                noteWithTitle = generateTitleFromTranscript(updatedNote, transcript, provider, geminiKey, groqKey) ?: updatedNote
                            } catch (e: Exception) {
                                e.printStackTrace()
                                if (noteWithTitle.title.isBlank()) {
                                    val fallbackTitle = transcript.take(60).trim().lineSequence().firstOrNull { it.isNotBlank() } ?: "Untitled Note"
                                    noteWithTitle = noteWithTitle.copy(title = fallbackTitle)
                                    launch(Dispatchers.Main) {
                                        _note.value = noteWithTitle
                                        noteRepository.update(noteWithTitle)
                                    }
                                }
                            }
                            launch(Dispatchers.Main) {
                                checkAndTriggerAutoProcess(noteWithTitle)
                            }
                        }
                    } else if (transcript?.contains("[No speech detected]") == true) {
                        _error.value = appContext.getString(R.string.error_no_speech_detected)
                    } else {
                        _error.value = appContext.getString(R.string.error_ai_empty_transcript)
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    _error.value = handleExceptionError(e)
                    _isLoading.value = false
                }
            } finally {
                if (provider == 0 && remoteFileName != null) {
                    try { RetrofitClient.service.deleteFile(remoteFileName, geminiKey) } catch (e: Exception) { e.printStackTrace() }
                }
                compressedFile?.let {
                    try { it.delete() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun getAudioDurationMs(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private suspend fun generateTitleFromTranscript(note: NoteEntity, transcript: String, provider: Int, geminiKey: String, groqKey: String): NoteEntity? {
        val systemPrompt = """
            Buat judul singkat 3-5 kata dalam bahasa yang sama dengan teks yang diberikan pengguna.
            RULES: Hanya output judulnya saja. Tanpa tanda kutip, tanpa titik di akhir, dan tanpa penjelasan apapun.
        """.trimIndent()
        val userPrompt = "Teks:\n${transcript.take(500)}"

        val effectiveProvider = if (provider == 2) {
            pickProviderForMix(MixTask.TITLE)
        } else provider
        val apiKey = if (effectiveProvider == 1) groqKey else geminiKey

        val aiTitle = if (effectiveProvider == 1) {
            val request = GroqChatRequest(
                model = GroqModels.GPT_OSS_20B,
                messages = listOf(
                    GroqMessage(role = "system", content = systemPrompt),
                    GroqMessage(role = "user", content = userPrompt)
                )
            )
            RetrofitClient.groqService.generateContent("Bearer $apiKey", request).choices?.firstOrNull()?.message?.content?.trim()
        } else {
            val request = GenerateContentRequest(
                systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                contents = listOf(Content(parts = listOf(Part(text = userPrompt))))
            )
            RetrofitClient.service.generateContent(
                model = GeminiModels.FLASH_LITE,
                apiKey = apiKey,
                request = request
            ).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        }

        if (!aiTitle.isNullOrBlank()) {
            val finalNote = note.copy(title = aiTitle)
            _note.value = finalNote
            noteRepository.update(finalNote)
            return finalNote
        }
        return null
    }

    // ============================================================
    // TEXT PROCESSING
    // ============================================================

    private fun processTextAuto(currentNote: NoteEntity, language: String, task: Int, format: Int, metaTag: String, provider: Int) {
        _isLoading.value = true
        _error.value = null
        _processingFailed.value = false
        _loadingMessage.value = appContext.getString(R.string.loading_ai_structuring)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val provider = settingsRepository.aiProviderFlow.first()

                val effectiveProvider = if (provider == 2) {
                    val taskType = if (currentNote.rawText.length > 600) MixTask.LONG_TEXT else MixTask.SHORT_TEXT
                    pickProviderForMix(taskType)
                } else provider

                val apiKey = if (effectiveProvider == 1) settingsRepository.groqApiKeyFlow.first() else settingsRepository.geminiApiKeyFlow.first()

                if (apiKey.isBlank()) {
                    launch(Dispatchers.Main) {
                        _error.value = appContext.getString(R.string.error_api_key_required_engine)
                        _processingFailed.value = true
                        _isLoading.value = false
                    }
                    return@launch
                }

                val taskInstruction = when (task) {
                    0 -> "Task: TIDY UP. Fix typos, grammar, and remove filler words/false starts. Be precise. But do NOT flatten the speaker's voice into generic corporate or robotic prose — keep their natural tone, word choices, and register exactly as it was (casual stays casual, formal stays formal, funny stays funny). You're cleaning up how it was said, not rewriting who said it. DO NOT add outside facts. If it's a multi-sentence text, divide it logically into sections."
                    1 -> "Task: SUMMARIZE. Extract the core information and make a concise summary. Ignore filler words. Keep it under 30% of the original length."
                    2 -> "Task: ANALYZE. Extract the main points, underlying sentiments, and any action items or decisions."
                    else -> "Task: TIDY UP."
                }

                val formatInstruction = when (format) {
                    0 -> "Format: MANDATORY: You MUST structure the text using logical Subheadings (##) only. DO NOT generate a Main Title (#) — it is already set separately. Start directly with the first Subheading. Do not output a flat wall of text. Use PARAGRAPHS for the details under each heading. DO NOT use bullet points. Use **bold** for key concepts, *italic* for emphasis, and > for quotes. DO NOT wrap text in quotes."
                    1 -> "Format: MANDATORY: You MUST structure the text using logical Subheadings (##) only. DO NOT generate a Main Title (#) — it is already set separately. Start directly with the first Subheading. Use BULLET POINTS ('-') for the details under each heading. NEVER use asterisks ('*'). Use **bold** for key concepts."
                    else -> ""
                }

                val taskFormatHint = when {
                    task == 0 && format == 1 -> "Hint: When tidying up into bullets, each bullet should be one complete thought. Don't split a single sentence across multiple bullets."
                    task == 1 && format == 0 -> "Hint: When summarizing into paragraphs, write 2-4 short paragraphs maximum. Each paragraph should cover one main theme."
                    task == 2 && format == 1 -> "Hint: When analyzing into bullets, group related points together. Start with 'Main Points', then 'Sentiments', then 'Action Items' if they exist."
                    else -> ""
                }

                val geminiSystemPrompt = """
                    [SYSTEM: TEXT PROCESSOR MODE]
                    You process text for a note-taking app, not a chatbot: never chat, greet, or comment — just return the processed text. Within that, write like a careful human editor, not a corporate style guide: match the register of the source instead of defaulting to stiff, formal phrasing.
                    TARGET LANGUAGE: $language. You MUST translate the output to $language if the input is different.
                    
                    $taskInstruction
                    $formatInstruction
                    $taskFormatHint

                    CRITICAL STRICT RULES YOU MUST OBEY:
                    1. ZERO YAPPING: Output EXACTLY the final processed text. NO greetings, NO introductions, NO explanations of what you did.
                    2. NO GLOBAL WRAPPING: DO NOT wrap your entire output in quotes or a global markdown code block.
                    3. MANDATORY LATEX & CHEMISTRY: Convert ALL mathematical concepts, formulas, and equations into valid LaTeX syntax. Use `${'$'}${'$'}` for block equations and `${'$'}` for inline math. For CHEMICAL formulas and reactions, you MUST use the `\ce{}` macro inside LaTeX.
                    4. NO MATH MARKDOWN & NO QUOTES: KaTeX WILL CRASH if you use Markdown inside it. NEVER use asterisks (`**`, `*`) or underscores (`_`) INSIDE or immediately touching LaTeX blocks.
                       - FATAL WRONG: `**${'$'}E=mc^2${'$'}**` or `${'$'}**E=mc^2**${'$'}`
                       - CORRECT: `${'$'}E=mc^2${'$'}`
                       If you desperately need to bold a mathematical element, YOU MUST use pure LaTeX: `${'$'}\mathbf{E}=mc^2${'$'}`. NEVER wrap equations in single or double quotes.
                    5. CRITICAL: DO NOT generate tables under any circumstances.
                    6. VISUAL DIAGRAMS (MANDATORY ANALYSIS):
                       - Silently check: Does the text contain a process, schedule, logic, IF/THEN, or sequence?
                       - IF YES: You MUST generate a Mermaid diagram in a ```mermaid ... ``` block.
                       - STRICT MERMAID RULES:
                         a) ONLY use `flowchart TD` or `flowchart LR`. DO NOT use sequenceDiagram, timeline, or anything else.
                         b) ALWAYS wrap node labels in double quotes. Example: `A["Start"] --> B["Check Data"]`.
                         c) For IF/THEN conditions, use standard edge text. Example: `B -->|Yes| C["Success"]` or `B -->|No| D["Fail"]`. NEVER use `|>`.
                         d) DO NOT use nested double quotes inside labels; use single quotes instead (e.g., `D["Kelas '07.00'"]`). Keep labels short (max 6 words).
                       - IF NO (purely descriptive): Skip diagram completely.
                """.trimIndent()

                val groqSystemPrompt = """
                    [SYSTEM: TEXT PROCESSOR MODE]
                    You process text for a note-taking app, not a chatbot: never chat, greet, or comment — just return the processed text. Within that, write like a careful human editor, not a corporate style guide: match the register of the source instead of defaulting to stiff, formal phrasing.
                    TARGET LANGUAGE: $language. You MUST translate the output to $language if the input is different.
                    
                    $taskInstruction
                    $formatInstruction
                    $taskFormatHint

                    CRITICAL STRICT RULES YOU MUST OBEY:
                    1. ZERO YAPPING: Output EXACTLY the final processed text. NO greetings, NO introductions, NO explanations of what you did.
                    2. NO GLOBAL WRAPPING: DO NOT wrap your entire output in quotes or a global markdown code block. (EXCEPTION: mermaid diagrams, see rule 7.)
                    3. MANDATORY LATEX & CHEMISTRY: Convert ALL mathematical concepts, formulas, and equations into valid LaTeX syntax. Use `${'$'}${'$'}` for block equations and `${'$'}` for inline math. For CHEMICAL formulas and reactions, you MUST use the `\ce{}` macro inside LaTeX.
                    4. NO MATH MARKDOWN & NO QUOTES: KaTeX WILL CRASH if you use Markdown inside it. NEVER use asterisks (`**`, `*`) or underscores (`_`) INSIDE or immediately touching LaTeX blocks.
                       - FATAL WRONG: `**${'$'}E=mc^2${'$'}**` or `${'$'}**E=mc^2**${'$'}`
                       - CORRECT: `${'$'}E=mc^2${'$'}`
                       If you desperately need to bold a mathematical element, YOU MUST use pure LaTeX: `${'$'}\mathbf{E}=mc^2${'$'}`. NEVER wrap equations in single or double quotes.
                    5. CRITICAL: DO NOT generate tables under any circumstances.
                    6. VISUAL DIAGRAMS (MANDATORY ANALYSIS):
                       - Silently check: Does the text contain a process, schedule, logic, IF/THEN, or sequence?
                       - IF YES: You MUST generate a Mermaid diagram in a ```mermaid ... ``` block.
                       - STRICT MERMAID RULES:
                         a) ONLY use `flowchart TD` or `flowchart LR`. DO NOT use sequenceDiagram, timeline, or anything else.
                         b) ALWAYS wrap node labels in double quotes. Example: `A["Start"] --> B["Check Data"]`.
                         c) For IF/THEN conditions, use standard edge text. Example: `B -->|Yes| C["Success"]` or `B -->|No| D["Fail"]`. NEVER use `|>`.
                         d) DO NOT use nested double quotes inside labels; use single quotes instead (e.g., `D["Kelas '07.00'"]`). Keep labels short (max 6 words).
                       - IF NO (purely descriptive): Skip diagram completely.
                    7. MERMAID ALLOWANCE: Rule #2 forbids global wrapping, but you MUST use ` ```mermaid ` blocks for diagrams. DO NOT avoid backticks for diagrams!
                    8. MERMAID ENFORCEMENT: If the text explains a system flow, login steps, conditions, or processes, YOU ARE FORCED to output a flowchart. Do not ignore logic.
                    9. STRICT MATH ISOLATION: Equations inside `${'$'}${'$'}` or `${'$'}` MUST remain in standard universal symbols (Latin/Greek/Numbers). DO NOT translate variables or put Arabic, Chinese, Korean, or any Non-Latin characters INSIDE the math blocks. Put all translated text OUTSIDE the LaTeX blocks.
                """.trimIndent()

                val userContent = "Process this text strictly into $language:\n\n${currentNote.rawText}"

                val processedText = if (effectiveProvider == 1) {
                    val request = GroqChatRequest(
                        model = GroqModels.GPT_OSS_120B,
                        messages = listOf(
                            GroqMessage(role = "system", content = groqSystemPrompt),
                            GroqMessage(role = "user", content = userContent)
                        )
                    )
                    RetrofitClient.groqService.generateContent("Bearer $apiKey", request).choices?.firstOrNull()?.message?.content
                } else {
                    val request = GenerateContentRequest(
                        systemInstruction = Content(parts = listOf(Part(text = geminiSystemPrompt))),
                        contents = listOf(Content(parts = listOf(Part(text = userContent))))
                    )
                    RetrofitClient.service.generateContent(
                        model = GeminiModels.FLASH,
                        apiKey = apiKey,
                        request = request
                    ).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                }

                launch(Dispatchers.Main) {
                    if (processedText != null) {
                        val cleanedText = processedText.trim().removeSurrounding("'", "'").removeSurrounding("\"", "\"")
                        val finalOutput = cleanedText + "\n\n" + metaTag

                        val updatedNote = currentNote.copy(summary = finalOutput, timestamp = System.currentTimeMillis())
                        _note.value = updatedNote
                        noteRepository.update(updatedNote)
                    } else {
                        _error.value = appContext.getString(R.string.error_ai_empty_text)
                        _processingFailed.value = true
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    _error.value = handleExceptionError(e)
                    _processingFailed.value = true
                    _isLoading.value = false
                }
            }
        }
    }

    private fun handleExceptionError(e: Exception): String {
        return if (e is HttpException) {
            when (e.code()) {
                400 -> appContext.getString(R.string.http_400)
                401 -> appContext.getString(R.string.http_401)
                403 -> appContext.getString(R.string.http_403)
                413 -> appContext.getString(R.string.http_413)
                429 -> appContext.getString(R.string.http_429)
                500 -> appContext.getString(R.string.http_500)
                503 -> appContext.getString(R.string.http_503)
                else -> appContext.getString(R.string.http_generic, e.code())
            }
        } else {
            appContext.getString(R.string.processing_failed, e.message ?: "")
        }
    }

    // ============================================================
    // AI Chat about this note (2.1)
    // ============================================================

    /**
     * Envía un mensaje del usuario al chat y agrega la respuesta del modelo
     * al historial. Si el historial ya llegó al límite (20 mensajes), ignora
     * el envío. No hace nada si el texto está vacío o si ya hay un envío en curso.
     */
        /**
     * Envía un mensaje del usuario al chat y agrega la respuesta del modelo
     * al historial. Si el historial ya llegó al límite (40 mensajes), ignora
     * el envío. No hace nada si el texto está vacío o si ya hay un envío en curso.
     *
     * El historial se persiste con cada mensaje agregado (user y assistant),
     * así sobrevive al cierre de la app. Ver persistChatHistory().
     */
    fun sendChatMessage(userMessage: String) {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty()) return
        if (_isChatSending.value) return
        if (_chatMessages.value.size >= MAX_CHAT_MESSAGES) return

        val currentNote = _note.value ?: return

        _chatMessages.value = _chatMessages.value + ChatMessage("user", trimmed)
        _isChatSending.value = true
        persistChatHistory()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val provider = settingsRepository.aiProviderFlow.first()
                val geminiKey = settingsRepository.geminiApiKeyFlow.first()
                val groqKey = settingsRepository.groqApiKeyFlow.first()
                val targetLanguage = settingsRepository.aiLanguageFlow.first()

                val effectiveProvider = if (provider == 2) {
                    pickProviderForMix(MixTask.CHAT)
                } else provider

                val apiKey = if (effectiveProvider == 1) groqKey else geminiKey
                if (apiKey.isBlank()) {
                    launch(Dispatchers.Main) {
                        _chatMessages.value = _chatMessages.value + ChatMessage(
                            "assistant",
                            appContext.getString(R.string.error_api_key_missing)
                        )
                        _isChatSending.value = false
                        persistChatHistory()
                    }
                    return@launch
                }

                val cleanSummary = currentNote.summary
                    ?.replace(Regex("<!--BINOT_META:.*?-->"), "")
                    ?.trimEnd()

                val systemPrompt = """
                    You are an AI assistant helping the user understand their own note.
                    Output language: $targetLanguage. If the user writes in a different language, reply in the user's language instead.

                    The note's content is provided below for context. Answer the user's questions based strictly on it. If the answer isn't in the note, say so politely.

                    STRICT RULES YOU MUST OBEY:
                    1. ZERO YAPPING: No greetings, no introductions, no self-references. Answer directly.
                    2. Markdown allowed: headers, bold, italic, lists, code blocks, links.
                    3. NO TABLES under any circumstances.
                    4. MATH DELIMITERS (CRITICAL):
                       - Inline math: ALWAYS use `${'$'}...${'$'}` (single dollar signs). Example: "The work is ${'$'}W = F \\cdot d${'$'} in joules."
                       - Block math: ALWAYS use `${'$'}${'$'}...${'$'}${'$'}` (double dollar signs) on their own line.
                       - NEVER use `\\(...\\)` or `\\[...\\]` — those delimiters are NOT supported by this app's renderer.
                       - NEVER wrap math in quotes or bold markers (`**${'$'}...${'$'}**` is forbidden).
                    5. NO MERMAID DIAGRAMS. This chat is for math and text only. Do not emit ```mermaid blocks.

                    --- NOTE TITLE ---
                    ${currentNote.title}

                    --- NOTE SUMMARY ---
                    ${cleanSummary ?: "(no summary yet)"}

                    --- NOTE RAW TEXT ---
                    ${currentNote.rawText}
                    --- END OF NOTE ---
                """.trimIndent()

                // Construimos el historial completo como una sola cadena para
                // el turno del usuario. Es más simple que serializar mensajes
                // individuales y el system prompt ya establece el contexto.
                val conversationHistory = _chatMessages.value.joinToString("\n\n") { msg ->
                    when (msg.role) {
                        "user" -> "User: ${msg.content}"
                        else -> "Assistant: ${msg.content}"
                    }
                }

                val responseText = if (effectiveProvider == 1) {
                    val request = GroqChatRequest(
                        model = GroqModels.GPT_OSS_20B,
                        messages = listOf(
                            GroqMessage(role = "system", content = systemPrompt),
                            GroqMessage(role = "user", content = conversationHistory)
                        )
                    )
                    RetrofitClient.groqService.generateContent("Bearer $apiKey", request)
                        .choices?.firstOrNull()?.message?.content?.trim()
                } else {
                    val request = GenerateContentRequest(
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        contents = listOf(Content(parts = listOf(Part(text = conversationHistory))))
                    )
                    RetrofitClient.service.generateContent(
                        model = GeminiModels.FLASH_LITE,
                        apiKey = apiKey,
                        request = request
                    ).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                }

                launch(Dispatchers.Main) {
                    val reply = if (responseText.isNullOrBlank()) {
                        appContext.getString(R.string.error_ai_empty_text)
                    } else {
                        responseText
                    }
                    _chatMessages.value = _chatMessages.value + ChatMessage("assistant", reply)
                    _isChatSending.value = false
                    persistChatHistory()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        "assistant",
                        handleExceptionError(e)
                    )
                    _isChatSending.value = false
                    persistChatHistory()
                }
            }
        }
    }

    /**
     * Borra el historial del chat de la nota (tanto en memoria como en la DB).
     * Se llama desde el botón "Clear conversation" del ChatSheet, con
     * confirmación previa del usuario.
     */
    fun clearChat() {
        _chatMessages.value = emptyList()
        _isChatSending.value = false
        viewModelScope.launch(Dispatchers.IO) {
            val currentNote = _note.value ?: return@launch
            val updated = currentNote.copy(chatHistory = null)
            _note.value = updated
            noteRepository.update(updated)
        }
    }

    /**
     * Serializa el historial actual a JSON y lo guarda en la nota.
     * Se llama después de agregar cada mensaje (user o assistant) para que
     * el chat sobreviva al cierre de la app.
     */
    private fun persistChatHistory() {
        val messages = _chatMessages.value
        val currentNote = _note.value ?: return
        val json = chatHistoryToJson(messages)
        val updated = currentNote.copy(chatHistory = json)
        _note.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            noteRepository.update(updated)
        }
    }

    /** Marca que el tooltip del long-press ya se mostró, para no repetirlo. */
    fun markChatTooltipShown() {
        viewModelScope.launch { settingsRepository.saveAiChatTooltipShown(true) }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        progressJob?.cancel()
    }

    companion object {
        /** Cap total de mensajes persistidos por nota. Más allá de esto, el
         *  usuario debe limpiar el chat. 40 = 20 turnos. */
        private const val MAX_CHAT_MESSAGES = 40

        /** Parsea el JSON de chatHistory a la lista de mensajes. Devuelve lista
         *  vacía si el JSON está ausente o malformado. */
        private fun parseChatHistory(json: String?): List<ChatMessage> {
            if (json.isNullOrBlank() || json == "[]") return emptyList()
            return try {
                val array = JSONArray(json)
                val list = mutableListOf<ChatMessage>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ChatMessage(
                            role = obj.optString("role", "assistant"),
                            content = obj.optString("content", "")
                        )
                    )
                }
                list
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

        /** Serializa la lista de mensajes a un JSON array. Null si está vacía. */
        private fun chatHistoryToJson(messages: List<ChatMessage>): String? {
            if (messages.isEmpty()) return null
            return try {
                val array = JSONArray()
                messages.forEach { msg ->
                    val obj = JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    }
                    array.put(obj)
                }
                array.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun provideFactory(
            noteId: Int,
            repository: NoteRepository,
            settingsRepository: SettingsRepository,
            labelRepository: LabelRepository,
            appContext: Context
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ResultViewModel(noteId, repository, settingsRepository, labelRepository, appContext) as T
                }
            }
    }
}