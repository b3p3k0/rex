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

package dev.rex.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import dev.rex.app.core.Gatekeeper
import dev.rex.app.core.SecurityGateRequiredException
import dev.rex.app.data.crypto.KeyVault
import dev.rex.app.data.crypto.KeyBlobId
import dev.rex.app.data.db.HostEntity
import dev.rex.app.data.repo.HostsRepository
import dev.rex.app.data.repo.KeysRepository
import dev.rex.app.data.ssh.SshProvisioner
import dev.rex.app.data.ssh.ProvisionResult
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HostDetailViewModelTest {

    private val mockHostsRepository = mockk<HostsRepository>()
    private val mockKeysRepository = mockk<KeysRepository>()
    private val mockKeyVault = mockk<KeyVault>()
    private val mockSshProvisioner = mockk<SshProvisioner>()
    private val mockGatekeeper = mockk<Gatekeeper>()
    private val mockSavedStateHandle = mockk<SavedStateHandle>()

    private lateinit var viewModel: HostDetailViewModel

    private val testHostId = "test-host-id"
    private val testHost = HostEntity(
        id = testHostId,
        nickname = "Test Host",
        hostname = "test.example.com",
        port = 22,
        username = "testuser",
        authMethod = "key",
        keyBlobId = "test-key-id",
        connectTimeoutMs = 5000,
        readTimeoutMs = 10000,
        strictHostKey = true,
        pinnedHostKeyFingerprint = "test-fingerprint",
        keyProvisionedAt = System.currentTimeMillis(),
        keyProvisionStatus = "success",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    @Before
    fun setup() {
        every { mockSavedStateHandle.get<String>("hostId") } returns testHostId
        coEvery { mockGatekeeper.requireGateForKeyOperation() } just Runs
        coEvery { mockHostsRepository.getHostById(testHostId) } returns testHost

        viewModel = HostDetailViewModel(
            mockHostsRepository,
            mockKeysRepository,
            mockKeyVault,
            mockSshProvisioner,
            mockGatekeeper,
            mockSavedStateHandle
        )
    }

    @Test
    fun `initial load displays host details`() = runTest {
        coEvery { mockKeyVault.getPublicKeyOpenssh(KeyBlobId("test-key-id")) } returns "ssh-ed25519 AAAAC3..."

        // Test the initial state after construction (loadHostDetails is called in init)
        val uiState = viewModel.uiState.value
        assertEquals("Host should be loaded", testHost, uiState.host)
        assertEquals("Key status should match", "success", uiState.keyStatus)
        assertNotNull("Key provisioned time should be set", uiState.keyProvisionedAt)
        assertFalse("Should not be loading", uiState.loading)
    }

    @Test
    fun `generateNewKey creates key and assigns to host`() = runTest {
        val keyBlobId = KeyBlobId("new-key-id")
        val publicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI..."

        coEvery { mockHostsRepository.getHostById(testHostId) } returns testHost
        coEvery { mockKeyVault.generateEd25519() } returns Pair(keyBlobId, publicKey)
        coEvery { mockHostsRepository.assignKeyToHost(testHostId, keyBlobId.id) } just Runs

        viewModel.generateNewKey()

        val uiState = viewModel.uiState.value
        assertEquals("Key status should be pending", "pending", uiState.keyStatus)
        assertEquals("Public key preview should be set", publicKey, uiState.publicKeyPreview)
        assertFalse("Should not be loading", uiState.loading)
        assertNull("Should not have error", uiState.error)

        coVerify { mockKeyVault.generateEd25519() }
        coVerify { mockHostsRepository.assignKeyToHost(testHostId, keyBlobId.id) }
    }

    @Test
    fun `generateNewKey handles security gate exception`() = runTest {
        coEvery { mockGatekeeper.requireGateForKeyOperation() } throws SecurityGateRequiredException("Auth required")

        viewModel.generateNewKey()

        val uiState = viewModel.uiState.value
        assertEquals("Should show authentication error", "Device authentication required", uiState.error)
        assertFalse("Should not be loading", uiState.loading)
    }

    @Test
    fun `importPrivateKey validates and imports PEM`() = runTest {
        val pemText = """
            -----BEGIN PRIVATE KEY-----
            MC4CAQAwBQYDK2VwBCIEINTuctv5E1hK1bbY8fdp+J6rCu6aUIgSFNnT0+77UjCa
            -----END PRIVATE KEY-----
        """.trimIndent()
        val keyBlobId = KeyBlobId("imported-key-id")

        viewModel.showImportDialog()
        viewModel.updateImportPemText(pemText)

        coEvery { mockKeyVault.validatePrivateKeyPem(any()) } returns true
        coEvery { mockKeyVault.importPrivateKeyPem(any()) } returns keyBlobId
        coEvery { mockHostsRepository.assignKeyToHost(testHostId, keyBlobId.id) } just Runs
        coEvery { mockKeyVault.getPublicKeyOpenssh(keyBlobId) } returns "ssh-ed25519 AAAAC3..."
        coEvery { mockHostsRepository.getHostById(testHostId) } returns testHost

        viewModel.importPrivateKey()

        val uiState = viewModel.uiState.value
        assertFalse("Import dialog should be hidden", uiState.showImportDialog)
        assertEquals("Key status should be pending", "pending", uiState.keyStatus)
        assertNull("Should not have import error", uiState.importError)

        coVerify { mockKeyVault.validatePrivateKeyPem(any()) }
        coVerify { mockKeyVault.importPrivateKeyPem(any()) }
        coVerify { mockHostsRepository.assignKeyToHost(testHostId, keyBlobId.id) }
    }

    @Test
    fun `importPrivateKey rejects invalid PEM`() = runTest {
        val invalidPem = "not a valid PEM"

        viewModel.showImportDialog()
        viewModel.updateImportPemText(invalidPem)

        coEvery { mockKeyVault.validatePrivateKeyPem(any()) } returns false

        viewModel.importPrivateKey()

        val uiState = viewModel.uiState.value
        assertEquals("Should show validation error", "Invalid PEM format", uiState.importError)
        assertTrue("Import dialog should remain open", uiState.showImportDialog)
    }

    @Test
    fun `deployKey successfully provisions key to host`() = runTest {
        val password = "test-password"
        val provisionResult = ProvisionResult(
            success = true,
            durationMs = 1500,
            stdout = "Key deployed successfully",
            stderr = "",
            errorMessage = null
        )

        coEvery { mockHostsRepository.getHostById(testHostId) } returns testHost
        coEvery { mockSshProvisioner.deployKeyToHost(any(), any(), any(), any(), any(), any()) } returns provisionResult
        coEvery { mockHostsRepository.updateKeyProvisionStatus(any(), any(), any()) } just Runs
        coEvery { mockKeysRepository.insertProvisionLog(any()) } just Runs

        viewModel.deployKey(password)

        val uiState = viewModel.uiState.value
        assertFalse("Should not be in progress", uiState.provisionInProgress)
        assertFalse("Password dialog should be hidden", uiState.showPasswordDialog)
        assertEquals("Should have provision result", provisionResult, uiState.lastProvisionResult)
        assertTrue("Should have provision output", uiState.provisionOutput.contains("Key deployed successfully"))

        coVerify {
            mockSshProvisioner.deployKeyToHost(
                hostname = testHost.hostname,
                port = testHost.port,
                username = testHost.username,
                password = password,
                keyBlobId = KeyBlobId(testHost.keyBlobId!!),
                timeoutsMs = Pair(testHost.connectTimeoutMs, testHost.readTimeoutMs)
            )
        }
        coVerify { mockHostsRepository.updateKeyProvisionStatus(testHostId, testHost.keyBlobId!!, "success") }
        coVerify { mockKeysRepository.insertProvisionLog(any()) }
    }

    @Test
    fun `testKeyAuth verifies SSH key authentication`() = runTest {
        val testResult = ProvisionResult(
            success = true,
            durationMs = 800,
            stdout = "SSH key authentication successful",
            stderr = "",
            errorMessage = null
        )

        coEvery { mockHostsRepository.getHostById(testHostId) } returns testHost
        coEvery { mockSshProvisioner.testKeyBasedAuth(any(), any(), any(), any(), any(), any()) } returns testResult

        viewModel.testKeyAuth()

        val uiState = viewModel.uiState.value
        assertTrue("Test dialog should be shown", uiState.showTestDialog)
        assertFalse("Should not be testing", uiState.testInProgress)
        assertEquals("Should have test result", testResult, uiState.testResult)

        coVerify {
            mockSshProvisioner.testKeyBasedAuth(
                hostname = testHost.hostname,
                port = testHost.port,
                username = testHost.username,
                keyBlobId = KeyBlobId(testHost.keyBlobId!!),
                timeoutsMs = Pair(testHost.connectTimeoutMs, testHost.readTimeoutMs),
                expectedPin = any()
            )
        }
    }

    @Test
    fun `deleteKey removes key and updates host`() = runTest {
        coEvery { mockHostsRepository.getHostById(testHostId) } returns testHost
        coEvery { mockKeysRepository.getKeyBlobById(testHost.keyBlobId!!) } returns mockk()
        coEvery { mockKeyVault.deleteKey(KeyBlobId(testHost.keyBlobId!!)) } just Runs
        coEvery { mockHostsRepository.updateKeyProvisionStatus(testHostId, null, "none") } just Runs

        viewModel.showDeleteConfirmation()
        viewModel.deleteKey()

        val uiState = viewModel.uiState.value
        assertFalse("Delete confirmation should be hidden", uiState.showDeleteConfirmation)
        assertEquals("Key status should be none", "none", uiState.keyStatus)
        assertNull("Public key preview should be cleared", uiState.publicKeyPreview)

        coVerify { mockKeyVault.deleteKey(KeyBlobId(testHost.keyBlobId!!)) }
        coVerify { mockHostsRepository.updateKeyProvisionStatus(testHostId, null, "none") }
    }

    @Test
    fun `error state management works correctly`() = runTest {
        viewModel.clearError()
        assertNull("Error should be cleared", viewModel.uiState.value.error)

        viewModel.clearProvisionOutput()
        assertEquals("Provision output should be empty", "", viewModel.uiState.value.provisionOutput)
        assertNull("Provision result should be cleared", viewModel.uiState.value.lastProvisionResult)
    }
}