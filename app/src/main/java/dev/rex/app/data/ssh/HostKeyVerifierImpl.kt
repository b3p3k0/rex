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

import android.util.Base64
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostKeyVerifierImpl @Inject constructor() : HostKeyVerifier {
    
    override fun computeFingerprint(pubKey: ByteArray): HostPin {
        // Extract algorithm and key data based on OpenSSH public key format
        val (algorithm, keyData) = parseOpenSshPublicKey(pubKey)
        
        // Compute SHA256 hash
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(keyData)
        
        // Encode as base64 (no padding) for OpenSSH format with SHA256: prefix
        val fingerprint = "SHA256:" + Base64.encodeToString(hash, Base64.NO_PADDING or Base64.NO_WRAP)
        
        return HostPin(algorithm, fingerprint)
    }
    
    override fun verifyPinned(expected: HostPin, actual: HostPin): Boolean {
        return expected.alg == actual.alg && expected.sha256 == actual.sha256
    }
    
    private fun parseOpenSshPublicKey(pubKey: ByteArray): Pair<String, ByteArray> {
        // This is a simplified parser for OpenSSH public key format
        // Real implementation would properly parse the SSH wire format
        
        val keyString = String(pubKey).trim()
        val parts = keyString.split(" ")
        
        if (parts.size < 2) {
            throw IllegalArgumentException("Invalid public key format")
        }
        
        val algorithm = parts[0]
        val keyData = try {
            Base64.decode(parts[1], Base64.DEFAULT)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid base64 encoding in public key", e)
        }
        
        return algorithm to keyData
    }
}