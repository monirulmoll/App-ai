package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ConnectionStatus
import com.example.ui.components.ChatInputBar
import com.example.ui.components.ChatTopBar
import com.example.ui.components.ConversationDrawer
import com.example.ui.components.EmptyChatWelcome
import com.example.ui.components.MessageItem
import com.example.ui.components.ModelSelectorSheet
import com.example.ui.components.RenameDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.theme.StatusError
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

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

    // Handle back button when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Auto-scroll to bottom on new message or updated text
    val lastMessageText = uiState.messages.lastOrNull()?.text ?: ""
    val messageCount = uiState.messages.size
    LaunchedEffect(messageCount, lastMessageText) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Auto-scroll to bottom when keyboard opens so user sees the latest messages
    val isImeOpen = WindowInsets.isImeVisible
    LaunchedEffect(isImeOpen) {
        if (isImeOpen && uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
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
                    currentModelName = uiState.currentConversation?.model?.ifEmpty { uiState.settings.modelName }
                        ?: uiState.settings.modelName,
                    connectionStatus = uiState.connectionStatus,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenModelSelector = { viewModel.setShowModelSelector(true) },
                    onOpenSettings = { viewModel.setShowSettingsDialog(true) },
                    onNewChat = { viewModel.startNewChat() },
                    onCheckConnection = { viewModel.checkConnection() }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Server unavailable or error banner
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

                // Messages area or Empty State Welcome (takes weight 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (uiState.messages.isEmpty()) {
                        EmptyChatWelcome(
                            modelName = uiState.currentConversation?.model?.ifEmpty { uiState.settings.modelName }
                                ?: uiState.settings.modelName,
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
                            items(uiState.messages, key = { it.messageId }) { msg ->
                                MessageItem(
                                    message = msg,
                                    onRetry = { viewModel.retryMessage(msg) },
                                    onRegenerate = {
                                        val userMsg = uiState.messages
                                            .takeWhile { it.messageId != msg.messageId }
                                            .lastOrNull { it.isUser }
                                        if (userMsg != null) {
                                            viewModel.retryMessage(userMsg)
                                        } else {
                                            viewModel.retryMessage(msg)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Chat Input Bar sits flush at bottom, padded by max(navigationBars, ime)
                ChatInputBar(
                    text = uiState.inputText,
                    onTextChanged = viewModel::onInputTextChanged,
                    onSendMessage = { viewModel.sendMessage() },
                    onStopGeneration = { viewModel.stopGeneration() },
                    isGenerating = uiState.isGenerating,
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.navigationBars.union(WindowInsets.ime)
                    )
                )
            }
        }
    }

    // Model Selector Sheet
    if (uiState.showModelSelector) {
        ModelSelectorSheet(
            selectedModel = uiState.currentConversation?.model?.ifEmpty { uiState.settings.modelName }
                ?: uiState.settings.modelName,
            onModelSelected = { modelName, provider ->
                viewModel.selectModel(modelName, provider)
            },
            onDismiss = { viewModel.setShowModelSelector(false) }
        )
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
}
