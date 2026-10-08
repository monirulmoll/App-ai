package com.example.data.model

data class LlmSettings(
    val firebaseUrl: String = DEFAULT_FIREBASE_URL,
    val provider: String = "Gemma",
    val modelName: String = "Gemma 2 9B",
    val themeMode: String = "SYSTEM", // "SYSTEM", "DARK", "LIGHT"
    val responseTimeoutSeconds: Int = 45,
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f
) {
    companion object {
        const val DEFAULT_FIREBASE_URL = "https://ussr-error-404-default-rtdb.firebaseio.com"

        val AVAILABLE_MODELS = listOf(
            LlmModelOption("Qwen", "Qwen 0.5B Q4", "Ultra-fast lightweight quantized model (0.5B Q4)"),
            LlmModelOption("Qwen", "Qwen 2.5 0.5B", "Compact high-speed edge reasoning model"),
            LlmModelOption("Qwen", "Qwen 2.5 72B", "Leading open-weights multilingual LLM"),
            LlmModelOption("Qwen", "Qwen 2.5 Coder", "Optimized for programming & code review"),
            LlmModelOption("Gemma", "Gemma 2 9B", "Google lightweight state-of-the-art model"),
            LlmModelOption("Gemma", "Gemma 2 27B", "High-accuracy Google open model"),
            LlmModelOption("Llama", "Llama 3.3 70B", "Flagship open LLM for deep reasoning"),
            LlmModelOption("Llama", "Llama 3.1 8B", "Fast and responsive conversational model"),
            LlmModelOption("Mistral", "Mistral Large", "Flagship European intelligence reasoning"),
            LlmModelOption("Mistral", "Mistral Nemo", "Compact high-efficiency assistant"),
            LlmModelOption("DeepSeek", "DeepSeek V3", "Advanced reasoning and dialogue"),
            LlmModelOption("DeepSeek", "DeepSeek R1", "Chain of thought logic and coding")
        )
    }
}

data class LlmModelOption(
    val provider: String,
    val modelName: String,
    val description: String
)
