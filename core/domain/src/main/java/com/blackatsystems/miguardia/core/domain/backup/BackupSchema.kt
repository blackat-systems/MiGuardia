package com.blackatsystems.miguardia.core.domain.backup

data class BackupTableSpec(
    val name: String,
    val columns: List<String>,
    val primaryKey: List<String>,
)

/**
 * Frozen logical allowlist for Room V5. It intentionally excludes sqlite internals,
 * room_master_table and every database file representation.
 */
object MiGuardiaBackupSchemaV5 {
    val tables: List<BackupTableSpec> = listOf(
        spec("objectives", "id", "fullName", "abbreviation", "address", "note", "isActive", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("shifts", "id", "startEpochMillis", "endEpochMillis", "zoneId", "localStartDate", "objectiveNameSnapshot", "objectiveAbbreviationSnapshot", "objectiveAddressSnapshot", "startTimeSnapshot", "endTimeSnapshot", "colorArgbSnapshot", "position", "status", "sourceObjectiveId", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("explicit_day_statuses", "localDate", "type", pk = listOf("localDate")),
        spec("medical_leaves", "id", "startDate", "endDateInclusive", "privateNote", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("holidays", "id", "localDate", "name", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("vacations", "id", "startDate", "endDateInclusive", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("schedule_photos", "id", "month", "objectiveId", "objectiveNameSnapshot", "objectiveAbbreviationSnapshot", "storageKey", "mimeType", "byteSize", "pixelWidth", "pixelHeight", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("work_configuration_roots", "timelineId", "singletonSlot", pk = listOf("timelineId")),
        spec("per_period_hours_definitions", "id", "timelineId", "periodKind", "weeklyFirstDayIso", "cycleAnchorDate", "cycleLengthDays", pk = listOf("id")),
        spec("work_configuration_revisions", "id", "timelineId", "effectiveFrom", "sector", "availabilityLabel", "hoursReferenceKind", "periodKind", "weeklyFirstDayIso", "cycleAnchorDate", "cycleLengthDays", "requiredMinutes", "perPeriodDefinitionId", "hoursReferenceStartedOn", pk = listOf("id")),
        spec("per_period_hours_values", "id", "definitionId", "windowStartInclusive", "windowEndExclusive", "requiredMinutes", pk = listOf("id")),
        spec("work_places", "id", "timelineId", "sector", "objectiveId", "isActive", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("work_types", "id", "timelineId", "sector", "name", "normalizedNameKey", "behavior", "isActive", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("work_templates", "id", "timelineId", "sector", "workPlaceId", "objectiveId", "workTypeId", "startTime", "endTime", "colorArgb", "isActive", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("workplace_rule_revisions", "id", "timelineId", "sector", "workPlaceId", "objectiveId", "effectiveFrom", "nightRuleCode", "nightStartTime", "nightEndTime", "nightDifferentTreatment", "nightShowDedicatedSummary", "weekendRuleCode", "weekendDifferentTreatment", "weekendShowDedicatedSummary", "holidayDifferentTreatment", "holidayShowDedicatedSummary", "createdAtEpochMillis", pk = listOf("id")),
        spec("recurring_plans", "id", "timelineId", "sector", "createdAtEpochMillis", pk = listOf("id")),
        spec("extra_work_classes", "id", "timelineId", "sector", "name", "normalizedNameKey", "helpsMeetHoursReference", "showDedicatedSummary", "isActive", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("shift_work_snapshots", "shiftId", "timelineId", "sector", "configurationRevisionId", "workPlaceId", "objectiveId", "templateId", "workTypeId", "workTypeNameSnapshot", "workTypeBehaviorSnapshot", pk = listOf("shiftId")),
        spec("shift_notes", "id", "shiftId", "body", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("shift_notification_configs", "shiftId", pk = listOf("shiftId")),
        spec("shift_notification_reminders", "shiftId", "leadMinutes", pk = listOf("shiftId", "leadMinutes")),
        spec("recurring_plan_revisions", "id", "planId", "revisionNumber", "effectiveFrom", "kind", "endDateInclusive", "patternKind", "weekdaysMask", "intervalCount", "monthlyOrdinal", "monthlyDayOfWeek", "templateId", "workPlaceId", "objectiveId", "workTypeId", "objectiveNameSnapshot", "objectiveAbbreviationSnapshot", "objectiveAddressSnapshot", "workTypeNameSnapshot", "workTypeBehaviorSnapshot", "startTimeSnapshot", "endTimeSnapshot", "colorArgbSnapshot", "positionSnapshot", "zoneId", "createdAtEpochMillis", pk = listOf("id")),
        spec("recurring_occurrences", "planId", "localDate", "revisionId", "shiftId", "state", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("planId", "localDate")),
        spec("shift_actual_records", "shiftId", "timelineId", "sector", "actualStartEpochMillis", "actualEndEpochMillis", "differenceReason", "explanation", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("shiftId")),
        spec("shift_extra_intervals", "id", "shiftId", "timelineId", "sector", "extraWorkClassId", "startEpochMillis", "endEpochMillis", "classNameSnapshot", "helpsMeetHoursReferenceSnapshot", "showDedicatedSummarySnapshot", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("independent_extra_work_records", "id", "timelineId", "sector", "configurationRevisionId", "workPlaceId", "objectiveId", "workTypeId", "templateId", "extraWorkClassId", "ownerLocalDate", "zoneId", "startEpochMillis", "endEpochMillis", "workPlaceNameSnapshot", "workPlaceAbbreviationSnapshot", "workPlaceAddressSnapshot", "workTypeNameSnapshot", "workTypeBehaviorSnapshot", "colorArgbSnapshot", "positionSnapshot", "classNameSnapshot", "helpsMeetHoursReferenceSnapshot", "showDedicatedSummarySnapshot", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
        spec("availability_windows", "id", "timelineId", "sector", "configurationRevisionId", "ownerLocalDate", "zoneId", "startEpochMillis", "endEpochMillis", "labelSnapshot", "createdAtEpochMillis", "updatedAtEpochMillis", pk = listOf("id")),
    )

    val byName: Map<String, BackupTableSpec> = tables.associateBy(BackupTableSpec::name)

    init {
        check(tables.size == 27)
        check(byName.size == tables.size)
    }

    fun requireValid(snapshot: BackupDatabaseSnapshot) {
        if (snapshot.roomVersion != MiGuardiaBackupContract.ROOM_VERSION) {
            throw InvalidBackupException("La copia usa una versión de datos no compatible.")
        }
        if (snapshot.roomIdentityHash != MiGuardiaBackupContract.ROOM_IDENTITY_HASH) {
            throw InvalidBackupException("La identidad Room de la copia no coincide con MiGuardia 2.0.")
        }
        if (snapshot.tables.map(BackupTable::name) != tables.map(BackupTableSpec::name)) {
            throw InvalidBackupException("La copia no contiene exactamente las 27 tablas esperadas.")
        }
        var total = 0
        snapshot.tables.zip(tables).forEach { (table, expected) ->
            if (table.columns != expected.columns || table.primaryKey != expected.primaryKey) {
                throw InvalidBackupException("La tabla ${expected.name} no coincide con el contrato Room V5.")
            }
            if (table.records.size > MiGuardiaBackupContract.MAX_TABLE_ROWS) {
                throw InvalidBackupException("La tabla ${expected.name} supera el límite seguro de filas.")
            }
            table.records.forEach { record ->
                if (record.values.size != expected.columns.size) {
                    throw InvalidBackupException("Una fila de ${expected.name} tiene una forma inválida.")
                }
            }
            total += table.records.size
            if (total > MiGuardiaBackupContract.MAX_TOTAL_ROWS) {
                throw InvalidBackupException("La copia supera el límite seguro de registros.")
            }
            var previousKey: BackupRecordKey? = null
            table.records.forEach { record ->
                val key = record.backupKey(expected)
                previousKey?.let { previous ->
                    when {
                        compareBackupRecordKeys(previous, key) == 0 ->
                            throw InvalidBackupException(
                                "La tabla ${expected.name} contiene identidades duplicadas.",
                            )
                        compareBackupRecordKeys(previous, key) > 0 ->
                            throw InvalidBackupException(
                                "La tabla ${expected.name} no conserva el orden canónico de su clave primaria.",
                            )
                    }
                }
                previousKey = key
            }
        }
        val roots = snapshot.table("work_configuration_roots")
        val timelineIndex = roots.columns.indexOf("timelineId")
        val storedTimeline = roots.records.singleOrNull()?.values?.get(timelineIndex) as? BackupValue.Text
        if (roots.records.size > 1 || storedTimeline?.value != snapshot.timelineId) {
            throw InvalidBackupException("La línea temporal declarada no coincide con los datos de la copia.")
        }
    }

    private fun spec(
        name: String,
        vararg columns: String,
        pk: List<String>,
    ): BackupTableSpec = BackupTableSpec(name, columns.toList(), pk)
}

fun BackupRecord.backupKey(spec: BackupTableSpec): BackupRecordKey = BackupRecordKey(
    table = spec.name,
    primaryKeyValues = spec.primaryKey.map { column -> values[spec.columns.indexOf(column)] },
)

internal fun compareBackupRecordKeys(first: BackupRecordKey, second: BackupRecordKey): Int {
    val tableOrder = first.table.compareTo(second.table)
    if (tableOrder != 0) return tableOrder
    first.primaryKeyValues.zip(second.primaryKeyValues).forEach { (left, right) ->
        val valueOrder = compareBackupValues(left, right)
        if (valueOrder != 0) return valueOrder
    }
    return first.primaryKeyValues.size.compareTo(second.primaryKeyValues.size)
}

private fun compareBackupValues(first: BackupValue, second: BackupValue): Int {
    val typeOrder = backupValueTypeOrder(first).compareTo(backupValueTypeOrder(second))
    if (typeOrder != 0) return typeOrder
    return when {
        first === BackupValue.Null && second === BackupValue.Null -> 0
        first is BackupValue.Text && second is BackupValue.Text -> first.value.compareTo(second.value)
        first is BackupValue.Integer && second is BackupValue.Integer -> first.value.compareTo(second.value)
        first is BackupValue.Real && second is BackupValue.Real ->
            java.lang.Double.compare(first.value, second.value)
        first is BackupValue.Binary && second is BackupValue.Binary -> compareBinary(first.value, second.value)
        else -> error("Tipos de copia incoherentes")
    }
}

private fun backupValueTypeOrder(value: BackupValue): Int = when (value) {
    BackupValue.Null -> 0
    is BackupValue.Integer -> 1
    is BackupValue.Real -> 2
    is BackupValue.Text -> 3
    is BackupValue.Binary -> 4
}

private fun compareBinary(first: ByteArray, second: ByteArray): Int {
    val shared = minOf(first.size, second.size)
    repeat(shared) { index ->
        val order = (first[index].toInt() and 0xFF).compareTo(second[index].toInt() and 0xFF)
        if (order != 0) return order
    }
    return first.size.compareTo(second.size)
}
