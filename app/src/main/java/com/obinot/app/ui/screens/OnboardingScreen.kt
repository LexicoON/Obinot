@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.obinot.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obinot.app.R
import com.obinot.app.data.Content
import com.obinot.app.data.GenerateContentRequest
import com.obinot.app.data.GroqChatRequest
import com.obinot.app.data.GroqMessage
import com.obinot.app.data.Part
import com.obinot.app.data.RetrofitClient
import com.obinot.app.data.SettingsRepository
import com.obinot.app.ui.components.AppLanguage
import com.obinot.app.ui.components.BouncyButton
import com.obinot.app.ui.components.BouncyOutlinedButton
import com.obinot.app.ui.components.BouncyToggleButton
import com.obinot.app.ui.components.FrostedModalBottomSheet
import com.obinot.app.ui.components.LoadPhase
import com.obinot.app.ui.components.PhaseLoader
import com.obinot.app.ui.components.displayLabel
import com.obinot.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class KeyVerificationState {
    IDLE, LOADING, SUCCESS, ERROR, SKIPPED
}

/**
 * "Mood" del onboarding. Controla la intensidad y el color del fondo reactivo.
 * Va de -1f (warning) a 1f (celebrate). 0f es calm.
 */
private const val MOOD_CALM = 0f
private const val MOOD_ACTIVE = 0.4f
private const val MOOD_LOADING = 0.6f
private const val MOOD_CELEBRATE = 1f
private const val MOOD_WARNING = -0.4f

