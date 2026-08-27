package com.blackatsystems.miguardia.ui.summary

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.repository.AvailabilityWindowRepository
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.IndependentExtraWorkRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryEssentials
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryProjection
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryViewModelTest {
    @Test
    fun realObserverReactsToEveryConfiguredLocalSource() = runBlocking {
        fun <T> replaying(value: T): MutableSharedFlow<T> = MutableSharedFlow<T>(replay = 1).also { flow ->
            check(flow.tryEmit(value))
        }
        val history = configurationHistory()
        val catalog = WorkCatalog(TIMELINE_ID, WorkSector.PRIVATE_SECURITY, emptyList(), emptyList(), emptyList(), emptyList())
        val configurations = replaying<WorkConfigurationHistory?>(history)
        val catalogs = replaying(catalog)
        val shifts = replaying<List<V2ShiftWrite>>(emptyList())
        val actuals = replaying<Map<UUID, ShiftActualAggregate>>(emptyMap())
        val extras = replaying<List<IndependentExtraWorkRecord>>(emptyList())
        val availability = replaying<List<AvailabilityWindowRecord>>(emptyList())
        val holidays = replaying<List<Holiday>>(emptyList())
        val medicalLeaves = replaying<List<MedicalLeave>>(emptyList())
        val vacations = replaying<List<Vacation>>(emptyList())
        val statuses = replaying<List<ExplicitDayStatus>>(emptyList())
        val observer = SummaryObserver(
            configurations = repository(WorkConfigurationRepository::class.java, "observe" to configurations),
            catalogs = repository(WorkCatalogRepository::class.java, "observeCatalog" to catalogs),
            shifts = repository(V2ShiftRepository::class.java, "observeAll" to shifts),
            actuals = repository(ShiftActualRepository::class.java, "observeAllActuals" to actuals),
            extras = repository(IndependentExtraWorkRepository::class.java, "observeAll" to extras),
            availability = repository(AvailabilityWindowRepository::class.java, "observeAll" to availability),
            holidays = repository(HolidayRepository::class.java, "observeBetween" to holidays),
            medicalLeaves = repository(MedicalLeaveRepository::class.java, "observeIntersecting" to medicalLeaves),
            vacations = repository(VacationRepository::class.java, "observeOverlapping" to vacations),
            explicitStatuses = repository(ExplicitDayStatusRepository::class.java, "observeBetween" to statuses),
            clock = CLOCK,
            zoneId = ZoneOffset.UTC,
        )
        var emissionCount = 0
        val collection = launch(Dispatchers.Unconfined) {
            observer.observe(MONTH).collect { emissionCount++ }
        }
        assertEquals(1, emissionCount)

        suspend fun <T> assertReacts(flow: MutableSharedFlow<T>, value: T) {
            val before = emissionCount
            flow.emit(value)
            yield()
            assertTrue("La fuente no volvió a proyectar el Resumen", emissionCount > before)
        }

        assertReacts(shifts, emptyList())
        assertReacts(actuals, emptyMap())
        assertReacts(extras, emptyList())
        assertReacts(availability, emptyList())
        assertReacts(catalogs, catalog)
        assertReacts(holidays, emptyList())
        assertReacts(medicalLeaves, emptyList())
        assertReacts(vacations, emptyList())
        assertReacts(statuses, emptyList())
        assertReacts(configurations, history)

        collection.cancel()
        collection.join()
    }

    @Test
    fun realObserverReactsToInsertEditDeleteAndCancelsAllSourceCollection() = runBlocking {
        val history = configurationHistory()
        val catalog = WorkCatalog(TIMELINE_ID, WorkSector.PRIVATE_SECURITY, emptyList(), emptyList(), emptyList(), emptyList())
        val availabilityState = MutableStateFlow<List<AvailabilityWindowRecord>>(emptyList())
        var availabilitySubscriptions = 0
        var availabilityCompletions = 0
        val trackedAvailability = availabilityState
            .onStart { availabilitySubscriptions++ }
            .onCompletion { availabilityCompletions++ }
        val emptyListFlow = MutableStateFlow(emptyList<Any>())
        val observer = SummaryObserver(
            configurations = repository(
                WorkConfigurationRepository::class.java,
                "observe" to MutableStateFlow<WorkConfigurationHistory?>(history),
            ),
            catalogs = repository(
                WorkCatalogRepository::class.java,
                "observeCatalog" to MutableStateFlow(catalog),
            ),
            shifts = repository(V2ShiftRepository::class.java, "observeAll" to emptyListFlow),
            actuals = repository(
                ShiftActualRepository::class.java,
                "observeAllActuals" to MutableStateFlow(emptyMap<UUID, Any>()),
            ),
            extras = repository(IndependentExtraWorkRepository::class.java, "observeAll" to emptyListFlow),
            availability = repository(AvailabilityWindowRepository::class.java, "observeAll" to trackedAvailability),
            holidays = repository(HolidayRepository::class.java, "observeBetween" to emptyListFlow),
            medicalLeaves = repository(MedicalLeaveRepository::class.java, "observeIntersecting" to emptyListFlow),
            vacations = repository(VacationRepository::class.java, "observeOverlapping" to emptyListFlow),
            explicitStatuses = repository(ExplicitDayStatusRepository::class.java, "observeBetween" to emptyListFlow),
            clock = CLOCK,
            zoneId = ZoneOffset.UTC,
        )
        val emissions = mutableListOf<MonthlySummaryProjection>()
        val collection = launch(Dispatchers.Unconfined) {
            observer.observe(MONTH).take(4).toList(emissions)
        }
        val inserted = availability("observer-inserted", 8, 10)
        val edited = availability("observer-inserted", 8, 11)

        assertEquals(1, emissions.size)
        availabilityState.value = listOf(inserted)
        availabilityState.value = listOf(edited)
        availabilityState.value = emptyList()
        collection.join()

        assertEquals(listOf(null, 120L, 180L, null), emissions.map { it.availability?.programmed?.value })
        assertEquals(1, availabilitySubscriptions)
        assertEquals(1, availabilityCompletions)
    }

    @Test
    fun observationReactsOnlyWhileSummaryIsActiveAndCancelsOnMonthOrDestinationChange() {
        val observer = TrackingObserver()
        val dataStore = ControllablePreferencesDataStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(observer, dataStore, SavedStateHandle(), scope)

        assertTrue(observer.subscriptions.isEmpty())
        viewModel.setActive(true)
        assertEquals(listOf(MONTH), observer.subscriptions)
        assertSame(observer.current(MONTH), viewModel.uiState.value.projection)

        val refreshed = projection(MONTH, marker = true)
        observer.emit(MONTH, refreshed)
        assertSame(refreshed, viewModel.uiState.value.projection)

        viewModel.setActive(false)
        assertEquals(listOf(MONTH), observer.cancellations)
        val hiddenUpdate = projection(MONTH, marker = false)
        observer.emit(MONTH, hiddenUpdate)
        assertSame(refreshed, viewModel.uiState.value.projection)

        viewModel.setActive(true)
        assertSame(hiddenUpdate, viewModel.uiState.value.projection)
        viewModel.showNextMonth()
        assertEquals(listOf(MONTH, MONTH, MONTH.plusMonths(1)), observer.subscriptions)
        assertEquals(listOf(MONTH, MONTH), observer.cancellations)

        viewModel.setActive(false)
        assertEquals(listOf(MONTH, MONTH, MONTH.plusMonths(1)), observer.cancellations)
        scope.cancel()
    }

    @Test
    fun temporalUpdatesSkipFinishedSourcesAndUseOnlyFutureOrActiveBoundaries() {
        val now = Instant.parse("2026-08-27T12:00:20Z")
        val finished = Instant.parse("2026-08-27T10:00:00Z") to Instant.parse("2026-08-27T11:00:00Z")
        val future = Instant.parse("2026-08-27T15:00:00Z") to Instant.parse("2026-08-27T17:00:00Z")
        val active = Instant.parse("2026-08-27T11:00:00Z") to Instant.parse("2026-08-27T14:00:00Z")
        val endingSoon = Instant.parse("2026-08-27T11:00:00Z") to Instant.parse("2026-08-27T12:00:40Z")

        assertNull(nextSummaryTemporalUpdate(now, emptyList()))
        assertNull(nextSummaryTemporalUpdate(now, listOf(finished)))
        assertEquals(future.first, nextSummaryTemporalUpdate(now, listOf(finished, future)))
        assertEquals(Instant.parse("2026-08-27T12:01:00Z"), nextSummaryTemporalUpdate(now, listOf(active)))
        assertEquals(endingSoon.second, nextSummaryTemporalUpdate(now, listOf(active, endingSoon)))
    }

    @Test
    fun failedPreferenceWriteIsReportedAndTheSameOperationCanBeRetried() = runBlocking {
        val observer = TrackingObserver()
        val dataStore = ControllablePreferencesDataStore(failNextWrite = true)
        val preferences = SummaryPreferencesStore(dataStore)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = SummaryViewModel(
            observer,
            preferences,
            CLOCK,
            ZoneOffset.UTC,
            SavedStateHandle(),
            scope,
        )

        viewModel.setFamilyVisible(SummaryOptionalFamily.NIGHTS, visible = false)
        assertTrue(viewModel.uiState.value.preferenceErrorMessage.orEmpty().contains("reintentarlo"))
        assertEquals(1, dataStore.writeAttempts)

        viewModel.retryPreferenceWrite()
        assertNull(viewModel.uiState.value.preferenceErrorMessage)
        assertEquals(2, dataStore.writeAttempts)
        assertFalse(preferences.current().isVisible(SummaryOptionalFamily.NIGHTS))
        scope.cancel()
    }

    @Test
    fun consecutiveFailedPreferenceChangesRemainQueuedUntilBothCanBeSaved() = runBlocking {
        val dataStore = ControllablePreferencesDataStore(failuresBeforeSuccess = 2)
        val preferences = SummaryPreferencesStore(dataStore)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = SummaryViewModel(
            TrackingObserver(),
            preferences,
            CLOCK,
            ZoneOffset.UTC,
            SavedStateHandle(),
            scope,
        )

        viewModel.setFamilyVisible(SummaryOptionalFamily.NIGHTS, visible = false)
        viewModel.setFamilyVisible(SummaryOptionalFamily.HOLIDAYS, visible = false)
        assertEquals(1, dataStore.writeAttempts)

        viewModel.retryPreferenceWrite()
        assertEquals(2, dataStore.writeAttempts)
        assertTrue(viewModel.uiState.value.preferenceErrorMessage.orEmpty().contains("reintentarlo"))

        viewModel.retryPreferenceWrite()
        assertEquals(4, dataStore.writeAttempts)
        assertNull(viewModel.uiState.value.preferenceErrorMessage)
        assertFalse(preferences.current().isVisible(SummaryOptionalFamily.NIGHTS))
        assertFalse(preferences.current().isVisible(SummaryOptionalFamily.HOLIDAYS))
        scope.cancel()
    }

    @Test
    fun overviewScrollSurvivesSubsurfaceNavigationAndSavedStateRecreation() {
        val handle = SavedStateHandle()
        val dataStore = ControllablePreferencesDataStore()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val first = viewModel(TrackingObserver(), dataStore, handle, firstScope)

        first.updateOverviewScrollPosition(734)
        first.openPersonalization()
        first.back()
        assertEquals(734, first.uiState.value.overviewScrollPosition)

        val recreatedScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val recreated = viewModel(TrackingObserver(), dataStore, handle, recreatedScope)
        assertEquals(734, recreated.uiState.value.overviewScrollPosition)
        firstScope.cancel()
        recreatedScope.cancel()
    }

    private fun viewModel(
        observer: MonthlySummaryObserver,
        dataStore: DataStore<Preferences>,
        handle: SavedStateHandle,
        scope: CoroutineScope,
    ): SummaryViewModel = SummaryViewModel(
        observer,
        SummaryPreferencesStore(dataStore),
        CLOCK,
        ZoneOffset.UTC,
        handle,
        scope,
    )

    private class TrackingObserver : MonthlySummaryObserver {
        val subscriptions = mutableListOf<YearMonth>()
        val cancellations = mutableListOf<YearMonth>()
        private val sources = mutableMapOf<YearMonth, MutableStateFlow<MonthlySummaryProjection>>()

        override fun observe(month: YearMonth): Flow<MonthlySummaryProjection> = source(month)
            .onStart { subscriptions += month }
            .onCompletion { cancellations += month }

        fun current(month: YearMonth): MonthlySummaryProjection = source(month).value

        fun emit(month: YearMonth, projection: MonthlySummaryProjection) {
            source(month).value = projection
        }

        private fun source(month: YearMonth): MutableStateFlow<MonthlySummaryProjection> =
            sources.getOrPut(month) { MutableStateFlow(projection(month, marker = false)) }
    }

    private class ControllablePreferencesDataStore(
        var failNextWrite: Boolean = false,
        failuresBeforeSuccess: Int = if (failNextWrite) 1 else 0,
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        private var failuresRemaining = failuresBeforeSuccess
        var writeAttempts: Int = 0
            private set

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writeAttempts++
            if (failuresRemaining > 0) {
                failuresRemaining--
                failNextWrite = failuresRemaining > 0
                throw IOException("fallo de escritura ficticio")
            }
            return transform(state.value).also { state.value = it }
        }
    }

    private fun configurationHistory(): WorkConfigurationHistory = WorkConfigurationHistory(
        EffectiveDateTimeline(
            TIMELINE_ID,
            listOf(
                EffectiveRevision(
                    REVISION_ID,
                    LocalDate.of(2026, 1, 1),
                    WorkConfiguration(
                        WorkSector.PRIVATE_SECURITY,
                        HoursReference.NotUsed,
                        AvailabilityLabel.ON_CALL_RETAINER,
                    ),
                ),
            ),
        ),
        PerPeriodHoursValues(emptyList()),
    )

    private fun availability(key: String, startHour: Int, endHour: Int): AvailabilityWindowRecord =
        AvailabilityWindowRecord(
            id = UUID.nameUUIDFromBytes(key.toByteArray()),
            timelineId = TIMELINE_ID,
            sector = WorkSector.PRIVATE_SECURITY,
            configurationRevisionId = REVISION_ID,
            ownerLocalDate = LocalDate.of(2026, 8, 20),
            zoneId = ZoneOffset.UTC,
            start = Instant.parse("2026-08-20T${startHour.toString().padStart(2, '0')}:00:00Z"),
            end = Instant.parse("2026-08-20T${endHour.toString().padStart(2, '0')}:00:00Z"),
            labelSnapshot = AvailabilityLabel.ON_CALL_RETAINER.displayName,
            createdAt = Instant.parse("2026-08-19T12:00:00Z"),
            updatedAt = Instant.parse("2026-08-19T12:00:00Z"),
        )

    private fun <T : Any> repository(
        type: Class<T>,
        vararg methods: Pair<String, Any>,
    ): T {
        val values = methods.toMap()
        val instance = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
            when (method.name) {
                "toString" -> "SummaryObserverTestProxy(${type.simpleName})"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === arguments?.firstOrNull()
                else -> values[method.name]
                    ?: throw UnsupportedOperationException("${type.simpleName}.${method.name} no debe ejecutarse")
            }
        }
        return requireNotNull(type.cast(instance))
    }

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC)
        val TIMELINE_ID: UUID = UUID.nameUUIDFromBytes("summary-observer-timeline".toByteArray())
        val REVISION_ID: UUID = UUID.nameUUIDFromBytes("summary-observer-revision".toByteArray())

        fun projection(month: YearMonth, marker: Boolean): MonthlySummaryProjection =
            MonthlySummaryProjection(
                month = month,
                essentials = MonthlySummaryEssentials(null, null, null, null),
                compliance = emptyList(),
                availability = null,
                optionalSections = emptyList(),
                hasContent = marker,
            )
    }
}
