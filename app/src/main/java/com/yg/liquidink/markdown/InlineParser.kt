package com.yg.liquidink.markdown

/**
 * A deliberately small, allocation-conscious inline Markdown parser.
 *
 * It covers the inline syntax needed by the app: emphasis, strong emphasis,
 * strikethrough, code spans, links, images, autolinks, footnote references and
 * backslash escapes. Unknown syntax is preserved as text instead of dropped.
 */
class InlineParser {

    fun parse(text: String): List<MdInline> {
        if (text.isEmpty()) return emptyList()
        return parseInternal(text, depth = 0)
    }

    private fun parseInternal(text: String, depth: Int): List<MdInline> {
        if (depth > MAX_NESTING) return listOf(MdInline.Text(text))

        val out = mutableListOf<MdInline>()
        var i = 0

        while (i < text.length) {
            val c = text[i]

            if (c == '\\' && i + 1 < text.length && isEscapable(text[i + 1])) {
                out.appendText(text[i + 1].toString())
                i += 2
                continue
            }

            if (c == '`') {
                val tickCount = countRun(text, i, '`')
                val closing = findClosingRun(text, i + tickCount, tickCount, '`')
                if (closing >= 0) {
                    var code = text.substring(i + tickCount, closing)
                    if (
                        code.length >= 2 &&
                        code.first() == ' ' &&
                        code.last() == ' ' &&
                        code.any { !it.isWhitespace() }
                    ) {
                        code = code.substring(1, code.length - 1)
                    }
                    out += MdInline.Code(code.replace('\n', ' '))
                    i = closing + tickCount
                    continue
                }
            }

            if (c == '!' && i + 1 < text.length && text[i + 1] == '[') {
                val link = parseBracketLink(text, bracketIndex = i + 1)
                if (link != null) {
                    out += MdInline.InlineImage(
                        alt = unescape(link.label),
                        destination = link.destination,
                        title = link.title,
                    )
                    i = link.endExclusive
                    continue
                }
            }

            if (c == '[') {
                if (i + 1 < text.length && text[i + 1] == '^') {
                    val close = text.indexOf(']', startIndex = i + 2)
                    if (close > i + 2) {
                        val label = text.substring(i + 2, close)
                        if (label.none { it.isWhitespace() || it == '[' || it == ']' }) {
                            out += MdInline.FootnoteRef(label)
                            i = close + 1
                            continue
                        }
                    }
                }

                val link = parseBracketLink(text, bracketIndex = i)
                if (link != null) {
                    out += MdInline.Link(
                        children = parseInternal(link.label, depth + 1).ifEmpty {
                            listOf(MdInline.Text(link.destination))
                        },
                        destination = link.destination,
                        title = link.title,
                    )
                    i = link.endExclusive
                    continue
                }
            }

            if (c == '<') {
                val close = text.indexOf('>', startIndex = i + 1)
                if (close > i + 1) {
                    val inside = text.substring(i + 1, close)
                    if (isAutolink(inside)) {
                        val destination = if (inside.contains('@') && !inside.contains("://")) {
                            "mailto:$inside"
                        } else {
                            inside
                        }
                        out += MdInline.Link(
                            children = listOf(MdInline.Text(inside)),
                            destination = destination,
                        )
                        i = close + 1
                        continue
                    }
                }
            }

            if (c == '~' && i + 1 < text.length && text[i + 1] == '~') {
                val closed = findEmphasisClose(text, i + 2, "~~")
                if (closed > i + 2) {
                    val children = parseInternal(text.substring(i + 2, closed), depth + 1)
                    out += MdInline.Strike(children)
                    i = closed + 2
                    continue
                }
            }

            if (c == '*' || c == '_') {
                val delimiterChar = c
                val run = countRun(text, i, delimiterChar)
                val openerAllowed = isLeftFlanking(text, i, run, delimiterChar)
                if (openerAllowed) {
                    val markerLength = when {
                        run >= 3 && findEmphasisClose(
                            text,
                            i + 3,
                            "${delimiterChar}${delimiterChar}${delimiterChar}",
                        ) > i + 3 -> 3
                        run >= 2 && findEmphasisClose(
                            text,
                            i + 2,
                            "$delimiterChar$delimiterChar",
                        ) > i + 2 -> 2
                        findEmphasisClose(text, i + 1, delimiterChar.toString()) > i + 1 -> 1
                        else -> 0
                    }

                    if (markerLength > 0) {
                        val marker = delimiterChar.toString().repeat(markerLength)
                        val close = findEmphasisClose(text, i + markerLength, marker)
                        if (close > i + markerLength) {
                            val inner = parseInternal(
                                text.substring(i + markerLength, close),
                                depth + 1,
                            )
                            out += when (markerLength) {
                                3 -> MdInline.Bold(listOf(MdInline.Italic(inner)))
                                2 -> MdInline.Bold(inner)
                                else -> MdInline.Italic(inner)
                            }
                            i = close + markerLength
                            continue
                        }
                    }
                }
            }

            out.appendText(c.toString())
            i++
        }

        return out
    }

