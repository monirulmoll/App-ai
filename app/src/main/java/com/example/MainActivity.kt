package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AgentDashboardScreen
import com.example.ui.screens.AgentSectionScreen
import com.example.ui.screens.AppScreen
import com.example.ui.screens.BackendStatusScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CodeWorkspaceScreen
import com.example.ui.screens.ConversationsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.MemoryManagementScreen
import com.example.ui.screens.ModelManagementScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.VisionAnalysisScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: ChatViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

            val isDarkTheme = when (uiState.settings.themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            val activeModelName = uiState.activeGgufModel.ifEmpty {
                uiState.currentConversation?.model?.ifEmpty { uiState.settings.modelName }
                    ?: uiState.settings.modelName
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Crossfade(targetState = currentScreen, label = "main_screen_crossfade") { screen ->
                        when (screen) {
                            AppScreen.SPLASH -> {
                                SplashScreen(
                                    connectionStatus = uiState.connectionStatus,
                                    onGetStarted = { viewModel.startFromSplash() }
                                )
                            }
                            AppScreen.ONBOARDING -> {
                                OnboardingScreen(
                                    onFinishOnboarding = { viewModel.finishOnboarding() }
                                )
                            }
                            AppScreen.HOME -> {
                                HomeScreen(
                                    conversations = uiState.conversations,
                                    activeModelName = activeModelName,
                                    onNavigate = { target -> viewModel.navigateTo(target) },
                                    onSelectConversation = { convId ->
                                        viewModel.selectConversation(convId)
                                    },
                                    onQuickPrompt = { promptText ->
                                        viewModel.sendQuickPrompt(promptText)
                                    }
                                )
                            }
                            AppScreen.CHAT -> {
                                ChatScreen(viewModel = viewModel)
                            }
                            AppScreen.CONVERSATIONS -> {
                                ConversationsScreen(
                                    conversations = uiState.conversations,
                                    searchQuery = uiState.searchQuery,
                                    onSearchChanged = { viewModel.onSearchQueryChanged(it) },
                                    onSelectConversation = { convId ->
                                        viewModel.selectConversation(convId)
                                        viewModel.navigateTo(AppScreen.CHAT)
                                    },
                                    onNewChat = {
                                        viewModel.startNewChat()
                                        viewModel.navigateTo(AppScreen.CHAT)
                                    },
                                    onRenameConversation = { conv ->
                                        viewModel.openRenameDialog(conv)
                                    },
                                    onDeleteConversation = { convId ->
                                        viewModel.deleteConversation(convId)
                                    },
                                    onNavigate = { target -> viewModel.navigateTo(target) },
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                            AppScreen.IMAGE_GEN -> {
                                ImageStudioScreen(
                                    onNavigate = { target -> viewModel.navigateTo(target) },
                                    onSendPromptToChat = { prompt ->
                                        viewModel.sendQuickPrompt(prompt)
                                    },
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                            AppScreen.VISION -> {
                                VisionAnalysisScreen(
                                    onNavigate = { target -> viewModel.navigateTo(target) },
                                    onSendToChatWithImage = { text, uri, b64 ->
                                        viewModel.sendToChatWithImage(text, uri, b64)
                                    },
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                            AppScreen.CODE_WORKSPACE, AppScreen.FILE_MANAGER -> {
                                CodeWorkspaceScreen(
                                    files = uiState.workspaceFiles,
                                    onCreateFile = { name, content ->
                                        viewModel.createWorkspaceFile(name, content)
                                    },
                                    onDeleteFile = { name ->
                                        viewModel.deleteWorkspaceFile(name)
                                    },
                                    onSendCodeToChat = { prompt ->
                                        viewModel.sendQuickPrompt(prompt)
                                    },
                                    onNavigate = { target -> viewModel.navigateTo(target) },
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                            AppScreen.AGENT_MODE -> {
                                AgentSectionScreen(
                                    isAgentMode = uiState.isAgentMode,
                                    onToggleAgentMode = { viewModel.toggleAgentMode() },
                                    workspaceFiles = uiState.workspaceFiles,
                                    onCreateWorkspaceFile = { name: String, content: String ->
                                        viewModel.createWorkspaceFile(name, content)
                                    },
                                    onDeleteWorkspaceFile = { name: String ->
                                        viewModel.deleteWorkspaceFile(name)
                                    },
                                    onSendPromptToChat = { prompt: String ->
                                        viewModel.sendQuickPrompt(prompt)
                                    },
                                    onNavigate = { target: AppScreen -> viewModel.navigateTo(target) },
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                            AppScreen.MODEL_SELECTION -> {
                                ModelManagementScreen(
                                    activeModel = activeModelName,
                                    models = uiState.models,
                                    isModelSwitching = uiState.isModelSwitching,
                                    modelSwitchResponse = uiState.modelSwitchResponse,
                                    onSelectModel = { name, prov ->
                                        viewModel.selectModel(name, prov)
                                    },
                                    onAddModel = { input ->
                                        viewModel.addModel(input)
                                    },
                                    onDeleteModel = { filename ->
                                        viewModel.deleteModel(filename)
                                    },
                                    onDismissResponse = {
                                        viewModel.dismissModelSwitchResponse()
                                    },
                                    onNavigate = { target -> viewModel.navigateTo(target) },
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                            AppScreen.MEMORY -> {
                                MemoryManagementScreen(
                                    memories = uiState.memories,
                                    onAddMemory = { content, cat ->
                                        viewModel.addMemory(content, cat)
                                    },
                                    onDeleteMemory = { id ->
                                        viewModel.deleteMemory(id)
                                    },
                                    onToggleMemory = { id ->
                                        viewModel.toggleMemory(id)
                                    },
                                    onClearAll = {
                                        viewModel.clearAllMemories()
                                    },
                                    onNavigate = { target -> viewModel.navigateTo(target) },
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                            AppScreen.SETTINGS -> {
                                SettingsScreen(
                                    currentSettings = uiState.settings,
                                    connectionStatus = uiState.connectionStatus,
                                    onSaveSettings = { newSettings ->
                                        viewModel.updateSettings(newSettings)
                                    },
                                    onCheckConnection = {
                                        viewModel.checkConnection()
                                    },
                                    onNavigate = { target -> viewModel.navigateTo(target) },
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                            AppScreen.BACKEND_STATUS -> {
                                BackendStatusScreen(
                                    firebaseUrl = uiState.settings.firebaseUrl,
                                    activeModel = activeModelName,
                                    connectionStatus = uiState.connectionStatus,
                                    onTestPing = { viewModel.checkConnection() },
                                    onNavigate = { target -> viewModel.navigateTo(target) },
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                            else -> {
                                HomeScreen(
                                    conversations = uiState.conversations,
                                    activeModelName = activeModelName,
                                    onNavigate = { target -> viewModel.navigateTo(target) },
                                    onSelectConversation = { convId ->
                                        viewModel.selectConversation(convId)
                                    },
                                    onQuickPrompt = { promptText ->
                                        viewModel.sendQuickPrompt(promptText)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
