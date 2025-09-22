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

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [
        HostEntity::class,
        CommandEntity::class,
        HostCommandEntity::class,
        KeyBlobEntity::class,
        LogEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class RexDatabase : RoomDatabase() {
    abstract fun hostsDao(): HostsDao
    abstract fun commandsDao(): CommandsDao
    abstract fun hostCommandsDao(): HostCommandsDao
    abstract fun keyBlobsDao(): KeyBlobsDao
    abstract fun logsDao(): LogsDao

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

        fun getDatabase(context: Context): RexDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RexDatabase::class.java,
                    "rex_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}