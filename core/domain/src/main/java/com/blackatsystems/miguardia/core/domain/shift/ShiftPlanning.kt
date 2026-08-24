package com.blackatsystems.miguardia.core.domain.shift

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
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

fun validateSingleMonth(dates: Set<LocalDate>): YearMonth {
    if (dates.isEmpty()) throw InvalidLocalDataException("Elegí al menos una fecha.")
    val months = dates.map(YearMonth::from).distinct()
    if (months.size != 1) {
        throw InvalidLocalDataException("La selección múltiple debe pertenecer a un solo mes.")
    }
    return months.single()
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
