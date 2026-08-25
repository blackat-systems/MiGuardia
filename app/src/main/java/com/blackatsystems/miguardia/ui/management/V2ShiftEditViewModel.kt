package com.blackatsystems.miguardia.ui.management

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrence
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.RecurringPlanRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import com.blackatsystems.miguardia.core.domain.shift.ShiftPlanningWarning
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.shift.editV2ShiftPositionOnly
import com.blackatsystems.miguardia.core.domain.shift.editV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.shift.planV2ShiftBatch
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.normalizeOptionalWorkText
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

enum class V2ShiftEditStage {
    IDLE,
    DAY_ACTIONS,
    CHOOSE_EDIT_SCOPE,
    CHOOSE_DELETE_SCOPE,
    EDIT_FORM,
    CONFIRM_WARNINGS,
    REVIEW,
    CONFIRM_DELETE,
    CONFIRM_DISCARD,
}

enum class V2ShiftDayInspectionState {
    IDLE,
    LOADING,
    CONTENT,
    ERROR,
}

data class V2ShiftEditDayRow(
    val shift: Shift,
    val snapshot: ShiftWorkSnapshot,
    val ordinal: Int,
    val total: Int,
)

data class V2ShiftEditTemplateOption(
    val objective: Objective,
    val workPlace: WorkPlace,
    val workType: WorkType,
    val template: WorkTemplate,
    val matchesHistoricalSelection: Boolean = false,
)

data class V2ShiftEditUiState(
    val stage: V2ShiftEditStage = V2ShiftEditStage.IDLE,
    val timelineId: UUID? = null,
    val date: LocalDate? = null,
    val inspectionState: V2ShiftDayInspectionState = V2ShiftDayInspectionState.IDLE,
    val dayRows: List<V2ShiftEditDayRow> = emptyList(),
    val targetShiftId: UUID? = null,
    val originalWrite: V2ShiftWrite? = null,
    val recurringOccurrence: RecurringOccurrence? = null,
    val templateOptions: List<V2ShiftEditTemplateOption> = emptyList(),
    val selectedTemplateId: UUID? = null,
    val usesHistoricalTemplate: Boolean = true,
    val position: String = "",
    val warnings: List<String> = emptyList(),
    val acknowledgedWarnings: List<String> = emptyList(),
    val reviewFingerprint: String? = null,
    val confirmedPairFingerprint: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val successSequence: Int = 0,
) {
    val isBlocking: Boolean
        get() = stage != V2ShiftEditStage.IDLE

    val hasEditableRows: Boolean
        get() = dayRows.isNotEmpty()

    val selectedOption: V2ShiftEditTemplateOption?
        get() = if (usesHistoricalTemplate) {
            null
        } else {
            templateOptions.firstOrNull { it.template.id == selectedTemplateId }
        }

    val hasUnconfirmedChanges: Boolean
        get() = originalWrite?.let { original ->
            !usesHistoricalTemplate ||
                normalizeOptionalWorkText(position) != original.shift.position
        } == true
}

internal data class V2ShiftEditPersistedState(
    val stage: V2ShiftEditStage = V2ShiftEditStage.IDLE,
    val timelineId: UUID? = null,
    val date: LocalDate? = null,
    val targetShiftId: UUID? = null,
    val selectedTemplateId: UUID? = null,
    val usesHistoricalTemplate: Boolean = true,
    val position: String = "",
    val acknowledgedWarnings: List<String> = emptyList(),
    val reviewFingerprint: String? = null,
    val confirmedPairFingerprint: String? = null,
)

class V2ShiftEditViewModel(
    configurationRepository: WorkConfigurationRepository,
    catalogRepository: WorkCatalogRepository,
    objectiveRepository: ObjectiveRepository,
    shiftRepository: ShiftRepository,
    medicalLeaveRepository: MedicalLeaveRepository,
    v2ShiftRepository: V2ShiftRepository,
    recurringPlanRepository: RecurringPlanRepository,
    clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val coordinator = V2ShiftEditCoordinator(
        configurationRepository = configurationRepository,
        catalogRepository = catalogRepository,
        objectiveRepository = objectiveRepository,
        shiftRepository = shiftRepository,
        medicalLeaveRepository = medicalLeaveRepository,
        v2ShiftRepository = v2ShiftRepository,
        recurringPlanRepository = recurringPlanRepository,
        clock = clock,
        scope = viewModelScope,
        initialPersistedState = savedStateHandle.readV2ShiftEditState(),
        persist = savedStateHandle::writeV2ShiftEditState,
    )

    val uiState: StateFlow<V2ShiftEditUiState> = coordinator.uiState

    fun resume(rootState: WorkSetupState) = coordinator.resume(rootState)
    fun inspectDay(rootState: WorkSetupState, date: LocalDate) = coordinator.inspectDay(rootState, date)
    fun retryInspection() = coordinator.retryInspection()
    fun clearInspection() = coordinator.clearInspection()
    fun beginDayEditing() = coordinator.beginDayEditing()
    fun editShift(id: UUID) = coordinator.editShift(id)
    fun requestDelete(id: UUID) = coordinator.requestDelete(id)
    fun editOnlyThisOccurrence() = coordinator.editOnlyThisOccurrence()
    fun deleteOnlyThisOccurrence() = coordinator.deleteOnlyThisOccurrence()
    fun cancelScopeChoice() = coordinator.cancelScopeChoice()
    fun handoffToRecurring() = coordinator.handoffToRecurring()
    fun chooseHistoricalTemplate() = coordinator.chooseHistoricalTemplate()
    fun chooseTemplate(id: UUID) = coordinator.chooseTemplate(id)
    fun updatePosition(value: String) = coordinator.updatePosition(value)
    fun requestReview() = coordinator.requestReview()
    fun confirmWarnings() = coordinator.confirmWarnings()
    fun dismissWarnings() = coordinator.dismissWarnings()
    fun save() = coordinator.save()
    fun confirmDelete() = coordinator.confirmDelete()
    fun cancelDelete() = coordinator.cancelDelete()
    fun back() = coordinator.back()
    fun confirmDiscard() = coordinator.confirmDiscard()
    fun cancelDiscard() = coordinator.cancelDiscard()
    fun cancelToDetail() = coordinator.cancelToDetail()
    fun retry() = coordinator.retry()
    fun discardIncompatible() = coordinator.discardIncompatible()
    fun clearMessage() = coordinator.clearMessage()
    fun consumeSuccess(sequence: Int) = coordinator.consumeSuccess(sequence)

    class Factory(
        private val configurationRepository: WorkConfigurationRepository,
        private val catalogRepository: WorkCatalogRepository,
        private val objectiveRepository: ObjectiveRepository,
        private val shiftRepository: ShiftRepository,
        private val medicalLeaveRepository: MedicalLeaveRepository,
        private val v2ShiftRepository: V2ShiftRepository,
        private val recurringPlanRepository: RecurringPlanRepository,
        private val clock: Clock = Clock.system(AppDefaults.zoneId()),
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(V2ShiftEditViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return V2ShiftEditViewModel(
                configurationRepository = configurationRepository,
                catalogRepository = catalogRepository,
                objectiveRepository = objectiveRepository,
                shiftRepository = shiftRepository,
                medicalLeaveRepository = medicalLeaveRepository,
                v2ShiftRepository = v2ShiftRepository,
                recurringPlanRepository = recurringPlanRepository,
                clock = clock,
                savedStateHandle = extras.createSavedStateHandle(),
            ) as T
        }
    }
}

