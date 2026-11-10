package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.database.entity.ShiftSituationEntity
import com.blackatsystems.miguardia.core.domain.model.CancellationActor
import com.blackatsystems.miguardia.core.domain.model.CancellationEvidence
import com.blackatsystems.miguardia.core.domain.model.ShiftSituation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Instant
import java.util.UUID

internal fun ShiftSituation.toEntity() = ShiftSituationEntity(
    shiftId = shiftId.toString(),
    status = status.name,
    reason = reason,
    privateNote = privateNote,
    cancellationActor = cancellationActor?.name,
    notifiedAtEpochMillis = notifiedAt?.toEpochMilli(),
    evidenceStorageKey = evidence?.storageKey,
    evidenceMimeType = evidence?.mimeType,
    evidenceByteSize = evidence?.byteSize,
    evidencePixelWidth = evidence?.pixelWidth,
    evidencePixelHeight = evidence?.pixelHeight,
    evidenceSha256 = evidence?.sha256,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ShiftSituationEntity.toDomainSituation(): ShiftSituation = try {
    val evidenceFields = listOf(evidenceStorageKey, evidenceMimeType, evidenceByteSize, evidencePixelWidth, evidencePixelHeight, evidenceSha256)
    require(evidenceFields.all { it == null } || evidenceFields.all { it != null }) {
        "La evidencia debe conservar metadatos completos"
    }
    ShiftSituation(
        shiftId = UUID.fromString(shiftId),
        status = ShiftStatus.valueOf(status),
        reason = reason,
        privateNote = privateNote,
        cancellationActor = cancellationActor?.let(CancellationActor::valueOf),
        notifiedAt = notifiedAtEpochMillis?.let(Instant::ofEpochMilli),
        evidence = evidenceStorageKey?.let {
            CancellationEvidence(
                storageKey = it,
                mimeType = requireNotNull(evidenceMimeType),
                byteSize = requireNotNull(evidenceByteSize),
                pixelWidth = requireNotNull(evidencePixelWidth),
                pixelHeight = requireNotNull(evidencePixelHeight),
                sha256 = requireNotNull(evidenceSha256),
            )
        },
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
} catch (error: IllegalArgumentException) {
    throw InvalidLocalDataException("La situación de la jornada $shiftId contiene datos inválidos.", error)
}
