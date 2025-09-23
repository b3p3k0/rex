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

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.rex.app.data.db.HostCommandMapping

@Composable
fun HostCommandRow(
    hostCommand: HostCommandMapping,
    onExecute: (HostCommandMapping) -> Unit,
    onEdit: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { 
                contentDescription = "Host command: ${hostCommand.nickname} ${hostCommand.name}" 
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${hostCommand.nickname} • ${hostCommand.name}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = hostCommand.hostname,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onEdit(hostCommand.mappingId) },
                    modifier = Modifier.semantics {
                        contentDescription = "Edit ${hostCommand.name}"
                    }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "Delete ${hostCommand.name}"
                    }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }

                Button(
                    onClick = { onExecute(hostCommand) },
                    modifier = Modifier.semantics {
                        contentDescription = "Run ${hostCommand.name} on ${hostCommand.nickname}"
                    }
                ) {
                    Text("Run")
                }
            }
        }
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