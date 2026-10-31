package com.blackatsystems.miguardia

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.espresso.Espresso.pressBack
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftSituationExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursProgress
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.resolveHoursReferenceSegment
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.Stage2DayActions
import com.blackatsystems.miguardia.ui.DayDetailSheet
import com.blackatsystems.miguardia.ui.availability.AvailabilityLoadState
import com.blackatsystems.miguardia.ui.availability.AvailabilityActions
import com.blackatsystems.miguardia.ui.availability.AvailabilitySource
import com.blackatsystems.miguardia.ui.availability.AvailabilityUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarDateTextBounds
import com.blackatsystems.miguardia.ui.calendar.CalendarDateFontSizePx
import com.blackatsystems.miguardia.ui.calendar.CalendarDateTextOverflow
import com.blackatsystems.miguardia.ui.calendar.enterEditing
import com.blackatsystems.miguardia.ui.calendar.finishEditing
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasLoadState
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasActions
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasSource
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasUiState
import com.blackatsystems.miguardia.core.domain.weather.ShiftWeatherSummary
import com.blackatsystems.miguardia.core.domain.weather.WeatherCondition
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.ui.weather.ShiftWeatherBrief
import com.blackatsystems.miguardia.ui.weather.WeatherActions
import com.blackatsystems.miguardia.ui.weather.WeatherSurface
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.ui.notifications.NotificationActions
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.weather.WeatherPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.After
import kotlinx.coroutines.delay

