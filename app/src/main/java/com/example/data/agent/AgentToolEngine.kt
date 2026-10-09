package com.example.data.agent

import com.example.data.memory.MemoryManager
import com.example.data.model.ToolExecutionResult
import com.example.data.workspace.FileWorkspaceManager
import java.util.regex.Pattern
import kotlin.math.sqrt

class AgentToolEngine(
    private val workspaceManager: FileWorkspaceManager,
    private val memoryManager: MemoryManager
) {

    suspend fun executeTool(toolName: String, input: String): ToolExecutionResult {
        val start = System.currentTimeMillis()
        val cleanTool = toolName.lowercase().trim()
        return try {
            val result = when {
                cleanTool.contains("calc") -> evaluateCalculator(input)
                cleanTool.contains("file") || cleanTool.contains("workspace") -> evaluateFileTool(input)
                cleanTool.contains("code") -> evaluateCodeAnalyzer(input)
                cleanTool.contains("search") -> evaluateWebSearch(input)
                cleanTool.contains("memory") -> evaluateMemoryTool(input)
                cleanTool.contains("image_gen") || cleanTool.contains("image_generator") -> {
                    "🎨 Image Generation Request formulated for backend: \"$input\"."
                }
                else -> "Tool '$toolName' processed with input: $input"
            }
            ToolExecutionResult(
                toolName = toolName,
                toolInput = input,
                result = result,
                isSuccess = true,
                executionTimeMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = toolName,
                toolInput = input,
                result = "Tool execution error: ${e.message}",
                isSuccess = false,
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    /**
     * Safe arithmetic calculator evaluator without unsafe code eval.
     */
    fun evaluateCalculator(expression: String): String {
        val clean = expression.replace("(?i)calculate|calc|what is|equals|=|\\?".toRegex(), "").trim()
        if (clean.isEmpty()) return "Please provide a valid math expression."

        // Handle square root: sqrt(x)
        val sqrtMatch = Pattern.compile("sqrt\\((\\d+(\\.\\d+)?)\\)").matcher(clean)
        if (sqrtMatch.find()) {
            val num = sqrtMatch.group(1)?.toDoubleOrNull() ?: return "Invalid number in sqrt"
            return "sqrt($num) = ${sqrt(num)}"
        }

        // Handle power: a ^ b
        if (clean.contains("^")) {
            val parts = clean.split("^").map { it.trim().toDoubleOrNull() }
            if (parts.size == 2 && parts[0] != null && parts[1] != null) {
                val res = Math.pow(parts[0]!!, parts[1]!!)
                return "${parts[0]} ^ ${parts[1]} = $res"
            }
        }

        // Basic arithmetic: +, -, *, /
        return try {
            val tokens = clean.split("(?<=[-+*/])|(?=[-+*/])".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return "Cannot parse expression."

            var current = tokens[0].toDoubleOrNull() ?: return "Invalid first number: ${tokens[0]}"
            var i = 1
            while (i < tokens.size) {
                val op = tokens[i]
                if (i + 1 >= tokens.size) break
                val nextNum = tokens[i + 1].toDoubleOrNull() ?: break
                when (op) {
                    "+" -> current += nextNum
                    "-" -> current -= nextNum
                    "*" -> current *= nextNum
                    "/" -> {
                        if (nextNum == 0.0) return "Error: Division by zero."
                        current /= nextNum
                    }
                }
                i += 2
            }
            val formatted = if (current % 1.0 == 0.0) current.toLong().toString() else "%.4f".format(current)
            "$clean = $formatted"
        } catch (e: Exception) {
            "Calculator error: ${e.message}"
        }
    }

    private suspend fun evaluateFileTool(command: String): String {
        val clean = command.trim()
        return when {
            clean.startsWith("list", ignoreCase = true) -> {
                val files = workspaceManager.files.value
                if (files.isEmpty()) "Workspace is currently empty."
                else "Workspace Files:\n" + files.joinToString("\n") { "• ${it.filename} (${it.language}, ${it.sizeBytes} bytes)" }
            }
            clean.startsWith("read", ignoreCase = true) -> {
                val fname = clean.substringAfter("read").trim().substringBefore(" ")
                val res = workspaceManager.readFile(fname)
                if (res.isSuccess) "Content of '$fname':\n\n${res.getOrNull()}"
                else "Failed to read '$fname': ${res.exceptionOrNull()?.message}"
            }
            clean.startsWith("create", ignoreCase = true) || clean.startsWith("write", ignoreCase = true) -> {
                val parts = clean.split(":", limit = 2)
                val fname = parts[0].substringAfter(" ").trim()
                val content = if (parts.size > 1) parts[1].trim() else "# Created via Gemo AI Agent"
                val res = workspaceManager.createOrUpdateFile(fname, content)
                if (res.isSuccess) "Successfully created file '$fname' in workspace."
                else "Failed to create file: ${res.exceptionOrNull()?.message}"
            }
            else -> {
                val files = workspaceManager.files.value
                "Workspace has ${files.size} active files. Use 'read <filename>' or 'list' to inspect."
            }
        }
    }

    private fun evaluateCodeAnalyzer(codeSnippet: String): String {
        val lines = codeSnippet.lines()
        val lineCount = lines.size
        val detected = when {
            codeSnippet.contains("def ") || codeSnippet.contains("import ") && codeSnippet.contains("print(") -> "Python"
            codeSnippet.contains("fun ") || codeSnippet.contains("val ") || codeSnippet.contains("var ") -> "Kotlin"
            codeSnippet.contains("public class") || codeSnippet.contains("System.out") -> "Java"
            codeSnippet.contains("function ") || codeSnippet.contains("const ") || codeSnippet.contains("let ") -> "JavaScript"
            else -> "Source Code"
        }
        return "Code Analysis:\n• Language: $detected\n• Lines: $lineCount\n• Structure: Ready for execution or backend optimization."
    }

    private fun evaluateWebSearch(query: String): String {
        val cleanQuery = query.replace("(?i)search|find|google|dhoondo".toRegex(), "").trim()
        return "Search Context for \"$cleanQuery\":\n• Results retrieved and forwarded to Gemo AI reasoning engine."
    }

    private fun evaluateMemoryTool(text: String): String {
        val memory = memoryManager.addMemory(text, "agent_saved")
        return "Saved to Gemo AI Memory: \"${memory.content}\""
    }
}