private val AI_LANGUAGES: List<String> = listOf(
    SettingsRepository.AI_LANGUAGE_SYSTEM,
    "English", "Indonesia", "Spanish", "French", "German", "Chinese (Simplified)",
    "Chinese (Traditional)", "Japanese", "Korean", "Arabic", "Russian", "Portuguese",
    "Italian", "Hindi", "Bengali", "Urdu", "Turkish", "Vietnamese", "Thai",
    "Dutch", "Polish", "Swedish", "Malay"
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    settingsViewModel: SettingsViewModel,
    onComplete: (name: String, provider: Int, key: String, task: Int, format: Int, autoCompression: Int, aiLanguage: String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 8 })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val appLanguage by settingsViewModel.appLanguage.collectAsState()
    val blurEnabled by settingsViewModel.blurEnabled.collectAsState()

    var nameInput by rememberSaveable { mutableStateOf("") }
    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var aiProvider by rememberSaveable { mutableStateOf(1) }

    var aiTask by rememberSaveable { mutableStateOf(0) }
    var aiFormat by rememberSaveable { mutableStateOf(0) }
    var autoCompression by rememberSaveable { mutableStateOf(1) }

    var aiLanguage by rememberSaveable { mutableStateOf(SettingsRepository.AI_LANGUAGE_SYSTEM) }
    var showAiLanguageSheet by rememberSaveable { mutableStateOf(false) }

    var keyState by rememberSaveable { mutableStateOf(KeyVerificationState.IDLE) }
    var keyErrorMessage by rememberSaveable { mutableStateOf("") }

    // Mood global del onboarding. Se actualiza desde las páginas y desde el
    // flujo de verificación. El fondo reactivo lo consume.
    var mood by remember { mutableFloatStateOf(MOOD_CALM) }

    // Página 0 es la única sin fondo animado (perf-safe). De la 1 en adelante
    // el fondo se muestra si el blur está habilitado Y el SO lo soporta.
    val isPreflightPage = pagerState.currentPage == 0

    val isFinalPage = pagerState.currentPage == 7
    val isVerificationPage = pagerState.currentPage == 6

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isPreflightPage) {
            ReactiveBackground(mood = mood, blurEnabled = blurEnabled)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                AnimatedVisibility(
                    visible = !isFinalPage && !isVerificationPage,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    OnboardingBottomBar(
                        currentPage = pagerState.currentPage,
                        pageCount = 8,
                        blurEnabled = blurEnabled && !isPreflightPage,
                        nextEnabled = when (pagerState.currentPage) {
                            1 -> nameInput.isNotBlank()
                            else -> true
                        },
                        nextLabelRes = if (pagerState.currentPage == 5) R.string.onboarding_verify_key
                                       else R.string.onboarding_next,
                        onNext = {
                            mood = MOOD_ACTIVE
                            if (pagerState.currentPage < 5) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else if (pagerState.currentPage == 5) {
                                if (apiKeyInput.isBlank()) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(6)
                                        keyState = KeyVerificationState.SKIPPED
                                        mood = MOOD_WARNING
                                    }
                                    return@OnboardingBottomBar
                                }
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(6)
                                    keyState = KeyVerificationState.LOADING
                                    mood = MOOD_LOADING
                                    try {
                                        val cleanKey = apiKeyInput
                                            .replace(Regex("(?i)^bearer\\s*"), "")
                                            .replace(" ", "")
                                            .trim()

                                        if (aiProvider == 0) {
                                            val req = GenerateContentRequest(
                                                contents = listOf(Content(parts = listOf(Part(text = "hi"))))
                                            )
                                            RetrofitClient.service.generateContent(
                                                model = "gemini-3.5-flash-lite",
                                                apiKey = cleanKey,
                                                request = req
                                            )
                                        } else {
                                            val req = GroqChatRequest(
                                                model = "llama-3.1-8b-instant",
                                                messages = listOf(GroqMessage(role = "user", content = "hi"))
                                            )
                                            RetrofitClient.groqService.generateContent("Bearer $cleanKey", req)
                                        }

                                        delay(800)
                                        keyState = KeyVerificationState.SUCCESS
                                        mood = MOOD_CELEBRATE
                                    } catch (e: Exception) {
                                        delay(800)
                                        if (e is HttpException && aiProvider == 1 && (e.code() == 400 || e.code() == 404 || e.code() == 422)) {
                                            keyState = KeyVerificationState.SUCCESS
                                            mood = MOOD_CELEBRATE
                                        } else {
                                            keyState = KeyVerificationState.ERROR
                                            mood = MOOD_WARNING
                                            keyErrorMessage = when (e) {
                                                is HttpException -> when (e.code()) {
                                                    400 -> if (aiProvider == 0) {
                                                        context.getString(R.string.onboarding_error_400_gemini)
                                                    } else {
                                                        context.getString(R.string.onboarding_error_400_groq)
                                                    }
                                                    401 -> context.getString(R.string.onboarding_error_401)
                                                    403 -> context.getString(R.string.onboarding_error_403)
                                                    429 -> context.getString(R.string.onboarding_error_429)
                                                    else -> context.getString(R.string.onboarding_error_server_code, e.code())
                                                }
                                                is UnknownHostException, is ConnectException, is SocketTimeoutException ->
                                                    context.getString(R.string.onboarding_error_network)
                                                else -> context.getString(
                                                    R.string.onboarding_error_unexpected,
                                                    e.localizedMessage ?: e.javaClass.simpleName
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
            val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets

            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

                // En la página 0 el selector está dentro de la propia página.
                if (!isPreflightPage && !isFinalPage) {
                    OnboardingLanguageSelector(
                        selected = appLanguage,
                        onSelect = { code ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            settingsViewModel.saveAppLanguage(code)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = safeTopMargin + 8.dp, start = 24.dp, end = 24.dp)
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val isActive = pagerState.currentPage == page
                    PagerPage(isActive = isActive) {
                        when (page) {
                            0 -> PreflightPage(
                                appLanguage = appLanguage,
                                onLanguageSelect = { code ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    settingsViewModel.saveAppLanguage(code)
                                },
                                blurEnabled = blurEnabled,
                                onBlurChange = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    settingsViewModel.saveBlurEnabled(it)
                                },
                                topPadding = safeTopMargin
                            )
                            1 -> NamePage(nameInput) { nameInput = it; mood = MOOD_ACTIVE }
                            2 -> TaskFormatPage(
                                aiTask = aiTask,
                                aiFormat = aiFormat,
                                onTaskChange = { aiTask = it; mood = MOOD_ACTIVE },
                                onFormatChange = { aiFormat = it; mood = MOOD_ACTIVE }
                            )
                            3 -> AiLanguagePage(
                                current = aiLanguage,
                                onPick = { showAiLanguageSheet = true; mood = MOOD_ACTIVE }
                            )
                            4 -> ProviderPage(
                                aiProvider = aiProvider,
                                onProviderChange = { aiProvider = it; mood = MOOD_ACTIVE },
                                apiKeyInput = apiKeyInput,
                                onApiKeyChange = { apiKeyInput = it },
                                onOpenConsole = {
                                    val url = when (aiProvider) {
                                        0 -> "https://aistudio.google.com/app/apikey"
                                        1 -> "https://console.groq.com/keys"
                                        else -> "https://aistudio.google.com/app/apikey"
                                    }
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            )
                            5 -> CompressionPage(
                                autoCompression = autoCompression,
                                onCompressionChange = { autoCompression = it; mood = MOOD_ACTIVE }
                            )
                            6 -> VerificationPage(
                                keyState = keyState,
                                aiProvider = aiProvider,
                                errorMessage = keyErrorMessage,
                                onReview = {
                                    mood = MOOD_CALM
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(4)
                                        keyState = KeyVerificationState.IDLE
                                    }
                                },
                                onContinue = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(7)
                                        mood = MOOD_CELEBRATE
                                    }
                                }
                            )
                            7 -> FinalWelcomePage(
                                onStart = {
                                    val finalCleanKey = apiKeyInput
                                        .replace(Regex("(?i)^bearer\\s*"), "")
                                        .replace(" ", "")
                                        .trim()
                                    onComplete(
                                        nameInput.trim(),
                                        aiProvider,
                                        finalCleanKey,
                                        aiTask,
                                        aiFormat,
                                        autoCompression,
                                        aiLanguage
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAiLanguageSheet) {
        AiLanguagePickerSheet(
            current = aiLanguage,
            onSelect = { selected ->
                aiLanguage = selected
                showAiLanguageSheet = false
            },
            onDismiss = { showAiLanguageSheet = false }
        )
    }
}

// ============================================================
// REACTIVE BACKGROUND
// ============================================================

/**
 * Fondo reactivo. Dos blobs (secondary + tertiary) que driftean lento, con
 * parámetros modulados por [mood]:
 *   - mood < 0  → warning: colores se desaturan hacia error, alpha más bajo
 *   - mood = 0  → calm
 *   - mood > 0  → celebrate: alphas más altos, radios más grandes
 *
 * Los parámetros se animan con animateFloatAsState para que el cambio sea
 * suave. Sin blur activo, el fondo se dibuja igual pero sin el frosted layer.
 */
@Composable
private fun ReactiveBackground(mood: Float, blurEnabled: Boolean) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error

    val animatedMood by animateFloatAsState(
        targetValue = mood,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "bgMood"
    )

    // Intensidad global 0.6 .. 1.6 según mood positivo; y de 0.6 a 0.4 en negativo.
    val celebrateIntensity = (1f + animatedMood * 0.6f).coerceIn(0.4f, 1.6f)
    // Cuánto del color se va hacia error cuando mood es negativo.
    val warningBlend = (-animatedMood).coerceIn(0f, 1f)

    val blobAColor = lerpColor(secondaryColor, errorColor, warningBlend * 0.7f)
    val blobBColor = lerpColor(tertiaryColor, errorColor, warningBlend * 0.5f)

    val baseAlphaA = if (isDark) 0.18f else 0.10f
    val baseAlphaB = if (isDark) 0.14f else 0.08f

    val alphaA = (baseAlphaA * celebrateIntensity).coerceIn(0f, 0.4f)
    val alphaB = (baseAlphaB * celebrateIntensity).coerceIn(0f, 0.35f)

    val infinite = rememberInfiniteTransition(label = "reactive_bg")
    val phaseA by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_a"
    )
    val phaseB by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(21000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_b"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val radiusBoost = 1f + animatedMood * 0.15f

        val baseRadius = 0.55f * minOf(w, h) * radiusBoost

        val cxA = w * 0.55f + cos(phaseA) * (w * 0.22f)
        val cyA = h * 0.28f + sin(phaseA) * (h * 0.14f)
        val radiusA = baseRadius * 1.10f

        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to blobAColor.copy(alpha = alphaA),
                    0.55f to blobAColor.copy(alpha = alphaA * 0.30f),
                    1.0f to Color.Transparent
                ),
                center = Offset(cxA, cyA),
                radius = radiusA
            ),
            center = Offset(cxA, cyA),
            radius = radiusA
        )

        val cxB = w * 0.35f + cos(phaseB) * (w * 0.18f)
        val cyB = h * 0.72f + sin(phaseB) * (h * 0.20f)
        val radiusB = baseRadius * 0.95f

        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to blobBColor.copy(alpha = alphaB),
                    0.55f to blobBColor.copy(alpha = alphaB * 0.30f),
                    1.0f to Color.Transparent
                ),
                center = Offset(cxB, cyB),
                radius = radiusB
            ),
            center = Offset(cxB, cyB),
            radius = radiusB
        )

        // Sparkles sutiles en el fondo (muy tenues) cuando mood > 0.5.
        if (animatedMood > 0.5f && blurEnabled) {
            drawSparkles(
                color = MaterialTheme.colorScheme.primary,
                count = 24,
                timePhase = phaseA / (2 * PI).toFloat(),
                intensity = (animatedMood - 0.5f) * 2f
            )
        }
    }
}

/** Interpola dos colores linealmente en RGB con alpha preservado del primero. */
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val ct = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * ct,
        green = a.green + (b.green - a.green) * ct,
        blue = a.blue + (b.blue - a.blue) * ct,
        alpha = a.alpha
    )
}

