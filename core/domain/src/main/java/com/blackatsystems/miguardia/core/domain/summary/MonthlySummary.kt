package com.blackatsystems.miguardia.core.domain.summary

import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.AvailabilityIntervalBreakdown
import com.blackatsystems.miguardia.core.domain.model.AvailabilityMinuteSegment
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.model.calculateAvailabilityIntervalBreakdown
import com.blackatsystems.miguardia.core.domain.model.resolveAvailabilityActiveWorkIntervals
import com.blackatsystems.miguardia.core.domain.work.DateWindow
import com.blackatsystems.miguardia.core.domain.work.HistoricalExtraClassKey
import com.blackatsystems.miguardia.core.domain.work.HoursContribution
import com.blackatsystems.miguardia.core.domain.work.HoursContributionKind
import com.blackatsystems.miguardia.core.domain.work.HoursProgress
import com.blackatsystems.miguardia.core.domain.work.HoursReferenceSegment
import com.blackatsystems.miguardia.core.domain.work.HoursSegmentBoundaryReason
import com.blackatsystems.miguardia.core.domain.work.HoursTargetState
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkProtectionPeriod
import com.blackatsystems.miguardia.core.domain.work.WorkedShiftSource
import com.blackatsystems.miguardia.core.domain.work.calculateHoursContributions
import com.blackatsystems.miguardia.core.domain.work.resolveHoursReferenceSegment
import com.blackatsystems.miguardia.core.domain.work.summarizeHoursContributions
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class SummaryOptionalFamily {
    NIGHTS,
    HOLIDAYS,
    WEEKENDS,
    PLANNED_VS_ACTUAL,
    WORK_PLACES,
    WORK_TYPES,
    EXTRA_CLASSES,
    SITUATIONS,
}

enum class SummaryValueUnit {
    MINUTES,
    COUNT,
}

enum class SummaryContributionKind {
    REGULAR_WORK,
    SHIFT_EXTRA,
    INDEPENDENT_EXTRA,
    PENDING_WORK,
    AVAILABILITY_PROGRAMMED,
    AVAILABILITY_EFFECTIVE,
    AVAILABILITY_REPLACED,
    AVAILABILITY_PENDING,
    AVAILABILITY_PROJECTED,
    NIGHT,
    HOLIDAY,
    WEEKEND,
    PLANNED_DURATION,
    ACTUAL_DURATION,
    ACTUAL_DIFFERENCE,
    ABSENCE,
    CANCELLATION,
    MEDICAL_LEAVE,
    VACATION,
    DAY_OFF,
    REFERENCE_TARGET,
    REFERENCE_WORK_OFFSET,
}

data class SummaryContribution(
    val id: String,
    val sourceId: String,
    val ownerLocalDate: LocalDate,
    val start: Instant?,
    val end: Instant?,
    val zoneId: ZoneId? = null,
    val value: Long,
    val unit: SummaryValueUnit,
    val kind: SummaryContributionKind,
    val sourceLabel: String,
    val workPlaceLabel: String? = null,
    val workTypeLabel: String? = null,
    val extraClassLabel: String? = null,
    val explanation: String? = null,
) {
    init {
        require((start == null) == (end == null)) {
            "Una contribución debe conservar ambos extremos o ninguno"
        }
        if (start != null && end != null) {
            require(start < end) { "El intervalo explicado debe tener duración positiva" }
            requireNotNull(zoneId) { "Un intervalo explicado debe conservar su zona horaria" }
            if (unit == SummaryValueUnit.MINUTES) {
                val duration = ChronoUnit.MINUTES.between(start, end)
                require(value == duration || value == -duration) {
                    "Los minutos explicados deben coincidir exactamente con su intervalo"
                }
            }
        }
    }
}

data class SummaryMetric(
    val id: String,
    val label: String,
    val value: Long,
    val unit: SummaryValueUnit,
    val contributions: List<SummaryContribution>,
) {
    init {
        require(contributions.all { it.unit == unit }) {
            "Todas las filas de una cifra deben usar la misma unidad"
        }
        val reconciled = contributions.fold(0L) { total, row -> Math.addExact(total, row.value) }
        require(reconciled == value) {
            "El detalle de $label suma $reconciled y la cifra informa $value"
        }
        require(contributions.map { it.id }.distinct().size == contributions.size) {
            "Las filas que explican $label deben tener identificadores únicos"
        }
    }
}

data class MonthlySummaryEssentials(
    val totalWorked: SummaryMetric?,
    val regularWorked: SummaryMetric?,
    val extras: SummaryMetric?,
    val pendingScheduled: SummaryMetric?,
)

data class SummaryCompliancePeriod(
    val segment: HoursReferenceSegment,
    val progress: HoursProgress,
    val contributingWork: SummaryMetric,
    val target: SummaryMetric?,
    val missing: SummaryMetric?,
    val excess: SummaryMetric?,
)

data class SummaryAvailability(
    val programmed: SummaryMetric,
    val effectiveElapsed: SummaryMetric,
    val replacedElapsed: SummaryMetric?,
    val pending: SummaryMetric?,
    val projectedEffectiveAtEnd: SummaryMetric,
)

data class SummaryOptionalSection(
    val family: SummaryOptionalFamily,
    val metrics: List<SummaryMetric>,
) {
    init {
        require(metrics.isNotEmpty()) { "Una sección opcional vacía no debe formar parte de la proyección" }
    }
}

data class MonthlySummaryProjection(
    val month: YearMonth,
    val essentials: MonthlySummaryEssentials,
    val compliance: List<SummaryCompliancePeriod>,
    val availability: SummaryAvailability?,
    val optionalSections: List<SummaryOptionalSection>,
    val hasContent: Boolean,
) {
    init {
        require(compliance == compliance.sortedBy { it.segment.startInclusive }) {
            "Los tramos de cumplimiento deben conservar orden cronológico"
        }
        require(optionalSections.map { it.family }.distinct().size == optionalSections.size) {
            "La proyección no puede repetir familias opcionales"
        }
        val metricIds = allMetrics().map { it.id }.toList()
        require(metricIds.distinct().size == metricIds.size) {
            "Cada cifra del Resumen debe tener un identificador único"
        }
    }

    fun metric(id: String): SummaryMetric? = allMetrics().firstOrNull { it.id == id }

    private fun allMetrics(): Sequence<SummaryMetric> = sequence {
        essentials.totalWorked?.let { yield(it) }
        essentials.regularWorked?.let { yield(it) }
        essentials.extras?.let { yield(it) }
        essentials.pendingScheduled?.let { yield(it) }
        compliance.forEach { period ->
            yield(period.contributingWork)
            period.target?.let { yield(it) }
            period.missing?.let { yield(it) }
            period.excess?.let { yield(it) }
        }
        availability?.let { summary ->
            yield(summary.programmed)
            yield(summary.effectiveElapsed)
            summary.replacedElapsed?.let { yield(it) }
            summary.pending?.let { yield(it) }
            yield(summary.projectedEffectiveAtEnd)
        }
        optionalSections.forEach { section -> section.metrics.forEach { yield(it) } }
    }
}

