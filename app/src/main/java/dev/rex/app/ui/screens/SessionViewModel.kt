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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.rex.app.core.DangerousCommandValidator
import dev.rex.app.core.Gatekeeper
import dev.rex.app.core.GlobalCEH
import dev.rex.app.core.Redactor
import dev.rex.app.data.crypto.KeyBlobId
import dev.rex.app.data.crypto.KeyVault
import dev.rex.app.data.repo.HostCommandRepository
import dev.rex.app.data.repo.HostsRepository
import dev.rex.app.data.settings.SettingsStore
import dev.rex.app.data.ssh.HostPin
import dev.rex.app.data.ssh.SshClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import javax.inject.Inject

data class SessionUiState(
    val output: String = "",
    val isRunning: Boolean = false,
    val exitCode: Int? = null,
    val elapsedTimeMs: Long = 0,
    val canCopy: Boolean = false,
    val error: String? = null,
    val hostNickname: String = "",
    val commandName: String = ""
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sshClient: SshClient,
    private val savedStateHandle: SavedStateHandle,
    private val gatekeeper: Gatekeeper,
    private val settingsStore: SettingsStore,
    private val dangerousCommandValidator: DangerousCommandValidator,
    private val hostCommandRepository: HostCommandRepository,
    private val keyVault: KeyVault,
    private val hostsRepository: HostsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var executionJob: Job? = null
    private var timerJob: Job? = null
    private var clipboardClearJob: Job? = null
    private val rawOutput = StringBuilder()

    fun startSession(mappingId: String) {
        // Cancel any in-flight execution job to prevent double-tap races
        executionJob?.cancel()

        viewModelScope.launch(GlobalCEH.handler) {
            try {
                // Fetch the mapping
                val mapping = hostCommandRepository.getHostCommandMapping(mappingId)
                if (mapping == null) {
                    _uiState.value = _uiState.value.copy(
                        error = "Command mapping not found: $mappingId"
                    )
                    return@launch
                }

                // Update UI state with mapping info and reset for new session
                _uiState.value = _uiState.value.copy(
                    hostNickname = mapping.nickname,
                    commandName = mapping.name,
                    output = "",
                    exitCode = null,
                    elapsedTimeMs = 0,
                    error = null,
                    canCopy = false
                )

                // Check if command is dangerous and require gate
                if (dangerousCommandValidator.isDangerous(mapping.command)) {
                    gatekeeper.requireGateForDangerousCommand()
                }

                // Build expected host pin if we have a fingerprint
                val expectedPin = if (!mapping.pinnedHostKeyFingerprint.isNullOrBlank()) {
                    HostPin("ssh-rsa", mapping.pinnedHostKeyFingerprint)
                } else null

                // Connect to host
                val actualPin = sshClient.connect(
                    host = mapping.hostname,
                    port = mapping.port,
                    timeoutsMs = Pair(mapping.connectTimeoutMs, mapping.readTimeoutMs),
                    expectedPin = expectedPin
                )

                // TOFU fingerprint update: if no strict host key and no stored fingerprint
                if (!mapping.strictHostKey && mapping.pinnedHostKeyFingerprint.isNullOrBlank()) {
                    hostsRepository.updateHostFingerprint(mapping.id, actualPin.sha256)
                }

                // Authenticate based on auth method
                when (mapping.authMethod.lowercase()) {
                    "key" -> {
                        if (mapping.keyBlobId.isNullOrBlank()) {
                            _uiState.value = _uiState.value.copy(
                                error = "Key authentication required but no key blob ID found"
                            )
                            return@launch
                        }

                        val keyBytes = keyVault.decryptPrivateKey(KeyBlobId(mapping.keyBlobId))
                        sshClient.authUsernameKey(mapping.username, keyBytes)
                    }
                    "password" -> {
                        _uiState.value = _uiState.value.copy(
                            error = "Password authentication is not yet supported"
                        )
                        return@launch
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            error = "Unsupported authentication method: ${mapping.authMethod}"
                        )
                        return@launch
                    }
                }

                // Execute the command
                executeCommandWithTimeout(mapping.command, mapping.allowPty, mapping.defaultTimeoutMs)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Session failed: ${e.message}"
                )
            } finally {
                sshClient.close()
                stopTimer()
            }
        }
    }

    private fun executeCommandWithTimeout(command: String, pty: Boolean = false, timeoutMs: Int) {
        if (_uiState.value.isRunning) return

        _uiState.value = _uiState.value.copy(
            isRunning = true
        )

        rawOutput.clear()
        startTimer()

        executionJob = viewModelScope.launch(GlobalCEH.handler) {
            try {
                // Stream command output
                sshClient.exec(command, pty).collect { byteString ->
                    val text = byteString.utf8()
                    rawOutput.append(text)

                    // Apply redaction before displaying
                    val redactedOutput = Redactor.redact(rawOutput.toString())

                    _uiState.value = _uiState.value.copy(
                        output = redactedOutput
                    )
                }

                // Wait for exit code with timeout from mapping or default
                val actualTimeout = if (timeoutMs > 0) timeoutMs else 30000
                val exitCode = sshClient.waitExitCode(actualTimeout)

                val allowCopy = settingsStore.allowCopyOutput.first()
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    exitCode = exitCode,
                    canCopy = allowCopy && exitCode == 0 // Only allow copy on success
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    error = "Execution failed: ${e.message}",
                    canCopy = false
                )
            } finally {
                stopTimer()
                rawOutput.clear()
            }
        }
    }

    fun executeCommand(command: String, pty: Boolean = false) {
        executeCommandWithTimeout(command, pty, 30000)
    }

    fun cancelExecution() {
        executionJob?.cancel()
        viewModelScope.launch(GlobalCEH.handler) {
            try {
                sshClient.cancel()
            } catch (e: Exception) {
                // Ignore cancel errors
            }
            val allowCopy = settingsStore.allowCopyOutput.first()
            _uiState.value = _uiState.value.copy(
                isRunning = false,
                canCopy = allowCopy
            )
            stopTimer()
            rawOutput.clear()
        }
    }

    fun copyOutput() {
        viewModelScope.launch(GlobalCEH.handler) {
            val allowCopy = settingsStore.allowCopyOutput.first()
            if (!allowCopy) return@launch

            val output = _uiState.value.output
            if (output.isBlank()) return@launch

            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("Rex Output", output).apply {
                // Add FLAG_SENSITIVE for Android 13+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    description.extras = android.os.PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                }
            }
            clipboardManager.setPrimaryClip(clipData)

            // Auto-clear clipboard after 60 seconds per SPEC requirements
            clipboardClearJob?.cancel()
            clipboardClearJob = viewModelScope.launch(GlobalCEH.handler) {
                delay(60_000) // 60 seconds
                try {
                    val emptyClip = ClipData.newPlainText("", "")
                    clipboardManager.setPrimaryClip(emptyClip)
                } catch (e: Exception) {
                    // Ignore clipboard clear errors
                }
            }
        }
    }

    private fun startTimer() {
        val startTime = System.currentTimeMillis()
        timerJob = viewModelScope.launch(GlobalCEH.handler) {
            while (_uiState.value.isRunning) {
                val elapsed = System.currentTimeMillis() - startTime
                _uiState.value = _uiState.value.copy(elapsedTimeMs = elapsed)
                delay(1000) // Update every second
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        executionJob?.cancel()
        timerJob?.cancel()
        clipboardClearJob?.cancel()

        // Clear sensitive data from memory
        rawOutput.clear()

        // Clean up SSH client
        viewModelScope.launch(GlobalCEH.handler) {
            try {
                sshClient.close()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    fun isDangerous(command: String): Boolean {
        return dangerousCommandValidator.isDangerous(command)
    }

    fun getDangerReason(command: String): String {
        return dangerousCommandValidator.getDangerReason(command) ?: "Unknown danger"
    }
}