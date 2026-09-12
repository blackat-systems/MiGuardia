package com.blackatsystems.miguardia

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.media.RingtoneManager
import android.os.Build
import android.os.Parcel
import android.os.SystemClock
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.blackatsystems.miguardia.beta.BetaCoordinator
import com.blackatsystems.miguardia.beta.BetaDeviceIssuer
import com.blackatsystems.miguardia.beta.BetaEnvironment
import com.blackatsystems.miguardia.beta.BetaPhase
import com.blackatsystems.miguardia.beta.BetaRecord
import com.blackatsystems.miguardia.beta.BetaStorage
import com.blackatsystems.miguardia.beta.BetaVariant
import com.blackatsystems.miguardia.beta.KeystoreBetaIdentity
import com.blackatsystems.miguardia.beta.installedBetaApp
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.projectLoadedWorkSetupState
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.ShiftNotificationPresenter
import com.blackatsystems.miguardia.ui.help.OnboardingPreferencesStore
import com.blackatsystems.miguardia.ui.help.OnboardingStoreState
import com.blackatsystems.miguardia.weather.WeatherCreditLink
import com.blackatsystems.miguardia.weather.WeatherCredits
import com.blackatsystems.miguardia.widget.NextEventAppWidgetProvider
import com.blackatsystems.miguardia.widget.WidgetRemoteViewsRenderer
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MAIN-only QA on the public root; existing setup and owner-unlocked access are required.
 * Presenter evidence contains synthetic events only. Navigation uses the real Activity without
 * screenshots, hierarchy dumps or external browser launches. MAIN must stop the process afterwards,
 * after verifying cleanup-complete. Existing private files, including beta residues, stay intact.
 */
