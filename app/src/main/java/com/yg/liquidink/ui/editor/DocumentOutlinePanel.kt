package com.yg.liquidink.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yg.liquidink.markdown.OutlineEntry
import com.yg.liquidink.ui.components.GlassSurface
import com.yg.liquidink.ui.theme.LocalLiquidGlass
import com.yg.liquidink.ui.theme.LiquidGlassConfig

/** A one-shot command passed to MarkdownPreview; the nonce replays the same target. */
data class OutlineScrollRequest(
    val blockIndex: Int,
    val nonce: Long,
)

@Composable
fun DocumentOutlinePanel(
    visible: Boolean,
    entries: List<OutlineEntry>,
    isParsing: Boolean,
    motionEnabled: Boolean,
    onSelect: (OutlineEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val baseGlass = LocalLiquidGlass.current
    val readableGlass = remember(baseGlass) {
        baseGlass.copy(
            panelTopAlpha = maxOf(baseGlass.panelTopAlpha, 0.97f),
            panelBottomAlpha = maxOf(baseGlass.panelBottomAlpha, 0.93f),
            borderAlpha = maxOf(baseGlass.borderAlpha, 0.72f),
        )
    }
    val enter = if (motionEnabled) {
        fadeIn(tween(150)) + scaleIn(initialScale = 0.97f, animationSpec = tween(180))
    } else {
        EnterTransition.None
    }
    val exit = if (motionEnabled) {
        fadeOut(tween(120)) + scaleOut(targetScale = 0.985f, animationSpec = tween(140))
    } else {
        ExitTransition.None
    }

    AnimatedVisibility(visible = visible, enter = enter, exit = exit) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val panelWidth = minOf(maxWidth * 0.86f, 360.dp)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.24f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "关闭目录",
                        onClick = onDismiss,
                    )
                    .clearAndSetSemantics { },
            )
            CompositionLocalProvider(LocalLiquidGlass provides readableGlass) {
                GlassSurface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(panelWidth)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(vertical = 10.dp, horizontal = 10.dp),
                    cornerRadius = 28.dp,
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        OutlineHeader(
                            count = entries.size,
                            onDismiss = onDismiss,
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                        when {
                            entries.isNotEmpty() -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 10.dp,
                                        end = 10.dp,
                                        top = 8.dp,
                                        bottom = 20.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    items(entries, key = { it.blockIndex }) { entry ->
                                        OutlineRow(entry = entry, onClick = { onSelect(entry) })
                                    }
                                }
                            }
                            isParsing -> {
                                OutlineEmptyState(text = "正在解析标题…")
                            }
                            else -> {
                                OutlineEmptyState(text = "当前文档没有 H1–H3 标题")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineHeader(count: Int, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "目录",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
            )
            Text(
                text = if (count > 0) "$count 个标题" else "标题大纲",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics {
                    contentDescription = "关闭目录"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun OutlineRow(entry: OutlineEntry, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val startPadding = when (entry.level) {
        1 -> 12.dp
        2 -> 28.dp
        else -> 44.dp
    }
    val textStyle = when (entry.level) {
        1 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        2 -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        else -> MaterialTheme.typography.bodySmall
    }
    val textColor = when (entry.level) {
        1 -> scheme.onSurface
        2 -> scheme.onSurface.copy(alpha = 0.84f)
        else -> scheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = "跳转到 ${entry.title}",
                onClick = onClick,
            )
            .padding(
                start = startPadding,
                end = 12.dp,
                top = 10.dp,
                bottom = 10.dp,
            )
            .semantics {
                contentDescription = "H${entry.level}，${entry.title}，跳转"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(if (entry.level == 1) 3.dp else 2.dp)
                .height(if (entry.level == 1) 24.dp else 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (entry.level == 1) {
                        scheme.primary
                    } else {
                        scheme.outlineVariant.copy(alpha = 0.8f)
                    },
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = entry.title,
            style = textStyle,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OutlineEmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
