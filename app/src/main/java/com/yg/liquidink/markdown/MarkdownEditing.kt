package com.yg.liquidink.markdown

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

enum class MarkdownFormat {
    HEADING_1,
    HEADING_2,
    HEADING_3,
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    INLINE_CODE,
    BULLET_LIST,
    NUMBERED_LIST,
    TASK_LIST,
    QUOTE,
    CODE_BLOCK,
    TABLE,
    LINK,
}

/**
 * Pure editing commands shared by the toolbar and unit tests. Keeping selection
 * handling here prevents the Compose screen from becoming a state machine.
 */
object MarkdownEditing {

    private const val AUTO_FORMAT_LIMIT = 1_500_000

    fun apply(value: TextFieldValue, format: MarkdownFormat): TextFieldValue = when (format) {
        MarkdownFormat.HEADING_1 -> setHeading(value, 1)
        MarkdownFormat.HEADING_2 -> setHeading(value, 2)
        MarkdownFormat.HEADING_3 -> setHeading(value, 3)
        MarkdownFormat.BOLD -> wrapSelection(value, "**", "**", "粗体")
        MarkdownFormat.ITALIC -> wrapSelection(value, "*", "*", "斜体")
        MarkdownFormat.STRIKETHROUGH -> wrapSelection(value, "~~", "~~", "删除线")
        MarkdownFormat.INLINE_CODE -> wrapSelection(value, "`", "`", "代码")
        MarkdownFormat.BULLET_LIST -> toggleLinePrefix(value, LinePrefix.BULLET)
        MarkdownFormat.NUMBERED_LIST -> toggleLinePrefix(value, LinePrefix.NUMBERED)
        MarkdownFormat.TASK_LIST -> toggleLinePrefix(value, LinePrefix.TASK)
        MarkdownFormat.QUOTE -> toggleLinePrefix(value, LinePrefix.QUOTE)
        MarkdownFormat.CODE_BLOCK -> insertCodeBlock(value)
        MarkdownFormat.TABLE -> insertTable(value)
        MarkdownFormat.LINK -> insertLink(value)
    }

    fun wrapSelection(
        value: TextFieldValue,
        prefix: String,
        suffix: String,
        placeholder: String,
    ): TextFieldValue {
        val start = value.selection.min.collapse(value)
        val end = value.selection.max.collapse(value)
        val selected = value.text.substring(start, end)
        val shouldUnwrap = selected.isEmpty() &&
            start >= prefix.length &&
            end + suffix.length <= value.text.length &&
            value.text.substring(start - prefix.length, start) == prefix &&
            value.text.substring(end, end + suffix.length) == suffix

        if (shouldUnwrap) {
            val newText = value.text.replaceRange(start - prefix.length, end + suffix.length, selected)
            return value.copy(
                text = newText,
                selection = TextRange(start - prefix.length),
                composition = null,
            )
        }

        val content = selected.ifEmpty { placeholder }
        val newText = value.text.replaceRange(start, end, prefix + content + suffix)
        val contentStart = start + prefix.length
        val contentEnd = contentStart + content.length
        return value.copy(
            text = newText,
            selection = if (selected.isEmpty()) {
                TextRange(contentStart, contentEnd)
            } else {
                TextRange(contentStart, contentEnd)
            },
            composition = null,
        )
    }

    private fun setHeading(value: TextFieldValue, level: Int): TextFieldValue {
        val lines = selectedLines(value)
        val nonBlank = lines.filter { value.text.substring(it.start, it.end).isNotBlank() }
        val targetLines = nonBlank.ifEmpty { lines }
        if (targetLines.isEmpty()) return value

        val desired = "#".repeat(level) + " "
        val active = targetLines.all { line ->
            val content = value.text.substring(line.start, line.end)
            val indentLength = content.takeWhile { it == ' ' || it == '\t' }.length
            content.substring(indentLength).startsWith(desired)
        }

        val edits = targetLines.map { line ->
            val content = value.text.substring(line.start, line.end)
            val indentLength = content.takeWhile { it == ' ' || it == '\t' }.length
            val withoutIndent = content.substring(indentLength)
            val headingPrefix = HEADING_PREFIX.find(withoutIndent)
            val existingLength = headingPrefix?.value?.length ?: 0
            val replacement = if (active) "" else desired
            Edit(
                start = line.start + indentLength,
                end = line.start + indentLength + existingLength,
                text = replacement,
            )
        }
        return applyEdits(value, edits)
    }

