package com.blackatsystems.miguardia.core.domain.backup

import java.security.MessageDigest
import java.time.LocalDate
import java.util.ArrayDeque

object BackupComparator {
    fun compare(
        current: BackupDatabaseSnapshot,
        incoming: BackupDatabaseSnapshot,
        currentPreferences: List<BackupPreference> = emptyList(),
        incomingPreferences: List<BackupPreference> = emptyList(),
    ): BackupComparison {
        MiGuardiaBackupSchemaV6.requireValid(current)
        MiGuardiaBackupSchemaV6.requireValid(incoming)
        val timelineCompatible = current.timelineId == null || current.timelineId == incoming.timelineId
        val newDatabaseKeys = mutableSetOf<BackupRecordKey>()
        var identicalRecords = 0
        val conflicts = ConflictCollector()
        MiGuardiaBackupSchemaV6.tables.forEach { spec ->
            val currentTable = current.table(spec.name)
            val incomingTable = incoming.table(spec.name)
            val currentByKey = currentTable.records.associateBy { it.backupKey(spec) }
            val incomingByKey = incomingTable.records.associateBy { it.backupKey(spec) }
            incomingByKey.forEach { (key, record) ->
                val existing = currentByKey[key]
                when {
                    existing == null -> newDatabaseKeys += key
                    existing == record -> identicalRecords++
                    else -> if (conflicts.accepting) {
                        conflicts.add(sameIdentityConflict(spec, key, existing, record))
                    }
                }
            }
            naturalAndTemporalConflicts(
                spec,
                currentTable,
                incomingTable,
                currentByKey,
                conflicts,
            )
        }
        crossSituationConflicts(current, incoming, conflicts)
        var newRecords = if (conflicts.mergeBlockedReason == null) {
            newDatabaseKeys.count { key -> key !in conflicts.conflictingIncomingKeys }
        } else {
            0
        }
        val currentPrefs = currentPreferences.associateBy(BackupPreference::key)
        incomingPreferences.forEach { incomingPreference ->
            val currentPreference = currentPrefs[incomingPreference.key]
            when {
                currentPreference == null -> newRecords++
                currentPreference == incomingPreference -> identicalRecords++
                incomingPreference.key == MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE ->
                    newRecords++
                else -> if (conflicts.accepting) {
                    conflicts.add(preferenceConflict(currentPreference, incomingPreference))
                }
            }
        }
        dismissedEventKeysMergeBlockReason(currentPreferences, incomingPreferences)?.let { reason ->
            conflicts.block(
                reason = reason,
                table = PREFERENCES_TABLE,
                keyText = MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE,
            )
        }
        if (conflicts.mergeBlockedReason != null) newRecords = 0
        return BackupComparison(
            timelineCompatible = timelineCompatible,
            destinationEmpty = current.isEmpty,
            newRecords = newRecords,
            identicalRecords = identicalRecords,
            conflicts = conflicts.result(),
            mergeBlockedReason = conflicts.mergeBlockedReason,
        )
    }

