package com.yg.liquidink.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yg.liquidink.core.AppSettings
import com.yg.liquidink.core.GlassIntensity
import com.yg.liquidink.core.PerformanceMode
import com.yg.liquidink.core.ReaderBackground
import com.yg.liquidink.core.ThemeMode
import com.yg.liquidink.ui.components.GlassIconButton
import com.yg.liquidink.ui.components.GlassSegmentedControl
import com.yg.liquidink.ui.components.GlassSurface
import com.yg.liquidink.ui.components.LiquidBackground
import com.yg.liquidink.ui.theme.LocalLiquidGlass
import com.yg.liquidink.ui.theme.LiquidGlassConfig
import com.yg.liquidink.ui.theme.ParchmentPaperBottom
import com.yg.liquidink.ui.theme.ParchmentPaperEdge
import com.yg.liquidink.ui.theme.ParchmentPaperTop
import com.yg.liquidink.ui.theme.ParchmentReaderColors

internal const val SETTINGS_PANEL_TOP_ALPHA = 0.97f
internal const val SETTINGS_PANEL_BOTTOM_ALPHA = 0.93f
internal const val SETTINGS_PANEL_BORDER_ALPHA = 0.72f

/**
 * Settings must remain readable over the liquid background. The adaptive token
 * can be flatter or more transparent elsewhere, but the settings surface gets a
 * minimum panel opacity and border contrast.
 */
fun readableSettingsGlass(base: LiquidGlassConfig): LiquidGlassConfig = base.copy(
    panelTopAlpha = maxOf(base.panelTopAlpha, SETTINGS_PANEL_TOP_ALPHA),
    panelBottomAlpha = maxOf(base.panelBottomAlpha, SETTINGS_PANEL_BOTTOM_ALPHA),
    borderAlpha = maxOf(base.borderAlpha, SETTINGS_PANEL_BORDER_ALPHA),
)

