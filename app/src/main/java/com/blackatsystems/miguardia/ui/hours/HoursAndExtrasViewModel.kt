package com.blackatsystems.miguardia.ui.hours

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkDraft
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkExpectation
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkMutation
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSelection
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkWriteResult
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.buildIndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.resolveActualLocalDateTime
import com.blackatsystems.miguardia.core.domain.repository.IndependentExtraWorkRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursProgress
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.HoursReferenceSegment
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursEntry
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursLookup
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValueMutation
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValueWriteResult
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationReferenceMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationReferenceWriteResult
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.WorkProtectionPeriod
import com.blackatsystems.miguardia.core.domain.work.WorkedShiftSource
import com.blackatsystems.miguardia.core.domain.work.calculateHoursProgress
import com.blackatsystems.miguardia.core.domain.work.requiresStartedOnMarker
import com.blackatsystems.miguardia.core.domain.work.resolveHoursReferenceSegment
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class HoursAndExtrasSurface {
    NONE,
    PROGRESS,
    REFERENCE_EDITOR,
    PERIOD_VALUE_EDITOR,
    EXTRA_EDITOR,
    DELETE_CONFIRMATION,
}

enum class HoursAndExtrasLoadState {
    LOADING,
    CONTENT,
    ERROR,
}

enum class HoursReferenceChoice {
    PENDING,
    NOT_USED,
    UNKNOWN,
    FIXED,
    PER_PERIOD,
}

enum class HoursPeriodChoice {
    NONE,
    MONTHLY,
    WEEKLY,
    CYCLE,
}

enum class ReferenceStartChoice {
    TODAY,
    NEXT_PERIOD,
    CUSTOM,
}

enum class EditorStage {
    EDIT,
    REVIEW,
}

data class HoursReferenceDraft(
    val choice: HoursReferenceChoice = HoursReferenceChoice.PENDING,
    val periodChoice: HoursPeriodChoice = HoursPeriodChoice.NONE,
    val weeklyFirstDay: DayOfWeek = HoursPeriod.Weekly.suggestedFirstDay,
    val cycleLengthDays: String = "",
    val cycleAnchorDate: String = "",
    val requiredMinutes: String = "",
    val initialPerPeriodMinutes: String = "",
    val startChoice: ReferenceStartChoice = ReferenceStartChoice.TODAY,
    val customStartDate: String = "",
    val confirmPastRecalculation: Boolean = false,
    val confirmShortFirstSegment: Boolean = false,
    val stage: EditorStage = EditorStage.EDIT,
    val revisionId: UUID = UUID.randomUUID(),
    val definitionId: UUID = UUID.randomUUID(),
    val valueId: UUID = UUID.randomUUID(),
    val expectedFingerprint: String? = null,
)

data class HoursReferenceReview(
    val reference: HoursReference,
    val startedOn: LocalDate,
    val previousSegmentEndInclusive: LocalDate?,
    val naturalWindowStart: LocalDate?,
    val naturalWindowEndExclusive: LocalDate?,
    val recalculationEndExclusive: LocalDate,
    val isPast: Boolean,
    val isShortFirstSegment: Boolean,
    val initialValue: PerPeriodHoursEntry?,
)

data class PerPeriodValueDraft(
    val requiredMinutes: String = "",
    val confirmCorrection: Boolean = false,
    val valueId: UUID = UUID.randomUUID(),
    val expectedFingerprint: String? = null,
)

data class IndependentExtraDraftState(
    val recordId: UUID = UUID.randomUUID(),
    val ownerDate: LocalDate? = null,
    val startTime: String = "",
    val endDate: String = "",
    val endTime: String = "",
    val workPlaceId: UUID? = null,
    val workTypeId: UUID? = null,
    val templateId: UUID? = null,
    val extraClassId: UUID? = null,
    val colorArgb: Int? = null,
    val position: String = "",
    val stage: EditorStage = EditorStage.EDIT,
    val overlapConfirmed: Boolean = false,
    val protectionConfirmed: Boolean = false,
    val expectedFingerprint: String? = null,
    val openedRecordFingerprint: String? = null,
)

data class IndependentExtraReview(
    val record: IndependentExtraWorkRecord,
    val expectation: IndependentExtraWorkExpectation,
    val hasOverlap: Boolean,
    val hasProtectedDates: Boolean,
)

data class HoursAndExtrasSource(
    val history: WorkConfigurationHistory,
    val catalog: WorkCatalog,
    val objectives: List<Objective>,
    val extraClasses: List<ExtraWorkClass>,
    val independentExtras: List<IndependentExtraWorkRecord>,
    val segment: HoursReferenceSegment,
    val progress: HoursProgress,
    val today: LocalDate,
)

data class HoursAndExtrasUiState(
    val loadState: HoursAndExtrasLoadState = HoursAndExtrasLoadState.LOADING,
    val source: HoursAndExtrasSource? = null,
    val surface: HoursAndExtrasSurface = HoursAndExtrasSurface.NONE,
    val referenceDraft: HoursReferenceDraft? = null,
    val referenceReview: HoursReferenceReview? = null,
    val periodValueDraft: PerPeriodValueDraft? = null,
    val extraDraft: IndependentExtraDraftState? = null,
    val extraReview: IndependentExtraReview? = null,
    val deletingRecord: IndependentExtraWorkRecord? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
    val successSequence: Int = 0,
) {
    val isBlocking: Boolean
        get() = surface != HoursAndExtrasSurface.NONE

    val canRegisterIndependentExtra: Boolean
        get() = loadState == HoursAndExtrasLoadState.CONTENT && source?.let { data ->
            data.catalog.workPlaces.any { place ->
                place.isActive && data.objectives.any { it.id == place.objectiveId && it.isActive }
            } &&
                data.catalog.workTypes.any { it.isActive && it.behavior == WorkTypeBehavior.ACTIVE_WORK } &&
                data.extraClasses.any { it.isActive }
        } == true

    fun canRegisterIndependentExtraOn(date: LocalDate): Boolean =
        canRegisterIndependentExtra && source?.let { data ->
            !date.isAfter(data.today) && data.history.timeline.revisionAt(date) != null
        } == true

    fun extrasOn(date: LocalDate): List<IndependentExtraWorkRecord> = source
        ?.independentExtras
        ?.filter { it.ownerLocalDate == date }
        ?.sortedWith(compareBy(IndependentExtraWorkRecord::start, IndependentExtraWorkRecord::id))
        .orEmpty()
}

