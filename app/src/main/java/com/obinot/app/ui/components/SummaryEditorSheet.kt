package com.obinot.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obinot.app.R

/**
 * Sheet para editar el summary (el texto procesado por la IA) como Markdown
 * crudo, con un toggle de vista previa que renderiza en vivo usando el mismo
 * MarkdownText que el lector de notas.
 *
 * Al guardar, el ViewModel se encarga de re-adjuntar el meta tag BINOT_META
 * (que preserva las preferencias de idioma/task/format con las que se generó
 * el summary original).
 *
 * No incluye el chatHistory ni toca el rawText: solo el summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryEditorSheet(
    initialSummary: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialSummary,
                selection = TextRange(initialSummary.length)
            )
        )
    }
    var isPreview by remember { mutableStateOf(false) }
    val previewScrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp)
        ) {
            // ---------- Header ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.summary_editor_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                BouncyIconButton(
                    onClick = { isPreview = !isPreview },
                    expandOnPress = 3.dp
                ) {
                    Icon(
                        imageVector = if (isPreview) Icons.Default.Edit else Icons.Default.Visibility,
                        contentDescription = stringResource(
                            if (isPreview) R.string.summary_editor_edit
                            else R.string.summary_editor_preview
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(4.dp))
                BouncyButton(
                    onClick = { onSave(value.text) },
                    enabled = value.text.isNotBlank()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.summary_editor_save))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            // ---------- Body ----------
            if (isPreview) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MarkdownText(
                        text = value.text,
                        scrollState = previewScrollState,
                        fontFamily = FontFamily.SansSerif,
                        enableScroll = true,
                        compact = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(16.dp)
                ) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.summary_editor_placeholder),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { value = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 22.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}