    fun mergeDatabase(
        current: BackupDatabaseSnapshot,
        incoming: BackupDatabaseSnapshot,
        conflicts: List<BackupConflict>,
        resolutions: List<ResolvedBackupConflict>,
    ): BackupDatabaseSnapshot {
        MiGuardiaBackupSchemaV6.requireValid(current)
        MiGuardiaBackupSchemaV6.requireValid(incoming)
        if (current.timelineId != null && current.timelineId != incoming.timelineId) {
            throw UnresolvedBackupConflictException(
                "Sólo se pueden combinar datos vacíos o pertenecientes a la misma línea temporal.",
            )
        }
        val freshComparison = compare(current, incoming)
        val databaseConflicts = freshComparison.conflicts.filter { it.table != PREFERENCES_TABLE }
        val suppliedDatabaseConflicts = conflicts.filter { it.table != PREFERENCES_TABLE }
        requireFreshConflicts(databaseConflicts, suppliedDatabaseConflicts)
        if (databaseConflicts.any { it.classification == BackupRecordClassification.INVALID }) {
            throw UnresolvedBackupConflictException(
                freshComparison.mergeBlockedReason
                    ?: databaseConflicts.first { it.classification == BackupRecordClassification.INVALID }.summary,
            )
        }
        val databaseConflictIds = databaseConflicts.mapTo(hashSetOf(), BackupConflict::id)
        val resolutionById = requireCompleteResolutions(
            databaseConflicts,
            resolutions.filter { resolution -> resolution.conflictId in databaseConflictIds },
        )
        val removalsFromCurrent = mutableSetOf<BackupRecordKey>()
        val removalsFromIncoming = mutableSetOf<BackupRecordKey>()
        val keptFromCurrent = mutableSetOf<BackupRecordKey>()
        val keptFromIncoming = mutableSetOf<BackupRecordKey>()
        databaseConflicts.forEach { conflict ->
            when (resolutionById.getValue(conflict.id)) {
                BackupConflictResolution.KEEP_CURRENT -> {
                    keptFromCurrent += conflict.currentKey
                    removalsFromIncoming += conflict.incomingKey
                }
                BackupConflictResolution.USE_BACKUP -> {
                    removalsFromCurrent += conflict.currentKey
                    keptFromIncoming += conflict.incomingKey
                }
                BackupConflictResolution.KEEP_BOTH -> {
                    if (!conflict.keepBothAllowed) {
                        throw UnresolvedBackupConflictException(
                            "El conflicto ${conflict.id} no admite conservar ambos registros.",
                        )
                    }
                    keptFromCurrent += conflict.currentKey
                    keptFromIncoming += conflict.incomingKey
                }
            }
        }
        val aggregateRemovalsFromCurrent = current.expandAggregateRemovals(removalsFromCurrent)
        val aggregateRemovalsFromIncoming = incoming.expandAggregateRemovals(removalsFromIncoming)
        val survivingIncomingKeys = incoming.allRecordKeys() - aggregateRemovalsFromIncoming
        val hiddenCurrentRemovals = (aggregateRemovalsFromCurrent - removalsFromCurrent)
            .filterNotTo(linkedSetOf()) { key -> key in survivingIncomingKeys }
        if (hiddenCurrentRemovals.isNotEmpty()) {
            val impact = hiddenCurrentRemovals.groupingBy(BackupRecordKey::table)
                .eachCount()
                .toSortedMap()
                .entries
                .joinToString { (table, count) -> "$table: $count" }
            throw UnresolvedBackupConflictException(
                "Usar la copia eliminaría ${hiddenCurrentRemovals.size} registros actuales relacionados " +
                    "que no fueron mostrados como conflictos ($impact). Para evitar una pérdida silenciosa, " +
                    "elegí conservar tus datos actuales o usá Reemplazar todo.",
            )
        }
        if (keptFromCurrent.any { it in aggregateRemovalsFromCurrent } ||
            keptFromIncoming.any { it in aggregateRemovalsFromIncoming }
        ) {
            throw UnresolvedBackupConflictException(
                "Las resoluciones elegidas se contradicen entre sí. Revisá los conflictos relacionados.",
            )
        }
        val tables = MiGuardiaBackupSchemaV6.tables.map { spec ->
            val currentRecords = current.table(spec.name).records
                .filterNot { it.backupKey(spec) in aggregateRemovalsFromCurrent }
            val incomingRecords = incoming.table(spec.name).records
                .filterNot { it.backupKey(spec) in aggregateRemovalsFromIncoming }
            val selected = linkedMapOf<BackupRecordKey, BackupRecord>()
            currentRecords.forEach { selected[it.backupKey(spec)] = it }
            incomingRecords.forEach { record ->
                val key = record.backupKey(spec)
                val existing = selected[key]
                if (existing == null || existing == record || key in aggregateRemovalsFromCurrent) {
                    selected[key] = record
                } else {
                    throw UnresolvedBackupConflictException("Quedó un conflicto de identidad sin resolver.")
                }
            }
            BackupTable(
                name = spec.name,
                columns = spec.columns,
                primaryKey = spec.primaryKey,
                records = selected.entries
                    .sortedWith { first, second -> compareBackupRecordKeys(first.key, second.key) }
                    .map(Map.Entry<BackupRecordKey, BackupRecord>::value),
            )
        }
        val roots = tables.single { it.name == "work_configuration_roots" }
        val timelineIndex = roots.columns.indexOf("timelineId")
        val timeline = (roots.records.singleOrNull()?.values?.get(timelineIndex) as? BackupValue.Text)?.value
        return BackupDatabaseSnapshot(timelineId = timeline, tables = tables).also(
            MiGuardiaBackupSchemaV6::requireValid,
        )
    }

    fun mergePreferences(
        current: List<BackupPreference>,
        incoming: List<BackupPreference>,
        conflicts: List<BackupConflict>,
        resolutions: List<ResolvedBackupConflict>,
    ): List<BackupPreference> {
        if (conflicts.any { it.classification == BackupRecordClassification.INVALID }) {
            throw UnresolvedBackupConflictException(
                conflicts.first { it.classification == BackupRecordClassification.INVALID }.summary,
            )
        }
        val preferenceConflicts = conflicts.filter { it.table == PREFERENCES_TABLE }
        val preferenceConflictIds = preferenceConflicts.mapTo(hashSetOf(), BackupConflict::id)
        val resolved = requireCompleteResolutions(
            preferenceConflicts,
            resolutions.filter { resolution -> resolution.conflictId in preferenceConflictIds },
        )
        val currentByKey = current.associateBy(BackupPreference::key).toMutableMap()
        incoming.forEach { preference ->
            val existing = currentByKey[preference.key]
            when {
                existing == null || existing == preference -> currentByKey[preference.key] = preference
                preference.key == MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE -> {
                    if (existing.type != BackupPreferenceType.TEXT_LIST ||
                        preference.type != BackupPreferenceType.TEXT_LIST
                    ) {
                        throw UnresolvedBackupConflictException(
                            "Las identidades de avisos ocultados tienen un formato inválido.",
                        )
                    }
                    val mergedValues = (existing.values + preference.values).distinct().sorted()
                    if (mergedValues.size > MiGuardiaBackupContract.MAX_DISMISSED_EVENT_KEYS) {
                        throw UnresolvedBackupConflictException(DISMISSED_EVENT_KEYS_LIMIT_REASON)
                    }
                    currentByKey[preference.key] = preference.copy(values = mergedValues)
                }
                else -> {
                    val conflict = preferenceConflicts.single { it.incomingKey.primaryKeyValues == listOf(BackupValue.Text(preference.key)) }
                    when (resolved.getValue(conflict.id)) {
                        BackupConflictResolution.KEEP_CURRENT -> Unit
                        BackupConflictResolution.USE_BACKUP -> currentByKey[preference.key] = preference
                        BackupConflictResolution.KEEP_BOTH -> throw UnresolvedBackupConflictException(
                            "Una preferencia única no admite conservar ambos valores.",
                        )
                    }
                }
            }
        }
        return currentByKey.values.sortedBy(BackupPreference::key).also(::requireDismissedEventKeysWithinLimit)
    }

