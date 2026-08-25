package com.blackatsystems.miguardia.core.domain.model

import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.isMillisecondNormalized
import com.blackatsystems.miguardia.core.domain.work.normalizeOptionalWorkText
import com.blackatsystems.miguardia.core.domain.work.normalizeRequiredWorkText
import com.blackatsystems.miguardia.core.domain.work.normalizedToMilliseconds
import java.time.Clock
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections
import java.util.UUID

data class ShiftActualRecord(
    val shiftId: UUID,
    val timelineId: UUID,
    val sector: WorkSector,
    val actualStart: Instant,
    val actualEnd: Instant,
    val differenceReason: String,
    val explanation: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        requireWholeMinute(actualStart, "El inicio real")
        requireWholeMinute(actualEnd, "El final real")
        require(actualStart < actualEnd) { "El inicio real debe ser anterior al final real" }
        require(
            differenceReason == normalizeRequiredWorkText(differenceReason, "El motivo de la diferencia"),
        ) { "El motivo de la diferencia debe estar normalizado" }
        require(explanation == normalizeOptionalWorkText(explanation)) {
            "La explicación debe estar normalizada"
        }
        require(isMillisecondNormalized(createdAt) && isMillisecondNormalized(updatedAt)) {
            "Las fechas del registro real deben expresarse en milisegundos"
        }
        require(!updatedAt.isBefore(createdAt)) {
            "La actualización del registro real no puede ser anterior a su creación"
        }
    }

    val durationMinutes: Long
        get() = exactDurationMinutes(actualStart, actualEnd)
}

data class ShiftExtraInterval(
    val id: UUID,
    val shiftId: UUID,
    val timelineId: UUID,
    val sector: WorkSector,
    val extraWorkClassId: UUID,
    val start: Instant,
    val end: Instant,
    val classNameSnapshot: String,
    val helpsMeetHoursReferenceSnapshot: Boolean,
    val showDedicatedSummarySnapshot: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        requireWholeMinute(start, "El inicio del fragmento extra")
        requireWholeMinute(end, "El final del fragmento extra")
        require(start < end) { "Un fragmento extra debe tener duración positiva" }
        require(
            classNameSnapshot == normalizeRequiredWorkText(
                classNameSnapshot,
                "El nombre histórico de la clase extra",
            ),
        ) { "El nombre histórico de la clase extra debe estar normalizado" }
        require(isMillisecondNormalized(createdAt) && isMillisecondNormalized(updatedAt)) {
            "Las fechas del fragmento extra deben expresarse en milisegundos"
        }
        require(!updatedAt.isBefore(createdAt)) {
            "La actualización del fragmento no puede ser anterior a su creación"
        }
    }

    val durationMinutes: Long
        get() = exactDurationMinutes(start, end)
}

class ShiftActualAggregate(
    val record: ShiftActualRecord,
    extraIntervals: List<ShiftExtraInterval>,
) {
    val extraIntervals: List<ShiftExtraInterval> = Collections.unmodifiableList(
        ArrayList(extraIntervals),
    )

    init {
        val ordered = this.extraIntervals.sortedWith(
            compareBy(ShiftExtraInterval::start, ShiftExtraInterval::end, ShiftExtraInterval::id),
        )
        require(this.extraIntervals == ordered) { "Los fragmentos extra deben conservar un orden estable" }
        require(this.extraIntervals.map { it.id }.distinct().size == this.extraIntervals.size) {
            "Un registro real no puede repetir fragmentos"
        }
        require(this.extraIntervals.all { interval ->
            interval.shiftId == record.shiftId &&
                interval.timelineId == record.timelineId &&
                interval.sector == record.sector
        }) { "Los fragmentos extra deben pertenecer al mismo registro real" }
        require(this.extraIntervals.map { it.extraWorkClassId }.distinct().size <= 1) {
            "Todos los fragmentos extra deben compartir una sola clase"
        }
    }

    val totalMinutes: Long
        get() = record.durationMinutes

    val extraMinutes: Long
        get() = extraIntervals.fold(0L) { total, interval ->
            Math.addExact(total, interval.durationMinutes)
        }

    val regularMinutes: Long
        get() = Math.subtractExact(totalMinutes, extraMinutes)

    operator fun component1(): ShiftActualRecord = record

    operator fun component2(): List<ShiftExtraInterval> = extraIntervals

    fun copy(
        record: ShiftActualRecord = this.record,
        extraIntervals: List<ShiftExtraInterval> = this.extraIntervals,
    ): ShiftActualAggregate = ShiftActualAggregate(record, extraIntervals)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShiftActualAggregate) return false
        return record == other.record && extraIntervals == other.extraIntervals
    }

    override fun hashCode(): Int = 31 * record.hashCode() + extraIntervals.hashCode()

    override fun toString(): String =
        "ShiftActualAggregate(record=$record, extraIntervals=$extraIntervals)"
}

