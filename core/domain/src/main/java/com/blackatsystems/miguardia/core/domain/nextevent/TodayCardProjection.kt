package com.blackatsystems.miguardia.core.domain.nextevent

import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
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

/** Privacy-safe row consumed by the top card. */
data class TodayShiftSummary(
    val event: NextEventItem.Shift,
    val state: TodayShiftState,
    val hasActualTime: Boolean,
    val isVacationProtected: Boolean,
    val isMedicalLeaveProtected: Boolean,
    val startedBeforeToday: Boolean,
)

@ConsistentCopyVisibility
data class TodayCardProjection private constructor(
    val referenceInstant: Instant,
    val date: LocalDate,
    val zoneId: ZoneId,
    val shifts: List<TodayShiftSummary>,
    val primary: TodayCardPrimary,
    val primaryShift: TodayShiftSummary?,
    val todayShiftCount: Int,
    val completedTodayCount: Int,
    val remaining: Duration,
    val futureEvent: NextEventResult,
) {
    val canExpand: Boolean
        get() = shifts.isNotEmpty()

    companion object {
        internal fun create(
            referenceInstant: Instant,
            date: LocalDate,
            zoneId: ZoneId,
            shifts: List<TodayShiftSummary>,
            primary: TodayCardPrimary,
            primaryShift: TodayShiftSummary?,
            todayShiftCount: Int,
            completedTodayCount: Int,
            remaining: Duration,
            futureEvent: NextEventResult,
        ): TodayCardProjection = TodayCardProjection(
            referenceInstant = referenceInstant,
            date = date,
            zoneId = zoneId,
            shifts = Collections.unmodifiableList(shifts.toList()),
            primary = primary,
            primaryShift = primaryShift,
            todayShiftCount = todayShiftCount,
            completedTodayCount = completedTodayCount,
            remaining = remaining,
            futureEvent = futureEvent,
        )
    }
}

/**
 * Builds the day-owned rows while delegating active/upcoming eligibility and
 * priority exclusively to [futureEvent].
 */
