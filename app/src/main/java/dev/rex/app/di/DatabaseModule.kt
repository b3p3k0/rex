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

package dev.rex.app.di

import android.content.Context
import androidx.room.Room
import dev.rex.app.core.Gatekeeper
import dev.rex.app.core.SecurityManager
import dev.rex.app.data.db.*
import dev.rex.app.data.repo.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRexDatabase(@ApplicationContext context: Context): RexDatabase {
        return RexDatabase.getDatabase(context)
    }

    @Provides
    fun provideHostsDao(database: RexDatabase): HostsDao = database.hostsDao()

    @Provides
    fun provideCommandsDao(database: RexDatabase): CommandsDao = database.commandsDao()

    @Provides
    fun provideHostCommandsDao(database: RexDatabase): HostCommandsDao = database.hostCommandsDao()

    @Provides
    fun provideKeyBlobsDao(database: RexDatabase): KeyBlobsDao = database.keyBlobsDao()

    @Provides
    fun provideLogsDao(database: RexDatabase): LogsDao = database.logsDao()

    @Provides
    fun provideKeyProvisionLogsDao(database: RexDatabase): KeyProvisionLogsDao = database.keyProvisionLogsDao()

    // Repositories
    @Provides
    @Singleton
    fun provideHostsRepository(hostsDao: HostsDao, hostCommandsDao: HostCommandsDao): HostsRepository = 
        HostsRepository(hostsDao, hostCommandsDao)

    @Provides
    @Singleton
    fun provideCommandsRepository(commandsDao: CommandsDao, hostCommandsDao: HostCommandsDao): CommandsRepository =
        CommandsRepository(commandsDao, hostCommandsDao)

    @Provides
    @Singleton
    fun provideLogsRepository(logsDao: LogsDao): LogsRepository = 
        LogsRepository(logsDao)

    @Provides
    @Singleton
    fun provideGatekeeper(securityManager: SecurityManager): Gatekeeper =
        Gatekeeper(securityManager)

    @Provides
    @Singleton
    fun provideKeysRepository(keyBlobsDao: KeyBlobsDao, keyProvisionLogsDao: KeyProvisionLogsDao, gatekeeper: Gatekeeper): KeysRepository =
        KeysRepository(keyBlobsDao, keyProvisionLogsDao, gatekeeper)

    @Provides
    @Singleton
    fun provideHostCommandRepository(hostCommandsDao: HostCommandsDao): HostCommandRepository = 
        HostCommandRepository(hostCommandsDao)
}