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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages security gate state with TTL-based access control.
 * Provides in-memory gate state that expires after a configurable timeout.
 */
@Singleton
class SecurityManager @Inject constructor() {

    private val _gateState = MutableStateFlow(GateState.CLOSED)
    val gateState: StateFlow<GateState> = _gateState.asStateFlow()

    private var gateOpenTime: Long = 0L
    private var gateTtlMs: Long = DEFAULT_TTL_MS
    private var autoCloseJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Opens the security gate and starts TTL countdown
     */
    fun openGate() {
        synchronized(this) {
            gateOpenTime = System.currentTimeMillis()
            _gateState.value = GateState.OPEN
            Log.d("Rex", "Security gate opened, TTL: ${gateTtlMs}ms")

            // Cancel existing auto-close job and schedule new one
            autoCloseJob?.cancel()
            autoCloseJob = scope.launch {
                kotlinx.coroutines.delay(gateTtlMs)
                closeGateIfExpired()
            }
        }
    }

    /**
     * Closes the security gate immediately
     */
    fun closeGate() {
        synchronized(this) {
            autoCloseJob?.cancel()
            autoCloseJob = null
            gateOpenTime = 0L
            _gateState.value = GateState.CLOSED
            Log.d("Rex", "Security gate closed")
        }
    }

    /**
     * Checks if the security gate is currently open (within TTL)
     */
    fun isGateOpen(): Boolean {
        synchronized(this) {
            if (gateOpenTime == 0L) return false

            val elapsed = System.currentTimeMillis() - gateOpenTime
            val isOpen = elapsed < gateTtlMs

            if (!isOpen && _gateState.value == GateState.OPEN) {
                // Gate has expired, close it
                closeGate()
            }

            return isOpen
        }
    }

    /**
     * Gets the remaining TTL in milliseconds, or 0 if gate is closed
     */
    fun getRemainingTtlMs(): Long {
        synchronized(this) {
            if (gateOpenTime == 0L) return 0L

            val elapsed = System.currentTimeMillis() - gateOpenTime
            val remaining = gateTtlMs - elapsed

            return if (remaining > 0) remaining else 0L
        }
    }

    /**
     * Sets the TTL for the security gate in minutes
     */
    fun setGateTtlMinutes(minutes: Int) {
        require(minutes in 1..60) { "TTL must be between 1 and 60 minutes" }
        synchronized(this) {
            val oldTtlMs = gateTtlMs
            gateTtlMs = minutes * 60L * 1000L
            Log.d("Rex", "Security gate TTL changed: ${oldTtlMs}ms -> ${gateTtlMs}ms")

            // If gate is open, reschedule auto-close with new TTL
            if (_gateState.value == GateState.OPEN && gateOpenTime > 0L) {
                val elapsed = System.currentTimeMillis() - gateOpenTime
                val remaining = gateTtlMs - elapsed

                if (remaining <= 0) {
                    // TTL already expired with new setting
                    Log.w("Rex", "Gate closed due to TTL reduction")
                    closeGate()
                } else {
                    // Reschedule with remaining time
                    autoCloseJob?.cancel()
                    autoCloseJob = scope.launch {
                        kotlinx.coroutines.delay(remaining)
                        closeGateIfExpired()
                    }
                }
            }
        }
    }

    /**
     * Gets the current TTL in minutes
     */
    fun getGateTtlMinutes(): Int {
        synchronized(this) {
            return (gateTtlMs / (60L * 1000L)).toInt()
        }
    }

    /**
     * Forces the gate closed if the TTL has expired
     */
    private fun closeGateIfExpired() {
        synchronized(this) {
            if (gateOpenTime > 0L) {
                val elapsed = System.currentTimeMillis() - gateOpenTime
                if (elapsed >= gateTtlMs) {
                    closeGate()
                }
            }
        }
    }

    /**
     * Checks if a sensitive operation is allowed
     * This is a convenience method that combines gate check with logging
     */
    fun requireGateOpen(operation: String): Boolean {
        val isOpen = isGateOpen()
        if (isOpen) {
            Log.d("Rex", "Security gate check passed for operation: $operation")
        } else {
            Log.w("Rex", "Security gate check failed for operation: $operation")
        }
        return isOpen
    }

    companion object {
        private const val DEFAULT_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }
}

/**
 * Represents the current state of the security gate
 */
enum class GateState {
    OPEN,
    CLOSED
}