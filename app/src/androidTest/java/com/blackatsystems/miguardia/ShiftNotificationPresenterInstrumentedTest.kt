package com.blackatsystems.miguardia

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationAttentionMode
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
import org.junit.Assert.assertNotNull
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        manager.cancelAll()
        waitForNotifications { manager.activeNotifications.isEmpty() }
    }

    @After
    fun clearQaNotifications() {
        if (context.packageName.endsWith(".qa")) manager.cancelAll()
    }

    @Test
    fun completeReminderUsesStableUuidIdentityHistoricalContentAndTwoActions() {
        val shift = shift(SHIFT_ONE)
        presenter.show(shift, NOW, NotificationPreferences(enabled = true))

        val posted = notificationForTag(shift.id)
        assertEquals("PRÓXIMA GUARDIA · Objetivo ficticio", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("QA · 19:00–07:00", posted.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(2, posted.actions.size)
        assertEquals(listOf("Ver detalles", "Cómo llegar"), posted.actions.map { it.title.toString() })
        assertEquals(2, posted.actions.map { it.actionIntent }.toSet().size)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertTrue(posted.actions.all { it.isAuthenticationRequired })
        }
        assertTrue(posted.deleteIntent != null)
        assertEquals(ShiftNotificationPresenter.GROUP_KEY, posted.group)
        assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertNotNull(posted.contentView)
        val compact = posted.contentView.apply(context, null)
        assertEquals(
            "PRÓXIMA GUARDIA · Objetivo ficticio",
            compact.findViewById<TextView>(R.id.notification_title).text.toString(),
        )
        assertEquals(
            "QA · 19:00–07:00",
            compact.findViewById<TextView>(R.id.notification_schedule).text.toString(),
        )
        assertEquals(
            shift.colorArgbSnapshot,
            (compact.findViewById<View>(R.id.notification_accent).background as ColorDrawable).color,
        )
        val compactCountdown = compact
            .findViewById<Chronometer>(R.id.notification_countdown)
        assertTrue(compactCountdown.isCountDown)
        assertTrue(compactCountdown.format.toString().startsWith("Comienza en"))
        val expanded = posted.bigContentView.apply(context, null)
        assertEquals("PRÓXIMA GUARDIA", expanded.findViewById<TextView>(R.id.notification_title).text.toString())
        assertEquals("Objetivo ficticio", expanded.findViewById<TextView>(R.id.notification_objective).text.toString())
        assertEquals("QA · Horario 19:00–07:00", expanded.findViewById<TextView>(R.id.notification_schedule).text.toString())
        assertEquals("Puesto: Acceso", expanded.findViewById<TextView>(R.id.notification_position).text.toString())
        val dismissControl = expanded
            .findViewById<TextView>(R.id.notification_dismiss)
        assertEquals("Eliminar notificación", dismissControl.text.toString())
        assertTrue(dismissControl.hasOnClickListeners())

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
        assertEquals("EN CURSO", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("EN CURSO", posted.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Horario 19:00–07:00", posted.publicVersion.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertFalse(posted.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        val bodyCountdown = posted.bigContentView.apply(context, null)
            .findViewById<Chronometer>(R.id.notification_countdown)
        assertTrue(bodyCountdown.isCountDown)
        assertTrue(bodyCountdown.format.toString().startsWith("Finaliza en"))

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
        val hidden = posted.contentView.apply(context, null)
        assertEquals(View.GONE, hidden.findViewById<View>(R.id.notification_countdown).visibility)
        val hiddenAccent = (hidden.findViewById<View>(R.id.notification_accent).background as ColorDrawable).color
        assertNotEquals(shift.colorArgbSnapshot, hiddenAccent)
        assertEquals(hiddenAccent, posted.color)
    }

    @Test
    fun weatherAppearsOnlyInCompletePrivacyAndSilentRefreshDoesNotAlertAgain() {
        val shift = shift(SHIFT_ONE)
        presenter.show(
            shift,
            NOW,
            NotificationPreferences(enabled = true, privacy = NotificationPrivacy.COMPLETE),
            weatherText = "Clima: Lluvia · 12–18 °C",
            silentUpdate = true,
        )
        var posted = notificationForTag(shift.id)
        var expanded = posted.bigContentView.apply(context, null)
        assertEquals(
            "Clima: Lluvia · 12–18 °C",
            expanded.findViewById<TextView>(R.id.notification_weather).text.toString(),
        )
        assertTrue(posted.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)

        presenter.show(
            shift,
            NOW,
            NotificationPreferences(enabled = true, privacy = NotificationPrivacy.REDUCED),
            weatherText = "Clima: dato que debe omitirse",
        )
        posted = notificationForTag(shift.id) {
            it.publicVersion?.extras?.getString(Notification.EXTRA_TEXT) == "Horario 19:00–07:00"
        }
        expanded = posted.bigContentView.apply(context, null)
        assertEquals(View.GONE, expanded.findViewById<View>(R.id.notification_weather).visibility)
        assertFalse(posted.publicVersion.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("Clima:"))
    }

    @Test
    fun simultaneousGuardsStaySeparateAndAttentionModesOwnOneVersionedChannel() {
        val first = shift(SHIFT_ONE)
        val second = shift(SHIFT_TWO)
        val legacyChannelId = "guard_shifts_v1_legacy"
        manager.createNotificationChannel(
            NotificationChannel(legacyChannelId, "Canal QA anterior", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val custom = NotificationPreferences(
            enabled = true,
            soundUri = Uri.parse("content://settings/system/alarm_alert"),
        )
        presenter.show(first, NOW, custom)
        val channelBefore = notificationForTag(first.id).channelId
        presenter.show(second, NOW, custom)
        presenter.updateGroupSummary(2, custom)

        val expectedIndividualTags = setOf(first.id.toString(), second.id.toString())
        waitForNotifications {
            val active = manager.activeNotifications
            expectedIndividualTags.all { expectedTag -> active.any { it.tag == expectedTag } } &&
                active.any {
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 &&
                        it.notification.group == ShiftNotificationPresenter.GROUP_KEY
                }
        }
        val active = manager.activeNotifications
        val individual = active.filter { it.tag == first.id.toString() || it.tag == second.id.toString() }
        assertEquals(2, individual.size)
        assertTrue(individual.all { it.notification.group == ShiftNotificationPresenter.GROUP_KEY })
        assertTrue(active.any { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 })
        assertTrue(channelBefore.startsWith(ShiftNotificationPresenter.CHANNEL_PREFIX))
        assertTrue(manager.getNotificationChannel(channelBefore).shouldVibrate())
        assertNotNull(manager.getNotificationChannel(channelBefore).sound)
        assertTrue(manager.notificationChannels.none { it.id == legacyChannelId })

        presenter.show(
            first,
            NOW,
            custom.copy(attentionMode = NotificationAttentionMode.VIBRATION_ONLY),
        )
        val vibrationChannelId = notificationForTag(first.id).channelId
        val vibrationChannel = manager.getNotificationChannel(vibrationChannelId)
        assertNotEquals(channelBefore, vibrationChannelId)
        assertTrue(vibrationChannel.shouldVibrate())
        assertEquals(null, vibrationChannel.sound)

        presenter.show(
            first,
            NOW,
            custom.copy(attentionMode = NotificationAttentionMode.SILENT),
        )
        val silentChannel = manager.getNotificationChannel(notificationForTag(first.id).channelId)
        assertFalse(silentChannel.shouldVibrate())
        assertEquals(null, silentChannel.sound)
        assertEquals(NotificationManager.IMPORTANCE_LOW, silentChannel.importance)
        assertEquals(1, manager.notificationChannels.count { it.id.startsWith(ShiftNotificationPresenter.OWNED_CHANNEL_PREFIX) })
    }

    @Test
    fun testNotificationUsesReservedIdentityNoShiftActionsAndExpiresAlone() {
        presenter.showTestNotification(
            NotificationPreferences(
                enabled = false,
                privacy = NotificationPrivacy.REDUCED,
                attentionMode = NotificationAttentionMode.SILENT,
            ),
        )

        val posted = notificationForTag(
            tag = ShiftNotificationPresenter.PREVIEW_TAG,
            id = ShiftNotificationPresenter.PREVIEW_NOTIFICATION_ID,
        )
        assertEquals("PRUEBA · PRÓXIMA", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Horario 19:00–07:00", posted.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(posted.actions.isNullOrEmpty())
        assertEquals(null, posted.group)
        assertEquals(null, posted.deleteIntent)
        assertFalse(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(60_000L, posted.timeoutAfter)
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
        sourceObjectiveId = UUID(0L, 266L),
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun notificationForTag(
        shiftId: UUID,
        predicate: (Notification) -> Boolean = { true },
    ): Notification = notificationForTag(shiftId.toString(), ShiftNotificationPresenter.NOTIFICATION_ID, predicate)

    private fun notificationForTag(
        tag: String,
        id: Int,
        predicate: (Notification) -> Boolean = { true },
    ): Notification {
        repeat(40) {
            manager.activeNotifications.firstOrNull { it.tag == tag && it.id == id }
                ?.notification
                ?.takeIf(predicate)
                ?.let { return it }
            SystemClock.sleep(50)
        }
        error("No se publicó la notificación QA esperada para la identidad $tag/$id.")
    }

    private fun waitForNotifications(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("Las notificaciones QA no alcanzaron el estado esperado.", condition())
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T20:00:00Z")
        val SHIFT_ONE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000701")
        val SHIFT_TWO: UUID = UUID.fromString("00000000-0000-0000-0000-000000000702")
    }
}
