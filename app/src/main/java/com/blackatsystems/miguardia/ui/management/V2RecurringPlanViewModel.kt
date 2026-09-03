package com.blackatsystems.miguardia.ui.management

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState
import com.blackatsystems.miguardia.core.domain.model.RecurringPattern
import com.blackatsystems.miguardia.core.domain.model.RecurringPlan
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanExpectation
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevision
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.model.RecurringProtectionExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.RecurringPlanRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.V2RecurringShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.shift.RecurringConflictPolicy
import com.blackatsystems.miguardia.core.domain.shift.RecurringDateAction
import com.blackatsystems.miguardia.core.domain.shift.RecurringMutationPreview
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.shift.editV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.shift.expandRecurringDates
import com.blackatsystems.miguardia.core.domain.shift.planNewRecurringPlan
import com.blackatsystems.miguardia.core.domain.shift.planRecurringFinalization
import com.blackatsystems.miguardia.core.domain.shift.planRecurringRevision
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.normalizeOptionalWorkText
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

enum class V2RecurringStage {
    IDLE,
    FORM,
    PREVIEW,
    PLANS,
    PLAN_DETAIL,
    CONFIRM_DISCARD,
}

enum class V2RecurringMode {
    CREATE,
    CHANGE,
    FINALIZE,
}

enum class V2RecurringPatternKind {
    WEEKDAYS,
    EVERY_N_DAYS,
    EVERY_N_WEEKS,
    MONTHLY,
}

data class V2RecurringTemplateOption(
    val objective: Objective,
    val workPlace: WorkPlace,
    val workType: WorkType,
    val template: WorkTemplate,
)

data class V2RecurringUiState(
    val stage: V2RecurringStage = V2RecurringStage.IDLE,
    val mode: V2RecurringMode = V2RecurringMode.CREATE,
    val timelineId: UUID? = null,
    val referenceDate: LocalDate? = null,
    val plans: List<RecurringPlanAggregate> = emptyList(),
    val plansReadSuccessfully: Boolean = false,
    val selectedPlanId: UUID? = null,
    val selectedPlan: RecurringPlanAggregate? = null,
    val draftPlanId: UUID? = null,
    val cutDate: LocalDate? = null,
    val templateOptions: List<V2RecurringTemplateOption> = emptyList(),
    val selectedTemplateId: UUID? = null,
    val position: String = "",
    val patternKind: V2RecurringPatternKind = V2RecurringPatternKind.WEEKDAYS,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val intervalText: String = "1",
    val monthlyOrdinal: MonthlyOrdinal = MonthlyOrdinal.FIRST,
    val monthlyDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val startDateText: String = "",
    val endDateText: String = "",
    val conflictPolicy: RecurringConflictPolicy = RecurringConflictPolicy.KEEP_EXISTING,
    val preview: RecurringMutationPreview? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val canRetry: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val successSequence: Int = 0,
) {
    val isBlocking: Boolean
        get() = stage != V2RecurringStage.IDLE

    val selectedOption: V2RecurringTemplateOption?
        get() = templateOptions.firstOrNull { it.template.id == selectedTemplateId }

    val hasDraft: Boolean
        get() = stage in setOf(V2RecurringStage.FORM, V2RecurringStage.PREVIEW) &&
            mode != V2RecurringMode.FINALIZE
}

internal data class V2RecurringPersistedState(
    val stage: V2RecurringStage = V2RecurringStage.IDLE,
    val mode: V2RecurringMode = V2RecurringMode.CREATE,
    val timelineId: UUID? = null,
    val selectedPlanId: UUID? = null,
    val draftPlanId: UUID? = null,
    val cutDate: LocalDate? = null,
    val selectedTemplateId: UUID? = null,
    val position: String = "",
    val patternKind: V2RecurringPatternKind = V2RecurringPatternKind.WEEKDAYS,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val intervalText: String = "1",
    val monthlyOrdinal: MonthlyOrdinal = MonthlyOrdinal.FIRST,
    val monthlyDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val startDateText: String = "",
    val endDateText: String = "",
    val conflictPolicy: RecurringConflictPolicy = RecurringConflictPolicy.KEEP_EXISTING,
)

class V2RecurringPlanViewModel(
    configurationRepository: WorkConfigurationRepository,
    catalogRepository: WorkCatalogRepository,
    objectiveRepository: ObjectiveRepository,
    shiftRepository: ShiftRepository,
    medicalLeaveRepository: MedicalLeaveRepository,
    recurringPlanRepository: RecurringPlanRepository,
    recurringShiftRepository: V2RecurringShiftRepository,
    clock: Clock,
    zoneId: ZoneId,
    uuidProvider: UuidProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val coordinator = V2RecurringPlanCoordinator(
        configurationRepository,
        catalogRepository,
        objectiveRepository,
        shiftRepository,
        medicalLeaveRepository,
        recurringPlanRepository,
        recurringShiftRepository,
        clock,
        zoneId,
        uuidProvider,
        viewModelScope,
        savedStateHandle.readRecurringState(),
        savedStateHandle::writeRecurringState,
    )

    val uiState: StateFlow<V2RecurringUiState> = coordinator.uiState

    fun resume(rootState: WorkSetupState) = coordinator.resume(rootState)
    fun openCreate(rootState: WorkSetupState) = coordinator.openCreate(rootState)
    fun openPlans(rootState: WorkSetupState) = coordinator.openPlans(rootState)
    fun openPlan(planId: UUID) = coordinator.openPlan(planId)
    fun changeFrom(planId: UUID, cutDate: LocalDate) = coordinator.changeFrom(planId, cutDate)
    fun finalizeFrom(planId: UUID, cutDate: LocalDate) = coordinator.finalizeFrom(planId, cutDate)
    fun selectTemplate(id: UUID) = coordinator.selectTemplate(id)
    fun updatePosition(value: String) = coordinator.updatePosition(value)
    fun selectPattern(kind: V2RecurringPatternKind) = coordinator.selectPattern(kind)
    fun toggleWeekday(day: DayOfWeek) = coordinator.toggleWeekday(day)
    fun updateInterval(value: String) = coordinator.updateInterval(value)
    fun selectMonthlyOrdinal(value: MonthlyOrdinal) = coordinator.selectMonthlyOrdinal(value)
    fun selectMonthlyDay(value: DayOfWeek) = coordinator.selectMonthlyDay(value)
    fun updateStartDate(value: String) = coordinator.updateStartDate(value)
    fun updateEndDate(value: String) = coordinator.updateEndDate(value)
    fun selectConflictPolicy(value: RecurringConflictPolicy) = coordinator.selectConflictPolicy(value)
    fun review() = coordinator.review()
    fun save() = coordinator.save()
    fun retry() = coordinator.retry()
    fun back() = coordinator.back()
    fun confirmDiscard() = coordinator.confirmDiscard()
    fun cancelDiscard() = coordinator.cancelDiscard()
    fun close() = coordinator.close()
    fun clearMessage() = coordinator.clearMessage()
    fun consumeSuccess(sequence: Int) = coordinator.consumeSuccess(sequence)

    class Factory(
        private val configurationRepository: WorkConfigurationRepository,
        private val catalogRepository: WorkCatalogRepository,
        private val objectiveRepository: ObjectiveRepository,
        private val shiftRepository: ShiftRepository,
        private val medicalLeaveRepository: MedicalLeaveRepository,
        private val recurringPlanRepository: RecurringPlanRepository,
        private val recurringShiftRepository: V2RecurringShiftRepository,
        private val clock: Clock = Clock.system(AppDefaults.zoneId()),
        private val zoneId: ZoneId = AppDefaults.zoneId(),
        private val uuidProvider: UuidProvider = UuidProvider(UUID::randomUUID),
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(V2RecurringPlanViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return V2RecurringPlanViewModel(
                configurationRepository,
                catalogRepository,
                objectiveRepository,
                shiftRepository,
                medicalLeaveRepository,
                recurringPlanRepository,
                recurringShiftRepository,
                clock,
                zoneId,
                uuidProvider,
                extras.createSavedStateHandle(),
            ) as T
        }
    }
}

