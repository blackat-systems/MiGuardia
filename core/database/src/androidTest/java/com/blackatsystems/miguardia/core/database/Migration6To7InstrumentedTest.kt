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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration6To7InstrumentedTest {
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
    fun migrationFromV6PreservesSeventeenFamiliesAndAddsFiveEmptyTables() {
        helper.createDatabase(DATABASE_NAME, 6).apply {
            seedAllV6Families(this)
            close()
        }

        val database = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            7,
            true,
            MIGRATION_6_7,
        )

        assertEquals(7, database.version)
        assertEquals(22, applicationTableCount(database))
        V5HistoricalFixture.assertAllFamiliesPreserved(database)
        assertV6ConfigurationPreserved(database)
        assertV7TablesEmpty(database)
        assertV7IndexesAndForeignKeys(database)
        V5HistoricalFixture.assertNoForeignKeyViolations(database)
        database.close()
    }

    @Test
    fun completeChainFromV1ToV7PreservesOriginalFamiliesAndCreatesEmptyCatalog() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            V5HistoricalFixture.seedV1Families(this)
            close()
        }

        val database = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            7,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
        )

        assertEquals(7, database.version)
        assertEquals(22, applicationTableCount(database))
        V5HistoricalFixture.assertV1FamiliesPreserved(database)
        database.query(
            "SELECT timelineId, origin FROM work_configuration_roots",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(MIGRATED_V1_WORK_CONFIGURATION_TIMELINE_ID, cursor.getString(0))
            assertEquals("MIGRATED_V1", cursor.getString(1))
            assertFalse(cursor.moveToNext())
        }
        assertEquals(0, rowCount(database, "per_period_hours_definitions"))
        assertEquals(0, rowCount(database, "work_configuration_revisions"))
        assertEquals(0, rowCount(database, "per_period_hours_values"))
        assertV7TablesEmpty(database)
        V5HistoricalFixture.assertNoForeignKeyViolations(database)
        database.close()
    }

    @Test
    fun freshV7DatabaseHasTwentyTwoTablesAndNoInventedRootOrCatalog() {
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
            assertV7TablesEmpty(sqlite)
            V5HistoricalFixture.assertNoForeignKeyViolations(sqlite)
        } finally {
            database.close()
        }
    }

    @Test
    fun migratedV7SchemaEnforcesForeignKeysAndDeclaredIndexes() {
        helper.createDatabase(DATABASE_NAME, 6).apply {
            seedAllV6Families(this)
            close()
        }
        val database = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            7,
            true,
            MIGRATION_6_7,
        )
        database.setForeignKeyConstraintsEnabled(true)

        assertEquals(1, scalarInt(database, "PRAGMA foreign_keys"))
        assertV7IndexesAndForeignKeys(database)
        insertValidV7CatalogAndSnapshot(database)
        V7_TABLES.forEach { table -> assertEquals(table, 1, rowCount(database, table)) }
        V5HistoricalFixture.assertNoForeignKeyViolations(database)

        assertSqlRejected(database) {
            execSQL(
                """INSERT INTO work_types (
                    id, timelineId, sector, name, normalizedNameKey, behavior,
                    isActive, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    '$ORPHAN_ID', '$ORPHAN_ID', 'PRIVATE_SECURITY', 'Huérfano',
                    'HUÉRFANO', 'ACTIVE_WORK', 1, 1700001000000, 1700001000000
                )""".trimIndent(),
            )
        }
        assertSqlRejected(database) {
            execSQL(
                """INSERT INTO work_types (
                    id, timelineId, sector, name, normalizedNameKey, behavior,
                    isActive, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    '$DUPLICATE_ID', '$TIMELINE_ID', 'PRIVATE_SECURITY', 'guardia habitual',
                    'GUARDIA HABITUAL', 'ACTIVE_WORK', 1, 1700001000000, 1700001000000
                )""".trimIndent(),
            )
        }
        assertSqlRejected(database) {
            execSQL("DELETE FROM objectives WHERE id = '${V5HistoricalFixture.OBJECTIVE_ID}'")
        }

        V7_TABLES.forEach { table -> assertEquals(table, 1, rowCount(database, table)) }
        V5HistoricalFixture.assertNoForeignKeyViolations(database)
        database.close()
    }

    @Test
    fun failedMigrationRollsBackAndKeepsVersionSixData() {
        helper.createDatabase(DATABASE_NAME, 6).apply {
            seedAllV6Families(this)
            close()
        }
        val failingMigration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE migration_6_7_failure_marker (id INTEGER NOT NULL PRIMARY KEY)")
                db.execSQL(
                    "UPDATE objectives SET fullName = 'Nombre que debe revertirse' " +
                        "WHERE id = '${V5HistoricalFixture.OBJECTIVE_ID}'",
                )
                error("Fallo ficticio de migración 6→7")
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
            assertEquals(6, database.version)
            assertRawV6FamiliesPreserved(database)
            assertFalse(rawTableExists(database, "migration_6_7_failure_marker"))
            assertFalse(rawTableExists(database, "work_places"))
        }
    }

    @Test
    fun missingMigrationPathFailsWithoutDeletingVersionSixData() {
        helper.createDatabase(DATABASE_NAME, 6).apply {
            seedAllV6Families(this)
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
            assertEquals(6, database.version)
            assertRawV6FamiliesPreserved(database)
            V7_TABLES.forEach { table -> assertFalse(table, rawTableExists(database, table)) }
        }
    }

    private fun seedAllV6Families(database: SupportSQLiteDatabase) {
        V5HistoricalFixture.seedAllFamilies(database)
        database.execSQL(
            """INSERT INTO work_configuration_roots (
                timelineId, singletonSlot, origin
            ) VALUES (
                '$TIMELINE_ID', 1, 'NEW_V2'
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO per_period_hours_definitions (
                id, timelineId, periodKind, weeklyFirstDayIso, cycleAnchorDate, cycleLengthDays
            ) VALUES (
                '$DEFINITION_ID', '$TIMELINE_ID', 'MONTHLY', NULL, NULL, NULL
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO work_configuration_revisions (
                id, timelineId, effectiveFrom, sector, availabilityLabel,
                hoursReferenceKind, periodKind, weeklyFirstDayIso, cycleAnchorDate,
                cycleLengthDays, requiredMinutes, perPeriodDefinitionId
            ) VALUES (
                '$CONFIGURATION_REVISION_ID', '$TIMELINE_ID', '2026-08-01',
                'PRIVATE_SECURITY', 'Disponibilidad ficticia', 'PER_PERIOD',
                'MONTHLY', NULL, NULL, NULL, NULL, '$DEFINITION_ID'
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO per_period_hours_values (
                id, definitionId, windowStartInclusive, windowEndExclusive, requiredMinutes
            ) VALUES (
                '$VALUE_ID', '$DEFINITION_ID', '2026-08-01', '2026-09-01', 10200
            )""".trimIndent(),
        )
    }

    private fun insertValidV7CatalogAndSnapshot(database: SupportSQLiteDatabase) {
        database.execSQL(
            """INSERT INTO work_places (
                id, timelineId, sector, objectiveId, isActive,
                createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                '$WORK_PLACE_ID', '$TIMELINE_ID', 'PRIVATE_SECURITY',
                '${V5HistoricalFixture.OBJECTIVE_ID}', 1, 1700000100000, 1700000101000
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO work_types (
                id, timelineId, sector, name, normalizedNameKey, behavior,
                isActive, createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                '$WORK_TYPE_ID', '$TIMELINE_ID', 'PRIVATE_SECURITY', 'Guardia habitual',
                'GUARDIA HABITUAL', 'ACTIVE_WORK', 1, 1700000102000, 1700000103000
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO work_templates (
                id, timelineId, sector, workPlaceId, objectiveId, workTypeId,
                startTime, endTime, colorArgb, isActive, legacyScheduleCombinationId,
                createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                '$WORK_TEMPLATE_ID', '$TIMELINE_ID', 'PRIVATE_SECURITY', '$WORK_PLACE_ID',
                '${V5HistoricalFixture.OBJECTIVE_ID}', '$WORK_TYPE_ID', '19:00', '07:00',
                -13408615, 1, '${V5HistoricalFixture.SCHEDULE_ID}', 1700000104000, 1700000105000
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO workplace_rule_revisions (
                id, timelineId, sector, workPlaceId, objectiveId, effectiveFrom,
                nightRuleCode, nightStartTime, nightEndTime, nightDifferentTreatment,
                nightShowDedicatedSummary, weekendRuleCode, weekendDifferentTreatment,
                weekendShowDedicatedSummary, holidayDifferentTreatment,
                holidayShowDedicatedSummary, createdAtEpochMillis
            ) VALUES (
                '$WORKPLACE_RULE_ID', '$TIMELINE_ID', 'PRIVATE_SECURITY', '$WORK_PLACE_ID',
                '${V5HistoricalFixture.OBJECTIVE_ID}', '2026-08-01', 'DEFINED', '21:00',
                '06:00', 1, 1, 'SATURDAY_AND_SUNDAY', 1, 1, 1, 1, 1700000106000
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO shift_work_snapshots (
                shiftId, timelineId, sector, configurationRevisionId, workPlaceId,
                objectiveId, templateId, workTypeId, workTypeNameSnapshot,
                workTypeBehaviorSnapshot
            ) VALUES (
                '${V5HistoricalFixture.PRIMARY_SHIFT_ID}', '$TIMELINE_ID', 'PRIVATE_SECURITY',
                '$CONFIGURATION_REVISION_ID', '$WORK_PLACE_ID',
                '${V5HistoricalFixture.OBJECTIVE_ID}', '$WORK_TEMPLATE_ID', '$WORK_TYPE_ID',
                'Guardia habitual', 'ACTIVE_WORK'
            )""".trimIndent(),
        )
    }

    private fun assertV6ConfigurationPreserved(database: SupportSQLiteDatabase) {
        database.query(
            "SELECT timelineId, singletonSlot, origin FROM work_configuration_roots",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(TIMELINE_ID, cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("NEW_V2", cursor.getString(2))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            """SELECT periodKind, weeklyFirstDayIso, cycleAnchorDate, cycleLengthDays
                FROM per_period_hours_definitions WHERE id = '$DEFINITION_ID'""".trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("MONTHLY", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            """SELECT effectiveFrom, sector, availabilityLabel, hoursReferenceKind,
                periodKind, perPeriodDefinitionId
                FROM work_configuration_revisions WHERE id = '$CONFIGURATION_REVISION_ID'""".trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-08-01", cursor.getString(0))
            assertEquals("PRIVATE_SECURITY", cursor.getString(1))
            assertEquals("Disponibilidad ficticia", cursor.getString(2))
            assertEquals("PER_PERIOD", cursor.getString(3))
            assertEquals("MONTHLY", cursor.getString(4))
            assertEquals(DEFINITION_ID, cursor.getString(5))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            """SELECT windowStartInclusive, windowEndExclusive, requiredMinutes
                FROM per_period_hours_values WHERE id = '$VALUE_ID'""".trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-08-01", cursor.getString(0))
            assertEquals("2026-09-01", cursor.getString(1))
            assertEquals(10200, cursor.getInt(2))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun assertV7TablesEmpty(database: SupportSQLiteDatabase) {
        V7_TABLES.forEach { table -> assertEquals(table, 0, rowCount(database, table)) }
    }

    private fun assertV7IndexesAndForeignKeys(database: SupportSQLiteDatabase) {
        listOf(
            IndexExpectation("work_places", "index_work_places_timelineId_sector_objectiveId", true),
            IndexExpectation("work_places", "index_work_places_id_timelineId_sector_objectiveId", true),
            IndexExpectation("work_places", "index_work_places_objectiveId", false),
            IndexExpectation("work_types", "index_work_types_timelineId_sector_normalizedNameKey", true),
            IndexExpectation("work_types", "index_work_types_id_timelineId_sector", true),
            IndexExpectation("work_templates", "index_work_templates_timelineId", false),
            IndexExpectation(
                "work_templates",
                "index_work_templates_workPlaceId_timelineId_sector_objectiveId",
                false,
            ),
            IndexExpectation(
                "work_templates",
                "index_work_templates_workTypeId_timelineId_sector",
                false,
            ),
            IndexExpectation("work_templates", "index_work_templates_legacyScheduleCombinationId", false),
            IndexExpectation(
                "work_templates",
                "index_work_templates_workPlaceId_workTypeId_startTime_endTime",
                true,
            ),
            IndexExpectation(
                "work_templates",
                "index_work_templates_id_timelineId_sector_workPlaceId_objectiveId_workTypeId",
                true,
            ),
            IndexExpectation(
                "workplace_rule_revisions",
                "index_workplace_rule_revisions_workPlaceId_effectiveFrom",
                true,
            ),
            IndexExpectation("workplace_rule_revisions", "index_workplace_rule_revisions_timelineId", false),
            IndexExpectation(
                "workplace_rule_revisions",
                "index_workplace_rule_revisions_workPlaceId_timelineId_sector_objectiveId",
                false,
            ),
            IndexExpectation("shift_work_snapshots", "index_shift_work_snapshots_timelineId", false),
            IndexExpectation(
                "shift_work_snapshots",
                "index_shift_work_snapshots_configurationRevisionId",
                false,
            ),
            IndexExpectation(
                "shift_work_snapshots",
                "index_shift_work_snapshots_workPlaceId_timelineId_sector_objectiveId",
                false,
            ),
            IndexExpectation(
                "shift_work_snapshots",
                "index_shift_work_snapshots_workTypeId_timelineId_sector",
                false,
            ),
            IndexExpectation(
                "shift_work_snapshots",
                "index_shift_work_snapshots_templateId_timelineId_sector_workPlaceId_objectiveId_workTypeId",
                false,
            ),
        ).forEach { expectation -> assertIndex(database, expectation) }

        assertForeignKey(
            database,
            "work_places",
            "work_configuration_roots",
            listOf("timelineId"),
            listOf("timelineId"),
            "RESTRICT",
        )
        assertForeignKey(
            database,
            "work_places",
            "objectives",
            listOf("objectiveId"),
            listOf("id"),
            "RESTRICT",
        )
        assertForeignKey(
            database,
            "work_types",
            "work_configuration_roots",
            listOf("timelineId"),
            listOf("timelineId"),
            "RESTRICT",
        )
        assertForeignKey(
            database,
            "work_templates",
            "work_configuration_roots",
            listOf("timelineId"),
            listOf("timelineId"),
            "RESTRICT",
        )
        assertForeignKey(
            database,
            "work_templates",
            "work_places",
            listOf("workPlaceId", "timelineId", "sector", "objectiveId"),
            listOf("id", "timelineId", "sector", "objectiveId"),
            "RESTRICT",
        )
        assertForeignKey(
            database,
            "work_templates",
            "work_types",
            listOf("workTypeId", "timelineId", "sector"),
            listOf("id", "timelineId", "sector"),
            "RESTRICT",
        )
        assertForeignKey(
            database,
            "work_templates",
            "schedule_combinations",
            listOf("legacyScheduleCombinationId"),
            listOf("id"),
            "SET NULL",
        )
        assertForeignKey(
            database,
            "workplace_rule_revisions",
            "work_configuration_roots",
            listOf("timelineId"),
            listOf("timelineId"),
            "RESTRICT",
        )
        assertForeignKey(
            database,
            "workplace_rule_revisions",
            "work_places",
            listOf("workPlaceId", "timelineId", "sector", "objectiveId"),
            listOf("id", "timelineId", "sector", "objectiveId"),
            "RESTRICT",
        )
        assertForeignKey(
            database,
            "shift_work_snapshots",
            "shifts",
            listOf("shiftId"),
            listOf("id"),
            "CASCADE",
        )
        assertForeignKey(
            database,
            "shift_work_snapshots",
            "work_configuration_roots",
            listOf("timelineId"),
            listOf("timelineId"),
            "RESTRICT",
        )
        assertForeignKey(
            database,
            "shift_work_snapshots",
            "work_configuration_revisions",
            listOf("configurationRevisionId"),
            listOf("id"),
            "RESTRICT",
        )
        assertForeignKey(
            database,
            "shift_work_snapshots",
            "work_places",
            listOf("workPlaceId", "timelineId", "sector", "objectiveId"),
            listOf("id", "timelineId", "sector", "objectiveId"),
            "RESTRICT",
        )
        assertForeignKey(
            database,
            "shift_work_snapshots",
            "work_types",
            listOf("workTypeId", "timelineId", "sector"),
            listOf("id", "timelineId", "sector"),
            "RESTRICT",
        )
        assertForeignKey(
            database,
            "shift_work_snapshots",
            "work_templates",
            listOf("templateId", "timelineId", "sector", "workPlaceId", "objectiveId", "workTypeId"),
            listOf("id", "timelineId", "sector", "workPlaceId", "objectiveId", "workTypeId"),
            "RESTRICT",
        )
    }

    private fun assertIndex(
        database: SupportSQLiteDatabase,
        expectation: IndexExpectation,
    ) {
        database.query("PRAGMA index_list(`${expectation.table}`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val uniqueColumn = cursor.getColumnIndexOrThrow("unique")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == expectation.name) {
                    assertEquals(
                        expectation.name,
                        if (expectation.unique) 1 else 0,
                        cursor.getInt(uniqueColumn),
                    )
                    found = true
                }
            }
            assertTrue("No se encontró el índice ${expectation.name}", found)
        }
        val expectedColumns = expectation.name
            .removePrefix("index_${expectation.table}_")
            .split('_')
        val actualColumns = buildList {
            database.query("PRAGMA index_info(`${expectation.name}`)").use { cursor ->
                val sequenceColumn = cursor.getColumnIndexOrThrow("seqno")
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                val indexed = mutableListOf<Pair<Int, String>>()
                while (cursor.moveToNext()) {
                    indexed += cursor.getInt(sequenceColumn) to cursor.getString(nameColumn)
                }
                addAll(indexed.sortedBy { it.first }.map { it.second })
            }
        }
        assertEquals(expectation.name, expectedColumns, actualColumns)
    }

    private fun assertForeignKey(
        database: SupportSQLiteDatabase,
        table: String,
        parentTable: String,
        childColumns: List<String>,
        parentColumns: List<String>,
        onDelete: String,
    ) {
        val rows = buildList {
            database.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow("id")
                val sequenceColumn = cursor.getColumnIndexOrThrow("seq")
                val tableColumn = cursor.getColumnIndexOrThrow("table")
                val fromColumn = cursor.getColumnIndexOrThrow("from")
                val toColumn = cursor.getColumnIndexOrThrow("to")
                val deleteColumn = cursor.getColumnIndexOrThrow("on_delete")
                while (cursor.moveToNext()) {
                    add(
                        ForeignKeyRow(
                            id = cursor.getInt(idColumn),
                            sequence = cursor.getInt(sequenceColumn),
                            parentTable = cursor.getString(tableColumn),
                            childColumn = cursor.getString(fromColumn),
                            parentColumn = cursor.getString(toColumn),
                            onDelete = cursor.getString(deleteColumn),
                        ),
                    )
                }
            }
        }
        val found = rows.groupBy(ForeignKeyRow::id).values.any { group ->
            val ordered = group.sortedBy(ForeignKeyRow::sequence)
            ordered.first().parentTable == parentTable &&
                ordered.first().onDelete == onDelete &&
                ordered.map(ForeignKeyRow::childColumn) == childColumns &&
                ordered.map(ForeignKeyRow::parentColumn) == parentColumns
        }
        assertTrue(
            "No se encontró la FK $table(${childColumns.joinToString()}) → " +
                "$parentTable(${parentColumns.joinToString()}) $onDelete",
            found,
        )
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
        scalarInt(database, "SELECT COUNT(*) FROM `$table`")

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

    private fun assertRawV6FamiliesPreserved(database: SQLiteDatabase) {
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
            "work_configuration_roots" to 1,
            "per_period_hours_definitions" to 1,
            "work_configuration_revisions" to 1,
            "per_period_hours_values" to 1,
        ).forEach { (table, expected) ->
            assertEquals(table, expected, rawTableCount(database, table))
        }
        database.rawQuery(
            "SELECT fullName, abbreviation FROM objectives WHERE id = ?",
            arrayOf(V5HistoricalFixture.OBJECTIVE_ID),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Objetivo histórico ficticio", cursor.getString(0))
            assertEquals("QA", cursor.getString(1))
            assertFalse(cursor.moveToNext())
        }
        database.rawQuery(
            "SELECT origin FROM work_configuration_roots WHERE timelineId = ?",
            arrayOf(TIMELINE_ID),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("NEW_V2", cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun rawTableCount(database: SQLiteDatabase, table: String): Int =
        database.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun rawTableExists(database: SQLiteDatabase, table: String): Boolean =
        database.rawQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0) == 1
        }

    private data class IndexExpectation(
        val table: String,
        val name: String,
        val unique: Boolean,
    )

    private data class ForeignKeyRow(
        val id: Int,
        val sequence: Int,
        val parentTable: String,
        val childColumn: String,
        val parentColumn: String,
        val onDelete: String,
    )

    private companion object {
        const val DATABASE_NAME = "migration-6-7-test.db"
        const val TIMELINE_ID = "00000000-0000-0000-0000-000000000701"
        const val DEFINITION_ID = "00000000-0000-0000-0000-000000000711"
        const val CONFIGURATION_REVISION_ID = "00000000-0000-0000-0000-000000000721"
        const val VALUE_ID = "00000000-0000-0000-0000-000000000731"
        const val WORK_PLACE_ID = "00000000-0000-0000-0000-000000000741"
        const val WORK_TYPE_ID = "00000000-0000-0000-0000-000000000751"
        const val WORK_TEMPLATE_ID = "00000000-0000-0000-0000-000000000761"
        const val WORKPLACE_RULE_ID = "00000000-0000-0000-0000-000000000771"
        const val ORPHAN_ID = "00000000-0000-0000-0000-000000000798"
        const val DUPLICATE_ID = "00000000-0000-0000-0000-000000000799"

        val V7_TABLES = listOf(
            "work_places",
            "work_types",
            "work_templates",
            "workplace_rule_revisions",
            "shift_work_snapshots",
        )
    }
}
