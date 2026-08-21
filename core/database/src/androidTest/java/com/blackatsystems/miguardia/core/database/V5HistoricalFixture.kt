package com.blackatsystems.miguardia.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Datos completamente ficticios que representan las trece familias del esquema Room v5. */
internal object V5HistoricalFixture {
    const val OBJECTIVE_ID: String = "00000000-0000-0000-0000-000000000501"
    const val SCHEDULE_ID: String = "00000000-0000-0000-0000-000000000511"
    const val PRIMARY_SHIFT_ID: String = "00000000-0000-0000-0000-000000000521"
    const val RELATED_SHIFT_ID: String = "00000000-0000-0000-0000-000000000522"

    private const val MEDICAL_LEAVE_ID: String = "00000000-0000-0000-0000-000000000531"
    private const val HOLIDAY_ID: String = "00000000-0000-0000-0000-000000000541"
    private const val SHIFT_NOTE_ID: String = "00000000-0000-0000-0000-000000000551"
    private const val SHIFT_NOVELTY_ID: String = "00000000-0000-0000-0000-000000000561"
    private const val FORMAL_CHANGE_ID: String = "00000000-0000-0000-0000-000000000571"
    private const val VACATION_ID: String = "00000000-0000-0000-0000-000000000581"
    private const val SCHEDULE_PHOTO_ID: String = "00000000-0000-0000-0000-000000000591"

    fun seedAllFamilies(database: SupportSQLiteDatabase) {
        seedV1Families(database)
        insertRelatedShift(database)
        database.execSQL(
            """INSERT INTO holidays (
                id, localDate, name, createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                '$HOLIDAY_ID', '2026-09-06', 'Feriado ficticio', 1700000005000, 1700000006000
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO shift_notes (
                id, shiftId, body, createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                '$SHIFT_NOTE_ID', '$PRIMARY_SHIFT_ID', 'Nota histórica ficticia',
                1700000007000, 1700000008000
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO shift_novelties (
                id, shiftId, type, description, relatedShiftId,
                createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                '$SHIFT_NOVELTY_ID', '$PRIMARY_SHIFT_ID', 'SECOND_SHIFT',
                'Segunda guardia ficticia', '$RELATED_SHIFT_ID',
                1700000009000, 1700000010000
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO formal_shift_changes (
                id, shiftId, scheduleChanged, objectiveChanged, description,
                createdAtEpochMillis, updatedAtEpochMillis,
                original_startEpochMillis, original_endEpochMillis, original_zoneId,
                original_localStartDate, original_objectiveName, original_objectiveAbbreviation,
                original_objectiveAddress, original_startTime, original_endTime,
                original_colorArgb, original_position, original_status,
                original_sourceObjectiveId, original_sourceScheduleCombinationId,
                final_startEpochMillis, final_endEpochMillis, final_zoneId,
                final_localStartDate, final_objectiveName, final_objectiveAbbreviation,
                final_objectiveAddress, final_startTime, final_endTime,
                final_colorArgb, final_position, final_status,
                final_sourceObjectiveId, final_sourceScheduleCombinationId
            ) VALUES (
                '$FORMAL_CHANGE_ID', '$PRIMARY_SHIFT_ID', 1, 0, 'Extensión ficticia',
                1700000011000, 1700000012000,
                1788213600000, 1788256800000, 'America/Argentina/Cordoba',
                '2026-08-31', 'Objetivo histórico ficticio', 'QA',
                'Calle Ficticia 100', '19:00', '07:00',
                -13408615, 'Acceso norte', 'PLANNED', '$OBJECTIVE_ID', '$SCHEDULE_ID',
                1788213600000, 1788260400000, 'America/Argentina/Cordoba',
                '2026-08-31', 'Objetivo histórico ficticio', 'QA',
                'Calle Ficticia 100', '19:00', '08:00',
                -13408615, 'Acceso norte', 'PLANNED', '$OBJECTIVE_ID', '$SCHEDULE_ID'
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO vacations (
                id, startDate, endDateInclusive, createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                '$VACATION_ID', '2026-09-10', '2026-09-12', 1700000013000, 1700000014000
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO schedule_photos (
                id, month, objectiveId, objectiveNameSnapshot, objectiveAbbreviationSnapshot,
                storageKey, mimeType, byteSize, pixelWidth, pixelHeight,
                createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                '$SCHEDULE_PHOTO_ID', '2026-08', '$OBJECTIVE_ID',
                'Objetivo histórico ficticio', 'QA', 'fixture-v5-photo.jpg', 'image/jpeg',
                12345, 1080, 1920, 1700000015000, 1700000016000
            )""".trimIndent(),
        )
        database.execSQL(
            "INSERT INTO shift_notification_configs (shiftId) VALUES ('$PRIMARY_SHIFT_ID')",
        )
        database.execSQL(
            "INSERT INTO shift_notification_reminders (shiftId, leadMinutes) VALUES ('$PRIMARY_SHIFT_ID', 360)",
        )
        database.execSQL(
            "INSERT INTO shift_notification_reminders (shiftId, leadMinutes) VALUES ('$PRIMARY_SHIFT_ID', 720)",
        )
    }

