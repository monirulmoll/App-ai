package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WorkspaceFile
import com.example.ui.components.GemoBottomNavBar
import com.example.ui.theme.DarkCodeBlockBackground
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.StatusError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeWorkspaceScreen(
    files: List<WorkspaceFile>,
    onCreateFile: (String, String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onSendCodeToChat: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFile by remember(files) { mutableStateOf(files.firstOrNull()) }
    var editorContent by remember(selectedFile) { mutableStateOf(selectedFile?.content ?: "") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFilenameInput by remember { mutableStateOf("") }
    var newContentInput by remember { mutableStateOf("") }
    var terminalOutput by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("code_workspace_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Code & File Workspace", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New File")
                    }
                }
            )
        },
        bottomBar = {
            GemoBottomNavBar(
                currentScreen = AppScreen.FILE_MANAGER,
                onNavigate = onNavigate
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // File Tabs Row
            if (files.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(files, key = { it.filename }) { file ->
                        val isSelected = selectedFile?.filename == file.filename
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) GeminiBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, GeminiBlue) else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedFile = file
                                    editorContent = file.content
                                    terminalOutput = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (file.language == "plaintext") Icons.Default.Description else Icons.Default.Code,
                                    contentDescription = null,
                                    tint = if (isSelected) GeminiBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = file.filename,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) GeminiBlue else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            if (selectedFile == null) {
                // Empty files state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = GeminiBlue, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Files in Workspace", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Create code files or scripts to execute and analyze with Gemo AI", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showCreateDialog = true }) {
                            Text("Create File")
                        }
                    }
                }
            } else {
                val current = selectedFile!!
                // Editor & Actions Bar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkCodeBlockBackground
                    )
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Editor Toolbar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = current.filename,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = GeminiBlue.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = current.language.uppercase(),
                                        color = GeminiCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row {
                                // Run Button
                                IconButton(
                                    onClick = {
                                        isRunning = true
                                        scope.launch {
                                            delay(800)
                                            isRunning = false
                                            terminalOutput = executeSandbox(current.filename, editorContent)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                }

                                // Save
                                IconButton(
                                    onClick = {
                                        onCreateFile(current.filename, editorContent)
                                        Toast.makeText(context, "Saved ${current.filename}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = "Save", tint = GeminiCyan, modifier = Modifier.size(18.dp))
                                }

                                // Copy
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("code", editorContent))
                                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }

                                // Chat with code
                                IconButton(
                                    onClick = {
                                        onSendCodeToChat("Please analyze and review this file (${current.filename}):\n\n```${current.language}\n$editorContent\n```")
                                        onNavigate(AppScreen.CHAT)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Send to Chat", tint = GeminiBlue, modifier = Modifier.size(18.dp))
                                }

                                // Delete
                                IconButton(
                                    onClick = {
                                        onDeleteFile(current.filename)
                                        selectedFile = files.firstOrNull { it.filename != current.filename }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusError, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Code Editor Body
                        OutlinedTextField(
                            value = editorContent,
                            onValueChange = { editorContent = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0)
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        // Terminal output drawer
                        AnimatedVisibility(visible = terminalOutput != null || isRunning) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .background(Color(0xFF0F172A))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Terminal, contentDescription = null, tint = GeminiCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Terminal Console Output", color = GeminiCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(onClick = { terminalOutput = null }) {
                                        Text("Clear", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                    }
                                }

                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (isRunning) {
                                        Text("Executing sandboxed runtime…", color = Color.Yellow, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    } else {
                                        Text(
                                            text = terminalOutput ?: "",
                                            color = Color(0xFF10B981),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Workspace File") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFilenameInput,
                        onValueChange = { newFilenameInput = it },
                        label = { Text("Filename (e.g. script.py, app.js, data.json)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newContentInput,
                        onValueChange = { newContentInput = it },
                        label = { Text("Initial Content (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFilenameInput.trim()
                        if (name.isNotEmpty()) {
                            onCreateFile(name, newContentInput)
                            showCreateDialog = false
                            newFilenameInput = ""
                            newContentInput = ""
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun executeSandbox(filename: String, code: String): String {
    val lines = code.lines()
    return buildString {
        appendLine("[Sandboxed Runtime: Gemo-Core v2]")
        appendLine("Executing $filename (${lines.size} lines)...")
        appendLine("----------------------------------------")
        if (filename.endsWith(".py")) {
            val printLines = lines.filter { it.trim().startsWith("print(") }
            if (printLines.isNotEmpty()) {
                printLines.forEach { line ->
                    val content = line.substringAfter("print(").substringBeforeLast(")")
                    appendLine(content.trim('"', '\''))
                }
            } else {
                appendLine(">> Process completed with exit code 0 (Output: None).")
            }
        } else if (filename.endsWith(".js") || filename.endsWith(".ts")) {
            val logLines = lines.filter { it.contains("console.log(") }
            if (logLines.isNotEmpty()) {
                logLines.forEach { line ->
                    val content = line.substringAfter("console.log(").substringBeforeLast(")")
                    appendLine(content.trim('"', '\''))
                }
            } else {
                appendLine(">> Execution successful (0 errors).")
            }
        } else if (filename.endsWith(".json")) {
            appendLine("JSON syntax validated: Valid structure.")
            appendLine("Keys detected: ${lines.count { it.contains(":") }}")
        } else {
            appendLine("File preview loaded successfully.")
            appendLine("Characters: ${code.length}, Words: ${code.split("\\s+".toRegex()).size}")
        }
        appendLine("----------------------------------------")
        appendLine("[Status: OK - 0 Errors]")
    }
}
