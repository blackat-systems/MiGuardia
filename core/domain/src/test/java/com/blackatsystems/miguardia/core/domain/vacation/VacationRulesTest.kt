package com.blackatsystems.miguardia.core.domain.vacation

import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Vacation
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VacationRulesTest {
    @Test fun singleDayRangeIsInclusive() {
        val date = LocalDate.of(2026, 8, 13)
        assertEquals(setOf(date), vacationDatesInMonth(YearMonth.from(date), listOf(vacation(date, date))))
    }

    @Test fun contiguousRangesDoNotOverlap() {
        val first = vacation(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5))
        val second = vacation(LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 10))
        assertFalse(first.overlaps(second))
    }

    @Test fun sharedBoundaryOverlaps() {
        val first = vacation(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5))
        val second = vacation(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 10))
        assertTrue(first.overlaps(second))
    }

    @Test fun medicalLeaveIntersectionIsInclusive() {
        val vacation = vacation(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5))
        val leave = MedicalLeave(
            id = UUID.fromString("10000000-0000-0000-0000-000000000001"),
            startDate = LocalDate.of(2026, 8, 5),
            endDateInclusive = LocalDate.of(2026, 8, 7),
            privateNote = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        assertTrue(vacation.overlaps(leave))
    }

    @Test fun invalidRangeIsRejectedDeterministically() {
        assertThrows(IllegalArgumentException::class.java) {
            vacationDatesInMonth(
                YearMonth.of(2026, 8),
                listOf(vacation(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1))),
            )
        }
    }

    @Test fun yearSpanningRangeIsClippedToEachMonth() {
        val vacation = vacation(
            LocalDate.of(2026, 12, 30),
            LocalDate.of(2027, 1, 3),
        )

        assertEquals(
            setOf(LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 31)),
            vacationDatesInMonth(YearMonth.of(2026, 12), listOf(vacation)),
        )
        assertEquals(
            setOf(
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 2),
                LocalDate.of(2027, 1, 3),
            ),
            vacationDatesInMonth(YearMonth.of(2027, 1), listOf(vacation)),
        )
    }

    private fun vacation(start: LocalDate, end: LocalDate) = Vacation(
        id = UUID.nameUUIDFromBytes("$start-$end".toByteArray()),
        startDate = start,
        endDateInclusive = end,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
