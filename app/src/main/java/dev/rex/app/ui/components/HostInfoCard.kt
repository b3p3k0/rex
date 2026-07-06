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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rex.app.data.db.HostEntity

@Composable
fun HostInfoCard(
    host: HostEntity,
    onEditHost: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Host Information",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = onEditHost,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit host configuration",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row {
                Text(
                    text = "Hostname: ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${host.hostname}:${host.port}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row {
                Text(
                    text = "Username: ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = host.username,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row {
                Text(
                    text = "Auth Method: ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = host.authMethod,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
