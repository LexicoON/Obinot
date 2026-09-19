package com.obinot.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obinot.app.R
import com.obinot.app.data.LabelEntity
import com.obinot.app.data.NoteEntity
import com.obinot.app.ui.components.BouncyButton
import com.obinot.app.ui.components.BouncyIconButton
import com.obinot.app.ui.components.BouncyToggleButton
import com.obinot.app.ui.components.MarkdownText
import com.obinot.app.ui.components.bouncyClickable
import com.obinot.app.ui.components.observeBouncyPress
import com.obinot.app.ui.theme.isDynamicLabelColor
import com.obinot.app.ui.theme.resolveLabelColors
import com.obinot.app.ui.theme.resolveLabelDotColor
import com.obinot.app.utils.ImportExportHelper
import com.obinot.app.viewmodel.HistoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Stable
class MagneticSwipeState {
    var activeId by mutableStateOf<Int?>(null)
    var dragX by mutableFloatStateOf(0f)
    var isDismissing by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onNoteClick: (Int) -> Unit,
    onTrashClick: () -> Unit,
    onImportFile: suspend (Uri) -> Int?,
    useNativePicker: Boolean = false
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val notes by viewModel.filteredNotes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val latestRelease by viewModel.latestRelease.collectAsState()
    val labelColors by viewModel.labelColors.collectAsState()

    val uniqueLabels by viewModel.uniqueLabels.collectAsState()
    val selectedLabels by viewModel.selectedLabels.collectAsState()
    val isMultiSelectLabelMode by viewModel.isMultiSelectLabelMode.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()

    var selectionMode by remember { mutableStateOf(false) }
    var selectedNotes by remember { mutableStateOf(setOf<Int>()) }
    var showSelectionMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNewLabelDialog by remember { mutableStateOf(false) }
    var newLabelInput by remember { mutableStateOf("") }
    var newLabelColor by remember { mutableStateOf(LabelEntity.DEFAULT_COLOR) }

    var labelBeingManaged by remember { mutableStateOf<String?>(null) }
    var renameLabelInput by remember { mutableStateOf("") }
    var renameLabelColor by remember { mutableStateOf(LabelEntity.DEFAULT_COLOR) }
    var showDeleteMultipleLabelsDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchFocused by remember { mutableStateOf(false) }

    val sharedPreferences = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var isGridView by remember { mutableStateOf(sharedPreferences.getBoolean("is_grid_view", true)) }

    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Mapa id → note para lookups O(1). Antes isAllPinned hacía notes.find{} en bucle,
    // lo que con 100 notas y 20 seleccionadas eran 2000 comparaciones por recomposición.
    val notesById = remember(notes) { notes.associateBy { it.id } }
    val isAllPinned = remember(selectedNotes, notesById) {
        selectedNotes.isNotEmpty() && selectedNotes.all { notesById[it]?.isPinned == true }
    }

    // filter() recorre la lista entera. Recordarlo evita recorrerla en cada recomposición
    // disparada por cualquier estado no relacionado (focus del search, sheet, etc).
    val pinnedNotes = remember(notes) { notes.filter { it.isPinned } }
    val unpinnedNotes = remember(notes) { notes.filter { !it.isPinned } }

    val gridState = rememberLazyStaggeredGridState()
    val isFabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

    val swipeState = remember { MagneticSwipeState() }
    val orderedNoteIds = remember(pinnedNotes, unpinnedNotes) {
        pinnedNotes.map { it.id } + unpinnedNotes.map { it.id }
    }

    // Lista estática de opciones de sort. Guardamos el resource ID del label para
    // que se resuelva en el momento de la composición (y respete cambios de locale).
    val sortOptions = remember {
        listOf(
            Icons.Default.AccessTime to R.string.sort_newest,
            Icons.Default.History to R.string.sort_oldest,
            Icons.AutoMirrored.Filled.Sort to R.string.history_sort_alpha
        )
    }

