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
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Dao
interface HostsDao {
    @Query("SELECT * FROM hosts ORDER BY updated_at DESC")
    fun getAllHosts(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun getHostById(id: String): HostEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHost(host: HostEntity)

    @Update
    suspend fun updateHost(host: HostEntity)

    @Delete
    suspend fun deleteHost(host: HostEntity)
    
    @Query("DELETE FROM hosts WHERE id = :hostId")
    suspend fun deleteById(hostId: String): Int
}

@Dao
interface CommandsDao {
    @Query("SELECT * FROM commands ORDER BY updated_at DESC")
    fun getAllCommands(): Flow<List<CommandEntity>>

    @Query("SELECT * FROM commands WHERE id = :id")
    suspend fun getCommandById(id: String): CommandEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommand(command: CommandEntity)

    @Update
    suspend fun updateCommand(command: CommandEntity)

    @Delete
    suspend fun deleteCommand(command: CommandEntity)

    @Query("DELETE FROM commands WHERE id = :commandId")
    suspend fun deleteCommandById(commandId: String): Int
}

@Dao
interface HostCommandsDao {
    @Query("SELECT * FROM host_commands ORDER BY sort_index ASC")
    fun getAllHostCommands(): Flow<List<HostCommandEntity>>

    @Query("SELECT * FROM host_commands WHERE host_id = :hostId ORDER BY sort_index ASC")
    fun getHostCommandsByHostId(hostId: String): Flow<List<HostCommandEntity>>

    @Query("""
        SELECT h.id, h.nickname, h.hostname, h.port, h.username,
               h.auth_method, h.key_blob_id, h.connect_timeout_ms, h.read_timeout_ms,
               h.strict_host_key, h.pinned_host_key_fingerprint, h.key_provisioned_at, h.key_provision_status,
               h.created_at, h.updated_at,
               c.name, c.command, c.require_confirmation, c.default_timeout_ms, c.allow_pty,
               hc.host_id || '_' || hc.command_id as mapping_id, hc.sort_index
        FROM host_commands hc
        INNER JOIN hosts h ON hc.host_id = h.id
        INNER JOIN commands c ON hc.command_id = c.id
        ORDER BY hc.sort_index ASC
    """)
    fun getHostCommandMappings(): Flow<List<HostCommandMapping>>

    @Query("""
        SELECT h.id            AS hostId,
               h.nickname      AS hostNickname,
               h.hostname      AS hostName,
               h.port          AS hostPort,
               h.username      AS hostUser,
               h.auth_method   AS hostAuthMethod,
               h.key_blob_id   AS hostKeyBlobId,
               h.connect_timeout_ms AS hostConnectTimeoutMs,
               h.read_timeout_ms AS hostReadTimeoutMs,
               h.strict_host_key AS hostStrictHostKey,
               h.pinned_host_key_fingerprint AS hostPinnedHostKeyFingerprint,
               h.key_provisioned_at AS hostKeyProvisionedAt,
               h.key_provision_status AS hostKeyProvisionStatus,
               h.created_at    AS hostCreatedAt,
               h.updated_at    AS hostUpdatedAt,
               c.id            AS cmdId,
               c.name          AS cmdName,
               c.command       AS cmdCommand,
               c.require_confirmation AS cmdRequireConfirmation,
               c.default_timeout_ms AS cmdDefaultTimeoutMs,
               c.allow_pty     AS cmdAllowPty,
               hc.host_id || '_' || hc.command_id AS mappingId,
               hc.sort_index   AS sortIndex
        FROM hosts h
        LEFT JOIN host_commands hc ON hc.host_id = h.id
        LEFT JOIN commands c       ON c.id = hc.command_id
        ORDER BY h.nickname COLLATE NOCASE, c.name COLLATE NOCASE
    """)
    fun observeHostCommandRows(): Flow<List<HostCommandRow>>

    @Query("""
        SELECT h.id, h.nickname, h.hostname, h.port, h.username,
               h.auth_method, h.key_blob_id, h.connect_timeout_ms, h.read_timeout_ms,
               h.strict_host_key, h.pinned_host_key_fingerprint, h.key_provisioned_at, h.key_provision_status,
               h.created_at, h.updated_at,
               c.name, c.command, c.require_confirmation, c.default_timeout_ms, c.allow_pty,
               hc.host_id || '_' || hc.command_id as mapping_id, hc.sort_index
        FROM host_commands hc
        INNER JOIN hosts h ON hc.host_id = h.id
        INNER JOIN commands c ON hc.command_id = c.id
        WHERE hc.host_id || '_' || hc.command_id = :mappingId
        LIMIT 1
    """)
    suspend fun getHostCommandMapping(mappingId: String): HostCommandMapping?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHostCommand(hostCommand: HostCommandEntity): Long

    @Delete
    suspend fun deleteHostCommand(hostCommand: HostCommandEntity)
    
    @Query("DELETE FROM host_commands WHERE host_id = :hostId")
    suspend fun deleteMappingsForHost(hostId: String)
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

    @Query("DELETE FROM logs WHERE ts < :cutoffTimestamp")
    suspend fun deleteOldLogsByAge(cutoffTimestamp: Long)

    @Query("SELECT id, (bytes_stdout + bytes_stderr) as total_bytes FROM logs ORDER BY ts DESC")
    suspend fun getLogsSizeInfo(): List<LogSizeInfo>

    @Query("DELETE FROM logs WHERE id IN (:idsToDelete)")
    suspend fun deleteLogsByIds(idsToDelete: List<String>)
}

@Dao
interface KeyProvisionLogsDao {
    @Query("SELECT * FROM key_provision_logs WHERE host_id = :hostId ORDER BY ts DESC")
    fun getProvisionLogsByHost(hostId: String): Flow<List<KeyProvisionLogEntity>>

    @Query("SELECT * FROM key_provision_logs WHERE key_blob_id = :keyBlobId ORDER BY ts DESC")
    fun getProvisionLogsByKey(keyBlobId: String): Flow<List<KeyProvisionLogEntity>>

    @Query("SELECT * FROM key_provision_logs ORDER BY ts DESC LIMIT :limit")
    fun getRecentProvisionLogs(limit: Int): Flow<List<KeyProvisionLogEntity>>

    @Insert
    suspend fun insertProvisionLog(log: KeyProvisionLogEntity)

    @Query("DELETE FROM key_provision_logs WHERE id NOT IN (SELECT id FROM key_provision_logs ORDER BY ts DESC LIMIT :keepCount)")
    suspend fun deleteOldProvisionLogsByCount(keepCount: Int)
}