    private fun naturalAndTemporalConflicts(
        spec: BackupTableSpec,
        current: BackupTable,
        incoming: BackupTable,
        currentByKey: Map<BackupRecordKey, BackupRecord>,
        conflicts: ConflictCollector,
    ) {
        if (!conflicts.accepting) return
        val incomingChangedOrNew = incoming.records.filter { incomingRecord ->
            currentByKey[incomingRecord.backupKey(spec)] != incomingRecord
        }
        if (incomingChangedOrNew.isEmpty() || current.records.isEmpty()) return
        naturalUniqueColumns[spec.name].orEmpty().forEach { uniqueColumns ->
            if (!conflicts.accepting) return
            val currentByNatural = current.records.mapNotNull { record ->
                record.naturalKeyOrNull(spec, uniqueColumns)?.let { key -> key to record }
            }.toMap()
            incomingChangedOrNew.forEach { incomingRecord ->
                if (!conflicts.accepting) return@forEach
                val naturalKey = incomingRecord.naturalKeyOrNull(spec, uniqueColumns) ?: return@forEach
                val currentRecord = currentByNatural[naturalKey] ?: return@forEach
                if (currentRecord.backupKey(spec) == incomingRecord.backupKey(spec)) return@forEach
                conflicts.add(overlapConflict(
                    spec,
                    currentRecord,
                    incomingRecord,
                    "La identidad funcional única ya existe con otro identificador.",
                    keepBothAllowed = false,
                ))
            }
        }
        temporalColumns[spec.name]?.let { (startName, endName) ->
            val currentIndexes = current.records.buildIntervalIndexes(spec, startName, endName)
            incomingChangedOrNew.forEach { incomingRecord ->
                if (!conflicts.accepting) return@forEach
                val incomingInterval = incomingRecord.longInterval(spec, startName, endName) ?: return@forEach
                currentIndexes[incomingRecord.overlapScopeKey(spec)]?.query(
                    incomingInterval.first,
                    incomingInterval.second,
                ) { currentRecord ->
                    if (currentRecord.backupKey(spec) == incomingRecord.backupKey(spec)) {
                        return@query conflicts.accepting
                    }
                    conflicts.add(
                        overlapConflict(
                            spec,
                            currentRecord,
                            incomingRecord,
                            "Los períodos se superponen y requieren una decisión consciente.",
                            keepBothAllowed = spec.name in keepBothTemporalTables,
                        ),
                    )
                    conflicts.accepting
                }
            }
        }
        dateRangeColumns[spec.name]?.let { (startName, endName) ->
            val currentIndex = IntervalIndex.from(
                current.records.mapNotNull { record ->
                    record.dateInterval(spec, startName, endName)?.let { (start, end) ->
                        IndexedInterval(record, start, end)
                    }
                },
            )
            incomingChangedOrNew.forEach { incomingRecord ->
                if (!conflicts.accepting) return@forEach
                val incomingRange = incomingRecord.dateInterval(spec, startName, endName) ?: return@forEach
                currentIndex.query(incomingRange.first, incomingRange.second) { currentRecord ->
                    if (currentRecord.backupKey(spec) == incomingRecord.backupKey(spec)) {
                        return@query conflicts.accepting
                    }
                    conflicts.add(
                        overlapConflict(
                            spec,
                            currentRecord,
                            incomingRecord,
                            "Los rangos de fechas se superponen y requieren una decisión consciente.",
                            keepBothAllowed = false,
                        ),
                    )
                    conflicts.accepting
                }
            }
        }
    }

    private fun sameIdentityConflict(
        spec: BackupTableSpec,
        key: BackupRecordKey,
        current: BackupRecord,
        incoming: BackupRecord,
    ): BackupConflict = BackupConflict(
        id = conflictId("identity", key.stableText(), key.stableText()),
        classification = BackupRecordClassification.CONFLICT,
        table = spec.name,
        currentKey = key,
        incomingKey = key,
        keepBothAllowed = false,
        summary = "El mismo registro tiene contenidos diferentes.",
        currentDescription = current.safeDescription(spec),
        incomingDescription = incoming.safeDescription(spec),
    )

    private fun crossSituationConflicts(
        current: BackupDatabaseSnapshot,
        incoming: BackupDatabaseSnapshot,
        conflicts: ConflictCollector,
    ) {
        addCrossSituationConflicts(
            current = current.table("vacations"),
            incoming = incoming.table("medical_leaves"),
            conflicts = conflicts,
        )
        addCrossSituationConflicts(
            current = current.table("medical_leaves"),
            incoming = incoming.table("vacations"),
            conflicts = conflicts,
        )
    }