class HoursAndExtrasViewModel(
    private val configurationRepository: WorkConfigurationRepository,
    private val catalogRepository: WorkCatalogRepository,
    private val objectiveRepository: ObjectiveRepository,
    private val shiftRepository: V2ShiftRepository,
    private val shiftActualRepository: ShiftActualRepository,
    private val independentExtraRepository: IndependentExtraWorkRepository,
    private val medicalLeaveRepository: MedicalLeaveRepository,
    private val vacationRepository: VacationRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val uuidProvider: () -> UUID,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HoursAndExtrasUiState(
            surface = savedStateHandle.readSurface(),
            referenceDraft = savedStateHandle.readReferenceDraft(),
            periodValueDraft = savedStateHandle.readPeriodValueDraft(),
            extraDraft = savedStateHandle.readExtraDraft(),
        ),
    )
    val uiState: StateFlow<HoursAndExtrasUiState> = _uiState
    private var observationJob: Job? = null
    private var preparedExtraReview: IndependentExtraReview? = null

    init {
        startObservation()
    }

    fun refresh() = startObservation()

    fun retry() = startObservation()

    fun openProgress() {
        _uiState.value = _uiState.value.copy(
            surface = HoursAndExtrasSurface.PROGRESS,
            message = null,
        )
        savedStateHandle.writeSurface(HoursAndExtrasSurface.PROGRESS)
    }

    fun close() {
        if (_uiState.value.isSaving) return
        preparedExtraReview = null
        _uiState.value = _uiState.value.copy(
            surface = HoursAndExtrasSurface.NONE,
            referenceDraft = null,
            referenceReview = null,
            periodValueDraft = null,
            extraDraft = null,
            extraReview = null,
            deletingRecord = null,
            message = null,
        )
        savedStateHandle.clearEditors()
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun openReferenceEditor() {
        if (_uiState.value.isSaving) return
        val source = _uiState.value.source ?: return
        val current = source.segment.ownerRevision.value.hoursReference
        val draft = HoursReferenceDraft(
            choice = current.toChoice(),
            periodChoice = current.periodOrNull().toChoice(),
            weeklyFirstDay = (current.periodOrNull() as? HoursPeriod.Weekly)?.firstDay
                ?: HoursPeriod.Weekly.suggestedFirstDay,
            cycleLengthDays = (current.periodOrNull() as? HoursPeriod.Cycle)?.lengthDays?.toString().orEmpty(),
            cycleAnchorDate = (current.periodOrNull() as? HoursPeriod.Cycle)?.anchorDate?.toString().orEmpty(),
            requiredMinutes = (current as? HoursReference.Fixed)?.requiredMinutes?.value?.toString().orEmpty(),
            startChoice = ReferenceStartChoice.TODAY,
            customStartDate = source.today.toString(),
            revisionId = uuidProvider(),
            definitionId = uuidProvider(),
            valueId = uuidProvider(),
        )
        _uiState.value = _uiState.value.copy(referenceDraft = draft)
        savedStateHandle.writeReferenceDraft(draft)
        _uiState.value = _uiState.value.copy(
            surface = HoursAndExtrasSurface.REFERENCE_EDITOR,
            referenceReview = null,
            message = null,
        )
        savedStateHandle.writeSurface(HoursAndExtrasSurface.REFERENCE_EDITOR)
    }

    fun updateReferenceDraft(transform: (HoursReferenceDraft) -> HoursReferenceDraft) {
        if (_uiState.value.isSaving) return
        val current = _uiState.value.referenceDraft ?: return
        val transformed = transform(current)
        val decisionsChanged = current.decisionFingerprint() != transformed.decisionFingerprint()
        val stageChanged = transformed.stage != current.stage
        val invalidatesReview = decisionsChanged || stageChanged
        val updated = transformed.copy(
            stage = if (decisionsChanged) EditorStage.EDIT else transformed.stage,
            confirmPastRecalculation = if (decisionsChanged) false else transformed.confirmPastRecalculation,
            confirmShortFirstSegment = if (decisionsChanged) false else transformed.confirmShortFirstSegment,
            expectedFingerprint = if (invalidatesReview) null else transformed.expectedFingerprint,
        )
        _uiState.value = _uiState.value.copy(
            referenceDraft = updated,
            referenceReview = if (invalidatesReview) null else _uiState.value.referenceReview,
            message = null,
        )
        savedStateHandle.writeReferenceDraft(updated)
    }

    fun reviewReference() {
        if (_uiState.value.isSaving) return
        val source = _uiState.value.source ?: return
        val draft = _uiState.value.referenceDraft ?: return
        val review = runCatching { buildReferenceReview(source, draft) }
            .getOrElse { error ->
                _uiState.value = _uiState.value.copy(message = error.userMessage())
                return
            }
        val reviewed = draft.copy(
            stage = EditorStage.REVIEW,
            expectedFingerprint = source.referenceExpectationFingerprint(review),
        )
        _uiState.value = _uiState.value.copy(
            referenceDraft = reviewed,
            referenceReview = review,
            message = null,
        )
        savedStateHandle.writeReferenceDraft(reviewed)
    }

    fun backReference() {
        if (_uiState.value.isSaving) return
        val draft = _uiState.value.referenceDraft ?: return
        if (draft.stage == EditorStage.REVIEW) {
            updateReferenceDraft { it.copy(stage = EditorStage.EDIT) }
        } else {
            openProgress()
        }
    }

    fun saveReference() {
        if (_uiState.value.isSaving) return
        val source = _uiState.value.source ?: return
        val draft = _uiState.value.referenceDraft ?: return
        val review = runCatching { buildReferenceReview(source, draft) }
            .getOrElse { error ->
                _uiState.value = _uiState.value.copy(message = error.userMessage())
                return
            }
        val expectedFingerprint = draft.expectedFingerprint
        if (expectedFingerprint == null || source.referenceExpectationFingerprint(review) != expectedFingerprint) {
            _uiState.value = _uiState.value.copy(
                message = "La configuración o la fecha cambió desde la revisión. Revisá nuevamente antes de guardar.",
            )
            return
        }
        if (review.isPast && !draft.confirmPastRecalculation) {
            _uiState.value = _uiState.value.copy(
                message = "Confirmá que querés recalcular los tramos desde ${review.startedOn}.",
            )
            return
        }
        if (review.isShortFirstSegment && !draft.confirmShortFirstSegment) {
            _uiState.value = _uiState.value.copy(
                message = "Confirmá que el primer tramo será más corto y conservará la meta completa.",
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, message = null)
            val currentAtStart = source.history.timeline.revisionAt(review.startedOn)
                ?: source.history.timeline.revisions.last()
            val sameDate = source.history.timeline.revisions.singleOrNull {
                it.effectiveFrom == review.startedOn
            }
            val revision = EffectiveRevision(
                id = sameDate?.id ?: draft.revisionId,
                effectiveFrom = review.startedOn,
                value = WorkConfiguration(
                    sector = currentAtStart.value.sector,
                    hoursReference = review.reference,
                    availabilityLabel = currentAtStart.value.availabilityLabel,
                    hoursReferenceStartedOn = review.startedOn.takeIf {
                        review.reference.requiresStartedOnMarker
                    },
                ),
            )
            val result = runCatching {
                configurationRepository.applyReferenceMutation(
                    WorkConfigurationReferenceMutation(
                        expectedHistory = source.history,
                        revision = revision,
                        initialPerPeriodValue = review.initialValue,
                    ),
                )
            }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(isSaving = false, message = error.userMessage())
                return@launch
            }
            when (result) {
                is WorkConfigurationReferenceWriteResult.Saved -> {
                    savedStateHandle.clearReferenceDraft()
                    _uiState.value = _uiState.value.copy(
                        surface = HoursAndExtrasSurface.PROGRESS,
                        referenceDraft = null,
                        referenceReview = null,
                        isSaving = false,
                        message = "La meta quedó guardada desde ${review.startedOn}.",
                    )
                    savedStateHandle.writeSurface(HoursAndExtrasSurface.PROGRESS)
                }
                WorkConfigurationReferenceWriteResult.Conflict -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        message = "La configuración cambió. Refrescá y revisá el borrador antes de guardar.",
                    )
                }
            }
        }
    }

    fun openPerPeriodValueEditor() {
        if (_uiState.value.isSaving) return
        val source = _uiState.value.source ?: return
        val reference = source.segment.ownerRevision.value.hoursReference as? HoursReference.PerPeriod
            ?: return
        val window = source.segment.naturalWindow ?: return
        val existing = when (val lookup = source.history.perPeriodHoursValues.valueFor(reference.keyFor(window))) {
            PerPeriodHoursLookup.Missing -> null
            is PerPeriodHoursLookup.Defined -> lookup.entry
        }
        val draft = PerPeriodValueDraft(
            requiredMinutes = existing?.requiredMinutes?.value?.toString().orEmpty(),
            valueId = existing?.id ?: uuidProvider(),
            expectedFingerprint = source.periodValueExpectationFingerprint(reference, window, existing),
        )
        _uiState.value = _uiState.value.copy(
            surface = HoursAndExtrasSurface.PERIOD_VALUE_EDITOR,
            periodValueDraft = draft,
            message = null,
        )
        savedStateHandle.writeSurface(HoursAndExtrasSurface.PERIOD_VALUE_EDITOR)
        savedStateHandle.writePeriodValueDraft(draft)
    }

    fun updatePerPeriodValueDraft(transform: (PerPeriodValueDraft) -> PerPeriodValueDraft) {
        if (_uiState.value.isSaving) return
        val current = _uiState.value.periodValueDraft ?: return
        val updated = transform(current)
        _uiState.value = _uiState.value.copy(periodValueDraft = updated, message = null)
        savedStateHandle.writePeriodValueDraft(updated)
    }

    fun backPerPeriodValue() {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(
            surface = HoursAndExtrasSurface.PROGRESS,
            periodValueDraft = null,
            message = null,
        )
        savedStateHandle.writeSurface(HoursAndExtrasSurface.PROGRESS)
        savedStateHandle.clearPeriodValueDraft()
    }

    fun savePerPeriodValue() {
        if (_uiState.value.isSaving) return
        val source = _uiState.value.source ?: return
        val draft = _uiState.value.periodValueDraft ?: return
        val reference = source.segment.ownerRevision.value.hoursReference as? HoursReference.PerPeriod
            ?: return
        val window = source.segment.naturalWindow ?: return
        val key = reference.keyFor(window)
        val expected = when (val lookup = source.history.perPeriodHoursValues.valueFor(key)) {
            PerPeriodHoursLookup.Missing -> null
            is PerPeriodHoursLookup.Defined -> lookup.entry
        }
        if (
            draft.expectedFingerprint == null ||
            source.periodValueExpectationFingerprint(reference, window, expected) != draft.expectedFingerprint
        ) {
            _uiState.value = _uiState.value.copy(
                message = "El período o su valor cambió. Volvé al avance y abrí nuevamente esta meta.",
            )
            return
        }
        if (expected != null && !draft.confirmCorrection) {
            _uiState.value = _uiState.value.copy(
                message = "Confirmá la corrección del valor de este período.",
            )
            return
        }
        val replacement = runCatching {
            PerPeriodHoursEntry(
                id = expected?.id ?: draft.valueId,
                key = key,
                requiredMinutes = PositiveMinutes(
                    parsePositiveLong(draft.requiredMinutes, "minutos del período"),
                ),
            )
        }.getOrElse { error ->
            _uiState.value = _uiState.value.copy(message = error.userMessage())
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, message = null)
            val result = runCatching {
                configurationRepository.applyPerPeriodHoursValueMutation(
                    PerPeriodHoursValueMutation(source.history, replacement),
                )
            }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(isSaving = false, message = error.userMessage())
                return@launch
            }
            if (result == PerPeriodHoursValueWriteResult.Conflict) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    message = "La meta cambió. El borrador se conserva para revisarlo.",
                )
                return@launch
            }
            savedStateHandle.clearPeriodValueDraft()
            savedStateHandle.writeSurface(HoursAndExtrasSurface.PROGRESS)
            _uiState.value = _uiState.value.copy(
                surface = HoursAndExtrasSurface.PROGRESS,
                periodValueDraft = null,
                isSaving = false,
                message = if (expected == null) {
                    "La meta del período quedó informada."
                } else {
                    "La meta del período quedó corregida."
                },
            )
        }
    }

    fun openCreateExtra(date: LocalDate) {
        if (_uiState.value.isSaving) return
        val source = _uiState.value.source ?: return
        if (!_uiState.value.canRegisterIndependentExtraOn(date)) {
            _uiState.value = _uiState.value.copy(
                message = if (source.history.timeline.revisionAt(date) == null) {
                    "La configuración laboral todavía no estaba vigente en esa fecha."
                } else {
                    "Primero necesitás lugar, tipo y clase extra utilizables."
                },
            )
            return
        }
        val draft = IndependentExtraDraftState(
            recordId = uuidProvider(),
            ownerDate = date,
            endDate = date.toString(),
            workPlaceId = source.catalog.workPlaces.firstOrNull { place ->
                place.isActive && source.objectives.any { it.id == place.objectiveId && it.isActive }
            }?.id,
            workTypeId = source.catalog.workTypes.firstOrNull {
                it.isActive && it.behavior == WorkTypeBehavior.ACTIVE_WORK
            }?.id,
            extraClassId = source.extraClasses.firstOrNull { it.isActive }?.id,
        )
        openExtraDraft(draft)
    }

    fun openCorrectExtra(record: IndependentExtraWorkRecord) {
        if (_uiState.value.isSaving) return
        val draft = IndependentExtraDraftState(
            recordId = record.id,
            ownerDate = record.ownerLocalDate,
            startTime = record.start.atZone(record.zoneId).toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString(),
            endDate = record.end.atZone(record.zoneId).toLocalDate().toString(),
            endTime = record.end.atZone(record.zoneId).toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString(),
            workPlaceId = record.workPlaceId,
            workTypeId = record.workTypeId,
            templateId = record.templateId,
            extraClassId = record.extraWorkClassId,
            colorArgb = record.snapshot.colorArgb,
            position = record.snapshot.position.orEmpty(),
            openedRecordFingerprint = record.stableFingerprint(),
        )
        openExtraDraft(draft)
    }

    private fun openExtraDraft(draft: IndependentExtraDraftState) {
        preparedExtraReview = null
        _uiState.value = _uiState.value.copy(
            surface = HoursAndExtrasSurface.EXTRA_EDITOR,
            extraDraft = draft,
            extraReview = null,
            deletingRecord = null,
            message = null,
        )
        savedStateHandle.writeSurface(HoursAndExtrasSurface.EXTRA_EDITOR)
        savedStateHandle.writeExtraDraft(draft)
    }

    fun updateExtraDraft(transform: (IndependentExtraDraftState) -> IndependentExtraDraftState) {
        if (_uiState.value.isSaving) return
        val current = _uiState.value.extraDraft ?: return
        val updated = transform(current).copy(
            stage = EditorStage.EDIT,
            expectedFingerprint = null,
            overlapConfirmed = false,
            protectionConfirmed = false,
        )
        preparedExtraReview = null
        _uiState.value = _uiState.value.copy(
            extraDraft = updated,
            extraReview = null,
            message = null,
        )
        savedStateHandle.writeExtraDraft(updated)
    }

    fun selectTemplate(templateId: UUID?) {
        val source = _uiState.value.source ?: return
        val current = _uiState.value.extraDraft ?: return
        val template = templateId?.let { id -> source.catalog.workTemplates.singleOrNull { it.id == id } }
        if (template == null) {
            updateExtraDraft { it.copy(templateId = null, colorArgb = null) }
            return
        }
        val date = requireNotNull(current.ownerDate)
        val endDate = if (!template.endTime.isAfter(template.startTime)) date.plusDays(1) else date
        updateExtraDraft {
            it.copy(
                workPlaceId = template.workPlaceId,
                workTypeId = template.workTypeId,
                templateId = template.id,
                startTime = template.startTime.toString(),
                endDate = endDate.toString(),
                endTime = template.endTime.toString(),
                colorArgb = template.colorArgb,
            )
        }
    }

    fun reviewExtra() {
        if (_uiState.value.isSaving) return
        val draft = _uiState.value.extraDraft ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, message = null)
            val review = runCatching { prepareExtraReview(draft) }
                .getOrElse { error ->
                    _uiState.value = _uiState.value.copy(isSaving = false, message = error.userMessage())
                    return@launch
                }
            preparedExtraReview = review
            val fingerprint = review.expectation.stableFingerprint()
            val reviewed = draft.copy(
                stage = EditorStage.REVIEW,
                expectedFingerprint = fingerprint,
            )
            _uiState.value = _uiState.value.copy(
                extraDraft = reviewed,
                extraReview = review,
                isSaving = false,
                message = null,
            )
            savedStateHandle.writeExtraDraft(reviewed)
        }
    }

    fun confirmOverlap(value: Boolean) = updateReviewConfirmation(overlap = value)

    fun confirmProtection(value: Boolean) = updateReviewConfirmation(protection = value)

    private fun updateReviewConfirmation(overlap: Boolean? = null, protection: Boolean? = null) {
        if (_uiState.value.isSaving) return
        val draft = _uiState.value.extraDraft ?: return
        val updated = draft.copy(
            overlapConfirmed = overlap ?: draft.overlapConfirmed,
            protectionConfirmed = protection ?: draft.protectionConfirmed,
        )
        _uiState.value = _uiState.value.copy(extraDraft = updated, message = null)
        savedStateHandle.writeExtraDraft(updated)
    }

    fun backExtra() {
        if (_uiState.value.isSaving) return
        val draft = _uiState.value.extraDraft ?: return
        if (draft.stage == EditorStage.REVIEW) {
            val updated = draft.copy(stage = EditorStage.EDIT)
            preparedExtraReview = null
            _uiState.value = _uiState.value.copy(extraDraft = updated, extraReview = null, message = null)
            savedStateHandle.writeExtraDraft(updated)
        } else {
            close()
        }
    }

    fun saveExtra() {
        if (_uiState.value.isSaving) return
        val draft = _uiState.value.extraDraft ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, message = null)
            val review = runCatching {
                val prepared = preparedExtraReview ?: prepareExtraReview(draft)
                val savedFingerprint = draft.expectedFingerprint
                    ?: error("El borrador debe revisarse antes de guardarlo")
                require(prepared.expectation.stableFingerprint() == savedFingerprint) {
                    "Las fuentes cambiaron desde la revisión. Refrescá y revisá otra vez."
                }
                prepared
            }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(isSaving = false, message = error.userMessage())
                return@launch
            }
            if (review.hasOverlap && !draft.overlapConfirmed) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    message = "Confirmá que querés conservar todos los trabajos superpuestos.",
                )
                return@launch
            }
            if (review.hasProtectedDates && !draft.protectionConfirmed) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    message = "Confirmá la convivencia con carpeta médica o vacaciones.",
                )
                return@launch
            }
            val result = runCatching {
                independentExtraRepository.applyMutation(
                    IndependentExtraWorkMutation(
                        expectation = review.expectation,
                        replacement = review.record,
                        overlappingWorkConfirmed = draft.overlapConfirmed,
                        protectedDateConfirmed = draft.protectionConfirmed,
                    ),
                )
            }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(isSaving = false, message = error.userMessage())
                return@launch
            }
            when (result) {
                is IndependentExtraWorkWriteResult.Saved -> finishExtraWrite("El trabajo extra quedó guardado.")
                IndependentExtraWorkWriteResult.Deleted -> finishExtraWrite("El trabajo extra quedó eliminado.")
                IndependentExtraWorkWriteResult.Conflict -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        message = "Los datos cambiaron. El borrador se conserva para que refresques y revises.",
                    )
                }
            }
        }
    }

    fun requestDelete(record: IndependentExtraWorkRecord) {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(
            surface = HoursAndExtrasSurface.DELETE_CONFIRMATION,
            deletingRecord = record,
            message = null,
        )
        savedStateHandle.writeSurface(HoursAndExtrasSurface.DELETE_CONFIRMATION)
        savedStateHandle[KEY_DELETE_ID] = record.id.toString()
        savedStateHandle[KEY_DELETE_FINGERPRINT] = record.stableFingerprint()
    }

    fun dismissDelete() {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(
            surface = HoursAndExtrasSurface.NONE,
            deletingRecord = null,
            message = null,
        )
        savedStateHandle.writeSurface(HoursAndExtrasSurface.NONE)
        savedStateHandle.remove<String>(KEY_DELETE_ID)
        savedStateHandle.remove<String>(KEY_DELETE_FINGERPRINT)
    }

    fun confirmDelete() {
        if (_uiState.value.isSaving) return
        val source = _uiState.value.source ?: return
        val displayed = _uiState.value.deletingRecord ?: return
        val expectedFingerprint = savedStateHandle.get<String>(KEY_DELETE_FINGERPRINT)
            ?: displayed.stableFingerprint()
        val record = source.independentExtras.singleOrNull { it.id == displayed.id }
        if (record == null || record.stableFingerprint() != expectedFingerprint) {
            _uiState.value = _uiState.value.copy(
                message = "El trabajo extra cambió desde que lo abriste. Cerrá y revisá la versión actual.",
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, message = null)
            val selection = runCatching { selectionFor(record, source) }
                .getOrElse { error ->
                    _uiState.value = _uiState.value.copy(isSaving = false, message = error.userMessage())
                    return@launch
                }
            val expectation = runCatching {
                independentExtraRepository.captureExpectation(
                    id = record.id,
                    selection = selection,
                    windowStart = record.start,
                    windowEnd = record.end,
                    windowStartDate = record.ownerLocalDate,
                    windowEndDateInclusive = record.end.minusNanos(1).atZone(record.zoneId).toLocalDate(),
                )
            }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(isSaving = false, message = error.userMessage())
                return@launch
            }
            val result = runCatching {
                independentExtraRepository.applyMutation(
                    IndependentExtraWorkMutation(expectation, null, true, true),
                )
            }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(isSaving = false, message = error.userMessage())
                return@launch
            }
            when (result) {
                IndependentExtraWorkWriteResult.Deleted -> finishExtraWrite("El trabajo extra quedó eliminado.")
                IndependentExtraWorkWriteResult.Conflict -> _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    message = "El trabajo extra cambió. Refrescá antes de eliminarlo.",
                )
                is IndependentExtraWorkWriteResult.Saved -> error("Una eliminación no puede guardar un registro")
            }
        }
    }

    fun consumeSuccess(sequence: Int) {
        if (_uiState.value.successSequence == sequence) {
            _uiState.value = _uiState.value.copy(successSequence = 0)
        }
    }

    private fun finishExtraWrite(message: String) {
        preparedExtraReview = null
        savedStateHandle.clearEditors()
        _uiState.value = _uiState.value.copy(
            surface = HoursAndExtrasSurface.NONE,
            extraDraft = null,
            extraReview = null,
            deletingRecord = null,
            isSaving = false,
            message = message,
            successSequence = _uiState.value.successSequence + 1,
        )
    }

    private suspend fun prepareExtraReview(draft: IndependentExtraDraftState): IndependentExtraReview {
        val source = requireNotNull(_uiState.value.source) { "Todavía no se cargaron las fuentes laborales" }
        val ownerDate = requireNotNull(draft.ownerDate) { "Falta la fecha del trabajo extra" }
        val startTime = parseTime(draft.startTime, "inicio")
        val endDate = parseDate(draft.endDate, "fecha final")
        val endTime = parseTime(draft.endTime, "final")
        val start = resolveActualLocalDateTime(ownerDate.atTime(startTime), zoneId)
        val end = resolveActualLocalDateTime(endDate.atTime(endTime), zoneId)
        require(start < end) { "El final debe ser posterior al inicio" }
        val previous = source.independentExtras.singleOrNull { it.id == draft.recordId }
        if (draft.openedRecordFingerprint == null) {
            require(previous == null) {
                "Ya existe un trabajo extra con esta identidad. Volvé a abrir el formulario."
            }
        } else {
            requireNotNull(previous) {
                "El trabajo extra que abriste ya no existe. El borrador no se guardó."
            }
            require(previous.stableFingerprint() == draft.openedRecordFingerprint) {
                "El trabajo extra cambió desde que lo abriste. Refrescá antes de corregirlo."
            }
        }
        val selection = selectionFor(draft, source, ownerDate)
        val preserveHistorical = previous != null &&
            previous.workPlaceId == selection.workPlace.id &&
            previous.objectiveId == selection.objective.id &&
            previous.workTypeId == selection.workType.id &&
            previous.templateId == selection.template?.id &&
            previous.extraWorkClassId == selection.extraWorkClass.id
        val timestamp = maxOf(
            clock.instant().truncatedTo(ChronoUnit.MILLIS),
            previous?.updatedAt?.plusMillis(1) ?: Instant.MIN,
        )
        val record = buildIndependentExtraWorkRecord(
            draft = IndependentExtraWorkDraft(
                id = draft.recordId,
                ownerLocalDate = ownerDate,
                zoneId = zoneId,
                start = start,
                end = end,
                colorArgb = requireNotNull(draft.colorArgb) {
                    "Elegí un color o una plantilla"
                },
                position = draft.position,
            ),
            selection = selection,
            clock = clock,
            timestamp = timestamp,
            previous = previous,
            preserveHistoricalSnapshot = preserveHistorical,
        )
        val windowStart = minOf(start, previous?.start ?: start)
        val windowEnd = maxOf(end, previous?.end ?: end)
        val windowStartDate = minOf(ownerDate, previous?.ownerLocalDate ?: ownerDate)
        val windowEndDate = maxOf(
            end.minusNanos(1).atZone(zoneId).toLocalDate(),
            previous?.let { record ->
                record.end.minusNanos(1).atZone(record.zoneId).toLocalDate()
            } ?: ownerDate,
        )
        val expectation = independentExtraRepository.captureExpectation(
            id = previous?.id,
            selection = selection,
            windowStart = windowStart,
            windowEnd = windowEnd,
            windowStartDate = windowStartDate,
            windowEndDateInclusive = windowEndDate,
        )
        return IndependentExtraReview(
            record = record,
            expectation = expectation,
            hasOverlap = expectation.hasOverlappingWorkFor(record),
            hasProtectedDates = expectation.hasProtectedDatesFor(record),
        )
    }

    private fun selectionFor(
        draft: IndependentExtraDraftState,
        source: HoursAndExtrasSource,
        ownerDate: LocalDate,
    ): IndependentExtraWorkSelection {
        val place = source.catalog.workPlaces.singleOrNull { it.id == draft.workPlaceId }
            ?: error("Elegí un lugar de trabajo")
        val objective = source.objectives.singleOrNull { it.id == place.objectiveId }
            ?: error("El lugar ya no conserva su objetivo")
        val type = source.catalog.workTypes.singleOrNull { it.id == draft.workTypeId }
            ?: error("Elegí un tipo de trabajo")
        val template = draft.templateId?.let { id ->
            source.catalog.workTemplates.singleOrNull { it.id == id }
                ?: error("La plantilla elegida ya no está disponible")
        }
        val extraClass = source.extraClasses.singleOrNull { it.id == draft.extraClassId }
            ?: error("Elegí una clase de horas extra")
        return IndependentExtraWorkSelection(
            configuration = ResolvedWorkConfigurationRevision.resolve(source.history, ownerDate),
            workPlace = place,
            objective = objective,
            workType = type,
            template = template,
            extraWorkClass = extraClass,
        )
    }

    private fun selectionFor(
        record: IndependentExtraWorkRecord,
        source: HoursAndExtrasSource,
    ): IndependentExtraWorkSelection = IndependentExtraWorkSelection(
        configuration = ResolvedWorkConfigurationRevision.resolve(source.history, record.ownerLocalDate),
        workPlace = source.catalog.workPlaces.single { it.id == record.workPlaceId },
        objective = source.objectives.single { it.id == record.objectiveId },
        workType = source.catalog.workTypes.single { it.id == record.workTypeId },
        template = record.templateId?.let { id -> source.catalog.workTemplates.single { it.id == id } },
        extraWorkClass = source.extraClasses.single { it.id == record.extraWorkClassId },
    )

    private fun buildReferenceReview(
        source: HoursAndExtrasSource,
        draft: HoursReferenceDraft,
    ): HoursReferenceReview = buildHoursReferenceReview(source.history, source.today, draft)

    private fun startObservation() {
        observationJob?.cancel()
        _uiState.value = _uiState.value.copy(loadState = HoursAndExtrasLoadState.LOADING, message = null)
        observationJob = viewModelScope.launch {
            sourceFlow()
                .catch { error -> emit(SourceLoad.Error(error.userMessage())) }
                .collect { load ->
                    when (load) {
                        is SourceLoad.Content -> {
                            val currentState = _uiState.value
                            val deleting = currentState.deletingRecord
                                ?: savedStateHandle.get<String>(KEY_DELETE_ID)?.let { raw ->
                                    runCatching { UUID.fromString(raw) }.getOrNull()?.let { id ->
                                        load.value.independentExtras.singleOrNull { it.id == id }
                                    }
                                }
                            val reviewedReferenceDraft = currentState.referenceDraft
                                ?.takeIf { it.stage == EditorStage.REVIEW }
                            val recomputedReferenceReview = reviewedReferenceDraft?.let { draft ->
                                runCatching {
                                    buildHoursReferenceReview(load.value.history, load.value.today, draft)
                                }.getOrNull()
                            }
                            val referenceStillMatches = reviewedReferenceDraft == null || (
                                recomputedReferenceReview != null &&
                                    reviewedReferenceDraft.expectedFingerprint ==
                                    load.value.referenceExpectationFingerprint(recomputedReferenceReview)
                                )
                            val restoredReferenceReview = if (referenceStillMatches) {
                                currentState.referenceReview ?: recomputedReferenceReview
                            } else {
                                null
                            }
                            _uiState.value = currentState.copy(
                                loadState = HoursAndExtrasLoadState.CONTENT,
                                source = load.value,
                                deletingRecord = deleting,
                                referenceReview = restoredReferenceReview,
                                message = if (!referenceStillMatches) {
                                    "La configuración o la fecha cambió desde la revisión. Revisá nuevamente."
                                } else {
                                    currentState.message
                                },
                            )
                            val restoredExtraDraft = _uiState.value.extraDraft
                                ?.takeIf { draft ->
                                    draft.stage == EditorStage.REVIEW &&
                                        _uiState.value.extraReview == null
                                }
                            if (restoredExtraDraft != null) {
                                val restoredReview = runCatching {
                                    prepareExtraReview(restoredExtraDraft)
                                }.getOrNull()
                                if (
                                    restoredReview != null &&
                                    restoredReview.expectation.stableFingerprint() ==
                                    restoredExtraDraft.expectedFingerprint
                                ) {
                                    preparedExtraReview = restoredReview
                                    _uiState.value = _uiState.value.copy(extraReview = restoredReview)
                                } else {
                                    _uiState.value = _uiState.value.copy(
                                        message = "Las fuentes del trabajo extra cambiaron. Volvé a revisarlo antes de guardar.",
                                    )
                                }
                            }
                        }
                        is SourceLoad.Error -> _uiState.value = _uiState.value.copy(
                            loadState = HoursAndExtrasLoadState.ERROR,
                            message = load.message,
                        )
                    }
                }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun sourceFlow(): Flow<SourceLoad> = configurationRepository.observe().flatMapLatest { history ->
        if (history == null) return@flatMapLatest flowOf(SourceLoad.Error("Todavía no existe una configuración laboral."))
        minutePulse()
            .map { LocalDate.now(clock.withZone(zoneId)) }
            .distinctUntilChanged()
            .flatMapLatest { today ->
                val segment = resolveHoursReferenceSegment(history, today)
                    ?: return@flatMapLatest flowOf(
                        SourceLoad.Error("La configuración laboral todavía no está vigente."),
                    )
                val timelineId = history.timeline.id
                val sector = segment.ownerRevision.value.sector
                val catalogAndObjectives = combine(
                    catalogRepository.observeCatalog(timelineId, sector),
                    objectiveRepository.observeAll(),
                ) { catalog, objectives -> catalog to objectives }
                val extrasAndClasses = combine(
                    shiftActualRepository.observeExtraWorkClasses(timelineId, sector),
                    independentExtraRepository.observeAll(timelineId, sector),
                ) { classes, extras -> classes to extras }
                val shiftsAndActuals = combine(
                    shiftRepository.observeAll(timelineId, sector),
                    shiftActualRepository.observeAllActuals(timelineId, sector),
                ) { shifts, actuals -> shifts to actuals }
                val protectionEnd = if (segment.endExclusive == LocalDate.MAX) {
                    LocalDate.MAX
                } else {
                    segment.endExclusive.minusDays(1)
                }
                val protections = combine(
                    medicalLeaveRepository.observeIntersecting(segment.startInclusive, protectionEnd),
                    vacationRepository.observeOverlapping(segment.startInclusive, protectionEnd),
                ) { medicalLeaves, vacations ->
                    medicalLeaves.map { WorkProtectionPeriod(it.startDate, it.endDateInclusive) } +
                        vacations.map { WorkProtectionPeriod(it.startDate, it.endDateInclusive) }
                }
                val workAndMinute = combine(shiftsAndActuals, minutePulse()) { workData, _ -> workData }
                combine(
                    catalogAndObjectives,
                    extrasAndClasses,
                    workAndMinute,
                    protections,
                ) { catalogData, extrasData, workData, protectionData ->
                    val shifts = workData.first.map { write ->
                        WorkedShiftSource(write.shift, workData.second[write.shift.id])
                    }
                    SourceLoad.Content(
                        HoursAndExtrasSource(
                            history = history,
                            catalog = catalogData.first,
                            objectives = catalogData.second,
                            extraClasses = extrasData.first,
                            independentExtras = extrasData.second,
                            segment = segment,
                            progress = calculateHoursProgress(
                                segment,
                                shifts,
                                extrasData.second,
                                clock,
                                zoneId,
                                protectionData,
                            ),
                            today = today,
                        ),
                    )
                }
            }
    }

    private fun minutePulse(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            val millisIntoMinute = Math.floorMod(clock.millis(), MILLIS_PER_MINUTE)
            delay(MILLIS_PER_MINUTE - millisIntoMinute)
        }
    }

    private fun HoursReferenceDraft.buildPeriod(): HoursPeriod? = when (periodChoice) {
        HoursPeriodChoice.NONE -> null
        HoursPeriodChoice.MONTHLY -> HoursPeriod.Monthly
        HoursPeriodChoice.WEEKLY -> HoursPeriod.Weekly(weeklyFirstDay)
        HoursPeriodChoice.CYCLE -> HoursPeriod.Cycle(
            anchorDate = parseDate(cycleAnchorDate, "fecha de anclaje"),
            lengthDays = cycleLengthDays.toIntOrNull()?.takeIf { it > 0 }
                ?: error("La cantidad de días del ciclo debe ser positiva"),
        )
    }

    private fun parseDate(raw: String, label: String): LocalDate = runCatching {
        LocalDate.parse(raw.trim())
    }.getOrElse { error("La $label debe escribirse como AAAA-MM-DD") }

    private fun parseTime(raw: String, label: String): LocalTime = runCatching {
        LocalTime.parse(raw.trim()).truncatedTo(ChronoUnit.MINUTES)
    }.getOrElse { error("La hora de $label debe escribirse como HH:mm") }

    private fun parsePositiveLong(raw: String, label: String): Long = raw.trim().toLongOrNull()
        ?.takeIf { it > 0L }
        ?: error("Los $label deben ser un número entero positivo")

    private fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() }
        ?: "No pudimos completar la operación. Los datos no se modificaron."

    private sealed interface SourceLoad {
        data class Content(val value: HoursAndExtrasSource) : SourceLoad
        data class Error(val message: String) : SourceLoad
    }

    class Factory(
        private val configurationRepository: WorkConfigurationRepository,
        private val catalogRepository: WorkCatalogRepository,
        private val objectiveRepository: ObjectiveRepository,
        private val shiftRepository: V2ShiftRepository,
        private val shiftActualRepository: ShiftActualRepository,
        private val independentExtraRepository: IndependentExtraWorkRepository,
        private val medicalLeaveRepository: MedicalLeaveRepository,
        private val vacationRepository: VacationRepository,
        private val clock: Clock,
        private val zoneId: ZoneId,
        private val uuidProvider: () -> UUID = UUID::randomUUID,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(HoursAndExtrasViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return HoursAndExtrasViewModel(
                configurationRepository,
                catalogRepository,
                objectiveRepository,
                shiftRepository,
                shiftActualRepository,
                independentExtraRepository,
                medicalLeaveRepository,
                vacationRepository,
                clock,
                zoneId,
                uuidProvider,
                extras.createSavedStateHandle(),
            ) as T
        }
    }
}

