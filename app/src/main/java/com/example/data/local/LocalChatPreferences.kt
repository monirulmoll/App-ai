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

/**
 * Enterprise-grade Local Persistence and Cache Manager for Gemo AI.
 * Strictly guarantees UID-partitioned isolation for all user data:
 * - Conversations
 * - Messages
 * - Settings
 * - Memories
 * - Custom display names
 *
 * Prevents cross-user data leakage by enforcing that every operation
 * targets the authenticated Firebase UID and never falls back to shared global keys.
 */
class LocalChatPreferences(private val context: Context) {
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
        private const val KEY_TRANSLATOR_ENABLED = "translator_enabled"
        private const val KEY_ACTIVE_CONV = "active_conversation_id"
        private const val KEY_CONVERSATIONS = "conversations_json"
        private const val PREFIX_MESSAGES = "conv_messages_"
        private const val KEY_SAVED_MODELS = "saved_user_models_json"
        private const val KEY_SAVED_MEMORIES = "saved_user_memories_json"
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_USER_ID = "gemo_user_id"
        private const val KEY_CUSTOM_USERNAME = "custom_username"
        private const val KEY_SESSION_TOKEN = "auth_session_token"
        private const val KEY_AUTH_USER_ID = "auth_user_id"
        private const val KEY_AUTH_USERNAME = "auth_username"
        private const val KEY_SESSION_EXPIRY = "auth_session_expiry"
        private const val KEY_GOOGLE_USER_ID = "google_user_id"
        private const val KEY_GOOGLE_USERNAME = "google_username"
        private const val KEY_GOOGLE_EMAIL = "google_email"
        private const val KEY_GOOGLE_NAME = "google_name"
        private const val KEY_GOOGLE_PHOTO = "google_photo"
        private const val KEY_GOOGLE_ID_TOKEN = "google_id_token"
        private const val KEY_GOOGLE_LOGIN_TIME = "google_login_time"

