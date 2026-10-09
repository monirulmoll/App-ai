package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.WorkspaceFile
import com.example.ui.components.GemoBottomNavBar
import com.example.ui.theme.DarkCodeBlockBackground
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.GeminiPurple
import com.example.ui.theme.StatusError
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeWorkspaceScreen(
    files: List<WorkspaceFile>,
    onCreateFile: (String, String) -> Unit,
    onRenameFile: (String, String) -> Unit = { _, _ -> },
    onDeleteFile: (String) -> Unit,
    onImportFile: (String, ByteArray) -> Unit = { _, _ -> },
    onExportProjectZip: (File, (Result<File>) -> Unit) -> Unit = { _, _ -> },
    onSendCodeToChat: (String) -> Unit,
    onRunInTerminal: (filename: String, code: String) -> Unit = { _, _ -> },
    onNavigate: (AppScreen) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFile by remember(files) {
        mutableStateOf(files.firstOrNull())
    }
    var editorContent by remember(selectedFile) {
        mutableStateOf(selectedFile?.content ?: "")
    }

    // Sidebar Visibility
    var showFileSidebar by remember { mutableStateOf(true) }
    var fileSearchQuery by remember { mutableStateOf("") }

    // Dialogs
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFilenameInput by remember { mutableStateOf("") }
    var newContentInput by remember { mutableStateOf("") }

    var fileToRename by remember { mutableStateOf<WorkspaceFile?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var fileToDelete by remember { mutableStateOf<WorkspaceFile?>(null) }

    // File Import Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                val filename = uri.lastPathSegment?.substringAfterLast("/")?.ifEmpty { "imported_file.txt" } ?: "imported_file.txt"
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() } ?: ByteArray(0)
                onImportFile(filename, bytes)
                Toast.makeText(context, "Imported $filename successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to import file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportAndShareZip() {
        val cacheZip = File(context.cacheDir, "gemo_project_${System.currentTimeMillis()}.zip")
        onExportProjectZip(cacheZip) { result ->
            result.onSuccess { zipFile ->
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", zipFile)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Gemo AI Project Export")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Download / Share Project ZIP"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Exported to: ${zipFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
            }.onFailure { err ->
                Toast.makeText(context, "Failed to export project: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareSingleFile(file: WorkspaceFile) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, file.content)
            putExtra(Intent.EXTRA_SUBJECT, file.filename)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Download / Share ${file.filename}"))
    }

    val filteredFiles = remember(files, fileSearchQuery) {
        if (fileSearchQuery.isBlank()) files
        else files.filter { it.filename.contains(fileSearchQuery, ignoreCase = true) }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("code_workspace_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = GeminiCyan, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Code Workspace", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showFileSidebar = !showFileSidebar },
                        modifier = Modifier.testTag("toggle_files_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Toggle Files",
                            tint = if (showFileSidebar) GeminiCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { exportAndShareZip() },
                        modifier = Modifier.testTag("download_zip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = "Download Project ZIP",
                            tint = GeminiCyan
                        )
                    }
                }
            )
        },
        bottomBar = {
            GemoBottomNavBar(
                currentScreen = AppScreen.CODE_WORKSPACE,
                onNavigate = onNavigate
            )
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // -------------------------------------------------------------
            // LEFT SIDEBAR: File Tree & Project Structure
            // -------------------------------------------------------------
            AnimatedVisibility(visible = showFileSidebar) {
                Surface(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        // Header Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "PROJECT FILES",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = GeminiCyan
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${files.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = { showCreateDialog = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "New File", tint = GeminiCyan, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = "Import File", tint = GeminiCyan, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { exportAndShareZip() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = "Download ZIP", tint = Color(0xFF00E676), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Search Bar
                        OutlinedTextField(
                            value = fileSearchQuery,
                            onValueChange = { fileSearchQuery = it },
                            placeholder = { Text("Filter files…", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .padding(vertical = 2.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = GeminiCyan,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (filteredFiles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No files yet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = { showCreateDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = ButtonDefaults.TextButtonContentPadding
                                    ) {
                                        Text("+ Add File", fontSize = 11.sp)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredFiles, key = { it.filename }) { file ->
                                    val isSelected = selectedFile?.filename == file.filename
                                    var showMenu by remember { mutableStateOf(false) }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) GeminiCyan.copy(alpha = 0.16f) else Color.Transparent,
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, GeminiCyan.copy(alpha = 0.5f)) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedFile = file
                                                editorContent = file.content
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // File Icon
                                            val (iconColor, extBadge) = getFileVisuals(file.extension)
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(iconColor.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = extBadge,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = iconColor
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.filename,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${file.sizeBytes} B",
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Box {
                                                IconButton(
                                                    onClick = { showMenu = true },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = "File actions", modifier = Modifier.size(16.dp))
                                                }
                                                DropdownMenu(
                                                    expanded = showMenu,
                                                    onDismissRequest = { showMenu = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Rename") },
                                                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                        onClick = {
                                                            showMenu = false
                                                            fileToRename = file
                                                            renameInput = file.filename
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Download") },
                                                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                        onClick = {
                                                            showMenu = false
                                                            shareSingleFile(file)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Run in Terminal") },
                                                        leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = GeminiCyan, modifier = Modifier.size(16.dp)) },
                                                        onClick = {
                                                            showMenu = false
                                                            onRunInTerminal(file.filename, file.content)
                                                            onNavigate(AppScreen.TERMINAL)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Delete", color = StatusError) },
                                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp)) },
                                                        onClick = {
                                                            showMenu = false
                                                            fileToDelete = file
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Action: Download entire zip
                        Button(
                            onClick = { exportAndShareZip() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GeminiCyan)
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download Project ZIP", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // RIGHT PANEL: Code Editor Area
            // -------------------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                if (selectedFile == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = GeminiCyan, modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Welcome to Code Workspace",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Ask Gemo AI in Chat to build an app (e.g. 'Make a hello world app'),\nor create files using the sidebar to edit and run scripts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onNavigate(AppScreen.CHAT) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Chat")
                                }
                                Button(
                                    onClick = { showCreateDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GeminiCyan)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New File", color = Color.Black)
                                }
                            }
                        }
                    }
                } else {
                    val curFile = selectedFile!!
                    // Editor Top Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = curFile.filename,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Run in Terminal Button
                            Button(
                                onClick = {
                                    onCreateFile(curFile.filename, editorContent)
                                    onRunInTerminal(curFile.filename, editorContent)
                                    onNavigate(AppScreen.TERMINAL)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Run", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // Save Button
                            Button(
                                onClick = {
                                    onCreateFile(curFile.filename, editorContent)
                                    Toast.makeText(context, "Saved ${curFile.filename}", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", fontSize = 12.sp)
                            }

                            // Send to Chat Button
                            IconButton(
                                onClick = {
                                    onSendCodeToChat("Please analyze and review this file `${curFile.filename}`:\n```${curFile.extension}\n$editorContent\n```")
                                    onNavigate(AppScreen.CHAT)
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Send to Chat", tint = GeminiCyan)
                            }
                        }
                    }

                    // Code Editor Text Field with Monospace and Dark Background
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCodeBlockBackground)
                    ) {
                        TextField(
                            value = editorContent,
                            onValueChange = { editorContent = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("code_editor_field"),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = Color(0xFFE2E8F0)
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = GeminiCyan
                            )
                        )
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // DIALOGS: Create, Rename, Delete
    // -------------------------------------------------------------
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New File") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFilenameInput,
                        onValueChange = { newFilenameInput = it },
                        label = { Text("Filename (e.g. main.py, hello.cpp, app.js)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newContentInput,
                        onValueChange = { newContentInput = it },
                        label = { Text("Initial Code (Optional)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
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

    fileToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New file name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameInput.trim()
                        if (newName.isNotEmpty() && newName != target.filename) {
                            onRenameFile(target.filename, newName)
                        }
                        fileToRename = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    fileToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File") },
            text = { Text("Are you sure you want to delete '${target.filename}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFile(target.filename)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun getFileVisuals(ext: String): Pair<Color, String> {
    return when (ext.lowercase()) {
        "py" -> Pair(Color(0xFF00E676), "PY")
        "cpp", "c", "cc", "h", "hpp" -> Pair(Color(0xFF00D2D3), "C++")
        "kt", "kts" -> Pair(Color(0xFF7C4DFF), "KT")
        "java" -> Pair(Color(0xFFFF9100), "JV")
        "js" -> Pair(Color(0xFFFFD600), "JS")
        "ts" -> Pair(Color(0xFF2979FF), "TS")
        "html" -> Pair(Color(0xFFFF6D00), "HT")
        "css" -> Pair(Color(0xFF00B0FF), "CS")
        "json" -> Pair(Color(0xFFE040FB), "JSON")
        "md" -> Pair(Color(0xFF90CAF9), "MD")
        "sh", "bash" -> Pair(Color(0xFF69F0AE), "SH")
        else -> Pair(Color(0xFF9E9E9E), ext.take(2).uppercase().ifEmpty { "TX" })
    }
}
