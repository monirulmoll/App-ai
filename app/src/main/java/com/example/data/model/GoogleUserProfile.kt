package com.example.data.model

// Backward-compatible adapter for legacy references
data class GoogleUserProfile(
    val googleUserId: String = "",
    val email: String = "",
    val displayName: String = "",
    val username: String = "",
    val photoUrl: String? = null,
    val idToken: String? = null,
    val lastLoginAt: Long = System.currentTimeMillis()
) {
    val isAvailable: Boolean get() = username.isNotBlank() || googleUserId.isNotBlank()

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to googleUserId,
        "username" to username,
        "displayName" to displayName,
        "lastLoginAt" to lastLoginAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): GoogleUserProfile = GoogleUserProfile(
            googleUserId = (map["userId"] as? String) ?: (map["googleUserId"] as? String) ?: "",
            username = (map["username"] as? String) ?: "",
            displayName = (map["displayName"] as? String) ?: (map["username"] as? String) ?: "",
            lastLoginAt = (map["lastLoginAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}
