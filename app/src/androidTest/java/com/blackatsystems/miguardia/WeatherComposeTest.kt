package com.blackatsystems.miguardia

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.window.DialogWindowProvider
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
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.weather.WeatherCreditLink
import com.blackatsystems.miguardia.weather.WeatherCredits
import com.blackatsystems.miguardia.weather.WeatherPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.io.File
import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

class WeatherComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(Dispatchers.Main.immediate)

    @Test
    fun shortDialogWrapsLoadingThenKeepsMissingLocationAndExpandedCreditsReachable() {
        val originalOrientation = compose.activity.requestedOrientation
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            compose.waitUntil(5_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            }
            var dark by mutableStateOf(false)
            var scale by mutableStateOf(TestFontScale.STANDARD)
            var state by mutableStateOf(WeatherUiState(
                surface = WeatherSurface.SHIFT, selectedShift = shift(),
                preferences = WeatherPreferences(enabled = true, providerExplanationAccepted = true),
            ))
            var density = 1f
            compose.setContent {
                density = LocalDensity.current.density
                AndroidFontScaleTheme(darkTheme = dark, fontScale = scale) {
                    WeatherSurfaceHost(state, WeatherActions())
                }
            }
            for (theme in listOf(false, true)) for (fontScale in TestFontScale.entries)
                for (viewport in listOf(320 to 480, 360 to 640)) {
                compose.runOnIdle {
                    dark = theme; scale = fontScale
                    state = state.copy(isLoading = true, ineligibleReason = null, forecast = null,
                        shiftHours = emptyList(), shiftSummary = null)
                }
                constrainQaDialog(viewport.first, viewport.second)
                assertWrappedBottom("weather-loading", 16f * density, density)
                assertHeaderContainedAndUncovered()

                compose.runOnIdle {
                    state = state.copy(isLoading = false, ineligibleReason = "Este objetivo todavía no tiene ubicación.")
                }
                constrainQaDialog(viewport.first, viewport.second)
                compose.onNodeWithText("Este objetivo todavía no tiene ubicación.").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Actualizar").assertDoesNotExist()
                compose.onNodeWithTag("weather-provider-details").performScrollTo().assertIsDisplayed()
                assertHeaderContainedAndUncovered()

                val forecast = forecast()
                compose.runOnIdle {
                    state = state.copy(ineligibleReason = null, forecast = forecast,
                        shiftHours = forecast.hours, freshness = WeatherFreshness.FRESH,
                        shiftSummary = com.blackatsystems.miguardia.core.domain.weather.summarizeShiftWeather(
                            shift().startAt, shift().endAt, forecast,
                        ))
                }
                constrainQaDialog(viewport.first, viewport.second)
                compose.onNodeWithTag("weather-provider-details").performScrollTo().performClick()
                compose.onNodeWithText(WeatherCredits.PRESENTATION).performScrollTo().assertIsDisplayed()
                assertWrappedBottom("weather-provider-expanded", 16f * density, density)
                assertHeaderContainedAndUncovered()
                // Return to collapsed state before testing the next text/theme configuration.
                compose.onNodeWithTag("weather-provider-details").performScrollTo().performClick()
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                compose.activity.requestedOrientation = originalOrientation
            }
        }
    }

    @Test
    fun lowLandscapeDialogKeepsHeaderAndOneCloseTapOutsideNoticeAndScroll() {
        val originalOrientation = compose.activity.requestedOrientation
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.waitUntil(5_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            var dark by mutableStateOf(false)
            var scale by mutableStateOf(TestFontScale.STANDARD)
            var closes = 0
            compose.setContent {
                AndroidFontScaleTheme(darkTheme = dark, fontScale = scale) {
                    WeatherSurfaceHost(
                        WeatherUiState(surface = WeatherSurface.GLOBAL, isLoading = false,
                            infoMessage = "Pronóstico actualizado."),
                        WeatherActions(close = { closes++ }),
                    )
                }
            }
            for (theme in listOf(false, true)) for (fontScale in TestFontScale.entries) {
                compose.runOnIdle { dark = theme; scale = fontScale }
                constrainQaDialog(600, 320)
                compose.onNodeWithText("Borrar pronósticos guardados").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("transient-confirmation").assertIsDisplayed()
                assertHeaderContainedAndUncovered()
                val before = closes
                compose.onNodeWithTag("weather-close").performTouchInput { click() }
                compose.runOnIdle { assertEquals(before + 1, closes) }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                compose.activity.requestedOrientation = originalOrientation
            }
        }
    }

    private fun assertWrappedBottom(lastElementTag: String, expectedPaddingPx: Float, tolerancePx: Float) {
        val surface = compose.onNodeWithTag("weather-dialog-surface").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag(lastElementTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("Dialog has unused space below its actual last element: $lastElementTag",
            expectedPaddingPx, surface.bottom - last.bottom, tolerancePx)
        assertTrue(last.bottom <= surface.bottom)
    }

    private fun assertHeaderContainedAndUncovered() {
        val surface = compose.onNodeWithTag("weather-dialog-surface").fetchSemanticsNode().boundsInRoot
        val heading = compose.onNodeWithTag("weather-heading").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val close = compose.onNodeWithTag("weather-close").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("weather-scroll-content").fetchSemanticsNode().boundsInRoot
        assertTrue(heading.top >= surface.top && heading.bottom <= surface.bottom)
        assertTrue(close.left >= surface.left && close.right <= surface.right && close.bottom <= surface.bottom)
        assertFalse("Header must remain outside the scroll viewport", heading.overlaps(body))
        val notices = compose.onAllNodesWithTag("transient-confirmation").fetchSemanticsNodes()
        notices.forEach { notice ->
            assertFalse("Notice covers header", notice.boundsInRoot.overlaps(heading))
            assertFalse("Notice covers Close", notice.boundsInRoot.overlaps(close))
            assertFalse("Notice covers scroll content", notice.boundsInRoot.overlaps(body))
        }
    }

    private fun constrainQaDialog(widthDp: Int, heightDp: Int) {
        // Constrain the actual Dialog Window, not a requiredSize on its caller.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val density = compose.activity.resources.displayMetrics.density
            val host = compose.activity.window.decorView
            val width = minOf((widthDp * density).toInt(), host.width)
            val height = minOf((heightDp * density).toInt(), host.height)
            check(width > 0 && height > 0)
            qaDialogWindow().setLayout(width, height)
        }
        compose.waitForIdle()
    }

    private fun qaDialogWindow(): Window {
        check(Build.VERSION.SDK_INT >= 29) { "Window-scoped QA inspection requires API 29+." }
        val windows = mutableSetOf<Window>()
        fun collect(view: View) {
            if (view is DialogWindowProvider) windows += view.window
            if (view is ViewGroup) repeat(view.childCount) { collect(view.getChildAt(it)) }
        }
        // WindowInspector lists only this process's windows; never another app's tree.
        WindowInspector.getGlobalWindowViews().forEach { root ->
            check(root.context.packageName == "com.blackatsystems.miguardia.qa")
            collect(root)
        }
        return windows.single()
    }

    @Test
    fun creditsRemainReadableInBothThemesAndEveryAndroidFontScale() {
        val dark = mutableStateOf(false)
        val fontScale = mutableStateOf(TestFontScale.STANDARD)
        val showBrief = mutableStateOf(false)
        val summary = com.blackatsystems.miguardia.core.domain.weather.summarizeShiftWeather(
            forecast().coverageStart, forecast().coverageEndExclusive, forecast(),
        )
        val evidence = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "stage12-weather-visual-${UUID.randomUUID()}")
        check(evidence.mkdir()) { "Evidence directory must be new." }
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark.value, fontScale = fontScale.value) {
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
        for (theme in listOf(false, true)) for (scale in TestFontScale.entries) {
            compose.runOnIdle { dark.value = theme; fontScale.value = scale; showBrief.value = false }
            compose.onNodeWithTag("weather-provider-details").performScrollTo().performClick()
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
                    saveQaNodeCapture(
                        "weather-dialog-surface",
                        File(evidence, "${theme}-${scale.name}-${if (label == WeatherCreditLink.LICENCE.label) "links" else "explanation"}.png"),
                        requireDialog = true,
                    )
                }
            }
            compose.runOnIdle { showBrief.value = true }
            compose.onNodeWithText(WeatherCredits.SUMMARY).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(WeatherCredits.OPEN).performScrollTo().assertIsDisplayed()
            saveQaNodeCapture("credits-brief", File(evidence, "${theme}-${scale.name}-brief.png"), requireDialog = false)
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
        compose.onNodeWithTag("weather-provider-details").performScrollTo().performClick()
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
        compose.onNodeWithTag("weather-close").assertIsDisplayed().performClick()
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
        compose.onNodeWithTag("weather-provider-details").performScrollTo().performClick()
        compose.onNodeWithText(WeatherCreditLink.LICENCE.label).performScrollTo().performClick()
        compose.onNodeWithTag("weather-close").assertIsDisplayed().performClick()
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
        compose.onNodeWithTag("weather-essential-privacy").assertIsDisplayed()
        compose.onNodeWithText("Clima desactivado").assertIsDisplayed()
        compose.onNodeWithText("Proveedor y privacidad").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, enabled) }
        compose.onNodeWithText("Entiendo y habilitar Clima").assertIsDisplayed().performClick()
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
    fun shiftEntryExplainsPrivacyBeforeActivationAndKeepsProviderDetailsAccessible() {
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

        compose.onNodeWithTag("weather-essential-privacy").assertIsDisplayed()
        compose.onNodeWithText("Entiendo y habilitar Clima").assertIsDisplayed()
        compose.onNodeWithTag("weather-provider-details").performScrollTo().performClick()
        compose.onNodeWithText("Proveedor y privacidad").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(WeatherCreditLink.TERMS.label).performScrollTo().assertIsDisplayed()
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
                        ineligibleReason = "Este objetivo todavía no tiene ubicación para el clima. Agregá una dirección o usá tu ciudad actual desde Mi forma de trabajar > Opciones avanzadas.",
                        isLoading = false,
                    ),
                    WeatherActions(),
                )
            }
        }

        compose.onNodeWithText("Pronóstico para: ubicación sin configurar").assertExists()
        compose.onNodeWithText("Este objetivo todavía no tiene ubicación para el clima. Agregá una dirección o usá tu ciudad actual desde Mi forma de trabajar > Opciones avanzadas.").assertExists()
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
        compose.onNodeWithText("16/08/2026 19:00 – 17/08/2026 07:00").assertExists()
        compose.onNodeWithText("Cobertura completa").assertExists()
        compose.onNodeWithText("Temperatura: 10–17 °C").assertExists()
        compose.onNodeWithText("Precipitación estimada en el intervalo: 4,5 mm").assertExists()
        compose.onNodeWithText("Deslizá hacia la derecha.").assertExists()
        compose.onNodeWithTag("weather-hourly-carousel").assertExists()
        compose.onNodeWithText("17/08 00:00").performScrollTo().assertExists()
        compose.onNodeWithTag("weather-provider-details").performScrollTo().performClick()
        compose.onNodeWithText("Datos meteorológicos: Open-Meteo").performScrollTo().assertExists()
        compose.onAllNodesWithText(WeatherCreditLink.LICENCE.label).assertCountEquals(1)
        compose.onNodeWithText(WeatherCreditLink.LICENCE.label).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(WeatherCredits.PRESENTATION).assertExists()
    }

    private fun saveQaNodeCapture(tag: String, output: File, requireDialog: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName == "com.blackatsystems.miguardia.qa")
        check(compose.activity.packageName == "com.blackatsystems.miguardia.qa")
        var window: Window? = null
        instrumentation.runOnMainSync {
            window = if (requireDialog) qaDialogWindow() else compose.activity.window
            check(checkNotNull(window).decorView.hasWindowFocus()) { "QA window lost focus." }
            check(checkNotNull(window).attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0) {
                "Protected QA windows are not captured; FLAG_SECURE remains unchanged."
            }
        }
        val node = if (requireDialog) {
            compose.onNode(hasTestTag(tag) and hasAnyAncestor(isDialog()))
        } else {
            compose.onNodeWithTag(tag)
        }
        val bounds = node.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        check(bounds.width > 0 && bounds.height > 0)
        // Capture this exact QA node, including a Dialog's own root. No full-display
        // bitmap is ever acquired or cropped. Overlay adversary still needs Android QA.
        val bitmap = node.captureToImage().asAndroidBitmap()
        try {
            check(kotlin.math.abs(bitmap.width - bounds.width) <= 1f)
            check(kotlin.math.abs(bitmap.height - bounds.height) <= 1f)
            val colors = (0..19).flatMap { x -> (0..19).map { y ->
                bitmap.getPixel(x * (bitmap.width - 1) / 19, y * (bitmap.height - 1) / 19)
            } }.toSet()
            check(colors.size > 3) { "A blank capture is not visual evidence." }
            instrumentation.runOnMainSync {
                check(checkNotNull(window).decorView.hasWindowFocus()) { "QA window lost focus before persist." }
                check(checkNotNull(window).attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0)
            }
            check(output.createNewFile()) { "Evidence images must never overwrite an earlier capture." }
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
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
