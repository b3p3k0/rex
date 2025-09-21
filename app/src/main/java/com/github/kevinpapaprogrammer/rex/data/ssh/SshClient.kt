/*
 * Rex — Remote Exec for Android
 * Copyright (C) 2024 Kevin Papa
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

package com.github.kevinpapaprogrammer.rex.data.ssh

import kotlinx.coroutines.flow.Flow
import okio.ByteString

data class HostPin(val alg: String, val sha256: String)

interface HostKeyVerifier {
    fun computeFingerprint(pubKey: ByteArray): HostPin
    fun verifyPinned(expected: HostPin, actual: HostPin): Boolean
}

interface SshClient : AutoCloseable {
    suspend fun connect(
        host: String,
        port: Int,
        timeoutsMs: Pair<Int, Int>,
        expectedPin: HostPin?
    ): HostPin
    
    suspend fun authUsernameKey(username: String, privateKeyPem: ByteArray)
    
    fun exec(command: String, pty: Boolean = false): Flow<ByteString>
    
    suspend fun waitExitCode(timeoutMs: Int?): Int
    
    suspend fun cancel()
}