internal class V2RecurringPlanCoordinator(
    private val configurationRepository: WorkConfigurationRepository,
    private val catalogRepository: WorkCatalogRepository,
    private val objectiveRepository: ObjectiveRepository,
    private val shiftRepository: ShiftRepository,
    private val medicalLeaveRepository: MedicalLeaveRepository,
    private val recurringPlanRepository: RecurringPlanRepository,
    private val recurringShiftRepository: V2RecurringShiftRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val uuidProvider: UuidProvider,
    private val scope: kotlinx.coroutines.CoroutineScope,
    initialState: V2RecurringPersistedState = V2RecurringPersistedState(),
    private val persist: (V2RecurringPersistedState) -> Unit = {},
) {
    private val _uiState = MutableStateFlow(initialState.toUiState())
    val uiState: StateFlow<V2RecurringUiState> = _uiState
    private val writeMutex = Mutex()
    private var readyState: WorkSetupState.V2Ready? = null
    private var restorationPending = initialState.stage != V2RecurringStage.IDLE
    private var prepared: PreparedRecurringMutation? = null
    private var plansJob: Job? = null
    private var retryAction = V2RecurringRetryAction.NONE

    fun resume(rootState: WorkSetupState) {
        val ready = rootState as? WorkSetupState.V2Ready
        if (ready == null || (_uiState.value.timelineId != null && _uiState.value.timelineId != ready.timelineId)) {
            readyState = null
            if (_uiState.value.isBlocking && !_uiState.value.isSaving) close()
            return
        }
        readyState = ready
        _uiState.update { it.copy(referenceDate = LocalDate.now(clock.withZone(zoneId))) }
        if (restorationPending) {
            restorationPending = false
            when (_uiState.value.stage) {
                V2RecurringStage.PLANS,
                V2RecurringStage.PLAN_DETAIL,
                -> openPlans(ready, restoreSelected = true)

                V2RecurringStage.FORM,
                V2RecurringStage.PREVIEW,
                V2RecurringStage.CONFIRM_DISCARD,
                -> restoreEditor(ready)

                V2RecurringStage.IDLE -> Unit
            }
        }
    }

    fun openCreate(rootState: WorkSetupState) {
        val ready = rootState as? WorkSetupState.V2Ready ?: return
        readyState = ready
        plansJob?.cancel()
        retryAction = V2RecurringRetryAction.NONE
        val today = LocalDate.now(clock.withZone(zoneId))
        _uiState.value = V2RecurringUiState(
            stage = V2RecurringStage.FORM,
            mode = V2RecurringMode.CREATE,
            timelineId = ready.timelineId,
            referenceDate = today,
            draftPlanId = uuidProvider.newUuid(),
            weekdays = setOf(today.dayOfWeek),
            monthlyDayOfWeek = today.dayOfWeek,
            startDateText = formatRecurringDate(today),
            endDateText = formatRecurringDate(today.plusMonths(1)),
            isLoading = true,
        )
        persistCurrent()
        loadEditorSources()
    }

    fun openPlans(rootState: WorkSetupState) {
        val ready = rootState as? WorkSetupState.V2Ready ?: return
        readyState = ready
        openPlans(ready, restoreSelected = false)
    }

    private fun openPlans(ready: WorkSetupState.V2Ready, restoreSelected: Boolean) {
        prepared = null
        retryAction = V2RecurringRetryAction.NONE
        val selectedId = if (restoreSelected) _uiState.value.selectedPlanId else null
        _uiState.update {
            it.copy(
                stage = if (restoreSelected && selectedId != null) V2RecurringStage.PLAN_DETAIL else V2RecurringStage.PLANS,
                timelineId = ready.timelineId,
                referenceDate = LocalDate.now(clock.withZone(zoneId)),
                plansReadSuccessfully = false,
                isLoading = true,
                canRetry = false,
                errorMessage = null,
            )
        }
        persistCurrent()
        plansJob?.cancel()
        plansJob = scope.launch {
            try {
                recurringPlanRepository.observePlans(
                    ready.timelineId,
                    ready.configurationRevision.value.sector,
                ).collect { plans ->
                    val requestedId = selectedId ?: _uiState.value.selectedPlan?.plan?.id
                    _uiState.update { state ->
                        state.copy(
                            plans = plans,
                            plansReadSuccessfully = true,
                            selectedPlan = requestedId?.let { id -> plans.firstOrNull { it.plan.id == id } },
                            selectedPlanId = requestedId?.takeIf { id -> plans.any { it.plan.id == id } },
                            stage = if (state.stage == V2RecurringStage.PLAN_DETAIL &&
                                plans.any { it.plan.id == requestedId }
                            ) {
                                V2RecurringStage.PLAN_DETAIL
                            } else {
                                V2RecurringStage.PLANS
                            },
                            isLoading = false,
                            canRetry = false,
                            errorMessage = null,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                retryAction = V2RecurringRetryAction.LOAD_PLANS
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        canRetry = true,
                        errorMessage = error.message ?: "No pudimos leer los planes recurrentes.",
                    )
                }
            }
        }
    }

    fun openPlan(planId: UUID) {
        val plan = _uiState.value.plans.firstOrNull { it.plan.id == planId } ?: return
        _uiState.update {
            it.copy(
                stage = V2RecurringStage.PLAN_DETAIL,
                selectedPlanId = planId,
                selectedPlan = plan,
            )
        }
        persistCurrent()
    }

    fun changeFrom(planId: UUID, cutDate: LocalDate) = openExistingEditor(
        planId,
        cutDate,
        V2RecurringMode.CHANGE,
    )

    fun finalizeFrom(planId: UUID, cutDate: LocalDate) = openExistingEditor(
        planId,
        cutDate,
        V2RecurringMode.FINALIZE,
    )

    private fun openExistingEditor(
        planId: UUID,
        cutDate: LocalDate,
        mode: V2RecurringMode,
    ) {
        val ready = readyState ?: return
        val today = LocalDate.now(clock.withZone(zoneId))
        if (cutDate.isBefore(today)) {
            _uiState.update { it.copy(errorMessage = "El pasado sólo puede corregirse jornada por jornada.") }
            return
        }
        plansJob?.cancel()
        prepared = null
        retryAction = V2RecurringRetryAction.NONE
        _uiState.value = V2RecurringUiState(
            stage = V2RecurringStage.FORM,
            mode = mode,
            timelineId = ready.timelineId,
            referenceDate = today,
            draftPlanId = planId,
            selectedPlanId = planId,
            cutDate = cutDate,
            startDateText = formatRecurringDate(cutDate),
            endDateText = formatRecurringDate(cutDate),
            isLoading = true,
            canRetry = false,
        )
        persistCurrent()
        scope.launch {
            try {
                val plan = recurringPlanRepository.getPlan(planId)
                    ?: error("El plan ya no existe. Volvé a revisar la lista.")
                if (plan.plan.timelineId != ready.timelineId) error("El plan pertenece a otra forma de trabajar.")
                if (plan.latestRevision.kind == RecurringPlanRevisionKind.FINALIZED) {
                    error("Este plan ya está finalizado.")
                }
                val options = loadTemplateOptions(ready)
                val latest = plan.latestRevision
                val patternState = latest.pattern.toEditorState()
                _uiState.update {
                    it.copy(
                        selectedPlan = plan,
                        templateOptions = options,
                        selectedTemplateId = latest.templateId,
                        position = latest.positionSnapshot.orEmpty(),
                        patternKind = patternState.kind,
                        weekdays = patternState.weekdays,
                        intervalText = patternState.interval.toString(),
                        monthlyOrdinal = patternState.ordinal,
                        monthlyDayOfWeek = patternState.dayOfWeek,
                        endDateText = formatRecurringDate(maxOf(latest.endDateInclusive, cutDate)),
                        isLoading = false,
                        canRetry = false,
                        errorMessage = if (
                            mode == V2RecurringMode.CHANGE && options.none { option ->
                                option.template.id == latest.templateId
                            }
                        ) {
                            "La plantilla histórica ya no está activa. Elegí una plantilla activa para la nueva versión."
                        } else {
                            null
                        },
                    )
                }
                persistCurrent()
                if (mode == V2RecurringMode.FINALIZE) review()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                retryAction = V2RecurringRetryAction.LOAD_EXISTING_EDITOR
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        canRetry = true,
                        errorMessage = error.message ?: "No pudimos abrir el plan. Reintentá.",
                    )
                }
            }
        }
    }

    fun selectTemplate(id: UUID) = editDraft { state ->
        if (state.templateOptions.none { it.template.id == id }) state else state.copy(selectedTemplateId = id)
    }

    fun updatePosition(value: String) = editDraft { it.copy(position = value.take(MAX_POSITION_LENGTH)) }

    fun selectPattern(kind: V2RecurringPatternKind) = editDraft { it.copy(patternKind = kind) }

    fun toggleWeekday(day: DayOfWeek) = editDraft { state ->
        val updated = state.weekdays.toMutableSet().apply {
            if (!add(day)) remove(day)
        }
        state.copy(weekdays = updated)
    }

    fun updateInterval(value: String) = editDraft {
        it.copy(intervalText = value.filter(Char::isDigit))
    }

    fun selectMonthlyOrdinal(value: MonthlyOrdinal) = editDraft { it.copy(monthlyOrdinal = value) }

    fun selectMonthlyDay(value: DayOfWeek) = editDraft { it.copy(monthlyDayOfWeek = value) }

    fun updateStartDate(value: String) = editDraft { state ->
        if (state.cutDate != null) {
            state
        } else {
            normalizeRecurringDateDraftUpdate(value)
                ?.let { formatted -> state.copy(startDateText = formatted) }
                ?: state
        }
    }

    fun updateEndDate(value: String) = editDraft { state ->
        normalizeRecurringDateDraftUpdate(value)
            ?.let { formatted -> state.copy(endDateText = formatted) }
            ?: state
    }

    fun selectConflictPolicy(value: RecurringConflictPolicy) = editDraft {
        it.copy(conflictPolicy = value)
    }

    private fun editDraft(transform: (V2RecurringUiState) -> V2RecurringUiState) {
        val state = _uiState.value
        if (state.stage !in setOf(V2RecurringStage.FORM, V2RecurringStage.PREVIEW) ||
            state.isLoading || state.isSaving || state.mode == V2RecurringMode.FINALIZE
        ) {
            return
        }
        prepared = null
        _uiState.value = transform(state).copy(
            stage = V2RecurringStage.FORM,
            preview = null,
            canRetry = false,
            errorMessage = null,
        )
        retryAction = V2RecurringRetryAction.NONE
        persistCurrent()
    }

    fun review() {
        val state = _uiState.value
        if (state.stage !in setOf(V2RecurringStage.FORM, V2RecurringStage.PREVIEW) ||
            state.isLoading || state.isSaving
        ) {
            return
        }
        prepared = null
        retryAction = V2RecurringRetryAction.NONE
        _uiState.update { it.copy(isLoading = true, canRetry = false, errorMessage = null) }
        scope.launch {
            try {
                val built = if (state.mode == V2RecurringMode.FINALIZE) {
                    prepareFinalization(state)
                } else {
                    prepareActiveRevision(state)
                }
                prepared = built
                _uiState.update {
                    it.copy(
                        stage = V2RecurringStage.PREVIEW,
                        preview = built.preview,
                        selectedPlan = built.primaryPlan,
                        isLoading = false,
                        canRetry = false,
                        errorMessage = when {
                            built.preview.canConfirm -> null
                            built.preview.results.any { result ->
                                result.action == RecurringDateAction.BLOCKED_BY_CANCEL
                            } -> "Cancelar detuvo el lote porque hay fechas ocupadas. Elegí otra política para continuar."
                            else -> "La política elegida no produciría ninguna jornada concreta. Elegí otro resultado."
                        },
                    )
                }
                persistCurrent()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                retryAction = V2RecurringRetryAction.REVIEW
                _uiState.update {
                    it.copy(
                        stage = V2RecurringStage.FORM,
                        preview = null,
                        isLoading = false,
                        canRetry = true,
                        errorMessage = error.message ?: "No pudimos preparar la vista previa.",
                    )
                }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val currentPrepared = prepared
        if (
            state.stage != V2RecurringStage.PREVIEW ||
            state.isLoading ||
            state.isSaving ||
            currentPrepared == null ||
            !currentPrepared.preview.canConfirm
        ) {
            return
        }
        val effectiveStart = state.cutDate ?: parseDate(state.startDateText, "inicio")
        if (effectiveStart.isBefore(LocalDate.now(clock.withZone(zoneId)))) {
            prepared = null
            retryAction = V2RecurringRetryAction.REVIEW
            _uiState.update {
                it.copy(
                    stage = V2RecurringStage.FORM,
                    preview = null,
                    canRetry = true,
                    errorMessage = "La fecha inicial quedó en el pasado. Revisá nuevamente el plan.",
                )
            }
            persistCurrent()
            return
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (!writeMutex.tryLock()) return@launch
            retryAction = V2RecurringRetryAction.NONE
            _uiState.update { it.copy(isSaving = true, canRetry = false, errorMessage = null) }
            try {
                recurringShiftRepository.applyRecurringPlanMutation(
                    mutation = requireNotNull(currentPrepared.preview.mutation),
                    expectedPlan = currentPrepared.expectedPlans,
                    expectedOccupancy = currentPrepared.expectedOccupancy,
                    expectedPairs = currentPrepared.expectedPairs,
                    expectedProtection = currentPrepared.expectedProtection,
                )
                val sequence = _uiState.value.successSequence + 1
                prepared = null
                plansJob?.cancel()
                _uiState.value = V2RecurringUiState(
                    infoMessage = when (state.mode) {
                        V2RecurringMode.CREATE -> "Plan recurrente creado."
                        V2RecurringMode.CHANGE ->
                            "Plan actualizado desde ${state.cutDate?.let(::formatRecurringDate).orEmpty()}."
                        V2RecurringMode.FINALIZE ->
                            "Plan finalizado desde ${state.cutDate?.let(::formatRecurringDate).orEmpty()}."
                    },
                    successSequence = sequence,
                )
                persist(V2RecurringPersistedState())
            } catch (error: CancellationException) {
                throw error
            } catch (error: ConflictingLocalWriteException) {
                prepared = null
                retryAction = V2RecurringRetryAction.REVIEW
                _uiState.update {
                    it.copy(
                        stage = V2RecurringStage.FORM,
                        preview = null,
                        isSaving = false,
                        canRetry = true,
                        errorMessage = error.message ?: "Los datos cambiaron. Revisá de nuevo.",
                    )
                }
                persistCurrent()
            } catch (error: Exception) {
                retryAction = V2RecurringRetryAction.SAVE
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        canRetry = true,
                        errorMessage = error.message ?: "No pudimos guardar. El borrador sigue disponible.",
                    )
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
                writeMutex.unlock()
            }
        }
    }

    fun retry() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || !state.canRetry) return
        val action = retryAction
        retryAction = V2RecurringRetryAction.NONE
        _uiState.update { it.copy(canRetry = false, errorMessage = null) }
        when (action) {
            V2RecurringRetryAction.NONE -> Unit
            V2RecurringRetryAction.LOAD_PLANS -> readyState?.let { ready ->
                openPlans(ready, restoreSelected = state.selectedPlanId != null)
            }
            V2RecurringRetryAction.LOAD_CREATE_SOURCES -> {
                _uiState.update { it.copy(isLoading = true) }
                loadEditorSources()
            }
            V2RecurringRetryAction.LOAD_EXISTING_EDITOR -> {
                val planId = state.draftPlanId ?: return
                val cutDate = state.cutDate ?: return
                openExistingEditor(planId, cutDate, state.mode)
            }
            V2RecurringRetryAction.REVIEW -> review()
            V2RecurringRetryAction.SAVE -> save()
        }
    }

    fun back() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        when (state.stage) {
            V2RecurringStage.IDLE -> Unit
            V2RecurringStage.PLANS -> close()
            V2RecurringStage.PLAN_DETAIL -> {
                _uiState.update {
                    it.copy(
                        stage = V2RecurringStage.PLANS,
                        selectedPlanId = null,
                        selectedPlan = null,
                    )
                }
                persistCurrent()
            }
            V2RecurringStage.FORM,
            V2RecurringStage.PREVIEW,
            -> if (state.hasDraft) {
                _uiState.update { it.copy(stage = V2RecurringStage.CONFIRM_DISCARD) }
                persistCurrent()
            } else {
                close()
            }
            V2RecurringStage.CONFIRM_DISCARD -> cancelDiscard()
        }
    }

    fun confirmDiscard() = close()

    fun cancelDiscard() {
        if (_uiState.value.stage != V2RecurringStage.CONFIRM_DISCARD) return
        _uiState.update { it.copy(stage = V2RecurringStage.FORM) }
        persistCurrent()
    }

    fun close() {
        if (_uiState.value.isSaving) return
        prepared = null
        retryAction = V2RecurringRetryAction.NONE
        plansJob?.cancel()
        val message = _uiState.value.infoMessage
        val sequence = _uiState.value.successSequence
        _uiState.value = V2RecurringUiState(infoMessage = message, successSequence = sequence)
        persist(V2RecurringPersistedState())
    }

    fun clearMessage() {
        retryAction = V2RecurringRetryAction.NONE
        _uiState.update { it.copy(canRetry = false, errorMessage = null, infoMessage = null) }
    }

    fun consumeSuccess(sequence: Int) = _uiState.update {
        if (it.successSequence == sequence) it.copy(successSequence = 0) else it
    }

    private fun restoreEditor(ready: WorkSetupState.V2Ready) {
        val shouldRestorePreview = _uiState.value.stage == V2RecurringStage.PREVIEW
        val shouldRestoreDiscard = _uiState.value.stage == V2RecurringStage.CONFIRM_DISCARD
        if (_uiState.value.mode == V2RecurringMode.CREATE) {
            _uiState.update { it.copy(stage = V2RecurringStage.FORM, isLoading = true, preview = null) }
            loadEditorSources()
            if (shouldRestorePreview || shouldRestoreDiscard) {
                scope.launch {
                    while (_uiState.value.isLoading) kotlinx.coroutines.yield()
                    if (_uiState.value.stage == V2RecurringStage.FORM && _uiState.value.errorMessage == null) {
                        if (shouldRestorePreview) review() else {
                            _uiState.update { it.copy(stage = V2RecurringStage.CONFIRM_DISCARD) }
                            persistCurrent()
                        }
                    }
                }
            }
        } else {
            val planId = _uiState.value.draftPlanId ?: return close()
            val persisted = _uiState.value
            openExistingEditor(planId, requireNotNull(persisted.cutDate), persisted.mode)
            scope.launch {
                while (_uiState.value.isLoading) kotlinx.coroutines.yield()
                if (_uiState.value.stage == V2RecurringStage.FORM && persisted.mode != V2RecurringMode.FINALIZE) {
                    _uiState.update {
                        it.copy(
                            selectedTemplateId = persisted.selectedTemplateId,
                            position = persisted.position,
                            patternKind = persisted.patternKind,
                            weekdays = persisted.weekdays,
                            intervalText = persisted.intervalText,
                            monthlyOrdinal = persisted.monthlyOrdinal,
                            monthlyDayOfWeek = persisted.monthlyDayOfWeek,
                            endDateText = persisted.endDateText,
                        )
                    }
                    persistCurrent()
                    if (_uiState.value.errorMessage == null) {
                        if (shouldRestorePreview) review() else if (shouldRestoreDiscard) {
                            _uiState.update { it.copy(stage = V2RecurringStage.CONFIRM_DISCARD) }
                            persistCurrent()
                        }
                    }
                }
            }
        }
    }

    private fun loadEditorSources() {
        val ready = readyState ?: return
        scope.launch {
            try {
                val options = loadTemplateOptions(ready)
                _uiState.update { state ->
                    state.copy(
                        templateOptions = options,
                        selectedTemplateId = state.selectedTemplateId
                            ?.takeIf { id -> options.any { it.template.id == id } }
                            ?: options.firstOrNull()?.template?.id,
                        isLoading = false,
                        canRetry = false,
                        errorMessage = if (options.isEmpty()) {
                            "No hay una plantilla activa disponible para repetir jornadas."
                        } else {
                            null
                        },
                    )
                }
                persistCurrent()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                retryAction = V2RecurringRetryAction.LOAD_CREATE_SOURCES
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        canRetry = true,
                        errorMessage = error.message ?: "No pudimos leer las plantillas. Reintentá.",
                    )
                }
            }
        }
    }

    private suspend fun loadTemplateOptions(
        ready: WorkSetupState.V2Ready,
    ): List<V2RecurringTemplateOption> {
        val catalog = catalogRepository.observeCatalog(
            ready.timelineId,
            ready.configurationRevision.value.sector,
        ).first()
        val places = catalog.workPlaces.filter(WorkPlace::isActive).associateBy { it.id }
        val types = catalog.workTypes.filter(WorkType::isActive).associateBy { it.id }
        return catalog.workTemplates
            .filter(WorkTemplate::isActive)
            .mapNotNull { template ->
                val place = places[template.workPlaceId] ?: return@mapNotNull null
                val type = types[template.workTypeId] ?: return@mapNotNull null
                val objective = objectiveRepository.getById(template.objectiveId) ?: return@mapNotNull null
                V2RecurringTemplateOption(objective, place, type, template)
            }
            .sortedWith(
                compareBy<V2RecurringTemplateOption> { it.objective.fullName }
                    .thenBy { it.workType.name }
                    .thenBy { it.template.startTime },
            )
    }

    private suspend fun prepareActiveRevision(state: V2RecurringUiState): PreparedRecurringMutation {
        val ready = readyState ?: error("La forma de trabajar ya no está lista.")
        val history = configurationRepository.get() ?: error("Ya no existe la configuración laboral.")
        if (history.timeline.id != ready.timelineId) error("La forma de trabajar cambió. Volvé a revisar.")
        val option = state.selectedOption ?: error("Elegí una plantilla activa.")
        val pattern = state.toPattern()
        val start = state.cutDate ?: parseDate(state.startDateText, "inicio")
        val end = parseDate(state.endDateText, "final")
        val dates = expandRecurringDates(pattern, start, end, clock, zoneId)
        val current = if (state.mode == V2RecurringMode.CHANGE) {
            recurringPlanRepository.getPlan(requireNotNull(state.draftPlanId))
                ?: error("El plan ya no existe. Volvé a revisar la lista.")
        } else {
            null
        }
        val affectedEnd = maxOf(
            end,
            current?.occurrences?.maxOfOrNull { it.localDate } ?: end,
        )
        val windowStart = safeMinusDays(start, REVIEW_NEIGHBOR_DAYS)
        val windowEnd = safePlusDays(affectedEnd, REVIEW_NEIGHBOR_DAYS)
        val existing = shiftRepository.observeStartingBetween(windowStart, windowEnd).first()
        val linked = existing.mapNotNull { shift ->
            recurringPlanRepository.getOccurrenceForShift(shift.id)
        }
        val timestamp = nextPlanMutationInstant(
            candidate = java.time.Instant.ofEpochMilli(clock.millis()),
            current = current,
            additionalInstants = linked.map { it.updatedAt } + existing.map { it.updatedAt },
        )
        val revision = buildRevision(state, option, pattern, start, end, timestamp, current)
        val candidates = buildCandidates(dates, option, state.position, history, current, timestamp)
        val protection = recurringPlanRepository.captureProtection(
            linked.mapNotNullTo(linkedSetOf()) { it.shiftId },
            windowStart,
            windowEnd,
        )
        val medicalDates = medicalLeaveRepository.observeIntersecting(start, affectedEnd).first()
            .flatMap { leave ->
                val from = maxOf(start, leave.startDate)
                val to = minOf(affectedEnd, leave.endDateInclusive)
                generateSequence(from) { date -> if (date < to) date.plusDays(1) else null }.toList()
            }
            .toSet()
        val preview = if (current == null) {
            planNewRecurringPlan(
                plan = RecurringPlan(
                    id = requireNotNull(state.draftPlanId),
                    timelineId = ready.timelineId,
                    sector = ready.configurationRevision.value.sector,
                    createdAt = timestamp,
                ),
                revision = revision,
                dates = dates,
                candidates = candidates,
                existingShifts = existing,
                linkedOccurrences = linked,
                protection = protection,
                conflictPolicy = state.conflictPolicy,
                medicalLeaveDates = medicalDates,
            )
        } else {
            planRecurringRevision(
                current = current,
                revision = revision,
                dates = dates,
                candidates = candidates,
                existingShifts = existing,
                linkedOccurrences = linked,
                protection = protection,
                conflictPolicy = state.conflictPolicy,
                medicalLeaveDates = medicalDates,
            )
        }
        return buildPrepared(preview, current, existing, windowStart, windowEnd, protection)
    }

    private suspend fun prepareFinalization(state: V2RecurringUiState): PreparedRecurringMutation {
        val plan = recurringPlanRepository.getPlan(requireNotNull(state.draftPlanId))
            ?: error("El plan ya no existe. Volvé a revisar la lista.")
        val latest = plan.latestRevision
        val cut = requireNotNull(state.cutDate)
        if (cut.isBefore(LocalDate.now(clock.withZone(zoneId)))) {
            error("El pasado sólo puede corregirse jornada por jornada.")
        }
        val end = maxOf(
            maxOf(latest.endDateInclusive, cut),
            plan.occurrences.maxOfOrNull { it.localDate } ?: cut,
        )
        val timestamp = nextPlanMutationInstant(java.time.Instant.ofEpochMilli(clock.millis()), plan)
        val finalRevision = latest.copy(
            id = uuidProvider.newUuid(),
            revisionNumber = latest.revisionNumber + 1,
            effectiveFrom = cut,
            kind = RecurringPlanRevisionKind.FINALIZED,
            endDateInclusive = maxOf(latest.endDateInclusive, cut),
            createdAt = timestamp,
        )
        val windowStart = cut
        val windowEnd = safePlusDays(end, REVIEW_NEIGHBOR_DAYS)
        val existing = shiftRepository.observeStartingBetween(windowStart, windowEnd).first()
        val relevantIds = plan.occurrences
            .filter { it.localDate in windowStart..windowEnd }
            .mapNotNullTo(linkedSetOf()) { it.shiftId }
        val protection = recurringPlanRepository.captureProtection(relevantIds, windowStart, windowEnd)
        val preview = planRecurringFinalization(plan, finalRevision, existing, protection)
        return buildPrepared(preview, plan, existing, windowStart, windowEnd, protection)
    }

    private suspend fun buildPrepared(
        preview: RecurringMutationPreview,
        current: RecurringPlanAggregate?,
        existing: List<com.blackatsystems.miguardia.core.domain.model.Shift>,
        windowStart: LocalDate,
        windowEnd: LocalDate,
        protection: RecurringProtectionExpectation,
    ): PreparedRecurringMutation {
        val mutation = preview.mutation
        val targetPlanId = mutation?.revisionToInsert?.planId ?: requireNotNull(_uiState.value.draftPlanId)
        val relatedIds = buildSet {
            add(targetPlanId)
            mutation?.occurrencesToUpdate?.forEach { add(it.planId) }
        }
        val expectedById = relatedIds.associateWith { id ->
            if (id == targetPlanId) current else recurringPlanRepository.getPlan(id)
                ?: error("Otro plan alcanzado ya no existe.")
        }
        val comparedIds = mutation?.shiftMutation?.let { it.shiftIdsToDelete + it.shiftsToUpdate.map { write -> write.shift.id } }
            .orEmpty()
        val pairs = comparedIds.map { id ->
            when (val lookup = recurringShiftRepository.getShift(id)) {
                V2ShiftLookup.Missing -> error("Una jornada alcanzada ya no existe. Revisá de nuevo.")
                is V2ShiftLookup.V2 -> lookup.write
            }
        }
        return PreparedRecurringMutation(
            preview = preview,
            primaryPlan = current,
            expectedPlans = RecurringPlanExpectation.capture(expectedById),
            expectedOccupancy = ShiftOccupancyExpectation.capture(
                windowStart,
                windowEnd,
                existing.filter { it.localStartDate in windowStart..windowEnd },
            ),
            expectedPairs = V2ShiftWriteExpectation.capture(pairs),
            expectedProtection = protection,
        )
    }

    private suspend fun buildCandidates(
        dates: List<LocalDate>,
        option: V2RecurringTemplateOption,
        rawPosition: String,
        history: WorkConfigurationHistory,
        current: RecurringPlanAggregate?,
        timestamp: java.time.Instant,
    ): List<V2ShiftWrite> {
        val currentByDate = current?.occurrences?.associateBy { it.localDate }.orEmpty()
        return dates.map { date ->
            val applicable = history.timeline.revisionAt(date)
                ?: error("No hay una configuración laboral aplicable a $date.")
            if (applicable.value.sector != option.workPlace.sector) {
                error("La forma de trabajar de $date no coincide con la plantilla elegida.")
            }
            val catalog = catalogRepository.observeCatalog(history.timeline.id, applicable.value.sector).first()
            if (catalog.ruleRevisionAt(option.workPlace.id, date) == null) {
                error("El lugar elegido no tiene reglas laborales aplicables a $date.")
            }
            val context = ResolvedWorkConfigurationRevision.resolve(history, date)
            val occurrence = currentByDate[date]
            val original = occurrence
                ?.takeIf { it.state == RecurringOccurrenceState.AUTOMATIC }
                ?.shiftId
                ?.let { id ->
                    when (val lookup = recurringShiftRepository.getShift(id)) {
                        V2ShiftLookup.Missing -> error("La jornada recurrente de $date ya no existe.")
                        is V2ShiftLookup.V2 -> lookup.write
                    }
                }
            if (original == null) {
                buildV2ShiftWrite(
                    id = uuidProvider.newUuid(),
                    date = date,
                    objective = option.objective,
                    workPlace = option.workPlace,
                    workType = option.workType,
                    template = option.template,
                    configurationContext = context,
                    position = rawPosition,
                    timestamp = timestamp,
                    zoneId = zoneId,
                )
            } else {
                editV2ShiftWrite(
                    original = original,
                    date = date,
                    objective = option.objective,
                    workPlace = option.workPlace,
                    workType = option.workType,
                    template = option.template,
                    configurationContext = context,
                    position = rawPosition,
                    updatedAt = timestamp,
                )
            }
        }
    }

    private fun buildRevision(
        state: V2RecurringUiState,
        option: V2RecurringTemplateOption,
        pattern: RecurringPattern,
        start: LocalDate,
        end: LocalDate,
        timestamp: java.time.Instant,
        current: RecurringPlanAggregate?,
    ) = RecurringPlanRevision(
        id = uuidProvider.newUuid(),
        planId = requireNotNull(state.draftPlanId),
        revisionNumber = (current?.latestRevision?.revisionNumber ?: 0) + 1,
        effectiveFrom = start,
        kind = RecurringPlanRevisionKind.ACTIVE,
        endDateInclusive = end,
        pattern = pattern,
        templateId = option.template.id,
        workPlaceId = option.workPlace.id,
        objectiveId = option.objective.id,
        workTypeId = option.workType.id,
        objectiveNameSnapshot = option.objective.fullName,
        objectiveAbbreviationSnapshot = option.objective.abbreviation,
        objectiveAddressSnapshot = option.objective.address,
        workTypeNameSnapshot = option.workType.name,
        workTypeBehaviorSnapshot = option.workType.behavior,
        startTimeSnapshot = option.template.startTime,
        endTimeSnapshot = option.template.endTime,
        colorArgbSnapshot = option.template.colorArgb,
        positionSnapshot = normalizeOptionalWorkText(state.position),
        zoneId = zoneId,
        createdAt = timestamp,
    )

    private fun V2RecurringUiState.toPattern(): RecurringPattern = when (patternKind) {
        V2RecurringPatternKind.WEEKDAYS -> RecurringPattern.Weekdays.of(weekdays)
        V2RecurringPatternKind.EVERY_N_DAYS -> RecurringPattern.EveryNDays(parsePositiveInterval())
        V2RecurringPatternKind.EVERY_N_WEEKS -> RecurringPattern.EveryNWeeks(parsePositiveInterval())
        V2RecurringPatternKind.MONTHLY -> RecurringPattern.Monthly(monthlyOrdinal, monthlyDayOfWeek)
    }

    private fun V2RecurringUiState.parsePositiveInterval(): Int = intervalText.toIntOrNull()
        ?.takeIf { it > 0 }
        ?: error("La repetición necesita un intervalo entero positivo.")

    private fun parseDate(value: String, label: String): LocalDate = parseRecurringDate(value)
        ?: error("La fecha de $label no es válida. Escribila como DD/MM/AAAA.")

    private fun persistCurrent() = persist(_uiState.value.toPersisted())

    private data class PreparedRecurringMutation(
        val preview: RecurringMutationPreview,
        val primaryPlan: RecurringPlanAggregate?,
        val expectedPlans: RecurringPlanExpectation,
        val expectedOccupancy: ShiftOccupancyExpectation,
        val expectedPairs: V2ShiftWriteExpectation,
        val expectedProtection: RecurringProtectionExpectation,
    )

    private data class PatternEditorState(
        val kind: V2RecurringPatternKind,
        val weekdays: Set<DayOfWeek> = emptySet(),
        val interval: Int = 1,
        val ordinal: MonthlyOrdinal = MonthlyOrdinal.FIRST,
        val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    )

    private fun RecurringPattern.toEditorState(): PatternEditorState = when (this) {
        is RecurringPattern.Weekdays -> PatternEditorState(V2RecurringPatternKind.WEEKDAYS, weekdays = days)
        is RecurringPattern.EveryNDays -> PatternEditorState(V2RecurringPatternKind.EVERY_N_DAYS, interval = intervalCount)
        is RecurringPattern.EveryNWeeks -> PatternEditorState(V2RecurringPatternKind.EVERY_N_WEEKS, interval = intervalCount)
        is RecurringPattern.Monthly -> PatternEditorState(
            V2RecurringPatternKind.MONTHLY,
            ordinal = ordinal,
            dayOfWeek = dayOfWeek,
        )
    }

    private companion object {
        const val MAX_POSITION_LENGTH = 120
        const val REVIEW_NEIGHBOR_DAYS = 2L
    }

    private enum class V2RecurringRetryAction {
        NONE,
        LOAD_PLANS,
        LOAD_CREATE_SOURCES,
        LOAD_EXISTING_EDITOR,
        REVIEW,
        SAVE,
    }
}

