package com.blackatsystems.miguardia

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventInput
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardProjection
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.nextevent.projectTodayCard
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
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

        compose.onNodeWithText("Buscando jornadas, disponibilidad y francos…").assertExists()
        compose.runOnIdle {
            state = NextEventUiState(
                loadState = NextEventLoadState.ERROR,
                result = projection(shifts = listOf(futureShift())),
                errorMessage = "Error ficticio recuperable",
            )
        }
        compose.onNodeWithText("Próxima jornada").assertExists()
        compose.onNodeWithText("Error ficticio recuperable").assertExists()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun activeOvernightStartedYesterdayIsTheClosedPrimaryWithoutPrivateContent() {
        val earlyMorning = instant(2026, 8, 15, 2, 0)
        val overnight = shift(
            id = "10000000-0000-0000-0000-000000000001",
            start = instant(2026, 8, 14, 21, 0),
            end = instant(2026, 8, 15, 6, 0),
            name = "Objetivo ficticio Norte",
            abbreviation = "NRT",
            position = "Acceso uno",
        )

        setCard(projection(now = earlyMorning, shifts = listOf(overnight)))

        compose.onNodeWithText("Jornada en curso").assertExists()
        compose.onNodeWithText("NRT · Objetivo ficticio Norte").assertExists()
        compose.onNodeWithText("21:00–06:00", substring = true).assertExists()
        compose.onNodeWithText("Inició ayer, 14/08/2026").assertExists()
        compose.onNodeWithText("Puesto: Acceso uno").assertExists()
        compose.onNodeWithText("Termina en 4 h").assertExists()
        compose.onNodeWithTag("today-card-list").assertDoesNotExist()
        compose.onNodeWithText("nota médica privada", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Jornada en curso", substring = true).assertExists()
    }

    @Test
    fun activeAvailabilityKeepsItsHistoricalLabelAndDoesNotBecomeAShift() {
        val availability = availability(
            id = "11000000-0000-0000-0000-000000000001",
            start = instant(2026, 8, 15, 9, 0),
            end = instant(2026, 8, 15, 17, 0),
            label = "Retén",
        )

        setCard(projection(availability = listOf(availability)))

        compose.onNodeWithText("Disponibilidad activa").assertExists()
        compose.onNodeWithText("Retén").assertExists()
        compose.onNodeWithText("15/08/2026 · 09:00–17:00").assertExists()
        compose.onNodeWithText("Termina en 5 h").assertExists()
        compose.onNodeWithText("1 jornada", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Puesto:", substring = true).assertDoesNotExist()
    }

    @Test
    fun simultaneousUpcomingShiftAndAvailabilityRemainVisibleWithoutChangingShiftCount() {
        val start = instant(2026, 8, 15, 13, 0)
        val shift = shift(
            id = "12000000-0000-0000-0000-000000000001",
            start = start,
            end = instant(2026, 8, 15, 17, 0),
        )
        val availability = availability(
            id = "12000000-0000-0000-0000-000000000001",
            start = start,
            end = instant(2026, 8, 15, 18, 0),
            label = "Disponible para llamado",
            sector = WorkSector.MEDICINE,
        )

        setCard(projection(shifts = listOf(shift), availability = listOf(availability)))

        compose.onNodeWithText("Próxima jornada").assertExists()
        compose.onNodeWithText("También: Disponible para llamado · 13:00–18:00").assertExists()
        compose.onNodeWithText("2 jornadas comparten este estado.").assertDoesNotExist()
        compose.onNodeWithText("Ver jornadas de hoy").assertExists()
    }

    @Test
    fun availabilityRemainsReadableInLightDarkAndEveryInternalZoom() {
        val result = projection(
            availability = listOf(
                availability(
                    id = "13000000-0000-0000-0000-000000000001",
                    start = instant(2026, 8, 15, 9, 0),
                    end = instant(2026, 8, 15, 17, 0),
                    label = "Guardia pasiva",
                ),
            ),
        )
        var dark by mutableStateOf(false)
        var zoom by mutableStateOf(AppZoom.STANDARD)
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                NextEventCard(contentState(result), {})
            }
        }

        AppZoom.entries.forEach { option ->
            compose.runOnIdle { zoom = option }
            compose.onNodeWithText("Disponibilidad activa").assertIsDisplayed()
            compose.onNodeWithText("Guardia pasiva").assertIsDisplayed()
        }
        compose.runOnIdle { dark = true }
        compose.onNodeWithContentDescription("Disponibilidad activa", substring = true).assertIsDisplayed()
    }

    @Test
    fun sameTimeUpcomingShiftsStayDifferentiatedAndExpandInStableOrder() {
        val start = instant(2026, 8, 15, 13, 0)
        val end = instant(2026, 8, 15, 17, 0)
        val first = shift(
            id = "20000000-0000-0000-0000-000000000001",
            start = start,
            end = end,
            name = "Objetivo ficticio uno",
            abbreviation = "UNO",
        )
        val second = shift(
            id = "20000000-0000-0000-0000-000000000002",
            start = start,
            end = end,
            name = "Objetivo ficticio dos",
            abbreviation = "DOS",
        )

        setCard(projection(shifts = listOf(second, first)))

        compose.onNodeWithText("Próxima jornada").assertExists()
        compose.onNodeWithText("UNO · Objetivo ficticio uno").assertExists()
        compose.onAllNodesWithText("13:00–17:00", substring = true).assertCountEquals(2)
        compose.onNodeWithText("Comienza en 1 h").assertExists()
        compose.onNodeWithText("2 jornadas hoy.").assertExists()
        compose.onNodeWithTag("today-card-list").assertDoesNotExist()

        compose.onNodeWithText("Ver jornadas de hoy").performClick()
        compose.onNodeWithTag("today-card-list").assertExists()
        compose.onNodeWithTag("today-card-shift-${first.id}").assertExists()
        compose.onNodeWithTag("today-card-shift-${second.id}").assertExists()
        compose.onNodeWithText("DOS · Objetivo ficticio dos").assertExists()
    }

    @Test
    fun completedOnlySummaryExpandsAndCanCloseWithoutLosingItsData() {
        val completed = shift(
            id = "30000000-0000-0000-0000-000000000001",
            start = instant(2026, 8, 15, 7, 0),
            end = instant(2026, 8, 15, 11, 0),
            name = "Objetivo completado ficticio",
            abbreviation = "CMP",
        )

        setCard(projection(shifts = listOf(completed)))

        compose.onNodeWithText("Hoy: 1 jornada completada").assertExists()
        compose.onNodeWithTag("today-card-list").assertDoesNotExist()
        compose.onNodeWithText("Ver jornadas de hoy").performClick()
        compose.onNodeWithTag("today-card-shift-${completed.id}").assertExists()
        compose.onNodeWithText("Completada").assertExists()

        compose.onNodeWithText("Ocultar jornadas de hoy").performClick()
        compose.onNodeWithTag("today-card-list").assertDoesNotExist()
        compose.onNodeWithText("Ver jornadas de hoy").performClick()
        compose.onNodeWithTag("today-card-shift-${completed.id}").assertExists()
        compose.onNodeWithText("CMP · Objetivo completado ficticio").assertExists()
        compose.onNodeWithContentDescription("1 jornada completada", substring = true).assertExists()
    }

    @Test
    fun oneHistoricalTodayRecordStillOffersItsFullExpandableDetail() {
        val cancelled = todayShift(
            id = "30000000-0000-0000-0000-000000000006",
            hour = 7,
            name = "Jornada histórica ficticia",
            abbreviation = "HIS",
        ).copy(status = ShiftStatus.CANCELLED)

        setCard(projection(shifts = listOf(cancelled)))

        compose.onNodeWithText("Hoy no tenés trabajo").assertExists()
        compose.onNodeWithText("Próximo evento").assertDoesNotExist()
        compose.onNodeWithText("Sin próximos eventos").assertDoesNotExist()
        compose.onNodeWithText("Ver jornadas de hoy").performClick()
        compose.onNodeWithTag("today-card-shift-${cancelled.id}").assertExists()
        compose.onNodeWithText("HIS · Jornada histórica ficticia").assertExists()
        compose.onNodeWithText("Cancelada").assertExists()
    }

    @Test
    fun expansionResetsWhenTheCivilDateChanges() {
        val firstDay = shift(
            id = "30000000-0000-0000-0000-000000000002",
            start = instant(2026, 8, 15, 7, 0),
            end = instant(2026, 8, 15, 11, 0),
        )
        val secondDay = shift(
            id = "30000000-0000-0000-0000-000000000003",
            start = instant(2026, 8, 16, 7, 0),
            end = instant(2026, 8, 16, 11, 0),
        )
        var state by mutableStateOf(contentState(projection(shifts = listOf(firstDay))))
        compose.setContent { MiGuardiaTheme { NextEventCard(state, {}) } }

        compose.onNodeWithText("Ver jornadas de hoy").performClick()
        compose.onNodeWithTag("today-card-list").assertExists()
        compose.runOnIdle {
            state = contentState(
                projection(
                    now = instant(2026, 8, 16, 12, 0),
                    shifts = listOf(secondDay),
                ),
            )
        }

        compose.onNodeWithTag("today-card-list").assertDoesNotExist()
        compose.onNodeWithText("Ver jornadas de hoy").assertExists()
    }

    @Test
    fun restoredExpansionStaysBoundToTheCivilDateThatWasSaved() {
        val firstDay = shift(
            id = "30000000-0000-0000-0000-000000000004",
            start = instant(2026, 8, 15, 7, 0),
            end = instant(2026, 8, 15, 11, 0),
        )
        val secondDay = shift(
            id = "30000000-0000-0000-0000-000000000005",
            start = instant(2026, 8, 16, 7, 0),
            end = instant(2026, 8, 16, 11, 0),
        )
        var state = contentState(projection(shifts = listOf(firstDay)))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MiGuardiaTheme { NextEventCard(state, {}) } }

        compose.onNodeWithText("Ver jornadas de hoy").performClick()
        compose.onNodeWithTag("today-card-list").assertExists()
        compose.runOnIdle {
            state = contentState(
                projection(
                    now = instant(2026, 8, 16, 12, 0),
                    shifts = listOf(secondDay),
                ),
            )
        }
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("today-card-list").assertDoesNotExist()
        compose.onNodeWithText("Ver jornadas de hoy").assertExists()
    }

    @Test
    fun expandedListIncludesCancelledAbsentAndProtectedTodayShifts() {
        val cancelled = todayShift(
            id = "40000000-0000-0000-0000-000000000001",
            hour = 7,
            name = "Jornada cancelada ficticia",
        ).copy(status = ShiftStatus.CANCELLED)
        val absent = todayShift(
            id = "40000000-0000-0000-0000-000000000002",
            hour = 9,
            name = "Jornada ausente ficticia",
        ).copy(status = ShiftStatus.ABSENT)
        val protected = todayShift(
            id = "40000000-0000-0000-0000-000000000003",
            hour = 14,
            name = "Jornada protegida ficticia",
        )
        val medicalLeave = medicalLeave(privateNote = "nota médica privada que nunca debe mostrarse")

        setCard(
            projection(
                shifts = listOf(protected, absent, cancelled, futureShift()),
                medicalLeaves = listOf(medicalLeave),
            ),
        )

        compose.onNodeWithText("Hoy no tenés trabajo").assertExists()
        compose.onNodeWithText("3 jornadas registradas hoy.").assertExists()
        compose.onNodeWithText("Próxima jornada").assertExists()
        compose.onNodeWithContentDescription(
            "Hoy no tenés trabajo. Próxima jornada",
            substring = true,
        ).assertExists()
        compose.onNodeWithText("Ver jornadas de hoy").performClick()
        compose.onNodeWithTag("today-card-shift-${cancelled.id}").assertExists()
        compose.onNodeWithTag("today-card-shift-${absent.id}").assertExists()
        compose.onNodeWithTag("today-card-shift-${protected.id}").assertExists()
        compose.onNodeWithText("Cancelada · carpeta médica").assertExists()
        compose.onNodeWithText("Ausente · carpeta médica").assertExists()
        compose.onNodeWithText("Protegida · carpeta médica").assertExists()
        compose.onNodeWithText("nota médica privada", substring = true).assertDoesNotExist()
    }

    @Test
    fun expandedMixedListPreservesEveryTodayStateAndPlannedSnapshots() {
        val ongoing = todayShift(
            id = "50000000-0000-0000-0000-000000000001",
            hour = 10,
            durationHours = 5,
            name = "Jornada en curso ficticia",
        )
        val completed = todayShift(
            id = "50000000-0000-0000-0000-000000000002",
            hour = 6,
            durationHours = 4,
            name = "Jornada completada ficticia",
        )
        val cancelled = todayShift(
            id = "50000000-0000-0000-0000-000000000003",
            hour = 16,
            name = "Jornada cancelada ficticia",
        ).copy(status = ShiftStatus.CANCELLED)

        setCard(projection(shifts = listOf(cancelled, ongoing, completed)))

        compose.onNodeWithText("Jornada en curso").assertExists()
        compose.onNodeWithText("3 jornadas hoy.").assertExists()
        compose.onNodeWithText("10:00–15:00", substring = true).assertExists()
        compose.onNodeWithText("Ver jornadas de hoy").performClick()
        listOf(completed, ongoing, cancelled).forEach { item ->
            compose.onNodeWithTag("today-card-shift-${item.id}").assertExists()
        }
        compose.onNodeWithText("Completada").assertExists()
        compose.onNodeWithText("En curso").assertExists()
        compose.onNodeWithText("Cancelada").assertExists()
        compose.onNodeWithText("06:00–10:00", substring = true).assertExists()
        compose.onNodeWithText("16:00–20:00", substring = true).assertExists()
    }

    @Test
    fun actualTimeAndProtectionStayVisibleWithoutLeakingReasonsOrMedicalNotes() {
        val completed = todayShift(
            id = "60000000-0000-0000-0000-000000000001",
            hour = 7,
            durationHours = 4,
            name = "Jornada con horario real ficticia",
        )
        val actual = actualFor(
            shift = completed,
            start = instant(2026, 8, 15, 7, 15),
            end = instant(2026, 8, 15, 10, 45),
            reason = "motivo real privado",
            explanation = "explicación real privada",
        )
        val leave = medicalLeave(privateNote = "detalle médico privado")
        val vacation = vacation()

        setCard(
            projection(
                shifts = listOf(completed),
                actuals = mapOf(completed.id to actual),
                vacations = listOf(vacation),
                medicalLeaves = listOf(leave),
            ),
        )

        compose.onNodeWithText("Hoy: 1 jornada completada").assertExists()
        compose.onNodeWithText("Ver jornadas de hoy").performClick()
        compose.onNodeWithText(
            "Completada · horario real registrado · Vacaciones · carpeta médica",
        ).assertExists()
        compose.onNodeWithText("07:00–11:00", substring = true).assertExists()
        compose.onNodeWithText("motivo real privado", substring = true).assertDoesNotExist()
        compose.onNodeWithText("explicación real privada", substring = true).assertDoesNotExist()
        compose.onNodeWithText("detalle médico privado", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("motivo real privado", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("detalle médico privado", substring = true).assertDoesNotExist()
    }

    @Test
    fun futureShiftExplicitDayOffAndHonestEmptyStateAreDistinct() {
        var state by mutableStateOf(contentState(projection(shifts = listOf(futureShift()))))
        compose.setContent { MiGuardiaTheme { NextEventCard(state, {}) } }

        compose.onNodeWithText("Próxima jornada").assertExists()
        compose.onNodeWithText("16/08/2026", substring = true).assertExists()
        compose.onNodeWithText("19:00–07:00", substring = true).assertExists()
        compose.onNodeWithText("Comienza en 1 d 7 h").assertExists()

        compose.runOnIdle {
            state = contentState(
                projection(
                    statuses = listOf(
                        ExplicitDayStatus(LocalDate.of(2026, 8, 16), ExplicitDayStatusType.DAY_OFF),
                        ExplicitDayStatus(LocalDate.of(2026, 8, 15), ExplicitDayStatusType.UNDEFINED),
                    ),
                ),
            )
        }
        compose.onNodeWithText("Próximo franco").assertExists()
        compose.onNodeWithText("16/08/2026").assertExists()
        compose.onNodeWithText("Mañana").assertExists()

        compose.runOnIdle { state = contentState(projection()) }
        compose.onNodeWithText("Sin próximos eventos").assertExists()
        compose.onNodeWithText(
            "No hay jornadas, disponibilidad ni francos explícitos pendientes desde hoy.",
        ).assertExists()
    }

    @Test
    fun protectedFutureProducesAnHonestEmptyStateWithoutAnnouncingWork() {
        val future = futureShift()
        val protectedDate = future.localStartDate

        setCard(
            projection(
                shifts = listOf(future),
                vacations = listOf(
                    vacation().copy(
                        startDate = protectedDate,
                        endDateInclusive = protectedDate,
                    ),
                ),
            ),
        )

        compose.onNodeWithText("Sin próximos eventos").assertExists()
        compose.onNodeWithText(
            "No hay jornadas, disponibilidad ni francos explícitos pendientes desde hoy.",
        ).assertExists()
        compose.onNodeWithText("Próxima jornada").assertDoesNotExist()
        compose.onNodeWithText("FIC · Objetivo ficticio").assertDoesNotExist()
    }

    @Test
    fun exactTemporalTransitionsUpdateUpcomingToOngoingAndThenCompleted() {
        val shift = todayShift(
            id = "70000000-0000-0000-0000-000000000001",
            hour = 13,
            durationHours = 4,
        )
        var state by mutableStateOf(contentState(projection(now = NOW, shifts = listOf(shift))))
        compose.setContent { MiGuardiaTheme { NextEventCard(state, {}) } }

        compose.onNodeWithText("Próxima jornada").assertExists()
        compose.runOnIdle {
            state = contentState(projection(now = shift.startAt, shifts = listOf(shift)))
        }
        compose.onNodeWithText("Jornada en curso").assertExists()
        compose.runOnIdle {
            state = contentState(projection(now = shift.endAt, shifts = listOf(shift)))
        }
        compose.onNodeWithText("Hoy: 1 jornada completada").assertExists()
    }

    @Test
    fun changingVisibleMonthDoesNotCreateASecondTopCardSourceOfTruth() {
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

        compose.onNodeWithText("FIC · Objetivo ficticio").assertExists()
        compose.onNodeWithContentDescription("Mes anterior").performClick()
        compose.onNodeWithText("Julio de 2026").assertExists()
        compose.onNodeWithText("FIC · Objetivo ficticio").assertExists()

        val edited = futureShift().copy(objectiveNameSnapshot = "Objetivo editado ficticio")
        compose.runOnIdle { nextState = contentState(projection(shifts = listOf(edited))) }
        compose.onNodeWithText("FIC · Objetivo editado ficticio").assertExists()
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
        compose.runOnIdle {
            dark = true
            zoom = AppZoom.LARGE
        }
        compose.onNodeWithTag("next-event-card").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { zoom = AppZoom.EXTRA_LARGE }
        compose.onNodeWithText("Reintentar").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun todayCardRemainsReadableInLandscapeAndRestoresOrientation() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            device.setOrientationLeft()
            device.waitForIdle()
            compose.setContent {
                MiGuardiaTheme(darkTheme = false) {
                    NextEventCard(contentState(projection(shifts = listOf(futureShift()))), {})
                }
            }

            compose.onNodeWithText("Próxima jornada").assertIsDisplayed()
            compose.onNodeWithText("19:00–07:00", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Comienza en 1 d 7 h").assertIsDisplayed()
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    private fun setCard(result: TodayCardProjection) {
        compose.setContent {
            MiGuardiaTheme {
                NextEventCard(contentState(result), {})
            }
        }
    }

    private fun contentState(result: TodayCardProjection) = NextEventUiState(
        loadState = NextEventLoadState.CONTENT,
        result = result,
    )

    private fun projection(
        now: Instant = NOW,
        shifts: List<Shift> = emptyList(),
        availability: List<AvailabilityWindowRecord> = emptyList(),
        statuses: List<ExplicitDayStatus> = emptyList(),
        actuals: Map<UUID, ShiftActualAggregate> = emptyMap(),
        vacations: List<Vacation> = emptyList(),
        medicalLeaves: List<MedicalLeave> = emptyList(),
    ): TodayCardProjection {
        val writes = shifts.map(::v2Write)
        val futureEvent = projectNextEvent(
            now = now,
            zoneId = AppDefaults.zoneId(),
            input = NextEventInput(
                shifts = writes,
                availabilityWindows = availability,
                actualsByShiftId = actuals,
                independentExtras = emptyList(),
                explicitDayStatuses = statuses,
                vacations = vacations,
                medicalLeaves = medicalLeaves,
            ),
        )
        return projectTodayCard(
            now = now,
            zoneId = AppDefaults.zoneId(),
            shifts = writes,
            actualsByShiftId = actuals,
            vacations = vacations,
            medicalLeaves = medicalLeaves,
            futureEvent = futureEvent,
        )
    }

    private fun futureShift(): Shift = shift(
        id = "80000000-0000-0000-0000-000000000001",
        start = instant(2026, 8, 16, 19, 0),
        end = instant(2026, 8, 17, 7, 0),
    )

    private fun todayShift(
        id: String,
        hour: Int,
        durationHours: Long = 4,
        name: String = "Objetivo ficticio",
        abbreviation: String = "FIC",
    ): Shift {
        val start = instant(2026, 8, 15, hour, 0)
        return shift(
            id = id,
            start = start,
            end = start.plusSeconds(durationHours * 60 * 60),
            name = name,
            abbreviation = abbreviation,
        )
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

    private fun availability(
        id: String,
        start: Instant,
        end: Instant,
        label: String,
        sector: WorkSector = WorkSector.PRIVATE_SECURITY,
    ) = AvailabilityWindowRecord(
        id = UUID.fromString(id),
        timelineId = UUID(0L, 601L),
        sector = sector,
        configurationRevisionId = UUID(0L, 602L),
        ownerLocalDate = start.atZone(AppDefaults.zoneId()).toLocalDate(),
        zoneId = AppDefaults.zoneId(),
        start = start,
        end = end,
        labelSnapshot = label,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun actualFor(
        shift: Shift,
        start: Instant,
        end: Instant,
        reason: String,
        explanation: String,
    ) = ShiftActualAggregate(
        record = ShiftActualRecord(
            shiftId = shift.id,
            timelineId = UUID(0L, 601L),
            sector = WorkSector.PRIVATE_SECURITY,
            actualStart = start,
            actualEnd = end,
            differenceReason = reason,
            explanation = explanation,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ),
        extraIntervals = emptyList(),
    )

    private fun v2Write(shift: Shift): V2ShiftWrite = V2ShiftWrite(
        shift = shift,
        snapshot = ShiftWorkSnapshot(
            shiftId = shift.id,
            timelineId = UUID(0L, 601L),
            sector = WorkSector.PRIVATE_SECURITY,
            configurationRevisionId = UUID(0L, 602L),
            workPlaceId = UUID(0L, 603L),
            objectiveId = shift.sourceObjectiveId,
            templateId = UUID(0L, 604L),
            workTypeId = UUID(0L, 605L),
            workTypeNameSnapshot = "Jornada ficticia",
            workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
        ),
    )

    private fun vacation() = Vacation(
        id = UUID(0L, 701L),
        startDate = TODAY,
        endDateInclusive = TODAY,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun medicalLeave(privateNote: String) = MedicalLeave(
        id = UUID(0L, 702L),
        startDate = TODAY,
        endDateInclusive = TODAY,
        privateNote = privateNote,
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

    private fun instant(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Instant = ZonedDateTime.of(
        LocalDate.of(year, month, day),
        LocalTime.of(hour, minute),
        AppDefaults.zoneId(),
    ).toInstant()

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 8, 15)
        val NOW: Instant = ZonedDateTime.of(
            TODAY,
            LocalTime.NOON,
            AppDefaults.zoneId(),
        ).toInstant()
    }
}
