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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.rex.app.core.SecurityManager
import dev.rex.app.ui.components.SecurityGate
import java.text.SimpleDateFormat
import java.util.*

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
                            uiState = uiState,
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

@Composable
private fun HostInfoCard(
    host: dev.rex.app.data.db.HostEntity,
    onEditHost: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Host Information",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = onEditHost,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit host configuration",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row {
                Text(
                    text = "Hostname: ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${host.hostname}:${host.port}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row {
                Text(
                    text = "Username: ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = host.username,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row {
                Text(
                    text = "Auth Method: ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = host.authMethod,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun KeyManagementCard(
    uiState: HostDetailUiState,
    onGenerateKey: () -> Unit,
    onImportKey: () -> Unit,
    onDeployKey: () -> Unit,
    onTestKey: () -> Unit,
    onDeleteKey: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SSH Key Management",
                style = MaterialTheme.typography.titleMedium
            )

            // Key Status
            KeyStatusSection(
                status = uiState.keyStatus,
                provisionedAt = uiState.keyProvisionedAt,
                publicKey = uiState.publicKeyPreview
            )

            // Action Buttons
            KeyActionButtons(
                keyStatus = uiState.keyStatus,
                hasKey = uiState.host?.keyBlobId != null,
                provisionInProgress = uiState.provisionInProgress,
                testInProgress = uiState.testInProgress,
                onGenerateKey = onGenerateKey,
                onImportKey = onImportKey,
                onDeployKey = onDeployKey,
                onTestKey = onTestKey,
                onDeleteKey = onDeleteKey
            )
        }
    }
}

@Composable
private fun KeyStatusSection(
    status: String,
    provisionedAt: Long?,
    publicKey: String?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Status: ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            KeyStatusChip(status = status)
        }

        provisionedAt?.let { timestamp ->
            val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            Text(
                text = "Last provisioned: ${formatter.format(Date(timestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        publicKey?.let { key ->
            PublicKeyPreview(publicKey = key)
        }
    }
}

@Composable
private fun KeyStatusChip(status: String) {
    val (text, color) = when (status) {
        "none" -> "No Key" to MaterialTheme.colorScheme.surfaceVariant
        "pending" -> "Pending Deployment" to MaterialTheme.colorScheme.secondary
        "success" -> "Deployed" to MaterialTheme.colorScheme.primary
        "failed" -> "Deployment Failed" to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.surfaceVariant
    }

    FilterChip(
        onClick = { },
        label = { Text(text) },
        selected = false,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = color,
            labelColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun PublicKeyPreview(publicKey: String) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Public Key",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(publicKey))
                }
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy public key",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Text(
            text = publicKey,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeyActionButtons(
    keyStatus: String,
    hasKey: Boolean,
    provisionInProgress: Boolean,
    testInProgress: Boolean,
    onGenerateKey: () -> Unit,
    onImportKey: () -> Unit,
    onDeployKey: () -> Unit,
    onTestKey: () -> Unit,
    onDeleteKey: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!hasKey) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGenerateKey,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.VpnKey, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Key")
                }

                OutlinedButton(
                    onClick = onImportKey,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Key")
                }
            }
        } else {
            // Key exists, show deployment and management actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDeployKey,
                    enabled = !provisionInProgress && keyStatus != "success",
                    modifier = Modifier.weight(1f)
                ) {
                    if (provisionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (provisionInProgress) "Deploying..." else "Deploy Key")
                }

                OutlinedButton(
                    onClick = onTestKey,
                    enabled = !testInProgress && keyStatus == "success",
                    modifier = Modifier.weight(1f)
                ) {
                    if (testInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Security, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (testInProgress) "Testing..." else "Test Auth")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onGenerateKey,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rotate Key")
                }

                OutlinedButton(
                    onClick = onDeleteKey,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Key")
                }
            }
        }
    }
}

@Composable
private fun ProvisionResultCard(
    result: dev.rex.app.data.ssh.ProvisionResult,
    output: String,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.success)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (result.success) "Deployment Successful" else "Deployment Failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result.success)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )

                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                }
            }

            Text(
                text = "Duration: ${result.durationMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            result.errorMessage?.let { error ->
                Text(
                    text = "Error: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (output.isNotBlank()) {
                Text(
                    text = "Output:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = output,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter SSH Password") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Enter the SSH password for automated key deployment:")

                OutlinedTextField(
                    value = password,
                    onValueChange = { input ->
                        password = input.filterNot { it == '\n' || it == '\r' }
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        val icon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        val description = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(icon, contentDescription = description)
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) {
                Text("Deploy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ImportKeyDialog(
    pemText: String,
    error: String?,
    onDismiss: () -> Unit,
    onPemTextChange: (String) -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Private Key") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Paste your OpenSSH private key in PEM format:")

                OutlinedTextField(
                    value = pemText,
                    onValueChange = onPemTextChange,
                    label = { Text("Private Key (PEM)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 10,
                    isError = error != null
                )

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onImport,
                enabled = pemText.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TestResultDialog(
    inProgress: Boolean,
    result: dev.rex.app.data.ssh.ProvisionResult?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = if (inProgress) { {} } else onDismiss,
        title = { Text("Authentication Test") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (inProgress) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Testing SSH key authentication...")
                    }
                } else {
                    result?.let { testResult ->
                        Text(
                            text = if (testResult.success) "✓ Authentication successful!" else "✗ Authentication failed",
                            color = if (testResult.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )

                        Text(
                            text = "Duration: ${testResult.durationMs}ms",
                            style = MaterialTheme.typography.bodySmall
                        )

                        testResult.errorMessage?.let { error ->
                            Text(
                                text = "Error: $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (testResult.stdout.isNotBlank()) {
                            Text(
                                text = "Output: ${testResult.stdout}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!inProgress) {
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun DeleteKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete SSH Key") },
        text = {
            Text("Are you sure you want to delete this SSH key? This action cannot be undone and you will need to generate or import a new key for this host.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
