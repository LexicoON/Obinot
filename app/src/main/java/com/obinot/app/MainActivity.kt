package com.obinot.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.obinot.app.ui.screens.HistoryScreen
import com.obinot.app.ui.screens.OnboardingScreen
import com.obinot.app.ui.screens.RecordScreen
import com.obinot.app.ui.screens.ResultScreen
import com.obinot.app.ui.screens.SettingsScreen
import com.obinot.app.ui.screens.TrashScreen
import com.obinot.app.ui.theme.BinotTheme
import com.obinot.app.ui.theme.ColorStyle
import com.obinot.app.utils.ImportExportHelper
import com.obinot.app.viewmodel.HistoryViewModel
import com.obinot.app.viewmodel.RecordViewModel
import com.obinot.app.viewmodel.ResultViewModel
import com.obinot.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    val incomingIntentUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Aplicar idioma ANTES de setContent. Idempotente con el LaunchedEffect
        // de BinotApp — acá solo cubrimos el arranque limpio.
        applyAppLanguageBlocking()

        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

        val appContainer = (application as BinotApplication).container

        handleIntent(intent)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(
                    appContainer.settingsRepository,
                    appContainer.noteRepository,
                    appContainer.labelRepository
                )
            )
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val colorStyleInt by settingsViewModel.colorStyle.collectAsState()
            val colorStyle = ColorStyle.entries.getOrElse(colorStyleInt) { ColorStyle.TONAL_SPOT }

            BinotTheme(
                themeMode = themeMode,
                colorStyle = colorStyle
            ) {
                BinotApp(appContainer, settingsViewModel, this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            incomingIntentUri.value = intent.data
        }
    }

    private fun applyAppLanguageBlocking() {
        val settingsRepo = (application as BinotApplication).container.settingsRepository
        val lang = runBlocking { settingsRepo.appLanguageFlow.first() }
        applyAppLanguageValue(lang)
    }
}

/**
 * Helper compartido entre MainActivity.applyAppLanguageBlocking() y el
 * LaunchedEffect de BinotApp. Centraliza el mapeo código → LocaleListCompat
 * para que no haya dos lugares con la misma lógica.
 */
