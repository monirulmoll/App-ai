package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ConnectionStatus
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.GeminiPurple
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    currentModelName: String = "",
    connectionStatus: ConnectionStatus,
    onOpenDrawer: () -> Unit,
    onOpenModelSelector: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenMemory: () -> Unit,
    onNewChat: () -> Unit,
    onCheckConnection: () -> Unit,
    onClearChat: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val statusDotColor = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> StatusConnected
        ConnectionStatus.CONNECTING, ConnectionStatus.MESSAGE_SENDING -> StatusConnecting
        ConnectionStatus.AI_GENERATING, ConnectionStatus.RESPONSE_RECEIVED -> GeminiCyan
        ConnectionStatus.SERVER_UNAVAILABLE -> StatusError
    }

    val statusLabel = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> "Live"
        ConnectionStatus.CONNECTING -> "Connecting…"
        ConnectionStatus.MESSAGE_SENDING -> "Sending…"
        ConnectionStatus.AI_GENERATING -> "Thinking…"
        ConnectionStatus.RESPONSE_RECEIVED -> "Ready"
        ConnectionStatus.SERVER_UNAVAILABLE -> "Offline"
    }

    TopAppBar(
        modifier = modifier.testTag("chat_top_bar"),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        navigationIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("open_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open navigation menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // App Logo Badge
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(GeminiBlue, GeminiPurple, GeminiCyan)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Gemo AI",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            // Workspace Button
            IconButton(
                onClick = onOpenWorkspace,
                modifier = Modifier.testTag("top_workspace_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Workspace",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Memory Button
            IconButton(
                onClick = onOpenMemory,
                modifier = Modifier.testTag("top_memory_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Memory",
                    tint = GeminiBlue
                )
            }

            // New Chat Button
            IconButton(
                onClick = onNewChat,
                modifier = Modifier.testTag("new_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.new_chat),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Overflow Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            showMenu = false
                            onOpenSettings()
                        },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Messages") },
                        onClick = {
                            showMenu = false
                            onClearChat()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Test Connection") },
                        onClick = {
                            showMenu = false
                            onCheckConnection()
                        },
                        leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) }
                    )
                }
            }
        }
    )
}
