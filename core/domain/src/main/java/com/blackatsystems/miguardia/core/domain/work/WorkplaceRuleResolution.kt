package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.RetroactiveWorkplaceRuleException
import java.time.Instant
import java.time.LocalDate

data class CivilWorkRuleSegment(
    val localDate: LocalDate,
    val startAt: Instant,
    val endAt: Instant,
    val ruleRevision: WorkplaceRuleRevision,
) {
    init {
        require(endAt.isAfter(startAt)) { "Un tramo civil debe tener duracion positiva" }
        require(!ruleRevision.effectiveFrom.isAfter(localDate)) {
            "La regla del tramo civil todavia no estaba vigente"
        }
    }
}

fun resolveWorkplaceRuleSegments(
    shift: Shift,
    snapshot: ShiftWorkSnapshot,
    revisions: List<WorkplaceRuleRevision>,
): List<CivilWorkRuleSegment> {
    validateShiftForRuleResolution(shift, snapshot)
    validateRuleTimeline(snapshot, revisions)
    return civilIntervals(shift).map { interval ->
        val revision = revisions
            .asSequence()
            .filter { !it.effectiveFrom.isAfter(interval.localDate) }
            .maxWithOrNull(compareBy<WorkplaceRuleRevision> { it.effectiveFrom }.thenBy { it.id })
            ?: throw InvalidLocalDataException(
                "El lugar no tiene reglas vigentes para el ${interval.localDate}.",
            )
        CivilWorkRuleSegment(
            localDate = interval.localDate,
            startAt = interval.startAt,
            endAt = interval.endAt,
            ruleRevision = revision,
        )
    }
}

fun validateWorkplaceRuleInsertion(
    candidate: WorkplaceRuleRevision,
    existingRevisions: List<WorkplaceRuleRevision>,
    existingV2Shifts: List<V2ShiftWrite>,
    confirmationNow: Instant,
) {
    existingRevisions.forEach { revision ->
        requireSameRuleContext(candidate, revision)
    }
    if (existingRevisions.any { it.id == candidate.id }) {
        throw InvalidLocalDataException("Ya existe una revision de reglas con ese identificador.")
    }
    if (existingRevisions.any { it.effectiveFrom == candidate.effectiveFrom }) {
        throw InvalidLocalDataException("Ya existen reglas para ese lugar desde la fecha elegida.")
    }
    val nextEffectiveDate = existingRevisions
        .asSequence()
        .map { it.effectiveFrom }
        .filter { it.isAfter(candidate.effectiveFrom) }
        .minOrNull()
    val reachesStartedShift = existingV2Shifts.any { write ->
        val shift = write.shift
        val snapshot = write.snapshot
        snapshot.workPlaceId == candidate.workPlaceId &&
            !shift.startAt.isAfter(confirmationNow) &&
            civilIntervals(shift).any { interval ->
                !interval.localDate.isBefore(candidate.effectiveFrom) &&
                    (nextEffectiveDate == null || interval.localDate.isBefore(nextEffectiveDate))
            }
    }
    if (reachesStartedShift) {
        throw RetroactiveWorkplaceRuleException(candidate.effectiveFrom)
    }
}

private data class CivilInterval(
    val localDate: LocalDate,
    val startAt: Instant,
    val endAt: Instant,
)

private fun civilIntervals(shift: Shift): List<CivilInterval> {
    if (!shift.endAt.isAfter(shift.startAt)) {
        throw InvalidLocalDataException("La jornada debe finalizar despues de comenzar.")
    }
    val calculatedLocalStartDate = shift.startAt.atZone(shift.zoneId).toLocalDate()
    if (calculatedLocalStartDate != shift.localStartDate) {
        throw InvalidLocalDataException("La fecha local de la jornada no coincide con su inicio.")
    }
    val result = mutableListOf<CivilInterval>()
    var cursor = shift.startAt
    while (cursor < shift.endAt) {
        val localDate = cursor.atZone(shift.zoneId).toLocalDate()
        val nextDayStart = localDate.plusDays(1).atStartOfDay(shift.zoneId).toInstant()
        if (!nextDayStart.isAfter(cursor)) {
            throw InvalidLocalDataException("No se pudo dividir la jornada en dias locales validos.")
        }
        val segmentEnd = minOf(shift.endAt, nextDayStart)
        result += CivilInterval(localDate, cursor, segmentEnd)
        cursor = segmentEnd
    }
    return result
}

private fun validateShiftForRuleResolution(
    shift: Shift,
    snapshot: ShiftWorkSnapshot,
) {
    if (shift.id != snapshot.shiftId || shift.sourceObjectiveId != snapshot.objectiveId) {
        throw InvalidLocalDataException("La jornada y su fotografia laboral no coinciden.")
    }
}

private fun validateRuleTimeline(
    snapshot: ShiftWorkSnapshot,
    revisions: List<WorkplaceRuleRevision>,
) {
    if (revisions.isEmpty()) {
        throw InvalidLocalDataException("El lugar no conserva ninguna revision de reglas.")
    }
    if (
        revisions.any { revision ->
            revision.timelineId != snapshot.timelineId ||
                revision.sector != snapshot.sector ||
                revision.workPlaceId != snapshot.workPlaceId ||
                revision.objectiveId != snapshot.objectiveId
        }
    ) {
        throw InvalidLocalDataException("Las reglas no pertenecen al lugar de la jornada.")
    }
    if (revisions.map { it.id }.distinct().size != revisions.size) {
        throw InvalidLocalDataException("Hay revisiones de reglas repetidas.")
    }
    if (revisions.map { it.effectiveFrom }.distinct().size != revisions.size) {
        throw InvalidLocalDataException("Hay mas de una revision de reglas desde la misma fecha.")
    }
}

private fun requireSameRuleContext(
    first: WorkplaceRuleRevision,
    second: WorkplaceRuleRevision,
) {
    if (
        first.timelineId != second.timelineId ||
        first.sector != second.sector ||
        first.workPlaceId != second.workPlaceId ||
        first.objectiveId != second.objectiveId
    ) {
        throw InvalidLocalDataException("Las revisiones no pertenecen al mismo lugar.")
    }
}
