package com.blackatsystems.miguardia.core.domain.shift

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.normalizeOptionalWorkText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

data class V2ShiftBatchPlan(
    val mutation: V2ShiftBatchMutation,
    val occupiedDates: Set<LocalDate>,
    val omittedDates: Set<LocalDate>,
    val warnings: List<ShiftPlanningWarning>,
)

fun buildV2ShiftWrite(
    id: UUID,
    date: LocalDate,
    objective: Objective,
    workPlace: WorkPlace,
    workType: WorkType,
    template: WorkTemplate,
    configurationContext: ResolvedWorkConfigurationRevision,
    position: String?,
    timestamp: Instant,
    zoneId: ZoneId,
): V2ShiftWrite {
    validateV2Sources(
        date = date,
        objective = objective,
        workPlace = workPlace,
        workType = workType,
        template = template,
        configurationContext = configurationContext,
    )
    val startAt = date.atTime(template.startTime).atZone(zoneId).toInstant()
    val endDate = if (template.endTime > template.startTime) date else date.plusDays(1)
    val endAt = endDate.atTime(template.endTime).atZone(zoneId).toInstant()
    val shift = Shift(
        id = id,
        startAt = startAt,
        endAt = endAt,
        zoneId = zoneId,
        localStartDate = date,
        objectiveNameSnapshot = objective.fullName,
        objectiveAbbreviationSnapshot = objective.abbreviation,
        objectiveAddressSnapshot = objective.address,
        startTimeSnapshot = template.startTime,
        endTimeSnapshot = template.endTime,
        colorArgbSnapshot = template.colorArgb,
        position = normalizeOptionalWorkText(position),
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = objective.id,
        sourceScheduleCombinationId = template.legacyScheduleCombinationId,
        createdAt = timestamp,
        updatedAt = timestamp,
    )
    return V2ShiftWrite(
        shift = shift,
        snapshot = ShiftWorkSnapshot(
            shiftId = shift.id,
            timelineId = workPlace.timelineId,
            sector = workPlace.sector,
            configurationRevisionId = configurationContext.revision.id,
            workPlaceId = workPlace.id,
            objectiveId = objective.id,
            templateId = template.id,
            workTypeId = workType.id,
            workTypeNameSnapshot = workType.name,
            workTypeBehaviorSnapshot = workType.behavior,
        ),
    )
}

fun editV2ShiftWrite(
    original: V2ShiftWrite,
    date: LocalDate,
    objective: Objective,
    workPlace: WorkPlace,
    workType: WorkType,
    template: WorkTemplate,
    configurationContext: ResolvedWorkConfigurationRevision,
    position: String?,
    updatedAt: Instant,
): V2ShiftWrite {
    require(date == original.shift.localStartDate) {
        "Este editor no puede mover una jornada a otra fecha"
    }
    val persistedUpdate = nextPersistedV2ShiftUpdate(original.shift.updatedAt, updatedAt)
    val rebuilt = buildV2ShiftWrite(
        id = original.shift.id,
        date = date,
        objective = objective,
        workPlace = workPlace,
        workType = workType,
        template = template,
        configurationContext = configurationContext,
        position = position,
        timestamp = persistedUpdate,
        zoneId = original.shift.zoneId,
    )
    return rebuilt.copy(
        shift = rebuilt.shift.copy(
            status = original.shift.status,
            createdAt = original.shift.createdAt,
        ),
    )
}

fun editV2ShiftPositionOnly(
    original: V2ShiftWrite,
    position: String?,
    updatedAt: Instant,
): V2ShiftWrite {
    val normalizedPosition = normalizeOptionalWorkText(position)
    require(normalizedPosition != original.shift.position) {
        "La correccion de puesto debe modificar el valor confirmado"
    }
    return original.copy(
        shift = original.shift.copy(
            position = normalizedPosition,
            updatedAt = nextPersistedV2ShiftUpdate(original.shift.updatedAt, updatedAt),
        ),
    )
}

