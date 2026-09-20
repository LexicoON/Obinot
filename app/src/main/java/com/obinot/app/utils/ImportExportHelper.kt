package com.obinot.app.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.obinot.app.data.LabelRepository
import com.obinot.app.data.NoteEntity
import com.obinot.app.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ImportExportHelper {

    /** Buffer compartido para todas las copias de stream. 64 KB reduce syscalls ~8x vs. 8 KB. */
    private const val STREAM_BUFFER_SIZE = 64 * 1024

    /**
     * Umbral a partir del cual dejamos de cargar el audio a RAM y usamos un
     * temp file en cache. 5 MB cubre el 90% de las grabaciones de voz típicas
     * (una hora a 96 kbps ≈ 43 MB, pero la mayoría son < 2 min).
     */
    private const val MEMORY_COPY_THRESHOLD = 5L * 1024 * 1024

    /**
     * Versión actual del formato .binot. v1 = Binot original, v2 = Obinot.
     * El importador acepta ambas y degrada con gracia.
     */
    private const val BINOT_FORMAT_VERSION = 2

    /**
     * Exporta una nota al formato .binot (ZIP con data.json + audio.mp4 opcional).
     *
     * Formato v2 (Obinot):
     *   data.json           — mismo formato que v1 + "version": 2
     *   audio.mp4           — opcional, igual que v1
     *   obinot_meta.json    — NUEVO: metadatos extendidos (colores de labels, timestamp)
     *
     * Los lectores v1 (Binot original) siguen funcionando porque ignoran entradas
     * que no conocen y data.json mantiene todos los campos v1 con la misma forma.
     *
     * @param labelColors mapa nombre→hex de los labels asignados a ESTA nota. Si está
     *                    vacío o no contiene el label, el receptor usará el color
     *                    default del tema. Solo se incluyen los labels que la nota
     *                    efectivamente usa (no todo el catálogo global).
     */
    suspend fun exportNoteToBinot(
        context: Context,
        note: NoteEntity,
        labelColors: Map<String, String> = emptyMap()
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "shared_notes").apply { mkdirs() }
            val safeTitle = note.title.ifBlank { "Obinot_Note" }.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val fileName = "${safeTitle}.binot"
            val outFile = File(cacheDir, fileName)

            // Filtrar el mapa global al subset de labels que esta nota usa.
            val noteLabels = note.label
                ?.split("|")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val relevantColors = if (noteLabels.isEmpty() || labelColors.isEmpty()) {
                emptyMap()
            } else {
                noteLabels.mapNotNull { name ->
                    labelColors[name]?.let { hex -> name to hex }
                }.toMap()
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile), STREAM_BUFFER_SIZE)).use { zos ->
                // --- data.json ---
                val json = JSONObject().apply {
                    put("version", BINOT_FORMAT_VERSION)
                    put("createdBy", "Obinot")
                    put("title", note.title)
                    put("rawText", note.rawText)
                    put("summary", note.summary)
                    put("highlightsInfo", note.highlightsInfo)
                    put("label", note.label)
                    put("hasAudio", note.audioPath != null)
                }
                val jsonBytes = json.toString().toByteArray(Charsets.UTF_8)
                val jsonEntry = ZipEntry("data.json").apply {
                    method = ZipEntry.STORED
                    size = jsonBytes.size.toLong()
                    compressedSize = jsonBytes.size.toLong()
                    val crc = CRC32().apply { update(jsonBytes) }
                    this.crc = crc.value
                }
                zos.putNextEntry(jsonEntry)
                zos.write(jsonBytes)
                zos.closeEntry()

                // --- obinot_meta.json (solo si hay algo que meter) ---
                // Se omite si no hay colores, para no agregar peso a notas simples.
                // El importador trata la ausencia como "todos los labels con color default".
                if (relevantColors.isNotEmpty()) {
                    val colorsObj = JSONObject().apply {
                        relevantColors.forEach { (name, hex) -> put(name, hex) }
                    }
                    val meta = JSONObject().apply {
                        put("formatVersion", BINOT_FORMAT_VERSION)
                        put("exportedAt", System.currentTimeMillis())
                        put("appVersion", "2.0.0")
                        put("labelColors", colorsObj)
                    }
                    val metaBytes = meta.toString().toByteArray(Charsets.UTF_8)
                    val metaEntry = ZipEntry("obinot_meta.json").apply {
                        method = ZipEntry.STORED
                        size = metaBytes.size.toLong()
                        compressedSize = metaBytes.size.toLong()
                        val crc = CRC32().apply { update(metaBytes) }
                        this.crc = crc.value
                    }
                    zos.putNextEntry(metaEntry)
                    zos.write(metaBytes)
                    zos.closeEntry()
                }

                // --- audio.mp4 ---
                if (note.audioPath != null) {
                    val audioFile = File(note.audioPath)
                    if (audioFile.exists()) {
                        writeStoredFile(zos, audioFile, "audio.mp4")
                    }
                }
            }

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Exporta una nota a un archivo .md (Markdown puro).
     *
     * Estructura:
     *   # {title}
     *
     *   {summary sin el meta tag BINOT_META}
     *
     * Si la nota no tiene summary, se exporta el rawText en su lugar.
     * El meta tag `<!--BINOT_META:...-->` nunca se incluye.
     */
    suspend fun exportNoteToMarkdown(
        context: Context,
        note: NoteEntity
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "shared_notes").apply { mkdirs() }
            val safeTitle = note.title.ifBlank { "Obinot_Note" }.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val fileName = "${safeTitle}.md"
            val outFile = File(cacheDir, fileName)

            val cleanSummary = note.summary
                ?.replace(Regex("<!--BINOT_META:.*?-->"), "")
                ?.trimEnd()

            val body = when {
                !cleanSummary.isNullOrBlank() -> cleanSummary
                note.rawText.isNotBlank() -> note.rawText
                else -> ""
            }

            val title = note.title.ifBlank { "Untitled" }

            val content = buildString {
                append("# ")
                append(title)
                append("\n\n")
                append(body)
                append("\n")
            }

            outFile.writeText(content, Charsets.UTF_8)

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Escribe un archivo al ZIP con método STORED (sin compresión — el audio ya
     * viene comprimido y DEFLATE solo gastaría CPU sin ganar tamaño).
     *
     * STORED requiere CRC32 y size precalculados, así que tenemos que leer el
     * archivo antes de poder escribir el header del ZipEntry. Para no leer el
     * source dos veces:
     *
     *  - Archivos chicos (≤ 5 MB): se cargan a RAM en una sola lectura, se calcula
     *    el CRC y se escriben al ZIP.
     *  - Archivos grandes (> 5 MB): se leen una sola vez al temp file en cache
     *    (calculando CRC y size en esa misma pasada), después se copia del temp
     *    al ZIP. Como el temp acaba de ser escrito, el page cache del SO lo tiene
     *    entero en RAM, así que la segunda lectura no toca disco.
     */
    private fun writeStoredFile(zos: ZipOutputStream, source: File, entryName: String) {
        val fileSize = source.length()

        if (fileSize <= MEMORY_COPY_THRESHOLD) {
            // Camino rápido: 1 sola lectura del source.
            val data = source.readBytes()
            val crc = CRC32().apply { update(data) }
            val entry = ZipEntry(entryName).apply {
                method = ZipEntry.STORED
                size = data.size.toLong()
                compressedSize = data.size.toLong()
                this.crc = crc.value
            }
            zos.putNextEntry(entry)
            zos.write(data)
            zos.closeEntry()
            return
        }

        // Camino streaming: 1 sola lectura del source (a temp), después
        // se copia del temp al ZIP con page cache caliente.
        val tempFile = File.createTempFile("obinot_export_", ".tmp", source.parentFile)
        try {
            val crc = CRC32()
            var size = 0L
            BufferedInputStream(source.inputStream(), STREAM_BUFFER_SIZE).use { input ->
                BufferedOutputStream(tempFile.outputStream(), STREAM_BUFFER_SIZE).use { output ->
                    val buf = ByteArray(STREAM_BUFFER_SIZE)
                    var len: Int
                    while (input.read(buf).also { len = it } > 0) {
                        crc.update(buf, 0, len)
                        output.write(buf, 0, len)
                        size += len
                    }
                }
            }
            val entry = ZipEntry(entryName).apply {
                method = ZipEntry.STORED
                this.size = size
                compressedSize = size
                this.crc = crc.value
            }
            zos.putNextEntry(entry)
            BufferedInputStream(tempFile.inputStream(), STREAM_BUFFER_SIZE).use { input ->
                val buf = ByteArray(STREAM_BUFFER_SIZE)
                var len: Int
                while (input.read(buf).also { len = it } > 0) {
                    zos.write(buf, 0, len)
                }
            }
            zos.closeEntry()
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Importa un archivo .binot (v1 o v2) o un audio crudo.
     *
     * @param labelRepository opcional. Si se pasa y el archivo es v2 con
     *                        obinot_meta.json, se aplican los colores de los
     *                        labels importados. Si es null o el archivo es v1,
     *                        los labels se crean con el color default (que es
     *                        el comportamiento histórico).
     * @return el id de la nota insertada, o null si falló.
     */
    suspend fun importFile(
        context: Context,
        uri: Uri,
        repository: NoteRepository,
        labelRepository: LabelRepository? = null
    ): Int? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            var isBinotArchive = false
            var jsonData = ""
            var obinotMetaJson: String? = null
            var audioTempFile: File? = null

            // LOGIKA BARU: Jangan percaya OS. Langsung bongkar filenya.
            // Kalau ketemu data.json, esto es un archivo .binot (ZIP).
            try {
                contentResolver.openInputStream(uri)?.use { rawInput ->
                    BufferedInputStream(rawInput, STREAM_BUFFER_SIZE).use { buffered ->
                        ZipInputStream(buffered).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                when (entry.name) {
                                    "data.json" -> {
                                        isBinotArchive = true
                                        jsonData = zis.bufferedReader(Charsets.UTF_8).readText()
                                    }
                                    "obinot_meta.json" -> {
                                        obinotMetaJson = zis.bufferedReader(Charsets.UTF_8).readText()
                                    }
                                    "audio.mp4" -> {
                                        val tempAudio = File(context.cacheDir, "temp_import_audio.mp4")
                                        BufferedOutputStream(tempAudio.outputStream(), STREAM_BUFFER_SIZE).use { output ->
                                            val buf = ByteArray(STREAM_BUFFER_SIZE)
                                            var len: Int
                                            while (zis.read(buf).also { len = it } > 0) {
                                                output.write(buf, 0, len)
                                            }
                                        }
                                        audioTempFile = tempAudio
                                    }
                                    // transcript.srt y cualquier otra entrada futura
                                    // se ignoran silenciosamente: forward-compat con
                                    // versiones más nuevas del formato.
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Biarkan lewat. Esto significa que es un archivo de audio puro, no un ZIP/.binot.
            }

            if (isBinotArchive && jsonData.isNotEmpty()) {
                val json = JSONObject(jsonData)
                var finalAudioPath: String? = null

                if (json.optBoolean("hasAudio") && audioTempFile != null && audioTempFile!!.exists()) {
                    val audioDir = File(context.filesDir, "audio_records").apply { mkdirs() }
                    val newAudioFile = File(audioDir, "RECORD_${System.currentTimeMillis()}.mp4")
                    audioTempFile!!.copyTo(newAudioFile, overwrite = true)
                    audioTempFile!!.delete()
                    finalAudioPath = newAudioFile.absolutePath
                }

                // Aplicar colores de labels si es v2 y hay meta + repositorio.
                // Nota: se hace ANTES de insertar la nota porque si el usuario ya
                // tenía un label con otro color, queremos actualizarlo al que viene
                // en el archivo — es la intención explícita del exportador v2.
                if (labelRepository != null && obinotMetaJson != null) {
                    try {
                        val meta = JSONObject(obinotMetaJson)
                        val colorsObj = meta.optJSONObject("labelColors")
                        if (colorsObj != null) {
                            val noteLabels = json.optString("label", "")
                                .split("|")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                            for (labelName in noteLabels) {
                                val hex = colorsObj.optString(labelName, "")
                                if (hex.isNotBlank()) {
                                    val existing = labelRepository.getLabel(labelName)
                                    if (existing == null) {
                                        labelRepository.createLabel(labelName, hex)
                                    } else {
                                        labelRepository.updateColor(labelName, hex)
                                    }
                                } else {
                                    // Asegurar que exista aunque no tenga color
                                    labelRepository.createLabel(labelName)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Si la meta está malformada, no rompemos el import de la nota.
                        // Los labels se crearán con default más adelante vía ensureLabelsExist.
                        e.printStackTrace()
                    }
                }

                val newNote = NoteEntity(
                    title = json.optString("title", ""),
                    rawText = json.optString("rawText", ""),
                    summary = if (json.isNull("summary")) null else json.optString("summary"),
                    highlightsInfo = if (json.isNull("highlightsInfo")) null else json.optString("highlightsInfo"),
                    label = if (json.isNull("label")) null else json.optString("label"),
                    audioPath = finalAudioPath
                )
                return@withContext repository.insert(newNote).toInt()

            } else {
                // FALLBACK: Si no hay data.json, tratar como archivo de audio directo.
                val audioDir = File(context.filesDir, "audio_records").apply { mkdirs() }
                val newAudioFile = File(audioDir, "RECORD_${System.currentTimeMillis()}.mp4")

                contentResolver.openInputStream(uri)?.use { rawInput ->
                    BufferedInputStream(rawInput, STREAM_BUFFER_SIZE).use { input ->
                        BufferedOutputStream(newAudioFile.outputStream(), STREAM_BUFFER_SIZE).use { output ->
                            val buf = ByteArray(STREAM_BUFFER_SIZE)
                            var len: Int
                            while (input.read(buf).also { len = it } > 0) {
                                output.write(buf, 0, len)
                            }
                        }
                    }
                }

                if (newAudioFile.exists() && newAudioFile.length() > 0) {
                    val newNote = NoteEntity(
                        title = "",
                        rawText = "Pending Transcription",
                        summary = null,
                        audioPath = newAudioFile.absolutePath
                    )
                    return@withContext repository.insert(newNote).toInt()
                } else {
                    newAudioFile.delete()
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}