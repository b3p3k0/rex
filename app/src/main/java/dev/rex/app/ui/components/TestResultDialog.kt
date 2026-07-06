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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.rex.app.data.ssh.ProvisionResult

@Composable
fun TestResultDialog(
    inProgress: Boolean,
    result: ProvisionResult?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = if (inProgress) { {} } else onDismiss,
        title = { Text("Authentication Test") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (inProgress) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Testing SSH key authentication...")
                    }
                } else {
                    result?.let { testResult ->
                        Text(
                            text = if (testResult.success) "✓ Authentication successful!" else "✗ Authentication failed",
                            color = if (testResult.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )

                        Text(
                            text = "Duration: ${testResult.durationMs}ms",
                            style = MaterialTheme.typography.bodySmall
                        )

                        testResult.errorMessage?.let { error ->
                            Text(
                                text = "Error: $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (testResult.stdout.isNotBlank()) {
                            Text(
                                text = "Output: ${testResult.stdout}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!inProgress) {
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
