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

import dev.rex.app.data.db.HostCommandEntity
import dev.rex.app.data.db.HostCommandMapping
import dev.rex.app.data.db.HostCommandRow
import dev.rex.app.data.db.HostCommandsDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostCommandRepository @Inject constructor(
    private val hostCommandsDao: HostCommandsDao
) {
    
    fun getAllHostCommandMappings(): Flow<List<HostCommandMapping>> {
        return hostCommandsDao.getHostCommandMappings()
    }

    suspend fun getHostCommandMapping(mappingId: String): HostCommandMapping? {
        return hostCommandsDao.getHostCommandMapping(mappingId)
    }
    
    fun observeHostCommandRows(): Flow<List<HostCommandRow>> {
        return hostCommandsDao.observeHostCommandRows()
    }
    
    fun getHostCommandsByHostId(hostId: String): Flow<List<HostCommandEntity>> {
        return hostCommandsDao.getHostCommandsByHostId(hostId)
    }
    
    suspend fun insertHostCommand(hostCommand: HostCommandEntity) {
        hostCommandsDao.insertHostCommand(hostCommand)
    }
    
    suspend fun deleteHostCommand(hostCommand: HostCommandEntity) {
        hostCommandsDao.deleteHostCommand(hostCommand)
    }
    
    suspend fun createMapping(hostId: String, commandId: String, sortIndex: Int = 0) {
        val mapping = HostCommandEntity(
            hostId = hostId,
            commandId = commandId,
            sortIndex = sortIndex,
            createdAt = System.currentTimeMillis()
        )
        insertHostCommand(mapping)
    }
}