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
import dev.rex.app.data.db.HostEntity
import dev.rex.app.data.repo.HostsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AddHostUiState(
    val nickname: String = "",
    val hostname: String = "",
    val username: String = "",
    val nicknameError: String? = null,
    val hostnameError: String? = null,
    val usernameError: String? = null,
    val isLoading: Boolean = false
) {
    val canSave: Boolean
        get() = nickname.isNotBlank() && 
                hostname.isNotBlank() && 
                username.isNotBlank() && 
                nicknameError == null && 
                hostnameError == null && 
                usernameError == null &&
                !isLoading
}

@HiltViewModel
class AddHostViewModel @Inject constructor(
    private val hostsRepository: HostsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddHostUiState())
    val uiState: StateFlow<AddHostUiState> = _uiState.asStateFlow()

    fun updateNickname(nickname: String) {
        _uiState.value = _uiState.value.copy(
            nickname = nickname,
            nicknameError = if (nickname.isBlank()) "Nickname is required" else null
        )
    }

    fun updateHostname(hostname: String) {
        _uiState.value = _uiState.value.copy(
            hostname = hostname,
            hostnameError = if (hostname.isBlank()) "Hostname is required" else null
        )
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(
            username = username,
            usernameError = if (username.isBlank()) "Username is required" else null
        )
    }

    fun saveHost(onSuccess: () -> Unit) {
        val currentState = _uiState.value
        if (!currentState.canSave) return

        _uiState.value = currentState.copy(isLoading = true)

        viewModelScope.launch {
            try {
                val host = HostEntity(
                    id = UUID.randomUUID().toString(),
                    nickname = currentState.nickname.trim(),
                    hostname = currentState.hostname.trim(),
                    port = 22,
                    username = currentState.username.trim(),
                    authMethod = "key",
                    keyBlobId = null, // Stubbed for now
                    connectTimeoutMs = 8000,
                    readTimeoutMs = 15000,
                    strictHostKey = true,
                    pinnedHostKeyFingerprint = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                
                hostsRepository.insertHost(host)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isLoading = false,
                    hostnameError = "Failed to save host: ${e.message}"
                )
            }
        }
    }
}