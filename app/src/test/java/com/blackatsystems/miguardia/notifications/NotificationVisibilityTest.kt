package com.blackatsystems.miguardia.notifications

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationVisibilityTest {
    @Test
    fun oneShotNotificationWorkContainsRecoverableFailuresAndPreservesCancellation() = runBlocking {
        assertTrue(runNotificationOperation { })
        assertFalse(runNotificationOperation { error("fallo recuperable") })

        var cancellationPropagated = false
        try {
            runNotificationOperation { throw CancellationException("cancelado") }
        } catch (_: CancellationException) {
            cancellationPropagated = true
        }
        assertTrue(cancellationPropagated)
    }

    @Test
    fun rebuildClearsTrackingBeforeCancellingPlatformAlarms() = runBlocking {
        val operations = mutableListOf<String>()
        retireInstalledNotificationBoundaries(
            opaqueKeys = linkedSetOf("primera", "segunda"),
            clearInstalled = { operations += "vaciar" },
            cancel = { key -> operations += "cancelar:$key" },
        )
        assertEquals(listOf("vaciar", "cancelar:primera", "cancelar:segunda"), operations)

        val cancellationsAfterFailedClear = mutableListOf<String>()
        runCatching {
            retireInstalledNotificationBoundaries(
                opaqueKeys = setOf("no-debe-cancelarse"),
                clearInstalled = { error("DataStore no disponible") },
                cancel = cancellationsAfterFailedClear::add,
            )
        }
        assertEquals(emptyList<String>(), cancellationsAfterFailedClear)
    }

    @Test
    fun restorableEventsIncludeShiftAndAvailabilityButRespectShiftException() {
        val earlier = shift(EARLIER_ID, NOW.plusSeconds(3_600), NOW.plusSeconds(7_200))
        val availability = availability(AVAILABILITY_ID, NOW.plusSeconds(5_400), NOW.plusSeconds(9_000))
        val disabled = shift(DISABLED_ID, NOW.plusSeconds(7_200), NOW.plusSeconds(10_800))

        val result = restorableDismissedEvents(
            events = listOf(earlier, availability, disabled),
            configs = listOf(ShiftNotificationConfig(disabled.shiftId, emptyList())),
            dismissedEventKeys = setOf(
                earlier.identity.trackingKey,
                availability.identity.trackingKey,
                disabled.identity.trackingKey,
            ),
        )

        assertEquals(
            listOf(earlier.identity.trackingKey, availability.identity.trackingKey),
            result.map { it.identity.trackingKey },
        )
    }

    @Test
    fun deletedDismissedEventIsNotRestorableWhileItsCompanionRemainsRestorable() {
        val deleted = shift(DELETED_ID, NOW.plusSeconds(3_600), NOW.plusSeconds(7_200))
        val companion = availability(AVAILABILITY_ID, NOW.plusSeconds(7_200), NOW.plusSeconds(10_800))

        val result = restorableDismissedEvents(
            events = listOf(companion),
            configs = emptyList(),
            dismissedEventKeys = setOf(deleted.identity.trackingKey, companion.identity.trackingKey, "invalid"),
        )

        assertEquals(listOf(companion.identity.trackingKey), result.map { it.identity.trackingKey })
    }

    @Test
    fun deletedEventCancelsVisibleTrackingWithoutTouchingItsCompanion() {
        val deletedKey = "shift:$DELETED_ID"
        val companionKey = availability(AVAILABILITY_ID, NOW, NOW.plusSeconds(3_600)).identity.trackingKey

        val result = reconcileNotificationVisibility(
            notificationsEnabled = true,
            notificationPermissionGranted = true,
            eligibleEventKeys = setOf(companionKey),
            startedEligibleEventKeys = setOf(companionKey),
            displayedEventKeys = setOf(deletedKey, companionKey),
            retainedDismissedEventKeys = emptySet(),
        )

        assertEquals(setOf(deletedKey), result.eventKeysToCancel)
        assertEquals(setOf(companionKey), result.eventKeysToDisplay)
        assertEquals(emptySet<String>(), result.retainedDismissedEventKeys)
    }

    @Test
    fun retainedDismissalCancelsOnlyThatEventAndKeepsItsTracking() {
        val dismissedKey = "shift:$DELETED_ID"
        val companionKey = availability(AVAILABILITY_ID, NOW, NOW.plusSeconds(3_600)).identity.trackingKey

        val result = reconcileNotificationVisibility(
            notificationsEnabled = true,
            notificationPermissionGranted = true,
            eligibleEventKeys = setOf(dismissedKey, companionKey),
            startedEligibleEventKeys = setOf(dismissedKey, companionKey),
            displayedEventKeys = setOf(dismissedKey, companionKey),
            retainedDismissedEventKeys = setOf(dismissedKey),
        )

        assertEquals(setOf(dismissedKey), result.eventKeysToCancel)
        assertEquals(setOf(companionKey), result.eventKeysToDisplay)
        assertEquals(setOf(dismissedKey), result.retainedDismissedEventKeys)
    }

    @Test
    fun temporarilyIneligibleDismissalSurvivesAndBlocksAutomaticRedisplay() {
        val dismissedKey = "shift:$DELETED_ID"
        val whileProtected = reconcileNotificationVisibility(
            notificationsEnabled = true,
            notificationPermissionGranted = true,
            eligibleEventKeys = emptySet(),
            startedEligibleEventKeys = emptySet(),
            displayedEventKeys = emptySet(),
            retainedDismissedEventKeys = setOf(dismissedKey),
        )
        val afterProtectionEnds = reconcileNotificationVisibility(
            notificationsEnabled = true,
            notificationPermissionGranted = true,
            eligibleEventKeys = setOf(dismissedKey),
            startedEligibleEventKeys = setOf(dismissedKey),
            displayedEventKeys = emptySet(),
            retainedDismissedEventKeys = whileProtected.retainedDismissedEventKeys,
        )

        assertEquals(setOf(dismissedKey), whileProtected.retainedDismissedEventKeys)
        assertEquals(emptySet<String>(), afterProtectionEnds.eventKeysToDisplay)
        assertEquals(setOf(dismissedKey), afterProtectionEnds.retainedDismissedEventKeys)
    }

    @Test
    fun rawShiftDismissalSurvivesProjectionAbsenceUntilItsExactEnd() {
        val key = NextEventIdentity.Shift(DELETED_ID).trackingKey
        val sources = mapOf(
            DELETED_ID to NotificationSourceLifetime(
                start = NOW.minusSeconds(3_600),
                end = NOW.plusSeconds(3_600),
            ),
        )

        val whileTemporarilyProtected = retainLiveDismissedEventKeys(
            dismissedEventKeys = setOf(key),
            now = NOW,
            shiftSources = sources,
            availabilitySources = emptyMap(),
        )
        val oneMillisecondBeforeEnd = retainLiveDismissedEventKeys(
            dismissedEventKeys = setOf(key),
            now = NOW.plusSeconds(3_600).minusMillis(1),
            shiftSources = sources,
            availabilitySources = emptyMap(),
        )
        val atExactEnd = retainLiveDismissedEventKeys(
            dismissedEventKeys = setOf(key),
            now = NOW.plusSeconds(3_600),
            shiftSources = sources,
            availabilitySources = emptyMap(),
        )

        assertEquals(setOf(key), whileTemporarilyProtected)
        assertEquals(setOf(key), oneMillisecondBeforeEnd)
        assertEquals(emptySet<String>(), atExactEnd)
    }

    @Test
    fun extendedActualShiftKeepsDismissalAlivePastPlannedEndUntilActualEnd() {
        val planned = rawShift(
            id = DELETED_ID,
            start = NOW.minusSeconds(3_600),
            end = NOW,
        )
        val actualEnd = NOW.plusSeconds(3_600)
        val actual = ShiftActualAggregate(
            record = ShiftActualRecord(
                shiftId = planned.id,
                timelineId = TIMELINE_ID,
                sector = WorkSector.PRIVATE_SECURITY,
                actualStart = planned.startAt,
                actualEnd = actualEnd,
                differenceReason = "Salida extendida",
                explanation = null,
                createdAt = NOW.minusSeconds(7_200),
                updatedAt = NOW.minusSeconds(60),
            ),
            extraIntervals = emptyList(),
        )
        val sources = mapOf(
            planned.id to shiftNotificationSourceLifetime(planned, actual),
        )
        val key = NextEventIdentity.Shift(planned.id).trackingKey

        val atPlannedEnd = retainLiveDismissedEventKeys(
            dismissedEventKeys = setOf(key),
            now = NOW,
            shiftSources = sources,
            availabilitySources = emptyMap(),
        )
        val atActualEnd = retainLiveDismissedEventKeys(
            dismissedEventKeys = setOf(key),
            now = actualEnd,
            shiftSources = sources,
            availabilitySources = emptyMap(),
        )

        assertEquals(setOf(key), atPlannedEnd)
        assertEquals(emptySet<String>(), atActualEnd)
    }

    @Test
    fun rawAvailabilityDismissalSurvivesProtectionButNotDeletionOrIncompatibleCorrection() {
        val segmentStart = NOW.plusSeconds(600)
        val segmentEnd = NOW.plusSeconds(3_600)
        val identity = NextEventIdentity.Availability(
            windowId = AVAILABILITY_ID,
            segmentStart = segmentStart,
            segmentEnd = segmentEnd,
        )
        val originalSource = NotificationSourceLifetime(
            start = NOW,
            end = NOW.plusSeconds(7_200),
        )

        val whileTemporarilyProtected = retainLiveDismissedEventKeys(
            dismissedEventKeys = setOf(identity.trackingKey),
            now = NOW,
            shiftSources = emptyMap(),
            availabilitySources = mapOf(AVAILABILITY_ID to originalSource),
        )
        val afterDeletion = retainLiveDismissedEventKeys(
            dismissedEventKeys = setOf(identity.trackingKey),
            now = NOW,
            shiftSources = emptyMap(),
            availabilitySources = emptyMap(),
        )
        val afterCorrectionExcludesOldSegment = retainLiveDismissedEventKeys(
            dismissedEventKeys = setOf(identity.trackingKey),
            now = NOW,
            shiftSources = emptyMap(),
            availabilitySources = mapOf(
                AVAILABILITY_ID to originalSource.copy(start = segmentStart.plusSeconds(60)),
            ),
        )

        assertEquals(setOf(identity.trackingKey), whileTemporarilyProtected)
        assertEquals(emptySet<String>(), afterDeletion)
        assertEquals(emptySet<String>(), afterCorrectionExcludesOldSegment)
    }

    @Test
    fun staleDeletedExpiredAndMalformedDismissalsArePrunedWithoutTouchingLiveCompanion() {
        val liveShiftKey = NextEventIdentity.Shift(EARLIER_ID).trackingKey
        val deletedShiftKey = NextEventIdentity.Shift(DELETED_ID).trackingKey
        val expiredAvailability = NextEventIdentity.Availability(
            windowId = AVAILABILITY_ID,
            segmentStart = NOW.minusSeconds(3_600),
            segmentEnd = NOW,
        )

        val result = retainLiveDismissedEventKeys(
            dismissedEventKeys = linkedSetOf(
                liveShiftKey,
                deletedShiftKey,
                expiredAvailability.trackingKey,
                "identidad-invalida",
            ),
            now = NOW,
            shiftSources = mapOf(
                EARLIER_ID to NotificationSourceLifetime(NOW, NOW.plusSeconds(3_600)),
            ),
            availabilitySources = mapOf(
                AVAILABILITY_ID to NotificationSourceLifetime(
                    NOW.minusSeconds(7_200),
                    NOW.plusSeconds(3_600),
                ),
            ),
        )

        assertEquals(setOf(liveShiftKey), result)
    }

    @Test
    fun notificationRetryBackoffIsBounded() {
        assertEquals(1_000L, notificationObservationRetryDelayMillis(0))
        assertEquals(60_000L, notificationObservationRetryDelayMillis(8))
        assertEquals(60_000L, notificationObservationRetryDelayMillis(Int.MAX_VALUE))
    }

    @Test
    fun hiddenGroupSummaryNeverRevealsCountOrLaborContext() {
        val content = notificationGroupSummaryContent(2, NotificationPrivacy.HIDDEN)

        assertEquals("MiGuardia", content.title)
        assertEquals("Tenés avisos de MiGuardia.", content.text)
        assertEquals("MiGuardia", content.publicTitle)
        assertEquals("Tenés avisos de MiGuardia.", content.publicText)
    }

    private fun shift(id: UUID, start: Instant, end: Instant): NextEventItem.Shift = NextEventItem.Shift(
        shiftId = id,
        start = start,
        end = end,
        zoneId = ZONE,
        ownerLocalDate = start.atZone(ZONE).toLocalDate(),
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

    private fun availability(id: UUID, start: Instant, end: Instant): NextEventItem.Availability =
        NextEventItem.Availability(
            windowId = id,
            start = start,
            end = end,
            zoneId = ZONE,
            ownerLocalDate = LocalDate.from(start.atZone(ZONE)),
            labelSnapshot = "Guardia pasiva",
            isResumption = false,
        )

    private fun rawShift(id: UUID, start: Instant, end: Instant): Shift = Shift(
        id = id,
        startAt = start,
        endAt = end,
        zoneId = ZONE,
        localStartDate = start.atZone(ZONE).toLocalDate(),
        objectiveNameSnapshot = "Lugar ficticio",
        objectiveAbbreviationSnapshot = "FIC",
        objectiveAddressSnapshot = null,
        startTimeSnapshot = LocalTime.from(start.atZone(ZONE)),
        endTimeSnapshot = LocalTime.from(end.atZone(ZONE)),
        colorArgbSnapshot = 0xff336699.toInt(),
        position = null,
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = OBJECTIVE_ID,
        createdAt = NOW.minusSeconds(7_200),
        updatedAt = NOW.minusSeconds(60),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T20:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val EARLIER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000901")
        val DISABLED_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000903")
        val DELETED_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000907")
        val AVAILABILITY_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000908")
        val TIMELINE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000909")
        val OBJECTIVE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000910")
    }
}
