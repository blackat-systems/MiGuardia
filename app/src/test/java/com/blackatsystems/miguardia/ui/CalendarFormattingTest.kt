package com.blackatsystems.miguardia.ui

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarFormattingTest {
    @Test fun compactHoursKeepMinutesAndTheActualEndDay() {
        assertEquals("08–16", shift("08:00", "16:00").calendarTimeRange())
        assertEquals("08:30–16:00", shift("08:30", "16:00").calendarTimeRange())
        assertEquals("08:00–16:15", shift("08:00", "16:15").calendarTimeRange())
        assertEquals("22:00–06:00 +1", shift("22:00", "06:00", 1).calendarTimeRange())
        assertEquals("22:30–06:15 +1", shift("22:30", "06:15", 1).calendarTimeRange())
        assertEquals("08:00–08:00 +1", shift("08:00", "08:00", 1).calendarTimeRange())
        assertEquals("08:00–16:00 +2", shift("08:00", "16:00", 2).calendarTimeRange())
    }

    @Test fun detailKeepsTheFullTimeAndArgentineDatesAcrossMonthBoundary() {
        assertEquals("08:00–16:00", shift("08:00", "16:00").timeRange())
        assertEquals("29/02/2024 22:30 – 01/03/2024 06:15", shift("22:30", "06:15", 1).timeRange())
        assertEquals("29/02/2024", LocalDate.of(2024, 2, 29).shortDisplayDate())
        assertEquals("Viernes 1 de Marzo del 2024", LocalDate.of(2024, 3, 1).dayEditorDisplayName())
    }

    private fun shift(start: String, end: String, endDayOffset: Long = 0): Shift {
        val date = LocalDate.of(2024, 2, 29)
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val from = LocalTime.parse(start); val to = LocalTime.parse(end)
        val instant = date.atTime(from).atZone(zone).toInstant()
        return Shift(UUID.randomUUID(), instant, date.plusDays(endDayOffset).atTime(to).atZone(zone).toInstant(),
            zone, date, "Institución ficticia", "FI", null, from, to, 0xFF123456.toInt(), null,
            ShiftStatus.PLANNED, UUID.randomUUID(), instant, instant)
    }
}