    private data class BracketLink(
        val label: String,
        val destination: String,
        val title: String?,
        val endExclusive: Int,
    )

    private fun parseBracketLink(text: String, bracketIndex: Int): BracketLink? {
        if (bracketIndex >= text.length || text[bracketIndex] != '[') return null

        var i = bracketIndex + 1
        var nested = 0
        var closeBracket = -1
        while (i < text.length) {
            when {
                text[i] == '\\' && i + 1 < text.length -> i += 2
                text[i] == '[' -> {
                    nested++
                    i++
                }
                text[i] == ']' && nested == 0 -> {
                    closeBracket = i
                    i = text.length
                }
                text[i] == ']' -> {
                    nested--
                    i++
                }
                else -> i++
            }
        }
        if (closeBracket < 0 || closeBracket + 1 >= text.length) return null
        if (text[closeBracket + 1] != '(') return null

        val openParen = closeBracket + 1
        var depth = 0
        var closeParen = -1
        i = openParen + 1
        while (i < text.length) {
            when {
                text[i] == '\\' && i + 1 < text.length -> i += 2
                text[i] == '(' -> {
                    depth++
                    i++
                }
                text[i] == ')' && depth == 0 -> {
                    closeParen = i
                    i = text.length
                }
                text[i] == ')' -> {
                    depth--
                    i++
                }
                else -> i++
            }
        }
        if (closeParen < 0) return null

        val destination = parseDestination(text.substring(openParen + 1, closeParen)) ?: return null
        return BracketLink(
            label = text.substring(bracketIndex + 1, closeBracket),
            destination = unescape(destination.first).replace(" ", "%20"),
            title = destination.second?.let(::unescape),
            endExclusive = closeParen + 1,
        )
    }

    /**
     * Splits `url "optional title"` using a tiny state machine. Parentheses are
     * already balanced by the caller; angle-bracket destinations are accepted.
     */
    private fun parseDestination(raw: String): Pair<String, String?>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "" to null

        if (trimmed.startsWith('<')) {
            val close = trimmed.indexOf('>')
            if (close <= 1) return null
            val url = trimmed.substring(1, close)
            val title = trimmed.substring(close + 1).trim().trimMatchingQuotes()
            return url to title
        }

        var i = 0
        val url = StringBuilder()
        while (i < trimmed.length) {
            val c = trimmed[i]
            if (c == '\\' && i + 1 < trimmed.length) {
                url.append(c).append(trimmed[i + 1])
                i += 2
                continue
            }
            if (c.isWhitespace()) break
            url.append(c)
            i++
        }
        if (url.isEmpty()) return null

