package com.blackatsystems.miguardia.core.domain.calendar

import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarProjectionTest {
    private val zone: ZoneId = AppDefaults.zoneId()

    @Test
    fun plannedShiftUsesInclusiveStartAndEndBoundaries() {
        val shift = shift(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            localDate = LocalDate.of(2026, 8, 13),
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(7, 0),
        )

        assertEquals(
            ShiftTemporalStatus.UPCOMING,
            shift.temporalStatusAt(shift.startAt.minusNanos(1)),
        )
        assertEquals(ShiftTemporalStatus.IN_PROGRESS, shift.temporalStatusAt(shift.startAt))
        assertEquals(
            ShiftTemporalStatus.IN_PROGRESS,
            shift.temporalStatusAt(shift.endAt.minusNanos(1)),
        )
        assertEquals(ShiftTemporalStatus.COMPLETED, shift.temporalStatusAt(shift.endAt))
    }

    @Test
    fun historicalShiftIsCompletedWithoutChangingPersistedStatus() {
        val shift = shift(
            id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            localDate = LocalDate.of(2026, 7, 10),
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(16, 0),
        )

        assertEquals(
            ShiftTemporalStatus.COMPLETED,
            shift.temporalStatusAt(Instant.parse("2026-08-13T15:00:00Z")),
        )
        assertEquals(ShiftStatus.PLANNED, shift.status)
    }

    @Test
    fun explicitCancellationAndAbsenceOverrideElapsedTime() {
        val endedShift = shift(
            id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
            localDate = LocalDate.of(2026, 8, 1),
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(16, 0),
        )
        val now = Instant.parse("2026-08-13T15:00:00Z")

        assertEquals(
            ShiftTemporalStatus.CANCELLED,
            endedShift.copy(status = ShiftStatus.CANCELLED).temporalStatusAt(now),
        )
        assertEquals(
            ShiftTemporalStatus.ABSENT,
            endedShift.copy(status = ShiftStatus.ABSENT).temporalStatusAt(now),
        )
    }

    @Test
    fun overnightShiftRemainsInProgressAfterCivilMidnight() {
        val shift = shift(
            id = UUID.fromString("00000000-0000-0000-0000-000000000004"),
            localDate = LocalDate.of(2026, 8, 12),
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(7, 0),
        )
        val afterMidnight = ZonedDateTime.of(
            LocalDate.of(2026, 8, 13),
            LocalTime.of(1, 0),
            zone,
        ).toInstant()

        assertEquals(ShiftTemporalStatus.IN_PROGRESS, shift.temporalStatusAt(afterMidnight))
    }

    @Test
    fun monthProjectionKeepsOvernightShiftOnlyOnItsStartDateAndSortsMultipleShifts() {
        val later = shift(
            id = UUID.fromString("00000000-0000-0000-0000-000000000006"),
            localDate = LocalDate.of(2026, 8, 31),
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(7, 0),
        )
        val earlier = shift(
            id = UUID.fromString("00000000-0000-0000-0000-000000000005"),
            localDate = LocalDate.of(2026, 8, 31),
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(12, 0),
        )

        val august = projectCalendarMonth(
            month = YearMonth.of(2026, 8),
            shifts = listOf(later, earlier),
            explicitDayStatuses = emptyList(),
            medicalLeaves = emptyList(),
            now = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val september = projectCalendarMonth(
            month = YearMonth.of(2026, 9),
            shifts = listOf(later, earlier),
            explicitDayStatuses = emptyList(),
            medicalLeaves = emptyList(),
            now = Instant.parse("2026-08-01T00:00:00Z"),
        )

        assertEquals(listOf(earlier.id, later.id), august.last().shifts.map { it.shift.id })
        assertTrue(september.all { it.shifts.isEmpty() })
    }

    @Test
    fun monthProjectionUsesEndThenUuidWhenShiftsStartTogether() {
        val longerWithLowerUuid = shift(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            localDate = LocalDate.of(2026, 8, 31),
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(16, 0),
        )
        val shorterWithHigherUuid = shift(
            id = UUID.fromString("00000000-0000-0000-0000-000000000009"),
            localDate = LocalDate.of(2026, 8, 31),
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(12, 0),
        )

        val august = projectCalendarMonth(
            month = YearMonth.of(2026, 8),
            shifts = listOf(longerWithLowerUuid, shorterWithHigherUuid),
            explicitDayStatuses = emptyList(),
            medicalLeaves = emptyList(),
            now = Instant.parse("2026-08-01T00:00:00Z"),
        )

        assertEquals(
            listOf(shorterWithHigherUuid.id, longerWithLowerUuid.id),
            august.last().shifts.map { it.shift.id },
        )
    }

    @Test
    fun implicitUndefinedDiffersFromExplicitUndefinedAndDayOff() {
        val month = YearMonth.of(2026, 8)
        val days = projectCalendarMonth(
            month = month,
            shifts = emptyList(),
            explicitDayStatuses = listOf(
                ExplicitDayStatus(month.atDay(2), ExplicitDayStatusType.UNDEFINED),
                ExplicitDayStatus(month.atDay(3), ExplicitDayStatusType.DAY_OFF),
            ),
            medicalLeaves = emptyList(),
            now = Instant.EPOCH,
        )

        assertTrue(days[0].isImplicitlyUndefined)
        assertEquals(null, days[0].explicitStatus)
        assertFalse(days[1].isImplicitlyUndefined)
        assertEquals(ExplicitDayStatusType.UNDEFINED, days[1].explicitStatus)
        assertFalse(days[2].isImplicitlyUndefined)
        assertEquals(ExplicitDayStatusType.DAY_OFF, days[2].explicitStatus)
    }

    @Test
    fun medicalLeaveCrossingMonthBoundaryMarksOnlyIntersectingInclusiveDates() {
        val leave = MedicalLeave(
            id = UUID.fromString("00000000-0000-0000-0000-000000000007"),
            startDate = LocalDate.of(2026, 7, 30),
            endDateInclusive = LocalDate.of(2026, 8, 2),
            privateNote = "Nota ficticia",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        val days = projectCalendarMonth(
            month = YearMonth.of(2026, 8),
            shifts = emptyList(),
            explicitDayStatuses = emptyList(),
            medicalLeaves = listOf(leave),
            now = Instant.EPOCH,
        )

        assertTrue(days[0].hasMedicalLeave)
        assertTrue(days[1].hasMedicalLeave)
        assertFalse(days[2].hasMedicalLeave)
    }

    @Test
    fun leapYearAndYearBoundaryProduceExactCalendarDates() {
        val february = projectCalendarMonth(
            month = YearMonth.of(2028, 2),
            shifts = emptyList(),
            explicitDayStatuses = emptyList(),
            medicalLeaves = emptyList(),
            now = Instant.EPOCH,
        )
        val december = projectCalendarMonth(
            month = YearMonth.of(2026, 12),
            shifts = emptyList(),
            explicitDayStatuses = emptyList(),
            medicalLeaves = emptyList(),
            now = Instant.EPOCH,
        )

        assertEquals(29, february.size)
        assertEquals(LocalDate.of(2028, 2, 29), february.last().date)
        assertEquals(LocalDate.of(2026, 12, 31), december.last().date)
        assertEquals(YearMonth.of(2027, 1), YearMonth.from(december.last().date).plusMonths(1))
    }

    @Test
    fun shiftConstructionUsesCordobaZoneIndependentFromSystemDefault() {
        val shift = shift(
            id = UUID.fromString("00000000-0000-0000-0000-000000000008"),
            localDate = LocalDate.of(2026, 8, 13),
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(7, 0),
        )

        assertEquals(ZoneId.of("America/Argentina/Cordoba"), shift.zoneId)
        assertEquals(Instant.parse("2026-08-13T22:00:00Z"), shift.startAt)
        assertEquals(Instant.parse("2026-08-14T10:00:00Z"), shift.endAt)
    }

    @Test
    fun vacationCrossingMonthIsProjectedWithoutHidingImplicitUndefinedOrShift() {
        val month = YearMonth.of(2026, 8)
        val vacation = Vacation(
            id = UUID.fromString("00000000-0000-0000-0000-000000000090"),
            startDate = LocalDate.of(2026, 7, 31),
            endDateInclusive = LocalDate.of(2026, 8, 2),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val shift = shift(
            id = UUID.fromString("00000000-0000-0000-0000-000000000091"),
            localDate = month.atDay(2),
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(16, 0),
        )
        val days = projectCalendarMonth(
            month = month,
            shifts = listOf(shift),
            explicitDayStatuses = emptyList(),
            medicalLeaves = emptyList(),
            now = Instant.EPOCH,
            vacations = listOf(vacation),
        )

        assertEquals(vacation, days[0].vacation)
        assertTrue(days[0].isImplicitlyUndefined)
        assertEquals(vacation, days[1].vacation)
        assertEquals(shift.id, days[1].shifts.single().shift.id)
        assertEquals(null, days[2].vacation)
    }

    private fun shift(
        id: UUID,
        localDate: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
    ): Shift {
        val start = ZonedDateTime.of(localDate, startTime, zone)
        val endDate = if (endTime > startTime) localDate else localDate.plusDays(1)
        val end = ZonedDateTime.of(endDate, endTime, zone)
        return Shift(
            id = id,
            startAt = start.toInstant(),
            endAt = end.toInstant(),
            zoneId = zone,
            localStartDate = localDate,
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "OBJ",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = startTime,
            endTimeSnapshot = endTime,
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = UUID.fromString("00000000-0000-0000-0000-000000000099"),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