class CalendarCommonV2ComposeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    @Before
    fun useTheProductThemeInAPortraitTestActivity() {
        compose.activityRule.scenario.onActivity { activity ->
            originalOrientation = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(30_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
    }

    @After
    fun restoreOnlyThisTestActivityOrientation() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = originalOrientation }
    }

    @Test
    fun stage3LongPressVisibleAlternativeAndActionSheetShareOneSelection() {
        val first = LocalDate.of(2026, 8, 3)
        val second = LocalDate.of(2026, 8, 4)
        var state by mutableStateOf(calendarState().copy(lastOpenedDate = first))
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = state,
                    onPreviousMonth = {}, onNextMonth = {}, onToday = {},
                    onSelectDate = {}, onDismissDate = {}, onRetry = {},
                    onEnterCalendarEditMode = { date -> state = state.enterEditing(date) },
                    onEditSelectionChange = { dates -> state = state.copy(editSelectedDates = dates) },
                    onFinishCalendarEditMode = { state = state.finishEditing() },
                    onCalendarCollapsedChange = { collapsed -> state = state.copy(calendarCollapsed = collapsed) },
                )
            }
        }

        compose.onNodeWithTag("calendar-primary-actions").assertDoesNotExist()
        compose.onNodeWithTag("main-menu-button").performClick()
        compose.onNodeWithTag("drawer-action-calendar-select").performScrollTo().assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("day-$first").performTouchInput { longClick() }
        compose.onNodeWithText("1 día seleccionado").assertIsDisplayed()
        compose.onNodeWithTag("day-$second").performClick()
        compose.onNodeWithText("2 días seleccionados").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(calendarCollapsed = true) }
        compose.onNodeWithTag("day-${LocalDate.of(2026, 8, 20)}").assertIsDisplayed()
        compose.onNodeWithTag("calendar-selection-continue").performClick()
        compose.onNodeWithText("Cargar jornadas").assertIsDisplayed()
        compose.onNodeWithText("Marcar Franco").assertIsDisplayed()
        compose.onNodeWithText("Marcar Sin definir").assertIsDisplayed()
        compose.onNodeWithText("Agregar Retén").assertIsDisplayed()
        compose.onNodeWithText("Ausencia").assertDoesNotExist()
    }

    @Test
    fun backDismissesGeneralDialogBeforeCancellingSelectionWithoutWriting() {
        val selectedDate = LocalDate.of(2026, 8, 3)
        var state by mutableStateOf(calendarState().enterEditing(selectedDate))
        var finishes = 0
        var previews = 0
        var writes = 0
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = state,
                    onPreviousMonth = {}, onNextMonth = {}, onToday = {},
                    onSelectDate = {}, onDismissDate = {}, onRetry = {},
                    onEditSelectionChange = { dates -> state = state.copy(editSelectedDates = dates) },
                    onResumeEditSelection = { state = state.copy(editSelectionConfirmed = false) },
                    onFinishCalendarEditMode = {
                        finishes += 1
                        state = state.finishEditing()
                    },
                    onPreviewCalendarBatch = { _, _ -> previews += 1 },
                    onApplyCalendarBatch = { writes += 1 },
                )
            }
        }

        compose.onNodeWithTag("calendar-selection-continue").performClick()
        compose.onNodeWithText("Marcar Franco").assertIsDisplayed()

        pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Marcar Franco").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, finishes)
            assertEquals(CalendarInteractionMode.EDIT, state.interactionMode)
            assertEquals(setOf(selectedDate), state.editSelectedDates)
            assertEquals(0, previews)
            assertEquals(0, writes)
        }

        pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("calendar-primary-actions").assertDoesNotExist()
        compose.onNodeWithTag("day-${LocalDate.of(2026, 8, 31)}").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, finishes)
            assertEquals(CalendarInteractionMode.VIEW, state.interactionMode)
            assertEquals(emptySet<LocalDate>(), state.editSelectedDates)
            assertEquals(0, previews)
            assertEquals(0, writes)
        }
    }

    @Test
    fun stage2ActionsDistinguishImplicitUnknownAndTargetTheCorrectShiftCard() {
        val first = shift("UNO", "Objetivo uno", TWO_SHIFTS_DATE, 6, 10)
        val second = shift("DOS", "Objetivo dos", TWO_SHIFTS_DATE, 14, 18)
        var selectedShift: UUID? = null
        var toggledDate: LocalDate? = null
        var state by mutableStateOf(
            calendarState(shifts = listOf(first, second), detailDate = TWO_SHIFTS_DATE).copy(
                stage2Expectations = mapOf(first.id to expectation(first), second.id to expectation(second)),
            ),
        )
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = state,
                    onPreviousMonth = {}, onNextMonth = {}, onToday = {}, onSelectDate = {},
                    onDismissDate = {}, onRetry = {},
                    stage2Actions = Stage2DayActions(
                        toggleUndefined = { date, _ -> toggledDate = date },
                        changeSituation = { expected, _, _, _, _, _, _, _ ->
                            selectedShift = expected.planned.shift.id
                        },
                    ),
                )
            }
        }

        compose.onNodeWithText("Lunes 3 de agosto de 2026").assertIsDisplayed()
        compose.onNodeWithTag("shift-absence-${second.id}").assertDoesNotExist()
        compose.onNodeWithText("Agregar carpeta médica").assertDoesNotExist()
        // Editar día opens the unified load flow; situation actions belong to
        // the existing day-options menu. Keep the shift identity assertions.
        compose.onNodeWithTag("day-other-options").performScrollTo().performClick()
        compose.onNodeWithTag("shift-absence-${second.id}").performScrollTo().performClick()
        compose.onNodeWithText("DOS ·", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Confirmar").performClick()
        compose.runOnIdle { assertEquals(second.id, selectedShift) }

        compose.runOnIdle {
            state = calendarState(
                shifts = listOf(second.copy(status = ShiftStatus.ABSENT)),
                detailDate = TWO_SHIFTS_DATE,
            ).copy(
                stage2Expectations = mapOf(
                    second.id to expectation(second.copy(status = ShiftStatus.ABSENT)),
                ),
            )
        }
        compose.onNodeWithTag("shift-edit-situation-${second.id}").performScrollTo().performClick()
        compose.onNodeWithTag("shift-situation-dialog").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()

        compose.runOnIdle {
            state = calendarState(
                explicitStatuses = listOf(ExplicitDayStatus(UNDEFINED_DATE, ExplicitDayStatusType.UNDEFINED)),
                detailDate = UNDEFINED_DATE,
            )
        }
        compose.onNodeWithText("Condición del día").assertIsDisplayed()
        compose.onNodeWithText("Sin definir").assertIsDisplayed()
        compose.onNodeWithTag("day-other-options").performScrollTo().performClick()
        compose.onNodeWithText("Quitar marca Sin definir").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(UNDEFINED_DATE, toggledDate) }

        val emptyDate = LocalDate.of(2026, 8, 29)
        compose.runOnIdle { state = calendarState(detailDate = emptyDate) }
        compose.onNodeWithText("Jornadas del día").assertDoesNotExist()
        compose.onNodeWithText("Condición del día").assertDoesNotExist()
        compose.onNodeWithText("Sin definir").assertDoesNotExist()
        compose.onNodeWithTag("day-other-options").performScrollTo().performClick()
        compose.onNodeWithText("Sábado 29 de Agosto del 2026").assertIsDisplayed()
        compose.onNodeWithText("Agregar jornada o franco").assertIsDisplayed()
        compose.onNodeWithText("Agregar disponibilidad de retén").assertIsDisplayed()
        compose.onNodeWithText("Agregar carpeta médica").assertIsDisplayed()
        compose.onNodeWithText("Marcar Sin definir").assertDoesNotExist()
        compose.onNodeWithText("Disponibilidad").assertDoesNotExist()
        compose.onNodeWithText("Volver al detalle").assertDoesNotExist()
    }

    @Test
    fun completedDetailKeepsWholeStatusAndActionsInNarrowWindowsAtEveryThemeAndFontScale() {
        var dark by mutableStateOf(false)
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var baseWidth by mutableStateOf(320)
        val completed = shift("CMP", "Hospital completado", COMPLETED_DATE, 6, 10)
        val day = calendarState(listOf(completed)).days.single { it.date == COMPLETED_DATE }
        var noteActions = 0
        compose.setContent {
            AndroidFontScaleTheme(fontScale = TestFontScale.STANDARD) {
                Box(Modifier.width(baseWidth.dp).height(480.dp)) {
                    AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                        DayDetailSheet(
                            day = day,
                            referenceInstant = REFERENCE_NOW,
                            onEditDay = null,
                            onOpenNotes = { noteActions++ },
                            onOpenNotifications = {},
                            onOpenWeather = {},
                            weatherState = WeatherUiState(),
                        )
                    }
                }
            }
        }
        for (width in listOf(320, 360)) for (theme in listOf(false, true)) for (scale in TestFontScale.entries) {
            compose.runOnIdle { baseWidth = width; dark = theme; fontScale = scale }
            val context = "width=$width theme=$theme fontScale=$scale"
            val badge = compose.onNodeWithText("Completada", useUnmergedTree = true)
            badge.performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            badge.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals(context, "Completada", layout.layoutInput.text.text)
            assertEquals(context, 1, layout.lineCount)
            assertFalse("$context text=${layout.layoutInput.text.text} size=${layout.size} " +
                "constraints=${layout.layoutInput.constraints} widthOverflow=${layout.didOverflowWidth} " +
                "heightOverflow=${layout.didOverflowHeight} paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height} " +
                "intrinsicWidth=${layout.multiParagraph.intrinsics.maxIntrinsicWidth} " +
                "line0=${layout.getLineLeft(0)}..${layout.getLineRight(0)} " +
                "bounds=${badge.fetchSemanticsNode().boundsInRoot}", layout.hasVisualOverflow)
            assertFalse(context, layout.isLineEllipsized(0))
            val bounds = badge.fetchSemanticsNode().boundsInRoot
            val card = compose.onNodeWithTag("shift-detail-card-${completed.id}").fetchSemanticsNode().boundsInRoot
            assertTrue("$context clipped state", bounds.width + 1 >= layout.size.width &&
                bounds.left >= card.left && bounds.right <= card.right)
            compose.onNodeWithTag("shift-notes-${completed.id}").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("shift-notifications-${completed.id}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("shift-weather-${completed.id}").performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle { assertEquals(12, noteActions) }
    }

    @Test
    fun dayDetailKeepsSameObjectiveSameTimeShiftsIndependentAndWeatherReturnsToIt() {
        val first = shift("MIS", "Mismo objetivo", TWO_SHIFTS_DATE, 6, 10)
        val second = first.copy(
            id = UUID.fromString("88000000-0000-0000-0000-000000000002"),
            createdAt = Instant.ofEpochSecond(1),
            updatedAt = Instant.ofEpochSecond(1),
        )
        var noteId: UUID? = null
        var noticeId: UUID? = null
        var weatherId: UUID? = null
        var weatherState by mutableStateOf(
            WeatherUiState(
                preferences = WeatherPreferences(enabled = false, providerExplanationAccepted = true),
                isLoading = false,
            ),
        )
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = calendarState(listOf(first, second), detailDate = TWO_SHIFTS_DATE),
                    onPreviousMonth = {}, onNextMonth = {}, onToday = {}, onSelectDate = {},
                    onDismissDate = {}, onRetry = {},
                    exceptionsActions = com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions(
                        openNotes = { noteId = it.id },
                    ),
                    notificationActions = NotificationActions(openShift = { noticeId = it.id }),
                    weatherState = weatherState,
                    weatherActions = WeatherActions(
                        openShift = { id ->
                            weatherId = id
                            weatherState = weatherState.copy(
                                surface = WeatherSurface.SHIFT,
                                selectedShift = listOf(first, second).first { it.id == id },
                                isLoading = false,
                            )
                        },
                        close = { weatherState = weatherState.copy(surface = WeatherSurface.NONE) },
                    ),
                )
            }
        }

        compose.onNodeWithText("Lunes 3 de agosto de 2026").assertIsDisplayed()
        compose.onNodeWithText("Jornadas del día").assertIsDisplayed()
        compose.onNodeWithTag("shift-detail-card-${first.id}").assertExists()
        compose.onNodeWithTag("shift-detail-card-${second.id}").assertExists()
        compose.onAllNodesWithText("03/08/2026 06:00 – 03/08/2026 10:00").assertCountEquals(2)

        compose.onNodeWithTag("shift-notes-${second.id}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(second.id, noteId) }
        compose.onNodeWithTag("shift-notifications-${first.id}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(first.id, noticeId) }
        compose.onNodeWithTag("shift-weather-${second.id}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(second.id, weatherId) }
        compose.onNodeWithText("Clima desactivado").assertIsDisplayed()
        compose.onNodeWithTag("weather-close").assertIsDisplayed().performClick()
        compose.onNodeWithTag("shift-detail-card-${first.id}").assertExists()
        compose.onNodeWithTag("shift-detail-card-${second.id}").assertExists()
    }

    @Test
    fun commonCalendarMarkersAndDayDetailsRemainAvailableInV2() {
        var state by mutableStateOf(contentState())
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = state,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = { state = state.copy(detailDate = it) },
                    onDismissDate = { state = state.copy(detailDate = null) },
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("2 turnos", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("2 turnos", substring = true).assertExists()
        compose.onNodeWithTag("completed-day-$COMPLETED_DATE").assertExists()
        compose.onNodeWithText("CMP", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("CMP de 06:00 a 10:00", substring = true).assertExists()
        compose.onNodeWithText("06–10", useUnmergedTree = true).assertExists()
        assertSignal(COMPLETED_DATE, "work")
        compose.onNodeWithTag("calendar-check-$COMPLETED_DATE", useUnmergedTree = true).assertIsDisplayed()
        assertOptionalCellLabelWholeOrAbsent(COMPLETED_DATE, "Completada", setOf("CMP", "06–10"))
        compose.onAllNodesWithContentDescription("completada", substring = true).assertCountEquals(2)
        compose.onNodeWithText("F", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("CM", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Fer.", substring = true, useUnmergedTree = true).assertExists()
        assertSignal(DAY_OFF_DATE, "day_off")
        assertSignal(MEDICAL_DATE, "medical")
        assertSignal(HOLIDAY_DATE, "holiday")
        compose.onNodeWithContentDescription(
            "día sin definir marcado explícitamente",
            substring = true,
        ).assertExists()

        dayCell(COMPLETED_DATE).assertIsDisplayed().performClick()
        val completedId = state.days.single { it.date == COMPLETED_DATE }.shifts.single().shift.id
        val completedCard = hasAnyAncestor(hasTestTag("shift-detail-card-$completedId"))
        compose.onNodeWithText("Hospital completado (CMP)").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Completada") and completedCard, useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasText("04/08/2026 06:00 – 04/08/2026 10:00") and completedCard, useUnmergedTree = true)[0]
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state = state.copy(detailDate = null) }

        compose.runOnIdle { state = state.copy(lastOpenedDate = VACATION_DATE) }
        compose.onAllNodesWithText("V", useUnmergedTree = true).assertCountEquals(2)

        compose.runOnIdle { state = state.copy(detailDate = TWO_SHIFTS_DATE) }
        compose.onNodeWithText("Hospital Norte (HNO)").assertIsDisplayed()
        compose.onNodeWithText("Hospital Sur (HSU)").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Notas").fetchSemanticsNodes().also { nodes ->
            check(nodes.size == 2) { "Cada jornada V2 debe conservar su acceso a Notas." }
        }

        compose.runOnIdle { state = state.copy(detailDate = DAY_OFF_DATE) }
        compose.onNodeWithText("Franco").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(detailDate = UNDEFINED_DATE) }
        compose.onNodeWithText("Sin definir").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(detailDate = MEDICAL_DATE) }
        compose.onNodeWithText("Carpeta médica · 08/08/2026 – 08/08/2026").assertIsDisplayed()
        compose.onNodeWithText("Agregar carpeta médica").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(detailDate = HOLIDAY_DATE) }
        compose.onNodeWithText("Feriado · Feriado ficticio").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(detailDate = VACATION_DATE) }
        compose.onNodeWithText("Vacaciones ·", substring = true).assertIsDisplayed()
    }

    @Test
    fun loadingWeatherMustNotDismissANewlyOpenedDaySheet() {
        val shift = shift("CLI", "Clínica ficticia", WEATHER_DATE, 15, 23)
        var calendar by mutableStateOf(calendarState(listOf(shift)))
        var weather by mutableStateOf(WeatherUiState(
            preferences = WeatherPreferences(enabled = true, providerExplanationAccepted = true),
            isLoading = false,
        ))
        var dismissals = 0
        compose.setContent {
            AndroidFontScaleTheme {
                LaunchedEffect(weather.loadingBriefIds) {
                    if (weather.loadingBriefIds.isNotEmpty()) {
                        delay(20)
                        weather = weather.copy(loadingBriefIds = emptySet())
                    }
                }
                MiGuardiaApp(
                    calendarState = calendar,
                    onPreviousMonth = {}, onNextMonth = {}, onToday = {},
                    onSelectDate = { calendar = calendar.copy(detailDate = it) },
                    onDismissDate = { dismissals++; calendar = calendar.copy(detailDate = null) },
                    onRetry = {},
                    weatherState = weather,
                    weatherActions = WeatherActions(
                        loadBriefs = { weather = weather.copy(loadingBriefIds = it) },
                        clearBriefs = { weather = weather.copy(loadingBriefIds = emptySet()) },
                    ),
                )
            }
        }
        repeat(3) { opening ->
            compose.runOnIdle { calendar = calendar.copy(detailDate = WEATHER_DATE) }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(3_000)
            compose.onNodeWithTag("day-detail-modal-sheet").assertIsDisplayed()
            compose.onNodeWithText("Viernes 14 de agosto de 2026").assertIsDisplayed()
            compose.runOnIdle { assertEquals("El clima no debe cerrar la hoja", opening, dismissals) }
            pressBack()
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(opening + 1, dismissals) }
        }
    }

    @Test
    fun upcomingV2ShiftKeepsItsWeatherBrief() {
        val shift = shift("CLI", "Clínica ficticia", WEATHER_DATE, 15, 23)
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
        val state = calendarState(
            shifts = listOf(shift),
            detailDate = WEATHER_DATE,
        )

        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = state,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    weatherState = WeatherUiState(
                        preferences = WeatherPreferences(
                            enabled = true,
                            providerExplanationAccepted = true,
                        ),
                        shiftBriefs = mapOf(
                            shift.id to ShiftWeatherBrief(
                                summary,
                                WeatherFreshness.FRESH,
                                shift.sourceObjectiveId,
                            ),
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithText("Clima durante la jornada").assertExists()
        compose.onNodeWithText("Lluvia · 8–14 °C").assertExists()
        compose.onNodeWithText("Probabilidad máxima de lluvia: 70 %").assertExists()
        compose.onNodeWithText("Cobertura completa del horario").assertExists()
    }

    @Test
    fun cancelledAndAbsentShiftsKeepTheirCellStateAndSingleDayDetail() {
        val cancelled = shift("CAN", "Objetivo cancelado ficticio", CANCELLED_DATE, 8, 12)
            .copy(status = ShiftStatus.CANCELLED)
        val absent = shift("AUS", "Objetivo ausente ficticio", ABSENT_DATE, 8, 12)
            .copy(status = ShiftStatus.ABSENT)
        var state by mutableStateOf(
            calendarState(
                shifts = listOf(cancelled, absent),
            ),
        )
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = state,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = { state = state.copy(detailDate = it) },
                    onDismissDate = { state = state.copy(detailDate = null) },
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Cancelada", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("day-$CANCELLED_DATE").performClick()
        compose.onNodeWithText("Objetivo cancelado ficticio (CAN)").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(detailDate = null) }
        compose.onNodeWithText("Ausente", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("day-$ABSENT_DATE").performClick()
        compose.onNodeWithText("Objetivo ausente ficticio (AUS)").assertIsDisplayed()
    }

    @Test
    fun shiftExtraAvailabilityAndCommonMarkersCoexistInOneCellAndSemantics() {
        var calendar by mutableStateOf(coexistingCalendar())
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = calendar,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = { calendar = calendar.copy(detailDate = it) },
                    onDismissDate = { calendar = calendar.copy(detailDate = null) },
                    onRetry = {},
                    hoursAndExtrasState = hoursState(),
                    availabilityState = availabilityState(),
                )
            }
        }

        compose.onNodeWithContentDescription("COX de 06:00 a 10:00", substring = true).assertExists()
        // Native text metrics can omit optional labels. Conditions must remain
        // identifiable through the measured signal band and complete detail.
        assertConditionSignals(COMPOSITE_DATE,
            listOf("work", "day_off", "medical", "holiday", "extra", "availability"))
        listOf("franco marcado explícitamente", "carpeta médica", "feriado Feriado compuesto").forEach {
            compose.onNodeWithContentDescription(it, substring = true).assertExists()
        }
        compose.onNodeWithContentDescription("un trabajo extra independiente", substring = true).assertExists()
        compose.onNodeWithContentDescription(
            "una ventana de disponibilidad: Disponible para llamado",
            substring = true,
        )
            .assertExists()
        compose.onNodeWithText("Nota médica privada", substring = true).assertDoesNotExist()
        dayCell(COMPOSITE_DATE).performClick()
        assertCoexistingDetail()
    }

    @Test
    fun multipleAvailabilityUsesAQuantityAndKeepsEveryLabelInSemantics() {
        val secondWindow = COMPOSITE_AVAILABILITY.copy(
            id = UUID.fromString("96000000-0000-0000-0000-000000000009"),
            start = COMPOSITE_DATE.atTime(19, 0).atZone(AppDefaults.zoneId()).toInstant(),
            end = COMPOSITE_DATE.atTime(22, 0).atZone(AppDefaults.zoneId()).toInstant(),
            labelSnapshot = "Retén",
        )
        var calendar by mutableStateOf(calendarState().copy(lastOpenedDate = COMPOSITE_DATE))
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = calendar,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = { calendar = calendar.copy(detailDate = it) },
                    onDismissDate = { calendar = calendar.copy(detailDate = null) },
                    onRetry = {},
                    availabilityState = availabilityState(
                        windows = listOf(COMPOSITE_AVAILABILITY, secondWindow),
                    ),
                )
            }
        }

        assertOptionalCellLabelWholeOrAbsent(COMPOSITE_DATE, "2 disponibil.", setOf("?"))
        assertSignal(COMPOSITE_DATE, "availability")
        compose.onNodeWithContentDescription(
            "2 ventanas de disponibilidad: Disponible para llamado / Retén",
            substring = true,
        ).assertExists()
        dayCell(COMPOSITE_DATE).performClick()
        compose.onNodeWithText("Disponible para llamado · 05/08/2026 14:00 – 05/08/2026 18:00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Retén · 05/08/2026 19:00 – 05/08/2026 22:00").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun partialExtraAndAvailabilityErrorsKeepTheirLastMarkersAndExposeRetry() {
        var retries = 0
        val shift = shift("ERR", "Hospital con fuentes recuperables", COMPOSITE_DATE, 6, 10)
        var calendar by mutableStateOf(calendarState(shifts = listOf(shift)).copy(lastOpenedDate = COMPOSITE_DATE))
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = calendar,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = { calendar = calendar.copy(detailDate = it) },
                    onDismissDate = { calendar = calendar.copy(detailDate = null) },
                    onRetry = {},
                    hoursAndExtrasState = hoursState().copy(
                        loadState = HoursAndExtrasLoadState.ERROR,
                        message = "Fallo recuperable de extras",
                    ),
                    hoursAndExtrasActions = HoursAndExtrasActions(retry = { retries += 1 }),
                    availabilityState = availabilityState().copy(
                        loadState = AvailabilityLoadState.ERROR,
                        message = "Fallo recuperable de disponibilidad",
                    ),
                    availabilityActions = AvailabilityActions(retry = { retries += 1 }),
                )
            }
        }

        compose.onNodeWithText("Fallo recuperable de extras").assertExists()
        compose.onNodeWithText("Fallo recuperable de disponibilidad").assertExists()
        assertConditionSignals(COMPOSITE_DATE, listOf("work", "extra", "availability"))
        compose.onNodeWithContentDescription("un trabajo extra independiente", substring = true).assertExists()
        compose.onNodeWithContentDescription(
            "una ventana de disponibilidad: Disponible para llamado", substring = true).assertExists()
        compose.onAllNodesWithText("Reintentar").assertCountEquals(2)
        compose.onAllNodesWithText("Reintentar")[0].performClick()
        compose.onAllNodesWithText("Reintentar")[1].performClick()
        compose.runOnIdle { check(retries == 2) }
        dayCell(COMPOSITE_DATE).performClick()
        listOf("Trabajo extra · Refuerzo ficticio", "Disponible para llamado · 05/08/2026 14:00 – 05/08/2026 18:00").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun compactCellsKeepIdentifiableSignalsAndAllCoexistingConditionsInTheirDetail() {
        var calendar by mutableStateOf(coexistingCalendar())
        compose.setContent {
            AndroidFontScaleTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    Box(Modifier.sizeIn(maxWidth = 380.dp, maxHeight = 520.dp).fillMaxSize()) {
                        AndroidFontScaleTheme(fontScale = TestFontScale.EXTRA_LARGE) {
                            MiGuardiaApp(
                                calendarState = calendar,
                                onPreviousMonth = {}, onNextMonth = {}, onToday = {},
                                onSelectDate = { calendar = calendar.copy(detailDate = it) },
                                onDismissDate = { calendar = calendar.copy(detailDate = null) }, onRetry = {},
                                hoursAndExtrasState = hoursState(), availabilityState = availabilityState(),
                            )
                        }
                    }
                }
            }
        }
        listOf("work", "day_off", "medical", "more").forEach { assertSignal(COMPOSITE_DATE, it) }
        compose.onNodeWithText("COX", useUnmergedTree = true).assertDoesNotExist()
        listOf("franco marcado explícitamente", "carpeta médica", "feriado Feriado compuesto",
            "un trabajo extra independiente", "una ventana de disponibilidad: Disponible para llamado").forEach {
            compose.onNodeWithContentDescription(it, substring = true).assertExists()
        }
        dayCell(COMPOSITE_DATE).performClick()
        assertCoexistingDetail()
    }

    @Test
    fun twoDigitCompletedAndSelectedDateDoesNotOverlapItsVectorCheckAtTwoHundredPercent() {
        val date = LocalDate.of(2026, 8, 24)
        val now = date.plusDays(1).atTime(LocalTime.NOON).atZone(AppDefaults.zoneId()).toInstant()
        var calendar by mutableStateOf(calendarState(
            shifts = listOf(shift("CHK", "Objetivo de check ficticio", date, 6, 10)), referenceNow = now,
        ))
        var baseDensity = 0f
        var expectedDateFontPx = 0f
        compose.setContent {
            AndroidFontScaleTheme {
                val density = LocalDensity.current.density
                SideEffect { baseDensity = density }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    Box(Modifier.sizeIn(maxWidth = 380.dp, maxHeight = 520.dp).fillMaxSize()) {
                        AndroidFontScaleTheme(fontScale = TestFontScale.EXTRA_LARGE) {
                            val nativeDensity = LocalDensity.current
                            val dateFontPx = with(nativeDensity) { 14.sp.toPx() }
                            SideEffect {
                                assertEquals("Font size must not rescale geometry", density, nativeDensity.density, 0f)
                                assertEquals(2f, nativeDensity.fontScale, 0f)
                                expectedDateFontPx = dateFontPx
                            }
                            MiGuardiaApp(
                                calendarState = calendar, onPreviousMonth = {}, onNextMonth = {}, onToday = {},
                                onSelectDate = {}, onDismissDate = {}, onRetry = {},
                                onEnterCalendarEditMode = { calendar = calendar.enterEditing(it) },
                                onEditSelectionChange = { calendar = calendar.copy(editSelectedDates = it) },
                                onFinishCalendarEditMode = { calendar = calendar.finishEditing() },
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertDateAndVectorCheck(date, baseDensity, expectedDateFontPx)
        dayCell(date).assertIsDisplayed().performTouchInput { longClick() }
        compose.waitForIdle()
        assertTrue(dayCell(date).fetchSemanticsNode().config[SemanticsProperties.Selected])
        assertDateAndVectorCheck(date, baseDensity, expectedDateFontPx)
        compose.onNodeWithContentDescription("día completado", substring = true).assertExists()
        compose.onNodeWithContentDescription("seleccionado para editar", substring = true).assertExists()
    }

    private fun assertDateAndVectorCheck(date: LocalDate, baseDensity: Float, expectedDateFontPx: Float) {
        val node = dayCell(date).fetchSemanticsNode()
        val cell = node.boundsInRoot
        val renderedDate = node.config[CalendarDateTextBounds]
        val check = compose.onNodeWithTag("calendar-check-$date", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(expectedDateFontPx, node.config[CalendarDateFontSizePx], .1f)
        assertFalse("Date must render every digit without clipping", node.config[CalendarDateTextOverflow])
        // One-line Trim.Both keeps natural FontMetrics: 16sp×2 was not a 32-dp cap.
        // The actual natural date and signal band must fit together without cutting glyphs.
        assertTrue("Natural date plus indicator must fit the 48-base-dp budget: $renderedDate / $check",
            renderedDate.height + check.height + (check.top - renderedDate.bottom) +
                2f * (renderedDate.top - cell.top) <= 48f * baseDensity)
        assertTrue("Calendar visual cell must retain 48 base dp: $cell",
            cell.width >= 48f * baseDensity && cell.height >= 48f * baseDensity)
        listOf(renderedDate, check).forEach { bounds ->
            assertTrue("Rendered date/check must remain inside $cell: $bounds",
                bounds.width > 0f && bounds.height > 0f && bounds.left >= cell.left && bounds.right <= cell.right &&
                    bounds.top >= cell.top && bounds.bottom <= cell.bottom)
        }
        assertTrue("Vector check must not invade the two-digit date: $renderedDate / $check",
            renderedDate.right <= check.left || check.right <= renderedDate.left ||
                renderedDate.bottom <= check.top || check.bottom <= renderedDate.top)
    }

    private fun assertSignal(date: LocalDate, kind: String) {
        val signal = compose.onNodeWithTag("calendar-signal-$kind-$date", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val cell = dayCell(date).fetchSemanticsNode().boundsInRoot
        assertTrue("Signal $kind must occupy a visible area inside $date: $signal / $cell",
            signal.width > 0f && signal.height > 0f && signal.left >= cell.left && signal.right <= cell.right &&
                signal.top >= cell.top && signal.bottom <= cell.bottom)
    }

    private fun assertConditionSignals(date: LocalDate, orderedConditions: List<String>) {
        val cell = dayCell(date).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Signal checks must use native geometric density", density > 0f)
        // Independent geometry oracle: 8-dp icons, 2-dp gaps, 2-dp side padding.
        // A truncated band reserves its last slot for a visible '+'; it cannot
        // silently drop conditions or draw icons outside the cell.
        val slots = ((cell.width / density - 4f + 2f) / 10f).toInt().coerceAtLeast(1)
        val visibleCount = if (orderedConditions.size <= slots) orderedConditions.size else slots - 1
        orderedConditions.take(visibleCount).forEach { assertSignal(date, it) }
        orderedConditions.drop(visibleCount).forEach {
            compose.onNodeWithTag("calendar-signal-$it-$date", useUnmergedTree = true).assertDoesNotExist()
        }
        if (visibleCount < orderedConditions.size) assertSignal(date, "more")
        else compose.onNodeWithTag("calendar-signal-more-$date", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun assertOptionalCellLabelWholeOrAbsent(date: LocalDate, label: String, otherLabels: Set<String>) {
        val cellMatcher = hasTestTag("day-$date") or hasTestTag("completed-day-$date")
        val descendants = hasAnyAncestor(cellMatcher)
        val cell = dayCell(date).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val allowedText = otherLabels + label + date.dayOfMonth.toString()
        compose.onAllNodes(descendants, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { SemanticsProperties.Text in it.config }
            .flatMap { it.config[SemanticsProperties.Text] }
            .forEach { text ->
                assertTrue("Unexpected or partial text in $date: ${text.text}", text.text in allowedText)
            }

        // Native width/height can omit a secondary label, never clip its value.
        // Its signal, complete accessible description and opened detail are
        // asserted unconditionally by each caller, not replaced by this branch.
        val labels = compose.onAllNodes(hasText(label) and descendants, useUnmergedTree = true)
        val count = labels.fetchSemanticsNodes().size
        assertTrue("Optional label must not be duplicated in $date: $label", count <= 1)
        if (count == 0) return
        val node = labels[0].assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(label, layout.layoutInput.text.text)
        assertEquals("Optional label must occupy one complete line: $label", 1, layout.lineCount)
        assertFalse("Optional label must not overflow in $date: $label", layout.hasVisualOverflow)
        assertFalse("Optional label must not be ellipsized in $date: $label", layout.isLineEllipsized(0))
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue("Optional label must remain wholly inside $date: $bounds / $cell",
            bounds.width > 0f && bounds.height > 0f && bounds.width + 1f >= layout.size.width &&
                bounds.height + 1f >= layout.size.height && bounds.left >= cell.left && bounds.right <= cell.right &&
                bounds.top >= cell.top && bounds.bottom <= cell.bottom)
    }

    private fun dayCell(date: LocalDate) = compose.onNode(
        hasTestTag("day-$date") or hasTestTag("completed-day-$date"),
    )

    private fun assertCoexistingDetail() {
        compose.onNodeWithText("Hospital coexistente (COX)").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("05/08/2026 06:00 – 05/08/2026 10:00", useUnmergedTree = true)[0]
            .performScrollTo().assertIsDisplayed()
        listOf("Franco", "Carpeta médica · 05/08/2026 – 05/08/2026", "Feriado · Feriado compuesto",
            "Trabajo extra · Refuerzo ficticio", "Disponible para llamado · 05/08/2026 14:00 – 05/08/2026 18:00").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Nota médica privada", substring = true).assertDoesNotExist()
    }

    private fun coexistingCalendar() = calendarState(
        shifts = listOf(shift("COX", "Hospital coexistente", COMPOSITE_DATE, 6, 10)),
        explicitStatuses = listOf(ExplicitDayStatus(COMPOSITE_DATE, ExplicitDayStatusType.DAY_OFF)),
        medicalLeaves = listOf(MedicalLeave(UUID(0L, 41L), COMPOSITE_DATE, COMPOSITE_DATE,
            "Nota médica privada que no debe aparecer", Instant.EPOCH, Instant.EPOCH)),
        holidays = listOf(Holiday(UUID(0L, 42L), COMPOSITE_DATE, "Feriado compuesto", Instant.EPOCH, Instant.EPOCH)),
    ).copy(lastOpenedDate = COMPOSITE_DATE)

    @Test
    fun transientAvailabilityMessageClearsTheVisibleSourceWhenHoursHasAPersistentError() {
        var hoursClears = 0
        var availabilityClears = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = calendarState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    hoursAndExtrasState = hoursState().copy(
                        loadState = HoursAndExtrasLoadState.ERROR,
                        message = "Fallo persistente de horas",
                    ),
                    hoursAndExtrasActions = HoursAndExtrasActions(
                        clearMessage = { hoursClears += 1 },
                    ),
                    availabilityState = availabilityState().copy(
                        message = "Disponibilidad actualizada",
                    ),
                    availabilityActions = AvailabilityActions(
                        clearMessage = { availabilityClears += 1 },
                    ),
                )
            }
        }

        compose.onNodeWithTag("calendar-header-notice").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Aviso: Disponibilidad actualizada. Tocar para ver el mensaje completo",
        ).assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag("calendar-notice-details").assertIsDisplayed()
        compose.onNodeWithText("Disponibilidad actualizada").assertIsDisplayed()
        compose.onNodeWithText("Entendido").performClick()
        compose.mainClock.advanceTimeBy(2_600L)
        compose.runOnIdle {
            check(hoursClears == 0)
            check(availabilityClears == 1)
        }
    }

    private fun contentState(): CalendarUiState = calendarState(
        shifts = listOf(
            shift("HNO", "Hospital Norte", TWO_SHIFTS_DATE, 6, 10),
            shift("HSU", "Hospital Sur", TWO_SHIFTS_DATE, 12, 16),
            shift("CMP", "Hospital completado", COMPLETED_DATE, 6, 10),
        ),
        explicitStatuses = listOf(
            ExplicitDayStatus(DAY_OFF_DATE, ExplicitDayStatusType.DAY_OFF),
            ExplicitDayStatus(UNDEFINED_DATE, ExplicitDayStatusType.UNDEFINED),
        ),
        medicalLeaves = listOf(
            MedicalLeave(
                id = UUID(0L, 30L),
                startDate = MEDICAL_DATE,
                endDateInclusive = MEDICAL_DATE,
                privateNote = "Texto privado que no debe mostrarse",
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        ),
        holidays = listOf(
            Holiday(UUID(0L, 31L), HOLIDAY_DATE, "Feriado ficticio", Instant.EPOCH, Instant.EPOCH),
        ),
        vacations = listOf(
            Vacation(UUID(0L, 32L), VACATION_DATE, VACATION_DATE.plusDays(1), Instant.EPOCH, Instant.EPOCH),
        ),
    ).copy(lastOpenedDate = TWO_SHIFTS_DATE)

    private fun calendarState(
        shifts: List<Shift> = emptyList(),
        explicitStatuses: List<ExplicitDayStatus> = emptyList(),
        medicalLeaves: List<MedicalLeave> = emptyList(),
        holidays: List<Holiday> = emptyList(),
        vacations: List<Vacation> = emptyList(),
        detailDate: LocalDate? = null,
        referenceNow: Instant = REFERENCE_NOW,
    ) = CalendarUiState(
        visibleMonth = MONTH,
        referenceInstant = referenceNow,
        days = projectCalendarMonth(
            month = MONTH,
            shifts = shifts,
            explicitDayStatuses = explicitStatuses,
            medicalLeaves = medicalLeaves,
            now = referenceNow,
            holidays = holidays,
            vacations = vacations,
        ),
        detailDate = detailDate,
        hasAnyShifts = shifts.isNotEmpty(),
        hasAnyShiftsLoaded = true,
        loadState = CalendarLoadState.CONTENT,
    )

    private fun shift(
        abbreviation: String,
        name: String,
        date: LocalDate,
        startHour: Int,
        endHour: Int,
    ): Shift {
        val startTime = LocalTime.of(startHour, 0)
        val endTime = LocalTime.of(endHour, 0)
        val start = ZonedDateTime.of(date, startTime, AppDefaults.zoneId())
        val endDate = if (endTime <= startTime) date.plusDays(1) else date
        val end = ZonedDateTime.of(endDate, endTime, AppDefaults.zoneId())
        return Shift(
            id = UUID.nameUUIDFromBytes("$abbreviation-$date-$startHour".toByteArray()),
            startAt = start.toInstant(),
            endAt = end.toInstant(),
            zoneId = AppDefaults.zoneId(),
            localStartDate = date,
            objectiveNameSnapshot = name,
            objectiveAbbreviationSnapshot = abbreviation,
            objectiveAddressSnapshot = "Dirección ficticia",
            startTimeSnapshot = startTime,
            endTimeSnapshot = endTime,
            colorArgbSnapshot = 0xFF315DA8.toInt(),
            position = "Puesto ficticio",
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = UUID.nameUUIDFromBytes("objective-$abbreviation".toByteArray()),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    private fun expectation(shift: Shift): ShiftSituationExpectation {
        val objectiveId = requireNotNull(shift.sourceObjectiveId)
        return ShiftSituationExpectation(
            planned = V2ShiftWrite(
                shift,
                ShiftWorkSnapshot(
                    shiftId = shift.id,
                    timelineId = UUID.nameUUIDFromBytes("timeline".toByteArray()),
                    sector = WorkSector.PRIVATE_SECURITY,
                    configurationRevisionId = UUID.nameUUIDFromBytes("revision".toByteArray()),
                    workPlaceId = UUID.nameUUIDFromBytes("place-${shift.id}".toByteArray()),
                    objectiveId = objectiveId,
                    templateId = UUID.nameUUIDFromBytes("template-${shift.id}".toByteArray()),
                    workTypeId = UUID.nameUUIDFromBytes("type".toByteArray()),
                    workTypeNameSnapshot = "Jornada habitual",
                    workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
                ),
            ),
            previous = null,
            actual = null,
            recurringOccurrence = null,
        )
    }

    private fun hoursState(): HoursAndExtrasUiState {
        val segment = requireNotNull(resolveHoursReferenceSegment(COMPOSITE_HISTORY, COMPOSITE_DATE))
        return HoursAndExtrasUiState(
            loadState = HoursAndExtrasLoadState.CONTENT,
            source = HoursAndExtrasSource(
                history = COMPOSITE_HISTORY,
                catalog = WorkCatalog(
                    COMPOSITE_TIMELINE,
                    WorkSector.NURSING,
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
                objectives = emptyList(),
                extraClasses = emptyList(),
                independentExtras = listOf(COMPOSITE_EXTRA),
                segment = segment,
                progress = HoursProgress(
                    segment = segment,
                    regularWorkedMinutes = 0L,
                    extrasByClass = emptyList(),
                    totalWorkedMinutes = 0L,
                    helpsMeetReferenceMinutes = 0L,
                    doesNotHelpReferenceMinutes = 0L,
                    pendingScheduledMinutes = 0L,
                    targetMinutes = null,
                    missingMinutes = null,
                    excessMinutes = null,
                    completionPercentage = null,
                ),
                today = REFERENCE_NOW.atZone(AppDefaults.zoneId()).toLocalDate(),
            ),
        )
    }

    private fun availabilityState(
        windows: List<AvailabilityWindowRecord> = listOf(COMPOSITE_AVAILABILITY),
    ): AvailabilityUiState = AvailabilityUiState(
        loadState = AvailabilityLoadState.CONTENT,
        source = AvailabilitySource(
            history = COMPOSITE_HISTORY,
            windows = windows,
            breakdowns = emptyMap(),
            totals = null,
            protectedWindowIds = emptySet(),
            activeWork = emptyList(),
            protectedRanges = emptyList(),
            today = REFERENCE_NOW.atZone(AppDefaults.zoneId()).toLocalDate(),
        ),
    )

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        val TWO_SHIFTS_DATE: LocalDate = LocalDate.of(2026, 8, 3)
        val COMPLETED_DATE: LocalDate = LocalDate.of(2026, 8, 4)
        val DAY_OFF_DATE: LocalDate = LocalDate.of(2026, 8, 6)
        val UNDEFINED_DATE: LocalDate = LocalDate.of(2026, 8, 7)
        val MEDICAL_DATE: LocalDate = LocalDate.of(2026, 8, 8)
        val HOLIDAY_DATE: LocalDate = LocalDate.of(2026, 8, 9)
        val VACATION_DATE: LocalDate = LocalDate.of(2026, 8, 10)
        val CANCELLED_DATE: LocalDate = LocalDate.of(2026, 8, 11)
        val ABSENT_DATE: LocalDate = LocalDate.of(2026, 8, 12)
        val WEATHER_DATE: LocalDate = LocalDate.of(2026, 8, 14)
        val COMPOSITE_DATE: LocalDate = LocalDate.of(2026, 8, 5)
        val REFERENCE_NOW: Instant = ZonedDateTime.of(
            LocalDate.of(2026, 8, 13),
            LocalTime.NOON,
            AppDefaults.zoneId(),
        ).toInstant()
        val COMPOSITE_TIMELINE: UUID = UUID.fromString("96000000-0000-0000-0000-000000000001")
        val COMPOSITE_REVISION: UUID = UUID.fromString("96000000-0000-0000-0000-000000000002")
        val COMPOSITE_HISTORY = WorkConfigurationHistory(
            EffectiveDateTimeline(
                COMPOSITE_TIMELINE,
                listOf(
                    EffectiveRevision(
                        COMPOSITE_REVISION,
                        LocalDate.of(2026, 8, 1),
                        WorkConfiguration(
                            sector = WorkSector.NURSING,
                            hoursReference = HoursReference.PendingSetup,
                            availabilityLabel = AvailabilityLabel.AVAILABLE_FOR_CALL,
                        ),
                    ),
                ),
            ),
            PerPeriodHoursValues(emptyList()),
        )
        val COMPOSITE_EXTRA = IndependentExtraWorkRecord(
            id = UUID.fromString("96000000-0000-0000-0000-000000000003"),
            timelineId = COMPOSITE_TIMELINE,
            sector = WorkSector.NURSING,
            configurationRevisionId = COMPOSITE_REVISION,
            workPlaceId = UUID.fromString("96000000-0000-0000-0000-000000000004"),
            objectiveId = UUID.fromString("96000000-0000-0000-0000-000000000005"),
            workTypeId = UUID.fromString("96000000-0000-0000-0000-000000000006"),
            templateId = null,
            extraWorkClassId = UUID.fromString("96000000-0000-0000-0000-000000000007"),
            ownerLocalDate = COMPOSITE_DATE,
            zoneId = AppDefaults.zoneId(),
            start = COMPOSITE_DATE.atTime(11, 0).atZone(AppDefaults.zoneId()).toInstant(),
            end = COMPOSITE_DATE.atTime(13, 0).atZone(AppDefaults.zoneId()).toInstant(),
            snapshot = IndependentExtraWorkSnapshot(
                workPlaceName = "Hospital extra ficticio",
                workPlaceAbbreviation = "HEX",
                workPlaceAddress = null,
                workTypeName = "Refuerzo",
                workTypeBehavior = WorkTypeBehavior.ACTIVE_WORK,
                colorArgb = 0xFF336699.toInt(),
                position = null,
                className = "Refuerzo ficticio",
                helpsMeetHoursReference = false,
                showDedicatedSummary = true,
            ),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val COMPOSITE_AVAILABILITY = AvailabilityWindowRecord(
            id = UUID.fromString("96000000-0000-0000-0000-000000000008"),
            timelineId = COMPOSITE_TIMELINE,
            sector = WorkSector.NURSING,
            configurationRevisionId = COMPOSITE_REVISION,
            ownerLocalDate = COMPOSITE_DATE,
            zoneId = AppDefaults.zoneId(),
            start = COMPOSITE_DATE.atTime(14, 0).atZone(AppDefaults.zoneId()).toInstant(),
            end = COMPOSITE_DATE.atTime(18, 0).atZone(AppDefaults.zoneId()).toInstant(),
            labelSnapshot = "Disponible para llamado",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
