package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationAttentionMode
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.notifications.NotificationRhythm
import com.blackatsystems.miguardia.notifications.NotificationSystemAccessState
import com.blackatsystems.miguardia.ui.notifications.NotificationActions
import com.blackatsystems.miguardia.ui.notifications.NotificationSurface
import com.blackatsystems.miguardia.ui.notifications.NotificationSurfaceHost
import com.blackatsystems.miguardia.ui.notifications.NotificationUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun globalSettingsOffersPreviewRhythmsAndAttentionBeforeAdvancedChoices() {
        var selectedRhythm: NotificationRhythm? = null
        var selectedAttention: NotificationAttentionMode? = null
        var testsSent = 0
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    NotificationUiState(
                        surface = NotificationSurface.GLOBAL,
                        preferences = NotificationPreferences(enabled = true, preciseTiming = true),
                        systemAccess = NotificationSystemAccessState(true, false),
                        isLoading = false,
                    ),
                    NotificationActions(
                        applyRhythm = { selectedRhythm = it },
                        setAttentionMode = { selectedAttention = it },
                        sendTestNotification = { testsSent++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Notificaciones").assertExists()
        compose.onNodeWithText("1. Permití los avisos").assertExists()
        compose.onNodeWithText("Vista previa").assertExists()
        compose.onNodeWithContentDescription("Vista previa Pulso Vigilia").assertExists()
        compose.onNodeWithText("PRÓXIMA JORNADA · Hospital Norte").assertExists()
        compose.onNodeWithText("Ritmo de avisos").assertExists()
        compose.onNodeWithText("Acompañado").performScrollTo().performClick()
        assertEquals(NotificationRhythm.ACCOMPANIED, selectedRhythm)
        compose.onNodeWithText("Cuándo te acompaña").assertExists()
        compose.onNodeWithText("Permanencia").assertExists()
        compose.onNodeWithText("Cómo llama tu atención").assertExists()
        compose.onNodeWithText("Sólo vibración").performScrollTo().performClick()
        assertEquals(NotificationAttentionMode.VIBRATION_ONLY, selectedAttention)
        compose.onNodeWithText("Recomendamos 12 horas antes.", substring = true).assertExists()
        compose.onNodeWithText("Abrir ajustes de notificaciones").assertExists()
        compose.onNodeWithText("Enviar notificación de prueba").performScrollTo().performClick()
        assertEquals(1, testsSent)
        compose.onNodeWithText("Puntualidad exacta: pendiente").assertDoesNotExist()
        compose.onNodeWithText("Ver opciones avanzadas").performScrollTo().performClick()
        compose.onNodeWithText("Puntualidad exacta: pendiente").assertExists()
        compose.onNodeWithText("Completa: tipo, lugar, horario y puesto").assertExists()
        compose.onNodeWithText("Reducida: estado y horario").assertExists()
        compose.onNodeWithText("Oculta: mensaje genérico").assertExists()
        compose.onNodeWithText("Android conserva el control final del canal, el sonido y la vibración.")
            .performScrollTo().assertExists()
    }

    @Test
    fun deniedPermissionKeepsTheSetupFocusedOnTheFirstStep() {
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    NotificationUiState(
                        surface = NotificationSurface.GLOBAL,
                        preferences = NotificationPreferences(enabled = true),
                        systemAccess = NotificationSystemAccessState(false, false),
                        isLoading = false,
                    ),
                    NotificationActions(),
                )
            }
        }

        compose.onNodeWithText("1. Permití los avisos").assertExists()
        compose.onNodeWithText("Permiso de Android: pendiente").assertExists()
        compose.onNodeWithText("Notificaciones de la aplicación: pendiente").assertExists()
        compose.onNodeWithText("Vista previa").assertExists()
        compose.onNodeWithText("Ritmo de avisos").assertDoesNotExist()
        compose.onNodeWithText("Cuándo te acompaña").assertDoesNotExist()
    }

    @Test
    fun grantedRuntimePermissionAndBlockedAppNotificationsStayDistinct() {
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    NotificationUiState(
                        surface = NotificationSurface.GLOBAL,
                        preferences = NotificationPreferences(enabled = true),
                        systemAccess = NotificationSystemAccessState(
                            notificationPermissionGranted = true,
                            exactAlarmAccessGranted = false,
                            appNotificationsEnabled = false,
                        ),
                        isLoading = false,
                    ),
                    NotificationActions(),
                )
            }
        }

        compose.onNodeWithText("Permiso de Android: concedido").assertExists()
        compose.onNodeWithText("Notificaciones de la aplicación: pendiente").assertExists()
        compose.onNodeWithText("Ritmo de avisos").assertDoesNotExist()
        compose.onNodeWithText("Enviar notificación de prueba").assertIsNotEnabled()
    }

    @Test
    fun notificationSurfaceRemainsReachableInLightDarkAndEveryInternalZoom() {
        var dark by mutableStateOf(false)
        var zoom by mutableStateOf(AppZoom.STANDARD)
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                NotificationSurfaceHost(
                    NotificationUiState(
                        surface = NotificationSurface.GLOBAL,
                        preferences = NotificationPreferences(enabled = true),
                        systemAccess = NotificationSystemAccessState(true, false),
                        isLoading = false,
                    ),
                    NotificationActions(),
                )
            }
        }

        AppZoom.entries.forEach { option ->
            compose.runOnIdle { zoom = option }
            compose.onNodeWithText("Notificaciones").assertExists()
            compose.onNodeWithText("Vista previa").performScrollTo().assertExists()
        }
        compose.runOnIdle { dark = true }
        compose.onNodeWithText("Volver").performScrollTo().assertExists()
    }

    @Test
    fun oneHourReminderUsesSingularCopy() {
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    NotificationUiState(
                        surface = NotificationSurface.GLOBAL,
                        preferences = NotificationPreferences(
                            enabled = true,
                            globalReminderLeadMinutes = listOf(60L),
                        ),
                        systemAccess = NotificationSystemAccessState(true, false),
                        isLoading = false,
                    ),
                    NotificationActions(),
                )
            }
        }

        compose.onNodeWithText("Un aviso 1 hora antes.").assertExists()
        compose.onNodeWithText("Un aviso 1 horas antes.").assertDoesNotExist()
    }

    @Test
    fun customHiddenPreferencesAreNamedPersonalizedAndPreviewLeaksNoFixtureDetails() {
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    NotificationUiState(
                        surface = NotificationSurface.GLOBAL,
                        preferences = NotificationPreferences(
                            enabled = true,
                            globalReminderLeadMinutes = listOf(360L),
                            privacy = NotificationPrivacy.HIDDEN,
                        ),
                        systemAccess = NotificationSystemAccessState(true, false),
                        isLoading = false,
                    ),
                    NotificationActions(),
                )
            }
        }

        compose.onNodeWithText("Actual: Personalizado", substring = true).assertExists()
        compose.onNodeWithText("Tenés un aviso de MiGuardia.").assertExists()
        compose.onNodeWithText("Hospital Norte").assertDoesNotExist()
        compose.onNodeWithText("NOR · 19:00–07:00").assertDoesNotExist()
        compose.onNodeWithText("Comienza en 3 h 12 min").assertDoesNotExist()
    }

    @Test
    fun disabledShiftOffersSimpleRestoreAndPersonalizeActions() {
        var personalized = 0
        var global = 0
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    NotificationUiState(
                        surface = NotificationSurface.SHIFT,
                        selectedShift = shift(),
                        shiftOverride = ShiftNotificationConfig(SHIFT_ID, emptyList()),
                        isLoading = false,
                    ),
                    NotificationActions(
                        setShiftReminders = { personalized++ },
                        useGlobalForShift = { global++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Avisos desactivados").assertExists()
        compose.onNodeWithText("Desactivar sólo en esta jornada").assertDoesNotExist()
        compose.onNodeWithText("Volver a usar la configuración general").performScrollTo().performClick()
        compose.onNodeWithText("Personalizar esta jornada").performScrollTo().performClick()
        assertEquals(1, personalized)
        assertEquals(1, global)
    }

    @Test
    fun hiddenNotificationsOfferIndividualAndBatchRestore() {
        val restored = mutableListOf<String>()
        var restoredAll = 0
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    NotificationUiState(
                        surface = NotificationSurface.GLOBAL,
                        preferences = NotificationPreferences(enabled = true),
                        systemAccess = NotificationSystemAccessState(true, false),
                        restorableEvents = listOf(
                            event(),
                            event().copy(
                                shiftId = SECOND_SHIFT_ID,
                                placeAbbreviationSnapshot = "QB",
                                start = event().start.plusSeconds(3600),
                                end = event().end.plusSeconds(3600),
                            ),
                        ),
                        isLoading = false,
                    ),
                    NotificationActions(
                        restoreNotification = { restored += it },
                        restoreAllNotifications = { restoredAll++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Notificaciones ocultas").assertExists()
        compose.onAllNodesWithText("Mostrar notificación nuevamente")[0]
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Mostrar todas nuevamente").performScrollTo().performClick()
        assertEquals(listOf("shift:$SHIFT_ID"), restored)
        assertEquals(1, restoredAll)
    }

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
        sourceObjectiveId = UUID(0L, 239L),
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

    private companion object {
        val SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000601")
        val SECOND_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000602")
    }
}
