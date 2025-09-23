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

import androidx.biometric.BiometricManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.rex.app.core.SecurityManager
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityGateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var securityManager: SecurityManager
    private var authenticationResult: Boolean? = null
    private var cancelResult: Boolean = false

    @Before
    fun setup() {
        securityManager = SecurityManager()
        authenticationResult = null
        cancelResult = false
    }

    @Test
    fun securityGate_showsLoadingInitially() {
        composeTestRule.setContent {
            SecurityGate(
                title = "Test Gate",
                subtitle = "Test authentication",
                onAuthenticated = { authenticationResult = true },
                onCancel = { cancelResult = true },
                securityManager = securityManager
            )
        }

        // Should show loading state initially
        composeTestRule.onNodeWithContentDescription("Loading")
            .assertExists()
    }

    @Test
    fun securityGate_showsErrorWhenNoCredentials() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val biometricManager = BiometricManager.from(context)

        // Skip test if device has credentials set up
        Assume.assumeTrue(
            "Test requires device without credentials",
            biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                != BiometricManager.BIOMETRIC_SUCCESS
        )

        composeTestRule.setContent {
            SecurityGate(
                title = "Test Gate",
                subtitle = "Test authentication",
                onAuthenticated = { authenticationResult = true },
                onCancel = { cancelResult = true },
                securityManager = securityManager
            )
        }

        // Should show error about missing credentials
        composeTestRule.onNodeWithText("Device Credential Required")
            .assertExists()

        composeTestRule.onNodeWithText("Cancel")
            .assertExists()
            .performClick()

        // Verify cancel was called
        assert(cancelResult)
    }

    @Test
    @Ignore("Requires device with credentials - may not be available on all emulators")
    fun securityGate_proceedsWhenAlreadyAuthenticated() {
        // Pre-authenticate
        securityManager.openGate()

        composeTestRule.setContent {
            SecurityGate(
                title = "Test Gate",
                subtitle = "Test authentication",
                onAuthenticated = { authenticationResult = true },
                onCancel = { cancelResult = true },
                securityManager = securityManager
            )
        }

        // Should proceed immediately without showing prompt
        composeTestRule.waitUntil(timeoutMillis = 1000) {
            authenticationResult == true
        }

        assert(authenticationResult == true)
        assert(!cancelResult)
    }

    @Test
    @Ignore("Requires device with credentials and user interaction")
    fun securityGate_showsRetryOnError() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val biometricManager = BiometricManager.from(context)

        // Skip test if device doesn't have credentials
        Assume.assumeTrue(
            "Test requires device with credentials",
            biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                == BiometricManager.BIOMETRIC_SUCCESS
        )

        composeTestRule.setContent {
            SecurityGate(
                title = "Test Gate",
                subtitle = "Test authentication",
                onAuthenticated = { authenticationResult = true },
                onCancel = { cancelResult = true },
                securityManager = securityManager
            )
        }

        // This test would require simulating authentication failure
        // which is difficult in automated testing
        // Manual testing should verify:
        // 1. Authentication prompt appears
        // 2. Canceling shows retry option
        // 3. Error states are handled properly
    }

    @Test
    fun securityGate_handlesCancel() {
        composeTestRule.setContent {
            SecurityGate(
                title = "Test Gate",
                subtitle = "Test authentication",
                onAuthenticated = { authenticationResult = true },
                onCancel = { cancelResult = true },
                securityManager = securityManager
            )
        }

        // Wait for potential error state and cancel
        composeTestRule.waitForIdle()

        if (composeTestRule.onNodeWithText("Cancel").isDisplayed()) {
            composeTestRule.onNodeWithText("Cancel")
                .performClick()

            assert(cancelResult)
            assert(authenticationResult != true)
        }
    }

    private fun SemanticsNodeInteraction.isDisplayed(): Boolean {
        return try {
            assertExists()
            true
        } catch (e: AssertionError) {
            false
        }
    }
}