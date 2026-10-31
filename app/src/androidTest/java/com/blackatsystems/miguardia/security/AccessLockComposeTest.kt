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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.ColorUtils
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            AndroidFontScaleTheme(fontScale = TestFontScale.EXTRA_LARGE) {
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
    fun authenticationInProgressKeepsTheGateClosedAndTheDisabledButtonIndicatorVisible() {
        var dark by mutableStateOf(false)
        var unlocks = 0
        var sensitiveCompositions = 0
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark) {
                AccessLockContentGate(
                    state = enabledState(locked = true).copy(authenticationInProgress = true),
                    capability = DeviceAuthenticationCapability.AVAILABLE,
                    onUnlock = { unlocks++ },
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

        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            val button = compose.onNodeWithTag("access-lock-unlock")
                .performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            val indicator = compose.onNodeWithTag("access-lock-authenticating", useUnmergedTree = true)
                .assertIsDisplayed()
            val buttonImage = button.captureToImage().asAndroidBitmap()
            val indicatorImage = indicator.captureToImage().asAndroidBitmap()
            try {
                // Sample the actual disabled container away from the centered indicator.
                // Contrast comes from rendered pixels, independent of the selected token.
                val background = buttonImage.getPixel(buttonImage.width / 4, buttonImage.height / 2)
                var maximumContrast = 1.0
                for (y in 0 until indicatorImage.height) for (x in 0 until indicatorImage.width) {
                    val pixel = indicatorImage.getPixel(x, y)
                    if ((pixel ushr 24) == 0xFF) {
                        maximumContrast = maxOf(maximumContrast, ColorUtils.calculateContrast(pixel, background))
                    }
                }
                assertTrue("dark=$theme indicator contrast=$maximumContrast", maximumContrast >= 3.0)
            } finally {
                buttonImage.recycle()
                indicatorImage.recycle()
            }
            compose.onNodeWithTag("sensitive-content").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(0, unlocks)
                assertEquals(0, sensitiveCompositions)
            }
        }
    }

    @Test
    fun storeErrorStaysGenericAndRepairRequiresSecureDeviceCapability() {
        var deviceSecurityRetries = 0
        compose.setContent {
            AndroidFontScaleTheme {
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
            AndroidFontScaleTheme {
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
            AndroidFontScaleTheme {
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
