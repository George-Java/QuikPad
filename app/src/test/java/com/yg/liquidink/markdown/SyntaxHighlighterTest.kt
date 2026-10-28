package com.yg.liquidink.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    @Test
    fun highlightsKotlinKeywordsStringsAndCommentsWithoutMixingThem() {
        val code = "val greeting = \"hello // not comment\" // real comment"
        val tokens = SyntaxHighlighter.highlight(code, "kt")
        val kinds = tokens.map { it.kind }.toSet()
        assertTrue(kinds.contains(CodeTokenKind.KEYWORD))
        assertTrue(kinds.contains(CodeTokenKind.STRING))
        assertTrue(kinds.contains(CodeTokenKind.COMMENT))

        val stringToken = tokens.single { it.kind == CodeTokenKind.STRING }
        assertEquals("\"hello // not comment\"", code.substring(stringToken.start, stringToken.end))
        val commentToken = tokens.single { it.kind == CodeTokenKind.COMMENT }
        assertEquals("// real comment", code.substring(commentToken.start, commentToken.end))
    }

    @Test
    fun highlightsJsonKeysDifferentlyFromValues() {
        val code = """{"name": "liquid", "ready": true, "count": 3}"""
        val tokens = SyntaxHighlighter.highlight(code, "json")
        assertTrue(tokens.any { it.kind == CodeTokenKind.PROPERTY })
        assertTrue(tokens.any { it.kind == CodeTokenKind.STRING })
        assertTrue(tokens.any { it.kind == CodeTokenKind.KEYWORD })
        assertTrue(tokens.any { it.kind == CodeTokenKind.NUMBER })
        val property = tokens.first { it.kind == CodeTokenKind.PROPERTY }
        assertEquals("\"name\"", code.substring(property.start, property.end))
    }

    @Test
    fun recognizesPythonAndMarkup() {
        val python = SyntaxHighlighter.highlight("def f(x):\n    return '# no'  # yes", "python")
        assertTrue(python.any { it.kind == CodeTokenKind.KEYWORD })
        assertTrue(python.any { it.kind == CodeTokenKind.COMMENT })

        val html = SyntaxHighlighter.highlight("<section class=\"glass\">x</section>", "html")
        assertTrue(html.any { it.kind == CodeTokenKind.TAG })
        assertTrue(html.any { it.kind == CodeTokenKind.ATTRIBUTE })
        assertTrue(html.any { it.kind == CodeTokenKind.STRING })
    }

    @Test
    fun skipsLargeBlocks() {
        val code = "val x = 1\n".repeat(10_000)
        assertTrue(SyntaxHighlighter.highlight(code, "kotlin", maxChars = 1_000).isEmpty())
    }

    @Test
    fun normalizesAliases() {
        assertEquals("kotlin", SyntaxHighlighter.normalize("KTS"))
        assertEquals("javascript", SyntaxHighlighter.normalize("js"))
        assertEquals("python", SyntaxHighlighter.normalize("py"))
        assertEquals("xml", SyntaxHighlighter.normalize("svg"))
        assertEquals("generic", SyntaxHighlighter.normalize(""))
    }

    @Test
    fun unknownLanguageStillGetsBasicTokens() {
        val tokens = SyntaxHighlighter.highlight("if x = 3 // hi", "invented")
        assertTrue(tokens.any { it.kind == CodeTokenKind.KEYWORD })
        assertTrue(tokens.any { it.kind == CodeTokenKind.NUMBER })
    }

    @Test
    fun tokensDoNotOverlap() {
        val code = "fun main() { val s = \"x\" /* c */ }"
        val tokens = SyntaxHighlighter.highlight(code, "kotlin")
        tokens.sortedBy { it.start }.zipWithNext().forEach { (a, b) ->
            assertFalse(a.end > b.start)
        }
    }
}
