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
import dev.rex.app.core.Gatekeeper
import dev.rex.app.core.GlobalCEH
import dev.rex.app.core.SecurityGateRequiredException
import dev.rex.app.data.crypto.KeyVault
import dev.rex.app.data.crypto.KeyBlobId
import dev.rex.app.data.db.HostEntity
import dev.rex.app.data.db.KeyBlobEntity
import dev.rex.app.data.repo.HostsRepository
import dev.rex.app.data.repo.KeysRepository
import dev.rex.app.data.ssh.SshProvisioner
import dev.rex.app.data.ssh.ProvisionResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class HostSecurityAction(
    val title: String,
    val subtitle: String
) {
    object GenerateKey : HostSecurityAction(
        "Generate SSH Key",
        "Authenticate to create a new Ed25519 key pair"
    )
    object ImportKey : HostSecurityAction(
        "Import Private Key",
        "Authenticate to import an existing private key"
    )
    object Deploy : HostSecurityAction(
        "Deploy SSH Key",
        "Authenticate to deploy key to remote host"
    )
    object Test : HostSecurityAction(
        "Test SSH Key",
        "Authenticate to test key-based authentication"
    )
    object Delete : HostSecurityAction(
        "Delete SSH Key",
        "Authenticate to permanently delete this key"
    )
}

