package com.blackatsystems.miguardia.core.domain.notification

import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.validateReminderLeadMinutes
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.TestZone
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.nextevent.testAvailability
import com.blackatsystems.miguardia.core.domain.nextevent.testInput
import com.blackatsystems.miguardia.core.domain.nextevent.testWrite
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftNotificationPlanTest {
    private val now = Instant.parse("2027-01-01T12:00:00Z")

    @Test
    fun `validates zero through five unique positive reminders`() {
        assertEquals(emptyList<Long>(), validateReminderLeadMinutes(emptyList()))
        assertEquals(listOf(360L), validateReminderLeadMinutes(listOf(360L)))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), validateReminderLeadMinutes(listOf(5L, 4L, 3L, 2L, 1L)))
    }

    @Test
    fun `rejects six duplicate zero and negative reminders`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateReminderLeadMinutes(listOf(1L, 2L, 3L, 4L, 5L, 6L))
        }
        assertThrows(IllegalArgumentException::class.java) { validateReminderLeadMinutes(listOf(60L, 60L)) }
        assertThrows(IllegalArgumentException::class.java) { validateReminderLeadMinutes(listOf(0L)) }
        assertThrows(IllegalArgumentException::class.java) { validateReminderLeadMinutes(listOf(-1L)) }
        assertThrows(IllegalArgumentException::class.java) {
            validateReminderLeadMinutes(listOf(Long.MAX_VALUE))
        }
    }

    @Test
    fun `future shift produces reminder start and end exact boundaries`() {
        val shift = testWrite(501, "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val plan = plan(
            projection = projection(shifts = listOf(shift)),
            global = listOf(360L),
        )

        assertEquals(
            listOf(
                Instant.parse("2027-01-01T13:00:00Z"),
                shift.shift.startAt,
                shift.shift.endAt,
            ),
            plan.boundaries.map { it.identity.triggerAt },
        )
        assertEquals(
            listOf(NotificationBoundaryType.REMINDER, NotificationBoundaryType.START, NotificationBoundaryType.END),
            plan.boundaries.map { it.identity.type },
        )
    }

    @Test
    fun `zero global reminders keep start and end for shift and availability`() {
        val shift = testWrite(502, "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val availability = testAvailability(503, "2027-01-02T10:00:00Z", "2027-01-02T14:00:00Z")
        val plan = plan(
            projection = projection(shifts = listOf(shift), availability = listOf(availability)),
            global = emptyList(),
        )

        assertEquals(4, plan.boundaries.size)
        assertTrue(plan.boundaries.none { it.identity.type == NotificationBoundaryType.REMINDER })
    }

    @Test
    fun `shift override replaces globals while availability keeps globals`() {
        val shift = testWrite(504, "2027-01-02T12:00:00Z", "2027-01-02T16:00:00Z")
        val availability = testAvailability(505, "2027-01-03T12:00:00Z", "2027-01-03T16:00:00Z")
        val plan = plan(
            projection = projection(shifts = listOf(shift), availability = listOf(availability)),
            global = listOf(720L),
            overrides = listOf(ShiftNotificationConfig(shift.shift.id, listOf(60L))),
        )

        val reminders = plan.boundaries.filter { it.identity.type == NotificationBoundaryType.REMINDER }
        assertEquals(2, reminders.size)
        assertEquals(60L, reminders.single { it.event is NextEventItem.Shift }.identity.leadMinutes)
        assertEquals(720L, reminders.single { it.event is NextEventItem.Availability }.identity.leadMinutes)
    }

    @Test
    fun `empty shift exception disables its full plan but not availability`() {
        val shift = testWrite(506, "2027-01-02T12:00:00Z", "2027-01-02T16:00:00Z")
        val availability = testAvailability(507, "2027-01-03T12:00:00Z", "2027-01-03T16:00:00Z")
        val plan = plan(
            projection = projection(shifts = listOf(shift), availability = listOf(availability)),
            overrides = listOf(ShiftNotificationConfig(shift.shift.id, emptyList())),
        )

        assertTrue(plan.boundaries.all { it.event is NextEventItem.Availability })
    }

    @Test
    fun `availability resumption has silent structural boundaries without repeated reminder`() {
        val window = testAvailability(508, "2027-01-02T08:00:00Z", "2027-01-02T20:00:00Z")
        val shift = testWrite(509, "2027-01-02T12:00:00Z", "2027-01-02T14:00:00Z")
        val projection = projection(shifts = listOf(shift), availability = listOf(window))
        val resumptions = projection.events
            .filterIsInstance<NextEventItem.Availability>()
            .filter(NextEventItem.Availability::isResumption)
        val plan = plan(projection, global = listOf(60L))
        val resumptionKeys = resumptions.map { it.identity.trackingKey }.toSet()
        val resumptionBoundaries = plan.boundaries.filter {
            it.identity.eventIdentity.trackingKey in resumptionKeys
        }

        assertEquals(
            listOf(NotificationBoundaryType.START, NotificationBoundaryType.END),
            resumptionBoundaries.map { it.identity.type },
        )
    }

    @Test
    fun `past reminders are omitted without late scheduling`() {
        val shift = testWrite(510, "2027-01-01T13:00:00Z", "2027-01-01T21:00:00Z")
        val plan = plan(projection(shifts = listOf(shift)), global = listOf(720L))

        assertEquals(
            listOf(NotificationBoundaryType.START, NotificationBoundaryType.END),
            plan.boundaries.map { it.identity.type },
        )
    }

    @Test
    fun `reminder exactly at the reference instant remains valid`() {
        val shift = testWrite(518, "2027-01-02T00:00:00Z", "2027-01-02T08:00:00Z")
        val plan = plan(projection(shifts = listOf(shift)), global = listOf(720L))

        assertEquals(now, plan.boundaries.first().identity.triggerAt)
        assertEquals(NotificationBoundaryType.REMINDER, plan.boundaries.first().identity.type)
    }

    @Test
    fun `disabled notifications produce no boundaries for either event type`() {
        val shift = testWrite(519, "2027-01-02T10:00:00Z", "2027-01-02T14:00:00Z")
        val availability = testAvailability(520, "2027-01-03T10:00:00Z", "2027-01-03T14:00:00Z")

        val disabled = buildNotificationPlan(
            now = now,
            notificationsEnabled = false,
            globalReminderLeadMinutes = listOf(60L),
            projection = projection(shifts = listOf(shift), availability = listOf(availability)),
            shiftOverrides = emptyList(),
        )

        assertTrue(disabled.boundaries.isEmpty())
    }

    @Test
    fun `temporal edit and deletion expose only obsolete installed boundaries`() {
        val original = testWrite(521, "2027-01-02T10:00:00Z", "2027-01-02T14:00:00Z")
        val edited = original.copy(
            shift = original.shift.copy(
                startAt = original.shift.startAt.plusSeconds(3_600),
                endAt = original.shift.endAt.plusSeconds(3_600),
            ),
        )
        val originalPlan = plan(projection(shifts = listOf(original)), global = listOf(60L))
        val installed = originalPlan.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey }

        val afterEdit = reconcileNotificationPlan(
            installed,
            plan(projection(shifts = listOf(edited)), global = listOf(60L)),
        )
        val afterDelete = reconcileNotificationPlan(
            installed,
            plan(projection(), global = listOf(60L)),
        )

        assertEquals(installed, afterEdit.cancelOpaqueKeys)
        assertTrue(afterEdit.scheduleBoundaries.isNotEmpty())
        assertEquals(installed, afterDelete.cancelOpaqueKeys)
        assertTrue(afterDelete.scheduleBoundaries.isEmpty())
    }

    @Test
    fun `ongoing events retain only future end boundaries`() {
        val shift = testWrite(511, "2027-01-01T10:00:00Z", "2027-01-01T14:00:00Z")
        val availability = testAvailability(
            512,
            "2027-01-01T09:00:00Z",
            "2027-01-01T18:00:00Z",
            sector = WorkSector.MEDICINE,
        )
        val plan = plan(projection(shifts = listOf(shift), availability = listOf(availability)))

        assertTrue(plan.boundaries.all { it.identity.type == NotificationBoundaryType.END })
        assertEquals(2, plan.boundaries.size)
    }

    @Test
    fun `card primary and notification plan share the exact same event identity`() {
        val shift = testWrite(513, "2027-01-02T10:00:00Z", "2027-01-02T14:00:00Z")
        val availability = testAvailability(
            514,
            "2027-01-02T10:00:00Z",
            "2027-01-02T16:00:00Z",
            sector = WorkSector.MEDICINE,
        )
        val projection = projection(shifts = listOf(shift), availability = listOf(availability))
        val plan = plan(projection)

        assertEquals(NextEventPrimary.UPCOMING_SHIFT, projection.primaryEvent)
        val startIdentities = plan.boundaries.filter {
            it.identity.type == NotificationBoundaryType.START &&
                it.identity.triggerAt == projection.primaryEvents.first().start
        }.map { it.identity.eventIdentity }.toSet()
        assertEquals(projection.primaryEvents.map { it.identity }.toSet(), startIdentities)
    }

    @Test
    fun `typed opaque identities do not collide`() {
        val shift = testWrite(515, "2027-01-02T10:00:00Z", "2027-01-02T14:00:00Z")
        val availability = testAvailability(515, "2027-01-03T10:00:00Z", "2027-01-03T14:00:00Z")
        val plan = plan(projection(shifts = listOf(shift), availability = listOf(availability)))
        val keys = plan.boundaries.map { it.identity.opaqueKey }

        assertEquals(keys.size, keys.distinct().size)
        assertNotEquals(
            NextEventIdentity.Shift(shift.shift.id).trackingKey,
            (plan.boundaries.first { it.event is NextEventItem.Availability }).identity.eventIdentity.trackingKey,
        )
    }

    @Test
    fun `reconciliation is idempotent and precision change forces replacement`() {
        val shift = testWrite(516, "2027-01-02T10:00:00Z", "2027-01-02T14:00:00Z")
        val desired = plan(projection(shifts = listOf(shift)))
        val installed = desired.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey }

        val stable = reconcileNotificationPlan(installed, desired)
        assertTrue(stable.cancelOpaqueKeys.isEmpty())
        assertTrue(stable.scheduleBoundaries.isEmpty())

        val forced = reconcileNotificationPlan(installed, desired, forceReschedule = true)
        assertEquals(installed, forced.cancelOpaqueKeys)
        assertEquals(desired.boundaries, forced.scheduleBoundaries)
    }

    @Test
    fun `plan output is immutable`() {
        val shift = testWrite(517, "2027-01-02T10:00:00Z", "2027-01-02T14:00:00Z")
        val plan = plan(projection(shifts = listOf(shift)))

        assertThrows(UnsupportedOperationException::class.java) {
            (plan.boundaries as MutableList<NotificationBoundary>).clear()
        }
    }

    @Test
    fun `rolling installation window keeps only the earliest boundaries of a large finite plan`() {
        val shifts = (1..2_000).map { index ->
            val start = now.plusSeconds(index * 86_400L)
            testWrite(
                id = 20_000 + index,
                start = start.toString(),
                end = start.plusSeconds(28_800L).toString(),
            )
        }
        val fullPlan = plan(projection(shifts = shifts), global = listOf(60L))
        val installation = fullPlan.earliestBoundaries(128)

        assertTrue(fullPlan.boundaries.size > 500)
        assertEquals(128, installation.boundaries.size)
        assertEquals(fullPlan.boundaries.take(128), installation.boundaries)
        assertThrows(UnsupportedOperationException::class.java) {
            (installation.boundaries as MutableList<NotificationBoundary>).clear()
        }
    }

    @Test
    fun `content only edit updates the event without replacing alarm identities`() {
        val original = testWrite(522, "2027-01-02T10:00:00Z", "2027-01-02T14:00:00Z")
        val edited = original.copy(shift = original.shift.copy(position = "Puesto editado ficticio"))
        val originalPlan = plan(projection(shifts = listOf(original)))
        val editedPlan = plan(projection(shifts = listOf(edited)))
        val installed = originalPlan.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey }

        val changes = reconcileNotificationPlan(installed, editedPlan)

        assertEquals(installed, editedPlan.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey })
        assertEquals(
            "Puesto editado ficticio",
            (editedPlan.boundaries.first().event as NextEventItem.Shift).positionSnapshot,
        )
        assertTrue(changes.cancelOpaqueKeys.isEmpty())
        assertTrue(changes.scheduleBoundaries.isEmpty())
    }

    @Test
    fun `temporal edit replaces only the edited event boundaries`() {
        val editedTarget = testWrite(523, "2027-01-02T10:00:00Z", "2027-01-02T14:00:00Z")
        val companion = testWrite(524, "2027-01-03T10:00:00Z", "2027-01-03T14:00:00Z")
        val originalPlan = plan(projection(shifts = listOf(editedTarget, companion)))
        val installed = originalPlan.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey }
        val edited = editedTarget.copy(
            shift = editedTarget.shift.copy(
                startAt = editedTarget.shift.startAt.plusSeconds(3_600),
                endAt = editedTarget.shift.endAt.plusSeconds(3_600),
            ),
        )

        val changes = reconcileNotificationPlan(
            installed,
            plan(projection(shifts = listOf(edited, companion))),
        )
        val editedPrefix = "v2|shift:${editedTarget.shift.id}|"

        assertTrue(changes.cancelOpaqueKeys.isNotEmpty())
        assertTrue(changes.cancelOpaqueKeys.all { it.startsWith(editedPrefix) })
        assertTrue(changes.scheduleBoundaries.isNotEmpty())
        assertTrue(changes.scheduleBoundaries.all {
            it.identity.eventIdentity == NextEventIdentity.Shift(editedTarget.shift.id)
        })
    }

    @Test
    fun `deleting one event leaves companion boundaries installed`() {
        val deleted = testWrite(525, "2027-01-02T10:00:00Z", "2027-01-02T14:00:00Z")
        val companion = testWrite(526, "2027-01-03T10:00:00Z", "2027-01-03T14:00:00Z")
        val originalPlan = plan(projection(shifts = listOf(deleted, companion)))
        val installed = originalPlan.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey }

        val changes = reconcileNotificationPlan(
            installed,
            plan(projection(shifts = listOf(companion))),
        )
        val deletedPrefix = "v2|shift:${deleted.shift.id}|"

        assertTrue(changes.cancelOpaqueKeys.isNotEmpty())
        assertTrue(changes.cancelOpaqueKeys.all { it.startsWith(deletedPrefix) })
        assertTrue(changes.scheduleBoundaries.isEmpty())
        assertTrue(installed.any { it.contains("shift:${companion.shift.id}") })
    }

    @Test
    fun `one shift override does not alter a simultaneous companion`() {
        val first = testWrite(527, "2027-01-02T10:00:00Z", "2027-01-02T14:00:00Z")
        val companion = testWrite(528, "2027-01-02T10:00:00Z", "2027-01-02T16:00:00Z")

        val reminders = plan(
            projection = projection(shifts = listOf(companion, first)),
            global = listOf(720L),
            overrides = listOf(ShiftNotificationConfig(first.shift.id, listOf(60L))),
        ).boundaries.filter { it.identity.type == NotificationBoundaryType.REMINDER }

        assertEquals(
            60L,
            reminders.single { it.identity.eventIdentity == NextEventIdentity.Shift(first.shift.id) }
                .identity.leadMinutes,
        )
        assertEquals(
            720L,
            reminders.single { it.identity.eventIdentity == NextEventIdentity.Shift(companion.shift.id) }
                .identity.leadMinutes,
        )
    }

    private fun projection(
        shifts: List<com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite> = emptyList(),
        availability: List<com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord> = emptyList(),
    ) = projectNextEvent(
        now = now,
        zoneId = TestZone,
        input = testInput(shifts = shifts, availability = availability),
    )

    private fun plan(
        projection: com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult,
        global: List<Long> = listOf(360L),
        overrides: List<ShiftNotificationConfig> = emptyList(),
    ): ShiftNotificationPlan = buildNotificationPlan(
        now = now,
        notificationsEnabled = true,
        globalReminderLeadMinutes = global,
        projection = projection,
        shiftOverrides = overrides,
    )
}