internal fun formatRecurringDateInput(rawValue: String): String? {
    if (rawValue.any { character -> character !in '0'..'9' && character != '/' }) return null
    if (rawValue.count { it == '/' } > 2) return null

    if (rawValue.count { it == '/' } == 2) {
        val parts = rawValue.split('/')
        val day = parts[0]
        val month = parts[1]
        val year = parts[2]
        if (
            day.isNotEmpty() && month.isNotEmpty() && year.isNotEmpty() &&
            day.length <= 2 && month.length <= 2 && year.length <= 4
        ) {
            return "${day.padStart(2, '0')}/${month.padStart(2, '0')}/$year"
        }
    }

    val digits = rawValue.filter { it in '0'..'9' }
    if (digits.length > 8) return null
    return when {
        digits.length <= 2 -> digits
        digits.length <= 4 -> "${digits.take(2)}/${digits.drop(2)}"
        else -> "${digits.take(2)}/${digits.substring(2, 4)}/${digits.drop(4)}"
    }
}

internal fun normalizeRecurringDateDraftUpdate(rawValue: String): String? {
    val formatted = formatRecurringDateInput(rawValue) ?: return null
    return if ('/' in rawValue && rawValue.length < 10) rawValue else formatted
}

internal fun formatRecurringDate(date: LocalDate): String = date.format(RECURRING_DATE_FORMATTER)

