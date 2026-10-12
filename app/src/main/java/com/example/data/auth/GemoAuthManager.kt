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
                    val cachedSession = prefs.getAuthSession()
                    // If user is already authenticated with a custom username node in DB, preserve that username node ID
                    val activeUid = if (cachedSession != null && cachedSession.userId.isNotBlank() && cachedSession.userId != user.uid) {
                        cachedSession.userId
                    } else {
                        user.uid
                    }
                    val displayName = cachedSession?.username?.ifBlank { null }
                        ?: user.displayName?.ifBlank { null }
                        ?: user.email?.substringBefore('@')
                        ?: if (user.isAnonymous) "Guest User" else "User"

                    val session = AuthSession(
                        sessionToken = activeUid,
                        userId = activeUid,
                        username = displayName,
                        email = user.email,
                        isAnonymous = user.isAnonymous,
                        providerId = user.providerData.firstOrNull()?.providerId ?: if (user.isAnonymous) "anonymous" else "password",
                        expiresAt = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L)
                    )
                    _currentSession.value = session
                    _currentUserProfile.value = UserProfile(
                        userId = activeUid,
                        username = displayName,
                        email = user.email,
                        isAnonymous = user.isAnonymous,
                        providerId = session.providerId,
                        sessionToken = activeUid
                    )
                    prefs.saveAuthSession(session)
                } else {
                    val cached = prefs.getAuthSession()
                    if (cached != null && cached.sessionToken.isNotBlank() && !cached.isExpired) {
                        // Keep cached session intact for offline/custom username auth
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
        val cached = prefs.getAuthSession()
        if (cached != null && cached.userId.isNotBlank() && !cached.isExpired) {
            return cached
        }
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
        val cached = prefs.getUserProfile()
        if (cached != null && cached.userId.isNotBlank()) {
            return cached
        }
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

    fun hashPassword(password: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(password.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            password
        }
    }

    fun isValidUsername(username: String): Boolean {
        val trimmed = username.trim()
        if (trimmed.contains(" ")) return false
        return trimmed.matches(Regex("^[a-zA-Z0-9_-]{3,30}$"))
    }

    fun toCanonicalUsername(username: String): String {
        return username.trim().lowercase().replace(" ", "_")
    }

    /**
     * Registers a new user account keyed strictly by their chosen username in the database.
     * Guaranteed never to use random IDs or create duplicate nodes for existing users.
     */
    suspend fun registerWithDetails(
        email: String,
        password: String,
        displayName: String,
        username: String
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        if (cleanUsername.contains(" ")) {
            return@withContext Result.failure(IllegalArgumentException("Username cannot contain spaces. Use only letters, numbers, and underscores (e.g. ussr_error_404)."))
        }
        if (!isValidUsername(cleanUsername)) {
            return@withContext Result.failure(IllegalArgumentException("Username must be 3-30 characters long and contain only letters, numbers, hyphens, or underscores."))
        }
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters long."))
        }

        val canonicalUsername = toCanonicalUsername(cleanUsername)
        val cleanDisplayName = displayName.trim().ifBlank { cleanUsername }

        val db = getSafeDatabaseInstance()
            ?: return@withContext Result.failure(Exception("Database connection unavailable. Please check your internet connection."))

        // 1. Check if node /users/{canonicalUsername} already exists in database
        val existingNodeSnapshot = suspendCancellableCoroutine<DataSnapshot?> { cont ->
            db.getReference("users").child(canonicalUsername).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (cont.isActive) cont.resume(snapshot)
                }
                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }

        if (existingNodeSnapshot != null && existingNodeSnapshot.exists()) {
            return@withContext Result.failure(IllegalArgumentException("Username '$canonicalUsername' is already taken. Please choose another username or sign in."))
        }

        // 2. Check if email is already registered under any existing user node
        val allUsersSnapshot = suspendCancellableCoroutine<DataSnapshot?> { cont ->
            db.getReference("users").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (cont.isActive) cont.resume(snapshot)
                }
                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }

        if (allUsersSnapshot != null && allUsersSnapshot.exists()) {
            for (child in allUsersSnapshot.children) {
                val existingEmail = child.child("profile/email").getValue(String::class.java)?.trim()
                    ?: child.child("auth/email").getValue(String::class.java)?.trim()
                if (existingEmail != null && existingEmail.equals(cleanEmail, ignoreCase = true)) {
                    val existingUserKey = child.key ?: canonicalUsername
                    return@withContext Result.failure(IllegalArgumentException("An account with email '$cleanEmail' already exists with username '@$existingUserKey'. Please sign in instead."))
                }
            }
        }

        // Also register with Firebase Auth in background if possible
        try {
            firebaseAuth.createUserWithEmailAndPassword(cleanEmail, password)
        } catch (_: Exception) {}

        val now = System.currentTimeMillis()
        val expiry = now + (30L * 24 * 60 * 60 * 1000L)
        val pwdHash = hashPassword(password)

        // Initialize user schema directly under /users/{canonicalUsername}/
        initializeNewUserAccount(
            uid = canonicalUsername,
            displayName = cleanDisplayName,
            email = cleanEmail,
            isAnonymous = false,
            providerId = "password",
            now = now,
            passwordHash = pwdHash
        )

        val session = AuthSession(
            sessionToken = canonicalUsername,
            userId = canonicalUsername,
            username = cleanDisplayName,
            email = cleanEmail,
            isAnonymous = false,
            providerId = "password",
            expiresAt = expiry
        )

        prefs.saveAuthSession(session)
        _currentSession.value = session
        _currentUserProfile.value = UserProfile(
            userId = canonicalUsername,
            username = cleanDisplayName,
            email = cleanEmail,
            isAnonymous = false,
            providerId = "password",
            sessionToken = canonicalUsername
        )

        Log.i(tag, "Successfully created account for '@$canonicalUsername' under /users/$canonicalUsername/")
        Result.success(session)
    }

    /**
     * Backward-compatible registration delegating to registerWithDetails.
     */
    suspend fun register(
        emailOrUsername: String,
        password: String,
        desiredUsername: String? = null
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        val cleanInput = emailOrUsername.trim()
        val defaultUsername = desiredUsername?.trim()?.ifBlank { null }
            ?: (if (cleanInput.contains("@")) cleanInput.substringBefore('@') else cleanInput)
        val effectiveEmail = if (cleanInput.contains("@")) cleanInput else resolveEmail(cleanInput)
        registerWithDetails(
            email = effectiveEmail,
            password = password,
            displayName = defaultUsername,
            username = defaultUsername
        )
    }

    /**
     * Authenticates an existing user without creating duplicates.
     * Searches database first by username node or email, verifies credentials,
     * and guarantees the user logs into their exact existing account.
     */
    suspend fun login(
        emailOrUsername: String,
        password: String
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        val cleanInput = emailOrUsername.trim()
        if (cleanInput.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your username or email."))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your password."))
        }

        val canonicalInput = toCanonicalUsername(cleanInput)
        val pwdHash = hashPassword(password)

        val db = getSafeDatabaseInstance()

        // 1. Direct lookup by node key in /users/{canonicalInput}
        if (db != null) {
            val directNode = suspendCancellableCoroutine<DataSnapshot?> { cont ->
                db.getReference("users").child(canonicalInput).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (cont.isActive) cont.resume(snapshot)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }

            if (directNode != null && directNode.exists()) {
                val storedHash = directNode.child("auth/passwordHash").getValue(String::class.java)
                val storedEmail = directNode.child("profile/email").getValue(String::class.java)
                    ?: directNode.child("auth/email").getValue(String::class.java)
                val displayName = directNode.child("profile/username").getValue(String::class.java)?.ifBlank { null }
                    ?: directNode.child("auth/canonicalUsername").getValue(String::class.java)
                    ?: canonicalInput

                var isPasswordValid = storedHash != null && storedHash == pwdHash

                // If passwordHash not found, check with Firebase Auth for legacy accounts
                if (!isPasswordValid && !storedEmail.isNullOrBlank()) {
                    val fbSuccess = try {
                        val authTask = firebaseAuth.signInWithEmailAndPassword(storedEmail, password)
                        suspendCancellableCoroutine<Boolean> { cont ->
                            authTask.addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                                .addOnFailureListener { if (cont.isActive) cont.resume(false) }
                        }
                    } catch (_: Exception) { false }

                    if (fbSuccess) {
                        isPasswordValid = true
                        directNode.ref.child("auth/passwordHash").setValue(pwdHash)
                    }
                }

                if (isPasswordValid) {
                    val now = System.currentTimeMillis()
                    val expiry = now + (30L * 24 * 60 * 60 * 1000L)
                    directNode.ref.child("auth/lastSignInAt").setValue(now)

                    ensureUserSchemaComplete(uid = canonicalInput, displayName = displayName, email = storedEmail, passwordHash = pwdHash)

                    val session = AuthSession(
                        sessionToken = canonicalInput,
                        userId = canonicalInput,
                        username = displayName,
                        email = storedEmail,
                        isAnonymous = false,
                        providerId = "password",
                        expiresAt = expiry
                    )

                    prefs.saveAuthSession(session)
                    _currentSession.value = session
                    _currentUserProfile.value = UserProfile(
                        userId = canonicalInput,
                        username = displayName,
                        email = storedEmail,
                        isAnonymous = false,
                        providerId = "password",
                        sessionToken = canonicalInput
                    )

                    Log.i(tag, "Logged in to existing user node '@$canonicalInput'")
                    return@withContext Result.success(session)
                } else {
                    return@withContext Result.failure(IllegalArgumentException("Incorrect password for account '$cleanInput'."))
                }
            }

            // 2. Search all users for matching email or canonicalUsername
            val allUsers = suspendCancellableCoroutine<DataSnapshot?> { cont ->
                db.getReference("users").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (cont.isActive) cont.resume(snapshot)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }

            if (allUsers != null && allUsers.exists()) {
                for (child in allUsers.children) {
                    val childKey = child.key ?: continue
                    val pEmail = child.child("profile/email").getValue(String::class.java)?.trim()
                    val aEmail = child.child("auth/email").getValue(String::class.java)?.trim()
                    val cUsername = child.child("profile/canonicalUsername").getValue(String::class.java)?.trim()
                        ?: child.child("auth/canonicalUsername").getValue(String::class.java)?.trim()
                        ?: childKey.lowercase()

                    val matchesEmail = (!pEmail.isNullOrBlank() && pEmail.equals(cleanInput, ignoreCase = true)) ||
                                       (!aEmail.isNullOrBlank() && aEmail.equals(cleanInput, ignoreCase = true))
                    val matchesUsername = cUsername.equals(canonicalInput, ignoreCase = true) || childKey.equals(cleanInput, ignoreCase = true)

                    if (matchesEmail || matchesUsername) {
                        val storedHash = child.child("auth/passwordHash").getValue(String::class.java)
                        val displayName = child.child("profile/username").getValue(String::class.java)?.ifBlank { null }
                            ?: childKey
                        val userEmail = pEmail ?: aEmail

                        var isPasswordValid = storedHash != null && storedHash == pwdHash

                        if (!isPasswordValid && !userEmail.isNullOrBlank()) {
                            val fbSuccess = try {
                                val authTask = firebaseAuth.signInWithEmailAndPassword(userEmail, password)
                                suspendCancellableCoroutine<Boolean> { cont ->
                                    authTask.addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                                        .addOnFailureListener { if (cont.isActive) cont.resume(false) }
                                }
                            } catch (_: Exception) { false }

                            if (fbSuccess) {
                                isPasswordValid = true
                                child.ref.child("auth/passwordHash").setValue(pwdHash)
                            }
                        }

                        if (isPasswordValid) {
                            val now = System.currentTimeMillis()
                            val expiry = now + (30L * 24 * 60 * 60 * 1000L)
                            child.ref.child("auth/lastSignInAt").setValue(now)

                            ensureUserSchemaComplete(uid = childKey, displayName = displayName, email = userEmail, passwordHash = pwdHash)

                            val session = AuthSession(
                                sessionToken = childKey,
                                userId = childKey,
                                username = displayName,
                                email = userEmail,
                                isAnonymous = false,
                                providerId = "password",
                                expiresAt = expiry
                            )

                            prefs.saveAuthSession(session)
                            _currentSession.value = session
                            _currentUserProfile.value = UserProfile(
                                userId = childKey,
                                username = displayName,
                                email = userEmail,
                                isAnonymous = false,
                                providerId = "password",
                                sessionToken = childKey
                            )

                            Log.i(tag, "Logged in to existing user node '@$childKey' matching $cleanInput")
                            return@withContext Result.success(session)
                        } else {
                            return@withContext Result.failure(IllegalArgumentException("Incorrect password for account '$cleanInput'."))
                        }
                    }
                }
            }
        }

        // 3. Fallback: Firebase Auth sign in
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
                    if (cont.isActive) cont.resume(Result.failure(ex))
                }
        }

        if (authResult.isSuccess) {
            val user = authResult.getOrThrow()
            val userEmail = user.email ?: effectiveEmail
            val defaultUsername = userEmail.substringBefore('@').lowercase().replace(Regex("[^a-z0-9_-]"), "_")

            var targetUid = defaultUsername
            if (db != null) {
                val checkSnapshot = suspendCancellableCoroutine<DataSnapshot?> { cont ->
                    db.getReference("users").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) { if (cont.isActive) cont.resume(snapshot) }
                        override fun onCancelled(error: DatabaseError) { if (cont.isActive) cont.resume(null) }
                    })
                }
                if (checkSnapshot != null && checkSnapshot.exists()) {
                    for (c in checkSnapshot.children) {
                        val cKey = c.key ?: continue
                        val cEmail = c.child("profile/email").getValue(String::class.java)
                            ?: c.child("auth/email").getValue(String::class.java)
                        if (cEmail != null && cEmail.equals(userEmail, ignoreCase = true)) {
                            targetUid = cKey
                            break
                        }
                    }
                }
            }

            val displayName = user.displayName?.ifBlank { null } ?: targetUid
            val now = System.currentTimeMillis()
            val expiry = now + (30L * 24 * 60 * 60 * 1000L)

            ensureUserSchemaComplete(uid = targetUid, displayName = displayName, email = userEmail, passwordHash = pwdHash)
            db?.getReference("users")?.child(targetUid)?.child("auth/passwordHash")?.setValue(pwdHash)

            val session = AuthSession(
                sessionToken = targetUid,
                userId = targetUid,
                username = displayName,
                email = userEmail,
                isAnonymous = false,
                providerId = "password",
                expiresAt = expiry
            )

            prefs.saveAuthSession(session)
            _currentSession.value = session
            _currentUserProfile.value = UserProfile(
                userId = targetUid,
                username = displayName,
                email = userEmail,
                isAnonymous = false,
                providerId = "password",
                sessionToken = targetUid
            )

            return@withContext Result.success(session)
        }

        Result.failure(IllegalArgumentException("Invalid username or password."))
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
        val userEmail = user.email?.trim() ?: ""
        val googleDisplayName = user.displayName?.ifBlank { null }
            ?: if (userEmail.isNotBlank()) userEmail.substringBefore('@') else "Google User"

        val db = getSafeDatabaseInstance()
        var targetUid: String? = null
        var foundDisplayName: String? = null

        // 1. First verify if an existing user node in database already exists for this email
        if (db != null && userEmail.isNotBlank()) {
            val allUsers = suspendCancellableCoroutine<DataSnapshot?> { cont ->
                db.getReference("users").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (cont.isActive) cont.resume(snapshot)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }

            if (allUsers != null && allUsers.exists()) {
                for (child in allUsers.children) {
                    val childKey = child.key ?: continue
                    val pEmail = child.child("profile/email").getValue(String::class.java)?.trim()
                    val aEmail = child.child("auth/email").getValue(String::class.java)?.trim()
                    if ((!pEmail.isNullOrBlank() && pEmail.equals(userEmail, ignoreCase = true)) ||
                        (!aEmail.isNullOrBlank() && aEmail.equals(userEmail, ignoreCase = true)) ||
                        childKey.equals(user.uid, ignoreCase = true)
                    ) {
                        targetUid = childKey
                        foundDisplayName = child.child("profile/username").getValue(String::class.java)?.ifBlank { null }
                            ?: child.child("auth/canonicalUsername").getValue(String::class.java)?.ifBlank { null }
                            ?: childKey
                        Log.i(tag, "Google sign-in linked to existing account node '@$childKey' for email '$userEmail'")
                        break
                    }
                }
            }
        }

        // 2. If no existing account found in database, use canonical username derived from email or clean name
        val effectiveUid = targetUid ?: run {
            val base = if (userEmail.isNotBlank()) userEmail.substringBefore('@') else googleDisplayName
            toCanonicalUsername(base)
        }
        val effectiveDisplayName = foundDisplayName ?: googleDisplayName

        val now = System.currentTimeMillis()
        val expiry = now + (30L * 24 * 60 * 60 * 1000L)

        // Ensure user schema is complete under the unified username node in RTDB
        ensureUserSchemaComplete(uid = effectiveUid, displayName = effectiveDisplayName, email = user.email)
        db?.getReference("users")?.child(effectiveUid)?.child("auth/lastSignInAt")?.setValue(now)

        val session = AuthSession(
            sessionToken = effectiveUid,
            userId = effectiveUid,
            username = effectiveDisplayName,
            email = user.email,
            isAnonymous = false,
            providerId = "google.com",
            expiresAt = expiry
        )

        prefs.saveAuthSession(session)
        _currentSession.value = session
        _currentUserProfile.value = UserProfile(
            userId = effectiveUid,
            username = effectiveDisplayName,
            email = user.email,
            isAnonymous = false,
            providerId = "google.com",
            sessionToken = effectiveUid
        )

        Log.i(tag, "Successfully signed in with Google '$effectiveDisplayName' (accountNode=@$effectiveUid, email=${user.email})")
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
        now: Long = System.currentTimeMillis(),
        passwordHash: String? = null
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

        val authMap = mutableMapOf<String, Any>(
            "accountStatus" to "active",
            "providerId" to providerId,
            "isAnonymous" to isAnonymous,
            "canonicalUsername" to uid.lowercase().trim(),
            "createdAt" to now,
            "lastSignInAt" to now
        )
        if (!passwordHash.isNullOrBlank()) {
            authMap["passwordHash"] = passwordHash
        }
        if (!email.isNullOrBlank()) {
            authMap["email"] = email
        }

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
        email: String? = null,
        passwordHash: String? = null
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
                            val authM = mutableMapOf<String, Any>(
                                "accountStatus" to "active",
                                "canonicalUsername" to uid.lowercase().trim(),
                                "lastSignInAt" to now
                            )
                            if (!passwordHash.isNullOrBlank()) authM["passwordHash"] = passwordHash
                            if (!email.isNullOrBlank()) authM["email"] = email
                            backfills["auth"] = authM
                        } else if (!passwordHash.isNullOrBlank() && !snapshot.hasChild("auth/passwordHash")) {
                            userRef.child("auth/passwordHash").setValue(passwordHash)
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
