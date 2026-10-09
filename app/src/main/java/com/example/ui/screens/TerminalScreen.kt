package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.StatusError
import kotlinx.coroutines.launch

private data class TerminalLine(
    val type: LineType,
    val text: String
)

private enum class LineType {
    COMMAND, STDOUT, STDERR, INFO, SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    workspaceFiles: List<WorkspaceFile>,
    initialFileToRun: String? = null,
    onExecuteCommand: (command: String, language: String, filename: String?, code: String?, (stdout: String, stderr: String, exitCode: Int, status: String) -> Unit) -> String,
    onCancelCommand: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var commandInput by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var activeCommandId by remember { mutableStateOf<String?>(null) }
    var showBackendHelpDialog by remember { mutableStateOf(false) }
    val commandHistoryList = remember { mutableStateListOf<String>() }
    var historyCursor by remember { mutableStateOf(-1) }

    val terminalHistory = remember {
        mutableStateListOf<TerminalLine>().apply {
            add(TerminalLine(LineType.INFO, "Gemo AI Termux Execution Shell v2.4 [Android Sandbox]"))
            add(TerminalLine(LineType.INFO, "Working Directory: ~/workspace (/data/.../gemo_workspace)"))
            add(TerminalLine(LineType.INFO, "Backend Engine: Autonomous.py Linux Runner Compatible"))
            add(TerminalLine(LineType.INFO, "Full POSIX Shell: sh, ls, pwd, cat, mkdir, rm, touch, ps"))
            add(TerminalLine(LineType.INFO, "Python/C++: Native execution or Autonomous.py remote fallback"))
            add(TerminalLine(LineType.INFO, "--------------------------------------------------------"))
        }
    }

    fun appendLine(type: LineType, text: String) {
        terminalHistory.add(TerminalLine(type, text))
    }

    fun runCommand(cmd: String, lang: String = "shell", filename: String? = null, code: String? = null) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return

        if (!commandHistoryList.contains(trimmed)) {
            commandHistoryList.add(trimmed)
        }
        historyCursor = -1

        if (trimmed == "clear") {
            terminalHistory.clear()
            appendLine(LineType.INFO, "Gemo AI Termux Terminal Cleared.")
            commandInput = ""
            return
        }

        if (trimmed == "help") {
            appendLine(LineType.COMMAND, "$ $trimmed")
            appendLine(LineType.INFO, "Termux POSIX Shell Commands:")
            appendLine(LineType.INFO, "  ls / ls -la         : Real directory list in gemo_workspace")
            appendLine(LineType.INFO, "  pwd                 : Print current working directory")
            appendLine(LineType.INFO, "  cd <dir> / cd ..    : Change working directory")
            appendLine(LineType.INFO, "  cat <file>          : View file contents")
            appendLine(LineType.INFO, "  touch / mkdir / rm  : File and folder operations")
            appendLine(LineType.INFO, "  python3 <file.py>   : Run Python script (local or Autonomous.py)")
            appendLine(LineType.INFO, "  g++ <file.cpp>      : Compile and run C++")
            appendLine(LineType.INFO, "  whoami / uname -a   : System environment info")
            appendLine(LineType.INFO, "  clear               : Clear the screen")
            commandInput = ""
            return
        }

        // Determine language and code payload if targeting a workspace file
        var detectedLang = lang
        var detectedFilename = filename
        var detectedCode = code

        if (trimmed.startsWith("python ") || trimmed.startsWith("python3 ")) {
            val fname = trimmed.substringAfter("python3 ").substringAfter("python ").trim()
            detectedLang = "python"
            detectedFilename = fname
            val match = workspaceFiles.find { it.filename == fname }
            detectedCode = match?.content
        } else if (trimmed.startsWith("g++ ") || trimmed.contains(".cpp")) {
            val fname = trimmed.substringAfter("g++ ").substringBefore(" ").trim()
            detectedLang = "cpp"
            detectedFilename = fname
            val match = workspaceFiles.find { it.filename == fname }
            detectedCode = match?.content
        }

        appendLine(LineType.COMMAND, "rohit@gemo-termux:~/workspace$ $trimmed")
        isRunning = true

