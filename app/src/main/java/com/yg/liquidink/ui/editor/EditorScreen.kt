package com.yg.liquidink.ui.editor

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.yg.liquidink.core.ReaderBackground
import com.yg.liquidink.markdown.MarkdownFormat
import com.yg.liquidink.markdown.buildOutline
import com.yg.liquidink.ui.EditorUiState
import com.yg.liquidink.ui.components.GlassDialog
import com.yg.liquidink.ui.components.GlassSurface
import com.yg.liquidink.ui.components.GlassTextField
import com.yg.liquidink.ui.components.LiquidBackground
import com.yg.liquidink.ui.theme.LocalLiquidGlass
import com.yg.liquidink.ui.theme.ParchmentPaperBottom
import com.yg.liquidink.ui.theme.ParchmentPaperEdge
import com.yg.liquidink.ui.theme.ParchmentPaperTop
import com.yg.liquidink.ui.theme.ParchmentReaderColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(
    state: EditorUiState,
    readerBackground: ReaderBackground,
    onValueChange: (TextFieldValue) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormat: (MarkdownFormat) -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTask: (Int, Int) -> Unit,
    onSetSelection: (Int, Int) -> Unit,
    onReplaceRange: (IntRange, String) -> Unit,
    onReplaceAll: (String, String, Boolean) -> Unit,
    onSaveAndClose: () -> Unit,
    onDiscardAndClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(EditorMode.PREVIEW) }
    var focusMode by rememberSaveable { mutableStateOf(false) }
    var showFind by rememberSaveable { mutableStateOf(false) }
    var showFormat by rememberSaveable { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(state.document.displayName) }
    var pendingSourceFocus by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var caseSensitive by rememberSaveable { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(SearchResults.Empty) }
    var searching by remember { mutableStateOf(false) }
    var currentMatch by remember { mutableIntStateOf(-1) }
    var showOutline by rememberSaveable { mutableStateOf(false) }
    var outlineNonce by remember { mutableLongStateOf(0L) }
    var outlineScrollRequest by remember { mutableStateOf<OutlineScrollRequest?>(null) }

    val sourceFocus = remember { FocusRequester() }
    val renameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    val large = state.isLargeDocument
    val glass = LocalLiquidGlass.current
    val motion = glass.animationsEnabled
    val outlineEntries = remember(state.parsed.blocks) { buildOutline(state.parsed.blocks) }
    val topEnter = if (motion) {
        fadeIn(tween(180)) + expandVertically(expandFrom = Alignment.Top)
    } else {
        EnterTransition.None
    }
    val topExit = if (motion) {
        fadeOut(tween(130)) + shrinkVertically(shrinkTowards = Alignment.Top)
    } else {
        ExitTransition.None
    }
    val bottomEnter = if (motion) {
        fadeIn(tween(160)) + expandVertically(expandFrom = Alignment.Bottom)
    } else {
        EnterTransition.None
    }
    val bottomExit = if (motion) {
        fadeOut(tween(120)) + shrinkVertically(shrinkTowards = Alignment.Bottom)
    } else {
        ExitTransition.None
    }

    val openLink: (String) -> Unit = { destination ->
        val parsed = runCatching { Uri.parse(destination) }.getOrNull()
        val protocol = parsed?.scheme?.lowercase()
        if (protocol in setOf("http", "https", "mailto", "tel")) {
            runCatching { uriHandler.openUri(destination) }.onFailure {
                Toast.makeText(context, "无法打开该链接。", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "当前仅支持打开网页、邮件和电话链接。", Toast.LENGTH_SHORT).show()
        }
    }

    val jumpToMatch: (Int) -> Unit = { index ->
        val range = searchResults.ranges.getOrNull(index)
        if (range != null) {
            currentMatch = index
            if (mode == EditorMode.PREVIEW) mode = EditorMode.SOURCE
            onSetSelection(range.first, range.last + 1)
            pendingSourceFocus = true
        }
    }

    val goPrevious: () -> Unit = {
        if (searchResults.ranges.isNotEmpty()) {
            val next = if (currentMatch <= 0) {
                searchResults.ranges.lastIndex
            } else {
                currentMatch - 1
            }
            jumpToMatch(next)
        }
    }

    val goNext: () -> Unit = {
        if (searchResults.ranges.isNotEmpty()) {
            val next = if (currentMatch < 0 || currentMatch >= searchResults.ranges.lastIndex) {
                0
            } else {
                currentMatch + 1
            }
            jumpToMatch(next)
        }
    }

    val requestBack: () -> Unit = {
        when {
            showOutline -> showOutline = false
            focusMode -> focusMode = false
            showFind -> showFind = false
            state.isDirty -> showExitDialog = true
            else -> onDiscardAndClose()
        }
    }

    BackHandler(
        enabled = !showExitDialog && !showRenameDialog && !showDeleteDialog,
        onBack = requestBack,
    )

    LaunchedEffect(state.value.text, query, caseSensitive, showFind) {
        if (!showFind || query.isEmpty()) {
            searching = false
            searchResults = SearchResults.Empty
            currentMatch = -1
            return@LaunchedEffect
        }
        searching = true
        delay(120)
        val results = withContext(Dispatchers.Default) {
            findLiteralMatches(state.value.text, query, caseSensitive)
        }
        searchResults = results
        searching = false
        if (currentMatch >= results.count) currentMatch = -1
    }

    LaunchedEffect(pendingSourceFocus, mode) {
        if (pendingSourceFocus && (mode == EditorMode.SOURCE || mode == EditorMode.LIVE)) {
            delay(100)
            runCatching { sourceFocus.requestFocus() }
            pendingSourceFocus = false
        }
    }

    LaunchedEffect(showRenameDialog) {
        if (showRenameDialog) {
            delay(120)
            runCatching { renameFocus.requestFocus() }
        }
    }

    val activeRange = if (showFind) {
        searchResults.ranges.getOrNull(currentMatch)
    } else {
        null
    }

    LiquidBackground(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                AnimatedVisibility(
                    visible = !focusMode,
                    enter = topEnter,
                    exit = topExit,
                ) {
                    EditorTopBar(
                        documentName = state.document.displayName,
                        isDirty = state.isDirty,
                        isSaving = state.isSaving,
                        isParsing = state.isParsing,
                        isLoading = state.isLoading,
                        lastSavedAt = state.lastSavedAt,
                        isLargeDocument = large,
                        onBack = requestBack,
                        onOpenOutline = {
                            showFind = false
                            showFormat = false
                            keyboard?.hide()
                            showOutline = true
                        },
                        onSave = onSave,
                        onSaveAs = onSaveAs,
                        onRename = {
                            renameValue = state.document.displayName
                            showRenameDialog = true
                        },
                        onDelete = { showDeleteDialog = true },
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = if (focusMode) 6.dp else 2.dp),
                ) {
                    if (state.isLoading) {
                        LoadingPane(modifier = Modifier.fillMaxSize())
                    } else {
                        EditorContent(
                            state = state,
                            mode = mode,
                            largeDocument = large,
                            readerBackground = readerBackground,
                            outlineScrollRequest = outlineScrollRequest,
                            onOutlineScrollConsumed = { nonce ->
                                if (outlineScrollRequest?.nonce == nonce) {
                                    outlineScrollRequest = null
                                }
                            },
                            searchResults = if (showFind) searchResults.ranges else emptyList(),
                            activeMatch = activeRange,
                            sourceFocus = sourceFocus,
                            onValueChange = onValueChange,
                            onToggleTask = onToggleTask,
                            onLinkClick = openLink,
                            onEditBlock = { start ->
                                mode = EditorMode.LIVE
                                onSetSelection(start, start)
                                pendingSourceFocus = true
                            },
                            onStartWriting = {
                                mode = EditorMode.SOURCE
                                pendingSourceFocus = true
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !focusMode && !state.isLoading,
                    enter = bottomEnter,
                    exit = bottomExit,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                    ) {
                        AnimatedVisibility(
                            visible = showFind,
                            enter = bottomEnter,
                            exit = bottomExit,
                        ) {
                            FindReplaceBar(
                                query = query,
                                onQueryChange = {
                                    query = it
                                    currentMatch = -1
                                },
                                replacement = replacement,
                                onReplacementChange = { replacement = it },
                                caseSensitive = caseSensitive,
                                onCaseSensitiveChange = {
                                    caseSensitive = it
                                    currentMatch = -1
                                },
                                currentIndex = currentMatch,
                                resultCount = searchResults.count,
                                truncated = searchResults.truncated,
                                isSearching = searching,
                                onPrevious = goPrevious,
                                onNext = goNext,
                                onReplace = {
                                    val index = if (currentMatch in searchResults.ranges.indices) {
                                        currentMatch
                                    } else {
                                        0
                                    }
                                    val range = searchResults.ranges.getOrNull(index)
                                    if (range != null) {
                                        onReplaceRange(range, replacement)
                                        currentMatch = -1
                                        pendingSourceFocus = true
                                    }
                                },
                                onReplaceAll = {
                                    onReplaceAll(query, replacement, caseSensitive)
                                    currentMatch = -1
                                },
                                onClose = {
                                    showFind = false
                                    currentMatch = -1
                                },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        AnimatedVisibility(
                            visible = showFormat && mode != EditorMode.PREVIEW,
                            enter = bottomEnter,
                            exit = bottomExit,
                        ) {
                            EditorFormatBar(
                                onFormat = onFormat,
                                onClose = { showFormat = false },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        EditorBottomBar(
                            mode = mode,
                            onModeSelected = { selected ->
                                mode = selected
                                if (selected == EditorMode.PREVIEW) {
                                    showFind = false
                                    showFormat = false
                                    currentMatch = -1
                                    keyboard?.hide()
                                }
                            },
                            stats = state.stats,
                            canUndo = state.canUndo,
                            canRedo = state.canRedo,
                            formatOpen = showFormat,
                            focusMode = focusMode,
                            onUndo = onUndo,
                            onRedo = onRedo,
                            onFind = {
                                showFormat = false
                                showFind = !showFind
                                if (!showFind) currentMatch = -1
                            },
                            onFormat = {
                                showFind = false
                                showFormat = !showFormat
                            },
                            onFocus = {
                                focusMode = true
                                showFind = false
                                showFormat = false
                                keyboard?.hide()
                            },
                            onStartEditing = {
                                mode = EditorMode.SOURCE
                                pendingSourceFocus = true
                            },
                            onOpenAppearance = onOpenSettings,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = focusMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 14.dp),
                enter = if (motion) fadeIn(tween(160)) else EnterTransition.None,
                exit = if (motion) fadeOut(tween(120)) else ExitTransition.None,
            ) {
                FocusModeExitButton(onExit = { focusMode = false })
            }

            DocumentOutlinePanel(
                visible = showOutline,
                entries = outlineEntries,
                isParsing = state.isParsing,
                motionEnabled = motion,
                onDismiss = { showOutline = false },
                onSelect = { entry ->
                    showOutline = false
                    keyboard?.hide()
                    if (mode == EditorMode.SOURCE) {
                        mode = EditorMode.PREVIEW
                    }
                    outlineNonce += 1
                    outlineScrollRequest = OutlineScrollRequest(
                        blockIndex = entry.blockIndex,
                        nonce = outlineNonce,
                    )
                },
            )
        }
    }

    if (showExitDialog) {
        GlassDialog(
            title = "离开这篇文档？",
            message = "还有未保存的更改。可以先保存，或直接离开。",
            confirmLabel = "保存并离开",
            dismissLabel = "取消",
            neutralLabel = "直接离开",
            onNeutral = {
                showExitDialog = false
                onDiscardAndClose()
            },
            onConfirm = {
                showExitDialog = false
                onSaveAndClose()
            },
            onDismiss = { showExitDialog = false },
        )
    }

    if (showRenameDialog) {
        GlassDialog(
            title = "重命名文档",
            confirmLabel = "重命名",
            dismissLabel = "取消",
            confirmEnabled = renameValue.isNotBlank() &&
                renameValue.trim() != state.document.displayName,
            onConfirm = {
                showRenameDialog = false
                onRename(renameValue.trim())
            },
            onDismiss = { showRenameDialog = false },
        ) {
            GlassTextField(
                value = renameValue,
                onValueChange = { renameValue = it },
                placeholder = "文件名.md",
                focusRequester = renameFocus,
                onImeAction = {
                    if (
                        renameValue.isNotBlank() &&
                        renameValue.trim() != state.document.displayName
                    ) {
                        showRenameDialog = false
                        onRename(renameValue.trim())
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "保留 .md、.markdown 或 .txt 扩展名；不填写时会沿用原扩展名。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDeleteDialog) {
        GlassDialog(
            title = "删除文档？",
            message = "“${state.document.displayName}”将从存储中永久删除，此操作不可撤销。",
            confirmLabel = "删除",
            dismissLabel = "取消",
            destructive = true,
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun EditorContent(
    state: EditorUiState,
    mode: EditorMode,
    largeDocument: Boolean,
    readerBackground: ReaderBackground,
    outlineScrollRequest: OutlineScrollRequest?,
    onOutlineScrollConsumed: (Long) -> Unit,
    searchResults: List<IntRange>,
    activeMatch: IntRange?,
    sourceFocus: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onToggleTask: (Int, Int) -> Unit,
    onLinkClick: (String) -> Unit,
    onEditBlock: (Int) -> Unit,
    onStartWriting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        EditorMode.PREVIEW -> {
            PreviewPane(
                state = state,
                readerBackground = readerBackground,
                scrollRequest = outlineScrollRequest,
                onScrollRequestConsumed = onOutlineScrollConsumed,
                onLinkClick = onLinkClick,
                onEditBlock = onEditBlock,
                onToggleTask = onToggleTask,
                onStartWriting = onStartWriting,
                modifier = modifier,
            )
        }

        EditorMode.SOURCE -> {
            SourcePane(
                state = state,
                largeDocument = largeDocument,
                searchResults = searchResults,
                activeMatch = activeMatch,
                sourceFocus = sourceFocus,
                onValueChange = onValueChange,
                modifier = modifier,
            )
        }

        EditorMode.LIVE -> {
            BoxWithConstraints(modifier) {
                val useRow = maxWidth > maxHeight * 1.08f
                if (useRow) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SourcePane(
                            state = state,
                            largeDocument = largeDocument,
                            searchResults = searchResults,
                            activeMatch = activeMatch,
                            sourceFocus = sourceFocus,
                            onValueChange = onValueChange,
                            showHeader = true,
                            modifier = Modifier
                                .weight(0.44f)
                                .fillMaxSize(),
                        )
                        PreviewPane(
                            state = state,
                            readerBackground = readerBackground,
                            scrollRequest = outlineScrollRequest,
                            onScrollRequestConsumed = onOutlineScrollConsumed,
                            onLinkClick = onLinkClick,
                            onEditBlock = onEditBlock,
                            onToggleTask = onToggleTask,
                            onStartWriting = onStartWriting,
                            showHeader = true,
                            modifier = Modifier
                                .weight(0.56f)
                                .fillMaxSize(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SourcePane(
                            state = state,
                            largeDocument = largeDocument,
                            searchResults = searchResults,
                            activeMatch = activeMatch,
                            sourceFocus = sourceFocus,
                            onValueChange = onValueChange,
                            showHeader = true,
                            modifier = Modifier
                                .weight(0.38f)
                                .fillMaxWidth(),
                        )
                        PreviewPane(
                            state = state,
                            readerBackground = readerBackground,
                            scrollRequest = outlineScrollRequest,
                            onScrollRequestConsumed = onOutlineScrollConsumed,
                            onLinkClick = onLinkClick,
                            onEditBlock = onEditBlock,
                            onToggleTask = onToggleTask,
                            onStartWriting = onStartWriting,
                            showHeader = true,
                            modifier = Modifier
                                .weight(0.62f)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePane(
    state: EditorUiState,
    largeDocument: Boolean,
    searchResults: List<IntRange>,
    activeMatch: IntRange?,
    sourceFocus: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = false,
) {
    GlassSurface(
        modifier = modifier,
        cornerRadius = 24.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (showHeader) {
                PaneHeader(
                    title = "源码",
                    trailing = if (largeDocument) {
                        "大文件 · 已关闭语法着色"
                    } else {
                        "Markdown"
                    },
                )
            }
            SourceEditor(
                value = state.value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                focusRequester = sourceFocus,
                searchMatches = searchResults,
                activeMatch = activeMatch,
                largeDocument = largeDocument,
            )
        }
    }
}

@Composable
private fun PreviewPane(
    state: EditorUiState,
    readerBackground: ReaderBackground,
    onLinkClick: (String) -> Unit,
    onEditBlock: (Int) -> Unit,
    onToggleTask: (Int, Int) -> Unit,
    onStartWriting: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = false,
    scrollRequest: OutlineScrollRequest? = null,
    onScrollRequestConsumed: (Long) -> Unit = {},
) {
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            if (showHeader) {
                PaneHeader(title = "预览", trailing = state.encoding.label)
            }
            if (state.isParsing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                )
            }
            MarkdownPreview(
                parsed = state.parsed,
                baseUri = state.document.uri,
                isParsing = state.isParsing,
                onEditBlock = onEditBlock,
                onToggleTask = onToggleTask,
                onLinkClick = onLinkClick,
                onStartWriting = onStartWriting,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 18.dp,
                    bottom = 28.dp,
                ),
                scrollRequest = scrollRequest,
                onScrollRequestConsumed = onScrollRequestConsumed,
            )
        }
    }

    when (readerBackground) {
        ReaderBackground.DEFAULT -> {
            GlassSurface(
                modifier = modifier,
                cornerRadius = 24.dp,
            ) {
                content()
            }
        }

        ReaderBackground.PARCHMENT -> {
            val glass = LocalLiquidGlass.current
            val shape = RoundedCornerShape(24.dp)
            val shadowModifier = if (glass.enhancedShadows) {
                Modifier.shadow(
                    elevation = 14.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = ParchmentPaperEdge.copy(alpha = 0.18f),
                    spotColor = ParchmentPaperEdge.copy(alpha = 0.24f),
                )
            } else {
                Modifier
            }
            Box(
                modifier = modifier
                    .then(shadowModifier)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(ParchmentPaperTop, ParchmentPaperBottom),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        color = ParchmentPaperEdge.copy(alpha = 0.78f),
                        shape = shape,
                    ),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides ParchmentReaderColors.onSurface,
                ) {
                    MaterialTheme(
                        colorScheme = ParchmentReaderColors,
                        typography = MaterialTheme.typography,
                        shapes = MaterialTheme.shapes,
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingPane(modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier,
        cornerRadius = 24.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "正在打开文档…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
