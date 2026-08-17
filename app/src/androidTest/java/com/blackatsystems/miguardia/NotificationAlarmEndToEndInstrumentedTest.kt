package com.blackatsystems.miguardia

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Chronometer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryIdentity
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryType
import com.blackatsystems.miguardia.notifications.NotificationSystemAccess
import com.blackatsystems.miguardia.notifications.ShiftAlarmReceiver
import com.blackatsystems.miguardia.notifications.ShiftNotificationPresenter
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationAlarmEndToEndInstrumentedTest {
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
        try {
            application.notificationPreferences.setGlobalReminderLeadMinutes(listOf(60L))
            application.notificationPreferences.setEnabled(true)
            application.localDataStore.shifts.insert(original)
            application.notificationRuntime.reconcile()
            waitUntil(5_000L) { installedFor(application, original.id).size == 3 }
            val originalKeys = installedFor(application, original.id)

            application.notificationRuntime.rebuildNow()
            waitUntil(5_000L) { installedFor(application, original.id) == originalKeys }

            val edited = original.copy(
                startAt = original.startAt.plusSeconds(1800),
                endAt = original.endAt.plusSeconds(1800),
                updatedAt = Instant.now(),
            )
            application.localDataStore.shifts.update(edited)
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
            application.localDataStore.vacations.delete(vacation.id)
            waitUntil(5_000L) { installedFor(application, original.id).size == 3 }

            application.localDataStore.shifts.delete(original.id)
            waitUntil(5_000L) { installedFor(application, original.id).isEmpty() }
        } finally {
            application.notificationPreferences.setEnabled(false)
            application.localDataStore.shiftNotificationConfigs.clear(original.id)
            application.localDataStore.vacations.delete(vacation.id)
            application.localDataStore.shifts.delete(original.id)
            application.notificationRuntime.reconcile()
        }
    }

    @Test
    fun staleEndBoundaryDoesNotCancelEditedShiftNotification() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        assumeTrue("Las notificaciones instrumentadas sólo pueden tocar el paquete QA.", application.packageName.endsWith(".qa"))
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            application.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
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
            application.localDataStore.shifts.insert(shift)
            ShiftNotificationPresenter(application).show(shift, now, application.notificationPreferences.current())
            application.notificationPreferences.markDisplayed(shift.id.toString())

            application.sendBroadcast(boundaryIntent(application, staleEnd))
            waitUntil(5_000L) {
                manager.activeNotifications.any { it.tag == shift.id.toString() }
            }
            assertTrue(manager.activeNotifications.any { it.tag == shift.id.toString() })
        } finally {
            application.notificationPreferences.setEnabled(false)
            application.notificationPreferences.clearShiftTracking(shift.id.toString())
            application.localDataStore.shifts.delete(shift.id)
            manager.cancel(shift.id.toString(), 1042)
            application.notificationRuntime.reconcile()
        }
    }

    @Test
    fun dismissibleOngoingNotificationStaysDismissedUntilAnotherBoundary() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        assumeTrue("Las notificaciones instrumentadas sólo pueden tocar el paquete QA.", application.packageName.endsWith(".qa"))
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            application.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val manager = application.getSystemService(NotificationManager::class.java)
        val now = Instant.now()
        val shift = futureShift(DISMISS_SHIFT_ID, now.minusSeconds(60), now.plusSeconds(3600), now)
        try {
            application.notificationPreferences.setEnabled(true)
            application.notificationPreferences.setPersistentWhileActive(false)
            application.localDataStore.shifts.insert(shift)
            ShiftNotificationPresenter(application).show(shift, now, application.notificationPreferences.current())
            application.notificationPreferences.markDisplayed(shift.id.toString())
            val posted = manager.activeNotifications.first { it.tag == shift.id.toString() }.notification

            assertTrue(posted.deleteIntent != null)
            manager.cancel(shift.id.toString(), ShiftNotificationPresenter.NOTIFICATION_ID)
            application.notificationRuntime.dismissNow(shift.id.toString())
            assertTrue(shift.id.toString() in application.notificationPreferences.dismissedShiftIds())
            application.notificationRuntime.reconcileNow()
            waitUntil(5_000L) {
                manager.activeNotifications.none { it.tag == shift.id.toString() }
            }
            assertFalse(manager.activeNotifications.any { it.tag == shift.id.toString() })
        } finally {
            application.notificationPreferences.setEnabled(false)
            application.notificationPreferences.clearShiftTracking(shift.id.toString())
            application.localDataStore.shifts.delete(shift.id)
            manager.cancel(shift.id.toString(), 1042)
            application.notificationRuntime.reconcile()
        }
    }

    @Test
    fun realQaAlarmsDeliverReminderUpdateAtStartAndCancelAtEnd() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        assumeTrue("El recorrido temporal sólo puede modificar el paquete QA.", application.packageName.endsWith(".qa"))
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            application.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        assertTrue("QA necesita acceso exacto para este recorrido físico.", NotificationSystemAccess(application).read().exactAlarmAccessGranted)

        val notificationManager = application.getSystemService(NotificationManager::class.java)
        val now = Instant.now()
        val start = now.plusSeconds(70)
        val end = start.plusSeconds(20)
        val shift = futureShift(SHIFT_ID, start, end, now)
        try {
            application.notificationPreferences.setGlobalReminderLeadMinutes(listOf(1L))
            application.notificationPreferences.setPreciseTiming(true)
            application.notificationPreferences.setEnabled(true)
            application.localDataStore.shifts.insert(shift)
            application.notificationRuntime.reconcile()

            val reminder = waitForNotification(notificationManager, "Entrás a las", 25_000L)
            assertTrue(reminder.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("QAT"))

            val ongoing = waitForNotification(notificationManager, "Guardia en curso", 75_000L)
            assertFalse(ongoing.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
            assertFalse(ongoing.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
            assertFalse(ongoing.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
            val countdown = ongoing.bigContentView.apply(application, null)
                .findViewById<Chronometer>(R.id.notification_countdown)
            assertTrue(countdown.isCountDown)
            assertTrue(countdown.format.toString().startsWith("Finaliza en"))

            waitUntil(30_000L) {
                notificationManager.activeNotifications.none { it.tag == SHIFT_ID.toString() }
            }
            assertFalse(notificationManager.activeNotifications.any { it.tag == SHIFT_ID.toString() })
        } finally {
            application.notificationPreferences.setEnabled(false)
            application.localDataStore.shifts.delete(SHIFT_ID)
            application.notificationRuntime.reconcile()
            notificationManager.cancel(SHIFT_ID.toString(), 1042)
        }
    }

    private fun futureShift(id: UUID, start: Instant, end: Instant, createdAt: Instant): Shift {
        val zone = AppDefaults.zoneId()
        return Shift(
            id = id,
            startAt = start,
            endAt = end,
            zoneId = zone,
            localStartDate = start.atZone(zone).toLocalDate(),
            objectiveNameSnapshot = "Objetivo temporal ficticio",
            objectiveAbbreviationSnapshot = "QAT",
            objectiveAddressSnapshot = "Dirección ficticia 123",
            startTimeSnapshot = LocalTime.from(start.atZone(zone)),
            endTimeSnapshot = LocalTime.from(end.atZone(zone)),
            colorArgbSnapshot = 0xff336699.toInt(),
            position = "Puesto ficticio",
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = null,
            sourceScheduleCombinationId = null,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }

    private suspend fun installedFor(application: MiGuardiaApplication, shiftId: UUID): Set<String> =
        application.notificationPreferences.installedBoundaryKeys().filterTo(linkedSetOf()) {
            it.startsWith(shiftId.toString())
        }

    private fun boundaryIntent(
        application: MiGuardiaApplication,
        identity: NotificationBoundaryIdentity,
    ): Intent = Intent(application, ShiftAlarmReceiver::class.java)
        .setAction(ShiftAlarmReceiver.ACTION_DELIVER_BOUNDARY)
        .setData(
            Uri.Builder()
                .scheme("miguardia")
                .authority("shift-alarm")
                .appendQueryParameter("boundary", identity.opaqueKey)
                .build(),
        )

    private suspend fun waitForNotification(
        manager: NotificationManager,
        expectedTitle: String,
        timeoutMillis: Long,
    ): Notification {
        var found: Notification? = null
        waitUntil(timeoutMillis) {
            found = manager.activeNotifications
                .firstOrNull { it.tag == SHIFT_ID.toString() }
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
        val SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000801")
        val REACTIVE_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000802")
        val VACATION_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000803")
        val STALE_END_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000804")
        val DISMISS_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000805")
    }
}
