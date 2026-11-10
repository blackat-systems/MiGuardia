package com.blackatsystems.miguardia.stage14

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.security.AccessLockConfiguration
import com.blackatsystems.miguardia.security.AccessLockContentGate
import com.blackatsystems.miguardia.security.AccessLockMessage
import com.blackatsystems.miguardia.security.AccessLockPhase
import com.blackatsystems.miguardia.security.AccessLockSettingsScreen
import com.blackatsystems.miguardia.security.AccessLockState
import com.blackatsystems.miguardia.security.AccessLockTimeout
import com.blackatsystems.miguardia.security.DeviceAuthenticationCapability
import com.blackatsystems.miguardia.stage12.stage12VisibleOverflow
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Etapa 14 · Bloqueo y privacidad de entrada, como prueba de pantalla.
 * Estado y acciones ficticios en memoria: no lee preferencias, no autentica y no abre MainActivity.
 */
class Stage14AccessLockScreensTest {
    @get:Rule
    val compose = createComposeRule()

    // A256, A257
    @Test
    fun closedGateShowsTheOwnMessageOfEachAuthenticationErrorAndStaysClosed() {
        assertEquals(AccessLockMessage.entries.toSet(), MessageTexts.keys)
        var state by mutableStateOf(lockedState())
        val calls = GateCalls()
        compose.setContent {
            AndroidFontScaleTheme { Gate(state, DeviceAuthenticationCapability.AVAILABLE, calls) }
        }

        compose.onNodeWithTag(MESSAGE).assertDoesNotExist()
        MessageTexts.forEach { (message, text) ->
            compose.runOnIdle { state = lockedState().copy(message = message) }

            compose.onNodeWithTag(MESSAGE).performScrollTo().assertIsDisplayed().assertTextEquals(text)
            compose.onNodeWithText("MiGuardia está bloqueada").assertExists()
            compose.onNodeWithTag(UNLOCK).performScrollTo().assertIsEnabled()
                .assertTextEquals("Desbloquear MiGuardia")
            compose.onNodeWithTag(SENSITIVE).assertDoesNotExist()
        }
        // The read-error door reports its own failures with the same visible message.
        compose.runOnIdle {
            state = AccessLockState(
                phase = AccessLockPhase.STORE_ERROR,
                message = AccessLockMessage.STORE_WRITE_ERROR,
            )
        }
        compose.onNodeWithTag(MESSAGE).performScrollTo().assertIsDisplayed()
            .assertTextEquals(MessageTexts.getValue(AccessLockMessage.STORE_WRITE_ERROR))
        compose.runOnIdle { state = lockedState() }
        compose.onNodeWithTag(MESSAGE).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, calls.sensitiveCompositions) }
    }

    // A257
    @Test
    fun settingsShowTheOwnMessageOfEachAuthenticationError() {
        var state by mutableStateOf(openState())
        compose.setContent {
            AndroidFontScaleTheme { Settings(state, DeviceAuthenticationCapability.AVAILABLE, SettingsCalls()) }
        }

        compose.onNodeWithTag(MESSAGE).assertDoesNotExist()
        MessageTexts.forEach { (message, text) ->
            compose.runOnIdle { state = openState().copy(message = message) }

            compose.onNodeWithTag(MESSAGE).performScrollTo().assertIsDisplayed().assertTextEquals(text)
            compose.onNodeWithTag(STATUS).performScrollTo().assertTextEquals("Activado")
        }
    }

    // A246, A257
    @Test
    fun closedGateWithoutUsableDeviceSecurityExplainsItBlocksUnlockAndOpensSecurity() {
        var capability by mutableStateOf(DeviceAuthenticationCapability.AVAILABLE)
        val calls = GateCalls()
        compose.setContent { AndroidFontScaleTheme { Gate(lockedState(), capability, calls) } }

        compose.onNodeWithTag(CAPABILITY).performScrollTo()
            .assertTextEquals("El teléfono tiene configurada una credencial segura compatible.")
        compose.onNodeWithTag(OPEN_SECURITY).assertDoesNotExist()
        compose.onNodeWithTag(RETRY_SECURITY).assertDoesNotExist()
        compose.onNodeWithTag(UNLOCK).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, calls.unlocks) }

        UnavailableTexts.entries.forEachIndexed { index, (unavailable, text) ->
            compose.runOnIdle { capability = unavailable }

            compose.onNodeWithTag(CAPABILITY).performScrollTo().assertIsDisplayed().assertTextEquals(text)
            compose.onNodeWithTag(UNLOCK).performScrollTo().assertIsNotEnabled().performClick()
            compose.onNodeWithTag(RETRY_SECURITY).performScrollTo().assertIsEnabled()
                .assertTextEquals("Volver a comprobar").performClick()
            compose.onNodeWithTag(OPEN_SECURITY).performScrollTo().assertIsEnabled()
                .assertTextEquals("Abrir seguridad del teléfono").performClick()
            compose.runOnIdle {
                assertEquals("$unavailable", 1, calls.unlocks)
                assertEquals("$unavailable", index + 1, calls.securityRetries)
                assertEquals("$unavailable", index + 1, calls.securityOpens)
                assertEquals("$unavailable", 0, calls.sensitiveCompositions)
            }
            compose.onNodeWithTag(SENSITIVE).assertDoesNotExist()
        }
    }

    // A246, A257
    @Test
    fun settingsWithoutUsableDeviceSecurityBlockActivationKeepLockNowAndOpenSecurity() {
        var capability by mutableStateOf(DeviceAuthenticationCapability.NO_SECURE_CREDENTIAL)
        var state by mutableStateOf(disabledState())
        val calls = SettingsCalls()
        compose.setContent { AndroidFontScaleTheme { Settings(state, capability, calls) } }

        UnavailableTexts.entries.forEachIndexed { index, (unavailable, text) ->
            compose.runOnIdle {
                capability = unavailable
                state = disabledState()
            }
            compose.onNodeWithTag(STATUS).performScrollTo().assertTextEquals("Desactivado")
            compose.onNodeWithTag(CAPABILITY).performScrollTo().assertIsDisplayed().assertTextEquals(text)
            compose.onNodeWithTag(ENABLED_CONTROL).performScrollTo().assertIsNotEnabled()
                .assertTextEquals("Activar bloqueo").performClick()
            // Choosing a timeout is still a harmless draft; it activates nothing.
            compose.onNodeWithTag(timeoutTag(AccessLockTimeout.FIVE_MINUTES)).performScrollTo()
                .performClick().assertIsSelected()
            compose.onNodeWithTag(OPEN_SECURITY).performScrollTo().assertIsEnabled()
                .assertTextEquals("Abrir seguridad del teléfono").performClick()
            compose.onNodeWithTag(RETRY_SECURITY).performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle {
                assertNull("$unavailable", calls.activated)
                assertTrue("$unavailable", calls.changed.isEmpty())
                assertEquals("$unavailable", index + 1, calls.securityOpens)
                assertEquals("$unavailable", index + 1, calls.securityRetries)
            }

            // A lock that was already enabled cannot be disabled, but can always be closed.
            compose.runOnIdle { state = openState() }
            compose.onNodeWithTag(ENABLED_CONTROL).performScrollTo().assertIsNotEnabled()
                .assertTextEquals("Desactivar bloqueo").performClick()
            compose.onNodeWithTag(LOCK_NOW).performScrollTo().assertIsEnabled()
                .assertTextEquals("Bloquear ahora").performClick()
            compose.runOnIdle {
                assertEquals("$unavailable", 0, calls.disables)
                assertEquals("$unavailable", index + 1, calls.lockNows)
            }
        }
    }

    // A258
    @Test
    fun readErrorDoorCallsRetryAndRepairAndIgnoresThemWhileBusy() {
        var state by mutableStateOf(AccessLockState(phase = AccessLockPhase.STORE_ERROR))
        val calls = GateCalls()
        compose.setContent {
            AndroidFontScaleTheme { Gate(state, DeviceAuthenticationCapability.AVAILABLE, calls) }
        }

        compose.onNodeWithText("No pudimos leer la configuración del bloqueo").performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag(UNLOCK).assertDoesNotExist()
        compose.onNodeWithTag(RETRY_STORE).performScrollTo().assertIsEnabled()
            .assertTextEquals("Reintentar").performClick()
        compose.runOnIdle {
            assertEquals(1, calls.storeRetries)
            assertEquals(0, calls.repairs)
        }
        compose.onNodeWithTag(REPAIR_STORE).performScrollTo().assertIsEnabled()
            .assertTextEquals("Restablecer bloqueo").performClick()
        compose.runOnIdle {
            assertEquals(1, calls.storeRetries)
            assertEquals(1, calls.repairs)
        }

        listOf(
            AccessLockState(phase = AccessLockPhase.STORE_ERROR, authenticationInProgress = true),
            AccessLockState(phase = AccessLockPhase.STORE_ERROR, persistenceInProgress = true),
        ).forEach { busy ->
            compose.runOnIdle { state = busy }
            compose.onNodeWithTag(RETRY_STORE).performScrollTo().assertIsNotEnabled().performClick()
            compose.onNodeWithTag(REPAIR_STORE).performScrollTo().assertIsNotEnabled().performClick()
        }
        compose.runOnIdle {
            assertEquals(1, calls.storeRetries)
            assertEquals(1, calls.repairs)
            assertEquals(0, calls.unlocks)
            assertEquals(0, calls.sensitiveCompositions)
        }
        compose.onNodeWithTag(SENSITIVE).assertDoesNotExist()
    }

    // A259
    @Test
    fun recoveryLoadingAndPrivacyCoverAreDrawnWithoutComposingSensitiveContent() {
        var state by mutableStateOf(AccessLockState())
        val calls = GateCalls()
        compose.setContent {
            AndroidFontScaleTheme { Gate(state, DeviceAuthenticationCapability.AVAILABLE, calls) }
        }

        val unlockedButCovered = openState().copy(privacyCoverVisible = true)
        assertFalse(unlockedButCovered.locked)
        listOf(
            AccessLockState(phase = AccessLockPhase.WAITING_FOR_RECOVERY) to LOADING,
            AccessLockState(phase = AccessLockPhase.LOADING) to LOADING,
            // Even a state that would be open once READY stays covered while it is being read.
            disabledState().copy(phase = AccessLockPhase.LOADING) to LOADING,
            disabledState().copy(phase = AccessLockPhase.WAITING_FOR_RECOVERY) to LOADING,
            unlockedButCovered to PRIVACY_COVER,
            disabledState().copy(privacyCoverVisible = true) to PRIVACY_COVER,
        ).forEach { (covered, tag) ->
            compose.runOnIdle { state = covered }

            compose.onNodeWithTag("access-lock-gate").assertIsDisplayed()
            compose.onNodeWithTag(tag).assertIsDisplayed()
            compose.onNodeWithText("Protegiendo MiGuardia…").assertIsDisplayed()
            // A cover is not a lock: it offers no action and no error.
            compose.onNodeWithTag(UNLOCK).assertDoesNotExist()
            compose.onNodeWithTag(RETRY_STORE).assertDoesNotExist()
            compose.onNodeWithTag(MESSAGE).assertDoesNotExist()
            compose.onNodeWithTag(SENSITIVE).assertDoesNotExist()
            compose.onNodeWithText(SENSITIVE_TEXT).assertDoesNotExist()
            compose.runOnIdle { assertEquals("$covered", 0, calls.sensitiveCompositions) }
        }

        compose.runOnIdle { state = openState() }

        compose.onNodeWithTag(SENSITIVE).assertIsDisplayed()
        compose.onNodeWithTag(PRIVACY_COVER).assertDoesNotExist()
        compose.onNodeWithTag(LOADING).assertDoesNotExist()

        // Losing privacy again (pause, Recientes) removes the content from the composition.
        compose.runOnIdle { state = unlockedButCovered }

        compose.onNodeWithTag(PRIVACY_COVER).assertIsDisplayed()
        compose.onNodeWithTag(SENSITIVE).assertDoesNotExist()
        compose.onNodeWithText(SENSITIVE_TEXT).assertDoesNotExist()
    }

    // A254
    @Test
    fun enabledSettingsCallDisableOnceAndNeverActivateAndBlockItWhileBusy() {
        var state by mutableStateOf(openState())
        val calls = SettingsCalls()
        compose.setContent {
            AndroidFontScaleTheme { Settings(state, DeviceAuthenticationCapability.AVAILABLE, calls) }
        }

        compose.onNodeWithTag(STATUS).performScrollTo().assertTextEquals("Activado")
        compose.onNodeWithTag(ENABLED_CONTROL).performScrollTo().assertIsEnabled()
            .assertTextEquals("Desactivar bloqueo").performClick()
        compose.runOnIdle {
            assertEquals(1, calls.disables)
            assertNull(calls.activated)
            assertTrue(calls.changed.isEmpty())
            assertEquals(0, calls.lockNows)
        }

        listOf(
            openState().copy(authenticationInProgress = true),
            openState().copy(persistenceInProgress = true),
        ).forEach { busy ->
            compose.runOnIdle { state = busy }
            compose.onNodeWithTag(ENABLED_CONTROL).performScrollTo().assertIsNotEnabled().performClick()
            compose.onNodeWithTag(LOCK_NOW).performScrollTo().assertIsNotEnabled().performClick()
        }
        compose.runOnIdle {
            assertEquals(1, calls.disables)
            assertEquals(0, calls.lockNows)
        }

        // Once the coordinator publishes the disabled lock, the same control offers to activate.
        compose.runOnIdle { state = disabledState() }
        compose.onNodeWithTag(STATUS).performScrollTo().assertTextEquals("Desactivado")
        compose.onNodeWithTag(ENABLED_CONTROL).performScrollTo().assertIsEnabled()
            .assertTextEquals("Activar bloqueo")
        compose.onNodeWithTag(LOCK_NOW).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, calls.disables) }
    }

    // A247–A254 (letra al 200% y ambos temas)
    @Test
    fun settingsAtDoubleFontKeepFourTimeoutsAndButtonsReachableWithWholeTextInBothThemes() {
        var dark by mutableStateOf(false)
        var state by mutableStateOf(openState())
        val calls = SettingsCalls()
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark, fontScale = TestFontScale.EXTRA_LARGE) {
                Settings(state, DeviceAuthenticationCapability.AVAILABLE, calls)
            }
        }

        listOf(false, true).forEachIndexed { round, theme ->
            val context = "dark=$theme"
            compose.runOnIdle {
                dark = theme
                state = openState()
                calls.changed.clear()
                calls.activated = null
            }
            assertWholeText(STATUS, "Activado", context)
            assertWholeText(
                CAPABILITY,
                "El teléfono tiene configurada una credencial segura compatible.",
                context,
            )
            assertWholeText(ENABLED_CONTROL, "Desactivar bloqueo", context)
            assertWholeText(LOCK_NOW, "Bloquear ahora", context)
            TimeoutTexts.forEach { (timeout, text) ->
                assertWholeText(timeoutTag(timeout), text, context)
                compose.onNodeWithTag(timeoutTag(timeout)).performScrollTo().performClick()
            }
            compose.onNodeWithTag(ENABLED_CONTROL).performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithTag(LOCK_NOW).performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle {
                assertEquals(context, AccessLockTimeout.entries.toList(), calls.changed)
                assertEquals(context, round + 1, calls.disables)
                assertEquals(context, round + 1, calls.lockNows)
                assertNull(context, calls.activated)
            }

            // Disabled lock: same four choices as a draft, and the chosen one travels with «Activar».
            compose.runOnIdle {
                state = disabledState()
                calls.changed.clear()
            }
            assertWholeText(STATUS, "Desactivado", context)
            assertWholeText(ENABLED_CONTROL, "Activar bloqueo", context)
            compose.onNodeWithTag(LOCK_NOW).assertDoesNotExist()
            TimeoutTexts.forEach { (timeout, text) -> assertWholeText(timeoutTag(timeout), text, context) }
            compose.onNodeWithTag(timeoutTag(AccessLockTimeout.IMMEDIATE)).performScrollTo().assertIsSelected()
            compose.onNodeWithTag(timeoutTag(AccessLockTimeout.FIFTEEN_MINUTES)).performScrollTo()
                .performClick().assertIsSelected()
            compose.onNodeWithTag(ENABLED_CONTROL).performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle {
                assertEquals(context, AccessLockTimeout.FIFTEEN_MINUTES, calls.activated)
                assertTrue(context, calls.changed.isEmpty())
                assertEquals(context, round + 1, calls.disables)
            }
        }
    }

    // A256–A258 (letra al 200% y ambos temas)
    @Test
    fun gateInErrorAtDoubleFontKeepsMessagesAndButtonsReachableWithWholeTextInBothThemes() {
        var dark by mutableStateOf(false)
        var state by mutableStateOf(lockedState())
        var capability by mutableStateOf(DeviceAuthenticationCapability.AVAILABLE)
        val calls = GateCalls()
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark, fontScale = TestFontScale.EXTRA_LARGE) {
                Gate(state, capability, calls)
            }
        }

        listOf(false, true).forEachIndexed { round, theme ->
            val context = "dark=$theme"
            // Closed lock after a failed authentication on a phone that answers.
            compose.runOnIdle {
                dark = theme
                capability = DeviceAuthenticationCapability.AVAILABLE
                state = lockedState().copy(message = AccessLockMessage.LOCKOUT)
            }
            assertWholeText(MESSAGE, MessageTexts.getValue(AccessLockMessage.LOCKOUT), context)
            assertWholeText(UNLOCK, "Desbloquear MiGuardia", context)
            compose.onNodeWithTag(UNLOCK).performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle { assertEquals(context, round + 1, calls.unlocks) }

            // Closed lock on a phone that lost or cannot use its secure credential.
            UnavailableTexts.entries.forEachIndexed { index, (unavailable, text) ->
                compose.runOnIdle {
                    capability = unavailable
                    state = lockedState().copy(message = AccessLockMessage.HARDWARE_UNAVAILABLE)
                }
                assertWholeText(
                    MESSAGE,
                    MessageTexts.getValue(AccessLockMessage.HARDWARE_UNAVAILABLE),
                    "$context $unavailable",
                )
                assertWholeText(CAPABILITY, text, "$context $unavailable")
                assertWholeText(UNLOCK, "Desbloquear MiGuardia", "$context $unavailable")
                assertWholeText(RETRY_SECURITY, "Volver a comprobar", "$context $unavailable")
                assertWholeText(OPEN_SECURITY, "Abrir seguridad del teléfono", "$context $unavailable")
                compose.onNodeWithTag(OPEN_SECURITY).performScrollTo().performClick()
                compose.runOnIdle {
                    assertEquals("$context $unavailable", round * 3 + index + 1, calls.securityOpens)
                }
            }

            // Read-error door with every action it can offer at once.
            compose.runOnIdle {
                capability = DeviceAuthenticationCapability.NO_SECURE_CREDENTIAL
                state = AccessLockState(
                    phase = AccessLockPhase.STORE_ERROR,
                    message = AccessLockMessage.STORE_WRITE_ERROR,
                )
            }
            assertWholeText(MESSAGE, MessageTexts.getValue(AccessLockMessage.STORE_WRITE_ERROR), context)
            assertWholeText(RETRY_STORE, "Reintentar", context)
            assertWholeText(REPAIR_STORE, "Restablecer bloqueo", context)
            assertWholeText(RETRY_SECURITY, "Volver a comprobar", context)
            assertWholeText(OPEN_SECURITY, "Abrir seguridad del teléfono", context)
            compose.onNodeWithTag(RETRY_STORE).performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithTag(RETRY_SECURITY).performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithTag(OPEN_SECURITY).performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle {
                assertEquals(context, round + 1, calls.storeRetries)
                assertEquals(context, round + 1, calls.securityRetries)
                assertEquals(context, round * 3 + 3, calls.securityOpens)
            }
            compose.runOnIdle { capability = DeviceAuthenticationCapability.AVAILABLE }
            compose.onNodeWithTag(REPAIR_STORE).performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle {
                assertEquals(context, round + 1, calls.repairs)
                assertEquals(context, 0, calls.sensitiveCompositions)
            }
            compose.onNodeWithTag(SENSITIVE).assertDoesNotExist()
        }
    }

    /** The tagged control is scrolled into view and its text is complete, unclipped and inside it. */
    private fun assertWholeText(tag: String, expected: String, context: String) {
        val label = "$context $tag"
        val owner = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        val ownerBounds = owner.fetchSemanticsNode().boundsInRoot
        val text = compose.onNode(
            hasText(expected) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))),
            useUnmergedTree = true,
        )
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Sin texto medido: $label", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertEquals(label, expected, layout.layoutInput.text.text)
            val lines = layout.lineCount
            assertTrue(
                "Texto incompleto: $label",
                lines > 0 && layout.getLineEnd(lines - 1, visibleEnd = true) == expected.length,
            )
            assertFalse("Texto con puntos suspensivos: $label", (0 until lines).any(layout::isLineEllipsized))
            assertFalse("Texto recortado: $label", layout.stage12VisibleOverflow())
        }
        val textBounds = text.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Texto fuera de su control: $label text=$textBounds owner=$ownerBounds",
            textBounds.left >= ownerBounds.left - 1f && textBounds.top >= ownerBounds.top - 1f &&
                textBounds.right <= ownerBounds.right + 1f && textBounds.bottom <= ownerBounds.bottom + 1f,
        )
    }

    @Composable
    private fun Gate(
        state: AccessLockState,
        capability: DeviceAuthenticationCapability,
        calls: GateCalls,
    ) {
        AccessLockContentGate(
            state = state,
            capability = capability,
            onUnlock = { calls.unlocks++ },
            onRetryStore = { calls.storeRetries++ },
            onRepairStore = { calls.repairs++ },
            onRetryDeviceSecurity = { calls.securityRetries++ },
            onOpenDeviceSecurity = { calls.securityOpens++ },
        ) {
            calls.sensitiveCompositions++
            Text(SENSITIVE_TEXT, modifier = Modifier.testTag(SENSITIVE))
        }
    }

    @Composable
    private fun Settings(
        state: AccessLockState,
        capability: DeviceAuthenticationCapability,
        calls: SettingsCalls,
    ) {
        AccessLockSettingsScreen(
            state = state,
            capability = capability,
            contentPadding = PaddingValues(0.dp),
            onActivate = { calls.activated = it },
            onChangeTimeout = { calls.changed += it },
            onDisable = { calls.disables++ },
            onLockNow = { calls.lockNows++ },
            onRetryDeviceSecurity = { calls.securityRetries++ },
            onOpenDeviceSecurity = { calls.securityOpens++ },
        )
    }

    private class GateCalls {
        var unlocks = 0
        var storeRetries = 0
        var repairs = 0
        var securityRetries = 0
        var securityOpens = 0
        var sensitiveCompositions = 0
    }

    private class SettingsCalls {
        var activated: AccessLockTimeout? = null
        val changed = mutableListOf<AccessLockTimeout>()
        var disables = 0
        var lockNows = 0
        var securityRetries = 0
        var securityOpens = 0
    }

    private fun lockedState() = AccessLockState(
        phase = AccessLockPhase.READY,
        configuration = AccessLockConfiguration(true, AccessLockTimeout.FIVE_MINUTES),
        locked = true,
        privacyCoverVisible = true,
    )

    private fun openState() = AccessLockState(
        phase = AccessLockPhase.READY,
        configuration = AccessLockConfiguration(true, AccessLockTimeout.FIVE_MINUTES),
        locked = false,
        privacyCoverVisible = false,
    )

    private fun disabledState() = AccessLockState(
        phase = AccessLockPhase.READY,
        configuration = AccessLockConfiguration(),
        locked = false,
        privacyCoverVisible = false,
    )

    private fun timeoutTag(timeout: AccessLockTimeout) = "access-lock-timeout-${timeout.name.lowercase()}"

    private companion object {
        const val MESSAGE = "access-lock-message"
        const val CAPABILITY = "access-lock-device-capability"
        const val UNLOCK = "access-lock-unlock"
        const val RETRY_STORE = "access-lock-retry-store"
        const val REPAIR_STORE = "access-lock-repair-store"
        const val RETRY_SECURITY = "access-lock-retry-device-security"
        const val OPEN_SECURITY = "access-lock-open-device-security"
        const val LOADING = "access-lock-loading"
        const val PRIVACY_COVER = "access-lock-privacy-cover"
        const val STATUS = "access-lock-status"
        const val ENABLED_CONTROL = "access-lock-enabled-control"
        const val LOCK_NOW = "access-lock-now"
        const val SENSITIVE = "stage14-sensitive-content"
        const val SENSITIVE_TEXT = "CONTENIDO LABORAL FICTICIO"

        val MessageTexts = linkedMapOf(
            AccessLockMessage.USER_CANCELLED to "No se hizo ningún cambio porque cancelaste la autenticación.",
            AccessLockMessage.SYSTEM_CANCELLED to "Android cerró la autenticación. Podés volver a intentar.",
            AccessLockMessage.LOCKOUT to
                "El teléfono pausó la autenticación por demasiados intentos. Esperá y reintentá.",
            AccessLockMessage.HARDWARE_UNAVAILABLE to "La autenticación no está disponible ahora. Probá nuevamente.",
            AccessLockMessage.BIOMETRIC_NOT_ENROLLED to
                "No hay una biometría fuerte configurada. Podés usar la credencial segura del teléfono.",
            AccessLockMessage.NO_SECURE_CREDENTIAL to "Primero configurá un bloqueo seguro en tu teléfono.",
            AccessLockMessage.UNSUPPORTED to "Este método no está disponible de forma segura en el teléfono.",
            AccessLockMessage.RECOVERABLE_ERROR to "No pudimos confirmar tu identidad. Nada cambió.",
            AccessLockMessage.FINAL_ERROR to "No pudimos confirmar tu identidad. Nada cambió.",
            AccessLockMessage.STORE_WRITE_ERROR to
                "No pudimos guardar el cambio. La configuración anterior se conservó.",
        )

        val UnavailableTexts = linkedMapOf(
            DeviceAuthenticationCapability.NO_SECURE_CREDENTIAL to
                "Primero configurá un bloqueo seguro en tu teléfono.",
            DeviceAuthenticationCapability.TEMPORARILY_UNAVAILABLE to
                "La autenticación del teléfono no está disponible ahora. Probá nuevamente.",
        )

        val TimeoutTexts = linkedMapOf(
            AccessLockTimeout.IMMEDIATE to "Inmediatamente",
            AccessLockTimeout.ONE_MINUTE to "Después de 1 minuto",
            AccessLockTimeout.FIVE_MINUTES to "Después de 5 minutos",
            AccessLockTimeout.FIFTEEN_MINUTES to "Después de 15 minutos",
        )
    }
}
