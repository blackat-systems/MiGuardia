package com.blackatsystems.miguardia.ui.management

import com.blackatsystems.miguardia.ui.EventTimeRangeStyle
import com.blackatsystems.miguardia.ui.formatEventTimeRange
import androidx.lifecycle.SavedStateHandle
import android.net.Uri
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchRepository
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAction
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchPreview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import com.blackatsystems.miguardia.core.domain.shift.ShiftPlanningWarning
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.shift.planV2ShiftBatch
import com.blackatsystems.miguardia.core.domain.shift.V2ShiftBatchPlan
import com.blackatsystems.miguardia.core.domain.shift.validateSingleMonth
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.NewV2Backfill
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkDateSelection
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleBackfill
import com.blackatsystems.miguardia.core.domain.work.classifyWorkDateSelection
import com.blackatsystems.miguardia.ui.worksetup.WorkCreationKind
import com.blackatsystems.miguardia.ui.worksetup.WorkCreationRequest
import com.blackatsystems.miguardia.ui.worksetup.WorkCreationResult
import com.blackatsystems.miguardia.ui.worksetup.WorkCreationOutcome
import com.blackatsystems.miguardia.ui.worksetup.readWorkCreationRequest
import com.blackatsystems.miguardia.ui.worksetup.readWorkCreationResult
import com.blackatsystems.miguardia.ui.worksetup.writeWorkCreationRequest
import com.blackatsystems.miguardia.ui.worksetup.writeWorkCreationResult
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import com.blackatsystems.miguardia.ui.photos.NoteImageSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

enum class V2ManualShiftLoadStage {
    IDLE,
    SELECT_DATES,
    CHOOSE_TEMPLATE,
    CONFIRM_BACKFILL,
    CHOOSE_OCCUPIED_POLICY,
    CONFIRM_WARNINGS,
    REVIEW,
}

data class V2ManualShiftTemplateOption(
    val objective: Objective,
    val workPlace: WorkPlace,
    val workType: WorkType,
    val template: WorkTemplate,
)

data class ManualWorkPlaceOption(val objective: Objective, val place: WorkPlace)

private class IncompatibleLoadDatesException(message: String) : IllegalStateException(message)

private class SelectedTemplateUnavailableException : IllegalStateException(
    "El horario elegido ya no está disponible para estas fechas.",
)

data class V2ManualShiftLoadUiState(
    val operationId: UUID? = null,
    val operationTimestamp: java.time.Instant? = null,
    val attemptedDates: Set<LocalDate> = emptySet(),
    val noteBody: String = "",
    val noteAttachments: List<ShiftNoteAttachment> = emptyList(),
    val places: List<ManualWorkPlaceOption> = emptyList(),
    val batchPreview: CalendarBatchPreview? = null,
    val conflictDetails: List<String> = emptyList(),
    val stage: V2ManualShiftLoadStage = V2ManualShiftLoadStage.IDLE,
    val timelineId: UUID? = null,
    val sector: WorkSector? = null,
    val selectedDates: Set<LocalDate> = emptySet(),
    val templateOptions: List<V2ManualShiftTemplateOption> = emptyList(),
    val selectedTemplateId: UUID? = null,
    val selectedTemplateFingerprint: String? = null,
    val position: String = "",
    val creationRequest: WorkCreationRequest? = null,
    val creationResult: WorkCreationResult? = null,
    val occupiedPolicy: OccupiedDatePolicy? = null,
    val occupiedDates: Set<LocalDate> = emptySet(),
    val plannedDates: Set<LocalDate> = emptySet(),
    val omittedDates: Set<LocalDate> = emptySet(),
    val warnings: List<String> = emptyList(),
    val acknowledgedWarnings: List<String> = emptyList(),
    val reviewFingerprint: String? = null,
    val saveAfterPreparation: Boolean = false,
    val backfillFrom: LocalDate? = null,
    val configuredFrom: LocalDate? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val successSequence: Int = 0,
    val pendingDayOff: Boolean? = null,
) {
    val canCreate: Boolean
        get() = stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE && !isLoading && !isSaving && creationRequest == null && pendingDayOff == null && batchPreview == null

    val isActive: Boolean
        get() = stage != V2ManualShiftLoadStage.IDLE

    val selectedOption: V2ManualShiftTemplateOption?
        get() = templateOptions.firstOrNull { it.template.id == selectedTemplateId }
}

internal data class V2ManualShiftLoadPersistedState(
    val operationId: UUID? = null,
    val operationTimestamp: java.time.Instant? = null,
    val attemptedDates: Set<LocalDate> = emptySet(),
    val noteBody: String = "",
    val noteAttachments: List<ShiftNoteAttachment> = emptyList(),
    val stage: V2ManualShiftLoadStage = V2ManualShiftLoadStage.IDLE,
    val timelineId: UUID? = null,
    val sector: WorkSector? = null,
    val selectedDates: Set<LocalDate> = emptySet(),
    val selectedTemplateId: UUID? = null,
    val selectedTemplateFingerprint: String? = null,
    val position: String = "",
    val creationRequest: WorkCreationRequest? = null,
    val creationResult: WorkCreationResult? = null,
    val occupiedPolicy: OccupiedDatePolicy? = null,
    val occupiedDates: Set<LocalDate> = emptySet(),
    val acknowledgedWarnings: List<String> = emptyList(),
    val reviewFingerprint: String? = null,
    val saveAfterPreparation: Boolean = false,
    val pendingDayOff: Boolean? = null,
)

class V2ManualShiftLoadViewModel(
    configurationRepository: WorkConfigurationRepository,
    catalogRepository: WorkCatalogRepository,
    objectiveRepository: ObjectiveRepository,
    shiftRepository: ShiftRepository,
    medicalLeaveRepository: MedicalLeaveRepository,
    v2ShiftRepository: V2ShiftRepository,
    shiftActualRepository: ShiftActualRepository,
    explicitDayStatusRepository: ExplicitDayStatusRepository,
    clock: Clock,
    zoneId: ZoneId,
    uuidProvider: UuidProvider,
    private val savedStateHandle: SavedStateHandle,
    calendarBatchRepository: CalendarBatchRepository? = null,
    noteFiles: ManualLoadNotes? = null,
) : ViewModel() {
    private val coordinator = V2ManualShiftLoadCoordinator(
        configurationRepository = configurationRepository,
        catalogRepository = catalogRepository,
        objectiveRepository = objectiveRepository,
        shiftRepository = shiftRepository,
        medicalLeaveRepository = medicalLeaveRepository,
        v2ShiftRepository = v2ShiftRepository,
        shiftActualRepository = shiftActualRepository,
        explicitDayStatusRepository = explicitDayStatusRepository,
        clock = clock,
        zoneId = zoneId,
        uuidProvider = uuidProvider,
        scope = viewModelScope,
        initialPersistedState = savedStateHandle.readV2ManualShiftLoadState(),
        persist = savedStateHandle::writeV2ManualShiftLoadState,
        calendarBatchRepository = calendarBatchRepository,
        noteFiles = noteFiles,
    )

    val uiState: StateFlow<V2ManualShiftLoadUiState> = coordinator.uiState

    fun beginCreation(kind: WorkCreationKind) = coordinator.beginCreation(kind)
    fun beginTemplateForPlace(id: UUID) = coordinator.beginCreation(WorkCreationKind.TEMPLATE, id)
    fun updateNote(value: String) = coordinator.updateNote(value)
    fun importNoteImages(uris: List<Uri>) = coordinator.importNoteImages(uris)
    fun removeNoteImage(id: UUID) = coordinator.removeNoteImage(id)
    fun requestClear() = coordinator.requestBatch(CalendarBatchAction.CLEAR)
    fun confirmBatch() = coordinator.confirmBatch()
    fun cancelBatch() = coordinator.cancelBatch()
    fun receiveCreationResult(result: WorkCreationResult) = coordinator.receiveCreationResult(result)
    fun start(rootState: WorkSetupState) = coordinator.start(rootState)
    fun confirmDates(dates: Set<LocalDate>) = coordinator.confirmDates(dates)
    fun updateSelectedDates(dates: Set<LocalDate>) = coordinator.updateSelectedDates(dates)
    fun requestDayOff(dates: Set<LocalDate>, mark: Boolean) = coordinator.requestDayOff(dates, mark)
    fun confirmDayOff() = coordinator.confirmDayOff()
    fun cancelDayOff() = coordinator.cancelDayOff()
    fun chooseTemplate(id: UUID) = coordinator.chooseTemplate(id)
    fun updatePosition(value: String) = coordinator.updatePosition(value)
    fun requestReview() = coordinator.requestReview()
    fun requestSave() = coordinator.requestSave()
    fun confirmBackfill() = coordinator.confirmBackfill()
    fun cancelBackfill() = coordinator.cancelBackfill()
    fun chooseOccupiedPolicy(policy: OccupiedDatePolicy) = coordinator.chooseOccupiedPolicy(policy)
    fun confirmWarnings() = coordinator.confirmWarnings()
    fun dismissWarnings() = coordinator.dismissWarnings()
    fun save() = coordinator.save()
    fun backToDateSelection() = coordinator.backToDateSelection()
    fun retry() = coordinator.retry()
    fun cancel() = coordinator.cancel()
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
        private val shiftActualRepository: ShiftActualRepository,
        private val explicitDayStatusRepository: ExplicitDayStatusRepository,
        private val clock: Clock = Clock.system(AppDefaults.zoneId()),
        private val zoneId: ZoneId = AppDefaults.zoneId(),
        private val uuidProvider: UuidProvider = UuidProvider(UUID::randomUUID),
        private val calendarBatchRepository: CalendarBatchRepository? = null,
        private val noteFiles: ManualLoadNotes? = null,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(V2ManualShiftLoadViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return V2ManualShiftLoadViewModel(
                configurationRepository = configurationRepository,
                catalogRepository = catalogRepository,
                objectiveRepository = objectiveRepository,
                shiftRepository = shiftRepository,
                medicalLeaveRepository = medicalLeaveRepository,
                v2ShiftRepository = v2ShiftRepository,
                shiftActualRepository = shiftActualRepository,
                explicitDayStatusRepository = explicitDayStatusRepository,
                clock = clock,
                zoneId = zoneId,
                uuidProvider = uuidProvider,
                savedStateHandle = extras.createSavedStateHandle(),
                calendarBatchRepository = calendarBatchRepository,
                noteFiles = noteFiles,
            ) as T
        }
    }
}

