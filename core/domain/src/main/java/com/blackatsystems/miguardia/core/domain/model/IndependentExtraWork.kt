package com.blackatsystems.miguardia.core.domain.model

import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.isMillisecondNormalized
import com.blackatsystems.miguardia.core.domain.work.normalizeOptionalWorkText
import com.blackatsystems.miguardia.core.domain.work.normalizeRequiredWorkText
import com.blackatsystems.miguardia.core.domain.work.normalizedToMilliseconds
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.UUID

data class IndependentExtraWorkSnapshot(
    val workPlaceName: String,
    val workPlaceAbbreviation: String,
    val workPlaceAddress: String?,
    val workTypeName: String,
    val workTypeBehavior: WorkTypeBehavior,
    val colorArgb: Int,
    val position: String?,
    val className: String,
    val helpsMeetHoursReference: Boolean,
    val showDedicatedSummary: Boolean,
) {
    init {
        require(
            workPlaceName == normalizeRequiredWorkText(workPlaceName, "El nombre histórico del lugar"),
        ) { "El nombre histórico del lugar debe estar normalizado" }
        require(
            workPlaceAbbreviation == normalizeRequiredWorkText(
                workPlaceAbbreviation,
                "La abreviatura histórica del lugar",
            ),
        ) { "La abreviatura histórica del lugar debe estar normalizada" }
        require(workPlaceAddress == normalizeOptionalWorkText(workPlaceAddress)) {
            "La dirección histórica del lugar debe estar normalizada"
        }
        require(
            workTypeName == normalizeRequiredWorkText(workTypeName, "El tipo histórico del extra"),
        ) { "El tipo histórico del extra debe estar normalizado" }
        require(position == normalizeOptionalWorkText(position)) {
            "El puesto histórico debe estar normalizado"
        }
        require(className == normalizeRequiredWorkText(className, "La clase histórica del extra")) {
            "La clase histórica del extra debe estar normalizada"
        }
    }
}

data class IndependentExtraWorkRecord(
    val id: UUID,
    val timelineId: UUID,
    val sector: WorkSector,
    val configurationRevisionId: UUID,
    val workPlaceId: UUID,
    val objectiveId: UUID,
    val workTypeId: UUID,
    val templateId: UUID?,
    val extraWorkClassId: UUID,
    val ownerLocalDate: LocalDate,
    val zoneId: ZoneId,
    val start: Instant,
    val end: Instant,
    val snapshot: IndependentExtraWorkSnapshot,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        requireWholeMinute(start, "El inicio del extra independiente")
        requireWholeMinute(end, "El final del extra independiente")
        require(start < end) { "El extra independiente debe tener duración positiva" }
        require(ownerLocalDate == start.atZone(zoneId).toLocalDate()) {
            "La fecha dueña del extra debe corresponder a su inicio exacto"
        }
        require(isMillisecondNormalized(createdAt) && isMillisecondNormalized(updatedAt)) {
            "Las fechas del extra independiente deben expresarse en milisegundos"
        }
        require(!updatedAt.isBefore(createdAt)) {
            "La actualización del extra independiente no puede anteceder a su creación"
        }
    }

    val durationMinutes: Long
        get() = exactDurationMinutes(start, end)
}

data class IndependentExtraWorkSelection(
    val configuration: ResolvedWorkConfigurationRevision,
    val workPlace: WorkPlace,
    val objective: Objective,
    val workType: WorkType,
    val template: WorkTemplate?,
    val extraWorkClass: ExtraWorkClass,
) {
    init {
        val timelineId = configuration.timelineId
        val sector = configuration.revision.value.sector
        require(
            workPlace.timelineId == timelineId &&
                workType.timelineId == timelineId &&
                extraWorkClass.timelineId == timelineId,
        ) { "Las fuentes del extra independiente deben compartir la línea temporal" }
        require(
            workPlace.sector == sector &&
                workType.sector == sector &&
                extraWorkClass.sector == sector,
        ) { "Las fuentes del extra independiente deben compartir el sector" }
        require(workPlace.objectiveId == objective.id) {
            "El objetivo elegido no corresponde al lugar"
        }
        require(workType.behavior == WorkTypeBehavior.ACTIVE_WORK) {
            "El tipo elegido no representa trabajo activo"
        }
        template?.let { selected ->
            require(
                selected.timelineId == timelineId &&
                    selected.sector == sector &&
                    selected.workPlaceId == workPlace.id &&
                    selected.objectiveId == objective.id &&
                    selected.workTypeId == workType.id,
            ) { "La plantilla elegida no corresponde al lugar y tipo seleccionados" }
        }
    }
}

