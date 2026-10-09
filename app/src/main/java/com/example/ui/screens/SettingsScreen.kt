package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionStatus
import com.example.data.model.LlmSettings
import com.example.ui.components.GemoBottomNavBar
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.GeminiPurple
import com.example.ui.theme.StatusError
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: LlmSettings,
    connectionStatus: ConnectionStatus,
    onSaveSettings: (LlmSettings) -> Unit,
    onCheckConnection: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var firebaseUrl by remember { mutableStateOf(currentSettings.firebaseUrl) }
    var provider by remember { mutableStateOf(currentSettings.provider) }
    var themeMode by remember { mutableStateOf(currentSettings.themeMode) }
    var timeoutSeconds by remember { mutableIntStateOf(currentSettings.responseTimeoutSeconds) }
    var maxTokens by remember { mutableIntStateOf(currentSettings.maxTokens) }
    var temperature by remember { mutableFloatStateOf(currentSettings.temperature) }
    var agentModeEnabled by remember { mutableStateOf(currentSettings.agentModeEnabled) }
    var memoryEnabled by remember { mutableStateOf(currentSettings.memoryEnabled) }
    var visionEnabled by remember { mutableStateOf(currentSettings.visionEnabled) }
    var systemInstruction by remember { mutableStateOf(currentSettings.systemInstruction) }
    var speechLang by remember { mutableStateOf(currentSettings.speechInputLanguage) }
    var translatorEnabled by remember { mutableStateOf(currentSettings.translatorEnabled) }

    val providers = listOf("Qwen", "Ollama", "vLLM", "Custom")
    val speechLangs = listOf("auto", "en", "hi", "es", "fr")

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Settings & Config", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val updated = currentSettings.copy(
                                firebaseUrl = firebaseUrl.trim(),
                                provider = provider,
                                themeMode = themeMode,
                                responseTimeoutSeconds = timeoutSeconds,
                                maxTokens = maxTokens,
                                temperature = (temperature * 100).roundToInt() / 100f,
                                agentModeEnabled = agentModeEnabled,
                                memoryEnabled = memoryEnabled,
                                visionEnabled = visionEnabled,
                                systemInstruction = systemInstruction.trim(),
                                speechInputLanguage = speechLang,
                                translatorEnabled = translatorEnabled
                            )
                            onSaveSettings(updated)
                            Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Save")
                    }
                }
            )
        },
        bottomBar = {
            GemoBottomNavBar(
                currentScreen = AppScreen.SETTINGS,
                onNavigate = onNavigate
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Backend Connection Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Firebase RTDB Backend",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )

                            // Status badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (connectionStatus) {
                                    ConnectionStatus.CONNECTED -> Color(0xFF1E3A24)
                                    ConnectionStatus.SERVER_UNAVAILABLE -> Color(0xFF3E1D1D)
                                    else -> GeminiBlue.copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = connectionStatus.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (connectionStatus) {
                                        ConnectionStatus.CONNECTED -> Color(0xFF4CAF50)
                                        ConnectionStatus.SERVER_UNAVAILABLE -> Color(0xFFEF5350)
                                        else -> GeminiCyan
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = firebaseUrl,
                            onValueChange = { firebaseUrl = it },
                            label = { Text("Realtime Database URL") },
                            placeholder = { Text("https://your-project-rtdb.firebaseio.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Backend Provider",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            OutlinedButton(
                                onClick = onCheckConnection,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Connection", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            providers.forEach { prov ->
                                FilterChip(
                                    selected = provider == prov,
                                    onClick = { provider = prov },
                                    label = { Text(prov, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // Dedicated Heavy Media & Cloud Storage Configuration (Images, Videos & Large Files)
            item {
                var storageBucketUrl by remember { mutableStateOf("https://firebasestorage.googleapis.com/v0/b/gemo-ai-cloud.appspot.com") }
                var mediaProvider by remember { mutableStateOf("Firebase Storage") }
                val mediaProviders = listOf("Firebase Storage", "S3 Compatible", "Local WebDAV")
                var maxMediaUploadMb by remember { mutableFloatStateOf(100f) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = GeminiBlue.copy(alpha = 0.08f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = GeminiBlue, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Heavy Media & Cloud Storage",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = GeminiBlue.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Storage Slot",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GeminiBlue,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Separate dedicated storage endpoint for large binary assets (Videos, high-res Images, APKs and archives) to avoid choking Realtime Database.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Media Storage Provider", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            mediaProviders.forEach { mp ->
                                FilterChip(
                                    selected = mediaProvider == mp,
                                    onClick = { mediaProvider = mp },
                                    label = { Text(mp, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = storageBucketUrl,
                            onValueChange = { storageBucketUrl = it },
                            label = { Text("Cloud Storage Bucket / Endpoint URL") },
                            placeholder = { Text("https://firebasestorage.googleapis.com/v0/b/...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Max Upload Size Limit", fontSize = 12.sp)
                            Text("${maxMediaUploadMb.toInt()} MB", fontWeight = FontWeight.Bold, color = GeminiBlue)
                        }
                        Slider(
                            value = maxMediaUploadMb,
                            onValueChange = { maxMediaUploadMb = it },
                            valueRange = 10f..500f,
                            steps = 8
                        )
                    }
                }
            }

            // Appearance & Theme
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Theme & Appearance",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("SYSTEM" to "System", "DARK" to "Dark", "LIGHT" to "Light").forEach { (mode, label) ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { themeMode = mode },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GeminiBlue.copy(alpha = 0.2f),
                                        selectedLabelColor = GeminiBlue
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // AI Features & Flags
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Intelligent Modules",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Agent mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Autonomous Agent Mode", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Allow model to trigger calculators and files", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = agentModeEnabled, onCheckedChange = { agentModeEnabled = it })
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Memory
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Persistent AI Memory", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Inject saved facts and personal preferences", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = memoryEnabled, onCheckedChange = { memoryEnabled = it })
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Vision
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Vision & Multimodal Input", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Allow image analysis and visual questions", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = visionEnabled, onCheckedChange = { visionEnabled = it })
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Automatic Language Translator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Live Language Translator", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Automatically translate prompts and responses (Default: ON)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = translatorEnabled, onCheckedChange = { translatorEnabled = it })
                        }
                    }
                }
            }

            // Model Hyperparameters
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Model Generation Parameters",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Temperature
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Temperature: ${((temperature * 10).roundToInt() / 10f)}", fontSize = 12.sp)
                            Text(if (temperature < 0.4f) "Precise" else if (temperature < 0.8f) "Balanced" else "Creative", fontSize = 11.sp, color = GeminiBlue)
                        }
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0.1f..1.2f,
                            colors = SliderDefaults.colors(thumbColor = GeminiBlue, activeTrackColor = GeminiBlue)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Max tokens
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Max Tokens: $maxTokens", fontSize = 12.sp)
                        }
                        Slider(
                            value = maxTokens.toFloat(),
                            onValueChange = { maxTokens = it.roundToInt() },
                            valueRange = 256f..4096f,
                            steps = 14,
                            colors = SliderDefaults.colors(thumbColor = GeminiPurple, activeTrackColor = GeminiPurple)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Timeout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Timeout: ${timeoutSeconds}s", fontSize = 12.sp)
                        }
                        Slider(
                            value = timeoutSeconds.toFloat(),
                            onValueChange = { timeoutSeconds = it.roundToInt() },
                            valueRange = 15f..120f,
                            steps = 6,
                            colors = SliderDefaults.colors(thumbColor = GeminiCyan, activeTrackColor = GeminiCyan)
                        )
                    }
                }
            }

            // System Instruction
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "System Instruction & Persona",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = systemInstruction,
                            onValueChange = { systemInstruction = it },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // About Gemo AI
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = GeminiBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("About Gemo AI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Version: 2.0 Next-Generation AI Platform", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Creator: Rohit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Architecture: Firebase RTDB + Local Fallback Engine", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Multimodal: Text, Images, Code & Agent Tools", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
