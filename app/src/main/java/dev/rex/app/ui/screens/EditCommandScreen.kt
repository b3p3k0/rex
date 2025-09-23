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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCommandScreen(
    commandId: String,
    onNavigateBack: () -> Unit,
    viewModel: EditCommandViewModel = hiltViewModel()
) {
    Log.i("Rex", "Screen: EditCommandScreen for command $commandId")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Command") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Go back"
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoadingInitial) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChanged,
                    label = { Text("Command Name") },
                    placeholder = { Text("e.g., Update System") },
                    isError = uiState.nameError != null,
                    supportingText = uiState.nameError?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Command name input" },
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.command,
                    onValueChange = viewModel::onCommandChanged,
                    label = { Text("Shell Command") },
                    placeholder = { Text("e.g., sudo apt update && sudo apt upgrade") },
                    isError = uiState.commandError != null,
                    supportingText = uiState.commandError?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Shell command input" },
                    minLines = 3,
                    maxLines = 6
                )

                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Command Settings",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Require confirmation",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Ask before executing this command",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.requireConfirmation,
                                onCheckedChange = viewModel::onRequireConfirmationChanged,
                                modifier = Modifier.semantics {
                                    contentDescription = "Require confirmation toggle"
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Allow PTY",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Enable interactive terminal features",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.allowPty,
                                onCheckedChange = viewModel::onAllowPtyChanged,
                                modifier = Modifier.semantics {
                                    contentDescription = "Allow PTY toggle"
                                }
                            )
                        }

                        Column {
                            Text(
                                text = "Timeout: ${uiState.defaultTimeoutMs / 1000}s",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Maximum execution time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = uiState.defaultTimeoutMs.toFloat(),
                                onValueChange = { viewModel.onDefaultTimeoutChanged(it.toInt()) },
                                valueRange = 5000f..300000f,
                                steps = 58,
                                modifier = Modifier.semantics {
                                    contentDescription = "Command timeout slider"
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        viewModel.onSaveCommand {
                            onNavigateBack()
                        }
                    },
                    enabled = uiState.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Save command changes" }
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save Changes")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}