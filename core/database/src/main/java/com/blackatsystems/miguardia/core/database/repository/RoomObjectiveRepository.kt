package com.blackatsystems.miguardia.core.database.repository

import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomObjectiveRepository(
    private val database: MiGuardiaV2Database,
) : ObjectiveRepository {
    private val objectiveDao = database.objectiveDao()

    override fun observeActive(): Flow<List<Objective>> =
        objectiveDao.observeActive().map { rows ->
            database.withTransaction {
                database.requireValidV2LocalData()
                rows.map { it.toDomain() }
            }
        }

    override fun observeAll(): Flow<List<Objective>> =
        objectiveDao.observeAll().map { rows ->
            database.withTransaction {
                database.requireValidV2LocalData()
                rows.map { it.toDomain() }
            }
        }

    override suspend fun getById(id: UUID): Objective? = database.withTransaction {
        database.requireValidV2LocalData()
        objectiveDao.getById(id.toString())?.toDomain()
    }
}
