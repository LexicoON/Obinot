package com.obinot.app.utils

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.log10
import kotlin.random.Random

/**
 * Grabador de audio con dos modos:
 *
 * **Fast (mode 0)** — Solo [SpeechRecognizer]. Transcripción del teléfono en vivo.
 *   El audio NO se guarda en disco. Si el recognizer no está disponible, se usa
 *   un modo simulado para no romper la UI (emuladores).
 *
 * **Accurate (mode 1)** — [MediaRecorder] siempre. El [SpeechRecognizer] en paralelo
 *   es OPCIONAL y solo se activa si [liveTranscriptEnabled] es true. Esto existe
 *   porque el recognizer simultáneo falla o se traba en muchos dispositivos, y
 *   para esos casos el usuario prefiere solo grabar audio y dejar que la IA lo
 *   transcriba después. Default OFF.
 *
 * El [SpeechRecognizer] en modo 1 es best-effort incluso cuando está habilitado:
 * si falla, la grabación continúa sin interrupciones.
 */
class AudioRecorderManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorderManager"

        /** Marca que indica que el rawText es una transcripción del teléfono pendiente de IA. */
        const val PHONE_TRANSCRIPTION_MARKER = "[PHONE_TRANSCRIPTION]"

        /** Placeholder que dispara auto-transcripción con IA al abrir la nota. */
        const val PENDING_TRANSCRIPTION = "Pending Transcription"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFilePath: String? = null

    private var currentRecordMode = 0
    private var speechRecognizerAvailable = false
    private var currentLiveTranscriptEnabled = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var amplitudeJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    @Volatile
    private var isPaused = false

    private var originalMusicVolume = -1
    private var originalSystemVolume = -1
    private var originalRingVolume = -1
    private var originalNotificationVolume = -1
    private var originalAlarmVolume = -1
    private var originalVoiceCallVolume = -1
    private var originalRingerMode = -1
    private var originalHapticFeedbackStatus = -1

    // ============================================================
    // Volume muting
    // ============================================================

    private fun forceMuteAllBeeps() {
        try {
            if (originalMusicVolume == -1) {
                originalMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                originalSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
                originalRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                originalNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                originalVoiceCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                originalRingerMode = audioManager.ringerMode
            }

            try { audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT } catch (e: Exception) {}

            try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) } catch (e: Exception) {}
            try { audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0) } catch (e: Exception) {}
            try { audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0) } catch (e: Exception) {}
            try { audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0) } catch (e: Exception) {}
            try { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0) } catch (e: Exception) {}
            try { audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0) } catch (e: Exception) {}

            try {
                originalHapticFeedbackStatus = Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1)
                Settings.System.putInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 0)
            } catch (e: Exception) {}

        } catch (e: Exception) {
            Log.e(TAG, "forceMuteAllBeeps", e)
        }
    }

    private fun restoreAllVolumes() {
        try {
            if (originalRingerMode != -1) {
                try { audioManager.ringerMode = originalRingerMode } catch (e: Exception) {}
                originalRingerMode = -1
            }

            if (originalMusicVolume != -1) {
                try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalMusicVolume, 0) } catch (e: Exception) {}
                originalMusicVolume = -1
            }
            if (originalSystemVolume != -1) {
                try { audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVolume, 0) } catch (e: Exception) {}
                originalSystemVolume = -1
            }
            if (originalRingVolume != -1) {
                try { audioManager.setStreamVolume(AudioManager.STREAM_RING, originalRingVolume, 0) } catch (e: Exception) {}
                originalRingVolume = -1
            }
            if (originalNotificationVolume != -1) {
                try { audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotificationVolume, 0) } catch (e: Exception) {}
                originalNotificationVolume = -1
            }
            if (originalAlarmVolume != -1) {
                try { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0) } catch (e: Exception) {}
                originalAlarmVolume = -1
            }
            if (originalVoiceCallVolume != -1) {
                try { audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, originalVoiceCallVolume, 0) } catch (e: Exception) {}
                originalVoiceCallVolume = -1
            }

            if (originalHapticFeedbackStatus != -1) {
                try {
                    Settings.System.putInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, originalHapticFeedbackStatus)
                } catch (e: Exception) {}
                originalHapticFeedbackStatus = -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "restoreAllVolumes", e)
        }
    }

    // ============================================================
    // Recording lifecycle
    // ============================================================

    /**
     * Inicia la grabación.
     * - mode 0 (Fast): solo SpeechRecognizer. Audio NO se guarda.
     * - mode 1 (Accurate): MediaRecorder siempre. SpeechRecognizer solo si
     *   [liveTranscriptEnabled] es true y el dispositivo lo soporta.
     *
     * @param isEmulator si true, evita arrancar el recognizer real.
     * @param mode 0 = Fast, 1 = Accurate.
     * @param liveTranscriptEnabled si true, corre el SpeechRecognizer en paralelo
     *   durante Accurate para mostrar texto en vivo. Default false.
     */
    fun startRecording(
        isEmulator: Boolean = false,
        mode: Int = 0,
        liveTranscriptEnabled: Boolean = false
    ) {
        if (_isRecording.value) return

        _isRecording.value = true
        _recognizedText.value = ""
        isPaused = false
        currentRecordMode = mode
        currentLiveTranscriptEnabled = liveTranscriptEnabled
        speechRecognizerAvailable = false

        forceMuteAllBeeps()

        if (mode == 1) {
            // 1) Primero el audio, que es lo crítico.
            prepareMediaRecorder()

            // 2) SpeechRecognizer en paralelo SOLO si el usuario lo pidió.
            //    En la gran mayoría de dispositivos esto es lo que causaba
            //    comportamiento errático del recognizer, así que ahora es opt-in.
            if (liveTranscriptEnabled && !isEmulator && SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizerAvailable = true
                initSpeechRecognizer()
            }

            // Amplitud desde MediaRecorder (más fiable que el recognizer).
            // Siempre se activa, sin importar el estado del live transcript.
            startAmplitudePolling()
        } else {
            // Fast: el recognizer ES la grabación. Debe correr siempre.
            if (isEmulator || !SpeechRecognizer.isRecognitionAvailable(context)) {
                startSimulatedRecording()
            } else {
                speechRecognizerAvailable = true
                initSpeechRecognizer()
            }
        }
    }

    private fun prepareMediaRecorder() {
        val dir = File(context.filesDir, "audio_records")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "RECORD_${System.currentTimeMillis()}.mp4")
        currentAudioFilePath = file.absolutePath

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(AudioCompressor.IDEAL_VOICE_BITRATE)
            setAudioSamplingRate(44100)
            setAudioChannels(1)
            setOutputFile(currentAudioFilePath)

            try {
                prepare()
                start()
            } catch (e: IOException) {
                Log.e(TAG, "MediaRecorder failed to start", e)
                currentAudioFilePath = null
            }
        }
    }

    fun pauseRecording() {
        if (!_isRecording.value || isPaused) return
        isPaused = true

        // Pausar MediaRecorder (si estamos en modo 1 y existe)
        if (currentRecordMode == 1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try { mediaRecorder?.pause() } catch (e: Exception) { Log.w(TAG, "pause mr", e) }
            }
        }

        // Pausar SpeechRecognizer (si existe)
        try { speechRecognizer?.stopListening() } catch (e: Exception) { Log.w(TAG, "pause sr", e) }

        _amplitude.value = 0f
    }

    fun resumeRecording() {
        if (!_isRecording.value || !isPaused) return
        isPaused = false

        if (currentRecordMode == 1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try { mediaRecorder?.resume() } catch (e: Exception) { Log.w(TAG, "resume mr", e) }
            }
            // Solo reactivar el recognizer si el usuario lo tenía encendido.
            if (currentLiveTranscriptEnabled && speechRecognizerAvailable) initSpeechRecognizer()
            startAmplitudePolling()
        } else {
            if (speechRecognizerAvailable) {
                initSpeechRecognizer()
            } else {
                startSimulatedRecording()
            }
        }
    }

    private fun initSpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        // En modo Fast, la amplitud viene del recognizer.
                        // En modo Accurate, la amplitud viene del MediaRecorder.
                        if (currentRecordMode == 0 && _isRecording.value && !isPaused) {
                            _amplitude.value = (rmsdB / 10f).coerceIn(0f, 1f)
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        if (currentRecordMode == 0) _amplitude.value = 0f
                    }

                    override fun onError(error: Int) {
                        val fatalError = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                                error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                                error == SpeechRecognizer.ERROR_CLIENT

                        if (fatalError) {
                            speechRecognizerAvailable = false
                            return
                        }

                        if (_isRecording.value && !isPaused) {
                            // En ambos modos se reintenta, pero en Accurate solo si
                            // el usuario tenía el flag prendido (si no, ni siquiera
                            // estamos acá).
                            initSpeechRecognizer()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val current = _recognizedText.value
                            _recognizedText.value = current + (if (current.isNotEmpty()) "\n" else "") + matches[0]
                        }
                        if (_isRecording.value && !isPaused) {
                            initSpeechRecognizer()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                startListening(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "initSpeechRecognizer failed", e)
            speechRecognizerAvailable = false
        }
    }

    /**
     * Polling de amplitud desde MediaRecorder. Se usa en modo Accurate
     * porque el recognizer en simultáneo no siempre reporta RMS con precisión.
     */
    private fun startAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = coroutineScope.launch {
            while (_isRecording.value && !isPaused && currentRecordMode == 1) {
                _amplitude.value = try {
                    val amp = mediaRecorder?.maxAmplitude ?: 0
                    if (amp > 0) {
                        val db = 20 * log10(amp.toDouble() / 32767.0)
                        ((db + 45) / 45).coerceIn(0.02, 1.0).toFloat()
                    } else 0.02f
                } catch (e: Exception) {
                    0.02f
                }
                delay(30)
            }
            _amplitude.value = 0f
        }
    }

    /**
     * Fallback para emuladores o dispositivos sin SpeechRecognizer.
     * Simula amplitud y texto para no romper la UI.
     */
    private fun startSimulatedRecording() {
        val thread = Thread {
            val phrases = listOf("This is a simulation.", "Obinot is recording.")
            var phraseIndex = 0

            while (_isRecording.value && !isPaused) {
                _amplitude.value = Random.nextFloat()
                Thread.sleep(200)
                if (Random.nextInt(10) > 7 && phraseIndex < phrases.size) {
                    val current = _recognizedText.value
                    _recognizedText.value = current + (if (current.isNotEmpty()) " " else "") + phrases[phraseIndex]
                    phraseIndex++
                }
            }
            _amplitude.value = 0f
        }
        thread.start()
    }

    /**
     * Detiene la grabación y devuelve el path del audio si se guardó.
     * En modo Fast, devuelve null.
     */
    fun stopRecording(): String? {
        _isRecording.value = false
        isPaused = false
        amplitudeJob?.cancel()
        amplitudeJob = null

        if (currentRecordMode == 1) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (e: Exception) {
                Log.e(TAG, "stop MediaRecorder", e)
                currentAudioFilePath = null
            }
            mediaRecorder = null
        }

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "stop SpeechRecognizer", e)
        }
        speechRecognizer = null
        speechRecognizerAvailable = false

        _amplitude.value = 0f

        coroutineScope.launch {
            delay(600)
            restoreAllVolumes()
        }

        val finalPath = currentAudioFilePath
        currentAudioFilePath = null
        return finalPath
    }
}