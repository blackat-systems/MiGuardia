package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.ShiftSituation
import com.blackatsystems.miguardia.core.domain.model.ShiftSituationExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftSituationMutation
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ShiftSituationRepository {
    fun observeForShift(shiftId: UUID): Flow<ShiftSituation?>
    suspend fun inspect(shiftId: UUID): ShiftSituationExpectation?
    suspend fun apply(mutation: ShiftSituationMutation)
    suspend fun evidenceStorageKeys(): Set<String> = emptySet()
}