internal fun parseRecurringDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value, RECURRING_DATE_FORMATTER) }
        .recoverCatching { LocalDate.parse(value) }
        .getOrNull()

private fun normalizeRecurringDateText(value: String): String =
    parseRecurringDate(value)?.let(::formatRecurringDate)
        ?: formatRecurringDateInput(value)
        ?: value.take(10)

private fun safeMinusDays(date: LocalDate, days: Long): LocalDate =
    runCatching { date.minusDays(days) }.getOrDefault(LocalDate.MIN)

private fun safePlusDays(date: LocalDate, days: Long): LocalDate =
    runCatching { date.plusDays(days) }.getOrDefault(LocalDate.MAX)

private fun nextPlanMutationInstant(
    candidate: java.time.Instant,
    current: RecurringPlanAggregate?,
    additionalInstants: Iterable<java.time.Instant> = emptyList(),
): java.time.Instant {
    val currentInstants = current?.let { plan ->
        sequenceOf(
            sequenceOf(plan.plan.createdAt),
            plan.revisions.asSequence().map { it.createdAt },
            plan.occurrences.asSequence().map { it.updatedAt },
        ).flatten()
    }.orEmpty()
    val latestStored = (currentInstants + additionalInstants.asSequence()).maxOrNull()
    return if (latestStored == null || candidate.isAfter(latestStored)) candidate else latestStored.plusMillis(1)
}

