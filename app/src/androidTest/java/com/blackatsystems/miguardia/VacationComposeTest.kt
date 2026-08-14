package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.vacation.VacationActions
import com.blackatsystems.miguardia.ui.vacation.VacationDraft
import com.blackatsystems.miguardia.ui.vacation.VacationSurface
import com.blackatsystems.miguardia.ui.vacation.VacationSurfaceHost
import com.blackatsystems.miguardia.ui.vacation.VacationUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VacationComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun calendarAddFlowOffersVacationsForVisibleMonth() {
        var requestedMonth: YearMonth? = null
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = calendarState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    vacationActions = VacationActions(
                        openCreate = { month, _ -> requestedMonth = month },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Agregar").performScrollTo().performClick()
        composeRule.onNodeWithText("Vacaciones").performClick()
        composeRule.runOnIdle { assertEquals(YearMonth.of(2026, 8), requestedMonth) }
    }

    @Test fun editorShowsInclusivePreviewAndSaves() {
        var saves = 0
        val start = LocalDate.of(2026, 8, 30)
        composeRule.setContent {
            MaterialTheme {
                VacationSurfaceHost(
                    state = VacationUiState(
                        surface = VacationSurface.EDITOR,
                        visibleMonth = YearMonth.of(2026, 8),
                        draft = VacationDraft(
                            startDate = start,
                            endDateInclusive = LocalDate.of(2026, 9, 2),
                            isDirty = true,
                        ),
                    ),
                    actions = VacationActions(save = { saves += 1 }),
                )
            }
        }

        composeRule.onNodeWithText("Vista previa: 4 días corridos").assertExists()
        composeRule.onNodeWithContentDescription("4 días corridos, fechas inclusivas").assertExists()
        composeRule.onNodeWithText("Guardar vacaciones").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun singleDayPreviewUsesSingularWording() {
        val date = LocalDate.of(2026, 8, 14)
        composeRule.setContent {
            MaterialTheme {
                VacationSurfaceHost(
                    state = VacationUiState(
                        surface = VacationSurface.EDITOR,
                        visibleMonth = YearMonth.from(date),
                        draft = VacationDraft(startDate = date, endDateInclusive = date),
                    ),
                    actions = VacationActions(),
                )
            }
        }

        composeRule.onNodeWithText("Vista previa: 1 día corrido").assertExists()
        composeRule.onNodeWithContentDescription("1 día corrido, fechas inclusivas").assertExists()
    }

    @Test fun listSupportsEmptyErrorEditAndConfirmedDeleteStates() {
        val vacation = vacation()
        var edits = 0
        var retries = 0
        var deletes = 0
        var state by mutableStateOf(
            VacationUiState(
                surface = VacationSurface.LIST,
                visibleMonth = YearMonth.of(2026, 8),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                VacationSurfaceHost(
                    state = state,
                    actions = VacationActions(
                        retry = { retries += 1 },
                        edit = { edits += 1 },
                        confirmDelete = { deletes += 1 },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("No hay vacaciones que intersecten este mes.").assertExists()
        composeRule.runOnIdle {
            state = state.copy(errorMessage = "Error recuperable ficticio")
        }
        composeRule.onNodeWithText("Error recuperable ficticio").assertExists()
        composeRule.onNodeWithText("Reintentar").performClick()
        composeRule.runOnIdle {
            assertEquals(1, retries)
            state = state.copy(errorMessage = null, vacations = listOf(vacation))
        }
        composeRule.onNodeWithText("Editar").performClick()
        composeRule.runOnIdle {
            assertEquals(1, edits)
            state = state.copy(vacations = emptyList(), pendingDeleteId = vacation.id)
        }
        composeRule.onNodeWithText("Eliminar vacaciones").assertExists()
        composeRule.onNodeWithText("Eliminar").performClick()
        composeRule.runOnIdle {
            assertEquals(1, deletes)
        }
    }

    @Test fun discardConfirmationProtectsUnsavedDraft() {
        var discarded = 0
        composeRule.setContent {
            MaterialTheme {
                VacationSurfaceHost(
                    state = VacationUiState(
                        surface = VacationSurface.EDITOR,
                        visibleMonth = YearMonth.of(2026, 8),
                        draft = VacationDraft(
                            startDate = LocalDate.of(2026, 8, 1),
                            endDateInclusive = LocalDate.of(2026, 8, 2),
                            isDirty = true,
                        ),
                        showDiscardConfirmation = true,
                    ),
                    actions = VacationActions(confirmDiscard = { discarded += 1 }),
                )
            }
        }

        composeRule.onNodeWithText("Descartar cambios").assertExists()
        composeRule.onNodeWithText("Descartar").performClick()
        composeRule.runOnIdle { assertEquals(1, discarded) }
    }

    @Test fun calendarShowsVacationWithShiftAndExplainsExcludedHoursInDetail() {
        val vacation = vacation()
        val shift = shift(vacation.startDate)
        val days = projectCalendarMonth(
            month = YearMonth.of(2026, 8),
            shifts = listOf(shift),
            explicitDayStatuses = emptyList(),
            medicalLeaves = emptyList(),
            now = Instant.parse("2026-08-20T12:00:00Z"),
            vacations = listOf(vacation),
        )
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = calendarState().copy(days = days, selectedDate = vacation.startDate),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Esta guardia se conserva, pero no computa horas porque su fecha inicial está en vacaciones.",
        ).assertExists()
        composeRule.onNodeWithText("Vacaciones:", substring = true).assertExists()
        assertTrue(days.single { it.date == vacation.startDate }.shifts.isNotEmpty())
    }

    @Test fun calendarVacationMarkerIsOnlyVWhileCoincidentDataRemainsAccessible() {
        val vacation = vacation()
        val firstDate = vacation.startDate
        val days = projectCalendarMonth(
            month = YearMonth.of(2026, 8),
            shifts = listOf(shift(firstDate)),
            explicitDayStatuses = listOf(
                ExplicitDayStatus(firstDate, ExplicitDayStatusType.DAY_OFF),
                ExplicitDayStatus(firstDate.plusDays(1), ExplicitDayStatusType.UNDEFINED),
            ),
            medicalLeaves = emptyList(),
            now = Instant.parse("2026-08-20T12:00:00Z"),
            holidays = listOf(
                Holiday(
                    id = UUID.fromString("60000000-0000-0000-0000-000000000003"),
                    date = firstDate,
                    name = "Feriado ficticio",
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                ),
            ),
            vacations = listOf(vacation),
        )
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = calendarState().copy(days = days),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                )
            }
        }

        assertEquals(
            3,
            composeRule.onAllNodesWithText("V", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(
            3,
            composeRule.onAllNodesWithContentDescription("vacaciones desde", substring = true)
                .fetchSemanticsNodes()
                .size,
        )
        composeRule.onNodeWithContentDescription("feriado Feriado ficticio", substring = true)
            .assertExists()
        composeRule.onNodeWithContentDescription("franco marcado explícitamente", substring = true)
            .assertExists()
        composeRule.onNodeWithContentDescription("día sin definir marcado explícitamente", substring = true)
            .assertExists()
        assertTrue(days.single { it.date == firstDate.plusDays(2) }.isImplicitlyUndefined)
    }

    private fun calendarState(): CalendarUiState {
        val month = YearMonth.of(2026, 8)
        return CalendarUiState(
            visibleMonth = month,
            referenceInstant = Instant.parse("2026-08-14T12:00:00Z"),
            days = projectCalendarMonth(month, emptyList(), emptyList(), emptyList(), Instant.EPOCH),
            loadState = CalendarLoadState.CONTENT,
        )
    }

    private fun vacation() = Vacation(
        id = UUID.fromString("60000000-0000-0000-0000-000000000001"),
        startDate = LocalDate.of(2026, 8, 13),
        endDateInclusive = LocalDate.of(2026, 8, 15),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun shift(date: LocalDate): Shift {
        val zone = ZoneId.of("America/Argentina/Cordoba")
        val start = date.atTime(8, 0).atZone(zone).toInstant()
        return Shift(
            id = UUID.fromString("60000000-0000-0000-0000-000000000002"),
            startAt = start,
            endAt = start.plusSeconds(8 * 3600L),
            zoneId = zone,
            localStartDate = date,
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "VAC",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(8, 0),
            endTimeSnapshot = LocalTime.of(16, 0),
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = null,
            sourceScheduleCombinationId = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
