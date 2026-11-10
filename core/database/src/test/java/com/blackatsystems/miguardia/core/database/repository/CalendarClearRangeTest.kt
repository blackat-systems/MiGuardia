package com.blackatsystems.miguardia.core.database.repository

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarClearRangeTest {
    private val first = LocalDate.of(2026, 9, 1)
    private fun remaining(vararg days: Int) = remainingCalendarRanges(first.toString(), first.plusDays(6).toString(), days.map { first.plusDays(it.toLong()) }.toSet())

    @Test fun middleDaysPreserveBothSides() {
        assertEquals(listOf(first to first.plusDays(1), first.plusDays(4) to first.plusDays(6)), remaining(2, 3))
    }
    @Test fun disjointSelectionPreservesAllGaps() {
        assertEquals(listOf(first.plusDays(1) to first.plusDays(1), first.plusDays(3) to first.plusDays(4)), remaining(0, 2, 5, 6))
    }
    @Test fun selectingWholeRangeLeavesNoAttribution() {
        assertEquals(emptyList<Pair<LocalDate, LocalDate>>(), remaining(0, 1, 2, 3, 4, 5, 6))
    }
    @Test fun unrelatedDatesDoNotTrimRange() {
        assertEquals(listOf(first to first.plusDays(6)), remaining(-1, 7))
    }
}
