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

package dev.rex.app.core

import android.util.Log
import dev.rex.app.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initializes security settings on application startup.
 * Ensures SecurityManager TTL is synced with persisted settings.
 */
@Singleton
class SettingsInitializer @Inject constructor(
    private val settingsStore: SettingsStore,
    private val securityManager: SecurityManager,
    private val scope: CoroutineScope
) {

    fun initialize() {
        scope.launch {
            try {
                val ttlMinutes = retryWithBackoff(maxRetries = 3) {
                    settingsStore.credentialGateTtlMinutes.first()
                }
                securityManager.setGateTtlMinutes(ttlMinutes)
                Log.i("Rex", "SecurityManager initialized with TTL: ${ttlMinutes} minutes")
            } catch (e: Exception) {
                Log.e("Rex", "Failed to initialize settings, using defaults", e)
                securityManager.setGateTtlMinutes(SettingsStore.DEFAULT_CREDENTIAL_GATE_TTL_MINUTES)
            }
        }
    }

    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 100,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: IOException) {
                if (attempt == maxRetries - 1) throw e
                Log.w("Rex", "DataStore read attempt ${attempt + 1} failed, retrying in ${currentDelay}ms", e)
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        error("Should not reach here")
    }
}
