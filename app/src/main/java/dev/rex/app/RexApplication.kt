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
import javax.inject.Inject

@HiltAndroidApp
class RexApplication : Application() {

    @Inject
    lateinit var settingsInitializer: SettingsInitializer

    override fun onCreate() {
        super.onCreate()

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