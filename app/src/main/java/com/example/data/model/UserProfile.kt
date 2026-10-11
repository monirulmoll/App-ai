package com.example.data.model

data class UserProfile(
    val userId: String, // Stable unique internal user ID (e.g. "user_e534ece1a4")
    val username: String, // Display username (chosen during registration)
    val canonicalUsername: String = username.lowercase().trim(),
    val createdAt: Long = System.currentTimeMillis(),
    val sessionToken: String? = null // Transient session credential, never exposed publicly
) {
    val isAvailable: Boolean get() = userId.isNotBlank() && username.isNotBlank()

    fun toPublicMap(): Map<String, Any?> = mapOf(
        "username" to username,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>, fallbackUserId: String = ""): UserProfile = UserProfile(
            userId = (map["userId"] as? String)?.ifBlank { null } ?: fallbackUserId,
            username = (map["username"] as? String) ?: "",
            canonicalUsername = (map["canonicalUsername"] as? String)
                ?: ((map["username"] as? String)?.lowercase()?.trim() ?: ""),
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            sessionToken = null
        )
    }
}
