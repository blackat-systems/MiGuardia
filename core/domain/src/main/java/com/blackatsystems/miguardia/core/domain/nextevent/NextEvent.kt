package com.blackatsystems.miguardia.core.domain.nextevent

import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.model.calculateAvailabilityIntervalBreakdown
import com.blackatsystems.miguardia.core.domain.model.resolveAvailabilityActiveWorkIntervals
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Collections
import java.util.UUID

enum class NextEventPrimary {
    ONGOING_SHIFT,
    ONGOING_AVAILABILITY,
    UPCOMING_SHIFT,
    UPCOMING_AVAILABILITY,
    DAY_OFF,
    NONE,
}

sealed interface NextEventIdentity {
    val trackingKey: String
    val stableOrderKey: String

    data class Shift(val shiftId: UUID) : NextEventIdentity {
        override val trackingKey: String = "shift:$shiftId"
        override val stableOrderKey: String = trackingKey
    }

    data class Availability(
        val windowId: UUID,
        val segmentStart: Instant,
        val segmentEnd: Instant,
    ) : NextEventIdentity {
        init {
            require(segmentStart < segmentEnd) {
                "Un tramo efectivo de disponibilidad debe tener duracion positiva"
            }
        }

        override val trackingKey: String = buildString {
            append("availability:")
            append(windowId)
            append(':')
            append(segmentStart.toEpochMilli())
            append(':')
            append(segmentEnd.toEpochMilli())
        }
        override val stableOrderKey: String = trackingKey
    }

    companion object {
        fun parseTrackingKey(value: String): NextEventIdentity? {
            val normalized = value.trim()
            runCatching { UUID.fromString(normalized) }.getOrNull()?.let { legacyId ->
                return Shift(legacyId)
            }
            if (normalized.startsWith("shift:")) {
                return normalized.substringAfter("shift:")
                    .let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?.let(::Shift)
            }
            if (!normalized.startsWith("availability:")) return null
            val parts = normalized.split(':')
            if (parts.size != 4) return null
            return runCatching {
                Availability(
                    windowId = UUID.fromString(parts[1]),
                    segmentStart = Instant.ofEpochMilli(parts[2].toLong()),
                    segmentEnd = Instant.ofEpochMilli(parts[3].toLong()),
                )
            }.getOrNull()
        }
    }
}

sealed interface NextEventItem {
    val identity: NextEventIdentity
    val start: Instant
    val end: Instant
    val zoneId: ZoneId
    val ownerLocalDate: LocalDate

    data class Shift(
        val shiftId: UUID,
        override val start: Instant,
        override val end: Instant,
        override val zoneId: ZoneId,
        override val ownerLocalDate: LocalDate,
        val sector: WorkSector,
        val workTypeNameSnapshot: String,
        val workTypeBehaviorSnapshot: WorkTypeBehavior,
        val placeNameSnapshot: String,
        val placeAbbreviationSnapshot: String,
        val startTimeSnapshot: LocalTime,
        val endTimeSnapshot: LocalTime,
        val colorArgbSnapshot: Int,
        val positionSnapshot: String?,
        val hasHistoricalAddress: Boolean,
    ) : NextEventItem {
        override val identity: NextEventIdentity = NextEventIdentity.Shift(shiftId)
    }

    data class Availability(
        val windowId: UUID,
        override val start: Instant,
        override val end: Instant,
        override val zoneId: ZoneId,
        override val ownerLocalDate: LocalDate,
        val labelSnapshot: String,
        val isResumption: Boolean,
    ) : NextEventItem {
        override val identity: NextEventIdentity = NextEventIdentity.Availability(
            windowId = windowId,
            segmentStart = start,
            segmentEnd = end,
        )
    }
}

data class NextEventInput(
    val shifts: List<V2ShiftWrite>,
    val availabilityWindows: List<AvailabilityWindowRecord>,
    val actualsByShiftId: Map<UUID, ShiftActualAggregate>,
    val independentExtras: List<IndependentExtraWorkRecord>,
    val explicitDayStatuses: List<ExplicitDayStatus>,
    val vacations: List<Vacation>,
    val medicalLeaves: List<MedicalLeave>,
)

