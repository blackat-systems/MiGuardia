package com.blackatsystems.miguardia.upgrade

import com.blackatsystems.miguardia.core.domain.backup.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Pure synthetic fixture shared by JVM and opt-in Android tests, never production. */
object UpgradeFixtureSnapshots {
    fun sourceV6(day: LocalDate, photoBytes: Long): BackupDatabaseSnapshot {
        val pastDay = day.minusDays(3)
        val automaticDay = day.plusDays(1)
        val independentDay = day.minusDays(2)
        val firstRevision = day.minusDays(90)
        val currentRevision = day.minusDays(30)
        val month = YearMonth.from(day)
        val createdAt = day.minusDays(100).atStartOfDay(ZONE).toInstant().toEpochMilli()
        fun epoch(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(ZONE).toInstant().toEpochMilli()
        val plannedStart = epoch(pastDay, 8)
        val plannedEnd = epoch(pastDay, 16)
        val independentStart = epoch(independentDay, 18)
        val independentEnd = epoch(independentDay, 20)
        val availabilityStart = epoch(day, 18)
        val availabilityEnd = epoch(day, 20)
        val rows = mapOf(
            "objectives" to row(
                text(OBJECTIVE_ID), text("Objetivo ficticio"), text("FIC"), text("Dirección ficticia"),
                nullValue(), integer(1), integer(createdAt), integer(createdAt),
                BackupValue.Real(-31.4201), BackupValue.Real(-64.1888),
            ),
            "shifts" to row(
                text(SHIFT_ID), integer(plannedStart), integer(plannedEnd), text(ZONE_ID), text(pastDay.toString()),
                text("Objetivo ficticio"), text("FIC"), text("Dirección ficticia"), text("08:00"),
                text("16:00"), integer(COLOR), text("Puesto ficticio"), text("PLANNED"),
                text(OBJECTIVE_ID), integer(createdAt), integer(createdAt),
            ),
            "explicit_day_statuses" to row(text(day.plusDays(2).toString()), text("DAY_OFF")),
            "medical_leaves" to row(
                text(MEDICAL_ID), text(day.plusDays(3).toString()), text(day.plusDays(3).toString()), text("Nota médica ficticia"),
                integer(createdAt), integer(createdAt),
            ),
            "holidays" to row(
                text(HOLIDAY_ID), text(day.plusDays(5).toString()), text("Feriado ficticio"),
                integer(createdAt), integer(createdAt),
            ),
            "vacations" to row(
                text(VACATION_ID), text(day.plusDays(4).toString()), text(day.plusDays(4).toString()),
                integer(createdAt), integer(createdAt),
            ),
            "schedule_photos" to row(
                text(PHOTO_ID), text(month.toString()), text(OBJECTIVE_ID), text("Objetivo ficticio"), text("FIC"),
                text(PHOTO_STORAGE_KEY), text("image/png"), integer(photoBytes), integer(PHOTO_WIDTH), integer(PHOTO_HEIGHT),
                integer(createdAt), integer(createdAt),
            ),
            "work_configuration_roots" to row(text(TIMELINE_ID), integer(1)),
            "per_period_hours_definitions" to row(
                text(DEFINITION_ID), text(TIMELINE_ID), text("MONTHLY"), nullValue(), nullValue(), nullValue(),
            ),
            "work_configuration_revisions" to row(
                text(CONFIGURATION_ID), text(TIMELINE_ID), text(currentRevision.toString()), text("PRIVATE_SECURITY"),
                text("PASSIVE_GUARD"), text("PER_PERIOD"), nullValue(), nullValue(), nullValue(), nullValue(),
                nullValue(), text(DEFINITION_ID), text(firstRevision.toString()),
            ),
            "per_period_hours_values" to row(
                text(PERIOD_VALUE_ID), text(DEFINITION_ID), text(month.atDay(1).toString()), text(month.plusMonths(1).atDay(1).toString()),
                integer(12_000),
            ),
            "work_places" to row(
                text(PLACE_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text(OBJECTIVE_ID),
                integer(1), integer(createdAt), integer(createdAt),
            ),
            "work_types" to row(
                text(TYPE_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text("Turno ficticio"),
                text("TURNO FICTICIO"), text("ACTIVE_WORK"), integer(1), integer(createdAt), integer(createdAt),
            ),
            "work_templates" to row(
                text(TEMPLATE_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text(PLACE_ID), text(OBJECTIVE_ID),
                text(TYPE_ID), text("08:00"), text("16:00"), integer(COLOR), integer(1),
                integer(createdAt), integer(createdAt),
            ),
            "workplace_rule_revisions" to row(
                text(RULE_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text(PLACE_ID), text(OBJECTIVE_ID),
                text(firstRevision.toString()), text("DISABLED"), nullValue(), nullValue(), nullValue(), nullValue(),
                text("NONE"), nullValue(), nullValue(), integer(0), integer(0), integer(createdAt),
            ),
            "recurring_plans" to row(
                text(PLAN_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), integer(createdAt),
            ),
            "extra_work_classes" to row(
                text(EXTRA_CLASS_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text("Extensión ficticia"),
                text("EXTENSIÓN FICTICIA"), integer(1), integer(1), integer(1),
                integer(createdAt), integer(createdAt),
            ),
            "shift_work_snapshots" to row(
                text(SHIFT_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text(CONFIGURATION_ID),
                text(PLACE_ID), text(OBJECTIVE_ID), text(TEMPLATE_ID), text(TYPE_ID),
                text("Turno ficticio"), text("ACTIVE_WORK"),
            ),
            "shift_notes" to row(
                text(NOTE_ID), text(SHIFT_ID), text("Nota ficticia"), integer(createdAt), integer(createdAt),
            ),
            "shift_notification_configs" to row(text(SHIFT_ID)),
            "shift_notification_reminders" to row(text(SHIFT_ID), integer(60)),
            "recurring_plan_revisions" to row(
                text(PLAN_REVISION_ID), text(PLAN_ID), integer(1), text(automaticDay.toString()), text("ACTIVE"),
                text(automaticDay.toString()), text("EVERY_N_DAYS"), nullValue(), integer(1), nullValue(), nullValue(),
                text(TEMPLATE_ID), text(PLACE_ID), text(OBJECTIVE_ID), text(TYPE_ID), text("Objetivo ficticio"),
                text("FIC"), text("Dirección ficticia"), text("Turno ficticio"), text("ACTIVE_WORK"),
                text("08:00"), text("16:00"), integer(COLOR), nullValue(), text(ZONE_ID), integer(createdAt),
            ),
            "recurring_occurrences" to row(
                text(PLAN_ID), text(automaticDay.toString()), text(PLAN_REVISION_ID), text(AUTOMATIC_SHIFT_ID), text("AUTOMATIC"),
                integer(createdAt), integer(createdAt),
            ),
            "shift_actual_records" to row(
                text(SHIFT_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), integer(plannedStart),
                integer(plannedEnd + HOUR_MILLIS), text("Salida posterior ficticia"), nullValue(),
                integer(createdAt), integer(createdAt),
            ),
            "shift_extra_intervals" to row(
                text(EXTRA_INTERVAL_ID), text(SHIFT_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"),
                text(EXTRA_CLASS_ID), integer(plannedEnd), integer(plannedEnd + HOUR_MILLIS),
                text("Extensión ficticia"), integer(1), integer(1), integer(createdAt), integer(createdAt),
            ),
            "independent_extra_work_records" to row(
                text(INDEPENDENT_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text(CONFIGURATION_ID),
                text(PLACE_ID), text(OBJECTIVE_ID), text(TYPE_ID), text(TEMPLATE_ID), text(EXTRA_CLASS_ID),
                text(independentDay.toString()), text(ZONE_ID), integer(independentStart), integer(independentEnd),
                text("Objetivo ficticio"), text("FIC"), text("Dirección ficticia"), text("Turno ficticio"),
                text("ACTIVE_WORK"), integer(COLOR), nullValue(), text("Extensión ficticia"), integer(1), integer(1),
                integer(createdAt), integer(createdAt),
            ),
            "availability_windows" to row(
                text(AVAILABILITY_ID), text(TIMELINE_ID), text("PRIVATE_SECURITY"), text(CONFIGURATION_ID),
                text(day.toString()), text(ZONE_ID), integer(availabilityStart), integer(availabilityEnd),
                text("Guardia pasiva"), integer(createdAt), integer(createdAt),
            ),
        )
        fun replacing(record: BackupRecord, vararg replacements: Pair<Int, BackupValue>) =
            record.copy(values = record.values.toMutableList().also { values ->
                replacements.forEach { (index, value) -> values[index] = value }
            })
        val automaticShift = replacing(
            rows.getValue("shifts"),
            0 to text(AUTOMATIC_SHIFT_ID), 1 to integer(epoch(automaticDay, 8)),
            2 to integer(epoch(automaticDay, 16)), 4 to text(automaticDay.toString()), 11 to nullValue(),
        )
        val automaticSnapshot = replacing(rows.getValue("shift_work_snapshots"), 0 to text(AUTOMATIC_SHIFT_ID))
        val historicalConfiguration = replacing(
            rows.getValue("work_configuration_revisions"),
            0 to text(HISTORICAL_CONFIGURATION_ID), 2 to text(firstRevision.toString()),
        )
        return BackupDatabaseSnapshot(
            roomVersion = MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V6,
            roomIdentityHash = MiGuardiaBackupContract.LEGACY_ROOM_IDENTITY_HASH_V6,
            timelineId = TIMELINE_ID,
            tables = MiGuardiaBackupSchemaV6.tables.map { spec ->
                val first = rows[spec.name]
                val records = when {
                    first == null -> error("Falta la fixture de ${spec.name}")
                    else -> when (spec.name) {
                    "shifts" -> listOf(first, automaticShift)
                    "shift_work_snapshots" -> listOf(first, automaticSnapshot)
                    "work_configuration_revisions" -> listOf(first, historicalConfiguration)
                    "shift_notification_configs", "shift_notification_reminders" ->
                        listOf(first, replacing(first, 0 to text(AUTOMATIC_SHIFT_ID)))
                    else -> listOf(first)
                    }
                }
                // Every multi-row fixture table differs first by a fixed-length UUID.
                // The public stable key orders these exact records; the production
                // schema/coordinator still validate their canonical representation.
                val sorted = records.sortedBy { it.backupKey(spec).stableText() }
                BackupTable(spec.name, spec.columns, spec.primaryKey, sorted)
            },
        ).also(MiGuardiaBackupSchemaV6::requireValid)
    }

    fun legacyV5(day: LocalDate, photoBytes: Long): BackupDatabaseSnapshot = sourceV6(day, photoBytes).let { source ->
        source.copy(
            roomVersion = MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V5,
            roomIdentityHash = MiGuardiaBackupContract.LEGACY_ROOM_IDENTITY_HASH_V5,
            tables = source.tables.map { table ->
                if (table.name == "objectives") table.copy(
                    columns = table.columns.dropLast(2),
                    records = table.records.map { it.copy(values = it.values.dropLast(2)) },
                ) else table
            },
        ).also(MiGuardiaBackupSchemaV5::requireValid)
    }

    fun legacyV7(day: LocalDate, photoBytes: Long): BackupDatabaseSnapshot = sourceV6(day, photoBytes).copy(
        roomVersion = MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V7,
        roomIdentityHash = MiGuardiaBackupContract.LEGACY_ROOM_IDENTITY_HASH_V7,
        tables = sourceV6(day, photoBytes).tables + emptyTable(MiGuardiaBackupSchemaV7.byName.getValue("shift_note_attachments")),
    ).also(MiGuardiaBackupSchemaV7::requireValid)

    /** Independent expected shape from the allowlist; adaptation under test is production code. */
    fun targetV8(day: LocalDate, photoBytes: Long): BackupDatabaseSnapshot = sourceV6(day, photoBytes).copy(
        roomVersion = MiGuardiaBackupContract.ROOM_VERSION,
        roomIdentityHash = MiGuardiaBackupContract.ROOM_IDENTITY_HASH,
        tables = sourceV6(day, photoBytes).tables + listOf(
            emptyTable(MiGuardiaBackupSchemaV8.byName.getValue("shift_note_attachments")),
            emptyTable(MiGuardiaBackupSchemaV8.byName.getValue("shift_situations")),
        ),
    ).also(MiGuardiaBackupSchemaV8::requireValid)

    private fun emptyTable(spec: BackupTableSpec) = BackupTable(spec.name, spec.columns, spec.primaryKey, emptyList())

    private fun row(vararg values: BackupValue): BackupRecord = BackupRecord(values.toList())
    private fun text(value: String): BackupValue = BackupValue.Text(value)
    private fun integer(value: Int): BackupValue = BackupValue.Integer(value.toLong())
    private fun integer(value: Long): BackupValue = BackupValue.Integer(value)
    private fun nullValue(): BackupValue = BackupValue.Null


    const val PHOTO_WIDTH = 800
    const val PHOTO_HEIGHT = 480
    const val AUTOMATIC_SHIFT_ID = "30000000-0000-4000-8000-000000000021"
    const val HISTORICAL_CONFIGURATION_ID = "30000000-0000-4000-8000-000000000022"
    val ZONE: ZoneId = ZoneId.of(ZONE_ID)
    const val HOUR_MILLIS = 3_600_000L
    const val COLOR = -13_408_615L
    const val OBJECTIVE_ID = "11111111-1111-4111-8111-111111111111"
    const val TIMELINE_ID = "30000000-0000-4000-8000-000000000001"
    const val CONFIGURATION_ID = "30000000-0000-4000-8000-000000000002"
    const val DEFINITION_ID = "30000000-0000-4000-8000-000000000003"
    const val PERIOD_VALUE_ID = "30000000-0000-4000-8000-000000000004"
    const val PLACE_ID = "30000000-0000-4000-8000-000000000005"
    const val TYPE_ID = "30000000-0000-4000-8000-000000000006"
    const val TEMPLATE_ID = "30000000-0000-4000-8000-000000000007"
    const val RULE_ID = "30000000-0000-4000-8000-000000000008"
    const val SHIFT_ID = "30000000-0000-4000-8000-000000000009"
    const val NOTE_ID = "30000000-0000-4000-8000-000000000010"
    const val MEDICAL_ID = "30000000-0000-4000-8000-000000000011"
    const val HOLIDAY_ID = "30000000-0000-4000-8000-000000000012"
    const val VACATION_ID = "30000000-0000-4000-8000-000000000013"
    const val PHOTO_ID = "30000000-0000-4000-8000-000000000014"
    const val PHOTO_STORAGE_KEY = "30000000-0000-4000-8000-000000000014.png"
    const val PLAN_ID = "30000000-0000-4000-8000-000000000015"
    const val PLAN_REVISION_ID = "30000000-0000-4000-8000-000000000016"
    const val EXTRA_CLASS_ID = "30000000-0000-4000-8000-000000000017"
    const val EXTRA_INTERVAL_ID = "30000000-0000-4000-8000-000000000018"
    const val INDEPENDENT_ID = "30000000-0000-4000-8000-000000000019"
    const val AVAILABILITY_ID = "30000000-0000-4000-8000-000000000020"
    const val ZONE_ID = "America/Argentina/Buenos_Aires"
}
