package com.yg.liquidink.markdown

/**
 * Immutable inline node tree. The parser produces this model on a background
 * dispatcher; the Compose renderer only ever reads it.
 */
sealed interface MdInline {
    data class Text(val text: String) : MdInline
    data class Code(val text: String) : MdInline
    data class Bold(val children: List<MdInline>) : MdInline
    data class Italic(val children: List<MdInline>) : MdInline
    data class Strike(val children: List<MdInline>) : MdInline
    data class Link(
        val children: List<MdInline>,
        val destination: String,
        val title: String? = null,
    ) : MdInline
    data class InlineImage(
        val alt: String,
        val destination: String,
        val title: String? = null,
    ) : MdInline
    data class FootnoteRef(val label: String) : MdInline
}

enum class TableAlignment { LEFT, CENTER, RIGHT }

/** A list item keeps absolute source offsets so a checkbox can edit the source. */
data class MdListItem(
    val inlines: List<MdInline>,
    val checked: Boolean?,
    val level: Int,
    val number: Int?,
    val taskMarkerStart: Int? = null,
    val taskMarkerEnd: Int? = null,
    val sourceStart: Int = 0,
    val sourceEnd: Int = 0,
)

/**
 * Block source offsets are half-open: [start, end). They point into the original
 * Markdown source and make preview -> source navigation and task toggling exact.
 */
sealed interface MdBlock {
    val start: Int
    val end: Int

    data class Heading(
        val level: Int,
        val inlines: List<MdInline>,
        override val start: Int,
        override val end: Int,
    ) : MdBlock

    data class Paragraph(
        val inlines: List<MdInline>,
        override val start: Int,
        override val end: Int,
    ) : MdBlock

    data class Quote(
        val lines: List<List<MdInline>>,
        override val start: Int,
        override val end: Int,
    ) : MdBlock

    data class ListBlock(
        val ordered: Boolean,
        val startNumber: Int,
        val items: List<MdListItem>,
        override val start: Int,
        override val end: Int,
    ) : MdBlock

    data class CodeBlock(
        val language: String,
        val code: String,
        val closed: Boolean,
        override val start: Int,
        override val end: Int,
    ) : MdBlock

    data class Table(
        val headers: List<List<MdInline>>,
        val alignments: List<TableAlignment>,
        val rows: List<List<List<MdInline>>>,
        override val start: Int,
        override val end: Int,
    ) : MdBlock

    data class Image(
        val alt: String,
        val destination: String,
        val title: String?,
        override val start: Int,
        override val end: Int,
    ) : MdBlock

    data class ThematicBreak(
        override val start: Int,
        override val end: Int,
    ) : MdBlock
}

data class MdFootnoteDefinition(
    val label: String,
    val inlines: List<MdInline>,
    val start: Int,
    val end: Int,
)

data class ParsedMarkdown(
    val blocks: List<MdBlock>,
    val footnotes: List<MdFootnoteDefinition>,
) {
    companion object {
        val Empty = ParsedMarkdown(emptyList(), emptyList())
    }
}
