package com.example.data.config

/**
 * Single Authoritative Source of Truth for all Gemo AI Database Configuration.
 * Every database URL, connection parameter, and operational database path
 * must be defined here.
 *
 * DO NOT hardcode database URLs or paths anywhere else in the application.
 */
object DatabaseConfig {

    // =========================================================================
    // 1. MAIN DATABASE CONNECTION CONFIGURATION
    // =========================================================================

    /**
     * The primary authoritative Firebase Realtime Database URL.
     * Edit this single constant to redirect the entire application to another database.
     */
    const val MAIN_DATABASE_URL: String = "https://ussr-error-404-default-rtdb.firebaseio.com"

    /**
     * Database protocol scheme.
     */
    const val DATABASE_PROTOCOL: String = "https"

    /**
     * Connection timeout in milliseconds.
     */
    const val CONNECTION_TIMEOUT_MS: Long = 15000L

    /**
     * Keepalive / heartbeat system check path.
     */
    const val SYSTEM_CONNECTED_PATH: String = ".info/connected"

    // =========================================================================
    // 2. ROOT & BASE COLLECTION PATHS
    // =========================================================================

    /**
     * Single authoritative root node under which all user data is partitioned.
     */
    const val ROOT_USERS_PATH: String = "users"

    /**
     * Path to a specific user's root node.
     * Template: users/{userId}
     */
    const val USER_NODE_PATH: String = "users/{userId}"

    // =========================================================================
    // 3. USER PROFILE PATHS
    // =========================================================================

    /**
     * User profile path template for reading and writing user profile info.
     * Template: users/{userId}/profile
     */
    const val USER_PROFILE_PATH: String = "users/{userId}/profile"

    /**
     * User display username path.
     * Template: users/{userId}/profile/username
     */
    const val USER_USERNAME_PATH: String = "users/{userId}/profile/username"

    // =========================================================================
    // 4. AUTHENTICATION & SESSION PATHS
    // =========================================================================

    /**
     * Path to user authentication node.
     * Template: users/{userId}/auth
     */
    const val AUTH_ROOT_PATH: String = "users/{userId}/auth"

    /**
     * Password hash path for verification.
     * Template: users/{userId}/auth/passwordHash
     */
    const val AUTH_PASSWORD_HASH_PATH: String = "users/{userId}/auth/passwordHash"

    /**
     * Account status path (e.g. "active", "suspended").
     * Template: users/{userId}/auth/accountStatus
     */
    const val AUTH_ACCOUNT_STATUS_PATH: String = "users/{userId}/auth/accountStatus"

    /**
     * Active sessions container path.
     * Template: users/{userId}/auth/sessions
     */
    const val AUTH_SESSIONS_PATH: String = "users/{userId}/auth/sessions"

    /**
     * Specific session token hash path.
     * Template: users/{userId}/auth/sessions/{sessionTokenHash}
     */
    const val AUTH_SESSION_ITEM_PATH: String = "users/{userId}/auth/sessions/{sessionTokenHash}"

    /**
     * Specific session revoked flag path.
     * Template: users/{userId}/auth/sessions/{sessionTokenHash}/revoked
     */
    const val AUTH_SESSION_REVOKED_PATH: String = "users/{userId}/auth/sessions/{sessionTokenHash}/revoked"

    // =========================================================================
    // 5. CONVERSATION PATHS (READ, WRITE, UPDATE, DELETE)
    // =========================================================================

    /**
     * Read path for all conversations belonging to a user.
     * Template: users/{userId}/conversations
     */
    const val CONVERSATIONS_READ_PATH: String = "users/{userId}/conversations"

    /**
     * Write / update path for user conversations.
     * Template: users/{userId}/conversations
     */
    const val CONVERSATIONS_WRITE_PATH: String = "users/{userId}/conversations"

    /**
     * Specific conversation path.
     * Template: users/{userId}/conversations/{conversationId}
     */
    const val CONVERSATION_ITEM_PATH: String = "users/{userId}/conversations/{conversationId}"

    /**
     * Conversation metadata update path.
     * Template: users/{userId}/conversations/{conversationId}
     */
    const val CONVERSATION_METADATA_PATH: String = "users/{userId}/conversations/{conversationId}"

    // =========================================================================
    // 6. MESSAGES PATHS (READ, WRITE, STREAMING)
    // =========================================================================

    /**
     * Read path for messages in a conversation.
     * Template: users/{userId}/conversations/{conversationId}/messages
     */
    const val MESSAGES_READ_PATH: String = "users/{userId}/conversations/{conversationId}/messages"

    /**
     * Write path for messages in a conversation.
     * Template: users/{userId}/conversations/{conversationId}/messages
     */
    const val MESSAGES_WRITE_PATH: String = "users/{userId}/conversations/{conversationId}/messages"

    /**
     * Specific message item path.
     * Template: users/{userId}/conversations/{conversationId}/messages/{messageId}
     */
    const val MESSAGE_ITEM_PATH: String = "users/{userId}/conversations/{conversationId}/messages/{messageId}"

