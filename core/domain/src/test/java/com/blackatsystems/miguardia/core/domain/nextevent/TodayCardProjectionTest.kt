package com.blackatsystems.miguardia.core.domain.nextevent

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayCardProjectionTest {
    @Test
    fun `ongoing shift uses the shared projection and safe historical row`() {
        val shift = testWrite(401, "2027-03-01T10:00:00Z", "2027-03-01T14:00:00Z")
        val card = card("2027-03-01T12:00:00Z", testInput(shifts = listOf(shift)))

        assertEquals(TodayCardPrimary.ONGOING_SHIFT, card.primary)
        assertEquals(shift.shift.id, card.primaryShift?.event?.shiftId)
        assertEquals("Lugar ficticio", card.primaryShift?.event?.placeNameSnapshot)
        assertEquals(Duration.ofHours(2), card.remaining)
    }

    @Test
    fun `overnight active shift remains visible after its owner date`() {
        val shift = testWrite(402, "2027-03-02T23:00:00Z", "2027-03-03T05:00:00Z")
        val card = card("2027-03-03T03:00:00Z", testInput(shifts = listOf(shift)))

        assertEquals(TodayCardPrimary.ONGOING_SHIFT, card.primary)
        assertTrue(card.primaryShift?.startedBeforeToday == true)
        assertEquals(shift.shift.id, card.shifts.single().event.shiftId)
    }

    @Test
    fun `upcoming shift owned today is primary even when another row completed`() {
        val completed = testWrite(403, "2027-03-04T08:00:00Z", "2027-03-04T10:00:00Z")
        val upcoming = testWrite(404, "2027-03-04T14:00:00Z", "2027-03-04T18:00:00Z")
        val card = card(
            "2027-03-04T12:00:00Z",
            testInput(shifts = listOf(completed, upcoming)),
        )

        assertEquals(TodayCardPrimary.UPCOMING_SHIFT, card.primary)
        assertEquals(upcoming.shift.id, card.primaryShift?.event?.shiftId)
        assertEquals(2, card.todayShiftCount)
        assertEquals(1, card.completedTodayCount)
    }

    @Test
    fun `future availability uses shared future content without incrementing shift count`() {
        val availability = testAvailability(405, "2027-03-05T14:00:00Z", "2027-03-05T18:00:00Z")
        val card = card("2027-03-05T12:00:00Z", testInput(availability = listOf(availability)))

        assertEquals(TodayCardPrimary.FUTURE_EVENT, card.primary)
        assertEquals(NextEventPrimary.UPCOMING_AVAILABILITY, card.futureEvent.primaryEvent)
        assertEquals(0, card.todayShiftCount)
        assertNull(card.primaryShift)
    }

    @Test
    fun `completed row remains the summary when the next event belongs to a future day`() {
        val completed = testWrite(406, "2027-03-06T08:00:00Z", "2027-03-06T10:00:00Z")
        val tomorrow = testWrite(430, "2027-03-07T08:00:00Z", "2027-03-07T10:00:00Z")
        val card = card(
            "2027-03-06T12:00:00Z",
            testInput(shifts = listOf(completed, tomorrow)),
        )

        assertEquals(TodayCardPrimary.COMPLETED_SUMMARY, card.primary)
        assertEquals(1, card.completedTodayCount)
        assertTrue(card.canExpand)
        assertEquals(NextEventPrimary.UPCOMING_SHIFT, card.futureEvent.primaryEvent)
    }

    @Test
    fun `cancelled absent and protected rows remain visual but never become active events`() {
        val cancelled = testWrite(
            407,
            "2027-03-07T14:00:00Z",
            "2027-03-07T18:00:00Z",
            status = ShiftStatus.CANCELLED,
        )
        val absent = testWrite(
            408,
            "2027-03-07T18:00:00Z",
            "2027-03-07T22:00:00Z",
            status = ShiftStatus.ABSENT,
        )
        val protected = testWrite(409, "2027-03-07T22:00:00Z", "2027-03-08T02:00:00Z")
        val card = card(
            "2027-03-07T12:00:00Z",
            testInput(
                shifts = listOf(cancelled, absent, protected),
                medicalLeaves = listOf(testMedicalLeave(410, "2027-03-07", "2027-03-07")),
            ),
        )

        assertEquals(TodayCardPrimary.NO_WORK_TODAY, card.primary)
        assertEquals(
            listOf(TodayShiftState.CANCELLED, TodayShiftState.ABSENT, TodayShiftState.PROTECTED),
            card.shifts.map(TodayShiftSummary::state),
        )
        assertEquals(NextEventPrimary.NONE, card.futureEvent.primaryEvent)
    }

    @Test
    fun `actual interval labels completed row but never rewrites planned snapshot`() {
        val shift = testWrite(411, "2027-03-08T10:00:00Z", "2027-03-08T14:00:00Z")
        val actual = testActual(shift, "2027-03-08T09:00:00Z", "2027-03-08T11:00:00Z")
        val card = card(
            "2027-03-08T12:00:00Z",
            testInput(shifts = listOf(shift), actuals = mapOf(shift.shift.id to actual)),
        )

        val row = card.shifts.single()
        assertEquals(TodayShiftState.COMPLETED, row.state)
        assertTrue(row.hasActualTime)
        assertEquals(shift.shift.startAt, row.event.start)
        assertEquals(shift.shift.endAt, row.event.end)
        assertEquals(NextEventPrimary.NONE, card.futureEvent.primaryEvent)
    }

    @Test
    fun `projection rejects a different instant or zone`() {
        val input = testInput()
        val future = projectNextEvent(
            now = Instant.parse("2027-03-09T12:00:00Z"),
            zoneId = TestZone,
            input = input,
        )

        assertThrows(IllegalArgumentException::class.java) {
            projectTodayCard(
                now = Instant.parse("2027-03-09T12:01:00Z"),
                zoneId = TestZone,
                shifts = emptyList(),
                actualsByShiftId = emptyMap(),
                vacations = emptyList(),
                medicalLeaves = emptyList(),
                futureEvent = future,
            )
        }
    }

    @Test
    fun `today rows are immutable and input mutations do not leak`() {
        val shift = testWrite(412, "2027-03-10T14:00:00Z", "2027-03-10T18:00:00Z")
        val mutable = mutableListOf(shift)
        val input = testInput(shifts = mutable)
        val card = card("2027-03-10T12:00:00Z", input)
        mutable.clear()

        assertEquals(1, card.shifts.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (card.shifts as MutableList<TodayShiftSummary>).clear()
        }
    }

    @Test
    fun `ongoing shift keeps priority over an upcoming shift`() {
        val ongoing = testWrite(413, "2027-03-11T10:00:00Z", "2027-03-11T14:00:00Z")
        val upcoming = testWrite(414, "2027-03-11T16:00:00Z", "2027-03-11T20:00:00Z")

        val card = card("2027-03-11T12:00:00Z", testInput(shifts = listOf(upcoming, ongoing)))

        assertEquals(TodayCardPrimary.ONGOING_SHIFT, card.primary)
        assertEquals(ongoing.shift.id, card.primaryShift?.event?.shiftId)
        assertEquals(2, card.todayShiftCount)
    }

    @Test
    fun `today shifts keep stable start end and uuid order`() {
        val laterEnd = testWrite(415, "2027-03-12T10:00:00Z", "2027-03-12T15:00:00Z")
        val earlierEnd = testWrite(416, "2027-03-12T10:00:00Z", "2027-03-12T14:00:00Z")
        val laterStart = testWrite(417, "2027-03-12T16:00:00Z", "2027-03-12T20:00:00Z")

        val first = card(
            "2027-03-12T08:00:00Z",
            testInput(shifts = listOf(laterStart, laterEnd, earlierEnd)),
        )
        val second = card(
            "2027-03-12T08:00:00Z",
            testInput(shifts = listOf(earlierEnd, laterEnd, laterStart)),
        )

        val expected = listOf(earlierEnd.shift.id, laterEnd.shift.id, laterStart.shift.id)
        assertEquals(expected, first.shifts.map { it.event.shiftId })
        assertEquals(expected, second.shifts.map { it.event.shiftId })
    }

    @Test
    fun `simultaneous ongoing shifts remain separate in the shared result`() {
        val first = testWrite(418, "2027-03-13T10:00:00Z", "2027-03-13T14:00:00Z")
        val second = testWrite(419, "2027-03-13T10:00:00Z", "2027-03-13T16:00:00Z")

        val card = card("2027-03-13T12:00:00Z", testInput(shifts = listOf(second, first)))

        assertEquals(2, card.futureEvent.primaryEvents.size)
        assertEquals(2, card.shifts.size)
        assertEquals(first.shift.id, card.primaryShift?.event?.shiftId)
    }

    @Test
    fun `vacation and medical leave keep their distinct historical flags`() {
        val vacationShift = testWrite(420, "2027-03-14T14:00:00Z", "2027-03-14T18:00:00Z")
        val medicalShift = testWrite(421, "2027-03-15T14:00:00Z", "2027-03-15T18:00:00Z")

        val vacationCard = card(
            "2027-03-14T12:00:00Z",
            testInput(
                shifts = listOf(vacationShift),
                vacations = listOf(testVacation(422, "2027-03-14", "2027-03-14")),
            ),
        )
        val medicalCard = card(
            "2027-03-15T12:00:00Z",
            testInput(
                shifts = listOf(medicalShift),
                medicalLeaves = listOf(testMedicalLeave(423, "2027-03-15", "2027-03-15")),
            ),
        )

        assertTrue(vacationCard.shifts.single().isVacationProtected)
        assertFalse(vacationCard.shifts.single().isMedicalLeaveProtected)
        assertFalse(medicalCard.shifts.single().isVacationProtected)
        assertTrue(medicalCard.shifts.single().isMedicalLeaveProtected)
    }

    @Test
    fun `confirmed actual time remains visible when the owner date is protected`() {
        val shift = testWrite(424, "2027-03-16T14:00:00Z", "2027-03-16T18:00:00Z")
        val actual = testActual(shift, "2027-03-16T08:00:00Z", "2027-03-16T11:00:00Z")
        val card = card(
            "2027-03-16T12:00:00Z",
            testInput(
                shifts = listOf(shift),
                actuals = mapOf(shift.shift.id to actual),
                medicalLeaves = listOf(testMedicalLeave(425, "2027-03-16", "2027-03-16")),
            ),
        )

        val row = card.shifts.single()
        assertEquals(TodayShiftState.COMPLETED, row.state)
        assertTrue(row.hasActualTime)
        assertTrue(row.isMedicalLeaveProtected)
        assertEquals(NextEventPrimary.NONE, card.futureEvent.primaryEvent)
    }

    @Test
    fun `explicit day off never displaces a priority work event`() {
        val shift = testWrite(426, "2027-03-17T14:00:00Z", "2027-03-17T18:00:00Z")
        val dayOff = ExplicitDayStatus(LocalDate.of(2027, 3, 17), ExplicitDayStatusType.DAY_OFF)

        val withShift = card(
            "2027-03-17T12:00:00Z",
            testInput(shifts = listOf(shift), statuses = listOf(dayOff)),
        )
        val withoutShift = card(
            "2027-03-17T12:00:00Z",
            testInput(statuses = listOf(dayOff)),
        )

        assertEquals(TodayCardPrimary.UPCOMING_SHIFT, withShift.primary)
        assertEquals(TodayCardPrimary.FUTURE_EVENT, withoutShift.primary)
        assertEquals(NextEventPrimary.DAY_OFF, withoutShift.futureEvent.primaryEvent)
    }

    @Test
    fun `undefined and empty civil days are not day off`() {
        val undefined = ExplicitDayStatus(LocalDate.of(2027, 3, 18), ExplicitDayStatusType.UNDEFINED)

        val undefinedCard = card(
            "2027-03-18T12:00:00Z",
            testInput(statuses = listOf(undefined)),
        )
        val emptyCard = card("2027-03-18T12:00:00Z", testInput())

        assertEquals(TodayCardPrimary.EMPTY, undefinedCard.primary)
        assertNull(undefinedCard.futureEvent.nextDayOff)
        assertEquals(TodayCardPrimary.EMPTY, emptyCard.primary)
        assertNull(emptyCard.futureEvent.nextDayOff)
    }

    @Test
    fun `shift start is inclusive and end is exclusive`() {
        val shift = testWrite(427, "2027-03-19T12:00:00Z", "2027-03-19T16:00:00Z")

        val atStart = card("2027-03-19T12:00:00Z", testInput(shifts = listOf(shift)))
        val atEnd = card("2027-03-19T16:00:00Z", testInput(shifts = listOf(shift)))

        assertEquals(TodayCardPrimary.ONGOING_SHIFT, atStart.primary)
        assertEquals(TodayShiftState.IN_PROGRESS, atStart.shifts.single().state)
        assertEquals(TodayCardPrimary.COMPLETED_SUMMARY, atEnd.primary)
        assertEquals(TodayShiftState.COMPLETED, atEnd.shifts.single().state)
    }

    @Test
    fun `month year and leap boundaries retain the active overnight shift`() {
        listOf(
            LocalDate.of(2027, 8, 31),
            LocalDate.of(2027, 12, 31),
            LocalDate.of(2028, 2, 29),
        ).forEachIndexed { index, ownerDate ->
            val start = ZonedDateTime.of(ownerDate, LocalTime.of(23, 0), TestZone).toInstant()
            val end = ZonedDateTime.of(ownerDate.plusDays(1), LocalTime.of(2, 0), TestZone).toInstant()
            val shift = testWrite(428 + index, start.toString(), end.toString())
            val afterMidnight = ZonedDateTime.of(
                ownerDate.plusDays(1),
                LocalTime.MIDNIGHT,
                TestZone,
            ).toInstant()

            val card = card(afterMidnight, TestZone, testInput(shifts = listOf(shift)))

            assertEquals(ownerDate.plusDays(1), card.date)
            assertEquals(TodayCardPrimary.ONGOING_SHIFT, card.primary)
            assertTrue(card.shifts.single().startedBeforeToday)
            assertEquals(0, card.todayShiftCount)
        }
    }

    @Test
    fun `injected zone owns the civil date independently from another machine zone`() {
        val reference = Instant.parse("2028-01-01T01:30:00Z")
        val shift = testWrite(431, "2028-01-01T01:00:00Z", "2028-01-01T04:00:00Z")

        val cordoba = card(reference, TestZone, testInput(shifts = listOf(shift)))
        val utc = card(reference, ZoneId.of("UTC"), testInput(shifts = listOf(shift)))

        assertEquals(LocalDate.of(2027, 12, 31), cordoba.date)
        assertEquals(LocalDate.of(2028, 1, 1), utc.date)
        assertEquals(1, cordoba.todayShiftCount)
        assertEquals(0, utc.todayShiftCount)
        assertEquals(TodayCardPrimary.ONGOING_SHIFT, cordoba.primary)
        assertEquals(TodayCardPrimary.ONGOING_SHIFT, utc.primary)
    }

    private fun card(now: String, input: NextEventInput): TodayCardProjection {
        return card(Instant.parse(now), TestZone, input)
    }

    private fun card(
        now: Instant,
        zoneId: ZoneId,
        input: NextEventInput,
    ): TodayCardProjection {
        val future = projectNextEvent(now, zoneId, input)
        return projectTodayCard(
            now = now,
            zoneId = zoneId,
            shifts = input.shifts,
            actualsByShiftId = input.actualsByShiftId,
            vacations = input.vacations,
            medicalLeaves = input.medicalLeaves,
            futureEvent = future,
        )
    }
}
