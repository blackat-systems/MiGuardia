package com.blackatsystems.miguardia.core.database

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupRecord
import com.blackatsystems.miguardia.core.domain.backup.BackupTable
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupSchemaV6
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupSchemaV8
import com.blackatsystems.miguardia.upgrade.UpgradeFixtureSnapshots
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

/**
 * Pure database migration, using a UUID-named database owned only by this test.
 * No live database, preferences, photo files, installed upgrade or SAF is exercised.
 */
@RunWith(AndroidJUnit4::class)
class V6ToV8ConservationInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val databaseName = "stage9-v6-v8-${UUID.randomUUID()}.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MiGuardiaV2Database::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun deleteOnlyTheOwnedMigrationDatabase() {
        check(databaseName.startsWith("stage9-v6-v8-") && databaseName.endsWith(".db"))
        check(databaseName != MiGuardiaV2Database.DATABASE_NAME)
        context.deleteDatabase(databaseName)
    }

    @Test
    fun actualSixToEightMigrationsPreserveEveryTypedFieldAndStartNewTablesEmpty() = runBlocking {
        // Bytes are synthetic metadata here; Android photo-file conservation is separate.
        val source = UpgradeFixtureSnapshots.sourceV6(DAY, PHOTO_BYTES)
        val expected = UpgradeFixtureSnapshots.targetV8(DAY, PHOTO_BYTES)
        MiGuardiaBackupSchemaV6.requireValid(source)
        MiGuardiaBackupSchemaV8.requireValid(expected)
        assertEquals(27, source.tables.size)
        assertTrue(source.tables.all { it.records.isNotEmpty() })
        val sourceValues = source.tables.flatMap { table -> table.records.flatMap { it.values } }
        assertTrue(sourceValues.any { it == BackupValue.Null })
        assertTrue(sourceValues.any { it is BackupValue.Text })
        assertTrue(sourceValues.any { it is BackupValue.Integer })
        assertTrue(sourceValues.any { it is BackupValue.Real })

        helper.createDatabase(databaseName, 6).use { original ->
            original.execSQL("PRAGMA foreign_keys = ON")
            assertEquals(1L, original.scalarLong("PRAGMA foreign_keys"))
            original.seed(source)
            original.assertSnapshotEquals(source)
            original.assertHealthy()
        }

        // The helper validates the actual V8 schema, including indices and foreign keys.
        helper.runMigrationsAndValidate(
            databaseName, 8, true,
            MiGuardiaV2Database.MIGRATION_6_7,
            MiGuardiaV2Database.MIGRATION_7_8,
        ).use { migrated ->
            source.tables.forEach { table ->
                assertEquals("Every V6 field must survive in ${table.name}", table, migrated.readTable(table))
            }
            migrated.assertSnapshotEquals(expected)
            assertEquals(0L, migrated.scalarLong("SELECT COUNT(*) FROM shift_note_attachments"))
            assertEquals(0L, migrated.scalarLong("SELECT COUNT(*) FROM shift_situations"))
            migrated.assertHealthy()
        }

        // Reopen through the generated Room implementation and audit the real V2 graph.
        // Direct Room access avoids the backup gateway's unrelated orphan-candidate sweep.
        val reopened = MiGuardiaV2Database.build(context, databaseName)
        try {
            reopened.withTransaction {
                reopened.requireValidV2LocalData()
                reopened.openHelper.writableDatabase.apply {
                    assertSnapshotEquals(expected)
                    assertHealthy()
                }
            }
        } finally {
            reopened.close()
        }
        Unit
    }

    private fun SupportSQLiteDatabase.seed(source: BackupDatabaseSnapshot) {
        beginTransaction()
        try {
            // The source fixture follows the portable allowlist, not dependency order.
            // Defer checks until every referenced identity has been inserted atomically.
            execSQL("PRAGMA defer_foreign_keys = ON")
            source.tables.forEach { table ->
                val columns = table.columns.joinToString(",") { it.quoted() }
                val placeholders = table.columns.joinToString(",") { "?" }
                compileStatement("INSERT INTO ${table.name.quoted()} ($columns) VALUES ($placeholders)").use { statement ->
                    table.records.forEach { record ->
                        record.values.forEachIndexed { index, value -> statement.bindValue(index + 1, value) }
                        statement.executeInsert()
                        statement.clearBindings()
                    }
                }
            }
            assertHealthy()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun SupportSQLiteStatement.bindValue(index: Int, value: BackupValue) {
        when (value) {
            BackupValue.Null -> bindNull(index)
            is BackupValue.Text -> bindString(index, value.value)
            is BackupValue.Integer -> bindLong(index, value.value)
            is BackupValue.Real -> bindDouble(index, value.value)
            is BackupValue.Binary -> bindBlob(index, value.value)
        }
    }

    private fun SupportSQLiteDatabase.assertSnapshotEquals(expected: BackupDatabaseSnapshot) {
        assertEquals(expected.roomVersion.toLong(), scalarLong("PRAGMA user_version"))
        assertEquals(
            expected.roomIdentityHash,
            scalarText("SELECT identity_hash FROM room_master_table WHERE id = 42"),
        )
        val names = query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' AND name != 'room_master_table'",
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertEquals(expected.tables.map { it.name }.toSet(), names)
        val actual = BackupDatabaseSnapshot(
            roomVersion = scalarLong("PRAGMA user_version").toInt(),
            roomIdentityHash = scalarText("SELECT identity_hash FROM room_master_table WHERE id = 42"),
            timelineId = scalarText("SELECT timelineId FROM work_configuration_roots"),
            tables = expected.tables.map { readTable(it) },
        )
        assertEquals(expected, actual)
    }

    private fun SupportSQLiteDatabase.readTable(shape: BackupTable): BackupTable {
        val columns = shape.columns.joinToString(",") { it.quoted() }
        val primaryKey = shape.primaryKey.joinToString(",") { it.quoted() }
        val records = query("SELECT $columns FROM ${shape.name.quoted()} ORDER BY $primaryKey").use { cursor ->
            assertEquals(shape.columns, cursor.columnNames.toList())
            buildList {
                while (cursor.moveToNext()) {
                    add(BackupRecord(shape.columns.indices.map { cursor.backupValue(it) }))
                }
            }
        }
        return shape.copy(records = records)
    }

    private fun Cursor.backupValue(index: Int): BackupValue = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> BackupValue.Null
        Cursor.FIELD_TYPE_STRING -> BackupValue.Text(getString(index))
        Cursor.FIELD_TYPE_INTEGER -> BackupValue.Integer(getLong(index))
        Cursor.FIELD_TYPE_FLOAT -> BackupValue.Real(getDouble(index))
        Cursor.FIELD_TYPE_BLOB -> BackupValue.Binary(getBlob(index))
        else -> error("Unexpected SQLite type in isolated fixture")
    }

    private fun SupportSQLiteDatabase.assertHealthy() {
        query("PRAGMA integrity_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
        query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
    }

    private fun SupportSQLiteDatabase.scalarLong(sql: String): Long = query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        val value = cursor.getLong(0)
        assertFalse(cursor.moveToNext())
        value
    }

    private fun SupportSQLiteDatabase.scalarText(sql: String): String = query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        val value = cursor.getString(0)
        assertFalse(cursor.moveToNext())
        value
    }

    private fun String.quoted(): String = "`${replace("`", "``")}`"

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 21)
        const val PHOTO_BYTES = 31_337L
    }
}
