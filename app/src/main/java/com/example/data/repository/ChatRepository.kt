package com.example.data.repository

import android.content.Context
import com.example.data.firebase.FirebaseRtdbManager
import com.example.data.local.LocalChatPreferences
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
import com.example.data.model.Conversation
import com.example.data.model.LlmSettings
import com.example.data.translator.TranslatorHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    // Track active request ID currently awaiting AI response
    private var pendingRequestId: String? = null

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

        // Publish current active model to RTDB
        rtdbManager.publishActiveModel(
            currentSettings.modelName,
            currentSettings.provider,
            _currentConversation.value?.id
        )
    }

    fun getSettings(): LlmSettings = _settings.value

    fun selectModel(modelName: String, provider: String) {
        val updated = _settings.value.copy(modelName = modelName, provider = provider)
        _settings.value = updated
        prefs.saveSettings(updated)

        val currentConvId = _currentConversation.value?.id
        rtdbManager.publishActiveModel(modelName, provider, currentConvId)

        if (currentConvId != null) {
            val updatedList = _conversations.value.map {
                if (it.id == currentConvId) it.copy(model = modelName) else it
            }
            _conversations.value = updatedList
            prefs.saveConversations(updatedList)
            _currentConversation.value = updatedList.find { it.id == currentConvId }
        }
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

        if (oldUrl != newSettings.firebaseUrl) {
            rtdbManager.initialize(newSettings.firebaseUrl)
            // Re-subscribe current conversation to new database
            _currentConversation.value?.let { conv ->
                subscribeToConversation(conv.id)
            }
        }
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
                // Mark active pending message if any as error
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
                // Keep the user's original typed message in the bubble so it never changes to English on screen
                val currentList = _messages.value
                val original = userOriginalTextMap[incoming.messageId]
                    ?: userOriginalTextMap[incoming.requestId]
                    ?: incoming.originalText?.takeIf { it.isNotBlank() }
                    ?: currentList.find { it.messageId == incoming.messageId || (it.requestId.isNotEmpty() && it.requestId == incoming.requestId && it.isUser) }?.let { it.originalText ?: it.text }
                    ?: incoming.text
                processedMessage = incoming.copy(text = original, originalText = original)
            } else if (incoming.isAi) {
                val langInfo = requestLanguageMap[incoming.requestId] ?: lastActiveLanguageInfo
                val rawPrompt = langInfo?.rawPrompt ?: _messages.value.lastOrNull { it.isUser }?.let { it.originalText ?: it.text } ?: ""
                val engPrompt = langInfo?.englishPrompt ?: _messages.value.lastOrNull { it.isUser }?.text ?: ""
                val targetLang = langInfo?.sourceLang ?: _messages.value.lastOrNull { it.isUser }?.sourceLang ?: "en"
                val isLatin = langInfo?.isLatinScript ?: TranslatorHelper.isMostlyLatin(rawPrompt)

                // Model consistency: ensure the message reflects the model chosen by user for this chat
                val existingMsg = _messages.value.find {
                    it.messageId == incoming.messageId ||
                    (incoming.requestId.isNotEmpty() && it.requestId == incoming.requestId && it.isAi)
                }
                val requestedModel = existingMsg?.model?.takeIf { it.isNotBlank() }
                    ?: _currentConversation.value?.model?.takeIf { it.isNotBlank() }
                    ?: _settings.value.modelName

                if (incoming.text.isNotEmpty()) {
                    // 1. Enforce Rohit as creator and scrub competitor company mentions cleanly
                    val formatted = TranslatorHelper.sanitizeAndFormatReply(
                        rawAiResponse = incoming.text,
                        rawPrompt = rawPrompt,
                        englishPrompt = engPrompt,
                        targetLang = targetLang,
                        isLatinScript = isLatin
                    )

                    // 2. If target language is non-English, translate back into user language
                    val finalDisplayText = if (targetLang != "en" && targetLang != "auto") {
                        if (TranslatorHelper.isCreatorOrIdentityQuery(rawPrompt, engPrompt)) {
                            val lowRaw = rawPrompt.lowercase()
                            if (targetLang.startsWith("hi") || targetLang.startsWith("bn") ||
                                lowRaw.contains("banaya") || lowRaw.contains("baniyeche") || lowRaw.contains("banieche") ||
                                lowRaw.contains("toiri") || lowRaw.contains("বানিয়ে")) {
                                formatted
                            } else {
                                TranslatorHelper.translateFromEnglishToUserLang(
                                    englishText = formatted,
                                    targetLang = targetLang,
                                    preferRomanized = isLatin
                                )
                            }
                        } else {
                            TranslatorHelper.translateFromEnglishToUserLang(
                                englishText = formatted,
                                targetLang = targetLang,
                                preferRomanized = isLatin
                            )
                        }
                    } else {
                        formatted
                    }

                    processedMessage = incoming.copy(text = finalDisplayText, model = requestedModel)
                } else {
                    processedMessage = incoming.copy(model = requestedModel)
                }
            }

            val currentList = _messages.value.toMutableList()
            val existingIndex = currentList.indexOfFirst {
                it.messageId == processedMessage.messageId || (it.requestId.isNotEmpty() && it.requestId == processedMessage.requestId && it.sender == processedMessage.sender)
            }

            if (existingIndex >= 0) {
                // Update existing message
                currentList[existingIndex] = processedMessage
            } else {
                // If it's an AI message matching our pending request or newly added
                currentList.add(processedMessage)
            }

            _messages.value = currentList
            _currentConversation.value?.let { conv ->
                prefs.saveMessages(conv.id, currentList)
            }

            // If incoming is AI response for our pending request
            if (incoming.isAi && incoming.requestId == pendingRequestId) {
                rtdbManager.cancelResponseTimeout()
                if (incoming.status == "completed" || incoming.text.isNotEmpty()) {
                    rtdbManager.setConnectionStatus(ConnectionStatus.RESPONSE_RECEIVED)
                    pendingRequestId = null
                } else if (incoming.status == "generating") {
                    rtdbManager.setConnectionStatus(ConnectionStatus.AI_GENERATING)
                }
            }
        }
    }

    fun sendMessage(text: String, overrideModel: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val conv = _currentConversation.value ?: createNewConversation()
        val reqId = UUID.randomUUID().toString()
        val userMsgId = "msg_user_${System.currentTimeMillis()}"
        val chosenModel = overrideModel ?: conv.model.ifEmpty { _settings.value.modelName }

        val userMessage = ChatMessage(
            messageId = userMsgId,
            conversationId = conv.id,
            sender = "user",
            text = trimmed,
            originalText = trimmed,
            timestamp = System.currentTimeMillis(),
            status = "sending",
            requestId = reqId,
            model = chosenModel
        )

        // If conversation is "New Chat", auto-title from first message
        if (conv.title == "New Chat" || conv.title.isEmpty()) {
            val autoTitle = if (trimmed.length > 28) trimmed.take(28) + "…" else trimmed
            renameConversation(conv.id, autoTitle)
        }

        // Record original text to permanently prevent UI translation
        userOriginalTextMap[userMsgId] = trimmed
        userOriginalTextMap[reqId] = trimmed

        // Add user message to UI immediately
        val currentList = _messages.value.toMutableList()
        currentList.add(userMessage)
        _messages.value = currentList
        prefs.saveMessages(conv.id, currentList)

        // Create pending AI placeholder for typing animation
        val aiPlaceholderId = "msg_ai_${System.currentTimeMillis() + 1}"
        val aiPlaceholder = ChatMessage(
            messageId = aiPlaceholderId,
            conversationId = conv.id,
            sender = "ai",
            text = "",
            timestamp = System.currentTimeMillis() + 1,
            status = "generating",
            requestId = reqId,
            model = chosenModel
        )

        pendingRequestId = reqId

        // Asynchronously translate to English before saving to Firebase RTDB!
        scope.launch {
            val transResult = TranslatorHelper.translateToEnglish(trimmed)
            val englishPrompt = transResult.translatedEnglish
            val detectedLang = transResult.detectedLanguage
            val isLatin = transResult.isLatinScript

            // Save request language mapping
            val langInfo = RequestLangInfo(
                rawPrompt = trimmed,
                englishPrompt = englishPrompt,
                sourceLang = detectedLang,
                isLatinScript = isLatin
            )
            requestLanguageMap[reqId] = langInfo
            lastActiveLanguageInfo = langInfo

            val messageToSend = userMessage.copy(
                text = englishPrompt, // Clean English stored in Firebase RTDB
                originalText = trimmed,
                sourceLang = detectedLang,
                status = "sent"
            )

            // Send to Firebase RTDB
            rtdbManager.sendMessage(
                message = messageToSend,
                onSuccess = {
                    // Update user message status to "sent"
                    updateUserMessageStatus(userMsgId, "sent", null)

                    // Add AI placeholder so user sees immediate modern AI thinking animation
                    val listWithAi = _messages.value.toMutableList()
                    if (listWithAi.none { it.requestId == reqId && it.isAi }) {
                        listWithAi.add(aiPlaceholder)
                        _messages.value = listWithAi
                        prefs.saveMessages(conv.id, listWithAi)
                    }

                    // Start timeout for AI response
                    rtdbManager.startResponseTimeout(_settings.value.responseTimeoutSeconds) {
                        // AI response timed out
                        handleAiResponseTimeout(reqId, aiPlaceholderId)
                    }
                },
                onError = { errorText ->
                    // Mark user message as error and allow retry
                    updateUserMessageStatus(userMsgId, "error", errorText)
                    pendingRequestId = null
                }
            )
        }
    }

    fun retryMessage(failedMessage: ChatMessage) {
        val conv = _currentConversation.value ?: return

        // Update status back to sending
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

                // Add or reset AI placeholder
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

    fun stopGeneration() {
        rtdbManager.cancelResponseTimeout()
        pendingRequestId?.let { reqId ->
            // Mark any generating AI message as stopped
            val currentList = _messages.value.toMutableList()
            val aiIdx = currentList.indexOfFirst { it.requestId == reqId && it.isAi && it.isGenerating }
            if (aiIdx >= 0) {
                val current = currentList[aiIdx]
                val updatedText = if (current.text.isEmpty()) "Generation stopped by user." else current.text
                currentList[aiIdx] = current.copy(status = "completed", text = updatedText)
                _messages.value = currentList
                _currentConversation.value?.let { conv ->
                    prefs.saveMessages(conv.id, currentList)
                }
            }
        }
        pendingRequestId = null
        rtdbManager.setConnectionStatus(ConnectionStatus.CONNECTED)
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
}
