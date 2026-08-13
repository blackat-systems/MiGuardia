package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.database.entity.ExplicitDayStatusEntity
import com.blackatsystems.miguardia.core.database.entity.MedicalLeaveEntity
import com.blackatsystems.miguardia.core.database.entity.ObjectiveEntity
import com.blackatsystems.miguardia.core.database.entity.ScheduleCombinationEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftEntity
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

internal fun Objective.toEntity() = ObjectiveEntity(
    id = id.toString(),
    fullName = fullName,
    abbreviation = abbreviation,
    address = address,
    note = note,
    isActive = isActive,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ObjectiveEntity.toDomain(): Objective = decodeEntity("objetivo", id) {
    Objective(
        id = UUID.fromString(id),
        fullName = fullName,
        abbreviation = abbreviation,
        address = address,
        note = note,
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun ScheduleCombination.toEntity() = ScheduleCombinationEntity(
    id = id.toString(),
    objectiveId = objectiveId.toString(),
    startTime = startTime.toString(),
    endTime = endTime.toString(),
    colorArgb = colorArgb,
    isActive = isActive,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ScheduleCombinationEntity.toDomain(): ScheduleCombination = decodeEntity("combinación", id) {
    ScheduleCombination(
        id = UUID.fromString(id),
        objectiveId = UUID.fromString(objectiveId),
        startTime = LocalTime.parse(startTime),
        endTime = LocalTime.parse(endTime),
        colorArgb = colorArgb,
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun Shift.toEntity() = ShiftEntity(
    id = id.toString(),
    startEpochMillis = startAt.toEpochMilli(),
    endEpochMillis = endAt.toEpochMilli(),
    zoneId = zoneId.id,
    localStartDate = localStartDate.toString(),
    objectiveNameSnapshot = objectiveNameSnapshot,
    objectiveAbbreviationSnapshot = objectiveAbbreviationSnapshot,
    objectiveAddressSnapshot = objectiveAddressSnapshot,
    startTimeSnapshot = startTimeSnapshot.toString(),
    endTimeSnapshot = endTimeSnapshot.toString(),
    colorArgbSnapshot = colorArgbSnapshot,
    position = position,
    status = status.name,
    sourceObjectiveId = sourceObjectiveId?.toString(),
    sourceScheduleCombinationId = sourceScheduleCombinationId?.toString(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ShiftEntity.toDomain(): Shift = decodeEntity("guardia", id) {
    Shift(
        id = UUID.fromString(id),
        startAt = Instant.ofEpochMilli(startEpochMillis),
        endAt = Instant.ofEpochMilli(endEpochMillis),
        zoneId = ZoneId.of(zoneId),
        localStartDate = LocalDate.parse(localStartDate),
        objectiveNameSnapshot = objectiveNameSnapshot,
        objectiveAbbreviationSnapshot = objectiveAbbreviationSnapshot,
        objectiveAddressSnapshot = objectiveAddressSnapshot,
        startTimeSnapshot = LocalTime.parse(startTimeSnapshot),
        endTimeSnapshot = LocalTime.parse(endTimeSnapshot),
        colorArgbSnapshot = colorArgbSnapshot,
        position = position,
        status = ShiftStatus.valueOf(status),
        sourceObjectiveId = sourceObjectiveId?.let(UUID::fromString),
        sourceScheduleCombinationId = sourceScheduleCombinationId?.let(UUID::fromString),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun ExplicitDayStatus.toEntity() = ExplicitDayStatusEntity(
    localDate = date.toString(),
    type = type.name,
)

internal fun ExplicitDayStatusEntity.toDomain(): ExplicitDayStatus = decodeEntity("estado diario", localDate) {
    ExplicitDayStatus(
        date = LocalDate.parse(localDate),
        type = ExplicitDayStatusType.valueOf(type),
    )
}

internal fun MedicalLeave.toEntity() = MedicalLeaveEntity(
    id = id.toString(),
    startDate = startDate.toString(),
    endDateInclusive = endDateInclusive.toString(),
    privateNote = privateNote,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun MedicalLeaveEntity.toDomain(): MedicalLeave = decodeEntity("carpeta médica", id) {
    MedicalLeave(
        id = UUID.fromString(id),
        startDate = LocalDate.parse(startDate),
        endDateInclusive = LocalDate.parse(endDateInclusive),
        privateNote = privateNote,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

private inline fun <T> decodeEntity(kind: String, id: String, block: () -> T): T = try {
    block()
} catch (error: IllegalArgumentException) {
    throw InvalidLocalDataException("La fila de $kind $id contiene datos inválidos.", error)
}
