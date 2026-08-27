package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.database.entity.AvailabilityWindowEntity
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal fun AvailabilityWindowRecord.toAvailabilityEntity(): AvailabilityWindowEntity =
    AvailabilityWindowEntity(
        id = id.toString(),
        timelineId = timelineId.toString(),
        sector = sector.encodeSector(),
        configurationRevisionId = configurationRevisionId.toString(),
        ownerLocalDate = ownerLocalDate.toString(),
        zoneId = zoneId.id,
        startEpochMillis = start.toEpochMilli(),
        endEpochMillis = end.toEpochMilli(),
        labelSnapshot = labelSnapshot,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        updatedAtEpochMillis = updatedAt.toEpochMilli(),
    )

internal fun AvailabilityWindowEntity.toDomainAvailability(): AvailabilityWindowRecord = try {
    AvailabilityWindowRecord(
        id = UUID.fromString(id),
        timelineId = UUID.fromString(timelineId),
        sector = sector.decodeSector(),
        configurationRevisionId = UUID.fromString(configurationRevisionId),
        ownerLocalDate = LocalDate.parse(ownerLocalDate),
        zoneId = ZoneId.of(zoneId),
        start = Instant.ofEpochMilli(startEpochMillis),
        end = Instant.ofEpochMilli(endEpochMillis),
        labelSnapshot = labelSnapshot,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
} catch (error: RuntimeException) {
    throw InvalidLocalDataException("La disponibilidad $id contiene datos inválidos.", error)
}
