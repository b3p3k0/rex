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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KeyManagementCard(
    keyStatus: String,
    keyProvisionedAt: Long?,
    publicKeyPreview: String?,
    hasKey: Boolean,
    provisionInProgress: Boolean,
    testInProgress: Boolean,
    onGenerateKey: () -> Unit,
    onImportKey: () -> Unit,
    onDeployKey: () -> Unit,
    onTestKey: () -> Unit,
    onDeleteKey: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SSH Key Management",
                style = MaterialTheme.typography.titleMedium
            )

            // Key Status
            KeyStatusSection(
                status = keyStatus,
                provisionedAt = keyProvisionedAt,
                publicKey = publicKeyPreview
            )

            // Action Buttons
            KeyActionButtons(
                keyStatus = keyStatus,
                hasKey = hasKey,
                provisionInProgress = provisionInProgress,
                testInProgress = testInProgress,
                onGenerateKey = onGenerateKey,
                onImportKey = onImportKey,
                onDeployKey = onDeployKey,
                onTestKey = onTestKey,
                onDeleteKey = onDeleteKey
            )
        }
    }
}

@Composable
private fun KeyStatusSection(
    status: String,
    provisionedAt: Long?,
    publicKey: String?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Status: ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            KeyStatusChip(status = status)
        }

        provisionedAt?.let { timestamp ->
            val locale = Locale.forLanguageTag(
                androidx.compose.ui.text.intl.Locale.current.toLanguageTag()
            )
            val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", locale)
            Text(
                text = "Last provisioned: ${formatter.format(Date(timestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        publicKey?.let { key ->
            PublicKeyPreview(publicKey = key)
        }
    }
}

@Composable
private fun KeyStatusChip(status: String) {
    val (text, color) = when (status) {
        "none" -> "No Key" to MaterialTheme.colorScheme.surfaceVariant
        "pending" -> "Pending Deployment" to MaterialTheme.colorScheme.secondary
        "success" -> "Deployed" to MaterialTheme.colorScheme.primary
        "failed" -> "Deployment Failed" to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.surfaceVariant
    }

    FilterChip(
        onClick = { },
        label = { Text(text) },
        selected = false,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = color,
            labelColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun PublicKeyPreview(publicKey: String) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Public Key",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(publicKey))
                }
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy public key",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Text(
            text = publicKey,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeyActionButtons(
    keyStatus: String,
    hasKey: Boolean,
    provisionInProgress: Boolean,
    testInProgress: Boolean,
    onGenerateKey: () -> Unit,
    onImportKey: () -> Unit,
    onDeployKey: () -> Unit,
    onTestKey: () -> Unit,
    onDeleteKey: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!hasKey) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGenerateKey,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.VpnKey, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Key")
                }

                OutlinedButton(
                    onClick = onImportKey,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Key")
                }
            }
        } else {
            // Key exists, show deployment and management actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDeployKey,
                    enabled = !provisionInProgress && keyStatus != "success",
                    modifier = Modifier.weight(1f)
                ) {
                    if (provisionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (provisionInProgress) "Deploying..." else "Deploy Key")
                }

                OutlinedButton(
                    onClick = onTestKey,
                    enabled = !testInProgress && keyStatus == "success",
                    modifier = Modifier.weight(1f)
                ) {
                    if (testInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Security, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (testInProgress) "Testing..." else "Test Auth")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onGenerateKey,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rotate Key")
                }

                OutlinedButton(
                    onClick = onDeleteKey,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Key")
                }
            }
        }
    }
}
