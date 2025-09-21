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
        val encryptedBlob = encryptWithNewDek(pem)
        
        val keyBlobEntity = KeyBlobEntity(
            id = keyBlobId.id,
            alg = "ed25519",
            encBlob = encryptedBlob.ciphertext,
            encBlobIv = encryptedBlob.iv,
            encBlobTag = encryptedBlob.tag,
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
            WrappedKey(keyBlob.encBlobIv, keyBlob.encBlobTag, keyBlob.encBlob)
        )
    }
    
    private suspend fun encryptWithNewDek(plaintext: ByteArray): WrappedKey {
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
            
            // Split ciphertext and tag
            val actualCiphertext = ciphertext.copyOfRange(0, ciphertext.size - GCM_TAG_LENGTH)
            val tag = ciphertext.copyOfRange(ciphertext.size - GCM_TAG_LENGTH, ciphertext.size)
            
            // Wrap the DEK with the KEK (this would need to be stored with the key blob)
            val wrappedDek = keystoreManager.wrapDek(dek)
            
            return WrappedKey(iv, tag, actualCiphertext)
        } finally {
            // Zeroize the DEK
            dek.fill(0)
        }
    }
    
    private suspend fun decryptWithDek(wrappedKey: WrappedKey): ByteArray {
        // This is a simplified implementation. In practice, we'd need to store
        // the wrapped DEK with each key blob and unwrap it here.
        TODO("DEK unwrapping and decryption not fully implemented")
    }
    
    private fun extractPublicKeyFromPem(pem: ByteArray): String {
        // Placeholder - would parse PEM to extract public key in OpenSSH format
        return "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI... (placeholder)"
    }
}