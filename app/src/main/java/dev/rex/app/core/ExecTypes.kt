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

sealed interface ExecStatus {
    data object Connecting : ExecStatus
    data object Running : ExecStatus
    data class Completed(val exitCode: Int) : ExecStatus
    data class Failed(val reason: ExecError) : ExecStatus
    data object Cancelled : ExecStatus
}

/**
 * Connection state tracking for SSH operations
 */
sealed class SshConnectionState {
    data object Idle : SshConnectionState()
    data object Connecting : SshConnectionState()
    data object Authenticating : SshConnectionState()
    data object Connected : SshConnectionState()
    data class Failed(val error: String) : SshConnectionState()
}

/**
 * Command execution state tracking with real-time feedback
 */
sealed class CommandExecutionState {
    data object Idle : CommandExecutionState()
    data object Starting : CommandExecutionState()
    data class Streaming(val output: String) : CommandExecutionState()
    data class Completed(val exitCode: Int, val output: String) : CommandExecutionState()
    data object Cancelling : CommandExecutionState()
    data class Cancelled(val partialOutput: String) : CommandExecutionState()
    data class Failed(val error: String) : CommandExecutionState()
}

/**
 * Provisioning step status for progress tracking
 */
data class ProvisioningStep(
    val name: String,
    val status: StepStatus = StepStatus.Pending
)

enum class StepStatus { Pending, InProgress, Completed, Failed }

enum class ExecError {
    DNS,
    REFUSED,
    TIMEOUT,
    HOSTKEY_MISMATCH,
    AUTH_FAILED,
    IO
}