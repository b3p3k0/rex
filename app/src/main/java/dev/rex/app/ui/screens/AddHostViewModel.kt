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
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val originalHost: HostEntity? = null
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

    init {
        // Check if we're in edit mode by looking for hostId in saved state
        val editHostId = savedStateHandle.get<String>("hostId")
        if (editHostId != null) {
            loadHostForEdit(editHostId)
        }
    }

    private fun loadHostForEdit(hostId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, isEditMode = true)

        viewModelScope.launch(GlobalCEH.handler) {
            try {
                val host = hostsRepository.getHostById(hostId)
                if (host != null) {
                    _uiState.value = _uiState.value.copy(
                        nickname = host.nickname,
                        hostname = host.hostname,
                        username = host.username,
                        originalHost = host,
                        isLoading = false
                    )
                } else {
                    // Host not found, revert to add mode
                    _uiState.value = AddHostUiState()
                }
            } catch (e: Exception) {
                Log.e("Rex", "Failed to load host for edit: ${e.message}", e)
                _uiState.value = AddHostUiState()
            }
        }
    }

    fun updateNickname(nickname: String) {
        val sanitized = nickname.filterNot { it == '\n' || it == '\r' }
        _uiState.value = _uiState.value.copy(
            nickname = sanitized,
            nicknameError = if (sanitized.isBlank()) "Nickname is required" else null
        )
    }

    fun updateHostname(hostname: String) {
        val sanitized = hostname.filterNot { it == '\n' || it == '\r' }
        _uiState.value = _uiState.value.copy(
            hostname = sanitized,
            hostnameError = if (sanitized.isBlank()) "Hostname is required" else null
        )
    }

    fun updateUsername(username: String) {
        val sanitized = username.filterNot { it == '\n' || it == '\r' }
        _uiState.value = _uiState.value.copy(
            username = sanitized,
            usernameError = if (sanitized.isBlank()) "Username is required" else null
        )
    }

    fun saveHost(onSuccess: (String) -> Unit) {
        val currentState = _uiState.value
        if (!currentState.canSave) return

        _uiState.value = currentState.copy(isLoading = true)

        viewModelScope.launch(GlobalCEH.handler) {
            try {
                if (currentState.isEditMode && currentState.originalHost != null) {
                    // Update existing host
                    val updatedHost = currentState.originalHost.copy(
                        nickname = currentState.nickname.trim(),
                        hostname = currentState.hostname.trim(),
                        username = currentState.username.trim(),
                        updatedAt = System.currentTimeMillis()
                    )

                    hostsRepository.updateHost(updatedHost)
                    Log.d("Rex", "Updated host id=${updatedHost.id}")

                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess(updatedHost.id)
                } else {
                    // Create new host
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

                    val hostId = hostsRepository.insertHost(host)
                    Log.d("Rex", "inserted host id=$hostId")

                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess(host.id)
                }
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isLoading = false,
                    hostnameError = "Failed to save host: ${e.message}"
                )
            }
        }
    }
}
