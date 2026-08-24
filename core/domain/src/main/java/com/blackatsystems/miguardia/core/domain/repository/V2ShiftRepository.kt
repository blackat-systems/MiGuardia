package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface V2ShiftRepository {
    fun observeWorkSnapshot(shiftId: UUID): Flow<ShiftWorkSnapshot?>

    suspend fun getWorkSnapshot(shiftId: UUID): ShiftWorkSnapshot?

    suspend fun getShift(shiftId: UUID): V2ShiftLookup

    suspend fun insert(write: V2ShiftWrite)

    suspend fun deleteShift(expected: V2ShiftWrite)

    suspend fun applyV2Batch(
        mutation: V2ShiftBatchMutation,
        expectedOccupancy: ShiftOccupancyExpectation,
        expectedUpdates: V2ShiftWriteExpectation = V2ShiftWriteExpectation.EMPTY,
    )
}
