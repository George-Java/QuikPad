package com.yg.liquidink.ui.theme

import com.yg.liquidink.core.GlassIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassConfigTest {

    private fun config(
        dark: Boolean = false,
        highContrast: Boolean = false,
        reduced: Boolean = false,
        enabled: Boolean = true,
        intensity: GlassIntensity = GlassIntensity.MEDIUM,
        motion: Boolean = true,
    ) = resolveGlassConfig(
        darkTheme = dark,
        highContrast = highContrast,
        reducedEffects = reduced,
        glassEffectsEnabled = enabled,
        glassIntensity = intensity,
        animationsEnabled = motion,
    )

    @Test
    fun disabledGlassIsOpaqueAndFlat() {
        val glass = config(enabled = false, intensity = GlassIntensity.HIGH)
        assertFalse(glass.glassEnabled)
        assertEquals(1f, glass.panelTopAlpha, 0.001f)
        assertEquals(1f, glass.panelBottomAlpha, 0.001f)
        assertFalse(glass.enhancedShadows)
        assertFalse(glass.specularHighlights)
    }

    @Test
    fun lowerIntensityIsMoreOpaque() {
        val low = config(intensity = GlassIntensity.LOW)
        val high = config(intensity = GlassIntensity.HIGH)
        assertTrue(low.panelTopAlpha > high.panelTopAlpha)
        assertTrue(low.panelBottomAlpha > high.panelBottomAlpha)
        assertTrue(low.borderAlpha > high.borderAlpha)
    }

    @Test
    fun highContrastRaisesReadabilityFloor() {
        val normal = config(intensity = GlassIntensity.HIGH, highContrast = false)
        val contrast = config(intensity = GlassIntensity.HIGH, highContrast = true)
        assertTrue(contrast.panelTopAlpha >= normal.panelTopAlpha)
        assertTrue(contrast.panelBottomAlpha >= normal.panelBottomAlpha)
        assertTrue(contrast.borderAlpha >= normal.borderAlpha)
    }

    @Test
    fun reducedEffectsAndDisabledMotionStopAnimations() {
        assertFalse(config(reduced = true).animationsEnabled)
        assertFalse(config(motion = false).animationsEnabled)
        assertTrue(config(reduced = false, motion = true).animationsEnabled)
    }

    @Test
    fun lowIntensityRemovesHeavyEffects() {
        val low = config(intensity = GlassIntensity.LOW, reduced = false)
        assertFalse(low.enhancedShadows)
        assertFalse(low.specularHighlights)
    }
}
