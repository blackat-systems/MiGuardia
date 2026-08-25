package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.exactDurationMinutes
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class HoursSegmentBoundaryReason {
    NATURAL_PERIOD,
    REFERENCE_RESTART,
    NEXT_REFERENCE_REVISION,
}

sealed interface HoursTargetState {
    data object PendingSetup : HoursTargetState
    data object NotUsed : HoursTargetState
    data object Unknown : HoursTargetState
    data object MissingPerPeriodValue : HoursTargetState
    data class Defined(val requiredMinutes: PositiveMinutes) : HoursTargetState
}

data class HoursReferenceSegment(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
    val ownerRevision: EffectiveRevision<WorkConfiguration>,
    val naturalWindow: DateWindow?,
    val target: HoursTargetState,
    val startsBecause: HoursSegmentBoundaryReason,
    val endsBecause: HoursSegmentBoundaryReason,
) {
    init {
        require(endExclusive.isAfter(startInclusive)) { "El tramo debe tener al menos un día" }
        naturalWindow?.let { window ->
            require(startInclusive in window && !endExclusive.isAfter(window.endExclusive)) {
                "El tramo debe permanecer dentro de su período natural"
            }
        }
    }

    val isShortNaturalSegment: Boolean
        get() = naturalWindow != null &&
            (startInclusive != naturalWindow.startInclusive || endExclusive != naturalWindow.endExclusive)
}

fun resolveHoursReferenceSegment(
    history: WorkConfigurationHistory,
    date: LocalDate,
): HoursReferenceSegment? {
    val revision = history.timeline.revisionAt(date) ?: return null
    val reference = revision.value.hoursReference
    val period = reference.periodOrNull()
    if (period == null) {
        val revisionsThroughDate = history.timeline.revisions
            .takeWhile { candidate -> !candidate.effectiveFrom.isAfter(date) }
        val groupStart = revisionsThroughDate.zipWithNext()
            .indexOfLast { (previous, current) ->
                previous.value.referenceIdentity() != current.value.referenceIdentity()
            }
            .let { lastChangeIndex -> revisionsThroughDate[lastChangeIndex + 1] }
        val nextBoundary = history.timeline.revisions
            .firstOrNull { candidate ->
                candidate.effectiveFrom.isAfter(date) &&
                    candidate.value.referenceIdentity() != revision.value.referenceIdentity()
            }
            ?.effectiveFrom
        return HoursReferenceSegment(
            startInclusive = groupStart.effectiveFrom,
            endExclusive = nextBoundary ?: LocalDate.MAX,
            ownerRevision = groupStart,
            naturalWindow = null,
            target = reference.targetFor(null, history.perPeriodHoursValues),
            startsBecause = HoursSegmentBoundaryReason.NEXT_REFERENCE_REVISION,
            endsBecause = HoursSegmentBoundaryReason.NEXT_REFERENCE_REVISION,
        )
    }

    val startedOn = requireNotNull(revision.value.hoursReferenceStartedOn) {
        "Una referencia con período debe conservar la fecha elegida de inicio"
    }
    require(!date.isBefore(startedOn)) {
        "La referencia consultada todavía no comenzó"
    }
    val naturalWindow = period.windowContaining(date)
    val start = maxOf(naturalWindow.startInclusive, startedOn)
    val nextReferenceBoundary = history.timeline.revisions
        .asSequence()
        .filter { it.effectiveFrom.isAfter(start) }
        .filter { candidate -> candidate.value.referenceIdentity() != revision.value.referenceIdentity() }
        .map { it.effectiveFrom }
        .firstOrNull()
    val end = listOfNotNull(naturalWindow.endExclusive, nextReferenceBoundary).minOrNull()
        ?: naturalWindow.endExclusive
    val owner = history.timeline.revisionAt(start) ?: revision
    return HoursReferenceSegment(
        startInclusive = start,
        endExclusive = end,
        ownerRevision = owner,
        naturalWindow = naturalWindow,
        target = owner.value.hoursReference.targetFor(naturalWindow, history.perPeriodHoursValues),
        startsBecause = if (start == startedOn) {
            HoursSegmentBoundaryReason.REFERENCE_RESTART
        } else {
            HoursSegmentBoundaryReason.NATURAL_PERIOD
        },
        endsBecause = if (nextReferenceBoundary != null && end == nextReferenceBoundary) {
            HoursSegmentBoundaryReason.NEXT_REFERENCE_REVISION
        } else {
            HoursSegmentBoundaryReason.NATURAL_PERIOD
        },
    )
}

