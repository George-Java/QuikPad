package com.yg.liquidink.markdown

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditingTest {

    @Test
    fun wrapsSelectedTextWithBoldMarkers() {
        val value = TextFieldValue("hello", TextRange(0, 5))
        val result = MarkdownEditing.apply(value, MarkdownFormat.BOLD)
        assertEquals("**hello**", result.text)
        assertEquals(TextRange(2, 7), result.selection)
    }

    @Test
    fun boldWithCollapsedSelectionTogglesMarkers() {
        val value = TextFieldValue("****", TextRange(2))
        val result = MarkdownEditing.apply(value, MarkdownFormat.BOLD)
        assertEquals("", result.text)
        assertEquals(TextRange(0), result.selection)
    }

    @Test
    fun headingToggleAddsAndRemovesPrefix() {
        val added = MarkdownEditing.apply(
            TextFieldValue("Title", TextRange(0)),
            MarkdownFormat.HEADING_2,
        )
        assertEquals("## Title", added.text)
        val removed = MarkdownEditing.apply(added, MarkdownFormat.HEADING_2)
        assertEquals("Title", removed.text)
    }

    @Test
    fun headingReplacesExistingLevel() {
        val value = TextFieldValue("# Title", TextRange(0))
        val result = MarkdownEditing.apply(value, MarkdownFormat.HEADING_3)
        assertEquals("### Title", result.text)
    }

    @Test
    fun bulletListTogglesMultipleLines() {
        val value = TextFieldValue("a\nb", TextRange(0, 3))
        val added = MarkdownEditing.apply(value, MarkdownFormat.BULLET_LIST)
        assertEquals("- a\n- b", added.text)
        val removed = MarkdownEditing.apply(added, MarkdownFormat.BULLET_LIST)
        assertEquals("a\nb", removed.text)
    }

    @Test
    fun taskListUsesTaskPrefix() {
        val result = MarkdownEditing.apply(
            TextFieldValue("todo", TextRange(0)),
            MarkdownFormat.TASK_LIST,
        )
        assertEquals("- [ ] todo", result.text)
    }

    @Test
    fun autoContinuesBulletList() {
        val old = TextFieldValue("- item", TextRange(6))
        val new = TextFieldValue("- item\n", TextRange(7))
        val result = MarkdownEditing.applyAutoFormatting(old, new)
        assertEquals("- item\n- ", result.text)
        assertEquals(TextRange(9), result.selection)
    }

    @Test
    fun autoContinuesOrderedListWithIncrement() {
        val old = TextFieldValue("9. item", TextRange(7))
        val new = TextFieldValue("9. item\n", TextRange(8))
        val result = MarkdownEditing.applyAutoFormatting(old, new)
        assertEquals("9. item\n10. ", result.text)
    }

    @Test
    fun autoContinuesTaskList() {
        val old = TextFieldValue("- [x] done", TextRange(10))
        val new = TextFieldValue("- [x] done\n", TextRange(11))
        val result = MarkdownEditing.applyAutoFormatting(old, new)
        assertEquals("- [x] done\n- [ ] ", result.text)
    }

    @Test
    fun autoContinuesQuote() {
        val old = TextFieldValue("> quote", TextRange(7))
        val new = TextFieldValue("> quote\n", TextRange(8))
        val result = MarkdownEditing.applyAutoFormatting(old, new)
        assertEquals("> quote\n> ", result.text)
    }

    @Test
    fun emptyListItemEndsList() {
        val old = TextFieldValue("- ", TextRange(2))
        val new = TextFieldValue("- \n", TextRange(3))
        val result = MarkdownEditing.applyAutoFormatting(old, new)
        assertEquals("\n", result.text)
        assertEquals(TextRange(1), result.selection)
    }

    @Test
    fun openingFenceAutoInsertsClosingFence() {
        val old = TextFieldValue("```", TextRange(3))
        val new = TextFieldValue("```\n", TextRange(4))
        val result = MarkdownEditing.applyAutoFormatting(old, new)
        assertEquals("```\n\n```", result.text)
        assertEquals(TextRange(4), result.selection)
    }

    @Test
    fun codeFenceContentDoesNotContinueLists() {
        val source = "```\n- item"
        val old = TextFieldValue(source, TextRange(source.length))
        val new = TextFieldValue("$source\n", TextRange(source.length + 1))
        val result = MarkdownEditing.applyAutoFormatting(old, new)
        assertEquals("$source\n", result.text)
    }

    @Test
    fun insertingCodeBlockPlacesCaretInsideFence() {
        val result = MarkdownEditing.apply(
            TextFieldValue("", TextRange(0)),
            MarkdownFormat.CODE_BLOCK,
        )
        assertTrue(result.text.contains("```"))
        assertEquals(result.text.indexOf("```") + 4, result.selection.start)
    }

    @Test
    fun insertsTableAndSelectsPlaceholder() {
        val result = MarkdownEditing.apply(
            TextFieldValue("", TextRange(0)),
            MarkdownFormat.TABLE,
        )
        assertTrue(result.text.contains("| 列 1 | 列 2 |"))
        assertEquals("列 1", result.text.substring(result.selection.min, result.selection.max))
    }

    @Test
    fun insertsLinkAndSelectsUrlForExistingText() {
        val result = MarkdownEditing.apply(
            TextFieldValue("OpenAI", TextRange(0, 6)),
            MarkdownFormat.LINK,
        )
        assertEquals("[OpenAI](https://)", result.text)
        assertEquals("https://", result.text.substring(result.selection.min, result.selection.max))
    }

    @Test
    fun pasteIsLeftUntouchedByAutoFormatting() {
        val old = TextFieldValue("", TextRange(0))
        val new = TextFieldValue("- paste\n", TextRange(8))
        val result = MarkdownEditing.applyAutoFormatting(old, new)
        assertEquals(new, result)
    }
}
