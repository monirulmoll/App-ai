package com.example.data.model

enum class ConnectionStatus(val label: String) {
    CONNECTED("Connected"),
    CONNECTING("Connecting…"),
    SERVER_UNAVAILABLE("Server unavailable"),
    MESSAGE_SENDING("Message sending…"),
    AI_GENERATING("AI generating…"),
    RESPONSE_RECEIVED("Response received")
}
