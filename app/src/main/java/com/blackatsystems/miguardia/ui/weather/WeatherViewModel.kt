package com.blackatsystems.miguardia.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blackatsystems.miguardia.core.domain.nextevent.isEligibleForWeather
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.weather.WeatherFailureKind
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.WeatherRefreshResult
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.core.domain.weather.summarizeShiftWeather
import com.blackatsystems.miguardia.core.domain.weather.weatherFreshness
import com.blackatsystems.miguardia.weather.WeatherRuntime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WeatherViewModel(
    private val runtime: WeatherRuntime,
    private val shifts: ShiftRepository,
    private val vacations: VacationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState
    private var loadJob: Job? = null
    private var refreshJob: Job? = null
    private var cacheJob: Job? = null
    private var clearCacheJob: Job? = null
    private var briefJob: Job? = null
    private var briefLoadGeneration = 0L
    private var selectedShiftId: UUID? = null
    private var requestedBriefIds: Set<UUID> = emptySet()

    init {
        viewModelScope.launch {
            runtime.preferences.preferences
                .catch { showError("No pudimos leer la configuración de Clima.") }
                .collect { preferences ->
                    val wasEnabled = _uiState.value.preferences.enabled
                    if (!preferences.enabled) {
                        runtime.cancelRefresh()
                        clearBriefs()
                    }
                    _uiState.update { it.copy(preferences = preferences, isLoading = false) }
                    if (preferences.enabled && !wasEnabled) refresh(force = false)
                }
        }
        viewModelScope.launch {
            runtime.cacheInvalidations.collect { objectiveId ->
                briefLoadGeneration += 1
                briefJob?.cancel()
                briefJob = null
                _uiState.update { state ->
                    val selectedInvalidated = objectiveId == null ||
                        state.selectedShift?.sourceObjectiveId == objectiveId
                    state.copy(
                        shiftBriefs = retainBriefsOutsideInvalidatedObjective(state.shiftBriefs, objectiveId),
                        loadingBriefIds = emptySet(),
                        forecast = if (selectedInvalidated) null else state.forecast,
                        freshness = if (selectedInvalidated) null else state.freshness,
                        shiftSummary = if (selectedInvalidated) null else state.shiftSummary,
                        shiftHours = if (selectedInvalidated) emptyList() else state.shiftHours,
                    )
                }
                val pendingReload = requestedBriefIds
                if (
                    objectiveId != null &&
                    pendingReload.isNotEmpty() &&
                    _uiState.value.preferences.enabled
                ) {
                    loadBriefs(pendingReload)
                }
            }
        }
    }

    fun openGlobal() {
        cancelDetailJobs()
        selectedShiftId = null
        _uiState.update {
            it.copy(
                surface = WeatherSurface.GLOBAL,
                selectedShift = null,
                weatherLocationName = null,
                forecast = null,
                freshness = null,
                shiftSummary = null,
                shiftHours = emptyList(),
                ineligibleReason = null,
                errorMessage = null,
                infoMessage = null,
                isLoading = false,
                isRefreshing = false,
            )
        }
    }

    fun openShift(shiftId: UUID) {
        cancelDetailJobs()
        selectedShiftId = shiftId
        _uiState.update {
            it.copy(
                surface = WeatherSurface.SHIFT,
                selectedShift = null,
                weatherLocationName = null,
                forecast = null,
                freshness = null,
                shiftSummary = null,
                shiftHours = emptyList(),
                ineligibleReason = null,
                isLoading = true,
                isRefreshing = false,
                errorMessage = null,
                infoMessage = null,
            )
        }
        loadJob = viewModelScope.launch {
            val eligible = revalidateSelectedShift(shiftId)
            if (eligible && _uiState.value.preferences.enabled) refresh(force = false) else reloadCache()
        }
    }

    fun close() {
        cancelDetailJobs()
        selectedShiftId = null
        _uiState.update {
            WeatherUiState(
                preferences = it.preferences,
                shiftBriefs = it.shiftBriefs,
                loadingBriefIds = it.loadingBriefIds,
                isLoading = false,
            )
        }
    }

    fun loadBriefs(shiftIds: Set<UUID>) {
        briefLoadGeneration += 1
        val loadGeneration = briefLoadGeneration
        briefJob?.cancel()
        requestedBriefIds = shiftIds
        if (shiftIds.isEmpty() || !_uiState.value.preferences.enabled) {
            clearBriefs()
            return
        }
        _uiState.update {
            it.copy(
                shiftBriefs = it.shiftBriefs.filterKeys(shiftIds::contains),
                loadingBriefIds = shiftIds,
            )
        }
        briefJob = viewModelScope.launch {
            try {
                val briefs = buildMap {
                    shiftIds.forEach { shiftId ->
                        val shift = shifts.getById(shiftId) ?: return@forEach
                        val applicableVacations = vacations
                            .observeOverlapping(shift.localStartDate, shift.localStartDate)
                            .first()
                        if (!shift.isEligibleForWeather(runtime.clock.instant(), applicableVacations)) return@forEach
                        val forecast = when (val refresh = runtime.refreshIfEnabled(shift, force = false)) {
                            is WeatherRefreshResult.Success -> refresh.forecast
                            is WeatherRefreshResult.Failure ->
                                refresh.cachedForecast ?: runtime.latestForShift(shift)
                            null -> runtime.latestForShift(shift)
                        } ?: return@forEach
                        if (!runtime.forecastMatchesCurrentLocation(shift, forecast)) return@forEach
                        val freshness = weatherFreshness(forecast.fetchedAt, runtime.clock.instant())
                        val summary = summarizeShiftWeather(shift.startAt, shift.endAt, forecast)
                        if (summary.coverage != WeatherCoverage.NONE) {
                            put(shiftId, ShiftWeatherBrief(summary, freshness, shift.sourceObjectiveId))
                        }
                    }
                }
                if (loadGeneration == briefLoadGeneration) {
                    _uiState.update { it.copy(shiftBriefs = briefs, loadingBriefIds = emptySet()) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (loadGeneration == briefLoadGeneration) {
                    _uiState.update { it.copy(loadingBriefIds = emptySet()) }
                }
            }
        }
    }

    fun clearBriefs() {
        briefLoadGeneration += 1
        briefJob?.cancel()
        briefJob = null
        requestedBriefIds = emptySet()
        _uiState.update { it.copy(shiftBriefs = emptyMap(), loadingBriefIds = emptySet()) }
    }

    fun enableAfterExplanation() = launchPreferenceWrite {
        runtime.preferences.enableAfterExplanation()
        if (_uiState.value.surface == WeatherSurface.SHIFT) refresh(force = false)
    }

    fun setEnabled(enabled: Boolean) = launchPreferenceWrite {
        runtime.preferences.setEnabled(enabled)
        if (enabled && _uiState.value.surface == WeatherSurface.SHIFT) refresh(force = false) else if (!enabled) runtime.cancelRefresh()
    }

    fun setUnitSystem(unit: WeatherUnitSystem) = launchPreferenceWrite {
        runtime.preferences.setUnitSystem(unit)
        reloadCache()
    }

    fun setIncludeInNotifications(include: Boolean) = launchPreferenceWrite {
        runtime.preferences.setIncludeInNotifications(include)
    }

    fun manualRefresh() = refresh(force = true)

    fun onResume() {
        if (_uiState.value.preferences.enabled && _uiState.value.surface == WeatherSurface.SHIFT) refresh(force = false)
    }

    fun clearCache() {
        cancelDetailJobs()
        clearCacheJob?.cancel()
        _uiState.update { it.copy(isLoading = false, isRefreshing = false, errorMessage = null) }
        clearCacheJob = viewModelScope.launch {
            try {
                runtime.clearCache()
                clearBriefs()
                _uiState.update {
                    it.copy(
                        forecast = null,
                        freshness = null,
                        shiftSummary = null,
                        shiftHours = emptyList(),
                        infoMessage = "Pronósticos guardados borrados.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showError("No pudimos borrar los pronósticos guardados.")
            }
        }
    }

    fun externalLinkFailed() = showError("No hay una aplicación compatible para abrir el enlace.")
    fun clearMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    private fun refresh(force: Boolean) {
        if (_uiState.value.surface != WeatherSurface.SHIFT) return
        val expectedShiftId = selectedShiftId ?: return
        if (refreshJob?.isActive == true) return
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        refreshJob = viewModelScope.launch {
            try {
                if (!revalidateSelectedShift(expectedShiftId)) return@launch
                val shift = _uiState.value.selectedShift
                    ?.takeIf { it.id == expectedShiftId }
                    ?: return@launch
                when (val result = runtime.refreshIfEnabled(shift, force)) {
                    is WeatherRefreshResult.Success -> {
                        if (!revalidateSelectedShift(expectedShiftId)) return@launch
                        val applied = applyForecast(result.forecast, expectedShiftId)
                        if (applied && result.downloaded && isCurrentShift(expectedShiftId)) {
                            _uiState.update { it.copy(infoMessage = "Pronóstico actualizado.") }
                        }
                    }
                    is WeatherRefreshResult.Failure -> {
                        if (!revalidateSelectedShift(expectedShiftId)) return@launch
                        result.cachedForecast?.let { applyForecast(it, expectedShiftId) }
                        showErrorForShift(expectedShiftId, result.error.kind.userMessage())
                    }
                    null -> Unit
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showErrorForShift(expectedShiftId, "No pudimos actualizar el pronóstico.")
            } finally {
                if (isCurrentShift(expectedShiftId)) {
                    _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
                }
            }
        }
    }

    private fun reloadCache() {
        if (_uiState.value.surface != WeatherSurface.SHIFT) return
        val expectedShiftId = selectedShiftId ?: return
        cacheJob?.cancel()
        cacheJob = viewModelScope.launch {
            try {
                val valid = revalidateSelectedShift(expectedShiftId)
                val shift = _uiState.value.selectedShift?.takeIf { it.id == expectedShiftId }
                val forecast = if (valid && shift != null) runtime.latestForShift(shift) else null
                if (!isCurrentShift(expectedShiftId)) return@launch
                if (forecast == null) {
                    _uiState.update {
                        it.copy(forecast = null, freshness = null, shiftSummary = null, shiftHours = emptyList(), isLoading = false)
                    }
                } else {
                    applyForecast(forecast, expectedShiftId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showErrorForShift(expectedShiftId, "No pudimos leer el pronóstico guardado.")
            }
        }
    }

    private suspend fun applyForecast(
        forecast: com.blackatsystems.miguardia.core.domain.weather.WeatherForecast,
        expectedShiftId: UUID,
    ): Boolean {
        if (!isCurrentShift(expectedShiftId)) return false
        val currentState = _uiState.value
        val shift = currentState.selectedShift?.takeIf {
            it.id == expectedShiftId &&
                currentState.ineligibleReason == null &&
                forecast.location.id == it.sourceObjectiveId.toString()
        } ?: return false
        if (!runtime.forecastMatchesCurrentLocation(shift, forecast)) return false
        val summary = summarizeShiftWeather(shift.startAt, shift.endAt, forecast)
        val hours = forecast.hours.filter { hour ->
            hour.validFrom < shift.endAt && hour.validUntilExclusive > shift.startAt
        }
        _uiState.update {
            if (!isCurrentShift(expectedShiftId) || it.selectedShift?.id != expectedShiftId) {
                it
            } else {
                it.copy(
                    forecast = forecast,
                    weatherLocationName = forecast.location.displayName,
                    freshness = weatherFreshness(forecast.fetchedAt, runtime.clock.instant()),
                    shiftSummary = summary,
                    shiftHours = hours,
                    isLoading = false,
                )
            }
        }
        return isCurrentShift(expectedShiftId) && _uiState.value.forecast == forecast
    }

    private suspend fun revalidateSelectedShift(expectedShiftId: UUID): Boolean {
        if (_uiState.value.surface != WeatherSurface.SHIFT) return true
        if (!isCurrentShift(expectedShiftId)) return false
        val shift = shifts.getById(expectedShiftId)
        if (!isCurrentShift(expectedShiftId)) return false
        if (shift == null) {
            _uiState.update {
                if (!isCurrentShift(expectedShiftId)) it else {
                    it.copy(
                        selectedShift = null,
                        weatherLocationName = null,
                        forecast = null,
                        freshness = null,
                        shiftSummary = null,
                        shiftHours = emptyList(),
                        ineligibleReason = "La jornada ya no está disponible.",
                        isLoading = false,
                    )
                }
            }
            return false
        }
        val applicableVacations = vacations.observeOverlapping(shift.localStartDate, shift.localStartDate).first()
        if (!isCurrentShift(expectedShiftId)) return false
        val eligible = shift.isEligibleForWeather(runtime.clock.instant(), applicableVacations)
        val hasLocation = runtime.hasLocationFor(shift)
        if (!isCurrentShift(expectedShiftId)) return false
        val usable = eligible && hasLocation
        _uiState.update {
            if (!isCurrentShift(expectedShiftId)) it else {
                it.copy(
                    selectedShift = shift,
                    weatherLocationName = if (hasLocation) shift.objectiveNameSnapshot else null,
                    forecast = if (usable) it.forecast else null,
                    freshness = if (usable) it.freshness else null,
                    shiftSummary = if (usable) it.shiftSummary else null,
                    shiftHours = if (usable) it.shiftHours else emptyList(),
                    ineligibleReason = when {
                        !eligible -> "El pronóstico ya no aplica a esta jornada."
                        !hasLocation -> "Este objetivo todavía no tiene ubicación para el clima. Agregá una dirección o usá tu ciudad actual desde Mi forma de trabajar > Opciones avanzadas."
                        else -> null
                    },
                    isLoading = false,
                )
            }
        }
        return usable
    }

    private fun isCurrentShift(expectedShiftId: UUID): Boolean =
        selectedShiftId == expectedShiftId && _uiState.value.surface == WeatherSurface.SHIFT

    private fun cancelDetailJobs() {
        loadJob?.cancel()
        refreshJob?.cancel()
        cacheJob?.cancel()
        loadJob = null
        refreshJob = null
        cacheJob = null
    }

    private fun showErrorForShift(expectedShiftId: UUID, message: String) {
        if (isCurrentShift(expectedShiftId)) showError(message)
    }

    private fun launchPreferenceWrite(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showError("No pudimos guardar la configuración de Clima.")
            }
        }
    }

    private fun showError(message: String) = _uiState.update { it.copy(errorMessage = message, isLoading = false) }

    class Factory(
        private val runtime: WeatherRuntime,
        private val shifts: ShiftRepository,
        private val vacations: VacationRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WeatherViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(runtime, shifts, vacations) as T
        }
    }
}

private fun WeatherFailureKind.userMessage(): String = when (this) {
    WeatherFailureKind.OFFLINE_OR_TIMEOUT -> "No pudimos conectar. Se conserva el último pronóstico disponible."
    WeatherFailureKind.RATE_LIMITED -> "Open-Meteo pidió esperar antes de volver a consultar."
    WeatherFailureKind.CLIENT_ERROR -> "El proveedor rechazó la solicitud meteorológica."
    WeatherFailureKind.SERVER_ERROR -> "El proveedor meteorológico no está disponible temporalmente."
    WeatherFailureKind.INVALID_RESPONSE -> "El proveedor devolvió un pronóstico que MiGuardia no puede validar."
    WeatherFailureKind.CACHE_ERROR -> "El pronóstico llegó, pero no se pudo guardar de forma segura."
    WeatherFailureKind.UNKNOWN -> "No pudimos actualizar el pronóstico."
}
