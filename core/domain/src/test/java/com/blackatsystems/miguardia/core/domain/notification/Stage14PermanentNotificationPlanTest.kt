package com.blackatsystems.miguardia.core.domain.notification

import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.TestZone
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.nextevent.testAvailability
import com.blackatsystems.miguardia.core.domain.nextevent.testInput
import com.blackatsystems.miguardia.core.domain.nextevent.testWrite
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aviso permanente (etapa 14): el plan agrega un despertar silencioso cuando faltan 24 horas para que
 * empiece cada evento, que es cuando el aviso pasa a mostrar la cuenta regresiva viva.
 */
class Stage14PermanentNotificationPlanTest {
    private val now = Instant.parse("2027-01-01T12:00:00Z")

    @Test
    fun `the countdown window of the permanent notification is twenty four hours`() {
        assertEquals(Duration.ofHours(24), PermanentNotificationCountdownWindow)
    }

    @Test
    fun `without the permanent notification the plan has no silent refresh`() {
        val shift = testWrite(901, "2027-01-04T19:00:00Z", "2027-01-05T07:00:00Z")

        val plan = plan(shifts = listOf(shift), refreshLead = null)

        assertTrue(plan.boundaries.none { it.identity.type == NotificationBoundaryType.REFRESH })
        assertEquals(
            listOf(NotificationBoundaryType.REMINDER, NotificationBoundaryType.START, NotificationBoundaryType.END),
            plan.boundaries.map { it.identity.type },
        )
    }

    @Test
    fun `a far event gets one silent refresh exactly twenty four hours before its start`() {
        val shift = testWrite(902, "2027-01-04T19:00:00Z", "2027-01-05T07:00:00Z")

        val plan = plan(shifts = listOf(shift))

        val refresh = plan.boundaries.single { it.identity.type == NotificationBoundaryType.REFRESH }
        assertEquals(Instant.parse("2027-01-03T19:00:00Z"), refresh.identity.triggerAt)
        assertEquals(NextEventIdentity.Shift(shift.shift.id), refresh.identity.eventIdentity)
        assertEquals(null, refresh.identity.leadMinutes)
        // Chronological order: the refresh comes before the reminder six hours ahead, the start and the end.
        assertEquals(
            listOf(
                NotificationBoundaryType.REFRESH,
                NotificationBoundaryType.REMINDER,
                NotificationBoundaryType.START,
                NotificationBoundaryType.END,
            ),
            plan.boundaries.map { it.identity.type },
        )
    }

    @Test
    fun `an event already inside the window or running gets no refresh in the past`() {
        val soon = testWrite(903, "2027-01-02T08:00:00Z", "2027-01-02T16:00:00Z")
        val exactlyAtTheWindow = testWrite(904, "2027-01-02T12:00:00Z", "2027-01-02T20:00:00Z")
        val running = testWrite(905, "2027-01-01T08:00:00Z", "2027-01-01T20:00:00Z")

        val plan = plan(shifts = listOf(soon, exactlyAtTheWindow, running))

        assertTrue(plan.boundaries.none { it.identity.type == NotificationBoundaryType.REFRESH })
    }

    @Test
    fun `each far event gets its own refresh and availability too`() {
        val first = testWrite(906, "2027-01-04T19:00:00Z", "2027-01-05T07:00:00Z")
        val second = testWrite(907, "2027-01-06T19:00:00Z", "2027-01-07T07:00:00Z")
        val availability = testAvailability(908, "2027-01-08T10:00:00Z", "2027-01-08T14:00:00Z")

        val plan = plan(shifts = listOf(first, second), availability = listOf(availability))

        assertEquals(
            listOf(
                Instant.parse("2027-01-03T19:00:00Z"),
                Instant.parse("2027-01-05T19:00:00Z"),
                Instant.parse("2027-01-07T10:00:00Z"),
            ),
            plan.boundaries.filter { it.identity.type == NotificationBoundaryType.REFRESH }.map { it.identity.triggerAt },
        )
    }

    @Test
    fun `a shift with its notifications turned off gets no refresh and disabled notifications get nothing`() {
        val silenced = testWrite(909, "2027-01-04T19:00:00Z", "2027-01-05T07:00:00Z")
        val other = testWrite(910, "2027-01-06T19:00:00Z", "2027-01-07T07:00:00Z")

        val plan = plan(
            shifts = listOf(silenced, other),
            overrides = listOf(ShiftNotificationConfig(silenced.shift.id, emptyList())),
        )

        assertTrue(plan.boundaries.none { it.identity.eventIdentity == NextEventIdentity.Shift(silenced.shift.id) })
        assertEquals(
            1,
            plan.boundaries.count { it.identity.type == NotificationBoundaryType.REFRESH },
        )
        assertTrue(plan(shifts = listOf(other), enabled = false).boundaries.isEmpty())
    }

    @Test
    fun `the refresh works without any reminder configured`() {
        val shift = testWrite(911, "2027-01-04T19:00:00Z", "2027-01-05T07:00:00Z")

        val plan = plan(shifts = listOf(shift), global = emptyList())

        assertEquals(
            listOf(NotificationBoundaryType.REFRESH, NotificationBoundaryType.START, NotificationBoundaryType.END),
            plan.boundaries.map { it.identity.type },
        )
    }

    @Test
    fun `the refresh has its own stored key and replaces nothing when the plan is reconciled again`() {
        val shift = testWrite(912, "2027-01-04T19:00:00Z", "2027-01-05T07:00:00Z")
        val plan = plan(shifts = listOf(shift))
        val keys = plan.boundaries.map { it.identity.opaqueKey }

        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.any { "|REFRESH|" in it })
        val unchanged = reconcileNotificationPlan(keys.toSet(), plan, forceReschedule = false)
        assertTrue(unchanged.cancelOpaqueKeys.isEmpty())
        assertTrue(unchanged.scheduleBoundaries.isEmpty())

        // Turning the permanent notification off retires only the refresh.
        val withoutPermanent = plan(shifts = listOf(shift), refreshLead = null)
        val turnedOff = reconcileNotificationPlan(keys.toSet(), withoutPermanent, forceReschedule = false)
        assertEquals(1, turnedOff.cancelOpaqueKeys.size)
        assertTrue(turnedOff.cancelOpaqueKeys.single().contains("|REFRESH|"))
        assertTrue(turnedOff.scheduleBoundaries.isEmpty())
    }

    private fun plan(
        shifts: List<com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite> = emptyList(),
        availability: List<com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord> = emptyList(),
        global: List<Long> = listOf(360L),
        overrides: List<ShiftNotificationConfig> = emptyList(),
        enabled: Boolean = true,
        refreshLead: Duration? = PermanentNotificationCountdownWindow,
    ): ShiftNotificationPlan = buildNotificationPlan(
        now = now,
        notificationsEnabled = enabled,
        globalReminderLeadMinutes = global,
        projection = projectNextEvent(
            now = now,
            zoneId = TestZone,
            input = testInput(shifts = shifts, availability = availability),
        ),
        shiftOverrides = overrides,
        countdownRefreshLead = refreshLead,
    )
}
