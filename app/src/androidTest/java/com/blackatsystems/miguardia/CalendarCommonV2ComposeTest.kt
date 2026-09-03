package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.blackatsystems.miguardia.ui.availability.AvailabilityLoadState
import com.blackatsystems.miguardia.ui.availability.AvailabilityActions
import com.blackatsystems.miguardia.ui.availability.AvailabilitySource
import com.blackatsystems.miguardia.ui.availability.AvailabilityUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasLoadState
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasActions
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasSource
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasUiState
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
        compose.onNodeWithContentDescription("2 turnos", substring = true).assertExists()
        compose.onNodeWithTag("completed-day-$COMPLETED_DATE").assertExists()
        compose.onNodeWithText("CMP", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("06:00–10:00", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Completada", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithContentDescription("completada", substring = true).assertCountEquals(2)
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
        val shift = shift("COX", "Hospital coexistente", COMPOSITE_DATE, 6, 10)
        val calendar = calendarState(
            shifts = listOf(shift),
            explicitStatuses = listOf(
                ExplicitDayStatus(COMPOSITE_DATE, ExplicitDayStatusType.DAY_OFF),
            ),
            medicalLeaves = listOf(
                MedicalLeave(
                    id = UUID(0L, 41L),
                    startDate = COMPOSITE_DATE,
                    endDateInclusive = COMPOSITE_DATE,
                    privateNote = "Nota médica privada que no debe aparecer",
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                ),
            ),
            holidays = listOf(
                Holiday(UUID(0L, 42L), COMPOSITE_DATE, "Feriado compuesto", Instant.EPOCH, Instant.EPOCH),
            ),
        )
        compose.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = calendar,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    hoursAndExtrasState = hoursState(),
                    availabilityState = availabilityState(),
                )
            }
        }

        compose.onNodeWithText("COX", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("06:00–10:00", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Extra", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Disponible para llamado", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("F · CM · Fer.", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("un trabajo extra independiente", substring = true).assertExists()
        compose.onNodeWithContentDescription(
            "una ventana de disponibilidad: Disponible para llamado",
            substring = true,
        )
            .assertExists()
        compose.onNodeWithText("Nota médica privada", substring = true).assertDoesNotExist()
    }

    @Test
    fun multipleAvailabilityUsesAQuantityAndKeepsEveryLabelInSemantics() {
        val secondWindow = COMPOSITE_AVAILABILITY.copy(
            id = UUID.fromString("96000000-0000-0000-0000-000000000009"),
            start = COMPOSITE_DATE.atTime(19, 0).atZone(AppDefaults.zoneId()).toInstant(),
            end = COMPOSITE_DATE.atTime(22, 0).atZone(AppDefaults.zoneId()).toInstant(),
            labelSnapshot = "Retén",
        )
        compose.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = calendarState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    availabilityState = availabilityState(
                        windows = listOf(COMPOSITE_AVAILABILITY, secondWindow),
                    ),
                )
            }
        }

        compose.onNodeWithText("2 disponibil.", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription(
            "2 ventanas de disponibilidad: Disponible para llamado / Retén",
            substring = true,
        ).assertExists()
    }

    @Test
    fun partialExtraAndAvailabilityErrorsKeepTheirLastMarkersAndExposeRetry() {
        var retries = 0
        val shift = shift("ERR", "Hospital con fuentes recuperables", COMPOSITE_DATE, 6, 10)
        compose.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = calendarState(shifts = listOf(shift)),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
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
        compose.onNodeWithText("Extra", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Disponible para llamado", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Reintentar").assertCountEquals(2)
        compose.onAllNodesWithText("Reintentar")[0].performClick()
        compose.onAllNodesWithText("Reintentar")[1].performClick()
        compose.runOnIdle { check(retries == 2) }
    }

    @Test
    fun transientAvailabilityMessageClearsTheVisibleSourceWhenHoursHasAPersistentError() {
        var hoursClears = 0
        var availabilityClears = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
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

        compose.onNodeWithText("Disponibilidad actualizada").assertExists()
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
    )

    private fun calendarState(
        shifts: List<Shift> = emptyList(),
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
