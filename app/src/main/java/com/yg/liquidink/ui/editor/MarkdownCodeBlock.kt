package com.yg.liquidink.ui.editor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.liquidink.markdown.CodeTokenKind
import com.yg.liquidink.markdown.SyntaxHighlighter
import com.yg.liquidink.ui.theme.CodeFont
import com.yg.liquidink.ui.theme.LocalSyntaxPalette
import com.yg.liquidink.ui.theme.SyntaxPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MarkdownCodeBlock(
    code: String,
    language: String,
    closed: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSyntaxPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var annotated by remember(code, language, palette) {
        mutableStateOf(AnnotatedString(code))
    }
    LaunchedEffect(code, language, palette) {
        annotated = withContext(Dispatchers.Default) {
            highlightCode(code, language, palette)
        }
    }
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.background)
            .border(
                width = 1.dp,
                color = palette.plain.copy(alpha = 0.12f),
                shape = shape,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.035f))
                .padding(start = 14.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = language.takeIf { it.isNotBlank() } ?: "纯文本",
                    color = palette.plain.copy(alpha = 0.66f),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = CodeFont,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                if (!closed) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "未闭合",
                        color = palette.attribute,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) {
                        clipboard.setText(AnnotatedString(code))
                        Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "复制代码",
                    tint = palette.plain.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        SelectionContainer {
            Text(
                text = annotated,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                color = palette.plain,
                fontFamily = CodeFont,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.15.sp,
                softWrap = false,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = CodeFont,
                    color = palette.plain,
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                ),
            )
        }
    }
}

private fun highlightCode(
    code: String,
    language: String,
    palette: SyntaxPalette,
): AnnotatedString {
    val tokens = SyntaxHighlighter.highlight(code, language)
    return buildAnnotatedString {
        append(code)
        tokens.forEach { token ->
            addStyle(
                SpanStyle(color = tokenColor(token.kind, palette)),
                token.start,
                token.end,
            )
        }
    }
}

private fun tokenColor(kind: CodeTokenKind, palette: SyntaxPalette): Color = when (kind) {
    CodeTokenKind.KEYWORD -> palette.keyword
    CodeTokenKind.STRING -> palette.string
    CodeTokenKind.COMMENT -> palette.comment
    CodeTokenKind.NUMBER -> palette.number
    CodeTokenKind.FUNCTION -> palette.function
    CodeTokenKind.TAG -> palette.tag
    CodeTokenKind.ATTRIBUTE -> palette.attribute
    CodeTokenKind.PROPERTY -> palette.property
    CodeTokenKind.VARIABLE -> palette.variable
}
