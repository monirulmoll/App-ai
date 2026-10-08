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
import com.example.data.repository.ChatRepository
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
    val models: List<LlmModelOption> = emptyList()
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
            _dismissedModelSwitchTime
        ) { showRename, convToRename, dismissedTime ->
            Triple(showRename, convToRename, dismissedTime)
        }
    ) { (conversations, currentConv, messages, connStatus, isConnected),
        (settings, activeRtdbModel, modelResponse, isSwitching, models),
        (inputText, searchQuery, errorMsg, showModel, showSettings),
        (showRename, convToRename, dismissedTime) ->

        val isGenerating = messages.any { it.isGenerating } || connStatus == ConnectionStatus.AI_GENERATING
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
            models = models
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

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        _inputText.value = ""
        viewModelScope.launch {
            repository.sendMessage(text)
        }
    }

    fun retryMessage(message: ChatMessage) {
        viewModelScope.launch {
            repository.retryMessage(message)
        }
    }

    fun stopGeneration() {
        repository.stopGeneration()
    }

    fun startNewChat() {
        viewModelScope.launch {
            repository.createNewConversation()
        }
    }

    fun selectConversation(id: String) {
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
        _showSettingsDialog.value = false
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
}
