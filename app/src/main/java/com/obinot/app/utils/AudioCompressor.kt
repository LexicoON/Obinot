package com.obinot.app.utils

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Compresor nativo de audio AAC usando MediaCodec en modo asíncrono.
 *
 * Pipeline: MediaExtractor → MediaCodec decoder (AAC→PCM) → MediaCodec encoder (PCM→AAC) → MediaMuxer (MP4)
 *
 * Se usa para reducir el tamaño de grabaciones largas antes de subirlas a la API, ya que
 * algunas (Groq) tienen un límite de 25 MB por archivo.
 *
 * Punto dulce de calidad para voz: 64-96 kbps mono.
 * Por debajo de 48 kbps, la voz empieza a sonar metálica y pierde inteligibilidad.
 *
 * ---
 * ¿Por qué modo asíncrono?
 *
 * El modo síncrono (dequeueInput/OutputBuffer con timeouts en un bucle) tiene un
 * overhead significativo: el hilo hace polling continuo y desperdicia ciclos de CPU
 * esperando a que el codec tenga buffers disponibles. En dispositivos de gama baja,
 * esto se traduce en compresiones que tardan 3-5x más de lo necesario.
 *
 * El modo asíncrono (setCallback) usa dos hilos internos del codec: uno para input
 * y otro para output. El sistema te avisa cuando hay un buffer listo, en vez de que
 * tú preguntes en un bucle. Esto reduce el overhead y permite que el codec use
 * recursos de hardware de forma más eficiente.
 *
 * La documentación oficial lo marca como el método preferido desde API 21.
 */
object AudioCompressor {

    private const val TAG = "AudioCompressor"

    /** Bitrate mínimo aceptable para voz. Por debajo de esto, preferimos avisar al usuario. */
    const val MIN_ACCEPTABLE_BITRATE = 48_000

    /** Bitrate ideal para voz: buena calidad, tamaño contenido. */
    const val IDEAL_VOICE_BITRATE = 96_000

    /** Bitrate máximo: no tiene sentido subir más para voz. */
    const val MAX_BITRATE = 128_000

    /** Tamaño objetivo por defecto (un poco por debajo de 25 MB para dar margen al protocolo HTTP). */
    const val DEFAULT_TARGET_SIZE_MB = 24.5

    /**
     * Máximo de buffers PCM encolados entre decoder y encoder.
     * Cada buffer es típicamente 4-8 KB de PCM. Con 16 buffers el pico de memoria
     * es ~128 KB, insignificante incluso en dispositivos con poca RAM.
     * Si el encoder se atrasa, la cola aplica backpressure natural al decoder.
     */
    private const val PCM_QUEUE_CAPACITY = 16

    /** Timeout de espera al final de la cadena para drenar buffers pendientes. */
    private const val DRAIN_TIMEOUT_MS = 500L

    /** Si el extractor no avanza en este tiempo, se asume que el pipeline se trabó. */
    private const val STALL_TIMEOUT_MS = 8_000L

    /** Tope absoluto de seguridad: 10 minutos. */
    private const val HARD_TIMEOUT_MS = 10 * 60 * 1000L

    sealed class Result {
        data class Success(
            val outputFile: File,
            val originalSize: Long,
            val newSize: Long
        ) : Result()

        data class Failure(val reason: String) : Result()

        /** El archivo es tan largo que comprimirlo por debajo de [MIN_ACCEPTABLE_BITRATE] lo arruinaría. */
        data class QualityTooLow(
            val requiredBitrate: Int,
            val minimumBitrate: Int
        ) : Result()
    }

    /**
     * Calcula el bitrate necesario para que el archivo entre en [targetSizeMB].
     * Retorna null si el bitrate requerido sería menor al mínimo aceptable.
     */
    fun calculateTargetBitrate(
        durationMs: Long,
        targetSizeMB: Double = DEFAULT_TARGET_SIZE_MB
    ): Int? {
        if (durationMs <= 0) return IDEAL_VOICE_BITRATE

        val durationSec = durationMs / 1000.0
        val targetBytes = targetSizeMB * 1024 * 1024
        val bitsPerSecond = (targetBytes * 8) / durationSec
        val bitrate = bitsPerSecond.toInt()

        return if (bitrate < MIN_ACCEPTABLE_BITRATE) null
        else bitrate.coerceAtMost(MAX_BITRATE)
    }

