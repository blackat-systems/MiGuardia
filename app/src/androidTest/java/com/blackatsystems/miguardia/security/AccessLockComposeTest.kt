package com.blackatsystems.miguardia.security

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccessLockComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun closedGateDoesNotComposeSensitiveContentAndReturnsToItAfterSuccess() {
        var state by mutableStateOf(enabledState(locked = true))
        var sensitiveCompositions = 0
        compose.setContent {
            MiGuardiaTheme(appZoom = AppZoom.EXTRA_LARGE) {
                AccessLockContentGate(
                    state = state,
                    capability = DeviceAuthenticationCapability.AVAILABLE,
                    onUnlock = {},
                    onRetryStore = {},
                    onRepairStore = {},
                    onRetryDeviceSecurity = {},
                    onOpenDeviceSecurity = {},
                ) {
                    sensitiveCompositions++
                    Text("SENSITIVE CALENDAR", modifier = Modifier.testTag("sensitive-content"))
                }
            }
        }

        compose.onNodeWithTag("access-lock-gate").assertIsDisplayed()
        compose.onNodeWithText("MiGuardia está bloqueada").assertIsDisplayed()
        compose.onNodeWithTag("sensitive-content").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, sensitiveCompositions) }

        compose.runOnIdle { state = enabledState(locked = false) }

        compose.onNodeWithTag("sensitive-content").assertIsDisplayed()
    }

    @Test
    fun storeErrorStaysGenericAndRepairRequiresSecureDeviceCapability() {
        var deviceSecurityRetries = 0
        compose.setContent {
            MiGuardiaTheme {
                AccessLockContentGate(
                    state = AccessLockState(phase = AccessLockPhase.STORE_ERROR),
                    capability = DeviceAuthenticationCapability.NO_SECURE_CREDENTIAL,
                    onUnlock = {},
                    onRetryStore = {},
                    onRepairStore = {},
                    onRetryDeviceSecurity = { deviceSecurityRetries++ },
                    onOpenDeviceSecurity = {},
                ) { Text("SENSITIVE CALENDAR") }
            }
        }

        compose.onNodeWithText("No pudimos leer la configuración del bloqueo").assertIsDisplayed()
        compose.onNodeWithTag("access-lock-retry-store").assertIsEnabled()
        compose.onNodeWithTag("access-lock-repair-store").assertIsNotEnabled()
        compose.onNodeWithTag("access-lock-retry-device-security").performClick()
        compose.runOnIdle { assertEquals(1, deviceSecurityRetries) }
        compose.onNodeWithText("SENSITIVE CALENDAR").assertDoesNotExist()
    }

    @Test
    fun settingsExposeFourExactTimeoutsAndProtectActivationAndChanges() {
        var activated: AccessLockTimeout? = null
        var changed: AccessLockTimeout? = null
        var state by mutableStateOf(disabledState())
        compose.setContent {
            MiGuardiaTheme {
                AccessLockSettingsScreen(
                    state = state,
                    capability = DeviceAuthenticationCapability.AVAILABLE,
                    contentPadding = PaddingValues(0.dp),
                    onActivate = { activated = it },
                    onChangeTimeout = { changed = it },
                    onDisable = {},
                    onLockNow = {},
                    onRetryDeviceSecurity = {},
                    onOpenDeviceSecurity = {},
                )
            }
        }

        listOf(
            "Inmediatamente",
            "Después de 1 minuto",
            "Después de 5 minutos",
            "Después de 15 minutos",
        ).forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithTag("access-lock-timeout-five_minutes").performScrollTo().performClick()
        compose.onNodeWithTag("access-lock-enabled-control").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AccessLockTimeout.FIVE_MINUTES, activated) }

        compose.runOnIdle { state = enabledState(locked = false) }
        compose.onNodeWithTag("access-lock-timeout-fifteen_minutes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AccessLockTimeout.FIFTEEN_MINUTES, changed) }
    }

    @Test
    fun unlockRestoresTheExistingSaveableSurfaceAndDraftWithinTheLiveProcess() {
        var state by mutableStateOf(enabledState(locked = false))
        compose.setContent {
            MiGuardiaTheme {
                AccessLockContentGate(
                    state = state,
                    capability = DeviceAuthenticationCapability.AVAILABLE,
                    onUnlock = {},
                    onRetryStore = {},
                    onRepairStore = {},
                    onRetryDeviceSecurity = {},
                    onOpenDeviceSecurity = {},
                ) {
                    var draft by rememberSaveable { mutableStateOf("Resumen") }
                    androidx.compose.material3.Button(
                        onClick = { draft = "Detalle vigente" },
                        modifier = Modifier.testTag("sensitive-draft"),
                    ) { Text(draft) }
                }
            }
        }

        compose.onNodeWithTag("sensitive-draft").performClick()
        compose.onNodeWithText("Detalle vigente").assertIsDisplayed()
        compose.runOnIdle { state = enabledState(locked = true) }
        compose.onNodeWithText("Detalle vigente").assertDoesNotExist()
        compose.runOnIdle { state = enabledState(locked = false) }

        compose.onNodeWithText("Detalle vigente").assertIsDisplayed()
    }

    private fun enabledState(locked: Boolean) = AccessLockState(
        phase = AccessLockPhase.READY,
        configuration = AccessLockConfiguration(true, AccessLockTimeout.FIVE_MINUTES),
        locked = locked,
        privacyCoverVisible = false,
    )

    private fun disabledState() = AccessLockState(
        phase = AccessLockPhase.READY,
        configuration = AccessLockConfiguration(),
        locked = false,
        privacyCoverVisible = false,
    )
}
