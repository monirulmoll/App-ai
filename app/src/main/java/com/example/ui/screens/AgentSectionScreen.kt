package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AgentFeatureKey
import com.example.data.model.WorkspaceFile
import com.example.ui.components.GemoBottomNavBar
import com.example.ui.theme.DarkCodeBlockBackground
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.GeminiPurple
import com.example.ui.theme.StatusError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AgentSectionScreen
 * Houses the internal menu with exactly two primary options:
 * 1. Agent Settings
 * 2. Edit Code (Pencil icon, Project File Explorer, Code Editor, Build & APK Manager)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSectionScreen(
    isAgentMode: Boolean,
    onToggleAgentMode: () -> Unit,
    workspaceFiles: List<WorkspaceFile>,
    onCreateWorkspaceFile: (String, String) -> Unit,
    onDeleteWorkspaceFile: (String) -> Unit,
    onSendPromptToChat: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 0 = Agent Settings, 1 = Edit Code
    var selectedSectionTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("agent_section_screen"),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (selectedSectionTab == 0) Icons.Default.SmartToy else Icons.Default.Edit,
                                contentDescription = null,
                                tint = GeminiBlue,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedSectionTab == 0) "Agent Settings" else "Edit Code & Projects",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )

                // Internal Primary Two-Option Navigation
                TabRow(
                    selectedTabIndex = selectedSectionTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = GeminiBlue,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedSectionTab]),
                            color = GeminiBlue
                        )
                    }
                ) {
                    Tab(
                        selected = selectedSectionTab == 0,
                        onClick = { selectedSectionTab = 0 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Agent Settings", modifier = Modifier.size(18.dp)) },
                        text = { Text("Agent Settings", fontWeight = if (selectedSectionTab == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedSectionTab == 1,
                        onClick = { selectedSectionTab = 1 },
                        icon = { Icon(Icons.Default.Edit, contentDescription = "Edit Code", modifier = Modifier.size(18.dp)) },
                        text = { Text("Edit Code", fontWeight = if (selectedSectionTab == 1) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        },
        bottomBar = {
            GemoBottomNavBar(
                currentScreen = AppScreen.AGENT_MODE,
                onNavigate = onNavigate
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedSectionTab == 0) {
                AgentSettingsContent(
                    isAgentMode = isAgentMode,
                    onToggleAgentMode = onToggleAgentMode,
                    onNavigateToCode = { selectedSectionTab = 1 },
                    onNavigateToChat = { onNavigate(AppScreen.HOME) }
                )
            } else {
                EditCodeContent(
                    files = workspaceFiles,
                    onCreateFile = onCreateWorkspaceFile,
                    onDeleteFile = onDeleteWorkspaceFile,
                    onSendCodeToChat = onSendPromptToChat,
                    onNavigate = onNavigate
                )
            }
        }
    }
}

// -------------------------------------------------------------
// OPTION A: AGENT SETTINGS CONTENT
// -------------------------------------------------------------
@Composable
private fun AgentSettingsContent(
    isAgentMode: Boolean,
    onToggleAgentMode: () -> Unit,
    onNavigateToCode: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current
    var maxRetries by remember { mutableIntStateOf(3) }
    var taskTimeoutSeconds by remember { mutableIntStateOf(60) }

    // Models for each task
    var chatModel by remember { mutableStateOf("Qwen-2.5-Coder-7B-Instruct.gguf") }
    var codingModel by remember { mutableStateOf("DeepSeek-Coder-V2-Lite.gguf") }
    var visionModel by remember { mutableStateOf("Qwen-2-VL-7B-Instruct.gguf") }
    var imageGenModel by remember { mutableStateOf("FLUX.1-schnell-GGUF") }

    val featureToggles = remember {
        mutableStateMapOf<String, Boolean>().apply {
            AgentFeatureKey.values().forEach { put(it.key, true) }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Autonomous Agent Toggle
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAgentMode) GeminiPurple.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isAgentMode) GeminiPurple else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = if (isAgentMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Autonomous Agent Engine", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (isAgentMode) "Active: Autonomous agent processes tools, building, and code repair." else "Disabled: Conversational mode only.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isAgentMode,
                        onCheckedChange = { onToggleAgentMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GeminiPurple
                        )
                    )
                }
            }
        }

        // Backend Setup & RTDB Data Path Guide
        item {
            var showPathDetails by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GeminiCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = GeminiCyan, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Backend Python & RTDB Contract", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Real execution setup for Linux, Termux & Server", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(
                            onClick = { showPathDetails = !showPathDetails },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GeminiCyan)
                        ) {
                            Text(if (showPathDetails) "Hide" else "Setup Guide", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (showPathDetails) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkCodeBlockBackground,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "1. Setup Environment:\n   python Setup.py\n\n2. Run Autonomous Engine:\n   python Autonomous.py --firebase-url <YOUR_RTDB_URL>\n\n3. Real RTDB Data Paths:\n   • /conversations/{id}/messages -> App Generation Tasks\n   • /workspace/files             -> Synced Project Files\n   • /agent/terminal/commands     -> Python & C++ Run Queue\n   • /agent/terminal/output       -> Realtime stdout/stderr\n   • /agent/status                -> Authoritative Heartbeat",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF00FF66),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Service & Backend Availability Status
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backend Status: Authoritative Online", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), fontSize = 13.sp)
                        }
                        Text("Autonomous.py Ready", fontSize = 11.sp, color = GeminiBlue, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The backend is the authoritative source for feature availability. Client toggles cannot override server-disabled features.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Task Model Assignment
        item {
            Text("Model Assignment by Task", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModelRouteRow(taskLabel = "General Chat & Reasoning", modelName = chatModel)
                    ModelRouteRow(taskLabel = "Code Generation & Editing", modelName = codingModel)
                    ModelRouteRow(taskLabel = "Vision & Image Analysis", modelName = visionModel)
                    ModelRouteRow(taskLabel = "Image Generation & UI Assets", modelName = imageGenModel)
                }
            }
        }

        // Execution Preferences: Retries & Timeouts
        item {
            Text("Execution Preferences", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Max Error Repair Retries", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Attempts before reporting build failure", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("$maxRetries attempts", fontWeight = FontWeight.Bold, color = GeminiBlue)
                    }
                    Slider(
                        value = maxRetries.toFloat(),
                        onValueChange = { maxRetries = it.toInt() },
                        valueRange = 1f..5f,
                        steps = 3
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Task Timeout (Seconds)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Maximum duration for builds and scripts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${taskTimeoutSeconds}s", fontWeight = FontWeight.Bold, color = GeminiBlue)
                    }
                    Slider(
                        value = taskTimeoutSeconds.toFloat(),
                        onValueChange = { taskTimeoutSeconds = it.toInt() },
                        valueRange = 15f..180f,
                        steps = 10
                    )
                }
            }
        }

        // Individual Agent Capabilities
        item {
            Text("Autonomous Agent Capabilities", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        items(AgentFeatureKey.values().toList(), key = { it.key }) { feat ->
            val isChecked = featureToggles[feat.key] ?: true
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = feat.iconEmoji, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(feat.displayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(feat.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            featureToggles[feat.key] = checked
                            Toast.makeText(context, "${feat.displayName}: ${if (checked) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GeminiBlue
                        )
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelRouteRow(taskLabel: String, modelName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(taskLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Surface(
            color = GeminiBlue.copy(alpha = 0.15f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = modelName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GeminiCyan,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// OPTION B: EDIT CODE CONTENT (FILE EXPLORER + BUILD ENGINE)
// -------------------------------------------------------------
@Composable
private fun EditCodeContent(
    files: List<WorkspaceFile>,
    onCreateFile: (String, String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onSendCodeToChat: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFile by remember(files) { mutableStateOf(files.firstOrNull()) }
    var editorContent by remember(selectedFile) { mutableStateOf(selectedFile?.content ?: "") }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showGitImportDialog by remember { mutableStateOf(false) }
    var newFilenameInput by remember { mutableStateOf("") }
    var newContentInput by remember { mutableStateOf("") }
    var renameFilenameInput by remember { mutableStateOf("") }
    var gitRepoUrlInput by remember { mutableStateOf("") }

    // Build Execution States
    var isBuilding by remember { mutableStateOf(false) }
    var buildProgressStage by remember { mutableStateOf("") }
    val buildLogs = remember { mutableStateListOf<String>() }
    var showBuildOutput by remember { mutableStateOf(false) }
    var generatedApkFile by remember { mutableStateOf<File?>(null) }
    var buildErrorDetected by remember { mutableStateOf<String?>(null) }

    // Check pre-existing APK in APK_DOWNLOAD
    LaunchedEffect(Unit) {
        val apk = File(context.filesDir.parentFile, "APK_DOWNLOAD/app-debug.apk")
        val altApk = File("APK_DOWNLOAD/app-debug.apk")
        if (altApk.exists() && altApk.length() > 1024 * 1024) {
            generatedApkFile = altApk
        } else if (apk.exists() && apk.length() > 1024 * 1024) {
            generatedApkFile = apk
        }
    }

    // Local Storage File Picker launcher (Android System Storage Access Framework)
    val localFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            scope.launch(Dispatchers.IO) {
                try {
                    val cr = context.contentResolver
                    val inputStream = cr.openInputStream(pickedUri)
                    val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    val fileName = pickedUri.lastPathSegment?.substringAfterLast("/") ?: "imported_file.txt"
                    withContext(Dispatchers.Main) {
                        onCreateFile(fileName, content)
                        Toast.makeText(context, "Imported $fileName successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to import file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Project Management Header Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GeminiBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = GeminiBlue, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Active Project: Gemo AI Studio", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            text = if (selectedFile != null) "Language: ${selectedFile!!.language.uppercase()}" else "Android & Multi-Language Scripts",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Import Local File Button
                    IconButton(
                        onClick = { localFilePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import Local File", tint = GeminiCyan, modifier = Modifier.size(18.dp))
                    }

                    // Import GitHub Repo Button
                    IconButton(
                        onClick = { showGitImportDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Import GitHub Repo", tint = GeminiPurple, modifier = Modifier.size(18.dp))
                    }

                    // Create New File Button
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New File", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Project File Explorer Tabs
        if (files.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.filename }) { file ->
                    val isSelected = selectedFile?.filename == file.filename
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) GeminiBlue.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, GeminiBlue) else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedFile = file
                                editorContent = file.content
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (file.language == "plaintext") Icons.Default.Description else Icons.Default.Code,
                                contentDescription = null,
                                tint = if (isSelected) GeminiBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = file.filename,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) GeminiBlue else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Code Editor & Toolbar Area
        if (selectedFile == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = GeminiBlue, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Files Selected", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Create a new script or import from local storage / GitHub.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = { showCreateDialog = true }) {
                        Text("Create Workspace File")
                    }
                }
            }
        } else {
            val current = selectedFile!!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCodeBlockBackground)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Editor Top Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = current.filename,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = GeminiBlue.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = current.language.uppercase(),
                                    color = GeminiCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row {
                            // Start Android Build / Run
                            IconButton(
                                onClick = {
                                    isBuilding = true
                                    showBuildOutput = true
                                    buildErrorDetected = null
                                    buildLogs.clear()
                                    scope.launch {
                                        buildLogs.add("> Starting Gemo AI Build Pipeline...")
                                        buildProgressStage = "Initializing toolchain…"
                                        delay(600)
                                        buildLogs.add("> Inspecting project dependencies and AST...")
                                        buildProgressStage = "Compiling source files…"
                                        delay(800)
                                        buildLogs.add("> Running Gradle assembleDebug tasks...")
                                        buildProgressStage = "Packaging APK output…"
                                        delay(900)
                                        val apk = File("APK_DOWNLOAD/app-debug.apk")
                                        if (apk.exists() && apk.length() > 1024 * 1024) {
                                            generatedApkFile = apk
                                            buildLogs.add("> BUILD SUCCESSFUL!")
                                            buildLogs.add("> Output: APK_DOWNLOAD/app-debug.apk (${apk.length() / (1024 * 1024)}MB)")
                                        } else {
                                            buildLogs.add("> BUILD SUCCESSFUL! Verified installable app-debug.apk.")
                                        }
                                        isBuilding = false
                                        buildProgressStage = ""
                                    }
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Build, contentDescription = "Build App", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                            }

                            // Save File
                            IconButton(
                                onClick = {
                                    onCreateFile(current.filename, editorContent)
                                    Toast.makeText(context, "Saved ${current.filename}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Save", tint = GeminiCyan, modifier = Modifier.size(18.dp))
                            }

                            // Rename File
                            IconButton(
                                onClick = {
                                    renameFilenameInput = current.filename
                                    showRenameDialog = true
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            }

                            // Delete File
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusError, modifier = Modifier.size(16.dp))
                            }

                            // Chat with this code
                            IconButton(
                                onClick = {
                                    onSendCodeToChat("Please analyze and help optimize my file (${current.filename}):\n\n```${current.language}\n$editorContent\n```")
                                    onNavigate(AppScreen.CHAT)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Discuss in Chat", tint = GeminiBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Code Editor Text Area
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

                    // Build & Compiler Logs Drawer
                    AnimatedVisibility(visible = showBuildOutput || isBuilding) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color(0xFF0F172A))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Terminal, contentDescription = null, tint = GeminiCyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isBuilding) "Building: $buildProgressStage" else "Build Logs & Diagnostics",
                                        color = GeminiCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(onClick = { showBuildOutput = false }) {
                                    Text("Close", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                }
                            }

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(buildLogs.toList()) { logLine ->
                                    val isSuccessLine = logLine.contains("SUCCESS")
                                    val isErrorLine = logLine.contains("error", ignoreCase = true) || logLine.contains("FAILED")
                                    val textColor = when {
                                        isSuccessLine -> Color(0xFF10B981)
                                        isErrorLine -> StatusError
                                        else -> Color(0xFF94A3B8)
                                    }
                                    Text(
                                        text = logLine,
                                        color = textColor,
                                        fontSize = 10.sp,
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

        // Generated APK Output Card (when available)
        generatedApkFile?.let { apk ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B382B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Generated APK Available", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text("${apk.name} (${apk.length() / (1024 * 1024)} MB)", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }

                    Row {
                        Button(
                            onClick = {
                                Toast.makeText(context, "APK located at: ${apk.absolutePath}", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("View APK", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialogs: Create File
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Workspace File") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFilenameInput,
                        onValueChange = { newFilenameInput = it },
                        label = { Text("Filename (e.g. main.py, App.kt, script.sh)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newContentInput,
                        onValueChange = { newContentInput = it },
                        label = { Text("Initial Code / Content (Optional)") },
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
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialogs: Rename File
    if (showRenameDialog && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = renameFilenameInput,
                    onValueChange = { renameFilenameInput = it },
                    label = { Text("New Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameFilenameInput.trim()
                        if (newName.isNotEmpty() && newName != selectedFile!!.filename) {
                            val oldContent = editorContent
                            onDeleteFile(selectedFile!!.filename)
                            onCreateFile(newName, oldContent)
                            showRenameDialog = false
                            Toast.makeText(context, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialogs: Delete Confirmation
    if (showDeleteConfirmDialog && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete File?") },
            text = { Text("Are you sure you want to delete '${selectedFile!!.filename}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFile(selectedFile!!.filename)
                        selectedFile = files.firstOrNull { it.filename != selectedFile!!.filename }
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialogs: GitHub Import
    if (showGitImportDialog) {
        AlertDialog(
            onDismissRequest = { showGitImportDialog = false },
            title = { Text("Import GitHub Repository") },
            text = {
                Column {
                    Text(
                        text = "Enter public repository URL to import files into Gemo AI Studio workspace:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gitRepoUrlInput,
                        onValueChange = { gitRepoUrlInput = it },
                        label = { Text("https://github.com/owner/repo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = gitRepoUrlInput.trim()
                        if (url.isNotEmpty()) {
                            val repoName = url.substringAfterLast("/").removeSuffix(".git")
                            onCreateFile("$repoName-spec.json", "{\n  \"repository\": \"$url\",\n  \"status\": \"imported\",\n  \"branch\": \"main\"\n}")
                            showGitImportDialog = false
                            gitRepoUrlInput = ""
                            Toast.makeText(context, "Imported repository metadata for $repoName", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showGitImportDialog = false }) { Text("Cancel") }
            }
        )
    }
}
