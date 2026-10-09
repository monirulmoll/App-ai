package com.example.data.firebase

import android.content.Context
import android.util.Log
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

    private var activeConversationListener: ChildEventListener? = null
    private var activeConversationRef: DatabaseReference? = null
    private var activeConversationId: String? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var pendingTimeoutJob: Job? = null

    fun initialize(url: String) {
        val sanitizedUrl = url.trim().removeSuffix("/")
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

            val connRef = db.getReference(".info/connected")
            connectedRef = connRef
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) == true
                    Log.d(tag, "RTDB .info/connected: $connected")
                    _isConnectedToRtdb.value = connected
                    if (connected) {
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
        try {
            activeModelListener?.let { db.getReference("model").removeEventListener(it) }
            modelResponseListener?.let { db.getReference("model").child("response").removeEventListener(it) }
        } catch (_: Exception) {}

        // 1. Listen to "/model"
        val modelRef = db.getReference("model")
        val mListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val activeName = when {
                    snapshot.hasChild("name") -> snapshot.child("name").getValue(String::class.java)
                    snapshot.value is Map<*, *> -> (snapshot.value as Map<*, *>)["name"]?.toString()
                    snapshot.value is String -> snapshot.getValue(String::class.java)
                    else -> null
                }
                if (!activeName.isNullOrBlank()) {
                    val exactGguf = LlmSettings.ensureGgufFilename(activeName)
                    Log.d(tag, "Active model read from /model: $exactGguf")
                    _activeModelFromRtdb.value = exactGguf
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(tag, "Failed to read /model: ${error.message}")
            }
        }
        activeModelListener = mListener
        modelRef.addValueEventListener(mListener)

        // 2. Listen to "/model/response"
        val responseRef = db.getReference("model").child("response")
        val rListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val success = snapshot.child("success").getValue(Boolean::class.java) ?: false
                val requested = snapshot.child("requested").getValue(String::class.java) ?: ""
                val active = snapshot.child("active").getValue(String::class.java) ?: ""
                val message = snapshot.child("message").getValue(String::class.java) ?: ""

                if (requested.isNotBlank() || active.isNotBlank() || message.isNotBlank()) {
                    val resp = ModelSwitchResponse(
                        success = success,
                        requested = requested,
                        active = active,
                        message = message,
                        timestamp = System.currentTimeMillis()
                    )
                    Log.d(tag, "Received /model/response: $resp")
                    _isModelSwitching.value = false
                    _modelSwitchResponse.value = resp
                    if (active.isNotBlank()) {
                        _activeModelFromRtdb.value = LlmSettings.ensureGgufFilename(active)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _isModelSwitching.value = false
                Log.w(tag, "Failed to read /model/response: ${error.message}")
            }
        }
        modelResponseListener = rListener
        responseRef.addValueEventListener(rListener)
    }

    fun requestModelSwitch(ggufFilename: String, onComplete: ((Boolean) -> Unit)? = null) {
        val db = databaseInstance
        if (db == null) {
            onComplete?.invoke(false)
            return
        }
        _isModelSwitching.value = true
        val exactGguf = LlmSettings.ensureGgufFilename(ggufFilename)
        val requestMap = mapOf("name" to exactGguf)

        db.getReference("model").child("request").setValue(requestMap) { error, _ ->
            if (error != null) {
                Log.e(tag, "Failed to write to /model/request: ${error.message}")
                _isModelSwitching.value = false
                onComplete?.invoke(false)
            } else {
                Log.d(tag, "Successfully wrote to /model/request: $requestMap")
                onComplete?.invoke(true)
            }
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
            "timestamp" to System.currentTimeMillis()
        )

        // 1. Write stop to conversation path
        if (conversationId.isNotEmpty()) {
            val convRef = db.getReference("conversations").child(conversationId)
            convRef.child("stop").setValue(stopPayload)
            convRef.child("stopGeneration").setValue(true)
        }

        // 2. Also write to global /stop and /stopGeneration
        db.getReference("stop").setValue(stopPayload)
        db.getReference("stopGeneration").setValue(true)

        var completed = false
        val completeOnce: (Boolean) -> Unit = { success ->
            if (!completed) {
                completed = true
                onResponse(success)
            }
        }

        // Safety fallback timer so UI is guaranteed to unblock
        scope.launch {
            delay(3500)
            completeOnce(true)
        }

        // 3. Listen for backend stop response
        val stopRespRef = if (conversationId.isNotEmpty()) {
            db.getReference("conversations").child(conversationId).child("stop").child("response")
        } else {
            db.getReference("stop").child("response")
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

        _connectionStatus.value = ConnectionStatus.MESSAGE_SENDING

        val msgRef = db.getReference("conversations")
            .child(message.conversationId)
            .child("messages")
            .child(message.messageId)

        val map = message.toFirebaseMap()

        msgRef.setValue(map) { databaseError, _ ->
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
                Log.d(tag, "Message ${message.messageId} successfully written to RTDB")
                _connectionStatus.value = ConnectionStatus.AI_GENERATING
                // Update conversation metadata
                try {
                    val convRef = db.getReference("conversations")
                        .child(message.conversationId)
                    convRef.child("updatedAt").setValue(System.currentTimeMillis())
                    convRef.child("lastMessage").setValue(message.text)
                    convRef.child("prompt").setValue(message.text)
                    convRef.child("lastPrompt").setValue(message.text)
                    convRef.child("model").setValue(message.model)
                    convRef.child("maker").setValue("Rohit")
                    if (message.hasImage) {
                        convRef.child("hasImage").setValue(true)
                    }
                    if (message.agentMode) {
                        convRef.child("agentMode").setValue(true)
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
        val messagesRef = db.getReference("conversations")
            .child(conversationId)
            .child("messages")
        activeConversationRef = messagesRef

        val childListener = object : ChildEventListener {
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

        activeConversationListener = childListener
        messagesRef.addChildEventListener(childListener)
    }

    fun stopListeningToConversation() {
        try {
            activeConversationRef?.let { ref ->
                activeConversationListener?.let { listener ->
                    ref.removeEventListener(listener)
                }
            }
        } catch (_: Exception) {}
        activeConversationRef = null
        activeConversationListener = null
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
        databaseInstance?.getReference("conversations")
            ?.child(conversationId)
            ?.removeValue { _, _ -> onComplete() } ?: onComplete()
    }

    fun publishActiveModel(modelName: String, provider: String, conversationId: String?) {
        val exactGguf = LlmSettings.ensureGgufFilename(modelName)
        requestModelSwitch(exactGguf)

        val db = databaseInstance ?: return
        if (!conversationId.isNullOrEmpty()) {
            db.getReference("conversations")
                .child(conversationId)
                .child("model")
                .setValue(exactGguf)
        }
    }

    // Sync all button configurations and settings to Firebase RTDB (/agent/config/settings & /agent/config/features)
    fun syncSettingsToRtdb(settings: LlmSettings) {
        val db = databaseInstance ?: return
        val configRef = db.getReference("agent").child("config")

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

        // Features map for backend capabilities contract
        val featuresMap = mapOf<String, Any>(
            "agent_mode" to settings.agentModeEnabled,
            "memory_retrieval" to settings.memoryEnabled,
            "image_understanding" to settings.visionEnabled,
            "translator" to settings.translatorEnabled
        )

        // 1. Write full configuration under /agent/config/settings
        configRef.child("settings").setValue(settingsMap)
        configRef.child("features").updateChildren(featuresMap)

        // 2. Write numeric slider parameters directly under /agent/config/parameters
        val parametersMap = mapOf(
            "max_tokens" to exactMaxTokens,
            "temperature" to exactTemperature,
            "timeout" to exactTimeout,
            "response_timeout_seconds" to exactTimeout,
            "updated_at" to System.currentTimeMillis()
        )
        configRef.child("parameters").setValue(parametersMap)

        // 3. Write dedicated system instruction paths
        configRef.child("system_instruction").setValue(settings.systemInstruction)
        db.getReference("agent").child("instruction").setValue(settings.systemInstruction)

        // 4. Also sync to root /settings node for easy backend access
        val rootSettingsRef = db.getReference("settings")
        rootSettingsRef.child("parameters").setValue(parametersMap)
        rootSettingsRef.child("max_tokens").setValue(exactMaxTokens)
        rootSettingsRef.child("temperature").setValue(exactTemperature)
        rootSettingsRef.child("timeout").setValue(exactTimeout)
        rootSettingsRef.child("system_instruction").setValue(settings.systemInstruction)
    }

    // Direct Realtime Slider update as user slides (max_tokens, temperature, timeout)
    fun syncSliderParameter(paramKey: String, value: Number) {
        val db = databaseInstance ?: return
        val configRef = db.getReference("agent").child("config")
        val rootSettingsRef = db.getReference("settings")

        // Update in /agent/config/settings/{paramKey}
        configRef.child("settings").child(paramKey).setValue(value)
        // Update in /agent/config/parameters/{paramKey}
        configRef.child("parameters").child(paramKey).setValue(value)
        // Update in /settings/{paramKey} and /settings/parameters/{paramKey}
        rootSettingsRef.child(paramKey).setValue(value)
        rootSettingsRef.child("parameters").child(paramKey).setValue(value)

        // Ensure timeout alias compatibility
        if (paramKey == "timeout") {
            configRef.child("settings").child("response_timeout_seconds").setValue(value)
            configRef.child("parameters").child("response_timeout_seconds").setValue(value)
        } else if (paramKey == "response_timeout_seconds") {
            configRef.child("settings").child("timeout").setValue(value)
            configRef.child("parameters").child("timeout").setValue(value)
            rootSettingsRef.child("timeout").setValue(value)
        }

        // Timestamp
        val now = System.currentTimeMillis()
        configRef.child("parameters").child("updated_at").setValue(now)
        rootSettingsRef.child("updated_at").setValue(now)
    }

    // Direct Realtime System Instruction update
    fun syncSystemInstruction(instruction: String) {
        val db = databaseInstance ?: return
        val configRef = db.getReference("agent").child("config")
        val rootSettingsRef = db.getReference("settings")

        configRef.child("settings").child("system_instruction").setValue(instruction)
        configRef.child("system_instruction").setValue(instruction)
        db.getReference("agent").child("instruction").setValue(instruction)
        rootSettingsRef.child("system_instruction").setValue(instruction)
    }

    // Next-Gen additions: Workspace & Memory Sync to Firebase RTDB
    fun syncWorkspaceFile(file: WorkspaceFile) {
        val db = databaseInstance ?: return
        val sanitized = file.filename.replace(".", "_")
        db.getReference("workspace").child("files").child(sanitized).setValue(file.toMap())
    }

    fun syncMemories(memories: List<UserMemory>) {
        val db = databaseInstance ?: return
        val map = memories.associate { it.id to it.toMap() }
        db.getReference("memory").child("active").setValue(map)
    }

    // Terminal Command Execution via Autonomous Backend
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

        val cmdRef = db.getReference("agent").child("terminal").child("commands").child(commandId)
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

        // Listen for output from backend
        val outRef = db.getReference("agent").child("terminal").child("output").child(commandId)
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
        db.getReference("agent").child("terminal").child("commands").child(commandId).child("status").setValue("CANCELLED")
    }
}
