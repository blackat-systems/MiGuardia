package com.blackatsystems.miguardia.stage14

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.weather.WeatherCondition
import com.blackatsystems.miguardia.core.domain.weather.WeatherForecast
import com.blackatsystems.miguardia.core.domain.weather.WeatherHour
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.ui.weather.WeatherViewModel
import com.blackatsystems.miguardia.weather.WeatherCacheStore
import com.blackatsystems.miguardia.weather.WeatherClientResult
import com.blackatsystems.miguardia.weather.WeatherForecastClient
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherRuntime
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/*
 * Stage 14 · Clima: shared doubles for the instrumented model tests. Everything lives in a
 * TemporaryFolder supplied by the test: no app database, no real preferences, no network.
 */

internal val STAGE14_WEATHER_NOW: Instant = Instant.parse("2026-09-02T12:00:00Z")
internal val STAGE14_WEATHER_SHIFT_START: Instant = Instant.parse("2026-09-03T08:00:00Z")
internal val STAGE14_WEATHER_SHIFT_END: Instant = Instant.parse("2026-09-03T16:00:00Z")
internal val STAGE14_WEATHER_CLOCK: Clock = Clock.fixed(STAGE14_WEATHER_NOW, ZoneOffset.UTC)

/** Counts every request and answers with whatever the test scripted last. Never touches the network. */
internal class Stage14ScriptedWeatherClient(
    @Volatile var answer: suspend (WeatherLocation) -> WeatherClientResult,
) : WeatherForecastClient {
    private val count = AtomicInteger()
    val calls: Int get() = count.get()

    override suspend fun fetch(location: WeatherLocation): WeatherClientResult {
        count.incrementAndGet()
        return answer(location)
    }
}

/** Only a files directory (and the test target's package name, which the QA scope may ask for). */
internal class Stage14WeatherFilesContext(private val root: File) : ContextWrapper(null) {
    private val targetPackage: String = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = root
    override fun getPackageName(): String = targetPackage
}

internal class Stage14WeatherObjectives(objectives: List<Objective>) : ObjectiveRepository {
    private val byId = objectives.associateBy(Objective::id)
    override fun observeActive(): Flow<List<Objective>> = flowOf(byId.values.filter(Objective::isActive))
    override fun observeAll(): Flow<List<Objective>> = flowOf(byId.values.toList())
    override suspend fun getById(id: UUID): Objective? = byId[id]
}

