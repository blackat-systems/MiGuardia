package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaDatabase
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.validateUpdateTimestamp
import com.blackatsystems.miguardia.core.database.validation.validated
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.repository.DuplicateObjectiveAbbreviationException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomObjectiveRepository(
    private val database: MiGuardiaDatabase,
) : ObjectiveRepository {
    private val objectiveDao = database.objectiveDao()
    private val scheduleDao = database.scheduleCombinationDao()

    override fun observeActive(): Flow<List<Objective>> =
        objectiveDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Objective>> =
        objectiveDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: UUID): Objective? = objectiveDao.getById(id.toString())?.toDomain()

    override suspend fun create(objective: Objective) {
        val entity = objective.validated().toEntity()
        try {
            objectiveDao.insert(entity)
        } catch (error: SQLiteConstraintException) {
            mapConstraint(entity.id, entity.abbreviation, error)
        }
    }

    override suspend fun update(objective: Objective) {
        val entity = objective.validated().toEntity()
        try {
            if (objectiveDao.update(entity) == 0) missing(entity.id)
        } catch (error: SQLiteConstraintException) {
            mapConstraint(entity.id, entity.abbreviation, error)
        }
    }

    override suspend fun hide(id: UUID, updatedAt: Instant) {
        val existing = objectiveDao.getById(id.toString()) ?: missing(id.toString())
        validateUpdateTimestamp(
            createdAt = Instant.ofEpochMilli(existing.createdAtEpochMillis),
            updatedAt = updatedAt,
        )
        objectiveDao.hide(id.toString(), updatedAt.toEpochMilli())
    }

    override suspend fun delete(id: UUID) {
        database.withTransaction {
            scheduleDao.deleteByObjective(id.toString())
            objectiveDao.delete(id.toString())
        }
    }

    private suspend fun mapConstraint(
        id: String,
        abbreviation: String,
        error: SQLiteConstraintException,
    ): Nothing {
        if (objectiveDao.findIdByAbbreviation(abbreviation) != null) {
            throw DuplicateObjectiveAbbreviationException(abbreviation, error)
        }
        throw InvalidLocalDataException("No se pudo guardar el objetivo $id.", error)
    }

    private fun missing(id: String): Nothing =
        throw InvalidLocalDataException("No existe el objetivo $id.")
}
