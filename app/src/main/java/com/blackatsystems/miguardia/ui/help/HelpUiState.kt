package com.blackatsystems.miguardia.ui.help

import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface

enum class HelpSessionMode {
    AUTOMATIC,
    REPLAY,
}

enum class HelpSessionStage {
    INTRODUCTION,
    TOUR,
}

enum class HelpTourStep {
    MENU,
    TODAY_CARD,
    MONTH_AND_GRID,
    DAY_DETAIL,
    PHOTOS,
    LOAD_AND_REPEAT,
    SUMMARY,
    HELP,
}

data class HelpSession(
    val token: Long,
    val mode: HelpSessionMode,
    val stage: HelpSessionStage = HelpSessionStage.INTRODUCTION,
    val stepIndex: Int = 0,
) {
    init {
        val lastIndex = when (stage) {
            HelpSessionStage.INTRODUCTION -> INTRODUCTION_STEP_COUNT - 1
            HelpSessionStage.TOUR -> HelpTourStep.entries.lastIndex
        }
        require(stepIndex in 0..lastIndex)
    }

    val tourStep: HelpTourStep?
        get() = if (stage == HelpSessionStage.TOUR) HelpTourStep.entries[stepIndex] else null
}

sealed interface HelpReadState {
    data object Loading : HelpReadState
    data class Ready(val completedVersion: Int) : HelpReadState
    data object Error : HelpReadState
}

enum class HelpNavigationTarget {
    CALENDAR,
    HELP,
}

data class HelpNavigationEvent(
    val sequence: Long,
    val target: HelpNavigationTarget,
)

data class HelpUiState(
    val readState: HelpReadState = HelpReadState.Loading,
    val session: HelpSession? = null,
    val isSaving: Boolean = false,
    val showExitConfirmation: Boolean = false,
    val errorMessage: String? = null,
    val navigationEvent: HelpNavigationEvent? = null,
    val workSetupResolved: Boolean = false,
    val rootIsV2Ready: Boolean = false,
    val workSetupSurface: WorkSetupSurface = WorkSetupSurface.NONE,
) {
    val currentVersionCompleted: Boolean
        get() = (readState as? HelpReadState.Ready)
            ?.completedVersion
            ?.let { it >= OnboardingPreferencesStore.CURRENT_VERSION } == true

    val automaticDecisionPending: Boolean
        get() = rootIsV2Ready &&
            workSetupSurface == WorkSetupSurface.NONE &&
            when (val read = readState) {
                HelpReadState.Loading,
                HelpReadState.Error,
                -> true

                is HelpReadState.Ready ->
                    read.completedVersion < OnboardingPreferencesStore.CURRENT_VERSION
            }

    val canConsumePendingDestination: Boolean
        get() = workSetupResolved &&
            rootIsV2Ready &&
            workSetupSurface == WorkSetupSurface.NONE &&
            session == null &&
            !isSaving &&
            currentVersionCompleted

    val canRepeat: Boolean
        get() = rootIsV2Ready &&
            workSetupSurface == WorkSetupSurface.NONE &&
            session == null &&
            readState is HelpReadState.Ready

    companion object {
        fun completedForPreview(): HelpUiState = HelpUiState(
            readState = HelpReadState.Ready(OnboardingPreferencesStore.CURRENT_VERSION),
            workSetupResolved = true,
            rootIsV2Ready = true,
        )
    }
}

data class HelpActions(
    val synchronizeWorkSetup: (WorkSetupState, WorkSetupSurface) -> Unit = { _, _ -> },
    val retryRead: () -> Unit = {},
    val next: () -> Unit = {},
    val back: () -> Unit = {},
    val requestExit: () -> Unit = {},
    val dismissExitConfirmation: () -> Unit = {},
    val confirmExit: () -> Unit = {},
    val finish: () -> Unit = {},
    val retryCompletion: () -> Unit = {},
    val startReplay: () -> Unit = {},
    val consumeNavigation: (Long) -> Unit = {},
) {
    companion object {
        fun from(viewModel: HelpViewModel): HelpActions = HelpActions(
            synchronizeWorkSetup = viewModel::synchronizeWorkSetup,
            retryRead = viewModel::retryRead,
            next = viewModel::next,
            back = viewModel::back,
            requestExit = viewModel::requestExit,
            dismissExitConfirmation = viewModel::dismissExitConfirmation,
            confirmExit = viewModel::confirmExit,
            finish = viewModel::finish,
            retryCompletion = viewModel::retryCompletion,
            startReplay = viewModel::startReplay,
            consumeNavigation = viewModel::consumeNavigation,
        )
    }
}

internal const val INTRODUCTION_STEP_COUNT = 3
