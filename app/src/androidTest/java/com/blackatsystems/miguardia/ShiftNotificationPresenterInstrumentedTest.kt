package com.blackatsystems.miguardia

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationAttentionMode
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.notifications.ShiftNotificationPresenter
import com.blackatsystems.miguardia.weather.WeatherCredits
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.io.File
import kotlinx.coroutines.runBlocking
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
    private val application: MiGuardiaApplication = ApplicationProvider.getApplicationContext()
    private val context: Context = application
    private val manager by lazy { context.getSystemService(NotificationManager::class.java) }
    private val presenter by lazy { ShiftNotificationPresenter(context) }

    @Before
    fun grantQaPermission() = runBlocking {
        assumeTrue("Las notificaciones instrumentadas sólo pueden tocar el paquete QA.", context.packageName.endsWith(".qa"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        com.blackatsystems.miguardia.beta.PublicQaRootFixture.awaitReady(application)
        application.notificationPreferences.setEnabled(false)
        application.notificationPreferences.setPreciseTiming(false)
        application.notificationRuntime.rebuildNow()
        manager.cancelAll()
        waitForNotifications { manager.activeNotifications.isEmpty() }
    }

    @After
    fun clearQaNotifications() = runBlocking {
        if (context.packageName.endsWith(".qa")) {
            application.notificationPreferences.setEnabled(false)
            application.notificationPreferences.setPreciseTiming(false)
            application.notificationRuntime.rebuildNow()
            manager.cancelAll()
            waitForNotifications { manager.activeNotifications.isEmpty() }
        }
    }

    @Test
    fun recoveryCoverPreservesExactDestinationsAndDismissalsWithoutPrivateContent() {
        val events = listOf(shift(SHIFT_ONE), availability(SHIFT_TWO))
        events.forEach { event -> presenter.show(event, NOW, NotificationPreferences(enabled = true)) }
        val originals = events.associate { event ->
            event.identity.trackingKey to notificationForTag(event.identity.trackingKey, ShiftNotificationPresenter.NOTIFICATION_ID)
        }
        try {
            application.startupRecoveryGate.recovering()
            // Both showRecoveryForExisting and the final publication gate convert the notification.
            presenter.showRecoveryForExisting()
            originals.forEach { (tag, original) ->
                val covered = notificationForTag(tag, ShiftNotificationPresenter.NOTIFICATION_ID) {
                    it.extras.getString(Notification.EXTRA_TITLE) == "MiGuardia"
                }
                assertNotNull(original.contentIntent)
                assertNotNull(original.deleteIntent)
                assertEquals(original.contentIntent, covered.contentIntent)
                assertEquals(original.deleteIntent, covered.deleteIntent)
                assertEquals("Abrí MiGuardia para terminar la recuperación de tus datos.", covered.extras.getString(Notification.EXTRA_TEXT))
                assertTrue(covered.actions.isNullOrEmpty())
                assertEquals(null, covered.contentView)
                assertEquals(null, covered.bigContentView)
                assertEquals(null, covered.publicVersion)
                assertEquals(Notification.VISIBILITY_PUBLIC, covered.visibility)
            }
            presenter.showRecoveryForExisting()
            originals.forEach { (tag, original) ->
                val covered = notificationForTag(tag, ShiftNotificationPresenter.NOTIFICATION_ID)
                assertEquals(original.contentIntent, covered.contentIntent)
                assertEquals(original.deleteIntent, covered.deleteIntent)
            }
        } finally {
            application.startupRecoveryGate.ready()
        }
    }

    @Test
    fun completeReminderUsesStableUuidIdentityHistoricalContentAndTwoActions() {
        val shift = shift(SHIFT_ONE)
        presenter.show(shift, NOW, NotificationPreferences(enabled = true))

        val posted = notificationForTag(shift.shiftId)
        assertEquals(
            "PRÓXIMA JORNADA · Jornada ficticia · Objetivo ficticio",
            posted.extras.getString(Notification.EXTRA_TITLE),
        )
        assertEquals("QA · 19:00–07:00", posted.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(2, posted.actions.size)
        assertEquals(listOf("Ver detalles", "Cómo llegar"), posted.actions.map { it.title.toString() })
        assertEquals(2, posted.actions.map { it.actionIntent }.toSet().size)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertTrue(posted.actions.all { it.isAuthenticationRequired })
        }
        assertTrue(posted.deleteIntent != null)
        assertEquals(ShiftNotificationPresenter.GROUP_KEY, posted.group)
        assertFalse(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertNotNull(posted.contentView)
        val compact = posted.contentView.apply(context, null)
        assertEquals(
            "PRÓXIMA JORNADA · Jornada ficticia · Objetivo ficticio",
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
        assertEquals("PRÓXIMA JORNADA", expanded.findViewById<TextView>(R.id.notification_title).text.toString())
        assertEquals(
            "Jornada ficticia · Objetivo ficticio",
            expanded.findViewById<TextView>(R.id.notification_objective).text.toString(),
        )
        assertEquals("QA · Horario 19:00–07:00", expanded.findViewById<TextView>(R.id.notification_schedule).text.toString())
        assertEquals("Puesto: Acceso", expanded.findViewById<TextView>(R.id.notification_position).text.toString())
        assertEquals(View.GONE, expanded.findViewById<View>(R.id.notification_weather_credit).visibility)
        val dismissControl = expanded
            .findViewById<TextView>(R.id.notification_dismiss)
        assertEquals("Eliminar notificación", dismissControl.text.toString())
        assertTrue(dismissControl.hasOnClickListeners())

        presenter.show(shift, NOW.plusSeconds(60), NotificationPreferences(enabled = true))
        assertEquals(1, manager.activeNotifications.count { it.tag == shift.identity.trackingKey })
    }

    @Test
    fun ongoingReducedAndHiddenVersionsProtectLockscreenContent() {
        val shift = shift(SHIFT_ONE, start = NOW.minusSeconds(60), end = NOW.plusSeconds(3600))
        presenter.show(
            shift,
            NOW,
            NotificationPreferences(enabled = true, persistentWhileActive = true, privacy = NotificationPrivacy.REDUCED),
        )
        var posted = notificationForTag(shift.shiftId)
        assertEquals("JORNADA EN CURSO", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("JORNADA EN CURSO", posted.publicVersion.extras.getString(Notification.EXTRA_TITLE))
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
        posted = notificationForTag(shift.shiftId) {
            it.publicVersion?.extras?.getString(Notification.EXTRA_TITLE) == "MiGuardia"
        }
        assertEquals("MiGuardia", posted.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Tenés un aviso de MiGuardia.", posted.publicVersion.extras.getString(Notification.EXTRA_TEXT))
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
        var posted = notificationForTag(shift.shiftId)
        var expanded = posted.bigContentView.apply(context, null)
        assertEquals(
            "Clima: Lluvia · 12–18 °C",
            expanded.findViewById<TextView>(R.id.notification_weather).text.toString(),
        )
        assertTrue(posted.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        val credit = expanded.findViewById<TextView>(R.id.notification_weather_credit)
        assertEquals(WeatherCredits.SUMMARY, credit.text.toString())
        assertTrue(credit.hasOnClickListeners())
        assertTrue(credit.contentDescription.toString().contains(WeatherCredits.OPEN))
        assertFalse(expanded.findViewById<TextView>(R.id.notification_weather).text.contains("Open-Meteo"))

        presenter.show(
            shift,
            NOW,
            NotificationPreferences(enabled = true, privacy = NotificationPrivacy.REDUCED),
            weatherText = "Clima: dato que debe omitirse",
        )
        posted = notificationForTag(shift.shiftId) {
            it.publicVersion?.extras?.getString(Notification.EXTRA_TEXT) == "Horario 19:00–07:00"
        }
        expanded = posted.bigContentView.apply(context, null)
        assertEquals(View.GONE, expanded.findViewById<View>(R.id.notification_weather).visibility)
        assertEquals(View.GONE, expanded.findViewById<View>(R.id.notification_weather_credit).visibility)
        assertFalse(posted.publicVersion.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("Clima:"))
    }

    @Test
    fun longWeatherKeepsSeparateCreditAndHiddenRefreshRemovesIt() {
        val shift = shift(SHIFT_ONE)
        presenter.show(shift, NOW, NotificationPreferences(enabled = true), weatherText = "Clima: lluvia intensa · 12–18 °C ".repeat(30))
        val complete = notificationForTag(shift.shiftId).bigContentView.apply(context, null)
        assertEquals(WeatherCredits.SUMMARY, complete.findViewById<TextView>(R.id.notification_weather_credit).text.toString())
        assertEquals(2, complete.findViewById<TextView>(R.id.notification_weather).maxLines)
        val density = context.resources.displayMetrics.density
        complete.measure(
            View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        complete.layout(0, 0, complete.measuredWidth, complete.measuredHeight)
        val credit = complete.findViewById<TextView>(R.id.notification_weather_credit)
        assertEquals(0, credit.layout.getEllipsisCount(credit.layout.lineCount - 1))
        val bitmap = Bitmap.createBitmap(complete.width, complete.height, Bitmap.Config.ARGB_8888)
        try {
            complete.draw(Canvas(bitmap))
            val evidence = File(context.cacheDir, "credits-notification-visual")
            check(evidence.mkdirs() || evidence.isDirectory)
            File(evidence, "expanded-long.png").outputStream()
                .use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        presenter.show(shift, NOW, NotificationPreferences(enabled = true, privacy = NotificationPrivacy.HIDDEN), weatherText = "Clima privado")
        val hidden = notificationForTag(shift.shiftId) {
            it.publicVersion?.extras?.getString(Notification.EXTRA_TITLE) == "MiGuardia"
        }.bigContentView.apply(context, null)
        listOf(R.id.notification_weather, R.id.notification_weather_credit).forEach { id ->
            assertEquals(View.GONE, hidden.findViewById<View>(id).visibility)
            assertEquals("", hidden.findViewById<TextView>(id).text.toString())
        }
    }

    @Test
    fun syntheticWeatherPreviewDoesNotClaimOpenMeteoData() {
        presenter.showTestNotification(NotificationPreferences(privacy = NotificationPrivacy.COMPLETE))
        val view = notificationForTag(ShiftNotificationPresenter.PREVIEW_TAG, ShiftNotificationPresenter.PREVIEW_NOTIFICATION_ID)
            .bigContentView.apply(context, null)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.notification_weather).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.notification_weather_credit).visibility)
    }

    @Test
    fun availabilityUsesItsHistoricalLabelOnlyAndOpensOneDetailAction() {
        val availability = availability(SHIFT_ONE)
        presenter.show(
            availability,
            NOW,
            NotificationPreferences(enabled = true, privacy = NotificationPrivacy.COMPLETE),
            weatherText = "Clima: dato ficticio que no corresponde",
        )

        val posted = notificationForTag(
            availability.identity.trackingKey,
            ShiftNotificationPresenter.NOTIFICATION_ID,
        )
        assertEquals(
            "PRÓXIMA DISPONIBILIDAD · Retén",
            posted.extras.getString(Notification.EXTRA_TITLE),
        )
        assertEquals("19:00–07:00", posted.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(listOf("Ver detalles"), posted.actions.map { it.title.toString() })
        val expanded = posted.bigContentView.apply(context, null)
        assertEquals("Retén", expanded.findViewById<TextView>(R.id.notification_objective).text.toString())
        assertEquals(View.GONE, expanded.findViewById<View>(R.id.notification_weather_credit).visibility)
        assertEquals(View.GONE, expanded.findViewById<View>(R.id.notification_position).visibility)
        assertEquals(View.GONE, expanded.findViewById<View>(R.id.notification_weather).visibility)
        assertFalse(posted.extras.toString().contains("Objetivo ficticio"))
        assertFalse(posted.extras.toString().contains("Acceso"))
    }

    @Test
    fun shiftAndAvailabilityWithTheSameUuidKeepDifferentNotificationIdentities() {
        val shift = shift(SHIFT_ONE)
        val availability = availability(SHIFT_ONE)

        presenter.show(shift, NOW, NotificationPreferences(enabled = true))
        presenter.show(availability, NOW, NotificationPreferences(enabled = true))

        waitForNotifications {
            manager.activeNotifications.any { it.tag == shift.identity.trackingKey } &&
                manager.activeNotifications.any { it.tag == availability.identity.trackingKey }
        }
        assertNotEquals(shift.identity.trackingKey, availability.identity.trackingKey)
        assertEquals(
            2,
            manager.activeNotifications.count {
                it.tag == shift.identity.trackingKey || it.tag == availability.identity.trackingKey
            },
        )
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
        val channelBefore = notificationForTag(first.shiftId).channelId
        presenter.show(second, NOW, custom)
        presenter.updateGroupSummary(2, custom)

        val expectedIndividualTags = setOf(first.identity.trackingKey, second.identity.trackingKey)
        waitForNotifications {
            val active = manager.activeNotifications
            expectedIndividualTags.all { expectedTag -> active.any { it.tag == expectedTag } } &&
                active.any {
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 &&
                        it.notification.group == ShiftNotificationPresenter.GROUP_KEY
                }
        }
        val active = manager.activeNotifications
        val individual = active.filter {
            it.tag == first.identity.trackingKey || it.tag == second.identity.trackingKey
        }
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
        val vibrationChannelId = notificationForTag(first.shiftId).channelId
        val vibrationChannel = manager.getNotificationChannel(vibrationChannelId)
        assertNotEquals(channelBefore, vibrationChannelId)
        assertTrue(vibrationChannel.shouldVibrate())
        assertEquals(null, vibrationChannel.sound)

        presenter.show(
            first,
            NOW,
            custom.copy(attentionMode = NotificationAttentionMode.SILENT),
        )
        val silentChannel = manager.getNotificationChannel(notificationForTag(first.shiftId).channelId)
        assertFalse(silentChannel.shouldVibrate())
        assertEquals(null, silentChannel.sound)
        assertEquals(NotificationManager.IMPORTANCE_LOW, silentChannel.importance)
        assertEquals(1, manager.notificationChannels.count { it.id.startsWith(ShiftNotificationPresenter.OWNED_CHANNEL_PREFIX) })
    }

    @Test
    fun hiddenGroupSummaryDoesNotExposeCountOrLaborContextInEitherVersion() {
        presenter.updateGroupSummary(
            count = 2,
            preferences = NotificationPreferences(
                enabled = true,
                privacy = NotificationPrivacy.HIDDEN,
            ),
        )

        waitForNotifications {
            manager.activeNotifications.any {
                it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 &&
                    it.notification.group == ShiftNotificationPresenter.GROUP_KEY
            }
        }
        val posted = manager.activeNotifications.single {
            it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 &&
                it.notification.group == ShiftNotificationPresenter.GROUP_KEY
        }.notification
        val privateTitle = posted.extras.getString(Notification.EXTRA_TITLE).orEmpty()
        val privateText = posted.extras.getString(Notification.EXTRA_TEXT).orEmpty()
        val publicTitle = posted.publicVersion.extras.getString(Notification.EXTRA_TITLE).orEmpty()
        val publicText = posted.publicVersion.extras.getString(Notification.EXTRA_TEXT).orEmpty()

        assertEquals("MiGuardia", privateTitle)
        assertEquals("Tenés avisos de MiGuardia.", privateText)
        assertEquals(privateTitle, publicTitle)
        assertEquals(privateText, publicText)
        listOf(privateTitle, privateText, publicTitle, publicText).forEach { visibleText ->
            assertFalse(visibleText.contains("2"))
            assertFalse(visibleText.contains("labor", ignoreCase = true))
            assertFalse(visibleText.contains("jornada", ignoreCase = true))
            assertFalse(visibleText.contains("disponibilidad", ignoreCase = true))
        }
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
        assertEquals("PRUEBA · PRÓXIMA JORNADA", posted.extras.getString(Notification.EXTRA_TITLE))
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
    ) = NextEventItem.Shift(
        shiftId = id,
        start = start,
        end = end,
        zoneId = ZoneId.of("America/Argentina/Cordoba"),
        ownerLocalDate = LocalDate.of(2026, 9, 1),
        sector = WorkSector.PRIVATE_SECURITY,
        workTypeNameSnapshot = "Jornada ficticia",
        workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
        placeNameSnapshot = "Objetivo ficticio",
        placeAbbreviationSnapshot = "QA",
        startTimeSnapshot = LocalTime.of(19, 0),
        endTimeSnapshot = LocalTime.of(7, 0),
        colorArgbSnapshot = 0xff336699.toInt(),
        positionSnapshot = "Acceso",
        hasHistoricalAddress = true,
    )

    private fun availability(id: UUID) = NextEventItem.Availability(
        windowId = id,
        start = LocalDate.of(2026, 9, 1).atTime(19, 0)
            .atZone(ZoneId.of("America/Argentina/Cordoba")).toInstant(),
        end = LocalDate.of(2026, 9, 2).atTime(7, 0)
            .atZone(ZoneId.of("America/Argentina/Cordoba")).toInstant(),
        zoneId = ZoneId.of("America/Argentina/Cordoba"),
        ownerLocalDate = LocalDate.of(2026, 9, 1),
        labelSnapshot = "Retén",
        isResumption = false,
    )

    private fun notificationForTag(
        shiftId: UUID,
        predicate: (Notification) -> Boolean = { true },
    ): Notification = notificationForTag("shift:$shiftId", ShiftNotificationPresenter.NOTIFICATION_ID, predicate)

    private fun notificationForTag(
        tag: String,
        id: Int,
        predicate: (Notification) -> Boolean = { true },
    ): Notification {
        repeat(200) {
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