    /**
     * Comprime [inputFile] a AAC con el bitrate [targetBitrate].
     * Escribe el resultado en [outputFile].
     *
     * [onProgress] reporta 0-100 en base a cuánto del audio ya se leyó del extractor.
     * El callback se invoca desde el hilo de control (no desde el hilo del codec),
     * así que es seguro llamar funciones de UI a través de un `launch(Dispatchers.Main)`.
     */
    suspend fun compress(
        inputFile: File,
        outputFile: File,
        targetBitrate: Int,
        onProgress: (percent: Int) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        if (!inputFile.exists()) {
            return@withContext Result.Failure("Input file does not exist")
        }

        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var decoderThread: HandlerThread? = null
        var encoderThread: HandlerThread? = null

        // Flags compartidos entre hilos.
        val inputDone = AtomicBoolean(false)
        val decoderDone = AtomicBoolean(false)
        val encoderDone = AtomicBoolean(false)
        val muxerStarted = AtomicBoolean(false)
        val muxerStopped = AtomicBoolean(false)
        val compressorFailed = AtomicBoolean(false)
        var failureReason: String? = null
        val outputTrackIndex = AtomicLong(-1L)

        // Cola PCM entre decoder y encoder. El decoder deposita, el encoder consume.
        // La capacidad limitada aplica backpressure natural: si el encoder se atrasa,
        // el decoder se bloquea al intentar encolar, y a su vez el input thread se
        // bloquea al intentar alimentar al decoder. El pipeline se auto-regula.
        val pcmQueue = LinkedBlockingQueue<PcmChunk>(PCM_QUEUE_CAPACITY)

        // Marca de tiempo del último sample leído por el extractor.
        // Se usa para detectar stalls: si no avanza en STALL_TIMEOUT_MS, abortamos.
        val lastProgressUs = AtomicLong(0L)
        val lastProgressAt = AtomicLong(System.currentTimeMillis())

        // Estado interno del pipeline. Se accede solo desde el hilo de control.
        var totalDurationUs = -1L
        var lastReportedPercent = -1

        fun reportProgress() {
            if (totalDurationUs <= 0) return
            val currentUs = lastProgressUs.get()
            val percent = ((currentUs.coerceAtMost(totalDurationUs) * 100) / totalDurationUs)
                .toInt()
                .coerceIn(0, 99)
            if (percent != lastReportedPercent) {
                lastReportedPercent = percent
                onProgress(percent)
            }
        }

        try {
            extractor = MediaExtractor().apply {
                setDataSource(inputFile.absolutePath)
            }

            // Buscar la pista de audio.
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || inputFormat == null) {
                return@withContext Result.Failure("No audio track found in input")
            }

            extractor.selectTrack(audioTrackIndex)

            totalDurationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else -1L

            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME)!!

            // --- HandlerThreads dedicados para los callbacks del codec ---
            // Cada codec corre en su propio hilo. MediaCodec garantiza que los
            // callbacks de un mismo codec se serializan en el Handler asociado.
            decoderThread = HandlerThread("AudioDecoder").apply { start() }
            encoderThread = HandlerThread("AudioEncoder").apply { start() }
            val decoderHandler = Handler(decoderThread.looper)
            val encoderHandler = Handler(encoderThread.looper)

