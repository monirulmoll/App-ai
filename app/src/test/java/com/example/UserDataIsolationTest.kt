package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalChatPreferences
import com.example.data.memory.MemoryManager
import com.example.data.model.AuthSession
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.LlmSettings
import com.example.data.model.UserMemory
import com.example.data.workspace.FileWorkspaceManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserDataIsolationTest {

    private lateinit var context: Context
    private lateinit var prefs: LocalChatPreferences
    private lateinit var workspaceManager: FileWorkspaceManager
    private lateinit var memoryManager: MemoryManager

    private val userA_uid = "Uid_Account_A_MixedCase123"
    private val userB_uid = "Uid_Account_B_MixedCase456"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = LocalChatPreferences(context)
        prefs.clearAuthSession()
        workspaceManager = FileWorkspaceManager(context)
        memoryManager = MemoryManager(context, prefs)
    }

    @Test
    fun testCompleteUserDataIsolationBetweenAccountAAndAccountB() = runBlocking {
        // ====================================================================
        // STEP 1: Account A Signs In
        // ====================================================================
        val sessionA = AuthSession(
            sessionToken = "token_A",
            userId = userA_uid,
            username = "AliceA",
            email = "alice@example.com",
            expiresAt = System.currentTimeMillis() + 86400000L
        )
        prefs.saveAuthSession(sessionA)
        assertEquals(userA_uid, prefs.getAuthenticatedUserId())
        assertEquals("AliceA", prefs.getDisplayUsername(userA_uid))

        workspaceManager.switchUser(userA_uid)
        memoryManager.switchUser(userA_uid)

        // Account A creates 2 conversations with messages
        val convA1 = Conversation(id = "conv_a1", title = "Alice Project Alpha", userId = userA_uid)
        val convA2 = Conversation(id = "conv_a2", title = "Alice Confidential Chat", userId = userA_uid)
        prefs.saveConversations(listOf(convA1, convA2), userA_uid)
        prefs.setActiveConversationId(convA1.id, userA_uid)

        val msgA1 = ChatMessage(
            messageId = "msg_a1",
            conversationId = convA1.id,
            sender = "user",
            text = "Secret notes for Project Alpha",
            userId = userA_uid
        )
        prefs.saveMessages(convA1.id, listOf(msgA1), userA_uid)

        // Account A customizes settings
        val settingsA = LlmSettings(
            modelName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            temperature = 0.2f,
            systemInstruction = "You are Alice's private assistant."
        )
        prefs.saveSettings(settingsA, userA_uid)

        // Account A saves private memory
        memoryManager.addMemory("Alice loves quantum computing.", "preference")

        // Account A creates private workspace file
        workspaceManager.createOrUpdateFile("alice_notes.txt", "Top secret algorithm code.")

        // Verify Account A sees their data
        val convsForA = prefs.getConversations(userA_uid)
        assertEquals(2, convsForA.size)
        assertEquals(listOf("conv_a1", "conv_a2"), convsForA.map { it.id })
        assertEquals(1, prefs.getMessages(convA1.id, userA_uid).size)
        assertEquals("Secret notes for Project Alpha", prefs.getMessages(convA1.id, userA_uid)[0].text)
        assertEquals(0.2f, prefs.getSettings(userA_uid).temperature, 0.01f)
        assertEquals("You are Alice's private assistant.", prefs.getSettings(userA_uid).systemInstruction)
        assertTrue(workspaceManager.files.value.any { it.filename == "alice_notes.txt" })

        // ====================================================================
        // STEP 2: Account A Logs Out
        // ====================================================================
        prefs.clearAuthSession()
        prefs.setActiveConversationId(null, userA_uid)
        workspaceManager.clearActiveFiles()
        memoryManager.clearAllMemories()

        assertNull(prefs.getAuthenticatedUserId())
        assertTrue(workspaceManager.files.value.isEmpty())
        assertTrue(memoryManager.memories.value.isEmpty())

        // ====================================================================
        // STEP 3: Account B Signs In
        // ====================================================================
        val sessionB = AuthSession(
            sessionToken = "token_B",
            userId = userB_uid,
            username = "BobB",
            email = "bob@example.com",
            expiresAt = System.currentTimeMillis() + 86400000L
        )
        prefs.saveAuthSession(sessionB)
        assertEquals(userB_uid, prefs.getAuthenticatedUserId())
        assertEquals("BobB", prefs.getDisplayUsername(userB_uid))

        workspaceManager.switchUser(userB_uid)
        memoryManager.switchUser(userB_uid)

        // CONFIRM ACCOUNT A'S DATA NEVER APPEARS IN ACCOUNT B
        val convsForB = prefs.getConversations(userB_uid)
        assertTrue("Account B must not see Account A's conversations", convsForB.isEmpty())
        val msgsForB = prefs.getMessages("conv_a1", userB_uid)
        assertTrue("Account B must not see Account A's messages", msgsForB.isEmpty())
        assertNull(prefs.getActiveConversationId(userB_uid))

        // Confirm Account B gets default settings, not Account A's custom settings
        val settingsB = prefs.getSettings(userB_uid)
        assertEquals(0.7f, settingsB.temperature, 0.01f)
        assertNotEquals("You are Alice's private assistant.", settingsB.systemInstruction)

        // Confirm Account B does not have Account A's workspace files
        assertFalse(workspaceManager.files.value.any { it.filename == "alice_notes.txt" })

        // Confirm Account B does not have Alice's memory
        assertFalse(memoryManager.memories.value.any { it.content.contains("quantum computing") })

        // Account B creates their own conversations
        val convB1 = Conversation(id = "conv_b1", title = "Bob Recipe Chat", userId = userB_uid)
        prefs.saveConversations(listOf(convB1), userB_uid)
        val msgB1 = ChatMessage(
            messageId = "msg_b1",
            conversationId = convB1.id,
            sender = "user",
            text = "How to make pasta?",
            userId = userB_uid
        )
        prefs.saveMessages(convB1.id, listOf(msgB1), userB_uid)
        workspaceManager.createOrUpdateFile("bob_recipes.txt", "Delicious spaghetti recipe.")

        assertEquals(1, prefs.getConversations(userB_uid).size)
        assertEquals("conv_b1", prefs.getConversations(userB_uid)[0].id)
        assertTrue(workspaceManager.files.value.any { it.filename == "bob_recipes.txt" })

        // ====================================================================
        // STEP 4: Account B Logs Out
        // ====================================================================
        prefs.clearAuthSession()
        workspaceManager.clearActiveFiles()
        memoryManager.clearAllMemories()

        // ====================================================================
        // STEP 5: Account A Signs Back In
        // ====================================================================
        prefs.saveAuthSession(sessionA)
        assertEquals(userA_uid, prefs.getAuthenticatedUserId())
        workspaceManager.switchUser(userA_uid)
        memoryManager.switchUser(userA_uid)

        // CONFIRM ACCOUNT A'S DATA RETURNS INTACT
        val restoredConvsA = prefs.getConversations(userA_uid)
        assertEquals(2, restoredConvsA.size)
        assertTrue(restoredConvsA.any { it.id == "conv_a1" && it.title == "Alice Project Alpha" })
        assertTrue(restoredConvsA.any { it.id == "conv_a2" && it.title == "Alice Confidential Chat" })

        val restoredMsgsA = prefs.getMessages("conv_a1", userA_uid)
        assertEquals(1, restoredMsgsA.size)
        assertEquals("Secret notes for Project Alpha", restoredMsgsA[0].text)

        val restoredSettingsA = prefs.getSettings(userA_uid)
        assertEquals(0.2f, restoredSettingsA.temperature, 0.01f)
        assertEquals("You are Alice's private assistant.", restoredSettingsA.systemInstruction)

        // CONFIRM ACCOUNT B'S DATA NEVER APPEARS IN ACCOUNT A
        assertFalse(restoredConvsA.any { it.id == "conv_b1" })
        assertTrue(prefs.getMessages("conv_b1", userA_uid).isEmpty())
        assertFalse(workspaceManager.files.value.any { it.filename == "bob_recipes.txt" })
        assertTrue(workspaceManager.files.value.any { it.filename == "alice_notes.txt" })
    }

    @Test
    fun testUidCaseSensitivityPreserved() {
        val mixedCaseUid = "AbCd_EfGh_12345"
        val session = AuthSession(
            sessionToken = "token_mixed",
            userId = mixedCaseUid,
            username = "CaseTester"
        )
        prefs.saveAuthSession(session)
        assertEquals(mixedCaseUid, prefs.getAuthenticatedUserId())

        val conv = Conversation(id = "conv_case", title = "Case Test", userId = mixedCaseUid)
        prefs.saveConversations(listOf(conv), mixedCaseUid)

        val retrieved = prefs.getConversations(mixedCaseUid)
        assertEquals(1, retrieved.size)
        assertEquals(mixedCaseUid, retrieved[0].userId)
    }

    @Test
    fun testLegacyAmbiguousDataNeverAssignedAutomatically() {
        // Simulate legacy un-scoped key containing a foreign conversation
        val foreignUid = "user_foreign_999"
        val legacyJson = """[{"id":"conv_legacy","title":"Old Unknown Chat","userId":"$foreignUid"}]"""
        context.getSharedPreferences("gemo_ai_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("conversations_json", legacyJson)
            .apply()

        // New user registers
        val newUid = "uid_new_user_888"
        prefs.migrateLegacyLocalDataForUser(newUid)

        // Confirm new user gets 0 conversations and does NOT inherit foreign conversation
        val userConvs = prefs.getConversations(newUid)
        assertTrue("Ambiguous or foreign legacy data must never be assigned to new user", userConvs.isEmpty())
    }
}