data class MonthlySummaryInput(
    val month: YearMonth,
    val configuration: WorkConfigurationHistory,
    val shifts: List<V2ShiftWrite>,
    val actuals: List<ShiftActualAggregate>,
    val independentExtras: List<IndependentExtraWorkRecord>,
    val availabilityWindows: List<AvailabilityWindowRecord>,
    val catalogs: List<WorkCatalog>,
    val holidays: List<Holiday>,
    val medicalLeaves: List<MedicalLeave>,
    val vacations: List<Vacation>,
    val explicitDayStatuses: List<ExplicitDayStatus>,
)

fun calculateMonthlySummary(
    input: MonthlySummaryInput,
    clock: Clock,
    zoneId: ZoneId,
): MonthlySummaryProjection {
    val monthWindow = DateWindow(input.month.atDay(1), input.month.plusMonths(1).atDay(1))
    val actualsByShiftId = input.actuals.associateBy { it.record.shiftId }.also { indexed ->
        require(indexed.size == input.actuals.size) { "No puede haber dos horarios reales para una jornada" }
    }
    val protections = (input.medicalLeaves.map { WorkProtectionPeriod(it.startDate, it.endDateInclusive) } +
        input.vacations.map { WorkProtectionPeriod(it.startDate, it.endDateInclusive) })
    val shiftSources = input.shifts.map { write ->
        WorkedShiftSource(write.shift, actualsByShiftId[write.shift.id])
    }
    val syntheticMonthSegment = HoursReferenceSegment(
        startInclusive = monthWindow.startInclusive,
        endExclusive = monthWindow.endExclusive,
        ownerRevision = input.configuration.timeline.revisions.first(),
        naturalWindow = null,
        target = HoursTargetState.NotUsed,
        startsBecause = HoursSegmentBoundaryReason.NATURAL_PERIOD,
        endsBecause = HoursSegmentBoundaryReason.NATURAL_PERIOD,
    )
    val monthlyLedger = calculateHoursContributions(
        segment = syntheticMonthSegment,
        shifts = shiftSources,
        independentExtras = input.independentExtras,
        clock = clock,
        zoneId = zoneId,
        protectionPeriods = protections,
    )
    val monthlyProgress = summarizeHoursContributions(syntheticMonthSegment, monthlyLedger)
    val metadata = WorkMetadataIndex(input.shifts, input.independentExtras)
    metadata.markActuals(input.actuals)
    val essentials = buildEssentials(monthlyProgress, monthlyLedger, metadata)
    val compliance = resolveSummaryComplianceSegments(input.configuration, input.month)
        .filter(HoursReferenceSegment::isApplicableComplianceSegment)
        .map { segment ->
        val ledger = calculateHoursContributions(
            segment = segment,
            shifts = shiftSources,
            independentExtras = input.independentExtras,
            clock = clock,
            zoneId = zoneId,
            protectionPeriods = protections,
        )
        val progress = summarizeHoursContributions(segment, ledger)
        val helping = ledger.filter { contribution ->
            contribution.kind == HoursContributionKind.REGULAR_SHIFT ||
                requireNotNull(contribution.extraClass).helpsMeetHoursReference
        }
        buildCompliancePeriod(segment, progress, helping, metadata)
    }
    val availability = buildAvailability(input, actualsByShiftId, protections, clock)
    val optionalSections = buildOptionalSections(
        input = input,
        monthlyLedger = monthlyLedger,
        actualsByShiftId = actualsByShiftId,
        protections = protections,
        metadata = metadata,
        clock = clock,
        zoneId = zoneId,
    )
    val hasContent = listOfNotNull(
        essentials.totalWorked,
        essentials.regularWorked,
        essentials.extras,
        essentials.pendingScheduled,
        availability?.programmed,
    ).isNotEmpty() || optionalSections.isNotEmpty()
    return MonthlySummaryProjection(
        month = input.month,
        essentials = essentials,
        compliance = compliance,
        availability = availability,
        optionalSections = optionalSections,
        hasContent = hasContent,
    )
}

