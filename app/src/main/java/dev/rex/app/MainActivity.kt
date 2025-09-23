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

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.rex.app.data.settings.SettingsStore
import dev.rex.app.ui.navigation.RexNavigation
import dev.rex.app.ui.screens.SettingsViewModel
import dev.rex.app.ui.theme.RexTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("Rex", "MainActivity created")
        setContent {
            RexTheme {
                // Apply screen capture protection based on settings
                ApplyScreenProtection()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RexApp()
                }
            }
        }
    }

    @Composable
    private fun ApplyScreenProtection() {
        val settingsViewModel: SettingsViewModel = hiltViewModel()
        val settingsData by settingsViewModel.settingsData.collectAsStateWithLifecycle()

        LaunchedEffect(settingsData.screenCaptureProtection) {
            if (settingsData.screenCaptureProtection) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

@Composable
fun RexApp() {
    val navController = rememberNavController()

    RexNavigation(navController = navController)
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    RexTheme {
        Text("Rex")
    }
}
