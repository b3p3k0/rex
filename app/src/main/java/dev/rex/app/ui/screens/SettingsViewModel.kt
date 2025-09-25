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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rex.app.core.GlobalCEH
import dev.rex.app.core.SecurityManager
import dev.rex.app.data.settings.SettingsStore
import dev.rex.app.data.settings.SettingsData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val securityManager: SecurityManager
) : ViewModel() {

    val settingsData: StateFlow<SettingsData> = settingsStore.allSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsData(
                screenCaptureProtection = SettingsStore.DEFAULT_SCREEN_CAPTURE_PROTECTION,
                allowCopyOutput = SettingsStore.DEFAULT_ALLOW_COPY_OUTPUT,
                credentialGateTtlMinutes = SettingsStore.DEFAULT_CREDENTIAL_GATE_TTL_MINUTES,
                defaultCommandTimeoutSeconds = SettingsStore.DEFAULT_COMMAND_TIMEOUT_SECONDS,
                logRetentionCount = SettingsStore.DEFAULT_LOG_RETENTION_COUNT,
                logRetentionAgeDays = SettingsStore.DEFAULT_LOG_RETENTION_AGE_DAYS,
                logRetentionSizeMb = SettingsStore.DEFAULT_LOG_RETENTION_SIZE_MB,
                hapticFeedbackLongPress = SettingsStore.DEFAULT_HAPTIC_FEEDBACK_LONG_PRESS
            )
        )

    fun setScreenCaptureProtection(enabled: Boolean) {
        viewModelScope.launch(GlobalCEH.handler) {
            settingsStore.setScreenCaptureProtection(enabled)
        }
    }

    fun setAllowCopyOutput(enabled: Boolean) {
        viewModelScope.launch(GlobalCEH.handler) {
            settingsStore.setAllowCopyOutput(enabled)
        }
    }

    fun setCredentialGateTtlMinutes(minutes: Int) {
        viewModelScope.launch(GlobalCEH.handler) {
            settingsStore.setCredentialGateTtlMinutes(minutes)
            // Update SecurityManager TTL immediately
            securityManager.setGateTtlMinutes(minutes)
        }
    }

    fun setDefaultCommandTimeoutSeconds(seconds: Int) {
        viewModelScope.launch(GlobalCEH.handler) {
            settingsStore.setDefaultCommandTimeoutSeconds(seconds)
        }
    }

    fun setLogRetentionCount(count: Int) {
        viewModelScope.launch(GlobalCEH.handler) {
            settingsStore.setLogRetentionCount(count)
        }
    }

    fun setLogRetentionAgeDays(days: Int) {
        viewModelScope.launch(GlobalCEH.handler) {
            settingsStore.setLogRetentionAgeDays(days)
        }
    }

    fun setLogRetentionSizeMb(sizeMb: Int) {
        viewModelScope.launch(GlobalCEH.handler) {
            settingsStore.setLogRetentionSizeMb(sizeMb)
        }
    }

    fun setHapticFeedbackLongPress(enabled: Boolean) {
        viewModelScope.launch(GlobalCEH.handler) {
            settingsStore.setHapticFeedbackLongPress(enabled)
        }
    }
}