package com.yg.liquidink.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentOutlineTest {

    @Test
    fun includesH1ToH3AndSkipsDeeperLevels() {
        val parsed = MarkdownParser().parse(
            """
            # One

            ## Two

            ### Three

            #### Four
            """.trimIndent(),
        )
        val outline = buildOutline(parsed.blocks)
        assertEquals(3, outline.size)
        assertEquals(listOf(1, 2, 3), outline.map { it.level })
        assertEquals(listOf("One", "Two", "Three"), outline.map { it.title })
    }

    @Test
    fun entriesPointAtTheirRealBlockIndexes() {
        val parsed = MarkdownParser().parse(
            """
            Intro

            # First

            Body

            ## Second
            """.trimIndent(),
        )
        val outline = buildOutline(parsed.blocks)
        assertEquals(2, outline.size)
        val firstHeading = parsed.blocks[outline[0].blockIndex] as MdBlock.Heading
        assertEquals("First", (firstHeading.inlines.single() as MdInline.Text).text)
        assertEquals("Second", outline[1].title)
        assertTrue(parsed.blocks[outline[1].blockIndex] is MdBlock.Heading)
    }

    @Test
    fun flattensInlineMarkupIntoReadableTitles() {
        val parsed = MarkdownParser().parse(
            "## A **bold** title with `code` and [link](https://example.com)",
        )
        val outline = buildOutline(parsed.blocks)
        assertEquals(
            "A bold title with code and link",
            outline.single().title,
        )
    }

    @Test
    fun blankHeadingGetsFallbackTitle() {
        val parsed = MarkdownParser().parse("#   ")
        assertEquals("未命名标题", buildOutline(parsed.blocks).single().title)
    }
}
