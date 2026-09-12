package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.mapping.encodeSector
import com.blackatsystems.miguardia.core.database.mapping.toAvailabilityEntity
import com.blackatsystems.miguardia.core.database.mapping.toDomainAvailability
import com.blackatsystems.miguardia.core.database.mapping.toDomainOrNull
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.domain.model.AvailabilitySourceVersion
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowExpectation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowMutation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowWriteResult
import com.blackatsystems.miguardia.core.domain.model.toAvailabilityVersion
import com.blackatsystems.miguardia.core.domain.repository.AvailabilityWindowRepository
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class RoomAvailabilityWindowRepository(
    private val database: MiGuardiaV2Database,
) : AvailabilityWindowRepository {
    private val dao = database.availabilityWindowDao()
    private val sourceDao = database.independentExtraWorkDao()

    override fun observeAll(
        timelineId: UUID,
        sector: WorkSector,
    ): Flow<List<AvailabilityWindowRecord>> = combine(
        dao.observeAll(timelineId.toString(), sector.encodeSector()),
        dao.observeInvalidRowCount(),
        database.workConfigurationDao().observeOrphanRowCount(),
    ) { rows, invalidCount, orphanCount ->
        if (orphanCount != 0) invalid("La configuración laboral contiene filas huérfanas.")
        if (invalidCount != 0) invalid("Las disponibilidades almacenadas contienen filas inválidas.")
        rows.map { it.toDomainAvailability() }.also(::requireNoAvailabilityOverlaps)
    }

    override fun observeOn(
        timelineId: UUID,
        sector: WorkSector,
        ownerLocalDate: java.time.LocalDate,
    ): Flow<List<AvailabilityWindowRecord>> = combine(
        dao.observeOn(timelineId.toString(), sector.encodeSector(), ownerLocalDate.toString()),
        dao.observeInvalidRowCount(),
    ) { rows, invalidCount ->
        if (invalidCount != 0) invalid("Las disponibilidades almacenadas contienen filas inválidas.")
        rows.map { it.toDomainAvailability() }
    }

    override suspend fun get(id: UUID): AvailabilityWindowRecord? = database.withTransaction {
        database.requireValidV2LocalData()
        dao.getById(id.toString())?.toDomainAvailability()
    }

    override suspend fun captureExpectation(
        id: UUID?,
        configuration: ResolvedWorkConfigurationRevision,
        windowStart: Instant,
        windowEnd: Instant,
    ): AvailabilityWindowExpectation = database.withTransaction {
        database.requireValidV2LocalData()
        captureExpectationInternal(id, configuration, windowStart, windowEnd)
    }

    override suspend fun applyMutation(
        mutation: AvailabilityWindowMutation,
    ): AvailabilityWindowWriteResult = try {
        database.withTransaction {
            database.requireValidV2LocalData()
            val expected = mutation.expectation
            val current = try {
                captureExpectationInternal(
                    id = expected.previous?.id,
                    configuration = expected.configuration,
                    windowStart = expected.observedStart,
                    windowEnd = expected.observedEnd,
                    staleExpectationIsConflict = true,
                )
            } catch (_: AvailabilityExpectationConflictException) {
                return@withTransaction AvailabilityWindowWriteResult.Conflict
            }
            if (!current.sameSnapshotAs(expected)) {
                return@withTransaction AvailabilityWindowWriteResult.Conflict
            }
            val replacement = mutation.replacement
            if (replacement == null) {
                if (dao.delete(requireNotNull(expected.previous).id.toString()) != 1) {
                    return@withTransaction AvailabilityWindowWriteResult.Conflict
                }
                database.requireValidV2LocalData()
                return@withTransaction AvailabilityWindowWriteResult.Deleted
            }
            validateReplacement(expected, replacement)
            if (current.overlaps(replacement)) {
                return@withTransaction AvailabilityWindowWriteResult.Overlap
            }
            val entity = replacement.toAvailabilityEntity()
            if (expected.previous == null) {
                dao.insert(entity)
            } else if (dao.update(entity) != 1) {
                return@withTransaction AvailabilityWindowWriteResult.Conflict
            }
            database.requireValidV2LocalData()
            AvailabilityWindowWriteResult.Saved(replacement)
        }
    } catch (_: SQLiteConstraintException) {
        AvailabilityWindowWriteResult.Conflict
    }

    private suspend fun captureExpectationInternal(
        id: UUID?,
        configuration: ResolvedWorkConfigurationRevision,
        windowStart: Instant,
        windowEnd: Instant,
        staleExpectationIsConflict: Boolean = false,
    ): AvailabilityWindowExpectation {
        require(windowStart < windowEnd) { "La ventana observada debe tener duración positiva" }
        val history = database.workConfigurationDao().getRoots().toDomainOrNull(
            database.workConfigurationDao().getOrphanRowCount(),
        ) ?: invalid("Todavía no existe una configuración laboral.")
        val exact = ResolvedWorkConfigurationRevision.resolve(history, configuration.referenceDate)
        if (!exact.sameAs(configuration)) {
            stale(
                staleExpectationIsConflict,
                "La configuración laboral observada quedó desactualizada.",
            )
        }
        val previous = id?.let { dao.getById(it.toString())?.toDomainAvailability() }
        if (id != null && previous == null) {
            stale(staleExpectationIsConflict, "La disponibilidad observada ya no existe.")
        }
        if (previous != null && (previous.start < windowStart || previous.end > windowEnd)) {
            stale(
                staleExpectationIsConflict,
                "La disponibilidad observada cambió fuera de la ventana revisada.",
            )
        }
        val sector = configuration.revision.value.sector
        val timeline = configuration.timelineId
        val excludedId = id?.toString().orEmpty()
        val windows = dao.getOverlapping(
            timeline.toString(),
            sector.encodeSector(),
            excludedId,
            windowStart.toEpochMilli(),
            windowEnd.toEpochMilli(),
        ).map { it.toDomainAvailability().toAvailabilityVersion() }
        val shiftRows = sourceDao.getOverlappingShifts(
            timeline.toString(),
            sector.encodeSector(),
            windowStart.toEpochMilli(),
            windowEnd.toEpochMilli(),
        )
        val shifts = shiftRows.map { row ->
            AvailabilitySourceVersion(
                key = "shift:${row.shiftId}",
                start = Instant.ofEpochMilli(row.startEpochMillis),
                end = Instant.ofEpochMilli(row.endEpochMillis),
                version = listOf(
                    row.status,
                    row.shiftUpdatedAtEpochMillis,
                    row.actualUpdatedAtEpochMillis ?: "planned",
                ).joinToString(":"),
            )
        }
        val extras = sourceDao.getOverlappingExtras(
            timeline.toString(),
            sector.encodeSector(),
            EMPTY_EXCLUDED_ID,
            windowStart.toEpochMilli(),
            windowEnd.toEpochMilli(),
        ).map { row ->
            AvailabilitySourceVersion(
                key = "extra:${row.id}",
                start = Instant.ofEpochMilli(row.startEpochMillis),
                end = Instant.ofEpochMilli(row.endEpochMillis),
                version = row.updatedAtEpochMillis.toString(),
            )
        }
        val protectedOwnerDates = buildSet {
            add(configuration.referenceDate)
            shiftRows.forEach { row -> add(LocalDate.parse(row.ownerLocalDate)) }
        }
        val firstProtectedDate = protectedOwnerDates.minOrNull() ?: configuration.referenceDate
        val lastProtectedDate = protectedOwnerDates.maxOrNull() ?: configuration.referenceDate
        val protectionFingerprint = buildList {
            sourceDao.getMedicalLeaves(firstProtectedDate.toString(), lastProtectedDate.toString()).forEach { row ->
                val range = LocalDate.parse(row.startDate)..LocalDate.parse(row.endDateInclusive)
                if (protectedOwnerDates.any { it in range }) {
                    add("medical:${row.id}:${row.startDate}:${row.endDateInclusive}:${row.updatedAtEpochMillis}")
                }
            }
            sourceDao.getVacations(firstProtectedDate.toString(), lastProtectedDate.toString()).forEach { row ->
                val range = LocalDate.parse(row.startDate)..LocalDate.parse(row.endDateInclusive)
                if (protectedOwnerDates.any { it in range }) {
                    add("vacation:${row.id}:${row.startDate}:${row.endDateInclusive}:${row.updatedAtEpochMillis}")
                }
            }
        }.sorted().joinToString("|")
        return AvailabilityWindowExpectation.capture(
            previous = previous,
            configuration = exact,
            observedStart = windowStart,
            observedEnd = windowEnd,
            observedWindows = windows,
            observedActiveSources = shifts + extras,
            protectionFingerprint = protectionFingerprint,
        )
    }

    private fun validateReplacement(
        expectation: AvailabilityWindowExpectation,
        record: AvailabilityWindowRecord,
    ) {
        val previous = expectation.previous
        if (previous == null) {
            requireNotNull(expectation.configuration.revision.value.availabilityLabel) {
                "La disponibilidad no está habilitada para la fecha elegida"
            }
            require(record.configurationRevisionId == expectation.configuration.revision.id) {
                "Una disponibilidad nueva debe guardar la revisión exacta observada"
            }
            require(record.createdAt == record.updatedAt) {
                "Una disponibilidad nueva debe comenzar con una sola versión temporal"
            }
        } else {
            require(
                record.configurationRevisionId == previous.configurationRevisionId &&
                    record.labelSnapshot == previous.labelSnapshot &&
                    record.ownerLocalDate == previous.ownerLocalDate &&
                    record.createdAt == previous.createdAt &&
                    record.updatedAt.isAfter(previous.updatedAt),
            ) { "La corrección debe conservar fecha, historia y creación, y avanzar su versión" }
        }
        require(record.start >= expectation.observedStart && record.end <= expectation.observedEnd) {
            "La disponibilidad debe permanecer dentro de la ventana observada"
        }
    }

    private fun ResolvedWorkConfigurationRevision.sameAs(other: ResolvedWorkConfigurationRevision): Boolean =
        timelineId == other.timelineId && referenceDate == other.referenceDate && revision == other.revision

    private fun requireNoAvailabilityOverlaps(records: List<AvailabilityWindowRecord>) {
        records.sortedWith(compareBy(AvailabilityWindowRecord::start, AvailabilityWindowRecord::id))
            .zipWithNext()
            .forEach { (first, second) ->
                if (first.end > second.start) {
                    invalid("Las disponibilidades almacenadas se superponen.")
                }
            }
    }

    private fun AvailabilityWindowExpectation.sameSnapshotAs(other: AvailabilityWindowExpectation): Boolean =
        previous == other.previous &&
            configuration.sameAs(other.configuration) &&
            observedStart == other.observedStart &&
            observedEnd == other.observedEnd &&
            observedWindows == other.observedWindows &&
            observedActiveSources == other.observedActiveSources &&
            protectionFingerprint == other.protectionFingerprint

    private fun invalid(message: String): Nothing = throw InvalidLocalDataException(message)

    private fun stale(asConflict: Boolean, message: String): Nothing {
        if (asConflict) throw AvailabilityExpectationConflictException()
        invalid(message)
    }

    private companion object {
        const val EMPTY_EXCLUDED_ID: String = ""
    }

    private class AvailabilityExpectationConflictException : RuntimeException()
}
