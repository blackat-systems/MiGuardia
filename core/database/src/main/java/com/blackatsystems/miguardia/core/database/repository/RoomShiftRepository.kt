package com.blackatsystems.miguardia.core.database.repository

import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.dao.ShiftDao
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.database.validation.validateRange
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class RoomShiftRepository(
    private val database: MiGuardiaV2Database,
) : ShiftRepository {
    private val dao: ShiftDao = database.shiftDao()
    private val integrityChanges = database.workCatalogDao().observeInvalidV2RowCount()

    override fun observeHasAny(): Flow<Boolean> = combine(
        dao.observeHasAny(),
        integrityChanges,
    ) { hasAny, _ -> hasAny }
        .map { hasAny ->
            database.withTransaction {
                database.requireValidV2LocalData()
                hasAny
            }
        }

    override fun observeStartingBetween(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<Shift>> {
        validateRange(startDateInclusive, endDateInclusive)
        return combine(
            dao.observeStartingBetween(
                startDateInclusive.toString(),
                endDateInclusive.toString(),
            ),
            integrityChanges,
        ) { rows, _ -> rows }
            .map { rows ->
                database.withTransaction {
                    database.requireValidV2LocalData()
                    rows.map { it.toDomain() }
                }
            }
    }

    override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> = combine(
        dao.observeEndingAfter(instantExclusive.toEpochMilli()),
        integrityChanges,
    ) { rows, _ -> rows }
        .map { rows ->
            database.withTransaction {
                database.requireValidV2LocalData()
                rows.map { it.toDomain() }
            }
        }

    override suspend fun getById(id: UUID): Shift? = database.withTransaction {
        database.requireValidV2LocalData()
        dao.getById(id.toString())?.toDomain()
    }
}
