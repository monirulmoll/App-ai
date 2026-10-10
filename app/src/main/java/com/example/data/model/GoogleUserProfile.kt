package com.example.data.model

data class GoogleUserProfile(
    val googleUserId: String, // Stable Google Account ID (sub) — permanent primary identity
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val idToken: String? = null,
    val lastLoginAt: Long = System.currentTimeMillis()
) {
    val isAvailable: Boolean get() = googleUserId.isNotBlank()

    fun toMap(): Map<String, Any?> = mapOf(
        "googleUserId" to googleUserId,
        "email" to email,
        "displayName" to displayName,
        "photoUrl" to photoUrl,
        "idToken" to idToken,
        "lastLoginAt" to lastLoginAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): GoogleUserProfile = GoogleUserProfile(
            googleUserId = (map["googleUserId"] as? String) ?: "",
            email = (map["email"] as? String) ?: "",
            displayName = (map["displayName"] as? String) ?: "",
            photoUrl = map["photoUrl"] as? String,
            idToken = map["idToken"] as? String,
            lastLoginAt = (map["lastLoginAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}
