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

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalar(sql: String): Int = query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.string(sql: String): String = query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }

    private companion object {
        const val DB = "v2-migration-1-2-test.db"
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
    }
}
