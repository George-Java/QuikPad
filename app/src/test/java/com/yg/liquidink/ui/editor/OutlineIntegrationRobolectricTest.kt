package com.yg.liquidink.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import com.yg.liquidink.core.ReaderBackground
import com.yg.liquidink.core.TextStatistics
import com.yg.liquidink.core.ThemeMode
import com.yg.liquidink.markdown.MarkdownParser
import com.yg.liquidink.ui.DocumentInfo
import com.yg.liquidink.ui.EditorUiState
import com.yg.liquidink.ui.theme.LiquidInkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OutlineIntegrationRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingOutlineEntryScrollsToTheHeading() {
        val source = buildString {
            append("# Start\n\n")
            repeat(120) { index ->
                append("Paragraph ").append(index).append(" with some text.\n\n")
            }
            append("## Middle\n\n")
            repeat(30) { index ->
                append("More text ").append(index).append(".\n\n")
            }
            append("### Jump Target\n\n")
            append("The skip target is now visible.\n")
        }
        val parsed = MarkdownParser().parse(source)
        val state = EditorUiState(
            document = DocumentInfo(
                uri = "content://test/document.md",
                displayName = "outline-test.md",
            ),
            value = TextFieldValue(source),
            parsed = parsed,
            stats = TextStatistics.compute(source),
            isLoading = false,
            isParsing = false,
        )

        composeRule.setContent {
            LiquidInkTheme(
                themeMode = ThemeMode.LIGHT,
                highContrast = false,
                reducedEffects = false,
                glassEffectsEnabled = false,
                animationsEnabled = false,
            ) {
                Box(Modifier.size(width = 411.dp, height = 891.dp)) {
                    EditorScreen(
                        state = state,
                        readerBackground = ReaderBackground.DEFAULT,
                        onValueChange = {},
                        onUndo = {},
                        onRedo = {},
                        onFormat = {},
                        onSave = {},
                        onSaveAs = {},
                        onRename = {},
                        onDelete = {},
                        onOpenSettings = {},
                        onToggleTask = { _, _ -> },
                        onSetSelection = { _, _ -> },
                        onReplaceRange = { _, _ -> },
                        onReplaceAll = { _, _, _ -> },
                        onSaveAndClose = {},
                        onDiscardAndClose = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("目录大纲").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("H3，Jump Target，跳转").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Jump Target").assertIsDisplayed()
    }
}
