package com.example.data.model

enum class AgentFeatureKey(
    val key: String,
    val displayName: String,
    val description: String,
    val iconEmoji: String
) {
    APP_BUILDING(
        "app_building",
        "App Building",
        "Compile Android projects, run Gradle build tasks, and produce APKs",
        "📱"
    ),
    SCRIPT_GENERATION(
        "script_generation",
        "Script Generation",
        "Generate executable scripts in Python, Kotlin, JavaScript, Shell, and Rust",
        "⚡"
    ),
    WEB_SEARCH(
        "web_search",
        "Web Search",
        "Live internet search and real-time facts retrieval",
        "🔍"
    ),
    MEMORY_RETRIEVAL(
        "memory_retrieval",
        "Persistent Memory Retrieval",
        "Recall user preferences, learned facts, and project guidelines across conversations",
        "🧠"
    ),
    IMAGE_UNDERSTANDING(
        "image_understanding",
        "Image Understanding",
        "Inspect, analyze, and describe uploaded images, UI mockups, and diagrams",
        "🖼️"
    ),
    FILE_UNDERSTANDING(
        "file_understanding",
        "File Understanding",
        "Parse, read, and analyze project files, logs, and configuration documents",
        "📄"
    ),
    IMAGE_GENERATION(
        "image_generation",
        "Image Generation",
        "Create app icons, splash art, and UI asset graphics using image diffusion models",
        "🎨"
    ),
    FILE_CREATION(
        "file_creation",
        "File Creation",
        "Generate and write new source files and directories into the project workspace",
        "📁"
    ),
    LOCAL_FILE_IMPORTS(
        "local_file_imports",
        "Local File Imports",
        "Import files and scripts from Android local storage via system Storage Access Framework",
        "📥"
    ),
    GITHUB_IMPORTS(
        "github_imports",
        "GitHub Repository Imports",
        "Clone, pull, and import repositories from GitHub into project workspace",
        "🐙"
    ),
    CODE_EDITING(
        "code_editing",
        "Code Editing",
        "Inspect, modify, and refactor existing project workspace code",
        "✏️"
    ),
    BUILD_AND_REPAIR(
        "build_and_repair",
        "Build & Error Repair",
        "Autonomously diagnose compilation/build errors and patch project bugs",
        "🛠️"
    );

    companion object {
        fun fromKey(key: String): AgentFeatureKey? =
            values().find { it.key.equals(key, ignoreCase = true) }

        val ALL_DEFAULT_FEATURES: Map<String, Boolean> = values().associate { it.key to true }
    }
}

data class AgentFeatureItem(
    val key: String,
    val name: String,
    val description: String,
    val iconEmoji: String,
    val isEnabled: Boolean = true,
    val isBackendAvailable: Boolean = true
)

enum class AgentProgressState(val label: String) {
    SEARCHING("Searching…"),
    ANALYZING_IMAGE("Analyzing image…"),
    READING_FILE("Reading file…"),
    BUILDING("Building…"),
    REPAIRING("Repairing…"),
    GENERATING_IMAGE("Generating image…"),
    THINKING("Thinking…")
}
