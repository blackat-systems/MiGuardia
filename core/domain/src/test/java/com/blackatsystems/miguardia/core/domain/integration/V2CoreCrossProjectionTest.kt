package com.blackatsystems.miguardia.core.domain.integration

import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrence
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState
import com.blackatsystems.miguardia.core.domain.model.RecurringPattern
import com.blackatsystems.miguardia.core.domain.model.RecurringPlan
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevision
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftExtraInterval
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventInput
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.TodayShiftState
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.nextevent.projectTodayCard
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryType
import com.blackatsystems.miguardia.core.domain.notification.buildNotificationPlan
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryInput
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryProjection
import com.blackatsystems.miguardia.core.domain.summary.SummaryMetric
import com.blackatsystems.miguardia.core.domain.summary.SummaryValueUnit
import com.blackatsystems.miguardia.core.domain.summary.calculateMonthlySummary
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursContributionPhase
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkProtectionPeriod
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.WorkedShiftSource
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRules
import com.blackatsystems.miguardia.core.domain.work.calculateHoursContributions
import com.blackatsystems.miguardia.core.domain.work.calculateHoursProgress
import com.blackatsystems.miguardia.core.domain.work.resolveHoursReferenceSegment
import com.blackatsystems.miguardia.core.domain.work.summarizeHoursContributions
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V2CoreCrossProjectionTest {
    @Test
    fun oneDeterministicSnapshotReconcilesEveryCoreProjection() {
        val fixture = coreFixture()
        val writes = listOf(fixture.workedShift, fixture.protectedNeighbor)
        val shifts = writes.map(V2ShiftWrite::shift)
        val actualsByShiftId = mapOf(fixture.workedShift.shift.id to fixture.actual)
        val protections = listOf(
            WorkProtectionPeriod(
                fixture.protectedNeighbor.shift.localStartDate,
                fixture.protectedNeighbor.shift.localStartDate,
            ),
        )
        val workedSources = writes.map { write ->
            WorkedShiftSource(write.shift, actualsByShiftId[write.shift.id])
        }
        val segment = requireNotNull(
            resolveHoursReferenceSegment(fixture.configuration, fixture.workedShift.shift.localStartDate),
        )

        val calendar = projectCalendarMonth(
            month = fixture.month,
            shifts = shifts,
            explicitDayStatuses = emptyList(),
            medicalLeaves = listOf(fixture.medicalLeave),
            now = fixture.clock.instant(),
        )
        val hoursLedger = calculateHoursContributions(
            segment = segment,
            shifts = workedSources,
            independentExtras = listOf(fixture.independentExtra),
            clock = fixture.clock,
            zoneId = fixture.zoneId,
            protectionPeriods = protections,
        )
        val hours = calculateHoursProgress(
            segment = segment,
            shifts = workedSources,
            independentExtras = listOf(fixture.independentExtra),
            clock = fixture.clock,
            zoneId = fixture.zoneId,
            protectionPeriods = protections,
        )
        val summary = calculateMonthlySummary(
            input = MonthlySummaryInput(
                month = fixture.month,
                configuration = fixture.configuration,
                shifts = writes,
                actuals = listOf(fixture.actual),
                independentExtras = listOf(fixture.independentExtra),
                availabilityWindows = listOf(fixture.availability),
                catalogs = listOf(fixture.catalog),
                holidays = emptyList(),
                medicalLeaves = listOf(fixture.medicalLeave),
                vacations = emptyList(),
                explicitDayStatuses = emptyList(),
            ),
            clock = fixture.clock,
            zoneId = fixture.zoneId,
        )
        val nextEvent = projectNextEvent(
            now = fixture.clock.instant(),
            zoneId = fixture.zoneId,
            input = NextEventInput(
                shifts = writes,
                availabilityWindows = listOf(fixture.availability),
                actualsByShiftId = actualsByShiftId,
                independentExtras = listOf(fixture.independentExtra),
                explicitDayStatuses = emptyList(),
                vacations = emptyList(),
                medicalLeaves = listOf(fixture.medicalLeave),
            ),
        )
        val todayCard = projectTodayCard(
            now = fixture.clock.instant(),
            zoneId = fixture.zoneId,
            shifts = writes,
            actualsByShiftId = actualsByShiftId,
            vacations = emptyList(),
            medicalLeaves = listOf(fixture.medicalLeave),
            futureEvent = nextEvent,
        )
        val notificationPlan = buildNotificationPlan(
            now = fixture.clock.instant(),
            notificationsEnabled = true,
            globalReminderLeadMinutes = listOf(60L),
            projection = nextEvent,
            shiftOverrides = emptyList(),
        )

        val planRevision = fixture.recurringPlan.revisions.single()
        val occurrence = fixture.recurringPlan.occurrences.single()
        assertEquals(RecurringOccurrenceState.AUTOMATIC, occurrence.state)
        assertEquals(fixture.workedShift.shift.id, occurrence.shiftId)
        assertEquals(fixture.workedShift.shift.localStartDate, occurrence.localDate)
        assertEquals(planRevision.id, occurrence.revisionId)
        assertTrue(planRevision.id in fixture.recurringPlan.revisions.map { it.id })
        assertEquals(
            fixture.configuration.timeline.revisions.single().id,
            fixture.workedShift.snapshot.configurationRevisionId,
        )
        assertNotEquals(planRevision.id, fixture.workedShift.snapshot.configurationRevisionId)

        val workedDay = calendar.single { it.date == fixture.workedShift.shift.localStartDate }
        assertEquals(fixture.workedShift.shift.id, workedDay.shifts.single().shift.id)
        assertEquals(ShiftTemporalStatus.COMPLETED, workedDay.shifts.single().temporalStatus)
        val protectedDay = calendar.single { it.date == fixture.protectedNeighbor.shift.localStartDate }
        assertTrue(protectedDay.hasMedicalLeave)
        assertEquals(fixture.protectedNeighbor.shift.id, protectedDay.shifts.single().shift.id)
        assertEquals(1, calendar.flatMap { it.shifts }.count { it.shift.id == fixture.workedShift.shift.id })

        assertEquals(summarizeHoursContributions(segment, hoursLedger), hours)
        assertEquals(210L, hours.regularWorkedMinutes)
        assertEquals(60L, hours.extrasByClass.sumOf { it.shiftMinutes })
        assertEquals(60L, hours.extrasByClass.sumOf { it.independentMinutes })
        assertEquals(330L, hours.totalWorkedMinutes)
        assertEquals(270L, hours.helpsMeetReferenceMinutes)
        assertEquals(60L, hours.doesNotHelpReferenceMinutes)
        assertEquals(300L, hours.targetMinutes)
        assertEquals(30L, hours.missingMinutes)
        assertEquals(0L, hours.excessMinutes)

        assertEquals(210L, summary.essentials.regularWorked?.value)
        assertEquals(120L, summary.essentials.extras?.value)
        assertEquals(330L, summary.essentials.totalWorked?.value)
        val compliance = summary.compliance.single()
        assertEquals(hours, compliance.progress)
        assertEquals(270L, compliance.contributingWork.value)
        assertEquals(300L, compliance.target?.value)
        assertEquals(30L, compliance.missing?.value)
        assertEquals(
            hoursLedger.filter { it.phase == HoursContributionPhase.WORKED }
                .map { "${it.sourceId}|${it.start}|${it.end}" }
                .sorted(),
            requireNotNull(summary.essentials.totalWorked).contributions
                .map { "${it.sourceId}|${it.start}|${it.end}" }
                .sorted(),
        )

        val availability = requireNotNull(summary.availability)
        assertEquals(600L, availability.programmed.value)
        assertEquals(150L, availability.effectiveElapsed.value)
        assertEquals(330L, availability.replacedElapsed?.value)
        assertEquals(120L, availability.pending?.value)
        assertEquals(270L, availability.projectedEffectiveAtEnd.value)

        assertEquals(NextEventPrimary.ONGOING_AVAILABILITY, nextEvent.primaryEvent)
        val resumedAvailability = nextEvent.events.single() as NextEventItem.Availability
        assertTrue(resumedAvailability.isResumption)
        assertEquals(Instant.parse("2026-08-25T18:00:00Z"), resumedAvailability.start)
        assertEquals(Instant.parse("2026-08-25T20:00:00Z"), resumedAvailability.end)
        assertTrue(nextEvent.events.none { it is NextEventItem.Shift })

        assertEquals(TodayCardPrimary.FUTURE_EVENT, todayCard.primary)
        assertEquals(nextEvent, todayCard.futureEvent)
        assertEquals(fixture.workedShift.shift.id, todayCard.shifts.single().event.shiftId)
        assertEquals(TodayShiftState.COMPLETED, todayCard.shifts.single().state)
        assertEquals(1, todayCard.completedTodayCount)

        val boundary = notificationPlan.boundaries.single()
        assertEquals(resumedAvailability.identity, boundary.identity.eventIdentity)
        assertEquals(NotificationBoundaryType.END, boundary.identity.type)
        assertEquals(Instant.parse("2026-08-25T20:00:00Z"), boundary.identity.triggerAt)
        assertEquals(null, boundary.identity.leadMinutes)

        val protectedId = fixture.protectedNeighbor.shift.id
        assertTrue(hoursLedger.none { it.sourceId == protectedId })
        assertTrue(
            requireNotNull(summary.essentials.totalWorked).contributions.none {
                it.sourceId == protectedId.toString()
            },
        )
        assertTrue(nextEvent.events.none { it.identity.trackingKey == "shift:$protectedId" })
        assertTrue(notificationPlan.boundaries.none { it.event.identity.trackingKey == "shift:$protectedId" })

        val calendarShiftIds = calendar.flatMap { it.shifts }.map { it.shift.id }
        assertEquals(calendarShiftIds.size, calendarShiftIds.distinct().size)
        val ledgerIdentities = hoursLedger.map {
            "${it.contributionId}|${it.phase}|${it.kind}|${it.start}|${it.end}"
        }
        assertEquals(ledgerIdentities.size, ledgerIdentities.distinct().size)
        assertEquals(
            nextEvent.events.size,
            nextEvent.events.map { it.identity.trackingKey }.distinct().size,
        )
        assertEquals(
            notificationPlan.boundaries.size,
            notificationPlan.boundaries.map { it.identity.opaqueKey }.distinct().size,
        )
        assertSummaryReconciles(summary)
    }

    private fun assertSummaryReconciles(summary: MonthlySummaryProjection) {
        val metrics = allSummaryMetrics(summary)
        assertEquals(metrics.size, metrics.map(SummaryMetric::id).distinct().size)
        metrics.forEach { metric ->
            assertEquals(metric.value, metric.contributions.sumOf { it.value })
            assertEquals(
                metric.contributions.size,
                metric.contributions.map { it.id }.distinct().size,
            )
            if (metric.unit == SummaryValueUnit.MINUTES) {
                metric.contributions.forEach { contribution ->
                    val start = contribution.start ?: return@forEach
                    val end = requireNotNull(contribution.end)
                    val exactMinutes = ChronoUnit.MINUTES.between(start, end)
                    val absoluteValue = if (contribution.value < 0L) -contribution.value else contribution.value
                    assertEquals(exactMinutes, absoluteValue)
                }
            }
        }
    }

    private fun allSummaryMetrics(summary: MonthlySummaryProjection): List<SummaryMetric> = buildList {
        with(summary.essentials) {
            addAll(listOfNotNull(totalWorked, regularWorked, extras, pendingScheduled))
        }
        summary.compliance.forEach { period ->
            addAll(listOfNotNull(period.contributingWork, period.target, period.missing, period.excess))
        }
        summary.availability?.let { availability ->
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
        summary.optionalSections.forEach { addAll(it.metrics) }
    }

    private fun coreFixture(): CoreFixture {
        val configurationRevision = EffectiveRevision(
            id = CONFIGURATION_REVISION_ID,
            effectiveFrom = MONTH.atDay(1),
            value = WorkConfiguration(
                sector = SECTOR,
                hoursReference = HoursReference.Fixed(
                    period = com.blackatsystems.miguardia.core.domain.work.HoursPeriod.Monthly,
                    requiredMinutes = PositiveMinutes(300),
                ),
                availabilityLabel = AvailabilityLabel.ON_CALL_RETAINER,
                hoursReferenceStartedOn = MONTH.atDay(1),
            ),
        )
        val configuration = WorkConfigurationHistory(
            timeline = EffectiveDateTimeline(TIMELINE_ID, listOf(configurationRevision)),
            perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
        )
        val workedShift = shiftWrite(WORKED_SHIFT_ID, WORKED_DATE)
        val protectedNeighbor = shiftWrite(PROTECTED_SHIFT_ID, WORKED_DATE.plusDays(1))
        val actual = ShiftActualAggregate(
            record = ShiftActualRecord(
                shiftId = workedShift.shift.id,
                timelineId = TIMELINE_ID,
                sector = SECTOR,
                actualStart = localInstant(WORKED_DATE, LocalTime.of(8, 30)),
                actualEnd = localInstant(WORKED_DATE, LocalTime.of(13, 0)),
                differenceReason = "Horario real ficticio",
                explanation = null,
                createdAt = CREATED_AT,
                updatedAt = CREATED_AT,
            ),
            extraIntervals = listOf(
                ShiftExtraInterval(
                    id = SHIFT_EXTRA_ID,
                    shiftId = workedShift.shift.id,
                    timelineId = TIMELINE_ID,
                    sector = SECTOR,
                    extraWorkClassId = HELPING_EXTRA_CLASS_ID,
                    start = localInstant(WORKED_DATE, LocalTime.NOON),
                    end = localInstant(WORKED_DATE, LocalTime.of(13, 0)),
                    classNameSnapshot = "Extensión ficticia",
                    helpsMeetHoursReferenceSnapshot = true,
                    showDedicatedSummarySnapshot = true,
                    createdAt = CREATED_AT,
                    updatedAt = CREATED_AT,
                ),
            ),
        )
        val independentExtra = IndependentExtraWorkRecord(
            id = INDEPENDENT_EXTRA_ID,
            timelineId = TIMELINE_ID,
            sector = SECTOR,
            configurationRevisionId = CONFIGURATION_REVISION_ID,
            workPlaceId = WORK_PLACE_ID,
            objectiveId = OBJECTIVE_ID,
            workTypeId = WORK_TYPE_ID,
            templateId = null,
            extraWorkClassId = NON_HELPING_EXTRA_CLASS_ID,
            ownerLocalDate = WORKED_DATE,
            zoneId = ZONE,
            start = localInstant(WORKED_DATE, LocalTime.of(14, 0)),
            end = localInstant(WORKED_DATE, LocalTime.of(15, 0)),
            snapshot = IndependentExtraWorkSnapshot(
                workPlaceName = PLACE_NAME,
                workPlaceAbbreviation = PLACE_ABBREVIATION,
                workPlaceAddress = null,
                workTypeName = "Servicio extra ficticio",
                workTypeBehavior = WorkTypeBehavior.ACTIVE_WORK,
                colorArgb = COLOR,
                position = null,
                className = "Extra independiente ficticio",
                helpsMeetHoursReference = false,
                showDedicatedSummary = true,
            ),
            createdAt = CREATED_AT,
            updatedAt = CREATED_AT,
        )
        val availability = AvailabilityWindowRecord(
            id = AVAILABILITY_ID,
            timelineId = TIMELINE_ID,
            sector = SECTOR,
            configurationRevisionId = CONFIGURATION_REVISION_ID,
            ownerLocalDate = WORKED_DATE,
            zoneId = ZONE,
            start = localInstant(WORKED_DATE, LocalTime.of(7, 0)),
            end = localInstant(WORKED_DATE, LocalTime.of(17, 0)),
            labelSnapshot = AvailabilityLabel.ON_CALL_RETAINER.displayName,
            createdAt = CREATED_AT,
            updatedAt = CREATED_AT,
        )
        val plan = RecurringPlan(PLAN_ID, TIMELINE_ID, SECTOR, CREATED_AT)
        val planRevision = RecurringPlanRevision(
            id = PLAN_REVISION_ID,
            planId = plan.id,
            revisionNumber = 1,
            effectiveFrom = WORKED_DATE,
            kind = RecurringPlanRevisionKind.ACTIVE,
            endDateInclusive = WORKED_DATE,
            pattern = RecurringPattern.Weekdays.of(listOf(DayOfWeek.TUESDAY)),
            templateId = TEMPLATE_ID,
            workPlaceId = WORK_PLACE_ID,
            objectiveId = OBJECTIVE_ID,
            workTypeId = WORK_TYPE_ID,
            objectiveNameSnapshot = PLACE_NAME,
            objectiveAbbreviationSnapshot = PLACE_ABBREVIATION,
            objectiveAddressSnapshot = null,
            workTypeNameSnapshot = WORK_TYPE_NAME,
            workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
            startTimeSnapshot = LocalTime.of(8, 0),
            endTimeSnapshot = LocalTime.NOON,
            colorArgbSnapshot = COLOR,
            positionSnapshot = null,
            zoneId = ZONE,
            createdAt = CREATED_AT,
        )
        val occurrence = RecurringOccurrence(
            planId = plan.id,
            localDate = WORKED_DATE,
            revisionId = planRevision.id,
            shiftId = workedShift.shift.id,
            state = RecurringOccurrenceState.AUTOMATIC,
            createdAt = CREATED_AT,
            updatedAt = CREATED_AT,
        )
        return CoreFixture(
            month = MONTH,
            zoneId = ZONE,
            clock = CLOCK,
            configuration = configuration,
            catalog = catalog(),
            recurringPlan = RecurringPlanAggregate(plan, listOf(planRevision), listOf(occurrence)),
            workedShift = workedShift,
            protectedNeighbor = protectedNeighbor,
            actual = actual,
            independentExtra = independentExtra,
            availability = availability,
            medicalLeave = MedicalLeave(
                id = MEDICAL_LEAVE_ID,
                startDate = protectedNeighbor.shift.localStartDate,
                endDateInclusive = protectedNeighbor.shift.localStartDate,
                privateNote = "Protección ficticia",
                createdAt = CREATED_AT,
                updatedAt = CREATED_AT,
            ),
        )
    }

    private fun shiftWrite(id: UUID, date: LocalDate): V2ShiftWrite {
        val startTime = LocalTime.of(8, 0)
        val endTime = LocalTime.NOON
        return V2ShiftWrite(
            shift = Shift(
                id = id,
                startAt = localInstant(date, startTime),
                endAt = localInstant(date, endTime),
                zoneId = ZONE,
                localStartDate = date,
                objectiveNameSnapshot = PLACE_NAME,
                objectiveAbbreviationSnapshot = PLACE_ABBREVIATION,
                objectiveAddressSnapshot = null,
                startTimeSnapshot = startTime,
                endTimeSnapshot = endTime,
                colorArgbSnapshot = COLOR,
                position = null,
                status = ShiftStatus.PLANNED,
                sourceObjectiveId = OBJECTIVE_ID,
                createdAt = CREATED_AT,
                updatedAt = CREATED_AT,
            ),
            snapshot = ShiftWorkSnapshot(
                shiftId = id,
                timelineId = TIMELINE_ID,
                sector = SECTOR,
                configurationRevisionId = CONFIGURATION_REVISION_ID,
                workPlaceId = WORK_PLACE_ID,
                objectiveId = OBJECTIVE_ID,
                templateId = TEMPLATE_ID,
                workTypeId = WORK_TYPE_ID,
                workTypeNameSnapshot = WORK_TYPE_NAME,
                workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
            ),
        )
    }

    private fun catalog(): WorkCatalog {
        val workType = WorkType.create(
            id = WORK_TYPE_ID,
            timelineId = TIMELINE_ID,
            sector = SECTOR,
            rawName = WORK_TYPE_NAME,
            timestamp = CREATED_AT,
        )
        return WorkCatalog(
            timelineId = TIMELINE_ID,
            sector = SECTOR,
            workPlaces = listOf(
                WorkPlace(
                    id = WORK_PLACE_ID,
                    timelineId = TIMELINE_ID,
                    sector = SECTOR,
                    objectiveId = OBJECTIVE_ID,
                    isActive = true,
                    createdAt = CREATED_AT,
                    updatedAt = CREATED_AT,
                ),
            ),
            workTypes = listOf(workType),
            workTemplates = listOf(
                WorkTemplate(
                    id = TEMPLATE_ID,
                    timelineId = TIMELINE_ID,
                    sector = SECTOR,
                    workPlaceId = WORK_PLACE_ID,
                    objectiveId = OBJECTIVE_ID,
                    workTypeId = WORK_TYPE_ID,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.NOON,
                    colorArgb = COLOR,
                    isActive = true,
                    createdAt = CREATED_AT,
                    updatedAt = CREATED_AT,
                ),
            ),
            workplaceRuleRevisions = listOf(
                WorkplaceRuleRevision(
                    id = WORKPLACE_RULE_ID,
                    timelineId = TIMELINE_ID,
                    sector = SECTOR,
                    workPlaceId = WORK_PLACE_ID,
                    objectiveId = OBJECTIVE_ID,
                    effectiveFrom = MONTH.atDay(1),
                    rules = WorkplaceRules(
                        nightHours = NightHoursRule.Disabled,
                        weekend = WeekendRule.None,
                        holiday = HolidayRule(false, false),
                    ),
                    createdAt = CREATED_AT,
                ),
            ),
        )
    }

    private fun localInstant(date: LocalDate, time: LocalTime): Instant =
        ZonedDateTime.of(date, time, ZONE).toInstant()

    private data class CoreFixture(
        val month: YearMonth,
        val zoneId: ZoneId,
        val clock: Clock,
        val configuration: WorkConfigurationHistory,
        val catalog: WorkCatalog,
        val recurringPlan: RecurringPlanAggregate,
        val workedShift: V2ShiftWrite,
        val protectedNeighbor: V2ShiftWrite,
        val actual: ShiftActualAggregate,
        val independentExtra: IndependentExtraWorkRecord,
        val availability: AvailabilityWindowRecord,
        val medicalLeave: MedicalLeave,
    )

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        val WORKED_DATE: LocalDate = LocalDate.of(2026, 8, 25)
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val NOW: Instant = Instant.parse("2026-08-25T18:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZONE)
        val CREATED_AT: Instant = Instant.parse("2026-08-01T12:00:00Z")
        val SECTOR: WorkSector = WorkSector.NURSING
        const val COLOR: Int = 0xFF315DA8.toInt()
        const val PLACE_NAME: String = "Institución ficticia"
        const val PLACE_ABBREVIATION: String = "IFI"
        const val WORK_TYPE_NAME: String = "Turno habitual"
        val TIMELINE_ID: UUID = uuid(1)
        val CONFIGURATION_REVISION_ID: UUID = uuid(2)
        val WORK_PLACE_ID: UUID = uuid(3)
        val OBJECTIVE_ID: UUID = uuid(4)
        val WORK_TYPE_ID: UUID = uuid(5)
        val TEMPLATE_ID: UUID = uuid(6)
        val WORKPLACE_RULE_ID: UUID = uuid(7)
        val PLAN_ID: UUID = uuid(8)
        val PLAN_REVISION_ID: UUID = uuid(9)
        val WORKED_SHIFT_ID: UUID = uuid(10)
        val PROTECTED_SHIFT_ID: UUID = uuid(11)
        val HELPING_EXTRA_CLASS_ID: UUID = uuid(12)
        val NON_HELPING_EXTRA_CLASS_ID: UUID = uuid(13)
        val SHIFT_EXTRA_ID: UUID = uuid(14)
        val INDEPENDENT_EXTRA_ID: UUID = uuid(15)
        val AVAILABILITY_ID: UUID = uuid(16)
        val MEDICAL_LEAVE_ID: UUID = uuid(17)

        fun uuid(number: Int): UUID = UUID.fromString(
            "98000000-0000-0000-0000-${number.toString().padStart(12, '0')}",
        )
    }
}
