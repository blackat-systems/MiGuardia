package com.blackatsystems.miguardia.ui.nextevent

import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEventFormattingTest {
    @Test
    fun remainingUsesHumanDaysHoursAndMinutesWithoutSeconds() {
        assertEquals("1 d 4 h 30 min", formatRemaining(Duration.ofMinutes(1_710)))
        assertEquals("5 h 20 min", formatRemaining(Duration.ofMinutes(320)))
        assertEquals("0 min", formatRemaining(Duration.ofSeconds(59)))
        assertEquals("0 min", formatRemaining(Duration.ofMinutes(-1)))
    }

    @Test
    fun dayOffUsesCivilDateLabels() {
        val today = LocalDate.of(2026, 12, 31)
        assertEquals("Hoy", dayDistanceLabel(today, today))
        assertEquals("Mañana", dayDistanceLabel(today, LocalDate.of(2027, 1, 1)))
        assertEquals("En 3 días", dayDistanceLabel(today, LocalDate.of(2027, 1, 3)))
    }

    @Test
    fun activeEventRefreshesAtMinuteBoundaryWithoutPerSecondPolling() {
        val now = Instant.parse("2026-08-15T15:00:42Z")
        val result = NextEventResult(
            referenceInstant = now,
            ongoingShifts = emptyList(),
            upcomingShifts = emptyList(),
            nextDayOff = null,
            primaryEvent = NextEventPrimary.UPCOMING_SHIFT,
            remaining = Duration.ofHours(1),
        )

        val delay = nextRefreshDelay(
            now = now,
            zoneId = AppDefaults.zoneId(),
            result = result,
        )

        assertEquals(Duration.ofSeconds(18), delay)
        assertTrue(delay > Duration.ofSeconds(1))
    }
}
