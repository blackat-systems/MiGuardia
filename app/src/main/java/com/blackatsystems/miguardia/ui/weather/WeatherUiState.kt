package com.blackatsystems.miguardia.ui.weather

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.weather.ShiftWeatherSummary
import com.blackatsystems.miguardia.core.domain.weather.WeatherForecast
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.WeatherHour
import com.blackatsystems.miguardia.weather.WeatherPreferences
import java.util.UUID

enum class WeatherSurface { NONE, GLOBAL, SHIFT }

data class ShiftWeatherBrief(
    val summary: ShiftWeatherSummary,
    val freshness: WeatherFreshness,
)

data class WeatherUiState(
    val surface: WeatherSurface = WeatherSurface.NONE,
    val preferences: WeatherPreferences = WeatherPreferences(),
    val forecast: WeatherForecast? = null,
    val freshness: WeatherFreshness? = null,
    val selectedShift: Shift? = null,
    val shiftSummary: ShiftWeatherSummary? = null,
    val shiftHours: List<WeatherHour> = emptyList(),
    val shiftBriefs: Map<UUID, ShiftWeatherBrief> = emptyMap(),
    val loadingBriefIds: Set<UUID> = emptySet(),
    val ineligibleReason: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)
