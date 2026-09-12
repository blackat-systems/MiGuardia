package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import java.time.LocalDate
import java.util.UUID
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

    /** Atomically changes only explicit days off after revalidating the current timeline.
     * Marking refuses dates occupied by a planned/actual shift; other records are preserved.
     * Removing never clears an UNDEFINED status. Repeating the same request is idempotent.
     */
    suspend fun updateDaysOff(
        expectedTimelineId: UUID,
        dates: Set<LocalDate>,
        markAsDayOff: Boolean,
    ) {
        throw UnsupportedOperationException("Este repositorio no permite editar francos.")
    }
}
