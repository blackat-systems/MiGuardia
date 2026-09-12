package com.blackatsystems.miguardia

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import com.blackatsystems.miguardia.ui.ShiftWeatherBriefCard
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.weather.WeatherActions
import com.blackatsystems.miguardia.ui.weather.WeatherSurface
import com.blackatsystems.miguardia.ui.weather.WeatherSurfaceHost
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.ui.weather.ShiftWeatherBrief
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.weather.WeatherCreditLink
import com.blackatsystems.miguardia.weather.WeatherCredits
import com.blackatsystems.miguardia.weather.WeatherPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.io.File
import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WeatherComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun creditsRemainReadableInBothThemesAndEveryInternalZoom() {
        val dark = mutableStateOf(false)
        val zoom = mutableStateOf(AppZoom.STANDARD)
        val showBrief = mutableStateOf(false)
        val summary = com.blackatsystems.miguardia.core.domain.weather.summarizeShiftWeather(
            forecast().coverageStart, forecast().coverageEndExclusive, forecast(),
        )
        val evidence = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "credits-weather-visual")
        check(evidence.mkdirs() || evidence.isDirectory)
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark.value, appZoom = zoom.value) {
                if (showBrief.value) {
                    Column(Modifier.widthIn(max = 320.dp).testTag("credits-brief").verticalScroll(rememberScrollState())) {
                        ShiftWeatherBriefCard(
                            true, false, ShiftWeatherBrief(summary, WeatherFreshness.FRESH, OBJECTIVE_ID),
                            WeatherUnitSystem.CELSIUS,
                        ) {}
                    }
                } else {
                    WeatherSurfaceHost(
                        WeatherUiState(surface = WeatherSurface.GLOBAL, isLoading = false), WeatherActions(),
                    )
                }
            }
        }
        for (theme in listOf(false, true)) for (scale in AppZoom.entries) {
            compose.runOnIdle { dark.value = theme; zoom.value = scale; showBrief.value = false }
            (WeatherCreditLink.entries.map { it.label } + WeatherCredits.PRESENTATION).forEach { label ->
                val text = compose.onNodeWithText(label, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                layouts.forEach { layout ->
                    assertTrue(!layout.multiParagraph.didExceedMaxLines)
                    for (line in 0 until layout.lineCount) {
                        assertTrue(!layout.isLineEllipsized(line))
                        assertTrue(layout.getLineRight(line) <= layout.size.width + 1)
                    }
                }
                if (label == WeatherCreditLink.LICENCE.label || label == WeatherCredits.PRESENTATION) {
                    // Compose root capture can return the Activity behind a separate Dialog.
                    // Capture only the visible QA bounds from the device, never another app.
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    val device = UiDevice.getInstance(instrumentation)
                    check(device.currentPackageName == "com.blackatsystems.miguardia.qa")
                    // By.pkg().findObject() may select only a heading. The active accessibility
                    // root belongs to the synthetic Dialog and bounds its entire QA window.
                    val windowRoot = checkNotNull(instrumentation.uiAutomation.rootInActiveWindow)
                    check(windowRoot.packageName?.toString() == "com.blackatsystems.miguardia.qa")
                    val bounds = android.graphics.Rect().also(windowRoot::getBoundsInScreen)
                    check(bounds.width() > device.displayWidth / 2 && bounds.height() > device.displayHeight / 2)
                    val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                    val bitmap = Bitmap.createBitmap(screen, bounds.left, bounds.top, bounds.width(), bounds.height())
                    if (bitmap !== screen) screen.recycle()
                    try {
                        val colors = (0..19).flatMap { x -> (0..19).map { y -> bitmap.getPixel(
                            x * (bitmap.width - 1) / 19, y * (bitmap.height - 1) / 19,
                        ) } }.toSet()
                        check(colors.size > 3) { "A blank capture is not visual evidence" }
                        File(evidence, "${theme}-${scale.name}-${if (label == WeatherCreditLink.LICENCE.label) "links" else "explanation"}.png")
                            .outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                }
            }
            compose.runOnIdle { showBrief.value = true }
            compose.onNodeWithText(WeatherCredits.SUMMARY).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(WeatherCredits.OPEN).performScrollTo().assertIsDisplayed()
            val bitmap = compose.onNodeWithTag("credits-brief").captureToImage().asAndroidBitmap()
            try {
                File(evidence, "${theme}-${scale.name}-brief.png").outputStream()
                    .use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
    }

    @Test
    fun creditsOpenOnlyTheSelectedHttpsLinkAndKeepTheReturnAction() {
        val launches = mutableListOf<Intent>()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startActivity(intent: Intent) { launches += intent }
        }
        var closes = 0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme {
                    WeatherSurfaceHost(
                        WeatherUiState(surface = WeatherSurface.GLOBAL, isLoading = false),
                        WeatherActions(close = { closes++ }),
                    )
                }
            }
        }
        compose.runOnIdle { assertTrue(launches.isEmpty()) }
        WeatherCreditLink.entries.forEachIndexed { index, link ->
            compose.onAllNodesWithText(link.label).assertCountEquals(1)
            compose.onNodeWithText(link.label).performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(index + 1, launches.size)
                assertEquals(Intent.ACTION_VIEW, launches.last().action)
                assertEquals(link.url, launches.last().dataString)
                assertTrue(launches.last().extras == null)
            }
        }
        compose.onNodeWithText(WeatherCredits.PRESENTATION).assertExists()
        compose.onNodeWithText("Volver").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    @Test
    fun missingBrowserReportsFailureAndAllowsReturn() = assertLinkFailure(ActivityNotFoundException())

    @Test
    fun blockedBrowserReportsFailureAndAllowsReturn() = assertLinkFailure(SecurityException())

    private fun assertLinkFailure(failure: RuntimeException) {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startActivity(intent: Intent) { throw failure }
        }
        var failures = 0
        var closes = 0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme {
                    WeatherSurfaceHost(
                        WeatherUiState(surface = WeatherSurface.GLOBAL, isLoading = false),
                        WeatherActions(externalLinkFailed = { failures++ }, close = { closes++ }),
                    )
                }
            }
        }
        compose.runOnIdle { assertEquals(0, failures) }
        compose.onNodeWithText(WeatherCreditLink.LICENCE.label).performScrollTo().performClick()
        compose.onNodeWithText("Volver").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, failures)
            assertEquals(1, closes)
        }
    }

    @Test
    fun briefCreditsAppearOnceOnlyWithVisibleDataAndUseTheExistingDetailAction() {
        val enabled = mutableStateOf(true)
        val summary = com.blackatsystems.miguardia.core.domain.weather.summarizeShiftWeather(
            forecast().coverageStart, forecast().coverageEndExclusive, forecast(),
        )
        val brief = mutableStateOf<ShiftWeatherBrief?>(ShiftWeatherBrief(summary, WeatherFreshness.FRESH, OBJECTIVE_ID))
        var opens = 0
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ShiftWeatherBriefCard(enabled.value, false, brief.value, WeatherUnitSystem.FAHRENHEIT) { opens++ }
                }
            }
        }
        compose.onAllNodesWithText(WeatherCredits.SUMMARY).assertCountEquals(1)
        compose.onNodeWithText(WeatherCredits.SUMMARY).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opens); enabled.value = false }
        compose.onNodeWithText(WeatherCredits.SUMMARY).assertDoesNotExist()
        compose.runOnIdle { enabled.value = true; brief.value = null }
        compose.onNodeWithText(WeatherCredits.SUMMARY).assertDoesNotExist()
        val uncovered = com.blackatsystems.miguardia.core.domain.weather.summarizeShiftWeather(
            NOW.minusSeconds(7200), NOW.minusSeconds(3600), forecast(),
        )
        compose.runOnIdle { brief.value = ShiftWeatherBrief(uncovered, WeatherFreshness.FRESH, OBJECTIVE_ID) }
        compose.onNodeWithText(WeatherCredits.SUMMARY).assertDoesNotExist()
    }

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
    fun disabledWeatherExplainsPerObjectivePrivacyBeforeConsciousActivation() {
        var enabled = 0
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    WeatherUiState(surface = WeatherSurface.GLOBAL, isLoading = false),
                    WeatherActions(enableAfterExplanation = { enabled++ }),
                )
            }
        }
        compose.onNodeWithText("Pronóstico según la ubicación guardada en cada objetivo.").assertExists()
        compose.onNodeWithText("Open-Meteo recibe sólo las coordenadas del objetivo consultado y la IP habitual de conexión. No se envían guardias, nombres, direcciones ni notas.").assertExists()
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
        compose.onNodeWithText("Cada objetivo conserva su último pronóstico. Puede actualizarse al mostrar una jornada de ese lugar.").assertExists()
    }

    @Test
    fun globalWeatherConfirmsClearingCachesForEveryObjective() {
        var clears = 0
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    WeatherUiState(
                        surface = WeatherSurface.GLOBAL,
                        preferences = WeatherPreferences(enabled = true, providerExplanationAccepted = true),
                        isLoading = false,
                    ),
                    WeatherActions(clearCache = { clears++ }),
                )
            }
        }

        compose.onNodeWithText("Borrar pronósticos guardados").performScrollTo().performClick()
        compose.onNodeWithText("Se eliminarán los pronósticos guardados de todos los objetivos. La configuración se conserva.").assertIsDisplayed()
        compose.onNodeWithText("Borrar").performClick()
        compose.runOnIdle { assertEquals(1, clears) }
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
    fun shiftWithoutObjectiveLocationExplainsHowToEnableItAndCannotRefresh() {
        compose.setContent {
            MaterialTheme {
                WeatherSurfaceHost(
                    WeatherUiState(
                        surface = WeatherSurface.SHIFT,
                        preferences = WeatherPreferences(enabled = true, providerExplanationAccepted = true),
                        selectedShift = shift(),
                        ineligibleReason = "Este objetivo todavía no tiene ubicación. Activala cuando estés allí desde Mi forma de trabajar > Opciones avanzadas.",
                        isLoading = false,
                    ),
                    WeatherActions(),
                )
            }
        }

        compose.onNodeWithText("Pronóstico para: ubicación sin configurar").assertExists()
        compose.onNodeWithText("Este objetivo todavía no tiene ubicación. Activala cuando estés allí desde Mi forma de trabajar > Opciones avanzadas.").assertExists()
        compose.onNodeWithText("Todavía no hay un pronóstico disponible para esta jornada.").assertDoesNotExist()
        compose.onNodeWithText("Actualizar").assertDoesNotExist()
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
                        weatherLocationName = forecast.location.displayName,
                        shiftSummary = summary,
                        shiftHours = forecast.hours,
                        isLoading = false,
                    ),
                    WeatherActions(),
                )
            }
        }
        compose.onNodeWithText("Pronóstico para: Objetivo ficticio").assertExists()
        compose.onNodeWithText("Cobertura completa").assertExists()
        compose.onNodeWithText("Temperatura: 10–17 °C").assertExists()
        compose.onNodeWithText("Precipitación estimada en el intervalo: 4,5 mm").assertExists()
        compose.onNodeWithText("Deslizá hacia la derecha.").assertExists()
        compose.onNodeWithTag("weather-hourly-carousel").assertExists()
        compose.onNodeWithText("17/08 00:00").performScrollTo().assertExists()
        compose.onNodeWithText("Datos meteorológicos: Open-Meteo").performScrollTo().assertExists()
        compose.onAllNodesWithText(WeatherCreditLink.LICENCE.label).assertCountEquals(1)
        compose.onNodeWithText(WeatherCreditLink.LICENCE.label).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(WeatherCredits.PRESENTATION).assertExists()
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
        sourceObjectiveId = OBJECTIVE_ID,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-16T20:00:00Z")
        val OBJECTIVE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000901")
        val LOCATION = WeatherLocation(
            OBJECTIVE_ID.toString(), "Objetivo ficticio", -34.6037, -58.3816, ZoneId.of("America/Argentina/Buenos_Aires"),
        )
    }
}
