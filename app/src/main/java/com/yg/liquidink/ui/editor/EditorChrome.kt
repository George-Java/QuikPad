package com.yg.liquidink.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StrikethroughS
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yg.liquidink.core.TextStats
import com.yg.liquidink.markdown.MarkdownFormat
import com.yg.liquidink.ui.components.GlassIconButton
import com.yg.liquidink.ui.components.GlassSegmentedControl
import com.yg.liquidink.ui.components.GlassSurface
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EditorMode(val label: String) {
    SOURCE("源码"),
    LIVE("实时"),
    PREVIEW("预览"),
}

@Composable
fun EditorTopBar(
    documentName: String,
    isDirty: Boolean,
    isSaving: Boolean,
    isParsing: Boolean,
    isLoading: Boolean,
    lastSavedAt: Long?,
    isLargeDocument: Boolean,
    onBack: () -> Unit,
    onOpenOutline: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val status = when {
        isLoading -> "正在打开"
        isSaving -> "保存中…"
        isDirty -> "有未保存更改"
        else -> "已保存"
    }
    val statusColor = when {
        isLoading || isSaving -> scheme.secondary
        isDirty -> scheme.tertiary
        else -> scheme.primary
    }
    val savedTime = remember(lastSavedAt) {
        lastSavedAt?.let {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
        }
    }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = documentName,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(statusColor),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        if (savedTime != null && !isDirty && !isSaving) {
                            Text(
                                text = " · $savedTime",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                        }
                        if (isLargeDocument) {
                            Text(
                                text = " · 大文件",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.tertiary,
                                maxLines = 1,
                            )
                        }
                        if (isParsing && !isLoading) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.2.dp,
                                color = scheme.primary.copy(alpha = 0.72f),
                            )
                        }
                    }
                }
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                    contentDescription = "目录大纲",
                    onClick = onOpenOutline,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.width(4.dp))
                if (isSaving) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = scheme.primary,
                        )
                    }
                } else {
                    GlassIconButton(
                        icon = Icons.Rounded.Save,
                        contentDescription = "保存",
                        onClick = onSave,
                        modifier = Modifier.size(44.dp),
                        enabled = !isLoading,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box {
                    GlassIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "更多文档操作",
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(44.dp),
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.width(210.dp),
                    ) {
                        EditorMenuItem(
                            label = "另存为",
                            icon = Icons.Rounded.Save,
                            onClick = {
                                menuExpanded = false
                                onSaveAs()
                            },
                        )
                        EditorMenuItem(
                            label = "重命名",
                            icon = Icons.Rounded.Edit,
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        EditorMenuItem(
                            label = "外观与设置",
                            icon = Icons.Rounded.Settings,
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            },
                        )
                        EditorMenuItem(
                            label = "删除文档",
                            icon = Icons.Rounded.Delete,
                            tint = scheme.error,
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = tint,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
    )
}

@Composable
fun EditorBottomBar(
    mode: EditorMode,
    onModeSelected: (EditorMode) -> Unit,
    stats: TextStats,
    canUndo: Boolean,
    canRedo: Boolean,
    formatOpen: Boolean,
    focusMode: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFind: () -> Unit,
    onFormat: () -> Unit,
    onFocus: () -> Unit,
    onStartEditing: () -> Unit,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val chars = remember(stats.words) {
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(stats.words)
    }
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column {
            GlassSegmentedControl(
                options = EditorMode.entries,
                selected = mode,
                onSelected = onModeSelected,
                label = { it.label },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$chars 字 · " + if (stats.readingMinutes > 0) "${stats.readingMinutes} 分钟" else "0 分钟",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (mode == EditorMode.PREVIEW) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.primary.copy(alpha = 0.12f))
                            .clickable(role = Role.Button, onClick = onStartEditing)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "编辑",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary,
                        )
                    }
                    EditorChromeAction(
                        icon = Icons.Rounded.Tune,
                        label = "阅读外观",
                        onClick = onOpenAppearance,
                    )
                } else {
                    EditorChromeAction(
                        icon = Icons.AutoMirrored.Rounded.Undo,
                        label = "撤销",
                        enabled = canUndo,
                        onClick = onUndo,
                    )
                    EditorChromeAction(
                        icon = Icons.AutoMirrored.Rounded.Redo,
                        label = "重做",
                        enabled = canRedo,
                        onClick = onRedo,
                    )
                    EditorChromeAction(
                        icon = Icons.Rounded.Search,
                        label = "查找与替换",
                        onClick = onFind,
                    )
                    FormatChromeAction(
                        selected = formatOpen,
                        onClick = onFormat,
                    )
                }
                EditorChromeAction(
                    icon = Icons.Rounded.Fullscreen,
                    label = "进入专注模式",
                    selected = focusMode,
                    onClick = onFocus,
                )
            }
        }
    }
}

@Composable
private fun EditorChromeAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent,
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.32f)
                selected -> scheme.primary
                else -> scheme.onSurfaceVariant
            },
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun FormatChromeAction(selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "Markdown 语法快捷栏"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Aa",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

data class FormatActionItem(
    val format: MarkdownFormat,
    val label: String,
    val icon: ImageVector? = null,
    val textGlyph: String? = null,
)

private val formatActions = listOf(
    FormatActionItem(MarkdownFormat.HEADING_1, "一级标题", textGlyph = "H1"),
    FormatActionItem(MarkdownFormat.HEADING_2, "二级标题", textGlyph = "H2"),
    FormatActionItem(MarkdownFormat.HEADING_3, "三级标题", textGlyph = "H3"),
    FormatActionItem(MarkdownFormat.BOLD, "粗体", Icons.Rounded.FormatBold),
    FormatActionItem(MarkdownFormat.ITALIC, "斜体", Icons.Rounded.FormatItalic),
    FormatActionItem(MarkdownFormat.STRIKETHROUGH, "删除线", Icons.Rounded.StrikethroughS),
    FormatActionItem(MarkdownFormat.INLINE_CODE, "行内代码", Icons.Rounded.Code),
    FormatActionItem(MarkdownFormat.BULLET_LIST, "无序列表", Icons.AutoMirrored.Rounded.FormatListBulleted),
    FormatActionItem(MarkdownFormat.NUMBERED_LIST, "有序列表", Icons.Rounded.FormatListNumbered),
    FormatActionItem(MarkdownFormat.TASK_LIST, "任务列表", Icons.Rounded.CheckBox),
    FormatActionItem(MarkdownFormat.QUOTE, "引用", Icons.Rounded.FormatQuote),
    FormatActionItem(MarkdownFormat.CODE_BLOCK, "代码块", Icons.Rounded.DataObject),
    FormatActionItem(MarkdownFormat.TABLE, "表格", Icons.Rounded.TableChart),
    FormatActionItem(MarkdownFormat.LINK, "链接", Icons.Rounded.Link),
)

@Composable
fun EditorFormatBar(
    onFormat: (MarkdownFormat) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Markdown 快捷语法",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "收起语法快捷栏",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(formatActions, key = { it.format.name }) { item ->
                    FormatActionChip(item = item, onClick = { onFormat(item.format) })
                }
            }
        }
    }
}

@Composable
private fun FormatActionChip(item: FormatActionItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.24f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = item.label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = scheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = item.textGlyph.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
            )
        }
    }
}

@Composable
fun FocusModeExitButton(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surface.copy(alpha = 0.78f))
            .clickable(role = Role.Button, onClick = onExit)
            .semantics {
                contentDescription = "退出专注模式"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.FullscreenExit,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun PaneHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
