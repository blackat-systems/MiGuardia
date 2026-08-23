package com.blackatsystems.miguardia.core.domain.model

import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.normalizeRequiredWorkText
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ShiftWorkSnapshot(
    val shiftId: UUID,
    val timelineId: UUID,
    val sector: WorkSector,
    val configurationRevisionId: UUID,
    val workPlaceId: UUID,
    val objectiveId: UUID,
    val templateId: UUID,
    val workTypeId: UUID,
    val workTypeNameSnapshot: String,
    val workTypeBehaviorSnapshot: WorkTypeBehavior,
) {
    init {
        require(
            workTypeNameSnapshot == normalizeRequiredWorkText(
                workTypeNameSnapshot,
                "El nombre historico del tipo de trabajo",
            ),
        ) { "El nombre historico del tipo de trabajo debe estar normalizado" }
    }
}

data class V2ShiftWrite(
    val shift: Shift,
    val snapshot: ShiftWorkSnapshot,
) {
    init {
        require(shift.id == snapshot.shiftId) {
            "La jornada y su fotografia deben compartir el identificador"
        }
        require(shift.sourceObjectiveId == snapshot.objectiveId) {
            "La jornada y su fotografia deben compartir el objetivo fisico"
        }
    }
}

data class ShiftOccupancyVersion(
    val shiftId: UUID,
    val localStartDate: LocalDate,
    val startAt: Instant,
    val endAt: Instant,
    val status: ShiftStatus,
    val updatedAt: Instant,
)

@ConsistentCopyVisibility
data class ShiftOccupancyExpectation private constructor(
    val startDateInclusive: LocalDate,
    val endDateInclusive: LocalDate,
    val observedShifts: Set<ShiftOccupancyVersion>,
) {
    companion object {
        fun capture(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
            shifts: Iterable<Shift>,
        ): ShiftOccupancyExpectation {
            require(!endDateInclusive.isBefore(startDateInclusive)) {
                "La ventana de ocupacion no puede terminar antes de empezar"
            }
            val versions = shifts.map(Shift::toOccupancyVersion)
            require(versions.map { it.shiftId }.distinct().size == versions.size) {
                "Una ocupacion observada no puede repetir una jornada"
            }
            require(versions.all { it.localStartDate in startDateInclusive..endDateInclusive }) {
                "Las jornadas observadas deben pertenecer a la ventana de ocupacion"
            }
            return ShiftOccupancyExpectation(
                startDateInclusive = startDateInclusive,
                endDateInclusive = endDateInclusive,
                observedShifts = versions.toSet(),
            )
        }
    }
}

private fun Shift.toOccupancyVersion(): ShiftOccupancyVersion = ShiftOccupancyVersion(
    shiftId = id,
    localStartDate = localStartDate,
    startAt = startAt,
    endAt = endAt,
    status = status,
    updatedAt = updatedAt,
)

data class V2ShiftBatchMutation(
    val shiftIdsToDelete: Set<UUID> = emptySet(),
    val shiftsToInsert: List<V2ShiftWrite> = emptyList(),
    val shiftsToUpdate: List<V2ShiftWrite> = emptyList(),
    val explicitDayStatusDatesToClear: Set<LocalDate> = emptySet(),
) {
    init {
        val insertedIds = shiftsToInsert.map { it.shift.id }
        val updatedIds = shiftsToUpdate.map { it.shift.id }
        require(insertedIds.distinct().size == insertedIds.size) {
            "Una jornada V2 no puede insertarse dos veces en el mismo lote"
        }
        require(updatedIds.distinct().size == updatedIds.size) {
            "Una jornada V2 no puede actualizarse dos veces en el mismo lote"
        }
        require(insertedIds.none(updatedIds::contains)) {
            "Una jornada V2 no puede insertarse y actualizarse en el mismo lote"
        }
        require(shiftIdsToDelete.none(insertedIds::contains) && shiftIdsToDelete.none(updatedIds::contains)) {
            "Una jornada no puede borrarse y escribirse en el mismo lote"
        }
        val writes = shiftsToInsert + shiftsToUpdate
        val timelines = writes.map { it.snapshot.timelineId }.distinct()
        require(timelines.size <= 1) {
            "Una carga V2 no puede mezclar lineas temporales"
        }
        val sectors = writes.map { it.snapshot.sector }.distinct()
        require(sectors.size <= 1) {
            "Una carga V2 no puede mezclar sectores"
        }
    }
}
