package com.blackatsystems.miguardia.ui.worksetup

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.normalizeNewWorkPlaceAbbreviation
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.UUID

enum class WorkSetupSurface {
    NONE,
    OVERVIEW,
    FIRST_WORK_SET,
    ADDITIONAL_PLACE,
    ADDITIONAL_TEMPLATE,
    COMPLETION,
}

enum class WorkSetupStep {
    PLACE_AND_RULES,
    TYPE_AND_TEMPLATE,
}

data class WorkPlaceDraft(
    val name: String = "",
    val abbreviation: String = "",
    val address: String = "",
    val note: String = "",
    val nightHoursEnabled: Boolean = false,
    val nightStart: String = "",
    val nightEnd: String = "",
    val classifySaturday: Boolean = false,
    val classifySunday: Boolean = false,
    val showWeekendSummary: Boolean = false,
    val classifyHoliday: Boolean = false,
    val showHolidaySummary: Boolean = false,
)

data class WorkTemplateDraft(
    val typeName: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val colorArgb: Int? = null,
)

data class WorkPlaceOption(
    val id: UUID,
    val label: String,
    val abbreviation: String,
)

data class WorkTypeOption(
    val id: UUID,
    val label: String,
)

data class WorkSetupUiState(
    val rootState: WorkSetupState = WorkSetupState.Loading,
    val selectedSector: WorkSector? = null,
    val isSavingSector: Boolean = false,
    val surface: WorkSetupSurface = WorkSetupSurface.NONE,
    val step: WorkSetupStep = WorkSetupStep.PLACE_AND_RULES,
    val placeDraft: WorkPlaceDraft = WorkPlaceDraft(),
    val templateDraft: WorkTemplateDraft = WorkTemplateDraft(),
    val catalog: WorkCatalog? = null,
    val objectivesById: Map<UUID, Objective> = emptyMap(),
    val selectedTemplatePlaceId: UUID? = null,
    val selectedTemplateTypeId: UUID? = null,
    val lastCreatedPlaceId: UUID? = null,
    val lastCreatedTypeId: UUID? = null,
    val isSavingWorkSet: Boolean = false,
    val isSavingTemplate: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val sectorOptions: List<WorkSector>
        get() = WORK_SECTOR_OPTIONS

    val canContinueSector: Boolean
        get() = selectedSector != null && !isSavingSector

    val activePlaceOptions: List<WorkPlaceOption>
        get() = catalog
            ?.workPlaces
            ?.asSequence()
            ?.filter { it.isActive }
            ?.mapNotNull { place ->
                objectivesById[place.objectiveId]?.let { objective ->
                    WorkPlaceOption(
                        id = place.id,
                        label = objective.fullName,
                        abbreviation = objective.abbreviation,
                    )
                }
            }
            ?.sortedBy { it.label.lowercase(Locale.ROOT) }
            ?.toList()
            .orEmpty()

    val activeTypeOptions: List<WorkTypeOption>
        get() = catalog
            ?.workTypes
            ?.asSequence()
            ?.filter { it.isActive }
            ?.map { WorkTypeOption(id = it.id, label = it.name) }
            ?.sortedBy { it.label.lowercase(Locale.ROOT) }
            ?.toList()
            .orEmpty()

    val hasUnconfirmedDraft: Boolean
        get() = when (surface) {
            WorkSetupSurface.FIRST_WORK_SET ->
                placeDraft != WorkPlaceDraft() ||
                    templateDraft != WorkTemplateDraft(
                        typeName = selectedSector?.suggestedRegularTypeName().orEmpty(),
                    )

            WorkSetupSurface.ADDITIONAL_PLACE -> placeDraft != WorkPlaceDraft()

            WorkSetupSurface.ADDITIONAL_TEMPLATE ->
                templateDraft.startTime.isNotBlank() ||
                    templateDraft.endTime.isNotBlank() ||
                    templateDraft.colorArgb != null

            else -> false
        }
}

