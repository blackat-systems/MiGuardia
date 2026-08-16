package com.blackatsystems.miguardia.weather

import android.content.Context
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.weather.ShiftWeatherSummary
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import com.blackatsystems.miguardia.core.domain.weather.WeatherRefreshResult
import com.blackatsystems.miguardia.core.domain.weather.WeatherRepository
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.core.domain.weather.roundedTemperature
import com.blackatsystems.miguardia.core.domain.weather.spanishLabel
import com.blackatsystems.miguardia.core.domain.weather.summarizeShiftWeather
import com.blackatsystems.miguardia.core.domain.weather.weatherFreshness
import java.time.Clock
import java.time.Instant

class WeatherRuntime(
    context: Context,
    val preferences: WeatherPreferencesStore,
    clock: Clock = Clock.system(AppDefaults.zoneId()),
) {
    val location = WeatherLocation(
        id = AppDefaults.WEATHER_LOCATION_ID,
        displayName = AppDefaults.WEATHER_LOCATION_NAME,
        latitude = AppDefaults.WEATHER_LATITUDE,
        longitude = AppDefaults.WEATHER_LONGITUDE,
        zoneId = AppDefaults.zoneId(),
    )
    private val repositoryImpl = DefaultWeatherRepository(
        location = location,
        client = OpenMeteoForecastClient(clock),
        cache = WeatherCacheStore.inFilesDir(context.applicationContext.filesDir),
        preferences = preferences,
        clock = clock,
    )
    val repository: WeatherRepository get() = repositoryImpl
    val clock: Clock = clock

    suspend fun refreshIfEnabled(force: Boolean = false): WeatherRefreshResult? {
        if (!preferences.current().enabled) return null
        return repository.refreshIfStale(force)
    }

    suspend fun notificationTextFromCache(shift: Shift, now: Instant): String? {
        val config = preferences.current()
        if (!config.enabled || !config.includeInNotifications || !config.providerExplanationAccepted) return null
        val forecast = repository.latest() ?: return null
        if (weatherFreshness(forecast.fetchedAt, now) != WeatherFreshness.FRESH) return null
        val summary = summarizeShiftWeather(shift.startAt, shift.endAt, forecast)
        if (summary.coverage != WeatherCoverage.COMPLETE) return null
        return formatWeatherForNotification(summary, config.unitSystem)
    }

    suspend fun refreshNotificationText(shift: Shift): String? {
        val config = preferences.current()
        if (!config.enabled || !config.includeInNotifications || !config.providerExplanationAccepted) return null
        repository.refreshIfStale(force = false)
        return notificationTextFromCache(shift, clock.instant())
    }

    fun cancelRefresh() = repositoryImpl.cancelActiveRefresh()
}

internal fun formatWeatherForNotification(
    summary: ShiftWeatherSummary,
    unit: WeatherUnitSystem,
): String? {
    if (summary.coverage != WeatherCoverage.COMPLETE) return null
    val pieces = mutableListOf<String>()
    summary.condition?.let { pieces += it.spanishLabel() }
    val minimum = summary.minimumTemperatureCelsius
    val maximum = summary.maximumTemperatureCelsius
    if (minimum != null && maximum != null) {
        val suffix = if (unit == WeatherUnitSystem.CELSIUS) "°C" else "°F"
        pieces += "${roundedTemperature(minimum, unit)}–${roundedTemperature(maximum, unit)} $suffix"
    }
    summary.maximumPrecipitationProbabilityPercent?.let { pieces += "lluvia $it %" }
    return pieces.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Clima: ", separator = " · ")
}
