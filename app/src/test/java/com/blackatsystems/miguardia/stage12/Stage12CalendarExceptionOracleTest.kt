package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Comparator/encoding checks only. No Room execution or product-policy credit. */
class Stage12CalendarExceptionOracleTest {
    @Test fun medicalAndVacationChangesCannotAbsorbAnUnselectedSimilarNeighbour() {
        val medical = MedicalLeave(UUID(1, 1), DATE, DATE.plusDays(2), "Nota privada propia", STAMP, STAMP)
        val neighbour = medical.copy(id = UUID(1, 2))
        val vacation = Vacation(UUID(2, 1), DATE.plusDays(7), DATE.plusDays(10), STAMP, STAMP)
        val otherVacation = vacation.copy(id = UUID(2, 2), startDate = DATE.plusDays(14), endDateInclusive = DATE.plusDays(17))
        val before = snapshot(listOf(medical, neighbour).map(O::medical) + listOf(vacation, otherVacation).map(O::vacation))
        val changed = medical.copy(startDate = DATE.plusDays(1), endDateInclusive = DATE.plusDays(3), updatedAt = STAMP.plusMillis(1))
        val expected = O.expected(before, listOf(O.medical(medical)), listOf(O.medical(changed)))
        O.requireExact(expected, snapshot(listOf(changed, neighbour).map(O::medical) + listOf(vacation, otherVacation).map(O::vacation)))
        val corrupted = O.expected(expected, listOf(O.medical(neighbour)), listOf(O.medical(neighbour.copy(privateNote = "No permitido"))))
        assertThrows(IllegalStateException::class.java) { O.requireExact(corrupted, expected) }
        assertEquals(setOf("id", "startDate", "endDateInclusive", "privateNote", "createdAtEpochMillis", "updatedAtEpochMillis"), O.medical(changed).values.keys)
        assertEquals(5, O.vacation(vacation).values.size)
    }

    @Test fun aHolidayMoveKeepsTheEditedIdentityAndEveryUndeclaredNeighbour() {
        val edited = Holiday(UUID(3, 1), DATE, "Parecido", STAMP, STAMP)
        val occupied = edited.copy(id = UUID(3, 2), date = DATE.plusDays(1))
        val neighbour = edited.copy(id = UUID(3, 3), date = DATE.plusDays(2))
        val before = snapshot(listOf(edited, occupied, neighbour).map(O::holiday))
        val moved = edited.copy(date = occupied.date, name = "Movido", updatedAt = STAMP.plusMillis(2))
        val expected = O.expected(before, listOf(edited, occupied).map(O::holiday), listOf(O.holiday(moved)))
        O.requireExact(expected, snapshot(listOf(moved, neighbour).map(O::holiday)))
        assertThrows(IllegalStateException::class.java) {
            O.expected(before, inserted = listOf(O.holiday(moved)))
        }
        assertEquals(setOf("id", "localDate", "name", "createdAtEpochMillis", "updatedAtEpochMillis"), O.holiday(moved).values.keys)
        assertEquals(O.holiday(edited).values.getValue("createdAtEpochMillis"), O.holiday(moved).values.getValue("createdAtEpochMillis"))
    }

    @Test fun removingAnActualRemovesItsIntervalsButPreservesTheNeighboursAndSQLiteBooleans() {
        val actual = actual(UUID(4, 1), 0)
        val neighbour = actual(UUID(4, 2), 10)
        val rows = O.actual(actual) + O.actual(neighbour)
        val intervals = O.actual(actual).filter { it.table == "shift_extra_intervals" }
        assertEquals(2, intervals.size)
        assertTrue(intervals.all { it.values.getValue("helpsMeetHoursReferenceSnapshot") == "I:0" })
        assertTrue(intervals.all { it.values.getValue("showDedicatedSummarySnapshot") == "I:1" })
        assertEquals(12, intervals.first().values.size)
        assertEquals(9, O.actual(actual).first().values.size)
        val before = snapshot(rows)
        val expected = O.expected(before, removed = O.actual(actual))
        O.requireExact(expected, snapshot(O.actual(neighbour), includeEmpty = rows))
        val corrupted = O.expected(expected, removed = O.actual(neighbour))
        assertThrows(IllegalStateException::class.java) { O.requireExact(corrupted, expected) }
    }

