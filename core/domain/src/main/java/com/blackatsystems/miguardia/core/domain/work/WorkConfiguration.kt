package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.repository.InvalidV2SelectionException
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
    val hoursReferenceStartedOn: LocalDate? = null,
) {
    init {
        require(hoursReferenceStartedOn != null == hoursReference.requiresStartedOnMarker) {
            "El inicio de la referencia debe existir exactamente cuando hay un período que contar"
        }
    }
}

val HoursReference.requiresStartedOnMarker: Boolean
    get() = when (this) {
        HoursReference.PendingSetup,
        HoursReference.NotUsed,
        -> false

        is HoursReference.Unknown -> period != null
        is HoursReference.Fixed,
        is HoursReference.PerPeriod,
        -> true
    }

data class EffectiveRevision<T>(
    val id: UUID,
    val effectiveFrom: LocalDate,
    val value: T,
)

data class WorkConfigurationReferenceMutation(
    val expectedHistory: WorkConfigurationHistory,
    val revision: EffectiveRevision<WorkConfiguration>,
    val initialPerPeriodValue: PerPeriodHoursEntry? = null,
) {
    init {
        val previousAtStart = requireNotNull(
            expectedHistory.timeline.revisionAt(revision.effectiveFrom),
        ) { "La referencia no puede comenzar antes de la configuración laboral" }
        require(
            revision.value.sector == previousAtStart.value.sector &&
                revision.value.availabilityLabel == previousAtStart.value.availabilityLabel,
        ) {
            "Cambiar la referencia no puede modificar el rubro ni la disponibilidad"
        }
        require(
            !revision.value.hoursReference.requiresStartedOnMarker ||
                revision.effectiveFrom == revision.value.hoursReferenceStartedOn,
        ) {
            "Una nueva referencia debe comenzar en la fecha elegida para el reinicio"
        }
        val perPeriod = revision.value.hoursReference as? HoursReference.PerPeriod
        initialPerPeriodValue?.let { entry ->
            requireNotNull(perPeriod) {
                "Sólo una referencia por período admite un valor inicial"
            }
            require(entry.key.definitionId == perPeriod.definitionId && entry.key.period == perPeriod.period) {
                "El valor inicial no pertenece a la nueva referencia"
            }
            require(revision.effectiveFrom in entry.key.window) {
                "El valor inicial debe corresponder al primer período de la referencia"
            }
        }
    }
}

data class WorkConfigurationAvailabilityMutation(
    val expectedHistory: WorkConfigurationHistory,
    val revision: EffectiveRevision<WorkConfiguration>,
) {
    init {
        val previousAtStart = requireNotNull(
            expectedHistory.timeline.revisionAt(revision.effectiveFrom),
        ) { "La disponibilidad no puede comenzar antes de la configuración laboral" }
        require(
            revision.value.sector == previousAtStart.value.sector &&
                revision.value.hoursReference == previousAtStart.value.hoursReference &&
                revision.value.hoursReferenceStartedOn == previousAtStart.value.hoursReferenceStartedOn,
        ) {
            "Cambiar la disponibilidad no puede modificar el rubro ni la referencia de horas"
        }
    }
}

data class PerPeriodHoursValueMutation(
    val expectedHistory: WorkConfigurationHistory,
    val replacement: PerPeriodHoursEntry,
) {
    init {
        val matchingReferences = expectedHistory.timeline.revisions
            .asSequence()
            .mapNotNull { it.value.hoursReference as? HoursReference.PerPeriod }
            .filter { it.definitionId == replacement.key.definitionId }
            .toList()
        require(matchingReferences.isNotEmpty()) {
            "El valor debe pertenecer a una definición vigente del historial"
        }
        require(matchingReferences.all { it.period == replacement.key.period }) {
            "El valor debe conservar el período de su definición"
        }
    }
}

sealed interface PerPeriodHoursValueWriteResult {
    data class Saved(val history: WorkConfigurationHistory) : PerPeriodHoursValueWriteResult
    data object Conflict : PerPeriodHoursValueWriteResult
}

sealed interface WorkConfigurationReferenceWriteResult {
    data class Saved(val history: WorkConfigurationHistory) : WorkConfigurationReferenceWriteResult
    data object Conflict : WorkConfigurationReferenceWriteResult
}

sealed interface WorkConfigurationAvailabilityWriteResult {
    data class Saved(val history: WorkConfigurationHistory) : WorkConfigurationAvailabilityWriteResult
    data object Conflict : WorkConfigurationAvailabilityWriteResult
}

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

    fun revisionAt(date: LocalDate): EffectiveRevision<T>? = revisions
        .lastOrNull { revision -> !revision.effectiveFrom.isAfter(date) }

    fun valueAt(date: LocalDate): T? = revisionAt(date)?.value
}

/**
 * Demuestra que [revision] es la revisión exacta de [timelineId] resuelta para
 * [referenceDate]. El constructor privado impide asociar una revisión anterior
 * del mismo sector con una fecha más nueva.
 */
class ResolvedWorkConfigurationRevision private constructor(
    val timelineId: UUID,
    val referenceDate: LocalDate,
    val revision: EffectiveRevision<WorkConfiguration>,
) {
    companion object {
        fun resolve(
            history: WorkConfigurationHistory,
            date: LocalDate,
        ): ResolvedWorkConfigurationRevision {
            val revision = history.timeline.revisionAt(date)
                ?: throw InvalidV2SelectionException(
                    "MiGuardia 2.0 todavia no esta configurada para el $date.",
                )
            return ResolvedWorkConfigurationRevision(
                timelineId = history.timeline.id,
                referenceDate = date,
                revision = revision,
            )
        }
    }
}
