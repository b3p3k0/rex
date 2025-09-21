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

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps exceptions to ExecError enums and user messages per SPEC §8
 */
object ErrorMapper {
    
    private val errorMappingTable = mapOf(
        UnknownHostException::class.java to Pair(ExecError.DNS, "Hostname not found."),
        ConnectException::class.java to Pair(ExecError.REFUSED, "Connection refused."),
        SocketTimeoutException::class.java to Pair(ExecError.TIMEOUT, "Timed out."),
        IOException::class.java to Pair(ExecError.IO, "Input/output error.")
    )
    
    fun mapException(throwable: Throwable): Pair<ExecError, String> {
        return when (throwable) {
            is UnknownHostException -> ExecError.DNS to "Hostname not found."
            is ConnectException -> ExecError.REFUSED to "Connection refused."
            is SocketTimeoutException -> ExecError.TIMEOUT to "Timed out."
            is IOException -> ExecError.IO to "Input/output error."
            else -> {
                // Check if it's a specialized SSH exception
                when (throwable::class.java.simpleName) {
                    "HostKeyMismatchException" -> ExecError.HOSTKEY_MISMATCH to "Host key mismatch."
                    "AuthException" -> ExecError.AUTH_FAILED to "Authentication failed."
                    else -> ExecError.IO to "Input/output error."
                }
            }
        }
    }
    
    fun getErrorMessage(execError: ExecError): String {
        return when (execError) {
            ExecError.DNS -> "Hostname not found."
            ExecError.REFUSED -> "Connection refused."
            ExecError.TIMEOUT -> "Timed out."
            ExecError.HOSTKEY_MISMATCH -> "Host key mismatch."
            ExecError.AUTH_FAILED -> "Authentication failed."
            ExecError.IO -> "Input/output error."
        }
    }
}