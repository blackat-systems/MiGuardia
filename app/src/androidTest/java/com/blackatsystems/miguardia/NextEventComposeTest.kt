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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.nextevent.NextEventCard
import com.blackatsystems.miguardia.ui.nextevent.NextEventLoadState
import com.blackatsystems.miguardia.ui.nextevent.NextEventUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NextEventComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadingAndRecoverableErrorExposeRetryWithoutHidingLastValidResult() {
        var retries = 0
        var state by mutableStateOf(NextEventUiState())
        compose.setContent {
            MiGuardiaTheme {
                NextEventCard(state = state, onRetry = { retries += 1 })
            }
        }

        compose.onNodeWithText("Buscando guardias y francos…").assertExists()
        compose.runOnIdle {
            state = NextEventUiState(
                loadState = NextEventLoadState.ERROR,
                result = projection(shifts = listOf(futureShift())),
                errorMessage = "Error ficticio recuperable",
            )
        }
        compose.onNodeWithText("Próxima guardia").assertExists()
        compose.onNodeWithText("Error ficticio recuperable").assertExists()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun ongoingShiftShowsHistoricalDetailsRemainingTimeAndMultiplicityWithoutPrivateContent() {
        val first = shift(
            id = "10000000-0000-0000-0000-000000000001",
            start = NOW.minusSeconds(4 * 60 * 60),
            end = NOW.plusSeconds(5 * 60 * 60 + 20 * 60),
            name = "Objetivo ficticio Norte",
            abbreviation = "NRT",
            position = "Acceso uno",
        )
        val second = shift(
            id = "10000000-0000-0000-0000-000000000002",
            start = NOW.minusSeconds(30 * 60),
            end = NOW.plusSeconds(6 * 60 * 60),
            name = "Objetivo ficticio Sur",
            abbreviation = "SUR",
        )

        setCard(projection(shifts = listOf(second, first)))

        compose.onNodeWithText("Guardia en curso").assertExists()
        compose.onNodeWithText("NRT").assertExists()
        compose.onNodeWithText("Objetivo ficticio Norte").assertExists()
        compose.onNodeWithText("15/08/2026").assertExists()
        compose.onNodeWithText("08:00–17:20").assertExists()
        compose.onNodeWithText("Puesto: Acceso uno").assertExists()
        compose.onNodeWithText("Termina en 5 h 20 min").assertExists()
        compose.onNodeWithText("2 guardias comparten este estado.").assertExists()
        compose.onNodeWithText("nota médica privada", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Guardia en curso, Objetivo ficticio Norte", substring = true).assertExists()
    }

    @Test
    fun upcomingShiftShowsArgentineDateFullTimeCountAndSecondaryDayOff() {
        val first = futureShift()
        val second = first.copy(
            id = UUID.fromString("20000000-0000-0000-0000-000000000002"),
            objectiveNameSnapshot = "Objetivo ficticio dos",
        )
        val dayOff = ExplicitDayStatus(LocalDate.of(2026, 8, 17), ExplicitDayStatusType.DAY_OFF)

        setCard(projection(shifts = listOf(second, first), statuses = listOf(dayOff)))

        compose.onNodeWithText("Próxima guardia").assertExists()
        compose.onNodeWithText("16/08/2026").assertExists()
        compose.onNodeWithText("19:00–07:00").assertExists()
        compose.onNodeWithText("Comienza en 1 d 7 h").assertExists()
        compose.onNodeWithText("2 guardias comparten este estado.").assertExists()
        compose.onNodeWithText("Próximo franco: 17/08/2026").assertExists()
    }

    @Test
    fun explicitDayOffAndHonestEmptyStateAreDistinct() {
        var state by mutableStateOf(
            contentState(
                projection(
                    statuses = listOf(
                        ExplicitDayStatus(LocalDate.of(2026, 8, 16), ExplicitDayStatusType.DAY_OFF),
                        ExplicitDayStatus(LocalDate.of(2026, 8, 15), ExplicitDayStatusType.UNDEFINED),
                    ),
                ),
            ),
        )
        compose.setContent { MiGuardiaTheme { NextEventCard(state, {}) } }

        compose.onNodeWithText("Próximo franco").assertExists()
        compose.onNodeWithText("16/08/2026").assertExists()
        compose.onNodeWithText("Mañana").assertExists()

        compose.runOnIdle { state = contentState(projection()) }
        compose.onNodeWithText("Sin próximos eventos").assertExists()
        compose.onNodeWithText("No hay guardias planificadas ni francos marcados explícitamente desde hoy.").assertExists()
    }

    @Test
    fun exactTemporalTransitionsUpdateUpcomingToOngoingAndThenEmpty() {
        val shift = futureShift()
        var state by mutableStateOf(contentState(projectNextEvent(NOW, AppDefaults.zoneId(), listOf(shift), emptyList(), emptyList())))
        compose.setContent { MiGuardiaTheme { NextEventCard(state, {}) } }

        compose.onNodeWithText("Próxima guardia").assertExists()
        compose.runOnIdle {
            state = contentState(projectNextEvent(shift.startAt, AppDefaults.zoneId(), listOf(shift), emptyList(), emptyList()))
        }
        compose.onNodeWithText("Guardia en curso").assertExists()
        compose.runOnIdle {
            state = contentState(projectNextEvent(shift.endAt, AppDefaults.zoneId(), listOf(shift), emptyList(), emptyList()))
        }
        compose.onNodeWithText("Sin próximos eventos").assertExists()
    }

    @Test
    fun editingDeletingAndChangingVisibleMonthDoNotCreateASecondSourceOfTruth() {
        var nextState by mutableStateOf(contentState(projection(shifts = listOf(futureShift()))))
        var calendarState by mutableStateOf(calendarState(YearMonth.of(2026, 8)))
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendarState,
                    onPreviousMonth = { calendarState = calendarState(YearMonth.of(2026, 7)) },
                    onNextMonth = { calendarState = calendarState(YearMonth.of(2026, 9)) },
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    nextEventState = nextState,
                )
            }
        }

        compose.onNodeWithText("FIC").assertExists()
        compose.onNodeWithText("Objetivo ficticio").assertExists()
        compose.onNodeWithContentDescription("Mes anterior").performClick()
        compose.onNodeWithText("Julio de 2026").assertExists()
        compose.onNodeWithText("FIC").assertExists()
        compose.onNodeWithText("Objetivo ficticio").assertExists()

        val edited = futureShift().copy(objectiveNameSnapshot = "Objetivo editado ficticio")
        compose.runOnIdle { nextState = contentState(projection(shifts = listOf(edited))) }
        compose.onNodeWithText("FIC").assertExists()
        compose.onNodeWithText("Objetivo editado ficticio").assertExists()
        compose.runOnIdle { nextState = contentState(projection()) }
        compose.onNodeWithText("Sin próximos eventos").assertExists()
    }

    @Test
    fun engineErrorKeepsCalendarUsableAcrossThemeAndInternalZoom() {
        var dark by mutableStateOf(false)
        var zoom by mutableStateOf(AppZoom.STANDARD)
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                MiGuardiaApp(
                    calendarState = calendarState(YearMonth.of(2026, 8)),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    nextEventState = NextEventUiState(
                        loadState = NextEventLoadState.ERROR,
                        errorMessage = "Motor temporalmente no disponible",
                    ),
                    appZoom = zoom,
                )
            }
        }

        compose.onNodeWithText("Motor temporalmente no disponible").assertExists()
        compose.onNodeWithTag("month-grid").assertExists()
        compose.runOnIdle { dark = true; zoom = AppZoom.LARGE }
        compose.onNodeWithTag("next-event-card").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { zoom = AppZoom.EXTRA_LARGE }
        compose.onNodeWithText("Reintentar").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun nextEventCardRemainsReadableInLandscapeAndRestoresOrientation() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            device.setOrientationLeft()
            device.waitForIdle()
            compose.setContent {
                MiGuardiaTheme(darkTheme = false) {
                    NextEventCard(contentState(projection(shifts = listOf(futureShift()))), {})
                }
            }

            compose.onNodeWithText("Próxima guardia").assertIsDisplayed()
            compose.onNodeWithText("19:00–07:00").assertIsDisplayed()
            compose.onNodeWithText("Comienza en 1 d 7 h").assertIsDisplayed()
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    private fun setCard(result: NextEventResult) {
        compose.setContent {
            MiGuardiaTheme {
                NextEventCard(contentState(result), {})
            }
        }
    }

    private fun contentState(result: NextEventResult) = NextEventUiState(
        loadState = NextEventLoadState.CONTENT,
        result = result,
    )

    private fun projection(
        shifts: List<Shift> = emptyList(),
        statuses: List<ExplicitDayStatus> = emptyList(),
    ) = projectNextEvent(NOW, AppDefaults.zoneId(), shifts, statuses, emptyList())

    private fun futureShift(): Shift {
        val date = LocalDate.of(2026, 8, 16)
        val start = ZonedDateTime.of(date, LocalTime.of(19, 0), AppDefaults.zoneId()).toInstant()
        val end = ZonedDateTime.of(date.plusDays(1), LocalTime.of(7, 0), AppDefaults.zoneId()).toInstant()
        return shift("20000000-0000-0000-0000-000000000001", start, end)
    }

    private fun shift(
        id: String,
        start: Instant,
        end: Instant,
        name: String = "Objetivo ficticio",
        abbreviation: String = "FIC",
        position: String? = null,
    ) = Shift(
        id = UUID.fromString(id),
        startAt = start,
        endAt = end,
        zoneId = AppDefaults.zoneId(),
        localStartDate = start.atZone(AppDefaults.zoneId()).toLocalDate(),
        objectiveNameSnapshot = name,
        objectiveAbbreviationSnapshot = abbreviation,
        objectiveAddressSnapshot = null,
        startTimeSnapshot = start.atZone(AppDefaults.zoneId()).toLocalTime(),
        endTimeSnapshot = end.atZone(AppDefaults.zoneId()).toLocalTime(),
        colorArgbSnapshot = 0xFF315DA8.toInt(),
        position = position,
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = UUID(0L, 291L),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun calendarState(month: YearMonth) = CalendarUiState(
        visibleMonth = month,
        referenceInstant = NOW,
        days = (1..month.lengthOfMonth()).map { day ->
            com.blackatsystems.miguardia.core.domain.calendar.CalendarDay(
                date = month.atDay(day),
                shifts = emptyList(),
                explicitStatus = null,
                hasMedicalLeave = false,
            )
        },
        loadState = CalendarLoadState.CONTENT,
    )

    private companion object {
        val NOW: Instant = ZonedDateTime.of(
            LocalDate.of(2026, 8, 15),
            LocalTime.NOON,
            AppDefaults.zoneId(),
        ).toInstant()
    }
}