    private fun addCrossSituationConflicts(
        current: BackupTable,
        incoming: BackupTable,
        conflicts: ConflictCollector,
    ) {
        if (!conflicts.accepting) return
        val currentSpec = MiGuardiaBackupSchemaV6.byName.getValue(current.name)
        val incomingSpec = MiGuardiaBackupSchemaV6.byName.getValue(incoming.name)
        val currentIndex = IntervalIndex.from(
            current.records.mapNotNull { record ->
                record.dateInterval(currentSpec, "startDate", "endDateInclusive")?.let { (start, end) ->
                    IndexedInterval(record, start, end)
                }
            },
        )
        incoming.records.forEach { incomingRecord ->
            if (!conflicts.accepting) return@forEach
            val incomingRange = incomingRecord.dateInterval(
                incomingSpec,
                "startDate",
                "endDateInclusive",
            ) ?: return@forEach
            currentIndex.query(incomingRange.first, incomingRange.second) { currentRecord ->
                val currentKey = currentRecord.backupKey(currentSpec)
                val incomingKey = incomingRecord.backupKey(incomingSpec)
                conflicts.add(
                    BackupConflict(
                        id = conflictId("situation", currentKey.stableText(), incomingKey.stableText()),
                        classification = BackupRecordClassification.CONFLICT,
                        table = "medical_leaves/vacations",
                        currentKey = currentKey,
                        incomingKey = incomingKey,
                        keepBothAllowed = false,
                        summary = "Una fecha no puede pertenecer a vacaciones y carpeta médica a la vez.",
                        currentDescription = currentRecord.safeDescription(currentSpec),
                        incomingDescription = incomingRecord.safeDescription(incomingSpec),
                    ),
                )
                conflicts.accepting
            }
        }
    }

    private fun overlapConflict(
        spec: BackupTableSpec,
        current: BackupRecord,
        incoming: BackupRecord,
        summary: String,
        keepBothAllowed: Boolean,
    ): BackupConflict {
        val currentKey = current.backupKey(spec)
        val incomingKey = incoming.backupKey(spec)
        return BackupConflict(
            id = conflictId("overlap", currentKey.stableText(), incomingKey.stableText()),
            classification = BackupRecordClassification.SIGNIFICANT_OVERLAP,
            table = spec.name,
            currentKey = currentKey,
            incomingKey = incomingKey,
            keepBothAllowed = keepBothAllowed,
            summary = summary,
            currentDescription = current.safeDescription(spec),
            incomingDescription = incoming.safeDescription(spec),
        )
    }

    private fun preferenceConflict(current: BackupPreference, incoming: BackupPreference): BackupConflict {
        val key = incoming.key
        val recordKey = BackupRecordKey(PREFERENCES_TABLE, listOf(BackupValue.Text(key)))
        return BackupConflict(
            id = conflictId("preference", recordKey.stableText(), recordKey.stableText()),
            classification = BackupRecordClassification.CONFLICT,
            table = PREFERENCES_TABLE,
            currentKey = recordKey,
            incomingKey = recordKey,
            keepBothAllowed = false,
            summary = "${preferenceLabels[key] ?: key} tiene valores diferentes.",
            currentDescription = current.safeDescription(),
            incomingDescription = incoming.safeDescription(),
        )
    }

    private fun BackupRecord.safeDescription(spec: BackupTableSpec): String {
        val pieces = spec.columns.zip(values).mapNotNull { (column, value) ->
            if (column in hiddenDescriptionColumns || column.endsWith("Id") || column == "id" ||
                column == "timelineId" || column.endsWith("EpochMillis")
            ) return@mapNotNull null
            val rendered = when (value) {
                BackupValue.Null -> null
                is BackupValue.Text -> value.value.take(DESCRIPTION_VALUE_LIMIT)
                is BackupValue.Integer -> value.value.toString()
                is BackupValue.Real -> value.value.toString()
                is BackupValue.Binary -> "${value.value.size} bytes"
            } ?: return@mapNotNull null
            "${descriptionColumnLabels[column] ?: column}: $rendered"
        }.take(DESCRIPTION_FIELD_LIMIT)
        return pieces.takeIf { it.isNotEmpty() }?.joinToString(" · ")
            ?: "Registro ${backupKey(spec).primaryKeyValues.joinToString { it.stableText().takeLast(12) }}"
    }

    private fun BackupPreference.safeDescription(): String = values
        .joinToString(separator = ", ") { it.take(DESCRIPTION_VALUE_LIMIT) }
        .ifEmpty { "Sin valor" }

    private fun requireCompleteResolutions(
        conflicts: List<BackupConflict>,
        resolutions: List<ResolvedBackupConflict>,
    ): Map<String, BackupConflictResolution> {
        if (resolutions.map { it.conflictId }.distinct().size != resolutions.size) {
            throw UnresolvedBackupConflictException("Hay resoluciones duplicadas.")
        }
        val result = resolutions.associate { it.conflictId to it.resolution }
        val expected = conflicts.mapTo(linkedSetOf(), BackupConflict::id)
        if (result.keys != expected) {
            throw UnresolvedBackupConflictException("Todos los conflictos deben resolverse antes de restaurar.")
        }
        return result
    }

    private fun requireFreshConflicts(
        expected: List<BackupConflict>,
        supplied: List<BackupConflict>,
    ) {
        val suppliedById = supplied.associateBy(BackupConflict::id)
        if (suppliedById.size != supplied.size || expected.associateBy(BackupConflict::id) != suppliedById) {
            throw UnresolvedBackupConflictException(
                "La lista de conflictos quedó desactualizada o incompleta. Volvé a revisar la copia antes de combinar.",
            )
        }
    }

