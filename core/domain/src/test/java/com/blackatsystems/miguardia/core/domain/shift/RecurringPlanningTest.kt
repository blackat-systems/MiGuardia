package com.blackatsystems.miguardia.core.domain.shift

import com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal
import com.blackatsystems.miguardia.core.domain.model.RecurringMedicalLeaveVersion
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrence
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState
import com.blackatsystems.miguardia.core.domain.model.RecurringPattern
import com.blackatsystems.miguardia.core.domain.model.RecurringPlan
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanExpectation
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevision
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.model.RecurringProtectionExpectation
import com.blackatsystems.miguardia.core.domain.model.RecurringShiftProtectionVersion
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringPlanningTest {
    @Test
    fun everyWeekdayAndACombinationAreInclusiveOrderedAndUnique() {
        DayOfWeek.entries.forEach { day ->
            val dates = expand(
                RecurringPattern.Weekdays.of(listOf(day)),
                DATE,
                DATE.plusDays(13),
            )
            assertEquals(listOf(day, day), dates.map(LocalDate::getDayOfWeek))
        }

        val mixed = expand(
            RecurringPattern.Weekdays.of(
                listOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            ),
            DATE,
            DATE.plusDays(8),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 8, 26),
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 8, 31),
            ),
            mixed,
        )
        assertEquals(mixed.sorted(), mixed)
        assertEquals(mixed.distinct(), mixed)
    }

    @Test
    fun everyNDaysAndWeeksStayAnchoredAcrossMonthAndYear() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 12, 29),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 4),
            ),
            expand(
                RecurringPattern.EveryNDays(3),
                LocalDate.of(2026, 12, 29),
                LocalDate.of(2027, 1, 4),
            ),
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 23),
                LocalDate.of(2026, 9, 6),
                LocalDate.of(2026, 9, 20),
            ),
            expand(
                RecurringPattern.EveryNWeeks(2),
                DATE,
                LocalDate.of(2026, 9, 20),
            ),
        )
    }

    @Test
    fun monthlySupportsFirstSecondThirdFourthAndLastWeekdayIncludingLeapFebruary() {
        val expected = mapOf(
            MonthlyOrdinal.FIRST to LocalDate.of(2028, 2, 7),
            MonthlyOrdinal.SECOND to LocalDate.of(2028, 2, 14),
            MonthlyOrdinal.THIRD to LocalDate.of(2028, 2, 21),
            MonthlyOrdinal.FOURTH to LocalDate.of(2028, 2, 28),
            MonthlyOrdinal.LAST to LocalDate.of(2028, 2, 28),
        )

        expected.forEach { (ordinal, date) ->
            assertEquals(
                listOf(date),
                expand(
                    RecurringPattern.Monthly(ordinal, DayOfWeek.MONDAY),
                    LocalDate.of(2028, 2, 1),
                    LocalDate.of(2028, 2, 29),
                    today = LocalDate.of(2028, 2, 1),
                ),
            )
        }
    }

    @Test
    fun monthlyRangeCanStartAfterItsOnlyMatchAndFailsInsteadOfOmittingSilently() {
        assertThrows(InvalidLocalDataException::class.java) {
            expand(
                RecurringPattern.Monthly(MonthlyOrdinal.FIRST, DayOfWeek.MONDAY),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 31),
            )
        }
    }

    @Test
    fun pastReverseAndUnrepresentableRangesFailInAControlledWay() {
        assertThrows(InvalidLocalDataException::class.java) {
            expand(RecurringPattern.EveryNDays(1), DATE.minusDays(1), DATE)
        }
        assertThrows(InvalidLocalDataException::class.java) {
            expand(RecurringPattern.EveryNDays(1), DATE.plusDays(1), DATE)
        }
        assertThrows(InvalidLocalDataException::class.java) {
            expand(
                RecurringPattern.EveryNDays(1),
                LocalDate.MIN,
                LocalDate.MAX,
                today = LocalDate.MIN,
            )
        }
    }

    @Test
    fun broadFiniteRangeIsCompleteAndDeterministic() {
        val first = expand(
            RecurringPattern.EveryNDays(17),
            DATE,
            DATE.plusYears(20),
        )
        val second = expand(
            RecurringPattern.EveryNDays(17),
            DATE,
            DATE.plusYears(20),
        )

        assertEquals(430, first.size)
        assertEquals(first, second)
        assertEquals(DATE, first.first())
        assertTrue(first.last() <= DATE.plusYears(20))
    }

    @Test
    fun concreteShiftLimitAcceptsTwoThousandAndRejectsTwoThousandOneWithoutTruncating() {
        val accepted = expand(
            RecurringPattern.EveryNDays(1),
            DATE,
            DATE.plusDays((MAX_RECURRING_CONCRETE_SHIFTS - 1).toLong()),
        )

        assertEquals(MAX_RECURRING_CONCRETE_SHIFTS, accepted.size)
        assertEquals(DATE.plusDays(1_999), accepted.last())

        val error = assertThrows(InvalidLocalDataException::class.java) {
            expand(
                RecurringPattern.EveryNDays(1),
                DATE,
                DATE.plusDays(MAX_RECURRING_CONCRETE_SHIFTS.toLong()),
            )
        }
        assertTrue(error.message.orEmpty().contains("2.000 jornadas concretas"))
    }

    @Test
    fun newPlanAndRevisionRejectDatesThatAreNotTheExactPatternExpansion() {
        val fullRangeRevision = revision(end = DATE.plusDays(2))
        assertThrows(IllegalArgumentException::class.java) {
            planNewRecurringPlan(
                plan = plan(),
                revision = fullRangeRevision,
                dates = listOf(DATE, DATE.plusDays(2)),
                candidates = listOf(write(DATE, SHIFT_A), write(DATE.plusDays(2), SHIFT_B)),
                existingShifts = emptyList(),
                linkedOccurrences = emptyList(),
                protection = RecurringProtectionExpectation.EMPTY,
                conflictPolicy = RecurringConflictPolicy.KEEP_EXISTING,
                medicalLeaveDates = emptySet(),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            planRecurringRevision(
                current = aggregate(emptyList()),
                revision = revision(
                    id = NEXT_REVISION_ID,
                    number = 2,
                    effectiveFrom = DATE,
                    end = DATE.plusDays(2),
                ),
                dates = listOf(DATE, DATE.plusDays(1)),
                candidates = listOf(write(DATE, SHIFT_A), write(DATE.plusDays(1), SHIFT_B)),
                existingShifts = emptyList(),
                linkedOccurrences = emptyList(),
                protection = RecurringProtectionExpectation.EMPTY,
                conflictPolicy = RecurringConflictPolicy.KEEP_EXISTING,
                medicalLeaveDates = emptySet(),
            )
        }
    }

    @Test
    fun createPlanKeepsOccupiedDatesAsDurableExclusions() {
        val manual = write(date = DATE, id = SHIFT_A)
        val dates = listOf(DATE, DATE.plusDays(1))
        val preview = planNewRecurringPlan(
            plan = plan(),
            revision = revision(end = DATE.plusDays(1)),
            dates = dates,
            candidates = dates.mapIndexed { index, date ->
                write(date, if (index == 0) SHIFT_B else SHIFT_C)
            },
            existingShifts = listOf(manual.shift),
            linkedOccurrences = emptyList(),
            protection = RecurringProtectionExpectation.EMPTY,
            conflictPolicy = RecurringConflictPolicy.KEEP_EXISTING,
            medicalLeaveDates = setOf(DATE.plusDays(1)),
        )

        assertTrue(preview.canConfirm)
        assertEquals(listOf(DATE), preview.occupiedDates)
        assertEquals(setOf(DATE.plusDays(1)), preview.medicalLeaveDates)
        assertEquals(1, preview.mutation!!.shiftMutation.shiftsToInsert.size)
        assertEquals(
            RecurringOccurrenceState.EXCLUDED,
            preview.mutation.occurrencesToInsert.single { it.localDate == DATE }.state,
        )
        assertNull(preview.mutation.occurrencesToInsert.single { it.localDate == DATE }.shiftId)
    }

    @Test
    fun createPlanReplacesOnlyAutomaticIntactAndRetiresItsPreviousOccurrence() {
        val oldWrite = write(DATE, SHIFT_A)
        val oldOccurrence = occurrence(
            planId = OTHER_PLAN_ID,
            revisionId = OTHER_REVISION_ID,
            date = DATE,
            shiftId = SHIFT_A,
        )
        val preview = planNewRecurringPlan(
            plan = plan(),
            revision = revision(end = DATE),
            dates = listOf(DATE),
            candidates = listOf(write(DATE, SHIFT_B)),
            existingShifts = listOf(oldWrite.shift),
            linkedOccurrences = listOf(oldOccurrence),
            protection = protection(oldWrite.shift),
            conflictPolicy = RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT,
            medicalLeaveDates = emptySet(),
        )

        assertEquals(RecurringDateAction.REPLACE_AUTOMATIC, preview.results.single().action)
        assertEquals(setOf(SHIFT_A), preview.mutation!!.shiftMutation.shiftIdsToDelete)
        assertEquals(
            RecurringOccurrenceState.RETIRED,
            preview.mutation.occurrencesToUpdate.single().state,
        )
        assertNull(preview.mutation.occurrencesToUpdate.single().shiftId)
    }

    @Test
    fun manualCustomizedOrProtectedShiftIsNeverReplacedSilently() {
        val manual = write(DATE, SHIFT_A)
        val customized = write(DATE, SHIFT_B)
        val protected = write(DATE, SHIFT_C).copy(
            shift = write(DATE, SHIFT_C).shift.copy(status = ShiftStatus.ABSENT),
        )
        val occurrences = listOf(
            occurrence(OTHER_PLAN_ID, OTHER_REVISION_ID, DATE, SHIFT_B, RecurringOccurrenceState.CUSTOMIZED),
            occurrence(THIRD_PLAN_ID, THIRD_REVISION_ID, DATE, SHIFT_C),
        )
        val preview = planNewRecurringPlan(
            plan(),
            revision(end = DATE),
            listOf(DATE),
            listOf(write(DATE, SHIFT_D)),
            listOf(manual.shift, customized.shift, protected.shift),
            occurrences,
            protection(customized.shift, protected.shift),
            RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT,
            emptySet(),
        )

        assertFalse(preview.canConfirm)
        assertEquals(RecurringDateAction.KEEP_EXISTING_AS_EXCLUDED, preview.results.single().action)
        assertTrue(preview.mutation == null)
    }

    @Test
    fun keepBothRequiresExplicitPolicyAndKeepsSecondShiftWarnings() {
        val existing = write(DATE, SHIFT_A)
        val candidate = write(DATE, SHIFT_B)
        val preview = planNewRecurringPlan(
            plan(),
            revision(end = DATE),
            listOf(DATE),
            listOf(candidate),
            listOf(existing.shift),
            emptyList(),
            RecurringProtectionExpectation.EMPTY,
            RecurringConflictPolicy.KEEP_BOTH,
            emptySet(),
        )

        assertTrue(preview.canConfirm)
        assertEquals(RecurringDateAction.KEEP_BOTH, preview.results.single().action)
        assertTrue(preview.warnings.any { it is ShiftPlanningWarning.SameDate })
        assertTrue(preview.warnings.any { it is ShiftPlanningWarning.Overlap })
    }

    @Test
    fun previewWarnsWhenARecurringCandidateLeavesLessThanTwelveHoursOfRest() {
        val previous = write(
            date = DATE.minusDays(1),
            id = SHIFT_A,
            start = LocalTime.of(15, 0),
            end = LocalTime.of(23, 0),
        )
        val candidate = write(DATE, SHIFT_B)

        val preview = planNewRecurringPlan(
            plan(),
            revision(end = DATE),
            listOf(DATE),
            listOf(candidate),
            listOf(previous.shift),
            emptyList(),
            RecurringProtectionExpectation.EMPTY,
            RecurringConflictPolicy.KEEP_EXISTING,
            emptySet(),
        )

        assertTrue(preview.warnings.any { warning ->
            warning is ShiftPlanningWarning.ShortRest && warning.actualRest.toHours() == 9L
        })
    }

    @Test
    fun cancelNeverBuildsAMutation() {
        val preview = planNewRecurringPlan(
            plan(),
            revision(end = DATE),
            listOf(DATE),
            listOf(write(DATE, SHIFT_B)),
            listOf(write(DATE, SHIFT_A).shift),
            emptyList(),
            RecurringProtectionExpectation.EMPTY,
            RecurringConflictPolicy.CANCEL,
            emptySet(),
        )

        assertFalse(preview.canConfirm)
        assertNull(preview.mutation)
        assertEquals(RecurringDateAction.BLOCKED_BY_CANCEL, preview.results.single().action)
    }

    @Test
    fun cancelConflictPolicyStillCreatesWhenEveryDateIsFree() {
        val candidate = write(DATE, SHIFT_A)
        val preview = planNewRecurringPlan(
            plan(),
            revision(end = DATE),
            listOf(DATE),
            listOf(candidate),
            emptyList(),
            emptyList(),
            RecurringProtectionExpectation.EMPTY,
            RecurringConflictPolicy.CANCEL,
            emptySet(),
        )

        assertTrue(preview.canConfirm)
        assertEquals(listOf(candidate), preview.mutation!!.shiftMutation.shiftsToInsert)
    }

    @Test
    fun changingFromDateUpdatesAndRetiresOnlyAutomaticIntactFutureOccurrences() {
        val pastDate = DATE.minusDays(1)
        val keptDate = DATE
        val retiredDate = DATE.plusDays(1)
        val customizedDate = DATE.plusDays(2)
        val excludedDate = DATE.plusDays(3)
        val protectedDate = DATE.plusDays(4)
        val occurrences = listOf(
            occurrence(PLAN_ID, REVISION_ID, pastDate, SHIFT_A),
            occurrence(PLAN_ID, REVISION_ID, keptDate, SHIFT_B),
            occurrence(PLAN_ID, REVISION_ID, retiredDate, SHIFT_C),
            occurrence(PLAN_ID, REVISION_ID, customizedDate, SHIFT_D, RecurringOccurrenceState.CUSTOMIZED),
            occurrence(PLAN_ID, REVISION_ID, excludedDate, null, RecurringOccurrenceState.EXCLUDED),
            occurrence(PLAN_ID, REVISION_ID, protectedDate, SHIFT_E),
        )
        val writes = listOf(
            write(pastDate, SHIFT_A),
            write(keptDate, SHIFT_B),
            write(retiredDate, SHIFT_C),
            write(customizedDate, SHIFT_D),
            write(protectedDate, SHIFT_E, status = ShiftStatus.ABSENT),
        )
        val current = aggregate(occurrences)
        val next = revision(
            id = NEXT_REVISION_ID,
            number = 2,
            effectiveFrom = DATE,
            end = protectedDate,
            pattern = RecurringPattern.Weekdays.of(
                listOf(
                    keptDate.dayOfWeek,
                    customizedDate.dayOfWeek,
                    excludedDate.dayOfWeek,
                    protectedDate.dayOfWeek,
                ),
            ),
        )
        val preview = planRecurringRevision(
            current = current,
            revision = next,
            dates = listOf(keptDate, customizedDate, excludedDate, protectedDate),
            candidates = listOf(
                write(keptDate, SHIFT_B, updatedAt = NOW.plusSeconds(60)),
                write(customizedDate, UUID.randomUUID()),
                write(excludedDate, UUID.randomUUID()),
                write(protectedDate, UUID.randomUUID()),
            ),
            existingShifts = writes.map(V2ShiftWrite::shift),
            linkedOccurrences = occurrences,
            protection = protection(*writes.map(V2ShiftWrite::shift).toTypedArray()),
            conflictPolicy = RecurringConflictPolicy.KEEP_EXISTING,
            medicalLeaveDates = emptySet(),
        )

        val mutation = preview.mutation!!
        assertEquals(listOf(SHIFT_B), mutation.shiftMutation.shiftsToUpdate.map { it.shift.id })
        assertEquals(setOf(SHIFT_C), mutation.shiftMutation.shiftIdsToDelete)
        assertEquals(
            RecurringOccurrenceState.RETIRED,
            mutation.occurrencesToUpdate.single { it.localDate == retiredDate }.state,
        )
        assertTrue(mutation.occurrencesToUpdate.none { it.localDate == pastDate })
        assertTrue(mutation.occurrencesToUpdate.none { it.localDate == customizedDate })
        assertTrue(mutation.occurrencesToUpdate.none { it.localDate == excludedDate })
        assertTrue(mutation.occurrencesToUpdate.none { it.localDate == protectedDate })
    }

    @Test
    fun changingAnAutomaticDateKeepsOtherOccupantsVisibleAndExcludesOwnForKeepExisting() {
        val own = write(DATE, SHIFT_A)
        val manual = write(DATE, SHIFT_B)
        val ownOccurrence = occurrence(PLAN_ID, REVISION_ID, DATE, SHIFT_A)

        val preview = planRecurringRevision(
            current = aggregate(listOf(ownOccurrence)),
            revision = revision(NEXT_REVISION_ID, 2, DATE, DATE),
            dates = listOf(DATE),
            candidates = listOf(write(DATE, SHIFT_A, updatedAt = NOW.plusSeconds(60))),
            existingShifts = listOf(own.shift, manual.shift),
            linkedOccurrences = listOf(ownOccurrence),
            protection = protection(own.shift),
            conflictPolicy = RecurringConflictPolicy.KEEP_EXISTING,
            medicalLeaveDates = emptySet(),
        )

        assertEquals(RecurringDateAction.KEEP_EXISTING_AS_EXCLUDED, preview.results.single().action)
        assertEquals(setOf(SHIFT_A, SHIFT_B), preview.results.single().occupants.mapTo(linkedSetOf()) { it.shift.id })
        val mutation = preview.mutation!!
        assertEquals(setOf(SHIFT_A), mutation.shiftMutation.shiftIdsToDelete)
        assertTrue(mutation.shiftMutation.shiftsToUpdate.isEmpty())
        assertEquals(RecurringOccurrenceState.EXCLUDED, mutation.occurrencesToUpdate.single().state)
        assertNull(mutation.occurrencesToUpdate.single().shiftId)
    }

    @Test
    fun changingAnAutomaticDateKeepsBothByUpdatingOwnAndPreservingOtherOccupants() {
        val own = write(DATE, SHIFT_A)
        val manual = write(DATE, SHIFT_B)
        val ownOccurrence = occurrence(PLAN_ID, REVISION_ID, DATE, SHIFT_A)

        val preview = planRecurringRevision(
            current = aggregate(listOf(ownOccurrence)),
            revision = revision(NEXT_REVISION_ID, 2, DATE, DATE),
            dates = listOf(DATE),
            candidates = listOf(write(DATE, SHIFT_A, updatedAt = NOW.plusSeconds(60))),
            existingShifts = listOf(own.shift, manual.shift),
            linkedOccurrences = listOf(ownOccurrence),
            protection = protection(own.shift),
            conflictPolicy = RecurringConflictPolicy.KEEP_BOTH,
            medicalLeaveDates = emptySet(),
        )

        assertEquals(RecurringDateAction.KEEP_BOTH, preview.results.single().action)
        assertEquals(listOf(SHIFT_A), preview.mutation!!.shiftMutation.shiftsToUpdate.map { it.shift.id })
        assertTrue(preview.mutation.shiftMutation.shiftIdsToDelete.isEmpty())
        assertEquals(REVISION_ID, ownOccurrence.revisionId)
        assertEquals(NEXT_REVISION_ID, preview.mutation.occurrencesToUpdate.single().revisionId)
        assertTrue(preview.warnings.any { it is ShiftPlanningWarning.SameDate })
    }

    @Test
    fun changingAnAutomaticDateCancelsTheWholeMutationWhenAnotherOccupantExists() {
        val own = write(DATE, SHIFT_A)
        val manual = write(DATE, SHIFT_B)
        val ownOccurrence = occurrence(PLAN_ID, REVISION_ID, DATE, SHIFT_A)

        val preview = planRecurringRevision(
            current = aggregate(listOf(ownOccurrence)),
            revision = revision(NEXT_REVISION_ID, 2, DATE, DATE),
            dates = listOf(DATE),
            candidates = listOf(write(DATE, SHIFT_A, updatedAt = NOW.plusSeconds(60))),
            existingShifts = listOf(own.shift, manual.shift),
            linkedOccurrences = listOf(ownOccurrence),
            protection = protection(own.shift),
            conflictPolicy = RecurringConflictPolicy.CANCEL,
            medicalLeaveDates = emptySet(),
        )

        assertEquals(RecurringDateAction.BLOCKED_BY_CANCEL, preview.results.single().action)
        assertNull(preview.mutation)
    }

    @Test
    fun changingAnAutomaticDateCanReplaceOnlyAnotherAutomaticIntactOccurrence() {
        val own = write(DATE, SHIFT_A)
        val other = write(DATE, SHIFT_B)
        val ownOccurrence = occurrence(PLAN_ID, REVISION_ID, DATE, SHIFT_A)
        val otherOccurrence = occurrence(OTHER_PLAN_ID, OTHER_REVISION_ID, DATE, SHIFT_B)

        val preview = planRecurringRevision(
            current = aggregate(listOf(ownOccurrence)),
            revision = revision(NEXT_REVISION_ID, 2, DATE, DATE),
            dates = listOf(DATE),
            candidates = listOf(write(DATE, SHIFT_A, updatedAt = NOW.plusSeconds(60))),
            existingShifts = listOf(own.shift, other.shift),
            linkedOccurrences = listOf(ownOccurrence, otherOccurrence),
            protection = protection(own.shift, other.shift),
            conflictPolicy = RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT,
            medicalLeaveDates = emptySet(),
        )

        val mutation = preview.mutation!!
        assertEquals(RecurringDateAction.REPLACE_AUTOMATIC, preview.results.single().action)
        assertEquals(listOf(SHIFT_A), mutation.shiftMutation.shiftsToUpdate.map { it.shift.id })
        assertEquals(setOf(SHIFT_B), mutation.shiftMutation.shiftIdsToDelete)
        assertEquals(
            RecurringOccurrenceState.AUTOMATIC,
            mutation.occurrencesToUpdate.single { it.planId == PLAN_ID }.state,
        )
        assertEquals(
            RecurringOccurrenceState.RETIRED,
            mutation.occurrencesToUpdate.single { it.planId == OTHER_PLAN_ID }.state,
        )
    }

    @Test
    fun replacePolicyNeverTouchesManualCustomizedOrProtectedOtherOccupants() {
        val own = write(DATE, SHIFT_A)
        val manual = write(DATE, SHIFT_B)
        val customized = write(DATE, SHIFT_C)
        val protected = write(DATE, SHIFT_D, status = ShiftStatus.ABSENT)
        val ownOccurrence = occurrence(PLAN_ID, REVISION_ID, DATE, SHIFT_A)
        val customizedOccurrence = occurrence(
            OTHER_PLAN_ID,
            OTHER_REVISION_ID,
            DATE,
            SHIFT_C,
            RecurringOccurrenceState.CUSTOMIZED,
        )
        val protectedOccurrence = occurrence(THIRD_PLAN_ID, THIRD_REVISION_ID, DATE, SHIFT_D)

        val preview = planRecurringRevision(
            current = aggregate(listOf(ownOccurrence)),
            revision = revision(NEXT_REVISION_ID, 2, DATE, DATE),
            dates = listOf(DATE),
            candidates = listOf(write(DATE, SHIFT_A, updatedAt = NOW.plusSeconds(60))),
            existingShifts = listOf(own.shift, manual.shift, customized.shift, protected.shift),
            linkedOccurrences = listOf(ownOccurrence, customizedOccurrence, protectedOccurrence),
            protection = protection(own.shift, protected.shift),
            conflictPolicy = RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT,
            medicalLeaveDates = emptySet(),
        )

        val mutation = preview.mutation!!
        assertEquals(RecurringDateAction.KEEP_EXISTING_AS_EXCLUDED, preview.results.single().action)
        assertEquals(setOf(SHIFT_A), mutation.shiftMutation.shiftIdsToDelete)
        assertTrue(mutation.shiftMutation.shiftsToUpdate.isEmpty())
        assertTrue(mutation.occurrencesToUpdate.none { it.planId != PLAN_ID })
        assertEquals(
            setOf(SHIFT_A, SHIFT_B, SHIFT_C, SHIFT_D),
            preview.results.single().occupants.mapTo(linkedSetOf()) { it.shift.id },
        )
    }

    @Test
    fun laterRevisionCanReactivateRetiredButNeverExcludedOccurrence() {
        val retiredDate = DATE
        val excludedDate = DATE.plusDays(1)
        val occurrences = listOf(
            occurrence(PLAN_ID, REVISION_ID, retiredDate, null, RecurringOccurrenceState.RETIRED),
            occurrence(PLAN_ID, REVISION_ID, excludedDate, null, RecurringOccurrenceState.EXCLUDED),
        )
        val preview = planRecurringRevision(
            current = aggregate(occurrences),
            revision = revision(NEXT_REVISION_ID, 2, DATE, excludedDate),
            dates = listOf(retiredDate, excludedDate),
            candidates = listOf(write(retiredDate, SHIFT_A), write(excludedDate, SHIFT_B)),
            existingShifts = emptyList(),
            linkedOccurrences = occurrences,
            protection = RecurringProtectionExpectation.EMPTY,
            conflictPolicy = RecurringConflictPolicy.KEEP_EXISTING,
            medicalLeaveDates = emptySet(),
        )

        assertEquals(listOf(SHIFT_A), preview.mutation!!.shiftMutation.shiftsToInsert.map { it.shift.id })
        assertEquals(
            RecurringOccurrenceState.AUTOMATIC,
            preview.mutation.occurrencesToUpdate.single { it.localDate == retiredDate }.state,
        )
        assertTrue(preview.mutation.occurrencesToUpdate.none { it.localDate == excludedDate })
    }

    @Test
    fun finalizationRetiresOnlyAutomaticIntactAndKeepsProtectedAndExceptions() {
        val automatic = occurrence(PLAN_ID, REVISION_ID, DATE, SHIFT_A)
        val protected = occurrence(PLAN_ID, REVISION_ID, DATE.plusDays(1), SHIFT_B)
        val customized = occurrence(
            PLAN_ID,
            REVISION_ID,
            DATE.plusDays(2),
            SHIFT_C,
            RecurringOccurrenceState.CUSTOMIZED,
        )
        val excluded = occurrence(
            PLAN_ID,
            REVISION_ID,
            DATE.plusDays(3),
            null,
            RecurringOccurrenceState.EXCLUDED,
        )
        val automaticWrite = write(DATE, SHIFT_A)
        val protectedWrite = write(DATE.plusDays(1), SHIFT_B, ShiftStatus.ABSENT)
        val customizedWrite = write(DATE.plusDays(2), SHIFT_C)
        val final = revision(
            NEXT_REVISION_ID,
            2,
            DATE,
            DATE.plusDays(3),
            RecurringPlanRevisionKind.FINALIZED,
        )
        val preview = planRecurringFinalization(
            current = aggregate(listOf(automatic, protected, customized, excluded)),
            finalRevision = final,
            existingShifts = listOf(automaticWrite.shift, protectedWrite.shift, customizedWrite.shift),
            protection = protection(automaticWrite.shift, protectedWrite.shift, customizedWrite.shift),
        )

        assertTrue(preview.canConfirm)
        assertEquals(setOf(SHIFT_A), preview.mutation!!.shiftMutation.shiftIdsToDelete)
        assertEquals(1, preview.mutation.occurrencesToUpdate.size)
        assertEquals(RecurringOccurrenceState.RETIRED, preview.mutation.occurrencesToUpdate.single().state)
        assertTrue(preview.results.any { it.action == RecurringDateAction.PRESERVE_PROTECTED })
        assertTrue(preview.results.any { it.action == RecurringDateAction.KEEP_CUSTOMIZED })
        assertTrue(preview.results.any { it.action == RecurringDateAction.KEEP_EXCLUDED })
        assertTrue(preview.freeDates.isEmpty())
    }

    @Test
    fun medicalLeaveProtectsAutomaticOccurrenceAndRetiredDatesRemainExplicitInPreview() {
        val automatic = occurrence(PLAN_ID, REVISION_ID, DATE, SHIFT_A)
        val retiredDate = DATE.plusDays(1)
        val retired = occurrence(
            PLAN_ID,
            REVISION_ID,
            retiredDate,
            null,
            RecurringOccurrenceState.RETIRED,
        )
        val automaticWrite = write(DATE, SHIFT_A)
        val protected = RecurringProtectionExpectation.capture(
            versions = protection(automaticWrite.shift).versionsByShiftId.values,
            startDateInclusive = DATE,
            endDateInclusive = retiredDate,
            medicalLeaves = listOf(
                RecurringMedicalLeaveVersion(
                    id = UUID(0L, 999L),
                    startDate = DATE,
                    endDateInclusive = DATE,
                    updatedAt = NOW,
                ),
            ),
        )
        val final = revision(
            NEXT_REVISION_ID,
            2,
            DATE,
            retiredDate,
            RecurringPlanRevisionKind.FINALIZED,
        )

        val preview = planRecurringFinalization(
            current = aggregate(listOf(automatic, retired)),
            finalRevision = final,
            existingShifts = listOf(automaticWrite.shift),
            protection = protected,
        )

        assertTrue(preview.mutation!!.shiftMutation.shiftIdsToDelete.isEmpty())
        assertEquals(
            listOf(RecurringDateAction.PRESERVE_PROTECTED, RecurringDateAction.KEEP_RETIRED),
            preview.results.map(RecurringDateResult::action),
        )
        assertEquals(setOf(DATE), preview.medicalLeaveDates)
        assertTrue(preview.freeDates.isEmpty())
    }

    @Test
    fun dateSpecificSnapshotsAndMidnightCrossingRemainDistinctInThePlan() {
        val first = write(
            DATE,
            SHIFT_A,
            configurationRevisionId = CONFIG_REVISION_ID,
            start = LocalTime.of(21, 0),
            end = LocalTime.of(6, 0),
        )
        val second = write(
            DATE.plusDays(1),
            SHIFT_B,
            configurationRevisionId = NEXT_CONFIG_REVISION_ID,
            start = LocalTime.of(21, 0),
            end = LocalTime.of(6, 0),
        )
        val preview = planNewRecurringPlan(
            plan(),
            revision(
                end = DATE.plusDays(1),
                startTime = LocalTime.of(21, 0),
                endTime = LocalTime.of(6, 0),
            ),
            listOf(DATE, DATE.plusDays(1)),
            listOf(first, second),
            emptyList(),
            emptyList(),
            RecurringProtectionExpectation.EMPTY,
            RecurringConflictPolicy.KEEP_EXISTING,
            emptySet(),
        )

        val inserted = preview.mutation!!.shiftMutation.shiftsToInsert
        assertEquals(listOf(CONFIG_REVISION_ID, NEXT_CONFIG_REVISION_ID), inserted.map { it.snapshot.configurationRevisionId })
        assertEquals(9 * 60, java.time.Duration.between(second.shift.startAt, second.shift.endAt).toMinutes())
    }

    @Test
    fun everyCandidateMustMatchTheCompleteRevisionSnapshotAndItsPlan() {
        val valid = write(DATE, SHIFT_A)
        val differentId = UUID.fromString("91000000-0000-0000-0000-000000000099")
        val mismatches = listOf(
            valid.copy(snapshot = valid.snapshot.copy(timelineId = differentId)),
            valid.copy(snapshot = valid.snapshot.copy(sector = WorkSector.POLICE)),
            valid.copy(snapshot = valid.snapshot.copy(templateId = differentId)),
            valid.copy(snapshot = valid.snapshot.copy(workPlaceId = differentId)),
            valid.copy(
                shift = valid.shift.copy(sourceObjectiveId = differentId),
                snapshot = valid.snapshot.copy(objectiveId = differentId),
            ),
            valid.copy(snapshot = valid.snapshot.copy(workTypeId = differentId)),
            valid.copy(snapshot = valid.snapshot.copy(workTypeNameSnapshot = "Consultorio")),
            valid.copy(shift = valid.shift.copy(objectiveNameSnapshot = "Hospital Sur")),
            valid.copy(shift = valid.shift.copy(objectiveAbbreviationSnapshot = "HS")),
            valid.copy(shift = valid.shift.copy(objectiveAddressSnapshot = "Calle 2")),
            valid.copy(shift = valid.shift.copy(startTimeSnapshot = LocalTime.of(9, 0))),
            valid.copy(shift = valid.shift.copy(endTimeSnapshot = LocalTime.of(17, 0))),
            valid.copy(shift = valid.shift.copy(colorArgbSnapshot = 0xFF654321.toInt())),
            valid.copy(shift = valid.shift.copy(position = "Puesto 2")),
            valid.copy(shift = valid.shift.copy(zoneId = ZoneId.of("UTC"))),
            valid.copy(shift = valid.shift.copy(startAt = valid.shift.startAt.plusSeconds(60))),
            valid.copy(shift = valid.shift.copy(endAt = valid.shift.endAt.plusSeconds(60))),
        )

        mismatches.forEach { mismatch ->
            assertThrows(IllegalArgumentException::class.java) {
                planNewRecurringPlan(
                    plan = plan(),
                    revision = revision(end = DATE),
                    dates = listOf(DATE),
                    candidates = listOf(mismatch),
                    existingShifts = emptyList(),
                    linkedOccurrences = emptyList(),
                    protection = RecurringProtectionExpectation.EMPTY,
                    conflictPolicy = RecurringConflictPolicy.KEEP_EXISTING,
                    medicalLeaveDates = emptySet(),
                )
            }
        }
    }

    @Test
    fun expectationsAreDefensiveAndDetectAChangedAggregateByValue() {
        val current = aggregate(emptyList())
        val expected = RecurringPlanExpectation.capture(PLAN_ID, current)
        val changed = RecurringPlanExpectation.capture(
            PLAN_ID,
            current.copy(plan = current.plan.copy(createdAt = NOW.plusSeconds(1))),
        )

        assertNotEquals(expected, changed)
        @Suppress("UNCHECKED_CAST")
        val exposed = expected.aggregatesById as MutableMap<UUID, RecurringPlanAggregate?>
        assertThrows(UnsupportedOperationException::class.java) { exposed.clear() }
    }

    private fun expand(
        pattern: RecurringPattern,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate = DATE,
    ): List<LocalDate> = expandRecurringDates(
        pattern,
        start,
        end,
        Clock.fixed(today.atStartOfDay(ZONE).toInstant(), ZONE),
        ZONE,
    )

    private fun plan() = RecurringPlan(PLAN_ID, TIMELINE_ID, WorkSector.MEDICINE, NOW)

    private fun revision(
        id: UUID = REVISION_ID,
        number: Int = 1,
        effectiveFrom: LocalDate = DATE,
        end: LocalDate = DATE.plusDays(10),
        kind: RecurringPlanRevisionKind = RecurringPlanRevisionKind.ACTIVE,
        pattern: RecurringPattern = RecurringPattern.EveryNDays(1),
        startTime: LocalTime = LocalTime.of(8, 0),
        endTime: LocalTime = LocalTime.of(16, 0),
    ) = RecurringPlanRevision(
        id = id,
        planId = PLAN_ID,
        revisionNumber = number,
        effectiveFrom = effectiveFrom,
        kind = kind,
        endDateInclusive = end,
        pattern = pattern,
        templateId = TEMPLATE_ID,
        workPlaceId = PLACE_ID,
        objectiveId = OBJECTIVE_ID,
        workTypeId = TYPE_ID,
        objectiveNameSnapshot = "Hospital Norte",
        objectiveAbbreviationSnapshot = "HN",
        objectiveAddressSnapshot = "Calle 1",
        workTypeNameSnapshot = "Guardia",
        workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
        startTimeSnapshot = startTime,
        endTimeSnapshot = endTime,
        colorArgbSnapshot = 0xFF123456.toInt(),
        positionSnapshot = null,
        zoneId = ZONE,
        createdAt = NOW.plusSeconds(number.toLong()),
    )

    private fun aggregate(occurrences: List<RecurringOccurrence>) = RecurringPlanAggregate(
        plan = plan(),
        revisions = listOf(revision()),
        occurrences = occurrences,
    )

    private fun occurrence(
        planId: UUID,
        revisionId: UUID,
        date: LocalDate,
        shiftId: UUID?,
        state: RecurringOccurrenceState = RecurringOccurrenceState.AUTOMATIC,
    ) = RecurringOccurrence(planId, date, revisionId, shiftId, state, NOW, NOW)

    private fun protection(vararg shifts: Shift): RecurringProtectionExpectation =
        RecurringProtectionExpectation.capture(
            shifts.map { shift ->
                RecurringShiftProtectionVersion(
                    shiftId = shift.id,
                    status = shift.status,
                    notes = emptySet(),
                    hasNotificationConfig = false,
                    notificationLeadMinutes = emptyList(),
                )
            },
        )

    private fun write(
        date: LocalDate,
        id: UUID,
        status: ShiftStatus = ShiftStatus.PLANNED,
        configurationRevisionId: UUID = CONFIG_REVISION_ID,
        start: LocalTime = LocalTime.of(8, 0),
        end: LocalTime = LocalTime.of(16, 0),
        updatedAt: Instant = NOW,
    ): V2ShiftWrite {
        val startAt = date.atTime(start).atZone(ZONE).toInstant()
        val endDate = if (end > start) date else date.plusDays(1)
        val shift = Shift(
            id = id,
            startAt = startAt,
            endAt = endDate.atTime(end).atZone(ZONE).toInstant(),
            zoneId = ZONE,
            localStartDate = date,
            objectiveNameSnapshot = "Hospital Norte",
            objectiveAbbreviationSnapshot = "HN",
            objectiveAddressSnapshot = "Calle 1",
            startTimeSnapshot = start,
            endTimeSnapshot = end,
            colorArgbSnapshot = 0xFF123456.toInt(),
            position = null,
            status = status,
            sourceObjectiveId = OBJECTIVE_ID,
            createdAt = NOW,
            updatedAt = updatedAt,
        )
        return V2ShiftWrite(
            shift,
            ShiftWorkSnapshot(
                shiftId = id,
                timelineId = TIMELINE_ID,
                sector = WorkSector.MEDICINE,
                configurationRevisionId = configurationRevisionId,
                workPlaceId = PLACE_ID,
                objectiveId = OBJECTIVE_ID,
                templateId = TEMPLATE_ID,
                workTypeId = TYPE_ID,
                workTypeNameSnapshot = "Guardia",
                workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
            ),
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 23)
        val NOW: Instant = Instant.parse("2026-08-23T12:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val TIMELINE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000001")
        val PLAN_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000002")
        val OTHER_PLAN_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000003")
        val THIRD_PLAN_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000004")
        val REVISION_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000005")
        val OTHER_REVISION_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000006")
        val THIRD_REVISION_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000007")
        val NEXT_REVISION_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000008")
        val TEMPLATE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000009")
        val PLACE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000010")
        val OBJECTIVE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000011")
        val TYPE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000012")
        val CONFIG_REVISION_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000013")
        val NEXT_CONFIG_REVISION_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000014")
        val SHIFT_A: UUID = UUID.fromString("91000000-0000-0000-0000-000000000015")
        val SHIFT_B: UUID = UUID.fromString("91000000-0000-0000-0000-000000000016")
        val SHIFT_C: UUID = UUID.fromString("91000000-0000-0000-0000-000000000017")
        val SHIFT_D: UUID = UUID.fromString("91000000-0000-0000-0000-000000000018")
        val SHIFT_E: UUID = UUID.fromString("91000000-0000-0000-0000-000000000019")
    }
}
