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

package dev.rex.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HostsDao {
    @Query("SELECT * FROM hosts ORDER BY updated_at DESC")
    fun getAllHosts(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun getHostById(id: String): HostEntity?

    @Insert
    suspend fun insertHost(host: HostEntity)

    @Update
    suspend fun updateHost(host: HostEntity)

    @Delete
    suspend fun deleteHost(host: HostEntity)
}

@Dao
interface CommandsDao {
    @Query("SELECT * FROM commands ORDER BY updated_at DESC")
    fun getAllCommands(): Flow<List<CommandEntity>>

    @Query("SELECT * FROM commands WHERE id = :id")
    suspend fun getCommandById(id: String): CommandEntity?

    @Insert
    suspend fun insertCommand(command: CommandEntity)

    @Update
    suspend fun updateCommand(command: CommandEntity)

    @Delete
    suspend fun deleteCommand(command: CommandEntity)
}

@Dao
interface HostCommandsDao {
    @Query("SELECT * FROM host_commands ORDER BY sort_index ASC")
    fun getAllHostCommands(): Flow<List<HostCommandEntity>>

    @Query("SELECT * FROM host_commands WHERE host_id = :hostId ORDER BY sort_index ASC")
    fun getHostCommandsByHostId(hostId: String): Flow<List<HostCommandEntity>>

    @Insert
    suspend fun insertHostCommand(hostCommand: HostCommandEntity)

    @Delete
    suspend fun deleteHostCommand(hostCommand: HostCommandEntity)
}

@Dao
interface KeyBlobsDao {
    @Query("SELECT * FROM key_blobs WHERE id = :id")
    suspend fun getKeyBlobById(id: String): KeyBlobEntity?

    @Insert
    suspend fun insertKeyBlob(keyBlob: KeyBlobEntity)

    @Delete
    suspend fun deleteKeyBlob(keyBlob: KeyBlobEntity)
}

@Dao
interface LogsDao {
    @Query("SELECT * FROM logs ORDER BY ts DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs WHERE host_nickname = :hostNickname ORDER BY ts DESC")
    fun getLogsByHost(hostNickname: String): Flow<List<LogEntity>>

    @Insert
    suspend fun insertLog(log: LogEntity)

    @Query("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY ts DESC LIMIT :keepCount)")
    suspend fun deleteOldLogsByCount(keepCount: Int)
}