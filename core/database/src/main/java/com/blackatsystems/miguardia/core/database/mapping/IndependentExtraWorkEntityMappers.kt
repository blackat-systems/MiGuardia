package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.database.entity.IndependentExtraWorkRecordEntity
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal fun IndependentExtraWorkRecord.toEntity() = IndependentExtraWorkRecordEntity(
    id = id.toString(),
    timelineId = timelineId.toString(),
    sector = sector.encodeSector(),
    configurationRevisionId = configurationRevisionId.toString(),
    workPlaceId = workPlaceId.toString(),
    objectiveId = objectiveId.toString(),
    workTypeId = workTypeId.toString(),
    templateId = templateId?.toString(),
    extraWorkClassId = extraWorkClassId.toString(),
    ownerLocalDate = ownerLocalDate.toString(),
    zoneId = zoneId.id,
    startEpochMillis = start.toEpochMilli(),
    endEpochMillis = end.toEpochMilli(),
    workPlaceNameSnapshot = snapshot.workPlaceName,
    workPlaceAbbreviationSnapshot = snapshot.workPlaceAbbreviation,
    workPlaceAddressSnapshot = snapshot.workPlaceAddress,
    workTypeNameSnapshot = snapshot.workTypeName,
    workTypeBehaviorSnapshot = snapshot.workTypeBehavior.name,
    colorArgbSnapshot = snapshot.colorArgb,
    positionSnapshot = snapshot.position,
    classNameSnapshot = snapshot.className,
    helpsMeetHoursReferenceSnapshot = snapshot.helpsMeetHoursReference,
    showDedicatedSummarySnapshot = snapshot.showDedicatedSummary,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun IndependentExtraWorkRecordEntity.toDomainIndependentExtra(): IndependentExtraWorkRecord =
    decodeIndependentExtra(id) {
        IndependentExtraWorkRecord(
            id = UUID.fromString(id),
            timelineId = UUID.fromString(timelineId),
            sector = sector.decodeSector(),
            configurationRevisionId = UUID.fromString(configurationRevisionId),
            workPlaceId = UUID.fromString(workPlaceId),
            objectiveId = UUID.fromString(objectiveId),
            workTypeId = UUID.fromString(workTypeId),
            templateId = templateId?.let(UUID::fromString),
            extraWorkClassId = UUID.fromString(extraWorkClassId),
            ownerLocalDate = LocalDate.parse(ownerLocalDate),
            zoneId = ZoneId.of(zoneId),
            start = Instant.ofEpochMilli(startEpochMillis),
            end = Instant.ofEpochMilli(endEpochMillis),
            snapshot = IndependentExtraWorkSnapshot(
                workPlaceName = workPlaceNameSnapshot,
                workPlaceAbbreviation = workPlaceAbbreviationSnapshot,
                workPlaceAddress = workPlaceAddressSnapshot,
                workTypeName = workTypeNameSnapshot,
                workTypeBehavior = WorkTypeBehavior.valueOf(workTypeBehaviorSnapshot),
                colorArgb = colorArgbSnapshot,
                position = positionSnapshot,
                className = classNameSnapshot,
                helpsMeetHoursReference = helpsMeetHoursReferenceSnapshot,
                showDedicatedSummary = showDedicatedSummarySnapshot,
            ),
            createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
        )
    }

private inline fun <T> decodeIndependentExtra(id: String, block: () -> T): T = try {
    block()
} catch (error: RuntimeException) {
    throw InvalidLocalDataException("El extra independiente $id contiene datos inválidos.", error)
}
