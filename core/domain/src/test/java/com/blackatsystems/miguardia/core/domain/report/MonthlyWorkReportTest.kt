package com.blackatsystems.miguardia.core.domain.report

import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftExtraInterval
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryInput
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursEntry
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursKey
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.WeekendDays
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRules
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyWorkReportTest {
    @Test
    fun monthStateChangesExactlyAtTheZonedBoundaryAndHandlesLeapYearAndYearChange() {
        val justBefore = Clock.fixed(Instant.parse("2026-09-01T02:59:59.999Z"), ZoneOffset.UTC)
        val atBoundary = Clock.fixed(Instant.parse("2026-09-01T03:00:00Z"), ZoneOffset.UTC)
        val buenosAires = java.time.ZoneId.of("America/Argentina/Buenos_Aires")

        assertTrue(resolveReportMonthState(MONTH, justBefore, buenosAires) is ReportMonthState.PartialAsOf)
        assertEquals(ReportMonthState.ClosedMonth, resolveReportMonthState(MONTH, atBoundary, buenosAires))
        assertEquals(
            ReportMonthState.ClosedMonth,
            resolveReportMonthState(YearMonth.of(2024, 2), atBoundary, buenosAires),
        )
        assertEquals(
            ReportMonthState.ClosedMonth,
            resolveReportMonthState(YearMonth.of(2025, 12), Clock.fixed(Instant.parse("2026-01-01T03:00:00Z"), ZoneOffset.UTC), buenosAires),
        )
        assertThrows(FutureReportMonthException::class.java) {
            resolveReportMonthState(YearMonth.of(2026, 10), atBoundary, buenosAires)
        }
    }

    @Test
    fun reportRowsReconcileExactlyWithSummaryWithoutAddingClassifications() {
        val planned = shift("worked", "2026-08-21T20:00:00Z", "2026-08-22T04:00:00Z")
        val actual = actual(
            planned,
            "2026-08-21T20:00:00Z",
            "2026-08-22T06:00:00Z",
            "2026-08-22T04:00:00Z",
            "2026-08-22T06:00:00Z",
        )
        val independent = independent("independent", "2026-08-23T10:00:00Z", "2026-08-23T12:00:00Z")
        val projection = report(
            input(
                shifts = listOf(planned),
                actuals = listOf(actual),
                extras = listOf(independent),
                holidays = listOf(Holiday(id("holiday"), LocalDate.of(2026, 8, 21), "Feriado", NOW, NOW)),
            ),
        )

        assertEquals(projection.summary.essentials.totalWorked?.value, projection.workRows.sumOf { it.accountedMinutes })
        assertEquals(720L, projection.workRows.sumOf { it.accountedMinutes })
        assertEquals(480L, projection.workRows.sumOf { it.regularMinutes })
        assertEquals(240L, projection.workRows.sumOf { row -> row.extraBreakdown.sumOf { it.minutes } })
        assertTrue(projection.workRows.sumOf { it.nightMinutes } > 0L)
        assertTrue(projection.workRows.sumOf { it.holidayMinutes } > 0L)
        assertEquals(720L, projection.workRows.sumOf { it.accountedMinutes })
        assertEquals(listOf(0, 1), projection.workRows.map { it.stableOrder })
        val independentRow = projection.workRows.single { it.kind == ReportWorkKind.INDEPENDENT_EXTRA }
        assertNull(independentRow.plannedStart)
        assertEquals(Instant.parse("2026-08-23T10:00:00Z"), independentRow.actualStart)
    }

    @Test
    fun crossMonthAndDifferentActualStartKeepCanonicalOwnerAndFullInterval() {
        val original = shift("cross", "2026-07-31T22:00:00Z", "2026-08-01T06:00:00Z")
        val write = original.copy(snapshot = original.snapshot.copy(sector = WorkSector.NURSING))
        val originalActual = actual(write, "2026-08-01T00:00:00Z", "2026-08-02T08:00:00Z")
        val actual = originalActual.copy(
            record = originalActual.record.copy(sector = WorkSector.NURSING),
        )
        val projection = report(
            input(
                shifts = listOf(write),
                actuals = listOf(actual),
                catalogs = listOf(catalog(), catalog(WorkSector.NURSING)),
            ),
        )
        val row = projection.workRows.single()

        assertEquals(LocalDate.of(2026, 8, 1), row.ownerLocalDate)
        assertEquals(Instant.parse("2026-07-31T22:00:00Z"), row.plannedStart)
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), row.actualStart)
        assertEquals(1_920L, row.accountedMinutes)
        assertEquals(1_920L, projection.summary.essentials.totalWorked?.value)
        assertTrue(WorkSector.NURSING in projection.sectors)
    }

    @Test
    fun availabilityAndEverySafeSituationStaySeparateFromWorkedMinutes() {
        val work = shift("work", "2026-08-10T09:00:00Z", "2026-08-10T12:00:00Z")
        val availability = availability("availability", "2026-08-10T08:00:00Z", "2026-08-10T14:00:00Z")
        val absent = shift("absent", "2026-08-11T08:00:00Z", "2026-08-11T16:00:00Z", ShiftStatus.ABSENT)
        val cancelled = shift("cancelled", "2026-08-12T08:00:00Z", "2026-08-12T16:00:00Z", ShiftStatus.CANCELLED)
        val projection = report(
            input(
                shifts = listOf(work, absent, cancelled),
                availability = listOf(availability),
                medical = listOf(MedicalLeave(id("medical"), LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13), "Privada", NOW, NOW)),
                vacations = listOf(Vacation(id("vacation"), LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 14), NOW, NOW)),
                statuses = listOf(
                    ExplicitDayStatus(LocalDate.of(2026, 8, 15), ExplicitDayStatusType.DAY_OFF),
                    ExplicitDayStatus(LocalDate.of(2026, 8, 16), ExplicitDayStatusType.UNDEFINED),
                ),
            ),
        )

        assertEquals(180L, projection.summary.essentials.totalWorked?.value)
        assertEquals(360L, projection.availabilityRows.single().programmedMinutes)
        assertEquals(
            setOf(
                ReportSituationKind.ABSENCE,
                ReportSituationKind.CANCELLATION,
                ReportSituationKind.MEDICAL_LEAVE,
                ReportSituationKind.VACATION,
                ReportSituationKind.DAY_OFF,
                ReportSituationKind.UNDEFINED,
            ),
            projection.situations.map { it.kind }.toSet(),
        )
    }

    @Test
    fun privacyIsAWhitelistAndForbiddenSourceFieldsNeverReachProjection() {
        val write = shift("private", "2026-08-20T08:00:00Z", "2026-08-20T12:00:00Z")
        val actual = actual(write, "2026-08-20T09:00:00Z", "2026-08-20T13:00:00Z")
        val note = ShiftNote(id("note"), write.shift.id, "Nota elegible", NOW, NOW)
        val medical = MedicalLeave(id("medical-private"), LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 23), "Nota médica elegible", NOW, NOW)
        val neighboringMedical = MedicalLeave(
            id("medical-neighbor"),
            LocalDate.of(2026, 7, 30),
            LocalDate.of(2026, 7, 31),
            "Nota médica vecina que no pertenece al mes",
            NOW,
            NOW,
        )

        val excluded = report(input(shifts = listOf(write), actuals = listOf(actual), medical = listOf(medical)))
        assertNull(excluded.privateInclusions.displayName)
        assertNull(excluded.workRows.single().position)
        assertTrue(excluded.notes.isEmpty())
        val excludedText = excluded.toString()
        assertFalse(excludedText.contains("Dirección privada"))
        assertFalse(excludedText.contains("Motivo privado"))
        assertFalse(excludedText.contains("Explicación privada"))
        assertFalse(excludedText.contains("Nota médica elegible"))

        val included = report(
            input(
                history = history(
                    HoursReference.Fixed(HoursPeriod.Weekly(DayOfWeek.MONDAY), PositiveMinutes(2_400)),
                    LocalDate.of(2026, 1, 1),
                ),
                shifts = listOf(write),
                actuals = listOf(actual),
                medical = listOf(medical, neighboringMedical),
            ),
            privacy = ReportPrivacySelection(
                includeDisplayName = true,
                includePosition = true,
                includeShiftNotes = true,
                includeMedicalNotes = true,
            ),
            displayName = "Joa de prueba",
            notes = listOf(note),
        )
        assertEquals("Joa de prueba", included.privateInclusions.displayName)
        assertEquals("Puesto privado", included.workRows.single().position)
        assertEquals(setOf(ReportNoteKind.SHIFT, ReportNoteKind.MEDICAL_LEAVE), included.notes.map { it.kind }.toSet())
        assertFalse(included.toString().contains("Nota médica vecina"))
        assertFalse(included.toString().contains("Dirección privada"))
        assertFalse(included.toString().contains("Motivo privado"))
        assertFalse(included.toString().contains("Explicación privada"))
    }

    @Test
    fun allFiveReferenceStatesRemainExplicitAndNeverBecomeFalseZeroTargets() {
        val states = listOf(
            history(HoursReference.PendingSetup) to ReportReferenceState.PENDING_SETUP,
            history(HoursReference.NotUsed) to ReportReferenceState.NOT_USED,
            history(HoursReference.Unknown(HoursPeriod.Monthly), MONTH.atDay(1)) to ReportReferenceState.UNKNOWN,
            perPeriodHistory(withValue = false) to ReportReferenceState.MISSING_VALUE_FOR_PERIOD,
            history(HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(9_600)), MONTH.atDay(1)) to ReportReferenceState.DEFINED,
        )
        states.forEach { (history, expected) ->
            val reference = report(input(history = history)).references.single()
            assertEquals(expected, reference.state)
            if (expected == ReportReferenceState.DEFINED) {
                assertEquals(9_600L, reference.targetMinutes)
                assertEquals(0L, reference.contributingMinutes)
                assertEquals(9_600L, reference.missingMinutes)
                assertEquals(0L, reference.excessMinutes)
            } else {
                assertNull(reference.targetMinutes)
                assertNull(reference.missingMinutes)
                assertNull(reference.excessMinutes)
            }
        }
    }

    @Test
    fun weeklyAndCycleCaptureRangesIncludeCompleteNeighborSegments() {
        val weekly = history(
            HoursReference.Fixed(HoursPeriod.Weekly(DayOfWeek.MONDAY), PositiveMinutes(2_400)),
            LocalDate.of(2026, 1, 1),
        )
        assertEquals(
            ReportCaptureRange(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 9, 7)),
            resolveReportCaptureRange(MONTH, weekly),
        )
        val cycle = history(
            HoursReference.Fixed(
                HoursPeriod.Cycle(LocalDate.of(2026, 7, 30), 10),
                PositiveMinutes(3_000),
            ),
            LocalDate.of(2026, 1, 1),
        )
        assertEquals(
            ReportCaptureRange(LocalDate.of(2026, 7, 30), LocalDate.of(2026, 9, 8)),
            resolveReportCaptureRange(MONTH, cycle),
        )
    }

    @Test
    fun weeklyAndCycleReferencesCountNeighborContributionsWithoutAddingThemToTheMonthlyTotal() {
        val weeklyHistory = history(
            HoursReference.Fixed(HoursPeriod.Weekly(DayOfWeek.MONDAY), PositiveMinutes(2_400)),
            LocalDate.of(2026, 1, 1),
        )
        val weeklyProjection = report(
            input(
                history = weeklyHistory,
                shifts = listOf(
                    shift("weekly-neighbor", "2026-07-30T08:00:00Z", "2026-07-30T16:00:00Z"),
                    shift("weekly-month", "2026-08-01T08:00:00Z", "2026-08-01T16:00:00Z"),
                ),
            ),
            clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val firstWeek = weeklyProjection.references.first()
        assertEquals(LocalDate.of(2026, 7, 27), firstWeek.startInclusive)
        assertEquals(960L, firstWeek.contributingMinutes)
        assertEquals(480L, weeklyProjection.summary.essentials.totalWorked?.value)
        assertEquals(480L, weeklyProjection.workRows.sumOf { it.accountedMinutes })

        val cycleHistory = history(
            HoursReference.Fixed(
                HoursPeriod.Cycle(LocalDate.of(2026, 7, 30), 10),
                PositiveMinutes(3_000),
            ),
            LocalDate.of(2026, 1, 1),
        )
        val cycleProjection = report(
            input(
                history = cycleHistory,
                shifts = listOf(
                    shift("cycle-month", "2026-08-31T08:00:00Z", "2026-08-31T16:00:00Z"),
                    shift("cycle-neighbor", "2026-09-01T08:00:00Z", "2026-09-01T16:00:00Z"),
                ),
            ),
            clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val finalCycle = cycleProjection.references.last()
        assertEquals(LocalDate.of(2026, 8, 29), finalCycle.startInclusive)
        assertEquals(LocalDate.of(2026, 9, 8), finalCycle.endExclusive)
        assertEquals(960L, finalCycle.contributingMinutes)
        assertEquals(480L, cycleProjection.summary.essentials.totalWorked?.value)
    }

    @Test
    fun emptyMonthIsGeneratableAndUsesSafeSuggestedNames() {
        val partial = report(input())
        assertFalse(partial.hasActivity)
        assertEquals("Informe parcial al 27/08/2026", partial.statusText)
        assertEquals("MiGuardia_2026-08_informe_parcial.pdf", suggestedReportFileName(partial, ReportFormat.PDF))

        val closed = report(input(month = YearMonth.of(2026, 7)), clock = CLOCK)
        assertEquals(ReportMonthState.ClosedMonth, closed.monthState)
        assertEquals("MiGuardia_2026-07_informe_mensual.xlsx", suggestedReportFileName(closed, ReportFormat.XLSX))
    }

    @Test
    fun selectedPhotosAreCappedAndProjectionContainsOnlySafeCaptions() {
        val ids = (1..13).map { id("photo-$it") }.toSet()
        assertThrows(IllegalArgumentException::class.java) {
            ReportPrivacySelection(selectedPhotoIds = ids)
        }
        val photoId = id("photo-safe")
        val photo = SchedulePhoto(
            photoId,
            MONTH,
            null,
            "Objetivo visible",
            "OV",
            "ruta-interna-que-no-debe-salir.jpg",
            "image/jpeg",
            100L,
            10,
            10,
            NOW,
            NOW,
        )
        val privacy = ReportPrivacySelection(selectedPhotoIds = setOf(photoId))
        val projection = report(input(), privacy = privacy, photos = listOf(photo))
        assertEquals("Objetivo visible", projection.photos.single().caption)
        assertFalse(projection.toString().contains("ruta-interna"))
    }

    private fun report(
        input: MonthlySummaryInput,
        privacy: ReportPrivacySelection = ReportPrivacySelection(),
        displayName: String? = null,
        notes: List<ShiftNote> = emptyList(),
        photos: List<SchedulePhoto> = emptyList(),
        clock: Clock = CLOCK,
    ): MonthlyWorkReportProjection {
        val request = MonthlyReportSnapshotRequest(
            input.month,
            privacy.includeShiftNotes,
            privacy.includeMedicalNotes,
            privacy.selectedPhotoIds,
        )
        val monthStart = input.month.atDay(1)
        val monthEnd = input.month.plusMonths(1).atDay(1)
        val scrubbedInput = input.copy(
            medicalLeaves = input.medicalLeaves.map { leave ->
                val intersectsReportMonth = leave.startDate < monthEnd &&
                    !leave.endDateInclusive.isBefore(monthStart)
                if (privacy.includeMedicalNotes && intersectsReportMonth) {
                    leave
                } else {
                    leave.copy(privateNote = null)
                }
            },
        )
        return buildMonthlyWorkReport(
            MonthlyReportSourceSnapshot(
                request,
                resolveReportCaptureRange(input.month, input.configuration),
                scrubbedInput,
                if (privacy.includeShiftNotes) notes else emptyList(),
                photos,
            ),
            MonthlyReportBuildOptions(privacy, displayName),
            clock,
            ZoneOffset.UTC,
        )
    }

    private fun input(
        month: YearMonth = MONTH,
        history: WorkConfigurationHistory = history(
            HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(9_600)),
            MONTH.atDay(1),
        ),
        shifts: List<V2ShiftWrite> = emptyList(),
        actuals: List<ShiftActualAggregate> = emptyList(),
        extras: List<IndependentExtraWorkRecord> = emptyList(),
        availability: List<AvailabilityWindowRecord> = emptyList(),
        holidays: List<Holiday> = emptyList(),
        medical: List<MedicalLeave> = emptyList(),
        vacations: List<Vacation> = emptyList(),
        statuses: List<ExplicitDayStatus> = emptyList(),
        catalogs: List<WorkCatalog> = listOf(catalog()),
    ): MonthlySummaryInput = MonthlySummaryInput(
        month,
        history,
        shifts,
        actuals,
        extras,
        availability,
        catalogs,
        holidays,
        medical,
        vacations,
        statuses,
    )

    private fun history(
        reference: HoursReference,
        startedOn: LocalDate? = null,
    ): WorkConfigurationHistory = WorkConfigurationHistory(
        EffectiveDateTimeline(
            TIMELINE_ID,
            listOf(
                EffectiveRevision(
                    CONFIG_REVISION_ID,
                    LocalDate.of(2026, 1, 1),
                    WorkConfiguration(
                        WorkSector.PRIVATE_SECURITY,
                        reference,
                        AvailabilityLabel.ON_CALL_RETAINER,
                        startedOn,
                    ),
                ),
            ),
        ),
        PerPeriodHoursValues(emptyList()),
    )

    private fun perPeriodHistory(withValue: Boolean): WorkConfigurationHistory {
        val definitionId = id("definition")
        val period = HoursPeriod.Monthly
        val reference = HoursReference.PerPeriod(definitionId, period)
        val window = period.windowContaining(MONTH.atDay(1))
        val entries = if (withValue) {
            listOf(PerPeriodHoursEntry(id("period-value"), PerPeriodHoursKey(definitionId, period, window), PositiveMinutes(9_000)))
        } else {
            emptyList()
        }
        return WorkConfigurationHistory(
            EffectiveDateTimeline(
                TIMELINE_ID,
                listOf(
                    EffectiveRevision(
                        CONFIG_REVISION_ID,
                        LocalDate.of(2026, 1, 1),
                        WorkConfiguration(WorkSector.PRIVATE_SECURITY, reference, null, MONTH.atDay(1)),
                    ),
                ),
            ),
            PerPeriodHoursValues(entries),
        )
    }

    private fun shift(
        key: String,
        start: String,
        end: String,
        status: ShiftStatus = ShiftStatus.PLANNED,
    ): V2ShiftWrite {
        val shiftId = id("shift-$key")
        val startInstant = Instant.parse(start)
        val endInstant = Instant.parse(end)
        return V2ShiftWrite(
            Shift(
                shiftId,
                startInstant,
                endInstant,
                ZoneOffset.UTC,
                startInstant.atZone(ZoneOffset.UTC).toLocalDate(),
                "Lugar histórico",
                "LH",
                "Dirección privada",
                startInstant.atZone(ZoneOffset.UTC).toLocalTime(),
                endInstant.atZone(ZoneOffset.UTC).toLocalTime(),
                0xff336699.toInt(),
                "Puesto privado",
                status,
                OBJECTIVE_ID,
                NOW,
                NOW,
            ),
            ShiftWorkSnapshot(
                shiftId,
                TIMELINE_ID,
                WorkSector.PRIVATE_SECURITY,
                CONFIG_REVISION_ID,
                PLACE_ID,
                OBJECTIVE_ID,
                TEMPLATE_ID,
                TYPE_ID,
                "Tipo histórico",
                WorkTypeBehavior.ACTIVE_WORK,
            ),
        )
    }

    private fun actual(
        write: V2ShiftWrite,
        start: String,
        end: String,
        extraStart: String? = null,
        extraEnd: String? = null,
    ): ShiftActualAggregate {
        val record = ShiftActualRecord(
            write.shift.id,
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            Instant.parse(start),
            Instant.parse(end),
            "Motivo privado",
            "Explicación privada",
            NOW,
            NOW,
        )
        val intervals = if (extraStart != null && extraEnd != null) {
            listOf(
                ShiftExtraInterval(
                    id("interval-${write.shift.id}"),
                    write.shift.id,
                    TIMELINE_ID,
                    WorkSector.PRIVATE_SECURITY,
                    id("extra-class"),
                    Instant.parse(extraStart),
                    Instant.parse(extraEnd),
                    "Extra histórica",
                    true,
                    true,
                    NOW,
                    NOW,
                ),
            )
        } else {
            emptyList()
        }
        return ShiftActualAggregate(record, intervals)
    }

    private fun independent(key: String, start: String, end: String): IndependentExtraWorkRecord {
        val startInstant = Instant.parse(start)
        return IndependentExtraWorkRecord(
            id("extra-$key"),
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            CONFIG_REVISION_ID,
            PLACE_ID,
            OBJECTIVE_ID,
            TYPE_ID,
            null,
            id("independent-class"),
            startInstant.atZone(ZoneOffset.UTC).toLocalDate(),
            ZoneOffset.UTC,
            startInstant,
            Instant.parse(end),
            IndependentExtraWorkSnapshot(
                "Lugar extra",
                "LE",
                "Dirección privada extra",
                "Tipo extra",
                WorkTypeBehavior.ACTIVE_WORK,
                0xff224466.toInt(),
                "Puesto privado extra",
                "Extra independiente",
                true,
                true,
            ),
            NOW,
            NOW,
        )
    }

    private fun availability(key: String, start: String, end: String): AvailabilityWindowRecord {
        val startInstant = Instant.parse(start)
        return AvailabilityWindowRecord(
            id(key),
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            CONFIG_REVISION_ID,
            startInstant.atZone(ZoneOffset.UTC).toLocalDate(),
            ZoneOffset.UTC,
            startInstant,
            Instant.parse(end),
            AvailabilityLabel.ON_CALL_RETAINER.displayName,
            NOW,
            NOW,
        )
    }

    private fun catalog(sector: WorkSector = WorkSector.PRIVATE_SECURITY): WorkCatalog {
        val workType = WorkType.create(TYPE_ID, TIMELINE_ID, sector, "Tipo", NOW)
        return WorkCatalog(
            TIMELINE_ID,
            sector,
            listOf(WorkPlace(PLACE_ID, TIMELINE_ID, sector, OBJECTIVE_ID, true, NOW, NOW)),
            listOf(workType),
            listOf(
                WorkTemplate(
                    TEMPLATE_ID,
                    TIMELINE_ID,
                    sector,
                    PLACE_ID,
                    OBJECTIVE_ID,
                    TYPE_ID,
                    LocalTime.of(8, 0),
                    LocalTime.of(16, 0),
                    0xff336699.toInt(),
                    true,
                    NOW,
                    NOW,
                ),
            ),
            listOf(
                WorkplaceRuleRevision(
                    id("rules"),
                    TIMELINE_ID,
                    sector,
                    PLACE_ID,
                    OBJECTIVE_ID,
                    LocalDate.of(2026, 1, 1),
                    WorkplaceRules(
                        com.blackatsystems.miguardia.core.domain.work.NightHoursRule.Defined(
                            LocalTime.of(21, 0),
                            LocalTime.of(6, 0),
                            true,
                            true,
                        ),
                        WeekendRule.Defined(WeekendDays.SATURDAY_AND_SUNDAY, true, true),
                        HolidayRule(true, true),
                    ),
                    NOW,
                ),
            ),
        )
    }

    private fun id(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray())

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        val NOW: Instant = Instant.parse("2026-08-27T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val TIMELINE_ID: UUID = UUID.nameUUIDFromBytes("timeline-report".toByteArray())
        val CONFIG_REVISION_ID: UUID = UUID.nameUUIDFromBytes("configuration-report".toByteArray())
        val OBJECTIVE_ID: UUID = UUID.nameUUIDFromBytes("objective-report".toByteArray())
        val PLACE_ID: UUID = UUID.nameUUIDFromBytes("place-report".toByteArray())
        val TYPE_ID: UUID = UUID.nameUUIDFromBytes("type-report".toByteArray())
        val TEMPLATE_ID: UUID = UUID.nameUUIDFromBytes("template-report".toByteArray())
    }
}
