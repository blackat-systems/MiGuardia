package com.blackatsystems.miguardia.core.domain.nextevent

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayCardProjectionTest {
    private val zone: ZoneId = ZoneId.of("America/Argentina/Cordoba")
    private val clock: Clock = Clock.fixed(
        ZonedDateTime.of(2026, 8, 27, 12, 0, 0, 0, zone).toInstant(),
        zone,
    )
    private val now: Instant = clock.instant()
    private val today: LocalDate = LocalDate.of(2026, 8, 27)

    @Test
    fun noShiftsTodayUsesTheExistingFutureEvent() {
        // 1. Hoy sin jornadas usa el próximo evento futuro.
        val futureShift = shiftAt(
            id = "10000000-0000-0000-0000-000000000001",
            date = today.plusDays(1),
            start = LocalTime.of(8, 0),
            end = LocalTime.of(16, 0),
        )
        val future = futureEvent(now, shifts = listOf(futureShift))

        val result = projection(futureEvent = future)

        assertEquals(TodayCardPrimary.FUTURE_EVENT, result.primary)
        assertEquals(NextEventPrimary.UPCOMING_SHIFT, result.futureEvent.primaryEvent)
        assertEquals(listOf(futureShift), result.futureEvent.upcomingShifts)
        assertTrue(result.shifts.isEmpty())
        assertEquals(0, result.todayShiftCount)
    }

    @Test
    fun upcomingShiftTodayIsThePrimaryShift() {
        // 2. Una jornada próxima de hoy queda como principal.
        val upcoming = shiftAt(
            id = "10000000-0000-0000-0000-000000000002",
            date = today,
            start = LocalTime.of(14, 0),
            end = LocalTime.of(22, 0),
        )

        val result = projection(todayShifts = listOf(upcoming))

        assertEquals(TodayCardPrimary.UPCOMING_SHIFT, result.primary)
        assertEquals(upcoming.id, result.primaryShift?.shift?.id)
        assertEquals(TodayShiftState.UPCOMING, result.primaryShift?.state)
        assertEquals(Duration.ofHours(2), result.remaining)
    }

    @Test
    fun ongoingShiftPrecedesAnUpcomingShift() {
        // 3. Una jornada en curso queda antes que una próxima.
        val upcoming = shiftAt(
            id = "10000000-0000-0000-0000-000000000003",
            date = today,
            start = LocalTime.of(14, 0),
            end = LocalTime.of(22, 0),
        )
        val ongoing = shiftAt(
            id = "10000000-0000-0000-0000-000000000004",
            date = today,
            start = LocalTime.of(8, 0),
            end = LocalTime.of(16, 0),
        )

        val result = projection(todayShifts = listOf(upcoming, ongoing))

        assertEquals(TodayCardPrimary.ONGOING_SHIFT, result.primary)
        assertEquals(ongoing.id, result.primaryShift?.shift?.id)
        assertEquals(listOf(ongoing.id, upcoming.id), result.shifts.map { it.shift.id })
        assertEquals(Duration.ofHours(4), result.remaining)
    }

    @Test
    fun activeOvernightShiftFromYesterdayIsPrimaryAndDeduplicated() {
        // 4. La nocturna iniciada ayer sigue activa, es principal y aparece una vez.
        val overnight = shiftAt(
            id = "10000000-0000-0000-0000-000000000005",
            date = today.minusDays(1),
            start = LocalTime.of(21, 0),
            end = LocalTime.of(13, 0),
        )
        val todayUpcoming = shiftAt(
            id = "10000000-0000-0000-0000-000000000006",
            date = today,
            start = LocalTime.of(18, 0),
            end = LocalTime.of(23, 0),
        )

        val result = projection(
            todayShifts = listOf(todayUpcoming),
            previousDayCandidates = listOf(overnight, overnight.copy()),
        )

        assertEquals(TodayCardPrimary.ONGOING_SHIFT, result.primary)
        assertEquals(overnight.id, result.primaryShift?.shift?.id)
        assertTrue(result.primaryShift?.startedYesterday == true)
        assertEquals(1, result.shifts.count { it.shift.id == overnight.id })
        assertEquals(1, result.todayShiftCount)
    }

    @Test
    fun completedShiftTodayRemainsInTheExpandableHistory() {
        // 5. Una jornada completada hoy permanece en la lista desplegable.
        val completed = shiftAt(
            id = "10000000-0000-0000-0000-000000000007",
            date = today,
            start = LocalTime.of(6, 0),
            end = LocalTime.of(10, 0),
        )

        val result = projection(todayShifts = listOf(completed))

        assertEquals(TodayCardPrimary.COMPLETED_SUMMARY, result.primary)
        assertEquals(TodayShiftState.COMPLETED, result.shifts.single().state)
        assertEquals(1, result.completedTodayCount)
        assertTrue(result.canExpand)
    }

    @Test
    fun todayShiftsAreOrderedByStartThenEndThenUuid() {
        // 6. Varias jornadas se ordenan por inicio, fin y UUID.
        val first = shiftAt(
            id = "20000000-0000-0000-0000-000000000003",
            date = today,
            start = LocalTime.of(6, 0),
            end = LocalTime.of(15, 0),
        )
        val shorterIdLater = shiftAt(
            id = "20000000-0000-0000-0000-000000000002",
            date = today,
            start = LocalTime.of(8, 0),
            end = LocalTime.of(12, 0),
        )
        val shorterIdEarlier = shiftAt(
            id = "20000000-0000-0000-0000-000000000001",
            date = today,
            start = LocalTime.of(8, 0),
            end = LocalTime.of(12, 0),
        )
        val longer = shiftAt(
            id = "20000000-0000-0000-0000-000000000004",
            date = today,
            start = LocalTime.of(8, 0),
            end = LocalTime.of(14, 0),
        )

        val result = projection(
            todayShifts = listOf(longer, shorterIdLater, first, shorterIdEarlier),
        )

        assertEquals(
            listOf(first.id, shorterIdEarlier.id, shorterIdLater.id, longer.id),
            result.shifts.map { summary -> summary.shift.id },
        )
    }

    @Test
    fun simultaneousOngoingShiftsAndEqualStartsAreAllKeptInStableOrder() {
        // 7 y 8. Dos jornadas simultáneas, incluso con el mismo inicio, conservan fin y UUID.
        val longer = shiftAt(
            id = "30000000-0000-0000-0000-000000000003",
            date = today,
            start = LocalTime.of(8, 0),
            end = LocalTime.of(18, 0),
        )
        val idLater = shiftAt(
            id = "30000000-0000-0000-0000-000000000002",
            date = today,
            start = LocalTime.of(8, 0),
            end = LocalTime.of(16, 0),
        )
        val idEarlier = shiftAt(
            id = "30000000-0000-0000-0000-000000000001",
            date = today,
            start = LocalTime.of(8, 0),
            end = LocalTime.of(16, 0),
        )

        val result = projection(todayShifts = listOf(longer, idLater, idEarlier))

        assertEquals(TodayCardPrimary.ONGOING_SHIFT, result.primary)
        assertEquals(3, result.shifts.size)
        assertTrue(result.shifts.all { it.state == TodayShiftState.IN_PROGRESS })
        assertEquals(
            listOf(idEarlier.id, idLater.id, longer.id),
            result.shifts.map { summary -> summary.shift.id },
        )
    }

    @Test
    fun cancelledAndAbsentRemainHistoricalWithoutBecomingPendingWork() {
        // 9. CANCELLED y ABSENT se conservan sin anunciarse como trabajo pendiente.
        val cancelled = shiftAt(
            id = "40000000-0000-0000-0000-000000000001",
            date = today,
            start = LocalTime.of(14, 0),
            end = LocalTime.of(18, 0),
            status = ShiftStatus.CANCELLED,
        )
        val absent = shiftAt(
            id = "40000000-0000-0000-0000-000000000002",
            date = today,
            start = LocalTime.of(18, 0),
            end = LocalTime.of(22, 0),
            status = ShiftStatus.ABSENT,
        )

        val result = projection(todayShifts = listOf(absent, cancelled))

        assertEquals(TodayCardPrimary.NO_WORK_TODAY, result.primary)
        assertNull(result.primaryShift)
        assertEquals(
            listOf(TodayShiftState.CANCELLED, TodayShiftState.ABSENT),
            result.shifts.map(TodayShiftSummary::state),
        )
    }

    @Test
    fun vacationAndMedicalLeaveProtectPlannedWorkFromBeingAnnounced() {
        // 10. Vacaciones y carpeta médica no anuncian trabajo sólo planificado.
        val planned = shiftAt(
            id = "40000000-0000-0000-0000-000000000003",
            date = today,
            start = LocalTime.of(14, 0),
            end = LocalTime.of(22, 0),
        )
        val vacationResult = projection(
            todayShifts = listOf(planned),
            vacations = listOf(vacation(today, today)),
        )
        val leaveResult = projection(
            todayShifts = listOf(planned),
            medicalLeaves = listOf(medicalLeave(today, today)),
        )

        assertEquals(TodayCardPrimary.NO_WORK_TODAY, vacationResult.primary)
        assertEquals(TodayShiftState.PROTECTED, vacationResult.shifts.single().state)
        assertTrue(vacationResult.shifts.single().isVacationProtected)
        assertFalse(vacationResult.shifts.single().isMedicalLeaveProtected)
        assertEquals(TodayCardPrimary.NO_WORK_TODAY, leaveResult.primary)
        assertEquals(TodayShiftState.PROTECTED, leaveResult.shifts.single().state)
        assertFalse(leaveResult.shifts.single().isVacationProtected)
        assertTrue(leaveResult.shifts.single().isMedicalLeaveProtected)
    }

    @Test
    fun onlyCancelledAbsentOrProtectedKeepsHistoryAndTheFutureEvent() {
        // 11. Sólo históricos/protegidos dicen que hoy no hay trabajo y conservan el futuro.
        val cancelled = shiftAt(
            id = "40000000-0000-0000-0000-000000000004",
            date = today,
            start = LocalTime.of(6, 0),
            end = LocalTime.of(10, 0),
            status = ShiftStatus.CANCELLED,
        )
        val absent = shiftAt(
            id = "40000000-0000-0000-0000-000000000005",
            date = today,
            start = LocalTime.of(10, 0),
            end = LocalTime.of(14, 0),
            status = ShiftStatus.ABSENT,
        )
        val protected = shiftAt(
            id = "40000000-0000-0000-0000-000000000006",
            date = today,
            start = LocalTime.of(18, 0),
            end = LocalTime.of(22, 0),
        )
        val tomorrow = shiftAt(
            id = "40000000-0000-0000-0000-000000000007",
            date = today.plusDays(1),
            start = LocalTime.of(8, 0),
            end = LocalTime.of(16, 0),
        )
        val future = futureEvent(now, shifts = listOf(tomorrow))

        val result = projection(
            todayShifts = listOf(cancelled, absent, protected),
            vacations = listOf(vacation(today, today)),
            futureEvent = future,
        )

        assertEquals(TodayCardPrimary.NO_WORK_TODAY, result.primary)
        assertEquals(3, result.shifts.size)
        assertTrue(result.canExpand)
        assertEquals(NextEventPrimary.UPCOMING_SHIFT, result.futureEvent.primaryEvent)
        assertEquals(tomorrow.id, result.futureEvent.upcomingShifts.single().id)
    }

    @Test
    fun actualTimeRemainsVisibleWhenTheDateIsProtected() {
        // 12. El horario real confirmado no queda oculto por una protección.
        val planned = shiftAt(
            id = "50000000-0000-0000-0000-000000000001",
            date = today,
            start = LocalTime.of(14, 0),
            end = LocalTime.of(22, 0),
        )
        val actual = actual(
            shift = planned,
            start = instant(today, LocalTime.of(8, 0)),
            end = instant(today, LocalTime.of(11, 0)),
        )

        val result = projection(
            todayShifts = listOf(planned),
            actualsByShiftId = mapOf(planned.id to actual),
            medicalLeaves = listOf(medicalLeave(today, today)),
        )

        val summary = result.shifts.single()
        assertEquals(TodayCardPrimary.COMPLETED_SUMMARY, result.primary)
        assertEquals(TodayShiftState.COMPLETED, summary.state)
        assertTrue(summary.hasActualTime)
        assertTrue(summary.isMedicalLeaveProtected)
    }

    @Test
    fun explicitDayOffIsOnlyUsedWhenTodayHasNoPriorityShift() {
        // 13. DAY_OFF es próximo franco, pero no desplaza una jornada prioritaria de hoy.
        val dayOff = ExplicitDayStatus(today, ExplicitDayStatusType.DAY_OFF)
        val upcoming = shiftAt(
            id = "50000000-0000-0000-0000-000000000002",
            date = today,
            start = LocalTime.of(14, 0),
            end = LocalTime.of(18, 0),
        )
        val future = futureEvent(now, statuses = listOf(dayOff))

        val withShift = projection(todayShifts = listOf(upcoming), futureEvent = future)
        val withoutShift = projection(futureEvent = future)

        assertEquals(TodayCardPrimary.UPCOMING_SHIFT, withShift.primary)
        assertEquals(TodayCardPrimary.FUTURE_EVENT, withoutShift.primary)
        assertEquals(NextEventPrimary.DAY_OFF, withoutShift.futureEvent.primaryEvent)
        assertEquals(today, withoutShift.futureEvent.nextDayOff)
    }

    @Test
    fun undefinedAndAnEmptyDayAreNotDayOff() {
        // 14. UNDEFINED y día vacío no son francos.
        val undefined = ExplicitDayStatus(today, ExplicitDayStatusType.UNDEFINED)
        val undefinedFuture = futureEvent(now, statuses = listOf(undefined))

        val undefinedResult = projection(futureEvent = undefinedFuture)
        val emptyResult = projection()

        assertEquals(TodayCardPrimary.EMPTY, undefinedResult.primary)
        assertEquals(NextEventPrimary.NONE, undefinedResult.futureEvent.primaryEvent)
        assertNull(undefinedResult.futureEvent.nextDayOff)
        assertEquals(TodayCardPrimary.EMPTY, emptyResult.primary)
        assertNull(emptyResult.futureEvent.nextDayOff)
    }

    @Test
    fun startIsInclusiveAndEndIsExclusive() {
        // 15. Inicio inclusivo y fin exclusivo.
        val shift = shiftAt(
            id = "60000000-0000-0000-0000-000000000001",
            date = today,
            start = LocalTime.of(12, 0),
            end = LocalTime.of(16, 0),
        )

        val atStart = projection(now = shift.startAt, todayShifts = listOf(shift))
        val atEnd = projection(now = shift.endAt, todayShifts = listOf(shift))

        assertEquals(TodayCardPrimary.ONGOING_SHIFT, atStart.primary)
        assertEquals(TodayShiftState.IN_PROGRESS, atStart.shifts.single().state)
        assertEquals(TodayCardPrimary.COMPLETED_SUMMARY, atEnd.primary)
        assertEquals(TodayShiftState.COMPLETED, atEnd.shifts.single().state)
    }

    @Test
    fun midnightChangesTheOwningDateAndCarriesOnlyTheStillActiveOvernightShift() {
        // 16. El cambio de día se calcula desde el instante y la zona inyectados.
        val firstDate = LocalDate.of(2026, 8, 27)
        val night = shiftAt(
            id = "60000000-0000-0000-0000-000000000002",
            date = firstDate,
            start = LocalTime.of(23, 0),
            end = LocalTime.of(2, 0),
        )
        val beforeMidnight = instant(firstDate, LocalTime.of(23, 59))
        val afterMidnight = instant(firstDate.plusDays(1), LocalTime.MIDNIGHT)

        val before = projection(now = beforeMidnight, todayShifts = listOf(night))
        val after = projection(now = afterMidnight, previousDayCandidates = listOf(night))

        assertEquals(firstDate, before.date)
        assertEquals(firstDate.plusDays(1), after.date)
        assertEquals(1, before.todayShiftCount)
        assertEquals(0, after.todayShiftCount)
        assertEquals(TodayCardPrimary.ONGOING_SHIFT, after.primary)
        assertTrue(after.shifts.single().startedYesterday)
    }

    @Test
    fun monthYearAndLeapDayBoundariesKeepTheActiveOvernightShift() {
        // 17. Fin de mes, fin de año y febrero bisiesto.
        assertOvernightBoundary(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1), "70000000-0000-0000-0000-000000000001")
        assertOvernightBoundary(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1), "70000000-0000-0000-0000-000000000002")
        assertOvernightBoundary(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 1), "70000000-0000-0000-0000-000000000003")
    }

    @Test
    fun cordobaZoneDeterminesTodayIndependentlyFromAnotherMachineZone() {
        // 18. La zona Córdoba decide la fecha civil aunque otra zona ya esté en mañana.
        val reference = Instant.parse("2026-08-28T02:30:00Z")
        val cordobaDate = LocalDate.of(2026, 8, 27)
        val shift = shiftAt(
            id = "80000000-0000-0000-0000-000000000001",
            date = cordobaDate,
            start = LocalTime.of(22, 0),
            end = LocalTime.of(0, 30),
        )

        val result = projection(now = reference, todayShifts = listOf(shift), zoneId = zone)

        assertEquals(cordobaDate, result.date)
        assertEquals(LocalDate.of(2026, 8, 28), reference.atZone(ZoneId.of("Asia/Tokyo")).toLocalDate())
        assertEquals(TodayCardPrimary.ONGOING_SHIFT, result.primary)
    }

    @Test
    fun projectionDoesNotMutateInputsOrPersistDerivedState() {
        // 19. Proyectar no muta Shift, protecciones, horario real ni listas de entrada.
        val shift = shiftAt(
            id = "90000000-0000-0000-0000-000000000001",
            date = today,
            start = LocalTime.of(6, 0),
            end = LocalTime.of(10, 0),
        )
        val vacation = vacation(today, today)
        val leave = medicalLeave(today, today)
        val actual = actual(
            shift = shift,
            start = instant(today, LocalTime.of(5, 30)),
            end = instant(today, LocalTime.of(10, 30)),
        )
        val shiftSnapshot = shift.copy()
        val vacationSnapshot = vacation.copy()
        val leaveSnapshot = leave.copy()
        val actualSnapshot = actual.copy()
        val shifts = mutableListOf(shift)
        val vacations = mutableListOf(vacation)
        val medicalLeaves = mutableListOf(leave)
        val actuals = mutableMapOf(shift.id to actual)

        val first = projection(
            todayShifts = shifts,
            actualsByShiftId = actuals,
            vacations = vacations,
            medicalLeaves = medicalLeaves,
        )
        val second = projection(
            todayShifts = shifts,
            actualsByShiftId = actuals,
            vacations = vacations,
            medicalLeaves = medicalLeaves,
        )

        assertEquals(first, second)
        assertEquals(listOf(shiftSnapshot), shifts)
        assertEquals(listOf(vacationSnapshot), vacations)
        assertEquals(listOf(leaveSnapshot), medicalLeaves)
        assertEquals(mapOf(shift.id to actualSnapshot), actuals)
        assertEquals(ShiftStatus.PLANNED, shift.status)
        assertEquals(instant(today, LocalTime.of(6, 0)), shift.startAt)
        assertEquals(instant(today, LocalTime.of(10, 0)), shift.endAt)
    }

    @Test
    fun projectedCollectionsAreDefensiveAndUnmodifiable() {
        val first = shiftAt(
            id = "91000000-0000-0000-0000-000000000001",
            date = today,
            start = LocalTime.of(6, 0),
            end = LocalTime.of(10, 0),
        )
        val second = shiftAt(
            id = "91000000-0000-0000-0000-000000000002",
            date = today,
            start = LocalTime.of(11, 0),
            end = LocalTime.of(15, 0),
        )
        val ongoing = shiftAt(
            id = "91000000-0000-0000-0000-000000000003",
            date = today,
            start = LocalTime.of(10, 0),
            end = LocalTime.of(14, 0),
        )
        val upcoming = shiftAt(
            id = "91000000-0000-0000-0000-000000000004",
            date = today.plusDays(1),
            start = LocalTime.of(8, 0),
            end = LocalTime.of(12, 0),
        )
        val future = projectNextEvent(
            now = now,
            zoneId = zone,
            shifts = listOf(upcoming, ongoing),
            explicitDayStatuses = emptyList(),
            vacations = emptyList(),
        )
        val result = projection(
            todayShifts = listOf(second, first),
            futureEvent = future,
        )
        val originalOrder = result.shifts.map { summary -> summary.shift.id }

        assertThrows(UnsupportedOperationException::class.java) {
            (result.shifts as MutableList<TodayShiftSummary>)[0] = result.shifts[1]
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (future.ongoingShifts as MutableList<Shift>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (future.upcomingShifts as MutableList<Shift>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (result.futureEvent.ongoingShifts as MutableList<Shift>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (result.futureEvent.upcomingShifts as MutableList<Shift>).clear()
        }
        assertEquals(originalOrder, result.shifts.map { summary -> summary.shift.id })
        assertEquals(second.id, result.primaryShift?.shift?.id)
        assertTrue(result.primaryShift in result.shifts)

        val mutableOngoing = mutableListOf(ongoing)
        val mutableUpcoming = mutableListOf(upcoming)
        val mutableFuture = NextEventResult(
            referenceInstant = now,
            ongoingShifts = mutableOngoing,
            upcomingShifts = mutableUpcoming,
            nextDayOff = null,
            primaryEvent = NextEventPrimary.ONGOING_SHIFT,
            remaining = Duration.ofHours(2),
        )
        val copiedResult = projection(futureEvent = mutableFuture)

        mutableOngoing.clear()
        mutableUpcoming.clear()

        assertEquals(listOf(ongoing.id), copiedResult.futureEvent.ongoingShifts.map(Shift::id))
        assertEquals(listOf(upcoming.id), copiedResult.futureEvent.upcomingShifts.map(Shift::id))
        assertEquals(NextEventPrimary.ONGOING_SHIFT, copiedResult.futureEvent.primaryEvent)
        assertEquals(Duration.ofHours(2), copiedResult.futureEvent.remaining)
        assertThrows(UnsupportedOperationException::class.java) {
            (copiedResult.futureEvent.ongoingShifts as MutableList<Shift>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (copiedResult.futureEvent.upcomingShifts as MutableList<Shift>).clear()
        }
    }

    private fun assertOvernightBoundary(
        previousDate: LocalDate,
        expectedDate: LocalDate,
        id: String,
    ) {
        val overnight = shiftAt(
            id = id,
            date = previousDate,
            start = LocalTime.of(23, 0),
            end = LocalTime.of(2, 0),
        )
        val reference = instant(expectedDate, LocalTime.MIDNIGHT)

        val result = projection(
            now = reference,
            previousDayCandidates = listOf(overnight),
        )

        assertEquals(expectedDate, result.date)
        assertEquals(TodayCardPrimary.ONGOING_SHIFT, result.primary)
        assertEquals(overnight.id, result.shifts.single().shift.id)
        assertTrue(result.shifts.single().startedYesterday)
    }

    private fun projection(
        now: Instant = this.now,
        zoneId: ZoneId = zone,
        todayShifts: List<Shift> = emptyList(),
        previousDayCandidates: List<Shift> = emptyList(),
        actualsByShiftId: Map<UUID, ShiftActualAggregate> = emptyMap(),
        vacations: List<Vacation> = emptyList(),
        medicalLeaves: List<MedicalLeave> = emptyList(),
        futureEvent: NextEventResult = futureEvent(now),
    ): TodayCardProjection = projectTodayCard(
        now = now,
        zoneId = zoneId,
        todayShifts = todayShifts,
        previousDayCandidates = previousDayCandidates,
        actualsByShiftId = actualsByShiftId,
        vacations = vacations,
        medicalLeaves = medicalLeaves,
        futureEvent = futureEvent,
    )

    private fun futureEvent(
        now: Instant,
        shifts: List<Shift> = emptyList(),
        statuses: List<ExplicitDayStatus> = emptyList(),
        vacations: List<Vacation> = emptyList(),
        medicalLeaves: List<MedicalLeave> = emptyList(),
    ): NextEventResult = projectNextEvent(
        now = now,
        zoneId = zone,
        shifts = shifts,
        explicitDayStatuses = statuses,
        vacations = vacations,
        medicalLeaves = medicalLeaves,
    )

    private fun shiftAt(
        id: String,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        status: ShiftStatus = ShiftStatus.PLANNED,
    ): Shift {
        val startAt = instant(date, start)
        val endDate = if (end <= start) date.plusDays(1) else date
        val endAt = instant(endDate, end)
        return Shift(
            id = UUID.fromString(id),
            startAt = startAt,
            endAt = endAt,
            zoneId = zone,
            localStartDate = date,
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "FIC",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = start,
            endTimeSnapshot = end,
            colorArgbSnapshot = 0xFF315DA8.toInt(),
            position = null,
            status = status,
            sourceObjectiveId = UUID.fromString("f0000000-0000-0000-0000-000000000001"),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    private fun actual(
        shift: Shift,
        start: Instant,
        end: Instant,
    ): ShiftActualAggregate = ShiftActualAggregate(
        record = ShiftActualRecord(
            shiftId = shift.id,
            timelineId = UUID.fromString("f0000000-0000-0000-0000-000000000002"),
            sector = WorkSector.PRIVATE_SECURITY,
            actualStart = start,
            actualEnd = end,
            differenceReason = "Horario real confirmado",
            explanation = "Texto privado que la proyección no expone",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ),
        extraIntervals = emptyList(),
    )

    private fun vacation(start: LocalDate, end: LocalDate): Vacation = Vacation(
        id = UUID.fromString("f0000000-0000-0000-0000-000000000003"),
        startDate = start,
        endDateInclusive = end,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun medicalLeave(start: LocalDate, end: LocalDate): MedicalLeave = MedicalLeave(
        id = UUID.fromString("f0000000-0000-0000-0000-000000000004"),
        startDate = start,
        endDateInclusive = end,
        privateNote = "Nota médica privada que la proyección no expone",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun instant(date: LocalDate, time: LocalTime): Instant =
        ZonedDateTime.of(date, time, zone).toInstant()
}