@RunWith(AndroidJUnit4::class)
class WeatherCreditsSessionInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun authorizedPresenterSessionPreservesOwnerStateAndRestoresClosedAccess() = runBlocking {
        assumeTrue("Requires an explicitly supervised credits session",
            InstrumentationRegistry.getArguments().getString("creditsTemporarySession") == "authorized")
        val app = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        check(app.packageName == QA_PACKAGE) { "Exact QA package required" }
        await { app.startupRecoveryGate.isReady }
        check(app.accessLockCoordinator.state.value.allowsSensitiveContent) { "The owner must unlock first" }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            check(Stage.values().filterNot { it == Stage.DESTROYED }.all { monitor.getActivitiesInStage(it).isEmpty() }) {
                "Close QA Activities before the supervised credits session"
            }
        }
        check(app.pendingMainDestinations.state.value == null) { "Preserve an existing pending destination" }
        val onboarding = app.onboardingPreferences.state.first()
        check(onboarding is OnboardingStoreState.Ready &&
            onboarding.completedVersion >= OnboardingPreferencesStore.CURRENT_VERSION) {
            "The owner must already have completed or skipped onboarding; this test cannot change it"
        }
        val today = LocalDate.now(AppDefaults.zoneId())
        val history = checkNotNull(app.localDataStore.workConfiguration.get()) { "Existing work configuration required" }
        val revision = checkNotNull(history.timeline.revisionAt(today)) { "Applicable work configuration required" }
        val catalog = app.localDataStore.workCatalog.observeCatalog(history.timeline.id, revision.value.sector).first()
        check(projectLoadedWorkSetupState(history, catalog, today) is WorkSetupState.V2Ready) {
            "The existing first work set must be complete; this test cannot create it"
        }
        val manager = app.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33) check(app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) { "Notification permission must already be granted" }
        check(manager.areNotificationsEnabled()) { "QA notifications must already be enabled" }
        check(manager.activeNotifications.isEmpty()) { "Preserve existing QA notifications; active count must be zero" }
        check(AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, NextEventAppWidgetProvider::class.java)).isEmpty()) {
            "Preserve existing QA widgets; this session requires zero instances"
        }

        val expectedChannel = defaultChannelId()
        val channelsBefore = manager.notificationChannels.associate { it.id to channelFingerprint(it) }
        check(channelsBefore.keys.none { it.startsWith(ShiftNotificationPresenter.OWNED_CHANNEL_PREFIX) && it != expectedChannel }) {
            "An incompatible owned QA channel exists; do not delete or replace it"
        }
        val existingChannel = manager.getNotificationChannel(expectedChannel)
        check(existingChannel == null || (existingChannel.name.toString() == "Avisos laborales" &&
            existingChannel.description == "Recordatorios y estado de jornadas y disponibilidad configuradas." &&
            existingChannel.importance <= NotificationManager.IMPORTANCE_DEFAULT &&
            existingChannel.importance != NotificationManager.IMPORTANCE_NONE)) {
            "Preserve channel customization; default presenter channel is incompatible"
        }

        @Suppress("UNCHECKED_CAST")
        val preferences = app.notificationPreferences.javaClass.getDeclaredField("dataStore").apply { isAccessible = true }
            .get(app.notificationPreferences) as DataStore<Preferences>
        val originalPreferences = preferences.data.first()
        val workBefore = databaseFingerprint(app)
        val filesBefore = privateFilesFingerprint(app)
        val evidence = File(app.cacheDir, "weather-credits-supervised")
        check(!evidence.exists() && evidence.mkdir()) { "MAIN must handle prior supervised evidence first" }
        val notificationWasPaused = app.notificationRuntime.isPausedForRestore
        val widgetWasPaused = app.widgetRuntime.isPausedForRestore
        val sessionJob = SupervisorJob()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            app.notificationRuntime.pauseForRestore()
            app.widgetRuntime.pauseForRestore()
            app.weatherRuntime.cancelRefresh()
            check(app.accessLockCoordinator.state.value.allowsSensitiveContent) { "Owner lock closed; do not run presenter checks" }

            val presenterChecks = ShiftNotificationPresenterInstrumentedTest()
            // Deliberately do not call its @Before/@After: those change permission/preferences
            // and cancel all QA notifications. Each selected case uses only synthetic events.
            val cases = listOf<() -> Unit>(
                presenterChecks::completeReminderUsesStableUuidIdentityHistoricalContentAndTwoActions,
                presenterChecks::weatherAppearsOnlyInCompletePrivacyAndSilentRefreshDoesNotAlertAgain,
                presenterChecks::longWeatherKeepsSeparateCreditAndHiddenRefreshRemovesIt,
                presenterChecks::syntheticWeatherPreviewDoesNotClaimOpenMeteoData,
                presenterChecks::availabilityUsesItsHistoricalLabelOnlyAndOpensOneDetailAction,
            )
            cases.forEach { test ->
                check(app.accessLockCoordinator.state.value.allowsSensitiveContent)
                test()
                cancelSyntheticNotifications(manager)
            }
            File(evidence, "presenter-checks-passed").writeText("five synthetic presenter checks passed\n")
            scenario = ActivityScenario.launch(Intent(app, MainActivity::class.java))
            // Errors in real Activity semantics are deliberately redacted: a default Compose
            // failure can otherwise print an owner's calendar or place names into the runner log.
            safeUi("Opening the existing Help destination failed") {
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("main-menu-button").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("Abrir menú").performClick()
                compose.onNodeWithTag("main-destination-help").performScrollTo().performClick()
                compose.onNodeWithTag("help-screen").assertIsDisplayed()
            }
            val originalActivity = foregroundMainActivity()
            val widgetWeather = WidgetRemoteViewsRenderer(app).weatherPendingIntentForTest(SYNTHETIC_WIDGET_ID)
            try {
                safeUi("Widget PendingIntent did not preserve the previous destination") {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync { widgetWeather.send() }
                    assertGlobalWeatherAndReturnToHelp(app, originalActivity)
                }
                File(evidence, "widget-navigation-passed").writeText("production Widget weather PendingIntent -> Clima -> previous Help destination\n")
            } finally { widgetWeather.cancel() }

            diagnoseNotificationNavigation(evidence, originalActivity) { checkpoint ->
                val now = Instant.parse("2026-09-01T20:00:00Z")
                val shift = NextEventItem.Shift(
                    shiftId = syntheticId, start = now.plusSeconds(3600), end = now.plusSeconds(13 * 3600),
                    zoneId = syntheticZone, ownerLocalDate = LocalDate.of(2026, 9, 1),
                    sector = WorkSector.PRIVATE_SECURITY, workTypeNameSnapshot = "Jornada ficticia",
                    workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
                    placeNameSnapshot = "Objetivo ficticio", placeAbbreviationSnapshot = "QA",
                    startTimeSnapshot = LocalTime.of(19, 0), endTimeSnapshot = LocalTime.of(7, 0),
                    colorArgbSnapshot = 0xff336699.toInt(), positionSnapshot = null, hasHistoricalAddress = false,
                )
                ShiftNotificationPresenter(app).show(shift, now, NotificationPreferences(enabled = true),
                    weatherText = "Clima: lluvia · 12–18 °C", silentUpdate = true)
                checkpoint("await-notification")
                await { manager.activeNotifications.any { it.tag == shift.identity.trackingKey && it.id == ShiftNotificationPresenter.NOTIFICATION_ID } }
                val notification = manager.activeNotifications.single {
                    it.tag == shift.identity.trackingKey && it.id == ShiftNotificationPresenter.NOTIFICATION_ID
                }.notification
                checkpoint("before-credit-click")
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val credit = notification.bigContentView.apply(app, null)
                        .findViewById<TextView>(R.id.notification_weather_credit)
                    check(credit.text.toString() == WeatherCredits.SUMMARY && credit.hasOnClickListeners())
                    check(credit.performClick())
                }
                checkpoint("credit-click-dispatched")
                assertGlobalWeatherAndReturnToHelp(app, originalActivity, checkpoint)
            }
            cancelSyntheticNotifications(manager)
            File(evidence, "notification-navigation-passed").writeText("real notification credit RemoteViews click -> Clima -> previous Help destination\n")

            verifyLicenceIntentAndReturn(app, originalActivity)
            File(evidence, "licence-intent-return-passed").writeText("real Clima link -> exact intercepted CC BY HTTPS intent -> previous Help destination; browser not launched\n")
            checkNotNull(scenario).close()
            scenario = null
            check(workBefore == databaseFingerprint(app)) { "Application tables changed" }
            check(filesBefore == privateFilesFingerprint(app)) { "Private files or preferences changed" }
        } finally {
            withContext(NonCancellable) {
                val failures = mutableListOf<String>()
                suspend fun cleanup(label: String, action: suspend () -> Unit) {
                    try { action() } catch (_: Throwable) { failures += label }
                }
                cleanup("test Activities") {
                    scenario?.close()
                    // The entry guard required zero Activities. Finish only MainActivity instances
                    // introduced by these pending intents, including an unexpected duplicate.
                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                        Stage.values().filterNot { it == Stage.DESTROYED }
                            .flatMap { monitor.getActivitiesInStage(it) }.distinct()
                            .filterIsInstance<MainActivity>().forEach(Activity::finish)
                    }
                    await { noMainActivities() }
                }
                cleanup("temporary jobs") { sessionJob.cancelAndJoin() }
                cleanup("notification preferences") {
                    if (preferences.data.first() != originalPreferences) preferences.updateData { originalPreferences }
                    check(preferences.data.first() == originalPreferences)
                }
                cleanup("synthetic notifications") { cancelSyntheticNotifications(manager) }
                cleanup("notification channels") {
                    // Never delete a channel that existed before this test. Android may keep a
                    // tombstone for the new QA channel; this asserts the visible channel inventory.
                    if (!channelsBefore.containsKey(expectedChannel)) manager.deleteNotificationChannel(expectedChannel)
                    check(manager.notificationChannels.associate { it.id to channelFingerprint(it) } == channelsBefore)
                }
                // Reopen maintenance only when local recovery allows it.
                // If recovery is closed, leave both runtimes paused for MAIN.
                if (app.startupRecoveryGate.isReady) {
                    cleanup("notification runtime") { if (!notificationWasPaused) app.notificationRuntime.resumeAfterRestore() }
                    cleanup("widget runtime") { if (!widgetWasPaused) app.widgetRuntime.resumeAfterRestore() }
                }
                cleanup("database preservation") { check(workBefore == databaseFingerprint(app)) }
                cleanup("private-file preservation") { check(filesBefore == privateFilesFingerprint(app)) }
                cleanup("notification permission") {
                    if (Build.VERSION.SDK_INT >= 33) check(app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED)
                    check(manager.areNotificationsEnabled())
                }
                check(failures.isEmpty()) { "Supervised cleanup failed: ${failures.joinToString()}" }
                File(evidence, "cleanup-complete").writeText("public root; preferences and tables preserved\n")
            }
        }
    }

    private fun assertGlobalWeatherAndReturnToHelp(
        app: MiGuardiaApplication,
        expected: MainActivity,
        checkpoint: (String) -> Unit = {},
    ) {
        val heading = "Pronóstico según la ubicación guardada en cada objetivo."
        checkpoint("await-weather")
        compose.waitUntil(15_000) { compose.onAllNodesWithText(heading).fetchSemanticsNodes().isNotEmpty() }
        checkpoint("weather-found")
        compose.onNodeWithText(heading).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(WeatherCreditLink.LICENCE.label).performScrollTo().assertIsDisplayed()
        checkpoint("before-return")
        compose.onNodeWithText("Volver").performScrollTo().performClick()
        checkpoint("return-clicked")
        compose.waitUntil(15_000) { compose.onAllNodesWithText(heading).fetchSemanticsNodes().isEmpty() }
        checkpoint("dialog-closed")
        compose.onNodeWithTag("help-screen").assertIsDisplayed()
        checkpoint("help-restored")
        check(foregroundMainActivity() === expected) { "An additional Activity replaced the prior destination" }
        checkpoint("activity-preserved")
        check(app.pendingMainDestinations.state.value == null) { "Weather destination was not consumed" }
        checkpoint("destination-consumed")
    }

    /** Diagnostic output is static stage names, counts and booleans only; never exception messages. */
    private fun diagnoseNotificationNavigation(
        evidence: File,
        expected: MainActivity,
        block: ((String) -> Unit) -> Unit,
    ) {
        var stage = "start"
        fun checkpoint(next: String) {
            stage = next
            writeNavigationCheckpoint(evidence, next, expected)
        }
        try {
            checkpoint(stage)
            block(::checkpoint)
        } catch (error: Throwable) {
            val type = error.javaClass.simpleName.takeIf { it.matches(Regex("[A-Za-z0-9_]+")) } ?: "Throwable"
            runCatching { writeNavigationCheckpoint(evidence, "failed-$stage", expected, type) }
            throw AssertionError("Notification credit failed at $stage ($type)")
        }
    }

    private fun writeNavigationCheckpoint(
        evidence: File,
        stage: String,
        expected: MainActivity,
        exceptionType: String = "none",
    ) {
        check(stage.matches(Regex("[a-z-]+")))
        var total = 0
        var resumed = 0
        var originalPresent = false
        var originalResumed = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            val activities = Stage.values().filterNot { it == Stage.DESTROYED }
                .flatMap { monitor.getActivitiesInStage(it) }.filterIsInstance<MainActivity>().distinct()
            val foreground = monitor.getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
            total = activities.size
            resumed = foreground.size
            originalPresent = activities.any { it === expected }
            originalResumed = foreground.any { it === expected }
        }
        val weatherVisible = runCatching {
            compose.onNodeWithText("Pronóstico según la ubicación guardada en cada objetivo.").assertIsDisplayed()
        }.isSuccess
        val helpVisible = runCatching { compose.onNodeWithTag("help-screen").assertIsDisplayed() }.isSuccess
        File(evidence, "notification-diagnostic-$stage.txt").writeText(
            "stage=$stage\nexception_type=$exceptionType\nmain_count=$total\nresumed_count=$resumed\n" +
                "original_present=$originalPresent\noriginal_resumed=$originalResumed\n" +
                "weather_visible=$weatherVisible\nhelp_visible=$helpVisible\n",
        )
    }

    private fun verifyLicenceIntentAndReturn(app: MiGuardiaApplication, expected: MainActivity) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val requests = AtomicInteger()
        val unexpected = AtomicBoolean()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                requests.incrementAndGet()
                if (intent.dataString != WeatherCreditLink.LICENCE.url || intent.extras != null || intent.clipData != null) {
                    unexpected.set(true)
                }
                // Public URL contract exercised through Main, without browser/account/chooser effects.
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            safeUi("Licence link or its return path failed") {
                compose.onNodeWithContentDescription("Abrir menú").performClick()
                compose.onNodeWithTag("drawer-action-weather").performScrollTo().performClick()
                compose.onNodeWithText(WeatherCreditLink.LICENCE.label).performScrollTo().performClick()
                compose.runOnIdle { check(requests.get() == 1 && !unexpected.get()) }
                assertGlobalWeatherAndReturnToHelp(app, expected)
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    private fun foregroundMainActivity(): MainActivity {
        var activity: MainActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().singleOrNull()
        }
        return checkNotNull(activity) { "Exactly one resumed MainActivity is required" }
    }

    private fun noMainActivities(): Boolean {
        var empty = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            empty = Stage.values().filterNot { it == Stage.DESTROYED }
                .none { stage -> monitor.getActivitiesInStage(stage).any { it is MainActivity } }
        }
        return empty
    }

    private fun safeUi(message: String, block: () -> Unit) {
        try { block() } catch (_: Throwable) { throw AssertionError(message) }
    }

    private fun cancelSyntheticNotifications(manager: NotificationManager) {
        syntheticIdentities.forEach { (tag, id) -> manager.cancel(tag, id) }
        await { manager.activeNotifications.none { (it.tag to it.id) in syntheticIdentities } }
        check(manager.activeNotifications.isEmpty()) { "Unexpected QA notification preserved; stop and inspect" }
    }

    private fun defaultChannelId(): String {
        val key = "SOUND_AND_VIBRATION|${RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)}"
        return ShiftNotificationPresenter.CHANNEL_PREFIX + hash(key.toByteArray(Charsets.UTF_8)).take(12)
    }

    private fun channelFingerprint(channel: NotificationChannel): String {
        val parcel = Parcel.obtain()
        return try { channel.writeToParcel(parcel, 0); hash(parcel.marshall()) } finally { parcel.recycle() }
    }

    private fun databaseFingerprint(app: MiGuardiaApplication): String {
        val file = app.getDatabasePath("miguardia-v2.db")
        check(file.isFile) { "Existing V2 database required" }
        return SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("PRAGMA quick_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok") }
            val tables = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table') ORDER BY name", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            check(tables.size == 27)
            val rows = tables.flatMap { table ->
                database.rawQuery("SELECT * FROM \"${table.replace("\"", "\"\"")}\"", null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val digest = MessageDigest.getInstance("SHA-256")
                            digest.update(table.toByteArray(Charsets.UTF_8))
                            for (column in 0 until cursor.columnCount) {
                                val type = cursor.getType(column)
                                val bytes = when (type) {
                                    Cursor.FIELD_TYPE_NULL -> byteArrayOf()
                                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(column)
                                    else -> cursor.getString(column).toByteArray(Charsets.UTF_8)
                                }
                                digest.update("$type:${bytes.size}:".toByteArray(Charsets.UTF_8))
                                digest.update(bytes)
                            }
                            add(hex(digest.digest()))
                        }
                    }
                }
            }
            hash((tables + rows.sorted()).joinToString("\n").toByteArray(Charsets.UTF_8))
        }
    }

    private fun privateFilesFingerprint(app: MiGuardiaApplication): String {
        val roots = listOf(app.filesDir, File(app.applicationInfo.dataDir, "shared_prefs"), app.noBackupFilesDir)
        val fingerprints = roots.flatMap { root ->
            if (!root.exists()) emptyList() else root.walkTopDown().filter(File::isFile).filterNot {
                (root == app.filesDir && it == File(root, "profileInstalled")) ||
                    (root.name == "shared_prefs" && it == File(root, "android.app.ActivityThread.IDS.xml"))
            }.map { root.name + "/" + it.relativeTo(root).path + ":" + hash(it.readBytes()) }.toList()
        }
        return hash(fingerprints.sorted().joinToString("\n").toByteArray(Charsets.UTF_8))
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        check(condition()) { "Supervised QA condition did not complete" }
    }

    private fun hash(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private companion object {
        const val QA_PACKAGE = "com.blackatsystems.miguardia.qa"
        const val IDENTITY_ALIAS = "miguardia.beta.installation.v1"
        const val SYNTHETIC_WIDGET_ID = 2_070_907
        val syntheticId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000701")
        val syntheticZone: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val syntheticIdentities = setOf(
            NextEventIdentity.Shift(syntheticId).trackingKey to ShiftNotificationPresenter.NOTIFICATION_ID,
            NextEventIdentity.Availability(syntheticId,
                LocalDate.of(2026, 9, 1).atTime(19, 0).atZone(syntheticZone).toInstant(),
                LocalDate.of(2026, 9, 2).atTime(7, 0).atZone(syntheticZone).toInstant()).trackingKey to ShiftNotificationPresenter.NOTIFICATION_ID,
            ShiftNotificationPresenter.PREVIEW_TAG to ShiftNotificationPresenter.PREVIEW_NOTIFICATION_ID,
        )
    }
}
