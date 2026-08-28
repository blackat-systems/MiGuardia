package com.blackatsystems.miguardia

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationAttentionMode
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.notifications.NotificationRhythm
import java.util.UUID
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPreferencesInstrumentedTest {
    @Test
    fun defaultsAreDisabledWithTwelveHourReminder() = runBlocking {
        val store = isolatedStore()
        val defaults = store.current()
        assertFalse(defaults.enabled)
        assertEquals(listOf(NotificationPreferences.DEFAULT_REMINDER_MINUTES), defaults.globalReminderLeadMinutes)
        assertEquals(true, defaults.persistentWhileActive)
        assertEquals(NotificationPrivacy.COMPLETE, defaults.privacy)
        assertEquals(NotificationAttentionMode.SOUND_AND_VIBRATION, defaults.attentionMode)
        assertEquals(NotificationRhythm.ESSENTIAL, defaults.rhythm())
    }

    @Test
    fun preferencesPersistInTheirExclusiveDataStoreFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "notification-preferences-${UUID.randomUUID()}")
        check(directory.mkdirs())
        val file = File(directory, "preferences.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = NotificationPreferencesStore(file, firstScope)
        first.setEnabled(true)
        first.setPreciseTiming(true)
        first.setPersistentWhileActive(true)
        first.setPrivacy(NotificationPrivacy.REDUCED)
        first.setAttentionMode(NotificationAttentionMode.VIBRATION_ONLY)
        first.setGlobalReminderLeadMinutes(listOf(360L, 720L))
        first.setSoundUri(Uri.parse("content://settings/system/notification_sound"))
        first.markDismissed(LEGACY_SHIFT_ID)

        firstScope.cancel()
        delay(100)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val second = NotificationPreferencesStore(file, secondScope)
        val saved = second.current()
        assertEquals(true, saved.enabled)
        assertEquals(true, saved.preciseTiming)
        assertEquals(true, saved.persistentWhileActive)
        assertEquals(NotificationPrivacy.REDUCED, saved.privacy)
        assertEquals(NotificationAttentionMode.VIBRATION_ONLY, saved.attentionMode)
        assertEquals(listOf(360L, 720L), saved.globalReminderLeadMinutes)
        assertEquals("content://settings/system/notification_sound", saved.soundUri.toString())
        assertEquals(setOf(SHIFT_KEY), second.dismissedEventKeys())
        secondScope.cancel()
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun applyingRhythmIsAtomicAndDoesNotEraseNotificationTracking() = runBlocking {
        val store = isolatedStore()
        store.markDismissed(SHIFT_KEY)

        store.applyRhythm(NotificationRhythm.ACCOMPANIED)
        val accompanied = store.current()
        assertEquals(listOf(120L, 720L), accompanied.globalReminderLeadMinutes)
        assertEquals(true, accompanied.persistentWhileActive)
        assertEquals(NotificationPrivacy.COMPLETE, accompanied.privacy)
        assertEquals(NotificationAttentionMode.SOUND_AND_VIBRATION, accompanied.attentionMode)
        assertEquals(NotificationRhythm.ACCOMPANIED, accompanied.rhythm())
        assertEquals(setOf(SHIFT_KEY), store.dismissedEventKeys())

        store.applyRhythm(NotificationRhythm.DISCREET)
        val discreet = store.current()
        assertEquals(listOf(NotificationPreferences.DEFAULT_REMINDER_MINUTES), discreet.globalReminderLeadMinutes)
        assertEquals(false, discreet.persistentWhileActive)
        assertEquals(NotificationPrivacy.REDUCED, discreet.privacy)
        assertEquals(NotificationAttentionMode.SILENT, discreet.attentionMode)
        assertEquals(NotificationRhythm.DISCREET, discreet.rhythm())
        assertEquals(setOf(SHIFT_KEY), store.dismissedEventKeys())
    }

    @Test
    fun customChangesAreDerivedAndDismissedShiftCannotBeMarkedDisplayed() = runBlocking {
        val store = isolatedStore()
        store.setGlobalReminderLeadMinutes(listOf(360L))
        assertEquals(NotificationRhythm.CUSTOM, store.current().rhythm())

        store.markDismissed(SHIFT_KEY)
        assertFalse(store.markDisplayedUnlessDismissed(SHIFT_KEY))
        assertEquals(setOf(SHIFT_KEY), store.dismissedEventKeys())
        assertFalse(SHIFT_KEY in store.displayedEventKeys())

        store.clearEventTracking(SHIFT_KEY)
        assertTrue(store.markDisplayedUnlessDismissed(SHIFT_KEY))
        assertTrue(SHIFT_KEY in store.displayedEventKeys())

        store.markDismissed(AVAILABILITY_KEY)
        assertTrue(AVAILABILITY_KEY in store.dismissedEventKeys())
        assertFalse(store.markDisplayedUnlessDismissed(AVAILABILITY_KEY))
        store.clearEventTracking(AVAILABILITY_KEY)
        assertTrue(store.markDisplayedUnlessDismissed(AVAILABILITY_KEY))
    }

    private fun isolatedStore(): NotificationPreferencesStore = NotificationPreferencesStore(
        ApplicationProvider.getApplicationContext(),
        "notification-${UUID.randomUUID()}.preferences_pb",
    )

    private companion object {
        const val LEGACY_SHIFT_ID = "00000000-0000-0000-0000-000000000807"
        const val SHIFT_KEY = "shift:$LEGACY_SHIFT_ID"
        const val AVAILABILITY_KEY =
            "availability:00000000-0000-0000-0000-000000000808:1788278400000:1788282000000"
    }
}
