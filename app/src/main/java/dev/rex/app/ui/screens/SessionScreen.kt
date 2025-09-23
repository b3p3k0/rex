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
import androidx.compose.material.icons.filled.ArrowBack
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
import dev.rex.app.ui.components.TerminalPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    hostNickname: String,
    commandName: String,
    onNavigateBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    Log.i("Rex", "Screen: SessionScreen")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$hostNickname • $commandName") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { 
                            contentDescription = "Go back to main screen" 
                        }
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                onCancel = { viewModel.cancelExecution() },
                onCopy = { viewModel.copyOutput() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TerminalPane(
                output = uiState.output,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SessionBottomBar(
    isRunning: Boolean,
    exitCode: Int?,
    elapsedTimeMs: Long,
    canCopy: Boolean,
    onCancel: () -> Unit,
    onCopy: () -> Unit
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