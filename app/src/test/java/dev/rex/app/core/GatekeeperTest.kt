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
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class GatekeeperTest {

    private lateinit var securityManager: SecurityManager
    private lateinit var gatekeeper: Gatekeeper

    @Before
    fun setup() {
        // Mock Android Log to prevent "not mocked" errors in unit tests
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        securityManager = SecurityManager()
        gatekeeper = Gatekeeper(securityManager)
    }

    @Test
    fun `blocks key operations when gate is closed`() = runTest {
        var exceptionThrown = false
        try {
            gatekeeper.requireGateForKeyOperation()
        } catch (e: SecurityGateRequiredException) {
            exceptionThrown = true
        }
        assert(exceptionThrown) { "Expected SecurityGateRequiredException" }
    }

    @Test
    fun `blocks dangerous commands when gate is closed`() = runTest {
        var exceptionThrown = false
        try {
            gatekeeper.requireGateForDangerousCommand()
        } catch (e: SecurityGateRequiredException) {
            exceptionThrown = true
        }
        assert(exceptionThrown) { "Expected SecurityGateRequiredException" }
    }

    @Test
    fun `blocks TOFU trust when gate is closed`() = runTest {
        var exceptionThrown = false
        try {
            gatekeeper.requireGateForTofuTrust()
        } catch (e: SecurityGateRequiredException) {
            exceptionThrown = true
        }
        assert(exceptionThrown) { "Expected SecurityGateRequiredException" }
    }

    @Test
    fun `allows key operations when gate is open`() = runTest {
        securityManager.openGate()

        // Should not throw
        gatekeeper.requireGateForKeyOperation()
    }

    @Test
    fun `allows dangerous commands when gate is open`() = runTest {
        securityManager.openGate()

        // Should not throw
        gatekeeper.requireGateForDangerousCommand()
    }

    @Test
    fun `allows TOFU trust when gate is open`() = runTest {
        securityManager.openGate()

        // Should not throw
        gatekeeper.requireGateForTofuTrust()
    }

    @Test
    fun `blocks operations after gate closes`() = runTest {
        securityManager.openGate()
        securityManager.closeGate()

        var exceptionThrown = false
        try {
            gatekeeper.requireGateForKeyOperation()
        } catch (e: SecurityGateRequiredException) {
            exceptionThrown = true
        }
        assert(exceptionThrown) { "Expected SecurityGateRequiredException" }
    }
}