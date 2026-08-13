package com.blackatsystems.miguardia.core.domain.shift

import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.calendar.temporalStatusAt
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftPlanningTest {
    @Test
    fun dayOvernightAndEqualTimeSchedulesBuildExactIntervals() {
        val date = LocalDate.of(2026, 8, 13)
        val day = newShift(date, schedule(start = LocalTime.of(8, 0), end = LocalTime.of(16, 0)))
        val overnight = newShift(date, schedule(start = LocalTime.of(19, 0), end = LocalTime.of(7, 0)))
        val fullDay = newShift(date, schedule(start = LocalTime.of(8, 0), end = LocalTime.of(8, 0)))

        assertEquals(Duration.ofHours(8), Duration.between(day.startAt, day.endAt))
        assertEquals(Duration.ofHours(12), Duration.between(overnight.startAt, overnight.endAt))
        assertEquals(Duration.ofHours(24), Duration.between(fullDay.startAt, fullDay.endAt))
        assertEquals(date, overnight.localStartDate)
    }

    @Test
    fun historicalShiftRemainsPlannedAndProjectsCompleted() {
        val shift = newShift(LocalDate.of(2026, 7, 1), schedule())

        assertEquals(ShiftStatus.PLANNED, shift.status)
        assertEquals(ShiftTemporalStatus.COMPLETED, shift.temporalStatusAt(NOW))
    }

    @Test
    fun snapshotsAndOptionalPositionAreNormalized() {
        val shift = buildShift(
            id = SHIFT_ID,
            date = DATE,
            objective = objective().copy(fullName = " Depósito ", abbreviation = " dep ", address = " Calle 1 "),
            combination = schedule(),
            position = "   ",
            timestamp = CREATED_AT,
            zoneId = AppDefaults.zoneId(),
        )

        assertEquals("Depósito", shift.objectiveNameSnapshot)
        assertEquals("DEP", shift.objectiveAbbreviationSnapshot)
        assertEquals("Calle 1", shift.objectiveAddressSnapshot)
        assertEquals(schedule().colorArgb, shift.colorArgbSnapshot)
        assertNull(shift.position)
    }

    @Test(expected = InvalidLocalDataException::class)
    fun multipleSelectionRejectsDifferentMonths() {
        validateSingleMonth(setOf(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun replaceDeletesOnlySelectedOccupiedDates() {
        val occupied = oldShift(DATE)
        val outside = oldShift(DATE.plusDays(3), id = OTHER_SHIFT_ID)
        val candidate = newShift(DATE, schedule(), id = NEW_SHIFT_ID)

        val plan = planShiftBatch(
            selectedDates = setOf(DATE),
            existingShifts = listOf(occupied, outside),
            candidates = listOf(candidate),
            policy = OccupiedDatePolicy.REPLACE,
        )

        assertEquals(setOf(occupied.id), plan.mutation.shiftIdsToDelete)
        assertEquals(listOf(candidate), plan.mutation.shiftsToInsert)
        assertFalse(outside.id in plan.mutation.shiftIdsToDelete)
    }

    @Test
    fun keepOccupiedOmitsOnlyOccupiedAndCancelMutatesNothing() {
        val secondDate = DATE.plusDays(1)
        val occupied = oldShift(DATE)
        val candidates = listOf(
            newShift(DATE, schedule(), id = NEW_SHIFT_ID),
            newShift(secondDate, schedule(), id = SECOND_NEW_SHIFT_ID),
        )
        val keep = planShiftBatch(
            selectedDates = setOf(DATE, secondDate),
            existingShifts = listOf(occupied),
            candidates = candidates,
            policy = OccupiedDatePolicy.KEEP_OCCUPIED,
        )
        val cancel = planShiftBatch(
            selectedDates = setOf(DATE, secondDate),
            existingShifts = listOf(occupied),
            candidates = candidates,
            policy = OccupiedDatePolicy.CANCEL,
        )

        assertEquals(setOf(DATE), keep.omittedDates)
        assertEquals(listOf(secondDate), keep.mutation.shiftsToInsert.map { it.localStartDate })
        assertTrue(cancel.mutation.shiftIdsToDelete.isEmpty())
        assertTrue(cancel.mutation.shiftsToInsert.isEmpty())
    }

    @Test
    fun sameDateAndOverlapProduceWarningsWithoutBlockingPlan() {
        val first = newShift(DATE, schedule(start = LocalTime.of(8, 0), end = LocalTime.of(16, 0)))
        val second = newShift(
            DATE,
            schedule(id = OTHER_SCHEDULE_ID, start = LocalTime.of(12, 0), end = LocalTime.of(20, 0)),
            id = NEW_SHIFT_ID,
        )

        val warnings = evaluateShiftWarnings(listOf(first), listOf(second))

        assertTrue(warnings.any { it is ShiftPlanningWarning.SameDate })
        assertTrue(warnings.any { it is ShiftPlanningWarning.Overlap })
    }

    @Test
    fun restBelowTwelveHoursWarnsAndExactlyTwelveDoesNot() {
        val first = newShift(DATE, schedule(start = LocalTime.of(8, 0), end = LocalTime.of(16, 0)))
        val tooSoonStart = first.endAt.plus(Duration.ofHours(11)).plus(Duration.ofMinutes(59))
        val tooSoon = shiftAt(NEW_SHIFT_ID, tooSoonStart, tooSoonStart.plus(Duration.ofHours(4)))
        val exactStart = first.endAt.plus(Duration.ofHours(12))
        val exact = shiftAt(SECOND_NEW_SHIFT_ID, exactStart, exactStart.plus(Duration.ofHours(4)))

        assertTrue(evaluateShiftWarnings(listOf(first), listOf(tooSoon)).any {
            it is ShiftPlanningWarning.ShortRest
        })
        assertFalse(evaluateShiftWarnings(listOf(first), listOf(exact)).any {
            it is ShiftPlanningWarning.ShortRest
        })
    }

    @Test
    fun cancelledAndAbsentShiftsDoNotCauseRestWarnings() {
        val base = newShift(DATE, schedule(start = LocalTime.of(8, 0), end = LocalTime.of(16, 0)))
        val next = newShift(
            DATE,
            schedule(id = OTHER_SCHEDULE_ID, start = LocalTime.of(17, 0), end = LocalTime.of(20, 0)),
            id = NEW_SHIFT_ID,
        )

        assertTrue(evaluateShiftWarnings(listOf(base.copy(status = ShiftStatus.CANCELLED)), listOf(next)).isEmpty())
        assertTrue(evaluateShiftWarnings(listOf(base.copy(status = ShiftStatus.ABSENT)), listOf(next)).isEmpty())
    }

    @Test
    fun candidatesAreComparedWithEachOther() {
        val first = newShift(DATE, schedule(start = LocalTime.of(8, 0), end = LocalTime.of(16, 0)))
        val second = newShift(
            DATE.plusDays(1),
            schedule(id = OTHER_SCHEDULE_ID, start = LocalTime.of(1, 0), end = LocalTime.of(9, 0)),
            id = NEW_SHIFT_ID,
        )

        assertTrue(evaluateShiftWarnings(emptyList(), listOf(first, second)).any {
            it is ShiftPlanningWarning.ShortRest
        })
    }

    @Test
    fun editingPreservesIdentityCreationAndStatus() {
        val original = oldShift(DATE).copy(status = ShiftStatus.ABSENT)
        val edited = editShift(
            original = original,
            date = DATE.plusDays(1),
            objective = objective(),
            combination = schedule(),
            position = "Portón",
            updatedAt = NOW,
        )

        assertEquals(original.id, edited.id)
        assertEquals(original.createdAt, edited.createdAt)
        assertEquals(ShiftStatus.ABSENT, edited.status)
        assertEquals(NOW, edited.updatedAt)
    }

    @Test
    fun colorSimilarityWarnsButReturnsOnlyInformation() {
        assertTrue(areColorsTooSimilar(0xFF112233.toInt(), 0xFF122334.toInt()))
        assertFalse(areColorsTooSimilar(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
    }

    private fun newShift(
        date: LocalDate,
        combination: ScheduleCombination,
        id: UUID = SHIFT_ID,
    ): Shift = buildShift(
        id = id,
        date = date,
        objective = objective(),
        combination = combination,
        position = null,
        timestamp = CREATED_AT,
        zoneId = AppDefaults.zoneId(),
    )

    private fun oldShift(date: LocalDate, id: UUID = SHIFT_ID): Shift =
        newShift(date, schedule(), id).copy(createdAt = CREATED_AT.minusSeconds(60))

    private fun shiftAt(id: UUID, start: Instant, end: Instant): Shift = oldShift(DATE, id).copy(
        startAt = start,
        endAt = end,
        localStartDate = start.atZone(AppDefaults.zoneId()).toLocalDate(),
    )

    private fun objective() = Objective(
        id = OBJECTIVE_ID,
        fullName = "Depósito",
        abbreviation = "DEP",
        address = "Calle 1",
        note = null,
        isActive = true,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
    )

    private fun schedule(
        id: UUID = SCHEDULE_ID,
        start: LocalTime = LocalTime.of(8, 0),
        end: LocalTime = LocalTime.of(16, 0),
    ) = ScheduleCombination(
        id = id,
        objectiveId = OBJECTIVE_ID,
        startTime = start,
        endTime = end,
        colorArgb = 0xFF336699.toInt(),
        isActive = true,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
    )

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 13)
        val CREATED_AT: Instant = Instant.parse("2026-08-13T10:00:00Z")
        val NOW: Instant = Instant.parse("2026-08-13T20:00:00Z")
        val OBJECTIVE_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000001")
        val SCHEDULE_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000002")
        val OTHER_SCHEDULE_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000003")
        val SHIFT_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000004")
        val OTHER_SHIFT_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000005")
        val NEW_SHIFT_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000006")
        val SECOND_NEW_SHIFT_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000007")
    }
}