internal fun buildHoursReferenceReview(
    history: WorkConfigurationHistory,
    today: LocalDate,
    draft: HoursReferenceDraft,
): HoursReferenceReview {
    val period = draft.buildReferencePeriod()
    val startedOn = when (draft.startChoice) {
        ReferenceStartChoice.TODAY -> today
        ReferenceStartChoice.NEXT_PERIOD -> requireNotNull(period) {
            "Elegí un período antes de usar su próximo inicio"
        }.windowContaining(today).endExclusive
        ReferenceStartChoice.CUSTOM -> parseHoursDate(draft.customStartDate, "fecha de inicio")
    }
    val firstConfiguredDate = history.timeline.revisions.first().effectiveFrom
    require(!startedOn.isBefore(firstConfiguredDate)) {
        "La meta no puede comenzar antes del $firstConfiguredDate"
    }
    val reference = when (draft.choice) {
        HoursReferenceChoice.PENDING -> HoursReference.PendingSetup
        HoursReferenceChoice.NOT_USED -> HoursReference.NotUsed
        HoursReferenceChoice.UNKNOWN -> HoursReference.Unknown(period)
        HoursReferenceChoice.FIXED -> HoursReference.Fixed(
            period = requireNotNull(period) { "Elegí un período para la meta fija" },
            requiredMinutes = PositiveMinutes(
                parsePositiveHoursValue(draft.requiredMinutes, "minutos de la meta"),
            ),
        )
        HoursReferenceChoice.PER_PERIOD -> HoursReference.PerPeriod(
            definitionId = (
                history.timeline.revisionAt(startedOn)?.value?.hoursReference as? HoursReference.PerPeriod
                )?.takeIf { existing -> existing.period == period }
                ?.definitionId
                ?: draft.definitionId,
            period = requireNotNull(period) { "Elegí un período para la meta variable" },
        )
    }
    val window = reference.periodOrNull()?.windowContaining(startedOn)
    val previousAtStart = requireNotNull(history.timeline.revisionAt(startedOn))
    val recalculationEndExclusive = history.timeline.revisions
        .asSequence()
        .filter { it.effectiveFrom.isAfter(startedOn) }
        .firstOrNull { revision ->
            revision.value.hoursReference != previousAtStart.value.hoursReference ||
                revision.value.hoursReferenceStartedOn != previousAtStart.value.hoursReferenceStartedOn
        }
        ?.effectiveFrom
        ?: LocalDate.MAX
    val initialValue = (reference as? HoursReference.PerPeriod)?.let { perPeriod ->
        val key = perPeriod.keyContaining(startedOn)
        draft.initialPerPeriodMinutes.trim().takeIf { it.isNotEmpty() }?.let { raw ->
            val minutes = PositiveMinutes(parsePositiveHoursValue(raw, "minutos del primer período"))
            when (val existing = history.perPeriodHoursValues.valueFor(key)) {
                PerPeriodHoursLookup.Missing -> PerPeriodHoursEntry(draft.valueId, key, minutes)
                is PerPeriodHoursLookup.Defined -> {
                    require(existing.entry.requiredMinutes == minutes) {
                        "Corregí la meta ya informada desde la acción específica de este período"
                    }
                    null
                }
            }
        }
    }
    return HoursReferenceReview(
        reference = reference,
        startedOn = startedOn,
        previousSegmentEndInclusive = if (startedOn.isAfter(firstConfiguredDate)) {
            startedOn.minusDays(1)
        } else {
            null
        },
        naturalWindowStart = window?.startInclusive,
        naturalWindowEndExclusive = window?.endExclusive,
        recalculationEndExclusive = recalculationEndExclusive,
        isPast = startedOn.isBefore(today),
        isShortFirstSegment = window != null && startedOn != window.startInclusive,
        initialValue = initialValue,
    )
}

