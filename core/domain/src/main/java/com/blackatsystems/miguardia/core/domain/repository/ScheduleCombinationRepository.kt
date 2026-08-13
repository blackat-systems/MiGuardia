package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.RecentScheduleCombination
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ScheduleCombinationRepository {
    fun observeByObjective(objectiveId: UUID): Flow<List<ScheduleCombination>>
    fun observeRecentlyUsed(limit: Int = 5): Flow<List<RecentScheduleCombination>>
    suspend fun getById(id: UUID): ScheduleCombination?
    suspend fun create(combination: ScheduleCombination)
    suspend fun update(combination: ScheduleCombination)
    suspend fun hide(id: UUID, updatedAt: Instant)
    suspend fun delete(id: UUID)
}
