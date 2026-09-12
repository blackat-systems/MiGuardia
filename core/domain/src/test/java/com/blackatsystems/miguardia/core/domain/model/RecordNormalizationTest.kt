package com.blackatsystems.miguardia.core.domain.model

import com.blackatsystems.miguardia.core.domain.repository.EmptyShiftNoteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordNormalizationTest {
    @Test
    fun holidayNameIsTrimmedWithoutChangingItsIdentityOrTimestamps() {
        val holiday = Holiday(
            id = HOLIDAY_ID,
            date = DATE,
            name = "  Día local  ",
            createdAt = CREATED_AT,
            updatedAt = UPDATED_AT,
        )

        assertEquals(
            holiday.copy(name = "Día local"),
            holiday.normalized(),
        )
    }

    @Test
    fun blankHolidayNameBecomesNullAndNullRemainsNull() {
        assertNull(holiday(name = " \t ").normalized().name)
        assertNull(holiday(name = null).normalized().name)
    }

    @Test
    fun shiftNoteBodyIsTrimmedAndValidTimestampsArePreserved() {
        val note = ShiftNote(
            id = NOTE_ID,
            shiftId = SHIFT_ID,
            body = "  Entrega de guardia  ",
            createdAt = CREATED_AT,
            updatedAt = UPDATED_AT,
        )

        assertEquals(
            note.copy(body = "Entrega de guardia"),
            note.normalized(),
        )
    }

    @Test
    fun blankShiftNoteIsRejectedAfterTrimming() {
        assertThrows(EmptyShiftNoteException::class.java) {
            note(body = " \n\t ").normalized()
        }
    }

    @Test
    fun shiftNoteModificationCannotPrecedeCreation() {
        assertThrows(InvalidLocalDataException::class.java) {
            note(
                body = "Nota",
                createdAt = UPDATED_AT,
                updatedAt = CREATED_AT,
            ).normalized()
        }

        assertEquals(
            CREATED_AT,
            note(body = "Nota", createdAt = CREATED_AT, updatedAt = CREATED_AT)
                .normalized()
                .updatedAt,
        )
    }

    private fun holiday(name: String?) = Holiday(
        id = HOLIDAY_ID,
        date = DATE,
        name = name,
        createdAt = CREATED_AT,
        updatedAt = UPDATED_AT,
    )

    private fun note(
        body: String,
        createdAt: Instant = CREATED_AT,
        updatedAt: Instant = UPDATED_AT,
    ) = ShiftNote(
        id = NOTE_ID,
        shiftId = SHIFT_ID,
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 23)
        val CREATED_AT: Instant = Instant.parse("2026-08-23T12:00:00Z")
        val UPDATED_AT: Instant = Instant.parse("2026-08-23T12:01:00Z")
        val HOLIDAY_ID: UUID = UUID.fromString("86000000-0000-0000-0000-000000000001")
        val NOTE_ID: UUID = UUID.fromString("86000000-0000-0000-0000-000000000002")
        val SHIFT_ID: UUID = UUID.fromString("86000000-0000-0000-0000-000000000003")
    }
}
