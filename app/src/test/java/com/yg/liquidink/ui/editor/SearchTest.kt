package com.yg.liquidink.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    @Test
    fun findsLiteralMatchesIgnoringCaseByDefault() {
        val text = "Glass glass GLASS"
        val results = findLiteralMatches(text, "glass", caseSensitive = false)
        assertEquals(3, results.count)
        assertEquals(0 until 5, results.ranges[0])
        assertEquals(6 until 11, results.ranges[1])
        assertEquals(12 until 17, results.ranges[2])
        assertFalse(results.truncated)
    }

    @Test
    fun respectsCaseSensitivity() {
        val results = findLiteralMatches("Glass glass", "Glass", caseSensitive = true)
        assertEquals(1, results.count)
        assertEquals(0 until 5, results.ranges.single())
    }

    @Test
    fun doesNotReturnOverlappingMatches() {
        val results = findLiteralMatches("aaaa", "aa", caseSensitive = true)
        assertEquals(2, results.count)
    }

    @Test
    fun capsMatchesAndReportsTruncation() {
        val results = findLiteralMatches(
            text = "x ".repeat(100),
            query = "x",
            caseSensitive = true,
            limit = 10,
        )
        assertEquals(10, results.count)
        assertTrue(results.truncated)
    }

    @Test
    fun blankQueryReturnsEmpty() {
        assertTrue(findLiteralMatches("text", "", caseSensitive = false).ranges.isEmpty())
    }

    @Test
    fun doesNotSplitUnicodeSurrogatePairs() {
        val text = "a😀b😀c"
        val results = findLiteralMatches(text, "😀", caseSensitive = true)
        assertEquals(2, results.count)
        assertEquals("😀", text.substring(results.ranges[0].first, results.ranges[0].last + 1))
    }
}
