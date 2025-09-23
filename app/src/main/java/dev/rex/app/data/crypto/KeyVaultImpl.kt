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
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.KeyPairGenerator
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.SecureRandom
import java.util.Base64
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
        val keyBlobId = KeyBlobId(UUID.randomUUID().toString())

        // Generate Ed25519 keypair
        val spec: EdDSAParameterSpec = EdDSANamedCurveTable.getByName("Ed25519")
        val keyPairGenerator = KeyPairGenerator()
        keyPairGenerator.initialize(spec, SecureRandom())
        val keyPair: KeyPair = keyPairGenerator.generateKeyPair()

        val privateKey = keyPair.private as EdDSAPrivateKey
        val publicKey = keyPair.public as EdDSAPublicKey

        var privateKeyPem: ByteArray? = null
        var opensshPublicKey: String

        try {
            // Convert private key to PKCS#8 PEM format
            val privateKeyDer = privateKey.encoded
            privateKeyPem = toPkcs8Pem(privateKeyDer)

            // Build OpenSSH public key format
            opensshPublicKey = buildOpenSshPublicKey(publicKey.abyte)

            // Encrypt the PEM bytes
            val (encryptedBlob, wrappedDek) = encryptWithNewDek(privateKeyPem)

            val keyBlobEntity = KeyBlobEntity(
                id = keyBlobId.id,
                alg = "ed25519",
                encBlob = encryptedBlob.ciphertext,
                encBlobIv = encryptedBlob.iv,
                encBlobTag = encryptedBlob.tag,
                wrappedDekIv = wrappedDek.iv,
                wrappedDekTag = wrappedDek.tag,
                wrappedDekCiphertext = wrappedDek.ciphertext,
                publicKeyOpenssh = opensshPublicKey,
                createdAt = System.currentTimeMillis()
            )

            keysRepository.insertKeyBlob(keyBlobEntity)
            return Pair(keyBlobId, opensshPublicKey)

        } finally {
            // Zeroize sensitive material
            privateKeyPem?.fill(0)
            privateKey.encoded?.fill(0)
        }
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

    override suspend fun getPublicKeyOpenssh(id: KeyBlobId): String {
        val keyBlob = keysRepository.getKeyBlobById(id.id)
            ?: throw IllegalArgumentException("Key blob not found: ${id.id}")

        return keyBlob.publicKeyOpenssh
    }

    override suspend fun validatePrivateKeyPem(pem: ByteArray): Boolean {
        return try {
            extractPublicKeyFromPem(pem)
            true
        } catch (e: Exception) {
            false
        }
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

    private fun toPkcs8Pem(derBytes: ByteArray): ByteArray {
        val base64 = Base64.getEncoder().encodeToString(derBytes)
        val chunked = base64.chunked(64).joinToString("\n")
        val pem = "-----BEGIN PRIVATE KEY-----\n$chunked\n-----END PRIVATE KEY-----\n"
        return pem.toByteArray(Charsets.UTF_8)
    }

    private fun buildOpenSshPublicKey(publicKeyBytes: ByteArray): String {
        val keyType = "ssh-ed25519"
        val buffer = ByteArrayOutputStream()

        // Write SSH wire format: length-prefixed strings
        writeString(buffer, keyType)
        writeString(buffer, publicKeyBytes)

        val sshBlob = buffer.toByteArray()
        val base64Blob = Base64.getEncoder().encodeToString(sshBlob)

        return "$keyType $base64Blob rex@android"
    }

    private fun writeString(buffer: ByteArrayOutputStream, data: String) {
        writeString(buffer, data.toByteArray(Charsets.UTF_8))
    }

    private fun writeString(buffer: ByteArrayOutputStream, data: ByteArray) {
        val lengthBytes = ByteBuffer.allocate(4).putInt(data.size).array()
        buffer.write(lengthBytes)
        buffer.write(data)
    }
}