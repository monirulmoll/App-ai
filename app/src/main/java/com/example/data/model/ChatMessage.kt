package com.example.data.model

data class ChatMessage(
    val messageId: String = "",
    val conversationId: String = "",
    val sender: String = "user", // "user" or "ai"
    val text: String = "",
    val originalText: String? = null,
    val sourceLang: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "sent", // "sending", "sent", "generating", "completed", "error"
    val requestId: String = "",
    val model: String = "",
    val errorMessage: String? = null
) {
    val isUser: Boolean get() = sender.equals("user", ignoreCase = true)
    val isAi: Boolean get() = !isUser
    val isGenerating: Boolean get() = status == "generating" || (isAi && text.isEmpty() && status != "completed" && status != "error")
    val isError: Boolean get() = status == "error"
    val isSending: Boolean get() = status == "sending"
    val isCompleted: Boolean get() = status == "completed"

    val displayText: String get() = if (isUser && !originalText.isNullOrBlank()) originalText else text

    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "messageId" to messageId,
        "conversationId" to conversationId,
        "sender" to sender,
        "text" to text,
        "prompt" to text,
        "originalText" to (originalText ?: text),
        "sourceLang" to (sourceLang ?: "en"),
        "timestamp" to timestamp,
        "status" to status,
        "requestId" to requestId,
        "model" to model,
        "maker" to "Rohit",
        "systemPrompt" to "You are Gemo AI, an intelligent AI created by Rohit. Whenever introducing yourself or asked who created or made you, proudly state that your maker and creator is Rohit."
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): ChatMessage {
            return ChatMessage(
                messageId = (map["messageId"] as? String) ?: id,
                conversationId = (map["conversationId"] as? String) ?: "",
                sender = (map["sender"] as? String) ?: "ai",
                text = (map["text"] as? String) ?: "",
                originalText = map["originalText"] as? String,
                sourceLang = map["sourceLang"] as? String,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                status = (map["status"] as? String) ?: "completed",
                requestId = (map["requestId"] as? String) ?: "",
                model = (map["model"] as? String) ?: "",
                errorMessage = map["errorMessage"] as? String
            )
        }
    }
}
