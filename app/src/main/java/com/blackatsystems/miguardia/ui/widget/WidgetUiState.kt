package com.blackatsystems.miguardia.ui.widget

import com.blackatsystems.miguardia.widget.WidgetInstancePreferences

enum class WidgetSurface {
    NONE,
    GLOBAL,
}

data class InstalledWidgetUi(
    val appWidgetId: Int,
    val position: Int,
    val preferences: WidgetInstancePreferences,
)

data class WidgetUiState(
    val surface: WidgetSurface = WidgetSurface.NONE,
    val instances: List<InstalledWidgetUi> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
