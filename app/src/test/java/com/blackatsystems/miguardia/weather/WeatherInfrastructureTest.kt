package com.blackatsystems.miguardia.weather

import com.blackatsystems.miguardia.core.domain.weather.WeatherCondition
import com.blackatsystems.miguardia.core.domain.weather.WeatherForecast
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.WeatherHour
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import com.blackatsystems.miguardia.core.domain.weather.WeatherRefreshResult
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherInfrastructureTest {
    private val now = Instant.parse("2026-08-16T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))
    private val location = WeatherLocation(
        "cordoba-capital",
        "Córdoba Capital, Argentina",
        -31.4201,
        -64.1888,
        ZoneId.of("America/Argentina/Cordoba"),
    )

    @Test
    fun `URL is fixed HTTPS and contains no shift data`() {
        val url = buildOpenMeteoUrl(location)
        assertEquals("https", url.protocol)
        assertEquals("api.open-meteo.com", url.host)
        assertEquals("/v1/forecast", url.path)
        assertTrue(url.query.contains("latitude=-31.4201"))
        assertTrue(url.query.contains("longitude=-64.1888"))
        assertTrue(url.query.contains("forecast_days=16"))
        assertTrue(url.query.contains("timeformat=unixtime"))
        assertFalse(url.toString().contains("shift", ignoreCase = true))
        assertFalse(url.toString().contains("objective", ignoreCase = true))
    }

    @Test
    fun `cache round trip persists canonical snapshot and clears only owned files`() = runBlocking {
        val root = temporaryDirectory()
        val unrelated = File(root, "keep.txt").apply { writeText("keep") }
        val store = WeatherCacheStore(root)
        val expected = forecast(now)
        store.write(expected)
        assertEquals(expected, store.read())
        store.clear()
        assertNull(store.read())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `truncated and unknown cache never crash or become valid`() = runBlocking {
        val root = temporaryDirectory()
        val store = WeatherCacheStore(root)
        File(root, "weather_v1.cache").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store.read())
        File(root, "weather_v1.cache").writeBytes(ByteArray(64) { 7 })
        assertNull(store.read())
    }

    @Test
    fun `orphan temporary file never replaces last valid cache`() = runBlocking {
        val root = temporaryDirectory()
        val store = WeatherCacheStore(root)
        val expected = forecast(now.minusSeconds(1200))
        store.write(expected)
        File(root, "weather_v1.tmp").writeText("partial")
        assertEquals(expected, store.read())
    }

    @Test
    fun `fresh cache avoids network and unit preference never causes fetch`() = runBlocking {
        val fixture = repositoryFixture(forecast(now.minusSeconds(1200)))
        val result = fixture.repository.refreshIfStale()
        assertTrue(result is WeatherRefreshResult.Success && !result.downloaded)
        assertEquals(0, fixture.fetchCount.get())
        fixture.preferences.setUnitSystem(WeatherUnitSystem.FAHRENHEIT)
        assertEquals(0, fixture.fetchCount.get())
    }

    @Test
    fun `simultaneous refreshes coalesce into one download`() = runBlocking {
        val fixture = repositoryFixture(cached = null, fetchDelayMillis = 100)
        val first = async { fixture.repository.refreshIfStale() }
        val second = async { fixture.repository.refreshIfStale() }
        assertTrue(first.await() is WeatherRefreshResult.Success)
        assertTrue(second.await() is WeatherRefreshResult.Success)
        assertEquals(1, fixture.fetchCount.get())
    }

    @Test
    fun `cancelling the refresh owner propagates into the network client`() = runBlocking {
        val root = temporaryDirectory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val preferences = WeatherPreferencesStore(File(root, "${UUID.randomUUID()}.preferences_pb"), scope)
        val started = CompletableDeferred<Unit>()
        val cancelled = AtomicBoolean(false)
        val repository = DefaultWeatherRepository(
            location,
            WeatherForecastClient {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.set(true)
                }
            },
            WeatherCacheStore(root),
            preferences,
            clock,
        )

        val refresh = launch { repository.refreshIfStale(force = true) }
        started.await()
        refresh.cancelAndJoin()

        assertTrue(cancelled.get())
    }

    @Test
    fun `failed refresh never overwrites last valid forecast`() = runBlocking {
        val cached = forecast(now.minusSeconds(7 * 3600))
        val root = temporaryDirectory()
        val cache = WeatherCacheStore(root)
        cache.write(cached)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val preferences = WeatherPreferencesStore(File(root, "${UUID.randomUUID()}.preferences_pb"), scope)
        val repository = DefaultWeatherRepository(
            location,
            WeatherForecastClient {
                WeatherClientResult.Failure(
                    com.blackatsystems.miguardia.core.domain.weather.WeatherFailure(
                        com.blackatsystems.miguardia.core.domain.weather.WeatherFailureKind.INVALID_RESPONSE,
                    ),
                )
            },
            cache,
            preferences,
            clock,
        )
        val result = repository.refreshIfStale()
        assertTrue(result is WeatherRefreshResult.Failure)
        assertEquals(cached, repository.latest())
    }

    @Test
    fun `valid retry after blocks immediate manual retry`() = runBlocking {
        val root = temporaryDirectory()
        val cache = WeatherCacheStore(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val preferences = WeatherPreferencesStore(File(root, "${UUID.randomUUID()}.preferences_pb"), scope)
        val count = AtomicInteger()
        val repository = DefaultWeatherRepository(
            location,
            WeatherForecastClient {
                count.incrementAndGet()
                WeatherClientResult.Failure(
                    com.blackatsystems.miguardia.core.domain.weather.WeatherFailure(
                        com.blackatsystems.miguardia.core.domain.weather.WeatherFailureKind.RATE_LIMITED,
                        Duration.ofMinutes(2),
                    ),
                )
            },
            cache,
            preferences,
            clock,
        )
        repository.refreshIfStale(force = true)
        delay(20)
        val second = repository.refreshIfStale(force = true)
        assertEquals(1, count.get())
        assertTrue(second is WeatherRefreshResult.Failure)
        assertEquals(Duration.ofMinutes(2), (second as WeatherRefreshResult.Failure).error.retryAfter)
    }

    @Test
    fun `preferences start disabled Celsius and without notification enrichment`() = runBlocking {
        val root = temporaryDirectory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = WeatherPreferencesStore(File(root, "${UUID.randomUUID()}.preferences_pb"), scope)
        val initial = preferences.current()
        assertFalse(initial.enabled)
        assertFalse(initial.includeInNotifications)
        assertFalse(initial.providerExplanationAccepted)
        assertEquals(WeatherUnitSystem.CELSIUS, initial.unitSystem)
        preferences.enableAfterExplanation()
        assertTrue(preferences.current().enabled)
        assertTrue(preferences.current().providerExplanationAccepted)
    }

    @Test
    fun `notification formatter requires complete summary and honors Fahrenheit`() {
        val summary = com.blackatsystems.miguardia.core.domain.weather.summarizeShiftWeather(
            now,
            now.plusSeconds(3600),
            forecast(now.minusSeconds(60)),
        )
        val text = formatWeatherForNotification(summary, WeatherUnitSystem.FAHRENHEIT)
        assertNotNull(text)
        assertTrue(text!!.contains("°F"))
        assertTrue(text.contains("Clima:"))
    }

    private suspend fun repositoryFixture(
        cached: WeatherForecast?,
        fetchDelayMillis: Long = 0,
    ): Fixture {
        val root = temporaryDirectory()
        val cache = WeatherCacheStore(root)
        cached?.let { cache.write(it) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val preferences = WeatherPreferencesStore(File(root, "${UUID.randomUUID()}.preferences_pb"), scope)
        val count = AtomicInteger()
        val client = WeatherForecastClient {
            count.incrementAndGet()
            if (fetchDelayMillis > 0) delay(fetchDelayMillis)
            WeatherClientResult.Success(forecast(now))
        }
        val repository = DefaultWeatherRepository(location, client, cache, preferences, clock)
        return Fixture(repository, preferences, count)
    }

    private fun forecast(fetchedAt: Instant): WeatherForecast {
        val hours = (0..2).map { offset ->
            val from = now.plusSeconds(offset * 3600L)
            WeatherHour(
                validFrom = from,
                validUntilExclusive = from.plusSeconds(3600),
                temperatureCelsius = 20.0 + offset,
                apparentTemperatureCelsius = 19.0 + offset,
                precipitationMillimeters = 0.5,
                precipitationProbabilityPercent = 40,
                weatherCode = 61,
                condition = WeatherCondition.RAIN,
                windSpeedKmh = 12.0,
                windGustKmh = 20.0,
                windDirectionDegrees = 180.0,
            )
        }
        return WeatherForecast("open-meteo", location, fetchedAt, hours.first().validFrom, hours.last().validUntilExclusive, hours)
    }

    private fun temporaryDirectory(): File = Files.createTempDirectory("weather-${UUID.randomUUID()}").toFile()

    private data class Fixture(
        val repository: DefaultWeatherRepository,
        val preferences: WeatherPreferencesStore,
        val fetchCount: AtomicInteger,
    )
}
