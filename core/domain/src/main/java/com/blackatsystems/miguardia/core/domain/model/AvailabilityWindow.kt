package com.blackatsystems.miguardia.core.domain.model

import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.isMillisecondNormalized
import com.blackatsystems.miguardia.core.domain.work.normalizedToMilliseconds
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.UUID

data class AvailabilityWindowRecord(
    val id: UUID,
    val timelineId: UUID,
    val sector: WorkSector,
    val configurationRevisionId: UUID,
    val ownerLocalDate: LocalDate,
    val zoneId: ZoneId,
    val start: Instant,
    val end: Instant,
    val labelSnapshot: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        requireWholeAvailabilityMinute(start, "El inicio de la disponibilidad")
        requireWholeAvailabilityMinute(end, "El final de la disponibilidad")
        require(start < end) { "La disponibilidad debe tener duración positiva" }
        require(ownerLocalDate == start.atZone(zoneId).toLocalDate()) {
            "La fecha dueña de la disponibilidad debe corresponder a su inicio exacto"
        }
        require(labelSnapshot in AvailabilityLabel.entries.map(AvailabilityLabel::displayName)) {
            "La disponibilidad debe conservar uno de los tres nombres permitidos"
        }
        require(isMillisecondNormalized(createdAt) && isMillisecondNormalized(updatedAt)) {
            "Las fechas técnicas de la disponibilidad deben expresarse en milisegundos"
        }
        require(!updatedAt.isBefore(createdAt)) {
            "La actualización de la disponibilidad no puede anteceder a su creación"
        }
    }

    val durationMinutes: Long
        get() = ChronoUnit.MINUTES.between(start, end)
}

data class AvailabilityWindowDraft(
    val id: UUID,
    val ownerLocalDate: LocalDate,
    val zoneId: ZoneId,
    val start: Instant,
    val end: Instant,
)

fun buildAvailabilityWindowRecord(
    draft: AvailabilityWindowDraft,
    configuration: ResolvedWorkConfigurationRevision,
    timestamp: Instant,
    previous: AvailabilityWindowRecord? = null,
): AvailabilityWindowRecord {
    requireWholeAvailabilityMinute(draft.start, "El inicio de la disponibilidad")
    requireWholeAvailabilityMinute(draft.end, "El final de la disponibilidad")
    require(draft.start < draft.end) { "La disponibilidad debe tener duración positiva" }
    require(draft.ownerLocalDate == draft.start.atZone(draft.zoneId).toLocalDate()) {
        "La fecha dueña de la disponibilidad debe corresponder a su inicio exacto"
    }
    require(configuration.referenceDate == draft.ownerLocalDate) {
        "La configuración debe resolverse para la fecha dueña exacta de la disponibilidad"
    }
    val label = configuration.revision.value.availabilityLabel
    if (previous == null) {
        requireNotNull(label) { "La disponibilidad no está habilitada para la fecha elegida" }
    }
    previous?.let { stored ->
        require(stored.id == draft.id) { "Corregir una disponibilidad no puede cambiar su identidad" }
        require(stored.ownerLocalDate == draft.ownerLocalDate) {
            "Corregir una disponibilidad no puede cambiar su fecha dueña"
        }
        require(stored.timelineId == configuration.timelineId && stored.sector == configuration.revision.value.sector) {
            "La disponibilidad corregida debe conservar su línea temporal y sector"
        }
    }
    val normalizedTimestamp = timestamp.normalizedToMilliseconds()
    previous?.let {
        require(normalizedTimestamp.isAfter(it.updatedAt)) {
            "La corrección de la disponibilidad debe avanzar en el tiempo"
        }
    }
    return AvailabilityWindowRecord(
        id = draft.id,
        timelineId = configuration.timelineId,
        sector = configuration.revision.value.sector,
        configurationRevisionId = previous?.configurationRevisionId ?: configuration.revision.id,
        ownerLocalDate = draft.ownerLocalDate,
        zoneId = draft.zoneId,
        start = draft.start,
        end = draft.end,
        labelSnapshot = previous?.labelSnapshot ?: requireNotNull(label).displayName,
        createdAt = previous?.createdAt ?: normalizedTimestamp,
        updatedAt = normalizedTimestamp,
    )
}

enum class AvailabilityTemporalState {
    FUTURE,
    IN_PROGRESS,
    COMPLETED,
    PROTECTED,
}

