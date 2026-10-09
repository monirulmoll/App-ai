package com.example.data.model

data class ModelSwitchResponse(
    val success: Boolean = false,
    val requested: String = "",
    val active: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class LlmSettings(
    val firebaseUrl: String = DEFAULT_FIREBASE_URL,
    val provider: String = "Qwen",
    val modelName: String = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
    val themeMode: String = "SYSTEM", // "SYSTEM", "DARK", "LIGHT"
    val responseTimeoutSeconds: Int = 45,
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f,
    // Next-Gen additions
    val agentModeEnabled: Boolean = false,
    val memoryEnabled: Boolean = true,
    val visionEnabled: Boolean = true,
    val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    val speechInputLanguage: String = "auto",
    val translatorEnabled: Boolean = true
) {
    companion object {
        const val DEFAULT_FIREBASE_URL = "https://ussr-error-404-default-rtdb.firebaseio.com"
        const val DEFAULT_SYSTEM_INSTRUCTION = "You are Gemo AI, an intelligent AI created by Rohit. Whenever introducing yourself or asked who created or made you, proudly state that your maker and creator is Rohit."

        val AVAILABLE_MODELS: List<LlmModelOption> = emptyList()

        val DEFAULT_POPULAR_MODELS: List<LlmModelOption> = listOf(
            LlmModelOption(
                provider = "Qwen",
                modelName = "Qwen 2.5 1.5B Instruct",
                ggufFilename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                description = "Fast, intelligent, multilingual, code-aware",
                capabilities = listOf("chat", "code", "agent"),
                sizeHint = "1.5B"
            ),
            LlmModelOption(
                provider = "Qwen",
                modelName = "Qwen 2.5 Coder 1.5B",
                ggufFilename = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
                description = "Specialized for code generation, debugging, and file workspace",
                capabilities = listOf("chat", "code"),
                sizeHint = "1.5B"
            ),
            LlmModelOption(
                provider = "Llama",
                modelName = "Llama 3.2 1B Instruct",
                ggufFilename = "llama-3.2-1b-instruct-q4_k_m.gguf",
                description = "Ultra-fast lightweight conversational model by Meta",
                capabilities = listOf("chat", "agent"),
                sizeHint = "1B"
            ),
            LlmModelOption(
                provider = "DeepSeek",
                modelName = "DeepSeek R1 Distill Qwen 1.5B",
                ggufFilename = "deepseek-r1-distill-qwen-1.5b-q4_k_m.gguf",
                description = "Deep reasoning model with step-by-step thinking",
                capabilities = listOf("chat", "reasoning"),
                sizeHint = "1.5B"
            ),
            LlmModelOption(
                provider = "Gemma",
                modelName = "Gemma 2 2B IT",
                ggufFilename = "gemma-2-2b-it-q4_k_m.gguf",
                description = "High quality compact model by Google",
                capabilities = listOf("chat"),
                sizeHint = "2B"
            )
        )

        fun ensureGgufFilename(nameOrFilename: String): String {
            val clean = nameOrFilename.trim()
            if (clean.isEmpty()) return "model.gguf"
            if (clean.endsWith(".gguf", ignoreCase = true)) {
                return clean
            }
            if (clean.equals("Qwen 2.5 1.5B Instruct", ignoreCase = true)) {
                return "qwen2.5-1.5b-instruct-q4_k_m.gguf"
            }
            return "${clean.lowercase().replace(" ", "-")}.gguf"
        }

        fun detectProvider(filenameOrName: String): String {
            val low = filenameOrName.lowercase()
            return when {
                low.contains("qwen") -> "Qwen"
                low.contains("llama") -> "Llama"
                low.contains("gemma") -> "Gemma"
                low.contains("deepseek") -> "DeepSeek"
                low.contains("mistral") -> "Mistral"
                low.contains("phi") -> "Phi"
                else -> "Custom"
            }
        }

        fun getDisplayName(filenameOrName: String): String {
            val clean = filenameOrName.trim()
            return if (clean.endsWith(".gguf", ignoreCase = true)) {
                clean.removeSuffix(".gguf")
            } else {
                clean
            }
        }
    }
}

data class LlmModelOption(
    val provider: String,
    val modelName: String,
    val ggufFilename: String,
    val description: String = "",
    val capabilities: List<String> = listOf("chat"),
    val sizeHint: String = ""
) {
    val displayName: String get() = modelName
    val filename: String get() = ggufFilename
    val isCustom: Boolean get() = provider.equals("Custom", ignoreCase = true)
    val sizeLabel: String get() = sizeHint.ifEmpty { "GGUF" }
    val quantization: String get() = if (ggufFilename.contains("-q")) ggufFilename.substringAfterLast("-").removeSuffix(".gguf").uppercase() else "Q4_K_M"
}
