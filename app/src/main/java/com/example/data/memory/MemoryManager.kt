package com.example.data.memory

import android.content.Context
import com.example.data.local.LocalChatPreferences
import com.example.data.model.UserMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class MemoryManager(context: Context, private val prefs: LocalChatPreferences) {

    private val _memories = MutableStateFlow<List<UserMemory>>(emptyList())
    val memories: StateFlow<List<UserMemory>> = _memories.asStateFlow()

    init {
        loadMemories()
    }

    private fun loadMemories() {
        val saved = prefs.getMemories()
        if (saved.isEmpty()) {
            // Seed default initial memories
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
            prefs.saveMemories(initial)
        } else {
            _memories.value = saved
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
        prefs.saveMemories(updated)
        return newMem
    }

    fun deleteMemory(memoryId: String) {
        val updated = _memories.value.filter { it.id != memoryId }
        _memories.value = updated
        prefs.saveMemories(updated)
    }

    fun toggleMemory(memoryId: String) {
        val updated = _memories.value.map {
            if (it.id == memoryId) it.copy(isEnabled = !it.isEnabled) else it
        }
        _memories.value = updated
        prefs.saveMemories(updated)
    }

    fun clearAllMemories() {
        _memories.value = emptyList()
        prefs.saveMemories(emptyList())
    }

    /**
     * Privacy-conscious context builder: returns relevant active memories.
     * Does not blindly dump everything; filters by enabled state and relevant keywords.
     */
    fun buildContextForPrompt(prompt: String, isMemoryEnabled: Boolean): String {
        if (!isMemoryEnabled) return ""
        val active = _memories.value.filter { it.isEnabled }
        if (active.isEmpty()) return ""

        val lowerPrompt = prompt.lowercase()
        // If query asks about identity or preferences, include matching memories
        val relevant = active.filter { memory ->
            val lowerMem = memory.content.lowercase()
            // High priority memory always included: creator identity
            if (lowerMem.contains("rohit")) return@filter true

            // Match words
            val words = lowerPrompt.split("\\s+".toRegex()).filter { it.length > 3 }
            words.any { word -> lowerMem.contains(word) } || memory.category == "preference"
        }.take(3)

        if (relevant.isEmpty()) return ""

        return relevant.joinToString("; ") { it.content }
    }
}
