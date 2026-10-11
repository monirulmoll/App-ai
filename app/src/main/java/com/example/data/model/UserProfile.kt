package com.example.data.model

data class UserProfile(
    val userId: String, // Stable unique internal user ID (Firebase Auth UID)
    val username: String, // Display username (chosen during registration or from Firebase)
    val email: String? = null,
    val canonicalUsername: String = username.lowercase().trim(),
    val isAnonymous: Boolean = false,
    val providerId: String = "password",
    val createdAt: Long = System.currentTimeMillis(),
    val sessionToken: String? = null // Transient session credential, never exposed publicly
) {
    val isAvailable: Boolean get() = userId.isNotBlank() && username.isNotBlank()

    fun toPublicMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "username" to username,
        "email" to (email ?: ""),
        "isAnonymous" to isAnonymous,
        "providerId" to providerId,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>, fallbackUserId: String = ""): UserProfile = UserProfile(
            userId = (map["userId"] as? String)?.ifBlank { null } ?: fallbackUserId,
            username = (map["username"] as? String) ?: "",
            email = map["email"] as? String,
            canonicalUsername = (map["canonicalUsername"] as? String)
                ?: ((map["username"] as? String)?.lowercase()?.trim() ?: ""),
            isAnonymous = (map["isAnonymous"] as? Boolean) == true,
            providerId = (map["providerId"] as? String) ?: "password",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            sessionToken = null
        )
    }
}
