package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import com.blackatsystems.miguardia.core.database.dao.ShiftDao
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.validateRange
import com.blackatsystems.miguardia.core.database.validation.validated
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomShiftRepository(
    private val dao: ShiftDao,
) : ShiftRepository {
    override fun observeStartingBetween(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<Shift>> {
        validateRange(startDateInclusive, endDateInclusive)
        return dao.observeStartingBetween(
            startDateInclusive.toString(),
            endDateInclusive.toString(),
        ).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getById(id: UUID): Shift? = dao.getById(id.toString())?.toDomain()

    override suspend fun insert(shift: Shift) {
        val entity = shift.validated().toEntity()
        try {
            dao.insert(entity)
        } catch (error: SQLiteConstraintException) {
            throw InvalidLocalDataException("No se pudo guardar la guardia ${entity.id}.", error)
        }
    }

    override suspend fun update(shift: Shift) {
        val entity = shift.validated().toEntity()
        try {
            if (dao.update(entity) == 0) {
                throw InvalidLocalDataException("No existe la guardia ${entity.id}.")
            }
        } catch (error: SQLiteConstraintException) {
            throw InvalidLocalDataException("No se pudo actualizar la guardia ${entity.id}.", error)
        }
    }

    override suspend fun delete(id: UUID) {
        dao.delete(id.toString())
    }
}
