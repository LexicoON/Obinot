package com.obinot.app.ui.components

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.obinot.app.R
import com.obinot.app.data.SettingsRepository
import com.obinot.app.data.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Representa un archivo de audio encontrado por MediaStore.
 */
data class AudioFileInfo(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val dateModified: Long = 0L
) {
    val durationFormatted: String
        get() {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val sizeFormatted: String
        get() = when {
            sizeBytes >= 1024 * 1024 -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
            sizeBytes >= 1024 -> "%.0f KB".format(sizeBytes / 1024.0)
            else -> "$sizeBytes B"
        }

    val formatFormatted: String
        get() = mimeType.substringAfterLast("/").uppercase()
}

/** Representa un archivo .binot (o .zip con contenido .binot) encontrado por MediaStore. */
data class BinotNoteFileInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val dateModified: Long
) {
    val sizeFormatted: String
        get() = when {
            sizeBytes >= 1024 * 1024 -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
            sizeBytes >= 1024 -> "%.0f KB".format(sizeBytes / 1024.0)
            else -> "$sizeBytes B"
        }
}

/**
 * Órdenes de la pestaña Audio. Cada uno lleva el fragmento SQL que se pasa
 * directo al MediaStore, para que la paginación server-side respete el orden.
 */
enum class AudioSortOrder(@StringRes val labelRes: Int, val sql: String) {
    DATE_MODIFIED_DESC(R.string.sort_newest, "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"),
    DATE_MODIFIED_ASC(R.string.sort_oldest, "${MediaStore.Audio.Media.DATE_MODIFIED} ASC"),
    NAME_ASC(R.string.sort_name_asc, "${MediaStore.Audio.Media.DISPLAY_NAME} COLLATE NOCASE ASC"),
    NAME_DESC(R.string.sort_name_desc, "${MediaStore.Audio.Media.DISPLAY_NAME} COLLATE NOCASE DESC"),
    DURATION_DESC(R.string.sort_longest, "${MediaStore.Audio.Media.DURATION} DESC"),
    DURATION_ASC(R.string.sort_shortest, "${MediaStore.Audio.Media.DURATION} ASC"),
    SIZE_DESC(R.string.sort_largest, "${MediaStore.Audio.Media.SIZE} DESC"),
    SIZE_ASC(R.string.sort_smallest, "${MediaStore.Audio.Media.SIZE} ASC")
}

/** Qué pestaña del picker unificado está activa. */
enum class PickerTab(@StringRes val labelRes: Int) {
    AUDIO(R.string.picker_tab_audio),
    NOTES(R.string.picker_tab_notes)
}

/** Tamaño de página para paginar MediaStore. 150 es un buen balance entre
 *  cantidad de filas por query y fluidez del scroll inicial. */
private const val PAGE_SIZE = 150

