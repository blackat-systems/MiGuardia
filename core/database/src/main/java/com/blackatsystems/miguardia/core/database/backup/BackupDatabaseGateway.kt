package com.blackatsystems.miguardia.core.database.backup

import android.content.Context
import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseMemoryEstimator
import com.blackatsystems.miguardia.core.domain.backup.BackupMemoryBudget
import com.blackatsystems.miguardia.core.domain.backup.BackupRecord
import com.blackatsystems.miguardia.core.domain.backup.BackupTable
import com.blackatsystems.miguardia.core.domain.backup.BackupTableSpec
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupContract
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupSchemaV6
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class BackupDatabaseGateway internal constructor(
    private val context: Context,
    private val database: MiGuardiaV2Database,
    private val databaseName: String,
) {
    init {
        if (!databaseName.isCandidateDatabaseName()) {
            sweepOrphanedCandidateDatabases()
        }
    }

    suspend fun capture(
        decodedMemoryLimitBytes: Long = BackupMemoryBudget.operationalHeapBytes(),
    ): BackupDatabaseSnapshot = database.withTransaction {
        val sqlite = database.openHelper.writableDatabase
        requireExpectedSchema(sqlite)
        captureInsideTransaction(sqlite, decodedMemoryLimitBytes)
    }

    suspend fun replace(snapshot: BackupDatabaseSnapshot) {
        MiGuardiaBackupSchemaV6.requireValid(snapshot)
        database.withTransaction {
            val sqlite = database.openHelper.writableDatabase
            requireExpectedSchema(sqlite)
            replaceInsideTransaction(sqlite, snapshot)
            requireDatabaseChecks(sqlite)
            database.requireValidV2LocalData()
        }
    }

    /**
     * Holds Room's write transaction from the final current-state check until every
     * externally journaled part of the restore has been swapped. Other Room writers
     * therefore complete before the captured baseline or wait until this replacement
     * commits; they can never be silently overwritten in the middle.
     */
    suspend fun replaceWithWriteBarrier(
        expectedCurrent: BackupDatabaseSnapshot,
        replacement: BackupDatabaseSnapshot,
        decodedMemoryLimitBytes: Long = BackupMemoryBudget.operationalHeapBytes(),
        beforeReplace: suspend (BackupDatabaseSnapshot) -> Unit,
        afterReplace: suspend () -> Unit,
    ) {
        MiGuardiaBackupSchemaV6.requireValid(expectedCurrent)
        MiGuardiaBackupSchemaV6.requireValid(replacement)
        BackupMemoryBudget.requireSnapshotFits(expectedCurrent, decodedMemoryLimitBytes)
        BackupMemoryBudget.requireSnapshotFits(replacement, decodedMemoryLimitBytes)
        database.withTransaction {
            val sqlite = database.openHelper.writableDatabase
            requireExpectedSchema(sqlite)
            val current = captureInsideTransaction(sqlite, decodedMemoryLimitBytes)
            if (current != expectedCurrent) {
                throw InvalidBackupException(
                    "Los datos cambiaron después de la vista previa. Volvé a validar la copia antes de restaurar.",
                )
            }
            beforeReplace(current)
            replaceInsideTransaction(sqlite, replacement)
            requireDatabaseChecks(sqlite)
            database.requireValidV2LocalData()
            afterReplace()
            requireDatabaseChecks(sqlite)
            database.requireValidV2LocalData()
        }
    }

    suspend fun validateCandidate(
        snapshot: BackupDatabaseSnapshot,
        decodedMemoryLimitBytes: Long = BackupMemoryBudget.operationalHeapBytes(),
    ) {
        MiGuardiaBackupSchemaV6.requireValid(snapshot)
        BackupMemoryBudget.requireSnapshotFits(snapshot, decodedMemoryLimitBytes)
        val candidateName = "miguardia-backup-candidate-${UUID.randomUUID()}.db"
        val candidate = MiGuardiaV2Database.build(context, candidateName)
        try {
            val gateway = BackupDatabaseGateway(context, candidate, candidateName)
            gateway.replace(snapshot)
            val roundTrip = gateway.capture(decodedMemoryLimitBytes)
            if (roundTrip != snapshot) {
                throw InvalidBackupException("La base aislada no reproduce exactamente los datos de la copia.")
            }
        } finally {
            candidate.close()
            deleteCandidateDatabase(candidateName)
        }
    }

    /**
     * A process death skips validateCandidate's finally block. The next live gateway removes
     * only names produced by this class, including SQLite sidecars, before accepting new work.
     */
    private fun sweepOrphanedCandidateDatabases() {
        val databaseDirectory = context.getDatabasePath(databaseName).parentFile ?: return
        databaseDirectory.listFiles().orEmpty()
            .mapNotNull { file -> file.candidateDatabaseNameOrNull() }
            .toSet()
            .forEach(::deleteCandidateDatabase)
    }

    private fun deleteCandidateDatabase(candidateName: String) {
        check(candidateName.isCandidateDatabaseName()) {
            "Se intentó limpiar una base que no pertenece a la validación aislada."
        }
        val databaseFile = context.getDatabasePath(candidateName)
        val knownFiles = listOf(
            databaseFile,
            File("${databaseFile.path}-journal"),
            File("${databaseFile.path}-shm"),
            File("${databaseFile.path}-wal"),
        )
        context.deleteDatabase(candidateName)
        knownFiles.forEach { file ->
            if (file.exists() && !file.delete()) {
                throw IOException("No se pudo retirar una base aislada de validación.")
            }
        }
    }

    private fun File.candidateDatabaseNameOrNull(): String? {
        val match = CANDIDATE_FILE_PATTERN.matchEntire(name) ?: return null
        val uuid = match.groupValues[1]
        val parsed = runCatching { UUID.fromString(uuid) }.getOrNull() ?: return null
        if (!parsed.toString().equals(uuid, ignoreCase = true)) return null
        return "$CANDIDATE_DATABASE_PREFIX$uuid.db"
    }

    private fun String.isCandidateDatabaseName(): Boolean {
        val match = CANDIDATE_DATABASE_NAME_PATTERN.matchEntire(this) ?: return false
        val uuid = match.groupValues[1]
        val parsed = runCatching { UUID.fromString(uuid) }.getOrNull() ?: return false
        return parsed.toString().equals(uuid, ignoreCase = true)
    }

    suspend fun verifyLiveAfterReopen(
        expected: BackupDatabaseSnapshot,
        decodedMemoryLimitBytes: Long = BackupMemoryBudget.operationalHeapBytes(),
    ) {
        BackupMemoryBudget.requireSnapshotFits(expected, decodedMemoryLimitBytes)
        val reopened = MiGuardiaV2Database.build(context, databaseName)
        try {
            val gateway = BackupDatabaseGateway(context, reopened, databaseName)
            val actual = gateway.capture(decodedMemoryLimitBytes)
            if (actual != expected) {
                throw InvalidBackupException("La base reabierta no coincide con la restauración aplicada.")
            }
            reopened.withTransaction {
                requireDatabaseChecks(reopened.openHelper.writableDatabase)
                reopened.requireValidV2LocalData()
            }
        } finally {
            reopened.close()
        }
    }

    fun withoutSchedulePhotos(snapshot: BackupDatabaseSnapshot): BackupDatabaseSnapshot = snapshot.copy(
        tables = snapshot.tables.map { table ->
            if (table.name == "schedule_photos") table.copy(records = emptyList()) else table
        },
    ).also(MiGuardiaBackupSchemaV6::requireValid)

    private fun captureInsideTransaction(
        sqlite: SupportSQLiteDatabase,
        decodedMemoryLimitBytes: Long,
    ): BackupDatabaseSnapshot {
        val estimator = BackupDatabaseMemoryEstimator(decodedMemoryLimitBytes)
        estimator.consumeSnapshotObject()
        estimator.consumeString(MiGuardiaBackupContract.ROOM_IDENTITY_HASH)
        estimator.consumeTableReferences(MiGuardiaBackupSchemaV6.tables.size)
        var totalRows = 0
        val tables = MiGuardiaBackupSchemaV6.tables.map { spec ->
            estimator.consumeTableMetadata(spec.name, spec.columns, spec.primaryKey)
            val query = buildString {
                append("SELECT ")
                append(spec.columns.joinToString(",") { it.quoted() })
                append(" FROM ")
                append(spec.name.quoted())
                append(" ORDER BY ")
                append(spec.primaryKey.joinToString(",") { it.quoted() })
            }
            val records = sqlite.query(query).use { cursor ->
                buildList {
                    var tableRows = 0
                    while (cursor.moveToNext()) {
                        tableRows += 1
                        totalRows += 1
                        if (tableRows > MiGuardiaBackupContract.MAX_TABLE_ROWS ||
                            totalRows > MiGuardiaBackupContract.MAX_TOTAL_ROWS
                        ) {
                            throw InvalidBackupException("La base local supera el límite seguro de registros.")
                        }
                        estimator.consumeRecordObject(spec.columns.size)
                        val values = List(spec.columns.size) { index ->
                            cursor.backupValue(index, estimator)
                        }
                        add(BackupRecord(values))
                    }
                }
            }
            BackupTable(spec.name, spec.columns, spec.primaryKey, records)
        }
        val roots = tables.single { it.name == "work_configuration_roots" }
        val timelineIndex = roots.columns.indexOf("timelineId")
        val timelineId = (roots.records.singleOrNull()?.values?.get(timelineIndex) as? BackupValue.Text)?.value
        timelineId?.let(estimator::consumeString)
        return BackupDatabaseSnapshot(timelineId = timelineId, tables = tables).also(
            MiGuardiaBackupSchemaV6::requireValid,
        )
    }

    private suspend fun replaceInsideTransaction(
        sqlite: SupportSQLiteDatabase,
        snapshot: BackupDatabaseSnapshot,
    ) {
        sqlite.execSQL("PRAGMA defer_foreign_keys = ON")
        MiGuardiaBackupSchemaV6.tables.asReversed().forEach { spec ->
            sqlite.execSQL("DELETE FROM ${spec.name.quoted()}")
        }
        MiGuardiaBackupSchemaV6.tables.forEach { spec ->
            val table = snapshot.table(spec.name)
            val contracts = readColumnContracts(sqlite, spec)
            val statement = sqlite.compileStatement(insertSql(spec))
            table.records.forEach { record ->
                requireSemanticRecord(spec, record)
                record.values.forEachIndexed { index, value ->
                    requireCompatibleValue(spec, contracts[index], value)
                    statement.bind(index + 1, value)
                }
                statement.executeInsert()
                statement.clearBindings()
            }
        }
    }

    private fun requireExpectedSchema(sqlite: SupportSQLiteDatabase) {
        val identity = sqlite.query(
            "SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1",
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (identity != MiGuardiaBackupContract.ROOM_IDENTITY_HASH) {
            throw InvalidBackupException("La identidad de la base Room V6 no coincide.")
        }
        MiGuardiaBackupSchemaV6.tables.forEach { spec -> readColumnContracts(sqlite, spec) }
    }

    private fun readColumnContracts(
        sqlite: SupportSQLiteDatabase,
        spec: BackupTableSpec,
    ): List<ColumnContract> {
        val contracts = sqlite.query("PRAGMA table_info(${spec.name.quoted()})").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            val pkIndex = cursor.getColumnIndexOrThrow("pk")
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ColumnContract(
                            name = cursor.getString(nameIndex),
                            affinity = cursor.getString(typeIndex).uppercase(),
                            nullable = cursor.getInt(notNullIndex) == 0,
                            primaryKeyPosition = cursor.getInt(pkIndex),
                        ),
                    )
                }
            }
        }
        if (contracts.map(ColumnContract::name) != spec.columns) {
            throw InvalidBackupException("La forma real de ${spec.name} no coincide con Room V6.")
        }
        val primaryKey = contracts.filter { it.primaryKeyPosition > 0 }
            .sortedBy(ColumnContract::primaryKeyPosition)
            .map(ColumnContract::name)
        if (primaryKey != spec.primaryKey) {
            throw InvalidBackupException("La clave primaria de ${spec.name} no coincide con Room V6.")
        }
        return contracts
    }

    private fun requireCompatibleValue(
        spec: BackupTableSpec,
        column: ColumnContract,
        value: BackupValue,
    ) {
        if (value == BackupValue.Null) {
            if (!column.nullable) {
                throw InvalidBackupException("${spec.name}.${column.name} no admite valores vacíos.")
            }
            return
        }
        val valid = when (column.affinity) {
            "TEXT" -> value is BackupValue.Text
            "INTEGER" -> value is BackupValue.Integer
            "REAL" -> value is BackupValue.Real || value is BackupValue.Integer
            "BLOB" -> value is BackupValue.Binary
            else -> false
        }
        if (!valid) throw InvalidBackupException("${spec.name}.${column.name} tiene un tipo inválido.")
    }

    private fun requireSemanticRecord(spec: BackupTableSpec, record: BackupRecord) {
        fun value(name: String): BackupValue = record.values[spec.columns.indexOf(name)]
        spec.columns.forEach { column ->
            val stored = value(column)
            if (stored !is BackupValue.Text) return@forEach
            when {
                column == "month" -> parseOrReject("mes") { YearMonth.parse(stored.value) }
                column == "zoneId" -> parseOrReject("zona horaria") { ZoneId.of(stored.value) }
                column.contains("Date", ignoreCase = true) || column == "effectiveFrom" ||
                    column.startsWith("windowStart") || column.startsWith("windowEnd") ->
                    parseOrReject("fecha") { LocalDate.parse(stored.value) }
                column in localTimeColumns -> parseOrReject("hora") { LocalTime.parse(stored.value) }
                isUuidColumn(column) -> parseOrReject("UUID") {
                    check(UUID.fromString(stored.value).toString() == stored.value.lowercase())
                }
            }
        }
        booleanColumns[spec.name].orEmpty().forEach { name ->
            val stored = value(name)
            if (stored != BackupValue.Null && (stored !is BackupValue.Integer || stored.value !in 0L..1L)) {
                throw InvalidBackupException("${spec.name} contiene un booleano inválido.")
            }
        }
        if (spec.name == "explicit_day_statuses") {
            requireEnumText(value("type"), setOf("DAY_OFF", "UNDEFINED"), spec.name)
        }
        if (spec.name == "shifts") {
            requireEnumText(value("status"), setOf("PLANNED", "CANCELLED", "ABSENT"), spec.name)
        }
        if (spec.name == "schedule_photos") {
            val mime = (value("mimeType") as? BackupValue.Text)?.value
            val size = (value("byteSize") as? BackupValue.Integer)?.value ?: -1
            val width = (value("pixelWidth") as? BackupValue.Integer)?.value ?: -1
            val height = (value("pixelHeight") as? BackupValue.Integer)?.value ?: -1
            if (mime !in setOf("image/jpeg", "image/png", "image/webp") ||
                size !in 1..MiGuardiaBackupContract.MAX_SINGLE_PHOTO_BYTES ||
                width !in 1..65_535 || height !in 1..65_535
            ) {
                throw InvalidBackupException("Los metadatos de una fotografía son inválidos.")
            }
        }
    }

    private fun requireEnumText(value: BackupValue, allowed: Set<String>, table: String) {
        if ((value as? BackupValue.Text)?.value !in allowed) {
            throw InvalidBackupException("$table contiene un valor enumerado inválido.")
        }
    }

    private val localTimeColumns = setOf(
        "startTimeSnapshot",
        "endTimeSnapshot",
        "startTime",
        "endTime",
        "nightStartTime",
        "nightEndTime",
    )

    private inline fun parseOrReject(kind: String, block: () -> Unit) {
        try {
            block()
        } catch (error: RuntimeException) {
            throw InvalidBackupException("La copia contiene un valor de $kind inválido.", error)
        }
    }

    private fun requireDatabaseChecks(sqlite: SupportSQLiteDatabase) {
        val integrity = sqlite.query("PRAGMA integrity_check").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        if (integrity != listOf("ok")) {
            throw InvalidBackupException("La base restaurada no supera integrity_check.")
        }
        val foreignKeyErrors = sqlite.query("PRAGMA foreign_key_check").use { cursor -> cursor.count }
        if (foreignKeyErrors != 0) {
            throw InvalidBackupException("La base restaurada contiene relaciones huérfanas.")
        }
        val invalidSituationRanges = sqlite.longScalar(
            """
            SELECT
                (SELECT COUNT(*) FROM medical_leaves WHERE startDate > endDateInclusive) +
                (SELECT COUNT(*) FROM vacations WHERE startDate > endDateInclusive)
            """.trimIndent(),
        )
        if (invalidSituationRanges != 0L) {
            throw InvalidBackupException("La copia contiene un período de situación inválido.")
        }
        val overlappingSituations = sqlite.longScalar(
            """
            SELECT
                (SELECT COUNT(*) FROM medical_leaves a JOIN medical_leaves b
                    ON a.id < b.id AND a.startDate <= b.endDateInclusive AND b.startDate <= a.endDateInclusive) +
                (SELECT COUNT(*) FROM vacations a JOIN vacations b
                    ON a.id < b.id AND a.startDate <= b.endDateInclusive AND b.startDate <= a.endDateInclusive) +
                (SELECT COUNT(*) FROM medical_leaves m JOIN vacations v
                    ON m.startDate <= v.endDateInclusive AND v.startDate <= m.endDateInclusive)
            """.trimIndent(),
        )
        if (overlappingSituations != 0L) {
            throw InvalidBackupException("La copia contiene situaciones especiales superpuestas.")
        }
    }

    private fun SupportSQLiteDatabase.longScalar(sql: String): Long = query(sql).use { cursor ->
        if (!cursor.moveToFirst()) 0L else cursor.getLong(0)
    }

    private fun Cursor.backupValue(
        index: Int,
        estimator: BackupDatabaseMemoryEstimator,
    ): BackupValue = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> BackupValue.Null
        Cursor.FIELD_TYPE_INTEGER -> {
            estimator.consumeValueObject()
            BackupValue.Integer(getLong(index))
        }
        Cursor.FIELD_TYPE_FLOAT -> {
            estimator.consumeValueObject()
            BackupValue.Real(getDouble(index))
        }
        Cursor.FIELD_TYPE_STRING -> {
            val value = getString(index)
            estimator.consumeValueObject()
            estimator.consumeString(value)
            BackupValue.Text(value)
        }
        Cursor.FIELD_TYPE_BLOB -> {
            val value = getBlob(index)
            estimator.consumeBinaryValue(value.size)
            BackupValue.Binary(value)
        }
        else -> throw InvalidBackupException("Room devolvió un tipo de celda desconocido.")
    }

    private fun SupportSQLiteStatement.bind(index: Int, value: BackupValue) = when (value) {
        BackupValue.Null -> bindNull(index)
        is BackupValue.Text -> bindString(index, value.value)
        is BackupValue.Integer -> bindLong(index, value.value)
        is BackupValue.Real -> bindDouble(index, value.value)
        is BackupValue.Binary -> bindBlob(index, value.value)
    }

    private fun insertSql(spec: BackupTableSpec): String = buildString {
        append("INSERT INTO ")
        append(spec.name.quoted())
        append(" (")
        append(spec.columns.joinToString(",") { it.quoted() })
        append(") VALUES (")
        append(List(spec.columns.size) { "?" }.joinToString(","))
        append(')')
    }

    private fun String.quoted(): String {
        check(matches(IDENTIFIER))
        return "`$this`"
    }

    private fun isUuidColumn(column: String): Boolean =
        column == "id" || column.endsWith("Id") || column == "timelineId"

    private data class ColumnContract(
        val name: String,
        val affinity: String,
        val nullable: Boolean,
        val primaryKeyPosition: Int,
    )

    private companion object {
        const val CANDIDATE_DATABASE_PREFIX = "miguardia-backup-candidate-"
        val CANDIDATE_DATABASE_NAME_PATTERN =
            Regex("^${CANDIDATE_DATABASE_PREFIX}([0-9a-fA-F-]{36})\\.db$")
        val CANDIDATE_FILE_PATTERN =
            Regex("^${CANDIDATE_DATABASE_PREFIX}([0-9a-fA-F-]{36})\\.db(?:-(?:journal|shm|wal))?$")
        val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val booleanColumns = mapOf(
            "objectives" to setOf("isActive"),
            "work_places" to setOf("isActive"),
            "work_types" to setOf("isActive"),
            "work_templates" to setOf("isActive"),
            "workplace_rule_revisions" to setOf(
                "nightDifferentTreatment",
                "nightShowDedicatedSummary",
                "weekendDifferentTreatment",
                "weekendShowDedicatedSummary",
                "holidayDifferentTreatment",
                "holidayShowDedicatedSummary",
            ),
            "extra_work_classes" to setOf("helpsMeetHoursReference", "showDedicatedSummary", "isActive"),
            "shift_extra_intervals" to setOf(
                "helpsMeetHoursReferenceSnapshot",
                "showDedicatedSummarySnapshot",
            ),
            "independent_extra_work_records" to setOf(
                "helpsMeetHoursReferenceSnapshot",
                "showDedicatedSummarySnapshot",
            ),
        )
    }
}
