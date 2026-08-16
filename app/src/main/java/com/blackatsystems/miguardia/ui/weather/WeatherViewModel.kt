package com.blackatsystems.miguardia.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blackatsystems.miguardia.core.domain.nextevent.isEligibleUpcomingWork
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
    private var briefJob: Job? = null
    private var selectedShiftId: UUID? = null

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
    }

    fun openGlobal() {
        selectedShiftId = null
        _uiState.update {
            it.copy(
                surface = WeatherSurface.GLOBAL,
                selectedShift = null,
                shiftSummary = null,
                shiftHours = emptyList(),
                ineligibleReason = null,
                errorMessage = null,
                infoMessage = null,
            )
        }
        reloadCache()
    }

    fun openShift(shiftId: UUID) {
        selectedShiftId = shiftId
        _uiState.update {
            it.copy(
                surface = WeatherSurface.SHIFT,
                selectedShift = null,
                shiftSummary = null,
                shiftHours = emptyList(),
                ineligibleReason = null,
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val eligible = revalidateSelectedShift()
            if (eligible && _uiState.value.preferences.enabled) refresh(force = false) else reloadCache()
        }
    }

    fun close() {
        loadJob?.cancel()
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
        briefJob?.cancel()
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
                val forecast = when (val refresh = runtime.refreshIfEnabled(force = false)) {
                    is WeatherRefreshResult.Success -> refresh.forecast
                    is WeatherRefreshResult.Failure -> refresh.cachedForecast ?: runtime.repository.latest()
                    null -> runtime.repository.latest()
                }
                if (forecast == null) {
                    _uiState.update { it.copy(shiftBriefs = emptyMap(), loadingBriefIds = emptySet()) }
                    return@launch
                }
                val freshness = weatherFreshness(forecast.fetchedAt, runtime.clock.instant())
                val briefs = buildMap {
                    shiftIds.forEach { shiftId ->
                        val shift = shifts.getById(shiftId) ?: return@forEach
                        val applicableVacations = vacations
                            .observeOverlapping(shift.localStartDate, shift.localStartDate)
                            .first()
                        if (!shift.isEligibleUpcomingWork(runtime.clock.instant(), applicableVacations)) return@forEach
                        val summary = summarizeShiftWeather(shift.startAt, shift.endAt, forecast)
                        if (summary.coverage != WeatherCoverage.NONE) {
                            put(shiftId, ShiftWeatherBrief(summary, freshness))
                        }
                    }
                }
                _uiState.update { it.copy(shiftBriefs = briefs, loadingBriefIds = emptySet()) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(loadingBriefIds = emptySet()) }
            }
        }
    }

    fun clearBriefs() {
        briefJob?.cancel()
        _uiState.update { it.copy(shiftBriefs = emptyMap(), loadingBriefIds = emptySet()) }
    }

    fun enableAfterExplanation() = launchPreferenceWrite {
        runtime.preferences.enableAfterExplanation()
        refresh(force = false)
    }

    fun setEnabled(enabled: Boolean) = launchPreferenceWrite {
        runtime.preferences.setEnabled(enabled)
        if (enabled) refresh(force = false) else runtime.cancelRefresh()
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
        if (_uiState.value.preferences.enabled) refresh(force = false)
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                runtime.repository.clearCache()
                _uiState.update {
                    it.copy(
                        forecast = null,
                        freshness = null,
                        shiftSummary = null,
                        shiftHours = emptyList(),
                        infoMessage = "Caché meteorológico borrado.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showError("No pudimos borrar el caché meteorológico.")
            }
        }
    }

    fun externalLinkFailed() = showError("No hay una aplicación compatible para abrir el enlace.")
    fun clearMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    private fun refresh(force: Boolean) {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                if (!revalidateSelectedShift()) return@launch
                when (val result = runtime.refreshIfEnabled(force)) {
                    is WeatherRefreshResult.Success -> {
                        revalidateSelectedShift()
                        applyForecast(result.forecast)
                        if (result.downloaded) {
                            _uiState.update { it.copy(infoMessage = "Pronóstico actualizado.") }
                        }
                    }
                    is WeatherRefreshResult.Failure -> {
                        revalidateSelectedShift()
                        result.cachedForecast?.let(::applyForecast)
                        showError(result.error.kind.userMessage())
                    }
                    null -> Unit
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showError("No pudimos actualizar el pronóstico.")
            } finally {
                _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
            }
        }
    }

    private fun reloadCache() {
        viewModelScope.launch {
            try {
                revalidateSelectedShift()
                val forecast = runtime.repository.latest()
                if (forecast == null) {
                    _uiState.update {
                        it.copy(forecast = null, freshness = null, shiftSummary = null, shiftHours = emptyList(), isLoading = false)
                    }
                } else {
                    applyForecast(forecast)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showError("El caché meteorológico no se puede leer.")
            }
        }
    }

    private fun applyForecast(forecast: com.blackatsystems.miguardia.core.domain.weather.WeatherForecast) {
        val shift = _uiState.value.selectedShift.takeIf { _uiState.value.ineligibleReason == null }
        val summary = shift?.let { summarizeShiftWeather(it.startAt, it.endAt, forecast) }
        val hours = shift?.let {
            forecast.hours.filter { hour -> hour.validFrom < it.endAt && hour.validUntilExclusive > it.startAt }
        }.orEmpty()
        _uiState.update {
            it.copy(
                forecast = forecast,
                freshness = weatherFreshness(forecast.fetchedAt, runtime.clock.instant()),
                shiftSummary = summary,
                shiftHours = hours,
                isLoading = false,
            )
        }
    }

    private suspend fun revalidateSelectedShift(): Boolean {
        if (_uiState.value.surface != WeatherSurface.SHIFT) return true
        val id = selectedShiftId ?: return false
        val shift = shifts.getById(id)
        if (shift == null) {
            _uiState.update {
                it.copy(
                    selectedShift = null,
                    shiftSummary = null,
                    shiftHours = emptyList(),
                    ineligibleReason = "La guardia ya no está disponible.",
                    isLoading = false,
                )
            }
            return false
        }
        val applicableVacations = vacations.observeOverlapping(shift.localStartDate, shift.localStartDate).first()
        val eligible = shift.isEligibleUpcomingWork(runtime.clock.instant(), applicableVacations)
        _uiState.update {
            it.copy(
                selectedShift = shift,
                shiftSummary = if (eligible) it.shiftSummary else null,
                shiftHours = if (eligible) it.shiftHours else emptyList(),
                ineligibleReason = if (eligible) null else "El pronóstico operativo ya no aplica a esta guardia.",
                isLoading = false,
            )
        }
        return eligible
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
