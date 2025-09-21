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

enum class ExecError {
    DNS,
    REFUSED,
    TIMEOUT,
    HOSTKEY_MISMATCH,
    AUTH_FAILED,
    IO
}