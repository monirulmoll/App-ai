package com.example.data.repository

import android.content.Context
import com.example.data.agent.AgentToolEngine
import com.example.data.firebase.FirebaseRtdbManager
import com.example.data.local.LocalChatPreferences
import com.example.data.memory.MemoryManager
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
import com.example.data.model.Conversation
import com.example.data.model.LlmModelOption
import com.example.data.model.LlmSettings
import com.example.data.model.UserMemory
import com.example.data.model.WorkspaceFile
import com.example.data.router.RequestRouter
import com.example.data.terminal.NativeTerminalExecutor
import com.example.data.translator.TranslatorHelper
import com.example.data.workspace.FileWorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private data class RequestLangInfo(
    val rawPrompt: String,
    val englishPrompt: String,
    val sourceLang: String,
    val isLatinScript: Boolean
)

class ChatRepository(context: Context) {
    private val prefs = LocalChatPreferences(context)
    private val rtdbManager = FirebaseRtdbManager(context)
    val workspaceManager = FileWorkspaceManager(context)
    val memoryManager = MemoryManager(context, prefs)
    val toolEngine = AgentToolEngine(workspaceManager, memoryManager)
    val nativeTerminal = NativeTerminalExecutor(context)

    private val scope = CoroutineScope(Dispatchers.Main)
    private val requestLanguageMap = mutableMapOf<String, RequestLangInfo>()
    private val userOriginalTextMap = mutableMapOf<String, String>()
    private var lastActiveLanguageInfo: RequestLangInfo? = null

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _settings = MutableStateFlow(prefs.getSettings())
    val settings: StateFlow<LlmSettings> = _settings.asStateFlow()

    val connectionStatus: StateFlow<ConnectionStatus> = rtdbManager.connectionStatus
    val isConnectedToRtdb: StateFlow<Boolean> = rtdbManager.isConnectedToRtdb
    val activeModelFromRtdb: StateFlow<String?> = rtdbManager.activeModelFromRtdb
    val modelSwitchResponse: StateFlow<com.example.data.model.ModelSwitchResponse?> = rtdbManager.modelSwitchResponse
    val isModelSwitching: StateFlow<Boolean> = rtdbManager.isModelSwitching

    private val _userModels = MutableStateFlow<List<LlmModelOption>>(prefs.getSavedModels())
    val userModels: StateFlow<List<LlmModelOption>> = _userModels.asStateFlow()

    val memories: StateFlow<List<UserMemory>> = memoryManager.memories
    val workspaceFiles: StateFlow<List<WorkspaceFile>> = workspaceManager.files

    // Track active request ID currently awaiting AI response
    private var pendingRequestId: String? = null
    val isCurrentlyGeneratingFlow = MutableStateFlow(false)
    val isCurrentlyGenerating: Boolean get() = isCurrentlyGeneratingFlow.value
    private val stoppedRequestIds = mutableSetOf<String>()

    init {
        // Initialize Firebase with current saved URL
        val currentSettings = prefs.getSettings()
        rtdbManager.initialize(currentSettings.firebaseUrl)

        // Load existing conversations
        val savedConversations = prefs.getConversations().toMutableList()
        _conversations.value = savedConversations

        val activeId = prefs.getActiveConversationId()
        val initialConv = savedConversations.find { it.id == activeId }
            ?: savedConversations.firstOrNull()

        if (initialConv != null) {
            selectConversation(initialConv.id)
        } else {
            // Create initial welcome chat
            createNewConversation()
        }

        // Listen for active model updates written by backend to "/model"
        scope.launch {
            rtdbManager.activeModelFromRtdb.collect { activeModel ->
                if (!activeModel.isNullOrBlank()) {
                    val updatedSettings = _settings.value.copy(modelName = activeModel)
                    _settings.value = updatedSettings
                    prefs.saveSettings(updatedSettings)

                    val currentConvId = _currentConversation.value?.id
                    if (currentConvId != null) {
                        val updatedList = _conversations.value.map {
                            if (it.id == currentConvId) it.copy(model = activeModel) else it
                        }
                        _conversations.value = updatedList
                        prefs.saveConversations(updatedList)
                        _currentConversation.value = updatedList.find { it.id == currentConvId }
                    }
                }
            }
        }

        // Send initial model switch request to "/model/request"
        val initialGguf = LlmSettings.ensureGgufFilename(currentSettings.modelName)
        rtdbManager.requestModelSwitch(initialGguf)

        // Sync initial settings & features to RTDB
        rtdbManager.syncSettingsToRtdb(currentSettings)

        // Sync initial memories to RTDB if connected
        scope.launch {
            memoryManager.memories.collect { memList ->
                rtdbManager.syncMemories(memList)
            }
        }
    }

