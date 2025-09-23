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

package dev.rex.app.data.repo

import dev.rex.app.core.Gatekeeper
import dev.rex.app.data.db.KeyBlobEntity
import dev.rex.app.data.db.KeyBlobsDao
import dev.rex.app.data.db.KeyProvisionLogsDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeysRepository @Inject constructor(
    private val keyBlobsDao: KeyBlobsDao,
    private val keyProvisionLogsDao: KeyProvisionLogsDao,
    private val gatekeeper: Gatekeeper
) {
    suspend fun getKeyBlobById(id: String): KeyBlobEntity? = keyBlobsDao.getKeyBlobById(id)

    suspend fun insertKeyBlob(keyBlob: KeyBlobEntity) {
        gatekeeper.requireGateForKeyOperation()
        keyBlobsDao.insertKeyBlob(keyBlob)
    }

    suspend fun deleteKeyBlob(keyBlob: KeyBlobEntity) {
        gatekeeper.requireGateForKeyOperation()
        keyBlobsDao.deleteKeyBlob(keyBlob)
    }

    suspend fun getProvisionLogsByHost(hostId: String) =
        keyProvisionLogsDao.getProvisionLogsByHost(hostId)

    suspend fun getProvisionLogsByKey(keyBlobId: String) =
        keyProvisionLogsDao.getProvisionLogsByKey(keyBlobId)

    suspend fun insertProvisionLog(log: dev.rex.app.data.db.KeyProvisionLogEntity) {
        keyProvisionLogsDao.insertProvisionLog(log)
    }
}