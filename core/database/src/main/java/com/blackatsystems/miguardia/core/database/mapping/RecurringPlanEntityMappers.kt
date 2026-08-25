package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.database.entity.RecurringOccurrenceEntity
import com.blackatsystems.miguardia.core.database.entity.RecurringPlanEntity
import com.blackatsystems.miguardia.core.database.entity.RecurringPlanRevisionEntity
import com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrence
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState
import com.blackatsystems.miguardia.core.domain.model.RecurringPattern
import com.blackatsystems.miguardia.core.domain.model.RecurringPlan
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevision
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

internal fun RecurringPlan.toEntity() = RecurringPlanEntity(
    id = id.toString(),
    timelineId = timelineId.toString(),
    sector = sector.encodeSector(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
)

internal fun RecurringPlanRevision.toEntity(): RecurringPlanRevisionEntity {
    val encodedPattern = pattern.encode()
    return RecurringPlanRevisionEntity(
        id = id.toString(),
        planId = planId.toString(),
        revisionNumber = revisionNumber,
        effectiveFrom = effectiveFrom.toString(),
        kind = kind.name,
        endDateInclusive = endDateInclusive.toString(),
        patternKind = encodedPattern.kind,
        weekdaysMask = encodedPattern.weekdaysMask,
        intervalCount = encodedPattern.intervalCount,
        monthlyOrdinal = encodedPattern.monthlyOrdinal,
        monthlyDayOfWeek = encodedPattern.monthlyDayOfWeek,
        templateId = templateId.toString(),
        workPlaceId = workPlaceId.toString(),
        objectiveId = objectiveId.toString(),
        workTypeId = workTypeId.toString(),
        objectiveNameSnapshot = objectiveNameSnapshot,
        objectiveAbbreviationSnapshot = objectiveAbbreviationSnapshot,
        objectiveAddressSnapshot = objectiveAddressSnapshot,
        workTypeNameSnapshot = workTypeNameSnapshot,
        workTypeBehaviorSnapshot = workTypeBehaviorSnapshot.name,
        startTimeSnapshot = startTimeSnapshot.toString(),
        endTimeSnapshot = endTimeSnapshot.toString(),
        colorArgbSnapshot = colorArgbSnapshot,
        positionSnapshot = positionSnapshot,
        zoneId = zoneId.id,
        createdAtEpochMillis = createdAt.toEpochMilli(),
    )
}

internal fun RecurringOccurrence.toEntity() = RecurringOccurrenceEntity(
    planId = planId.toString(),
    localDate = localDate.toString(),
    revisionId = revisionId.toString(),
    shiftId = shiftId?.toString(),
    state = state.name,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun decodeRecurringPlanAggregate(
    plan: RecurringPlanEntity,
    revisions: List<RecurringPlanRevisionEntity>,
    occurrences: List<RecurringOccurrenceEntity>,
): RecurringPlanAggregate = decodeRecurringRows {
    RecurringPlanAggregate(
        plan = plan.toDomain(),
        revisions = revisions.map(RecurringPlanRevisionEntity::toDomain),
        occurrences = occurrences.map(RecurringOccurrenceEntity::toDomain),
    )
}

internal fun RecurringOccurrenceEntity.toDomainOccurrence(): RecurringOccurrence =
    decodeRecurringRows { toDomain() }

private fun RecurringPlanEntity.toDomain() = RecurringPlan(
    id = UUID.fromString(id),
    timelineId = UUID.fromString(timelineId),
    sector = sector.decodeSector(),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

private fun RecurringPlanRevisionEntity.toDomain() = RecurringPlanRevision(
    id = UUID.fromString(id),
    planId = UUID.fromString(planId),
    revisionNumber = revisionNumber,
    effectiveFrom = LocalDate.parse(effectiveFrom),
    kind = RecurringPlanRevisionKind.valueOf(kind),
    endDateInclusive = LocalDate.parse(endDateInclusive),
    pattern = decodePattern(),
    templateId = UUID.fromString(templateId),
    workPlaceId = UUID.fromString(workPlaceId),
    objectiveId = UUID.fromString(objectiveId),
    workTypeId = UUID.fromString(workTypeId),
    objectiveNameSnapshot = objectiveNameSnapshot,
    objectiveAbbreviationSnapshot = objectiveAbbreviationSnapshot,
    objectiveAddressSnapshot = objectiveAddressSnapshot,
    workTypeNameSnapshot = workTypeNameSnapshot,
    workTypeBehaviorSnapshot = WorkTypeBehavior.valueOf(workTypeBehaviorSnapshot),
    startTimeSnapshot = LocalTime.parse(startTimeSnapshot),
    endTimeSnapshot = LocalTime.parse(endTimeSnapshot),
    colorArgbSnapshot = colorArgbSnapshot,
    positionSnapshot = positionSnapshot,
    zoneId = ZoneId.of(zoneId),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

private fun RecurringOccurrenceEntity.toDomain() = RecurringOccurrence(
    planId = UUID.fromString(planId),
    localDate = LocalDate.parse(localDate),
    revisionId = UUID.fromString(revisionId),
    shiftId = shiftId?.let(UUID::fromString),
    state = RecurringOccurrenceState.valueOf(state),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

private data class EncodedRecurringPattern(
    val kind: String,
    val weekdaysMask: Int? = null,
    val intervalCount: Int? = null,
    val monthlyOrdinal: String? = null,
    val monthlyDayOfWeek: Int? = null,
)

private fun RecurringPattern.encode(): EncodedRecurringPattern = when (this) {
    is RecurringPattern.Weekdays -> EncodedRecurringPattern(
        kind = PATTERN_WEEKDAYS,
        weekdaysMask = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) },
    )

    is RecurringPattern.EveryNDays -> EncodedRecurringPattern(
        kind = PATTERN_EVERY_N_DAYS,
        intervalCount = intervalCount,
    )

    is RecurringPattern.EveryNWeeks -> EncodedRecurringPattern(
        kind = PATTERN_EVERY_N_WEEKS,
        intervalCount = intervalCount,
    )

    is RecurringPattern.Monthly -> EncodedRecurringPattern(
        kind = PATTERN_MONTHLY,
        monthlyOrdinal = ordinal.name,
        monthlyDayOfWeek = dayOfWeek.value,
    )
}

private fun RecurringPlanRevisionEntity.decodePattern(): RecurringPattern = when (patternKind) {
    PATTERN_WEEKDAYS -> {
        require(intervalCount == null && monthlyOrdinal == null && monthlyDayOfWeek == null) {
            "El patrón semanal contiene parámetros incompatibles"
        }
        val mask = requireNotNull(weekdaysMask) { "El patrón semanal no indica días" }
        require(mask in 1..VALID_WEEKDAYS_MASK) { "La máscara semanal es inválida" }
        RecurringPattern.Weekdays.of(
            DayOfWeek.entries.filter { day -> mask and (1 shl (day.value - 1)) != 0 },
        )
    }

    PATTERN_EVERY_N_DAYS -> {
        require(weekdaysMask == null && monthlyOrdinal == null && monthlyDayOfWeek == null) {
            "El patrón cada N días contiene parámetros incompatibles"
        }
        RecurringPattern.EveryNDays(requireNotNull(intervalCount))
    }

    PATTERN_EVERY_N_WEEKS -> {
        require(weekdaysMask == null && monthlyOrdinal == null && monthlyDayOfWeek == null) {
            "El patrón cada N semanas contiene parámetros incompatibles"
        }
        RecurringPattern.EveryNWeeks(requireNotNull(intervalCount))
    }

    PATTERN_MONTHLY -> {
        require(weekdaysMask == null && intervalCount == null) {
            "El patrón mensual contiene parámetros incompatibles"
        }
        RecurringPattern.Monthly(
            ordinal = MonthlyOrdinal.valueOf(requireNotNull(monthlyOrdinal)),
            dayOfWeek = DayOfWeek.of(requireNotNull(monthlyDayOfWeek)),
        )
    }

    else -> error("Código de patrón recurrente desconocido: $patternKind")
}

private inline fun <T> decodeRecurringRows(block: () -> T): T = try {
    block()
} catch (error: InvalidLocalDataException) {
    throw error
} catch (error: RuntimeException) {
    throw InvalidLocalDataException("Los planes recurrentes almacenados contienen datos inválidos.", error)
}

private const val PATTERN_WEEKDAYS = "WEEKDAYS"
private const val PATTERN_EVERY_N_DAYS = "EVERY_N_DAYS"
private const val PATTERN_EVERY_N_WEEKS = "EVERY_N_WEEKS"
private const val PATTERN_MONTHLY = "MONTHLY"
private const val VALID_WEEKDAYS_MASK = 0b111_1111
