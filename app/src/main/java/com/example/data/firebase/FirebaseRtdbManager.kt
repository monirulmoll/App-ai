package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
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
        if (sanitizedUrl == currentUrl && databaseInstance != null) {
            return
        }

        currentUrl = sanitizedUrl
        try {
            ensureFirebaseAppInitialized(sanitizedUrl)
            val db = FirebaseDatabase.getInstance(sanitizedUrl)
            // Enable persistence or sync
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
                        // Double check with HTTP probe before saying unavailable
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

    fun probeServerReachability(url: String = currentUrl) {
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
                        // Server is reachable (401/403 means Firebase is alive and enforcing security rules)
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

    /**
     * Send user message to conversations/{conversationId}/messages/{messageId}
     */
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
                } catch (_: Exception) {}

                onSuccess()
            }
        }
    }

    /**
     * Start listening for messages in conversationId
     */
    fun listenToConversation(
        conversationId: String,
        onMessageReceived: (ChatMessage) -> Unit,
        onMessageUpdated: (ChatMessage) -> Unit,
        onError: (String) -> Unit
    ) {
        val db = databaseInstance ?: return

        // Detach old listener if switching conversations
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

            override fun onChildRemoved(snapshot: DataSnapshot) {
                // message removed
            }

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

    /**
     * Start a timeout job for AI response
     */
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

    /**
     * Publishes selected model to Firebase RTDB root under "current_model"
     * so that backend scripts (e.g. Python in Termux) can detect model changes in real time.
     */
    fun publishActiveModel(modelName: String, provider: String, conversationId: String?) {
        val db = databaseInstance ?: return
        val modelData = mapOf(
            "model" to modelName,
            "provider" to provider,
            "maker" to "Rohit",
            "systemPrompt" to "You are Gemo AI, an intelligent AI created by Rohit. Whenever introducing yourself or asked who created or made you, proudly state that your maker and creator is Rohit.",
            "updatedAt" to System.currentTimeMillis(),
            "conversationId" to (conversationId ?: "")
        )

        // 1. Root level current_model node
        db.getReference("current_model").setValue(modelData)

        // 2. Also update conversation node if active
        if (!conversationId.isNullOrEmpty()) {
            db.getReference("conversations")
                .child(conversationId)
                .child("model")
                .setValue(modelName)
        }
    }
}
