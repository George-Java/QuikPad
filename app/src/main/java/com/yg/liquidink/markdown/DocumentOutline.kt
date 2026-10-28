package com.yg.liquidink.markdown

data class OutlineEntry(
    val blockIndex: Int,
    val level: Int,
    val title: String,
)

/**
 * Builds the reading table of contents from the parsed block list. Keeping the
 * block index (rather than a pixel offset) is what makes LazyColumn navigation
 * stable and accurate, even while the document is being reparsed.
 */
fun buildOutline(blocks: List<MdBlock>): List<OutlineEntry> {
    val result = ArrayList<OutlineEntry>()
    blocks.forEachIndexed { index, block ->
        if (block is MdBlock.Heading && block.level in 1..3) {
            result += OutlineEntry(
                blockIndex = index,
                level = block.level,
                title = inlinePlainText(block.inlines).ifBlank { "未命名标题" },
            )
        }
    }
    return result
}

fun inlinePlainText(nodes: List<MdInline>): String {
    val text = buildString {
        fun appendNodes(items: List<MdInline>) {
            items.forEach { node ->
                when (node) {
                    is MdInline.Text -> append(node.text)
                    is MdInline.Code -> append(node.text)
                    is MdInline.Bold -> appendNodes(node.children)
                    is MdInline.Italic -> appendNodes(node.children)
                    is MdInline.Strike -> appendNodes(node.children)
                    is MdInline.Link -> appendNodes(node.children)
                    is MdInline.InlineImage -> append(node.alt)
                    is MdInline.FootnoteRef -> append("[^").append(node.label).append(']')
                }
            }
        }
        appendNodes(nodes)
    }
    return text.trim().replace(WHITESPACE, " ")
}

private val WHITESPACE = Regex("""\s+""")
