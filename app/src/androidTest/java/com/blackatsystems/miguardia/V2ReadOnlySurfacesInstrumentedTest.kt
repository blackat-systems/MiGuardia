package com.blackatsystems.miguardia

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjectionConfig
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize
import com.blackatsystems.miguardia.core.domain.widget.projectWidget
import com.blackatsystems.miguardia.ui.calendar.CalendarMonthObserver
import com.blackatsystems.miguardia.ui.nextevent.NextEventObserver
import com.blackatsystems.miguardia.ui.nextevent.TemporalDelay
import com.blackatsystems.miguardia.ui.nextevent.V2WorkEventSourceObserver
import com.blackatsystems.miguardia.ui.summary.SummaryObserver
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V2ReadOnlySurfacesInstrumentedTest {
    @Test
    fun calendarSummaryTodayCardAndWidgetQueriesLeaveAllApplicationTablesUnchanged() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "v2-read-only-surfaces-${UUID.randomUUID()}.db"
        var store = LocalDataStore.create(context, databaseName)
        var storeIsOpen = true
        var sqlite: SQLiteDatabase? = null
        try {
            val shift = deterministicShift()
            val write = V2AppTestFixture.writeFor(store, shift, MONTH.atDay(1))
            store.v2Shifts.insert(write)
            sqlite = SQLiteDatabase.openDatabase(
                context.getDatabasePath(databaseName).absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            val baseline = logicalSnapshot(sqlite)
            assertEquals(27, baseline.tables.size)
            assertEquals(EXPECTED_APPLICATION_TABLES, baseline.tables.toSet())

            val calendar = withTimeout(5_000) {
                CalendarMonthObserver(
                    shiftRepository = store.shifts,
                    explicitDayStatusRepository = store.explicitDayStatuses,
                    medicalLeaveRepository = store.medicalLeaves,
                    holidayRepository = store.holidays,
                    vacationRepository = store.vacations,
                ).observe(MONTH).first()
            }
            assertEquals(listOf(shift.id), calendar.shifts.map { it.id })
            assertTrue(calendar.explicitStatuses.isEmpty())
            assertUnchanged(baseline, logicalSnapshot(sqlite), "después de Calendario")

            val summary = withTimeout(5_000) {
                SummaryObserver(
                    configurations = store.workConfiguration,
                    catalogs = store.workCatalog,
                    shifts = store.v2Shifts,
                    actuals = store.shiftActuals,
                    extras = store.independentExtraWork,
                    availability = store.availabilityWindows,
                    holidays = store.holidays,
                    medicalLeaves = store.medicalLeaves,
                    vacations = store.vacations,
                    explicitStatuses = store.explicitDayStatuses,
                    clock = CLOCK,
                    zoneId = ZONE,
                ).observe(MONTH).first()
            }
            assertTrue(summary.hasContent)
            assertEquals(0L, summary.essentials.totalWorked?.value)
            assertEquals(120L, summary.essentials.pendingScheduled?.value)
            assertUnchanged(baseline, logicalSnapshot(sqlite), "después de Resumen")

            val todayCard = withTimeout(5_000) {
                NextEventObserver(
                    shifts = store.v2Shifts,
                    availabilityWindows = store.availabilityWindows,
                    shiftActuals = store.shiftActuals,
                    independentExtras = store.independentExtraWork,
                    explicitDayStatuses = store.explicitDayStatuses,
                    vacations = store.vacations,
                    medicalLeaves = store.medicalLeaves,
                    workConfiguration = store.workConfiguration,
                    clock = CLOCK,
                    zoneId = ZONE,
                    temporalDelay = TemporalDelay { awaitCancellation() },
                ).observe().first()
            }
            assertEquals(TodayCardPrimary.UPCOMING_SHIFT, todayCard.primary)
            assertEquals(NextEventPrimary.UPCOMING_SHIFT, todayCard.futureEvent.primaryEvent)
            assertEquals(
                shift.id,
                (todayCard.futureEvent.primaryEvents.single() as NextEventItem.Shift).shiftId,
            )
            assertUnchanged(baseline, logicalSnapshot(sqlite), "después de la tarjeta superior")

            val widget = withTimeout(5_000) {
                val source = V2WorkEventSourceObserver(
                    shifts = store.v2Shifts,
                    availabilityWindows = store.availabilityWindows,
                    shiftActuals = store.shiftActuals,
                    independentExtras = store.independentExtraWork,
                    explicitDayStatuses = store.explicitDayStatuses,
                    vacations = store.vacations,
                    medicalLeaves = store.medicalLeaves,
                    workConfiguration = store.workConfiguration,
                ).observe(WORK_DATE).first()
                projectWidget(
                    result = projectNextEvent(NOW, ZONE, source.toInput()),
                    config = WidgetProjectionConfig(
                        mode = WidgetMode.AUTOMATIC,
                        privacy = WidgetPrivacy.COMPLETE,
                        size = WidgetSize.EXPANDED,
                        configured = true,
                    ),
                )
            }
            assertEquals(
                shift.id,
                (widget.events.single().identity as NextEventIdentity.Shift).shiftId,
            )
            assertUnchanged(baseline, logicalSnapshot(sqlite), "después del Widget")

            store.close()
            storeIsOpen = false
            store = LocalDataStore.create(context, databaseName)
            storeIsOpen = true
            assertNotNull(store.shifts.getById(shift.id))
            assertUnchanged(baseline, logicalSnapshot(sqlite), "después de cerrar y reabrir Room")
        } finally {
            sqlite?.close()
            if (storeIsOpen) store.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun logicalSnapshot(database: SQLiteDatabase): LogicalDatabaseSnapshot {
        val tables = database.rawQuery(
            "SELECT name FROM sqlite_master " +
                "WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table') " +
                "ORDER BY name",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        val columnsByTable = linkedMapOf<String, List<ColumnDefinition>>()
        val countsByTable = linkedMapOf<String, Int>()
        val canonical = StringBuilder()
        tables.forEach { table ->
            val columns = readColumns(database, table)
            val rows = readTypedRows(database, table, columns)
            columnsByTable[table] = columns
            countsByTable[table] = rows.size
            canonical.append("table|").append(encodedText(table)).append('\n')
            columns.forEach { column ->
                canonical.append("column|")
                    .append(column.cid).append('|')
                    .append(encodedText(column.name)).append('|')
                    .append(encodedText(column.declaredType)).append('|')
                    .append(column.notNull).append('|')
                    .append(column.defaultValue?.let(::encodedText) ?: "N").append('|')
                    .append(column.primaryKeyPosition).append('\n')
            }
            rows.sorted().forEach { row -> canonical.append("row|").append(row).append('\n') }
        }
        val canonicalText = canonical.toString()
        return LogicalDatabaseSnapshot(
            tables = tables,
            columnsByTable = columnsByTable,
            countsByTable = countsByTable,
            canonical = canonicalText,
            sha256 = sha256(canonicalText),
            dataVersion = scalarLong(database, "PRAGMA data_version"),
        )
    }

    private fun readColumns(database: SQLiteDatabase, table: String): List<ColumnDefinition> =
        database.rawQuery("PRAGMA table_info(${quotedIdentifier(table)})", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ColumnDefinition(
                            cid = cursor.getInt(0),
                            name = cursor.getString(1),
                            declaredType = cursor.getString(2).orEmpty(),
                            notNull = cursor.getInt(3) != 0,
                            defaultValue = if (cursor.isNull(4)) null else cursor.getString(4),
                            primaryKeyPosition = cursor.getInt(5),
                        ),
                    )
                }
            }
        }

    private fun readTypedRows(
        database: SQLiteDatabase,
        table: String,
        columns: List<ColumnDefinition>,
    ): List<String> {
        val projection = columns.joinToString(", ") { quotedIdentifier(it.name) }
        return database.rawQuery(
            "SELECT $projection FROM ${quotedIdentifier(table)}",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        columns.indices.joinToString("|") { index ->
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> "N"
                                Cursor.FIELD_TYPE_INTEGER -> "I:${cursor.getLong(index)}"
                                Cursor.FIELD_TYPE_FLOAT -> "F:${java.lang.Double.toHexString(cursor.getDouble(index))}"
                                Cursor.FIELD_TYPE_STRING -> "S:${encodedText(cursor.getString(index))}"
                                Cursor.FIELD_TYPE_BLOB -> "B:${Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP)}"
                                else -> error("Tipo SQLite desconocido en $table.${columns[index].name}")
                            }
                        },
                    )
                }
            }
        }
    }

    private fun assertUnchanged(
        expected: LogicalDatabaseSnapshot,
        actual: LogicalDatabaseSnapshot,
        stage: String,
    ) {
        assertEquals("Tablas $stage", expected.tables, actual.tables)
        assertEquals("Columnas $stage", expected.columnsByTable, actual.columnsByTable)
        assertEquals("Conteos $stage", expected.countsByTable, actual.countsByTable)
        assertEquals("Huella SHA-256 $stage", expected.sha256, actual.sha256)
        assertEquals("Representación lógica $stage", expected.canonical, actual.canonical)
        assertEquals("PRAGMA data_version $stage", expected.dataVersion, actual.dataVersion)
    }

    private fun deterministicShift(): Shift {
        val startTime = LocalTime.of(16, 0)
        val endTime = LocalTime.of(18, 0)
        val start = ZonedDateTime.of(WORK_DATE, startTime, ZONE).toInstant()
        val end = ZonedDateTime.of(WORK_DATE, endTime, ZONE).toInstant()
        return Shift(
            id = SHIFT_ID,
            startAt = start,
            endAt = end,
            zoneId = ZONE,
            localStartDate = WORK_DATE,
            objectiveNameSnapshot = "Institución ficticia",
            objectiveAbbreviationSnapshot = "IFI",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = startTime,
            endTimeSnapshot = endTime,
            colorArgbSnapshot = 0xFF315DA8.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = CREATED_AT,
            updatedAt = CREATED_AT,
        )
    }

    private fun scalarLong(database: SQLiteDatabase, sql: String): Long =
        database.rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst()) { "La consulta escalar no devolvió filas: $sql" }
            cursor.getLong(0)
        }

    private fun quotedIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun encodedText(value: String): String = Base64.encodeToString(
        value.toByteArray(StandardCharsets.UTF_8),
        Base64.NO_WRAP,
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class ColumnDefinition(
        val cid: Int,
        val name: String,
        val declaredType: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val primaryKeyPosition: Int,
    )

    private data class LogicalDatabaseSnapshot(
        val tables: List<String>,
        val columnsByTable: Map<String, List<ColumnDefinition>>,
        val countsByTable: Map<String, Int>,
        val canonical: String,
        val sha256: String,
        val dataVersion: Long,
    )

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        val WORK_DATE: LocalDate = LocalDate.of(2026, 8, 25)
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val NOW: Instant = Instant.parse("2026-08-25T18:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZONE)
        val CREATED_AT: Instant = Instant.parse("2026-08-01T12:00:00Z")
        val SHIFT_ID: UUID = UUID.fromString("99000000-0000-0000-0000-000000000001")
        val EXPECTED_APPLICATION_TABLES: Set<String> = setOf(
            "availability_windows",
            "explicit_day_statuses",
            "extra_work_classes",
            "holidays",
            "independent_extra_work_records",
            "medical_leaves",
            "objectives",
            "per_period_hours_definitions",
            "per_period_hours_values",
            "recurring_occurrences",
            "recurring_plan_revisions",
            "recurring_plans",
            "schedule_photos",
            "shift_actual_records",
            "shift_extra_intervals",
            "shift_notes",
            "shift_notification_configs",
            "shift_notification_reminders",
            "shift_work_snapshots",
            "shifts",
            "vacations",
            "work_configuration_revisions",
            "work_configuration_roots",
            "work_places",
            "work_templates",
            "work_types",
            "workplace_rule_revisions",
        )
    }
}
