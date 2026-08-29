package com.blackatsystems.miguardia.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blackatsystems.miguardia.widget.NextEventAppWidgetProvider
import com.blackatsystems.miguardia.widget.WidgetPreferencesStore
import com.blackatsystems.miguardia.widget.WidgetRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WidgetViewModel(
    private val appWidgetManager: AppWidgetManager,
    private val provider: ComponentName,
    private val preferences: WidgetPreferencesStore,
    private val runtime: WidgetRuntime,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WidgetUiState())
    val uiState: StateFlow<WidgetUiState> = _uiState

    fun open() {
        _uiState.update { it.copy(surface = WidgetSurface.GLOBAL) }
        refresh()
    }

    fun close() = _uiState.update { it.copy(surface = WidgetSurface.NONE, errorMessage = null) }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val installedIds = appWidgetManager.getAppWidgetIds(provider).filter { it > 0 }.sorted()
                val stored = preferences.all()
                _uiState.update { state ->
                    state.copy(
                        instances = installedIds.mapIndexed { index, id ->
                            InstalledWidgetUi(
                                appWidgetId = id,
                                position = index + 1,
                                preferences = stored[id] ?: WidgetPreferencesStore.SafeDefault,
                            )
                        },
                        isLoading = false,
                    )
                }
                runtime.refreshAll()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        instances = emptyList(),
                        isLoading = false,
                        errorMessage = "No pudimos leer los Widgets instalados.",
                    )
                }
            }
        }
    }

    class Factory(
        private val context: Context,
        private val preferences: WidgetPreferencesStore,
        private val runtime: WidgetRuntime,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WidgetViewModel::class.java))
            val appContext = context.applicationContext
            return WidgetViewModel(
                appWidgetManager = AppWidgetManager.getInstance(appContext),
                provider = ComponentName(appContext, NextEventAppWidgetProvider::class.java),
                preferences = preferences,
                runtime = runtime,
            ) as T
        }
    }
}
