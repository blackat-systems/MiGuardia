package com.blackatsystems.miguardia

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
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
        assertEquals(NotificationPrivacy.COMPLETE, defaults.privacy)
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
        first.setGlobalReminderLeadMinutes(listOf(360L, 720L))
        first.setSoundUri(Uri.parse("content://settings/system/notification_sound"))

        firstScope.cancel()
        delay(100)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val saved = NotificationPreferencesStore(file, secondScope).current()
        assertEquals(true, saved.enabled)
        assertEquals(true, saved.preciseTiming)
        assertEquals(true, saved.persistentWhileActive)
        assertEquals(NotificationPrivacy.REDUCED, saved.privacy)
        assertEquals(listOf(360L, 720L), saved.globalReminderLeadMinutes)
        assertEquals("content://settings/system/notification_sound", saved.soundUri.toString())
        secondScope.cancel()
        directory.deleteRecursively()
        Unit
    }

    private fun isolatedStore(): NotificationPreferencesStore = NotificationPreferencesStore(
        ApplicationProvider.getApplicationContext(),
        "notification-${UUID.randomUUID()}.preferences_pb",
    )
}
