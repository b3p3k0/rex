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
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import dev.rex.app.data.db.HostCommandMapping
import dev.rex.app.ui.components.HostCommandRow
import dev.rex.app.ui.components.SecurityGate
import dev.rex.app.ui.components.SudoPasswordDialog
import dev.rex.app.ui.components.TofuConfirmationDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostCommandsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddCommand: (String) -> Unit,
    onNavigateToEditCommand: (String) -> Unit,
    onNavigateToHostDetail: (String) -> Unit,
    viewModel: HostCommandsViewModel = hiltViewModel()
) {
    Log.i("Rex", "Screen: HostCommandsScreen")
    val host by viewModel.host.collectAsStateWithLifecycle()
    val commands by viewModel.commands.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var blockedCommand by remember { mutableStateOf<HostCommandMapping?>(null) }

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsData by settingsViewModel.settingsData.collectAsStateWithLifecycle()

    // Activity-scoped so a running execution survives navigating away from
    // this screen (viewModelScope would cancel it on back navigation)
    val activity = checkNotNull(LocalActivity.current as? ComponentActivity)
    val sessionViewModel: SessionViewModel = hiltViewModel(activity)
    val sessionState by sessionViewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val securityManager = remember {
        EntryPointAccessors.fromApplication(
            context,
            SecurityManagerEntryPoint::class.java
        ).securityManager()
    }

    fun startExecution(command: HostCommandMapping) {
        if (sessionState.isRunning && sessionState.activeMappingId != command.mappingId) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Another command is currently running",
                    duration = SnackbarDuration.Short
                )
            }
            return
        }

        if (sessionState.isRunning && sessionState.activeMappingId == command.mappingId) {
            // Already executing; ignore duplicate tap
            return
        }

        if (sessionState.showOutputDialog) {
            sessionViewModel.dismissOutputDialog()
        }

        if (sessionState.error != null) {
            sessionViewModel.clearError()
        }

        sessionViewModel.startSession(command.mappingId)
    }

    fun handleExecuteCommand(command: HostCommandMapping) {
        val requiresKey = command.authMethod.equals("key", ignoreCase = true)
        val keyProvisioned = !command.keyBlobId.isNullOrBlank() &&
            command.keyProvisionStatus.equals("success", ignoreCase = true)

        if (requiresKey && !keyProvisioned) {
            blockedCommand = command
            return
        }

        startExecution(command)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(host?.nickname ?: "")
                        host?.let {
                            Text(
                                text = "${it.username}@${it.hostname}:${it.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Back to hosts"
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to hosts")
                    }
                },
                actions = {
                    host?.let { currentHost ->
                        IconButton(
                            onClick = { onNavigateToHostDetail(currentHost.id) },
                            modifier = Modifier.semantics {
                                contentDescription = "Manage keys for ${currentHost.nickname}"
                            }
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = "Manage keys")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            host?.let { currentHost ->
                FloatingActionButton(
                    onClick = { onNavigateToAddCommand(currentHost.id) },
                    modifier = Modifier.semantics {
                        contentDescription = "Add command for ${currentHost.nickname}"
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add command")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                commands.isEmpty() -> {
                    EmptyCommandsState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(commands, key = { it.mappingId }) { command ->
                            val isActiveCommand = sessionState.activeMappingId == command.mappingId
                            val isRunningCommand = sessionState.isRunning && isActiveCommand
                            val lastDuration = if (!sessionState.isRunning && isActiveCommand && sessionState.exitCode != null) {
                                sessionState.elapsedTimeMs
                            } else {
                                0L
                            }

                            HostCommandRow(
                                hostCommand = command,
                                onExecute = { handleExecuteCommand(command) },
                                onEdit = { mappingId ->
                                    // Extract command ID from mappingId format: hostId_commandId
                                    val commandId = mappingId.substringAfter("_")
                                    onNavigateToEditCommand(commandId)
                                },
                                onDelete = { mappingId ->
                                    // Extract command ID from mappingId format: hostId_commandId
                                    val commandId = mappingId.substringAfter("_")
                                    viewModel.onDeleteCommand(commandId)
                                },
                                enableHapticFeedback = settingsData.hapticFeedbackLongPress,
                                isRunning = isRunningCommand,
                                elapsedTimeMs = lastDuration,
                                errorMessage = if (isActiveCommand) sessionState.error else null,
                                hasStoredOutput = isActiveCommand && sessionState.output.isNotBlank(),
                                showOutputDialog = sessionState.showOutputDialog,
                                canCopyOutput = sessionState.canCopy,
                                onViewOutput = { sessionViewModel.showOutputDialog() }
                            )
                        }
                    }
                }
            }
        }
    }

    sessionState.tofuPrompt?.let { prompt ->
        TofuConfirmationDialog(
            prompt = prompt,
            onTrust = { sessionViewModel.confirmTofuTrust() },
            onReject = { sessionViewModel.dismissTofuPrompt() }
        )
    }

    sessionState.sudoPasswordPrompt?.let { prompt ->
        SudoPasswordDialog(
            hostNickname = prompt.hostNickname,
            username = prompt.username,
            commandName = prompt.commandName,
            onDismiss = { sessionViewModel.dismissSudoPrompt() },
            onConfirm = { password, remember ->
                sessionViewModel.submitSudoPassword(password, remember)
            }
        )
    }

    // Security gate overlay: shown when the keystore auth window has expired;
    // on success the pending command is retried automatically
    if (sessionState.showSecurityGate) {
        SecurityGate(
            title = "Authenticate to run command",
            subtitle = "Your device credential is required to unlock SSH keys",
            onAuthenticated = sessionViewModel::onSecurityGateAuthenticated,
            onCancel = sessionViewModel::onSecurityGateCancelled,
            securityManager = securityManager
        )
    }

    if (sessionState.showOutputDialog) {
        AlertDialog(
            onDismissRequest = { sessionViewModel.dismissOutputDialog() },
            title = { Text("Command Output") },
            text = {
                val scrollState = rememberScrollState()
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(scrollState)
                    ) {
                        sessionState.error?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val dialogText = if (sessionState.output.isBlank()) {
                            "Waiting for output..."
                        } else {
                            sessionState.output
                        }

                        Text(
                            text = dialogText,
                            fontFamily = FontFamily.Monospace
                        )

                        if (!sessionState.canCopy && sessionState.output.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Copying is disabled in Preferences",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessionState.isRunning) {
                        TextButton(
                            onClick = { sessionViewModel.cancelExecution() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Cancel")
                        }
                    }
                    TextButton(
                        onClick = { sessionViewModel.copyOutput() },
                        enabled = sessionState.canCopy && sessionState.output.isNotBlank()
                    ) {
                        Text("Copy all")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionViewModel.dismissOutputDialog() }) {
                    Text("Close")
                }
            }
        )
    }

    blockedCommand?.let { command ->
        AlertDialog(
            onDismissRequest = { blockedCommand = null },
            title = { Text("Key required before running") },
            text = {
                Text(
                    text = "${command.nickname} uses key-based authentication but doesn't have a deployed key yet. Add or deploy a key before running commands.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        blockedCommand = null
                        startExecution(command)
                    }) {
                        Text("Run anyway")
                    }
                    TextButton(onClick = {
                        blockedCommand = null
                        onNavigateToHostDetail(command.id)
                    }) {
                        Text("Manage keys")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { blockedCommand = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyCommandsState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No commands yet",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap + to add one",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
