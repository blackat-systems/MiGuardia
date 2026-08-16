package com.blackatsystems.miguardia.core.domain.nextevent

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class NextEventPrimary {
    ONGOING_SHIFT,
    UPCOMING_SHIFT,
    DAY_OFF,
    NONE,
}

data class NextEventResult(
    val referenceInstant: Instant,
    val ongoingShifts: List<Shift>,
    val upcomingShifts: List<Shift>,
    val nextDayOff: LocalDate?,
    val primaryEvent: NextEventPrimary,
    val remaining: Duration,
)

val NextEventShiftOrder: Comparator<Shift> = compareBy<Shift>(
    Shift::startAt,
    Shift::endAt,
    { it.id.toString() },
)

fun Shift.isEligibleUpcomingWork(
    now: Instant,
    vacations: List<Vacation>,
): Boolean = status == ShiftStatus.PLANNED &&
    endAt > now &&
    vacations.none { vacation -> localStartDate in vacation.startDate..vacation.endDateInclusive }

/**
 * Builds the reusable next-event projection without reading a clock or mutating persisted data.
 */
fun projectNextEvent(
    now: Instant,
    zoneId: ZoneId,
    shifts: List<Shift>,
    explicitDayStatuses: List<ExplicitDayStatus>,
    vacations: List<Vacation>,
): NextEventResult {
    val today = now.atZone(zoneId).toLocalDate()
    val candidateShifts = shifts
        .asSequence()
        .filter { shift -> shift.isEligibleUpcomingWork(now, vacations) }
        .sortedWith(NextEventShiftOrder)
        .toList()
    val ongoing = candidateShifts.filter { shift -> shift.startAt <= now }
    val nextStart = candidateShifts
        .asSequence()
        .filter { shift -> shift.startAt > now }
        .map(Shift::startAt)
        .minOrNull()
    val upcoming = if (nextStart == null) {
        emptyList()
    } else {
        candidateShifts.filter { shift -> shift.startAt == nextStart }
    }
    val nextDayOff = explicitDayStatuses
        .asSequence()
        .filter { status ->
            status.type == ExplicitDayStatusType.DAY_OFF && status.date >= today
        }
        .map(ExplicitDayStatus::date)
        .minOrNull()

    val primary = when {
        ongoing.isNotEmpty() -> NextEventPrimary.ONGOING_SHIFT
        upcoming.isNotEmpty() -> NextEventPrimary.UPCOMING_SHIFT
        nextDayOff != null -> NextEventPrimary.DAY_OFF
        else -> NextEventPrimary.NONE
    }
    val remaining = when (primary) {
        NextEventPrimary.ONGOING_SHIFT -> Duration.between(now, ongoing.first().endAt)
        NextEventPrimary.UPCOMING_SHIFT -> Duration.between(now, upcoming.first().startAt)
        NextEventPrimary.DAY_OFF,
        NextEventPrimary.NONE,
        -> Duration.ZERO
    }.coerceNonNegative()

    return NextEventResult(
        referenceInstant = now,
        ongoingShifts = ongoing,
        upcomingShifts = upcoming,
        nextDayOff = nextDayOff,
        primaryEvent = primary,
        remaining = remaining,
    )
}

private fun Duration.coerceNonNegative(): Duration = if (isNegative) Duration.ZERO else this
