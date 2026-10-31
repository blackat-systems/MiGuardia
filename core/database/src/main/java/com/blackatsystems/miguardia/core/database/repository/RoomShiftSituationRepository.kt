package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.mapping.toDomainOccurrence
import com.blackatsystems.miguardia.core.database.mapping.toDomainSituation
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkSnapshot
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState
import com.blackatsystems.miguardia.core.domain.model.ShiftSituation
import com.blackatsystems.miguardia.core.domain.model.ShiftSituationExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftSituationMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ShiftSituationRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomShiftSituationRepository(
    private val database: MiGuardiaV2Database,
    private val clock: Clock = Clock.systemUTC(),
) : ShiftSituationRepository {
    private val dao = database.shiftSituationDao()
    private val actualReader = RoomShiftActualRepository(database)

    override fun observeForShift(shiftId: UUID): Flow<ShiftSituation?> =
        dao.observe(shiftId.toString()).map { it?.toDomainSituation() }

    override suspend fun inspect(shiftId: UUID): ShiftSituationExpectation? = database.withTransaction {
        inspectInside(shiftId.toString())
    }

    override suspend fun evidenceStorageKeys(): Set<String> = dao.getAll()
        .mapNotNull { it.evidenceStorageKey }
        .toSet()

    override suspend fun apply(mutation: ShiftSituationMutation) {
        try {
            database.withTransaction {
                val shiftId = mutation.expected.planned.shift.id.toString()
                val current = inspectInside(shiftId)
                if (current != mutation.expected) conflict()
                val previousActual = current.actual?.previousActual
                val replacement = mutation.replacement
                val targetStatus = replacement?.status ?: ShiftStatus.PLANNED
                val actualWillBeRemoved = previousActual != null
                if (actualWillBeRemoved && !mutation.removeIncompatibleActual) {
                    throw InvalidLocalDataException(
                        "La jornada tiene horario real y extras. Confirmá expresamente que se retirarán.",
                    )
                }
                val previousSituation = current.previous
                if (current.planned.shift.status == ShiftStatus.PLANNED && replacement == null) {
                    throw InvalidLocalDataException("La jornada ya está Planificada; no hay una situación para retirar.")
                }
                if (replacement != null) {
                    require(replacement.shiftId == current.planned.shift.id)
                    require(replacement.status != ShiftStatus.PLANNED)
                    require(replacement.createdAt == (previousSituation?.createdAt ?: replacement.createdAt)) {
                        "La corrección debe conservar la fecha de creación de la situación"
                    }
                    if (previousSituation != null) require(replacement.updatedAt.isAfter(previousSituation.updatedAt))
                }
                if (actualWillBeRemoved) {
                    database.shiftActualDao().deleteIntervals(shiftId)
                    if (database.shiftActualDao().deleteRecord(shiftId) != 1) conflict()
                }
                val timestamp = replacement?.updatedAt ?: nextTimestamp(current.planned.shift.updatedAt)
                val updatedShift = current.planned.shift.copy(status = targetStatus, updatedAt = timestamp)
                if (database.v2ShiftDao().updateShift(updatedShift.toEntity()) != 1) conflict()
                if (replacement == null) dao.delete(shiftId) else dao.replace(replacement.toEntity())

                current.recurringOccurrence?.takeIf { it.state == RecurringOccurrenceState.AUTOMATIC }?.let { occurrence ->
                    val occurrenceTimestamp = nextTimestamp(occurrence.updatedAt, timestamp)
                    val customized = occurrence.copy(
                        state = RecurringOccurrenceState.CUSTOMIZED,
                        updatedAt = occurrenceTimestamp,
                    )
                    if (database.recurringPlanDao().updateOccurrence(customized.toEntity()) != 1) conflict()
                }
                val saved = inspectInside(shiftId) ?: conflict()
                if (saved.planned.shift.status != targetStatus || saved.previous != replacement) conflict()
            }
        } catch (error: SQLiteConstraintException) {
            throw ConflictingLocalWriteException(
                "La jornada cambió mientras confirmabas. Revisala e intentá nuevamente.",
            )
        }
    }

    private suspend fun inspectInside(shiftId: String): ShiftSituationExpectation? {
        val row = database.v2ShiftDao().getShiftWithSnapshot(shiftId) ?: return null
        val write = V2ShiftWrite(row.shift.toDomain(), row.snapshot.toDomainWorkSnapshot())
        val situation = dao.get(shiftId)?.toDomainSituation()
        if (situation != null && situation.status != write.shift.status) {
            throw InvalidLocalDataException("La situación no coincide con el estado de la jornada.")
        }
        if (situation == null && write.shift.status == ShiftStatus.PLANNED) Unit
        val actual = actualReader.readExpectationInside(shiftId)
        val occurrence = database.recurringPlanDao().getOccurrenceForShift(shiftId)?.toDomainOccurrence()
        return ShiftSituationExpectation(write, situation, actual, occurrence)
    }

    private fun nextTimestamp(previous: Instant, candidate: Instant = clock.instant()): Instant =
        if (candidate.isAfter(previous)) candidate else previous.plusMillis(1)

    private fun conflict(): Nothing = throw ConflictingLocalWriteException(
        "La jornada o su situación cambió mientras confirmabas. Revisala e intentá nuevamente.",
    )
}
