package com.blackatsystems.miguardia.core.database.validation

import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.InvalidVacationRangeException
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

internal fun Objective.validated(): Objective {
    val normalizedName = fullName.trim()
    val normalizedAbbreviation = abbreviation.trim().uppercase(Locale.ROOT)
    if (normalizedName.isEmpty()) invalid("El nombre del objetivo es obligatorio.")
    if (normalizedAbbreviation.length !in 2..5) {
        invalid("La abreviatura del objetivo debe tener entre 2 y 5 caracteres.")
    }
    validateTimestamps(createdAt, updatedAt)
    return copy(
        fullName = normalizedName,
        abbreviation = normalizedAbbreviation,
        address = address.normalizedOptional(),
        note = note.normalizedOptional(),
    )
}

internal fun ScheduleCombination.validated(): ScheduleCombination {
    validateTimestamps(createdAt, updatedAt)
    return this
}

internal fun Shift.validated(): Shift {
    if (!endAt.isAfter(startAt)) invalid("El fin de la guardia debe ser posterior al inicio.")
    if (localStartDate != startAt.atZone(zoneId).toLocalDate()) {
        invalid("La fecha local inicial no coincide con el instante y la zona de la guardia.")
    }
    val normalizedName = objectiveNameSnapshot.trim()
    val normalizedAbbreviation = objectiveAbbreviationSnapshot.trim().uppercase(Locale.ROOT)
    if (normalizedName.isEmpty()) invalid("El nombre histórico del objetivo es obligatorio.")
    if (normalizedAbbreviation.isEmpty()) invalid("La abreviatura histórica del objetivo es obligatoria.")
    validateTimestamps(createdAt, updatedAt)
    return copy(
        objectiveNameSnapshot = normalizedName,
        objectiveAbbreviationSnapshot = normalizedAbbreviation,
        objectiveAddressSnapshot = objectiveAddressSnapshot.normalizedOptional(),
        position = position.normalizedOptional(),
    )
}

internal fun MedicalLeave.validated(): MedicalLeave {
    if (endDateInclusive.isBefore(startDate)) {
        invalid("La fecha final de la carpeta médica no puede ser anterior a la inicial.")
    }
    validateTimestamps(createdAt, updatedAt)
    return copy(privateNote = privateNote.normalizedOptional())
}

internal fun Vacation.validated(): Vacation {
    if (endDateInclusive.isBefore(startDate)) {
        throw InvalidVacationRangeException()
    }
    validateTimestamps(createdAt, updatedAt)
    return this
}

internal fun validateRange(startDateInclusive: LocalDate, endDateInclusive: LocalDate) {
    if (endDateInclusive.isBefore(startDateInclusive)) {
        invalid("El fin del intervalo no puede ser anterior al inicio.")
    }
}

internal fun validateUpdateTimestamp(createdAt: Instant, updatedAt: Instant) {
    if (updatedAt.isBefore(createdAt)) {
        invalid("La última modificación no puede ser anterior a la creación.")
    }
}

private fun validateTimestamps(createdAt: Instant, updatedAt: Instant) =
    validateUpdateTimestamp(createdAt, updatedAt)

private fun String?.normalizedOptional(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun invalid(message: String): Nothing = throw InvalidLocalDataException(message)
