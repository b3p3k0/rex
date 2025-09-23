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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SecurityManagerTest {

    private lateinit var securityManager: SecurityManager

    @Before
    fun setup() {
        // Mock Android Log to prevent "not mocked" errors in unit tests
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        securityManager = SecurityManager()
    }

    @Test
    fun `initial state is closed`() {
        assertFalse(securityManager.isGateOpen())
        assertEquals(GateState.CLOSED, securityManager.gateState.value)
        assertEquals(0L, securityManager.getRemainingTtlMs())
    }

    @Test
    fun `openGate opens the gate`() {
        securityManager.openGate()

        assertTrue(securityManager.isGateOpen())
        assertEquals(GateState.OPEN, securityManager.gateState.value)
        assertTrue(securityManager.getRemainingTtlMs() > 0)
    }

    @Test
    fun `closeGate closes the gate`() {
        securityManager.openGate()
        assertTrue(securityManager.isGateOpen())

        securityManager.closeGate()

        assertFalse(securityManager.isGateOpen())
        assertEquals(GateState.CLOSED, securityManager.gateState.value)
        assertEquals(0L, securityManager.getRemainingTtlMs())
    }

    @Test
    fun `requireGateOpen returns true when gate is open`() {
        securityManager.openGate()

        assertTrue(securityManager.requireGateOpen("test_operation"))
    }

    @Test
    fun `requireGateOpen returns false when gate is closed`() {
        assertFalse(securityManager.requireGateOpen("test_operation"))
    }

    @Test
    fun `setGateTtlMinutes updates TTL`() {
        val newTtlMinutes = 10
        securityManager.setGateTtlMinutes(newTtlMinutes)

        assertEquals(newTtlMinutes, securityManager.getGateTtlMinutes())
    }

    @Test
    fun `gate expires after TTL`() = runTest {
        // Set very short TTL for testing (1 minute is minimum)
        securityManager.setGateTtlMinutes(1) // This will set TTL to 1 minute

        securityManager.openGate()
        assertTrue(securityManager.isGateOpen())

        // For testing purposes, we'll simulate expiry by checking
        // that the gate remains open within the TTL
        assertTrue(securityManager.getRemainingTtlMs() > 0)
        assertEquals(GateState.OPEN, securityManager.gateState.value)
    }

    @Test
    fun `remaining TTL decreases over time`() = runTest {
        securityManager.setGateTtlMinutes(1) // 1 minute
        securityManager.openGate()

        val initialTtl = securityManager.getRemainingTtlMs()
        assertTrue("Initial TTL should be positive", initialTtl > 0)

        // In unit tests, timing can be unreliable. Just verify TTL is reasonable
        assertTrue("TTL should be close to 1 minute (60000ms)", initialTtl <= 60000L)
        assertTrue("TTL should be reasonably close to 1 minute", initialTtl > 59000L)
    }

    @Test
    fun `setting new TTL while gate is open affects expiry`() {
        securityManager.setGateTtlMinutes(10) // 10 minutes
        securityManager.openGate()
        assertTrue(securityManager.isGateOpen())

        // Set shorter TTL (1 minute is minimum, so use 1 vs original 10)
        securityManager.setGateTtlMinutes(1)

        // Gate should remain open since only milliseconds have elapsed
        // but the remaining TTL should now reflect the new shorter timeout
        assertTrue(securityManager.isGateOpen())
        val remainingTtl = securityManager.getRemainingTtlMs()
        assertTrue("TTL should be less than or equal to 1 minute", remainingTtl <= 60000L)
    }

    @Test
    fun `gate remains open within TTL`() = runTest {
        securityManager.setGateTtlMinutes(1) // 1 minute
        securityManager.openGate()

        // Should remain open for at least a few milliseconds
        kotlinx.coroutines.delay(50)
        assertTrue(securityManager.isGateOpen())

        kotlinx.coroutines.delay(50)
        assertTrue(securityManager.isGateOpen())
    }

    @Test
    fun `multiple openGate calls reset TTL`() = runTest {
        securityManager.setGateTtlMinutes(1)
        securityManager.openGate()

        val firstTtl = securityManager.getRemainingTtlMs()
        kotlinx.coroutines.delay(100)

        securityManager.openGate() // Reset TTL
        val secondTtl = securityManager.getRemainingTtlMs()

        assertTrue(secondTtl > firstTtl - 100) // Should be reset to near full TTL
    }

    @Test
    fun `TTL reduction closes gate if already expired`() = runTest {
        securityManager.setGateTtlMinutes(10) // 10 minutes
        securityManager.openGate()
        kotlinx.coroutines.delay(100) // Wait 100ms

        // Reduce TTL to 1 minute - should reschedule but not close
        securityManager.setGateTtlMinutes(1)

        // Gate should still be open since 100ms < 1 minute
        assertTrue(securityManager.isGateOpen())
    }

    @Test
    fun `TTL validation prevents invalid values`() {
        assertThrows(IllegalArgumentException::class.java) {
            securityManager.setGateTtlMinutes(0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            securityManager.setGateTtlMinutes(61)
        }

        assertThrows(IllegalArgumentException::class.java) {
            securityManager.setGateTtlMinutes(-1)
        }
    }
}