private fun V2RecurringPersistedState.toUiState() = V2RecurringUiState(
    stage = stage,
    mode = mode,
    timelineId = timelineId,
    draftPlanId = draftPlanId,
    cutDate = cutDate,
    selectedPlanId = selectedPlanId,
    selectedTemplateId = selectedTemplateId,
    position = position,
    patternKind = patternKind,
    weekdays = weekdays,
    intervalText = intervalText,
    monthlyOrdinal = monthlyOrdinal,
    monthlyDayOfWeek = monthlyDayOfWeek,
    startDateText = normalizeRecurringDateText(startDateText),
    endDateText = normalizeRecurringDateText(endDateText),
    conflictPolicy = conflictPolicy,
)

private fun V2RecurringUiState.toPersisted() = V2RecurringPersistedState(
    stage = stage,
    mode = mode,
    timelineId = timelineId,
    selectedPlanId = selectedPlanId ?: selectedPlan?.plan?.id,
    draftPlanId = draftPlanId,
    cutDate = cutDate,
    selectedTemplateId = selectedTemplateId,
    position = position,
    patternKind = patternKind,
    weekdays = weekdays,
    intervalText = intervalText,
    monthlyOrdinal = monthlyOrdinal,
    monthlyDayOfWeek = monthlyDayOfWeek,
    startDateText = startDateText,
    endDateText = endDateText,
    conflictPolicy = conflictPolicy,
)

