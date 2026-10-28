package com.yg.liquidink.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yg.liquidink.ui.theme.LocalLiquidGlass
import com.yg.liquidink.ui.theme.LocalLiquidPalette
import kotlin.math.cos
import kotlin.math.sin

@Immutable
private data class GlassDrawState(
    val panelTop: Color,
    val panelBottom: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val sheen: Color,
    val tint: Color,
    val topHighlight: Color,
)

@Composable
fun LiquidBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val config = LocalLiquidGlass.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            !config.glassEnabled -> FlatLiquidBackdrop()
            config.animationsEnabled -> AnimatedLiquidBackdrop(intensity = config.intensity)
            else -> LiquidBackdrop(phase = 0.6f, intensity = config.intensity)
        }
        content()
    }
}

@Composable
private fun FlatLiquidBackdrop() {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.background,
                        scheme.surfaceVariant.copy(alpha = 0.14f),
                        scheme.background,
                    ),
                ),
            ),
    )
}

@Composable
private fun AnimatedLiquidBackdrop(intensity: Float) {
    val transition = rememberInfiniteTransition(label = "liquid-background")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 52_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "liquid-phase",
    )
    LiquidBackdrop(phase = phase, intensity = intensity)
}

@Composable
private fun LiquidBackdrop(phase: Float, intensity: Float) {
    val palette = LocalLiquidPalette.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val backgroundColor = MaterialTheme.colorScheme.background
    val blobAlpha = 0.55f + 0.45f * intensity.coerceIn(0f, 1f)
    val topAura = if (dark) {
        Color.White.copy(alpha = 0.04f)
    } else {
        Color.White.copy(alpha = 0.5f)
    }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val minDimension = minOf(width, height)
        val driftX = sin(phase) * width * 0.055f
        val driftY = cos(phase * 0.8f) * height * 0.035f
        val driftX2 = cos(phase * 0.9f) * width * 0.045f
        val driftY2 = sin(phase * 0.7f) * height * 0.03f

        drawSoftBlob(
            center = Offset(width * 0.06f + driftX, height * 0.12f + driftY),
            radius = minDimension * 0.72f,
            color = palette.mint,
            alphaMultiplier = blobAlpha,
        )
        drawSoftBlob(
            center = Offset(width * 1.02f - driftX2, height * 0.22f + driftY2),
            radius = minDimension * 0.62f,
            color = palette.sky,
            alphaMultiplier = blobAlpha,
        )
        drawSoftBlob(
            center = Offset(width * 0.12f - driftX2, height * 0.92f - driftY),
            radius = minDimension * 0.68f,
            color = palette.peach,
            alphaMultiplier = blobAlpha,
        )
        drawSoftBlob(
            center = Offset(width * 0.88f + driftX, height * 0.82f - driftY2),
            radius = minDimension * 0.56f,
            color = palette.lilac,
            alphaMultiplier = blobAlpha,
        )
        drawSoftBlob(
            center = Offset(width * 0.55f + driftX2 * 0.5f, height * 0.48f),
            radius = minDimension * 0.38f,
            color = palette.gold,
            alphaMultiplier = blobAlpha,
        )

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    topAura,
                    Color.Transparent,
                    backgroundColor.copy(alpha = 0.16f),
                    backgroundColor.copy(alpha = 0.58f),
                ),
                start = Offset(0f, 0f),
                end = Offset(width * 0.9f, height),
            ),
        )

        // A soft diagonal reflection band, like light through a pane of glass.
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = if (dark) 0.025f else 0.14f),
                    Color.Transparent,
                ),
                start = Offset(width * 0.15f, 0f),
                end = Offset(width * 0.85f, height * 0.55f),
            ),
        )
    }
}

