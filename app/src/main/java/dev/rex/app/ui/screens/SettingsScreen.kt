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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rex.app.data.settings.ThemeMode
import kotlin.math.roundToInt

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

                    SettingSwitch(
                        title = "Haptic Feedback on Long Press",
                        subtitle = "Vibrate when long-pressing command cards",
                        checked = settingsData.hapticFeedbackLongPress,
                        onCheckedChange = viewModel::setHapticFeedbackLongPress
                    )

                    ThemeSelector(
                        title = "Theme",
                        subtitle = "Choose app appearance",
                        selectedMode = settingsData.themeMode,
                        onModeSelected = viewModel::setThemeMode
                    )

                    SettingSlider(
                        title = "Credential Gate TTL",
                        subtitle = "Minutes before re-authentication required",
                        value = settingsData.credentialGateTtlMinutes,
                        valueRange = 1f..60f,
                        onValueChange = viewModel::setCredentialGateTtlMinutes,
                        valueText = "${settingsData.credentialGateTtlMinutes} min"
                    )

                    SettingSlider(
                        title = "Default Command Timeout",
                        subtitle = "Default timeout for command execution",
                        value = settingsData.defaultCommandTimeoutSeconds,
                        valueRange = 10f..300f,
                        onValueChange = viewModel::setDefaultCommandTimeoutSeconds,
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
                        onValueChange = viewModel::setLogRetentionCount,
                        valueText = "${settingsData.logRetentionCount} entries"
                    )

                    SettingSlider(
                        title = "Log Retention Age",
                        subtitle = "Maximum age of log entries in days",
                        value = settingsData.logRetentionAgeDays,
                        valueRange = 1f..90f,
                        onValueChange = viewModel::setLogRetentionAgeDays,
                        valueText = "${settingsData.logRetentionAgeDays} days"
                    )

                    SettingSlider(
                        title = "Log Retention Size",
                        subtitle = "Maximum total size of logs in MB",
                        value = settingsData.logRetentionSizeMb,
                        valueRange = 10f..200f,
                        onValueChange = viewModel::setLogRetentionSizeMb,
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
    onValueChange: (Int) -> Unit,
    valueText: String
) {
    var lastDispatched by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        lastDispatched = value
    }

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
            onValueChange = { raw ->
                val min = valueRange.start.roundToInt()
                val max = valueRange.endInclusive.roundToInt()
                val rounded = raw.roundToInt().coerceIn(min, max)
                if (rounded != lastDispatched) {
                    lastDispatched = rounded
                    onValueChange(rounded)
                }
            },
            valueRange = valueRange
        )
    }
}

@Composable
private fun ThemeSelector(
    title: String,
    subtitle: String,
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .semantics { contentDescription = "Theme selection" },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.values().forEach { mode ->
                val isSelected = selectedMode == mode
                val label = when (mode) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }

                FilterChip(
                    onClick = { onModeSelected(mode) },
                    label = { Text(label) },
                    selected = isSelected,
                    modifier = Modifier
                        .selectable(
                            selected = isSelected,
                            onClick = { onModeSelected(mode) },
                            role = Role.RadioButton
                        )
                        .semantics {
                            contentDescription = "$label theme mode"
                        }
                )
            }
        }
    }
}