    private fun dismissedEventKeysMergeBlockReason(
        current: List<BackupPreference>,
        incoming: List<BackupPreference>,
    ): String? {
        val key = MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE
        val currentPreference = current.singleOrNull { it.key == key }
        val incomingPreference = incoming.singleOrNull { it.key == key }
        if (currentPreference == null && incomingPreference == null) return null
        if (current.count { it.key == key } > 1 || incoming.count { it.key == key } > 1) {
            return "Las identidades de avisos ocultados están duplicadas y no se pueden combinar de forma segura."
        }
        val dismissedPreferences = listOfNotNull(currentPreference, incomingPreference)
        if (dismissedPreferences.any { it.type != BackupPreferenceType.TEXT_LIST }) {
            return "Las identidades de avisos ocultados tienen un formato inválido."
        }
        if (dismissedPreferences.any {
                it.values.size > MiGuardiaBackupContract.MAX_DISMISSED_EVENT_KEYS
            }
        ) {
            return DISMISSED_EVENT_KEYS_LIMIT_REASON
        }
        val distinctCount = sequenceOf(currentPreference, incomingPreference)
            .filterNotNull()
            .flatMap { it.values.asSequence() }
            .distinct()
            .take(MiGuardiaBackupContract.MAX_DISMISSED_EVENT_KEYS + 1)
            .count()
        return DISMISSED_EVENT_KEYS_LIMIT_REASON.takeIf {
            distinctCount > MiGuardiaBackupContract.MAX_DISMISSED_EVENT_KEYS
        }
    }

    private fun requireDismissedEventKeysWithinLimit(preferences: List<BackupPreference>) {
        val dismissed = preferences.singleOrNull {
            it.key == MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE
        } ?: return
        if (dismissed.type != BackupPreferenceType.TEXT_LIST ||
            dismissed.values.size > MiGuardiaBackupContract.MAX_DISMISSED_EVENT_KEYS
        ) {
            throw UnresolvedBackupConflictException(DISMISSED_EVENT_KEYS_LIMIT_REASON)
        }
    }

    private fun BackupRecord.naturalKeyOrNull(
        spec: BackupTableSpec,
        columns: List<String>,
    ): List<BackupValue>? = columns.map { column ->
        val value = values[spec.columns.indexOf(column)]
        if (value === BackupValue.Null) return null
        if (spec.name == "objectives" && column == "abbreviation" && value is BackupValue.Text) {
            BackupValue.Text(value.value.lowercase())
        } else {
            value
        }
    }

    private fun BackupRecord.longInterval(
        spec: BackupTableSpec,
        startColumn: String,
        endColumn: String,
    ): Pair<Long, Long>? {
        val start = (values[spec.columns.indexOf(startColumn)] as? BackupValue.Integer)?.value ?: return null
        val end = (values[spec.columns.indexOf(endColumn)] as? BackupValue.Integer)?.value ?: return null
        return (start to end).takeIf { start < end }
    }

    private fun BackupRecord.dateInterval(
        spec: BackupTableSpec,
        startColumn: String,
        endColumn: String,
    ): Pair<Long, Long>? {
        val start = (values[spec.columns.indexOf(startColumn)] as? BackupValue.Text)?.value ?: return null
        val end = (values[spec.columns.indexOf(endColumn)] as? BackupValue.Text)?.value ?: return null
        return try {
            val startDay = LocalDate.parse(start).toEpochDay()
            val endExclusive = Math.addExact(LocalDate.parse(end).toEpochDay(), 1L)
            (startDay to endExclusive).takeIf { startDay < endExclusive }
        } catch (error: RuntimeException) {
            throw InvalidBackupException("La copia contiene un rango de fechas inválido.", error)
        }
    }

    private fun BackupRecord.overlapScopeKey(
        spec: BackupTableSpec,
    ): List<BackupValue> = overlapScopeColumns[spec.name].orEmpty().map { column ->
        values[spec.columns.indexOf(column)]
    }

    private fun List<BackupRecord>.buildIntervalIndexes(
        spec: BackupTableSpec,
        startColumn: String,
        endColumn: String,
    ): Map<List<BackupValue>, IntervalIndex> = mapNotNull { record ->
        record.longInterval(spec, startColumn, endColumn)?.let { (start, end) ->
            record.overlapScopeKey(spec) to IndexedInterval(record, start, end)
        }
    }.groupBy(
        keySelector = { pair -> pair.first },
        valueTransform = { pair -> pair.second },
    ).mapValues { (_, intervals) -> IntervalIndex.from(intervals) }

    private fun conflictId(kind: String, current: String, incoming: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$kind\u0000$current\u0000$incoming".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$kind-${digest.take(24)}"
    }

    /**
     * A conflict resolution selects or discards a complete logical aggregate, not only its
     * parent row. Expanding removals along every current Room relationship prevents hybrid
     * aggregates and orphaned descendants without rewriting any identity.
     */
    private fun BackupDatabaseSnapshot.expandAggregateRemovals(
        seeds: Set<BackupRecordKey>,
    ): Set<BackupRecordKey> {
        if (seeds.isEmpty()) return emptySet()
        val recordsByTable = MiGuardiaBackupSchemaV6.tables.associate { spec ->
            spec.name to table(spec.name).records.associateBy { record -> record.backupKey(spec) }
        }
        val childrenByRelationship = aggregateRelationships.associateWith { relationship ->
            val childSpec = MiGuardiaBackupSchemaV6.byName.getValue(relationship.childTable)
            table(relationship.childTable).records.groupBy(
                keySelector = { child -> child.relationshipValues(childSpec, relationship.childColumns) },
                valueTransform = { child -> child.backupKey(childSpec) },
            )
        }
        val relationshipsByParent = aggregateRelationships.groupBy(BackupRelationship::parentTable)
        val removals = seeds.toMutableSet()
        val pending = ArrayDeque<BackupRecordKey>().apply { seeds.forEach(::addLast) }
        while (pending.isNotEmpty()) {
            val removedParentKey = pending.removeFirst()
            val parentRecord = recordsByTable[removedParentKey.table]?.get(removedParentKey) ?: continue
            relationshipsByParent[removedParentKey.table].orEmpty().forEach { relationship ->
                val parentSpec = MiGuardiaBackupSchemaV6.byName.getValue(relationship.parentTable)
                val relationshipKey = parentRecord.relationshipValues(parentSpec, relationship.parentColumns)
                childrenByRelationship.getValue(relationship)[relationshipKey].orEmpty().forEach { childKey ->
                    if (removals.add(childKey)) {
                        pending.addLast(childKey)
                    }
                }
            }
        }
        return removals
    }

