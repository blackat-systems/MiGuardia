package com.blackatsystems.miguardia.core.database.repository

import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.validateRange
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomExplicitDayStatusRepository(
    private val database: MiGuardiaV2Database,
) : ExplicitDayStatusRepository {
    private val dao = database.explicitDayStatusDao()
    override fun observeBetween(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<ExplicitDayStatus>> {
        validateRange(startDateInclusive, endDateInclusive)
        return dao.observeBetween(startDateInclusive.toString(), endDateInclusive.toString())
            .map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeFrom(startDateInclusive: LocalDate): Flow<List<ExplicitDayStatus>> =
        dao.observeFrom(startDateInclusive.toString())
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun set(date: LocalDate, type: ExplicitDayStatusType) {
        dao.set(ExplicitDayStatus(date, type).toEntity())
    }

    override suspend fun setAll(dates: Set<LocalDate>, type: ExplicitDayStatusType) {
        dao.setAll(dates.sorted().map { date -> ExplicitDayStatus(date, type).toEntity() })
    }

    override suspend fun clear(date: LocalDate) {
        dao.clear(date.toString())
    }

    override suspend fun updateUndefined(
        date: LocalDate,
        expected: ExplicitDayStatus?,
        mark: Boolean,
    ) {
        database.withTransaction {
            val current = dao.getAll().singleOrNull { it.localDate == date.toString() }?.toDomain()
            if (current != expected) {
                throw ConflictingLocalWriteException("El día cambió mientras confirmabas. Revisalo nuevamente.")
            }
            if (mark) {
                val conflicts = buildList {
                    if (database.shiftDao().getStartingBetween(date.toString(), date.toString()).isNotEmpty()) add("jornadas")
                    if (current?.type == ExplicitDayStatusType.DAY_OFF) add("Franco")
                }
                if (conflicts.isNotEmpty()) {
                    throw ConflictingLocalWriteException(
                        "No se puede marcar Sin definir porque el día conserva ${conflicts.joinToString()}.",
                    )
                }
                dao.set(ExplicitDayStatus(date, ExplicitDayStatusType.UNDEFINED).toEntity())
            } else {
                if (current?.type != ExplicitDayStatusType.UNDEFINED) {
                    throw ConflictingLocalWriteException("El día ya no está marcado como Sin definir.")
                }
                dao.clear(date.toString())
            }
        }
    }

    override suspend fun updateDaysOff(
        expectedTimelineId: UUID,
        dates: Set<LocalDate>,
        markAsDayOff: Boolean,
    ) {
        val selected = dates.toSortedSet()
        require(selected.isNotEmpty() && selected.size <= 366) {
            "Seleccioná entre 1 y 366 días."
        }
        database.withTransaction {
            val history = database.requireValidV2LocalData()
            if (history?.timeline?.id != expectedTimelineId) {
                throw ConflictingLocalWriteException(
                    "La configuración cambió. Volvé a seleccionar los días.",
                )
            }
            if (markAsDayOff) {
                // Match Calendar ownership, not civil-day interval overlap. A shift that
                // started yesterday may finish today without preventing today's Franco.
                // Keep its complete planned/actual interval and all recorded hours intact.
                val occupiedDates = database.shiftDao().getStartingBetween(
                    selected.first().toString(), selected.last().toString(),
                ).asSequence()
                    .filter { it.status == "PLANNED" }
                    .map { LocalDate.parse(it.localStartDate) }
                    .toSet()
                val conflicts = selected.filter { it in occupiedDates }
                if (conflicts.isNotEmpty()) {
                    val formatted = conflicts.take(5).joinToString(", ") {
                        it.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
                    }
                    throw ConflictingLocalWriteException(
                        "Hay jornadas en $formatted${if (conflicts.size > 5) " y otros días" else ""}. " +
                            "No se marcó ningún franco. Revisá esas jornadas o cambiá la selección.",
                    )
                }
                dao.setAll(selected.map { ExplicitDayStatus(it, ExplicitDayStatusType.DAY_OFF).toEntity() })
            } else {
                val removable = dao.getAll().filter {
                    it.toDomain().let { status ->
                        status.date in selected && status.type == ExplicitDayStatusType.DAY_OFF
                    }
                }
                removable.forEach { dao.clear(it.localDate) }
            }
        }
    }
}
