package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.validateRange
import com.blackatsystems.miguardia.core.database.validation.validated
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationMedicalLeaveConflictException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomMedicalLeaveRepository(
    private val database: MiGuardiaV2Database,
) : MedicalLeaveRepository {
    private val dao = database.medicalLeaveDao()

    override fun observeIntersecting(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<MedicalLeave>> {
        validateRange(startDateInclusive, endDateInclusive)
        return dao.observeIntersecting(startDateInclusive.toString(), endDateInclusive.toString())
            .map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun create(medicalLeave: MedicalLeave) {
        val entity = medicalLeave.validated().toEntity()
        try {
            database.withTransaction {
                ensureNoVacationConflict(entity.startDate, entity.endDateInclusive)
                dao.insert(entity)
            }
        } catch (error: SQLiteConstraintException) {
            throw InvalidLocalDataException("No se pudo guardar la carpeta médica ${entity.id}.", error)
        }
    }

    override suspend fun update(medicalLeave: MedicalLeave) {
        val entity = medicalLeave.validated().toEntity()
        try {
            database.withTransaction {
                ensureNoVacationConflict(entity.startDate, entity.endDateInclusive)
                if (dao.update(entity) == 0) {
                    throw InvalidLocalDataException("No existe la carpeta médica ${entity.id}.")
                }
            }
        } catch (error: SQLiteConstraintException) {
            throw InvalidLocalDataException("No se pudo actualizar la carpeta médica ${entity.id}.", error)
        }
    }

    override suspend fun delete(id: UUID) {
        dao.delete(id.toString())
    }

    private suspend fun ensureNoVacationConflict(startDate: String, endDateInclusive: String) {
        if (database.vacationDao().findFirstOverlapping(
                startDateInclusive = startDate,
                endDateInclusive = endDateInclusive,
                excludedId = null,
            ) != null
        ) {
            throw VacationMedicalLeaveConflictException()
        }
    }
}