    fun seedV1Families(database: SupportSQLiteDatabase) {
        database.execSQL(
            """INSERT INTO objectives (
                id, fullName, abbreviation, address, note, isActive,
                createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                '$OBJECTIVE_ID', 'Objetivo histórico ficticio', 'QA',
                'Calle Ficticia 100', 'Nota de objetivo ficticia', 1,
                1700000000000, 1700000001000
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO schedule_combinations (
                id, objectiveId, startTime, endTime, colorArgb, isActive,
                createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                '$SCHEDULE_ID', '$OBJECTIVE_ID', '19:00', '07:00', -13408615, 1,
                1700000001000, 1700000002000
            )""".trimIndent(),
        )
        insertPrimaryShift(database)
        database.execSQL(
            "INSERT INTO explicit_day_statuses (localDate, type) VALUES ('2026-09-03', 'DAY_OFF')",
        )
        database.execSQL(
            """INSERT INTO medical_leaves (
                id, startDate, endDateInclusive, privateNote,
                createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                '$MEDICAL_LEAVE_ID', '2026-09-04', '2026-09-05',
                'Nota médica ficticia', 1700000003000, 1700000004000
            )""".trimIndent(),
        )
    }

    fun assertAllFamiliesPreserved(database: SupportSQLiteDatabase) {
        assertV1FamiliesPreserved(database, expectedShiftCount = 2)
        assertCount(database, "shifts", 2)
        assertCount(database, "holidays", 1)
        assertCount(database, "shift_notes", 1)
        assertCount(database, "shift_novelties", 1)
        assertCount(database, "formal_shift_changes", 1)
        assertCount(database, "vacations", 1)
        assertCount(database, "schedule_photos", 1)
        assertCount(database, "shift_notification_configs", 1)
        assertCount(database, "shift_notification_reminders", 2)

        database.query(
            "SELECT relatedShiftId, type, description FROM shift_novelties WHERE id = '$SHIFT_NOVELTY_ID'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(RELATED_SHIFT_ID, cursor.getString(0))
            assertEquals("SECOND_SHIFT", cursor.getString(1))
            assertEquals("Segunda guardia ficticia", cursor.getString(2))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            """SELECT localStartDate, startTimeSnapshot, endTimeSnapshot, position
                FROM shifts WHERE id = '$RELATED_SHIFT_ID'""".trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-09-02", cursor.getString(0))
            assertEquals("08:00", cursor.getString(1))
            assertEquals("16:00", cursor.getString(2))
            assertEquals("Acceso sur", cursor.getString(3))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            """SELECT original_objectiveAbbreviation, original_endTime,
                final_objectiveAbbreviation, final_endTime
                FROM formal_shift_changes WHERE id = '$FORMAL_CHANGE_ID'""".trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("QA", cursor.getString(0))
            assertEquals("07:00", cursor.getString(1))
            assertEquals("QA", cursor.getString(2))
            assertEquals("08:00", cursor.getString(3))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            """SELECT objectiveAbbreviationSnapshot, storageKey, byteSize, pixelWidth, pixelHeight
                FROM schedule_photos WHERE id = '$SCHEDULE_PHOTO_ID'""".trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("QA", cursor.getString(0))
            assertEquals("fixture-v5-photo.jpg", cursor.getString(1))
            assertEquals(12345L, cursor.getLong(2))
            assertEquals(1080, cursor.getInt(3))
            assertEquals(1920, cursor.getInt(4))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            "SELECT localDate, name FROM holidays WHERE id = '$HOLIDAY_ID'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-09-06", cursor.getString(0))
            assertEquals("Feriado ficticio", cursor.getString(1))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            "SELECT shiftId, body FROM shift_notes WHERE id = '$SHIFT_NOTE_ID'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(PRIMARY_SHIFT_ID, cursor.getString(0))
            assertEquals("Nota histórica ficticia", cursor.getString(1))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            "SELECT startDate, endDateInclusive FROM vacations WHERE id = '$VACATION_ID'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-09-10", cursor.getString(0))
            assertEquals("2026-09-12", cursor.getString(1))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            """SELECT leadMinutes FROM shift_notification_reminders
                WHERE shiftId = '$PRIMARY_SHIFT_ID' ORDER BY leadMinutes""".trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(360L, cursor.getLong(0))
            assertTrue(cursor.moveToNext())
            assertEquals(720L, cursor.getLong(0))
            assertFalse(cursor.moveToNext())
        }
    }

    fun assertV1FamiliesPreserved(
        database: SupportSQLiteDatabase,
        expectedShiftCount: Int = 1,
    ) {
        assertCount(database, "objectives", 1)
        assertCount(database, "schedule_combinations", 1)
        assertCount(database, "shifts", expectedShiftCount)
        assertCount(database, "explicit_day_statuses", 1)
        assertCount(database, "medical_leaves", 1)

        database.query(
            """SELECT fullName, abbreviation, address, note
                FROM objectives WHERE id = '$OBJECTIVE_ID'""".trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Objetivo histórico ficticio", cursor.getString(0))
            assertEquals("QA", cursor.getString(1))
            assertEquals("Calle Ficticia 100", cursor.getString(2))
            assertEquals("Nota de objetivo ficticia", cursor.getString(3))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            """SELECT objectiveId, startTime, endTime, colorArgb, isActive
                FROM schedule_combinations WHERE id = '$SCHEDULE_ID'""".trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(OBJECTIVE_ID, cursor.getString(0))
            assertEquals("19:00", cursor.getString(1))
            assertEquals("07:00", cursor.getString(2))
            assertEquals(-13408615, cursor.getInt(3))
            assertEquals(1, cursor.getInt(4))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            """SELECT objectiveNameSnapshot, objectiveAbbreviationSnapshot,
                startTimeSnapshot, endTimeSnapshot, sourceObjectiveId, sourceScheduleCombinationId
                FROM shifts WHERE id = '$PRIMARY_SHIFT_ID'""".trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Objetivo histórico ficticio", cursor.getString(0))
            assertEquals("QA", cursor.getString(1))
            assertEquals("19:00", cursor.getString(2))
            assertEquals("07:00", cursor.getString(3))
            assertEquals(OBJECTIVE_ID, cursor.getString(4))
            assertEquals(SCHEDULE_ID, cursor.getString(5))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            "SELECT privateNote FROM medical_leaves WHERE id = '$MEDICAL_LEAVE_ID'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Nota médica ficticia", cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
        database.query(
            "SELECT type FROM explicit_day_statuses WHERE localDate = '2026-09-03'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("DAY_OFF", cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
    }

    fun assertNoForeignKeyViolations(database: SupportSQLiteDatabase) {
        database.query("PRAGMA foreign_key_check").use { cursor ->
            assertFalse("Se encontraron claves foráneas inválidas", cursor.moveToFirst())
        }
    }

    private fun insertPrimaryShift(database: SupportSQLiteDatabase) {
        insertShift(
            database = database,
            id = PRIMARY_SHIFT_ID,
            startEpochMillis = 1788213600000,
            endEpochMillis = 1788256800000,
            localStartDate = "2026-08-31",
            startTime = "19:00",
            endTime = "07:00",
            position = "Acceso norte",
        )
    }

    private fun insertRelatedShift(database: SupportSQLiteDatabase) {
        insertShift(
            database = database,
            id = RELATED_SHIFT_ID,
            startEpochMillis = 1788332400000,
            endEpochMillis = 1788361200000,
            localStartDate = "2026-09-02",
            startTime = "08:00",
            endTime = "16:00",
            position = "Acceso sur",
        )
    }

    private fun insertShift(
        database: SupportSQLiteDatabase,
        id: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        localStartDate: String,
        startTime: String,
        endTime: String,
        position: String,
    ) {
        database.execSQL(
            """INSERT INTO shifts (
                id, startEpochMillis, endEpochMillis, zoneId, localStartDate,
                objectiveNameSnapshot, objectiveAbbreviationSnapshot, objectiveAddressSnapshot,
                startTimeSnapshot, endTimeSnapshot, colorArgbSnapshot, position, status,
                sourceObjectiveId, sourceScheduleCombinationId, createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                '$id', $startEpochMillis, $endEpochMillis, 'America/Argentina/Cordoba', '$localStartDate',
                'Objetivo histórico ficticio', 'QA', 'Calle Ficticia 100',
                '$startTime', '$endTime', -13408615, '$position', 'PLANNED',
                '$OBJECTIVE_ID', '$SCHEDULE_ID', 1700000002000, 1700000003000
            )""".trimIndent(),
        )
    }

    private fun assertCount(
        database: SupportSQLiteDatabase,
        table: String,
        expected: Int,
    ) {
        database.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(table, expected, cursor.getInt(0))
        }
    }
}
