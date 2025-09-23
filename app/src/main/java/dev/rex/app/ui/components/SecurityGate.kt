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

package dev.rex.app.ui.components

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.rex.app.core.SecurityManager

/**
 * Security gate composable that requires device credential authentication
 * for sensitive operations. Implements fail-closed security - denies access
 * if device credentials are not available.
 */
@Composable
fun SecurityGate(
    title: String,
    subtitle: String,
    onAuthenticated: () -> Unit,
    onCancel: () -> Unit,
    securityManager: SecurityManager
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var authState by remember { mutableStateOf(AuthState.CHECKING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Check if gate is already open (within TTL)
        if (securityManager.isGateOpen()) {
            Log.d("Rex", "Security gate already open, proceeding")
            onAuthenticated()
            return@LaunchedEffect
        }

        // Check device capability
        val biometricManager = BiometricManager.from(context)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                authState = AuthState.READY
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                authState = AuthState.NO_CREDENTIAL
                errorMessage = "No device credential (PIN, pattern, or password) is set up on this device"
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                authState = AuthState.NO_CREDENTIAL
                errorMessage = "Device credential authentication is not available"
            }
            else -> {
                authState = AuthState.NO_CREDENTIAL
                errorMessage = "Device credential authentication is not supported"
            }
        }
    }

    when (authState) {
        AuthState.CHECKING -> {
            // Show loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        AuthState.NO_CREDENTIAL -> {
            // Fail-closed: Show error and block access
            SecurityGateError(
                title = "Device Credential Required",
                message = errorMessage ?: "Device credential authentication is required for this operation",
                onCancel = onCancel
            )
        }

        AuthState.READY -> {
            // Show authentication prompt
            LaunchedEffect(Unit) {
                if (activity != null) {
                    showBiometricPrompt(
                        activity = activity,
                        title = title,
                        subtitle = subtitle,
                        onSuccess = {
                            Log.d("Rex", "Security gate authentication successful")
                            securityManager.openGate()
                            onAuthenticated()
                        },
                        onError = { error ->
                            Log.w("Rex", "Security gate authentication failed: $error")
                            errorMessage = error
                            authState = AuthState.ERROR
                        },
                        onCancel = onCancel
                    )
                } else {
                    Log.e("Rex", "Activity is null, cannot show biometric prompt")
                    errorMessage = "Unable to show authentication prompt"
                    authState = AuthState.ERROR
                }
            }
        }

        AuthState.ERROR -> {
            // Show error with retry option
            SecurityGateError(
                title = "Authentication Error",
                message = errorMessage ?: "Authentication failed",
                onCancel = onCancel,
                showRetry = true,
                onRetry = {
                    // Clean reset to initial state
                    authState = AuthState.CHECKING
                    errorMessage = null
                }
            )
        }
    }
}

@Composable
private fun SecurityGateError(
    title: String,
    message: String,
    onCancel: () -> Unit,
    showRetry: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }

            if (showRetry && onRetry != null) {
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)

    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onCancel()
                    else -> onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Authentication failed. Please try again.")
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()

    biometricPrompt.authenticate(promptInfo)
}

private enum class AuthState {
    CHECKING,
    READY,
    NO_CREDENTIAL,
    ERROR
}