package com.blackatsystems.miguardia

import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.blackatsystems.miguardia.beta.BetaPhase
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Preserves the owner's existing QA state; never activates, seeds, restores or bypasses any gate. */
class UpdatesActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
    private fun awaitRoot() {
        compose.waitUntil(15000) { app.startupRecoveryGate.isReady }
        compose.waitForIdle()
    }
    @Test fun closedRootCannotExposeUpdatesAndRecreationDoesNotMutateExistingTablesOrBeta() {
        check(app.packageName == "com.blackatsystems.miguardia.qa")
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use { scenario ->
            awaitRoot()
            assumeTrue(!app.accessLockCoordinator.state.value.allowsSensitiveContent || !app.startupRecoveryGate.isReady)
            val before = fingerprints()
            compose.onNodeWithTag("drawer-action-updates").assertDoesNotExist()
            scenario.recreate(); awaitRoot()
            compose.onNodeWithTag("updates-screen").assertDoesNotExist()
            assertEquals(before, fingerprints())
        }
    }
    @Test fun activityRestoresTheProducerSequenceBeforeIssuingAnotherCalendarRequest() {
        check(app.packageName == "com.blackatsystems.miguardia.qa")
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use { scenario ->
            awaitRoot()
            val set = MainActivity::class.java.getDeclaredMethod("setCalendarNavigationRequest", Long::class.javaPrimitiveType).apply { isAccessible = true }
            val get = MainActivity::class.java.getDeclaredMethod("getCalendarNavigationRequest").apply { isAccessible = true }
            scenario.onActivity { set.invoke(it, 41L) }
            scenario.recreate(); awaitRoot()
            scenario.onActivity {
                val restored = get.invoke(it) as Long
                assertEquals(41L, restored)
                assertEquals(42L, nextCalendarNavigationRequest(restored))
            }
        }
    }
    @Test fun authorizedRootReturnsToHelpAndPreservesAll27TablesAndPrivatePreferencesWithoutStartingASearch() {
        check(app.packageName == "com.blackatsystems.miguardia.qa")
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use { scenario ->
            awaitRoot()
            assumeTrue("Existing user session must already permit content; no synthetic authority", app.accessLockCoordinator.state.value.allowsSensitiveContent && app.startupRecoveryGate.isReady)
            assumeTrue("User must finish configuration and help", compose.onAllNodesWithTag("main-menu-button").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithContentDescription("Abrir menú").performClick()
            compose.onNodeWithTag("main-destination-help").performScrollTo().performClick()
            val before = fingerprints()
            compose.onNodeWithContentDescription("Abrir menú").performClick()
            compose.onNodeWithTag("drawer-action-updates").performScrollTo().performClick()
            compose.onNodeWithTag("updates-screen").assertIsDisplayed()
            compose.onNodeWithTag("updates-search").performScrollTo().assertIsDisplayed()
            assertEquals(before, fingerprints())
            scenario.recreate(); awaitRoot()
            if (app.accessLockCoordinator.state.value.allowsSensitiveContent && app.startupRecoveryGate.isReady) {
                compose.onNodeWithTag("updates-screen").assertIsDisplayed()
                compose.onNodeWithTag("updates-back").performScrollTo().performClick()
                compose.onNodeWithTag("help-screen").assertIsDisplayed()
            } else compose.onNodeWithTag("updates-screen").assertDoesNotExist()
            assertEquals(before, fingerprints())
        }
    }
    private fun fingerprints(): List<String> {
        val path = app.getDatabasePath("miguardia-v2.db")
        val work = if (!path.exists()) "absent" else SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            val tables = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table') ORDER BY name", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            assertEquals(29, tables.size)
            val rows = tables.flatMap { table ->
                database.rawQuery("SELECT * FROM \"${table.replace("\"", "\"\"")}\"", null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val digest = MessageDigest.getInstance("SHA-256"); digest.update(table.toByteArray())
                            for (i in 0 until cursor.columnCount) {
                                val type = cursor.getType(i)
                                val bytes = when (type) { Cursor.FIELD_TYPE_NULL -> byteArrayOf(); Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i); else -> cursor.getString(i).toByteArray() }
                                digest.update("$type:${bytes.size}:".toByteArray()); digest.update(bytes)
                            }
                            add(digest.digest().joinToString("") { "%02x".format(it) })
                        }
                    }
                }
            }
            hash((tables + rows.sorted()).joinToString("\n").toByteArray())
        }
        val beta = File(app.noBackupFilesDir, "beta-v1.preferences_pb")
        val preferences = listOf(app.filesDir, File(app.applicationInfo.dataDir, "shared_prefs")).flatMap { root ->
            if (!root.exists()) emptyList() else root.walkTopDown().filter(File::isFile).filterNot {
                it == File(app.filesDir, "profileInstalled") || (root.name == "shared_prefs" && it.name == "android.app.ActivityThread.IDS.xml")
            }.map { root.name + "/" + it.relativeTo(root).path + ":" + hash(it.readBytes()) }.toList()
        }
        return listOf(work, if (beta.exists()) hash(beta.readBytes()) else "absent", hash(preferences.sorted().joinToString("\n").toByteArray()))
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
