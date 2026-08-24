package com.blackatsystems.miguardia

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.weather.ShiftWeatherSummary
import com.blackatsystems.miguardia.core.domain.weather.WeatherCondition
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurface
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsUiState
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadActions
import com.blackatsystems.miguardia.ui.management.V2ShiftDayInspectionState
import com.blackatsystems.miguardia.ui.management.V2ShiftEditActions
import com.blackatsystems.miguardia.ui.management.V2ShiftEditDayRow
import com.blackatsystems.miguardia.ui.management.V2ShiftEditUiState
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.weather.ShiftWeatherBrief
import com.blackatsystems.miguardia.ui.weather.WeatherActions
import com.blackatsystems.miguardia.ui.weather.WeatherSurface
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupActions
import com.blackatsystems.miguardia.weather.WeatherPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalendarComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun controlsAndHorizontalGestureKeepCalendarNavigation() {
        var previous = 0
        var next = 0
        var today = 0
        setApp(
            state = contentState(),
            onPrevious = { previous++ },
            onNext = { next++ },
            onToday = { today++ },
        )

        compose.onNodeWithContentDescription("Mes anterior").performClick()
        compose.onNodeWithContentDescription("Mes siguiente").performClick()
        compose.onNodeWithText("Ir a hoy").performClick()
        compose.onNodeWithTag("month-grid").performTouchInput { swipeLeft() }

        compose.runOnIdle {
            assertEquals(1, previous)
            assertEquals(2, next)
            assertEquals(1, today)
        }
    }

    @Test
    fun loadingAndRecoverableErrorRemainBlockingAndHonest() {
        var retries = 0
        var state by mutableStateOf(
            contentState().copy(days = emptyList(), loadState = CalendarLoadState.LOADING),
        )
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = state,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = { retries++ },
                )
            }
        }
        compose.onNodeWithText("Cargando calendario…").assertIsDisplayed()

        compose.runOnIdle {
            state = state.copy(
                loadState = CalendarLoadState.ERROR,
                errorMessage = "Error ficticio recuperable",
            )
        }
        compose.onNodeWithText("Error ficticio recuperable").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun v2ReadyOffersOnlyManualLoadAndWorkSetup() {
        var starts = 0
        var entersEdit = 0
        var opensSetup = 0
        setApp(
            state = contentState(),
            manualActions = V2ManualShiftLoadActions(start = { _ -> starts++ }),
            workSetupActions = WorkSetupActions(openOverview = { opensSetup++ }),
            onEnterEdit = { entersEdit++ },
        )

        compose.onNodeWithTag("calendar-v2-load-shifts").performScrollTo().performClick()
        compose.onNodeWithTag("calendar-work-setup-action").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, entersEdit)
            assertEquals(1, opensSetup)
        }
        compose.onNodeWithText("Cargar datos").assertDoesNotExist()
        compose.onNodeWithText("Editar calendario").assertDoesNotExist()
        compose.onNodeWithText("Informar novedad / notas").assertDoesNotExist()
    }

    @Test
    fun v2DayDetailKeepsNotesAndExactEditEntry() {
        var openedNotes: UUID? = null
        var editRequests = 0
        val events = mutableListOf<String>()
        val shift = fixtureShift()
        val snapshot = fixtureSnapshot(shift.id)
        val state = contentState(shift).copy(detailDate = DATE)
        setApp(
            state = state,
            onDismiss = { events += "dismiss" },
            exceptionsActions = ExceptionsActions(
                openNotes = {
                    events += "notes"
                    openedNotes = it.id
                },
            ),
            editState = V2ShiftEditUiState(
                timelineId = TIMELINE_ID,
                date = DATE,
                inspectionState = V2ShiftDayInspectionState.CONTENT,
                dayRows = listOf(V2ShiftEditDayRow(shift, snapshot, 1, 1)),
            ),
            editActions = V2ShiftEditActions(beginDayEditing = { editRequests++ }),
        )

        compose.onNodeWithText("Hospital ficticio (HFI)").assertIsDisplayed()
        compose.onNodeWithText("Notas").performClick()
        compose.onNodeWithTag("v2-edit-day-action").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(shift.id, openedNotes)
            assertEquals(1, editRequests)
            assertEquals(listOf("dismiss", "notes"), events)
        }
        compose.onNodeWithText("Registrar ausencia").assertDoesNotExist()
        compose.onNodeWithText("Agregar segunda guardia").assertDoesNotExist()
    }

    @Test
    fun openingNotesReplacesTheDaySheetInsteadOfLeavingItOnTop() {
        val shift = fixtureShift()
        val snapshot = fixtureSnapshot(shift.id)
        val events = mutableListOf<String>()
        var calendarState by mutableStateOf(contentState(shift).copy(detailDate = DATE))
        var exceptionsState by mutableStateOf(
            ExceptionsUiState(
                surface = ExceptionsSurface.NONE,
                holidayMonth = MONTH,
            ),
        )
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendarState,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {
                        events += "dismiss"
                        calendarState = calendarState.copy(detailDate = null)
                    },
                    onRetry = {},
                    exceptionsState = exceptionsState,
                    exceptionsActions = ExceptionsActions(
                        openNotes = { selected ->
                            events += "notes"
                            exceptionsState = exceptionsState.copy(
                                surface = ExceptionsSurface.NOTES,
                                selectedShift = selected,
                            )
                        },
                    ),
                    v2ShiftEditState = V2ShiftEditUiState(
                        timelineId = TIMELINE_ID,
                        date = DATE,
                        inspectionState = V2ShiftDayInspectionState.CONTENT,
                        dayRows = listOf(V2ShiftEditDayRow(shift, snapshot, 1, 1)),
                    ),
                )
            }
        }

        compose.onNodeWithTag("v2-edit-day-action").assertIsDisplayed()
        compose.onNodeWithText("Notas").performClick()

        compose.onNodeWithText("Notas privadas").assertIsDisplayed()
        compose.onNodeWithTag("v2-edit-day-action").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("dismiss", "notes"), events) }
    }

    @Test
    fun openingWeatherReplacesTheDaySheetInsteadOfLeavingItOnTop() {
        val shift = fixtureShift()
        val summary = fixtureWeatherSummary(shift)
        val events = mutableListOf<String>()
        var calendarState by mutableStateOf(contentState(shift).copy(detailDate = DATE))
        var weatherState by mutableStateOf(
            WeatherUiState(
                preferences = WeatherPreferences(
                    enabled = true,
                    providerExplanationAccepted = true,
                ),
                shiftBriefs = mapOf(shift.id to ShiftWeatherBrief(summary, WeatherFreshness.FRESH)),
                isLoading = false,
            ),
        )
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendarState,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {
                        events += "dismiss"
                        calendarState = calendarState.copy(detailDate = null)
                    },
                    onRetry = {},
                    weatherState = weatherState,
                    weatherActions = WeatherActions(
                        openShift = { shiftId ->
                            events += "weather"
                            weatherState = weatherState.copy(
                                surface = WeatherSurface.SHIFT,
                                selectedShift = shift.takeIf { it.id == shiftId },
                                shiftSummary = summary,
                                isLoading = false,
                            )
                        },
                    ),
                )
            }
        }

        compose.onNodeWithText("Clima durante la guardia").performScrollTo().performClick()

        val weatherTitle = compose.onNodeWithText("Clima de la guardia")
        compose.waitUntil(5_000L) {
            runCatching { weatherTitle.fetchSemanticsNode() }.isSuccess
        }
        weatherTitle.performScrollTo()
        compose.waitUntil(5_000L) {
            runCatching { weatherTitle.assertIsDisplayed() }.isSuccess
        }
        weatherTitle.assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("dismiss", "weather"), events) }
    }

    private fun setApp(
        state: CalendarUiState,
        onPrevious: () -> Unit = {},
        onNext: () -> Unit = {},
        onToday: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onEnterEdit: (LocalDate?) -> Unit = {},
        manualActions: V2ManualShiftLoadActions = V2ManualShiftLoadActions(),
        workSetupActions: WorkSetupActions = WorkSetupActions(),
        exceptionsActions: ExceptionsActions = ExceptionsActions(),
        editState: V2ShiftEditUiState = V2ShiftEditUiState(),
        editActions: V2ShiftEditActions = V2ShiftEditActions(),
    ) {
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = state,
                    onPreviousMonth = onPrevious,
                    onNextMonth = onNext,
                    onToday = onToday,
                    onSelectDate = {},
                    onDismissDate = onDismiss,
                    onRetry = {},
                    onEnterCalendarEditMode = onEnterEdit,
                    v2ManualShiftLoadActions = manualActions,
                    workSetupActions = workSetupActions,
                    exceptionsActions = exceptionsActions,
                    v2ShiftEditState = editState,
                    v2ShiftEditActions = editActions,
                )
            }
        }
    }

    private fun contentState(shift: Shift? = null): CalendarUiState {
        val shifts = listOfNotNull(shift)
        return CalendarUiState(
            visibleMonth = MONTH,
            referenceInstant = NOW,
            days = projectCalendarMonth(
                month = MONTH,
                shifts = shifts,
                explicitDayStatuses = emptyList(),
                medicalLeaves = emptyList(),
                now = NOW,
            ),
            hasAnyShifts = shifts.isNotEmpty(),
            hasAnyShiftsLoaded = true,
            loadState = CalendarLoadState.CONTENT,
        )
    }

    private fun fixtureShift(): Shift {
        val start = ZonedDateTime.of(DATE, LocalTime.of(8, 0), AppDefaults.zoneId())
        return Shift(
            id = SHIFT_ID,
            startAt = start.toInstant(),
            endAt = start.plusHours(8).toInstant(),
            zoneId = AppDefaults.zoneId(),
            localStartDate = DATE,
            objectiveNameSnapshot = "Hospital ficticio",
            objectiveAbbreviationSnapshot = "HFI",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(8, 0),
            endTimeSnapshot = LocalTime.of(16, 0),
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = OBJECTIVE_ID,
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    private fun fixtureSnapshot(shiftId: UUID) = ShiftWorkSnapshot(
        shiftId = shiftId,
        timelineId = TIMELINE_ID,
        sector = WorkSector.NURSING,
        configurationRevisionId = UUID(0L, 4L),
        workPlaceId = UUID(0L, 5L),
        objectiveId = OBJECTIVE_ID,
        templateId = UUID(0L, 6L),
        workTypeId = UUID(0L, 7L),
        workTypeNameSnapshot = "Turno asistencial",
        workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
    )

    private fun fixtureWeatherSummary(shift: Shift) = ShiftWeatherSummary(
        shiftStart = shift.startAt,
        shiftEndExclusive = shift.endAt,
        coveredFrom = shift.startAt,
        coveredUntilExclusive = shift.endAt,
        coverage = WeatherCoverage.COMPLETE,
        condition = WeatherCondition.CLEAR,
        minimumTemperatureCelsius = 12.0,
        maximumTemperatureCelsius = 20.0,
        minimumApparentTemperatureCelsius = 11.0,
        maximumApparentTemperatureCelsius = 20.0,
        maximumPrecipitationProbabilityPercent = 0,
        precipitationMillimeters = 0.0,
        maximumWindSpeedKmh = 10.0,
        maximumWindGustKmh = 15.0,
    )

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        val DATE: LocalDate = LocalDate.of(2026, 8, 10)
        val NOW: Instant = Instant.parse("2026-08-01T12:00:00Z")
        val SHIFT_ID: UUID = UUID(0L, 1L)
        val OBJECTIVE_ID: UUID = UUID(0L, 2L)
        val TIMELINE_ID: UUID = UUID(0L, 3L)
    }
}
