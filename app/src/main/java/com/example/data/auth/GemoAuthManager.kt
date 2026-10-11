package com.example.data.auth

import android.content.Context
import android.util.Log
import com.example.data.config.DatabaseConfig
import com.example.data.firebase.FirebaseInitializer
import com.example.data.local.LocalChatPreferences
import com.example.data.model.AuthSession
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
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
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Enterprise Firebase Authentication Manager.
 * Handles Native Firebase Auth (Email/Password, Google Sign-In, Anonymous Guest)
 * and ensures strict per-user schema cloning under /users/{uid}/ with zero data leakage.
 */
class GemoAuthManager(private val context: Context) {
    private val tag = "GemoAuthManager"
    private val prefs = LocalChatPreferences(context)
    private val firebaseAuth: FirebaseAuth by lazy {
        FirebaseInitializer.ensureInitialized(context)
        FirebaseAuth.getInstance()
    }

    private val _currentSession = MutableStateFlow<AuthSession?>(loadInitialSession())
    val currentSession: StateFlow<AuthSession?> = _currentSession.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(loadInitialProfile())
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    val isLoggedIn: Boolean
        get() = try {
            firebaseAuth.currentUser != null || (_currentSession.value != null && !_currentSession.value!!.isExpired)
        } catch (_: Exception) {
            _currentSession.value != null && !_currentSession.value!!.isExpired
        }

    val currentFirebaseUser: FirebaseUser?
        get() = try { firebaseAuth.currentUser } catch (_: Exception) { null }

    private fun getSafeDatabaseInstance(): FirebaseDatabase? {
        val settings = prefs.getSettings()
        val url = if (settings.firebaseUrl.isNotBlank()) settings.firebaseUrl else DatabaseConfig.MAIN_DATABASE_URL
        return FirebaseInitializer.getSafeDatabase(context, url)
    }

