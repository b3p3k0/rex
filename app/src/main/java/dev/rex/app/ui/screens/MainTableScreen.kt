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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import dev.rex.app.ui.components.HostCommandRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTableScreen(
    onNavigateToAddHost: () -> Unit,
    onNavigateToAddCommand: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onExecuteCommand: (HostCommandMapping) -> Unit,
    viewModel: MainTableViewModel = hiltViewModel()
) {
    val hostCommands by viewModel.hostCommands.collectAsStateWithLifecycle()
    var showAddMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rex") },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.semantics { 
                            contentDescription = "Settings" 
                        }
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddMenu = true },
                modifier = Modifier.semantics { 
                    contentDescription = "Add Host or Command" 
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                hostCommands.isEmpty() -> {
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
                        items(hostCommands) { hostCommand ->
                            HostCommandRow(
                                hostCommand = hostCommand,
                                onExecute = { onExecuteCommand(hostCommand) }
                            )
                        }
                    }
                }
            }
        }

        if (showAddMenu) {
            AddMenuDialog(
                onDismiss = { showAddMenu = false },
                onAddHost = {
                    showAddMenu = false
                    onNavigateToAddHost()
                },
                onAddCommand = {
                    showAddMenu = false
                    onNavigateToAddCommand()
                }
            )
        }
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
            text = "Tap + to add one",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddMenuDialog(
    onDismiss: () -> Unit,
    onAddHost: () -> Unit,
    onAddCommand: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add new") },
        text = {
            Column {
                TextButton(
                    onClick = onAddHost,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Add Host" }
                ) {
                    Text("Add Host")
                }
                TextButton(
                    onClick = onAddCommand,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Add Command" }
                ) {
                    Text("Add Command")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}