package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.entity.VacationEntity
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.validateRange
import com.blackatsystems.miguardia.core.database.validation.validated
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.OverlappingVacationException
import com.blackatsystems.miguardia.core.domain.repository.VacationMedicalLeaveConflictException
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomVacationRepository(
    private val database: MiGuardiaV2Database,
) : VacationRepository {
    private val dao = database.vacationDao()
    private val medicalLeaveDao = database.medicalLeaveDao()

    override fun observeOverlapping(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<Vacation>> {
        validateRange(startDateInclusive, endDateInclusive)
        return dao.observeOverlapping(startDateInclusive.toString(), endDateInclusive.toString())
            .map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeEndingOnOrAfter(dateInclusive: LocalDate): Flow<List<Vacation>> =
        dao.observeEndingOnOrAfter(dateInclusive.toString())
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: UUID): Vacation? = dao.getById(id.toString())?.toDomain()

    override suspend fun insert(vacation: Vacation) {
        val entity = vacation.validated().toEntity()
        try {
            database.withTransaction {
                ensureNoConflicts(entity.startDate, entity.endDateInclusive, excludedId = null)
                dao.insert(entity)
            }
        } catch (error: SQLiteConstraintException) {
            throw ConflictingLocalWriteException("No se pudo guardar el período de vacaciones.")
        }
    }

    override suspend fun update(expected: Vacation, replacement: Vacation) {
        val expectedEntity = expected.validated().toEntity()
        val replacementEntity = replacement.validated().toEntity()
        requireCompatibleUpdate(expectedEntity, replacementEntity)
        try {
            database.withTransaction {
                val current = dao.getById(expectedEntity.id)
                if (current != expectedEntity) {
                    throw staleVacationConflict()
                }
                ensureNoConflicts(
                    replacementEntity.startDate,
                    replacementEntity.endDateInclusive,
                    excludedId = replacementEntity.id,
                )
                if (dao.update(replacementEntity) != 1) {
                    throw staleVacationConflict()
                }
            }
        } catch (error: SQLiteConstraintException) {
            throw ConflictingLocalWriteException("No se pudo actualizar el período de vacaciones.")
        }
    }

    override suspend fun delete(expected: Vacation) {
        val expectedEntity = expected.validated().toEntity()
        database.withTransaction {
            val current = dao.getById(expectedEntity.id)
            if (current != expectedEntity || dao.delete(expectedEntity.id) != 1) {
                throw staleVacationConflict()
            }
        }
    }

    private fun requireCompatibleUpdate(
        expected: VacationEntity,
        replacement: VacationEntity,
    ) {
        if (
            replacement.id != expected.id ||
            replacement.createdAtEpochMillis != expected.createdAtEpochMillis
        ) {
            throw ConflictingLocalWriteException(
                "La identidad del período de vacaciones cambió mientras se estaba editando.",
            )
        }
        if (replacement.updatedAtEpochMillis <= expected.updatedAtEpochMillis) {
            throw InvalidLocalDataException(
                "La modificación de vacaciones debe ser posterior a la versión observada.",
            )
        }
    }

    private fun staleVacationConflict() = ConflictingLocalWriteException(
        "El período de vacaciones cambió mientras se estaba editando.",
    )

    private suspend fun ensureNoConflicts(
        startDate: String,
        endDateInclusive: String,
        excludedId: String?,
    ) {
        if (dao.findFirstOverlapping(startDate, endDateInclusive, excludedId) != null) {
            throw OverlappingVacationException()
        }
        if (medicalLeaveDao.findFirstIntersecting(startDate, endDateInclusive) != null) {
            throw VacationMedicalLeaveConflictException()
        }
    }
}
