package com.blackatsystems.miguardia.core.domain.work

import java.time.LocalDate
import java.util.Collections
import java.util.UUID

data class SuggestedWorkVocabulary(
    val placeLabel: String,
    val shiftLabel: String,
) {
    init {
        require(placeLabel.isNotBlank()) { "La etiqueta sugerida del lugar no puede estar vacía" }
        require(shiftLabel.isNotBlank()) { "La etiqueta sugerida de la jornada no puede estar vacía" }
    }
}

enum class WorkSector(
    val displayName: String,
    val suggestedVocabulary: SuggestedWorkVocabulary,
) {
    PRIVATE_SECURITY(
        displayName = "Vigilancia privada",
        suggestedVocabulary = SuggestedWorkVocabulary(
            placeLabel = "Objetivo",
            shiftLabel = "Guardia",
        ),
    ),
    POLICE(
        displayName = "Policía",
        suggestedVocabulary = SuggestedWorkVocabulary(
            placeLabel = "Dependencia o lugar de servicio",
            shiftLabel = "Guardia",
        ),
    ),
    NURSING(
        displayName = "Enfermería",
        suggestedVocabulary = SuggestedWorkVocabulary(
            placeLabel = "Institución o servicio",
            shiftLabel = "Turno",
        ),
    ),
    MEDICINE(
        displayName = "Medicina",
        suggestedVocabulary = SuggestedWorkVocabulary(
            placeLabel = "Hospital, clínica, consultorio o servicio",
            shiftLabel = "Jornada",
        ),
    ),
}

data class WorkConfiguration(
    val sector: WorkSector,
    val hoursReference: HoursReference,
    val availabilityLabel: AvailabilityLabel?,
)

data class EffectiveRevision<T>(
    val id: UUID,
    val effectiveFrom: LocalDate,
    val value: T,
)

class EffectiveDateTimeline<T>(
    val id: UUID,
    revisions: Iterable<EffectiveRevision<T>>,
) {
    val revisions: List<EffectiveRevision<T>> = Collections.unmodifiableList(
        revisions
            .toList()
            .sortedWith(compareBy<EffectiveRevision<T>> { it.effectiveFrom }.thenBy { it.id })
            .also { ordered ->
                require(ordered.map { it.id }.distinct().size == ordered.size) {
                    "No puede haber dos revisiones con el mismo identificador"
                }
                require(ordered.zipWithNext().none { (first, second) ->
                    first.effectiveFrom == second.effectiveFrom
                }) {
                    "No puede haber dos revisiones vigentes desde la misma fecha"
                }
            },
    )

    fun valueAt(date: LocalDate): T? = revisions
        .lastOrNull { revision -> !revision.effectiveFrom.isAfter(date) }
        ?.value
}