/** Dibuja partículas tipo sparkle con twinkle y drift. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkles(
    color: Color,
    count: Int,
    timePhase: Float,
    intensity: Float
) {
    val w = size.width
    val h = size.height
    val random = Random(42)
    val baseRadius = size.minDimension * 0.0035f
    repeat(count) { i ->
        val baseX = random.nextFloat()
        val baseY = random.nextFloat()
        val twinkleSpeed = 0.7f + random.nextFloat() * 1.6f
        val phase = random.nextFloat()
        val drift = timePhase * (0.02f + random.nextFloat() * 0.05f)

        val x = ((baseX + drift) % 1f) * w
        val y = ((baseY + drift * 0.6f) % 1f) * h

        val twinkle = (sin((timePhase * twinkleSpeed + phase) * 2f * PI).toFloat() + 1f) * 0.5f
        val alpha = (0.25f + 0.75f * twinkle) * intensity.coerceIn(0f, 1f)

        drawCircle(
            color = color.copy(alpha = alpha * 0.7f),
            radius = baseRadius * (0.6f + random.nextFloat() * 0.8f),
            center = Offset(x, y)
        )
    }
}

// ============================================================
// LAYOUT HELPERS
// ============================================================

@Composable
private fun PagerPage(
    isActive: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pageAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.90f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pageScale"
    )
    val translationY by animateFloatAsState(
        targetValue = if (isActive) 0f else 40f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pageY"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                    this.translationY = translationY
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Ícono hero circular con floating sutil (bob vertical). El fondo es un
 * gradiente radial sutil del primaryContainer.
 */
