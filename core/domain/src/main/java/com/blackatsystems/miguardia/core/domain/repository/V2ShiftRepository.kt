package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface V2ShiftRepository {
    fun observeWorkSnapshot(shiftId: UUID): Flow<ShiftWorkSnapshot?>

    suspend fun getWorkSnapshot(shiftId: UUID): ShiftWorkSnapshot?

    suspend fun insert(write: V2ShiftWrite)

    suspend fun update(write: V2ShiftWrite)

    suspend fun deleteShift(shiftId: UUID)

    suspend fun applyV2Batch(mutation: V2ShiftBatchMutation)
}
