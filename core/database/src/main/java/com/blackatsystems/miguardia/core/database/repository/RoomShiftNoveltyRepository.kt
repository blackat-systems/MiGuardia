package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaDatabase
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.database.validation.validated
import com.blackatsystems.miguardia.core.domain.model.ShiftNovelty
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyType
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.toOperationalSnapshot
import com.blackatsystems.miguardia.core.domain.novelty.normalized
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoveltyRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomShiftNoveltyRepository(private val database: MiGuardiaDatabase) : ShiftNoveltyRepository {
    private val noveltyDao = database.shiftNoveltyDao()
    private val shiftDao = database.shiftDao()

    override fun observeForShift(shiftId: UUID): Flow<List<ShiftNovelty>> =
        noveltyDao.observeForShift(shiftId.toString()).map { rows -> rows.map { it.toDomain() } }

    override fun observeFormalChange(shiftId: UUID) =
        noveltyDao.observeFormalChange(shiftId.toString()).map { it?.toDomain() }

    override suspend fun getById(id: UUID): ShiftNovelty? = noveltyDao.getById(id.toString())?.toDomain()

    override suspend fun applyMutation(mutation: ShiftNoveltyMutation) {
        try {
            database.withTransaction {
                when (mutation) {
                    is ShiftNoveltyMutation.SaveInformative -> saveInformative(mutation.novelty)
                    is ShiftNoveltyMutation.ChangeStatus -> changeStatus(mutation)
                    is ShiftNoveltyMutation.ApplyFormalChange -> applyFormalChange(mutation)
                    is ShiftNoveltyMutation.RestoreOriginalPlan -> restoreOriginal(mutation)
                    is ShiftNoveltyMutation.CreateSecondShift -> createSecondShift(mutation)
                    is ShiftNoveltyMutation.DeleteInformative -> deleteInformative(mutation.noveltyId)
                    is ShiftNoveltyMutation.DeleteSecondShift -> deleteSecondShift(mutation)
                }
            }
        } catch (error: SQLiteConstraintException) {
            throw InvalidLocalDataException("No se pudo guardar la novedad.", error)
        }
    }

    private suspend fun saveInformative(novelty: ShiftNovelty) {
        val normalized = novelty.normalized()
        if (normalized.type !in setOf(
                ShiftNoveltyType.ADDITIONAL_TIME,
                ShiftNoveltyType.EARLY_DEPARTURE,
                ShiftNoveltyType.OTHER,
            )
        ) throw InvalidLocalDataException("La categoría no es una novedad informativa.")
        requireShift(normalized.shiftId)
        val entity = normalized.toEntity()
        val existing = noveltyDao.getById(entity.id)
        if (existing == null) {
            noveltyDao.insert(entity)
        } else {
            if (existing.shiftId != entity.shiftId) conflict()
            if (noveltyDao.update(entity) == 0) conflict()
        }
    }

    private suspend fun changeStatus(mutation: ShiftNoveltyMutation.ChangeStatus) {
        val shift = mutation.updatedShift.validated()
        val current = requireShift(shift.id).toDomain()
        if (
            shift.copy(status = current.status, updatedAt = current.updatedAt) != current ||
            shift.updatedAt.isBefore(current.updatedAt)
        ) {
            conflict()
        }
        val isV2 = database.v2ShiftDao().hasSnapshot(shift.id.toString())
        if (isV2) database.requireValidV2LocalData()
        val novelty = mutation.novelty?.normalized()
        when (shift.status) {
            ShiftStatus.PLANNED -> if (novelty != null) conflict()
            ShiftStatus.ABSENT -> if (novelty?.type != ShiftNoveltyType.ABSENCE) conflict()
            ShiftStatus.CANCELLED -> if (novelty?.type != ShiftNoveltyType.CANCELLATION) conflict()
        }
        if (novelty != null && novelty.shiftId != shift.id) conflict()
        noveltyDao.deleteStateControllers(shift.id.toString())
        if (shiftDao.update(shift.toEntity()) == 0) conflict()
        novelty?.let { noveltyDao.insert(it.toEntity()) }
        if (isV2) database.requireValidV2LocalData()
    }

    private suspend fun applyFormalChange(mutation: ShiftNoveltyMutation.ApplyFormalChange) {
        val updatedShift = mutation.updatedShift.validated()
        rejectStructuralV1WriterForV2Shift(updatedShift.id)
        val incoming = mutation.change.normalized()
        if (incoming.shiftId != updatedShift.id || incoming.final != updatedShift.toOperationalSnapshot()) conflict()
        val currentShift = requireShift(updatedShift.id).toDomain()
        val existing = noveltyDao.getFormalChange(updatedShift.id.toString())?.toDomain()
        if (existing != null && currentShift.toOperationalSnapshot() != existing.final) conflict()
        if (existing == null && incoming.original != currentShift.toOperationalSnapshot()) conflict()
        val stored = if (existing == null) incoming else incoming.copy(
            id = existing.id,
            original = existing.original,
            scheduleChanged = existing.scheduleChanged || incoming.scheduleChanged,
            objectiveChanged = existing.objectiveChanged || incoming.objectiveChanged,
            createdAt = existing.createdAt,
        )
        if (shiftDao.update(updatedShift.toEntity()) == 0) conflict()
        noveltyDao.upsertFormalChange(stored.toEntity())
    }

    private suspend fun restoreOriginal(mutation: ShiftNoveltyMutation.RestoreOriginalPlan) {
        val restored = mutation.restoredShift.validated()
        rejectStructuralV1WriterForV2Shift(restored.id)
        val current = requireShift(restored.id).toDomain()
        val formal = noveltyDao.getFormalChange(restored.id.toString())?.toDomain() ?: conflict()
        if (formal.final != mutation.expectedFinal || current.toOperationalSnapshot() != mutation.expectedFinal) conflict()
        if (restored.toOperationalSnapshot() != formal.original) conflict()
        if (shiftDao.update(restored.toEntity()) == 0) conflict()
        noveltyDao.deleteFormalChange(restored.id.toString())
    }

    private suspend fun createSecondShift(mutation: ShiftNoveltyMutation.CreateSecondShift) {
        val novelty = mutation.novelty.normalized()
        val second = mutation.secondShift.validated()
        if (novelty.type != ShiftNoveltyType.SECOND_SHIFT || novelty.relatedShiftId != second.id) conflict()
        requireShift(novelty.shiftId)
        rejectStructuralV1WriterForV2Shift(novelty.shiftId)
        if (shiftDao.getById(second.id.toString()) != null) conflict()
        shiftDao.insert(second.toEntity())
        noveltyDao.insert(novelty.toEntity())
    }

    private suspend fun deleteInformative(id: UUID) {
        val existing = noveltyDao.getById(id.toString())?.toDomain() ?: return
        if (existing.type !in setOf(
                ShiftNoveltyType.ADDITIONAL_TIME,
                ShiftNoveltyType.EARLY_DEPARTURE,
                ShiftNoveltyType.OTHER,
            )
        ) conflict()
        noveltyDao.delete(id.toString())
    }

    private suspend fun deleteSecondShift(mutation: ShiftNoveltyMutation.DeleteSecondShift) {
        val existing = noveltyDao.getById(mutation.noveltyId.toString())?.toDomain() ?: conflict()
        if (existing.type != ShiftNoveltyType.SECOND_SHIFT || existing.relatedShiftId != mutation.secondShiftId) conflict()
        rejectStructuralV1WriterForV2Shift(existing.shiftId)
        rejectStructuralV1WriterForV2Shift(mutation.secondShiftId)
        noveltyDao.delete(existing.id.toString())
        noveltyDao.deleteLinksToShift(mutation.secondShiftId.toString())
        shiftDao.delete(mutation.secondShiftId.toString())
    }

    private suspend fun requireShift(id: UUID) = shiftDao.getById(id.toString())
        ?: throw InvalidLocalDataException("La guardia indicada no existe.")

    private suspend fun rejectStructuralV1WriterForV2Shift(id: UUID) {
        if (database.v2ShiftDao().hasSnapshot(id.toString())) {
            throw InvalidLocalDataException(
                "La jornada $id pertenece a MiGuardia 2.0 y no admite cambios estructurales heredados.",
            )
        }
    }

    private fun conflict(): Nothing = throw ConflictingLocalWriteException(
        "Los datos cambiaron mientras editabas. Volvé a abrir el detalle e intentá nuevamente.",
    )
}