/**
 * Full-screen settings destination. It is intentionally not a ModalBottomSheet:
 * the root renders only this destination while settings are open, so Home/Editor
 * content cannot overlap or bleed through the glass.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAutoSaveEnabledChange: (Boolean) -> Unit,
    onAutoSaveIntervalChange: (Int) -> Unit,
    onPerformanceModeChange: (PerformanceMode) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    onReaderBackgroundChange: (ReaderBackground) -> Unit,
    onGlassEffectsEnabledChange: (Boolean) -> Unit,
    onGlassIntensityChange: (GlassIntensity) -> Unit,
    onAnimationsEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    val scheme = MaterialTheme.colorScheme
    val baseGlass = LocalLiquidGlass.current
    val motion = baseGlass.animationsEnabled
    val readableGlass = remember(baseGlass) { readableSettingsGlass(baseGlass) }

    LiquidBackground(modifier = modifier.fillMaxSize()) {
        // A light veil reduces the contrast range of the animated background.
        // It is below the settings card, never over its text.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scheme.background.copy(alpha = 0.24f),
                            scheme.background.copy(alpha = 0.10f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            CompositionLocalProvider(LocalLiquidGlass provides readableGlass) {
                SettingsTopBar(onDismiss = onDismiss)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                CompositionLocalProvider(LocalLiquidGlass provides readableGlass) {
                    GlassSurface(
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 30.dp,
                        tint = scheme.surface.copy(alpha = 0.14f),
                        contentPadding = PaddingValues(
                            start = 22.dp,
                            end = 22.dp,
                            top = 20.dp,
                            bottom = 20.dp,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = "偏好设置",
                                style = MaterialTheme.typography.headlineSmall,
                                color = scheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "所有更改立即生效，并只保存在本机。",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )

                            SettingsDivider()
                            SettingsLabel("阅读外观")
                            Text(
                                text = "独立于全局深浅色的文档背景，仅影响阅读页。",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            GlassSegmentedControl(
                                options = ReaderBackground.entries,
                                selected = settings.readerBackground,
                                onSelected = onReaderBackgroundChange,
                                label = {
                                    when (it) {
                                        ReaderBackground.DEFAULT -> "默认"
                                        ReaderBackground.PARCHMENT -> "羊皮纸"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            ReaderBackgroundPreview(background = settings.readerBackground)
                            Spacer(Modifier.height(14.dp))
                            SettingsSwitchRow(
                                title = "液态玻璃效果",
                                description = "关闭后使用清晰、平面的面板；阅读背景仍然可选。",
                                checked = settings.glassEffectsEnabled,
                                onCheckedChange = onGlassEffectsEnabledChange,
                            )
                            AnimatedVisibility(
                                visible = settings.glassEffectsEnabled,
                                enter = if (motion) {
                                    fadeIn() + expandVertically()
                                } else {
                                    EnterTransition.None
                                },
                                exit = if (motion) {
                                    fadeOut() + shrinkVertically()
                                } else {
                                    ExitTransition.None
                                },
                            ) {
                                Column {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = "玻璃强度",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = scheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    GlassSegmentedControl(
                                        options = GlassIntensity.entries,
                                        selected = settings.glassIntensity,
                                        onSelected = onGlassIntensityChange,
                                        label = {
                                            when (it) {
                                                GlassIntensity.LOW -> "低"
                                                GlassIntensity.MEDIUM -> "中"
                                                GlassIntensity.HIGH -> "高"
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            ReaderGlassPreview(
                                glass = baseGlass,
                                intensity = settings.glassIntensity,
                                enabled = settings.glassEffectsEnabled,
                            )
                            Spacer(Modifier.height(6.dp))
                            SettingsSwitchRow(
                                title = "界面动效",
                                description = "关闭后页面切换、目录和按钮反馈将立即完成；系统“减少动态效果”优先。",
                                checked = settings.animationsEnabled,
                                onCheckedChange = onAnimationsEnabledChange,
                            )

                            SettingsDivider()
                            SettingsLabel("主题")
                            GlassSegmentedControl(
                                options = ThemeMode.entries,
                                selected = settings.themeMode,
                                onSelected = onThemeModeChange,
                                label = {
                                    when (it) {
                                        ThemeMode.SYSTEM -> "系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            SettingsDivider()
                            SettingsSwitchRow(
                                title = "自动保存",
                                description = "停止输入一段时间后，将改动写回原文件。",
                                checked = settings.autoSaveEnabled,
                                onCheckedChange = onAutoSaveEnabledChange,
                            )
                            AnimatedVisibility(
                                visible = settings.autoSaveEnabled,
                                enter = if (motion) {
                                    fadeIn() + expandVertically()
                                } else {
                                    EnterTransition.None
                                },
                                exit = if (motion) {
                                    fadeOut() + shrinkVertically()
                                } else {
                                    ExitTransition.None
                                },
                            ) {
                                Column {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = "自动保存间隔",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = scheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        listOf(5, 15, 30, 60).forEach { seconds ->
                                            IntervalChip(
                                                label = "${seconds}s",
                                                selected = settings.autoSaveIntervalSeconds == seconds,
                                                onClick = { onAutoSaveIntervalChange(seconds) },
                                            )
                                        }
                                    }
                                }
                            }

                            SettingsDivider()
                            SettingsLabel("渲染性能")
                            GlassSegmentedControl(
                                options = PerformanceMode.entries,
                                selected = settings.performanceMode,
                                onSelected = onPerformanceModeChange,
                                label = {
                                    when (it) {
                                        PerformanceMode.AUTO -> "自动"
                                        PerformanceMode.FULL -> "完整"
                                        PerformanceMode.REDUCED -> "省电"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = when (settings.performanceMode) {
                                    PerformanceMode.AUTO ->
                                        "低内存或旧设备自动简化玻璃动效与阴影；其他设备使用完整液态玻璃。"
                                    PerformanceMode.FULL ->
                                        "始终启用玻璃高光与流动背景，适合中高端设备。"
                                    PerformanceMode.REDUCED ->
                                        "冻结背景动效并使用更平的半透明面板，优先保证输入与滚动。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )

                            SettingsDivider()
                            SettingsSwitchRow(
                                title = "增强文字对比度",
                                description = "提高玻璃面板不透明度与边框对比，适合强光环境。",
                                checked = settings.highContrast,
                                onCheckedChange = onHighContrastChange,
                            )

                            SettingsDivider()
                            Text(
                                text = "文件编码",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "打开时自动识别并保留 UTF-8、UTF-16 与 GB18030；新文档默认 UTF-8。",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )

                            Spacer(Modifier.height(22.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(scheme.outlineVariant.copy(alpha = 0.35f)),
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Liquid Ink 1.0.0  ·  Android 8.0+",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onDismiss,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "外观与写作",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(
                    text = "完成",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ReaderGlassPreview(
    glass: LiquidGlassConfig,
    intensity: GlassIntensity,
    enabled: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val intensityLabel = when (intensity) {
        GlassIntensity.LOW -> "低"
        GlassIntensity.MEDIUM -> "中"
        GlassIntensity.HIGH -> "高"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.34f),
                        scheme.tertiary.copy(alpha = 0.22f),
                        scheme.secondary.copy(alpha = 0.3f),
                    ),
                ),
            ),
    ) {
        CompositionLocalProvider(LocalLiquidGlass provides glass) {
            GlassSurface(
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 18.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        text = if (enabled) "玻璃效果预览" else "玻璃效果已关闭",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (enabled) "当前强度：$intensityLabel" else "平面、清晰、无折射",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBackgroundPreview(background: ReaderBackground) {
    val shape = RoundedCornerShape(18.dp)
    if (background == ReaderBackground.PARCHMENT) {
        MaterialTheme(
            colorScheme = ParchmentReaderColors,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(ParchmentPaperTop, ParchmentPaperBottom),
                        ),
                    )
                    .border(1.dp, ParchmentPaperEdge.copy(alpha = 0.75f), shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "章节标题 · H2",
                    style = MaterialTheme.typography.titleMedium,
                    color = ParchmentReaderColors.onSurface,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "暖色低蓝光纸面，正文与链接保持清晰对比。",
                    style = MaterialTheme.typography.bodySmall,
                    color = ParchmentReaderColors.onSurfaceVariant,
                )
            }
        }
    } else {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "章节标题 · H2",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "默认阅读背景会跟随当前应用主题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f),
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = title
                role = Role.Switch
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            ),
        )
    }
}

@Composable
private fun IntervalChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    scheme.primary.copy(alpha = 0.16f)
                } else {
                    scheme.surfaceVariant.copy(alpha = 0.26f)
                },
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp)
            .semantics {
                contentDescription = "自动保存间隔 $label"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}
