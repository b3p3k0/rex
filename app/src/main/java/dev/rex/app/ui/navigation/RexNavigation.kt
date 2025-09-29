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

package dev.rex.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import dev.rex.app.ui.screens.AddCommandScreen
import dev.rex.app.ui.screens.AddHostScreen
import dev.rex.app.ui.screens.EditCommandScreen
import dev.rex.app.ui.screens.HostDetailScreen
import dev.rex.app.ui.screens.LogsScreen
import dev.rex.app.ui.screens.MainTableScreen
import dev.rex.app.ui.screens.SettingsScreen

object RexDestinations {
    const val MAIN_TABLE = "main_table"
    const val HOST_EDIT = "host_edit"
    const val HOST_DETAIL = "host_detail"
    const val ADD_COMMAND = "add-command"
    const val COMMAND_EDIT_EXISTING = "command_edit_existing"
    const val SETTINGS = "settings"
    const val LOGS = "logs"
}

@Composable
fun RexNavigation(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = RexDestinations.MAIN_TABLE
    ) {
        composable(RexDestinations.MAIN_TABLE) {
            MainTableScreen(
                onNavigateToAddHost = {
                    navController.navigate(RexDestinations.HOST_EDIT)
                },
                onNavigateToAddCommand = { hostId ->
                    navController.navigate("${RexDestinations.ADD_COMMAND}/$hostId")
                },
                onNavigateToEditCommand = { commandId ->
                    navController.navigate("${RexDestinations.COMMAND_EDIT_EXISTING}/$commandId")
                },
                onNavigateToSettings = {
                    navController.navigate(RexDestinations.SETTINGS)
                },
                onNavigateToLogs = {
                    navController.navigate(RexDestinations.LOGS)
                },
                onNavigateToHostDetail = { hostId ->
                    navController.navigate("${RexDestinations.HOST_DETAIL}/$hostId")
                }
            )
        }

        composable(RexDestinations.HOST_EDIT) {
            AddHostScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onHostCreated = { hostId ->
                    navController.popBackStack()
                    navController.navigate("${RexDestinations.HOST_DETAIL}/$hostId?autoKeyOnboarding=true")
                }
            )
        }

        composable(
            "${RexDestinations.HOST_DETAIL}/{hostId}?autoKeyOnboarding={autoKeyOnboarding}",
            arguments = listOf(
                navArgument("hostId") { type = NavType.StringType },
                navArgument("autoKeyOnboarding") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val hostId = backStackEntry.arguments?.getString("hostId") ?: ""
            val autoKeyOnboarding = backStackEntry.arguments?.getBoolean("autoKeyOnboarding") ?: false
            HostDetailScreen(
                hostId = hostId,
                autoKeyOnboarding = autoKeyOnboarding,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("${RexDestinations.ADD_COMMAND}/{hostId}") { backStackEntry ->
            val hostId = backStackEntry.arguments?.getString("hostId") ?: ""
            AddCommandScreen(
                hostId = hostId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("${RexDestinations.COMMAND_EDIT_EXISTING}/{commandId}") { backStackEntry ->
            val commandId = backStackEntry.arguments?.getString("commandId") ?: ""
            EditCommandScreen(
                commandId = commandId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(RexDestinations.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(RexDestinations.LOGS) {
            LogsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
