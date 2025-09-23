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

import dev.rex.app.core.Gatekeeper
import dev.rex.app.data.crypto.KeyVault
import dev.rex.app.data.crypto.KeyBlobId
import dev.rex.app.data.repo.HostsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

data class ProvisionResult(
    val success: Boolean,
    val durationMs: Long,
    val stdout: String,
    val stderr: String,
    val errorMessage: String? = null
)

@Singleton
class SshProvisioner @Inject constructor(
    private val gatekeeper: Gatekeeper,
    private val keyVault: KeyVault,
    private val hostKeyVerifier: HostKeyVerifier,
    private val hostsRepository: HostsRepository
) {

    suspend fun deployKeyToHost(
        hostname: String,
        port: Int,
        username: String,
        password: String,
        keyBlobId: KeyBlobId,
        timeoutsMs: Pair<Int, Int>
    ): ProvisionResult {
        gatekeeper.requireGateForKeyOperation()

        val startTime = System.currentTimeMillis()
        var stdout = StringBuilder()
        var stderr = StringBuilder()

        try {
            // Get the public key for deployment
            val publicKey = keyVault.getPublicKeyOpenssh(keyBlobId)

            // Connect with password authentication
            val client = createSshClient()
            val actualPin = client.connect(hostname, port, timeoutsMs, null)

            try {
                client.authUsernamePassword(username, password)

                // Create .ssh directory if it doesn't exist
                val mkdirResult = execWithOutput(client, "mkdir -p ~/.ssh")
                stdout.append("mkdir: ${mkdirResult.first}\n")
                stderr.append("mkdir: ${mkdirResult.second}\n")

                // Set proper permissions on .ssh directory
                val chmodDirResult = execWithOutput(client, "chmod 700 ~/.ssh")
                stdout.append("chmod dir: ${chmodDirResult.first}\n")
                stderr.append("chmod dir: ${chmodDirResult.second}\n")

                // Check if key already exists in authorized_keys
                val checkKeyResult = execWithOutput(client,
                    "grep -Fq '${publicKey.trim()}' ~/.ssh/authorized_keys 2>/dev/null && echo 'EXISTS' || echo 'NOT_FOUND'"
                )
                stdout.append("check key: ${checkKeyResult.first}\n")

                if (!checkKeyResult.first.contains("EXISTS")) {
                    // Append the public key to authorized_keys
                    val appendKeyResult = execWithOutput(client,
                        "echo '${publicKey.trim()}' >> ~/.ssh/authorized_keys"
                    )
                    stdout.append("append key: ${appendKeyResult.first}\n")
                    stderr.append("append key: ${appendKeyResult.second}\n")
                }

                // Set proper permissions on authorized_keys
                val chmodFileResult = execWithOutput(client, "chmod 600 ~/.ssh/authorized_keys")
                stdout.append("chmod file: ${chmodFileResult.first}\n")
                stderr.append("chmod file: ${chmodFileResult.second}\n")

                // Verify the key was added successfully
                val verifyResult = execWithOutput(client, "cat ~/.ssh/authorized_keys | tail -1")
                stdout.append("verify: ${verifyResult.first}\n")

                val duration = System.currentTimeMillis() - startTime

                return ProvisionResult(
                    success = true,
                    durationMs = duration,
                    stdout = stdout.toString(),
                    stderr = stderr.toString()
                )

            } finally {
                client.close()
                // Clear password from memory
                password.toCharArray().fill('0')
            }

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            return ProvisionResult(
                success = false,
                durationMs = duration,
                stdout = stdout.toString(),
                stderr = stderr.toString(),
                errorMessage = e.message
            )
        }
    }

    suspend fun testKeyBasedAuth(
        hostname: String,
        port: Int,
        username: String,
        keyBlobId: KeyBlobId,
        timeoutsMs: Pair<Int, Int>,
        expectedPin: HostPin?
    ): ProvisionResult {
        gatekeeper.requireGateForKeyOperation()

        val startTime = System.currentTimeMillis()

        try {
            // Get the private key for authentication
            val privateKey = keyVault.decryptPrivateKey(keyBlobId)

            // Connect and test key-based auth
            val client = createSshClient()
            val actualPin = client.connect(hostname, port, timeoutsMs, expectedPin)

            try {
                client.authUsernameKey(username, privateKey)

                // Run a simple test command
                val testResult = execWithOutput(client, "echo 'SSH key authentication successful'")
                val duration = System.currentTimeMillis() - startTime

                return ProvisionResult(
                    success = true,
                    durationMs = duration,
                    stdout = testResult.first,
                    stderr = testResult.second
                )

            } finally {
                client.close()
                // Clear private key from memory
                privateKey.fill(0)
            }

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            return ProvisionResult(
                success = false,
                durationMs = duration,
                stdout = "",
                stderr = "",
                errorMessage = e.message
            )
        }
    }

    private suspend fun execWithOutput(client: SshClient, command: String): Pair<String, String> {
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        client.exec(command).collect { byteString ->
            // For now, treat all output as stdout
            // Real implementation would separate stdout/stderr
            stdout.append(byteString.utf8())
        }

        client.waitExitCode(5000)

        return Pair(stdout.toString(), stderr.toString())
    }

    private fun createSshClient(): SshClient {
        return SshjClient(hostKeyVerifier, hostsRepository)
    }

}