enum class ShiftActualDifferenceChoice {
    ALL_REGULAR,
    EXTRA_CLASS,
}

data class ShiftActualFragmentDraft(
    val id: UUID,
    val start: Instant,
    val end: Instant,
)

sealed interface ShiftActualClassSelection {
    data class Existing(
        val observed: ExtraWorkClass,
        val preserveHistoricalSnapshot: ShiftExtraInterval? = null,
    ) : ShiftActualClassSelection

    data class NewDraft(
        val id: UUID,
        val name: String,
        val helpsMeetHoursReference: Boolean?,
        val showDedicatedSummary: Boolean?,
    ) : ShiftActualClassSelection
}

data class ShiftActualDraft(
    val actualStart: Instant,
    val actualEnd: Instant,
    val differenceReason: String,
    val explanation: String?,
    val differenceChoice: ShiftActualDifferenceChoice?,
    val classSelection: ShiftActualClassSelection?,
    val fragments: List<ShiftActualFragmentDraft>,
)

data class ShiftActualExpectation(
    val planned: V2ShiftWrite,
    val previousActual: ShiftActualAggregate?,
    val observedClass: ExtraWorkClass?,
    val recurringOccurrence: RecurringOccurrence?,
    val protectionFingerprint: String,
)

data class ShiftActualSaveMutation(
    val expectation: ShiftActualExpectation,
    val classToCreate: ExtraWorkClass?,
    val selectedClass: ExtraWorkClass?,
    val replacement: ShiftActualAggregate,
)

sealed interface ShiftActualWriteResult {
    data class Saved(val aggregate: ShiftActualAggregate) : ShiftActualWriteResult
    data object ReturnedToPlanned : ShiftActualWriteResult
    data object DuplicateClassName : ShiftActualWriteResult
    data object Conflict : ShiftActualWriteResult
}

sealed interface ExtraWorkClassWriteResult {
    data class Saved(val value: ExtraWorkClass) : ExtraWorkClassWriteResult
    data object Conflict : ExtraWorkClassWriteResult
    data object DuplicateName : ExtraWorkClassWriteResult
}

