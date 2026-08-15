package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurface
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurfaceHost
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsUiState
import com.blackatsystems.miguardia.ui.exceptions.ExceptionPlanningOperation
import com.blackatsystems.miguardia.ui.exceptions.PendingExceptionPlanning
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExceptionsComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun holidaysUseCalendarSelectionForOneOrSeveralDates() {
        var input = ""
        var edited: Holiday? = null
        val holiday = Holiday(UUID(0, 1), LocalDate.of(2026, 8, 17), "Feriado ficticio", NOW, NOW)
        composeRule.setContent {
            val state = remember {
                mutableStateOf(
                    ExceptionsUiState(
                        surface = ExceptionsSurface.HOLIDAYS,
                        holidayMonth = YearMonth.of(2026, 8),
                        holidays = listOf(holiday),
                    ),
                )
            }
            MaterialTheme {
                ExceptionsSurfaceHost(
                    state.value,
                    ExceptionsActions(
                        updateHolidayDraft = { transform ->
                            val updated = transform(state.value.holidayDraft)
                            input = updated.datesText
                            state.value = state.value.copy(holidayDraft = updated)
                        },
                        editHoliday = { edited = it },
                    ),
                )
            }
        }
        composeRule.onNodeWithContentDescription("17 Agosto de 2026, sin seleccionar").performClick()
        composeRule.onNodeWithContentDescription("18 Agosto de 2026, sin seleccionar").performClick()
        composeRule.onNodeWithText("2 fechas seleccionadas.").assertExists()
        composeRule.onNodeWithText("Feriado ficticio").assertExists()
        composeRule.onNodeWithText("Editar").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("2026-08-17,2026-08-18", input)
            assertEquals(holiday, edited)
        }
    }

    @Test fun shiftSurfaceExplainsInformativeAndHourChangingOperationsWithoutExposingNoteSemantics() {
        composeRule.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    ExceptionsUiState(
                        surface = ExceptionsSurface.SHIFT,
                        holidayMonth = YearMonth.of(2026, 8),
                        selectedShift = SHIFT,
                        notes = listOf(ShiftNote(UUID(0, 2), SHIFT.id, "Texto privado ficticio", NOW, NOW)),
                    ),
                    ExceptionsActions(),
                )
            }
        }

        composeRule.onNodeWithText("Ausencia y cancelación llevan las horas trabajadas a cero.", substring = true).assertExists()
        composeRule.onNodeWithText("Texto privado ficticio").performScrollTo().assertExists()
        composeRule.onNodeWithText("Tiempo adicional, salida anticipada y otra novedad no modifican las horas.")
            .performScrollTo().assertExists()
        composeRule.onNodeWithText("Cambiar objetivo u horario sí modifica las horas.", substring = true)
            .performScrollTo().assertExists()
        composeRule.onNodeWithText("Eliminar").performScrollTo().performClick()
        composeRule.onNodeWithText("Eliminar nota").assertExists()
    }

    @Test fun planningWarningShowsConcreteEvidenceBeforeSecondShiftIsCreated() {
        composeRule.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    ExceptionsUiState(
                        surface = ExceptionsSurface.SHIFT,
                        holidayMonth = YearMonth.of(2026, 8),
                        selectedShift = SHIFT,
                        planningWarnings = listOf(
                            "2026-08-13: ya habrá más de una guardia (09:00–17:00 y 10:00–18:00).",
                        ),
                        pendingPlanning = PendingExceptionPlanning(
                            operation = ExceptionPlanningOperation.SECOND_SHIFT,
                            combinationId = UUID(0, 99),
                            description = "",
                        ),
                    ),
                    ExceptionsActions(),
                )
            }
        }

        composeRule.onNodeWithText("Confirmar segunda guardia").assertExists()
        composeRule.onNodeWithText("2026-08-13: ya habrá más de una guardia", substring = true).assertExists()
    }

    @Test fun absenceAndCancellationOfferAnOptionalDescriptionBeforeConfirming() {
        var changedStatus: ShiftStatus? = null
        var savedDescription: String? = null
        composeRule.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    ExceptionsUiState(
                        surface = ExceptionsSurface.SHIFT,
                        holidayMonth = YearMonth.of(2026, 8),
                        selectedShift = SHIFT,
                    ),
                    ExceptionsActions(
                        changeStatus = { status, description ->
                            changedStatus = status
                            savedDescription = description
                        },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Registrar ausencia").performScrollTo().performClick()
        composeRule.onNodeWithText("+ Agregar descripción opcional").performClick()
        composeRule.onNodeWithTag("status-description-field").performTextInput("Motivo ficticio")
        composeRule.onNodeWithText("Confirmar").performClick()

        composeRule.runOnIdle {
            assertEquals(ShiftStatus.ABSENT, changedStatus)
            assertEquals("Motivo ficticio", savedDescription)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-13T12:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val SHIFT = Shift(
            UUID(0, 10), NOW, NOW.plusSeconds(8 * 3600), ZONE, LocalDate.of(2026, 8, 13),
            "Objetivo Ficticio", "OBJ", null, LocalTime.of(9, 0), LocalTime.of(17, 0),
            0xFF123456.toInt(), null, ShiftStatus.PLANNED, null, null, NOW, NOW,
        )
    }
}
