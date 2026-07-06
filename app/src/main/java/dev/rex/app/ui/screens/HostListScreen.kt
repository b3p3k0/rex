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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rex.app.data.db.HostEntity
import dev.rex.app.ui.components.AboutDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    onNavigateToAddHost: () -> Unit,
    onNavigateToHostCommands: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToHostDetail: (String) -> Unit,
    viewModel: HostListViewModel = hiltViewModel()
) {
    Log.i("Rex", "Screen: HostListScreen")
    val hosts by viewModel.hosts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showAbout by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsData by settingsViewModel.settingsData.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Rex") },
                actions = {
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
                                text = { Text("Settings") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = "Settings"
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = "About"
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showAbout = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Logs") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Description,
                                        contentDescription = "Logs"
                                    )
                                },
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
                hosts.isEmpty() -> {
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
                        items(hosts, key = { it.id }) { host ->
                            HostListItem(
                                host = host,
                                onClick = { onNavigateToHostCommands(host.id) },
                                onNavigateToHostDetail = onNavigateToHostDetail,
                                onDeleteHost = {
                                    viewModel.onDeleteHost(host.id, host.nickname)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Host \"${host.nickname}\" deleted",
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

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostListItem(
    host: HostEntity,
    onClick: () -> Unit,
    onNavigateToHostDetail: (String) -> Unit,
    onDeleteHost: () -> Unit,
    enableHapticFeedback: Boolean = true
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showHostActionsDialog by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (enableHapticFeedback) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    showHostActionsDialog = true
                }
            )
            .semantics {
                role = Role.Button
                contentDescription = "Host: ${host.nickname}. Tap to view commands, long press for actions."
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = host.nickname,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${host.username}@${host.hostname}:${host.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showHostActionsDialog) {
        AlertDialog(
            onDismissRequest = { showHostActionsDialog = false },
            title = { Text("Host Actions") },
            text = {
                Text("Choose an action for \"${host.nickname}\":")
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            showHostActionsDialog = false
                            onNavigateToHostDetail(host.id)
                        }
                    ) {
                        Text("Manage keys")
                    }

                    TextButton(
                        onClick = {
                            showHostActionsDialog = false
                            showDeleteConfirm = true
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete host")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showHostActionsDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete host?") },
            text = {
                Text("This will remove \"${host.nickname}\" and all its command mappings.")
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