fun buildShiftActualSaveMutation(
    expectation: ShiftActualExpectation,
    draft: ShiftActualDraft,
    clock: Clock,
    timestamp: Instant,
): ShiftActualSaveMutation? {
    val planned = expectation.planned
    val shift = planned.shift
    require(shift.status == ShiftStatus.PLANNED) {
        "Sólo una jornada planificada acepta horario real"
    }
    requireWholeMinute(draft.actualStart, "El inicio real")
    requireWholeMinute(draft.actualEnd, "El final real")
    require(draft.actualStart < draft.actualEnd) {
        "El inicio real debe ser anterior al final real"
    }
    require(!draft.actualEnd.isAfter(clock.instant())) {
        "El horario real todavía no terminó"
    }
    if (draft.actualStart == shift.startAt && draft.actualEnd == shift.endAt) return null

    val normalizedReason = normalizeRequiredWorkText(draft.differenceReason, "El motivo de la diferencia")
    val normalizedExplanation = normalizeOptionalWorkText(draft.explanation)
    val normalizedTimestamp = timestamp.normalizedToMilliseconds()
    val previousRecord = expectation.previousActual?.record
    val createdAt = previousRecord?.createdAt ?: normalizedTimestamp
    if (previousRecord != null) {
        require(normalizedTimestamp.isAfter(previousRecord.updatedAt)) {
            "La corrección del horario real debe avanzar en el tiempo"
        }
    }
    val record = ShiftActualRecord(
        shiftId = shift.id,
        timelineId = planned.snapshot.timelineId,
        sector = planned.snapshot.sector,
        actualStart = draft.actualStart,
        actualEnd = draft.actualEnd,
        differenceReason = normalizedReason,
        explanation = normalizedExplanation,
        createdAt = createdAt,
        updatedAt = normalizedTimestamp,
    )
    val plannedMinutes = exactDurationMinutes(shift.startAt, shift.endAt)
    val differenceMinutes = Math.subtractExact(record.durationMinutes, plannedMinutes)
    val requiresChoice = differenceMinutes > 0
    if (requiresChoice) requireNotNull(draft.differenceChoice) {
        "Elegí si la diferencia es habitual o una clase extra"
    }
    if (!requiresChoice) {
        require(draft.fragments.isEmpty()) { "No puede haber extras si el horario real no supera al planificado" }
    }

    var classToCreate: ExtraWorkClass? = null
    var selectedClassForWrite: ExtraWorkClass? = null
    val intervals = when {
        !requiresChoice || draft.differenceChoice == ShiftActualDifferenceChoice.ALL_REGULAR -> {
            require(draft.fragments.isEmpty()) { "La diferencia habitual no puede guardar fragmentos extra" }
            emptyList()
        }

        else -> {
            val selection = requireNotNull(draft.classSelection) { "Elegí una clase extra" }
            val selectedClass = when (selection) {
                is ShiftActualClassSelection.Existing -> {
                    val unchangedHistoricalSelection = expectation.previousActual
                        ?.extraIntervals
                        ?.firstOrNull()
                        ?.extraWorkClassId == selection.observed.id &&
                        expectation.previousActual.extraIntervals.map {
                            ShiftActualFragmentDraft(it.id, it.start, it.end)
                        } == draft.fragments
                    require(selection.observed.isActive || unchangedHistoricalSelection) {
                        "Una clase archivada no puede usarse para una clasificación nueva"
                    }
                    require(
                        selection.observed.timelineId == planned.snapshot.timelineId &&
                            selection.observed.sector == planned.snapshot.sector,
                    ) { "La clase extra no pertenece a la configuración de la jornada" }
                    selection.observed
                }

                is ShiftActualClassSelection.NewDraft -> {
                    val helps = requireNotNull(selection.helpsMeetHoursReference) {
                        "Respondé si la clase ayuda a cumplir la referencia"
                    }
                    val dedicated = requireNotNull(selection.showDedicatedSummary) {
                        "Respondé si la clase tendrá desglose propio"
                    }
                    ExtraWorkClass.create(
                        id = selection.id,
                        timelineId = planned.snapshot.timelineId,
                        sector = planned.snapshot.sector,
                        name = selection.name,
                        helpsMeetHoursReference = helps,
                        showDedicatedSummary = dedicated,
                        timestamp = normalizedTimestamp,
                    ).also { classToCreate = it }
                }
            }
            selectedClassForWrite = selectedClass
            buildExtraIntervals(
                record = record,
                planned = shift,
                selectedClass = selectedClass,
                drafts = draft.fragments,
                previous = expectation.previousActual,
                timestamp = normalizedTimestamp,
                requiredMinutes = differenceMinutes,
            )
        }
    }
    return ShiftActualSaveMutation(
        expectation = expectation,
        classToCreate = classToCreate,
        selectedClass = selectedClassForWrite,
        replacement = ShiftActualAggregate(record, intervals),
    )
}

