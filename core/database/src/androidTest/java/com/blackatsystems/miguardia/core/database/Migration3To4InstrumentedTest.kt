package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4InstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MiGuardiaDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())
    @After fun cleanUp() { context.deleteDatabase(DB) }

    @Test fun migrationCreatesEmptyPhotoTableAndPreservesAllTenFamilies() {
        helper.createDatabase(DB, 3).apply {
            execSQL("INSERT INTO objectives VALUES ('o1','Objetivo QA','QA',NULL,NULL,1,1,1)")
            execSQL("INSERT INTO schedule_combinations VALUES ('c1','o1','08:00','16:00',-1,1,1,1)")
            execSQL("INSERT INTO shifts VALUES ('s1',1,2,'America/Argentina/Cordoba','2026-08-13','Objetivo QA','QA',NULL,'08:00','16:00',-1,NULL,'PLANNED','o1','c1',1,1)")
            execSQL("INSERT INTO explicit_day_statuses VALUES ('2026-08-14','DAY_OFF')")
            execSQL("INSERT INTO medical_leaves VALUES ('m1','2026-08-15','2026-08-16',NULL,1,1)")
            execSQL("INSERT INTO holidays VALUES ('h1','2026-08-17','Feriado QA',1,1)")
            execSQL("INSERT INTO shift_notes VALUES ('n1','s1','Nota QA',1,1)")
            execSQL("INSERT INTO shift_novelties VALUES ('n2','s1','OTHER','Novedad QA',NULL,1,1)")
            execSQL(
                """INSERT INTO formal_shift_changes (
                    id, shiftId, scheduleChanged, objectiveChanged, description,
                    original_startEpochMillis, original_endEpochMillis, original_zoneId,
                    original_localStartDate, original_objectiveName, original_objectiveAbbreviation,
                    original_objectiveAddress, original_startTime, original_endTime, original_colorArgb,
                    original_position, original_status, original_sourceObjectiveId,
                    original_sourceScheduleCombinationId,
                    final_startEpochMillis, final_endEpochMillis, final_zoneId,
                    final_localStartDate, final_objectiveName, final_objectiveAbbreviation,
                    final_objectiveAddress, final_startTime, final_endTime, final_colorArgb,
                    final_position, final_status, final_sourceObjectiveId,
                    final_sourceScheduleCombinationId, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    'f1','s1',1,0,'Cambio QA',
                    1,2,'America/Argentina/Cordoba','2026-08-13','Objetivo QA','QA',
                    NULL,'08:00','16:00',-1,NULL,'PLANNED','o1','c1',
                    1,3,'America/Argentina/Cordoba','2026-08-13','Objetivo QA','QA',
                    NULL,'08:00','17:00',-1,NULL,'PLANNED','o1','c1',1,1
                )""",
            )
            execSQL("INSERT INTO vacations VALUES ('v1','2026-08-01','2026-08-02',1,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 4, true, MIGRATION_3_4)
        listOf(
            "objectives", "schedule_combinations", "shifts", "explicit_day_statuses",
            "medical_leaves", "holidays", "shift_notes", "shift_novelties",
            "formal_shift_changes", "vacations",
        ).forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(table, 1, it.getInt(0)) }
        }
        db.query("SELECT COUNT(*) FROM schedule_photos").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
    }

    @Test fun completeChainFromV1ToV4PreservesOriginalData() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO objectives VALUES ('o1','Objetivo QA','QA',NULL,NULL,1,1,1)")
            execSQL("INSERT INTO schedule_combinations VALUES ('c1','o1','08:00','16:00',-1,1,1,1)")
            execSQL("INSERT INTO shifts VALUES ('s1',1,2,'America/Argentina/Cordoba','2026-08-13','Objetivo QA','QA',NULL,'08:00','16:00',-1,NULL,'PLANNED','o1','c1',1,1)")
            execSQL("INSERT INTO explicit_day_statuses VALUES ('2026-08-14','DAY_OFF')")
            execSQL("INSERT INTO medical_leaves VALUES ('m1','2026-08-15','2026-08-16',NULL,1,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        listOf("objectives", "schedule_combinations", "shifts", "explicit_day_statuses", "medical_leaves").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        }
        db.query("SELECT COUNT(*) FROM schedule_photos").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
    }
    private companion object { const val DB = "migration-3-4-test.db" }
}
