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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidKeystoreManager @Inject constructor() : KeystoreManager {
    
    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEK_ALIAS = "rex_kek"
        const val AES_GCM_CIPHER = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 16
    }
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }
    
    override suspend fun ensureKeys() {
        if (!keyStore.containsAlias(KEK_ALIAS)) {
            generateKek()
        }
    }
    
    private fun generateKek() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEK_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // For now, will be updated for device credential flow
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }
    
    override suspend fun wrapDek(rawDek: ByteArray): WrappedKey {
        ensureKeys()
        
        val secretKey = keyStore.getKey(KEK_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(AES_GCM_CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(rawDek)
        
        // Split ciphertext and tag (last 16 bytes)
        val actualCiphertext = ciphertext.copyOfRange(0, ciphertext.size - GCM_TAG_LENGTH)
        val tag = ciphertext.copyOfRange(ciphertext.size - GCM_TAG_LENGTH, ciphertext.size)
        
        return WrappedKey(iv, tag, actualCiphertext)
    }
    
    override suspend fun unwrapDek(wrapped: WrappedKey): ByteArray {
        ensureKeys()
        
        val secretKey = keyStore.getKey(KEK_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(AES_GCM_CIPHER)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, wrapped.iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        
        // Reconstruct ciphertext with tag
        val ciphertextWithTag = wrapped.ciphertext + wrapped.tag
        return cipher.doFinal(ciphertextWithTag)
    }
}