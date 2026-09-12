package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowExpectation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowMutation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowWriteResult
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface AvailabilityWindowRepository {
    fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<AvailabilityWindowRecord>>

    fun observeOn(
        timelineId: UUID,
        sector: WorkSector,
        ownerLocalDate: LocalDate,
    ): Flow<List<AvailabilityWindowRecord>>

    suspend fun get(id: UUID): AvailabilityWindowRecord?

    suspend fun captureExpectation(
        id: UUID?,
        configuration: ResolvedWorkConfigurationRevision,
        windowStart: Instant,
        windowEnd: Instant,
    ): AvailabilityWindowExpectation

    suspend fun applyMutation(mutation: AvailabilityWindowMutation): AvailabilityWindowWriteResult
}
