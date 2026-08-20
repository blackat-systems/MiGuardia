package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationAttentionMode
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.notifications.NotificationRhythm
import com.blackatsystems.miguardia.notifications.NotificationSystemAccessState
import com.blackatsystems.miguardia.ui.notifications.NotificationActions
import com.blackatsystems.miguardia.ui.notifications.NotificationSurface
import com.blackatsystems.miguardia.ui.notifications.NotificationSurfaceHost
import com.blackatsystems.miguardia.ui.notifications.NotificationUiState
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
        compose.onNodeWithText("PRÓXIMA GUARDIA · Hospital Norte").assertExists()
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
        compose.onNodeWithText("Completa: objetivo, horario y puesto").assertExists()
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
        compose.onNodeWithText("Notificaciones: pendiente").assertExists()
        compose.onNodeWithText("Vista previa").assertExists()
        compose.onNodeWithText("Ritmo de avisos").assertDoesNotExist()
        compose.onNodeWithText("Cuándo te acompaña").assertDoesNotExist()
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
        compose.onNodeWithText("Tenés un aviso de guardia.").assertExists()
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
        compose.onNodeWithText("Desactivar sólo en esta guardia").assertDoesNotExist()
        compose.onNodeWithText("Volver a usar la configuración general").performScrollTo().performClick()
        compose.onNodeWithText("Personalizar esta guardia").performScrollTo().performClick()
        assertEquals(1, personalized)
        assertEquals(1, global)
    }

    @Test
    fun hiddenNotificationsOfferIndividualAndBatchRestore() {
        val restored = mutableListOf<UUID>()
        var restoredAll = 0
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    NotificationUiState(
                        surface = NotificationSurface.GLOBAL,
                        preferences = NotificationPreferences(enabled = true),
                        systemAccess = NotificationSystemAccessState(true, false),
                        restorableShifts = listOf(
                            shift(),
                            shift().copy(
                                id = SECOND_SHIFT_ID,
                                objectiveAbbreviationSnapshot = "QB",
                                startAt = shift().startAt.plusSeconds(3600),
                                endAt = shift().endAt.plusSeconds(3600),
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
        assertEquals(listOf(SHIFT_ID), restored)
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
        sourceObjectiveId = null,
        sourceScheduleCombinationId = null,
        createdAt = Instant.parse("2026-08-15T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-15T00:00:00Z"),
    )

    private companion object {
        val SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000601")
        val SECOND_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000602")
    }
}
