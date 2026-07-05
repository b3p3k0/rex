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

package dev.rex.app.data.ssh

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class HostKeyVerifierTest {

    private val verifier = HostKeyVerifierImpl()

    @Before
    fun setup() {
        // android.util.Base64 is a throwing stub in unit tests; delegate to java.util.Base64
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().withoutPadding().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @Test
    fun `computeFingerprint generates consistent hash`() {
        val testKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI4lFz6vMw+p9IelYPiRPL8H5Vx3N4O1sGzCWbHm5vqYz test@example.com".toByteArray()
        
        val fingerprint1 = verifier.computeFingerprint(testKey)
        val fingerprint2 = verifier.computeFingerprint(testKey)
        
        assertEquals("Fingerprints should be identical", fingerprint1, fingerprint2)
        assertEquals("Algorithm should be ed25519", "ssh-ed25519", fingerprint1.alg)
        assertNotNull("SHA256 should not be null", fingerprint1.sha256)
    }

    @Test
    fun `verifyPinned returns true for matching pins`() {
        val pin1 = HostPin("ssh-ed25519", "ABC123DEF456")
        val pin2 = HostPin("ssh-ed25519", "ABC123DEF456")
        
        assertTrue("Matching pins should verify", verifier.verifyPinned(pin1, pin2))
    }

    @Test
    fun `verifyPinned returns false for different algorithms`() {
        val pin1 = HostPin("ssh-ed25519", "ABC123DEF456")
        val pin2 = HostPin("ssh-rsa", "ABC123DEF456")
        
        assertFalse("Different algorithms should not verify", verifier.verifyPinned(pin1, pin2))
    }

    @Test
    fun `verifyPinned returns false for different fingerprints`() {
        val pin1 = HostPin("ssh-ed25519", "ABC123DEF456")
        val pin2 = HostPin("ssh-ed25519", "XYZ789UVW012")
        
        assertFalse("Different fingerprints should not verify", verifier.verifyPinned(pin1, pin2))
    }

    @Test
    fun `computeFingerprint falls back to raw key hashing for invalid OpenSSH format`() {
        // computeFingerprint deliberately does not throw: input that fails OpenSSH
        // parsing is treated as a raw transport key and hashed directly
        val invalidKey = "not-a-valid-key".toByteArray()

        val fingerprint = verifier.computeFingerprint(invalidKey)

        assertTrue("Fingerprint should be SHA256-prefixed", fingerprint.sha256.startsWith("SHA256:"))
        assertEquals("Raw fallback should be deterministic", fingerprint, verifier.computeFingerprint(invalidKey))
    }
}