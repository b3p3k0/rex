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
import dev.rex.app.data.db.HostCommandEntity
import dev.rex.app.data.db.HostCommandsDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandsRepository @Inject constructor(
    private val commandsDao: CommandsDao,
    private val hostCommandsDao: HostCommandsDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun getAllCommands(): Flow<List<CommandEntity>> = commandsDao.getAllCommands()

    suspend fun getCommandById(id: String): CommandEntity? = commandsDao.getCommandById(id)

    suspend fun insertCommand(command: CommandEntity) = commandsDao.insertCommand(command)

    suspend fun updateCommand(command: CommandEntity) = withContext(ioDispatcher) {
        try {
            commandsDao.updateCommand(command)
            Log.d("Rex", "updateCommand cmdId=${command.id} name=${command.name}")
        } catch (e: Exception) {
            Log.e("Rex", "Failed to update command ${command.id}: ${e.message}")
            throw e
        }
    }

    suspend fun deleteCommand(command: CommandEntity) = withContext(ioDispatcher) {
        try {
            commandsDao.deleteCommand(command)
            Log.d("Rex", "deleteCommand cmdId=${command.id} name=${command.name}")
        } catch (e: Exception) {
            Log.e("Rex", "Failed to delete command ${command.id}: ${e.message}")
            throw e
        }
    }

    suspend fun deleteCommandById(commandId: String): Int = withContext(ioDispatcher) {
        try {
            val deletedRows = commandsDao.deleteCommandById(commandId)
            Log.d("Rex", "deleteCommandById cmdId=$commandId deletedRows=$deletedRows")
            deletedRows
        } catch (e: Exception) {
            Log.e("Rex", "Failed to delete command by ID $commandId: ${e.message}")
            throw e
        }
    }

    suspend fun addCommandForHost(hostId: String, command: CommandEntity): String = withContext(ioDispatcher) {
        try {
            commandsDao.insertCommand(command)
            val mapping = HostCommandEntity(
                hostId = hostId,
                commandId = command.id,
                sortIndex = 0,
                createdAt = System.currentTimeMillis()
            )
            val mappingId = hostCommandsDao.insertHostCommand(mapping)
            Log.d("Rex", "addCommandForHost host=$hostId cmdId=${command.id} mappingId=$mappingId")
            command.id
        } catch (e: Exception) {
            Log.e("Rex", "Failed to add command for host $hostId: ${e.message}")
            throw e
        }
    }
}