package com.yg.liquidink.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.yg.liquidink.core.AppSettings
import com.yg.liquidink.core.ThemeMode
import com.yg.liquidink.markdown.OutlineEntry
import com.yg.liquidink.ui.settings.SettingsScreen
import com.yg.liquidink.ui.theme.LiquidInkTheme
import org.junit.Assert.assertEquals
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
class NewUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun outlineEntryClickReportsItsBlockIndex() {
        var clicked: OutlineEntry? = null
        composeRule.setContent {
            LiquidInkTheme(
                themeMode = ThemeMode.LIGHT,
                highContrast = false,
                reducedEffects = true,
                glassEffectsEnabled = false,
                animationsEnabled = false,
            ) {
                Box(Modifier.size(width = 411.dp, height = 891.dp)) {
                    DocumentOutlinePanel(
                        visible = true,
                        entries = listOf(
                            OutlineEntry(blockIndex = 3, level = 1, title = "Alpha"),
                            OutlineEntry(blockIndex = 9, level = 2, title = "Beta"),
                        ),
                        isParsing = false,
                        motionEnabled = false,
                        onSelect = { clicked = it },
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("H1，Alpha，跳转").performClick()
        composeRule.waitForIdle()
        assertEquals(3, clicked?.blockIndex)
    }

    @Test
    fun settingsExposesReaderAppearanceControls() {
        composeRule.setContent {
            LiquidInkTheme(
                themeMode = ThemeMode.DARK,
                highContrast = false,
                reducedEffects = true,
                glassEffectsEnabled = true,
                animationsEnabled = false,
            ) {
                Box(Modifier.size(width = 411.dp, height = 891.dp)) {
                    SettingsScreen(
                        settings = AppSettings(),
                        onThemeModeChange = {},
                        onAutoSaveEnabledChange = {},
                        onAutoSaveIntervalChange = {},
                        onPerformanceModeChange = {},
                        onHighContrastChange = {},
                        onReaderBackgroundChange = {},
                        onGlassEffectsEnabledChange = {},
                        onGlassIntensityChange = {},
                        onAnimationsEnabledChange = {},
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("阅读外观").assertExists()
        composeRule.onNodeWithText("羊皮纸").assertExists()
        composeRule.onNodeWithText("液态玻璃效果").assertExists()
        composeRule.onNodeWithText("界面动效").assertExists()
    }
}
