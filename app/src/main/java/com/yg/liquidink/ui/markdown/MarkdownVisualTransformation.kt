package com.yg.liquidink.ui.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.yg.liquidink.ui.theme.CodeFont

@Immutable
data class EditorSyntaxColors(
    val heading: Color,
    val marker: Color,
    val link: Color,
    val quote: Color,
    val inlineCode: Color,
    val inlineCodeBackground: Color,
    val fence: Color,
    val searchMatch: Color,
    val activeSearchMatch: Color,
)

@Composable
fun rememberEditorSyntaxColors(): EditorSyntaxColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        EditorSyntaxColors(
            heading = scheme.primary,
            marker = scheme.tertiary,
            link = scheme.secondary,
            quote = scheme.onSurfaceVariant,
            inlineCode = scheme.primary,
            inlineCodeBackground = scheme.primary.copy(alpha = 0.12f),
            fence = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            searchMatch = scheme.tertiary.copy(alpha = 0.28f),
            activeSearchMatch = scheme.primary.copy(alpha = 0.35f),
        )
    }
}

/**
 * Lightweight syntax decoration for the source editor. It deliberately keeps the
 * Markdown markers visible (Typora's "source + immediate rendering" hybrid) while
 * giving headings, quotes, code and matches enough visual hierarchy to scan.
 *
 * Above [MAX_DECORATED_CHARACTERS] the transformation falls back to identity so a
 * 1 MB+ file never pays a regex cost on every measure pass.
 */
class MarkdownVisualTransformation(
    private val colors: EditorSyntaxColors,
    private val searchMatches: List<IntRange> = emptyList(),
    private val activeMatch: IntRange? = null,
    private val maxDecoratedCharacters: Int = MAX_DECORATED_CHARACTERS,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.length > maxDecoratedCharacters) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val decorated = buildAnnotatedString {
            append(text.text)
            applyLineStyles(text.text)
            applyInlineStyles(text.text)
            applySearchStyles(text.text)
        }
        return TransformedText(decorated, OffsetMapping.Identity)
    }

    private fun AnnotatedString.Builder.applyLineStyles(source: String) {
        HEADING.findAll(source).forEach { match ->
            addStyle(
                SpanStyle(
                    color = colors.heading,
                    fontWeight = FontWeight.SemiBold,
                ),
                match.range.first,
                match.range.last + 1,
            )
            val markerEnd = match.groups[1]?.range?.last?.plus(1) ?: match.range.last + 1
            addStyle(
                SpanStyle(color = colors.marker.copy(alpha = 0.8f)),
                match.range.first,
                markerEnd,
            )
        }

        QUOTE.findAll(source).forEach { match ->
            addStyle(
                SpanStyle(color = colors.quote, fontStyle = FontStyle.Italic),
                match.range.first,
                match.range.last + 1,
            )
        }

        FENCE_LINE.findAll(source).forEach { match ->
            addStyle(
                SpanStyle(
                    color = colors.fence,
                    fontFamily = CodeFont,
                ),
                match.range.first,
                match.range.last + 1,
            )
        }

        LIST_MARKER.findAll(source).forEach { match ->
            addStyle(
                SpanStyle(color = colors.marker),
                match.range.first,
                match.range.last + 1,
            )
        }
    }

    private fun AnnotatedString.Builder.applyInlineStyles(source: String) {
        INLINE_CODE.findAll(source).forEach { match ->
            addStyle(
                SpanStyle(
                    color = colors.inlineCode,
                    background = colors.inlineCodeBackground,
                    fontFamily = CodeFont,
                ),
                match.range.first,
                match.range.last + 1,
            )
        }

        LINK.findAll(source).forEach { match ->
            val label = match.groups[1]
            val destination = match.groups[2]
            if (label != null) {
                addStyle(
                    SpanStyle(
                        color = colors.link,
                        fontWeight = FontWeight.Medium,
                    ),
                    label.range.first,
                    label.range.last + 1,
                )
            }
            if (destination != null) {
                addStyle(
                    SpanStyle(color = colors.link.copy(alpha = 0.62f)),
                    destination.range.first,
                    destination.range.last + 1,
                )
            }
        }

        BOLD.findAll(source).forEach { match ->
            val content = match.groups[1] ?: return@forEach
            addStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
                content.range.first,
                content.range.last + 1,
            )
        }

        STRIKE.findAll(source).forEach { match ->
            val content = match.groups[1] ?: return@forEach
            addStyle(
                SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                content.range.first,
                content.range.last + 1,
            )
        }
    }

    private fun AnnotatedString.Builder.applySearchStyles(source: String) {
        if (searchMatches.isEmpty()) return
        searchMatches.forEach { range ->
            val start = range.first.coerceIn(0, source.length)
            val end = (range.last + 1).coerceIn(start, source.length)
            if (start == end) return@forEach
            addStyle(
                SpanStyle(background = colors.searchMatch),
                start,
                end,
            )
        }
        activeMatch?.let { range ->
            val start = range.first.coerceIn(0, source.length)
            val end = (range.last + 1).coerceIn(start, source.length)
            if (start < end) {
                addStyle(
                    SpanStyle(
                        background = colors.activeSearchMatch,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    start,
                    end,
                )
            }
        }
    }

    private companion object {
        const val MAX_DECORATED_CHARACTERS = 140_000
        val HEADING = Regex("""(?m)^(#{1,6})[ \t].*$""")
        val QUOTE = Regex("""(?m)^[ \t]*>[^\n]*$""")
        val FENCE_LINE = Regex("""(?m)^[ \t]*(`{3,}|~{3,}).*$""")
        val LIST_MARKER = Regex("""(?m)^[ \t]*(?:[-*+]|\d{1,9}[.)])[ \t]+""")
        val INLINE_CODE = Regex("""`[^`\n]+`""")
        val LINK = Regex("""\[([^\]\n]+)]\(([^)\n]+)\)""")
        val BOLD = Regex("""\*\*(?!\s)(.+?)(?<!\s)\*\*""")
        val STRIKE = Regex("""~~(?!\s)(.+?)(?<!\s)~~""")
    }
}
