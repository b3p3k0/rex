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

package dev.rex.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "security_settings")

/**
 * Security-related settings using DataStore for persistence
 */
@Singleton
class SettingsStore @Inject constructor(
    private val context: Context
) {

    companion object {
        private val SCREEN_CAPTURE_PROTECTION = booleanPreferencesKey("screen_capture_protection")
        private val ALLOW_COPY_OUTPUT = booleanPreferencesKey("allow_copy_output")
        private val CREDENTIAL_GATE_TTL_MINUTES = intPreferencesKey("credential_gate_ttl_minutes")
        private val DEFAULT_COMMAND_TIMEOUT_SECONDS_KEY = intPreferencesKey("default_command_timeout_seconds")
        private val LOG_RETENTION_COUNT = intPreferencesKey("log_retention_count")
        private val LOG_RETENTION_AGE_DAYS = intPreferencesKey("log_retention_age_days")
        private val LOG_RETENTION_SIZE_MB = intPreferencesKey("log_retention_size_mb")
        private val HAPTIC_FEEDBACK_LONG_PRESS = booleanPreferencesKey("haptic_feedback_long_press")

        // Default values
        const val DEFAULT_SCREEN_CAPTURE_PROTECTION = true
        const val DEFAULT_ALLOW_COPY_OUTPUT = false
        const val DEFAULT_CREDENTIAL_GATE_TTL_MINUTES = 5
        const val DEFAULT_COMMAND_TIMEOUT_SECONDS = 60
        const val DEFAULT_LOG_RETENTION_COUNT = 1000
        const val DEFAULT_LOG_RETENTION_AGE_DAYS = 30
        const val DEFAULT_LOG_RETENTION_SIZE_MB = 50
        const val DEFAULT_HAPTIC_FEEDBACK_LONG_PRESS = true
    }

    /**
     * Screen capture protection setting
     */
    val screenCaptureProtection: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SCREEN_CAPTURE_PROTECTION] ?: DEFAULT_SCREEN_CAPTURE_PROTECTION
        }

    suspend fun setScreenCaptureProtection(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCREEN_CAPTURE_PROTECTION] = enabled
        }
    }

    /**
     * Allow copying output setting
     */
    val allowCopyOutput: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ALLOW_COPY_OUTPUT] ?: DEFAULT_ALLOW_COPY_OUTPUT
        }

    suspend fun setAllowCopyOutput(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ALLOW_COPY_OUTPUT] = enabled
        }
    }

    /**
     * Credential gate TTL in minutes
     */
    val credentialGateTtlMinutes: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[CREDENTIAL_GATE_TTL_MINUTES] ?: DEFAULT_CREDENTIAL_GATE_TTL_MINUTES
        }

    suspend fun setCredentialGateTtlMinutes(minutes: Int) {
        require(minutes in 1..60) { "TTL must be between 1 and 60 minutes" }
        context.dataStore.edit { preferences ->
            preferences[CREDENTIAL_GATE_TTL_MINUTES] = minutes
        }
    }

    /**
     * Default command timeout in seconds
     */
    val defaultCommandTimeoutSeconds: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[DEFAULT_COMMAND_TIMEOUT_SECONDS_KEY] ?: DEFAULT_COMMAND_TIMEOUT_SECONDS
        }

    suspend fun setDefaultCommandTimeoutSeconds(seconds: Int) {
        require(seconds in 10..3600) { "Timeout must be between 10 and 3600 seconds" }
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_COMMAND_TIMEOUT_SECONDS_KEY] = seconds
        }
    }

    /**
     * Log retention count limit
     */
    val logRetentionCount: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[LOG_RETENTION_COUNT] ?: DEFAULT_LOG_RETENTION_COUNT
        }

    suspend fun setLogRetentionCount(count: Int) {
        require(count in 100..10000) { "Log retention count must be between 100 and 10000" }
        context.dataStore.edit { preferences ->
            preferences[LOG_RETENTION_COUNT] = count
        }
    }

    /**
     * Log retention age limit in days
     */
    val logRetentionAgeDays: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[LOG_RETENTION_AGE_DAYS] ?: DEFAULT_LOG_RETENTION_AGE_DAYS
        }

    suspend fun setLogRetentionAgeDays(days: Int) {
        require(days in 1..365) { "Log retention age must be between 1 and 365 days" }
        context.dataStore.edit { preferences ->
            preferences[LOG_RETENTION_AGE_DAYS] = days
        }
    }

    /**
     * Log retention size limit in MB
     */
    val logRetentionSizeMb: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[LOG_RETENTION_SIZE_MB] ?: DEFAULT_LOG_RETENTION_SIZE_MB
        }

    suspend fun setLogRetentionSizeMb(sizeMb: Int) {
        require(sizeMb in 10..500) { "Log retention size must be between 10 and 500 MB" }
        context.dataStore.edit { preferences ->
            preferences[LOG_RETENTION_SIZE_MB] = sizeMb
        }
    }

    /**
     * Haptic feedback on long press setting
     */
    val hapticFeedbackLongPress: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[HAPTIC_FEEDBACK_LONG_PRESS] ?: DEFAULT_HAPTIC_FEEDBACK_LONG_PRESS
        }

    suspend fun setHapticFeedbackLongPress(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAPTIC_FEEDBACK_LONG_PRESS] = enabled
        }
    }

    /**
     * Get all settings as a data class for easier access
     */
    val allSettings: Flow<SettingsData> = context.dataStore.data
        .map { preferences ->
            SettingsData(
                screenCaptureProtection = preferences[SCREEN_CAPTURE_PROTECTION] ?: DEFAULT_SCREEN_CAPTURE_PROTECTION,
                allowCopyOutput = preferences[ALLOW_COPY_OUTPUT] ?: DEFAULT_ALLOW_COPY_OUTPUT,
                credentialGateTtlMinutes = preferences[CREDENTIAL_GATE_TTL_MINUTES] ?: DEFAULT_CREDENTIAL_GATE_TTL_MINUTES,
                defaultCommandTimeoutSeconds = preferences[DEFAULT_COMMAND_TIMEOUT_SECONDS_KEY] ?: DEFAULT_COMMAND_TIMEOUT_SECONDS,
                logRetentionCount = preferences[LOG_RETENTION_COUNT] ?: DEFAULT_LOG_RETENTION_COUNT,
                logRetentionAgeDays = preferences[LOG_RETENTION_AGE_DAYS] ?: DEFAULT_LOG_RETENTION_AGE_DAYS,
                logRetentionSizeMb = preferences[LOG_RETENTION_SIZE_MB] ?: DEFAULT_LOG_RETENTION_SIZE_MB,
                hapticFeedbackLongPress = preferences[HAPTIC_FEEDBACK_LONG_PRESS] ?: DEFAULT_HAPTIC_FEEDBACK_LONG_PRESS
            )
        }
}

/**
 * Data class representing all security settings
 */
data class SettingsData(
    val screenCaptureProtection: Boolean,
    val allowCopyOutput: Boolean,
    val credentialGateTtlMinutes: Int,
    val defaultCommandTimeoutSeconds: Int,
    val logRetentionCount: Int,
    val logRetentionAgeDays: Int,
    val logRetentionSizeMb: Int,
    val hapticFeedbackLongPress: Boolean
)