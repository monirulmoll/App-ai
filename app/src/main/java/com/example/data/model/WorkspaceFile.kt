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

    companion object {
        fun fromMap(map: Map<String, Any?>): WorkspaceFile = WorkspaceFile(
            filename = (map["filename"] as? String) ?: "file.txt",
            extension = (map["extension"] as? String) ?: "txt",
            sizeBytes = (map["sizeBytes"] as? Number)?.toLong() ?: 0L,
            lastModified = (map["lastModified"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            content = (map["content"] as? String) ?: ""
        )
    }
}
