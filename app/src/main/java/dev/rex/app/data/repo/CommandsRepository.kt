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

import dev.rex.app.data.db.CommandEntity
import dev.rex.app.data.db.CommandsDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandsRepository @Inject constructor(
    private val commandsDao: CommandsDao
) {
    fun getAllCommands(): Flow<List<CommandEntity>> = commandsDao.getAllCommands()
    
    suspend fun getCommandById(id: String): CommandEntity? = commandsDao.getCommandById(id)
    
    suspend fun insertCommand(command: CommandEntity) = commandsDao.insertCommand(command)
    
    suspend fun updateCommand(command: CommandEntity) = commandsDao.updateCommand(command)
    
    suspend fun deleteCommand(command: CommandEntity) = commandsDao.deleteCommand(command)
}