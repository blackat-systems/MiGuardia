package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
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
    fun globalSettingsExplainDeniedExactFallbackAndPrivacyChoices() {
        compose.setContent {
            MaterialTheme {
                NotificationSurfaceHost(
                    NotificationUiState(
                        surface = NotificationSurface.GLOBAL,
                        preferences = NotificationPreferences(enabled = true, preciseTiming = true),
                        systemAccess = NotificationSystemAccessState(false, false),
                        isLoading = false,
                    ),
                    NotificationActions(),
                )
            }
        }

        compose.onNodeWithText("Notificaciones").assertExists()
        compose.onNodeWithText("Notificaciones: pendiente").assertExists()
        compose.onNodeWithText("Puntualidad exacta: pendiente").assertExists()
        compose.onNodeWithText("Sin este acceso, Android puede demorar la notificación. Nunca suena ni se presenta como un despertador.").assertExists()
        compose.onNodeWithText("Mantener fija hasta finalizar la guardia").assertExists()
        compose.onNodeWithText("Completa: objetivo, horario y puesto").assertExists()
        compose.onNodeWithText("Reducida: estado y horario").assertExists()
        compose.onNodeWithText("Oculta: mensaje genérico").assertExists()
    }

    @Test
    fun shiftSettingsExposeOwnDisabledAndReturnToGlobalActions() {
        var disabled = 0
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
                        disableShift = { disabled++ },
                        useGlobalForShift = { global++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Los avisos están desactivados sólo para esta guardia.").assertExists()
        compose.onNodeWithText("Desactivar avisos en esta guardia").performScrollTo().performClick()
        compose.onNodeWithText("Volver a usar valores globales").performScrollTo().performClick()
        assertEquals(1, disabled)
        assertEquals(1, global)
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
    }
}