fun resolveActualLocalDateTime(
    localDateTime: LocalDateTime,
    zoneId: ZoneId,
    selectedOffset: ZoneOffset? = null,
): Instant {
    require(localDateTime.second == 0 && localDateTime.nano == 0) {
        "El horario local debe expresarse en minutos enteros"
    }
    val offsets = zoneId.rules.getValidOffsets(localDateTime)
    require(offsets.isNotEmpty()) { "Ese horario local no existe en ${zoneId.id}" }
    val offset = when (offsets.size) {
        1 -> offsets.single().also { only ->
            require(selectedOffset == null || selectedOffset == only) {
                "El offset elegido no corresponde al horario local"
            }
        }

        else -> requireNotNull(selectedOffset) {
            "Ese horario local es ambiguo; elegí uno de sus offsets"
        }.also { chosen ->
            require(chosen in offsets) { "El offset elegido no corresponde al horario ambiguo" }
        }
    }
    return try {
        localDateTime.toInstant(offset)
    } catch (error: DateTimeException) {
        throw IllegalArgumentException("El horario local no puede convertirse a un instante", error)
    }
}

fun effectiveWorkedInterval(
    planned: Shift,
    actual: ShiftActualAggregate?,
): Pair<Instant, Instant> = actual?.record?.let { it.actualStart to it.actualEnd }
    ?: (planned.startAt to planned.endAt)

fun requireValidStoredShiftActual(
    planned: V2ShiftWrite,
    aggregate: ShiftActualAggregate,
    selectedClass: ExtraWorkClass?,
) {
    val shift = planned.shift
    val record = aggregate.record
    require(shift.status == ShiftStatus.PLANNED) {
        "Sólo una jornada planificada puede conservar horario real"
    }
    require(
        record.shiftId == shift.id &&
            record.timelineId == planned.snapshot.timelineId &&
            record.sector == planned.snapshot.sector,
    ) { "El horario real no pertenece a la jornada planificada" }
    require(record.actualStart != shift.startAt || record.actualEnd != shift.endAt) {
        "Un horario real idéntico al planificado no debe persistirse"
    }
    val differenceMinutes = Math.subtractExact(
        record.durationMinutes,
        exactDurationMinutes(shift.startAt, shift.endAt),
    )
    if (differenceMinutes <= 0L) {
        require(aggregate.extraIntervals.isEmpty()) {
            "No puede haber extras si el horario real no supera al planificado"
        }
        require(selectedClass == null) { "No puede haber una clase extra sin fragmentos" }
        return
    }
    if (aggregate.extraIntervals.isEmpty()) {
        require(selectedClass == null) { "No puede haber una clase extra sin fragmentos" }
        return
    }
    val extraClass = requireNotNull(selectedClass) { "Los fragmentos extra requieren una clase existente" }
    require(
        extraClass.id == aggregate.extraIntervals.singleClassId() &&
            extraClass.timelineId == record.timelineId &&
            extraClass.sector == record.sector,
    ) { "La clase extra no pertenece al registro real" }
    val ordered = aggregate.extraIntervals
    ordered.forEach { fragment ->
        require(
            !fragment.createdAt.isBefore(record.createdAt) &&
                fragment.updatedAt == record.updatedAt,
        ) { "Los timestamps de los fragmentos deben pertenecer a la versión del horario real" }
        require(fragment.start >= record.actualStart && fragment.end <= record.actualEnd) {
            "Los fragmentos extra deben quedar dentro del horario real"
        }
        require(fragment.end <= shift.startAt || fragment.start >= shift.endAt) {
            "Los fragmentos extra deben ubicarse fuera del intervalo planificado"
        }
    }
    ordered.zipWithNext().forEach { (first, second) ->
        require(first.end <= second.start) { "Los fragmentos extra no pueden superponerse" }
    }
    require(aggregate.extraMinutes == differenceMinutes) {
        "Los fragmentos extra deben sumar exactamente la diferencia trabajada"
    }
}

