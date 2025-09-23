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

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HostEntity::class,
        CommandEntity::class,
        HostCommandEntity::class,
        KeyBlobEntity::class,
        LogEntity::class,
        KeyProvisionLogEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class RexDatabase : RoomDatabase() {
    abstract fun hostsDao(): HostsDao
    abstract fun commandsDao(): CommandsDao
    abstract fun hostCommandsDao(): HostCommandsDao
    abstract fun keyBlobsDao(): KeyBlobsDao
    abstract fun logsDao(): LogsDao
    abstract fun keyProvisionLogsDao(): KeyProvisionLogsDao

    companion object {
        @Volatile
        private var INSTANCE: RexDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    ALTER TABLE key_blobs ADD COLUMN wrapped_dek_iv BLOB NOT NULL DEFAULT X'00000000000000000000000000000000'
                """)
                database.execSQL("""
                    ALTER TABLE key_blobs ADD COLUMN wrapped_dek_tag BLOB NOT NULL DEFAULT X'00000000000000000000000000000000'
                """)
                database.execSQL("""
                    ALTER TABLE key_blobs ADD COLUMN wrapped_dek_ciphertext BLOB NOT NULL DEFAULT X'00000000000000000000000000000000'
                """)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Recreate host_commands table with composite primary key and unique constraint
                database.execSQL("""
                    CREATE TABLE host_commands_new (
                        host_id TEXT NOT NULL,
                        command_id TEXT NOT NULL,
                        sort_index INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (host_id, command_id),
                        FOREIGN KEY (host_id) REFERENCES hosts (id) ON DELETE CASCADE,
                        FOREIGN KEY (command_id) REFERENCES commands (id) ON DELETE CASCADE
                    )
                """)

                // Copy data from old table, excluding the id column
                database.execSQL("""
                    INSERT INTO host_commands_new (host_id, command_id, sort_index, created_at)
                    SELECT host_id, command_id, sort_index, created_at FROM host_commands
                """)

                // Drop old table and rename new one
                database.execSQL("DROP TABLE host_commands")
                database.execSQL("ALTER TABLE host_commands_new RENAME TO host_commands")

                // Create unique index
                database.execSQL("""
                    CREATE UNIQUE INDEX index_host_commands_host_id_command_id
                    ON host_commands (host_id, command_id)
                """)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add key provisioning fields to hosts table
                database.execSQL("""
                    ALTER TABLE hosts ADD COLUMN key_provisioned_at INTEGER
                """)
                database.execSQL("""
                    ALTER TABLE hosts ADD COLUMN key_provision_status TEXT NOT NULL DEFAULT 'none'
                """)

                // Create key provision logs table
                database.execSQL("""
                    CREATE TABLE key_provision_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        host_id TEXT NOT NULL,
                        key_blob_id TEXT NOT NULL,
                        ts INTEGER NOT NULL,
                        operation TEXT NOT NULL,
                        status TEXT NOT NULL,
                        duration_ms INTEGER,
                        stdout_preview TEXT,
                        stderr_preview TEXT,
                        error_message TEXT,
                        FOREIGN KEY (host_id) REFERENCES hosts (id) ON DELETE CASCADE,
                        FOREIGN KEY (key_blob_id) REFERENCES key_blobs (id) ON DELETE CASCADE
                    )
                """)
            }
        }

        fun getDatabase(context: Context): RexDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RexDatabase::class.java,
                    "rex_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.query("PRAGMA foreign_keys").use { cursor ->
                            if (cursor.moveToFirst()) {
                                Log.i("Rex", "FK enabled: ${cursor.getInt(0) == 1}")
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}