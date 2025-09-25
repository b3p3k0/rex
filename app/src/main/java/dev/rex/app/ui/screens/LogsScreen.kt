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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rex.app.data.db.LogEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onNavigateBack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel()
) {
    Log.i("Rex", "Screen: LogsScreen")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show purge message
    LaunchedEffect(uiState.lastPurgeMessage) {
        uiState.lastPurgeMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearPurgeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Execution Logs") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Go back"
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { viewModel.performManualPurge() },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Purge")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Filters Section
            FiltersSection(
                uiState = uiState,
                onHostFilterChanged = viewModel::updateHostFilter,
                onStatusFilterChanged = viewModel::updateStatusFilter,
                onSearchQueryChanged = viewModel::updateSearchQuery
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Content Section
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.filteredLogs.isEmpty() -> {
                    EmptyLogsState()
                }
                else -> {
                    LogsList(logs = uiState.filteredLogs)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSection(
    uiState: LogsUiState,
    onHostFilterChanged: (String) -> Unit,
    onStatusFilterChanged: (String?) -> Unit,
    onSearchQueryChanged: (String) -> Unit
) {
    Column {
        // Host Filter Dropdown
        var hostDropdownExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = hostDropdownExpanded,
            onExpandedChange = { hostDropdownExpanded = it }
        ) {
            TextField(
                value = uiState.selectedHostFilter,
                onValueChange = {},
                readOnly = true,
                label = { Text("Host Filter") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostDropdownExpanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = hostDropdownExpanded,
                onDismissRequest = { hostDropdownExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("All hosts") },
                    onClick = {
                        onHostFilterChanged("All hosts")
                        hostDropdownExpanded = false
                    }
                )
                uiState.distinctHostNicknames.forEach { hostNickname ->
                    DropdownMenuItem(
                        text = { Text(hostNickname) },
                        onClick = {
                            onHostFilterChanged(hostNickname)
                            hostDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status Filter Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.statusFilter == null,
                onClick = { onStatusFilterChanged(null) },
                label = { Text("All") }
            )
            FilterChip(
                selected = uiState.statusFilter == "success",
                onClick = {
                    onStatusFilterChanged(if (uiState.statusFilter == "success") null else "success")
                },
                label = { Text("Success") }
            )
            FilterChip(
                selected = uiState.statusFilter == "failure",
                onClick = {
                    onStatusFilterChanged(if (uiState.statusFilter == "failure") null else "failure")
                },
                label = { Text("Failure") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        TextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            label = { Text("Search logs...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmptyLogsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No execution logs",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Logs will appear here after running commands",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LogsList(logs: List<LogEntity>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(logs, key = { it.id }) { log ->
            LogEntryCard(log = log)
        }
    }
}

@Composable
private fun LogEntryCard(log: LogEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row: Host + Command
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${log.hostNickname} • ${log.commandName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatTimestamp(log.ts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                StatusBadge(
                    exitCode = log.exitCode,
                    status = log.status
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (log.durationMs != null) {
                    MetricItem(
                        label = "Duration",
                        value = formatDuration(log.durationMs)
                    )
                }

                val totalBytes = log.bytesStdout + log.bytesStderr
                if (totalBytes > 0) {
                    MetricItem(
                        label = "Data",
                        value = formatBytes(totalBytes)
                    )
                }

                if (log.exitCode != null) {
                    MetricItem(
                        label = "Exit",
                        value = log.exitCode.toString()
                    )
                }
            }

            // Summary (if available)
            if (!log.messageRedacted.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = log.messageRedacted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No summary",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(exitCode: Int?, status: String) {
    val (color, text) = when {
        exitCode == 0 -> MaterialTheme.colorScheme.primary to "Success"
        exitCode != null && exitCode != 0 -> MaterialTheme.colorScheme.error to "Failed"
        else -> MaterialTheme.colorScheme.outline to status.replaceFirstChar { it.uppercase() }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.padding(0.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatDuration(durationMs: Int): String {
    return when {
        durationMs < 1000 -> "${durationMs}ms"
        durationMs < 60_000 -> "${durationMs / 1000}s"
        else -> "${durationMs / 60_000}m ${(durationMs % 60_000) / 1000}s"
    }
}

private fun formatBytes(bytes: Int): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${bytes / (1024 * 1024)}MB"
    }
}