fun isExactV2PositionOnlyEdit(
    original: V2ShiftWrite,
    updated: V2ShiftWrite,
): Boolean {
    if (updated.snapshot != original.snapshot) return false
    if (!updated.shift.updatedAt.isAfter(original.shift.updatedAt)) return false
    if (updated.shift.position == original.shift.position) return false
    return updated.shift == original.shift.copy(
        position = normalizeOptionalWorkText(updated.shift.position),
        updatedAt = updated.shift.updatedAt,
    )
}

private fun nextPersistedV2ShiftUpdate(
    original: Instant,
    candidate: Instant,
): Instant {
    val storedOriginal = original.truncatedTo(ChronoUnit.MILLIS)
    val storedCandidate = candidate.truncatedTo(ChronoUnit.MILLIS)
    return if (storedCandidate.isAfter(storedOriginal)) {
        storedCandidate
    } else {
        storedOriginal.plusMillis(1)
    }
}

fun planV2ShiftBatch(
    selectedDates: Set<LocalDate>,
    existingShifts: List<Shift>,
    candidates: List<V2ShiftWrite>,
    policy: OccupiedDatePolicy,
    editingShiftId: UUID? = null,
): V2ShiftBatchPlan {
    val legacyPlan = planShiftBatch(
        selectedDates = selectedDates,
        existingShifts = existingShifts,
        candidates = candidates.map(V2ShiftWrite::shift),
        policy = policy,
        editingShiftId = editingShiftId,
    )
    val plannedIds = legacyPlan.mutation.shiftsToInsert.mapTo(hashSetOf()) { it.id }
    val plannedWrites = candidates.filter { it.shift.id in plannedIds }
    val (insertions, updates) = if (editingShiftId == null) {
        plannedWrites to emptyList()
    } else {
        require(candidates.size == 1 && candidates.single().shift.id == editingShiftId) {
            "Editar una jornada V2 requiere un unico candidato con el identificador original"
        }
        emptyList<V2ShiftWrite>() to plannedWrites
    }
    return V2ShiftBatchPlan(
        mutation = V2ShiftBatchMutation(
            shiftIdsToDelete = legacyPlan.mutation.shiftIdsToDelete,
            shiftsToInsert = insertions,
            shiftsToUpdate = updates,
            explicitDayStatusDatesToClear = legacyPlan.mutation.explicitDayStatusDatesToClear,
        ),
        occupiedDates = legacyPlan.occupiedDates,
        omittedDates = legacyPlan.omittedDates,
        warnings = legacyPlan.warnings,
    )
}

private fun validateV2Sources(
    date: LocalDate,
    objective: Objective,
    workPlace: WorkPlace,
    workType: WorkType,
    template: WorkTemplate,
    configurationContext: ResolvedWorkConfigurationRevision,
) {
    if (
        workPlace.objectiveId != objective.id ||
        template.workPlaceId != workPlace.id ||
        template.objectiveId != objective.id ||
        template.workTypeId != workType.id
    ) {
        throw InvalidLocalDataException("El lugar, el tipo y el horario seleccionados no coinciden.")
    }
    if (
        workPlace.timelineId != workType.timelineId ||
        workPlace.timelineId != template.timelineId ||
        workPlace.sector != workType.sector ||
        workPlace.sector != template.sector ||
        workPlace.timelineId != configurationContext.timelineId ||
        workPlace.sector != configurationContext.revision.value.sector
    ) {
        throw InvalidLocalDataException("La seleccion no pertenece a la misma forma de trabajar.")
    }
    if (!workPlace.isActive || !workType.isActive || !template.isActive) {
        throw InvalidLocalDataException("El lugar, el tipo y el horario deben estar activos para una carga nueva.")
    }
    if (configurationContext.referenceDate != date) {
        throw InvalidLocalDataException("La revision laboral no fue resuelta para la fecha de la jornada.")
    }
}
