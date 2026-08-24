package com.blackatsystems.miguardia.ui.management

import androidx.lifecycle.SavedStateHandle
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
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import com.blackatsystems.miguardia.core.domain.shift.ShiftPlanningWarning
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.shift.planV2ShiftBatch
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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
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

private class SelectedTemplateUnavailableException : IllegalStateException(
    "El horario elegido ya no está disponible para estas fechas.",
)

data class V2ManualShiftLoadUiState(
    val stage: V2ManualShiftLoadStage = V2ManualShiftLoadStage.IDLE,
    val timelineId: UUID? = null,
    val sector: WorkSector? = null,
    val selectedDates: Set<LocalDate> = emptySet(),
    val templateOptions: List<V2ManualShiftTemplateOption> = emptyList(),
    val selectedTemplateId: UUID? = null,
    val position: String = "",
    val occupiedPolicy: OccupiedDatePolicy? = null,
    val occupiedDates: Set<LocalDate> = emptySet(),
    val plannedDates: Set<LocalDate> = emptySet(),
    val omittedDates: Set<LocalDate> = emptySet(),
    val warnings: List<String> = emptyList(),
    val acknowledgedWarnings: List<String> = emptyList(),
    val reviewFingerprint: String? = null,
    val backfillFrom: LocalDate? = null,
    val configuredFrom: LocalDate? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val successSequence: Int = 0,
) {
    val isActive: Boolean
        get() = stage != V2ManualShiftLoadStage.IDLE

    val selectedOption: V2ManualShiftTemplateOption?
        get() = templateOptions.firstOrNull { it.template.id == selectedTemplateId }
}

internal data class V2ManualShiftLoadPersistedState(
    val stage: V2ManualShiftLoadStage = V2ManualShiftLoadStage.IDLE,
    val timelineId: UUID? = null,
    val sector: WorkSector? = null,
    val selectedDates: Set<LocalDate> = emptySet(),
    val selectedTemplateId: UUID? = null,
    val position: String = "",
    val occupiedPolicy: OccupiedDatePolicy? = null,
    val occupiedDates: Set<LocalDate> = emptySet(),
    val acknowledgedWarnings: List<String> = emptyList(),
    val reviewFingerprint: String? = null,
)