    private enum class LinePrefix {
        BULLET,
        NUMBERED,
        TASK,
        QUOTE,
    }

    private fun toggleLinePrefix(value: TextFieldValue, prefix: LinePrefix): TextFieldValue {
        val lines = selectedLines(value)
        val nonBlank = lines.filter { value.text.substring(it.start, it.end).isNotBlank() }
        val target = nonBlank.ifEmpty { lines }
        if (target.isEmpty()) return value

        fun existingLength(line: LineRange): Int {
            val content = value.text.substring(line.start, line.end)
            val indent = content.takeWhile { it == ' ' || it == '\t' }.length
            val rest = content.substring(indent)
            return when (prefix) {
                LinePrefix.BULLET -> BULLET_PREFIX.find(rest)?.value?.length ?: 0
                LinePrefix.NUMBERED -> NUMBERED_PREFIX.find(rest)?.value?.length ?: 0
                LinePrefix.TASK -> TASK_LINE_PREFIX.find(rest)?.value?.length ?: 0
                LinePrefix.QUOTE -> QUOTE_PREFIX.find(rest)?.value?.length ?: 0
            }
        }

        val active = target.all { existingLength(it) > 0 }
        val inserted = when (prefix) {
            LinePrefix.BULLET -> "- "
            LinePrefix.NUMBERED -> "1. "
            LinePrefix.TASK -> "- [ ] "
            LinePrefix.QUOTE -> "> "
        }

        val edits = target.map { line ->
            val content = value.text.substring(line.start, line.end)
            val indent = content.takeWhile { it == ' ' || it == '\t' }.length
            val length = existingLength(line)
            Edit(
                start = line.start + indent,
                end = line.start + indent + if (active) length else 0,
                text = if (active) "" else inserted,
            )
        }
        return applyEdits(value, edits)
    }

    private fun insertCodeBlock(value: TextFieldValue): TextFieldValue {
        val start = value.selection.min.collapse(value)
        val end = value.selection.max.collapse(value)
        val lineBefore = value.text.lastIndexOf('\n', start - 1) + 1
        val atLineStart = start == lineBefore
        val leadingNewline = if (start == 0 || atLineStart || value.text[start - 1] == '\n') {
            ""
        } else {
            "\n"
        }
        val snippet = "${leadingNewline}```\n\n```\n"
        val contentStart = start + leadingNewline.length + 4
        val newText = value.text.replaceRange(start, end, snippet)
        return value.copy(
            text = newText,
            selection = TextRange(contentStart),
            composition = null,
        )
    }

    private fun insertTable(value: TextFieldValue): TextFieldValue {
        val start = value.selection.min.collapse(value)
        val end = value.selection.max.collapse(value)
        val snippet = """
            | 列 1 | 列 2 |
            | --- | --- |
            | 内容 | 内容 |
        """.trimIndent()
        val leading = if (start == 0 || value.text[start - 1] == '\n') "" else "\n"
        val newText = value.text.replaceRange(start, end, "$leading$snippet\n")
        val firstPlaceholder = newText.indexOf("列 1", startIndex = start)
        return value.copy(
            text = newText,
            selection = TextRange(firstPlaceholder, firstPlaceholder + 3),
            composition = null,
        )
    }

    private fun insertLink(value: TextFieldValue): TextFieldValue {
        val start = value.selection.min.collapse(value)
        val end = value.selection.max.collapse(value)
        val selected = value.text.substring(start, end)
        val label = selected.ifEmpty { "链接文字" }
        val insertion = "[$label](https://)"
        val newText = value.text.replaceRange(start, end, insertion)
        val selectionStart = if (selected.isEmpty()) {
            start + 1
        } else {
            start + label.length + 3
        }
        val selectionEnd = if (selected.isEmpty()) {
            selectionStart + label.length
        } else {
            selectionStart + "https://".length
        }
        return value.copy(
            text = newText,
            selection = TextRange(selectionStart, selectionEnd),
            composition = null,
        )
    }

