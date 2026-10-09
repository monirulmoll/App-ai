package com.example

import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.LlmModelOption
import com.example.data.model.LlmSettings
import com.example.data.translator.TranslatorHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatMessageUnitTest {
    @Test
    fun testChatMessageUserStatus() {
        val userMsg = ChatMessage(
            messageId = "1",
            conversationId = "conv1",
            sender = "user",
            text = "Hello world",
            status = "sent",
            requestId = "req_123"
        )
        assertTrue(userMsg.isUser)
        assertFalse(userMsg.isAi)
        assertFalse(userMsg.isGenerating)
        assertFalse(userMsg.isError)
    }

    @Test
    fun testChatMessageAiGenerating() {
        val aiMsg = ChatMessage(
            messageId = "2",
            conversationId = "conv1",
            sender = "ai",
            text = "",
            status = "generating",
            requestId = "req_123"
        )
        assertTrue(aiMsg.isAi)
        assertFalse(aiMsg.isUser)
        assertTrue(aiMsg.isGenerating)
    }

    @Test
    fun testChatMessageSerialization() {
        val map = mapOf(
            "messageId" to "m1",
            "conversationId" to "c1",
            "sender" to "ai",
            "text" to "Hello!",
            "timestamp" to 123456L,
            "status" to "completed",
            "requestId" to "r1",
            "model" to "Gemma 2 9B"
        )
        val msg = ChatMessage.fromMap("m1", map)
        assertEquals("m1", msg.messageId)
        assertEquals("Hello!", msg.text)
        assertEquals("completed", msg.status)
        assertEquals("Gemma 2 9B", msg.model)
    }

    @Test
    fun testDefaultFirebaseUrl() {
        assertEquals("https://ussr-error-404-default-rtdb.firebaseio.com", LlmSettings.DEFAULT_FIREBASE_URL)
    }

    @Test
    fun testConversationDefaults() {
        val conv = Conversation(id = "conv_1", title = "Kotlin Flow")
        assertEquals("conv_1", conv.id)
        assertEquals("Kotlin Flow", conv.title)
    }

    @Test
    fun testCreatorQueryDetection() {
        assertTrue(TranslatorHelper.isCreatorOrIdentityQuery("tumhe kisne banaya", "who made you"))
        assertTrue(TranslatorHelper.isCreatorOrIdentityQuery("kisne banaya tumhe", "who created you"))
        assertTrue(TranslatorHelper.isCreatorOrIdentityQuery("tomake ke banieche", "who is your maker"))
        assertTrue(TranslatorHelper.isCreatorOrIdentityQuery("tumake ke baniyeche", "who made you"))
        assertTrue(TranslatorHelper.isCreatorOrIdentityQuery("usse kisne banaya", "who made that"))
        assertTrue(TranslatorHelper.isCreatorOrIdentityQuery("usse kisne baniya", "who made it"))
        assertTrue(TranslatorHelper.isCreatorOrIdentityQuery("isse kisne banaya", "who made this"))
        assertTrue(TranslatorHelper.isCreatorOrIdentityQuery("ye kisne banaya", "who made this"))
        assertTrue(TranslatorHelper.isCreatorOrIdentityQuery("eta ke baniyeche", "who made this"))
        assertTrue(TranslatorHelper.isCreatorOrIdentityQuery("rohit kaun hai", "who is rohit"))
        assertTrue(TranslatorHelper.isCreatorOrIdentityQuery("rohit ke", "who is rohit"))
        assertFalse(TranslatorHelper.isCreatorOrIdentityQuery("What is the capital of France?", "What is the capital of France?"))
        assertFalse(TranslatorHelper.isCreatorOrIdentityQuery("google ki", "What is google?"))
        assertFalse(TranslatorHelper.isCreatorOrIdentityQuery("moonshot ki", "What is moonshot?"))
    }

    @Test
    fun testUsseKisneBanayaReturnsRohitIdentity() {
        val replyHindi = TranslatorHelper.sanitizeAndFormatReply(
            rawAiResponse = "Some random text from server",
            rawPrompt = "usse kisne banaya",
            englishPrompt = "who made that",
            targetLang = "hi",
            isLatinScript = true
        )
        assertEquals("Isse Rohit ne banaya hai.", replyHindi)

        val replyBaniya = TranslatorHelper.sanitizeAndFormatReply(
            rawAiResponse = "Some random text from server",
            rawPrompt = "usse kisne baniya",
            englishPrompt = "who made it",
            targetLang = "hi",
            isLatinScript = true
        )
        assertEquals("Isse Rohit ne banaya hai.", replyBaniya)

        val replyTumhe = TranslatorHelper.sanitizeAndFormatReply(
            rawAiResponse = "Some random text from server",
            rawPrompt = "tumhe kisne banaya",
            englishPrompt = "who made you",
            targetLang = "hi",
            isLatinScript = true
        )
        assertEquals("Mujhe Rohit ne banaya hai.", replyTumhe)

        val replyBengali = TranslatorHelper.sanitizeAndFormatReply(
            rawAiResponse = "Some random text from server",
            rawPrompt = "tomake ke baniyeche",
            englishPrompt = "who made you",
            targetLang = "bn",
            isLatinScript = true
        )
        assertEquals("Amake Rohit baniyeche.", replyBengali)
    }

    @Test
    fun testSanitizeCompetitorAttributionReplacedWithRohit() {
        val competitorResp = "I am a large language model created by Anthropic. I am designed to assist users in generating text."
        val sanitized = TranslatorHelper.sanitizeAndFormatReply(
            rawAiResponse = competitorResp,
            rawPrompt = "tumake ke baniyeche",
            englishPrompt = "who made you",
            targetLang = "bn",
            isLatinScript = true
        )
        assertTrue(sanitized.contains("Rohit"))
        assertFalse(sanitized.contains("Anthropic"))
    }

    @Test
    fun testFactualCompanyMentionsNotReplacedWithRohit() {
        // Bug fix verification: factual answers about Google, Microsoft, OpenAI should never turn into Rohit!
        val factualResp = "Google is a multinational technology company focusing on search engines, cloud computing, and software. Microsoft and Google have headquarters in multiple cities."
        val sanitized = TranslatorHelper.sanitizeAndFormatReply(
            rawAiResponse = factualResp,
            rawPrompt = "google ki",
            englishPrompt = "What is google?",
            targetLang = "bn",
            isLatinScript = true
        )
        assertTrue(sanitized.contains("Google"))
        assertTrue(sanitized.contains("Microsoft"))
        assertFalse(sanitized.startsWith("Rohit is a"))
    }

    @Test
    fun testBanglishQuestionTranslationPatterns() = runBlocking {
        val moonshotResult = TranslatorHelper.translateToEnglish("moonshot ki")
        assertEquals("What is moonshot?", moonshotResult.translatedEnglish)
        assertEquals("bn", moonshotResult.detectedLanguage)
        assertTrue(moonshotResult.isLatinScript)

        val googleResult = TranslatorHelper.translateToEnglish("google ki")
        assertEquals("What is google?", googleResult.translatedEnglish)
        assertEquals("bn", googleResult.detectedLanguage)

        val dubaiResult = TranslatorHelper.translateToEnglish("dubai ki")
        assertEquals("What is dubai?", dubaiResult.translatedEnglish)

        val creatorResult = TranslatorHelper.translateToEnglish("tumake ke baniyeche")
        assertEquals("who made you", creatorResult.translatedEnglish)
    }

    @Test
    fun testScriptNormalizationForBengali() {
        // Devanagari "सहायता" (U+0938 U+0939 U+093E U+092F U+0924 U+093E)
        val devanagariText = "\u0938\u0939\u093E\u092F\u0924\u093E"
        val normalized = TranslatorHelper.normalizeScriptForLanguage(devanagariText, "bn")
        // Should convert to Bengali characters (U+09B8 U+09B9 U+09BE U+09AF U+09A4 U+09BE)
        val expectedBengali = "\u09B8\u09B9\u09BE\u09AF\u09A4\u09BE"
        assertEquals(expectedBengali, normalized)
    }

    @Test
    fun testModelRequestExactGgufFilenameFormat() {
        // /model/request requires exact .gguf filename
        val target = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        val formatted = LlmSettings.ensureGgufFilename("Qwen 2.5 1.5B Instruct")
        assertEquals(target, formatted)

        val alreadyGguf = LlmSettings.ensureGgufFilename("qwen2.5-1.5b-instruct-q4_k_m.gguf")
        assertEquals(target, alreadyGguf)

        val customGguf = LlmSettings.ensureGgufFilename("custom-model-q4")
        assertEquals("custom-model-q4.gguf", customGguf)

        // Payload structure for /model/request
        val requestPayload = mapOf("name" to formatted)
        assertEquals("qwen2.5-1.5b-instruct-q4_k_m.gguf", requestPayload["name"])
    }

    @Test
    fun testModelResponseSuccessAndFailure() {
        // Test parsing /model/response success payload
        val successPayload = com.example.data.model.ModelSwitchResponse(
            success = true,
            requested = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            active = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            message = "Model switched successfully."
        )
        assertTrue(successPayload.success)
        assertEquals("qwen2.5-1.5b-instruct-q4_k_m.gguf", successPayload.requested)
        assertEquals("qwen2.5-1.5b-instruct-q4_k_m.gguf", successPayload.active)
        assertEquals("Model switched successfully.", successPayload.message)

        // Test parsing /model/response failure payload
        val failurePayload = com.example.data.model.ModelSwitchResponse(
            success = false,
            requested = "model-name.gguf",
            active = "qwen2.5-0.5b-instruct-q4_0.gguf",
            message = "Model not available: model-name.gguf"
        )
        assertFalse(failurePayload.success)
        assertEquals("model-name.gguf", failurePayload.requested)
        assertEquals("qwen2.5-0.5b-instruct-q4_0.gguf", failurePayload.active)
        assertEquals("Model not available: model-name.gguf", failurePayload.message)
    }

    @Test
    fun testPureBengaliVsLatinScriptResponses() {
        // Pure Bengali script query -> pure Bengali script response
        val pureBengaliReply = TranslatorHelper.sanitizeAndFormatReply(
            rawAiResponse = "Some server reply",
            rawPrompt = "তোমাকে কে বানিয়েছে",
            englishPrompt = "who made you",
            targetLang = "bn",
            isLatinScript = false
        )
        assertEquals("আমাকে রোহিত বানিয়েছে।", pureBengaliReply)

        // Latin Banglish query -> Latin Banglish response
        val latinBanglishReply = TranslatorHelper.sanitizeAndFormatReply(
            rawAiResponse = "Some server reply",
            rawPrompt = "tomake ke baniyeche",
            englishPrompt = "who made you",
            targetLang = "bn",
            isLatinScript = true
        )
        assertEquals("Amake Rohit baniyeche.", latinBanglishReply)

        // "usse kisne baniya" in Hinglish -> Latin Hinglish response
        val latinHinglishReply = TranslatorHelper.sanitizeAndFormatReply(
            rawAiResponse = "Some server reply",
            rawPrompt = "usse kisne baniya",
            englishPrompt = "who made it",
            targetLang = "hi",
            isLatinScript = true
        )
        assertEquals("Isse Rohit ne banaya hai.", latinHinglishReply)

        // Pure Hindi query -> Devanagari Hindi response
        val pureHindiReply = TranslatorHelper.sanitizeAndFormatReply(
            rawAiResponse = "Some server reply",
            rawPrompt = "तुम्हें किसने बनाया",
            englishPrompt = "who made you",
            targetLang = "hi",
            isLatinScript = false
        )
        assertEquals("मुझे रोहित ने बनाया है।", pureHindiReply)
    }

    @Test
    fun testStopPayloadStructure() {
        val stopPayload = mapOf(
            "stop" to true,
            "requestId" to "req-12345",
            "conversationId" to "conv-67890",
            "timestamp" to 1728394857000L
        )
        assertEquals(true, stopPayload["stop"])
        assertEquals("req-12345", stopPayload["requestId"])
        assertEquals("conv-67890", stopPayload["conversationId"])
    }

    @Test
    fun testPreAddedModelsRemoved() {
        // Verify all 13 pre-added models are removed from the pre-loaded list
        assertTrue(LlmSettings.AVAILABLE_MODELS.isEmpty())
    }

    @Test
    fun testEnsureGgufFilename() {
        assertEquals("qwen2.5-1.5b.gguf", LlmSettings.ensureGgufFilename("qwen2.5-1.5b"))
        assertEquals("llama-3.2-3b.gguf", LlmSettings.ensureGgufFilename("llama-3.2-3b.gguf"))
        assertEquals("mistral-7b.gguf", LlmSettings.ensureGgufFilename("mistral-7b"))
    }

    @Test
    fun testDetectProvider() {
        assertEquals("Qwen", LlmSettings.detectProvider("qwen2.5-1.5b-instruct.gguf"))
        assertEquals("Llama", LlmSettings.detectProvider("llama-3.2-3b-instruct.gguf"))
        assertEquals("Gemma", LlmSettings.detectProvider("gemma-2-9b-it.gguf"))
        assertEquals("DeepSeek", LlmSettings.detectProvider("deepseek-r1-distill.gguf"))
        assertEquals("Custom", LlmSettings.detectProvider("my-custom-model.gguf"))
    }

    @Test
    fun testTranslatorEnabledByDefault() {
        val settings = LlmSettings()
        assertTrue(settings.translatorEnabled)
    }

    @Test
    fun testHuPhoneticMappingHindi() = runBlocking {
        val result = TranslatorHelper.translateToEnglish("hu")
        assertEquals("hi", result.detectedLanguage)
        assertTrue(result.isLatinScript)
    }
}