fun projectTodayCard(
    now: Instant,
    zoneId: ZoneId,
    shifts: List<V2ShiftWrite>,
    actualsByShiftId: Map<UUID, ShiftActualAggregate>,
    vacations: List<Vacation>,
    medicalLeaves: List<MedicalLeave>,
    futureEvent: NextEventResult,
): TodayCardProjection {
    require(futureEvent.referenceInstant == now && futureEvent.zoneId == zoneId) {
        "La tarjeta y la proyeccion laboral deben compartir instante y zona"
    }
    val today = now.atZone(zoneId).toLocalDate()
    val writesById = shifts
        .toList()
        .groupBy { it.shift.id }
        .mapValues { (id, copies) ->
            require(copies.distinct().size == 1) {
                "La jornada $id aparece con dos fotografias V2 incompatibles"
            }
            copies.first()
        }
    val todayRows = writesById.values
        .asSequence()
        .filter { write -> write.shift.localStartDate == today }
        .map { write ->
            write.toTodaySummary(
                now = now,
                today = today,
                actual = actualsByShiftId[write.shift.id],
                vacations = vacations,
                medicalLeaves = medicalLeaves,
            )
        }
        .sortedWith(TodayShiftSummaryOrder)
        .toList()
    val activeHistoricalRows = futureEvent.activeEvents
        .filterIsInstance<NextEventItem.Shift>()
        .filter { event -> event.ownerLocalDate < today }
        .mapNotNull { event ->
            writesById[event.shiftId]?.toTodaySummary(
                now = now,
                today = today,
                actual = actualsByShiftId[event.shiftId],
                vacations = vacations,
                medicalLeaves = medicalLeaves,
            )
        }
    val visible = (activeHistoricalRows + todayRows)
        .distinctBy { summary -> summary.event.shiftId }
        .sortedWith(TodayShiftSummaryOrder)
    val primaryEvent = futureEvent.primaryEvents.firstOrNull()
    val primaryShift = (primaryEvent as? NextEventItem.Shift)?.let { event ->
        visible.firstOrNull { summary -> summary.event.shiftId == event.shiftId }
    }
    val completedTodayCount = todayRows.count { summary -> summary.state == TodayShiftState.COMPLETED }
    val primaryEventStartsToday = primaryEvent?.start?.atZone(zoneId)?.toLocalDate() == today
    val primary = when {
        futureEvent.primaryEvent == NextEventPrimary.ONGOING_SHIFT && primaryShift != null -> {
            TodayCardPrimary.ONGOING_SHIFT
        }
        futureEvent.primaryEvent == NextEventPrimary.UPCOMING_SHIFT &&
            primaryShift?.event?.ownerLocalDate == today -> TodayCardPrimary.UPCOMING_SHIFT
        futureEvent.primaryEvent == NextEventPrimary.ONGOING_AVAILABILITY -> TodayCardPrimary.FUTURE_EVENT
        futureEvent.primaryEvent == NextEventPrimary.UPCOMING_AVAILABILITY && primaryEventStartsToday -> {
            TodayCardPrimary.FUTURE_EVENT
        }
        completedTodayCount > 0 -> TodayCardPrimary.COMPLETED_SUMMARY
        todayRows.isNotEmpty() -> TodayCardPrimary.NO_WORK_TODAY
        futureEvent.primaryEvent != NextEventPrimary.NONE -> TodayCardPrimary.FUTURE_EVENT
        else -> TodayCardPrimary.EMPTY
    }

    return TodayCardProjection.create(
        referenceInstant = now,
        date = today,
        zoneId = zoneId,
        shifts = visible,
        primary = primary,
        primaryShift = primaryShift.takeIf {
            primary == TodayCardPrimary.ONGOING_SHIFT || primary == TodayCardPrimary.UPCOMING_SHIFT
        },
        todayShiftCount = todayRows.size,
        completedTodayCount = completedTodayCount,
        remaining = if (
            primary == TodayCardPrimary.ONGOING_SHIFT ||
            primary == TodayCardPrimary.UPCOMING_SHIFT ||
            primary == TodayCardPrimary.FUTURE_EVENT
        ) {
            futureEvent.remaining
        } else {
            Duration.ZERO
        },
        futureEvent = futureEvent,
    )
}

private val TodayShiftSummaryOrder: Comparator<TodayShiftSummary> =
    compareBy<TodayShiftSummary>(
        { it.event.start },
        { it.event.end },
        { it.event.shiftId.toString() },
    )

private fun V2ShiftWrite.toTodaySummary(
    now: Instant,
    today: LocalDate,
    actual: ShiftActualAggregate?,
    vacations: List<Vacation>,
    medicalLeaves: List<MedicalLeave>,
): TodayShiftSummary {
    val vacationProtected = vacations.any { vacation ->
        shift.localStartDate in vacation.startDate..vacation.endDateInclusive
    }
    val medicalLeaveProtected = medicalLeaves.any { leave ->
        shift.localStartDate in leave.startDate..leave.endDateInclusive
    }
    val state = when (shift.status) {
        ShiftStatus.CANCELLED -> TodayShiftState.CANCELLED
        ShiftStatus.ABSENT -> TodayShiftState.ABSENT
        ShiftStatus.PLANNED -> when {
            actual == null && (vacationProtected || medicalLeaveProtected) -> TodayShiftState.PROTECTED
            else -> {
                val (effectiveStart, effectiveEnd) = effectiveWorkedInterval(shift, actual)
                when {
                    now < effectiveStart -> TodayShiftState.UPCOMING
                    now < effectiveEnd -> TodayShiftState.IN_PROGRESS
                    else -> TodayShiftState.COMPLETED
                }
            }
        }
    }
    return TodayShiftSummary(
        event = toNextEventItem(),
        state = state,
        hasActualTime = actual != null,
        isVacationProtected = vacationProtected,
        isMedicalLeaveProtected = medicalLeaveProtected,
        startedBeforeToday = shift.localStartDate < today,
    )
}
