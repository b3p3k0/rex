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

package dev.rex.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import dev.rex.app.core.SettingsInitializer
import dev.rex.app.core.SshSecurityBootstrap
import net.schmizz.sshj.common.SecurityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.inject.Inject

@HiltAndroidApp
class RexApplication : Application() {

    @Inject
    lateinit var settingsInitializer: SettingsInitializer

    override fun onCreate() {
        // TODO(claude): remove once SSH provisioning is stable
        // Must run before any SSHJ class loading to configure slf4j logging
        System.setProperty("org.slf4j.simpleLogger.log.net.schmizz", "debug")

        super.onCreate()

        val providerWasInstalled = SshSecurityBootstrap.installIfNeeded { message ->
            Log.d("Rex", message)
        }

        // Ensure we have the full BouncyCastle provider for SSHJ Ed25519 key parsing
        var bc = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (bc == null || bc.javaClass != BouncyCastleProvider::class.java) {
            bc = BouncyCastleProvider()
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(bc)
        }

        // Configure SSHJ to use our full BC provider
        SecurityUtils.registerSecurityProvider(BouncyCastleProvider.PROVIDER_NAME)
        SecurityUtils.setSecurityProvider(BouncyCastleProvider.PROVIDER_NAME)

        // TODO(claude): remove once SSH provisioning is stable
        val eddsaProvider = Security.getProvider("EdDSA")
        val sshjProvider = SecurityUtils.getSecurityProvider()
        Log.i(
            "Rex",
            "Providers — EdDSA available: ${eddsaProvider != null}, BC: ${bc.javaClass.name}, SSHJ active: ${sshjProvider?.javaClass?.name}, installed now: $providerWasInstalled"
        )

        // Force injection and initialization
        settingsInitializer.initialize()

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            Log.e("Rex", "Uncaught", e)
        }
        
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
    }

}
