package com.blackatsystems.miguardia.core.domain.backup

import com.blackatsystems.miguardia.core.domain.model.CancellationEvidence
import com.blackatsystems.miguardia.core.domain.model.CancellationActor
import com.blackatsystems.miguardia.core.domain.model.ShiftSituation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import java.time.Instant
import java.util.UUID

data class BackupTableSpec(
    val name: String,
    val columns: List<String>,
    val primaryKey: List<String>,
)

/** Frozen logical allowlist for backups created while Room V5 was current. */
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
        requireValidBackupSnapshot(
            snapshot = snapshot,
            expectedRoomVersion = MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V5,
            expectedIdentityHash = MiGuardiaBackupContract.LEGACY_ROOM_IDENTITY_HASH_V5,
            expectedTables = tables,
        )
    }

    private fun spec(
        name: String,
        vararg columns: String,
        pk: List<String>,
    ): BackupTableSpec = BackupTableSpec(name, columns.toList(), pk)
}

/**
 * Frozen logical allowlist for the current Room V6 database. It intentionally excludes sqlite internals,
 * room_master_table and every database file representation.
 */
object MiGuardiaBackupSchemaV6 {
    val tables: List<BackupTableSpec> = MiGuardiaBackupSchemaV5.tables.map { table ->
        if (table.name == "objectives") {
            table.copy(columns = table.columns + listOf("weatherLatitude", "weatherLongitude"))
        } else {
            table
        }
    }

    val byName: Map<String, BackupTableSpec> = tables.associateBy(BackupTableSpec::name)

    init {
        check(tables.size == 27)
        check(byName.size == tables.size)
    }

    fun requireValid(snapshot: BackupDatabaseSnapshot) {
        requireValidBackupSnapshot(
            snapshot = snapshot,
            expectedRoomVersion = MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V6,
            expectedIdentityHash = MiGuardiaBackupContract.LEGACY_ROOM_IDENTITY_HASH_V6,
            expectedTables = tables,
        )
    }
}

/** Current V7 allowlist. Note attachments are metadata only; binary inclusion follows photoMode. */
object MiGuardiaBackupSchemaV7 {
    val tables: List<BackupTableSpec> = MiGuardiaBackupSchemaV6.tables + spec(
        "shift_note_attachments",
        "id",
        "noteId",
        "storageKey",
        "mimeType",
        "byteSize",
        "pixelWidth",
        "pixelHeight",
        "position",
        "createdAtEpochMillis",
        "updatedAtEpochMillis",
        pk = listOf("id"),
    )

    val byName: Map<String, BackupTableSpec> = tables.associateBy(BackupTableSpec::name)

    init {
        check(tables.size == 28)
        check(byName.size == tables.size)
    }

    fun requireValid(snapshot: BackupDatabaseSnapshot) {
        requireValidBackupSnapshot(
            snapshot = snapshot,
            expectedRoomVersion = MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V7,
            expectedIdentityHash = MiGuardiaBackupContract.LEGACY_ROOM_IDENTITY_HASH_V7,
            expectedTables = tables,
        )
        requireValidNoteAttachments(snapshot)
    }

    internal fun requireValidNoteAttachments(snapshot: BackupDatabaseSnapshot) {
        val notes = snapshot.table("shift_notes")
        val noteIdIndex = notes.columns.indexOf("id")
        val noteBodyIndex = notes.columns.indexOf("body")
        val noteBodies = notes.records.associate { record ->
            val id = (record.values[noteIdIndex] as? BackupValue.Text)?.value
                ?: throw InvalidBackupException("Una nota de la copia tiene una identidad inválida.")
            val body = (record.values[noteBodyIndex] as? BackupValue.Text)?.value
                ?: throw InvalidBackupException("Una nota de la copia tiene un contenido inválido.")
            id to body
        }

        val attachments = snapshot.table("shift_note_attachments")
        val attachmentNoteIdIndex = attachments.columns.indexOf("noteId")
        val positionIndex = attachments.columns.indexOf("position")
        val positionsByNote = attachments.records.groupBy { record ->
            (record.values[attachmentNoteIdIndex] as? BackupValue.Text)?.value
                ?: throw InvalidBackupException("Un adjunto de nota tiene una referencia inválida.")
        }.mapValues { (noteId, records) ->
            if (noteId !in noteBodies) {
                throw InvalidBackupException("Un adjunto de nota no pertenece a una nota de la copia.")
            }
            records.map { record ->
                (record.values[positionIndex] as? BackupValue.Integer)?.value
                    ?: throw InvalidBackupException("Un adjunto de nota tiene una posición inválida.")
            }.sorted()
        }

        positionsByNote.forEach { (_, positions) ->
            val expected = positions.indices.map(Int::toLong)
            if (positions != expected || positions.size > 30) {
                throw InvalidBackupException("Los adjuntos de una nota no conservan un orden continuo válido.")
            }
        }
        noteBodies.forEach { (noteId, body) ->
            if (body.isBlank() && positionsByNote[noteId].isNullOrEmpty()) {
                throw InvalidBackupException("La copia contiene una nota vacía sin imágenes.")
            }
        }
    }

