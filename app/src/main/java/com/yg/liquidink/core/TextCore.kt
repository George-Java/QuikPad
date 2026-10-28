package com.yg.liquidink.core

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class TextStats(
    val words: Int,
    val characters: Int,
    val charactersWithoutSpaces: Int,
    val lines: Int,
    val readingMinutes: Int,
)

object TextStatistics {

    fun compute(text: String): TextStats {
        if (text.isEmpty()) {
            return TextStats(
                words = 0,
                characters = 0,
                charactersWithoutSpaces = 0,
                lines = 0,
                readingMinutes = 0,
            )
        }

        var words = 0
        var cjkCharacters = 0
        var charactersWithoutSpaces = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            if (!Character.isWhitespace(codePoint)) charactersWithoutSpaces++

            when {
                isIdeographic(codePoint) -> {
                    words++
                    cjkCharacters++
                }
                Character.isLetterOrDigit(codePoint) -> {
                    words++
                    index += charCount
                    while (index < text.length) {
                        val next = text.codePointAt(index)
                        if (isIdeographic(next) || !Character.isLetterOrDigit(next)) break
                        index += Character.charCount(next)
                    }
                    continue
                }
            }
            index += charCount
        }

        val characters = Character.codePointCount(text, 0, text.length)
        val lines = text.count { it == '\n' } + 1
        val readingMinutes = if (words == 0) {
            0
        } else {
            val latinWords = (words - cjkCharacters).coerceAtLeast(0)
            val minutes = cjkCharacters / 400.0 + latinWords / 220.0
            kotlin.math.ceil(minutes).toInt().coerceAtLeast(1)
        }

        return TextStats(
            words = words,
            characters = characters,
            charactersWithoutSpaces = charactersWithoutSpaces,
            lines = lines,
            readingMinutes = readingMinutes,
        )
    }

    private fun isIdeographic(codePoint: Int): Boolean =
        when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL,
            -> true
            else -> false
        }
}

enum class BomKind(val bytes: ByteArray, val charsetName: String?) {
    NONE(ByteArray(0), null),
    UTF8(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), "UTF-8"),
    UTF16_LE(byteArrayOf(0xFF.toByte(), 0xFE.toByte()), "UTF-16LE"),
    UTF16_BE(byteArrayOf(0xFE.toByte(), 0xFF.toByte()), "UTF-16BE"),
}

data class TextEncoding(
    val charsetName: String = "UTF-8",
    val bom: BomKind = BomKind.NONE,
) {
    val charset: Charset get() = Charset.forName(charsetName)

    val label: String
        get() = when {
            bom == BomKind.UTF8 -> "UTF-8 BOM"
            bom == BomKind.UTF16_LE -> "UTF-16 LE BOM"
            bom == BomKind.UTF16_BE -> "UTF-16 BE BOM"
            charsetName.equals("GB18030", ignoreCase = true) -> "GB18030"
            charsetName.equals("UTF-16LE", ignoreCase = true) -> "UTF-16 LE"
            charsetName.equals("UTF-16BE", ignoreCase = true) -> "UTF-16 BE"
            else -> charsetName
        }

    companion object {
        val Utf8 = TextEncoding("UTF-8", BomKind.NONE)
    }
}

data class DecodedText(
    val text: String,
    val encoding: TextEncoding,
)

/**
 * Decodes Markdown while preserving its byte-level encoding so a save does not
 * silently destroy a legacy file. New documents default to UTF-8 without BOM.
 */
object TextCodec {

    fun decode(bytes: ByteArray): DecodedText {
        if (bytes.isEmpty()) return DecodedText("", TextEncoding.Utf8)

        detectBom(bytes)?.let { (bom, offset) ->
            val charset = Charset.forName(requireNotNull(bom.charsetName))
            val text = decodeLosslessly(bytes, offset, bytes.size - offset, charset)
                ?: String(bytes, offset, bytes.size - offset, charset)
            return DecodedText(text, TextEncoding(charset.name(), bom))
        }

        detectBomlessUtf16(bytes)?.let { charset ->
            return DecodedText(String(bytes, charset), TextEncoding(charset.name(), BomKind.NONE))
        }

        val utf8 = decodeLosslessly(bytes, 0, bytes.size, Charsets.UTF_8)
        if (utf8 != null) return DecodedText(utf8, TextEncoding.Utf8)

        val gb18030 = runCatching {
            val charset = Charset.forName("GB18030")
            decodeLosslessly(bytes, 0, bytes.size, charset)?.let {
                DecodedText(it, TextEncoding("GB18030", BomKind.NONE))
            }
        }.getOrNull()
        if (gb18030 != null) return gb18030

        val latin1 = Charset.forName("ISO-8859-1")
        return DecodedText(String(bytes, latin1), TextEncoding("ISO-8859-1", BomKind.NONE))
    }

    fun encode(text: String, encoding: TextEncoding): ByteArray {
        val body = text.toByteArray(encoding.charset)
        if (encoding.bom == BomKind.NONE) return body
        return encoding.bom.bytes + body
    }

    private fun detectBom(bytes: ByteArray): Pair<BomKind, Int>? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return BomKind.UTF8 to 3
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            // UTF-32LE starts with FF FE 00 00. We do not advertise UTF-32
            // support, so treat the leading pair as UTF-16LE only when the
            // following bytes do not look like a UTF-32 null pair.
            if (bytes.size < 4 || bytes[2] != 0.toByte() || bytes[3] != 0.toByte()) {
                return BomKind.UTF16_LE to 2
            }
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return BomKind.UTF16_BE to 2
        }
        return null
    }

    private fun detectBomlessUtf16(bytes: ByteArray): Charset? {
        if (bytes.size < 4 || bytes.size % 2 != 0) return null
        val sample = minOf(bytes.size, 4096)
        var evenZeros = 0
        var oddZeros = 0
        var pairs = 0
        var i = 0
        while (i + 1 < sample) {
            if (bytes[i] == 0.toByte()) evenZeros++
            if (bytes[i + 1] == 0.toByte()) oddZeros++
            pairs++
            i += 2
        }
        if (pairs == 0) return null
        val evenRatio = evenZeros.toDouble() / pairs
        val oddRatio = oddZeros.toDouble() / pairs
        return when {
            oddRatio > 0.35 && evenRatio < 0.1 -> Charsets.UTF_16LE
            evenRatio > 0.35 && oddRatio < 0.1 -> Charsets.UTF_16BE
            else -> null
        }
    }

    private fun decodeLosslessly(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        charset: Charset,
    ): String? {
        if (length == 0) return ""
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, length))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
