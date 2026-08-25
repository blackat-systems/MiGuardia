package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.database.entity.ExtraWorkClassEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftActualRecordEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftExtraIntervalEntity
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftExtraInterval
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import java.time.Instant
import java.util.UUID

internal fun ExtraWorkClass.toEntity(): ExtraWorkClassEntity = ExtraWorkClassEntity(
    id = id.toString(),
    timelineId = timelineId.toString(),
    sector = sector.encodeSector(),
    name = name,
    normalizedNameKey = normalizedNameKey,
    helpsMeetHoursReference = helpsMeetHoursReference,
    showDedicatedSummary = showDedicatedSummary,
    isActive = isActive,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ExtraWorkClassEntity.toDomainExtraWorkClass(): ExtraWorkClass = decodeActualRow(
    "clase extra",
    id,
) {
    ExtraWorkClass(
        id = UUID.fromString(id),
        timelineId = UUID.fromString(timelineId),
        sector = sector.decodeSector(),
        name = name,
        normalizedNameKey = normalizedNameKey,
        helpsMeetHoursReference = helpsMeetHoursReference,
        showDedicatedSummary = showDedicatedSummary,
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun ShiftActualRecord.toEntity(): ShiftActualRecordEntity = ShiftActualRecordEntity(
    shiftId = shiftId.toString(),
    timelineId = timelineId.toString(),
    sector = sector.encodeSector(),
    actualStartEpochMillis = actualStart.toEpochMilli(),
    actualEndEpochMillis = actualEnd.toEpochMilli(),
    differenceReason = differenceReason,
    explanation = explanation,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ShiftActualRecordEntity.toDomainActualRecord(): ShiftActualRecord = decodeActualRow(
    "horario real",
    shiftId,
) {
    ShiftActualRecord(
        shiftId = UUID.fromString(shiftId),
        timelineId = UUID.fromString(timelineId),
        sector = sector.decodeSector(),
        actualStart = Instant.ofEpochMilli(actualStartEpochMillis),
        actualEnd = Instant.ofEpochMilli(actualEndEpochMillis),
        differenceReason = differenceReason,
        explanation = explanation,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun ShiftExtraInterval.toEntity(): ShiftExtraIntervalEntity = ShiftExtraIntervalEntity(
    id = id.toString(),
    shiftId = shiftId.toString(),
    timelineId = timelineId.toString(),
    sector = sector.encodeSector(),
    extraWorkClassId = extraWorkClassId.toString(),
    startEpochMillis = start.toEpochMilli(),
    endEpochMillis = end.toEpochMilli(),
    classNameSnapshot = classNameSnapshot,
    helpsMeetHoursReferenceSnapshot = helpsMeetHoursReferenceSnapshot,
    showDedicatedSummarySnapshot = showDedicatedSummarySnapshot,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ShiftExtraIntervalEntity.toDomainExtraInterval(): ShiftExtraInterval = decodeActualRow(
    "fragmento extra",
    id,
) {
    ShiftExtraInterval(
        id = UUID.fromString(id),
        shiftId = UUID.fromString(shiftId),
        timelineId = UUID.fromString(timelineId),
        sector = sector.decodeSector(),
        extraWorkClassId = UUID.fromString(extraWorkClassId),
        start = Instant.ofEpochMilli(startEpochMillis),
        end = Instant.ofEpochMilli(endEpochMillis),
        classNameSnapshot = classNameSnapshot,
        helpsMeetHoursReferenceSnapshot = helpsMeetHoursReferenceSnapshot,
        showDedicatedSummarySnapshot = showDedicatedSummarySnapshot,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

private inline fun <T> decodeActualRow(kind: String, id: String, block: () -> T): T = try {
    block()
} catch (error: IllegalArgumentException) {
    throw InvalidLocalDataException("La fila de $kind $id contiene datos inválidos.", error)
}