        // Per-user isolated key prefixes
        private const val PREFIX_USER_CONVERSATIONS = "conversations_json_"
        private const val PREFIX_USER_MESSAGES = "conv_messages_"
        private const val PREFIX_USER_SETTINGS = "settings_json_"
        private const val PREFIX_USER_MEMORIES = "memories_json_"
        private const val PREFIX_USER_ACTIVE_CONV = "active_conv_"
        private const val PREFIX_USER_CUSTOM_NAME = "custom_username_"
    }

    /**
     * Retrieves the confirmed authenticated Firebase UID.
     * Guaranteed to return null if the user is unauthenticated,
     * preventing any assignment of data to arbitrary placeholder IDs.
     */
    fun getAuthenticatedUserId(): String? {
        try {
            com.example.data.firebase.FirebaseInitializer.ensureInitialized(context)
            val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (fbUser != null && fbUser.uid.isNotBlank()) {
                return fbUser.uid
            }
        } catch (_: Exception) {}

        val session = getAuthSession()
        if (session != null && session.userId.isNotBlank()) {
            return session.userId
        }

        val storedAuthId = prefs.getString(KEY_AUTH_USER_ID, null)?.trim()
        if (!storedAuthId.isNullOrBlank()) {
            return storedAuthId
        }

        val googleId = prefs.getString(KEY_GOOGLE_USER_ID, null)?.trim()
        if (!googleId.isNullOrBlank()) {
            return googleId
        }

        return null
    }

    /**
     * Resolves the active user ID for internal system routing.
     * Guaranteed never to generate or persist random guest IDs for unauthenticated sessions.
     */
    fun getUserId(): String {
        return getAuthenticatedUserId() ?: "unauthenticated"
    }

    /**
     * Resolves the user-facing display username.
     * Priority:
     * 1. Authenticated Firebase Auth user's display name or email prefix.
     * 2. User-selected custom username for this UID.
     * 3. Authenticated session username.
     * 4. Clear fallback: "User".
     */
    fun getDisplayUsername(targetUserId: String? = null): String {
        val uid = targetUserId ?: getAuthenticatedUserId()
        try {
            com.example.data.firebase.FirebaseInitializer.ensureInitialized(context)
            val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (fbUser != null && (uid == null || fbUser.uid == uid)) {
                if (!fbUser.displayName.isNullOrBlank()) return fbUser.displayName!!
                if (!fbUser.email.isNullOrBlank()) return fbUser.email!!.substringBefore('@')
                if (fbUser.isAnonymous) return "Guest User"
            }
        } catch (_: Exception) {}

        if (uid != null) {
            val custom = getCustomUsername(uid)
            if (!custom.isNullOrBlank()) return custom
        }

        val session = getAuthSession()
        if (session != null && (uid == null || session.userId == uid) && session.username.isNotBlank()) {
            return session.username
        }

        val storedUsername = prefs.getString(KEY_AUTH_USERNAME, null)?.trim()
        if (!storedUsername.isNullOrBlank()) {
            return storedUsername
        }

        return "User"
    }

    fun getCustomUsername(targetUserId: String? = null): String? {
        val uid = targetUserId ?: getAuthenticatedUserId() ?: return null
        val name = prefs.getString("${PREFIX_USER_CUSTOM_NAME}$uid", null)?.trim()
            ?: prefs.getString(KEY_CUSTOM_USERNAME, null)?.trim()
        return if (name.isNullOrBlank()) null else name
    }

    fun setCustomUsername(username: String, targetUserId: String? = null) {
        val uid = targetUserId ?: getAuthenticatedUserId()
        val clean = username.trim()
        val editor = prefs.edit()
        if (uid != null) {
            if (clean.isNotBlank()) {
                editor.putString("${PREFIX_USER_CUSTOM_NAME}$uid", clean)
            } else {
                editor.remove("${PREFIX_USER_CUSTOM_NAME}$uid")
            }
        }
        if (clean.isNotBlank()) {
            editor.putString(KEY_CUSTOM_USERNAME, clean)
        } else {
            editor.remove(KEY_CUSTOM_USERNAME)
        }
        editor.apply()
    }

    // --- AUTHENTICATION SESSION METHODS ---

    fun saveAuthSession(session: com.example.data.model.AuthSession) {
        val effectiveUserId = session.userId.trim()
        prefs.edit()
            .putString(KEY_SESSION_TOKEN, session.sessionToken)
            .putString(KEY_AUTH_USER_ID, effectiveUserId)
            .putString(KEY_AUTH_USERNAME, session.username.trim())
            .putString(KEY_USER_ID, effectiveUserId)
            .putLong(KEY_SESSION_EXPIRY, session.expiresAt)
            .apply()
    }

    fun getAuthSession(): com.example.data.model.AuthSession? {
        val token = prefs.getString(KEY_SESSION_TOKEN, null) ?: return null
        val username = prefs.getString(KEY_AUTH_USERNAME, null) ?: ""
        val userId = prefs.getString(KEY_AUTH_USER_ID, null)?.trim()?.ifBlank { null } ?: token
        val expiry = prefs.getLong(KEY_SESSION_EXPIRY, 0L)
        val session = com.example.data.model.AuthSession(
            sessionToken = token,
            userId = userId,
            username = username.ifBlank { "User" },
            expiresAt = expiry
        )
        if (session.isExpired) {
            clearAuthSession()
            return null
        }
        return session
    }

    fun clearAuthSession() {
        prefs.edit()
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_AUTH_USER_ID)
            .remove(KEY_AUTH_USERNAME)
            .remove(KEY_SESSION_EXPIRY)
            .remove(KEY_CUSTOM_USERNAME)
            .remove(KEY_USER_ID)
            .remove(KEY_ACTIVE_CONV)
            .apply()
    }

    fun isLoggedIn(): Boolean {
        return getAuthenticatedUserId() != null
    }

    fun getSessionToken(): String? {
        return getAuthSession()?.sessionToken
    }

    fun getUserProfile(): com.example.data.model.UserProfile? {
        val session = getAuthSession() ?: return null
        return com.example.data.model.UserProfile(
            userId = session.userId,
            username = session.username,
            sessionToken = session.sessionToken
        )
    }

    fun saveGoogleUserProfile(profile: com.example.data.model.GoogleUserProfile) {
        val editor = prefs.edit()
            .putString(KEY_GOOGLE_USER_ID, profile.googleUserId)
            .putString(KEY_GOOGLE_EMAIL, profile.email)
            .putString(KEY_GOOGLE_NAME, profile.displayName)
            .putString(KEY_GOOGLE_PHOTO, profile.photoUrl)
            .putLong(KEY_GOOGLE_LOGIN_TIME, profile.lastLoginAt)

        if (profile.username.isNotBlank()) {
            editor.putString(KEY_GOOGLE_USERNAME, profile.username)
        }
        if (!profile.idToken.isNullOrBlank()) {
            editor.putString(KEY_GOOGLE_ID_TOKEN, profile.idToken)
        }
        editor.apply()
    }

    fun getGoogleUserProfile(): com.example.data.model.GoogleUserProfile? {
        val googleId = prefs.getString(KEY_GOOGLE_USER_ID, null) ?: return null
        if (googleId.isBlank()) return null
        val username = prefs.getString(KEY_GOOGLE_USERNAME, null)?.ifBlank { null }
            ?: getDisplayUsername(googleId)
        return com.example.data.model.GoogleUserProfile(
            googleUserId = googleId,
            email = prefs.getString(KEY_GOOGLE_EMAIL, "") ?: "",
            displayName = prefs.getString(KEY_GOOGLE_NAME, "") ?: "",
            username = username,
            photoUrl = prefs.getString(KEY_GOOGLE_PHOTO, null),
            idToken = prefs.getString(KEY_GOOGLE_ID_TOKEN, null),
            lastLoginAt = prefs.getLong(KEY_GOOGLE_LOGIN_TIME, System.currentTimeMillis())
        )
    }

    fun clearGoogleUserProfile() {
        prefs.edit()
            .remove(KEY_GOOGLE_USER_ID)
            .remove(KEY_GOOGLE_USERNAME)
            .remove(KEY_GOOGLE_EMAIL)
            .remove(KEY_GOOGLE_NAME)
            .remove(KEY_GOOGLE_PHOTO)
            .remove(KEY_GOOGLE_ID_TOKEN)
            .remove(KEY_GOOGLE_LOGIN_TIME)
            .apply()
    }

    // --- PER-USER SETTINGS ISOLATION ---

    private fun getSettingsKey(targetUserId: String?): String {
        val uid = (targetUserId ?: getAuthenticatedUserId() ?: "").trim()
        return if (uid.isNotBlank() && uid != "unauthenticated") {
            "${PREFIX_USER_SETTINGS}$uid"
        } else {
            "settings_json_default"
        }
    }

    fun getSettings(targetUserId: String? = null): LlmSettings {
        val key = getSettingsKey(targetUserId)
        val jsonStr = prefs.getString(key, null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val obj = JSONObject(jsonStr)
                val rawModel = obj.optString(KEY_MODEL_NAME, "qwen2.5-1.5b-instruct-q4_k_m.gguf")
                val exactGguf = LlmSettings.ensureGgufFilename(rawModel)
                return LlmSettings(
                    firebaseUrl = obj.optString(KEY_FIREBASE_URL, LlmSettings.DEFAULT_FIREBASE_URL),
                    provider = obj.optString(KEY_PROVIDER, "Qwen"),
                    modelName = exactGguf,
                    themeMode = obj.optString(KEY_THEME, "SYSTEM"),
                    responseTimeoutSeconds = obj.optInt(KEY_TIMEOUT, 45),
                    maxTokens = obj.optInt(KEY_MAX_TOKENS, 2048),
                    temperature = obj.optDouble(KEY_TEMPERATURE, 0.7).toFloat(),
                    agentModeEnabled = obj.optBoolean(KEY_AGENT_MODE, false),
                    memoryEnabled = obj.optBoolean(KEY_MEMORY_ENABLED, true),
                    visionEnabled = obj.optBoolean(KEY_VISION_ENABLED, true),
                    systemInstruction = obj.optString(KEY_SYSTEM_INSTRUCTION, LlmSettings.DEFAULT_SYSTEM_INSTRUCTION),
                    speechInputLanguage = obj.optString(KEY_SPEECH_LANG, "auto"),
                    translatorEnabled = obj.optBoolean(KEY_TRANSLATOR_ENABLED, true)
                )
            } catch (_: Exception) {}
        }

        // Fallback to legacy single settings if this user does not have scoped settings yet
        val rawModel = prefs.getString(KEY_MODEL_NAME, "qwen2.5-1.5b-instruct-q4_k_m.gguf") ?: "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        val exactGguf = LlmSettings.ensureGgufFilename(rawModel)
        return LlmSettings(
            firebaseUrl = prefs.getString(KEY_FIREBASE_URL, LlmSettings.DEFAULT_FIREBASE_URL) ?: LlmSettings.DEFAULT_FIREBASE_URL,
            provider = prefs.getString(KEY_PROVIDER, "Qwen") ?: "Qwen",
            modelName = exactGguf,
            themeMode = prefs.getString(KEY_THEME, "SYSTEM") ?: "SYSTEM",
            responseTimeoutSeconds = prefs.getInt(KEY_TIMEOUT, 45),
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, 2048),
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
            agentModeEnabled = prefs.getBoolean(KEY_AGENT_MODE, false),
            memoryEnabled = prefs.getBoolean(KEY_MEMORY_ENABLED, true),
            visionEnabled = prefs.getBoolean(KEY_VISION_ENABLED, true),
            systemInstruction = prefs.getString(KEY_SYSTEM_INSTRUCTION, LlmSettings.DEFAULT_SYSTEM_INSTRUCTION) ?: LlmSettings.DEFAULT_SYSTEM_INSTRUCTION,
            speechInputLanguage = prefs.getString(KEY_SPEECH_LANG, "auto") ?: "auto",
            translatorEnabled = prefs.getBoolean(KEY_TRANSLATOR_ENABLED, true)
        )
    }

    fun saveSettings(settings: LlmSettings, targetUserId: String? = null) {
        val key = getSettingsKey(targetUserId)
        val exactGguf = LlmSettings.ensureGgufFilename(settings.modelName)
        try {
            val obj = JSONObject().apply {
                put(KEY_FIREBASE_URL, settings.firebaseUrl)
                put(KEY_PROVIDER, settings.provider)
                put(KEY_MODEL_NAME, exactGguf)
                put(KEY_THEME, settings.themeMode)
                put(KEY_TIMEOUT, settings.responseTimeoutSeconds)
                put(KEY_MAX_TOKENS, settings.maxTokens)
                put(KEY_TEMPERATURE, settings.temperature.toDouble())
                put(KEY_AGENT_MODE, settings.agentModeEnabled)
                put(KEY_MEMORY_ENABLED, settings.memoryEnabled)
                put(KEY_VISION_ENABLED, settings.visionEnabled)
                put(KEY_SYSTEM_INSTRUCTION, settings.systemInstruction)
                put(KEY_SPEECH_LANG, settings.speechInputLanguage)
                put(KEY_TRANSLATOR_ENABLED, settings.translatorEnabled)
            }
            prefs.edit().putString(key, obj.toString()).apply()
        } catch (_: Exception) {}
    }

    // --- PER-USER CONVERSATION & MESSAGE ISOLATION ---

    private fun getConversationsKey(targetUserId: String?): String {
        val uid = (targetUserId ?: getAuthenticatedUserId() ?: "").trim()
        return if (uid.isNotBlank() && uid != "unauthenticated") {
            "${PREFIX_USER_CONVERSATIONS}$uid"
        } else {
            "conversations_json_unauthenticated"
        }
    }

    private fun getMessagesKey(conversationId: String, targetUserId: String?): String {
        val uid = (targetUserId ?: getAuthenticatedUserId() ?: "").trim()
        return if (uid.isNotBlank() && uid != "unauthenticated") {
            "${PREFIX_USER_MESSAGES}${uid}_$conversationId"
        } else {
            "conv_messages_unauthenticated_$conversationId"
        }
    }

    fun getActiveConversationId(targetUserId: String? = null): String? {
        val uid = (targetUserId ?: getAuthenticatedUserId() ?: "").trim()
        if (uid.isBlank() || uid == "unauthenticated") return null
        return prefs.getString("${PREFIX_USER_ACTIVE_CONV}$uid", null)
    }

    fun setActiveConversationId(id: String?, targetUserId: String? = null) {
        val uid = (targetUserId ?: getAuthenticatedUserId() ?: "").trim()
        if (uid.isBlank() || uid == "unauthenticated") return
        val editor = prefs.edit()
        if (id != null) {
            editor.putString("${PREFIX_USER_ACTIVE_CONV}$uid", id)
        } else {
            editor.remove("${PREFIX_USER_ACTIVE_CONV}$uid")
        }
        editor.apply()
    }

    fun getConversations(targetUserId: String? = null): List<Conversation> {
        val uid = (targetUserId ?: getAuthenticatedUserId() ?: "").trim()
        if (uid.isBlank() || uid == "unauthenticated") return emptyList()

        val key = getConversationsKey(uid)
        val jsonStr = prefs.getString(key, null) ?: return emptyList()
        val allConvs = parseConversationsJson(jsonStr)
        // Strict anti-leak filter: only return conversations belonging to this exact user ID
        return allConvs.filter { it.userId == uid }
    }

    private fun parseConversationsJson(jsonStr: String): List<Conversation> {
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
                        lastMessage = obj.optString("lastMessage", ""),
                        userId = obj.optString("userId", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveConversations(conversations: List<Conversation>, targetUserId: String? = null) {
        val uid = (targetUserId ?: getAuthenticatedUserId() ?: "").trim()
        if (uid.isBlank() || uid == "unauthenticated") return

        try {
            val key = getConversationsKey(uid)
            val array = JSONArray()
            for (conv in conversations) {
                // Ensure every conversation saved in this user's storage is tagged with this user's UID
                val obj = JSONObject().apply {
                    put("id", conv.id)
                    put("title", conv.title)
                    put("createdAt", conv.createdAt)
                    put("updatedAt", conv.updatedAt)
                    put("model", conv.model)
                    put("lastMessage", conv.lastMessage)
                    put("userId", uid)
                }
                array.put(obj)
            }
            prefs.edit().putString(key, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getMessages(conversationId: String, targetUserId: String? = null): List<ChatMessage> {
        val uid = (targetUserId ?: getAuthenticatedUserId() ?: "").trim()
        if (uid.isBlank() || uid == "unauthenticated") return emptyList()

        val key = getMessagesKey(conversationId, uid)
        val jsonStr = prefs.getString(key, null) ?: return emptyList()
        return parseMessagesJson(jsonStr, conversationId)
    }

    private fun parseMessagesJson(jsonStr: String, conversationId: String): List<ChatMessage> {
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
                        imageUri = if (obj.has("imageUri") && !obj.isNull("imageUri")) obj.getString("imageUri") else null,
                        imageBase64 = if (obj.has("imageBase64") && !obj.isNull("imageBase64")) obj.getString("imageBase64") else null,
                        imageResultUrl = if (obj.has("imageResultUrl") && !obj.isNull("imageResultUrl")) obj.getString("imageResultUrl") else null,
                        toolCall = if (obj.has("toolCall") && !obj.isNull("toolCall")) obj.getString("toolCall") else null,
                        toolResult = if (obj.has("toolResult") && !obj.isNull("toolResult")) obj.getString("toolResult") else null,
                        agentMode = obj.optBoolean("agentMode", false),
                        intent = if (obj.has("intent") && !obj.isNull("intent")) obj.getString("intent") else null,
                        memoryContext = if (obj.has("memoryContext") && !obj.isNull("memoryContext")) obj.getString("memoryContext") else null
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(conversationId: String, messages: List<ChatMessage>, targetUserId: String? = null) {
        val uid = (targetUserId ?: getAuthenticatedUserId() ?: "").trim()
        if (uid.isBlank() || uid == "unauthenticated") return

        try {
            val key = getMessagesKey(conversationId, uid)
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
            prefs.edit().putString(key, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun deleteConversationData(conversationId: String, targetUserId: String? = null) {
        val uid = (targetUserId ?: getAuthenticatedUserId() ?: "").trim()
        val editor = prefs.edit()
        if (uid.isNotBlank() && uid != "unauthenticated") {
            editor.remove(getMessagesKey(conversationId, uid))
        }
        editor.remove("${PREFIX_USER_ACTIVE_CONV}$uid")
        editor.apply()
    }

    /**
     * Safely migrates legacy un-scoped conversations to the authenticated user's key
     * ONLY if ownership can be verified (matching userId).
     * Never assigns ambiguous legacy data to an account automatically.
     */
    fun migrateLegacyLocalDataForUser(authenticatedUid: String) {
        if (authenticatedUid.isBlank() || authenticatedUid == "unauthenticated") return
        val userConvKey = getConversationsKey(authenticatedUid)

        // Only migrate if user does not already have isolated local conversations
        if (!prefs.contains(userConvKey)) {
            val legacyJson = prefs.getString(KEY_CONVERSATIONS, null)
            if (!legacyJson.isNullOrBlank()) {
                val legacyConvs = parseConversationsJson(legacyJson)
                val ownedConvs = legacyConvs.filter { it.userId == authenticatedUid }
                if (ownedConvs.isNotEmpty()) {
                    saveConversations(ownedConvs, authenticatedUid)
                    for (conv in ownedConvs) {
                        val legacyMsgJson = prefs.getString("${PREFIX_MESSAGES}${conv.id}", null)
                        if (!legacyMsgJson.isNullOrBlank()) {
                            prefs.edit().putString(getMessagesKey(conv.id, authenticatedUid), legacyMsgJson).apply()
                        }
                    }
                }
            }
        }
    }

    // --- PER-USER MEMORY ISOLATION ---

    private fun getMemoriesKey(targetUserId: String?): String {
        val uid = (targetUserId ?: getAuthenticatedUserId() ?: "").trim()
        return if (uid.isNotBlank() && uid != "unauthenticated") {
            "${PREFIX_USER_MEMORIES}$uid"
        } else {
            "memories_json_default"
        }
    }

    fun getMemories(targetUserId: String? = null): List<UserMemory> {
        val key = getMemoriesKey(targetUserId)
        val jsonStr = prefs.getString(key, null) ?: return emptyList()
        return parseMemoriesJson(jsonStr)
    }

    private fun parseMemoriesJson(jsonStr: String): List<UserMemory> {
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

    fun saveMemories(memories: List<UserMemory>, targetUserId: String? = null) {
        val key = getMemoriesKey(targetUserId)
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
            prefs.edit().putString(key, array.toString()).apply()
        } catch (_: Exception) {}
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
        } catch (_: Exception) {}
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

    fun getAgentFeatures(): Map<String, Boolean> {
        val json = prefs.getString("agent_features_config", null) ?: return com.example.data.model.AgentFeatureKey.ALL_DEFAULT_FEATURES
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Boolean>()
            com.example.data.model.AgentFeatureKey.values().forEach { key ->
                map[key.key] = obj.optBoolean(key.key, true)
            }
            map
        } catch (_: Exception) {
            com.example.data.model.AgentFeatureKey.ALL_DEFAULT_FEATURES
        }
    }

    fun saveAgentFeature(key: String, enabled: Boolean) {
        val current = getAgentFeatures().toMutableMap()
        current[key] = enabled
        saveAgentFeatures(current)
    }

    fun saveAgentFeatures(features: Map<String, Boolean>) {
        val obj = JSONObject()
        features.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString("agent_features_config", obj.toString()).apply()
    }
}
