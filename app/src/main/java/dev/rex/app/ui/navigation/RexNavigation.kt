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
import dev.rex.app.ui.screens.AddCommandScreen
import dev.rex.app.ui.screens.AddHostScreen
import dev.rex.app.ui.screens.MainTableScreen

object RexDestinations {
    const val MAIN_TABLE = "main_table"
    const val HOST_EDIT = "host_edit"
    const val COMMAND_EDIT = "command_edit"
}

@Composable
fun RexNavigation(
    navController: NavHostController,
    onExecuteCommand: (dev.rex.app.data.db.HostCommandMapping) -> Unit
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
                onNavigateToAddCommand = {
                    navController.navigate(RexDestinations.COMMAND_EDIT)
                },
                onNavigateToSettings = {
                    // Settings not implemented in this slice
                },
                onExecuteCommand = onExecuteCommand
            )
        }

        composable(RexDestinations.HOST_EDIT) {
            AddHostScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(RexDestinations.COMMAND_EDIT) {
            AddCommandScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}