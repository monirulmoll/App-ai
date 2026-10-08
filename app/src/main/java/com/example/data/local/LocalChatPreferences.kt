package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.LlmModelOption
import com.example.data.model.LlmSettings
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
        private const val KEY_ACTIVE_CONV = "active_conversation_id"
        private const val KEY_CONVERSATIONS = "conversations_json"
        private const val PREFIX_MESSAGES = "conv_messages_"
        private const val KEY_SAVED_MODELS = "saved_user_models_json"
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
            responseTimeoutSeconds = prefs.getInt(KEY_TIMEOUT, 45)
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
                        errorMessage = if (obj.has("errorMessage") && !obj.isNull("errorMessage")) obj.getString("errorMessage") else null
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
                    if (msg.originalText != null) {
                        put("originalText", msg.originalText)
                    }
                    if (msg.sourceLang != null) {
                        put("sourceLang", msg.sourceLang)
                    }
                    put("timestamp", msg.timestamp)
                    put("status", msg.status)
                    put("requestId", msg.requestId)
                    put("model", msg.model)
                    if (msg.errorMessage != null) {
                        put("errorMessage", msg.errorMessage)
                    }
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
        val jsonStr = prefs.getString(KEY_SAVED_MODELS, null) ?: return emptyList()
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
                    list.add(
                        LlmModelOption(
                            provider = provider,
                            modelName = modelName,
                            ggufFilename = exactGguf,
                            description = desc
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
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
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_SAVED_MODELS, array.toString()).apply()
        } catch (_: Exception) {
        }
    }
}