private fun buildCompliancePeriod(
    segment: HoursReferenceSegment,
    progress: HoursProgress,
    helping: List<HoursContribution>,
    metadata: WorkMetadataIndex,
): SummaryCompliancePeriod {
    val prefix = "compliance:${segment.startInclusive}:${segment.endExclusive}:${segment.ownerRevision.id}"
    val contributingWork = hoursMetric(
        id = "$prefix:contributing",
        label = "Horas que cumplen",
        ledger = helping,
        metadata = metadata,
        valueOf = HoursContribution::workedMinutes,
    )
    val target = progress.targetMinutes?.let { targetMinutes ->
        metric(
            id = "$prefix:target",
            label = "Meta del período",
            unit = SummaryValueUnit.MINUTES,
            rows = listOf(
                SummaryContribution(
                    id = "$prefix:target:${segment.ownerRevision.id}",
                    sourceId = segment.ownerRevision.id.toString(),
                    ownerLocalDate = segment.startInclusive,
                    start = null,
                    end = null,
                    value = targetMinutes,
                    unit = SummaryValueUnit.MINUTES,
                    kind = SummaryContributionKind.REFERENCE_TARGET,
                    sourceLabel = "Meta configurada para este período",
                    explanation = "Proviene de la referencia de horas vigente al comenzar el período completo.",
                ),
            ),
        )
    }
    val missing = progress.missingMinutes
        ?.takeIf { it > 0L }
        ?.let {
            val targetRows = requireNotNull(target).contributions
            val workOffsets = contributingWork.contributions.map { contribution ->
                contribution.copy(
                    id = "$prefix:missing:work:${contribution.id}",
                    value = -contribution.value,
                    kind = SummaryContributionKind.REFERENCE_WORK_OFFSET,
                    explanation = "Se resta porque esta franja ya cumplió parte de la meta.",
                )
            }
            metric(
                id = "$prefix:missing",
                label = "Faltante para la meta",
                unit = SummaryValueUnit.MINUTES,
                rows = targetRows.map { contribution ->
                    contribution.copy(id = "$prefix:missing:target:${contribution.id}")
                } + workOffsets,
            )
        }
    val excess = progress.excessMinutes
        ?.takeIf { it > 0L }
        ?.let {
            val workRows = contributingWork.contributions.map { contribution ->
                contribution.copy(
                    id = "$prefix:excess:work:${contribution.id}",
                    kind = SummaryContributionKind.REFERENCE_WORK_OFFSET,
                    explanation = "Se suma porque esta franja cuenta para el cumplimiento.",
                )
            }
            val targetOffset = requireNotNull(target).contributions.map { contribution ->
                contribution.copy(
                    id = "$prefix:excess:target:${contribution.id}",
                    value = -contribution.value,
                    kind = SummaryContributionKind.REFERENCE_TARGET,
                    explanation = "Se resta la meta configurada para explicar únicamente la superación.",
                )
            }
            metric(
                id = "$prefix:excess",
                label = "Superación de la meta",
                unit = SummaryValueUnit.MINUTES,
                rows = workRows + targetOffset,
            )
        }
    return SummaryCompliancePeriod(
        segment = segment,
        progress = progress,
        contributingWork = contributingWork,
        target = target,
        missing = missing,
        excess = excess,
    )
}

fun resolveSummaryComplianceSegments(
    history: WorkConfigurationHistory,
    month: YearMonth,
): List<HoursReferenceSegment> {
    val monthStart = month.atDay(1)
    val monthEnd = month.plusMonths(1).atDay(1)
    val result = linkedMapOf<String, HoursReferenceSegment>()
    var cursor = monthStart
    while (cursor < monthEnd) {
        val revision = history.timeline.revisionAt(cursor)
        if (revision == null) {
            cursor = history.timeline.revisions
                .firstOrNull { !it.effectiveFrom.isBefore(cursor) }
                ?.effectiveFrom
                ?.coerceAtMost(monthEnd)
                ?: monthEnd
            continue
        }
        val marker = revision.value.hoursReferenceStartedOn
        if (marker != null && cursor.isBefore(marker)) {
            val nextRevision = history.timeline.revisions
                .firstOrNull { it.effectiveFrom.isAfter(cursor) }
                ?.effectiveFrom
            cursor = listOfNotNull(marker, nextRevision, monthEnd)
                .filter { it.isAfter(cursor) }
                .minOrNull()
                ?: monthEnd
            continue
        }
        val segment = resolveHoursReferenceSegment(history, cursor)
        if (segment == null) {
            cursor = cursor.plusDays(1)
            continue
        }
        if (segment.startInclusive < monthEnd && segment.endExclusive > monthStart) {
            val key = "${segment.startInclusive}|${segment.endExclusive}|${segment.ownerRevision.id}"
            result[key] = segment
        }
        cursor = if (segment.endExclusive > cursor && segment.endExclusive < monthEnd) {
            segment.endExclusive
        } else {
            monthEnd
        }
    }
    return result.values.sortedWith(
        compareBy(HoursReferenceSegment::startInclusive, HoursReferenceSegment::endExclusive),
    )
}

private fun HoursReferenceSegment.isApplicableComplianceSegment(): Boolean = when (target) {
    is HoursTargetState.Defined,
    HoursTargetState.MissingPerPeriodValue,
    -> true
    HoursTargetState.Unknown -> naturalWindow != null
    HoursTargetState.PendingSetup,
    HoursTargetState.NotUsed,
    -> false
}

private fun buildEssentials(
    progress: HoursProgress,
    ledger: List<HoursContribution>,
    metadata: WorkMetadataIndex,
): MonthlySummaryEssentials {
    val hasWorkSource = ledger.isNotEmpty()
    val regular = ledger.filter { it.kind == HoursContributionKind.REGULAR_SHIFT }
    val extras = ledger.filter { it.kind != HoursContributionKind.REGULAR_SHIFT }
    return MonthlySummaryEssentials(
        totalWorked = if (hasWorkSource) {
            hoursMetric("essential:total", "Total trabajado", ledger, metadata, HoursContribution::workedMinutes)
        } else {
            null
        },
        regularWorked = if (hasWorkSource) {
            hoursMetric("essential:regular", "Trabajo habitual", regular, metadata, HoursContribution::workedMinutes)
        } else {
            null
        },
        extras = if (progress.extrasByClass.any { it.totalMinutes > 0L }) {
            hoursMetric("essential:extras", "Extras", extras, metadata, HoursContribution::workedMinutes)
        } else {
            null
        },
        pendingScheduled = if (progress.pendingScheduledMinutes > 0L) {
            hoursMetric(
                "essential:pending",
                "Pendiente programado",
                ledger,
                metadata,
                HoursContribution::pendingMinutes,
                pending = true,
            )
        } else {
            null
        },
    )
}

private fun hoursMetric(
    id: String,
    label: String,
    ledger: List<HoursContribution>,
    metadata: WorkMetadataIndex,
    valueOf: (HoursContribution) -> Long,
    pending: Boolean = false,
): SummaryMetric {
    val rows = ledger.mapNotNull { contribution ->
        val value = valueOf(contribution)
        if (value == 0L) return@mapNotNull null
        val work = metadata.forContribution(contribution)
        SummaryContribution(
            id = "$id:${contribution.kind}:${contribution.phase}:${contribution.contributionId}:" +
                "${contribution.start}:${contribution.end}",
            sourceId = contribution.sourceId.toString(),
            ownerLocalDate = contribution.ownerLocalDate,
            start = contribution.start,
            end = contribution.end,
            zoneId = work.zoneId,
            value = value,
            unit = SummaryValueUnit.MINUTES,
            kind = if (pending) {
                SummaryContributionKind.PENDING_WORK
            } else {
                contribution.kind.toSummaryKind()
            },
            sourceLabel = work.sourceLabel(contribution),
            workPlaceLabel = work.placeLabel,
            workTypeLabel = work.workTypeName,
            extraClassLabel = contribution.extraClass?.name,
            explanation = work.attributionExplanation,
        )
    }.sortedWith(SummaryContributionOrder)
    return metric(id, label, SummaryValueUnit.MINUTES, rows)
}

