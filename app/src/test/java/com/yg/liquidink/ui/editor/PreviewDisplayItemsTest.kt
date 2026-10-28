package com.yg.liquidink.ui.editor

import com.yg.liquidink.markdown.MarkdownParser
import com.yg.liquidink.markdown.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewDisplayItemsTest {

    @Test
    fun shortListsStayAsOneDisplayItem() {
        val parsed = MarkdownParser().parse(
            (0 until 20).joinToString("\n") { "- item $it" },
        )
        val items = buildPreviewDisplayItems(parsed.blocks)
        assertEquals(1, items.size)
        assertEquals(20, (items.single().block as MdBlock.ListBlock).items.size)
    }

    @Test
    fun longListIsChunkedIntoBoundedLazyItems() {
        val parsed = MarkdownParser().parse(
            (0 until 500).joinToString("\n") { "- item $it" },
        )
        val list = parsed.blocks.single() as MdBlock.ListBlock
        val items = buildPreviewDisplayItems(parsed.blocks)

        assertEquals(500, list.items.size)
        assertEquals((500 + LIST_RENDER_CHUNK_SIZE - 1) / LIST_RENDER_CHUNK_SIZE, items.size)
        assertTrue(items.all { (it.block as MdBlock.ListBlock).items.size <= LIST_RENDER_CHUNK_SIZE })
        assertEquals(1, items.count { !it.isListContinuation })
        assertTrue(items.drop(1).all { it.isListContinuation })
    }

    @Test
    fun blockIndexesRemainStableAcrossChunking() {
        val source = "# Before\n\n" +
            (0 until 120).joinToString("\n") { "- item $it" } +
            "\n\n# After"
        val parsed = MarkdownParser().parse(source)
        val display = buildPreviewDisplayItems(parsed.blocks)
        val mapping = displayIndexByBlock(display)

        val headingIndexes = parsed.blocks.mapIndexedNotNull { index, block ->
            index.takeIf { block is MdBlock.Heading }
        }
        assertEquals(2, headingIndexes.size)
        assertTrue(headingIndexes.all { it in mapping })
        assertEquals(display.size - 1, mapping[headingIndexes.last()])
    }
}
