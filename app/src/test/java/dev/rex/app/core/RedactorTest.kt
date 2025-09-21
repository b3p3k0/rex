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

class RedactorTest {

    @Test
    fun `redacts password patterns`() {
        val input = "user=admin password=secret123"
        val result = Redactor.redact(input)
        assertEquals("user=admin [REDACTED]", result)
    }

    @Test
    fun `redacts token patterns`() {
        val input = "Authorization: Bearer abc123def456"
        val result = Redactor.redact(input)
        assertEquals("Authorization: [REDACTED]", result)
    }

    @Test
    fun `redacts API key patterns`() {
        val input = "api_key=sk_test_123456789abcdef"
        val result = Redactor.redact(input)
        assertEquals("[REDACTED]", result)
    }

    @Test
    fun `redacts hex patterns`() {
        val input = "Hash: 1234567890abcdef1234567890abcdef12345678"
        val result = Redactor.redact(input)
        assertEquals("Hash: [REDACTED]", result)
    }

    @Test
    fun `redacts multiple patterns in same string`() {
        val input = "password=secret token:abc123 hash=def456789012345678901234567890123456"
        val result = Redactor.redact(input)
        assertEquals("[REDACTED] [REDACTED] hash=[REDACTED]", result)
    }

    @Test
    fun `does not redact safe content`() {
        val input = "This is a safe message with no secrets"
        val result = Redactor.redact(input)
        assertEquals(input, result)
    }

    @Test
    fun `containsSensitiveData detects patterns correctly`() {
        assertTrue(Redactor.containsSensitiveData("password=secret"))
        assertTrue(Redactor.containsSensitiveData("token: abc123"))
        assertTrue(Redactor.containsSensitiveData("bearer xyz789"))
        assertTrue(Redactor.containsSensitiveData("hash 1234567890abcdef1234567890abcdef"))
        assertFalse(Redactor.containsSensitiveData("This is safe content"))
    }

    @Test
    fun `handles case insensitive patterns`() {
        val input = "PASSWORD=secret TOKEN:abc123 Bearer xyz789"
        val result = Redactor.redact(input)
        assertEquals("[REDACTED] [REDACTED] [REDACTED]", result)
    }
}