data class IndependentExtraWorkDraft(
    val id: UUID,
    val ownerLocalDate: LocalDate,
    val zoneId: ZoneId,
    val start: Instant,
    val end: Instant,
    val colorArgb: Int,
    val position: String?,
)

fun buildIndependentExtraWorkRecord(
    draft: IndependentExtraWorkDraft,
    selection: IndependentExtraWorkSelection,
    clock: Clock,
    timestamp: Instant,
    previous: IndependentExtraWorkRecord? = null,
    preserveHistoricalSnapshot: Boolean = false,
): IndependentExtraWorkRecord {
    val normalizedNow = clock.instant().truncatedTo(ChronoUnit.MINUTES)
    val normalizedTimestamp = timestamp.normalizedToMilliseconds()
    requireWholeMinute(draft.start, "El inicio del extra independiente")
    requireWholeMinute(draft.end, "El final del extra independiente")
    require(draft.start < draft.end) { "El extra independiente debe tener duración positiva" }
    require(!draft.end.isAfter(normalizedNow)) {
        "El extra independiente debe representar trabajo ya finalizado"
    }
    require(draft.ownerLocalDate == draft.start.atZone(draft.zoneId).toLocalDate()) {
        "La fecha dueña del extra debe corresponder a su inicio exacto"
    }
    require(selection.configuration.referenceDate == draft.ownerLocalDate) {
        "La configuración laboral debe resolverse para la fecha dueña exacta del extra"
    }
    require(!draft.ownerLocalDate.isAfter(LocalDate.now(clock.withZone(draft.zoneId)))) {
        "El extra independiente no puede pertenecer a una fecha futura"
    }
    previous?.let {
        require(it.id == draft.id) { "Corregir un extra no puede cambiar su identidad" }
        require(normalizedTimestamp.isAfter(it.updatedAt)) {
            "La corrección del extra independiente debe avanzar en el tiempo"
        }
    }

    val keepsSameSources = previous != null &&
        previous.timelineId == selection.configuration.timelineId &&
        previous.workPlaceId == selection.workPlace.id &&
        previous.objectiveId == selection.objective.id &&
        previous.workTypeId == selection.workType.id &&
        previous.templateId == selection.template?.id &&
        previous.extraWorkClassId == selection.extraWorkClass.id
    val shouldPreserveHistoricalSnapshot = preserveHistoricalSnapshot || keepsSameSources
    if (shouldPreserveHistoricalSnapshot) {
        val existing = requireNotNull(previous) {
            "Sólo una corrección puede conservar fotografías históricas"
        }
        require(
            existing.timelineId == selection.configuration.timelineId &&
                existing.workPlaceId == selection.workPlace.id &&
                existing.objectiveId == selection.objective.id &&
                existing.workTypeId == selection.workType.id &&
                existing.templateId == selection.template?.id &&
                existing.extraWorkClassId == selection.extraWorkClass.id,
        ) { "Conservar fotografías exige mantener exactamente las fuentes históricas" }
    } else {
        require(
            selection.workPlace.isActive &&
                selection.objective.isActive &&
                selection.workType.isActive &&
                selection.extraWorkClass.isActive &&
                (selection.template?.isActive != false),
        ) { "Crear o reclasificar un extra exige fuentes activas" }
    }

    val snapshot = if (shouldPreserveHistoricalSnapshot) {
        val existing = requireNotNull(previous)
        existing.snapshot.copy(
            colorArgb = if (existing.templateId == null) draft.colorArgb else existing.snapshot.colorArgb,
            position = normalizeOptionalWorkText(draft.position),
        )
    } else {
        IndependentExtraWorkSnapshot(
            workPlaceName = selection.objective.fullName,
            workPlaceAbbreviation = selection.objective.abbreviation,
            workPlaceAddress = selection.objective.address,
            workTypeName = selection.workType.name,
            workTypeBehavior = selection.workType.behavior,
            colorArgb = selection.template?.colorArgb ?: draft.colorArgb,
            position = normalizeOptionalWorkText(draft.position),
            className = selection.extraWorkClass.name,
            helpsMeetHoursReference = selection.extraWorkClass.helpsMeetHoursReference,
            showDedicatedSummary = selection.extraWorkClass.showDedicatedSummary,
        )
    }
    return IndependentExtraWorkRecord(
        id = draft.id,
        timelineId = selection.configuration.timelineId,
        sector = selection.configuration.revision.value.sector,
        configurationRevisionId = if (shouldPreserveHistoricalSnapshot) {
            requireNotNull(previous).configurationRevisionId
        } else {
            selection.configuration.revision.id
        },
        workPlaceId = selection.workPlace.id,
        objectiveId = selection.objective.id,
        workTypeId = selection.workType.id,
        templateId = selection.template?.id,
        extraWorkClassId = selection.extraWorkClass.id,
        ownerLocalDate = draft.ownerLocalDate,
        zoneId = draft.zoneId,
        start = draft.start,
        end = draft.end,
        snapshot = snapshot,
        createdAt = previous?.createdAt ?: normalizedTimestamp,
        updatedAt = normalizedTimestamp,
    )
}

