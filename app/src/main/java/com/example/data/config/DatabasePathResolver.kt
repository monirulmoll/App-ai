package com.example.data.config

/**
 * Safe, robust resolver for path templates defined in [DatabaseConfig].
 * Sanitizes input placeholders and guarantees user data isolation.
 */
object DatabasePathResolver {

    /**
     * Resolves a path template by substituting placeholders with sanitized values.
     *
     * @param template The template string from [DatabaseConfig] (e.g. "users/{userId}/profile")
     * @param userId The canonical user ID (must be sanitized and non-blank if required by template)
     * @param conversationId The conversation ID if required by template
     * @param messageId The message ID if required by template
     * @param sessionTokenHash The session token hash if required by template
     * @param memoryId The memory item ID if required by template
     * @param fileKey The workspace file key if required by template
     * @param commandId The agent command ID if required by template
     * @return Resolved, normalized Firebase RTDB path without leading/trailing slashes.
     */
    fun resolve(
        template: String,
        userId: String? = null,
        conversationId: String? = null,
        messageId: String? = null,
        sessionTokenHash: String? = null,
        memoryId: String? = null,
        fileKey: String? = null,
        commandId: String? = null
    ): String {
        var resolved = template

        if (resolved.contains("{userId}")) {
            val safeUserId = sanitizePathSegment(userId)
            require(safeUserId.isNotBlank()) { "Cannot resolve path template '$template': userId is missing or blank" }
            resolved = resolved.replace("{userId}", safeUserId)
        }

        if (resolved.contains("{conversationId}")) {
            val safeConvId = sanitizePathSegment(conversationId)
            require(safeConvId.isNotBlank()) { "Cannot resolve path template '$template': conversationId is missing or blank" }
            resolved = resolved.replace("{conversationId}", safeConvId)
        }

        if (resolved.contains("{messageId}")) {
            val safeMsgId = sanitizePathSegment(messageId)
            require(safeMsgId.isNotBlank()) { "Cannot resolve path template '$template': messageId is missing or blank" }
            resolved = resolved.replace("{messageId}", safeMsgId)
        }

        if (resolved.contains("{sessionTokenHash}")) {
            val safeHash = sanitizePathSegment(sessionTokenHash)
            require(safeHash.isNotBlank()) { "Cannot resolve path template '$template': sessionTokenHash is missing or blank" }
            resolved = resolved.replace("{sessionTokenHash}", safeHash)
        }

        if (resolved.contains("{memoryId}")) {
            val safeMemId = sanitizePathSegment(memoryId)
            require(safeMemId.isNotBlank()) { "Cannot resolve path template '$template': memoryId is missing or blank" }
            resolved = resolved.replace("{memoryId}", safeMemId)
        }

        if (resolved.contains("{fileKey}")) {
            val safeKey = sanitizePathSegment(fileKey)
            require(safeKey.isNotBlank()) { "Cannot resolve path template '$template': fileKey is missing or blank" }
            resolved = resolved.replace("{fileKey}", safeKey)
        }

        if (resolved.contains("{commandId}")) {
            val safeCmdId = sanitizePathSegment(commandId)
            require(safeCmdId.isNotBlank()) { "Cannot resolve path template '$template': commandId is missing or blank" }
            resolved = resolved.replace("{commandId}", safeCmdId)
        }

        // Clean any accidental duplicate slashes and trim boundaries
        return resolved.trim('/').replace(Regex("/{2,}"), "/")
    }

    /**
     * Sanitizes a path segment to prevent path traversal or invalid Firebase characters (. # $ [ ] /).
     */
    fun sanitizePathSegment(raw: String?): String {
        if (raw == null) return ""
        return raw.trim()
            .replace("/", "_")
            .replace("\\", "_")
            .replace(".", "_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
    }
}
