package com.blackatsystems.miguardia

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.Chronometer
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowDraft
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowMutation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowWriteResult
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.model.buildAvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryIdentity
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryType
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.toNextEventItem
import com.blackatsystems.miguardia.notifications.AndroidShiftAlarmScheduler
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.notifications.NotificationSystemAccess
import com.blackatsystems.miguardia.notifications.ShiftAlarmReceiver
import com.blackatsystems.miguardia.notifications.ShiftNotificationPresenter
import com.blackatsystems.miguardia.notifications.processQueuedNotificationActionsUnderMutationGate
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityWriteResult
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationAlarmEndToEndInstrumentedTest {
    @Before
    fun resetQaDatabase() {
        val application = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        check(application.packageName == QA_APPLICATION_ID) {
            "La preparación instrumentada sólo puede limpiar el paquete QA."
        }
        val arguments = InstrumentationRegistry.getArguments()
        val packageReplacementPhase = arguments.getString(PACKAGE_REPLACEMENT_PHASE_ARGUMENT)
        if (packageReplacementPhase != null) {
            check(arguments.getString("class") == PACKAGE_REPLACEMENT_TEST_TARGET) {
                "La fase de reemplazo QA exige filtrar únicamente su prueba dedicada."
            }
        } else {
            runBlocking {
                application.notificationPreferences.setEnabled(false)
                application.notificationPreferences.setPreciseTiming(false)
                application.notificationRuntime.rebuildNow()
            }
        }
        if (packageReplacementPhase != PACKAGE_REPLACEMENT_VERIFY) {
            application.localDataStore.clearAllDataForInstrumentation()
        }
    }

    @Test
    fun typedAndLegacyAlarmPayloadsRemainReconstructible() {
        val eventIdentity = NextEventIdentity.Availability(
            windowId = UUID.fromString("00000000-0000-0000-0000-000000000730"),
            segmentStart = Instant.parse("2026-09-01T19:00:00Z"),
            segmentEnd = Instant.parse("2026-09-02T07:00:00Z"),
        )
        val typed = NotificationBoundaryIdentity(
            eventIdentity = eventIdentity,
            type = NotificationBoundaryType.START,
            triggerAt = eventIdentity.segmentStart,
        )
        val typedIntent = boundaryIntent(typed.opaqueKey)
        assertEquals(typed, AndroidShiftAlarmScheduler.readIdentity(typedIntent))

        val legacyKey = "$REACTIVE_SHIFT_ID|START|1788289200000|0"
        val legacy = AndroidShiftAlarmScheduler.readIdentity(boundaryIntent(legacyKey))
        assertEquals(REACTIVE_SHIFT_ID, legacy?.shiftId)
        assertEquals(NotificationBoundaryType.START, legacy?.type)
    }

    @Test
    fun boundaryReceivedDuringStartupRecoveryIsDeliveredAfterReady() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        assumeTrue(
            "La entrega diferida instrumentada sólo puede modificar el paquete QA.",
            application.packageName == QA_APPLICATION_ID,
        )
        grantPostNotificationsIfRequired(application)
        val manager = application.getSystemService(NotificationManager::class.java)
        val now = Instant.now()
        val shift = futureShift(
            id = STARTUP_RECOVERY_SHIFT_ID,
            start = now.plus(30, ChronoUnit.MINUTES),
            end = now.plus(90, ChronoUnit.MINUTES),
            createdAt = now,
        )
        val identity = NotificationBoundaryIdentity(
            shiftId = shift.id,
            type = NotificationBoundaryType.REMINDER,
            triggerAt = shift.startAt.minus(60, ChronoUnit.MINUTES),
            leadMinutes = 60L,
        )
        val deferredPreferences = application.getSharedPreferences(
            "notification_deferred_actions",
            Context.MODE_PRIVATE,
        )
        try {
            assertTrue(deferredPreferences.edit().clear().commit())
            application.notificationPreferences.setEnabled(false)
            application.notificationRuntime.rebuildNow()
            application.notificationPreferences.setDisplayedEventKeys(emptySet())
            application.notificationPreferences.setDismissedEventKeys(emptySet())
            application.notificationPreferences.setGlobalReminderLeadMinutes(listOf(60L))
            manager.cancelAll()
            insertV2(application, shift)
            application.notificationPreferences.setEnabled(true)
            application.notificationRuntime.reconcileNow()
            application.notificationRuntime.pauseForRestore()
            application.startupRecoveryGate.recovering()

            ShiftAlarmReceiver().onReceive(application, boundaryIntent(application, identity))

            assertTrue(application.notificationDeferredActions.hasPendingActions())
            assertTrue(manager.activeNotifications.none { it.tag == "shift:${shift.id}" })

            application.startupRecoveryGate.ready()
            application.notificationRuntime.resumeAfterRestore()
            processQueuedNotificationActionsUnderMutationGate(application)
            waitUntil(5_000L) {
                manager.activeNotifications.any { it.tag == "shift:${shift.id}" }
            }

            assertFalse(application.notificationDeferredActions.hasPendingActions())
            processQueuedNotificationActionsUnderMutationGate(application)
            assertEquals(
                1,
                manager.activeNotifications.count { it.tag == "shift:${shift.id}" },
            )
        } finally {
            application.startupRecoveryGate.ready()
            if (application.notificationRuntime.isPausedForRestore) {
                runCatching { application.notificationRuntime.resumeAfterRestore() }
            }
            application.notificationPreferences.setEnabled(false)
            application.notificationPreferences.clearEventTracking("shift:${shift.id}")
            deleteIfPresent(application, shift.id)
            manager.cancel("shift:${shift.id}", ShiftNotificationPresenter.NOTIFICATION_ID)
            assertTrue(deferredPreferences.edit().clear().commit())
            application.notificationRuntime.reconcile()
        }
    }

    @Test
    fun rebuildRestoresCurrentBoundaries() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        assumeTrue(
            "La reconstrucción instrumentada sólo puede modificar el paquete QA.",
            application.packageName == QA_APPLICATION_ID,
        )
        when (
            val phase = InstrumentationRegistry.getArguments()
                .getString(PACKAGE_REPLACEMENT_PHASE_ARGUMENT)
        ) {
            null -> verifyExplicitRebuild(application)
            PACKAGE_REPLACEMENT_PREPARE -> preparePackageReplacementRebuild(application)
            PACKAGE_REPLACEMENT_VERIFY -> verifyPackageReplacementRebuild(application)
            else -> error("Fase de reemplazo QA desconocida: $phase")
        }
    }

    private suspend fun verifyExplicitRebuild(application: MiGuardiaApplication) {
        grantPostNotificationsIfRequired(application)
        val manager = application.getSystemService(NotificationManager::class.java)
        val fixture = createRebuildFixture(application, manager)
        try {
            application.notificationRuntime.rebuildNow()
            assertRebuildResult(application, fixture)
        } finally {
            clearRebuildFixture(application, manager)
        }
    }

    private suspend fun preparePackageReplacementRebuild(application: MiGuardiaApplication) {
        grantPostNotificationsIfRequired(application)
        val manager = application.getSystemService(NotificationManager::class.java)
        val fixture = createRebuildFixture(application, manager)
        val scheduler = AndroidShiftAlarmScheduler(application)
        fixture.currentBoundaries.forEach(scheduler::cancel)
        assertEquals(fixture.currentBoundaries, installedFor(application, REBUILD_SHIFT_ID))
    }

    private suspend fun verifyPackageReplacementRebuild(application: MiGuardiaApplication) {
        grantPostNotificationsIfRequired(application)
        val manager = application.getSystemService(NotificationManager::class.java)
        val persisted = requireV2Write(application, REBUILD_SHIFT_ID)
        val fixture = RebuildFixture(
            persistedEvent = persisted.toNextEventItem(),
            trackingKey = "shift:$REBUILD_SHIFT_ID",
            currentBoundaries = installedFor(application, REBUILD_SHIFT_ID),
        )
        try {
            assertTrue("La preparación QA debe conservar las fronteras registradas.", fixture.currentBoundaries.isNotEmpty())
            val externallyVerifiedEpoch = InstrumentationRegistry.getArguments()
                .getString(PACKAGE_REPLACEMENT_REBUILT_EPOCH_ARGUMENT)
                ?.toLongOrNull()
            assertEquals(
                "La fase verify exige el instante confirmado externamente en AlarmManager tras instalar -r.",
                fixture.persistedEvent.end.toEpochMilli(),
                externallyVerifiedEpoch,
            )
            assertRebuildResult(application, fixture)
        } finally {
            clearRebuildFixture(application, manager)
        }
    }

    private suspend fun createRebuildFixture(
        application: MiGuardiaApplication,
        manager: NotificationManager,
    ): RebuildFixture {
        val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        val shift = futureShift(
            id = REBUILD_SHIFT_ID,
            start = now.minus(1, ChronoUnit.MINUTES),
            end = now.plus(60, ChronoUnit.MINUTES),
            createdAt = now.minus(1, ChronoUnit.DAYS),
        )
        val trackingKey = "shift:${shift.id}"
        application.notificationPreferences.setEnabled(false)
        application.notificationRuntime.rebuildNow()
        application.notificationPreferences.setDisplayedEventKeys(emptySet())
        application.notificationPreferences.setDismissedEventKeys(emptySet())
        manager.cancelAll()
        application.notificationPreferences.setPrivacy(NotificationPrivacy.COMPLETE)
        application.notificationPreferences.setGlobalReminderLeadMinutes(listOf(60L))
        application.notificationPreferences.setPreciseTiming(false)
        application.notificationPreferences.setEnabled(true)
        val persisted = insertV2(application, shift)
        application.notificationRuntime.reconcileNow()
        waitUntil(5_000L) {
            manager.activeNotifications.any { notification -> notification.tag == trackingKey }
        }
        val currentBoundaries = installedFor(application, shift.id)
        assertTrue("La jornada activa debe conservar al menos su frontera final.", currentBoundaries.isNotEmpty())
        val persistedEvent = persisted.toNextEventItem()
        return RebuildFixture(persistedEvent, trackingKey, currentBoundaries)
    }

    private suspend fun assertRebuildResult(
        application: MiGuardiaApplication,
        fixture: RebuildFixture,
    ) {
        assertEquals(fixture.currentBoundaries, installedFor(application, REBUILD_SHIFT_ID))
        assertEquals(setOf(fixture.trackingKey), application.notificationPreferences.displayedEventKeys())
    }

    private suspend fun clearRebuildFixture(
        application: MiGuardiaApplication,
        manager: NotificationManager,
    ) {
        application.notificationPreferences.setEnabled(false)
        application.notificationPreferences.setPreciseTiming(false)
        application.notificationPreferences.clearEventTracking("shift:$REBUILD_SHIFT_ID")
        deleteIfPresent(application, REBUILD_SHIFT_ID)
        application.notificationRuntime.rebuildNow()
        manager.cancelAll()
    }

    @Test
    fun qaPlanReactsToEditOwnDisableGlobalRestoreVacationAndDeletion() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        assumeTrue("La reconciliación física sólo puede modificar el paquete QA.", application.packageName.endsWith(".qa"))
        val now = Instant.now()
        val original = futureShift(REACTIVE_SHIFT_ID, now.plusSeconds(7200), now.plusSeconds(10_800), now)
        val vacation = Vacation(
            id = VACATION_ID,
            startDate = original.localStartDate,
            endDateInclusive = original.localStartDate,
            createdAt = now,
            updatedAt = now,
        )
        var currentWrite: V2ShiftWrite? = null
        try {
            application.notificationPreferences.setGlobalReminderLeadMinutes(listOf(60L))
            application.notificationPreferences.setEnabled(true)
            val originalWrite = insertV2(application, original)
            currentWrite = originalWrite
            application.notificationRuntime.reconcile()
            waitUntil(5_000L) { installedFor(application, original.id).size == 3 }
            val originalKeys = installedFor(application, original.id)

            application.notificationRuntime.rebuildNow()
            waitUntil(5_000L) { installedFor(application, original.id) == originalKeys }

            val persistedOriginal = originalWrite.shift
            val editMinutes = if (
                persistedOriginal.startTimeSnapshot.plusMinutes(30) > persistedOriginal.startTimeSnapshot
            ) {
                30L
            } else {
                -30L
            }
            val edited = persistedOriginal.copy(
                startAt = persistedOriginal.startAt.plusSeconds(editMinutes * 60),
                endAt = persistedOriginal.endAt.plusSeconds(editMinutes * 60),
                startTimeSnapshot = persistedOriginal.startTimeSnapshot.plusMinutes(editMinutes),
                endTimeSnapshot = persistedOriginal.endTimeSnapshot.plusMinutes(editMinutes),
                updatedAt = Instant.ofEpochMilli(
                    maxOf(Instant.now().toEpochMilli(), persistedOriginal.updatedAt.toEpochMilli() + 1L),
                ),
            )
            val editedWrite = V2AppTestFixture.writeFor(
                application.localDataStore,
                edited,
                original.localStartDate,
            )
            application.localDataStore.v2Shifts.applyV2Batch(
                mutation = V2ShiftBatchMutation(shiftsToUpdate = listOf(editedWrite)),
                expectedOccupancy = ShiftOccupancyExpectation.capture(
                    startDateInclusive = original.localStartDate,
                    endDateInclusive = original.localStartDate,
                    shifts = listOf(originalWrite.shift),
                ),
                expectedUpdates = V2ShiftWriteExpectation.capture(listOf(originalWrite)),
            )
            val persistedEditedWrite = requireV2Write(application, editedWrite.shift.id)
            currentWrite = persistedEditedWrite
            waitUntil(5_000L) {
                val current = installedFor(application, original.id)
                current.size == 3 && current.intersect(originalKeys).isEmpty()
            }

            application.localDataStore.shiftNotificationConfigs.replace(
                ShiftNotificationConfig(original.id, emptyList()),
            )
            waitUntil(5_000L) { installedFor(application, original.id).isEmpty() }
            application.localDataStore.shiftNotificationConfigs.clear(original.id)
            waitUntil(5_000L) { installedFor(application, original.id).size == 3 }

            application.localDataStore.vacations.insert(vacation)
            waitUntil(5_000L) { installedFor(application, original.id).isEmpty() }
            val persistedVacation = requireNotNull(
                application.localDataStore.vacations.getById(vacation.id),
            )
            application.localDataStore.vacations.delete(persistedVacation)
            waitUntil(5_000L) { installedFor(application, original.id).size == 3 }

            application.localDataStore.v2Shifts.deleteShift(persistedEditedWrite)
            currentWrite = null
            waitUntil(5_000L) { installedFor(application, original.id).isEmpty() }
        } finally {
            application.notificationPreferences.setEnabled(false)
            application.localDataStore.shiftNotificationConfigs.clear(original.id)
            application.localDataStore.vacations.getById(vacation.id)?.let { persistedVacation ->
                application.localDataStore.vacations.delete(persistedVacation)
            }
            currentWrite?.let { deleteIfPresent(application, it.shift.id) }
            application.notificationRuntime.reconcile()
        }
    }

    @Test
    fun staleEndBoundaryDoesNotCancelEditedShiftNotification() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        assumeTrue("Las notificaciones instrumentadas sólo pueden tocar el paquete QA.", application.packageName.endsWith(".qa"))
        grantPostNotificationsIfRequired(application)
        val manager = application.getSystemService(NotificationManager::class.java)
        val now = Instant.now()
        val shift = futureShift(STALE_END_SHIFT_ID, now.minusSeconds(60), now.plusSeconds(3600), now)
        val staleEnd = NotificationBoundaryIdentity(
            shiftId = shift.id,
            type = NotificationBoundaryType.END,
            triggerAt = now.minusSeconds(1),
        )
        try {
            application.notificationPreferences.setEnabled(true)
            val write = insertV2(application, shift)
            val event = write.toNextEventItem()
            ShiftNotificationPresenter(application).show(event, now, application.notificationPreferences.current())
            application.notificationPreferences.markDisplayed(event.identity.trackingKey)

            application.sendBroadcast(boundaryIntent(application, staleEnd))
            waitUntil(5_000L) {
                manager.activeNotifications.any { it.tag == event.identity.trackingKey }
            }
            assertTrue(manager.activeNotifications.any { it.tag == event.identity.trackingKey })
        } finally {
            application.notificationPreferences.setEnabled(false)
            application.notificationPreferences.clearEventTracking("shift:${shift.id}")
            deleteIfPresent(application, shift.id)
            manager.cancel("shift:${shift.id}", 1042)
            application.notificationRuntime.reconcile()
        }
    }

    @Test
    fun dismissedShiftRejectsStartBoundaryAndCanBeRestoredSilently() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        assumeTrue("Las notificaciones instrumentadas sólo pueden tocar el paquete QA.", application.packageName.endsWith(".qa"))
        grantPostNotificationsIfRequired(application)
        val manager = application.getSystemService(NotificationManager::class.java)
        val now = Instant.now()
        val shift = futureShift(DISMISS_SHIFT_ID, now.minusSeconds(60), now.plusSeconds(3600), now)
        try {
            application.notificationPreferences.setEnabled(true)
            application.notificationPreferences.setPersistentWhileActive(false)
            val write = insertV2(application, shift)
            val event = write.toNextEventItem()
            ShiftNotificationPresenter(application).show(event, now, application.notificationPreferences.current())
            application.notificationPreferences.markDisplayed(event.identity.trackingKey)
            val posted = manager.activeNotifications.first { it.tag == event.identity.trackingKey }.notification

            assertTrue(posted.deleteIntent != null)
            val dismissControl = posted.bigContentView.apply(application, null)
                .findViewById<TextView>(R.id.notification_dismiss)
            assertEquals("Eliminar notificación", dismissControl.text.toString())
            assertTrue(dismissControl.hasOnClickListeners())
            application.notificationRuntime.dismissNow(event.identity.trackingKey)
            waitUntil(5_000L) {
                event.identity.trackingKey in application.notificationPreferences.dismissedEventKeys()
            }
            application.notificationRuntime.reconcileNow()
            waitUntil(5_000L) {
                manager.activeNotifications.none { it.tag == event.identity.trackingKey }
            }
            assertFalse(manager.activeNotifications.any { it.tag == event.identity.trackingKey })

            application.sendBroadcast(
                boundaryIntent(
                    application,
                    NotificationBoundaryIdentity(
                        shiftId = shift.id,
                        type = NotificationBoundaryType.START,
                        triggerAt = shift.startAt,
                    ),
                ),
            )
            SystemClock.sleep(1_000L)
            assertTrue(event.identity.trackingKey in application.notificationPreferences.dismissedEventKeys())
            assertFalse(manager.activeNotifications.any { it.tag == event.identity.trackingKey })

            assertTrue(application.notificationRuntime.restoreNow(event.identity.trackingKey))
            waitUntil(5_000L) {
                manager.activeNotifications.any { it.tag == event.identity.trackingKey }
            }
            val restored = manager.activeNotifications.first { it.tag == event.identity.trackingKey }.notification
            assertTrue(restored.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
            assertFalse(event.identity.trackingKey in application.notificationPreferences.dismissedEventKeys())
            assertTrue(event.identity.trackingKey in application.notificationPreferences.displayedEventKeys())
        } finally {
            application.notificationPreferences.setEnabled(false)
            application.notificationPreferences.clearEventTracking("shift:${shift.id}")
            deleteIfPresent(application, shift.id)
            manager.cancel("shift:${shift.id}", 1042)
            application.notificationRuntime.reconcile()
        }
    }

    @Test
    fun availabilityStartBoundaryReadsThePersistedV2WindowAndPostsItsHistoricalLabel() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        assumeTrue("Las notificaciones instrumentadas sólo pueden tocar el paquete QA.", application.packageName.endsWith(".qa"))
        grantPostNotificationsIfRequired(application)
        val manager = application.getSystemService(NotificationManager::class.java)
        val zone = AppDefaults.zoneId()
        val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        val windowId = AVAILABILITY_WINDOW_ID
        val windowStart = now.minus(1, ChronoUnit.MINUTES)
        val windowEnd = now.plus(60, ChronoUnit.MINUTES)
        val ownerDate = windowStart.atZone(zone).toLocalDate()
        val seed = futureShift(
            id = AVAILABILITY_SEED_SHIFT_ID,
            start = now.plus(120, ChronoUnit.MINUTES),
            end = now.plus(180, ChronoUnit.MINUTES),
            createdAt = now.minus(1, ChronoUnit.DAYS),
        )
        var trackingKey: String? = null
        try {
            V2AppTestFixture.writeFor(application.localDataStore, seed, ownerDate)
            val originalHistory = requireNotNull(application.localDataStore.workConfiguration.get())
            val previous = requireNotNull(originalHistory.timeline.revisionAt(ownerDate))
            val configurationResult = application.localDataStore.workConfiguration.applyAvailabilityMutation(
                WorkConfigurationAvailabilityMutation(
                    originalHistory,
                    EffectiveRevision(
                        id = AVAILABILITY_REVISION_ID,
                        effectiveFrom = ownerDate,
                        value = previous.value.copy(availabilityLabel = AvailabilityLabel.ON_CALL_RETAINER),
                    ),
                ),
            )
            assertTrue(configurationResult is WorkConfigurationAvailabilityWriteResult.Saved)
            val resolved = ResolvedWorkConfigurationRevision.resolve(
                history = requireNotNull(application.localDataStore.workConfiguration.get()),
                date = ownerDate,
            )
            val record = buildAvailabilityWindowRecord(
                draft = AvailabilityWindowDraft(
                    id = windowId,
                    ownerLocalDate = ownerDate,
                    zoneId = zone,
                    start = windowStart,
                    end = windowEnd,
                ),
                configuration = resolved,
                timestamp = now,
            )
            val expectation = application.localDataStore.availabilityWindows.captureExpectation(
                id = null,
                configuration = resolved,
                windowStart = windowStart,
                windowEnd = windowEnd,
            )
            assertTrue(
                application.localDataStore.availabilityWindows.applyMutation(
                    AvailabilityWindowMutation(expectation, record),
                ) is AvailabilityWindowWriteResult.Saved,
            )
            val eventIdentity = NextEventIdentity.Availability(windowId, windowStart, windowEnd)
            trackingKey = eventIdentity.trackingKey
            application.notificationPreferences.setEnabled(true)

            application.sendBroadcast(
                boundaryIntent(
                    application,
                    NotificationBoundaryIdentity(
                        eventIdentity = eventIdentity,
                        type = NotificationBoundaryType.START,
                        triggerAt = windowStart,
                    ),
                ),
            )
            waitUntil(5_000L) {
                manager.activeNotifications.any { it.tag == eventIdentity.trackingKey }
            }
            val posted = manager.activeNotifications.first { it.tag == eventIdentity.trackingKey }.notification
            assertTrue(posted.extras.getString(Notification.EXTRA_TITLE).orEmpty().contains("Retén"))
            assertFalse(posted.extras.toString().contains("Dirección"))
        } finally {
            application.notificationPreferences.setEnabled(false)
            trackingKey?.let { key ->
                application.notificationPreferences.clearEventTracking(key)
                manager.cancel(key, ShiftNotificationPresenter.NOTIFICATION_ID)
            }
            application.localDataStore.clearAllDataForInstrumentation()
            application.notificationRuntime.reconcile()
        }
    }

    @Test
    fun realQaAlarmsDeliverReminderUpdateAtStartAndCancelAtEnd() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        assumeTrue("El recorrido temporal sólo puede modificar el paquete QA.", application.packageName.endsWith(".qa"))
        grantPostNotificationsIfRequired(application)
        assumeTrue(
            "Recorrido temporal omitido: QA no tiene acceso a alarmas exactas y la prueba no debe habilitarlo.",
            NotificationSystemAccess(application).read().exactAlarmAccessGranted,
        )

        val notificationManager = application.getSystemService(NotificationManager::class.java)
        val now = Instant.now()
        val start = now.atZone(AppDefaults.zoneId())
            .plusMinutes(2)
            .withSecond(0)
            .withNano(0)
            .toInstant()
        val end = start.plusSeconds(60)
        val shift = futureShift(SHIFT_ID, start, end, now)
        try {
            application.notificationPreferences.setGlobalReminderLeadMinutes(listOf(1L))
            application.notificationPreferences.setPreciseTiming(true)
            application.notificationPreferences.setEnabled(true)
            insertV2(application, shift)
            application.notificationRuntime.reconcile()

            val reminder = waitForNotification(notificationManager, "PRÓXIMA JORNADA", 75_000L)
            assertTrue(reminder.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("QAT"))

            val ongoing = waitForNotification(notificationManager, "JORNADA EN CURSO", 135_000L)
            assertFalse(ongoing.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
            assertFalse(ongoing.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
            assertFalse(ongoing.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
            val countdown = ongoing.bigContentView.apply(application, null)
                .findViewById<Chronometer>(R.id.notification_countdown)
            assertTrue(countdown.isCountDown)
            assertTrue(countdown.format.toString().startsWith("Finaliza en"))

            waitUntil(75_000L) {
                notificationManager.activeNotifications.none { it.tag == "shift:$SHIFT_ID" }
            }
            assertFalse(notificationManager.activeNotifications.any { it.tag == "shift:$SHIFT_ID" })
        } finally {
            application.notificationPreferences.setEnabled(false)
            deleteIfPresent(application, SHIFT_ID)
            application.notificationRuntime.reconcile()
            notificationManager.cancel("shift:$SHIFT_ID", 1042)
        }
    }

    private fun futureShift(id: UUID, start: Instant, end: Instant, createdAt: Instant): Shift {
        val zone = AppDefaults.zoneId()
        val exactStart = start.atZone(zone).withSecond(0).withNano(0)
        val exactEnd = end.atZone(zone).withSecond(0).withNano(0)
        return Shift(
            id = id,
            startAt = exactStart.toInstant(),
            endAt = exactEnd.toInstant(),
            zoneId = zone,
            localStartDate = exactStart.toLocalDate(),
            objectiveNameSnapshot = "Objetivo temporal ficticio",
            objectiveAbbreviationSnapshot = "QAT",
            objectiveAddressSnapshot = "Dirección ficticia 123",
            startTimeSnapshot = exactStart.toLocalTime(),
            endTimeSnapshot = exactEnd.toLocalTime(),
            colorArgbSnapshot = 0xff336699.toInt(),
            position = "Puesto ficticio",
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }

    private suspend fun insertV2(
        application: MiGuardiaApplication,
        shift: Shift,
    ): V2ShiftWrite {
        val write = V2AppTestFixture.writeFor(
            store = application.localDataStore,
            shift = shift,
            effectiveFrom = shift.localStartDate,
        )
        application.localDataStore.v2Shifts.insert(write)
        return requireV2Write(application, write.shift.id)
    }

    private suspend fun requireV2Write(
        application: MiGuardiaApplication,
        shiftId: UUID,
    ): V2ShiftWrite = when (val lookup = application.localDataStore.v2Shifts.getShift(shiftId)) {
        is V2ShiftLookup.V2 -> lookup.write
        V2ShiftLookup.Missing -> error("La jornada QA no quedó persistida como V2.")
    }

    private suspend fun deleteIfPresent(application: MiGuardiaApplication, shiftId: UUID) {
        val lookup = application.localDataStore.v2Shifts.getShift(shiftId)
        if (lookup is V2ShiftLookup.V2) {
            application.localDataStore.v2Shifts.deleteShift(lookup.write)
        }
    }

    private fun grantPostNotificationsIfRequired(application: MiGuardiaApplication) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                application.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    private suspend fun installedFor(application: MiGuardiaApplication, shiftId: UUID): Set<String> =
        application.notificationPreferences.installedBoundaryKeys().filterTo(linkedSetOf()) {
            it.startsWith("v2|shift:$shiftId|")
        }

    private fun boundaryIntent(
        application: MiGuardiaApplication,
        identity: NotificationBoundaryIdentity,
    ): Intent = Intent(application, ShiftAlarmReceiver::class.java)
        .setAction(ShiftAlarmReceiver.ACTION_DELIVER_BOUNDARY)
        .setData(boundaryData(identity.opaqueKey))

    private fun boundaryIntent(opaqueKey: String): Intent = Intent()
        .setAction(ShiftAlarmReceiver.ACTION_DELIVER_BOUNDARY)
        .setData(boundaryData(opaqueKey))

    private fun boundaryData(opaqueKey: String): Uri = Uri.Builder()
        .scheme("miguardia")
        .authority("shift-alarm")
        .appendQueryParameter("boundary", opaqueKey)
        .build()

    private suspend fun waitForNotification(
        manager: NotificationManager,
        expectedTitle: String,
        timeoutMillis: Long,
    ): Notification {
        var found: Notification? = null
        waitUntil(timeoutMillis) {
            found = manager.activeNotifications
                .firstOrNull { it.tag == "shift:$SHIFT_ID" }
                ?.notification
                ?.takeIf { it.extras.getString(Notification.EXTRA_TITLE).orEmpty().startsWith(expectedTitle) }
            found != null
        }
        return requireNotNull(found) { "No llegó el estado QA esperado: $expectedTitle" }
    }

    private suspend fun waitUntil(timeoutMillis: Long, condition: suspend () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(250)
        }
        assertTrue("La frontera temporal QA no llegó dentro del margen esperado.", condition())
    }

    private companion object {
        const val QA_APPLICATION_ID: String = "com.blackatsystems.miguardia.qa"
        const val PACKAGE_REPLACEMENT_PHASE_ARGUMENT: String = "packageReplacementPhase"
        const val PACKAGE_REPLACEMENT_REBUILT_EPOCH_ARGUMENT: String = "packageReplacementRebuiltEpoch"
        const val PACKAGE_REPLACEMENT_PREPARE: String = "prepare"
        const val PACKAGE_REPLACEMENT_VERIFY: String = "verify"
        const val PACKAGE_REPLACEMENT_TEST_TARGET: String =
            "com.blackatsystems.miguardia.NotificationAlarmEndToEndInstrumentedTest#rebuildRestoresCurrentBoundaries"
        val SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000801")
        val REACTIVE_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000802")
        val VACATION_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000803")
        val STALE_END_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000804")
        val DISMISS_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000805")
        val AVAILABILITY_WINDOW_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000806")
        val AVAILABILITY_SEED_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000807")
        val AVAILABILITY_REVISION_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000808")
        val REBUILD_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000809")
        val STARTUP_RECOVERY_SHIFT_ID: UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000810")
    }

    private data class RebuildFixture(
        val persistedEvent: com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem.Shift,
        val trackingKey: String,
        val currentBoundaries: Set<String>,
    )
}