/**
 * Picker unificado: audios del dispositivo + notas .binot/.zip exportadas.
 *
 * Desde 2.2, el tab .binot también lista archivos .zip (mismo contenido, distinto
 * nombre cuando el archivo fue reempaquetado por WhatsApp, Gmail, etc.).
 *
 * LIMITACIÓN REAL DE ANDROID (no es un bug): a partir de Android 13, sin el permiso
 * MANAGE_EXTERNAL_STORAGE, MediaStore solo puede listar de forma fiable archivos que
 * la propia Obinot exportó. Por eso la pestaña de Notas siempre incluye un botón
 * "Browse files" que abre el selector del sistema como respaldo garantizado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObinotFilePickerSheet(
    onDismiss: () -> Unit,
    onFileSelected: (Uri) -> Unit,
    initialTab: PickerTab = PickerTab.AUDIO
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(initialTab) }

    var audioFiles by remember { mutableStateOf<List<AudioFileInfo>>(emptyList()) }
    var binotFiles by remember { mutableStateOf<List<BinotNoteFileInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var page by remember { mutableIntStateOf(0) }
    var sortOrder by remember { mutableStateOf(AudioSortOrder.DATE_MODIFIED_DESC) }

    var binotTabPermissionPrompted by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        binotTabPermissionPrompted = context.dataStore.data.first()[
            SettingsRepository.BINOT_TAB_PERMISSION_PROMPTED_KEY
        ] ?: false
    }

    val storagePermission = if (Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, storagePermission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (initialTab == PickerTab.AUDIO && !hasPermission) {
            permissionLauncher.launch(storagePermission)
        }
    }

    LaunchedEffect(hasPermission, activeTab, sortOrder) {
        if (!hasPermission) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        page = 0
        hasMore = true
        when (activeTab) {
            PickerTab.AUDIO -> {
                val initial = withContext(Dispatchers.IO) {
                    queryAudioFiles(context, sortOrder.sql, PAGE_SIZE, 0)
                }
                audioFiles = initial
                hasMore = initial.size == PAGE_SIZE
            }
            PickerTab.NOTES -> {
                val initial = withContext(Dispatchers.IO) {
                    queryBinotFiles(context, PAGE_SIZE, 0)
                }
                binotFiles = initial
                hasMore = initial.size == PAGE_SIZE
            }
        }
        isLoading = false
    }

    fun loadMore() {
        if (isLoadingMore || !hasMore) return
        isLoadingMore = true
        scope.launch {
            val nextPage = page + 1
            when (activeTab) {
                PickerTab.AUDIO -> {
                    val next = withContext(Dispatchers.IO) {
                        queryAudioFiles(context, sortOrder.sql, PAGE_SIZE, nextPage * PAGE_SIZE)
                    }
                    audioFiles = audioFiles + next
                    hasMore = next.size == PAGE_SIZE
                }
                PickerTab.NOTES -> {
                    val next = withContext(Dispatchers.IO) {
                        queryBinotFiles(context, PAGE_SIZE, nextPage * PAGE_SIZE)
                    }
                    binotFiles = binotFiles + next
                    hasMore = next.size == PAGE_SIZE
                }
            }
            page = nextPage
            isLoadingMore = false
        }
    }

    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onFileSelected(uri) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = if (activeTab == PickerTab.AUDIO) Icons.Default.Audiotrack else Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PickerTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PickerTab.entries.size),
                        onClick = { activeTab = tab },
                        selected = activeTab == tab
                    ) { Text(stringResource(tab.labelRes)) }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (activeTab == PickerTab.AUDIO) {
                SortOrderRow(current = sortOrder, onSelect = { sortOrder = it })
                Spacer(Modifier.height(8.dp))
            } else {
                BouncyOutlinedButton(
                    onClick = { safLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.picker_browse_files))
                }
                Spacer(Modifier.height(8.dp))
            }

            val isEmpty = if (activeTab == PickerTab.AUDIO) audioFiles.isEmpty() else binotFiles.isEmpty()

            val showBinotPermissionAviso = activeTab == PickerTab.NOTES
                && !hasPermission
                && binotTabPermissionPrompted == false

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (showBinotPermissionAviso) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.picker_binot_permission_aviso),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        BouncyButton(
                            onClick = {
                                scope.launch {
                                    context.dataStore.edit {
                                        it[SettingsRepository.BINOT_TAB_PERMISSION_PROMPTED_KEY] = true
                                    }
                                    binotTabPermissionPrompted = true
                                    permissionLauncher.launch(storagePermission)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.picker_binot_permission_continue))
                        }
                    }
                }
            } else if (isEmpty) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (!hasPermission) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.picker_permission_message),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            BouncyOutlinedButton(onClick = { permissionLauncher.launch(storagePermission) }) {
                                Text(stringResource(R.string.picker_grant_access))
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (activeTab == PickerTab.AUDIO) stringResource(R.string.picker_no_audio)
                                       else stringResource(R.string.picker_no_notes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (activeTab == PickerTab.AUDIO) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(audioFiles, key = { it.uri.toString() }) { file ->
                        AudioFileRow(file = file, onSelect = { onFileSelected(file.uri) })
                    }
                    if (hasMore) {
                        item(key = "load_more_audio") {
                            LoadMoreRow(isLoading = isLoadingMore, onClick = { loadMore() })
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(binotFiles, key = { it.uri.toString() }) { file ->
                        BinotFileRow(file = file, onSelect = { onFileSelected(file.uri) })
                    }
                    if (hasMore) {
                        item(key = "load_more_binot") {
                            LoadMoreRow(isLoading = isLoadingMore, onClick = { loadMore() })
                        }
                    }
                }
            }
        }
    }
}

/** Alias retrocompatible: abre el picker unificado directamente en la pestaña de Audio. */
@Composable
fun AudioFilePickerSheet(onDismiss: () -> Unit, onFileSelected: (Uri) -> Unit) {
    ObinotFilePickerSheet(onDismiss = onDismiss, onFileSelected = onFileSelected, initialTab = PickerTab.AUDIO)
}

@Composable
private fun LoadMoreRow(isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            BouncyOutlinedButton(onClick = onClick) {
                Text(stringResource(R.string.picker_load_more))
            }
        }
    }
}

