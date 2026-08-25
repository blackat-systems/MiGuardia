package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.core.domain.weather.ShiftWeatherSummary
import com.blackatsystems.miguardia.core.domain.weather.WeatherCondition
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.ui.weather.ShiftWeatherBrief
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.weather.WeatherPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Rule
import org.junit.Test

class CalendarCommonV2ComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun commonCalendarMarkersAndDayDetailsRemainAvailableInV2() {
        var state by mutableStateOf(contentState())
        compose.setContent {
            MaterialTheme {
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
        compose.onNodeWithText("F", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("CM", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Fer.", substring = true, useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("V", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithContentDescription(
            "día sin definir marcado explícitamente",
            substring = true,
        ).assertExists()

        compose.runOnIdle { state = state.copy(detailDate = TWO_SHIFTS_DATE) }
        compose.onNodeWithText("Hospital Norte (HNO)").assertIsDisplayed()
        compose.onNodeWithText("Hospital Sur (HSU)").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Notas").fetchSemanticsNodes().also { nodes ->
            check(nodes.size == 2) { "Cada jornada V2 debe conservar su acceso a Notas." }
        }

        compose.runOnIdle { state = state.copy(detailDate = DAY_OFF_DATE) }
        compose.onNodeWithText("Franco marcado explícitamente.").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(detailDate = UNDEFINED_DATE) }
        compose.onNodeWithText("Día sin definir marcado explícitamente.").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(detailDate = MEDICAL_DATE) }
        compose.onNodeWithText("Carpeta médica. La nota privada permanece oculta.").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(detailDate = HOLIDAY_DATE) }
        compose.onNodeWithText("Feriado: Feriado ficticio").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(detailDate = VACATION_DATE) }
        compose.onNodeWithText("Vacaciones:", substring = true).assertIsDisplayed()
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
                        preferences = WeatherPreferences(
                            enabled = true,
                            providerExplanationAccepted = true,
                        ),
                        shiftBriefs = mapOf(
                            shift.id to ShiftWeatherBrief(summary, WeatherFreshness.FRESH),
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithText("Clima durante la guardia").assertExists()
        compose.onNodeWithText("Lluvia · 8–14 °C").assertExists()
        compose.onNodeWithText("Probabilidad máxima de lluvia: 70 %").assertExists()
        compose.onNodeWithText("Cobertura completa del horario").assertExists()
    }

    private fun contentState(): CalendarUiState = calendarState(
        shifts = listOf(
            shift("HNO", "Hospital Norte", TWO_SHIFTS_DATE, 6, 10),
            shift("HSU", "Hospital Sur", TWO_SHIFTS_DATE, 12, 16),
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
    )

    private fun calendarState(
        shifts: List<Shift>,
        explicitStatuses: List<ExplicitDayStatus> = emptyList(),
        medicalLeaves: List<MedicalLeave> = emptyList(),
        holidays: List<Holiday> = emptyList(),
        vacations: List<Vacation> = emptyList(),
        detailDate: LocalDate? = null,
    ) = CalendarUiState(
        visibleMonth = MONTH,
        referenceInstant = REFERENCE_NOW,
        days = projectCalendarMonth(
            month = MONTH,
            shifts = shifts,
            explicitDayStatuses = explicitStatuses,
            medicalLeaves = medicalLeaves,
            now = REFERENCE_NOW,
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

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        val TWO_SHIFTS_DATE: LocalDate = LocalDate.of(2026, 8, 3)
        val DAY_OFF_DATE: LocalDate = LocalDate.of(2026, 8, 6)
        val UNDEFINED_DATE: LocalDate = LocalDate.of(2026, 8, 7)
        val MEDICAL_DATE: LocalDate = LocalDate.of(2026, 8, 8)
        val HOLIDAY_DATE: LocalDate = LocalDate.of(2026, 8, 9)
        val VACATION_DATE: LocalDate = LocalDate.of(2026, 8, 10)
        val WEATHER_DATE: LocalDate = LocalDate.of(2026, 8, 14)
        val REFERENCE_NOW: Instant = ZonedDateTime.of(
            LocalDate.of(2026, 8, 13),
            LocalTime.NOON,
            AppDefaults.zoneId(),
        ).toInstant()
    }
}
