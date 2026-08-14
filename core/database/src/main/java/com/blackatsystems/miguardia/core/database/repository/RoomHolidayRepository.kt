package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaDatabase
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.validateRange
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayBatchMutation
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import com.blackatsystems.miguardia.core.domain.novelty.normalized
import com.blackatsystems.miguardia.core.domain.repository.DuplicateHolidayDateException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomHolidayRepository(private val database: MiGuardiaDatabase) : HolidayRepository {
    private val dao = database.holidayDao()

    override fun observeBetween(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<Holiday>> {
        validateRange(startDateInclusive, endDateInclusive)
        return dao.observeBetween(startDateInclusive.toString(), endDateInclusive.toString())
            .map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getById(id: UUID): Holiday? = dao.getById(id.toString())?.toDomain()
    override suspend fun getByDate(date: LocalDate): Holiday? = dao.getByDate(date.toString())?.toDomain()

    override suspend fun insert(holiday: Holiday) = mapConstraint { dao.insert(holiday.validatedEntity()) }

    override suspend fun update(holiday: Holiday) = mapConstraint {
        if (dao.update(holiday.validatedEntity()) == 0) throw InvalidLocalDataException("No existe el feriado indicado.")
    }

    override suspend fun delete(id: UUID) {
        dao.delete(id.toString())
    }

    override suspend fun applyBatch(mutation: HolidayBatchMutation) {
        val holidays = mutation.holidaysToSave.map(Holiday::normalized)
        val ids = holidays.map(Holiday::id)
        if (ids.size != ids.distinct().size || ids.any { it in mutation.holidayIdsToDelete }) {
            throw InvalidLocalDataException("El lote de feriados contiene identificadores repetidos o incompatibles.")
        }
        if (holidays.map(Holiday::date).distinct().size != holidays.size) {
            throw InvalidLocalDataException("El lote contiene más de un feriado para la misma fecha.")
        }
        holidays.forEach { validateTimestamps(it) }
        mapConstraint {
            database.withTransaction {
                if (mutation.holidayIdsToDelete.isNotEmpty()) {
                    dao.deleteByIds(mutation.holidayIdsToDelete.map(UUID::toString))
                }
                val entities = holidays.mapNotNull { incoming ->
                    val existing = dao.getByDate(incoming.date.toString())
                    when {
                        existing == null -> incoming.toEntity()
                        existing.id == incoming.id.toString() -> incoming.toEntity()
                        mutation.conflictPolicy == HolidayConflictPolicy.KEEP_EXISTING -> null
                        else -> existing.copy(
                            name = incoming.name,
                            updatedAtEpochMillis = incoming.updatedAt.toEpochMilli(),
                        )
                    }
                }
                if (entities.isNotEmpty()) dao.upsertAll(entities)
            }
        }
    }

    private fun Holiday.validatedEntity() = normalized().also(::validateTimestamps).toEntity()

    private fun validateTimestamps(holiday: Holiday) {
        if (holiday.updatedAt < holiday.createdAt) {
            throw InvalidLocalDataException("La modificación del feriado no puede ser anterior a su creación.")
        }
    }

    private suspend fun <T> mapConstraint(block: suspend () -> T): T = try {
        block()
    } catch (error: SQLiteConstraintException) {
        throw DuplicateHolidayDateException(error)
    }
}