    private fun spec(name: String, vararg columns: String, pk: List<String>) =
        BackupTableSpec(name, columns.toList(), pk)
}

/** Current V8 allowlist. Legacy ABSENT/CANCELLED rows may omit the controller table. */
object MiGuardiaBackupSchemaV8 {
    val tables: List<BackupTableSpec> = MiGuardiaBackupSchemaV7.tables + BackupTableSpec(
        name = "shift_situations",
        columns = listOf(
            "shiftId", "status", "reason", "privateNote", "cancellationActor",
            "notifiedAtEpochMillis", "evidenceStorageKey", "evidenceMimeType",
            "evidenceByteSize", "evidencePixelWidth", "evidencePixelHeight", "evidenceSha256",
            "createdAtEpochMillis", "updatedAtEpochMillis",
        ),
        primaryKey = listOf("shiftId"),
    )
    val byName: Map<String, BackupTableSpec> = tables.associateBy(BackupTableSpec::name)

    init { check(tables.size == 29 && byName.size == tables.size) }

    fun requireValid(snapshot: BackupDatabaseSnapshot) {
        requireValidBackupSnapshot(snapshot, MiGuardiaBackupContract.ROOM_VERSION,
            MiGuardiaBackupContract.ROOM_IDENTITY_HASH, tables)
        MiGuardiaBackupSchemaV7.requireValidNoteAttachments(snapshot)
        requireValidShiftSituations(snapshot)
    }

