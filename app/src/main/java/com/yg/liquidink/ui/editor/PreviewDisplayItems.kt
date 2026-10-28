package com.yg.liquidink.ui.editor

import com.yg.liquidink.markdown.MdBlock

internal const val LIST_RENDER_CHUNK_SIZE = 48

/**
 * A LazyColumn item. Ordinary Markdown blocks map 1:1. A very long list is split
 * into bounded chunks so entering the list never composes hundreds of rows in a
 * single lazy item (the main source of jank/ANR on long lists).
 */
internal data class PreviewDisplayItem(
    val blockIndex: Int,
    val block: MdBlock,
    val isListContinuation: Boolean = false,
)

internal fun buildPreviewDisplayItems(blocks: List<MdBlock>): List<PreviewDisplayItem> {
    val result = ArrayList<PreviewDisplayItem>(blocks.size)
    blocks.forEachIndexed { blockIndex, block ->
        if (block is MdBlock.ListBlock && block.items.size > LIST_RENDER_CHUNK_SIZE) {
            block.items.chunked(LIST_RENDER_CHUNK_SIZE).forEachIndexed { chunkIndex, items ->
                result += PreviewDisplayItem(
                    blockIndex = blockIndex,
                    block = block.copy(items = items),
                    isListContinuation = chunkIndex > 0,
                )
            }
        } else {
            result += PreviewDisplayItem(blockIndex = blockIndex, block = block)
        }
    }
    return result
}

internal fun displayIndexByBlock(items: List<PreviewDisplayItem>): Map<Int, Int> =
    buildMap {
        items.forEachIndexed { displayIndex, item ->
            putIfAbsent(item.blockIndex, displayIndex)
        }
    }
