package com.blackatsystems.miguardia.backup

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupPreference
import com.blackatsystems.miguardia.core.domain.backup.BackupPreferenceType
import com.blackatsystems.miguardia.core.domain.backup.BackupRecord
import com.blackatsystems.miguardia.core.domain.backup.BackupTable
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupContract
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupSchemaV6
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.notifications.NotificationAttentionMode
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.security.AccessLockConfiguration
import com.blackatsystems.miguardia.security.AccessLockCoordinator
import com.blackatsystems.miguardia.security.AccessLockPreferencesStore
import com.blackatsystems.miguardia.security.AccessLockStoreRead
import com.blackatsystems.miguardia.security.AccessLockTimeout
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.ui.summary.SummaryPreferences
import com.blackatsystems.miguardia.ui.summary.SummaryPreferencesStore
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortablePreferencesGatewayInstrumentedTest {
    @Test
    fun backupReadsPropagateDataStoreIoFailuresInsteadOfUsingDefaults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = snapshotWithShift(EVENT_SHIFT_ID)

        repeat(4) { failingIndex ->
            val stores = List(4) { index -> TestPreferencesDataStore(failReads = index == failingIndex) }
            val gateway = PortablePreferencesGateway(
                context = context,
                guardProfile = GuardProfileStore(stores[0]),
                summaryStore = SummaryPreferencesStore(stores[1]),
                notificationStore = NotificationPreferencesStore(stores[2]),
                weatherStore = WeatherPreferencesStore(stores[3]),
            )

            assertTrue(assertSuspendFails { gateway.capture(database) } is IOException)
        }
    }

    @Test
    fun restoreMovesOnlySemanticPreferencesAndKeepsDeviceBookkeepingOut() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "portable-preferences-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val prefix = "backup-test-${UUID.randomUUID()}"
        val context = IsolatedPreferencesContext(base, prefix, root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val profile = GuardProfileStore(File(root, "profile.preferences_pb"), scope)
            val summary = SummaryPreferencesStore(File(root, "summary.preferences_pb"), scope)
            val notifications = NotificationPreferencesStore(File(root, "notifications.preferences_pb"), scope)
            val weather = WeatherPreferencesStore(File(root, "weather.preferences_pb"), scope)
            val accessLock = AccessLockPreferencesStore(
                context.preferencesDataStoreFile(AccessLockPreferencesStore.DEFAULT_FILE_NAME),
                scope,
            )
            val gateway = PortablePreferencesGateway(context, profile, summary, notifications, weather)
            val display = context.getSharedPreferences(MainActivity.DISPLAY_PREFERENCES, Context.MODE_PRIVATE)

            profile.save("Persona ficticia")
            summary.replacePortable(
                SummaryPreferences(
                    orderedFamilies = SummaryOptionalFamily.entries.reversed(),
                    hiddenFamilies = setOf(SummaryOptionalFamily.entries.first()),
                    introSeen = true,
                ),
            )
            notifications.setEnabled(true)
            notifications.setPreciseTiming(true)
            notifications.setPersistentWhileActive(false)
            notifications.setPrivacy(NotificationPrivacy.REDUCED)
            notifications.setAttentionMode(NotificationAttentionMode.VIBRATION_ONLY)
            notifications.setGlobalReminderLeadMinutes(listOf(120L, 720L))
            notifications.setSoundUri(Uri.parse("content://test/device-only-sound"))
            notifications.markDismissed(EVENT_KEY)
            notifications.markDismissed(STALE_EVENT_KEY)
            weather.enableAfterExplanation()
            weather.setUnitSystem(WeatherUnitSystem.FAHRENHEIT)
            weather.setIncludeInNotifications(true)
            weather.recordRefreshAttempt(1_788_131_400_000L)
            weather.setRetryAfterUntil(1_788_131_460_000L)
            val localAccessLock = AccessLockConfiguration(true, AccessLockTimeout.FIVE_MINUTES)
            accessLock.replace(localAccessLock)
            assertTrue(
                display.edit()
                    .putInt(MainActivity.APP_ZOOM_PERCENT, AppZoom.LARGE.percent)
                    .putString(MainActivity.APP_THEME_MODE, AppThemeMode.DARK.name)
                    .commit(),
            )

            val database = snapshotWithShift(EVENT_SHIFT_ID)
            val portable = gateway.capture(database)
            assertEquals(17, portable.size)
            assertFalse(portable.any { it.key.contains("lock", ignoreCase = true) })
            assertFalse(portable.any { it.key.contains("sound") || it.key.contains("retry") })
            assertEquals(
                listOf(EVENT_KEY),
                portable.single {
                    it.key == MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE
                }.values,
            )
            val unknownError = assertSuspendFails {
                gateway.decode(
                    portable + BackupPreference(
                        "unknown.device_state",
                        BackupPreferenceType.TEXT,
                        listOf("forbidden"),
                    ),
                    database,
                )
            }
            assertTrue(unknownError is InvalidBackupException)

            profile.save("Otro perfil")
            summary.replacePortable(SummaryPreferences())
            notifications.setEnabled(false)
            notifications.setDismissedEventKeys(setOf(STALE_EVENT_KEY))
            weather.setEnabled(false)
            display.edit()
                .putInt(MainActivity.APP_ZOOM_PERCENT, AppZoom.STANDARD.percent)
                .putString(MainActivity.APP_THEME_MODE, AppThemeMode.LIGHT.name)
                .commit()

            val restored = gateway.replace(portable, database)

            assertEquals("Persona ficticia", restored.displayName)
            assertEquals(AppZoom.LARGE, restored.zoom)
            assertEquals(AppThemeMode.DARK, restored.theme)
            assertEquals(SummaryOptionalFamily.entries.reversed(), restored.summary.orderedFamilies)
            assertEquals(Uri.parse("content://test/device-only-sound"), notifications.current().soundUri)
            assertEquals(setOf(EVENT_KEY), notifications.dismissedEventKeys())
            assertNull(weather.current().lastRefreshAttemptAtEpochMillis)
            assertNull(weather.current().retryAfterUntilEpochMillis)
            assertEquals(
                AccessLockStoreRead.Ready(localAccessLock),
                accessLock.read(),
            )
            val restartedLock = AccessLockCoordinator(accessLock, scope)
            restartedLock.initializeAfterRecovery()
            restartedLock.activityStarted(Any(), deviceLocked = false)
            assertTrue(restartedLock.state.value.locked)
            assertFalse(restartedLock.state.value.allowsSensitiveContent)
        } finally {
            scope.cancel()
            context.clearIsolatedPreferences()
            root.deleteRecursively()
        }
    }

    private fun snapshotWithShift(shiftId: String): BackupDatabaseSnapshot = BackupDatabaseSnapshot(
        timelineId = null,
        tables = MiGuardiaBackupSchemaV6.tables.map { spec ->
            BackupTable(
                spec.name,
                spec.columns,
                spec.primaryKey,
                if (spec.name == "shifts") {
                    listOf(
                        BackupRecord(
                            listOf(
                                BackupValue.Text(shiftId),
                                BackupValue.Integer(1_788_131_400_000L),
                                BackupValue.Integer(1_788_160_200_000L),
                                BackupValue.Text("America/Argentina/Buenos_Aires"),
                                BackupValue.Text("2026-08-31"),
                                BackupValue.Text("Objetivo ficticio"),
                                BackupValue.Text("FIC"),
                                BackupValue.Null,
                                BackupValue.Text("08:00"),
                                BackupValue.Text("16:00"),
                                BackupValue.Integer(0xFF000000),
                                BackupValue.Null,
                                BackupValue.Text("PLANNED"),
                                BackupValue.Null,
                                BackupValue.Integer(1_788_131_400_000L),
                                BackupValue.Integer(1_788_131_400_000L),
                            ),
                        ),
                    )
                } else {
                    emptyList()
                },
            )
        },
    )

    private suspend fun assertSuspendFails(block: suspend () -> Unit): Throwable = try {
        block()
        throw AssertionError("Se esperaba una falla")
    } catch (error: AssertionError) {
        throw error
    } catch (error: Throwable) {
        error
    }

    private class IsolatedPreferencesContext(
        base: Context,
        private val prefix: String,
        private val root: File,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").also(File::mkdirs)

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("$prefix-$name", mode)

        fun clearIsolatedPreferences() {
            getSharedPreferences(MainActivity.DISPLAY_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private class TestPreferencesDataStore(
        private val failReads: Boolean,
    ) : DataStore<Preferences> {
        private var current: Preferences = emptyPreferences()

        override val data: Flow<Preferences> = flow {
            if (failReads) throw IOException("Falla ficticia de lectura")
            emit(current)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            current = transform(current)
            return current
        }
    }

    private companion object {
        const val EVENT_SHIFT_ID = "00000000-0000-4000-8000-000000000019"
        const val EVENT_KEY = "shift:$EVENT_SHIFT_ID"
        const val STALE_EVENT_KEY = "shift:00000000-0000-4000-8000-000000000020"
    }
}