    @Test fun cancellationEvidenceAndRestorationCompareEveryMetadataFieldAndPrivateFile() {
        val id = UUID(5, 1)
        val evidence = CancellationEvidence("cancel_${id}_1234abcd.png", "image/png", 68, 1, 1, "a".repeat(64))
        val selected = ShiftSituation(id, ShiftStatus.CANCELLED, "Motivo propio", "Nota privada", CancellationActor.ME,
            STAMP.minusSeconds(60), evidence, STAMP, STAMP)
        val neighbourId = UUID(5, 2)
        val neighbour = selected.copy(shiftId = neighbourId, evidence = evidence.copy(storageKey = "cancel_${neighbourId}_1234abcd.png"))
        val selectedFile = "files/schedule_photos/${evidence.storageKey}"
        val neighbourFile = "files/schedule_photos/${neighbour.evidence!!.storageKey}"
        val files = mapOf(selectedFile to Stage12FileSnapshot(68, evidence.sha256), neighbourFile to Stage12FileSnapshot(68, evidence.sha256))
        val before = snapshot(listOf(O.situation(selected), O.situation(neighbour))).copy(files = files)
        val expected = O.expected(before, removed = listOf(O.situation(selected)), files = files - selectedFile)
        assertEquals(listOf(O.situation(neighbour).values.toSortedMap().values.toList()), expected.tables.getValue("shift_situations").rows)
        assertEquals(files - selectedFile, expected.files)
        assertEquals(14, O.situation(selected).values.size)
        assertEquals("I:68", O.situation(selected).values["evidenceByteSize"])
        assertEquals("I:1", O.situation(selected).values["evidencePixelWidth"])
        assertThrows(IllegalStateException::class.java) { O.requireExact(expected.copy(files = emptyMap()), expected) }
        assertEquals("N", O.situation(neighbour.copy(evidence = null)).values["evidenceSha256"])
    }

    private fun actual(id: UUID, seed: Long): ShiftActualAggregate {
        val start = Instant.parse("2026-10-23T11:00:00Z")
        val end = Instant.parse("2026-10-23T20:00:00Z")
        val record = ShiftActualRecord(id, UUID(6, 1), WorkSector.NURSING, start, end, "Diferencia expresa", null, STAMP, STAMP)
        val ranges = listOf(start to start.plusSeconds(900), end.minusSeconds(900) to end)
        return ShiftActualAggregate(record, ranges.mapIndexed { i, range ->
            ShiftExtraInterval(UUID(7, seed+i), id, record.timelineId, record.sector, UUID(8, 1), range.first, range.second,
                "Clase expresa", false, true, STAMP, STAMP)
        })
    }
    private fun snapshot(rows: List<Stage12RecurringReadbackOracle.Row>, includeEmpty: List<Stage12RecurringReadbackOracle.Row> = rows): Stage12StoreSnapshot {
        val schemas = (includeEmpty + rows).groupBy { it.table }.mapValues { (_, rs) -> rs.first().values.keys.sorted() }
        val tables = schemas.mapValues { (name, columns) -> Stage12TableSnapshot(columns,
            rows.filter { it.table == name }.map { row -> columns.map { row.values.getValue(it) } }.sortedBy { it.joinToString("\u0000") }) }
        return Stage12StoreSnapshot(tables, mapOf("own-canary" to "keep"), emptyMap())
    }
    private companion object {
        val O = Stage12RecurringReadbackOracle
        val DATE: LocalDate = LocalDate.of(2026, 10, 23)
        val STAMP: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
