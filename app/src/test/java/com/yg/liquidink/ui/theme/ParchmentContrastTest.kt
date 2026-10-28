package com.yg.liquidink.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ParchmentContrastTest {

    @Test
    fun bodyTextPassesWcagAaaOnParchment() {
        val ratio = contrastRatio(ParchmentReaderColors.onSurface, ParchmentPaperTop)
        assertTrue("Contrast was $ratio", ratio >= 7.0)
    }

    @Test
    fun secondaryTextPassesWcagAaOnParchment() {
        val ratio = contrastRatio(ParchmentReaderColors.onSurfaceVariant, ParchmentPaperTop)
        assertTrue("Contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun linkColorHasReadableContrast() {
        val ratio = contrastRatio(ParchmentReaderColors.primary, ParchmentPaperBottom)
        assertTrue("Contrast was $ratio", ratio >= 4.5)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val l1 = relativeLuminance(a)
        val l2 = relativeLuminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
