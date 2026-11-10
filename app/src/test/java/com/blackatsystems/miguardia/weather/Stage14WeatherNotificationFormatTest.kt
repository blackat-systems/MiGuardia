package com.blackatsystems.miguardia.weather

import com.blackatsystems.miguardia.core.domain.weather.ShiftWeatherSummary
import com.blackatsystems.miguardia.core.domain.weather.WeatherCondition
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Stage 14 · Clima: the text a complete notification may carry. Synthetic data, no Android. */
class Stage14WeatherNotificationFormatTest {
    private val start = Instant.parse("2026-09-03T08:00:00Z")
    private val end = Instant.parse("2026-09-03T16:00:00Z")

    // A236
    @Test
    fun `complete summary gives condition range and rain in the chosen unit`() {
        val summary = summary(WeatherCoverage.COMPLETE)

        assertEquals(
            "Clima: Lluvia · 10–17 °C · lluvia 70 %",
            formatWeatherForNotification(summary, WeatherUnitSystem.CELSIUS),
        )
        assertEquals(
            "Clima: Lluvia · 50–63 °F · lluvia 70 %",
            formatWeatherForNotification(summary, WeatherUnitSystem.FAHRENHEIT),
        )
    }

    // A236
    @Test
    fun `partial or missing coverage never produces notification text`() {
        assertNull(formatWeatherForNotification(summary(WeatherCoverage.PARTIAL), WeatherUnitSystem.CELSIUS))
        assertNull(formatWeatherForNotification(summary(WeatherCoverage.NONE), WeatherUnitSystem.CELSIUS))
    }

    // A236, A243
    @Test
    fun `a missing value is left out instead of being invented`() {
        val withoutMinimum = summary(WeatherCoverage.COMPLETE).copy(minimumTemperatureCelsius = null)
        val withoutRain = summary(WeatherCoverage.COMPLETE).copy(maximumPrecipitationProbabilityPercent = null)
        val empty = summary(WeatherCoverage.COMPLETE).copy(
            condition = null,
            minimumTemperatureCelsius = null,
            maximumTemperatureCelsius = null,
            maximumPrecipitationProbabilityPercent = null,
        )

        assertEquals(
            "Clima: Lluvia · lluvia 70 %",
            formatWeatherForNotification(withoutMinimum, WeatherUnitSystem.CELSIUS),
        )
        assertEquals(
            "Clima: Lluvia · 10–17 °C",
            formatWeatherForNotification(withoutRain, WeatherUnitSystem.CELSIUS),
        )
        assertNull(formatWeatherForNotification(empty, WeatherUnitSystem.CELSIUS))
    }

    private fun summary(coverage: WeatherCoverage) = ShiftWeatherSummary(
        shiftStart = start,
        shiftEndExclusive = end,
        coveredFrom = start,
        coveredUntilExclusive = end,
        coverage = coverage,
        condition = WeatherCondition.RAIN,
        minimumTemperatureCelsius = 10.0,
        maximumTemperatureCelsius = 17.0,
        minimumApparentTemperatureCelsius = 9.0,
        maximumApparentTemperatureCelsius = 16.0,
        maximumPrecipitationProbabilityPercent = 70,
        precipitationMillimeters = 4.5,
        maximumWindSpeedKmh = 30.0,
        maximumWindGustKmh = 45.0,
    )
}
