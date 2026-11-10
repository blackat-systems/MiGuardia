package com.blackatsystems.miguardia.stage12

import java.time.Instant
import java.time.LocalTime
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Typed comparator controls only; no SQLite, Room or Android execution. */
class Stage12TemporalReadbackOracleTest {
    private val oracle = Stage12RecurringReadbackOracle
    private val recipe = Stage12TemporalRecipe(UUID.fromString("b119c026-0000-4000-8000-000000000002"), Instant.ofEpochMilli(123))
    private fun text(v: Any) = "T:" + Base64.getEncoder().encodeToString(v.toString().toByteArray(Charsets.UTF_8))
    private val literal = Stage12TemporalLiteralOracle.literal("A119-25h")
    private val window = recipe.expectedWindow(literal)
    private val template = recipe.template(LocalTime.of(8, 15), LocalTime.of(16, 45))
    private fun expectedRows() = listOf(
        Stage12RecurringReadbackOracle.Row("availability_windows", linkedMapOf("id" to text(window.id),
            "timelineId" to text(recipe.timelineId), "sector" to text("NURSING"), "configurationRevisionId" to text(recipe.revisionId),
            "ownerLocalDate" to text("2026-10-23"), "zoneId" to text("America/Argentina/Buenos_Aires"),
            "startEpochMillis" to "I:1792753200000", "endEpochMillis" to "I:1792843200000", "labelSnapshot" to text("Guardia pasiva"),
            "createdAtEpochMillis" to "I:123", "updatedAtEpochMillis" to "I:123")),
        Stage12RecurringReadbackOracle.Row("objectives", linkedMapOf("id" to text(recipe.objectiveId), "fullName" to text("Hospital Temporal"),
            "abbreviation" to text("HTP"), "address" to text("Calle de prueba 120"), "note" to "N", "isActive" to "I:1",
            "createdAtEpochMillis" to "I:123", "updatedAtEpochMillis" to "I:123", "weatherLatitude" to "N", "weatherLongitude" to "N")),
        Stage12RecurringReadbackOracle.Row("work_places", linkedMapOf("id" to text(recipe.placeId), "timelineId" to text(recipe.timelineId),
            "sector" to text("NURSING"), "objectiveId" to text(recipe.objectiveId), "isActive" to "I:1",
            "createdAtEpochMillis" to "I:123", "updatedAtEpochMillis" to "I:123")),
        Stage12RecurringReadbackOracle.Row("work_types", linkedMapOf("id" to text(recipe.typeId), "timelineId" to text(recipe.timelineId),
            "sector" to text("NURSING"), "name" to text("Enfermería temporal"), "normalizedNameKey" to text("ENFERMERÍA TEMPORAL"),
            "behavior" to text("ACTIVE_WORK"), "isActive" to "I:1", "createdAtEpochMillis" to "I:123", "updatedAtEpochMillis" to "I:123")),
        Stage12RecurringReadbackOracle.Row("work_templates", linkedMapOf("id" to text(recipe.templateId), "timelineId" to text(recipe.timelineId),
            "sector" to text("NURSING"), "workPlaceId" to text(recipe.placeId), "objectiveId" to text(recipe.objectiveId), "workTypeId" to text(recipe.typeId),
            "startTime" to text("08:15"), "endTime" to text("16:45"), "colorArgb" to "I:-13408615", "isActive" to "I:1",
            "createdAtEpochMillis" to "I:123", "updatedAtEpochMillis" to "I:123")),
    )
    private fun actualRows() = listOf(oracle.availability(window), oracle.objective(recipe.objective), oracle.place(recipe.place),
        oracle.type(recipe.type), oracle.template(template))
    private fun baseline(): Stage12StoreSnapshot {
        val rows = expectedRows() + oracle.write(recipe.expectedShift(Stage12TemporalLiteralOracle.literal("C026-historicos")))
        val tables = rows.associate { row -> row.table to Stage12TableSnapshot(row.values.keys.toList(), listOf(row.values.values.toList())) } +
            ("unrelated_metadata" to Stage12TableSnapshot(listOf("untouched"), listOf(listOf("I:9"))))
        return Stage12StoreSnapshot(tables, mapOf("own#typed" to "BOOLEAN:1"),
            mapOf("files/schedule_photos/own-neighbour.png" to Stage12FileSnapshot(10, "a".repeat(64))))
    }

