package com.blackatsystems.miguardia.core.domain.shift

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftPlanningTest {
    @Test
    fun multipleSelectionRejectsEmptyAndDifferentMonths() {
        assertThrows(InvalidLocalDataException::class.java) { validateSingleMonth(emptySet()) }
        assertThrows(InvalidLocalDataException::class.java) {
            validateSingleMonth(setOf(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1)))
        }
    }

    @Test
    fun sameDateAndOverlapProduceWarnings() {
        val first = shift(SHIFT_ID, DATE, 8, 16)
        val second = shift(OTHER_SHIFT_ID, DATE, 12, 20)

        val warnings = evaluateShiftWarnings(listOf(first), listOf(second))

        assertTrue(warnings.any { it is ShiftPlanningWarning.SameDate })
        assertTrue(warnings.any { it is ShiftPlanningWarning.Overlap })
    }

    @Test
    fun restBelowTwelveHoursWarnsAndExactlyTwelveDoesNot() {
        val first = shift(SHIFT_ID, DATE, 8, 16)
        val tooSoonStart = first.endAt.plus(Duration.ofHours(11)).plus(Duration.ofMinutes(59))
        val tooSoon = shiftAt(OTHER_SHIFT_ID, tooSoonStart, tooSoonStart.plus(Duration.ofHours(4)))
        val exactStart = first.endAt.plus(Duration.ofHours(12))
        val exact = shiftAt(THIRD_SHIFT_ID, exactStart, exactStart.plus(Duration.ofHours(4)))

        assertTrue(evaluateShiftWarnings(listOf(first), listOf(tooSoon)).any {
            it is ShiftPlanningWarning.ShortRest
        })
        assertFalse(evaluateShiftWarnings(listOf(first), listOf(exact)).any {
            it is ShiftPlanningWarning.ShortRest
        })
    }

    @Test
    fun cancelledAndAbsentShiftsDoNotCauseRestWarnings() {
        val first = shift(SHIFT_ID, DATE, 8, 16)
        val next = shift(OTHER_SHIFT_ID, DATE, 17, 20)

        assertTrue(evaluateShiftWarnings(listOf(first.copy(status = ShiftStatus.CANCELLED)), listOf(next)).isEmpty())
        assertTrue(evaluateShiftWarnings(listOf(first.copy(status = ShiftStatus.ABSENT)), listOf(next)).isEmpty())
    }

    @Test
    fun candidateShiftsAreComparedWithEachOther() {
        val first = shift(SHIFT_ID, DATE, 8, 16)
        val second = shift(OTHER_SHIFT_ID, DATE.plusDays(1), 1, 9)

        assertTrue(evaluateShiftWarnings(emptyList(), listOf(first, second)).any {
            it is ShiftPlanningWarning.ShortRest
        })
    }

    @Test
    fun colorSimilarityIsOnlyInformational() {
        assertTrue(areColorsTooSimilar(0xFF112233.toInt(), 0xFF122334.toInt()))
        assertFalse(areColorsTooSimilar(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
    }

    private fun shift(id: UUID, date: LocalDate, startHour: Int, endHour: Int): Shift {
        val start = date.atTime(startHour, 0).atZone(ZONE).toInstant()
        val endDate = if (endHour > startHour) date else date.plusDays(1)
        val end = endDate.atTime(endHour, 0).atZone(ZONE).toInstant()
        return shiftAt(id, start, end)
    }

    private fun shiftAt(id: UUID, start: Instant, end: Instant) = Shift(
        id = id,
        startAt = start,
        endAt = end,
        zoneId = ZONE,
        localStartDate = start.atZone(ZONE).toLocalDate(),
        objectiveNameSnapshot = "Hospital",
        objectiveAbbreviationSnapshot = "HOS",
        objectiveAddressSnapshot = null,
        startTimeSnapshot = LocalTime.ofInstant(start, ZONE),
        endTimeSnapshot = LocalTime.ofInstant(end, ZONE),
        colorArgbSnapshot = 0xFF336699.toInt(),
        position = null,
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = OBJECTIVE_ID,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 13)
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val OBJECTIVE_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000001")
        val SHIFT_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000002")
        val OTHER_SHIFT_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000003")
        val THIRD_SHIFT_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000004")
    }
}
