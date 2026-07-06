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

package dev.rex.app.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.rex.app.core.SecurityManager
import dev.rex.app.ui.components.DeleteKeyDialog
import dev.rex.app.ui.components.HostInfoCard
import dev.rex.app.ui.components.ImportKeyDialog
import dev.rex.app.ui.components.KeyManagementCard
import dev.rex.app.ui.components.PasswordDialog
import dev.rex.app.ui.components.ProvisionResultCard
import dev.rex.app.ui.components.SecurityGate
import dev.rex.app.ui.components.TestResultDialog
import dev.rex.app.ui.components.TofuConfirmationDialog

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SecurityManagerEntryPoint {
    fun securityManager(): SecurityManager
}

private enum class AutoKeyOnboardingStage {
    Idle, LaunchGenerate, AwaitKey, LaunchDeploy, Finished
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostDetailScreen(
    hostId: String,
    autoKeyOnboarding: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateToEditHost: (String) -> Unit,
    viewModel: HostDetailViewModel = hiltViewModel()
) {
    Log.i("Rex", "Screen: HostDetailScreen for host $hostId")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val securityManager = remember {
        EntryPointAccessors.fromApplication(
            context,
            SecurityManagerEntryPoint::class.java
        ).securityManager()
    }

    var passwordText by remember { mutableStateOf("") }

    // Auto key onboarding state machine
    var onboardingStage by rememberSaveable(hostId, autoKeyOnboarding) {
        mutableStateOf(AutoKeyOnboardingStage.Idle)
    }

    // Auto key onboarding logic
    LaunchedEffect(autoKeyOnboarding, uiState.host?.keyBlobId, uiState.keyStatus, uiState.error) {
        if (!autoKeyOnboarding) return@LaunchedEffect
        val host = uiState.host ?: return@LaunchedEffect

        Log.d("Rex", "Auto-onboarding: stage=$onboardingStage, keyBlobId=${host.keyBlobId}, keyStatus=${uiState.keyStatus}, error=${uiState.error}")

        when (onboardingStage) {
            AutoKeyOnboardingStage.Idle -> {
                // Only start if no key exists
                if (host.keyBlobId == null) {
                    Log.d("Rex", "Auto-onboarding: Idle -> LaunchGenerate")
                    onboardingStage = AutoKeyOnboardingStage.LaunchGenerate
                } else {
                    Log.d("Rex", "Auto-onboarding: Idle -> Finished (key exists)")
                    onboardingStage = AutoKeyOnboardingStage.Finished
                }
            }
            AutoKeyOnboardingStage.LaunchGenerate -> {
                Log.d("Rex", "Auto-onboarding: LaunchGenerate -> AwaitKey")
                onboardingStage = AutoKeyOnboardingStage.AwaitKey
                viewModel.generateNewKey()
            }
            AutoKeyOnboardingStage.AwaitKey -> {
                // Check for cancellation or error
                if (uiState.error != null) {
                    Log.d("Rex", "Auto-onboarding: AwaitKey -> Finished (error: ${uiState.error})")
                    onboardingStage = AutoKeyOnboardingStage.Finished
                } else if (host.keyBlobId != null && uiState.keyStatus == "pending") {
                    Log.d("Rex", "Auto-onboarding: AwaitKey -> LaunchDeploy")
                    onboardingStage = AutoKeyOnboardingStage.LaunchDeploy
                }
            }
            AutoKeyOnboardingStage.LaunchDeploy -> {
                Log.d("Rex", "Auto-onboarding: LaunchDeploy -> Finished, calling showPasswordDialog()")
                onboardingStage = AutoKeyOnboardingStage.Finished
                viewModel.showPasswordDialog()
            }
            AutoKeyOnboardingStage.Finished -> {
                // Do nothing, onboarding complete
                Log.v("Rex", "Auto-onboarding: stage=Finished (no-op)")
            }
        }
    }

    // Handle snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.host?.nickname ?: "Host Details")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.host?.let { host ->
                        HostInfoCard(
                            host = host,
                            onEditHost = { onNavigateToEditHost(host.id) }
                        )
                        KeyManagementCard(
                            keyStatus = uiState.keyStatus,
                            keyProvisionedAt = uiState.keyProvisionedAt,
                            publicKeyPreview = uiState.publicKeyPreview,
                            hasKey = host.keyBlobId != null,
                            provisionInProgress = uiState.provisionInProgress,
                            testInProgress = uiState.testInProgress,
                            onGenerateKey = viewModel::generateNewKey,
                            onImportKey = viewModel::showImportDialog,
                            onDeployKey = viewModel::showPasswordDialog,
                            onTestKey = viewModel::testKeyAuth,
                            onDeleteKey = viewModel::showDeleteConfirmation
                        )

                        if (uiState.lastProvisionResult != null) {
                            ProvisionResultCard(
                                result = uiState.lastProvisionResult!!,
                                output = uiState.provisionOutput,
                                onClear = viewModel::clearProvisionOutput
                            )
                        }
                    }
                }
            }

            // Error Snackbar
            uiState.error?.let { error ->
                LaunchedEffect(error) {
                    // In a real app, you'd show a Snackbar here
                    Log.e("Rex", "HostDetail error: $error")
                }
            }
        }
    }

    // Password Dialog
    if (uiState.showPasswordDialog) {
        PasswordDialog(
            onDismiss = viewModel::hidePasswordDialog,
            onConfirm = { password ->
                viewModel.deployKey(password)
                passwordText = ""
            }
        )
    }

    // Import Key Dialog
    if (uiState.showImportDialog) {
        ImportKeyDialog(
            pemText = uiState.importPemText,
            error = uiState.importError,
            onDismiss = viewModel::hideImportDialog,
            onPemTextChange = viewModel::updateImportPemText,
            onImport = viewModel::importPrivateKey
        )
    }

    // Test Result Dialog
    if (uiState.showTestDialog) {
        TestResultDialog(
            inProgress = uiState.testInProgress,
            result = uiState.testResult,
            onDismiss = viewModel::hideTestDialog
        )
    }

    // Delete Confirmation Dialog
    if (uiState.showDeleteConfirmation) {
        DeleteKeyDialog(
            onDismiss = viewModel::hideDeleteConfirmation,
            onConfirm = viewModel::deleteKey
        )
    }

    // TOFU first-connection confirmation
    uiState.tofuPrompt?.let { prompt ->
        TofuConfirmationDialog(
            prompt = prompt,
            onTrust = viewModel::onTofuTrusted,
            onReject = viewModel::onTofuRejected
        )
    }

    // Security gate overlay
    if (uiState.showSecurityGate) {
        SecurityGate(
            title = uiState.securityGateTitle,
            subtitle = uiState.securityGateSubtitle,
            onAuthenticated = viewModel::onSecurityGateAuthenticated,
            onCancel = viewModel::onSecurityGateCancelled,
            securityManager = securityManager
        )
    }
}