private fun DrawScope.drawSoftBlob(
    center: Offset,
    radius: Float,
    color: Color,
    alphaMultiplier: Float,
) {
    if (radius <= 0f) return
    val alpha = alphaMultiplier.coerceIn(0f, 1f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to color.copy(alpha = 0.56f * alpha),
                0.45f to color.copy(alpha = 0.30f * alpha),
                0.75f to color.copy(alpha = 0.10f * alpha),
                1.0f to Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * A translucent, rounded glass panel. On API 26+ we use layered gradients,
 * specular edges and soft shadows instead of a costly real-time backdrop blur;
 * the background itself is already a field of blurred gradients, so the panel
 * reads as frosted glass without stealing frames from typing.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    tint: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val config = LocalLiquidGlass.current
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(cornerRadius)
    if (!config.glassEnabled) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(scheme.surface)
                .background(tint)
                .border(
                    width = 1.dp,
                    color = scheme.outlineVariant.copy(alpha = config.borderAlpha),
                    shape = shape,
                ),
        ) {
            Box(Modifier.padding(contentPadding), content = content)
        }
        return
    }
    val intensity = config.intensity.coerceIn(0f, 1f)
    val highlightFactor = 0.55f + 0.45f * intensity
    val state = GlassDrawState(
        panelTop = scheme.surface.copy(alpha = config.panelTopAlpha),
        panelBottom = scheme.surface.copy(alpha = config.panelBottomAlpha),
        borderTop = if (config.highContrast) {
            scheme.onSurface.copy(alpha = config.borderAlpha)
        } else {
            (if (scheme.background.luminance() < 0.35f) Color.White else Color.White)
                .copy(alpha = config.borderAlpha)
        },
        borderBottom = scheme.outline.copy(alpha = config.borderAlpha * 0.45f),
        sheen = if (scheme.background.luminance() < 0.35f) {
            Color.White.copy(alpha = 0.12f * highlightFactor)
        } else {
            Color.White.copy(alpha = 0.55f * highlightFactor)
        },
        tint = tint,
        topHighlight = if (scheme.background.luminance() < 0.35f) {
            Color.White.copy(alpha = 0.28f * highlightFactor)
        } else {
            Color.White.copy(alpha = 0.85f * highlightFactor)
        },
    )
    val shadowModifier = if (config.enhancedShadows) {
        Modifier.shadow(
            elevation = (8f + 10f * config.intensity.coerceIn(0f, 1f)).dp,
            shape = shape,
            clip = false,
            ambientColor = scheme.primary.copy(alpha = 0.16f),
            spotColor = scheme.primary.copy(alpha = 0.22f),
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(shadowModifier)
            .clip(shape)
            .drawWithCache {
                val corner = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
                val clipSize = Size(size.width, size.height)
                val panelBrush = Brush.verticalGradient(
                    colors = listOf(state.panelTop, state.panelBottom),
                    startY = 0f,
                    endY = size.height,
                )
                val refraction = Brush.radialGradient(
                    colors = listOf(state.sheen, Color.Transparent),
                    center = Offset(size.width * 0.18f, -size.height * 0.2f),
                    radius = size.width * 0.95f,
                )
                val borderBrush = Brush.verticalGradient(
                    colors = listOf(state.borderTop, state.borderBottom),
                )
                val highlightBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        state.topHighlight,
                        Color.Transparent,
                    ),
                )
                onDrawBehind {
                    drawRoundRect(
                        brush = panelBrush,
                        size = clipSize,
                        cornerRadius = corner,
                    )
                    if (state.tint != Color.Transparent) {
                        drawRoundRect(
                            color = state.tint,
                            size = clipSize,
                            cornerRadius = corner,
                        )
                    }
                    drawRoundRect(
                        brush = refraction,
                        size = clipSize,
                        cornerRadius = corner,
                    )
                    if (config.specularHighlights) {
                        val inset = cornerRadius.toPx() * 1.4f
                        drawLine(
                            brush = highlightBrush,
                            start = Offset(inset, 1.15f),
                            end = Offset(size.width - inset, 1.15f),
                            strokeWidth = 1.15.dp.toPx(),
                        )
                    }
                    drawRoundRect(
                        brush = borderBrush,
                        size = clipSize,
                        cornerRadius = corner,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            },
    ) {
        Box(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    val config = LocalLiquidGlass.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && config.animationsEnabled) 0.94f else 1f,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.75f),
        label = "glass-icon-scale",
    )
    val selectedTint = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        Color.Transparent
    }
    GlassSurface(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.42f
            },
        cornerRadius = 16.dp,
        tint = selectedTint,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else tint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val config = LocalLiquidGlass.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && config.animationsEnabled) 0.975f else 1f,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.8f),
        label = "glass-button-scale",
    )
    val contentColor = if (primary) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    GlassSurface(
        modifier = modifier
            .defaultMinSize(minHeight = 54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.42f
            },
        cornerRadius = 18.dp,
        tint = if (primary) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            Color.Transparent
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (primary) MaterialTheme.colorScheme.primary else contentColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (primary) MaterialTheme.colorScheme.primary else contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun <T> GlassSegmentedControl(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val config = LocalLiquidGlass.current
    val colorDuration = if (config.animationsEnabled) 260 else 0
    val contentDuration = if (config.animationsEnabled) 220 else 0
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.26f))
            .border(
                width = 1.dp,
                color = scheme.outlineVariant.copy(alpha = 0.28f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) {
                    scheme.surface.copy(alpha = 0.92f)
                } else {
                    Color.Transparent
                },
                animationSpec = tween(colorDuration, easing = FastOutSlowInEasing),
                label = "segment-background",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                animationSpec = tween(contentDuration),
                label = "segment-color",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(background)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelected(option) },
                    )
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(focusModifier),
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(scheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(
                        width = 1.dp,
                        color = scheme.outline.copy(alpha = 0.35f),
                        shape = shape,
                    )
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
fun GlassDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissLabel: String? = "取消",
    neutralLabel: String? = null,
    onNeutral: (() -> Unit)? = null,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlassSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                cornerRadius = 28.dp,
                contentPadding = PaddingValues(24.dp),
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (message != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    content()
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissLabel != null) {
                            TextButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text(dismissLabel)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        if (neutralLabel != null && onNeutral != null) {
                            TextButton(
                                onClick = onNeutral,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text(neutralLabel)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        TextButton(
                            onClick = onConfirm,
                            enabled = confirmEnabled,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (destructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            ),
                        ) {
                            Text(confirmLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlassVerticalDivider(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(height)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
    )
}

@Composable
fun GlassHorizontalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
    )
}

private fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue
