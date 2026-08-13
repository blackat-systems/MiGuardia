package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import com.blackatsystems.miguardia.core.database.dao.ScheduleCombinationDao
import com.blackatsystems.miguardia.core.database.entity.ScheduleCombinationEntity
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.validateUpdateTimestamp
import com.blackatsystems.miguardia.core.database.validation.validated
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.RecentScheduleCombination
import com.blackatsystems.miguardia.core.domain.repository.DuplicateScheduleCombinationException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ScheduleCombinationRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomScheduleCombinationRepository(
    private val dao: ScheduleCombinationDao,
) : ScheduleCombinationRepository {
    override fun observeByObjective(objectiveId: UUID): Flow<List<ScheduleCombination>> =
        dao.observeByObjective(objectiveId.toString()).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecentlyUsed(limit: Int): Flow<List<RecentScheduleCombination>> {
        if (limit !in 1..5) {
            throw InvalidLocalDataException("La cantidad de horarios recientes debe estar entre 1 y 5.")
        }
        return dao.observeRecentlyUsed(limit).map { rows ->
            rows.map { row ->
                RecentScheduleCombination(
                    objective = row.objective.toDomain(),
                    combination = row.combination.toDomain(),
                    lastUsedAt = Instant.ofEpochMilli(row.lastUsedAtEpochMillis),
                )
            }
        }
    }

    override suspend fun getById(id: UUID): ScheduleCombination? = dao.getById(id.toString())?.toDomain()

    override suspend fun create(combination: ScheduleCombination) {
        val entity = combination.validated().toEntity()
        try {
            dao.insert(entity)
        } catch (error: SQLiteConstraintException) {
            mapConstraint(entity, error)
        }
    }

    override suspend fun update(combination: ScheduleCombination) {
        val entity = combination.validated().toEntity()
        try {
            if (dao.update(entity) == 0) missing(entity.id)
        } catch (error: SQLiteConstraintException) {
            mapConstraint(entity, error)
        }
    }

    override suspend fun hide(id: UUID, updatedAt: Instant) {
        val existing = dao.getById(id.toString()) ?: missing(id.toString())
        validateUpdateTimestamp(
            createdAt = Instant.ofEpochMilli(existing.createdAtEpochMillis),
            updatedAt = updatedAt,
        )
        dao.hide(id.toString(), updatedAt.toEpochMilli())
    }

    override suspend fun delete(id: UUID) {
        dao.delete(id.toString())
    }

    private suspend fun mapConstraint(
        entity: ScheduleCombinationEntity,
        error: SQLiteConstraintException,
    ): Nothing {
        val duplicateId = dao.findExactId(entity.objectiveId, entity.startTime, entity.endTime)
        if (duplicateId != null) throw DuplicateScheduleCombinationException(error)
        throw InvalidLocalDataException("No se pudo guardar la combinación ${entity.id}.", error)
    }

    private fun missing(id: String): Nothing =
        throw InvalidLocalDataException("No existe la combinación $id.")
}
