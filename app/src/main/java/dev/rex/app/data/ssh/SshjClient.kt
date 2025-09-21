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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SshjClient @Inject constructor(
    private val hostKeyVerifier: HostKeyVerifier
) : SshClient {
    
    private var sshClient: SSHClient? = null
    private var currentSession: Session? = null
    
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
            
            // Set up host key verification
            if (expectedPin != null) {
                client.addHostKeyVerifier { hostname, port, key ->
                    val actualPin = hostKeyVerifier.computeFingerprint(key.encoded)
                    hostKeyVerifier.verifyPinned(expectedPin, actualPin)
                }
            } else {
                // TOFU mode - accept all host keys for first connection
                client.addHostKeyVerifier { _, _, _ -> true }
            }
            
            client.connect(host, port)
            sshClient = client
            
            // Return the actual host key fingerprint for pinning
            val hostKey = client.remoteHostKey
            return hostKeyVerifier.computeFingerprint(hostKey.encoded)
            
        } catch (e: Exception) {
            val (error, message) = ErrorMapper.mapException(e)
            throw RuntimeException("SSH connection failed: $message", e)
        }
    }
    
    override suspend fun authUsernameKey(username: String, privateKeyPem: ByteArray) {
        val client = sshClient ?: throw IllegalStateException("Not connected")
        
        try {
            // Parse private key and authenticate
            // This is a placeholder - real implementation would parse PEM properly
            client.authPassword(username, "placeholder") // Temporary
        } catch (e: Exception) {
            val (error, message) = ErrorMapper.mapException(e)
            throw RuntimeException("SSH authentication failed: $message", e)
        }
    }
    
    override fun exec(command: String, pty: Boolean): Flow<ByteString> = flow {
        val client = sshClient ?: throw IllegalStateException("Not connected")
        
        try {
            val session = client.startSession()
            currentSession = session
            
            val cmd = session.exec(command)
            
            // Stream stdout and stderr
            val stdout = cmd.inputStream
            val stderr = cmd.errorStream
            
            // Simple implementation - would need proper streaming in production
            val buffer = ByteArray(1024)
            var bytesRead: Int
            
            while (stdout.read(buffer).also { bytesRead = it } != -1) {
                emit(buffer.copyOfRange(0, bytesRead).toByteString())
            }
            
        } catch (e: Exception) {
            val (error, message) = ErrorMapper.mapException(e)
            throw RuntimeException("Command execution failed: $message", e)
        }
    }
    
    override suspend fun waitExitCode(timeoutMs: Int?): Int {
        val session = currentSession ?: throw IllegalStateException("No active session")
        
        return try {
            if (timeoutMs != null) {
                session.join(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            } else {
                session.join()
            }
            session.exitStatus ?: -1
        } catch (e: Exception) {
            -1
        }
    }
    
    override suspend fun cancel() {
        currentSession?.close()
        currentSession = null
    }
    
    override fun close() {
        currentSession?.close()
        sshClient?.close()
        currentSession = null
        sshClient = null
    }
}