            // --- Decoder: AAC → PCM ---
            // Los callbacks se configuran ANTES de configure() en async mode.
            decoder = MediaCodec.createDecoderByType(inputMime).apply {
                setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                        if (compressorFailed.get() || inputDone.get()) {
                            // Si ya no hay más input, señalamos EOS al decoder.
                            try {
                                codec.queueInputBuffer(
                                    index, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "decoder queue EOS", e)
                            }
                            return
                        }

                        try {
                            val buffer = codec.getInputBuffer(index) ?: return
                            buffer.clear()
                            val sampleSize = extractor.readSampleData(buffer, 0)

                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    index, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone.set(true)
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(index, 0, sampleSize, pts, 0)
                                extractor.advance()

                                // Actualizar progreso desde el hilo del decoder.
                                lastProgressUs.set(pts)
                                lastProgressAt.set(System.currentTimeMillis())
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "decoder input error", e)
                            compressorFailed.set(true)
                            failureReason = "Decoder input failed: ${e.message}"
                        }
                    }

                    override fun onOutputBufferAvailable(
                        codec: MediaCodec,
                        index: Int,
                        info: MediaCodec.BufferInfo
                    ) {
                        if (compressorFailed.get()) {
                            try { codec.releaseOutputBuffer(index, false) } catch (_: Exception) {}
                            return
                        }

                        try {
                            val isEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                            if (info.size > 0) {
                                val pcmBuffer = codec.getOutputBuffer(index)
                                if (pcmBuffer != null) {
                                    // Copiar PCM a un array propio. El buffer del codec
                                    // se recicla inmediatamente, así que no podemos
                                    // retener una referencia.
                                    val pcm = ByteArray(info.size)
                                    pcmBuffer.position(info.offset)
                                    pcmBuffer.limit(info.offset + info.size)
                                    pcmBuffer.get(pcm)

                                    // Encolar con backpressure. offer con timeout
                                    // evita deadlock si el encoder falló y ya nadie consume.
                                    val queued = pcmQueue.offer(
                                        PcmChunk(pcm, info.presentationTimeUs, isEos),
                                        DRAIN_TIMEOUT_MS,
                                        TimeUnit.MILLISECONDS
                                    )
                                    if (!queued) {
                                        // El encoder no está consumiendo. Abortar.
                                        compressorFailed.set(true)
                                        failureReason = "Encoder stalled (PCM queue full)"
                                    }
                                }
                            } else if (isEos) {
                                // EOS sin datos: propagar marca a la cola.
                                pcmQueue.offer(PcmChunk(null, info.presentationTimeUs, true))
                            }

                            codec.releaseOutputBuffer(index, false)

                            if (isEos) {
                                decoderDone.set(true)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "decoder output error", e)
                            compressorFailed.set(true)
                            failureReason = "Decoder output failed: ${e.message}"
                        }
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                        // El decoder cambia a formato PCM. No necesitamos hacer nada.
                    }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        Log.e(TAG, "decoder error", e)
                        compressorFailed.set(true)
                        failureReason = "Decoder error: ${e.message}"
                    }
                }, decoderHandler)

                configure(inputFormat, null, null, 0)
                start()
            }

            // --- Encoder: PCM → AAC ---
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                        if (compressorFailed.get()) {
                            try {
                                codec.queueInputBuffer(
                                    index, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                            } catch (_: Exception) {}
                            return
                        }

                        try {
                            // Fallback defensivo: si el decoder terminó, la cola
                            // está vacía y ya pasó suficiente tiempo, señalizar EOS
                            // directamente. Cubre decoders que no propagan la marca
                            // en su último buffer de salida, dejando el encoder
                            // esperando chunks que nunca llegan (cuelgue al 99%).
                            if (inputDone.get() && decoderDone.get() && pcmQueue.isEmpty()) {
                                codec.queueInputBuffer(
                                    index, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                return
                            }

                            // Tomar el siguiente chunk PCM. Poll con timeout para
                            // no bloquear el hilo del encoder indefinidamente.
                            val chunk = pcmQueue.poll(DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)

                            if (chunk == null) {
                                // No hay PCM listo todavía. Devolver el buffer al
                                // encoder sin datos; el sistema nos volverá a avisar.
                                codec.queueInputBuffer(index, 0, 0, 0, 0)
                                return
                            }

                            val buffer = codec.getInputBuffer(index) ?: return
                            buffer.clear()

                            if (chunk.data != null) {
                                buffer.put(chunk.data)
                                // Propagar EOS si el chunk lo trae. Algunos decoders
                                // marcan el último buffer PCM real con EOS + datos, no
                                // como un buffer vacío separado. Sin esto, el encoder
                                // nunca ve el fin de stream y la compresión se queda
                                // colgada al 99%.
                                codec.queueInputBuffer(
                                    index, 0, chunk.data.size,
                                    chunk.presentationTimeUs,
                                    if (chunk.isEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                                )
                            } else {
                                // Chunk EOS sin datos: solo propagar la marca.
                                codec.queueInputBuffer(
                                    index, 0, 0,
                                    chunk.presentationTimeUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "encoder input error", e)
                            compressorFailed.set(true)
                            failureReason = "Encoder input failed: ${e.message}"
                        }
                    }

                    override fun onOutputBufferAvailable(
                        codec: MediaCodec,
                        index: Int,
                        info: MediaCodec.BufferInfo
                    ) {
                        if (compressorFailed.get()) {
                            try { codec.releaseOutputBuffer(index, false) } catch (_: Exception) {}
                            return
                        }

                        try {
                            val isEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                            // Ignorar el buffer de configuración del codec (contiene
                            // el header AAC, no datos de audio).
                            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                codec.releaseOutputBuffer(index, false)
                                if (isEos) encoderDone.set(true)
                                return
                            }

                            if (info.size > 0 && muxerStarted.get()) {
                                val encodedData = codec.getOutputBuffer(index)
                                if (encodedData != null) {
                                    encodedData.position(info.offset)
                                    encodedData.limit(info.offset + info.size)
                                    muxer?.writeSampleData(
                                        outputTrackIndex.get().toInt(),
                                        encodedData,
                                        info
                                    )
                                }
                            }

                            codec.releaseOutputBuffer(index, false)

                            if (isEos) {
                                encoderDone.set(true)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "encoder output error", e)
                            compressorFailed.set(true)
                            failureReason = "Encoder output failed: ${e.message}"
                        }
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                        // El encoder cambió su formato de salida (header AAC listo).
                        // Aquí es donde arrancamos el muxer.
                        if (!muxerStarted.get()) {
                            try {
                                val idx = muxer!!.addTrack(format)
                                outputTrackIndex.set(idx.toLong())
                                muxer!!.start()
                                muxerStarted.set(true)
                            } catch (e: Exception) {
                                Log.e(TAG, "muxer start error", e)
                                compressorFailed.set(true)
                                failureReason = "Muxer start failed: ${e.message}"
                            }
                        }
                    }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        Log.e(TAG, "encoder error", e)
                        compressorFailed.set(true)
                        failureReason = "Encoder error: ${e.message}"
                    }
                }, encoderHandler)

                val outputFormat = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    sampleRate,
                    channelCount
                ).apply {
                    setInteger(
                        MediaFormat.KEY_AAC_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AACObjectLC
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                    // 64 KB cubre holgadamente cualquier chunk PCM típico de un
                    // decoder de audio, incluso estéreo a 48 kHz.
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)

                    // KEY_OPERATING_RATE es un hint para que el codec priorice
                    // velocidad sobre latencia. Para audio el valor es en Hz
                    // (muestras por segundo). Usamos el sample rate (1x) que es
                    // el valor seguro. Ratios más altos (10x) pueden crashear
                    // el codec en algunos dispositivos.
                    // Requiere API 23+ (Android 6.0).
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        setFloat(MediaFormat.KEY_OPERATING_RATE, sampleRate.toFloat())
                    }
                }

                configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            // --- Muxer (sin start todavía) ---
            muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            // --- Hilo de control: monitorea progreso, stalls y finalización ---
            // En async mode el trabajo pesado lo hacen los HandlerThreads.
            // Este bucle solo observa el estado compartido y decide cuándo terminar.
            val startTime = System.currentTimeMillis()
            while (!encoderDone.get()) {
                val now = System.currentTimeMillis()

                if (compressorFailed.get()) {
                    return@withContext Result.Failure(failureReason ?: "Unknown error")
                }

                if (now - startTime > HARD_TIMEOUT_MS) {
                    return@withContext Result.Failure("Compression timed out")
                }

                // Stall detection: si el extractor no avanza en STALL_TIMEOUT_MS,
                // asumimos que algo se trabó y abortamos con un error claro.
                if (!inputDone.get() && now - lastProgressAt.get() > STALL_TIMEOUT_MS) {
                    return@withContext Result.Failure(
                        "Compression stalled (no progress for ${STALL_TIMEOUT_MS / 1000}s)"
                    )
                }

                reportProgress()

                // Si el decoder terminó pero el encoder sigue esperando chunks,
                // el fallback defensivo del callback de input del encoder le
                // señaliza EOS automáticamente. Solo esperamos un poco entre iteraciones.
                Thread.sleep(50)
            }

            // Drenar cualquier PCM que haya quedado en la cola antes de cerrar.
            pcmQueue.clear()

            // Cerrar el muxer limpiamente.
            if (muxerStarted.get() && !muxerStopped.getAndSet(true)) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "muxer stop", e)
                }
            }

            onProgress(100)
            Result.Success(
                outputFile = outputFile,
                originalSize = inputFile.length(),
                newSize = outputFile.length()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed", e)
            if (outputFile.exists()) outputFile.delete()
            Result.Failure(e.message ?: "Unknown error during compression")
        } finally {
            // Orden de liberación importa: primero los codecs (para que los
            // callbacks dejen de dispararse), luego el muxer, luego los hilos.
            try { decoder?.stop() } catch (e: Exception) { Log.w(TAG, "decoder stop", e) }
            try { decoder?.release() } catch (e: Exception) { Log.w(TAG, "decoder release", e) }
            try { encoder?.stop() } catch (e: Exception) { Log.w(TAG, "encoder stop", e) }
            try { encoder?.release() } catch (e: Exception) { Log.w(TAG, "encoder release", e) }
            try { muxer?.release() } catch (e: Exception) { Log.w(TAG, "muxer release", e) }
            try { extractor?.release() } catch (e: Exception) { Log.w(TAG, "extractor release", e) }
            try { decoderThread?.quitSafely() } catch (e: Exception) { Log.w(TAG, "decoderThread quit", e) }
            try { encoderThread?.quitSafely() } catch (e: Exception) { Log.w(TAG, "encoderThread quit", e) }
        }
    }

    /**
     * Chunk de PCM listo para alimentar al encoder.
     * [data] null significa "marca EOS" (fin de stream sin datos adicionales).
     */
    private data class PcmChunk(
        val data: ByteArray?,
        val presentationTimeUs: Long,
        val isEos: Boolean = false
    )
}