package com.blackatsystems.miguardia.stage12

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Only the Calendar VM receives this value. Android animation/system clocks are untouched. */
internal object Stage12CalendarFixtureClock {
    fun forMode(mode: String, zone: ZoneId): Clock? = when (mode) {
        "REAL" -> null
        "STAGE12_20261023" -> Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), zone)
        else -> throw IllegalArgumentException("Unknown Stage12 calendar fixture")
    }
}