@ConsistentCopyVisibility
data class NextEventResult private constructor(
    val referenceInstant: Instant,
    val zoneId: ZoneId,
    val events: List<NextEventItem>,
    val activeEvents: List<NextEventItem>,
    val upcomingEvents: List<NextEventItem>,
    val primaryEvents: List<NextEventItem>,
    val nextDayOff: LocalDate?,
    val primaryEvent: NextEventPrimary,
    val remaining: Duration,
) {
    companion object {
        internal fun create(
            referenceInstant: Instant,
            zoneId: ZoneId,
            events: List<NextEventItem>,
            activeEvents: List<NextEventItem>,
            upcomingEvents: List<NextEventItem>,
            primaryEvents: List<NextEventItem>,
            nextDayOff: LocalDate?,
            primaryEvent: NextEventPrimary,
            remaining: Duration,
        ): NextEventResult = NextEventResult(
            referenceInstant = referenceInstant,
            zoneId = zoneId,
            events = events.immutableCopy(),
            activeEvents = activeEvents.immutableCopy(),
            upcomingEvents = upcomingEvents.immutableCopy(),
            primaryEvents = primaryEvents.immutableCopy(),
            nextDayOff = nextDayOff,
            primaryEvent = primaryEvent,
            remaining = remaining,
        )
    }
}

val NextEventItemOrder: Comparator<NextEventItem> = compareBy<NextEventItem>(
    NextEventItem::start,
    { event -> if (event is NextEventItem.Shift) 0 else 1 },
    NextEventItem::end,
    { event -> event.identity.stableOrderKey },
)

/**
 * Legacy precondition used by the weather surface. It is not the V2 event
 * projection and must not be used by the card or notifications.
 */
fun Shift.isEligibleForWeather(
    now: Instant,
    vacations: List<Vacation>,
): Boolean = status == ShiftStatus.PLANNED &&
    endAt > now &&
    vacations.none { vacation -> localStartDate in vacation.startDate..vacation.endDateInclusive }

/**
 * Builds the single V2 work-event truth used by the top card and local alerts.
 * It is pure: every source, the reference instant and the civil zone are
 * explicit, and the returned collections cannot be mutated by consumers.
 */
fun projectNextEvent(
    now: Instant,
    zoneId: ZoneId,
    input: NextEventInput,
): NextEventResult {
    val today = now.atZone(zoneId).toLocalDate()
    val vacations = input.vacations.toList()
    val medicalLeaves = input.medicalLeaves.toList()
    val protectedRanges: List<ClosedRange<LocalDate>> = buildList {
        vacations.forEach { add(it.startDate..it.endDateInclusive) }
        medicalLeaves.forEach { add(it.startDate..it.endDateInclusive) }
    }
    val actuals = input.actualsByShiftId.toMap()
    val shifts = input.shifts
        .toList()
        .deduplicateWrites()
    val eligibleShiftEvents = shifts
        .asSequence()
        .filter { write ->
            val shift = write.shift
            shift.status == ShiftStatus.PLANNED &&
                shift.endAt > now &&
                shift.id !in actuals &&
                protectedRanges.none { shift.localStartDate in it }
        }
        .map(V2ShiftWrite::toNextEventItem)
        .toList()

    val shiftsByContext = shifts.groupBy { write ->
        WorkContext(write.snapshot.timelineId, write.snapshot.sector)
    }
    val extrasByContext = input.independentExtras
        .toList()
        .groupBy { extra -> WorkContext(extra.timelineId, extra.sector) }
    val availabilityEvents = input.availabilityWindows
        .toList()
        .deduplicateWindows()
        .flatMap { window ->
            val context = WorkContext(window.timelineId, window.sector)
            val activeWork = resolveAvailabilityActiveWorkIntervals(
                shifts = shiftsByContext[context].orEmpty(),
                actualsByShiftId = actuals,
                independentExtras = extrasByContext[context].orEmpty(),
                protectedOwnerDates = protectedRanges,
            )
            val isProtected = protectedRanges.any { window.ownerLocalDate in it }
            calculateAvailabilityIntervalBreakdown(
                window = window,
                activeWork = activeWork,
                isProtected = isProtected,
                clock = Clock.fixed(now, zoneId),
            ).effectiveProjectedAtEnd.mapIndexedNotNull { index, segment ->
                if (segment.end <= now) {
                    null
                } else {
                    NextEventItem.Availability(
                        windowId = window.id,
                        start = segment.start,
                        end = segment.end,
                        zoneId = window.zoneId,
                        ownerLocalDate = window.ownerLocalDate,
                        labelSnapshot = window.labelSnapshot,
                        isResumption = index > 0,
                    )
                }
            }
        }
    val events = (eligibleShiftEvents + availabilityEvents)
        .sortedWith(NextEventItemOrder)
    val active = events.filter { event -> event.start <= now && now < event.end }
    val activeShifts = active.filterIsInstance<NextEventItem.Shift>()
    val activeAvailability = active.filterIsInstance<NextEventItem.Availability>()
    val nextStart = events
        .asSequence()
        .filter { event -> event.start > now }
        .map(NextEventItem::start)
        .minOrNull()
    val upcoming = if (nextStart == null) {
        emptyList()
    } else {
        events.filter { event -> event.start == nextStart }
    }
    val nextDayOff = input.explicitDayStatuses
        .toList()
        .asSequence()
        .filter { status ->
            status.type == ExplicitDayStatusType.DAY_OFF && status.date >= today
        }
        .map(ExplicitDayStatus::date)
        .minOrNull()
    val primary = when {
        activeShifts.isNotEmpty() -> NextEventPrimary.ONGOING_SHIFT
        activeAvailability.isNotEmpty() -> NextEventPrimary.ONGOING_AVAILABILITY
        upcoming.firstOrNull() is NextEventItem.Shift -> NextEventPrimary.UPCOMING_SHIFT
        upcoming.firstOrNull() is NextEventItem.Availability -> NextEventPrimary.UPCOMING_AVAILABILITY
        nextDayOff != null -> NextEventPrimary.DAY_OFF
        else -> NextEventPrimary.NONE
    }
    val primaryItems: List<NextEventItem> = when (primary) {
        NextEventPrimary.ONGOING_SHIFT -> activeShifts
        NextEventPrimary.ONGOING_AVAILABILITY -> activeAvailability
        NextEventPrimary.UPCOMING_SHIFT,
        NextEventPrimary.UPCOMING_AVAILABILITY,
        -> upcoming
        NextEventPrimary.DAY_OFF,
        NextEventPrimary.NONE,
        -> emptyList()
    }
    val remaining = when (primary) {
        NextEventPrimary.ONGOING_SHIFT,
        NextEventPrimary.ONGOING_AVAILABILITY,
        -> Duration.between(now, primaryItems.first().end)
        NextEventPrimary.UPCOMING_SHIFT,
        NextEventPrimary.UPCOMING_AVAILABILITY,
        -> Duration.between(now, primaryItems.first().start)
        NextEventPrimary.DAY_OFF,
        NextEventPrimary.NONE,
        -> Duration.ZERO
    }.coerceNonNegative()

    return NextEventResult.create(
        referenceInstant = now,
        zoneId = zoneId,
        events = events,
        activeEvents = active,
        upcomingEvents = upcoming,
        primaryEvents = primaryItems,
        nextDayOff = nextDayOff,
        primaryEvent = primary,
        remaining = remaining,
    )
}