private fun buildAvailability(
    input: MonthlySummaryInput,
    actualsByShiftId: Map<UUID, ShiftActualAggregate>,
    protections: List<WorkProtectionPeriod>,
    clock: Clock,
): SummaryAvailability? {
    val windows = input.availabilityWindows
        .filter { YearMonth.from(it.ownerLocalDate) == input.month }
        .sortedWith(compareBy(AvailabilityWindowRecord::start, AvailabilityWindowRecord::end, AvailabilityWindowRecord::id))
    if (windows.isEmpty()) return null
    val activeWork = resolveAvailabilityActiveWorkIntervals(
        shifts = input.shifts,
        actualsByShiftId = actualsByShiftId,
        independentExtras = input.independentExtras,
        protectedOwnerDates = protections.map { it.startDateInclusive..it.endDateInclusive },
    )
    val rows = windows.map { window ->
        val protected = protections.any { window.ownerLocalDate in it }
        window to calculateAvailabilityIntervalBreakdown(window, activeWork, protected, clock)
    }
    fun availabilityMetric(
        id: String,
        label: String,
        kind: SummaryContributionKind,
        segmentsOf: (AvailabilityIntervalBreakdown) -> List<AvailabilityMinuteSegment>,
    ): SummaryMetric {
        val contributions = rows.flatMap { (window, result) ->
            segmentsOf(result).map { segment ->
                SummaryContribution(
                    id = "$id:${window.id}:${segment.start}:${segment.end}:" +
                        stableKey(segment.activeWorkKeys.joinToString("|")),
                    sourceId = window.id.toString(),
                    ownerLocalDate = window.ownerLocalDate,
                    start = segment.start,
                    end = segment.end,
                    zoneId = window.zoneId,
                    value = segment.durationMinutes,
                    unit = SummaryValueUnit.MINUTES,
                    kind = kind,
                    sourceLabel = window.labelSnapshot,
                    explanation = when {
                        protections.any { window.ownerLocalDate in it } ->
                            "Ventana protegida: sólo explica la disponibilidad programada."
                        segment.activeWorkKeys.isNotEmpty() ->
                            "Esta franja coincide con trabajo activo y reemplaza disponibilidad."
                        else ->
                            "La disponibilidad permanece separada del trabajo y del cumplimiento."
                    },
                )
            }
        }.sortedWith(SummaryContributionOrder)
        return metric(id, label, SummaryValueUnit.MINUTES, contributions)
    }
    val programmed = availabilityMetric(
        "availability:programmed",
        "Disponibilidad programada",
        SummaryContributionKind.AVAILABILITY_PROGRAMMED,
    ) { it.programmed }
    val effective = availabilityMetric(
        "availability:effective",
        "Disponibilidad efectiva transcurrida",
        SummaryContributionKind.AVAILABILITY_EFFECTIVE,
    ) { it.effectiveElapsed }
    val replaced = availabilityMetric(
        "availability:replaced",
        "Disponibilidad reemplazada por trabajo",
        SummaryContributionKind.AVAILABILITY_REPLACED,
    ) { it.replacedElapsed }.takeIf { it.value > 0L }
    val pending = availabilityMetric(
        "availability:pending",
        "Disponibilidad pendiente",
        SummaryContributionKind.AVAILABILITY_PENDING,
    ) { it.futurePending }.takeIf { it.value > 0L }
    val projected = availabilityMetric(
        "availability:projected",
        "Disponibilidad efectiva proyectada",
        SummaryContributionKind.AVAILABILITY_PROJECTED,
    ) { it.effectiveProjectedAtEnd }
    return SummaryAvailability(programmed, effective, replaced, pending, projected)
}

private fun buildOptionalSections(
    input: MonthlySummaryInput,
    monthlyLedger: List<HoursContribution>,
    actualsByShiftId: Map<UUID, ShiftActualAggregate>,
    protections: List<WorkProtectionPeriod>,
    metadata: WorkMetadataIndex,
    clock: Clock,
    zoneId: ZoneId,
): List<SummaryOptionalSection> {
    val sections = mutableListOf<SummaryOptionalSection>()
    val classifications = classifyWorkedIntervals(
        input = input,
        actualsByShiftId = actualsByShiftId,
        protections = protections,
        clock = clock,
        zoneId = zoneId,
    )
    listOf(
        SummaryOptionalFamily.NIGHTS to classifications.nights,
        SummaryOptionalFamily.HOLIDAYS to classifications.holidays,
        SummaryOptionalFamily.WEEKENDS to classifications.weekends,
    ).forEach { (family, rows) ->
        if (rows.isNotEmpty()) {
            sections += SummaryOptionalSection(
                family,
                listOf(metric("optional:${family.name.lowercase()}", family.defaultLabel(), SummaryValueUnit.MINUTES, rows)),
            )
        }
    }

    val actualComparison = buildPlannedActualMetrics(input, actualsByShiftId)
    if (actualComparison.isNotEmpty()) {
        sections += SummaryOptionalSection(SummaryOptionalFamily.PLANNED_VS_ACTUAL, actualComparison)
    }
    val workedLedger = monthlyLedger.filter { it.workedMinutes > 0L }
    val places = groupedHoursMetrics(
        familyId = "places",
        ledger = workedLedger,
        metadata = metadata,
        keyOf = { contribution, work -> "${work.workPlaceId}|${work.placeLabel}" },
        labelOf = { _, work -> work.placeLabel },
    )
    if (places.isNotEmpty()) sections += SummaryOptionalSection(SummaryOptionalFamily.WORK_PLACES, places)
    val types = groupedHoursMetrics(
        familyId = "types",
        ledger = workedLedger,
        metadata = metadata,
        keyOf = { _, work -> "${work.workTypeId}|${work.workTypeName}" },
        labelOf = { _, work -> work.workTypeName },
    )
    if (types.isNotEmpty()) sections += SummaryOptionalSection(SummaryOptionalFamily.WORK_TYPES, types)
    val classes = groupedExtraClassMetrics(monthlyLedger, metadata)
    if (classes.isNotEmpty()) sections += SummaryOptionalSection(SummaryOptionalFamily.EXTRA_CLASSES, classes)
    val situations = buildSituationMetrics(input)
    if (situations.isNotEmpty()) sections += SummaryOptionalSection(SummaryOptionalFamily.SITUATIONS, situations)
    return sections
}