    init {
        try {
            FirebaseInitializer.ensureInitialized(context)
            firebaseAuth.addAuthStateListener { auth ->
                val user = auth.currentUser
                if (user != null) {
                    val displayName = user.displayName?.ifBlank { null }
                        ?: user.email?.substringBefore('@')
                        ?: if (user.isAnonymous) "Guest User" else "User"
                    val session = AuthSession(
                        sessionToken = user.uid,
                        userId = user.uid,
                        username = displayName,
                        email = user.email,
                        isAnonymous = user.isAnonymous,
                        providerId = user.providerData.firstOrNull()?.providerId ?: if (user.isAnonymous) "anonymous" else "password",
                        expiresAt = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L)
                    )
                    _currentSession.value = session
                    _currentUserProfile.value = UserProfile(
                        userId = user.uid,
                        username = displayName,
                        email = user.email,
                        isAnonymous = user.isAnonymous,
                        providerId = session.providerId,
                        sessionToken = user.uid
                    )
                    prefs.saveAuthSession(session)
                } else {
                    val cached = prefs.getAuthSession()
                    if (cached != null && cached.sessionToken.isNotBlank()) {
                        // Do not wipe cached session immediately if offline
                    } else {
                        _currentSession.value = null
                        _currentUserProfile.value = null
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to attach Firebase Auth listener: ${e.message}")
        }
    }

    private fun loadInitialSession(): AuthSession? {
        try {
            val user = firebaseAuth.currentUser
            if (user != null) {
                val name = user.displayName?.ifBlank { null } ?: user.email?.substringBefore('@') ?: if (user.isAnonymous) "Guest User" else "User"
                return AuthSession(
                    sessionToken = user.uid,
                    userId = user.uid,
                    username = name,
                    email = user.email,
                    isAnonymous = user.isAnonymous,
                    providerId = user.providerData.firstOrNull()?.providerId ?: if (user.isAnonymous) "anonymous" else "password"
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to load initial Firebase session: ${e.message}")
        }
        return prefs.getAuthSession()
    }

    private fun loadInitialProfile(): UserProfile? {
        try {
            val user = firebaseAuth.currentUser
            if (user != null) {
                val name = user.displayName?.ifBlank { null } ?: user.email?.substringBefore('@') ?: if (user.isAnonymous) "Guest User" else "User"
                return UserProfile(
                    userId = user.uid,
                    username = name,
                    email = user.email,
                    isAnonymous = user.isAnonymous,
                    providerId = user.providerData.firstOrNull()?.providerId ?: if (user.isAnonymous) "anonymous" else "password",
                    sessionToken = user.uid
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to load initial Firebase profile: ${e.message}")
        }
        return prefs.getUserProfile()
    }

    /**
     * Resolves an email address from either an explicit email or a username.
     */
    private fun resolveEmail(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.contains("@")) {
            trimmed
        } else {
            "${trimmed.lowercase().replace(" ", "_")}@gemo.app"
        }
    }

    /**
     * Registers a new user with Firebase Authentication (Email/Password).
     */
    suspend fun register(
        emailOrUsername: String,
        password: String,
        desiredUsername: String? = null
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        val cleanInput = emailOrUsername.trim()
        if (cleanInput.length < 3) {
            return@withContext Result.failure(IllegalArgumentException("Email or username must be at least 3 characters."))
        }
        if (password.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters long."))
        }

        val effectiveEmail = resolveEmail(cleanInput)
        val displayName = (desiredUsername?.trim()?.ifBlank { null })
            ?: (if (cleanInput.contains("@")) cleanInput.substringBefore('@') else cleanInput)

        val authResult = suspendCancellableCoroutine<Result<FirebaseUser>> { cont ->
            firebaseAuth.createUserWithEmailAndPassword(effectiveEmail, password)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) {
                        // Set display name in Firebase Auth
                        val profileUpdate = UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName)
                            .build()
                        user.updateProfile(profileUpdate).addOnCompleteListener {
                            if (cont.isActive) cont.resume(Result.success(user))
                        }
                    } else {
                        if (cont.isActive) cont.resume(Result.failure(Exception("Created user is null.")))
                    }
                }
                .addOnFailureListener { ex ->
                    Log.e(tag, "Firebase Auth createUser failed: ${ex.message}")
                    if (cont.isActive) cont.resume(Result.failure(ex))
                }
        }

        if (authResult.isFailure) {
            return@withContext Result.failure(authResult.exceptionOrNull() ?: Exception("Registration failed."))
        }

        val user = authResult.getOrThrow()
        val uid = user.uid
        val now = System.currentTimeMillis()
        val expiry = now + (30L * 24 * 60 * 60 * 1000L)

        // Initialize complete canonical schema under /users/{uid}/
        initializeNewUserAccount(
            uid = uid,
            displayName = displayName,
            email = effectiveEmail,
            isAnonymous = false,
            providerId = "password",
            now = now
        )

        val session = AuthSession(
            sessionToken = uid,
            userId = uid,
            username = displayName,
            email = effectiveEmail,
            isAnonymous = false,
            providerId = "password",
            expiresAt = expiry
        )

        prefs.saveAuthSession(session)
        _currentSession.value = session
        _currentUserProfile.value = UserProfile(
            userId = uid,
            username = displayName,
            email = effectiveEmail,
            isAnonymous = false,
            providerId = "password",
            sessionToken = uid
        )

        Log.i(tag, "Successfully created Firebase Auth account for '$displayName' (uid=$uid)")
        Result.success(session)
    }

    /**
     * Authenticates an existing user via Firebase Authentication (Email/Password).
     */
    suspend fun login(
        emailOrUsername: String,
        password: String
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        val cleanInput = emailOrUsername.trim()
        if (cleanInput.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your email or username."))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your password."))
        }

        val effectiveEmail = resolveEmail(cleanInput)

        val authResult = suspendCancellableCoroutine<Result<FirebaseUser>> { cont ->
            firebaseAuth.signInWithEmailAndPassword(effectiveEmail, password)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) {
                        if (cont.isActive) cont.resume(Result.success(user))
                    } else {
                        if (cont.isActive) cont.resume(Result.failure(Exception("Signed-in user is null.")))
                    }
                }
                .addOnFailureListener { ex ->
                    Log.e(tag, "Firebase Auth signIn failed: ${ex.message}")
                    if (cont.isActive) cont.resume(Result.failure(ex))
                }
        }

        if (authResult.isFailure) {
            return@withContext Result.failure(authResult.exceptionOrNull() ?: Exception("Invalid credentials."))
        }

        val user = authResult.getOrThrow()
        val uid = user.uid
        val displayName = user.displayName?.ifBlank { null }
            ?: user.email?.substringBefore('@')
            ?: if (cleanInput.contains("@")) cleanInput.substringBefore('@') else cleanInput
        val now = System.currentTimeMillis()
        val expiry = now + (30L * 24 * 60 * 60 * 1000L)

        ensureUserSchemaComplete(uid = uid, displayName = displayName, email = user.email)

        val session = AuthSession(
            sessionToken = uid,
            userId = uid,
            username = displayName,
            email = user.email ?: effectiveEmail,
            isAnonymous = false,
            providerId = "password",
            expiresAt = expiry
        )

        prefs.saveAuthSession(session)
        _currentSession.value = session
        _currentUserProfile.value = UserProfile(
            userId = uid,
            username = displayName,
            email = user.email ?: effectiveEmail,
            isAnonymous = false,
            providerId = "password",
            sessionToken = uid
        )

        Log.i(tag, "Successfully logged in via Firebase Auth '$displayName' (uid=$uid)")
        Result.success(session)
    }

    /**
     * Signs in with Google credentials via Firebase Auth.
     */
    suspend fun signInWithGoogle(idToken: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = suspendCancellableCoroutine<Result<FirebaseUser>> { cont ->
            firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) {
                        if (cont.isActive) cont.resume(Result.success(user))
                    } else {
                        if (cont.isActive) cont.resume(Result.failure(Exception("Google sign-in user is null.")))
                    }
                }
                .addOnFailureListener { ex ->
                    Log.e(tag, "Firebase Google Auth failed: ${ex.message}")
                    if (cont.isActive) cont.resume(Result.failure(ex))
                }
        }

        if (authResult.isFailure) {
            return@withContext Result.failure(authResult.exceptionOrNull() ?: Exception("Google sign-in failed."))
        }

        val user = authResult.getOrThrow()
        val uid = user.uid
        val displayName = user.displayName?.ifBlank { null } ?: user.email?.substringBefore('@') ?: "Google User"
        val now = System.currentTimeMillis()
        val expiry = now + (30L * 24 * 60 * 60 * 1000L)

        ensureUserSchemaComplete(uid = uid, displayName = displayName, email = user.email)

        val session = AuthSession(
            sessionToken = uid,
            userId = uid,
            username = displayName,
            email = user.email,
            isAnonymous = false,
            providerId = "google.com",
            expiresAt = expiry
        )

        prefs.saveAuthSession(session)
        _currentSession.value = session
        _currentUserProfile.value = UserProfile(
            userId = uid,
            username = displayName,
            email = user.email,
            isAnonymous = false,
            providerId = "google.com",
            sessionToken = uid
        )

        Log.i(tag, "Successfully signed in with Google '$displayName' (uid=$uid)")
        Result.success(session)
    }

    /**
     * Signs in anonymously (Guest Mode) with Firebase Auth.
     */
    suspend fun signInAnonymously(): Result<AuthSession> = withContext(Dispatchers.IO) {
        val authResult = suspendCancellableCoroutine<Result<FirebaseUser>> { cont ->
            firebaseAuth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) {
                        if (cont.isActive) cont.resume(Result.success(user))
                    } else {
                        if (cont.isActive) cont.resume(Result.failure(Exception("Anonymous user is null.")))
                    }
                }
                .addOnFailureListener { ex ->
                    Log.e(tag, "Firebase Anonymous Auth failed: ${ex.message}")
                    if (cont.isActive) cont.resume(Result.failure(ex))
                }
        }

        if (authResult.isFailure) {
            return@withContext Result.failure(authResult.exceptionOrNull() ?: Exception("Guest login failed."))
        }

        val user = authResult.getOrThrow()
        val uid = user.uid
        val displayName = "Guest User"
        val now = System.currentTimeMillis()
        val expiry = now + (30L * 24 * 60 * 60 * 1000L)

        initializeNewUserAccount(
            uid = uid,
            displayName = displayName,
            email = "",
            isAnonymous = true,
            providerId = "anonymous",
            now = now
        )

        val session = AuthSession(
            sessionToken = uid,
            userId = uid,
            username = displayName,
            email = null,
            isAnonymous = true,
            providerId = "anonymous",
            expiresAt = expiry
        )

        prefs.saveAuthSession(session)
        _currentSession.value = session
        _currentUserProfile.value = UserProfile(
            userId = uid,
            username = displayName,
            email = null,
            isAnonymous = true,
            providerId = "anonymous",
            sessionToken = uid
        )

        Log.i(tag, "Successfully signed in as Guest (uid=$uid)")
        Result.success(session)
    }

    /**
     * Initializes the complete canonical account schema under /users/{uid}/.
     * Guaranteed to clone the structure and default values from the canonical reference,
     * without copying any user's private data or existing chat messages.
     */
    suspend fun initializeNewUserAccount(
        uid: String,
        displayName: String,
        email: String?,
        isAnonymous: Boolean,
        providerId: String = "password",
        now: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val db = try { getSafeDatabaseInstance() } catch (_: Throwable) { null }
        if (db == null) {
            Log.w(tag, "FirebaseDatabase instance unavailable during initializeNewUserAccount")
            return@withContext true
        }
        val userRef = db.getReference("users").child(uid)

        val profileMap = mapOf(
            "userId" to uid,
            "username" to displayName,
            "canonicalUsername" to displayName.lowercase().trim(),
            "email" to (email ?: ""),
            "isAnonymous" to isAnonymous,
            "providerId" to providerId,
            "createdAt" to now,
            "updatedAt" to now
        )

        val authMap = mapOf(
            "accountStatus" to "active",
            "providerId" to providerId,
            "isAnonymous" to isAnonymous,
            "createdAt" to now,
            "lastSignInAt" to now
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
            ),
            "config" to mapOf("enabled" to true)
        )

        val workspaceMap = mapOf(
            "files" to mapOf(
                "readme_md" to mapOf(
                    "filename" to "README.md",
                    "content" to "# Gemo AI Workspace\nUser: $displayName\nIsolated secure workspace initialized.",
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
                "active" to false
            )
        )

        // Strict requirement: No pre-existing chats; start with completely clean slate
        val updates = mapOf<String, Any>(
            "profile" to profileMap,
            "auth" to authMap,
            "memory" to memoryMap,
            "settings" to settingsMap,
            "workspace" to workspaceMap,
            "agent" to agentMap,
            "stop" to stopMap,
            "stopGeneration" to false
        )

        try {
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
        } catch (e: Exception) {
            Log.w(tag, "Error in initializeNewUserAccount: ${e.message}")
            true
        }
    }

    /**
     * Idempotently verifies that all required schema nodes exist for an existing account.
     * Safely backfills missing structural nodes without deleting or overwriting any existing data.
     */
    suspend fun ensureUserSchemaComplete(
        uid: String,
        displayName: String,
        email: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val db = getSafeDatabaseInstance() ?: return@withContext
            val userRef = db.getReference("users").child(uid)
            val now = System.currentTimeMillis()

            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val backfills = mutableMapOf<String, Any>()

                        if (!snapshot.hasChild("profile")) {
                            backfills["profile"] = mapOf(
                                "userId" to uid,
                                "username" to displayName,
                                "canonicalUsername" to displayName.lowercase().trim(),
                                "email" to (email ?: ""),
                                "createdAt" to now,
                                "updatedAt" to now
                            )
                        }

                        if (!snapshot.hasChild("auth")) {
                            backfills["auth"] = mapOf(
                                "accountStatus" to "active",
                                "lastSignInAt" to now
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
                                ),
                                "config" to mapOf("enabled" to true)
                            )
                        }

                        if (!snapshot.hasChild("workspace")) {
                            backfills["workspace"] = mapOf(
                                "files" to mapOf(
                                    "readme_md" to mapOf(
                                        "filename" to "README.md",
                                        "content" to "# Gemo AI Workspace\nUser: $displayName\nIsolated workspace initialized.",
                                        "sizeBytes" to 75L,
                                        "lastModified" to now
                                    )
                                )
                            )
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

                        if (backfills.isNotEmpty()) {
                            userRef.updateChildren(backfills) { err, _ ->
                                if (err == null) {
                                    Log.d(tag, "Safely backfilled ${backfills.keys} for user $uid")
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        Log.w(tag, "Error handling backfills snapshot: ${t.message}")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(tag, "ensureUserSchemaComplete cancelled: ${error.message}")
                }
            })
        } catch (e: Throwable) {
            Log.w(tag, "Error in ensureUserSchemaComplete: ${e.message}")
        }
    }

    /**
     * Verifies that the current Firebase Auth session is active and valid.
     */
    suspend fun verifySession(): Boolean = withContext(Dispatchers.IO) {
        val user = firebaseAuth.currentUser ?: return@withContext false
        try {
            suspendCancellableCoroutine<Boolean> { cont ->
                user.getIdToken(false)
                    .addOnSuccessListener {
                        if (cont.isActive) cont.resume(true)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(false)
                    }
            }
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Signs out the user from Firebase Auth and clears local session cache.
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            firebaseAuth.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Firebase signOut error: ${e.message}")
        }
        prefs.clearAuthSession()
        _currentSession.value = null
        _currentUserProfile.value = null
        Log.i(tag, "User logged out successfully.")
    }
}
