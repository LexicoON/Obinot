package com.obinot.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.obinot.app.R
import com.obinot.app.viewmodel.ChatMessage
import com.obinot.app.viewmodel.ResultViewModel

/**
 * ModalBottomSheet con el chat sobre la nota.
 *
 * El historial NO se persiste: vive en el ResultViewModel y se limpia
 * cuando el sheet se cierra. Límite duro de 20 mensajes por sesión
 * (impuesto en el ViewModel).
 *
 * Ancho máximo: 560dp. En tablets y landscape, el sheet queda centrado
 * en vez de estirarse a lo ancho de la pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSheet(
    viewModel: ResultViewModel,
    onDismiss: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isSending by viewModel.isChatSending.collectAsState()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll al final cuando llega un mensaje nuevo o cuando aparece
    // el indicador de "pensando". Usamos el tamaño total como key para que
    // dispare en cada cambio de lista.
    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty() || isSending) {
            val target = messages.size + if (isSending) 1 else 0
            listState.animateScrollToItem(target.coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.clearChat()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .widthIn(max = 560.dp)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.chat_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))

            // Lista de mensajes
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty() && !isSending) {
                    // Estado vacío
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.chat_empty_state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(messages) { msg ->
                            ChatBubble(message = msg)
                        }
                        if (isSending) {
                            item(key = "thinking_indicator") {
                                ThinkingBubble()
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Aviso de límite
            val atLimit = messages.size >= 20
            AnimatedVisibility(
                visible = atLimit,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Text(
                    text = stringResource(R.string.chat_limit_reached),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Input
            ChatInputRow(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    val toSend = input.trim()
                    if (toSend.isNotEmpty() && !atLimit) {
                        viewModel.sendChatMessage(toSend)
                        input = ""
                    }
                },
                enabled = !atLimit && !isSending
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "thinking")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "thinking_alpha"
            )
            Text(
                text = stringResource(R.string.chat_thinking),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}

@Composable
private fun ChatInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.width(8.dp))

        val canSend = enabled && value.isNotBlank()
        BouncyIconButton(
            onClick = { if (canSend) onSend() },
            enabled = canSend,
            expandOnPress = 3.dp
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.chat_send_cd),
                tint = if (canSend) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}