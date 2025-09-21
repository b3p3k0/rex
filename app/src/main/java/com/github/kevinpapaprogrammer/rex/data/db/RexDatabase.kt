/*
 * Rex — Remote Exec for Android
 * Copyright (C) 2024 Kevin Papa
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

package com.github.kevinpapaprogrammer.rex.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [
        HostEntity::class,
        CommandEntity::class,
        HostCommandEntity::class,
        KeyBlobEntity::class,
        LogEntity::class
    ],
    version = 1,
    exportSchema = false
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

        fun getDatabase(context: Context): RexDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RexDatabase::class.java,
                    "rex_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}