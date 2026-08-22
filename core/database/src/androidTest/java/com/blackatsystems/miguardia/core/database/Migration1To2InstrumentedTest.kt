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
class Migration1To2InstrumentedTest {
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
        context.deleteDatabase(DB)
    }

    @Test
    @Throws(IOException::class)
    fun migrationPreservesAllV1FamiliesAndCreatesV2Tables() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO objectives VALUES ('o1','Objetivo Ficticio','OBJ',NULL,NULL,1,1,1)")
            execSQL("INSERT INTO schedule_combinations VALUES ('c1','o1','19:00','07:00',-1,1,1,1)")
            execSQL("INSERT INTO shifts VALUES ('s1',1,2,'America/Argentina/Cordoba','2026-08-13','Objetivo Ficticio','OBJ',NULL,'19:00','07:00',-1,NULL,'PLANNED','o1','c1',1,1)")
            execSQL("INSERT INTO explicit_day_statuses VALUES ('2026-08-14','DAY_OFF')")
            execSQL("INSERT INTO medical_leaves VALUES ('m1','2026-08-15','2026-08-16',NULL,1,1)")
            close()
        }

        helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2).close()
        val db = Room.databaseBuilder(context, MiGuardiaDatabase::class.java, DB)
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )
            .build()
        val sqlite = db.openHelper.readableDatabase
        listOf("objectives", "schedule_combinations", "shifts", "explicit_day_statuses", "medical_leaves").forEach { table ->
            sqlite.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals("No se preservó $table", 1, cursor.getInt(0))
            }
        }
        sqlite.query("SELECT fullName, abbreviation, isActive FROM objectives WHERE id = 'o1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Objetivo Ficticio", cursor.getString(0))
            assertEquals("OBJ", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
        }
        sqlite.query("SELECT objectiveId, startTime, endTime, colorArgb FROM schedule_combinations WHERE id = 'c1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("o1", cursor.getString(0))
            assertEquals("19:00", cursor.getString(1))
            assertEquals("07:00", cursor.getString(2))
            assertEquals(-1, cursor.getInt(3))
        }
        sqlite.query("SELECT zoneId, localStartDate, objectiveNameSnapshot, status FROM shifts WHERE id = 's1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("America/Argentina/Cordoba", cursor.getString(0))
            assertEquals("2026-08-13", cursor.getString(1))
            assertEquals("Objetivo Ficticio", cursor.getString(2))
            assertEquals("PLANNED", cursor.getString(3))
        }
        sqlite.query("SELECT type FROM explicit_day_statuses WHERE localDate = '2026-08-14'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("DAY_OFF", cursor.getString(0))
        }
        sqlite.query("SELECT startDate, endDateInclusive FROM medical_leaves WHERE id = 'm1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("2026-08-15", cursor.getString(0))
            assertEquals("2026-08-16", cursor.getString(1))
        }
        listOf("holidays", "shift_notes", "shift_novelties", "formal_shift_changes").forEach { table ->
            sqlite.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
        db.close()
    }

    private companion object { const val DB = "migration-1-2-test.db" }
}
