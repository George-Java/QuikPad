package com.yg.liquidink.ui.home

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yg.liquidink.core.AppSettings
import com.yg.liquidink.core.RecentDocument
import com.yg.liquidink.core.ThemeMode
import com.yg.liquidink.ui.components.GlassButton
import com.yg.liquidink.ui.components.GlassDialog
import com.yg.liquidink.ui.components.GlassIconButton
import com.yg.liquidink.ui.components.GlassSurface
import com.yg.liquidink.ui.components.GlassTextField
import com.yg.liquidink.ui.components.LiquidBackground
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    recent: List<RecentDocument>,
    settings: AppSettings,
    onNewDocument: () -> Unit,
    onOpenDocument: () -> Unit,
    onOpenRecent: (RecentDocument) -> Unit,
    onRenameRecent: (RecentDocument, String) -> Unit,
    onDeleteRecent: (RecentDocument) -> Unit,
    onForgetRecent: (RecentDocument) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var renameTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var deleteTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val renameFocus = remember { FocusRequester() }

    LaunchedEffect(renameTarget) {
        if (renameTarget != null) {
            delay(120)
            runCatching { renameFocus.requestFocus() }
        }
    }

    LiquidBackground(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 14.dp,
                    bottom = 36.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "brand") {
                    HomeBrandBar(
                        settings = settings,
                        onOpenSettings = onOpenSettings,
                    )
                }

                item(key = "hero") {
                    Column(
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                    ) {
                        GlassChip(
                            text = when (settings.themeMode) {
                                ThemeMode.SYSTEM -> "跟随系统 · 液态玻璃"
                                ThemeMode.LIGHT -> "浅色玻璃 · Liquid Glass"
                                ThemeMode.DARK -> "深色玻璃 · Liquid Glass"
                            },
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "把思绪，\n写进光里。",
                            style = MaterialTheme.typography.displayMedium,
                            color = scheme.onBackground,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Markdown 文档查看与写作。轻如玻璃，专注如纸。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onBackground.copy(alpha = 0.68f),
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            GlassButton(
                                label = "新建文档",
                                icon = Icons.Rounded.Add,
                                primary = true,
                                onClick = onNewDocument,
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(
                                label = "打开文件",
                                icon = Icons.Rounded.FolderOpen,
                                onClick = onOpenDocument,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item(key = "recent-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "最近文档",
                            style = MaterialTheme.typography.titleLarge,
                            color = scheme.onBackground,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (recent.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(scheme.primary.copy(alpha = 0.13f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = recent.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "本地 · 安全",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onBackground.copy(alpha = 0.44f),
                        )
                    }
                }

                if (recent.isEmpty()) {
                    item(key = "empty") {
                        EmptyRecentCard(onOpenDocument = onOpenDocument)
                    }
                } else {
                    items(recent, key = { it.uri }) { document ->
                        RecentDocumentCard(
                            document = document,
                            sizeLabel = if (document.sizeBytes > 0) {
                                Formatter.formatFileSize(context, document.sizeBytes)
                            } else {
                                "未知大小"
                            },
                            relativeTime = DateUtils.getRelativeTimeSpanString(
                                document.lastOpened,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE,
                            ).toString(),
                            onOpen = { onOpenRecent(document) },
                            onRename = {
                                renameValue = document.displayName
                                renameTarget = document
                            },
                            onDelete = { deleteTarget = document },
                            onForget = { onForgetRecent(document) },
                        )
                    }
                }

                item(key = "footer") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .width(42.dp)
                                .height(1.dp)
                                .background(scheme.outlineVariant.copy(alpha = 0.5f)),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Liquid Ink · Markdown，随光流动",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onBackground.copy(alpha = 0.42f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "文件通过系统文档授权在本机读写，不上传云端。",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onBackground.copy(alpha = 0.34f),
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        GlassDialog(
            title = "重命名文档",
            confirmLabel = "重命名",
            dismissLabel = "取消",
            confirmEnabled = renameValue.isNotBlank() && renameValue.trim() != target.displayName,
            onConfirm = {
                val name = renameValue.trim()
                renameTarget = null
                if (name.isNotBlank()) onRenameRecent(target, name)
            },
            onDismiss = { renameTarget = null },
        ) {
            GlassTextField(
                value = renameValue,
                onValueChange = { renameValue = it },
                placeholder = "文件名.md",
                focusRequester = renameFocus,
                onImeAction = {
                    val name = renameValue.trim()
                    if (name.isNotBlank() && name != target.displayName) {
                        renameTarget = null
                        onRenameRecent(target, name)
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "文件名会写回存储；不支持重命名的提供程序会提示使用“另存为”。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    deleteTarget?.let { target ->
        GlassDialog(
            title = "删除文档？",
            message = "“${target.displayName}”将从存储中永久删除，此操作不可撤销。",
            confirmLabel = "删除",
            dismissLabel = "取消",
            destructive = true,
            onConfirm = {
                deleteTarget = null
                onDeleteRecent(target)
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun HomeBrandBar(
    settings: AppSettings,
    onOpenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassSurface(
            modifier = Modifier.size(48.dp),
            cornerRadius = 16.dp,
            tint = scheme.primary.copy(alpha = 0.12f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "Liquid Ink",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "墨流 · 专注写作",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onBackground.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.weight(1f))
        GlassIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = "外观与设置",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun GlassChip(text: String) {
    GlassSurface(
        cornerRadius = 12.dp,
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentDocumentCard(
    document: RecentDocument,
    sizeLabel: String,
    relativeTime: String,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onForget: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val extension = document.displayName.substringAfterLast('.', "md").uppercase()
    val accent = when (extension) {
        "MD", "MARKDOWN" -> scheme.primary
        "TXT" -> scheme.secondary
        else -> scheme.tertiary
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = "打开 ${document.displayName}",
                onClick = onOpen,
            ),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.18f),
                                accent.copy(alpha = 0.06f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = document.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$extension · $sizeLabel · $relativeTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = document.encodingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.46f),
                    maxLines = 1,
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = "文档操作"
                            role = Role.Button
                        },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.width(200.dp),
                ) {
                    HomeMenuItem("打开", Icons.Rounded.Description) {
                        menuOpen = false
                        onOpen()
                    }
                    HomeMenuItem("重命名", Icons.Rounded.Edit) {
                        menuOpen = false
                        onRename()
                    }
                    HomeMenuItem("从最近列表移除", Icons.Rounded.Close) {
                        menuOpen = false
                        onForget()
                    }
                    HorizontalDivider(
                        color = scheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    HomeMenuItem(
                        label = "删除文件",
                        icon = Icons.Rounded.Delete,
                        tint = scheme.error,
                    ) {
                        menuOpen = false
                        onDelete()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium, color = tint) },
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
private fun EmptyRecentCard(onOpenDocument: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 26.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(scheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(
                        text = "还没有最近文档",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = "打开 .md、.markdown 或 .txt，让这里亮起来。",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            GlassButton(
                label = "打开本地文档",
                icon = Icons.Rounded.FolderOpen,
                onClick = onOpenDocument,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
