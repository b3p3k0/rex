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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class SshSecurityBootstrapTest {

    @Before
    fun resetProviders() {
        Security.removeProvider(EdDSASecurityProvider.PROVIDER_NAME)
    }

    @Test
    fun installsEdDsaProviderWhenMissing() {
        SshSecurityBootstrap.installIfNeeded()

        assertNotNull(
            "EdDSA provider should be registered with java.security",
            Security.getProvider(EdDSASecurityProvider.PROVIDER_NAME)
        )
    }
}
