package com.blackatsystems.miguardia.core.database.repository

import com.blackatsystems.miguardia.core.database.dao.ExplicitDayStatusDao
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.validateRange
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomExplicitDayStatusRepository(
    private val dao: ExplicitDayStatusDao,
) : ExplicitDayStatusRepository {
    override fun observeBetween(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<ExplicitDayStatus>> {
        validateRange(startDateInclusive, endDateInclusive)
        return dao.observeBetween(startDateInclusive.toString(), endDateInclusive.toString())
            .map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun set(date: LocalDate, type: ExplicitDayStatusType) {
        dao.set(ExplicitDayStatus(date, type).toEntity())
    }

    override suspend fun clear(date: LocalDate) {
        dao.clear(date.toString())
    }
}
