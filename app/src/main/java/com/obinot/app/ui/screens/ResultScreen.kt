package com.obinot.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.obinot.app.R
import com.obinot.app.ui.components.AudioFilePickerSheet
import com.obinot.app.ui.components.BouncyButton
import com.obinot.app.ui.components.BouncyCapsule
import com.obinot.app.ui.components.BouncyChip
import com.obinot.app.ui.components.BouncyIconButton
import com.obinot.app.ui.components.MarkdownText
import com.obinot.app.ui.theme.resolveLabelColors
import com.obinot.app.utils.AudioRecorderManager
import com.obinot.app.viewmodel.ResultViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    noteId: Int,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val note by viewModel.note.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()
    val error by viewModel.error.collectAsState()

    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val allLabels by viewModel.allLabels.collectAsState()
    val labelColors by viewModel.labelColors.collectAsState()
    val showAnalyzeChip by viewModel.showAnalyzeChip.collectAsState()

    // Detección de orientación. En landscape el side panel pasa a ser permanente
    // en la columna derecha; en portrait sigue siendo un ModalBottomSheet.
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val hasPhoneTranscription = remember(note?.rawText) {
        note?.rawText?.startsWith(AudioRecorderManager.PHONE_TRANSCRIPTION_MARKER) == true
    }

    var showSidePanel by remember { mutableStateOf(false) }
    var showNewLabelDialog by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }

    var isEditMode by remember { mutableStateOf(false) }
    var textValue by remember(note?.id) { mutableStateOf(TextFieldValue(note?.rawText ?: "")) }
    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showDestructiveConfirmDialog by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(textValue.text, note?.rawText) {
        hasUnsavedChanges = textValue.text != (note?.rawText ?: "")
    }

    var newLabelInput by remember { mutableStateOf("") }
    var searchHighlightQuery by remember { mutableStateOf("") }
    var temporaryHighlight by remember { mutableStateOf("") }
    var selectedFont by remember { mutableStateOf(FontFamily.SansSerif) }

    var showHighlightDialog by remember { mutableStateOf(false) }
    var currentHighlightWord by remember { mutableStateOf("") }
    var highlightNoteInput by remember { mutableStateOf("") }
    var pendingHighlightLine by remember { mutableStateOf(-1) }
    var pendingHighlightStart by remember { mutableStateOf(-1) }
    var pendingHighlightEnd by remember { mutableStateOf(-1) }

    var resolveMarkdownSelection by remember { mutableStateOf<((Rect, String) -> Triple<Int, Int, Int>?)?>(null) }
    var rawTextLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    var rawTextWindowBounds by remember { mutableStateOf<Rect?>(null) }

    var showAiExplainSheet by remember { mutableStateOf(false) }
    var aiExplainTargetWord by remember { mutableStateOf("") }

    var selectionRect by remember { mutableStateOf(Rect.Zero) }
    var showCustomMenu by remember { mutableStateOf(false) }
    var isTextSelected by remember { mutableStateOf(false) }
    var copyAction by remember { mutableStateOf<() -> Unit>({}) }
    var selectAllAction by remember { mutableStateOf<() -> Unit>({}) }
    var selectionResetKey by remember { mutableStateOf(0) }

    val clearSelection: () -> Unit = {
        if (showCustomMenu) showCustomMenu = false
        isTextSelected = false
        selectionResetKey++
    }

    val markdownScrollState = rememberScrollState()
    val markdownLinePositions: SnapshotStateMap<Int, Int> = remember { mutableStateMapOf() }

    val rawTextScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectionContentBounds by remember { mutableStateOf<Rect?>(null) }
    var dragPointerWindowY by remember { mutableStateOf<Float?>(null) }
    var isPointerDown by remember { mutableStateOf(false) }

    LaunchedEffect(isPointerDown) {
        if (!isPointerDown) return@LaunchedEffect
        val edgeThreshold = 72f
        val maxScrollSpeedPx = 28f
        while (isPointerDown) {
            val pointerY = dragPointerWindowY
            val bounds = selectionContentBounds
            if (pointerY != null && bounds != null) {
                val distanceFromTop = pointerY - bounds.top
                val distanceFromBottom = bounds.bottom - pointerY
                val usingMarkdown = note?.summary.isNullOrEmpty() == false
                when {
                    distanceFromTop in 0f..edgeThreshold -> {
                        val speed = maxScrollSpeedPx * (1f - distanceFromTop / edgeThreshold)
                        if (usingMarkdown) markdownScrollState.scrollBy(-speed) else rawTextScrollState.scrollBy(-speed)
                    }
                    distanceFromBottom in 0f..edgeThreshold -> {
                        val speed = maxScrollSpeedPx * (1f - distanceFromBottom / edgeThreshold)
                        if (usingMarkdown) markdownScrollState.scrollBy(speed) else rawTextScrollState.scrollBy(speed)
                    }
                }
            }
            delay(16L)
        }
    }

    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val deviceLanguage = java.util.Locale.getDefault().displayLanguage

    var isTitleFocused by remember { mutableStateOf(false) }
    val titleFocusRequester = remember { FocusRequester() }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val exportAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/mp4")
    ) { uri ->
        uri?.let {
            viewModel.exportAudio(context, it) { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        showContent = true
    }

    val closeNote: () -> Unit = {
        showContent = false
        onNavigateBack()
    }

    BackHandler(enabled = true) {
        if (isTextSelected || showCustomMenu) {
            clearSelection()
        } else if (showCancelConfirmDialog) {
            showCancelConfirmDialog = false
        } else if (showSidePanel && !isLandscape) {
            showSidePanel = false
        } else if (isEditMode) {
            if (hasUnsavedChanges) showCancelConfirmDialog = true else isEditMode = false
        } else if (isTitleFocused) {
            focusManager.clearFocus()
        } else {
            closeNote()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            showCustomMenu = false
            isTextSelected = false
        }
    }

    val customTextToolbar = remember {
        object : TextToolbar {
            override var status: TextToolbarStatus = TextToolbarStatus.Hidden
            override fun hide() {
                status = TextToolbarStatus.Hidden
                showCustomMenu = false
            }
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                selectionRect = rect
                copyAction = onCopyRequested ?: {}
                selectAllAction = onSelectAllRequested ?: {}
                status = TextToolbarStatus.Shown
                showCustomMenu = true
                isTextSelected = true
            }
        }
    }

    fun extractSelectedTextAndExecute(action: (String) -> Unit) {
        val oldClip = clipboardManager.getText()
        copyAction()
        val newClip = clipboardManager.getText()?.text ?: ""
        if (oldClip != null) {
            clipboardManager.setText(oldClip)
        } else {
            clipboardManager.setText(AnnotatedString(""))
        }
        clearSelection()
        action(newClip.trim())
    }

    val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets

    // ============================================================
    // Contenido del side panel (labels + find/format + font + export/media).
    //
    // Se extrajo a una lambda @Composable para poder reutilizarlo en los dos
    // layouts:
    //   - Portrait: dentro de ModalBottomSheet (comportamiento actual).
    //   - Landscape: columna fija a la derecha del contenido.
    //
    // La lambda captura el estado del composable padre. Cualquier acción que
    // requiera cerrar el sheet en portrait (mostrar picker, restaurar rawText,
    // etc.) también cierra el sheet explícitamente vía `showSidePanel = false`.
    // En landscape esa línea es un no-op (el sheet no está abierto).
    // ============================================================
    val sidePanelContent: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            PanelSectionHeader(icon = Icons.AutoMirrored.Filled.Label, title = stringResource(R.string.result_section_labels))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allLabels.filter { it.isNotBlank() }) { label ->
                    val activeLabels = note!!.label?.split("|")?.map { it.trim() } ?: emptyList()
                    val isSelected = activeLabels.contains(label)
                    val assignedHex = labelColors[label]

                    val (chipColor, chipTextColor) = resolveLabelColors(assignedHex, isSelected)

                    BouncyChip(
                        onClick = { viewModel.toggleLabel(label) },
                        containerColor = chipColor,
                        contentColor = chipTextColor
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Label, null, tint = chipTextColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = chipTextColor, fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    BouncyChip(
                        onClick = { showNewLabelDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.result_new_label_chip), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }

            SectionSpacer()

            PanelSectionHeader(icon = Icons.Default.Search, title = stringResource(R.string.result_section_find_format))
            OutlinedTextField(
                value = searchHighlightQuery,
                onValueChange = { searchHighlightQuery = it },
                label = { Text(stringResource(R.string.result_find_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_search)) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            val cleanSummaryForSearch = remember(note!!.summary) {
                note!!.summary?.replace(Regex("<!--BINOT_META:.*?-->"), "")?.trimEnd()
            }
            val textToSearch = cleanSummaryForSearch ?: note!!.rawText
            val lines = remember(textToSearch) { textToSearch.split("\n") }
            val searchResults = remember(lines, searchHighlightQuery) {
                if (searchHighlightQuery.isBlank()) emptyList()
                else lines.mapIndexedNotNull { index, line ->
                    if (line.contains(searchHighlightQuery, ignoreCase = true)) {
                        index to line.trim()
                    } else null
                }
            }

            if (searchHighlightQuery.isNotBlank() && searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                    items(searchResults) { (index, line) ->
                        Text(
                            text = line, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable {
                                coroutineScope.launch {
                                    temporaryHighlight = searchHighlightQuery
                                    if (note!!.summary != null) {
                                        val target = markdownLinePositions[index]
                                            ?: markdownLinePositions.keys.filter { it <= index }.maxOrNull()?.let { markdownLinePositions[it] }
                                        if (target != null) {
                                            markdownScrollState.animateScrollTo(target)
                                        }
                                    } else {
                                        rawTextScrollState.animateScrollTo(index * 60)
                                    }
                                    showSidePanel = false
                                    delay(4000)
                                    temporaryHighlight = ""
                                }
                            }.padding(vertical = 12.dp, horizontal = 8.dp)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                }
            }

            SectionSpacer()

            PanelSectionHeader(icon = Icons.Default.TextFields, title = stringResource(R.string.result_section_reading_font))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    onClick = { selectedFont = FontFamily.SansSerif },
                    selected = selectedFont == FontFamily.SansSerif
                ) { Text(stringResource(R.string.result_font_sans)) }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    onClick = { selectedFont = FontFamily.Serif },
                    selected = selectedFont == FontFamily.Serif
                ) { Text(stringResource(R.string.result_font_serif)) }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    onClick = { selectedFont = FontFamily.Monospace },
                    selected = selectedFont == FontFamily.Monospace
                ) { Text(stringResource(R.string.result_font_mono)) }
            }

            SectionSpacer()

            PanelSectionHeader(icon = Icons.Default.Tune, title = stringResource(R.string.result_section_export_media))

            if (note!!.audioPath == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.result_no_audio_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (note!!.summary != null) {
                    item {
                        BouncyCapsule(
                            onClick = {
                                viewModel.restoreRawText()
                                coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.result_summary_removed)) }
                                showSidePanel = false
                            },
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.result_restore_original), tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.result_restore_original), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item {
                    BouncyCapsule(
                        onClick = {
                            showSidePanel = false
                            showAudioPicker = true
                        },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (note!!.audioPath == null) stringResource(R.string.result_add_audio) else stringResource(R.string.result_replace_audio),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (note!!.audioPath != null) {
                    item {
                        val playInteraction = remember { MutableInteractionSource() }
                        val playScale = remember { Animatable(1f) }
                        LaunchedEffect(playInteraction) {
                            playInteraction.interactions.collect { i ->
                                when (i) {
                                    is PressInteraction.Press -> playScale.animateTo(0.92f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                                    is PressInteraction.Release, is PressInteraction.Cancel -> playScale.animateTo(1f, spring(0.40f, Spring.StiffnessMediumLow))
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = playScale.value
                                    scaleY = playScale.value
                                }
                                .height(48.dp).clip(CircleShape)
                                .background(if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer)
                                .clickable(
                                    interactionSource = playInteraction,
                                    indication = null,
                                    onClick = { viewModel.toggleAudio() }
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.result_cd_play_pause),
                                    tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (isPlaying) stringResource(R.string.result_pause) else stringResource(R.string.result_play),
                                    color = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    item {
                        BouncyCapsule(
                            onClick = { exportAudioLauncher.launch("Obinot_Audio_${note!!.id}.mp4") },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.result_save_audio_button), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.result_save_audio_button), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                item {
                    BouncyCapsule(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val cleanSummaryToCopy = note!!.summary?.replace(Regex("<!--BINOT_META:.*?-->"), "")?.trimEnd()
                            clipboard.setPrimaryClip(ClipData.newPlainText("Obinot Note", cleanSummaryToCopy ?: note!!.rawText))
                            coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.result_text_copied)) }
                            showSidePanel = false
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.result_copy), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.result_copy), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                item {
                    BouncyCapsule(
                        onClick = {
                            viewModel.shareBinotFile(context) { uri, msg ->
                                if (uri != null) {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.result_share_text, note!!.title))
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.result_share_chooser)))
                                } else {
                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            }
                            showSidePanel = false
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.result_share_binot), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.result_share_binot), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                }

                item {
                    BouncyCapsule(
                        onClick = {
                            viewModel.exportMarkdownFile(context) { uri, msg ->
                                if (uri != null) {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/markdown"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.result_share_text_markdown, note!!.title))
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.result_share_markdown_chooser)))
                                } else {
                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            }
                            showSidePanel = false
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(Icons.Default.Description, contentDescription = stringResource(R.string.result_export_markdown), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.result_export_markdown), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (note!!.audioPath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = playbackProgress,
                    onValueChange = { viewModel.seekAudio(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    with(sharedTransitionScope) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "note-$noteId"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                    boundsTransform = { _, _ -> tween(300) }
                )
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(top = safeTopMargin),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        if (note != null && showContent) {
                            BasicTextField(
                                value = note!!.title,
                                onValueChange = { viewModel.updateTitle(it) },
                                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                    .focusRequester(titleFocusRequester)
                                    .onFocusChanged { isTitleFocused = it.isFocused },
                                decorationBox = { innerTextField ->
                                    if (!isTitleFocused) {
                                        if (note!!.title.isBlank()) {
                                            Text(
                                                text = stringResource(R.string.result_title_placeholder),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Text(
                                                text = note!!.title,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    } else {
                                        innerTextField()
                                    }
                                }
                            )
                        } else if (note != null) {
                            if (note!!.title.isNotBlank()) {
                                Text(
                                    text = note!!.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        BouncyIconButton(
                            onClick = {
                                if (isTitleFocused) focusManager.clearFocus()
                                else if (isEditMode) {
                                    if (hasUnsavedChanges) showCancelConfirmDialog = true else isEditMode = false
                                }
                                else closeNote()
                            },
                            expandOnPress = 3.dp
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.result_cd_back))
                        }
                    },
                    actions = {
                        AnimatedVisibility(
                            visible = !isTitleFocused && showContent && note?.rawText != AudioRecorderManager.PENDING_TRANSCRIPTION,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            if (isEditMode) {
                                Row {
                                    BouncyIconButton(
                                        onClick = {
                                            if (undoStack.isNotEmpty()) {
                                                redoStack.add(textValue)
                                                textValue = undoStack.removeLast()
                                            }
                                        },
                                        enabled = undoStack.isNotEmpty()
                                    ) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.result_cd_undo), tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                    }
                                    BouncyIconButton(
                                        onClick = {
                                            if (redoStack.isNotEmpty()) {
                                                undoStack.add(textValue)
                                                textValue = redoStack.removeLast()
                                            }
                                        },
                                        enabled = redoStack.isNotEmpty()
                                    ) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.result_cd_redo), tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                    }
                                }
                            } else if (!isLandscape) {
                                // En landscape el side panel está siempre visible,
                                // así que el botón 3-puntos no hace falta.
                                BouncyIconButton(
                                    onClick = { showSidePanel = true },
                                    expandOnPress = 3.dp
                                ) {
                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.result_cd_options))
                                }
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                if (note != null && note!!.summary.isNullOrEmpty() && !isLoading && showContent && note!!.rawText != AudioRecorderManager.PENDING_TRANSCRIPTION) {
                    val isFabExpanded by remember { derivedStateOf { rawTextScrollState.value == 0 || isEditMode } }
                    val fabInteraction = remember { MutableInteractionSource() }
                    val fabScale = remember { Animatable(1f) }
                    LaunchedEffect(fabInteraction) {
                        fabInteraction.interactions.collect { interaction ->
                            when (interaction) {
                                is PressInteraction.Press -> fabScale.animateTo(
                                    0.94f,
                                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                )
                                is PressInteraction.Release, is PressInteraction.Cancel -> fabScale.animateTo(
                                    1f,
                                    spring(0.40f, Spring.StiffnessMediumLow)
                                )
                            }
                        }
                    }

                    with(sharedTransitionScope) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                if (isEditMode) {
                                    showDestructiveConfirmDialog = true
                                } else {
                                    textValue = TextFieldValue(note!!.rawText)
                                    undoStack.clear()
                                    redoStack.clear()
                                    isEditMode = true
                                }
                            },
                            expanded = isFabExpanded,
                            icon = { Icon(if (isEditMode) Icons.Default.Check else Icons.Default.Edit, contentDescription = if (isEditMode) stringResource(R.string.result_process) else stringResource(R.string.result_edit)) },
                            text = { Text(if (isEditMode) stringResource(R.string.result_process) else stringResource(R.string.result_edit)) },
                            containerColor = if (isEditMode) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isEditMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            interactionSource = fabInteraction,
                            modifier = Modifier
                                .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                                .alpha(if (animatedVisibilityScope.transition.targetState == EnterExitState.Visible) 1f else 0f)
                                .graphicsLayer {
                                    scaleX = fabScale.value
                                    scaleY = fabScale.value
                                }
                                .then(with(animatedVisibilityScope) {
                                    Modifier.animateEnterExit(
                                        enter = scaleIn(initialScale = 0f, animationSpec = tween(300)),
                                        exit = scaleOut(targetScale = 0f, animationSpec = tween(300))
                                    )
                                })
                                // En landscape el side panel está a la derecha, así
                                // que corremos el FAB hacia la izquierda para que no
                                // quede debajo del panel.
                                .padding(end = if (isLandscape) 360.dp else 0.dp)
                        )
                    }
                }
            }
        ) { paddingValues ->
            if (note == null || !showContent) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    AiThinkingAnimation(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .imePadding()
                        .onGloballyPositioned { coordinates ->
                            selectionContentBounds = coordinates.boundsInWindow()
                        }
                        .pointerInput(isTextSelected) {
                            awaitEachGesture {
                                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                                isPointerDown = true
                                dragPointerWindowY = down.position.y + (selectionContentBounds?.top ?: 0f)
                                var totalMovement = 0f
                                var lastPosition = down.position
                                do {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change != null) {
                                        dragPointerWindowY = change.position.y + (selectionContentBounds?.top ?: 0f)
                                        totalMovement += (change.position - lastPosition).getDistance()
                                        lastPosition = change.position
                                    }
                                } while (event.changes.any { it.pressed })
                                isPointerDown = false
                                dragPointerWindowY = null

                                if (isTextSelected && totalMovement < 24f) {
                                    clearSelection()
                                }
                            }
                        }
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Columna izquierda: contenido.
                        // En portrait ocupa el 100%; en landscape se queda con el
                        // espacio restante después del panel (360dp).
                        Column(
                            modifier = if (isLandscape) {
                                Modifier.weight(1f).fillMaxHeight()
                            } else {
                                Modifier.fillMaxSize()
                            }
                        ) {
                            if (showAnalyzeChip) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    BouncyChip(
                                        onClick = { viewModel.analyzeManually() },
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.result_analyze_chip),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (isLoading) {
                                val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.2f,
                                    targetValue = 0.6f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "shimmer_alpha"
                                )
                                val skeletonColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)

                                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(28.dp).clip(RoundedCornerShape(8.dp)).background(skeletonColor))
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(skeletonColor))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(skeletonColor))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Box(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(skeletonColor))
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Box(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(skeletonColor))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Box(modifier = Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(skeletonColor))

                                    Spacer(modifier = Modifier.height(48.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        AiThinkingAnimation(color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = loadingMessage.ifBlank { stringResource(R.string.result_processing) },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (error != null && note!!.rawText == AudioRecorderManager.PENDING_TRANSCRIPTION && note!!.audioPath != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(stringResource(R.string.result_error_api_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(error!!, color = MaterialTheme.colorScheme.onErrorContainer, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(modifier = Modifier.height(32.dp))

                                        val playInteraction = remember { MutableInteractionSource() }
                                        val playScale = remember { Animatable(1f) }
                                        LaunchedEffect(playInteraction) {
                                            playInteraction.interactions.collect { i ->
                                                when (i) {
                                                    is PressInteraction.Press -> playScale.animateTo(0.90f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                                                    is PressInteraction.Release, is PressInteraction.Cancel -> playScale.animateTo(1f, spring(0.40f, Spring.StiffnessMediumLow))
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .graphicsLayer {
                                                    scaleX = playScale.value
                                                    scaleY = playScale.value
                                                }
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.error)
                                                .clickable(interactionSource = playInteraction, indication = null) { viewModel.toggleAudio() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = stringResource(R.string.result_cd_play_pause),
                                                tint = MaterialTheme.colorScheme.onError,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Slider(
                                            value = playbackProgress,
                                            onValueChange = { viewModel.seekAudio(it) },
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.error,
                                                activeTrackColor = MaterialTheme.colorScheme.error
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                        BouncyCapsule(
                                            onClick = { exportAudioLauncher.launch("Obinot_Audio_Fallback_${note!!.id}.mp4") },
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.result_save_audio), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.result_save_audio), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else if (error != null) {
                                Text(
                                    text = error!!,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }

                            if (!note!!.summary.isNullOrEmpty() && !isLoading) {
                                CompositionLocalProvider(LocalTextToolbar provides customTextToolbar) {
                                    key(selectionResetKey) {
                                        SelectionContainer(modifier = Modifier.fillMaxSize().clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            if (isTitleFocused) focusManager.clearFocus()
                                        }) {
                                            val cleanSummary = remember(note!!.summary) {
                                                note!!.summary!!.replace(Regex("<!--BINOT_META:.*?-->"), "").trimEnd()
                                            }
                                            MarkdownText(
                                                text = cleanSummary,
                                                scrollState = markdownScrollState,
                                                highlightsInfo = note!!.highlightsInfo,
                                                onSavedHighlightClick = { word, noteText, line, start, end ->
                                                    currentHighlightWord = word
                                                    highlightNoteInput = noteText
                                                    pendingHighlightLine = line
                                                    pendingHighlightStart = start
                                                    pendingHighlightEnd = end
                                                    showHighlightDialog = true
                                                },
                                                onResolveSelection = { resolver -> resolveMarkdownSelection = resolver },
                                                highlightQuery = temporaryHighlight,
                                                onCheckboxToggle = { lineIndex -> viewModel.toggleCheckbox(lineIndex) },
                                                fontFamily = selectedFont,
                                                linePositions = markdownLinePositions,
                                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                            } else if (!isLoading && note!!.rawText != AudioRecorderManager.PENDING_TRANSCRIPTION) {
                                if (isEditMode) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .padding(bottom = 16.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .padding(16.dp)
                                        ) {
                                            BasicTextField(
                                                value = textValue,
                                                onValueChange = { newValue ->
                                                    if (newValue.text != textValue.text) {
                                                        undoStack.add(textValue)
                                                        redoStack.clear()
                                                    }
                                                    textValue = newValue
                                                },
                                                modifier = Modifier.fillMaxSize(),
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontFamily = selectedFont
                                                ),
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                } else {
                                    val rawPrefix = "Raw Transcript:\n\n"
                                    val savedRawHighlights = remember(note!!.highlightsInfo) {
                                        val list = mutableListOf<Triple<String, Int, Int>>()
                                        val json = note!!.highlightsInfo
                                        if (!json.isNullOrBlank() && json != "[]") {
                                            try {
                                                val array = org.json.JSONArray(json)
                                                for (i in 0 until array.length()) {
                                                    val obj = array.getJSONObject(i)
                                                    if (obj.optInt("line", -1) == -1 && obj.optInt("start", -1) >= 0) {
                                                        list.add(Triple(obj.getString("text"), obj.getInt("start"), obj.getInt("end")))
                                                    }
                                                }
                                            } catch (e: Exception) { e.printStackTrace() }
                                        }
                                        list
                                    }
                                    val rawHighlightNotesByKey = remember(note!!.highlightsInfo) {
                                        val map = mutableMapOf<String, String>()
                                        val json = note!!.highlightsInfo
                                        if (!json.isNullOrBlank() && json != "[]") {
                                            try {
                                                val array = org.json.JSONArray(json)
                                                for (i in 0 until array.length()) {
                                                    val obj = array.getJSONObject(i)
                                                    val isRaw = obj.optInt("line", -1) == -1
                                                    if (!isRaw) continue
                                                    val start = obj.optInt("start", -1)
                                                    val key = if (start >= 0) "${obj.getInt("start")}:${obj.getInt("end")}" else "legacy:${obj.getString("text")}"
                                                    map[key] = obj.getString("note")
                                                }
                                            } catch (e: Exception) { e.printStackTrace() }
                                        }
                                        map
                                    }
                                    val legacyRawHighlights = remember(note!!.highlightsInfo) {
                                        val map = mutableMapOf<String, String>()
                                        val json = note!!.highlightsInfo
                                        if (!json.isNullOrBlank() && json != "[]") {
                                            try {
                                                val array = org.json.JSONArray(json)
                                                for (i in 0 until array.length()) {
                                                    val obj = array.getJSONObject(i)
                                                    if (obj.optInt("line", -1) == -1 && obj.optInt("start", -1) < 0) {
                                                        map[obj.getString("text")] = obj.getString("note")
                                                    }
                                                }
                                            } catch (e: Exception) { e.printStackTrace() }
                                        }
                                        map
                                    }
                                    val rawSavedHighlightColor = MaterialTheme.colorScheme.tertiaryContainer
                                    val rawSavedHighlightTextColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    val rawTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)

                                    val displayRawText = remember(note!!.rawText) {
                                        if (note!!.rawText.startsWith(AudioRecorderManager.PHONE_TRANSCRIPTION_MARKER)) {
                                            note!!.rawText
                                                .removePrefix(AudioRecorderManager.PHONE_TRANSCRIPTION_MARKER)
                                                .trimStart('\n', ' ')
                                        } else {
                                            note!!.rawText
                                        }
                                    }
                                    val rawAnnotatedString = remember(displayRawText, savedRawHighlights, legacyRawHighlights, temporaryHighlight, rawSavedHighlightColor, rawSavedHighlightTextColor, rawTextColor) {
                                        buildHighlightedString(
                                            prefix = rawPrefix,
                                            text = displayRawText,
                                            query = temporaryHighlight,
                                            savedHighlights = savedRawHighlights,
                                            legacyHighlights = legacyRawHighlights,
                                            highlightColor = Color.Yellow.copy(alpha = 0.5f),
                                            savedHighlightColor = rawSavedHighlightColor,
                                            savedHighlightTextColor = rawSavedHighlightTextColor,
                                            textColor = rawTextColor
                                        )
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .verticalScroll(rawTextScrollState)
                                    ) {
                                        if (hasPhoneTranscription) {
                                            PhoneTranscriptionBanner(
                                                onReanalyze = { viewModel.reanalyzeWithAI() }
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                        SelectionContainer {
                                            Text(
                                                text = rawAnnotatedString,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = selectedFont),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onGloballyPositioned { coordinates ->
                                                        rawTextWindowBounds = coordinates.boundsInWindow()
                                                    }
                                                    .pointerInput(rawAnnotatedString) {
                                                        detectTapGestures { pos ->
                                                            rawTextLayoutResult?.let { layoutResult ->
                                                                val offset = layoutResult.getOffsetForPosition(pos)
                                                                rawAnnotatedString.getStringAnnotations(tag = "SAVED_HIGHLIGHT", start = offset, end = offset)
                                                                    .firstOrNull()?.let { annotation ->
                                                                        val parts = annotation.item.split("@@KEY@@")
                                                                        val displayWord = parts.getOrElse(0) { "" }
                                                                        val key = parts.getOrNull(1) ?: "legacy:$displayWord"
                                                                        currentHighlightWord = displayWord
                                                                        highlightNoteInput = rawHighlightNotesByKey[key] ?: ""
                                                                        if (key.startsWith("legacy:")) {
                                                                            pendingHighlightLine = -1
                                                                            pendingHighlightStart = -1
                                                                            pendingHighlightEnd = -1
                                                                        } else {
                                                                            val (s, e) = key.split(":").map { it.toInt() }
                                                                            pendingHighlightLine = -1
                                                                            pendingHighlightStart = s
                                                                            pendingHighlightEnd = e
                                                                        }
                                                                        showHighlightDialog = true
                                                                    }
                                                            }
                                                        }
                                                    },
                                                onTextLayout = { rawTextLayoutResult = it }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Columna derecha: side panel permanente (solo landscape).
                        if (isLandscape) {
                            // Divider vertical entre contenido y panel.
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )

                            // Panel: ancho fijo 360dp, altura completa, con su propio scroll.
                            sidePanelContent(Modifier.width(360.dp).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }

    if (showHighlightDialog) {
        fun closeHighlightDialog() {
            showHighlightDialog = false
            pendingHighlightLine = -1
            pendingHighlightStart = -1
            pendingHighlightEnd = -1
        }
        AlertDialog(
            onDismissRequest = { closeHighlightDialog() },
            title = { Text(stringResource(R.string.result_highlight_title)) },
            text = {
                Column {
                    Text("\"$currentHighlightWord\"", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = highlightNoteInput,
                        onValueChange = { highlightNoteInput = it },
                        label = { Text(stringResource(R.string.result_highlight_note)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                BouncyButton(onClick = {
                    viewModel.saveHighlightNote(
                        currentHighlightWord,
                        highlightNoteInput,
                        lineIndex = pendingHighlightLine,
                        startIndex = pendingHighlightStart,
                        endIndex = pendingHighlightEnd
                    )
                    closeHighlightDialog()
                }) {
                    Text(stringResource(R.string.history_save))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.removeHighlight(
                                currentHighlightWord,
                                lineIndex = pendingHighlightLine,
                                startIndex = pendingHighlightStart
                            )
                            closeHighlightDialog()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.result_highlight_remove))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { closeHighlightDialog() }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        )
    }

    if (showAiExplainSheet) {
        val explainResult by viewModel.explainResult.collectAsState()
        val isExplaining by viewModel.isExplaining.collectAsState()
        val explainScrollState = rememberScrollState()

        val scrollWall = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                showAiExplainSheet = false
                viewModel.clearExplainResult()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.result_ai_explain_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text("\"$aiExplainTargetWord\"", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(scrollWall)
                ) {
                    if (isExplaining) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AiThinkingAnimation(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        MarkdownText(
                            text = explainResult ?: stringResource(R.string.result_ai_explain_empty),
                            scrollState = explainScrollState,
                            highlightsInfo = null,
                            onSavedHighlightClick = { _, _, _, _, _ -> },
                            onResolveSelection = { null },
                            highlightQuery = "",
                            fontFamily = selectedFont,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showCustomMenu) {
        val density = LocalDensity.current
        Popup(
            popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {
                    var x = selectionRect.left.toInt() - (popupContentSize.width / 2) + (selectionRect.width.toInt() / 2)
                    var y = selectionRect.top.toInt() - popupContentSize.height - with(density) { 8.dp.roundToPx() }

                    if (x < 16) x = 16
                    if (x + popupContentSize.width > windowSize.width - 16) {
                        x = windowSize.width - popupContentSize.width - 16
                    }
                    if (y < 16) {
                        y = selectionRect.bottom.toInt() + with(density) { 8.dp.roundToPx() }
                    }
                    return IntOffset(x, y)
                }
            },
            onDismissRequest = { showCustomMenu = false }
        ) {
            Card(
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    BouncyIconButton(
                        onClick = {
                            val capturedRect = selectionRect
                            extractSelectedTextAndExecute { text ->
                                if (text.isNotBlank()) {
                                    currentHighlightWord = text
                                    highlightNoteInput = ""
                                    if (!note!!.summary.isNullOrEmpty()) {
                                        val resolved = resolveMarkdownSelection?.invoke(capturedRect, text)
                                        if (resolved != null) {
                                            pendingHighlightLine = resolved.first
                                            pendingHighlightStart = resolved.second
                                            pendingHighlightEnd = resolved.third
                                        } else {
                                            pendingHighlightLine = -1
                                            pendingHighlightStart = -1
                                            pendingHighlightEnd = -1
                                        }
                                    } else {
                                        val rawPrefix = "Raw Transcript:\n\n"
                                        val layoutResult = rawTextLayoutResult
                                        val bounds = rawTextWindowBounds
                                        if (layoutResult != null && bounds != null) {
                                            val localX = (capturedRect.left - bounds.left).coerceIn(0f, bounds.width)
                                            val localY = (capturedRect.center.y - bounds.top).coerceIn(0f, bounds.height)
                                            val approxOffset = try {
                                                layoutResult.getOffsetForPosition(androidx.compose.ui.geometry.Offset(localX, localY))
                                            } catch (e: Exception) { -1 }
                                            val fullText = rawPrefix + note!!.rawText
                                            val fullLower = fullText.lowercase()
                                            val textLower = text.lowercase()
                                            var bestStart = -1
                                            var bestDist = Int.MAX_VALUE
                                            var searchFrom = 0
                                            while (true) {
                                                val idx = fullLower.indexOf(textLower, searchFrom)
                                                if (idx == -1) break
                                                if (approxOffset >= 0) {
                                                    val dist = kotlin.math.abs(idx - approxOffset)
                                                    if (dist < bestDist) { bestDist = dist; bestStart = idx }
                                                } else if (bestStart == -1) {
                                                    bestStart = idx
                                                }
                                                searchFrom = idx + 1
                                            }
                                            if (bestStart >= rawPrefix.length) {
                                                pendingHighlightLine = -1
                                                pendingHighlightStart = bestStart - rawPrefix.length
                                                pendingHighlightEnd = bestStart - rawPrefix.length + text.length
                                            } else {
                                                pendingHighlightLine = -1
                                                pendingHighlightStart = -1
                                                pendingHighlightEnd = -1
                                            }
                                        } else {
                                            pendingHighlightLine = -1
                                            pendingHighlightStart = -1
                                            pendingHighlightEnd = -1
                                        }
                                    }
                                    showHighlightDialog = true
                                }
                            }
                        },
                        expandOnPress = 4.dp
                    ) {
                        Icon(Icons.Default.Brush, contentDescription = stringResource(R.string.result_cd_highlight), tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                    BouncyIconButton(
                        onClick = {
                            copyAction()
                            clearSelection()
                            coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.result_copied_clipboard)) }
                        },
                        expandOnPress = 4.dp
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.result_cd_copy), tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                    BouncyIconButton(
                        onClick = {
                            selectAllAction()
                        },
                        expandOnPress = 4.dp
                    ) {
                        Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.result_cd_select_all), tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                    BouncyIconButton(
                        onClick = {
                            extractSelectedTextAndExecute { text ->
                                if (text.isNotBlank()) {
                                    aiExplainTargetWord = text
                                    viewModel.explainText(text, deviceLanguage)
                                    showAiExplainSheet = true
                                }
                            }
                        },
                        expandOnPress = 4.dp
                    ) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.result_cd_ai_explain), tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
            }
        }
    }

    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = { Text(stringResource(R.string.result_cancel_edit_title)) },
            text = { Text(stringResource(R.string.result_cancel_edit_body)) },
            confirmButton = {
                BouncyButton(
                    onClick = {
                        showCancelConfirmDialog = false
                        hasUnsavedChanges = false
                        isEditMode = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.result_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text(stringResource(R.string.result_keep_editing))
                }
            }
        )
    }

    if (showDestructiveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDestructiveConfirmDialog = false },
            title = { Text(stringResource(R.string.result_overwrite_title)) },
            text = { Text(stringResource(R.string.result_overwrite_body)) },
            confirmButton = {
                BouncyButton(
                    onClick = {
                        viewModel.updateRawText(textValue.text)
                        hasUnsavedChanges = false
                        isEditMode = false
                        showDestructiveConfirmDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.result_processing))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.result_process_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDestructiveConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showNewLabelDialog) {
        AlertDialog(
            onDismissRequest = { showNewLabelDialog = false },
            title = { Text(stringResource(R.string.result_new_label_title)) },
            text = {
                OutlinedTextField(
                    value = newLabelInput,
                    onValueChange = { newLabelInput = it },
                    label = { Text(stringResource(R.string.history_label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                BouncyButton(onClick = {
                    if (newLabelInput.isNotBlank()) {
                        viewModel.toggleLabel(newLabelInput.trim())
                        showNewLabelDialog = false
                        newLabelInput = ""
                    }
                }) { Text(stringResource(R.string.result_create_assign)) }
            },
            dismissButton = { TextButton(onClick = { showNewLabelDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    if (showAudioPicker) {
        AudioFilePickerSheet(
            onDismiss = { showAudioPicker = false },
            onFileSelected = { uri ->
                showAudioPicker = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.result_replacing_audio))
                }
                viewModel.replaceAudio(context, uri) { success ->
                    coroutineScope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar(context.getString(R.string.result_replace_success))
                        } else {
                            snackbarHostState.showSnackbar(context.getString(R.string.result_replace_failed))
                        }
                    }
                }
            }
        )
    }

    // Sheet del panel — solo en portrait. En landscape el panel ya está
    // siempre visible como columna fija, así que el ModalBottomSheet es
    // innecesario.
    if (showSidePanel && note != null && !isLandscape) {
        ModalBottomSheet(
            onDismissRequest = { showSidePanel = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            sidePanelContent(Modifier.fillMaxWidth())
        }
    }
}

// ============================================================
// Helpers locales
// ============================================================

@Composable
private fun PanelSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionSpacer() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun PhoneTranscriptionBanner(
    onReanalyze: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.result_phone_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = stringResource(R.string.result_phone_banner_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
            )
            BouncyButton(
                onClick = onReanalyze,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.result_phone_banner_button), fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun buildHighlightedString(
    prefix: String = "",
    text: String,
    query: String,
    savedHighlights: List<Triple<String, Int, Int>> = emptyList(),
    legacyHighlights: Map<String, String> = emptyMap(),
    highlightColor: Color,
    savedHighlightColor: Color = highlightColor,
    savedHighlightTextColor: Color = Color.Black,
    textColor: Color
) = buildAnnotatedString {
    withStyle(SpanStyle(color = textColor)) { append(prefix) }
    withStyle(SpanStyle(color = textColor)) { append(text) }

    val fullLength = prefix.length + text.length

    savedHighlights.forEach { (word, start, end) ->
        val localStart = start + prefix.length
        val localEnd = end + prefix.length
        if (localStart in 0 until fullLength && localEnd in (localStart + 1)..fullLength) {
            addStyle(
                style = SpanStyle(background = savedHighlightColor, color = savedHighlightTextColor, fontWeight = FontWeight.SemiBold),
                start = localStart,
                end = localEnd
            )
            addStringAnnotation(
                tag = "SAVED_HIGHLIGHT",
                annotation = "$word@@KEY@@$start:$end",
                start = localStart,
                end = localEnd
            )
        }
    }

    val fullLower = (prefix + text).lowercase()
    legacyHighlights.keys.forEach { word ->
        val wordLower = word.lowercase()
        if (wordLower.isBlank()) return@forEach
        var idx = fullLower.indexOf(wordLower)
        while (idx >= 0) {
            addStyle(
                style = SpanStyle(background = savedHighlightColor, color = savedHighlightTextColor, fontWeight = FontWeight.SemiBold),
                start = idx,
                end = idx + wordLower.length
            )
            addStringAnnotation(
                tag = "SAVED_HIGHLIGHT",
                annotation = "$word@@KEY@@legacy:$word",
                start = idx,
                end = idx + wordLower.length
            )
            idx = fullLower.indexOf(wordLower, idx + 1)
        }
    }

    if (query.isNotBlank()) {
        val queryLower = query.lowercase()
        var idx = fullLower.indexOf(queryLower)
        while (idx >= 0) {
            addStyle(
                style = SpanStyle(background = highlightColor, color = Color.Black),
                start = idx,
                end = idx + queryLower.length
            )
            idx = fullLower.indexOf(queryLower, idx + 1)
        }
    }
}

@Composable
private fun AiThinkingAnimation(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_thinking")
    val heights = List(4) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 400, delayMillis = index * 100, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "height_$index"
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(32.dp)
    ) {
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight(height.value)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}