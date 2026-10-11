package com.example.data.model

enum class AuthRequestType {
    REGISTER,
    LOGIN,
    VERIFY_SESSION,
    LOGOUT
}

data class AuthSession(
    val sessionToken: String,
    val userId: String,
    val username: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) // 30 days
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
}

data class AuthResult(
    val success: Boolean,
    val session: AuthSession? = null,
    val errorMessage: String? = null
)
