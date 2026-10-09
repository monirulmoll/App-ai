package com.example.data.model

data class UserMemory(
    val id: String = "",
    val content: String = "",
    val category: String = "general", // "preference", "fact", "custom", "work"
    val createdAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "content" to content,
        "category" to category,
        "createdAt" to createdAt,
        "isEnabled" to isEnabled
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): UserMemory {
            return UserMemory(
                id = (map["id"] as? String) ?: id,
                content = (map["content"] as? String) ?: "",
                category = (map["category"] as? String) ?: "general",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                isEnabled = (map["isEnabled"] as? Boolean) ?: true
            )
        }
    }
}