fun requireValidShiftActualTransition(
    expectation: ShiftActualExpectation,
    replacement: ShiftActualAggregate,
    selectedClass: ExtraWorkClass?,
) {
    requireValidStoredShiftActual(expectation.planned, replacement, selectedClass)
    val previous = expectation.previousActual
    val record = replacement.record
    if (previous == null) {
        require(record.createdAt == record.updatedAt) {
            "La primera versión del horario real debe crearse en un único instante"
        }
    } else {
        require(record.createdAt == previous.record.createdAt) {
            "Corregir el horario real debe conservar su fecha de creación"
        }
        require(record.updatedAt.isAfter(previous.record.updatedAt)) {
            "La corrección del horario real debe avanzar estrictamente en el tiempo"
        }
    }

    if (replacement.extraIntervals.isEmpty()) return
    val extraClass = requireNotNull(selectedClass) {
        "Los fragmentos extra requieren una clase observada"
    }
    val previousIntervals = previous?.extraIntervals.orEmpty()
    val previousById = previousIntervals.associateBy(ShiftExtraInterval::id)
    if (!extraClass.isActive) {
        require(
            previousIntervals.size == replacement.extraIntervals.size &&
                previousIntervals.zip(replacement.extraIntervals).all { (prior, current) ->
                    prior.hasSameHistoricalIdentityAs(current)
                },
        ) {
            "Una clase archivada sólo puede conservar una clasificación histórica sin cambios"
        }
    }
    replacement.extraIntervals.forEach { fragment ->
        val priorWithSameMeaning = previousById[fragment.id]?.takeIf { prior ->
            prior.extraWorkClassId == fragment.extraWorkClassId &&
                prior.start == fragment.start &&
                prior.end == fragment.end
        }
        if (priorWithSameMeaning != null) {
            require(fragment.hasSameClassSnapshotAs(priorWithSameMeaning)) {
                "Un fragmento histórico sin reclasificar debe conservar su fotografía de clase"
            }
            require(fragment.createdAt == priorWithSameMeaning.createdAt) {
                "Un fragmento histórico sin reclasificar debe conservar su fecha de creación"
            }
        } else {
            require(extraClass.isActive) {
                "Una clase archivada no puede usarse para crear o reclasificar fragmentos"
            }
            require(fragment.hasClassSnapshotOf(extraClass)) {
                "La fotografía del fragmento debe coincidir con la clase observada al clasificar"
            }
            val previousWithSameId = previousById[fragment.id]
            val expectedCreatedAt = previousWithSameId?.createdAt ?: record.updatedAt
            require(fragment.createdAt == expectedCreatedAt) {
                "La clasificación debe conservar o crear correctamente la fecha del fragmento"
            }
        }
    }
}

private fun ShiftExtraInterval.hasSameHistoricalIdentityAs(other: ShiftExtraInterval): Boolean =
    id == other.id &&
        extraWorkClassId == other.extraWorkClassId &&
        start == other.start &&
        end == other.end

private fun ShiftExtraInterval.hasSameClassSnapshotAs(other: ShiftExtraInterval): Boolean =
    classNameSnapshot == other.classNameSnapshot &&
        helpsMeetHoursReferenceSnapshot == other.helpsMeetHoursReferenceSnapshot &&
        showDedicatedSummarySnapshot == other.showDedicatedSummarySnapshot

private fun ShiftExtraInterval.hasClassSnapshotOf(extraClass: ExtraWorkClass): Boolean =
    extraWorkClassId == extraClass.id &&
        classNameSnapshot == extraClass.name &&
        helpsMeetHoursReferenceSnapshot == extraClass.helpsMeetHoursReference &&
        showDedicatedSummarySnapshot == extraClass.showDedicatedSummary

