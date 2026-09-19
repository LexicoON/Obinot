@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.obinot.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.obinot.app.ui.components.BouncyButton
import com.obinot.app.ui.components.BouncyOutlinedButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.UnknownHostException
import java.net.ConnectException
import java.net.SocketTimeoutException

enum class KeyVerificationState {
    IDLE, LOADING, SUCCESS, ERROR, SKIPPED
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: (name: String, provider: Int, key: String, task: Int, format: Int, autoCompression: Int) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 6 })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var nameInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var aiProvider by remember { mutableStateOf(1) } // Default to Groq

    var aiTask by remember { mutableStateOf(0) }
    var aiFormat by remember { mutableStateOf(0) }
    var autoCompression by remember { mutableStateOf(1) } // Default: Balanced

    var keyState by remember { mutableStateOf(KeyVerificationState.IDLE) }
    var keyErrorMessage by remember { mutableStateOf("") }

    val isFinalPage = pagerState.currentPage == 5

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = !isFinalPage,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(5) { index ->
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            )
                        }
                    }

                    // Reemplaza el contentPadding custom con un Modifier.height para
                    // preservar la altura visual. El shared BouncyButton no expone
                    // contentPadding, pero 56dp cumple la misma función.
                    BouncyButton(
                        onClick = {
                            if (pagerState.currentPage < 4) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else if (pagerState.currentPage == 4) {
                                // Sin API key: saltamos la verificación y vamos a pág 5
                                // con estado SKIPPED. El usuario no queda bloqueado.
                                if (apiKeyInput.isBlank()) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(5)
                                        keyState = KeyVerificationState.SKIPPED
                                    }
                                    return@BouncyButton
                                }

                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(5)
                                    keyState = KeyVerificationState.LOADING

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

                                    } catch (e: Exception) {
                                        delay(800)

                                        if (e is HttpException && aiProvider == 1 && (e.code() == 400 || e.code() == 404 || e.code() == 422)) {
                                            keyState = KeyVerificationState.SUCCESS
                                        } else {
                                            keyState = KeyVerificationState.ERROR
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
                        },
                        // Solo la pág 1 exige texto (nombre). La pág 4 ya no exige
                        // API key: si está vacía, el botón lleva a la pág 5 en SKIPPED.
                        enabled = when (pagerState.currentPage) {
                            1 -> nameInput.isNotBlank()
                            else -> true
                        },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 4) stringResource(R.string.onboarding_verify_key)
                                   else stringResource(R.string.onboarding_next),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = safeTopMargin)
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (page) {
                    // ---------- PAGE 0: Welcome ----------
                    0 -> {
                        Text(
                            text = stringResource(R.string.onboarding_welcome_title),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.onboarding_welcome_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }

                    // ---------- PAGE 1: Name ----------
                    1 -> {
                        Text(
                            text = stringResource(R.string.onboarding_name_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.onboarding_name_question),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text(stringResource(R.string.onboarding_name_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // ---------- PAGE 2: Task + Format ----------
                    2 -> {
                        Text(
                            text = stringResource(R.string.onboarding_personalize_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.onboarding_personalize_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = stringResource(R.string.onboarding_task_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                onClick = { aiTask = 0 },
                                selected = aiTask == 0
                            ) { Text(stringResource(R.string.onboarding_task_tidy), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                onClick = { aiTask = 1 },
                                selected = aiTask == 1
                            ) { Text(stringResource(R.string.onboarding_task_summary), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                onClick = { aiTask = 2 },
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
                                onClick = { aiFormat = 0 },
                                selected = aiFormat == 0
                            ) { Text(stringResource(R.string.onboarding_format_paragraphs)) }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                onClick = { aiFormat = 1 },
                                selected = aiFormat == 1
                            ) { Text(stringResource(R.string.onboarding_format_bullets)) }
                        }
                    }

                    // ---------- PAGE 3: Provider + API key ----------
                    3 -> {
                        Text(
                            text = stringResource(R.string.onboarding_provider_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.onboarding_provider_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                onClick = { aiProvider = 0 },
                                selected = aiProvider == 0
                            ) { Text(stringResource(R.string.onboarding_provider_gemini)) }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                onClick = { aiProvider = 1 },
                                selected = aiProvider == 1
                            ) { Text(stringResource(R.string.onboarding_provider_groq)) }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        AnimatedContent(targetState = aiProvider, label = "provider_info") { provider ->
                            if (provider == 1) {
                                Text(
                                    text = stringResource(R.string.onboarding_provider_groq_hint),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.onboarding_provider_gemini_hint),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        BouncyButton(
                            onClick = {
                                val url = if (aiProvider == 0) "https://aistudio.google.com/app/apikey" else "https://console.groq.com/keys"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
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
                            onValueChange = { apiKeyInput = it },
                            label = { Text(stringResource(R.string.onboarding_api_key_hint)) },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // ---------- PAGE 4: Auto Compression ----------
                    4 -> {
                        Text(
                            text = stringResource(R.string.onboarding_last_thing_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
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
                                        autoCompression = index
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

                        Text(
                            text = when (autoCompression) {
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

                    // ---------- PAGE 5: Verification ----------
                    5 -> {
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
                                        ContainedLoadingIndicator(
                                            modifier = Modifier.size(96.dp),
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            indicatorColor = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(32.dp))
                                        Text(
                                            text = stringResource(
                                                R.string.onboarding_connecting,
                                                if (aiProvider == 0) stringResource(R.string.onboarding_provider_gemini)
                                                else stringResource(R.string.onboarding_provider_groq)
                                            ),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
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
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(96.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(32.dp))
                                        Text(
                                            text = stringResource(R.string.onboarding_connected_title),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(R.string.onboarding_connected_body),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(48.dp))

                                        BouncyButton(
                                            onClick = {
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
                                                    autoCompression
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(64.dp),
                                            expandOnPress = 0.dp
                                        ) {
                                            Text(
                                                text = stringResource(R.string.onboarding_start_workspace),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                KeyVerificationState.ERROR -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(96.dp)
                                                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Error,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(32.dp))
                                        Text(
                                            text = stringResource(R.string.onboarding_failed_title),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = keyErrorMessage,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(32.dp))

                                        // Botón outlined: volver a la pág 3 a revisar la key.
                                        BouncyOutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(3)
                                                    keyState = KeyVerificationState.IDLE
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            expandOnPress = 0.dp
                                        ) {
                                            Text(
                                                text = stringResource(R.string.onboarding_review_key),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Botón rojo: continuar de todos modos con key vacía.
                                        // Nunca bloqueamos al usuario en el onboarding.
                                        BouncyButton(
                                            onClick = {
                                                onComplete(
                                                    nameInput.trim(),
                                                    aiProvider,
                                                    "",
                                                    aiTask,
                                                    aiFormat,
                                                    autoCompression
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            expandOnPress = 0.dp,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            )
                                        ) {
                                            Text(
                                                text = stringResource(R.string.onboarding_start_workspace_no_key),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                KeyVerificationState.SKIPPED -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(96.dp)
                                                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(32.dp))
                                        Text(
                                            text = stringResource(R.string.onboarding_no_key_title),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(R.string.onboarding_no_key_body),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(32.dp))

                                        BouncyOutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(3)
                                                    keyState = KeyVerificationState.IDLE
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            expandOnPress = 0.dp
                                        ) {
                                            Text(
                                                text = stringResource(R.string.onboarding_review_key),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        BouncyButton(
                                            onClick = {
                                                onComplete(
                                                    nameInput.trim(),
                                                    aiProvider,
                                                    "",
                                                    aiTask,
                                                    aiFormat,
                                                    autoCompression
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            expandOnPress = 0.dp,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            )
                                        ) {
                                            Text(
                                                text = stringResource(R.string.onboarding_start_workspace_no_key),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}