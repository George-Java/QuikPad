package com.yg.liquidink.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    private val parser = MarkdownParser()

    @Test
    fun parsesHeadingsAndParagraphs() {
        val parsed = parser.parse(
            """
            # Hello

            A paragraph with **bold** text.

            ### Third level
            """.trimIndent(),
        )
        assertEquals(3, parsed.blocks.size)
        val heading = parsed.blocks[0] as MdBlock.Heading
        assertEquals(1, heading.level)
        assertEquals("Hello", (heading.inlines.single() as MdInline.Text).text)
        assertTrue(parsed.blocks[1] is MdBlock.Paragraph)
        assertEquals(3, (parsed.blocks[2] as MdBlock.Heading).level)
    }

    @Test
    fun parsesSetextHeading() {
        val parsed = parser.parse("A title\n=======\n\nBody")
        assertEquals(2, parsed.blocks.size)
        val heading = parsed.blocks[0] as MdBlock.Heading
        assertEquals(1, heading.level)
        assertEquals("A title", (heading.inlines.single() as MdInline.Text).text)
    }

    @Test
    fun parsesTaskListAndSourceOffsets() {
        val source = "- [x] Done\n- [ ] Todo"
        val parsed = parser.parse(source)
        val list = parsed.blocks.single() as MdBlock.ListBlock
        assertFalse(list.ordered)
        assertEquals(2, list.items.size)
        assertEquals(true, list.items[0].checked)
        assertEquals(false, list.items[1].checked)

        val first = list.items[0]
        assertNotNull(first.taskMarkerStart)
        assertNotNull(first.taskMarkerEnd)
        assertEquals(
            "[x]",
            source.substring(first.taskMarkerStart!!, first.taskMarkerEnd!!),
        )
        val second = list.items[1]
        assertEquals(
            "[ ]",
            source.substring(second.taskMarkerStart!!, second.taskMarkerEnd!!),
        )
    }

    @Test
    fun parsesOrderedList() {
        val list = parser.parse("3. three\n4. four").blocks.single() as MdBlock.ListBlock
        assertTrue(list.ordered)
        assertEquals(3, list.startNumber)
        assertEquals(listOf(3, 4), list.items.map { it.number })
    }

    @Test
    fun parsesFencedCodeWithoutHighlightingWork() {
        val parsed = parser.parse(
            """
            ```kotlin
            val answer = 42
            println(answer)
            ```
            """.trimIndent(),
        )
        val code = parsed.blocks.single() as MdBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val answer = 42\nprintln(answer)", code.code)
        assertTrue(code.closed)
    }

    @Test
    fun unclosedFenceRunsToEnd() {
        val code = parser.parse("```\nline 1\nline 2").blocks.single() as MdBlock.CodeBlock
        assertFalse(code.closed)
        assertEquals("line 1\nline 2", code.code)
    }

    @Test
    fun parsesTableAndAlignment() {
        val table = parser.parse(
            """
            | Name | Score | Notes |
            | :--- | :---: | ---: |
            | Ada | 99 | ok |
            | Lin | 88 | good |
            """.trimIndent(),
        ).blocks.single() as MdBlock.Table
        assertEquals(3, table.headers.size)
        assertEquals(
            listOf(TableAlignment.LEFT, TableAlignment.CENTER, TableAlignment.RIGHT),
            table.alignments,
        )
        assertEquals(2, table.rows.size)
        assertEquals("Ada", (table.rows[0][0].single() as MdInline.Text).text)
    }

    @Test
    fun tableInInlineCodeIsNotSplit() {
        val table = parser.parse(
            """
            | Expr | Meaning |
            | --- | --- |
            | `a | b` | pipe |
            """.trimIndent(),
        ).blocks.single() as MdBlock.Table
        assertEquals(2, table.headers.size)
        assertEquals(1, table.rows.size)
    }

    @Test
    fun parsesQuote() {
        val quote = parser.parse("> one\n> **two**").blocks.single() as MdBlock.Quote
        assertEquals(2, quote.lines.size)
        assertTrue(quote.lines[1].any { it is MdInline.Bold })
    }

    @Test
    fun parsesFootnoteDefinitionOutsideBlocks() {
        val parsed = parser.parse(
            """
            Text with a note[^a].

            [^a]: The **note** body.
            """.trimIndent(),
        )
        assertEquals(1, parsed.blocks.size)
        assertEquals(1, parsed.footnotes.size)
        assertEquals("a", parsed.footnotes[0].label)
        assertTrue(parsed.footnotes[0].inlines.any { it is MdInline.Bold })
        val paragraph = parsed.blocks[0] as MdBlock.Paragraph
        assertTrue(paragraph.inlines.any { it is MdInline.FootnoteRef })
    }

    @Test
    fun parsesStandaloneImageAndRule() {
        val parsed = parser.parse("![alt](https://example.com/a.png \"title\")\n\n---")
        val image = parsed.blocks[0] as MdBlock.Image
        assertEquals("alt", image.alt)
        assertEquals("https://example.com/a.png", image.destination)
        assertEquals("title", image.title)
        assertTrue(parsed.blocks[1] is MdBlock.ThematicBreak)
    }

    @Test
    fun blockRangesPointAtOriginalSource() {
        val source = "# Title\n\nParagraph\nwith two lines.\n\n> quote"
        val parsed = parser.parse(source)
        parsed.blocks.forEach { block ->
            assertTrue(block.start in 0..source.length)
            assertTrue(block.end in block.start..source.length)
        }
        val paragraph = parsed.blocks[1]
        assertEquals("Paragraph\nwith two lines.", source.substring(paragraph.start, paragraph.end))
    }

    @Test
    fun handlesWindowsLineEndings() {
        val parsed = parser.parse("# Title\r\n\r\nParagraph\r\nline two")
        assertEquals(2, parsed.blocks.size)
        val paragraph = parsed.blocks[1] as MdBlock.Paragraph
        assertTrue(paragraph.inlines.joinToString("") {
            if (it is MdInline.Text) it.text else ""
        }.contains("line two"))
    }

    @Test
    fun parsesCrLfTaskOffsets() {
        val source = "- [ ] win\r\n- [x] second"
        val items = (parser.parse(source).blocks.single() as MdBlock.ListBlock).items
        assertEquals("[ ]", source.substring(items[0].taskMarkerStart!!, items[0].taskMarkerEnd!!))
        assertEquals("[x]", source.substring(items[1].taskMarkerStart!!, items[1].taskMarkerEnd!!))
    }

    @Test
    fun parsesLargeDocumentIntoSeparateBlocks() {
        val source = buildString {
            repeat(2_000) { index ->
                append("## Section ").append(index).append("\n\n")
                append("Paragraph ").append(index).append(" with **bold** and `code`.\n\n")
            }
        }
        val parsed = parser.parse(source)
        assertEquals(4_000, parsed.blocks.size)
        assertTrue(parsed.blocks.first() is MdBlock.Heading)
        assertTrue(parsed.blocks.last() is MdBlock.Paragraph)
    }
}
