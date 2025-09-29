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
    val commandName: String = "",
    val showOutputDialog: Boolean = false,
    val activeMappingId: String? = null
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

    companion object {
        private const val OUTPUT_LINE_LIMIT = 128
    }

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var executionJob: Job? = null
    private var timerJob: Job? = null
    private var clipboardClearJob: Job? = null
    private val rawOutput = StringBuilder()

    private fun buildDisplayOutput(): String {
        if (rawOutput.isEmpty()) return ""

        val normalized = rawOutput.toString().replace("\r\n", "\n")
        var lines = normalized.lines()
        if (lines.isNotEmpty() && lines.last().isEmpty()) {
            lines = lines.dropLast(1)
        }

        if (lines.isEmpty()) return ""

        val excess = (lines.size - OUTPUT_LINE_LIMIT).coerceAtLeast(0)
        val linesToShow = if (excess > 0) lines.takeLast(OUTPUT_LINE_LIMIT) else lines
        val body = linesToShow.joinToString("\n")
        return if (excess > 0) "... (+$excess more lines)\n$body" else body
    }

    private fun emitFinalState(
        displayOutput: String,
        exitCode: Int?,
        allowCopy: Boolean,
        errorMessage: String? = null
    ) {
        val newState = _uiState.value.copy(
            isRunning = false,
            exitCode = exitCode,
            canCopy = allowCopy && displayOutput.isNotBlank(),
            output = displayOutput,
            error = errorMessage,
            showOutputDialog = true
        )
        android.util.Log.d(
            "RexSsh",
            "emitFinalState: exitCode=${exitCode}, canCopy=${newState.canCopy}, showOutputDialog=${newState.showOutputDialog}, output=[${displayOutput.replace("\n", "\\n")}]"
        )
        _uiState.value = newState
    }

    fun startSession(mappingId: String) {
        // Cancel any in-flight execution job to prevent double-tap races
        executionJob?.cancel()

        viewModelScope.launch(GlobalCEH.handler) {
            try {
                // Fetch the mapping
                val mapping = hostCommandRepository.getHostCommandMapping(mappingId)
                if (mapping == null) {
                    _uiState.value = _uiState.value.copy(
                        error = "Command mapping not found: $mappingId",
                        activeMappingId = null,
                        isRunning = false
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
                    canCopy = false,
                    showOutputDialog = false,
                    activeMappingId = mapping.mappingId
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

                // Authenticate based on auth method
                android.util.Log.d("RexSsh", "Starting authentication with method: ${mapping.authMethod}")
                when (mapping.authMethod.lowercase()) {
                    "key" -> {
                        android.util.Log.d("RexSsh", "Key authentication selected")
                        if (mapping.keyBlobId.isNullOrBlank()) {
                            android.util.Log.e("RexSsh", "Key authentication failed: no key blob ID")
                            _uiState.value = _uiState.value.copy(
                                error = "Key authentication required but no key blob ID found",
                                isRunning = false
                            )
                            return@launch
                        }

                        android.util.Log.d("RexSsh", "About to decrypt private key with ID: ${mapping.keyBlobId}")
                        val keyBytes = try {
                            keyVault.decryptPrivateKey(KeyBlobId(mapping.keyBlobId))
                        } catch (e: Exception) {
                            android.util.Log.e("RexSsh", "Failed to decrypt private key: ${e.javaClass.simpleName}: ${e.message}", e)
                            val userFriendlyMessage = when {
                                e.message?.contains("device security changes") == true ->
                                    "Private key access failed. Please unlock your device and try again."
                                e.message?.contains("User not authenticated") == true ||
                                e.message?.contains("SecurityGateRequiredException") == true ->
                                    "Device authentication required. Please unlock your device."
                                e.message?.contains("corrupted") == true ||
                                e.message?.contains("Please re-import your SSH key") == true ->
                                    "SSH key data is corrupted. Please re-import your SSH key."
                                e.message?.contains("keystore") == true ->
                                    "Keystore access failed. You may need to re-import your SSH key."
                                e.message?.contains("Attempt to get length of null array") == true ->
                                    "SSH key data is corrupted. Please re-import your SSH key."
                                else -> "Failed to decrypt private key: ${e.message}"
                            }
                            _uiState.value = _uiState.value.copy(
                                error = userFriendlyMessage,
                                isRunning = false
                            )
                            return@launch
                        }
                        android.util.Log.d("RexSsh", "Private key decrypted successfully, ${keyBytes.size} bytes")

                        android.util.Log.d("RexSsh", "About to authenticate with SSH client")
                        try {
                            sshClient.authUsernameKey(mapping.username, keyBytes)
                            android.util.Log.d("RexSsh", "SSH authentication completed successfully")
                        } catch (e: Exception) {
                            android.util.Log.e("RexSsh", "SSH authentication failed: ${e.javaClass.simpleName}: ${e.message}", e)
                            _uiState.value = _uiState.value.copy(
                                error = "SSH authentication failed: ${e.message}",
                                isRunning = false
                            )
                            return@launch
                        }

                        // TOFU fingerprint update: only after successful authentication
                        if (!mapping.strictHostKey && mapping.pinnedHostKeyFingerprint.isNullOrBlank()) {
                            hostsRepository.updateHostFingerprint(mapping.id, actualPin.sha256)
                        }
                    }
                    "password" -> {
                        // TODO: Implement password authentication support
                        _uiState.value = _uiState.value.copy(
                            error = "Password authentication is not yet supported",
                            isRunning = false
                        )
                        return@launch
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            error = "Unsupported authentication method: ${mapping.authMethod}",
                            isRunning = false
                        )
                        return@launch
                    }
                }

                // Execute the command and wait for completion before closing the client
                executeCommandWithTimeout(mapping.command, mapping.allowPty, mapping.defaultTimeoutMs)
                executionJob?.join()

            } catch (e: Exception) {
                val allowCopy = settingsStore.allowCopyOutput.first()
                val displayOutput = Redactor.redact(buildDisplayOutput())
                emitFinalState(
                    displayOutput = displayOutput,
                    exitCode = null,
                    allowCopy = allowCopy,
                    errorMessage = "Session failed: ${e.message}"
                )
                rawOutput.clear()
            } finally {
                sshClient.close()
                stopTimer()
            }
        }
    }

    private fun executeCommandWithTimeout(command: String, pty: Boolean = false, timeoutMs: Int) {
        if (_uiState.value.isRunning) return

        _uiState.value = _uiState.value.copy(
            isRunning = true,
            showOutputDialog = false,
            output = "",
            exitCode = null,
            canCopy = false,
            error = null
        )

        rawOutput.clear()
        startTimer()

        executionJob = viewModelScope.launch(GlobalCEH.handler) {
            try {
                // Stream command output
                sshClient.exec(command, pty).collect { byteString ->
                    val text = byteString.utf8()
                    rawOutput.append(text)
                }

                // Wait for exit code with timeout from mapping or default
                val actualTimeout = if (timeoutMs > 0) timeoutMs else 30000
                val exitCode = sshClient.waitExitCode(actualTimeout)

                val rawDisplay = buildDisplayOutput()
                android.util.Log.d("RexSsh", "buildDisplayOutput raw=[${rawDisplay.replace("\n", "\\n")}]")

                android.util.Log.d("RexSsh", "About to call Redactor.redact")
                val displayOutput = Redactor.redact(rawDisplay)
                android.util.Log.d("RexSsh", "Redactor completed, displayOutput=[${displayOutput.replace("\n", "\\n")}]")

                android.util.Log.d("RexSsh", "About to check allowCopyOutput setting")
                val allowCopy = try {
                    settingsStore.allowCopyOutput.first()
                } catch (e: Exception) {
                    android.util.Log.e("RexSsh", "Failed to get allowCopyOutput setting: ${e.message}", e)
                    false // Default to false if settings access fails
                }
                android.util.Log.d("RexSsh", "allowCopyOutput=$allowCopy")

                android.util.Log.d("RexSsh", "About to create new state")
                val newState = try {
                    _uiState.value.copy(
                        isRunning = false,
                        exitCode = exitCode,
                        canCopy = allowCopy && displayOutput.isNotBlank(),
                        output = displayOutput ?: "",
                        error = null,
                        showOutputDialog = true
                    )
                } catch (e: Exception) {
                    android.util.Log.e("RexSsh", "Failed to create new state: ${e.message}", e)
                    // Fallback state
                    SessionUiState(
                        isRunning = false,
                        exitCode = exitCode,
                        canCopy = false,
                        output = displayOutput ?: "",
                        error = null,
                        showOutputDialog = true
                    )
                }
                android.util.Log.d("RexSsh", "New state created: $newState")

                android.util.Log.d("RexSsh", "About to update _uiState.value")
                try {
                    _uiState.value = newState
                    android.util.Log.d("RexSsh", "UI state updated successfully")
                } catch (e: Exception) {
                    android.util.Log.e("RexSsh", "Failed to update UI state: ${e.message}", e)
                }
                rawOutput.clear()

            } catch (e: Exception) {
                android.util.Log.e("RexSsh", "Exception in executeCommandWithTimeout: ${e.javaClass.simpleName}: ${e.message}", e)
                val rawDisplay = buildDisplayOutput()
                android.util.Log.d("RexSsh", "buildDisplayOutput (error) raw=[${rawDisplay.replace("\n", "\\n")}]")

                val displayOutput = try {
                    Redactor.redact(rawDisplay)
                } catch (redactorException: Exception) {
                    android.util.Log.e("RexSsh", "Redactor failed in error handler: ${redactorException.message}", redactorException)
                    rawDisplay // Use raw display if redactor fails
                }

                val allowCopy = try {
                    settingsStore.allowCopyOutput.first()
                } catch (settingsException: Exception) {
                    android.util.Log.e("RexSsh", "Settings access failed in error handler: ${settingsException.message}", settingsException)
                    false
                }

                val newState = _uiState.value.copy(
                    isRunning = false,
                    exitCode = null,
                    canCopy = allowCopy && displayOutput.isNotBlank(),
                    output = displayOutput,
                    error = "Execution failed: ${e.message}",
                    showOutputDialog = true
                )
                android.util.Log.d("RexSsh", "emitFinalState error state=$newState")
                _uiState.value = newState
                rawOutput.clear()
            }
            // Note: cleanup handled by startSession's finally block
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
                canCopy = allowCopy,
                activeMappingId = null
            )
            stopTimer()
            rawOutput.clear()
        }
    }

    fun showOutputDialog() {
        _uiState.value = _uiState.value.copy(showOutputDialog = true)
    }

    fun dismissOutputDialog() {
        _uiState.value = _uiState.value.copy(
            showOutputDialog = false,
            activeMappingId = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
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
