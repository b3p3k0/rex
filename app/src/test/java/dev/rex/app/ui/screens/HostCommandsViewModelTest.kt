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

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import dev.rex.app.data.db.HostCommandMapping
import dev.rex.app.data.db.HostEntity
import dev.rex.app.data.repo.CommandsRepository
import dev.rex.app.data.repo.HostCommandRepository
import dev.rex.app.data.repo.HostsRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HostCommandsViewModelTest {

    private val mockHostCommandRepository = mockk<HostCommandRepository>()
    private val mockHostsRepository = mockk<HostsRepository>()
    private val mockCommandsRepository = mockk<CommandsRepository>()
    private val mockSavedStateHandle = mockk<SavedStateHandle>()

    private lateinit var viewModel: HostCommandsViewModel

    private val testHostId = "host-1"
    private val otherHostId = "host-2"

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
        pinnedHostKeyFingerprint = null,
        keyProvisionedAt = null,
        keyProvisionStatus = "success",
        createdAt = 1L,
        updatedAt = 1L
    )

    private val otherHost = testHost.copy(id = otherHostId, nickname = "Other Host")

    private fun mapping(hostId: String, commandId: String, commandName: String) = HostCommandMapping(
        id = hostId,
        nickname = if (hostId == testHostId) "Test Host" else "Other Host",
        hostname = "test.example.com",
        port = 22,
        username = "testuser",
        authMethod = "key",
        keyBlobId = "test-key-id",
        connectTimeoutMs = 5000,
        readTimeoutMs = 10000,
        strictHostKey = true,
        pinnedHostKeyFingerprint = null,
        keyProvisionedAt = null,
        keyProvisionStatus = "success",
        createdAt = 1L,
        updatedAt = 1L,
        name = commandName,
        command = "echo test",
        requireConfirmation = true,
        defaultTimeoutMs = 15000,
        allowPty = false,
        mappingId = "${hostId}_$commandId",
        sortIndex = 0
    )

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        every { mockSavedStateHandle.get<String>("hostId") } returns testHostId
        every { mockHostsRepository.getAllHosts() } returns flowOf(listOf(testHost, otherHost))
        every { mockHostCommandRepository.getAllHostCommandMappings() } returns flowOf(
            listOf(
                mapping(testHostId, "cmd-1", "uptime"),
                mapping(otherHostId, "cmd-2", "df"),
                mapping(testHostId, "cmd-3", "reboot")
            )
        )

        viewModel = HostCommandsViewModel(
            mockHostCommandRepository,
            mockHostsRepository,
            mockCommandsRepository,
            mockSavedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun `commands are filtered to this host`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.commands.collect {} }

        val commands = viewModel.commands.value
        assertEquals(2, commands.size)
        assertTrue(commands.all { it.id == testHostId })
        assertEquals(listOf("uptime", "reboot"), commands.map { it.name })

        job.cancel()
    }

    @Test
    fun `host resolves from repository by id`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.host.collect {} }

        assertEquals(testHost, viewModel.host.value)

        job.cancel()
    }

    @Test
    fun `onDeleteCommand delegates to commands repository`() = runTest {
        coEvery { mockCommandsRepository.deleteCommandById("cmd-1") } returns 1

        viewModel.onDeleteCommand("cmd-1")

        coVerify { mockCommandsRepository.deleteCommandById("cmd-1") }
    }
}
