package com.blackatsystems.miguardia.core.domain.report

import com.blackatsystems.miguardia.core.domain.model.AvailabilityActiveWorkInterval
import com.blackatsystems.miguardia.core.domain.model.AvailabilityTemporalState
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.calculateAvailabilityIntervalBreakdown
import com.blackatsystems.miguardia.core.domain.model.resolveAvailabilityActiveWorkIntervals
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryInput
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryProjection
import com.blackatsystems.miguardia.core.domain.summary.SummaryContribution
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import com.blackatsystems.miguardia.core.domain.summary.calculateMonthlySummary
import com.blackatsystems.miguardia.core.domain.summary.resolveSummaryComplianceSegments
import com.blackatsystems.miguardia.core.domain.work.HoursReferenceSegment
import com.blackatsystems.miguardia.core.domain.work.HoursTargetState
import com.blackatsystems.miguardia.core.domain.work.WorkProtectionPeriod
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.Locale
import java.util.UUID

enum class ReportFormat(
    val extension: String,
    val mimeType: String,
) {
    PDF("pdf", "application/pdf"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

data class ReportPrivacySelection(
    val includeDisplayName: Boolean = false,
    val includePosition: Boolean = false,
    val includeShiftNotes: Boolean = false,
    val includeMedicalNotes: Boolean = false,
    val selectedPhotoIds: Set<UUID> = emptySet(),
) {
    init {
        require(selectedPhotoIds.size <= MAX_REPORT_PHOTOS) {
            "Un informe puede incluir como máximo $MAX_REPORT_PHOTOS fotos"
        }
    }

    companion object {
        const val MAX_REPORT_PHOTOS: Int = 12
    }
}

data class MonthlyReportSnapshotRequest(
    val month: YearMonth,
    val includeShiftNotes: Boolean,
    val includeMedicalNotes: Boolean,
    val selectedPhotoIds: Set<UUID>,
) {
    init {
        require(selectedPhotoIds.size <= ReportPrivacySelection.MAX_REPORT_PHOTOS)
    }
}

data class ReportCaptureRange(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
) {
    init {
        require(startInclusive < endExclusive)
    }

    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(startInclusive) && date.isBefore(endExclusive)
}

/**
 * Captura de fuentes de dominio obtenida dentro de una sola transacción Room.
 * Los escritores nunca reciben este tipo: sólo reciben [MonthlyWorkReportProjection].
 */
data class MonthlyReportSourceSnapshot(
    val request: MonthlyReportSnapshotRequest,
    val captureRange: ReportCaptureRange,
    val summaryInput: MonthlySummaryInput,
    val shiftNotes: List<ShiftNote>,
    val selectedPhotos: List<SchedulePhoto>,
) {
    init {
        require(summaryInput.month == request.month)
        val actualsByShiftId = summaryInput.actuals.associateBy { it.record.shiftId }
        val reportMonthShiftIds = summaryInput.shifts
            .filter { write ->
                val ownerDate = actualsByShiftId[write.shift.id]
                    ?.record
                    ?.actualStart
                    ?.atZone(write.shift.zoneId)
                    ?.toLocalDate()
                    ?: write.shift.localStartDate
                YearMonth.from(ownerDate) == request.month
            }
            .mapTo(hashSetOf()) { it.shift.id }
        require(!request.includeShiftNotes || shiftNotes.all { note ->
            note.shiftId in reportMonthShiftIds
        })
        require(request.includeShiftNotes || shiftNotes.isEmpty())
        val monthStart = request.month.atDay(1)
        val monthEnd = request.month.plusMonths(1).atDay(1)
        require(summaryInput.medicalLeaves.all { leave ->
            val intersectsReportMonth = leave.startDate < monthEnd &&
                !leave.endDateInclusive.isBefore(monthStart)
            (request.includeMedicalNotes && intersectsReportMonth) || leave.privateNote == null
        })
        require(request.selectedPhotoIds.isEmpty() == selectedPhotos.isEmpty())
        require(selectedPhotos.map { it.id }.toSet() == request.selectedPhotoIds)
        require(selectedPhotos.all { it.month == request.month })
    }
}

fun interface MonthlyReportSnapshotRepository {
    suspend fun capture(request: MonthlyReportSnapshotRequest): MonthlyReportSourceSnapshot
}

sealed interface ReportMonthState {
    data class PartialAsOf(val date: LocalDate) : ReportMonthState
    data object ClosedMonth : ReportMonthState
}

class FutureReportMonthException(month: YearMonth) :
    IllegalArgumentException("No se puede generar un informe para el mes futuro $month.")

enum class ReportReferenceState {
    PENDING_SETUP,
    NOT_USED,
    UNKNOWN,
    MISSING_VALUE_FOR_PERIOD,
    DEFINED,
}

data class ReportReferencePeriod(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
    val state: ReportReferenceState,
    val targetMinutes: Long?,
    val contributingMinutes: Long?,
    val missingMinutes: Long?,
    val excessMinutes: Long?,
) {
    init {
        require(startInclusive < endExclusive)
        when (state) {
            ReportReferenceState.DEFINED -> {
                require(targetMinutes != null && targetMinutes > 0L)
                require(contributingMinutes != null && missingMinutes != null && excessMinutes != null)
            }
            else -> {
                require(targetMinutes == null)
                require(missingMinutes == null && excessMinutes == null)
            }
        }
        require(listOfNotNull(contributingMinutes, missingMinutes, excessMinutes).all { it >= 0L })
    }
}

enum class ReportWorkKind {
    SHIFT,
    INDEPENDENT_EXTRA,
}

enum class ReportWorkState {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    ABSENT,
    CANCELLED,
}

data class ReportExtraBreakdown(
    val className: String,
    val minutes: Long,
) {
    init {
        require(className.isNotBlank())
        require(minutes > 0L)
    }
}

/** Una fila segura: no conserva identidad técnica ni dirección. */
data class ReportWorkRow(
    val stableOrder: Int,
    val ownerLocalDate: LocalDate,
    val kind: ReportWorkKind,
    val state: ReportWorkState,
    val sector: WorkSector,
    val workPlace: String,
    val workType: String,
    val plannedStart: Instant?,
    val plannedEnd: Instant?,
    val actualStart: Instant?,
    val actualEnd: Instant?,
    val zoneId: ZoneId,
    val accountedMinutes: Long,
    val regularMinutes: Long,
    val extraBreakdown: List<ReportExtraBreakdown>,
    val pendingMinutes: Long,
    val nightMinutes: Long,
    val holidayMinutes: Long,
    val weekendMinutes: Long,
    val position: String?,
    val notes: List<String>,
) {
    init {
        require(stableOrder >= 0)
        require(workPlace.isNotBlank() && workType.isNotBlank())
        require((plannedStart == null) == (plannedEnd == null))
        require((actualStart == null) == (actualEnd == null))
        if (plannedStart != null && plannedEnd != null) require(plannedStart < plannedEnd)
        if (actualStart != null && actualEnd != null) require(actualStart < actualEnd)
        require(
            listOf(
                accountedMinutes,
                regularMinutes,
                pendingMinutes,
                nightMinutes,
                holidayMinutes,
                weekendMinutes,
            ).all { it >= 0L },
        )
        require(extraBreakdown.sumOf { it.minutes } + regularMinutes == accountedMinutes)
        require(notes.none(String::isBlank))
    }

    val displayedStart: Instant
        get() = actualStart ?: requireNotNull(plannedStart)

    val displayedEnd: Instant
        get() = actualEnd ?: requireNotNull(plannedEnd)
}

data class ReportAvailabilityRow(
    val stableOrder: Int,
    val ownerLocalDate: LocalDate,
    val sector: WorkSector,
    val label: String,
    val start: Instant,
    val end: Instant,
    val zoneId: ZoneId,
    val state: AvailabilityTemporalState,
    val programmedMinutes: Long,
    val effectiveMinutes: Long,
    val replacedMinutes: Long,
    val pendingMinutes: Long,
    val projectedMinutes: Long,
    val futureOccupiedMinutes: Long,
) {
    init {
        require(stableOrder >= 0 && start < end)
        require(label.isNotBlank())
        require(
            listOf(
                programmedMinutes,
                effectiveMinutes,
                replacedMinutes,
                pendingMinutes,
                projectedMinutes,
                futureOccupiedMinutes,
            ).all { it >= 0L },
        )
    }
}

enum class ReportSituationKind {
    DAY_OFF,
    UNDEFINED,
    VACATION,
    MEDICAL_LEAVE,
    ABSENCE,
    CANCELLATION,
}

data class ReportSituationRow(
    val stableOrder: Int,
    val date: LocalDate,
    val kind: ReportSituationKind,
    val label: String,
) {
    init {
        require(stableOrder >= 0 && label.isNotBlank())
    }
}

enum class ReportNoteKind {
    SHIFT,
    MEDICAL_LEAVE,
}

data class ReportNoteRow(
    val stableOrder: Int,
    val date: LocalDate,
    val kind: ReportNoteKind,
    val context: String,
    val body: String,
) {
    init {
        require(stableOrder >= 0 && context.isNotBlank() && body.isNotBlank())
    }
}

data class ReportPhotoRow(
    val stableOrder: Int,
    val caption: String,
) {
    init {
        require(stableOrder >= 0 && caption.isNotBlank())
    }
}

data class ReportPrivateInclusions(
    val displayName: String?,
    val positionsIncluded: Boolean,
    val shiftNotesIncluded: Boolean,
    val medicalNotesIncluded: Boolean,
    val photosIncluded: Boolean,
)

data class MonthlyWorkReportProjection(
    val month: YearMonth,
    val generatedAt: Instant,
    val zoneId: ZoneId,
    val monthState: ReportMonthState,
    val sectors: List<WorkSector>,
    val summary: MonthlySummaryProjection,
    val references: List<ReportReferencePeriod>,
    val workRows: List<ReportWorkRow>,
    val availabilityRows: List<ReportAvailabilityRow>,
    val situations: List<ReportSituationRow>,
    val notes: List<ReportNoteRow>,
    val photos: List<ReportPhotoRow>,
    val privateInclusions: ReportPrivateInclusions,
) {
    init {
        require(summary.month == month)
        require(sectors == sectors.distinct())
        require(sectors.containsAll(workRows.map { it.sector }))
        require(sectors.containsAll(availabilityRows.map { it.sector }))
        require(references == references.sortedBy { it.startInclusive })
        require(workRows.map { it.stableOrder } == workRows.indices.toList())
        require(availabilityRows.map { it.stableOrder } == availabilityRows.indices.toList())
        require(situations.map { it.stableOrder } == situations.indices.toList())
        require(notes.map { it.stableOrder } == notes.indices.toList())
        require(photos.map { it.stableOrder } == photos.indices.toList())
        require(privateInclusions.positionsIncluded || workRows.none { it.position != null })
        require(privateInclusions.shiftNotesIncluded || workRows.none { it.notes.isNotEmpty() })
        require(privateInclusions.medicalNotesIncluded || notes.none { it.kind == ReportNoteKind.MEDICAL_LEAVE })
        require(privateInclusions.photosIncluded == photos.isNotEmpty())
        val reportedWorked = workRows.sumOf { it.accountedMinutes }
        val canonicalWorked = summary.essentials.totalWorked?.value ?: 0L
        require(reportedWorked == canonicalWorked) {
            "Las filas del informe suman $reportedWorked minutos y Resumen informa $canonicalWorked"
        }
        val reportedRegular = workRows.sumOf { it.regularMinutes }
        val canonicalRegular = summary.essentials.regularWorked?.value ?: 0L
        require(reportedRegular == canonicalRegular)
        val reportedExtras = workRows.sumOf { row -> row.extraBreakdown.sumOf { it.minutes } }
        val canonicalExtras = summary.essentials.extras?.value ?: 0L
        require(reportedExtras == canonicalExtras)
        val reportedPending = workRows.sumOf { it.pendingMinutes }
        val canonicalPending = summary.essentials.pendingScheduled?.value ?: 0L
        require(reportedPending == canonicalPending)
    }

    val hasActivity: Boolean
        get() = summary.hasContent || situations.isNotEmpty()

    val statusText: String
        get() = when (val state = monthState) {
            is ReportMonthState.PartialAsOf ->
                "Informe parcial al ${state.date.format(PartialDateFormatter)}"
            ReportMonthState.ClosedMonth -> "Informe mensual cerrado"
        }
}

data class MonthlyReportBuildOptions(
    val privacy: ReportPrivacySelection,
    val displayName: String?,
)

fun buildMonthlyWorkReport(
    snapshot: MonthlyReportSourceSnapshot,
    options: MonthlyReportBuildOptions,
    clock: Clock,
    zoneId: ZoneId,
): MonthlyWorkReportProjection {
    require(snapshot.request.includeShiftNotes == options.privacy.includeShiftNotes)
    require(snapshot.request.includeMedicalNotes == options.privacy.includeMedicalNotes)
    require(snapshot.request.selectedPhotoIds == options.privacy.selectedPhotoIds)

    val generatedAt = clock.instant()
    val frozenClock = Clock.fixed(generatedAt, zoneId)
    val monthState = resolveReportMonthState(snapshot.request.month, frozenClock, zoneId)
    val summary = calculateMonthlySummary(snapshot.summaryInput, frozenClock, zoneId)
    val contributionIndex = SummaryContributionIndex(summary)
    val actualsByShiftId = snapshot.summaryInput.actuals.associateBy { it.record.shiftId }
    val notesByShiftId = snapshot.shiftNotes.groupBy(ShiftNote::shiftId)
    val workRows = buildWorkRows(
        input = snapshot.summaryInput,
        actualsByShiftId = actualsByShiftId,
        notesByShiftId = notesByShiftId,
        contributionIndex = contributionIndex,
        privacy = options.privacy,
        generatedAt = generatedAt,
    )
    val availabilityRows = buildAvailabilityRows(
        input = snapshot.summaryInput,
        actualsByShiftId = actualsByShiftId,
        clock = frozenClock,
    )
    reconcileAvailability(summary, availabilityRows)
    val situations = buildSituations(snapshot.summaryInput)
    val notes = buildNotes(snapshot, workRows, options.privacy)
    val photos = snapshot.selectedPhotos
        .sortedWith(compareBy(SchedulePhoto::createdAt, SchedulePhoto::id))
        .mapIndexed { index, photo ->
            ReportPhotoRow(
                stableOrder = index,
                caption = photo.objectiveNameSnapshot?.takeIf(String::isNotBlank) ?: "Foto mensual",
            )
        }
    val sectors = sectorsPresent(snapshot.summaryInput, workRows, availabilityRows)
    return MonthlyWorkReportProjection(
        month = snapshot.request.month,
        generatedAt = generatedAt,
        zoneId = zoneId,
        monthState = monthState,
        sectors = sectors,
        summary = summary,
        references = buildReferenceRows(snapshot.summaryInput, summary),
        workRows = workRows,
        availabilityRows = availabilityRows,
        situations = situations,
        notes = notes,
        photos = photos,
        privateInclusions = ReportPrivateInclusions(
            displayName = options.displayName
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.takeIf { options.privacy.includeDisplayName },
            positionsIncluded = options.privacy.includePosition,
            shiftNotesIncluded = options.privacy.includeShiftNotes,
            medicalNotesIncluded = options.privacy.includeMedicalNotes,
            photosIncluded = photos.isNotEmpty(),
        ),
    )
}

fun resolveReportMonthState(
    month: YearMonth,
    clock: Clock,
    zoneId: ZoneId,
): ReportMonthState {
    val today = clock.instant().atZone(zoneId).toLocalDate()
    val currentMonth = YearMonth.from(today)
    return when {
        month > currentMonth -> throw FutureReportMonthException(month)
        month == currentMonth -> ReportMonthState.PartialAsOf(today)
        else -> ReportMonthState.ClosedMonth
    }
}

fun suggestedReportFileName(
    projection: MonthlyWorkReportProjection,
    format: ReportFormat,
): String {
    val suffix = when (projection.monthState) {
        is ReportMonthState.PartialAsOf -> "informe_parcial"
        ReportMonthState.ClosedMonth -> "informe_mensual"
    }
    return "MiGuardia_${projection.month}_$suffix.${format.extension}"
}

fun resolveReportCaptureRange(
    inputMonth: YearMonth,
    configuration: com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory,
): ReportCaptureRange {
    val segments = resolveSummaryComplianceSegments(configuration, inputMonth)
    val monthStart = inputMonth.atDay(1)
    val monthEnd = inputMonth.plusMonths(1).atDay(1)
    val completeSegments = segments.filter { it.naturalWindow != null }
    return ReportCaptureRange(
        startInclusive = minOf(monthStart, completeSegments.minOfOrNull { it.startInclusive } ?: monthStart),
        endExclusive = maxOf(monthEnd, completeSegments.maxOfOrNull { it.endExclusive } ?: monthEnd),
    )
}

private class SummaryContributionIndex(summary: MonthlySummaryProjection) {
    val total = summary.essentials.totalWorked.bySource()
    val regular = summary.essentials.regularWorked.bySource()
    val extras = summary.essentials.extras.bySource()
    val pending = summary.essentials.pendingScheduled.bySource()
    val nights = summary.optional(SummaryOptionalFamily.NIGHTS)
    val holidays = summary.optional(SummaryOptionalFamily.HOLIDAYS)
    val weekends = summary.optional(SummaryOptionalFamily.WEEKENDS)

    private fun com.blackatsystems.miguardia.core.domain.summary.SummaryMetric?.bySource():
        Map<String, List<SummaryContribution>> = this?.contributions.orEmpty().groupBy { it.sourceId }

    private fun MonthlySummaryProjection.optional(
        family: SummaryOptionalFamily,
    ): Map<String, List<SummaryContribution>> = optionalSections
        .firstOrNull { it.family == family }
        ?.metrics
        .orEmpty()
        .flatMap { it.contributions }
        .groupBy { it.sourceId }
}

private data class UnsafeWorkRow(
    val sourceId: UUID,
    val ownerLocalDate: LocalDate,
    val kind: ReportWorkKind,
    val state: ReportWorkState,
    val sector: WorkSector,
    val workPlace: String,
    val workType: String,
    val plannedStart: Instant?,
    val plannedEnd: Instant?,
    val actualStart: Instant?,
    val actualEnd: Instant?,
    val zoneId: ZoneId,
    val accountedMinutes: Long,
    val regularMinutes: Long,
    val extraBreakdown: List<ReportExtraBreakdown>,
    val pendingMinutes: Long,
    val nightMinutes: Long,
    val holidayMinutes: Long,
    val weekendMinutes: Long,
    val position: String?,
    val notes: List<String>,
)

private fun buildWorkRows(
    input: MonthlySummaryInput,
    actualsByShiftId: Map<UUID, ShiftActualAggregate>,
    notesByShiftId: Map<UUID, List<ShiftNote>>,
    contributionIndex: SummaryContributionIndex,
    privacy: ReportPrivacySelection,
    generatedAt: Instant,
): List<ReportWorkRow> {
    val rows = buildList {
        input.shifts.forEach { write ->
            val actual = actualsByShiftId[write.shift.id]
            val ownerDate = actual?.record?.actualStart
                ?.atZone(write.shift.zoneId)
                ?.toLocalDate()
                ?: write.shift.localStartDate
            if (YearMonth.from(ownerDate) != input.month) return@forEach
            val sourceKey = write.shift.id.toString()
            val accounted = contributionIndex.total[sourceKey].sumValues()
            val regular = contributionIndex.regular[sourceKey].sumValues()
            val extras = contributionIndex.extras[sourceKey].toExtraBreakdown()
            val pending = contributionIndex.pending[sourceKey].sumValues()
            val displayStart = actual?.record?.actualStart ?: write.shift.startAt
            val displayEnd = actual?.record?.actualEnd ?: write.shift.endAt
            add(
                UnsafeWorkRow(
                    sourceId = write.shift.id,
                    ownerLocalDate = ownerDate,
                    kind = ReportWorkKind.SHIFT,
                    state = write.reportState(displayStart, displayEnd, generatedAt),
                    sector = write.snapshot.sector,
                    workPlace = safePlaceLabel(
                        write.shift.objectiveNameSnapshot,
                        write.shift.objectiveAbbreviationSnapshot,
                    ),
                    workType = write.snapshot.workTypeNameSnapshot,
                    plannedStart = write.shift.startAt,
                    plannedEnd = write.shift.endAt,
                    actualStart = actual?.record?.actualStart,
                    actualEnd = actual?.record?.actualEnd,
                    zoneId = write.shift.zoneId,
                    accountedMinutes = accounted,
                    regularMinutes = regular,
                    extraBreakdown = extras,
                    pendingMinutes = pending,
                    nightMinutes = contributionIndex.nights[sourceKey].sumValues(),
                    holidayMinutes = contributionIndex.holidays[sourceKey].sumValues(),
                    weekendMinutes = contributionIndex.weekends[sourceKey].sumValues(),
                    position = write.shift.position.takeIf { privacy.includePosition },
                    notes = notesByShiftId[write.shift.id]
                        .orEmpty()
                        .sortedWith(compareBy(ShiftNote::createdAt, ShiftNote::id))
                        .map(ShiftNote::body)
                        .filter(String::isNotBlank)
                        .takeIf { privacy.includeShiftNotes }
                        .orEmpty(),
                ),
            )
        }
        input.independentExtras.forEach { extra ->
            if (YearMonth.from(extra.ownerLocalDate) != input.month) return@forEach
            val sourceKey = extra.id.toString()
            val accounted = contributionIndex.total[sourceKey].sumValues()
            val extras = contributionIndex.extras[sourceKey].toExtraBreakdown()
            add(
                UnsafeWorkRow(
                    sourceId = extra.id,
                    ownerLocalDate = extra.ownerLocalDate,
                    kind = ReportWorkKind.INDEPENDENT_EXTRA,
                    state = ReportWorkState.COMPLETED,
                    sector = extra.sector,
                    workPlace = safePlaceLabel(
                        extra.snapshot.workPlaceName,
                        extra.snapshot.workPlaceAbbreviation,
                    ),
                    workType = extra.snapshot.workTypeName,
                    plannedStart = null,
                    plannedEnd = null,
                    actualStart = extra.start,
                    actualEnd = extra.end,
                    zoneId = extra.zoneId,
                    accountedMinutes = accounted,
                    regularMinutes = 0L,
                    extraBreakdown = extras,
                    pendingMinutes = 0L,
                    nightMinutes = contributionIndex.nights[sourceKey].sumValues(),
                    holidayMinutes = contributionIndex.holidays[sourceKey].sumValues(),
                    weekendMinutes = contributionIndex.weekends[sourceKey].sumValues(),
                    position = extra.snapshot.position.takeIf { privacy.includePosition },
                    notes = emptyList(),
                ),
            )
        }
    }.sortedWith(
        compareBy<UnsafeWorkRow>(
            UnsafeWorkRow::ownerLocalDate,
            { it.actualStart ?: it.plannedStart },
            { it.actualEnd ?: it.plannedEnd },
            UnsafeWorkRow::sourceId,
        ),
    )
    return rows.mapIndexed { index, row ->
        ReportWorkRow(
            stableOrder = index,
            ownerLocalDate = row.ownerLocalDate,
            kind = row.kind,
            state = row.state,
            sector = row.sector,
            workPlace = row.workPlace,
            workType = row.workType,
            plannedStart = row.plannedStart,
            plannedEnd = row.plannedEnd,
            actualStart = row.actualStart,
            actualEnd = row.actualEnd,
            zoneId = row.zoneId,
            accountedMinutes = row.accountedMinutes,
            regularMinutes = row.regularMinutes,
            extraBreakdown = Collections.unmodifiableList(row.extraBreakdown.toList()),
            pendingMinutes = row.pendingMinutes,
            nightMinutes = row.nightMinutes,
            holidayMinutes = row.holidayMinutes,
            weekendMinutes = row.weekendMinutes,
            position = row.position,
            notes = Collections.unmodifiableList(row.notes.toList()),
        )
    }
}

private fun V2ShiftWrite.reportState(
    displayStart: Instant,
    displayEnd: Instant,
    generatedAt: Instant,
): ReportWorkState = when (shift.status) {
    ShiftStatus.CANCELLED -> ReportWorkState.CANCELLED
    ShiftStatus.ABSENT -> ReportWorkState.ABSENT
    ShiftStatus.PLANNED -> when {
        generatedAt < displayStart -> ReportWorkState.SCHEDULED
        generatedAt < displayEnd -> ReportWorkState.IN_PROGRESS
        else -> ReportWorkState.COMPLETED
    }
}

private fun List<SummaryContribution>?.sumValues(): Long =
    orEmpty().fold(0L) { total, contribution -> Math.addExact(total, contribution.value) }

private fun List<SummaryContribution>?.toExtraBreakdown(): List<ReportExtraBreakdown> =
    orEmpty()
        .groupBy { contribution -> contribution.extraClassLabel ?: "Extra" }
        .map { (label, contributions) ->
            ReportExtraBreakdown(label, contributions.sumOf(SummaryContribution::value))
        }
        .filter { it.minutes > 0L }
        .sortedBy { it.className.lowercase(Locale.ROOT) }

private data class UnsafeAvailability(
    val sourceId: UUID,
    val ownerLocalDate: LocalDate,
    val sector: WorkSector,
    val label: String,
    val start: Instant,
    val end: Instant,
    val zoneId: ZoneId,
    val state: AvailabilityTemporalState,
    val programmed: Long,
    val effective: Long,
    val replaced: Long,
    val pending: Long,
    val projected: Long,
    val futureOccupied: Long,
)

private fun buildAvailabilityRows(
    input: MonthlySummaryInput,
    actualsByShiftId: Map<UUID, ShiftActualAggregate>,
    clock: Clock,
): List<ReportAvailabilityRow> {
    val protections = input.medicalLeaves.map { it.startDate..it.endDateInclusive } +
        input.vacations.map { it.startDate..it.endDateInclusive }
    val activeWork: List<AvailabilityActiveWorkInterval> = resolveAvailabilityActiveWorkIntervals(
        shifts = input.shifts,
        actualsByShiftId = actualsByShiftId,
        independentExtras = input.independentExtras,
        protectedOwnerDates = protections,
    )
    return input.availabilityWindows
        .asSequence()
        .filter { YearMonth.from(it.ownerLocalDate) == input.month }
        .map { window ->
            val result = calculateAvailabilityIntervalBreakdown(
                window = window,
                activeWork = activeWork,
                isProtected = protections.any { window.ownerLocalDate in it },
                clock = clock,
            ).totals
            UnsafeAvailability(
                sourceId = window.id,
                ownerLocalDate = window.ownerLocalDate,
                sector = window.sector,
                label = window.labelSnapshot,
                start = window.start,
                end = window.end,
                zoneId = window.zoneId,
                state = result.state,
                programmed = result.programmedMinutes,
                effective = result.effectiveElapsedMinutes,
                replaced = result.replacedElapsedMinutes,
                pending = result.futurePendingMinutes,
                projected = result.effectiveProjectedAtEndMinutes,
                futureOccupied = result.futureOccupiedByPlannedWorkMinutes,
            )
        }
        .sortedWith(compareBy(UnsafeAvailability::ownerLocalDate, UnsafeAvailability::start, UnsafeAvailability::end, UnsafeAvailability::sourceId))
        .mapIndexed { index, row ->
            ReportAvailabilityRow(
                stableOrder = index,
                ownerLocalDate = row.ownerLocalDate,
                sector = row.sector,
                label = row.label,
                start = row.start,
                end = row.end,
                zoneId = row.zoneId,
                state = row.state,
                programmedMinutes = row.programmed,
                effectiveMinutes = row.effective,
                replacedMinutes = row.replaced,
                pendingMinutes = row.pending,
                projectedMinutes = row.projected,
                futureOccupiedMinutes = row.futureOccupied,
            )
        }
        .toList()
}

private fun reconcileAvailability(
    summary: MonthlySummaryProjection,
    rows: List<ReportAvailabilityRow>,
) {
    val canonical = summary.availability
    require(rows.sumOf { it.programmedMinutes } == (canonical?.programmed?.value ?: 0L))
    require(rows.sumOf { it.effectiveMinutes } == (canonical?.effectiveElapsed?.value ?: 0L))
    require(rows.sumOf { it.replacedMinutes } == (canonical?.replacedElapsed?.value ?: 0L))
    require(rows.sumOf { it.pendingMinutes } == (canonical?.pending?.value ?: 0L))
    require(rows.sumOf { it.projectedMinutes } == (canonical?.projectedEffectiveAtEnd?.value ?: 0L))
}

private data class UnsafeSituation(
    val date: LocalDate,
    val kind: ReportSituationKind,
    val label: String,
    val stableIdentity: String,
)

private fun buildSituations(input: MonthlySummaryInput): List<ReportSituationRow> {
    val start = input.month.atDay(1)
    val end = input.month.atEndOfMonth()
    val rows = buildList {
        input.shifts.forEach { write ->
            val kind = when (write.shift.status) {
                ShiftStatus.ABSENT -> ReportSituationKind.ABSENCE
                ShiftStatus.CANCELLED -> ReportSituationKind.CANCELLATION
                ShiftStatus.PLANNED -> null
            } ?: return@forEach
            if (write.shift.localStartDate !in start..end) return@forEach
            add(
                UnsafeSituation(
                    write.shift.localStartDate,
                    kind,
                    if (kind == ReportSituationKind.ABSENCE) "Ausencia" else "Cancelación",
                    write.shift.id.toString(),
                ),
            )
        }
        input.explicitDayStatuses
            .filter { it.date in start..end }
            .distinctBy { it.date }
            .forEach { status ->
                val (kind, label) = when (status.type) {
                    ExplicitDayStatusType.DAY_OFF -> ReportSituationKind.DAY_OFF to "F"
                    ExplicitDayStatusType.UNDEFINED -> ReportSituationKind.UNDEFINED to "?"
                }
                add(UnsafeSituation(status.date, kind, label, "${status.type}:${status.date}"))
            }
        input.vacations.forEach { vacation ->
            eachDate(vacation.startDate, vacation.endDateInclusive, start, end) { date ->
                add(UnsafeSituation(date, ReportSituationKind.VACATION, "Vacaciones", vacation.id.toString()))
            }
        }
        input.medicalLeaves.forEach { leave ->
            eachDate(leave.startDate, leave.endDateInclusive, start, end) { date ->
                add(UnsafeSituation(date, ReportSituationKind.MEDICAL_LEAVE, "Carpeta médica", leave.id.toString()))
            }
        }
    }.distinctBy { listOf(it.date, it.kind, it.stableIdentity) }
        .sortedWith(compareBy(UnsafeSituation::date, UnsafeSituation::kind, UnsafeSituation::stableIdentity))
    return rows.mapIndexed { index, row ->
        ReportSituationRow(index, row.date, row.kind, row.label)
    }
}

private inline fun eachDate(
    rawStart: LocalDate,
    rawEnd: LocalDate,
    boundaryStart: LocalDate,
    boundaryEnd: LocalDate,
    action: (LocalDate) -> Unit,
) {
    var cursor = maxOf(rawStart, boundaryStart)
    val end = minOf(rawEnd, boundaryEnd)
    while (!cursor.isAfter(end)) {
        action(cursor)
        cursor = cursor.plusDays(1)
    }
}

private data class UnsafeNote(
    val date: LocalDate,
    val kind: ReportNoteKind,
    val context: String,
    val body: String,
    val stableIdentity: String,
)

private fun buildNotes(
    snapshot: MonthlyReportSourceSnapshot,
    workRows: List<ReportWorkRow>,
    privacy: ReportPrivacySelection,
): List<ReportNoteRow> {
    val rows = buildList {
        if (privacy.includeShiftNotes) {
            workRows.forEach { row ->
                row.notes.forEachIndexed { noteIndex, body ->
                    add(
                        UnsafeNote(
                            date = row.ownerLocalDate,
                            kind = ReportNoteKind.SHIFT,
                            context = "${row.workPlace} · ${row.workType}",
                            body = body,
                            stableIdentity = "${row.stableOrder}:$noteIndex",
                        ),
                    )
                }
            }
        }
        if (privacy.includeMedicalNotes) {
            val monthStart = snapshot.request.month.atDay(1)
            val monthEnd = snapshot.request.month.plusMonths(1).atDay(1)
            snapshot.summaryInput.medicalLeaves
                .filter { leave ->
                    leave.startDate < monthEnd && !leave.endDateInclusive.isBefore(monthStart)
                }
                .filter { !it.privateNote.isNullOrBlank() }
                .forEach { leave ->
                    add(
                        UnsafeNote(
                            date = leave.startDate,
                            kind = ReportNoteKind.MEDICAL_LEAVE,
                            context = if (leave.startDate == leave.endDateInclusive) {
                                "Carpeta médica del ${leave.startDate}"
                            } else {
                                "Carpeta médica del ${leave.startDate} al ${leave.endDateInclusive}"
                            },
                            body = requireNotNull(leave.privateNote),
                            stableIdentity = leave.id.toString(),
                        ),
                    )
                }
        }
    }.sortedWith(compareBy(UnsafeNote::date, UnsafeNote::kind, UnsafeNote::stableIdentity))
    return rows.mapIndexed { index, row ->
        ReportNoteRow(index, row.date, row.kind, row.context, row.body)
    }
}

private fun buildReferenceRows(
    input: MonthlySummaryInput,
    summary: MonthlySummaryProjection,
): List<ReportReferencePeriod> {
    val monthStart = input.month.atDay(1)
    val monthEnd = input.month.plusMonths(1).atDay(1)
    return resolveSummaryComplianceSegments(input.configuration, input.month).map { segment ->
        val summaryPeriod = summary.compliance.singleOrNull { it.segment.sameIdentity(segment) }
        val state = segment.target.toReportState()
        val preservesNaturalWindow = segment.naturalWindow != null
        ReportReferencePeriod(
            startInclusive = if (preservesNaturalWindow) segment.startInclusive else maxOf(segment.startInclusive, monthStart),
            endExclusive = if (preservesNaturalWindow) segment.endExclusive else minOf(segment.endExclusive, monthEnd),
            state = state,
            targetMinutes = (segment.target as? HoursTargetState.Defined)?.requiredMinutes?.value,
            contributingMinutes = summaryPeriod?.contributingWork?.value,
            missingMinutes = if (state == ReportReferenceState.DEFINED) summaryPeriod?.missing?.value ?: 0L else null,
            excessMinutes = if (state == ReportReferenceState.DEFINED) summaryPeriod?.excess?.value ?: 0L else null,
        )
    }.distinct().sortedBy { it.startInclusive }
}

private fun HoursReferenceSegment.sameIdentity(other: HoursReferenceSegment): Boolean =
    startInclusive == other.startInclusive &&
        endExclusive == other.endExclusive &&
        ownerRevision.id == other.ownerRevision.id

private fun HoursTargetState.toReportState(): ReportReferenceState = when (this) {
    HoursTargetState.PendingSetup -> ReportReferenceState.PENDING_SETUP
    HoursTargetState.NotUsed -> ReportReferenceState.NOT_USED
    HoursTargetState.Unknown -> ReportReferenceState.UNKNOWN
    HoursTargetState.MissingPerPeriodValue -> ReportReferenceState.MISSING_VALUE_FOR_PERIOD
    is HoursTargetState.Defined -> ReportReferenceState.DEFINED
}

private fun sectorsPresent(
    input: MonthlySummaryInput,
    workRows: List<ReportWorkRow>,
    availabilityRows: List<ReportAvailabilityRow>,
): List<WorkSector> {
    val monthStart = input.month.atDay(1)
    val monthEnd = input.month.plusMonths(1).atDay(1)
    val revisions = input.configuration.timeline.revisions
    val configurationSectors = revisions.mapIndexedNotNull { index, revision ->
        val revisionEnd = revisions.getOrNull(index + 1)?.effectiveFrom ?: LocalDate.MAX
        revision.value.sector.takeIf {
            revision.effectiveFrom < monthEnd && revisionEnd > monthStart
        }
    }
    val sourceSectors = workRows.map(ReportWorkRow::sector) +
        availabilityRows.map(ReportAvailabilityRow::sector)
    val present = (configurationSectors + sourceSectors).toSet()
    return WorkSector.entries.filter { it in present }
}

private fun safePlaceLabel(name: String, abbreviation: String): String =
    if (name == abbreviation) name else "$name ($abbreviation)"

private val PartialDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
