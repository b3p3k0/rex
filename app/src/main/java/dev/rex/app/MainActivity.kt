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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.rex.app.data.db.HostCommandMapping
import dev.rex.app.ui.navigation.RexNavigation
import dev.rex.app.ui.theme.RexTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RexTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RexApp()
                }
            }
        }
    }
}

@Composable
fun RexApp() {
    val navController = rememberNavController()
    var showSshDialog by remember { mutableStateOf(false) }
    var currentHostCommand by remember { mutableStateOf<HostCommandMapping?>(null) }

    RexNavigation(
        navController = navController,
        onExecuteCommand = { hostCommand ->
            currentHostCommand = hostCommand
            showSshDialog = true
        }
    )

    if (showSshDialog && currentHostCommand != null) {
        SshStubDialog(
            hostCommand = currentHostCommand!!,
            onDismiss = {
                showSshDialog = false
                currentHostCommand = null
            }
        )
    }
}

@Composable
fun SshStubDialog(
    hostCommand: HostCommandMapping,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Command Executed (Stubbed)") },
        text = {
            Text("Executed: ${hostCommand.command} on ${hostCommand.hostname} (stubbed)")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    RexTheme {
        Text("Rex")
    }
}