package com.blackatsystems.miguardia.ui.profile

import com.blackatsystems.miguardia.profile.ActiveProfileObjective
import com.blackatsystems.miguardia.profile.DEFAULT_GUARD_COMPANY
import com.blackatsystems.miguardia.profile.GuardProfile

enum class ProfileSurface { NONE, EDITOR }

data class ProfileDraft(
    val displayName: String = "",
    val company: String = DEFAULT_GUARD_COMPANY,
)

data class ProfileUiState(
    val surface: ProfileSurface = ProfileSurface.NONE,
    val profile: GuardProfile = GuardProfile(),
    val draft: ProfileDraft = ProfileDraft(),
    val activeObjectives: List<ActiveProfileObjective> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val canRetryLoad: Boolean = false,
) {
    val isDirty: Boolean
        get() = draft.displayName != profile.displayName.orEmpty() || draft.company != profile.company
}

internal fun GuardProfile.toDraft(): ProfileDraft = ProfileDraft(
    displayName = displayName.orEmpty(),
    company = company,
)
