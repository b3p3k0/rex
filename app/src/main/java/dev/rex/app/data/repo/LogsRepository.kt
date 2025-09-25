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
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogsRepository @Inject constructor(
    private val logsDao: LogsDao
) {
    fun getRecentLogs(limit: Int = 100): Flow<List<LogEntity>> = logsDao.getRecentLogs(limit)
    
    fun getLogsByHost(hostNickname: String): Flow<List<LogEntity>> = 
        logsDao.getLogsByHost(hostNickname)
    
    suspend fun insertLog(log: LogEntity) = logsDao.insertLog(log)
    
    suspend fun deleteOldLogsByCount(keepCount: Int) = logsDao.deleteOldLogsByCount(keepCount)

    suspend fun deleteOldLogsByAge(maxAgeDays: Int) {
        val cutoffTimestamp = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
        logsDao.deleteOldLogsByAge(cutoffTimestamp)
    }

    suspend fun deleteOldLogsBySize(maxBytes: Long) {
        val logSizeInfo = logsDao.getLogsSizeInfo()
        var currentSize = 0L
        val idsToKeep = mutableListOf<String>()

        // Keep logs from newest to oldest until we exceed the size limit
        for (logInfo in logSizeInfo) {
            if (currentSize + logInfo.total_bytes <= maxBytes) {
                currentSize += logInfo.total_bytes
                idsToKeep.add(logInfo.id)
            } else {
                break
            }
        }

        // If we have logs to delete, delete them
        val allIds = logSizeInfo.map { it.id }
        val idsToDelete = allIds - idsToKeep.toSet()

        if (idsToDelete.isNotEmpty()) {
            logsDao.deleteLogsByIds(idsToDelete)
        }
    }
}