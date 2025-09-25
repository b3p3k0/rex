/*
 * Rex — Remote Exec for Android
 * Copyright (C) 2024 Rex Maintainers (b3p3k0)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.rex.app.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rex.app.data.db.HostCommandMapping
import dev.rex.app.data.db.HostCommandRow
import dev.rex.app.ui.components.AboutDialog
import dev.rex.app.ui.components.HostCommandRow as HostCommandRowComponent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTableScreen(
    onNavigateToAddHost: () -> Unit,
    onNavigateToAddCommand: (String) -> Unit,
    onNavigateToEditCommand: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToHostDetail: (String) -> Unit,
    onExecuteCommand: (HostCommandMapping) -> Unit,
    viewModel: MainTableViewModel = hiltViewModel()
) {
    Log.i("Rex", "Screen: MainTableScreen")
    val hostCommandRows by viewModel.hostCommandRows.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showAbout by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var blockedHost by remember { mutableStateOf<HostCommandRow?>(null) }
    var blockedCommand by remember { mutableStateOf<HostCommandMapping?>(null) }

    // Get haptic feedback setting from SettingsViewModel
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsData by settingsViewModel.settingsData.collectAsStateWithLifecycle()
    
    fun clearBlockedState() {
        blockedHost = null
        blockedCommand = null
    }

    val handleExecuteCommand = remember(onExecuteCommand) {
        { hostRow: HostCommandRow, command: HostCommandMapping ->
            val requiresKey = hostRow.hostAuthMethod.equals("key", ignoreCase = true)
            val keyProvisioned = !hostRow.hostKeyBlobId.isNullOrBlank() &&
                hostRow.hostKeyProvisionStatus.equals("success", ignoreCase = true)

            if (requiresKey && !keyProvisioned) {
                blockedHost = hostRow
                blockedCommand = command
            } else {
                onExecuteCommand(command)
            }
        }
    }
    
    // Group by host to avoid duplicate host entries
    val groupedByHost = remember(hostCommandRows) {
        val grouped = hostCommandRows.groupBy { it.hostId }
        Log.d("Rex", "MainTableScreen: groupedByHost has ${grouped.size} hosts, total rows=${hostCommandRows.size}")
        grouped
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Rex") },
                actions = {
                    IconButton(
                        onClick = { showAbout = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Open about dialog"
                        }
                    ) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = "Open about dialog")
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.semantics {
                            contentDescription = "Settings"
                        }
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.semantics {
                                contentDescription = "More options"
                            }
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Logs") },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToLogs()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddHost,
                modifier = Modifier.semantics {
                    contentDescription = "Add host"
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add host")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                groupedByHost.isEmpty() -> {
                    EmptyState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groupedByHost.keys.toList()) { hostId ->
                            val hostRows = groupedByHost[hostId]!!
                            val hostRow = hostRows.first() // Host info is same across rows
                            
                            HostRowItem(
                                hostRow = hostRow,
                                commands = hostRows.mapNotNull { row ->
                                    if (row.cmdId != null && row.cmdName != null && row.cmdCommand != null) {
                                        HostCommandMapping(
                                            id = hostRow.hostId,
                                            nickname = hostRow.hostNickname,
                                            hostname = hostRow.hostName,
                                            port = hostRow.hostPort,
                                            username = hostRow.hostUser,
                                            authMethod = hostRow.hostAuthMethod,
                                            keyBlobId = hostRow.hostKeyBlobId,
                                            connectTimeoutMs = hostRow.hostConnectTimeoutMs,
                                            readTimeoutMs = hostRow.hostReadTimeoutMs,
                                            strictHostKey = hostRow.hostStrictHostKey,
                                            pinnedHostKeyFingerprint = hostRow.hostPinnedHostKeyFingerprint,
                                            keyProvisionedAt = hostRow.hostKeyProvisionedAt,
                                            keyProvisionStatus = hostRow.hostKeyProvisionStatus,
                                            createdAt = hostRow.hostCreatedAt,
                                            updatedAt = hostRow.hostUpdatedAt,
                                            name = row.cmdName,
                                            command = row.cmdCommand,
                                            requireConfirmation = row.cmdRequireConfirmation ?: true,
                                            defaultTimeoutMs = row.cmdDefaultTimeoutMs ?: 15000,
                                            allowPty = row.cmdAllowPty ?: false,
                                            mappingId = row.mappingId ?: "",
                                            sortIndex = row.sortIndex ?: 0
                                        )
                                    } else null
                                },
                                onExecuteCommand = { command -> handleExecuteCommand(hostRow, command) },
                                onNavigateToAddCommand = onNavigateToAddCommand,
                                onNavigateToEditCommand = onNavigateToEditCommand,
                                onNavigateToHostDetail = onNavigateToHostDetail,
                                onDeleteCommand = { commandId ->
                                    viewModel.onDeleteCommand(commandId)
                                },
                                onDeleteHost = {
                                    viewModel.onDeleteHost(hostRow.hostId, hostRow.hostNickname)
                                    // Show snackbar confirmation
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Host \"${hostRow.hostNickname}\" deleted",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                },
                                enableHapticFeedback = settingsData.hapticFeedbackLongPress
                            )
                        }
                    }
                }
            }
        }
    }

    // Show About dialog when requested
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    blockedHost?.let { hostRow ->
        val pendingCommand = blockedCommand
        AlertDialog(
            onDismissRequest = { clearBlockedState() },
            title = { Text("Key required before running") },
            text = {
                Text(
                    text = "${hostRow.hostNickname} uses key-based authentication but doesn't have a deployed key yet. Add or deploy a key before running commands.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingCommand?.let { command ->
                        TextButton(onClick = {
                            clearBlockedState()
                            onExecuteCommand(command)
                        }) {
                            Text("Run anyway")
                        }
                    }
                    TextButton(onClick = {
                        clearBlockedState()
                        onNavigateToHostDetail(hostRow.hostId)
                    }) {
                        Text("Manage keys")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { clearBlockedState() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HostRowItem(
    hostRow: HostCommandRow,
    commands: List<HostCommandMapping>,
    onExecuteCommand: (HostCommandMapping) -> Unit,
    onNavigateToAddCommand: (String) -> Unit,
    onNavigateToEditCommand: (String) -> Unit,
    onNavigateToHostDetail: (String) -> Unit,
    onDeleteCommand: (String) -> Unit,
    onDeleteHost: () -> Unit,
    enableHapticFeedback: Boolean = true
) {
    Log.d("Rex", "HostRowItem: ${hostRow.hostNickname} with ${commands.size} commands")
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Host header row with action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${hostRow.hostNickname} (${hostRow.hostUser}@${hostRow.hostName}:${hostRow.hostPort})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics {
                            contentDescription = "Host: ${hostRow.hostNickname}"
                        }
                    )
                }
                Row {
                    IconButton(
                        onClick = { onNavigateToHostDetail(hostRow.hostId) }
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Manage SSH keys for ${hostRow.hostNickname}"
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete host ${hostRow.hostNickname}"
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (commands.isEmpty()) {
                // No commands yet
                Text(
                    text = "No commands yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Show commands
                commands.forEach { command ->
                    HostCommandRowComponent(
                        hostCommand = command,
                        onExecute = { onExecuteCommand(command) },
                        onEdit = { mappingId ->
                            // Extract command ID from mappingId format: hostId_commandId
                            val commandId = mappingId.substringAfter("_")
                            onNavigateToEditCommand(commandId)
                        },
                        onDelete = { mappingId ->
                            // Extract command ID from mappingId format: hostId_commandId
                            val commandId = mappingId.substringAfter("_")
                            onDeleteCommand(commandId)
                        },
                        enableHapticFeedback = enableHapticFeedback
                    )
                    if (command != commands.last()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            // Always show Add Command button
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onNavigateToAddCommand(hostRow.hostId) },
                modifier = Modifier.semantics {
                    contentDescription = "Add command for ${hostRow.hostNickname}"
                }
            ) {
                Text("Add command")
            }
        }
    }
    
    // Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete host?") },
            text = { 
                if (commands.isEmpty()) {
                    Text("This will remove \"${hostRow.hostNickname}\".")
                } else {
                    Text("This will remove \"${hostRow.hostNickname}\" and ${commands.size} command mapping(s).")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteHost()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { 
                    Text("Delete") 
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { 
                    Text("Cancel") 
                }
            }
        )
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No hosts configured",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap + to add host",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
