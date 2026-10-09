package com.example.data.terminal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class TerminalExecutionResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val workingDir: String
)

class NativeTerminalExecutor(private val context: Context) {

    val workspaceDir: File = File(context.filesDir, "gemo_workspace").apply {
        if (!exists()) mkdirs()
    }

    var currentDir: File = workspaceDir
        private set

    private var activeProcess: Process? = null

    fun killActiveProcess() {
        try {
            activeProcess?.destroyForcibly()
            activeProcess = null
        } catch (_: Exception) {}
    }

    fun getDisplayWorkingDir(): String {
        return if (currentDir == workspaceDir) {
            "~/workspace"
        } else if (currentDir.startsWith(workspaceDir)) {
            "~/workspace/" + currentDir.relativeTo(workspaceDir).path
        } else {
            currentDir.name
        }
    }

    suspend fun executeCommand(
        cmd: String,
        onLine: ((stdout: String?, stderr: String?) -> Unit)? = null
    ): TerminalExecutionResult = withContext(Dispatchers.IO) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) {
            return@withContext TerminalExecutionResult("", "", 0, getDisplayWorkingDir())
        }

        // Built-in 'pwd' handling
        if (trimmed == "pwd") {
            val out = currentDir.absolutePath + "\n"
            onLine?.invoke(out, null)
            return@withContext TerminalExecutionResult(out, "", 0, getDisplayWorkingDir())
        }

        // Built-in 'cd' handling
        if (trimmed == "cd" || trimmed == "cd ~") {
            currentDir = workspaceDir
            return@withContext TerminalExecutionResult("", "", 0, getDisplayWorkingDir())
        }
        if (trimmed.startsWith("cd ")) {
            val target = trimmed.removePrefix("cd ").trim()
            val dest = if (target.startsWith("/")) File(target) else File(currentDir, target)
            val canonical = try { dest.canonicalFile } catch (_: Exception) { dest }
            if (canonical.exists() && canonical.isDirectory) {
                currentDir = canonical
                return@withContext TerminalExecutionResult("", "", 0, getDisplayWorkingDir())
            } else {
                val err = "cd: no such file or directory: $target\n"
                onLine?.invoke(null, err)
                return@withContext TerminalExecutionResult("", err, 1, getDisplayWorkingDir())
            }
        }

        val stdoutBuffer = StringBuilder()
        val stderrBuffer = StringBuilder()
        var exitCode = 0

        try {
            val pb = ProcessBuilder("/system/bin/sh", "-c", trimmed)
            pb.directory(currentDir)
            val env = pb.environment()
            env["HOME"] = workspaceDir.absolutePath
            env["USER"] = "rohit"
            env["PATH"] = "/data/data/com.termux/files/usr/bin:/data/data/com.termux/files/usr/bin/applets:/system/bin:/system/xbin:/vendor/bin"
            env["TERM"] = "xterm-256color"
            env["SHELL"] = "/system/bin/sh"
            env["PWD"] = currentDir.absolutePath
            env["PS1"] = "rohit@gemo-termux:${getDisplayWorkingDir()}$ "

            val proc = pb.start()
            activeProcess = proc

            val outThread = Thread {
                try {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val lineWithNl = line + "\n"
                            stdoutBuffer.append(lineWithNl)
                            onLine?.invoke(lineWithNl, null)
                        }
                    }
                } catch (_: Exception) {}
            }

            val errThread = Thread {
                try {
                    BufferedReader(InputStreamReader(proc.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val lineWithNl = line + "\n"
                            stderrBuffer.append(lineWithNl)
                            onLine?.invoke(null, lineWithNl)
                        }
                    }
                } catch (_: Exception) {}
            }

            outThread.start()
            errThread.start()

            exitCode = proc.waitFor()
            outThread.join(2000)
            errThread.join(2000)
            activeProcess = null

        } catch (e: Exception) {
            val err = "sh: execution error: ${e.message}\n"
            stderrBuffer.append(err)
            onLine?.invoke(null, err)
            exitCode = 127
            activeProcess = null
        }

        TerminalExecutionResult(
            stdout = stdoutBuffer.toString(),
            stderr = stderrBuffer.toString(),
            exitCode = exitCode,
            workingDir = getDisplayWorkingDir()
        )
    }
}