private fun HoursReferenceDraft.buildReferencePeriod(): HoursPeriod? = when (periodChoice) {
    HoursPeriodChoice.NONE -> null
    HoursPeriodChoice.MONTHLY -> HoursPeriod.Monthly
    HoursPeriodChoice.WEEKLY -> HoursPeriod.Weekly(weeklyFirstDay)
    HoursPeriodChoice.CYCLE -> HoursPeriod.Cycle(
        anchorDate = parseHoursDate(cycleAnchorDate, "fecha de anclaje"),
        lengthDays = cycleLengthDays.toIntOrNull()?.takeIf { it > 0 }
            ?: error("La cantidad de días del ciclo debe ser positiva"),
    )
}

private fun parseHoursDate(raw: String, label: String): LocalDate = runCatching {
    LocalDate.parse(raw.trim())
}.getOrElse { error("La $label debe escribirse como AAAA-MM-DD") }

private fun parsePositiveHoursValue(raw: String, label: String): Long = raw.trim().toLongOrNull()
    ?.takeIf { it > 0L }
    ?: error("Los $label deben ser un número entero positivo")

private fun HoursReference.toChoice(): HoursReferenceChoice = when (this) {
    HoursReference.PendingSetup -> HoursReferenceChoice.PENDING
    HoursReference.NotUsed -> HoursReferenceChoice.NOT_USED
    is HoursReference.Unknown -> HoursReferenceChoice.UNKNOWN
    is HoursReference.Fixed -> HoursReferenceChoice.FIXED
    is HoursReference.PerPeriod -> HoursReferenceChoice.PER_PERIOD
}

