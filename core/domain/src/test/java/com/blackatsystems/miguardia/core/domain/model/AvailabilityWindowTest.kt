package com.blackatsystems.miguardia.core.domain.model

import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AvailabilityWindowTest {
    @Test
    fun exactLabelsAndNoAvailabilityAreTheOnlyConfigurationChoices() {
        assertEquals(
            listOf("Guardia pasiva", "Disponible para llamado", "Retén"),
            AvailabilityLabel.entries.map(AvailabilityLabel::displayName),
        )
        assertEquals(null, configuration(null).revision.value.availabilityLabel)
    }

    @Test
    fun availabilityMutationPreservesSectorReferenceAndRestartForPastCurrentAndFutureDates() {
        val reference = HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(12_240))
        val base = WorkConfiguration(
            sector = WorkSector.PRIVATE_SECURITY,
            hoursReference = reference,
            availabilityLabel = null,
            hoursReferenceStartedOn = LocalDate.of(2026, 8, 1),
        )
        val history = history(base)
        listOf(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 27),
            LocalDate.of(2027, 1, 1),
        ).forEach { date ->
            val mutation = WorkConfigurationAvailabilityMutation(
                history,
                EffectiveRevision(UUID.randomUUID(), date, base.copy(availabilityLabel = AvailabilityLabel.ON_CALL_RETAINER)),
            )
            assertEquals(reference, mutation.revision.value.hoursReference)
            assertEquals(LocalDate.of(2026, 8, 1), mutation.revision.value.hoursReferenceStartedOn)
            assertEquals(WorkSector.PRIVATE_SECURITY, mutation.revision.value.sector)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkConfigurationAvailabilityMutation(
                history,
                EffectiveRevision(
                    UUID.randomUUID(),
                    LocalDate.of(2026, 8, 27),
                    base.copy(hoursReferenceStartedOn = LocalDate.of(2026, 8, 27)),
                ),
            )
        }
    }

    @Test
    fun positiveWholeMinuteIntervalUsesLocalStartAsOwnerAndCanExceedOneDay() {
        val start = Instant.parse("2028-02-29T23:30:00Z")
        val end = Instant.parse("2028-03-02T01:30:00Z")
        val record = record(start, end, LocalDate.of(2028, 2, 29))

        assertEquals(26L * 60L, record.durationMinutes)
        assertEquals(LocalDate.of(2028, 2, 29), record.ownerLocalDate)
        assertThrows(IllegalArgumentException::class.java) {
            record(start, end, LocalDate.of(2028, 3, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            record(start.plusSeconds(1), end, LocalDate.of(2028, 2, 29))
        }
        assertThrows(IllegalArgumentException::class.java) {
            record(start, start, LocalDate.of(2028, 2, 29))
        }
    }

    @Test
    fun ownerDateUsesInjectedZoneInsteadOfUtcDate() {
        val cordoba = ZoneId.of("America/Argentina/Cordoba")
        val start = Instant.parse("2026-08-28T01:00:00Z")
        val end = Instant.parse("2026-08-28T04:00:00Z")

        assertEquals(LocalDate.of(2026, 8, 27), record(start, end, LocalDate.of(2026, 8, 27), cordoba).ownerLocalDate)
        assertThrows(IllegalArgumentException::class.java) {
            record(start, end, LocalDate.of(2026, 8, 28), cordoba)
        }
    }

    @Test
    fun midnightMonthYearAndLeapDayBoundariesRemainExact() {
        val cases = listOf(
            Triple("2026-12-31T23:00:00Z", "2027-01-01T01:00:00Z", LocalDate.of(2026, 12, 31)),
            Triple("2028-02-29T23:00:00Z", "2028-03-01T01:00:00Z", LocalDate.of(2028, 2, 29)),
            Triple("2026-08-31T23:00:00Z", "2026-09-01T00:00:00Z", LocalDate.of(2026, 8, 31)),
        )
        cases.forEach { (start, end, owner) ->
            assertEquals(owner, record(Instant.parse(start), Instant.parse(end), owner).ownerLocalDate)
        }
    }

    @Test
    fun contiguousWindowsAreAcceptedAndEveryOverlapShapeIsDetected() {
        val base = record(instant(8), instant(12), DATE)
        val expectation = AvailabilityWindowExpectation.capture(
            previous = null,
            configuration = configuration(),
            observedStart = instant(0),
            observedEnd = instant(24),
            observedWindows = listOf(base.toAvailabilityVersion()),
            observedActiveSources = emptyList(),
            protectionFingerprint = "",
        )

        assertFalse(expectation.overlaps(record(instant(12), instant(14), DATE)))
        assertFalse(expectation.overlaps(record(instant(6), instant(8), DATE)))
        listOf(
            record(instant(7), instant(9), DATE),
            record(instant(11), instant(13), DATE),
            record(instant(9), instant(11), DATE),
            record(instant(7), instant(13), DATE),
            record(instant(8), instant(12), DATE),
        ).forEach { assertTrue(expectation.overlaps(it)) }
        assertThrows(IllegalArgumentException::class.java) {
            AvailabilityWindowMutation(
                expectation,
                record(instant(9), instant(11), DATE),
            )
        }
    }

    @Test
    fun pastWindowSeparatesEffectiveAndReplacedAndUnionsOverlappingActiveWork() {
        val window = record(instant(8), instant(16), DATE)
        val result = calculateAvailabilityBreakdown(
            window,
            listOf(
                active("planned", 9, 12, AvailabilityActiveWorkKind.SHIFT_PLANNED),
                active("actual", 10, 14, AvailabilityActiveWorkKind.SHIFT_ACTUAL),
                active("extra", 13, 15, AvailabilityActiveWorkKind.INDEPENDENT_EXTRA),
            ),
            isProtected = false,
            clock = fixed(instant(20)),
        )

        assertEquals(AvailabilityTemporalState.COMPLETED, result.state)
        assertEquals(480L, result.programmedMinutes)
        assertEquals(120L, result.effectiveElapsedMinutes)
        assertEquals(360L, result.replacedElapsedMinutes)
        assertEquals(0L, result.futurePendingMinutes)
        assertEquals(120L, result.effectiveProjectedAtEndMinutes)
    }

    @Test
    fun inProgressWindowTruncatesNowToMinuteAndSeparatesFutureOccupiedWork() {
        val result = calculateAvailabilityBreakdown(
            record(instant(8), instant(16), DATE),
            listOf(
                active("before", 7, 9, AvailabilityActiveWorkKind.SHIFT_PLANNED),
                active("inside", 10, 12, AvailabilityActiveWorkKind.SHIFT_ACTUAL),
                active("future", 14, 17, AvailabilityActiveWorkKind.SHIFT_PLANNED),
            ),
            false,
            Clock.fixed(Instant.parse("2026-08-27T12:30:59Z"), ZoneOffset.UTC),
        )

        assertEquals(AvailabilityTemporalState.IN_PROGRESS, result.state)
        assertEquals(90L, result.effectiveElapsedMinutes)
        assertEquals(180L, result.replacedElapsedMinutes)
        assertEquals(90L, result.futurePendingMinutes)
        assertEquals(120L, result.futureOccupiedByPlannedWorkMinutes)
        assertEquals(180L, result.effectiveProjectedAtEndMinutes)
    }

    @Test
    fun futureWindowHasNoRealizedMinutesAndKeepsPendingSeparateFromPlannedOccupation() {
        val result = calculateAvailabilityBreakdown(
            record(instant(8), instant(16), DATE),
            listOf(active("future", 10, 12, AvailabilityActiveWorkKind.SHIFT_PLANNED)),
            false,
            fixed(instant(7)),
        )

        assertEquals(AvailabilityTemporalState.FUTURE, result.state)
        assertEquals(0L, result.effectiveElapsedMinutes)
        assertEquals(0L, result.replacedElapsedMinutes)
        assertEquals(360L, result.futurePendingMinutes)
        assertEquals(120L, result.futureOccupiedByPlannedWorkMinutes)
        assertEquals(360L, result.effectiveProjectedAtEndMinutes)
    }

    @Test
    fun exactTotalsDetectOverflowInsteadOfWrappingToNegativeMinutes() {
        val maximum = AvailabilityBreakdown(
            state = AvailabilityTemporalState.COMPLETED,
            programmedMinutes = Long.MAX_VALUE,
            effectiveElapsedMinutes = 0L,
            replacedElapsedMinutes = 0L,
            futurePendingMinutes = 0L,
            effectiveProjectedAtEndMinutes = 0L,
            futureOccupiedByPlannedWorkMinutes = 0L,
        )
        val one = maximum.copy(programmedMinutes = 1L)

        assertThrows(ArithmeticException::class.java) {
            sumAvailabilityBreakdowns(listOf(maximum, one))
        }
    }

    @Test
    fun activeWorkBeforeAfterAndCrossingBothLimitsOnlyReplacesIntersection() {
        val result = calculateAvailabilityBreakdown(
            record(instant(8), instant(16), DATE),
            listOf(
                active("before", 5, 7, AvailabilityActiveWorkKind.INDEPENDENT_EXTRA),
                active("cross-start", 6, 9, AvailabilityActiveWorkKind.SHIFT_ACTUAL),
                active("cross-end", 15, 18, AvailabilityActiveWorkKind.SHIFT_PLANNED),
                active("after", 18, 20, AvailabilityActiveWorkKind.INDEPENDENT_EXTRA),
            ),
            false,
            fixed(instant(20)),
        )
        assertEquals(120L, result.replacedElapsedMinutes)
        assertEquals(360L, result.effectiveElapsedMinutes)
    }

    @Test
    fun activeSourceResolutionUsesActualInsteadOfPlannedAndDoesNotRepeatShiftExtraFragments() {
        val shiftId = UUID.randomUUID()
        val write = shiftWrite(shiftId, ShiftStatus.PLANNED, instant(8), instant(16), DATE)
        val actual = actual(
            shiftId = shiftId,
            start = instant(9),
            end = instant(18),
            fragmentStart = instant(16),
            fragmentEnd = instant(18),
        )

        val result = resolveAvailabilityActiveWorkIntervals(
            shifts = listOf(write),
            actualsByShiftId = mapOf(shiftId to actual),
            independentExtras = emptyList(),
            protectedOwnerDates = emptyList(),
        )

        assertEquals(1, result.size)
        assertEquals(AvailabilityActiveWorkKind.SHIFT_ACTUAL, result.single().kind)
        assertEquals(instant(9), result.single().start)
        assertEquals(instant(18), result.single().end)
    }

    @Test
    fun activeSourceResolutionExcludesAbsentCancelledAndProtectedPlannedShifts() {
        val protectedPlanned = shiftWrite(UUID.randomUUID(), ShiftStatus.PLANNED, instant(8), instant(12), DATE)
        val absent = shiftWrite(UUID.randomUUID(), ShiftStatus.ABSENT, instant(12), instant(14), DATE)
        val cancelled = shiftWrite(UUID.randomUUID(), ShiftStatus.CANCELLED, instant(14), instant(16), DATE)
        val nextDay = shiftWrite(
            UUID.randomUUID(),
            ShiftStatus.PLANNED,
            instantNextDay(8),
            instantNextDay(12),
            DATE.plusDays(1),
        )

        val result = resolveAvailabilityActiveWorkIntervals(
            shifts = listOf(protectedPlanned, absent, cancelled, nextDay),
            actualsByShiftId = emptyMap(),
            independentExtras = emptyList(),
            protectedOwnerDates = listOf(DATE..DATE),
        )

        assertEquals(listOf("shift:${nextDay.shift.id}"), result.map(AvailabilityActiveWorkInterval::key))
        assertEquals(AvailabilityActiveWorkKind.SHIFT_PLANNED, result.single().kind)
    }

    @Test
    fun actualShiftAndIndependentExtraRemainActiveInsideProtection() {
        val shiftId = UUID.randomUUID()
        val actual = actual(shiftId, instant(9), instant(11))
        val extra = independentExtra(instant(12), instant(13))

        val result = resolveAvailabilityActiveWorkIntervals(
            shifts = listOf(shiftWrite(shiftId, ShiftStatus.PLANNED, instant(8), instant(12), DATE)),
            actualsByShiftId = mapOf(shiftId to actual),
            independentExtras = listOf(extra),
            protectedOwnerDates = listOf(DATE..DATE),
        )

        assertEquals(
            listOf(AvailabilityActiveWorkKind.SHIFT_ACTUAL, AvailabilityActiveWorkKind.INDEPENDENT_EXTRA),
            result.map(AvailabilityActiveWorkInterval::kind),
        )
    }

    @Test
    fun vacationOrMedicalProtectionPreservesWindowWithoutEffectivePendingOrReplacement() {
        val result = calculateAvailabilityBreakdown(
            record(instant(8), instant(16), DATE),
            listOf(active("real-inside-protection", 10, 12, AvailabilityActiveWorkKind.SHIFT_ACTUAL)),
            isProtected = true,
            clock = fixed(instant(13)),
        )
        assertEquals(AvailabilityTemporalState.PROTECTED, result.state)
        assertEquals(480L, result.programmedMinutes)
        assertEquals(0L, result.effectiveElapsedMinutes)
        assertEquals(0L, result.replacedElapsedMinutes)
        assertEquals(0L, result.futurePendingMinutes)
        assertEquals(0L, result.effectiveProjectedAtEndMinutes)
    }

    @Test
    fun correctingPreservesOwnerRevisionLabelAndCreationEvenAfterConfigurationNameChanges() {
        val original = buildAvailabilityWindowRecord(
            AvailabilityWindowDraft(ID, DATE, ZONE, instant(8), instant(12)),
            configuration(AvailabilityLabel.PASSIVE_GUARD),
            instant(20),
        )
        val corrected = buildAvailabilityWindowRecord(
            AvailabilityWindowDraft(ID, DATE, ZONE, instant(9), instant(13)),
            configuration(AvailabilityLabel.ON_CALL_RETAINER),
            instant(21),
            previous = original,
        )

        assertEquals(original.ownerLocalDate, corrected.ownerLocalDate)
        assertEquals(original.configurationRevisionId, corrected.configurationRevisionId)
        assertEquals("Guardia pasiva", corrected.labelSnapshot)
        assertEquals(original.createdAt, corrected.createdAt)
    }

    @Test
    fun correctionCannotMoveOwnerDateAndNewWindowRequiresAvailabilityEnabled() {
        val original = record(instant(8), instant(12), DATE)
        assertThrows(IllegalArgumentException::class.java) {
            buildAvailabilityWindowRecord(
                AvailabilityWindowDraft(ID, DATE.plusDays(1), ZONE, instantNextDay(8), instantNextDay(12)),
                configuration(AvailabilityLabel.PASSIVE_GUARD, DATE.plusDays(1)),
                instantNextDay(20),
                original,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildAvailabilityWindowRecord(
                AvailabilityWindowDraft(UUID.randomUUID(), DATE, ZONE, instant(8), instant(12)),
                configuration(null),
                instant(20),
            )
        }
    }

    @Test
    fun expectationCapturesWindowsActiveSourcesAndProtectionForCompleteCas() {
        val expectation = AvailabilityWindowExpectation.capture(
            previous = null,
            configuration = configuration(),
            observedStart = instant(8),
            observedEnd = instant(16),
            observedWindows = listOf(record(instant(6), instant(8), DATE).toAvailabilityVersion()),
            observedActiveSources = listOf(
                AvailabilitySourceVersion("shift:1", instant(9), instant(11), "planned:1"),
                AvailabilitySourceVersion("extra:1", instant(12), instant(13), "2"),
            ),
            protectionFingerprint = "medical:1",
        )
        assertEquals(1, expectation.observedWindows.size)
        assertEquals(2, expectation.observedActiveSources.size)
        assertEquals("medical:1", expectation.protectionFingerprint)
    }

    private fun record(
        start: Instant,
        end: Instant,
        owner: LocalDate,
        zone: ZoneId = ZONE,
    ): AvailabilityWindowRecord =
        AvailabilityWindowRecord(
            id = UUID.randomUUID(),
            timelineId = TIMELINE,
            sector = WorkSector.PRIVATE_SECURITY,
            configurationRevisionId = REVISION,
            ownerLocalDate = owner,
            zoneId = zone,
            start = start,
            end = end,
            labelSnapshot = "Guardia pasiva",
            createdAt = Instant.parse("2026-08-27T20:00:00Z"),
            updatedAt = Instant.parse("2026-08-27T20:00:00Z"),
        )

    private fun active(
        key: String,
        startHour: Int,
        endHour: Int,
        kind: AvailabilityActiveWorkKind,
    ) = AvailabilityActiveWorkInterval(key, kind, instant(startHour), instant(endHour))

    private fun shiftWrite(
        id: UUID,
        status: ShiftStatus,
        start: Instant,
        end: Instant,
        owner: LocalDate,
    ): V2ShiftWrite = V2ShiftWrite(
        shift = Shift(
            id = id,
            startAt = start,
            endAt = end,
            zoneId = ZONE,
            localStartDate = owner,
            objectiveNameSnapshot = "Objetivo",
            objectiveAbbreviationSnapshot = "OBJ",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(8, 0),
            endTimeSnapshot = LocalTime.of(16, 0),
            colorArgbSnapshot = 0,
            position = null,
            status = status,
            sourceObjectiveId = OBJECTIVE,
            createdAt = TECHNICAL_TIME,
            updatedAt = TECHNICAL_TIME,
        ),
        snapshot = ShiftWorkSnapshot(
            shiftId = id,
            timelineId = TIMELINE,
            sector = WorkSector.PRIVATE_SECURITY,
            configurationRevisionId = REVISION,
            workPlaceId = WORK_PLACE,
            objectiveId = OBJECTIVE,
            templateId = TEMPLATE,
            workTypeId = WORK_TYPE,
            workTypeNameSnapshot = "Trabajo",
            workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
        ),
    )

    private fun actual(
        shiftId: UUID,
        start: Instant,
        end: Instant,
        fragmentStart: Instant? = null,
        fragmentEnd: Instant? = null,
    ): ShiftActualAggregate {
        val record = ShiftActualRecord(
            shiftId = shiftId,
            timelineId = TIMELINE,
            sector = WorkSector.PRIVATE_SECURITY,
            actualStart = start,
            actualEnd = end,
            differenceReason = "Cambio",
            explanation = null,
            createdAt = TECHNICAL_TIME,
            updatedAt = TECHNICAL_TIME,
        )
        val fragments = if (fragmentStart != null && fragmentEnd != null) {
            listOf(
                ShiftExtraInterval(
                    id = UUID.randomUUID(),
                    shiftId = shiftId,
                    timelineId = TIMELINE,
                    sector = WorkSector.PRIVATE_SECURITY,
                    extraWorkClassId = EXTRA_CLASS,
                    start = fragmentStart,
                    end = fragmentEnd,
                    classNameSnapshot = "Extra",
                    helpsMeetHoursReferenceSnapshot = true,
                    showDedicatedSummarySnapshot = true,
                    createdAt = TECHNICAL_TIME,
                    updatedAt = TECHNICAL_TIME,
                ),
            )
        } else {
            emptyList()
        }
        return ShiftActualAggregate(record, fragments)
    }

    private fun independentExtra(start: Instant, end: Instant): IndependentExtraWorkRecord =
        IndependentExtraWorkRecord(
            id = UUID.randomUUID(),
            timelineId = TIMELINE,
            sector = WorkSector.PRIVATE_SECURITY,
            configurationRevisionId = REVISION,
            workPlaceId = WORK_PLACE,
            objectiveId = OBJECTIVE,
            workTypeId = WORK_TYPE,
            templateId = TEMPLATE,
            extraWorkClassId = EXTRA_CLASS,
            ownerLocalDate = DATE,
            zoneId = ZONE,
            start = start,
            end = end,
            snapshot = IndependentExtraWorkSnapshot(
                workPlaceName = "Lugar",
                workPlaceAbbreviation = "LUG",
                workPlaceAddress = null,
                workTypeName = "Trabajo",
                workTypeBehavior = WorkTypeBehavior.ACTIVE_WORK,
                colorArgb = 0,
                position = null,
                className = "Extra",
                helpsMeetHoursReference = true,
                showDedicatedSummary = true,
            ),
            createdAt = TECHNICAL_TIME,
            updatedAt = TECHNICAL_TIME,
        )

    private fun configuration(
        label: AvailabilityLabel? = AvailabilityLabel.PASSIVE_GUARD,
        date: LocalDate = DATE,
    ): ResolvedWorkConfigurationRevision {
        val value = WorkConfiguration(WorkSector.PRIVATE_SECURITY, HoursReference.PendingSetup, label)
        val history = WorkConfigurationHistory(
            EffectiveDateTimeline(TIMELINE, listOf(EffectiveRevision(REVISION, date, value))),
            PerPeriodHoursValues(emptyList()),
        )
        return ResolvedWorkConfigurationRevision.resolve(history, date)
    }

    private fun history(value: WorkConfiguration): WorkConfigurationHistory = WorkConfigurationHistory(
        EffectiveDateTimeline(TIMELINE, listOf(EffectiveRevision(REVISION, LocalDate.of(2026, 8, 1), value))),
        PerPeriodHoursValues(emptyList()),
    )

    private fun instant(hour: Int): Instant = if (hour == 24) {
        Instant.parse("2026-08-28T00:00:00Z")
    } else {
        Instant.parse("2026-08-27T${hour.toString().padStart(2, '0')}:00:00Z")
    }
    private fun instantNextDay(hour: Int): Instant = Instant.parse("2026-08-28T${hour.toString().padStart(2, '0')}:00:00Z")
    private fun fixed(instant: Instant): Clock = Clock.fixed(instant, ZoneOffset.UTC)

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 27)
        val ZONE: ZoneId = ZoneOffset.UTC
        val TIMELINE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val REVISION: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val WORK_PLACE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val OBJECTIVE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000005")
        val TEMPLATE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000006")
        val WORK_TYPE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000007")
        val EXTRA_CLASS: UUID = UUID.fromString("00000000-0000-0000-0000-000000000008")
        val TECHNICAL_TIME: Instant = Instant.parse("2026-08-27T20:00:00Z")
    }
}