fun V2ShiftWrite.toNextEventItem(): NextEventItem.Shift = NextEventItem.Shift(
    shiftId = shift.id,
    start = shift.startAt,
    end = shift.endAt,
    zoneId = shift.zoneId,
    ownerLocalDate = shift.localStartDate,
    sector = snapshot.sector,
    workTypeNameSnapshot = snapshot.workTypeNameSnapshot,
    workTypeBehaviorSnapshot = snapshot.workTypeBehaviorSnapshot,
    placeNameSnapshot = shift.objectiveNameSnapshot,
    placeAbbreviationSnapshot = shift.objectiveAbbreviationSnapshot,
    startTimeSnapshot = shift.startTimeSnapshot,
    endTimeSnapshot = shift.endTimeSnapshot,
    colorArgbSnapshot = shift.colorArgbSnapshot,
    positionSnapshot = shift.position,
    hasHistoricalAddress = !shift.objectiveAddressSnapshot.isNullOrBlank(),
)

private fun List<V2ShiftWrite>.deduplicateWrites(): List<V2ShiftWrite> = groupBy { it.shift.id }
    .map { (id, copies) ->
        require(copies.distinct().size == 1) {
            "La jornada $id aparece con dos fotografias V2 incompatibles"
        }
        copies.first()
    }

private fun List<AvailabilityWindowRecord>.deduplicateWindows(): List<AvailabilityWindowRecord> =
    groupBy(AvailabilityWindowRecord::id).map { (id, copies) ->
        require(copies.distinct().size == 1) {
            "La disponibilidad $id aparece con dos fotografias incompatibles"
        }
        copies.first()
    }

private fun <T> List<T>.immutableCopy(): List<T> =
    Collections.unmodifiableList(toList())

private fun Duration.coerceNonNegative(): Duration = if (isNegative) Duration.ZERO else this

private data class WorkContext(
    val timelineId: UUID,
    val sector: WorkSector,
)