internal class V2ManualShiftLoadCoordinator(
    private val configurationRepository: WorkConfigurationRepository,
    private val catalogRepository: WorkCatalogRepository,
    private val objectiveRepository: ObjectiveRepository,
    private val shiftRepository: ShiftRepository,
    private val medicalLeaveRepository: MedicalLeaveRepository,
    private val v2ShiftRepository: V2ShiftRepository,
    private val shiftActualRepository: ShiftActualRepository? = null,
    private val explicitDayStatusRepository: ExplicitDayStatusRepository? = null,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val uuidProvider: UuidProvider,
    private val scope: CoroutineScope,
    initialPersistedState: V2ManualShiftLoadPersistedState = V2ManualShiftLoadPersistedState(),
    private val persist: (V2ManualShiftLoadPersistedState) -> Unit = {},
    private val calendarBatchRepository: CalendarBatchRepository? = null,
    private val noteFiles: ManualLoadNotes? = null,
) {
    private val writeMutex = Mutex()
    private val _uiState = MutableStateFlow(initialPersistedState.toUiState().let {
        if (it.isActive) it.copy(operationId = it.operationId ?: uuidProvider.newUuid(),
            operationTimestamp = it.operationTimestamp ?: clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)) else it
    })
    val uiState: StateFlow<V2ManualShiftLoadUiState> = _uiState

    init {
        scope.launch { noteFiles?.recoverDraft(_uiState.value.noteAttachments) }
        if (
            _uiState.value.isActive &&
            _uiState.value.pendingDayOff == null &&
            _uiState.value.stage != V2ManualShiftLoadStage.SELECT_DATES &&
            _uiState.value.selectedDates.isNotEmpty()
        ) {
            _uiState.update { it.copy(isLoading = true, saveAfterPreparation = false) }
            scope.launch {
                if (_uiState.value.creationResult != null) refreshAfterCreation() else restorePreparedState()
            }
        }
    }

    fun start(rootState: WorkSetupState) {
        if (_uiState.value.isActive || _uiState.value.isLoading || _uiState.value.isSaving) return
        val timelineId = when (rootState) {
            is WorkSetupState.V2Ready -> rootState.timelineId
            is WorkSetupState.V2NeedsFirstSet -> rootState.timelineId
            else -> return
        }
        updateAndPersist {
            V2ManualShiftLoadUiState(
                operationId = uuidProvider.newUuid(),
                operationTimestamp = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
                stage = V2ManualShiftLoadStage.SELECT_DATES,
                timelineId = timelineId,
                sector = null,
                successSequence = it.successSequence,
            )
        }
    }

    fun confirmDates(dates: Set<LocalDate>) {
        if (!_uiState.value.isActive || _uiState.value.isLoading || _uiState.value.isSaving || _uiState.value.creationRequest != null || _uiState.value.pendingDayOff != null) return
        val normalized = dates.toSortedSet()
        try {
            validateSingleMonth(normalized)
        } catch (error: Exception) {
            showError(error.message ?: "Elegí días de un único mes.")
            return
        }
        updateAndPersist {
            it.copy(
                selectedDates = normalized,
                plannedDates = emptySet(), omittedDates = emptySet(),
                batchPreview = null, conflictDetails = emptyList(),
                backfillFrom = null, configuredFrom = null,
                occupiedPolicy = null,
                occupiedDates = emptySet(),
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                saveAfterPreparation = false,
                errorMessage = null,
            )
        }
        _uiState.update { it.copy(isLoading = true) }
        scope.launch { loadTemplateOptions(V2ManualShiftLoadStage.CHOOSE_TEMPLATE) }
    }

    /** Calendar owns presentation; this session owns the draft, including an empty selection. */
    fun updateSelectedDates(dates: Set<LocalDate>) {
        val state = _uiState.value
        if (state.stage != V2ManualShiftLoadStage.SELECT_DATES || state.isLoading || state.isSaving ||
            state.creationRequest != null || state.pendingDayOff != null || dates == state.selectedDates) return
        if (dates.isNotEmpty()) {
            try { validateSingleMonth(dates) } catch (error: Exception) {
                showError(error.message ?: "Elegí días de un único mes.")
                return
            }
        }
        updateAndPersist { it.withoutPreparedDecision().copy(selectedDates = dates.toSortedSet()) }
    }

    // Independent of catalog loading/backfill: a day off has no workplace or template.
    fun requestDayOff(dates: Set<LocalDate>, mark: Boolean) {
        if (_uiState.value.isLoading || _uiState.value.isSaving || _uiState.value.batchPreview != null || !_uiState.value.isActive) return
        if (mark && calendarBatchRepository != null) {
            updateAndPersist { it.copy(selectedDates = dates.toSortedSet()) }
            requestBatch(CalendarBatchAction.DAY_OFF)
            return
        }
        val state = _uiState.value
        if (state.stage !in setOf(V2ManualShiftLoadStage.SELECT_DATES, V2ManualShiftLoadStage.CHOOSE_TEMPLATE) || state.isLoading || state.isSaving ||
            state.creationRequest != null || state.pendingDayOff != null) return
        try {
            validateSingleMonth(dates)
            require(dates.isNotEmpty()) { "Elegí al menos un día." }
        } catch (error: Exception) {
            showError(error.message ?: "Elegí días de un único mes.")
            return
        }
        updateAndPersist {
            it.copy(selectedDates = dates.toSortedSet(),
                pendingDayOff = mark, saveAfterPreparation = false, errorMessage = null)
        }
    }

    fun cancelDayOff() {
        if (_uiState.value.isSaving || _uiState.value.pendingDayOff == null) return
        updateAndPersist { it.copy(pendingDayOff = null, errorMessage = null) }
        // Options are intentionally not persisted. A recreated confirmation returns to its
        // original chooser and reloads that catalog only after the user cancels the modal.
        if (_uiState.value.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE && _uiState.value.templateOptions.isEmpty()) {
            _uiState.update { it.copy(isLoading = true) }
            scope.launch { loadTemplateOptions(V2ManualShiftLoadStage.CHOOSE_TEMPLATE) }
        }
    }

    fun confirmDayOff() {
        val state = _uiState.value
        val mark = state.pendingDayOff ?: return
        val timelineId = state.timelineId ?: return
        if (state.stage !in setOf(V2ManualShiftLoadStage.SELECT_DATES, V2ManualShiftLoadStage.CHOOSE_TEMPLATE) || state.isSaving || state.isLoading) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        scope.launch {
            if (!writeMutex.tryLock()) {
                _uiState.update { it.copy(isSaving = false) }
                return@launch
            }
            try {
                requireNotNull(explicitDayStatusRepository) { "No está disponible el guardado de francos." }
                    .updateDaysOff(timelineId, state.selectedDates, mark)
                _uiState.value = V2ManualShiftLoadUiState(
                    infoMessage = if (mark) "Francos guardados." else "Marcas de franco quitadas. No se modificaron jornadas.",
                    successSequence = state.successSequence + 1,
                )
                persistCurrentState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(error.message ?: "No pudimos actualizar los francos. Revisá los días y reintentá.")
            } finally {
                _uiState.update { it.copy(isSaving = false) }
                writeMutex.unlock()
            }
        }
    }

    fun chooseTemplate(id: UUID) {
        val state = _uiState.value
        if (state.stage != V2ManualShiftLoadStage.CHOOSE_TEMPLATE || state.isLoading || state.isSaving || state.creationRequest != null || state.pendingDayOff != null) return
        if (state.templateOptions.none { it.template.id == id }) return
        updateAndPersist {
            it.copy(
                selectedTemplateId = id,
                selectedTemplateFingerprint = state.templateOptions.first { option -> option.template.id == id }.selectionFingerprint(),
                plannedDates = emptySet(), omittedDates = emptySet(),
                backfillFrom = null, configuredFrom = null,
                occupiedPolicy = null,
                occupiedDates = emptySet(),
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                saveAfterPreparation = false,
                errorMessage = null,
            )
        }
    }

    fun updatePosition(value: String) {
        if (_uiState.value.isLoading || _uiState.value.isSaving || _uiState.value.creationRequest != null || _uiState.value.pendingDayOff != null) return
        updateAndPersist { it.copy(position = value.take(MAX_POSITION_LENGTH), errorMessage = null) }
    }

    fun updateNote(value: String) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        updateAndPersist { it.copy(noteBody = value, reviewFingerprint = null, errorMessage = null) }
    }

    fun importNoteImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val state = _uiState.value
        val operation = state.operationId ?: return
        if (state.isLoading || state.isSaving || state.selectedOption == null) return
        NoteImageSelection.rejectionMessage(state.noteAttachments.size, uris.size)?.let { showError(it); return }
        val files = noteFiles ?: return showError("No pudimos preparar el almacenamiento de imágenes.")
        _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        scope.launch {
            try {
                val imported = NoteImageSelection.importBatch(uris, state.noteAttachments.size,
                    importImage = { uri, position -> files.import(uri, operation, position, clock.instant()) },
                    discard = files::discard)
                updateAndPersist { it.copy(noteAttachments = state.noteAttachments + imported, reviewFingerprint = null) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showError(error.message ?: "No se pudieron importar las imágenes.")
            } finally { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    fun removeNoteImage(id: UUID) {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        val removed = state.noteAttachments.firstOrNull { it.id == id } ?: return
        updateAndPersist { it.copy(noteAttachments = it.noteAttachments.filterNot { a -> a.id == id }
            .mapIndexed { index, a -> a.copy(position = index) }, reviewFingerprint = null) }
        scope.launch { noteFiles?.discard(listOf(removed)) }
    }

    fun requestBatch(action: CalendarBatchAction) {
        val state = _uiState.value
        val repository = calendarBatchRepository ?: return
        if (state.isLoading || state.isSaving || state.selectedDates.isEmpty() || !state.isActive || state.batchPreview != null) return
        _uiState.update { it.copy(isLoading = true, saveAfterPreparation = false) }
        scope.launch {
            try {
                val preview = repository.preview(action, state.selectedDates)
                _uiState.update { it.copy(batchPreview = preview, errorMessage = null) }
            }
            catch (error: Exception) { showError(error.message ?: "No se pudo preparar la revisión.") }
            finally { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    fun cancelBatch() { if (!_uiState.value.isSaving) _uiState.update { it.copy(batchPreview = null) } }

    fun confirmBatch() {
        val state = _uiState.value
        val preview = state.batchPreview ?: return
        val repository = calendarBatchRepository ?: return
        if (state.isLoading || state.isSaving || preview.conflicts.isNotEmpty()) return
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            try {
                val files = noteFiles
                if (files != null) files.applyCalendarBatch(preview) { repository.apply(preview) }
                else repository.apply(preview)
                noteFiles?.discard(state.noteAttachments)
                _uiState.value = V2ManualShiftLoadUiState(successSequence = state.successSequence + 1,
                    infoMessage = if (preview.action == CalendarBatchAction.CLEAR) "Días limpiados." else "Francos guardados.")
                persistCurrentState()
            } catch (error: Exception) {
                _uiState.update { it.copy(batchPreview = null) }
                showError(error.message ?: "Los datos cambiaron. Revisá nuevamente; no se aplicó parcialmente.")
            } finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    fun requestReview() {
        val state = _uiState.value
        if (state.stage == V2ManualShiftLoadStage.SELECT_DATES || !state.isActive || state.batchPreview != null) return
        if (state.isLoading || state.isSaving || state.creationRequest != null || state.pendingDayOff != null) return
        if (state.selectedTemplateId == null) {
            showError("Elegí un lugar, tipo y horario.")
            return
        }
        updateAndPersist { it.copy(saveAfterPreparation = false, isLoading = true) }
        scope.launch { prepareReview() }
    }

    fun requestSave() {
        val state = _uiState.value
        if (state.stage == V2ManualShiftLoadStage.SELECT_DATES || !state.isActive || state.batchPreview != null) return
        if (state.isLoading || state.isSaving || state.creationRequest != null || state.pendingDayOff != null) return
        if (state.selectedTemplateId == null) {
            showError("Elegí un lugar, tipo y horario.")
            return
        }
        updateAndPersist { it.copy(saveAfterPreparation = true, isLoading = true) }
        scope.launch { prepareReview() }
    }

    fun confirmBackfill() {
        val state = _uiState.value
        if (state.stage != V2ManualShiftLoadStage.CONFIRM_BACKFILL || state.isLoading || state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            if (!writeMutex.tryLock()) return@launch
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            var shouldPrepare = false
            try {
                val context = loadSelectionContext()
                val selection = context.selection as? WorkDateSelection.NeedsNewV2Backfill
                if (selection != null) {
                    val option = context.requireSelectedOption()
                    val firstRevision = context.history.timeline.revisions.first()
                    val sourceRule = context.catalog.workplaceRuleRevisions
                        .asSequence()
                        .filter { it.workPlaceId == option.workPlace.id }
                        .minWithOrNull(compareBy({ it.effectiveFrom }, { it.id }))
                        ?: throw IllegalStateException("El lugar elegido no tiene reglas para extender.")
                    val timestamp = clock.instant()
                    catalogRepository.extendNewV2Backward(
                        NewV2Backfill(
                            currentHistory = context.history,
                            configurationRevision = EffectiveRevision(
                                id = uuidProvider.newUuid(),
                                effectiveFrom = selection.earliestDate,
                                value = firstRevision.value,
                            ),
                            workplaceRuleBackfills = listOf(
                                WorkplaceRuleBackfill(
                                    sourceRevision = sourceRule,
                                    earlierRevision = sourceRule.copy(
                                        id = uuidProvider.newUuid(),
                                        effectiveFrom = selection.earliestDate,
                                        createdAt = timestamp,
                                    ),
                                ),
                            ),
                        ),
                    )
                }
                _uiState.update {
                    it.copy(
                        stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                        backfillFrom = null,
                        configuredFrom = null,
                    )
                }
                persistCurrentState()
                shouldPrepare = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: SelectedTemplateUnavailableException) {
                recoverFromUnavailableTemplate()
            } catch (error: Exception) {
                updateAndPersist {
                    it.copy(
                        stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                        backfillFrom = null,
                        configuredFrom = null,
                    )
                }
                showError(
                    error.message
                        ?: "No pudimos extender esta configuración. El borrador sigue disponible para reintentar.",
                )
            } finally {
                _uiState.update { it.copy(isSaving = false) }
                writeMutex.unlock()
            }
            // Preparation may immediately save; release the backfill writer before that handoff.
            if (shouldPrepare) prepareReview()
        }
    }

    fun cancelBackfill() {
        if (
            _uiState.value.stage != V2ManualShiftLoadStage.CONFIRM_BACKFILL ||
            _uiState.value.isLoading ||
            _uiState.value.isSaving
        ) return
        updateAndPersist {
            it.copy(
                stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                backfillFrom = null,
                configuredFrom = null,
                reviewFingerprint = null,
                saveAfterPreparation = false,
                errorMessage = null,
            )
        }
    }

    fun chooseOccupiedPolicy(policy: OccupiedDatePolicy) {
        val state = _uiState.value
        if (state.stage != V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY || state.isLoading) return
        if (policy == OccupiedDatePolicy.CANCEL) {
            updateAndPersist {
                it.copy(
                    stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                    occupiedPolicy = null,
                    occupiedDates = emptySet(),
                    reviewFingerprint = null,
                    saveAfterPreparation = false,
                    errorMessage = null,
                )
            }
            return
        }
        updateAndPersist {
            it.copy(
                stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                occupiedPolicy = policy,
                occupiedDates = state.occupiedDates,
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                errorMessage = null,
            )
        }
        _uiState.update { it.copy(isLoading = true) }
        scope.launch { prepareReview() }
    }

    fun confirmWarnings() {
        val state = _uiState.value
        if (state.stage != V2ManualShiftLoadStage.CONFIRM_WARNINGS || state.isLoading) return
        updateAndPersist {
            it.copy(
                stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                acknowledgedWarnings = state.warnings,
                errorMessage = null,
            )
        }
        _uiState.update { it.copy(isLoading = true) }
        scope.launch { prepareReview() }
    }

    fun dismissWarnings() {
        if (
            _uiState.value.stage != V2ManualShiftLoadStage.CONFIRM_WARNINGS ||
            _uiState.value.isLoading ||
            _uiState.value.isSaving
        ) return
        updateAndPersist {
            it.copy(
                stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                saveAfterPreparation = false,
                errorMessage = null,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.stage != V2ManualShiftLoadStage.REVIEW || state.isSaving || state.isLoading || state.creationRequest != null || state.pendingDayOff != null) return
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            if (!writeMutex.tryLock()) return@launch
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val prepared = prepareMutationForCurrentState()
                if (prepared == null) return@launch
                if (_uiState.value.reviewFingerprint != prepared.fingerprint) {
                    updateAndPersist {
                        it.copy(
                            stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                            templateOptions = prepared.templateOptions,
                            occupiedPolicy = null,
                            occupiedDates = emptySet(),
                            warnings = emptyList(),
                            acknowledgedWarnings = emptyList(),
                            reviewFingerprint = null,
                            saveAfterPreparation = false,
                            isSaving = false,
                            errorMessage = "Las jornadas o advertencias cambiaron. Volvé a guardar para usar los datos actuales.",
                        )
                    }
                    return@launch
                }
                if (prepared.mutation.shiftsToInsert.isEmpty()) {
                    showError("No hay días disponibles para guardar con la opción elegida.")
                    return@launch
                }
                val files = noteFiles
                updateAndPersist { it.copy(attemptedDates = prepared.mutation.shiftsToInsert.mapTo(linkedSetOf()) { pair -> pair.shift.localStartDate }) }
                if (files != null) files.commit(prepared.mutation, state.noteBody, state.noteAttachments,
                    requireNotNull(state.operationId)) { mutation ->
                    v2ShiftRepository.applyV2Batch(mutation, prepared.expectedOccupancy)
                } else {
                    require(state.noteBody.isBlank() && state.noteAttachments.isEmpty()) { "El guardado de notas no está disponible." }
                    v2ShiftRepository.applyV2Batch(prepared.mutation, prepared.expectedOccupancy)
                }
                noteFiles?.discard(state.noteAttachments)
                val savedCount = prepared.mutation.shiftsToInsert.size
                val omittedCount = prepared.omittedDates.size
                val nextSequence = _uiState.value.successSequence + 1
                _uiState.value = V2ManualShiftLoadUiState(
                    infoMessage = buildString {
                        append(if (savedCount == 1) "Jornada guardada." else "$savedCount jornadas guardadas.")
                        if (omittedCount > 0) append(" Se conservaron $omittedCount fechas ocupadas.")
                    },
                    successSequence = nextSequence,
                )
                persistCurrentState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: SelectedTemplateUnavailableException) {
                recoverFromUnavailableTemplate()
            } catch (error: ConflictingLocalWriteException) {
                recoverFromConcurrentShiftChange(error.message)
            } catch (error: Exception) {
                showError(
                    error.message
                        ?: "No pudimos guardar las jornadas. El borrador sigue disponible para reintentar.",
                )
            } finally {
                _uiState.update { it.copy(isSaving = false) }
                writeMutex.unlock()
            }
        }
    }

    fun backToDateSelection() {
        if (!_uiState.value.isActive || _uiState.value.isLoading || _uiState.value.isSaving || _uiState.value.creationRequest != null || _uiState.value.pendingDayOff != null) return
        updateAndPersist {
            it.withoutPreparedDecision().copy(
                stage = V2ManualShiftLoadStage.SELECT_DATES,
                occupiedPolicy = null,
                occupiedDates = emptySet(),
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                saveAfterPreparation = false,
                backfillFrom = null,
                configuredFrom = null,
                errorMessage = null,
            )
        }
    }

    private fun V2ManualShiftLoadUiState.withoutPreparedDecision() = copy(
        occupiedPolicy = null, occupiedDates = emptySet(), plannedDates = emptySet(), omittedDates = emptySet(),
        warnings = emptyList(), acknowledgedWarnings = emptyList(), reviewFingerprint = null,
        batchPreview = null, conflictDetails = emptyList(), saveAfterPreparation = false,
        backfillFrom = null, configuredFrom = null, errorMessage = null,
    )

    fun retry() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        when {
            state.pendingDayOff != null -> Unit // The dialog owns explicit retries; never load templates here.
            state.creationResult != null -> {
                _uiState.update { it.copy(isLoading = true) }
                scope.launch { refreshAfterCreation() }
            }
            state.creationRequest != null -> Unit
            state.selectedDates.isEmpty() -> _uiState.update { it.copy(errorMessage = null) }
            else -> {
                _uiState.update { it.copy(isLoading = true) }
                scope.launch {
                    if (state.stage == V2ManualShiftLoadStage.REVIEW) prepareReview()
                    else loadTemplateOptions(V2ManualShiftLoadStage.CHOOSE_TEMPLATE)
                }
            }
        }
    }

    fun cancel() {
        if (_uiState.value.isLoading || _uiState.value.isSaving || (_uiState.value.creationRequest != null && _uiState.value.creationResult == null)) return
        val attachments = _uiState.value.noteAttachments
        scope.launch { noteFiles?.discard(attachments) }
        val nextSequence = _uiState.value.successSequence
        _uiState.value = V2ManualShiftLoadUiState(successSequence = nextSequence)
        persistCurrentState()
    }

    fun discardIncompatible() {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        val abandoned = _uiState.value.noteAttachments
        scope.launch { noteFiles?.discard(abandoned) }
        _uiState.value = V2ManualShiftLoadUiState(
            infoMessage = "La carga anterior ya no coincide con tu forma de trabajar actual. " +
                "Se descartó el borrador y no se guardó ninguna jornada.",
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

    fun beginCreation(kind: WorkCreationKind, placeId: UUID? = null) {
        val state = _uiState.value
        if (!state.canCreate) return
        val timelineId = state.timelineId ?: return
        val option = state.selectedOption
        updateAndPersist {
            it.copy(creationRequest = WorkCreationRequest(uuidProvider.newUuid(), timelineId, kind,
                placeId ?: option?.workPlace?.id, option?.workType?.id), creationResult = null,
                saveAfterPreparation = false, errorMessage = null, infoMessage = null)
        }
    }

    /** Persist the inbox before acknowledging the producer. Re-deliveries never select twice. */
    fun receiveCreationResult(result: WorkCreationResult) {
        val state = _uiState.value
        val request = state.creationRequest ?: return
        if (result.requestId != request.id || result.timelineId != state.timelineId || state.creationResult != null) return
        updateAndPersist { it.copy(creationResult = result, saveAfterPreparation = false) }
        // Restoration may already be loading options. Its completion drains the saved inbox.
        if (!state.isLoading && !state.isSaving) {
            _uiState.update { it.copy(isLoading = true) }
            scope.launch { refreshAfterCreation() }
        }
    }

    private suspend fun refreshAfterCreation() {
        val result = _uiState.value.creationResult ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val context = loadSelectionContext()
            if (_uiState.value.creationRequest?.id != result.requestId) return
            val previousId = _uiState.value.selectedTemplateId
            val candidateId = if (result.outcome == WorkCreationOutcome.CREATED) result.templateId else previousId
            val selectedId = candidateId?.takeIf { id -> context.options.any { option ->
                option.template.id == id && if (result.outcome == WorkCreationOutcome.CREATED) {
                    option.workPlace.id == result.placeId
                } else {
                    _uiState.value.selectedTemplateFingerprint?.let { it == option.selectionFingerprint() } ?: true
                }
            } }
            updateAndPersist {
                it.copy(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE, sector = context.sector,
                    templateOptions = context.options, places = context.places, selectedTemplateId = selectedId,
                    selectedTemplateFingerprint = context.options.firstOrNull { option -> option.template.id == selectedId }?.selectionFingerprint(),
                    creationRequest = null, creationResult = null, isLoading = false,
                    occupiedPolicy = null, occupiedDates = emptySet(), plannedDates = emptySet(),
                    omittedDates = emptySet(), warnings = emptyList(), acknowledgedWarnings = emptyList(),
                    reviewFingerprint = null, saveAfterPreparation = false, backfillFrom = null, configuredFrom = null,
                    infoMessage = when {
                        result.outcome == WorkCreationOutcome.CREATED && selectedId != null ->
                            "El horario quedó seleccionado. Revisá la carga y confirmá las jornadas cuando quieras."
                        result.outcome == WorkCreationOutcome.CANCELLED && result.placeId != null ->
                            "El lugar quedó guardado, aunque cancelaste su horario. Podés crear un horario para ese lugar sin volver a crearlo."
                        else -> null
                    },
                    errorMessage = when {
                        result.outcome == WorkCreationOutcome.CREATED && selectedId == null ->
                            "El horario se guardó, pero ya no está activo o no tiene reglas aplicables a todos los días elegidos. Conservamos las fechas y el puesto; cambiá los días o elegí otro horario."
                        previousId != null && selectedId == null ->
                            "El horario anterior ya no está disponible. Conservamos las fechas y el puesto; elegí otro."
                        else -> null
                    })
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IncompatibleLoadDatesException) {
            if (_uiState.value.creationRequest?.id == result.requestId) {
                updateAndPersist {
                    it.copy(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE, sector = null,
                        templateOptions = emptyList(), selectedTemplateId = null,
                        creationRequest = null, creationResult = null,
                        occupiedPolicy = null, occupiedDates = emptySet(), plannedDates = emptySet(),
                        omittedDates = emptySet(), warnings = emptyList(), acknowledgedWarnings = emptyList(),
                        reviewFingerprint = null, saveAfterPreparation = false, backfillFrom = null, configuredFrom = null,
                        errorMessage = "Conservamos las fechas y el puesto. ${error.message}",
                        infoMessage = if (result.outcome == WorkCreationOutcome.CREATED) "El horario creado quedó guardado." else null)
                }
            }
        } catch (_: Exception) {
            // Keep request + result. Retry only reads; it never launches another creator or write.
            if (_uiState.value.creationRequest?.id == result.requestId) {
                showError("No pudimos actualizar los horarios de esta carga. Conservamos el resultado y tu borrador; reintentá la lectura.")
            }
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun restorePreparedState() {
        val restoredStage = _uiState.value.stage
        val restoredFingerprint = _uiState.value.reviewFingerprint
        _uiState.update { it.copy(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE) }
        loadTemplateOptions(
            targetStage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
            persistLoadedState = false,
        )
        if (_uiState.value.creationResult != null) {
            refreshAfterCreation()
            return
        }
        if (_uiState.value.creationRequest != null) return
        if (
            restoredStage in setOf(
                V2ManualShiftLoadStage.CONFIRM_BACKFILL,
                V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY,
                V2ManualShiftLoadStage.CONFIRM_WARNINGS,
                V2ManualShiftLoadStage.REVIEW,
            ) &&
            _uiState.value.selectedTemplateId != null
        ) {
            prepareReview(
                expectedRestoredFingerprint = restoredFingerprint,
                requireRestoredFingerprintMatch = restoredStage == V2ManualShiftLoadStage.REVIEW,
            )
        }
    }

    private suspend fun loadTemplateOptions(
        targetStage: V2ManualShiftLoadStage,
        persistLoadedState: Boolean = true,
    ) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val context = loadSelectionContext()
            val previousSelection = _uiState.value.selectedTemplateId
            val selectedTemplateId = previousSelection?.takeIf { id ->
                context.options.any { option -> option.template.id == id &&
                    (_uiState.value.selectedTemplateFingerprint?.let { it == option.selectionFingerprint() } ?: true) }
            }
            val selectionBecameUnavailable = previousSelection != null && selectedTemplateId == null
            _uiState.update { state ->
                state.copy(
                    stage = targetStage,
                    sector = context.sector,
                    templateOptions = context.options,
                    places = context.places,
                    selectedTemplateId = selectedTemplateId,
                    selectedTemplateFingerprint = context.options.firstOrNull { it.template.id == selectedTemplateId }?.selectionFingerprint(),
                    occupiedPolicy = if (selectionBecameUnavailable) null else state.occupiedPolicy,
                    occupiedDates = if (selectionBecameUnavailable) emptySet() else state.occupiedDates,
                    plannedDates = if (selectionBecameUnavailable) emptySet() else state.plannedDates,
                    omittedDates = if (selectionBecameUnavailable) emptySet() else state.omittedDates,
                    warnings = if (selectionBecameUnavailable) emptyList() else state.warnings,
                    acknowledgedWarnings = if (selectionBecameUnavailable) emptyList() else state.acknowledgedWarnings,
                    reviewFingerprint = if (selectionBecameUnavailable) null else state.reviewFingerprint,
                    batchPreview = if (selectionBecameUnavailable) null else state.batchPreview,
                    conflictDetails = if (selectionBecameUnavailable) emptyList() else state.conflictDetails,
                    backfillFrom = if (selectionBecameUnavailable) null else state.backfillFrom,
                    configuredFrom = if (selectionBecameUnavailable) null else state.configuredFrom,
                    saveAfterPreparation = if (selectionBecameUnavailable) false else state.saveAfterPreparation,
                    isLoading = false,
                    errorMessage = when {
                        selectionBecameUnavailable ->
                            "La institución o el horario elegido cambió o ya no está disponible. Conservamos el borrador; elegí nuevamente una opción válida."
                        context.options.isEmpty() ->
                            "No hay lugares, tipos y horarios activos con reglas aplicables a todos los días elegidos."
                        else -> null
                    },
                )
            }
            if (persistLoadedState || selectionBecameUnavailable) persistCurrentState()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            showError(error.message ?: "No pudimos cargar tus horarios guardados. Reintentá.")
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun prepareReview(
        expectedRestoredFingerprint: String? = null,
        requireRestoredFingerprintMatch: Boolean = false,
    ) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val context = loadSelectionContext()
            val option = context.requireSelectedOption()
            if (recoverConfirmedLoad(context, option)) return
            val selection = context.selection
            if (selection is WorkDateSelection.NeedsNewV2Backfill) {
                val configuredFrom = context.history.timeline.revisions.first().effectiveFrom
                updateAndPersist {
                    it.copy(
                        stage = V2ManualShiftLoadStage.CONFIRM_BACKFILL,
                        sector = context.sector,
                        templateOptions = context.options,
                        selectedTemplateId = option.template.id,
                        backfillFrom = selection.earliestDate,
                        configuredFrom = configuredFrom,
                        isLoading = false,
                    )
                }
                return
            }
            val prepared = buildPreparedMutation(context, option, _uiState.value.occupiedPolicy)
            val currentState = _uiState.value
            if (
                currentState.occupiedPolicy != null &&
                currentState.occupiedDates != prepared.occupiedDates
            ) {
                updateAndPersist {
                    it.copy(
                        stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                        occupiedPolicy = null,
                        occupiedDates = prepared.occupiedDates,
                        plannedDates = emptySet(),
                        omittedDates = emptySet(),
                        warnings = emptyList(),
                        acknowledgedWarnings = emptyList(),
                        reviewFingerprint = null,
                        isLoading = false,
                        errorMessage = "Cambió qué fechas ya tienen jornadas. Revisá nuevamente antes de elegir qué hacer.",
                    )
                }
                return
            }
            if (prepared.occupiedDates.isNotEmpty() && _uiState.value.occupiedPolicy == null) {
                updateAndPersist {
                    it.copy(
                        stage = V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY,
                        templateOptions = context.options,
                        selectedTemplateId = option.template.id,
                        occupiedDates = prepared.occupiedDates,
                        warnings = emptyList(),
                        reviewFingerprint = null,
                        isLoading = false,
                    )
                }
                return
            }
            if (
                requireRestoredFingerprintMatch &&
                expectedRestoredFingerprint != prepared.fingerprint
            ) {
                updateAndPersist {
                    it.copy(
                        stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                        occupiedPolicy = null,
                        occupiedDates = emptySet(),
                        plannedDates = emptySet(),
                        omittedDates = emptySet(),
                        warnings = emptyList(),
                        acknowledgedWarnings = emptyList(),
                        reviewFingerprint = null,
                        isLoading = false,
                        errorMessage = "Las jornadas o advertencias cambiaron durante la pausa. " +
                            "Conservamos el borrador para que lo revises nuevamente.",
                    )
                }
                return
            }
            if (prepared.mutation.shiftsToInsert.isEmpty()) {
                updateAndPersist {
                    it.copy(
                        stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                        occupiedPolicy = null,
                        occupiedDates = prepared.occupiedDates,
                        plannedDates = emptySet(),
                        omittedDates = prepared.omittedDates,
                        warnings = emptyList(),
                        acknowledgedWarnings = emptyList(),
                        reviewFingerprint = null,
                        isLoading = false,
                        errorMessage = "Todas las fechas elegidas ya tienen jornadas. " +
                            "Elegí reemplazarlas, sumar una segunda o modificar los días.",
                    )
                }
                return
            }
            if (
                prepared.warnings.isNotEmpty() &&
                prepared.warnings != _uiState.value.acknowledgedWarnings
            ) {
                updateAndPersist {
                    it.copy(
                        stage = V2ManualShiftLoadStage.CONFIRM_WARNINGS,
                        templateOptions = context.options,
                        selectedTemplateId = option.template.id,
                        occupiedDates = prepared.occupiedDates,
                        warnings = prepared.warnings,
                        reviewFingerprint = null,
                        isLoading = false,
                    )
                }
                return
            }
            val shouldSaveImmediately = _uiState.value.saveAfterPreparation
            updateAndPersist {
                it.copy(
                    stage = V2ManualShiftLoadStage.REVIEW,
                    templateOptions = context.options,
                    selectedTemplateId = option.template.id,
                    occupiedDates = prepared.occupiedDates,
                    plannedDates = prepared.mutation.shiftsToInsert
                        .mapTo(linkedSetOf()) { write -> write.shift.localStartDate },
                    omittedDates = prepared.omittedDates,
                    warnings = prepared.warnings,
                    reviewFingerprint = prepared.fingerprint,
                    saveAfterPreparation = false,
                    isLoading = false,
                )
            }
            if (shouldSaveImmediately) save()
        } catch (error: CancellationException) {
            throw error
        } catch (error: SelectedTemplateUnavailableException) {
            recoverFromUnavailableTemplate()
        } catch (error: Exception) {
            showError(error.message ?: "No pudimos preparar la carga. Reintentá.")
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun prepareMutationForCurrentState(): PreparedV2Mutation? {
        val context = loadSelectionContext()
        val option = context.requireSelectedOption()
        if (context.selection is WorkDateSelection.NeedsNewV2Backfill) {
            updateAndPersist {
                it.copy(
                    stage = V2ManualShiftLoadStage.CONFIRM_BACKFILL,
                    backfillFrom = context.selection.earliestDate,
                    configuredFrom = context.history.timeline.revisions.first().effectiveFrom,
                    isSaving = false,
                )
            }
            return null
        }
        val prepared = buildPreparedMutation(context, option, _uiState.value.occupiedPolicy)
        if (
            _uiState.value.occupiedPolicy != null &&
            _uiState.value.occupiedDates != prepared.occupiedDates
        ) {
            updateAndPersist {
                it.copy(
                    stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                    occupiedPolicy = null,
                    occupiedDates = prepared.occupiedDates,
                    warnings = emptyList(),
                    acknowledgedWarnings = emptyList(),
                    reviewFingerprint = null,
                    isSaving = false,
                    errorMessage = "Cambió qué fechas ya tienen jornadas. Revisá nuevamente antes de elegir qué hacer.",
                )
            }
            return null
        }
        if (prepared.occupiedDates.isNotEmpty() && _uiState.value.occupiedPolicy == null) {
            updateAndPersist {
                it.copy(
                    stage = V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY,
                    occupiedDates = prepared.occupiedDates,
                    isSaving = false,
                )
            }
            return null
        }
        if (prepared.warnings.isNotEmpty() && prepared.warnings != _uiState.value.acknowledgedWarnings) {
            updateAndPersist {
                it.copy(
                    stage = V2ManualShiftLoadStage.CONFIRM_WARNINGS,
                    occupiedDates = prepared.occupiedDates,
                    warnings = prepared.warnings,
                    isSaving = false,
                )
            }
            return null
        }
        return prepared
    }

    /** A lost response/recreation never becomes a second intentional load with new identities. */
    private suspend fun recoverConfirmedLoad(context: LoadedSelectionContext, option: V2ManualShiftTemplateOption): Boolean {
        val state = _uiState.value
        if (state.attemptedDates.isEmpty()) return false
        val operation = state.operationId ?: return false
        val timestamp = state.operationTimestamp ?: return false
        val stored = state.attemptedDates.sorted().map { date ->
            val id = UUID.nameUUIDFromBytes("$operation:shift:$date".toByteArray())
            val expected = buildV2ShiftWrite(id, date, option.objective, option.workPlace, option.workType,
                option.template, ResolvedWorkConfigurationRevision.resolve(context.history, date),
                state.position, timestamp, zoneId)
            val actual = v2ShiftRepository.getShift(id) as? com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup.V2
            actual?.write == expected && (noteFiles?.matchesCommitted(expected, state.noteBody, state.noteAttachments, operation)
                ?: (state.noteBody.isBlank() && state.noteAttachments.isEmpty()))
        }
        if (!stored.all { it }) return false
        noteFiles?.discard(state.noteAttachments)
        _uiState.value = V2ManualShiftLoadUiState(successSequence = state.successSequence + 1,
            infoMessage = "La carga ya estaba guardada. No se duplicó ninguna jornada.")
        persistCurrentState()
        return true
    }

    private suspend fun loadSelectionContext(): LoadedSelectionContext {
        val state = _uiState.value
        val timelineId = requireNotNull(state.timelineId) { "La configuración de esta carga ya no está disponible." }
        val dates = state.selectedDates
        validateSingleMonth(dates)
        val history = configurationRepository.get()
            ?: throw IncompatibleLoadDatesException("La configuración de esta carga ya no está disponible.")
        if (history.timeline.id != timelineId) {
            throw IncompatibleLoadDatesException("La forma de trabajar cambió. Revisá las fechas de esta carga.")
        }
        val selection = try {
            classifyWorkDateSelection(history, dates)
        } catch (error: com.blackatsystems.miguardia.core.domain.repository.InvalidV2SelectionException) {
            throw IncompatibleLoadDatesException(error.message ?: "Revisá las fechas de esta carga.")
        }
        val sector = when (selection) {
            is WorkDateSelection.V2 -> selection.sector
            is WorkDateSelection.NeedsNewV2Backfill -> selection.sector
        }
        if (
            state.sector != null &&
            state.stage != V2ManualShiftLoadStage.SELECT_DATES &&
            sector != state.sector
        ) {
            throw IncompatibleLoadDatesException("La selección ya no pertenece al mismo rubro. Revisá las fechas de esta carga.")
        }
        val catalog = catalogRepository.observeCatalog(timelineId, sector).first()
        val configuredDates = when (selection) {
            is WorkDateSelection.V2 -> dates
            is WorkDateSelection.NeedsNewV2Backfill -> selection.configuredRevisionsByDate.keys
        }
        val needsBackfill = selection is WorkDateSelection.NeedsNewV2Backfill
        val placesById = catalog.workPlaces.filter(WorkPlace::isActive).associateBy(WorkPlace::id)
        val typesById = catalog.workTypes.filter(WorkType::isActive).associateBy(WorkType::id)
        val eligibleTemplates = catalog.workTemplates.filter { template ->
            if (!template.isActive) return@filter false
            val place = placesById[template.workPlaceId] ?: return@filter false
            if (typesById[template.workTypeId] == null) return@filter false
            val configuredRulesApply = needsBackfill || configuredDates.all { date ->
                catalog.ruleRevisionAt(place.id, date) != null
            }
            val canBackfill = !needsBackfill ||
                catalog.workplaceRuleRevisions.any { it.workPlaceId == place.id }
            configuredRulesApply && canBackfill
        }
        val objectivesById = eligibleTemplates
            .map { template -> requireNotNull(placesById[template.workPlaceId]).objectiveId }
            .distinct()
            .mapNotNull { objectiveId ->
                objectiveRepository.getById(objectiveId)?.takeIf(Objective::isActive)?.let { objectiveId to it }
            }.toMap()
        val options = eligibleTemplates.mapNotNull { template ->
            val place = requireNotNull(placesById[template.workPlaceId])
            val objective = objectivesById[place.objectiveId] ?: return@mapNotNull null
            V2ManualShiftTemplateOption(
                objective = objective,
                workPlace = place,
                workType = requireNotNull(typesById[template.workTypeId]),
                template = template,
            )
        }.sortedWith(
            compareBy<V2ManualShiftTemplateOption> { it.objective.abbreviation }
                .thenBy { it.workType.name }
                .thenBy { it.template.startTime }
                .thenBy { it.template.endTime },
        )
        val places = catalog.workPlaces.filter { it.isActive }.mapNotNull { place ->
            objectiveRepository.getById(place.objectiveId)?.takeIf(Objective::isActive)?.let { ManualWorkPlaceOption(it, place) }
        }.sortedBy { it.objective.fullName }
        return LoadedSelectionContext(history, selection, sector, catalog, options, places)
    }

    private suspend fun buildPreparedMutation(
        context: LoadedSelectionContext,
        option: V2ManualShiftTemplateOption,
        requestedPolicy: OccupiedDatePolicy?,
    ): PreparedV2Mutation {
        val dates = _uiState.value.selectedDates.sorted()
        val first = dates.first()
        val last = dates.last()
        val existing = shiftRepository
            .observeStartingBetween(first.minusDays(2), last.plusDays(2))
            .first()
        val medicalLeaves = medicalLeaveRepository.observeIntersecting(first, last).first()
        val timestamp = _uiState.value.operationTimestamp ?: clock.instant()
        val candidates = dates.map { date ->
            buildV2ShiftWrite(
                id = UUID.nameUUIDFromBytes("${_uiState.value.operationId}:shift:$date".toByteArray()),
                date = date,
                objective = option.objective,
                workPlace = option.workPlace,
                workType = option.workType,
                template = option.template,
                configurationContext = ResolvedWorkConfigurationRevision.resolve(context.history, date),
                position = _uiState.value.position,
                timestamp = timestamp,
                zoneId = zoneId,
            )
        }
        val explicitStatuses = explicitDayStatusRepository?.observeBetween(first, last)?.first().orEmpty()
            .filter { it.date in _uiState.value.selectedDates }
        val statusesByDate = explicitStatuses.associateBy { it.date }
        val conflicting = existing.filter { old -> candidates.any { candidate ->
            old.startAt < candidate.shift.endAt && candidate.shift.startAt < old.endAt
        } }
        val occupiedDates = candidates.filter { candidate -> conflicting.any { old ->
            old.startAt < candidate.shift.endAt && candidate.shift.startAt < old.endAt
        } }.mapTo(linkedSetOf()) { it.shift.localStartDate }
        occupiedDates += explicitStatuses.filter { it.type == ExplicitDayStatusType.DAY_OFF }.map { it.date }
        val policy = requestedPolicy ?: OccupiedDatePolicy.ADD_SECOND_SHIFT
        val retainedFrancoDates = if (policy == OccupiedDatePolicy.KEEP_OCCUPIED) {
            explicitStatuses.filter { it.type == ExplicitDayStatusType.DAY_OFF }.mapTo(linkedSetOf()) { it.date }
        } else emptySet()
        val planningDates = _uiState.value.selectedDates - retainedFrancoDates
        // Franco is an occupied day too. Exclude it before interval planning so neither
        // a new journey nor warnings from an omitted candidate enter the batch.
        val plan = if (planningDates.isEmpty()) V2ShiftBatchPlan(
            mutation = V2ShiftBatchMutation(),
            occupiedDates = retainedFrancoDates,
            omittedDates = retainedFrancoDates,
            warnings = emptyList(),
        ) else planV2ShiftBatch(
            selectedDates = planningDates,
            existingShifts = existing,
            candidates = candidates.filter { it.shift.localStartDate in planningDates },
            policy = policy,
        ).let { it.copy(occupiedDates = it.occupiedDates + retainedFrancoDates,
            omittedDates = it.omittedDates + retainedFrancoDates) }
        val savedDates = plan.mutation.shiftsToInsert
            .mapTo(linkedSetOf()) { it.shift.localStartDate }
        val actualExpectations = plan.mutation.shiftIdsToDelete
            .mapNotNull { shiftId ->
                shiftActualRepository?.getExpectation(shiftId)?.let { shiftId to it }
            }
            .toMap(linkedMapOf())
        val mutation = plan.mutation.copy(
            explicitDayStatusDatesToClear = savedDates.filterTo(linkedSetOf()) {
                policy == OccupiedDatePolicy.REPLACE || statusesByDate[it]?.type == ExplicitDayStatusType.UNDEFINED
            },
            actualExpectations = actualExpectations,
            deletionExpectations = v2ShiftRepository.captureDeletionExpectations(plan.mutation.shiftIdsToDelete),
            explicitStatusExpectations = if (explicitDayStatusRepository != null) savedDates.associateWith { statusesByDate[it] } else emptyMap(),
        )
        val coexistenceWarnings = medicalLeaves
            .filter { leave -> savedDates.any { it in leave.startDate..leave.endDateInclusive } }
            .map { leave ->
                "Existe una carpeta médica entre ${leave.startDate.format(DATE_FORMATTER)} y " +
                    "${leave.endDateInclusive.format(DATE_FORMATTER)}. No se modificará."
            }
        val actualWarnings = actualExpectations.values
            .filter { it.previousActual != null }
            .map { expectation ->
                "Reemplazar la jornada ${expectation.planned.shift.id} también eliminará su horario real y extras."
            }
        val losses = mutation.deletionExpectations.values.map { loss ->
            val shift = existing.first { it.id == loss.shiftId }
            "${shift.localStartDate.format(DATE_FORMATTER)} ${shift.timeRange()}: se quitará esta jornada" +
                ", ${loss.noteCount} notas, ${loss.attachmentStorageKeys.size} imágenes y ${loss.notificationCount} avisos" +
                (if (loss.hasActual) ", horario real y extras asociados" else "") +
                (if (loss.hasSituation) ", situación y evidencia asociadas" else "") + "."
        }
        val warningTexts = plan.warnings.map(::warningText) + coexistenceWarnings + actualWarnings + losses
        val conflictDetails = conflicting.map { old ->
            "${old.localStartDate.format(DATE_FORMATTER)}: ${old.objectiveNameSnapshot} (${old.objectiveAbbreviationSnapshot}), ${old.timeRange()} existente. " +
                "Nueva: ${option.objective.abbreviation} ${option.template.startTime}–${option.template.endTime}."
        } + explicitStatuses.filter { it.type == ExplicitDayStatusType.DAY_OFF }.map {
            "${it.date.format(DATE_FORMATTER)}: Franco existente. Nueva: ${option.template.startTime}–${option.template.endTime}."
        }
        _uiState.update { it.copy(conflictDetails = conflictDetails) }
        return PreparedV2Mutation(
            mutation = mutation,
            expectedOccupancy = ShiftOccupancyExpectation.capture(
                startDateInclusive = first.minusDays(2),
                endDateInclusive = last.plusDays(2),
                shifts = existing,
            ),
            templateOptions = context.options,
            occupiedDates = occupiedDates,
            omittedDates = plan.omittedDates,
            warnings = warningTexts,
            fingerprint = buildFingerprint(
                context = context,
                option = option,
                mutation = mutation,
                omittedDates = plan.omittedDates,
                warnings = warningTexts,
                existingShifts = existing,
            ),
        )
    }

    private fun buildFingerprint(
        context: LoadedSelectionContext,
        option: V2ManualShiftTemplateOption,
        mutation: V2ShiftBatchMutation,
        omittedDates: Set<LocalDate>,
        warnings: List<String>,
        existingShifts: List<Shift>,
    ): String {
        val raw = buildString {
            append("objective=")
            append(
                listOf(
                    option.objective.id,
                    option.objective.fullName,
                    option.objective.abbreviation,
                    option.objective.address,
                ).joinToString("|"),
            )
            append(";place=")
            append(listOf(option.workPlace.id, option.workPlace.objectiveId, option.workPlace.isActive).joinToString("|"))
            append(";type=")
            append(listOf(option.workType.id, option.workType.name, option.workType.behavior, option.workType.isActive).joinToString("|"))
            append(";template=")
            append(
                listOf(
                    option.template.id,
                    option.template.startTime,
                    option.template.endTime,
                    option.template.colorArgb,
                    option.template.isActive,
                ).joinToString("|"),
            )
            append(";position=")
            append(_uiState.value.position.trim())
            append(";note="); append(_uiState.value.noteBody)
            append(";attachments="); append(_uiState.value.noteAttachments.joinToString { it.encodeManualDraft() })
            append(";deletionExpectations="); append(mutation.deletionExpectations.toSortedMap())
            append(";statuses="); append(mutation.explicitStatusExpectations.toSortedMap())
            append(";revisions=")
            append(
                _uiState.value.selectedDates.sorted().joinToString(",") { date ->
                    "$date:${context.history.timeline.revisionAt(date)?.id}:" +
                        context.catalog.ruleRevisionAt(option.workPlace.id, date)?.id
                },
            )
            append(";delete=")
            append(mutation.shiftIdsToDelete.sorted().joinToString(","))
            append(";existing=")
            append(
                existingShifts
                    .sortedWith(compareBy<Shift> { it.localStartDate }.thenBy { it.startAt }.thenBy { it.id })
                    .joinToString(",") { shift ->
                        listOf(
                            shift.id,
                            shift.localStartDate,
                            shift.startAt,
                            shift.endAt,
                            shift.status,
                            shift.updatedAt,
                        ).joinToString("|")
                    },
            )
            append(";insertDates=")
            append(mutation.shiftsToInsert.map { it.shift.localStartDate }.sorted().joinToString(","))
            append(";omitted=")
            append(omittedDates.sorted().joinToString(","))
            append(";warnings=")
            append(warnings.joinToString("|"))
            append(";actualExpectations=")
            append(
                mutation.actualExpectations.entries
                    .sortedBy { it.key }
                    .joinToString("|") { (id, expectation) -> "$id=$expectation" },
            )
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun warningText(warning: ShiftPlanningWarning): String = when (warning) {
        is ShiftPlanningWarning.SameDate ->
            "${warning.first.localStartDate.format(DATE_FORMATTER)}: habrá más de una jornada " +
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
        formatEventTimeRange(startAt, endAt, zoneId, EventTimeRangeStyle.COMPACT)

    // Only immutable catalog values: safe to persist across recreation, never object identity.
    private fun V2ManualShiftTemplateOption.selectionFingerprint(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(listOf(objective, workPlace, workType, template).joinToString("|").toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun LoadedSelectionContext.requireSelectedOption(): V2ManualShiftTemplateOption {
        val selectedId = _uiState.value.selectedTemplateId
            ?: throw IllegalStateException("Elegí un lugar, tipo y horario.")
        return options.firstOrNull { it.template.id == selectedId }
            ?: throw SelectedTemplateUnavailableException()
    }

    private suspend fun recoverFromUnavailableTemplate() {
        loadTemplateOptions(V2ManualShiftLoadStage.CHOOSE_TEMPLATE)
        if (_uiState.value.errorMessage == null) {
            showError("El horario elegido ya no está disponible. Conservamos las fechas y el puesto; elegí otro.")
        }
    }

    private fun recoverFromConcurrentShiftChange(message: String?) {
        updateAndPersist {
            it.copy(
                stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                occupiedPolicy = null,
                occupiedDates = emptySet(),
                plannedDates = emptySet(),
                omittedDates = emptySet(),
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                isSaving = false,
                errorMessage = message
                    ?: "Las jornadas cambiaron mientras revisabas. Revisalas nuevamente antes de guardar.",
            )
        }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message, isLoading = false, isSaving = false) }
        persistCurrentState()
    }

    private fun updateAndPersist(transform: (V2ManualShiftLoadUiState) -> V2ManualShiftLoadUiState) {
        _uiState.update(transform)
        persistCurrentState()
    }

    private fun persistCurrentState() = persist(_uiState.value.toPersistedState())

    private data class LoadedSelectionContext(
        val history: WorkConfigurationHistory,
        val selection: WorkDateSelection,
        val sector: WorkSector,
        val catalog: WorkCatalog,
        val options: List<V2ManualShiftTemplateOption>,
        val places: List<ManualWorkPlaceOption>,
    )

    private data class PreparedV2Mutation(
        val mutation: V2ShiftBatchMutation,
        val expectedOccupancy: ShiftOccupancyExpectation,
        val templateOptions: List<V2ManualShiftTemplateOption>,
        val occupiedDates: Set<LocalDate>,
        val omittedDates: Set<LocalDate>,
        val warnings: List<String>,
        val fingerprint: String,
    )

    private companion object {
        const val MAX_POSITION_LENGTH = 120
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    }
}

private fun V2ManualShiftLoadPersistedState.toUiState() = V2ManualShiftLoadUiState(
    operationId = operationId,
    operationTimestamp = operationTimestamp,
    attemptedDates = attemptedDates,
    noteBody = noteBody,
    noteAttachments = noteAttachments,
    stage = stage,
    timelineId = timelineId,
    sector = sector,
    selectedDates = selectedDates,
    selectedTemplateId = selectedTemplateId,
    selectedTemplateFingerprint = selectedTemplateFingerprint,
    position = position,
    creationRequest = creationRequest,
    creationResult = creationResult,
    occupiedPolicy = occupiedPolicy,
    occupiedDates = occupiedDates,
    acknowledgedWarnings = acknowledgedWarnings,
    reviewFingerprint = reviewFingerprint,
    saveAfterPreparation = saveAfterPreparation,
    pendingDayOff = pendingDayOff,
)

private fun V2ManualShiftLoadUiState.toPersistedState() = V2ManualShiftLoadPersistedState(
    operationId = operationId,
    operationTimestamp = operationTimestamp,
    attemptedDates = attemptedDates,
    noteBody = noteBody,
    noteAttachments = noteAttachments,
    stage = stage,
    timelineId = timelineId,
    sector = sector,
    selectedDates = selectedDates,
    selectedTemplateId = selectedTemplateId,
    selectedTemplateFingerprint = selectedTemplateFingerprint,
    position = position,
    creationRequest = creationRequest,
    creationResult = creationResult,
    occupiedPolicy = occupiedPolicy,
    occupiedDates = occupiedDates,
    acknowledgedWarnings = acknowledgedWarnings,
    reviewFingerprint = reviewFingerprint,
    saveAfterPreparation = saveAfterPreparation,
    pendingDayOff = pendingDayOff,
)

internal fun SavedStateHandle.readV2ManualShiftLoadState(): V2ManualShiftLoadPersistedState =
    V2ManualShiftLoadPersistedState(
        operationId = get<String>("v2_manual_shift.operation")?.toUuidOrNull(),
        operationTimestamp = get<String>("v2_manual_shift.timestamp")?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() },
        attemptedDates = get<ArrayList<String>>("v2_manual_shift.attempted").orEmpty().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(),
        noteBody = get<String>("v2_manual_shift.note").orEmpty(),
        noteAttachments = get<ArrayList<String>>("v2_manual_shift.images").orEmpty().mapNotNull(::decodeManualDraft),
        stage = get<String>(KEY_STAGE)?.let { stored ->
            V2ManualShiftLoadStage.entries.firstOrNull { it.name == stored }
        } ?: V2ManualShiftLoadStage.IDLE,
        timelineId = get<String>(KEY_TIMELINE_ID)?.toUuidOrNull(),
        sector = get<String>(KEY_SECTOR)?.let { stored ->
            WorkSector.entries.firstOrNull { it.name == stored }
        },
        selectedDates = get<ArrayList<String>>(KEY_SELECTED_DATES)
            .orEmpty()
            .mapNotNull { stored -> runCatching { LocalDate.parse(stored) }.getOrNull() }
            .toSet(),
        selectedTemplateId = get<String>(KEY_TEMPLATE_ID)?.toUuidOrNull(),
        selectedTemplateFingerprint = get<String>(KEY_TEMPLATE_FINGERPRINT),
        position = get<String>(KEY_POSITION).orEmpty(),
        creationRequest = readWorkCreationRequest(KEY_CREATION_REQUEST),
        creationResult = readWorkCreationResult(KEY_CREATION_RESULT),
        occupiedPolicy = get<String>(KEY_OCCUPIED_POLICY)?.let { stored ->
            OccupiedDatePolicy.entries.firstOrNull { it.name == stored }
        },
        occupiedDates = get<ArrayList<String>>(KEY_OCCUPIED_DATES)
            .orEmpty()
            .mapNotNull { stored -> runCatching { LocalDate.parse(stored) }.getOrNull() }
            .toSet(),
        acknowledgedWarnings = get<ArrayList<String>>(KEY_ACKNOWLEDGED_WARNINGS).orEmpty(),
        reviewFingerprint = get<String>(KEY_REVIEW_FINGERPRINT),
        saveAfterPreparation = get<Boolean>(KEY_SAVE_AFTER_PREPARATION) ?: false,
        pendingDayOff = get<Boolean>(KEY_PENDING_DAY_OFF),
    )

internal fun SavedStateHandle.writeV2ManualShiftLoadState(state: V2ManualShiftLoadPersistedState) {
    this["v2_manual_shift.operation"] = state.operationId?.toString()
    this["v2_manual_shift.timestamp"] = state.operationTimestamp?.toString()
    this["v2_manual_shift.attempted"] = ArrayList(state.attemptedDates.map(LocalDate::toString))
    this["v2_manual_shift.note"] = state.noteBody
    this["v2_manual_shift.images"] = ArrayList(state.noteAttachments.map { it.encodeManualDraft() })
    if (state.stage == V2ManualShiftLoadStage.IDLE) {
        listOf(
            KEY_STAGE,
            KEY_TIMELINE_ID,
            KEY_SECTOR,
            KEY_SELECTED_DATES,
            KEY_TEMPLATE_ID,
            KEY_TEMPLATE_FINGERPRINT,
            KEY_POSITION,
            KEY_CREATION_REQUEST,
            KEY_CREATION_RESULT,
            KEY_OCCUPIED_POLICY,
            KEY_OCCUPIED_DATES,
            KEY_ACKNOWLEDGED_WARNINGS,
            KEY_REVIEW_FINGERPRINT,
            KEY_SAVE_AFTER_PREPARATION,
            KEY_PENDING_DAY_OFF,
        ).forEach { key -> remove<Any>(key) }
        return
    }
    this[KEY_STAGE] = state.stage.name
    this[KEY_TIMELINE_ID] = state.timelineId?.toString()
    this[KEY_SECTOR] = state.sector?.name
    this[KEY_SELECTED_DATES] = ArrayList(state.selectedDates.sorted().map(LocalDate::toString))
    this[KEY_TEMPLATE_ID] = state.selectedTemplateId?.toString()
    this[KEY_TEMPLATE_FINGERPRINT] = state.selectedTemplateFingerprint
    this[KEY_POSITION] = state.position
    writeWorkCreationRequest(KEY_CREATION_REQUEST, state.creationRequest)
    writeWorkCreationResult(KEY_CREATION_RESULT, state.creationResult)
    this[KEY_OCCUPIED_POLICY] = state.occupiedPolicy?.name
    this[KEY_OCCUPIED_DATES] = ArrayList(state.occupiedDates.sorted().map(LocalDate::toString))
    this[KEY_ACKNOWLEDGED_WARNINGS] = ArrayList(state.acknowledgedWarnings)
    this[KEY_REVIEW_FINGERPRINT] = state.reviewFingerprint
    this[KEY_SAVE_AFTER_PREPARATION] = state.saveAfterPreparation
    this[KEY_PENDING_DAY_OFF] = state.pendingDayOff
}

private fun String.toUuidOrNull(): UUID? = runCatching(UUID::fromString).getOrNull()

private const val KEY_STAGE = "v2_manual_shift.stage"
private const val KEY_TIMELINE_ID = "v2_manual_shift.timeline_id"
private const val KEY_SECTOR = "v2_manual_shift.sector"
private const val KEY_SELECTED_DATES = "v2_manual_shift.selected_dates"
private const val KEY_TEMPLATE_ID = "v2_manual_shift.template_id"
private const val KEY_TEMPLATE_FINGERPRINT = "v2_manual_shift.template_fingerprint"
private const val KEY_POSITION = "v2_manual_shift.position"
private const val KEY_OCCUPIED_POLICY = "v2_manual_shift.occupied_policy"
private const val KEY_OCCUPIED_DATES = "v2_manual_shift.occupied_dates"
private const val KEY_ACKNOWLEDGED_WARNINGS = "v2_manual_shift.acknowledged_warnings"
private const val KEY_REVIEW_FINGERPRINT = "v2_manual_shift.review_fingerprint"
private const val KEY_SAVE_AFTER_PREPARATION = "v2_manual_shift.save_after_preparation"
private const val KEY_PENDING_DAY_OFF = "v2_manual_shift.pending_day_off"

private const val KEY_CREATION_REQUEST = "v2_manual_shift.creation_request"
private const val KEY_CREATION_RESULT = "v2_manual_shift.creation_result"

/** An archived last template does not change the identity of an existing load draft. */
internal fun V2ManualShiftLoadUiState.belongsTo(root: WorkSetupState): Boolean {
    val currentTimelineId = when (root) {
        is WorkSetupState.V2Ready -> root.timelineId
        is WorkSetupState.V2NeedsFirstSet -> root.timelineId
        else -> null
    }
    return isActive && timelineId != null && timelineId == currentTimelineId
}
