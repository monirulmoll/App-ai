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

    private var activeModelListener: ValueEventListener? = null
    private var modelResponseListener: ValueEventListener? = null

    private var connectedListener: ValueEventListener? = null
    private var connectedRef: DatabaseReference? = null

    private val prefs = com.example.data.local.LocalChatPreferences(context)
    val userId: String get() = prefs.getUserId()

    private var activeUserMessagesRef: DatabaseReference? = null
    private var activeUserChildListener: ChildEventListener? = null
    private var activeRootMessagesRef: DatabaseReference? = null
    private var activeRootChildListener: ChildEventListener? = null
    private var activeConversationId: String? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var pendingTimeoutJob: Job? = null

    fun resetStopFlag() {
        val db = databaseInstance ?: return
        try {
            val falsePayload = mapOf(
                "stop" to false,
                "conversationId" to "",
                "requestId" to "",
                "timestamp" to System.currentTimeMillis()
            )
            // Reset per-user stop flag via central DatabaseConfig
            val stopPath = DatabasePathResolver.resolve(DatabaseConfig.STOP_FLAG_PATH, userId = userId)
            val stopGenPath = DatabasePathResolver.resolve(DatabaseConfig.STOP_GENERATION_PATH, userId = userId)
            db.getReference(stopPath).setValue(falsePayload)
            db.getReference(stopGenPath).setValue(false)
            Log.d(tag, "Stop flag reset to false for user $userId")
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
            setupModelPathListeners(db)
            probeServerReachability(sanitizedUrl)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize FirebaseDatabase with URL: $sanitizedUrl", e)
            _connectionStatus.value = ConnectionStatus.SERVER_UNAVAILABLE
            _isConnectedToRtdb.value = false
        }
    }

    private fun ensureFirebaseAppInitialized(databaseUrl: String) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val host = try {
                    java.net.URI(databaseUrl).host ?: "ussr-error-404-default"
                } catch (_: Exception) {
                    "ussr-error-404-default"
                }
                val projectId = host.substringBefore(".")

                val options = FirebaseOptions.Builder()
                    .setApplicationId(context.packageName)
                    .setProjectId(projectId)
                    .setDatabaseUrl(databaseUrl)
                    .build()

                FirebaseApp.initializeApp(context, options)
                Log.d(tag, "FirebaseApp initialized with project: $projectId, db: $databaseUrl")
            }
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

    private fun setupModelPathListeners(db: FirebaseDatabase) {
        // Scoped to user settings; no global /model root nodes
    }

    fun requestModelSwitch(ggufFilename: String, onComplete: ((Boolean) -> Unit)? = null) {
        val db = databaseInstance
        if (db == null) {
            onComplete?.invoke(false)
            return
        }
        val exactGguf = LlmSettings.ensureGgufFilename(ggufFilename)
        _activeModelFromRtdb.value = exactGguf
        _isModelSwitching.value = false
        val modelPath = DatabasePathResolver.resolve(DatabaseConfig.SETTINGS_MODEL_PATH, userId = userId)
        db.getReference(modelPath).setValue(exactGguf) { _, _ ->
            onComplete?.invoke(true)
        }
    }

    fun requestStopGeneration(
        conversationId: String,
        requestId: String,
        onResponse: (Boolean) -> Unit
    ) {
        val db = databaseInstance
        if (db == null) {
            onResponse(true)
            return
        }

        val stopPayload = mapOf(
            "stop" to true,
            "requestId" to requestId,
            "conversationId" to conversationId,
            "userId" to userId,
            "timestamp" to System.currentTimeMillis()
        )

        // 1. Write stop exclusively to user's conversation path and per-user stop path via DatabaseConfig
        if (conversationId.isNotEmpty()) {
            val convStopPath = DatabasePathResolver.resolve(DatabaseConfig.CONVERSATION_STOP_PATH, userId = userId, conversationId = conversationId)
            val convStopGenPath = DatabasePathResolver.resolve(DatabaseConfig.CONVERSATION_STOP_GENERATION_PATH, userId = userId, conversationId = conversationId)
            db.getReference(convStopPath).setValue(stopPayload)
            db.getReference(convStopGenPath).setValue(true)
        }

        val stopPath = DatabasePathResolver.resolve(DatabaseConfig.STOP_FLAG_PATH, userId = userId)
        val stopGenPath = DatabasePathResolver.resolve(DatabaseConfig.STOP_GENERATION_PATH, userId = userId)
        db.getReference(stopPath).setValue(stopPayload)
        db.getReference(stopGenPath).setValue(true)

        var completed = false
        val completeOnce: (Boolean) -> Unit = { success ->
            if (!completed) {
                completed = true
                // Automatically reset stop flag back to false so it does not stay permanently true in database
                scope.launch {
                    delay(500)
                    resetStopFlag()
                }
                onResponse(success)
            }
        }

        // Safety fallback timer: unblock UI and reset stop flag
        scope.launch {
            delay(2000)
            completeOnce(true)
        }

        // 3. Listen for backend stop response
        val stopRespRef = if (conversationId.isNotEmpty()) {
            val convStopRespPath = DatabasePathResolver.resolve(DatabaseConfig.CONVERSATION_STOP_RESPONSE_PATH, userId = userId, conversationId = conversationId)
            db.getReference(convStopRespPath)
        } else {
            db.getReference(DatabasePathResolver.resolve(DatabaseConfig.STOP_FLAG_PATH, userId = userId)).child("response")
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
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val db = databaseInstance
        if (db == null) {
            onError("Unable to connect to server. Database not initialized.")
            return
        }

        // Before sending, ensure stop flag is reset to false
        resetStopFlag()

        _connectionStatus.value = ConnectionStatus.MESSAGE_SENDING

        val map = message.toFirebaseMap().toMutableMap()
        map["userId"] = userId
        val sessionToken = prefs.getSessionToken()
        if (!sessionToken.isNullOrBlank()) {
            map["auth_token"] = sessionToken
        }

        // 1. Primary isolated user path via DatabaseConfig: users/{userId}/conversations/{conversationId}/messages/{messageId}
        val msgPath = DatabasePathResolver.resolve(
            DatabaseConfig.MESSAGE_ITEM_PATH,
            userId = userId,
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
                Log.d(tag, "Message ${message.messageId} successfully written to RTDB for user $userId")
                _connectionStatus.value = ConnectionStatus.AI_GENERATING
                // Update conversation metadata on canonical path via DatabaseConfig
                try {
                    val metaUpdates = mapOf<String, Any?>(
                        "title" to (message.originalText ?: message.text).take(40),
                        "updatedAt" to System.currentTimeMillis(),
                        "lastMessage" to message.text.take(80),
                        "prompt" to message.text,
                        "lastPrompt" to message.text,
                        "model" to message.model,
                        "maker" to "Rohit",
                        "userId" to userId
                    )
                    val convMetaPath = DatabasePathResolver.resolve(
                        DatabaseConfig.CONVERSATION_METADATA_PATH,
                        userId = userId,
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
        onMessageReceived: (ChatMessage) -> Unit,
        onMessageUpdated: (ChatMessage) -> Unit,
        onError: (String) -> Unit
    ) {
        val db = databaseInstance ?: return

        stopListeningToConversation()
        activeConversationId = conversationId

        fun createChildListener(): ChildEventListener {
            return object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val msg = parseMessageSnapshot(snapshot, conversationId)
                    if (msg != null) {
                        Log.d(tag, "onChildAdded: id=${msg.messageId}, sender=${msg.sender}, status=${msg.status}")
                        onMessageReceived(msg)
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    val msg = parseMessageSnapshot(snapshot, conversationId)
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

        // Listen to canonical isolated per-user path via DatabaseConfig: users/{userId}/conversations/{conversationId}/messages
        val messagesReadPath = DatabasePathResolver.resolve(
            DatabaseConfig.MESSAGES_READ_PATH,
            userId = userId,
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
            activeRootMessagesRef?.let { ref ->
                activeRootChildListener?.let { listener -> ref.removeEventListener(listener) }
            }
        } catch (_: Exception) {}
        activeUserMessagesRef = null
        activeUserChildListener = null
        activeRootMessagesRef = null
        activeRootChildListener = null
        activeConversationId = null
    }

    private fun parseMessageSnapshot(snapshot: DataSnapshot, conversationId: String): ChatMessage? {
        return try {
            val key = snapshot.key ?: return null
            val value = snapshot.value
            if (value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val map = value as Map<String, Any?>
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

    fun deleteConversation(conversationId: String, onComplete: () -> Unit) {
        val db = databaseInstance ?: run { onComplete(); return }
        db.getReference("users").child(userId).child("conversations")
            .child(conversationId)
            .removeValue { _, _ -> onComplete() }
    }

    fun publishActiveModel(modelName: String, provider: String, conversationId: String?) {
        val exactGguf = LlmSettings.ensureGgufFilename(modelName)
        requestModelSwitch(exactGguf)

        val db = databaseInstance ?: return
        db.getReference("users").child(userId).child("settings").child("model").setValue(exactGguf)
        if (!conversationId.isNullOrEmpty()) {
            db.getReference("users").child(userId).child("conversations")
                .child(conversationId)
                .child("model")
                .setValue(exactGguf)
        }
    }

    // Sync user-scoped settings to Firebase RTDB (/users/{userId}/settings)
    fun syncSettingsToRtdb(settings: LlmSettings) {
        val db = databaseInstance ?: return
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

        db.getReference("users").child(userId).child("settings").setValue(settingsMap)
    }

    // Direct Realtime Slider update under /users/{userId}/settings
    fun syncSliderParameter(paramKey: String, value: Number) {
        val db = databaseInstance ?: return
        val settingsRef = db.getReference("users").child(userId).child("settings")
        settingsRef.child(paramKey).setValue(value)
        if (paramKey == "timeout") {
            settingsRef.child("response_timeout_seconds").setValue(value)
        } else if (paramKey == "response_timeout_seconds") {
            settingsRef.child("timeout").setValue(value)
        }
        settingsRef.child("updated_at").setValue(System.currentTimeMillis())
    }

    // Direct Realtime System Instruction update under /users/{userId}/settings
    fun syncSystemInstruction(instruction: String) {
        val db = databaseInstance ?: return
        db.getReference("users").child(userId).child("settings").child("system_instruction").setValue(instruction)
    }

    // Workspace & Memory Sync to Firebase RTDB strictly under /users/{userId}/
    fun syncWorkspaceFile(file: WorkspaceFile) {
        val db = databaseInstance ?: return
        val sanitized = file.filename.replace(".", "_")
        db.getReference("users").child(userId).child("workspace").child("files").child(sanitized).setValue(file.toMap())
    }

    fun syncMemories(memories: List<UserMemory>) {
        val db = databaseInstance ?: return
        val map = memories.associate { it.id to it.toMap() }
        db.getReference("users").child(userId).child("memory").child("active").setValue(map)
    }

    // Terminal Command Execution strictly under /users/{userId}/agent/terminal/
    fun sendTerminalCommand(
        command: String,
        language: String = "shell",
        filename: String? = null,
        code: String? = null,
        onOutput: (stdout: String, stderr: String, exitCode: Int, status: String) -> Unit
    ): String {
        val db = databaseInstance
        val commandId = "cmd_" + java.util.UUID.randomUUID().toString().take(10)
        if (db == null) {
            onOutput("", "Error: Backend database not initialized. Please connect in Settings.", 1, "ERROR")
            return commandId
        }

        val cmdRef = db.getReference("users").child(userId).child("agent").child("terminal").child("commands").child(commandId)
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

        // Listen for output from backend under /users/{userId}/agent/terminal/output/{commandId}
        val outRef = db.getReference("users").child(userId).child("agent").child("terminal").child("output").child(commandId)
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

    fun cancelTerminalCommand(commandId: String) {
        val db = databaseInstance ?: return
        db.getReference("users").child(userId).child("agent").child("terminal").child("commands").child(commandId).child("status").setValue("CANCELLED")
    }

    fun syncUserProfile(profile: com.example.data.model.UserProfile) {
        val db = databaseInstance ?: return
        val userRef = db.getReference("users").child(profile.userId)
        userRef.child("profile").setValue(profile.toPublicMap())
        if (profile.username.isNotBlank()) {
            userRef.child("profile").child("username").setValue(profile.username)
        }
    }

    fun syncUserProfile(profile: com.example.data.model.GoogleUserProfile) {
        syncUserProfile(
            com.example.data.model.UserProfile(
                userId = profile.googleUserId.ifBlank { userId },
                username = profile.username.ifBlank { prefs.getDisplayUsername() }
            )
        )
    }

    fun syncDisplayUsername(username: String, targetUserId: String = userId) {
        val db = databaseInstance ?: return
        val clean = username.trim()
        if (clean.isNotBlank()) {
            db.getReference("users").child(targetUserId).child("profile").child("username").setValue(clean)
        }
    }

    fun fetchRemoteUsername(targetUserId: String, onResult: (String?) -> Unit) {
        val db = databaseInstance
        if (db == null) {
            onResult(null)
            return
        }
        db.getReference("users").child(targetUserId).child("profile").child("username")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val uname = snapshot.getValue(String::class.java)
                    onResult(if (uname.isNullOrBlank()) null else uname.trim())
                }
                override fun onCancelled(error: DatabaseError) {
                    onResult(null)
                }
            })
    }

    fun fetchUserWorkspaceFilesFromRtdb(
        targetUserId: String = userId,
        onResult: (List<WorkspaceFile>) -> Unit
    ) {
        val db = databaseInstance
        if (db == null) {
            onResult(emptyList())
            return
        }
        db.getReference("users").child(targetUserId).child("workspace").child("files")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<WorkspaceFile>()
                    for (child in snapshot.children) {
                        val valMap = child.value as? Map<*, *> ?: continue
                        @Suppress("UNCHECKED_CAST")
                        val file = WorkspaceFile.fromMap(valMap as Map<String, Any?>)
                        list.add(file)
                    }
                    onResult(list)
                }
                override fun onCancelled(error: DatabaseError) {
                    onResult(emptyList())
                }
            })
    }

    fun migrateLegacyUserConversations(
        legacyUserId: String,
        targetUserId: String,
        onComplete: () -> Unit
    ) {
        // Obsolete: Cross-user data copying is strictly disabled to guarantee absolute data privacy and isolation
        onComplete()
    }

    fun fetchUserConversationsFromRtdb(
        targetUserId: String = userId,
        onResult: (List<com.example.data.model.Conversation>, Map<String, List<ChatMessage>>) -> Unit
    ) {
        val db = databaseInstance ?: return
        val normalizedTarget = targetUserId.trim().lowercase()
        db.getReference("users").child(normalizedTarget).child("conversations")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val convList = mutableListOf<com.example.data.model.Conversation>()
                    val messagesMap = mutableMapOf<String, List<ChatMessage>>()
                    if (!snapshot.exists()) {
                        onResult(emptyList(), emptyMap())
                        return
                    }
                    for (convSnap in snapshot.children) {
                        val cId = convSnap.key ?: continue
                        val convUserId = (convSnap.child("userId").getValue(String::class.java) ?: normalizedTarget).trim().lowercase()
                        
                        // Strict Verification: Ignore any conversation not belonging to this exact user
                        if (convUserId.isNotBlank() && convUserId != normalizedTarget) {
                            Log.w(tag, "Security/Ownership verification: Skipped conversation $cId (owner: '$convUserId', current user: '$normalizedTarget')")
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
                                userId = normalizedTarget
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
                                val rawUserId = (mMap["userId"] as? String)?.trim()?.lowercase() ?: ""
                                
                                // Strict Verification: Discard user-sent messages with mismatched user ID
                                if (msg.sender == "user" && rawUserId.isNotBlank() && rawUserId != normalizedTarget) {
                                    Log.w(tag, "Security/Ownership verification: Skipped message $mKey from foreign sender '$rawUserId'")
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
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(tag, "fetchUserConversationsFromRtdb error: ${error.message}")
                    onResult(emptyList(), emptyMap())
                }
            })
    }
}
