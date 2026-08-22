package com.blackatsystems.miguardia.ui.worksetup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.repository.LocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.NewWorkPlace
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WeekendDays
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRules
import com.blackatsystems.miguardia.core.domain.work.normalizedForNewV2WorkPlace
import com.blackatsystems.miguardia.core.domain.work.projectLoadedWorkSetupState
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkSetupViewModel(
    configurationRepository: WorkConfigurationRepository,
    catalogRepository: WorkCatalogRepository,
    objectiveRepository: ObjectiveRepository,
    clock: Clock,
    uuidProvider: () -> UUID,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val coordinator = WorkSetupCoordinator(
        configurationRepository = configurationRepository,
        catalogRepository = catalogRepository,
        objectiveRepository = objectiveRepository,
        clock = clock,
        uuidProvider = uuidProvider,
        scope = viewModelScope,
        initialPersistedState = savedStateHandle.readWorkSetupState(),
        persist = savedStateHandle::writeWorkSetupState,
    )

    val uiState = coordinator.uiState

    fun retryLoad() = coordinator.retryLoad()
    fun refreshReferenceDate() = coordinator.refreshReferenceDate()
    fun selectSector(sector: WorkSector) = coordinator.selectSector(sector)
    fun saveInitialSector() = coordinator.saveInitialSector()
    fun openOverview() = coordinator.openOverview()
    fun openFirstWorkSet() = coordinator.openFirstWorkSet()
    fun updatePlaceDraft(transform: (WorkPlaceDraft) -> WorkPlaceDraft) =
        coordinator.updatePlaceDraft(transform)
    fun updateTemplateDraft(transform: (WorkTemplateDraft) -> WorkTemplateDraft) =
        coordinator.updateTemplateDraft(transform)
    fun continueToTemplate() = coordinator.continueToTemplate()
    fun saveFirstWorkSet() = coordinator.saveFirstWorkSet()
    fun openAdditionalTemplate() = coordinator.openAdditionalTemplate()
    fun selectTemplatePlace(id: UUID) = coordinator.selectTemplatePlace(id)
    fun selectTemplateType(id: UUID) = coordinator.selectTemplateType(id)
    fun saveAdditionalTemplate() = coordinator.saveAdditionalTemplate()
    fun startAnotherPlace() = coordinator.startAnotherPlace()
    fun saveAdditionalPlace() = coordinator.saveAdditionalPlace()
    fun returnToCalendar() = coordinator.returnToCalendar()
    fun requestBack() = coordinator.requestBack()
    fun dismissDiscard() = coordinator.dismissDiscard()
    fun confirmDiscard() = coordinator.confirmDiscard()
    fun clearMessage() = coordinator.clearMessage()

    class Factory(
        private val configurationRepository: WorkConfigurationRepository,
        private val catalogRepository: WorkCatalogRepository,
        private val objectiveRepository: ObjectiveRepository,
        private val clock: Clock,
        private val uuidProvider: () -> UUID = UUID::randomUUID,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(WorkSetupViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return WorkSetupViewModel(
                configurationRepository = configurationRepository,
                catalogRepository = catalogRepository,
                objectiveRepository = objectiveRepository,
                clock = clock,
                uuidProvider = uuidProvider,
                savedStateHandle = extras.createSavedStateHandle(),
            ) as T
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkSetupCoordinator(
    private val configurationRepository: WorkConfigurationRepository,
    private val catalogRepository: WorkCatalogRepository,
    private val objectiveRepository: ObjectiveRepository,
    private val clock: Clock,
    private val uuidProvider: () -> UUID,
    private val scope: CoroutineScope,
    initialPersistedState: WorkSetupPersistedState = WorkSetupPersistedState(),
    private val persist: (WorkSetupPersistedState) -> Unit = {},
) {
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(
        WorkSetupUiState(
            rootState = WorkSetupState.Loading,
            selectedSector = initialPersistedState.selectedSector,
            surface = initialPersistedState.surface,
            step = initialPersistedState.step,
            placeDraft = initialPersistedState.placeDraft,
            templateDraft = initialPersistedState.templateDraft,
            selectedTemplatePlaceId = initialPersistedState.selectedTemplatePlaceId,
            selectedTemplateTypeId = initialPersistedState.selectedTemplateTypeId,
            lastCreatedPlaceId = initialPersistedState.lastCreatedPlaceId,
            lastCreatedTypeId = initialPersistedState.lastCreatedTypeId,
        ),
    )
    val uiState: kotlinx.coroutines.flow.StateFlow<WorkSetupUiState> = _uiState

    private var loadJob: Job? = null
    private var currentHistory: WorkConfigurationHistory? = null

    init {
        startLoading()
    }

    fun retryLoad() {
        _uiState.update {
            it.copy(rootState = WorkSetupState.Loading, errorMessage = null, infoMessage = null)
        }
        startLoading()
    }

    fun refreshReferenceDate() = startLoading()

    fun selectSector(sector: WorkSector) {
        val state = _uiState.value
        if (state.rootState != WorkSetupState.FreshInstall || state.isSavingSector) return
        updateAndPersist { it.copy(selectedSector = sector, errorMessage = null) }
    }

    fun saveInitialSector() {
        val state = _uiState.value
        val sector = state.selectedSector ?: return
        if (state.rootState != WorkSetupState.FreshInstall || state.isSavingSector) return
        _uiState.update { it.copy(isSavingSector = true, errorMessage = null) }
        scope.launch {
            try {
                val today = LocalDate.now(clock)
                configurationRepository.createInitial(
                    timelineId = uuidProvider(),
                    firstRevision = EffectiveRevision(
                        id = uuidProvider(),
                        effectiveFrom = today,
                        value = WorkConfiguration(
                            sector = sector,
                            hoursReference = HoursReference.PendingSetup,
                            availabilityLabel = null,
                        ),
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingSector = false,
                        errorMessage = "No pudimos guardar el rubro. Tu selección sigue disponible para reintentar.",
                    )
                }
            }
        }
    }

    fun openOverview() {
        val surface = when (_uiState.value.rootState) {
            is WorkSetupState.LegacyV1,
            is WorkSetupState.LegacyV1WithFutureActivation,
            -> WorkSetupSurface.LEGACY_INFORMATION

            is WorkSetupState.V2NeedsFirstSet,
            is WorkSetupState.V2Ready,
            -> WorkSetupSurface.OVERVIEW

            else -> return
        }
        updateAndPersist { it.copy(surface = surface, errorMessage = null, infoMessage = null) }
    }

    fun openFirstWorkSet() {
        val sector = currentSector() ?: return
        val rootState = _uiState.value.rootState
        if (rootState !is WorkSetupState.V2NeedsFirstSet) return
        updateAndPersist {
            it.copy(
                surface = WorkSetupSurface.FIRST_WORK_SET,
                step = WorkSetupStep.PLACE_AND_RULES,
                placeDraft = if (it.placeDraft == WorkPlaceDraft()) WorkPlaceDraft() else it.placeDraft,
                templateDraft = if (it.templateDraft == WorkTemplateDraft()) {
                    WorkTemplateDraft(typeName = sector.suggestedRegularTypeName())
                } else {
                    it.templateDraft
                },
                showDiscardConfirmation = false,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun updatePlaceDraft(transform: (WorkPlaceDraft) -> WorkPlaceDraft) {
        if (_uiState.value.isSavingWorkSet) return
        updateAndPersist { it.copy(placeDraft = transform(it.placeDraft), errorMessage = null) }
    }

    fun updateTemplateDraft(transform: (WorkTemplateDraft) -> WorkTemplateDraft) {
        if (_uiState.value.isSavingWorkSet || _uiState.value.isSavingTemplate) return
        updateAndPersist { it.copy(templateDraft = transform(it.templateDraft), errorMessage = null) }
    }

    fun continueToTemplate() {
        val validation = validatePlaceDraft(_uiState.value.placeDraft)
        if (!validation.isValid) {
            _uiState.update { it.copy(errorMessage = validation.message) }
            return
        }
        updateAndPersist {
            it.copy(step = WorkSetupStep.TYPE_AND_TEMPLATE, errorMessage = null)
        }
    }

    fun saveFirstWorkSet() {
        val state = _uiState.value
        if (state.surface != WorkSetupSurface.FIRST_WORK_SET || state.isSavingWorkSet) return
        val placeValidation = validatePlaceDraft(state.placeDraft)
        val templateValidation = validateTemplateDraft(state.templateDraft, requireTypeName = true)
        val validationMessage = placeValidation.message ?: templateValidation.message
        if (validationMessage != null) {
            _uiState.update { it.copy(errorMessage = validationMessage) }
            return
        }
        val history = currentHistory
        val configurationRevision = currentConfigurationRevision()
        if (history == null || configurationRevision == null) {
            _uiState.update {
                it.copy(errorMessage = "No pudimos confirmar la configuración vigente. Reintentá la carga.")
            }
            return
        }
        val firstWorkSet = try {
            buildFirstWorkSet(state, history, configurationRevision)
        } catch (_: IllegalArgumentException) {
            _uiState.update {
                it.copy(errorMessage = "Revisá los datos del lugar y del horario antes de guardar.")
            }
            return
        }
        _uiState.update { it.copy(isSavingWorkSet = true, errorMessage = null) }
        scope.launch {
            try {
                catalogRepository.createFirstWorkSet(firstWorkSet)
                updateAndPersist {
                    it.copy(
                        surface = WorkSetupSurface.COMPLETION,
                        lastCreatedPlaceId = firstWorkSet.workPlace.id,
                        lastCreatedTypeId = firstWorkSet.workType.id,
                        selectedTemplatePlaceId = firstWorkSet.workPlace.id,
                        selectedTemplateTypeId = firstWorkSet.workType.id,
                        isSavingWorkSet = false,
                        errorMessage = null,
                        infoMessage = "El lugar y su primer horario quedaron guardados.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingWorkSet = false,
                        errorMessage = error.toEverydayMessage(
                            "No pudimos guardar el lugar y el horario. El borrador sigue disponible.",
                        ),
                    )
                }
            }
        }
    }

    fun openAdditionalTemplate() {
        val state = _uiState.value
        val selectedPlaceId = state.selectedTemplatePlaceId?.takeIf { selectedId ->
            state.catalog?.workPlaces?.any { place -> place.id == selectedId && place.isActive } == true
        }
        val selectedTypeId = state.selectedTemplateTypeId?.takeIf { selectedId ->
            state.catalog?.workTypes?.any { type -> type.id == selectedId && type.isActive } == true
        }
        val placeId = selectedPlaceId ?: state.lastCreatedPlaceId ?: state.activePlaceOptions.singleOrNull()?.id
        val typeId = selectedTypeId ?: state.lastCreatedTypeId ?: state.activeTypeOptions.singleOrNull()?.id
        updateAndPersist {
            it.copy(
                surface = WorkSetupSurface.ADDITIONAL_TEMPLATE,
                templateDraft = WorkTemplateDraft(),
                selectedTemplatePlaceId = placeId,
                selectedTemplateTypeId = typeId,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun selectTemplatePlace(id: UUID) {
        if (_uiState.value.isSavingTemplate) return
        updateAndPersist { it.copy(selectedTemplatePlaceId = id, errorMessage = null) }
    }

    fun selectTemplateType(id: UUID) {
        if (_uiState.value.isSavingTemplate) return
        updateAndPersist { it.copy(selectedTemplateTypeId = id, errorMessage = null) }
    }

    fun saveAdditionalTemplate() {
        val state = _uiState.value
        if (state.surface != WorkSetupSurface.ADDITIONAL_TEMPLATE || state.isSavingTemplate) return
        val placeId = state.selectedTemplatePlaceId
        val typeId = state.selectedTemplateTypeId
        val validation = validateTemplateDraft(state.templateDraft, requireTypeName = false)
        val validationMessage = when {
            placeId == null -> "Elegí el lugar para este horario."
            typeId == null -> "Elegí el tipo de trabajo para este horario."
            else -> validation.message
        }
        if (validationMessage != null) {
            _uiState.update { it.copy(errorMessage = validationMessage) }
            return
        }
        val catalog = state.catalog
        val place = catalog?.workPlaces?.singleOrNull { it.id == placeId && it.isActive }
        val type = catalog?.workTypes?.singleOrNull { it.id == typeId && it.isActive }
        if (place == null || type == null || place.timelineId != type.timelineId || place.sector != type.sector) {
            _uiState.update { it.copy(errorMessage = "El lugar o el tipo ya no está disponible.") }
            return
        }
        val configurationRevision = currentConfigurationRevision()
        if (configurationRevision == null || configurationRevision.value.sector != place.sector) {
            _uiState.update {
                it.copy(errorMessage = "La configuración vigente cambió. Volvé a abrir esta pantalla.")
            }
            return
        }
        val timestamp = clock.instant()
        val template = WorkTemplate(
            id = uuidProvider(),
            timelineId = place.timelineId,
            sector = place.sector,
            workPlaceId = place.id,
            objectiveId = place.objectiveId,
            workTypeId = type.id,
            startTime = requireNotNull(parseWorkTimeOrNull(state.templateDraft.startTime)),
            endTime = requireNotNull(parseWorkTimeOrNull(state.templateDraft.endTime)),
            colorArgb = requireNotNull(state.templateDraft.colorArgb),
            isActive = true,
            legacyScheduleCombinationId = null,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        _uiState.update { it.copy(isSavingTemplate = true, errorMessage = null) }
        scope.launch {
            try {
                catalogRepository.createWorkTemplate(template)
                updateAndPersist {
                    it.copy(
                        surface = WorkSetupSurface.COMPLETION,
                        lastCreatedPlaceId = place.id,
                        lastCreatedTypeId = type.id,
                        selectedTemplatePlaceId = place.id,
                        selectedTemplateTypeId = type.id,
                        isSavingTemplate = false,
                        errorMessage = null,
                        infoMessage = "El horario quedó guardado.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingTemplate = false,
                        errorMessage = error.toEverydayMessage(
                            "No pudimos guardar el horario. El borrador sigue disponible.",
                        ),
                    )
                }
            }
        }
    }

    fun startAnotherPlace() {
        val state = _uiState.value
        val followsSuccessfulFirstSet = state.surface == WorkSetupSurface.COMPLETION &&
            state.lastCreatedPlaceId != null &&
            state.lastCreatedTypeId != null
        if (state.rootState !is WorkSetupState.V2Ready && !followsSuccessfulFirstSet) return
        updateAndPersist {
            it.copy(
                surface = WorkSetupSurface.ADDITIONAL_PLACE,
                placeDraft = WorkPlaceDraft(),
                showDiscardConfirmation = false,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun saveAdditionalPlace() {
        val state = _uiState.value
        if (state.surface != WorkSetupSurface.ADDITIONAL_PLACE || state.isSavingWorkSet) return
        val validation = validatePlaceDraft(state.placeDraft)
        if (!validation.isValid) {
            _uiState.update { it.copy(errorMessage = validation.message) }
            return
        }
        val history = currentHistory
        val configurationRevision = currentConfigurationRevision()
        if (history == null || configurationRevision == null) {
            _uiState.update {
                it.copy(errorMessage = "No pudimos confirmar la configuración vigente. Reintentá la carga.")
            }
            return
        }
        val newWorkPlace = try {
            buildNewWorkPlace(state, history, configurationRevision)
        } catch (_: IllegalArgumentException) {
            _uiState.update { it.copy(errorMessage = "Revisá los datos del lugar antes de guardar.") }
            return
        }
        _uiState.update { it.copy(isSavingWorkSet = true, errorMessage = null) }
        scope.launch {
            try {
                catalogRepository.createWorkPlace(newWorkPlace)
                updateAndPersist {
                    it.copy(
                        surface = WorkSetupSurface.COMPLETION,
                        lastCreatedPlaceId = newWorkPlace.workPlace.id,
                        selectedTemplatePlaceId = newWorkPlace.workPlace.id,
                        isSavingWorkSet = false,
                        errorMessage = null,
                        infoMessage = "El lugar y sus reglas quedaron guardados. Ahora podés agregarle un horario.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingWorkSet = false,
                        errorMessage = error.toEverydayMessage(
                            "No pudimos guardar el lugar. El borrador sigue disponible.",
                        ),
                    )
                }
            }
        }
    }

    fun returnToCalendar() {
        updateAndPersist {
            it.copy(
                surface = WorkSetupSurface.NONE,
                step = WorkSetupStep.PLACE_AND_RULES,
                placeDraft = WorkPlaceDraft(),
                templateDraft = WorkTemplateDraft(),
                showDiscardConfirmation = false,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun requestBack() {
        val state = _uiState.value
        if (state.isSavingSector || state.isSavingWorkSet || state.isSavingTemplate) return
        when {
            state.surface == WorkSetupSurface.FIRST_WORK_SET &&
                state.step == WorkSetupStep.TYPE_AND_TEMPLATE -> updateAndPersist {
                it.copy(step = WorkSetupStep.PLACE_AND_RULES, errorMessage = null)
            }

            state.surface == WorkSetupSurface.COMPLETION -> returnToCalendar()
            state.hasUnconfirmedDraft -> _uiState.update { it.copy(showDiscardConfirmation = true) }
            else -> returnToCalendar()
        }
    }

    fun dismissDiscard() = _uiState.update { it.copy(showDiscardConfirmation = false) }

    fun confirmDiscard() {
        val state = _uiState.value
        if (state.isSavingSector || state.isSavingWorkSet || state.isSavingTemplate) return
        returnToCalendar()
    }

    fun clearMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    private fun startLoading() {
        loadJob?.cancel()
        loadJob = scope.launch {
            configurationRepository.observe()
                .flatMapLatest { history ->
                    val referenceDate = LocalDate.now(clock)
                    val revision = history?.timeline?.revisionAt(referenceDate)
                    if (history == null || revision == null) {
                        flowOf(
                            LoadedWorkSetup(
                                history = history,
                                catalog = null,
                                objectives = emptyList(),
                                referenceDate = referenceDate,
                            ),
                        )
                    } else {
                        combine(
                            catalogRepository.observeCatalog(history.timeline.id, revision.value.sector),
                            objectiveRepository.observeAll(),
                        ) { catalog, objectives ->
                            LoadedWorkSetup(
                                history = history,
                                catalog = catalog,
                                objectives = objectives,
                                referenceDate = referenceDate,
                            )
                        }
                    }
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    currentHistory = null
                    _uiState.update {
                        it.copy(
                            rootState = WorkSetupState.LoadError,
                            catalog = null,
                            errorMessage = "No pudimos leer tu configuración laboral.",
                            isSavingSector = false,
                        )
                    }
                }
                .collect { loaded ->
                    currentHistory = loaded.history
                    val rootState = projectLoadedWorkSetupState(
                        history = loaded.history,
                        catalog = loaded.catalog,
                        referenceDate = loaded.referenceDate,
                    )
                    val sector = rootState.currentSectorOrNull()
                    _uiState.update {
                        it.copy(
                            rootState = rootState,
                            selectedSector = sector ?: it.selectedSector,
                            catalog = loaded.catalog,
                            objectivesById = loaded.objectives.associateBy(Objective::id),
                            surface = it.surface.normalizedFor(rootState),
                            isSavingSector = false,
                            errorMessage = null,
                        )
                    }
                    persistCurrentState()
                }
        }
    }

    private fun buildFirstWorkSet(
        state: WorkSetupUiState,
        history: WorkConfigurationHistory,
        configurationRevision: EffectiveRevision<WorkConfiguration>,
    ): FirstWorkSet {
        val newWorkPlace = buildNewWorkPlace(state, history, configurationRevision)
        val timestamp = newWorkPlace.objective.createdAt
        val sector = configurationRevision.value.sector
        val workType = WorkType.create(
            id = uuidProvider(),
            timelineId = history.timeline.id,
            sector = sector,
            rawName = state.templateDraft.typeName,
            timestamp = timestamp,
        )
        val workTemplate = WorkTemplate(
            id = uuidProvider(),
            timelineId = history.timeline.id,
            sector = sector,
            workPlaceId = newWorkPlace.workPlace.id,
            objectiveId = newWorkPlace.objective.id,
            workTypeId = workType.id,
            startTime = requireNotNull(parseWorkTimeOrNull(state.templateDraft.startTime)),
            endTime = requireNotNull(parseWorkTimeOrNull(state.templateDraft.endTime)),
            colorArgb = requireNotNull(state.templateDraft.colorArgb),
            isActive = true,
            legacyScheduleCombinationId = null,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        return FirstWorkSet(
            objective = newWorkPlace.objective,
            workPlace = newWorkPlace.workPlace,
            firstRuleRevision = newWorkPlace.firstRuleRevision,
            configurationContext = newWorkPlace.configurationContext,
            workType = workType,
            workTemplate = workTemplate,
        )
    }

    private fun buildNewWorkPlace(
        state: WorkSetupUiState,
        history: WorkConfigurationHistory,
        configurationRevision: EffectiveRevision<WorkConfiguration>,
    ): NewWorkPlace {
        val timestamp = clock.instant()
        val configurationContext = ResolvedWorkConfigurationRevision.resolve(
            history = history,
            date = configurationRevision.effectiveFrom,
        )
        val sector = configurationRevision.value.sector
        val objective = Objective(
            id = uuidProvider(),
            fullName = state.placeDraft.name,
            abbreviation = state.placeDraft.abbreviation,
            address = state.placeDraft.address,
            note = state.placeDraft.note,
            isActive = true,
            createdAt = timestamp,
            updatedAt = timestamp,
        ).normalizedForNewV2WorkPlace()
        val workPlace = WorkPlace(
            id = uuidProvider(),
            timelineId = history.timeline.id,
            sector = sector,
            objectiveId = objective.id,
            isActive = true,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        val ruleRevision = WorkplaceRuleRevision(
            id = uuidProvider(),
            timelineId = history.timeline.id,
            sector = sector,
            workPlaceId = workPlace.id,
            objectiveId = objective.id,
            effectiveFrom = configurationRevision.effectiveFrom,
            rules = state.placeDraft.toWorkplaceRules(),
            createdAt = timestamp,
        )
        return NewWorkPlace(
            objective = objective,
            workPlace = workPlace,
            firstRuleRevision = ruleRevision,
            configurationContext = configurationContext,
        )
    }

    private fun currentSector(): WorkSector? = _uiState.value.rootState.currentSectorOrNull()

    private fun currentConfigurationRevision(): EffectiveRevision<WorkConfiguration>? {
        val rootRevision = when (val rootState = _uiState.value.rootState) {
            is WorkSetupState.V2NeedsFirstSet -> rootState.configurationRevision
            is WorkSetupState.V2Ready -> rootState.configurationRevision
            else -> null
        }
        val applicableRevision = currentHistory
            ?.timeline
            ?.revisionAt(LocalDate.now(clock))
            ?: return null
        return rootRevision?.takeIf { it.id == applicableRevision.id }
    }

    private fun updateAndPersist(transform: (WorkSetupUiState) -> WorkSetupUiState) {
        _uiState.update(transform)
        persistCurrentState()
    }

    private fun persistCurrentState() = persist(_uiState.value.toPersistedState())
}

private data class LoadedWorkSetup(
    val history: WorkConfigurationHistory?,
    val catalog: WorkCatalog?,
    val objectives: List<Objective>,
    val referenceDate: LocalDate,
)

private fun WorkPlaceDraft.toWorkplaceRules(): WorkplaceRules {
    val nightRule = if (nightHoursEnabled) {
        NightHoursRule.Defined(
            startInclusive = requireNotNull(parseWorkTimeOrNull(nightStart)),
            endExclusive = requireNotNull(parseWorkTimeOrNull(nightEnd)),
            differentTreatment = true,
            showDedicatedSummary = true,
        )
    } else {
        NightHoursRule.Disabled
    }
    val weekendDays = when {
        classifySaturday && classifySunday -> WeekendDays.SATURDAY_AND_SUNDAY
        classifySaturday -> WeekendDays.SATURDAY
        classifySunday -> WeekendDays.SUNDAY
        else -> null
    }
    val weekendRule = weekendDays?.let { days ->
        WeekendRule.Defined(
            days = days,
            differentTreatment = true,
            showDedicatedSummary = showWeekendSummary,
        )
    } ?: WeekendRule.None
    return WorkplaceRules(
        nightHours = nightRule,
        weekend = weekendRule,
        holiday = HolidayRule(
            differentTreatment = classifyHoliday,
            showDedicatedSummary = classifyHoliday && showHolidaySummary,
        ),
    )
}

private fun WorkSetupState.currentSectorOrNull(): WorkSector? = when (this) {
    is WorkSetupState.V2NeedsFirstSet -> configurationRevision.value.sector
    is WorkSetupState.V2Ready -> configurationRevision.value.sector
    else -> null
}

private fun WorkSetupSurface.normalizedFor(rootState: WorkSetupState): WorkSetupSurface = when (rootState) {
    WorkSetupState.Loading,
    WorkSetupState.LoadError,
    -> this

    WorkSetupState.FreshInstall -> WorkSetupSurface.NONE

    is WorkSetupState.LegacyV1,
    is WorkSetupState.LegacyV1WithFutureActivation,
    -> if (this == WorkSetupSurface.LEGACY_INFORMATION) this else WorkSetupSurface.NONE

    is WorkSetupState.V2NeedsFirstSet ->
        if (this == WorkSetupSurface.LEGACY_INFORMATION) WorkSetupSurface.OVERVIEW else this

    is WorkSetupState.V2Ready -> when (this) {
        WorkSetupSurface.LEGACY_INFORMATION -> WorkSetupSurface.OVERVIEW
        WorkSetupSurface.FIRST_WORK_SET -> WorkSetupSurface.COMPLETION
        else -> this
    }
}

private fun WorkSetupUiState.toPersistedState() = WorkSetupPersistedState(
    selectedSector = selectedSector,
    surface = surface,
    step = step,
    placeDraft = placeDraft,
    templateDraft = templateDraft,
    selectedTemplatePlaceId = selectedTemplatePlaceId,
    selectedTemplateTypeId = selectedTemplateTypeId,
    lastCreatedPlaceId = lastCreatedPlaceId,
    lastCreatedTypeId = lastCreatedTypeId,
)

private fun Exception.toEverydayMessage(fallback: String): String = when (this) {
    is LocalDataException -> message ?: fallback
    is IllegalArgumentException -> message ?: fallback
    else -> fallback
}

internal fun SavedStateHandle.readWorkSetupState(): WorkSetupPersistedState = WorkSetupPersistedState(
    selectedSector = get<String>(KEY_SELECTED_SECTOR)?.let { stored ->
        WorkSector.entries.firstOrNull { it.name == stored }
    },
    surface = get<String>(KEY_SURFACE)?.let { stored ->
        WorkSetupSurface.entries.firstOrNull { it.name == stored }
    } ?: WorkSetupSurface.NONE,
    step = get<String>(KEY_STEP)?.let { stored ->
        WorkSetupStep.entries.firstOrNull { it.name == stored }
    } ?: WorkSetupStep.PLACE_AND_RULES,
    placeDraft = WorkPlaceDraft(
        name = get<String>(KEY_PLACE_NAME).orEmpty(),
        abbreviation = get<String>(KEY_PLACE_ABBREVIATION).orEmpty(),
        address = get<String>(KEY_PLACE_ADDRESS).orEmpty(),
        note = get<String>(KEY_PLACE_NOTE).orEmpty(),
        nightHoursEnabled = get<Boolean>(KEY_NIGHT_ENABLED) ?: false,
        nightStart = get<String>(KEY_NIGHT_START).orEmpty(),
        nightEnd = get<String>(KEY_NIGHT_END).orEmpty(),
        classifySaturday = get<Boolean>(KEY_SATURDAY) ?: false,
        classifySunday = get<Boolean>(KEY_SUNDAY) ?: false,
        showWeekendSummary = get<Boolean>(KEY_WEEKEND_SUMMARY) ?: false,
        classifyHoliday = get<Boolean>(KEY_HOLIDAY) ?: false,
        showHolidaySummary = get<Boolean>(KEY_HOLIDAY_SUMMARY) ?: false,
    ),
    templateDraft = WorkTemplateDraft(
        typeName = get<String>(KEY_TYPE_NAME).orEmpty(),
        startTime = get<String>(KEY_START_TIME).orEmpty(),
        endTime = get<String>(KEY_END_TIME).orEmpty(),
        colorArgb = get<Int>(KEY_COLOR),
    ),
    selectedTemplatePlaceId = get<String>(KEY_TEMPLATE_PLACE_ID)?.toUuidOrNull(),
    selectedTemplateTypeId = get<String>(KEY_TEMPLATE_TYPE_ID)?.toUuidOrNull(),
    lastCreatedPlaceId = get<String>(KEY_LAST_PLACE_ID)?.toUuidOrNull(),
    lastCreatedTypeId = get<String>(KEY_LAST_TYPE_ID)?.toUuidOrNull(),
)

internal fun SavedStateHandle.writeWorkSetupState(state: WorkSetupPersistedState) {
    this[KEY_SELECTED_SECTOR] = state.selectedSector?.name
    this[KEY_SURFACE] = state.surface.name
    this[KEY_STEP] = state.step.name
    this[KEY_PLACE_NAME] = state.placeDraft.name
    this[KEY_PLACE_ABBREVIATION] = state.placeDraft.abbreviation
    this[KEY_PLACE_ADDRESS] = state.placeDraft.address
    this[KEY_PLACE_NOTE] = state.placeDraft.note
    this[KEY_NIGHT_ENABLED] = state.placeDraft.nightHoursEnabled
    this[KEY_NIGHT_START] = state.placeDraft.nightStart
    this[KEY_NIGHT_END] = state.placeDraft.nightEnd
    this[KEY_SATURDAY] = state.placeDraft.classifySaturday
    this[KEY_SUNDAY] = state.placeDraft.classifySunday
    this[KEY_WEEKEND_SUMMARY] = state.placeDraft.showWeekendSummary
    this[KEY_HOLIDAY] = state.placeDraft.classifyHoliday
    this[KEY_HOLIDAY_SUMMARY] = state.placeDraft.showHolidaySummary
    this[KEY_TYPE_NAME] = state.templateDraft.typeName
    this[KEY_START_TIME] = state.templateDraft.startTime
    this[KEY_END_TIME] = state.templateDraft.endTime
    this[KEY_COLOR] = state.templateDraft.colorArgb
    this[KEY_TEMPLATE_PLACE_ID] = state.selectedTemplatePlaceId?.toString()
    this[KEY_TEMPLATE_TYPE_ID] = state.selectedTemplateTypeId?.toString()
    this[KEY_LAST_PLACE_ID] = state.lastCreatedPlaceId?.toString()
    this[KEY_LAST_TYPE_ID] = state.lastCreatedTypeId?.toString()
}

private fun String.toUuidOrNull(): UUID? = runCatching(UUID::fromString).getOrNull()

private const val KEY_SELECTED_SECTOR = "work_setup_selected_sector"
private const val KEY_SURFACE = "work_setup_surface"
private const val KEY_STEP = "work_setup_step"
private const val KEY_PLACE_NAME = "work_setup_place_name"
private const val KEY_PLACE_ABBREVIATION = "work_setup_place_abbreviation"
private const val KEY_PLACE_ADDRESS = "work_setup_place_address"
private const val KEY_PLACE_NOTE = "work_setup_place_note"
private const val KEY_NIGHT_ENABLED = "work_setup_night_enabled"
private const val KEY_NIGHT_START = "work_setup_night_start"
private const val KEY_NIGHT_END = "work_setup_night_end"
private const val KEY_SATURDAY = "work_setup_saturday"
private const val KEY_SUNDAY = "work_setup_sunday"
private const val KEY_WEEKEND_SUMMARY = "work_setup_weekend_summary"
private const val KEY_HOLIDAY = "work_setup_holiday"
private const val KEY_HOLIDAY_SUMMARY = "work_setup_holiday_summary"
private const val KEY_TYPE_NAME = "work_setup_type_name"
private const val KEY_START_TIME = "work_setup_start_time"
private const val KEY_END_TIME = "work_setup_end_time"
private const val KEY_COLOR = "work_setup_color"
private const val KEY_TEMPLATE_PLACE_ID = "work_setup_template_place_id"
private const val KEY_TEMPLATE_TYPE_ID = "work_setup_template_type_id"
private const val KEY_LAST_PLACE_ID = "work_setup_last_place_id"
private const val KEY_LAST_TYPE_ID = "work_setup_last_type_id"
