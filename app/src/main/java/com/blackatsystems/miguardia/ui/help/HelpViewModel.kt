package com.blackatsystems.miguardia.ui.help

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HelpViewModel internal constructor(
    store: OnboardingVersionStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val coordinator = HelpCoordinator(
        store = store,
        scope = viewModelScope,
        restoredSession = savedStateHandle.readHelpSession(),
        persistSession = savedStateHandle::writeHelpSession,
    )

    val uiState: StateFlow<HelpUiState> = coordinator.uiState

    fun synchronizeWorkSetup(rootState: WorkSetupState, surface: WorkSetupSurface) =
        coordinator.synchronizeWorkSetup(rootState, surface)

    fun retryRead() = coordinator.retryRead()
    fun next() = coordinator.next()
    fun back() = coordinator.back()
    fun requestExit() = coordinator.requestExit()
    fun dismissExitConfirmation() = coordinator.dismissExitConfirmation()
    fun confirmExit() = coordinator.confirmExit()
    fun finish() = coordinator.finish()
    fun retryCompletion() = coordinator.retryCompletion()
    fun startReplay() = coordinator.startReplay()
    fun consumeNavigation(sequence: Long) = coordinator.consumeNavigation(sequence)

    internal class Factory(
        private val store: OnboardingVersionStore,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(HelpViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return HelpViewModel(store, extras.createSavedStateHandle()) as T
        }
    }
}

