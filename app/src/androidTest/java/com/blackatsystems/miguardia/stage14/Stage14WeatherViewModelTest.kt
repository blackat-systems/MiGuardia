package com.blackatsystems.miguardia.stage14

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherFailure
import com.blackatsystems.miguardia.core.domain.weather.WeatherFailureKind
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.weather.WeatherCacheStore
import com.blackatsystems.miguardia.weather.WeatherClientResult
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Stage 14 · Clima: WeatherViewModel over the real WeatherRuntime, preferences and cache, all in a
 * TemporaryFolder, with a scripted client instead of the network. Synthetic data only.
 */
@RunWith(AndroidJUnit4::class)
class Stage14WeatherViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // A241
    @Test
    fun openingAnEligibleShiftWithoutCacheDownloadsOnceSavesAndConfirms() = runBlocking<Unit> {
        val harness = harness("open-download")
        try {
            harness.preferences.enableAfterExplanation()
            val downloaded = stage14WeatherForecast(harness.location)
            harness.client.answer = { WeatherClientResult.Success(downloaded) }
            val viewModel = harness.enabledViewModel()

            onMain { viewModel.openShift(harness.shift.id) }
            val state = viewModel.awaitState("primera descarga aplicada") {
                it.forecast == downloaded && it.infoMessage == UPDATED && !it.isRefreshing && !it.isLoading
            }

            assertEquals(1, harness.client.calls)
            assertEquals(harness.shift.id, state.selectedShift?.id)
            assertEquals("Objetivo ficticio", state.weatherLocationName)
            assertEquals(WeatherFreshness.FRESH, state.freshness)
            assertEquals(WeatherCoverage.COMPLETE, state.shiftSummary?.coverage)
            assertEquals(8, state.shiftHours.size)
            assertNull(state.errorMessage)
            assertNull(state.ineligibleReason)
            assertEquals(downloaded, harness.runtime.latestForShift(harness.shift))
        } finally {
            harness.close()
        }
    }

    // A241, C084
    @Test
    fun manualRefreshForcesADownloadEvenWithAFreshCacheAndConfirmsTheUpdate() = runBlocking<Unit> {
        val harness = harness("manual-refresh")
        try {
            harness.preferences.enableAfterExplanation()
            val cached = stage14WeatherForecast(harness.location)
            val newer = stage14WeatherForecast(harness.location, baseTemperature = 5.0)
            harness.writeCache(cached)
            harness.client.answer = { WeatherClientResult.Success(newer) }
            val viewModel = harness.enabledViewModel()

            onMain { viewModel.openShift(harness.shift.id) }
            val opened = viewModel.awaitState("caché fresca aplicada") {
                it.forecast == cached && !it.isRefreshing && !it.isLoading
            }
            // A fresh cache is shown as is: no request and no "updated" notice.
            assertEquals(0, harness.client.calls)
            assertNull(opened.infoMessage)
            assertEquals(WeatherFreshness.FRESH, opened.freshness)

            onMain { viewModel.manualRefresh() }
            val refreshed = viewModel.awaitState("actualización manual") {
                it.forecast == newer && it.infoMessage == UPDATED && !it.isRefreshing
            }

            assertEquals(1, harness.client.calls)
            assertEquals(5.0, refreshed.shiftSummary?.minimumTemperatureCelsius)
            assertNull(refreshed.errorMessage)
            assertEquals(newer, harness.runtime.latestForShift(harness.shift))
        } finally {
            harness.close()
        }
    }

    // A234
    @Test
    fun disablingWeatherEmptiesTheDaySummariesAndKeepsThemEmpty() = runBlocking<Unit> {
        val harness = harness("disable-briefs")
        try {
            harness.preferences.enableAfterExplanation()
            harness.writeCache(stage14WeatherForecast(harness.location))
            val viewModel = harness.enabledViewModel()
            val ids = setOf(harness.shift.id)

            onMain { viewModel.loadBriefs(ids) }
            val loaded = viewModel.awaitState("resumen del día cargado") {
                it.shiftBriefs.keys == ids && it.loadingBriefIds.isEmpty()
            }
            assertEquals(WeatherCoverage.COMPLETE, loaded.shiftBriefs.getValue(harness.shift.id).summary.coverage)

            onMain { viewModel.setEnabled(false) }
            val disabled = viewModel.awaitState("Clima apagado") { !it.preferences.enabled }
            assertTrue(disabled.shiftBriefs.isEmpty())
            assertTrue(disabled.loadingBriefIds.isEmpty())
            // Turning Clima off keeps the accepted explanation: no silent re-consent, no permission.
            assertTrue(disabled.preferences.providerExplanationAccepted)

            onMain { viewModel.loadBriefs(ids) }
            val afterRequest = viewModel.awaitState("pedido de resumen con Clima apagado") {
                it.loadingBriefIds.isEmpty()
            }
            assertTrue(afterRequest.shiftBriefs.isEmpty())
            assertEquals(0, harness.client.calls)
        } finally {
            harness.close()
        }
    }

    // A234
    @Test
    fun disablingWeatherCancelsTheDownloadInFlightAndNoLaterRefreshReachesTheClient() = runBlocking<Unit> {
        val harness = harness("disable-cancels")
        try {
            harness.preferences.enableAfterExplanation()
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            harness.client.answer = {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            val viewModel = harness.enabledViewModel()

            onMain { viewModel.openShift(harness.shift.id) }
            withTimeout(5_000) { started.await() }
            onMain { viewModel.setEnabled(false) }
            withTimeout(5_000) { cancelled.await() }

            val disabled = viewModel.awaitState("Clima apagado y sin descarga") {
                !it.preferences.enabled && !it.isRefreshing
            }
            assertNull(disabled.forecast)
            assertEquals(1, harness.client.calls)

            harness.client.answer = { WeatherClientResult.Success(stage14WeatherForecast(it)) }
            onMain { viewModel.manualRefresh() }
            val afterManual = viewModel.awaitState("actualización manual con Clima apagado") { !it.isRefreshing }
            assertEquals(1, harness.client.calls)
            assertNull(afterManual.forecast)
            assertNull(afterManual.infoMessage)
            assertNull(harness.runtime.latestForShift(harness.shift))
        } finally {
            harness.close()
        }
    }

    // A235
    @Test
    fun changingTheUnitKeepsTheSameForecastAndNeverDownloadsAgain() = runBlocking<Unit> {
        val harness = harness("unit")
        try {
            harness.preferences.enableAfterExplanation()
            val downloaded = stage14WeatherForecast(harness.location)
            harness.client.answer = { WeatherClientResult.Success(downloaded) }
            val viewModel = harness.enabledViewModel()
            onMain { viewModel.openShift(harness.shift.id) }
            viewModel.awaitState("primera descarga aplicada") {
                it.forecast == downloaded && !it.isRefreshing && !it.isLoading
            }
            assertEquals(1, harness.client.calls)

            onMain { viewModel.setUnitSystem(WeatherUnitSystem.FAHRENHEIT) }
            viewModel.awaitState("unidad Fahrenheit") {
                it.preferences.unitSystem == WeatherUnitSystem.FAHRENHEIT
            }
            // Leave room for a wrong, late request before counting.
            delay(300)
            val fahrenheit = viewModel.uiState.value

            assertEquals(1, harness.client.calls)
            assertEquals(downloaded, fahrenheit.forecast)
            // The stored values stay in Celsius; only the presentation converts.
            assertEquals(18.0, fahrenheit.shiftSummary?.minimumTemperatureCelsius)
            assertEquals(25.0, fahrenheit.shiftSummary?.maximumTemperatureCelsius)
            assertNull(fahrenheit.errorMessage)
            assertEquals(WeatherUnitSystem.FAHRENHEIT, harness.preferences.current().unitSystem)

            onMain { viewModel.setUnitSystem(WeatherUnitSystem.CELSIUS) }
            viewModel.awaitState("unidad Celsius") { it.preferences.unitSystem == WeatherUnitSystem.CELSIUS }
            delay(300)
            assertEquals(1, harness.client.calls)
            assertEquals(downloaded, viewModel.uiState.value.forecast)
        } finally {
            harness.close()
        }
    }

    // A239
    @Test
    fun clearingTheCacheRemovesTheSavedForecastReportsItAndKeepsTheSettings() = runBlocking<Unit> {
        val harness = harness("clear-cache")
        try {
            harness.preferences.enableAfterExplanation()
            val cached = stage14WeatherForecast(harness.location)
            harness.writeCache(cached)
            val viewModel = harness.enabledViewModel()
            onMain { viewModel.openShift(harness.shift.id) }
            viewModel.awaitState("caché fresca aplicada") {
                it.forecast == cached && !it.isRefreshing && !it.isLoading
            }
            assertTrue(cacheFiles(harness.filesDir).isNotEmpty())

            onMain { viewModel.clearCache() }
            val cleared = viewModel.awaitState("cachés borradas") { it.infoMessage == CLEARED }

            assertNull(cleared.forecast)
            assertNull(cleared.freshness)
            assertNull(cleared.shiftSummary)
            assertTrue(cleared.shiftHours.isEmpty())
            assertTrue(cleared.shiftBriefs.isEmpty())
            assertNull(cleared.errorMessage)
            assertFalse(cleared.isRefreshing)
            assertNull(harness.runtime.latestForShift(harness.shift))
            assertTrue(cacheFiles(harness.filesDir).isEmpty())
            // Clearing is not a refresh and does not touch the configuration.
            assertEquals(0, harness.client.calls)
            assertTrue(harness.preferences.current().enabled)
            assertTrue(harness.preferences.current().providerExplanationAccepted)
        } finally {
            harness.close()
        }
    }

    // A245, C084
    @Test
    fun everyClientFailureShowsItsOwnMessageAndKeepsTheLastValidForecast() = runBlocking<Unit> {
        val harness = harness("failures")
        try {
            harness.preferences.enableAfterExplanation()
            // Two hours old: still visible, but old enough for opening the shift to ask again.
            val cached = stage14WeatherForecast(
                harness.location,
                fetchedAt = STAGE14_WEATHER_NOW.minus(Duration.ofHours(2)),
            )
            harness.writeCache(cached)
            assertEquals(WeatherFailureKind.entries.toSet(), FAILURE_MESSAGES.keys)
            val viewModel = harness.enabledViewModel()

            FAILURE_MESSAGES.entries.forEachIndexed { index, (kind, message) ->
                harness.client.answer = { WeatherClientResult.Failure(WeatherFailure(kind)) }
                if (index == 0) {
                    onMain { viewModel.openShift(harness.shift.id) }
                } else {
                    onMain {
                        viewModel.clearMessage()
                        viewModel.manualRefresh()
                    }
                }
                val state = viewModel.awaitState("mensaje de $kind") {
                    it.errorMessage == message && !it.isRefreshing && !it.isLoading
                }

                assertEquals("Una consulta por intento ($kind)", index + 1, harness.client.calls)
                assertEquals("Se conserva el pronóstico ($kind)", cached, state.forecast)
                assertEquals(WeatherFreshness.STALE, state.freshness)
                assertEquals(WeatherCoverage.COMPLETE, state.shiftSummary?.coverage)
                assertEquals(8, state.shiftHours.size)
                assertNull(state.infoMessage)
                assertEquals(cached, harness.runtime.latestForShift(harness.shift))
            }
        } finally {
            harness.close()
        }
    }

    // A245, C084
    @Test
    fun providerWaitRequestIsShownAndBlocksAnImmediateManualRetry() = runBlocking<Unit> {
        val harness = harness("rate-limit")
        try {
            harness.preferences.enableAfterExplanation()
            harness.client.answer = {
                WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.RATE_LIMITED, Duration.ofMinutes(2)))
            }
            val viewModel = harness.enabledViewModel()
            val message = FAILURE_MESSAGES.getValue(WeatherFailureKind.RATE_LIMITED)

            onMain { viewModel.openShift(harness.shift.id) }
            val limited = viewModel.awaitState("límite del proveedor") {
                it.errorMessage == message && !it.isRefreshing && !it.isLoading
            }
            assertNull(limited.forecast)
            assertEquals(1, harness.client.calls)
            assertEquals(
                STAGE14_WEATHER_NOW.plus(Duration.ofMinutes(2)).toEpochMilli(),
                harness.preferences.current().retryAfterUntilEpochMillis,
            )

            onMain {
                viewModel.clearMessage()
                viewModel.manualRefresh()
            }
            viewModel.awaitState("reintento inmediato bloqueado") {
                it.errorMessage == message && !it.isRefreshing
            }
            assertEquals(1, harness.client.calls)
        } finally {
            harness.close()
        }
    }

    // A245
    @Test
    fun forecastThatCannotBeSavedIsReportedAndNotShownAsValid() = runBlocking<Unit> {
        val harness = harness("cache-error")
        try {
            harness.preferences.enableAfterExplanation()
            // A regular file where the cache directory should be: every write fails.
            File(harness.filesDir, WeatherCacheStore.DIRECTORY_NAME).writeText("bloqueo ficticio")
            harness.client.answer = { WeatherClientResult.Success(stage14WeatherForecast(it)) }
            val viewModel = harness.enabledViewModel()

            onMain { viewModel.openShift(harness.shift.id) }
            val state = viewModel.awaitState("error al guardar") {
                it.errorMessage == FAILURE_MESSAGES.getValue(WeatherFailureKind.CACHE_ERROR) &&
                    !it.isRefreshing && !it.isLoading
            }

            assertEquals(1, harness.client.calls)
            assertNull(state.forecast)
            assertNull(state.shiftSummary)
            assertNull(state.infoMessage)
        } finally {
            harness.close()
        }
    }

    // A242, C084
    @Test
    fun shiftThatAlreadyEndedIsMarkedAsNotApplicableAndNeverDownloadsOrShowsCache() = runBlocking<Unit> {
        val objective = stage14WeatherObjective()
        // Ends exactly now: the first instant in which the forecast no longer applies.
        val ended = stage14WeatherShift(
            objective,
            startAt = STAGE14_WEATHER_NOW.minus(Duration.ofHours(8)),
            endAt = STAGE14_WEATHER_NOW,
        )
        val harness = Stage14WeatherHarness(
            temporaryFolder.newFolder("ended-shift"),
            Stage14ScriptedWeatherClient { WeatherClientResult.Success(stage14WeatherForecast(it)) },
            objective,
            ended,
        )
        try {
            harness.preferences.enableAfterExplanation()
            harness.writeCache(
                stage14WeatherForecast(harness.location, from = ended.startAt, hourCount = 8),
            )
            val viewModel = harness.enabledViewModel()

            onMain { viewModel.openShift(ended.id) }
            val state = viewModel.awaitState("jornada fuera de ventana") {
                it.selectedShift?.id == ended.id && it.ineligibleReason != null && !it.isLoading
            }
            assertEquals("El pronóstico ya no aplica a esta jornada.", state.ineligibleReason)
            assertNull(state.forecast)
            assertNull(state.shiftSummary)
            assertTrue(state.shiftHours.isEmpty())

            onMain { viewModel.manualRefresh() }
            val afterManual = viewModel.awaitState("actualización manual fuera de ventana") { !it.isRefreshing }
            assertNull(afterManual.forecast)
            assertNull(afterManual.infoMessage)

            onMain { viewModel.loadBriefs(setOf(ended.id)) }
            val afterBriefs = viewModel.awaitState("resumen del día fuera de ventana") {
                it.loadingBriefIds.isEmpty()
            }
            assertTrue(afterBriefs.shiftBriefs.isEmpty())
            assertEquals(0, harness.client.calls)
        } finally {
            harness.close()
        }
    }

    private fun harness(name: String): Stage14WeatherHarness = Stage14WeatherHarness(
        temporaryFolder.newFolder(name),
        Stage14ScriptedWeatherClient { _: WeatherLocation ->
            WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.UNKNOWN))
        },
    )

    private suspend fun <T> onMain(block: () -> T): T = stage14WeatherOnMain(block)

    private fun cacheFiles(filesDir: File): List<File> =
        File(filesDir, WeatherCacheStore.DIRECTORY_NAME).listFiles().orEmpty().filter { it.extension == "cache" }

    private companion object {
        const val UPDATED = "Pronóstico actualizado."
        const val CLEARED = "Pronósticos guardados borrados."

        // Literal copies of what WeatherViewModel shows for each failure kind.
        val FAILURE_MESSAGES: Map<WeatherFailureKind, String> = linkedMapOf(
            WeatherFailureKind.OFFLINE_OR_TIMEOUT to "No pudimos conectar. Se conserva el último pronóstico disponible.",
            WeatherFailureKind.RATE_LIMITED to "Open-Meteo pidió esperar antes de volver a consultar.",
            WeatherFailureKind.CLIENT_ERROR to "El proveedor rechazó la solicitud meteorológica.",
            WeatherFailureKind.SERVER_ERROR to "El proveedor meteorológico no está disponible temporalmente.",
            WeatherFailureKind.INVALID_RESPONSE to "El proveedor devolvió un pronóstico que MiGuardia no puede validar.",
            WeatherFailureKind.CACHE_ERROR to "El pronóstico llegó, pero no se pudo guardar de forma segura.",
            WeatherFailureKind.UNKNOWN to "No pudimos actualizar el pronóstico.",
        )
    }
}
