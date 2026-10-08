package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.ui.theme.DarkAiBubble
import com.example.ui.theme.DarkUserBubble
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.GeminiPurple
import com.example.ui.theme.LightAiBubble
import com.example.ui.theme.LightUserBubble
import com.example.ui.theme.StatusError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageItem(
    message: ChatMessage,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(initialOffsetY = { 30 })
    ) {
        if (message.isUser) {
            UserMessageBubble(
                message = message,
                onRetry = onRetry,
                modifier = modifier
            )
        } else {
            AiMessageBubble(
                message = message,
                onRetry = onRetry,
                onRegenerate = onRegenerate,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun UserMessageBubble(
    message: ChatMessage,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.red < 0.5f
    val bubbleColor = if (isDark) DarkUserBubble else LightUserBubble
    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 6.dp,
                        bottomStart = 20.dp,
                        bottomEnd = 20.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("user_message_${message.messageId}")
        ) {
            Column {
                Text(
                    text = message.displayText,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    when (message.status) {
                        "sending" -> {
                            CircularProgressIndicator(
                                strokeWidth = 1.5.dp,
                                modifier = Modifier
                                    .size(12.dp)
                                    .testTag("status_sending"),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        "sent" -> {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = "Sent to Firebase RTDB",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .testTag("status_sent")
                            )
                        }
                        "error" -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error sending",
                                tint = StatusError,
                                modifier = Modifier
                                    .size(14.dp)
                                    .testTag("status_error")
                            )
                        }
                    }
                }
            }
        }

        // Show retry button if message failed to send
        if (message.status == "error") {
            Row(
                modifier = Modifier
                    .padding(top = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.errorMessage ?: "Failed to deliver message",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = StatusError,
                    modifier = Modifier.padding(end = 8.dp)
                )

                FilledTonalButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = StatusError.copy(alpha = 0.15f),
                        contentColor = StatusError
                    ),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("retry_button_${message.messageId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun AiMessageBubble(
    message: ChatMessage,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val isDark = MaterialTheme.colorScheme.surface.red < 0.5f
    val bubbleColor = if (isDark) DarkAiBubble else LightAiBubble

    val sparkGradient = Brush.linearGradient(
        colors = listOf(GeminiBlue, GeminiPurple, GeminiCyan)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("ai_message_${message.messageId}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Sparkle avatar
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(sparkGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Gemo AI",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Header: Model name + status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Gemo AI",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (message.model.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = message.model,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Content area
                when {
                    message.isGenerating && message.text.isEmpty() -> {
                        // Polished typing/thinking animation
                        AiGeneratingIndicator(
                            statusText = if (message.model.isNotEmpty()) "Generating with ${message.model}…" else "Gemo is thinking…"
                        )
                    }

                    message.isError -> {
                        // Error card with clear message and retry action
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StatusError.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Server Error",
                                        tint = StatusError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = message.errorMessage ?: "Server connection failed. Please check your connection and try again.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedButton(
                                    onClick = onRetry,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("retry_ai_response_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    else -> {
                        // Display actual AI response with Markdown & Code blocks
                        Column {
                            MarkdownContent(content = message.text)

                            // If partially generating with text coming in
                            if (message.isGenerating) {
                                Spacer(modifier = Modifier.height(8.dp))
                                AiGeneratingIndicator(statusText = "Streaming from backend…")
                            }

                            // Action buttons when completed
                            if (message.status == "completed" || !message.isGenerating) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Copy response button
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("AI response", message.text))
                                            isCopied = true
                                            Toast.makeText(context, "Copied response to clipboard", Toast.LENGTH_SHORT).show()
                                            scope.launch {
                                                delay(2000)
                                                isCopied = false
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("copy_response_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = if (isCopied) "Copied response" else "Copy response",
                                            tint = if (isCopied) Color(0xFF34A853) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Regenerate button
                                    IconButton(
                                        onClick = onRegenerate,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("regenerate_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Regenerate response",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