enum class AvailabilityActiveWorkKind {
    SHIFT_PLANNED,
    SHIFT_ACTUAL,
    INDEPENDENT_EXTRA,
}

data class AvailabilityActiveWorkInterval(
    val key: String,
    val kind: AvailabilityActiveWorkKind,
    val start: Instant,
    val end: Instant,
) {
    init {
        requireWholeAvailabilityMinute(start, "El inicio del trabajo activo")
        requireWholeAvailabilityMinute(end, "El final del trabajo activo")
        require(start < end) { "El trabajo activo debe tener duración positiva" }
    }
}

fun resolveAvailabilityActiveWorkIntervals(
    shifts: Iterable<V2ShiftWrite>,
    actualsByShiftId: Map<UUID, ShiftActualAggregate>,
    independentExtras: Iterable<IndependentExtraWorkRecord>,
    protectedOwnerDates: Iterable<ClosedRange<LocalDate>>,
): List<AvailabilityActiveWorkInterval> {
    val protectedDates = protectedOwnerDates.toList()
    val intervals = buildList {
        shifts.forEach { write ->
            val shift = write.shift
            if (shift.status != ShiftStatus.PLANNED) return@forEach
            val actual = actualsByShiftId[shift.id]?.record
            if (actual != null) {
                require(
                    actual.shiftId == shift.id &&
                        actual.timelineId == write.snapshot.timelineId &&
                        actual.sector == write.snapshot.sector,
                ) { "El horario real no pertenece a la jornada observada" }
                add(
                    AvailabilityActiveWorkInterval(
                        key = "shift:${shift.id}",
                        kind = AvailabilityActiveWorkKind.SHIFT_ACTUAL,
                        start = actual.actualStart,
                        end = actual.actualEnd,
                    ),
                )
            } else if (protectedDates.none { shift.localStartDate in it }) {
                add(
                    AvailabilityActiveWorkInterval(
                        key = "shift:${shift.id}",
                        kind = AvailabilityActiveWorkKind.SHIFT_PLANNED,
                        start = shift.startAt,
                        end = shift.endAt,
                    ),
                )
            }
        }
        independentExtras.forEach { extra ->
            add(
                AvailabilityActiveWorkInterval(
                    key = "extra:${extra.id}",
                    kind = AvailabilityActiveWorkKind.INDEPENDENT_EXTRA,
                    start = extra.start,
                    end = extra.end,
                ),
            )
        }
    }
    return intervals.sortedWith(
        compareBy(
            AvailabilityActiveWorkInterval::start,
            AvailabilityActiveWorkInterval::end,
            AvailabilityActiveWorkInterval::key,
        ),
    )
}

data class AvailabilityBreakdown(
    val state: AvailabilityTemporalState,
    val programmedMinutes: Long,
    val effectiveElapsedMinutes: Long,
    val replacedElapsedMinutes: Long,
    val futurePendingMinutes: Long,
    val effectiveProjectedAtEndMinutes: Long,
    val futureOccupiedByPlannedWorkMinutes: Long,
) {
    init {
        require(
            listOf(
                programmedMinutes,
                effectiveElapsedMinutes,
                replacedElapsedMinutes,
                futurePendingMinutes,
                effectiveProjectedAtEndMinutes,
                futureOccupiedByPlannedWorkMinutes,
            ).all { it >= 0L },
        ) { "Los resultados de disponibilidad no pueden ser negativos" }
    }
}

data class AvailabilityTotals(
    val programmedMinutes: Long,
    val effectiveElapsedMinutes: Long,
    val replacedElapsedMinutes: Long,
    val futurePendingMinutes: Long,
    val effectiveProjectedAtEndMinutes: Long,
    val futureOccupiedByPlannedWorkMinutes: Long,
) {
    init {
        require(
            listOf(
                programmedMinutes,
                effectiveElapsedMinutes,
                replacedElapsedMinutes,
                futurePendingMinutes,
                effectiveProjectedAtEndMinutes,
                futureOccupiedByPlannedWorkMinutes,
            ).all { it >= 0L },
        ) { "Los totales de disponibilidad no pueden ser negativos" }
    }
}

