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

import dev.rex.app.data.db.LogEntity
import dev.rex.app.data.db.LogSizeInfo
import dev.rex.app.data.db.LogsDao
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class LogsRepositoryTest {

    private val mockLogsDao = mockk<LogsDao>()
    private val repository = LogsRepository(mockLogsDao)

    @Before
    fun setUp() {
        clearAllMocks()
    }

    @Test
    fun `getRecentLogs returns flow from dao`() = runTest {
        val expectedLogs = listOf(
            createTestLog("1", "host1", "cmd1", 1000),
            createTestLog("2", "host2", "cmd2", 2000)
        )
        every { mockLogsDao.getRecentLogs(100) } returns flowOf(expectedLogs)

        val result = repository.getRecentLogs(100)

        result.collect { logs ->
            assertEquals(expectedLogs, logs)
        }
        verify { mockLogsDao.getRecentLogs(100) }
    }

    @Test
    fun `getLogsByHost returns filtered flow from dao`() = runTest {
        val expectedLogs = listOf(
            createTestLog("1", "testhost", "cmd1", 1000)
        )
        every { mockLogsDao.getLogsByHost("testhost") } returns flowOf(expectedLogs)

        val result = repository.getLogsByHost("testhost")

        result.collect { logs ->
            assertEquals(expectedLogs, logs)
            assertEquals("testhost", logs.first().hostNickname)
        }
        verify { mockLogsDao.getLogsByHost("testhost") }
    }

    @Test
    fun `insertLog calls dao insert`() = runTest {
        val log = createTestLog("1", "host1", "cmd1", 1000)
        coEvery { mockLogsDao.insertLog(any()) } returns Unit

        repository.insertLog(log)

        coVerify { mockLogsDao.insertLog(log) }
    }

    @Test
    fun `deleteOldLogsByCount calls dao with correct keepCount`() = runTest {
        coEvery { mockLogsDao.deleteOldLogsByCount(any()) } returns Unit

        repository.deleteOldLogsByCount(50)

        coVerify { mockLogsDao.deleteOldLogsByCount(50) }
    }

    @Test
    fun `deleteOldLogsByAge calculates correct cutoff timestamp`() = runTest {
        // Capture the cutoff instead of mocking System.currentTimeMillis();
        // mockkStatic(System::class) deadlocks on JDK 18+ (SecurityManager recursion)
        val cutoffSlot = slot<Long>()
        coEvery { mockLogsDao.deleteOldLogsByAge(capture(cutoffSlot)) } returns Unit

        val maxAgeDays = 7
        val ageMillis = maxAgeDays * 24 * 60 * 60 * 1000L

        val before = System.currentTimeMillis()
        repository.deleteOldLogsByAge(maxAgeDays)
        val after = System.currentTimeMillis()

        coVerify { mockLogsDao.deleteOldLogsByAge(any()) }
        assertTrue(cutoffSlot.captured in (before - ageMillis)..(after - ageMillis))
    }

    @Test
    fun `deleteOldLogsByAge handles different age values`() = runTest {
        val cutoffs = mutableListOf<Long>()
        coEvery { mockLogsDao.deleteOldLogsByAge(capture(cutoffs)) } returns Unit

        val dayMillis = 24 * 60 * 60 * 1000L

        val before = System.currentTimeMillis()
        repository.deleteOldLogsByAge(1)
        repository.deleteOldLogsByAge(30)
        val after = System.currentTimeMillis()

        assertEquals(2, cutoffs.size)
        assertTrue(cutoffs[0] in (before - 1 * dayMillis)..(after - 1 * dayMillis))
        assertTrue(cutoffs[1] in (before - 30 * dayMillis)..(after - 30 * dayMillis))
    }

    @Test
    fun `deleteOldLogsBySize deletes correct logs based on size limit`() = runTest {
        val logSizeInfos = listOf(
            LogSizeInfo("1", 100), // newest, 100 bytes
            LogSizeInfo("2", 200), // 200 bytes
            LogSizeInfo("3", 300), // 300 bytes - should be deleted
            LogSizeInfo("4", 400)  // oldest, 400 bytes - should be deleted
        )

        coEvery { mockLogsDao.getLogsSizeInfo() } returns logSizeInfos
        coEvery { mockLogsDao.deleteLogsByIds(any()) } returns Unit

        val maxBytes = 300L // Only first two logs should fit (100 + 200 = 300)
        repository.deleteOldLogsBySize(maxBytes)

        coVerify { mockLogsDao.deleteLogsByIds(listOf("3", "4")) }
    }

    @Test
    fun `deleteOldLogsBySize handles zero bytes limit`() = runTest {
        val logSizeInfos = listOf(
            LogSizeInfo("1", 100),
            LogSizeInfo("2", 200)
        )

        coEvery { mockLogsDao.getLogsSizeInfo() } returns logSizeInfos
        coEvery { mockLogsDao.deleteLogsByIds(any()) } returns Unit

        repository.deleteOldLogsBySize(0L)

        coVerify { mockLogsDao.deleteLogsByIds(listOf("1", "2")) } // All should be deleted
    }

    @Test
    fun `deleteOldLogsBySize handles no logs to delete`() = runTest {
        val logSizeInfos = listOf(
            LogSizeInfo("1", 100),
            LogSizeInfo("2", 200)
        )

        coEvery { mockLogsDao.getLogsSizeInfo() } returns logSizeInfos
        coEvery { mockLogsDao.deleteLogsByIds(any()) } returns Unit

        val maxBytes = 1000L // All logs fit
        repository.deleteOldLogsBySize(maxBytes)

        coVerify(exactly = 0) { mockLogsDao.deleteLogsByIds(any()) }
    }

    private fun createTestLog(
        id: String,
        hostNickname: String,
        commandName: String,
        timestamp: Long,
        exitCode: Int? = 0,
        durationMs: Int? = 1000,
        bytesStdout: Int = 100,
        bytesStderr: Int = 50,
        status: String = "completed",
        messageRedacted: String? = "Test log message",
        idxSeq: Long = timestamp
    ): LogEntity {
        return LogEntity(
            id = id,
            ts = timestamp,
            hostNickname = hostNickname,
            commandName = commandName,
            exitCode = exitCode,
            durationMs = durationMs,
            bytesStdout = bytesStdout,
            bytesStderr = bytesStderr,
            status = status,
            messageRedacted = messageRedacted,
            idxSeq = idxSeq
        )
    }
}