fun nextNaturalPeriodStart(reference: HoursReference, date: LocalDate): LocalDate? =
    reference.periodOrNull()?.windowContaining(date)?.endExclusive

data class WorkedShiftSource(
    val planned: Shift,
    val actual: ShiftActualAggregate?,
) {
    init {
        require(actual == null || actual.record.shiftId == planned.id) {
            "El horario real no corresponde a la jornada"
        }
    }

    val ownerLocalDate: LocalDate
        get() = actual?.record?.actualStart
            ?.atZone(planned.zoneId)
            ?.toLocalDate()
            ?: planned.startAt.atZone(planned.zoneId).toLocalDate()
}

data class WorkProtectionPeriod(
    val startDateInclusive: LocalDate,
    val endDateInclusive: LocalDate,
) {
    init {
        require(!endDateInclusive.isBefore(startDateInclusive)) {
            "La protección laboral no puede terminar antes de comenzar"
        }
    }

    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDateInclusive) && !date.isAfter(endDateInclusive)
}

data class HistoricalExtraClassKey(
    val id: UUID,
    val name: String,
    val helpsMeetHoursReference: Boolean,
    val showDedicatedSummary: Boolean,
)

data class ExtraClassProgress(
    val key: HistoricalExtraClassKey,
    val shiftMinutes: Long,
    val independentMinutes: Long,
) {
    val totalMinutes: Long
        get() = Math.addExact(shiftMinutes, independentMinutes)
}

data class HoursProgress(
    val segment: HoursReferenceSegment,
    val regularWorkedMinutes: Long,
    val extrasByClass: List<ExtraClassProgress>,
    val totalWorkedMinutes: Long,
    val helpsMeetReferenceMinutes: Long,
    val doesNotHelpReferenceMinutes: Long,
    val pendingScheduledMinutes: Long,
    val targetMinutes: Long?,
    val missingMinutes: Long?,
    val excessMinutes: Long?,
    val completionPercentage: Double?,
)

fun calculateHoursProgress(
    segment: HoursReferenceSegment,
    shifts: Iterable<WorkedShiftSource>,
    independentExtras: Iterable<IndependentExtraWorkRecord>,
    clock: Clock,
    zoneId: ZoneId,
    protectionPeriods: Iterable<WorkProtectionPeriod> = emptyList(),
): HoursProgress {
    val normalizedNow = clock.withZone(zoneId).instant().truncatedTo(ChronoUnit.MINUTES)
    val classTotals = linkedMapOf<HistoricalExtraClassKey, MutableExtraMinutes>()
    var regular = 0L
    var pending = 0L

    val protections = protectionPeriods.toList()
    shifts.filter { it.ownerLocalDate in segment }.forEach { source ->
        if (source.planned.status != ShiftStatus.PLANNED) return@forEach
        val actual = source.actual
        if (actual == null && protections.any { source.ownerLocalDate in it }) return@forEach
        val start = actual?.record?.actualStart ?: source.planned.startAt
        val end = actual?.record?.actualEnd ?: source.planned.endAt
        val interval = elapsedInterval(start, end, normalizedNow)
        pending = Math.addExact(pending, interval.pendingMinutes)
        if (actual == null) {
            regular = Math.addExact(regular, interval.workedMinutes)
        } else {
            var elapsedExtra = 0L
            actual.extraIntervals.forEach { extra ->
                val worked = elapsedInterval(extra.start, extra.end, normalizedNow).workedMinutes
                elapsedExtra = Math.addExact(elapsedExtra, worked)
                val key = HistoricalExtraClassKey(
                    id = extra.extraWorkClassId,
                    name = extra.classNameSnapshot,
                    helpsMeetHoursReference = extra.helpsMeetHoursReferenceSnapshot,
                    showDedicatedSummary = extra.showDedicatedSummarySnapshot,
                )
                val totals = classTotals.getOrPut(key, ::MutableExtraMinutes)
                totals.shift = Math.addExact(totals.shift, worked)
            }
            regular = Math.addExact(regular, Math.subtractExact(interval.workedMinutes, elapsedExtra))
        }
    }

    independentExtras
        .filter { it.ownerLocalDate in segment }
        .forEach { extra ->
            val interval = elapsedInterval(extra.start, extra.end, normalizedNow)
            pending = Math.addExact(pending, interval.pendingMinutes)
            val key = HistoricalExtraClassKey(
                id = extra.extraWorkClassId,
                name = extra.snapshot.className,
                helpsMeetHoursReference = extra.snapshot.helpsMeetHoursReference,
                showDedicatedSummary = extra.snapshot.showDedicatedSummary,
            )
            val totals = classTotals.getOrPut(key, ::MutableExtraMinutes)
            totals.independent = Math.addExact(totals.independent, interval.workedMinutes)
        }

    val extras = classTotals.entries
        .sortedWith(compareBy({ it.key.name.lowercase() }, { it.key.id }))
        .map { (key, value) -> ExtraClassProgress(key, value.shift, value.independent) }
    val allExtraMinutes = extras.fold(0L) { total, value -> Math.addExact(total, value.totalMinutes) }
    val helpingExtras = extras
        .filter { it.key.helpsMeetHoursReference }
        .fold(0L) { total, value -> Math.addExact(total, value.totalMinutes) }
    val nonHelpingExtras = Math.subtractExact(allExtraMinutes, helpingExtras)
    val totalWorked = Math.addExact(regular, allExtraMinutes)
    val helps = Math.addExact(regular, helpingExtras)
    val target = (segment.target as? HoursTargetState.Defined)?.requiredMinutes?.value
    val missing = target?.let { Math.max(0L, Math.subtractExact(it, helps)) }
    val excess = target?.let { Math.max(0L, Math.subtractExact(helps, it)) }
    val percentage = target?.let { known -> helps.toDouble() * 100.0 / known.toDouble() }
    return HoursProgress(
        segment = segment,
        regularWorkedMinutes = regular,
        extrasByClass = extras,
        totalWorkedMinutes = totalWorked,
        helpsMeetReferenceMinutes = helps,
        doesNotHelpReferenceMinutes = nonHelpingExtras,
        pendingScheduledMinutes = pending,
        targetMinutes = target,
        missingMinutes = missing,
        excessMinutes = excess,
        completionPercentage = percentage,
    )
}

