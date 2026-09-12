package com.blackatsystems.miguardia.core.domain.weather

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherRulesTest {
    private val location = WeatherLocation("cordoba-capital", "Córdoba", -31.4201, -64.1888, ZoneId.of("America/Argentina/Cordoba"))

    @Test
    fun `day shift with all hours has complete coverage`() {
        val forecast = forecast(hours("2026-08-16T10:00:00Z", 8))
        val summary = summarizeShiftWeather(instant("2026-08-16T10:00:00Z"), instant("2026-08-16T18:00:00Z"), forecast)
        assertEquals(WeatherCoverage.COMPLETE, summary.coverage)
        assertEquals(10.0, summary.minimumTemperatureCelsius!!, 0.0)
        assertEquals(17.0, summary.maximumTemperatureCelsius!!, 0.0)
    }

    @Test
    fun `night shift crosses midnight without splitting`() {
        val forecast = forecast(hours("2026-08-16T22:00:00Z", 10))
        val summary = summarizeShiftWeather(instant("2026-08-16T22:00:00Z"), instant("2026-08-17T08:00:00Z"), forecast)
        assertEquals(WeatherCoverage.COMPLETE, summary.coverage)
        assertEquals(10.0, summary.precipitationMillimeters!!, 0.0001)
    }

    @Test
    fun `fractional boundaries weight precipitation`() {
        val forecast = forecast(hours("2026-08-16T19:00:00Z", 13, precipitation = 2.0))
        val summary = summarizeShiftWeather(instant("2026-08-16T19:30:00Z"), instant("2026-08-17T07:00:00Z"), forecast)
        assertEquals(WeatherCoverage.COMPLETE, summary.coverage)
        assertEquals(23.0, summary.precipitationMillimeters!!, 0.0001)
    }

    @Test
    fun `touching an exclusive boundary is not coverage`() {
        val forecast = forecast(hours("2026-08-16T10:00:00Z", 1))
        val summary = summarizeShiftWeather(instant("2026-08-16T11:00:00Z"), instant("2026-08-16T12:00:00Z"), forecast)
        assertEquals(WeatherCoverage.NONE, summary.coverage)
        assertNull(summary.condition)
    }

    @Test
    fun `gap stays partial and is never interpolated`() {
        val source = hours("2026-08-16T10:00:00Z", 4).let { listOf(it[0], it[1], it[3]) }
        val summary = summarizeShiftWeather(instant("2026-08-16T10:00:00Z"), instant("2026-08-16T14:00:00Z"), forecast(source))
        assertEquals(WeatherCoverage.PARTIAL, summary.coverage)
    }

    @Test
    fun `priority chooses storm independently of source order`() {
        val source = hours("2026-08-16T10:00:00Z", 3).mapIndexed { index, hour ->
            when (index) {
                0 -> hour.copy(condition = WeatherCondition.CLEAR)
                1 -> hour.copy(condition = WeatherCondition.THUNDERSTORM)
                else -> hour.copy(condition = WeatherCondition.RAIN)
            }
        }
        assertEquals(
            WeatherCondition.THUNDERSTORM,
            summarizeShiftWeather(source.first().validFrom, source.last().validUntilExclusive, forecast(source)).condition,
        )
    }

    @Test
    fun `missing optional measurements remain absent`() {
        val source = hours("2026-08-16T10:00:00Z", 1).map {
            it.copy(temperatureCelsius = null, apparentTemperatureCelsius = null, windGustKmh = null)
        }
        val summary = summarizeShiftWeather(source.first().validFrom, source.first().validUntilExclusive, forecast(source))
        assertNull(summary.minimumTemperatureCelsius)
        assertNull(summary.minimumApparentTemperatureCelsius)
        assertNull(summary.maximumWindGustKmh)
    }

    @Test
    fun `freshness boundaries and future clock are explicit`() {
        val fetched = instant("2026-08-16T10:00:00Z")
        assertEquals(WeatherFreshness.FRESH, weatherFreshness(fetched, fetched.plusSeconds(3600)))
        assertEquals(WeatherFreshness.STALE, weatherFreshness(fetched, fetched.plusSeconds(3601)))
        assertEquals(WeatherFreshness.STALE, weatherFreshness(fetched, fetched.plusSeconds(6 * 3600)))
        assertEquals(WeatherFreshness.EXPIRED, weatherFreshness(fetched, fetched.plusSeconds(6 * 3600 + 1)))
        assertEquals(WeatherFreshness.EXPIRED, weatherFreshness(fetched, fetched.minusSeconds(1)))
    }

    @Test
    fun `known and unknown WMO codes are stable`() {
        assertEquals(WeatherCondition.CLEAR, wmoWeatherCondition(0))
        assertEquals(WeatherCondition.THUNDERSTORM, wmoWeatherCondition(99))
        assertEquals(WeatherCondition.UNKNOWN, wmoWeatherCondition(999))
        assertEquals(WeatherCondition.UNKNOWN, wmoWeatherCondition(null))
    }

    @Test
    fun `fahrenheit conversion rounds only for presentation`() {
        assertEquals(32.0, convertTemperature(0.0, WeatherUnitSystem.FAHRENHEIT), 0.0)
        assertEquals(68, roundedTemperature(20.0, WeatherUnitSystem.FAHRENHEIT))
        assertEquals(20, roundedTemperature(20.4, WeatherUnitSystem.CELSIUS))
    }

    private fun forecast(hours: List<WeatherHour>) = WeatherForecast(
        providerId = "open-meteo",
        location = location,
        fetchedAt = instant("2026-08-16T09:00:00Z"),
        coverageStart = hours.first().validFrom,
        coverageEndExclusive = hours.last().validUntilExclusive,
        hours = hours,
    )

    private fun hours(start: String, count: Int, precipitation: Double = 1.0): List<WeatherHour> {
        val first = instant(start)
        return (0 until count).map { offset ->
            val from = first.plusSeconds(offset * 3600L)
            WeatherHour(
                validFrom = from,
                validUntilExclusive = from.plusSeconds(3600),
                temperatureCelsius = 10.0 + offset,
                apparentTemperatureCelsius = 9.0 + offset,
                precipitationMillimeters = precipitation,
                precipitationProbabilityPercent = offset.coerceAtMost(100),
                weatherCode = 0,
                condition = WeatherCondition.CLEAR,
                windSpeedKmh = 10.0 + offset,
                windGustKmh = 20.0 + offset,
                windDirectionDegrees = 180.0,
            )
        }
    }

    private fun instant(value: String): Instant = Instant.parse(value)
}
