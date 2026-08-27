package com.blackatsystems.miguardia.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V2DatabaseMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MiGuardiaV2Database::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationOneToTwoPreservesEveryV2TableAndStartsRecurringTablesEmpty() {
        helper.createDatabase(DB, 1).apply {
            seedEveryVersionOneTable()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB,
            2,
            true,
            MiGuardiaV2Database.MIGRATION_1_2,
        )

        VERSION_ONE_TABLES.forEach { table ->
            assertEquals("La migración debe preservar $table", 1, migrated.scalar("SELECT COUNT(*) FROM `$table`"))
        }
        NEW_TABLES.forEach { table ->
            assertEquals("La tabla $table debe comenzar vacía", 0, migrated.scalar("SELECT COUNT(*) FROM `$table`"))
        }
        assertEquals("shift-1", migrated.string("SELECT shiftId FROM shift_work_snapshots"))
        assertEquals("Hospital migrado", migrated.string("SELECT objectiveNameSnapshot FROM shifts"))
        migrated.query("PRAGMA integrity_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
        migrated.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        migrated.close()
    }

    @Test
    fun migrationTwoToThreePreservesAllTwentyTwoPopulatedTablesAndCreatesExactEmptySchema() {
        helper.createDatabase(DB_TWO_TO_THREE, 1).apply {
            seedEveryVersionOneTable()
            close()
        }
        helper.runMigrationsAndValidate(
            DB_TWO_TO_THREE,
            2,
            true,
            MiGuardiaV2Database.MIGRATION_1_2,
        ).apply {
            seedEveryVersionTwoRecurringTable()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_TWO_TO_THREE,
            3,
            true,
            MiGuardiaV2Database.MIGRATION_2_3,
        )

        VERSION_TWO_TABLES.forEach { table ->
            assertEquals("La migración debe preservar $table", 1, migrated.scalar("SELECT COUNT(*) FROM `$table`"))
        }
        VERSION_THREE_TABLES.forEach { table ->
            assertEquals("La tabla $table debe comenzar vacía", 0, migrated.scalar("SELECT COUNT(*) FROM `$table`"))
        }
        assertEquals(
            25,
            migrated.scalar(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' " +
                    "AND name != 'room_master_table'",
            ),
        )
        assertEquals(1, migrated.scalar("SELECT COUNT(*) FROM pragma_index_list('shift_work_snapshots') " +
            "WHERE name = 'index_shift_work_snapshots_shiftId_timelineId_sector' AND `unique` = 1"))
        assertForeignKey(migrated, "shift_actual_records", "shift_work_snapshots", "RESTRICT")
        assertForeignKey(migrated, "shift_extra_intervals", "shift_actual_records", "CASCADE")
        assertForeignKey(migrated, "shift_extra_intervals", "extra_work_classes", "RESTRICT")
        assertHealthy(migrated)
        migrated.close()
    }

    @Test
    fun migrationThreeToFourPreservesTwentyFiveTablesBackfillsReferenceStartsAndCreatesEmptyExtras() {
        helper.createDatabase(DB_THREE_TO_FOUR, 1).apply {
            seedEveryVersionOneTable()
            close()
        }
        helper.runMigrationsAndValidate(
            DB_THREE_TO_FOUR,
            3,
            true,
            MiGuardiaV2Database.MIGRATION_1_2,
            MiGuardiaV2Database.MIGRATION_2_3,
        ).apply {
            seedEveryVersionTwoRecurringTable()
            seedEveryVersionThreeTable()
            seedReferenceBackfillCases()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_THREE_TO_FOUR,
            4,
            true,
            MiGuardiaV2Database.MIGRATION_3_4,
        )

        (VERSION_TWO_TABLES + VERSION_THREE_TABLES).forEach { table ->
            assertTrue("La migración debe preservar $table", migrated.scalar("SELECT COUNT(*) FROM `$table`") > 0)
        }
        assertEquals(0, migrated.scalar("SELECT COUNT(*) FROM independent_extra_work_records"))
        assertEquals(
            26,
            migrated.scalar(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' " +
                    "AND name != 'room_master_table'",
            ),
        )
        assertEquals("2026-01-01", migrated.referenceStartedOn("configuration-1"))
        assertEquals("2026-01-01", migrated.referenceStartedOn("configuration-same-1"))
        assertEquals("2026-03-01", migrated.referenceStartedOn("configuration-fixed-1"))
        assertEquals("2026-03-01", migrated.referenceStartedOn("configuration-fixed-same"))
        assertEquals(null, migrated.referenceStartedOn("configuration-unknown-no-period"))
        assertEquals("2026-06-01", migrated.referenceStartedOn("configuration-unknown-monthly"))
        assertEquals("2026-07-01", migrated.referenceStartedOn("configuration-weekly-1"))
        assertEquals("2026-07-01", migrated.referenceStartedOn("configuration-weekly-same"))
        assertEquals("2026-09-01", migrated.referenceStartedOn("configuration-weekly-changed"))
        assertEquals("2026-10-01", migrated.referenceStartedOn("configuration-cycle-1"))
        assertEquals("2026-10-01", migrated.referenceStartedOn("configuration-cycle-same"))
        assertEquals("2026-12-01", migrated.referenceStartedOn("configuration-cycle-changed"))
        assertEquals(null, migrated.referenceStartedOn("configuration-not-used"))
        assertEquals("2027-02-01", migrated.referenceStartedOn("configuration-fixed-after-not-used"))
        assertEquals(null, migrated.referenceStartedOn("configuration-pending"))
        assertEquals("2027-04-01", migrated.referenceStartedOn("configuration-unknown-after-pending"))
        assertForeignKey(migrated, "independent_extra_work_records", "work_configuration_revisions", "RESTRICT")
        assertForeignKey(migrated, "independent_extra_work_records", "work_places", "RESTRICT")
        assertForeignKey(migrated, "independent_extra_work_records", "work_templates", "RESTRICT")
        assertForeignKey(migrated, "independent_extra_work_records", "extra_work_classes", "RESTRICT")
        assertHealthy(migrated)
        migrated.close()

        val reopened = helper.runMigrationsAndValidate(DB_THREE_TO_FOUR, 4, true)
        assertEquals("2026-01-01", reopened.referenceStartedOn("configuration-same-1"))
        assertEquals("2026-03-01", reopened.referenceStartedOn("configuration-fixed-same"))
        assertHealthy(reopened)
        reopened.close()
    }

    @Test
    fun migrationFourToFivePreservesAllTwentySixPopulatedTablesAndCreatesExactEmptyAvailability() {
        helper.createDatabase(DB_FOUR_TO_FIVE, 1).apply {
            seedEveryVersionOneTable()
            close()
        }
        helper.runMigrationsAndValidate(
            DB_FOUR_TO_FIVE,
            4,
            true,
            MiGuardiaV2Database.MIGRATION_1_2,
            MiGuardiaV2Database.MIGRATION_2_3,
            MiGuardiaV2Database.MIGRATION_3_4,
        ).apply {
            seedEveryVersionTwoRecurringTable()
            seedEveryVersionThreeTable()
            seedEveryVersionFourTable()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_FOUR_TO_FIVE,
            5,
            true,
            MiGuardiaV2Database.MIGRATION_4_5,
        )

        (VERSION_TWO_TABLES + VERSION_THREE_TABLES + VERSION_FOUR_TABLES).forEach { table ->
            assertTrue("La migración debe preservar $table", migrated.scalar("SELECT COUNT(*) FROM `$table`") > 0)
        }
        assertEquals(0, migrated.scalar("SELECT COUNT(*) FROM availability_windows"))
        assertEquals(
            27,
            migrated.scalar(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' " +
                    "AND name != 'room_master_table'",
            ),
        )
        assertForeignKey(migrated, "availability_windows", "work_configuration_roots", "RESTRICT")
        assertForeignKey(migrated, "availability_windows", "work_configuration_revisions", "RESTRICT")
        assertHealthy(migrated)
        migrated.close()
    }

    @Test
    fun migrationChainOneToTwoToThreeToFourToFivePreservesVersionOneData() {
        helper.createDatabase(DB_CHAIN, 1).apply {
            seedEveryVersionOneTable()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_CHAIN,
            5,
            true,
            MiGuardiaV2Database.MIGRATION_1_2,
            MiGuardiaV2Database.MIGRATION_2_3,
            MiGuardiaV2Database.MIGRATION_3_4,
            MiGuardiaV2Database.MIGRATION_4_5,
        )

        VERSION_ONE_TABLES.forEach { table ->
            assertEquals("La cadena debe preservar $table", 1, migrated.scalar("SELECT COUNT(*) FROM `$table`"))
        }
        (NEW_TABLES + VERSION_THREE_TABLES + VERSION_FOUR_TABLES + VERSION_FIVE_TABLES).forEach { table ->
            assertEquals("La cadena debe iniciar $table vacía", 0, migrated.scalar("SELECT COUNT(*) FROM `$table`"))
        }
        assertHealthy(migrated)
        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.seedEveryVersionOneTable() {
        execSQL("INSERT INTO work_configuration_roots VALUES ('timeline-1', 1)")
        execSQL("INSERT INTO per_period_hours_definitions VALUES ('definition-1', 'timeline-1', 'MONTHLY', NULL, NULL, NULL)")
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-1', 'timeline-1', '2026-01-01', 'PRIVATE_SECURITY', NULL,
                'PER_PERIOD', NULL, NULL, NULL, NULL, NULL, 'definition-1'
            )""",
        )
        execSQL(
            "INSERT INTO per_period_hours_values VALUES " +
                "('value-1', 'definition-1', '2026-08-01', '2026-09-01', 12240)",
        )
        execSQL(
            "INSERT INTO objectives VALUES " +
                "('objective-1', 'Hospital migrado', 'HMI', 'Dirección ficticia', NULL, 1, 1, 1)",
        )
        execSQL(
            "INSERT INTO work_places VALUES " +
                "('place-1', 'timeline-1', 'PRIVATE_SECURITY', 'objective-1', 1, 1, 1)",
        )
        execSQL(
            "INSERT INTO work_types VALUES " +
                "('type-1', 'timeline-1', 'PRIVATE_SECURITY', 'Jornada habitual', " +
                "'jornada habitual', 'ACTIVE_WORK', 1, 1, 1)",
        )
        execSQL(
            "INSERT INTO work_templates VALUES " +
                "('template-1', 'timeline-1', 'PRIVATE_SECURITY', 'place-1', 'objective-1', " +
                "'type-1', '08:00', '16:00', -13408615, 1, 1, 1)",
        )
        execSQL(
            """INSERT INTO workplace_rule_revisions VALUES (
                'rule-1', 'timeline-1', 'PRIVATE_SECURITY', 'place-1', 'objective-1', '2026-01-01',
                'DISABLED', NULL, NULL, NULL, NULL, 'NONE', NULL, NULL, 0, 0, 1
            )""",
        )
        execSQL(
            """INSERT INTO shifts VALUES (
                'shift-1', 1787472000000, 1787500800000, 'America/Argentina/Cordoba', '2026-08-23',
                'Hospital migrado', 'HMI', 'Dirección ficticia', '08:00', '16:00', -13408615,
                NULL, 'PLANNED', 'objective-1', 1, 1
            )""",
        )
        execSQL(
            """INSERT INTO shift_work_snapshots VALUES (
                'shift-1', 'timeline-1', 'PRIVATE_SECURITY', 'configuration-1', 'place-1',
                'objective-1', 'template-1', 'type-1', 'Jornada habitual', 'ACTIVE_WORK'
            )""",
        )
        execSQL("INSERT INTO explicit_day_statuses VALUES ('2026-08-24', 'DAY_OFF')")
        execSQL("INSERT INTO medical_leaves VALUES ('medical-1', '2026-08-25', '2026-08-26', NULL, 1, 1)")
        execSQL("INSERT INTO holidays VALUES ('holiday-1', '2026-08-27', 'Feriado ficticio', 1, 1)")
        execSQL("INSERT INTO shift_notes VALUES ('note-1', 'shift-1', 'Nota ficticia', 1, 1)")
        execSQL("INSERT INTO vacations VALUES ('vacation-1', '2026-09-01', '2026-09-02', 1, 1)")
        execSQL(
            "INSERT INTO schedule_photos VALUES " +
                "('photo-1', '2026-08', 'objective-1', 'Hospital migrado', 'HMI', " +
                "'qa/photo-1', 'image/png', 10, 1, 1, 1, 1)",
        )
        execSQL("INSERT INTO shift_notification_configs VALUES ('shift-1')")
        execSQL("INSERT INTO shift_notification_reminders VALUES ('shift-1', 30)")
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.seedEveryVersionTwoRecurringTable() {
        execSQL("INSERT INTO recurring_plans VALUES ('plan-1', 'timeline-1', 'PRIVATE_SECURITY', 1)")
        execSQL(
            """INSERT INTO recurring_plan_revisions (
                id, planId, revisionNumber, effectiveFrom, kind, endDateInclusive, patternKind,
                weekdaysMask, intervalCount, monthlyOrdinal, monthlyDayOfWeek, templateId,
                workPlaceId, objectiveId, workTypeId, objectiveNameSnapshot,
                objectiveAbbreviationSnapshot, objectiveAddressSnapshot, workTypeNameSnapshot,
                workTypeBehaviorSnapshot, startTimeSnapshot, endTimeSnapshot, colorArgbSnapshot,
                positionSnapshot, zoneId, createdAtEpochMillis
            ) VALUES (
                'revision-recurring-1', 'plan-1', 1, '2026-08-23', 'ACTIVE', '2026-08-23',
                'WEEKDAYS', 64, NULL, NULL, NULL, 'template-1', 'place-1', 'objective-1',
                'type-1', 'Hospital migrado', 'HMI', 'Dirección ficticia', 'Jornada habitual',
                'ACTIVE_WORK', '08:00', '16:00', -13408615, NULL, 'America/Argentina/Cordoba', 1
            )""".trimIndent(),
        )
        execSQL(
            "INSERT INTO recurring_occurrences VALUES " +
                "('plan-1', '2026-08-23', 'revision-recurring-1', 'shift-1', 'AUTOMATIC', 1, 1)",
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.seedEveryVersionThreeTable() {
        execSQL(
            "INSERT INTO extra_work_classes VALUES " +
                "('class-1', 'timeline-1', 'PRIVATE_SECURITY', 'Hora extra', 'HORA EXTRA', 1, 1, 1, 1, 1)",
        )
        execSQL(
            "INSERT INTO shift_actual_records VALUES " +
                "('shift-1', 'timeline-1', 'PRIVATE_SECURITY', 1787472000000, 1787500800000, " +
                "'Horario confirmado', NULL, 1, 1)",
        )
        execSQL(
            "INSERT INTO shift_extra_intervals VALUES " +
                "('interval-1', 'shift-1', 'timeline-1', 'PRIVATE_SECURITY', 'class-1', " +
                "1787497200000, 1787500800000, 'Hora extra', 1, 1, 1, 1)",
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.seedEveryVersionFourTable() {
        execSQL(
            """INSERT INTO independent_extra_work_records VALUES (
                'independent-1', 'timeline-1', 'PRIVATE_SECURITY', 'configuration-1',
                'place-1', 'objective-1', 'type-1', 'template-1', 'class-1',
                '2026-08-23', 'America/Argentina/Cordoba', 1787500800000, 1787504400000,
                'Hospital migrado', 'HMI', 'Dirección ficticia', 'Jornada habitual',
                'ACTIVE_WORK', -13408615, NULL, 'Hora extra', 1, 1, 1, 1
            )""".trimIndent(),
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.seedReferenceBackfillCases() {
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-same-1', 'timeline-1', '2026-02-01', 'PRIVATE_SECURITY', 'PASSIVE_GUARD',
                'PER_PERIOD', NULL, NULL, NULL, NULL, NULL, 'definition-1'
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-fixed-1', 'timeline-1', '2026-03-01', 'PRIVATE_SECURITY', NULL,
                'FIXED', 'MONTHLY', NULL, NULL, NULL, 9600, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-fixed-same', 'timeline-1', '2026-04-01', 'PRIVATE_SECURITY', 'PASSIVE_GUARD',
                'FIXED', 'MONTHLY', NULL, NULL, NULL, 9600, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-unknown-no-period', 'timeline-1', '2026-05-01', 'PRIVATE_SECURITY', NULL,
                'UNKNOWN', NULL, NULL, NULL, NULL, NULL, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-unknown-monthly', 'timeline-1', '2026-06-01', 'PRIVATE_SECURITY', NULL,
                'UNKNOWN', 'MONTHLY', NULL, NULL, NULL, NULL, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-weekly-1', 'timeline-1', '2026-07-01', 'PRIVATE_SECURITY', NULL,
                'FIXED', 'WEEKLY', 1, NULL, NULL, 2400, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-weekly-same', 'timeline-1', '2026-08-01', 'PRIVATE_SECURITY', 'PASSIVE_GUARD',
                'FIXED', 'WEEKLY', 1, NULL, NULL, 2400, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-weekly-changed', 'timeline-1', '2026-09-01', 'PRIVATE_SECURITY', NULL,
                'FIXED', 'WEEKLY', 4, NULL, NULL, 2400, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-cycle-1', 'timeline-1', '2026-10-01', 'PRIVATE_SECURITY', NULL,
                'UNKNOWN', 'CYCLE', NULL, '2026-09-15', 21, NULL, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-cycle-same', 'timeline-1', '2026-11-01', 'PRIVATE_SECURITY', 'PASSIVE_GUARD',
                'UNKNOWN', 'CYCLE', NULL, '2026-09-15', 21, NULL, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-cycle-changed', 'timeline-1', '2026-12-01', 'PRIVATE_SECURITY', NULL,
                'UNKNOWN', 'CYCLE', NULL, '2026-09-15', 28, NULL, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-not-used', 'timeline-1', '2027-01-01', 'PRIVATE_SECURITY', NULL,
                'NOT_USED', NULL, NULL, NULL, NULL, NULL, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-fixed-after-not-used', 'timeline-1', '2027-02-01', 'PRIVATE_SECURITY', NULL,
                'FIXED', 'MONTHLY', NULL, NULL, NULL, 9600, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-pending', 'timeline-1', '2027-03-01', 'PRIVATE_SECURITY', NULL,
                'PENDING_SETUP', NULL, NULL, NULL, NULL, NULL, NULL
            )""",
        )
        execSQL(
            """INSERT INTO work_configuration_revisions VALUES (
                'configuration-unknown-after-pending', 'timeline-1', '2027-04-01', 'PRIVATE_SECURITY', NULL,
                'UNKNOWN', 'MONTHLY', NULL, NULL, NULL, NULL, NULL
            )""",
        )
    }

    private fun assertHealthy(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.query("PRAGMA integrity_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
        database.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
    }

    private fun assertForeignKey(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        childTable: String,
        parentTable: String,
        expectedDeleteAction: String,
    ) {
        database.query("PRAGMA foreign_key_list(`$childTable`)").use { cursor ->
            val tableColumn = cursor.getColumnIndexOrThrow("table")
            val deleteColumn = cursor.getColumnIndexOrThrow("on_delete")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(tableColumn) == parentTable) {
                    assertEquals(expectedDeleteAction, cursor.getString(deleteColumn))
                    found = true
                }
            }
            assertTrue("Falta la FK $childTable → $parentTable", found)
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalar(sql: String): Int = query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.string(sql: String): String = query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.referenceStartedOn(id: String): String? =
        query(
            "SELECT hoursReferenceStartedOn FROM work_configuration_revisions WHERE id = ?",
            arrayOf(id),
        ).use { cursor ->
            check(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getString(0)
        }

    private companion object {
        const val DB = "v2-migration-1-2-test.db"
        const val DB_TWO_TO_THREE = "v2-migration-2-3-test.db"
        const val DB_CHAIN = "v2-migration-1-3-test.db"
        const val DB_THREE_TO_FOUR = "v2-migration-3-4-test.db"
        const val DB_FOUR_TO_FIVE = "v2-migration-4-5-test.db"
        val VERSION_ONE_TABLES = listOf(
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
        )
        val NEW_TABLES = listOf(
            "recurring_plans",
            "recurring_plan_revisions",
            "recurring_occurrences",
        )
        val VERSION_TWO_TABLES = VERSION_ONE_TABLES + NEW_TABLES
        val VERSION_THREE_TABLES = listOf(
            "extra_work_classes",
            "shift_actual_records",
            "shift_extra_intervals",
        )
        val VERSION_FOUR_TABLES = listOf("independent_extra_work_records")
        val VERSION_FIVE_TABLES = listOf("availability_windows")
    }
}