internal fun HoursReference.periodOrNull(): HoursPeriod? = when (this) {
    HoursReference.PendingSetup,
    HoursReference.NotUsed,
    -> null
    is HoursReference.Unknown -> period
    is HoursReference.Fixed -> period
    is HoursReference.PerPeriod -> period
}

private fun HoursPeriod?.toChoice(): HoursPeriodChoice = when (this) {
    null -> HoursPeriodChoice.NONE
    HoursPeriod.Monthly -> HoursPeriodChoice.MONTHLY
    is HoursPeriod.Weekly -> HoursPeriodChoice.WEEKLY
    is HoursPeriod.Cycle -> HoursPeriodChoice.CYCLE
}

private fun HoursReferenceDraft.decisionFingerprint(): String = listOf(
    choice,
    periodChoice,
    weeklyFirstDay,
    cycleLengthDays,
    cycleAnchorDate,
    requiredMinutes,
    initialPerPeriodMinutes,
    startChoice,
    customStartDate,
    revisionId,
    definitionId,
    valueId,
).joinToString(separator = "|")

private fun HoursAndExtrasSource.referenceExpectationFingerprint(
    review: HoursReferenceReview,
): String = listOf(
    history.timeline.id,
    history.timeline.revisions,
    history.perPeriodHoursValues.entries,
    today,
    review,
).joinToString(separator = "|")

