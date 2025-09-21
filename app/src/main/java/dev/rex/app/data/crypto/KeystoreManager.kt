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

interface KeystoreManager {
    suspend fun ensureKeys()
    suspend fun wrapDek(rawDek: ByteArray): WrappedKey
    suspend fun unwrapDek(wrapped: WrappedKey): ByteArray
}

data class WrappedKey(
    val iv: ByteArray,
    val tag: ByteArray,
    val ciphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WrappedKey

        if (!iv.contentEquals(other.iv)) return false
        if (!tag.contentEquals(other.tag)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}