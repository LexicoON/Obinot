package com.obinot.app.ui.screens

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.obinot.app.R
import com.obinot.app.data.NoteEntity
import com.obinot.app.ui.components.BouncyIconButton
import com.obinot.app.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit
) {
    val trashedNotes by viewModel.trashedNotes.collectAsState()
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var selectedNotes by remember { mutableStateOf(setOf<Int>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            if (selectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        windowInsets = WindowInsets(top = safeTopMargin),
                        title = { Text(stringResource(R.string.trash_selected_count, selectedNotes.size)) },
                        navigationIcon = {
                            // expandOnPress = 3.dp: pegado al borde izquierdo.
                            BouncyIconButton(
                                onClick = { selectionMode = false; selectedNotes = emptySet() },
                                expandOnPress = 3.dp
                            ) {
                                Icon(Icons.Default.Close, stringResource(R.string.common_cancel))
                            }
                        },
                        actions = {
                            // Pegados al borde derecho.
                            BouncyIconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.restoreMultipleFromTrash(selectedNotes)
                                    selectionMode = false
                                    selectedNotes = emptySet()
                                },
                                expandOnPress = 3.dp
                            ) { Icon(Icons.Default.Restore, stringResource(R.string.common_restore)) }
                            BouncyIconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showDeleteConfirmDialog = true
                                },
                                expandOnPress = 3.dp
                            ) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    stringResource(R.string.trash_delete_forever_cd),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            } else {
                TopAppBar(
                    windowInsets = WindowInsets(top = safeTopMargin),
                    title = { Text(stringResource(R.string.trash_title)) },
                    navigationIcon = {
                        // Pegado al borde izquierdo.
                        BouncyIconButton(
                            onClick = onNavigateBack,
                            expandOnPress = 3.dp
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        if (trashedNotes.isNotEmpty()) {
                            TextButton(onClick = { showEmptyTrashDialog = true }) {
                                Text(
                                    stringResource(R.string.trash_empty_button),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                )
            }
        }
    ) { innerPadding ->
        if (trashedNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.trash_empty_state),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(if (isLandscape) 3 else 2),
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp
            ) {
                items(trashedNotes, key = { it.id }) { note ->
                    val isSelected = selectedNotes.contains(note.id)
                    TrashedNoteCard(
                        note = note,
                        isSelected = isSelected,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!selectionMode) { selectionMode = true; selectedNotes = setOf(note.id) }
                        },
                        onClick = {
                            if (selectionMode) {
                                selectedNotes = if (isSelected) selectedNotes - note.id else selectedNotes + note.id
                                if (selectedNotes.isEmpty()) selectionMode = false
                            } else {
                                selectionMode = true
                                selectedNotes = setOf(note.id)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text(stringResource(R.string.trash_empty_dialog_title)) },
            text = { Text(stringResource(R.string.trash_empty_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    showEmptyTrashDialog = false
                }) {
                    Text(
                        stringResource(R.string.trash_empty_dialog_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.trash_delete_dialog_title)) },
            text = { Text(stringResource(R.string.trash_delete_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePermanentlyMultiple(selectedNotes)
                    showDeleteConfirmDialog = false
                    selectionMode = false
                    selectedNotes = emptySet()
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashedNoteCard(
    note: NoteEntity,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val minHeight = remember(note.id) { kotlin.random.Random(note.id).nextInt(140, 221).dp }
    val formatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val displayText = if (!note.summary.isNullOrEmpty()) note.summary else if (note.rawText.isNotBlank()) note.rawText else stringResource(R.string.trash_empty_note)

    val interactionSource = remember { MutableInteractionSource() }
    val cardScale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    cardScale.animateTo(0.97f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    cardScale.animateTo(1f, spring(0.40f, Spring.StiffnessMediumLow))
                }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .graphicsLayer {
                scaleX = cardScale.value
                scaleY = cardScale.value
            }
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            Text(displayText, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.weight(1f))
            Text(formatter.format(Date(note.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(top = 8.dp))
        }
    }
}