package com.yg.liquidink.ui.editor

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.yg.liquidink.markdown.MdBlock
import com.yg.liquidink.markdown.MdFootnoteDefinition
import com.yg.liquidink.markdown.MdInline
import com.yg.liquidink.markdown.MdListItem
import com.yg.liquidink.markdown.ParsedMarkdown
import com.yg.liquidink.markdown.TableAlignment
import com.yg.liquidink.ui.components.GlassButton
import com.yg.liquidink.ui.components.GlassDialog
import com.yg.liquidink.ui.markdown.InlineTextColors
import com.yg.liquidink.ui.markdown.MarkdownInlineText
import com.yg.liquidink.ui.markdown.rememberInlineTextColors
import com.yg.liquidink.ui.theme.LocalLiquidGlass
import java.io.File

private const val MAX_RENDERED_TABLE_COLUMNS = 32

@Composable
fun MarkdownPreview(
    parsed: ParsedMarkdown,
    baseUri: String?,
    isParsing: Boolean,
    onEditBlock: (Int) -> Unit,
    onToggleTask: (Int, Int) -> Unit,
    onLinkClick: (String) -> Unit,
    onStartWriting: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    scrollRequest: OutlineScrollRequest? = null,
    onScrollRequestConsumed: (Long) -> Unit = {},
) {
    val colors = rememberInlineTextColors()
    val listState = rememberLazyListState()
    val motionEnabled = LocalLiquidGlass.current.animationsEnabled
    val displayItems = remember(parsed.blocks) { buildPreviewDisplayItems(parsed.blocks) }
    val displayIndexes = remember(displayItems) { displayIndexByBlock(displayItems) }
    var selectedFootnote by remember { mutableStateOf<MdFootnoteDefinition?>(null) }

    LaunchedEffect(scrollRequest?.nonce, displayItems.size) {
        val request = scrollRequest ?: return@LaunchedEffect
        if (parsed.blocks.isEmpty()) return@LaunchedEffect
        val index = displayIndexes[request.blockIndex]
            ?.coerceIn(0, displayItems.lastIndex)
            ?: request.blockIndex.coerceIn(0, displayItems.lastIndex)
        try {
            if (motionEnabled) {
                listState.animateScrollToItem(index)
            } else {
                listState.scrollToItem(index)
            }
        } finally {
            onScrollRequestConsumed(request.nonce)
        }
    }

    Box(modifier.fillMaxSize()) {
        when {
            parsed.blocks.isEmpty() && parsed.footnotes.isEmpty() && isParsing -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "正在排版…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            parsed.blocks.isEmpty() && parsed.footnotes.isEmpty() -> {
                EmptyPreview(onStartWriting = onStartWriting)
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("markdown-preview"),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(displayItems) { index, displayItem ->
                        val block = displayItem.block
                        PreviewBlock(
                            block = block,
                            index = index,
                            baseUri = baseUri,
                            colors = colors,
                            onEditBlock = onEditBlock,
                            onToggleTask = onToggleTask,
                            onLinkClick = onLinkClick,
                            onFootnoteClick = { label ->
                                selectedFootnote = parsed.footnotes.firstOrNull { it.label == label }
                                    ?: MdFootnoteDefinition(label, emptyList(), 0, 0)
                            },
                        )
                    }
                    if (parsed.footnotes.isNotEmpty()) {
                        item(key = "footnote-section") {
                            FootnoteSection(
                                footnotes = parsed.footnotes,
                                colors = colors,
                                onLinkClick = onLinkClick,
                                onFootnoteClick = { footnote -> selectedFootnote = footnote },
                            )
                        }
                    }
                }
            }
        }
    }

    selectedFootnote?.let { footnote ->
        GlassDialog(
            title = "脚注 · ${footnote.label}",
            confirmLabel = "关闭",
            dismissLabel = null,
            onConfirm = { selectedFootnote = null },
            onDismiss = { selectedFootnote = null },
        ) {
            if (footnote.inlines.isEmpty()) {
                Text(
                    text = "没有找到这条脚注的定义。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SelectionContainer {
                    MarkdownInlineText(
                        inlines = footnote.inlines,
                        colors = colors,
                        onLinkClick = onLinkClick,
                        onFootnoteClick = { label ->
                            selectedFootnote = parsed.footnotes.firstOrNull { it.label == label }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewBlock(
    block: MdBlock,
    index: Int,
    baseUri: String?,
    colors: InlineTextColors,
    onEditBlock: (Int) -> Unit,
    onToggleTask: (Int, Int) -> Unit,
    onLinkClick: (String) -> Unit,
    onFootnoteClick: (String) -> Unit,
) {
    when (block) {
        is MdBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 31.sp,
                    lineHeight = 39.sp,
                    fontWeight = FontWeight.Bold,
                )
                2 -> MaterialTheme.typography.headlineMedium
                3 -> MaterialTheme.typography.headlineSmall
                4 -> MaterialTheme.typography.titleLarge
                5 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            val topPadding = when (block.level) {
                1 -> 18.dp
                2 -> 12.dp
                else -> 4.dp
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding)
                    .sourceJump(block.start, onEditBlock)
                    .semantics { heading() },
            ) {
                if (block.level == 1) {
                    Box(
                        Modifier
                            .width(34.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                                    ),
                                ),
                            ),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                SelectionContainer {
                    MarkdownInlineText(
                        inlines = block.inlines,
                        style = style,
                        colors = colors.copy(text = MaterialTheme.colorScheme.onSurface),
                        onLinkClick = onLinkClick,
                        onFootnoteClick = onFootnoteClick,
                    )
                }
            }
        }

        is MdBlock.Paragraph -> {
            SelectionContainer {
                MarkdownInlineText(
                    inlines = block.inlines,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sourceJump(block.start, onEditBlock),
                    style = MaterialTheme.typography.bodyLarge,
                    colors = colors,
                    onLinkClick = onLinkClick,
                    onFootnoteClick = onFootnoteClick,
                )
            }
        }

        is MdBlock.Quote -> {
            val interaction = remember { MutableInteractionSource() }
            val quoteBar = Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
                    .drawBehind {
                        drawRect(
                            brush = quoteBar,
                            size = Size(width = 3.dp.toPx(), height = size.height),
                        )
                    }
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClickLabel = "转到源码",
                        onClick = { onEditBlock(block.start) },
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 19.dp,
                        end = 16.dp,
                        top = 14.dp,
                        bottom = 14.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    block.lines.forEach { line ->
                        if (line.isEmpty()) {
                            Spacer(Modifier.height(3.dp))
                        } else {
                            SelectionContainer {
                                MarkdownInlineText(
                                    inlines = line,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = FontStyle.Italic,
                                    ),
                                    colors = colors.copy(
                                        text = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    onLinkClick = onLinkClick,
                                    onFootnoteClick = onFootnoteClick,
                                )
                            }
                        }
                    }
                }
            }
        }

        is MdBlock.ListBlock -> {
            Column(Modifier.fillMaxWidth()) {
                block.items.forEachIndexed { itemIndex, item ->
                    ListItemRow(
                        item = item,
                        ordered = block.ordered,
                        itemIndex = itemIndex,
                        startNumber = block.startNumber,
                        blockStart = block.start,
                        colors = colors,
                        onEditBlock = onEditBlock,
                        onToggleTask = onToggleTask,
                        onLinkClick = onLinkClick,
                        onFootnoteClick = onFootnoteClick,
                    )
                }
            }
        }

        is MdBlock.CodeBlock -> {
            MarkdownCodeBlock(
                code = block.code,
                language = block.language,
                closed = block.closed,
            )
        }

        is MdBlock.Table -> {
            MarkdownTableBlock(
                block = block,
                colors = colors,
                onLinkClick = onLinkClick,
                onFootnoteClick = onFootnoteClick,
            )
        }

        is MdBlock.Image -> {
            MarkdownImageBlock(
                block = block,
                baseUri = baseUri,
            )
        }

        is MdBlock.ThematicBreak -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.48f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun ListItemRow(
    item: MdListItem,
    ordered: Boolean,
    itemIndex: Int,
    startNumber: Int,
    blockStart: Int,
    colors: InlineTextColors,
    onEditBlock: (Int) -> Unit,
    onToggleTask: (Int, Int) -> Unit,
    onLinkClick: (String) -> Unit,
    onFootnoteClick: (String) -> Unit,
) {
    val indent = (item.level.coerceAtMost(6) * 18).dp
    val markerText = when {
        item.checked != null -> ""
        ordered -> "${item.number ?: (startNumber + itemIndex)}."
        item.level % 2 == 0 -> "•"
        else -> "◦"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (item.checked != null) {
            TaskCheckbox(
                checked = item.checked,
                enabled = item.taskMarkerStart != null && item.taskMarkerEnd != null,
                onCheckedChange = {
                    val start = item.taskMarkerStart ?: return@TaskCheckbox
                    val end = item.taskMarkerEnd ?: return@TaskCheckbox
                    onToggleTask(start, end)
                },
            )
        } else {
            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(28.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = markerText,
                    color = if (ordered) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (ordered) FontWeight.Medium else FontWeight.Normal,
                    ),
                )
            }
        }
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (item.checked != null) 4.dp else 0.dp)
                .sourceJump(blockStart, onEditBlock),
        ) {
            MarkdownInlineText(
                inlines = item.inlines,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = if (item.checked == true) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.64f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (item.checked == true) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                ),
                colors = colors,
                onLinkClick = onLinkClick,
                onFootnoteClick = onFootnoteClick,
            )
        }
    }
}

