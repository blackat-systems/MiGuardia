package com.blackatsystems.miguardia.core.domain.nextevent

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEventTest {
    @Test
    fun `manual and materialized shifts use their complete historical snapshots exactly once`() {
        val manual = testWrite(
            id = 1,
            start = "2028-02-29T22:00:00Z",
            end = "2028-03-01T10:00:00Z",
            sector = WorkSector.MEDICINE,
            workType = "Consultorio",
            place = "Hospital ficticio",
            abbreviation = "HFI",
            position = "Guardia central",
        )
        val materializedRecurrence = testWrite(
            id = 17,
            start = "2028-03-01T12:00:00Z",
            end = "2028-03-01T20:00:00Z",
            sector = WorkSector.NURSING,
            workType = "Turno de internación",
            place = "Clínica ficticia",
            abbreviation = "CLI",
        )
        val result = project(
            now = "2028-02-29T20:00:00Z",
            input = testInput(
                shifts = listOf(manual, manual, materializedRecurrence, materializedRecurrence),
            ),
        )

        assertEquals(NextEventPrimary.UPCOMING_SHIFT, result.primaryEvent)
        assertEquals(2, result.events.size)
        assertEquals(2, result.events.map { it.identity }.distinct().size)
        val event = result.events.filterIsInstance<NextEventItem.Shift>()
            .single { it.shiftId == manual.shift.id }
        assertEquals(manual.shift.id, event.shiftId)
        assertEquals(WorkSector.MEDICINE, event.sector)
        assertEquals("Consultorio", event.workTypeNameSnapshot)
        assertEquals("Hospital ficticio", event.placeNameSnapshot)
        assertEquals("HFI", event.placeAbbreviationSnapshot)
        assertEquals(manual.shift.startTimeSnapshot, event.startTimeSnapshot)
        assertEquals(manual.shift.endTimeSnapshot, event.endTimeSnapshot)
        assertEquals("Guardia central", event.positionSnapshot)
        assertTrue(event.hasHistoricalAddress)
    }

    @Test
    fun `edit replaces the snapshot and deletion removes the candidate`() {
        val original = testWrite(18, "2027-02-03T10:00:00Z", "2027-02-03T14:00:00Z")
        val edited = original.copy(
            shift = original.shift.copy(
                startAt = Instant.parse("2027-02-03T11:00:00Z"),
                endAt = Instant.parse("2027-02-03T15:00:00Z"),
                objectiveNameSnapshot = "Lugar editado ficticio",
                updatedAt = original.shift.updatedAt.plusMillis(1),
            ),
            snapshot = original.snapshot.copy(workTypeNameSnapshot = "Tipo editado ficticio"),
        )

        val before = project("2027-02-03T08:00:00Z", testInput(shifts = listOf(original)))
        val afterEdit = project("2027-02-03T08:00:00Z", testInput(shifts = listOf(edited)))
        val afterDelete = project("2027-02-03T08:00:00Z", testInput())

        assertEquals(Instant.parse("2027-02-03T10:00:00Z"), before.events.single().start)
        assertEquals(Instant.parse("2027-02-03T11:00:00Z"), afterEdit.events.single().start)
        assertEquals(
            "Lugar editado ficticio",
            (afterEdit.events.single() as NextEventItem.Shift).placeNameSnapshot,
        )
        assertEquals("Tipo editado ficticio", (afterEdit.events.single() as NextEventItem.Shift).workTypeNameSnapshot)
        assertTrue(afterDelete.events.isEmpty())
    }

    @Test
    fun `availability keeps each exact historical label`() {
        listOf("Guardia pasiva", "Disponible para llamado", "Retén")
            .forEachIndexed { index, label ->
                val event = project(
                    "2027-02-04T08:00:00Z",
                    testInput(
                        availability = listOf(
                            testAvailability(
                                id = 120 + index,
                                start = "2027-02-04T10:00:00Z",
                                end = "2027-02-04T14:00:00Z",
                                label = label,
                            ),
                        ),
                    ),
                ).events.single() as NextEventItem.Availability

                assertEquals(label, event.labelSnapshot)
            }
    }

    @Test
    fun `active shift wins over active availability while both remain observable`() {
        val shift = testWrite(2, "2027-01-01T10:00:00Z", "2027-01-01T14:00:00Z")
        val availability = testAvailability(
            102,
            "2027-01-01T09:00:00Z",
            "2027-01-01T16:00:00Z",
            sector = WorkSector.MEDICINE,
        )
        val result = project(
            now = "2027-01-01T12:00:00Z",
            input = testInput(shifts = listOf(shift), availability = listOf(availability)),
        )

        assertEquals(NextEventPrimary.ONGOING_SHIFT, result.primaryEvent)
        assertTrue(result.primaryEvents.single() is NextEventItem.Shift)
        assertEquals(2, result.activeEvents.size)
        assertTrue(result.activeEvents.any { it is NextEventItem.Availability })
    }

    @Test
    fun `future tie keeps shift first and preserves simultaneous availability`() {
        val start = "2027-01-02T10:00:00Z"
        val shift = testWrite(3, start, "2027-01-02T14:00:00Z")
        val availability = testAvailability(
            103,
            start,
            "2027-01-02T12:00:00Z",
            sector = WorkSector.MEDICINE,
        )
        val result = project(
            now = "2027-01-02T08:00:00Z",
            input = testInput(shifts = listOf(shift), availability = listOf(availability)),
        )

        assertEquals(NextEventPrimary.UPCOMING_SHIFT, result.primaryEvent)
        assertEquals(2, result.upcomingEvents.size)
        assertTrue(result.upcomingEvents[0] is NextEventItem.Shift)
        assertTrue(result.upcomingEvents[1] is NextEventItem.Availability)
    }

    @Test
    fun `fully replaced availability disappears`() {
        val window = testAvailability(104, "2027-01-03T10:00:00Z", "2027-01-03T18:00:00Z")
        val shift = testWrite(4, "2027-01-03T09:00:00Z", "2027-01-03T19:00:00Z")

        val result = project(
            now = "2027-01-03T08:00:00Z",
            input = testInput(shifts = listOf(shift), availability = listOf(window)),
        )

        assertFalse(result.events.any { it is NextEventItem.Availability })
    }

    @Test
    fun `split availability retains effective bounds and marks only later segment as resumption`() {
        val window = testAvailability(105, "2027-01-04T08:00:00Z", "2027-01-04T20:00:00Z")
        val shift = testWrite(5, "2027-01-04T12:00:00Z", "2027-01-04T14:00:00Z")

        val availability = project(
            now = "2027-01-04T07:00:00Z",
            input = testInput(shifts = listOf(shift), availability = listOf(window)),
        ).events.filterIsInstance<NextEventItem.Availability>()

        assertEquals(2, availability.size)
        assertEquals(Instant.parse("2027-01-04T08:00:00Z"), availability[0].start)
        assertEquals(Instant.parse("2027-01-04T12:00:00Z"), availability[0].end)
        assertFalse(availability[0].isResumption)
        assertEquals(Instant.parse("2027-01-04T14:00:00Z"), availability[1].start)
        assertEquals(Instant.parse("2027-01-04T20:00:00Z"), availability[1].end)
        assertTrue(availability[1].isResumption)
    }

    @Test
    fun `initial occupied portion moves effective start without inventing a resumption`() {
        val window = testAvailability(106, "2027-01-05T08:00:00Z", "2027-01-05T20:00:00Z")
        val shift = testWrite(6, "2027-01-05T08:00:00Z", "2027-01-05T12:00:00Z")

        val event = project(
            now = "2027-01-05T07:00:00Z",
            input = testInput(shifts = listOf(shift), availability = listOf(window)),
        ).events.filterIsInstance<NextEventItem.Availability>().single()

        assertEquals(Instant.parse("2027-01-05T12:00:00Z"), event.start)
        assertFalse(event.isResumption)
    }

    @Test
    fun `vacation and medical leave protect shifts and availability without exposing causes`() {
        val vacationShift = testWrite(7, "2027-01-06T10:00:00Z", "2027-01-06T14:00:00Z")
        val medicalShift = testWrite(8, "2027-01-07T10:00:00Z", "2027-01-07T14:00:00Z")
        val windows = listOf(
            testAvailability(107, "2027-01-06T08:00:00Z", "2027-01-06T20:00:00Z"),
            testAvailability(108, "2027-01-07T08:00:00Z", "2027-01-07T20:00:00Z"),
        )

        val result = project(
            now = "2027-01-06T07:00:00Z",
            input = testInput(
                shifts = listOf(vacationShift, medicalShift),
                availability = windows,
                vacations = listOf(testVacation(201, "2027-01-06", "2027-01-06")),
                medicalLeaves = listOf(testMedicalLeave(202, "2027-01-07", "2027-01-07")),
            ),
        )

        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `actual time removes planned event and prevents late planned boundaries`() {
        val shift = testWrite(9, "2027-01-08T10:00:00Z", "2027-01-08T14:00:00Z")
        val result = project(
            now = "2027-01-08T12:00:00Z",
            input = testInput(
                shifts = listOf(shift),
                actuals = mapOf(shift.shift.id to testActual(shift, "2027-01-08T09:00:00Z", "2027-01-08T11:00:00Z")),
            ),
        )

        assertTrue(result.events.isEmpty())
        assertEquals(NextEventPrimary.NONE, result.primaryEvent)
    }

    @Test
    fun `cancelled and absent shifts are never candidates`() {
        val cancelled = testWrite(
            10,
            "2027-01-09T10:00:00Z",
            "2027-01-09T14:00:00Z",
            status = ShiftStatus.CANCELLED,
        )
        val absent = testWrite(
            11,
            "2027-01-10T10:00:00Z",
            "2027-01-10T14:00:00Z",
            status = ShiftStatus.ABSENT,
        )

        assertTrue(
            project("2027-01-09T08:00:00Z", testInput(shifts = listOf(cancelled, absent))).events.isEmpty(),
        )
    }

    @Test
    fun `independent extra is not a future event but subtracts effective availability`() {
        val window = testAvailability(109, "2027-01-11T08:00:00Z", "2027-01-11T16:00:00Z")
        val extra = testExtra(301, "2027-01-11T10:00:00Z", "2027-01-11T12:00:00Z")
        val events = project(
            now = "2027-01-11T13:00:00Z",
            input = testInput(availability = listOf(window), extras = listOf(extra)),
        ).events

        assertTrue(events.all { it is NextEventItem.Availability })
        assertEquals(1, events.size)
        val event = events.single() as NextEventItem.Availability
        assertEquals(Instant.parse("2027-01-11T12:00:00Z"), event.start)
        assertTrue(event.isResumption)
    }

    @Test
    fun `half open limits transition from shift to resumed availability exactly at boundary`() {
        val window = testAvailability(110, "2027-01-12T08:00:00Z", "2027-01-12T16:00:00Z")
        val shift = testWrite(12, "2027-01-12T10:00:00Z", "2027-01-12T12:00:00Z")
        val result = project(
            now = "2027-01-12T12:00:00Z",
            input = testInput(shifts = listOf(shift), availability = listOf(window)),
        )

        assertEquals(NextEventPrimary.ONGOING_AVAILABILITY, result.primaryEvent)
        val event = result.primaryEvents.single() as NextEventItem.Availability
        assertEquals(Instant.parse("2027-01-12T12:00:00Z"), event.start)
        assertEquals(Duration.ofHours(4), result.remaining)
    }

    @Test
    fun `explicit day off crosses UTC year using the injected civil zone`() {
        val result = project(
            now = "2028-01-01T01:30:00Z",
            input = testInput(
                statuses = listOf(
                    ExplicitDayStatus(LocalDate.of(2027, 12, 31), ExplicitDayStatusType.DAY_OFF),
                    ExplicitDayStatus(LocalDate.of(2028, 1, 1), ExplicitDayStatusType.DAY_OFF),
                ),
            ),
        )

        assertEquals(NextEventPrimary.DAY_OFF, result.primaryEvent)
        assertEquals(LocalDate.of(2027, 12, 31), result.nextDayOff)
        assertTrue(result.events.isEmpty())
        assertEquals(Duration.ZERO, result.remaining)
    }

    @Test
    fun `leap day change of month and historical sectors keep deterministic order`() {
        val nursing = testWrite(
            13,
            "2028-03-01T02:00:00Z",
            "2028-03-01T06:00:00Z",
            sector = WorkSector.NURSING,
        )
        val police = testWrite(
            14,
            "2028-03-01T02:00:00Z",
            "2028-03-01T06:00:00Z",
            sector = WorkSector.POLICE,
        )

        val result = project(
            now = "2028-02-29T23:00:00Z",
            input = testInput(shifts = listOf(police, nursing)),
        )

        assertEquals(listOf(nursing.shift.id, police.shift.id).sorted(), result.upcomingEvents.map {
            (it as NextEventItem.Shift).shiftId
        }.sorted())
        assertEquals(result.upcomingEvents.sortedWith(NextEventItemOrder), result.upcomingEvents)
    }

    @Test
    fun `outputs and copied inputs remain immutable`() {
        val mutableShifts = mutableListOf(testWrite(15, "2027-02-01T10:00:00Z", "2027-02-01T14:00:00Z"))
        val result = project("2027-02-01T08:00:00Z", testInput(shifts = mutableShifts))
        mutableShifts.clear()

        assertEquals(1, result.events.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.events as MutableList<NextEventItem>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (result.primaryEvents as MutableList<NextEventItem>).clear()
        }
    }

    @Test
    fun `typed identities do not collide and legacy uuid becomes shift`() {
        val id = testUuid(16)
        val shift = NextEventIdentity.Shift(id)
        val availability = NextEventIdentity.Availability(
            windowId = id,
            segmentStart = Instant.parse("2027-01-01T10:00:00Z"),
            segmentEnd = Instant.parse("2027-01-01T11:00:00Z"),
        )

        assertFalse(shift.trackingKey == availability.trackingKey)
        assertEquals(shift, NextEventIdentity.parseTrackingKey(id.toString()))
        assertEquals(availability, NextEventIdentity.parseTrackingKey(availability.trackingKey))
        assertNull(NextEventIdentity.parseTrackingKey("invalid"))
    }

    private fun project(now: String, input: NextEventInput): NextEventResult = projectNextEvent(
        now = Instant.parse(now),
        zoneId = TestZone,
        input = input,
    )
}