    @Test fun allFiveMappersHaveIndependentCompleteColumnsAndSqlTypes() {
        assertEquals(listOf(11, 10, 7, 9, 12), actualRows().map { it.values.size })
        assertEquals(expectedRows(), actualRows())
        val coordinates = oracle.objective(recipe.objective.copy(weatherLatitude = 0.0, weatherLongitude = 1.0))
        assertEquals("R:0", coordinates.values.getValue("weatherLatitude"))
        assertEquals("R:3ff0000000000000", coordinates.values.getValue("weatherLongitude"))
    }

    @Test fun everyTemporalAndCatalogCellTypeMissingColumnAndDuplicateKeyIsObserved() {
        val before = baseline()
        for (row in actualRows()) {
            val table = before.tables.getValue(row.table)
            for (column in table.columns.indices) {
                val values = table.rows.single().toMutableList()
                values[column] = if (values[column].startsWith("I:")) text(values[column].substringAfter(':')) else "I:999"
                val mutant = before.copy(tables = before.tables + (row.table to table.copy(rows = listOf(values))))
                assertThrows(IllegalStateException::class.java) { oracle.requireExact(mutant, before) }
                val missing = row.copy(values = row.values - table.columns[column])
                if (table.columns[column] == "id") {
                    assertThrows(NoSuchElementException::class.java) { oracle.expected(before, removed = listOf(missing)) }
                } else {
                    assertThrows(IllegalStateException::class.java) { oracle.expected(before, removed = listOf(missing)) }
                }
            }
            assertThrows(IllegalStateException::class.java) { oracle.expected(before, removed = listOf(row, row)) }
            assertThrows(IllegalStateException::class.java) { oracle.expected(before, inserted = listOf(row)) }
        }
    }

    @Test fun historicalCatalogDeltaPreservesStoredShiftSnapshotAndAllUnmentionedData() {
        val before = baseline()
        val old = oracle.objective(recipe.objective)
        val changed = oracle.objective(recipe.objective.copy(fullName = "Institución modificada", abbreviation = "MOD",
            address = "Dirección modificada 999", updatedAt = Instant.ofEpochMilli(124)))
        val expected = oracle.expected(before, removed = listOf(old), inserted = listOf(changed))
        assertEquals(before.tables - "objectives", expected.tables - "objectives")
        assertEquals(before.preferences, expected.preferences)
        assertEquals(before.files, expected.files)
        val shiftTable = expected.tables.getValue("shifts")
        val mutated = shiftTable.rows.single().toMutableList()
        mutated[shiftTable.columns.indexOf("updatedAtEpochMillis")] = "I:124"
        assertThrows(IllegalStateException::class.java) {
            oracle.requireExact(expected.copy(tables = expected.tables + ("shifts" to shiftTable.copy(rows = listOf(mutated)))), expected)
        }
    }

    @Test fun insertedWindowCannotHideWrongUuidOwnerNeighbourPreferencesOrFiles() {
        val before = baseline()
        val row = oracle.availability(window)
        val empty = before.copy(tables = before.tables + (row.table to before.tables.getValue(row.table).copy(rows = emptyList())))
        val expected = oracle.expected(empty, inserted = listOf(row))
        oracle.requireExact(expected, before)
        for (field in listOf("id", "ownerLocalDate", "zoneId", "labelSnapshot", "startEpochMillis", "endEpochMillis")) {
            val incorrect = row.copy(values = row.values + (field to text("similar-neighbour")))
            assertThrows(IllegalStateException::class.java) { oracle.requireExact(oracle.expected(empty, inserted = listOf(incorrect)), expected) }
        }
        assertThrows(IllegalStateException::class.java) { oracle.requireExact(expected.copy(preferences = emptyMap()), expected) }
        assertThrows(IllegalStateException::class.java) { oracle.requireExact(expected.copy(files = emptyMap()), expected) }
    }
}
