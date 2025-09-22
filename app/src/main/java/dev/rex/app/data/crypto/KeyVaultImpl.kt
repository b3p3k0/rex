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

package dev.rex.app.data.crypto

import dev.rex.app.data.db.KeyBlobEntity
import dev.rex.app.data.repo.KeysRepository
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyVaultImpl @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val keysRepository: KeysRepository
) : KeyVault {
    
    private companion object {
        const val AES_GCM_CIPHER = "AES/GCM/NoPadding"
        const val DEK_KEY_SIZE = 32 // 256 bits
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 16
    }
    
    override suspend fun importPrivateKeyPem(pem: ByteArray): KeyBlobId {
        val keyBlobId = KeyBlobId(UUID.randomUUID().toString())
        val (encryptedBlob, wrappedDek) = encryptWithNewDek(pem)
        
        val keyBlobEntity = KeyBlobEntity(
            id = keyBlobId.id,
            alg = "ed25519",
            encBlob = encryptedBlob.ciphertext,
            encBlobIv = encryptedBlob.iv,
            encBlobTag = encryptedBlob.tag,
            wrappedDekIv = wrappedDek.iv,
            wrappedDekTag = wrappedDek.tag,
            wrappedDekCiphertext = wrappedDek.ciphertext,
            publicKeyOpenssh = extractPublicKeyFromPem(pem),
            createdAt = System.currentTimeMillis()
        )
        
        keysRepository.insertKeyBlob(keyBlobEntity)
        return keyBlobId
    }
    
    override suspend fun generateEd25519(): Pair<KeyBlobId, String> {
        // For now, this is a placeholder. Real implementation would use
        // BouncyCastle or similar to generate Ed25519 keys
        TODO("Ed25519 key generation not yet implemented")
    }
    
    override suspend fun deleteKey(id: KeyBlobId) {
        val keyBlob = keysRepository.getKeyBlobById(id.id)
        if (keyBlob != null) {
            keysRepository.deleteKeyBlob(keyBlob)
        }
    }
    
    override suspend fun decryptPrivateKey(id: KeyBlobId): ByteArray {
        val keyBlob = keysRepository.getKeyBlobById(id.id)
            ?: throw IllegalArgumentException("Key blob not found: ${id.id}")
        
        return decryptWithDek(
            encryptedData = WrappedKey(keyBlob.encBlobIv, keyBlob.encBlobTag, keyBlob.encBlob),
            wrappedDek = WrappedKey(keyBlob.wrappedDekIv, keyBlob.wrappedDekTag, keyBlob.wrappedDekCiphertext)
        )
    }
    
    private suspend fun encryptWithNewDek(plaintext: ByteArray): Pair<WrappedKey, WrappedKey> {
        // Generate a new DEK
        val dek = ByteArray(DEK_KEY_SIZE)
        SecureRandom().nextBytes(dek)
        
        try {
            // Encrypt the plaintext with the DEK
            val secretKey = SecretKeySpec(dek, "AES")
            val cipher = Cipher.getInstance(AES_GCM_CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)
            
            try {
                // Split ciphertext and tag
                val actualCiphertext = ciphertext.copyOfRange(0, ciphertext.size - GCM_TAG_LENGTH)
                val tag = ciphertext.copyOfRange(ciphertext.size - GCM_TAG_LENGTH, ciphertext.size)
                
                // Wrap the DEK with the KEK
                val wrappedDek = keystoreManager.wrapDek(dek)
                
                return Pair(
                    WrappedKey(iv, tag, actualCiphertext),
                    wrappedDek
                )
            } finally {
                // Zeroize the ciphertext buffer
                ciphertext.fill(0)
            }
        } finally {
            // Zeroize the DEK
            dek.fill(0)
        }
    }
    
    private suspend fun decryptWithDek(encryptedData: WrappedKey, wrappedDek: WrappedKey): ByteArray {
        // Unwrap the DEK using the KEK
        val dek = keystoreManager.unwrapDek(wrappedDek)
        
        try {
            // Decrypt the data with the DEK
            val secretKey = SecretKeySpec(dek, "AES")
            val cipher = Cipher.getInstance(AES_GCM_CIPHER)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            
            // Reconstruct ciphertext with tag
            val ciphertextWithTag = encryptedData.ciphertext + encryptedData.tag
            val result = cipher.doFinal(ciphertextWithTag)
            
            // Zeroize the reconstructed ciphertext buffer
            ciphertextWithTag.fill(0)
            
            return result
        } finally {
            // Zeroize the DEK
            dek.fill(0)
        }
    }
    
    private fun extractPublicKeyFromPem(pem: ByteArray): String {
        val pemString = String(pem, Charsets.UTF_8)
        
        // Basic PEM validation - check for standard headers
        if (pemString.contains("-----BEGIN PRIVATE KEY-----") || 
            pemString.contains("-----BEGIN OPENSSH PRIVATE KEY-----") ||
            pemString.contains("-----BEGIN EC PRIVATE KEY-----") ||
            pemString.contains("-----BEGIN RSA PRIVATE KEY-----")) {
            
            // For now, return a deterministic placeholder based on content hash
            // Real implementation would use BouncyCastle to extract public key
            val hash = pemString.hashCode().toString(16).take(8)
            return "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI${hash}Rex rex@android"
        }
        
        throw IllegalArgumentException("Invalid PEM format")
    }
}