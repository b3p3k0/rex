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

package dev.rex.app.data.repo

import android.util.Log
import dev.rex.app.data.db.CommandEntity
import dev.rex.app.data.db.CommandsDao
import dev.rex.app.data.db.HostCommandsDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

class CommandsRepositoryTest {

    private lateinit var commandsDao: CommandsDao
    private lateinit var repository: CommandsRepository
    private lateinit var hostCommandsDao: HostCommandsDao

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        commandsDao = mockk()
        hostCommandsDao = mockk()
        repository = CommandsRepository(commandsDao, hostCommandsDao)
    }

    @Test
    fun `getAllCommands returns dao result`() = runTest {
        val expected = flowOf(emptyList<CommandEntity>())
        coEvery { commandsDao.getAllCommands() } returns expected

        val result = repository.getAllCommands()

        assert(result == expected)
        coVerify { commandsDao.getAllCommands() }
    }

    @Test
    fun `insertCommand calls dao insert`() = runTest {
        val command = CommandEntity(
            id = UUID.randomUUID().toString(),
            name = "test-command",
            command = "echo 'test'",
            requireConfirmation = true,
            defaultTimeoutMs = 15000,
            allowPty = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        coEvery { commandsDao.insertCommand(command) } returns Unit

        repository.insertCommand(command)

        coVerify { commandsDao.insertCommand(command) }
    }

    @Test
    fun `addCommandForHost persists runWithSudo flag`() = runTest {
        val command = CommandEntity(
            id = UUID.randomUUID().toString(),
            name = "restart nginx",
            command = "systemctl restart nginx",
            requireConfirmation = true,
            defaultTimeoutMs = 15000,
            allowPty = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            runWithSudo = true
        )
        coEvery { commandsDao.insertCommand(any()) } returns Unit
        coEvery { hostCommandsDao.insertHostCommand(any()) } returns 1L

        repository.addCommandForHost("host-1", command)

        coVerify { commandsDao.insertCommand(match { it.runWithSudo }) }
        coVerify { hostCommandsDao.insertHostCommand(match { it.hostId == "host-1" && it.commandId == command.id }) }
    }
}