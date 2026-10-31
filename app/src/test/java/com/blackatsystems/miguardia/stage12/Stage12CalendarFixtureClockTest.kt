package com.blackatsystems.miguardia.stage12

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class Stage12CalendarFixtureClockTest {
    @Test fun fixedCalendarFixtureKeepsTheContractDateAndRealModeDoesNotOverride() {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        assertNull(Stage12CalendarFixtureClock.forMode("REAL", zone))
        val clock = requireNotNull(Stage12CalendarFixtureClock.forMode("STAGE12_20261023", zone))
        assertEquals(Instant.parse("2026-10-23T12:00:00Z"), clock.instant())
        assertEquals(LocalDate.of(2026, 10, 23), LocalDate.now(clock))
        assertEquals(zone, clock.zone)
    }

    @Test fun arbitraryClockModesCannotBecomeFixtures() {
        listOf("", "normal", "2026-10-24", "STAGE12_20261024").forEach {
            assertThrows(IllegalArgumentException::class.java) {
                Stage12CalendarFixtureClock.forMode(it, ZoneId.of("UTC"))
            }
        }
    }
}