class V2ManualShiftLoadViewModel(
    configurationRepository: WorkConfigurationRepository,
    catalogRepository: WorkCatalogRepository,
    objectiveRepository: ObjectiveRepository,
    shiftRepository: ShiftRepository,
    medicalLeaveRepository: MedicalLeaveRepository,
    v2ShiftRepository: V2ShiftRepository,
    clock: Clock,
    zoneId: ZoneId,
    uuidProvider: UuidProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val coordinator = V2ManualShiftLoadCoordinator(
        configurationRepository = configurationRepository,
        catalogRepository = catalogRepository,
        objectiveRepository = objectiveRepository,
        shiftRepository = shiftRepository,
        medicalLeaveRepository = medicalLeaveRepository,
        v2ShiftRepository = v2ShiftRepository,
        clock = clock,
        zoneId = zoneId,
        uuidProvider = uuidProvider,
        scope = viewModelScope,
        initialPersistedState = savedStateHandle.readV2ManualShiftLoadState(),
        persist = savedStateHandle::writeV2ManualShiftLoadState,
    )

    val uiState: StateFlow<V2ManualShiftLoadUiState> = coordinator.uiState

    fun start(rootState: WorkSetupState) = coordinator.start(rootState)
    fun confirmDates(dates: Set<LocalDate>) = coordinator.confirmDates(dates)
    fun chooseTemplate(id: UUID) = coordinator.chooseTemplate(id)
    fun updatePosition(value: String) = coordinator.updatePosition(value)
    fun requestReview() = coordinator.requestReview()
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
        private val clock: Clock = Clock.system(AppDefaults.zoneId()),
        private val zoneId: ZoneId = AppDefaults.zoneId(),
        private val uuidProvider: UuidProvider = UuidProvider(UUID::randomUUID),
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
                clock = clock,
                zoneId = zoneId,
                uuidProvider = uuidProvider,
                savedStateHandle = extras.createSavedStateHandle(),
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
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val uuidProvider: UuidProvider,
    private val scope: CoroutineScope,
    initialPersistedState: V2ManualShiftLoadPersistedState = V2ManualShiftLoadPersistedState(),
    private val persist: (V2ManualShiftLoadPersistedState) -> Unit = {},
) {
    private val writeMutex = Mutex()
    private val _uiState = MutableStateFlow(initialPersistedState.toUiState())
    val uiState: StateFlow<V2ManualShiftLoadUiState> = _uiState

    init {
        if (
            _uiState.value.isActive &&
            _uiState.value.stage != V2ManualShiftLoadStage.SELECT_DATES &&
            _uiState.value.selectedDates.isNotEmpty()
        ) {
            scope.launch { restorePreparedState() }
        }
    }

    fun start(rootState: WorkSetupState) {
        val ready = rootState as? WorkSetupState.V2Ready ?: return
        updateAndPersist {
            V2ManualShiftLoadUiState(
                stage = V2ManualShiftLoadStage.SELECT_DATES,
                timelineId = ready.timelineId,
                sector = null,
                successSequence = it.successSequence,
            )
        }
    }

    fun confirmDates(dates: Set<LocalDate>) {
        if (!_uiState.value.isActive || _uiState.value.isLoading || _uiState.value.isSaving) return
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
                occupiedPolicy = null,
                occupiedDates = emptySet(),
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                errorMessage = null,
            )
        }
        scope.launch { loadTemplateOptions(V2ManualShiftLoadStage.CHOOSE_TEMPLATE) }
    }

    fun chooseTemplate(id: UUID) {
        val state = _uiState.value
        if (state.stage != V2ManualShiftLoadStage.CHOOSE_TEMPLATE || state.isLoading) return
        if (state.templateOptions.none { it.template.id == id }) return
        updateAndPersist {
            it.copy(
                selectedTemplateId = id,
                occupiedPolicy = null,
                occupiedDates = emptySet(),
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                errorMessage = null,
            )
        }
    }

    fun updatePosition(value: String) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        updateAndPersist { it.copy(position = value.take(MAX_POSITION_LENGTH), errorMessage = null) }
    }

    fun requestReview() {
        val state = _uiState.value
        if (state.selectedTemplateId == null) {
            showError("Elegí un lugar, tipo y horario.")
            return
        }
        if (state.isLoading || state.isSaving) return
        scope.launch { prepareReview() }
    }

    fun confirmBackfill() {
        val state = _uiState.value
        if (state.stage != V2ManualShiftLoadStage.CONFIRM_BACKFILL || state.isLoading || state.isSaving) return
        scope.launch {
            if (!writeMutex.tryLock()) return@launch
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val context = loadSelectionContext()
                val selection = context.selection as? WorkDateSelection.NeedsNewV2Backfill
                if (selection == null) {
                    prepareReview()
                    return@launch
                }
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
                _uiState.update {
                    it.copy(
                        stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
                        isSaving = false,
                        backfillFrom = null,
                        configuredFrom = null,
                    )
                }
                persistCurrentState()
                prepareReview()
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
                errorMessage = null,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.stage != V2ManualShiftLoadStage.REVIEW || state.isSaving || state.isLoading) return
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
                            isSaving = false,
                            errorMessage = "Las jornadas o advertencias cambiaron. Revisá nuevamente antes de guardar.",
                        )
                    }
                    return@launch
                }
                if (prepared.mutation.shiftsToInsert.isEmpty()) {
                    showError("No hay días disponibles para guardar con la opción elegida.")
                    return@launch
                }
                v2ShiftRepository.applyV2Batch(
                    mutation = prepared.mutation,
                    expectedOccupancy = prepared.expectedOccupancy,
                )
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
        if (!_uiState.value.isActive || _uiState.value.isLoading || _uiState.value.isSaving) return
        updateAndPersist {
            it.copy(
                stage = V2ManualShiftLoadStage.SELECT_DATES,
                occupiedPolicy = null,
                occupiedDates = emptySet(),
                warnings = emptyList(),
                acknowledgedWarnings = emptyList(),
                reviewFingerprint = null,
                backfillFrom = null,
                configuredFrom = null,
                errorMessage = null,
            )
        }
    }

    fun retry() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        when {
            state.selectedDates.isEmpty() -> _uiState.update { it.copy(errorMessage = null) }
            state.stage == V2ManualShiftLoadStage.REVIEW -> scope.launch { prepareReview() }
            else -> scope.launch { loadTemplateOptions(V2ManualShiftLoadStage.CHOOSE_TEMPLATE) }
        }
    }

    fun cancel() {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        val nextSequence = _uiState.value.successSequence
        _uiState.value = V2ManualShiftLoadUiState(successSequence = nextSequence)
        persistCurrentState()
    }

    fun discardIncompatible() {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
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

    private suspend fun restorePreparedState() {
        val restoredStage = _uiState.value.stage
        val restoredFingerprint = _uiState.value.reviewFingerprint
        _uiState.update { it.copy(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE) }
        loadTemplateOptions(
            targetStage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
            persistLoadedState = false,
        )
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
                context.options.any { it.template.id == id }
            }
            val selectionBecameUnavailable = previousSelection != null && selectedTemplateId == null
            _uiState.update { state ->
                state.copy(
                    stage = targetStage,
                    sector = context.sector,
                    templateOptions = context.options,
                    selectedTemplateId = selectedTemplateId,
                    occupiedPolicy = if (selectionBecameUnavailable) null else state.occupiedPolicy,
                    occupiedDates = if (selectionBecameUnavailable) emptySet() else state.occupiedDates,
                    plannedDates = if (selectionBecameUnavailable) emptySet() else state.plannedDates,
                    omittedDates = if (selectionBecameUnavailable) emptySet() else state.omittedDates,
                    warnings = if (selectionBecameUnavailable) emptyList() else state.warnings,
                    acknowledgedWarnings = if (selectionBecameUnavailable) emptyList() else state.acknowledgedWarnings,
                    reviewFingerprint = if (selectionBecameUnavailable) null else state.reviewFingerprint,
                    isLoading = false,
                    errorMessage = when {
                        selectionBecameUnavailable ->
                            "El horario elegido ya no está disponible. Conservamos las fechas y el puesto; elegí otro."
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
                    isLoading = false,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SelectedTemplateUnavailableException) {
            recoverFromUnavailableTemplate()
        } catch (error: Exception) {
            showError(error.message ?: "No pudimos preparar la revisión. Reintentá.")
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

    private suspend fun loadSelectionContext(): LoadedSelectionContext {
        val state = _uiState.value
        val timelineId = requireNotNull(state.timelineId) { "La configuración de esta carga ya no está disponible." }
        val dates = state.selectedDates
        validateSingleMonth(dates)
        val history = configurationRepository.get()
            ?: throw IllegalStateException("No pudimos encontrar la configuración laboral.")
        if (history.timeline.id != timelineId) {
            throw IllegalStateException("La forma de trabajar cambió. Salí de la carga y volvé a empezar.")
        }
        val selection = classifyWorkDateSelection(history, dates)
        val sector = when (selection) {
            is WorkDateSelection.V2 -> selection.sector
            is WorkDateSelection.NeedsNewV2Backfill -> selection.sector
        }
        if (
            state.sector != null &&
            state.stage != V2ManualShiftLoadStage.SELECT_DATES &&
            sector != state.sector
        ) {
            throw IllegalStateException("La selección ya no pertenece al mismo rubro.")
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
            .associateWith { objectiveId ->
                objectiveRepository.getById(objectiveId)
                    ?: throw IllegalStateException("Un lugar guardado perdió su información visible.")
            }
        val options = eligibleTemplates.map { template ->
            val place = requireNotNull(placesById[template.workPlaceId])
            V2ManualShiftTemplateOption(
                objective = requireNotNull(objectivesById[place.objectiveId]),
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
        return LoadedSelectionContext(history, selection, sector, catalog, options)
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
        val timestamp = clock.instant()
        val candidates = dates.map { date ->
            buildV2ShiftWrite(
                id = uuidProvider.newUuid(),
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
        val occupiedDates = existing
            .filter { it.localStartDate in _uiState.value.selectedDates }
            .mapTo(linkedSetOf(), Shift::localStartDate)
        val policy = requestedPolicy ?: OccupiedDatePolicy.ADD_SECOND_SHIFT
        val plan = planV2ShiftBatch(
            selectedDates = _uiState.value.selectedDates,
            existingShifts = existing,
            candidates = candidates,
            policy = policy,
        )
        val savedDates = plan.mutation.shiftsToInsert
            .mapTo(linkedSetOf()) { it.shift.localStartDate }
        val mutation = plan.mutation.copy(
            explicitDayStatusDatesToClear = savedDates,
        )
        val coexistenceWarnings = medicalLeaves
            .filter { leave -> savedDates.any { it in leave.startDate..leave.endDateInclusive } }
            .map { leave ->
                "Existe una carpeta médica entre ${leave.startDate.format(DATE_FORMATTER)} y " +
                    "${leave.endDateInclusive.format(DATE_FORMATTER)}. No se modificará."
            }
        val warningTexts = plan.warnings.map(::warningText) + coexistenceWarnings
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
        "${startTimeSnapshot.format(TIME_FORMATTER)}–${endTimeSnapshot.format(TIME_FORMATTER)}"

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
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

private fun V2ManualShiftLoadPersistedState.toUiState() = V2ManualShiftLoadUiState(
    stage = stage,
    timelineId = timelineId,
    sector = sector,
    selectedDates = selectedDates,
    selectedTemplateId = selectedTemplateId,
    position = position,
    occupiedPolicy = occupiedPolicy,
    occupiedDates = occupiedDates,
    acknowledgedWarnings = acknowledgedWarnings,
    reviewFingerprint = reviewFingerprint,
)

private fun V2ManualShiftLoadUiState.toPersistedState() = V2ManualShiftLoadPersistedState(
    stage = stage,
    timelineId = timelineId,
    sector = sector,
    selectedDates = selectedDates,
    selectedTemplateId = selectedTemplateId,
    position = position,
    occupiedPolicy = occupiedPolicy,
    occupiedDates = occupiedDates,
    acknowledgedWarnings = acknowledgedWarnings,
    reviewFingerprint = reviewFingerprint,
)

internal fun SavedStateHandle.readV2ManualShiftLoadState(): V2ManualShiftLoadPersistedState =
    V2ManualShiftLoadPersistedState(
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
        position = get<String>(KEY_POSITION).orEmpty(),
        occupiedPolicy = get<String>(KEY_OCCUPIED_POLICY)?.let { stored ->
            OccupiedDatePolicy.entries.firstOrNull { it.name == stored }
        },
        occupiedDates = get<ArrayList<String>>(KEY_OCCUPIED_DATES)
            .orEmpty()
            .mapNotNull { stored -> runCatching { LocalDate.parse(stored) }.getOrNull() }
            .toSet(),
        acknowledgedWarnings = get<ArrayList<String>>(KEY_ACKNOWLEDGED_WARNINGS).orEmpty(),
        reviewFingerprint = get<String>(KEY_REVIEW_FINGERPRINT),
    )

internal fun SavedStateHandle.writeV2ManualShiftLoadState(state: V2ManualShiftLoadPersistedState) {
    if (state.stage == V2ManualShiftLoadStage.IDLE) {
        listOf(
            KEY_STAGE,
            KEY_TIMELINE_ID,
            KEY_SECTOR,
            KEY_SELECTED_DATES,
            KEY_TEMPLATE_ID,
            KEY_POSITION,
            KEY_OCCUPIED_POLICY,
            KEY_OCCUPIED_DATES,
            KEY_ACKNOWLEDGED_WARNINGS,
            KEY_REVIEW_FINGERPRINT,
        ).forEach { key -> remove<Any>(key) }
        return
    }
    this[KEY_STAGE] = state.stage.name
    this[KEY_TIMELINE_ID] = state.timelineId?.toString()
    this[KEY_SECTOR] = state.sector?.name
    this[KEY_SELECTED_DATES] = ArrayList(state.selectedDates.sorted().map(LocalDate::toString))
    this[KEY_TEMPLATE_ID] = state.selectedTemplateId?.toString()
    this[KEY_POSITION] = state.position
    this[KEY_OCCUPIED_POLICY] = state.occupiedPolicy?.name
    this[KEY_OCCUPIED_DATES] = ArrayList(state.occupiedDates.sorted().map(LocalDate::toString))
    this[KEY_ACKNOWLEDGED_WARNINGS] = ArrayList(state.acknowledgedWarnings)
    this[KEY_REVIEW_FINGERPRINT] = state.reviewFingerprint
}

private fun String.toUuidOrNull(): UUID? = runCatching(UUID::fromString).getOrNull()

private const val KEY_STAGE = "v2_manual_shift.stage"
private const val KEY_TIMELINE_ID = "v2_manual_shift.timeline_id"
private const val KEY_SECTOR = "v2_manual_shift.sector"
private const val KEY_SELECTED_DATES = "v2_manual_shift.selected_dates"
private const val KEY_TEMPLATE_ID = "v2_manual_shift.template_id"
private const val KEY_POSITION = "v2_manual_shift.position"
private const val KEY_OCCUPIED_POLICY = "v2_manual_shift.occupied_policy"
private const val KEY_OCCUPIED_DATES = "v2_manual_shift.occupied_dates"
private const val KEY_ACKNOWLEDGED_WARNINGS = "v2_manual_shift.acknowledged_warnings"
private const val KEY_REVIEW_FINGERPRINT = "v2_manual_shift.review_fingerprint"