    /**
     * Applies Markdown-aware behavior when the user presses Enter:
     * continues lists/quotes, exits an empty list item and creates a closing
     * fence after an opening code fence. All other edits are returned untouched.
     */
    fun applyAutoFormatting(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        if (new.text.length != old.text.length + 1) return new
        if (new.text.length > AUTO_FORMAT_LIMIT) return new

        val commonPrefix = commonPrefixLength(old.text, new.text)
        if (new.text.getOrNull(commonPrefix) != '\n') return new
        if (!commonPrefixSuffixMatches(old.text, new.text, commonPrefix)) return new

        val caretAfterNewline = commonPrefix + 1
        if (caretAfterNewline <= 0) return new

        val previousLineEnd = caretAfterNewline - 1
        val previousLineStart = new.text.lastIndexOf('\n', previousLineEnd - 1) + 1
        val previousLine = new.text.substring(previousLineStart, previousLineEnd).removeSuffix("\r")

        // Opening fence -> automatically add the closing fence and leave the
        // caret on a blank content line.
        val fence = FENCE_LINE.matchEntire(previousLine.trim())
        if (fence != null && !isInsideFence(new.text, previousLineStart)) {
            val marker = fence.groupValues[1]
            val closing = "\n$marker"
            val updated = new.text.substring(0, caretAfterNewline) +
                closing +
                new.text.substring(caretAfterNewline)
            return new.copy(
                text = updated,
                selection = TextRange(caretAfterNewline),
                composition = null,
            )
        }

        if (isInsideFence(new.text, previousLineStart)) return new

        val quote = QUOTE_LINE.find(previousLine)
        if (quote != null) {
            val body = quote.groupValues[2]
            return if (body.isBlank()) {
                new.copy(
                    text = new.text.removeRange(
                        previousLineStart + quote.groups[1]!!.range.first,
                        previousLineEnd,
                    ),
                    selection = TextRange(
                        caretAfterNewline - (previousLineEnd - previousLineStart),
                    ),
                    composition = null,
                )
            } else {
                val prefix = quote.groupValues[1] + "> "
                insertAtCaret(new, caretAfterNewline, prefix)
            }
        }

        val list = LIST_LINE.find(previousLine) ?: return new
        val indentation = list.groupValues[1]
        val marker = list.groupValues[2]
        val body = list.groupValues[4]
        if (body.isBlank()) {
            val removeStart = previousLineStart + list.groups[2]!!.range.first
            return new.copy(
                text = new.text.removeRange(removeStart, previousLineEnd),
                selection = TextRange(caretAfterNewline - (previousLineEnd - removeStart)),
                composition = null,
            )
        }

        val nextPrefix = when {
            marker.first().isDigit() -> {
                val number = marker.dropLast(1).toIntOrNull() ?: 1
                val delimiter = marker.last()
                "$indentation${number + 1}$delimiter "
            }
            body.startsWith("[ ]") || body.startsWith("[x]", ignoreCase = true) ->
                "$indentation$marker [ ] "
            else -> "$indentation$marker "
        }
        return insertAtCaret(new, caretAfterNewline, nextPrefix)
    }

    private fun insertAtCaret(value: TextFieldValue, caret: Int, text: String): TextFieldValue {
        val newText = value.text.substring(0, caret) + text + value.text.substring(caret)
        return value.copy(
            text = newText,
            selection = TextRange(caret + text.length),
            composition = null,
        )
    }

    /**
     * A conservative fence test: scans the previous 800 lines only, so even a
     * very large document does not stall a keystroke.
     */
    private fun isInsideFence(text: String, beforeIndex: Int): Boolean {
        var scanEnd = beforeIndex
        var fenceChar: Char? = null
        var fenceLength = 0
        var inspectedLines = 0
        while (scanEnd > 0 && inspectedLines < 800) {
            val lineEnd = scanEnd - 1
            if (lineEnd < 0) break
            val lineStart = text.lastIndexOf('\n', lineEnd - 1) + 1
            val line = text.substring(lineStart, lineEnd).trim()
            val match = FENCE_ANY.matchEntire(line)
            if (match != null) {
                val marker = match.groupValues[1]
                if (fenceChar == null) {
                    fenceChar = marker.first()
                    fenceLength = marker.length
                } else if (
                    marker.first() == fenceChar &&
                    marker.length >= fenceLength
                ) {
                    fenceChar = null
                    fenceLength = 0
                }
            }
            if (lineStart == 0) break
            scanEnd = lineStart
            inspectedLines++
        }
        return fenceChar != null
    }