data class HostDetailUiState(
    val host: HostEntity? = null,
    val loading: Boolean = false,
    val keyStatus: String = "none",
    val keyProvisionedAt: Long? = null,
    val publicKeyPreview: String? = null,
    val showPasswordDialog: Boolean = false,
    val provisionInProgress: Boolean = false,
    val provisionOutput: String = "",
    val lastProvisionResult: ProvisionResult? = null,
    val showImportDialog: Boolean = false,
    val importPemText: String = "",
    val importError: String? = null,
    val showTestDialog: Boolean = false,
    val testInProgress: Boolean = false,
    val testResult: ProvisionResult? = null,
    val showDeleteConfirmation: Boolean = false,
    val showSecurityGate: Boolean = false,
    val pendingAction: HostSecurityAction? = null,
    val securityGateTitle: String = "",
    val securityGateSubtitle: String = "",
    val snackbarMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class HostDetailViewModel @Inject constructor(
    private val hostsRepository: HostsRepository,
    private val keysRepository: KeysRepository,
    private val keyVault: KeyVault,
    private val sshProvisioner: SshProvisioner,
    private val gatekeeper: Gatekeeper,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hostId: String = checkNotNull(savedStateHandle["hostId"])

    private val _uiState = MutableStateFlow(HostDetailUiState())
    val uiState: StateFlow<HostDetailUiState> = _uiState.asStateFlow()

    init {
        loadHostDetails()
    }

    private fun loadHostDetails() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(loading = true, error = null)

                val host = hostsRepository.getHostById(hostId)
                if (host != null) {
                    _uiState.value = _uiState.value.copy(
                        host = host,
                        keyStatus = host.keyProvisionStatus,
                        keyProvisionedAt = host.keyProvisionedAt,
                        loading = false
                    )

                    // Load public key preview if key exists
                    host.keyBlobId?.let { keyBlobId ->
                        loadPublicKeyPreview(keyBlobId)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Host not found",
                        loading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("Rex", "Failed to load host details", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load host: ${e.message}",
                    loading = false
                )
            }
        }
    }

    private suspend fun loadPublicKeyPreview(keyBlobId: String) {
        try {
            val publicKey = keyVault.getPublicKeyOpenssh(KeyBlobId(keyBlobId))
            _uiState.value = _uiState.value.copy(publicKeyPreview = publicKey)
        } catch (e: Exception) {
            Log.w("Rex", "Failed to load public key preview", e)
        }
    }

    fun generateNewKey() {
        executeAction(HostSecurityAction.GenerateKey)
    }

    private suspend fun performGenerateKey() {
        _uiState.value = _uiState.value.copy(loading = true, error = null)

        val (keyBlobId, publicKey) = keyVault.generateEd25519()

        // Assign key to host
        hostsRepository.assignKeyToHost(hostId, keyBlobId.id)

        _uiState.value = _uiState.value.copy(
            keyStatus = "pending",
            publicKeyPreview = publicKey,
            loading = false
        )

        // Reload host details to get updated status
        loadHostDetails()
    }

    fun showImportDialog() {
        _uiState.value = _uiState.value.copy(
            showImportDialog = true,
            importPemText = "",
            importError = null
        )
    }

    fun hideImportDialog() {
        _uiState.value = _uiState.value.copy(showImportDialog = false)
    }

    fun updateImportPemText(text: String) {
        _uiState.value = _uiState.value.copy(importPemText = text, importError = null)
    }

    fun importPrivateKey() {
        executeAction(HostSecurityAction.ImportKey)
    }

    private suspend fun performImportKey() {
        val pemBytes = _uiState.value.importPemText.toByteArray(Charsets.UTF_8)

        if (!keyVault.validatePrivateKeyPem(pemBytes)) {
            _uiState.value = _uiState.value.copy(importError = "Invalid PEM format")
            return
        }

        _uiState.value = _uiState.value.copy(loading = true, importError = null)

        val keyBlobId = keyVault.importPrivateKeyPem(pemBytes)

        // Assign key to host
        hostsRepository.assignKeyToHost(hostId, keyBlobId.id)

        _uiState.value = _uiState.value.copy(
            showImportDialog = false,
            keyStatus = "pending",
            loading = false
        )

        // Load public key preview and reload host details
        loadPublicKeyPreview(keyBlobId.id)
        loadHostDetails()
    }

    fun showPasswordDialog() {
        // Guard against duplicate calls
        if (_uiState.value.showPasswordDialog || _uiState.value.provisionInProgress) {
            Log.d("Rex", "showPasswordDialog() ignored - already showing dialog or provision in progress")
            return
        }
        Log.d("Rex", "showPasswordDialog() executing Deploy action")
        executeAction(HostSecurityAction.Deploy)
    }

    private fun performShowPasswordDialog() {
        _uiState.value = _uiState.value.copy(showPasswordDialog = true)
    }

    fun hidePasswordDialog() {
        _uiState.value = _uiState.value.copy(
            showPasswordDialog = false,
            pendingAction = null // Clear pending action to prevent re-execution
        )
        Log.d("Rex", "hidePasswordDialog() - cleared pendingAction")
    }

    fun deployKey(password: String) {
        val currentHost = _uiState.value.host ?: return
        val keyBlobId = currentHost.keyBlobId ?: return

        Log.d("Rex", "deployKey() starting for host ${currentHost.nickname}")

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    showPasswordDialog = false,
                    provisionInProgress = true,
                    provisionOutput = "",
                    error = null
                )

                val result = sshProvisioner.deployKeyToHost(
                    hostname = currentHost.hostname,
                    port = currentHost.port,
                    username = currentHost.username,
                    password = password,
                    keyBlobId = KeyBlobId(keyBlobId),
                    timeoutsMs = Pair(currentHost.connectTimeoutMs, currentHost.readTimeoutMs)
                )

                _uiState.value = _uiState.value.copy(
                    provisionInProgress = false,
                    lastProvisionResult = result,
                    provisionOutput = "STDOUT:\n${result.stdout}\n\nSTDERR:\n${result.stderr}"
                )

                if (result.success) {
                    // Update host provision status
                    hostsRepository.updateKeyProvisionStatus(
                        hostId = hostId,
                        keyBlobId = keyBlobId,
                        provisionStatus = "success"
                    )

                    // Log the successful provisioning
                    keysRepository.insertProvisionLog(
                        dev.rex.app.data.db.KeyProvisionLogEntity(
                            id = UUID.randomUUID().toString(),
                            hostId = hostId,
                            keyBlobId = keyBlobId,
                            ts = System.currentTimeMillis(),
                            operation = "deploy",
                            status = "success",
                            durationMs = result.durationMs.toInt(),
                            stdoutPreview = result.stdout.take(500),
                            stderrPreview = result.stderr.take(500),
                            errorMessage = null
                        )
                    )

                    // Clear any pending action to prevent duplicate executions
                    _uiState.value = _uiState.value.copy(pendingAction = null)

                    loadHostDetails()
                } else {
                    // Update host provision status to failed
                    hostsRepository.updateKeyProvisionStatus(
                        hostId = hostId,
                        keyBlobId = keyBlobId,
                        provisionStatus = "failed"
                    )

                    // Log the failed provisioning
                    keysRepository.insertProvisionLog(
                        dev.rex.app.data.db.KeyProvisionLogEntity(
                            id = UUID.randomUUID().toString(),
                            hostId = hostId,
                            keyBlobId = keyBlobId,
                            ts = System.currentTimeMillis(),
                            operation = "deploy",
                            status = "failed",
                            durationMs = result.durationMs.toInt(),
                            stdoutPreview = result.stdout.take(500),
                            stderrPreview = result.stderr.take(500),
                            errorMessage = result.errorMessage
                        )
                    )
                }

            } catch (e: SecurityGateRequiredException) {
                _uiState.value = _uiState.value.copy(
                    provisionInProgress = false,
                    error = "Device authentication required"
                )
            } catch (e: Exception) {
                Log.e("Rex", "Failed to deploy key", e)
                _uiState.value = _uiState.value.copy(
                    provisionInProgress = false,
                    error = "Failed to deploy key: ${e.message}"
                )
            }
        }
    }

    fun testKeyAuth() {
        executeAction(HostSecurityAction.Test)
    }

    private suspend fun performTestKey() {
        val currentHost = _uiState.value.host ?: return
        val keyBlobId = currentHost.keyBlobId ?: return

        _uiState.value = _uiState.value.copy(
            showTestDialog = true,
            testInProgress = true,
            testResult = null,
            error = null
        )

        val result = sshProvisioner.testKeyBasedAuth(
            hostname = currentHost.hostname,
            port = currentHost.port,
            username = currentHost.username,
            keyBlobId = KeyBlobId(keyBlobId),
            timeoutsMs = Pair(currentHost.connectTimeoutMs, currentHost.readTimeoutMs),
            expectedPin = currentHost.pinnedHostKeyFingerprint?.let {
                dev.rex.app.data.ssh.HostPin("ssh-rsa", it)
            }
        )

        _uiState.value = _uiState.value.copy(
            testInProgress = false,
            testResult = result
        )
    }

    fun hideTestDialog() {
        _uiState.value = _uiState.value.copy(showTestDialog = false)
    }

    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = true)
    }

    fun hideDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = false)
    }

    fun deleteKey() {
        executeAction(HostSecurityAction.Delete)
    }

    private suspend fun performDeleteKey() {
        val currentHost = _uiState.value.host ?: return
        val keyBlobId = currentHost.keyBlobId ?: return

        _uiState.value = _uiState.value.copy(loading = true, error = null)

        // Get key blob for deletion
        val keyBlob = keysRepository.getKeyBlobById(keyBlobId)
        if (keyBlob != null) {
            keyVault.deleteKey(KeyBlobId(keyBlobId))
        }

        // Update host to remove key association
        hostsRepository.updateKeyProvisionStatus(
            hostId = hostId,
            keyBlobId = null,
            provisionStatus = "none"
        )

        _uiState.value = _uiState.value.copy(
            showDeleteConfirmation = false,
            keyStatus = "none",
            publicKeyPreview = null,
            loading = false
        )

        loadHostDetails()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearProvisionOutput() {
        _uiState.value = _uiState.value.copy(
            provisionOutput = "",
            lastProvisionResult = null
        )
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun onSecurityGateAuthenticated() {
        val pendingAction = _uiState.value.pendingAction
        _uiState.value = _uiState.value.copy(
            showSecurityGate = false,
            pendingAction = null,
            securityGateTitle = "",
            securityGateSubtitle = ""
        )

        pendingAction?.let { action ->
            executeAction(action)
        }
    }

    fun onSecurityGateCancelled() {
        _uiState.value = _uiState.value.copy(
            showSecurityGate = false,
            pendingAction = null,
            securityGateTitle = "",
            securityGateSubtitle = "",
            snackbarMessage = "Authentication cancelled"
        )
    }


    private fun executeAction(action: HostSecurityAction) {
        viewModelScope.launch {
            try {
                gatekeeper.requireGateForKeyOperation()

                // Gate is open, execute the specific action
                when (action) {
                    HostSecurityAction.GenerateKey -> performGenerateKey()
                    HostSecurityAction.ImportKey -> performImportKey()
                    HostSecurityAction.Deploy -> performShowPasswordDialog()
                    HostSecurityAction.Test -> performTestKey()
                    HostSecurityAction.Delete -> performDeleteKey()
                }

            } catch (e: SecurityGateRequiredException) {
                _uiState.value = _uiState.value.copy(
                    showSecurityGate = true,
                    pendingAction = action,
                    securityGateTitle = action.title,
                    securityGateSubtitle = action.subtitle
                )
            } catch (e: Exception) {
                Log.e("Rex", "Failed to execute ${action.title}", e)
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Failed to ${action.title.lowercase()}: ${e.message}"
                )
            }
        }
    }
}