@Composable
private fun FloatingIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "hero_float")
    val bob by infinite.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    Box(
        modifier = modifier
            .size(96.dp)
            .graphicsLayer { translationY = bob }
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(44.dp)
        )
    }
}

/**
 * Bottom bar con pills de progreso (la activa se estira) + CTA con sweep
 * shimmer. El sweep corre cada ~3s y atraviesa el botón de izquierda a
 * derecha, dando el toque "vivo".
 */
@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    pageCount: Int,
    blurEnabled: Boolean,
    nextEnabled: Boolean,
    nextLabelRes: Int,
    onNext: () -> Unit
) {
    val containerAlpha = if (blurEnabled) 0.72f else 0.92f
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    val isCurrent = currentPage == index
                    val isPast = index < currentPage
                    val width by animateDpAsState(
                        targetValue = if (isCurrent) 28.dp else 8.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "pillWidth_$index"
                    )
                    val color by animateColorAsState(
                        targetValue = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isPast -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        animationSpec = tween(300),
                        label = "pillColor_$index"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            Box {
                BouncyButton(
                    onClick = onNext,
                    enabled = nextEnabled,
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(
                        text = stringResource(nextLabelRes),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
                if (nextEnabled) {
                    SweepShimmer(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(26.dp))
                    )
                }
            }
        }
    }
}

/**
 * Barrido de luz diagonal que cruza el modifier cada ~3.2s. Es puro Canvas,
 * sin allocation por frame. Ideal para dar vida al CTA sin costo.
 */
@Composable
private fun SweepShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    Canvas(modifier = modifier) {
        val bandWidth = size.width * 0.28f
        val center = progress * size.width
        val brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.28f),
                Color.Transparent
            ),
            startX = center - bandWidth / 2f,
            endX = center + bandWidth / 2f
        )
        drawRect(brush = brush)
    }
}

