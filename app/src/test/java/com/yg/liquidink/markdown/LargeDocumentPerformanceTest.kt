package com.yg.liquidink.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke test for the 1 MB target. The timing budget is intentionally generous so
 * it catches algorithmic regressions, not noisy CI hardware.
 */
class LargeDocumentPerformanceTest {

    @Test
    fun parsesOneMegabyteDocumentWithinBudget() {
        val source = buildString {
            var section = 0
            while (length < 1024 * 1024) {
                append("## Section ").append(section).append("\n\n")
                append("Liquid glass writing sample with **bold text**, `inline code`, ")
                append("and a [link](https://example.com).\n\n")
                append("- [ ] a task item\n- a bullet item\n\n")
                append("```kotlin\n")
                append("fun section").append(section).append("() = \"liquid\"\n")
                append("```\n\n")
                section++
            }
        }
        assertTrue(source.toByteArray(Charsets.UTF_8).size >= 1024 * 1024)

        val parser = MarkdownParser()
        val started = System.nanoTime()
        val parsed = parser.parse(source)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertTrue(parsed.blocks.size > 10_000)
        assertTrue("Parsing $elapsedMs ms", elapsedMs < 8_000)
        // Every generated section contributes heading, paragraph, list and code.
        assertEquals(0, parsed.blocks.size % 4)
    }
}