data class WorkSetupPersistedState(
    val selectedSector: WorkSector? = null,
    val surface: WorkSetupSurface = WorkSetupSurface.NONE,
    val step: WorkSetupStep = WorkSetupStep.PLACE_AND_RULES,
    val placeDraft: WorkPlaceDraft = WorkPlaceDraft(),
    val templateDraft: WorkTemplateDraft = WorkTemplateDraft(),
    val selectedTemplatePlaceId: UUID? = null,
    val selectedTemplateTypeId: UUID? = null,
    val lastCreatedPlaceId: UUID? = null,
    val lastCreatedTypeId: UUID? = null,
)

internal data class WorkSetupDraftValidation(
    val message: String? = null,
) {
    val isValid: Boolean
        get() = message == null
}

internal fun validatePlaceDraft(draft: WorkPlaceDraft): WorkSetupDraftValidation {
    if (draft.name.isBlank()) return WorkSetupDraftValidation("Ingresá el nombre del lugar.")
    val abbreviationIsValid = try {
        normalizeNewWorkPlaceAbbreviation(draft.abbreviation)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
    if (!abbreviationIsValid) {
        return WorkSetupDraftValidation("El nombre corto debe tener entre tres y cinco caracteres, sin espacios.")
    }
    if (draft.nightHoursEnabled) {
        val start = parseWorkTimeOrNull(draft.nightStart)
            ?: return WorkSetupDraftValidation("Ingresá el inicio nocturno con formato HH:mm.")
        val end = parseWorkTimeOrNull(draft.nightEnd)
            ?: return WorkSetupDraftValidation("Ingresá el final nocturno con formato HH:mm.")
        if (start == end) {
            return WorkSetupDraftValidation("El inicio y el final de la franja nocturna deben ser distintos.")
        }
    }
    return WorkSetupDraftValidation()
}

internal fun validateTemplateDraft(
    draft: WorkTemplateDraft,
    requireTypeName: Boolean,
): WorkSetupDraftValidation {
    if (requireTypeName && draft.typeName.isBlank()) {
        return WorkSetupDraftValidation("Ingresá el nombre del tipo de trabajo.")
    }
    if (parseWorkTimeOrNull(draft.startTime) == null) {
        return WorkSetupDraftValidation("Ingresá la hora de inicio con formato HH:mm.")
    }
    if (parseWorkTimeOrNull(draft.endTime) == null) {
        return WorkSetupDraftValidation("Ingresá la hora de finalización con formato HH:mm.")
    }
    if (draft.colorArgb == null) return WorkSetupDraftValidation("Elegí un color para el horario.")
    return WorkSetupDraftValidation()
}

internal fun parseWorkTimeOrNull(rawValue: String): LocalTime? = try {
    LocalTime.parse(rawValue.trim(), WORK_TIME_FORMATTER)
} catch (_: DateTimeParseException) {
    null
}

internal fun WorkSector.suggestedRegularTypeName(): String =
    "${suggestedVocabulary.shiftLabel} habitual"

internal val WORK_SECTOR_OPTIONS: List<WorkSector> = listOf(
    WorkSector.PRIVATE_SECURITY,
    WorkSector.POLICE,
    WorkSector.NURSING,
    WorkSector.MEDICINE,
)

fun previewV2WorkSetupUiState(): WorkSetupUiState {
    val timelineId = UUID(0L, 0L)
    val sector = WorkSector.PRIVATE_SECURITY
    return WorkSetupUiState(
        rootState = WorkSetupState.V2Ready(
            timelineId = timelineId,
            configurationRevision = EffectiveRevision(
                id = UUID(0L, 1L),
                effectiveFrom = LocalDate.of(1970, 1, 1),
                value = WorkConfiguration(sector, HoursReference.PendingSetup, null),
            ),
        ),
        selectedSector = sector,
        catalog = WorkCatalog(timelineId, sector, emptyList(), emptyList(), emptyList(), emptyList()),
    )
}

private val WORK_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("HH:mm")
    .withResolverStyle(ResolverStyle.STRICT)