    private fun BackupDatabaseSnapshot.allRecordKeys(): Set<BackupRecordKey> = buildSet {
        MiGuardiaBackupSchemaV6.tables.forEach { spec ->
            table(spec.name).records.forEach { record -> add(record.backupKey(spec)) }
        }
    }

    private fun BackupRecord.relationshipValues(
        spec: BackupTableSpec,
        columns: List<String>,
    ): List<BackupValue> = columns.map { column -> values[spec.columns.indexOf(column)] }

    private const val PREFERENCES_TABLE = "portable_preferences"
    private val keepBothTemporalTables = setOf(
        "shifts",
        "independent_extra_work_records",
    )
    private val temporalColumns = mapOf(
        "shifts" to ("startEpochMillis" to "endEpochMillis"),
        "shift_extra_intervals" to ("startEpochMillis" to "endEpochMillis"),
        "independent_extra_work_records" to ("startEpochMillis" to "endEpochMillis"),
        "availability_windows" to ("startEpochMillis" to "endEpochMillis"),
    )
    private val dateRangeColumns = mapOf(
        "medical_leaves" to ("startDate" to "endDateInclusive"),
        "vacations" to ("startDate" to "endDateInclusive"),
    )
    private val overlapScopeColumns = mapOf(
        "shift_extra_intervals" to listOf("shiftId"),
        "availability_windows" to listOf("timelineId", "sector"),
        "independent_extra_work_records" to listOf("timelineId", "sector"),
    )
    private val naturalUniqueColumns = mapOf(
        "objectives" to listOf(listOf("abbreviation")),
        "holidays" to listOf(listOf("localDate")),
        "schedule_photos" to listOf(listOf("storageKey")),
        "work_configuration_roots" to listOf(listOf("singletonSlot")),
        "work_configuration_revisions" to listOf(listOf("timelineId", "effectiveFrom")),
        "per_period_hours_values" to listOf(listOf("definitionId", "windowStartInclusive")),
        "work_places" to listOf(listOf("timelineId", "sector", "objectiveId")),
        "work_types" to listOf(listOf("timelineId", "sector", "normalizedNameKey")),
        "work_templates" to listOf(listOf("workPlaceId", "workTypeId", "startTime", "endTime")),
        "workplace_rule_revisions" to listOf(listOf("workPlaceId", "effectiveFrom")),
        "recurring_plan_revisions" to listOf(
            listOf("planId", "revisionNumber"),
            listOf("planId", "effectiveFrom"),
        ),
        "extra_work_classes" to listOf(listOf("timelineId", "sector", "normalizedNameKey")),
        "recurring_occurrences" to listOf(listOf("shiftId")),
        "shift_extra_intervals" to listOf(listOf("shiftId", "startEpochMillis", "endEpochMillis")),
    )
    private val aggregateRelationships = listOf(
        relation("shifts", listOf("id"), "shift_work_snapshots", listOf("shiftId")),
        relation("shift_work_snapshots", listOf("shiftId"), "shifts", listOf("id")),
        relation("shifts", listOf("id"), "shift_notes", listOf("shiftId")),
        relation("shifts", listOf("id"), "shift_notification_configs", listOf("shiftId")),
        relation("shifts", listOf("id"), "recurring_occurrences", listOf("shiftId")),
        relation("recurring_occurrences", listOf("shiftId"), "shifts", listOf("id")),
        relation("shift_notification_configs", listOf("shiftId"), "shift_notification_reminders", listOf("shiftId")),
        relation("work_configuration_roots", listOf("timelineId"), "per_period_hours_definitions", listOf("timelineId")),
        relation("work_configuration_roots", listOf("timelineId"), "work_configuration_revisions", listOf("timelineId")),
        relation("work_configuration_roots", listOf("timelineId"), "work_places", listOf("timelineId")),
        relation("work_configuration_roots", listOf("timelineId"), "work_types", listOf("timelineId")),
        relation("work_configuration_roots", listOf("timelineId"), "work_templates", listOf("timelineId")),
        relation("work_configuration_roots", listOf("timelineId"), "workplace_rule_revisions", listOf("timelineId")),
        relation("work_configuration_roots", listOf("timelineId"), "recurring_plans", listOf("timelineId")),
        relation("work_configuration_roots", listOf("timelineId"), "extra_work_classes", listOf("timelineId")),
        relation("work_configuration_roots", listOf("timelineId"), "shift_work_snapshots", listOf("timelineId")),
        relation("work_configuration_roots", listOf("timelineId"), "independent_extra_work_records", listOf("timelineId")),
        relation("work_configuration_roots", listOf("timelineId"), "availability_windows", listOf("timelineId")),
        relation("per_period_hours_definitions", listOf("id"), "work_configuration_revisions", listOf("perPeriodDefinitionId")),
        relation("per_period_hours_definitions", listOf("id"), "per_period_hours_values", listOf("definitionId")),
        relation("work_configuration_revisions", listOf("id"), "shift_work_snapshots", listOf("configurationRevisionId")),
        relation(
            "work_configuration_revisions",
            listOf("id", "timelineId", "sector"),
            "independent_extra_work_records",
            listOf("configurationRevisionId", "timelineId", "sector"),
        ),
        relation(
            "work_configuration_revisions",
            listOf("id", "timelineId", "sector"),
            "availability_windows",
            listOf("configurationRevisionId", "timelineId", "sector"),
        ),
        relation("objectives", listOf("id"), "work_places", listOf("objectiveId")),
        relation("objectives", listOf("id"), "independent_extra_work_records", listOf("objectiveId")),
        relation(
            "work_places",
            listOf("id", "timelineId", "sector", "objectiveId"),
            "work_templates",
            listOf("workPlaceId", "timelineId", "sector", "objectiveId"),
        ),
        relation(
            "work_places",
            listOf("id", "timelineId", "sector", "objectiveId"),
            "workplace_rule_revisions",
            listOf("workPlaceId", "timelineId", "sector", "objectiveId"),
        ),
        relation(
            "work_places",
            listOf("id", "timelineId", "sector", "objectiveId"),
            "shift_work_snapshots",
            listOf("workPlaceId", "timelineId", "sector", "objectiveId"),
        ),
        relation(
            "work_places",
            listOf("id", "timelineId", "sector", "objectiveId"),
            "independent_extra_work_records",
            listOf("workPlaceId", "timelineId", "sector", "objectiveId"),
        ),
        relation(
            "work_types",
            listOf("id", "timelineId", "sector"),
            "work_templates",
            listOf("workTypeId", "timelineId", "sector"),
        ),
        relation(
            "work_types",
            listOf("id", "timelineId", "sector"),
            "shift_work_snapshots",
            listOf("workTypeId", "timelineId", "sector"),
        ),
        relation(
            "work_types",
            listOf("id", "timelineId", "sector"),
            "independent_extra_work_records",
            listOf("workTypeId", "timelineId", "sector"),
        ),
        relation(
            "work_templates",
            listOf("id", "timelineId", "sector", "workPlaceId", "objectiveId", "workTypeId"),
            "shift_work_snapshots",
            listOf("templateId", "timelineId", "sector", "workPlaceId", "objectiveId", "workTypeId"),
        ),
        relation(
            "work_templates",
            listOf("id", "timelineId", "sector", "workPlaceId", "objectiveId", "workTypeId"),
            "independent_extra_work_records",
            listOf("templateId", "timelineId", "sector", "workPlaceId", "objectiveId", "workTypeId"),
        ),
        relation("work_templates", listOf("id"), "recurring_plan_revisions", listOf("templateId")),
        relation("recurring_plans", listOf("id"), "recurring_plan_revisions", listOf("planId")),
        relation("recurring_plans", listOf("id"), "recurring_occurrences", listOf("planId")),
        relation(
            "recurring_plan_revisions",
            listOf("id", "planId"),
            "recurring_occurrences",
            listOf("revisionId", "planId"),
        ),
        relation(
            "shift_work_snapshots",
            listOf("shiftId", "timelineId", "sector"),
            "shift_actual_records",
            listOf("shiftId", "timelineId", "sector"),
        ),
        relation(
            "shift_actual_records",
            listOf("shiftId", "timelineId", "sector"),
            "shift_extra_intervals",
            listOf("shiftId", "timelineId", "sector"),
        ),
        relation(
            "shift_extra_intervals",
            listOf("shiftId", "timelineId", "sector"),
            "shift_actual_records",
            listOf("shiftId", "timelineId", "sector"),
        ),
        relation(
            "extra_work_classes",
            listOf("id", "timelineId", "sector"),
            "shift_extra_intervals",
            listOf("extraWorkClassId", "timelineId", "sector"),
        ),
        relation(
            "extra_work_classes",
            listOf("id", "timelineId", "sector"),
            "independent_extra_work_records",
            listOf("extraWorkClassId", "timelineId", "sector"),
        ),
    )
    private val hiddenDescriptionColumns = setOf(
        "address",
        "objectiveAddressSnapshot",
        "privateNote",
        "note",
        "body",
        "differenceReason",
        "explanation",
        "storageKey",
    )
    private val descriptionColumnLabels = mapOf(
        "fullName" to "Nombre",
        "abbreviation" to "Abreviatura",
        "localDate" to "Fecha",
        "startDate" to "Desde",
        "endDateInclusive" to "Hasta",
        "ownerLocalDate" to "Día",
        "startTime" to "Inicio",
        "endTime" to "Fin",
        "startTimeSnapshot" to "Inicio",
        "endTimeSnapshot" to "Fin",
        "objectiveNameSnapshot" to "Lugar",
        "objectiveAbbreviationSnapshot" to "Abreviatura",
        "name" to "Nombre",
        "sector" to "Rubro",
        "status" to "Estado",
        "type" to "Tipo",
        "isActive" to "Activo",
    )
    private val preferenceLabels = mapOf(
        "profile.display_name" to "Nombre o apodo",
        "display.theme" to "Tema",
        "display.zoom_percent" to "Zoom",
        "summary.ordered_families" to "Orden del Resumen",
        "summary.hidden_families" to "Secciones ocultas del Resumen",
        "summary.intro_seen" to "Introducción del Resumen",
        "notifications.enabled" to "Avisos activados",
        "notifications.precise_timing" to "Puntualidad exacta",
        "notifications.reminder_minutes" to "Anticipos de avisos",
        "notifications.persistent" to "Aviso durante la jornada",
        "notifications.privacy" to "Privacidad de avisos",
        "notifications.attention" to "Atención de avisos",
        "weather.enabled" to "Clima activado",
        "weather.unit" to "Unidad de clima",
        "weather.include_notifications" to "Clima en avisos",
        "weather.explanation_accepted" to "Explicación de clima aceptada",
    )
    private const val DESCRIPTION_FIELD_LIMIT = 4
    private const val DESCRIPTION_VALUE_LIMIT = 80
    private const val MERGE_LIMIT_TABLE = "merge_complexity_limit"
    private const val MERGE_COMPLEXITY_REASON =
        "La copia tiene demasiados conflictos para combinarla de forma segura. Podés conservar tus datos actuales o usar Reemplazar todo."
    private const val DISMISSED_EVENT_KEYS_LIMIT_REASON =
        "Combinar los avisos ocultados superaría el límite seguro. Podés conservar tus datos actuales o usar Reemplazar todo."

