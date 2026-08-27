package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.model.ExactMinuteInterval
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.subtractExactMinuteIntervals
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

enum class HoursContributionKind {
    REGULAR_SHIFT,
    SHIFT_EXTRA,
    INDEPENDENT_EXTRA,
}

enum class HoursContributionPhase {
    WORKED,
    PENDING,
}

/**
 * Una fila exacta del libro de horas compartido por avance y Resumen.
 *
 * Cada fila representa una sola fase y su intervalo coincide exactamente con
 * sus minutos. Una fuente en curso se parte en filas WORKED/PENDING y una
 * jornada con extras se particiona además en franjas habituales y extra.
 */
data class HoursContribution(
    val contributionId: UUID,
    val sourceId: UUID,
    val ownerLocalDate: LocalDate,
    val start: Instant,
    val end: Instant,
    val kind: HoursContributionKind,
    val phase: HoursContributionPhase,
    val extraClass: HistoricalExtraClassKey? = null,
) {
    init {
        require(start < end) { "Una contribución de horas debe tener duración positiva" }
        require((kind == HoursContributionKind.REGULAR_SHIFT) == (extraClass == null)) {
            "Sólo las contribuciones extra deben conservar su clase histórica"
        }
    }

    val minutes: Long
        get() = ExactMinuteInterval(start, end).durationMinutes

    val workedMinutes: Long
        get() = if (phase == HoursContributionPhase.WORKED) minutes else 0L

    val pendingMinutes: Long
        get() = if (phase == HoursContributionPhase.PENDING) minutes else 0L
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
    val contributions = calculateHoursContributions(
        segment = segment,
        shifts = shifts,
        independentExtras = independentExtras,
        clock = clock,
        zoneId = zoneId,
        protectionPeriods = protectionPeriods,
    )
    return summarizeHoursContributions(segment, contributions)
}

