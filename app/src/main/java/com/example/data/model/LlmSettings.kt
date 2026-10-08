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
    val provider: String = "Custom",
    val modelName: String = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
    val themeMode: String = "SYSTEM", // "SYSTEM", "DARK", "LIGHT"
    val responseTimeoutSeconds: Int = 45,
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f
) {
    companion object {
        const val DEFAULT_FIREBASE_URL = "https://ussr-error-404-default-rtdb.firebaseio.com"

        // All pre-added models removed as requested. Models are now dynamically added and saved by the user.
        val AVAILABLE_MODELS: List<LlmModelOption> = emptyList()

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
    val description: String = ""
)