    fun getSettings(): LlmSettings = _settings.value

    fun selectModel(modelName: String, provider: String) {
        val exactGguf = LlmSettings.ensureGgufFilename(modelName)
        val updated = _settings.value.copy(modelName = exactGguf, provider = provider)
        _settings.value = updated
        prefs.saveSettings(updated)

        // Write selected model's exact .gguf filename to "/model/request"
        rtdbManager.requestModelSwitch(exactGguf)

        val currentConvId = _currentConversation.value?.id
        if (currentConvId != null) {
            val updatedList = _conversations.value.map {
                if (it.id == currentConvId) it.copy(model = exactGguf) else it
            }
            _conversations.value = updatedList
            prefs.saveConversations(updatedList)
            _currentConversation.value = updatedList.find { it.id == currentConvId }
        }
    }

    fun addModel(rawInput: String, customProvider: String = ""): LlmModelOption {
        val exactGguf = LlmSettings.ensureGgufFilename(rawInput)
        val provider = if (customProvider.isNotBlank()) customProvider else LlmSettings.detectProvider(exactGguf)
        val displayName = LlmSettings.getDisplayName(exactGguf)
        val newOption = LlmModelOption(
            provider = provider,
            modelName = displayName,
            ggufFilename = exactGguf,
            description = "Exact: $exactGguf",
            capabilities = listOf("chat", "agent")
        )
        val currentList = _userModels.value
        val updated = currentList.filterNot { it.ggufFilename.equals(exactGguf, ignoreCase = true) } + newOption
        _userModels.value = updated
        prefs.saveSavedModels(updated)

        // Select and write to /model/request
        selectModel(exactGguf, provider)
        return newOption
    }

    fun deleteModel(ggufFilename: String) {
        val updated = _userModels.value.filterNot { it.ggufFilename.equals(ggufFilename, ignoreCase = true) }
        _userModels.value = updated
        prefs.saveSavedModels(updated)
    }

    fun updateSettings(newSettings: LlmSettings) {
        val oldUrl = _settings.value.firebaseUrl
        _settings.value = newSettings
        prefs.saveSettings(newSettings)

        // Publish model change to RTDB
        rtdbManager.publishActiveModel(
            newSettings.modelName,
            newSettings.provider,
            _currentConversation.value?.id
        )

        // Sync all settings & button states to Firebase RTDB for backend
        rtdbManager.syncSettingsToRtdb(newSettings)

        if (oldUrl != newSettings.firebaseUrl) {
            rtdbManager.initialize(newSettings.firebaseUrl)
            _currentConversation.value?.let { conv ->
                subscribeToConversation(conv.id)
            }
        }
    }

    fun toggleAgentMode(enabled: Boolean) {
        val updated = _settings.value.copy(agentModeEnabled = enabled)
        updateSettings(updated)
    }

    fun updateSliderParameter(paramKey: String, value: Number) {
        // Update local settings in-memory
        val current = _settings.value
        val updated = when (paramKey) {
            "max_tokens" -> current.copy(maxTokens = value.toInt())
            "temperature" -> current.copy(temperature = value.toFloat())
            "timeout", "response_timeout_seconds" -> current.copy(responseTimeoutSeconds = value.toInt())
            else -> current
        }
        _settings.value = updated
        prefs.saveSettings(updated)
        rtdbManager.syncSliderParameter(paramKey, value)
    }

    fun updateSystemInstruction(instruction: String) {
        val current = _settings.value
        val updated = current.copy(systemInstruction = instruction)
        _settings.value = updated
        prefs.saveSettings(updated)
        rtdbManager.syncSystemInstruction(instruction)
    }

