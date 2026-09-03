package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.Vacation
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface VacationRepository {
    fun observeOverlapping(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<Vacation>>

    fun observeEndingOnOrAfter(dateInclusive: LocalDate): Flow<List<Vacation>>

    suspend fun getById(id: UUID): Vacation?
    suspend fun insert(vacation: Vacation)
    suspend fun update(expected: Vacation, replacement: Vacation)
    suspend fun delete(expected: Vacation)
}