private data class WorkMetadata(
    val sourceId: UUID,
    val workPlaceId: UUID,
    val placeLabel: String,
    val workTypeId: UUID,
    val workTypeName: String,
    val zoneId: ZoneId,
    val isIndependent: Boolean,
    val usesActualStart: Boolean,
) {
    val attributionExplanation: String
        get() = when {
            isIndependent -> "Pertenece al mes de la fecha local de inicio exacto del extra independiente."
            usesActualStart -> "Pertenece al mes del inicio real; la celda del Calendario conserva su fecha planificada."
            else -> "Pertenece al mes del inicio planificado porque no posee horario real."
        }

    fun sourceLabel(contribution: HoursContribution): String = when (contribution.kind) {
        HoursContributionKind.REGULAR_SHIFT -> if (usesActualStart) {
            "Jornada con horario real · habitual sin extras"
        } else {
            "Jornada planificada · habitual"
        }
        HoursContributionKind.SHIFT_EXTRA -> "Fragmento extra de jornada"
        HoursContributionKind.INDEPENDENT_EXTRA -> "Trabajo extra independiente"
    }
}

private class WorkMetadataIndex(
    shifts: List<V2ShiftWrite>,
    extras: List<IndependentExtraWorkRecord>,
) {
    private val shiftsById = shifts.associateBy { it.shift.id }
    private val extrasById = extras.associateBy { it.id }
    private val actualShiftIds = mutableSetOf<UUID>()

    init {
        require(shiftsById.size == shifts.size) { "No puede haber jornadas repetidas en el Resumen" }
        require(extrasById.size == extras.size) { "No puede haber extras independientes repetidos" }
    }

    fun markActuals(actuals: Iterable<ShiftActualAggregate>) {
        actualShiftIds += actuals.map { it.record.shiftId }
    }

    fun forContribution(contribution: HoursContribution): WorkMetadata {
        val extra = extrasById[contribution.sourceId]
        if (extra != null) {
            return WorkMetadata(
                sourceId = extra.id,
                workPlaceId = extra.workPlaceId,
                placeLabel = safePlaceLabel(extra.snapshot.workPlaceName, extra.snapshot.workPlaceAbbreviation),
                workTypeId = extra.workTypeId,
                workTypeName = extra.snapshot.workTypeName,
                zoneId = extra.zoneId,
                isIndependent = true,
                usesActualStart = false,
            )
        }
        val write = requireNotNull(shiftsById[contribution.sourceId]) {
            "La contribución de horas no conserva una fuente conocida"
        }
        return WorkMetadata(
            sourceId = write.shift.id,
            workPlaceId = write.snapshot.workPlaceId,
            placeLabel = safePlaceLabel(
                write.shift.objectiveNameSnapshot,
                write.shift.objectiveAbbreviationSnapshot,
            ),
            workTypeId = write.snapshot.workTypeId,
            workTypeName = write.snapshot.workTypeNameSnapshot,
            zoneId = write.shift.zoneId,
            isIndependent = false,
            usesActualStart = write.shift.id in actualShiftIds,
        )
    }
}

private fun groupedHoursMetrics(
    familyId: String,
    ledger: List<HoursContribution>,
    metadata: WorkMetadataIndex,
    keyOf: (HoursContribution, WorkMetadata) -> String,
    labelOf: (HoursContribution, WorkMetadata) -> String,
): List<SummaryMetric> = ledger
    .groupBy { contribution -> keyOf(contribution, metadata.forContribution(contribution)) }
    .entries
    .map { (key, contributions) ->
        val first = contributions.first()
        val work = metadata.forContribution(first)
        hoursMetric(
            id = "optional:$familyId:${stableKey(key)}",
            label = labelOf(first, work),
            ledger = contributions,
            metadata = metadata,
            valueOf = HoursContribution::workedMinutes,
        )
    }
    .filter { it.value > 0L }
    .sortedWith(compareBy<SummaryMetric> { it.label.lowercase() }.thenBy { it.id })

private fun groupedExtraClassMetrics(
    ledger: List<HoursContribution>,
    metadata: WorkMetadataIndex,
): List<SummaryMetric> = ledger
    .filter { contribution ->
        contribution.kind != HoursContributionKind.REGULAR_SHIFT &&
            requireNotNull(contribution.extraClass).showDedicatedSummary &&
            contribution.workedMinutes > 0L
    }
    .groupBy { requireNotNull(it.extraClass) }
    .entries
    .map { (key, contributions) ->
        hoursMetric(
            id = "optional:extra-classes:${key.id}:${stableKey(key.name)}:${key.helpsMeetHoursReference}",
            label = key.name,
            ledger = contributions,
            metadata = metadata,
            valueOf = HoursContribution::workedMinutes,
        )
    }
    .sortedWith(compareBy<SummaryMetric> { it.label.lowercase() }.thenBy { it.id })

private data class ClassificationRows(
    val nights: List<SummaryContribution>,
    val holidays: List<SummaryContribution>,
    val weekends: List<SummaryContribution>,
)

private data class WorkedInterval(
    val key: String,
    val sourceId: UUID,
    val ownerDate: LocalDate,
    val start: Instant,
    val end: Instant,
    val zoneId: ZoneId,
    val timelineId: UUID,
    val sectorName: String,
    val workPlaceId: UUID,
    val placeLabel: String,
    val workTypeName: String,
    val sourceLabel: String,
)