    private class ConflictCollector {
        private val conflictsById = linkedMapOf<String, BackupConflict>()
        val conflictingIncomingKeys: MutableSet<BackupRecordKey> = hashSetOf()
        var mergeBlockedReason: String? = null
            private set
        val accepting: Boolean get() = mergeBlockedReason == null

        fun add(conflict: BackupConflict) {
            if (!accepting || conflict.id in conflictsById) return
            if (conflictsById.size >= MiGuardiaBackupContract.MAX_MERGE_CONFLICTS) {
                block(MERGE_COMPLEXITY_REASON, MERGE_LIMIT_TABLE, "too-many-conflicts")
                return
            }
            conflictsById[conflict.id] = conflict
            conflictingIncomingKeys += conflict.incomingKey
        }

        fun block(reason: String, table: String, keyText: String) {
            if (!accepting) return
            mergeBlockedReason = reason
            conflictsById.clear()
            conflictingIncomingKeys.clear()
            val limitKey = BackupRecordKey(
                table = table,
                primaryKeyValues = listOf(BackupValue.Text(keyText)),
            )
            conflictsById[conflictId("invalid", limitKey.stableText(), limitKey.stableText())] = BackupConflict(
                id = conflictId("invalid", limitKey.stableText(), limitKey.stableText()),
                classification = BackupRecordClassification.INVALID,
                table = table,
                currentKey = limitKey,
                incomingKey = limitKey,
                keepBothAllowed = false,
                summary = reason,
            )
        }

