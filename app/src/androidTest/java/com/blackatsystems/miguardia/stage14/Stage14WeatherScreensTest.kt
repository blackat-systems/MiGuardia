package com.blackatsystems.miguardia.stage14

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.calendar.CalendarShift
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
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
import com.blackatsystems.miguardia.core.domain.weather.summarizeShiftWeather
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.DayDetailSheet
import com.blackatsystems.miguardia.ui.ShiftWeatherBriefCard
import com.blackatsystems.miguardia.ui.weather.ShiftWeatherBrief
import com.blackatsystems.miguardia.ui.weather.WeatherActions
import com.blackatsystems.miguardia.ui.weather.WeatherSurface
import com.blackatsystems.miguardia.ui.weather.WeatherSurfaceHost
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.weather.WeatherPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Stage 14 · Clima on screen. Fictitious state and counted actions only: no ViewModel, no
 * preferences, no cache, no network and nothing written to disk.
 */
class Stage14WeatherScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(Dispatchers.Main.immediate)

    // ---- Shift detail: cache age, coverage and window ----

    // A242, C084
    @Test
    fun shiftDetailLabelsFreshOldAndExpiredForecastsAndTheAbsenceOfData() {
        val forecast = forecast()
        var state by mutableStateOf(detailState(forecast, WeatherFreshness.FRESH))
        compose.setContent { MaterialTheme { WeatherSurfaceHost(state, WeatherActions()) } }

        compose.onNodeWithText("$LAST_UPDATE · fresco.").performScrollTo().assertIsDisplayed()

        compose.runOnIdle { state = state.copy(freshness = WeatherFreshness.STALE) }
        compose.onNodeWithText("$LAST_UPDATE · desactualizado, todavía visible.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("$LAST_UPDATE · fresco.").assertDoesNotExist()
        compose.onNodeWithText("Actualizar").performScrollTo().assertIsEnabled()

        compose.runOnIdle { state = state.copy(freshness = WeatherFreshness.EXPIRED) }
        compose.onNodeWithText("$LAST_UPDATE · vencido; no se usa como vigente.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("$LAST_UPDATE · desactualizado, todavía visible.").assertDoesNotExist()
        compose.onNodeWithText("Actualizar").performScrollTo().assertIsEnabled()

        compose.runOnIdle {
            state = state.copy(forecast = null, freshness = null, shiftSummary = null, shiftHours = emptyList())
        }
        compose.onNodeWithText("Sin datos meteorológicos guardados.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Todavía no hay un pronóstico disponible para esta jornada.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Resumen de toda la jornada").assertDoesNotExist()
        compose.onNodeWithTag("weather-hourly-carousel").assertDoesNotExist()
    }

    // A242
    @Test
    fun shiftDetailDeclaresPartialCoverageWithoutFillingMissingHours() {
        val shift = shift()
        val forecast = forecast()
        val summary = summarizeShiftWeather(shift.startAt, shift.endAt, forecast)
        assertEquals(WeatherCoverage.PARTIAL, summary.coverage)
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    detailState(forecast, WeatherFreshness.FRESH).copy(shiftSummary = summary),
                    WeatherActions(),
                )
            }
        }

        compose.onNodeWithText("Cobertura parcial: no se rellenan horas faltantes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cobertura completa").assertDoesNotExist()
        // Only the two hours the provider gave are drawn.
        compose.onNodeWithTag("weather-hourly-carousel").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("16/08 23:00").assertExists()
        compose.onNodeWithText("17/08 00:00").assertExists()
        compose.onNodeWithText("17/08 01:00").assertDoesNotExist()
    }

    // A242
    @Test
    fun shiftDetailSaysWhenTheSavedForecastDoesNotIncludeTheShiftHours() {
        val shift = shift()
        val later = forecast(listOf(hour(Instant.parse("2026-08-18T02:00:00Z"))))
        val summary = summarizeShiftWeather(shift.startAt, shift.endAt, later)
        assertEquals(WeatherCoverage.NONE, summary.coverage)
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    detailState(later, WeatherFreshness.FRESH).copy(shiftSummary = summary, shiftHours = emptyList()),
                    WeatherActions(),
                )
            }
        }

        compose.onNodeWithText("El pronóstico guardado no incluye las horas de esta jornada.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin horas disponibles para esta jornada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("weather-hourly-carousel").assertDoesNotExist()
        compose.onNodeWithText("Clima durante la jornada").assertDoesNotExist()
        compose.onNodeWithText("Actualizar").performScrollTo().assertIsEnabled()
    }

    // A242, C084
    @Test
    fun shiftOutsideTheForecastWindowExplainsItAndOffersNoRefresh() {
        var state by mutableStateOf(
            WeatherUiState(
                surface = WeatherSurface.SHIFT,
                preferences = ENABLED,
                selectedShift = shift(),
                weatherLocationName = "Objetivo ficticio",
                ineligibleReason = "El pronóstico ya no aplica a esta jornada.",
                isLoading = false,
            ),
        )
        var refreshes = 0
        compose.setContent {
            MaterialTheme { WeatherSurfaceHost(state, WeatherActions(refresh = { refreshes++ })) }
        }

        compose.onNodeWithText("Clima no disponible para esta jornada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("El pronóstico ya no aplica a esta jornada.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Actualizar").assertDoesNotExist()
        compose.onNodeWithText("Resumen de toda la jornada").assertDoesNotExist()
        compose.onNodeWithText("Todavía no hay un pronóstico disponible para esta jornada.").assertDoesNotExist()
        compose.onNodeWithTag("weather-provider-details").performScrollTo().assertIsDisplayed()

        // The shift disappeared while the detail was open.
        compose.runOnIdle {
            state = state.copy(selectedShift = null, ineligibleReason = "La jornada ya no está disponible.")
        }
        compose.onNodeWithText("La jornada ya no está disponible.").assertIsDisplayed()
        compose.onNodeWithText("Actualizar").assertDoesNotExist()
        compose.onNodeWithTag("weather-close").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, refreshes) }
    }

    // ---- Day detail: the weather card of a shift ----

    // A242
    @Test
    fun dayCardSaysAnEndedShiftIsOutsideTheForecastWindowFromItsExactEnd() {
        val shift = shift()
        var reference by mutableStateOf(shift.endAt.minusSeconds(1))
        val opened = mutableListOf<UUID>()
        val weather = WeatherUiState(
            preferences = ENABLED,
            shiftBriefs = mapOf(shift.id to ShiftWeatherBrief(completeSummary(), WeatherFreshness.FRESH, OBJECTIVE_ID)),
            isLoading = false,
        )
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = false) {
                DayDetailSheet(
                    day = CalendarDay(
                        shift.localStartDate,
                        listOf(CalendarShift(shift, ShiftTemporalStatus.IN_PROGRESS)),
                        null,
                        false,
                    ),
                    referenceInstant = reference,
                    onEditDay = null,
                    onOpenNotes = {},
                    onOpenNotifications = {},
                    onOpenWeather = { opened += it },
                    weatherState = weather,
                )
            }
        }

        // One second before the end the forecast still applies.
        compose.onNodeWithText(BRIEF_FRESH).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(OUTSIDE_WINDOW).assertDoesNotExist()

        compose.runOnIdle { reference = shift.endAt }
        compose.onNodeWithText(OUTSIDE_WINDOW).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(BRIEF_FRESH).assertDoesNotExist()
        compose.onNodeWithText("Lluvia · 10–17 °C").assertDoesNotExist()

        // The card still opens the detail, where the reason is explained.
        compose.onNodeWithText(OUTSIDE_WINDOW).performClick()
        compose.runOnIdle { assertEquals(listOf(shift.id), opened) }
    }

    // A242, A243, C084
    @Test
    fun dayCardLabelsOldExpiredAndPartialForecastsAndOmitsMissingValues() {
        var brief by mutableStateOf<ShiftWeatherBrief?>(
            ShiftWeatherBrief(completeSummary(), WeatherFreshness.STALE, OBJECTIVE_ID),
        )
        var loading by mutableStateOf(false)
        var unit by mutableStateOf(WeatherUnitSystem.CELSIUS)
        var opens = 0
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ShiftWeatherBriefCard(true, loading, brief, unit) { opens++ }
                }
            }
        }

        compose.onNodeWithText("Pronóstico antiguo · tocá para actualizar o ver el detalle").assertIsDisplayed()
        compose.onNodeWithText(BRIEF_FRESH).assertDoesNotExist()
        compose.onNodeWithText("Lluvia · 10–17 °C").assertIsDisplayed()
        compose.onNodeWithText("Probabilidad máxima de lluvia: 70 %").assertIsDisplayed()
        compose.onNodeWithText("Cobertura completa del horario").assertIsDisplayed()
        compose.onNodeWithText("Pronóstico antiguo · tocá para actualizar o ver el detalle").performClick()
        compose.runOnIdle { assertEquals(1, opens) }

        // A refresh in progress never hides the last forecast.
        compose.runOnIdle { loading = true }
        compose.onNodeWithText("Actualizando sin ocultar el último pronóstico…").assertIsDisplayed()
        compose.onNodeWithText("Lluvia · 10–17 °C").assertIsDisplayed()

        compose.runOnIdle {
            loading = false
            brief = ShiftWeatherBrief(completeSummary(), WeatherFreshness.EXPIRED, OBJECTIVE_ID)
            unit = WeatherUnitSystem.FAHRENHEIT
        }
        compose.onNodeWithText("Pronóstico vencido · tocá para intentar actualizar").assertIsDisplayed()
        compose.onNodeWithText("Pronóstico antiguo · tocá para actualizar o ver el detalle").assertDoesNotExist()
        compose.onNodeWithText("Lluvia · 50–63 °F").assertIsDisplayed()

        val shift = shift()
        compose.runOnIdle {
            brief = ShiftWeatherBrief(
                summarizeShiftWeather(shift.startAt, shift.endAt, forecast()),
                WeatherFreshness.FRESH,
                OBJECTIVE_ID,
            )
        }
        compose.onNodeWithText("Cobertura parcial; no se inventan las horas faltantes").assertIsDisplayed()
        compose.onNodeWithText("Cobertura completa del horario").assertDoesNotExist()

        // Absent values are not drawn or replaced by zeros.
        compose.runOnIdle {
            brief = ShiftWeatherBrief(emptySummary(), WeatherFreshness.FRESH, OBJECTIVE_ID)
        }
        compose.onNodeWithText("Pronóstico disponible").assertIsDisplayed()
        compose.onAllNodes(hasText("Probabilidad máxima de lluvia", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("°F", substring = true)).assertCountEquals(0)

        compose.runOnIdle { brief = null }
        compose.onNodeWithText(
            "Todavía no hay un pronóstico utilizable. Tocá para revisar ubicación, consentimiento o cobertura.",
        ).assertIsDisplayed()
        compose.runOnIdle { loading = true }
        compose.onNodeWithText("Consultando el clima del objetivo…").assertIsDisplayed()
    }

    // ---- Errors ----

    // A245, C084
    @Test
    fun refreshErrorKeepsTheLastForecastAndOffersRetryAndCloseAtEveryFontSize() {
        var scale by mutableStateOf(TestFontScale.STANDARD)
        var retries = 0
        var dismissals = 0
        var closes = 0
        val state = detailState(forecast(), WeatherFreshness.STALE).copy(errorMessage = OFFLINE_ERROR)
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = false, fontScale = scale) {
                WeatherSurfaceHost(
                    state,
                    WeatherActions(
                        refresh = { retries++ },
                        clearMessage = { dismissals++ },
                        close = { closes++ },
                    ),
                )
            }
        }

        TestFontScale.entries.forEachIndexed { index, fontScale ->
            compose.runOnIdle { scale = fontScale }
            compose.onNodeWithText(OFFLINE_ERROR).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Reintentar").performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertEquals(index + 1, retries)
                assertEquals(index, dismissals)
            }
            compose.onNode(MESSAGE_CLOSE).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertEquals(index + 1, retries)
                assertEquals(index + 1, dismissals)
            }
            // The last valid forecast stays on screen next to the error.
            compose.onNodeWithText("Cobertura completa").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("$LAST_UPDATE · desactualizado, todavía visible.").performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle { assertEquals(0, closes) }
        compose.onNodeWithTag("weather-close").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    // A245
    @Test
    fun globalErrorCanBeDismissedHasNoRetryAndKeepsTheSettingsReachable() {
        var dismissals = 0
        var refreshes = 0
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    globalState(ENABLED).copy(errorMessage = NO_BROWSER_ERROR),
                    WeatherActions(clearMessage = { dismissals++ }, refresh = { refreshes++ }),
                )
            }
        }

        compose.onNodeWithText(NO_BROWSER_ERROR).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
        compose.onNode(MESSAGE_CLOSE).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(0, refreshes)
        }
        compose.onNodeWithText("Habilitar Clima").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Borrar pronósticos guardados").performScrollTo().assertIsDisplayed()
    }

    // ---- Shift detail: values ----

    // A243, C083
    @Test
    fun shiftDetailShowsFeelsLikeRainWindAndGustsInTheSummaryAndInTheHourCard() {
        val hour = hour(Instant.parse("2026-08-17T02:00:00Z"))
        val forecast = forecast(listOf(hour))
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(detailState(forecast, WeatherFreshness.FRESH), WeatherActions())
            }
        }

        compose.onNodeWithText("Condición relevante: Lluvia").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Temperatura: 10–17 °C").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sensación: 9–16 °C").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Máxima probabilidad de lluvia: 70 %").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Viento máximo: 30 km/h").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ráfaga máxima: 45 km/h").performScrollTo().assertIsDisplayed()

        // One card per hour, in the shift's own zone (02:00Z is 23:00 in Buenos Aires).
        compose.onNodeWithTag("weather-hourly-carousel").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("16/08 23:00").assertIsDisplayed()
        compose.onNodeWithText("12 °C").assertIsDisplayed()
        compose.onNodeWithText("Lluvia").assertExists()
        compose.onNodeWithText("Sensación 11 °C").assertExists()
        compose.onNodeWithText("Lluvia 50 %").assertExists()
        compose.onNodeWithText("0,2 mm").assertExists()
        compose.onNodeWithText("Viento 20 km/h").assertExists()
        compose.onNodeWithText("Ráfagas 30 km/h").assertExists()
    }

    // A243, C083
    @Test
    fun shiftDetailNeverDrawsAValueTheProviderDidNotGive() {
        val from = Instant.parse("2026-08-17T02:00:00Z")
        val emptyHour = WeatherHour(
            from, from.plusSeconds(3_600), null, null, null, null, null, WeatherCondition.UNKNOWN, null, null, null,
        )
        val forecast = forecast(listOf(emptyHour))
        var state by mutableStateOf(detailState(forecast, WeatherFreshness.FRESH).copy(shiftSummary = emptySummary()))
        compose.setContent { MaterialTheme { WeatherSurfaceHost(state, WeatherActions()) } }

        compose.onNodeWithText("Resumen de toda la jornada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("weather-hourly-carousel").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("16/08 23:00").assertIsDisplayed()
        compose.onNodeWithText("—").assertExists()
        compose.onNodeWithText("Condición desconocida").assertExists()
        listOf(
            "Condición relevante", "Temperatura", "Sensación", "probabilidad de lluvia", "Precipitación estimada",
            "Viento", "Ráfaga", "Lluvia", " mm", "°C", "km/h",
        ).forEach { fragment ->
            compose.onAllNodes(hasText(fragment, substring = true)).assertCountEquals(0)
        }

        // Only one end of a range: the other one is not invented.
        compose.runOnIdle {
            state = state.copy(
                shiftSummary = emptySummary().copy(
                    maximumTemperatureCelsius = 17.0,
                    minimumApparentTemperatureCelsius = 9.0,
                ),
            )
        }
        compose.onNodeWithText("Temperatura: hasta 17 °C").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sensación: desde 9 °C").performScrollTo().assertIsDisplayed()
    }

    // A235
    @Test
    fun shiftDetailShowsEveryTemperatureInFahrenheitWhenThatUnitIsChosen() {
        val forecast = forecast(listOf(hour(Instant.parse("2026-08-17T02:00:00Z"))))
        var state by mutableStateOf(detailState(forecast, WeatherFreshness.FRESH))
        compose.setContent { MaterialTheme { WeatherSurfaceHost(state, WeatherActions()) } }

        compose.onNodeWithText("Temperatura: 10–17 °C").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12 °C").assertExists()
        compose.onAllNodes(hasText("°F", substring = true)).assertCountEquals(0)

        compose.runOnIdle {
            state = state.copy(preferences = state.preferences.copy(unitSystem = WeatherUnitSystem.FAHRENHEIT))
        }
        compose.onNodeWithText("Temperatura: 50–63 °F").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sensación: 48–61 °F").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("54 °F").assertExists()
        compose.onNodeWithText("Sensación 52 °F").assertExists()
        compose.onAllNodes(hasText("°C", substring = true)).assertCountEquals(0)
        // Wind and rain do not change with the temperature unit.
        compose.onNodeWithText("Viento máximo: 30 km/h").assertExists()
        compose.onNodeWithText("Máxima probabilidad de lluvia: 70 %").assertExists()
    }

    // ---- Taps ----

    // A241
    @Test
    fun tappingRefreshAsksOnceAndCannotBeRepeatedWhileItIsRunning() {
        var refreshes = 0
        var state by mutableStateOf(detailState(forecast(), WeatherFreshness.STALE))
        compose.setContent {
            MaterialTheme { WeatherSurfaceHost(state, WeatherActions(refresh = { refreshes++ })) }
        }
        compose.runOnIdle { assertEquals(0, refreshes) }

        compose.onNodeWithText("Actualizar").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }

        compose.runOnIdle { state = state.copy(isRefreshing = true) }
        compose.onNodeWithText("Actualizar").assertDoesNotExist()
        compose.onNodeWithText("Actualizando…").performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }

        compose.runOnIdle { state = state.copy(isRefreshing = false) }
        compose.onNodeWithText("Actualizar").performScrollTo().assertIsEnabled()
    }

    // A234
    @Test
    fun enableSwitchTurnsWeatherOffAndOnWithoutAskingForTheExplanationAgain() {
        val requests = mutableListOf<Boolean>()
        var explanations = 0
        var state by mutableStateOf(globalState(ENABLED))
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    state,
                    WeatherActions(
                        setEnabled = { enabled ->
                            requests += enabled
                            state = state.copy(preferences = state.preferences.copy(enabled = enabled))
                        },
                        enableAfterExplanation = { explanations++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Clima activado").assertIsDisplayed()
        compose.onNodeWithText("Habilitar Clima").performScrollTo().assertIsOn().performClick()
        compose.runOnIdle { assertEquals(listOf(false), requests) }
        compose.onNodeWithText("Clima desactivado").assertIsDisplayed()
        compose.onNodeWithText("Clima activado").assertDoesNotExist()
        compose.onNodeWithText("Habilitar Clima").performScrollTo().assertIsOff()
        // Already explained once: the conscious-activation button does not come back.
        compose.onNodeWithText("Entiendo y habilitar Clima").assertDoesNotExist()

        compose.onNodeWithText("Habilitar Clima").performClick()
        compose.runOnIdle {
            assertEquals(listOf(false, true), requests)
            assertEquals(0, explanations)
        }
        compose.onNodeWithText("Habilitar Clima").assertIsOn()
        compose.onNodeWithText("Clima activado").assertIsDisplayed()
    }

    // A235
    @Test
    fun unitChoiceSelectsFahrenheitWithASingleRequestAndPromisesNoDownload() {
        val chosen = mutableListOf<WeatherUnitSystem>()
        var refreshes = 0
        var state by mutableStateOf(globalState(ENABLED))
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    state,
                    WeatherActions(
                        setUnitSystem = { unit ->
                            chosen += unit
                            state = state.copy(preferences = state.preferences.copy(unitSystem = unit))
                        },
                        refresh = { refreshes++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Celsius (°C)").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Fahrenheit (°F)").performScrollTo().assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals(listOf(WeatherUnitSystem.FAHRENHEIT), chosen) }
        compose.onNodeWithText("Fahrenheit (°F)").assertIsSelected()
        compose.onNodeWithText("Celsius (°C)").performScrollTo().assertIsNotSelected().performClick()
        compose.runOnIdle {
            assertEquals(listOf(WeatherUnitSystem.FAHRENHEIT, WeatherUnitSystem.CELSIUS), chosen)
            assertEquals(0, refreshes)
        }
        compose.onNodeWithText("Celsius (°C)").assertIsSelected()
        compose.onNodeWithText("Cambiar la unidad convierte la presentación; no vuelve a descargar.")
            .performScrollTo().assertIsDisplayed()
    }

    // A236
    @Test
    fun weatherInNotificationsSwitchWorksOnlyWhileWeatherIsEnabled() {
        val requests = mutableListOf<Boolean>()
        var state by mutableStateOf(globalState(ENABLED))
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    state,
                    WeatherActions(
                        setIncludeInNotifications = { include ->
                            requests += include
                            state = state.copy(preferences = state.preferences.copy(includeInNotifications = include))
                        },
                    ),
                )
            }
        }

        compose.onNodeWithText(INCLUDE_IN_NOTIFICATIONS).performScrollTo().assertIsEnabled().assertIsOff().performClick()
        compose.runOnIdle { assertEquals(listOf(true), requests) }
        compose.onNodeWithText(INCLUDE_IN_NOTIFICATIONS).assertIsOn()
        compose.onNodeWithText(
            "Los avisos reducidos u ocultos nunca muestran clima. Un fallo meteorológico no retrasa ni elimina el aviso.",
        ).performScrollTo().assertIsDisplayed()

        // Clima off: the switch stays visible but cannot be changed.
        compose.runOnIdle { state = state.copy(preferences = state.preferences.copy(enabled = false)) }
        compose.onNodeWithText(INCLUDE_IN_NOTIFICATIONS).performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(true), requests) }

        compose.runOnIdle { state = state.copy(preferences = state.preferences.copy(enabled = true)) }
        compose.onNodeWithText(INCLUDE_IN_NOTIFICATIONS).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(true, false), requests) }
    }

    // A239
    @Test
    fun cancellingTheCacheDeletionClosesTheQuestionAndDeletesNothing() {
        var clears = 0
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(globalState(ENABLED), WeatherActions(clearCache = { clears++ }))
            }
        }

        compose.onNodeWithText("Borrar pronósticos guardados").performScrollTo().performClick()
        compose.onNodeWithText(CLEAR_QUESTION).assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsDisplayed().performClick()
        compose.onNodeWithText(CLEAR_QUESTION).assertDoesNotExist()
        compose.onNodeWithText("Borrar").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, clears) }

        // Cancelling does not lock the action: asking again and confirming deletes once.
        compose.onNodeWithText("Borrar pronósticos guardados").performScrollTo().performClick()
        compose.onNodeWithText(CLEAR_QUESTION).assertIsDisplayed()
        compose.onNodeWithText("Borrar").performClick()
        compose.onNodeWithText(CLEAR_QUESTION).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, clears) }
    }

    // ---- Fictitious state ----

    private fun globalState(preferences: WeatherPreferences) = WeatherUiState(
        surface = WeatherSurface.GLOBAL,
        preferences = preferences,
        isLoading = false,
    )

    private fun detailState(forecast: WeatherForecast, freshness: WeatherFreshness) = WeatherUiState(
        surface = WeatherSurface.SHIFT,
        preferences = ENABLED,
        forecast = forecast,
        freshness = freshness,
        selectedShift = shift(),
        weatherLocationName = forecast.location.displayName,
        shiftSummary = completeSummary(),
        shiftHours = forecast.hours,
        isLoading = false,
    )

    private fun completeSummary(): ShiftWeatherSummary {
        val shift = shift()
        return ShiftWeatherSummary(
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
    }

    private fun emptySummary(): ShiftWeatherSummary = completeSummary().copy(
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

    private fun forecast(
        hours: List<WeatherHour> = listOf(
            hour(Instant.parse("2026-08-17T02:00:00Z")),
            hour(Instant.parse("2026-08-17T03:00:00Z")),
        ),
    ) = WeatherForecast("open-meteo", LOCATION, NOW, hours.first().validFrom, hours.last().validUntilExclusive, hours)

    private fun hour(from: Instant) = WeatherHour(
        from, from.plusSeconds(3_600), 12.0, 11.0, 0.2, 50, 61, WeatherCondition.RAIN, 20.0, 30.0, 180.0,
    )

    private fun shift() = Shift(
        id = UUID.fromString("00000000-0000-0000-0000-000000001411"),
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
        sourceObjectiveId = OBJECTIVE_ID,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-16T20:00:00Z")
        val OBJECTIVE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000001410")
        val LOCATION = WeatherLocation(
            OBJECTIVE_ID.toString(), "Objetivo ficticio", -34.6037, -58.3816, ZoneId.of("America/Argentina/Buenos_Aires"),
        )
        val ENABLED = WeatherPreferences(enabled = true, providerExplanationAccepted = true)

        // 20:00Z in Buenos Aires.
        const val LAST_UPDATE = "Última actualización: 16/08/2026 17:00"
        const val OFFLINE_ERROR = "No pudimos conectar. Se conserva el último pronóstico disponible."
        const val NO_BROWSER_ERROR = "No hay una aplicación compatible para abrir el enlace."
        const val OUTSIDE_WINDOW = "La jornada quedó fuera de la ventana futura del pronóstico."
        const val BRIEF_FRESH = "Actualizado · tocá para ver el detalle horario"
        const val INCLUDE_IN_NOTIFICATIONS = "Incluir clima en avisos completos"
        const val CLEAR_QUESTION =
            "Se eliminarán los pronósticos guardados de todos los objetivos. La configuración se conserva."

        /** The "Cerrar" of the error message, not the one in the dialog header. */
        val MESSAGE_CLOSE = hasText("Cerrar") and !hasTestTag("weather-close")
    }
}