        val rest = trimmed.substring(i).trim()
        val title = when {
            rest.isEmpty() -> null
            rest.startsWith("\"") && rest.endsWith("\"") && rest.length >= 2 ->
                rest.substring(1, rest.length - 1)
            rest.startsWith("'") && rest.endsWith("'") && rest.length >= 2 ->
                rest.substring(1, rest.length - 1)
            rest.startsWith("(") && rest.endsWith(")") && rest.length >= 2 ->
                rest.substring(1, rest.length - 1)
            else -> null
        }
        return url.toString() to title
    }

    private fun findEmphasisClose(text: String, from: Int, marker: String): Int {
        if (from >= text.length) return -1
        var index = text.indexOf(marker, from)
        val delimiter = marker.firstOrNull() ?: return -1
        while (index >= 0) {
            val beforeIsSpace = index == 0 || text[index - 1].isWhitespace()
            val afterIndex = index + marker.length
            val afterIsAlnum = afterIndex < text.length && text[afterIndex].isLetterOrDigit()
            val intrawordUnderscore = delimiter == '_' && afterIsAlnum
            if (!isEscaped(text, index) && !beforeIsSpace && !intrawordUnderscore) {
                return index
            }
            var next = index + marker.length
            while (next < text.length && text[next] == delimiter) next++
            index = text.indexOf(marker, next)
        }
        return -1
    }

    private fun isLeftFlanking(text: String, index: Int, run: Int, delimiter: Char): Boolean {
        if (index + run >= text.length) return false
        val next = text[index + run]
        if (next.isWhitespace()) return false
        if (delimiter == '_' && index > 0 && text[index - 1].isLetterOrDigit()) return false
        return true
    }

    private fun findClosingRun(text: String, from: Int, run: Int, char: Char): Int {
        if (from >= text.length) return -1
        val marker = char.toString().repeat(run)
        var index = text.indexOf(marker, from)
        while (index >= 0) {
            val beforeOk = index == 0 || text[index - 1] != char
            val after = index + run
            val afterOk = after >= text.length || text[after] != char
            if (beforeOk && afterOk) return index
            index = text.indexOf(marker, index + run)
        }
        return -1
    }

    private fun countRun(text: String, start: Int, char: Char): Int {
        var i = start
        while (i < text.length && text[i] == char) i++
        return i - start
    }

    private fun isEscaped(text: String, index: Int): Boolean {
        var backslashes = 0
        var i = index - 1
        while (i >= 0 && text[i] == '\\') {
            backslashes++
            i--
        }
        return backslashes % 2 == 1
    }

    private fun isEscapable(char: Char): Boolean =
        char in "\\`*_{}[]()#+-.!|>~<"

    private fun unescape(text: String): String {
        if (!text.contains('\\')) return text
        val result = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == '\\' && i + 1 < text.length && isEscapable(text[i + 1])) {
                result.append(text[i + 1])
                i += 2
            } else {
                result.append(text[i])
                i++
            }
        }
        return result.toString()
    }

    private fun isAutolink(value: String): Boolean {
        if (value.any { it.isWhitespace() }) return false
        if (value.contains("://") && value.substringBefore("://").isNotBlank()) return true
        return EMAIL_LIKE.matches(value)
    }

    private fun MutableList<MdInline>.appendText(value: String) {
        if (value.isEmpty()) return
        val last = lastOrNull()
        if (last is MdInline.Text) {
            this[lastIndex] = last.copy(text = last.text + value)
        } else {
            add(MdInline.Text(value))
        }
    }

    private fun String.trimMatchingQuotes(): String? {
        if (length < 2) return null
        return when {
            startsWith("\"") && endsWith("\"") -> substring(1, length - 1)
            startsWith("'") && endsWith("'") -> substring(1, length - 1)
            startsWith("(") && endsWith(")") -> substring(1, length - 1)
            else -> null
        }
    }

    private companion object {
        const val MAX_NESTING = 12
        val EMAIL_LIKE = Regex("""^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""")
    }
}
