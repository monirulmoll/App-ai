package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ConnectionStatus
import com.example.ui.components.AiGeneratingIndicator
import com.example.ui.components.ChatInputBar
import com.example.ui.components.ChatTopBar
import com.example.ui.components.ConversationDrawer
import com.example.ui.components.EmptyChatWelcome
import com.example.ui.components.ImageViewerDialog
import com.example.ui.components.MemoryDialog
import com.example.ui.components.MessageItem
import com.example.ui.components.ModelSelectorSheet
import com.example.ui.components.RenameDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.WorkspaceDialog
import com.example.ui.theme.StatusError
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Android Photo Picker Launcher (zero permission required)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            val maxDim = maxOf(bitmap.width, bitmap.height)
                            val scaled = if (maxDim > 1024) {
                                val scale = 1024f / maxDim
                                Bitmap.createScaledBitmap(
                                    bitmap,
                                    (bitmap.width * scale).toInt(),
                                    (bitmap.height * scale).toInt(),
                                    true
                                )
                            } else bitmap
                            val baos = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                            withContext(Dispatchers.Main) {
                                viewModel.onImageSelected(uri.toString(), base64)
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Voice Recognizer Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.onInputTextChanged(spoken)
            }
        }
    }

    // Back button handling
    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            viewModel.navigateTo(AppScreen.HOME)
        }
    }

    // Filter messages: Suppress AI placeholder messages that have empty text while generating.
    // The thinking state is cleanly displayed ONLY by AiGeneratingIndicator.
    val visibleMessages = remember(uiState.messages) {
        uiState.messages.filter { msg ->
            !(msg.isAi && msg.isGenerating && msg.text.isBlank())
        }
    }

    // Auto-scroll on new message or generation update
    val lastMessageText = visibleMessages.lastOrNull()?.text ?: ""
    val messageCount = visibleMessages.size
    LaunchedEffect(messageCount, lastMessageText, uiState.isGenerating) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.size - 1)
        }
    }

    val isImeOpen = WindowInsets.isImeVisible
    LaunchedEffect(isImeOpen) {
        if (isImeOpen && visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.size - 1)
        }
    }

    BackHandler {
        viewModel.navigateBack()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = uiState.filteredConversations,
                currentConversationId = uiState.currentConversation?.id,
                searchQuery = uiState.searchQuery,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onSelectConversation = { convId ->
                    viewModel.selectConversation(convId)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onRenameConversation = { conv ->
                    viewModel.openRenameDialog(conv)
                },
                onDeleteConversation = { convId ->
                    viewModel.deleteConversation(convId)
                },
                onOpenWorkspace = {
                    scope.launch { drawerState.close() }
                    viewModel.setShowWorkspaceDialog(true)
                },
                onOpenMemory = {
                    scope.launch { drawerState.close() }
                    viewModel.setShowMemoryDialog(true)
                },
                onNavigateHome = {
                    scope.launch { drawerState.close() }
                    viewModel.navigateTo(AppScreen.HOME)
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag("chat_screen"),
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                ChatTopBar(
                    connectionStatus = uiState.connectionStatus,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSettings = { viewModel.setShowSettingsDialog(true) },
                    onOpenWorkspace = { viewModel.navigateTo(AppScreen.CODE_WORKSPACE) },
                    onOpenMemory = { viewModel.setShowMemoryDialog(true) },
                    onNewChat = { viewModel.startNewChat() },
                    onCheckConnection = { viewModel.checkConnection() },
                    onClearChat = { viewModel.clearChat() },
                    onBack = { viewModel.navigateBack() }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // General error banner
                AnimatedVisibility(
                    visible = !uiState.errorMessage.isNullOrBlank(),
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    uiState.errorMessage?.let { errText ->
                        Surface(
                            color = Color(0xFF3E1D1D),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = Color(0xFFFFEBEE),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = viewModel::dismissError,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color(0xFFFFEBEE).copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Server unavailable banner
                AnimatedVisibility(
                    visible = uiState.connectionStatus == ConnectionStatus.SERVER_UNAVAILABLE,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StatusError.copy(alpha = 0.15f))
                            .clickable { viewModel.checkConnection() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Server connection warning",
                            tint = StatusError,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Server unavailable. Tap to test Firebase RTDB connection.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = StatusError,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry connection",
                            tint = StatusError,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Messages area or Empty State Welcome
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (visibleMessages.isEmpty()) {
                        EmptyChatWelcome(
                            onSelectPrompt = { promptText ->
                                viewModel.onInputTextChanged(promptText)
                                viewModel.sendMessage()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("messages_lazy_column"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                        ) {
                            items(visibleMessages, key = { it.messageId }) { msg ->
                                MessageItem(
                                    message = msg,
                                    onRetry = { viewModel.retryMessage(msg) },
                                    onRegenerate = { viewModel.regenerateResponse(msg) },
                                    onEdit = { viewModel.editAndResend(msg) },
                                    onViewImage = { urlOrUri -> viewModel.openImageViewer(urlOrUri) }
                                )
                            }
                        }
                    }
                }

                // Generating Indicator when AI is active
                AnimatedVisibility(
                    visible = uiState.isGenerating,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    AiGeneratingIndicator(
                        statusText = if (uiState.isAgentMode) "Gemo is executing tools…" else "Thinking…"
                    )
                }

                // Chat Input Bar
                ChatInputBar(
                    text = uiState.inputText,
                    onTextChanged = viewModel::onInputTextChanged,
                    onSendMessage = { viewModel.sendMessage() },
                    onStopGeneration = { viewModel.stopGeneration() },
                    isGenerating = uiState.isGenerating,
                    selectedImageUri = uiState.selectedImageUri,
                    selectedImageBase64 = uiState.selectedImageBase64,
                    onClearSelectedImage = { viewModel.clearSelectedImage() },
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onOpenWorkspace = { viewModel.setShowWorkspaceDialog(true) },
                    onStartVoiceInput = {
                        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Gemo AI…")
                        }
                        try {
                            voiceLauncher.launch(speechIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isAgentMode = uiState.isAgentMode,
                    onToggleAgentMode = { viewModel.toggleAgentMode() },
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.navigationBars.union(WindowInsets.ime)
                    )
                )
            }
        }
    }

    // Settings Dialog
    if (uiState.showSettingsDialog) {
        SettingsDialog(
            currentSettings = uiState.settings,
            onSave = { newSettings ->
                viewModel.updateSettings(newSettings)
            },
            onDismiss = { viewModel.setShowSettingsDialog(false) }
        )
    }

    // Rename Dialog
    if (uiState.showRenameDialog && uiState.conversationToRename != null) {
        RenameDialog(
            initialTitle = uiState.conversationToRename?.title ?: "",
            onConfirm = { newTitle ->
                viewModel.submitRename(newTitle)
            },
            onDismiss = { viewModel.dismissRenameDialog() }
        )
    }

    // File Workspace Dialog
    if (uiState.showWorkspaceDialog) {
        WorkspaceDialog(
            files = uiState.workspaceFiles,
            onCreateFile = { filename, content ->
                viewModel.createWorkspaceFile(filename, content)
            },
            onDeleteFile = { filename ->
                viewModel.deleteWorkspaceFile(filename)
            },
            onDismiss = { viewModel.setShowWorkspaceDialog(false) }
        )
    }

    // AI Memory Dialog
    if (uiState.showMemoryDialog) {
        MemoryDialog(
            memories = uiState.memories,
            onAddMemory = { content, category ->
                viewModel.addMemory(content, category)
            },
            onDeleteMemory = { id ->
                viewModel.deleteMemory(id)
            },
            onToggleMemory = { id ->
                viewModel.toggleMemory(id)
            },
            onClearAll = { viewModel.clearAllMemories() },
            onDismiss = { viewModel.setShowMemoryDialog(false) }
        )
    }

    // Image Viewer Dialog
    if (uiState.showImageViewerDialog && !uiState.viewingImageUrl.isNullOrBlank()) {
        ImageViewerDialog(
            imageUrlOrBase64 = uiState.viewingImageUrl,
            onDismiss = { viewModel.closeImageViewer() }
        )
    }
}
