package com.blackatsystems.miguardia.core.domain.photo

import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.util.UUID

private val storageKeyPattern = Regex(
    "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})" +
        "(?:_[0-9a-fA-F]{8})?\\.[a-z0-9]{2,8}",
)

fun SchedulePhoto.validated(): SchedulePhoto {
    val storageKeyMatch = storageKeyPattern.matchEntire(storageKey)
    if (storageKeyMatch == null || runCatching { UUID.fromString(storageKeyMatch.groupValues[1]) }.isFailure) {
        throw InvalidLocalDataException("La referencia interna de la foto no es válida.")
    }
    if (!mimeType.startsWith("image/") || byteSize <= 0 || pixelWidth <= 0 || pixelHeight <= 0) {
        throw InvalidLocalDataException("Los datos de la foto no son válidos.")
    }
    val hasObjective = objectiveId != null
    val hasSnapshots = !objectiveNameSnapshot.isNullOrBlank() &&
        !objectiveAbbreviationSnapshot.isNullOrBlank()
    if (hasObjective != hasSnapshots) {
        throw InvalidLocalDataException("La asociación de objetivo de la foto está incompleta.")
    }
    if (updatedAt.isBefore(createdAt)) {
        throw InvalidLocalDataException("La actualización de la foto no puede preceder a su creación.")
    }
    return this
}
