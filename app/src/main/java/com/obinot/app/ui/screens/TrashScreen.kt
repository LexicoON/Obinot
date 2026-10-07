package com.obinot.app.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obinot.app.R
import com.obinot.app.data.NoteEntity
import com.obinot.app.ui.components.BouncyButton
import com.obinot.app.ui.components.BouncyIconButton
import com.obinot.app.ui.components.observeBouncyPress
import com.obinot.app.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
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
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var selectedNotes by remember { mutableStateOf(setOf<Int>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (selectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        windowInsets = WindowInsets(top = safeTopMargin),
                        title = {
                            Text(
                                stringResource(R.string.trash_selected_count, selectedNotes.size),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            BouncyIconButton(
                                onClick = { selectionMode = false; selectedNotes = emptySet() },
                                expandOnPress = 3.dp
                            ) {
                                Icon(Icons.Default.Close, stringResource(R.string.common_cancel))
                            }
                        },
                        actions = {
                            BouncyIconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val idsToRestore = selectedNotes
                                    viewModel.restoreMultipleFromTrash(idsToRestore)
                                    selectionMode = false
                                    selectedNotes = emptySet()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = context.resources.getQuantityString(
                                                R.plurals.notes_restored,
                                                idsToRestore.size,
                                                idsToRestore.size
                                            ),
                                            duration = SnackbarDuration.Short
                                        )
                                    }
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
                    title = {
                        Text(
                            stringResource(R.string.trash_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        BouncyIconButton(
                            onClick = onNavigateBack,
                            expandOnPress = 3.dp
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        if (trashedNotes.isNotEmpty()) {
                            BouncyIconButton(
                                onClick = { showEmptyTrashDialog = true },
                                expandOnPress = 3.dp
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = stringResource(R.string.trash_empty_button),
                                    tint = MaterialTheme.colorScheme.error
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
            TrashEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Hero: contador de notas. No lo ocultamos en selection mode
                // porque da contexto útil (cuántas hay en total).
                TrashHero(
                    count = trashedNotes.size,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 8.dp)
                )

                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(if (isLandscape) 3 else 2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
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
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedNotes = setOf(note.id)
                                } else {
                                    selectedNotes = if (isSelected) selectedNotes - note.id else selectedNotes + note.id
                                    if (selectedNotes.isEmpty()) selectionMode = false
                                }
                            },
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
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
                BouncyButton(
                    onClick = {
                        val count = trashedNotes.size
                        viewModel.emptyTrash()
                        showEmptyTrashDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.resources.getQuantityString(
                                    R.plurals.notes_permanently_deleted,
                                    count,
                                    count
                                ),
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.trash_empty_dialog_confirm))
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
                BouncyButton(
                    onClick = {
                        val count = selectedNotes.size
                        viewModel.deletePermanentlyMultiple(selectedNotes)
                        showDeleteConfirmDialog = false
                        selectionMode = false
                        selectedNotes = emptySet()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.resources.getQuantityString(
                                    R.plurals.notes_permanently_deleted,
                                    count,
                                    count
                                ),
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.common_delete))
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

// ============================================================
// HERO — contador de notas
// ============================================================

/**
 * Fila compacta arriba del grid con el contador de notas en el trash.
 * Usa el mismo lenguaje visual que el hero de ResultScreen: chips
 * pequeños con ícono + texto. Le da contexto al usuario sin ocupar
 * espacio significativo.
 */
@Composable
private fun TrashHero(
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (count == 1) {
                        stringResource(R.string.trash_hero_one)
                    } else {
                        stringResource(R.string.trash_hero_many, count)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ============================================================
// EMPTY STATE
// ============================================================

/**
 * Empty state del trash: ícono grande circular + título + subtítulo.
 * Mismo lenguaje que los empty states de Settings/Result.
 */
@Composable
private fun TrashEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.trash_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.trash_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================
// TRASHED NOTE CARD
// ============================================================

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
        observeBouncyPress(interactionSource, cardScale, pressedScale = 0.97f)
    }

    val borderColor by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "borderAlpha"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (borderColor > 0.5f) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatter.format(Date(note.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = note.title.ifBlank { stringResource(R.string.trash_empty_note) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}