private fun HoursAndExtrasSource.periodValueExpectationFingerprint(
    reference: HoursReference.PerPeriod,
    window: com.blackatsystems.miguardia.core.domain.work.DateWindow,
    existing: PerPeriodHoursEntry?,
): String = listOf(
    history.timeline.id,
    history.timeline.revisions,
    history.perPeriodHoursValues.entries,
    reference.definitionId,
    reference.period,
    window.startInclusive,
    window.endExclusive,
    existing,
).joinToString(separator = "|")

private fun IndependentExtraWorkRecord.stableFingerprint(): String = toString()

private fun IndependentExtraWorkExpectation.stableFingerprint(): String = buildList {
    add(previous?.toString().orEmpty())
    add(selection.configuration.timelineId.toString())
    add(selection.configuration.referenceDate.toString())
    add(selection.configuration.revision.id.toString())
    add(selection.configuration.revision.effectiveFrom.toString())
    add(selection.configuration.revision.value.toString())
    add(selection.workPlace.toString())
    add(selection.objective.toString())
    add(selection.workType.toString())
    add(selection.template?.toString().orEmpty())
    add(selection.extraWorkClass.toString())
    add(windowStart.toString())
    add(windowEnd.toString())
    add(windowStartDate.toString())
    add(windowEndDateInclusive.toString())
    add(observedShifts.sortedBy { it.shiftId }.joinToString(separator = ";"))
    add(observedExtras.sortedBy { it.id }.joinToString(separator = ";"))
    add(
        observedProtectedDateRanges
            .sortedWith(compareBy({ it.startDateInclusive }, { it.endDateInclusive }))
            .joinToString(separator = ";"),
    )
    add(protectionFingerprint)
}.joinToString(separator = "|")

