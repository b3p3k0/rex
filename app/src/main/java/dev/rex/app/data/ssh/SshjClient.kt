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

import android.util.Log
import dev.rex.app.core.ErrorMapper
import dev.rex.app.data.repo.HostsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import dev.rex.app.di.IoDispatcher
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.userauth.UserAuthException
import okio.ByteString
import okio.ByteString.Companion.toByteString
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import java.security.KeyPair
import java.security.PublicKey
import java.security.Security
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SshjClient @Inject constructor(
    private val hostKeyVerifier: HostKeyVerifier,
    private val hostsRepository: HostsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SshClient {
    
    private var sshClient: SSHClient? = null
    private var currentSession: Session? = null
    private var currentCommand: Session.Command? = null
    private var connectedHost: String? = null

    private fun cleanupSession() {
        try {
            currentCommand?.close()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        try {
            currentSession?.close()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        currentCommand = null
        currentSession = null
    }
    
    override suspend fun connect(
        host: String,
        port: Int,
        timeoutsMs: Pair<Int, Int>,
        expectedPin: HostPin?
    ): HostPin = withContext(ioDispatcher) {
        // TODO(claude): remove once SSH provisioning is stable
        val eddsaProvider = Security.getProvider("EdDSA")
        Log.d("RexSsh", "Starting connect to $host:$port, timeouts=${timeoutsMs}, EdDSA provider present: ${eddsaProvider != null}")

        checkNotNull(Security.getProvider(EdDSASecurityProvider.PROVIDER_NAME)) {
            "EdDSA security provider not registered"
        }

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
            connectedHost = host

            // SECURITY: Extract the actual server host key from the captured key
            val actualPin = capturedHostKey?.let { key ->
                hostKeyVerifier.computeFingerprint(key.encoded)
            } ?: throw RuntimeException("Failed to capture server host key during connection")

            // TODO(claude): remove once SSH provisioning is stable
            Log.d("RexSsh", "Captured host key: algorithm=${actualPin.alg}, fingerprint=${actualPin.sha256}")

            // SECURITY: If no expected pin, log for future TOFU UI but allow connection
            if (expectedPin == null) {
                // TODO: Surface actualPin to user for verification in future TOFU UI implementation
                // For now, allow connection to proceed for password-based key deployment
                println("Rex TOFU: First connection to $host:$port, fingerprint: ${actualPin.alg} ${actualPin.sha256}")
            }

            actualPin

        } catch (e: TofuRequiredException) {
            // Re-throw TOFU exceptions to be handled by UI layer
            throw e
        } catch (e: HostKeyMismatchException) {
            // Re-throw host key mismatch exceptions
            throw e
        } catch (e: Exception) {
            // TODO(claude): remove once SSH provisioning is stable
            Log.d("RexSsh", "Connection failed with exception: ${e::class.qualifiedName}: ${e.message}")
            val (error, message) = ErrorMapper.mapSshException(e)
            throw RuntimeException("SSH connection failed: $message", e)
        }
    }
    
    override suspend fun authUsernameKey(username: String, privateKeyPem: ByteArray) {
        withContext(ioDispatcher) {
        val client = sshClient ?: throw IllegalStateException("Not connected")

        try {
            val pem = String(privateKeyPem, Charsets.UTF_8)
            val pkcs8Bytes = decodePkcs8Pem(pem)

            val privateKeyInfo = PrivateKeyInfo.getInstance(pkcs8Bytes)
            val algorithmOid = privateKeyInfo.privateKeyAlgorithm.algorithm.id
            if (algorithmOid != "1.3.101.112") {
                throw RuntimeException("Unsupported private key algorithm: $algorithmOid")
            }

            val privateKeyOctets = ASN1OctetString.getInstance(privateKeyInfo.parsePrivateKey()).octets
            if (privateKeyOctets.size != 32) {
                throw RuntimeException("Invalid Ed25519 seed length: ${privateKeyOctets.size}")
            }

            val edParams = EdDSANamedCurveTable.getByName("Ed25519")
                ?: throw RuntimeException("Ed25519 curve parameters not found")

            val privateSpec = EdDSAPrivateKeySpec(privateKeyOctets, edParams)
            val publicSpec = EdDSAPublicKeySpec(privateSpec.a, edParams)

            val edPrivateKey = EdDSAPrivateKey(privateSpec)
            val edPublicKey = EdDSAPublicKey(publicSpec)

            val keyPair = KeyPair(edPublicKey, edPrivateKey)
            val keyProvider = client.loadKeys(keyPair)

            Log.i("RexSsh", "Attempting authPublickey for $username@$connectedHost")
            client.authPublickey(username, keyProvider)
            Log.i("RexSsh", "authPublickey succeeded for $username@$connectedHost")

        } catch (e: UserAuthException) {
            Log.e("RexSsh", "authPublickey rejected by server for $username@$connectedHost", e)
            throw RuntimeException("SSH key authentication failed: Invalid credentials", e)
        } catch (e: Exception) {
            Log.e("RexSsh", "authUsernameKey failed for $username@$connectedHost", e)
            val (error, message) = ErrorMapper.mapSshException(e)
            throw RuntimeException("SSH authentication failed: $message", e)
        } finally {
            // SECURITY: Zero the original byte array
            privateKeyPem.fill(0)
        }
        }
    }

    override suspend fun authUsernamePassword(username: String, password: String) {
        withContext(ioDispatcher) {
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
                Log.d("RexSsh", "exec stream read: bytes=$bytesRead")
                if (bytesRead > 0) {
                    val chunk = buffer.copyOfRange(0, bytesRead).toByteString()
                    val printableChunk = chunk.utf8().replace("\n", "\\n")
                    Log.d("RexSsh", "exec chunk bytes=[$printableChunk]")
                    emit(chunk)
                }
            }

            Log.d("RexSsh", "exec stream completed for $connectedHost")
            Log.d("RexSsh", "exec: leaving session active for waitExitCode() call")

        } catch (e: Exception) {
            // Clean up session on any failure
            cleanupSession()
            val (error, message) = ErrorMapper.mapSshException(e)
            throw RuntimeException("Command execution failed: $message", e)
        }
    }.flowOn(ioDispatcher)
    
    override suspend fun waitExitCode(timeoutMs: Int?): Int {
        val session = currentSession ?: throw IllegalStateException("No active session")
        val command = currentCommand ?: throw IllegalStateException("No active command")

        return try {
            if (timeoutMs != null) {
                // Enforce timeout behavior: throw on timeout, never hang
                session.join(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            } else {
                session.join()
            }

            // Get the actual exit status from the command
            // Return -1 if null as documented
            val exitStatus = command.exitStatus ?: -1
            Log.d("RexSsh", "waitExitCode completed for $connectedHost with status=$exitStatus")
            exitStatus

        } catch (e: Exception) {
            val (error, message) = ErrorMapper.mapSshException(e)
            throw RuntimeException("Command timeout or execution failed: $message", e)
        } finally {
            Log.d("RexSsh", "waitExitCode cleanup: cleaning up session and command")
            cleanupSession()
            Log.d("RexSsh", "waitExitCode cleanup: session cleanup completed")
        }
    }

    private fun decodePkcs8Pem(pem: String): ByteArray {
        val sanitized = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        return Base64.getDecoder().decode(sanitized)
    }

    override suspend fun cancel() {
        try {
            cleanupSession()
        } catch (e: Exception) {
            // Log but don't throw on cleanup - cancellation should always succeed
        }
    }
    
    override fun close() {
        cleanupSession()

        try {
            sshClient?.disconnect()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }

        sshClient = null
        connectedHost = null
    }
}
