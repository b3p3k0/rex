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

package dev.rex.app.ui.screens

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rex.app.core.GlobalCEH
import dev.rex.app.data.db.CommandEntity
import dev.rex.app.data.db.HostCommandMapping
import dev.rex.app.data.db.HostCommandRow
import dev.rex.app.data.repo.CommandsRepository
import dev.rex.app.data.repo.HostCommandRepository
import dev.rex.app.data.repo.HostsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainTableViewModel @Inject constructor(
    private val hostCommandRepository: HostCommandRepository,
    private val hostsRepository: HostsRepository,
    private val commandsRepository: CommandsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    val hostCommands: StateFlow<List<HostCommandMapping>> = 
        hostCommandRepository.getAllHostCommandMappings()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    
    val hostCommandRows: StateFlow<List<HostCommandRow>> = 
        hostCommandRepository.observeHostCommandRows()
            .onEach { Log.d("Rex", "hostCommandRows size=${it.size}") }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    
    fun onDeleteHost(hostId: String, hostNickname: String = "") {
        viewModelScope.launch(GlobalCEH.handler) {
            val success = hostsRepository.deleteHostCascade(hostId)
            if (success) {
                Log.d("Rex", "User deleted host: $hostNickname ($hostId)")
            } else {
                Log.w("Rex", "Host $hostNickname ($hostId) was already removed")
                // Could emit UI event for "Host already removed" snackbar here
            }
        }
    }

    fun onEditCommand(commandId: String, updated: CommandEntity) {
        viewModelScope.launch(GlobalCEH.handler) {
            commandsRepository.updateCommand(updated)
            Log.d("Rex", "User edited command: ${updated.name} ($commandId)")
        }
    }

    fun onDeleteCommand(commandId: String) {
        viewModelScope.launch(GlobalCEH.handler) {
            val deletedRows = commandsRepository.deleteCommandById(commandId)
            if (deletedRows > 0) {
                Log.d("Rex", "User deleted command: $commandId")
            } else {
                Log.w("Rex", "Command $commandId was already removed")
                // Could emit UI event for "Command already removed" snackbar here
            }
        }
    }
}