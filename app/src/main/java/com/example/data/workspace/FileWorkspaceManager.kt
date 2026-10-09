package com.example.data.workspace

import android.content.Context
import com.example.data.model.WorkspaceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class FileWorkspaceManager(private val context: Context) {

    private val workspaceDir: File = File(context.filesDir, "gemo_workspace").apply {
        if (!exists()) mkdirs()
    }

    private val _files = MutableStateFlow<List<WorkspaceFile>>(emptyList())
    val files: StateFlow<List<WorkspaceFile>> = _files.asStateFlow()

    init {
        seedInitialFilesIfEmpty()
        refreshFiles()
    }

    private fun seedInitialFilesIfEmpty() {
        val existing = workspaceDir.listFiles()
        if (existing.isNullOrEmpty()) {
            val welcomePy = File(workspaceDir, "welcome.py")
            welcomePy.writeText(
                """# Welcome to Gemo AI Code Workspace!
# Created by Rohit
# You can view, edit, run, and ask Gemo to analyze or modify files.

def greet_gemo(user_name="Friend"):
    message = f"Hello {user_name}! I am Gemo AI, created by Rohit."
    print(message)
    return message

if __name__ == "__main__":
    greet_gemo()
""".trimIndent()
            )

            val notesMd = File(workspaceDir, "project_notes.md")
            notesMd.writeText(
                """# Gemo AI Next-Gen Workspace
- Creator: Rohit
- Multi-Model Support: Active
- Vision & Image Understanding: Ready
- Sandboxed File Tools: Enabled
""".trimIndent()
            )
        }
    }

    fun refreshFiles() {
        val list = mutableListOf<WorkspaceFile>()
        workspaceDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                val preview = try {
                    file.readText().take(500)
                } catch (_: Exception) {
                    ""
                }
                list.add(
                    WorkspaceFile(
                        filename = file.name,
                        extension = ext,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        content = preview
                    )
                )
            }
        }
        _files.value = list.sortedByDescending { it.lastModified }
    }

    private fun sanitizeFilename(rawName: String): String {
        val clean = rawName.trim().replace("\\", "/").substringAfterLast("/")
        return clean.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { "file.txt" }
    }

    private fun getSafeFile(filename: String): File {
        val safeName = sanitizeFilename(filename)
        val target = File(workspaceDir, safeName)
        // Ensure path traversal defense
        val canonical = target.canonicalPath
        val baseCanonical = workspaceDir.canonicalPath
        if (!canonical.startsWith(baseCanonical)) {
            throw SecurityException("Path traversal attempt detected: $filename")
        }
        return target
    }

    suspend fun createOrUpdateFile(filename: String, content: String): Result<WorkspaceFile> = withContext(Dispatchers.IO) {
        try {
            val target = getSafeFile(filename)
            target.writeText(content)
            refreshFiles()
            Result.success(
                WorkspaceFile(
                    filename = target.name,
                    extension = target.extension,
                    sizeBytes = target.length(),
                    lastModified = target.lastModified(),
                    content = content
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readFile(filename: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val target = getSafeFile(filename)
            if (!target.exists()) {
                Result.failure(NoSuchFileException(target, null, "File does not exist: $filename"))
            } else {
                Result.success(target.readText())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(filename: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val target = getSafeFile(filename)
            val deleted = target.delete()
            refreshFiles()
            Result.success(deleted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun searchFiles(query: String): List<WorkspaceFile> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return _files.value
        return _files.value.filter {
            it.filename.lowercase().contains(q) || it.content.lowercase().contains(q)
        }
    }
}
