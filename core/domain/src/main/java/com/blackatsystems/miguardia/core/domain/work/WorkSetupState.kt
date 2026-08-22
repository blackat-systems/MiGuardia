package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.repository.InvalidV2SelectionException
import java.time.LocalDate
import java.util.UUID

enum class MissingWorkSetupRequirement {
    ACTIVE_WORK_PLACE,
    APPLICABLE_WORKPLACE_RULE,
    ACTIVE_WORK_TYPE,
    ACTIVE_WORK_TEMPLATE,
}

sealed interface WorkSetupState {
    data object Loading : WorkSetupState
    data object LoadError : WorkSetupState
    data object FreshInstall : WorkSetupState

    data class LegacyV1(
        val timelineId: UUID,
    ) : WorkSetupState

    data class LegacyV1WithFutureActivation(
        val timelineId: UUID,
        val activationRevision: EffectiveRevision<WorkConfiguration>,
    ) : WorkSetupState

    data class V2NeedsFirstSet(
        val timelineId: UUID,
        val configurationRevision: EffectiveRevision<WorkConfiguration>,
        val missing: Set<MissingWorkSetupRequirement>,
    ) : WorkSetupState

    data class V2Ready(
        val timelineId: UUID,
        val configurationRevision: EffectiveRevision<WorkConfiguration>,
    ) : WorkSetupState
}

fun projectLoadedWorkSetupState(
    history: WorkConfigurationHistory?,
    catalog: WorkCatalog?,
    referenceDate: LocalDate,
): WorkSetupState {
    if (history == null) return WorkSetupState.FreshInstall
    val applicableRevision = history.timeline.revisionAt(referenceDate)
    if (applicableRevision == null) {
        return when (history.origin) {
            WorkConfigurationOrigin.MIGRATED_V1 -> history.timeline.revisions
                .firstOrNull { it.effectiveFrom.isAfter(referenceDate) }
                ?.let { future ->
                    WorkSetupState.LegacyV1WithFutureActivation(
                        timelineId = history.timeline.id,
                        activationRevision = future,
                    )
                }
                ?: WorkSetupState.LegacyV1(history.timeline.id)

            WorkConfigurationOrigin.NEW_V2 -> WorkSetupState.LoadError
        }
    }
    if (
        catalog == null ||
        catalog.timelineId != history.timeline.id ||
        catalog.sector != applicableRevision.value.sector
    ) {
        return WorkSetupState.LoadError
    }

    val activePlaces = catalog.workPlaces.filter(WorkPlace::isActive)
    val activeTypes = catalog.workTypes.filter(WorkType::isActive)
    val placesWithApplicableRules = activePlaces.filter { place ->
        catalog.ruleRevisionAt(place.id, referenceDate) != null
    }
    val selectableTemplateExists = catalog.workTemplates.any { template ->
        template.isActive &&
            placesWithApplicableRules.any { it.id == template.workPlaceId } &&
            activeTypes.any { it.id == template.workTypeId }
    }
    val missing = buildSet {
        if (activePlaces.isEmpty()) add(MissingWorkSetupRequirement.ACTIVE_WORK_PLACE)
        if (placesWithApplicableRules.isEmpty()) add(MissingWorkSetupRequirement.APPLICABLE_WORKPLACE_RULE)
        if (activeTypes.isEmpty()) add(MissingWorkSetupRequirement.ACTIVE_WORK_TYPE)
        if (!selectableTemplateExists) add(MissingWorkSetupRequirement.ACTIVE_WORK_TEMPLATE)
    }
    return if (missing.isEmpty()) {
        WorkSetupState.V2Ready(history.timeline.id, applicableRevision)
    } else {
        WorkSetupState.V2NeedsFirstSet(history.timeline.id, applicableRevision, missing)
    }
}

sealed interface WorkDateSelection {
    val dates: Set<LocalDate>

    data class LegacyV1(
        override val dates: Set<LocalDate>,
    ) : WorkDateSelection

    data class V2(
        override val dates: Set<LocalDate>,
        val sector: WorkSector,
        val configurationRevisionsByDate: Map<LocalDate, EffectiveRevision<WorkConfiguration>>,
    ) : WorkDateSelection

    data class NeedsNewV2Backfill(
        override val dates: Set<LocalDate>,
        val earliestDate: LocalDate,
        val sector: WorkSector,
        val configuredRevisionsByDate: Map<LocalDate, EffectiveRevision<WorkConfiguration>>,
    ) : WorkDateSelection
}

fun classifyWorkDateSelection(
    history: WorkConfigurationHistory,
    selectedDates: Set<LocalDate>,
): WorkDateSelection {
    if (selectedDates.isEmpty()) throw InvalidV2SelectionException("Elegi al menos una fecha.")
    val orderedDates = selectedDates.sorted()
    val revisionsByDate = orderedDates.associateWith(history.timeline::revisionAt)
    val configured = revisionsByDate.mapNotNull { (date, revision) ->
        revision?.let { date to it }
    }.toMap()
    val unconfiguredDates = revisionsByDate.filterValues { it == null }.keys

    if (history.origin == WorkConfigurationOrigin.MIGRATED_V1) {
        if (configured.isEmpty()) return WorkDateSelection.LegacyV1(selectedDates.toSet())
        if (unconfiguredDates.isNotEmpty()) {
            throw InvalidV2SelectionException(
                "La seleccion mezcla fechas de MiGuardia 1.0 y 2.0. Cargalas por separado.",
            )
        }
    } else if (unconfiguredDates.isNotEmpty()) {
        val firstRevision = history.timeline.revisions.first()
        val sectors = configured.values.map { it.value.sector }.toSet() + firstRevision.value.sector
        if (sectors.size != 1) {
            throw InvalidV2SelectionException(
                "La seleccion atraviesa formas de trabajar diferentes. Cargalas por separado.",
            )
        }
        return WorkDateSelection.NeedsNewV2Backfill(
            dates = selectedDates.toSet(),
            earliestDate = unconfiguredDates.minOrNull()!!,
            sector = sectors.single(),
            configuredRevisionsByDate = configured,
        )
    }

    val sectors = configured.values.map { it.value.sector }.distinct()
    if (sectors.size != 1) {
        throw InvalidV2SelectionException(
            "La seleccion mezcla sectores. Cargalos en operaciones separadas.",
        )
    }
    return WorkDateSelection.V2(
        dates = selectedDates.toSet(),
        sector = sectors.single(),
        configurationRevisionsByDate = configured,
    )
}
