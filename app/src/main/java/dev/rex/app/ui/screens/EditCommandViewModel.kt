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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditCommandUiState(
    val name: String = "",
    val command: String = "",
    val requireConfirmation: Boolean = true,
    val defaultTimeoutMs: Int = 15000,
    val allowPty: Boolean = false,
    val nameError: String? = null,
    val commandError: String? = null,
    val isLoading: Boolean = false,
    val isLoadingInitial: Boolean = true
) {
    val canSave: Boolean
        get() = name.isNotBlank() &&
                command.isNotBlank() &&
                nameError == null &&
                commandError == null &&
                !isLoading &&
                !isLoadingInitial
}

@HiltViewModel
class EditCommandViewModel @Inject constructor(
    private val commandsRepository: CommandsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val commandId: String = savedStateHandle.get<String>("commandId") ?: ""

    private val _uiState = MutableStateFlow(EditCommandUiState())
    val uiState: StateFlow<EditCommandUiState> = _uiState.asStateFlow()

    init {
        loadCommand()
    }

    private fun loadCommand() {
        viewModelScope.launch(GlobalCEH.handler) {
            try {
                _uiState.value = _uiState.value.copy(isLoadingInitial = true)
                val command = commandsRepository.getCommandById(commandId)
                if (command != null) {
                    _uiState.value = _uiState.value.copy(
                        name = command.name,
                        command = command.command,
                        requireConfirmation = command.requireConfirmation,
                        defaultTimeoutMs = command.defaultTimeoutMs,
                        allowPty = command.allowPty,
                        isLoadingInitial = false
                    )
                    Log.d("Rex", "Loaded command for editing: ${command.name}")
                } else {
                    Log.e("Rex", "Command not found: $commandId")
                    _uiState.value = _uiState.value.copy(isLoadingInitial = false)
                }
            } catch (e: Exception) {
                Log.e("Rex", "Failed to load command $commandId: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoadingInitial = false)
            }
        }
    }

    fun onNameChanged(newName: String) {
        _uiState.value = _uiState.value.copy(
            name = newName,
            nameError = validateName(newName)
        )
    }

    fun onCommandChanged(newCommand: String) {
        _uiState.value = _uiState.value.copy(
            command = newCommand,
            commandError = validateCommand(newCommand)
        )
    }

    fun onRequireConfirmationChanged(requireConfirmation: Boolean) {
        _uiState.value = _uiState.value.copy(requireConfirmation = requireConfirmation)
    }

    fun onDefaultTimeoutChanged(timeoutMs: Int) {
        _uiState.value = _uiState.value.copy(defaultTimeoutMs = timeoutMs)
    }

    fun onAllowPtyChanged(allowPty: Boolean) {
        _uiState.value = _uiState.value.copy(allowPty = allowPty)
    }

    fun onSaveCommand(onSuccess: () -> Unit) {
        val currentState = _uiState.value
        if (!currentState.canSave) return

        viewModelScope.launch(GlobalCEH.handler) {
            try {
                _uiState.value = currentState.copy(isLoading = true)

                val updatedCommand = CommandEntity(
                    id = commandId,
                    name = currentState.name.trim(),
                    command = currentState.command.trim(),
                    requireConfirmation = currentState.requireConfirmation,
                    defaultTimeoutMs = currentState.defaultTimeoutMs,
                    allowPty = currentState.allowPty,
                    createdAt = System.currentTimeMillis(), // Will be ignored in update
                    updatedAt = System.currentTimeMillis()
                )

                commandsRepository.updateCommand(updatedCommand)
                Log.d("Rex", "Updated command: ${updatedCommand.name}")

                onSuccess()
            } catch (e: Exception) {
                Log.e("Rex", "Failed to update command: ${e.message}")
                _uiState.value = currentState.copy(isLoading = false)
            }
        }
    }

    private fun validateName(name: String): String? {
        return when {
            name.isBlank() -> "Name is required"
            name.length > 100 -> "Name must be 100 characters or less"
            else -> null
        }
    }

    private fun validateCommand(command: String): String? {
        return when {
            command.isBlank() -> "Command is required"
            command.length > 1000 -> "Command must be 1000 characters or less"
            else -> null
        }
    }
}