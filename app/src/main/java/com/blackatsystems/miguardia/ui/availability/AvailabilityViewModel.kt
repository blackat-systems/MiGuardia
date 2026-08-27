package com.blackatsystems.miguardia.ui.availability

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.model.AvailabilityActiveWorkInterval
import com.blackatsystems.miguardia.core.domain.model.AvailabilityBreakdown
import com.blackatsystems.miguardia.core.domain.model.AvailabilityTotals
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowDraft
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowExpectation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowMutation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowWriteResult
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.buildAvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.calculateAvailabilityBreakdown
import com.blackatsystems.miguardia.core.domain.model.resolveAvailabilityActiveWorkIntervals
import com.blackatsystems.miguardia.core.domain.model.resolveActualLocalDateTime
import com.blackatsystems.miguardia.core.domain.model.sumAvailabilityBreakdowns
import com.blackatsystems.miguardia.core.domain.repository.AvailabilityWindowRepository
import com.blackatsystems.miguardia.core.domain.repository.IndependentExtraWorkRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityWriteResult
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

enum class AvailabilitySurface {
    NONE,
    OVERVIEW,
    CONFIG_EDITOR,
    CONFIG_REVIEW,
    WINDOW_EDITOR,
    WINDOW_REVIEW,
    DELETE_CONFIRMATION,
}

enum class AvailabilityLoadState { LOADING, CONTENT, ERROR }

data class AvailabilityConfigurationDraft(
    val label: AvailabilityLabel? = null,
    val effectiveDate: String = "",
)

data class AvailabilityWindowDraftState(
    val recordId: UUID = UUID.randomUUID(),
    val ownerDate: LocalDate? = null,
    val startTime: String = "",
    val endDate: String = "",
    val endTime: String = "",
)

data class AvailabilityWindowReview(
    val record: AvailabilityWindowRecord,
    val expectation: AvailabilityWindowExpectation,
    val breakdown: AvailabilityBreakdown,
    val isProtected: Boolean,
)

data class AvailabilitySource(
    val history: WorkConfigurationHistory,
    val windows: List<AvailabilityWindowRecord>,
    val breakdowns: Map<UUID, AvailabilityBreakdown>,
    val totals: AvailabilityTotals?,
    val protectedWindowIds: Set<UUID>,
    val activeWork: List<AvailabilityActiveWorkInterval>?,
    val protectedRanges: List<ClosedRange<LocalDate>>?,
    val today: LocalDate,
    val calculationError: String? = null,
) {
    fun windowsOn(date: LocalDate): List<AvailabilityWindowRecord> = windows
        .filter { it.ownerLocalDate == date }
        .sortedWith(compareBy(AvailabilityWindowRecord::start, AvailabilityWindowRecord::id))

    fun labelOn(date: LocalDate): AvailabilityLabel? = history.timeline.valueAt(date)?.availabilityLabel
}

data class AvailabilityUiState(
    val loadState: AvailabilityLoadState = AvailabilityLoadState.LOADING,
    val source: AvailabilitySource? = null,
    val surface: AvailabilitySurface = AvailabilitySurface.NONE,
    val configurationDraft: AvailabilityConfigurationDraft? = null,
    val configurationReview: WorkConfigurationAvailabilityMutation? = null,
    val windowDraft: AvailabilityWindowDraftState? = null,
    val windowReview: AvailabilityWindowReview? = null,
    val deletingRecord: AvailabilityWindowRecord? = null,
    val deleteExpectation: AvailabilityWindowExpectation? = null,
    val showDiscardConfirmation: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
) {
    val isBlocking: Boolean get() = surface != AvailabilitySurface.NONE

    fun canCreateOn(date: LocalDate): Boolean =
        loadState == AvailabilityLoadState.CONTENT && source?.labelOn(date) != null

    fun windowsOn(date: LocalDate): List<AvailabilityWindowRecord> = source?.windowsOn(date).orEmpty()
}

