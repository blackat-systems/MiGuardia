package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftExtraInterval
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HoursProgressTest {
    @Test
    fun restartInsideMonthUsesTheFullTargetWithoutProration() {
        val reference = HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(6_000))
        val history = history(
            revision(
                LocalDate.of(2026, 8, 15),
                reference,
                startedOn = LocalDate.of(2026, 8, 15),
            ),
        )

        val segment = requireNotNull(resolveHoursReferenceSegment(history, LocalDate.of(2026, 8, 20)))

        assertEquals(LocalDate.of(2026, 8, 15), segment.startInclusive)
        assertEquals(LocalDate.of(2026, 9, 1), segment.endExclusive)
        assertEquals(HoursTargetState.Defined(PositiveMinutes(6_000)), segment.target)
        assertTrue(segment.isShortNaturalSegment)
        assertEquals(HoursSegmentBoundaryReason.REFERENCE_RESTART, segment.startsBecause)
    }

    @Test
    fun unrelatedRevisionCopiesMarkerAndDoesNotRestartWhileConsciousSameValueDoes() {
        val reference = HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(6_000))
        val monthStart = LocalDate.of(2026, 8, 1)
        val continuous = history(
            revision(monthStart, reference, monthStart, REVISION_1_ID),
            revision(LocalDate.of(2026, 8, 10), reference, monthStart, REVISION_2_ID),
        )
        val reset = history(
            revision(monthStart, reference, monthStart, REVISION_1_ID),
            revision(
                LocalDate.of(2026, 8, 10),
                reference,
                LocalDate.of(2026, 8, 10),
                REVISION_2_ID,
            ),
        )

        assertEquals(
            LocalDate.of(2026, 8, 1),
            resolveHoursReferenceSegment(continuous, LocalDate.of(2026, 8, 20))?.startInclusive,
        )
        assertEquals(
            LocalDate.of(2026, 8, 10),
            resolveHoursReferenceSegment(reset, LocalDate.of(2026, 8, 20))?.startInclusive,
        )
    }

    @Test
    fun futureRestartLeavesThePreviousSegmentIntactUntilItsChosenDate() {
        val previous = HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(6_000))
        val future = HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(7_000))
        val firstDate = LocalDate.of(2026, 8, 1)
        val restartDate = LocalDate.of(2026, 9, 1)
        val history = history(
            revision(firstDate, previous, firstDate, REVISION_1_ID),
            revision(restartDate, future, restartDate, REVISION_2_ID),
        )

        val before = requireNotNull(
            resolveHoursReferenceSegment(history, restartDate.minusDays(1)),
        )
        val after = requireNotNull(resolveHoursReferenceSegment(history, restartDate))

        assertEquals(firstDate, before.startInclusive)
        assertEquals(restartDate, before.endExclusive)
        assertEquals(HoursTargetState.Defined(PositiveMinutes(6_000)), before.target)
        assertEquals(restartDate, after.startInclusive)
        assertEquals(HoursTargetState.Defined(PositiveMinutes(7_000)), after.target)
        assertEquals(HoursSegmentBoundaryReason.REFERENCE_RESTART, after.startsBecause)
    }

    @Test
    fun weeklyAndCyclePeriodsExposeTheirExactNextNaturalBoundary() {
        val weekly = HoursReference.Unknown(HoursPeriod.Weekly(DayOfWeek.THURSDAY))
        val cycle14 = HoursReference.Unknown(HoursPeriod.Cycle(LocalDate.of(2026, 8, 1), 14))
        val cycle21 = HoursReference.Unknown(HoursPeriod.Cycle(LocalDate.of(2026, 8, 1), 21))
        val cycle28 = HoursReference.Unknown(HoursPeriod.Cycle(LocalDate.of(2026, 8, 1), 28))

        assertEquals(LocalDate.of(2026, 8, 27), nextNaturalPeriodStart(weekly, LocalDate.of(2026, 8, 25)))
        assertEquals(LocalDate.of(2026, 8, 29), nextNaturalPeriodStart(cycle14, LocalDate.of(2026, 8, 25)))
        assertEquals(LocalDate.of(2026, 9, 12), nextNaturalPeriodStart(cycle21, LocalDate.of(2026, 8, 25)))
        assertEquals(LocalDate.of(2026, 8, 29), nextNaturalPeriodStart(cycle28, LocalDate.of(2026, 8, 25)))
    }

    @Test
    fun perPeriodTargetDistinguishesMissingFromDefined() {
        val reference = HoursReference.PerPeriod(DEFINITION_ID, HoursPeriod.Monthly)
        val revision = revision(DATE, reference, DATE)
        val key = reference.keyContaining(DATE)
        val entry = PerPeriodHoursEntry(ENTRY_ID, key, PositiveMinutes(7_200))

        assertEquals(
            HoursTargetState.MissingPerPeriodValue,
            resolveHoursReferenceSegment(history(revision), DATE)?.target,
        )
        assertEquals(
            HoursTargetState.Defined(PositiveMinutes(7_200)),
            resolveHoursReferenceSegment(history(listOf(revision), listOf(entry)), DATE)?.target,
        )
    }

    @Test
    fun plannedActualAndIndependentExtrasAreCountedOnceUsingHistoricalClassMeaning() {
        val segment = requireNotNull(resolveHoursReferenceSegment(fixedHistory(480), DATE))
        val plannedOnly = shiftSource(
            id = SHIFT_1_ID,
            start = instant("2026-08-25T08:00:00Z"),
            end = instant("2026-08-25T12:00:00Z"),
        )
        val actualWrite = shiftWrite(
            SHIFT_2_ID,
            instant("2026-08-25T13:00:00Z"),
            instant("2026-08-25T17:00:00Z"),
        )
        val actual = actual(
            actualWrite,
            instant("2026-08-25T13:00:00Z"),
            instant("2026-08-25T18:00:00Z"),
            listOf(
                interval(
                    actualWrite,
                    instant("2026-08-25T17:00:00Z"),
                    instant("2026-08-25T18:00:00Z"),
                    true,
                ),
            ),
        )
        val independent = independent(
            instant("2026-08-25T19:00:00Z"),
            instant("2026-08-25T21:00:00Z"),
            helps = false,
        )

        val progress = calculateHoursProgress(
            segment,
            listOf(plannedOnly, WorkedShiftSource(actualWrite.shift, actual)),
            listOf(independent),
            Clock.fixed(instant("2026-08-26T12:00:00Z"), ZoneOffset.UTC),
            ZoneOffset.UTC,
        )

        assertEquals(8L * 60L, progress.regularWorkedMinutes)
        assertEquals(3L * 60L, progress.extrasByClass.sumOf { it.totalMinutes })
        assertEquals(11L * 60L, progress.totalWorkedMinutes)
        assertEquals(9L * 60L, progress.helpsMeetReferenceMinutes)
        assertEquals(2L * 60L, progress.doesNotHelpReferenceMinutes)
        assertEquals(60L, progress.excessMinutes)
        assertEquals(0L, progress.missingMinutes)
        assertEquals(112.5, progress.completionPercentage!!, 0.0001)
    }

    @Test
    fun inProgressAndFutureSourcesSplitWorkedFromPendingAtTheClockMinute() {
        val segment = requireNotNull(resolveHoursReferenceSegment(fixedHistory(1_000), DATE))
        val active = shiftSource(
            SHIFT_1_ID,
            instant("2026-08-25T11:00:00Z"),
            instant("2026-08-25T13:00:00Z"),
        )
        val future = shiftSource(
            SHIFT_2_ID,
            instant("2026-08-25T14:00:00Z"),
            instant("2026-08-25T16:00:00Z"),
        )

        val progress = calculateHoursProgress(
            segment,
            listOf(active, future),
            emptyList(),
            CLOCK,
            ZoneOffset.UTC,
        )

        assertEquals(60L, progress.regularWorkedMinutes)
        assertEquals(3L * 60L, progress.pendingScheduledMinutes)

        val ledger = calculateHoursContributions(
            segment,
            listOf(active, future),
            emptyList(),
            CLOCK,
            ZoneOffset.UTC,
        )
        assertEquals(
            listOf(
                Triple(instant("2026-08-25T11:00:00Z"), instant("2026-08-25T12:00:00Z"), HoursContributionPhase.WORKED),
                Triple(instant("2026-08-25T12:00:00Z"), instant("2026-08-25T13:00:00Z"), HoursContributionPhase.PENDING),
                Triple(instant("2026-08-25T14:00:00Z"), instant("2026-08-25T16:00:00Z"), HoursContributionPhase.PENDING),
            ),
            ledger.map { Triple(it.start, it.end, it.phase) },
        )
        assertTrue(ledger.all { it.minutes == java.time.temporal.ChronoUnit.MINUTES.between(it.start, it.end) })
    }

    @Test
    fun explicitExtraInTheMiddleLeavesOnlyExactHabitualComplements() {
        val segment = requireNotNull(resolveHoursReferenceSegment(fixedHistory(2_000), DATE))
        val write = shiftWrite(
            SHIFT_1_ID,
            instant("2026-08-25T08:00:00Z"),
            instant("2026-08-25T14:00:00Z"),
        )
        val source = WorkedShiftSource(
            write.shift,
            actual(
                write,
                instant("2026-08-25T08:00:00Z"),
                instant("2026-08-25T14:00:00Z"),
                listOf(
                    interval(
                        write,
                        instant("2026-08-25T10:00:00Z"),
                        instant("2026-08-25T11:00:00Z"),
                        helps = true,
                    ),
                ),
            ),
        )

        val ledger = calculateHoursContributions(
            segment,
            listOf(source),
            emptyList(),
            Clock.fixed(instant("2026-08-25T20:00:00Z"), ZoneOffset.UTC),
            ZoneOffset.UTC,
        )
        val regular = ledger.filter { it.kind == HoursContributionKind.REGULAR_SHIFT }
        val extra = ledger.single { it.kind == HoursContributionKind.SHIFT_EXTRA }

        assertEquals(
            listOf(
                instant("2026-08-25T08:00:00Z") to instant("2026-08-25T10:00:00Z"),
                instant("2026-08-25T11:00:00Z") to instant("2026-08-25T14:00:00Z"),
            ),
            regular.map { it.start to it.end },
        )
        assertEquals(5L * 60L, regular.sumOf { it.workedMinutes })
        assertEquals(60L, extra.workedMinutes)
        assertTrue(ledger.all { it.minutes == java.time.temporal.ChronoUnit.MINUTES.between(it.start, it.end) })
    }

    @Test
    fun cancelledAndAbsentShiftsNeverAddWorkedOrPendingMinutes() {
        val segment = requireNotNull(resolveHoursReferenceSegment(fixedHistory(1_000), DATE))
        val cancelled = shiftSource(
            SHIFT_1_ID,
            instant("2026-08-25T08:00:00Z"),
            instant("2026-08-25T12:00:00Z"),
            ShiftStatus.CANCELLED,
        )
        val absent = shiftSource(
            SHIFT_2_ID,
            instant("2026-08-25T14:00:00Z"),
            instant("2026-08-25T18:00:00Z"),
            ShiftStatus.ABSENT,
        )

        val progress = calculateHoursProgress(
            segment,
            listOf(cancelled, absent),
            emptyList(),
            CLOCK,
            ZoneOffset.UTC,
        )

        assertEquals(0L, progress.totalWorkedMinutes)
        assertEquals(0L, progress.pendingScheduledMinutes)
    }

    @Test
    fun protectedPlannedShiftDoesNotBecomeWorkedButDeclaredActualStillCounts() {
        val segment = requireNotNull(resolveHoursReferenceSegment(fixedHistory(1_000), DATE))
        val planned = shiftWrite(
            SHIFT_1_ID,
            instant("2026-08-25T08:00:00Z"),
            instant("2026-08-25T12:00:00Z"),
        )
        val protection = WorkProtectionPeriod(DATE, DATE)
        val withoutActual = calculateHoursProgress(
            segment,
            listOf(WorkedShiftSource(planned.shift, null)),
            emptyList(),
            CLOCK,
            ZoneOffset.UTC,
            listOf(protection),
        )
        val withActual = calculateHoursProgress(
            segment,
            listOf(
                WorkedShiftSource(
                    planned.shift,
                    actual(
                        planned,
                        instant("2026-08-25T08:00:00Z"),
                        instant("2026-08-25T11:00:00Z"),
                        emptyList(),
                    ),
                ),
            ),
            emptyList(),
            CLOCK,
            ZoneOffset.UTC,
            listOf(protection),
        )

        assertEquals(0L, withoutActual.totalWorkedMinutes)
        assertEquals(0L, withoutActual.pendingScheduledMinutes)
        assertEquals(3L * 60L, withActual.totalWorkedMinutes)
    }

    @Test
    fun sourceCrossingMonthBoundaryBelongsEntirelyToItsStartSegment() {
        val segment = requireNotNull(
            resolveHoursReferenceSegment(fixedHistory(10_000), LocalDate.of(2026, 8, 31)),
        )
        val crossing = shiftSource(
            SHIFT_1_ID,
            instant("2026-08-31T22:00:00Z"),
            instant("2026-09-01T06:00:00Z"),
        )
        val progress = calculateHoursProgress(
            segment,
            listOf(crossing),
            emptyList(),
            Clock.fixed(instant("2026-09-02T12:00:00Z"), ZoneOffset.UTC),
            ZoneOffset.UTC,
        )

        assertEquals(8L * 60L, progress.regularWorkedMinutes)
    }

    @Test
    fun absentTargetNeverProducesFalseZeroPercentageMissingOrExcess() {
        val references = listOf(
            WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
            WorkConfiguration(WorkSector.NURSING, HoursReference.NotUsed, null),
            WorkConfiguration(WorkSector.NURSING, HoursReference.Unknown(), null),
        )
        references.forEachIndexed { index, configuration ->
            val history = history(
                EffectiveRevision(
                    UUID.nameUUIDFromBytes("revision-$index".toByteArray()),
                    DATE,
                    configuration,
                ),
            )
            val segment = requireNotNull(resolveHoursReferenceSegment(history, DATE))
            val progress = calculateHoursProgress(
                segment,
                emptyList(),
                emptyList(),
                CLOCK,
                ZoneOffset.UTC,
            )
            assertNull(progress.targetMinutes)
            assertNull(progress.missingMinutes)
            assertNull(progress.excessMinutes)
            assertNull(progress.completionPercentage)
        }
    }

    @Test
    fun unrelatedRevisionDoesNotSplitAReferenceWithoutANaturalPeriod() {
        val first = revision(
            DATE.minusDays(10),
            HoursReference.Unknown(),
            startedOn = null,
        )
        val unrelated = EffectiveRevision(
            REVISION_2_ID,
            DATE.minusDays(2),
            first.value.copy(availabilityLabel = AvailabilityLabel.PASSIVE_GUARD),
        )

        val segment = requireNotNull(
            resolveHoursReferenceSegment(history(first, unrelated), DATE),
        )

        assertEquals(first.effectiveFrom, segment.startInclusive)
        assertEquals(first.id, segment.ownerRevision.id)
        assertEquals(LocalDate.MAX, segment.endExclusive)
        assertEquals(HoursTargetState.Unknown, segment.target)
    }

    @Test
    fun shorterAndLongerActualHoursStayHabitualUnlessExplicitlyClassifiedAsExtra() {
        val segment = requireNotNull(resolveHoursReferenceSegment(fixedHistory(2_000), DATE))
        val shortWrite = shiftWrite(
            SHIFT_1_ID,
            instant("2026-08-25T08:00:00Z"),
            instant("2026-08-25T16:00:00Z"),
        )
        val longWrite = shiftWrite(
            SHIFT_2_ID,
            instant("2026-08-25T08:00:00Z"),
            instant("2026-08-25T12:00:00Z"),
        )
        val shortActual = actual(
            shortWrite,
            instant("2026-08-25T09:00:00Z"),
            instant("2026-08-25T14:00:00Z"),
            emptyList(),
        )
        val longActual = actual(
            longWrite,
            instant("2026-08-25T08:00:00Z"),
            instant("2026-08-25T18:00:00Z"),
            emptyList(),
        )

        val progress = calculateHoursProgress(
            segment,
            listOf(
                WorkedShiftSource(shortWrite.shift, shortActual),
                WorkedShiftSource(longWrite.shift, longActual),
            ),
            emptyList(),
            Clock.fixed(instant("2026-08-26T12:00:00Z"), ZoneOffset.UTC),
            ZoneOffset.UTC,
        )

        assertEquals(15L * 60L, progress.regularWorkedMinutes)
        assertTrue(progress.extrasByClass.isEmpty())
        assertEquals(15L * 60L, progress.totalWorkedMinutes)
    }

    @Test
    fun confirmedOverlapsSumCompletelyAndArithmeticOverflowIsDetected() {
        val segment = requireNotNull(resolveHoursReferenceSegment(fixedHistory(2_000), DATE))
        val first = shiftSource(
            SHIFT_1_ID,
            instant("2026-08-25T08:00:00Z"),
            instant("2026-08-25T12:00:00Z"),
        )
        val second = shiftSource(
            SHIFT_2_ID,
            instant("2026-08-25T10:00:00Z"),
            instant("2026-08-25T14:00:00Z"),
        )

        val progress = calculateHoursProgress(
            segment,
            listOf(first, second),
            emptyList(),
            Clock.fixed(instant("2026-08-26T12:00:00Z"), ZoneOffset.UTC),
            ZoneOffset.UTC,
        )

        assertEquals(8L * 60L, progress.regularWorkedMinutes)
        assertThrows(ArithmeticException::class.java) {
            ExtraClassProgress(
                HistoricalExtraClassKey(CLASS_ID, "Extra", true, true),
                Long.MAX_VALUE,
                1L,
            ).totalMinutes
        }
    }

    @Test
    fun realStartDateOwnsTheWholeShiftEvenWhenCalendarDateIsDifferent() {
        val segment = requireNotNull(resolveHoursReferenceSegment(fixedHistory(10_000), DATE))
        val write = shiftWrite(
            SHIFT_1_ID,
            instant("2026-08-24T22:00:00Z"),
            instant("2026-08-25T06:00:00Z"),
        )
        val moved = actual(
            write,
            instant("2026-08-25T00:30:00Z"),
            instant("2026-08-25T06:30:00Z"),
            emptyList(),
        )
        val source = WorkedShiftSource(write.shift, moved)

        assertEquals(LocalDate.of(2026, 8, 25), source.ownerLocalDate)
        assertEquals(
            6L * 60L,
            calculateHoursProgress(segment, listOf(source), emptyList(), CLOCK, ZoneOffset.UTC)
                .regularWorkedMinutes,
        )
    }

    @Test
    fun leapDayAndYearBoundaryWindowsRemainNatural() {
        val leap = HoursPeriod.Monthly.windowContaining(LocalDate.of(2028, 2, 29))
        val year = HoursPeriod.Monthly.windowContaining(LocalDate.of(2026, 12, 31))

        assertEquals(LocalDate.of(2028, 3, 1), leap.endExclusive)
        assertEquals(LocalDate.of(2027, 1, 1), year.endExclusive)
        assertFalse(LocalDate.of(2028, 3, 1) in leap)
    }

    private fun fixedHistory(target: Long) = history(
        revision(
            DATE,
            HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(target)),
            DATE,
        ),
    )

    private fun history(vararg revisions: EffectiveRevision<WorkConfiguration>) =
        history(revisions.toList(), emptyList())

    private fun history(
        revisions: List<EffectiveRevision<WorkConfiguration>>,
        entries: List<PerPeriodHoursEntry>,
    ) = WorkConfigurationHistory(
        EffectiveDateTimeline(TIMELINE_ID, revisions),
        PerPeriodHoursValues(entries),
    )

    private fun revision(
        effectiveFrom: LocalDate,
        reference: HoursReference,
        startedOn: LocalDate?,
        id: UUID = REVISION_1_ID,
    ) = EffectiveRevision(
        id,
        effectiveFrom,
        WorkConfiguration(WorkSector.NURSING, reference, null, startedOn),
    )

    private fun shiftSource(
        id: UUID,
        start: Instant,
        end: Instant,
        status: ShiftStatus = ShiftStatus.PLANNED,
    ) = WorkedShiftSource(shiftWrite(id, start, end, status).shift, null)

    private fun shiftWrite(
        id: UUID,
        start: Instant,
        end: Instant,
        status: ShiftStatus = ShiftStatus.PLANNED,
    ) = V2ShiftWrite(
        Shift(
            id,
            start,
            end,
            ZoneOffset.UTC,
            start.atZone(ZoneOffset.UTC).toLocalDate(),
            "Hospital",
            "HOS",
            null,
            LocalTime.ofInstant(start, ZoneOffset.UTC),
            LocalTime.ofInstant(end, ZoneOffset.UTC),
            COLOR,
            null,
            status,
            OBJECTIVE_ID,
            CREATED,
            CREATED,
        ),
        ShiftWorkSnapshot(
            id,
            TIMELINE_ID,
            WorkSector.NURSING,
            REVISION_1_ID,
            PLACE_ID,
            OBJECTIVE_ID,
            TEMPLATE_ID,
            TYPE_ID,
            "Turno habitual",
            WorkTypeBehavior.ACTIVE_WORK,
        ),
    )

    private fun actual(
        write: V2ShiftWrite,
        start: Instant,
        end: Instant,
        intervals: List<ShiftExtraInterval>,
    ) = ShiftActualAggregate(
        ShiftActualRecord(
            write.shift.id,
            TIMELINE_ID,
            WorkSector.NURSING,
            start,
            end,
            "Horario informado",
            null,
            CREATED,
            CREATED,
        ),
        intervals,
    )

    private fun interval(
        write: V2ShiftWrite,
        start: Instant,
        end: Instant,
        helps: Boolean,
    ) = ShiftExtraInterval(
        EXTRA_INTERVAL_ID,
        write.shift.id,
        TIMELINE_ID,
        WorkSector.NURSING,
        CLASS_ID,
        start,
        end,
        "Horas extra",
        helps,
        true,
        CREATED,
        CREATED,
    )

    private fun independent(start: Instant, end: Instant, helps: Boolean) = IndependentExtraWorkRecord(
        INDEPENDENT_ID,
        TIMELINE_ID,
        WorkSector.NURSING,
        REVISION_1_ID,
        PLACE_ID,
        OBJECTIVE_ID,
        TYPE_ID,
        null,
        NON_HELPING_CLASS_ID,
        start.atZone(ZoneOffset.UTC).toLocalDate(),
        ZoneOffset.UTC,
        start,
        end,
        IndependentExtraWorkSnapshot(
            "Hospital",
            "HOS",
            null,
            "Turno extra",
            WorkTypeBehavior.ACTIVE_WORK,
            COLOR,
            null,
            "Extra especial",
            helps,
            true,
        ),
        CREATED,
        CREATED,
    )

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 25)
        val NOW: Instant = instant("2026-08-25T12:00:00Z")
        val CREATED: Instant = instant("2026-08-20T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        const val COLOR: Int = 0xFF556677.toInt()
        val TIMELINE_ID: UUID = uuid(1)
        val REVISION_1_ID: UUID = uuid(2)
        val REVISION_2_ID: UUID = uuid(3)
        val DEFINITION_ID: UUID = uuid(4)
        val ENTRY_ID: UUID = uuid(5)
        val SHIFT_1_ID: UUID = uuid(6)
        val SHIFT_2_ID: UUID = uuid(7)
        val PLACE_ID: UUID = uuid(8)
        val OBJECTIVE_ID: UUID = uuid(9)
        val TEMPLATE_ID: UUID = uuid(10)
        val TYPE_ID: UUID = uuid(11)
        val CLASS_ID: UUID = uuid(12)
        val NON_HELPING_CLASS_ID: UUID = uuid(13)
        val EXTRA_INTERVAL_ID: UUID = uuid(14)
        val INDEPENDENT_ID: UUID = uuid(15)

        fun instant(value: String): Instant = Instant.parse(value)
        fun uuid(value: Int): UUID = UUID.fromString("92000000-0000-0000-0000-${value.toString().padStart(12, '0')}")
    }
}
