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

import org.junit.Test
import org.junit.Assert.*
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMapperTest {

    @Test
    fun `mapException maps UnknownHostException to DNS error`() {
        val exception = UnknownHostException("example.com")
        val (error, message) = ErrorMapper.mapException(exception)
        
        assertEquals(ExecError.DNS, error)
        assertEquals("Hostname not found.", message)
    }

    @Test
    fun `mapException maps ConnectException to REFUSED error`() {
        val exception = ConnectException("Connection refused")
        val (error, message) = ErrorMapper.mapException(exception)
        
        assertEquals(ExecError.REFUSED, error)
        assertEquals("Connection refused.", message)
    }

    @Test
    fun `mapException maps SocketTimeoutException to TIMEOUT error`() {
        val exception = SocketTimeoutException("Read timed out")
        val (error, message) = ErrorMapper.mapException(exception)
        
        assertEquals(ExecError.TIMEOUT, error)
        assertEquals("Timed out.", message)
    }

    @Test
    fun `mapException maps IOException to IO error`() {
        val exception = IOException("I/O error")
        val (error, message) = ErrorMapper.mapException(exception)
        
        assertEquals(ExecError.IO, error)
        assertEquals("Input/output error.", message)
    }

    @Test
    fun `mapException maps unknown exception to IO error`() {
        val exception = RuntimeException("Unknown error")
        val (error, message) = ErrorMapper.mapException(exception)
        
        assertEquals(ExecError.IO, error)
        assertEquals("Input/output error.", message)
    }

    @Test
    fun `getErrorMessage returns correct messages for all error types`() {
        assertEquals("Hostname not found.", ErrorMapper.getErrorMessage(ExecError.DNS))
        assertEquals("Connection refused.", ErrorMapper.getErrorMessage(ExecError.REFUSED))
        assertEquals("Timed out.", ErrorMapper.getErrorMessage(ExecError.TIMEOUT))
        assertEquals("Host key mismatch.", ErrorMapper.getErrorMessage(ExecError.HOSTKEY_MISMATCH))
        assertEquals("Authentication failed.", ErrorMapper.getErrorMessage(ExecError.AUTH_FAILED))
        assertEquals("Input/output error.", ErrorMapper.getErrorMessage(ExecError.IO))
    }
}