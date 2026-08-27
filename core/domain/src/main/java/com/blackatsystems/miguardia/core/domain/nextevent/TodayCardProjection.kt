package com.blackatsystems.miguardia.core.domain.nextevent

import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.model.effectiveWorkedInterval
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.UUID

enum class TodayShiftState {
    UPCOMING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    ABSENT,
    PROTECTED,
}

enum class TodayCardPrimary {
    ONGOING_SHIFT,
    UPCOMING_SHIFT,
    COMPLETED_SUMMARY,
    NO_WORK_TODAY,
    FUTURE_EVENT,
    EMPTY,
}

/**
 * Minimal, privacy-safe representation consumed by the top card.
 *
 * It deliberately exposes neither medical notes nor actual-time explanations.
 */
data class TodayShiftSummary(
    val shift: Shift,
    val state: TodayShiftState,
    val hasActualTime: Boolean,
    val isVacationProtected: Boolean,
    val isMedicalLeaveProtected: Boolean,
    val startedYesterday: Boolean,
)

data class TodayCardProjection(
    val referenceInstant: Instant,
    val date: LocalDate,
    val shifts: List<TodayShiftSummary>,
    val primary: TodayCardPrimary,
    val primaryShift: TodayShiftSummary?,
    val todayShiftCount: Int,
    val completedTodayCount: Int,
    val remaining: Duration,
    val futureEvent: NextEventResult,
) {
    val canExpand: Boolean
        get() = shifts.size > 1 || primary == TodayCardPrimary.COMPLETED_SUMMARY ||
            primary == TodayCardPrimary.NO_WORK_TODAY
}

/**
 * Builds the immutable projection for the civil date that owns the top card.
 * Planned snapshots remain untouched even when an actual interval exists.
 */
fun projectTodayCard(
    now: Instant,
    zoneId: ZoneId,
    todayShifts: List<Shift>,
    previousDayCandidates: List<Shift>,
    actualsByShiftId: Map<UUID, ShiftActualAggregate>,
    vacations: List<Vacation>,
    medicalLeaves: List<MedicalLeave>,
    futureEvent: NextEventResult,
): TodayCardProjection {
    val today = now.atZone(zoneId).toLocalDate()
    val yesterday = today.minusDays(1)
    val uniqueToday = todayShifts
        .asSequence()
        .filter { shift -> shift.localStartDate == today }
        .distinctBy(Shift::id)
        .map { shift ->
            shift.toTodaySummary(
                now = now,
                yesterday = yesterday,
                actual = actualsByShiftId[shift.id],
                vacations = vacations,
                medicalLeaves = medicalLeaves,
            )
        }
        .sortedWith(TodayShiftSummaryOrder)
        .toList()
    val activeFromYesterday = previousDayCandidates
        .asSequence()
        .filter { shift -> shift.localStartDate == yesterday }
        .distinctBy(Shift::id)
        .map { shift ->
            shift.toTodaySummary(
                now = now,
                yesterday = yesterday,
                actual = actualsByShiftId[shift.id],
                vacations = vacations,
                medicalLeaves = medicalLeaves,
            )
        }
        .filter { summary -> summary.state == TodayShiftState.IN_PROGRESS }
        .toList()
    val visible = (activeFromYesterday + uniqueToday)
        .distinctBy { summary -> summary.shift.id }
        .sortedWith(TodayShiftSummaryOrder)
    val ongoing = visible.firstOrNull { summary -> summary.state == TodayShiftState.IN_PROGRESS }
    val upcomingToday = uniqueToday.firstOrNull { summary -> summary.state == TodayShiftState.UPCOMING }
    val completedTodayCount = uniqueToday.count { summary -> summary.state == TodayShiftState.COMPLETED }
    val primary = when {
        ongoing != null -> TodayCardPrimary.ONGOING_SHIFT
        upcomingToday != null -> TodayCardPrimary.UPCOMING_SHIFT
        completedTodayCount > 0 -> TodayCardPrimary.COMPLETED_SUMMARY
        uniqueToday.isNotEmpty() -> TodayCardPrimary.NO_WORK_TODAY
        futureEvent.primaryEvent != NextEventPrimary.NONE -> TodayCardPrimary.FUTURE_EVENT
        else -> TodayCardPrimary.EMPTY
    }
    val primaryShift = when (primary) {
        TodayCardPrimary.ONGOING_SHIFT -> ongoing
        TodayCardPrimary.UPCOMING_SHIFT -> upcomingToday
        TodayCardPrimary.COMPLETED_SUMMARY,
        TodayCardPrimary.NO_WORK_TODAY,
        TodayCardPrimary.FUTURE_EVENT,
        TodayCardPrimary.EMPTY,
        -> null
    }
    val remaining = primaryShift?.let { summary ->
        val actual = actualsByShiftId[summary.shift.id]
        val (start, end) = effectiveWorkedInterval(summary.shift, actual)
        when (primary) {
            TodayCardPrimary.ONGOING_SHIFT -> Duration.between(now, end)
            TodayCardPrimary.UPCOMING_SHIFT -> Duration.between(now, start)
            else -> Duration.ZERO
        }
    }?.coerceNonNegative() ?: Duration.ZERO
    val immutableFutureEvent = futureEvent.copy(
        ongoingShifts = Collections.unmodifiableList(futureEvent.ongoingShifts.toList()),
        upcomingShifts = Collections.unmodifiableList(futureEvent.upcomingShifts.toList()),
    )

    return TodayCardProjection(
        referenceInstant = now,
        date = today,
        shifts = Collections.unmodifiableList(visible.toList()),
        primary = primary,
        primaryShift = primaryShift,
        todayShiftCount = uniqueToday.size,
        completedTodayCount = completedTodayCount,
        remaining = remaining,
        futureEvent = immutableFutureEvent,
    )
}

private val TodayShiftSummaryOrder: Comparator<TodayShiftSummary> =
    Comparator { first, second -> NextEventShiftOrder.compare(first.shift, second.shift) }

private fun Shift.toTodaySummary(
    now: Instant,
    yesterday: LocalDate,
    actual: ShiftActualAggregate?,
    vacations: List<Vacation>,
    medicalLeaves: List<MedicalLeave>,
): TodayShiftSummary {
    val vacationProtected = vacations.any { vacation ->
        localStartDate in vacation.startDate..vacation.endDateInclusive
    }
    val medicalLeaveProtected = medicalLeaves.any { leave ->
        localStartDate in leave.startDate..leave.endDateInclusive
    }
    val state = when (status) {
        ShiftStatus.CANCELLED -> TodayShiftState.CANCELLED
        ShiftStatus.ABSENT -> TodayShiftState.ABSENT
        ShiftStatus.PLANNED -> when {
            actual == null && (vacationProtected || medicalLeaveProtected) -> TodayShiftState.PROTECTED
            else -> {
                val (effectiveStart, effectiveEnd) = effectiveWorkedInterval(this, actual)
                when {
                    now < effectiveStart -> TodayShiftState.UPCOMING
                    now < effectiveEnd -> TodayShiftState.IN_PROGRESS
                    else -> TodayShiftState.COMPLETED
                }
            }
        }
    }
    return TodayShiftSummary(
        shift = this,
        state = state,
        hasActualTime = actual != null,
        isVacationProtected = vacationProtected,
        isMedicalLeaveProtected = medicalLeaveProtected,
        startedYesterday = localStartDate == yesterday,
    )
}

private fun Duration.coerceNonNegative(): Duration = if (isNegative) Duration.ZERO else this
