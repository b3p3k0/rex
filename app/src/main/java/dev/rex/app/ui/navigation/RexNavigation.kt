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

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import dev.rex.app.ui.screens.AddCommandScreen
import dev.rex.app.ui.screens.AddHostScreen
import dev.rex.app.ui.screens.EditCommandScreen
import dev.rex.app.ui.screens.HostCommandsScreen
import dev.rex.app.ui.screens.HostDetailScreen
import dev.rex.app.ui.screens.HostListScreen
import dev.rex.app.ui.screens.LogsScreen
import dev.rex.app.ui.screens.SettingsScreen

object RexDestinations {
    const val MAIN_TABLE = "main_table"
    const val HOST_ADD = "host_add"
    const val HOST_EDIT = "host_edit"
    const val HOST_DETAIL = "host_detail"
    const val HOST_COMMANDS = "host_commands"
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
            HostListScreen(
                onNavigateToAddHost = {
                    navController.navigate(RexDestinations.HOST_ADD)
                },
                onNavigateToHostCommands = { hostId ->
                    navController.navigate("${RexDestinations.HOST_COMMANDS}/$hostId")
                },
                onNavigateToSettings = {
                    navController.navigate(RexDestinations.SETTINGS)
                },
                onNavigateToLogs = {
                    navController.navigate(RexDestinations.LOGS)
                },
                onNavigateToHostDetail = { hostId ->
                    navController.navigate("${RexDestinations.HOST_DETAIL}/$hostId")
                },
            )
        }

        composable(
            "${RexDestinations.HOST_COMMANDS}/{hostId}",
            arguments = listOf(navArgument("hostId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
        ) {
            HostCommandsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAddCommand = { hostId ->
                    navController.navigate("${RexDestinations.ADD_COMMAND}/$hostId")
                },
                onNavigateToEditCommand = { commandId ->
                    navController.navigate("${RexDestinations.COMMAND_EDIT_EXISTING}/$commandId")
                },
                onNavigateToHostDetail = { hostId ->
                    navController.navigate("${RexDestinations.HOST_DETAIL}/$hostId")
                }
            )
        }

        // Add new host
        composable(RexDestinations.HOST_ADD) {
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

        // Edit existing host
        composable(
            "${RexDestinations.HOST_EDIT}/{hostId}",
            arguments = listOf(navArgument("hostId") { type = NavType.StringType })
        ) { backStackEntry ->
            val hostId = backStackEntry.arguments?.getString("hostId") ?: ""
            AddHostScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onHostCreated = { _ ->
                    navController.popBackStack()
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
                },
                onNavigateToEditHost = { editHostId ->
                    navController.navigate("${RexDestinations.HOST_EDIT}/$editHostId")
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