private fun buildExtraIntervals(
    record: ShiftActualRecord,
    planned: Shift,
    selectedClass: ExtraWorkClass,
    drafts: List<ShiftActualFragmentDraft>,
    previous: ShiftActualAggregate?,
    timestamp: Instant,
    requiredMinutes: Long,
): List<ShiftExtraInterval> {
    require(requiredMinutes > 0) { "La diferencia extra debe ser positiva" }
    require(drafts.isNotEmpty()) { "Ubicá al menos un fragmento extra" }
    require(drafts.map { it.id }.distinct().size == drafts.size) {
        "No puede repetirse un fragmento extra"
    }
    val ordered = drafts.sortedWith(compareBy(ShiftActualFragmentDraft::start, ShiftActualFragmentDraft::end, ShiftActualFragmentDraft::id))
    ordered.forEach { fragment ->
        requireWholeMinute(fragment.start, "El inicio del fragmento extra")
        requireWholeMinute(fragment.end, "El final del fragmento extra")
        require(fragment.start < fragment.end) { "Un fragmento extra debe tener duración positiva" }
        require(fragment.start >= record.actualStart && fragment.end <= record.actualEnd) {
            "Los fragmentos extra deben quedar dentro del horario real"
        }
        require(fragment.end <= planned.startAt || fragment.start >= planned.endAt) {
            "Los fragmentos extra deben ubicarse fuera del intervalo planificado"
        }
    }
    ordered.zipWithNext().forEach { (first, second) ->
        require(first.end <= second.start) { "Los fragmentos extra no pueden superponerse" }
    }
    val selectedMinutes = ordered.fold(0L) { total, fragment ->
        Math.addExact(total, exactDurationMinutes(fragment.start, fragment.end))
    }
    require(selectedMinutes == requiredMinutes) {
        "Los fragmentos extra deben sumar exactamente la diferencia trabajada"
    }
    val previousById = previous?.extraIntervals?.associateBy { it.id }.orEmpty()
    return ordered.map { draft ->
        val prior = previousById[draft.id]
        val preserveHistoricalSnapshot = prior != null &&
            prior.extraWorkClassId == selectedClass.id &&
            prior.start == draft.start &&
            prior.end == draft.end
        ShiftExtraInterval(
            id = draft.id,
            shiftId = record.shiftId,
            timelineId = record.timelineId,
            sector = record.sector,
            extraWorkClassId = selectedClass.id,
            start = draft.start,
            end = draft.end,
            classNameSnapshot = if (preserveHistoricalSnapshot) prior.classNameSnapshot else selectedClass.name,
            helpsMeetHoursReferenceSnapshot = if (preserveHistoricalSnapshot) {
                prior.helpsMeetHoursReferenceSnapshot
            } else {
                selectedClass.helpsMeetHoursReference
            },
            showDedicatedSummarySnapshot = if (preserveHistoricalSnapshot) {
                prior.showDedicatedSummarySnapshot
            } else {
                selectedClass.showDedicatedSummary
            },
            createdAt = prior?.createdAt ?: timestamp,
            updatedAt = timestamp,
        )
    }
}

internal fun exactDurationMinutes(start: Instant, end: Instant): Long {
    requireWholeMinute(start, "El inicio")
    requireWholeMinute(end, "El final")
    val millis = try {
        Math.subtractExact(end.toEpochMilli(), start.toEpochMilli())
    } catch (error: ArithmeticException) {
        throw IllegalArgumentException("La duración excede el rango admitido", error)
    }
    require(millis >= 0L) { "La duración no puede ser negativa" }
    return millis / MILLIS_PER_MINUTE
}

private fun requireWholeMinute(value: Instant, label: String) {
    val millis = try {
        value.toEpochMilli()
    } catch (error: ArithmeticException) {
        throw IllegalArgumentException("$label excede el rango admitido", error)
    }
    require(value.nano % 1_000_000 == 0 && millis % MILLIS_PER_MINUTE == 0L) {
        "$label debe expresarse en minutos enteros"
    }
}

private fun List<ShiftExtraInterval>.singleClassId(): UUID =
    map(ShiftExtraInterval::extraWorkClassId).distinct().single()

private const val MILLIS_PER_MINUTE = 60_000L