private fun SavedStateHandle.readRecurringState(): V2RecurringPersistedState = V2RecurringPersistedState(
    stage = get<String>(RECURRING_STAGE)?.let(V2RecurringStage::valueOf) ?: V2RecurringStage.IDLE,
    mode = get<String>(RECURRING_MODE)?.let(V2RecurringMode::valueOf) ?: V2RecurringMode.CREATE,
    timelineId = get<String>(RECURRING_TIMELINE)?.let(UUID::fromString),
    selectedPlanId = get<String>(RECURRING_SELECTED_PLAN)?.let(UUID::fromString),
    draftPlanId = get<String>(RECURRING_DRAFT_PLAN)?.let(UUID::fromString),
    cutDate = get<String>(RECURRING_CUT_DATE)?.let(LocalDate::parse),
    selectedTemplateId = get<String>(RECURRING_TEMPLATE)?.let(UUID::fromString),
    position = get<String>(RECURRING_POSITION).orEmpty(),
    patternKind = get<String>(RECURRING_PATTERN)?.let(V2RecurringPatternKind::valueOf)
        ?: V2RecurringPatternKind.WEEKDAYS,
    weekdays = get<ArrayList<Int>>(RECURRING_WEEKDAYS).orEmpty().mapTo(linkedSetOf(), DayOfWeek::of),
    intervalText = get<String>(RECURRING_INTERVAL) ?: "1",
    monthlyOrdinal = get<String>(RECURRING_ORDINAL)?.let(MonthlyOrdinal::valueOf) ?: MonthlyOrdinal.FIRST,
    monthlyDayOfWeek = get<Int>(RECURRING_MONTHLY_DAY)?.let(DayOfWeek::of) ?: DayOfWeek.MONDAY,
    startDateText = get<String>(RECURRING_START).orEmpty(),
    endDateText = get<String>(RECURRING_END).orEmpty(),
    conflictPolicy = get<String>(RECURRING_POLICY)?.let(RecurringConflictPolicy::valueOf)
        ?: RecurringConflictPolicy.KEEP_EXISTING,
)

