package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.ui.theme.DarkAiBubble
import com.example.ui.theme.DarkUserBubble
import com.example.ui.theme.DarkUserBubbleEnd
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.GeminiNeonPink
import com.example.ui.theme.GeminiPurple
import com.example.ui.theme.GleamGradientCyan
import com.example.ui.theme.GleamGradientPink
import com.example.ui.theme.GleamGradientViolet
import com.example.ui.theme.LightAiBubble
import com.example.ui.theme.LightUserBubble
import com.example.ui.theme.StatusError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: ChatMessage,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onViewImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // If AI message is currently generating and has no text yet,
    // suppress it completely so the screenshot's placeholder box never appears.
    // Only the single stylish AiGeneratingIndicator displays the thinking animation.
    if (message.isAi && message.isGenerating && message.text.isBlank()) {
        return
    }

    var showActionSheet by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(initialOffsetY = { 30 })
    ) {
        if (message.isUser) {
            UserMessageBubble(
                message = message,
                onRetry = onRetry,
                onEdit = onEdit,
                onViewImage = onViewImage,
                onLongPress = { showActionSheet = true },
                modifier = modifier
            )
        } else {
            AiMessageBubble(
                message = message,
                onRetry = onRetry,
                onRegenerate = onRegenerate,
                onViewImage = onViewImage,
                onLongPress = { showActionSheet = true },
                modifier = modifier
            )
        }
    }

    if (showActionSheet) {
        MessageActionBottomSheet(
            message = message,
            onDismiss = { showActionSheet = false },
            onRetry = onRetry,
            onRegenerate = onRegenerate,
            onEdit = onEdit
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserMessageBubble(
    message: ChatMessage,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onViewImage: (String) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.surface.red < 0.5f
    val bubbleBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF1E2846),
                Color(0xFF281E48),
                Color(0xFF1B243B)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFE8F0FE),
                Color(0xFFF3E8FD)
            )
        )
    }
    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 22.dp,
                            topEnd = 6.dp,
                            bottomStart = 22.dp,
                            bottomEnd = 22.dp
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(GeminiBlue.copy(alpha = 0.4f), GeminiPurple.copy(alpha = 0.5f))
                        ),
                        shape = RoundedCornerShape(
                            topStart = 22.dp,
                            topEnd = 6.dp,
                            bottomStart = 22.dp,
                            bottomEnd = 22.dp
                        )
                    )
                    .background(bubbleBrush)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("user_message_${message.messageId}")
            ) {
                Column {
                    // Attached Image Preview
                    if (message.hasImage) {
                        val imageSrc = message.imageUri ?: message.imageBase64
                        if (!imageSrc.isNullOrEmpty()) {
                            ImageThumbnail(
                                source = imageSrc,
                                onClick = { onViewImage(imageSrc) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Tool call badge if executed
                    if (!message.toolCall.isNullOrBlank()) {
                        ToolCallBadge(toolCall = message.toolCall)
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = message.displayText,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Status and timestamp row
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        UserStatusIcon(status = message.status)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // User Avatar with Chamakte Ring
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(GeminiBlue, GeminiPurple)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Action row for User (Edit, Retry)
        if (message.isError) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = message.errorMessage ?: "Failed to send",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError
                )
                FilledTonalButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiMessageBubble(
    message: ChatMessage,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onViewImage: (String) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.surface.red < 0.5f
    val bubbleColor = if (isDark) DarkAiBubble else LightAiBubble
    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Start
        ) {
            // AI Sparkle Avatar with Orbiting Glow
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(GleamGradientViolet, GleamGradientPink, GleamGradientCyan)
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Gemo AI",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                topStart = 6.dp,
                                topEnd = 22.dp,
                                bottomStart = 22.dp,
                                bottomEnd = 22.dp
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    GeminiCyan.copy(alpha = 0.35f),
                                    GeminiPurple.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(
                                topStart = 6.dp,
                                topEnd = 22.dp,
                                bottomStart = 22.dp,
                                bottomEnd = 22.dp
                            )
                        )
                        .background(
                            if (isDark) {
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF181A24),
                                        Color(0xFF14151E)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFFFFFFFF),
                                        Color(0xFFF9FAFD)
                                    )
                                )
                            }
                        )
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onLongPress
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("ai_message_${message.messageId}")
                ) {
                    Column {
                        // Model Badge & Agent Mode Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (message.model.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GeminiBlue.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = message.model.removeSuffix(".gguf"),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = GeminiBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (message.agentMode) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GeminiPurple.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Agent Mode",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = GeminiPurple,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Tool Execution Result Accordion
                        if (!message.toolCall.isNullOrBlank() || !message.toolResult.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            ToolExecutionCard(
                                toolCall = message.toolCall ?: "Tool executed",
                                toolResult = message.toolResult ?: "Success"
                            )
                        }

                        // Generated Image Result
                        if (!message.imageResultUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ImageThumbnail(
                                source = message.imageResultUrl,
                                onClick = { onViewImage(message.imageResultUrl) }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Message Text / Error State
                        when {
                            message.isError -> {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = StatusError,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = message.errorMessage ?: "Generation failed",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = StatusError
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = onRegenerate,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Regenerate")
                                    }
                                }
                            }
                            else -> {
                                if (message.text.isNotBlank()) {
                                    MarkdownContent(
                                        content = message.text,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Bottom row: Timestamp
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                // AI Action Row (Copy, Share, Regenerate)
                if (message.isCompleted && message.text.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Gemo AI Reply", message.text))
                                copied = true
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    delay(2000)
                                    copied = false
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (copied) GeminiCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, message.text)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Gemo AI Response"))
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCallBadge(toolCall: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = GeminiPurple.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Build,
                contentDescription = null,
                tint = GeminiPurple,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Tool: $toolCall",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = GeminiPurple
            )
        }
    }
}

@Composable
private fun ToolExecutionCard(toolCall: String, toolResult: String) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = GeminiCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🛠️ $toolCall",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        text = "Result: $toolResult",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageThumbnail(source: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(source) {
        try {
            if (source.startsWith("content://") || source.startsWith("file://")) {
                context.contentResolver.openInputStream(Uri.parse(source))?.use {
                    BitmapFactory.decodeStream(it)
                }
            } else {
                val clean = if (source.contains(",")) source.substringAfter(",") else source
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable { onClick() }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Image attachment",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun UserStatusIcon(status: String) {
    when (status) {
        "sending" -> {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Sending",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(13.dp)
            )
        }
        "sent" -> {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = "Sent",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.size(13.dp)
            )
        }
        "error" -> {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = StatusError,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionBottomSheet(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = if (message.isUser) "User Message Actions" else "AI Response Actions",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Copy
            Surface(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Message", message.displayText))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GeminiBlue)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Copy text")
                }
            }

            // Share
            Surface(
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, message.displayText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Message"))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = GeminiCyan)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Share message")
                }
            }

            if (message.isUser) {
                // Edit
                Surface(
                    onClick = {
                        onEdit()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = GeminiPurple)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Edit prompt")
                    }
                }
            } else {
                // Regenerate
                Surface(
                    onClick = {
                        onRegenerate()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = GeminiCyan)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Regenerate response")
                    }
                }
            }
        }
    }
}