    val currentVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0" }
        catch (e: Exception) { "1.0.0" }
    }

    val isTransitioning = animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState

    LaunchedEffect(Unit) {
        viewModel.checkForAppUpdate(currentVersion)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_importing))
                val newId = onImportFile(it)
                if (newId != null) {
                    onNoteClick(newId)
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_import_failed))
                }
            }
        }
    }

    var showNativePickerSheet by remember { mutableStateOf(false) }

    fun launchImportPicker() {
        if (useNativePicker) {
            showNativePickerSheet = true
        } else {
            importLauncher.launch(arrayOf("*/*"))
        }
    }

    var isDragHovering by remember { mutableStateOf(false) }

    // Handler de drag & drop. Las URIs se obtienen exclusivamente del clipData,
    // que es la única fuente que expone la API de DragEvent.
    val dragAndDropCallback = remember(context, coroutineScope, snackbarHostState, onImportFile) {
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

                val uris = mutableListOf<Uri>()
                val clipData = androidEvent.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }

                if (uris.isEmpty()) {
                    permission?.release()
                    return false
                }

                var firstImportedId: Int? = null
                var successCount = 0
                var failCount = 0
                coroutineScope.launch {
                    for (uri in uris) {
                        val newId = onImportFile(uri)
                        if (newId != null) {
                            successCount++
                            if (firstImportedId == null) firstImportedId = newId
                        } else {
                            failCount++
                        }
                    }
                    permission?.release()
                    val msg = when {
                        failCount == 0 && successCount > 1 -> context.getString(R.string.record_imported_multiple, successCount)
                        failCount == 0 -> context.getString(R.string.record_imported_success)
                        successCount == 0 -> context.getString(R.string.record_imported_none)
                        else -> context.getString(R.string.record_imported_partial, successCount, failCount)
                    }
                    snackbarHostState.showSnackbar(msg)
                    if (firstImportedId != null && uris.size == 1) {
                        onNoteClick(firstImportedId)
                    }
                }
                return true
            }
        }
    }

    BackHandler(enabled = isSearchFocused || searchQuery.isNotEmpty() || selectionMode || drawerState.isOpen) {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (selectionMode) {
            selectionMode = false; selectedNotes = emptySet()
        } else if (isSearchFocused) {
            focusManager.clearFocus()
        } else {
            viewModel.updateSearchQuery("")
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isTransitioning,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.history_sort_by), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                    ) {
                        sortOptions.forEachIndexed { index, (icon, descriptionRes) ->
                            BouncyToggleButton(
                                checked = sortMode == index,
                                onCheckedChange = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setSortMode(index)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(icon, contentDescription = stringResource(descriptionRes), modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.history_labels_header),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (isMultiSelectLabelMode && selectedLabels.isNotEmpty()) {
                            BouncyIconButton(onClick = { showDeleteMultipleLabelsDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_labels_delete_cd), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        BouncyIconButton(onClick = { viewModel.setMultiSelectLabelMode(!isMultiSelectLabelMode) }) {
                            Icon(
                                Icons.Default.Checklist,
                                contentDescription = stringResource(R.string.history_labels_multiselect_cd),
                                tint = if (isMultiSelectLabelMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.history_all_notes)) },
                        selected = selectedLabels.isEmpty(),
                        onClick = {
                            viewModel.clearLabelFilter()
                            if (!isMultiSelectLabelMode) coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    uniqueLabels.forEach { label ->
                        val isLabelSelected = label in selectedLabels
                        val assignedHex = labelColors[label]
                        val dotColor = resolveLabelDotColor(assignedHex)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    if (isLabelSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = {
                                        viewModel.toggleLabelFilter(label)
                                        if (!isMultiSelectLabelMode) coroutineScope.launch { drawerState.close() }
                                    },
                                    onLongClick = {
                                        labelBeingManaged = label
                                        renameLabelInput = label
                                        renameLabelColor = assignedHex ?: LabelEntity.DEFAULT_COLOR
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            if (isMultiSelectLabelMode) {
                                Checkbox(
                                    checked = isLabelSelected,
                                    onCheckedChange = { viewModel.toggleLabelFilter(label) }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                label,
                                color = if (isLabelSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isMultiSelectLabelMode) {
                                BouncyIconButton(
                                    onClick = {
                                        labelBeingManaged = label
                                        renameLabelInput = label
                                        renameLabelColor = assignedHex ?: LabelEntity.DEFAULT_COLOR
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.history_label_edit_cd),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.history_create_label)) },
                        icon = { Icon(Icons.Default.Add, null) },
                        selected = false,
                        onClick = {
                            newLabelInput = ""
                            newLabelColor = LabelEntity.DEFAULT_COLOR
                            showNewLabelDialog = true
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.trash_title), color = MaterialTheme.colorScheme.error) },
                        icon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            onTrashClick()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (selectionMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout)
                    ) {
                        TopAppBar(
                            title = { Text(stringResource(R.string.history_selected_count, selectedNotes.size)) },
                            navigationIcon = {
                                BouncyIconButton(onClick = { selectionMode = false; selectedNotes = emptySet() }) {
                                    Icon(Icons.Default.Close, stringResource(R.string.common_cancel))
                                }
                            },
                            actions = {
                                BouncyIconButton(onClick = { showSelectionMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_options_cd))
                                }

                                SelectionDropdownMenu(
                                    expanded = showSelectionMenu,
                                    onDismissRequest = { showSelectionMenu = false },
                                    isAllPinned = isAllPinned,
                                    selectedCount = selectedNotes.size,
                                    onSelectAll = {
                                        selectedNotes = notes.map { it.id }.toSet()
                                        showSelectionMenu = false
                                    },
                                    onTogglePin = {
                                        viewModel.togglePinMultiple(selectedNotes, !isAllPinned)
                                        selectionMode = false
                                        selectedNotes = emptySet()
                                        showSelectionMenu = false
                                    },
                                    onClone = {
                                        viewModel.cloneMultiple(selectedNotes)
                                        selectionMode = false
                                        selectedNotes = emptySet()
                                        showSelectionMenu = false
                                    },
                                    onShare = {
                                        val noteId = selectedNotes.firstOrNull()
                                        val noteToShare = noteId?.let { id -> notesById[id] }
                                        if (noteToShare != null) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_generating_binot))
                                                val uri = ImportExportHelper.exportNoteToBinot(context, noteToShare, labelColors)
                                                if (uri != null) {
                                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/zip"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text_binot, noteToShare.title))
                                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    }
                                                    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_chooser_title)))
                                                } else {
                                                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_generate_failed))
                                                }
                                            }
                                        }
                                        selectionMode = false
                                        selectedNotes = emptySet()
                                        showSelectionMenu = false
                                    },
                                    onDelete = {
                                        showDeleteDialog = true
                                        showSelectionMenu = false
                                    }
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    }
                } else {
                    with(animatedVisibilityScope) {
                        MorphingSearchBar(
                            query = searchQuery,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            isFocused = isSearchFocused,
                            onFocusChange = { isSearchFocused = it },
                            onClearFocus = { focusManager.clearFocus() },
                            onMenuClick = {
                                if (!isTransitioning) {
                                    coroutineScope.launch { drawerState.open() }
                                }
                            },
                            isGridView = isGridView,
                            onToggleViewClick = {
                                isGridView = !isGridView
                                sharedPreferences.edit().putBoolean("is_grid_view", isGridView).apply()
                            },
                            modifier = Modifier.animateEnterExit(
                                enter = scaleIn(initialScale = 0.9f, animationSpec = tween(300)) + fadeIn(tween(300)),
                                exit = scaleOut(targetScale = 0.9f, animationSpec = tween(300)) + fadeOut(tween(300))
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!selectionMode) {
                    with(sharedTransitionScope) {
                        val corner by animateDpAsState(
                            targetValue = if (isFabExpanded) 28.dp else 16.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "fabCorner"
                        )
                        val fabInteractionSource = remember { MutableInteractionSource() }
                        val fabScale = remember { Animatable(1f) }
                        LaunchedEffect(fabInteractionSource) {
                            observeBouncyPress(fabInteractionSource, fabScale, pressedScale = 0.92f)
                        }

                        FloatingActionButton(
                            onClick = { launchImportPicker() },
                            shape = RoundedCornerShape(corner),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                focusedElevation = 0.dp,
                                hoveredElevation = 0.dp
                            ),
                            interactionSource = fabInteractionSource,
                            modifier = Modifier
                                .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                                .alpha(if (animatedVisibilityScope.transition.targetState == EnterExitState.Visible) 1f else 0f)
                                .graphicsLayer {
                                    scaleX = fabScale.value
                                    scaleY = fabScale.value
                                }
                                .then(
                                    with(animatedVisibilityScope) {
                                        Modifier.animateEnterExit(
                                            enter = scaleIn(
                                                initialScale = 0f,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                            ),
                                            exit = scaleOut(targetScale = 0f, animationSpec = tween(300))
                                        )
                                    }
                                )
                                .animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    alignment = Alignment.Center
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = if (isFabExpanded) 20.dp else 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Audiotrack, contentDescription = stringResource(R.string.history_fab_import))
                                AnimatedVisibility(
                                    visible = isFabExpanded,
                                    enter = expandHorizontally(expandFrom = Alignment.Start, animationSpec = spring()) + fadeIn(animationSpec = spring()),
                                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = spring()) + fadeOut(animationSpec = spring())
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(stringResource(R.string.history_fab_import), style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { event ->
                            val types = event.mimeTypes()
                            types.isEmpty() ||
                            types.any { mimeType ->
                                mimeType.startsWith("audio/") ||
                                mimeType == "application/zip" ||
                                mimeType == "application/octet-stream" ||
                                mimeType.startsWith("application/") ||
                                mimeType == "*/*"
                            }
                        },
                        target = dragAndDropCallback
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (notes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (searchQuery.isNotEmpty() || selectedLabels.isNotEmpty()) stringResource(R.string.history_no_results) else stringResource(R.string.history_no_notes),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        with(animatedVisibilityScope) {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(if (isGridView) 2 else 1),
                                state = gridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .animateEnterExit(
                                        enter = slideInVertically(initialOffsetY = { 100 }, animationSpec = tween(300)) + fadeIn(tween(300)),
                                        exit = slideOutVertically(targetOffsetY = { 100 }, animationSpec = tween(300)) + fadeOut(tween(300))
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalItemSpacing = 8.dp
                            ) {
                                if (pinnedNotes.isNotEmpty()) {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        Text(stringResource(R.string.history_section_pinned), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp))
                                    }
                                    staggeredItems(pinnedNotes, key = { it.id }) { note ->
                                        DismissibleNoteCard(
                                            note = note,
                                            modifier = Modifier.animateItem(),
                                            isSelected = selectedNotes.contains(note.id),
                                            selectedLabels = selectedLabels,
                                            labelColors = labelColors,
                                            selectionMode = selectionMode,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            viewModel = viewModel,
                                            parentScope = coroutineScope,
                                            snackbarHostState = snackbarHostState,
                                            swipeState = swipeState,
                                            orderedNoteIds = orderedNoteIds,
                                            onSelect = {
                                                if (selectionMode) {
                                                    selectedNotes = if (selectedNotes.contains(note.id)) selectedNotes - note.id else selectedNotes + note.id
                                                    if (selectedNotes.isEmpty()) selectionMode = false
                                                } else { onNoteClick(note.id) }
                                            },
                                            onLongSelect = { if (!selectionMode) { selectionMode = true; selectedNotes = setOf(note.id) } }
                                        )
                                    }
                                }

                                if (unpinnedNotes.isNotEmpty()) {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        Text(stringResource(R.string.history_section_collection), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp))
                                    }
                                    staggeredItems(unpinnedNotes, key = { it.id }) { note ->
                                        DismissibleNoteCard(
                                            note = note,
                                            modifier = Modifier.animateItem(),
                                            isSelected = selectedNotes.contains(note.id),
                                            selectedLabels = selectedLabels,
                                            labelColors = labelColors,
                                            selectionMode = selectionMode,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            viewModel = viewModel,
                                            parentScope = coroutineScope,
                                            snackbarHostState = snackbarHostState,
                                            swipeState = swipeState,
                                            orderedNoteIds = orderedNoteIds,
                                            onSelect = {
                                                if (selectionMode) {
                                                    selectedNotes = if (selectedNotes.contains(note.id)) selectedNotes - note.id else selectedNotes + note.id
                                                    if (selectedNotes.isEmpty()) selectionMode = false
                                                } else { onNoteClick(note.id) }
                                            },
                                            onLongSelect = { if (!selectionMode) { selectionMode = true; selectedNotes = setOf(note.id) } }
                                        )
                                    }
                                }
                                item(span = StaggeredGridItemSpan.FullLine) { Spacer(modifier = Modifier.height(100.dp)) }
                            }
                        }
                    }
                }

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
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(20.dp)
                                )
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
                                stringResource(R.string.history_drop_to_import),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.history_drop_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNativePickerSheet) {
        com.obinot.app.ui.components.ObinotFilePickerSheet(
            onDismiss = { showNativePickerSheet = false },
            onFileSelected = { uri ->
                showNativePickerSheet = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_importing))
                    val newId = onImportFile(uri)
                    if (newId != null) {
                        onNoteClick(newId)
                    } else {
                        snackbarHostState.showSnackbar(context.getString(R.string.snackbar_import_failed))
                    }
                }
            }
        )
    }

    if (showNewLabelDialog) {
        AlertDialog(
            onDismissRequest = { showNewLabelDialog = false },
            title = { Text(stringResource(R.string.history_create_label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = newLabelInput,
                        onValueChange = { newLabelInput = it },
                        label = { Text(stringResource(R.string.history_label_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.history_label_color), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    LabelColorPicker(
                        selectedHex = newLabelColor,
                        onSelect = { newLabelColor = it }
                    )
                }
            },
            confirmButton = {
                BouncyButton(
                    onClick = {
                        if (newLabelInput.isNotBlank()) {
                            viewModel.createIndependentLabel(newLabelInput.trim(), newLabelColor)
                            showNewLabelDialog = false
                            newLabelInput = ""
                            newLabelColor = LabelEntity.DEFAULT_COLOR
                        }
                    }
                ) { Text(stringResource(R.string.history_create)) }
            },
            dismissButton = { TextButton(onClick = { showNewLabelDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    if (labelBeingManaged != null) {
        AlertDialog(
            onDismissRequest = { labelBeingManaged = null },
            title = { Text(stringResource(R.string.history_edit_label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = renameLabelInput,
                        onValueChange = { renameLabelInput = it },
                        label = { Text(stringResource(R.string.history_label_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.history_label_color), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    LabelColorPicker(
                        selectedHex = renameLabelColor,
                        onSelect = { renameLabelColor = it }
                    )
                    TextButton(
                        onClick = {
                            labelBeingManaged?.let { viewModel.deleteLabel(it) }
                            labelBeingManaged = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.history_delete_label))
                    }
                }
            },
            confirmButton = {
                BouncyButton(
                    onClick = {
                        val oldLabel = labelBeingManaged
                        if (oldLabel != null) {
                            if (renameLabelInput.isNotBlank() && renameLabelInput.trim() != oldLabel) {
                                viewModel.renameLabel(oldLabel, renameLabelInput.trim())
                                viewModel.setLabelColor(renameLabelInput.trim(), renameLabelColor)
                            } else {
                                viewModel.setLabelColor(oldLabel, renameLabelColor)
                            }
                        }
                        labelBeingManaged = null
                    }
                ) { Text(stringResource(R.string.history_save)) }
            },
            dismissButton = {
                TextButton(onClick = { labelBeingManaged = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showDeleteMultipleLabelsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMultipleLabelsDialog = false },
            title = { Text(stringResource(R.string.history_delete_labels_title)) },
            text = { Text(stringResource(R.string.history_delete_labels_body, selectedLabels.size)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMultipleLabels(selectedLabels)
                    showDeleteMultipleLabelsDialog = false
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMultipleLabelsDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.history_delete_notes_title)) },
            text = { Text(stringResource(R.string.history_delete_notes_body, selectedNotes.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val idsToDelete = selectedNotes
                    viewModel.deleteMultiple(idsToDelete)
                    showDeleteDialog = false
                    selectionMode = false
                    selectedNotes = emptySet()
                    coroutineScope.launch {
                        val msg = context.resources.getQuantityString(
                            R.plurals.notes_moved_to_trash,
                            idsToDelete.size,
                            idsToDelete.size
                        )
                        val result = snackbarHostState.showSnackbar(
                            message = msg,
                            actionLabel = context.getString(R.string.common_undo),
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoDelete()
                        } else {
                            viewModel.clearRecentlyDeleted()
                        }
                    }
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    if (latestRelease != null) {
        val scrollWall = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
            }
        }
        val updateScrollState = rememberScrollState()

        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissUpdateNotification() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NewReleases, contentDescription = stringResource(R.string.update_icon_cd), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.update_available_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.update_version_ready, latestRelease!!.tag_name), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .nestedScroll(scrollWall)
                ) {
                    MarkdownText(
                        text = latestRelease!!.body ?: stringResource(R.string.update_default_body),
                        scrollState = updateScrollState,
                        highlightsInfo = null,
                        onSavedHighlightClick = { _, _, _, _, _ -> },
                        onResolveSelection = { null },
                        highlightQuery = "",
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { val apkUrl = latestRelease!!.assets?.firstOrNull()?.browser_download_url ?: latestRelease!!.html_url; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))); viewModel.dismissUpdateNotification() }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(stringResource(R.string.update_download_button)) }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latestRelease!!.html_url))); viewModel.dismissUpdateNotification() }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(stringResource(R.string.update_view_github)) }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SelectionDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isAllPinned: Boolean,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onTogglePin: () -> Unit,
    onClone: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.select_all)) },
            leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
            onClick = onSelectAll
        )
        DropdownMenuItem(
            text = { Text(if (isAllPinned) stringResource(R.string.unpin) else stringResource(R.string.pin)) },
            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
            onClick = onTogglePin
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.clone)) },
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            onClick = onClone
        )
        if (selectedCount == 1) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = onShare
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = onDelete
        )
    }
}

