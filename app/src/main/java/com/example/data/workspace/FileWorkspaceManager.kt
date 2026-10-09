package com.example.data.workspace

import android.content.Context
import com.example.data.model.WorkspaceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileWorkspaceManager(private val context: Context) {

    val workspaceDir: File = File(context.filesDir, "gemo_workspace").apply {
        if (!exists()) mkdirs()
    }

    private val _files = MutableStateFlow<List<WorkspaceFile>>(emptyList())
    val files: StateFlow<List<WorkspaceFile>> = _files.asStateFlow()

    init {
        cleanupDemoFilesIfPresent()
        refreshFiles()
    }

    /**
     * Ensures NO demo, fake, or placeholder files are pre-loaded into the workspace.
     */
    private fun cleanupDemoFilesIfPresent() {
        val demoFiles = listOf("welcome.py", "project_notes.md")
        demoFiles.forEach { name ->
            val target = File(workspaceDir, name)
            if (target.exists()) {
                val text = try { target.readText() } catch (_: Exception) { "" }
                if (text.contains("Welcome to Gemo AI Code Workspace") || text.contains("Gemo AI Next-Gen Workspace")) {
                    target.delete()
                }
            }
        }
    }

    fun refreshFiles() {
        val list = mutableListOf<WorkspaceFile>()
        workspaceDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                val preview = try {
                    file.readText()
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
        _files.value = list.sortedBy { it.filename.lowercase() }
    }

    private fun sanitizeFilename(rawName: String): String {
        val clean = rawName.trim().replace("\\", "/").substringAfterLast("/")
        return clean.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { "file.txt" }
    }

    fun getSafeFile(filename: String): File {
        val safeName = sanitizeFilename(filename)
        val target = File(workspaceDir, safeName)
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

    suspend fun importFile(filename: String, bytes: ByteArray): Result<WorkspaceFile> = withContext(Dispatchers.IO) {
        try {
            val target = getSafeFile(filename)
            target.writeBytes(bytes)
            val content = try { target.readText() } catch (_: Exception) { "[Binary file]" }
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

    suspend fun renameFile(oldFilename: String, newFilename: String): Result<WorkspaceFile> = withContext(Dispatchers.IO) {
        try {
            val oldFile = getSafeFile(oldFilename)
            if (!oldFile.exists()) {
                return@withContext Result.failure(NoSuchFileException(oldFile, null, "File does not exist: $oldFilename"))
            }
            val newFile = getSafeFile(newFilename)
            if (newFile.exists() && newFile.canonicalPath != oldFile.canonicalPath) {
                return@withContext Result.failure(IllegalArgumentException("Target filename already exists: $newFilename"))
            }
            val content = oldFile.readText()
            val renamed = oldFile.renameTo(newFile)
            if (!renamed) {
                newFile.writeText(content)
                oldFile.delete()
            }
            refreshFiles()
            Result.success(
                WorkspaceFile(
                    filename = newFile.name,
                    extension = newFile.extension,
                    sizeBytes = newFile.length(),
                    lastModified = newFile.lastModified(),
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

    /**
     * Exports all workspace files as a ZIP archive for whole-project download.
     */
    suspend fun exportProjectZip(outputZipFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            outputZipFile.parentFile?.mkdirs()
            ZipOutputStream(FileOutputStream(outputZipFile)).use { zos ->
                val allFiles = workspaceDir.listFiles() ?: emptyArray()
                for (file in allFiles) {
                    if (file.isFile) {
                        val entry = ZipEntry(file.name)
                        entry.time = file.lastModified()
                        zos.putNextEntry(entry)
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
            Result.success(outputZipFile)
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
