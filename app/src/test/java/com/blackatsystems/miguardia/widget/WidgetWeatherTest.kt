package com.blackatsystems.miguardia.widget

import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.weather.WeatherCondition
import com.blackatsystems.miguardia.core.domain.weather.WeatherForecast
import com.blackatsystems.miguardia.core.domain.weather.WeatherHour
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.weather.WeatherPreferences
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetWeatherTest {
    @Test
    fun `fresh complete cache is eligible without a network result`() {
        assertNotNull(
            widgetWeatherTextFromCache(
                shift = shift(),
                global = enabledPreferences(),
                forecast = forecast(hourCount = 12, fetchedAt = NOW.minusSeconds(1_800)),
                now = NOW,
            ),
        )
    }

    @Test
    fun `stale expired partial and absent caches never render`() {
        val shift = shift()
        val global = enabledPreferences()

        assertNull(widgetWeatherTextFromCache(shift, global, forecast(12, NOW.minusSeconds(3_601)), NOW))
        assertNull(widgetWeatherTextFromCache(shift, global, forecast(12, NOW.minusSeconds(21_601)), NOW))
        assertNull(widgetWeatherTextFromCache(shift, global, forecast(2, NOW.minusSeconds(1_800)), NOW))
        assertNull(widgetWeatherTextFromCache(shift, global, null, NOW))
    }

    @Test
    fun `global opt in and provider explanation are both mandatory`() {
        val shift = shift()
        val forecast = forecast(12, NOW.minusSeconds(1_800))

        assertNull(
            widgetWeatherTextFromCache(
                shift,
                enabledPreferences().copy(enabled = false),
                forecast,
                NOW,
            ),
        )
        assertNull(
            widgetWeatherTextFromCache(
                shift,
                enabledPreferences().copy(providerExplanationAccepted = false),
                forecast,
                NOW,
            ),
        )
    }

    private fun shift() = NextEventItem.Shift(
        shiftId = UUID.fromString("93000000-0000-0000-0000-000000000099"),
        start = START,
        end = END,
        zoneId = ZONE,
        ownerLocalDate = START.atZone(ZONE).toLocalDate(),
        sector = WorkSector.PRIVATE_SECURITY,
        workTypeNameSnapshot = "Jornada ficticia",
        workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
        placeNameSnapshot = "Lugar ficticio",
        placeAbbreviationSnapshot = "LF",
        startTimeSnapshot = START.atZone(ZONE).toLocalTime(),
        endTimeSnapshot = END.atZone(ZONE).toLocalTime(),
        colorArgbSnapshot = 0xff336699.toInt(),
        positionSnapshot = null,
        hasHistoricalAddress = false,
    )

    private fun enabledPreferences() = WeatherPreferences(
        enabled = true,
        providerExplanationAccepted = true,
    )

    private fun forecast(hourCount: Int, fetchedAt: Instant): WeatherForecast {
        val hours = (0 until hourCount).map { offset ->
            val from = START.plusSeconds(offset * 3_600L)
            WeatherHour(
                validFrom = from,
                validUntilExclusive = from.plusSeconds(3_600),
                temperatureCelsius = 12.0 + offset,
                apparentTemperatureCelsius = 11.0 + offset,
                precipitationMillimeters = 0.0,
                precipitationProbabilityPercent = 0,
                weatherCode = 0,
                condition = WeatherCondition.CLEAR,
                windSpeedKmh = 8.0,
                windGustKmh = 12.0,
                windDirectionDegrees = 180.0,
            )
        }
        return WeatherForecast(
            providerId = "open-meteo",
            location = WeatherLocation("test", "Córdoba ficticia", -31.4, -64.1, ZONE),
            fetchedAt = fetchedAt,
            coverageStart = hours.first().validFrom,
            coverageEndExclusive = hours.last().validUntilExclusive,
            hours = hours,
        )
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val NOW: Instant = Instant.parse("2026-08-29T15:00:00Z")
        val START: Instant = Instant.parse("2026-08-29T22:00:00Z")
        val END: Instant = Instant.parse("2026-08-30T10:00:00Z")
    }
}
