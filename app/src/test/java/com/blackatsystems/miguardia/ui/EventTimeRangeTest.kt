package com.blackatsystems.miguardia.ui

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class EventTimeRangeTest {
    @Test
    fun sameDayMinutesStayCompactWhileDetailShowsBothCompleteEndpoints() {
        val start = local("2026-10-23T08:15")
        val end = local("2026-10-23T16:45")
        assertEquals("08:15–16:45", formatEventTimeRange(start, end, ZONE, EventTimeRangeStyle.COMPACT))
        assertEquals("23/10/2026 · 08:15–16:45", formatEventTimeRange(start, end, ZONE, EventTimeRangeStyle.COMPACT_DATED))
        assertEquals("23/10/2026 08:15 – 23/10/2026 16:45", formatEventTimeRange(start, end, ZONE, EventTimeRangeStyle.DETAIL))
        assertEquals("08:15–16:45", formatEventTimeRange(start, end, ZONE, EventTimeRangeStyle.CALENDAR))
    }

    @Test
    fun overnightTwentyFourTwentyFiveAndSeveralDaysUseCivilDayOffsets() {
        val cases = listOf(
            Triple("2026-10-23T19:00", "2026-10-24T07:00", "19:00–07:00 (+1 día)"),
            Triple("2026-10-23T08:00", "2026-10-24T08:00", "08:00–08:00 (+1 día)"),
            Triple("2026-10-23T08:00", "2026-10-24T09:00", "08:00–09:00 (+1 día)"),
            Triple("2026-10-23T08:00", "2026-10-26T09:00", "08:00–09:00 (+3 días)"),
        )
        cases.forEach { (start, end, expected) ->
            assertEquals(expected, formatEventTimeRange(local(start), local(end), ZONE, EventTimeRangeStyle.COMPACT))
            assertEquals("23/10/2026 · $expected", formatEventTimeRange(local(start), local(end), ZONE, EventTimeRangeStyle.COMPACT_DATED))
        }
    }

    @Test
    fun monthYearAndLeapDayCrossingsShowBothCompleteEndpointsInSummaries() {
        val cases = listOf(
            Triple("2026-10-31T22:00", "2026-11-01T06:00", "31/10/2026 22:00 – 01/11/2026 06:00"),
            Triple("2026-12-31T22:00", "2027-01-01T06:00", "31/12/2026 22:00 – 01/01/2027 06:00"),
            Triple("2028-02-29T22:30", "2028-03-01T06:15", "29/02/2028 22:30 – 01/03/2028 06:15"),
        )
        cases.forEach { (start, end, expected) ->
            listOf(EventTimeRangeStyle.COMPACT, EventTimeRangeStyle.COMPACT_DATED, EventTimeRangeStyle.DETAIL).forEach { style ->
                assertEquals("$style $start", expected, formatEventTimeRange(local(start), local(end), ZONE, style))
            }
        }
    }

    @Test
    fun monthlyCellKeepsWholeHoursAndExactlyOneCompactOffsetAcrossMonthBoundary() {
        assertEquals("08–16", formatEventTimeRange(local("2026-10-23T08:00"), local("2026-10-23T16:00"), ZONE, EventTimeRangeStyle.CALENDAR))
        assertEquals("22:30–06:15 +1", formatEventTimeRange(local("2028-02-29T22:30"), local("2028-03-01T06:15"), ZONE, EventTimeRangeStyle.CALENDAR))
        assertEquals("08:00–16:00 +2", formatEventTimeRange(local("2026-10-23T08:00"), local("2026-10-25T16:00"), ZONE, EventTimeRangeStyle.CALENDAR))
    }

    @Test
    fun springClockJumpStillShowsOneCivilDayEvenWhenLessThanTwentyFourHoursElapse() {
        val zone = ZoneId.of("Europe/Madrid")
        val start = local("2026-03-28T22:00", zone)
        val end = local("2026-03-29T06:00", zone)
        assertEquals(7L, Duration.between(start, end).toHours())
        assertEquals("22:00–06:00 (+1 día)", formatEventTimeRange(start, end, zone, EventTimeRangeStyle.COMPACT))
        assertEquals("22:00–06:00 +1", formatEventTimeRange(start, end, zone, EventTimeRangeStyle.CALENDAR))
    }

    @Test
    fun repeatedClockHourDoesNotInventANextDayWhenTwentyFourHoursElapseOnOneCivilDate() {
        val zone = ZoneId.of("Europe/Madrid")
        val start = local("2026-10-25T00:30", zone)
        val end = local("2026-10-25T23:30", zone)
        assertEquals(24L, Duration.between(start, end).toHours())
        assertEquals("00:30–23:30", formatEventTimeRange(start, end, zone, EventTimeRangeStyle.COMPACT))
        assertEquals("25/10/2026 00:30 – 25/10/2026 23:30", formatEventTimeRange(start, end, zone, EventTimeRangeStyle.DETAIL))
    }

    @Test
    fun savedEventZoneDeterminesBothCivilDatesWithoutReadingTheDeviceZone() {
        val start = java.time.Instant.parse("2027-01-01T01:00:00Z")
        val end = java.time.Instant.parse("2027-01-01T04:00:00Z")
        assertEquals("31/12/2026 22:00 – 01/01/2027 01:00", formatEventTimeRange(start, end, ZONE, EventTimeRangeStyle.COMPACT_DATED))
        assertEquals("01/01/2027 · 01:00–04:00", formatEventTimeRange(start, end, ZoneId.of("UTC"), EventTimeRangeStyle.COMPACT_DATED))
    }

    private fun local(value: String, zone: ZoneId = ZONE) = LocalDateTime.parse(value).atZone(zone).toInstant()

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
    }
}
