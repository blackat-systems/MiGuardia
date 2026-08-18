package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface ExplicitDayStatusRepository {
    fun observeBetween(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<ExplicitDayStatus>>

    fun observeFrom(startDateInclusive: LocalDate): Flow<List<ExplicitDayStatus>>

    suspend fun set(date: LocalDate, type: ExplicitDayStatusType)
    suspend fun setAll(dates: Set<LocalDate>, type: ExplicitDayStatusType)
    suspend fun clear(date: LocalDate)
}
