package com.blackatsystems.miguardia.core.domain.notification

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.model.validateReminderLeadMinutes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftNotificationPlanTest {
    private val now = Instant.parse("2026-12-31T12:00:00Z")

    @Test
    fun `validates zero one and five unique reminders`() {
        assertEquals(emptyList<Long>(), validateReminderLeadMinutes(emptyList()))
        assertEquals(listOf(360L), validateReminderLeadMinutes(listOf(360L)))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), validateReminderLeadMinutes(listOf(5L, 4L, 3L, 2L, 1L)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects six reminders`() = Unit.also {
        validateReminderLeadMinutes(listOf(1L, 2L, 3L, 4L, 5L, 6L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects duplicate reminders`() = Unit.also {
        validateReminderLeadMinutes(listOf(60L, 60L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero reminder`() = Unit.also { validateReminderLeadMinutes(listOf(0L)) }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative reminder`() = Unit.also { validateReminderLeadMinutes(listOf(-1L)) }

    @Test
    fun `global reminder produces reminder start and end exact boundaries`() {
        val shift = shift("00000000-0000-0000-0000-000000000001", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val plan = plan(shifts = listOf(shift), global = listOf(360L))

        assertEquals(
            listOf(
                Instant.parse("2027-01-01T13:00:00Z"),
                shift.startAt,
                shift.endAt,
            ),
            plan.boundaries.map { it.identity.triggerAt },
        )
        assertEquals(
            listOf(NotificationBoundaryType.REMINDER, NotificationBoundaryType.START, NotificationBoundaryType.END),
            plan.boundaries.map { it.identity.type },
        )
    }

    @Test
    fun `zero global reminders keep only start and end`() {
        val shift = shift("00000000-0000-0000-0000-000000000014", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        assertEquals(
            listOf(NotificationBoundaryType.START, NotificationBoundaryType.END),
            plan(listOf(shift), global = emptyList()).boundaries.map { it.identity.type },
        )
    }

    @Test
    fun `particular reminders replace globals`() {
        val shift = shift("00000000-0000-0000-0000-000000000002", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val plan = plan(
            shifts = listOf(shift),
            global = listOf(720L),
            overrides = listOf(ShiftNotificationConfig(shift.id, listOf(480L, 1440L))),
        )
        assertEquals(listOf(1440L, 480L), plan.boundaries.mapNotNull { it.identity.leadMinutes })
    }

    @Test
    fun `empty particular config disables all boundaries for that shift`() {
        val shift = shift("00000000-0000-0000-0000-000000000003", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        assertTrue(
            plan(
                shifts = listOf(shift),
                overrides = listOf(ShiftNotificationConfig(shift.id, emptyList())),
            ).boundaries.isEmpty(),
        )
    }

    @Test
    fun `past reminders are omitted without late scheduling`() {
        val shift = shift("00000000-0000-0000-0000-000000000004", "2026-12-31T13:00:00Z", "2026-12-31T21:00:00Z")
        val plan = plan(shifts = listOf(shift), global = listOf(720L))
        assertEquals(listOf(NotificationBoundaryType.START, NotificationBoundaryType.END), plan.boundaries.map { it.identity.type })
    }

    @Test
    fun `reminder exactly at current instant remains an exact boundary`() {
        val shift = shift("00000000-0000-0000-0000-000000000013", "2027-01-01T00:00:00Z", "2027-01-01T12:00:00Z")
        val plan = plan(shifts = listOf(shift), global = listOf(720L))

        assertEquals(now, plan.boundaries.first().identity.triggerAt)
        assertEquals(NotificationBoundaryType.REMINDER, plan.boundaries.first().identity.type)
    }

    @Test
    fun `ongoing overnight shift keeps only future end boundary`() {
        val shift = shift("00000000-0000-0000-0000-000000000005", "2026-12-30T23:00:00Z", "2026-12-31T13:00:00Z")
        val plan = plan(shifts = listOf(shift))
        assertEquals(listOf(NotificationBoundaryType.END), plan.boundaries.map { it.identity.type })
    }

    @Test
    fun `leap day and local zone remain represented by typed instants and dates`() {
        val zone = ZoneId.of("America/Argentina/Cordoba")
        val start = LocalDate.of(2028, 2, 29).atTime(19, 0).atZone(zone).toInstant()
        val end = LocalDate.of(2028, 3, 1).atTime(7, 0).atZone(zone).toInstant()
        val shift = shift("00000000-0000-0000-0000-000000000015", start.toString(), end.toString())
            .copy(
                zoneId = zone,
                localStartDate = LocalDate.of(2028, 2, 29),
                startTimeSnapshot = LocalTime.of(19, 0),
                endTimeSnapshot = LocalTime.of(7, 0),
            )
        val reference = start.minusSeconds(24L * 60L * 60L)
        val plan = buildShiftNotificationPlan(reference, true, listOf(720L), listOf(shift), emptyList(), emptyList())

        assertEquals(start.minusSeconds(12L * 60L * 60L), plan.boundaries.first().identity.triggerAt)
        assertEquals(LocalDate.of(2028, 2, 29), plan.boundaries.first().shift.localStartDate)
    }

    @Test
    fun `vacation cancelled and absent shifts are excluded`() {
        val vacationShift = shift("00000000-0000-0000-0000-000000000006", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val cancelled = shift("00000000-0000-0000-0000-000000000007", "2027-01-02T19:00:00Z", "2027-01-03T07:00:00Z", ShiftStatus.CANCELLED)
        val absent = shift("00000000-0000-0000-0000-000000000008", "2027-01-03T19:00:00Z", "2027-01-04T07:00:00Z", ShiftStatus.ABSENT)
        val vacation = Vacation(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            vacationShift.localStartDate,
            vacationShift.localStartDate,
            now,
            now,
        )
        assertTrue(plan(listOf(vacationShift, cancelled, absent), vacations = listOf(vacation)).boundaries.isEmpty())
    }

    @Test
    fun `simultaneous shifts remain separate and stably ordered`() {
        val laterEnd = shift("00000000-0000-0000-0000-000000000010", "2027-01-01T19:00:00Z", "2027-01-02T08:00:00Z")
        val firstId = shift("00000000-0000-0000-0000-000000000009", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val plan = plan(listOf(laterEnd, firstId), global = emptyList())
        assertEquals(
            listOf(firstId.id, laterEnd.id, firstId.id, laterEnd.id),
            plan.boundaries.map { it.identity.shiftId },
        )
    }

    @Test
    fun `identity changes after temporal edit and remains UUID based`() {
        val original = shift("00000000-0000-0000-0000-000000000011", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val edited = original.copy(startAt = original.startAt.plusSeconds(3600L), endAt = original.endAt.plusSeconds(3600L))
        val originalKey = plan(listOf(original)).boundaries.first().identity.opaqueKey
        val editedKey = plan(listOf(edited)).boundaries.first().identity.opaqueKey
        assertNotEquals(originalKey, editedKey)
        assertTrue(originalKey.startsWith(original.id.toString()))
    }

    @Test
    fun `deletion and config replacement produce obsolete identities`() {
        val shift = shift("00000000-0000-0000-0000-000000000016", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val oldKeys = plan(
            listOf(shift),
            overrides = listOf(ShiftNotificationConfig(shift.id, listOf(360L))),
        ).boundaries.map { it.identity.opaqueKey }.toSet()
        val replacedKeys = plan(
            listOf(shift),
            overrides = listOf(ShiftNotificationConfig(shift.id, listOf(480L))),
        ).boundaries.map { it.identity.opaqueKey }.toSet()

        assertEquals(1, (oldKeys - replacedKeys).size)
        assertEquals(oldKeys, oldKeys - plan(emptyList()).boundaries.map { it.identity.opaqueKey }.toSet())
    }

    @Test
    fun `simultaneous guard identities do not collide functionally`() {
        val first = shift("00000000-0000-0000-0000-000000000017", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val second = shift("00000000-0000-0000-0000-000000000018", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val keys = plan(listOf(first, second), global = emptyList()).boundaries.map { it.identity.opaqueKey }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `reconciliation is idempotent and replaces obsolete boundaries`() {
        val shift = shift("00000000-0000-0000-0000-000000000019", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val original = plan(listOf(shift), global = listOf(360L))
        val installed = original.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey }
        val unchanged = reconcileNotificationPlan(installed, original)
        assertTrue(unchanged.cancelOpaqueKeys.isEmpty())
        assertTrue(unchanged.scheduleBoundaries.isEmpty())

        val edited = plan(listOf(shift.copy(startAt = shift.startAt.plusSeconds(3600))), global = listOf(360L))
        val changed = reconcileNotificationPlan(installed, edited)
        assertTrue(changed.cancelOpaqueKeys.isNotEmpty())
        assertTrue(changed.scheduleBoundaries.isNotEmpty())
    }

    @Test
    fun `editing only position does not duplicate or replace notification boundaries`() {
        val shift = shift("00000000-0000-0000-0000-000000000021", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val original = plan(listOf(shift), global = listOf(360L))
        val installed = original.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey }
        val positionOnly = plan(
            listOf(shift.copy(position = "Puesto corregido", updatedAt = shift.updatedAt.plusMillis(1))),
            global = listOf(360L),
        )

        val changes = reconcileNotificationPlan(installed, positionOnly)

        assertTrue(changes.cancelOpaqueKeys.isEmpty())
        assertTrue(changes.scheduleBoundaries.isEmpty())
    }

    @Test
    fun `editing an interval replaces only that journey boundaries`() {
        val target = shift("00000000-0000-0000-0000-000000000022", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val companion = shift("00000000-0000-0000-0000-000000000023", "2027-01-03T19:00:00Z", "2027-01-04T07:00:00Z")
        val original = plan(listOf(target, companion), global = listOf(360L))
        val installed = original.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey }
        val editedTarget = target.copy(
            startAt = target.startAt.plusSeconds(3_600),
            endAt = target.endAt.plusSeconds(3_600),
            startTimeSnapshot = target.startTimeSnapshot.plusHours(1),
            endTimeSnapshot = target.endTimeSnapshot.plusHours(1),
            updatedAt = target.updatedAt.plusMillis(1),
        )

        val changes = reconcileNotificationPlan(
            installed,
            plan(listOf(editedTarget, companion), global = listOf(360L)),
        )

        assertTrue(changes.cancelOpaqueKeys.isNotEmpty())
        assertTrue(changes.cancelOpaqueKeys.all { it.startsWith(target.id.toString()) })
        assertTrue(changes.scheduleBoundaries.isNotEmpty())
        assertTrue(changes.scheduleBoundaries.all { it.identity.shiftId == target.id })
    }

    @Test
    fun `deleting a journey cancels its boundaries without touching companion boundaries`() {
        val target = shift("00000000-0000-0000-0000-000000000024", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val companion = shift("00000000-0000-0000-0000-000000000025", "2027-01-03T19:00:00Z", "2027-01-04T07:00:00Z")
        val original = plan(listOf(target, companion), global = listOf(360L))
        val installed = original.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey }

        val changes = reconcileNotificationPlan(
            installed,
            plan(listOf(companion), global = listOf(360L)),
        )

        assertTrue(changes.cancelOpaqueKeys.isNotEmpty())
        assertTrue(changes.cancelOpaqueKeys.all { it.startsWith(target.id.toString()) })
        assertTrue(changes.scheduleBoundaries.isEmpty())
    }

    @Test
    fun `precision mode change forces deterministic rescheduling`() {
        val shift = shift("00000000-0000-0000-0000-000000000020", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        val desired = plan(listOf(shift), global = emptyList())
        val installed = desired.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey }
        val changes = reconcileNotificationPlan(installed, desired, forceReschedule = true)

        assertEquals(installed, changes.cancelOpaqueKeys)
        assertEquals(desired.boundaries, changes.scheduleBoundaries)
    }

    @Test
    fun `disabled global notifications produce empty plan`() {
        val shift = shift("00000000-0000-0000-0000-000000000012", "2027-01-01T19:00:00Z", "2027-01-02T07:00:00Z")
        assertTrue(plan(listOf(shift), enabled = false).boundaries.isEmpty())
    }

    private fun plan(
        shifts: List<Shift>,
        global: List<Long> = listOf(720L),
        vacations: List<Vacation> = emptyList(),
        overrides: List<ShiftNotificationConfig> = emptyList(),
        enabled: Boolean = true,
    ) = buildShiftNotificationPlan(now, enabled, global, shifts, vacations, overrides)

    private fun shift(id: String, start: String, end: String, status: ShiftStatus = ShiftStatus.PLANNED): Shift {
        val startAt = Instant.parse(start)
        val endAt = Instant.parse(end)
        val zone = ZoneId.of("UTC")
        return Shift(
            id = UUID.fromString(id),
            startAt = startAt,
            endAt = endAt,
            zoneId = zone,
            localStartDate = startAt.atZone(zone).toLocalDate(),
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "OBJ",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.ofInstant(startAt, zone),
            endTimeSnapshot = LocalTime.ofInstant(endAt, zone),
            colorArgbSnapshot = 0xff336699.toInt(),
            position = null,
            status = status,
            sourceObjectiveId = null,
            sourceScheduleCombinationId = null,
            createdAt = now,
            updatedAt = now,
        )
    }
}
