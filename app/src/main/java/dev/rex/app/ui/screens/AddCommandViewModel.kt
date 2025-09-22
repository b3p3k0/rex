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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rex.app.data.db.CommandEntity
import dev.rex.app.data.repo.CommandsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AddCommandUiState(
    val name: String = "",
    val command: String = "",
    val nameError: String? = null,
    val commandError: String? = null,
    val isLoading: Boolean = false
) {
    val canSave: Boolean
        get() = name.isNotBlank() && 
                command.isNotBlank() && 
                nameError == null && 
                commandError == null &&
                !isLoading
}

@HiltViewModel
class AddCommandViewModel @Inject constructor(
    private val commandsRepository: CommandsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddCommandUiState())
    val uiState: StateFlow<AddCommandUiState> = _uiState.asStateFlow()

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = if (name.isBlank()) "Command name is required" else null
        )
    }

    fun updateCommand(command: String) {
        _uiState.value = _uiState.value.copy(
            command = command,
            commandError = when {
                command.isBlank() -> "Command is required"
                command.contains('\u0000') -> "Command cannot contain null bytes"
                else -> null
            }
        )
    }

    fun saveCommand(onSuccess: () -> Unit) {
        val currentState = _uiState.value
        if (!currentState.canSave) return

        _uiState.value = currentState.copy(isLoading = true)

        viewModelScope.launch {
            try {
                val command = CommandEntity(
                    id = UUID.randomUUID().toString(),
                    name = currentState.name.trim(),
                    command = currentState.command.trim(),
                    requireConfirmation = true,
                    defaultTimeoutMs = 15000,
                    allowPty = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                
                commandsRepository.insertCommand(command)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isLoading = false,
                    commandError = "Failed to save command: ${e.message}"
                )
            }
        }
    }
}