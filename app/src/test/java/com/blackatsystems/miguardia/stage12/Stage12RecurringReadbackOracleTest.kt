package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Conservation-comparator mutants only. These tests do not execute or emulate SQLite. */
class Stage12RecurringReadbackOracleTest {
    private val oracle = Stage12RecurringReadbackOracle
    private val note = ShiftNote(id(1), id(2), "Own original body", Instant.ofEpochMilli(10), Instant.ofEpochMilli(20))
    private fun id(n: Int) = UUID(0, n.toLong())
    private fun text(v: String) = "T:" + Base64.getEncoder().encodeToString(v.toByteArray(Charsets.UTF_8))
    private fun table(vararg values: Pair<String, String>) = Stage12TableSnapshot(values.map { it.first }, listOf(values.map { it.second }))
    private fun baseline() = Stage12StoreSnapshot(
        tables = linkedMapOf(
            "shift_notes" to table("id" to text(id(1).toString()), "shiftId" to text(id(2).toString()),
                "body" to text(note.body), "createdAtEpochMillis" to "I:10", "updatedAtEpochMillis" to "I:20"),
            "shift_note_attachments" to table("id" to text("attachment"), "noteId" to text(id(1).toString()),
                "mimeType" to text("image/png"), "byteSize" to "I:45", "position" to "I:0"),
            "shift_notification_configs" to table("shiftId" to text(id(2).toString()), "enabled" to "I:1"),
            "shift_notification_reminders" to table("shiftId" to text(id(2).toString()), "leadMinutes" to "I:15"),
            "shift_actual_records" to table("shiftId" to text(id(2).toString()), "differenceReason" to text("Own difference")),
            "shift_extra_intervals" to table("id" to text("extra"), "classNameSnapshot" to text("Own class")),
            "extra_work_classes" to table("id" to text("class"), "helpsMeetHoursReference" to "I:0"),
            "shift_situations" to table("shiftId" to text("neighbour"), "privateNote" to text("Own situation"), "evidenceStorageKey" to text("evidence.png")),
            "recurring_plan_revisions" to table("id" to text("revision"), "effectiveFrom" to text("2026-11-01")),
            "sqlite_master" to table("name" to text("index"), "sql" to "N", "rootpage" to "I:17"),
        ),
        preferences = mapOf("own#typed-value" to "boolean:1"),
        files = mapOf("files/schedule_photos/image.png" to Stage12FileSnapshot(45, "a".repeat(64)),
            "files/cancellation_evidence/evidence.png" to Stage12FileSnapshot(60, "b".repeat(64))),
    )
    private fun rejects(body: () -> Unit) { assertThrows(IllegalStateException::class.java, body) }

    @Test fun declaredNoteChangePreservesAllUnmentionedStores() {
        val before = baseline(); val changed = note.copy(body = "Own consciously changed body", updatedAt = Instant.ofEpochMilli(21))
        val expected = oracle.expected(before, listOf(oracle.note(note)), listOf(oracle.note(changed)))
        val row = before.tables.getValue("shift_notes").rows.single().toMutableList()
        row[2] = text(changed.body); row[4] = "I:21"
        val actual = before.copy(tables = before.tables + ("shift_notes" to before.tables.getValue("shift_notes").copy(rows = listOf(row))))
        oracle.requireExact(actual, expected)
        assertEquals(before.preferences, expected.preferences); assertEquals(before.files, expected.files)
        assertEquals(before.tables - "shift_notes", expected.tables - "shift_notes")
    }

    @Test fun everyUnmentionedCellIncludingAssociatesAndMetadataIsObserved() {
        val before = baseline()
        before.tables.forEach { (name, table) ->
            table.rows.single().indices.forEach { index ->
                val changed = table.rows.single().toMutableList(); changed[index] = text("mutant-$name-$index")
                val mutant = before.copy(tables = before.tables + (name to table.copy(rows = listOf(changed))))
                rejects { oracle.requireExact(mutant, before) }
            }
        }
    }

    @Test fun missingTableRowColumnAndSqlTypeNeverNormalizeToEquality() {
        val before = baseline(); val table = before.tables.getValue("shift_notes")
        rejects { oracle.requireExact(before.copy(tables = before.tables - "shift_situations"), before) }
        rejects { oracle.requireExact(before.copy(tables = before.tables + ("shift_notes" to table.copy(rows = emptyList()))), before) }
        rejects { oracle.requireExact(before.copy(tables = before.tables + ("shift_notes" to table.copy(columns = table.columns.dropLast(1)))), before) }
        val row = table.rows.single().toMutableList(); row[3] = text("10")
        rejects { oracle.requireExact(before.copy(tables = before.tables + ("shift_notes" to table.copy(rows = listOf(row)))), before) }
    }

    @Test fun sameIdsAndTimestampsDoNotHideChangedNoteValues() {
        val before = baseline(); val altered = note.copy(body = "Unexpected body with same ID and times")
        rejects { oracle.expected(before, removed = listOf(oracle.note(altered))) }
    }

    @Test fun insertsCannotClobberAndDeltaCannotRepeatKeys() {
        val before = baseline(); val row = oracle.note(note)
        rejects { oracle.expected(before, inserted = listOf(row)) }
        rejects { oracle.expected(before, removed = listOf(row, row)) }
        rejects { oracle.expected(before, removed = listOf(row), inserted = listOf(row, row)) }
    }

    @Test fun incompleteOrAdditionalColumnExpectationsAreRejected() {
        val before = baseline(); val row = oracle.note(note)
        rejects { oracle.expected(before, removed = listOf(row.copy(values = row.values - "body"))) }
        rejects { oracle.expected(before, removed = listOf(row.copy(values = row.values + ("ignored" to "N")))) }
    }

    @Test fun imageAndSituationEvidenceRequireExactKeysMetadataAndBytes() {
        val before = baseline()
        before.files.forEach { (key, file) ->
            for (changed in listOf(file.copy(size = file.size + 1), file.copy(sha256 = "c".repeat(64)), file.copy(kind = Stage12PrivateNodeKind.DIRECTORY))) {
                rejects { oracle.requireExact(before.copy(files = before.files + (key to changed)), before) }
            }
            rejects { oracle.requireExact(before.copy(files = before.files - key), before) }
        }
        rejects { oracle.requireExact(before.copy(files = before.files + ("unowned-added" to Stage12FileSnapshot(1, "d".repeat(64)))), before) }
        rejects { oracle.requireExact(before.copy(preferences = mapOf("own#typed-value" to "integer:1")), before) }
    }

    @Test fun fixtureCaseNamesAreClosedAndCannotAddressReceivedDatabase() {
        val run = "817511eb-6af2-4cc7-a2b7-bf98c958cb8b"
        assertEquals(7, Stage12RecurringCase.entries.size)
        Stage12RecurringCase.entries.forEach {
            assertEquals("stage12-recurring-$run-${it.wire}.db", it.databaseName(run))
            assertEquals(it, Stage12RecurringCase.fromWire(it.wire))
        }
        rejects { Stage12RecurringCase.fromWire("../miguardia-v2.db") }
        assertThrows(IllegalArgumentException::class.java) { Stage12RecurringCase.LIFECYCLE.databaseName("../received") }
    }
}
