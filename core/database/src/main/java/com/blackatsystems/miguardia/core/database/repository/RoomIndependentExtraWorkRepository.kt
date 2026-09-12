package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.mapping.encodeSector
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toDomainExtraWorkClass
import com.blackatsystems.miguardia.core.database.mapping.toDomainIndependentExtra
import com.blackatsystems.miguardia.core.database.mapping.toDomainOrNull
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkPlace
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkTemplate
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkType
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraOccupancyVersion
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraProtectedDateRange
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkExpectation
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkMutation
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSelection
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkWriteResult
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyVersion
import com.blackatsystems.miguardia.core.domain.repository.IndependentExtraWorkRepository
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class RoomIndependentExtraWorkRepository(
    private val database: MiGuardiaV2Database,
    private val clock: Clock,
) : IndependentExtraWorkRepository {
    private val dao = database.independentExtraWorkDao()

    override fun observeAll(
        timelineId: UUID,
        sector: WorkSector,
    ): Flow<List<IndependentExtraWorkRecord>> = combine(
        dao.observeAll(timelineId.toString(), sector.encodeSector()),
        dao.observeInvalidRowCount(),
    ) { rows, _ -> rows }
        .map { rows ->
            database.withTransaction {
                database.requireValidV2LocalData()
                rows.map { it.toDomainIndependentExtra() }
            }
        }

    override fun observeOn(
        timelineId: UUID,
        sector: WorkSector,
        ownerLocalDate: LocalDate,
    ): Flow<List<IndependentExtraWorkRecord>> = combine(
        dao.observeOn(timelineId.toString(), sector.encodeSector(), ownerLocalDate.toString()),
        dao.observeInvalidRowCount(),
    ) { rows, _ -> rows }
        .map { rows ->
            database.withTransaction {
                database.requireValidV2LocalData()
                rows.map { it.toDomainIndependentExtra() }
            }
        }

    override suspend fun get(id: UUID): IndependentExtraWorkRecord? = database.withTransaction {
        database.requireValidV2LocalData()
        dao.getById(id.toString())?.toDomainIndependentExtra()
    }

    override suspend fun captureExpectation(
        id: UUID?,
        selection: IndependentExtraWorkSelection,
        windowStart: Instant,
        windowEnd: Instant,
        windowStartDate: LocalDate,
        windowEndDateInclusive: LocalDate,
    ): IndependentExtraWorkExpectation = database.withTransaction {
        database.requireValidV2LocalData()
        captureExpectationLocked(
            id,
            selection,
            windowStart,
            windowEnd,
            windowStartDate,
            windowEndDateInclusive,
        )
    }

    override suspend fun applyMutation(
        mutation: IndependentExtraWorkMutation,
    ): IndependentExtraWorkWriteResult = try {
        database.withTransaction {
            database.requireValidV2LocalData()
            val expected = mutation.expectation
            val current = captureExpectationLocked(
                id = expected.previous?.id,
                selection = expected.selection,
                windowStart = expected.windowStart,
                windowEnd = expected.windowEnd,
                windowStartDate = expected.windowStartDate,
                windowEndDateInclusive = expected.windowEndDateInclusive,
            )
            if (current != expected) return@withTransaction IndependentExtraWorkWriteResult.Conflict
            val replacement = mutation.replacement
            if (replacement != null && expected.hasOverlappingWorkFor(replacement) &&
                !mutation.overlappingWorkConfirmed
            ) {
                invalid("Hay trabajos superpuestos que todavía no fueron confirmados.")
            }
            if (replacement != null && expected.hasProtectedDatesFor(replacement) &&
                !mutation.protectedDateConfirmed
            ) {
                invalid("La convivencia con carpeta médica o vacaciones todavía no fue confirmada.")
            }

            if (replacement == null) {
                if (dao.delete(requireNotNull(expected.previous).id.toString()) != 1) {
                    return@withTransaction IndependentExtraWorkWriteResult.Conflict
                }
                database.requireValidV2LocalData()
                return@withTransaction IndependentExtraWorkWriteResult.Deleted
            }
            requirePersistable(replacement, expected)
            val entity = replacement.toEntity()
            if (expected.previous == null) {
                dao.insert(entity)
            } else if (dao.update(entity) != 1) {
                return@withTransaction IndependentExtraWorkWriteResult.Conflict
            }
            database.requireValidV2LocalData()
            IndependentExtraWorkWriteResult.Saved(replacement)
        }
    } catch (_: SQLiteConstraintException) {
        IndependentExtraWorkWriteResult.Conflict
    }

    private suspend fun captureExpectationLocked(
        id: UUID?,
        selection: IndependentExtraWorkSelection,
        windowStart: Instant,
        windowEnd: Instant,
        windowStartDate: LocalDate,
        windowEndDateInclusive: LocalDate,
    ): IndependentExtraWorkExpectation {
        require(windowStart < windowEnd) { "La ventana de ocupación debe tener duración positiva" }
        require(!windowEndDateInclusive.isBefore(windowStartDate)) {
            "La ventana local de protección no puede estar invertida"
        }
        requireCurrentSelection(selection)
        val previous = id?.let { recordId -> dao.getById(recordId.toString())?.toDomainIndependentExtra() }
        previous?.let { stored ->
            require(
                stored.timelineId == selection.configuration.timelineId &&
                    stored.sector == selection.configuration.revision.value.sector,
            ) { "El extra observado pertenece a otro contexto laboral" }
        }
        val timeline = selection.configuration.timelineId.toString()
        val sector = selection.configuration.revision.value.sector.encodeSector()
        val excludedId = id?.toString() ?: EMPTY_EXCLUDED_ID
        val extras = dao.getOverlappingExtras(
            timeline,
            sector,
            excludedId,
            windowStart.toEpochMilli(),
            windowEnd.toEpochMilli(),
        ).map { row ->
            IndependentExtraOccupancyVersion(
                id = UUID.fromString(row.id),
                start = Instant.ofEpochMilli(row.startEpochMillis),
                end = Instant.ofEpochMilli(row.endEpochMillis),
                updatedAt = Instant.ofEpochMilli(row.updatedAtEpochMillis),
            )
        }.toSet()
        val shifts = dao.getOverlappingShifts(
            timeline,
            sector,
            windowStart.toEpochMilli(),
            windowEnd.toEpochMilli(),
        ).map { row ->
            val start = Instant.ofEpochMilli(row.startEpochMillis)
            ShiftOccupancyVersion(
                shiftId = UUID.fromString(row.shiftId),
                localStartDate = start.atZone(ZoneId.of(row.zoneId)).toLocalDate(),
                startAt = start,
                endAt = Instant.ofEpochMilli(row.endEpochMillis),
                status = com.blackatsystems.miguardia.core.domain.model.ShiftStatus.valueOf(row.status),
                updatedAt = Instant.ofEpochMilli(
                    maxOf(row.shiftUpdatedAtEpochMillis, row.actualUpdatedAtEpochMillis ?: Long.MIN_VALUE),
                ),
            )
        }.toSet()
        val startDate = windowStartDate.toString()
        val endDate = windowEndDateInclusive.toString()
        val medical = dao.getMedicalLeaves(startDate, endDate)
        val vacations = dao.getVacations(startDate, endDate)
        val statuses = dao.getExplicitDayStatuses(startDate, endDate)
        val fingerprint = buildString {
            medical.forEach { append("M:").append(it.id).append(':').append(it.startDate)
                .append(':').append(it.endDateInclusive).append(':').append(it.updatedAtEpochMillis).append('|') }
            vacations.forEach { append("V:").append(it.id).append(':').append(it.startDate)
                .append(':').append(it.endDateInclusive).append(':').append(it.updatedAtEpochMillis).append('|') }
            statuses.forEach { append("S:").append(it.localDate).append(':').append(it.type).append('|') }
        }
        return IndependentExtraWorkExpectation.capture(
            previous = previous,
            selection = selection,
            windowStart = windowStart,
            windowEnd = windowEnd,
            windowStartDate = windowStartDate,
            windowEndDateInclusive = windowEndDateInclusive,
            observedShifts = shifts,
            observedExtras = extras,
            observedProtectedDateRanges = medical.map { row ->
                IndependentExtraProtectedDateRange(
                    LocalDate.parse(row.startDate),
                    LocalDate.parse(row.endDateInclusive),
                )
            } + vacations.map { row ->
                IndependentExtraProtectedDateRange(
                    LocalDate.parse(row.startDate),
                    LocalDate.parse(row.endDateInclusive),
                )
            },
            protectionFingerprint = fingerprint,
        )
    }

    private suspend fun requireCurrentSelection(expected: IndependentExtraWorkSelection) {
        val configurationRows = database.workConfigurationDao().getRoots()
        val history = configurationRows.toDomainOrNull(database.workConfigurationDao().getOrphanRowCount())
            ?: invalid("Todavía no existe una configuración laboral.")
        val currentConfiguration = ResolvedWorkConfigurationRevision.resolve(
            history,
            expected.configuration.referenceDate,
        )
        if (!currentConfiguration.sameAs(expected.configuration)) {
            invalid("La configuración laboral cambió; refrescá antes de guardar.")
        }
        val catalog = database.workCatalogDao()
        val place = catalog.getWorkPlaceById(expected.workPlace.id.toString())?.toDomainWorkPlace()
        val objective = database.objectiveDao().getById(expected.objective.id.toString())?.toDomain()
        val type = catalog.getWorkTypeById(expected.workType.id.toString())?.toDomainWorkType()
        val template = expected.template?.let { chosen ->
            catalog.getWorkTemplateById(chosen.id.toString())?.toDomainWorkTemplate()
        }
        val extraClass = database.shiftActualDao()
            .getClass(expected.extraWorkClass.id.toString())
            ?.toDomainExtraWorkClass()
        if (
            place != expected.workPlace ||
            objective != expected.objective ||
            type != expected.workType ||
            template != expected.template ||
            extraClass != expected.extraWorkClass
        ) {
            invalid("Una fuente del extra cambió; refrescá antes de guardar.")
        }
    }

    private fun requirePersistable(
        record: IndependentExtraWorkRecord,
        expectation: IndependentExtraWorkExpectation,
    ) {
        val now = clock.instant().truncatedTo(ChronoUnit.MINUTES)
        require(!record.end.isAfter(now)) {
            "No se puede persistir un extra independiente en curso o futuro"
        }
        require(record.ownerLocalDate == record.start.atZone(record.zoneId).toLocalDate()) {
            "La fecha dueña persistida no coincide con el inicio exacto"
        }
        val previous = expectation.previous
        previous?.let { stored ->
            require(record.createdAt == stored.createdAt && record.updatedAt.isAfter(stored.updatedAt)) {
                "Una corrección debe conservar la creación y avanzar su versión"
            }
        } ?: require(record.createdAt == record.updatedAt) {
            "Un extra nuevo debe comenzar con una única versión temporal"
        }
        val preservesHistory = previous != null &&
            record.workPlaceId == previous.workPlaceId &&
            record.objectiveId == previous.objectiveId &&
            record.workTypeId == previous.workTypeId &&
            record.templateId == previous.templateId &&
            record.extraWorkClassId == previous.extraWorkClassId &&
            record.snapshot.hasSameHistoricalSourcesAs(previous.snapshot, record.templateId != null)
        if (!preservesHistory) {
            val selection = expectation.selection
            val selectedTemplate = selection.template
            require(
                selection.workPlace.isActive &&
                    selection.objective.isActive &&
                    selection.workType.isActive &&
                    selection.extraWorkClass.isActive &&
                (selectedTemplate?.isActive != false),
            ) { "Crear o reclasificar un extra exige fuentes activas" }
            require(
                record.snapshot.workPlaceName == selection.objective.fullName &&
                    record.snapshot.workPlaceAbbreviation == selection.objective.abbreviation &&
                    record.snapshot.workPlaceAddress == selection.objective.address &&
                    record.snapshot.workTypeName == selection.workType.name &&
                    record.snapshot.workTypeBehavior == selection.workType.behavior &&
                    record.snapshot.className == selection.extraWorkClass.name &&
                    record.snapshot.helpsMeetHoursReference == selection.extraWorkClass.helpsMeetHoursReference &&
                    record.snapshot.showDedicatedSummary == selection.extraWorkClass.showDedicatedSummary &&
                    (selectedTemplate == null || record.snapshot.colorArgb == selectedTemplate.colorArgb),
            ) { "Las fotografías del extra no coinciden con las fuentes elegidas" }
        }
    }

    private fun com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot
        .hasSameHistoricalSourcesAs(
            other: com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot,
            hasTemplate: Boolean,
        ): Boolean =
        workPlaceName == other.workPlaceName &&
            workPlaceAbbreviation == other.workPlaceAbbreviation &&
            workPlaceAddress == other.workPlaceAddress &&
            workTypeName == other.workTypeName &&
            workTypeBehavior == other.workTypeBehavior &&
            className == other.className &&
            helpsMeetHoursReference == other.helpsMeetHoursReference &&
            showDedicatedSummary == other.showDedicatedSummary &&
            (!hasTemplate || colorArgb == other.colorArgb)

    private fun ResolvedWorkConfigurationRevision.sameAs(other: ResolvedWorkConfigurationRevision): Boolean =
        timelineId == other.timelineId &&
            referenceDate == other.referenceDate &&
            revision == other.revision

    private fun invalid(message: String): Nothing = throw InvalidLocalDataException(message)

    private companion object {
        const val EMPTY_EXCLUDED_ID: String = ""
    }
}
