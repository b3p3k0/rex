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

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-UI service that enforces security gate requirements for sensitive operations.
 * Provides fail-closed enforcement that cannot be bypassed by direct repository calls.
 */
@Singleton
class Gatekeeper @Inject constructor(
    private val securityManager: SecurityManager
) {

    /**
     * Requires gate to be open for the specified operation
     * @throws SecurityGateRequiredException if gate is closed
     */
    suspend fun requireGate(operationId: String) {
        if (!securityManager.requireGateOpen(operationId)) {
            throw SecurityGateRequiredException("Operation $operationId requires authentication")
        }
    }

    /**
     * Gate check for key operations (import, delete, generate)
     */
    suspend fun requireGateForKeyOperation() = requireGate("KEY_OPERATION")

    /**
     * Gate check for TOFU trust decisions
     */
    suspend fun requireGateForTofuTrust() = requireGate("TOFU_TRUST")

    /**
     * Gate check for dangerous command execution
     */
    suspend fun requireGateForDangerousCommand() = requireGate("DANGEROUS_COMMAND")
}

/**
 * Exception thrown when security gate is required but not open
 */
class SecurityGateRequiredException(message: String) : Exception(message)