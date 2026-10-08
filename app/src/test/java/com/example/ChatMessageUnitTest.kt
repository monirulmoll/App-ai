package com.example

import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.LlmSettings
import com.example.data.translator.TranslatorHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertFalse(TranslatorHelper.isCreatorOrIdentityQuery("What is the capital of France?", "What is the capital of France?"))
    }

    @Test
    fun testSanitizeCompetitorAndEnforceRohit() {
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
}
