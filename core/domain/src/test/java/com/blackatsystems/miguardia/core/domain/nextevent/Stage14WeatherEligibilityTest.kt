package com.blackatsystems.miguardia.core.domain.nextevent

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stage 14 · Clima: the "shift already ended" branch of the weather precondition. Synthetic data. */
class Stage14WeatherEligibilityTest {
    private val planned = testWrite(1401, "2027-01-07T10:00:00Z", "2027-01-07T14:00:00Z").shift

    // A242
    @Test
    fun `a planned shift that has not ended yet is eligible before and during its hours`() {
        assertTrue(planned.isEligibleForWeather(Instant.parse("2027-01-06T10:00:00Z"), emptyList()))
        assertTrue(planned.isEligibleForWeather(planned.startAt, emptyList()))
        assertTrue(planned.isEligibleForWeather(planned.endAt.minusSeconds(1), emptyList()))
    }

    // A242
    @Test
    fun `a shift is no longer eligible from the exact instant it ends`() {
        assertFalse(planned.isEligibleForWeather(planned.endAt, emptyList()))
        assertFalse(planned.isEligibleForWeather(planned.endAt.plusSeconds(1), emptyList()))
        assertFalse(planned.isEligibleForWeather(Instant.parse("2027-02-01T00:00:00Z"), emptyList()))
    }

    // A242
    @Test
    fun `an ended shift stays ineligible even without vacations or medical leave`() {
        val afterEnd = planned.endAt.plusSeconds(3_600)
        val unrelatedVacation = testVacation(1402, "2027-03-01", "2027-03-10")
        val unrelatedLeave = testMedicalLeave(1403, "2027-03-01", "2027-03-10")

        assertFalse(planned.isEligibleForWeather(afterEnd, emptyList(), emptyList()))
        assertFalse(planned.isEligibleForWeather(afterEnd, listOf(unrelatedVacation), listOf(unrelatedLeave)))
    }

    // A242
    @Test
    fun `a vacation covering the local start date excludes a future shift and another range does not`() {
        val before = Instant.parse("2027-01-07T08:00:00Z")
        val covering = testVacation(1404, "2027-01-07", "2027-01-07")
        val dayAfter = testVacation(1405, "2027-01-08", "2027-01-12")

        assertFalse(planned.isEligibleForWeather(before, listOf(covering)))
        assertTrue(planned.isEligibleForWeather(before, listOf(dayAfter)))
    }
}
