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
class Migration4To5InstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MiGuardiaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After fun cleanUp() { context.deleteDatabase(DB) }

    @Test
    fun migrationCreatesEmptyNotificationTablesAndPreservesV4Data() {
        helper.createDatabase(DB, 4).apply {
            execSQL("INSERT INTO shifts VALUES ('s1',1,2,'America/Argentina/Cordoba','2026-08-13','Objetivo QA','QA',NULL,'08:00','16:00',-1,NULL,'PLANNED',NULL,NULL,1,1)")
            execSQL("INSERT INTO schedule_photos VALUES ('p1','2026-08',NULL,NULL,NULL,'opaque.jpg','image/jpeg',10,1,1,1,1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 5, true, MIGRATION_4_5)
        db.query("SELECT COUNT(*) FROM shifts").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM schedule_photos").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM shift_notification_configs").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM shift_notification_reminders").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_%' AND name != 'sqlite_sequence'").use {
            it.moveToFirst()
            assertEquals(13, it.getInt(0))
        }
        db.close()
    }

    @Test
    fun completeChainFromV1ToV5PreservesOriginalShift() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO shifts VALUES ('s1',1,2,'America/Argentina/Cordoba','2026-08-13','Objetivo QA','QA',NULL,'08:00','16:00',-1,NULL,'PLANNED',NULL,NULL,1,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(
            DB,
            5,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
        )
        db.query("SELECT objectiveNameSnapshot FROM shifts WHERE id='s1'").use {
            it.moveToFirst()
            assertEquals("Objetivo QA", it.getString(0))
        }
        db.close()
    }

    private companion object { const val DB = "migration-4-5-test.db" }
}
