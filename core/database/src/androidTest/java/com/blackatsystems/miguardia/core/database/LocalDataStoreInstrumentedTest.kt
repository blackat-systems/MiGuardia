package com.blackatsystems.miguardia.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDataStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaV2Database

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(V2_TEST_DB)
        context.deleteDatabase(LEGACY_TEST_DB)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) database.close()
        context.deleteDatabase(V2_TEST_DB)
        context.deleteDatabase(LEGACY_TEST_DB)
    }

    @Test
    fun v2DatabaseStartsAtVersionTwoWithTheExactTwentyTwoTables() {
        database = MiGuardiaV2Database.build(context)
        val sqlite = database.openHelper.writableDatabase
        val tables = linkedSetOf<String>()
        sqlite.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')",
        ).use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }

        assertEquals(2, sqlite.version)
        assertEquals(EXPECTED_TABLES, tables)
        assertFalse("schedule_combinations" in tables)
        assertFalse("shift_novelties" in tables)
        assertFalse("formal_shift_changes" in tables)
    }

    @Test
    fun removedV1ColumnsAreAbsentAndSourceObjectiveIsRequired() {
        database = MiGuardiaV2Database.build(context, V2_TEST_DB)
        val sqlite = database.openHelper.writableDatabase

        assertFalse("origin" in columnNames(sqlite, "work_configuration_roots"))
        assertFalse("legacyScheduleCombinationId" in columnNames(sqlite, "work_templates"))
        assertFalse("sourceScheduleCombinationId" in columnNames(sqlite, "shifts"))
        val shiftColumns = tableInfo(sqlite, "shifts")
        assertEquals(1, shiftColumns.getValue("sourceObjectiveId"))
    }

    @Test
    fun newV2DatabaseStartsWithoutApplicationRowsOrImplicitDefaults() {
        database = MiGuardiaV2Database.build(context, V2_TEST_DB)
        val sqlite = database.openHelper.writableDatabase

        EXPECTED_TABLES.forEach { table ->
            sqlite.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("La tabla $table debe nacer vacía", 0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun exportedV2SchemasKeepVersionOneAndMatchVersionTwo() {
        val assetRoot = "com.blackatsystems.miguardia.core.database.MiGuardiaV2Database"
        val versionOne = InstrumentationRegistry.getInstrumentation().context.assets.open(
            "$assetRoot/1.json",
        ).use { input -> input.readBytes() }
        val versionTwo = InstrumentationRegistry.getInstrumentation().context.assets.open(
            "$assetRoot/2.json",
        ).use { input -> input.readBytes() }

        assertEquals(EXPECTED_SCHEMA_ONE_SHA256, sha256(versionOne))
        assertEquals(EXPECTED_SCHEMA_TWO_SHA256, sha256(versionTwo))
    }

    @Test
    fun instrumentationResetIsRejectedOutsideTheQaApplication() {
        database = MiGuardiaV2Database.build(context, V2_TEST_DB)
        val store = LocalDataStore(database)

        val failure = runCatching { store.clearAllDataForInstrumentation() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun openingV2UsesAnotherFileAndDoesNotTransformTheLegacyDatabase() {
        assertEquals("miguardia-v2.db", MiGuardiaV2Database.DATABASE_NAME)
        assertEquals(
            context.getDatabasePath(MiGuardiaV2Database.DATABASE_NAME),
            context.getDatabasePath(V2_TEST_DB),
        )
        assertFalse(context.getDatabasePath(V2_TEST_DB) == context.getDatabasePath(LEGACY_TEST_DB))
        val legacyPath = context.getDatabasePath(LEGACY_TEST_DB)
        legacyPath.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(legacyPath, null).use { legacy ->
            legacy.execSQL("CREATE TABLE legacy_sentinel(value TEXT NOT NULL)")
            legacy.execSQL("INSERT INTO legacy_sentinel(value) VALUES ('intacto')")
            legacy.version = 7
        }
        val legacyHashBefore = sha256(legacyPath.readBytes())

        database = MiGuardiaV2Database.build(context)
        database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertFalse(cursor.moveToFirst())
        }
        database.close()
        val legacyHashAfter = sha256(legacyPath.readBytes())
        assertEquals(legacyHashBefore, legacyHashAfter)

        SQLiteDatabase.openDatabase(legacyPath.path, null, SQLiteDatabase.OPEN_READONLY).use { legacy ->
            assertEquals(7, legacy.version)
            legacy.rawQuery("SELECT value FROM legacy_sentinel", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("intacto", cursor.getString(0))
            }
        }
        assertTrue(context.getDatabasePath(V2_TEST_DB).exists())
    }

    private fun columnNames(sqlite: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Set<String> =
        tableInfo(sqlite, table).keys

    private fun tableInfo(
        sqlite: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
    ): Map<String, Int> = buildMap {
        sqlite.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            while (cursor.moveToNext()) put(cursor.getString(nameIndex), cursor.getInt(notNullIndex))
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val V2_TEST_DB = MiGuardiaV2Database.DATABASE_NAME
        const val LEGACY_TEST_DB = "miguardia.db"
        const val EXPECTED_SCHEMA_ONE_SHA256 =
            "5769c0f57667f7fa5a7c1c1da5474474537094a759f8fa4a0d66e6ef37c1287e"
        const val EXPECTED_SCHEMA_TWO_SHA256 = "e5a79603a6dd79532ef9f4a8f9ff241a6588424513107837aee707186c046c50"
        val EXPECTED_TABLES = linkedSetOf(
            "objectives",
            "shifts",
            "shift_work_snapshots",
            "explicit_day_statuses",
            "medical_leaves",
            "holidays",
            "shift_notes",
            "vacations",
            "schedule_photos",
            "shift_notification_configs",
            "shift_notification_reminders",
            "work_configuration_roots",
            "per_period_hours_definitions",
            "work_configuration_revisions",
            "per_period_hours_values",
            "work_places",
            "work_types",
            "work_templates",
            "workplace_rule_revisions",
            "recurring_plans",
            "recurring_plan_revisions",
            "recurring_occurrences",
        )
    }
}
