package com.obinot.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Aplica el efecto bouncy (squish + rebound) a un [Animatable] externo.
 *
 * Lo siguen usando: FAB de HistoryScreen, play button redondo de ResultScreen,
 * NoteCard de HistoryScreen, TrashedNoteCard de TrashScreen, y el play button
 * del side panel de ResultScreen.
 *
 * El truco del `snapTo` en Release es lo que garantiza que incluso un tap
 * ultra-rápido (press+release en el mismo frame) muestre siempre una
 * animación perceptible: si la animación de Press no alcanzó a progresar,
 * forzamos un snap intermedio antes de animar de vuelta al estado normal.
 */
suspend fun observeBouncyPress(
    interactionSource: MutableInteractionSource,
    scale: Animatable<Float, AnimationVector1D>,
    pressedScale: Float = 0.94f
) {
    interactionSource.interactions.collect { interaction ->
        when (interaction) {
            is PressInteraction.Press -> {
                scale.animateTo(
                    pressedScale,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = 1800f
                    )
                )
            }
            is PressInteraction.Release, is PressInteraction.Cancel -> {
                if (scale.value > 0.96f) scale.snapTo(0.93f)
                scale.animateTo(
                    1f,
                    spring(dampingRatio = 0.40f, stiffness = Spring.StiffnessMediumLow)
                )
            }
        }
    }
}

/**
 * Animación de expansión horizontal para los BouncyButton / Outlined / Capsule / Chip.
 *
 * Se usan [Animatable] + snapTo forzado en Release, igual que [observeBouncyPress].
 * Eso es lo que garantiza que un tap rápido (press+release en < 100ms) siempre
 * muestre una animación visible: si al soltar la animación de expansión apenas
 * arrancó (value < 40% del target), snapTo al 70% antes de animar de vuelta a 0.
 *
 * Retorna un State<Dp>: internamente es un Animatable expuesto con `.asState()`
 * (método miembro de Animatable, no requiere import adicional).
 */
@Composable
private fun rememberBouncyExpand(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    expandTarget: Dp
): State<Dp> {
    val padding = remember { Animatable(0.dp, Dp.VectorConverter) }

    LaunchedEffect(interactionSource, enabled, expandTarget) {
        if (!enabled || expandTarget <= 0.dp) {
            padding.snapTo(0.dp)
            return@LaunchedEffect
        }
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    padding.animateTo(
                        targetValue = expandTarget,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (padding.value < expandTarget * 0.4f) {
                        padding.snapTo(expandTarget * 0.7f)
                    }
                    padding.animateTo(
                        targetValue = 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
        }
    }
    return padding.asState()
}

/**
 * Modificador clickable con efecto bouncy (scale squish).
 */
@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        observeBouncyPress(interactionSource, scale)
    }
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

// ============================================================
// BouncyButton
// ============================================================

@Composable
fun BouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    expandOnPress: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val extraPad by rememberBouncyExpand(interactionSource, enabled, expandOnPress)

    val layoutDirection = LocalLayoutDirection.current
    val basePadding = ButtonDefaults.ContentPadding
    val contentPadding = PaddingValues(
        start = basePadding.calculateStartPadding(layoutDirection) + extraPad,
        top = basePadding.calculateTopPadding(),
        end = basePadding.calculateEndPadding(layoutDirection) + extraPad,
        bottom = basePadding.calculateBottomPadding()
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        shapes = ButtonDefaults.shapes(),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        modifier = modifier,
        content = content
    )
}

// ============================================================
// BouncyOutlinedButton
// ============================================================

@Composable
fun BouncyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expandOnPress: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val extraPad by rememberBouncyExpand(interactionSource, enabled, expandOnPress)

    val layoutDirection = LocalLayoutDirection.current
    val basePadding = ButtonDefaults.ContentPadding
    val contentPadding = PaddingValues(
        start = basePadding.calculateStartPadding(layoutDirection) + extraPad,
        top = basePadding.calculateTopPadding(),
        end = basePadding.calculateEndPadding(layoutDirection) + extraPad,
        bottom = basePadding.calculateBottomPadding()
    )

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        modifier = modifier,
        content = content
    )
}

// ============================================================
// BouncyIconButton
// ============================================================

/**
 * Icon button con squish al presionar (scale-inward).
 *
 * `expandOnPress` se conserva en la firma por compatibilidad de API.
 * No se usa: los IconButton de M3 no exponen contentPadding.
 */
@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expandOnPress: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        observeBouncyPress(interactionSource, scale, pressedScale = 0.88f)
    }

    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        content = content
    )
}

// ============================================================
// BouncyToggleButton
// ============================================================

/**
 * Wrapper de [ToggleButton] de M3 con el mismo squish que los BouncyIconButton.
 *
 * Usado en los toggle groups (Tidy Up / Summary / Analyze, Fast / Accurate,
 * Auto / Light / Dark / Amoled, Gemini / Groq / Dynamic, etc.) y en el sort
 * row del History sidebar.
 *
 * Estos ToggleButton están repartidos con `Modifier.weight(1f)` dentro de un
 * Row, así que no pueden crecer horizontalmente (el ancho ya está asignado).
 * La única forma de darles feedback al press sin romper el layout es el squish
 * scale, que no afecta el tamaño medido del botón.
 */
@Composable
fun BouncyToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        observeBouncyPress(interactionSource, scale, pressedScale = 0.94f)
    }

    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        content = content
    )
}

// ============================================================
// BouncyCapsule
// ============================================================

@Composable
fun BouncyCapsule(
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    expandOnPress: Dp = 12.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val extraPad by rememberBouncyExpand(interactionSource, enabled = true, expandOnPress)

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp + extraPad),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// ============================================================
// BouncyChip
// ============================================================

@Composable
fun BouncyChip(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    expandOnPress: Dp = 12.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val extraPad by rememberBouncyExpand(interactionSource, enabled = true, expandOnPress)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp + extraPad, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}