private fun classifyWorkedIntervals(
    input: MonthlySummaryInput,
    actualsByShiftId: Map<UUID, ShiftActualAggregate>,
    protections: List<WorkProtectionPeriod>,
    clock: Clock,
    zoneId: ZoneId,
): ClassificationRows {
    val normalizedNow = clock.withZone(zoneId).instant().truncatedTo(ChronoUnit.MINUTES)
    val intervals = buildList {
        input.shifts.forEach { write ->
            val shift = write.shift
            val actual = actualsByShiftId[shift.id]
            val ownerDate = actual?.record?.actualStart?.atZone(shift.zoneId)?.toLocalDate()
                ?: shift.startAt.atZone(shift.zoneId).toLocalDate()
            if (
                YearMonth.from(ownerDate) != input.month ||
                shift.status != ShiftStatus.PLANNED ||
                (actual == null && protections.any { ownerDate in it })
            ) return@forEach
            val start = actual?.record?.actualStart ?: shift.startAt
            val rawEnd = actual?.record?.actualEnd ?: shift.endAt
            val end = minOf(rawEnd, normalizedNow)
            if (start < end) {
                add(
                    WorkedInterval(
                        key = "shift:${shift.id}",
                        sourceId = shift.id,
                        ownerDate = ownerDate,
                        start = start,
                        end = end,
                        zoneId = shift.zoneId,
                        timelineId = write.snapshot.timelineId,
                        sectorName = write.snapshot.sector.name,
                        workPlaceId = write.snapshot.workPlaceId,
                        placeLabel = safePlaceLabel(
                            shift.objectiveNameSnapshot,
                            shift.objectiveAbbreviationSnapshot,
                        ),
                        workTypeName = write.snapshot.workTypeNameSnapshot,
                        sourceLabel = if (actual == null) "Jornada planificada" else "Jornada con horario real",
                    ),
                )
            }
        }
        input.independentExtras.forEach { extra ->
            if (YearMonth.from(extra.ownerLocalDate) != input.month) return@forEach
            val end = minOf(extra.end, normalizedNow)
            if (extra.start < end) {
                add(
                    WorkedInterval(
                        key = "extra:${extra.id}",
                        sourceId = extra.id,
                        ownerDate = extra.ownerLocalDate,
                        start = extra.start,
                        end = end,
                        zoneId = extra.zoneId,
                        timelineId = extra.timelineId,
                        sectorName = extra.sector.name,
                        workPlaceId = extra.workPlaceId,
                        placeLabel = safePlaceLabel(
                            extra.snapshot.workPlaceName,
                            extra.snapshot.workPlaceAbbreviation,
                        ),
                        workTypeName = extra.snapshot.workTypeName,
                        sourceLabel = "Trabajo extra independiente",
                    ),
                )
            }
        }
    }.sortedWith(compareBy(WorkedInterval::start, WorkedInterval::end, WorkedInterval::key))
    val holidaysByDate = input.holidays.associateBy { it.date }
    val nights = mutableListOf<SummaryContribution>()
    val holidays = mutableListOf<SummaryContribution>()
    val weekends = mutableListOf<SummaryContribution>()
    intervals.forEach { source ->
        val catalog = requireNotNull(input.catalogs.singleOrNull { catalog ->
            catalog.timelineId == source.timelineId && catalog.sector.name == source.sectorName
        }) { "No existe un catálogo único para clasificar ${source.key}" }
        civilPieces(source).forEach { piece ->
            val revision = requireNotNull(catalog.ruleRevisionAt(source.workPlaceId, piece.date)) {
                "No existen reglas históricas para ${source.placeLabel} el ${piece.date}"
            }
            val nightRule = revision.rules.nightHours
            if (nightRule is NightHoursRule.Defined && nightRule.showDedicatedSummary) {
                nightIntersections(piece, source.zoneId, nightRule).forEachIndexed { index, interval ->
                    nights += classifiedContribution(
                        id = "night:${source.key}:${piece.date}:$index:${interval.first}",
                        source = source,
                        pieceDate = piece.date,
                        start = interval.first,
                        end = interval.second,
                        kind = SummaryContributionKind.NIGHT,
                        explanation = "También puede coincidir con feriado o fin de semana; no se suma otra vez al total.",
                    )
                }
            }
            val holiday = holidaysByDate[piece.date]
            if (holiday != null && revision.rules.holiday.showDedicatedSummary) {
                holidays += classifiedContribution(
                    id = "holiday:${source.key}:${piece.date}",
                    source = source,
                    pieceDate = piece.date,
                    start = piece.start,
                    end = piece.end,
                    kind = SummaryContributionKind.HOLIDAY,
                    explanation = "Feriado ${holiday.name ?: "sin nombre"}; esta clasificación no agrega horas al total.",
                )
            }
            val weekendRule = revision.rules.weekend
            if (
                weekendRule is WeekendRule.Defined &&
                weekendRule.showDedicatedSummary &&
                weekendRule.days.includes(piece.date.dayOfWeek)
            ) {
                weekends += classifiedContribution(
                    id = "weekend:${source.key}:${piece.date}",
                    source = source,
                    pieceDate = piece.date,
                    start = piece.start,
                    end = piece.end,
                    kind = SummaryContributionKind.WEEKEND,
                    explanation = "Clasificación por la regla histórica del lugar; no agrega horas al total.",
                )
            }
        }
    }
    return ClassificationRows(
        nights.sortedWith(SummaryContributionOrder),
        holidays.sortedWith(SummaryContributionOrder),
        weekends.sortedWith(SummaryContributionOrder),
    )
}

private data class CivilPiece(val date: LocalDate, val start: Instant, val end: Instant)

private fun civilPieces(source: WorkedInterval): List<CivilPiece> = buildList {
    var cursor = source.start
    while (cursor < source.end) {
        val date = cursor.atZone(source.zoneId).toLocalDate()
        val nextDay = date.plusDays(1).atStartOfDay(source.zoneId).toInstant()
        val end = minOf(source.end, nextDay)
        require(cursor < end) { "No se pudo dividir un intervalo trabajado en días civiles" }
        add(CivilPiece(date, cursor, end))
        cursor = end
    }
}

private fun nightIntersections(
    piece: CivilPiece,
    zoneId: ZoneId,
    rule: NightHoursRule.Defined,
): List<Pair<Instant, Instant>> {
    val dayStart = piece.date.atStartOfDay(zoneId).toInstant()
    val nextDayStart = piece.date.plusDays(1).atStartOfDay(zoneId).toInstant()
    val windows = if (rule.startInclusive < rule.endExclusive) {
        listOf(localInstant(piece.date, rule.startInclusive, zoneId) to localInstant(piece.date, rule.endExclusive, zoneId))
    } else {
        listOf(
            dayStart to localInstant(piece.date, rule.endExclusive, zoneId),
            localInstant(piece.date, rule.startInclusive, zoneId) to nextDayStart,
        )
    }
    return windows.mapNotNull { (windowStart, windowEnd) ->
        val start = maxOf(piece.start, windowStart)
        val end = minOf(piece.end, windowEnd)
        if (start < end) start to end else null
    }
}