    private fun requireValidShiftSituations(snapshot: BackupDatabaseSnapshot) {
        val shifts = snapshot.table("shifts")
        val shiftIdIndex = shifts.columns.indexOf("id")
        val shiftStatusIndex = shifts.columns.indexOf("status")
        val statusById = shifts.records.associate { row ->
            (row.values[shiftIdIndex] as? BackupValue.Text)?.value to
                (row.values[shiftStatusIndex] as? BackupValue.Text)?.value
        }
        val table = snapshot.table("shift_situations")
        fun BackupRecord.value(column: String) = values[table.columns.indexOf(column)]
        table.records.forEach { row ->
            val shiftId = (row.value("shiftId") as? BackupValue.Text)?.value
                ?: throw InvalidBackupException("Una situación laboral tiene una identidad inválida.")
            runCatching { UUID.fromString(shiftId) }.getOrElse {
                throw InvalidBackupException("Una situación laboral tiene una identidad inválida.")
            }
            val status = (row.value("status") as? BackupValue.Text)?.value
            if (status !in setOf("ABSENT", "CANCELLED") || statusById[shiftId] != status) {
                throw InvalidBackupException("Una situación laboral no coincide con su jornada.")
            }
            val actor = (row.value("cancellationActor") as? BackupValue.Text)?.value
            val reason = (row.value("reason") as? BackupValue.Text)?.value
            val notice = row.value("notifiedAtEpochMillis")
            val evidenceValues = listOf(
                row.value("evidenceStorageKey"), row.value("evidenceMimeType"), row.value("evidenceByteSize"),
                row.value("evidencePixelWidth"), row.value("evidencePixelHeight"), row.value("evidenceSha256"),
            )
            if (status == "CANCELLED") {
                if (reason.isNullOrBlank() || actor !in setOf("NOT_REPORTED", "EMPLOYER", "ME", "OTHER") ||
                    notice !is BackupValue.Integer
                ) {
                    throw InvalidBackupException("Una cancelación no conserva quién canceló y cuándo se avisó.")
                }
            } else if (actor != null || notice !== BackupValue.Null || evidenceValues.any { it !== BackupValue.Null }) {
                throw InvalidBackupException("Una ausencia contiene metadatos propios de una cancelación.")
            }
            val evidence = if (evidenceValues.any { it !== BackupValue.Null }) {
                if (evidenceValues.any { it === BackupValue.Null }) {
                    throw InvalidBackupException("La evidencia de una cancelación está incompleta.")
                }
                try {
                    CancellationEvidence(
                        storageKey = (evidenceValues[0] as BackupValue.Text).value,
                        mimeType = (evidenceValues[1] as BackupValue.Text).value,
                        byteSize = (evidenceValues[2] as BackupValue.Integer).value,
                        pixelWidth = Math.toIntExact((evidenceValues[3] as BackupValue.Integer).value),
                        pixelHeight = Math.toIntExact((evidenceValues[4] as BackupValue.Integer).value),
                        sha256 = (evidenceValues[5] as BackupValue.Text).value,
                    )
                } catch (_: RuntimeException) {
                    throw InvalidBackupException("La evidencia de una cancelación es inválida.")
                }
            } else null
            try {
                ShiftSituation(
                    shiftId = UUID.fromString(shiftId),
                    status = ShiftStatus.valueOf(requireNotNull(status)),
                    reason = (row.value("reason") as? BackupValue.Text)?.value,
                    privateNote = (row.value("privateNote") as? BackupValue.Text)?.value,
                    cancellationActor = actor?.let(CancellationActor::valueOf),
                    notifiedAt = (notice as? BackupValue.Integer)?.value?.let(Instant::ofEpochMilli),
                    evidence = evidence,
                    createdAt = Instant.ofEpochMilli(
                        (row.value("createdAtEpochMillis") as? BackupValue.Integer)?.value
                            ?: throw IllegalArgumentException(),
                    ),
                    updatedAt = Instant.ofEpochMilli(
                        (row.value("updatedAtEpochMillis") as? BackupValue.Integer)?.value
                            ?: throw IllegalArgumentException(),
                    ),
                )
            } catch (_: RuntimeException) {
                throw InvalidBackupException("Una situación laboral contiene metadatos inválidos.")
            }
        }
    }
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

internal fun requireSupportedBackupSchema(snapshot: BackupDatabaseSnapshot) {
    when (snapshot.roomVersion) {
        MiGuardiaBackupContract.ROOM_VERSION -> MiGuardiaBackupSchemaV8.requireValid(snapshot)
        MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V7 -> MiGuardiaBackupSchemaV7.requireValid(snapshot)
        MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V6 -> MiGuardiaBackupSchemaV6.requireValid(snapshot)
        MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V5 -> MiGuardiaBackupSchemaV5.requireValid(snapshot)
        else -> throw InvalidBackupException("La copia usa una versión de datos no compatible.")
    }
}

fun upgradeSupportedBackupSchema(snapshot: BackupDatabaseSnapshot): BackupDatabaseSnapshot {
    requireSupportedBackupSchema(snapshot)
    if (snapshot.roomVersion == MiGuardiaBackupContract.ROOM_VERSION) return snapshot
    val v6 = if (snapshot.roomVersion == MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V5) {
        snapshot.copy(
            roomVersion = MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V6,
            roomIdentityHash = MiGuardiaBackupContract.LEGACY_ROOM_IDENTITY_HASH_V6,
            tables = snapshot.tables.map { table ->
            if (table.name == "objectives") {
                table.copy(
                    columns = table.columns + listOf("weatherLatitude", "weatherLongitude"),
                    records = table.records.map { record ->
                        record.copy(values = record.values + listOf(BackupValue.Null, BackupValue.Null))
                    },
                )
            } else {
                table
            }
        },
        )
    } else {
        snapshot
    }
    val v7 = if (v6.roomVersion == MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V7) v6 else {
        MiGuardiaBackupSchemaV6.requireValid(v6)
        v6.copy(
            roomVersion = MiGuardiaBackupContract.LEGACY_ROOM_VERSION_V7,
            roomIdentityHash = MiGuardiaBackupContract.LEGACY_ROOM_IDENTITY_HASH_V7,
            tables = v6.tables + BackupTable(
                name = "shift_note_attachments",
                columns = MiGuardiaBackupSchemaV7.byName.getValue("shift_note_attachments").columns,
                primaryKey = listOf("id"),
                records = emptyList(),
            ),
        )
    }
    MiGuardiaBackupSchemaV7.requireValid(v7)
    val upgraded = v7.copy(
        roomVersion = MiGuardiaBackupContract.ROOM_VERSION,
        roomIdentityHash = MiGuardiaBackupContract.ROOM_IDENTITY_HASH,
        tables = v7.tables + BackupTable(
            name = "shift_situations",
            columns = MiGuardiaBackupSchemaV8.byName.getValue("shift_situations").columns,
            primaryKey = listOf("shiftId"),
            records = emptyList(),
        ),
    )
    MiGuardiaBackupSchemaV8.requireValid(upgraded)
    return upgraded
}

private fun requireValidBackupSnapshot(
    snapshot: BackupDatabaseSnapshot,
    expectedRoomVersion: Int,
    expectedIdentityHash: String,
    expectedTables: List<BackupTableSpec>,
) {
    if (snapshot.roomVersion != expectedRoomVersion) {
        throw InvalidBackupException("La copia usa una versión de datos no compatible.")
    }
    if (snapshot.roomIdentityHash != expectedIdentityHash) {
        throw InvalidBackupException("La identidad Room de la copia no coincide con MiGuardia 2.0.")
    }
    if (snapshot.tables.map(BackupTable::name) != expectedTables.map(BackupTableSpec::name)) {
        throw InvalidBackupException("La copia no contiene exactamente las tablas esperadas.")
    }
    var total = 0
    snapshot.tables.zip(expectedTables).forEach { (table, expected) ->
        if (table.columns != expected.columns || table.primaryKey != expected.primaryKey) {
            throw InvalidBackupException("La tabla ${expected.name} no coincide con el contrato Room vigente.")
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
