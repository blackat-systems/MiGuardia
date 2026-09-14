package com.blackatsystems.miguardia

import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.beta.BetaDeviceIssuer
import com.blackatsystems.miguardia.beta.BetaEnvironment
import com.blackatsystems.miguardia.beta.BetaPhase
import com.blackatsystems.miguardia.beta.BetaRecord
import com.blackatsystems.miguardia.beta.BetaStorage
import com.blackatsystems.miguardia.beta.BetaVariant
import com.blackatsystems.miguardia.beta.installedBetaApp
import com.blackatsystems.miguardia.ui.help.OnboardingPreferencesStore
import com.blackatsystems.miguardia.ui.help.OnboardingStoreState
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** MAIN-supervised only. Real Activity return, no update search, server, download or installer. */
class UpdatesSupervisedActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()

    @Test fun authorizedTemporarySessionPreservesHelpAndSummaryAfterRealUpdatesRecreation() {
        assumeTrue("Requires specific owner authorization for a supervised public return",
            InstrumentationRegistry.getArguments().getString("updates28TemporarySession") == "authorized")
        check(app.packageName == "com.blackatsystems.miguardia.qa")
        compose.waitUntil(15_000) { app.startupRecoveryGate.isReady }
        check(app.accessLockCoordinator.state.value.allowsSensitiveContent) { "Owner must unlock; never bypass the lock" }
        runBlocking {
            check(app.localDataStore.workConfiguration.get() != null) { "Existing work configuration required" }
            val onboarding = app.onboardingPreferences.state.first()
            check(onboarding is OnboardingStoreState.Ready &&
                onboarding.completedVersion >= OnboardingPreferencesStore.CURRENT_VERSION) { "Owner must finish onboarding" }
        }

        val notificationWasPaused = app.notificationRuntime.isPausedForRestore
        val widgetWasPaused = app.widgetRuntime.isPausedForRestore
        val workBefore = workFingerprint()
        val preferencesBefore = privateFilesFingerprint()
        val evidence = File(app.cacheDir, "updates28-supervised")
        check(!evidence.exists()) { "MAIN must first handle previous supervised evidence" }
        check(evidence.mkdir())
        var scenario: ActivityScenario<MainActivity>? = null
        var originalFailure: Throwable? = null
        try {
            runBlocking {
                app.notificationRuntime.pauseForRestore()
                app.widgetRuntime.pauseForRestore()
                app.weatherRuntime.cancelRefresh()
            }
            // ActivityScenario matches lifecycle callbacks by the original Intent action.
            // Keep that launch stable: the app deliberately clears consumed external actions.
            val activeScenario = ActivityScenario.launch<MainActivity>(
                Intent(app, MainActivity::class.java),
            )
            scenario = activeScenario
            awaitRoot()
            awaitTag("month-grid")
            // Deliver through the real Activity callback, without changing its saved producer.
            activeScenario.onActivity { activity ->
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity,
                    Intent(app, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CALENDAR))
            }
            compose.waitUntil(15_000) { app.pendingMainDestinations.state.value == null }
            compose.waitForIdle()
            activeScenario.onActivity { activity ->
                val getter = MainActivity::class.java.getDeclaredMethod("getCalendarNavigationRequest").apply { isAccessible = true }
                assertTrue((getter.invoke(activity) as Long) > 0L)
                assertNull(activity.intent.action)
            }

            for ((destination, tag) in listOf("help" to "help-screen", "summary" to "summary-overview")) {
                navigate(destination)
                awaitTag(tag)
                compose.onNodeWithContentDescription("Abrir menú").performClick()
                compose.onNodeWithTag("drawer-action-updates").performScrollTo().performClick()
                awaitTag("updates-screen")
                compose.onNodeWithTag("updates-search").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("updates-status").assertTextEquals("Buscá cuando quieras comprobar si hay otra versión.")
                if (destination == "help") saveSafeNode(evidence, "01-updates.png", "updates-surface")

                // First reproduce the original bug without recreation: the positive
                // request must not replay merely because the content is remounted.
                compose.onNodeWithTag("updates-back").performScrollTo().performClick()
                awaitTag(tag)
                compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
                compose.onNodeWithContentDescription("Abrir menú").performClick()
                compose.onNodeWithTag("drawer-action-updates").performScrollTo().performClick()
                awaitTag("updates-screen")
                activeScenario.recreate()
                awaitRoot()
                awaitTag("updates-screen")
                compose.onNodeWithTag("updates-status").assertTextEquals("Buscá cuando quieras comprobar si hay otra versión.")
                compose.onNodeWithTag("updates-back").performScrollTo().performClick()
                awaitTag(tag)
                compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
                if (destination == "help") saveSafeNode(evidence, "02-help-return.png", "help-screen")
                activeScenario.recreate()
                awaitRoot()
                awaitTag(tag)
                compose.onNodeWithTag("updates-screen").assertDoesNotExist()
                assertEquals(workBefore, workFingerprint())
                assertEquals(preferencesBefore, privateFilesFingerprint())
            }
            File(evidence, "return-verified").writeText("Help and Summary returned after real Activity recreation; no search or download.\n")
        } catch (failure: Throwable) {
            originalFailure = failure
            throw failure
        } finally {
            val cleanupErrors = mutableListOf<Throwable>()
            fun cleanup(action: () -> Unit) { try { action() } catch (error: Throwable) { cleanupErrors += error } }
            cleanup { scenario?.close() }
            cleanup { runBlocking { if (!notificationWasPaused) app.notificationRuntime.resumeAfterRestore() } }
            cleanup { runBlocking { if (!widgetWasPaused) app.widgetRuntime.resumeAfterRestore() } }
            cleanup { assertEquals(workBefore, workFingerprint()) }
            cleanup { assertEquals(preferencesBefore, privateFilesFingerprint()) }
            if (cleanupErrors.isNotEmpty()) {
                val failure = originalFailure ?: AssertionError("Supervised return cleanup failed")
                cleanupErrors.forEach(failure::addSuppressed)
                if (originalFailure == null) throw failure
            } else File(evidence, "cleanup-verified").writeText("Public root; work and preferences unchanged.\n")
        }
    }

    private fun awaitRoot() {
        compose.waitUntil(15_000) {
            app.startupRecoveryGate.isReady
        }
        compose.waitForIdle()
        check(app.accessLockCoordinator.state.value.allowsSensitiveContent) { "Closed lock requires the owner; no bypass" }
        check(app.startupRecoveryGate.isReady)
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun navigate(destination: String) {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithTag("main-destination-$destination").performScrollTo().performClick()
    }

    /** Crop to safe QA UI only: never capture Summary, Calendar, system dialogs or other apps. */
    private fun saveSafeNode(directory: File, name: String, tag: String) {
        check(tag == "updates-surface" || tag == "help-screen")
        val bitmap = compose.onNodeWithTag(tag).assertIsDisplayed().captureToImage().asAndroidBitmap()
        try {
            File(directory, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    /** Hashes only; failed assertions must not print database rows or private preference contents. */
    private fun workFingerprint(): String {
        val path = app.getDatabasePath("miguardia-v2.db")
        check(path.exists())
        return SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            val tables = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table') ORDER BY name", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            assertEquals(28, tables.size)
            val rows = tables.flatMap { table ->
                database.rawQuery("SELECT * FROM \"${table.replace("\"", "\"\"")}\"", null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val digest = MessageDigest.getInstance("SHA-256")
                            digest.update(table.toByteArray())
                            for (column in 0 until cursor.columnCount) {
                                val type = cursor.getType(column)
                                val bytes = when (type) {
                                    Cursor.FIELD_TYPE_NULL -> byteArrayOf()
                                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(column)
                                    else -> cursor.getString(column).toByteArray()
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
    }

    private fun privateFilesFingerprint(): String {
        val roots = listOf(app.filesDir, File(app.applicationInfo.dataDir, "shared_prefs"))
        val values = roots.flatMap { root ->
            if (!root.exists()) emptyList() else root.walkTopDown().filter(File::isFile).filterNot {
                (root == app.filesDir && it == File(root, "profileInstalled")) ||
                    (root.name == "shared_prefs" && it == File(root, "android.app.ActivityThread.IDS.xml"))
            }.map { root.name + "/" + it.relativeTo(root).path + ":" + hash(it.readBytes()) }.toList()
        }
        return hash(values.sorted().joinToString("\n").toByteArray())
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
