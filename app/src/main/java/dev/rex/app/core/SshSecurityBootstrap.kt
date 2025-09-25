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

package dev.rex.app.core

import java.security.Security
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider

object SshSecurityBootstrap {

    private const val EDDSA_PROVIDER_NAME = EdDSASecurityProvider.PROVIDER_NAME
    private const val BC_PROVIDER_NAME = "BC"

    @Synchronized
    fun installIfNeeded(onInstall: ((String) -> Unit)? = null): Boolean {
        var installed = false

        val currentBc = Security.getProvider(BC_PROVIDER_NAME)
        if (currentBc !is BouncyCastleProvider) {
            val replacement = BouncyCastleProvider()
            try {
                Security.removeProvider(BC_PROVIDER_NAME)
            } catch (_: Exception) {
                // Ignore inability to remove; insert will override if allowed
            }

            val position = Security.insertProviderAt(replacement, 1)
            if (position > 0) {
                installed = true
                onInstall?.invoke("Installed BouncyCastle provider with Curve25519 support at position $position")
            } else {
                // If insert failed, make a best-effort registration at the end
                val added = Security.addProvider(replacement)
                if (added > 0) {
                    installed = true
                    onInstall?.invoke("Installed BouncyCastle provider with Curve25519 support at end of list")
                } else {
                    onInstall?.invoke("Unable to replace BouncyCastle provider; existing provider=${currentBc?.javaClass?.name}")
                }
            }
        }

        if (Security.getProvider(EDDSA_PROVIDER_NAME) == null) {
            Security.addProvider(EdDSASecurityProvider())
            installed = true
            onInstall?.invoke("Installed EdDSA security provider")
        }

        return installed
    }
}
