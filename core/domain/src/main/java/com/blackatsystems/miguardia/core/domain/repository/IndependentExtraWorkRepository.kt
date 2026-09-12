package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkExpectation
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkMutation
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSelection
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkWriteResult
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface IndependentExtraWorkRepository {
    fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<IndependentExtraWorkRecord>>

    fun observeOn(
        timelineId: UUID,
        sector: WorkSector,
        ownerLocalDate: LocalDate,
    ): Flow<List<IndependentExtraWorkRecord>>

    suspend fun get(id: UUID): IndependentExtraWorkRecord?

    suspend fun captureExpectation(
        id: UUID?,
        selection: IndependentExtraWorkSelection,
        windowStart: Instant,
        windowEnd: Instant,
        windowStartDate: LocalDate,
        windowEndDateInclusive: LocalDate,
    ): IndependentExtraWorkExpectation

    suspend fun applyMutation(mutation: IndependentExtraWorkMutation): IndependentExtraWorkWriteResult
}
