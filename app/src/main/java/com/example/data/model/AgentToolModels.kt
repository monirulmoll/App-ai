package com.example.data.model

enum class AgentToolType(val id: String, val displayName: String, val icon: String) {
    CALCULATOR("calculator", "Calculator", "🧮"),
    FILE_WORKSPACE("file_workspace", "File Workspace", "📁"),
    CODE_ANALYZER("code_analyzer", "Code Analyzer", "💻"),
    VISION("vision", "Vision / Image", "🖼️"),
    IMAGE_GENERATOR("image_generator", "Image Generator", "🎨"),
    WEB_SEARCH("web_search", "Search Tool", "🔍"),
    MEMORY("memory", "Memory Store", "🧠")
}

data class ToolExecutionResult(
    val toolName: String,
    val toolInput: String,
    val result: String,
    val isSuccess: Boolean = true,
    val executionTimeMs: Long = 0L,
    val extraData: Map<String, Any?> = emptyMap()
)

enum class RequestIntent(val id: String) {
    CHAT("chat"),
    VISION("vision"),
    IMAGE_GENERATION("image_generation"),
    CALCULATOR("calculator"),
    FILE_WORKSPACE("file_workspace"),
    CODE_ANALYSIS("code_analysis"),
    SEARCH("search"),
    MODEL_SWITCH("model_switch"),
    MEMORY("memory")
}
