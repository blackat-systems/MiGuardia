package com.blackatsystems.miguardia.ui.notifications

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationSystemAccessState

enum class NotificationSurface {
    NONE,
    GLOBAL,
    SHIFT,
}

data class NotificationUiState(
    val surface: NotificationSurface = NotificationSurface.NONE,
    val preferences: NotificationPreferences = NotificationPreferences(),
    val systemAccess: NotificationSystemAccessState = NotificationSystemAccessState(
        notificationPermissionGranted = false,
        exactAlarmAccessGranted = false,
    ),
    val selectedShift: Shift? = null,
    val shiftOverride: ShiftNotificationConfig? = null,
    val restorableShifts: List<Shift> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)
