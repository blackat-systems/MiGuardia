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
    private val objectiveId = UUID.fromString("00000000-0000-0000-0000-000000000901")
    private val location = WeatherLocation(
        objectiveId.toString(),
        "Hospital ficticio",
        -34.6037,
        -58.3816,
        ZoneId.of("America/Argentina/Buenos_Aires"),
    )

    @Test
    fun `URL uses objective coordinates and zone without shift data`() {
        val url = buildOpenMeteoUrl(location)
        assertEquals("https", url.protocol)
        assertEquals("api.open-meteo.com", url.host)
        assertEquals("/v1/forecast", url.path)
        assertTrue(url.query.contains("latitude=-34.6037"))
        assertTrue(url.query.contains("longitude=-58.3816"))
        assertTrue(url.query.contains("timezone=America%2FArgentina%2FBuenos_Aires"))
        assertTrue(url.query.contains("forecast_days=16"))
        assertTrue(url.query.contains("timeformat=unixtime"))
        assertFalse(url.toString().contains("shift", ignoreCase = true))
        assertFalse(url.toString().contains("objective", ignoreCase = true))
        assertFalse(url.toString().contains(location.id))
        assertFalse(url.toString().contains(location.displayName))
    }

    @Test
    fun `objective caches stay isolated and clearing one preserves the others`() = runBlocking {
        val filesDir = temporaryDirectory()
        val cacheRoot = File(filesDir, WeatherCacheStore.DIRECTORY_NAME).apply { mkdirs() }
        val unrelated = File(cacheRoot, "keep.txt").apply { writeText("keep") }
        val otherLocation = location.copy(
            id = UUID.fromString("00000000-0000-0000-0000-000000000902").toString(),
            displayName = "Clínica ficticia",
            latitude = -32.9442,
            longitude = -60.6505,
        )
        val firstStore = WeatherCacheStore.forLocation(filesDir, location)
        val secondStore = WeatherCacheStore.forLocation(filesDir, otherLocation)
        val firstForecast = forecast(now, location)
        val secondForecast = forecast(now.minusSeconds(60), otherLocation)

        firstStore.write(firstForecast)
        secondStore.write(secondForecast)

        assertEquals(firstForecast, firstStore.read())
        assertEquals(secondForecast, secondStore.read())
        firstStore.clear()
        assertNull(firstStore.read())
        assertEquals(secondForecast, secondStore.read())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `two locations of the same objective use different cache files`() = runBlocking {
        val filesDir = temporaryDirectory()
        val previousLocation = location.copy(latitude = -31.4201, longitude = -64.1888)
        val otherObjective = location.copy(
            id = UUID.fromString("00000000-0000-0000-0000-000000000902").toString(),
            displayName = "Otro objetivo ficticio",
        )
        val previousStore = WeatherCacheStore.forLocation(filesDir, previousLocation)
        val currentStore = WeatherCacheStore.forLocation(filesDir, location)
        val otherStore = WeatherCacheStore.forLocation(filesDir, otherObjective)
        val previousForecast = forecast(now.minusSeconds(120), previousLocation)
        val currentForecast = forecast(now, location)
        val otherForecast = forecast(now, otherObjective)

        previousStore.write(previousForecast)
        currentStore.write(currentForecast)
        otherStore.write(otherForecast)

        assertEquals(previousForecast, previousStore.read())
        assertEquals(currentForecast, currentStore.read())
        assertEquals(
            3,
            File(filesDir, WeatherCacheStore.DIRECTORY_NAME)
                .listFiles()
                .orEmpty()
                .count { it.extension == "cache" },
        )

        WeatherCacheStore.clearObjective(filesDir, location.id)

        assertNull(previousStore.read())
        assertNull(currentStore.read())
        assertEquals(otherForecast, otherStore.read())
    }

    @Test
    fun `renaming an objective creates a different in-memory repository identity`() {
        val renamed = location.copy(displayName = "Hospital ficticio renombrado")

        assertTrue(weatherRepositoryKey(location) != weatherRepositoryKey(renamed))
    }

    @Test
    fun `clear all removes every objective and legacy weather cache but preserves unrelated files`() = runBlocking {
        val filesDir = temporaryDirectory()
        val cacheRoot = File(filesDir, WeatherCacheStore.DIRECTORY_NAME).apply { mkdirs() }
        val unrelated = File(cacheRoot, "keep.txt").apply { writeText("keep") }
        val firstStore = WeatherCacheStore.forLocation(filesDir, location)
        val secondLocation = location.copy(
            id = UUID.fromString("00000000-0000-0000-0000-000000000902").toString(),
            displayName = "Clínica ficticia",
        )
        val secondStore = WeatherCacheStore.forLocation(filesDir, secondLocation)
        val legacyStore = WeatherCacheStore.inFilesDir(filesDir)
        firstStore.write(forecast(now))
        secondStore.write(forecast(now.minusSeconds(60)))
        legacyStore.write(forecast(now.minusSeconds(120)))

        WeatherCacheStore.clearAll(filesDir)

        assertNull(firstStore.read())
        assertNull(secondStore.read())
        assertNull(legacyStore.read())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `truncated and unknown objective cache never crash or become valid`() = runBlocking {
        val filesDir = temporaryDirectory()
        val store = WeatherCacheStore.forLocation(filesDir, location)
        store.write(forecast(now))
        val target = objectiveCacheFile(filesDir, "cache")
        target.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store.read())
        target.writeBytes(ByteArray(64) { 7 })
        assertNull(store.read())
    }

    @Test
    fun `orphan objective temporary file never replaces last valid cache`() = runBlocking {
        val filesDir = temporaryDirectory()
        val store = WeatherCacheStore.forLocation(filesDir, location)
        val expected = forecast(now.minusSeconds(1200))
        store.write(expected)
        val target = objectiveCacheFile(filesDir, "cache")
        File(target.parentFile, target.name.removeSuffix(".cache") + ".tmp").writeText("partial")
        assertEquals(expected, store.read())
    }

    @Test
    fun `repository rejects cache from old coordinates of the same objective`() = runBlocking {
        val filesDir = temporaryDirectory()
        val cache = WeatherCacheStore.forLocation(filesDir, location)
        val previousLocation = location.copy(latitude = -31.4201, longitude = -64.1888)
        cache.write(forecast(now, previousLocation))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val preferences = WeatherPreferencesStore(File(filesDir, "${UUID.randomUUID()}.preferences_pb"), scope)
        val repository = DefaultWeatherRepository(
            location,
            WeatherForecastClient { error("latest() no debe iniciar una descarga") },
            cache,
            preferences,
            clock,
        )

        assertNull(repository.latest())
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
            WeatherCacheStore.forLocation(root, location),
            preferences,
            clock,
        )

        val refresh = launch { repository.refreshIfStale(force = true) }
        started.await()
        refresh.cancelAndJoin()

        assertTrue(cancelled.get())
    }

    @Test
    fun `clearing cache cancels an active download and leaves no forecast behind`() = runBlocking {
        val root = temporaryDirectory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val preferences = WeatherPreferencesStore(File(root, "${UUID.randomUUID()}.preferences_pb"), scope)
        val started = CompletableDeferred<Unit>()
        val repository = DefaultWeatherRepository(
            location,
            WeatherForecastClient {
                started.complete(Unit)
                awaitCancellation()
            },
            WeatherCacheStore.forLocation(root, location),
            preferences,
            clock,
        )
        val refresh = launch { repository.refreshIfStale(force = true) }
        started.await()

        repository.clearCache()
        refresh.join()

        assertTrue(refresh.isCancelled)
        assertNull(repository.latest())
    }

    @Test
    fun `failed refresh never overwrites last valid forecast`() = runBlocking {
        val cached = forecast(now.minusSeconds(7 * 3600))
        val root = temporaryDirectory()
        val cache = WeatherCacheStore.forLocation(root, location)
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
        val cache = WeatherCacheStore.forLocation(root, location)
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
        val cache = WeatherCacheStore.forLocation(root, location)
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

    private fun forecast(fetchedAt: Instant, atLocation: WeatherLocation = location): WeatherForecast {
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
        return WeatherForecast("open-meteo", atLocation, fetchedAt, hours.first().validFrom, hours.last().validUntilExclusive, hours)
    }

    private fun temporaryDirectory(): File = Files.createTempDirectory("weather-${UUID.randomUUID()}").toFile()

    private fun objectiveCacheFile(filesDir: File, extension: String): File =
        requireNotNull(
            File(filesDir, WeatherCacheStore.DIRECTORY_NAME)
                .listFiles()
                ?.singleOrNull { it.name.startsWith(WeatherCacheStore.OWNED_PREFIX) && it.extension == extension },
        )

    private data class Fixture(
        val repository: DefaultWeatherRepository,
        val preferences: WeatherPreferencesStore,
        val fetchCount: AtomicInteger,
    )
}
