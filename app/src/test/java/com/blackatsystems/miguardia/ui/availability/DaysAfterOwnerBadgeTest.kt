package com.blackatsystems.miguardia.ui.availability

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DaysAfterOwnerBadgeTest {
    private val owner = LocalDate.of(2026, 8, 27)

    @Test
    fun sameDayShowsNothing() = assertNull(daysAfterOwnerBadge(owner, "2026-08-27"))

    @Test
    fun crossingMidnightShowsOneDay() = assertEquals("+1 día", daysAfterOwnerBadge(owner, "2026-08-28"))

    @Test
    fun longerWindowsShowTheExactCount() = assertEquals("+3 días", daysAfterOwnerBadge(owner, "2026-08-30"))

    @Test
    fun crossingTheYearCountsCalendarDays() =
        assertEquals("+1 día", daysAfterOwnerBadge(LocalDate.of(2026, 12, 31), "2027-01-01"))

    @Test
    fun incompleteOrEarlierDatesShowNothing() {
        assertNull(daysAfterOwnerBadge(owner, ""))
        assertNull(daysAfterOwnerBadge(owner, "2026-08"))
        assertNull(daysAfterOwnerBadge(owner, "2026-02-30"))
        assertNull(daysAfterOwnerBadge(owner, "2026-08-26"))
        assertNull(daysAfterOwnerBadge(null, "2026-08-28"))
    }
}