private operator fun HoursReferenceSegment.contains(date: LocalDate): Boolean =
    !date.isBefore(startInclusive) && date.isBefore(endExclusive)

private data class ReferenceIdentity(
    val reference: HoursReference,
    val startedOn: LocalDate?,
)

private fun WorkConfiguration.referenceIdentity(): ReferenceIdentity =
    ReferenceIdentity(hoursReference, hoursReferenceStartedOn)

private fun HoursReference.periodOrNull(): HoursPeriod? = when (this) {
    HoursReference.PendingSetup,
    HoursReference.NotUsed,
    -> null

    is HoursReference.Unknown -> period
    is HoursReference.Fixed -> period
    is HoursReference.PerPeriod -> period
}

private fun HoursReference.targetFor(
    naturalWindow: DateWindow?,
    values: PerPeriodHoursValues,
): HoursTargetState = when (this) {
    HoursReference.PendingSetup -> HoursTargetState.PendingSetup
    HoursReference.NotUsed -> HoursTargetState.NotUsed
    is HoursReference.Unknown -> HoursTargetState.Unknown
    is HoursReference.Fixed -> HoursTargetState.Defined(requiredMinutes)
    is HoursReference.PerPeriod -> {
        val window = requireNotNull(naturalWindow) { "La referencia por período necesita una ventana" }
        when (val lookup = values.valueFor(keyFor(window))) {
            PerPeriodHoursLookup.Missing -> HoursTargetState.MissingPerPeriodValue
            is PerPeriodHoursLookup.Defined -> HoursTargetState.Defined(lookup.entry.requiredMinutes)
        }
    }
}

private data class ElapsedInterval(
    val workedMinutes: Long,
    val pendingMinutes: Long,
)

private fun elapsedInterval(start: Instant, end: Instant, now: Instant): ElapsedInterval {
    require(start < end) { "Una fuente de trabajo debe tener duración positiva" }
    val total = exactDurationMinutes(start, end)
    return when {
        !now.isAfter(start) -> ElapsedInterval(workedMinutes = 0L, pendingMinutes = total)
        !now.isBefore(end) -> ElapsedInterval(workedMinutes = total, pendingMinutes = 0L)
        else -> {
            val worked = exactDurationMinutes(start, now)
            ElapsedInterval(workedMinutes = worked, pendingMinutes = Math.subtractExact(total, worked))
        }
    }
}

private class MutableExtraMinutes(
    var shift: Long = 0L,
    var independent: Long = 0L,
)
