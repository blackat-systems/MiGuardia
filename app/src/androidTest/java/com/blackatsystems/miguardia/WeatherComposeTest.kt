package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.weather.ShiftWeatherSummary
import com.blackatsystems.miguardia.core.domain.weather.WeatherCondition
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherForecast
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.WeatherHour
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.weather.WeatherActions
import com.blackatsystems.miguardia.ui.weather.WeatherSurface
import com.blackatsystems.miguardia.ui.weather.WeatherSurfaceHost
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.weather.WeatherPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WeatherComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun drawerGroupsWeatherWithNotificationsAndOffersAppearance() {
        compose.setContent {
            MaterialTheme {
                MiGuardiaApp(
                    calendarState = CalendarUiState(
                        visibleMonth = YearMonth.of(2026, 8),
                        referenceInstant = NOW,
                        loadState = CalendarLoadState.CONTENT,
                    ),
                    onPreviousMonth = {}, onNextMonth = {}, onToday = {}, onSelectDate = {}, onDismissDate = {}, onRetry = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Notificaciones").performScrollTo().assertExists()
        compose.onNodeWithText("Clima").performScrollTo().assertExists()
        compose.onNodeWithText("Apariencia").performScrollTo().assertExists()
    }

    @Test
    fun disabledWeatherRequiresConsciousExplanation() {
        var enabled = 0
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    WeatherUiState(surface = WeatherSurface.GLOBAL, isLoading = false),
                    WeatherActions(enableAfterExplanation = { enabled++ }),
                )
            }
        }
        compose.onNodeWithText("Open-Meteo recibe sólo la coordenada fija de Córdoba y la IP habitual de conexión. No se envían guardias, objetivos, direcciones ni datos del teléfono.").assertExists()
        compose.onNodeWithText("Entiendo y habilitar Clima").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, enabled) }
    }

    @Test
    fun successfulRefreshConfirmationIsVisibleInsideTheWeatherDialog() {
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    WeatherUiState(
                        surface = WeatherSurface.GLOBAL,
                        preferences = WeatherPreferences(
                            enabled = true,
                            providerExplanationAccepted = true,
                        ),
                        isLoading = false,
                        infoMessage = "Pronóstico actualizado.",
                    ),
                    WeatherActions(),
                )
            }
        }

        compose.onNodeWithText("Pronóstico actualizado.").assertIsDisplayed()
        compose.onNodeWithText("Clima").assertIsDisplayed()
    }

    @Test
    fun shiftEntryAlsoRequiresTheFullFirstActivationExplanation() {
        var enabled = 0
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    WeatherUiState(
                        surface = WeatherSurface.SHIFT,
                        selectedShift = shift(),
                        isLoading = false,
                    ),
                    WeatherActions(enableAfterExplanation = { enabled++ }),
                )
            }
        }

        compose.onNodeWithText("Proveedor y privacidad").assertExists()
        compose.onNodeWithText("Entiendo y habilitar Clima").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, enabled) }
    }

    @Test
    fun shiftDetailShowsCompleteOvernightSummaryHoursAndAttribution() {
        val forecast = forecast()
        val shift = shift()
        val summary = ShiftWeatherSummary(
            shiftStart = shift.startAt,
            shiftEndExclusive = shift.endAt,
            coveredFrom = shift.startAt,
            coveredUntilExclusive = shift.endAt,
            coverage = WeatherCoverage.COMPLETE,
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
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    WeatherUiState(
                        surface = WeatherSurface.SHIFT,
                        preferences = WeatherPreferences(enabled = true, providerExplanationAccepted = true),
                        forecast = forecast,
                        freshness = WeatherFreshness.FRESH,
                        selectedShift = shift,
                        shiftSummary = summary,
                        shiftHours = forecast.hours,
                        isLoading = false,
                    ),
                    WeatherActions(),
                )
            }
        }
        compose.onNodeWithText("Cobertura completa").assertExists()
        compose.onNodeWithText("Temperatura: 10–17 °C").assertExists()
        compose.onNodeWithText("Deslizá hacia la derecha.").assertExists()
        compose.onNodeWithTag("weather-hourly-carousel").assertExists()
        compose.onNodeWithText("17/08 00:00").performScrollTo().assertExists()
        compose.onNodeWithText("Datos meteorológicos: Open-Meteo").performScrollTo().assertExists()
    }

    private fun forecast(): WeatherForecast {
        val hours = listOf(
            hour(Instant.parse("2026-08-17T02:00:00Z")),
            hour(Instant.parse("2026-08-17T03:00:00Z")),
        )
        return WeatherForecast("open-meteo", LOCATION, NOW, hours.first().validFrom, hours.last().validUntilExclusive, hours)
    }

    private fun hour(from: Instant) = WeatherHour(
        from, from.plusSeconds(3600), 12.0, 11.0, 0.2, 50, 61, WeatherCondition.RAIN, 20.0, 30.0, 180.0,
    )

    private fun shift() = Shift(
        id = UUID.fromString("00000000-0000-0000-0000-000000000601"),
        startAt = Instant.parse("2026-08-16T22:00:00Z"),
        endAt = Instant.parse("2026-08-17T10:00:00Z"),
        zoneId = LOCATION.zoneId,
        localStartDate = LocalDate.of(2026, 8, 16),
        objectiveNameSnapshot = "Objetivo ficticio",
        objectiveAbbreviationSnapshot = "QA",
        objectiveAddressSnapshot = null,
        startTimeSnapshot = LocalTime.of(19, 0),
        endTimeSnapshot = LocalTime.of(7, 0),
        colorArgbSnapshot = 0xff336699.toInt(),
        position = null,
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = null,
        sourceScheduleCombinationId = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-16T20:00:00Z")
        val LOCATION = WeatherLocation(
            "cordoba-capital", "Córdoba Capital, Argentina", -31.4201, -64.1888, ZoneId.of("America/Argentina/Cordoba"),
        )
    }
}
