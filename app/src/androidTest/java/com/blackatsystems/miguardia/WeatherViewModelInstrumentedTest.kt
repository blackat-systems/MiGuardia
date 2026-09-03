package com.blackatsystems.miguardia.ui.weather

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.blackatsystems.miguardia.weather.WeatherCacheStore
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherRuntime
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun openingShiftBWithoutCacheNeverRetainsForecastOrFreshnessFromShiftA() = runBlocking {
        val filesDir = temporaryFolder.newFolder("weather-view-model")
        val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val preferences = WeatherPreferencesStore(
                File(filesDir, "weather-test.preferences_pb"),
                preferenceScope,
            )
            preferences.enableAfterExplanation()
            preferences.setRetryAfterUntil(NOW.plusSeconds(3_600).toEpochMilli())

            val objectiveA = objective("objective-a", "Objetivo A", -31.4201, -64.1888)
            val objectiveB = objective("objective-b", "Objetivo B", -32.0, -65.0)
            val shiftA = shift("shift-a", objectiveA)
            val shiftB = shift("shift-b", objectiveB)
            val locationA = WeatherLocation(
                id = objectiveA.id.toString(),
                displayName = objectiveA.fullName,
                latitude = requireNotNull(objectiveA.weatherLatitude),
                longitude = requireNotNull(objectiveA.weatherLongitude),
                zoneId = shiftA.zoneId,
            )
            val forecastA = forecast(locationA)
            WeatherCacheStore.forLocation(filesDir, locationA).write(forecastA)

            val runtime = WeatherRuntime(
                context = TestFilesContext(filesDir),
                preferences = preferences,
                objectives = FakeObjectiveRepository(listOf(objectiveA, objectiveB)),
                clock = CLOCK,
            )
            val viewModel = WeatherViewModel(
                runtime = runtime,
                shifts = FakeShiftRepository(listOf(shiftA, shiftB)),
                vacations = EmptyVacationRepository,
            )

            withTimeout(5_000) {
                viewModel.uiState.filter { it.preferences.enabled }.first()
            }
            viewModel.openShift(shiftA.id)
            val stateA = withTimeout(5_000) {
                viewModel.uiState.filter { state ->
                    state.selectedShift?.id == shiftA.id &&
                        state.forecast == forecastA &&
                        state.freshness != null &&
                        !state.isLoading &&
                        !state.isRefreshing
                }.first()
            }
            assertEquals(forecastA, stateA.forecast)
            assertNotNull(stateA.freshness)

            viewModel.openShift(shiftB.id)
            val stateB = withTimeout(5_000) {
                viewModel.uiState.filter { state ->
                    state.selectedShift?.id == shiftB.id &&
                        state.errorMessage != null &&
                        !state.isLoading &&
                        !state.isRefreshing
                }.first()
            }

            assertNull(stateB.forecast)
            assertNull(stateB.freshness)
        } finally {
            preferenceScope.cancel()
        }
    }

    @Test
    fun invalidatingOneObjectiveNeverDropsOrRepopulatesAnotherObjectivesBrief() = runBlocking {
        val filesDir = temporaryFolder.newFolder("weather-brief-invalidation")
        val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val preferences = WeatherPreferencesStore(
                File(filesDir, "weather-briefs.preferences_pb"),
                preferenceScope,
            )
            preferences.enableAfterExplanation()
            preferences.setRetryAfterUntil(NOW.plusSeconds(3_600).toEpochMilli())
            val objectiveA = objective("objective-a", "Objetivo A", -31.4201, -64.1888)
            val objectiveB = objective("objective-b", "Objetivo B", -32.0, -65.0)
            val shiftA = shift("shift-a", objectiveA)
            val shiftB = shift("shift-b", objectiveB)
            val locationA = WeatherLocation(
                objectiveA.id.toString(),
                objectiveA.fullName,
                requireNotNull(objectiveA.weatherLatitude),
                requireNotNull(objectiveA.weatherLongitude),
                shiftA.zoneId,
            )
            val locationB = WeatherLocation(
                objectiveB.id.toString(),
                objectiveB.fullName,
                requireNotNull(objectiveB.weatherLatitude),
                requireNotNull(objectiveB.weatherLongitude),
                shiftB.zoneId,
            )
            WeatherCacheStore.forLocation(filesDir, locationA).write(forecast(locationA))
            WeatherCacheStore.forLocation(filesDir, locationB).write(forecast(locationB))
            val runtime = WeatherRuntime(
                context = TestFilesContext(filesDir),
                preferences = preferences,
                objectives = FakeObjectiveRepository(listOf(objectiveA, objectiveB)),
                clock = CLOCK,
            )
            val viewModel = WeatherViewModel(
                runtime = runtime,
                shifts = FakeShiftRepository(listOf(shiftA, shiftB)),
                vacations = EmptyVacationRepository,
            )

            withTimeout(5_000) { viewModel.uiState.filter { it.preferences.enabled }.first() }
            viewModel.loadBriefs(setOf(shiftA.id, shiftB.id))
            withTimeout(5_000) {
                viewModel.uiState.filter { state ->
                    state.loadingBriefIds.isEmpty() &&
                        state.shiftBriefs.keys == setOf(shiftA.id, shiftB.id)
                }.first()
            }

            runtime.clearCacheForObjective(objectiveB.id)

            val afterInvalidation = withTimeout(5_000) {
                viewModel.uiState.filter { state ->
                    state.loadingBriefIds.isEmpty() && state.shiftBriefs.keys == setOf(shiftA.id)
                }.first()
            }
            assertTrue(shiftA.id in afterInvalidation.shiftBriefs)
            assertTrue(shiftB.id !in afterInvalidation.shiftBriefs)
        } finally {
            preferenceScope.cancel()
        }
    }

    private fun objective(key: String, name: String, latitude: Double, longitude: Double): Objective = Objective(
        id = UUID.nameUUIDFromBytes(key.toByteArray()),
        fullName = name,
        abbreviation = key.takeLast(1).uppercase(),
        address = null,
        note = null,
        isActive = true,
        createdAt = NOW.minusSeconds(3_600),
        updatedAt = NOW.minusSeconds(3_600),
        weatherLatitude = latitude,
        weatherLongitude = longitude,
    )

    private fun shift(key: String, objective: Objective): Shift = Shift(
        id = UUID.nameUUIDFromBytes(key.toByteArray()),
        startAt = SHIFT_START,
        endAt = SHIFT_END,
        zoneId = ZoneOffset.UTC,
        localStartDate = LocalDate.of(2026, 9, 3),
        objectiveNameSnapshot = objective.fullName,
        objectiveAbbreviationSnapshot = objective.abbreviation,
        objectiveAddressSnapshot = null,
        startTimeSnapshot = LocalTime.of(8, 0),
        endTimeSnapshot = LocalTime.of(16, 0),
        colorArgbSnapshot = 0xff336699.toInt(),
        position = null,
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = objective.id,
        createdAt = NOW.minusSeconds(3_600),
        updatedAt = NOW.minusSeconds(3_600),
    )

    private fun forecast(location: WeatherLocation): WeatherForecast {
        val hour = WeatherHour(
            validFrom = SHIFT_START,
            validUntilExclusive = SHIFT_END,
            temperatureCelsius = 20.0,
            apparentTemperatureCelsius = 20.0,
            precipitationMillimeters = 0.0,
            precipitationProbabilityPercent = 0,
            weatherCode = 0,
            condition = WeatherCondition.CLEAR,
            windSpeedKmh = 5.0,
            windGustKmh = 8.0,
            windDirectionDegrees = 90.0,
        )
        return WeatherForecast(
            providerId = "open-meteo",
            location = location,
            fetchedAt = NOW,
            coverageStart = SHIFT_START,
            coverageEndExclusive = SHIFT_END,
            hours = listOf(hour),
        )
    }

    private class TestFilesContext(private val root: File) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = root
    }

    private class FakeObjectiveRepository(objectives: List<Objective>) : ObjectiveRepository {
        private val byId = objectives.associateBy(Objective::id)
        override fun observeActive(): Flow<List<Objective>> = flowOf(byId.values.filter(Objective::isActive))
        override fun observeAll(): Flow<List<Objective>> = flowOf(byId.values.toList())
        override suspend fun getById(id: UUID): Objective? = byId[id]
    }

    private class FakeShiftRepository(shifts: List<Shift>) : ShiftRepository {
        private val byId = shifts.associateBy(Shift::id)
        override fun observeHasAny(): Flow<Boolean> = flowOf(byId.isNotEmpty())
        override fun observeStartingBetween(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<Shift>> =
            emptyFlow()
        override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> = emptyFlow()
        override suspend fun getById(id: UUID): Shift? = byId[id]
    }

    private object EmptyVacationRepository : VacationRepository {
        override fun observeOverlapping(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<Vacation>> =
            flowOf(emptyList())
        override fun observeEndingOnOrAfter(dateInclusive: LocalDate): Flow<List<Vacation>> = emptyFlow()
        override suspend fun getById(id: UUID): Vacation? = null
        override suspend fun insert(vacation: Vacation) = error("No debe escribir")
        override suspend fun update(vacation: Vacation) = error("No debe escribir")
        override suspend fun delete(id: UUID) = error("No debe escribir")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-02T12:00:00Z")
        val SHIFT_START: Instant = Instant.parse("2026-09-03T08:00:00Z")
        val SHIFT_END: Instant = Instant.parse("2026-09-03T16:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    }
}