private fun localInstant(date: LocalDate, time: LocalTime, zoneId: ZoneId): Instant =
    date.atTime(time).atZone(zoneId).toInstant()

private fun classifiedContribution(
    id: String,
    source: WorkedInterval,
    pieceDate: LocalDate,
    start: Instant,
    end: Instant,
    kind: SummaryContributionKind,
    explanation: String,
): SummaryContribution = SummaryContribution(
    id = id,
    sourceId = source.sourceId.toString(),
    ownerLocalDate = source.ownerDate,
    start = start,
    end = end,
    zoneId = source.zoneId,
    value = ChronoUnit.MINUTES.between(start, end),
    unit = SummaryValueUnit.MINUTES,
    kind = kind,
    sourceLabel = "${source.sourceLabel} · $pieceDate",
    workPlaceLabel = source.placeLabel,
    workTypeLabel = source.workTypeName,
    explanation = explanation,
)

private fun buildPlannedActualMetrics(
    input: MonthlySummaryInput,
    actualsByShiftId: Map<UUID, ShiftActualAggregate>,
): List<SummaryMetric> {
    val writes = input.shifts.filter { write ->
        val actual = actualsByShiftId[write.shift.id]
        actual != null &&
            write.shift.status == ShiftStatus.PLANNED &&
            YearMonth.from(actual.record.actualStart.atZone(write.shift.zoneId).toLocalDate()) == input.month
    }.sortedWith(compareBy({ actualsByShiftId.getValue(it.shift.id).record.actualStart }, { it.shift.id }))
    if (writes.isEmpty()) return emptyList()
    fun rows(
        kind: SummaryContributionKind,
        sourceLabel: String,
        intervalOf: (V2ShiftWrite, ShiftActualAggregate) -> Pair<Instant, Instant>?,
        valueOf: (V2ShiftWrite, ShiftActualAggregate) -> Long,
    ) =
        writes.map { write ->
            val actual = actualsByShiftId.getValue(write.shift.id)
            val interval = intervalOf(write, actual)
            SummaryContribution(
                id = "planned-actual:${kind.name}:${write.shift.id}",
                sourceId = write.shift.id.toString(),
                ownerLocalDate = actual.record.actualStart.atZone(write.shift.zoneId).toLocalDate(),
                start = interval?.first,
                end = interval?.second,
                zoneId = interval?.let { write.shift.zoneId },
                value = valueOf(write, actual),
                unit = SummaryValueUnit.MINUTES,
                kind = kind,
                sourceLabel = sourceLabel,
                workPlaceLabel = safePlaceLabel(
                    write.shift.objectiveNameSnapshot,
                    write.shift.objectiveAbbreviationSnapshot,
                ),
                workTypeLabel = write.snapshot.workTypeNameSnapshot,
                explanation = "Compara duraciones sin interpretar dinero, deuda, premio, sanción ni saldo legal.",
            )
        }.sortedWith(SummaryContributionOrder)
    val planned = rows(
        kind = SummaryContributionKind.PLANNED_DURATION,
        sourceLabel = "Horario planificado de jornada corregida",
        intervalOf = { write, _ -> write.shift.startAt to write.shift.endAt },
        valueOf = { write, _ -> ChronoUnit.MINUTES.between(write.shift.startAt, write.shift.endAt) },
    )
    val actual = rows(
        kind = SummaryContributionKind.ACTUAL_DURATION,
        sourceLabel = "Horario real confirmado",
        intervalOf = { _, value -> value.record.actualStart to value.record.actualEnd },
        valueOf = { _, value -> value.record.durationMinutes },
    )
    val difference = writes.flatMap { write ->
        val value = actualsByShiftId.getValue(write.shift.id)
        val placeLabel = safePlaceLabel(
            write.shift.objectiveNameSnapshot,
            write.shift.objectiveAbbreviationSnapshot,
        )
        val explanation = "La diferencia suma el horario real y resta el horario planificado de la misma jornada."
        listOf(
            SummaryContribution(
                id = "planned-actual:difference:actual:${write.shift.id}",
                sourceId = "${write.shift.id}:actual",
                ownerLocalDate = value.record.actualStart.atZone(write.shift.zoneId).toLocalDate(),
                start = value.record.actualStart,
                end = value.record.actualEnd,
                zoneId = write.shift.zoneId,
                value = value.record.durationMinutes,
                unit = SummaryValueUnit.MINUTES,
                kind = SummaryContributionKind.ACTUAL_DIFFERENCE,
                sourceLabel = "Horario real sumado",
                workPlaceLabel = placeLabel,
                workTypeLabel = write.snapshot.workTypeNameSnapshot,
                explanation = explanation,
            ),
            SummaryContribution(
                id = "planned-actual:difference:planned:${write.shift.id}",
                sourceId = "${write.shift.id}:planned",
                ownerLocalDate = write.shift.startAt.atZone(write.shift.zoneId).toLocalDate(),
                start = write.shift.startAt,
                end = write.shift.endAt,
                zoneId = write.shift.zoneId,
                value = Math.negateExact(ChronoUnit.MINUTES.between(write.shift.startAt, write.shift.endAt)),
                unit = SummaryValueUnit.MINUTES,
                kind = SummaryContributionKind.ACTUAL_DIFFERENCE,
                sourceLabel = "Horario planificado restado",
                workPlaceLabel = placeLabel,
                workTypeLabel = write.snapshot.workTypeNameSnapshot,
                explanation = explanation,
            ),
        )
    }.sortedWith(SummaryContributionOrder)
    return listOf(
        metric("optional:planned-vs-actual:planned", "Duración planificada", SummaryValueUnit.MINUTES, planned),
        metric("optional:planned-vs-actual:actual", "Duración real confirmada", SummaryValueUnit.MINUTES, actual),
        metric("optional:planned-vs-actual:difference", "Diferencia real − planificada", SummaryValueUnit.MINUTES, difference),
    )
}

