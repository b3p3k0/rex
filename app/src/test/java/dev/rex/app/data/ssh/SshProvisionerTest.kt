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

import dev.rex.app.core.Gatekeeper
import dev.rex.app.data.crypto.KeyVault
import dev.rex.app.data.crypto.KeyBlobId
import dev.rex.app.data.repo.HostsRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SshProvisionerTest {

    private val mockGatekeeper = mockk<Gatekeeper>()
    private val mockKeyVault = mockk<KeyVault>()
    private val mockHostKeyVerifier = mockk<HostKeyVerifier>()
    private val mockHostsRepository = mockk<HostsRepository>()

    private lateinit var sshProvisioner: SshProvisioner

    @Before
    fun setup() {
        sshProvisioner = SshProvisioner(
            mockGatekeeper,
            mockKeyVault,
            mockHostKeyVerifier,
            mockHostsRepository
        )

        coEvery { mockGatekeeper.requireGateForKeyOperation() } just Runs
    }

    @Test
    fun `deployKeyToHost calls gatekeeper for security`() = runTest {
        // Arrange
        val keyBlobId = KeyBlobId("test-key-id")
        val publicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI... test@example.com"

        coEvery { mockKeyVault.getPublicKeyOpenssh(keyBlobId) } returns publicKey

        // Act
        try {
            sshProvisioner.deployKeyToHost(
                hostname = "test.example.com",
                port = 22,
                username = "testuser",
                password = "testpass",
                keyBlobId = keyBlobId,
                timeoutsMs = Pair(5000, 10000)
            )
        } catch (e: Exception) {
            // Expected to fail due to missing SSH client mock
        }

        // Assert
        coVerify { mockGatekeeper.requireGateForKeyOperation() }
        coVerify { mockKeyVault.getPublicKeyOpenssh(keyBlobId) }
    }

    @Test
    fun `testKeyBasedAuth calls gatekeeper for security`() = runTest {
        // Arrange
        val keyBlobId = KeyBlobId("test-key-id")
        val privateKey = "-----BEGIN PRIVATE KEY-----\n...test key...\n-----END PRIVATE KEY-----".toByteArray()

        coEvery { mockKeyVault.decryptPrivateKey(keyBlobId) } returns privateKey

        // Act
        try {
            sshProvisioner.testKeyBasedAuth(
                hostname = "test.example.com",
                port = 22,
                username = "testuser",
                keyBlobId = keyBlobId,
                timeoutsMs = Pair(5000, 10000),
                expectedPin = null
            )
        } catch (e: Exception) {
            // Expected to fail due to missing SSH client mock
        }

        // Assert
        coVerify { mockGatekeeper.requireGateForKeyOperation() }
        coVerify { mockKeyVault.decryptPrivateKey(keyBlobId) }
    }

    @Test
    fun `provisioner returns failure result on exception`() = runTest {
        // Arrange
        val keyBlobId = KeyBlobId("test-key-id")
        coEvery { mockKeyVault.getPublicKeyOpenssh(keyBlobId) } throws RuntimeException("Test error")

        // Act
        val result = sshProvisioner.deployKeyToHost(
            hostname = "test.example.com",
            port = 22,
            username = "testuser",
            password = "testpass",
            keyBlobId = keyBlobId,
            timeoutsMs = Pair(5000, 10000)
        )

        // Assert
        assertFalse("Deployment should fail", result.success)
        assertEquals("Error message should match", "Test error", result.errorMessage)
        assertTrue("Duration should be positive", result.durationMs > 0)
    }

    @Test
    fun `provisioner creates proper ProvisionResult structure`() = runTest {
        // Arrange
        val keyBlobId = KeyBlobId("test-key-id")
        coEvery { mockKeyVault.decryptPrivateKey(keyBlobId) } throws RuntimeException("Auth failed")

        // Act
        val result = sshProvisioner.testKeyBasedAuth(
            hostname = "test.example.com",
            port = 22,
            username = "testuser",
            keyBlobId = keyBlobId,
            timeoutsMs = Pair(5000, 10000),
            expectedPin = null
        )

        // Assert
        assertFalse("Authentication test should fail", result.success)
        assertEquals("Error message should match", "Auth failed", result.errorMessage)
        assertTrue("Duration should be positive", result.durationMs > 0)
        assertTrue("Stdout should be non-null", result.stdout != null)
        assertTrue("Stderr should be non-null", result.stderr != null)
    }
}