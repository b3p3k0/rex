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

package dev.rex.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey val id: String,
    val nickname: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    @ColumnInfo(name = "auth_method") val authMethod: String = "key",
    @ColumnInfo(name = "key_blob_id") val keyBlobId: String?,
    @ColumnInfo(name = "connect_timeout_ms") val connectTimeoutMs: Int = 8000,
    @ColumnInfo(name = "read_timeout_ms") val readTimeoutMs: Int = 15000,
    @ColumnInfo(name = "strict_host_key") val strictHostKey: Boolean = true,
    @ColumnInfo(name = "pinned_host_key_fingerprint") val pinnedHostKeyFingerprint: String?,
    @ColumnInfo(name = "key_provisioned_at") val keyProvisionedAt: Long? = null,
    @ColumnInfo(name = "key_provision_status") val keyProvisionStatus: String = "none",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "commands")
data class CommandEntity(
    @PrimaryKey val id: String,
    val name: String,
    val command: String,
    @ColumnInfo(name = "require_confirmation") val requireConfirmation: Boolean = true,
    @ColumnInfo(name = "default_timeout_ms") val defaultTimeoutMs: Int = 15000,
    @ColumnInfo(name = "allow_pty") val allowPty: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "host_commands",
    primaryKeys = ["host_id", "command_id"],
    indices = [Index(value = ["host_id", "command_id"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = HostEntity::class,
            parentColumns = ["id"],
            childColumns = ["host_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CommandEntity::class,
            parentColumns = ["id"],
            childColumns = ["command_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HostCommandEntity(
    @ColumnInfo(name = "host_id") val hostId: String,
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "sort_index") val sortIndex: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "key_blobs")
data class KeyBlobEntity(
    @PrimaryKey val id: String,
    val alg: String = "ed25519",
    @ColumnInfo(name = "enc_blob") val encBlob: ByteArray,
    @ColumnInfo(name = "enc_blob_iv") val encBlobIv: ByteArray,
    @ColumnInfo(name = "enc_blob_tag") val encBlobTag: ByteArray,
    @ColumnInfo(name = "wrapped_dek_iv") val wrappedDekIv: ByteArray,
    @ColumnInfo(name = "wrapped_dek_tag") val wrappedDekTag: ByteArray,
    @ColumnInfo(name = "wrapped_dek_ciphertext") val wrappedDekCiphertext: ByteArray,
    @ColumnInfo(name = "public_key_openssh") val publicKeyOpenssh: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as KeyBlobEntity
        
        if (id != other.id) return false
        if (alg != other.alg) return false
        if (!encBlob.contentEquals(other.encBlob)) return false
        if (!encBlobIv.contentEquals(other.encBlobIv)) return false
        if (!encBlobTag.contentEquals(other.encBlobTag)) return false
        if (!wrappedDekIv.contentEquals(other.wrappedDekIv)) return false
        if (!wrappedDekTag.contentEquals(other.wrappedDekTag)) return false
        if (!wrappedDekCiphertext.contentEquals(other.wrappedDekCiphertext)) return false
        if (publicKeyOpenssh != other.publicKeyOpenssh) return false
        if (createdAt != other.createdAt) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + alg.hashCode()
        result = 31 * result + encBlob.contentHashCode()
        result = 31 * result + encBlobIv.contentHashCode()
        result = 31 * result + encBlobTag.contentHashCode()
        result = 31 * result + wrappedDekIv.contentHashCode()
        result = 31 * result + wrappedDekTag.contentHashCode()
        result = 31 * result + wrappedDekCiphertext.contentHashCode()
        result = 31 * result + publicKeyOpenssh.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

data class HostCommandMapping(
    val id: String,
    val nickname: String,
    val hostname: String,
    val port: Int,
    val username: String,
    @ColumnInfo(name = "auth_method") val authMethod: String,
    @ColumnInfo(name = "key_blob_id") val keyBlobId: String?,
    @ColumnInfo(name = "connect_timeout_ms") val connectTimeoutMs: Int,
    @ColumnInfo(name = "read_timeout_ms") val readTimeoutMs: Int,
    @ColumnInfo(name = "strict_host_key") val strictHostKey: Boolean,
    @ColumnInfo(name = "pinned_host_key_fingerprint") val pinnedHostKeyFingerprint: String?,
    @ColumnInfo(name = "key_provisioned_at") val keyProvisionedAt: Long?,
    @ColumnInfo(name = "key_provision_status") val keyProvisionStatus: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val name: String,
    val command: String,
    @ColumnInfo(name = "require_confirmation") val requireConfirmation: Boolean,
    @ColumnInfo(name = "default_timeout_ms") val defaultTimeoutMs: Int,
    @ColumnInfo(name = "allow_pty") val allowPty: Boolean,
    @ColumnInfo(name = "mapping_id") val mappingId: String,
    @ColumnInfo(name = "sort_index") val sortIndex: Int
)

data class HostCommandRow(
    val hostId: String,
    val hostNickname: String,
    val hostName: String,
    val hostPort: Int,
    val hostUser: String,
    val hostAuthMethod: String,
    val hostKeyBlobId: String?,
    val hostConnectTimeoutMs: Int,
    val hostReadTimeoutMs: Int,
    val hostStrictHostKey: Boolean,
    val hostPinnedHostKeyFingerprint: String?,
    val hostKeyProvisionedAt: Long?,
    val hostKeyProvisionStatus: String,
    val hostCreatedAt: Long,
    val hostUpdatedAt: Long,
    val cmdId: String?,       // nullable when no commands
    val cmdName: String?,     // nullable
    val cmdCommand: String?,  // nullable
    val cmdRequireConfirmation: Boolean?,
    val cmdDefaultTimeoutMs: Int?,
    val cmdAllowPty: Boolean?,
    val mappingId: String?,   // nullable
    val sortIndex: Int?       // nullable
)

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey val id: String,
    val ts: Long,
    @ColumnInfo(name = "host_nickname") val hostNickname: String,
    @ColumnInfo(name = "command_name") val commandName: String,
    @ColumnInfo(name = "exit_code") val exitCode: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Int?,
    @ColumnInfo(name = "bytes_stdout") val bytesStdout: Int,
    @ColumnInfo(name = "bytes_stderr") val bytesStderr: Int,
    val status: String,
    @ColumnInfo(name = "message_redacted") val messageRedacted: String?,
    @ColumnInfo(name = "idx_seq") val idxSeq: Long
)

data class LogSizeInfo(
    val id: String,
    val total_bytes: Int
)

@Entity(
    tableName = "key_provision_logs",
    foreignKeys = [
        ForeignKey(
            entity = HostEntity::class,
            parentColumns = ["id"],
            childColumns = ["host_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = KeyBlobEntity::class,
            parentColumns = ["id"],
            childColumns = ["key_blob_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class KeyProvisionLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "host_id") val hostId: String,
    @ColumnInfo(name = "key_blob_id") val keyBlobId: String,
    val ts: Long,
    val operation: String,
    val status: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Int?,
    @ColumnInfo(name = "stdout_preview") val stdoutPreview: String?,
    @ColumnInfo(name = "stderr_preview") val stderrPreview: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?
)