package com.blackatsystems.miguardia.ui.management

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.blackatsystems.miguardia.core.domain.model.ExtraWorkClassWriteResult
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualClassSelection
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDifferenceChoice
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftActualFragmentDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftActualSaveMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftActualWriteResult
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.buildShiftActualSaveMutation
import com.blackatsystems.miguardia.core.domain.model.resolveActualLocalDateTime
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import java.time.Clock
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class V2ShiftActualSurface {
    NONE,
    EDITOR,
    CLASS_CATALOG,
}

enum class V2ShiftActualStage {
    IDENTITY,
    ACTUAL_TIME,
    CLASSIFICATION,
    REVIEW,
}

sealed interface V2ShiftActualRowState {
    data object Loading : V2ShiftActualRowState
    data class Error(val message: String) : V2ShiftActualRowState
    data class Content(
        val expectation: ShiftActualExpectation,
        val canRegister: Boolean,
        val unavailableMessage: String?,
    ) : V2ShiftActualRowState
}

data class V2ActualFragmentInput(
    val id: UUID,
    val startDate: String,
    val startTime: String,
    val startOffset: String? = null,
    val endDate: String,
    val endTime: String,
    val endOffset: String? = null,
)

data class V2ActualEditorDraft(
    val startDate: String = "",
    val startTime: String = "",
    val startOffset: String? = null,
    val endDate: String = "",
    val endTime: String = "",
    val endOffset: String? = null,
    val reason: String = "",
    val explanation: String = "",
    val choice: ShiftActualDifferenceChoice? = null,
    val selectedClassId: UUID? = null,
    val selectedClassUpdatedAt: String? = null,
    val isCreatingInlineClass: Boolean = false,
    val inlineClassId: UUID? = null,
    val inlineClassName: String = "",
    val inlineHelpsReference: Boolean? = null,
    val inlineDedicatedSummary: Boolean? = null,
    val fragments: List<V2ActualFragmentInput> = emptyList(),
)

data class V2ShiftActualEditorState(
    val expectation: ShiftActualExpectation,
    val ordinal: Int,
    val count: Int,
    val ownerDate: LocalDate,
    val stage: V2ShiftActualStage,
    val draft: V2ActualEditorDraft,
    val preparedMutation: ShiftActualSaveMutation? = null,
    val errorMessage: String? = null,
    val sourceConflict: Boolean = false,
    val showReturnConfirmation: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
)

data class V2ExtraClassEditorState(
    val expected: ExtraWorkClass? = null,
    val id: UUID,
    val name: String = "",
    val helpsReference: Boolean? = null,
    val dedicatedSummary: Boolean? = null,
    val errorMessage: String? = null,
)

data class V2ShiftActualUiState(
    val inspectedDate: LocalDate? = null,
    val rows: Map<UUID, V2ShiftActualRowState> = emptyMap(),
    val surface: V2ShiftActualSurface = V2ShiftActualSurface.NONE,
    val editor: V2ShiftActualEditorState? = null,
    val classes: List<ExtraWorkClass> = emptyList(),
    val classEditor: V2ExtraClassEditorState? = null,
    val isLoadingClasses: Boolean = false,
    val classesLoadError: String? = null,
    val isSaving: Boolean = false,
    val isRefreshingSource: Boolean = false,
    val infoMessage: String? = null,
    val successSequence: Int = 0,
    val restoredDraftError: String? = null,
) {
    val isBlocking: Boolean
        get() = surface != V2ShiftActualSurface.NONE || restoredDraftError != null
}

class V2ShiftActualViewModel(
    repository: ShiftActualRepository,
    clock: Clock,
    uuidProvider: () -> UUID,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val coordinator = V2ShiftActualCoordinator(
        repository = repository,
        clock = clock,
        uuidProvider = uuidProvider,
        scope = viewModelScope,
        restoredDraft = savedStateHandle.readActualDraft(),
        persistDraft = savedStateHandle::writeActualDraft,
    )
    val uiState: StateFlow<V2ShiftActualUiState> = coordinator.uiState

    fun resume(rootState: WorkSetupState) = coordinator.resume(rootState)
    fun inspectDay(
        rootState: WorkSetupState,
        date: LocalDate,
        shifts: List<Shift>,
        referenceInstant: Instant,
    ) = coordinator.inspectDay(rootState, date, shifts, referenceInstant)
    fun retryInspection() = coordinator.retryInspection()
    fun clearInspection() = coordinator.clearInspection()
    fun begin(shiftId: UUID, ordinal: Int, count: Int, ownerDate: LocalDate) =
        coordinator.begin(shiftId, ordinal, count, ownerDate)
    fun updateDraft(transform: (V2ActualEditorDraft) -> V2ActualEditorDraft) = coordinator.updateDraft(transform)
    fun next() = coordinator.next()
    fun back() = coordinator.back()
    fun addFragment() = coordinator.addFragment()
    fun updateFragment(id: UUID, transform: (V2ActualFragmentInput) -> V2ActualFragmentInput) =
        coordinator.updateFragment(id, transform)
    fun removeFragment(id: UUID) = coordinator.removeFragment(id)
    fun startInlineClass() = coordinator.startInlineClass()
    fun cancelInlineClass() = coordinator.cancelInlineClass()
    fun retryClasses() = coordinator.retryClasses()
    fun refreshEditorSource() = coordinator.refreshEditorSource()
    fun save() = coordinator.save()
    fun requestReturnToPlanned(shiftId: UUID) = coordinator.requestReturnToPlanned(shiftId)
    fun dismissReturnConfirmation() = coordinator.dismissReturnConfirmation()
    fun confirmReturnToPlanned() = coordinator.confirmReturnToPlanned()
    fun openCatalog(rootState: WorkSetupState) = coordinator.openCatalog(rootState)
    fun startNewClass() = coordinator.startNewClass()
    fun editClass(id: UUID) = coordinator.editClass(id)
    fun updateClassEditor(transform: (V2ExtraClassEditorState) -> V2ExtraClassEditorState) =
        coordinator.updateClassEditor(transform)
    fun saveClass() = coordinator.saveClass()
    fun toggleClassActive(id: UUID) = coordinator.toggleClassActive(id)
    fun cancelClassEditor() = coordinator.cancelClassEditor()
    fun close() = coordinator.close()
    fun dismissDiscardConfirmation() = coordinator.dismissDiscardConfirmation()
    fun confirmDiscard() = coordinator.confirmDiscard()
    fun discardUnavailableRestoredDraft() = coordinator.discardUnavailableRestoredDraft()
    fun clearMessage() = coordinator.clearMessage()
    fun consumeSuccess(sequence: Int) = coordinator.consumeSuccess(sequence)

    class Factory(
        private val repository: ShiftActualRepository,
        private val clock: Clock,
        private val uuidProvider: () -> UUID = UUID::randomUUID,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(V2ShiftActualViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return V2ShiftActualViewModel(
                repository,
                clock,
                uuidProvider,
                extras.createSavedStateHandle(),
            ) as T
        }
    }
}

