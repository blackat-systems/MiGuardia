package com.blackatsystems.miguardia

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.notifications.ShiftNotificationPresenter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShiftNotificationPresenterInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager by lazy { context.getSystemService(NotificationManager::class.java) }
    private val presenter by lazy { ShiftNotificationPresenter(context) }

    @Before
    fun grantQaPermission() {
        assumeTrue("Las notificaciones instrumentadas sólo pueden tocar el paquete QA.", context.packageName.endsWith(".qa"))
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        manager.cancelAll()
    }

    @After
    fun clearQaNotifications() {
        if (context.packageName.endsWith(".qa")) manager.cancelAll()
    }

    @Test
    fun completeReminderUsesStableUuidIdentityHistoricalContentAndThreeActions() {
        val shift = shift(SHIFT_ONE)
        presenter.show(shift, NOW, NotificationPreferences(enabled = true))

        val posted = notificationForTag(shift.id)
        assertEquals("Próxima guardia", posted.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(posted.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("Objetivo ficticio (QA) · 19:00–07:00 · Puesto: Acceso"))
        assertEquals(3, posted.actions.size)
        assertEquals(listOf("Ver detalles", "Cómo llegar", "Informar novedad"), posted.actions.map { it.title.toString() })
        assertEquals(3, posted.actions.map { it.actionIntent }.toSet().size)
        assertTrue(posted.actions.all { it.isAuthenticationRequired })
        assertTrue(posted.deleteIntent != null)
        assertEquals(ShiftNotificationPresenter.GROUP_KEY, posted.group)
        assertFalse(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)

        presenter.show(shift, NOW.plusSeconds(60), NotificationPreferences(enabled = true))
        assertEquals(1, manager.activeNotifications.count { it.tag == shift.id.toString() })
    }

    @Test
    fun ongoingReducedAndHiddenVersionsProtectLockscreenContent() {
        val shift = shift(SHIFT_ONE, start = NOW.minusSeconds(60), end = NOW.plusSeconds(3600))
        presenter.show(
            shift,
            NOW,
            NotificationPreferences(enabled = true, persistentWhileActive = true, privacy = NotificationPrivacy.REDUCED),
        )
        var posted = notificationForTag(shift.id)
        assertEquals("Guardia en curso", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Guardia en curso · 19:00–07:00", posted.publicVersion.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(posted.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))

        presenter.show(
            shift,
            NOW,
            NotificationPreferences(enabled = true, privacy = NotificationPrivacy.HIDDEN),
        )
        posted = notificationForTag(shift.id) {
            it.publicVersion?.extras?.getString(Notification.EXTRA_TITLE) == "MiGuardia"
        }
        assertEquals("MiGuardia", posted.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Tenés un aviso de guardia.", posted.publicVersion.extras.getString(Notification.EXTRA_TEXT))
        assertFalse(posted.publicVersion.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("Objetivo"))
    }

    @Test
    fun simultaneousGuardsStaySeparateGroupedAndSoundCreatesDeterministicVersionedChannel() {
        val first = shift(SHIFT_ONE)
        val second = shift(SHIFT_TWO)
        val custom = NotificationPreferences(
            enabled = true,
            soundUri = Uri.parse("content://settings/system/alarm_alert"),
        )
        presenter.show(first, NOW, custom)
        val channelBefore = notificationForTag(first.id).channelId
        presenter.show(second, NOW, custom)
        presenter.updateGroupSummary(2, custom)

        val individual = manager.activeNotifications.filter { it.tag == first.id.toString() || it.tag == second.id.toString() }
        assertEquals(2, individual.size)
        assertTrue(individual.all { it.notification.group == ShiftNotificationPresenter.GROUP_KEY })
        assertTrue(manager.activeNotifications.any { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 })
        assertTrue(channelBefore.startsWith(ShiftNotificationPresenter.CHANNEL_PREFIX))

        presenter.show(first, NOW, custom.copy(soundUri = null))
        val defaultChannel = notificationForTag(first.id).channelId
        assertNotEquals(channelBefore, defaultChannel)
        assertEquals(1, manager.notificationChannels.count { it.id.startsWith(ShiftNotificationPresenter.CHANNEL_PREFIX) })
    }

    private fun shift(
        id: UUID,
        start: Instant = NOW.plusSeconds(3600),
        end: Instant = NOW.plusSeconds(13 * 3600),
    ) = Shift(
        id = id,
        startAt = start,
        endAt = end,
        zoneId = ZoneId.of("America/Argentina/Cordoba"),
        localStartDate = LocalDate.of(2026, 9, 1),
        objectiveNameSnapshot = "Objetivo ficticio",
        objectiveAbbreviationSnapshot = "QA",
        objectiveAddressSnapshot = "Calle de prueba 123",
        startTimeSnapshot = LocalTime.of(19, 0),
        endTimeSnapshot = LocalTime.of(7, 0),
        colorArgbSnapshot = 0xff336699.toInt(),
        position = "Acceso",
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = null,
        sourceScheduleCombinationId = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun notificationForTag(
        shiftId: UUID,
        predicate: (Notification) -> Boolean = { true },
    ): Notification {
        repeat(40) {
            manager.activeNotifications.firstOrNull { it.tag == shiftId.toString() }
                ?.notification
                ?.takeIf(predicate)
                ?.let { return it }
            SystemClock.sleep(50)
        }
        error("No se publicó la notificación QA esperada para el UUID ficticio.")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T20:00:00Z")
        val SHIFT_ONE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000701")
        val SHIFT_TWO: UUID = UUID.fromString("00000000-0000-0000-0000-000000000702")
    }
}