data class IndependentExtraOccupancyVersion(
    val id: UUID,
    val start: Instant,
    val end: Instant,
    val updatedAt: Instant,
)

data class IndependentExtraProtectedDateRange(
    val startDateInclusive: LocalDate,
    val endDateInclusive: LocalDate,
) {
    init {
        require(!endDateInclusive.isBefore(startDateInclusive)) {
            "La protección observada no puede tener un rango invertido"
        }
    }

    fun intersects(startDate: LocalDate, endDate: LocalDate): Boolean =
        !endDateInclusive.isBefore(startDate) && !startDateInclusive.isAfter(endDate)
}

@ConsistentCopyVisibility
data class IndependentExtraWorkExpectation private constructor(
    val previous: IndependentExtraWorkRecord?,
    val selection: IndependentExtraWorkSelection,
    val windowStart: Instant,
    val windowEnd: Instant,
    val windowStartDate: LocalDate,
    val windowEndDateInclusive: LocalDate,
    val observedShifts: Set<ShiftOccupancyVersion>,
    val observedExtras: Set<IndependentExtraOccupancyVersion>,
    val observedProtectedDateRanges: Set<IndependentExtraProtectedDateRange>,
    val protectionFingerprint: String,
) {
    init {
        require(windowStart < windowEnd) { "La ventana observada debe tener duración positiva" }
        require(!windowEndDateInclusive.isBefore(windowStartDate)) {
            "La ventana local observada no puede estar invertida"
        }
        previous?.let { record ->
            require(record.start >= windowStart && record.end <= windowEnd) {
                "La ventana observada debe incluir el intervalo anterior completo"
            }
            require(coversLocalDatesOf(record)) {
                "La ventana local observada debe incluir todos los días del intervalo anterior"
            }
        }
        require(previous == null || previous.id !in observedExtras.map { it.id }) {
            "La ocupación vecina no debe incluir el registro que se está corrigiendo"
        }
        require(observedExtras.map { it.id }.distinct().size == observedExtras.size) {
            "La ocupación observada no puede repetir extras independientes"
        }
        require(observedShifts.map { it.shiftId }.distinct().size == observedShifts.size) {
            "La ocupación observada no puede repetir jornadas"
        }
    }

    companion object {
        fun capture(
            previous: IndependentExtraWorkRecord?,
            selection: IndependentExtraWorkSelection,
            windowStart: Instant,
            windowEnd: Instant,
            windowStartDate: LocalDate,
            windowEndDateInclusive: LocalDate,
            observedShifts: Iterable<ShiftOccupancyVersion>,
            observedExtras: Iterable<IndependentExtraOccupancyVersion>,
            observedProtectedDateRanges: Iterable<IndependentExtraProtectedDateRange> = emptyList(),
            protectionFingerprint: String,
        ): IndependentExtraWorkExpectation = IndependentExtraWorkExpectation(
            previous = previous,
            selection = selection,
            windowStart = windowStart,
            windowEnd = windowEnd,
            windowStartDate = windowStartDate,
            windowEndDateInclusive = windowEndDateInclusive,
            observedShifts = Collections.unmodifiableSet(LinkedHashSet(observedShifts.toList())),
            observedExtras = Collections.unmodifiableSet(LinkedHashSet(observedExtras.toList())),
            observedProtectedDateRanges = Collections.unmodifiableSet(
                LinkedHashSet(observedProtectedDateRanges.toList()),
            ),
            protectionFingerprint = protectionFingerprint,
        )
    }

    fun hasOverlappingWorkFor(record: IndependentExtraWorkRecord): Boolean =
        observedShifts.any { occupied ->
            occupied.startAt < record.end && occupied.endAt > record.start
        } || observedExtras.any { occupied ->
            occupied.start < record.end && occupied.end > record.start
        }

    fun hasProtectedDatesFor(record: IndependentExtraWorkRecord): Boolean {
        val lastOccupiedDate = record.end.minusNanos(1).atZone(record.zoneId).toLocalDate()
        return observedProtectedDateRanges.any { range ->
            range.intersects(record.ownerLocalDate, lastOccupiedDate)
        }
    }
}

