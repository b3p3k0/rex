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
import androidx.room.Transaction
import dev.rex.app.data.db.HostEntity
import dev.rex.app.data.db.HostCommandsDao
import dev.rex.app.data.db.HostsDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostsRepository @Inject constructor(
    private val hostsDao: HostsDao,
    private val hostCommandsDao: HostCommandsDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun getAllHosts(): Flow<List<HostEntity>> = hostsDao.getAllHosts()
    
    suspend fun getHostById(id: String): HostEntity? = hostsDao.getHostById(id)
    
    suspend fun insertHost(host: HostEntity) = withContext(ioDispatcher) { 
        hostsDao.insertHost(host)
        host.id // Return the ID for logging
    }
    
    suspend fun updateHost(host: HostEntity) = withContext(ioDispatcher) {
        hostsDao.updateHost(host)
    }

    suspend fun updateHostFingerprint(hostId: String, fingerprint: String) = withContext(ioDispatcher) {
        val host = hostsDao.getHostById(hostId)
        if (host != null) {
            val updatedHost = host.copy(
                pinnedHostKeyFingerprint = fingerprint,
                updatedAt = System.currentTimeMillis()
            )
            hostsDao.updateHost(updatedHost)
        }
    }

    suspend fun updateKeyProvisionStatus(
        hostId: String,
        keyBlobId: String?,
        provisionStatus: String,
        provisionedAt: Long? = null
    ) = withContext(ioDispatcher) {
        val host = hostsDao.getHostById(hostId)
        if (host != null) {
            val updatedHost = host.copy(
                keyBlobId = keyBlobId,
                keyProvisionStatus = provisionStatus,
                keyProvisionedAt = provisionedAt ?: if (provisionStatus == "success") System.currentTimeMillis() else host.keyProvisionedAt,
                updatedAt = System.currentTimeMillis()
            )
            hostsDao.updateHost(updatedHost)
        }
    }

    suspend fun assignKeyToHost(hostId: String, keyBlobId: String) = withContext(ioDispatcher) {
        val host = hostsDao.getHostById(hostId)
        if (host != null) {
            val updatedHost = host.copy(
                keyBlobId = keyBlobId,
                keyProvisionStatus = "pending",
                updatedAt = System.currentTimeMillis()
            )
            hostsDao.updateHost(updatedHost)
        }
    }

    suspend fun updateHostUsername(hostId: String, username: String): Boolean = withContext(ioDispatcher) {
        val host = hostsDao.getHostById(hostId) ?: return@withContext false

        val sanitized = username.filterNot { it == '\n' || it == '\r' }.trim()

        // Log warning if sanitization removed characters for audit trail
        if (sanitized != username) {
            Log.w("Rex", "Sanitized username for host $hostId: removed whitespace/newlines")
        }

        val updatedHost = host.copy(
            username = sanitized,
            updatedAt = System.currentTimeMillis()
        )
        hostsDao.updateHost(updatedHost)
        true
    }
    
    suspend fun deleteHost(host: HostEntity) = withContext(ioDispatcher) { 
        hostsDao.deleteHost(host) 
    }
    
    @Transaction
    suspend fun deleteHostCascade(hostId: String): Boolean = withContext(ioDispatcher) {
        try {
            // FK CASCADE should handle mappings automatically
            val rowsAffected = hostsDao.deleteById(hostId)
            if (rowsAffected > 0) {
                Log.i("Rex", "Deleted host $hostId via FK CASCADE")
                true
            } else {
                Log.w("Rex", "Host $hostId already removed or not found")
                false
            }
        } catch (e: Exception) {
            Log.e("Rex", "Failed to delete host $hostId", e)
            // Fallback manual cleanup if FK CASCADE fails
            try {
                hostCommandsDao.deleteMappingsForHost(hostId)
                val rowsAffected = hostsDao.deleteById(hostId)
                Log.i("Rex", "Deleted host $hostId with manual mapping cleanup")
                rowsAffected > 0
            } catch (fallbackException: Exception) {
                Log.e("Rex", "Manual cleanup also failed for host $hostId", fallbackException)
                throw fallbackException
            }
        }
    }
}