package com.yg.liquidink.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.liquidink.ui.markdown.MarkdownVisualTransformation
import com.yg.liquidink.ui.markdown.rememberEditorSyntaxColors

@Composable
fun SourceEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    searchMatches: List<IntRange> = emptyList(),
    activeMatch: IntRange? = null,
    largeDocument: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = rememberEditorSyntaxColors()
    val transformation = remember(colors, searchMatches, activeMatch) {
        MarkdownVisualTransformation(
            colors = colors,
            searchMatches = searchMatches,
            activeMatch = activeMatch,
        )
    }
    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    val textStyle = TextStyle(
        color = scheme.onSurface,
        fontSize = 16.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.1.sp,
    )

    Box(modifier.fillMaxSize()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .then(focusModifier)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .semantics {
                    contentDescription = if (largeDocument) {
                        "Markdown 源码编辑区，大文件模式"
                    } else {
                        "Markdown 源码编辑区"
                    }
                },
            textStyle = textStyle,
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = KeyboardType.Text,
            ),
            visualTransformation = transformation,
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize()) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = "从这里开始写下第一行…",
                            style = textStyle.copy(
                                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