private const val KEY_SURFACE = "hours_extras_surface"
private const val KEY_REFERENCE = "hours_reference_draft"
private const val KEY_PERIOD_VALUE = "per_period_value_draft"
private const val KEY_EXTRA = "independent_extra_draft"
private const val KEY_DELETE_ID = "independent_extra_delete_id"
private const val KEY_DELETE_FINGERPRINT = "independent_extra_delete_fingerprint"
private const val MILLIS_PER_MINUTE: Long = 60_000L

private fun SavedStateHandle.readSurface(): HoursAndExtrasSurface =
    get<String>(KEY_SURFACE)?.let { raw ->
        runCatching { HoursAndExtrasSurface.valueOf(raw) }.getOrNull()
    } ?: HoursAndExtrasSurface.NONE

private fun SavedStateHandle.writeSurface(value: HoursAndExtrasSurface) {
    this[KEY_SURFACE] = value.name
}

private fun SavedStateHandle.writeReferenceDraft(value: HoursReferenceDraft) {
    this[KEY_REFERENCE] = arrayListOf(
        value.choice.name,
        value.periodChoice.name,
        value.weeklyFirstDay.name,
        value.cycleLengthDays,
        value.cycleAnchorDate,
        value.requiredMinutes,
        value.initialPerPeriodMinutes,
        value.startChoice.name,
        value.customStartDate,
        value.confirmPastRecalculation.toString(),
        value.confirmShortFirstSegment.toString(),
        value.stage.name,
        value.revisionId.toString(),
        value.definitionId.toString(),
        value.valueId.toString(),
        value.expectedFingerprint.orEmpty(),
    )
}

