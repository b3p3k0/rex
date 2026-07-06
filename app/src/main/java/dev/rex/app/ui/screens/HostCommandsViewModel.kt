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
import dev.rex.app.data.db.HostCommandMapping
import dev.rex.app.data.db.HostEntity
import dev.rex.app.data.repo.CommandsRepository
import dev.rex.app.data.repo.HostCommandRepository
import dev.rex.app.data.repo.HostsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HostCommandsViewModel @Inject constructor(
    hostCommandRepository: HostCommandRepository,
    hostsRepository: HostsRepository,
    private val commandsRepository: CommandsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hostId: String = checkNotNull(savedStateHandle["hostId"])

    val host: StateFlow<HostEntity?> =
        hostsRepository.getAllHosts()
            .map { hosts -> hosts.find { it.id == hostId } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    val commands: StateFlow<List<HostCommandMapping>> =
        hostCommandRepository.getAllHostCommandMappings()
            .map { mappings -> mappings.filter { it.id == hostId } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun onDeleteCommand(commandId: String) {
        viewModelScope.launch(GlobalCEH.handler) {
            val deletedRows = commandsRepository.deleteCommandById(commandId)
            if (deletedRows > 0) {
                Log.d("Rex", "User deleted command: $commandId")
            } else {
                Log.w("Rex", "Command $commandId was already removed")
            }
        }
    }
}
