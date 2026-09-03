package com.blackatsystems.miguardia.ui.weather

import com.blackatsystems.miguardia.core.domain.weather.ShiftWeatherSummary
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherUiStateTest {
    @Test
    fun `invalidating one objective keeps briefs that belong to other objectives`() {
        val firstShift = UUID(0L, 1L)
        val secondShift = UUID(0L, 2L)
        val firstObjective = UUID(0L, 11L)
        val secondObjective = UUID(0L, 12L)
        val firstBrief = brief(firstObjective)
        val secondBrief = brief(secondObjective)
        val briefs = mapOf(firstShift to firstBrief, secondShift to secondBrief)

        assertEquals(
            mapOf(secondShift to secondBrief),
            retainBriefsOutsideInvalidatedObjective(briefs, firstObjective),
        )
        assertEquals(emptyMap<UUID, ShiftWeatherBrief>(), retainBriefsOutsideInvalidatedObjective(briefs, null))
    }

    private fun brief(objectiveId: UUID) = ShiftWeatherBrief(
        summary = ShiftWeatherSummary(
            shiftStart = START,
            shiftEndExclusive = END,
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
        ),
        freshness = WeatherFreshness.FRESH,
        objectiveId = objectiveId,
    )

    private companion object {
        val START: Instant = Instant.parse("2026-09-03T08:00:00Z")
        val END: Instant = Instant.parse("2026-09-03T16:00:00Z")
    }
}
