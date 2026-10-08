package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkCodeBlockBackground
import com.example.ui.theme.GeminiBlueLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class ContentBlock {
    data class TextBlock(val content: String) : ContentBlock()
    data class CodeBlock(val language: String, val code: String) : ContentBlock()
}

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(content) { parseBlocks(content) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.TextBlock -> {
                    RenderFormattedText(block.content, textColor)
                }
                is ContentBlock.CodeBlock -> {
                    CodeBlockCard(language = block.language, code = block.code)
                }
            }
        }
    }
}

private fun parseBlocks(raw: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val codeFenceRegex = Regex("```(\\w*)\\n?([\\s\\S]*?)```")
    var lastIndex = 0

    val matches = codeFenceRegex.findAll(raw)
    for (match in matches) {
        val range = match.range
        if (range.first > lastIndex) {
            val textBefore = raw.substring(lastIndex, range.first)
            if (textBefore.isNotBlank()) {
                blocks.add(ContentBlock.TextBlock(textBefore.trim()))
            }
        }
        val lang = match.groupValues[1].ifBlank { "code" }
        val code = match.groupValues[2].trimEnd()
        blocks.add(ContentBlock.CodeBlock(lang, code))
        lastIndex = range.last + 1
    }

    if (lastIndex < raw.length) {
        val remaining = raw.substring(lastIndex)
        if (remaining.isNotBlank()) {
            blocks.add(ContentBlock.TextBlock(remaining.trim()))
        }
    }

    if (blocks.isEmpty() && raw.isNotEmpty()) {
        blocks.add(ContentBlock.TextBlock(raw))
    }

    return blocks
}

@Composable
private fun RenderFormattedText(rawText: String, textColor: Color) {
    val lines = rawText.lines()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            when {
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = textColor
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        ),
                        color = textColor
                    )
                }
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# "),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp
                        ),
                        color = textColor
                    )
                }
                line.startsWith("* ") || line.startsWith("- ") -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = GeminiBlueLight,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        val bulletContent = line.substring(2)
                        Text(
                            text = parseInlineMarkdown(bulletContent, textColor),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = textColor
                        )
                    }
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(line, textColor),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = textColor
                    )
                }
            }
        }
    }
}

private fun parseInlineMarkdown(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(`[^`]+`)|(\\*\\*[^\\*]+\\*\\*)|(\\*[^*]+\\*)")
        val matches = regex.findAll(text)

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > cursor) {
                append(text.substring(cursor, start))
            }

            val value = match.value
            when {
                value.startsWith("`") && value.endsWith("`") -> {
                    val code = value.removeSurrounding("`")
                    val codeStart = length
                    append(code)
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x334E89FF),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        codeStart,
                        codeStart + code.length
                    )
                }
                value.startsWith("**") && value.endsWith("**") -> {
                    val bold = value.removeSurrounding("**")
                    val boldStart = length
                    append(bold)
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold),
                        boldStart,
                        boldStart + bold.length
                    )
                }
                value.startsWith("*") && value.endsWith("*") -> {
                    val italic = value.removeSurrounding("*")
                    val itStart = length
                    append(italic)
                    addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic),
                        itStart,
                        itStart + italic.length
                    )
                }
            }
            cursor = end
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

@Composable
fun CodeBlockCard(language: String, code: String) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCodeBlockBackground)
            .testTag("code_block_$language")
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E2128))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.lowercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFA0AEC0)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(0.dp)
                ) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("code", code)
                            clipboard.setPrimaryClip(clip)
                            isCopied = true
                            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("copy_code_button")
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = if (isCopied) "Code copied" else "Copy code",
                            tint = if (isCopied) Color(0xFF34A853) else Color(0xFFA0AEC0),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Code content with horizontal scrolling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = Color(0xFFECEFF1)
                )
            }
        }
    }
}
