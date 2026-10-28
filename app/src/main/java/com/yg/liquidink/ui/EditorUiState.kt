package com.yg.liquidink.ui

import androidx.compose.ui.text.input.TextFieldValue
import com.yg.liquidink.core.TextEncoding
import com.yg.liquidink.core.TextStats
import com.yg.liquidink.core.TextStatistics
import com.yg.liquidink.markdown.ParsedMarkdown

data class DocumentInfo(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
) {
    val titleWithoutExtension: String
        get() = displayName.substringBeforeLast('.', displayName)

    val extension: String
        get() = displayName.substringAfterLast('.', "")
}

data class EditorUiState(
    val document: DocumentInfo,
    val value: TextFieldValue = TextFieldValue(""),
    val parsed: ParsedMarkdown = ParsedMarkdown.Empty,
    val encoding: TextEncoding = TextEncoding.Utf8,
    val stats: TextStats = TextStatistics.compute(""),
    val isLoading: Boolean = false,
    val isParsing: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val lastSavedAt: Long? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val text: String get() = value.text

    val isLargeDocument: Boolean
        get() = value.text.length >= LARGE_DOCUMENT_CHARACTERS

    companion object {
        const val LARGE_DOCUMENT_CHARACTERS = 1_000_000
        const val VERY_LARGE_DOCUMENT_CHARACTERS = 2_500_000
    }
}

data class UiMessage(
    val text: String,
    val isError: Boolean = false,
)
