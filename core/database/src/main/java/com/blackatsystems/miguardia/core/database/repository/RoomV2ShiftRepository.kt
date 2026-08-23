package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaDatabase
import com.blackatsystems.miguardia.core.database.dao.ShiftWithWorkSnapshotRow
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toDomainRuleRevision
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkPlace
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkSnapshot
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkTemplate
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkType
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.database.validation.requireExactShiftSnapshotInstants
import com.blackatsystems.miguardia.core.database.validation.validated
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.LegacyShiftCannotBeUpdatedAsV2Exception
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.shift.isExactV2PositionOnlyEdit
import com.blackatsystems.miguardia.core.domain.work.resolveWorkplaceRuleSegments
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class RoomV2ShiftRepository(
    private val database: MiGuardiaDatabase,
) : V2ShiftRepository {
    private val dao = database.v2ShiftDao()

    override fun observeWorkSnapshot(shiftId: UUID): Flow<ShiftWorkSnapshot?> = combine(
        dao.observeSnapshot(shiftId.toString()),
        database.workCatalogDao().observeInvalidV2RowCount(),
        database.workConfigurationDao().observeRoots(),
    ) { snapshot, _, _ -> snapshot }
        .map {
            database.withTransaction {
                database.requireValidV2LocalData()
                dao.getSnapshot(shiftId.toString())?.toDomainWorkSnapshot()
            }
        }

    override suspend fun getWorkSnapshot(shiftId: UUID): ShiftWorkSnapshot? =
        database.withTransaction {
            database.requireValidV2LocalData()
            dao.getSnapshot(shiftId.toString())?.toDomainWorkSnapshot()
        }

    override suspend fun getShift(shiftId: UUID): V2ShiftLookup = database.withTransaction {
        database.requireValidV2LocalData()
        val storedShift = database.shiftDao().getById(shiftId.toString())
            ?: return@withTransaction V2ShiftLookup.Missing
        val pair = dao.getShiftWithSnapshot(shiftId.toString())
            ?: return@withTransaction V2ShiftLookup.LegacyV1(storedShift.toDomain())
        V2ShiftLookup.V2(pair.toDomainWrite())
    }

    override suspend fun insert(write: V2ShiftWrite): Unit = writeShiftData(
        "No se pudo guardar la jornada 2.0.",
    ) {
        database.requireValidV2LocalData()
        if (database.shiftDao().getById(write.shift.id.toString()) != null) {
            invalid("Ya existe una jornada con ese identificador.")
        }
        validateIncomingWrite(write)
        dao.insertPair(write.shift.validated().toEntity(), write.snapshot.toEntity())
        database.requireValidV2LocalData()
    }

    override suspend fun update(write: V2ShiftWrite): Unit = writeShiftData(
        "No se pudo actualizar la jornada 2.0.",
    ) {
        database.requireValidV2LocalData()
        val existing = requireExistingV2Write(write.shift.id)
        validateUpdateIdentity(existing, write)
        validateIncomingWrite(write)
        val (shiftRows, snapshotRows) = dao.updatePair(
            write.shift.validated().toEntity(),
            write.snapshot.toEntity(),
        )
        if (shiftRows != 1 || snapshotRows != 1) {
            invalid("La jornada cambió mientras se confirmaba la edición.")
        }
        database.requireValidV2LocalData()
    }

    override suspend fun deleteShift(expected: V2ShiftWrite): Unit = writeShiftData(
        "No se pudo eliminar la jornada 2.0.",
    ) {
        database.requireValidV2LocalData()
        val current = dao.getShiftWithSnapshot(expected.shift.id.toString())?.toDomainWrite()
            ?: throw ConflictingLocalWriteException(
                "La jornada ya no existe o dejó de ser V2. Revisá el día nuevamente.",
            )
        if (current != expected) {
            throw ConflictingLocalWriteException(
                "La jornada cambió mientras confirmabas la eliminación. Revisala nuevamente.",
            )
        }
        database.shiftNoveltyDao().deleteLinksToShift(expected.shift.id.toString())
        if (dao.deleteShiftAndOwnedSnapshot(expected.shift.id.toString()) != 1) {
            missingShift(expected.shift.id)
        }
        database.requireValidV2LocalData()
    }

    override suspend fun applyV2Batch(
        mutation: V2ShiftBatchMutation,
        expectedOccupancy: ShiftOccupancyExpectation,
        expectedUpdates: V2ShiftWriteExpectation,
    ): Unit = writeShiftData(
        "No se pudo guardar el lote de jornadas 2.0.",
    ) {
        database.requireValidV2LocalData()

        val writeDates = (mutation.shiftsToInsert + mutation.shiftsToUpdate)
            .mapTo(hashSetOf()) { write -> write.shift.localStartDate }
        val expectedIds = expectedOccupancy.observedShifts
            .mapTo(hashSetOf()) { version -> version.shiftId }
        val updatedIds = mutation.shiftsToUpdate.mapTo(linkedSetOf()) { it.shift.id }
        if (
            writeDates.any { it !in expectedOccupancy.startDateInclusive..expectedOccupancy.endDateInclusive } ||
            !expectedIds.containsAll(mutation.shiftIdsToDelete) ||
            !expectedIds.containsAll(updatedIds) ||
            expectedUpdates.writesById.keys != updatedIds
        ) {
            invalid("La ocupacion revisada no cubre todas las jornadas del lote.")
        }
        val currentOccupancy = ShiftOccupancyExpectation.capture(
            startDateInclusive = expectedOccupancy.startDateInclusive,
            endDateInclusive = expectedOccupancy.endDateInclusive,
            shifts = database.shiftDao().getStartingBetween(
                expectedOccupancy.startDateInclusive.toString(),
                expectedOccupancy.endDateInclusive.toString(),
            ).map { entity -> entity.toDomain() },
        )
        if (currentOccupancy != expectedOccupancy) {
            throw ConflictingLocalWriteException(
                "Las jornadas cambiaron mientras revisabas la carga. Revisalas nuevamente antes de guardar.",
            )
        }

        if (!writeDates.containsAll(mutation.explicitDayStatusDatesToClear)) {
            invalid("Una carga V2 sólo puede limpiar estados de las fechas que guarda.")
        }
        mutation.shiftIdsToDelete.forEach { id ->
            val stored = database.shiftDao().getById(id.toString())?.toDomain()
                ?: missingShift(id)
            if (stored.localStartDate !in writeDates) {
                invalid("Una carga V2 sólo puede reemplazar jornadas de las fechas que guarda.")
            }
        }

        mutation.shiftsToInsert.forEach { write ->
            if (database.shiftDao().getById(write.shift.id.toString()) != null) {
                invalid("Ya existe la jornada ${write.shift.id}.")
            }
            validateIncomingWrite(write)
        }
        mutation.shiftsToUpdate.forEach { write ->
            val existing = requireExistingV2Write(write.shift.id)
            if (existing != expectedUpdates.writesById[write.shift.id]) {
                throw ConflictingLocalWriteException(
                    "La jornada cambió mientras revisabas la edición. Revisala nuevamente antes de guardar.",
                )
            }
            validateUpdateIdentity(existing, write)
            if (isExactV2PositionOnlyEdit(existing, write)) {
                write.shift.validated()
                requireExactShiftSnapshotInstants(write.shift)
            } else {
                validateIncomingWrite(write)
            }
        }

        mutation.shiftIdsToDelete.forEach { id ->
            database.shiftNoveltyDao().deleteLinksToShift(id.toString())
        }
        if (mutation.shiftIdsToDelete.isNotEmpty()) {
            val deletedRows = database.shiftDao()
                .deleteByIds(mutation.shiftIdsToDelete.map(UUID::toString))
            if (deletedRows != mutation.shiftIdsToDelete.size) {
                invalid("Una jornada cambió mientras se confirmaba el reemplazo.")
            }
        }
        mutation.shiftsToInsert.forEach { write ->
            dao.insertPair(write.shift.validated().toEntity(), write.snapshot.toEntity())
        }
        mutation.shiftsToUpdate.forEach { write ->
            val (shiftRows, snapshotRows) = dao.updatePair(
                write.shift.validated().toEntity(),
                write.snapshot.toEntity(),
            )
            if (shiftRows != 1 || snapshotRows != 1) {
                invalid("No existe la jornada ${write.shift.id} que se quiere actualizar.")
            }
        }
        mutation.explicitDayStatusDatesToClear.forEach { date ->
            database.explicitDayStatusDao().clear(date.toString())
        }
        database.requireValidV2LocalData()
    }

    private suspend fun validateIncomingWrite(write: V2ShiftWrite) {
        val shift = write.shift.validated()
        requireExactShiftSnapshotInstants(shift)
        val snapshot = write.snapshot
        val history = database.requireValidV2LocalData()
            ?: invalid("Todavía no existe una configuración laboral.")
        val applicable = history.timeline.revisionAt(shift.localStartDate)
            ?: invalid("MiGuardia 2.0 todavía no está configurada para ${shift.localStartDate}.")
        if (
            history.timeline.id != snapshot.timelineId ||
            applicable.id != snapshot.configurationRevisionId ||
            applicable.value.sector != snapshot.sector
        ) {
            invalid("La jornada no usa la revisión laboral exacta de su fecha.")
        }

        val place = database.workCatalogDao().getWorkPlaceById(snapshot.workPlaceId.toString())
            ?.toDomainWorkPlace() ?: invalid("No existe el lugar elegido.")
        val type = database.workCatalogDao().getWorkTypeById(snapshot.workTypeId.toString())
            ?.toDomainWorkType() ?: invalid("No existe el tipo de trabajo elegido.")
        val template = database.workCatalogDao().getWorkTemplateById(snapshot.templateId.toString())
            ?.toDomainWorkTemplate() ?: invalid("No existe la plantilla elegida.")
        val objective = database.objectiveDao().getById(snapshot.objectiveId.toString())
            ?.toDomain() ?: invalid("No existe el lugar físico elegido.")

        if (!place.isActive || !type.isActive || !template.isActive) {
            invalid("El lugar, el tipo y el horario deben estar activos para guardar la jornada.")
        }
        if (
            place.timelineId != snapshot.timelineId ||
            place.sector != snapshot.sector ||
            place.objectiveId != snapshot.objectiveId ||
            type.timelineId != snapshot.timelineId ||
            type.sector != snapshot.sector ||
            template.timelineId != snapshot.timelineId ||
            template.sector != snapshot.sector ||
            template.workPlaceId != place.id ||
            template.objectiveId != objective.id ||
            template.workTypeId != type.id ||
            shift.sourceObjectiveId != objective.id
        ) {
            invalid("La jornada mezcla un lugar, tipo o plantilla de otra forma de trabajar.")
        }
        if (
            shift.objectiveNameSnapshot != objective.fullName ||
            shift.objectiveAbbreviationSnapshot != objective.abbreviation ||
            shift.objectiveAddressSnapshot != objective.address ||
            shift.startTimeSnapshot != template.startTime ||
            shift.endTimeSnapshot != template.endTime ||
            shift.colorArgbSnapshot != template.colorArgb ||
            shift.sourceScheduleCombinationId != template.legacyScheduleCombinationId ||
            snapshot.workTypeNameSnapshot != type.name ||
            snapshot.workTypeBehaviorSnapshot != type.behavior
        ) {
            invalid("Las fotografías de la jornada no coinciden con la selección confirmada.")
        }
        val rules = database.workCatalogDao()
            .getRuleRevisionsForWorkPlace(place.id.toString())
            .map { it.toDomainRuleRevision() }
        resolveWorkplaceRuleSegments(shift, snapshot, rules)
    }

    private suspend fun requireExistingV2Write(id: UUID): V2ShiftWrite {
        val pair = dao.getShiftWithSnapshot(id.toString())
        if (pair == null) {
            if (database.shiftDao().getById(id.toString()) != null) {
                throw LegacyShiftCannotBeUpdatedAsV2Exception()
            }
            missingShift(id)
        }
        return pair.toDomainWrite()
    }

    private fun validateUpdateIdentity(existing: V2ShiftWrite, updated: V2ShiftWrite) {
        if (
            existing.shift.id != updated.shift.id ||
            existing.shift.createdAt != updated.shift.createdAt ||
            existing.shift.status != updated.shift.status ||
            existing.shift.localStartDate != updated.shift.localStartDate ||
            existing.shift.zoneId != updated.shift.zoneId ||
            !updated.shift.updatedAt.isAfter(existing.shift.updatedAt)
        ) {
            invalid("Editar una jornada debe conservar UUID, fecha, zona, creación y estado.")
        }
    }

    private fun ShiftWithWorkSnapshotRow.toDomainWrite(): V2ShiftWrite = V2ShiftWrite(
        shift = shift.toDomain(),
        snapshot = snapshot.toDomainWorkSnapshot(),
    )

    private suspend fun <T> writeShiftData(
        message: String,
        block: suspend () -> T,
    ): T = try {
        database.withTransaction { block() }
    } catch (error: SQLiteConstraintException) {
        throw InvalidLocalDataException(message, error)
    } catch (error: IllegalArgumentException) {
        throw InvalidLocalDataException(message, error)
    }

    private fun missingShift(id: UUID): Nothing = invalid("No existe la jornada $id.")

    private fun invalid(message: String): Nothing = throw InvalidLocalDataException(message)
}
