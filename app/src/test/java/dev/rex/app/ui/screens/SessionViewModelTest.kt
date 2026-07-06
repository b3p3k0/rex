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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import dev.rex.app.core.DangerousCommandValidator
import dev.rex.app.core.Gatekeeper
import dev.rex.app.core.SecurityGateRequiredException
import dev.rex.app.data.crypto.KeyVault
import dev.rex.app.data.db.HostCommandMapping
import dev.rex.app.data.repo.HostCommandRepository
import dev.rex.app.data.repo.HostsRepository
import dev.rex.app.data.settings.SettingsStore
import dev.rex.app.data.ssh.HostPin
import dev.rex.app.data.ssh.SshClient
import dev.rex.app.data.ssh.TofuRequiredException
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val mockContext = mockk<Context>()
    private val mockSshClient = mockk<SshClient>()
    private val mockSavedStateHandle = mockk<SavedStateHandle>()
    private val mockGatekeeper = mockk<Gatekeeper>()
    private val mockSettingsStore = mockk<SettingsStore>()
    private val mockDangerousCommandValidator = mockk<DangerousCommandValidator>()
    private val mockHostCommandRepository = mockk<HostCommandRepository>()
    private val mockKeyVault = mockk<KeyVault>()
    private val mockHostsRepository = mockk<HostsRepository>()

    private val testMappingId = "mapping-1"

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // Mock Android Log to prevent "not mocked" errors in unit tests
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        // Defaults for a successful key-auth session; tests override as needed
        coEvery { mockHostCommandRepository.getHostCommandMapping(testMappingId) } returns createMapping()
        every { mockDangerousCommandValidator.isDangerous(any()) } returns false
        coEvery { mockGatekeeper.requireGateForDangerousCommand() } just Runs
        coEvery { mockSshClient.connect(any(), any(), any(), any()) } returns HostPin("ssh-ed25519", "SHA256:abc")
        coEvery { mockKeyVault.decryptPrivateKey(any()) } returns "key-bytes".toByteArray()
        coEvery { mockSshClient.authUsernameKey(any(), any()) } just Runs
        every { mockSshClient.exec(any(), any()) } returns flowOf("hello\n".encodeUtf8())
        coEvery { mockSshClient.waitExitCode(any()) } returns 0
        coEvery { mockSshClient.cancel() } just Runs
        every { mockSshClient.close() } just Runs
        every { mockSettingsStore.allowCopyOutput } returns flowOf(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SessionViewModel(
        mockContext,
        mockSshClient,
        mockSavedStateHandle,
        mockGatekeeper,
        mockSettingsStore,
        mockDangerousCommandValidator,
        mockHostCommandRepository,
        mockKeyVault,
        mockHostsRepository
    )

    private fun createMapping(
        authMethod: String = "key",
        keyBlobId: String? = "key-1",
        command: String = "uptime",
        pinnedFingerprint: String? = "SHA256:abc"
    ) = HostCommandMapping(
        id = "host-1",
        nickname = "Test Host",
        hostname = "test.example.com",
        port = 22,
        username = "testuser",
        authMethod = authMethod,
        keyBlobId = keyBlobId,
        connectTimeoutMs = 5000,
        readTimeoutMs = 10000,
        strictHostKey = true,
        pinnedHostKeyFingerprint = pinnedFingerprint,
        keyProvisionedAt = 0L,
        keyProvisionStatus = "success",
        createdAt = 0L,
        updatedAt = 0L,
        name = "Uptime",
        command = command,
        requireConfirmation = false,
        defaultTimeoutMs = 30000,
        allowPty = false,
        mappingId = testMappingId,
        sortIndex = 0
    )

    @Test
    fun `startSession executes command and emits final state`() = runTest {
        val viewModel = createViewModel()

        viewModel.startSession(testMappingId)

        val state = viewModel.uiState.value
        assertEquals("Output should contain streamed text", "hello", state.output)
        assertEquals("Exit code should be reported", 0, state.exitCode)
        assertFalse("Execution should be finished", state.isRunning)
        assertTrue("Output dialog should be shown", state.showOutputDialog)
        assertTrue("Copy should be allowed", state.canCopy)
        assertNull("No error expected", state.error)
        assertEquals("Test Host", state.hostNickname)
        assertEquals("Uptime", state.commandName)
        coVerify { mockSshClient.authUsernameKey("testuser", "key-bytes".toByteArray()) }
        coVerify { mockSshClient.waitExitCode(30000) }
    }

    @Test
    fun `output is truncated to the line limit with an overflow marker`() = runTest {
        val lines = (1..130).joinToString("\n") { "line$it" } + "\n"
        every { mockSshClient.exec(any(), any()) } returns flowOf(lines.encodeUtf8())
        val viewModel = createViewModel()

        viewModel.startSession(testMappingId)

        val output = viewModel.uiState.value.output
        assertTrue("Overflow marker expected", output.startsWith("... (+2 more lines)"))
        assertTrue("Last line should be kept", output.endsWith("line130"))
        assertFalse("Truncated lines should be dropped", output.contains("line2\n"))
    }

    @Test
    fun `dangerous command requires the gate before executing`() = runTest {
        every { mockDangerousCommandValidator.isDangerous("uptime") } returns true
        val viewModel = createViewModel()

        viewModel.startSession(testMappingId)

        coVerify { mockGatekeeper.requireGateForDangerousCommand() }
        assertEquals("Command should still run once gated", 0, viewModel.uiState.value.exitCode)
    }

    @Test
    fun `closed gate aborts the session with an error`() = runTest {
        every { mockDangerousCommandValidator.isDangerous("uptime") } returns true
        coEvery { mockGatekeeper.requireGateForDangerousCommand() } throws
            SecurityGateRequiredException("Auth required")
        val viewModel = createViewModel()

        viewModel.startSession(testMappingId)

        val state = viewModel.uiState.value
        assertEquals("Session failed: Auth required", state.error)
        assertFalse(state.isRunning)
        coVerify(exactly = 0) { mockSshClient.connect(any(), any(), any(), any()) }
    }

    @Test
    fun `missing mapping reports a friendly error`() = runTest {
        coEvery { mockHostCommandRepository.getHostCommandMapping(testMappingId) } returns null
        val viewModel = createViewModel()

        viewModel.startSession(testMappingId)

        val state = viewModel.uiState.value
        assertEquals("Command mapping not found. The command may have been deleted.", state.error)
        assertEquals("Mapping id should stay active for inline error", testMappingId, state.activeMappingId)
        assertFalse(state.isRunning)
    }

    @Test
    fun `password auth is reported as unsupported`() = runTest {
        coEvery { mockHostCommandRepository.getHostCommandMapping(testMappingId) } returns
            createMapping(authMethod = "password")
        val viewModel = createViewModel()

        viewModel.startSession(testMappingId)

        assertEquals("Password authentication is not yet supported", viewModel.uiState.value.error)
        coVerify(exactly = 0) { mockSshClient.exec(any(), any()) }
    }

    @Test
    fun `key auth without key blob id reports an error`() = runTest {
        coEvery { mockHostCommandRepository.getHostCommandMapping(testMappingId) } returns
            createMapping(keyBlobId = null)
        val viewModel = createViewModel()

        viewModel.startSession(testMappingId)

        assertEquals(
            "Key authentication required but no key blob ID found",
            viewModel.uiState.value.error
        )
    }

    @Test
    fun `decrypt failure maps to a user-friendly message`() = runTest {
        coEvery { mockKeyVault.decryptPrivateKey(any()) } throws
            RuntimeException("User not authenticated")
        val viewModel = createViewModel()

        viewModel.startSession(testMappingId)

        assertEquals(
            "Device authentication required. Please unlock your device.",
            viewModel.uiState.value.error
        )
        coVerify(exactly = 0) { mockSshClient.exec(any(), any()) }
    }

    @Test
    fun `sensitive output is redacted before display`() = runTest {
        every { mockSshClient.exec(any(), any()) } returns
            flowOf("password=hunter2\n".encodeUtf8())
        val viewModel = createViewModel()

        viewModel.startSession(testMappingId)

        assertEquals("[REDACTED]", viewModel.uiState.value.output)
    }

    @Test
    fun `copy is disallowed when the setting is off`() = runTest {
        every { mockSettingsStore.allowCopyOutput } returns flowOf(false)
        val viewModel = createViewModel()

        viewModel.startSession(testMappingId)

        val state = viewModel.uiState.value
        assertEquals("hello", state.output)
        assertFalse("Copy must be gated by the setting", state.canCopy)
    }

    @Test
    fun `copyOutput puts redacted output on the clipboard when allowed`() = runTest {
        val clipboardManager = mockk<ClipboardManager>(relaxed = true)
        every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager
        mockkStatic(ClipData::class)
        every { ClipData.newPlainText(any(), any()) } returns mockk(relaxed = true)
        val viewModel = createViewModel()
        viewModel.startSession(testMappingId)

        viewModel.copyOutput()

        verify { clipboardManager.setPrimaryClip(any()) }
        unmockkStatic(ClipData::class)
    }

    @Test
    fun `copyOutput does nothing when the setting is off`() = runTest {
        every { mockSettingsStore.allowCopyOutput } returns flowOf(false)
        val viewModel = createViewModel()
        viewModel.startSession(testMappingId)

        viewModel.copyOutput()

        verify(exactly = 0) { mockContext.getSystemService(any()) }
    }

    @Test
    fun `exec failure surfaces an execution error`() = runTest {
        every { mockSshClient.exec(any(), any()) } returns flow { throw RuntimeException("boom") }
        val viewModel = createViewModel()

        viewModel.startSession(testMappingId)

        val state = viewModel.uiState.value
        assertEquals("Execution failed: boom", state.error)
        assertFalse(state.isRunning)
        assertTrue("Dialog should show the failure", state.showOutputDialog)
    }

    @Test
    fun `first connection surfaces a TOFU prompt instead of executing`() = runTest {
        coEvery { mockHostCommandRepository.getHostCommandMapping(testMappingId) } returns
            createMapping(pinnedFingerprint = null)
        coEvery { mockSshClient.connect(any(), any(), any(), null) } throws
            TofuRequiredException(HostPin("ssh-ed25519", "SHA256:xyz"), "First connection")
        val viewModel = createViewModel()

        viewModel.startSession(testMappingId)

        val state = viewModel.uiState.value
        assertNotNull("TOFU prompt expected", state.tofuPrompt)
        assertEquals("test.example.com", state.tofuPrompt?.hostname)
        assertEquals(22, state.tofuPrompt?.port)
        assertEquals("SHA256:xyz", state.tofuPrompt?.pin?.sha256)
        assertNull("A TOFU prompt is not an error", state.error)
        assertFalse(state.isRunning)
        coVerify(exactly = 0) { mockSshClient.exec(any(), any()) }
        coVerify(exactly = 0) { mockHostsRepository.updateHostFingerprint(any(), any()) }
    }

    @Test
    fun `confirmTofuTrust pins the fingerprint and runs the session`() = runTest {
        var pinned: String? = null
        coEvery { mockHostCommandRepository.getHostCommandMapping(testMappingId) } coAnswers {
            createMapping(pinnedFingerprint = pinned)
        }
        coEvery { mockHostsRepository.updateHostFingerprint("host-1", "SHA256:xyz") } coAnswers {
            pinned = "SHA256:xyz"
        }
        coEvery { mockSshClient.connect(any(), any(), any(), any()) } coAnswers {
            val expected = arg<HostPin?>(3)
            if (expected == null) {
                throw TofuRequiredException(HostPin("ssh-ed25519", "SHA256:xyz"), "First connection")
            }
            HostPin("ssh-ed25519", "SHA256:xyz")
        }
        val viewModel = createViewModel()
        viewModel.startSession(testMappingId)
        assertNotNull(viewModel.uiState.value.tofuPrompt)

        viewModel.confirmTofuTrust()

        val state = viewModel.uiState.value
        assertNull("Prompt should be dismissed", state.tofuPrompt)
        assertEquals("Command should have run after trust", 0, state.exitCode)
        assertEquals("hello", state.output)
        coVerify { mockHostsRepository.updateHostFingerprint("host-1", "SHA256:xyz") }
        coVerify { mockSshClient.connect(any(), any(), any(), HostPin("ssh-rsa", "SHA256:xyz")) }
    }

    @Test
    fun `dismissTofuPrompt clears the prompt without pinning`() = runTest {
        coEvery { mockHostCommandRepository.getHostCommandMapping(testMappingId) } returns
            createMapping(pinnedFingerprint = null)
        coEvery { mockSshClient.connect(any(), any(), any(), null) } throws
            TofuRequiredException(HostPin("ssh-ed25519", "SHA256:xyz"), "First connection")
        val viewModel = createViewModel()
        viewModel.startSession(testMappingId)

        viewModel.dismissTofuPrompt()

        val state = viewModel.uiState.value
        assertNull(state.tofuPrompt)
        assertNull("Active mapping should be cleared", state.activeMappingId)
        coVerify(exactly = 0) { mockHostsRepository.updateHostFingerprint(any(), any()) }
    }

    @Test
    fun `cancelExecution stops the session and clears the active mapping`() = runTest {
        val viewModel = createViewModel()
        viewModel.startSession(testMappingId)

        viewModel.cancelExecution()

        val state = viewModel.uiState.value
        assertFalse(state.isRunning)
        assertNull("Active mapping should be cleared", state.activeMappingId)
        coVerify { mockSshClient.cancel() }
    }
}
