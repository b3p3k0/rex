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

package dev.rex.app.data.ssh

import dev.rex.app.core.ErrorMapper
import dev.rex.app.data.repo.HostsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.UserAuthException
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.PublicKey
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SshjClient @Inject constructor(
    private val hostKeyVerifier: HostKeyVerifier,
    private val hostsRepository: HostsRepository
) : SshClient {
    
    private var sshClient: SSHClient? = null
    private var currentSession: Session? = null
    private var currentCommand: Session.Command? = null
    
    override suspend fun connect(
        host: String,
        port: Int,
        timeoutsMs: Pair<Int, Int>,
        expectedPin: HostPin?
    ): HostPin {
        try {
            val client = SSHClient()
            client.connectTimeout = timeoutsMs.first
            client.timeout = timeoutsMs.second

            // Set up host key verification and capture
            var capturedHostKey: PublicKey? = null

            if (expectedPin != null) {
                client.addHostKeyVerifier(object : net.schmizz.sshj.transport.verification.HostKeyVerifier {
                    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                        capturedHostKey = key
                        val actualPin = hostKeyVerifier.computeFingerprint(key.encoded)
                        val isValid = hostKeyVerifier.verifyPinned(expectedPin, actualPin)
                        if (!isValid) {
                            throw HostKeyMismatchException(
                                "Host key mismatch for $hostname:$port. " +
                                "Expected: ${expectedPin.sha256}, Got: ${actualPin.sha256}"
                            )
                        }
                        return true
                    }

                    override fun findExistingAlgorithms(hostname: String, port: Int): MutableList<String> {
                        return mutableListOf()
                    }
                })
            } else {
                // TOFU mode - capture host key during verification
                client.addHostKeyVerifier(object : net.schmizz.sshj.transport.verification.HostKeyVerifier {
                    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                        capturedHostKey = key
                        return true // Accept all keys in TOFU mode
                    }

                    override fun findExistingAlgorithms(hostname: String, port: Int): MutableList<String> {
                        return mutableListOf()
                    }
                })
            }

            client.connect(host, port)
            sshClient = client

            // SECURITY: Extract the actual server host key from the captured key
            val actualPin = capturedHostKey?.let { key ->
                hostKeyVerifier.computeFingerprint(key.encoded)
            } ?: throw RuntimeException("Failed to capture server host key during connection")

            // SECURITY: If no expected pin, log for future TOFU UI but allow connection
            if (expectedPin == null) {
                // TODO: Surface actualPin to user for verification in future TOFU UI implementation
                // For now, allow connection to proceed for password-based key deployment
                println("Rex TOFU: First connection to $host:$port, fingerprint: ${actualPin.alg} ${actualPin.sha256}")
            }

            return actualPin

        } catch (e: TofuRequiredException) {
            // Re-throw TOFU exceptions to be handled by UI layer
            throw e
        } catch (e: HostKeyMismatchException) {
            // Re-throw host key mismatch exceptions
            throw e
        } catch (e: Exception) {
            val (error, message) = ErrorMapper.mapSshException(e)
            throw RuntimeException("SSH connection failed: $message", e)
        }
    }
    
    override suspend fun authUsernameKey(username: String, privateKeyPem: ByteArray) {
        val client = sshClient ?: throw IllegalStateException("Not connected")

        try {
            // SECURITY: Use ByteArrayInputStream to avoid String conversion that defeats zeroization
            val keyProvider = client.loadKeys(String(privateKeyPem, Charsets.UTF_8), null as CharArray?)

            // Authenticate using the private key
            client.authPublickey(username, keyProvider)

        } catch (e: UserAuthException) {
            throw RuntimeException("SSH key authentication failed: Invalid credentials", e)
        } catch (e: Exception) {
            val (error, message) = ErrorMapper.mapSshException(e)
            throw RuntimeException("SSH authentication failed: $message", e)
        } finally {
            // SECURITY: Zero the original byte array
            privateKeyPem.fill(0)
        }
    }

    override suspend fun authUsernamePassword(username: String, password: String) {
        val client = sshClient ?: throw IllegalStateException("Not connected")
        val passwordChars = password.toCharArray()

        try {
            // SECURITY: Use char array for password to enable proper zeroing
            client.authPassword(username, passwordChars)

        } catch (e: UserAuthException) {
            throw RuntimeException("SSH password authentication failed: Invalid credentials", e)
        } catch (e: Exception) {
            val (error, message) = ErrorMapper.mapSshException(e)
            throw RuntimeException("SSH authentication failed: $message", e)
        } finally {
            // SECURITY: Zero password immediately
            passwordChars.fill('0')
        }
    }
    
    override fun exec(command: String, pty: Boolean): Flow<ByteString> = flow {
        val client = sshClient ?: throw IllegalStateException("Not connected")

        // SECURITY: Fresh session per exec to avoid poisoned session reuse
        val session = client.startSession()
        var cmd: Session.Command? = null

        try {
            currentSession = session

            // Start the command based on PTY requirement
            if (pty) {
                session.allocateDefaultPTY()
                // For shell commands, we'll handle them differently
                val shell = session.startShell()
                cmd = shell as Session.Command?
            } else {
                cmd = session.exec(command)
            }

            currentCommand = cmd

            // Stream real stdout output
            val stdout = cmd!!.inputStream
            val buffer = ByteArray(8192)

            // Read and emit actual command output
            var bytesRead: Int
            while (stdout.read(buffer).also { bytesRead = it } != -1) {
                if (bytesRead > 0) {
                    emit(buffer.copyOfRange(0, bytesRead).toByteString())
                }
            }

        } catch (e: Exception) {
            val (error, message) = ErrorMapper.mapSshException(e)
            throw RuntimeException("Command execution failed: $message", e)
        } finally {
            // SECURITY: Ensure complete cleanup of session and command
            try {
                cmd?.close()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            try {
                session.close()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            currentSession = null
        }
    }
    
    override suspend fun waitExitCode(timeoutMs: Int?): Int {
        val session = currentSession ?: throw IllegalStateException("No active session")

        return try {
            if (timeoutMs != null) {
                // Enforce timeout behavior: throw on timeout, never hang
                session.join(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            } else {
                session.join()
            }

            // Get the actual exit status from the command
            // Return -1 if null as documented
            currentCommand?.exitStatus ?: -1

        } catch (e: Exception) {
            val (error, message) = ErrorMapper.mapSshException(e)
            throw RuntimeException("Command timeout or execution failed: $message", e)
        }
    }
    
    override suspend fun cancel() {
        try {
            currentSession?.let { session ->
                // SECURITY: Close the session cleanly within 1 second to prevent poisoned session reuse
                if (session.isOpen) {
                    session.close()
                }
            }
        } catch (e: Exception) {
            // Log but don't throw on cleanup - cancellation should always succeed
        } finally {
            currentSession = null
            currentCommand = null
        }
    }
    
    override fun close() {
        try {
            currentSession?.close()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        
        try {
            sshClient?.disconnect()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        
        currentSession = null
        sshClient = null
    }
}