    private data class LineRange(val start: Int, val end: Int)

    private fun selectedLines(value: TextFieldValue): List<LineRange> {
        val text = value.text
        val start = value.selection.min.collapse(value)
        var end = value.selection.max.collapse(value)
        if (end > start && end > 0 && end > text.lastIndex && text.getOrNull(end - 1) == '\n') {
            end--
        }
        val firstLineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let {
            if (start == 0) 0 else text.lastIndexOf('\n', start - 1) + 1
        }
        val lastLineEnd = if (end >= text.length) {
            text.length
        } else {
            text.indexOf('\n', end).let { if (it < 0) text.length else it }
        }
        val lines = mutableListOf<LineRange>()
        var position = firstLineStart
        while (position <= lastLineEnd) {
            val next = if (position >= text.length) text.length else {
                text.indexOf('\n', position).let { if (it < 0) text.length else it }
            }
            lines += LineRange(position, next)
            if (next >= lastLineEnd || next >= text.length) break
            position = next + 1
        }
        return lines
    }

    private data class Edit(val start: Int, val end: Int, val text: String)

    private fun applyEdits(value: TextFieldValue, rawEdits: List<Edit>): TextFieldValue {
        val edits = rawEdits
            .filter { it.start in 0..value.text.length && it.end in it.start..value.text.length }
            .sortedBy { it.start }
        if (edits.isEmpty()) return value

        val newText = StringBuilder(value.text.length + edits.size * 2)
        var cursor = 0
        for (edit in edits) {
            if (edit.start < cursor) continue
            newText.append(value.text, cursor, edit.start)
            newText.append(edit.text)
            cursor = edit.end
        }
        newText.append(value.text, cursor, value.text.length)

        val oldStart = value.selection.min.collapse(value)
        val oldEnd = value.selection.max.collapse(value)
        val newStart = mapOffset(oldStart, edits)
        val newEnd = mapOffset(oldEnd, edits)
        return value.copy(
            text = newText.toString(),
            selection = TextRange(newStart.coerceAtMost(newText.length), newEnd.coerceAtMost(newText.length)),
            composition = null,
        )
    }

    private fun mapOffset(position: Int, edits: List<Edit>): Int {
        var delta = 0
        for (edit in edits) {
            if (position < edit.start) break
            if (position >= edit.end) {
                delta += edit.text.length - (edit.end - edit.start)
            } else {
                return edit.start + delta + (position - edit.start).coerceAtMost(edit.text.length)
            }
        }
        return position + delta
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var i = 0
        while (i < limit && a[i] == b[i]) i++
        return i
    }

    private fun commonPrefixSuffixMatches(old: String, new: String, prefix: Int): Boolean {
        val oldSuffixLength = old.length - prefix
        if (oldSuffixLength < 0) return false
        if (new.length < prefix + 1 + oldSuffixLength) return false
        val newSuffixStart = new.length - oldSuffixLength
        if (newSuffixStart < prefix + 1) return false
        return old.regionMatches(prefix, new, newSuffixStart, oldSuffixLength)
    }

    private val HEADING_PREFIX = Regex("""^#{1,6}[ \t]+""")
    private val BULLET_PREFIX = Regex("""^[-*+][ \t]+""")
    private val NUMBERED_PREFIX = Regex("""^\d{1,9}[.)][ \t]+""")
    private val TASK_LINE_PREFIX = Regex("""^[-*+][ \t]+\[[ xX]\][ \t]+""")
    private val QUOTE_PREFIX = Regex("""^>[ \t]?""")
    private val FENCE_LINE = Regex("""^(`{3,}|~{3,})[^`~]*$""")
    private val FENCE_ANY = Regex("""^(`{3,}|~{3,})(?:[^`~]*)$""")
    private val QUOTE_LINE = Regex("""^([ \t]*)>(.*)$""")
    private val LIST_LINE = Regex("""^([ \t]*)([-*+]|\d{1,9}[.)])([ \t]+)(.*)$""")

    private val TextRange.min: Int get() = minOf(start, end)
    private val TextRange.max: Int get() = maxOf(start, end)

    private fun Int.collapse(value: TextFieldValue): Int = coerceIn(0, value.text.length)
}
