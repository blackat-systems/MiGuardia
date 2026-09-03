package com.blackatsystems.miguardia.weather

import android.content.Context
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.weather.ShiftWeatherSummary
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherForecast
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import com.blackatsystems.miguardia.core.domain.weather.WeatherRefreshResult
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.core.domain.weather.roundedTemperature
import com.blackatsystems.miguardia.core.domain.weather.spanishLabel
import com.blackatsystems.miguardia.core.domain.weather.summarizeShiftWeather
import com.blackatsystems.miguardia.core.domain.weather.weatherFreshness
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WeatherRuntime(
    context: Context,
    val preferences: WeatherPreferencesStore,
    private val objectives: ObjectiveRepository,
    clock: Clock = Clock.systemUTC(),
) {
    private val filesDir = context.applicationContext.filesDir
    private val client = OpenMeteoForecastClient(clock)
    private val repositories = ConcurrentHashMap<String, DefaultWeatherRepository>()
    private val cacheLifecycleMutex = Mutex()
    private val mutableCacheInvalidations = MutableSharedFlow<UUID?>(extraBufferCapacity = 1)
    val cacheInvalidations: SharedFlow<UUID?> = mutableCacheInvalidations.asSharedFlow()
    val clock: Clock = clock

    suspend fun refreshIfEnabled(shift: Shift, force: Boolean = false): WeatherRefreshResult? {
        if (!preferences.current().enabled) return null
        return cacheLifecycleMutex.withLock {
            repositoryFor(shift)?.refreshIfStale(force)
        }
    }

    suspend fun latestForShift(shift: Shift): WeatherForecast? = cacheLifecycleMutex.withLock {
        repositoryFor(shift)?.latest()
    }

    suspend fun hasLocationFor(shift: Shift): Boolean = objectiveLocationFor(shift) != null

    suspend fun forecastMatchesCurrentLocation(shift: Shift, forecast: WeatherForecast): Boolean =
        objectiveLocationFor(shift) == forecast.location

    suspend fun clearCache() {
        cancelRefresh()
        cacheLifecycleMutex.withLock {
            repositories.values.forEach { repository -> repository.clearCache() }
            repositories.clear()
            WeatherCacheStore.clearAll(filesDir)
        }
        mutableCacheInvalidations.emit(null)
    }

    suspend fun clearCacheForObjective(objectiveId: UUID) {
        val id = objectiveId.toString()
        repositories.entries
            .filter { (key, _) -> key.startsWith("$id|") }
            .forEach { (_, repository) -> repository.cancelActiveRefresh() }
        cacheLifecycleMutex.withLock {
            val matchingKeys = repositories.keys.filter { it.startsWith("$id|") }
            matchingKeys.forEach { key -> repositories[key]?.clearCache() }
            matchingKeys.forEach(repositories::remove)
            WeatherCacheStore.clearObjective(filesDir, id)
        }
        mutableCacheInvalidations.emit(objectiveId)
    }

    suspend fun notificationTextFromCache(shift: Shift, now: Instant): String? {
        val config = preferences.current()
        if (!config.enabled || !config.includeInNotifications || !config.providerExplanationAccepted) return null
        val forecast = latestForShift(shift) ?: return null
        if (weatherFreshness(forecast.fetchedAt, now) != WeatherFreshness.FRESH) return null
        val summary = summarizeShiftWeather(shift.startAt, shift.endAt, forecast)
        if (summary.coverage != WeatherCoverage.COMPLETE) return null
        return formatWeatherForNotification(summary, config.unitSystem)
    }

    suspend fun refreshNotificationText(shift: Shift): String? {
        val config = preferences.current()
        if (!config.enabled || !config.includeInNotifications || !config.providerExplanationAccepted) return null
        refreshIfEnabled(shift, force = false)
        return notificationTextFromCache(shift, clock.instant())
    }

    fun cancelRefresh() = repositories.values.forEach(DefaultWeatherRepository::cancelActiveRefresh)

    private suspend fun repositoryFor(shift: Shift): DefaultWeatherRepository? {
        val location = objectiveLocationFor(shift) ?: return null
        val key = weatherRepositoryKey(location)
        return repositories.computeIfAbsent(key) {
            DefaultWeatherRepository(
                location = location,
                client = client,
                cache = WeatherCacheStore.forLocation(filesDir, location),
                preferences = preferences,
                clock = clock,
            )
        }
    }

    private suspend fun objectiveLocationFor(shift: Shift): WeatherLocation? {
        val objective = objectives.getById(shift.sourceObjectiveId) ?: return null
        val latitude = objective.weatherLatitude ?: return null
        val longitude = objective.weatherLongitude ?: return null
        return WeatherLocation(
            id = objective.id.toString(),
            displayName = objective.fullName,
            latitude = latitude,
            longitude = longitude,
            zoneId = shift.zoneId,
        )
    }
}

internal fun weatherRepositoryKey(location: WeatherLocation): String = listOf(
    location.id,
    location.displayName,
    location.latitude.toBits().toString(),
    location.longitude.toBits().toString(),
    location.zoneId.id,
).joinToString("|")

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
