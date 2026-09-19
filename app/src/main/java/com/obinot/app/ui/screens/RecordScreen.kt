@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.obinot.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.obinot.app.R
import com.obinot.app.ui.components.AudioFilePickerSheet
import com.obinot.app.ui.components.BouncyButton
import com.obinot.app.ui.components.observeBouncyPress
import com.obinot.app.viewmodel.RecordViewModel
import com.obinot.app.ui.components.AudioWaveform
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    userName: String,
    recordMode: Int,
    aiProvider: Int = 0,
    snackbarHostState: SnackbarHostState,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onNoteClick: (Int) -> Unit,
    onImportFile: suspend (Uri) -> Int? = { null },
    useNativePicker: Boolean = false
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val isAppInLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val recordingSeconds by viewModel.recordingSeconds.collectAsState()
    val recentNotes by viewModel.recentNotes.collectAsState()
    val liveTranscriptEnabled by viewModel.liveTranscriptEnabled.collectAsState()

    val visibleNotes = remember(recentNotes) {
        recentNotes.filterNot { note ->
            val t = note.title.trim()
            t.contains("binot_syst", ignoreCase = true) ||
            t.contains("binot_system", ignoreCase = true) ||
            (t.startsWith("[") && t.endsWith("]"))
        }
    }

    val coroutineScope = rememberCoroutineScope()

    var isTappedExpanded by remember { mutableStateOf(false) }
    var isPressExpanded by remember { mutableStateOf(false) }
    val isExpanded = isTappedExpanded || isPressExpanded

    var greetingTapCount by remember { mutableStateOf(0) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var easterEggAnswer by remember { mutableStateOf("") }
    var showLovePopup by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var isDragHovering by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val morningGreetings = remember(context) {
        listOf(
            context.getString(R.string.record_greeting_morning_1),
            context.getString(R.string.record_greeting_morning_2),
            context.getString(R.string.record_greeting_morning_3),
            context.getString(R.string.record_greeting_morning_4),
            context.getString(R.string.record_greeting_morning_5),
        )
    }
    val afternoonGreetings = remember(context) {
        listOf(
            context.getString(R.string.record_greeting_afternoon_1),
            context.getString(R.string.record_greeting_afternoon_2),
            context.getString(R.string.record_greeting_afternoon_3),
            context.getString(R.string.record_greeting_afternoon_4),
            context.getString(R.string.record_greeting_afternoon_5),
        )
    }
    val eveningGreetings = remember(context) {
        listOf(
            context.getString(R.string.record_greeting_evening_1),
            context.getString(R.string.record_greeting_evening_2),
            context.getString(R.string.record_greeting_evening_3),
            context.getString(R.string.record_greeting_evening_4),
            context.getString(R.string.record_greeting_evening_5),
        )
    }
    val nightGreetings = remember(context) {
        listOf(
            context.getString(R.string.record_greeting_night_1),
            context.getString(R.string.record_greeting_night_2),
            context.getString(R.string.record_greeting_night_3),
            context.getString(R.string.record_greeting_night_4),
            context.getString(R.string.record_greeting_night_5),
        )
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greetings = remember(hour, morningGreetings, afternoonGreetings, eveningGreetings, nightGreetings) {
        when (hour) {
            in 5..11 -> morningGreetings
            in 12..16 -> afternoonGreetings
            in 17..20 -> eveningGreetings
            else -> nightGreetings
        }
    }
    val randomGreeting = remember(hour, greetings) { greetings.random() }

    val guestFallback = stringResource(R.string.record_guest)
    val greetingText = buildAnnotatedString {
        append("$randomGreeting\n")
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(if (userName.isNotBlank()) userName else guestFallback)
        }
        append(".")
    }

    val minutes = (recordingSeconds / 60).toString().padStart(2, '0')
    val seconds = (recordingSeconds % 60).toString().padStart(2, '0')
    val timeString = "$minutes:$seconds"

    val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets

    val listeningText = stringResource(R.string.record_listening)
    val recordingForAiText = stringResource(R.string.record_recording_for_ai)
    val waitingVoiceText = stringResource(R.string.record_waiting_voice)
    val displayLiveText = when {
        recognizedText.isNotEmpty() -> recognizedText
        recordMode == 1 && liveTranscriptEnabled -> listeningText
        recordMode == 1 && !liveTranscriptEnabled -> recordingForAiText
        else -> waitingVoiceText
    }

    val scrollState = rememberScrollState()

    val dragDropTarget = remember(context, coroutineScope, snackbarHostState, onImportFile) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                isDragHovering = true
            }
            override fun onEnded(event: DragAndDropEvent) {
                isDragHovering = false
            }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragHovering = false
                val activity = context as? android.app.Activity
                val androidEvent = event.toAndroidDragEvent()
                val permission = activity?.requestDragAndDropPermissions(androidEvent)

                val clipData = androidEvent.clipData
                if (clipData != null && clipData.itemCount > 0) {
                    var firstImportedId: Int? = null
                    var successCount = 0
                    var failCount = 0
                    coroutineScope.launch {
                        isImporting = true
                        for (i in 0 until clipData.itemCount) {
                            val uri = clipData.getItemAt(i).uri
                            if (uri != null) {
                                val newId = onImportFile(uri)
                                if (newId != null) {
                                    successCount++
                                    if (firstImportedId == null) firstImportedId = newId
                                } else {
                                    failCount++
                                }
                            }
                        }
                        isImporting = false
                        permission?.release()

                        val msg = when {
                            failCount == 0 && successCount > 1 -> context.getString(R.string.record_imported_multiple, successCount)
                            failCount == 0 -> context.getString(R.string.record_imported_success)
                            successCount == 0 -> context.getString(R.string.record_imported_none)
                            else -> context.getString(R.string.record_imported_partial, successCount, failCount)
                        }
                        snackbarHostState.showSnackbar(msg)
                        if (firstImportedId != null && clipData.itemCount == 1) {
                            onNoteClick(firstImportedId)
                        }
                    }
                    return true
                }
                permission?.release()
                return false
            }
        }
    }

    with(animatedVisibilityScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event ->
                        event.mimeTypes().isEmpty() ||
                        event.mimeTypes().any { mimeType ->
                            mimeType.startsWith("audio/") ||
                            mimeType == "application/zip" ||
                            mimeType == "application/octet-stream" ||
                            mimeType.startsWith("application/")
                        }
                    },
                    target = dragDropTarget
                )
        ) {
            M3ExpressiveBackground()

            if (isLandscape) {
                // ============================================================
                // LANDSCAPE — dos columnas.
                // Izquierda: flujo de grabación completo.
                // Derecha: notas recientes en grid vertical.
                // ============================================================
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isExpanded) {
                            if (isExpanded) {
                                detectTapGestures(
                                    onTap = {
                                        isTappedExpanded = false
                                        isPressExpanded = false
                                    }
                                )
                            }
                        }
                ) {
                    // --- Columna izquierda: grabación ---
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(safeTopMargin + 16.dp))

                        RecordScreenGreetingBlock(
                            greetingText = greetingText,
                            timeString = timeString,
                            isRecording = isRecording,
                            isPaused = isPaused,
                            onGreetingTap = {
                                greetingTapCount++
                                if (greetingTapCount > 4) {
                                    greetingTapCount = 0
                                    showEasterEggDialog = true
                                    easterEggAnswer = ""
                                }
                            },
                            horizontalPadding = 24.dp
                        )

                        Spacer(modifier = Modifier.weight(0.3f))

                        // Botones de acción: reutilizamos el bloque existente
                        RecordScreenActionButtons(
                            isRecording = isRecording,
                            isPaused = isPaused,
                            hasPermission = hasPermission,
                            recordMode = recordMode,
                            aiProvider = aiProvider,
                            onRequestPermission = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                            onToggleRecording = {
                                val isEmulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")
                                viewModel.toggleRecording(isEmulator, recordMode)
                            },
                            onPauseRecording = { viewModel.pauseRecording() },
                            onResumeRecording = { viewModel.resumeRecording() },
                            onStopRecording = {
                                viewModel.stopRecordingInstant()
                                coroutineScope.launch {
                                    val saved = viewModel.saveNote(recordMode, aiProvider)
                                    val savedMsg = context.getString(R.string.record_note_saved)
                                    val noTextMsg = context.getString(R.string.record_no_text_to_save)
                                    snackbarHostState.showSnackbar(
                                        message = if (saved) savedMsg else noTextMsg,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            onImportClick = { showAudioPicker = true }
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // --- Columna derecha: notas recientes ---
                    Column(
                        modifier = Modifier
                            .weight(0.7f)
                            .fillMaxHeight()
                            .padding(end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(safeTopMargin + 16.dp))

                        if (visibleNotes.isNotEmpty()) {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(1),
                                modifier = Modifier.fillMaxSize(),
                                verticalItemSpacing = 8.dp,
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(visibleNotes, key = { it.id }) { note ->
                                    val displayTitle = if (note.title.isBlank()) stringResource(R.string.trash_empty_note) else note.title

                                    val noteInteraction = remember { MutableInteractionSource() }
                                    val noteScale = remember { Animatable(1f) }
                                    LaunchedEffect(noteInteraction) {
                                        observeBouncyPress(
                                            interactionSource = noteInteraction,
                                            scale = noteScale,
                                            pressedScale = 0.95f
                                        )
                                    }

                                    with(sharedTransitionScope) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    scaleX = noteScale.value
                                                    scaleY = noteScale.value
                                                }
                                                .sharedBounds(
                                                    sharedContentState = rememberSharedContentState("record_note-${note.id}"),
                                                    animatedVisibilityScope = animatedVisibilityScope,
                                                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                                    boundsTransform = { _, _ -> tween(300) }
                                                )
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .clickable(
                                                    interactionSource = noteInteraction,
                                                    indication = null,
                                                    onClick = { onNoteClick(note.id) }
                                                )
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = displayTitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isAppInLightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.history_no_notes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // ============================================================
                // PORTRAIT — comportamiento original intacto.
                // ============================================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isExpanded) {
                            if (isExpanded) {
                                detectTapGestures(
                                    onTap = {
                                        isTappedExpanded = false
                                        isPressExpanded = false
                                    }
                                )
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(safeTopMargin + 24.dp))

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val availableHeight = maxHeight

                        val stiffSpring = spring<Dp>(dampingRatio = 0.9f, stiffness = 400f)

                        val boxHeight by animateDpAsState(
                            targetValue = if (isExpanded) availableHeight else 160.dp,
                            animationSpec = stiffSpring,
                            label = "boxHeight"
                        )
                        val topAlpha by animateFloatAsState(
                            targetValue = if (isExpanded) 0f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "topAlpha"
                        )
                        val cornerRadius by animateDpAsState(
                            targetValue = if (isExpanded) 40.dp else 32.dp,
                            animationSpec = stiffSpring,
                            label = "cornerRadius"
                        )
                        val containerColor by animateColorAsState(
                            targetValue = if (isExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "containerColor"
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (isExpanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "contentColor"
                        )

                        val boxScale by animateFloatAsState(
                            targetValue = if (isPressExpanded) 0.97f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessHigh),
                            label = "boxScale"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 176.dp)
                                .alpha(topAlpha)
                                .animateEnterExit(enter = slideInVertically { -50 } + fadeIn()),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = greetingText,
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                greetingTapCount++
                                                if (greetingTapCount > 4) {
                                                    greetingTapCount = 0
                                                    showEasterEggDialog = true
                                                    easterEggAnswer = ""
                                                }
                                            }
                                        )
                                    }
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isPaused -> MaterialTheme.colorScheme.tertiaryContainer
                                    isRecording -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                },
                                modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
                            ) {
                                AnimatedContent(targetState = timeString, label = "timeAnimation") { time ->
                                    Text(
                                        text = time,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = when {
                                            isPaused -> MaterialTheme.colorScheme.onTertiaryContainer
                                            isRecording -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isRecording || isPaused,
                                    enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.8f)),
                                    exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f)
                                ) {
                                    AudioWaveform(
                                        amplitude = amplitude,
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
                                    )
                                }

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !isRecording && !isPaused && visibleNotes.isNotEmpty(),
                                    enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 50 }),
                                    exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { 50 })
                                ) {
                                    LazyHorizontalStaggeredGrid(
                                        rows = StaggeredGridCells.Fixed(2),
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalItemSpacing = 12.dp,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)
                                    ) {
                                        items(visibleNotes, key = { it.id }) { note ->
                                            val displayTitle = if (note.title.isBlank()) stringResource(R.string.trash_empty_note) else note.title
                                            val randomPadding = remember(note.id) { (note.id * 23 % 40).dp }

                                            val noteInteraction = remember { MutableInteractionSource() }
                                            val noteScale = remember { Animatable(1f) }
                                            LaunchedEffect(noteInteraction) {
                                                observeBouncyPress(
                                                    interactionSource = noteInteraction,
                                                    scale = noteScale,
                                                    pressedScale = 0.95f
                                                )
                                            }

                                            with(sharedTransitionScope) {
                                                Box(
                                                    modifier = Modifier
                                                        .graphicsLayer {
                                                            scaleX = noteScale.value
                                                            scaleY = noteScale.value
                                                        }
                                                        .sharedBounds(
                                                            sharedContentState = rememberSharedContentState("record_note-${note.id}"),
                                                            animatedVisibilityScope = animatedVisibilityScope,
                                                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                                            boundsTransform = { _, _ -> tween(300) }
                                                        )
                                                        .clip(RoundedCornerShape(32.dp))
                                                        .background(MaterialTheme.colorScheme.surface)
                                                        .clickable(
                                                            interactionSource = noteInteraction,
                                                            indication = null,
                                                            onClick = { onNoteClick(note.id) }
                                                        )
                                                        .heightIn(min = 64.dp)
                                                        .padding(
                                                            horizontal = (32.dp + randomPadding),
                                                            vertical = 22.dp
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = displayTitle,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = if (isAppInLightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(0.5f))
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(boxHeight)
                                .scale(boxScale)
                                .padding(horizontal = 24.dp)
                                .clip(RoundedCornerShape(cornerRadius))
                                .background(containerColor)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { _, dragAmount ->
                                        if (!isExpanded && dragAmount < -5) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isTappedExpanded = true
                                        }
                                    }
                                }
                                .pointerInput("tap", isTappedExpanded) {
                                    if (!isTappedExpanded) {
                                        detectTapGestures(
                                            onTap = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                isTappedExpanded = true
                                            }
                                        )
                                    }
                                }
                                .pointerInput("hold", isTappedExpanded) {
                                    if (!isTappedExpanded) {
                                        detectTapGestures(
                                            onPress = {
                                                isPressExpanded = true
                                                tryAwaitRelease()
                                                isPressExpanded = false
                                            }
                                        )
                                    }
                                }
                                .padding(top = 8.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)
                        ) {
                            LaunchedEffect(recognizedText, isExpanded) {
                                if (recognizedText.isNotEmpty()) {
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                }
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clickable(
                                            enabled = isExpanded,
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isTappedExpanded = false
                                            isPressExpanded = false
                                        }
                                        .pointerInput(isExpanded) {
                                            if (isExpanded) {
                                                detectVerticalDragGestures { _, dragAmount ->
                                                    if (dragAmount > 5) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        isTappedExpanded = false
                                                        isPressExpanded = false
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(32.dp)
                                            .height(4.dp)
                                            .clip(CircleShape)
                                            .background(contentColor.copy(alpha = 0.3f))
                                    )
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.record_live_transcription),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }

                                AnimatedContent(
                                    targetState = displayLiveText,
                                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                                    label = "TranscriptionFade"
                                ) { text ->
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = contentColor,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(scrollState, enabled = isExpanded)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    RecordScreenActionButtons(
                        isRecording = isRecording,
                        isPaused = isPaused,
                        hasPermission = hasPermission,
                        recordMode = recordMode,
                        aiProvider = aiProvider,
                        onRequestPermission = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                        onToggleRecording = {
                            val isEmulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")
                            viewModel.toggleRecording(isEmulator, recordMode)
                        },
                        onPauseRecording = { viewModel.pauseRecording() },
                        onResumeRecording = { viewModel.resumeRecording() },
                        onStopRecording = {
                            viewModel.stopRecordingInstant()
                            coroutineScope.launch {
                                val saved = viewModel.saveNote(recordMode, aiProvider)
                                val savedMsg = context.getString(R.string.record_note_saved)
                                val noTextMsg = context.getString(R.string.record_no_text_to_save)
                                snackbarHostState.showSnackbar(
                                    message = if (saved) savedMsg else noTextMsg,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        },
                        onImportClick = { showAudioPicker = true }
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Overlay de import (aplica en ambos layouts)
            if (isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.record_importing),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Overlay de drag hover (aplica en ambos layouts)
            if (isDragHovering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 32.dp, vertical = 24.dp)
                    ) {
                        Icon(
                            Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.record_drop_to_import),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.record_drop_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    // SAF fallback
    val safAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            coroutineScope.launch {
                val newId = onImportFile(uri)
                isImporting = false
                if (newId != null) onNoteClick(newId)
                else snackbarHostState.showSnackbar(context.getString(R.string.record_import_failed_audio))
            }
        }
    }

    LaunchedEffect(showAudioPicker) {
        if (showAudioPicker && !useNativePicker) {
            showAudioPicker = false
            safAudioLauncher.launch(arrayOf("audio/*"))
        }
    }

    if (showAudioPicker && useNativePicker) {
        AudioFilePickerSheet(
            onDismiss = { showAudioPicker = false },
            onFileSelected = { uri ->
                showAudioPicker = false
                isImporting = true
                coroutineScope.launch {
                    val newId = onImportFile(uri)
                    isImporting = false
                    if (newId != null) {
                        onNoteClick(newId)
                    } else {
                        snackbarHostState.showSnackbar(context.getString(R.string.record_import_failed_audio))
                    }
                }
            }
        )
    }

    if (showEasterEggDialog) {
        AlertDialog(
            onDismissRequest = {
                showEasterEggDialog = false
                easterEggAnswer = ""
            },
            title = {
                Text(
                    text = stringResource(R.string.record_egg_question_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.record_egg_question_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = easterEggAnswer,
                        onValueChange = { if (it.length <= 5) easterEggAnswer = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.record_egg_answer_hint)) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (easterEggAnswer.trim().equals("dinda", ignoreCase = true)) {
                            showEasterEggDialog = false
                            showLovePopup = true
                        }
                        easterEggAnswer = ""
                    }
                ) {
                    Text(stringResource(R.string.record_egg_submit))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEasterEggDialog = false
                    easterEggAnswer = ""
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showLovePopup) {
        AlertDialog(
            onDismissRequest = { showLovePopup = false },
            title = null,
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(text = "💖", style = MaterialTheme.typography.displayMedium)
                    Text(
                        text = stringResource(R.string.record_egg_poem_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.record_egg_poem),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.record_egg_signature),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = { showLovePopup = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.record_egg_close))
                }
            }
        )
    }
}

/**
 * Bloque de saludo + timer. Se usa en landscape y en portrait.
 */
@Composable
private fun RecordScreenGreetingBlock(
    greetingText: androidx.compose.ui.text.AnnotatedString,
    timeString: String,
    isRecording: Boolean,
    isPaused: Boolean,
    onGreetingTap: () -> Unit,
    horizontalPadding: Dp = 24.dp
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = greetingText,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onGreetingTap() })
                }
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = CircleShape,
            color = when {
                isPaused -> MaterialTheme.colorScheme.tertiaryContainer
                isRecording -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            },
            modifier = Modifier.padding(start = horizontalPadding, bottom = 16.dp)
        ) {
            AnimatedContent(targetState = timeString, label = "timeAnimation") { time ->
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        isPaused -> MaterialTheme.colorScheme.onTertiaryContainer
                        isRecording -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Bloque de botones de acción (record/pause/resume/stop/import).
 * Se usa en landscape y en portrait.
 */
@Composable
private fun RecordScreenActionButtons(
    isRecording: Boolean,
    isPaused: Boolean,
    hasPermission: Boolean,
    recordMode: Int,
    aiProvider: Int,
    onRequestPermission: () -> Unit,
    onToggleRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onImportClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isSplit = isRecording || isPaused
    val totalAreaWidth = 280.dp
    val importButtonSize = 64.dp
    val gapBetweenButtons = 12.dp

    Box(
        modifier = Modifier
            .widthIn(min = totalAreaWidth)
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        var isLeftPressed by remember { mutableStateOf(false) }
        var isStopPressed by remember { mutableStateOf(false) }
        var isImportPressed by remember { mutableStateOf(false) }

        val leftTargetWidth = when {
            isStopPressed && isSplit -> 88.dp
            isLeftPressed && isSplit -> 152.dp
            isLeftPressed            -> totalAreaWidth + 56.dp
            isSplit                  -> 120.dp
            else                     -> totalAreaWidth
        }
        val rightTargetWidth = when {
            !isSplit                  -> 0.dp
            isStopPressed              -> 152.dp
            isLeftPressed               -> 88.dp
            else                        -> 120.dp
        }
        val gapTarget = if (isSplit) 16.dp else 0.dp

        val leftButtonWidth by animateDpAsState(targetValue = leftTargetWidth, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "leftWidth")
        val rightButtonWidth by animateDpAsState(targetValue = rightTargetWidth, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "rightWidth")
        val rightButtonAlpha by animateFloatAsState(targetValue = if (isSplit) 1f else 0f, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "rightAlpha")
        val gapWidth by animateDpAsState(targetValue = gapTarget, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "gap")
        val leftIconScale by animateFloatAsState(targetValue = if (isLeftPressed && !isSplit) 1.12f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "leftIconScale")
        val importAlpha by animateFloatAsState(targetValue = if (isSplit) 0f else 1f, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "importAlpha")
        val importScale by animateFloatAsState(targetValue = if (isImportPressed) 0.90f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "importScale")

        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isSplit) gapWidth else gapBetweenButtons),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth()
        ) {
            Box(
                modifier = Modifier
                    .width(leftButtonWidth)
                    .height(80.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSplit && !isPaused -> MaterialTheme.colorScheme.secondaryContainer
                            isSplit && isPaused  -> MaterialTheme.colorScheme.primaryContainer
                            else                 -> MaterialTheme.colorScheme.primary
                        }
                    )
                    .pointerInput(isSplit, isPaused) {
                        detectTapGestures(
                            onPress = {
                                isLeftPressed = true
                                tryAwaitRelease()
                                isLeftPressed = false
                                when {
                                    !isSplit -> {
                                        if (!hasPermission) onRequestPermission()
                                        else onToggleRecording()
                                    }
                                    isPaused -> onResumeRecording()
                                    else     -> onPauseRecording()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val pauseCd = stringResource(R.string.record_pause_cd)
                val resumeCd = stringResource(R.string.record_resume_cd)
                val recordCd = stringResource(R.string.record_record_cd)
                val recordLabel = stringResource(R.string.record_record_button)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            isSplit && isPaused -> Icons.Default.PlayArrow
                            isSplit            -> Icons.Default.Pause
                            else               -> Icons.Default.Mic
                        },
                        contentDescription = when {
                            isSplit && isPaused -> resumeCd
                            isSplit            -> pauseCd
                            else               -> recordCd
                        },
                        tint = when {
                            isSplit && !isPaused -> MaterialTheme.colorScheme.onSecondaryContainer
                            isSplit && isPaused  -> MaterialTheme.colorScheme.onPrimaryContainer
                            else                 -> MaterialTheme.colorScheme.onPrimary
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .scale(leftIconScale)
                    )
                    if (!isSplit) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = recordLabel,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            if (rightButtonWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .width(rightButtonWidth)
                        .height(80.dp)
                        .alpha(rightButtonAlpha)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isStopPressed = true
                                    tryAwaitRelease()
                                    isStopPressed = false
                                    onStopRecording()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isStopPressed && recordMode == 1) {
                        LoadingIndicator(
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = stringResource(R.string.record_stop_cd),
                            tint = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            if (!isSplit) {
                Box(
                    modifier = Modifier
                        .size(importButtonSize)
                        .graphicsLayer {
                            scaleX = importScale
                            scaleY = importScale
                            alpha = importAlpha
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isImportPressed = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    tryAwaitRelease()
                                    isImportPressed = false
                                    onImportClick()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = stringResource(R.string.record_import_audio_cd),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun M3ExpressiveBackground() {
    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    val secondaryColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryColor, Color.Transparent),
                center = Offset(w * 0.5f, h * 0.2f),
                radius = w * 0.8f
            ),
            center = Offset(w * 0.5f, h * 0.2f),
            radius = w * 0.8f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryColor, Color.Transparent),
                center = Offset(w * 0.2f, h * 0.7f),
                radius = w * 0.7f
            ),
            center = Offset(w * 0.2f, h * 0.7f),
            radius = w * 0.7f
        )
    }
}