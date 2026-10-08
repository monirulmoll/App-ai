package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.LlmSettings
import com.example.ui.theme.GeminiBlue

@Composable
fun SettingsDialog(
    currentSettings: LlmSettings,
    onSave: (LlmSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var firebaseUrl by remember { mutableStateOf(currentSettings.firebaseUrl) }
    var selectedModel by remember { mutableStateOf(currentSettings.modelName) }
    var selectedProvider by remember { mutableStateOf(currentSettings.provider) }
    var themeMode by remember { mutableStateOf(currentSettings.themeMode) }
    var timeoutSeconds by remember { mutableFloatStateOf(currentSettings.responseTimeoutSeconds.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("settings_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(id = R.string.settings),
                tint = GeminiBlue,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(id = R.string.settings),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Firebase Database URL
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Firebase RTDB Backend",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(
                            onClick = { firebaseUrl = LlmSettings.DEFAULT_FIREBASE_URL },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset default URL",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "Used for sending messages and receiving AI replies in real time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = firebaseUrl,
                        onValueChange = { firebaseUrl = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("firebase_url_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // AI Response Timeout
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "AI Response Timeout",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${timeoutSeconds.toInt()}s",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "How long to wait for the backend to write the AI response.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Slider(
                        value = timeoutSeconds,
                        onValueChange = { timeoutSeconds = it },
                        valueRange = 10f..120f,
                        steps = 10,
                        modifier = Modifier.testTag("timeout_slider")
                    )
                }

                // Theme selection
                Column {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("SYSTEM" to "System", "DARK" to "Dark", "LIGHT" to "Light").forEach { (mode, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                RadioButton(
                                    selected = themeMode == mode,
                                    onClick = { themeMode = mode },
                                    modifier = Modifier.testTag("theme_radio_$mode")
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sanitizedUrl = firebaseUrl.trim().ifEmpty { LlmSettings.DEFAULT_FIREBASE_URL }
                    onSave(
                        currentSettings.copy(
                            firebaseUrl = sanitizedUrl,
                            modelName = selectedModel,
                            provider = selectedProvider,
                            themeMode = themeMode,
                            responseTimeoutSeconds = timeoutSeconds.toInt()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_settings_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
