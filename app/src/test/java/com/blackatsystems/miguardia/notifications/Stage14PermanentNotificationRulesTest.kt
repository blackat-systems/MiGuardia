package com.blackatsystems.miguardia.notifications

import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryIdentity
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryType
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aviso permanente (etapa 14): qué evento queda siempre a la vista y cuándo muestra su cuenta regresiva. */
class Stage14PermanentNotificationRulesTest {
    @Test
    fun newPreferencesStartWithThePermanentNotificationOffAndTheLockScreenAllowed() {
        val defaults = NotificationPreferences()

        assertFalse(defaults.alwaysShowNext)
        assertTrue(defaults.showOnLockScreen)
        assertTrue(defaults.countdownWhileActive)
    }

    @Test
    fun thePermanentChoicesDoNotChangeWhichRhythmIsRecognised() {
        val essential = NotificationPreferences(enabled = true)
        assertEquals(NotificationRhythm.ESSENTIAL, essential.rhythm())

        assertEquals(NotificationRhythm.ESSENTIAL, essential.copy(alwaysShowNext = true).rhythm())
        assertEquals(NotificationRhythm.ESSENTIAL, essential.copy(showOnLockScreen = false).rhythm())
        assertEquals(NotificationRhythm.ESSENTIAL, essential.copy(countdownWhileActive = false).rhythm())
    }

    @Test
    fun thePermanentEventIsTheFirstOneThatHasNotEndedAndOnlyThatOne() {
        val finished = shift(ID_1, NOW.minusSeconds(7_200), NOW)
        val running = shift(ID_2, NOW.minusSeconds(600), NOW.plusSeconds(3_600))
        val next = shift(ID_3, NOW.plusSeconds(86_400 * 5), NOW.plusSeconds(86_400 * 5 + 3_600))

        assertEquals(
            setOf(running.identity.trackingKey),
            permanentNotificationEventKeys(listOf(finished, running, next), NOW),
        )
        // With nothing running, the next one is chosen however far away it is.
        assertEquals(
            setOf(next.identity.trackingKey),
            permanentNotificationEventKeys(listOf(finished, next), NOW),
        )
        assertEquals(emptySet<String>(), permanentNotificationEventKeys(listOf(finished), NOW))
        assertEquals(emptySet<String>(), permanentNotificationEventKeys(emptyList(), NOW))
    }

    @Test
    fun aShiftWithItsNotificationsTurnedOffIsNeverThePermanentOne() {
        val silenced = shift(ID_1, NOW.plusSeconds(3_600), NOW.plusSeconds(7_200))
        val following = shift(ID_2, NOW.plusSeconds(86_400), NOW.plusSeconds(90_000))
        val eligible = eligibleNotificationEvents(
            events = listOf(silenced, following),
            configs = listOf(ShiftNotificationConfig(ID_1, emptyList())),
        )

        assertEquals(setOf(following.identity.trackingKey), permanentNotificationEventKeys(eligible, NOW))
    }

    @Test
    fun thePermanentEventIsDisplayedBeforeAnyReminderWasDelivered() {
        val next = shift(ID_1, NOW.plusSeconds(86_400 * 5), NOW.plusSeconds(86_400 * 5 + 3_600))
        val key = next.identity.trackingKey

        val withoutPermanent = reconcile(eligible = setOf(key))
        assertEquals(emptySet<String>(), withoutPermanent.eventKeysToDisplay)

        val withPermanent = reconcile(eligible = setOf(key), permanent = setOf(key))
        assertEquals(setOf(key), withPermanent.eventKeysToDisplay)
        assertEquals(emptySet<String>(), withPermanent.eventKeysToCancel)
    }

    @Test
    fun aDismissedPermanentEventStaysHiddenAndIsNotReplaced() {
        val next = shift(ID_1, NOW.plusSeconds(3_600), NOW.plusSeconds(7_200))
        val key = next.identity.trackingKey

        val result = reconcile(eligible = setOf(key), permanent = setOf(key), dismissed = setOf(key))

        assertEquals(emptySet<String>(), result.eventKeysToDisplay)
        assertEquals(setOf(key), result.eventKeysToCancel)
        assertEquals(setOf(key), result.retainedDismissedEventKeys)
    }

    @Test
    fun thePermanentEventNeedsNotificationsEnabledPermissionAndEligibility() {
        val key = shift(ID_1, NOW.plusSeconds(3_600), NOW.plusSeconds(7_200)).identity.trackingKey

        assertEquals(
            emptySet<String>(),
            reconcile(eligible = setOf(key), permanent = setOf(key), enabled = false).eventKeysToDisplay,
        )
        assertEquals(
            emptySet<String>(),
            reconcile(eligible = setOf(key), permanent = setOf(key), permission = false).eventKeysToDisplay,
        )
        // A key that stopped being eligible (deleted, on leave) is not shown and a shown one is withdrawn.
        val gone = reconcile(eligible = emptySet(), permanent = setOf(key), displayed = setOf(key))
        assertEquals(emptySet<String>(), gone.eventKeysToDisplay)
        assertEquals(setOf(key), gone.eventKeysToCancel)
    }

