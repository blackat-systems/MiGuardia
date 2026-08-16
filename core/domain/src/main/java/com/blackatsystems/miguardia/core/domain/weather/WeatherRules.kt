package com.blackatsystems.miguardia.core.domain.weather

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

private val FreshLimit: Duration = Duration.ofMinutes(60)
private val StaleLimit: Duration = Duration.ofHours(6)

fun weatherFreshness(fetchedAt: Instant, now: Instant): WeatherFreshness {
    val age = Duration.between(fetchedAt, now)
    if (age.isNegative) return WeatherFreshness.EXPIRED
    return when {
        age <= FreshLimit -> WeatherFreshness.FRESH
        age <= StaleLimit -> WeatherFreshness.STALE
        else -> WeatherFreshness.EXPIRED
    }
}

fun wmoWeatherCondition(code: Int?): WeatherCondition = when (code) {
    0, 1 -> WeatherCondition.CLEAR
    2, 3 -> WeatherCondition.CLOUDY
    45, 48 -> WeatherCondition.FOG
    51, 53, 55, 80, 81 -> WeatherCondition.SHOWERS
    56, 57, 66, 67 -> WeatherCondition.FREEZING_PRECIPITATION
    61, 63 -> WeatherCondition.RAIN
    65, 82 -> WeatherCondition.HEAVY_RAIN
    71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
    95, 96, 99 -> WeatherCondition.THUNDERSTORM
    else -> WeatherCondition.UNKNOWN
}

fun summarizeShiftWeather(
    shiftStart: Instant,
    shiftEndExclusive: Instant,
    forecast: WeatherForecast,
): ShiftWeatherSummary {
    require(shiftEndExclusive > shiftStart)
    val overlaps = forecast.hours.mapNotNull { hour ->
        val overlapStart = maxOf(shiftStart, hour.validFrom)
        val overlapEnd = minOf(shiftEndExclusive, hour.validUntilExclusive)
        if (overlapEnd <= overlapStart) null else Overlap(hour, overlapStart, overlapEnd)
    }
    if (overlaps.isEmpty()) {
        return ShiftWeatherSummary(
            shiftStart = shiftStart,
            shiftEndExclusive = shiftEndExclusive,
            coveredFrom = null,
            coveredUntilExclusive = null,
            coverage = WeatherCoverage.NONE,
            condition = null,
            minimumTemperatureCelsius = null,
            maximumTemperatureCelsius = null,
            minimumApparentTemperatureCelsius = null,
            maximumApparentTemperatureCelsius = null,
            maximumPrecipitationProbabilityPercent = null,
            precipitationMillimeters = null,
            maximumWindSpeedKmh = null,
            maximumWindGustKmh = null,
        )
    }
    val merged = overlaps.sortedBy(Overlap::start).fold(mutableListOf<Interval>()) { acc, overlap ->
        val last = acc.lastOrNull()
        if (last != null && overlap.start <= last.end) {
            last.end = maxOf(last.end, overlap.end)
        } else {
            acc += Interval(overlap.start, overlap.end)
        }
        acc
    }
    val fullCoverage = merged.size == 1 && merged.first().start <= shiftStart && merged.first().end >= shiftEndExclusive
    val temperatures = overlaps.mapNotNull { it.hour.temperatureCelsius }
    val apparent = overlaps.mapNotNull { it.hour.apparentTemperatureCelsius }
    return ShiftWeatherSummary(
        shiftStart = shiftStart,
        shiftEndExclusive = shiftEndExclusive,
        coveredFrom = merged.first().start,
        coveredUntilExclusive = merged.last().end,
        coverage = if (fullCoverage) WeatherCoverage.COMPLETE else WeatherCoverage.PARTIAL,
        condition = overlaps.map(Overlap::hour).maxByOrNull { conditionPriority(it.condition) }?.condition,
        minimumTemperatureCelsius = temperatures.minOrNull(),
        maximumTemperatureCelsius = temperatures.maxOrNull(),
        minimumApparentTemperatureCelsius = apparent.minOrNull(),
        maximumApparentTemperatureCelsius = apparent.maxOrNull(),
        maximumPrecipitationProbabilityPercent = overlaps.mapNotNull { it.hour.precipitationProbabilityPercent }.maxOrNull(),
        precipitationMillimeters = overlaps.mapNotNull { overlap ->
            overlap.hour.precipitationMillimeters?.let { value ->
                val hourDuration = Duration.between(overlap.hour.validFrom, overlap.hour.validUntilExclusive)
                val overlapDuration = Duration.between(overlap.start, overlap.end)
                value * overlapDuration.toMillis().toDouble() / hourDuration.toMillis().toDouble()
            }
        }.takeIf(List<Double>::isNotEmpty)?.sum(),
        maximumWindSpeedKmh = overlaps.mapNotNull { it.hour.windSpeedKmh }.maxOrNull(),
        maximumWindGustKmh = overlaps.mapNotNull { it.hour.windGustKmh }.maxOrNull(),
    )
}

fun convertTemperature(valueCelsius: Double, unit: WeatherUnitSystem): Double = when (unit) {
    WeatherUnitSystem.CELSIUS -> valueCelsius
    WeatherUnitSystem.FAHRENHEIT -> valueCelsius * 9.0 / 5.0 + 32.0
}

fun roundedTemperature(valueCelsius: Double, unit: WeatherUnitSystem): Int =
    convertTemperature(valueCelsius, unit).roundToInt()

fun WeatherCondition.spanishLabel(): String = when (this) {
    WeatherCondition.CLEAR -> "Despejado"
    WeatherCondition.CLOUDY -> "Nublado"
    WeatherCondition.FOG -> "Niebla"
    WeatherCondition.SHOWERS -> "Lloviznas o chaparrones"
    WeatherCondition.RAIN -> "Lluvia"
    WeatherCondition.HEAVY_RAIN -> "Lluvia fuerte"
    WeatherCondition.SNOW -> "Nieve"
    WeatherCondition.FREEZING_PRECIPITATION -> "Precipitación helada"
    WeatherCondition.THUNDERSTORM -> "Tormenta"
    WeatherCondition.UNKNOWN -> "Condición desconocida"
}

private fun conditionPriority(condition: WeatherCondition): Int = when (condition) {
    WeatherCondition.THUNDERSTORM -> 9
    WeatherCondition.FREEZING_PRECIPITATION -> 8
    WeatherCondition.SNOW -> 7
    WeatherCondition.HEAVY_RAIN -> 6
    WeatherCondition.RAIN -> 5
    WeatherCondition.SHOWERS -> 4
    WeatherCondition.FOG -> 3
    WeatherCondition.CLOUDY -> 2
    WeatherCondition.CLEAR -> 1
    WeatherCondition.UNKNOWN -> 0
}

private data class Overlap(val hour: WeatherHour, val start: Instant, val end: Instant)
private data class Interval(val start: Instant, var end: Instant)
