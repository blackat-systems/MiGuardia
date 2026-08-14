package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.database.entity.ExplicitDayStatusEntity
import com.blackatsystems.miguardia.core.database.entity.FormalShiftChangeEntity
import com.blackatsystems.miguardia.core.database.entity.HolidayEntity
import com.blackatsystems.miguardia.core.database.entity.MedicalLeaveEntity
import com.blackatsystems.miguardia.core.database.entity.ObjectiveEntity
import com.blackatsystems.miguardia.core.database.entity.ScheduleCombinationEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNoteEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNoveltyEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftOperationalSnapshotEntity
import com.blackatsystems.miguardia.core.database.entity.VacationEntity
import com.blackatsystems.miguardia.core.domain.model.FormalShiftChange
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNovelty
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyType
import com.blackatsystems.miguardia.core.domain.model.ShiftOperationalSnapshot
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
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

internal fun Vacation.toEntity() = VacationEntity(
    id = id.toString(),
    startDate = startDate.toString(),
    endDateInclusive = endDateInclusive.toString(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun VacationEntity.toDomain(): Vacation = decodeEntity("vacaciones", id) {
    Vacation(
        id = UUID.fromString(id),
        startDate = LocalDate.parse(startDate),
        endDateInclusive = LocalDate.parse(endDateInclusive),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun Holiday.toEntity() = HolidayEntity(
    id = id.toString(),
    localDate = date.toString(),
    name = name,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun HolidayEntity.toDomain(): Holiday = decodeEntity("feriado", id) {
    Holiday(
        id = UUID.fromString(id),
        date = LocalDate.parse(localDate),
        name = name,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun ShiftNote.toEntity() = ShiftNoteEntity(
    id = id.toString(),
    shiftId = shiftId.toString(),
    body = body,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ShiftNoteEntity.toDomain(): ShiftNote = decodeEntity("nota de guardia", id) {
    ShiftNote(
        id = UUID.fromString(id),
        shiftId = UUID.fromString(shiftId),
        body = body,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun ShiftNovelty.toEntity() = ShiftNoveltyEntity(
    id = id.toString(),
    shiftId = shiftId.toString(),
    type = type.name,
    description = description,
    relatedShiftId = relatedShiftId?.toString(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ShiftNoveltyEntity.toDomain(): ShiftNovelty = decodeEntity("novedad", id) {
    ShiftNovelty(
        id = UUID.fromString(id),
        shiftId = UUID.fromString(shiftId),
        type = ShiftNoveltyType.valueOf(type),
        description = description,
        relatedShiftId = relatedShiftId?.let(UUID::fromString),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun ShiftOperationalSnapshot.toEntity() = ShiftOperationalSnapshotEntity(
    startEpochMillis = startAt.toEpochMilli(),
    endEpochMillis = endAt.toEpochMilli(),
    zoneId = zoneId.id,
    localStartDate = localStartDate.toString(),
    objectiveName = objectiveName,
    objectiveAbbreviation = objectiveAbbreviation,
    objectiveAddress = objectiveAddress,
    startTime = startTime.toString(),
    endTime = endTime.toString(),
    colorArgb = colorArgb,
    position = position,
    status = status.name,
    sourceObjectiveId = sourceObjectiveId?.toString(),
    sourceScheduleCombinationId = sourceScheduleCombinationId?.toString(),
)

internal fun ShiftOperationalSnapshotEntity.toDomain(): ShiftOperationalSnapshot = ShiftOperationalSnapshot(
    startAt = Instant.ofEpochMilli(startEpochMillis),
    endAt = Instant.ofEpochMilli(endEpochMillis),
    zoneId = ZoneId.of(zoneId),
    localStartDate = LocalDate.parse(localStartDate),
    objectiveName = objectiveName,
    objectiveAbbreviation = objectiveAbbreviation,
    objectiveAddress = objectiveAddress,
    startTime = LocalTime.parse(startTime),
    endTime = LocalTime.parse(endTime),
    colorArgb = colorArgb,
    position = position,
    status = ShiftStatus.valueOf(status),
    sourceObjectiveId = sourceObjectiveId?.let(UUID::fromString),
    sourceScheduleCombinationId = sourceScheduleCombinationId?.let(UUID::fromString),
)

internal fun FormalShiftChange.toEntity() = FormalShiftChangeEntity(
    id = id.toString(),
    shiftId = shiftId.toString(),
    scheduleChanged = scheduleChanged,
    objectiveChanged = objectiveChanged,
    description = description,
    original = original.toEntity(),
    final = final.toEntity(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun FormalShiftChangeEntity.toDomain(): FormalShiftChange = decodeEntity("cambio formal", id) {
    FormalShiftChange(
        id = UUID.fromString(id),
        shiftId = UUID.fromString(shiftId),
        scheduleChanged = scheduleChanged,
        objectiveChanged = objectiveChanged,
        description = description,
        original = original.toDomain(),
        final = final.toDomain(),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

private inline fun <T> decodeEntity(kind: String, id: String, block: () -> T): T = try {
    block()
} catch (error: IllegalArgumentException) {
    throw InvalidLocalDataException("La fila de $kind $id contiene datos inválidos.", error)
}
