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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    Log.i("Rex", "Screen: SettingsScreen")
    val settingsData by viewModel.settingsData.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Go back"
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security Section
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Security",
                        style = MaterialTheme.typography.titleMedium
                    )

                    SettingSwitch(
                        title = "Screen Capture Protection",
                        subtitle = "Prevents screenshots and screen recording",
                        checked = settingsData.screenCaptureProtection,
                        onCheckedChange = viewModel::setScreenCaptureProtection
                    )

                    SettingSwitch(
                        title = "Allow Copy Output",
                        subtitle = "Enable copying command output to clipboard",
                        checked = settingsData.allowCopyOutput,
                        onCheckedChange = viewModel::setAllowCopyOutput
                    )

                    SettingSlider(
                        title = "Credential Gate TTL",
                        subtitle = "Minutes before re-authentication required",
                        value = settingsData.credentialGateTtlMinutes,
                        valueRange = 1f..60f,
                        steps = 58,
                        onValueChange = { viewModel.setCredentialGateTtlMinutes(it.toInt()) },
                        valueText = "${settingsData.credentialGateTtlMinutes} min"
                    )

                    SettingSlider(
                        title = "Default Command Timeout",
                        subtitle = "Default timeout for command execution",
                        value = settingsData.defaultCommandTimeoutSeconds,
                        valueRange = 10f..300f,
                        steps = 29,
                        onValueChange = { viewModel.setDefaultCommandTimeoutSeconds(it.toInt()) },
                        valueText = "${settingsData.defaultCommandTimeoutSeconds}s"
                    )
                }
            }

            // Logs Section
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Logs & Storage",
                        style = MaterialTheme.typography.titleMedium
                    )

                    SettingSlider(
                        title = "Log Retention Count",
                        subtitle = "Maximum number of log entries to keep",
                        value = settingsData.logRetentionCount,
                        valueRange = 100f..5000f,
                        steps = 49,
                        onValueChange = { viewModel.setLogRetentionCount(it.toInt()) },
                        valueText = "${settingsData.logRetentionCount} entries"
                    )

                    SettingSlider(
                        title = "Log Retention Age",
                        subtitle = "Maximum age of log entries in days",
                        value = settingsData.logRetentionAgeDays,
                        valueRange = 1f..90f,
                        steps = 88,
                        onValueChange = { viewModel.setLogRetentionAgeDays(it.toInt()) },
                        valueText = "${settingsData.logRetentionAgeDays} days"
                    )

                    SettingSlider(
                        title = "Log Retention Size",
                        subtitle = "Maximum total size of logs in MB",
                        value = settingsData.logRetentionSizeMb,
                        valueRange = 10f..200f,
                        steps = 19,
                        onValueChange = { viewModel.setLogRetentionSizeMb(it.toInt()) },
                        valueText = "${settingsData.logRetentionSizeMb} MB"
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingSlider(
    title: String,
    subtitle: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueText: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}