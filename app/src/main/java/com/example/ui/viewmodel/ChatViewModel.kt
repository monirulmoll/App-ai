package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
import com.example.data.model.Conversation
import com.example.data.model.LlmModelOption
import com.example.data.model.LlmSettings
import com.example.data.model.ModelSwitchResponse
import com.example.data.model.UserMemory
import com.example.data.model.WorkspaceFile
import com.example.data.repository.ChatRepository
import com.example.ui.screens.AppScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentConversation: Conversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTING,
    val isConnected: Boolean = false,
    val settings: LlmSettings = LlmSettings(),
    val inputText: String = "",
    val searchQuery: String = "",
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val showModelSelector: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val conversationToRename: Conversation? = null,
    val activeGgufModel: String = "",
    val modelSwitchResponse: ModelSwitchResponse? = null,
    val isModelSwitching: Boolean = false,
    val models: List<LlmModelOption> = emptyList(),
    // Next-Gen additions
    val selectedImageUri: String? = null,
    val selectedImageBase64: String? = null,
    val isAgentMode: Boolean = false,
    val showWorkspaceDialog: Boolean = false,
    val showMemoryDialog: Boolean = false,
    val showImageViewerDialog: Boolean = false,
    val viewingImageUrl: String? = null,
    val workspaceFiles: List<WorkspaceFile> = emptyList(),
    val memories: List<UserMemory> = emptyList()
) {
    val filteredConversations: List<Conversation>
        get() = if (searchQuery.isBlank()) {
            conversations
        } else {
            conversations.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.lastMessage.contains(searchQuery, ignoreCase = true)
            }
        }
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application.applicationContext)

    private val _inputText = MutableStateFlow("")
    private val _searchQuery = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _showModelSelector = MutableStateFlow(false)
    private val _showSettingsDialog = MutableStateFlow(false)
    private val _showRenameDialog = MutableStateFlow(false)
    private val _conversationToRename = MutableStateFlow<Conversation?>(null)
    private val _dismissedModelSwitchTime = MutableStateFlow<Long>(0L)

    private val _selectedImageUri = MutableStateFlow<String?>(null)
    private val _selectedImageBase64 = MutableStateFlow<String?>(null)
    private val _isForceStopped = MutableStateFlow(false)
    private val _isAgentMode = MutableStateFlow(false)
    private val _showWorkspaceDialog = MutableStateFlow(false)
    private val _showMemoryDialog = MutableStateFlow(false)
    private val _showImageViewerDialog = MutableStateFlow(false)
    private val _viewingImageUrl = MutableStateFlow<String?>(null)
    private val _currentScreen = MutableStateFlow(AppScreen.SPLASH)
    val currentScreen: StateFlow<AppScreen> = _currentScreen

    val currentUserProfile: StateFlow<com.example.data.model.GoogleUserProfile?> = repository.currentUserProfile

    fun signInWithGoogle(
        activity: androidx.activity.ComponentActivity,
        serverClientId: String? = null,
        onResult: (Result<com.example.data.model.GoogleUserProfile>) -> Unit
    ) {
        repository.signInWithGoogle(activity, serverClientId, onResult)
    }

    fun signOutGoogle(onComplete: () -> Unit = {}) {
        repository.signOutGoogle(onComplete)
    }

    fun isOnboardingCompleted(): Boolean = repository.isOnboardingCompleted()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun navigateBack() {
        when (_currentScreen.value) {
            AppScreen.CHAT, AppScreen.CONVERSATIONS, AppScreen.IMAGE_GEN,
            AppScreen.VISION, AppScreen.FILE_MANAGER, AppScreen.CODE_WORKSPACE,
            AppScreen.AGENT_MODE, AppScreen.MODEL_SELECTION, AppScreen.MEMORY,
            AppScreen.SETTINGS, AppScreen.BACKEND_STATUS -> {
                _currentScreen.value = AppScreen.HOME
            }
            AppScreen.ONBOARDING -> {
                _currentScreen.value = AppScreen.SPLASH
            }
            else -> {}
        }
    }

    fun finishOnboarding() {
        repository.setOnboardingCompleted(true)
        _currentScreen.value = AppScreen.HOME
    }

    fun startFromSplash() {
        if (repository.isOnboardingCompleted()) {
            _currentScreen.value = AppScreen.HOME
        } else {
            _currentScreen.value = AppScreen.ONBOARDING
        }
    }

    fun sendQuickPrompt(promptText: String) {
        _inputText.value = promptText
        sendMessage()
        _currentScreen.value = AppScreen.CHAT
    }

    fun sendToChatWithImage(text: String, imageUri: String, imageBase64: String) {
        _inputText.value = text
        _selectedImageUri.value = imageUri
        _selectedImageBase64.value = imageBase64
        sendMessage()
        _currentScreen.value = AppScreen.CHAT
    }

    val uiState: StateFlow<ChatUiState> = combine(
        combine(
            repository.conversations,
            repository.currentConversation,
            repository.messages,
            repository.connectionStatus,
            repository.isConnectedToRtdb
        ) { conversations, currentConv, messages, connStatus, isConnected ->
            tuple5(conversations, currentConv, messages, connStatus, isConnected)
        },
        combine(
            repository.settings,
            repository.activeModelFromRtdb,
            repository.modelSwitchResponse,
            repository.isModelSwitching,
            repository.userModels
        ) { settings, activeRtdbModel, modelResponse, isSwitching, models ->
            tuple5(settings, activeRtdbModel, modelResponse, isSwitching, models)
        },
        combine(
            _inputText,
            _searchQuery,
            _errorMessage,
            _showModelSelector,
            _showSettingsDialog
        ) { inputText, searchQuery, errorMsg, showModel, showSettings ->
            tuple5(inputText, searchQuery, errorMsg, showModel, showSettings)
        },
        combine(
            _showRenameDialog,
            _conversationToRename,
            _dismissedModelSwitchTime,
            _selectedImageUri,
            combine(_selectedImageBase64, _isForceStopped, repository.isCurrentlyGeneratingFlow) { b64, stopped, genFlow ->
                GenState(b64, stopped, genFlow)
            }
        ) { showRename, convToRename, dismissedTime, imageUri, genState ->
            tuple5(showRename, convToRename, dismissedTime, imageUri, genState)
        },
        combine(
            _isAgentMode,
            _showWorkspaceDialog,
            _showMemoryDialog,
            _showImageViewerDialog,
            combine(_viewingImageUrl, repository.workspaceFiles, repository.memories) { url, files, mems ->
                Triple(url, files, mems)
            }
        ) { agentMode, showWorkspace, showMem, showViewer, (viewingUrl, files, mems) ->
            tuple7(agentMode, showWorkspace, showMem, showViewer, viewingUrl, files, mems)
        }
    ) { (conversations, currentConv, messages, connStatus, isConnected),
        (settings, activeRtdbModel, modelResponse, isSwitching, models),
        (inputText, searchQuery, errorMsg, showModel, showSettings),
        (showRename, convToRename, dismissedTime, imageUri, genState),
        (agentMode, showWorkspace, showMem, showViewer, viewingUrl, files, mems) ->

        val isGenerating = !genState.stopped && (genState.genFlow || messages.any { it.isAi && it.status == "generating" })
        val currentActiveModel = activeRtdbModel ?: currentConv?.model ?: settings.modelName
        val visibleModelResponse = if (modelResponse != null && modelResponse.timestamp > dismissedTime) modelResponse else null

        ChatUiState(
            conversations = conversations,
            currentConversation = currentConv,
            messages = messages,
            connectionStatus = connStatus,
            isConnected = isConnected,
            settings = settings,
            inputText = inputText,
            searchQuery = searchQuery,
            isGenerating = isGenerating,
            errorMessage = errorMsg,
            showModelSelector = showModel,
            showSettingsDialog = showSettings,
            showRenameDialog = showRename,
            conversationToRename = convToRename,
            activeGgufModel = currentActiveModel,
            modelSwitchResponse = visibleModelResponse,
            isModelSwitching = isSwitching,
            models = models,
            selectedImageUri = imageUri,
            selectedImageBase64 = genState.imageBase64,
            isAgentMode = agentMode || settings.agentModeEnabled,
            showWorkspaceDialog = showWorkspace,
            showMemoryDialog = showMem,
            showImageViewerDialog = showViewer,
            viewingImageUrl = viewingUrl,
            workspaceFiles = files,
            memories = mems
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onImageSelected(uri: String?, base64: String?) {
        _selectedImageUri.value = uri
        _selectedImageBase64.value = base64
    }

    fun clearSelectedImage() {
        _selectedImageUri.value = null
        _selectedImageBase64.value = null
    }

    fun toggleAgentMode() {
        val next = !_isAgentMode.value
        _isAgentMode.value = next
        repository.toggleAgentMode(next)
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        val imageUri = _selectedImageUri.value
        val imageBase64 = _selectedImageBase64.value
        if (text.isEmpty() && imageUri.isNullOrEmpty()) return

        _inputText.value = ""
        clearSelectedImage()
        _isForceStopped.value = false

        viewModelScope.launch {
            repository.sendMessage(
                text = text,
                attachedImageUri = imageUri,
                attachedImageBase64 = imageBase64,
                forcedAgentMode = _isAgentMode.value
            )
        }
    }

    fun retryMessage(message: ChatMessage) {
        _isForceStopped.value = false
        viewModelScope.launch {
            repository.retryMessage(message)
        }
    }

    fun regenerateResponse(aiMessage: ChatMessage) {
        _isForceStopped.value = false
        viewModelScope.launch {
            repository.regenerateMessage(aiMessage)
        }
    }

    fun editAndResend(message: ChatMessage) {
        _inputText.value = message.originalText ?: message.text
        if (message.imageUri != null) {
            _selectedImageUri.value = message.imageUri
            _selectedImageBase64.value = message.imageBase64
        }
    }

    fun stopGeneration() {
        _isForceStopped.value = true
        repository.stopGeneration()
    }

    fun startNewChat() {
        _isForceStopped.value = false
        viewModelScope.launch {
            repository.createNewConversation()
        }
    }

    fun selectConversation(id: String) {
        _isForceStopped.value = false
        viewModelScope.launch {
            repository.selectConversation(id)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }

    fun openRenameDialog(conversation: Conversation) {
        _conversationToRename.value = conversation
        _showRenameDialog.value = true
    }

    fun dismissRenameDialog() {
        _showRenameDialog.value = false
        _conversationToRename.value = null
    }

    fun submitRename(newTitle: String) {
        val conv = _conversationToRename.value ?: return
        if (newTitle.isNotBlank()) {
            repository.renameConversation(conv.id, newTitle)
        }
        dismissRenameDialog()
    }

    fun setShowModelSelector(show: Boolean) {
        _showModelSelector.value = show
    }

    fun selectModel(modelName: String, provider: String) {
        repository.selectModel(modelName, provider)
        _showModelSelector.value = false
    }

    fun addModel(modelInput: String) {
        val trimmed = modelInput.trim()
        if (trimmed.isNotEmpty()) {
            repository.addModel(trimmed)
        }
    }

    fun deleteModel(ggufFilename: String) {
        repository.deleteModel(ggufFilename)
    }

    fun dismissModelSwitchResponse() {
        _dismissedModelSwitchTime.value = System.currentTimeMillis()
    }

    fun setShowSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun updateSettings(settings: LlmSettings) {
        repository.updateSettings(settings)
        _isAgentMode.value = settings.agentModeEnabled
        _showSettingsDialog.value = false
    }

    fun updateSliderParameter(paramKey: String, value: Number) {
        repository.updateSliderParameter(paramKey, value)
    }

    fun updateSystemInstruction(instruction: String) {
        repository.updateSystemInstruction(instruction)
    }

    fun setShowWorkspaceDialog(show: Boolean) {
        _showWorkspaceDialog.value = show
    }

    fun setShowMemoryDialog(show: Boolean) {
        _showMemoryDialog.value = show
    }

    fun openImageViewer(urlOrUri: String) {
        _viewingImageUrl.value = urlOrUri
        _showImageViewerDialog.value = true
    }

    fun closeImageViewer() {
        _showImageViewerDialog.value = false
        _viewingImageUrl.value = null
    }

    // Workspace & Memory wrappers
    fun createWorkspaceFile(filename: String, content: String) {
        viewModelScope.launch {
            repository.createWorkspaceFile(filename, content)
        }
    }

    fun renameWorkspaceFile(oldFilename: String, newFilename: String) {
        viewModelScope.launch {
            repository.renameWorkspaceFile(oldFilename, newFilename)
        }
    }

    fun importWorkspaceFile(filename: String, bytes: ByteArray) {
        viewModelScope.launch {
            repository.importWorkspaceFile(filename, bytes)
        }
    }

    fun exportProjectZip(outputZipFile: java.io.File, onComplete: (Result<java.io.File>) -> Unit) {
        viewModelScope.launch {
            val result = repository.exportProjectZip(outputZipFile)
            onComplete(result)
        }
    }

    fun deleteWorkspaceFile(filename: String) {
        viewModelScope.launch {
            repository.deleteWorkspaceFile(filename)
        }
    }

    fun executeTerminalCommand(
        command: String,
        language: String = "shell",
        filename: String? = null,
        code: String? = null,
        onOutput: (stdout: String, stderr: String, exitCode: Int, status: String) -> Unit
    ): String {
        return repository.executeTerminalCommand(command, language, filename, code, onOutput)
    }

    fun cancelTerminalCommand(commandId: String) {
        repository.cancelTerminalCommand(commandId)
    }

    fun addMemory(content: String, category: String = "custom") {
        repository.addMemory(content, category)
    }

    fun deleteMemory(id: String) {
        repository.deleteMemory(id)
    }

    fun toggleMemory(id: String) {
        repository.toggleMemory(id)
    }

    fun clearAllMemories() {
        repository.clearAllMemories()
    }

    fun checkConnection() {
        repository.checkConnection()
    }

    fun clearChat() {
        repository.clearChat()
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    private fun <A, B, C, D, E> tuple5(a: A, b: B, c: C, d: D, e: E) = Tuple5(a, b, c, d, e)
    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    private fun <A, B, C, D, E, F, G> tuple7(a: A, b: B, c: C, d: D, e: E, f: F, g: G) = Tuple7(a, b, c, d, e, f, g)
    private data class Tuple7<A, B, C, D, E, F, G>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G)

    private data class GenState(val imageBase64: String?, val stopped: Boolean, val genFlow: Boolean)
}
