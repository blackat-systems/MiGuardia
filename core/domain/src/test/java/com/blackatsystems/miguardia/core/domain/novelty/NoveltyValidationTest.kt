package com.blackatsystems.miguardia.core.domain.novelty

import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.hours.calculateMonthlyHours
import com.blackatsystems.miguardia.core.domain.model.FormalShiftChange
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNovelty
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyType
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.toOperationalSnapshot
import com.blackatsystems.miguardia.core.domain.repository.EmptyShiftNoteException
import com.blackatsystems.miguardia.core.domain.repository.MissingNoveltyDescriptionException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoveltyValidationTest {
    @Test
    fun holidayNameIsNormalizedAndDoesNotOccupyCalendarDay() {
        val holiday = Holiday(id(1), LocalDate.of(2026, 8, 17), "   ", NOW, NOW).normalized()
        val day = projectCalendarMonth(
            YearMonth.of(2026, 8),
            emptyList(),
            emptyList(),
            emptyList(),
            NOW,
            listOf(holiday),
        ).first { it.date == holiday.date }

        assertNull(holiday.name)
        assertEquals(holiday, day.holiday)
        assertTrue(day.isImplicitlyUndefined)
    }

    @Test(expected = EmptyShiftNoteException::class)
    fun blankNoteIsRejected() {
        ShiftNote(id(2), id(1), "  \n ", NOW, NOW).normalized()
    }

    @Test(expected = MissingNoveltyDescriptionException::class)
    fun otherNoveltyRequiresDescription() {
        ShiftNovelty(id(3), id(1), ShiftNoveltyType.OTHER, " ", null, NOW, NOW).normalized()
    }

    @Test
    fun informativeNoveltiesDoNotChangeHours() {
        val shift = shift()
        listOf(ShiftNoveltyType.ADDITIONAL_TIME, ShiftNoveltyType.EARLY_DEPARTURE).forEach { type ->
            ShiftNovelty(id(type.ordinal + 10), shift.id, type, "Dato manual", null, NOW, NOW).normalized()
        }
        val summary = calculateMonthlyHours(
            YearMonth.of(2026, 8), listOf(shift), emptyList(), emptyList(), shift.endAt, emptySet(),
        )
        assertEquals(12, summary.worked.toHours())
        assertEquals(12, summary.planned.toHours())
    }

    @Test
    fun formalCorrectionCanPreserveFirstOriginalSnapshot() {
        val original = shift()
        val firstFinal = original.copy(objectiveNameSnapshot = "Objetivo Dos")
        val first = FormalShiftChange(
            id(20), original.id, false, true, null,
            original.toOperationalSnapshot(), firstFinal.toOperationalSnapshot(), NOW, NOW,
        ).normalized()
        val secondFinal = firstFinal.copy(endAt = firstFinal.endAt.plusSeconds(3600))
        val corrected = first.copy(
            scheduleChanged = true,
            final = secondFinal.toOperationalSnapshot(),
            updatedAt = NOW.plusSeconds(1),
        ).normalized()

        assertEquals(original.toOperationalSnapshot(), corrected.original)
        assertEquals(secondFinal.toOperationalSnapshot(), corrected.final)
    }

    @Test
    fun holidayAfterMonthEndClassifiesCrossingShift() {
        val shift = shift(
            date = LocalDate.of(2026, 8, 31),
            start = LocalTime.of(19, 0),
            endDate = LocalDate.of(2026, 9, 1),
            end = LocalTime.of(7, 0),
        )
        val summary = calculateMonthlyHours(
            YearMonth.of(2026, 8), listOf(shift), emptyList(), emptyList(), shift.endAt,
            setOf(LocalDate.of(2026, 9, 1)),
        )
        assertEquals(7, summary.holidayWorked.toHours())
        assertEquals(12, summary.worked.toHours())
    }

    private fun shift(
        date: LocalDate = LocalDate.of(2026, 8, 13),
        start: LocalTime = LocalTime.of(19, 0),
        endDate: LocalDate = date.plusDays(1),
        end: LocalTime = LocalTime.of(7, 0),
    ): Shift = Shift(
        id = id(1),
        startAt = date.atTime(start).atZone(ZONE).toInstant(),
        endAt = endDate.atTime(end).atZone(ZONE).toInstant(),
        zoneId = ZONE,
        localStartDate = date,
        objectiveNameSnapshot = "Objetivo Ficticio",
        objectiveAbbreviationSnapshot = "OBJ",
        objectiveAddressSnapshot = null,
        startTimeSnapshot = start,
        endTimeSnapshot = end,
        colorArgbSnapshot = 0xFF123456.toInt(),
        position = null,
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = null,
        sourceScheduleCombinationId = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun id(number: Int): UUID = UUID(0, number.toLong())

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-13T12:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
    }
}