private fun SavedStateHandle.readReferenceDraft(): HoursReferenceDraft? = runCatching {
    val values = get<ArrayList<String>>(KEY_REFERENCE) ?: return null
    HoursReferenceDraft(
        choice = HoursReferenceChoice.valueOf(values[0]),
        periodChoice = HoursPeriodChoice.valueOf(values[1]),
        weeklyFirstDay = DayOfWeek.valueOf(values[2]),
        cycleLengthDays = values[3],
        cycleAnchorDate = values[4],
        requiredMinutes = values[5],
        initialPerPeriodMinutes = values[6],
        startChoice = ReferenceStartChoice.valueOf(values[7]),
        customStartDate = values[8],
        confirmPastRecalculation = values[9].toBooleanStrict(),
        confirmShortFirstSegment = values[10].toBooleanStrict(),
        stage = EditorStage.valueOf(values[11]),
        revisionId = UUID.fromString(values[12]),
        definitionId = UUID.fromString(values[13]),
        valueId = UUID.fromString(values[14]),
        expectedFingerprint = values.getOrNull(15)?.takeIf(String::isNotEmpty),
    )
}.getOrNull()

private fun SavedStateHandle.writePeriodValueDraft(value: PerPeriodValueDraft) {
    this[KEY_PERIOD_VALUE] = arrayListOf(
        value.requiredMinutes,
        value.confirmCorrection.toString(),
        value.valueId.toString(),
        value.expectedFingerprint.orEmpty(),
    )
}

private fun SavedStateHandle.readPeriodValueDraft(): PerPeriodValueDraft? = runCatching {
    val values = get<ArrayList<String>>(KEY_PERIOD_VALUE) ?: return null
    PerPeriodValueDraft(
        requiredMinutes = values[0],
        confirmCorrection = values[1].toBooleanStrict(),
        valueId = UUID.fromString(values[2]),
        expectedFingerprint = values.getOrNull(3)?.takeIf(String::isNotEmpty),
    )
}.getOrNull()

private fun SavedStateHandle.writeExtraDraft(value: IndependentExtraDraftState) {
    this[KEY_EXTRA] = arrayListOf(
        value.recordId.toString(),
        value.ownerDate?.toString().orEmpty(),
        value.startTime,
        value.endDate,
        value.endTime,
        value.workPlaceId?.toString().orEmpty(),
        value.workTypeId?.toString().orEmpty(),
        value.templateId?.toString().orEmpty(),
        value.extraClassId?.toString().orEmpty(),
        value.colorArgb?.toString().orEmpty(),
        value.position,
        value.stage.name,
        value.overlapConfirmed.toString(),
        value.protectionConfirmed.toString(),
        value.expectedFingerprint.orEmpty(),
        value.openedRecordFingerprint.orEmpty(),
    )
}

private fun SavedStateHandle.readExtraDraft(): IndependentExtraDraftState? = runCatching {
    val values = get<ArrayList<String>>(KEY_EXTRA) ?: return null
    IndependentExtraDraftState(
        recordId = UUID.fromString(values[0]),
        ownerDate = values[1].takeIf(String::isNotEmpty)?.let(LocalDate::parse),
        startTime = values[2],
        endDate = values[3],
        endTime = values[4],
        workPlaceId = values[5].takeIf(String::isNotEmpty)?.let(UUID::fromString),
        workTypeId = values[6].takeIf(String::isNotEmpty)?.let(UUID::fromString),
        templateId = values[7].takeIf(String::isNotEmpty)?.let(UUID::fromString),
        extraClassId = values[8].takeIf(String::isNotEmpty)?.let(UUID::fromString),
        colorArgb = values[9].takeIf(String::isNotEmpty)?.toInt(),
        position = values[10],
        stage = EditorStage.valueOf(values[11]),
        overlapConfirmed = values[12].toBooleanStrict(),
        protectionConfirmed = values[13].toBooleanStrict(),
        expectedFingerprint = values[14].takeIf(String::isNotEmpty),
        openedRecordFingerprint = values.getOrNull(15)?.takeIf(String::isNotEmpty),
    )
}.getOrNull()

private fun SavedStateHandle.clearReferenceDraft() {
    remove<ArrayList<String>>(KEY_REFERENCE)
}

private fun SavedStateHandle.clearPeriodValueDraft() {
    remove<ArrayList<String>>(KEY_PERIOD_VALUE)
}

private fun SavedStateHandle.clearEditors() {
    remove<String>(KEY_SURFACE)
    remove<ArrayList<String>>(KEY_REFERENCE)
    remove<ArrayList<String>>(KEY_PERIOD_VALUE)
    remove<ArrayList<String>>(KEY_EXTRA)
    remove<String>(KEY_DELETE_ID)
    remove<String>(KEY_DELETE_FINGERPRINT)
}
