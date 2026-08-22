package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaDatabase
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.validateUpdateTimestamp
import com.blackatsystems.miguardia.core.database.validation.validated
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.repository.AdoptedObjectiveInUseException
import com.blackatsystems.miguardia.core.domain.repository.DuplicateObjectiveAbbreviationException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.work.normalizedForV2Update
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
        try {
            database.withTransaction {
                database.requireValidV2LocalData()
                val previousEntity = objectiveDao.getById(objective.id.toString())
                    ?: missing(objective.id.toString())
                val normalized = if (
                    database.workCatalogDao().countWorkPlacesForObjective(previousEntity.id) > 0
                ) {
                    try {
                        objective.normalizedForV2Update(previousEntity.toDomain())
                    } catch (error: IllegalArgumentException) {
                        throw InvalidLocalDataException(
                            "No se pudieron actualizar los datos del lugar adoptado.",
                            error,
                        )
                    }
                } else {
                    objective.validated()
                }
                val entity = normalized.toEntity()
                if (objectiveDao.update(entity) == 0) missing(entity.id)
                database.requireValidV2LocalData()
            }
        } catch (error: SQLiteConstraintException) {
            mapConstraint(objective.id.toString(), objective.abbreviation, error)
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
            database.requireValidV2LocalData()
            if (database.workCatalogDao().countWorkPlacesForObjective(id.toString()) > 0) {
                throw AdoptedObjectiveInUseException()
            }
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
