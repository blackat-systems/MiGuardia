package com.blackatsystems.miguardia.core.domain.summary

import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftExtraInterval
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.HoursTargetState
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
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
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlySummaryTest {
    @Test
    fun emptyMonthHasOneHonestStateWhileFutureWorkOnlyCreatesPending() {
        val empty = project(input())
        assertFalse(empty.hasContent)
        assertNull(empty.essentials.totalWorked)
        assertTrue(empty.optionalSections.isEmpty())

        val future = project(
            input(shifts = listOf(shift("future", "2026-08-28T08:00:00Z", "2026-08-28T12:00:00Z"))),
        )
        assertTrue(future.hasContent)
        assertEquals(0L, future.essentials.totalWorked?.value)
        assertEquals(240L, future.essentials.pendingScheduled?.value)
        assertNull(future.essentials.extras)
        assertMetricReconciles(future.essentials.pendingScheduled)
    }

    @Test
    fun plannedActualExtrasAndIndependentWorkShareOneExactLedger() {
        val planned = shift("planned", "2026-08-20T08:00:00Z", "2026-08-20T12:00:00Z")
        val corrected = shift("actual", "2026-08-21T08:00:00Z", "2026-08-21T12:00:00Z")
        val actual = actual(
            corrected,
            "2026-08-21T08:00:00Z",
            "2026-08-21T14:00:00Z",
            extraStart = "2026-08-21T12:00:00Z",
            extraEnd = "2026-08-21T14:00:00Z",
            helps = true,
        )
        val independent = independent(
            "independent",
            "2026-08-22T10:00:00Z",
            "2026-08-22T12:00:00Z",
            helps = false,
        )

        val result = project(input(shifts = listOf(planned, corrected), actuals = listOf(actual), extras = listOf(independent)))

        assertEquals(480L, result.essentials.regularWorked?.value)
        assertEquals(240L, result.essentials.extras?.value)
        assertEquals(720L, result.essentials.totalWorked?.value)
        assertMetricReconciles(result.essentials.totalWorked)
        assertMetricReconciles(result.essentials.regularWorked)
        assertMetricReconciles(result.essentials.extras)
        val classes = result.optionalSections.single { it.family == SummaryOptionalFamily.EXTRA_CLASSES }
        assertEquals(listOf("Extra histórica", "Extra independiente"), classes.metrics.map { it.label })
    }

    @Test
    fun actualStartOwnsTheMonthAndExplainsWhyCalendarDateDoesNotMove() {
        val plannedInAugust = shift("moved", "2026-08-31T23:00:00Z", "2026-09-01T03:00:00Z")
        val actualInSeptember = actual(
            plannedInAugust,
            "2026-09-01T00:30:00Z",
            "2026-09-01T04:30:00Z",
        )

        val august = project(input(shifts = listOf(plannedInAugust), actuals = listOf(actualInSeptember)))
        val september = calculateMonthlySummary(
            input(
                month = YearMonth.of(2026, 9),
                shifts = listOf(plannedInAugust),
                actuals = listOf(actualInSeptember),
            ),
            Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC),
            ZoneOffset.UTC,
        )

        assertFalse(august.hasContent)
        assertEquals(240L, september.essentials.totalWorked?.value)
        val row = september.essentials.totalWorked!!.contributions.single()
        assertEquals(LocalDate.of(2026, 9, 1), row.ownerLocalDate)
        assertTrue(row.explanation.orEmpty().contains("inicio real"))
        assertFalse(row.sourceLabel.contains("Dirección"))
    }

    @Test
    fun inProgressOverlapAndProtectionKeepTheirFrozenSemantics() {
        val activeA = shift("active-a", "2026-08-27T09:00:00Z", "2026-08-27T14:00:00Z")
        val activeB = shift("active-b", "2026-08-27T11:00:00Z", "2026-08-27T13:00:00Z")
        val protectedPlanned = shift("protected", "2026-08-26T08:00:00Z", "2026-08-26T12:00:00Z")
        val confirmed = shift("confirmed", "2026-08-25T08:00:00Z", "2026-08-25T12:00:00Z")
        val confirmedActual = actual(confirmed, "2026-08-25T08:00:00Z", "2026-08-25T11:00:00Z")
        val medical = MedicalLeave(
            id("medical"),
            LocalDate.of(2026, 8, 25),
            LocalDate.of(2026, 8, 26),
            "dato privado que no debe salir",
            NOW,
            NOW,
        )

        val result = project(
            input(
                shifts = listOf(activeA, activeB, protectedPlanned, confirmed),
                actuals = listOf(confirmedActual),
                medical = listOf(medical),
            ),
        )

        assertEquals(420L, result.essentials.totalWorked?.value)
        assertEquals(180L, result.essentials.pendingScheduled?.value)
        val total = requireNotNull(result.essentials.totalWorked)
        assertTrue(total.contributions.none { it.sourceId == protectedPlanned.shift.id.toString() })
        assertTrue(total.contributions.any { it.sourceId == confirmed.shift.id.toString() })
        val renderedProjection = result.toString()
        listOf(
            "dato privado que no debe salir",
            "Dirección privada que el Resumen no usa",
            "Puesto privado",
            "Corrección ficticia",
            "Explicación privada",
        ).forEach { privateValue -> assertFalse(renderedProjection.contains(privateValue)) }
    }

    @Test
    fun weeklyAndCyclesTouchingMonthRemainCompleteForEverySupportedLength() {
        val weeklyHistory = history(
            HoursReference.Fixed(HoursPeriod.Weekly(DayOfWeek.THURSDAY), PositiveMinutes(2_400)),
            startedOn = LocalDate.of(2026, 7, 30),
            effectiveFrom = LocalDate.of(2026, 7, 30),
        )
        val weekly = resolveSummaryComplianceSegments(weeklyHistory, MONTH)
        assertEquals(LocalDate.of(2026, 7, 30), weekly.first().startInclusive)
        assertEquals(LocalDate.of(2026, 9, 3), weekly.last().endExclusive)
        listOf(14, 21, 28).forEach { length ->
            val cycle = HoursPeriod.Cycle(LocalDate.of(2026, 7, 25), length)
            val segments = resolveSummaryComplianceSegments(
                history(
                    HoursReference.Fixed(cycle, PositiveMinutes(4_800)),
                    startedOn = LocalDate.of(2026, 7, 25),
                    effectiveFrom = LocalDate.of(2026, 7, 25),
                ),
                MONTH,
            )
            assertTrue(segments.first().startInclusive <= MONTH.atDay(1))
            assertTrue(segments.last().endExclusive > MONTH.atEndOfMonth())
            assertTrue(segments.all { segment ->
                java.time.temporal.ChronoUnit.DAYS.between(segment.startInclusive, segment.endExclusive) == length.toLong()
            })
        }
    }

    @Test
    fun restartKeepsFullTargetAndUnknownMissingPendingAndNotUsedNeverBecomeZero() {
        val fixed = HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(6_000))
        val restartHistory = WorkConfigurationHistory(
            EffectiveDateTimeline(
                TIMELINE_ID,
                listOf(
                    revision("old", LocalDate.of(2026, 8, 1), fixed, LocalDate.of(2026, 8, 1)),
                    revision("restart", LocalDate.of(2026, 8, 15), fixed, LocalDate.of(2026, 8, 15)),
                ),
            ),
            PerPeriodHoursValues(emptyList()),
        )
        val result = project(input(history = restartHistory))
        assertEquals(2, result.compliance.size)
        assertTrue(result.compliance.all { it.progress.targetMinutes == 6_000L })
        assertEquals(LocalDate.of(2026, 8, 15), result.compliance.last().segment.startInclusive)

        val unknown = project(input(history = history(HoursReference.Unknown(HoursPeriod.Monthly), MONTH.atDay(1))))
        assertEquals(HoursTargetState.Unknown, unknown.compliance.single().segment.target)
        assertNull(unknown.compliance.single().progress.targetMinutes)
        assertNull(unknown.compliance.single().target)
        assertNull(unknown.compliance.single().missing)
        assertNull(unknown.compliance.single().excess)
        val missing = HoursReference.PerPeriod(id("definition"), HoursPeriod.Monthly)
        val missingResult = project(input(history = history(missing, MONTH.atDay(1))))
        assertEquals(HoursTargetState.MissingPerPeriodValue, missingResult.compliance.single().segment.target)
        assertNull(missingResult.compliance.single().progress.missingMinutes)
        assertTrue(project(input(history = history(HoursReference.PendingSetup))).compliance.isEmpty())
        assertTrue(project(input(history = history(HoursReference.NotUsed))).compliance.isEmpty())
    }

    @Test
    fun historicalRulesClassifyNightHolidayAndWeekendWithoutChangingTotal() {
        val fridayNight = shift("night", "2026-08-21T21:00:00Z", "2026-08-22T07:00:00Z")
        val rules = listOf(
            rule(
                "rule-old",
                LocalDate.of(2026, 1, 1),
                night = NightHoursRule.Defined(LocalTime.of(22, 0), LocalTime.of(6, 0), true, true),
                weekend = WeekendRule.Defined(WeekendDays.SATURDAY_AND_SUNDAY, true, true),
                holidaySummary = true,
            ),
        )
        val holiday = Holiday(id("holiday"), LocalDate.of(2026, 8, 22), "Fecha ficticia", NOW, NOW)
        val result = project(
            input(shifts = listOf(fridayNight), catalogs = listOf(catalog(rules)), holidays = listOf(holiday)),
        )

        assertEquals(600L, result.essentials.totalWorked?.value)
        val byFamily = result.optionalSections.associateBy { it.family }
        assertEquals(480L, byFamily.getValue(SummaryOptionalFamily.NIGHTS).metrics.single().value)
        assertEquals(420L, byFamily.getValue(SummaryOptionalFamily.HOLIDAYS).metrics.single().value)
        assertEquals(420L, byFamily.getValue(SummaryOptionalFamily.WEEKENDS).metrics.single().value)
        assertEquals(600L, result.essentials.totalWorked?.value)
        byFamily.values.flatMap { it.metrics }.forEach(::assertMetricReconciles)

        val hiddenByRule = project(
            input(
                shifts = listOf(fridayNight),
                catalogs = listOf(
                    catalog(
                        listOf(
                            rule(
                                "disabled",
                                LocalDate.of(2026, 1, 1),
                                night = NightHoursRule.Disabled,
                                weekend = WeekendRule.Defined(WeekendDays.SATURDAY_AND_SUNDAY, true, false),
                                holidaySummary = false,
                            ),
                        ),
                    ),
                ),
                holidays = listOf(holiday),
            ),
        )
        assertTrue(hiddenByRule.optionalSections.none {
            it.family in setOf(SummaryOptionalFamily.NIGHTS, SummaryOptionalFamily.HOLIDAYS, SummaryOptionalFamily.WEEKENDS)
        })
    }

    @Test
    fun leapMonthSourceLongerThanOneDayKeepsItsOwnerAndClassifiesTheFollowingMonth() {
        val leapMonth = YearMonth.of(2028, 2)
        val longShift = shift("leap-long", "2028-02-29T23:00:00Z", "2028-03-02T01:00:00Z")
        val marchHoliday = Holiday(id("march-holiday"), LocalDate.of(2028, 3, 1), "Feriado ficticio", NOW, NOW)
        val result = calculateMonthlySummary(
            input(
                month = leapMonth,
                history = history(
                    HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(9_600)),
                    leapMonth.atDay(1),
                    LocalDate.of(2028, 1, 1),
                ),
                shifts = listOf(longShift),
                holidays = listOf(marchHoliday),
            ),
            Clock.fixed(Instant.parse("2028-03-03T12:00:00Z"), ZoneOffset.UTC),
            ZoneOffset.UTC,
        )

        assertEquals(26L * 60L, result.essentials.totalWorked?.value)
        assertEquals(LocalDate.of(2028, 2, 29), result.essentials.totalWorked?.contributions?.single()?.ownerLocalDate)
        assertEquals(
            24L * 60L,
            result.optionalSections.single { it.family == SummaryOptionalFamily.HOLIDAYS }.metrics.single().value,
        )
    }

    @Test
    fun historicalRuleChangeCanDisableOnlyTheLaterDedicatedClassification() {
        val before = shift("rule-before", "2026-08-10T22:00:00Z", "2026-08-11T06:00:00Z")
        val after = shift("rule-after", "2026-08-20T22:00:00Z", "2026-08-21T06:00:00Z")
        val revisions = listOf(
            rule("enabled", LocalDate.of(2026, 1, 1)),
            rule(
                "hidden",
                LocalDate.of(2026, 8, 15),
                night = NightHoursRule.Defined(LocalTime.of(22, 0), LocalTime.of(6, 0), true, false),
            ),
        )
        val result = project(input(shifts = listOf(before, after), catalogs = listOf(catalog(revisions))))

        assertEquals(16L * 60L, result.essentials.totalWorked?.value)
        assertEquals(
            8L * 60L,
            result.optionalSections.single { it.family == SummaryOptionalFamily.NIGHTS }.metrics.single().value,
        )
    }

    @Test
    fun nonHelpingExtraRemainsWorkedButDoesNotAdvanceCompliance() {
        val regular = shift("compliance-regular", "2026-08-20T08:00:00Z", "2026-08-20T12:00:00Z")
        val nonHelping = independent(
            "non-helping",
            "2026-08-20T13:00:00Z",
            "2026-08-20T15:00:00Z",
            helps = false,
        )
        val result = project(input(shifts = listOf(regular), extras = listOf(nonHelping)))

        assertEquals(360L, result.essentials.totalWorked?.value)
        assertEquals(240L, result.compliance.single().contributingWork.value)
        assertEquals(240L, result.compliance.single().progress.helpsMeetReferenceMinutes)
        assertEquals(120L, result.compliance.single().progress.doesNotHelpReferenceMinutes)
    }

    @Test
    fun historicalPlaceTypeClassAndPlannedActualStayPhotographed() {
        val first = shift(
            "old-photo",
            "2026-08-10T08:00:00Z",
            "2026-08-10T12:00:00Z",
            placeName = "Lugar anterior",
            typeName = "Tipo anterior",
        )
        val second = shift(
            "new-photo",
            "2026-08-11T08:00:00Z",
            "2026-08-11T12:00:00Z",
            placeName = "Lugar nuevo",
            typeName = "Tipo nuevo",
        )
        val actual = actual(second, "2026-08-11T09:00:00Z", "2026-08-11T14:00:00Z")
        val result = project(input(shifts = listOf(first, second), actuals = listOf(actual)))
        val sections = result.optionalSections.associateBy { it.family }

        assertEquals(
            listOf("Lugar anterior (LP)", "Lugar nuevo (LP)"),
            sections.getValue(SummaryOptionalFamily.WORK_PLACES).metrics.map { it.label },
        )
        assertEquals(
            listOf("Tipo anterior", "Tipo nuevo"),
            sections.getValue(SummaryOptionalFamily.WORK_TYPES).metrics.map { it.label },
        )
        val comparison = sections.getValue(SummaryOptionalFamily.PLANNED_VS_ACTUAL).metrics
        assertEquals(listOf(240L, 300L, 60L), comparison.map { it.value })
        assertEquals(second.shift.startAt, comparison[0].contributions.single().start)
        assertEquals(actual.record.actualStart, comparison[1].contributions.single().start)
        assertEquals(
            listOf(second.shift.startAt, actual.record.actualStart),
            comparison[2].contributions.map { it.start },
        )
        assertEquals(listOf(-240L, 300L), comparison[2].contributions.map { it.value })
        assertEquals(
            listOf("Horario planificado restado", "Horario real sumado"),
            comparison[2].contributions.map { it.sourceLabel },
        )
        assertMetricReconciles(comparison[2])
        assertMinuteIntervalsAreExact(comparison[2])
    }

    @Test
    fun availabilityIsSeparatedReconciledAndProtectionDoesNotInventEffectiveMinutes() {
        val work = shift("work", "2026-08-20T10:00:00Z", "2026-08-20T12:00:00Z")
        val first = availability("available", "2026-08-20T08:00:00Z", "2026-08-20T16:00:00Z")
        val protected = availability("protected-availability", "2026-08-21T08:00:00Z", "2026-08-21T12:00:00Z")
        val vacation = Vacation(id("vacation"), LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 21), NOW, NOW)
        val result = project(
            input(shifts = listOf(work), availability = listOf(first, protected), vacations = listOf(vacation)),
        )

        assertEquals(120L, result.essentials.totalWorked?.value)
        assertEquals(720L, result.availability?.programmed?.value)
        assertEquals(120L, result.availability?.replacedElapsed?.value)
        assertEquals(360L, result.availability?.effectiveElapsed?.value)
        assertEquals(360L, result.availability?.projectedEffectiveAtEnd?.value)
        listOfNotNull(
            result.availability?.programmed,
            result.availability?.effectiveElapsed,
            result.availability?.replacedElapsed,
            result.availability?.projectedEffectiveAtEnd,
        ).forEach(::assertMetricReconciles)
        allMetrics(result).forEach(::assertMinuteIntervalsAreExact)
    }

    @Test
    fun historicalLabelsThatNormalizeSimilarlyKeepUniqueStableMetricIds() {
        val spaced = shift(
            "place-spaced",
            "2026-08-10T08:00:00Z",
            "2026-08-10T10:00:00Z",
            placeName = "Puesto A",
        )
        val dashed = shift(
            "place-dashed",
            "2026-08-11T08:00:00Z",
            "2026-08-11T10:00:00Z",
            placeName = "Puesto-A",
        )
        val firstProjection = project(input(shifts = listOf(spaced, dashed)))
        val secondProjection = project(input(shifts = listOf(spaced, dashed)))
        val firstMetrics = firstProjection.optionalSections
            .single { it.family == SummaryOptionalFamily.WORK_PLACES }
            .metrics
        val secondIds = secondProjection.optionalSections
            .single { it.family == SummaryOptionalFamily.WORK_PLACES }
            .metrics
            .associate { it.label to it.id }

        assertEquals(2, firstMetrics.map { it.id }.distinct().size)
        assertEquals(firstMetrics.associate { it.label to it.id }, secondIds)
        firstMetrics.forEach { metric -> assertEquals(metric, firstProjection.metric(metric.id)) }
    }

    @Test
    fun targetMissingAndExcessAreIndependentReconciledMetrics() {
        val fourHours = shift("compliance-metrics", "2026-08-20T08:00:00Z", "2026-08-20T12:00:00Z")
        val missingProjection = project(input(shifts = listOf(fourHours)))
        val missingPeriod = missingProjection.compliance.single()

        assertEquals(9_600L, missingPeriod.target?.value)
        assertEquals(9_360L, missingPeriod.missing?.value)
        assertNull(missingPeriod.excess)
        listOfNotNull(missingPeriod.target, missingPeriod.missing).forEach { metric ->
            assertMetricReconciles(metric)
            assertEquals(metric, missingProjection.metric(metric.id))
        }

        val excessProjection = project(
            input(
                history = history(
                    HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(120)),
                    MONTH.atDay(1),
                ),
                shifts = listOf(fourHours),
            ),
        )
        val excessPeriod = excessProjection.compliance.single()
        assertEquals(120L, excessPeriod.target?.value)
        assertNull(excessPeriod.missing)
        assertEquals(120L, excessPeriod.excess?.value)
        listOfNotNull(excessPeriod.target, excessPeriod.excess).forEach { metric ->
            assertMetricReconciles(metric)
            assertEquals(metric, excessProjection.metric(metric.id))
        }
        allMetrics(excessProjection).forEach(::assertMinuteIntervalsAreExact)
    }

    @Test
    fun onlyExistingSituationsAppearAndUndefinedIsNeverReinterpreted() {
        val absent = shift("absent", "2026-08-05T08:00:00Z", "2026-08-05T12:00:00Z", ShiftStatus.ABSENT)
        val cancelled = shift("cancelled", "2026-08-06T08:00:00Z", "2026-08-06T12:00:00Z", ShiftStatus.CANCELLED)
        val medical = MedicalLeave(id("medical-days"), LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 8), null, NOW, NOW)
        val vacation = Vacation(id("vacation-days"), LocalDate.of(2026, 8, 9), LocalDate.of(2026, 8, 10), NOW, NOW)
        val statuses = listOf(
            ExplicitDayStatus(LocalDate.of(2026, 8, 11), ExplicitDayStatusType.DAY_OFF),
            ExplicitDayStatus(LocalDate.of(2026, 8, 12), ExplicitDayStatusType.UNDEFINED),
        )
        val result = project(
            input(
                shifts = listOf(absent, cancelled),
                medical = listOf(medical),
                vacations = listOf(vacation),
                statuses = statuses,
            ),
        )
        val situations = result.optionalSections.single { it.family == SummaryOptionalFamily.SITUATIONS }
        assertEquals(
            listOf("Ausencias", "Cancelaciones", "Días de carpeta médica", "Días de vacaciones", "Francos F explícitos"),
            situations.metrics.map { it.label },
        )
        assertEquals(listOf(1L, 1L, 2L, 2L, 1L), situations.metrics.map { it.value })
        assertFalse(situations.toString().contains("UNDEFINED"))
    }

    private fun project(input: MonthlySummaryInput): MonthlySummaryProjection =
        calculateMonthlySummary(input, CLOCK, ZoneOffset.UTC)

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
        catalogs: List<WorkCatalog> = listOf(catalog()),
        holidays: List<Holiday> = emptyList(),
        medical: List<MedicalLeave> = emptyList(),
        vacations: List<Vacation> = emptyList(),
        statuses: List<ExplicitDayStatus> = emptyList(),
    ) = MonthlySummaryInput(
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
        effectiveFrom: LocalDate = LocalDate.of(2026, 1, 1),
    ): WorkConfigurationHistory = WorkConfigurationHistory(
        EffectiveDateTimeline(
            TIMELINE_ID,
            listOf(revision("revision-$effectiveFrom", effectiveFrom, reference, startedOn)),
        ),
        PerPeriodHoursValues(emptyList()),
    )

    private fun revision(
        key: String,
        effectiveFrom: LocalDate,
        reference: HoursReference,
        startedOn: LocalDate?,
    ) = EffectiveRevision(
        id(key),
        effectiveFrom,
        WorkConfiguration(WorkSector.PRIVATE_SECURITY, reference, AvailabilityLabel.ON_CALL_RETAINER, startedOn),
    )

    private fun shift(
        key: String,
        start: String,
        end: String,
        status: ShiftStatus = ShiftStatus.PLANNED,
        placeName: String = "Lugar ficticio",
        typeName: String = "Guardia ficticia",
    ): V2ShiftWrite {
        val shiftId = id("shift-$key")
        val startInstant = Instant.parse(start)
        val endInstant = Instant.parse(end)
        return V2ShiftWrite(
            Shift(
                id = shiftId,
                startAt = startInstant,
                endAt = endInstant,
                zoneId = ZoneOffset.UTC,
                localStartDate = startInstant.atZone(ZoneOffset.UTC).toLocalDate(),
                objectiveNameSnapshot = placeName,
                objectiveAbbreviationSnapshot = "LP",
                objectiveAddressSnapshot = "Dirección privada que el Resumen no usa",
                startTimeSnapshot = startInstant.atZone(ZoneOffset.UTC).toLocalTime(),
                endTimeSnapshot = endInstant.atZone(ZoneOffset.UTC).toLocalTime(),
                colorArgbSnapshot = 0xff336699.toInt(),
                position = "Puesto privado",
                status = status,
                sourceObjectiveId = OBJECTIVE_ID,
                createdAt = NOW,
                updatedAt = NOW,
            ),
            ShiftWorkSnapshot(
                shiftId,
                TIMELINE_ID,
                WorkSector.PRIVATE_SECURITY,
                id("config-revision"),
                PLACE_ID,
                OBJECTIVE_ID,
                TEMPLATE_ID,
                TYPE_ID,
                typeName,
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
        helps: Boolean = true,
    ): ShiftActualAggregate {
        val record = ShiftActualRecord(
            write.shift.id,
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            Instant.parse(start),
            Instant.parse(end),
            "Corrección ficticia",
            "Explicación privada",
            NOW,
            NOW,
        )
        val interval = if (extraStart != null && extraEnd != null) {
            listOf(
                ShiftExtraInterval(
                    id("fragment-${write.shift.id}"),
                    write.shift.id,
                    TIMELINE_ID,
                    WorkSector.PRIVATE_SECURITY,
                    id("class-shift"),
                    Instant.parse(extraStart),
                    Instant.parse(extraEnd),
                    "Extra histórica",
                    helps,
                    true,
                    NOW,
                    NOW,
                ),
            )
        } else {
            emptyList()
        }
        return ShiftActualAggregate(record, interval)
    }

    private fun independent(key: String, start: String, end: String, helps: Boolean): IndependentExtraWorkRecord {
        val startInstant = Instant.parse(start)
        return IndependentExtraWorkRecord(
            id("extra-$key"),
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            id("config-extra"),
            PLACE_ID,
            OBJECTIVE_ID,
            TYPE_ID,
            null,
            id("class-$key"),
            startInstant.atZone(ZoneOffset.UTC).toLocalDate(),
            ZoneOffset.UTC,
            startInstant,
            Instant.parse(end),
            IndependentExtraWorkSnapshot(
                "Lugar extra",
                "LE",
                "Dirección privada",
                "Tipo extra",
                WorkTypeBehavior.ACTIVE_WORK,
                0xff112233.toInt(),
                "Puesto privado",
                "Extra independiente",
                helps,
                true,
            ),
            NOW,
            NOW,
        )
    }

    private fun availability(key: String, start: String, end: String): AvailabilityWindowRecord {
        val startInstant = Instant.parse(start)
        return AvailabilityWindowRecord(
            id("availability-$key"),
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            id("availability-config"),
            startInstant.atZone(ZoneOffset.UTC).toLocalDate(),
            ZoneOffset.UTC,
            startInstant,
            Instant.parse(end),
            AvailabilityLabel.ON_CALL_RETAINER.displayName,
            NOW,
            NOW,
        )
    }

    private fun catalog(revisions: List<WorkplaceRuleRevision> = listOf(rule())): WorkCatalog {
        val type = WorkType.create(TYPE_ID, TIMELINE_ID, WorkSector.PRIVATE_SECURITY, "Tipo actual", NOW)
        return WorkCatalog(
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            listOf(WorkPlace(PLACE_ID, TIMELINE_ID, WorkSector.PRIVATE_SECURITY, OBJECTIVE_ID, true, NOW, NOW)),
            listOf(type),
            listOf(
                WorkTemplate(
                    TEMPLATE_ID,
                    TIMELINE_ID,
                    WorkSector.PRIVATE_SECURITY,
                    PLACE_ID,
                    OBJECTIVE_ID,
                    TYPE_ID,
                    LocalTime.of(8, 0),
                    LocalTime.of(12, 0),
                    0xff336699.toInt(),
                    true,
                    NOW,
                    NOW,
                ),
            ),
            revisions,
        )
    }

    private fun rule(
        key: String = "rule",
        effectiveFrom: LocalDate = LocalDate.of(2026, 1, 1),
        night: NightHoursRule = NightHoursRule.Defined(LocalTime.of(21, 0), LocalTime.of(6, 0), true, true),
        weekend: WeekendRule = WeekendRule.Defined(WeekendDays.SATURDAY_AND_SUNDAY, true, true),
        holidaySummary: Boolean = true,
    ) = WorkplaceRuleRevision(
        id(key),
        TIMELINE_ID,
        WorkSector.PRIVATE_SECURITY,
        PLACE_ID,
        OBJECTIVE_ID,
        effectiveFrom,
        WorkplaceRules(night, weekend, HolidayRule(true, holidaySummary)),
        NOW,
    )

    private fun assertMetricReconciles(metric: SummaryMetric?) {
        requireNotNull(metric)
        assertEquals(metric.value, metric.contributions.sumOf { it.value })
        assertEquals(metric.contributions.sortedWith(CONTRIBUTION_ORDER), metric.contributions)
    }

    private fun assertMinuteIntervalsAreExact(metric: SummaryMetric) {
        if (metric.unit != SummaryValueUnit.MINUTES) return
        metric.contributions.forEach { contribution ->
            val start = contribution.start ?: return@forEach
            val end = requireNotNull(contribution.end)
            val duration = java.time.temporal.ChronoUnit.MINUTES.between(start, end)
            assertTrue(contribution.value == duration || contribution.value == -duration)
        }
    }

    private fun allMetrics(projection: MonthlySummaryProjection): List<SummaryMetric> = buildList {
        with(projection.essentials) {
            addAll(listOfNotNull(totalWorked, regularWorked, extras, pendingScheduled))
        }
        projection.compliance.forEach { period ->
            addAll(listOfNotNull(period.contributingWork, period.target, period.missing, period.excess))
        }
        projection.availability?.let { availability ->
            addAll(
                listOfNotNull(
                    availability.programmed,
                    availability.effectiveElapsed,
                    availability.replacedElapsed,
                    availability.pending,
                    availability.projectedEffectiveAtEnd,
                ),
            )
        }
        projection.optionalSections.forEach { addAll(it.metrics) }
    }

    private fun id(key: String): UUID = UUID.nameUUIDFromBytes(key.toByteArray())

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        val NOW: Instant = Instant.parse("2026-08-27T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val TIMELINE_ID: UUID = UUID.nameUUIDFromBytes("timeline".toByteArray())
        val OBJECTIVE_ID: UUID = UUID.nameUUIDFromBytes("objective".toByteArray())
        val PLACE_ID: UUID = UUID.nameUUIDFromBytes("place".toByteArray())
        val TYPE_ID: UUID = UUID.nameUUIDFromBytes("type".toByteArray())
        val TEMPLATE_ID: UUID = UUID.nameUUIDFromBytes("template".toByteArray())
        val CONTRIBUTION_ORDER = compareBy<SummaryContribution>(
            { it.start ?: Instant.MIN },
            { it.end ?: Instant.MIN },
            { it.kind },
            { it.id },
        )
    }
}