fun sumAvailabilityBreakdowns(results: Iterable<AvailabilityBreakdown>): AvailabilityTotals =
    results.fold(
        AvailabilityTotals(0L, 0L, 0L, 0L, 0L, 0L),
    ) { totals, result ->
        AvailabilityTotals(
            programmedMinutes = Math.addExact(totals.programmedMinutes, result.programmedMinutes),
            effectiveElapsedMinutes = Math.addExact(
                totals.effectiveElapsedMinutes,
                result.effectiveElapsedMinutes,
            ),
            replacedElapsedMinutes = Math.addExact(
                totals.replacedElapsedMinutes,
                result.replacedElapsedMinutes,
            ),
            futurePendingMinutes = Math.addExact(totals.futurePendingMinutes, result.futurePendingMinutes),
            effectiveProjectedAtEndMinutes = Math.addExact(
                totals.effectiveProjectedAtEndMinutes,
                result.effectiveProjectedAtEndMinutes,
            ),
            futureOccupiedByPlannedWorkMinutes = Math.addExact(
                totals.futureOccupiedByPlannedWorkMinutes,
                result.futureOccupiedByPlannedWorkMinutes,
            ),
        )
    }

fun calculateAvailabilityBreakdown(
    window: AvailabilityWindowRecord,
    activeWork: Iterable<AvailabilityActiveWorkInterval>,
    isProtected: Boolean,
    clock: Clock,
): AvailabilityBreakdown {
    val now = clock.instant().truncatedTo(ChronoUnit.MINUTES)
    val programmed = window.durationMinutes
    if (isProtected) {
        return AvailabilityBreakdown(
            state = AvailabilityTemporalState.PROTECTED,
            programmedMinutes = programmed,
            effectiveElapsedMinutes = 0L,
            replacedElapsedMinutes = 0L,
            futurePendingMinutes = 0L,
            effectiveProjectedAtEndMinutes = 0L,
            futureOccupiedByPlannedWorkMinutes = 0L,
        )
    }
    val elapsedEnd = when {
        now <= window.start -> window.start
        now >= window.end -> window.end
        else -> now
    }
    val futureStart = when {
        now <= window.start -> window.start
        now >= window.end -> window.end
        else -> now
    }
    val clipped = activeWork.mapNotNull { source ->
        val start = maxOf(window.start, source.start)
        val end = minOf(window.end, source.end)
        if (start < end) start to end else null
    }
    val replacedElapsed = unionMinutes(clipped, window.start, elapsedEnd)
    val futureOccupied = unionMinutes(clipped, futureStart, window.end)
    val elapsed = ChronoUnit.MINUTES.between(window.start, elapsedEnd)
    val future = ChronoUnit.MINUTES.between(futureStart, window.end)
    val totalReplaced = unionMinutes(clipped, window.start, window.end)
    return AvailabilityBreakdown(
        state = when {
            now < window.start -> AvailabilityTemporalState.FUTURE
            now >= window.end -> AvailabilityTemporalState.COMPLETED
            else -> AvailabilityTemporalState.IN_PROGRESS
        },
        programmedMinutes = programmed,
        effectiveElapsedMinutes = Math.subtractExact(elapsed, replacedElapsed),
        replacedElapsedMinutes = replacedElapsed,
        futurePendingMinutes = Math.subtractExact(future, futureOccupied),
        effectiveProjectedAtEndMinutes = Math.subtractExact(programmed, totalReplaced),
        futureOccupiedByPlannedWorkMinutes = futureOccupied,
    )
}

private fun unionMinutes(
    intervals: Iterable<Pair<Instant, Instant>>,
    boundaryStart: Instant,
    boundaryEnd: Instant,
): Long {
    if (boundaryStart >= boundaryEnd) return 0L
    val ordered = intervals.mapNotNull { (rawStart, rawEnd) ->
        val start = maxOf(boundaryStart, rawStart)
        val end = minOf(boundaryEnd, rawEnd)
        if (start < end) start to end else null
    }.sortedBy { it.first }
    var total = 0L
    var currentStart: Instant? = null
    var currentEnd: Instant? = null
    ordered.forEach { (start, end) ->
        val previousEnd = currentEnd
        if (currentStart == null || previousEnd == null) {
            currentStart = start
            currentEnd = end
        } else if (start <= previousEnd) {
            currentEnd = maxOf(previousEnd, end)
        } else {
            total = Math.addExact(total, ChronoUnit.MINUTES.between(requireNotNull(currentStart), previousEnd))
            currentStart = start
            currentEnd = end
        }
    }
    currentStart?.let { start ->
        total = Math.addExact(total, ChronoUnit.MINUTES.between(start, requireNotNull(currentEnd)))
    }
    return total
}

