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
import dev.rex.app.data.repo.CommandsRepository
import dev.rex.app.data.repo.HostsRepository
import dev.rex.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AddCommandUiState(
    val name: String = "",
    val command: String = "",
    val hostNickname: String = "",
    val nameError: String? = null,
    val commandError: String? = null,
    val isLoading: Boolean = false,
    val hostNotFound: Boolean = false
) {
    val canSave: Boolean
        get() = name.isNotBlank() &&
                command.isNotBlank() &&
                nameError == null &&
                commandError == null &&
                !isLoading &&
                !hostNotFound
}

@HiltViewModel
class AddCommandViewModel @Inject constructor(
    private val commandsRepository: CommandsRepository,
    private val hostsRepository: HostsRepository,
    private val savedStateHandle: SavedStateHandle,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val hostId: String = savedStateHandle.get<String>("hostId") ?: ""

    private val _uiState = MutableStateFlow(AddCommandUiState())
    val uiState: StateFlow<AddCommandUiState> = _uiState.asStateFlow()

    init {
        loadHostInfo()
    }

    private fun loadHostInfo() {
        if (hostId.isEmpty()) {
            _uiState.value = _uiState.value.copy(hostNotFound = true)
            return
        }

        viewModelScope.launch(GlobalCEH.handler) {
            try {
                val host = hostsRepository.getHostById(hostId)
                if (host != null) {
                    _uiState.value = _uiState.value.copy(
                        hostNickname = host.nickname,
                        hostNotFound = false
                    )
                    Log.d("Rex", "Loaded host for add command: ${host.nickname}")
                } else {
                    _uiState.value = _uiState.value.copy(hostNotFound = true)
                    Log.e("Rex", "Host not found: $hostId")
                }
            } catch (e: Exception) {
                Log.e("Rex", "Failed to load host $hostId: ${e.message}")
                _uiState.value = _uiState.value.copy(hostNotFound = true)
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = validateName(name)
        )
    }

    fun updateCommand(command: String) {
        _uiState.value = _uiState.value.copy(
            command = command,
            commandError = validateCommand(command)
        )
    }

    private fun validateName(name: String): String? {
        return when {
            name.isBlank() -> "Command name is required"
            name.length > 100 -> "Name must be 100 characters or less"
            else -> null
        }
    }

    private fun validateCommand(command: String): String? {
        return when {
            command.isBlank() -> "Command is required"
            command.contains('\u0000') -> "Command cannot contain null bytes"
            command.length > 1000 -> "Command must be 1000 characters or less"
            else -> null
        }
    }

    fun saveCommand(onSuccess: () -> Unit) {
        val currentState = _uiState.value
        if (!currentState.canSave) return

        _uiState.value = currentState.copy(isLoading = true)

        viewModelScope.launch(GlobalCEH.handler) {
            try {
                val defaultTimeoutSeconds = runCatching {
                    settingsStore.defaultCommandTimeoutSeconds.first()
                }.getOrElse { SettingsStore.DEFAULT_COMMAND_TIMEOUT_SECONDS }

                val command = CommandEntity(
                    id = UUID.randomUUID().toString(),
                    name = currentState.name.trim(),
                    command = currentState.command.trim(),
                    requireConfirmation = true,
                    defaultTimeoutMs = defaultTimeoutSeconds * 1000,
                    allowPty = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val commandId = commandsRepository.addCommandForHost(hostId, command)
                Log.d("Rex", "Saved command=$commandId for host=$hostId")
                _uiState.value = currentState.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                Log.e("Rex", "Failed to save command for host $hostId: ${e.message}")
                _uiState.value = currentState.copy(
                    isLoading = false,
                    commandError = "Failed to save command: ${e.message}"
                )
            }
        }
    }
}