// ============================================================
// PAGES
// ============================================================

/**
 * Página 0: idioma + toggle de blur. Es la única sin fondo animado para
 * garantizar arranque seguro en teléfonos viejos. El usuario que quiera
 * performance la desactiva acá y todo el resto del onboarding corre liviano.
 */
@Composable
private fun PreflightPage(
    appLanguage: String,
    onLanguageSelect: (String) -> Unit,
    blurEnabled: Boolean,
    onBlurChange: (Boolean) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp
) {
    Spacer(modifier = Modifier.height(topPadding + 16.dp))

    // Selector de idioma arriba, sin efectos pesados.
    OnboardingLanguageSelector(
        selected = appLanguage,
        onSelect = onLanguageSelect,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(48.dp))

    Text(
        text = stringResource(R.string.onboarding_blur_toggle_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_blur_toggle_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp)
    )

    Spacer(modifier = Modifier.height(32.dp))

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_blur),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_blur_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = blurEnabled,
                    onCheckedChange = onBlurChange
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.onboarding_blur_toggle_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun NamePage(value: String, onValueChange: (String) -> Unit) {
    FloatingIcon(Icons.Default.Person)
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.onboarding_name_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_name_question),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(32.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.onboarding_name_hint)) },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TaskFormatPage(
    aiTask: Int,
    aiFormat: Int,
    onTaskChange: (Int) -> Unit,
    onFormatChange: (Int) -> Unit
) {
    FloatingIcon(Icons.Default.Tune)
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.onboarding_personalize_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_personalize_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(28.dp))

    Text(
        text = stringResource(R.string.onboarding_task_label),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            onClick = { onTaskChange(0) },
            selected = aiTask == 0
        ) { Text(stringResource(R.string.onboarding_task_tidy), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            onClick = { onTaskChange(1) },
            selected = aiTask == 1
        ) { Text(stringResource(R.string.onboarding_task_summary), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            onClick = { onTaskChange(2) },
            selected = aiTask == 2
        ) { Text(stringResource(R.string.onboarding_task_analyze), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.onboarding_format_label),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            onClick = { onFormatChange(0) },
            selected = aiFormat == 0
        ) { Text(stringResource(R.string.onboarding_format_paragraphs)) }
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            onClick = { onFormatChange(1) },
            selected = aiFormat == 1
        ) { Text(stringResource(R.string.onboarding_format_bullets)) }
    }
}

