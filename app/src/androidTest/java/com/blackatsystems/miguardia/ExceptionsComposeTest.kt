package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurface
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurfaceHost
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsUiState
import com.blackatsystems.miguardia.ui.exceptions.HolidayDraft
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
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
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun holidaysKeepMainCalendarSelectionAndLocalEditing() {
        val holiday = Holiday(UUID(0L, 1L), LocalDate.of(2026, 8, 17), "Feriado ficticio", NOW, NOW)
        var edited: Holiday? = null
        var saves: List<HolidayConflictPolicy?> = emptyList()
        var state by mutableStateOf(
            ExceptionsUiState(
                surface = ExceptionsSurface.HOLIDAYS,
                holidayMonth = YearMonth.of(2026, 8),
                holidays = listOf(holiday),
                holidayDraft = HolidayDraft(datesText = "2026-08-17,2026-08-18"),
            ),
        )
        compose.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    state,
                    ExceptionsActions(
                        updateHolidayDraft = { transform ->
                            state = state.copy(holidayDraft = transform(state.holidayDraft))
                        },
                        editHoliday = { edited = it },
                        saveHolidays = { policy -> saves = saves + policy },
                    ),
                )
            }
        }

        compose.onNodeWithTag("holiday-date-selector").assertDoesNotExist()
        compose.onNodeWithText("17/08/2026, 18/08/2026").assertExists()
        compose.onNodeWithText("Nombre opcional").performTextInput("Feriado manual")
        compose.onNodeWithText("Guardar feriado(s)").performScrollTo().performClick()
        compose.onNodeWithText("Feriado ficticio").assertExists()
        compose.onNodeWithText("Editar").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals("2026-08-17,2026-08-18", state.holidayDraft.datesText)
            assertEquals("Feriado manual", state.holidayDraft.name)
            assertEquals(listOf(null), saves)
            assertEquals(holiday, edited)
        }
        compose.onNodeWithText("Novedades").assertDoesNotExist()
    }

    @Test
    fun holidayDateSelectionUsesTheOnlyMainMonthGrid() {
        val month = YearMonth.of(2026, 8)
        val first = LocalDate.of(2026, 8, 17)
        val second = first.plusDays(1)
        var calendar by mutableStateOf(calendarState(month))
        var exceptions by mutableStateOf(
            ExceptionsUiState(
                surface = ExceptionsSurface.HOLIDAYS,
                holidayMonth = month,
            ),
        )
        val actions = ExceptionsActions(
            beginHolidaySelection = { requestedMonth ->
                exceptions = exceptions.copy(
                    surface = ExceptionsSurface.NONE,
                    holidayMonth = requestedMonth,
                    holidaySelectionActive = true,
                )
            },
            updateHolidaySelection = { requestedMonth, dates ->
                exceptions = exceptions.copy(
                    holidayMonth = requestedMonth,
                    holidayDraft = exceptions.holidayDraft.copy(
                        datesText = dates.sorted().joinToString(","),
                    ),
                )
            },
            confirmHolidaySelection = { requestedMonth, dates ->
                exceptions = exceptions.copy(
                    surface = ExceptionsSurface.HOLIDAYS,
                    holidayMonth = requestedMonth,
                    holidaySelectionActive = false,
                    holidayDraft = exceptions.holidayDraft.copy(
                        datesText = dates.sorted().joinToString(","),
                    ),
                )
            },
            cancelHolidaySelection = {
                exceptions = exceptions.copy(
                    surface = ExceptionsSurface.HOLIDAYS,
                    holidaySelectionActive = false,
                )
            },
        )
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendar,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    onEnterCalendarEditMode = {
                        calendar = calendar.copy(
                            interactionMode = CalendarInteractionMode.EDIT,
                            editSelectedDates = emptySet(),
                        )
                    },
                    onEditSelectionChange = { dates -> calendar = calendar.copy(editSelectedDates = dates) },
                    onFinishCalendarEditMode = {
                        calendar = calendar.copy(
                            interactionMode = CalendarInteractionMode.VIEW,
                            editSelectedDates = emptySet(),
                        )
                    },
                    exceptionsState = exceptions,
                    exceptionsActions = actions,
                )
            }
        }

        compose.onNodeWithTag("holiday-date-selector").assertDoesNotExist()
        compose.onNodeWithTag("holiday-open-calendar-selection").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("month-grid").assertCountEquals(1)
        compose.onNodeWithTag("holiday-calendar-selection").assertExists()
        compose.onNodeWithTag("calendar-v2-load-shifts").assertDoesNotExist()
        compose.onNodeWithTag("calendar-v2-repeat-shifts").assertDoesNotExist()
        compose.onNodeWithTag("calendar-work-setup-action").assertDoesNotExist()

        compose.onNodeWithTag("day-$first").performScrollTo().performClick()
        compose.onNodeWithTag("day-$second").performScrollTo().performClick()
        compose.onNodeWithText("2 fechas seleccionadas.").assertExists()
        compose.onNodeWithContentDescription("seleccionado como feriado", substring = true)
            .assertExists()
        compose.onNodeWithTag("holiday-confirm-calendar-selection").performScrollTo().performClick()

        compose.onNodeWithTag("holiday-date-selector").assertDoesNotExist()
        compose.onNodeWithText("17/08/2026, 18/08/2026").assertExists()
        compose.runOnIdle {
            assertEquals(setOf(first, second), exceptions.holidayDraft.selectedDates)
            assertEquals(CalendarInteractionMode.VIEW, calendar.interactionMode)
            assertEquals(emptySet<LocalDate>(), calendar.editSelectedDates)
        }
    }

    @Test
    fun holidayConflictsExposeReplaceKeepAndCancelPolicies() {
        val conflict = LocalDate.of(2026, 8, 17)
        val policies = mutableListOf<HolidayConflictPolicy?>()
        var cancelled = 0
        compose.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    ExceptionsUiState(
                        surface = ExceptionsSurface.HOLIDAYS,
                        holidayMonth = YearMonth.of(2026, 8),
                        holidayDraft = HolidayDraft(
                            datesText = conflict.toString(),
                            conflictDates = setOf(conflict),
                        ),
                    ),
                    ExceptionsActions(
                        saveHolidays = policies::add,
                        cancelHolidayConflict = { cancelled++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Fechas con feriado").assertExists()
        compose.onNodeWithText("Conservar existentes").performClick()
        compose.runOnIdle { assertEquals(listOf(HolidayConflictPolicy.KEEP_EXISTING), policies) }

        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
    }

    @Test
    fun closingWithAHolidayDraftRequiresExplicitDiscard() {
        var closes = 0
        var state by mutableStateOf(
            ExceptionsUiState(
                surface = ExceptionsSurface.HOLIDAYS,
                holidayMonth = YearMonth.of(2026, 8),
            ),
        )
        compose.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    state,
                    ExceptionsActions(
                        close = { closes++ },
                        updateHolidayDraft = { transform ->
                            state = state.copy(holidayDraft = transform(state.holidayDraft))
                        },
                    ),
                )
            }
        }

        compose.onNodeWithText("Nombre opcional").performTextInput("Borrador ficticio")
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Hay datos del feriado sin guardar.").assertExists()
        compose.onNodeWithText("Seguir editando").performClick()
        compose.runOnIdle { assertEquals(0, closes) }

        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Descartar").performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    @Test
    fun notesRemainPrivateAndDeletionRequiresConfirmation() {
        compose.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    ExceptionsUiState(
                        surface = ExceptionsSurface.NOTES,
                        holidayMonth = YearMonth.of(2026, 8),
                        selectedShift = SHIFT,
                        notes = listOf(ShiftNote(UUID(0L, 2L), SHIFT.id, "Texto privado ficticio", NOW, NOW)),
                    ),
                    ExceptionsActions(),
                )
            }
        }

        compose.onNodeWithText("Notas privadas").assertExists()
        compose.onNodeWithText("Texto privado ficticio").performScrollTo().assertExists()
        compose.onNodeWithText("Eliminar").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar nota").assertExists()
        compose.onNodeWithText("La nota privada se eliminará.").assertExists()
        compose.onNodeWithText("Registrar ausencia").assertDoesNotExist()
        compose.onNodeWithText("Agregar segunda guardia").assertDoesNotExist()
    }

    @Test
    fun noteDraftUsesOnlyTheSelectedV2Shift() {
        var body = ""
        var saves = 0
        var state by mutableStateOf(
            ExceptionsUiState(
                surface = ExceptionsSurface.NOTES,
                selectedShift = SHIFT,
            ),
        )
        compose.setContent {
            MaterialTheme {
                ExceptionsSurfaceHost(
                    state,
                    ExceptionsActions(
                        updateNoteDraft = { transform ->
                            state = state.copy(noteDraft = transform(state.noteDraft))
                            body = state.noteDraft.body
                        },
                        saveNote = { saves++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Nota").performTextInput("Dato ficticio")
        compose.onNodeWithText("Guardar nota").performClick()
        compose.runOnIdle {
            assertEquals("Dato ficticio", body)
            assertEquals(1, saves)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-13T12:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val SHIFT = Shift(
            id = UUID(0L, 10L),
            startAt = NOW,
            endAt = NOW.plusSeconds(8 * 3_600),
            zoneId = ZONE,
            localStartDate = LocalDate.of(2026, 8, 13),
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "OBJ",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(9, 0),
            endTimeSnapshot = LocalTime.of(17, 0),
            colorArgbSnapshot = 0xFF123456.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = UUID(0L, 11L),
            createdAt = NOW,
            updatedAt = NOW,
        )

        fun calendarState(month: YearMonth): CalendarUiState = CalendarUiState(
            visibleMonth = month,
            referenceInstant = NOW,
            days = projectCalendarMonth(
                month = month,
                shifts = emptyList(),
                explicitDayStatuses = emptyList(),
                medicalLeaves = emptyList(),
                now = NOW,
            ),
            loadState = CalendarLoadState.CONTENT,
        )
    }
}
