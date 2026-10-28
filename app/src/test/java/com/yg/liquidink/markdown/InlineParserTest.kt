package com.yg.liquidink.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineParserTest {

    private val parser = InlineParser()

    @Test
    fun parsesStrongEmphasisAndStrikethrough() {
        val nodes = parser.parse("A **bold** and ~~gone~~.")
        assertEquals("A ", (nodes[0] as MdInline.Text).text)
        assertTrue(nodes[1] is MdInline.Bold)
        assertTrue(nodes[3] is MdInline.Strike)
    }

    @Test
    fun parsesItalicAndInlineCode() {
        val nodes = parser.parse("an *italic* `code`")
        assertTrue(nodes.any { it is MdInline.Italic })
        val code = nodes.filterIsInstance<MdInline.Code>().single()
        assertEquals("code", code.text)
    }

    @Test
    fun parsesLinkWithTitle() {
        val link = parser.parse("[OpenAI](https://openai.com \"site\")")
            .single() as MdInline.Link
        assertEquals("https://openai.com", link.destination)
        assertEquals("site", link.title)
        assertEquals("OpenAI", (link.children.single() as MdInline.Text).text)
    }

    @Test
    fun parsesImage() {
        val image = parser.parse("![A cat](cat.png \"Cat\")")
            .single() as MdInline.InlineImage
        assertEquals("A cat", image.alt)
        assertEquals("cat.png", image.destination)
        assertEquals("Cat", image.title)
    }

    @Test
    fun parsesFootnoteReference() {
        val nodes = parser.parse("Note[^1].")
        val footnote = nodes.filterIsInstance<MdInline.FootnoteRef>().single()
        assertEquals("1", footnote.label)
    }

    @Test
    fun parsesAutolink() {
        val link = parser.parse("<https://example.com>").single() as MdInline.Link
        assertEquals("https://example.com", link.destination)
    }

    @Test
    fun backslashEscapesDelimiters() {
        val text = parser.parse("""\*not italic\* and \`code\`""").joinToString("") {
            if (it is MdInline.Text) it.text else ""
        }
        assertEquals("*not italic* and `code`", text)
    }

    @Test
    fun codeSpanKeepsAsterisksLiteral() {
        val nodes = parser.parse("`**not bold**`")
        val code = nodes.single() as MdInline.Code
        assertEquals("**not bold**", code.text)
    }

    @Test
    fun nestedMarkdownInsideLinkLabel() {
        val link = parser.parse("[**bold** text](https://example.com)")
            .single() as MdInline.Link
        assertTrue(link.children.first() is MdInline.Bold)
    }

    @Test
    fun leavesUnknownSyntaxAsText() {
        val nodes = parser.parse("[not a link]")
        assertEquals("[not a link]", nodes.joinToString("") {
            if (it is MdInline.Text) it.text else ""
        })
    }
}