@Composable
private fun SortOrderRow(
    current: AudioSortOrder,
    onSelect: (AudioSortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        BouncyOutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(current.labelRes))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AudioSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(order.labelRes)) },
                    onClick = {
                        onSelect(order)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AudioFileRow(
    file: AudioFileInfo,
    onSelect: () -> Unit
) {
    val context = LocalContext.current

    // Carga lazy del artwork embebido. Arranca en null (muestra ícono
    // genérico) y se actualiza en background cuando termina la extracción.
    // Al ser lazy por row, solo se carga lo que el usuario ve; las filas
    // fuera de pantalla recién cargan cuando aparecen por el reciclaje del
    // LazyColumn.
    var artwork by remember(file.uri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file.uri) {
        if (artwork != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            loadAudioArtwork(context, file.uri)
        }
        if (loaded != null) artwork = loaded
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .bouncyClickable(pressedScale = 0.97f) { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val currentArt = artwork
            if (currentArt != null) {
                Image(
                    bitmap = currentArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.durationFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(file.sizeFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(file.formatFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Extrae el artwork embebido de un archivo de audio. Devuelve null si
 * el archivo no tiene artwork o si falla la extracción.
 *
 * Usamos MediaMetadataRetriever directamente (no loadThumbnail) porque
 * loadThumbnail puede devolver un placeholder genérico del sistema cuando
 * el archivo no tiene artwork, y eso taparía nuestro ícono de fallback
 * con uno que no es nuestro.
 *
 * Es costoso por archivo (~5-15ms), por eso se llama lazy en cada row.
 */
private fun loadAudioArtwork(context: Context, uri: Uri): ImageBitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val picture = retriever.embeddedPicture
            if (picture != null && picture.isNotEmpty()) {
                BitmapFactory.decodeByteArray(picture, 0, picture.size)?.asImageBitmap()
            } else {
                null
            }
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun BinotFileRow(
    file: BinotNoteFileInfo,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .bouncyClickable(pressedScale = 0.97f) { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(file.sizeFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================
// MediaStore queries con paginación
//
// En API 26+ usamos el Bundle de ContentResolver para empujar LIMIT y OFFSET
// al cursor de MediaStore. Eso evita materializar miles de filas en memoria.
// En API 24-25 hacemos el skip manual con moveToPosition.
// ============================================================

private fun queryAudioFiles(
    context: Context,
    sortOrderSql: String,
    limit: Int,
    offset: Int
): List<AudioFileInfo> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DATE_MODIFIED
    )

    val result = mutableListOf<AudioFileInfo>()
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bundle = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrderSql)
            }
            context.contentResolver.query(collection, projection, bundle, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    result.add(
                        AudioFileInfo(
                            uri = uri,
                            name = cursor.getString(nameCol) ?: "Unnamed",
                            durationMs = cursor.getLong(durationCol),
                            sizeBytes = cursor.getLong(sizeCol),
                            mimeType = cursor.getString(mimeCol) ?: "audio/*",
                            dateModified = cursor.getLong(dateCol)
                        )
                    )
                }
            }
        } else {
            context.contentResolver.query(collection, projection, null, null, sortOrderSql)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                var skipped = 0
                while (skipped < offset && cursor.moveToNext()) skipped++

                var read = 0
                while (read < limit && cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    result.add(
                        AudioFileInfo(
                            uri = uri,
                            name = cursor.getString(nameCol) ?: "Unnamed",
                            durationMs = cursor.getLong(durationCol),
                            sizeBytes = cursor.getLong(sizeCol),
                            mimeType = cursor.getString(mimeCol) ?: "audio/*",
                            dateModified = cursor.getLong(dateCol)
                        )
                    )
                    read++
                }
            }
        }
    } catch (e: Exception) {
        // Cursor puede fallar si el permiso se revocó justo antes de la query.
    }
    return result
}

/**
 * Consulta MediaStore.Files buscando archivos .binot y .zip.
 *
 * Desde 2.2 también listamos .zip porque cuando alguien comparte una nota por
 * WhatsApp, Gmail, o algunos file managers, el archivo llega renombrado a .zip
 * (mismo contenido adentro). El importador detecta el data.json interno, así
 * que ambos casos funcionan igual.
 *
 * Best-effort: en Android 13+, sin MANAGE_EXTERNAL_STORAGE, esto solo ve
 * archivos que la propia app indexó. Por eso el picker siempre ofrece
 * "Browse files" como respaldo.
 */
private fun queryBinotFiles(
    context: Context,
    limit: Int,
    offset: Int
): List<BinotNoteFileInfo> {
    val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED
    )
    val selection = "(${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?)"
    val args = arrayOf("%.binot", "%.zip")
    val sortOrderSql = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

    val result = mutableListOf<BinotNoteFileInfo>()
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bundle = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrderSql)
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            }
            context.contentResolver.query(collection, projection, bundle, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    result.add(
                        BinotNoteFileInfo(
                            uri = uri,
                            name = cursor.getString(nameCol) ?: "Unnamed.binot",
                            sizeBytes = cursor.getLong(sizeCol),
                            dateModified = cursor.getLong(dateCol)
                        )
                    )
                }
            }
        } else {
            context.contentResolver.query(collection, projection, selection, args, sortOrderSql)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                var skipped = 0
                while (skipped < offset && cursor.moveToNext()) skipped++

                var read = 0
                while (read < limit && cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    result.add(
                        BinotNoteFileInfo(
                            uri = uri,
                            name = cursor.getString(nameCol) ?: "Unnamed.binot",
                            sizeBytes = cursor.getLong(sizeCol),
                            dateModified = cursor.getLong(dateCol)
                        )
                    )
                    read++
                }
            }
        }
    } catch (e: Exception) {
        // Igual que arriba: se degrada a lista vacía y el botón "Browse files" sigue disponible.
    }
    return result
}