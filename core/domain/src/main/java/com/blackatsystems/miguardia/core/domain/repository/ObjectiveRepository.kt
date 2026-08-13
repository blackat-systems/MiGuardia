package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.Objective
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ObjectiveRepository {
    fun observeActive(): Flow<List<Objective>>
    fun observeAll(): Flow<List<Objective>>
    suspend fun getById(id: UUID): Objective?
    suspend fun create(objective: Objective)
    suspend fun update(objective: Objective)
    suspend fun hide(id: UUID, updatedAt: Instant)
    suspend fun delete(id: UUID)
}
