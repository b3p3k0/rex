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
        // Binary wire-format blobs (length-prefixed, from the SSH transport)
        // take priority; text parsing could misfire on binary data.
        if (parseWireAlgorithm(pubKey) != null) {
            return computeFingerprintFromRawKey(pubKey)
        }
        return try {
            // Text form: "ssh-ed25519 <base64-wire-blob> [comment]"
            val (algorithm, keyData) = parseOpenSshPublicKey(pubKey)
            computeFingerprintFromKeyData(algorithm, keyData)
        } catch (e: Exception) {
            computeFingerprintFromRawKey(pubKey)
        }
    }

    private fun computeFingerprintFromKeyData(algorithm: String, keyData: ByteArray): HostPin {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(keyData)
        val fingerprint = "SHA256:" + Base64.encodeToString(hash, Base64.NO_PADDING or Base64.NO_WRAP)
        return HostPin(algorithm, fingerprint)
    }

    private fun computeFingerprintFromRawKey(pubKey: ByteArray): HostPin {
        // SSH wire-format blob: hashing the whole blob yields the same
        // SHA256:… value OpenSSH prints (ssh-keygen -lf)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pubKey)
        val fingerprint = "SHA256:" + Base64.encodeToString(hash, Base64.NO_PADDING or Base64.NO_WRAP)

        val algorithm = parseWireAlgorithm(pubKey) ?: when {
            pubKey.size == 32 -> "ssh-ed25519"
            pubKey.size > 256 -> "ssh-rsa"
            else -> "ssh-ecdsa"
        }

        return HostPin(algorithm, fingerprint)
    }

    /** Reads the leading algorithm name of an SSH wire-format public key blob. */
    private fun parseWireAlgorithm(blob: ByteArray): String? {
        if (blob.size < 8) return null
        val len = ((blob[0].toInt() and 0xff) shl 24) or
            ((blob[1].toInt() and 0xff) shl 16) or
            ((blob[2].toInt() and 0xff) shl 8) or
            (blob[3].toInt() and 0xff)
        if (len < 4 || len > 64 || blob.size < 4 + len) return null
        val alg = String(blob, 4, len, Charsets.US_ASCII)
        return if (alg.all { it.code in 33..126 }) alg else null
    }
    
    override fun verifyPinned(expected: HostPin, actual: HostPin): Boolean {
        // SECURITY: only the SHA-256 fingerprint is compared. It hashes the full
        // encoded key, which commits to the algorithm; the alg field is a display
        // label that is never persisted (the DB stores only the fingerprint), so
        // comparing it would reject every pinned reconnect.
        return expected.sha256 == actual.sha256
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