@Composable
private fun AiLanguagePage(
    current: String,
    onPick: () -> Unit
) {
    val displayValue = if (current == SettingsRepository.AI_LANGUAGE_SYSTEM) {
        stringResource(R.string.settings_language_device)
    } else {
        current
    }

    FloatingIcon(Icons.Default.Language)
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.onboarding_ai_language_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_ai_language_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(32.dp))

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onPick() }
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_output_language),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ProviderPage(
    aiProvider: Int,
    onProviderChange: (Int) -> Unit,
    apiKeyInput: String,
    onApiKeyChange: (String) -> Unit,
    onOpenConsole: () -> Unit
) {
    FloatingIcon(Icons.Default.Key)
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.onboarding_provider_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_provider_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            onClick = { onProviderChange(0) },
            selected = aiProvider == 0
        ) { Text(stringResource(R.string.onboarding_provider_gemini), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            onClick = { onProviderChange(1) },
            selected = aiProvider == 1
        ) { Text(stringResource(R.string.onboarding_provider_groq), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            onClick = { onProviderChange(2) },
            selected = aiProvider == 2
        ) { Text(stringResource(R.string.onboarding_provider_dynamic), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }

    Spacer(modifier = Modifier.height(16.dp))

    AnimatedContent(targetState = aiProvider, label = "provider_info") { provider ->
        when (provider) {
            1 -> Text(
                text = stringResource(R.string.onboarding_provider_groq_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            2 -> Text(
                text = stringResource(R.string.onboarding_provider_dynamic_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            else -> Text(
                text = stringResource(R.string.onboarding_provider_gemini_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    BouncyButton(
        onClick = onOpenConsole,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        expandOnPress = 0.dp,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.onboarding_get_api_key),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = apiKeyInput,
        onValueChange = onApiKeyChange,
        label = { Text(stringResource(R.string.onboarding_api_key_hint)) },
        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CompressionPage(
    autoCompression: Int,
    onCompressionChange: (Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current

    FloatingIcon(Icons.Default.GraphicEq)
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.onboarding_last_thing_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_last_thing_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(32.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Icon(
            Icons.Default.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.onboarding_compression_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        val labels = listOf(
            stringResource(R.string.onboarding_compression_off),
            stringResource(R.string.onboarding_compression_balanced),
            stringResource(R.string.onboarding_compression_max),
        )
        labels.forEachIndexed { index, label ->
            ToggleButton(
                checked = autoCompression == index,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCompressionChange(index)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (autoCompression == index) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    AnimatedContent(targetState = autoCompression, label = "compression_desc") { mode ->
        Text(
            text = when (mode) {
                0 -> stringResource(R.string.onboarding_compression_off_desc)
                1 -> stringResource(R.string.onboarding_compression_balanced_desc)
                else -> stringResource(R.string.onboarding_compression_max_desc)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun VerificationPage(
    keyState: KeyVerificationState,
    aiProvider: Int,
    errorMessage: String,
    onReview: () -> Unit,
    onContinue: () -> Unit
) {
    AnimatedContent(
        targetState = keyState,
        label = "verification_state",
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }
    ) { state ->
        when (state) {
            KeyVerificationState.IDLE, KeyVerificationState.LOADING -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Pulso de anillos concéntricos alrededor del loader.
                    Box(
                        modifier = Modifier.size(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PulsingRings()
                        PhaseLoader(phase = LoadPhase.PROCESSING, size = 96.dp)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = stringResource(
                            R.string.onboarding_connecting,
                            if (aiProvider == 0) stringResource(R.string.onboarding_provider_gemini)
                            else stringResource(R.string.onboarding_provider_groq)
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.onboarding_verifying),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            KeyVerificationState.SUCCESS -> {
                VerificationResult(
                    icon = Icons.Default.Check,
                    iconTint = MaterialTheme.colorScheme.primary,
                    bg = MaterialTheme.colorScheme.primaryContainer,
                    title = stringResource(R.string.onboarding_connected_title),
                    titleColor = MaterialTheme.colorScheme.primary,
                    body = stringResource(R.string.onboarding_connected_body),
                    primaryLabel = stringResource(R.string.onboarding_next),
                    onPrimary = onContinue
                )
            }

            KeyVerificationState.ERROR -> {
                VerificationResult(
                    icon = Icons.Default.Error,
                    iconTint = MaterialTheme.colorScheme.error,
                    bg = MaterialTheme.colorScheme.errorContainer,
                    title = stringResource(R.string.onboarding_failed_title),
                    titleColor = MaterialTheme.colorScheme.error,
                    body = errorMessage,
                    primaryLabel = stringResource(R.string.onboarding_start_workspace_no_key),
                    onPrimary = onContinue,
                    secondaryLabel = stringResource(R.string.onboarding_review_key),
                    onSecondary = onReview,
                    primaryIsError = true
                )
            }

            KeyVerificationState.SKIPPED -> {
                VerificationResult(
                    icon = Icons.Default.Key,
                    iconTint = MaterialTheme.colorScheme.error,
                    bg = MaterialTheme.colorScheme.errorContainer,
                    title = stringResource(R.string.onboarding_no_key_title),
                    titleColor = MaterialTheme.colorScheme.error,
                    body = stringResource(R.string.onboarding_no_key_body),
                    primaryLabel = stringResource(R.string.onboarding_next),
                    onPrimary = onContinue,
                    secondaryLabel = stringResource(R.string.onboarding_review_key),
                    onSecondary = onReview,
                    primaryIsError = true
                )
            }
        }
    }
}

/**
 * Anillos concéntricos que pulsan hacia afuera. Puras líneas, cero alloc por
 * frame. Solo se muestra mientras se verifica la API key.
 */
@Composable
private fun PulsingRings() {
    val transition = rememberInfiniteTransition(label = "rings")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringPhase"
    )
    val color = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxRadius = size.minDimension / 2f
        for (i in 0 until 3) {
            val p = ((phase + i / 3f) % 1f)
            val radius = maxRadius * (0.35f + p * 0.65f)
            val alpha = (1f - p) * 0.5f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx()
                )
            )
        }
    }
}

/**
 * Layout común para los 3 estados finales de verificación. La escala de
 * entrada es bouncy, igual que el resto del onboarding.
 */
@Composable
private fun VerificationResult(
    icon: ImageVector,
    iconTint: Color,
    bg: Color,
    title: String,
    titleColor: Color,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    primaryIsError: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        val scale = remember { Animatable(0.5f) }
        LaunchedEffect(Unit) {
            scale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (secondaryLabel != null && onSecondary != null) {
            BouncyOutlinedButton(
                onClick = onSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                expandOnPress = 0.dp
            ) {
                Text(
                    text = secondaryLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        BouncyButton(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            expandOnPress = 0.dp,
            colors = if (primaryIsError) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(
                text = primaryLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Página final: welcome celebratorio con partículas, glow y slogan. Es la
 * única con particle field completo (60 partículas), porque es el cierre
 * del onboarding y el momento "wow".
 */
@Composable
private fun FinalWelcomePage(onStart: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    // Entrada: header y slogan aparecen con stagger.
    val titleScale = remember { Animatable(0.7f) }
    val titleAlpha = remember { Animatable(0f) }
    val sloganAlpha = remember { Animatable(0f) }
    val ctaAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        titleScale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
        titleAlpha.animateTo(1f, tween(500))
        sloganAlpha.animateTo(1f, tween(700, delayMillis = 150))
        ctaAlpha.animateTo(1f, tween(700, delayMillis = 450))
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Particle field: 60 dots, un solo infinite transition.
        val transition = rememberInfiniteTransition(label = "final_particles")
        val time by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(24000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "time"
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val rng = Random(2026)
            repeat(60) { i ->
                val bx = rng.nextFloat()
                val by = rng.nextFloat()
                val drift = rng.nextFloat() * 0.08f + 0.02f
                val twinkleSpeed = 0.5f + rng.nextFloat() * 1.5f
                val phase = rng.nextFloat()

                val x = ((bx + time * drift) % 1f) * w
                val y = ((by + time * drift * 0.7f) % 1f) * h
                val twinkle = (sin((time * twinkleSpeed + phase) * 2f * PI).toFloat() + 1f) * 0.5f
                val alpha = (0.15f + 0.55f * twinkle)
                val color = if (i % 3 == 0) tertiary else primary

                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = (size.minDimension * 0.0022f) * (0.5f + rng.nextFloat() * 0.9f),
                    center = Offset(x, y)
                )
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Hero: glow grande detrás del ícono.
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .graphicsLayer {
                        scaleX = titleScale.value
                        scaleY = titleScale.value
                        this.alpha = titleAlpha.value
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primary.copy(alpha = 0.35f),
                                    primary.copy(alpha = 0.0f)
                                )
                            ),
                            shape = CircleShape
                        )
                )
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboarding_final_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    scaleX = titleScale.value
                    scaleY = titleScale.value
                    alpha = titleAlpha.value
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.onboarding_final_slogan),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = sloganAlpha.value
                }
            )

            Spacer(modifier = Modifier.height(64.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = ctaAlpha.value }
            ) {
                BouncyButton(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    expandOnPress = 0.dp
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_final_cta),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                SweepShimmer(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(32.dp))
                )
            }
        }
    }
}

// ============================================================
// AI LANGUAGE PICKER SHEET
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiLanguagePickerSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val systemResolved = remember {
        SettingsRepository.resolveAiLanguage(SettingsRepository.AI_LANGUAGE_SYSTEM)
    }

    FrostedModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_select_language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(AI_LANGUAGES) { lang ->
                    val isSystem = lang == SettingsRepository.AI_LANGUAGE_SYSTEM
                    val display = if (isSystem) {
                        "${stringResource(R.string.settings_language_device)} → $systemResolved"
                    } else {
                        lang
                    }
                    val isSelected = lang == current

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else Color.Transparent
                            )
                            .clickable { onSelect(lang) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = display,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// LANGUAGE SELECTOR (app language)
// ============================================================

@Composable
private fun OnboardingLanguageSelector(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = AppLanguage.entries.toList()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEach { lang ->
            val isSelected = selected == lang.code
            BouncyToggleButton(
                checked = isSelected,
                onCheckedChange = { onSelect(lang.code) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = lang.displayLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}