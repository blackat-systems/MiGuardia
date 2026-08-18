package com.blackatsystems.miguardia.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.ScheduleCombinationRepository
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.profile.activeProfileObjectives
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val store: GuardProfileStore,
    private val objectives: ObjectiveRepository,
    private val schedules: ScheduleCombinationRepository,
) : ViewModel() {
    private val saveMutex = Mutex()
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(ProfileUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<ProfileUiState> = _uiState
    private var profileJob: Job? = null
    private var projectionJob: Job? = null

    init {
        observeProfile()
        observeActiveWork()
    }

    fun open() = _uiState.update {
        it.copy(
            surface = ProfileSurface.EDITOR,
            draft = it.profile.toDraft(),
            showDiscardConfirmation = false,
            errorMessage = null,
            infoMessage = null,
            canRetryLoad = false,
        )
    }

    fun updateDisplayName(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(displayName = value), errorMessage = null, canRetryLoad = false)
    }

    fun updateCompany(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(company = value), errorMessage = null, canRetryLoad = false)
    }

    fun save() {
        if (_uiState.value.isSaving) return
        val draft = _uiState.value.draft
        if (draft.company.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Ingresá la empresa para guardar el perfil.",
                    canRetryLoad = false,
                )
            }
            return
        }
        viewModelScope.launch {
            if (!saveMutex.tryLock()) return@launch
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                    infoMessage = null,
                    canRetryLoad = false,
                )
            }
            try {
                val saved = store.save(draft.displayName, draft.company)
                _uiState.update {
                    it.copy(
                        profile = saved,
                        draft = saved.toDraft(),
                        isSaving = false,
                        infoMessage = "Perfil laboral guardado.",
                        canRetryLoad = false,
                    )
                }
            } catch (_: IllegalArgumentException) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Ingresá la empresa para guardar el perfil.",
                        canRetryLoad = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "No pudimos guardar el perfil. Intentá nuevamente.",
                        canRetryLoad = false,
                    )
                }
            } finally {
                saveMutex.unlock()
            }
        }
    }

    fun requestBack() = _uiState.update {
        if (it.isDirty) {
            it.copy(showDiscardConfirmation = true)
        } else {
            it.copy(surface = ProfileSurface.NONE, infoMessage = null, errorMessage = null)
        }
    }

    fun dismissDiscard() = _uiState.update { it.copy(showDiscardConfirmation = false) }

    fun confirmDiscard() = _uiState.update {
        it.copy(
            surface = ProfileSurface.NONE,
            draft = it.profile.toDraft(),
            showDiscardConfirmation = false,
            errorMessage = null,
            infoMessage = null,
        )
    }

    fun clearMessage() = _uiState.update {
        it.copy(errorMessage = null, infoMessage = null, canRetryLoad = false)
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, canRetryLoad = false) }
        observeProfile()
        observeActiveWork()
    }

    private fun observeProfile() {
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            store.profile
                .catch {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "No pudimos leer el perfil guardado.",
                            canRetryLoad = true,
                        )
                    }
                }
                .collect { profile ->
                    _uiState.update { state ->
                        state.copy(
                            profile = profile,
                            draft = if (state.isDirty) state.draft else profile.toDraft(),
                            isLoading = false,
                        )
                    }
                }
        }
    }

    private fun observeActiveWork() {
        projectionJob?.cancel()
        projectionJob = viewModelScope.launch {
            objectives.observeActive()
                .flatMapLatest { activeObjectives ->
                    if (activeObjectives.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            activeObjectives.map { objective -> schedules.observeByObjective(objective.id) },
                        ) { scheduleLists ->
                            activeProfileObjectives(
                                objectives = activeObjectives,
                                schedules = scheduleLists.flatMap(List<ScheduleCombination>::toList),
                            )
                        }
                    }
                }
                .catch {
                    _uiState.update {
                        it.copy(
                            errorMessage = "No pudimos leer los objetivos y horarios activos.",
                            canRetryLoad = true,
                        )
                    }
                }
                .collect { projection ->
                    _uiState.update { it.copy(activeObjectives = projection) }
                }
        }
    }

    class Factory(
        private val store: GuardProfileStore,
        private val objectives: ObjectiveRepository,
        private val schedules: ScheduleCombinationRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(store, objectives, schedules) as T
        }
    }
}
