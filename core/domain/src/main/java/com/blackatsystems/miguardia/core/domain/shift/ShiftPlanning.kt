package com.blackatsystems.miguardia.core.domain.shift

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.util.Locale
import kotlin.math.abs

enum class OccupiedDatePolicy {
    REPLACE,
    KEEP_OCCUPIED,
    ADD_SECOND_SHIFT,
    CANCEL,
}

sealed interface ShiftPlanningWarning {
    data class SameDate(val first: Shift, val second: Shift) : ShiftPlanningWarning
    data class Overlap(val first: Shift, val second: Shift) : ShiftPlanningWarning
    data class ShortRest(
        val first: Shift,
        val second: Shift,
        val actualRest: Duration,
    ) : ShiftPlanningWarning
}

data class ShiftBatchPlan(
    val mutation: ShiftBatchMutation,
    val occupiedDates: Set<LocalDate>,
    val omittedDates: Set<LocalDate>,
    val warnings: List<ShiftPlanningWarning>,
)

fun buildShift(
    id: UUID,
    date: LocalDate,
    objective: Objective,
    combination: ScheduleCombination,
    position: String?,
    timestamp: Instant,
    zoneId: ZoneId,
): Shift {
    if (combination.objectiveId != objective.id) {
        throw InvalidLocalDataException("El horario no pertenece al objetivo seleccionado.")
    }
    val start = date.atTime(combination.startTime).atZone(zoneId).toInstant()
    val endDate = if (combination.endTime > combination.startTime) date else date.plusDays(1)
    val end = endDate.atTime(combination.endTime).atZone(zoneId).toInstant()
    return Shift(
        id = id,
        startAt = start,
        endAt = end,
        zoneId = zoneId,
        localStartDate = date,
        objectiveNameSnapshot = objective.fullName.trim(),
        objectiveAbbreviationSnapshot = objective.abbreviation.trim().uppercase(Locale.ROOT),
        objectiveAddressSnapshot = objective.address?.trim()?.takeIf(String::isNotEmpty),
        startTimeSnapshot = combination.startTime,
        endTimeSnapshot = combination.endTime,
        colorArgbSnapshot = combination.colorArgb,
        position = position?.trim()?.takeIf(String::isNotEmpty),
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = objective.id,
        sourceScheduleCombinationId = combination.id,
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}

fun editShift(
    original: Shift,
    date: LocalDate,
    objective: Objective,
    combination: ScheduleCombination,
    position: String?,
    updatedAt: Instant,
): Shift = buildShift(
    id = original.id,
    date = date,
    objective = objective,
    combination = combination,
    position = position,
    timestamp = updatedAt,
    zoneId = original.zoneId,
).copy(
    status = original.status,
    createdAt = original.createdAt,
)

fun duplicateShift(
    original: Shift,
    id: UUID,
    date: LocalDate,
    timestamp: Instant,
): Shift {
    val start = date.atTime(original.startTimeSnapshot).atZone(original.zoneId).toInstant()
    val endDate = if (original.endTimeSnapshot > original.startTimeSnapshot) date else date.plusDays(1)
    val end = endDate.atTime(original.endTimeSnapshot).atZone(original.zoneId).toInstant()
    return original.copy(
        id = id,
        startAt = start,
        endAt = end,
        localStartDate = date,
        status = ShiftStatus.PLANNED,
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}

fun validateSingleMonth(dates: Set<LocalDate>): YearMonth {
    if (dates.isEmpty()) throw InvalidLocalDataException("Elegí al menos una fecha.")
    val months = dates.map(YearMonth::from).distinct()
    if (months.size != 1) {
        throw InvalidLocalDataException("La selección múltiple debe pertenecer a un solo mes.")
    }
    return months.single()
}

fun planShiftBatch(
    selectedDates: Set<LocalDate>,
    existingShifts: List<Shift>,
    candidates: List<Shift>,
    policy: OccupiedDatePolicy,
    editingShiftId: UUID? = null,
): ShiftBatchPlan {
    validateSingleMonth(selectedDates)
    if (candidates.map { it.localStartDate }.toSet() != selectedDates) {
        throw InvalidLocalDataException("Las guardias preparadas no coinciden con las fechas seleccionadas.")
    }
    val relevantExisting = existingShifts.filterNot { it.id == editingShiftId }
    val occupiedDates = relevantExisting
        .filter { it.localStartDate in selectedDates }
        .mapTo(linkedSetOf()) { it.localStartDate }
    if (policy == OccupiedDatePolicy.CANCEL) {
        return ShiftBatchPlan(
            mutation = ShiftBatchMutation(),
            occupiedDates = occupiedDates,
            omittedDates = selectedDates,
            warnings = emptyList(),
        )
    }
    val omittedDates = if (policy == OccupiedDatePolicy.KEEP_OCCUPIED) occupiedDates else emptySet()
    val insertions = candidates.filterNot { it.localStartDate in omittedDates }
    val deletions = buildSet {
        if (policy == OccupiedDatePolicy.REPLACE) {
            relevantExisting
                .filter { it.localStartDate in occupiedDates }
                .mapTo(this) { it.id }
        }
    }
    val remaining = relevantExisting.filterNot { it.id in deletions }
    return ShiftBatchPlan(
        mutation = ShiftBatchMutation(
            shiftIdsToDelete = deletions,
            shiftsToInsert = insertions,
        ),
        occupiedDates = occupiedDates,
        omittedDates = omittedDates,
        warnings = evaluateShiftWarnings(remaining, insertions),
    )
}

fun evaluateShiftWarnings(
    existingShifts: List<Shift>,
    candidateShifts: List<Shift>,
    minimumRest: Duration = Duration.ofHours(12),
): List<ShiftPlanningWarning> {
    val activeExisting = existingShifts.filter(Shift::countsForRestWarning)
    val activeCandidates = candidateShifts.filter(Shift::countsForRestWarning)
    val warnings = mutableListOf<ShiftPlanningWarning>()
    activeCandidates.forEachIndexed { index, candidate ->
        val others = activeExisting + activeCandidates.drop(index + 1)
        others.forEach { other ->
            val (first, second) = if (candidate.startAt <= other.startAt) {
                candidate to other
            } else {
                other to candidate
            }
            if (candidate.localStartDate == other.localStartDate) {
                warnings += ShiftPlanningWarning.SameDate(first, second)
            }
            when {
                second.startAt < first.endAt -> warnings += ShiftPlanningWarning.Overlap(first, second)
                Duration.between(first.endAt, second.startAt) < minimumRest -> {
                    warnings += ShiftPlanningWarning.ShortRest(
                        first = first,
                        second = second,
                        actualRest = Duration.between(first.endAt, second.startAt),
                    )
                }
            }
        }
    }
    return warnings.distinct()
}

fun areColorsTooSimilar(firstArgb: Int, secondArgb: Int, threshold: Int = 72): Boolean {
    require(threshold >= 0)
    val red = abs(((firstArgb shr 16) and 0xFF) - ((secondArgb shr 16) and 0xFF))
    val green = abs(((firstArgb shr 8) and 0xFF) - ((secondArgb shr 8) and 0xFF))
    val blue = abs((firstArgb and 0xFF) - (secondArgb and 0xFF))
    return red + green + blue < threshold
}

private fun Shift.countsForRestWarning(): Boolean =
    status != ShiftStatus.CANCELLED && status != ShiftStatus.ABSENT
