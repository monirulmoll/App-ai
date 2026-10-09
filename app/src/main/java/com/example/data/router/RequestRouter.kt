package com.example.data.router

import com.example.data.model.RequestIntent

data class RoutedRequest(
    val intent: RequestIntent,
    val detectedTool: String? = null,
    val toolInput: String? = null,
    val suggestedModel: String? = null,
    val requiresVision: Boolean = false
)

object RequestRouter {

    fun route(
        prompt: String,
        hasImage: Boolean,
        agentModeEnabled: Boolean,
        currentModel: String
    ): RoutedRequest {
        val trimmed = prompt.trim()
        val lower = trimmed.lowercase()

        // 1. Vision Intent
        if (hasImage) {
            return RoutedRequest(
                intent = RequestIntent.VISION,
                detectedTool = "vision",
                toolInput = prompt,
                requiresVision = true
            )
        }

        // 2. Image Generation Intent
        if (lower.startsWith("image banao") ||
            lower.startsWith("photo banao") ||
            lower.startsWith("generate image") ||
            lower.startsWith("draw ") ||
            lower.startsWith("/image") ||
            lower.contains("create an image") ||
            lower.contains("generate a picture")
        ) {
            val description = trimmed
                .replace(Regex("(?i)^(image banao|photo banao|generate image|draw|/image|create an image of|generate a picture of)[:\\s]*"), "")
                .trim()
                .ifEmpty { trimmed }

            return RoutedRequest(
                intent = RequestIntent.IMAGE_GENERATION,
                detectedTool = "image_generator",
                toolInput = description
            )
        }

        // If Agent Mode is NOT enabled, check for explicit tool prefixes or fallback to normal CHAT
        if (!agentModeEnabled && !lower.startsWith("calc") && !lower.startsWith("file") && !lower.startsWith("remember")) {
            return RoutedRequest(
                intent = RequestIntent.CHAT,
                suggestedModel = currentModel
            )
        }

        // 3. Calculator Intent
        if (lower.startsWith("calculate") ||
            lower.startsWith("calc ") ||
            lower.matches(Regex("^[0-9+\\-*/^().\\s=]+$")) ||
            lower.contains("sqrt(")
        ) {
            return RoutedRequest(
                intent = RequestIntent.CALCULATOR,
                detectedTool = "calculator",
                toolInput = trimmed
            )
        }

        // 4. File Workspace Intent
        if (lower.startsWith("create file") ||
            lower.startsWith("read file") ||
            lower.startsWith("file banao") ||
            lower.startsWith("list files") ||
            lower.startsWith("workspace")
        ) {
            return RoutedRequest(
                intent = RequestIntent.FILE_WORKSPACE,
                detectedTool = "file_workspace",
                toolInput = trimmed
            )
        }

        // 5. Memory Intent
        if (lower.startsWith("remember ") ||
            lower.startsWith("yaad rakh") ||
            lower.startsWith("save memory")
        ) {
            val memContent = trimmed.replace(Regex("(?i)^(remember|yaad rakhna|yaad rakho|save memory)[:\\s]*"), "").trim()
            return RoutedRequest(
                intent = RequestIntent.MEMORY,
                detectedTool = "memory",
                toolInput = memContent
            )
        }

        // 6. Search Intent
        if (lower.startsWith("search ") ||
            lower.startsWith("dhoondo ") ||
            lower.startsWith("google ")
        ) {
            return RoutedRequest(
                intent = RequestIntent.SEARCH,
                detectedTool = "web_search",
                toolInput = trimmed
            )
        }

        // 7. Code Analysis
        if (lower.startsWith("analyze code") ||
            lower.startsWith("code check") ||
            lower.contains("```")
        ) {
            return RoutedRequest(
                intent = RequestIntent.CODE_ANALYSIS,
                detectedTool = "code_analyzer",
                toolInput = trimmed
            )
        }

        return RoutedRequest(
            intent = RequestIntent.CHAT,
            suggestedModel = currentModel
        )
    }
}
