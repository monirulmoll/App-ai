package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.GeminiPurple
import com.example.ui.theme.GleamGradientCyan
import com.example.ui.theme.GleamGradientPink
import com.example.ui.theme.GleamGradientViolet
import com.example.ui.theme.StatusError

@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    isGenerating: Boolean,
    selectedImageUri: String?,
    selectedImageBase64: String?,
    onClearSelectedImage: () -> Unit,
    onPickImage: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onStartVoiceInput: () -> Unit,
    isAgentMode: Boolean,
    onToggleAgentMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAttachMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chat_input_bar"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Selected Image Preview Thumbnail
            AnimatedVisibility(visible = !selectedImageUri.isNullOrEmpty() || !selectedImageBase64.isNullOrEmpty()) {
                val imageSrc = selectedImageUri ?: selectedImageBase64
                if (!imageSrc.isNullOrEmpty()) {
                    val previewBitmap = remember(imageSrc) {
                        try {
                            if (imageSrc.startsWith("content://") || imageSrc.startsWith("file://")) {
                                context.contentResolver.openInputStream(Uri.parse(imageSrc))?.use {
                                    BitmapFactory.decodeStream(it)
                                }
                            } else {
                                val clean = if (imageSrc.contains(",")) imageSrc.substringAfter(",") else imageSrc
                                val bytes = Base64.decode(clean, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, GeminiBlue, RoundedCornerShape(10.dp))
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "Attached image preview",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        // Remove button
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { onClearSelectedImage() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove image",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // Main Input Field + Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Attachment (+) Button
                Box {
                    IconButton(
                        onClick = { showAttachMenu = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAgentMode) GeminiPurple.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .then(
                                if (isAgentMode) Modifier.border(1.2.dp, GeminiPurple, CircleShape)
                                else Modifier
                            )
                            .testTag("attachment_menu_button")
                    ) {
                        Icon(
                            imageVector = if (isAgentMode) Icons.Default.SmartToy else Icons.Default.Add,
                            contentDescription = "Attach options",
                            tint = if (isAgentMode) GeminiPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isAgentMode) "🤖 Agent Mode: ON (Tap to Disable)"
                                    else "🤖 Agent Mode: OFF (Tap to Enable)"
                                )
                            },
                            onClick = {
                                showAttachMenu = false
                                onToggleAgentMode()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = if (isAgentMode) GeminiPurple else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("🖼️ Upload Image / Vision") },
                            onClick = {
                                showAttachMenu = false
                                onPickImage()
                            },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = GeminiCyan) }
                        )
                        DropdownMenuItem(
                            text = { Text("🎬 Send Video Reference / Clip") },
                            onClick = {
                                showAttachMenu = false
                                onTextChanged("Analyze video: ")
                            },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = GeminiPurple) }
                        )
                        DropdownMenuItem(
                            text = { Text("📄 Attach Document / File") },
                            onClick = {
                                showAttachMenu = false
                                onOpenWorkspace()
                            },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = GeminiCyan) }
                        )
                        DropdownMenuItem(
                            text = { Text("📁 File & Code Workspace") },
                            onClick = {
                                showAttachMenu = false
                                onOpenWorkspace()
                            },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = GeminiBlue) }
                        )
                        DropdownMenuItem(
                            text = { Text("🧮 Run Calculator") },
                            onClick = {
                                showAttachMenu = false
                                onTextChanged("Calculate: ")
                            },
                            leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = GeminiPurple) }
                        )
                        DropdownMenuItem(
                            text = { Text("🎨 Generate Image") },
                            onClick = {
                                showAttachMenu = false
                                onTextChanged("Generate image: ")
                            },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = GeminiPurple) }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Multiline TextField Container with Chamakte Border
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    GeminiCyan.copy(alpha = 0.35f),
                                    GeminiPurple.copy(alpha = 0.35f)
                                )
                            ),
                            shape = RoundedCornerShape(22.dp)
                        ),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    TextField(
                        value = text,
                        onValueChange = onTextChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("message_input_field"),
                        placeholder = {
                            Text(
                                text = if (isAgentMode) "Ask Gemo AI or instruct agent…" else stringResource(id = R.string.type_message_hint),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Microphone button (Voice Input)
                IconButton(
                    onClick = onStartVoiceInput,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .border(1.dp, GeminiCyan.copy(alpha = 0.3f), CircleShape)
                        .testTag("voice_input_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = GeminiCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action button: Stop when generating, otherwise Send with Chamakte Gradient
                AnimatedContent(
                    targetState = isGenerating,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "send_stop_transition"
                ) { generating ->
                    if (generating) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(StatusError)
                                .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                .testTag("stop_generation_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = onStopGeneration) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = stringResource(id = R.string.stop_generation),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        val canSend = text.isNotBlank() || !selectedImageUri.isNullOrEmpty() || !selectedImageBase64.isNullOrEmpty()
                        val buttonBrush = if (canSend) {
                            Brush.linearGradient(
                                listOf(GleamGradientViolet, GleamGradientPink, GleamGradientCyan)
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(buttonBrush)
                                .then(
                                    if (canSend) {
                                        Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                    } else Modifier
                                )
                                .testTag("send_message_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = onSendMessage,
                                enabled = canSend
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(id = R.string.send),
                                    tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
