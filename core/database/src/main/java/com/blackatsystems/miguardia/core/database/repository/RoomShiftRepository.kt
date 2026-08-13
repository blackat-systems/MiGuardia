package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaDatabase
import com.blackatsystems.miguardia.core.database.dao.ShiftDao
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.validateRange
import com.blackatsystems.miguardia.core.database.validation.validated
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomShiftRepository(
    private val database: MiGuardiaDatabase,
) : ShiftRepository {
    private val dao: ShiftDao = database.shiftDao()
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

    override suspend fun applyBatch(mutation: ShiftBatchMutation) {
        val insertIds = mutation.shiftsToInsert.map { it.id }
        if (insertIds.size != insertIds.distinct().size) {
            throw InvalidLocalDataException("La carga contiene identificadores de guardia duplicados.")
        }
        if (insertIds.any { it in mutation.shiftIdsToDelete }) {
            throw InvalidLocalDataException("Una guardia no puede borrarse e insertarse en el mismo lote.")
        }
        val entities = mutation.shiftsToInsert.map { it.validated().toEntity() }
        try {
            database.withTransaction {
                if (mutation.shiftIdsToDelete.isNotEmpty()) {
                    dao.deleteByIds(mutation.shiftIdsToDelete.map(UUID::toString))
                }
                if (entities.isNotEmpty()) dao.insertAll(entities)
            }
        } catch (error: SQLiteConstraintException) {
            throw InvalidLocalDataException("No se pudo guardar el lote de guardias.", error)
        }
    }
}
