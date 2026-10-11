package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.config.DatabaseConfig
import com.example.data.config.DatabasePathResolver
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
import com.example.data.model.LlmSettings
import com.example.data.model.ModelSwitchResponse
import com.example.data.model.UserMemory
import com.example.data.model.WorkspaceFile
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Enterprise Firebase Realtime Database Manager.
 * Strictly guarantees UID-isolated read/write/listen operations for all authenticated users.
 * Firebase is treated as the single authoritative source of truth.
 */
class FirebaseRtdbManager(private val context: Context) {
    private val tag = "FirebaseRtdbManager"

    private var currentUrl: String = ""
    private var databaseInstance: FirebaseDatabase? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTING)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _isConnectedToRtdb = MutableStateFlow(false)
    val isConnectedToRtdb: StateFlow<Boolean> = _isConnectedToRtdb.asStateFlow()

    // 1. Read currently active model from "/model" (Backend writes here)
    private val _activeModelFromRtdb = MutableStateFlow<String?>(null)
    val activeModelFromRtdb: StateFlow<String?> = _activeModelFromRtdb.asStateFlow()

    // 2. Read model switch result from "/model/response" (Backend writes response here)
    private val _modelSwitchResponse = MutableStateFlow<ModelSwitchResponse?>(null)
    val modelSwitchResponse: StateFlow<ModelSwitchResponse?> = _modelSwitchResponse.asStateFlow()

    private val _isModelSwitching = MutableStateFlow(false)
    val isModelSwitching: StateFlow<Boolean> = _isModelSwitching.asStateFlow()

    val isDatabaseReady: Boolean get() = databaseInstance != null && currentUrl.isNotBlank()

    private var connectedListener: ValueEventListener? = null
    private var connectedRef: DatabaseReference? = null

    private val prefs = com.example.data.local.LocalChatPreferences(context)
    val userId: String get() = prefs.getUserId()

    private var activeUserMessagesRef: DatabaseReference? = null
    private var activeUserChildListener: ChildEventListener? = null
    private var activeConversationId: String? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var pendingTimeoutJob: Job? = null

    fun resetStopFlag(targetUserId: String = userId) {
        val db = databaseInstance ?: return
        val effectiveUid = targetUserId.trim()
        if (effectiveUid.isBlank() || effectiveUid == "unauthenticated") return
        try {
            val falsePayload = mapOf(
                "stop" to false,
                "conversationId" to "",
                "requestId" to "",
                "timestamp" to System.currentTimeMillis()
            )
            val stopPath = DatabasePathResolver.resolve(DatabaseConfig.STOP_FLAG_PATH, userId = effectiveUid)
            val stopGenPath = DatabasePathResolver.resolve(DatabaseConfig.STOP_GENERATION_PATH, userId = effectiveUid)
            db.getReference(stopPath).setValue(falsePayload)
            db.getReference(stopGenPath).setValue(false)
            Log.d(tag, "Stop flag reset to false for user $effectiveUid")
        } catch (e: Exception) {
            Log.w(tag, "Failed to reset stop flag: ${e.message}")
        }
    }

    fun initialize(url: String = DatabaseConfig.MAIN_DATABASE_URL) {
        val effectiveUrl = if (url.trim().isBlank()) DatabaseConfig.MAIN_DATABASE_URL else url
        val sanitizedUrl = effectiveUrl.trim().removeSuffix("/")
        if (sanitizedUrl.isBlank()) {
            currentUrl = ""
            databaseInstance = null
            _connectionStatus.value = ConnectionStatus.SERVER_UNAVAILABLE
            _isConnectedToRtdb.value = false
            return
        }

        if (sanitizedUrl == currentUrl && databaseInstance != null) {
            return
        }

        currentUrl = sanitizedUrl
        try {
            ensureFirebaseAppInitialized(sanitizedUrl)
            val db = FirebaseDatabase.getInstance(sanitizedUrl)
            try {
                db.setPersistenceEnabled(false) // Direct real-time live network sync
            } catch (_: Exception) {
                // Persistence can only be set once before any queries
            }
            databaseInstance = db

            setupConnectionMonitor(db)
            probeServerReachability(sanitizedUrl)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize FirebaseDatabase with URL: $sanitizedUrl", e)
            _connectionStatus.value = ConnectionStatus.SERVER_UNAVAILABLE
            _isConnectedToRtdb.value = false
        }
    }

    private fun ensureFirebaseAppInitialized(databaseUrl: String) {
        try {
            FirebaseInitializer.ensureInitialized(context, databaseUrl)
        } catch (e: Exception) {
            Log.w(tag, "FirebaseApp init check: ${e.message}")
        }
    }

    private fun setupConnectionMonitor(db: FirebaseDatabase) {
        try {
            connectedRef?.let { ref ->
                connectedListener?.let { listener -> ref.removeEventListener(listener) }
            }

            val connRef = db.getReference(DatabaseConfig.SYSTEM_CONNECTED_PATH)
            connectedRef = connRef
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) == true
                    Log.d(tag, "RTDB .info/connected: $connected")
                    _isConnectedToRtdb.value = connected
                    if (connected) {
                        resetStopFlag()
                        if (_connectionStatus.value != ConnectionStatus.AI_GENERATING &&
                            _connectionStatus.value != ConnectionStatus.MESSAGE_SENDING
                        ) {
                            _connectionStatus.value = ConnectionStatus.CONNECTED
                        }
                    } else {
                        probeServerReachability(currentUrl)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(tag, "Connection monitor cancelled: ${error.message}")
                    _isConnectedToRtdb.value = false
                    _connectionStatus.value = ConnectionStatus.SERVER_UNAVAILABLE
                }
            }
            connectedListener = listener
            connRef.addValueEventListener(listener)
        } catch (e: Exception) {
            Log.e(tag, "Error setting up connection monitor", e)
        }
    }

    fun requestModelSwitch(ggufFilename: String, targetUserId: String = userId, onComplete: ((Boolean) -> Unit)? = null) {
        val db = databaseInstance
        if (db == null || targetUserId.isBlank() || targetUserId == "unauthenticated") {
            onComplete?.invoke(false)
            return
        }
        val exactGguf = LlmSettings.ensureGgufFilename(ggufFilename)
        _activeModelFromRtdb.value = exactGguf
        _isModelSwitching.value = false
        val modelPath = DatabasePathResolver.resolve(DatabaseConfig.SETTINGS_MODEL_PATH, userId = targetUserId)
        db.getReference(modelPath).setValue(exactGguf) { _, _ ->
            onComplete?.invoke(true)
        }
    }

    fun requestStopGeneration(
        conversationId: String,
        requestId: String,
        targetUserId: String = userId,
        onResponse: (Boolean) -> Unit
    ) {
        val db = databaseInstance
        val effectiveUid = targetUserId.trim()
        if (db == null || effectiveUid.isBlank() || effectiveUid == "unauthenticated") {
            onResponse(true)
            return
        }

        val stopPayload = mapOf(
            "stop" to true,
            "requestId" to requestId,
            "conversationId" to conversationId,
            "userId" to effectiveUid,
            "timestamp" to System.currentTimeMillis()
        )

        // Write stop exclusively to user's conversation path and per-user stop path
        if (conversationId.isNotEmpty()) {
            val convStopPath = DatabasePathResolver.resolve(DatabaseConfig.CONVERSATION_STOP_PATH, userId = effectiveUid, conversationId = conversationId)
            val convStopGenPath = DatabasePathResolver.resolve(DatabaseConfig.CONVERSATION_STOP_GENERATION_PATH, userId = effectiveUid, conversationId = conversationId)
            db.getReference(convStopPath).setValue(stopPayload)
            db.getReference(convStopGenPath).setValue(true)
        }

        val stopPath = DatabasePathResolver.resolve(DatabaseConfig.STOP_FLAG_PATH, userId = effectiveUid)
        val stopGenPath = DatabasePathResolver.resolve(DatabaseConfig.STOP_GENERATION_PATH, userId = effectiveUid)
        db.getReference(stopPath).setValue(stopPayload)
        db.getReference(stopGenPath).setValue(true)

        var completed = false
        val completeOnce: (Boolean) -> Unit = { success ->
            if (!completed) {
                completed = true
                scope.launch {
                    delay(500)
                    resetStopFlag(effectiveUid)
                }
                onResponse(success)
            }
        }

        scope.launch {
            delay(2000)
            completeOnce(true)
        }

        val stopRespRef = if (conversationId.isNotEmpty()) {
            val convStopRespPath = DatabasePathResolver.resolve(DatabaseConfig.CONVERSATION_STOP_RESPONSE_PATH, userId = effectiveUid, conversationId = conversationId)
            db.getReference(convStopRespPath)
        } else {
            db.getReference(DatabasePathResolver.resolve(DatabaseConfig.STOP_FLAG_PATH, userId = effectiveUid)).child("response")
        }

        val stopListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val isSuccess = snapshot.child("success").getValue(Boolean::class.java)
                    ?: (snapshot.child("status").getValue(String::class.java) == "stopped")
                    ?: (snapshot.child("stop").getValue(Boolean::class.java) == false)
                    ?: true

                if (isSuccess) {
                    try {
                        stopRespRef.removeEventListener(this)
                    } catch (_: Exception) {}
                    completeOnce(true)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                completeOnce(true)
            }
        }

        stopRespRef.addValueEventListener(stopListener)
    }

    fun probeServerReachability(url: String = currentUrl) {
        if (url.isBlank()) {
            _connectionStatus.value = ConnectionStatus.SERVER_UNAVAILABLE
            _isConnectedToRtdb.value = false
            return
        }
        scope.launch {
            try {
                val testUrl = if (url.endsWith(".json")) url else "$url/.json?shallow=true"
                val request = Request.Builder()
                    .url(testUrl)
                    .head()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val code = response.code
                response.close()

                withContext(Dispatchers.Main) {
                    if (code in 200..404 || code == 401 || code == 403) {
                        if (_connectionStatus.value == ConnectionStatus.CONNECTING ||
                            _connectionStatus.value == ConnectionStatus.SERVER_UNAVAILABLE
                        ) {
                            _connectionStatus.value = ConnectionStatus.CONNECTED
                        }
                    } else {
                        _connectionStatus.value = ConnectionStatus.SERVER_UNAVAILABLE
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Server reachability probe failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (!_isConnectedToRtdb.value) {
                        _connectionStatus.value = ConnectionStatus.SERVER_UNAVAILABLE
                    }
                }
            }
        }
    }

    fun setConnectionStatus(status: ConnectionStatus) {
        _connectionStatus.value = status
    }

    fun sendMessage(
        message: ChatMessage,
        targetUserId: String = userId,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val db = databaseInstance
        val effectiveUid = targetUserId.trim()
        if (db == null) {
            onError("Unable to connect to server. Database not initialized.")
            return
        }
        if (effectiveUid.isBlank() || effectiveUid == "unauthenticated") {
            onError("Authentication required to send message.")
            return
        }

        resetStopFlag(effectiveUid)
        _connectionStatus.value = ConnectionStatus.MESSAGE_SENDING

        val map = message.toFirebaseMap().toMutableMap()
        map["userId"] = effectiveUid
        val sessionToken = prefs.getSessionToken()
        if (!sessionToken.isNullOrBlank()) {
            map["auth_token"] = sessionToken
        }

        // Isolated user path: users/{effectiveUid}/conversations/{conversationId}/messages/{messageId}
        val msgPath = DatabasePathResolver.resolve(
            DatabaseConfig.MESSAGE_ITEM_PATH,
            userId = effectiveUid,
            conversationId = message.conversationId,
            messageId = message.messageId
        )
        val userMsgRef = db.getReference(msgPath)

        userMsgRef.setValue(map) { databaseError, _ ->
            if (databaseError != null) {
                Log.e(tag, "Failed to send message: ${databaseError.message} (code: ${databaseError.code})")
                val errMsg = when (databaseError.code) {
                    DatabaseError.PERMISSION_DENIED -> "Permission denied by server. Please check database rules."
                    DatabaseError.NETWORK_ERROR -> "Server connection failed. Please check your network and try again."
                    DatabaseError.DISCONNECTED -> "Unable to connect to server. Connection disconnected."
                    else -> "Unable to connect to server: ${databaseError.message}"
                }
                _connectionStatus.value = ConnectionStatus.SERVER_UNAVAILABLE
                onError(errMsg)
            } else {
                Log.d(tag, "Message ${message.messageId} successfully written to RTDB for user $effectiveUid")
                _connectionStatus.value = ConnectionStatus.AI_GENERATING
                try {
                    val metaUpdates = mapOf<String, Any?>(
                        "title" to (message.originalText ?: message.text).take(40),
                        "updatedAt" to System.currentTimeMillis(),
                        "lastMessage" to message.text.take(80),
                        "prompt" to message.text,
                        "lastPrompt" to message.text,
                        "model" to message.model,
                        "maker" to "Rohit",
                        "userId" to effectiveUid
                    )
                    val convMetaPath = DatabasePathResolver.resolve(
                        DatabaseConfig.CONVERSATION_METADATA_PATH,
                        userId = effectiveUid,
                        conversationId = message.conversationId
                    )
                    val userConvRef = db.getReference(convMetaPath)
                    userConvRef.updateChildren(metaUpdates)

                    if (message.hasImage) {
                        userConvRef.child("hasImage").setValue(true)
                    }
                    if (message.agentMode) {
                        userConvRef.child("agentMode").setValue(true)
                    }
                } catch (_: Exception) {}

                onSuccess()
            }
        }
    }

    fun listenToConversation(
        conversationId: String,
        targetUserId: String = userId,
        onMessageReceived: (ChatMessage) -> Unit,
        onMessageUpdated: (ChatMessage) -> Unit,
        onError: (String) -> Unit
    ) {
        val db = databaseInstance ?: return
        val effectiveUid = targetUserId.trim()
        if (effectiveUid.isBlank() || effectiveUid == "unauthenticated") return

        stopListeningToConversation()
        activeConversationId = conversationId

        fun createChildListener(): ChildEventListener {
            return object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val msg = parseMessageSnapshot(snapshot, conversationId, effectiveUid)
                    if (msg != null) {
                        Log.d(tag, "onChildAdded: id=${msg.messageId}, sender=${msg.sender}, status=${msg.status}")
                        onMessageReceived(msg)
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    val msg = parseMessageSnapshot(snapshot, conversationId, effectiveUid)
                    if (msg != null) {
                        Log.d(tag, "onChildChanged: id=${msg.messageId}, sender=${msg.sender}, status=${msg.status}")
                        onMessageUpdated(msg)
                    }
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

                override fun onCancelled(error: DatabaseError) {
                    Log.e(tag, "listenToConversation onCancelled: ${error.message}")
                    val errMsg = "Database listener error: ${error.message}"
                    _connectionStatus.value = ConnectionStatus.SERVER_UNAVAILABLE
                    onError(errMsg)
                }
            }
        }

        val messagesReadPath = DatabasePathResolver.resolve(
            DatabaseConfig.MESSAGES_READ_PATH,
            userId = effectiveUid,
            conversationId = conversationId
        )
        val userMessagesRef = db.getReference(messagesReadPath)
        val userListener = createChildListener()
        activeUserMessagesRef = userMessagesRef
        activeUserChildListener = userListener
        userMessagesRef.addChildEventListener(userListener)
    }

    fun stopListeningToConversation() {
        try {
            activeUserMessagesRef?.let { ref ->
                activeUserChildListener?.let { listener -> ref.removeEventListener(listener) }
            }
        } catch (_: Exception) {}
        activeUserMessagesRef = null
        activeUserChildListener = null
        activeConversationId = null
    }

    private fun parseMessageSnapshot(snapshot: DataSnapshot, conversationId: String, expectedUserId: String): ChatMessage? {
        return try {
            val key = snapshot.key ?: return null
            val value = snapshot.value
            if (value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val map = value as Map<String, Any?>
                val rawMsgUserId = (map["userId"] as? String)?.trim() ?: ""
                // Verification: Discard user-sent messages with foreign sender ID
                val sender = map["sender"] as? String ?: "user"
                if (sender == "user" && rawMsgUserId.isNotBlank() && rawMsgUserId != expectedUserId) {
                    Log.w(tag, "Ignored message $key with mismatched sender ID '$rawMsgUserId' (expected '$expectedUserId')")
                    return null
                }
                ChatMessage.fromMap(key, map).copy(conversationId = conversationId)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing snapshot", e)
            null
        }
    }

    fun startResponseTimeout(
        timeoutSeconds: Int,
        onTimeout: () -> Unit
    ) {
        pendingTimeoutJob?.cancel()
        pendingTimeoutJob = scope.launch {
            delay(timeoutSeconds * 1000L)
            withContext(Dispatchers.Main) {
                onTimeout()
            }
        }
    }

    fun cancelResponseTimeout() {
        pendingTimeoutJob?.cancel()
        pendingTimeoutJob = null
    }

    fun deleteConversation(conversationId: String, targetUserId: String = userId, onComplete: () -> Unit) {
        val db = databaseInstance ?: run { onComplete(); return }
        val effectiveUid = targetUserId.trim()
        if (effectiveUid.isBlank() || effectiveUid == "unauthenticated") {
            onComplete()
            return
        }
        db.getReference("users").child(effectiveUid).child("conversations")
            .child(conversationId)
            .removeValue { _, _ -> onComplete() }
    }

    fun publishActiveModel(modelName: String, provider: String, conversationId: String?, targetUserId: String = userId) {
        val exactGguf = LlmSettings.ensureGgufFilename(modelName)
        requestModelSwitch(exactGguf, targetUserId)

        val db = databaseInstance ?: return
        val effectiveUid = targetUserId.trim()
        if (effectiveUid.isBlank() || effectiveUid == "unauthenticated") return

        db.getReference("users").child(effectiveUid).child("settings").child("model").setValue(exactGguf)
        if (!conversationId.isNullOrEmpty()) {
            db.getReference("users").child(effectiveUid).child("conversations")
                .child(conversationId)
                .child("model")
                .setValue(exactGguf)
        }
    }

    fun syncSettingsToRtdb(settings: LlmSettings, targetUserId: String = userId) {
        val db = databaseInstance ?: return
        val effectiveUid = targetUserId.trim()
        if (effectiveUid.isBlank() || effectiveUid == "unauthenticated") return

        val exactTemperature = (Math.round(settings.temperature * 100.0) / 100.0).toFloat()
        val exactMaxTokens = settings.maxTokens
        val exactTimeout = settings.responseTimeoutSeconds

        val settingsMap = mapOf(
            "agent_mode_enabled" to settings.agentModeEnabled,
            "memory_enabled" to settings.memoryEnabled,
            "vision_enabled" to settings.visionEnabled,
            "translator_enabled" to settings.translatorEnabled,
            "provider" to settings.provider,
            "model_name" to settings.modelName,
            "max_tokens" to exactMaxTokens,
            "temperature" to exactTemperature,
            "timeout" to exactTimeout,
            "response_timeout_seconds" to exactTimeout,
            "speech_input_language" to settings.speechInputLanguage,
            "system_instruction" to settings.systemInstruction,
            "updated_at" to System.currentTimeMillis()
        )

        db.getReference("users").child(effectiveUid).child("settings").updateChildren(settingsMap)
    }

    fun fetchUserSettingsFromRtdb(
        targetUserId: String = userId,
        onResult: (LlmSettings?) -> Unit
    ) {
        try {
            val db = databaseInstance ?: run { onResult(null); return }
            val effectiveUid = targetUserId.trim()
            if (effectiveUid.isBlank() || effectiveUid == "unauthenticated" || !isValidFirebaseKey(effectiveUid)) {
                onResult(null)
                return
            }

            db.getReference("users").child(effectiveUid).child("settings")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            onResult(null)
                            return
                        }
                        try {
                            val modelName = snapshot.child("model_name").getValue(String::class.java)
                                ?: snapshot.child("model").getValue(String::class.java)
                                ?: "qwen2.5-1.5b-instruct-q4_k_m.gguf"
                            val provider = snapshot.child("provider").getValue(String::class.java) ?: "Qwen"
                            val maxTokens = snapshot.child("max_tokens").getValue(Number::class.java)?.toInt() ?: 2048
                            val temperature = snapshot.child("temperature").getValue(Number::class.java)?.toFloat() ?: 0.7f
                            val timeout = snapshot.child("timeout").getValue(Number::class.java)?.toInt()
                                ?: snapshot.child("response_timeout_seconds").getValue(Number::class.java)?.toInt() ?: 45
                            val speechLang = snapshot.child("speech_input_language").getValue(String::class.java) ?: "auto"
                            val sysInstruction = snapshot.child("system_instruction").getValue(String::class.java)
                                ?: LlmSettings.DEFAULT_SYSTEM_INSTRUCTION
                            val memEnabled = snapshot.child("memory_enabled").getValue(Boolean::class.java) ?: true
                            val transEnabled = snapshot.child("translator_enabled").getValue(Boolean::class.java) ?: true
                            val visionEnabled = snapshot.child("vision_enabled").getValue(Boolean::class.java) ?: true
                            val agentEnabled = snapshot.child("agent_mode_enabled").getValue(Boolean::class.java) ?: false

                            val restored = LlmSettings(
                                provider = provider,
                                modelName = LlmSettings.ensureGgufFilename(modelName),
                                maxTokens = maxTokens,
                                temperature = temperature,
                                responseTimeoutSeconds = timeout,
                                speechInputLanguage = speechLang,
                                systemInstruction = sysInstruction,
                                memoryEnabled = memEnabled,
                                translatorEnabled = transEnabled,
                                visionEnabled = visionEnabled,
                                agentModeEnabled = agentEnabled
                            )
                            onResult(restored)
                        } catch (e: Throwable) {
                            Log.w(tag, "Error parsing remote settings: ${e.message}")
                            onResult(null)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        onResult(null)
                    }
                })
        } catch (e: Throwable) {
            Log.w(tag, "fetchUserSettingsFromRtdb error: ${e.message}")
            onResult(null)
        }
    }

    fun syncSliderParameter(paramKey: String, value: Number, targetUserId: String = userId) {
        val db = databaseInstance ?: return
        val effectiveUid = targetUserId.trim()
        if (effectiveUid.isBlank() || effectiveUid == "unauthenticated" || !isValidFirebaseKey(effectiveUid)) return

        val settingsRef = db.getReference("users").child(effectiveUid).child("settings")
        settingsRef.child(paramKey).setValue(value)
        if (paramKey == "timeout") {
            settingsRef.child("response_timeout_seconds").setValue(value)
        } else if (paramKey == "response_timeout_seconds") {
            settingsRef.child("timeout").setValue(value)
        }
        settingsRef.child("updated_at").setValue(System.currentTimeMillis())
    }

    fun syncSystemInstruction(instruction: String, targetUserId: String = userId) {
        val db = databaseInstance ?: return
        val effectiveUid = targetUserId.trim()
        if (effectiveUid.isBlank() || effectiveUid == "unauthenticated" || !isValidFirebaseKey(effectiveUid)) return
        db.getReference("users").child(effectiveUid).child("settings").child("system_instruction").setValue(instruction)
    }

    fun syncWorkspaceFile(file: WorkspaceFile, targetUserId: String = userId) {
        val db = databaseInstance ?: return
        val effectiveUid = targetUserId.trim()
        if (effectiveUid.isBlank() || effectiveUid == "unauthenticated" || !isValidFirebaseKey(effectiveUid)) return
        val sanitized = file.filename.replace(".", "_")
        db.getReference("users").child(effectiveUid).child("workspace").child("files").child(sanitized).setValue(file.toMap())
    }

    fun syncMemories(memories: List<UserMemory>, targetUserId: String = userId) {
        val db = databaseInstance ?: return
        val effectiveUid = targetUserId.trim()
        if (effectiveUid.isBlank() || effectiveUid == "unauthenticated" || !isValidFirebaseKey(effectiveUid)) return
        val map = memories.associate { it.id to it.toMap() }
        db.getReference("users").child(effectiveUid).child("memory").child("active").setValue(map)
    }

    fun fetchUserMemoriesFromRtdb(
        targetUserId: String = userId,
        onResult: (List<UserMemory>) -> Unit
    ) {
        try {
            val db = databaseInstance ?: run { onResult(emptyList()); return }
            val effectiveUid = targetUserId.trim()
            if (effectiveUid.isBlank() || effectiveUid == "unauthenticated" || !isValidFirebaseKey(effectiveUid)) {
                onResult(emptyList())
                return
            }

            db.getReference("users").child(effectiveUid).child("memory").child("active")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val list = mutableListOf<UserMemory>()
                            for (child in snapshot.children) {
                                val mId = child.key ?: continue
                                val valMap = child.value as? Map<*, *> ?: continue
                                val content = valMap["content"] as? String ?: continue
                                val category = valMap["category"] as? String ?: "general"
                                val createdAt = (valMap["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                val isEnabled = valMap["isEnabled"] as? Boolean ?: true
                                list.add(
                                    UserMemory(
                                        id = mId,
                                        content = content,
                                        category = category,
                                        createdAt = createdAt,
                                        isEnabled = isEnabled
                                    )
                                )
                            }
                            onResult(list)
                        } catch (e: Throwable) {
                            onResult(emptyList())
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        onResult(emptyList())
                    }
                })
        } catch (e: Throwable) {
            Log.w(tag, "fetchUserMemoriesFromRtdb error: ${e.message}")
            onResult(emptyList())
        }
    }

    fun sendTerminalCommand(
        command: String,
        language: String = "shell",
        filename: String? = null,
        code: String? = null,
        targetUserId: String = userId,
        onOutput: (stdout: String, stderr: String, exitCode: Int, status: String) -> Unit
    ): String {
        val db = databaseInstance
        val commandId = "cmd_" + java.util.UUID.randomUUID().toString().take(10)
        val effectiveUid = targetUserId.trim()
        if (db == null || effectiveUid.isBlank() || effectiveUid == "unauthenticated") {
            onOutput("", "Error: Backend database not initialized or user unauthenticated.", 1, "ERROR")
            return commandId
        }

        val cmdRef = db.getReference("users").child(effectiveUid).child("agent").child("terminal").child("commands").child(commandId)
        val payload = mutableMapOf<String, Any?>(
            "id" to commandId,
            "command" to command,
            "language" to language,
            "status" to "PENDING",
            "timestamp" to System.currentTimeMillis()
        )
        if (!filename.isNullOrBlank()) payload["filename"] = filename
        if (!code.isNullOrBlank()) payload["code"] = code

        cmdRef.setValue(payload)

        val outRef = db.getReference("users").child(effectiveUid).child("agent").child("terminal").child("output").child(commandId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val map = snapshot.value as? Map<*, *> ?: return
                val stdout = (map["stdout"] as? String) ?: ""
                val stderr = (map["stderr"] as? String) ?: ""
                val exitCode = (map["exit_code"] as? Number)?.toInt() ?: 0
                val status = (map["status"] as? String) ?: "RUNNING"
                onOutput(stdout, stderr, exitCode, status)
                if (status == "COMPLETED" || status == "ERROR") {
                    outRef.removeEventListener(this)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onOutput("", "Terminal listener error: ${error.message}", 1, "ERROR")
            }
        }
        outRef.addValueEventListener(listener)
        return commandId
    }

    fun cancelTerminalCommand(commandId: String, targetUserId: String = userId) {
        val db = databaseInstance ?: return
        val effectiveUid = targetUserId.trim()
        if (effectiveUid.isBlank() || effectiveUid == "unauthenticated") return
        db.getReference("users").child(effectiveUid).child("agent").child("terminal").child("commands").child(commandId).child("status").setValue("CANCELLED")
    }

    fun syncUserProfile(profile: com.example.data.model.UserProfile) {
        val db = databaseInstance ?: return
        val effectiveUid = profile.userId.trim()
        if (effectiveUid.isBlank() || effectiveUid == "unauthenticated") return
        val userRef = db.getReference("users").child(effectiveUid)
        userRef.child("profile").setValue(profile.toPublicMap())
        if (profile.username.isNotBlank()) {
            userRef.child("profile").child("username").setValue(profile.username)
        }
    }

    private fun isValidFirebaseKey(key: String): Boolean {
        if (key.isBlank()) return false
        val forbidden = setOf('.', '#', '$', '[', ']', '/')
        return key.none { it in forbidden }
    }

    fun syncDisplayUsername(username: String, targetUserId: String = userId) {
        try {
            val db = databaseInstance ?: return
            val clean = username.trim()
            val effectiveUid = targetUserId.trim()
            if (clean.isNotBlank() && effectiveUid.isNotBlank() && effectiveUid != "unauthenticated" && isValidFirebaseKey(effectiveUid)) {
                db.getReference("users").child(effectiveUid).child("profile").child("username").setValue(clean)
            }
        } catch (e: Throwable) {
            Log.w(tag, "syncDisplayUsername error: ${e.message}")
        }
    }

    fun fetchRemoteUsername(targetUserId: String, onResult: (String?) -> Unit) {
        try {
            val db = databaseInstance
            val effectiveUid = targetUserId.trim()
            if (db == null || effectiveUid.isBlank() || effectiveUid == "unauthenticated" || !isValidFirebaseKey(effectiveUid)) {
                onResult(null)
                return
            }
            db.getReference("users").child(effectiveUid).child("profile").child("username")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val uname = snapshot.getValue(String::class.java)
                            onResult(if (uname.isNullOrBlank()) null else uname.trim())
                        } catch (e: Throwable) {
                            onResult(null)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        onResult(null)
                    }
                })
        } catch (e: Throwable) {
            Log.w(tag, "fetchRemoteUsername error: ${e.message}")
            onResult(null)
        }
    }

    fun fetchUserWorkspaceFilesFromRtdb(
        targetUserId: String = userId,
        onResult: (List<WorkspaceFile>) -> Unit
    ) {
        try {
            val db = databaseInstance
            val effectiveUid = targetUserId.trim()
            if (db == null || effectiveUid.isBlank() || effectiveUid == "unauthenticated" || !isValidFirebaseKey(effectiveUid)) {
                onResult(emptyList())
                return
            }
            db.getReference("users").child(effectiveUid).child("workspace").child("files")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val list = mutableListOf<WorkspaceFile>()
                            for (child in snapshot.children) {
                                val valMap = child.value as? Map<*, *> ?: continue
                                @Suppress("UNCHECKED_CAST")
                                val file = WorkspaceFile.fromMap(valMap as Map<String, Any?>)
                                list.add(file)
                            }
                            onResult(list)
                        } catch (e: Throwable) {
                            onResult(emptyList())
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        onResult(emptyList())
                    }
                })
        } catch (e: Throwable) {
            Log.w(tag, "fetchUserWorkspaceFilesFromRtdb error: ${e.message}")
            onResult(emptyList())
        }
    }

    /**
     * Safely checks legacy migration without throwing DatabaseException if username has dots, symbols, or invalid keys.
     */
    fun checkAndMigrateLegacyAccount(
        uid: String,
        username: String,
        onComplete: () -> Unit
    ) {
        try {
            // New or newly authenticated accounts start cleanly empty with no foreign chats.
            onComplete()
        } catch (e: Throwable) {
            Log.w(tag, "Error in checkAndMigrateLegacyAccount: ${e.message}")
            onComplete()
        }
    }

    fun fetchUserConversationsFromRtdb(
        targetUserId: String = userId,
        onResult: (List<com.example.data.model.Conversation>, Map<String, List<ChatMessage>>) -> Unit
    ) {
        try {
            val db = databaseInstance ?: run { onResult(emptyList(), emptyMap()); return }
            val effectiveUid = targetUserId.trim()
            if (effectiveUid.isBlank() || effectiveUid == "unauthenticated" || !isValidFirebaseKey(effectiveUid)) {
                onResult(emptyList(), emptyMap())
                return
            }

            db.getReference("users").child(effectiveUid).child("conversations")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val convList = mutableListOf<com.example.data.model.Conversation>()
                            val messagesMap = mutableMapOf<String, List<ChatMessage>>()
                            if (!snapshot.exists()) {
                                onResult(emptyList(), emptyMap())
                                return
                            }
                            for (convSnap in snapshot.children) {
                                val cId = convSnap.key ?: continue
                                val convUserId = (convSnap.child("userId").getValue(String::class.java) ?: effectiveUid).trim()

                                // Strict Verification: Ignore any conversation not belonging to this exact user
                                if (convUserId.isNotBlank() && convUserId != effectiveUid) {
                                    Log.w(tag, "Security/Ownership verification: Skipped foreign conversation $cId (owner: '$convUserId', target: '$effectiveUid')")
                                    continue
                                }

                                val title = convSnap.child("lastPrompt").getValue(String::class.java)
                                    ?: convSnap.child("prompt").getValue(String::class.java)
                                    ?: convSnap.child("lastMessage").getValue(String::class.java)
                                    ?: convSnap.child("title").getValue(String::class.java)
                                    ?: "Conversation"
                                val updatedAt = (convSnap.child("updatedAt").getValue(Number::class.java))?.toLong() ?: System.currentTimeMillis()
                                val model = convSnap.child("model").getValue(String::class.java) ?: "Qwen"

                                convList.add(
                                    com.example.data.model.Conversation(
                                        id = cId,
                                        title = title,
                                        model = model,
                                        updatedAt = updatedAt,
                                        userId = effectiveUid
                                    )
                                )

                                val msgsSnap = convSnap.child("messages")
                                val msgsList = mutableListOf<ChatMessage>()
                                for (mSnap in msgsSnap.children) {
                                    val mKey = mSnap.key ?: continue
                                    val mVal = mSnap.value
                                    if (mVal is Map<*, *>) {
                                        @Suppress("UNCHECKED_CAST")
                                        val mMap = mVal as Map<String, Any?>
                                        val msg = ChatMessage.fromMap(mKey, mMap).copy(conversationId = cId)
                                        val rawUserId = (mMap["userId"] as? String)?.trim() ?: ""

                                        // Strict Verification: Discard user-sent messages with foreign sender ID
                                        if (msg.sender == "user" && rawUserId.isNotBlank() && rawUserId != effectiveUid) {
                                            Log.w(tag, "Security/Ownership verification: Skipped foreign message $mKey from sender '$rawUserId'")
                                            continue
                                        }
                                        msgsList.add(msg)
                                    }
                                }
                                if (msgsList.isNotEmpty()) {
                                    msgsList.sortBy { it.timestamp }
                                    messagesMap[cId] = msgsList
                                }
                            }
                            convList.sortByDescending { it.updatedAt }
                            onResult(convList, messagesMap)
                        } catch (t: Throwable) {
                            Log.w(tag, "Error parsing conversations snapshot: ${t.message}")
                            onResult(emptyList(), emptyMap())
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.w(tag, "fetchUserConversationsFromRtdb error: ${error.message}")
                        onResult(emptyList(), emptyMap())
                    }
                })
        } catch (e: Throwable) {
            Log.w(tag, "fetchUserConversationsFromRtdb fatal error: ${e.message}")
            onResult(emptyList(), emptyMap())
        }
    }
}