        val cmdId = onExecuteCommand(trimmed, detectedLang, detectedFilename, detectedCode) { stdout, stderr, exitCode, status ->
            if (stdout.isNotBlank()) {
                stdout.lines().filter { it.isNotEmpty() }.forEach { line ->
                    appendLine(LineType.STDOUT, line)
                }
            }
            if (stderr.isNotBlank()) {
                stderr.lines().filter { it.isNotEmpty() }.forEach { line ->
                    appendLine(LineType.STDERR, line)
                }
            }
            if (status == "COMPLETED" || status == "ERROR") {
                isRunning = false
                activeCommandId = null
                if (exitCode == 0) {
                    appendLine(LineType.SUCCESS, "[Process completed with exit code 0]")
                } else {
                    appendLine(LineType.STDERR, "[Process exited with code $exitCode]")
                }
            }
        }
        activeCommandId = cmdId
        commandInput = ""

        scope.launch {
            listState.animateScrollToItem(terminalHistory.size)
        }
    }

    LaunchedEffect(initialFileToRun) {
        if (!initialFileToRun.isNullOrBlank()) {
            val file = workspaceFiles.find { it.filename == initialFileToRun }
            if (file != null) {
                if (file.filename.endsWith(".py")) {
                    runCommand("python3 ${file.filename}", lang = "python", filename = file.filename, code = file.content)
                } else if (file.filename.endsWith(".cpp") || file.filename.endsWith(".c")) {
                    runCommand("g++ ${file.filename} -o main && ./main", lang = "cpp", filename = file.filename, code = file.content)
                } else {
                    runCommand("cat ${file.filename}", filename = file.filename, code = file.content)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("terminal_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF00FF66), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gemo Termux Terminal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showBackendHelpDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Setup Guide", tint = GeminiCyan)
                    }
                    IconButton(onClick = {
                        terminalHistory.clear()
                        appendLine(LineType.INFO, "Gemo Terminal Cleared.")
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0C1017),
                    titleContentColor = Color(0xFF00FF66),
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            GemoBottomNavBar(
                currentScreen = AppScreen.TERMINAL,
                onNavigate = onNavigate
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF090D14))
        ) {
            // Quick Command Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111722))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickChip(label = "ls -la", color = Color(0xFF00E676)) {
                    runCommand("ls -la")
                }

                QuickChip(label = "pwd", color = Color(0xFF00D2D3)) {
                    runCommand("pwd")
                }

                QuickChip(label = "whoami", color = Color(0xFF90CAF9)) {
                    runCommand("whoami")
                }

                // Find first python file if any
                val pyFile = workspaceFiles.firstOrNull { it.filename.endsWith(".py") }
                if (pyFile != null) {
                    QuickChip(label = "python3 ${pyFile.filename}", color = Color(0xFF00FF66)) {
                        runCommand("python3 ${pyFile.filename}", lang = "python", filename = pyFile.filename, code = pyFile.content)
                    }
                }

                // Find first cpp file if any
                val cppFile = workspaceFiles.firstOrNull { it.filename.endsWith(".cpp") || it.filename.endsWith(".c") }
                if (cppFile != null) {
                    QuickChip(label = "g++ ${cppFile.filename}", color = Color(0xFF00E5FF)) {
                        runCommand("g++ ${cppFile.filename} -o main && ./main", lang = "cpp", filename = cppFile.filename, code = cppFile.content)
                    }
                }

                QuickChip(label = "help", color = Color(0xFFFFB800)) {
                    runCommand("help")
                }

                QuickChip(label = "clear", color = Color(0xFFEF5350)) {
                    runCommand("clear")
                }
            }

            // Terminal Logs Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp)
            ) {
                items(terminalHistory) { line ->
                    val color = when (line.type) {
                        LineType.COMMAND -> Color(0xFF00E5FF)
                        LineType.STDOUT -> Color(0xFFE2E8F0)
                        LineType.STDERR -> StatusError
                        LineType.INFO -> Color(0xFF94A3B8)
                        LineType.SUCCESS -> Color(0xFF00FF66)
                    }
                    Text(
                        text = line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = color,
                        lineHeight = 16.sp
                    )
                }

                if (isRunning) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Color(0xFF00FF66),
                                strokeWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Executing in Termux sandbox…",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF00FF66)
                            )
                        }
                    }
                }
            }

            // Termux Accessory Key Bar (ESC, TAB, CTRL, ALT, -, /, |, ~, ↑, ↓)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TermuxKeyButton(label = "ESC") {
                    commandInput = ""
                    historyCursor = -1
                }
                TermuxKeyButton(label = "TAB") {
                    val parts = commandInput.split(" ")
                    val lastWord = parts.lastOrNull() ?: ""
                    if (lastWord.isNotEmpty()) {
                        val match = workspaceFiles.firstOrNull { it.filename.startsWith(lastWord, ignoreCase = true) }
                        if (match != null) {
                            val newCmd = (parts.dropLast(1) + match.filename).joinToString(" ")
                            commandInput = newCmd
                        }
                    } else {
                        commandInput += "    "
                    }
                }
                TermuxKeyButton(label = "CTRL") {
                    if (isRunning && activeCommandId != null) {
                        onCancelCommand(activeCommandId!!)
                        isRunning = false
                        appendLine(LineType.STDERR, "^C [Process killed]")
                    } else {
                        appendLine(LineType.INFO, "^C")
                    }
                }
                TermuxKeyButton(label = "ALT") {}
                TermuxKeyButton(label = "-") { commandInput += "-" }
                TermuxKeyButton(label = "/") { commandInput += "/" }
                TermuxKeyButton(label = "|") { commandInput += "|" }
                TermuxKeyButton(label = "~") { commandInput += "~" }
                TermuxKeyButton(label = "↑") {
                    if (commandHistoryList.isNotEmpty()) {
                        historyCursor = if (historyCursor == -1) {
                            commandHistoryList.size - 1
                        } else {
                            (historyCursor - 1).coerceAtLeast(0)
                        }
                        commandInput = commandHistoryList[historyCursor]
                    }
                }
                TermuxKeyButton(label = "↓") {
                    if (commandHistoryList.isNotEmpty() && historyCursor != -1) {
                        if (historyCursor < commandHistoryList.size - 1) {
                            historyCursor += 1
                            commandInput = commandHistoryList[historyCursor]
                        } else {
                            historyCursor = -1
                            commandInput = ""
                        }
                    }
                }
            }

            // Terminal Command Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0C1017),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$ ",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF66),
                        fontSize = 14.sp
                    )

                    TextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        placeholder = {
                            Text("e.g. python3 main.py, g++ main.cpp, ls, clear", fontSize = 12.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("terminal_input_field"),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color.White
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color(0xFF00FF66)
                        )
                    )

                    if (isRunning && activeCommandId != null) {
                        IconButton(
                            onClick = {
                                activeCommandId?.let { onCancelCommand(it) }
                                isRunning = false
                                appendLine(LineType.STDERR, "[Process cancelled by user]")
                            }
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = StatusError)
                        }
                    } else {
                        IconButton(
                            onClick = { runCommand(commandInput) },
                            enabled = commandInput.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Execute",
                                tint = if (commandInput.isNotBlank()) Color(0xFF00FF66) else Color.DarkGray
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBackendHelpDialog) {
        AlertDialog(
            onDismissRequest = { showBackendHelpDialog = false },
            title = { Text("Autonomous Backend & Terminal Setup") },
            text = {
                Column {
                    Text(
                        text = "To run Python, C++, and build apps in real hardware/termux:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Install dependencies on your PC or Termux:\n   python Setup.py\n\n2. Run the Autonomous Core with your Firebase RTDB URL:\n   python Autonomous.py --firebase-url <YOUR_URL>\n\n3. RTDB Schema Path:\n   • Commands: /agent/terminal/commands\n   • Output: /agent/terminal/output\n   • Files: /workspace/files\n\nWhen connected, every command executes natively with real stdout/stderr!",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showBackendHelpDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }
}

@Composable
private fun QuickChip(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TermuxKeyButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF21262D),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF30363D))
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFE6EDF3),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
        )
    }
}
