package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.Shift
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ShiftRepository {
    fun observeStartingBetween(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<Shift>>

    suspend fun getById(id: UUID): Shift?
    suspend fun insert(shift: Shift)
    suspend fun update(shift: Shift)
    suspend fun delete(id: UUID)
}
