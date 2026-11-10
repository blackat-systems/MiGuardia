package com.blackatsystems.miguardia

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurface
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsUiState
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadActions
import com.blackatsystems.miguardia.ui.management.V2RecurringActions
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
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupUiState
import com.blackatsystems.miguardia.ui.worksetup.previewV2WorkSetupUiState
import com.blackatsystems.miguardia.weather.WeatherPreferences
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        compose.onNodeWithTag("main-menu-button").performClick()
        compose.onNodeWithTag("drawer-action-calendar-today").performClick()
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
    fun v2ReadyKeepsDisplacedCalendarFunctionsReachableFromTheExistingMenu() {
        var starts = 0
        var repeats = 0
        var entersEdit = 0
        var opensSetup = 0
        setApp(
            state = contentState(),
            manualActions = V2ManualShiftLoadActions(start = { _ -> starts++ }),
            recurringActions = V2RecurringActions(openCreate = { _ -> repeats++ }),
            workSetupActions = WorkSetupActions(openOverview = { opensSetup++ }),
            onEnterManual = { entersEdit++ },
        )

        compose.onNodeWithTag("calendar-v2-repeat-shifts").assertDoesNotExist()
        compose.onNodeWithTag("calendar-work-setup-action").assertDoesNotExist()
        compose.onNodeWithTag("calendar-v2-load-shifts").assertDoesNotExist()
        compose.onNodeWithTag("advanced-options-toggle").assertDoesNotExist()

        compose.onNodeWithTag("main-menu-button").performClick()
        compose.onNodeWithTag("drawer-action-calendar-repeat").performClick()
        compose.onNodeWithTag("main-menu-button").performClick()
        compose.onNodeWithTag("drawer-action-work-setup").performClick()
        compose.onNodeWithTag("main-menu-button").performClick()
        compose.onNodeWithTag("drawer-action-calendar-load").performClick()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, repeats)
            assertEquals(1, entersEdit)
            assertEquals(1, opensSetup)
        }
        compose.onNodeWithText("Cargar datos").assertDoesNotExist()
        compose.onNodeWithText("Editar calendario").assertDoesNotExist()
        compose.onNodeWithText("Informar novedad / notas").assertDoesNotExist()
    }

    @Test
    fun v2DayDetailKeepsNotesAndRoutesEditToExactUnifiedLoad() {
        var openedNotes: UUID? = null
        var editRequests = 0
        var manualRoot: WorkSetupState? = null
        var manualDate: LocalDate? = null
        var confirmedDates: Set<LocalDate>? = null
        val events = mutableListOf<String>()
        val shift = fixtureShift()
        val snapshot = fixtureSnapshot(shift.id)
        val workSetup = previewV2WorkSetupUiState()
        val state = contentState(shift).copy(detailDate = DATE)
        setApp(
            state = state,
            onDismiss = { events += "dismiss" },
            workSetupState = workSetup,
            onEnterManual = {
                events += "enter-manual"
                manualDate = it
            },
            manualActions = V2ManualShiftLoadActions(
                start = {
                    events += "start"
                    manualRoot = it
                },
                confirmDates = {
                    events += "confirm-dates"
                    confirmedDates = it
                },
            ),
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
        compose.onNodeWithText("Notas").assertIsDisplayed()
        compose.onNodeWithTag("v2-edit-day-action").assertDoesNotExist()
        compose.onNodeWithTag("day-edit-menu").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(null, openedNotes)
            assertEquals(0, editRequests)
            assertEquals(workSetup.rootState, manualRoot)
            assertEquals(DATE, manualDate)
            assertEquals(setOf(DATE), confirmedDates)
            assertEquals(listOf("dismiss", "start", "enter-manual", "confirm-dates"), events)
        }
        compose.onNodeWithTag("day-edit-menu").assertDoesNotExist()
        compose.onNodeWithTag("day-other-options").assertDoesNotExist()
        compose.onNodeWithTag("v2-edit-day-action").assertDoesNotExist()
        compose.onNodeWithText("Notas privadas").assertDoesNotExist()
        compose.onNodeWithText("Registrar ausencia").assertDoesNotExist()
        compose.onNodeWithText("Agregar segunda guardia").assertDoesNotExist()
    }

    @Test
    fun v2DayDetailSecondaryOptionsKeepExactEditEntrySeparateFromUnifiedLoad() {
        val events = mutableListOf<String>()
        val shift = fixtureShift()
        val snapshot = fixtureSnapshot(shift.id)
        setApp(
            state = contentState(shift).copy(detailDate = DATE),
            onDismiss = { events += "dismiss" },
            onEnterManual = { events += "enter-manual" },
            manualActions = V2ManualShiftLoadActions(
                start = { events += "start" },
                confirmDates = { events += "confirm-dates" },
            ),
            exceptionsActions = ExceptionsActions(openNotes = { events += "notes" }),
            editState = V2ShiftEditUiState(
                timelineId = TIMELINE_ID,
                date = DATE,
                inspectionState = V2ShiftDayInspectionState.CONTENT,
                dayRows = listOf(V2ShiftEditDayRow(shift, snapshot, 1, 1)),
            ),
            editActions = V2ShiftEditActions(beginDayEditing = { events += "edit-existing" }),
        )

        compose.onNodeWithTag("v2-edit-day-action").assertDoesNotExist()
        compose.onNodeWithTag("day-other-options").assertIsDisplayed().performClick()
        compose.onNodeWithTag("day-edit-menu").assertDoesNotExist()
        compose.onNodeWithTag("day-add-retainer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("day-add-medical-leave").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("v2-edit-day-action").performScrollTo().assertIsDisplayed().performClick()

        compose.runOnIdle { assertEquals(listOf("edit-existing"), events) }
        compose.onNodeWithText("Notas privadas").assertDoesNotExist()
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

        compose.onNodeWithTag("day-edit-menu").assertIsDisplayed()
        compose.onNodeWithText("Notas").performClick()

        compose.onNodeWithText("Notas privadas").assertIsDisplayed()
        compose.onNodeWithTag("v2-edit-day-action").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("dismiss", "notes"), events) }
    }

    @Test
    fun openingWeatherKeepsDayDetailAsTheBackDestination() {
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
                shiftBriefs = mapOf(
                    shift.id to ShiftWeatherBrief(summary, WeatherFreshness.FRESH, shift.sourceObjectiveId),
                ),
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
                        close = {
                            events += "weather-close"
                            weatherState = weatherState.copy(surface = WeatherSurface.NONE)
                        },
                    ),
                )
            }
        }

        compose.onNode(
            hasText("Clima durante la jornada") and hasClickAction(),
        ).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("weather"), events) }
        compose.onNodeWithTag("v2-edit-day-action").assertDoesNotExist()
        compose.onNodeWithTag("weather-close").assertIsDisplayed().performClick()
        compose.onNodeWithTag("day-edit-menu").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("weather", "weather-close"), events) }
    }

    @Test
    fun daySheetReachesWindowBottomAndAppliesTheNavigationInsetOnlyOnce() {
        var dark by mutableStateOf(false)
        var scale by mutableStateOf(TestFontScale.STANDARD)
        var state by mutableStateOf(contentState().copy(detailDate = DATE))
        var navigationInsetPx = 0
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark, fontScale = scale) {
                navigationInsetPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
                MiGuardiaApp(
                    calendarState = state,
                    onPreviousMonth = {}, onNextMonth = {}, onToday = {},
                    onSelectDate = {}, onDismissDate = { state = state.copy(detailDate = null) },
                    onRetry = {},
                )
            }
        }
        for (theme in listOf(false, true)) for (font in TestFontScale.entries) {
            for (shift in listOf(null, fixtureShift())) {
                compose.runOnIdle {
                    dark = theme
                    scale = font
                    state = contentState(shift).copy(detailDate = DATE)
                }
                compose.waitForIdle()
                val context = "dark=$theme font=$font shift=${shift != null}"
                val sheet = compose.onNodeWithTag("day-detail-modal-sheet", useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode()
                var windowRoot = sheet
                while (windowRoot.parent != null) windowRoot = windowRoot.parent!!
                // The public sheet modifier precedes Material's draggable anchor placement.
                // Measure its laid-out Surface, not the tag's pre-placement coordinator.
                val surface = sheet.layoutInfo.coordinates.boundsInWindow()
                val window = windowRoot.layoutInfo.coordinates.boundsInWindow()
                val body = compose.onNodeWithTag("day-detail-scroll", useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInWindow
                assertEquals("$context no transparent margin below the sheet", window.bottom, surface.bottom, 1f)
                val bottomGap = surface.bottom - body.bottom
                assertTrue("$context navigation inset applied more than once: $bottomGap / $navigationInsetPx",
                    bottomGap >= -1f && bottomGap <= navigationInsetPx + 1f)
                assertTrue("$context content not contained", body.left >= surface.left - 1f &&
                    body.right <= surface.right + 1f && body.top >= surface.top - 1f)
                compose.onNodeWithTag("day-edit-menu").performScrollTo().assertIsDisplayed()
            }
        }
    }

    private fun setApp(
        state: CalendarUiState,
        onPrevious: () -> Unit = {},
        onNext: () -> Unit = {},
        onToday: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onEnterEdit: (LocalDate?) -> Unit = {},
        onEnterManual: (LocalDate?) -> Unit = {},
        manualActions: V2ManualShiftLoadActions = V2ManualShiftLoadActions(),
        recurringActions: V2RecurringActions = V2RecurringActions(),
        workSetupActions: WorkSetupActions = WorkSetupActions(),
        workSetupState: WorkSetupUiState = previewV2WorkSetupUiState(),
        exceptionsActions: ExceptionsActions = ExceptionsActions(),
        editState: V2ShiftEditUiState = V2ShiftEditUiState(),
        editActions: V2ShiftEditActions = V2ShiftEditActions(),
    ) {
        var calendarState by mutableStateOf(state)
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendarState,
                    onPreviousMonth = onPrevious,
                    onNextMonth = onNext,
                    onToday = onToday,
                    onSelectDate = {},
                    onDismissDate = {
                        onDismiss()
                        calendarState = calendarState.copy(detailDate = null)
                    },
                    onRetry = {},
                    onEnterCalendarEditMode = onEnterEdit,
                    onEnterManualCalendarSelection = onEnterManual,
                    v2ManualShiftLoadActions = manualActions,
                    v2RecurringActions = recurringActions,
                    workSetupActions = workSetupActions,
                    workSetupState = workSetupState,
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
