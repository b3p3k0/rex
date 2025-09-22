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

import dev.rex.app.data.db.HostEntity
import dev.rex.app.data.db.HostsDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

class HostsRepositoryTest {

    private lateinit var hostsDao: HostsDao
    private lateinit var repository: HostsRepository

    @Before
    fun setup() {
        hostsDao = mockk()
        repository = HostsRepository(hostsDao)
    }

    @Test
    fun `getAllHosts returns dao result`() = runTest {
        val expected = flowOf(emptyList<HostEntity>())
        coEvery { hostsDao.getAllHosts() } returns expected

        val result = repository.getAllHosts()

        assert(result == expected)
        coVerify { hostsDao.getAllHosts() }
    }

    @Test
    fun `insertHost calls dao insert`() = runTest {
        val host = HostEntity(
            id = UUID.randomUUID().toString(),
            nickname = "test",
            hostname = "test.com",
            port = 22,
            username = "user",
            authMethod = "key",
            keyBlobId = null,
            connectTimeoutMs = 8000,
            readTimeoutMs = 15000,
            strictHostKey = true,
            pinnedHostKeyFingerprint = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        coEvery { hostsDao.insertHost(host) } returns Unit

        repository.insertHost(host)

        coVerify { hostsDao.insertHost(host) }
    }
}