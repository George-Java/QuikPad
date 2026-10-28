package com.yg.liquidink.markdown

/**
 * Linear, allocation-conscious Markdown parser for the app's supported feature
 * set. It runs off the main thread and keeps source ranges for every block.
 *
 * Supported blocks: ATX + setext headings, paragraphs, block quotes, ordered /
 * unordered / task lists, fenced code, tables, standalone images, thematic
 * breaks and footnote definitions.
 */
class MarkdownParser(
    private val inlineParser: InlineParser = InlineParser(),
) {

    fun parse(source: String): ParsedMarkdown {
        if (source.isBlank()) return ParsedMarkdown.Empty

        val lines = splitLines(source)
        val blocks = mutableListOf<MdBlock>()
        val footnotes = LinkedHashMap<String, MdFootnoteDefinition>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]

            if (line.text.isBlank()) {
                index++
                continue
            }

            val fence = FENCE_OPEN.matchEntire(line.text)
            if (fence != null) {
                val parsed = parseFence(lines, index, fence)
                blocks += parsed.block
                index = parsed.nextIndex
                continue
            }

            val atx = ATX_HEADING.matchEntire(line.text)
            if (atx != null) {
                val level = atx.groupValues[1].length
                val raw = atx.groupValues.getOrNull(2).orEmpty()
                    .replace(CLOSING_HASHES, "")
                    .trim()
                blocks += MdBlock.Heading(
                    level = level,
                    inlines = inlineParser.parse(raw),
                    start = line.start,
                    end = line.end,
                )
                index++
                continue
            }

            val footnoteMatch = FOOTNOTE_DEFINITION.matchEntire(line.text)
            if (footnoteMatch != null) {
                val label = footnoteMatch.groupValues[1]
                val builder = StringBuilder(footnoteMatch.groupValues[2])
                var end = line.end
                var next = index + 1
                while (next < lines.size) {
                    val continuation = lines[next]
                    if (continuation.text.isBlank()) break
                    if (
                        continuation.text.startsWith("  ") ||
                        continuation.text.startsWith("\t")
                    ) {
                        if (builder.isNotEmpty()) builder.append('\n')
                        builder.append(continuation.text.trimStart())
                        end = continuation.end
                        next++
                    } else {
                        break
                    }
                }
                footnotes.putIfAbsent(
                    label,
                    MdFootnoteDefinition(
                        label = label,
                        inlines = inlineParser.parse(builder.toString()),
                        start = line.start,
                        end = end,
                    ),
                )
                index = next
                continue
            }

            val quote = QUOTE_LINE.matchEntire(line.text)
            if (quote != null) {
                val quoteLines = mutableListOf<List<MdInline>>()
                var end = line.end
                var next = index
                while (next < lines.size) {
                    val match = QUOTE_LINE.matchEntire(lines[next].text) ?: break
                    quoteLines += inlineParser.parse(match.groupValues[1])
                    end = lines[next].end
                    next++
                }
                blocks += MdBlock.Quote(
                    lines = quoteLines,
                    start = line.start,
                    end = end,
                )
                index = next
                continue
            }

            val listMatch = LIST_LINE.matchEntire(line.text)
            if (listMatch != null) {
                val parsed = parseList(lines, index, listMatch)
                blocks += parsed.block
                index = parsed.nextIndex
                continue
            }

            if (isTableStart(lines, index)) {
                val table = parseTable(lines, index)
                blocks += table.block
                index = table.nextIndex
                continue
            }

            if (THEMATIC_BREAK.matches(line.text)) {
                blocks += MdBlock.ThematicBreak(start = line.start, end = line.end)
                index++
                continue
            }

            val imageMatch = STANDALONE_IMAGE.matchEntire(line.text)
            if (imageMatch != null) {
                blocks += MdBlock.Image(
                    alt = imageMatch.groupValues[1],
                    destination = imageMatch.groupValues[2].removeSurrounding("<", ">"),
                    title = imageMatch.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() },
                    start = line.start,
                    end = line.end,
                )
                index++
                continue
            }

            // Everything else starts a paragraph. A single paragraph may span
            // several lines and may become a setext heading when underlined.
            val start = line.start
            var contentEnd = line.end
            var scan = index + 1
            var setextLevel = 0
            var paragraphConsumedEnd = line.end

            while (scan < lines.size) {
                val candidate = lines[scan]
                if (candidate.text.isBlank()) break

                val underline = setextUnderlineLevel(candidate.text)
                if (underline > 0) {
                    setextLevel = underline
                    paragraphConsumedEnd = candidate.end
                    scan++
                    break
                }

                if (isBlockStart(lines, scan)) break

                contentEnd = candidate.end
                paragraphConsumedEnd = candidate.end
                scan++
            }

            if (setextLevel > 0) {
                blocks += MdBlock.Heading(
                    level = setextLevel,
                    inlines = inlineParser.parse(source.substring(start, contentEnd)),
                    start = start,
                    end = paragraphConsumedEnd,
                )
            } else {
                blocks += MdBlock.Paragraph(
                    inlines = inlineParser.parse(source.substring(start, contentEnd)),
                    start = start,
                    end = contentEnd,
                )
            }
            index = scan
        }

        return ParsedMarkdown(
            blocks = blocks,
            footnotes = footnotes.values.toList(),
        )
    }

    private data class FenceResult(val block: MdBlock.CodeBlock, val nextIndex: Int)

    private fun parseFence(
        lines: List<LineInfo>,
        startIndex: Int,
        opener: MatchResult,
    ): FenceResult {
        val openingMarker = opener.groupValues[1]
        val fenceChar = openingMarker.first()
        val fenceLength = openingMarker.length
        val info = opener.groupValues[2].trim()
        val language = info.substringBefore(' ').substringBefore('\t')
        val code = StringBuilder()
        val start = lines[startIndex].start
        var end = lines[startIndex].end
        var nextIndex = startIndex + 1
        var closed = false

        while (nextIndex < lines.size) {
            val line = lines[nextIndex]
            val indent = line.text.takeWhile { it == ' ' || it == '\t' }
            val trimmed = line.text.trim(' ', '\t')
            val isClosing = indentColumns(indent) <= 3 &&
                trimmed.length >= fenceLength &&
                trimmed.all { it == fenceChar }
            if (isClosing) {
                closed = true
                end = line.end
                nextIndex++
                break
            }
            if (code.isNotEmpty()) code.append('\n')
            code.append(line.text)
            end = line.end
            nextIndex++
        }

        if (!closed && lines.isNotEmpty()) {
            end = lines.last().end
        }
        return FenceResult(
            block = MdBlock.CodeBlock(
                language = language.take(32),
                code = code.toString(),
                closed = closed,
                start = start,
                end = end,
            ),
            nextIndex = nextIndex,
        )
    }

    private data class ListResult(val block: MdBlock.ListBlock, val nextIndex: Int)

    private fun parseList(
        lines: List<LineInfo>,
        startIndex: Int,
        first: MatchResult,
    ): ListResult {
        val ordered = first.groupValues[2].first().isDigit()
        val startNumber = if (ordered) {
            first.groupValues[2].dropLast(1).toIntOrNull() ?: 1
        } else {
            1
        }
        val items = mutableListOf<MdListItem>()
        var nextIndex = startIndex

        while (nextIndex < lines.size) {
            val line = lines[nextIndex]
            val match = LIST_LINE.matchEntire(line.text)
            if (match != null) {
                val thisOrdered = match.groupValues[2].first().isDigit()
                if (thisOrdered != ordered) break

                val indent = indentColumns(match.groupValues[1])
                val marker = match.groupValues[2]
                val contentGroup = match.groups[4]
                val contentStart = line.start + (contentGroup?.range?.first ?: line.text.length)
                var content = match.groupValues[4]
                var checked: Boolean? = null
                var taskMarkerStart: Int? = null
                var taskMarkerEnd: Int? = null

                val task = TASK_PREFIX.find(content)
                if (task != null) {
                    checked = task.groupValues[1].equals("x", ignoreCase = true)
                    taskMarkerStart = contentStart
                    taskMarkerEnd = contentStart + 3
                    content = content.substring(task.range.last + 1)
                }

                val number = if (ordered) marker.dropLast(1).toIntOrNull() else null
                items += MdListItem(
                    inlines = inlineParser.parse(content),
                    checked = checked,
                    level = (indent / 2).coerceIn(0, 8),
                    number = number,
                    taskMarkerStart = taskMarkerStart,
                    taskMarkerEnd = taskMarkerEnd,
                    sourceStart = line.start,
                    sourceEnd = line.end,
                )
                nextIndex++
                continue
            }

            if (line.text.isBlank()) break

            val continuationIndent = line.text.takeWhile { it == ' ' || it == '\t' }
            val continuation = items.lastOrNull() != null &&
                continuationIndent.isNotEmpty() &&
                indentColumns(continuationIndent) > 0
            if (!continuation) break

            val item = items.last()
            val rawContinuation = line.text.trim()
            val current = buildInlineSource(item.inlines)
            val merged = buildString {
                if (current.isNotEmpty()) {
                    append(current)
                    append('\n')
                }
                append(rawContinuation)
            }
            items[items.lastIndex] = item.copy(
                inlines = inlineParser.parse(merged),
                sourceEnd = line.end,
            )
            nextIndex++
        }

        return ListResult(
            block = MdBlock.ListBlock(
                ordered = ordered,
                startNumber = startNumber,
                items = items,
                start = lines[startIndex].start,
                end = lines.getOrNull(nextIndex - 1)?.end ?: lines[startIndex].end,
            ),
            nextIndex = nextIndex,
        )
    }

    private data class TableResult(val block: MdBlock.Table, val nextIndex: Int)

    private fun parseTable(
        lines: List<LineInfo>,
        startIndex: Int,
    ): TableResult {
        val headerCells = splitTableRow(lines[startIndex].text)
        val delimiterCells = splitTableRow(lines[startIndex + 1].text)
        val alignments = delimiterCells.map { cell -> alignmentOf(cell) }
        val columns = maxOf(headerCells.size, alignments.size)

        val headers = padCells(headerCells, columns).map(inlineParser::parse)
        val rows = mutableListOf<List<List<MdInline>>>()
        var end = lines[startIndex + 1].end
        var next = startIndex + 2

        while (next < lines.size) {
            val line = lines[next]
            if (line.text.isBlank() || !line.text.contains('|')) break
            if (isTableStart(lines, next)) break
            val cells = splitTableRow(line.text)
            if (cells.isEmpty()) break
            rows += padCells(cells, columns).map(inlineParser::parse)
            end = line.end
            next++
        }

        return TableResult(
            block = MdBlock.Table(
                headers = headers,
                alignments = alignments + List((columns - alignments.size).coerceAtLeast(0)) { TableAlignment.LEFT },
                rows = rows,
                start = lines[startIndex].start,
                end = end,
            ),
            nextIndex = next,
        )
    }

    private fun isTableStart(lines: List<LineInfo>, index: Int): Boolean {
        if (index + 1 >= lines.size) return false
        val header = lines[index].text
        val delimiter = lines[index + 1].text
        if (!header.contains('|') || !delimiter.contains('|')) return false
        if (!delimiter.contains('-')) return false

        val headerCells = splitTableRow(header)
        val delimiterCells = splitTableRow(delimiter)
        if (headerCells.isEmpty() || delimiterCells.isEmpty()) return false
        if (headerCells.size != delimiterCells.size) return false
        return delimiterCells.all { DELIMITER_CELL.matches(it.trim()) }
    }

    private fun isBlockStart(lines: List<LineInfo>, index: Int): Boolean {
        val text = lines[index].text
        if (text.isBlank()) return true
        if (FENCE_OPEN.matches(text)) return true
        if (ATX_HEADING.matches(text)) return true
        if (QUOTE_LINE.matches(text)) return true
        if (LIST_LINE.matches(text)) return true
        if (FOOTNOTE_DEFINITION.matches(text)) return true
        if (THEMATIC_BREAK.matches(text)) return true
        if (STANDALONE_IMAGE.matches(text)) return true
        if (isTableStart(lines, index)) return true
        return false
    }

    private fun splitTableRow(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()

        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        var codeTicks = 0
        while (i < trimmed.length) {
            val c = trimmed[i]
            if (c == '\\' && i + 1 < trimmed.length) {
                current.append(c).append(trimmed[i + 1])
                i += 2
                continue
            }
            if (c == '`') {
                val run = countCharacterRun(trimmed, i, '`')
                if (codeTicks == 0) codeTicks = run else if (run == codeTicks) codeTicks = 0
                repeat(run) { current.append('`') }
                i += run
                continue
            }
            if (c == '|' && codeTicks == 0) {
                cells += current.toString()
                current.clear()
                i++
                continue
            }
            current.append(c)
            i++
        }
        cells += current.toString()

        if (cells.isNotEmpty() && cells.first().isBlank()) cells.removeAt(0)
        if (cells.isNotEmpty() && cells.last().isBlank()) cells.removeAt(cells.lastIndex)
        return cells.map { it.trim() }
    }

    private fun padCells(cells: List<String>, columns: Int): List<String> {
        if (cells.size >= columns) return cells.take(columns)
        return cells + List(columns - cells.size) { "" }
    }

    private fun alignmentOf(cell: String): TableAlignment {
        val trim = cell.trim()
        return when {
            trim.startsWith(":") && trim.endsWith(":") -> TableAlignment.CENTER
            trim.endsWith(":") -> TableAlignment.RIGHT
            else -> TableAlignment.LEFT
        }
    }

    private fun setextUnderlineLevel(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.length < 1) return 0
        val indent = text.takeWhile { it == ' ' || it == '\t' }
        if (indentColumns(indent) > 3) return 0
        if (trimmed.all { it == '=' }) return 1
        if (trimmed.all { it == '-' }) return 2
        return 0
    }

    private fun indentColumns(indent: String): Int {
        var columns = 0
        for (char in indent) {
            columns += if (char == '\t') 4 else 1
        }
        return columns
    }

    private fun countCharacterRun(text: String, start: Int, char: Char): Int {
        var i = start
        while (i < text.length && text[i] == char) i++
        return i - start
    }

    /**
     * A compact reconstruction used only for list continuation lines. It keeps
     * the original inline content and loses formatting markers, which is good
     * enough for a continuation paragraph; the first line remains exact.
     */
    private fun buildInlineSource(inlines: List<MdInline>): String = buildString {
        fun appendAll(nodes: List<MdInline>) {
            nodes.forEach { node ->
                when (node) {
                    is MdInline.Text -> append(node.text)
                    is MdInline.Code -> append('`').append(node.text).append('`')
                    is MdInline.Bold -> append("**").also { appendAll(node.children) }.append("**")
                    is MdInline.Italic -> append('*').also { appendAll(node.children) }.append('*')
                    is MdInline.Strike -> append("~~").also { appendAll(node.children) }.append("~~")
                    is MdInline.Link -> {
                        append('[')
                        appendAll(node.children)
                        append("](")
                        append(node.destination)
                        append(')')
                    }
                    is MdInline.InlineImage -> {
                        append("![")
                        append(node.alt)
                        append("](")
                        append(node.destination)
                        append(')')
                    }
                    is MdInline.FootnoteRef -> append("[^").append(node.label).append(']')
                }
            }
        }
        appendAll(inlines)
    }

    private data class LineInfo(val text: String, val start: Int, val end: Int)

    private fun splitLines(source: String): List<LineInfo> {
        val lines = ArrayList<LineInfo>(source.length / 24 + 4)
        var lineStart = 0
        while (lineStart <= source.length) {
            val newline = source.indexOf('\n', lineStart)
            val lineEnd = if (newline < 0) source.length else newline
            val contentEnd = if (lineEnd > lineStart && source[lineEnd - 1] == '\r') {
                lineEnd - 1
            } else {
                lineEnd
            }
            lines += LineInfo(
                text = source.substring(lineStart, contentEnd),
                start = lineStart,
                end = contentEnd,
            )
            if (newline < 0) break
            lineStart = newline + 1
        }
        return lines
    }

    private companion object {
        val FENCE_OPEN = Regex("""^ {0,3}(`{3,}|~{3,})([^\r\n]*)$""")
        val ATX_HEADING = Regex("""^ {0,3}(#{1,6})(?:[ \t]+(.*?))?[ \t]*$""")
        val CLOSING_HASHES = Regex("""[ \t]+#+[ \t]*$""")
        val QUOTE_LINE = Regex("""^ {0,3}>[ \t]?(.*)$""")
        val LIST_LINE = Regex("""^([ \t]*)([-*+]|\d{1,9}[.)])([ \t]+)(.*)$""")
        val TASK_PREFIX = Regex("""^\[([ xX])\](?:[ \t]+|$)""")
        val FOOTNOTE_DEFINITION = Regex("""^ {0,3}\[\^([^\]\s]+)\]:[ \t]*(.*)$""")
        val THEMATIC_BREAK = Regex("""^ {0,3}((?:\*[ \t]*){3,}|(?:-[ \t]*){3,}|(?:_[ \t]*){3,})$""")
        val STANDALONE_IMAGE = Regex(
            """^ {0,3}!\[((?:\\.|[^\]])*)]\(\s*(<[^>]*>|[^\s)]+)(?:\s+["']([^"']*)["'])?\s*\)\s*$""",
        )
        val DELIMITER_CELL = Regex("""^:?-+:?$""")
    }
}
