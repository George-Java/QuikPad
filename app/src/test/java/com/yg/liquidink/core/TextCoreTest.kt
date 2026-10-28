package com.yg.liquidink.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class TextCoreTest {

    @Test
    fun utf8RoundTrip() {
        val source = "# 你好，Liquid Ink\n\n流动的玻璃。"
        val decoded = TextCodec.decode(TextCodec.encode(source, TextEncoding.Utf8))
        assertEquals(source, decoded.text)
        assertEquals("UTF-8", decoded.encoding.charsetName)
        assertEquals(BomKind.NONE, decoded.encoding.bom)
    }

    @Test
    fun preservesUtf8Bom() {
        val source = "hello"
        val encoding = TextEncoding("UTF-8", BomKind.UTF8)
        val bytes = TextCodec.encode(source, encoding)
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            bytes.copyOfRange(0, 3),
        )
        val decoded = TextCodec.decode(bytes)
        assertEquals(source, decoded.text)
        assertEquals(BomKind.UTF8, decoded.encoding.bom)
    }

    @Test
    fun preservesUtf16LittleEndianBom() {
        val source = "hello 世界"
        val encoding = TextEncoding("UTF-16LE", BomKind.UTF16_LE)
        val bytes = TextCodec.encode(source, encoding)
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
            bytes.copyOfRange(0, 2),
        )
        val decoded = TextCodec.decode(bytes)
        assertEquals(source, decoded.text)
        assertEquals("UTF-16LE", decoded.encoding.charsetName)
        assertEquals(BomKind.UTF16_LE, decoded.encoding.bom)
    }

    @Test
    fun preservesUtf16BigEndianBom() {
        val source = "hello 世界"
        val encoding = TextEncoding("UTF-16BE", BomKind.UTF16_BE)
        val decoded = TextCodec.decode(TextCodec.encode(source, encoding))
        assertEquals(source, decoded.text)
        assertEquals("UTF-16BE", decoded.encoding.charsetName)
        assertEquals(BomKind.UTF16_BE, decoded.encoding.bom)
    }

    @Test
    fun detectsBomlessUtf16WithAsciiContent() {
        val source = "hello world"
        val bytes = source.toByteArray(Charset.forName("UTF-16LE"))
        val decoded = TextCodec.decode(bytes)
        assertEquals(source, decoded.text)
        assertEquals("UTF-16LE", decoded.encoding.charsetName)
    }

    @Test
    fun detectsGbkFamilyFallback() {
        val source = "中文文件编码"
        val bytes = source.toByteArray(Charset.forName("GB18030"))
        val decoded = TextCodec.decode(bytes)
        assertEquals(source, decoded.text)
        assertEquals("GB18030", decoded.encoding.charsetName)
    }

    @Test
    fun emptyInputDefaultsToUtf8() {
        val decoded = TextCodec.decode(ByteArray(0))
        assertEquals("", decoded.text)
        assertEquals(TextEncoding.Utf8, decoded.encoding)
    }

    @Test
    fun countsChineseCharactersAndEnglishWords() {
        val stats = TextStatistics.compute("你好，世界。\nHello liquid ink.")
        assertEquals(7, stats.words) // 4 Han + 3 English words
        assertTrue(stats.characters > 0)
        assertEquals(2, stats.lines)
        assertTrue(stats.readingMinutes >= 1)
    }

    @Test
    fun countsCharactersWithoutSpaces() {
        val stats = TextStatistics.compute("a b\nc")
        assertEquals(5, stats.characters)
        assertEquals(3, stats.charactersWithoutSpaces)
        assertEquals(3, stats.words)
        assertEquals(2, stats.lines)
    }

    @Test
    fun emptyStatsAreZero() {
        val stats = TextStatistics.compute("")
        assertEquals(0, stats.words)
        assertEquals(0, stats.characters)
        assertEquals(0, stats.lines)
        assertEquals(0, stats.readingMinutes)
    }
}
