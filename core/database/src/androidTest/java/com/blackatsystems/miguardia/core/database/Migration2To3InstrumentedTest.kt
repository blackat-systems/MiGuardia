package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2To3InstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MiGuardiaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After fun cleanUp() {
        context.deleteDatabase(DB_V2)
        context.deleteDatabase(DB_CHAIN)
    }

    @Test
    @Throws(IOException::class)
    fun migrationFromV2PreservesAllNineFamiliesAndCreatesEmptyVacations() {
        helper.createDatabase(DB_V2, 2).apply {
            insertV1Families(this)
            execSQL("INSERT INTO holidays VALUES ('h1','2026-08-17','Feriado ficticio',1,1)")
            execSQL("INSERT INTO shift_notes VALUES ('n1','s1','Nota ficticia',1,1)")
            execSQL("INSERT INTO shift_novelties VALUES ('v1','s1','OTHER','Novedad ficticia',NULL,1,1)")
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
                    'f1','s1',1,0,'Cambio ficticio',
                    1,2,'America/Argentina/Cordoba','2026-08-13','Objetivo Ficticio','OBJ',
                    NULL,'19:00','07:00',-1,NULL,'PLANNED','o1','c1',
                    1,3,'America/Argentina/Cordoba','2026-08-13','Objetivo Ficticio','OBJ',
                    NULL,'19:00','08:00',-1,NULL,'PLANNED','o1','c1',1,1
                )""",
            )
            close()
        }

        helper.runMigrationsAndValidate(DB_V2, 3, true, MIGRATION_2_3).close()
        val db = Room.databaseBuilder(context, MiGuardiaDatabase::class.java, DB_V2)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        val sqlite = db.openHelper.readableDatabase
        ALL_V2_TABLES.forEach { table -> assertTableCount(sqlite, table, 1) }
        assertTableCount(sqlite, "vacations", 0)
        sqlite.query("SELECT name FROM holidays WHERE id = 'h1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Feriado ficticio", cursor.getString(0))
        }
        sqlite.query("SELECT final_endTime FROM formal_shift_changes WHERE id = 'f1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("08:00", cursor.getString(0))
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun completeV1ToV2ToV3ChainPreservesOriginalFiveFamilies() {
        helper.createDatabase(DB_CHAIN, 1).apply {
            insertV1Families(this)
            close()
        }

        helper.runMigrationsAndValidate(
            DB_CHAIN,
            3,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
        ).close()
        val db = Room.databaseBuilder(context, MiGuardiaDatabase::class.java, DB_CHAIN)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        val sqlite = db.openHelper.readableDatabase
        V1_TABLES.forEach { table -> assertTableCount(sqlite, table, 1) }
        listOf("holidays", "shift_notes", "shift_novelties", "formal_shift_changes", "vacations")
            .forEach { table -> assertTableCount(sqlite, table, 0) }
        db.close()
    }

    private fun insertV1Families(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO objectives VALUES ('o1','Objetivo Ficticio','OBJ',NULL,NULL,1,1,1)")
        db.execSQL("INSERT INTO schedule_combinations VALUES ('c1','o1','19:00','07:00',-1,1,1,1)")
        db.execSQL("INSERT INTO shifts VALUES ('s1',1,2,'America/Argentina/Cordoba','2026-08-13','Objetivo Ficticio','OBJ',NULL,'19:00','07:00',-1,NULL,'PLANNED','o1','c1',1,1)")
        db.execSQL("INSERT INTO explicit_day_statuses VALUES ('2026-08-14','DAY_OFF')")
        db.execSQL("INSERT INTO medical_leaves VALUES ('m1','2026-08-15','2026-08-16',NULL,1,1)")
    }

    private fun assertTableCount(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        expected: Int,
    ) {
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Cantidad inesperada en $table", expected, cursor.getInt(0))
        }
    }

    private companion object {
        const val DB_V2 = "migration-2-3-test.db"
        const val DB_CHAIN = "migration-1-3-test.db"
        val V1_TABLES = listOf(
            "objectives",
            "schedule_combinations",
            "shifts",
            "explicit_day_statuses",
            "medical_leaves",
        )
        val ALL_V2_TABLES = V1_TABLES + listOf(
            "holidays",
            "shift_notes",
            "shift_novelties",
            "formal_shift_changes",
        )
    }
}
