package com.blackatsystems.miguardia.core.domain.hours

import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MonthlyHoursTest {
    private val month = YearMonth.of(2026, 8)
    private val zone = AppDefaults.zoneId()

    @Test fun emptyMonthHasOnlyZeroValues() {
        val result = summary(emptyList(), at(13, 12))
        assertEquals(Duration.ZERO, result.planned)
        assertEquals(Duration.ZERO, result.worked)
        assertEquals(Duration.ZERO, result.pending)
        assertEquals(0, result.shiftCount)
    }

    @Test fun completedShiftIsEntirelyWorked() {
        val result = summary(listOf(shift(1, 8, 16)), at(2, 0))
        assertHours(8, result.planned)
        assertHours(8, result.worked)
        assertHours(0, result.pending)
    }

    @Test fun futureShiftIsEntirelyPending() {
        val result = summary(listOf(shift(20, 8, 16)), at(13, 12))
        assertHours(0, result.worked)
        assertHours(8, result.pending)
    }

    @Test fun currentShiftSplitsUsingTheExactReferenceInstant() {
        val result = summary(listOf(shift(13, 8, 16)), at(13, 12, 30))
        assertEquals(Duration.ofHours(4).plusMinutes(30), result.worked)
        assertEquals(Duration.ofHours(3).plusMinutes(30), result.pending)
    }

    @Test fun exactStartLeavesAllHoursPending() {
        val result = summary(listOf(shift(13, 8, 16)), at(13, 8))
        assertHours(0, result.worked)
        assertHours(8, result.pending)
    }

    @Test fun exactEndMakesAllHoursWorked() {
        val result = summary(listOf(shift(13, 8, 16)), at(13, 16))
        assertHours(8, result.worked)
        assertHours(0, result.pending)
    }

    @Test fun overnightShiftUsesRealInstants() {
        val result = summary(listOf(shift(12, 19, 7)), at(13, 1))
        assertHours(6, result.worked)
        assertHours(6, result.pending)
    }

    @Test fun shiftBelongsOnlyToItsLocalStartMonth() {
        val augustShift = shift(31, 19, 7)
        assertHours(12, summary(listOf(augustShift), at(31, 23)).planned)
        val september = calculateMonthlyHours(
            month = YearMonth.of(2026, 9),
            shifts = listOf(augustShift),
            explicitDayStatuses = emptyList(),
            medicalLeaves = emptyList(),
            referenceInstant = at(31, 23),
        )
        assertEquals(0, september.shiftCount)
    }

    @Test fun overlappingSecondShiftIsCountedIndependently() {
        val first = shift(1, 8, 16)
        val second = first.copy(id = UUID.fromString("40000000-0000-0000-0000-000000000001"))
        val result = summary(listOf(first, second), at(2, 0))
        assertEquals(2, result.shiftCount)
        assertHours(16, result.planned)
        assertHours(16, result.worked)
    }

    @Test fun full24HourShiftUsesItsRealDuration() {
        val result = summary(listOf(shift(1, 8, 8)), at(3, 0))
        assertHours(24, result.planned)
        assertHours(24, result.worked)
    }

    @Test fun absenceKeepsPlannedHoursButNeverCreatesWorkedOrPending() {
        val result = summary(listOf(shift(1, 8, 16, ShiftStatus.ABSENT)), at(13, 12))
        assertHours(8, result.planned)
        assertHours(8, result.absenceHours)
        assertEquals(1, result.absenceCount)
        assertHours(0, result.worked)
        assertHours(0, result.pending)
    }

    @Test fun cancellationKeepsPlannedHoursButNeverCreatesWorkedOrPending() {
        val result = summary(listOf(shift(20, 8, 16, ShiftStatus.CANCELLED)), at(13, 12))
        assertHours(8, result.cancellationHours)
        assertEquals(1, result.cancellationCount)
        assertHours(0, result.pending)
    }

    @Test fun medicalLeaveClassifiesPlannedShiftByLocalStartDate() {
        val leave = leave(LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 12))
        val result = summary(listOf(shift(12, 19, 7)), at(13, 2), leaves = listOf(leave))
        assertHours(12, result.medicalLeaveHours)
        assertHours(0, result.worked)
        assertHours(0, result.pending)
    }

    @Test fun absencePrecedesMedicalLeave() {
        val date = LocalDate.of(2026, 8, 12)
        val result = summary(
            listOf(shift(12, 8, 16, ShiftStatus.ABSENT)),
            at(13, 0),
            leaves = listOf(leave(date, date)),
        )
        assertHours(8, result.absenceHours)
        assertHours(0, result.medicalLeaveHours)
    }

    @Test fun cancellationPrecedesMedicalLeave() {
        val date = LocalDate.of(2026, 8, 12)
        val result = summary(
            listOf(shift(12, 8, 16, ShiftStatus.CANCELLED)),
            at(13, 0),
            leaves = listOf(leave(date, date)),
        )
        assertHours(8, result.cancellationHours)
        assertHours(0, result.medicalLeaveHours)
    }

    @Test fun overlappingMedicalLeavesCountUniqueClippedDays() {
        val result = summary(
            emptyList(),
            at(13, 0),
            leaves = listOf(
                leave(LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 2)),
                leave(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 4)),
            ),
        )
        assertEquals(4, result.medicalLeaveDayCount)
    }

    @Test fun explicitDayOffsAreUniqueAndLimitedToMonth() {
        val statuses = listOf(
            ExplicitDayStatus(LocalDate.of(2026, 8, 2), ExplicitDayStatusType.DAY_OFF),
            ExplicitDayStatus(LocalDate.of(2026, 8, 2), ExplicitDayStatusType.DAY_OFF),
            ExplicitDayStatus(LocalDate.of(2026, 8, 3), ExplicitDayStatusType.UNDEFINED),
            ExplicitDayStatus(LocalDate.of(2026, 9, 1), ExplicitDayStatusType.DAY_OFF),
        )
        assertEquals(1, summary(emptyList(), at(13, 0), statuses = statuses).dayOffCount)
    }

    @Test fun exactly204WorkedHoursDoNotProduceOvertime() {
        val result = summary(listOf(longShift(204)), at(31, 23))
        assertHours(204, result.worked)
        assertHours(0, result.overtime)
    }

    @Test fun overtimeIsOnlyWorkedTimeAbove204Hours() {
        val result = summary(listOf(longShift(210)), at(31, 23))
        assertHours(6, result.overtime)
    }

    @Test fun pendingHoursNeverProduceOvertime() {
        val result = summary(listOf(longShift(210)), at(1, 0))
        assertHours(0, result.overtime)
    }

    @Test fun nightWindowIncludes21AndExcludes06() {
        val result = summary(listOf(shift(1, 21, 6)), at(2, 7))
        assertHours(9, result.nightWorked)
    }

    @Test fun nightClassificationCutsMixedShiftAtBothBoundaries() {
        val result = summary(listOf(shift(1, 20, 7)), at(2, 8))
        assertHours(9, result.nightWorked)
        assertHours(11, result.worked)
    }

    @Test fun nightClassificationOnlyCountsWorkedPartOfCurrentShift() {
        val result = summary(listOf(shift(13, 19, 7)), at(13, 23))
        assertHours(2, result.nightWorked)
    }

    @Test fun holidayClassificationUsesCivilDateIntervals() {
        val result = summary(
            listOf(shift(14, 19, 7)),
            at(16, 0),
            holidays = setOf(LocalDate.of(2026, 8, 15)),
        )
        assertHours(7, result.holidayWorked)
    }

    @Test fun holidayClassificationOnlyCountsElapsedWork() {
        val result = summary(
            listOf(shift(14, 19, 7)),
            at(15, 2),
            holidays = setOf(LocalDate.of(2026, 8, 15)),
        )
        assertHours(2, result.holidayWorked)
    }

    @Test fun holidayClassificationCrossesTheEndOfTheYear() {
        val december = YearMonth.of(2026, 12)
        val start = december.atEndOfMonth().atTime(19, 0).atZone(zone)
        val end = start.toLocalDate().plusDays(1).atTime(7, 0).atZone(zone)
        val result = calculateMonthlyHours(
            month = december,
            shifts = listOf(shift(start, end, start.toLocalDate(), ShiftStatus.PLANNED)),
            explicitDayStatuses = emptyList(),
            medicalLeaves = emptyList(),
            referenceInstant = end.plusHours(1).toInstant(),
            holidayDates = setOf(LocalDate.of(2027, 1, 1)),
        )
        assertHours(12, result.worked)
        assertHours(7, result.holidayWorked)
    }

    @Test fun nightWindowUsesRealElapsedTimeAcrossDaylightSavingChange() {
        val newYork = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 3, 7)
        val start = date.atTime(21, 0).atZone(newYork)
        val end = date.plusDays(1).atTime(7, 0).atZone(newYork)
        val result = calculateMonthlyHours(
            month = YearMonth.of(2026, 3),
            shifts = listOf(shift(start, end, date, ShiftStatus.PLANNED, newYork)),
            explicitDayStatuses = emptyList(),
            medicalLeaves = emptyList(),
            referenceInstant = end.toInstant(),
        )
        assertHours(9, result.worked)
        assertHours(8, result.nightWorked)
    }

    @Test fun specialClassificationsCanOverlapWithoutChangingWorkedTotal() {
        val result = summary(
            listOf(shift(14, 21, 6)),
            at(16, 0),
            holidays = setOf(LocalDate.of(2026, 8, 15)),
        )
        assertHours(9, result.worked)
        assertHours(9, result.nightWorked)
        assertHours(6, result.holidayWorked)
    }

    @Test fun currentShiftCanCrossTheOvertimeThresholdPartially() {
        val current = shift(20, 8, 16)
        val result = summary(listOf(longShift(200), current), at(20, 13))
        assertHours(205, result.worked)
        assertHours(1, result.overtime)
        assertHours(3, result.pending)
    }

    @Test fun absenceCancellationAndMedicalLeaveDoNotApproachThreshold() {
        val medicalDate = LocalDate.of(2026, 8, 22)
        val result = summary(
            shifts = listOf(
                longShift(203),
                shift(20, 8, 16, ShiftStatus.ABSENT),
                shift(21, 8, 16, ShiftStatus.CANCELLED),
                shift(22, 8, 16),
                shift(25, 8, 10),
            ),
            now = at(31, 23),
            leaves = listOf(leave(medicalDate, medicalDate)),
        )
        assertHours(205, result.worked)
        assertHours(1, result.overtime)
        assertHours(8, result.absenceHours)
        assertHours(8, result.cancellationHours)
        assertHours(8, result.medicalLeaveHours)
    }

    @Test fun leapFebruaryAndYearSpanningMedicalLeaveAreClippedCorrectly() {
        val leapMonth = YearMonth.of(2028, 2)
        val result = calculateMonthlyHours(
            month = leapMonth,
            shifts = emptyList(),
            explicitDayStatuses = emptyList(),
            medicalLeaves = listOf(
                leave(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 3, 1)),
            ),
            referenceInstant = Instant.parse("2028-02-29T12:00:00Z"),
        )
        assertEquals(29, result.medicalLeaveDayCount)
    }

    @Test fun shiftsWithSameStartProduceStableSummaryRegardlessOfInputOrder() {
        val first = shift(1, 8, 16)
        val second = first.copy(
            id = UUID.fromString("40000000-0000-0000-0000-000000000002"),
            endAt = first.endAt.plus(Duration.ofHours(4)),
        )
        val forward = summary(listOf(first, second), at(2, 0))
        val reverse = summary(listOf(second, first), at(2, 0))
        assertEquals(forward, reverse)
    }

    @Test fun accountingInvariantCoversEveryPlannedHour() {
        val shifts = listOf(
            shift(1, 8, 16),
            shift(13, 8, 16),
            shift(20, 8, 16),
            shift(4, 8, 16, ShiftStatus.ABSENT),
            shift(5, 8, 16, ShiftStatus.CANCELLED),
            shift(6, 8, 16),
        )
        val result = summary(
            shifts,
            at(13, 12),
            leaves = listOf(leave(LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 6))),
        )
        assertEquals(
            result.planned,
            result.worked + result.pending + result.absenceHours +
                result.cancellationHours + result.medicalLeaveHours,
        )
    }

    @Test fun invalidShiftIntervalIsRejected() {
        val valid = shift(1, 8, 16)
        assertThrows(IllegalArgumentException::class.java) {
            summary(listOf(valid.copy(endAt = valid.startAt)), at(13, 0))
        }
    }

    @Test fun vacationDaysAreInclusiveUniqueAndClippedToMonth() {
        val result = summary(
            shifts = emptyList(),
            now = at(13, 0),
            vacations = listOf(
                vacation(LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 2)),
                vacation(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 4)),
            ),
        )
        assertEquals(4, result.vacationDayCount)
    }

    @Test fun plannedPastShiftInVacationIsExcludedFromEveryHourCategory() {
        val date = LocalDate.of(2026, 8, 12)
        val result = summary(
            shifts = listOf(shift(12, 19, 7)),
            now = at(14, 0),
            holidays = setOf(date, date.plusDays(1)),
            vacations = listOf(vacation(date, date)),
        )
        assertHours(0, result.planned)
        assertHours(0, result.worked)
        assertHours(0, result.pending)
        assertHours(0, result.nightWorked)
        assertHours(0, result.holidayWorked)
        assertHours(0, result.overtime)
        assertEquals(1, result.shiftCount)
    }

    @Test fun plannedCurrentAndFutureShiftsInVacationStayExcluded() {
        val dates = listOf(LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 20))
        val result = summary(
            shifts = listOf(shift(13, 8, 16), shift(20, 8, 16)),
            now = at(13, 12),
            vacations = dates.map { vacation(it, it) },
        )
        assertHours(0, result.planned)
        assertHours(0, result.worked)
        assertHours(0, result.pending)
        assertEquals(2, result.shiftCount)
    }

    @Test fun absenceAndCancellationPrecedeVacation() {
        val date = LocalDate.of(2026, 8, 12)
        val result = summary(
            shifts = listOf(
                shift(12, 8, 16, ShiftStatus.ABSENT),
                shift(12, 19, 7, ShiftStatus.CANCELLED),
            ),
            now = at(14, 0),
            vacations = listOf(vacation(date, date)),
        )
        assertHours(20, result.planned)
        assertHours(8, result.absenceHours)
        assertHours(12, result.cancellationHours)
        assertEquals(1, result.absenceCount)
        assertEquals(1, result.cancellationCount)
    }

    @Test fun vacationExclusionPreservesAccountingInvariantForIncludedShifts() {
        val vacationDate = LocalDate.of(2026, 8, 2)
        val result = summary(
            shifts = listOf(shift(1, 8, 16), shift(2, 8, 16), shift(20, 8, 16)),
            now = at(13, 12),
            vacations = listOf(vacation(vacationDate, vacationDate)),
        )
        assertHours(16, result.planned)
        assertEquals(
            result.planned,
            result.worked + result.pending + result.absenceHours +
                result.cancellationHours + result.medicalLeaveHours,
        )
    }

    @Test fun leapFebruaryVacationCountsAllCivilDates() {
        val leapMonth = YearMonth.of(2028, 2)
        val result = calculateMonthlyHours(
            month = leapMonth,
            shifts = emptyList(),
            explicitDayStatuses = emptyList(),
            medicalLeaves = emptyList(),
            referenceInstant = Instant.parse("2028-02-15T12:00:00Z"),
            vacations = listOf(
                vacation(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 3, 1)),
            ),
        )
        assertEquals(29, result.vacationDayCount)
    }

    @Test fun invalidMedicalRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            summary(
                emptyList(),
                at(13, 0),
                leaves = listOf(leave(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1))),
            )
        }
    }

    @Test fun negativeThresholdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            calculateMonthlyHours(
                month,
                emptyList(),
                emptyList(),
                emptyList(),
                at(13, 0),
                monthlyThreshold = Duration.ofHours(-1),
            )
        }
    }

    private fun summary(
        shifts: List<Shift>,
        now: Instant,
        statuses: List<ExplicitDayStatus> = emptyList(),
        leaves: List<MedicalLeave> = emptyList(),
        holidays: Set<LocalDate> = emptySet(),
        vacations: List<Vacation> = emptyList(),
    ): MonthlyHoursSummary = calculateMonthlyHours(
        month = month,
        shifts = shifts,
        explicitDayStatuses = statuses,
        medicalLeaves = leaves,
        referenceInstant = now,
        holidayDates = holidays,
        vacations = vacations,
    )

    private fun shift(
        day: Int,
        startHour: Int,
        endHour: Int,
        status: ShiftStatus = ShiftStatus.PLANNED,
    ): Shift {
        val date = month.atDay(day)
        val start = date.atTime(startHour, 0).atZone(zone)
        val endDate = if (endHour > startHour) date else date.plusDays(1)
        val end = endDate.atTime(endHour, 0).atZone(zone)
        return shift(start, end, date, status)
    }

    private fun longShift(hours: Long): Shift {
        val start = month.atDay(1).atStartOfDay(zone)
        return shift(start, start.plusHours(hours), month.atDay(1), ShiftStatus.PLANNED)
    }

    private fun shift(
        start: ZonedDateTime,
        end: ZonedDateTime,
        localDate: LocalDate,
        status: ShiftStatus,
        zoneId: ZoneId = zone,
    ) = Shift(
        id = UUID.nameUUIDFromBytes("$start-$end-$status".toByteArray()),
        startAt = start.toInstant(),
        endAt = end.toInstant(),
        zoneId = zoneId,
        localStartDate = localDate,
        objectiveNameSnapshot = "Objetivo ficticio",
        objectiveAbbreviationSnapshot = "OBJ",
        objectiveAddressSnapshot = null,
        startTimeSnapshot = start.toLocalTime(),
        endTimeSnapshot = end.toLocalTime(),
        colorArgbSnapshot = 0xFF336699.toInt(),
        position = null,
        status = status,
        sourceObjectiveId = null,
        sourceScheduleCombinationId = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun leave(start: LocalDate, end: LocalDate) = MedicalLeave(
        id = UUID.nameUUIDFromBytes("$start-$end".toByteArray()),
        startDate = start,
        endDateInclusive = end,
        privateNote = "Nota ficticia",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun vacation(start: LocalDate, end: LocalDate) = Vacation(
        id = UUID.nameUUIDFromBytes("vacation-$start-$end".toByteArray()),
        startDate = start,
        endDateInclusive = end,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        month.atDay(day).atTime(hour, minute).atZone(zone).toInstant()

    private fun assertHours(expected: Long, actual: Duration) =
        assertEquals(Duration.ofHours(expected), actual)
}
