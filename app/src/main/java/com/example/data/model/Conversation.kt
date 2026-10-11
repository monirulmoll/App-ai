package com.example.data.model

data class Conversation(
    val id: String = "",
    val title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val model: String = "Gemma 2 9B",
    val lastMessage: String = "",
    val userId: String = ""
) {
    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "model" to model,
        "lastMessage" to lastMessage,
        "userId" to userId
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>, fallbackUserId: String = ""): Conversation {
            return Conversation(
                id = (map["id"] as? String) ?: id,
                title = (map["title"] as? String) ?: "Chat",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                model = (map["model"] as? String) ?: "Gemma 2 9B",
                lastMessage = (map["lastMessage"] as? String) ?: "",
                userId = (map["userId"] as? String)?.ifBlank { null } ?: fallbackUserId
            )
        }
    }
}
