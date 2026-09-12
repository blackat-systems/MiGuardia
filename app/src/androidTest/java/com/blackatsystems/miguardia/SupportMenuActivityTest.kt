package com.blackatsystems.miguardia

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.beta.BetaDeviceIssuer
import com.blackatsystems.miguardia.beta.BetaEnvironment
import com.blackatsystems.miguardia.beta.BetaPhase
import com.blackatsystems.miguardia.beta.BetaRecord
import com.blackatsystems.miguardia.beta.BetaStorage
import com.blackatsystems.miguardia.beta.BetaVariant
import com.blackatsystems.miguardia.beta.installedBetaApp
import com.blackatsystems.miguardia.ui.help.OnboardingStoreState
import com.blackatsystems.miguardia.ui.help.OnboardingPreferencesStore
import com.blackatsystems.miguardia.support.SUPPORT_URL
import java.io.File
import java.security.MessageDigest
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Default cases preserve existing access; the real-browser case requires explicit operator opt-in. */
class SupportMenuActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun closedRootNeverExposesOrReplaysSupportAcrossRecreation() {
        check(app.packageName == "com.blackatsystems.miguardia.qa")
        val outgoing = CopyOnWriteArrayList<Intent>()
        val monitor = monitor(outgoing)
        try {
            ActivityScenario.launch<MainActivity>(launchIntent()).use { scenario ->
                awaitRoot()
                assumeTrue("Existing QA must remain closed for this case",
                    !app.accessLockCoordinator.state.value.allowsSensitiveContent || !app.startupRecoveryGate.isReady)
                val before = fingerprints()
                compose.onNodeWithTag("drawer-action-support").assertDoesNotExist()
                compose.onNodeWithTag("main-navigation-drawer").assertDoesNotExist()
                scenario.recreate()
                awaitRoot()
                compose.onNodeWithTag("drawer-action-support").assertDoesNotExist()
                assertTrue(outgoing.isEmpty())
                assertEquals(before, fingerprints())
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun existingAuthorizedSessionPreservesDataAndDestinationWithExactInterceptedIntent() {
        check(app.packageName == "com.blackatsystems.miguardia.qa")
        val outgoing = CopyOnWriteArrayList<Intent>()
        val monitor = monitor(outgoing)
        try {
            ActivityScenario.launch<MainActivity>(launchIntent()).use { scenario ->
                awaitRoot()
                assumeTrue("Owner's QA access must already allow content; never enable a fixture",
                    app.accessLockCoordinator.state.value.allowsSensitiveContent && app.startupRecoveryGate.isReady)
                compose.waitForIdle()
                assumeTrue("Owner must finish configuration/onboarding", compose.onAllNodesWithTag("main-menu-button").fetchSemanticsNodes().isNotEmpty())
                val before = fingerprints()
                compose.onNodeWithContentDescription("Abrir menú").performClick()
                compose.onNodeWithTag("main-destination-help").performScrollTo().performClick()
                compose.onNodeWithTag("help-screen").assertIsDisplayed()
                compose.onNodeWithContentDescription("Abrir menú").performClick()
                assertEquals(before, fingerprints())
                compose.onNodeWithTag("drawer-action-support").performScrollTo().performClick()
                compose.waitForIdle()
                assertEquals(1, outgoing.size)
                val sent = outgoing.single()
                assertEquals(Intent.ACTION_VIEW, sent.action)
                assertEquals(SUPPORT_URL, sent.dataString)
                assertNull(sent.extras)
                assertNull(sent.clipData)
                assertNull(sent.`package`)
                assertNull(sent.component)
                assertEquals(0, sent.flags)
                compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
                compose.onNodeWithTag("help-screen").assertIsDisplayed()
                assertEquals(before, fingerprints())
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                awaitRoot()
                scenario.recreate()
                awaitRoot()
                assertEquals(1, outgoing.size)
                if (app.accessLockCoordinator.state.value.allowsSensitiveContent && app.startupRecoveryGate.isReady) {
                    compose.onNodeWithTag("help-screen").assertIsDisplayed()
                    compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
                } else compose.onNodeWithTag("drawer-action-support").assertDoesNotExist()
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    /** MAIN-only, expressly authorized session. Never part of an unattended suite. */
    @Test fun authorizedTemporarySessionOpensRealBrowserAndPreservesOwnerData() {
        assumeTrue("Requires specific owner authorization and supervised browser QA",
            InstrumentationRegistry.getArguments().getString("support27TemporarySession") == "authorized")
        check(app.packageName == "com.blackatsystems.miguardia.qa")
        compose.waitUntil(15_000) { app.startupRecoveryGate.isReady }
        check(app.accessLockCoordinator.state.value.allowsSensitiveContent) { "Owner must unlock; test cannot bypass lock" }
        runBlocking {
            check(app.localDataStore.workConfiguration.get() != null) { "Existing work configuration required" }
            val onboarding = app.onboardingPreferences.state.first()
            check(onboarding is OnboardingStoreState.Ready &&
                onboarding.completedVersion >= OnboardingPreferencesStore.CURRENT_VERSION) { "Owner must complete onboarding" }
        }
        val workBefore = fingerprints().first()
        val preferencesBefore = privateFilesFingerprint()
        val evidence = File(app.cacheDir, "support27-supervised")
        check(!evidence.exists()) { "Prior supervised evidence must be handled by MAIN first" }
        check(evidence.mkdir())
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            runBlocking {
                app.notificationRuntime.pauseForRestore()
                app.widgetRuntime.pauseForRestore()
                app.weatherRuntime.cancelRefresh()
            }
            // Re-run the existing root/intent/return assertions now that legitimate test trust exists.
            existingAuthorizedSessionPreservesDataAndDestinationWithExactInterceptedIntent()
            scenario = ActivityScenario.launch(launchIntent())
            awaitRoot()
            compose.onNodeWithContentDescription("Abrir menú").performClick()
            compose.onNodeWithTag("main-destination-help").performScrollTo().performClick()
            compose.onNodeWithTag("help-screen").assertIsDisplayed()
            compose.onNodeWithContentDescription("Abrir menú").performClick()
            compose.onNodeWithTag("drawer-action-support").performScrollTo().assertIsDisplayed()
            saveScreen(evidence, "01-menu.png")
            compose.onNodeWithTag("drawer-action-support").performClick()
            compose.waitForIdle()
            UiDevice.getInstance(instrumentation).dumpWindowHierarchy(File(evidence, "02-window.xml"))
            instrumentation.sendStatus(0, Bundle().apply { putString("support27_stage", "BROWSER_OPENED_REVIEW_AND_CONTINUE") })
            val continueFile = File(evidence, "browser-reviewed")
            val deadline = android.os.SystemClock.elapsedRealtime() + 180_000
            while (!continueFile.exists() && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(200)
            }
            check(continueFile.exists()) { "Operator has not verified the actual support page" }
            UiDevice.getInstance(instrumentation).pressBack()
            compose.waitUntil(15_000) { scenario.state == Lifecycle.State.RESUMED }
            awaitRoot()
            compose.onNodeWithTag("help-screen").assertIsDisplayed()
            compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
            saveScreen(evidence, "03-return.png")
            scenario.recreate()
            awaitRoot()
            compose.onNodeWithTag("help-screen").assertIsDisplayed()
            compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
            assertEquals(workBefore, fingerprints().first())
            assertEquals(preferencesBefore, privateFilesFingerprint())
        } finally {
            val cleanupErrors = mutableListOf<Throwable>()
            fun cleanup(action: () -> Unit) { try { action() } catch (error: Throwable) { cleanupErrors += error } }
            cleanup { scenario?.close() }
            cleanup { assertEquals(workBefore, fingerprints().first()) }
            cleanup { assertEquals(preferencesBefore, privateFilesFingerprint()) }
            if (cleanupErrors.isNotEmpty()) throw AssertionError("Supervised cleanup failed", cleanupErrors.first())
        }
    }

    private fun saveScreen(directory: File, name: String) {
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun privateFilesFingerprint(): String {
        val roots = listOf(app.filesDir, File(app.applicationInfo.dataDir, "shared_prefs"))
        val hashes = roots.flatMap { root ->
            if (!root.exists()) emptyList() else root.walkTopDown().filter(File::isFile).filterNot {
                // Only these exact framework cache paths: neither contains MiGuardia user state.
                (root.name == "shared_prefs" && it == File(root, "android.app.ActivityThread.IDS.xml")) ||
                    (root == app.filesDir && it == File(root, "profileInstalled"))
            }.map {
                root.name + "/" + it.relativeTo(root).path + ":" + hash(it.readBytes())
            }.toList()
        }
        return hash(hashes.sorted().joinToString("\n").toByteArray())
    }

    private fun launchIntent() = Intent(app, MainActivity::class.java)
        .putExtra("external_url", "https://example.invalid/untrusted")
        .putExtra("private_extra", "SYNTHETIC_NOT_FOR_BROWSER")

    private fun monitor(outgoing: MutableList<Intent>): Instrumentation.ActivityMonitor {
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                outgoing += Intent(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        return monitor
    }

    private fun awaitRoot() {
        compose.waitUntil(15_000) {
            app.startupRecoveryGate.isReady
        }
        compose.waitForIdle()
    }

    /** Hashes only; a failed assertion cannot dump rows or an activation receipt into test logs. */
    private fun fingerprints(): List<String> {
        val path = app.getDatabasePath("miguardia-v2.db")
        val work = if (!path.exists()) "absent" else SQLiteDatabase.openDatabase(
            path.absolutePath, null, SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            val tables = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table') ORDER BY name", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            assertEquals(27, tables.size)
            val rows = tables.flatMap { table ->
                database.rawQuery("SELECT * FROM \"${table.replace("\"", "\"\"")}\"", null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val digest = MessageDigest.getInstance("SHA-256")
                            digest.update(table.toByteArray())
                            for (i in 0 until cursor.columnCount) {
                                val type = cursor.getType(i)
                                val bytes = when (type) {
                                    Cursor.FIELD_TYPE_NULL -> byteArrayOf()
                                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                                    else -> cursor.getString(i).toByteArray()
                                }
                                digest.update("$type:${bytes.size}:".toByteArray())
                                digest.update(bytes)
                            }
                            add(digest.digest().joinToString("") { "%02x".format(it) })
                        }
                    }
                }
            }
            hash((tables + rows.sorted()).joinToString("\n").toByteArray())
        }
        val beta = File(app.noBackupFilesDir, "beta-v1.preferences_pb")
        return listOf(work, if (beta.exists()) hash(beta.readBytes()) else "absent")
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
