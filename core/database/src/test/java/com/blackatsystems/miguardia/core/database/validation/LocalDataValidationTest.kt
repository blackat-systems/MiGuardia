package com.blackatsystems.miguardia.core.database.validation

import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalDataValidationTest {
    @Test
    fun objectiveAndSnapshotAbbreviationsAreNormalized() {
        val validated = validShift().copy(objectiveAbbreviationSnapshot = "  dep ").validated()

        assertEquals("DEP", validated.objectiveAbbreviationSnapshot)
    }

    @Test
    fun shiftEndMustBeStrictlyAfterStart() {
        val shift = validShift().copy(endAt = validShift().startAt)

        assertThrows(InvalidLocalDataException::class.java) { shift.validated() }
    }

    @Test
    fun shiftLocalStartDateMustMatchItsInstantAndZone() {
        val shift = validShift().copy(localStartDate = LocalDate.of(2026, 8, 21))

        assertThrows(InvalidLocalDataException::class.java) { shift.validated() }
    }

    @Test
    fun medicalLeaveEndCannotPrecedeStart() {
        val leave = MedicalLeave(
            id = UUID.fromString("00000000-0000-0000-0000-000000000091"),
            startDate = LocalDate.of(2026, 9, 2),
            endDateInclusive = LocalDate.of(2026, 9, 1),
            privateNote = null,
            createdAt = Instant.parse("2026-08-13T12:00:00Z"),
            updatedAt = Instant.parse("2026-08-13T12:00:00Z"),
        )

        assertThrows(InvalidLocalDataException::class.java) { leave.validated() }
    }

    @Test
    fun updateTimestampCannotPrecedeCreation() {
        val shift = validShift().copy(
            updatedAt = Instant.parse("2026-08-13T11:59:59Z"),
        )

        assertThrows(InvalidLocalDataException::class.java) { shift.validated() }
    }

    private fun validShift(): Shift {
        val zone = ZoneId.of("America/Argentina/Cordoba")
        val start = LocalDate.of(2026, 8, 20).atTime(19, 0).atZone(zone).toInstant()
        val end = LocalDate.of(2026, 8, 21).atTime(7, 0).atZone(zone).toInstant()
        return Shift(
            id = UUID.fromString("00000000-0000-0000-0000-000000000090"),
            startAt = start,
            endAt = end,
            zoneId = zone,
            localStartDate = LocalDate.of(2026, 8, 20),
            objectiveNameSnapshot = "Depósito Norte",
            objectiveAbbreviationSnapshot = "DEP",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(19, 0),
            endTimeSnapshot = LocalTime.of(7, 0),
            colorArgbSnapshot = 0xFF123456.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = null,
            sourceScheduleCombinationId = null,
            createdAt = Instant.parse("2026-08-13T12:00:00Z"),
            updatedAt = Instant.parse("2026-08-13T12:00:00Z"),
        )
    }
}
