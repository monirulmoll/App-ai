package com.example.data.workspace

import android.util.Log

object WorkspaceFileExtractor {
    private const val TAG = "WorkspaceExtractor"

    data class ExtractedFile(
        val filename: String,
        val content: String
    )

    /**
     * Extracts files from AI responses containing code blocks, file headers, or XML tags.
     */
    fun extractFiles(text: String, userPrompt: String = ""): List<ExtractedFile> {
        val results = mutableListOf<ExtractedFile>()
        if (text.isBlank()) return results

        // Pattern 1: <file name="filename.ext">code</file>
        val xmlPattern = Regex("<file\\s+name=[\"']([^\"']+)[\"']\\s*>([\\s\\S]*?)</file>", RegexOption.IGNORE_CASE)
        xmlPattern.findAll(text).forEach { match ->
            val filename = match.groupValues[1].trim()
            val content = match.groupValues[2].trim()
            if (filename.isNotEmpty() && content.isNotEmpty()) {
                results.add(ExtractedFile(cleanFilename(filename), content))
            }
        }

        // Pattern 2: Explicit headers like "File: app.py" or "### app.py" right before a code block
        val headerPattern = Regex(
            "(?:###|##|\\*\\*|#|//|/\\*)\\s*(?:File(?:name)?|Path)?[:\\s]*([a-zA-Z0-9_\\-\\./]+\\.[a-zA-Z0-9]+)[\\s*]*\\n+```(?:[a-zA-Z0-9_+-]+)?\\n([\\s\\S]*?)```",
            RegexOption.IGNORE_CASE
        )
        headerPattern.findAll(text).forEach { match ->
            val filename = match.groupValues[1].trim()
            val content = match.groupValues[2].trim()
            if (filename.isNotEmpty() && content.isNotEmpty() && results.none { it.filename == filename }) {
                results.add(ExtractedFile(cleanFilename(filename), content))
            }
        }

        // Pattern 3: ```python:main.py or ```cpp:hello.cpp or ```html:index.html
        val fencedNamedPattern = Regex(
            "```[a-zA-Z0-9_+-]+[:\\s]+([a-zA-Z0-9_\\-\\./]+\\.[a-zA-Z0-9]+)\\n([\\s\\S]*?)```",
            RegexOption.IGNORE_CASE
        )
        fencedNamedPattern.findAll(text).forEach { match ->
            val filename = match.groupValues[1].trim()
            val content = match.groupValues[2].trim()
            if (filename.isNotEmpty() && content.isNotEmpty() && results.none { it.filename == filename }) {
                results.add(ExtractedFile(cleanFilename(filename), content))
            }
        }

        // Pattern 4: Fallback for single or multiple standalone code blocks when user asked to create an app or script
        if (results.isEmpty() && isAppOrScriptGenerationIntent(userPrompt, text)) {
            val codeBlockPattern = Regex("```([a-zA-Z0-9_+-]*)\\n([\\s\\S]*?)```")
            val matches = codeBlockPattern.findAll(text).toList()
            matches.forEachIndexed { index, match ->
                val lang = match.groupValues[1].trim().lowercase()
                val code = match.groupValues[2].trim()
                if (code.isNotEmpty()) {
                    val defaultName = inferFilenameFromLanguage(lang, userPrompt, index, matches.size)
                    results.add(ExtractedFile(defaultName, code))
                }
            }
        }

        Log.d(TAG, "Extracted ${results.size} files from text payload.")
        return results
    }

    private fun isAppOrScriptGenerationIntent(prompt: String, text: String): Boolean {
        val p = prompt.lowercase()
        val buildKeywords = listOf("make", "build", "create", "generate", "write", "code", "app", "script", "program")
        val matchesPrompt = buildKeywords.any { p.contains(it) }
        val hasCodeBlock = text.contains("```")
        return (matchesPrompt || p.isEmpty()) && hasCodeBlock
    }

    private fun inferFilenameFromLanguage(lang: String, prompt: String, index: Int, totalBlocks: Int): String {
        val p = prompt.lowercase()
        val suffix = if (totalBlocks > 1) "_${index + 1}" else ""
        return when (lang) {
            "python", "py" -> "main$suffix.py"
            "cpp", "c++", "cc" -> "main$suffix.cpp"
            "c" -> "main$suffix.c"
            "html" -> if (index == 0) "index.html" else "page$suffix.html"
            "css" -> "style$suffix.css"
            "javascript", "js" -> "script$suffix.js"
            "typescript", "ts" -> "app$suffix.ts"
            "kotlin", "kt" -> "Main$suffix.kt"
            "java" -> "Main$suffix.java"
            "json" -> "data$suffix.json"
            "sh", "bash" -> "run$suffix.sh"
            else -> {
                if (p.contains("python") || p.contains(".py")) "main$suffix.py"
                else if (p.contains("c++") || p.contains("cpp")) "main$suffix.cpp"
                else if (p.contains("html") || p.contains("website") || p.contains("web app")) "index$suffix.html"
                else "script$suffix.txt"
            }
        }
    }

    private fun cleanFilename(raw: String): String {
        val cleaned = raw.replace("\\", "/").substringAfterLast("/").trim()
        return cleaned.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { "file.txt" }
    }
}
