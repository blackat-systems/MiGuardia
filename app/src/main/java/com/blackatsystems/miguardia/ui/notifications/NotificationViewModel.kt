package com.blackatsystems.miguardia.ui.notifications

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.repository.ShiftNotificationConfigRepository
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationAttentionMode
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.notifications.NotificationRhythm
import com.blackatsystems.miguardia.notifications.NotificationRuntime
import com.blackatsystems.miguardia.notifications.NotificationSystemAccess
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class NotificationViewModel(
    private val preferencesStore: NotificationPreferencesStore,
    private val configs: ShiftNotificationConfigRepository,
    private val systemAccess: NotificationSystemAccess,
    private val runtime: NotificationRuntime,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationUiState(systemAccess = systemAccess.read()))
    val uiState: StateFlow<NotificationUiState> = _uiState
    private val writeMutex = Mutex()
    private var preferencesJob: Job? = null
    private var shiftJob: Job? = null
    private var restorableEventsJob: Job? = null

    init {
        observePreferences()
        observeRestorableEvents()
    }

    private fun observeRestorableEvents() {
        restorableEventsJob?.cancel()
        restorableEventsJob = viewModelScope.launch {
            runtime.restorableEvents
                .catch { showError("No pudimos leer las notificaciones ocultas.") }
                .collect { events -> _uiState.update { it.copy(restorableEvents = events) } }
        }
    }

    private fun observePreferences() {
        preferencesJob?.cancel()
        preferencesJob = viewModelScope.launch {
            preferencesStore.preferences
                .catch { showError("No pudimos leer la configuración de avisos.") }
                .collect { preferences ->
                    _uiState.update {
                        it.copy(preferences = preferences, isLoading = false, errorMessage = null)
                    }
                }
        }
    }

    fun openGlobal() {
        shiftJob?.cancel()
        _uiState.update {
            it.copy(
                surface = NotificationSurface.GLOBAL,
                selectedShift = null,
                shiftOverride = null,
                systemAccess = systemAccess.read(),
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun openShift(shift: Shift) {
        shiftJob?.cancel()
        _uiState.update {
            it.copy(
                surface = NotificationSurface.SHIFT,
                selectedShift = shift,
                shiftOverride = null,
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
        }
        shiftJob = viewModelScope.launch {
            configs.observeForShift(shift.id)
                .catch { showError("No pudimos leer los avisos de esta jornada.") }
                .collect { config ->
                    _uiState.update { it.copy(shiftOverride = config, isLoading = false) }
                }
        }
    }

    fun close() {
        shiftJob?.cancel()
        _uiState.update {
            it.copy(
                surface = NotificationSurface.NONE,
                selectedShift = null,
                shiftOverride = null,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun refreshSystemAccess() {
        _uiState.update { it.copy(systemAccess = systemAccess.read()) }
        runtime.reconcile()
    }
    fun setEnabled(value: Boolean) = launchWrite { preferencesStore.setEnabled(value) }
    fun setPreciseTiming(value: Boolean) = launchWrite { preferencesStore.setPreciseTiming(value) }
    fun setPersistent(value: Boolean) = launchWrite { preferencesStore.setPersistentWhileActive(value) }
    fun setPrivacy(value: NotificationPrivacy) = launchWrite { preferencesStore.setPrivacy(value) }
    fun setAttentionMode(value: NotificationAttentionMode) = launchWrite {
        preferencesStore.setAttentionMode(value)
    }
    fun applyRhythm(value: NotificationRhythm) = launchWrite { preferencesStore.applyRhythm(value) }
    fun setSound(uri: Uri?) = launchWrite { preferencesStore.setSoundUri(uri) }
    fun setGlobalReminders(values: Collection<Long>) = launchWrite {
        preferencesStore.setGlobalReminderLeadMinutes(values)
    }

    fun setShiftReminders(values: Collection<Long>) {
        val shift = _uiState.value.selectedShift ?: return
        launchWrite { configs.replace(ShiftNotificationConfig(shift.id, values.toList())) }
    }

    fun disableShift() = setShiftReminders(emptyList())

    fun useGlobalForShift() {
        val shift = _uiState.value.selectedShift ?: return
        launchWrite { configs.clear(shift.id) }
    }

    fun restoreNotification(eventKey: String) = launchOperation {
        if (runtime.restoreNow(eventKey)) {
            "Notificación mostrada nuevamente."
        } else {
            "El evento ya no podía mostrar esa notificación."
        }
    }

    fun restoreAllNotifications() {
        val eventKeys = _uiState.value.restorableEvents.map { event -> event.identity.trackingKey }
        if (eventKeys.isEmpty()) return
        launchOperation {
            val restored = eventKeys.count { runtime.restoreNow(it) }
            when (restored) {
                0 -> "Los eventos ya no podían mostrar esas notificaciones."
                1 -> "Se mostró nuevamente una notificación."
                else -> "Se mostraron nuevamente $restored notificaciones."
            }
        }
    }

    fun sendTestNotification() = launchOperation {
        if (!systemAccess.read().notificationAccessGranted) {
            return@launchOperation "Primero permití las notificaciones en Android."
        }
        runtime.showTestNotification(preferencesStore.current())
        "Prueba enviada. Se elimina sola en un minuto."
    }

    fun clearMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observePreferences()
        observeRestorableEvents()
        _uiState.value.selectedShift?.let(::openShift)
        refreshSystemAccess()
    }

    private fun launchWrite(block: suspend () -> Unit) = launchOperation {
        block()
        "Configuración guardada."
    }

    private fun launchOperation(block: suspend () -> String) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            if (!writeMutex.tryLock()) return@launch
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val message = block()
                _uiState.update { it.copy(infoMessage = message) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(error.message ?: "No pudimos guardar la configuración.")
            } finally {
                _uiState.update { it.copy(isSaving = false) }
                writeMutex.unlock()
            }
        }
    }

    private fun showError(message: String) = _uiState.update {
        it.copy(errorMessage = message, isLoading = false, isSaving = false)
    }

    class Factory(
        private val preferencesStore: NotificationPreferencesStore,
        private val configs: ShiftNotificationConfigRepository,
        private val systemAccess: NotificationSystemAccess,
        private val runtime: NotificationRuntime,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NotificationViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return NotificationViewModel(preferencesStore, configs, systemAccess, runtime) as T
        }
    }
}
