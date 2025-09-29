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

package dev.rex.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.rex.app.data.db.HostCommandMapping

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HostCommandRow(
    hostCommand: HostCommandMapping,
    onExecute: (HostCommandMapping) -> Unit,
    onEdit: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    enableHapticFeedback: Boolean = true,
    modifier: Modifier = Modifier,
    isRunning: Boolean = false,
    elapsedTimeMs: Long = 0,
    errorMessage: String? = null,
    hasStoredOutput: Boolean = false,
    showOutputDialog: Boolean = false,
    canCopyOutput: Boolean = false,
    onViewOutput: () -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showActionsDialog by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = {
                    if (!isRunning) {
                        onExecute(hostCommand)
                    }
                },
                onLongClick = {
                    if (enableHapticFeedback) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    showActionsDialog = true
                }
            )
            .semantics {
                role = Role.Button
                contentDescription = "Host command: ${hostCommand.nickname} ${hostCommand.name}. Tap to execute, long press for options."
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${hostCommand.nickname} • ${hostCommand.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )

                if (isRunning) {
                    Spacer(modifier = Modifier.width(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Running…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = hostCommand.hostname,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isRunning && elapsedTimeMs > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Completed in ${formatRunDuration(elapsedTimeMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Show "View output" button when output exists but dialog is closed
            if (hasStoredOutput && !showOutputDialog) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onViewOutput,
                    modifier = Modifier.semantics {
                        contentDescription = "View output for ${hostCommand.name}"
                    }
                ) {
                    Text(
                        text = if (canCopyOutput) "View output" else "View output (copy disabled)",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }

    // Long-press actions dialog
    if (showActionsDialog) {
        AlertDialog(
            onDismissRequest = { showActionsDialog = false },
            title = { Text("Command Actions") },
            text = {
                Text("Choose an action for \"${hostCommand.name}\" on ${hostCommand.nickname}:")
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            showActionsDialog = false
                            onEdit(hostCommand.mappingId)
                        }
                    ) {
                        Text("Edit")
                    }

                    TextButton(
                        onClick = {
                            showActionsDialog = false
                            showDeleteConfirm = true
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showActionsDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete command?") },
            text = {
                Text("This will remove the command \"${hostCommand.name}\" from ${hostCommand.nickname}.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(hostCommand.mappingId)
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

private fun formatRunDuration(elapsedMs: Long): String {
    if (elapsedMs <= 0) return "0s"

    val seconds = elapsedMs / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60

    return if (minutes > 0) {
        "${minutes}m ${remainingSeconds}s"
    } else {
        "${remainingSeconds}s"
    }
}
