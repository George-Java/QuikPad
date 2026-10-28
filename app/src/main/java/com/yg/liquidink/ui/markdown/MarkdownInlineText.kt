package com.yg.liquidink.ui.markdown

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.yg.liquidink.markdown.MdInline
import com.yg.liquidink.ui.theme.CodeFont

const val INLINE_LINK_TAG = "liquid-ink-link"
const val INLINE_FOOTNOTE_TAG = "liquid-ink-footnote"

@Immutable
data class InlineTextColors(
    val text: Color,
    val link: Color,
    val code: Color,
    val codeBackground: Color,
    val footnote: Color,
    val muted: Color,
)

@Composable
fun rememberInlineTextColors(): InlineTextColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        InlineTextColors(
            text = scheme.onSurface,
            link = scheme.primary,
            code = scheme.onSurface,
            codeBackground = scheme.primary.copy(alpha = 0.12f),
            footnote = scheme.tertiary,
            muted = scheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MarkdownInlineText(
    inlines: List<MdInline>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    colors: InlineTextColors = rememberInlineTextColors(),
    onLinkClick: (String) -> Unit = {},
    onFootnoteClick: (String) -> Unit = {},
    maxLines: Int = Int.MAX_VALUE,
) {
    val annotated = remember(inlines, colors) {
        buildInlineAnnotatedString(inlines, colors)
    }
    val links = remember(annotated) {
        annotated.getStringAnnotations(INLINE_LINK_TAG, 0, annotated.length)
            .map { it.item }
            .distinct()
    }
    val footnotes = remember(annotated) {
        annotated.getStringAnnotations(INLINE_FOOTNOTE_TAG, 0, annotated.length)
            .map { it.item }
            .distinct()
    }
    val interactive = links.isNotEmpty() || footnotes.isNotEmpty()
    var layout by remember(annotated) { mutableStateOf<TextLayoutResult?>(null) }

    val semanticsModifier = if (interactive) {
        Modifier.semantics {
            val actions = buildList {
                links.take(4).forEach { destination ->
                    add(
                        CustomAccessibilityAction(label = "打开链接 $destination") {
                            onLinkClick(destination)
                            true
                        },
                    )
                }
                footnotes.take(4).forEach { label ->
                    add(
                        CustomAccessibilityAction(label = "查看脚注 $label") {
                            onFootnoteClick(label)
                            true
                        },
                    )
                }
            }
            if (actions.isNotEmpty()) customActions = actions
        }
    } else {
        Modifier
    }

    val tapModifier = if (interactive) {
        Modifier.pointerInput(annotated, onLinkClick, onFootnoteClick) {
            detectTapGestures { position ->
                if (annotated.isEmpty()) return@detectTapGestures
                val result = layout ?: return@detectTapGestures
                val offset = result.getOffsetForPosition(position)
                    .coerceIn(0, annotated.length)
                val link = annotated
                    .getStringAnnotations(INLINE_LINK_TAG, offset, offset)
                    .firstOrNull()
                if (link != null) {
                    onLinkClick(link.item)
                    return@detectTapGestures
                }
                val footnote = annotated
                    .getStringAnnotations(INLINE_FOOTNOTE_TAG, offset, offset)
                    .firstOrNull()
                if (footnote != null) {
                    onFootnoteClick(footnote.item)
                }
            }
        }
    } else {
        Modifier
    }

    Text(
        text = annotated,
        modifier = modifier
            .then(semanticsModifier)
            .then(tapModifier),
        style = style.copy(color = colors.text),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
    )
}

fun buildInlineAnnotatedString(
    inlines: List<MdInline>,
    colors: InlineTextColors,
): AnnotatedString = buildAnnotatedString {
    appendInlineNodes(inlines, colors)
}

private fun AnnotatedString.Builder.appendInlineNodes(
    nodes: List<MdInline>,
    colors: InlineTextColors,
) {
    nodes.forEach { node ->
        when (node) {
            is MdInline.Text -> append(node.text)
            is MdInline.Code -> {
                pushStyle(
                    SpanStyle(
                        fontFamily = CodeFont,
                        color = colors.code,
                        background = colors.codeBackground,
                    ),
                )
                append(node.text)
                pop()
            }
            is MdInline.Bold -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                appendInlineNodes(node.children, colors)
                pop()
            }
            is MdInline.Italic -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendInlineNodes(node.children, colors)
                pop()
            }
            is MdInline.Strike -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                appendInlineNodes(node.children, colors)
                pop()
            }
            is MdInline.Link -> {
                pushStringAnnotation(INLINE_LINK_TAG, node.destination)
                pushStyle(
                    SpanStyle(
                        color = colors.link,
                        textDecoration = TextDecoration.Underline,
                    ),
                )
                appendInlineNodes(node.children, colors)
                pop()
                pop()
            }
            is MdInline.InlineImage -> {
                pushStyle(
                    SpanStyle(
                        color = colors.muted,
                        fontStyle = FontStyle.Italic,
                    ),
                )
                append("[图片")
                if (node.alt.isNotBlank()) {
                    append("：")
                    append(node.alt)
                }
                append("]")
                pop()
            }
            is MdInline.FootnoteRef -> {
                pushStringAnnotation(INLINE_FOOTNOTE_TAG, node.label)
                pushStyle(
                    SpanStyle(
                        color = colors.footnote,
                        baselineShift = BaselineShift.Superscript,
                        fontSize = 11.sp,
                        textDecoration = TextDecoration.Underline,
                    ),
                )
                append(node.label)
                pop()
                pop()
            }
        }
    }
}
