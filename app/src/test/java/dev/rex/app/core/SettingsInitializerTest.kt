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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SettingsInitializerTest {

    private lateinit var settingsStore: SettingsStore
    private lateinit var securityManager: SecurityManager
    private lateinit var settingsInitializer: SettingsInitializer

    @Before
    fun setup() {
        // Mock Android Log to prevent "not mocked" errors in unit tests
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        settingsStore = mockk()
        securityManager = mockk(relaxed = true)
        settingsInitializer = SettingsInitializer(settingsStore, securityManager)
    }

    @Test
    fun `initializes SecurityManager with persisted TTL`() = runTest {
        val customTtl = 10
        every { settingsStore.credentialGateTtlMinutes } returns flowOf(customTtl)

        settingsInitializer.initialize()

        // Wait for background coroutine
        kotlinx.coroutines.delay(100)

        verify { securityManager.setGateTtlMinutes(customTtl) }
    }

    @Test
    fun `falls back to defaults on DataStore corruption`() = runTest {
        every { settingsStore.credentialGateTtlMinutes } returns kotlinx.coroutines.flow.flow {
            throw IOException("DataStore corrupted")
        }

        settingsInitializer.initialize()

        // Wait for background coroutine and retry logic
        kotlinx.coroutines.delay(500)

        verify { securityManager.setGateTtlMinutes(SettingsStore.DEFAULT_CREDENTIAL_GATE_TTL_MINUTES) }
    }

    @Test
    fun `falls back to defaults on unexpected errors`() = runTest {
        every { settingsStore.credentialGateTtlMinutes } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("Unexpected error")
        }

        settingsInitializer.initialize()

        // Wait for background coroutine
        kotlinx.coroutines.delay(100)

        verify { securityManager.setGateTtlMinutes(SettingsStore.DEFAULT_CREDENTIAL_GATE_TTL_MINUTES) }
    }
}