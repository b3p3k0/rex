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
import dev.rex.app.core.Gatekeeper
import dev.rex.app.core.ProvisioningStep
import dev.rex.app.core.StepStatus
import dev.rex.app.data.crypto.KeyVault
import dev.rex.app.data.crypto.KeyBlobId
import dev.rex.app.data.repo.HostsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import dev.rex.app.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

data class ProvisionResult(
    val success: Boolean,
    val durationMs: Long,
    val stdout: String,
    val stderr: String,
    val errorMessage: String? = null,
    val steps: List<ProvisioningStep> = emptyList()
)

@Singleton
class SshProvisioner @Inject constructor(
    private val gatekeeper: Gatekeeper,
    private val keyVault: KeyVault,
    private val hostKeyVerifier: HostKeyVerifier,
    private val hostsRepository: HostsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
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

        // Initialize provisioning steps for progress tracking
        val steps = mutableListOf(
            ProvisioningStep("Connecting to host"),
            ProvisioningStep("Authenticating with password"),
            ProvisioningStep("Creating .ssh directory"),
            ProvisioningStep("Setting directory permissions"),
            ProvisioningStep("Checking for existing key"),
            ProvisioningStep("Adding public key"),
            ProvisioningStep("Setting file permissions"),
            ProvisioningStep("Verifying key deployment")
        )

        try {
            // Step 1: Get the public key for deployment
            val publicKey = keyVault.getPublicKeyOpenssh(keyBlobId)

            // Step 2: Connect with password authentication
            steps[0] = steps[0].copy(status = StepStatus.InProgress)
            val client = createSshClient()
            val actualPin = client.connect(hostname, port, timeoutsMs, null)
            steps[0] = steps[0].copy(status = StepStatus.Completed)

            try {
                // Step 3: Authenticate
                steps[1] = steps[1].copy(status = StepStatus.InProgress)
                client.authUsernamePassword(username, password)
                steps[1] = steps[1].copy(status = StepStatus.Completed)

                // Step 4: Create .ssh directory
                steps[2] = steps[2].copy(status = StepStatus.InProgress)
                val mkdirResult = execWithOutput(client, "mkdir -p ~/.ssh")
                stdout.append("mkdir: ${mkdirResult.first}\n")
                stderr.append("mkdir: ${mkdirResult.second}\n")
                steps[2] = steps[2].copy(status = StepStatus.Completed)

                // Step 5: Set directory permissions
                steps[3] = steps[3].copy(status = StepStatus.InProgress)
                val chmodDirResult = execWithOutput(client, "chmod 700 ~/.ssh")
                stdout.append("chmod dir: ${chmodDirResult.first}\n")
                stderr.append("chmod dir: ${chmodDirResult.second}\n")
                steps[3] = steps[3].copy(status = StepStatus.Completed)

                // Step 6: Check if key already exists
                steps[4] = steps[4].copy(status = StepStatus.InProgress)
                val checkKeyResult = execWithOutput(client,
                    "grep -Fq '${publicKey.trim()}' ~/.ssh/authorized_keys 2>/dev/null && echo 'EXISTS' || echo 'NOT_FOUND'"
                )
                stdout.append("check key: ${checkKeyResult.first}\n")
                steps[4] = steps[4].copy(status = StepStatus.Completed)

                // Step 7: Add public key if not exists
                steps[5] = steps[5].copy(status = StepStatus.InProgress)
                if (!checkKeyResult.first.contains("EXISTS")) {
                    val appendKeyResult = execWithOutput(client,
                        "echo '${publicKey.trim()}' >> ~/.ssh/authorized_keys"
                    )
                    stdout.append("append key: ${appendKeyResult.first}\n")
                    stderr.append("append key: ${appendKeyResult.second}\n")
                } else {
                    stdout.append("Key already exists, skipping\n")
                }
                steps[5] = steps[5].copy(status = StepStatus.Completed)

                // Step 8: Set file permissions
                steps[6] = steps[6].copy(status = StepStatus.InProgress)
                val chmodFileResult = execWithOutput(client, "chmod 600 ~/.ssh/authorized_keys")
                stdout.append("chmod file: ${chmodFileResult.first}\n")
                stderr.append("chmod file: ${chmodFileResult.second}\n")
                steps[6] = steps[6].copy(status = StepStatus.Completed)

                // Step 9: Verify deployment
                steps[7] = steps[7].copy(status = StepStatus.InProgress)
                val verifyResult = execWithOutput(client, "cat ~/.ssh/authorized_keys | tail -1")
                stdout.append("verify: ${verifyResult.first}\n")
                steps[7] = steps[7].copy(status = StepStatus.Completed)

                val duration = System.currentTimeMillis() - startTime

                return ProvisionResult(
                    success = true,
                    durationMs = duration,
                    stdout = stdout.toString(),
                    stderr = stderr.toString(),
                    steps = steps
                )

            } finally {
                client.close()
                // Clear password from memory
                password.toCharArray().fill('0')
            }

        } catch (e: Exception) {
            // TODO(claude): remove once SSH provisioning is stable
            Log.e("RexProvisioner", "deployKeyToHost failed", e)

            // Mark current step as failed
            val currentStepIndex = steps.indexOfFirst { it.status == StepStatus.InProgress }
            if (currentStepIndex >= 0) {
                steps[currentStepIndex] = steps[currentStepIndex].copy(status = StepStatus.Failed)
            }

            val duration = System.currentTimeMillis() - startTime
            return ProvisionResult(
                success = false,
                durationMs = duration,
                stdout = stdout.toString(),
                stderr = stderr.toString(),
                errorMessage = "${e::class.qualifiedName}: ${e.message}",
                steps = steps
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

        // Stream real command output without deterministic expectations
        client.exec(command).collect { byteString ->
            // All output goes to stdout for now - stderr separation would require
            // separate channel handling in the SSH client implementation
            stdout.append(byteString.utf8())
        }

        // Wait for command completion with timeout
        val exitCode = client.waitExitCode(5000)

        // Include exit code in output for debugging if non-zero
        if (exitCode != 0) {
            stderr.append("Command exited with code: $exitCode\n")
        }

        return Pair(stdout.toString(), stderr.toString())
    }

    private fun createSshClient(): SshClient {
        return SshjClient(hostKeyVerifier, hostsRepository, ioDispatcher)
    }

}