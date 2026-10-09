package com.example.ui.screens

enum class AppScreen(val title: String, val route: String) {
    SPLASH("Splash", "splash"),
    ONBOARDING("Onboarding", "onboarding"),
    HOME("Home Dashboard", "home"),
    CHAT("AI Chat", "chat"),
    CONVERSATIONS("Conversations", "conversations"),
    IMAGE_GEN("Image Studio", "image_gen"),
    VISION("Vision Analysis", "vision"),
    CODE_WORKSPACE("Code Workspace", "code_workspace"),
    FILE_MANAGER("File Manager", "file_manager"),
    AGENT_MODE("AI Agent Mode", "agent_mode"),
    MODEL_SELECTION("Select Model", "model_selection"),
    SETTINGS("Settings", "settings"),
    MEMORY("AI Memory", "memory"),
    VOICE("Voice Assistant", "voice"),
    TRANSLATION("Translation", "translation"),
    APPEARANCE("Appearance & Theme", "appearance"),
    FILE_ANALYSIS("File Analysis", "file_analysis"),
    MULTI_MODEL_CHAT("Multi-Model Chat", "multi_model_chat"),
    BACKEND_STATUS("Backend Status", "backend_status"),
    PROFILE("Profile", "profile"),
    MEDIA_GALLERY("Media Gallery", "media_gallery"),
    PREMIUM("Go Premium", "premium"),
    REQUEST_STATUS("Request Status", "request_status"),
    ERROR_STATE("Error State", "error_state")
}
