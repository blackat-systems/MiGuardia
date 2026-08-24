package com.blackatsystems.miguardia.core.domain.nextevent

import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEventTest {
    private val zone: ZoneId = AppDefaults.zoneId()
    private val now = ZonedDateTime.of(2026, 8, 15, 12, 0, 0, 0, zone).toInstant()

    @Test
    fun futureShiftIsUpcomingWithDurationUntilItsInclusiveStart() {
        val future = shift("10000000-0000-0000-0000-000000000001", now.plusSeconds(90 * 60), now.plusSeconds(150 * 60))

        val before = projection(now, shifts = listOf(future))
        val atStart = projection(future.startAt, shifts = listOf(future))

        assertEquals(NextEventPrimary.UPCOMING_SHIFT, before.primaryEvent)
        assertEquals(Duration.ofMinutes(90), before.remaining)
        assertEquals(listOf(future), before.upcomingShifts)
        assertEquals(NextEventPrimary.ONGOING_SHIFT, atStart.primaryEvent)
    }

    @Test
    fun exactEndIsExclusiveAndEndedShiftDisappears() {
        val current = shift("10000000-0000-0000-0000-000000000002", now.minusSeconds(60), now.plusSeconds(60))

        assertEquals(NextEventPrimary.ONGOING_SHIFT, projection(now, shifts = listOf(current)).primaryEvent)
        assertEquals(NextEventPrimary.NONE, projection(current.endAt, shifts = listOf(current)).primaryEvent)
    }

    @Test
    fun overnightShiftStartedYesterdayRemainsOngoingUntilRealEnd() {
        val date = LocalDate.of(2026, 8, 14)
        val overnight = shiftAt(
            id = "10000000-0000-0000-0000-000000000003",
            date = date,
            start = LocalTime.of(19, 0),
            end = LocalTime.of(7, 0),
        )
        val afterMidnight = ZonedDateTime.of(2026, 8, 15, 3, 0, 0, 0, zone).toInstant()

        val result = projection(afterMidnight, shifts = listOf(overnight))

        assertEquals(NextEventPrimary.ONGOING_SHIFT, result.primaryEvent)
        assertEquals(Duration.ofHours(4), result.remaining)
    }

    @Test
    fun cancelledAbsentAndCompletedShiftsAreNeverCandidates() {
        val completed = shift("10000000-0000-0000-0000-000000000004", now.minusSeconds(120), now.minusSeconds(60))
        val cancelled = shift("10000000-0000-0000-0000-000000000005", now.plusSeconds(60), now.plusSeconds(120), ShiftStatus.CANCELLED)
        val absent = shift("10000000-0000-0000-0000-000000000006", now.plusSeconds(60), now.plusSeconds(120), ShiftStatus.ABSENT)

        val result = projection(now, shifts = listOf(completed, cancelled, absent))

        assertEquals(NextEventPrimary.NONE, result.primaryEvent)
        assertTrue(result.ongoingShifts.isEmpty())
        assertTrue(result.upcomingShifts.isEmpty())
    }

    @Test
    fun vacationExcludesPlannedShiftWithoutMutationAndLaterShiftRemainsEligible() {
        val inside = shiftAt("10000000-0000-0000-0000-000000000007", LocalDate.of(2026, 8, 16), LocalTime.of(8, 0), LocalTime.of(16, 0))
        val after = shiftAt("10000000-0000-0000-0000-000000000008", LocalDate.of(2026, 8, 18), LocalTime.of(8, 0), LocalTime.of(16, 0))
        val original = inside.copy()

        val result = projection(
            now,
            shifts = listOf(inside, after),
            vacations = listOf(vacation(LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 17))),
        )

        assertEquals(listOf(after), result.upcomingShifts)
        assertEquals(original, inside)
        assertEquals(ShiftStatus.PLANNED, inside.status)
    }

    @Test
    fun simultaneousOngoingShiftsAreKeptAndStablyOrdered() {
        val laterEnd = shift("30000000-0000-0000-0000-000000000003", now.minusSeconds(120), now.plusSeconds(300))
        val idLater = shift("30000000-0000-0000-0000-000000000002", now.minusSeconds(60), now.plusSeconds(120))
        val idEarlier = shift("30000000-0000-0000-0000-000000000001", now.minusSeconds(60), now.plusSeconds(120))

        val result = projection(now, shifts = listOf(idLater, laterEnd, idEarlier))

        assertEquals(listOf(laterEnd, idEarlier, idLater), result.ongoingShifts)
        assertEquals(Duration.ofMinutes(5), result.remaining)
    }

    @Test
    fun equalUpcomingStartKeepsEveryShiftAndOrdersByEndThenUuid() {
        val start = now.plusSeconds(3600)
        val long = shift("40000000-0000-0000-0000-000000000003", start, start.plusSeconds(7200))
        val idLater = shift("40000000-0000-0000-0000-000000000002", start, start.plusSeconds(3600))
        val idEarlier = shift("40000000-0000-0000-0000-000000000001", start, start.plusSeconds(3600))
        val other = shift("40000000-0000-0000-0000-000000000004", start.plusSeconds(60), start.plusSeconds(7200))

        val result = projection(now, shifts = listOf(long, idLater, other, idEarlier))

        assertEquals(listOf(idEarlier, idLater, long), result.upcomingShifts)
        assertFalse(other in result.upcomingShifts)
    }

    @Test
    fun dayOffTodayAndMinimumFutureDayOffAreRecognizedButUndefinedIsNot() {
        val today = LocalDate.of(2026, 8, 15)
        val statuses = listOf(
            ExplicitDayStatus(today.plusDays(2), ExplicitDayStatusType.DAY_OFF),
            ExplicitDayStatus(today, ExplicitDayStatusType.DAY_OFF),
            ExplicitDayStatus(today.plusDays(1), ExplicitDayStatusType.UNDEFINED),
            ExplicitDayStatus(today.minusDays(1), ExplicitDayStatusType.DAY_OFF),
        )

        val result = projection(now, statuses = statuses)

        assertEquals(NextEventPrimary.DAY_OFF, result.primaryEvent)
        assertEquals(today, result.nextDayOff)
        assertEquals(Duration.ZERO, result.remaining)
        assertEquals(NextEventPrimary.NONE, projection(now).primaryEvent)
    }

    @Test
    fun dayOffCoexistingWithShiftIsPreservedAsSecondaryResult() {
        val future = shift("50000000-0000-0000-0000-000000000001", now.plusSeconds(60), now.plusSeconds(120))
        val dayOff = ExplicitDayStatus(LocalDate.of(2026, 8, 15), ExplicitDayStatusType.DAY_OFF)

        val result = projection(now, shifts = listOf(future), statuses = listOf(dayOff))

        assertEquals(NextEventPrimary.UPCOMING_SHIFT, result.primaryEvent)
        assertEquals(dayOff.date, result.nextDayOff)
    }

    @Test
    fun priorityIsOngoingThenUpcomingThenDayOffThenEmpty() {
        val ongoing = shift("60000000-0000-0000-0000-000000000001", now.minusSeconds(60), now.plusSeconds(60))
        val upcoming = shift("60000000-0000-0000-0000-000000000002", now.plusSeconds(120), now.plusSeconds(180))
        val dayOff = ExplicitDayStatus(LocalDate.of(2026, 8, 16), ExplicitDayStatusType.DAY_OFF)

        assertEquals(NextEventPrimary.ONGOING_SHIFT, projection(now, listOf(ongoing, upcoming), listOf(dayOff)).primaryEvent)
        assertEquals(NextEventPrimary.UPCOMING_SHIFT, projection(now, listOf(upcoming), listOf(dayOff)).primaryEvent)
        assertEquals(NextEventPrimary.DAY_OFF, projection(now, statuses = listOf(dayOff)).primaryEvent)
        assertEquals(NextEventPrimary.NONE, projection(now).primaryEvent)
    }

    @Test
    fun durationsNeverBecomeNegativeEvenForZeroLengthCorruptInput() {
        val unusual = shift("70000000-0000-0000-0000-000000000001", now, now)

        val result = projection(now, shifts = listOf(unusual))

        assertEquals(Duration.ZERO, result.remaining)
        assertEquals(NextEventPrimary.NONE, result.primaryEvent)
    }

    @Test
    fun civilDateSemanticsCoverMonthYearLeapDayAndIgnoreMachineZone() {
        val cordobaNow = ZonedDateTime.of(2028, 2, 29, 23, 30, 0, 0, zone).toInstant()
        val statuses = listOf(
            ExplicitDayStatus(LocalDate.of(2028, 2, 29), ExplicitDayStatusType.DAY_OFF),
            ExplicitDayStatus(LocalDate.of(2028, 3, 1), ExplicitDayStatusType.DAY_OFF),
            ExplicitDayStatus(LocalDate.of(2029, 1, 1), ExplicitDayStatusType.DAY_OFF),
        )

        val result = projection(cordobaNow, statuses = statuses)

        assertEquals(LocalDate.of(2028, 2, 29), result.nextDayOff)
        assertEquals(zone, ZoneId.of("America/Argentina/Cordoba"))
        assertFalse(LocalDate.ofInstant(cordobaNow, ZoneId.of("Asia/Tokyo")) == LocalDate.ofInstant(cordobaNow, zone))
    }

    private fun projection(
        instant: Instant,
        shifts: List<Shift> = emptyList(),
        statuses: List<ExplicitDayStatus> = emptyList(),
        vacations: List<Vacation> = emptyList(),
    ) = projectNextEvent(instant, zone, shifts, statuses, vacations)

    private fun shiftAt(
        id: String,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
    ): Shift {
        val startAt = ZonedDateTime.of(date, start, zone).toInstant()
        val endDate = if (end <= start) date.plusDays(1) else date
        return shift(id, startAt, ZonedDateTime.of(endDate, end, zone).toInstant())
    }

    private fun shift(
        id: String,
        start: Instant,
        end: Instant,
        status: ShiftStatus = ShiftStatus.PLANNED,
    ) = Shift(
        id = UUID.fromString(id),
        startAt = start,
        endAt = end,
        zoneId = zone,
        localStartDate = start.atZone(zone).toLocalDate(),
        objectiveNameSnapshot = "Objetivo ficticio",
        objectiveAbbreviationSnapshot = "FIC",
        objectiveAddressSnapshot = null,
        startTimeSnapshot = start.atZone(zone).toLocalTime(),
        endTimeSnapshot = end.atZone(zone).toLocalTime(),
        colorArgbSnapshot = 0xFF315DA8.toInt(),
        position = null,
        status = status,
        sourceObjectiveId = UUID.fromString("80000000-0000-0000-0000-000000000099"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun vacation(start: LocalDate, end: LocalDate) = Vacation(
        id = UUID.fromString("80000000-0000-0000-0000-000000000001"),
        startDate = start,
        endDateInclusive = end,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