private fun SavedStateHandle.writeRecurringState(state: V2RecurringPersistedState) {
    this[RECURRING_STAGE] = state.stage.name
    this[RECURRING_MODE] = state.mode.name
    this[RECURRING_TIMELINE] = state.timelineId?.toString()
    this[RECURRING_SELECTED_PLAN] = state.selectedPlanId?.toString()
    this[RECURRING_DRAFT_PLAN] = state.draftPlanId?.toString()
    this[RECURRING_CUT_DATE] = state.cutDate?.toString()
    this[RECURRING_TEMPLATE] = state.selectedTemplateId?.toString()
    this[RECURRING_POSITION] = state.position
    this[RECURRING_PATTERN] = state.patternKind.name
    this[RECURRING_WEEKDAYS] = ArrayList(state.weekdays.map(DayOfWeek::getValue))
    this[RECURRING_INTERVAL] = state.intervalText
    this[RECURRING_ORDINAL] = state.monthlyOrdinal.name
    this[RECURRING_MONTHLY_DAY] = state.monthlyDayOfWeek.value
    this[RECURRING_START] = state.startDateText
    this[RECURRING_END] = state.endDateText
    this[RECURRING_POLICY] = state.conflictPolicy.name
}

private const val RECURRING_STAGE = "v2_recurring_stage"
private const val RECURRING_MODE = "v2_recurring_mode"
private const val RECURRING_TIMELINE = "v2_recurring_timeline"
private const val RECURRING_SELECTED_PLAN = "v2_recurring_selected_plan"
private const val RECURRING_DRAFT_PLAN = "v2_recurring_draft_plan"
private const val RECURRING_CUT_DATE = "v2_recurring_cut_date"
private const val RECURRING_TEMPLATE = "v2_recurring_template"
private const val RECURRING_POSITION = "v2_recurring_position"
private const val RECURRING_PATTERN = "v2_recurring_pattern"
private const val RECURRING_WEEKDAYS = "v2_recurring_weekdays"
private const val RECURRING_INTERVAL = "v2_recurring_interval"
private const val RECURRING_ORDINAL = "v2_recurring_ordinal"
private const val RECURRING_MONTHLY_DAY = "v2_recurring_monthly_day"
private const val RECURRING_START = "v2_recurring_start"
private const val RECURRING_END = "v2_recurring_end"
private const val RECURRING_POLICY = "v2_recurring_policy"
private val RECURRING_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
