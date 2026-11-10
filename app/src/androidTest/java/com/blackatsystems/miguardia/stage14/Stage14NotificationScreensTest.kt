package com.blackatsystems.miguardia.stage14

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.notifications.NotificationSystemAccessState
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.notifications.NotificationActions
import com.blackatsystems.miguardia.ui.notifications.NotificationSurface
import com.blackatsystems.miguardia.ui.notifications.NotificationSurfaceHost
import com.blackatsystems.miguardia.ui.notifications.NotificationUiState
import com.blackatsystems.miguardia.ui.notifications.restorableLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Etapa 14.1 · Avisos y notificaciones, pruebas de pantalla con estado y acciones ficticios.
 * No abre la base, no lee preferencias reales, no publica avisos ni lanza pantallas de Android:
 * los lanzamientos externos quedan retenidos en un registro de resultados de mentira.
 */
class Stage14NotificationScreensTest {
    @get:Rule val compose = createComposeRule()

    // A208, A209
    @Test
    fun disabledNotificationsShowTheOffStateAndBothControlsAskToEnable() {
        val enabledRequests = mutableListOf<Boolean>()
        show(
            state = { globalState(NotificationPreferences(enabled = false)) },
            actions = NotificationActions(setEnabled = { enabledRequests += it }),
        )

        compose.onNodeWithText("Notificaciones").assertExists()
        compose.onNodeWithText("Avisos desactivados").assertExists()
        compose.onNodeWithText("MiGuardia no mostrará avisos hasta que los actives.").assertExists()
        compose.onNodeWithText("Avisos activados").assertDoesNotExist()
        compose.onNodeWithText("Usar notificaciones").assertExists()
        compose.onNodeWithTag("notification-enabled").assertIsOff()
        // Con avisos apagados sólo quedan el estado, la vista previa y el acceso a Ajustes.
        compose.onNodeWithText("Vista previa").performScrollTo().assertExists()
        compose.onNodeWithText("1. Permití los avisos").assertDoesNotExist()
        compose.onNodeWithText("Ritmo de avisos").assertDoesNotExist()
        compose.onNodeWithText("Cuándo te acompaña").assertDoesNotExist()
        compose.onNodeWithText("Ver opciones avanzadas").assertDoesNotExist()
        compose.onAllNodesWithText("Abrir ajustes de notificaciones").assertCountEquals(1)

        compose.onNodeWithTag("notification-enabled").performScrollTo().performClick()
        compose.onNodeWithText("Activar avisos").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(true, true), enabledRequests) }
    }

    // A208, A209
    @Test
    fun switchTurnsNotificationsOffAndTheButtonTurnsThemBackOn() {
        var state by mutableStateOf(globalState())
        val enabledRequests = mutableListOf<Boolean>()
        show(
            state = { state },
            actions = NotificationActions(
                setEnabled = { value ->
                    enabledRequests += value
                    state = state.copy(preferences = state.preferences.copy(enabled = value))
                },
            ),
        )

        compose.onNodeWithText("Avisos activados").assertExists()
        compose.onNodeWithText("Te guiamos en tres pasos. Podés cambiar cada detalle cuando quieras.").assertExists()
        compose.onNodeWithText("Activar avisos").assertDoesNotExist()
        compose.onNodeWithText("1. Permití los avisos").assertExists()
        compose.onNodeWithTag("notification-enabled").assertIsOn().performScrollTo().performClick()

        compose.runOnIdle { assertEquals(listOf(false), enabledRequests) }
        compose.onNodeWithText("Avisos desactivados").assertExists()
        compose.onNodeWithTag("notification-enabled").assertIsOff()
        compose.onNodeWithText("1. Permití los avisos").assertDoesNotExist()
        compose.onNodeWithText("Cuándo te acompaña").assertDoesNotExist()

        compose.onNodeWithText("Activar avisos").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(false, true), enabledRequests) }
        compose.onNodeWithText("Avisos activados").assertExists()
        compose.onNodeWithTag("notification-enabled").assertIsOn()
        compose.onNodeWithText("Cuándo te acompaña").assertExists()
    }

    // A217
    @Test
    fun eachQuickLeadButtonSendsItsMinutesAndBecomesTheOnlyReminder() {
        var state by mutableStateOf(
            globalState(NotificationPreferences(enabled = true, globalReminderLeadMinutes = listOf(120L, 720L))),
        )
        val received = mutableListOf<List<Long>>()
        show(
            state = { state },
            actions = NotificationActions(
                setGlobalReminders = { values ->
                    received += values.toList()
                    state = state.copy(
                        preferences = state.preferences.copy(globalReminderLeadMinutes = values.sorted()),
                    )
                },
            ),
        )

        compose.onNodeWithText("Cuándo te acompaña").performScrollTo().assertExists()
        compose.onNodeWithText("2 avisos configurados.").assertExists()
        compose.onNodeWithText("Elegí una opción. Recomendamos 12 horas antes.").assertExists()
        listOf(6L, 8L, 12L, 24L).forEachIndexed { index, hours ->
            compose.onNodeWithText("$hours h").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(index + 1, received.size)
                assertEquals(listOf(hours * 60L), received.last())
            }
            compose.onNodeWithText("Un aviso $hours horas antes.").assertExists()
            compose.onNodeWithText("2 avisos configurados.").assertDoesNotExist()
        }
        // Tocar el que ya está elegido vuelve a pedir el mismo valor, sin acumular otro aviso.
        compose.onNodeWithText("24 h").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(1_440L), received.last()) }
        compose.onNodeWithText("Un aviso 24 horas antes.").assertExists()
    }

    // A218
    @Test
    fun reminderEditorAddsAndRemovesPresetAndCustomLeads() {
        var state by mutableStateOf(globalState())
        val received = mutableListOf<List<Long>>()
        show(
            state = { state },
            actions = NotificationActions(
                setGlobalReminders = { values ->
                    received += values.toList()
                    state = state.copy(
                        preferences = state.preferences.copy(globalReminderLeadMinutes = values.sorted()),
                    )
                },
            ),
        )

        compose.onNodeWithText("Más de un aviso").assertDoesNotExist()
        compose.onNodeWithText("Ver opciones avanzadas").performScrollTo().performClick()
        compose.onNodeWithText("Ocultar opciones avanzadas").assertExists()
        compose.onNodeWithText("Más de un aviso").performScrollTo().assertExists()
        compose.onNodeWithText("Entre cero y cinco avisos únicos; los avisos ya pasados no se recuperan tarde.")
            .assertExists()
        choiceRadio("12 horas antes").assertIsSelected()
        choiceRadio("6 horas antes").assertIsNotSelected()
        choiceRadio("8 horas antes").assertIsNotSelected()
        choiceRadio("24 horas antes").assertIsNotSelected()

        // Agregar un anticipo predefinido.
        compose.onNodeWithText("6 horas antes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(720L, 360L), received.last()) }
        choiceRadio("6 horas antes").assertIsSelected()
        choiceRadio("12 horas antes").assertIsSelected()
        compose.onNodeWithText("2 avisos configurados.").assertExists()

        // Quitar un anticipo predefinido.
        compose.onNodeWithText("12 horas antes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(360L), received.last()) }
        choiceRadio("12 horas antes").assertIsNotSelected()
        compose.onNodeWithText("Un aviso 6 horas antes.").assertExists()

        // Valor propio: sólo dígitos, positivo y no repetido.
        compose.onNodeWithText("Agregar aviso").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("4a5")
        compose.onNodeWithText("Agregar aviso").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(360L, 45L), received.last()) }
        compose.onNodeWithText("45 minutos antes").performScrollTo().assertExists()
        compose.onNodeWithText("2 avisos configurados.").assertExists()
        compose.onNodeWithText("Agregar aviso").performScrollTo().assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("360")
        compose.onNodeWithText("Agregar aviso").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("Agregar aviso").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("")

        // Quitar el valor propio y después el último: queda sin avisos previos.
        compose.onNodeWithText("Quitar").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(360L), received.last()) }
        compose.onNodeWithText("45 minutos antes").assertDoesNotExist()
        compose.onNodeWithText("6 horas antes").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(emptyList<Long>(), received.last())
            assertEquals(5, received.size)
        }
        compose.onNodeWithText("No hay avisos configurados.").performScrollTo().assertExists()
        compose.onNodeWithText("Sin avisos previos.").assertExists()
    }

    // A218
    @Test
    fun reminderEditorStopsAtFiveLeadsUntilOneIsRemoved() {
        var state by mutableStateOf(
            globalState(
                NotificationPreferences(
                    enabled = true,
                    globalReminderLeadMinutes = listOf(30L, 45L, 360L, 480L, 720L),
                ),
            ),
        )
        val received = mutableListOf<List<Long>>()
        show(
            state = { state },
            actions = NotificationActions(
                setGlobalReminders = { values ->
                    received += values.toList()
                    state = state.copy(
                        preferences = state.preferences.copy(globalReminderLeadMinutes = values.sorted()),
                    )
                },
            ),
        )

        compose.onNodeWithText("5 avisos configurados.").assertExists()
        compose.onNodeWithText("Ver opciones avanzadas").performScrollTo().performClick()
        compose.onNodeWithText("30 minutos antes").performScrollTo().assertExists()
        compose.onNodeWithText("45 minutos antes").performScrollTo().assertExists()
        compose.onAllNodesWithText("Quitar").assertCountEquals(2)

        // Con cinco, ni el predefinido que falta ni un valor propio válido agregan un sexto.
        compose.onNodeWithText("24 horas antes").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("50")
        compose.onNodeWithText("Agregar aviso").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(received.isEmpty()) }
        choiceRadio("24 horas antes").assertIsNotSelected()
        compose.onNodeWithText("5 avisos configurados.").assertExists()

        // Quitar uno libera el lugar para el valor que ya estaba escrito.
        compose.onNodeWithText("6 horas antes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(30L, 45L, 480L, 720L), received.single()) }
        compose.onNodeWithText("4 avisos configurados.").assertExists()
        compose.onNodeWithText("Agregar aviso").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(30L, 45L, 480L, 720L, 50L), received.last()) }
        compose.onNodeWithText("5 avisos configurados.").assertExists()
        compose.onNodeWithText("50 minutos antes").performScrollTo().assertExists()
        compose.onAllNodesWithText("Quitar").assertCountEquals(3)
        compose.onNodeWithText("Agregar aviso").performScrollTo().assertIsNotEnabled()
    }

    // A219
    @Test
    fun persistenceChoiceSendsPinnedOrDismissibleAndExplainsTheResult() {
        var state by mutableStateOf(globalState())
        val received = mutableListOf<Boolean>()
        show(
            state = { state },
            actions = NotificationActions(
                setPersistent = { value ->
                    received += value
                    state = state.copy(preferences = state.preferences.copy(persistentWhileActive = value))
                },
            ),
        )

        compose.onNodeWithText("Permanencia").performScrollTo().assertExists()
        compose.onNodeWithText("Queda visible mientras el evento está en curso.").assertExists()
        choiceRadio("Fija durante el evento").assertIsSelected()
        choiceRadio("Descartable").assertIsNotSelected()

        compose.onNodeWithText("Descartable").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(false), received) }
        compose.onNodeWithText("Podés descartarla como cualquier otra notificación.").assertExists()
        compose.onNodeWithText("Queda visible mientras el evento está en curso.").assertDoesNotExist()
        choiceRadio("Descartable").assertIsSelected()
        choiceRadio("Fija durante el evento").assertIsNotSelected()

        compose.onNodeWithText("Fija durante el evento").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(false, true), received) }
        compose.onNodeWithText("Queda visible mientras el evento está en curso.").assertExists()
        choiceRadio("Fija durante el evento").assertIsSelected()
    }

    // A221
    @Test
    fun defaultSoundButtonClearsTheChosenSoundAndTheLabelFollows() {
        var state by mutableStateOf(
            globalState(NotificationPreferences(enabled = true, soundUri = FAKE_SOUND)),
        )
        val received = mutableListOf<Uri?>()
        show(
            state = { state },
            actions = NotificationActions(
                setSound = { uri ->
                    received += uri
                    state = state.copy(preferences = state.preferences.copy(soundUri = uri))
                },
            ),
        )

        compose.onNodeWithText("Sonido: elegido en Android").performScrollTo().assertExists()
        compose.onNodeWithText("Sonido: predeterminado de Android").assertDoesNotExist()
        compose.onNodeWithText("Elegir sonido").performScrollTo().assertExists()
        compose.onNodeWithText("Predeterminado").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(listOf<Uri?>(null), received) }
        compose.onNodeWithText("Sonido: predeterminado de Android").performScrollTo().assertExists()
        compose.onNodeWithText("Sonido: elegido en Android").assertDoesNotExist()
    }

    // A221
    @Test
    fun soundControlsOnlyExistWhenTheAttentionModeUsesSound() {
        var state by mutableStateOf(globalState())
        show(
            state = { state },
            actions = NotificationActions(
                setAttentionMode = { mode ->
                    state = state.copy(preferences = state.preferences.copy(attentionMode = mode))
                },
            ),
        )

        compose.onNodeWithText("Suena y vibra al publicar el aviso.").performScrollTo().assertExists()
        compose.onNodeWithText("Sonido: predeterminado de Android").performScrollTo().assertExists()
        compose.onNodeWithText("Silencioso").performScrollTo().performClick()
        compose.onNodeWithText("Aparece sin sonido ni vibración.").performScrollTo().assertExists()
        compose.onNodeWithText("Sonido: predeterminado de Android").assertDoesNotExist()
        compose.onNodeWithText("Elegir sonido").assertDoesNotExist()
        compose.onNodeWithText("Predeterminado").assertDoesNotExist()
        compose.onNodeWithText("Sólo vibración").performScrollTo().performClick()
        compose.onNodeWithText("Vibra sin reproducir sonido.").performScrollTo().assertExists()
        compose.onNodeWithText("Elegir sonido").assertDoesNotExist()
    }

    // A221 (selector retenido: no se abre el selector real de Android)
    @Test
    fun soundPickerIsAskedForNotificationSoundsAndOnlyAnAcceptedResultIsSaved() {
        val registry = RecordingRegistry()
        val received = mutableListOf<Uri?>()
        show(
            state = { globalState(NotificationPreferences(enabled = true, soundUri = FAKE_SOUND)) },
            actions = NotificationActions(setSound = { received += it }),
            registryOwner = registry.owner,
        )

        compose.onNodeWithText("Elegir sonido").performScrollTo().performClick()
        val first = compose.runOnIdle { registry.launches.single() }
        val intent = first.input as Intent
        assertEquals(RingtoneManager.ACTION_RINGTONE_PICKER, intent.action)
        assertEquals(
            RingtoneManager.TYPE_NOTIFICATION,
            intent.getIntExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, -1),
        )
        assertFalse(intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true))
        assertEquals(
            FAKE_SOUND,
            IntentCompat.getParcelableExtra(intent, RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri::class.java),
        )

        // Volver sin elegir no guarda nada.
        compose.runOnUiThread {
            assertTrue(registry.dispatchResult(first.requestCode, Activity.RESULT_CANCELED, null))
        }
        compose.runOnIdle { assertTrue(received.isEmpty()) }

        compose.onNodeWithText("Elegir sonido").performScrollTo().performClick()
        val second = compose.runOnIdle { registry.launches.last() }
        compose.runOnUiThread {
            assertTrue(
                registry.dispatchResult(
                    second.requestCode,
                    Activity.RESULT_OK,
                    Intent().putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, OTHER_FAKE_SOUND),
                ),
            )
        }
        compose.runOnIdle {
            assertEquals(2, registry.launches.size)
            assertEquals(listOf<Uri?>(OTHER_FAKE_SOUND), received)
        }
    }

    // A222
    @Test
    fun preciseTimingSwitchSendsItsValueAndSaysItIsNotAnAlarmClock() {
        var state by mutableStateOf(
            globalState(access = NotificationSystemAccessState(true, true)),
        )
        val received = mutableListOf<Boolean>()
        show(
            state = { state },
            actions = NotificationActions(
                setPreciseTiming = { value ->
                    received += value
                    state = state.copy(preferences = state.preferences.copy(preciseTiming = value))
                },
            ),
        )

        compose.onNodeWithText("Puntualidad").assertDoesNotExist()
        compose.onNodeWithText("Ver opciones avanzadas").performScrollTo().performClick()
        compose.onNodeWithText("Puntualidad").performScrollTo().assertExists()
        compose.onNodeWithText("Es opcional. Sin este acceso, Android puede demorar algunos minutos el aviso.")
            .assertExists()
        compose.onNodeWithText("Puntualidad exacta: concedido").performScrollTo().assertExists()
        compose.onNodeWithText("Intentar publicar exactamente a horario").performScrollTo().assertExists()
        compose.onNodeWithText("Es una notificación común: nunca funciona como despertador.")
            .performScrollTo()
            .assertExists()

        // Usar notificaciones, los dos del aviso permanente y la puntualidad exacta.
        compose.onAllNodes(isToggleable()).assertCountEquals(4)
        preciseTimingSwitch().assertIsOff().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(true), received) }
        preciseTimingSwitch().assertIsOn().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(true, false), received) }
        preciseTimingSwitch().assertIsOff()
    }

    // A222 (destino retenido: no se abre Ajustes)
    @Test
    fun resolvingExactTimingAsksAndroidForThisAppsExactAlarmAccess() {
        val registry = RecordingRegistry()
        var refreshes = 0
        show(
            state = { globalState(access = NotificationSystemAccessState(true, false)) },
            actions = NotificationActions(refreshSystemAccess = { refreshes++ }),
            registryOwner = registry.owner,
        )

        compose.onNodeWithText("Ver opciones avanzadas").performScrollTo().performClick()
        compose.onNodeWithText("Puntualidad exacta: pendiente").performScrollTo().assertExists()
        compose.onAllNodesWithText("Resolver").assertCountEquals(1)
        compose.onNodeWithText("Resolver").performScrollTo().performClick()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val launch = compose.runOnIdle {
                assertEquals(0, refreshes)
                registry.launches.single()
            }
            val intent = launch.input as Intent
            assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
            assertEquals(Uri.parse("package:$targetPackage"), intent.data)
            assertNull(intent.component)
            // Al volver de Ajustes la pantalla vuelve a leer el acceso.
            compose.runOnUiThread {
                assertTrue(registry.dispatchResult(launch.requestCode, Activity.RESULT_CANCELED, null))
            }
            compose.runOnIdle { assertEquals(1, refreshes) }
        } else {
            // Antes de Android 12 no existe ese ajuste: sólo se relee el acceso.
            compose.runOnIdle {
                assertTrue(registry.launches.isEmpty())
                assertEquals(1, refreshes)
            }
        }
    }

    // A223
    @Test
    fun eachLockScreenPrivacyChoiceIsSentAndChangesThePreview() {
        var state by mutableStateOf(globalState())
        val received = mutableListOf<NotificationPrivacy>()
        show(
            state = { state },
            actions = NotificationActions(
                setPrivacy = { value ->
                    received += value
                    state = state.copy(preferences = state.preferences.copy(privacy = value))
                },
            ),
        )

        compose.onNodeWithText("Ver opciones avanzadas").performScrollTo().performClick()
        compose.onNodeWithText("Privacidad").performScrollTo().assertExists()
        compose.onNodeWithText("Pantalla bloqueada").performScrollTo().assertExists()
        choiceRadio(PRIVACY_COMPLETE).assertIsSelected()
        choiceRadio(PRIVACY_REDUCED).assertIsNotSelected()
        choiceRadio(PRIVACY_HIDDEN).assertIsNotSelected()
        compose.onNodeWithText("PRÓXIMA JORNADA · Hospital Norte").assertExists()

        compose.onNodeWithText(PRIVACY_REDUCED).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(NotificationPrivacy.REDUCED), received) }
        choiceRadio(PRIVACY_REDUCED).assertIsSelected()
        choiceRadio(PRIVACY_COMPLETE).assertIsNotSelected()
        compose.onNodeWithText("Horario 19:00–07:00").assertExists()
        compose.onNodeWithText("PRÓXIMA JORNADA · Hospital Norte").assertDoesNotExist()
        compose.onNodeWithText("Acceso principal").assertDoesNotExist()

        compose.onNodeWithText(PRIVACY_HIDDEN).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(NotificationPrivacy.REDUCED, NotificationPrivacy.HIDDEN), received)
        }
        choiceRadio(PRIVACY_HIDDEN).assertIsSelected()
        compose.onNodeWithText("Tenés un aviso de MiGuardia.").assertExists()
        compose.onNodeWithText("Horario 19:00–07:00").assertDoesNotExist()
        compose.onNodeWithText("Comienza en 3 h 12 min").assertDoesNotExist()

        compose.onNodeWithText(PRIVACY_COMPLETE).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(NotificationPrivacy.REDUCED, NotificationPrivacy.HIDDEN, NotificationPrivacy.COMPLETE),
                received,
            )
        }
        choiceRadio(PRIVACY_COMPLETE).assertIsSelected()
        compose.onNodeWithText("NOR · 19:00–07:00").assertExists()
        compose.onNodeWithText("Acceso principal").assertExists()
    }

    // A212 (destino retenido: no se abre Ajustes)
    @Test
    fun openingNotificationSettingsTargetsThisAppAndRefreshesAccessOnReturn() {
        val registry = RecordingRegistry()
        var refreshes = 0
        show(
            state = { globalState(access = NotificationSystemAccessState(true, false)) },
            actions = NotificationActions(refreshSystemAccess = { refreshes++ }),
            registryOwner = registry.owner,
        )

        compose.onAllNodesWithText("Abrir ajustes de notificaciones").assertCountEquals(1)
        compose.onNodeWithText("Abrir ajustes de notificaciones").performScrollTo().performClick()
        val launch = compose.runOnIdle {
            assertEquals(0, refreshes)
            registry.launches.single()
        }
        assertIsAppNotificationSettings(launch.input)

        compose.runOnUiThread {
            assertTrue(registry.dispatchResult(launch.requestCode, Activity.RESULT_CANCELED, null))
        }
        compose.runOnIdle { assertEquals(1, refreshes) }

        // Con el permiso concedido, «Ajustes» de las dos filas lleva al mismo destino.
        compose.onNodeWithText("Permiso de Android: concedido").performScrollTo().assertExists()
        compose.onNodeWithText("Notificaciones de la aplicación: concedido").performScrollTo().assertExists()
        compose.onAllNodesWithText("Ajustes").assertCountEquals(2)
        for (index in 0..1) {
            compose.onAllNodesWithText("Ajustes")[index].performScrollTo().performClick()
            val next = compose.runOnIdle {
                assertEquals(index + 2, registry.launches.size)
                registry.launches.last()
            }
            assertIsAppNotificationSettings(next.input)
        }
    }

    // A212, A208 (destino retenido: no se abre Ajustes)
    @Test
    fun disabledStateStillOpensThisAppsNotificationSettings() {
        val registry = RecordingRegistry()
        var refreshes = 0
        val enabledRequests = mutableListOf<Boolean>()
        show(
            state = { globalState(NotificationPreferences(enabled = false)) },
            actions = NotificationActions(
                refreshSystemAccess = { refreshes++ },
                setEnabled = { enabledRequests += it },
            ),
            registryOwner = registry.owner,
        )

        compose.onNodeWithText("Abrir ajustes de notificaciones").performScrollTo().performClick()
        val launch = compose.runOnIdle { registry.launches.single() }
        assertIsAppNotificationSettings(launch.input)
        compose.runOnUiThread {
            assertTrue(registry.dispatchResult(launch.requestCode, Activity.RESULT_OK, null))
        }
        compose.runOnIdle {
            assertEquals(1, refreshes)
            // Ir a Ajustes no activa los avisos por su cuenta.
            assertTrue(enabledRequests.isEmpty())
        }
    }

    // A224, A226
    @Test
    fun singleHiddenNotificationUsesSingularTitleWithoutBatchRestore() {
        val restored = mutableListOf<String>()
        var restoredAll = 0
        val event = event()
        show(
            state = { globalState().copy(restorableEvents = listOf(event)) },
            actions = NotificationActions(
                restoreNotification = { restored += it },
                restoreAllNotifications = { restoredAll++ },
            ),
        )

        compose.onNodeWithText("Notificación oculta").performScrollTo().assertExists()
        compose.onNodeWithText("Notificaciones ocultas").assertDoesNotExist()
        compose.onNodeWithText("Podés volver a mostrar cada aviso mientras el evento siga vigente.").assertExists()
        compose.onNodeWithText(event.restorableLabel()).assertExists()
        compose.onNodeWithText("Mostrar todas nuevamente").assertDoesNotExist()
        compose.onAllNodesWithText("Mostrar notificación nuevamente").assertCountEquals(1)
        compose.onNodeWithText("Mostrar notificación nuevamente").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("shift:$SHIFT_ID"), restored)
            assertEquals(0, restoredAll)
        }
    }

    // A224, A226
    @Test
    fun hiddenNotificationsStayListedButCannotBeRestoredWhileOffBlockedOrSaving() {
        val restored = mutableListOf<String>()
        var restoredAll = 0
        val events = listOf(
            event(),
            event().copy(
                shiftId = SECOND_SHIFT_ID,
                placeAbbreviationSnapshot = "QB",
                start = event().start.plusSeconds(3_600),
                end = event().end.plusSeconds(3_600),
            ),
        )
        var state by mutableStateOf(
            globalState(NotificationPreferences(enabled = false)).copy(restorableEvents = events),
        )
        show(
            state = { state },
            actions = NotificationActions(
                restoreNotification = { restored += it },
                restoreAllNotifications = { restoredAll++ },
            ),
        )

        fun assertListedButLocked() {
            compose.onNodeWithText("Notificaciones ocultas").performScrollTo().assertExists()
            compose.onNodeWithText("Mostrar todas nuevamente").performScrollTo().assertIsNotEnabled()
            compose.onAllNodesWithText("Mostrar notificación nuevamente").assertCountEquals(2)
            compose.onAllNodesWithText("Mostrar notificación nuevamente")[0].assertIsNotEnabled()
            compose.onAllNodesWithText("Mostrar notificación nuevamente")[1].assertIsNotEnabled()
        }

        // Avisos apagados.
        assertListedButLocked()
        compose.onNodeWithText("Activá las notificaciones y resolvé el permiso para volver a mostrarlas.")
            .assertExists()

        // Avisos encendidos pero bloqueados en Android.
        compose.runOnIdle {
            state = globalState(
                access = NotificationSystemAccessState(
                    notificationPermissionGranted = true,
                    exactAlarmAccessGranted = false,
                    appNotificationsEnabled = false,
                ),
            ).copy(restorableEvents = events)
        }
        assertListedButLocked()
        compose.onNodeWithText("Activá las notificaciones y resolvé el permiso para volver a mostrarlas.")
            .assertExists()

        // Todo en orden pero con un guardado en curso.
        compose.runOnIdle { state = globalState().copy(restorableEvents = events, isSaving = true) }
        assertListedButLocked()

        // Al terminar de guardar vuelven a estar disponibles.
        compose.runOnIdle { state = globalState().copy(restorableEvents = events) }
        compose.onNodeWithText("Podés volver a mostrar cada aviso mientras el evento siga vigente.").assertExists()
        compose.onNodeWithText("Mostrar todas nuevamente").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, restoredAll)
            assertTrue(restored.isEmpty())
        }
    }

    // C080, A227, A229
    @Test
    fun shiftUsingGeneralSettingsCanBeSilencedRestoredAndPersonalized() {
        var state by mutableStateOf(shiftState(override = null))
        val reminders = mutableListOf<List<Long>>()
        var disabled = 0
        var backToGlobal = 0
        show(
            state = { state },
            actions = NotificationActions(
                setShiftReminders = { values ->
                    reminders += values.toList()
                    state = state.copy(shiftOverride = ShiftNotificationConfig(SHIFT_ID, values.sorted()))
                },
                disableShift = {
                    disabled++
                    state = state.copy(shiftOverride = ShiftNotificationConfig(SHIFT_ID, emptyList()))
                },
                useGlobalForShift = {
                    backToGlobal++
                    state = state.copy(shiftOverride = null)
                },
            ),
        )

        compose.onNodeWithText("Avisos de la jornada").assertExists()
        compose.onNodeWithText("Notificaciones").assertDoesNotExist()
        compose.onNodeWithText("QA · 19:00–07:00").assertExists()
        compose.onNodeWithText("Usa la configuración general").assertExists()
        compose.onNodeWithText("Un aviso 12 horas antes.").assertExists()
        compose.onNodeWithText("Volver a usar la configuración general").assertDoesNotExist()
        compose.onNodeWithText("Usar configuración general").assertDoesNotExist()
        compose.onNodeWithText("Horarios de esta jornada").assertDoesNotExist()

        // A227: desactivar sólo esta jornada.
        compose.onNodeWithText("Desactivar sólo en esta jornada").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, disabled) }
        compose.onNodeWithText("Avisos desactivados").assertExists()
        compose.onNodeWithText("Esta jornada no tendrá avisos.").assertExists()
        compose.onNodeWithText("Desactivar sólo en esta jornada").assertDoesNotExist()

        // A229: volver a la general.
        compose.onNodeWithText("Volver a usar la configuración general").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, backToGlobal) }
        compose.onNodeWithText("Usa la configuración general").assertExists()
        compose.onNodeWithText("Un aviso 12 horas antes.").assertExists()

        // A228: personalizar parte de los anticipos generales y abre el editor propio.
        compose.onNodeWithText("Personalizar esta jornada").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(listOf(720L)), reminders) }
        compose.onNodeWithText("Configuración propia").assertExists()
        compose.onNodeWithText("Horarios de esta jornada").performScrollTo().assertExists()
        compose.onNodeWithText("Listo").performScrollTo().assertExists()
        choiceRadio("12 horas antes").assertIsSelected()
        compose.runOnIdle {
            assertEquals(1, disabled)
            assertEquals(1, backToGlobal)
        }
    }

    // C080, A228, A229
    @Test
    fun shiftWithOwnSettingsEditsItsLeadsAndCanReturnToGeneral() {
        var state by mutableStateOf(
            shiftState(override = ShiftNotificationConfig(SHIFT_ID, listOf(30L, 720L))),
        )
        val reminders = mutableListOf<List<Long>>()
        var backToGlobal = 0
        show(
            state = { state },
            actions = NotificationActions(
                setShiftReminders = { values ->
                    reminders += values.toList()
                    state = state.copy(shiftOverride = ShiftNotificationConfig(SHIFT_ID, values.sorted()))
                },
                useGlobalForShift = {
                    backToGlobal++
                    state = state.copy(shiftOverride = null)
                },
            ),
        )

        compose.onNodeWithText("Configuración propia").assertExists()
        compose.onNodeWithText("2 avisos configurados.").assertExists()
        compose.onNodeWithText("Personalizar esta jornada").assertDoesNotExist()
        compose.onNodeWithText("Horarios de esta jornada").assertDoesNotExist()

        compose.onNodeWithText("Cambiar horarios de aviso").performScrollTo().performClick()
        compose.onNodeWithText("Cambiar horarios de aviso").assertDoesNotExist()
        compose.onNodeWithText("Horarios de esta jornada").performScrollTo().assertExists()
        compose.onNodeWithText("30 minutos antes").performScrollTo().assertExists()
        choiceRadio("12 horas antes").assertIsSelected()
        choiceRadio("6 horas antes").assertIsNotSelected()

        compose.onNodeWithText("6 horas antes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(30L, 720L, 360L), reminders.last()) }
        compose.onNodeWithText("3 avisos configurados.").assertExists()
        choiceRadio("6 horas antes").assertIsSelected()

        compose.onNodeWithText("Quitar").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(360L, 720L), reminders.last()) }
        compose.onNodeWithText("30 minutos antes").assertDoesNotExist()
        compose.onNodeWithText("2 avisos configurados.").assertExists()

        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("15")
        compose.onNodeWithText("Agregar aviso").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(360L, 720L, 15L), reminders.last()) }
        compose.onNodeWithText("15 minutos antes").performScrollTo().assertExists()

        compose.onNodeWithText("Listo").performScrollTo().performClick()
        compose.onNodeWithText("Horarios de esta jornada").assertDoesNotExist()
        compose.onNodeWithText("Cambiar horarios de aviso").assertExists()
        compose.onNodeWithText("3 avisos configurados.").assertExists()

        compose.onNodeWithText("Usar configuración general").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, backToGlobal)
            assertEquals(3, reminders.size)
        }
        compose.onNodeWithText("Usa la configuración general").assertExists()
        compose.onNodeWithText("Un aviso 12 horas antes.").assertExists()
    }

    // A227, A228
    @Test
    fun shiftWithOwnSettingsCanBeSilencedFromTheButtonOrByRemovingItsLastLead() {
        var state by mutableStateOf(shiftState(override = ShiftNotificationConfig(SHIFT_ID, listOf(720L))))
        val reminders = mutableListOf<List<Long>>()
        var disabled = 0
        show(
            state = { state },
            actions = NotificationActions(
                setShiftReminders = { values ->
                    reminders += values.toList()
                    state = state.copy(shiftOverride = ShiftNotificationConfig(SHIFT_ID, values.sorted()))
                },
                disableShift = { disabled++ },
            ),
        )

        compose.onNodeWithText("Configuración propia").assertExists()
        compose.onNodeWithText("Un aviso 12 horas antes.").assertExists()
        compose.onNodeWithText("Desactivar sólo en esta jornada").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, disabled)
            assertTrue(reminders.isEmpty())
        }

        compose.onNodeWithText("Cambiar horarios de aviso").performScrollTo().performClick()
        compose.onNodeWithText("12 horas antes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(emptyList<Long>()), reminders) }
        compose.onNodeWithText("Avisos desactivados").assertExists()
        compose.onNodeWithText("Esta jornada no tendrá avisos.").assertExists()
        compose.onNodeWithText("Horarios de esta jornada").assertDoesNotExist()
        compose.onNodeWithText("Volver a usar la configuración general").assertExists()
    }

    // A228 · POSIBLE DEFECTO: con cero anticipos generales, «Personalizar esta jornada» envía una lista
    // vacía (NotificationScreens.kt:545 y :560), que equivale a desactivar, y el editor propio no se abre
    // (:579). La prueba afirma lo que el caso exige: poder dar anticipos propios a la jornada.
    @Test
    fun personalizingAShiftOpensItsOwnEditorEvenWhenGeneralHasNoReminders() {
        var state by mutableStateOf(
            shiftState(
                override = null,
                preferences = NotificationPreferences(enabled = true, globalReminderLeadMinutes = emptyList()),
            ),
        )
        show(
            state = { state },
            actions = NotificationActions(
                setShiftReminders = { values ->
                    state = state.copy(shiftOverride = ShiftNotificationConfig(SHIFT_ID, values.sorted()))
                },
            ),
        )

        compose.onNodeWithText("Usa la configuración general").assertExists()
        compose.onNodeWithText("Esta jornada no tendrá avisos.").assertExists()
        compose.onNodeWithText("Personalizar esta jornada").performScrollTo().performClick()

        compose.onNodeWithText("Horarios de esta jornada").assertExists()
        compose.onNodeWithText("Avisos desactivados").assertDoesNotExist()
    }

    // C080 (letra de Android al 200 %)
    @Test
    fun shiftActionsStayReachableWithTheLargestAndroidFontScale() {
        var closes = 0
        var disabled = 0
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = false, fontScale = TestFontScale.EXTRA_LARGE) {
                NotificationSurfaceHost(
                    shiftState(override = null),
                    NotificationActions(close = { closes++ }, disableShift = { disabled++ }),
                )
            }
        }

        compose.onNodeWithText("Avisos de la jornada").assertExists()
        compose.onNodeWithTag("notification-close").assertIsDisplayed()
        compose.onNodeWithText("Usa la configuración general").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Personalizar esta jornada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Desactivar sólo en esta jornada").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("notification-close").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, disabled)
            assertEquals(1, closes)
        }
    }

    // A208, C080 (carga)
    @Test
    fun loadingShowsOnlyProgressAndKeepsTheCloseAction() {
        var state by mutableStateOf(globalState().copy(isLoading = true))
        var closes = 0
        show(state = { state }, actions = NotificationActions(close = { closes++ }))

        compose.onNodeWithText("Notificaciones").assertExists()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        compose.onNodeWithText("Avisos activados").assertDoesNotExist()
        compose.onNodeWithText("Vista previa").assertDoesNotExist()
        compose.onNode(isToggleable()).assertDoesNotExist()

        compose.runOnIdle { state = shiftState(override = null).copy(isLoading = true) }
        compose.onNodeWithText("Avisos de la jornada").assertExists()
        compose.onNodeWithText("QA · 19:00–07:00").assertExists()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        compose.onNodeWithText("Usa la configuración general").assertDoesNotExist()
        compose.onNodeWithText("Personalizar esta jornada").assertDoesNotExist()

        compose.runOnIdle { state = shiftState(override = null) }
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
        compose.onNodeWithText("Usa la configuración general").assertExists()

        compose.onNodeWithTag("notification-close").performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    // A208 (error con reintento y aviso de guardado)
    @Test
    fun errorOffersRetryAndDismissWithoutHidingTheSettings() {
        var retries = 0
        var cleared = 0
        var closes = 0
        var state by mutableStateOf(
            globalState().copy(errorMessage = "No pudimos leer la configuración de avisos."),
        )
        show(
            state = { state },
            actions = NotificationActions(
                retry = { retries++ },
                clearMessage = { cleared++ },
                close = { closes++ },
            ),
        )

        compose.onNodeWithText("No pudimos leer la configuración de avisos.").assertExists()
        compose.onNodeWithText("Avisos activados").assertExists()
        compose.onNodeWithText("Reintentar").performScrollTo().performClick()
        compose.onNode(hasText("Cerrar") and !hasTestTag("notification-close")).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, cleared)
            assertEquals(0, closes)
        }

        compose.runOnIdle { state = globalState().copy(infoMessage = "Configuración guardada.") }
        compose.onNodeWithText("No pudimos leer la configuración de avisos.").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
        compose.onNodeWithText("Configuración guardada.").assertExists()
        compose.onAllNodesWithText("Cerrar").assertCountEquals(1)
    }

    // A214 (guardado en curso)
    @Test
    fun testNotificationButtonIsLockedWhileSavingAndSendsOnceUnlocked() {
        var state by mutableStateOf(globalState().copy(isSaving = true))
        var tests = 0
        show(state = { state }, actions = NotificationActions(sendTestNotification = { tests++ }))

        compose.onNodeWithText("Enviar notificación de prueba").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("La prueba usa datos ficticios y desaparece sola en un minuto.").assertExists()
        compose.runOnIdle { state = globalState() }
        compose.onNodeWithText("Enviar notificación de prueba").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, tests) }
    }

    private fun show(
        state: () -> NotificationUiState,
        actions: NotificationActions,
        registryOwner: ActivityResultRegistryOwner? = null,
    ) {
        compose.setContent {
            val content: @Composable () -> Unit = {
                MaterialTheme { NotificationSurfaceHost(state(), actions) }
            }
            if (registryOwner == null) {
                content()
            } else {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) { content() }
            }
        }
    }

    /** El botón redondo de una fila de opción; la fila y su texto son hermanos suyos sin fusionar. */
    private fun choiceRadio(label: String): SemanticsNodeInteraction = compose.onNode(
        isSelectable() and hasAnySibling(hasText(label)),
        useUnmergedTree = true,
    )

    /** Con las opciones avanzadas abiertas: primero «Usar notificaciones», después puntualidad. */
    private fun preciseTimingSwitch(): SemanticsNodeInteraction = compose.onNodeWithTag("notification-precise-timing")

    private val targetPackage: String
        get() = ApplicationProvider.getApplicationContext<Context>().packageName

    private fun assertIsAppNotificationSettings(input: Any?) {
        val intent = input as Intent
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(targetPackage, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertNull(intent.data)
        assertNull(intent.component)
    }

    private fun globalState(
        preferences: NotificationPreferences = NotificationPreferences(enabled = true),
        access: NotificationSystemAccessState = NotificationSystemAccessState(true, false),
    ) = NotificationUiState(
        surface = NotificationSurface.GLOBAL,
        preferences = preferences,
        systemAccess = access,
        isLoading = false,
    )

    private fun shiftState(
        override: ShiftNotificationConfig?,
        preferences: NotificationPreferences = NotificationPreferences(enabled = true),
    ) = NotificationUiState(
        surface = NotificationSurface.SHIFT,
        preferences = preferences,
        systemAccess = NotificationSystemAccessState(true, false),
        selectedShift = shift(),
        shiftOverride = override,
        isLoading = false,
    )

    private fun shift(): Shift = Shift(
        id = SHIFT_ID,
        startAt = Instant.parse("2026-09-01T22:00:00Z"),
        endAt = Instant.parse("2026-09-02T10:00:00Z"),
        zoneId = ZoneId.of("America/Argentina/Cordoba"),
        localStartDate = LocalDate.of(2026, 9, 1),
        objectiveNameSnapshot = "Objetivo ficticio",
        objectiveAbbreviationSnapshot = "QA",
        objectiveAddressSnapshot = null,
        startTimeSnapshot = LocalTime.of(19, 0),
        endTimeSnapshot = LocalTime.of(7, 0),
        colorArgbSnapshot = 0xff336699.toInt(),
        position = null,
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = UUID(0L, 1_401L),
        createdAt = Instant.parse("2026-08-15T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-15T00:00:00Z"),
    )

    private fun event(): NextEventItem.Shift = shift().let { shift ->
        NextEventItem.Shift(
            shiftId = shift.id,
            start = shift.startAt,
            end = shift.endAt,
            zoneId = shift.zoneId,
            ownerLocalDate = shift.localStartDate,
            sector = WorkSector.PRIVATE_SECURITY,
            workTypeNameSnapshot = "Jornada ficticia",
            workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
            placeNameSnapshot = shift.objectiveNameSnapshot,
            placeAbbreviationSnapshot = shift.objectiveAbbreviationSnapshot,
            startTimeSnapshot = shift.startTimeSnapshot,
            endTimeSnapshot = shift.endTimeSnapshot,
            colorArgbSnapshot = shift.colorArgbSnapshot,
            positionSnapshot = shift.position,
            hasHistoricalAddress = !shift.objectiveAddressSnapshot.isNullOrBlank(),
        )
    }

    private class Launch(val requestCode: Int, val input: Any?)

    /** Retiene cada lanzamiento externo en memoria; nunca abre una pantalla de Android. */
    private class RecordingRegistry : ActivityResultRegistry() {
        val launches = mutableListOf<Launch>()
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry get() = this@RecordingRegistry
        }

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launches += Launch(requestCode, input)
        }
    }

    private companion object {
        val SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000001401")
        val SECOND_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000001402")
        val FAKE_SOUND: Uri = Uri.parse("content://stage14.ficticio/sonido-elegido")
        val OTHER_FAKE_SOUND: Uri = Uri.parse("content://stage14.ficticio/otro-sonido")
        const val PRIVACY_COMPLETE = "Completa: tipo, lugar, horario y puesto"
        const val PRIVACY_REDUCED = "Reducida: estado y horario"
        const val PRIVACY_HIDDEN = "Oculta: mensaje genérico"
    }
}
