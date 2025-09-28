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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    mappingId: String,
    onNavigateBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(mappingId) {
        viewModel.startSession(mappingId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = if (uiState.hostNickname.isNotEmpty() && uiState.commandName.isNotEmpty()) {
                        "${uiState.hostNickname} • ${uiState.commandName}"
                    } else {
                        "Loading..."
                    }
                    Text(title)
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { 
                            contentDescription = "Go back to main screen" 
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            SessionBottomBar(
                isRunning = uiState.isRunning,
                exitCode = uiState.exitCode,
                elapsedTimeMs = uiState.elapsedTimeMs,
                canCopy = uiState.canCopy,
                hasOutput = uiState.output.isNotBlank(),
                onCancel = { viewModel.cancelExecution() },
                onCopy = { viewModel.copyOutput() },
                onShowOutput = { viewModel.showOutputDialog() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Output will open in a dialog when the command completes.",
                    style = MaterialTheme.typography.bodyMedium
                )

                uiState.error?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (uiState.showOutputDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissOutputDialog() },
            title = { Text("Command Output") },
            text = {
                val scrollState = rememberScrollState()
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(scrollState)
                    ) {
                        val dialogText = if (uiState.output.isBlank()) {
                            "Waiting for output..."
                        } else {
                            uiState.output
                        }
                        Text(
                            text = dialogText,
                            fontFamily = FontFamily.Monospace
                        )

                        if (!uiState.canCopy && uiState.output.isNotBlank()) {
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
                TextButton(
                    onClick = { viewModel.copyOutput() },
                    enabled = uiState.canCopy
                ) {
                    Text("Copy all")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissOutputDialog() }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SessionBottomBar(
    isRunning: Boolean,
    exitCode: Int?,
    elapsedTimeMs: Long,
    canCopy: Boolean,
    hasOutput: Boolean,
    onCancel: () -> Unit,
    onCopy: () -> Unit,
    onShowOutput: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status text
            Text(
                text = when {
                    isRunning -> "Running... ${formatElapsedTime(elapsedTimeMs)}"
                    exitCode != null -> "Completed with exit code $exitCode in ${formatElapsedTime(elapsedTimeMs)}"
                    else -> "Ready"
                },
                style = MaterialTheme.typography.bodySmall
            )

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasOutput) {
                    TextButton(
                        onClick = onShowOutput,
                        modifier = Modifier.semantics {
                            contentDescription = "View command output"
                        }
                    ) {
                        Text("View output")
                    }
                }

                if (canCopy) {
                    TextButton(
                        onClick = onCopy,
                        modifier = Modifier.semantics { 
                            contentDescription = "Copy output to clipboard" 
                        }
                    ) {
                        Text("Copy")
                    }
                }

                if (isRunning) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.semantics { 
                            contentDescription = "Cancel running command" 
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

private fun formatElapsedTime(elapsedMs: Long): String {
    val seconds = elapsedMs / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    
    return if (minutes > 0) {
        "${minutes}m ${remainingSeconds}s"
    } else {
        "${remainingSeconds}s"
    }
}
