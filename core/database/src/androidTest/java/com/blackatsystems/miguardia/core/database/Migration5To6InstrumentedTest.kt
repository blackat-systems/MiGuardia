package com.blackatsystems.miguardia.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationOrigin
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration5To6InstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MiGuardiaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun cleanUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationFromV5PreservesThirteenFamiliesAndCreatesEmptyMigratedRoot() {
        helper.createDatabase(DATABASE_NAME, 5).apply {
            V5HistoricalFixture.seedAllFamilies(this)
            close()
        }

        val database = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            6,
            true,
            MIGRATION_5_6,
        )

        assertEquals(6, database.version)
        V5HistoricalFixture.assertAllFamiliesPreserved(database)
        assertMigratedRootWithoutConfiguration(database)
        assertV6TablesAndIndexes(database)
        V5HistoricalFixture.assertNoForeignKeyViolations(database)
        database.close()

        val store = LocalDataStore.create(context, DATABASE_NAME)
        try {
            runBlocking {
                val history = requireNotNull(store.workConfiguration.get())
                assertEquals(WorkConfigurationOrigin.MIGRATED_V1, history.origin)
                assertEquals(
                    UUID.fromString(MIGRATED_V1_WORK_CONFIGURATION_TIMELINE_ID),
                    history.timeline.id,
                )
                assertTrue(history.timeline.revisions.isEmpty())
                assertTrue(history.perPeriodHoursValues.entries.isEmpty())
                assertEquals(
                    "QA",
                    store.objectives.getById(UUID.fromString(V5HistoricalFixture.OBJECTIVE_ID))?.abbreviation,
                )

                val activationDate = LocalDate.of(2026, 9, 1)
                val firstV2Revision = EffectiveRevision(
                    id = UUID.fromString(FIRST_MIGRATED_V2_REVISION_ID),
                    effectiveFrom = activationDate,
                    value = WorkConfiguration(
                        sector = WorkSector.POLICE,
                        hoursReference = HoursReference.PendingSetup,
                        availabilityLabel = null,
                    ),
                )
                store.workConfiguration.addRevision(
                    timelineId = UUID.fromString(MIGRATED_V1_WORK_CONFIGURATION_TIMELINE_ID),
                    revision = firstV2Revision,
                )

                val activated = requireNotNull(store.workConfiguration.get())
                assertEquals(WorkConfigurationOrigin.MIGRATED_V1, activated.origin)
                assertEquals(listOf(firstV2Revision), activated.timeline.revisions)
                assertEquals(null, activated.timeline.valueAt(activationDate.minusDays(1)))
                assertEquals(firstV2Revision.value, activated.timeline.valueAt(activationDate))
                assertEquals(
                    "QA",
                    store.objectives.getById(UUID.fromString(V5HistoricalFixture.OBJECTIVE_ID))?.abbreviation,
                )
            }
        } finally {
            store.close()
        }
    }

    @Test
    fun completeChainFromV1ToV6PreservesOriginalFamilies() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            V5HistoricalFixture.seedV1Families(this)
            close()
        }

        val database = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            6,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
        )

        V5HistoricalFixture.assertV1FamiliesPreserved(database)
        assertEquals(1, rowCount(database, "shifts"))
        assertMigratedRootWithoutConfiguration(database)
        assertEquals(0, rowCount(database, "holidays"))
        assertEquals(0, rowCount(database, "vacations"))
        assertEquals(0, rowCount(database, "schedule_photos"))
        assertEquals(0, rowCount(database, "shift_notification_configs"))
        V5HistoricalFixture.assertNoForeignKeyViolations(database)
        database.close()
    }

    @Test
    fun freshV7DatabaseStartsWithoutRootCatalogOrUniversalDefaults() {
        val database = MiGuardiaDatabase.build(context, DATABASE_NAME)
        try {
            val sqlite = database.openHelper.writableDatabase

            assertEquals(7, sqlite.version)
            assertEquals(1, scalarInt(sqlite, "PRAGMA foreign_keys"))
            assertEquals(22, applicationTableCount(sqlite))
            assertEquals(0, rowCount(sqlite, "work_configuration_roots"))
            assertEquals(0, rowCount(sqlite, "per_period_hours_definitions"))
            assertEquals(0, rowCount(sqlite, "work_configuration_revisions"))
            assertEquals(0, rowCount(sqlite, "per_period_hours_values"))
            assertEquals(0, rowCount(sqlite, "work_places"))
            assertEquals(0, rowCount(sqlite, "work_types"))
            assertEquals(0, rowCount(sqlite, "work_templates"))
            assertEquals(0, rowCount(sqlite, "workplace_rule_revisions"))
            assertEquals(0, rowCount(sqlite, "shift_work_snapshots"))
            assertEquals(
                0,
                scalarInt(
                    sqlite,
                    "SELECT COUNT(*) FROM work_configuration_revisions WHERE requiredMinutes = 12240",
                ),
            )
            assertEquals(
                0,
                scalarInt(
                    sqlite,
                    "SELECT COUNT(*) FROM work_configuration_revisions WHERE sector IS NOT NULL",
                ),
            )
            V5HistoricalFixture.assertNoForeignKeyViolations(sqlite)
        } finally {
            database.close()
        }
    }

    @Test
    fun migratedSchemaEnforcesForeignKeysAndUniqueKeys() {
        helper.createDatabase(DATABASE_NAME, 5).close()
        val database = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            6,
            true,
            MIGRATION_5_6,
        )

        // MigrationTestHelper entrega una conexión de validación cruda; la FK es por conexión.
        database.setForeignKeyConstraintsEnabled(true)
        assertEquals(1, scalarInt(database, "PRAGMA foreign_keys"))

        database.execSQL(
            """INSERT INTO per_period_hours_definitions (
                id, timelineId, periodKind, weeklyFirstDayIso, cycleAnchorDate, cycleLengthDays
            ) VALUES (
                '$DEFINITION_ID', '$MIGRATED_V1_WORK_CONFIGURATION_TIMELINE_ID',
                'MONTHLY', NULL, NULL, NULL
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO work_configuration_revisions (
                id, timelineId, effectiveFrom, sector, availabilityLabel,
                hoursReferenceKind, periodKind, weeklyFirstDayIso,
                cycleAnchorDate, cycleLengthDays, requiredMinutes, perPeriodDefinitionId
            ) VALUES (
                '$REVISION_ID', '$MIGRATED_V1_WORK_CONFIGURATION_TIMELINE_ID',
                '2026-09-01', 'PRIVATE_SECURITY', NULL,
                'PER_PERIOD', NULL, NULL, NULL, NULL, NULL, '$DEFINITION_ID'
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO per_period_hours_values (
                id, definitionId, windowStartInclusive, windowEndExclusive, requiredMinutes
            ) VALUES (
                '$VALUE_ID', '$DEFINITION_ID', '2026-09-01', '2026-10-01', 9600
            )""".trimIndent(),
        )

        assertSqlRejected(database) {
            execSQL(
                "INSERT INTO work_configuration_roots VALUES ('$SECOND_TIMELINE_ID', 1, 'NEW_V2')",
            )
        }
        assertSqlRejected(database) {
            execSQL(
                """INSERT INTO per_period_hours_definitions VALUES (
                    '$ORPHAN_ID', '$SECOND_TIMELINE_ID', 'MONTHLY', NULL, NULL, NULL
                )""".trimIndent(),
            )
        }
        assertSqlRejected(database) {
            execSQL(
                """INSERT INTO work_configuration_revisions VALUES (
                    '$SECOND_REVISION_ID', '$MIGRATED_V1_WORK_CONFIGURATION_TIMELINE_ID',
                    '2026-09-01', 'POLICE', NULL, 'NOT_USED',
                    NULL, NULL, NULL, NULL, NULL, NULL
                )""".trimIndent(),
            )
        }
        assertSqlRejected(database) {
            execSQL(
                """INSERT INTO per_period_hours_values VALUES (
                    '$SECOND_VALUE_ID', '$DEFINITION_ID', '2026-09-01', '2026-10-01', 9700
                )""".trimIndent(),
            )
        }
        assertSqlRejected(database) {
            execSQL(
                """INSERT INTO per_period_hours_values VALUES (
                    '$ORPHAN_ID', '$SECOND_DEFINITION_ID', '2026-10-01', '2026-11-01', 9800
                )""".trimIndent(),
            )
        }
        assertSqlRejected(database) {
            execSQL(
                "DELETE FROM work_configuration_roots WHERE timelineId = '$MIGRATED_V1_WORK_CONFIGURATION_TIMELINE_ID'",
            )
        }

        assertEquals(1, rowCount(database, "work_configuration_roots"))
        assertEquals(1, rowCount(database, "per_period_hours_definitions"))
        assertEquals(1, rowCount(database, "work_configuration_revisions"))
        assertEquals(1, rowCount(database, "per_period_hours_values"))
        V5HistoricalFixture.assertNoForeignKeyViolations(database)
        database.close()
    }

    @Test
    fun failedMigrationRollsBackWritesAndKeepsUserVersionFive() {
        helper.createDatabase(DATABASE_NAME, 5).apply {
            V5HistoricalFixture.seedAllFamilies(this)
            close()
        }
        val failingMigration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE migration_failure_marker (id INTEGER NOT NULL PRIMARY KEY)")
                db.execSQL(
                    "UPDATE objectives SET fullName = 'Nombre que debe revertirse' WHERE id = '${V5HistoricalFixture.OBJECTIVE_ID}'",
                )
                error("Fallo ficticio de migración")
            }
        }
        val roomDatabase = Room.databaseBuilder(
            context,
            MiGuardiaDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(failingMigration).build()

        try {
            assertThrowsAny { roomDatabase.openHelper.writableDatabase }
        } finally {
            roomDatabase.close()
        }

        openRawDatabase().use { database ->
            assertEquals(5, database.version)
            assertRawHistoricalCounts(database)
            database.rawQuery(
                "SELECT fullName FROM objectives WHERE id = ?",
                arrayOf(V5HistoricalFixture.OBJECTIVE_ID),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Objetivo histórico ficticio", cursor.getString(0))
            }
            database.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'migration_failure_marker'",
                null,
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun missingMigrationPathFailsWithoutDeletingV5Data() {
        helper.createDatabase(DATABASE_NAME, 5).apply {
            V5HistoricalFixture.seedAllFamilies(this)
            close()
        }
        val roomDatabase = Room.databaseBuilder(
            context,
            MiGuardiaDatabase::class.java,
            DATABASE_NAME,
        ).build()

        try {
            assertThrowsAny { roomDatabase.openHelper.writableDatabase }
        } finally {
            roomDatabase.close()
        }

        openRawDatabase().use { database ->
            assertEquals(5, database.version)
            assertRawHistoricalCounts(database)
            database.rawQuery("SELECT abbreviation FROM objectives", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("QA", cursor.getString(0))
            }
            database.rawQuery("SELECT COUNT(*) FROM shifts", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
            database.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'work_configuration_roots'",
                null,
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private fun assertMigratedRootWithoutConfiguration(database: SupportSQLiteDatabase) {
        database.query(
            "SELECT timelineId, singletonSlot, origin FROM work_configuration_roots",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(MIGRATED_V1_WORK_CONFIGURATION_TIMELINE_ID, cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("MIGRATED_V1", cursor.getString(2))
            assertFalse(cursor.moveToNext())
        }
        assertEquals(0, rowCount(database, "per_period_hours_definitions"))
        assertEquals(0, rowCount(database, "work_configuration_revisions"))
        assertEquals(0, rowCount(database, "per_period_hours_values"))
    }

    private fun assertV6TablesAndIndexes(database: SupportSQLiteDatabase) {
        assertEquals(17, applicationTableCount(database))
        assertIndex(database, "work_configuration_roots", "index_work_configuration_roots_singletonSlot", unique = true)
        assertIndex(database, "per_period_hours_definitions", "index_per_period_hours_definitions_timelineId", unique = false)
        assertIndex(
            database,
            "work_configuration_revisions",
            "index_work_configuration_revisions_timelineId_effectiveFrom",
            unique = true,
        )
        assertIndex(
            database,
            "work_configuration_revisions",
            "index_work_configuration_revisions_perPeriodDefinitionId",
            unique = false,
        )
        assertIndex(
            database,
            "per_period_hours_values",
            "index_per_period_hours_values_definitionId_windowStartInclusive",
            unique = true,
        )
        assertForeignKey(
            database,
            table = "per_period_hours_definitions",
            parentTable = "work_configuration_roots",
            childColumn = "timelineId",
            onDelete = "RESTRICT",
        )
        assertForeignKey(
            database,
            table = "work_configuration_revisions",
            parentTable = "per_period_hours_definitions",
            childColumn = "perPeriodDefinitionId",
            onDelete = "RESTRICT",
        )
        assertForeignKey(
            database,
            table = "per_period_hours_values",
            parentTable = "per_period_hours_definitions",
            childColumn = "definitionId",
            onDelete = "RESTRICT",
        )
    }

    private fun assertIndex(
        database: SupportSQLiteDatabase,
        table: String,
        index: String,
        unique: Boolean,
    ) {
        database.query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val uniqueColumn = cursor.getColumnIndexOrThrow("unique")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == index) {
                    assertEquals(index, if (unique) 1 else 0, cursor.getInt(uniqueColumn))
                    found = true
                }
            }
            assertTrue("No se encontró el índice $index", found)
        }
    }

    private fun assertForeignKey(
        database: SupportSQLiteDatabase,
        table: String,
        parentTable: String,
        childColumn: String,
        onDelete: String,
    ) {
        database.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
            val parentColumn = cursor.getColumnIndexOrThrow("table")
            val fromColumn = cursor.getColumnIndexOrThrow("from")
            val onDeleteColumn = cursor.getColumnIndexOrThrow("on_delete")
            var found = false
            while (cursor.moveToNext()) {
                if (
                    cursor.getString(parentColumn) == parentTable &&
                    cursor.getString(fromColumn) == childColumn
                ) {
                    assertEquals(onDelete, cursor.getString(onDeleteColumn))
                    found = true
                }
            }
            assertTrue("No se encontró la FK $table.$childColumn → $parentTable", found)
        }
    }

    private fun assertSqlRejected(
        database: SupportSQLiteDatabase,
        operation: SupportSQLiteDatabase.() -> Unit,
    ) {
        assertThrowsAny { database.operation() }
    }

    private fun assertThrowsAny(block: () -> Unit): Throwable {
        var thrown: Throwable? = null
        try {
            block()
        } catch (error: Throwable) {
            thrown = error
        }
        return thrown ?: throw AssertionError("Se esperaba que la operación fuera rechazada")
    }

    private fun applicationTableCount(database: SupportSQLiteDatabase): Int = scalarInt(
        database,
        """SELECT COUNT(*) FROM sqlite_master
            WHERE type = 'table'
              AND name NOT LIKE 'android_%'
              AND name NOT LIKE 'room_%'
              AND name NOT LIKE 'sqlite_%'""".trimIndent(),
    )

    private fun rowCount(database: SupportSQLiteDatabase, table: String): Int =
        scalarInt(database, "SELECT COUNT(*) FROM $table")

    private fun scalarInt(database: SupportSQLiteDatabase, query: String): Int =
        database.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun openRawDatabase(): SQLiteDatabase = SQLiteDatabase.openDatabase(
        context.getDatabasePath(DATABASE_NAME).path,
        null,
        SQLiteDatabase.OPEN_READONLY,
    )

    private fun assertRawHistoricalCounts(database: SQLiteDatabase) {
        mapOf(
            "objectives" to 1,
            "schedule_combinations" to 1,
            "shifts" to 2,
            "explicit_day_statuses" to 1,
            "medical_leaves" to 1,
            "holidays" to 1,
            "shift_notes" to 1,
            "shift_novelties" to 1,
            "formal_shift_changes" to 1,
            "vacations" to 1,
            "schedule_photos" to 1,
            "shift_notification_configs" to 1,
            "shift_notification_reminders" to 2,
        ).forEach { (table, expected) ->
            database.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(table, expected, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME: String = "migration-5-6-test.db"
        const val FIRST_MIGRATED_V2_REVISION_ID: String =
            "00000000-0000-0000-0000-000000000101"
        const val SECOND_TIMELINE_ID: String = "00000000-0000-0000-0000-000000000601"
        const val DEFINITION_ID: String = "00000000-0000-0000-0000-000000000611"
        const val SECOND_DEFINITION_ID: String = "00000000-0000-0000-0000-000000000612"
        const val REVISION_ID: String = "00000000-0000-0000-0000-000000000621"
        const val SECOND_REVISION_ID: String = "00000000-0000-0000-0000-000000000622"
        const val VALUE_ID: String = "00000000-0000-0000-0000-000000000631"
        const val SECOND_VALUE_ID: String = "00000000-0000-0000-0000-000000000632"
        const val ORPHAN_ID: String = "00000000-0000-0000-0000-000000000699"
    }
}