internal class HelpCoordinator(
    private val store: OnboardingVersionStore,
    private val scope: kotlinx.coroutines.CoroutineScope,
    restoredSession: RestoredHelpSession? = null,
    private val persistSession: (HelpSession?) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(HelpUiState())
    val uiState: StateFlow<HelpUiState> = mutableState

    private var readJob: Job? = null
    private var completionJob: Job? = null
    private var nextSessionToken = 1L
    private var nextNavigationSequence = 1L
    private var restoredSession: RestoredHelpSession? = restoredSession

    init {
        startReading()
    }

    fun synchronizeWorkSetup(rootState: WorkSetupState, surface: WorkSetupSurface) {
        val resolved = rootState != WorkSetupState.Loading
        val ready = rootState is WorkSetupState.V2Ready
        mutableState.update {
            it.copy(
                workSetupResolved = resolved,
                rootIsV2Ready = ready,
                workSetupSurface = surface,
            )
        }
        val automaticSession = mutableState.value.session?.takeIf {
            it.mode == HelpSessionMode.AUTOMATIC
        }
        if ((!ready || surface != WorkSetupSurface.NONE) && automaticSession != null) {
            cancelSessionWithoutCompletion()
        }
        maybeStartEligibleSession()
    }

    fun retryRead() {
        if (mutableState.value.readState != HelpReadState.Error) return
        mutableState.update { it.copy(readState = HelpReadState.Loading, errorMessage = null) }
        startReading()
    }

    fun startReplay() {
        val state = mutableState.value
        if (!state.canRepeat || state.session != null || state.isSaving) return
        startSession(
            mode = HelpSessionMode.REPLAY,
            restored = restoredSession?.takeIf { it.mode == HelpSessionMode.REPLAY },
        )
    }

    fun next() {
        val state = mutableState.value
        val session = state.session ?: return
        if (state.isSaving) return
        val next = when (session.stage) {
            HelpSessionStage.INTRODUCTION -> if (session.stepIndex < INTRODUCTION_STEP_COUNT - 1) {
                session.copy(stepIndex = session.stepIndex + 1)
            } else {
                session.copy(stage = HelpSessionStage.TOUR, stepIndex = 0)
            }

            HelpSessionStage.TOUR -> if (session.stepIndex < HelpTourStep.entries.lastIndex) {
                session.copy(stepIndex = session.stepIndex + 1)
            } else {
                return
            }
        }
        setSession(next)
    }

    fun back() {
        val state = mutableState.value
        val session = state.session ?: return
        if (state.isSaving) return
        when (session.stage) {
            HelpSessionStage.INTRODUCTION -> if (session.stepIndex > 0) {
                setSession(session.copy(stepIndex = session.stepIndex - 1))
            } else {
                requestExit()
            }

            HelpSessionStage.TOUR -> if (session.stepIndex > 0) {
                setSession(session.copy(stepIndex = session.stepIndex - 1))
            } else {
                setSession(
                    session.copy(
                        stage = HelpSessionStage.INTRODUCTION,
                        stepIndex = INTRODUCTION_STEP_COUNT - 1,
                    ),
                )
            }
        }
    }

    fun requestExit() {
        val state = mutableState.value
        val session = state.session ?: return
        if (state.isSaving) return
        if (session.mode == HelpSessionMode.REPLAY) {
            closeReplay()
        } else {
            mutableState.update { it.copy(showExitConfirmation = true, errorMessage = null) }
        }
    }

    fun dismissExitConfirmation() {
        if (mutableState.value.isSaving) return
        mutableState.update { it.copy(showExitConfirmation = false) }
    }

    fun confirmExit() {
        val session = mutableState.value.session ?: return
        if (session.mode != HelpSessionMode.AUTOMATIC) return
        completeAutomatic(session.token)
    }

    fun finish() {
        val session = mutableState.value.session ?: return
        if (mutableState.value.isSaving || session.stage != HelpSessionStage.TOUR ||
            session.stepIndex != HelpTourStep.entries.lastIndex
        ) return
        if (session.mode == HelpSessionMode.REPLAY) {
            closeReplay()
        } else {
            completeAutomatic(session.token)
        }
    }

    fun retryCompletion() {
        val session = mutableState.value.session ?: return
        if (session.mode != HelpSessionMode.AUTOMATIC || mutableState.value.isSaving) return
        completeAutomatic(session.token)
    }

    fun consumeNavigation(sequence: Long) {
        mutableState.update { state ->
            if (state.navigationEvent?.sequence == sequence) {
                state.copy(navigationEvent = null)
            } else {
                state
            }
        }
    }

    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch {
            store.state.collect { stored ->
                mutableState.update { state ->
                    state.copy(
                        readState = when (stored) {
                            OnboardingStoreState.Error -> HelpReadState.Error
                            is OnboardingStoreState.Ready -> HelpReadState.Ready(stored.completedVersion)
                        },
                        errorMessage = if (stored == OnboardingStoreState.Error) {
                            "No pudimos leer el estado de la guía. Tus datos laborales no cambiaron."
                        } else {
                            state.errorMessage
                        },
                    )
                }
                if (stored is OnboardingStoreState.Ready &&
                    stored.completedVersion >= OnboardingPreferencesStore.CURRENT_VERSION
                ) {
                    val automatic = mutableState.value.session?.mode == HelpSessionMode.AUTOMATIC
                    if (automatic && !mutableState.value.isSaving) {
                        clearSession()
                    }
                    if (restoredSession?.mode == HelpSessionMode.AUTOMATIC) {
                        restoredSession = null
                    }
                }
                maybeStartEligibleSession()
            }
        }
    }

    private fun maybeStartEligibleSession() {
        val state = mutableState.value
        val read = state.readState as? HelpReadState.Ready ?: return
        if (state.session != null || state.isSaving) return
        if (!state.rootIsV2Ready || state.workSetupSurface != WorkSetupSurface.NONE) return
        val replay = restoredSession?.takeIf { it.mode == HelpSessionMode.REPLAY }
        if (replay != null) {
            startSession(HelpSessionMode.REPLAY, replay)
            return
        }
        if (read.completedVersion >= OnboardingPreferencesStore.CURRENT_VERSION) {
            restoredSession = null
            return
        }
        val restored = restoredSession?.takeIf { it.mode == HelpSessionMode.AUTOMATIC }
        startSession(HelpSessionMode.AUTOMATIC, restored)
    }

    private fun startSession(mode: HelpSessionMode, restored: RestoredHelpSession?) {
        val stage = restored?.stage ?: HelpSessionStage.INTRODUCTION
        val maxIndex = when (stage) {
            HelpSessionStage.INTRODUCTION -> INTRODUCTION_STEP_COUNT - 1
            HelpSessionStage.TOUR -> HelpTourStep.entries.lastIndex
        }
        val session = HelpSession(
            token = nextSessionToken++,
            mode = mode,
            stage = stage,
            stepIndex = restored?.stepIndex?.coerceIn(0, maxIndex) ?: 0,
        )
        restoredSession = null
        mutableState.update {
            it.copy(
                session = session,
                isSaving = false,
                showExitConfirmation = false,
                errorMessage = null,
            )
        }
        persistSession(session)
    }

    private fun setSession(session: HelpSession) {
        if (mutableState.value.session?.token != session.token) return
        mutableState.update { it.copy(session = session, errorMessage = null) }
        persistSession(session)
    }

    private fun completeAutomatic(sessionToken: Long) {
        val state = mutableState.value
        if (state.isSaving || state.session?.token != sessionToken) return
        mutableState.update {
            it.copy(isSaving = true, showExitConfirmation = false, errorMessage = null)
        }
        completionJob?.cancel()
        completionJob = scope.launch {
            try {
                val completedVersion = store.completeAtLeast(
                    OnboardingPreferencesStore.CURRENT_VERSION,
                ) {
                    val current = mutableState.value
                    current.session?.token == sessionToken &&
                        current.rootIsV2Ready &&
                        current.workSetupSurface == WorkSetupSurface.NONE
                }
                val current = mutableState.value
                if (
                    current.session?.token != sessionToken ||
                    !current.rootIsV2Ready ||
                    current.workSetupSurface != WorkSetupSurface.NONE
                ) return@launch
                clearSession(
                    completedVersion = completedVersion,
                    navigationTarget = HelpNavigationTarget.CALENDAR,
                )
            } catch (cancelled: CancellationException) {
                if (mutableState.value.session?.token == sessionToken) {
                    mutableState.update { it.copy(isSaving = false) }
                }
                throw cancelled
            } catch (_: Exception) {
                if (mutableState.value.session?.token == sessionToken) {
                    mutableState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "No pudimos guardar que terminaste la guía. Seguí en este paso y reintentá.",
                        )
                    }
                }
            }
        }
    }

    private fun closeReplay() {
        val session = mutableState.value.session ?: return
        if (session.mode != HelpSessionMode.REPLAY) return
        clearSession(navigationTarget = HelpNavigationTarget.HELP)
    }

    private fun cancelSessionWithoutCompletion() {
        completionJob?.cancel()
        completionJob = null
        clearSession()
    }

    private fun clearSession(
        completedVersion: Int? = null,
        navigationTarget: HelpNavigationTarget? = null,
    ) {
        persistSession(null)
        mutableState.update { state ->
            state.copy(
                readState = completedVersion?.let { HelpReadState.Ready(it) } ?: state.readState,
                session = null,
                isSaving = false,
                showExitConfirmation = false,
                errorMessage = null,
                navigationEvent = navigationTarget?.let {
                    HelpNavigationEvent(nextNavigationSequence++, it)
                } ?: state.navigationEvent,
            )
        }
    }
}

internal data class RestoredHelpSession(
    val mode: HelpSessionMode,
    val stage: HelpSessionStage,
    val stepIndex: Int,
)

internal fun SavedStateHandle.readHelpSession(): RestoredHelpSession? {
    val mode = get<String>(KEY_HELP_MODE)
        ?.let { stored -> HelpSessionMode.entries.firstOrNull { it.name == stored } }
        ?: return null
    val stage = get<String>(KEY_HELP_STAGE)
        ?.let { stored -> HelpSessionStage.entries.firstOrNull { it.name == stored } }
        ?: return null
    return RestoredHelpSession(
        mode = mode,
        stage = stage,
        stepIndex = get<Int>(KEY_HELP_STEP) ?: 0,
    )
}

internal fun SavedStateHandle.writeHelpSession(session: HelpSession?) {
    this[KEY_HELP_MODE] = session?.mode?.name
    this[KEY_HELP_STAGE] = session?.stage?.name
    this[KEY_HELP_STEP] = session?.stepIndex
}

private const val KEY_HELP_MODE = "help_session_mode"
private const val KEY_HELP_STAGE = "help_session_stage"
private const val KEY_HELP_STEP = "help_session_step"
