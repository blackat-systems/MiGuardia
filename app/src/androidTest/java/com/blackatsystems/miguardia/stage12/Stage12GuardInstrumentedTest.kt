package com.blackatsystems.miguardia.stage12

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage12GuardInstrumentedTest {
    @Test fun rootContextRoutesReviewedStorageApisAndPreservesCanaries() {
        val session = Stage12Session.current
        val binding = session.binding
        val context = Stage12RootContext(binding.live, binding)
        val unbound = Stage12RootContext(binding.live, Stage12RootBinding(binding.live))
        assertThrows(IllegalStateException::class.java) { unbound.filesDir }
        assertThrows(IllegalStateException::class.java) { unbound.cacheDir }
        assertThrows(IllegalStateException::class.java) { unbound.getSharedPreferences("before-admission", 0) }
        listOf("../escape", "/escape", "C:/escape", "a\\b", "a/../../escape").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { context.openFileOutput(name, Context.MODE_PRIVATE) }
            assertThrows(IllegalArgumentException::class.java) { context.getDatabasePath(name) }
            assertThrows(IllegalArgumentException::class.java) { context.getSharedPreferences(name, Context.MODE_PRIVATE) }
        }
        assertThrows(SecurityException::class.java) { context.getExternalFilesDir(null) }
        assertThrows(SecurityException::class.java) { context.createDeviceProtectedStorageContext() }
        assertThrows(SecurityException::class.java) { context.createDeviceContext(0) }
        assertThrows(SecurityException::class.java) { context.createPackageContext(context.packageName, 0) }
        assertThrows(SecurityException::class.java) { context.createAttributionContext("stage12") }
        assertThrows(SecurityException::class.java) { context.createWindowContext(android.view.WindowManager.LayoutParams.TYPE_APPLICATION, null) }
        assertThrows(SecurityException::class.java) { context.createContext(android.content.ContextParams.Builder().build()) }
        assertThrows(SecurityException::class.java) { context.getSystemService(Context.ALARM_SERVICE) }
        assertThrows(SecurityException::class.java) { context.contentResolver }
        session.record("S12-B03", "root-context", "rejected-unbound-traversal-and-external", "FILES_ISOLATED", "android-context-guard")

        assertSame(binding.application, context.applicationContext)
        assertEquals(binding.root, context.dataDir)
        assertEquals(binding.root.path, context.applicationInfo.dataDir)
        assertEquals(binding.path("device_protected").path, context.applicationInfo.deviceProtectedDataDir)
        assertEquals(binding.directory("files"), context.applicationContext.filesDir)
        context.openFileOutput("guard.txt", Context.MODE_PRIVATE).use { it.write("synthetic".toByteArray()) }
        assertEquals("synthetic", context.openFileInput("guard.txt").bufferedReader().use { it.readText() })
        assertTrue("guard.txt" in context.fileList())
        assertTrue(context.getFileStreamPath("guard.txt").canonicalFile.toPath().startsWith(binding.root.toPath()))
        val preferences = context.getSharedPreferences("guard", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().putString("value", "synthetic").commit())
        assertEquals("synthetic", context.applicationContext.getSharedPreferences("guard", 0).getString("value", null))
        context.openOrCreateDatabase("guard.db", Context.MODE_PRIVATE, null).use { database ->
            database.execSQL("CREATE TABLE sample (id INTEGER NOT NULL)")
            database.execSQL("INSERT INTO sample VALUES (12)")
            database.rawQuery("SELECT id FROM sample", null).use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(12, cursor.getInt(0)); assertFalse(cursor.moveToNext()) }
        }
        assertTrue(context.getDatabasePath("guard.db").toPath().startsWith(binding.root.toPath()))
        assertTrue("guard.db" in context.databaseList())
        assertTrue(context.deleteDatabase("guard.db"))
        context.openOrCreateDatabase("wal.db", Context.MODE_ENABLE_WRITE_AHEAD_LOGGING or Context.MODE_NO_LOCALIZED_COLLATORS, null).use {
            assertTrue("Accepted WAL mode must retain its SQLite semantics", it.isWriteAheadLoggingEnabled)
        }
        assertTrue(context.deleteDatabase("wal.db"))
        assertThrows(IllegalArgumentException::class.java) { context.openOrCreateDatabase("world.db", 1, null) }
        assertFalse("world.db" in context.databaseList())
        assertTrue(context.deleteFile("guard.txt"))
        assertFalse("guard.txt" in context.fileList())
        session.record("S12-B03", "root-context", "owned-file-preferences-sqlite-readback", "FILES_ISOLATED", "android-storage-readback")
        // Received SQLite is not opened or queried. Canary/baseline checks are independent in finish.
    }
}
