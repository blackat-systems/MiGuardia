package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.Shift
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ShiftRepository {
    fun observeHasAny(): Flow<Boolean>

    fun observeStartingBetween(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<Shift>>

    fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>>

    suspend fun getById(id: UUID): Shift?
}
