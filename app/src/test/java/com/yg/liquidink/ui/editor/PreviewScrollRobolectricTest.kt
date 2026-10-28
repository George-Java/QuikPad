package com.yg.liquidink.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.yg.liquidink.core.ThemeMode
import com.yg.liquidink.markdown.MarkdownParser
import com.yg.liquidink.markdown.MdBlock
import com.yg.liquidink.markdown.inlinePlainText
import com.yg.liquidink.ui.theme.LiquidInkTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * Layout-level smoke test for the reported preview-scroll crash.
 *
 * It includes block quotes, tables, task lists and code blocks because those are
 * the measurement-heavy blocks. The document spans >500 source lines and uses a
 * reduced-effects / no-glass configuration so the test is deterministic and does
 * not depend on animation clocks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PreviewScrollRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scrollsLongDocumentWithTablesAndQuotesWithoutCrashing() {
        val source = buildLongDocument()
        require(source.lineSequence().count() >= 500)
        val parsed = MarkdownParser().parse(source)
        val targetIndex = parsed.blocks.indexOfFirst { block ->
            block is MdBlock.Heading && inlinePlainText(block.inlines) == "TARGET-HEADING"
        }
        require(targetIndex >= 0)
        var scrollConsumed = false

        composeRule.setContent {
            LiquidInkTheme(
                themeMode = ThemeMode.LIGHT,
                highContrast = false,
                reducedEffects = true,
                glassEffectsEnabled = false,
                animationsEnabled = false,
            ) {
                Box(Modifier.size(width = 411.dp, height = 891.dp)) {
                    MarkdownPreview(
                        parsed = parsed,
                        baseUri = null,
                        isParsing = false,
                        onEditBlock = {},
                        onToggleTask = { _, _ -> },
                        onLinkClick = {},
                        onStartWriting = {},
                        contentPadding = PaddingValues(20.dp),
                        modifier = Modifier.fillMaxSize(),
                        scrollRequest = OutlineScrollRequest(targetIndex, nonce = 1L),
                        onScrollRequestConsumed = { scrollConsumed = true },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("markdown-preview").assertExists()
        composeRule.onNodeWithText("TARGET-HEADING").assertIsDisplayed()
        assertTrue(scrollConsumed)

        val step = maxOf(1, parsed.blocks.lastIndex / 8)
        var index = 0
        while (index < parsed.blocks.lastIndex) {
            composeRule.onNodeWithTag("markdown-preview").performScrollToIndex(index)
            composeRule.waitForIdle()
            index += step
        }
        composeRule.onNodeWithTag("markdown-preview")
            .performScrollToIndex(parsed.blocks.lastIndex)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("The final paragraph is reachable after a long scroll.")
            .assertIsDisplayed()
    }

    private fun buildLongDocument(): String = buildString {
        append("# START\n\n")
        repeat(80) { section ->
            append("## Section ").append(section).append("\n\n")
            append("Paragraph ").append(section)
                .append(" with **bold**, `inline code`, and a [link](https://example.com).\n\n")
            append("> Quote ").append(section)
                .append(" with a second line.\n\n")
            append("- [ ] task ").append(section).append("\n")
            append("- plain item ").append(section).append("\n\n")
            append("```kotlin\n")
            append("fun section").append(section).append("(): Int = ").append(section).append('\n')
            append("```\n\n")
            if (section == 40) {
                append("### TARGET-HEADING\n\n")
            }
            if (section % 8 == 0) {
                append("| Key | Value |\n| --- | --- |\n")
                append("| section | ").append(section).append(" |\n\n")
            }
        }
        append("## END-OF-LONG-DOCUMENT\n\n")
        append("The final paragraph is reachable after a long scroll.\n")
    }
}