@Composable
private fun TaskCheckbox(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .semantics {
                contentDescription = if (checked) "已完成任务" else "未完成任务"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(21.dp)
                .clip(shape)
                .background(if (checked) scheme.primary else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (checked) scheme.primary else scheme.outline,
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun MarkdownTableBlock(
    block: MdBlock.Table,
    colors: InlineTextColors,
    onLinkClick: (String) -> Unit,
    onFootnoteClick: (String) -> Unit,
) {
    val columnCount = maxOf(
        block.headers.size,
        block.alignments.size,
        block.rows.maxOfOrNull { it.size } ?: 0,
    )
    if (columnCount == 0) return
    val renderedColumnCount = columnCount.coerceAtMost(MAX_RENDERED_TABLE_COLUMNS)
    val cellWidth = 148.dp
    val shape = RoundedCornerShape(16.dp)
    val scheme = MaterialTheme.colorScheme
    val horizontal = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontal),
    ) {
        Column(
            modifier = Modifier
                .width(cellWidth * renderedColumnCount.toFloat())
                .clip(shape)
                .border(
                    width = 1.dp,
                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                    shape = shape,
                ),
        ) {
            TableRow(
                cells = block.headers,
                alignments = block.alignments,
                columnCount = renderedColumnCount,
                cellWidth = cellWidth,
                header = true,
                colors = colors,
                onLinkClick = onLinkClick,
                onFootnoteClick = onFootnoteClick,
            )
            block.rows.forEachIndexed { rowIndex, row ->
                TableRow(
                    cells = row,
                    alignments = block.alignments,
                    columnCount = renderedColumnCount,
                    cellWidth = cellWidth,
                    header = false,
                    alternate = rowIndex % 2 == 1,
                    colors = colors,
                    onLinkClick = onLinkClick,
                    onFootnoteClick = onFootnoteClick,
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<List<MdInline>>,
    alignments: List<TableAlignment>,
    columnCount: Int,
    cellWidth: androidx.compose.ui.unit.Dp,
    header: Boolean,
    alternate: Boolean = false,
    colors: InlineTextColors,
    onLinkClick: (String) -> Unit,
    onFootnoteClick: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val background = when {
        header -> scheme.surfaceVariant.copy(alpha = 0.48f)
        alternate -> scheme.surfaceVariant.copy(alpha = 0.18f)
        else -> scheme.surface.copy(alpha = 0.12f)
    }
    Row(
        modifier = Modifier
            .background(background),
    ) {
        repeat(columnCount) { column ->
            val alignment = alignments.getOrNull(column) ?: TableAlignment.LEFT
            Box(
                modifier = Modifier
                    .width(cellWidth)
                    .border(
                        width = 0.5.dp,
                        color = scheme.outlineVariant.copy(alpha = 0.32f),
                    )
                    .padding(horizontal = 11.dp, vertical = 10.dp),
            ) {
                val textAlign = when (alignment) {
                    TableAlignment.LEFT -> TextAlign.Start
                    TableAlignment.CENTER -> TextAlign.Center
                    TableAlignment.RIGHT -> TextAlign.End
                }
                SelectionContainer {
                    MarkdownInlineText(
                        inlines = cells.getOrNull(column).orEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = textAlign,
                        ),
                        colors = colors,
                        onLinkClick = onLinkClick,
                        onFootnoteClick = onFootnoteClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownImageBlock(
    block: MdBlock.Image,
    baseUri: String?,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(18.dp)
    val glass = LocalLiquidGlass.current
    val model = remember(block.destination, baseUri) {
        resolveImageModel(context, baseUri, block.destination)
    }
    val request = remember(model, glass.animationsEnabled) {
        ImageRequest.Builder(context)
            .data(model)
            // Decode at a bounded size even if the containing item is measured
            // with loose constraints. This keeps huge photos from exhausting the
            // heap while scrolling.
            .size(2048, 2048)
            .crossfade(glass.animationsEnabled)
            .build()
    }

    Column(Modifier.fillMaxWidth()) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = block.alt.ifBlank { "文档图片" },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 420.dp)
                .clip(shape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
                    shape = shape,
                ),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            error = {
                ImageError(block.alt, block.destination)
            },
            success = {
                SubcomposeAsyncImageContent()
            },
        )
        if (block.alt.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = block.alt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ImageError(alt: String, destination: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = alt.ifBlank { "无法加载图片" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = destination,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FootnoteSection(
    footnotes: List<MdFootnoteDefinition>,
    colors: InlineTextColors,
    onLinkClick: (String) -> Unit,
    onFootnoteClick: (MdFootnoteDefinition) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "脚注",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        footnotes.forEach { footnote ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
                        .clickable(role = Role.Button) { onFootnoteClick(footnote) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = footnote.label.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    MarkdownInlineText(
                        inlines = footnote.inlines,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        colors = colors,
                        onLinkClick = onLinkClick,
                        onFootnoteClick = { label ->
                            footnotes.firstOrNull { it.label == label }?.let(onFootnoteClick)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPreview(onStartWriting: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "这一页还很安静",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Markdown 会在这里立即长成文章。轻触开始，写下第一行。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            GlassButton(
                label = "开始书写",
                onClick = onStartWriting,
                primary = true,
            )
        }
    }
}

@Composable
private fun Modifier.sourceJump(start: Int, onEditBlock: (Int) -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClickLabel = "转到源码",
        onClick = { onEditBlock(start) },
    )
}

private fun resolveImageModel(
    context: android.content.Context,
    baseUri: String?,
    rawDestination: String,
): Any {
    val destination = Uri.decode(rawDestination.trim())
    if (destination.isEmpty()) return destination
    if (destination.startsWith("//")) return "https:$destination"
    if (
        destination.startsWith("http://") ||
        destination.startsWith("https://") ||
        destination.startsWith("content://") ||
        destination.startsWith("file://") ||
        destination.startsWith("data:")
    ) {
        return Uri.parse(destination)
    }

    val base = baseUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    if (base != null) {
        when (base.scheme) {
            "http", "https" -> {
                runCatching {
                    return java.net.URI(base.toString()).resolve(destination).toString()
                }
            }
            "file" -> {
                val baseFile = File(base.path ?: "")
                return Uri.fromFile(File(baseFile.parentFile, destination))
            }
            "content" -> {
                runCatching {
                    val baseDocumentId = DocumentsContract.getDocumentId(base)
                    val parent = baseDocumentId.substringBeforeLast('/', "")
                    val target = if (parent.isEmpty()) destination else "$parent/$destination"
                    return DocumentsContract.buildDocumentUri(
                        requireNotNull(base.authority),
                        target,
                    )
                }
            }
        }
    }

    if (destination.startsWith('/')) {
        return Uri.fromFile(File(destination))
    }
    val relative = File(destination)
    return if (relative.exists()) Uri.fromFile(relative) else destination
}