data class IndependentExtraWorkMutation(
    val expectation: IndependentExtraWorkExpectation,
    val replacement: IndependentExtraWorkRecord?,
    val overlappingWorkConfirmed: Boolean,
    val protectedDateConfirmed: Boolean,
) {
    init {
        val previous = expectation.previous
        require(previous != null || replacement != null) {
            "No se puede eliminar un extra independiente inexistente"
        }
        replacement?.let { record ->
            require(record.id == (previous?.id ?: record.id)) {
                "Una corrección no puede cambiar la identidad del extra"
            }
            require(record.timelineId == expectation.selection.configuration.timelineId) {
                "El extra no pertenece a la línea temporal observada"
            }
            require(record.sector == expectation.selection.configuration.revision.value.sector) {
                "El extra no pertenece al sector observado"
            }
            val preservesHistoricalSources = previous != null &&
                record.workPlaceId == previous.workPlaceId &&
                record.objectiveId == previous.objectiveId &&
                record.workTypeId == previous.workTypeId &&
                record.templateId == previous.templateId &&
                record.extraWorkClassId == previous.extraWorkClassId
            val expectedRevisionId = if (preservesHistoricalSources) {
                requireNotNull(previous).configurationRevisionId
            } else {
                expectation.selection.configuration.revision.id
            }
            require(
                record.configurationRevisionId == expectedRevisionId &&
                    record.workPlaceId == expectation.selection.workPlace.id &&
                    record.objectiveId == expectation.selection.objective.id &&
                    record.workTypeId == expectation.selection.workType.id &&
                    record.templateId == expectation.selection.template?.id &&
                    record.extraWorkClassId == expectation.selection.extraWorkClass.id,
            ) { "Las fuentes guardadas no coinciden con las fuentes observadas" }
            require(record.start >= expectation.windowStart && record.end <= expectation.windowEnd) {
                "El extra debe permanecer dentro de la ventana observada"
            }
            require(expectation.coversLocalDatesOf(record)) {
                "La ventana local observada debe incluir todos los días trabajados"
            }
        }
        val ownerDate = replacement?.ownerLocalDate ?: requireNotNull(previous).ownerLocalDate
        require(expectation.selection.configuration.referenceDate == ownerDate) {
            "La configuración observada no corresponde a la fecha dueña del extra"
        }
    }
}

private fun IndependentExtraWorkExpectation.coversLocalDatesOf(
    record: IndependentExtraWorkRecord,
): Boolean {
    val lastOccupiedDate = record.end.minusNanos(1).atZone(record.zoneId).toLocalDate()
    return !windowStartDate.isAfter(record.ownerLocalDate) &&
        !windowEndDateInclusive.isBefore(lastOccupiedDate)
}

sealed interface IndependentExtraWorkWriteResult {
    data class Saved(val record: IndependentExtraWorkRecord) : IndependentExtraWorkWriteResult
    data object Deleted : IndependentExtraWorkWriteResult
    data object Conflict : IndependentExtraWorkWriteResult
}

fun IndependentExtraWorkRecord.toOccupancyVersion(): IndependentExtraOccupancyVersion =
    IndependentExtraOccupancyVersion(id = id, start = start, end = end, updatedAt = updatedAt)

private fun requireWholeMinute(value: Instant, label: String) {
    require(value.nano == 0 && value.epochSecond % SECONDS_PER_MINUTE == 0L) {
        "$label debe expresarse en minutos enteros"
    }
}

private const val SECONDS_PER_MINUTE: Long = 60L