@Composable
private fun LabelColorPicker(
    selectedHex: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(LabelEntity.FULL_PALETTE) { hex ->
            val dynamic = isDynamicLabelColor(hex)
            val color = if (dynamic) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                try { Color(AndroidColor.parseColor(hex)) } catch (e: Exception) { Color.Gray }
            }
            val isSelected = if (dynamic) isDynamicLabelColor(selectedHex)
                             else selectedHex.equals(hex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.common_selected_cd),
                        tint = if (color.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DismissibleNoteCard(
    note: NoteEntity,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    selectedLabels: Set<String>,
    labelColors: Map<String, String>,
    selectionMode: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: HistoryViewModel,
    parentScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    swipeState: MagneticSwipeState,
    orderedNoteIds: List<Int>,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { 380.dp.toPx() }
    val thresholdPx = with(density) { 110.dp.toPx() }

    var localOffsetX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    val isActive = swipeState.activeId == note.id

    val myIndex = remember(orderedNoteIds, note.id) { orderedNoteIds.indexOf(note.id) }
    val activeIndex = remember(orderedNoteIds, swipeState.activeId) {
        swipeState.activeId?.let { orderedNoteIds.indexOf(it) } ?: -1
    }
    val distance = if (myIndex == -1 || activeIndex == -1) 0 else abs(myIndex - activeIndex)

    val neighborFactor = when {
        isActive -> 1f
        distance == 0 -> 0f
        else -> (1f / (distance.toFloat() * distance.toFloat())) * 0.30f
    }

    val neighborTarget = swipeState.dragX * neighborFactor
    val animatedNeighborOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = neighborTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "neighborOffset"
    )

    val displayOffset = if (isActive) localOffsetX else animatedNeighborOffset

    val dragProgress = (abs(displayOffset) / thresholdPx).coerceIn(0f, 1f)
    val deleteColor by animateColorAsState(
        targetValue = if (dragProgress > 0f && isActive)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = dragProgress)
        else Color.Transparent,
        label = "deleteColor"
    )
    val iconScale = 0.65f + 0.35f * dragProgress
    val alignment = if (displayOffset > 0) Alignment.CenterStart else Alignment.CenterEnd

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(deleteColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp),
            contentAlignment = alignment
        ) {
            if (dragProgress > 0.05f && isActive) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.scale(iconScale)
                )
            }
        }

        with(sharedTransitionScope) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(displayOffset.roundToInt(), 0) }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        enabled = !selectionMode,
                        state = rememberDraggableState { delta ->
                            if (swipeState.activeId != note.id) {
                                swipeState.activeId = note.id
                            }
                            val progress = (abs(localOffsetX) / maxOffsetPx).coerceIn(0f, 1f)
                            val resistance = 1f - progress * progress * 0.85f
                            localOffsetX = (localOffsetX + delta * resistance)
                                .coerceIn(-maxOffsetPx, maxOffsetPx)
                            swipeState.dragX = localOffsetX
                        },
                        onDragStopped = { velocity ->
                            val currentOffset = localOffsetX
                            val shouldDismiss = abs(currentOffset) > thresholdPx || abs(velocity) > 800f
                            if (shouldDismiss) {
                                val target = if (currentOffset > 0) maxOffsetPx * 1.6f else -maxOffsetPx * 1.6f
                                scope.launch {
                                    animate(
                                        initialValue = currentOffset,
                                        targetValue = target,
                                        animationSpec = spring(
                                            dampingRatio = 0.70f,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    ) { value, _ ->
                                        localOffsetX = value
                                        swipeState.dragX = value
                                    }

                                    swipeState.activeId = null
                                    swipeState.dragX = 0f

                                    viewModel.deleteMultiple(setOf(note.id))
                                    parentScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.snackbar_note_moved_to_trash),
                                            actionLabel = context.getString(R.string.common_undo),
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.undoDelete()
                                        } else {
                                            viewModel.clearRecentlyDeleted()
                                        }
                                    }
                                }
                            } else {
                                scope.launch {
                                    animate(
                                        initialValue = currentOffset,
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.30f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ) { value, _ ->
                                        localOffsetX = value
                                        swipeState.dragX = value
                                    }
                                    swipeState.activeId = null
                                    swipeState.dragX = 0f
                                }
                            }
                        }
                    )
            ) {
                NoteCard(
                    note = note,
                    isSelected = isSelected,
                    selectedLabels = selectedLabels,
                    labelColors = labelColors,
                    modifier = Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState("note-${note.id}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                        boundsTransform = { _, _ -> tween(300) }
                    ),
                    onLongClick = onLongSelect,
                    onClick = onSelect,
                    onLabelClick = { label -> viewModel.toggleLabelFilter(label) }
                )
            }
        }
    }
}

