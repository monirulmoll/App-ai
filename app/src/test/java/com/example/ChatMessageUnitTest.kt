package com.example

import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
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
        assertFalse(TranslatorHelper.isCreatorOrIdentityQuery("What is the capital of France?", "What is the capital of France?"))
        assertFalse(TranslatorHelper.isCreatorOrIdentityQuery("google ki", "What is google?"))
        assertFalse(TranslatorHelper.isCreatorOrIdentityQuery("moonshot ki", "What is moonshot?"))
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
}
