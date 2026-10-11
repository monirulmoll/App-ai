package com.example.data.auth

import android.content.Context
import android.util.Log
import com.example.data.local.LocalChatPreferences
import com.example.data.model.AuthSession
import com.example.data.model.UserProfile
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.coroutines.resume

object PasswordHasher {
    private const val ITERATIONS = 600000
    private const val KEY_LENGTH = 256 // 32 bytes

    fun hashPassword(password: String): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        val saltHex = saltBytes.joinToString("") { "%02x".format(it) }

        val spec = PBEKeySpec(password.toCharArray(), saltHex.toByteArray(Charsets.UTF_8), ITERATIONS, KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = skf.generateSecret(spec).encoded
        val hashHex = hashBytes.joinToString("") { "%02x".format(it) }

        return "pbkdf2:sha256:$ITERATIONS\$$saltHex\$$hashHex"
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        if (!storedHash.startsWith("pbkdf2:sha256:")) return false
        val parts = storedHash.split("$")
        if (parts.size != 3) return false
        val iterations = parts[0].removePrefix("pbkdf2:sha256:").toIntOrNull() ?: return false
        val saltHex = parts[1]
        val expectedHashHex = parts[2]

        val spec = PBEKeySpec(password.toCharArray(), saltHex.toByteArray(Charsets.UTF_8), iterations, KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = skf.generateSecret(spec).encoded
        val computedHex = hashBytes.joinToString("") { "%02x".format(it) }

        return MessageDigest.isEqual(computedHex.toByteArray(Charsets.UTF_8), expectedHashHex.toByteArray(Charsets.UTF_8))
    }

    fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

class GemoAuthManager(private val context: Context) {
    private val tag = "GemoAuthManager"
    private val prefs = LocalChatPreferences(context)

    private val _currentSession = MutableStateFlow<AuthSession?>(prefs.getAuthSession())
    val currentSession: StateFlow<AuthSession?> = _currentSession.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(prefs.getUserProfile())
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    val isLoggedIn: Boolean get() = _currentSession.value != null && !_currentSession.value!!.isExpired

    private val database: FirebaseDatabase by lazy {
        val settings = prefs.getSettings()
        try {
            FirebaseDatabase.getInstance(settings.firebaseUrl)
        } catch (_: Exception) {
            FirebaseDatabase.getInstance()
        }
    }

    /**
     * Registers a new user directly under /users/{canonicalUsername}/.
     * Guaranteed case-insensitive validation and single-root architecture.
     */
    suspend fun register(
        username: String,
        password: String
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        val canonical = cleanUsername.lowercase()
        if (cleanUsername.length < 3 || cleanUsername.length > 30) {
            return@withContext Result.failure(IllegalArgumentException("Username must be between 3 and 30 characters."))
        }
        if (!cleanUsername.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return@withContext Result.failure(IllegalArgumentException("Username can only contain letters, numbers, and underscores."))
        }
        if (password.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters long."))
        }

        val userRef = database.getReference("users").child(canonical)

        // 1. Check if user already exists
        val exists = suspendCancellableCoroutine<Boolean> { cont ->
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val hasAuth = snapshot.child("auth").hasChild("passwordHash")
                    val hasProfile = snapshot.hasChild("profile")
                    if (cont.isActive) cont.resume(hasAuth || hasProfile)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }

        if (exists) {
            return@withContext Result.failure(Exception("Username is already taken. Please choose another."))
        }

        // 2. Create user record directly under /users/{canonical}/
        val now = System.currentTimeMillis()
        val expiry = now + (30L * 24 * 60 * 60 * 1000L) // 30 days
        val passwordHash = PasswordHasher.hashPassword(password)
        val sessionToken = "S_" + UUID.randomUUID().toString().replace("-", "") + "_" + UUID.randomUUID().toString().replace("-", "")
        val sessionTokenHash = PasswordHasher.sha256Hex(sessionToken)

        // Complete account initialization cloning reference schema structure & defaults
        val writeSuccess = initializeNewUserAccount(
            cleanUsername = cleanUsername,
            canonical = canonical,
            passwordHash = passwordHash,
            sessionTokenHash = sessionTokenHash,
            expiry = expiry,
            now = now
        )

        if (!writeSuccess) {
            return@withContext Result.failure(Exception("Failed to create account. Please check your internet connection."))
        }

        val session = AuthSession(
            sessionToken = sessionToken,
            userId = canonical,
            username = cleanUsername,
            expiresAt = expiry
        )

        prefs.saveAuthSession(session)
        _currentSession.value = session
        _currentUserProfile.value = UserProfile(
            userId = canonical,
            username = cleanUsername,
            sessionToken = sessionToken
        )
        Log.i(tag, "Successfully registered user '$cleanUsername' under /users/$canonical/")
        Result.success(session)
    }

    /**
     * Authenticates an existing user directly from /users/{canonicalUsername}/.
     */
    suspend fun login(
        username: String,
        password: String
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        val canonical = cleanUsername.lowercase()
        if (cleanUsername.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your username."))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your password."))
        }

        val userRef = database.getReference("users").child(canonical)

        val userSnapshot = suspendCancellableCoroutine<DataSnapshot?> { cont ->
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (cont.isActive) cont.resume(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(tag, "Login query error: ${error.message}")
                    if (cont.isActive) cont.resume(null)
                }
            })
        }

        if (userSnapshot == null || !userSnapshot.exists()) {
            return@withContext Result.failure(Exception("Invalid username or password."))
        }

        val authSnap = userSnapshot.child("auth")
        val passwordHash = authSnap.child("passwordHash").getValue(String::class.java)
        if (passwordHash.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Invalid username or password."))
        }

        val accountStatus = authSnap.child("accountStatus").getValue(String::class.java) ?: "active"
        if (accountStatus == "suspended") {
            return@withContext Result.failure(Exception("Account is suspended. Please contact administrator."))
        }

        val valid = PasswordHasher.verifyPassword(password, passwordHash)
        if (!valid) {
            return@withContext Result.failure(Exception("Invalid username or password."))
        }

        val displayUsername = userSnapshot.child("profile").child("username").getValue(String::class.java)
            ?.ifBlank { null } ?: cleanUsername

        val now = System.currentTimeMillis()
        val expiry = now + (30L * 24 * 60 * 60 * 1000L) // 30 days
        val sessionToken = "S_" + UUID.randomUUID().toString().replace("-", "") + "_" + UUID.randomUUID().toString().replace("-", "")
        val sessionTokenHash = PasswordHasher.sha256Hex(sessionToken)

        val sessionPayload = mapOf(
            "sessionTokenHash" to sessionTokenHash,
            "createdAt" to now,
            "expiresAt" to expiry,
            "revoked" to false
        )

        userRef.child("auth").child("sessions").child(sessionTokenHash).setValue(sessionPayload)

        val session = AuthSession(
            sessionToken = sessionToken,
            userId = canonical,
            username = displayUsername,
            expiresAt = expiry
        )

        prefs.saveAuthSession(session)
        _currentSession.value = session
        _currentUserProfile.value = UserProfile(
            userId = canonical,
            username = displayUsername,
            sessionToken = sessionToken
        )
        ensureUserSchemaComplete(canonical, displayUsername)

        Log.i(tag, "Successfully logged in user '$displayUsername' (userId=$canonical)")
        Result.success(session)
    }

    /**
     * Initializes the complete canonical account schema under /users/{canonical}/.
     * Guaranteed to clone the structure and default values from the canonical reference,
     * without copying any user's private data or existing chat messages.
     */
    suspend fun initializeNewUserAccount(
        cleanUsername: String,
        canonical: String,
        passwordHash: String,
        sessionTokenHash: String,
        expiry: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val userRef = database.getReference("users").child(canonical)

        val profileMap = mapOf(
            "username" to cleanUsername,
            "canonicalUsername" to canonical,
            "createdAt" to now,
            "updatedAt" to now
        )

        val authMap = mapOf(
            "passwordHash" to passwordHash,
            "accountStatus" to "active",
            "createdAt" to now,
            "sessions" to mapOf(
                sessionTokenHash to mapOf(
                    "sessionTokenHash" to sessionTokenHash,
                    "createdAt" to now,
                    "expiresAt" to expiry,
                    "revoked" to false
                )
            )
        )

        val settingsMap = mapOf(
            "agent_mode_enabled" to false,
            "max_tokens" to 2048,
            "memory_enabled" to true,
            "model_name" to "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "provider" to "Qwen",
            "response_timeout_seconds" to 45,
            "speech_input_language" to "auto",
            "system_instruction" to "You are Gemo AI, an intelligent AI created by Rohit. Whenever introducing yourself or asked who created or made you, proudly state that your maker and creator is Rohit.",
            "temperature" to 0.7,
            "timeout" to 45,
            "translator_enabled" to true,
            "updated_at" to now,
            "vision_enabled" to true
        )

        val memoryMap = mapOf(
            "active" to mapOf(
                "mem_creator" to mapOf(
                    "category" to "fact",
                    "content" to "Gemo AI's creator and maker is Rohit.",
                    "createdAt" to now,
                    "id" to "mem_creator",
                    "isEnabled" to true
                ),
                "mem_pref_lang" to mapOf(
                    "category" to "preference",
                    "content" to "User prefers polite, intelligent, and context-aware responses in their typed language.",
                    "createdAt" to now,
                    "id" to "mem_pref_lang",
                    "isEnabled" to true
                )
            )
        )

        val initialConversationId = UUID.randomUUID().toString().replace("-", "").take(12)
        val initialWelcomeMessageId = "msg_welcome_" + now

        val initialMessageMap = mapOf(
            "messageId" to initialWelcomeMessageId,
            "conversationId" to initialConversationId,
            "requestId" to UUID.randomUUID().toString(),
            "sender" to "ai",
            "userId" to canonical,
            "text" to "Hello $cleanUsername! Welcome to Gemo AI created by Rohit. How can I help you today?",
            "status" to "completed",
            "timestamp" to now,
            "model" to "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "maker" to "Rohit"
        )

        val initialConversationMap = mapOf(
            "id" to initialConversationId,
            "title" to "Welcome to Gemo AI",
            "userId" to canonical,
            "model" to "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "maker" to "Rohit",
            "createdAt" to now,
            "updatedAt" to now,
            "lastMessage" to "Hello $cleanUsername! Welcome to Gemo AI created by Rohit.",
            "prompt" to "Welcome",
            "lastPrompt" to "Welcome",
            "messages" to mapOf(
                initialWelcomeMessageId to initialMessageMap
            )
        )

        val conversationsMap = mapOf(
            initialConversationId to initialConversationMap
        )

        val workspaceMap = mapOf(
            "files" to mapOf(
                "readme_md" to mapOf(
                    "filename" to "README.md",
                    "content" to "# Gemo AI Workspace\nUser: $cleanUsername\nIsolated secure workspace initialized.",
                    "sizeBytes" to 80L,
                    "lastModified" to now
                )
            )
        )

        val stopMap = mapOf(
            "stop" to false,
            "conversationId" to "",
            "requestId" to "",
            "timestamp" to now
        )

        val agentMap = mapOf(
            "status" to "idle",
            "lastCommand" to "",
            "updatedAt" to now,
            "terminal" to mapOf(
                "commands" to mapOf<String, Any>(),
                "output" to mapOf<String, Any>()
            )
        )

        val updates = mapOf<String, Any>(
            "profile" to profileMap,
            "auth" to authMap,
            "conversations" to conversationsMap,
            "memory" to memoryMap,
            "settings" to settingsMap,
            "workspace" to workspaceMap,
            "agent" to agentMap,
            "stop" to stopMap,
            "stopGeneration" to false
        )

        suspendCancellableCoroutine<Boolean> { cont ->
            userRef.updateChildren(updates) { error, _ ->
                if (error == null) {
                    if (cont.isActive) cont.resume(true)
                } else {
                    Log.e(tag, "Failed to initialize user schema: ${error.message}")
                    if (cont.isActive) cont.resume(false)
                }
            }
        }
    }

    /**
     * Idempotently verifies that all required schema nodes exist for an existing account.
     * Safely backfills missing structural nodes without deleting or overwriting any existing data.
     */
    suspend fun ensureUserSchemaComplete(
        canonical: String,
        displayUsername: String
    ) = withContext(Dispatchers.IO) {
        val userRef = database.getReference("users").child(canonical)
        val now = System.currentTimeMillis()

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val backfills = mutableMapOf<String, Any>()

                if (!snapshot.hasChild("profile")) {
                    backfills["profile"] = mapOf(
                        "username" to displayUsername,
                        "canonicalUsername" to canonical,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                }

                if (!snapshot.hasChild("settings")) {
                    backfills["settings"] = mapOf(
                        "agent_mode_enabled" to false,
                        "max_tokens" to 2048,
                        "memory_enabled" to true,
                        "model_name" to "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                        "provider" to "Qwen",
                        "response_timeout_seconds" to 45,
                        "speech_input_language" to "auto",
                        "system_instruction" to "You are Gemo AI, an intelligent AI created by Rohit. Whenever introducing yourself or asked who created or made you, proudly state that your maker and creator is Rohit.",
                        "temperature" to 0.7,
                        "timeout" to 45,
                        "translator_enabled" to true,
                        "updated_at" to now,
                        "vision_enabled" to true
                    )
                }

                if (!snapshot.hasChild("memory")) {
                    backfills["memory"] = mapOf(
                        "active" to mapOf(
                            "mem_creator" to mapOf(
                                "category" to "fact",
                                "content" to "Gemo AI's creator and maker is Rohit.",
                                "createdAt" to now,
                                "id" to "mem_creator",
                                "isEnabled" to true
                            ),
                            "mem_pref_lang" to mapOf(
                                "category" to "preference",
                                "content" to "User prefers polite, intelligent, and context-aware responses in their typed language.",
                                "createdAt" to now,
                                "id" to "mem_pref_lang",
                                "isEnabled" to true
                            )
                        )
                    )
                }

                if (!snapshot.hasChild("stop")) {
                    backfills["stop"] = mapOf(
                        "stop" to false,
                        "conversationId" to "",
                        "requestId" to "",
                        "timestamp" to now
                    )
                }

                if (!snapshot.hasChild("stopGeneration")) {
                    backfills["stopGeneration"] = false
                }

                if (!snapshot.hasChild("agent")) {
                    backfills["agent"] = mapOf(
                        "status" to "idle",
                        "lastCommand" to "",
                        "updatedAt" to now,
                        "terminal" to mapOf(
                            "commands" to mapOf<String, Any>(),
                            "output" to mapOf<String, Any>()
                        )
                    )
                }

                if (!snapshot.hasChild("conversations")) {
                    val initCid = UUID.randomUUID().toString().replace("-", "").take(12)
                    val welcomeMsgId = "msg_welcome_" + now
                    backfills["conversations"] = mapOf(
                        initCid to mapOf(
                            "id" to initCid,
                            "title" to "Welcome to Gemo AI",
                            "userId" to canonical,
                            "model" to "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                            "maker" to "Rohit",
                            "createdAt" to now,
                            "updatedAt" to now,
                            "lastMessage" to "Hello $displayUsername! Welcome to Gemo AI created by Rohit.",
                            "prompt" to "Welcome",
                            "lastPrompt" to "Welcome",
                            "messages" to mapOf(
                                welcomeMsgId to mapOf(
                                    "messageId" to welcomeMsgId,
                                    "conversationId" to initCid,
                                    "sender" to "ai",
                                    "userId" to canonical,
                                    "text" to "Hello $displayUsername! Welcome to Gemo AI created by Rohit. How can I help you today?",
                                    "status" to "completed",
                                    "timestamp" to now,
                                    "model" to "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                                    "maker" to "Rohit"
                                )
                            )
                        )
                    )
                }

                if (!snapshot.hasChild("workspace")) {
                    backfills["workspace"] = mapOf(
                        "files" to mapOf(
                            "readme_md" to mapOf(
                                "filename" to "README.md",
                                "content" to "# Gemo AI Workspace\nUser: $displayUsername\nIsolated secure workspace initialized.",
                                "sizeBytes" to 80L,
                                "lastModified" to now
                            )
                        )
                    )
                }

                if (backfills.isNotEmpty()) {
                    userRef.updateChildren(backfills) { error, _ ->
                        if (error == null) {
                            Log.d(tag, "Safely backfilled ${backfills.keys} for user $canonical")
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(tag, "ensureUserSchemaComplete cancelled: ${error.message}")
            }
        })
    }

    /**
     * Verifies current session token against /users/{canonical}/auth/sessions/{tokenHash}.
     */
    suspend fun verifySession(): Boolean = withContext(Dispatchers.IO) {
        val session = _currentSession.value ?: prefs.getAuthSession() ?: return@withContext false
        if (session.isExpired) {
            logout()
            return@withContext false
        }

        val canonical = session.userId.lowercase().trim()
        val tokenHash = PasswordHasher.sha256Hex(session.sessionToken)
        val sessionRef = database.getReference("users").child(canonical).child("auth").child("sessions").child(tokenHash)

        val valid = suspendCancellableCoroutine<Boolean> { cont ->
            sessionRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        // Trust valid unexpired local session if offline or remote session was pruned
                        if (cont.isActive) cont.resume(true)
                        return
                    }
                    val revoked = snapshot.child("revoked").getValue(Boolean::class.java) == true
                    val expiresAt = snapshot.child("expiresAt").getValue(Number::class.java)?.toLong() ?: Long.MAX_VALUE
                    val now = System.currentTimeMillis()
                    if (cont.isActive) cont.resume(!revoked && expiresAt > now)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) cont.resume(true)
                }
            })
        }

        if (!valid) {
            logout()
            false
        } else {
            true
        }
    }

    /**
     * Logs out the user, revoking the session token in /users/{canonical}/auth/sessions/{tokenHash}.
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        val session = _currentSession.value ?: prefs.getAuthSession()
        if (session != null) {
            val canonical = session.userId.lowercase().trim()
            val tokenHash = PasswordHasher.sha256Hex(session.sessionToken)
            try {
                database.getReference("users").child(canonical).child("auth").child("sessions").child(tokenHash)
                    .child("revoked").setValue(true)
            } catch (e: Exception) {
                Log.w(tag, "Logout error: ${e.message}")
            }
        }
        prefs.clearAuthSession()
        _currentSession.value = null
        _currentUserProfile.value = null
        Log.i(tag, "User logged out successfully.")
    }
}