    // =========================================================================
    // 7. MEMORY PATHS
    // =========================================================================

    /**
     * Root memory path for a user.
     * Template: users/{userId}/memory
     */
    const val MEMORY_ROOT_PATH: String = "users/{userId}/memory"

    /**
     * Active memory collection read path.
     * Template: users/{userId}/memory/active
     */
    const val MEMORY_READ_PATH: String = "users/{userId}/memory/active"

    /**
     * Active memory collection write path.
     * Template: users/{userId}/memory/active
     */
    const val MEMORY_WRITE_PATH: String = "users/{userId}/memory/active"

    /**
     * Specific memory item path.
     * Template: users/{userId}/memory/active/{memoryId}
     */
    const val MEMORY_ITEM_PATH: String = "users/{userId}/memory/active/{memoryId}"

    // =========================================================================
    // 8. SETTINGS PATHS
    // =========================================================================

    /**
     * User settings read path.
     * Template: users/{userId}/settings
     */
    const val SETTINGS_READ_PATH: String = "users/{userId}/settings"

    /**
     * User settings write path.
     * Template: users/{userId}/settings
     */
    const val SETTINGS_WRITE_PATH: String = "users/{userId}/settings"

    /**
     * Model preference path within settings.
     * Template: users/{userId}/settings/model
     */
    const val SETTINGS_MODEL_PATH: String = "users/{userId}/settings/model"

    /**
     * System instruction path within settings.
     * Template: users/{userId}/settings/system_instruction
     */
    const val SETTINGS_SYSTEM_INSTRUCTION_PATH: String = "users/{userId}/settings/system_instruction"

    // =========================================================================
    // 9. WORKSPACE & CODE FILES PATHS
    // =========================================================================

    /**
     * Workspace files read path.
     * Template: users/{userId}/workspace/files
     */
    const val WORKSPACE_FILES_READ_PATH: String = "users/{userId}/workspace/files"

    /**
     * Workspace files write path.
     * Template: users/{userId}/workspace/files
     */
    const val WORKSPACE_FILES_WRITE_PATH: String = "users/{userId}/workspace/files"

    /**
     * Specific workspace file item path.
     * Template: users/{userId}/workspace/files/{fileKey}
     */
    const val WORKSPACE_FILE_ITEM_PATH: String = "users/{userId}/workspace/files/{fileKey}"

    // =========================================================================
    // 10. AGENT & TERMINAL PATHS
    // =========================================================================

    /**
     * Agent root path.
     * Template: users/{userId}/agent
     */
    const val AGENT_ROOT_PATH: String = "users/{userId}/agent"

    /**
     * Agent terminal commands queue path.
     * Template: users/{userId}/agent/terminal/commands
     */
    const val AGENT_TERMINAL_COMMANDS_PATH: String = "users/{userId}/agent/terminal/commands"

    /**
     * Specific terminal command path.
     * Template: users/{userId}/agent/terminal/commands/{commandId}
     */
    const val AGENT_TERMINAL_COMMAND_ITEM_PATH: String = "users/{userId}/agent/terminal/commands/{commandId}"

    /**
     * Agent terminal output path.
     * Template: users/{userId}/agent/terminal/output
     */
    const val AGENT_TERMINAL_OUTPUT_PATH: String = "users/{userId}/agent/terminal/output"

    /**
     * Specific terminal output item path.
     * Template: users/{userId}/agent/terminal/output/{commandId}
     */
    const val AGENT_TERMINAL_OUTPUT_ITEM_PATH: String = "users/{userId}/agent/terminal/output/{commandId}"

    // =========================================================================
    // 11. STOP GENERATION & FLOW CONTROL PATHS
    // =========================================================================

    /**
     * User-level stop flag object path.
     * Template: users/{userId}/stop
     */
    const val STOP_FLAG_PATH: String = "users/{userId}/stop"

    /**
     * User-level boolean stopGeneration path.
     * Template: users/{userId}/stopGeneration
     */
    const val STOP_GENERATION_PATH: String = "users/{userId}/stopGeneration"

    /**
     * Conversation-level stop flag object path.
     * Template: users/{userId}/conversations/{conversationId}/stop
     */
    const val CONVERSATION_STOP_PATH: String = "users/{userId}/conversations/{conversationId}/stop"

    /**
     * Conversation-level stop response path.
     * Template: users/{userId}/conversations/{conversationId}/stop/response
     */
    const val CONVERSATION_STOP_RESPONSE_PATH: String = "users/{userId}/conversations/{conversationId}/stop/response"

    /**
     * Conversation-level boolean stopGeneration path.
     * Template: users/{userId}/conversations/{conversationId}/stopGeneration
     */
    const val CONVERSATION_STOP_GENERATION_PATH: String = "users/{userId}/conversations/{conversationId}/stopGeneration"
}
