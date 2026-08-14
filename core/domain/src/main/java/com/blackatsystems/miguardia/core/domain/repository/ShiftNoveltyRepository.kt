package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.FormalShiftChange
import com.blackatsystems.miguardia.core.domain.model.ShiftNovelty
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyMutation
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ShiftNoveltyRepository {
    fun observeForShift(shiftId: UUID): Flow<List<ShiftNovelty>>
    fun observeFormalChange(shiftId: UUID): Flow<FormalShiftChange?>
    suspend fun getById(id: UUID): ShiftNovelty?
    suspend fun applyMutation(mutation: ShiftNoveltyMutation)
}
