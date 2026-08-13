package com.blackatsystems.miguardia.ui.summary

import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryTemporalBoundaryTest {
    private val zone = ZoneId.of("America/Argentina/Cordoba")

    @Test fun idleSummarySchedulesTheNextLocalMidnight() {
        val now = Instant.parse("2026-08-13T15:34:20Z")
        assertEquals(
            LocalDate.of(2026, 8, 14).atStartOfDay(zone).toInstant(),
            nextSummaryUpdateInstant(now, zone, emptyList(), emptyList()),
        )
    }

    @Test fun futureShiftStartPrecedesMidnight() {
        val now = Instant.parse("2026-08-13T15:00:00Z")
        val shift = shift(
            start = Instant.parse("2026-08-13T16:00:00Z"),
            end = Instant.parse("2026-08-13T20:00:00Z"),
        )
        assertEquals(shift.startAt, nextSummaryUpdateInstant(now, zone, listOf(shift), emptyList()))
    }

    @Test fun activeShiftSchedulesTheNextMinute() {
        val now = Instant.parse("2026-08-13T15:34:20Z")
        val shift = shift(
            start = Instant.parse("2026-08-13T15:00:00Z"),
            end = Instant.parse("2026-08-13T20:00:00Z"),
        )
        assertEquals(
            Instant.parse("2026-08-13T15:35:00Z"),
            nextSummaryUpdateInstant(now, zone, listOf(shift), emptyList()),
        )
    }

    @Test fun explicitStatusAndMedicalLeaveDoNotCreateActivePolling() {
        val now = Instant.parse("2026-08-13T15:34:20Z")
        val absent = shift(
            start = Instant.parse("2026-08-13T15:00:00Z"),
            end = Instant.parse("2026-08-13T20:00:00Z"),
        ).copy(status = ShiftStatus.ABSENT)
        val medical = shift(
            start = Instant.parse("2026-08-13T15:00:00Z"),
            end = Instant.parse("2026-08-13T20:00:00Z"),
        ).copy(id = UUID.fromString("50000000-0000-0000-0000-000000000002"))
        val leave = MedicalLeave(
            id = UUID.fromString("50000000-0000-0000-0000-000000000003"),
            startDate = medical.localStartDate,
            endDateInclusive = medical.localStartDate,
            privateNote = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val midnight = LocalDate.of(2026, 8, 14).atStartOfDay(zone).toInstant()
        assertEquals(midnight, nextSummaryUpdateInstant(now, zone, listOf(absent, medical), listOf(leave)))
    }

    private fun shift(start: Instant, end: Instant) = Shift(
        id = UUID.fromString("50000000-0000-0000-0000-000000000001"),
        startAt = start,
        endAt = end,
        zoneId = zone,
        localStartDate = start.atZone(zone).toLocalDate(),
        objectiveNameSnapshot = "Objetivo ficticio",
        objectiveAbbreviationSnapshot = "PRB",
        objectiveAddressSnapshot = null,
        startTimeSnapshot = LocalTime.of(12, 0),
        endTimeSnapshot = LocalTime.of(17, 0),
        colorArgbSnapshot = 0xFF336699.toInt(),
        position = null,
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = null,
        sourceScheduleCombinationId = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