        fun result(): List<BackupConflict> = conflictsById.values.sortedBy(BackupConflict::id)
    }

    private data class IndexedInterval(
        val record: BackupRecord,
        val startInclusive: Long,
        val endExclusive: Long,
    )

    private class IntervalIndex private constructor(
        private val root: Node?,
    ) {
        fun query(startInclusive: Long, endExclusive: Long, visitor: (BackupRecord) -> Boolean) {
            if (startInclusive >= endExclusive) return
            query(root, startInclusive, endExclusive, visitor)
        }

        private fun query(
            node: Node?,
            startInclusive: Long,
            endExclusive: Long,
            visitor: (BackupRecord) -> Boolean,
        ): Boolean {
            node ?: return true
            if (node.left?.maximumEndExclusive?.let { it > startInclusive } == true &&
                !query(node.left, startInclusive, endExclusive, visitor)
            ) {
                return false
            }
            val interval = node.interval
            if (interval.startInclusive < endExclusive && startInclusive < interval.endExclusive &&
                !visitor(interval.record)
            ) {
                return false
            }
            if (interval.startInclusive < endExclusive &&
                !query(node.right, startInclusive, endExclusive, visitor)
            ) {
                return false
            }
            return true
        }

        private data class Node(
            val interval: IndexedInterval,
            val left: Node?,
            val right: Node?,
            val maximumEndExclusive: Long,
        )

        companion object {
            fun from(intervals: List<IndexedInterval>): IntervalIndex {
                val ordered = intervals.sortedWith(
                    compareBy(IndexedInterval::startInclusive, IndexedInterval::endExclusive),
                )
                return IntervalIndex(build(ordered, 0, ordered.size))
            }

            private fun build(intervals: List<IndexedInterval>, from: Int, until: Int): Node? {
                if (from >= until) return null
                val middle = (from + until) ushr 1
                val left = build(intervals, from, middle)
                val right = build(intervals, middle + 1, until)
                val interval = intervals[middle]
                return Node(
                    interval = interval,
                    left = left,
                    right = right,
                    maximumEndExclusive = maxOf(
                        interval.endExclusive,
                        left?.maximumEndExclusive ?: Long.MIN_VALUE,
                        right?.maximumEndExclusive ?: Long.MIN_VALUE,
                    ),
                )
            }
        }
    }

    private data class BackupRelationship(
        val parentTable: String,
        val parentColumns: List<String>,
        val childTable: String,
        val childColumns: List<String>,
    )

    private fun relation(
        parentTable: String,
        parentColumns: List<String>,
        childTable: String,
        childColumns: List<String>,
    ): BackupRelationship {
        require(parentColumns.size == childColumns.size)
        return BackupRelationship(parentTable, parentColumns, childTable, childColumns)
    }
}
