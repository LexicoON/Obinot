@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.obinot.app.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.obinot.app.R
import com.obinot.app.ui.components.AppLanguage
import com.obinot.app.ui.components.BouncyButton
import com.obinot.app.ui.components.bouncyClickable
import com.obinot.app.ui.components.BouncyIconButton
import com.obinot.app.ui.components.displayLabel
import com.obinot.app.ui.components.BouncyOutlinedButton
import com.obinot.app.ui.components.BouncyToggleButton
import com.obinot.app.ui.components.bouncyClickable
import com.obinot.app.viewmodel.SettingsViewModel
import com.obinot.app.viewmodel.UpdateState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
// Reusable pieces
// ============================================================

@Composable
private fun ExpressiveToggleGroup(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    labels: List<String>,
    icons: List<ImageVector>,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = selectedIndex == index
            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.18f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "toggleScale_$index"
            )
            BouncyToggleButton(
                checked = isSelected,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(index)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.Check else icons[index],
                    contentDescription = label,
                    modifier = Modifier
                        .size(18.dp)
                        .scale(iconScale)
                )
            }
        }
    }
}

@Composable
private fun TextToggleGroup(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        labels.forEachIndexed { index, label ->
            BouncyToggleButton(
                checked = selectedIndex == index,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(index)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Fila clickeable que muestra un valor actual y abre un selector.
 * Usada dentro de Apariencia (App Language, Color Palette) y IA (Output Language).
 */
@Composable
private fun SettingsSelectorRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .bouncyClickable(pressedScale = 0.97f) { onClick() }
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSelectionSheet(
    title: String,
    options: List<String>,
    selected: String,
    searchable: Boolean = true,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = { onDismiss(); query = "" },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).padding(horizontal = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            if (searchable) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.settings_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
            }
            val filtered = if (searchable) options.filter { it.contains(query, ignoreCase = true) } else options
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(filtered) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(option)
                                query = ""
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = option, style = MaterialTheme.typography.bodyLarge)
                        if (option == selected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun BetaBadge() {
    BadgeChip(
        text = "BETA",
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    )
}

@Composable
private fun AlphaBadge() {
    BadgeChip(
        text = "ALPHA",
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
}

/**
 * Editor de lista de API keys para un provider.
 *
 * Comportamiento según [rotationEnabled]:
 *   - OFF: muestra solo el primer campo (comportamiento previo a 2.2).
 *          La lista completa se preserva internamente para no perder las
 *          keys extra al guardar.
 *   - ON:  muestra todos los campos, con botón [x] por campo (excepto
 *          cuando queda 1 solo) y botón [+ Add key] mientras no se alcance
 *          [maxKeys].
 *
 * El primer campo es siempre la "primary key": el router la intenta
 * primero y solo pasa a las siguientes si tira 429.
 */
@Composable
private fun KeyListEditor(
    keys: List<String>,
    onKeysChange: (List<String>) -> Unit,
    labelPrimaryRes: Int,
    labelBackupRes: Int,
    rotationEnabled: Boolean,
    maxKeys: Int,
    onDirty: () -> Unit
) {
    val visibleKeys = if (rotationEnabled) keys else listOf(keys.firstOrNull().orEmpty())

    Column {
        visibleKeys.forEachIndexed { visibleIdx, keyValue ->
            val actualIndex = if (rotationEnabled) visibleIdx else 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = keyValue,
                    onValueChange = { newValue ->
                        val newList = keys.toMutableList()
                        while (newList.size <= actualIndex) newList.add("")
                        newList[actualIndex] = newValue
                        onKeysChange(newList)
                        onDirty()
                    },
                    label = {
                        Text(
                            if (actualIndex == 0) stringResource(labelPrimaryRes)
                            else stringResource(labelBackupRes, actualIndex + 1)
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
                if (rotationEnabled && keys.size > 1) {
                    Spacer(Modifier.width(4.dp))
                    BouncyIconButton(
                        onClick = {
                            val newList = keys.toMutableList().also { it.removeAt(actualIndex) }
                            onKeysChange(newList.ifEmpty { listOf("") })
                            onDirty()
                        }
                    ) {
                        Icon(
                            Icons.Default.RemoveCircleOutline,
                            contentDescription = stringResource(R.string.settings_rotation_key_remove_cd),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            if (visibleIdx < visibleKeys.lastIndex) Spacer(Modifier.height(8.dp))
        }

        if (rotationEnabled && keys.size < maxKeys) {
            Spacer(Modifier.height(10.dp))
            BouncyOutlinedButton(
                onClick = {
                    onKeysChange(keys + "")
                    onDirty()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_rotation_key_add))
            }
        }

        if (rotationEnabled && keys.size >= maxKeys) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_rotation_key_limit, maxKeys),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Tipo de badge que puede llevar una fila.
 */
private enum class FeatureBadge { NONE, BETA, ALPHA }

/**
 * Fila de switch para dentro de las secciones (Grabación, IA, Advanced).
 * Sin `maxLines = 1` en el título: dejamos 2 líneas para que no se corte.
 */
@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: FeatureBadge = FeatureBadge.NONE
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (badge != FeatureBadge.NONE) {
                    Spacer(modifier = Modifier.width(8.dp))
                    when (badge) {
                        FeatureBadge.BETA -> BetaBadge()
                        FeatureBadge.ALPHA -> AlphaBadge()
                        FeatureBadge.NONE -> { /* no badge */ }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Divider consistente entre filas dentro de una misma sección.
 */
@Composable
private fun SectionRowDivider() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    Spacer(modifier = Modifier.height(16.dp))
}

/**
 * Sección colapsable de Settings.
 *
 * Header con icono + título + subtítulo + chevron rotativo. El contenido
 * se muestra con AnimatedVisibility cuando está expandida.
 */
@Composable
private fun CollapsableSettingsSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "sectionArrow"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { rotationZ = arrowRotation }
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(20.dp))
                    content()
                }
            }
        }
    }
}

// ============================================================
// Main composable
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isRecording: Boolean = false,
    onDiscardRecording: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val userName by viewModel.userName.collectAsState()
    val geminiApiKey by viewModel.apiKey.collectAsState()
    val groqApiKey by viewModel.groqApiKey.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val recordMode by viewModel.recordMode.collectAsState()
    val aiProvider by viewModel.aiProvider.collectAsState()
    val backgroundRecordingEnabled by viewModel.backgroundRecordingEnabled.collectAsState()
    val liveTranscriptEnabled by viewModel.liveTranscriptEnabled.collectAsState()
    val autoCompressionMode by viewModel.autoCompressionMode.collectAsState()
    val nativePickerEnabled by viewModel.nativePickerEnabled.collectAsState()
    val colorStyle by viewModel.colorStyle.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val autoProcessEnabled by viewModel.autoProcessEnabled.collectAsState()
    val geminiApiKeys by viewModel.geminiApiKeys.collectAsState()
    val groqApiKeys by viewModel.groqApiKeys.collectAsState()
    val keyRotationEnabled by viewModel.keyRotationEnabled.collectAsState()
    val alphaUnlocked by viewModel.alphaUnlocked.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* If denied, toggle still works; OS just won't show notification. */ }

    val aiLanguage by viewModel.aiLanguage.collectAsState()
    val aiTask by viewModel.aiTask.collectAsState()
    val aiFormat by viewModel.aiFormat.collectAsState()

    var tempAiLanguage by remember(aiLanguage) { mutableStateOf(aiLanguage) }
    var tempAiTask by remember(aiTask) { mutableStateOf(aiTask) }
    var tempAiFormat by remember(aiFormat) { mutableStateOf(aiFormat) }
    var tempAiProvider by remember(aiProvider) { mutableStateOf(aiProvider) }

    val updateState by viewModel.updateState.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val latestVersionStr by viewModel.latestVersionStr.collectAsState()

    var nameInput by remember(userName) { mutableStateOf(userName) }
    // Inputs de las API keys. Cada uno es una lista: cuando rotation está
    // OFF, solo se renderiza el primero, pero la lista completa se preserva
    // acá para no perder las keys extra al guardar.
    var geminiKeyInputs by remember(geminiApiKeys) {
        mutableStateOf(geminiApiKeys.ifEmpty { listOf("") })
    }
    var groqKeyInputs by remember(groqApiKeys) {
        mutableStateOf(groqApiKeys.ifEmpty { listOf("") })
    }

    var isNameDirty by remember { mutableStateOf(false) }
    var isGeminiKeyDirty by remember { mutableStateOf(false) }
    var isGroqKeyDirty by remember { mutableStateOf(false) }

    var showInfoDialog by remember { mutableStateOf(false) }
    var showAiInfoDialog by remember { mutableStateOf(false) }
    var showTaskInfoDialog by remember { mutableStateOf(false) }
    var showFormatInfoDialog by remember { mutableStateOf(false) }
    var showCompressionInfoDialog by remember { mutableStateOf(false) }
    var showColorInfoDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var showBackupInfoDialog by remember { mutableStateOf(false) }
    var pendingModeSelection by remember { mutableStateOf(-1) }
    var showApplyAllDialog by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showColorPaletteSheet by remember { mutableStateOf(false) }
    var showAppLanguageSheet by remember { mutableStateOf(false) }

    val supportedLanguages = remember {
        listOf(
            "English", "Indonesia", "Spanish", "French", "German", "Chinese (Simplified)",
            "Chinese (Traditional)", "Japanese", "Korean", "Arabic", "Russian", "Portuguese",
            "Italian", "Hindi", "Bengali", "Urdu", "Turkish", "Vietnamese", "Thai",
            "Dutch", "Polish", "Swedish", "Malay"
        ).sorted()
    }

    val supportedAppLanguages = remember { AppLanguage.entries.toList() }
    val currentAppLanguage = remember(appLanguage) { AppLanguage.fromCode(appLanguage) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    val currentVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0" } catch (e: Exception) { "1.0.0" }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportBackup(context, it) { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } } }
    }

    val exportLegacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportBackupLegacy(context, it) { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } } }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackup(context, it) { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } } }
    }

    // ============================================================
    // Dialogs
    // ============================================================

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.mode_info_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_dialog_fast_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_fast_body), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Text(stringResource(R.string.settings_dialog_accurate_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_accurate_body), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text(stringResource(R.string.settings_dialog_got_it)) } }
        )
    }

    if (showAiInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAiInfoDialog = false },
            title = { Text(stringResource(R.string.settings_dialog_providers)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_dialog_gemini_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_gemini_body), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Text(stringResource(R.string.settings_dialog_groq_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_groq_body), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Text(stringResource(R.string.settings_dialog_dynamic_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_dynamic_body), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showAiInfoDialog = false }) { Text(stringResource(R.string.settings_dialog_got_it)) } }
        )
    }

    if (showTaskInfoDialog) {
        AlertDialog(
            onDismissRequest = { showTaskInfoDialog = false },
            title = { Text(stringResource(R.string.settings_dialog_tasks)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_dialog_tidy_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_tidy_body), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Text(stringResource(R.string.settings_dialog_summary_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_summary_body), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Text(stringResource(R.string.settings_dialog_analyze_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_analyze_body), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showTaskInfoDialog = false }) { Text(stringResource(R.string.settings_dialog_got_it)) } }
        )
    }

    if (showFormatInfoDialog) {
        AlertDialog(
            onDismissRequest = { showFormatInfoDialog = false },
            title = { Text(stringResource(R.string.settings_dialog_formats)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_dialog_paragraphs_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_paragraphs_body), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Text(stringResource(R.string.settings_dialog_bullets_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_bullets_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_dialog_formats_tip),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showFormatInfoDialog = false }) { Text(stringResource(R.string.settings_dialog_got_it)) } }
        )
    }

    if (showCompressionInfoDialog) {
        AlertDialog(
            onDismissRequest = { showCompressionInfoDialog = false },
            title = { Text(stringResource(R.string.settings_auto_compression)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_dialog_compression_body), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Text(stringResource(R.string.settings_dialog_compression_off_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_compression_off_body), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.settings_dialog_compression_balanced_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_compression_balanced_body), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.settings_dialog_compression_max_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_compression_max_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_dialog_compression_tip),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showCompressionInfoDialog = false }) { Text(stringResource(R.string.settings_dialog_got_it)) } }
        )
    }

    if (showColorInfoDialog) {
        AlertDialog(
            onDismissRequest = { showColorInfoDialog = false },
            title = { Text(stringResource(R.string.settings_dialog_color_palette)) },
            text = {
                Text(
                    stringResource(R.string.settings_dialog_color_palette_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = { TextButton(onClick = { showColorInfoDialog = false }) { Text(stringResource(R.string.settings_dialog_got_it)) } }
        )
    }

    if (showBackupInfoDialog) {
        AlertDialog(
            onDismissRequest = { showBackupInfoDialog = false },
            title = { Text(stringResource(R.string.settings_dialog_backup)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_dialog_backup_full_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_backup_full_body), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Text(stringResource(R.string.settings_dialog_backup_legacy_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_dialog_backup_legacy_body), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showBackupInfoDialog = false }) { Text(stringResource(R.string.settings_dialog_got_it)) } }
        )
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false; pendingModeSelection = -1 },
            title = { Text(stringResource(R.string.settings_dialog_warning)) },
            text = { Text(stringResource(R.string.settings_dialog_warning_body)) },
            confirmButton = {
                BouncyButton(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onDiscardRecording()
                        if (pendingModeSelection != -1) {
                            viewModel.saveRecordMode(pendingModeSelection)
                        }
                        showWarningDialog = false
                        pendingModeSelection = -1
                    }
                ) { Text(stringResource(R.string.settings_dialog_warning_continue)) }
            },
            dismissButton = { TextButton(onClick = { showWarningDialog = false; pendingModeSelection = -1 }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    if (showApplyAllDialog) {
        AlertDialog(
            onDismissRequest = { showApplyAllDialog = false },
            title = { Text(stringResource(R.string.settings_dialog_apply_title)) },
            text = { Text(stringResource(R.string.settings_dialog_apply_body)) },
            confirmButton = {
                BouncyButton(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    onClick = {
                        showApplyAllDialog = false
                        viewModel.saveAiLanguage(tempAiLanguage)
                        viewModel.saveAiTask(tempAiTask)
                        viewModel.saveAiFormat(tempAiFormat)
                        viewModel.applyAiPreferencesToAllNotes { msg ->
                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                ) { Text(stringResource(R.string.settings_dialog_apply_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showApplyAllDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showLanguageSheet) {
        SettingsSelectionSheet(
            title = stringResource(R.string.settings_select_language),
            options = supportedLanguages,
            selected = tempAiLanguage,
            searchable = true,
            onDismiss = { showLanguageSheet = false },
            onSelect = { lang ->
                tempAiLanguage = lang
                showLanguageSheet = false
            }
        )
    }

    if (showColorPaletteSheet) {
        val styles = com.obinot.app.ui.theme.ColorStyle.entries
        val currentLabel = styles.getOrElse(colorStyle) { com.obinot.app.ui.theme.ColorStyle.TONAL_SPOT }.label
        SettingsSelectionSheet(
            title = stringResource(R.string.settings_dialog_color_palette),
            options = styles.map { it.label },
            selected = currentLabel,
            searchable = false,
            onDismiss = { showColorPaletteSheet = false },
            onSelect = { label ->
                val index = styles.indexOfFirst { it.label == label }.coerceAtLeast(0)
                viewModel.saveColorStyle(index)
                showColorPaletteSheet = false
            }
        )
    }

    if (showAppLanguageSheet) {
        val currentLabel = currentAppLanguage.displayLabel()
        val optionLabels = supportedAppLanguages.map { it.displayLabel() }
        SettingsSelectionSheet(
            title = stringResource(R.string.settings_select_app_language),
            options = optionLabels,
            selected = currentLabel,
            searchable = false,
            onDismiss = { showAppLanguageSheet = false },
            onSelect = { label ->
                val index = optionLabels.indexOf(label)
                val code = supportedAppLanguages.getOrNull(index)?.code ?: "device"
                viewModel.saveAppLanguage(code)
                showAppLanguageSheet = false
            }
        )
    }

    // ============================================================
    // Cards & sections
    // ============================================================

    // ---------- Always visible: Personalization ----------
    val personalizationCard: @Composable () -> Unit = {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.settings_personalization),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        isNameDirty = true
                    },
                    label = { Text(stringResource(R.string.settings_your_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                BouncyButton(
                    onClick = {
                        viewModel.saveUserName(nameInput)
                        isNameDirty = false
                        coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_name_saved)) }
                    },
                    enabled = isNameDirty,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.settings_save_name))
                }
            }
        }
    }

    // ---------- Always visible: AI Configuration (provider + API keys) ----------
    val aiConfigurationCard: @Composable () -> Unit = {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.settings_ai_configuration),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    BouncyIconButton(onClick = { showAiInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                ExpressiveToggleGroup(
                    selectedIndex = tempAiProvider,
                    onSelect = { tempAiProvider = it },
                    labels = listOf(
                        stringResource(R.string.settings_provider_gemini),
                        stringResource(R.string.settings_provider_groq),
                        stringResource(R.string.settings_provider_dynamic)
                    ),
                    icons = listOf(
                        Icons.Default.AutoAwesome,
                        Icons.Default.Bolt,
                        Icons.Default.Shuffle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedContent(targetState = tempAiProvider, label = "ApiKeyInput") { provider ->
                    val maxKeys = if (alphaUnlocked) 6 else 3

                    when (provider) {
                        0 -> {
                            Column {
                                KeyListEditor(
                                    keys = geminiKeyInputs,
                                    onKeysChange = { geminiKeyInputs = it },
                                    labelPrimaryRes = R.string.settings_rotation_key_primary,
                                    labelBackupRes = R.string.settings_rotation_key_backup,
                                    rotationEnabled = keyRotationEnabled,
                                    maxKeys = maxKeys,
                                    onDirty = { isGeminiKeyDirty = true }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.settings_get_api_key_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")))
                                    }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                BouncyButton(
                                    onClick = {
                                        viewModel.saveGeminiApiKeys(geminiKeyInputs.filter { it.isNotBlank() })
                                        viewModel.saveAiProvider(tempAiProvider)
                                        isGeminiKeyDirty = false
                                        coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_gemini_saved)) }
                                    },
                                    enabled = isGeminiKeyDirty || tempAiProvider != aiProvider,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(stringResource(R.string.settings_save_key))
                                }
                            }
                        }
                        1 -> {
                            Column {
                                KeyListEditor(
                                    keys = groqKeyInputs,
                                    onKeysChange = { groqKeyInputs = it },
                                    labelPrimaryRes = R.string.settings_rotation_key_primary,
                                    labelBackupRes = R.string.settings_rotation_key_backup,
                                    rotationEnabled = keyRotationEnabled,
                                    maxKeys = maxKeys,
                                    onDirty = { isGroqKeyDirty = true }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.settings_get_api_key_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")))
                                    }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                BouncyButton(
                                    onClick = {
                                        viewModel.saveGroqApiKeys(groqKeyInputs.filter { it.isNotBlank() })
                                        viewModel.saveAiProvider(tempAiProvider)
                                        isGroqKeyDirty = false
                                        coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_groq_saved)) }
                                    },
                                    enabled = isGroqKeyDirty || tempAiProvider != aiProvider,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(stringResource(R.string.settings_save_key))
                                }
                            }
                        }
                        else -> {
                            Column {
                                val geminiKey = geminiKeyInputs.firstOrNull().orEmpty()
                                val groqKey = groqKeyInputs.firstOrNull().orEmpty()
                                val bothConfigured = geminiKey.isNotBlank() && groqKey.isNotBlank()

                                Text(
                                    stringResource(R.string.settings_dynamic_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_dynamic_bullets),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                val geminiStatus = if (geminiKey.isNotBlank()) stringResource(R.string.settings_status_configured) else stringResource(R.string.settings_status_not_set)
                                val groqStatus = if (groqKey.isNotBlank()) stringResource(R.string.settings_status_configured) else stringResource(R.string.settings_status_not_set)
                                Text(
                                    text = stringResource(R.string.settings_dynamic_gemini_status, geminiStatus),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (geminiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = stringResource(R.string.settings_dynamic_groq_status, groqStatus),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (groqKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                BouncyButton(
                                    onClick = {
                                        viewModel.saveAiProvider(tempAiProvider)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(
                                                    if (bothConfigured) R.string.snackbar_dynamic_enabled
                                                    else R.string.snackbar_dynamic_needs_keys
                                                )
                                            )
                                        }
                                    },
                                    enabled = tempAiProvider != aiProvider,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(stringResource(R.string.settings_save_selection))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- Always visible: Backup ----------
    val backupCard: @Composable () -> Unit = {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.settings_backup_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    BouncyIconButton(onClick = { showBackupInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(R.string.settings_full_backup),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_full_backup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BouncyOutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.settings_import)) }
                    BouncyButton(
                        onClick = { exportLauncher.launch("Obinot_Backup_${formatter.format(Date())}.obinotbak") },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.settings_backup)) }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    stringResource(R.string.settings_legacy_backup),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_legacy_backup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                BouncyOutlinedButton(
                    onClick = { exportLegacyLauncher.launch("Obinot_Legacy_${formatter.format(Date())}.binotbak") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_backup_legacy_button)) }
            }
        }
    }

    // ---------- Always visible: Updates ----------
    val updatesCard: @Composable () -> Unit = {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.settings_updates_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(R.string.settings_app_version),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "v$currentVersion",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                if (updateState == UpdateState.Downloading) {
                    val animatedProgress by animateFloatAsState(targetValue = downloadProgress / 100f, label = "progress")
                    Spacer(Modifier.height(8.dp))
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_downloading_progress, downloadProgress),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (updateState == UpdateState.Available) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_new_version_ready, latestVersionStr),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (updateState == UpdateState.Error) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_update_failed, latestVersionStr),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (updateState == UpdateState.Idle && latestVersionStr.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_up_to_date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                when (updateState) {
                    UpdateState.Idle -> BouncyButton(
                        onClick = { viewModel.checkForUpdate(context, currentVersion) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_check_update)) }
                    UpdateState.Checking -> Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) { LoadingIndicator(modifier = Modifier.size(20.dp)) }
                    UpdateState.Available -> BouncyButton(
                        onClick = { viewModel.startDownload(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_update_app)) }
                    UpdateState.Downloading -> OutlinedButton(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_downloading)) }
                    UpdateState.Downloaded -> BouncyButton(
                        onClick = { viewModel.promptInstall(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_install))
                    }
                    UpdateState.Error -> BouncyOutlinedButton(
                        onClick = { viewModel.checkForUpdate(context, currentVersion) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_retry))
                    }
                }
            }
        }
    }

    // ---------- Section: Apariencia ----------
    val appearanceSection: @Composable () -> Unit = {
        CollapsableSettingsSection(
            icon = Icons.Default.Palette,
            title = stringResource(R.string.settings_appearance),
            subtitle = stringResource(R.string.settings_appearance_subtitle)
        ) {
            Column {
                Text(
                    stringResource(R.string.settings_theme_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                ExpressiveToggleGroup(
                    selectedIndex = themeMode,
                    onSelect = { viewModel.saveThemeMode(it) },
                    labels = listOf(
                        stringResource(R.string.settings_theme_auto),
                        stringResource(R.string.settings_theme_light),
                        stringResource(R.string.settings_theme_dark),
                        stringResource(R.string.settings_theme_amoled)
                    ),
                    icons = listOf(
                        Icons.Default.PhoneAndroid,
                        Icons.Default.LightMode,
                        Icons.Default.DarkMode,
                        Icons.Default.Contrast
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                SectionRowDivider()

                SettingsSelectorRow(
                    icon = Icons.Default.Palette,
                    label = stringResource(R.string.settings_color_style),
                    value = com.obinot.app.ui.theme.ColorStyle.entries
                        .getOrElse(colorStyle) { com.obinot.app.ui.theme.ColorStyle.TONAL_SPOT }.label,
                    onClick = { showColorPaletteSheet = true }
                )

                SectionRowDivider()

                SettingsSelectorRow(
                    icon = Icons.Default.Language,
                    label = stringResource(R.string.settings_app_language),
                    value = currentAppLanguage.displayLabel(),
                    onClick = { showAppLanguageSheet = true }
                )
            }
        }
    }

    // ---------- Section: IA ----------
    val iaSection: @Composable () -> Unit = {
        CollapsableSettingsSection(
            icon = Icons.Default.AutoAwesome,
            title = stringResource(R.string.settings_section_ia),
            subtitle = stringResource(R.string.settings_ia_subtitle)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_processing_task),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    BouncyIconButton(
                        onClick = { showTaskInfoDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                ExpressiveToggleGroup(
                    selectedIndex = tempAiTask,
                    onSelect = { tempAiTask = it },
                    labels = listOf(
                        stringResource(R.string.onboarding_task_tidy),
                        stringResource(R.string.onboarding_task_summary),
                        stringResource(R.string.onboarding_task_analyze)
                    ),
                    icons = listOf(
                        Icons.Default.AutoFixHigh,
                        Icons.Default.Summarize,
                        Icons.Default.Insights
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                SectionRowDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_output_format),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    BouncyIconButton(
                        onClick = { showFormatInfoDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                ExpressiveToggleGroup(
                    selectedIndex = tempAiFormat,
                    onSelect = { tempAiFormat = it },
                    labels = listOf(
                        stringResource(R.string.onboarding_format_paragraphs),
                        stringResource(R.string.onboarding_format_bullets)
                    ),
                    icons = listOf(
                        Icons.AutoMirrored.Filled.Notes,
                        Icons.AutoMirrored.Filled.FormatListBulleted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                SectionRowDivider()

                SettingsSelectorRow(
                    icon = Icons.Default.Language,
                    label = stringResource(R.string.settings_output_language),
                    value = tempAiLanguage,
                    onClick = { showLanguageSheet = true }
                )

                SectionRowDivider()

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_process),
                    description = stringResource(R.string.settings_auto_process_desc),
                    checked = autoProcessEnabled,
                    onCheckedChange = { viewModel.saveAutoProcess(it) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                val isChanged = tempAiLanguage != aiLanguage || tempAiTask != aiTask || tempAiFormat != aiFormat
                BouncyButton(
                    onClick = { showApplyAllDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isChanged
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_save_apply_all))
                }
            }
        }
    }

    // ---------- Section: Grabación ----------
    val recordingSection: @Composable () -> Unit = {
        CollapsableSettingsSection(
            icon = Icons.Default.Mic,
            title = stringResource(R.string.settings_section_recording),
            subtitle = stringResource(R.string.settings_recording_subtitle)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.recording_mode_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    BouncyIconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                ExpressiveToggleGroup(
                    selectedIndex = recordMode,
                    onSelect = { newIndex ->
                        if (recordMode != newIndex) {
                            if (isRecording) {
                                pendingModeSelection = newIndex
                                showWarningDialog = true
                            } else {
                                viewModel.saveRecordMode(newIndex)
                            }
                        }
                    },
                    labels = listOf(
                        stringResource(R.string.settings_fast),
                        stringResource(R.string.settings_accurate)
                    ),
                    icons = listOf(
                        Icons.Default.FlashOn,
                        Icons.Default.GraphicEq
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                SectionRowDivider()

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_record_background),
                    description = stringResource(R.string.settings_record_background_desc),
                    checked = backgroundRecordingEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.saveBackgroundRecording(enabled)
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )

                SectionRowDivider()

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_live_transcript),
                    description = stringResource(R.string.settings_live_transcript_desc),
                    checked = liveTranscriptEnabled,
                    onCheckedChange = { viewModel.saveLiveTranscript(it) },
                    badge = FeatureBadge.ALPHA
                )

                SectionRowDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.settings_auto_compression),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BetaBadge()
                    Spacer(modifier = Modifier.weight(1f))
                    BouncyIconButton(
                        onClick = { showCompressionInfoDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_auto_compression_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextToggleGroup(
                    selectedIndex = autoCompressionMode,
                    onSelect = { viewModel.saveAutoCompressionMode(it) },
                    labels = listOf(
                        stringResource(R.string.settings_compression_off),
                        stringResource(R.string.settings_compression_balanced),
                        stringResource(R.string.settings_compression_max)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                SectionRowDivider()

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_native_picker),
                    description = stringResource(R.string.settings_native_picker_desc),
                    checked = nativePickerEnabled,
                    onCheckedChange = { viewModel.saveNativePicker(it) }
                )
            }
        }
    }

    // ---------- Section: Advanced ----------
    val advancedSection: @Composable () -> Unit = {
        CollapsableSettingsSection(
            icon = Icons.Default.Tune,
            title = stringResource(R.string.settings_advanced),
            subtitle = stringResource(R.string.settings_advanced_subtitle)
        ) {
            Column {
                // Toggle de rotación de API keys.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_advanced_rotation_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BetaBadge()
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.settings_advanced_rotation_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = keyRotationEnabled,
                        onCheckedChange = { viewModel.saveKeyRotationEnabled(it) }
                    )
                }

                // ALPHA unlock: solo visible cuando la rotación está activa.
                // Permite extender el límite de keys por provider de 3 a 6.
                if (keyRotationEnabled) {
                    SectionRowDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.settings_rotation_alpha_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AlphaBadge()
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (alphaUnlocked) stringResource(R.string.settings_rotation_alpha_unlocked_desc)
                                else stringResource(R.string.settings_rotation_alpha_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        if (alphaUnlocked) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            BouncyButton(onClick = { viewModel.saveAlphaUnlocked(true) }) {
                                Text(stringResource(R.string.settings_rotation_alpha_button))
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- Section: About ----------
    val aboutSection: @Composable () -> Unit = {
        CollapsableSettingsSection(
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_about),
            subtitle = stringResource(R.string.settings_about_subtitle)
        ) {
            Column {
                Text(
                    stringResource(R.string.settings_about_fork_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SectionRowDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .bouncyClickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LexicoON/Obinot"))
                            )
                        }
                        .padding(14.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_github_repo),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.settings_github_repo_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ============================================================
    // Render
    // ============================================================

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->

        val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets

        with(animatedVisibilityScope) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .animateEnterExit(
                        enter = slideInVertically(
                            initialOffsetY = { 100 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                        ) + fadeIn(),
                        exit = fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { 100 })
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(safeTopMargin + 4.dp))

                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            personalizationCard()
                            aiConfigurationCard()
                            backupCard()
                            updatesCard()
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            appearanceSection()
                            iaSection()
                            recordingSection()
                            advancedSection()
                            aboutSection()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        personalizationCard()
                        aiConfigurationCard()
                        backupCard()
                        updatesCard()
                        appearanceSection()
                        iaSection()
                        recordingSection()
                        advancedSection()
                        aboutSection()
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}