package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayBatchMutation
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface HolidayRepository {
    fun observeBetween(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<Holiday>>
    suspend fun getById(id: UUID): Holiday?
    suspend fun getByDate(date: LocalDate): Holiday?
    suspend fun insert(holiday: Holiday)
    suspend fun update(holiday: Holiday)
    suspend fun delete(id: UUID)
    suspend fun applyBatch(mutation: HolidayBatchMutation)
}
