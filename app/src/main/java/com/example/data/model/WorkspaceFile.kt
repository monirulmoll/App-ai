package com.example.data.model

data class WorkspaceFile(
    val filename: String,
    val extension: String,
    val sizeBytes: Long,
    val lastModified: Long = System.currentTimeMillis(),
    val content: String = ""
) {
    val language: String
        get() = when (extension.lowercase()) {
            "py" -> "Python"
            "kt", "kts" -> "Kotlin"
            "java" -> "Java"
            "xml" -> "XML"
            "json" -> "JSON"
            "js" -> "JavaScript"
            "html" -> "HTML"
            "css" -> "CSS"
            "sh", "bash" -> "Shell"
            "smali" -> "Smali"
            "lua" -> "Lua"
            "sql" -> "SQL"
            "md" -> "Markdown"
            else -> "Plain Text"
        }

    fun toMap(): Map<String, Any?> = mapOf(
        "filename" to filename,
        "extension" to extension,
        "sizeBytes" to sizeBytes,
        "lastModified" to lastModified,
        "language" to language,
        "content" to content
    )
}
