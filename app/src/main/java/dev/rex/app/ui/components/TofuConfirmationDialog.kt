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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.rex.app.data.ssh.HostPin

/** First-connection host identity pending user confirmation. */
data class TofuPrompt(
    val hostname: String,
    val port: Int,
    val pin: HostPin
)

/**
 * Trust-on-first-use confirmation. Shown when connecting to a host with no
 * pinned fingerprint; the connection is aborted until the user explicitly
 * trusts the presented host key.
 */
@Composable
fun TofuConfirmationDialog(
    prompt: TofuPrompt,
    onTrust: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Verify host identity") },
        text = {
            Column {
                Text(
                    text = "First connection to ${prompt.hostname}:${prompt.port}. " +
                        "The server presented this key fingerprint:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = prompt.pin.alg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = prompt.pin.sha256,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Compare it against the fingerprint reported on the server " +
                        "(ssh-keygen -lf /etc/ssh/ssh_host_*.pub). If it does not match, " +
                        "reject the connection — it may be intercepted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onTrust) {
                Text("Trust & connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("Cancel")
            }
        }
    )
}
