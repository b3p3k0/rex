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
import dev.rex.app.core.SecurityGateRequiredException
import dev.rex.app.core.SecurityManager
import dev.rex.app.data.crypto.KeyBlobId
import dev.rex.app.data.crypto.KeyVault
import dev.rex.app.data.db.LogEntity
import dev.rex.app.data.repo.HostCommandRepository
import dev.rex.app.data.repo.HostsRepository
import dev.rex.app.data.repo.LogsRepository
import dev.rex.app.data.settings.SettingsStore
import dev.rex.app.data.ssh.HostPin
import dev.rex.app.data.ssh.SshClient
import dev.rex.app.data.ssh.TofuRequiredException
import dev.rex.app.ui.components.TofuPrompt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import java.util.UUID
import javax.inject.Inject

data class SudoPasswordPrompt(
    val hostNickname: String,
    val username: String,
    val commandName: String
)

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
    val activeMappingId: String? = null,
    val tofuPrompt: TofuPrompt? = null,
    val showSecurityGate: Boolean = false,
    val sudoPasswordPrompt: SudoPasswordPrompt? = null
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
    private val hostsRepository: HostsRepository,
    private val securityManager: SecurityManager,
    private val logsRepository: LogsRepository
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

    // Execution-log metrics for the current run
    private var streamedBytes = 0L
    private var executionStartMs = 0L

    // Held only between the sudo password dialog and the retried session
    private var pendingSudoPassword: String? = null
    private var pendingSudoRemember: Boolean = false

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
        // SECURITY: metadata-only logging; never log command output content
        android.util.Log.d(
            "RexSsh",
            "emitFinalState: exitCode=$exitCode, outputChars=${displayOutput.length}, canCopy=${newState.canCopy}"
        )
        _uiState.value = newState
    }

    // SECURITY: metadata-only execution log; command output is never stored.
    // messageRedacted holds only sanitized error/status text.
    private suspend fun recordExecutionLog(
        exitCode: Int?,
        bytesStdout: Long,
        durationMs: Long,
        errorMessage: String?
    ) {
        val state = _uiState.value
        if (state.commandName.isBlank()) return
        val ts = System.currentTimeMillis()
        try {
            logsRepository.insertLog(
                LogEntity(
                    id = UUID.randomUUID().toString(),
                    ts = ts,
                    hostNickname = state.hostNickname,
                    commandName = state.commandName,
                    exitCode = exitCode,
                    durationMs = durationMs.toInt(),
                    bytesStdout = bytesStdout.toInt(),
                    bytesStderr = 0,
                    status = if (exitCode == 0) "success" else "failure",
                    messageRedacted = errorMessage?.let { Redactor.redact(it) },
                    idxSeq = ts
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("RexSsh", "Failed to record execution log: ${e.message}", e)
        }
    }

    private fun requestSecurityGate() {
        // The app gate may still read as open while the hardware keystore
        // auth window has expired underneath it; close it so SecurityGate
        // shows the credential prompt instead of short-circuiting
        securityManager.closeGate()
        _uiState.value = _uiState.value.copy(
            showSecurityGate = true,
            isRunning = false
        )
    }

    fun onSecurityGateAuthenticated() {
        val mappingId = _uiState.value.activeMappingId
        _uiState.value = _uiState.value.copy(showSecurityGate = false)
        if (mappingId != null) {
            startSession(mappingId)
        }
    }

    fun onSecurityGateCancelled() {
        pendingSudoPassword = null
        pendingSudoRemember = false
        _uiState.value = _uiState.value.copy(
            showSecurityGate = false,
            activeMappingId = null
        )
    }

    fun submitSudoPassword(password: String, remember: Boolean) {
        val mappingId = _uiState.value.activeMappingId ?: return
        pendingSudoPassword = password
        pendingSudoRemember = remember
        _uiState.value = _uiState.value.copy(sudoPasswordPrompt = null)
        startSession(mappingId)
    }

    fun dismissSudoPrompt() {
        pendingSudoPassword = null
        pendingSudoRemember = false
        _uiState.value = _uiState.value.copy(
            sudoPasswordPrompt = null,
            activeMappingId = null
        )
    }

    fun startSession(mappingId: String) {
        // Cancel any in-flight execution job to prevent double-tap races
        executionJob?.cancel()

        val sessionStartMs = System.currentTimeMillis()
        viewModelScope.launch(GlobalCEH.handler) {
            try {
                // Fetch the mapping
                val mapping = hostCommandRepository.getHostCommandMapping(mappingId)
                if (mapping == null) {
                    val allowCopy = settingsStore.allowCopyOutput.first()
                    emitFinalState(
                        displayOutput = "",
                        exitCode = null,
                        allowCopy = allowCopy,
                        errorMessage = "Command mapping not found. The command may have been deleted."
                    )
                    _uiState.value = _uiState.value.copy(activeMappingId = mappingId) // Keep mapping ID so error shows inline
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
                    try {
                        gatekeeper.requireGateForDangerousCommand()
                    } catch (e: SecurityGateRequiredException) {
                        requestSecurityGate()
                        return@launch
                    }
                }

                // Resolve the sudo password before connecting: stored blob,
                // freshly entered password, or prompt the user
                var sudoPassword: String? = null
                if (mapping.runWithSudo) {
                    sudoPassword = pendingSudoPassword
                    if (sudoPassword == null) {
                        val blobId = hostsRepository.getHostById(mapping.id)?.sudoPasswordBlobId
                        if (blobId != null) {
                            sudoPassword = try {
                                String(keyVault.decryptSecret(KeyBlobId(blobId)), Charsets.UTF_8)
                            } catch (e: SecurityGateRequiredException) {
                                requestSecurityGate()
                                return@launch
                            }
                        }
                    }
                    if (sudoPassword == null) {
                        _uiState.value = _uiState.value.copy(
                            sudoPasswordPrompt = SudoPasswordPrompt(
                                hostNickname = mapping.nickname,
                                username = mapping.username,
                                commandName = mapping.name
                            ),
                            isRunning = false
                        )
                        return@launch
                    }
                    if (pendingSudoPassword != null && pendingSudoRemember) {
                        try {
                            val blobId = keyVault.storeSecret(sudoPassword.toByteArray(Charsets.UTF_8))
                            hostsRepository.setSudoPasswordBlobId(mapping.id, blobId.id)
                            pendingSudoRemember = false
                        } catch (e: SecurityGateRequiredException) {
                            // Keep the pending password; the gate retry re-enters here
                            requestSecurityGate()
                            return@launch
                        }
                    }
                    pendingSudoPassword = null
                }

                // Build expected host pin if we have a fingerprint
                val expectedPin = if (!mapping.pinnedHostKeyFingerprint.isNullOrBlank()) {
                    HostPin("ssh-rsa", mapping.pinnedHostKeyFingerprint)
                } else null

                // Connect to host; first contact aborts with a TOFU prompt the
                // user must confirm before the session is retried
                try {
                    sshClient.connect(
                        host = mapping.hostname,
                        port = mapping.port,
                        timeoutsMs = Pair(mapping.connectTimeoutMs, mapping.readTimeoutMs),
                        expectedPin = expectedPin
                    )
                } catch (e: TofuRequiredException) {
                    _uiState.value = _uiState.value.copy(
                        tofuPrompt = TofuPrompt(mapping.hostname, mapping.port, e.hostPin)
                    )
                    return@launch
                }

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
                        } catch (e: SecurityGateRequiredException) {
                            requestSecurityGate()
                            return@launch
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

                // Execute the command and wait for completion before closing the client.
                // Sudo: -S reads the password from stdin, -k forces sudo to always
                // consume it (so it can't fall through to the command on NOPASSWD
                // hosts), -p '' suppresses the prompt text.
                val execCommand: String
                val execStdin: ByteArray?
                if (mapping.runWithSudo) {
                    execCommand = "sudo -S -k -p '' ${mapping.command}"
                    execStdin = "$sudoPassword\n".toByteArray(Charsets.UTF_8)
                } else {
                    execCommand = mapping.command
                    execStdin = null
                }
                executeCommandWithTimeout(
                    command = execCommand,
                    pty = mapping.allowPty,
                    timeoutMs = mapping.defaultTimeoutMs,
                    stdin = execStdin,
                    sudo = mapping.runWithSudo
                )
                executionJob?.join()

            } catch (e: Exception) {
                val allowCopy = settingsStore.allowCopyOutput.first()
                val displayOutput = Redactor.redact(buildDisplayOutput())
                val errorMessage = "Session failed: ${e.message}"
                emitFinalState(
                    displayOutput = displayOutput,
                    exitCode = null,
                    allowCopy = allowCopy,
                    errorMessage = errorMessage
                )
                recordExecutionLog(
                    exitCode = null,
                    bytesStdout = 0L,
                    durationMs = System.currentTimeMillis() - sessionStartMs,
                    errorMessage = errorMessage
                )
                rawOutput.clear()
            } finally {
                sshClient.close()
                stopTimer()
            }
        }
    }

    private fun executeCommandWithTimeout(
        command: String,
        pty: Boolean = false,
        timeoutMs: Int,
        stdin: ByteArray? = null,
        sudo: Boolean = false
    ) {
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
        streamedBytes = 0L
        executionStartMs = System.currentTimeMillis()
        startTimer()

        executionJob = viewModelScope.launch(GlobalCEH.handler) {
            try {
                // Stream command output
                sshClient.exec(command, pty, stdin).collect { byteString ->
                    streamedBytes += byteString.size
                    val text = byteString.utf8()
                    rawOutput.append(text)
                }

                // Wait for exit code with timeout from mapping or default
                val actualTimeout = if (timeoutMs > 0) timeoutMs else 30000
                val exitCode = sshClient.waitExitCode(actualTimeout)

                val displayOutput = Redactor.redact(buildDisplayOutput())
                val allowCopy = try {
                    settingsStore.allowCopyOutput.first()
                } catch (e: Exception) {
                    android.util.Log.e("RexSsh", "Failed to get allowCopyOutput setting: ${e.message}", e)
                    false // Default to false if settings access fails
                }

                // sudo's own failures land on stderr (not captured); surface a
                // hint when the signature matches a rejected password
                val sudoHint = if (sudo && exitCode == 1 && displayOutput.isBlank()) {
                    "sudo authentication failed — check the stored sudo password in the host's key screen"
                } else null

                emitFinalState(
                    displayOutput = displayOutput,
                    exitCode = exitCode,
                    allowCopy = allowCopy,
                    errorMessage = sudoHint
                )
                recordExecutionLog(
                    exitCode = exitCode,
                    bytesStdout = streamedBytes,
                    durationMs = System.currentTimeMillis() - executionStartMs,
                    errorMessage = sudoHint
                )
                rawOutput.clear()

            } catch (e: Exception) {
                android.util.Log.e("RexSsh", "Exception in executeCommandWithTimeout: ${e.javaClass.simpleName}: ${e.message}", e)

                val displayOutput = try {
                    Redactor.redact(buildDisplayOutput())
                } catch (redactorException: Exception) {
                    android.util.Log.e("RexSsh", "Redactor failed in error handler: ${redactorException.message}", redactorException)
                    buildDisplayOutput() // Use raw display if redactor fails
                }

                val allowCopy = try {
                    settingsStore.allowCopyOutput.first()
                } catch (settingsException: Exception) {
                    android.util.Log.e("RexSsh", "Settings access failed in error handler: ${settingsException.message}", settingsException)
                    false
                }

                val errorMessage = "Execution failed: ${e.message}"
                emitFinalState(
                    displayOutput = displayOutput,
                    exitCode = null,
                    allowCopy = allowCopy,
                    errorMessage = errorMessage
                )
                recordExecutionLog(
                    exitCode = null,
                    bytesStdout = streamedBytes,
                    durationMs = System.currentTimeMillis() - executionStartMs,
                    errorMessage = errorMessage
                )
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

    fun confirmTofuTrust() {
        val prompt = _uiState.value.tofuPrompt ?: return
        val mappingId = _uiState.value.activeMappingId ?: return
        viewModelScope.launch(GlobalCEH.handler) {
            val mapping = hostCommandRepository.getHostCommandMapping(mappingId)
            if (mapping == null) {
                _uiState.value = _uiState.value.copy(
                    tofuPrompt = null,
                    error = "Command mapping not found. The command may have been deleted."
                )
                return@launch
            }
            hostsRepository.updateHostFingerprint(mapping.id, prompt.pin.sha256)
            _uiState.value = _uiState.value.copy(tofuPrompt = null)
            startSession(mappingId)
        }
    }

    fun dismissTofuPrompt() {
        _uiState.value = _uiState.value.copy(
            tofuPrompt = null,
            activeMappingId = null
        )
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
        pendingSudoPassword = null
        pendingSudoRemember = false

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
