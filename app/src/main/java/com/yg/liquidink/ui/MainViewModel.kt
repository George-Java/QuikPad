package com.yg.liquidink.ui

import android.app.Application
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yg.liquidink.core.AppSettings
import com.yg.liquidink.core.GlassIntensity
import com.yg.liquidink.core.PerformanceMode
import com.yg.liquidink.core.ReaderBackground
import com.yg.liquidink.core.RecentDocument
import com.yg.liquidink.core.TextEncoding
import com.yg.liquidink.core.TextStatistics
import com.yg.liquidink.core.ThemeMode
import com.yg.liquidink.core.UndoRedoManager
import com.yg.liquidink.data.DocumentException
import com.yg.liquidink.data.DocumentMetadata
import com.yg.liquidink.data.DocumentRepository
import com.yg.liquidink.data.RecentDocumentStore
import com.yg.liquidink.data.SettingsStore
import com.yg.liquidink.markdown.MarkdownEditing
import com.yg.liquidink.markdown.MarkdownFormat
import com.yg.liquidink.markdown.MarkdownParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application)
    private val recentStore = RecentDocumentStore(application)
    private val settingsStore = SettingsStore(application)
    private val parser = MarkdownParser()
    private val history = UndoRedoManager()
    private val saveMutex = Mutex()

    private val _editor = MutableStateFlow<EditorUiState?>(null)
    val editor: StateFlow<EditorUiState?> = _editor.asStateFlow()

    val recent: StateFlow<List<RecentDocument>> = recentStore.recent
    val settings: StateFlow<AppSettings> = settingsStore.settings

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 12)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val openGeneration = AtomicLong(0)
    private val parseGeneration = AtomicLong(0)
    private var parseJob: Job? = null
    private var autoSaveJob: Job? = null
    private var savedText: String? = null

    init {
        viewModelScope.launch {
            settings
                .map { it.autoSaveEnabled to it.autoSaveIntervalSeconds }
                .distinctUntilChanged()
                .collect {
                    if (_editor.value?.isDirty == true) scheduleAutoSave()
                }
        }
    }

    // ---------------------------------------------------------------- opening

    fun openDocument(uri: Uri) {
        val generation = openGeneration.incrementAndGet()
        parseJob?.cancel()
        autoSaveJob?.cancel()
        history.reset()
        savedText = null

        val fallbackName = uri.lastPathSegment
            ?.let { Uri.decode(it) }
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "文档"
        _editor.value = EditorUiState(
            document = DocumentInfo(uri = uri.toString(), displayName = fallbackName),
            isLoading = true,
        )

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.persistPermission(uri) }
                val loaded = repository.read(uri)
                if (generation != openGeneration.get()) return@launch

                val name = loaded.metadata.displayName
                if (!DocumentRepository.hasSupportedExtension(name)) {
                    throw DocumentException("仅支持 .md、.markdown 和 .txt 文档。")
                }

                val stats = withContext(Dispatchers.Default) {
                    TextStatistics.compute(loaded.text)
                }
                if (generation != openGeneration.get()) return@launch

                savedText = loaded.text
                val info = loaded.metadata.toInfo()
                _editor.value = EditorUiState(
                    document = info,
                    value = TextFieldValue(loaded.text, TextRange(0)),
                    encoding = loaded.encoding,
                    stats = stats,
                    isLoading = false,
                    isParsing = true,
                )
                history.reset()
                recentStore.markOpened(
                    RecentDocument(
                        uri = info.uri,
                        displayName = info.displayName,
                        lastOpened = System.currentTimeMillis(),
                        sizeBytes = loaded.metadata.sizeBytes,
                        encodingLabel = loaded.encoding.label,
                    ),
                )
                scheduleParse(loaded.text, delayMillis = 0)
            } catch (exception: Exception) {
                if (generation != openGeneration.get()) return@launch
                _editor.value = null
                recentStore.remove(uri.toString())
                notify(
                    exception.message ?: "打开文档失败，请重试。",
                    isError = true,
                )
            }
        }
    }

    fun createDocument(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.persistPermission(uri)
                    repository.write(uri, "", TextEncoding.Utf8)
                }
                openDocument(uri)
            } catch (exception: Exception) {
                notify(exception.message ?: "新建文档失败。", isError = true)
            }
        }
    }

    // ----------------------------------------------------------------- editing

    fun onEditorValueChange(newValue: TextFieldValue) {
        val state = _editor.value ?: return
        val current = state.value
        if (newValue.text == current.text) {
            if (newValue != current) {
                _editor.value = state.copy(value = newValue)
            }
            return
        }

        val adjusted = MarkdownEditing.applyAutoFormatting(current, newValue)
        if (adjusted.text == current.text) {
            _editor.value = state.copy(value = adjusted)
            return
        }

        val commonPrefix = commonPrefixLength(current.text, adjusted.text)
        val insertedNewline = adjusted.text.length > current.text.length &&
            adjusted.text.getOrNull(commonPrefix) == '\n'
        val structural = adjusted.text.length - current.text.length != 1 || insertedNewline
        history.recordBeforeChange(
            before = snapshot(current),
            structural = structural,
        )
        _editor.value = state.copy(
            value = adjusted,
            isDirty = true,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
        )
        scheduleParse(adjusted.text)
        scheduleAutoSave()
    }

    fun applyFormat(format: MarkdownFormat) {
        val state = _editor.value ?: return
        val newValue = MarkdownEditing.apply(state.value, format)
        if (newValue.text == state.value.text) {
            _editor.value = state.copy(value = newValue)
            return
        }
        history.recordBeforeChange(
            before = snapshot(state.value),
            forceNewGroup = true,
            structural = true,
        )
        _editor.value = state.copy(
            value = newValue,
            isDirty = true,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
        )
        scheduleParse(newValue.text)
        scheduleAutoSave()
    }

    fun undo() {
        val state = _editor.value ?: return
        val previous = history.undo(snapshot(state.value)) ?: return
        val value = TextFieldValue(
            previous.text,
            TextRange(
                previous.selectionStart.coerceIn(0, previous.text.length),
                previous.selectionEnd.coerceIn(0, previous.text.length),
            ),
        )
        _editor.value = state.copy(
            value = value,
            isDirty = previous.text != savedText,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
        )
        scheduleParse(previous.text, delayMillis = 0)
        if (previous.text != savedText) scheduleAutoSave() else autoSaveJob?.cancel()
    }

    fun redo() {
        val state = _editor.value ?: return
        val next = history.redo(snapshot(state.value)) ?: return
        val value = TextFieldValue(
            next.text,
            TextRange(
                next.selectionStart.coerceIn(0, next.text.length),
                next.selectionEnd.coerceIn(0, next.text.length),
            ),
        )
        _editor.value = state.copy(
            value = value,
            isDirty = next.text != savedText,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
        )
        scheduleParse(next.text, delayMillis = 0)
        if (next.text != savedText) scheduleAutoSave() else autoSaveJob?.cancel()
    }

    fun setSelection(start: Int, end: Int = start) {
        val state = _editor.value ?: return
        val safeStart = start.coerceIn(0, state.value.text.length)
        val safeEnd = end.coerceIn(0, state.value.text.length)
        if (
            state.value.selection.start == safeStart &&
            state.value.selection.end == safeEnd
        ) {
            return
        }
        _editor.value = state.copy(
            value = state.value.copy(
                selection = TextRange(safeStart, safeEnd),
                composition = null,
            ),
        )
    }

    fun toggleTask(markerStart: Int, markerEnd: Int) {
        val state = _editor.value ?: return
        val text = state.value.text
        if (markerStart < 0 || markerEnd > text.length || markerEnd - markerStart != 3) return
        val marker = text.substring(markerStart, markerEnd)
        if (marker.length != 3 || marker.first() != '[' || marker.last() != ']') return
        val replacement = if (marker[1].equals('x', ignoreCase = true)) "[ ]" else "[x]"
        replaceSpan(
            start = markerStart,
            endExclusive = markerEnd,
            replacement = replacement,
            historyAlreadyRecorded = false,
        )
    }

    /**
     * Used by the find/replace bar. [range] is inclusive, matching Regex ranges.
     */
    fun replaceRange(range: IntRange, replacement: String) {
        val state = _editor.value ?: return
        val start = range.first.coerceIn(0, state.value.text.length)
        val end = (range.last + 1).coerceIn(start, state.value.text.length)
        if (start == end) return
        replaceSpan(start, end, replacement)
    }

    fun replaceAll(query: String, replacement: String, caseSensitive: Boolean) {
        if (query.isEmpty()) return
        val state = _editor.value ?: return
        val original = state.value.text
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val regex = Regex(
                    Regex.escape(query),
                    if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE),
                )
                var count = 0
                val text = regex.replace(original) {
                    count++
                    replacement
                }
                text to count
            }
            val currentState = _editor.value ?: return@launch
            if (currentState.value.text != original) {
                notify("文档在替换过程中发生变化，请重试。", isError = true)
                return@launch
            }
            val count = result.second
            if (count == 0) {
                notify("没有找到匹配内容。")
                return@launch
            }
            val newText = result.first
            history.recordBeforeChange(
                before = snapshot(currentState.value),
                forceNewGroup = true,
                structural = true,
            )
            _editor.value = currentState.copy(
                value = TextFieldValue(newText, TextRange(newText.length)),
                isDirty = true,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
            )
            scheduleParse(newText)
            scheduleAutoSave()
            notify("已替换 $count 处。")
        }
    }

    private fun replaceSpan(
        start: Int,
        endExclusive: Int,
        replacement: String,
        historyAlreadyRecorded: Boolean = false,
    ) {
        val state = _editor.value ?: return
        val text = state.value.text
        if (start !in 0..text.length || endExclusive !in start..text.length) return
        if (!historyAlreadyRecorded) {
            history.recordBeforeChange(
                before = snapshot(state.value),
                forceNewGroup = true,
                structural = true,
            )
        }
        val newText = text.replaceRange(start, endExclusive, replacement)
        val oldSelection = state.value.selection
        val newStart = mapOffsetForReplacement(
            oldSelection.start,
            start,
            endExclusive,
            replacement.length,
        ).coerceIn(0, newText.length)
        val newEnd = mapOffsetForReplacement(
            oldSelection.end,
            start,
            endExclusive,
            replacement.length,
        ).coerceIn(0, newText.length)
        _editor.value = state.copy(
            value = TextFieldValue(newText, TextRange(newStart, newEnd)),
            isDirty = true,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
        )
        scheduleParse(newText)
        scheduleAutoSave()
    }

    // ----------------------------------------------------------------- saving

    fun save() {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            persistCurrent(silent = false)
        }
    }

    fun saveAndClose() {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            if (persistCurrent(silent = false) && _editor.value?.isDirty != true) {
                closeEditor()
            }
        }
    }

    fun saveAs(uri: Uri) {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            saveMutex.withLock {
                val state = _editor.value ?: return@withLock
                if (state.isLoading) return@withLock
                _editor.update { it?.copy(isSaving = true) }
                val capturedText = state.value.text
                try {
                    withContext(Dispatchers.IO) { repository.persistPermission(uri) }
                    repository.write(uri, capturedText, state.encoding)
                    val metadata = repository.metadata(uri)
                    val info = metadata.toInfo()
                    val stillCurrent = _editor.value
                    if (stillCurrent != null) {
                        savedText = capturedText
                        _editor.value = stillCurrent.copy(
                            document = info,
                            isSaving = false,
                            isDirty = stillCurrent.value.text != capturedText,
                            lastSavedAt = System.currentTimeMillis(),
                        )
                        recentStore.markOpened(
                            RecentDocument(
                                uri = info.uri,
                                displayName = info.displayName,
                                lastOpened = System.currentTimeMillis(),
                                sizeBytes = metadata.sizeBytes,
                                encodingLabel = state.encoding.label,
                            ),
                        )
                    }
                    notify("已另存为 ${info.displayName}。")
                } catch (exception: Exception) {
                    _editor.update { it?.copy(isSaving = false) }
                    notify(exception.message ?: "另存为失败。", isError = true)
                }
            }
        }
    }

    fun onAppBackgrounded() {
        val state = _editor.value ?: return
        if (!state.isDirty || !settings.value.autoSaveEnabled) return
        viewModelScope.launch {
            persistCurrent(silent = true)
        }
    }

    private suspend fun persistCurrent(silent: Boolean): Boolean {
        autoSaveJob?.cancel()
        saveMutex.withLock {
            val state = _editor.value ?: return false
            if (state.isLoading) return false
            if (!state.isDirty) {
                if (!silent) notify("文档已是最新状态。")
                return true
            }

            _editor.update { it?.copy(isSaving = true) }
            val capturedText = state.value.text
            val uri = Uri.parse(state.document.uri)
            return try {
                repository.write(uri, capturedText, state.encoding)
                savedText = capturedText
                _editor.update { current ->
                    current?.copy(
                        isSaving = false,
                        isDirty = current.value.text != capturedText,
                        lastSavedAt = System.currentTimeMillis(),
                    )
                }
                if (!silent) notify("已保存。")
                true
            } catch (exception: Exception) {
                _editor.update { it?.copy(isSaving = false) }
                notify(exception.message ?: "保存失败。", isError = true)
                false
            }
        }
    }

    // --------------------------------------------------------- file management

    fun renameDocument(newName: String) {
        val state = _editor.value ?: return
        viewModelScope.launch {
            saveMutex.withLock {
                try {
                    val finalName = normalizedFileName(state.document.displayName, newName)
                    _editor.update { it?.copy(isSaving = true) }
                    val metadata = repository.rename(
                        Uri.parse(state.document.uri),
                        finalName,
                    )
                    val info = metadata.toInfo()
                    val updated = _editor.value
                    if (updated != null) {
                        _editor.value = updated.copy(
                            document = info,
                            isSaving = false,
                        )
                        recentStore.updateUri(
                            oldUri = state.document.uri,
                            replacement = RecentDocument(
                                uri = info.uri,
                                displayName = info.displayName,
                                lastOpened = System.currentTimeMillis(),
                                sizeBytes = metadata.sizeBytes,
                                encodingLabel = updated.encoding.label,
                            ),
                        )
                    }
                    notify("已重命名为 ${info.displayName}。")
                } catch (exception: Exception) {
                    _editor.update { it?.copy(isSaving = false) }
                    notify(exception.message ?: "重命名失败。", isError = true)
                }
            }
        }
    }

    fun deleteDocument() {
        val state = _editor.value ?: return
        autoSaveJob?.cancel()
        viewModelScope.launch {
            saveMutex.withLock {
                _editor.update { it?.copy(isSaving = true) }
                try {
                    repository.delete(Uri.parse(state.document.uri))
                    recentStore.remove(state.document.uri)
                    closeEditor()
                    notify("已删除 ${state.document.displayName}。")
                } catch (exception: Exception) {
                    _editor.update { it?.copy(isSaving = false) }
                    notify(exception.message ?: "删除失败。", isError = true)
                }
            }
        }
    }

    fun renameRecent(uriString: String, newName: String) {
        val item = recentStore.recent.value.firstOrNull { it.uri == uriString } ?: return
        viewModelScope.launch {
            try {
                val finalName = normalizedFileName(item.displayName, newName)
                val metadata = repository.rename(Uri.parse(uriString), finalName)
                val info = metadata.toInfo()
                recentStore.updateUri(
                    oldUri = uriString,
                    replacement = RecentDocument(
                        uri = info.uri,
                        displayName = info.displayName,
                        lastOpened = item.lastOpened,
                        sizeBytes = metadata.sizeBytes,
                        encodingLabel = item.encodingLabel,
                    ),
                )
                notify("已重命名为 ${info.displayName}。")
            } catch (exception: Exception) {
                notify(exception.message ?: "重命名失败。", isError = true)
            }
        }
    }

    fun deleteRecent(uriString: String) {
        val item = recentStore.recent.value.firstOrNull { it.uri == uriString } ?: return
        viewModelScope.launch {
            try {
                repository.delete(Uri.parse(uriString))
                recentStore.remove(uriString)
                notify("已删除 ${item.displayName}。")
            } catch (exception: Exception) {
                notify(exception.message ?: "删除失败。", isError = true)
            }
        }
    }

    fun forgetRecent(uriString: String) {
        recentStore.remove(uriString)
    }

    fun closeEditor() {
        openGeneration.incrementAndGet()
        parseGeneration.incrementAndGet()
        parseJob?.cancel()
        autoSaveJob?.cancel()
        history.reset()
        savedText = null
        _editor.value = null
    }

    // ---------------------------------------------------------------- settings

    fun setThemeMode(mode: ThemeMode) = settingsStore.setThemeMode(mode)

    fun setAutoSaveEnabled(enabled: Boolean) = settingsStore.setAutoSaveEnabled(enabled)

    fun setAutoSaveIntervalSeconds(seconds: Int) =
        settingsStore.setAutoSaveIntervalSeconds(seconds)

    fun setPerformanceMode(mode: PerformanceMode) =
        settingsStore.setPerformanceMode(mode)

    fun setHighContrast(enabled: Boolean) = settingsStore.setHighContrast(enabled)

    fun setReaderBackground(background: ReaderBackground) =
        settingsStore.setReaderBackground(background)

    fun setGlassEffectsEnabled(enabled: Boolean) =
        settingsStore.setGlassEffectsEnabled(enabled)

    fun setGlassIntensity(intensity: GlassIntensity) =
        settingsStore.setGlassIntensity(intensity)

    fun setAnimationsEnabled(enabled: Boolean) =
        settingsStore.setAnimationsEnabled(enabled)

    // ----------------------------------------------------------------- parsing

    private fun scheduleParse(text: String, delayMillis: Long = debounceFor(text)) {
        parseGeneration.incrementAndGet()
        val generation = parseGeneration.get()
        parseJob?.cancel()
        _editor.update { it?.copy(isParsing = true) }
        parseJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val result = try {
                withContext(Dispatchers.Default) {
                    val parsed = parser.parse(text)
                    val stats = TextStatistics.compute(text)
                    parsed to stats
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == parseGeneration.get()) {
                    _editor.update { it?.copy(isParsing = false) }
                    notify(
                        "文档排版遇到异常结构，已保留可读内容。",
                        isError = true,
                    )
                }
                return@launch
            }
            if (generation != parseGeneration.get()) return@launch
            _editor.update { current ->
                if (current != null && current.value.text == text) {
                    current.copy(
                        parsed = result.first,
                        stats = result.second,
                        isParsing = false,
                    )
                } else {
                    current
                }
            }
        }
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        val currentSettings = settings.value
        if (!currentSettings.autoSaveEnabled) return
        if (_editor.value?.isDirty != true) return
        val interval = currentSettings.autoSaveIntervalMillis
        autoSaveJob = viewModelScope.launch {
            delay(interval)
            autoSaveJob = null
            if (_editor.value?.isDirty == true) {
                persistCurrent(silent = true)
            }
        }
    }

    private fun debounceFor(text: String): Long = when {
        text.length >= 1_000_000 -> 650L
        text.length >= 250_000 -> 320L
        else -> 120L
    }

    // ------------------------------------------------------------------ helpers

    private fun snapshot(value: TextFieldValue) = UndoRedoManager.Snapshot(
        text = value.text,
        selectionStart = value.selection.start,
        selectionEnd = value.selection.end,
    )

    private fun commonPrefixLength(first: String, second: String): Int {
        val limit = minOf(first.length, second.length)
        var index = 0
        while (index < limit && first[index] == second[index]) index++
        return index
    }

    private fun mapOffsetForReplacement(
        offset: Int,
        start: Int,
        endExclusive: Int,
        replacementLength: Int,
    ): Int = when {
        offset <= start -> offset
        offset >= endExclusive -> offset + (replacementLength - (endExclusive - start))
        else -> start + replacementLength
    }

    private fun normalizedFileName(currentName: String, requested: String): String {
        val trimmed = requested.trim()
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('\\')) {
            throw DocumentException("文件名不能为空，也不能包含斜杠。")
        }
        val requestedExtension = trimmed.substringAfterLast('.', "")
        if (requestedExtension.isEmpty()) {
            val oldExtension = currentName.substringAfterLast('.', "")
            return if (oldExtension.isEmpty()) "$trimmed.md" else "$trimmed.$oldExtension"
        }
        if (requestedExtension.lowercase() !in DocumentRepository.SUPPORTED_EXTENSIONS) {
            throw DocumentException("仅支持 .md、.markdown、.txt 文件名。")
        }
        return trimmed
    }

    private fun notify(text: String, isError: Boolean = false) {
        _messages.tryEmit(UiMessage(text = text, isError = isError))
    }

    private fun DocumentMetadata.toInfo() = DocumentInfo(
        uri = uri.toString(),
        displayName = displayName,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
    )
}
