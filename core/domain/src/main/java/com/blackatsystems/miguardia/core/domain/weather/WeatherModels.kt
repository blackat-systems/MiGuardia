package com.blackatsystems.miguardia.core.domain.weather

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class WeatherLocation(
    val id: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: ZoneId,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}
enum class WeatherUnitSystem { CELSIUS, FAHRENHEIT }

enum class WeatherCondition {
    CLEAR,
    CLOUDY,
    FOG,
    SHOWERS,
    RAIN,
    HEAVY_RAIN,
    SNOW,
    FREEZING_PRECIPITATION,
    THUNDERSTORM,
    UNKNOWN,
}

data class WeatherHour(
    val validFrom: Instant,
    val validUntilExclusive: Instant,
    val temperatureCelsius: Double?,
    val apparentTemperatureCelsius: Double?,
    val precipitationMillimeters: Double?,
    val precipitationProbabilityPercent: Int?,
    val weatherCode: Int?,
    val condition: WeatherCondition,
    val windSpeedKmh: Double?,
    val windGustKmh: Double?,
    val windDirectionDegrees: Double?,
) {
    init {
        require(validUntilExclusive > validFrom)
        temperatureCelsius?.let {
            it.requireFinite("temperature")
            require(it in -100.0..80.0)
        }
        apparentTemperatureCelsius?.let {
            it.requireFinite("apparent temperature")
            require(it in -120.0..100.0)
        }
        precipitationMillimeters?.let {
            it.requireFinite("precipitation")
            require(it in 0.0..1000.0)
        }
        precipitationProbabilityPercent?.let { require(it in 0..100) }
        windSpeedKmh?.let {
            it.requireFinite("wind speed")
            require(it in 0.0..500.0)
        }
        windGustKmh?.let {
            it.requireFinite("wind gust")
            require(it in 0.0..500.0)
        }
        windDirectionDegrees?.let {
            it.requireFinite("wind direction")
            require(it in 0.0..360.0)
        }
    }
}

data class WeatherForecast(
    val providerId: String,
    val location: WeatherLocation,
    val fetchedAt: Instant,
    val coverageStart: Instant,
    val coverageEndExclusive: Instant,
    val hours: List<WeatherHour>,
) {
    init {
        require(providerId.isNotBlank())
        require(coverageEndExclusive > coverageStart)
        require(hours.isNotEmpty())
        require(hours.zipWithNext().all { (left, right) -> left.validFrom < right.validFrom })
        require(hours.first().validFrom == coverageStart)
        require(hours.last().validUntilExclusive == coverageEndExclusive)
    }
}

enum class WeatherFreshness { FRESH, STALE, EXPIRED }

enum class WeatherCoverage { COMPLETE, PARTIAL, NONE }

data class ShiftWeatherSummary(
    val shiftStart: Instant,
    val shiftEndExclusive: Instant,
    val coveredFrom: Instant?,
    val coveredUntilExclusive: Instant?,
    val coverage: WeatherCoverage,
    val condition: WeatherCondition?,
    val minimumTemperatureCelsius: Double?,
    val maximumTemperatureCelsius: Double?,
    val minimumApparentTemperatureCelsius: Double?,
    val maximumApparentTemperatureCelsius: Double?,
    val maximumPrecipitationProbabilityPercent: Int?,
    val precipitationMillimeters: Double?,
    val maximumWindSpeedKmh: Double?,
    val maximumWindGustKmh: Double?,
)

enum class WeatherFailureKind {
    OFFLINE_OR_TIMEOUT,
    RATE_LIMITED,
    CLIENT_ERROR,
    SERVER_ERROR,
    INVALID_RESPONSE,
    CACHE_ERROR,
    UNKNOWN,
}

data class WeatherFailure(
    val kind: WeatherFailureKind,
    val retryAfter: Duration? = null,
)

sealed interface WeatherRefreshResult {
    data class Success(val forecast: WeatherForecast, val downloaded: Boolean) : WeatherRefreshResult
    data class Failure(val error: WeatherFailure, val cachedForecast: WeatherForecast?) : WeatherRefreshResult
}

interface WeatherRepository {
    suspend fun latest(): WeatherForecast?
    suspend fun refreshIfStale(force: Boolean = false): WeatherRefreshResult
    suspend fun clearCache()
}

private fun Double.requireFinite(label: String) {
    require(isFinite()) { "$label must be finite" }
}