    @Test
    fun otherNotificationsAlreadyOnDisplayStayWhenThePermanentOneIsAdded() {
        val permanent = shift(ID_1, NOW.plusSeconds(3_600), NOW.plusSeconds(7_200)).identity.trackingKey
        val reminded = shift(ID_2, NOW.plusSeconds(40_000), NOW.plusSeconds(50_000)).identity.trackingKey

        val result = reconcile(
            eligible = setOf(permanent, reminded),
            permanent = setOf(permanent),
            displayed = setOf(reminded),
        )

        assertEquals(setOf(permanent, reminded), result.eventKeysToDisplay)
    }

    @Test
    fun theLiveCountdownAppearsInsideTwentyFourHoursAndAlwaysWhileRunning() {
        // Permanent notification on: far away, the dated schedule replaces a long count of hours.
        assertFalse(countdown(permanent = true, untilStart = Duration.ofDays(5)))
        assertFalse(countdown(permanent = true, untilStart = Duration.ofHours(24).plusSeconds(1)))
        assertTrue(countdown(permanent = true, untilStart = Duration.ofHours(24)))
        assertTrue(countdown(permanent = true, untilStart = Duration.ofMinutes(5)))
        assertTrue(countdown(permanent = true, ongoing = true, untilStart = Duration.ofHours(-3)))

        // Permanent notification off: exactly as before, the countdown is always there.
        assertTrue(countdown(permanent = false, untilStart = Duration.ofDays(5)))
        assertTrue(countdown(permanent = false, untilStart = Duration.ofMinutes(5)))
        assertTrue(countdown(permanent = false, ongoing = true, untilStart = Duration.ofHours(-3)))
    }

    @Test
    fun aRunningEventShowsOnlyItsScheduleWhenThePersonTurnsTheCountdownOff() {
        fun running(permanent: Boolean, countdown: Boolean) = notificationCountdownVisible(
            alwaysShowNext = permanent,
            ongoing = true,
            untilStart = Duration.ofHours(-3),
            countdownWhileActive = countdown,
        )
        fun upcoming(permanent: Boolean, countdown: Boolean) = notificationCountdownVisible(
            alwaysShowNext = permanent,
            ongoing = false,
            untilStart = Duration.ofHours(2),
            countdownWhileActive = countdown,
        )

        assertTrue(running(permanent = true, countdown = true))
        assertTrue(running(permanent = false, countdown = true))
        assertFalse(running(permanent = true, countdown = false))
        assertFalse(running(permanent = false, countdown = false))
        // The choice is only about a running event: before the start nothing changes.
        assertTrue(upcoming(permanent = true, countdown = false))
        assertTrue(upcoming(permanent = false, countdown = false))
    }

    @Test
    fun theSilentRefreshSurvivesTheStoredAlarmKeyRoundTrip() {
        val identity = NotificationBoundaryIdentity(
            eventIdentity = NextEventIdentity.Shift(ID_1),
            type = NotificationBoundaryType.REFRESH,
            triggerAt = NOW.plusSeconds(3_600),
        )

        assertEquals(identity, AndroidShiftAlarmScheduler.decodeOpaqueKey(identity.opaqueKey))
    }

    private fun countdown(permanent: Boolean, untilStart: Duration, ongoing: Boolean = false): Boolean =
        notificationCountdownVisible(alwaysShowNext = permanent, ongoing = ongoing, untilStart = untilStart)

    private fun reconcile(
        eligible: Set<String>,
        permanent: Set<String> = emptySet(),
        displayed: Set<String> = emptySet(),
        dismissed: Set<String> = emptySet(),
        enabled: Boolean = true,
        permission: Boolean = true,
    ) = reconcileNotificationVisibility(
        notificationsEnabled = enabled,
        notificationPermissionGranted = permission,
        eligibleEventKeys = eligible,
        startedEligibleEventKeys = emptySet(),
        displayedEventKeys = displayed,
        retainedDismissedEventKeys = dismissed,
        permanentEventKeys = permanent,
    )

    private fun shift(id: UUID, start: Instant, end: Instant): NextEventItem.Shift = NextEventItem.Shift(
        shiftId = id,
        start = start,
        end = end,
        zoneId = ZONE,
        ownerLocalDate = LocalDate.from(start.atZone(ZONE)),
        sector = WorkSector.PRIVATE_SECURITY,
        workTypeNameSnapshot = "Jornada ficticia",
        workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
        placeNameSnapshot = "Lugar ficticio",
        placeAbbreviationSnapshot = "FIC",
        startTimeSnapshot = LocalTime.from(start.atZone(ZONE)),
        endTimeSnapshot = LocalTime.from(end.atZone(ZONE)),
        colorArgbSnapshot = 0xff336699.toInt(),
        positionSnapshot = null,
        hasHistoricalAddress = false,
    )

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val NOW: Instant = Instant.parse("2027-03-10T15:00:00Z")
        val ID_1: UUID = UUID.fromString("e1400000-0000-0000-0000-0000000000a1")
        val ID_2: UUID = UUID.fromString("e1400000-0000-0000-0000-0000000000a2")
        val ID_3: UUID = UUID.fromString("e1400000-0000-0000-0000-0000000000a3")
    }
}
