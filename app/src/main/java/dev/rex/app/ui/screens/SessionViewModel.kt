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
import dev.rex.app.core.Redactor
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
    val error: String? = null
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sshClient: SshClient,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var executionJob: Job? = null
    private var timerJob: Job? = null
    private var clipboardClearJob: Job? = null
    private val rawOutput = StringBuilder()

    fun executeCommand(command: String, pty: Boolean = false) {
        if (_uiState.value.isRunning) return

        _uiState.value = _uiState.value.copy(
            isRunning = true,
            output = "",
            exitCode = null,
            elapsedTimeMs = 0,
            error = null
        )

        rawOutput.clear()
        startTimer()

        executionJob = viewModelScope.launch {
            try {
                sshClient.exec(command, pty).collect { byteString ->
                    val text = byteString.utf8()
                    rawOutput.append(text)
                    
                    // Apply redaction before displaying
                    val redactedOutput = Redactor.redact(rawOutput.toString())
                    
                    _uiState.value = _uiState.value.copy(
                        output = redactedOutput
                    )
                }

                // Wait for exit code
                val exitCode = sshClient.waitExitCode(30000) // 30 second timeout
                
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    exitCode = exitCode,
                    canCopy = true // Allow copying after completion
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    error = "Execution failed: ${e.message}",
                    canCopy = false
                )
            } finally {
                stopTimer()
            }
        }
    }

    fun cancelExecution() {
        executionJob?.cancel()
        viewModelScope.launch {
            try {
                sshClient.cancel()
            } catch (e: Exception) {
                // Ignore cancel errors
            }
            _uiState.value = _uiState.value.copy(
                isRunning = false,
                canCopy = true
            )
            stopTimer()
        }
    }

    fun copyOutput() {
        val output = _uiState.value.output
        if (output.isBlank()) return

        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("Rex Output", output)
        clipboardManager.setPrimaryClip(clipData)

        // Auto-clear clipboard after 60 seconds per SPEC requirements
        clipboardClearJob?.cancel()
        clipboardClearJob = viewModelScope.launch {
            delay(60_000) // 60 seconds
            try {
                val emptyClip = ClipData.newPlainText("", "")
                clipboardManager.setPrimaryClip(emptyClip)
            } catch (e: Exception) {
                // Ignore clipboard clear errors
            }
        }
    }

    private fun startTimer() {
        val startTime = System.currentTimeMillis()
        timerJob = viewModelScope.launch {
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
        
        // Clean up SSH client
        viewModelScope.launch {
            try {
                sshClient.close()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}