internal class V2ShiftActualCoordinator(
    private val repository: ShiftActualRepository,
    private val clock: Clock,
    private val uuidProvider: () -> UUID,
    private val scope: CoroutineScope,
    restoredDraft: PersistedActualDraft? = null,
    private val persistDraft: (PersistedActualDraft?) -> Unit = {},
) {
    private val _uiState = MutableStateFlow(V2ShiftActualUiState())
    val uiState: StateFlow<V2ShiftActualUiState> = _uiState
    private var inspectionJob: Job? = null
    private var classJob: Job? = null
    private var observedClassContext: ReadyContext? = null
    private var readyContext: ReadyContext? = null
    private var lastInspection: InspectionRequest? = null
    private var pendingRestore: PersistedActualDraft? = restoredDraft
    private val referenceInstant = MutableStateFlow(clock.instant())

    fun resume(rootState: WorkSetupState) {
        val previous = readyContext
        val context = rootState.readyContextOrNull()
        readyContext = context
        if (context == null && !_uiState.value.isSaving) {
            classJob?.cancel()
            classJob = null
            observedClassContext = null
            close()
        } else if (
            context != null &&
            context != previous &&
            _uiState.value.surface == V2ShiftActualSurface.CLASS_CATALOG
        ) {
            observeClasses(context)
        }
    }

    fun inspectDay(
        rootState: WorkSetupState,
        date: LocalDate,
        shifts: List<Shift>,
        currentReferenceInstant: Instant = clock.instant(),
    ) {
        resume(rootState)
        val context = readyContext ?: return clearInspection()
        referenceInstant.value = currentReferenceInstant
        val ordered = shifts.sortedWith(compareBy(Shift::startAt, Shift::endAt, Shift::id))
        val request = InspectionRequest(rootState, context, date, ordered)
        if (request == lastInspection && inspectionJob?.isActive == true) return
        lastInspection = request
        inspectionJob?.cancel()
        _uiState.update { state ->
            state.copy(
                inspectedDate = date,
                rows = ordered.associate { it.id to V2ShiftActualRowState.Loading },
            )
        }
        if (ordered.isEmpty()) {
            revealUnavailableRestoredDraftIfApplicable(date)
            return
        }
        inspectionJob = scope.launch {
            val expectationFlow = combine(ordered.map { repository.observeExpectation(it.id) }) { values ->
                values.toList()
            }
            combine(expectationFlow, referenceInstant) { expectations, now -> expectations to now }
                .catch { error ->
                    if (error is CancellationException) throw error
                    val errorRows = ordered.associate { shift ->
                        shift.id to V2ShiftActualRowState.Error(
                            "No pudimos leer el horario real. Reintentá sin asumir que está vacío.",
                        )
                    }
                    _uiState.update { state ->
                        state.copy(rows = errorRows)
                    }
                    restoreEditorIfPossible(errorRows)
                }
                .collect { (expectations, now) ->
                    val rows = ordered.mapIndexed { index, shift ->
                        val expectation = expectations[index]
                        shift.id to if (expectation == null) {
                            V2ShiftActualRowState.Error("La jornada dejó de estar disponible como jornada V2.")
                        } else {
                            expectation.toRowState(now)
                        }
                    }.toMap(linkedMapOf())
                    _uiState.update { it.copy(rows = rows) }
                    restoreEditorIfPossible(rows)
                    detectOpenEditorConflict(rows)
                }
        }
    }

    fun retryInspection() {
        val request = lastInspection ?: return
        lastInspection = null
        inspectDay(request.rootState, request.date, request.shifts, referenceInstant.value)
    }

    fun clearInspection() {
        inspectionJob?.cancel()
        inspectionJob = null
        lastInspection = null
        if (_uiState.value.surface != V2ShiftActualSurface.EDITOR) {
            _uiState.update { it.copy(inspectedDate = null, rows = emptyMap()) }
        }
    }

    fun begin(shiftId: UUID, ordinal: Int, count: Int, ownerDate: LocalDate): Boolean {
        if (_uiState.value.isSaving || _uiState.value.isRefreshingSource) return false
        val row = _uiState.value.rows[shiftId] as? V2ShiftActualRowState.Content ?: return false
        if (!row.canRegister) return false
        observeClasses(row.expectation.classContext())
        val editor = newEditor(row.expectation, ordinal, count, ownerDate)
        _uiState.update {
            it.copy(surface = V2ShiftActualSurface.EDITOR, editor = editor, infoMessage = null)
        }
        persist(editor)
        return true
    }

    fun updateDraft(transform: (V2ActualEditorDraft) -> V2ActualEditorDraft) {
        if (_uiState.value.isSaving || _uiState.value.isRefreshingSource) return
        updateEditor { editor ->
            editor.copy(
                draft = transform(editor.draft),
                preparedMutation = null,
                errorMessage = null,
            )
        }
    }

    fun next() {
        val editor = _uiState.value.editor ?: return
        if (_uiState.value.isSaving || _uiState.value.isRefreshingSource || editor.sourceConflict) return
        when (editor.stage) {
            V2ShiftActualStage.IDENTITY -> updateEditor { it.copy(stage = V2ShiftActualStage.ACTUAL_TIME) }
            V2ShiftActualStage.ACTUAL_TIME -> {
                val parsed = parseActualDraft(editor) ?: return
                val plannedMinutes = Duration.between(
                    editor.expectation.planned.shift.startAt,
                    editor.expectation.planned.shift.endAt,
                ).toMinutes()
                val actualMinutes = Duration.between(parsed.first, parsed.second).toMinutes()
                updateEditor {
                    it.copy(
                        stage = if (actualMinutes > plannedMinutes) {
                            V2ShiftActualStage.CLASSIFICATION
                        } else {
                            V2ShiftActualStage.ACTUAL_TIME
                        },
                        draft = if (actualMinutes <= plannedMinutes) {
                            it.draft.copy(
                                choice = null,
                                selectedClassId = null,
                                selectedClassUpdatedAt = null,
                                isCreatingInlineClass = false,
                                inlineClassId = null,
                                inlineClassName = "",
                                inlineHelpsReference = null,
                                inlineDedicatedSummary = null,
                                fragments = emptyList(),
                            )
                        } else it.draft,
                    )
                }
                if (actualMinutes <= plannedMinutes) prepareReview()
            }

            V2ShiftActualStage.CLASSIFICATION -> prepareReview()
            V2ShiftActualStage.REVIEW -> save()
        }
    }

    fun back() {
        val editor = _uiState.value.editor ?: return
        if (_uiState.value.isSaving || _uiState.value.isRefreshingSource) return
        when (editor.stage) {
            V2ShiftActualStage.IDENTITY -> close()
            V2ShiftActualStage.ACTUAL_TIME -> updateEditor { it.copy(stage = V2ShiftActualStage.IDENTITY) }
            V2ShiftActualStage.CLASSIFICATION -> updateEditor { it.copy(stage = V2ShiftActualStage.ACTUAL_TIME) }
            V2ShiftActualStage.REVIEW -> {
                val mutation = editor.preparedMutation
                val plannedMinutes = Duration.between(
                    editor.expectation.planned.shift.startAt,
                    editor.expectation.planned.shift.endAt,
                ).toMinutes()
                val actualMinutes = mutation?.replacement?.totalMinutes ?: plannedMinutes
                updateEditor {
                    it.copy(
                        stage = if (actualMinutes > plannedMinutes) {
                            V2ShiftActualStage.CLASSIFICATION
                        } else V2ShiftActualStage.ACTUAL_TIME,
                        preparedMutation = null,
                    )
                }
            }
        }
    }

    fun addFragment() {
        _uiState.value.editor ?: return
        updateDraft { draft ->
            draft.copy(
                fragments = draft.fragments + V2ActualFragmentInput(
                    id = uuidProvider(),
                    startDate = "",
                    startTime = "",
                    endDate = "",
                    endTime = "",
                ),
            )
        }
    }

    fun updateFragment(id: UUID, transform: (V2ActualFragmentInput) -> V2ActualFragmentInput) {
        updateDraft { draft ->
            draft.copy(fragments = draft.fragments.map { if (it.id == id) transform(it) else it })
        }
    }

    fun removeFragment(id: UUID) {
        updateDraft { it.copy(fragments = it.fragments.filterNot { fragment -> fragment.id == id }) }
    }

    fun startInlineClass() {
        updateDraft {
            it.copy(
                isCreatingInlineClass = true,
                selectedClassId = null,
                selectedClassUpdatedAt = null,
                inlineClassId = it.inlineClassId ?: uuidProvider(),
                inlineHelpsReference = null,
                inlineDedicatedSummary = null,
            )
        }
    }

    fun cancelInlineClass() {
        updateDraft {
            it.copy(
                isCreatingInlineClass = false,
                inlineClassId = null,
                inlineClassName = "",
                inlineHelpsReference = null,
                inlineDedicatedSummary = null,
            )
        }
    }

    fun retryClasses() {
        val context = _uiState.value.editor?.expectation?.classContext()
            ?: readyContext
            ?: return
        observeClasses(context, force = true)
    }

    fun refreshEditorSource() {
        val state = _uiState.value
        val editor = state.editor ?: return
        if (state.isSaving || state.isRefreshingSource) return
        _uiState.update { it.copy(isRefreshingSource = true) }
        scope.launch {
            try {
                val refreshed = repository.getExpectation(editor.expectation.planned.shift.id)
                if (refreshed == null) {
                    updateEditorRefreshError("La jornada ya no está disponible. El borrador se conserva para que puedas revisarlo.")
                    return@launch
                }
                val updated = editor.copy(
                    expectation = refreshed,
                    preparedMutation = null,
                    sourceConflict = refreshed.planned.shift.status != ShiftStatus.PLANNED,
                    errorMessage = if (refreshed.planned.shift.status == ShiftStatus.PLANNED) {
                        "La jornada se actualizó. Revisá el borrador antes de guardar."
                    } else {
                        "La jornada ya no está planificada y no admite horario real."
                    },
                    showReturnConfirmation = false,
                    showDiscardConfirmation = false,
                )
                _uiState.update { it.copy(editor = updated, isRefreshingSource = false) }
                persist(updated)
                observeClasses(refreshed.classContext(), force = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateEditorRefreshError("No pudimos refrescar la jornada. El borrador sigue disponible para reintentar.")
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val editor = state.editor ?: return
        if (state.isSaving || state.isRefreshingSource || editor.sourceConflict) return
        val mutation = editor.preparedMutation ?: prepareMutation(editor) ?: return
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            try {
                when (repository.save(mutation)) {
                    is ShiftActualWriteResult.Saved -> confirmPersistedWrite(
                        shiftId = mutation.expectation.planned.shift.id,
                        expectedActual = mutation.replacement,
                        message = "El horario real quedó guardado.",
                    )
                    ShiftActualWriteResult.DuplicateClassName -> updateEditorSavingError(
                        "Ya existe una clase equivalente. Elegila del catálogo o usá otro nombre; el borrador se conserva.",
                    )
                    ShiftActualWriteResult.Conflict -> showWriteConflict()
                    ShiftActualWriteResult.ReturnedToPlanned -> Unit
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateEditorSavingError("No pudimos guardar. El borrador sigue disponible para reintentar.")
            }
        }
    }

    fun requestReturnToPlanned(shiftId: UUID): Boolean {
        if (_uiState.value.isSaving || _uiState.value.isRefreshingSource) return false
        val open = _uiState.value.editor
        if (open?.expectation?.planned?.shift?.id == shiftId) {
            updateEditor { it.copy(showReturnConfirmation = true) }
            return true
        }
        val row = _uiState.value.rows[shiftId] as? V2ShiftActualRowState.Content ?: return false
        if (row.expectation.previousActual == null) return false
        val orderedIds = lastInspection?.shifts.orEmpty().map { it.id }
        val index = orderedIds.indexOf(shiftId).coerceAtLeast(0)
        val editor = newEditor(
            expectation = row.expectation,
            ordinal = index + 1,
            count = orderedIds.size.coerceAtLeast(1),
            ownerDate = row.expectation.planned.shift.localStartDate,
        ).copy(showReturnConfirmation = true)
        _uiState.update { it.copy(surface = V2ShiftActualSurface.EDITOR, editor = editor) }
        persist(editor)
        return true
    }

    fun dismissReturnConfirmation() = updateEditor { it.copy(showReturnConfirmation = false) }

    fun confirmReturnToPlanned() {
        val state = _uiState.value
        val expectation = state.editor?.expectation ?: return
        if (state.isSaving || state.isRefreshingSource || expectation.previousActual == null) return
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            try {
                when (repository.returnToPlanned(expectation)) {
                    ShiftActualWriteResult.ReturnedToPlanned -> confirmPersistedWrite(
                        shiftId = expectation.planned.shift.id,
                        expectedActual = null,
                        message = "La jornada volvió al horario planificado.",
                    )
                    ShiftActualWriteResult.Conflict -> showWriteConflict()
                    ShiftActualWriteResult.DuplicateClassName -> updateEditorSavingError(
                        "La operación devolvió un resultado inesperado. Refrescá la jornada y volvé a intentarlo.",
                        conflict = true,
                    )
                    is ShiftActualWriteResult.Saved -> Unit
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateEditorSavingError("No pudimos quitar la corrección. Reintentá con el borrador intacto.")
            }
        }
    }

    fun openCatalog(rootState: WorkSetupState) {
        resume(rootState)
        val context = readyContext ?: return
        observeClasses(context)
        _uiState.update {
            it.copy(
                surface = V2ShiftActualSurface.CLASS_CATALOG,
                editor = null,
                classEditor = null,
                infoMessage = null,
            )
        }
        persistDraft(null)
    }

    fun startNewClass() {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(classEditor = V2ExtraClassEditorState(id = uuidProvider()))
        }
    }

    fun editClass(id: UUID) {
        if (_uiState.value.isSaving) return
        val selected = _uiState.value.classes.singleOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(
                classEditor = V2ExtraClassEditorState(
                    expected = selected,
                    id = selected.id,
                    name = selected.name,
                    helpsReference = selected.helpsMeetHoursReference,
                    dedicatedSummary = selected.showDedicatedSummary,
                ),
            )
        }
    }

    fun updateClassEditor(transform: (V2ExtraClassEditorState) -> V2ExtraClassEditorState) {
        if (_uiState.value.isSaving) return
        _uiState.update { state ->
            state.copy(classEditor = state.classEditor?.let(transform)?.copy(errorMessage = null))
        }
    }

    fun saveClass() {
        val state = _uiState.value
        val context = readyContext ?: return
        val editor = state.classEditor ?: return
        if (state.isSaving) return
        val helps = editor.helpsReference ?: return updateClassError("Respondé si estas horas extra cuentan para tu meta.")
        val dedicated = editor.dedicatedSummary ?: return updateClassError("Respondé si querés ver este tipo por separado.")
        val replacement = try {
            val timestamp = nextTimestamp(editor.expected?.updatedAt)
            editor.expected?.updated(
                name = editor.name,
                helpsMeetHoursReference = helps,
                showDedicatedSummary = dedicated,
                timestamp = timestamp,
            ) ?: ExtraWorkClass.create(
                id = editor.id,
                timelineId = context.timelineId,
                sector = context.sector,
                name = editor.name,
                helpsMeetHoursReference = helps,
                showDedicatedSummary = dedicated,
                timestamp = timestamp,
            )
        } catch (error: IllegalArgumentException) {
            return updateClassError(error.message ?: "Revisá los datos de la clase.")
        }
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            try {
                when (repository.saveExtraWorkClass(editor.expected, replacement)) {
                    is ExtraWorkClassWriteResult.Saved -> _uiState.update {
                        it.copy(
                            isSaving = false,
                            classEditor = null,
                            infoMessage = "La clase extra quedó guardada.",
                        )
                    }
                    ExtraWorkClassWriteResult.DuplicateName -> updateClassSavingError(
                        "Ya existe una clase equivalente en esta forma de trabajar.",
                    )
                    ExtraWorkClassWriteResult.Conflict -> updateClassSavingError(
                        "La clase cambió. Revisá el catálogo y volvé a intentarlo.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateClassSavingError("No pudimos guardar la clase. Tus respuestas siguen disponibles.")
            }
        }
    }

    fun toggleClassActive(id: UUID) {
        val selected = _uiState.value.classes.singleOrNull { it.id == id } ?: return
        if (_uiState.value.isSaving) return
        val replacement = try {
            selected.updated(isActive = !selected.isActive, timestamp = nextTimestamp(selected.updatedAt))
        } catch (_: IllegalArgumentException) {
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        scope.launch {
            try {
                when (repository.saveExtraWorkClass(selected, replacement)) {
                    is ExtraWorkClassWriteResult.Saved -> _uiState.update {
                        it.copy(
                            isSaving = false,
                            infoMessage = if (replacement.isActive) "La clase quedó reactivada." else "La clase quedó archivada.",
                        )
                    }
                    ExtraWorkClassWriteResult.Conflict,
                    ExtraWorkClassWriteResult.DuplicateName,
                    -> _uiState.update {
                        it.copy(isSaving = false, infoMessage = "La clase cambió. Revisá el catálogo.")
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, infoMessage = "No pudimos cambiar el estado de la clase.") }
            }
        }
    }

    fun cancelClassEditor() = _uiState.update { it.copy(classEditor = null) }

    fun close() {
        val state = _uiState.value
        if (state.isSaving || state.isRefreshingSource) return
        val editor = state.editor
        if (state.surface == V2ShiftActualSurface.EDITOR && editor != null && editor.hasDirtyDraft()) {
            updateEditor { it.copy(showDiscardConfirmation = true) }
            return
        }
        closeImmediately()
    }

    fun dismissDiscardConfirmation() = updateEditor { it.copy(showDiscardConfirmation = false) }

    fun confirmDiscard() {
        if (_uiState.value.isSaving || _uiState.value.isRefreshingSource) return
        closeImmediately()
    }

    fun discardUnavailableRestoredDraft() {
        if (_uiState.value.isSaving || _uiState.value.isRefreshingSource) return
        pendingRestore = null
        persistDraft(null)
        _uiState.update { it.copy(restoredDraftError = null) }
    }

    private fun closeImmediately() {
        _uiState.update {
            it.copy(
                surface = V2ShiftActualSurface.NONE,
                editor = null,
                classEditor = null,
                isRefreshingSource = false,
            )
        }
        persistDraft(null)
    }

    fun clearMessage() = _uiState.update { it.copy(infoMessage = null) }

    fun consumeSuccess(sequence: Int) {
        if (_uiState.value.successSequence == sequence) {
            _uiState.update { it.copy(successSequence = 0) }
        }
    }

    private fun prepareReview() {
        val editor = _uiState.value.editor ?: return
        val mutation = prepareMutation(editor) ?: return
        updateEditor {
            it.copy(
                stage = V2ShiftActualStage.REVIEW,
                preparedMutation = mutation,
                errorMessage = null,
            )
        }
    }

    private fun prepareMutation(editor: V2ShiftActualEditorState): ShiftActualSaveMutation? {
        if (
            editor.draft.isCreatingInlineClass &&
            (_uiState.value.isLoadingClasses || _uiState.value.classesLoadError != null)
        ) {
            showEditorError("Reintentá la lectura de clases antes de crear una nueva, así evitamos duplicados.")
            return null
        }
        val parsed = parseActualDraft(editor) ?: return null
        val domainDraft = try {
            ShiftActualDraft(
                actualStart = parsed.first,
                actualEnd = parsed.second,
                differenceReason = editor.draft.reason,
                explanation = editor.draft.explanation,
                differenceChoice = editor.draft.choice,
                classSelection = editor.draft.toClassSelection(editor.expectation, _uiState.value.classes),
                fragments = editor.draft.fragments.map { fragment ->
                    ShiftActualFragmentDraft(
                        id = fragment.id,
                        start = parseLocal(fragment.startDate, fragment.startTime, fragment.startOffset, editor.expectation),
                        end = parseLocal(fragment.endDate, fragment.endTime, fragment.endOffset, editor.expectation),
                    )
                },
            )
        } catch (error: IllegalArgumentException) {
            showEditorError(error.message ?: "Revisá la clasificación y los fragmentos.")
            return null
        }
        return try {
            val timestamp = nextTimestamp(editor.expectation.previousActual?.record?.updatedAt)
            val mutation = buildShiftActualSaveMutation(
                expectation = editor.expectation,
                draft = domainDraft,
                clock = clock,
                timestamp = timestamp,
            )
            val classToCreate = mutation?.classToCreate
            if (
                classToCreate != null &&
                _uiState.value.classes.any { existing ->
                    existing.id != classToCreate.id &&
                        existing.normalizedNameKey == classToCreate.normalizedNameKey
                }
            ) {
                showEditorError("Ya existe una clase equivalente. Elegila del catálogo o usá otro nombre.")
                return null
            }
            if (mutation == null) {
                updateEditor {
                    it.copy(
                        errorMessage = if (editor.expectation.previousActual == null) {
                            "Ese horario coincide con el planificado. No hay una corrección para guardar."
                        } else {
                            "Ese horario coincide con el planificado. Usá Volver al horario planificado."
                        },
                        showReturnConfirmation = editor.expectation.previousActual != null,
                    )
                }
            }
            mutation
        } catch (error: IllegalArgumentException) {
            showEditorError(error.message ?: "Revisá el horario real antes de continuar.")
            null
        }
    }

    private fun parseActualDraft(editor: V2ShiftActualEditorState): Pair<Instant, Instant>? = try {
        parseLocal(
            editor.draft.startDate,
            editor.draft.startTime,
            editor.draft.startOffset,
            editor.expectation,
        ) to parseLocal(
            editor.draft.endDate,
            editor.draft.endTime,
            editor.draft.endOffset,
            editor.expectation,
        )
    } catch (error: IllegalArgumentException) {
        showEditorError(error.message ?: "Ingresá fechas y horarios completos.")
        null
    }

    private fun parseLocal(
        rawDate: String,
        rawTime: String,
        rawOffset: String?,
        expectation: ShiftActualExpectation,
    ): Instant {
        val dateTime = try {
            LocalDateTime.of(LocalDate.parse(rawDate.trim()), LocalTime.parse(rawTime.trim()))
        } catch (error: DateTimeParseException) {
            throw IllegalArgumentException("Usá fecha AAAA-MM-DD y hora HH:mm.", error)
        }
        return resolveActualLocalDateTime(
            localDateTime = dateTime,
            zoneId = expectation.planned.shift.zoneId,
            selectedOffset = rawOffset?.trim()?.takeIf(String::isNotEmpty)?.let { value ->
                try {
                    ZoneOffset.of(value)
                } catch (error: DateTimeException) {
                    throw IllegalArgumentException("Usá un offset válido, por ejemplo -03:00.", error)
                }
            },
        )
    }

    private fun newEditor(
        expectation: ShiftActualExpectation,
        ordinal: Int,
        count: Int,
        ownerDate: LocalDate,
    ): V2ShiftActualEditorState {
        val shift = expectation.planned.shift
        val actual = expectation.previousActual
        val start = actual?.record?.actualStart ?: shift.startAt
        val end = actual?.record?.actualEnd ?: shift.endAt
        val zone = shift.zoneId
        val startLocal = start.atZone(zone)
        val endLocal = end.atZone(zone)
        val fragments = actual?.extraIntervals.orEmpty().map { interval ->
            val fragmentStart = interval.start.atZone(zone)
            val fragmentEnd = interval.end.atZone(zone)
            V2ActualFragmentInput(
                id = interval.id,
                startDate = fragmentStart.toLocalDate().toString(),
                startTime = fragmentStart.toLocalTime().toString(),
                startOffset = fragmentStart.offset.id,
                endDate = fragmentEnd.toLocalDate().toString(),
                endTime = fragmentEnd.toLocalTime().toString(),
                endOffset = fragmentEnd.offset.id,
            )
        }
        val plannedMinutes = Duration.between(shift.startAt, shift.endAt).toMinutes()
        val actualMinutes = Duration.between(start, end).toMinutes()
        return V2ShiftActualEditorState(
            expectation = expectation,
            ordinal = ordinal,
            count = count,
            ownerDate = ownerDate,
            stage = V2ShiftActualStage.IDENTITY,
            draft = V2ActualEditorDraft(
                startDate = startLocal.toLocalDate().toString(),
                startTime = startLocal.toLocalTime().toString(),
                startOffset = actual?.let { startLocal.offset.id },
                endDate = endLocal.toLocalDate().toString(),
                endTime = endLocal.toLocalTime().toString(),
                endOffset = actual?.let { endLocal.offset.id },
                reason = actual?.record?.differenceReason.orEmpty(),
                explanation = actual?.record?.explanation.orEmpty(),
                choice = when {
                    actual == null || actualMinutes <= plannedMinutes -> null
                    actual.extraIntervals.isEmpty() -> ShiftActualDifferenceChoice.ALL_REGULAR
                    else -> ShiftActualDifferenceChoice.EXTRA_CLASS
                },
                selectedClassId = expectation.observedClass?.id,
                selectedClassUpdatedAt = expectation.observedClass?.updatedAt?.toString(),
                fragments = fragments,
            ),
        )
    }

    private fun observeClasses(context: ReadyContext, force: Boolean = false) {
        if (!force && classJob?.isActive == true && observedClassContext == context) return
        classJob?.cancel()
        observedClassContext = context
        _uiState.update { it.copy(isLoadingClasses = true, classesLoadError = null) }
        classJob = scope.launch {
            repository.observeExtraWorkClasses(context.timelineId, context.sector)
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            isLoadingClasses = false,
                            classesLoadError = "No pudimos leer las clases extra. Reintentá antes de elegir o crear una.",
                        )
                    }
                }
                .collect { classes ->
                    _uiState.update {
                        it.copy(classes = classes, isLoadingClasses = false, classesLoadError = null)
                    }
                    val editor = _uiState.value.editor
                    if (
                        editor?.stage == V2ShiftActualStage.REVIEW &&
                        editor.preparedMutation == null &&
                        !editor.sourceConflict
                    ) {
                        prepareReview()
                    }
                }
        }
    }

    private fun restoreEditorIfPossible(rows: Map<UUID, V2ShiftActualRowState>) {
        val restored = pendingRestore ?: return
        val rowState = rows[restored.shiftId]
        if (rowState == null) {
            if (_uiState.value.inspectedDate == restored.ownerDate) revealUnavailableRestoredDraft(restored)
            return
        }
        if (rowState is V2ShiftActualRowState.Loading) return
        val row = rowState as? V2ShiftActualRowState.Content
        if (row == null) {
            revealUnavailableRestoredDraft(restored)
            return
        }
        pendingRestore = null
        val sourceMatches = restored.expectationFingerprint == row.expectation.fingerprint()
        val editor = V2ShiftActualEditorState(
            expectation = row.expectation,
            ordinal = restored.ordinal,
            count = restored.count,
            ownerDate = restored.ownerDate,
            stage = restored.stage,
            draft = restored.draft,
            sourceConflict = !sourceMatches,
            errorMessage = if (sourceMatches) null else "La jornada cambió desde que se guardó el borrador. Refrescá y revisá de nuevo.",
            showReturnConfirmation = restored.showReturnConfirmation,
            showDiscardConfirmation = restored.showDiscardConfirmation,
        )
        _uiState.update {
            it.copy(
                surface = V2ShiftActualSurface.EDITOR,
                editor = editor,
                restoredDraftError = null,
            )
        }
        observeClasses(row.expectation.classContext())
        if (
            sourceMatches &&
            restored.stage == V2ShiftActualStage.REVIEW &&
            !_uiState.value.isLoadingClasses &&
            _uiState.value.classesLoadError == null
        ) {
            prepareReview()
        }
    }

    private fun revealUnavailableRestoredDraftIfApplicable(inspectedDate: LocalDate) {
        val restored = pendingRestore ?: return
        if (restored.ownerDate == inspectedDate) revealUnavailableRestoredDraft(restored)
    }

    private fun revealUnavailableRestoredDraft(restored: PersistedActualDraft) {
        _uiState.update {
            it.copy(
                restoredDraftError =
                    "No pudimos recuperar la jornada ${restored.shiftId} del borrador. " +
                        "Podés reintentar la lectura o descartarlo conscientemente.",
            )
        }
    }

    private fun detectOpenEditorConflict(rows: Map<UUID, V2ShiftActualRowState>) {
        val editor = _uiState.value.editor ?: return
        val current = (rows[editor.expectation.planned.shift.id] as? V2ShiftActualRowState.Content)?.expectation
            ?: return
        if (current != editor.expectation && !_uiState.value.isSaving && !_uiState.value.isRefreshingSource) {
            updateEditor {
                it.copy(
                    sourceConflict = true,
                    errorMessage = "La jornada o su contexto cambió. El borrador se conserva; refrescá antes de guardar.",
                )
            }
        }
    }

    private suspend fun confirmPersistedWrite(
        shiftId: UUID,
        expectedActual: com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate?,
        message: String,
    ) {
        val confirmed = try {
            repository.getExpectation(shiftId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            updateEditorSavingError(
                "La escritura terminó, pero no pudimos releer el estado persistido. Refrescá la jornada conservando el borrador.",
                conflict = true,
            )
            return
        }
        if (confirmed == null || confirmed.previousActual != expectedActual) {
            updateEditorSavingError(
                "La escritura terminó, pero no pudimos confirmar el estado persistido. Refrescá la jornada conservando el borrador.",
                conflict = true,
            )
            return
        }
        val refreshedRow = confirmed.toRowState(referenceInstant.value)
        _uiState.update { state ->
            state.copy(rows = state.rows + (shiftId to refreshedRow))
        }
        finishSuccessfulWrite(message)
    }

    private fun finishSuccessfulWrite(message: String) {
        persistDraft(null)
        _uiState.update {
            it.copy(
                surface = V2ShiftActualSurface.NONE,
                editor = null,
                isSaving = false,
                isRefreshingSource = false,
                infoMessage = message,
                successSequence = it.successSequence + 1,
            )
        }
    }

    private fun showWriteConflict() {
        updateEditorSavingError("La jornada o configuración cambió. El borrador se conserva; refrescá y revisá de nuevo.", conflict = true)
    }

    private fun updateEditorRefreshError(message: String) {
        _uiState.update { state ->
            state.copy(
                isRefreshingSource = false,
                editor = state.editor?.copy(
                    sourceConflict = true,
                    errorMessage = message,
                    showReturnConfirmation = false,
                ),
            )
        }
    }

    private fun updateEditorSavingError(message: String, conflict: Boolean = false) {
        _uiState.update { state ->
            state.copy(
                isSaving = false,
                editor = state.editor?.copy(
                    errorMessage = message,
                    sourceConflict = state.editor.sourceConflict || conflict,
                    showReturnConfirmation = false,
                ),
            )
        }
    }

    private fun updateClassError(message: String) {
        _uiState.update { state -> state.copy(classEditor = state.classEditor?.copy(errorMessage = message)) }
    }

    private fun updateClassSavingError(message: String) {
        _uiState.update { state ->
            state.copy(isSaving = false, classEditor = state.classEditor?.copy(errorMessage = message))
        }
    }

    private fun showEditorError(message: String) = updateEditor { it.copy(errorMessage = message) }

    private fun updateEditor(transform: (V2ShiftActualEditorState) -> V2ShiftActualEditorState) {
        var updated: V2ShiftActualEditorState? = null
        _uiState.update { state ->
            val current = state.editor ?: return@update state
            transform(current).also { updated = it }.let { state.copy(editor = it) }
        }
        updated?.let(::persist)
    }

    private fun persist(editor: V2ShiftActualEditorState) {
        persistDraft(
            PersistedActualDraft(
                shiftId = editor.expectation.planned.shift.id,
                expectationFingerprint = editor.expectation.fingerprint(),
                ordinal = editor.ordinal,
                count = editor.count,
                ownerDate = editor.ownerDate,
                stage = editor.stage,
                draft = editor.draft,
                showReturnConfirmation = editor.showReturnConfirmation,
                showDiscardConfirmation = editor.showDiscardConfirmation,
            ),
        )
    }

    private fun nextTimestamp(previous: Instant?): Instant {
        return try {
            val now = Instant.ofEpochMilli(clock.instant().toEpochMilli())
            if (previous != null && !now.isAfter(previous)) previous.plusMillis(1) else now
        } catch (error: DateTimeException) {
            throw IllegalArgumentException("La fecha de actualización excede el rango admitido.", error)
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("La fecha de actualización excede el rango admitido.", error)
        }
    }

    internal data class ReadyContext(val timelineId: UUID, val sector: WorkSector)

    private data class InspectionRequest(
        val rootState: WorkSetupState,
        val context: ReadyContext,
        val date: LocalDate,
        val shifts: List<Shift>,
    )
}

private fun ShiftActualExpectation.classContext(): V2ShiftActualCoordinator.ReadyContext =
    V2ShiftActualCoordinator.ReadyContext(planned.snapshot.timelineId, planned.snapshot.sector)

private fun WorkSetupState.readyContextOrNull(): V2ShiftActualCoordinator.ReadyContext? = when (this) {
    is WorkSetupState.V2NeedsFirstSet -> V2ShiftActualCoordinator.ReadyContext(
        timelineId,
        configurationRevision.value.sector,
    )
    is WorkSetupState.V2Ready -> V2ShiftActualCoordinator.ReadyContext(
        timelineId,
        configurationRevision.value.sector,
    )
    else -> null
}

private fun V2ActualEditorDraft.toClassSelection(
    expectation: ShiftActualExpectation,
    classes: List<ExtraWorkClass>,
): ShiftActualClassSelection? = when {
    choice != ShiftActualDifferenceChoice.EXTRA_CLASS -> null
    isCreatingInlineClass -> ShiftActualClassSelection.NewDraft(
        id = requireNotNull(inlineClassId),
        name = inlineClassName,
        helpsMeetHoursReference = inlineHelpsReference,
        showDedicatedSummary = inlineDedicatedSummary,
    )
    else -> {
        val id = requireNotNull(selectedClassId) { "Elegí una clase extra" }
        val selected = classes.singleOrNull { it.id == id }
            ?: expectation.observedClass?.takeIf { it.id == id }
            ?: throw IllegalArgumentException("La clase elegida dejó de estar disponible")
        require(selected.updatedAt.toString() == selectedClassUpdatedAt) {
            "La clase elegida cambió. Volvé a seleccionarla para confirmar su versión actual."
        }
        ShiftActualClassSelection.Existing(selected)
    }
}

private fun ShiftActualExpectation.fingerprint(): String = toString()

private fun V2ShiftActualEditorState.hasDirtyDraft(): Boolean {
    val shift = expectation.planned.shift
    val actual = expectation.previousActual
    val start = actual?.record?.actualStart ?: shift.startAt
    val end = actual?.record?.actualEnd ?: shift.endAt
    val startLocal = start.atZone(shift.zoneId)
    val endLocal = end.atZone(shift.zoneId)
    val initialFragments = actual?.extraIntervals.orEmpty().map { interval ->
        val fragmentStart = interval.start.atZone(shift.zoneId)
        val fragmentEnd = interval.end.atZone(shift.zoneId)
        V2ActualFragmentInput(
            id = interval.id,
            startDate = fragmentStart.toLocalDate().toString(),
            startTime = fragmentStart.toLocalTime().toString(),
            startOffset = fragmentStart.offset.id,
            endDate = fragmentEnd.toLocalDate().toString(),
            endTime = fragmentEnd.toLocalTime().toString(),
            endOffset = fragmentEnd.offset.id,
        )
    }
    val plannedMinutes = Duration.between(shift.startAt, shift.endAt).toMinutes()
    val actualMinutes = Duration.between(start, end).toMinutes()
    val initial = V2ActualEditorDraft(
        startDate = startLocal.toLocalDate().toString(),
        startTime = startLocal.toLocalTime().toString(),
        startOffset = actual?.let { startLocal.offset.id },
        endDate = endLocal.toLocalDate().toString(),
        endTime = endLocal.toLocalTime().toString(),
        endOffset = actual?.let { endLocal.offset.id },
        reason = actual?.record?.differenceReason.orEmpty(),
        explanation = actual?.record?.explanation.orEmpty(),
        choice = when {
            actual == null || actualMinutes <= plannedMinutes -> null
            actual.extraIntervals.isEmpty() -> ShiftActualDifferenceChoice.ALL_REGULAR
            else -> ShiftActualDifferenceChoice.EXTRA_CLASS
        },
        selectedClassId = expectation.observedClass?.id,
        selectedClassUpdatedAt = expectation.observedClass?.updatedAt?.toString(),
        fragments = initialFragments,
    )
    return draft != initial
}

private fun ShiftActualExpectation.toRowState(now: Instant): V2ShiftActualRowState.Content {
    val statusMessage = when (planned.shift.status) {
        ShiftStatus.PLANNED -> null
        ShiftStatus.CANCELLED -> "Una jornada cancelada no admite horario real."
        ShiftStatus.ABSENT -> "Una jornada marcada como ausente no admite horario real."
    }
    return V2ShiftActualRowState.Content(
        expectation = this,
        canRegister = statusMessage == null &&
            (previousActual != null || !now.isBefore(planned.shift.endAt)),
        unavailableMessage = statusMessage ?: if (
            previousActual == null && now.isBefore(planned.shift.endAt)
        ) {
            "Podrás registrar el horario real cuando alcance el final planificado."
        } else null,
    )
}

internal data class PersistedActualDraft(
    val shiftId: UUID,
    val expectationFingerprint: String,
    val ordinal: Int,
    val count: Int,
    val ownerDate: LocalDate,
    val stage: V2ShiftActualStage,
    val draft: V2ActualEditorDraft,
    val showReturnConfirmation: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
)

private fun SavedStateHandle.writeActualDraft(value: PersistedActualDraft?) {
    if (value == null) {
        ACTUAL_KEYS.forEach { key -> remove<Any?>(key) }
        return
    }
    this[ACTUAL_SHIFT_ID] = value.shiftId.toString()
    this[ACTUAL_EXPECTATION] = value.expectationFingerprint
    this[ACTUAL_ORDINAL] = value.ordinal
    this[ACTUAL_COUNT] = value.count
    this[ACTUAL_OWNER_DATE] = value.ownerDate.toString()
    this[ACTUAL_STAGE] = value.stage.name
    this[ACTUAL_START_DATE] = value.draft.startDate
    this[ACTUAL_START_TIME] = value.draft.startTime
    this[ACTUAL_START_OFFSET] = value.draft.startOffset
    this[ACTUAL_END_DATE] = value.draft.endDate
    this[ACTUAL_END_TIME] = value.draft.endTime
    this[ACTUAL_END_OFFSET] = value.draft.endOffset
    this[ACTUAL_REASON] = value.draft.reason
    this[ACTUAL_EXPLANATION] = value.draft.explanation
    this[ACTUAL_CHOICE] = value.draft.choice?.name
    this[ACTUAL_CLASS_ID] = value.draft.selectedClassId?.toString()
    this[ACTUAL_CLASS_UPDATED_AT] = value.draft.selectedClassUpdatedAt
    this[ACTUAL_INLINE] = value.draft.isCreatingInlineClass
    this[ACTUAL_INLINE_ID] = value.draft.inlineClassId?.toString()
    this[ACTUAL_INLINE_NAME] = value.draft.inlineClassName
    this[ACTUAL_INLINE_HELPS] = value.draft.inlineHelpsReference?.toString()
    this[ACTUAL_INLINE_DEDICATED] = value.draft.inlineDedicatedSummary?.toString()
    this[ACTUAL_FRAGMENTS] = ArrayList(value.draft.fragments.map(V2ActualFragmentInput::encode))
    this[ACTUAL_RETURN_CONFIRMATION] = value.showReturnConfirmation
    this[ACTUAL_DISCARD_CONFIRMATION] = value.showDiscardConfirmation
}

private fun SavedStateHandle.readActualDraft(): PersistedActualDraft? = try {
    val shiftId = get<String>(ACTUAL_SHIFT_ID)?.let(UUID::fromString) ?: return null
    PersistedActualDraft(
        shiftId = shiftId,
        expectationFingerprint = requireNotNull(get<String>(ACTUAL_EXPECTATION)),
        ordinal = requireNotNull(get<Int>(ACTUAL_ORDINAL)),
        count = requireNotNull(get<Int>(ACTUAL_COUNT)),
        ownerDate = LocalDate.parse(requireNotNull(get<String>(ACTUAL_OWNER_DATE))),
        stage = V2ShiftActualStage.valueOf(requireNotNull(get<String>(ACTUAL_STAGE))),
        draft = V2ActualEditorDraft(
            startDate = get<String>(ACTUAL_START_DATE).orEmpty(),
            startTime = get<String>(ACTUAL_START_TIME).orEmpty(),
            startOffset = get(ACTUAL_START_OFFSET),
            endDate = get<String>(ACTUAL_END_DATE).orEmpty(),
            endTime = get<String>(ACTUAL_END_TIME).orEmpty(),
            endOffset = get(ACTUAL_END_OFFSET),
            reason = get<String>(ACTUAL_REASON).orEmpty(),
            explanation = get<String>(ACTUAL_EXPLANATION).orEmpty(),
            choice = get<String>(ACTUAL_CHOICE)?.let(ShiftActualDifferenceChoice::valueOf),
            selectedClassId = get<String>(ACTUAL_CLASS_ID)?.let(UUID::fromString),
            selectedClassUpdatedAt = get<String>(ACTUAL_CLASS_UPDATED_AT),
            isCreatingInlineClass = get<Boolean>(ACTUAL_INLINE) ?: false,
            inlineClassId = get<String>(ACTUAL_INLINE_ID)?.let(UUID::fromString),
            inlineClassName = get<String>(ACTUAL_INLINE_NAME).orEmpty(),
            inlineHelpsReference = get<String>(ACTUAL_INLINE_HELPS)?.toBooleanStrictOrNull(),
            inlineDedicatedSummary = get<String>(ACTUAL_INLINE_DEDICATED)?.toBooleanStrictOrNull(),
            fragments = get<ArrayList<String>>(ACTUAL_FRAGMENTS).orEmpty().map(::decodeActualFragment),
        ),
        showReturnConfirmation = get<Boolean>(ACTUAL_RETURN_CONFIRMATION) ?: false,
        showDiscardConfirmation = get<Boolean>(ACTUAL_DISCARD_CONFIRMATION) ?: false,
    )
} catch (_: RuntimeException) {
    null
}

private fun V2ActualFragmentInput.encode(): String = listOf(
    id.toString(), startDate, startTime, startOffset.orEmpty(), endDate, endTime, endOffset.orEmpty(),
).joinToString("|")

private fun decodeActualFragment(value: String): V2ActualFragmentInput {
    val parts = value.split('|')
    require(parts.size == 7)
    return V2ActualFragmentInput(
        id = UUID.fromString(parts[0]),
        startDate = parts[1],
        startTime = parts[2],
        startOffset = parts[3].ifEmpty { null },
        endDate = parts[4],
        endTime = parts[5],
        endOffset = parts[6].ifEmpty { null },
    )
}

private const val ACTUAL_SHIFT_ID = "v2_actual_shift_id"
private const val ACTUAL_EXPECTATION = "v2_actual_expectation"
private const val ACTUAL_ORDINAL = "v2_actual_ordinal"
private const val ACTUAL_COUNT = "v2_actual_count"
private const val ACTUAL_OWNER_DATE = "v2_actual_owner_date"
private const val ACTUAL_STAGE = "v2_actual_stage"
private const val ACTUAL_START_DATE = "v2_actual_start_date"
private const val ACTUAL_START_TIME = "v2_actual_start_time"
private const val ACTUAL_START_OFFSET = "v2_actual_start_offset"
private const val ACTUAL_END_DATE = "v2_actual_end_date"
private const val ACTUAL_END_TIME = "v2_actual_end_time"
private const val ACTUAL_END_OFFSET = "v2_actual_end_offset"
private const val ACTUAL_REASON = "v2_actual_reason"
private const val ACTUAL_EXPLANATION = "v2_actual_explanation"
private const val ACTUAL_CHOICE = "v2_actual_choice"
private const val ACTUAL_CLASS_ID = "v2_actual_class_id"
private const val ACTUAL_CLASS_UPDATED_AT = "v2_actual_class_updated_at"
private const val ACTUAL_INLINE = "v2_actual_inline"
private const val ACTUAL_INLINE_ID = "v2_actual_inline_id"
private const val ACTUAL_INLINE_NAME = "v2_actual_inline_name"
private const val ACTUAL_INLINE_HELPS = "v2_actual_inline_helps"
private const val ACTUAL_INLINE_DEDICATED = "v2_actual_inline_dedicated"
private const val ACTUAL_FRAGMENTS = "v2_actual_fragments"
private const val ACTUAL_RETURN_CONFIRMATION = "v2_actual_return_confirmation"
private const val ACTUAL_DISCARD_CONFIRMATION = "v2_actual_discard_confirmation"

private val ACTUAL_KEYS = listOf(
    ACTUAL_SHIFT_ID, ACTUAL_EXPECTATION, ACTUAL_ORDINAL, ACTUAL_COUNT, ACTUAL_OWNER_DATE,
    ACTUAL_STAGE, ACTUAL_START_DATE, ACTUAL_START_TIME, ACTUAL_START_OFFSET, ACTUAL_END_DATE,
    ACTUAL_END_TIME, ACTUAL_END_OFFSET, ACTUAL_REASON, ACTUAL_EXPLANATION, ACTUAL_CHOICE,
    ACTUAL_CLASS_ID, ACTUAL_CLASS_UPDATED_AT, ACTUAL_INLINE, ACTUAL_INLINE_ID, ACTUAL_INLINE_NAME,
    ACTUAL_INLINE_HELPS, ACTUAL_INLINE_DEDICATED, ACTUAL_FRAGMENTS, ACTUAL_RETURN_CONFIRMATION,
    ACTUAL_DISCARD_CONFIRMATION,
)