data class AvailabilityWindowVersion(
    val id: UUID,
    val start: Instant,
    val end: Instant,
    val updatedAt: Instant,
)

data class AvailabilitySourceVersion(
    val key: String,
    val start: Instant,
    val end: Instant,
    val version: String,
)

@ConsistentCopyVisibility
data class AvailabilityWindowExpectation private constructor(
    val previous: AvailabilityWindowRecord?,
    val configuration: ResolvedWorkConfigurationRevision,
    val observedStart: Instant,
    val observedEnd: Instant,
    val observedWindows: Set<AvailabilityWindowVersion>,
    val observedActiveSources: Set<AvailabilitySourceVersion>,
    val protectionFingerprint: String,
) {
    init {
        require(observedStart < observedEnd) { "La ventana observada debe tener duración positiva" }
        previous?.let { require(it.start >= observedStart && it.end <= observedEnd) }
        require(previous == null || previous.id !in observedWindows.map { it.id }) {
            "La ventana corregida no debe aparecer entre sus vecinas"
        }
        require(observedWindows.map { it.id }.distinct().size == observedWindows.size) {
            "La expectativa no puede repetir ventanas"
        }
        require(observedActiveSources.map { it.key }.distinct().size == observedActiveSources.size) {
            "La expectativa no puede repetir fuentes activas"
        }
    }

    companion object {
        fun capture(
            previous: AvailabilityWindowRecord?,
            configuration: ResolvedWorkConfigurationRevision,
            observedStart: Instant,
            observedEnd: Instant,
            observedWindows: Iterable<AvailabilityWindowVersion>,
            observedActiveSources: Iterable<AvailabilitySourceVersion>,
            protectionFingerprint: String,
        ): AvailabilityWindowExpectation = AvailabilityWindowExpectation(
            previous = previous,
            configuration = configuration,
            observedStart = observedStart,
            observedEnd = observedEnd,
            observedWindows = Collections.unmodifiableSet(LinkedHashSet(observedWindows.toList())),
            observedActiveSources = Collections.unmodifiableSet(LinkedHashSet(observedActiveSources.toList())),
            protectionFingerprint = protectionFingerprint,
        )
    }

    fun overlaps(record: AvailabilityWindowRecord): Boolean = observedWindows.any { other ->
        other.start < record.end && other.end > record.start
    }
}

data class AvailabilityWindowMutation(
    val expectation: AvailabilityWindowExpectation,
    val replacement: AvailabilityWindowRecord?,
) {
    init {
        val previous = expectation.previous
        require(previous != null || replacement != null) {
            "No se puede eliminar una disponibilidad inexistente"
        }
        previous?.let { record ->
            require(
                record.timelineId == expectation.configuration.timelineId &&
                    record.sector == expectation.configuration.revision.value.sector &&
                    record.ownerLocalDate == expectation.configuration.referenceDate,
            ) {
                "La disponibilidad observada no pertenece a la configuración usada por la mutación"
            }
        }
        replacement?.let { record ->
            require(previous == null || record.id == previous.id) {
                "Una corrección no puede cambiar la identidad de la disponibilidad"
            }
            require(record.timelineId == expectation.configuration.timelineId) {
                "La disponibilidad no pertenece a la línea temporal observada"
            }
            require(record.sector == expectation.configuration.revision.value.sector) {
                "La disponibilidad no pertenece al sector observado"
            }
            require(expectation.configuration.referenceDate == record.ownerLocalDate) {
                "La configuración observada no corresponde a la fecha dueña"
            }
            require(!expectation.overlaps(record)) {
                "La disponibilidad no puede superponerse con otra ventana"
            }
        }
    }
}

sealed interface AvailabilityWindowWriteResult {
    data class Saved(val record: AvailabilityWindowRecord) : AvailabilityWindowWriteResult
    data object Deleted : AvailabilityWindowWriteResult
    data object Conflict : AvailabilityWindowWriteResult
    data object Overlap : AvailabilityWindowWriteResult
}

fun AvailabilityWindowRecord.toAvailabilityVersion(): AvailabilityWindowVersion = AvailabilityWindowVersion(
    id = id,
    start = start,
    end = end,
    updatedAt = updatedAt,
)

private fun requireWholeAvailabilityMinute(value: Instant, label: String) {
    require(value.nano == 0 && value.epochSecond % 60L == 0L) {
        "$label debe expresarse en minutos enteros"
    }
}
