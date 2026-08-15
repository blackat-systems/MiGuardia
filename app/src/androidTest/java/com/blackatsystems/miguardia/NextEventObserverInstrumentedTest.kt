package com.blackatsystems.miguardia

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.ui.nextevent.NextEventObserver
import com.blackatsystems.miguardia.ui.nextevent.NextEventLoadState
import com.blackatsystems.miguardia.ui.nextevent.NextEventViewModel
import com.blackatsystems.miguardia.ui.nextevent.TemporalDelay
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NextEventObserverInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "next-event-observer-${UUID.randomUUID()}.db"
    private lateinit var store: LocalDataStore

    @Before
    fun setUp() {
        store = LocalDataStore.create(context, databaseName)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun roomChangesReactivelyReprojectGuardDayOffAndVacation() = runBlocking {
        val observer = NextEventObserver(
            shifts = store.shifts,
            explicitDayStatuses = store.explicitDayStatuses,
            vacations = store.vacations,
            clock = CLOCK,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { awaitCancellation() },
        )
        val emissions = Channel<com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult>(Channel.UNLIMITED)
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            observer.observe().collect { emissions.send(it) }
        }
        assertEquals(NextEventPrimary.NONE, withTimeout(5_000) { emissions.receive() }.primaryEvent)

        val shift = futureShift()
        store.shifts.insert(shift)
        val upcoming = receiveUntil(emissions) { it.primaryEvent == NextEventPrimary.UPCOMING_SHIFT }
        assertEquals(shift.id, upcoming.upcomingShifts.single().id)

        val dayOff = LocalDate.of(2026, 8, 17)
        store.explicitDayStatuses.set(dayOff, ExplicitDayStatusType.DAY_OFF)
        val withDayOff = receiveUntil(emissions) { it.nextDayOff == dayOff }
        assertEquals(NextEventPrimary.UPCOMING_SHIFT, withDayOff.primaryEvent)

        store.vacations.insert(
            Vacation(
                id = UUID.fromString("90000000-0000-0000-0000-000000000001"),
                startDate = shift.localStartDate,
                endDateInclusive = shift.localStartDate,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        assertEquals(
            NextEventPrimary.DAY_OFF,
            receiveUntil(emissions) { it.primaryEvent == NextEventPrimary.DAY_OFF }.primaryEvent,
        )

        store.explicitDayStatuses.clear(dayOff)
        assertEquals(
            NextEventPrimary.NONE,
            receiveUntil(emissions) { it.primaryEvent == NextEventPrimary.NONE }.primaryEvent,
        )
        collector.cancel()
    }

    @Test
    fun viewModelPreservesRecoverableErrorAndRetryStartsAFreshObservation() = runBlocking {
        val shifts = RecoveringShiftRepository()
        val viewModel = NextEventViewModel(
            shifts = shifts,
            explicitDayStatuses = EmptyExplicitStatusRepository(),
            vacations = EmptyVacationRepository(),
            clock = CLOCK,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { awaitCancellation() },
        )

        val error = withTimeout(5_000) {
            viewModel.uiState.first { it.loadState == NextEventLoadState.ERROR }
        }
        assertEquals("No pudimos actualizar el próximo evento.", error.errorMessage)
        shifts.fail = false
        viewModel.retry()

        val content = withTimeout(5_000) {
            viewModel.uiState.first { it.loadState == NextEventLoadState.CONTENT }
        }
        assertEquals(NextEventPrimary.NONE, content.result?.primaryEvent)
        assertEquals(2, shifts.collectionCount)
    }

    @Test
    fun temporalObserverTransitionsAtExactStartAndEndWithInjectedClock() = runBlocking {
        val clock = AdvancingClock(NOW, AppDefaults.zoneId())
        val shift = futureShift().copy(
            startAt = NOW.plusSeconds(60),
            endAt = NOW.plusSeconds(120),
            localStartDate = NOW.atZone(AppDefaults.zoneId()).toLocalDate(),
            startTimeSnapshot = NOW.plusSeconds(60).atZone(AppDefaults.zoneId()).toLocalTime(),
            endTimeSnapshot = NOW.plusSeconds(120).atZone(AppDefaults.zoneId()).toLocalTime(),
        )
        val results = NextEventObserver(
            shifts = StaticShiftRepository(listOf(shift)),
            explicitDayStatuses = EmptyExplicitStatusRepository(),
            vacations = EmptyVacationRepository(),
            clock = clock,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { duration -> clock.advance(duration) },
        ).observe().take(3).toList()

        assertEquals(
            listOf(
                NextEventPrimary.UPCOMING_SHIFT,
                NextEventPrimary.ONGOING_SHIFT,
                NextEventPrimary.NONE,
            ),
            results.map { it.primaryEvent },
        )
        assertEquals(shift.startAt, results[1].referenceInstant)
        assertEquals(shift.endAt, results[2].referenceInstant)
    }

    private suspend fun receiveUntil(
        channel: Channel<com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult>,
        predicate: (com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult) -> Boolean,
    ): com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult = withTimeout(5_000) {
        var value = channel.receive()
        while (!predicate(value)) value = channel.receive()
        value
    }

    private fun futureShift(): Shift {
        val date = LocalDate.of(2026, 8, 16)
        val start = ZonedDateTime.of(date, LocalTime.of(19, 0), AppDefaults.zoneId()).toInstant()
        val end = ZonedDateTime.of(date.plusDays(1), LocalTime.of(7, 0), AppDefaults.zoneId()).toInstant()
        return Shift(
            id = UUID.fromString("80000000-0000-0000-0000-000000000001"),
            startAt = start,
            endAt = end,
            zoneId = AppDefaults.zoneId(),
            localStartDate = date,
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "FIC",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(19, 0),
            endTimeSnapshot = LocalTime.of(7, 0),
            colorArgbSnapshot = 0xFF315DA8.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = null,
            sourceScheduleCombinationId = null,
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    private class RecoveringShiftRepository : ShiftRepository {
        var fail = true
        var collectionCount = 0

        override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> = flow {
            collectionCount += 1
            if (fail) error("Fallo ficticio")
            emit(emptyList())
        }

        override fun observeStartingBetween(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<Shift>> = MutableStateFlow(emptyList())

        override suspend fun getById(id: UUID): Shift? = null
        override suspend fun insert(shift: Shift) = Unit
        override suspend fun update(shift: Shift) = Unit
        override suspend fun delete(id: UUID) = Unit
        override suspend fun applyBatch(mutation: ShiftBatchMutation) = Unit
    }

    private class StaticShiftRepository(
        private val values: List<Shift>,
    ) : ShiftRepository {
        override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> =
            MutableStateFlow(values.filter { it.endAt > instantExclusive })

        override fun observeStartingBetween(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<Shift>> = MutableStateFlow(emptyList())

        override suspend fun getById(id: UUID): Shift? = values.firstOrNull { it.id == id }
        override suspend fun insert(shift: Shift) = Unit
        override suspend fun update(shift: Shift) = Unit
        override suspend fun delete(id: UUID) = Unit
        override suspend fun applyBatch(mutation: ShiftBatchMutation) = Unit
    }

    private class AdvancingClock(
        private var current: Instant,
        private val clockZone: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = clockZone
        override fun withZone(zone: ZoneId): Clock = AdvancingClock(current, zone)
        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    private class EmptyExplicitStatusRepository : ExplicitDayStatusRepository {
        private val values = MutableStateFlow<List<ExplicitDayStatus>>(emptyList())
        override fun observeFrom(startDateInclusive: LocalDate): Flow<List<ExplicitDayStatus>> = values
        override fun observeBetween(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<ExplicitDayStatus>> = values
        override suspend fun set(date: LocalDate, type: ExplicitDayStatusType) = Unit
        override suspend fun clear(date: LocalDate) = Unit
    }

    private class EmptyVacationRepository : VacationRepository {
        private val values = MutableStateFlow<List<Vacation>>(emptyList())
        override fun observeEndingOnOrAfter(dateInclusive: LocalDate): Flow<List<Vacation>> = values
        override fun observeOverlapping(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<Vacation>> = values
        override suspend fun getById(id: UUID): Vacation? = null
        override suspend fun insert(vacation: Vacation) = Unit
        override suspend fun update(vacation: Vacation) = Unit
        override suspend fun delete(id: UUID) = Unit
    }

    private companion object {
        val NOW: Instant = ZonedDateTime.of(
            LocalDate.of(2026, 8, 15),
            LocalTime.NOON,
            AppDefaults.zoneId(),
        ).toInstant()
        val CLOCK: Clock = Clock.fixed(NOW, AppDefaults.zoneId())
    }
}