private fun applyAppLanguageValue(lang: String) {
    val localeList = when (lang) {
        "en" -> LocaleListCompat.forLanguageTags("en")
        "es" -> LocaleListCompat.forLanguageTags("es")
        else -> LocaleListCompat.getEmptyLocaleList()
    }

    // Comparación por tag para evitar llamar setApplicationLocales de más.
    // (Un set innecesario dispara recreate() y puede entrar en loop.)
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val newTag = if (lang == "en" || lang == "es") lang else ""
    if (currentTag != newTag) {
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BinotApp(appContainer: AppContainer, settingsViewModel: SettingsViewModel, mainActivity: MainActivity) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val userName by settingsViewModel.userName.collectAsState()

    val isDataLoaded by settingsViewModel.isDataLoaded.collectAsState()
    val appLanguage by settingsViewModel.appLanguage.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Aplica el idioma cada vez que cambia el flow. AppCompat recrea la activity
    // automáticamente cuando el tag efectivamente cambia; con la guarda del
    // helper (currentTag != newTag) no hay loops.
    LaunchedEffect(appLanguage) {
        applyAppLanguageValue(appLanguage)
    }

    val incomingUri by mainActivity.incomingIntentUri.collectAsState()
    var isImportingFromExternal by remember { mutableStateOf(false) }

    val importFailedMsg = stringResource(R.string.main_import_failed)
    val unpackingMsg = stringResource(R.string.main_unpacking)

    if (!isDataLoaded) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
        return
    }

    val startDestination = if (userName.isBlank()) "onboarding" else "record"
    val snackbarHostState = remember { SnackbarHostState() }
    val isRecordingGlobal by appContainer.audioRecorderManager.isRecording.collectAsState()

    LaunchedEffect(incomingUri) {
        incomingUri?.let { uri ->
            isImportingFromExternal = true
            val newId = ImportExportHelper.importFile(
                context = context,
                uri = uri,
                repository = appContainer.noteRepository,
                labelRepository = appContainer.labelRepository
            )
            mainActivity.incomingIntentUri.value = null
            isImportingFromExternal = false

            if (newId != null) {
                navController.navigate("result/$newId")
            } else {
                snackbarHostState.showSnackbar(importFailedMsg)
            }
        }
    }

    val tabFadeSpec = spring<Float>(dampingRatio = 1.0f, stiffness = 1600f)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (currentRoute in listOf("record", "history", "settings")) {
                ShortNavigationBar {
                    ShortNavigationBarItem(
                        selected = currentRoute == "record",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("record") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.nav_record)) },
                        label = { Text(stringResource(R.string.nav_record)) }
                    )
                    ShortNavigationBarItem(
                        selected = currentRoute == "history",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("history") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = stringResource(R.string.nav_history)) },
                        label = { Text(stringResource(R.string.nav_history)) }
                    )
                    ShortNavigationBarItem(
                        selected = currentRoute == "settings",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("settings") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings)) },
                        label = { Text(stringResource(R.string.nav_settings)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        SharedTransitionLayout {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    if (targetState.destination.route?.startsWith("result") == true || initialState.destination.route?.startsWith("result") == true)
                        fadeIn(tween(0))
                    else
                        fadeIn(tabFadeSpec)
                },
                exitTransition = {
                    if (targetState.destination.route?.startsWith("result") == true || initialState.destination.route?.startsWith("result") == true)
                        fadeOut(tween(0))
                    else
                        fadeOut(tabFadeSpec)
                },
                popEnterTransition = {
                    if (targetState.destination.route?.startsWith("result") == true || initialState.destination.route?.startsWith("result") == true)
                        fadeIn(tween(0))
                    else
                        fadeIn(tabFadeSpec)
                },
                popExitTransition = {
                    if (targetState.destination.route?.startsWith("result") == true || initialState.destination.route?.startsWith("result") == true)
                        fadeOut(tween(0))
                    else
                        fadeOut(tabFadeSpec)
                }
            ) {
                composable("onboarding") {
                    OnboardingScreen(
                        settingsViewModel = settingsViewModel,
                        onComplete = { name, provider, key, task, format, compression ->
                            settingsViewModel.saveUserName(name)
                            settingsViewModel.saveAiProvider(provider)
                            if (provider == 0) settingsViewModel.saveApiKey(key) else settingsViewModel.saveGroqApiKey(key)
                            settingsViewModel.saveAiTask(task)
                            settingsViewModel.saveAiFormat(format)
                            settingsViewModel.saveAutoCompressionMode(compression)
                            navController.navigate("record") { popUpTo("onboarding") { inclusive = true } }
                        }
                    )
                }
                composable("record") {
                    val apiKey by settingsViewModel.apiKey.collectAsState()
                    val groqApiKey by settingsViewModel.groqApiKey.collectAsState()
                    val recordMode by settingsViewModel.recordMode.collectAsState()
                    val aiProvider by settingsViewModel.aiProvider.collectAsState()
                    val useNativePicker by settingsViewModel.nativePickerEnabled.collectAsState()

                    val recordViewModel: RecordViewModel = viewModel(
                        factory = RecordViewModel.provideFactory(
                            appContainer.audioRecorderManager,
                            appContainer.noteRepository,
                            apiKey,
                            groqApiKey,
                            context.applicationContext,
                            appContainer.settingsRepository
                        )
                    )
                    RecordScreen(
                        viewModel = recordViewModel,
                        userName = userName,
                        recordMode = recordMode,
                        aiProvider = aiProvider,
                        snackbarHostState = snackbarHostState,
                        animatedVisibilityScope = this@composable,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        onNoteClick = { id -> navController.navigate("result/$id") },
                        onImportFile = { uri ->
                            ImportExportHelper.importFile(
                                context = context,
                                uri = uri,
                                repository = appContainer.noteRepository,
                                labelRepository = appContainer.labelRepository
                            )
                        },
                        useNativePicker = useNativePicker
                    )
                }
                composable("history") {
                    val useNativePicker by settingsViewModel.nativePickerEnabled.collectAsState()

                    val historyViewModel: HistoryViewModel = viewModel(
                        factory = HistoryViewModel.provideFactory(
                            repository = appContainer.noteRepository,
                            labelRepository = appContainer.labelRepository
                        )
                    )
                    HistoryScreen(
                        viewModel = historyViewModel,
                        animatedVisibilityScope = this@composable,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        onNoteClick = { id -> navController.navigate("result/$id") },
                        onTrashClick = { navController.navigate("trash") },
                        useNativePicker = useNativePicker,
                        onImportFile = { uri ->
                            ImportExportHelper.importFile(
                                context = context,
                                uri = uri,
                                repository = appContainer.noteRepository,
                                labelRepository = appContainer.labelRepository
                            )
                        }
                    )
                }
                composable("trash") {
                    val historyViewModel: HistoryViewModel = viewModel(
                        factory = HistoryViewModel.provideFactory(
                            repository = appContainer.noteRepository,
                            labelRepository = appContainer.labelRepository
                        )
                    )
                    TrashScreen(
                        viewModel = historyViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        animatedVisibilityScope = this@composable,
                        isRecording = isRecordingGlobal,
                        onDiscardRecording = {
                            val path = appContainer.audioRecorderManager.stopRecording()
                            if (path != null) try { java.io.File(path).delete() } catch (e: Exception) {}
                        }
                    )
                }
                composable("result/{noteId}") { backStackEntry ->
                    val noteId = backStackEntry.arguments?.getString("noteId")?.toIntOrNull() ?: return@composable

                    val resultViewModel: ResultViewModel = viewModel(
                        factory = ResultViewModel.provideFactory(
                            noteId = noteId,
                            repository = appContainer.noteRepository,
                            settingsRepository = appContainer.settingsRepository,
                            labelRepository = appContainer.labelRepository,
                            appContext = context.applicationContext
                        )
                    )
                    ResultScreen(
                        viewModel = resultViewModel,
                        noteId = noteId,
                        animatedVisibilityScope = this@composable,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            if (isImportingFromExternal) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = unpackingMsg,
                            modifier = Modifier.padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}