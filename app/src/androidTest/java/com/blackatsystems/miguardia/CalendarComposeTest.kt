package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.weather.ShiftWeatherSummary
import com.blackatsystems.miguardia.core.domain.weather.WeatherCondition
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions
import com.blackatsystems.miguardia.ui.management.ManagementActions
import com.blackatsystems.miguardia.ui.management.DayOffDraft
import com.blackatsystems.miguardia.ui.management.ManagementSurface
import com.blackatsystems.miguardia.ui.management.ManagementUiState
import com.blackatsystems.miguardia.ui.management.ShiftDraft
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.weather.ShiftWeatherBrief
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.weather.WeatherPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.Test

class CalendarComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun wakeDevice() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).wakeUp()
    }

    @Test
    fun controlsAndHorizontalGesturesChangeMonthButShortDragDoesNot() {
        composeRule.setContent { CalendarHarness(contentState()) }

        composeRule.onNodeWithText("Agosto de 2026").assertExists()
        composeRule.onNodeWithContentDescription("Mes anterior").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Julio de 2026").assertExists()
        composeRule.onNodeWithContentDescription("Mes siguiente").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Agosto de 2026").assertExists()

        composeRule.onNodeWithTag("month-grid").performTouchInput { swipeLeft() }
        composeRule.waitUntil(3_000) { composeRule.onAllNodesWithText("Septiembre de 2026").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Septiembre de 2026").assertExists()
        composeRule.onNodeWithTag("month-grid").performTouchInput { swipeRight() }
        composeRule.waitUntil(3_000) { composeRule.onAllNodesWithText("Agosto de 2026").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Agosto de 2026").assertExists()

        composeRule.onNodeWithTag("month-grid").performTouchInput {
            down(center)
            moveBy(Offset(-20f, 0f))
            up()
        }
        composeRule.onNodeWithText("Agosto de 2026").assertExists()

        composeRule.onNodeWithContentDescription("Mes anterior").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Ir a hoy").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Agosto de 2026").assertExists()
    }

    @Test
    fun gridShowsEveryStateAndDayDetailKeepsMultipleShiftsAccessible() {
        composeRule.setContent { CalendarHarness(contentState()) }

        composeRule.onNodeWithText("ABCDE", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("23:00–03:00", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("completed-day-2026-08-02").assertExists()
        composeRule.onNodeWithText("CUR", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Ahora", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("PRO", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Próx.", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("CAN", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Cancel.", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("AUS", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Aus.", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("F", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("CM", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("3L", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("4M", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("5X", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription(
            "día sin definir marcado explícitamente",
            substring = true,
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            "Domingo 9 de agosto de 2026, sin definir",
        ).assertExists()

        composeRule.onNodeWithContentDescription("guardia DOS", substring = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Objetivo dos (DOS)").assertExists()
        composeRule.onNodeWithText("Objetivo tres (TRE)").assertExists()
        composeRule.onAllNodesWithText("Completada").assertCountEquals(2)
    }

    @Test
    fun loadingAndRecoverableErrorHaveHonestActions() {
        var retries = 0
        composeRule.setContent {
            MiGuardiaApp(
                calendarState = CalendarUiState(
                    visibleMonth = YearMonth.of(2026, 8),
                    referenceInstant = REFERENCE_NOW,
                    loadState = CalendarLoadState.ERROR,
                    errorMessage = "Error ficticio recuperable",
                ),
                onPreviousMonth = {},
                onNextMonth = {},
                onToday = {},
                onSelectDate = {},
                onDismissDate = {},
                onRetry = { retries += 1 },
            )
        }

        composeRule.onNodeWithText("Error ficticio recuperable").assertExists()
        composeRule.onNodeWithTag("month-grid").assertDoesNotExist()
        composeRule.onNodeWithText("Reintentar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun consultationDetailNeverExposesOrInvokesCalendarMutations() {
        var calendarState by mutableStateOf(contentState())
        var writes = 0
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = calendarState,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = { calendarState = calendarState.copy(selectedDate = it) },
                    onDismissDate = { calendarState = calendarState.copy(selectedDate = null) },
                    onRetry = {},
                    managementActions = ManagementActions(
                        openAddShift = { _, _ -> writes += 1 },
                        openDayOffs = { _, _ -> writes += 1 },
                        openEditShift = { writes += 1 },
                        deleteShift = { writes += 1 },
                    ),
                    exceptionsActions = ExceptionsActions(openShift = { writes += 1 }),
                )
            }
        }

        composeRule.onNodeWithContentDescription("guardia ABCDE", substring = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Objetivo de abreviatura máxima (ABCDE)").assertExists()
        composeRule.onNodeWithText("Informar novedad / notas").assertDoesNotExist()
        composeRule.onNodeWithText("Editar").assertDoesNotExist()
        composeRule.onNodeWithText("Agregar una segunda guardia").assertDoesNotExist()
        composeRule.onNodeWithText("Eliminar").assertDoesNotExist()
        composeRule.onNodeWithText("Agregar guardia").assertDoesNotExist()
        composeRule.onNodeWithText("Agregar francos").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, writes) }
    }

    @Test
    fun firstShiftCallToActionEntersEditAndOpensRealFormOnValidVisibleDate() {
        var calendarState by mutableStateOf(
            contentState(YearMonth.of(2026, 9)).copy(hasAnyShifts = false),
        )
        var managementState by mutableStateOf(ManagementUiState())
        var requestedDate: LocalDate? = null
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = calendarState,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onEnterCalendarEditMode = { date ->
                        calendarState = calendarState.copy(
                            interactionMode = CalendarInteractionMode.EDIT,
                            selectedDate = date,
                        )
                    },
                    onRetry = {},
                    managementState = managementState,
                    managementActions = ManagementActions(
                        openAddShift = { month, date ->
                            requestedDate = date
                            managementState = managementState.copy(
                                surface = ManagementSurface.SHIFT_FORM,
                                shiftDraft = ShiftDraft(
                                    month = month,
                                    selectedDates = setOfNotNull(date),
                                ),
                            )
                        },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Cargar mi primera guardia").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Guardias").assertExists()
        composeRule.runOnIdle {
            assertEquals(CalendarInteractionMode.EDIT, calendarState.interactionMode)
            assertEquals(LocalDate.of(2026, 9, 1), requestedDate)
        }
    }

    @Test
    fun guardOutsideVisibleMonthPreventsFalseFirstLoadState() {
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = contentState(YearMonth.of(2026, 9)).copy(hasAnyShifts = true),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Editar calendario").assertExists()
        composeRule.onNodeWithText("Cargar mi primera guardia").assertDoesNotExist()
    }

    @Test
    fun finishAndBackLeaveEditModeWithoutChangingVisibleMonth() {
        composeRule.setContent { CalendarHarness(contentState()) }

        composeRule.onNodeWithText("Editar calendario").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithContentDescription("Mes anterior")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Julio de 2026").assertExists()
        composeRule.onNodeWithText("Terminar").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Editar calendario").assertExists()
        composeRule.onNodeWithText("Julio de 2026").assertExists()

        composeRule.onNodeWithText("Editar calendario").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Editando calendario").assertExists()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("Editar calendario").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Julio de 2026").assertExists()
    }

    @Test
    fun backProtectsManagementDraftBeforeLeavingCalendarEditMode() {
        var calendarState by mutableStateOf(
            contentState().copy(interactionMode = CalendarInteractionMode.EDIT),
        )
        var managementState by mutableStateOf(
            ManagementUiState(
                surface = ManagementSurface.SHIFT_FORM,
                shiftDraft = ShiftDraft(
                    month = YearMonth.of(2026, 8),
                    selectedDates = setOf(LocalDate.of(2026, 8, 20)),
                ),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = calendarState,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onFinishCalendarEditMode = {
                        calendarState = calendarState.copy(interactionMode = CalendarInteractionMode.VIEW)
                    },
                    onRetry = {},
                    managementState = managementState,
                    managementActions = ManagementActions(
                        discardForm = { managementState = ManagementUiState() },
                    ),
                )
            }
        }

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressBack()
        composeRule.onNodeWithText("Descartar cambios").assertExists()
        composeRule.onNodeWithText("Seguir editando").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Guardias").assertExists()

        device.pressBack()
        composeRule.onNodeWithText("Descartar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Editando calendario").assertExists()
        device.pressBack()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("Editar calendario").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun editIndicatorAndFinishRemainReachableInLightDarkAndTwoHundredPercent() {
        var themeMode by mutableStateOf(AppThemeMode.DARK)
        composeRule.setContent {
            MiGuardiaTheme(darkTheme = themeMode == AppThemeMode.DARK, appZoom = AppZoom.EXTRA_LARGE) {
                MiGuardiaApp(
                    calendarState = contentState().copy(interactionMode = CalendarInteractionMode.EDIT),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    appZoom = AppZoom.EXTRA_LARGE,
                    appThemeMode = themeMode,
                )
            }
        }

        composeRule.onNodeWithText("Editando calendario").assertExists()
        composeRule.onNodeWithText("Terminar").performScrollTo().assertExists()
        composeRule.runOnIdle { themeMode = AppThemeMode.LIGHT }
        composeRule.onNodeWithText("Editando calendario").assertExists()
        composeRule.onNodeWithText("Terminar").performScrollTo().assertExists()
    }

    @Test
    fun editModeEmptyDateOpensRealShiftFormForVisibleMonth() {
        var calendarState by mutableStateOf(contentState())
        var managementState by mutableStateOf(ManagementUiState())
        var requestedMonth: YearMonth? = null
        var requestedDate: LocalDate? = null
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = calendarState,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = { calendarState = calendarState.copy(selectedDate = it) },
                    onDismissDate = { calendarState = calendarState.copy(selectedDate = null) },
                    onEnterCalendarEditMode = {
                        calendarState = calendarState.copy(interactionMode = CalendarInteractionMode.EDIT)
                    },
                    onFinishCalendarEditMode = {
                        calendarState = calendarState.copy(interactionMode = CalendarInteractionMode.VIEW)
                    },
                    onRetry = {},
                    managementState = managementState,
                    managementActions = ManagementActions(
                        openAddShift = { month, date ->
                            requestedMonth = month
                            requestedDate = date
                            managementState = managementState.copy(
                                surface = ManagementSurface.SHIFT_FORM,
                                shiftDraft = ShiftDraft(
                                    month = month,
                                    selectedDates = setOf(date ?: month.atDay(1)),
                                ),
                            )
                        },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Editar calendario").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Editando calendario").assertExists()
        composeRule.onNodeWithText("Terminar").assertExists()
        composeRule.onNodeWithContentDescription("Jueves 20 de agosto de 2026, sin definir")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Agregar guardia").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Guardias").assertExists()
        composeRule.onNodeWithText("Revisar y guardar").assertExists()
        composeRule.runOnIdle {
            assertEquals(YearMonth.of(2026, 8), requestedMonth)
            assertEquals(LocalDate.of(2026, 8, 20), requestedDate)
        }
    }

    @Test
    fun emptyDateInEditOffersGuardOrDayOffWithoutVacationOrRedundantMonthMenu() {
        val selectedDate = LocalDate.of(2026, 8, 20)
        var managementState by mutableStateOf(ManagementUiState())
        var requestedDayOffMonth: YearMonth? = null
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = contentState().copy(
                        selectedDate = selectedDate,
                        interactionMode = CalendarInteractionMode.EDIT,
                    ),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    managementState = managementState,
                    managementActions = ManagementActions(
                        openAddShift = { month, date ->
                            managementState = managementState.copy(
                                surface = ManagementSurface.SHIFT_FORM,
                                shiftDraft = ShiftDraft(
                                    month = month,
                                    selectedDates = setOf(date ?: month.atDay(1)),
                                ),
                            )
                        },
                        openDayOffs = { month, date ->
                            requestedDayOffMonth = month
                            managementState = managementState.copy(
                                surface = ManagementSurface.DAY_OFF_FORM,
                                dayOffDraft = DayOffDraft(month, setOfNotNull(date)),
                            )
                        },
                    ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Fotos del cronograma del mes").assertExists()
        composeRule.onNodeWithContentDescription("Menú del mes").assertDoesNotExist()
        composeRule.onNodeWithText("Agregar guardia").assertExists()
        composeRule.onNodeWithText("Agregar francos").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Agregar vacaciones").assertDoesNotExist()
        composeRule.onNodeWithText("Francos").assertExists()
        composeRule.runOnIdle { assertEquals(YearMonth.of(2026, 8), requestedDayOffMonth) }
    }

    @Test
    fun occupiedShiftDetailOrdersEditAddSecondAndConfirmedDelete() {
        val state = contentState().copy(
            selectedDate = LocalDate.of(2026, 8, 2),
            interactionMode = CalendarInteractionMode.EDIT,
        )
        var edited: UUID? = null
        var addedSecond: LocalDate? = null
        var deleted: UUID? = null
        var openedExceptions: UUID? = null
        var dismissed = 0
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = state,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = { dismissed += 1 },
                    onRetry = {},
                    managementActions = ManagementActions(
                        openEditShift = { edited = it.id },
                        openAddShift = { _, date -> addedSecond = date },
                        deleteShift = { deleted = it },
                    ),
                    exceptionsActions = ExceptionsActions(
                        openShift = { openedExceptions = it.id },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Agregar una segunda guardia").assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(LocalDate.of(2026, 8, 2), addedSecond) }
        composeRule.onNodeWithText("Agregar guardia").assertDoesNotExist()
        composeRule.onNodeWithText("Agregar francos").assertDoesNotExist()
        composeRule.onAllNodesWithText("Editar")[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertTrue(edited != null)
        }
        composeRule.onNodeWithText("Duplicar").assertDoesNotExist()
        composeRule.onNodeWithText("Avisos").assertDoesNotExist()
        composeRule.onAllNodesWithText("Informar novedad / notas")[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertTrue(openedExceptions != null)
            assertTrue(dismissed >= 2)
        }
        composeRule.onNodeWithText("Agregar vacaciones").assertDoesNotExist()
        composeRule.onAllNodesWithText("Eliminar")[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Eliminar guardia").assertExists()
        composeRule.onAllNodesWithText("Eliminar")[1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertTrue(deleted != null) }
    }

    @Test
    fun upcomingShiftDetailShowsWeatherBriefForItsFullSchedule() {
        val selectedDate = LocalDate.of(2026, 8, 14)
        val state = contentState().copy(selectedDate = selectedDate)
        val shift = requireNotNull(state.days.first { it.date == selectedDate }.shifts.single().shift)
        val summary = ShiftWeatherSummary(
            shiftStart = shift.startAt,
            shiftEndExclusive = shift.endAt,
            coveredFrom = shift.startAt,
            coveredUntilExclusive = shift.endAt,
            coverage = WeatherCoverage.COMPLETE,
            condition = WeatherCondition.RAIN,
            minimumTemperatureCelsius = 8.0,
            maximumTemperatureCelsius = 14.0,
            minimumApparentTemperatureCelsius = 7.0,
            maximumApparentTemperatureCelsius = 13.0,
            maximumPrecipitationProbabilityPercent = 70,
            precipitationMillimeters = 4.0,
            maximumWindSpeedKmh = 25.0,
            maximumWindGustKmh = 40.0,
        )
        composeRule.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = state,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    weatherState = WeatherUiState(
                        preferences = WeatherPreferences(enabled = true, providerExplanationAccepted = true),
                        shiftBriefs = mapOf(shift.id to ShiftWeatherBrief(summary, WeatherFreshness.FRESH)),
                        isLoading = false,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Clima durante la guardia").assertExists()
        composeRule.onNodeWithText("Lluvia · 8–14 °C").assertExists()
        composeRule.onNodeWithText("Probabilidad máxima de lluvia: 70 %").assertExists()
        composeRule.onNodeWithText("Cobertura completa del horario").assertExists()
    }

    @Composable
    private fun CalendarHarness(initialState: CalendarUiState) {
        var state by remember { mutableStateOf(initialState) }
        fun moveTo(month: YearMonth) {
            state = contentState(month = month).copy(
                selectedDate = null,
                interactionMode = state.interactionMode,
                hasAnyShifts = state.hasAnyShifts,
            )
        }
        MaterialTheme {
            MiGuardiaApp(
                calendarState = state,
                onPreviousMonth = { moveTo(state.visibleMonth.minusMonths(1)) },
                onNextMonth = { moveTo(state.visibleMonth.plusMonths(1)) },
                onToday = { moveTo(YearMonth.of(2026, 8)) },
                onSelectDate = { state = state.copy(selectedDate = it) },
                onDismissDate = { state = state.copy(selectedDate = null) },
                onEnterCalendarEditMode = {
                    state = state.copy(
                        interactionMode = CalendarInteractionMode.EDIT,
                        selectedDate = it ?: state.selectedDate,
                    )
                },
                onFinishCalendarEditMode = {
                    state = state.copy(interactionMode = CalendarInteractionMode.VIEW)
                },
                onRetry = {},
            )
        }
    }

    private fun contentState(month: YearMonth = YearMonth.of(2026, 8)): CalendarUiState {
        val shifts = if (month == YearMonth.of(2026, 8)) fixtureShifts() else emptyList()
        val statuses = if (month == YearMonth.of(2026, 8)) {
            listOf(
                ExplicitDayStatus(LocalDate.of(2026, 8, 6), ExplicitDayStatusType.DAY_OFF),
                ExplicitDayStatus(LocalDate.of(2026, 8, 7), ExplicitDayStatusType.UNDEFINED),
            )
        } else {
            emptyList()
        }
        val leaves = if (month == YearMonth.of(2026, 8)) {
            listOf(
                MedicalLeave(
                    id = UUID.fromString("10000000-0000-0000-0000-000000000001"),
                    startDate = LocalDate.of(2026, 8, 8),
                    endDateInclusive = LocalDate.of(2026, 8, 8),
                    privateNote = "Nota ficticia que no debe mostrarse",
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                ),
            )
        } else {
            emptyList()
        }
        return CalendarUiState(
            visibleMonth = month,
            referenceInstant = REFERENCE_NOW,
            days = projectCalendarMonth(
                month = month,
                shifts = shifts,
                explicitDayStatuses = statuses,
                medicalLeaves = leaves,
                now = REFERENCE_NOW,
                vacations = if (month == YearMonth.of(2026, 8)) {
                    listOf(
                        Vacation(
                            id = UUID.fromString("10000000-0000-0000-0000-000000000099"),
                            startDate = LocalDate.of(2026, 8, 10),
                            endDateInclusive = LocalDate.of(2026, 8, 11),
                            createdAt = Instant.EPOCH,
                            updatedAt = Instant.EPOCH,
                        ),
                    )
                } else {
                    emptyList()
                },
            ),
            loadState = CalendarLoadState.CONTENT,
        )
    }

    private fun fixtureShifts(): List<Shift> = listOf(
        shift("CMP", "Objetivo completado", 1, 8, 16),
        shift("ABCDE", "Objetivo de abreviatura máxima", 2, 23, 3),
        shift("DOS", "Objetivo dos", 3, 6, 10),
        shift("TRE", "Objetivo tres", 3, 12, 16),
        shift("CAN", "Objetivo cancelado", 4, 8, 16, ShiftStatus.CANCELLED),
        shift("AUS", "Objetivo ausencia", 5, 8, 16, ShiftStatus.ABSENT),
        shift("CUR", "Objetivo en curso", 13, 11, 13),
        shift("PRO", "Objetivo próximo", 14, 15, 18),
    )

    private fun shift(
        abbreviation: String,
        name: String,
        day: Int,
        startHour: Int,
        endHour: Int,
        status: ShiftStatus = ShiftStatus.PLANNED,
    ): Shift {
        val date = LocalDate.of(2026, 8, day)
        val startTime = LocalTime.of(startHour, 0)
        val endTime = LocalTime.of(endHour, 0)
        val start = ZonedDateTime.of(date, startTime, AppDefaults.zoneId())
        val endDate = if (endTime <= startTime) date.plusDays(1) else date
        val end = ZonedDateTime.of(endDate, endTime, AppDefaults.zoneId())
        return Shift(
            id = UUID.nameUUIDFromBytes("$abbreviation-$day-$startHour".toByteArray()),
            startAt = start.toInstant(),
            endAt = end.toInstant(),
            zoneId = AppDefaults.zoneId(),
            localStartDate = date,
            objectiveNameSnapshot = name,
            objectiveAbbreviationSnapshot = abbreviation,
            objectiveAddressSnapshot = "Calle ficticia 123",
            startTimeSnapshot = startTime,
            endTimeSnapshot = endTime,
            colorArgbSnapshot = 0xFF3F51B5.toInt(),
            position = "Puesto ficticio",
            status = status,
            sourceObjectiveId = null,
            sourceScheduleCombinationId = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    private companion object {
        val REFERENCE_NOW: Instant = ZonedDateTime.of(
            LocalDate.of(2026, 8, 13),
            LocalTime.of(12, 0),
            AppDefaults.zoneId(),
        ).toInstant()
    }
}
