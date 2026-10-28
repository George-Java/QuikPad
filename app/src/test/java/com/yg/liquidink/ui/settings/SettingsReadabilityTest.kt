package com.yg.liquidink.ui.settings

import com.yg.liquidink.ui.theme.LiquidGlassConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsReadabilityTest {

    private val lowContrastGlass = LiquidGlassConfig(
        animationsEnabled = true,
        enhancedShadows = false,
        specularHighlights = true,
        panelTopAlpha = 0.48f,
        panelBottomAlpha = 0.22f,
        borderAlpha = 0.18f,
        highContrast = false,
    )

    @Test
    fun settingsPanelGetsReadableMinimumOpacity() {
        val readable = readableSettingsGlass(lowContrastGlass)

        assertTrue(readable.panelTopAlpha >= SETTINGS_PANEL_TOP_ALPHA)
        assertTrue(readable.panelBottomAlpha >= SETTINGS_PANEL_BOTTOM_ALPHA)
        assertTrue(readable.borderAlpha >= SETTINGS_PANEL_BORDER_ALPHA)
    }

    @Test
    fun alreadyStrongPanelIsNotWeakened() {
        val strong = lowContrastGlass.copy(
            panelTopAlpha = 1f,
            panelBottomAlpha = 0.99f,
            borderAlpha = 0.95f,
        )
        val readable = readableSettingsGlass(strong)

        assertEquals(1f, readable.panelTopAlpha, 0.0001f)
        assertEquals(0.99f, readable.panelBottomAlpha, 0.0001f)
        assertEquals(0.95f, readable.borderAlpha, 0.0001f)
    }
}