fun summarizeHoursContributions(
    segment: HoursReferenceSegment,
    contributions: Iterable<HoursContribution>,
): HoursProgress {
    val classTotals = linkedMapOf<HistoricalExtraClassKey, MutableExtraMinutes>()
    var regular = 0L
    var pending = 0L
    contributions.forEach { contribution ->
        pending = Math.addExact(pending, contribution.pendingMinutes)
        when (contribution.kind) {
            HoursContributionKind.REGULAR_SHIFT -> {
                regular = Math.addExact(regular, contribution.workedMinutes)
            }
            HoursContributionKind.SHIFT_EXTRA,
            HoursContributionKind.INDEPENDENT_EXTRA,
            -> {
                val totals = classTotals.getOrPut(requireNotNull(contribution.extraClass), ::MutableExtraMinutes)
                when (contribution.kind) {
                    HoursContributionKind.SHIFT_EXTRA -> {
                        totals.shift = Math.addExact(totals.shift, contribution.workedMinutes)
                    }
                    HoursContributionKind.INDEPENDENT_EXTRA -> {
                        totals.independent = Math.addExact(totals.independent, contribution.workedMinutes)
                    }
                    HoursContributionKind.REGULAR_SHIFT -> error("Rama imposible")
                }
            }
        }
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

fun calculateHoursContributions(
    segment: HoursReferenceSegment,
    shifts: Iterable<WorkedShiftSource>,
    independentExtras: Iterable<IndependentExtraWorkRecord>,
    clock: Clock,
    zoneId: ZoneId,
    protectionPeriods: Iterable<WorkProtectionPeriod> = emptyList(),
): List<HoursContribution> {
    val normalizedNow = clock.withZone(zoneId).instant().truncatedTo(ChronoUnit.MINUTES)
    val protections = protectionPeriods.toList()
    return buildList {
        shifts.filter { it.ownerLocalDate in segment }.forEach { source ->
            if (source.planned.status != ShiftStatus.PLANNED) return@forEach
            val actual = source.actual
            if (actual == null && protections.any { source.ownerLocalDate in it }) return@forEach
            val start = actual?.record?.actualStart ?: source.planned.startAt
            val end = actual?.record?.actualEnd ?: source.planned.endAt
            if (actual == null) {
                addTemporalContributions(
                    contributionId = source.planned.id,
                    sourceId = source.planned.id,
                    ownerLocalDate = source.ownerLocalDate,
                    interval = ExactMinuteInterval(start, end),
                    kind = HoursContributionKind.REGULAR_SHIFT,
                    extraClass = null,
                    now = normalizedNow,
                )
            } else {
                require(actual.extraIntervals.all { extra -> extra.start >= start && extra.end <= end }) {
                    "Los fragmentos extra deben permanecer dentro del horario real"
                }
                require(actual.extraIntervals.zipWithNext().none { (first, second) -> first.end > second.start }) {
                    "Los fragmentos extra no pueden superponerse"
                }
                val extraIntervals = actual.extraIntervals.map { extra ->
                    ExactMinuteInterval(extra.start, extra.end)
                }
                subtractExactMinuteIntervals(ExactMinuteInterval(start, end), extraIntervals).forEach { regular ->
                    addTemporalContributions(
                        contributionId = source.planned.id,
                        sourceId = source.planned.id,
                        ownerLocalDate = source.ownerLocalDate,
                        interval = regular,
                        kind = HoursContributionKind.REGULAR_SHIFT,
                        extraClass = null,
                        now = normalizedNow,
                    )
                }
                actual.extraIntervals.forEach { extra ->
                    addTemporalContributions(
                        contributionId = extra.id,
                        sourceId = source.planned.id,
                        ownerLocalDate = source.ownerLocalDate,
                        interval = ExactMinuteInterval(extra.start, extra.end),
                        kind = HoursContributionKind.SHIFT_EXTRA,
                        extraClass = HistoricalExtraClassKey(
                            id = extra.extraWorkClassId,
                            name = extra.classNameSnapshot,
                            helpsMeetHoursReference = extra.helpsMeetHoursReferenceSnapshot,
                            showDedicatedSummary = extra.showDedicatedSummarySnapshot,
                        ),
                        now = normalizedNow,
                    )
                }
            }
        }

        independentExtras
            .filter { it.ownerLocalDate in segment }
            .forEach { extra ->
                addTemporalContributions(
                    contributionId = extra.id,
                    sourceId = extra.id,
                    ownerLocalDate = extra.ownerLocalDate,
                    interval = ExactMinuteInterval(extra.start, extra.end),
                    kind = HoursContributionKind.INDEPENDENT_EXTRA,
                    extraClass = HistoricalExtraClassKey(
                        id = extra.extraWorkClassId,
                        name = extra.snapshot.className,
                        helpsMeetHoursReference = extra.snapshot.helpsMeetHoursReference,
                        showDedicatedSummary = extra.snapshot.showDedicatedSummary,
                    ),
                    now = normalizedNow,
                )
            }
    }.sortedWith(
        compareBy(
            HoursContribution::start,
            HoursContribution::end,
            HoursContribution::phase,
            HoursContribution::kind,
            HoursContribution::contributionId,
        ),
    )
}

private fun MutableList<HoursContribution>.addTemporalContributions(
    contributionId: UUID,
    sourceId: UUID,
    ownerLocalDate: LocalDate,
    interval: ExactMinuteInterval,
    kind: HoursContributionKind,
    extraClass: HistoricalExtraClassKey?,
    now: Instant,
) {
    val workedEnd = minOf(interval.end, now)
    if (interval.start < workedEnd) {
        add(
            HoursContribution(
                contributionId = contributionId,
                sourceId = sourceId,
                ownerLocalDate = ownerLocalDate,
                start = interval.start,
                end = workedEnd,
                kind = kind,
                phase = HoursContributionPhase.WORKED,
                extraClass = extraClass,
            ),
        )
    }
    val pendingStart = maxOf(interval.start, now)
    if (pendingStart < interval.end) {
        add(
            HoursContribution(
                contributionId = contributionId,
                sourceId = sourceId,
                ownerLocalDate = ownerLocalDate,
                start = pendingStart,
                end = interval.end,
                kind = kind,
                phase = HoursContributionPhase.PENDING,
                extraClass = extraClass,
            ),
        )
    }
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

private class MutableExtraMinutes(
    var shift: Long = 0L,
    var independent: Long = 0L,
)
