package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.database.entity.ExplicitDayStatusEntity
import com.blackatsystems.miguardia.core.database.entity.HolidayEntity
import com.blackatsystems.miguardia.core.database.entity.MedicalLeaveEntity
import com.blackatsystems.miguardia.core.database.entity.ObjectiveEntity
import com.blackatsystems.miguardia.core.database.entity.SchedulePhotoEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNoteEntity
import com.blackatsystems.miguardia.core.database.entity.VacationEntity
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
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
    sourceObjectiveId = sourceObjectiveId.toString(),
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
        sourceObjectiveId = UUID.fromString(sourceObjectiveId),
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

internal fun SchedulePhoto.toEntity() = SchedulePhotoEntity(
    id = id.toString(), month = month.toString(), objectiveId = objectiveId?.toString(),
    objectiveNameSnapshot = objectiveNameSnapshot,
    objectiveAbbreviationSnapshot = objectiveAbbreviationSnapshot,
    storageKey = storageKey, mimeType = mimeType, byteSize = byteSize,
    pixelWidth = pixelWidth, pixelHeight = pixelHeight,
    createdAtEpochMillis = createdAt.toEpochMilli(), updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun SchedulePhotoEntity.toDomain(): SchedulePhoto = decodeEntity("foto de cronograma", id) {
    SchedulePhoto(
        id = UUID.fromString(id), month = YearMonth.parse(month),
        objectiveId = objectiveId?.let(UUID::fromString),
        objectiveNameSnapshot = objectiveNameSnapshot,
        objectiveAbbreviationSnapshot = objectiveAbbreviationSnapshot,
        storageKey = storageKey, mimeType = mimeType, byteSize = byteSize,
        pixelWidth = pixelWidth, pixelHeight = pixelHeight,
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

private inline fun <T> decodeEntity(kind: String, id: String, block: () -> T): T = try {
    block()
} catch (error: IllegalArgumentException) {
    throw InvalidLocalDataException("La fila de $kind $id contiene datos inválidos.", error)
}