class AvailabilityViewModel(
    private val configurationRepository: WorkConfigurationRepository,
    private val repository: AvailabilityWindowRepository,
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
        AvailabilityUiState(
            surface = savedStateHandle.get<String>(KEY_SURFACE)
                ?.let { runCatching { AvailabilitySurface.valueOf(it) }.getOrNull() }
                ?: AvailabilitySurface.NONE,
            configurationDraft = readConfigurationDraft(),
            windowDraft = readWindowDraft(),
            showDiscardConfirmation = savedStateHandle.get<Boolean>(KEY_CONFIRM_DISCARD) == true,
        ),
    )
    val uiState: StateFlow<AvailabilityUiState> = _uiState
    private var observationJob: Job? = null
    private var restoringConfigurationReview: Boolean = false
    private var restoringWindowReview: Boolean = false
    private var restoringDeleteReview: Boolean = false

    init {
        startObservation()
    }

    fun refresh() = startObservation()
    fun retry() = startObservation()
    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }

    fun openOverview() = setSurface(AvailabilitySurface.OVERVIEW)
    fun close() {
        val state = _uiState.value
        if (state.isSaving) return
        if (
            state.surface in setOf(
                AvailabilitySurface.CONFIG_EDITOR,
                AvailabilitySurface.CONFIG_REVIEW,
                AvailabilitySurface.WINDOW_EDITOR,
                AvailabilitySurface.WINDOW_REVIEW,
            ) && (state.configurationDraft != null || state.windowDraft != null)
        ) {
            _uiState.value = state.copy(showDiscardConfirmation = true)
            savedStateHandle[KEY_CONFIRM_DISCARD] = true
            return
        }
        closeNow()
    }

    fun dismissDiscard() {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(showDiscardConfirmation = false)
        savedStateHandle.remove<Boolean>(KEY_CONFIRM_DISCARD)
    }

    fun confirmDiscard() {
        if (_uiState.value.isSaving) return
        closeNow()
    }

    private fun closeNow() {
        clearConfigurationState()
        clearWindowState()
        clearDeleteState()
        _uiState.value = _uiState.value.copy(
            surface = AvailabilitySurface.NONE,
            configurationDraft = null,
            configurationReview = null,
            windowDraft = null,
            windowReview = null,
            deletingRecord = null,
            deleteExpectation = null,
            showDiscardConfirmation = false,
            message = null,
        )
        savedStateHandle[KEY_SURFACE] = AvailabilitySurface.NONE.name
        savedStateHandle.remove<Boolean>(KEY_CONFIRM_DISCARD)
    }

    fun openConfiguration() {
        if (_uiState.value.isSaving) return
        val source = _uiState.value.source ?: return
        val today = LocalDate.now(clock.withZone(zoneId))
        val current = source.history.timeline.valueAt(today)?.availabilityLabel
        updateConfigurationDraft(
            AvailabilityConfigurationDraft(label = current, effectiveDate = today.toString()),
        )
        setSurface(AvailabilitySurface.CONFIG_EDITOR)
    }

    fun updateConfigurationDraft(draft: AvailabilityConfigurationDraft) {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(
            configurationDraft = draft,
            configurationReview = null,
            message = null,
        )
        savedStateHandle[KEY_CONFIG_LABEL] = draft.label?.name ?: NONE_LABEL
        savedStateHandle[KEY_CONFIG_DATE] = draft.effectiveDate
        savedStateHandle.remove<String>(KEY_CONFIG_REVISION_ID)
        savedStateHandle.remove<String>(KEY_CONFIG_EXPECTATION)
    }

    fun reviewConfiguration() {
        if (_uiState.value.isSaving) return
        val history = _uiState.value.source?.history ?: return
        val draft = _uiState.value.configurationDraft ?: return
        runCatching { buildConfigurationMutation(history, draft) }
            .onSuccess { mutation ->
                _uiState.value = _uiState.value.copy(configurationReview = mutation)
                savedStateHandle[KEY_CONFIG_EXPECTATION] = history.availabilityFingerprint()
                setSurface(AvailabilitySurface.CONFIG_REVIEW)
            }
            .onFailure { showError(it) }
    }

    fun backConfiguration() {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(configurationReview = null)
        savedStateHandle.remove<String>(KEY_CONFIG_EXPECTATION)
        setSurface(AvailabilitySurface.CONFIG_EDITOR)
    }

    fun saveConfiguration() {
        val state = _uiState.value
        if (state.isSaving) return
        val draft = state.configurationDraft ?: return
        val mutation = state.configurationReview ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, message = null)
            runCatching { configurationRepository.applyAvailabilityMutation(mutation) }
                .onSuccess { result ->
                    when (result) {
                        is WorkConfigurationAvailabilityWriteResult.Saved -> {
                            _uiState.value = _uiState.value.copy(
                                surface = AvailabilitySurface.OVERVIEW,
                                configurationDraft = null,
                                configurationReview = null,
                                isSaving = false,
                                message = "La disponibilidad quedó configurada desde ${draft.effectiveDate}.",
                            )
                            clearConfigurationState()
                            savedStateHandle[KEY_SURFACE] = AvailabilitySurface.OVERVIEW.name
                        }
                        WorkConfigurationAvailabilityWriteResult.Conflict -> conflictMessage()
                    }
                }
                .onFailure { showError(it, saving = false) }
        }
    }

    fun openCreate(ownerDate: LocalDate) {
        if (_uiState.value.isSaving) return
        if (!_uiState.value.canCreateOn(ownerDate)) {
            _uiState.value = _uiState.value.copy(
                message = "Primero configurá cómo llamás a la disponibilidad para esa fecha.",
            )
            return
        }
        savedStateHandle[KEY_WINDOW_ORIGINAL] = NEW_WINDOW
        updateWindowDraft(
            AvailabilityWindowDraftState(
                recordId = uuidProvider(),
                ownerDate = ownerDate,
                startTime = "08:00",
                endDate = ownerDate.toString(),
                endTime = "12:00",
            ),
        )
        setSurface(AvailabilitySurface.WINDOW_EDITOR)
    }

    fun openCorrect(record: AvailabilityWindowRecord) {
        if (_uiState.value.isSaving) return
        savedStateHandle[KEY_WINDOW_ORIGINAL] = record.editFingerprint()
        updateWindowDraft(
            AvailabilityWindowDraftState(
                recordId = record.id,
                ownerDate = record.ownerLocalDate,
                startTime = record.start.atZone(record.zoneId).toLocalTime().toString(),
                endDate = record.end.atZone(record.zoneId).toLocalDate().toString(),
                endTime = record.end.atZone(record.zoneId).toLocalTime().toString(),
            ),
        )
        setSurface(AvailabilitySurface.WINDOW_EDITOR)
    }

    fun updateWindowDraft(draft: AvailabilityWindowDraftState) {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(windowDraft = draft, windowReview = null, message = null)
        savedStateHandle[KEY_WINDOW_ID] = draft.recordId.toString()
        savedStateHandle[KEY_WINDOW_OWNER] = draft.ownerDate?.toString()
        savedStateHandle[KEY_WINDOW_START] = draft.startTime
        savedStateHandle[KEY_WINDOW_END_DATE] = draft.endDate
        savedStateHandle[KEY_WINDOW_END] = draft.endTime
        savedStateHandle.remove<String>(KEY_WINDOW_EXPECTATION)
    }

    fun reviewWindow() {
        if (_uiState.value.isSaving) return
        val draft = _uiState.value.windowDraft ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, message = null)
            runCatching { prepareReview(draft) }
                .onSuccess { review ->
                    _uiState.value = _uiState.value.copy(
                        surface = AvailabilitySurface.WINDOW_REVIEW,
                        windowReview = review,
                        isSaving = false,
                    )
                    savedStateHandle[KEY_WINDOW_EXPECTATION] = review.expectation.availabilityFingerprint()
                    savedStateHandle[KEY_SURFACE] = AvailabilitySurface.WINDOW_REVIEW.name
                }
                .onFailure { showError(it, saving = false) }
        }
    }

    fun backWindow() {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(windowReview = null)
        savedStateHandle.remove<String>(KEY_WINDOW_EXPECTATION)
        setSurface(AvailabilitySurface.WINDOW_EDITOR)
    }

    fun saveWindow() {
        if (_uiState.value.isSaving) return
        val review = _uiState.value.windowReview ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, message = null)
            runCatching {
                repository.applyMutation(AvailabilityWindowMutation(review.expectation, review.record))
            }.onSuccess { result ->
                when (result) {
                    is AvailabilityWindowWriteResult.Saved -> {
                        val verb = if (review.expectation.previous == null) "registrada" else "corregida"
                        closeEditor("La ${result.record.labelSnapshot} quedó $verb.")
                    }
                    AvailabilityWindowWriteResult.Overlap -> {
                        _uiState.value = _uiState.value.copy(
                            surface = AvailabilitySurface.WINDOW_EDITOR,
                            windowReview = null,
                            isSaving = false,
                            message = "La disponibilidad se superpone con otra ventana. Los tramos contiguos sí están permitidos.",
                        )
                    }
                    AvailabilityWindowWriteResult.Conflict -> conflictMessage()
                    AvailabilityWindowWriteResult.Deleted -> error("La operación de guardado no puede eliminar")
                }
            }.onFailure { showError(it, saving = false) }
        }
    }

    fun requestDelete(record: AvailabilityWindowRecord) {
        if (_uiState.value.isSaving) return
        val source = _uiState.value.source ?: return
        _uiState.value = _uiState.value.copy(
            deletingRecord = record,
            deleteExpectation = null,
            surface = AvailabilitySurface.DELETE_CONFIRMATION,
            isSaving = true,
            message = null,
        )
        savedStateHandle[KEY_SURFACE] = AvailabilitySurface.DELETE_CONFIRMATION.name
        savedStateHandle[KEY_DELETING_ID] = record.id.toString()
        viewModelScope.launch {
            runCatching {
                val config = ResolvedWorkConfigurationRevision.resolve(source.history, record.ownerLocalDate)
                repository.captureExpectation(record.id, config, record.start, record.end)
            }.onSuccess { expectation ->
                if (expectation.previous != record) {
                    clearDeleteReviewWithConflict()
                } else {
                    _uiState.value = _uiState.value.copy(
                        deletingRecord = record,
                        deleteExpectation = expectation,
                        isSaving = false,
                    )
                    savedStateHandle[KEY_DELETE_EXPECTATION] = expectation.availabilityFingerprint()
                }
            }.onFailure { error ->
                clearDeleteState()
                _uiState.value = _uiState.value.copy(
                    deletingRecord = null,
                    deleteExpectation = null,
                    surface = AvailabilitySurface.NONE,
                    isSaving = false,
                    message = error.userMessage(),
                )
                savedStateHandle[KEY_SURFACE] = AvailabilitySurface.NONE.name
            }
        }
    }

    fun dismissDelete() {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(
            deletingRecord = null,
            deleteExpectation = null,
            surface = AvailabilitySurface.NONE,
            isSaving = false,
        )
        savedStateHandle[KEY_SURFACE] = AvailabilitySurface.NONE.name
        clearDeleteState()
    }

    fun confirmDelete() {
        val state = _uiState.value
        if (state.isSaving) return
        val expectation = state.deleteExpectation ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, message = null)
            runCatching {
                repository.applyMutation(AvailabilityWindowMutation(expectation, null))
            }.onSuccess { result ->
                when (result) {
                    AvailabilityWindowWriteResult.Deleted -> closeEditor("La disponibilidad fue eliminada.")
                    AvailabilityWindowWriteResult.Conflict -> conflictMessage()
                    else -> error("La eliminación devolvió un resultado inesperado")
                }
            }.onFailure { showError(it, saving = false) }
        }
    }

    private fun buildConfigurationMutation(
        history: WorkConfigurationHistory,
        draft: AvailabilityConfigurationDraft,
    ): WorkConfigurationAvailabilityMutation {
        val effectiveDate = parseDate(draft.effectiveDate, "fecha de vigencia")
        val previous = requireNotNull(history.timeline.revisionAt(effectiveDate)) {
            "La disponibilidad no puede comenzar antes de la configuración laboral"
        }
        val sameDate = history.timeline.revisions.singleOrNull { it.effectiveFrom == effectiveDate }
        val revisionId = sameDate?.id ?: savedStateHandle.get<String>(KEY_CONFIG_REVISION_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: uuidProvider().also { savedStateHandle[KEY_CONFIG_REVISION_ID] = it.toString() }
        return WorkConfigurationAvailabilityMutation(
            expectedHistory = history,
            revision = EffectiveRevision(
                id = revisionId,
                effectiveFrom = effectiveDate,
                value = previous.value.copy(availabilityLabel = draft.label),
            ),
        )
    }

    private suspend fun prepareReview(draft: AvailabilityWindowDraftState): AvailabilityWindowReview {
        val source = requireNotNull(_uiState.value.source)
        require(source.calculationError == null) {
            "No pudimos recalcular la disponibilidad. Los registros siguen visibles, pero no se puede revisar una escritura todavía."
        }
        val ownerDate = requireNotNull(draft.ownerDate) { "Falta la fecha dueña" }
        val startTime = parseTime(draft.startTime, "inicio")
        val endDate = parseDate(draft.endDate, "fecha final")
        val endTime = parseTime(draft.endTime, "final")
        val start = resolveActualLocalDateTime(LocalDateTime.of(ownerDate, startTime), zoneId)
        val end = resolveActualLocalDateTime(LocalDateTime.of(endDate, endTime), zoneId)
        val originalFingerprint = requireNotNull(savedStateHandle.get<String>(KEY_WINDOW_ORIGINAL)) {
            "No pudimos recuperar la versión original de la disponibilidad. Volvé a abrirla desde el Calendario."
        }
        val currentRecord = source.windows.singleOrNull { it.id == draft.recordId }
        val previous = if (originalFingerprint == NEW_WINDOW) {
            require(currentRecord == null) {
                "Ya existe una disponibilidad con esta identidad. Volvé a iniciar la carga."
            }
            null
        } else {
            require(currentRecord != null && currentRecord.editFingerprint() == originalFingerprint) {
                "La disponibilidad cambió desde que abriste el editor. Volvé a abrirla antes de corregir."
            }
            currentRecord
        }
        val configuration = ResolvedWorkConfigurationRevision.resolve(source.history, ownerDate)
        val observedStart = minOf(start, previous?.start ?: start)
        val observedEnd = maxOf(end, previous?.end ?: end)
        val expectation = repository.captureExpectation(
            id = previous?.id,
            configuration = configuration,
            windowStart = observedStart,
            windowEnd = observedEnd,
        )
        val record = buildAvailabilityWindowRecord(
            draft = AvailabilityWindowDraft(draft.recordId, ownerDate, zoneId, start, end),
            configuration = configuration,
            timestamp = nextVersionTimestamp(previous),
            previous = previous,
        )
        if (expectation.overlaps(record)) {
            error("La disponibilidad se superpone con otra ventana. Los tramos contiguos sí están permitidos.")
        }
        val isProtected = expectation.protectionFingerprint.isNotEmpty()
        val breakdown = source.breakdowns[record.id] ?: calculateForDraft(record, source, isProtected)
        return AvailabilityWindowReview(
            record = record,
            expectation = expectation,
            breakdown = breakdown,
            isProtected = record.id in source.protectedWindowIds || isProtected,
        )
    }

    private fun calculateForDraft(
        record: AvailabilityWindowRecord,
        source: AvailabilitySource,
        isProtected: Boolean,
    ): AvailabilityBreakdown = calculateAvailabilityBreakdown(
        record,
        requireNotNull(source.activeWork),
        isProtected || requireNotNull(source.protectedRanges).any { record.ownerLocalDate in it },
        clock,
    )

    private fun nextVersionTimestamp(previous: AvailabilityWindowRecord?): Instant {
        val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        return previous?.updatedAt?.plusMillis(1)?.let { maxOf(now, it) } ?: now
    }

    private fun startObservation() {
        observationJob?.cancel()
        _uiState.value = _uiState.value.copy(loadState = AvailabilityLoadState.LOADING, message = null)
        observationJob = viewModelScope.launch {
            sourceFlow()
                .catch { error -> emit(SourceLoad.Error(error.userMessage())) }
                .collect { load ->
                    when (load) {
                        is SourceLoad.Content -> {
                            val deletingId = savedStateHandle.get<String>(KEY_DELETING_ID)
                                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            val restoredDeletingRecord = deletingId?.let { id ->
                                load.source.windows.singleOrNull { it.id == id }
                            }
                            _uiState.value = _uiState.value.copy(
                                loadState = AvailabilityLoadState.CONTENT,
                                source = load.source,
                                deletingRecord = if (_uiState.value.deleteExpectation != null) {
                                    _uiState.value.deletingRecord
                                } else {
                                    restoredDeletingRecord
                                },
                            )
                            restoreConfigurationReviewIfNeeded(load.source)
                            restoreWindowReviewIfNeeded()
                            restoreDeleteReviewIfNeeded(load.source)
                        }
                        is SourceLoad.Error -> _uiState.value = _uiState.value.copy(
                            loadState = AvailabilityLoadState.ERROR,
                            message = load.message,
                        )
                    }
                }
        }
    }

    private fun restoreConfigurationReviewIfNeeded(source: AvailabilitySource) {
        val state = _uiState.value
        val draft = state.configurationDraft
        if (
            state.surface != AvailabilitySurface.CONFIG_REVIEW ||
            state.configurationReview != null ||
            draft == null ||
            restoringConfigurationReview
        ) return
        restoringConfigurationReview = true
        runCatching {
            val expectedFingerprint = requireNotNull(savedStateHandle.get<String>(KEY_CONFIG_EXPECTATION))
            require(source.history.availabilityFingerprint() == expectedFingerprint)
            buildConfigurationMutation(source.history, draft)
        }.onSuccess { mutation ->
            _uiState.value = _uiState.value.copy(configurationReview = mutation)
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(
                surface = AvailabilitySurface.CONFIG_EDITOR,
                configurationReview = null,
                message = "Los datos cambiaron durante la recreación. Revisá nuevamente. ${error.userMessage()}",
            )
            savedStateHandle.remove<String>(KEY_CONFIG_EXPECTATION)
            savedStateHandle[KEY_SURFACE] = AvailabilitySurface.CONFIG_EDITOR.name
        }
        restoringConfigurationReview = false
    }

    private suspend fun restoreWindowReviewIfNeeded() {
        val state = _uiState.value
        val draft = state.windowDraft
        if (
            state.surface != AvailabilitySurface.WINDOW_REVIEW ||
            state.windowReview != null ||
            draft == null ||
            restoringWindowReview
        ) return
        restoringWindowReview = true
        runCatching {
            val expectedFingerprint = requireNotNull(savedStateHandle.get<String>(KEY_WINDOW_EXPECTATION))
            prepareReview(draft).also { review ->
                require(review.expectation.availabilityFingerprint() == expectedFingerprint)
            }
        }.onSuccess { review ->
            _uiState.value = _uiState.value.copy(windowReview = review)
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(
                surface = AvailabilitySurface.WINDOW_EDITOR,
                windowReview = null,
                message = "Los datos cambiaron durante la recreación. Revisá nuevamente. ${error.userMessage()}",
            )
            savedStateHandle.remove<String>(KEY_WINDOW_EXPECTATION)
            savedStateHandle[KEY_SURFACE] = AvailabilitySurface.WINDOW_EDITOR.name
        }
        restoringWindowReview = false
    }

    private suspend fun restoreDeleteReviewIfNeeded(source: AvailabilitySource) {
        val state = _uiState.value
        val record = state.deletingRecord
        if (
            state.surface != AvailabilitySurface.DELETE_CONFIRMATION ||
            state.deleteExpectation != null ||
            state.isSaving ||
            restoringDeleteReview
        ) return
        if (record == null) {
            clearDeleteReviewWithConflict()
            return
        }
        restoringDeleteReview = true
        _uiState.value = _uiState.value.copy(isSaving = true)
        runCatching {
            val expectedFingerprint = requireNotNull(savedStateHandle.get<String>(KEY_DELETE_EXPECTATION))
            val configuration = ResolvedWorkConfigurationRevision.resolve(source.history, record.ownerLocalDate)
            repository.captureExpectation(record.id, configuration, record.start, record.end).also { expectation ->
                require(expectation.previous == record)
                require(expectation.availabilityFingerprint() == expectedFingerprint)
            }
        }.onSuccess { expectation ->
            _uiState.value = _uiState.value.copy(deleteExpectation = expectation, isSaving = false)
        }.onFailure {
            clearDeleteReviewWithConflict()
        }
        restoringDeleteReview = false
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun sourceFlow(): Flow<SourceLoad> = configurationRepository.observe().flatMapLatest { history ->
        if (history == null) return@flatMapLatest flowOf(SourceLoad.Error("Todavía no existe una configuración laboral."))
        val today = LocalDate.now(clock.withZone(zoneId))
        val current = history.timeline.revisionAt(today)
            ?: return@flatMapLatest flowOf(SourceLoad.Error("La configuración laboral todavía no está vigente."))
        val timelineId = history.timeline.id
        val sector = current.value.sector
        repository.observeAll(timelineId, sector).flatMapLatest { windows ->
            val work = combine(
                shiftRepository.observeAll(timelineId, sector),
                shiftActualRepository.observeAllActuals(timelineId, sector),
                independentExtraRepository.observeAll(timelineId, sector),
            ) { shifts, actuals, extras -> WorkSources(shifts, actuals, extras) }
            work.flatMapLatest { sources ->
                val relevantDates = buildList {
                    add(today)
                    windows.forEach { add(it.ownerLocalDate) }
                    sources.shifts.forEach { add(it.shift.localStartDate) }
                }
                val startDate = relevantDates.minOrNull() ?: today
                val endDate = relevantDates.maxOrNull() ?: today
                val protections = combine(
                    medicalLeaveRepository.observeIntersecting(startDate, endDate),
                    vacationRepository.observeOverlapping(startDate, endDate),
                ) { medical, vacations ->
                    medical.map { it.startDate..it.endDateInclusive } +
                        vacations.map { it.startDate..it.endDateInclusive }
                }
                combine(protections, minutePulse()) { protectedRanges, currentDate ->
                    val active = resolveAvailabilityActiveWorkIntervals(
                        shifts = sources.shifts,
                        actualsByShiftId = sources.actuals,
                        independentExtras = sources.extras,
                        protectedOwnerDates = protectedRanges,
                    )
                    val protectedIds = windows.filterTo(linkedSetOf()) { window ->
                        protectedRanges.any { window.ownerLocalDate in it }
                    }.mapTo(linkedSetOf(), AvailabilityWindowRecord::id)
                    val results = windows.associate { window ->
                        window.id to calculateAvailabilityBreakdown(
                            window = window,
                            activeWork = active,
                            isProtected = window.id in protectedIds,
                            clock = clock,
                        )
                    }
                    SourceLoad.Content(
                        AvailabilitySource(
                            history = history,
                            windows = windows,
                            breakdowns = results,
                            totals = sumAvailabilityBreakdowns(results.values),
                            protectedWindowIds = protectedIds,
                            activeWork = active,
                            protectedRanges = protectedRanges,
                            today = currentDate,
                        ),
                    )
                }
            }.catch { error ->
                emit(
                    SourceLoad.Content(
                        AvailabilitySource(
                            history = history,
                            windows = windows,
                            breakdowns = emptyMap(),
                            totals = null,
                            protectedWindowIds = emptySet(),
                            activeWork = null,
                            protectedRanges = null,
                            today = LocalDate.now(clock.withZone(zoneId)),
                            calculationError = error.userMessage(),
                        ),
                    ),
                )
            }
        }
    }

    private fun closeEditor(message: String) {
        clearWindowState()
        clearDeleteState()
        _uiState.value = _uiState.value.copy(
            surface = AvailabilitySurface.NONE,
            windowDraft = null,
            windowReview = null,
            deletingRecord = null,
            deleteExpectation = null,
            showDiscardConfirmation = false,
            isSaving = false,
            message = message,
        )
        savedStateHandle[KEY_SURFACE] = AvailabilitySurface.NONE.name
        savedStateHandle.remove<Boolean>(KEY_CONFIRM_DISCARD)
    }

    private fun conflictMessage() {
        val previousSurface = _uiState.value.surface
        val target = when (previousSurface) {
            AvailabilitySurface.CONFIG_REVIEW -> AvailabilitySurface.CONFIG_EDITOR
            AvailabilitySurface.WINDOW_REVIEW -> AvailabilitySurface.WINDOW_EDITOR
            AvailabilitySurface.DELETE_CONFIRMATION -> AvailabilitySurface.NONE
            else -> AvailabilitySurface.WINDOW_EDITOR.takeIf { _uiState.value.windowDraft != null }
                ?: AvailabilitySurface.CONFIG_EDITOR
        }
        when (previousSurface) {
            AvailabilitySurface.CONFIG_REVIEW -> savedStateHandle.remove<String>(KEY_CONFIG_EXPECTATION)
            AvailabilitySurface.WINDOW_REVIEW -> savedStateHandle.remove<String>(KEY_WINDOW_EXPECTATION)
            AvailabilitySurface.DELETE_CONFIRMATION -> clearDeleteState()
            else -> Unit
        }
        _uiState.value = _uiState.value.copy(
            surface = target,
            configurationReview = null,
            windowReview = null,
            deletingRecord = null,
            deleteExpectation = null,
            isSaving = false,
            message = "Los datos cambiaron desde la revisión. Volvé a revisar antes de guardar.",
        )
        savedStateHandle[KEY_SURFACE] = _uiState.value.surface.name
    }

    private fun clearDeleteReviewWithConflict() {
        clearDeleteState()
        _uiState.value = _uiState.value.copy(
            surface = AvailabilitySurface.NONE,
            deletingRecord = null,
            deleteExpectation = null,
            isSaving = false,
            message = "Los datos cambiaron desde la confirmación. Volvé a abrir la disponibilidad antes de eliminar.",
        )
        savedStateHandle[KEY_SURFACE] = AvailabilitySurface.NONE.name
    }

    private fun setSurface(surface: AvailabilitySurface) {
        _uiState.value = _uiState.value.copy(surface = surface, message = null)
        savedStateHandle[KEY_SURFACE] = surface.name
    }

    private fun showError(error: Throwable, saving: Boolean = _uiState.value.isSaving) {
        _uiState.value = _uiState.value.copy(isSaving = saving, message = error.userMessage())
    }

    private fun parseDate(raw: String, label: String): LocalDate = runCatching {
        LocalDate.parse(raw.trim())
    }.getOrElse { error("La $label debe escribirse como AAAA-MM-DD") }

    private fun parseTime(raw: String, label: String): LocalTime = runCatching {
        LocalTime.parse(raw.trim()).also { parsed ->
            require(parsed.second == 0 && parsed.nano == 0) {
                "La hora de $label debe expresarse en minutos enteros"
            }
        }
    }.getOrElse { error("La hora de $label debe escribirse como HH:mm") }

    private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank)
        ?: "No pudimos completar la operación. Los datos no se modificaron."

    private fun minutePulse(): Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now(clock.withZone(zoneId)))
            val intoMinute = Math.floorMod(clock.millis(), MILLIS_PER_MINUTE)
            delay(MILLIS_PER_MINUTE - intoMinute)
        }
    }

    private fun readConfigurationDraft(): AvailabilityConfigurationDraft? {
        val date = savedStateHandle.get<String>(KEY_CONFIG_DATE) ?: return null
        val labelName = savedStateHandle.get<String>(KEY_CONFIG_LABEL)
        return AvailabilityConfigurationDraft(
            label = labelName?.takeUnless { it == NONE_LABEL }
                ?.let { runCatching { AvailabilityLabel.valueOf(it) }.getOrNull() },
            effectiveDate = date,
        )
    }

    private fun readWindowDraft(): AvailabilityWindowDraftState? {
        val id = savedStateHandle.get<String>(KEY_WINDOW_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        return AvailabilityWindowDraftState(
            recordId = id,
            ownerDate = savedStateHandle.get<String>(KEY_WINDOW_OWNER)?.let(LocalDate::parse),
            startTime = savedStateHandle.get<String>(KEY_WINDOW_START).orEmpty(),
            endDate = savedStateHandle.get<String>(KEY_WINDOW_END_DATE).orEmpty(),
            endTime = savedStateHandle.get<String>(KEY_WINDOW_END).orEmpty(),
        )
    }

    private fun clearConfigurationState() {
        savedStateHandle.remove<String>(KEY_CONFIG_LABEL)
        savedStateHandle.remove<String>(KEY_CONFIG_DATE)
        savedStateHandle.remove<String>(KEY_CONFIG_REVISION_ID)
        savedStateHandle.remove<String>(KEY_CONFIG_EXPECTATION)
    }

    private fun clearWindowState() {
        savedStateHandle.remove<String>(KEY_WINDOW_ID)
        savedStateHandle.remove<String>(KEY_WINDOW_OWNER)
        savedStateHandle.remove<String>(KEY_WINDOW_START)
        savedStateHandle.remove<String>(KEY_WINDOW_END_DATE)
        savedStateHandle.remove<String>(KEY_WINDOW_END)
        savedStateHandle.remove<String>(KEY_WINDOW_EXPECTATION)
        savedStateHandle.remove<String>(KEY_WINDOW_ORIGINAL)
    }

    private fun clearDeleteState() {
        savedStateHandle.remove<String>(KEY_DELETING_ID)
        savedStateHandle.remove<String>(KEY_DELETE_EXPECTATION)
    }

    private fun WorkConfigurationHistory.availabilityFingerprint(): String = buildString {
        append(timeline.id)
        timeline.revisions.forEach { revision ->
            append('|').append(revision.id)
            append('|').append(revision.effectiveFrom)
            append('|').append(revision.value.sector)
            append('|').append(revision.value.hoursReference)
            append('|').append(revision.value.availabilityLabel)
            append('|').append(revision.value.hoursReferenceStartedOn)
        }
        perPeriodHoursValues.entries.forEach { entry ->
            append('|').append(entry)
        }
    }

    private fun AvailabilityWindowExpectation.availabilityFingerprint(): String = buildString {
        append(previous)
        append('|').append(configuration.timelineId)
        append('|').append(configuration.referenceDate)
        append('|').append(configuration.revision.id)
        append('|').append(configuration.revision.effectiveFrom)
        append('|').append(configuration.revision.value)
        append('|').append(observedStart)
        append('|').append(observedEnd)
        observedWindows.sortedBy { it.id }.forEach { append('|').append(it) }
        observedActiveSources.sortedBy { it.key }.forEach { append('|').append(it) }
        append('|').append(protectionFingerprint)
    }

    private fun AvailabilityWindowRecord.editFingerprint(): String = listOf(
        id,
        timelineId,
        sector,
        configurationRevisionId,
        ownerLocalDate,
        zoneId.id,
        start,
        end,
        labelSnapshot,
        createdAt,
        updatedAt,
    ).joinToString("|")

    private data class WorkSources(
        val shifts: List<V2ShiftWrite>,
        val actuals: Map<UUID, ShiftActualAggregate>,
        val extras: List<com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord>,
    )

    private sealed interface SourceLoad {
        data class Content(val source: AvailabilitySource) : SourceLoad
        data class Error(val message: String) : SourceLoad
    }

    class Factory(
        private val configurationRepository: WorkConfigurationRepository,
        private val repository: AvailabilityWindowRepository,
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
            require(modelClass.isAssignableFrom(AvailabilityViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return AvailabilityViewModel(
                configurationRepository,
                repository,
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

    private companion object {
        const val KEY_SURFACE = "availability.surface"
        const val KEY_CONFIG_LABEL = "availability.config.label"
        const val KEY_CONFIG_DATE = "availability.config.date"
        const val KEY_CONFIG_REVISION_ID = "availability.config.revisionId"
        const val KEY_CONFIG_EXPECTATION = "availability.config.expectation"
        const val KEY_WINDOW_ID = "availability.window.id"
        const val KEY_WINDOW_OWNER = "availability.window.owner"
        const val KEY_WINDOW_START = "availability.window.start"
        const val KEY_WINDOW_END_DATE = "availability.window.endDate"
        const val KEY_WINDOW_END = "availability.window.end"
        const val KEY_WINDOW_EXPECTATION = "availability.window.expectation"
        const val KEY_WINDOW_ORIGINAL = "availability.window.original"
        const val KEY_DELETING_ID = "availability.deleting"
        const val KEY_DELETE_EXPECTATION = "availability.delete.expectation"
        const val KEY_CONFIRM_DISCARD = "availability.confirmDiscard"
        const val NONE_LABEL = "NONE"
        const val NEW_WINDOW = "NEW"
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
