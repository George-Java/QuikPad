package com.yg.liquidink.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.yg.liquidink.ui.components.GlassSurface

@Composable
fun FindReplaceBar(
    query: String,
    onQueryChange: (String) -> Unit,
    replacement: String,
    onReplacementChange: (String) -> Unit,
    caseSensitive: Boolean,
    onCaseSensitiveChange: (Boolean) -> Unit,
    currentIndex: Int,
    resultCount: Int,
    truncated: Boolean,
    isSearching: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val queryFocus = remember { FocusRequester() }
    val matchLabel = when {
        query.isEmpty() -> "查找"
        isSearching -> "搜索中…"
        resultCount == 0 -> "无结果"
        currentIndex >= 0 -> "${currentIndex + 1}/$resultCount${if (truncated) "+" else ""}"
        else -> "$resultCount${if (truncated) "+" else ""} 处"
    }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 8.dp,
            top = 4.dp,
            bottom = 4.dp,
        ),
    ) {
        androidx.compose.foundation.layout.Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(queryFocus),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "在文档中查找",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant.copy(alpha = 0.64f),
                                )
                            }
                            inner()
                        }
                    },
                )
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = scheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Text(
                        text = matchLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(
                    onClick = onPrevious,
                    enabled = resultCount > 0,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "上一个匹配",
                        modifier = Modifier.size(21.dp),
                    )
                }
                IconButton(
                    onClick = onNext,
                    enabled = resultCount > 0,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "下一个匹配",
                        modifier = Modifier.size(21.dp),
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭查找",
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(scheme.outlineVariant.copy(alpha = 0.25f)),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (caseSensitive) {
                                scheme.primary.copy(alpha = 0.16f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        )
                        .clickable {
                            onCaseSensitiveChange(!caseSensitive)
                        }
                        .semantics {
                            contentDescription = if (caseSensitive) {
                                "区分大小写，已开启"
                            } else {
                                "区分大小写，已关闭"
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Aa",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (caseSensitive) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    value = replacement,
                    onValueChange = onReplacementChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (replacement.isEmpty()) {
                                Text(
                                    text = "替换为…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant.copy(alpha = 0.64f),
                                )
                            }
                            inner()
                        }
                    },
                )
                TextButton(
                    onClick = onReplace,
                    enabled = resultCount > 0,
                ) {
                    Text("替换", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onReplaceAll,
                    enabled = resultCount > 0,
                ) {
                    Text("全部", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { queryFocus.requestFocus() }
    }
}