internal class Stage14WeatherShifts(shifts: List<Shift>) : ShiftRepository {
    private val byId = shifts.associateBy(Shift::id)
    override fun observeHasAny(): Flow<Boolean> = flowOf(byId.isNotEmpty())
    override fun observeStartingBetween(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<Shift>> =
        emptyFlow()
    override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> = emptyFlow()
    override suspend fun getById(id: UUID): Shift? = byId[id]
}

internal object Stage14WeatherNoVacations : VacationRepository {
    override fun observeOverlapping(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<Vacation>> =
        flowOf(emptyList())
    override fun observeEndingOnOrAfter(dateInclusive: LocalDate): Flow<List<Vacation>> = emptyFlow()
    override suspend fun getById(id: UUID): Vacation? = null
    override suspend fun insert(vacation: Vacation) = error("No debe escribir")
    override suspend fun update(expected: Vacation, replacement: Vacation) = error("No debe escribir")
    override suspend fun delete(expected: Vacation) = error("No debe escribir")
}

/** Real WeatherRuntime, preferences and cache over a temporary directory, with a scripted client. */
internal class Stage14WeatherHarness(
    val filesDir: File,
    val client: Stage14ScriptedWeatherClient,
    val objective: Objective = stage14WeatherObjective(),
    val shift: Shift = stage14WeatherShift(objective),
) {
    private val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val preferences = WeatherPreferencesStore(File(filesDir, "stage14-weather.preferences_pb"), preferenceScope)
    val location: WeatherLocation = WeatherLocation(
        id = objective.id.toString(),
        displayName = objective.fullName,
        latitude = requireNotNull(objective.weatherLatitude),
        longitude = requireNotNull(objective.weatherLongitude),
        zoneId = shift.zoneId,
    )
    val runtime = WeatherRuntime(
        Stage14WeatherFilesContext(filesDir),
        preferences,
        Stage14WeatherObjectives(listOf(objective)),
        STAGE14_WEATHER_CLOCK,
        client,
    )

    suspend fun writeCache(forecast: WeatherForecast) =
        WeatherCacheStore.forLocation(filesDir, forecast.location).write(forecast)

    /** Built on the main thread, like the app does, and returned once it has read the preferences. */
    suspend fun enabledViewModel(): WeatherViewModel {
        val viewModel = withContext(Dispatchers.Main) {
            WeatherViewModel(runtime, Stage14WeatherShifts(listOf(shift)), Stage14WeatherNoVacations)
        }
        viewModel.awaitState("preferencias con Clima habilitado") { it.preferences.enabled && !it.isLoading }
        return viewModel
    }

    fun close() = preferenceScope.cancel()
}

/** Every call into the model runs on the main thread, where its own coroutines run. */
internal suspend fun <T> stage14WeatherOnMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

internal suspend fun WeatherViewModel.awaitState(
    description: String,
    predicate: (WeatherUiState) -> Boolean,
): WeatherUiState = try {
    withTimeout(5_000) { uiState.first { predicate(it) } }
} catch (_: TimeoutCancellationException) {
    val last = uiState.value
    throw AssertionError(
        "No se alcanzó el estado esperado ($description). Último: error=${last.errorMessage}, " +
            "info=${last.infoMessage}, motivo=${last.ineligibleReason}, pronóstico=${last.forecast != null}, " +
            "cargando=${last.isLoading}, actualizando=${last.isRefreshing}",
    )
}

internal fun stage14WeatherObjective(): Objective = Objective(
    id = UUID.fromString("00000000-0000-0000-0000-000000001401"),
    fullName = "Objetivo ficticio",
    abbreviation = "QA",
    address = null,
    note = null,
    isActive = true,
    createdAt = STAGE14_WEATHER_NOW.minusSeconds(3_600),
    updatedAt = STAGE14_WEATHER_NOW.minusSeconds(3_600),
    weatherLatitude = -31.4201,
    weatherLongitude = -64.1888,
)

internal fun stage14WeatherShift(
    objective: Objective,
    startAt: Instant = STAGE14_WEATHER_SHIFT_START,
    endAt: Instant = STAGE14_WEATHER_SHIFT_END,
): Shift = Shift(
    id = UUID.fromString("00000000-0000-0000-0000-000000001402"),
    startAt = startAt,
    endAt = endAt,
    zoneId = ZoneOffset.UTC,
    localStartDate = startAt.atZone(ZoneOffset.UTC).toLocalDate(),
    objectiveNameSnapshot = objective.fullName,
    objectiveAbbreviationSnapshot = objective.abbreviation,
    objectiveAddressSnapshot = null,
    startTimeSnapshot = LocalTime.of(8, 0),
    endTimeSnapshot = LocalTime.of(16, 0),
    colorArgbSnapshot = 0xff336699.toInt(),
    position = null,
    status = ShiftStatus.PLANNED,
    sourceObjectiveId = objective.id,
    createdAt = STAGE14_WEATHER_NOW.minusSeconds(3_600),
    updatedAt = STAGE14_WEATHER_NOW.minusSeconds(3_600),
)

/**
 * One rainy block per hour starting at [from]. With the defaults it covers the whole fictitious
 * shift: 18–25 °C, at most 75 % chance of rain.
 */
internal fun stage14WeatherForecast(
    location: WeatherLocation,
    fetchedAt: Instant = STAGE14_WEATHER_NOW,
    from: Instant = STAGE14_WEATHER_SHIFT_START,
    hourCount: Int = 8,
    baseTemperature: Double = 18.0,
): WeatherForecast {
    val hours = (0 until hourCount).map { index ->
        val start = from.plusSeconds(index * 3_600L)
        WeatherHour(
            validFrom = start,
            validUntilExclusive = start.plusSeconds(3_600),
            temperatureCelsius = baseTemperature + index,
            apparentTemperatureCelsius = baseTemperature + index - 1.0,
            precipitationMillimeters = 0.5,
            precipitationProbabilityPercent = 40 + index * 5,
            weatherCode = 61,
            condition = WeatherCondition.RAIN,
            windSpeedKmh = 12.0,
            windGustKmh = 20.0,
            windDirectionDegrees = 180.0,
        )
    }
    return WeatherForecast(
        providerId = "open-meteo",
        location = location,
        fetchedAt = fetchedAt,
        coverageStart = hours.first().validFrom,
        coverageEndExclusive = hours.last().validUntilExclusive,
        hours = hours,
    )
}