@Composable
fun MorphingSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onClearFocus: () -> Unit,
    onMenuClick: () -> Unit,
    isGridView: Boolean,
    onToggleViewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets

    val cornerRadius by animateDpAsState(targetValue = if (isFocused) 0.dp else 50.dp, animationSpec = spring(), label = "corner")
    val topMargin by animateDpAsState(targetValue = if (isFocused) 0.dp else safeTopMargin + 8.dp, animationSpec = spring(), label = "tMargin")
    val innerTopPadding by animateDpAsState(targetValue = if (isFocused) safeTopMargin + 16.dp else 12.dp, animationSpec = spring(), label = "innerTopPad")
    val outerHorizontalPadding by animateDpAsState(targetValue = if (isFocused) 0.dp else 8.dp, animationSpec = spring(), label = "outerHPad")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topMargin, bottom = 8.dp)
            .padding(horizontal = outerHorizontalPadding)
    ) {
        AnimatedVisibility(
            visible = !isFocused,
            enter = expandHorizontally(animationSpec = spring()) + fadeIn(animationSpec = spring()),
            exit = shrinkHorizontally(animationSpec = spring()) + fadeOut(animationSpec = spring())
        ) {
            BouncyIconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, stringResource(R.string.history_menu_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = innerTopPadding, bottom = 12.dp)
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Default.Search, stringResource(R.string.common_search), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) { Text(stringResource(R.string.history_search_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
                    BasicTextField(
                        value = query, onValueChange = onQueryChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), singleLine = true,
                        modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) }
                    )
                }
                if (isFocused || query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.history_close_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onQueryChange(""); onClearFocus() }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isFocused,
            enter = expandHorizontally(animationSpec = spring()) + fadeIn(animationSpec = spring()),
            exit = shrinkHorizontally(animationSpec = spring()) + fadeOut(animationSpec = spring())
        ) {
            BouncyIconButton(onClick = onToggleViewClick) {
                Icon(
                    imageVector = if (isGridView) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                    contentDescription = stringResource(R.string.history_toggle_view_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    isSelected: Boolean = false,
    selectedLabels: Set<String> = emptySet(),
    labelColors: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
    onLabelClick: (String) -> Unit = {}
) {
    val formatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val rawDisplayText = if (!note.summary.isNullOrEmpty()) note.summary else if (note.rawText.isNotBlank()) note.rawText else null

    val interactionSource = remember { MutableInteractionSource() }
    val cardScale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        observeBouncyPress(interactionSource, cardScale, pressedScale = 0.97f)
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
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!note.label.isNullOrBlank()) {
                val labels = note.label.split("|").map { it.trim() }.filter { it.isNotBlank() }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    labels.forEach { label ->
                        val isLabelActive = label in selectedLabels
                        val assignedHex = labelColors[label]

                        val (chipColor, chipContentColor) = resolveLabelColors(assignedHex, isLabelActive)

                        Box(
                            modifier = Modifier
                                .background(chipColor, RoundedCornerShape(50))
                                .clickable { onLabelClick(label) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.Label, null, tint = chipContentColor, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = chipContentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (note.title.isNotBlank()) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (rawDisplayText != null) {
                CardMarkdownPreview(
                    text = rawDisplayText,
                    maxLines = 7,
                    modifier = Modifier.weight(1f, fill = false)
                )
            } else {
                Text(
                    text = stringResource(R.string.history_waiting_ai),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatter.format(Date(note.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun CardMarkdownPreview(
    text: String,
    maxLines: Int = 7,
    modifier: Modifier = Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface

    val cleaned = remember(text) {
        var t = text
        t = t.replace(Regex("<!--BINOT_META:.*?-->"), "")
        t = t.replace(Regex("```mermaid[\\s\\S]*?```", RegexOption.MULTILINE), "")
        t = t.replace(Regex("\\$\\$[\\s\\S]*?\\$\\$", RegexOption.MULTILINE), "")
        t = t.replace(Regex("""\$[^$\n]+?\$"""), "")
        t = t.replace(Regex("^> ?", RegexOption.MULTILINE), "")
        t.trim()
    }

    val previewLines = remember(cleaned) {
        cleaned.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(maxLines)
    }

    val annotated = buildAnnotatedString {
        previewLines.forEachIndexed { i, line ->
            when {
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontSize = 11.sp, color = onSurface.copy(alpha = 0.85f))) {
                        appendInlineMarkdown(line.removePrefix("# "))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontSize = 11.sp, color = onSurface.copy(alpha = 0.75f))) {
                        appendInlineMarkdown(line.removePrefix("## "))
                    }
                }
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontSize = 11.sp, color = onSurface.copy(alpha = 0.65f))) {
                        appendInlineMarkdown(line.removePrefix("### "))
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    withStyle(SpanStyle(color = onSurface.copy(alpha = 0.7f), fontSize = 11.sp)) {
                        append("• ")
                        appendInlineMarkdown(line.drop(2))
                    }
                }
                line.matches(Regex("^\\d+\\. .*")) -> {
                    withStyle(SpanStyle(color = onSurface.copy(alpha = 0.7f), fontSize = 11.sp)) {
                        appendInlineMarkdown(line.replace(Regex("^\\d+\\. "), ""))
                    }
                }
                else -> {
                    withStyle(SpanStyle(color = onSurface.copy(alpha = 0.7f), fontSize = 11.sp)) {
                        appendInlineMarkdown(line)
                    }
                }
            }
            if (i < previewLines.lastIndex) append("\n")
        }
    }

    Text(
        text = annotated,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 17.sp,
        modifier = modifier
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    val pattern = Regex("\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|_(.*?)_")
    var cursor = 0
    for (match in pattern.findAll(text)) {
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        when {
            match.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groups[1]!!.value) }
            match.groups[2] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groups[2]!!.value) }
            match.groups[3] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groups[3]!!.value) }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}