internal class V2ShiftEditCoordinator(
    private val configurationRepository: WorkConfigurationRepository,
    private val catalogRepository: WorkCatalogRepository,
    private val objectiveRepository: ObjectiveRepository,
    private val shiftRepository: ShiftRepository,
    private val medicalLeaveRepository: MedicalLeaveRepository,
    private val v2ShiftRepository: V2ShiftRepository,
    private val recurringPlanRepository: RecurringPlanRepository? = null,
    private val clock: Clock,
    private val scope: CoroutineScope,
    initialPersistedState: V2ShiftEditPersistedState = V2ShiftEditPersistedState(),
    private val persist: (V2ShiftEditPersistedState) -> Unit = {},
) {
    private val writeMutex = Mutex()
    private val _uiState = MutableStateFlow(initialPersistedState.toUiState())
    val uiState: StateFlow<V2ShiftEditUiState> = _uiState

    private var inspectionJob: Job? = null
    private var readJob: Job? = null
    private var readyState: WorkSetupState.V2Ready? = null
    private var restorationPending = initialPersistedState.stage != V2ShiftEditStage.IDLE
    private var preparedReview: PreparedEdit? = null
    private var stateEpoch: Long = 0L
    private var incompatibleWhileSaving: Boolean = false

    fun resume(rootState: WorkSetupState) {
        val ready = rootState as? WorkSetupState.V2Ready
        val state = _uiState.value
        if (ready == null || (state.timelineId != null && state.timelineId != ready.timelineId)) {
            readyState = null
            if (state.isSaving) {
                incompatibleWhileSaving = true
            } else if (state.isBlocking) {
                discardIncompatible()
            }
            return
        }
        readyState = ready
        incompatibleWhileSaving = false
        if (restorationPending) {
            restorationPending = false
            launchRead { epoch -> restoreBlockingState(ready, epoch) }
        }
    }

    fun inspectDay(rootState: WorkSetupState, date: LocalDate) {
        val ready = rootState as? WorkSetupState.V2Ready ?: return
        readyState = ready
        if (_uiState.value.isBlocking) return
        if (
            _uiState.value.date == date &&
            _uiState.value.timelineId == ready.timelineId &&
            inspectionJob?.isActive == true
        ) {
            return
        }
        startInspection(ready, date)
    }

    fun retryInspection() {
        val ready = readyState ?: return
        val date = _uiState.value.date ?: return
        if (!_uiState.value.isBlocking) startInspection(ready, date)
    }

    fun clearInspection() {
        if (_uiState.value.isBlocking) return
        inspectionJob?.cancel()
        inspectionJob = null
        _uiState.update {
            V2ShiftEditUiState(
                infoMessage = it.infoMessage,
                successSequence = it.successSequence,
            )
        }
    }

    fun beginDayEditing() {
        val state = _uiState.value
        if (
            state.isBlocking ||
            state.inspectionState != V2ShiftDayInspectionState.CONTENT ||
            !state.hasEditableRows
        ) {
            return
        }
        inspectionJob?.cancel()
        inspectionJob = null
        updateAndPersist {
            it.copy(
                stage = V2ShiftEditStage.DAY_ACTIONS,
                errorMessage = null,
            )
        }
    }

    fun editShift(id: UUID) {
        if (_uiState.value.stage != V2ShiftEditStage.DAY_ACTIONS || _uiState.value.isLoading) return
        launchRead { epoch -> chooseEditScopeOrLoad(id, epoch) }
    }

    fun requestDelete(id: UUID) {
        if (_uiState.value.stage != V2ShiftEditStage.DAY_ACTIONS || _uiState.value.isLoading) return
        launchRead { epoch -> chooseDeleteScopeOrLoad(id, epoch) }
    }

    private suspend fun chooseEditScopeOrLoad(id: UUID, epoch: Long) {
        ensureCurrent(epoch)
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val write = requireTargetWrite(id)
            ensureCurrent(epoch)
            requireCompatibleTarget(write)
            val occurrence = recurringPlanRepository?.getOccurrenceForShift(id)
            ensureCurrent(epoch)
            if (occurrence != null &&
                !write.shift.localStartDate.isBefore(today()) &&
                hasActiveRecurringPlan(occurrence)
            ) {
                updateAndPersist {
                    it.copy(
                        stage = V2ShiftEditStage.CHOOSE_EDIT_SCOPE,
                        targetShiftId = id,
                        originalWrite = write,
                        recurringOccurrence = occurrence,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            } else {
                loadEditor(id, preserveDraft = false, finalStage = V2ShiftEditStage.EDIT_FORM, epoch = epoch)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent(epoch)) {
                showDayActionsError(error.message ?: "No pudimos abrir esta jornada. Reintentá.")
            }
        } finally {
            if (isCurrent(epoch)) _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun chooseDeleteScopeOrLoad(id: UUID, epoch: Long) {
        ensureCurrent(epoch)
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val write = requireTargetWrite(id)
            ensureCurrent(epoch)
            requireCompatibleTarget(write)
            val occurrence = recurringPlanRepository?.getOccurrenceForShift(id)
            ensureCurrent(epoch)
            if (occurrence != null &&
                !write.shift.localStartDate.isBefore(today()) &&
                hasActiveRecurringPlan(occurrence)
            ) {
                updateAndPersist {
                    it.copy(
                        stage = V2ShiftEditStage.CHOOSE_DELETE_SCOPE,
                        targetShiftId = id,
                        originalWrite = write,
                        recurringOccurrence = occurrence,
                        confirmedPairFingerprint = pairFingerprint(write),
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            } else {
                loadDeleteConfirmation(id, epoch)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent(epoch)) {
                showDayActionsError(error.message ?: "No pudimos preparar la eliminación. Reintentá.")
            }
        } finally {
            if (isCurrent(epoch)) _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun editOnlyThisOccurrence() {
        val state = _uiState.value
        val id = state.targetShiftId ?: return
        if (state.stage != V2ShiftEditStage.CHOOSE_EDIT_SCOPE || state.isLoading || state.isSaving) return
        launchRead { epoch ->
            loadEditor(id, preserveDraft = false, finalStage = V2ShiftEditStage.EDIT_FORM, epoch = epoch)
        }
    }

    fun deleteOnlyThisOccurrence() {
        val state = _uiState.value
        if (
            state.stage != V2ShiftEditStage.CHOOSE_DELETE_SCOPE ||
            state.originalWrite == null ||
            state.isLoading ||
            state.isSaving
        ) {
            return
        }
        updateAndPersist { it.copy(stage = V2ShiftEditStage.CONFIRM_DELETE) }
    }

    fun cancelScopeChoice() {
        val state = _uiState.value
        if (state.stage !in setOf(V2ShiftEditStage.CHOOSE_EDIT_SCOPE, V2ShiftEditStage.CHOOSE_DELETE_SCOPE)) return
        returnToDayActions()
    }

    fun handoffToRecurring() {
        val state = _uiState.value
        if (state.stage !in setOf(V2ShiftEditStage.CHOOSE_EDIT_SCOPE, V2ShiftEditStage.CHOOSE_DELETE_SCOPE)) return
        preparedReview = null
        _uiState.value = V2ShiftEditUiState(
            infoMessage = state.infoMessage,
            successSequence = state.successSequence,
        )
        persistCurrentState()
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private suspend fun hasActiveRecurringPlan(occurrence: RecurringOccurrence): Boolean =
        recurringPlanRepository
            ?.getPlan(occurrence.planId)
            ?.latestRevision
            ?.kind == RecurringPlanRevisionKind.ACTIVE

    private suspend fun loadDeleteConfirmation(id: UUID, epoch: Long) {
        ensureCurrent(epoch)
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val write = requireTargetWrite(id)
            ensureCurrent(epoch)
            requireCompatibleTarget(write)
            updateAndPersist {
                it.copy(
                    stage = V2ShiftEditStage.CONFIRM_DELETE,
                    targetShiftId = id,
                    originalWrite = write,
                    selectedTemplateId = null,
                    usesHistoricalTemplate = true,
                    position = "",
                    warnings = emptyList(),
                    acknowledgedWarnings = emptyList(),
                    reviewFingerprint = null,
                    confirmedPairFingerprint = pairFingerprint(write),
                    isLoading = false,
                    errorMessage = null,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent(epoch)) {
                showDayActionsError(error.message ?: "No pudimos preparar la eliminación. Reintentá.")
            }
        } finally {
            if (isCurrent(epoch)) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun chooseHistoricalTemplate() {
        val state = _uiState.value
        if (state.stage != V2ShiftEditStage.EDIT_FORM || state.isLoading || state.isSaving) return
        val originalId = state.originalWrite?.snapshot?.templateId ?: return
        preparedReview = null
        updateAndPersist {
            it.copy(
                selectedTemplateId = originalId,
                usesHistoricalTemplate = true,
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                errorMessage = null,
            )
        }
    }

    fun chooseTemplate(id: UUID) {
        val state = _uiState.value
        if (state.stage != V2ShiftEditStage.EDIT_FORM || state.isLoading || state.isSaving) return
        val option = state.templateOptions.firstOrNull { it.template.id == id } ?: return
        preparedReview = null
        updateAndPersist {
            it.copy(
                selectedTemplateId = id,
                usesHistoricalTemplate = option.matchesHistoricalSelection,
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                errorMessage = null,
            )
        }
    }

    fun updatePosition(value: String) {
        val state = _uiState.value
        if (state.stage != V2ShiftEditStage.EDIT_FORM || state.isLoading || state.isSaving) return
        preparedReview = null
        updateAndPersist {
            it.copy(
                position = value.take(MAX_POSITION_LENGTH),
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                errorMessage = null,
            )
        }
    }

    fun requestReview() {
        val state = _uiState.value
        if (
            state.stage != V2ShiftEditStage.EDIT_FORM ||
            state.isLoading ||
            state.isSaving ||
            !state.hasUnconfirmedChanges
        ) {
            return
        }
        launchRead { epoch -> prepareReview(epoch = epoch) }
    }

    fun confirmWarnings() {
        val state = _uiState.value
        if (state.stage != V2ShiftEditStage.CONFIRM_WARNINGS || state.isLoading || state.isSaving) return
        updateAndPersist {
            it.copy(
                stage = V2ShiftEditStage.EDIT_FORM,
                acknowledgedWarnings = state.warnings,
                errorMessage = null,
            )
        }
        launchRead { epoch -> prepareReview(epoch = epoch) }
    }

    fun dismissWarnings() {
        if (_uiState.value.stage != V2ShiftEditStage.CONFIRM_WARNINGS || _uiState.value.isSaving) return
        preparedReview = null
        updateAndPersist {
            it.copy(
                stage = V2ShiftEditStage.EDIT_FORM,
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                errorMessage = null,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        val prepared = preparedReview
        if (
            state.stage != V2ShiftEditStage.REVIEW ||
            state.isLoading ||
            state.isSaving ||
            prepared == null ||
            prepared.fingerprint != state.reviewFingerprint
        ) {
            return
        }
        val epoch = stateEpoch
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (!writeMutex.tryLock()) return@launch
            if (!isCurrent(epoch)) {
                writeMutex.unlock()
                return@launch
            }
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            if (!isCurrent(epoch)) {
                _uiState.update { it.copy(isSaving = false) }
                writeMutex.unlock()
                discardIncompatible()
                return@launch
            }
            try {
                v2ShiftRepository.applyV2Batch(
                    mutation = prepared.mutation,
                    expectedOccupancy = prepared.expectedOccupancy,
                    expectedUpdates = V2ShiftWriteExpectation.capture(listOf(prepared.original)),
                )
                finishSuccess("Jornada actualizada.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: ConflictingLocalWriteException) {
                if (!incompatibleWhileSaving) recoverEditorAfterConflict(error.message, epoch)
            } catch (error: Exception) {
                if (!incompatibleWhileSaving) {
                    showError(error.message ?: "No pudimos guardar los cambios. El borrador sigue disponible.")
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
                writeMutex.unlock()
                finishDeferredIncompatibility()
            }
        }
    }

    fun confirmDelete() {
        val state = _uiState.value
        val expected = state.originalWrite
        if (
            state.stage != V2ShiftEditStage.CONFIRM_DELETE ||
            state.isLoading ||
            state.isSaving ||
            expected == null ||
            pairFingerprint(expected) != state.confirmedPairFingerprint
        ) {
            return
        }
        val epoch = stateEpoch
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (!writeMutex.tryLock()) return@launch
            if (!isCurrent(epoch)) {
                writeMutex.unlock()
                return@launch
            }
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            if (!isCurrent(epoch)) {
                _uiState.update { it.copy(isSaving = false) }
                writeMutex.unlock()
                discardIncompatible()
                return@launch
            }
            try {
                v2ShiftRepository.deleteShift(expected)
                finishSuccess("Jornada eliminada.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: ConflictingLocalWriteException) {
                if (!incompatibleWhileSaving) {
                    reloadDayActions(
                        error.message ?: "La jornada cambió. Revisala antes de eliminar.",
                        epoch,
                    )
                }
            } catch (error: Exception) {
                if (!incompatibleWhileSaving) {
                    showError(error.message ?: "No pudimos eliminar la jornada. Reintentá.")
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
                writeMutex.unlock()
                finishDeferredIncompatibility()
            }
        }
    }

    fun cancelDelete() {
        if (_uiState.value.stage != V2ShiftEditStage.CONFIRM_DELETE || _uiState.value.isSaving) return
        updateAndPersist {
            it.copy(
                stage = V2ShiftEditStage.DAY_ACTIONS,
                targetShiftId = null,
                originalWrite = null,
                recurringOccurrence = null,
                confirmedPairFingerprint = null,
                errorMessage = null,
            )
        }
    }

    fun back() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        when (state.stage) {
            V2ShiftEditStage.IDLE -> Unit
            V2ShiftEditStage.DAY_ACTIONS -> cancelToDetail()
            V2ShiftEditStage.CHOOSE_EDIT_SCOPE,
            V2ShiftEditStage.CHOOSE_DELETE_SCOPE,
            -> cancelScopeChoice()
            V2ShiftEditStage.EDIT_FORM -> {
                if (state.hasUnconfirmedChanges) {
                    updateAndPersist { it.copy(stage = V2ShiftEditStage.CONFIRM_DISCARD) }
                } else {
                    returnToDayActions()
                }
            }
            V2ShiftEditStage.CONFIRM_WARNINGS,
            V2ShiftEditStage.REVIEW,
            -> {
                preparedReview = null
                updateAndPersist {
                    it.copy(
                        stage = V2ShiftEditStage.EDIT_FORM,
                        warnings = emptyList(),
                        acknowledgedWarnings = emptyList(),
                        reviewFingerprint = null,
                        errorMessage = null,
                    )
                }
            }
            V2ShiftEditStage.CONFIRM_DELETE -> cancelDelete()
            V2ShiftEditStage.CONFIRM_DISCARD -> cancelDiscard()
        }
    }

    fun confirmDiscard() {
        if (_uiState.value.stage != V2ShiftEditStage.CONFIRM_DISCARD || _uiState.value.isSaving) return
        returnToDayActions()
    }

    fun cancelDiscard() {
        if (_uiState.value.stage != V2ShiftEditStage.CONFIRM_DISCARD || _uiState.value.isSaving) return
        updateAndPersist { it.copy(stage = V2ShiftEditStage.EDIT_FORM) }
    }

    fun cancelToDetail() {
        val state = _uiState.value
        if (state.stage != V2ShiftEditStage.DAY_ACTIONS || state.isLoading || state.isSaving) return
        val ready = readyState
        val date = state.date
        preparedReview = null
        _uiState.value = state.copy(
            stage = V2ShiftEditStage.IDLE,
            targetShiftId = null,
            originalWrite = null,
            recurringOccurrence = null,
            templateOptions = emptyList(),
            selectedTemplateId = null,
            usesHistoricalTemplate = true,
            position = "",
            warnings = emptyList(),
            acknowledgedWarnings = emptyList(),
            reviewFingerprint = null,
            confirmedPairFingerprint = null,
            errorMessage = null,
        )
        persistCurrentState()
        if (ready != null && date != null) startInspection(ready, date, showLoading = false)
    }

    fun retry() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        when (state.stage) {
            V2ShiftEditStage.IDLE -> retryInspection()
            V2ShiftEditStage.DAY_ACTIONS -> launchRead { epoch -> reloadDayActions(epoch = epoch) }
            V2ShiftEditStage.CHOOSE_EDIT_SCOPE -> state.targetShiftId?.let { id ->
                launchRead { epoch -> chooseEditScopeOrLoad(id, epoch) }
            }
            V2ShiftEditStage.CHOOSE_DELETE_SCOPE -> state.targetShiftId?.let { id ->
                launchRead { epoch -> chooseDeleteScopeOrLoad(id, epoch) }
            }
            V2ShiftEditStage.EDIT_FORM -> state.targetShiftId?.let { id ->
                launchRead { epoch ->
                    loadEditor(id, preserveDraft = true, finalStage = V2ShiftEditStage.EDIT_FORM, epoch = epoch)
                }
            }
            V2ShiftEditStage.CONFIRM_WARNINGS,
            V2ShiftEditStage.REVIEW,
            -> launchRead { epoch -> prepareReview(epoch = epoch) }
            V2ShiftEditStage.CONFIRM_DELETE -> state.targetShiftId?.let { id ->
                launchRead { epoch -> loadDeleteConfirmation(id, epoch) }
            }
            V2ShiftEditStage.CONFIRM_DISCARD -> Unit
        }
    }

    fun discardIncompatible() {
        if (_uiState.value.isSaving) {
            readyState = null
            incompatibleWhileSaving = true
            return
        }
        readyState = null
        stateEpoch++
        readJob?.cancel()
        readJob = null
        inspectionJob?.cancel()
        inspectionJob = null
        preparedReview = null
        restorationPending = false
        incompatibleWhileSaving = false
        val nextSequence = _uiState.value.successSequence
        _uiState.value = V2ShiftEditUiState(
            infoMessage = "La edición anterior ya no coincide con tu forma de trabajar actual. No se guardó ningún cambio.",
            successSequence = nextSequence,
        )
        persistCurrentState()
    }

    fun clearMessage() = _uiState.update { it.copy(infoMessage = null, errorMessage = null) }

    fun consumeSuccess(sequence: Int) {
        if (sequence <= 0) return
        _uiState.update { state ->
            if (state.successSequence == sequence) state.copy(successSequence = 0) else state
        }
    }

    private fun launchRead(block: suspend (Long) -> Unit) {
        readJob?.cancel()
        val epoch = ++stateEpoch
        readJob = scope.launch { block(epoch) }
    }

    private fun isCurrent(epoch: Long): Boolean = epoch == stateEpoch

    private fun ensureCurrent(epoch: Long) {
        if (!isCurrent(epoch)) throw CancellationException("La operación de edición dejó de estar vigente.")
    }

    private fun finishDeferredIncompatibility() {
        if (!incompatibleWhileSaving) return
        incompatibleWhileSaving = false
        if (_uiState.value.isBlocking) discardIncompatible()
    }

    private fun startInspection(
        ready: WorkSetupState.V2Ready,
        date: LocalDate,
        showLoading: Boolean = true,
    ) {
        inspectionJob?.cancel()
        if (showLoading) {
            _uiState.update {
                it.copy(
                    timelineId = ready.timelineId,
                    date = date,
                    inspectionState = V2ShiftDayInspectionState.LOADING,
                    dayRows = emptyList(),
                    errorMessage = null,
                )
            }
        }
        inspectionJob = scope.launch {
            shiftRepository.observeStartingBetween(date, date)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            inspectionState = V2ShiftDayInspectionState.ERROR,
                            dayRows = emptyList(),
                            errorMessage = error.message ?: "No pudimos identificar las jornadas de este día.",
                        )
                    }
                }
                .collect { shifts ->
                    try {
                        val rows = loadRows(date, shifts)
                        _uiState.update {
                            if (it.isBlocking) it else it.copy(
                                timelineId = ready.timelineId,
                                date = date,
                                inspectionState = V2ShiftDayInspectionState.CONTENT,
                                dayRows = rows,
                                errorMessage = null,
                            )
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        _uiState.update {
                            if (it.isBlocking) it else it.copy(
                                inspectionState = V2ShiftDayInspectionState.ERROR,
                                dayRows = emptyList(),
                                errorMessage = error.message ?: "No pudimos identificar las jornadas de este día.",
                            )
                        }
                    }
                }
        }
    }

    private suspend fun loadRows(date: LocalDate, source: List<Shift>? = null): List<V2ShiftEditDayRow> {
        val shifts = source ?: shiftRepository.observeStartingBetween(date, date).first()
        val sorted = shifts.sortedWith(compareBy<Shift> { it.startAt }.thenBy { it.endAt }.thenBy { it.id })
        return sorted.mapIndexed { index, shift ->
            when (val lookup = v2ShiftRepository.getShift(shift.id)) {
                V2ShiftLookup.Missing -> throw IllegalStateException(
                    "Las jornadas cambiaron mientras se abría el detalle. Reintentá.",
                )
                is V2ShiftLookup.V2 -> {
                    require(lookup.write.shift.localStartDate == date) {
                        "La jornada ya no pertenece a este día."
                    }
                    V2ShiftEditDayRow(
                        shift = lookup.write.shift,
                        snapshot = lookup.write.snapshot,
                        ordinal = index + 1,
                        total = sorted.size,
                    )
                }
            }
        }
    }

    private suspend fun loadEditor(
        id: UUID,
        preserveDraft: Boolean,
        finalStage: V2ShiftEditStage,
        epoch: Long,
    ) {
        ensureCurrent(epoch)
        val previous = _uiState.value
        _uiState.update {
            it.copy(
                stage = V2ShiftEditStage.EDIT_FORM,
                targetShiftId = id,
                isLoading = true,
                errorMessage = null,
            )
        }
        persistCurrentState()
        try {
            val write = requireTargetWrite(id)
            ensureCurrent(epoch)
            requireCompatibleTarget(write)
            val context = loadEditorContext(write)
            ensureCurrent(epoch)
            val selectedTemplateId = if (preserveDraft) {
                previous.selectedTemplateId ?: write.snapshot.templateId
            } else {
                write.snapshot.templateId
            }
            val selectedActiveOption = context.options.firstOrNull { it.template.id == selectedTemplateId }
            val usesHistoricalTemplate = if (preserveDraft) {
                previous.usesHistoricalTemplate || selectedActiveOption?.matchesHistoricalSelection == true
            } else {
                true
            }
            val unavailableDraft = preserveDraft && !usesHistoricalTemplate && selectedActiveOption == null
            updateAndPersist {
                it.copy(
                    stage = finalStage,
                    timelineId = write.snapshot.timelineId,
                    date = write.shift.localStartDate,
                    targetShiftId = write.shift.id,
                    originalWrite = write,
                    templateOptions = context.options,
                    selectedTemplateId = selectedTemplateId,
                    usesHistoricalTemplate = usesHistoricalTemplate,
                    position = if (preserveDraft) previous.position else write.shift.position.orEmpty(),
                    warnings = emptyList(),
                    acknowledgedWarnings = if (preserveDraft) previous.acknowledgedWarnings else emptyList(),
                    reviewFingerprint = null,
                    confirmedPairFingerprint = null,
                    isLoading = false,
                    errorMessage = if (unavailableDraft) {
                        "La plantilla elegida ya no está activa. Conservamos el puesto; elegí otra opción."
                    } else {
                        null
                    },
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent(epoch)) {
                showDayActionsError(error.message ?: "No pudimos abrir esta jornada. Reintentá.")
            }
        } finally {
            if (isCurrent(epoch)) _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun prepareReview(
        expectedRestoredFingerprint: String? = null,
        requireRestoredFingerprintMatch: Boolean = false,
        epoch: Long,
    ) {
        ensureCurrent(epoch)
        val initial = _uiState.value
        val targetId = initial.targetShiftId ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val current = requireTargetWrite(targetId)
            ensureCurrent(epoch)
            requireCompatibleTarget(current)
            if (initial.originalWrite != current) {
                loadEditor(
                    targetId,
                    preserveDraft = true,
                    finalStage = V2ShiftEditStage.EDIT_FORM,
                    epoch = epoch,
                )
                ensureCurrent(epoch)
                showReviewInvalidated(
                    "La jornada cambió mientras la editabas. Conservamos tu borrador para que revises la versión actual.",
                )
                return
            }
            val context = loadEditorContext(current)
            ensureCurrent(epoch)
            val normalizedPosition = normalizeOptionalWorkText(initial.position)
            val candidate = if (initial.usesHistoricalTemplate) {
                editV2ShiftPositionOnly(
                    original = current,
                    position = normalizedPosition,
                    updatedAt = clock.instant(),
                )
            } else {
                val option = context.options.firstOrNull { it.template.id == initial.selectedTemplateId }
                    ?: throw IllegalStateException(
                        "El horario elegido ya no está activo. Conservamos el borrador para que elijas otro.",
                    )
                editV2ShiftWrite(
                    original = current,
                    date = current.shift.localStartDate,
                    objective = option.objective,
                    workPlace = option.workPlace,
                    workType = option.workType,
                    template = option.template,
                    configurationContext = context.configuration,
                    position = normalizedPosition,
                    updatedAt = clock.instant(),
                )
            }
            val date = current.shift.localStartDate
            val startDate = date.minusDays(2)
            val endDate = date.plusDays(2)
            val existing = shiftRepository.observeStartingBetween(startDate, endDate).first()
            ensureCurrent(epoch)
            val plan = planV2ShiftBatch(
                selectedDates = setOf(date),
                existingShifts = existing,
                candidates = listOf(candidate),
                policy = OccupiedDatePolicy.ADD_SECOND_SHIFT,
                editingShiftId = current.shift.id,
            )
            require(plan.mutation.shiftsToUpdate.singleOrNull() == candidate) {
                "La edición debe actualizar exactamente una jornada."
            }
            require(plan.mutation.shiftIdsToDelete.isEmpty() && plan.mutation.explicitDayStatusDatesToClear.isEmpty()) {
                "La edición no puede borrar jornadas ni cambiar estados del día."
            }
            val medicalWarnings = medicalLeaveRepository.observeIntersecting(date, date).first().map { leave ->
                "Existe una carpeta médica entre ${leave.startDate.format(DATE_FORMATTER)} y " +
                    "${leave.endDateInclusive.format(DATE_FORMATTER)}. No se modificará."
            }
            ensureCurrent(epoch)
            val warnings = plan.warnings.map(::warningText) + medicalWarnings
            val expectation = ShiftOccupancyExpectation.capture(startDate, endDate, existing)
            val prepared = PreparedEdit(
                original = current,
                mutation = plan.mutation,
                expectedOccupancy = expectation,
                warnings = warnings,
                fingerprint = reviewFingerprint(current, candidate, expectation, warnings),
            )
            if (requireRestoredFingerprintMatch && expectedRestoredFingerprint != prepared.fingerprint) {
                preparedReview = null
                updateAndPersist {
                    it.copy(
                        stage = V2ShiftEditStage.EDIT_FORM,
                        originalWrite = current,
                        templateOptions = context.options,
                        warnings = emptyList(),
                        acknowledgedWarnings = emptyList(),
                        reviewFingerprint = null,
                        isLoading = false,
                        errorMessage = "La jornada o sus advertencias cambiaron durante la pausa. Revisá nuevamente.",
                    )
                }
                return
            }
            if (warnings.isNotEmpty() && warnings != initial.acknowledgedWarnings) {
                preparedReview = null
                updateAndPersist {
                    it.copy(
                        stage = V2ShiftEditStage.CONFIRM_WARNINGS,
                        originalWrite = current,
                        templateOptions = context.options,
                        warnings = warnings,
                        reviewFingerprint = null,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                return
            }
            preparedReview = prepared
            updateAndPersist {
                it.copy(
                    stage = V2ShiftEditStage.REVIEW,
                    originalWrite = current,
                    templateOptions = context.options,
                    warnings = warnings,
                    reviewFingerprint = prepared.fingerprint,
                    isLoading = false,
                    errorMessage = null,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent(epoch)) {
                preparedReview = null
                showError(error.message ?: "No pudimos preparar la revisión. Reintentá.")
            }
        } finally {
            if (isCurrent(epoch)) _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadEditorContext(write: V2ShiftWrite): LoadedEditorContext {
        val history = configurationRepository.get()
            ?: throw IllegalStateException("No pudimos encontrar la configuración laboral.")
        if (history.timeline.id != write.snapshot.timelineId || history.timeline.id != _uiState.value.timelineId) {
            throw IllegalStateException("La forma de trabajar cambió. Cerrá el editor y volvé a empezar.")
        }
        val configuration = ResolvedWorkConfigurationRevision.resolve(history, write.shift.localStartDate)
        val options = if (configuration.revision.value.sector == write.snapshot.sector) {
            loadActiveOptions(history, write, configuration)
        } else {
            emptyList()
        }
        return LoadedEditorContext(history, configuration, options)
    }

    private suspend fun loadActiveOptions(
        history: WorkConfigurationHistory,
        write: V2ShiftWrite,
        configuration: ResolvedWorkConfigurationRevision,
    ): List<V2ShiftEditTemplateOption> {
        val catalog = catalogRepository.observeCatalog(history.timeline.id, write.snapshot.sector).first()
        val places = catalog.workPlaces.filter(WorkPlace::isActive).associateBy(WorkPlace::id)
        val types = catalog.workTypes.filter(WorkType::isActive).associateBy(WorkType::id)
        val templates = catalog.workTemplates.filter { template ->
            template.isActive &&
                places[template.workPlaceId] != null &&
                types[template.workTypeId] != null &&
                catalog.ruleRevisionAt(template.workPlaceId, write.shift.localStartDate) != null
        }
        val objectives = templates
            .map { template -> requireNotNull(places[template.workPlaceId]).objectiveId }
            .distinct()
            .associateWith { objectiveId ->
                objectiveRepository.getById(objectiveId)
                    ?: throw IllegalStateException("Un lugar guardado perdió su información visible.")
            }
        return templates.map { template ->
            val place = requireNotNull(places[template.workPlaceId])
            V2ShiftEditTemplateOption(
                objective = requireNotNull(objectives[place.objectiveId]),
                workPlace = place,
                workType = requireNotNull(types[template.workTypeId]),
                template = template,
                matchesHistoricalSelection = activeSelectionMatchesHistorical(
                    original = write,
                    configuration = configuration,
                    objective = requireNotNull(objectives[place.objectiveId]),
                    workPlace = place,
                    workType = requireNotNull(types[template.workTypeId]),
                    template = template,
                ),
            )
        }.sortedWith(
            compareBy<V2ShiftEditTemplateOption> { it.objective.abbreviation }
                .thenBy { it.workType.name }
                .thenBy { it.template.startTime }
                .thenBy { it.template.endTime },
        )
    }

    private fun activeSelectionMatchesHistorical(
        original: V2ShiftWrite,
        configuration: ResolvedWorkConfigurationRevision,
        objective: Objective,
        workPlace: WorkPlace,
        workType: WorkType,
        template: WorkTemplate,
    ): Boolean {
        val rebuilt = buildV2ShiftWrite(
            id = original.shift.id,
            date = original.shift.localStartDate,
            objective = objective,
            workPlace = workPlace,
            workType = workType,
            template = template,
            configurationContext = configuration,
            position = original.shift.position,
            timestamp = original.shift.createdAt,
            zoneId = original.shift.zoneId,
        )
        return rebuilt.copy(
            shift = rebuilt.shift.copy(
                status = original.shift.status,
                createdAt = original.shift.createdAt,
                updatedAt = original.shift.updatedAt,
            ),
        ) == original
    }

    private suspend fun restoreBlockingState(ready: WorkSetupState.V2Ready, epoch: Long) {
        ensureCurrent(epoch)
        val restored = _uiState.value
        val date = restored.date ?: run {
            discardIncompatible()
            return
        }
        try {
            val restoredRows = loadRows(date)
            ensureCurrent(epoch)
            updateAndPersist {
                it.copy(
                    timelineId = ready.timelineId,
                    inspectionState = V2ShiftDayInspectionState.CONTENT,
                    dayRows = restoredRows,
                )
            }
            when (restored.stage) {
                V2ShiftEditStage.IDLE -> Unit
                V2ShiftEditStage.DAY_ACTIONS -> {
                    updateAndPersist {
                        it.copy(
                            timelineId = ready.timelineId,
                            inspectionState = V2ShiftDayInspectionState.CONTENT,
                            dayRows = restoredRows,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
                V2ShiftEditStage.CHOOSE_EDIT_SCOPE -> {
                    val target = restored.targetShiftId ?: throw IllegalStateException("No existe la jornada restaurada.")
                    chooseEditScopeOrLoad(target, epoch)
                }
                V2ShiftEditStage.CHOOSE_DELETE_SCOPE -> {
                    val target = restored.targetShiftId ?: throw IllegalStateException("No existe la jornada restaurada.")
                    chooseDeleteScopeOrLoad(target, epoch)
                }
                V2ShiftEditStage.EDIT_FORM,
                V2ShiftEditStage.CONFIRM_DISCARD,
                -> {
                    val target = restored.targetShiftId ?: throw IllegalStateException("No existe la jornada restaurada.")
                    loadEditor(target, preserveDraft = true, finalStage = restored.stage, epoch = epoch)
                }
                V2ShiftEditStage.CONFIRM_WARNINGS,
                V2ShiftEditStage.REVIEW,
                -> {
                    val target = restored.targetShiftId ?: throw IllegalStateException("No existe la jornada restaurada.")
                    loadEditor(
                        target,
                        preserveDraft = true,
                        finalStage = V2ShiftEditStage.EDIT_FORM,
                        epoch = epoch,
                    )
                    prepareReview(
                        expectedRestoredFingerprint = restored.reviewFingerprint,
                        requireRestoredFingerprintMatch = restored.stage == V2ShiftEditStage.REVIEW,
                        epoch = epoch,
                    )
                }
                V2ShiftEditStage.CONFIRM_DELETE -> {
                    val target = restored.targetShiftId ?: throw IllegalStateException("No existe la jornada restaurada.")
                    val current = requireTargetWrite(target)
                    ensureCurrent(epoch)
                    requireCompatibleTarget(current)
                    if (pairFingerprint(current) != restored.confirmedPairFingerprint) {
                        reloadDayActions(
                            "La jornada cambió durante la pausa. Revisala nuevamente antes de eliminar.",
                            epoch,
                        )
                    } else {
                        updateAndPersist {
                            it.copy(
                                originalWrite = current,
                                inspectionState = V2ShiftDayInspectionState.CONTENT,
                                isLoading = false,
                                errorMessage = null,
                            )
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent(epoch)) {
                reloadDayActions(
                    error.message ?: "No pudimos restaurar la edición. Revisá el día nuevamente.",
                    epoch,
                )
            }
        }
    }

    private suspend fun requireTargetWrite(id: UUID): V2ShiftWrite = when (val lookup = v2ShiftRepository.getShift(id)) {
        V2ShiftLookup.Missing -> throw IllegalStateException("La jornada ya no existe.")
        is V2ShiftLookup.V2 -> lookup.write
    }

    private fun requireCompatibleTarget(write: V2ShiftWrite) {
        val ready = readyState ?: throw IllegalStateException("La forma de trabajar todavía no está lista.")
        val date = _uiState.value.date ?: write.shift.localStartDate
        require(write.snapshot.timelineId == ready.timelineId && write.shift.localStartDate == date) {
            "La jornada ya no coincide con este día o esta forma de trabajar."
        }
    }

    private suspend fun reloadDayActions(message: String? = null, epoch: Long) {
        ensureCurrent(epoch)
        val date = _uiState.value.date ?: return
        try {
            val rows = loadRows(date)
            ensureCurrent(epoch)
            preparedReview = null
            updateAndPersist {
                it.copy(
                    stage = V2ShiftEditStage.DAY_ACTIONS,
                    inspectionState = V2ShiftDayInspectionState.CONTENT,
                    dayRows = rows,
                    targetShiftId = null,
                    originalWrite = null,
                    recurringOccurrence = null,
                    templateOptions = emptyList(),
                    selectedTemplateId = null,
                    usesHistoricalTemplate = true,
                    position = "",
                    warnings = emptyList(),
                    acknowledgedWarnings = emptyList(),
                    reviewFingerprint = null,
                    confirmedPairFingerprint = null,
                    isLoading = false,
                    errorMessage = message,
                )
            }
        } catch (error: Exception) {
            if (isCurrent(epoch)) {
                showDayActionsError(message ?: error.message ?: "No pudimos releer las jornadas. Reintentá.")
            }
        }
    }

    private fun returnToDayActions() {
        preparedReview = null
        updateAndPersist {
            it.copy(
                stage = V2ShiftEditStage.DAY_ACTIONS,
                targetShiftId = null,
                originalWrite = null,
                recurringOccurrence = null,
                templateOptions = emptyList(),
                selectedTemplateId = null,
                usesHistoricalTemplate = true,
                position = "",
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                confirmedPairFingerprint = null,
                errorMessage = null,
            )
        }
    }

    private suspend fun recoverEditorAfterConflict(message: String?, epoch: Long) {
        val id = _uiState.value.targetShiftId ?: return
        preparedReview = null
        loadEditor(id, preserveDraft = true, finalStage = V2ShiftEditStage.EDIT_FORM, epoch = epoch)
        ensureCurrent(epoch)
        showReviewInvalidated(message ?: "La jornada cambió mientras revisabas. Revisá nuevamente antes de guardar.")
    }

    private fun showReviewInvalidated(message: String) {
        preparedReview = null
        updateAndPersist {
            it.copy(
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                isLoading = false,
                isSaving = false,
                errorMessage = message,
            )
        }
    }

    private fun finishSuccess(message: String) {
        stateEpoch++
        readJob?.cancel()
        readJob = null
        inspectionJob?.cancel()
        inspectionJob = null
        preparedReview = null
        incompatibleWhileSaving = false
        val nextSequence = _uiState.value.successSequence + 1
        _uiState.value = V2ShiftEditUiState(
            infoMessage = message,
            successSequence = nextSequence,
        )
        persistCurrentState()
    }

    private fun showDayActionsError(message: String) {
        preparedReview = null
        updateAndPersist {
            it.copy(
                stage = V2ShiftEditStage.DAY_ACTIONS,
                targetShiftId = null,
                originalWrite = null,
                recurringOccurrence = null,
                templateOptions = emptyList(),
                selectedTemplateId = null,
                usesHistoricalTemplate = true,
                position = "",
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                confirmedPairFingerprint = null,
                isLoading = false,
                isSaving = false,
                errorMessage = message,
            )
        }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(isLoading = false, isSaving = false, errorMessage = message) }
        persistCurrentState()
    }

    private fun warningText(warning: ShiftPlanningWarning): String = when (warning) {
        is ShiftPlanningWarning.SameDate ->
            "El ${warning.first.localStartDate.format(DATE_FORMATTER)} seguirá teniendo más de una jornada " +
                "(${warning.first.timeRange()} y ${warning.second.timeRange()})."
        is ShiftPlanningWarning.Overlap ->
            "Las jornadas del ${warning.first.localStartDate.format(DATE_FORMATTER)} ${warning.first.timeRange()} " +
                "y del ${warning.second.localStartDate.format(DATE_FORMATTER)} ${warning.second.timeRange()} se superponen."
        is ShiftPlanningWarning.ShortRest -> {
            val totalMinutes = warning.actualRest.toMinutes().coerceAtLeast(0)
            "Entre ${warning.first.localStartDate.format(DATE_FORMATTER)} ${warning.first.timeRange()} y " +
                "${warning.second.localStartDate.format(DATE_FORMATTER)} ${warning.second.timeRange()} hay " +
                "${totalMinutes / 60} h ${totalMinutes % 60} min de descanso."
        }
    }

    private fun Shift.timeRange(): String =
        "${startTimeSnapshot.format(TIME_FORMATTER)}–${endTimeSnapshot.format(TIME_FORMATTER)}"

    private fun reviewFingerprint(
        original: V2ShiftWrite,
        candidate: V2ShiftWrite,
        occupancy: ShiftOccupancyExpectation,
        warnings: List<String>,
    ): String = sha256(
        listOf(
            pairFingerprint(original),
            pairFingerprint(candidate),
            occupancyFingerprint(occupancy),
            warnings.joinToString("|"),
        ).joinToString(";"),
    )

    private fun occupancyFingerprint(expectation: ShiftOccupancyExpectation): String = sha256(
        buildString {
            append(expectation.startDateInclusive)
            append('|')
            append(expectation.endDateInclusive)
            expectation.observedShifts
                .sortedWith(compareBy({ it.localStartDate }, { it.startAt }, { it.endAt }, { it.shiftId }))
                .forEach { version ->
                    append(';')
                    append(version.shiftId)
                    append('|')
                    append(version.localStartDate)
                    append('|')
                    append(version.startAt)
                    append('|')
                    append(version.endAt)
                    append('|')
                    append(version.status)
                    append('|')
                    append(version.updatedAt)
                }
        },
    )

    private fun pairFingerprint(write: V2ShiftWrite): String = sha256(
        encodeFingerprintFields(
            listOf(
                write.shift.id,
                write.shift.startAt,
                write.shift.endAt,
                write.shift.zoneId,
                write.shift.localStartDate,
                write.shift.objectiveNameSnapshot,
                write.shift.objectiveAbbreviationSnapshot,
                write.shift.objectiveAddressSnapshot,
                write.shift.startTimeSnapshot,
                write.shift.endTimeSnapshot,
                write.shift.colorArgbSnapshot,
                write.shift.position,
                write.shift.status,
                write.shift.sourceObjectiveId,
                write.shift.createdAt,
                write.shift.updatedAt,
                write.snapshot.shiftId,
                write.snapshot.timelineId,
                write.snapshot.sector,
                write.snapshot.configurationRevisionId,
                write.snapshot.workPlaceId,
                write.snapshot.objectiveId,
                write.snapshot.templateId,
                write.snapshot.workTypeId,
                write.snapshot.workTypeNameSnapshot,
                write.snapshot.workTypeBehaviorSnapshot,
            ),
        ),
    )

    private fun encodeFingerprintFields(values: Iterable<Any?>): String = buildString {
        values.forEach { value ->
            val encoded = value?.toString()
            if (encoded == null) {
                append("-1:")
            } else {
                append(encoded.length)
                append(':')
                append(encoded)
            }
            append(';')
        }
    }

    private fun sha256(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun updateAndPersist(transform: (V2ShiftEditUiState) -> V2ShiftEditUiState) {
        _uiState.update(transform)
        persistCurrentState()
    }

    private fun persistCurrentState() = persist(_uiState.value.toPersistedState())

    private data class LoadedEditorContext(
        val history: WorkConfigurationHistory,
        val configuration: ResolvedWorkConfigurationRevision,
        val options: List<V2ShiftEditTemplateOption>,
    )

    private data class PreparedEdit(
        val original: V2ShiftWrite,
        val mutation: com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation,
        val expectedOccupancy: ShiftOccupancyExpectation,
        val warnings: List<String>,
        val fingerprint: String,
    )

    private companion object {
        const val MAX_POSITION_LENGTH = 120
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

private fun V2ShiftEditPersistedState.toUiState(): V2ShiftEditUiState = V2ShiftEditUiState(
    stage = stage,
    timelineId = timelineId,
    date = date,
    targetShiftId = targetShiftId,
    selectedTemplateId = selectedTemplateId,
    usesHistoricalTemplate = usesHistoricalTemplate,
    position = position,
    acknowledgedWarnings = acknowledgedWarnings,
    reviewFingerprint = reviewFingerprint,
    confirmedPairFingerprint = confirmedPairFingerprint,
    isLoading = stage != V2ShiftEditStage.IDLE,
)

private fun V2ShiftEditUiState.toPersistedState(): V2ShiftEditPersistedState = V2ShiftEditPersistedState(
    stage = stage,
    timelineId = timelineId,
    date = date,
    targetShiftId = targetShiftId,
    selectedTemplateId = selectedTemplateId,
    usesHistoricalTemplate = usesHistoricalTemplate,
    position = position,
    acknowledgedWarnings = acknowledgedWarnings,
    reviewFingerprint = reviewFingerprint,
    confirmedPairFingerprint = confirmedPairFingerprint,
)

internal fun SavedStateHandle.readV2ShiftEditState(): V2ShiftEditPersistedState = V2ShiftEditPersistedState(
    stage = get<String>(KEY_STAGE)?.let { stored ->
        V2ShiftEditStage.entries.firstOrNull { it.name == stored }
    } ?: V2ShiftEditStage.IDLE,
    timelineId = get<String>(KEY_TIMELINE_ID)?.toUuidOrNullForV2Edit(),
    date = get<String>(KEY_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    targetShiftId = get<String>(KEY_TARGET_SHIFT_ID)?.toUuidOrNullForV2Edit(),
    selectedTemplateId = get<String>(KEY_TEMPLATE_ID)?.toUuidOrNullForV2Edit(),
    usesHistoricalTemplate = get<Boolean>(KEY_USES_HISTORICAL_TEMPLATE) ?: true,
    position = get<String>(KEY_POSITION).orEmpty(),
    acknowledgedWarnings = get<ArrayList<String>>(KEY_ACKNOWLEDGED_WARNINGS).orEmpty(),
    reviewFingerprint = get<String>(KEY_REVIEW_FINGERPRINT),
    confirmedPairFingerprint = get<String>(KEY_CONFIRMED_PAIR_FINGERPRINT),
)

internal fun SavedStateHandle.writeV2ShiftEditState(state: V2ShiftEditPersistedState) {
    if (state.stage == V2ShiftEditStage.IDLE) {
        EDIT_STATE_KEYS.forEach { key -> remove<Any>(key) }
        return
    }
    this[KEY_STAGE] = state.stage.name
    this[KEY_TIMELINE_ID] = state.timelineId?.toString()
    this[KEY_DATE] = state.date?.toString()
    this[KEY_TARGET_SHIFT_ID] = state.targetShiftId?.toString()
    this[KEY_TEMPLATE_ID] = state.selectedTemplateId?.toString()
    this[KEY_USES_HISTORICAL_TEMPLATE] = state.usesHistoricalTemplate
    this[KEY_POSITION] = state.position
    this[KEY_ACKNOWLEDGED_WARNINGS] = ArrayList(state.acknowledgedWarnings)
    this[KEY_REVIEW_FINGERPRINT] = state.reviewFingerprint
    this[KEY_CONFIRMED_PAIR_FINGERPRINT] = state.confirmedPairFingerprint
}

private fun String.toUuidOrNullForV2Edit(): UUID? = runCatching(UUID::fromString).getOrNull()

private const val KEY_STAGE = "v2_shift_edit.stage"
private const val KEY_TIMELINE_ID = "v2_shift_edit.timeline_id"
private const val KEY_DATE = "v2_shift_edit.date"
private const val KEY_TARGET_SHIFT_ID = "v2_shift_edit.target_shift_id"
private const val KEY_TEMPLATE_ID = "v2_shift_edit.template_id"
private const val KEY_USES_HISTORICAL_TEMPLATE = "v2_shift_edit.uses_historical_template"
private const val KEY_POSITION = "v2_shift_edit.position"
private const val KEY_ACKNOWLEDGED_WARNINGS = "v2_shift_edit.acknowledged_warnings"
private const val KEY_REVIEW_FINGERPRINT = "v2_shift_edit.review_fingerprint"
private const val KEY_CONFIRMED_PAIR_FINGERPRINT = "v2_shift_edit.confirmed_pair_fingerprint"
private val EDIT_STATE_KEYS = listOf(
    KEY_STAGE,
    KEY_TIMELINE_ID,
    KEY_DATE,
    KEY_TARGET_SHIFT_ID,
    KEY_TEMPLATE_ID,
    KEY_USES_HISTORICAL_TEMPLATE,
    KEY_POSITION,
    KEY_ACKNOWLEDGED_WARNINGS,
    KEY_REVIEW_FINGERPRINT,
    KEY_CONFIRMED_PAIR_FINGERPRINT,
)
