package com.example.data.memory

import android.content.Context
import com.example.data.local.LocalChatPreferences
import com.example.data.model.UserMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Enterprise-grade User Memory Manager for Gemo AI.
 * Strictly guarantees that memories are loaded, stored, and managed per authenticated UID.
 * Zero cross-user memory leakage.
 */
class MemoryManager(context: Context, private val prefs: LocalChatPreferences) {

    private var activeUserId: String = ""
    private val _memories = MutableStateFlow<List<UserMemory>>(emptyList())
    val memories: StateFlow<List<UserMemory>> = _memories.asStateFlow()

    init {
        val initialUid = prefs.getAuthenticatedUserId()
        if (!initialUid.isNullOrBlank()) {
            switchUser(initialUid)
        }
    }

    /**
     * Switches the active memory context to the newly authenticated user UID.
     */
    fun switchUser(userId: String) {
        val clean = userId.trim()
        activeUserId = clean
        if (clean.isBlank() || clean == "unauthenticated") {
            _memories.value = emptyList()
            return
        }

        val saved = prefs.getMemories(clean)
        if (saved.isEmpty()) {
            // Seed default initial memories for this specific user
            val initial = listOf(
                UserMemory(
                    id = "mem_creator",
                    content = "Gemo AI's creator and maker is Rohit.",
                    category = "fact",
                    isEnabled = true
                ),
                UserMemory(
                    id = "mem_pref_lang",
                    content = "User prefers polite, intelligent, and context-aware responses in their typed language.",
                    category = "preference",
                    isEnabled = true
                )
            )
            _memories.value = initial
            prefs.saveMemories(initial, clean)
        } else {
            _memories.value = saved
        }
    }

    /**
     * Clears all in-memory items on logout or account switch.
     */
    fun clearAllMemories() {
        _memories.value = emptyList()
        if (activeUserId.isNotBlank() && activeUserId != "unauthenticated") {
            prefs.saveMemories(emptyList(), activeUserId)
        }
    }

    fun syncFromCloud(userId: String, cloudMemories: List<UserMemory>) {
        if (userId.isBlank() || cloudMemories.isEmpty()) return
        val currentLocal = prefs.getMemories(userId)
        val merged = (cloudMemories + currentLocal).distinctBy { it.id }
        prefs.saveMemories(merged, userId)
        if (userId == activeUserId) {
            _memories.value = merged
        }
    }

    fun addMemory(content: String, category: String = "custom"): UserMemory {
        val clean = content.trim()
        val newMem = UserMemory(
            id = "mem_" + UUID.randomUUID().toString().take(8),
            content = clean,
            category = category,
            createdAt = System.currentTimeMillis(),
            isEnabled = true
        )
        val updated = listOf(newMem) + _memories.value
        _memories.value = updated
        if (activeUserId.isNotBlank() && activeUserId != "unauthenticated") {
            prefs.saveMemories(updated, activeUserId)
        }
        return newMem
    }

    fun deleteMemory(memoryId: String) {
        val updated = _memories.value.filter { it.id != memoryId }
        _memories.value = updated
        if (activeUserId.isNotBlank() && activeUserId != "unauthenticated") {
            prefs.saveMemories(updated, activeUserId)
        }
    }

    fun toggleMemory(memoryId: String) {
        val updated = _memories.value.map {
            if (it.id == memoryId) it.copy(isEnabled = !it.isEnabled) else it
        }
        _memories.value = updated
        if (activeUserId.isNotBlank() && activeUserId != "unauthenticated") {
            prefs.saveMemories(updated, activeUserId)
        }
    }

    /**
     * Privacy-conscious context builder: returns relevant active memories.
     * Only uses memories belonging to the currently active authenticated user.
     */
    fun buildContextForPrompt(prompt: String, isMemoryEnabled: Boolean): String {
        if (!isMemoryEnabled) return ""
        val active = _memories.value.filter { it.isEnabled }
        if (active.isEmpty()) return ""

        val lowerPrompt = prompt.lowercase()
        val relevant = active.filter { memory ->
            val lowerMem = memory.content.lowercase()
            if (lowerMem.contains("rohit")) return@filter true

            val words = lowerPrompt.split("\\s+".toRegex()).filter { it.length > 3 }
            words.any { word -> lowerMem.contains(word) } || memory.category == "preference"
        }.take(3)

        if (relevant.isEmpty()) return ""
        return relevant.joinToString("; ") { it.content }
    }
}