private fun buildSituationMetrics(input: MonthlySummaryInput): List<SummaryMetric> {
    val monthStart = input.month.atDay(1)
    val monthEndInclusive = input.month.atEndOfMonth()
    val result = mutableListOf<SummaryMetric>()
    fun shiftSituation(
        status: ShiftStatus,
        label: String,
        singularLabel: String,
        kind: SummaryContributionKind,
    ) {
        val rows = input.shifts
            .filter { it.shift.status == status && it.shift.localStartDate in monthStart..monthEndInclusive }
            .map { write ->
                SummaryContribution(
                    id = "situation:${kind.name}:${write.shift.id}",
                    sourceId = write.shift.id.toString(),
                    ownerLocalDate = write.shift.localStartDate,
                    start = write.shift.startAt,
                    end = write.shift.endAt,
                    zoneId = write.shift.zoneId,
                    value = 1L,
                    unit = SummaryValueUnit.COUNT,
                    kind = kind,
                    sourceLabel = singularLabel,
                    workPlaceLabel = safePlaceLabel(
                        write.shift.objectiveNameSnapshot,
                        write.shift.objectiveAbbreviationSnapshot,
                    ),
                    workTypeLabel = write.snapshot.workTypeNameSnapshot,
                )
            }.sortedWith(SummaryContributionOrder)
        if (rows.isNotEmpty()) result += metric("optional:situations:${kind.name.lowercase()}", label, SummaryValueUnit.COUNT, rows)
    }
    shiftSituation(ShiftStatus.ABSENT, "Ausencias", "Ausencia", SummaryContributionKind.ABSENCE)
    shiftSituation(ShiftStatus.CANCELLED, "Cancelaciones", "Cancelación", SummaryContributionKind.CANCELLATION)

    fun dateRows(
        id: String,
        label: String,
        kind: SummaryContributionKind,
        datesBySource: List<Pair<String, ClosedRange<LocalDate>>>,
    ) {
        val chosen = linkedMapOf<LocalDate, String>()
        datesBySource.sortedBy { it.first }.forEach { (sourceId, range) ->
            val start = maxOf(range.start, monthStart)
            val end = minOf(range.endInclusive, monthEndInclusive)
            if (!end.isBefore(start)) {
                var date = start
                while (!date.isAfter(end)) {
                    chosen.putIfAbsent(date, sourceId)
                    date = date.plusDays(1)
                }
            }
        }
        val rows = chosen.map { (date, sourceId) ->
            SummaryContribution(
                id = "$id:$date",
                sourceId = sourceId,
                ownerLocalDate = date,
                start = null,
                end = null,
                value = 1L,
                unit = SummaryValueUnit.COUNT,
                kind = kind,
                sourceLabel = label,
            )
        }.sortedWith(SummaryContributionOrder)
        if (rows.isNotEmpty()) result += metric(id, label, SummaryValueUnit.COUNT, rows)
    }
    dateRows(
        "optional:situations:medical",
        "Días de carpeta médica",
        SummaryContributionKind.MEDICAL_LEAVE,
        input.medicalLeaves.map { it.id.toString() to (it.startDate..it.endDateInclusive) },
    )
    dateRows(
        "optional:situations:vacation",
        "Días de vacaciones",
        SummaryContributionKind.VACATION,
        input.vacations.map { it.id.toString() to (it.startDate..it.endDateInclusive) },
    )
    val dayOffRows = input.explicitDayStatuses
        .filter { it.type == ExplicitDayStatusType.DAY_OFF && it.date in monthStart..monthEndInclusive }
        .distinctBy { it.date }
        .map { status ->
            SummaryContribution(
                id = "optional:situations:day-off:${status.date}",
                sourceId = "day-off:${status.date}",
                ownerLocalDate = status.date,
                start = null,
                end = null,
                value = 1L,
                unit = SummaryValueUnit.COUNT,
                kind = SummaryContributionKind.DAY_OFF,
                sourceLabel = "Franco F explícito",
            )
        }.sortedWith(SummaryContributionOrder)
    if (dayOffRows.isNotEmpty()) {
        result += metric(
            "optional:situations:day-off",
            "Francos F explícitos",
            SummaryValueUnit.COUNT,
            dayOffRows,
        )
    }
    return result
}

private fun metric(
    id: String,
    label: String,
    unit: SummaryValueUnit,
    rows: List<SummaryContribution>,
): SummaryMetric = SummaryMetric(
    id = id,
    label = label,
    value = rows.fold(0L) { total, row -> Math.addExact(total, row.value) },
    unit = unit,
    contributions = rows.sortedWith(SummaryContributionOrder),
)

private fun HoursContributionKind.toSummaryKind(): SummaryContributionKind = when (this) {
    HoursContributionKind.REGULAR_SHIFT -> SummaryContributionKind.REGULAR_WORK
    HoursContributionKind.SHIFT_EXTRA -> SummaryContributionKind.SHIFT_EXTRA
    HoursContributionKind.INDEPENDENT_EXTRA -> SummaryContributionKind.INDEPENDENT_EXTRA
}

private fun SummaryOptionalFamily.defaultLabel(): String = when (this) {
    SummaryOptionalFamily.NIGHTS -> "Noches"
    SummaryOptionalFamily.HOLIDAYS -> "Feriados"
    SummaryOptionalFamily.WEEKENDS -> "Fines de semana"
    SummaryOptionalFamily.PLANNED_VS_ACTUAL -> "Planificado frente a real"
    SummaryOptionalFamily.WORK_PLACES -> "Lugares de trabajo"
    SummaryOptionalFamily.WORK_TYPES -> "Tipos de trabajo"
    SummaryOptionalFamily.EXTRA_CLASSES -> "Clases extra"
    SummaryOptionalFamily.SITUATIONS -> "Situaciones especiales e intercambios"
}

private fun safePlaceLabel(name: String, abbreviation: String): String =
    if (name == abbreviation) name else "$name ($abbreviation)"

private fun stableKey(value: String): String {
    val digits = "0123456789abcdef"
    return buildString(value.length * 2) {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            append(digits[unsigned ushr 4])
            append(digits[unsigned and 0x0f])
        }
    }
}

private val SummaryContributionOrder = compareBy<SummaryContribution>(
    { it.start ?: it.ownerLocalDate.atStartOfDay(ZoneId.of("UTC")).toInstant() },
    { it.end ?: it.ownerLocalDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant() },
    { it.kind },
    { it.id },
)