    fun checkConnection() {
        rtdbManager.probeServerReachability(_settings.value.firebaseUrl)
    }

    fun selectConversation(conversationId: String) {
        val conv = _conversations.value.find { it.id == conversationId } ?: return
        _currentConversation.value = conv
        prefs.setActiveConversationId(conversationId)

        // Load cached messages first
        val cached = prefs.getMessages(conversationId)
        _messages.value = cached

        // Subscribe to RTDB real-time changes
        subscribeToConversation(conversationId)
    }

    fun createNewConversation(title: String = "New Chat", model: String = _settings.value.modelName): Conversation {
        val newId = UUID.randomUUID().toString().take(12)
        val newConv = Conversation(
            id = newId,
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            model = model
        )

        val updatedList = listOf(newConv) + _conversations.value
        _conversations.value = updatedList
        prefs.saveConversations(updatedList)

        selectConversation(newId)
        return newConv
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        val updated = _conversations.value.map {
            if (it.id == conversationId) it.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis()) else it
        }
        _conversations.value = updated
        prefs.saveConversations(updated)

        if (_currentConversation.value?.id == conversationId) {
            _currentConversation.value = updated.find { it.id == conversationId }
        }
    }

    fun deleteConversation(conversationId: String) {
        rtdbManager.deleteConversation(conversationId) {}
        prefs.deleteConversationData(conversationId)

        val updated = _conversations.value.filter { it.id != conversationId }
        _conversations.value = updated
        prefs.saveConversations(updated)

        if (_currentConversation.value?.id == conversationId) {
            if (updated.isNotEmpty()) {
                selectConversation(updated.first().id)
            } else {
                createNewConversation()
            }
        }
    }

    private fun subscribeToConversation(conversationId: String) {
        rtdbManager.listenToConversation(
            conversationId = conversationId,
            onMessageReceived = { newMsg ->
                handleIncomingMessage(newMsg)
            },
            onMessageUpdated = { updatedMsg ->
                handleIncomingMessage(updatedMsg)
            },
            onError = { errorText ->
                pendingRequestId?.let { reqId ->
                    updateMessageStatusByRequestId(reqId, "error", errorText)
                }
            }
        )
    }

    private fun handleIncomingMessage(incoming: ChatMessage) {
        scope.launch {
            var processedMessage = incoming

            if (incoming.isUser) {
                val currentList = _messages.value
                val original = userOriginalTextMap[incoming.messageId]
                    ?: userOriginalTextMap[incoming.requestId]
                    ?: incoming.originalText?.takeIf { it.isNotBlank() }
                    ?: currentList.find { it.messageId == incoming.messageId || (it.requestId.isNotEmpty() && it.requestId == incoming.requestId && it.isUser) }?.let { it.originalText ?: it.text }
                    ?: incoming.text
                processedMessage = incoming.copy(text = original, originalText = original)
            } else if (incoming.isAi) {
                val currentList = _messages.value
                val existingMsg = currentList.find {
                    it.messageId == incoming.messageId ||
                    (incoming.requestId.isNotEmpty() && it.requestId == incoming.requestId && it.isAi)
                }

                // If user stopped this request, clamp status to completed and never resurrect generating
                val isStopped = (incoming.requestId.isNotEmpty() && stoppedRequestIds.contains(incoming.requestId)) ||
                        (!isCurrentlyGenerating && incoming.requestId != pendingRequestId && (existingMsg?.status == "completed"))
                val currentStatus = if (isStopped) "completed" else incoming.status

                val requestedModel = existingMsg?.model?.takeIf { it.isNotBlank() }
                    ?: _currentConversation.value?.model?.takeIf { it.isNotBlank() }
                    ?: _settings.value.modelName

                val isHistorical = (incoming.requestId != pendingRequestId && incoming.requestId.isNotEmpty() && existingMsg != null && existingMsg.status == "completed")

                if (isHistorical) {
                    // Do NOT re-translate historical messages! Keep existing text to prevent screen-opening glitch
                    processedMessage = incoming.copy(
                        text = existingMsg.text.ifBlank { incoming.text },
                        originalText = existingMsg.originalText ?: incoming.originalText,
                        model = requestedModel,
                        status = "completed",
                        toolCall = incoming.toolCall ?: existingMsg.toolCall,
                        toolResult = incoming.toolResult ?: existingMsg.toolResult
                    )
                } else if (incoming.text.isNotEmpty()) {
                    val langInfo = requestLanguageMap[incoming.requestId] ?: lastActiveLanguageInfo
                    val rawPrompt = langInfo?.rawPrompt ?: currentList.lastOrNull { it.isUser }?.let { it.originalText ?: it.text } ?: ""
                    val engPrompt = langInfo?.englishPrompt ?: currentList.lastOrNull { it.isUser }?.text ?: ""
                    val targetLang = langInfo?.sourceLang ?: "en"
                    val isLatin = langInfo?.isLatinScript ?: TranslatorHelper.isMostlyLatin(rawPrompt)

                    val formatted = TranslatorHelper.sanitizeAndFormatReply(
                        rawAiResponse = incoming.text,
                        rawPrompt = rawPrompt,
                        englishPrompt = engPrompt,
                        targetLang = targetLang,
                        isLatinScript = isLatin
                    )

                    // Real-time streaming:
                    // During streaming (status != "completed"), stream raw/formatted text directly into UI with 0 latency.
                    // Do NOT launch asynchronous Google Translate HTTP calls on intermediate streaming tokens.
                    // When fully completed (currentStatus == "completed"), if translator is ON and needed, translate once cleanly.
                    val finalDisplayText = if (!_settings.value.translatorEnabled) {
                        formatted
                    } else if (TranslatorHelper.isCreatorOrIdentityQuery(rawPrompt, engPrompt)) {
                        formatted
                    } else if (currentStatus == "completed" && targetLang != "en" && targetLang != "auto") {
                        TranslatorHelper.translateFromEnglishToUserLang(
                            englishText = formatted,
                            targetLang = targetLang,
                            preferRomanized = isLatin
                        )
                    } else {
                        formatted
                    }

                    processedMessage = incoming.copy(
                        text = finalDisplayText,
                        model = requestedModel,
                        status = currentStatus,
                        toolCall = incoming.toolCall ?: existingMsg?.toolCall,
                        toolResult = incoming.toolResult ?: existingMsg?.toolResult
                    )
                } else {
                    processedMessage = incoming.copy(
                        model = requestedModel,
                        status = currentStatus,
                        toolCall = incoming.toolCall ?: existingMsg?.toolCall,
                        toolResult = incoming.toolResult ?: existingMsg?.toolResult
                    )
                }
            }

            if (incoming.isAi && processedMessage.text.isNotBlank()) {
                val userPrompt = userOriginalTextMap[processedMessage.requestId] ?: ""
                val extracted = com.example.data.workspace.WorkspaceFileExtractor.extractFiles(processedMessage.text, userPrompt)
                if (extracted.isNotEmpty()) {
                    val fileNames = extracted.map { it.filename }
                    processedMessage = processedMessage.copy(generatedFiles = fileNames)
                    scope.launch {
                        extracted.forEach { file ->
                            createWorkspaceFile(file.filename, file.content)
                        }
                    }
                }
            }

            val currentList = _messages.value.toMutableList()
            val existingIndex = currentList.indexOfFirst {
                it.messageId == processedMessage.messageId || (it.requestId.isNotEmpty() && it.requestId == processedMessage.requestId && it.sender == processedMessage.sender)
            }

            if (existingIndex >= 0) {
                currentList[existingIndex] = processedMessage
            } else {
                currentList.add(processedMessage)
            }

            _messages.value = currentList
            _currentConversation.value?.let { conv ->
                prefs.saveMessages(conv.id, currentList)
            }

            if (incoming.isAi && incoming.requestId == pendingRequestId) {
                if (processedMessage.status == "completed" || incoming.status == "completed") {
                    rtdbManager.cancelResponseTimeout()
                    rtdbManager.setConnectionStatus(ConnectionStatus.RESPONSE_RECEIVED)
                    isCurrentlyGeneratingFlow.value = false
                    pendingRequestId = null
                } else if (processedMessage.status == "generating" && isCurrentlyGenerating) {
                    rtdbManager.setConnectionStatus(ConnectionStatus.AI_GENERATING)
                }
            }
        }
    }

    fun sendMessage(
        text: String,
        overrideModel: String? = null,
        attachedImageUri: String? = null,
        attachedImageBase64: String? = null,
        forcedAgentMode: Boolean? = null
    ) {
        val trimmed = text.trim()
        val hasImage = !attachedImageUri.isNullOrEmpty() || !attachedImageBase64.isNullOrEmpty()
        if (trimmed.isEmpty() && !hasImage) return

        val conv = _currentConversation.value ?: createNewConversation()
        val chosenModel = overrideModel ?: conv.model.ifEmpty { _settings.value.modelName }
        val agentModeActive = forcedAgentMode ?: _settings.value.agentModeEnabled

        // Route the request to determine intent and tools
        val routing = RequestRouter.route(
            prompt = trimmed,
            hasImage = hasImage,
            agentModeEnabled = agentModeActive,
            currentModel = chosenModel
        )

        // Generate request and message IDs
        val reqId = UUID.randomUUID().toString()
        val userMsgId = "msg_user_${System.currentTimeMillis()}"

        // Memory context lookup
        val memoryContext = memoryManager.buildContextForPrompt(trimmed, _settings.value.memoryEnabled)

        // Execute local tool if applicable (e.g. calculator or file operation)
        var localToolCall: String? = null
        var localToolResult: String? = null

        if (routing.detectedTool != null && agentModeActive) {
            localToolCall = "${routing.detectedTool}(${routing.toolInput ?: ""})"
            if (routing.detectedTool == "calculator" || routing.detectedTool == "file_workspace") {
                scope.launch {
                    val toolResult = toolEngine.executeTool(routing.detectedTool, routing.toolInput ?: trimmed)
                    localToolResult = toolResult.result
                }
            }
        }

        val userMessage = ChatMessage(
            messageId = userMsgId,
            conversationId = conv.id,
            sender = "user",
            text = trimmed.ifEmpty { "[Image Attachment]" },
            originalText = trimmed.ifEmpty { "[Image Attachment]" },
            timestamp = System.currentTimeMillis(),
            status = "sending",
            requestId = reqId,
            model = chosenModel,
            imageUri = attachedImageUri,
            imageBase64 = attachedImageBase64,
            toolCall = localToolCall,
            toolResult = localToolResult,
            agentMode = agentModeActive,
            intent = routing.intent.id,
            memoryContext = memoryContext.ifEmpty { null }
        )

        // Auto-title conversation from first prompt
        if (conv.title == "New Chat" || conv.title.isEmpty()) {
            val displayTitle = if (trimmed.length > 28) trimmed.take(28) + "…" else trimmed.ifEmpty { "Image Analysis" }
            renameConversation(conv.id, displayTitle)
        }

        userOriginalTextMap[userMsgId] = userMessage.text
        userOriginalTextMap[reqId] = userMessage.text

        val currentList = _messages.value.toMutableList()
        currentList.add(userMessage)
        _messages.value = currentList
        prefs.saveMessages(conv.id, currentList)

        val aiPlaceholderId = "msg_ai_${System.currentTimeMillis() + 1}"
        val aiPlaceholder = ChatMessage(
            messageId = aiPlaceholderId,
            conversationId = conv.id,
            sender = "ai",
            text = "",
            timestamp = System.currentTimeMillis() + 1,
            status = "generating",
            requestId = reqId,
            model = chosenModel,
            toolCall = localToolCall,
            toolResult = localToolResult,
            agentMode = agentModeActive,
            intent = routing.intent.id
        )

        pendingRequestId = reqId
        stoppedRequestIds.remove(reqId)
        isCurrentlyGeneratingFlow.value = true

        scope.launch {
            // Immediate zero-latency database transmission:
            // Send user message directly to Firebase RTDB without any blocking HTTP translation call.
            val messageToSend = userMessage.copy(
                text = trimmed.ifEmpty { "[Image Attachment]" },
                originalText = trimmed.ifEmpty { "[Image Attachment]" },
                sourceLang = "auto",
                status = "sent"
            )

            rtdbManager.sendMessage(
                message = messageToSend,
                onSuccess = {
                    updateUserMessageStatus(userMsgId, "sent", null)

                    val listWithAi = _messages.value.toMutableList()
                    if (listWithAi.none { it.requestId == reqId && it.isAi }) {
                        listWithAi.add(aiPlaceholder)
                        _messages.value = listWithAi
                        prefs.saveMessages(conv.id, listWithAi)
                    }

                    rtdbManager.startResponseTimeout(_settings.value.responseTimeoutSeconds) {
                        handleAiResponseTimeout(reqId, aiPlaceholderId)
                    }
                },
                onError = { errorText ->
                    updateUserMessageStatus(userMsgId, "error", errorText)
                    pendingRequestId = null
                    isCurrentlyGeneratingFlow.value = false
                }
            )
        }
    }

    private fun addErrorMessageToChat(convId: String, text: String, model: String, errorMsg: String) {
        val userMsgId = "msg_user_${System.currentTimeMillis()}"
        val errMessage = ChatMessage(
            messageId = userMsgId,
            conversationId = convId,
            sender = "user",
            text = text,
            originalText = text,
            timestamp = System.currentTimeMillis(),
            status = "error",
            errorMessage = errorMsg,
            model = model
        )
        val currentList = _messages.value.toMutableList()
        currentList.add(errMessage)
        _messages.value = currentList
        prefs.saveMessages(convId, currentList)
    }

    fun retryMessage(failedMessage: ChatMessage) {
        val conv = _currentConversation.value ?: return

        updateUserMessageStatus(failedMessage.messageId, "sending", null)

        val reqId = if (failedMessage.requestId.isNotEmpty()) failedMessage.requestId else UUID.randomUUID().toString()
        val updatedMessage = failedMessage.copy(
            status = "sent",
            requestId = reqId,
            errorMessage = null,
            timestamp = System.currentTimeMillis()
        )

        pendingRequestId = reqId

        rtdbManager.sendMessage(
            message = updatedMessage,
            onSuccess = {
                updateUserMessageStatus(failedMessage.messageId, "sent", null)

                val listWithAi = _messages.value.toMutableList()
                val existingAiIdx = listWithAi.indexOfFirst { it.requestId == reqId && it.isAi }
                if (existingAiIdx >= 0) {
                    listWithAi[existingAiIdx] = listWithAi[existingAiIdx].copy(
                        status = "generating",
                        errorMessage = null,
                        text = ""
                    )
                } else {
                    val aiPlaceholder = ChatMessage(
                        messageId = "msg_ai_${System.currentTimeMillis()}",
                        conversationId = conv.id,
                        sender = "ai",
                        text = "",
                        timestamp = System.currentTimeMillis() + 1,
                        status = "generating",
                        requestId = reqId,
                        model = failedMessage.model
                    )
                    listWithAi.add(aiPlaceholder)
                }
                _messages.value = listWithAi
                prefs.saveMessages(conv.id, listWithAi)

                rtdbManager.startResponseTimeout(_settings.value.responseTimeoutSeconds) {
                    handleAiResponseTimeout(reqId, "msg_ai_${System.currentTimeMillis()}")
                }
            },
            onError = { errorText ->
                updateUserMessageStatus(failedMessage.messageId, "error", errorText)
                pendingRequestId = null
            }
        )
    }

    fun regenerateMessage(aiMessage: ChatMessage) {
        val conv = _currentConversation.value ?: return
        val currentList = _messages.value
        val aiIdx = currentList.indexOfFirst { it.messageId == aiMessage.messageId }
        val userMsg = if (aiIdx > 0) currentList[aiIdx - 1] else currentList.lastOrNull { it.isUser }
        if (userMsg != null) {
            sendMessage(
                text = userMsg.originalText ?: userMsg.text,
                overrideModel = aiMessage.model.ifEmpty { conv.model },
                attachedImageUri = userMsg.imageUri,
                attachedImageBase64 = userMsg.imageBase64,
                forcedAgentMode = userMsg.agentMode
            )
        }
    }

    fun stopGeneration() {
        rtdbManager.cancelResponseTimeout()
        val currentConvId = _currentConversation.value?.id ?: ""
        val reqId = pendingRequestId ?: ""

        if (reqId.isNotEmpty()) {
            stoppedRequestIds.add(reqId)
        }
        isCurrentlyGeneratingFlow.value = false
        pendingRequestId = null

        // Instantly mark all generating AI messages in active conversation as completed
        val currentList = _messages.value.map { msg ->
            if (msg.isAi && (msg.isGenerating || msg.status == "generating")) {
                val updatedText = if (msg.text.isBlank()) "Generation stopped by user." else msg.text
                msg.copy(status = "completed", text = updatedText)
            } else {
                msg
            }
        }
        _messages.value = currentList
        _currentConversation.value?.let { conv ->
            prefs.saveMessages(conv.id, currentList)
        }

        rtdbManager.setConnectionStatus(ConnectionStatus.CONNECTED)

        // Notify backend in background asynchronously
        rtdbManager.requestStopGeneration(currentConvId, reqId) { _ -> }
    }

    private fun handleAiResponseTimeout(reqId: String, aiMsgId: String) {
        val currentList = _messages.value.toMutableList()
        val aiIdx = currentList.indexOfFirst { (it.requestId == reqId && it.isAi) || it.messageId == aiMsgId }
        val timeoutError = "Server response timed out. The backend did not reply in time. Please check backend connection and retry."
        if (aiIdx >= 0) {
            currentList[aiIdx] = currentList[aiIdx].copy(
                status = "error",
                errorMessage = timeoutError
            )
        } else {
            currentList.add(
                ChatMessage(
                    messageId = aiMsgId,
                    conversationId = _currentConversation.value?.id ?: "",
                    sender = "ai",
                    text = "",
                    timestamp = System.currentTimeMillis(),
                    status = "error",
                    requestId = reqId,
                    errorMessage = timeoutError
                )
            )
        }
        _messages.value = currentList
        _currentConversation.value?.let { conv ->
            prefs.saveMessages(conv.id, currentList)
        }
        pendingRequestId = null
        rtdbManager.setConnectionStatus(ConnectionStatus.SERVER_UNAVAILABLE)
    }

    private fun updateUserMessageStatus(messageId: String, status: String, errorMessage: String?) {
        val currentList = _messages.value.toMutableList()
        val idx = currentList.indexOfFirst { it.messageId == messageId }
        if (idx >= 0) {
            val original = userOriginalTextMap[messageId]
                ?: currentList[idx].originalText
                ?: currentList[idx].text
            currentList[idx] = currentList[idx].copy(
                text = original,
                originalText = original,
                status = status,
                errorMessage = errorMessage
            )
            _messages.value = currentList
            _currentConversation.value?.let { conv ->
                prefs.saveMessages(conv.id, currentList)
            }
        }
    }

    private fun updateMessageStatusByRequestId(requestId: String, status: String, errorMessage: String?) {
        val currentList = _messages.value.toMutableList()
        val idx = currentList.indexOfFirst { it.requestId == requestId && it.isAi }
        if (idx >= 0) {
            currentList[idx] = currentList[idx].copy(
                status = status,
                errorMessage = errorMessage
            )
            _messages.value = currentList
            _currentConversation.value?.let { conv ->
                prefs.saveMessages(conv.id, currentList)
            }
        }
    }

    fun clearChat() {
        val conv = _currentConversation.value ?: return
        _messages.value = emptyList()
        prefs.deleteConversationData(conv.id)
    }

    // Workspace & Memory Operations
    suspend fun createWorkspaceFile(filename: String, content: String): Result<WorkspaceFile> {
        val result = workspaceManager.createOrUpdateFile(filename, content)
        result.getOrNull()?.let { rtdbManager.syncWorkspaceFile(it) }
        return result
    }

    suspend fun renameWorkspaceFile(oldFilename: String, newFilename: String): Result<WorkspaceFile> {
        val result = workspaceManager.renameFile(oldFilename, newFilename)
        result.getOrNull()?.let { rtdbManager.syncWorkspaceFile(it) }
        return result
    }

    suspend fun importWorkspaceFile(filename: String, bytes: ByteArray): Result<WorkspaceFile> {
        val result = workspaceManager.importFile(filename, bytes)
        result.getOrNull()?.let { rtdbManager.syncWorkspaceFile(it) }
        return result
    }

    suspend fun exportProjectZip(outputZipFile: java.io.File): Result<java.io.File> {
        return workspaceManager.exportProjectZip(outputZipFile)
    }

    suspend fun deleteWorkspaceFile(filename: String): Result<Boolean> {
        return workspaceManager.deleteFile(filename)
    }

    suspend fun readWorkspaceFile(filename: String): Result<String> {
        return workspaceManager.readFile(filename)
    }

    // Terminal Operations (Real Termux execution + Autonomous backend runner)
    fun executeTerminalCommand(
        command: String,
        language: String = "shell",
        filename: String? = null,
        code: String? = null,
        onOutput: (stdout: String, stderr: String, exitCode: Int, status: String) -> Unit
    ): String {
        val trimmed = command.trim()
        val cmdId = "cmd_" + UUID.randomUUID().toString().take(10)

        // If code is provided for a workspace file, ensure it is written locally first
        if (!filename.isNullOrBlank() && !code.isNullOrBlank()) {
            scope.launch {
                workspaceManager.createOrUpdateFile(filename, code)
            }
        }

        val isBackendConnected = isConnectedToRtdb.value && rtdbManager.isDatabaseReady
        val isExplicitBackendLanguage = language == "python" || language == "cpp" || language == "c" ||
                trimmed.startsWith("python ") || trimmed.startsWith("python3 ") ||
                trimmed.startsWith("g++ ") || trimmed.startsWith("clang++ ")

        if (isExplicitBackendLanguage && isBackendConnected) {
            // Forward to Autonomous backend
            return rtdbManager.sendTerminalCommand(command, language, filename, code, onOutput)
        }

        // Run on local device shell (Termux-compatible native shell runner)
        scope.launch(Dispatchers.IO) {
            val result = nativeTerminal.executeCommand(trimmed) { outLine, errLine ->
                if (outLine != null) onOutput(outLine, "", 0, "RUNNING")
                if (errLine != null) onOutput("", errLine, 0, "RUNNING")
            }

            // Check if user tried to run python3/g++ but it's not on Android device shell
            if (result.exitCode == 127 && (trimmed.startsWith("python") || trimmed.startsWith("g++"))) {
                if (isBackendConnected) {
                    withContext(Dispatchers.Main) {
                        onOutput("", "[Termux notice: python3/g++ not found on local device, redirecting to Autonomous.py backend...]\n", 0, "RUNNING")
                        rtdbManager.sendTerminalCommand(command, language, filename, code, onOutput)
                    }
                    return@launch
                } else {
                    val helpMsg = "\n[Termux Shell: command not found: ${trimmed.substringBefore(' ')}]\n" +
                            "To execute Python or C++ scripts:\n" +
                            "  1) Connect Firebase in Settings to run via Autonomous.py\n" +
                            "  2) Or run in Termux app (`pkg install python`)\n"
                    withContext(Dispatchers.Main) {
                        onOutput(result.stdout, result.stderr + helpMsg, 127, "ERROR")
                    }
                    return@launch
                }
            }

            // If the command modified files (e.g. touch, rm, echo > file), refresh workspace
            workspaceManager.refreshFiles()

            withContext(Dispatchers.Main) {
                onOutput(
                    result.stdout,
                    result.stderr,
                    result.exitCode,
                    if (result.exitCode == 0) "COMPLETED" else "ERROR"
                )
            }
        }

        return cmdId
    }

    fun cancelTerminalCommand(commandId: String) {
        nativeTerminal.killActiveProcess()
        rtdbManager.cancelTerminalCommand(commandId)
    }

    fun addMemory(content: String, category: String = "custom"): UserMemory {
        return memoryManager.addMemory(content, category)
    }

    fun deleteMemory(id: String) {
        memoryManager.deleteMemory(id)
    }

    fun toggleMemory(id: String) {
        memoryManager.toggleMemory(id)
    }

    fun clearAllMemories() {
        memoryManager.clearAllMemories()
    }

    fun isOnboardingCompleted(): Boolean = prefs.isOnboardingCompleted()

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.setOnboardingCompleted(completed)
    }
}
