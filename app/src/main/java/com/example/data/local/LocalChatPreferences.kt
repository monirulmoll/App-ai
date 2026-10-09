package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.LlmModelOption
import com.example.data.model.LlmSettings
import com.example.data.model.UserMemory
import org.json.JSONArray
import org.json.JSONObject

class LocalChatPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "gemo_ai_prefs"
        private const val KEY_FIREBASE_URL = "firebase_url"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_TIMEOUT = "timeout_seconds"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_AGENT_MODE = "agent_mode_enabled"
        private const val KEY_MEMORY_ENABLED = "memory_enabled"
        private const val KEY_VISION_ENABLED = "vision_enabled"
        private const val KEY_SYSTEM_INSTRUCTION = "system_instruction"
        private const val KEY_SPEECH_LANG = "speech_input_language"
        private const val KEY_ACTIVE_CONV = "active_conversation_id"
        private const val KEY_CONVERSATIONS = "conversations_json"
        private const val PREFIX_MESSAGES = "conv_messages_"
        private const val KEY_SAVED_MODELS = "saved_user_models_json"
        private const val KEY_SAVED_MEMORIES = "saved_user_memories_json"
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_FONT_SCALE = "font_scale"
    }

    fun getSettings(): LlmSettings {
        val rawModel = prefs.getString(KEY_MODEL_NAME, "qwen2.5-1.5b-instruct-q4_k_m.gguf") ?: "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        val exactGguf = LlmSettings.ensureGgufFilename(rawModel)
        return LlmSettings(
            firebaseUrl = prefs.getString(KEY_FIREBASE_URL, LlmSettings.DEFAULT_FIREBASE_URL)
                ?: LlmSettings.DEFAULT_FIREBASE_URL,
            provider = prefs.getString(KEY_PROVIDER, "Qwen") ?: "Qwen",
            modelName = exactGguf,
            themeMode = prefs.getString(KEY_THEME, "SYSTEM") ?: "SYSTEM",
            responseTimeoutSeconds = prefs.getInt(KEY_TIMEOUT, 45),
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, 2048),
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
            agentModeEnabled = prefs.getBoolean(KEY_AGENT_MODE, false),
            memoryEnabled = prefs.getBoolean(KEY_MEMORY_ENABLED, true),
            visionEnabled = prefs.getBoolean(KEY_VISION_ENABLED, true),
            systemInstruction = prefs.getString(KEY_SYSTEM_INSTRUCTION, LlmSettings.DEFAULT_SYSTEM_INSTRUCTION)
                ?: LlmSettings.DEFAULT_SYSTEM_INSTRUCTION,
            speechInputLanguage = prefs.getString(KEY_SPEECH_LANG, "auto") ?: "auto"
        )
    }

    fun saveSettings(settings: LlmSettings) {
        val exactGguf = LlmSettings.ensureGgufFilename(settings.modelName)
        prefs.edit()
            .putString(KEY_FIREBASE_URL, settings.firebaseUrl)
            .putString(KEY_PROVIDER, settings.provider)
            .putString(KEY_MODEL_NAME, exactGguf)
            .putString(KEY_THEME, settings.themeMode)
            .putInt(KEY_TIMEOUT, settings.responseTimeoutSeconds)
            .putInt(KEY_MAX_TOKENS, settings.maxTokens)
            .putFloat(KEY_TEMPERATURE, settings.temperature)
            .putBoolean(KEY_AGENT_MODE, settings.agentModeEnabled)
            .putBoolean(KEY_MEMORY_ENABLED, settings.memoryEnabled)
            .putBoolean(KEY_VISION_ENABLED, settings.visionEnabled)
            .putString(KEY_SYSTEM_INSTRUCTION, settings.systemInstruction)
            .putString(KEY_SPEECH_LANG, settings.speechInputLanguage)
            .apply()
    }

    fun getActiveConversationId(): String? {
        return prefs.getString(KEY_ACTIVE_CONV, null)
    }

    fun setActiveConversationId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_CONV, id).apply()
    }

    fun getConversations(): List<Conversation> {
        val jsonStr = prefs.getString(KEY_CONVERSATIONS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<Conversation>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Conversation(
                        id = obj.getString("id"),
                        title = obj.optString("title", "Chat"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        model = LlmSettings.ensureGgufFilename(obj.optString("model", "qwen2.5-1.5b-instruct-q4_k_m.gguf")),
                        lastMessage = obj.optString("lastMessage", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveConversations(conversations: List<Conversation>) {
        try {
            val array = JSONArray()
            for (conv in conversations) {
                val obj = JSONObject().apply {
                    put("id", conv.id)
                    put("title", conv.title)
                    put("createdAt", conv.createdAt)
                    put("updatedAt", conv.updatedAt)
                    put("model", conv.model)
                    put("lastMessage", conv.lastMessage)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_CONVERSATIONS, array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun getMessages(conversationId: String): List<ChatMessage> {
        val jsonStr = prefs.getString(PREFIX_MESSAGES + conversationId, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ChatMessage(
                        messageId = obj.getString("messageId"),
                        conversationId = obj.optString("conversationId", conversationId),
                        sender = obj.optString("sender", "user"),
                        text = obj.optString("text", ""),
                        originalText = if (obj.has("originalText") && !obj.isNull("originalText")) obj.getString("originalText") else obj.optString("text", ""),
                        sourceLang = obj.optString("sourceLang", "en"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        status = obj.optString("status", "sent"),
                        requestId = obj.optString("requestId", ""),
                        model = obj.optString("model", ""),
                        errorMessage = if (obj.has("errorMessage") && !obj.isNull("errorMessage")) obj.getString("errorMessage") else null,
                        imageUri = obj.optString("imageUri", null),
                        imageBase64 = obj.optString("imageBase64", null),
                        imageResultUrl = obj.optString("imageResultUrl", null),
                        toolCall = obj.optString("toolCall", null),
                        toolResult = obj.optString("toolResult", null),
                        agentMode = obj.optBoolean("agentMode", false),
                        intent = obj.optString("intent", null),
                        memoryContext = obj.optString("memoryContext", null)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(conversationId: String, messages: List<ChatMessage>) {
        try {
            val array = JSONArray()
            for (msg in messages) {
                val obj = JSONObject().apply {
                    put("messageId", msg.messageId)
                    put("conversationId", msg.conversationId)
                    put("sender", msg.sender)
                    put("text", msg.text)
                    if (msg.originalText != null) put("originalText", msg.originalText)
                    if (msg.sourceLang != null) put("sourceLang", msg.sourceLang)
                    put("timestamp", msg.timestamp)
                    put("status", msg.status)
                    put("requestId", msg.requestId)
                    put("model", msg.model)
                    if (msg.errorMessage != null) put("errorMessage", msg.errorMessage)
                    if (msg.imageUri != null) put("imageUri", msg.imageUri)
                    if (msg.imageBase64 != null) put("imageBase64", msg.imageBase64)
                    if (msg.imageResultUrl != null) put("imageResultUrl", msg.imageResultUrl)
                    if (msg.toolCall != null) put("toolCall", msg.toolCall)
                    if (msg.toolResult != null) put("toolResult", msg.toolResult)
                    put("agentMode", msg.agentMode)
                    if (msg.intent != null) put("intent", msg.intent)
                    if (msg.memoryContext != null) put("memoryContext", msg.memoryContext)
                }
                array.put(obj)
            }
            prefs.edit().putString(PREFIX_MESSAGES + conversationId, array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun deleteConversationData(conversationId: String) {
        prefs.edit().remove(PREFIX_MESSAGES + conversationId).apply()
    }

    fun getSavedModels(): List<LlmModelOption> {
        val jsonStr = prefs.getString(KEY_SAVED_MODELS, null) ?: return LlmSettings.DEFAULT_POPULAR_MODELS
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<LlmModelOption>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawFilename = obj.optString("ggufFilename", "")
                if (rawFilename.isNotBlank()) {
                    val exactGguf = LlmSettings.ensureGgufFilename(rawFilename)
                    val modelName = obj.optString("modelName", LlmSettings.getDisplayName(exactGguf))
                    val provider = obj.optString("provider", LlmSettings.detectProvider(exactGguf))
                    val desc = obj.optString("description", "")
                    val sizeHint = obj.optString("sizeHint", "")
                    list.add(
                        LlmModelOption(
                            provider = provider,
                            modelName = modelName,
                            ggufFilename = exactGguf,
                            description = desc,
                            sizeHint = sizeHint
                        )
                    )
                }
            }
            if (list.isEmpty()) LlmSettings.DEFAULT_POPULAR_MODELS else list
        } catch (_: Exception) {
            LlmSettings.DEFAULT_POPULAR_MODELS
        }
    }

    fun saveSavedModels(models: List<LlmModelOption>) {
        try {
            val array = JSONArray()
            for (m in models) {
                val obj = JSONObject().apply {
                    put("provider", m.provider)
                    put("modelName", m.modelName)
                    put("ggufFilename", m.ggufFilename)
                    put("description", m.description)
                    put("sizeHint", m.sizeHint)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_SAVED_MODELS, array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun getMemories(): List<UserMemory> {
        val jsonStr = prefs.getString(KEY_SAVED_MEMORIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<UserMemory>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    UserMemory(
                        id = obj.getString("id"),
                        content = obj.getString("content"),
                        category = obj.optString("category", "general"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMemories(memories: List<UserMemory>) {
        try {
            val array = JSONArray()
            for (mem in memories) {
                val obj = JSONObject().apply {
                    put("id", mem.id)
                    put("content", mem.content)
                    put("category", mem.category)
                    put("createdAt", mem.createdAt)
                    put("isEnabled", mem.isEnabled)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_SAVED_MEMORIES, array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
    }

    fun getAccentColor(): String {
        return prefs.getString(KEY_ACCENT_COLOR, "BLUE") ?: "BLUE"
    }

    fun setAccentColor(colorName: String) {
        prefs.edit().putString(KEY_ACCENT_COLOR, colorName).apply()
    }

    fun getFontScale(): Float {
        return prefs.getFloat(KEY_FONT_SCALE, 1.0f)
    }

    fun setFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT_SCALE, scale).apply()
    }
}
