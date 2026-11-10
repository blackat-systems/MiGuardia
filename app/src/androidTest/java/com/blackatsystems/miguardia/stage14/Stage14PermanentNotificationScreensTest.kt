package com.blackatsystems.miguardia.stage14

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationRhythm
import com.blackatsystems.miguardia.notifications.NotificationSystemAccessState
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.notifications.NotificationActions
import com.blackatsystems.miguardia.ui.notifications.NotificationSurface
import com.blackatsystems.miguardia.ui.notifications.NotificationSurfaceHost
import com.blackatsystems.miguardia.ui.notifications.NotificationUiState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Aviso permanente (etapa 14): los dos controles de la pantalla de Avisos y su guardado.
 * Pantalla con estado y acciones ficticios; las preferencias viven en una carpeta temporal.
 * No publica avisos ni toca las preferencias reales de la app.
 */
@RunWith(AndroidJUnit4::class)
class Stage14PermanentNotificationScreensTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun permanentNotificationStartsOffExplainsItselfAndSendsTrueWhenTurnedOn() {
        var state by mutableStateOf(globalState())
        val received = mutableListOf<Boolean>()
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    state,
                    NotificationActions(
                        setAlwaysShowNext = { value ->
                            received += value
                            state = state.copy(preferences = state.preferences.copy(alwaysShowNext = value))
                        },
                    ),
                )
            }
        }

        compose.onNodeWithText("Aviso permanente").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("El aviso aparece recién con el primer anticipo.").assertExists()
        compose.onNodeWithText("Activalo para ver siempre cuánto falta, sin esperar al primer anticipo.").assertExists()
        compose.onNodeWithTag(ALWAYS).performScrollTo().assertIsOff().performClick()

        compose.runOnIdle { assertEquals(listOf(true), received) }
        compose.onNodeWithTag(ALWAYS).assertIsOn()
        compose.onNodeWithText("Tu próxima jornada queda siempre a la vista.").assertExists()
        compose.onNodeWithText("La cuenta regresiva aparece cuando faltan 24 horas o menos.", substring = true)
            .assertExists()
        compose.onNodeWithText("Si lo deslizás, vuelve con la jornada siguiente.", substring = true).assertExists()

        compose.onNodeWithTag(ALWAYS).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(true, false), received) }
        compose.onNodeWithTag(ALWAYS).assertIsOff()
    }

    @Test
    fun lockScreenSwitchStartsOnAndWarnsWhenNothingWillShowWhileLocked() {
        var state by mutableStateOf(globalState())
        val received = mutableListOf<Boolean>()
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    state,
                    NotificationActions(
                        setShowOnLockScreen = { value ->
                            received += value
                            state = state.copy(preferences = state.preferences.copy(showOnLockScreen = value))
                        },
                    ),
                )
            }
        }
        val warning = "Con el teléfono bloqueado no se muestra ningún aviso de jornada."

        compose.onNodeWithTag(LOCK).performScrollTo().assertIsOn()
        compose.onNodeWithText(warning).assertDoesNotExist()
        compose.onNodeWithTag(LOCK).performClick()

        compose.runOnIdle { assertEquals(listOf(false), received) }
        compose.onNodeWithTag(LOCK).assertIsOff()
        compose.onNodeWithText(warning).performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag(LOCK).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(false, true), received) }
        compose.onNodeWithText(warning).assertDoesNotExist()
    }

    @Test
    fun activeCountdownSwitchStartsOnAndExplainsTheScheduleOnlyChoice() {
        var state by mutableStateOf(globalState())
        val received = mutableListOf<Boolean>()
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    state,
                    NotificationActions(
                        setCountdownWhileActive = { value ->
                            received += value
                            state = state.copy(preferences = state.preferences.copy(countdownWhileActive = value))
                        },
                    ),
                )
            }
        }
        val note = "Mientras la jornada está en curso el aviso muestra sólo el horario, sin cuenta regresiva."

        compose.onNodeWithText("Contar hasta el final durante la jornada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ACTIVE).performScrollTo().assertIsOn()
        compose.onNodeWithText(note).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVE).performClick()

        compose.runOnIdle { assertEquals(listOf(false), received) }
        compose.onNodeWithTag(ACTIVE).assertIsOff()
        compose.onNodeWithText(note).performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag(ACTIVE).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(false, true), received) }
        compose.onNodeWithText(note).assertDoesNotExist()
    }

    @Test
    fun permanentControlsAreHiddenWhileNotificationsAreOff() {
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    globalState(NotificationPreferences(enabled = false)),
                    NotificationActions(),
                )
            }
        }

        compose.onNodeWithText("Aviso permanente").assertDoesNotExist()
        compose.onNodeWithTag(ALWAYS).assertDoesNotExist()
        compose.onNodeWithTag(LOCK).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVE).assertDoesNotExist()
    }

    @Test
    fun permanentControlsStayReachableWithTheLargestAndroidFontScale() {
        var turnedOn = 0
        var lockChanges = 0
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = false, fontScale = TestFontScale.EXTRA_LARGE) {
                NotificationSurfaceHost(
                    globalState(),
                    NotificationActions(
                        setAlwaysShowNext = { turnedOn++ },
                        setShowOnLockScreen = { lockChanges++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Mostrar siempre la próxima jornada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ALWAYS).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Mostrar en la pantalla bloqueada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(LOCK).performScrollTo().assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertEquals(1, turnedOn)
            assertEquals(1, lockChanges)
        }
    }

    @Test
    fun permanentChoicesAreSavedReopenedAndSurviveARhythmChange() = runBlocking {
        val file = File(folder.newFolder("stage14-permanent"), "stage14-permanent.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = NotificationPreferencesStore(file, firstScope)
            assertFalse(store.current().alwaysShowNext)
            assertTrue(store.current().showOnLockScreen)

            assertTrue(store.current().countdownWhileActive)
            store.setAlwaysShowNext(true)
            store.setShowOnLockScreen(false)
            store.setCountdownWhileActive(false)
            // A rhythm rewrites reminders, permanence, privacy and attention; it must not touch these two.
            store.applyRhythm(NotificationRhythm.DISCREET)

            assertTrue(store.current().alwaysShowNext)
            assertFalse(store.current().showOnLockScreen)
            assertFalse(store.current().countdownWhileActive)
            assertEquals(NotificationRhythm.DISCREET, store.current().rhythm())
        } finally {
            firstScope.cancel()
        }

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = NotificationPreferencesStore(file, secondScope).current()
            assertTrue(reopened.alwaysShowNext)
            assertFalse(reopened.showOnLockScreen)
            assertFalse(reopened.countdownWhileActive)
        } finally {
            secondScope.cancel()
        }
    }

    private fun globalState(
        preferences: NotificationPreferences = NotificationPreferences(enabled = true),
    ) = NotificationUiState(
        surface = NotificationSurface.GLOBAL,
        preferences = preferences,
        systemAccess = NotificationSystemAccessState(true, false),
        isLoading = false,
    )

    private companion object {
        const val ALWAYS = "notification-always-show-next"
        const val LOCK = "notification-lock-screen"
        const val ACTIVE = "notification-active-countdown"
    }
}
