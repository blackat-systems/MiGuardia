package com.blackatsystems.miguardia

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardProjection
import com.blackatsystems.miguardia.core.domain.repository.AvailabilityWindowRepository
import com.blackatsystems.miguardia.core.domain.repository.IndependentExtraWorkRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.ui.nextevent.NextEventLoadState
import com.blackatsystems.miguardia.ui.nextevent.NextEventObserver
import com.blackatsystems.miguardia.ui.nextevent.NextEventUiState
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
    fun roomChangesReactivelyReprojectShiftDayOffMedicalLeaveAndVacation() = runBlocking {
        val observer = observer(clock = CLOCK)
        val emissions = Channel<TodayCardProjection>(Channel.UNLIMITED)
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            observer.observe().collect { emissions.send(it) }
        }
        val empty = withTimeout(5_000) { emissions.receive() }
        assertEquals(TodayCardPrimary.EMPTY, empty.primary)
        assertEquals(NextEventPrimary.NONE, empty.futureEvent.primaryEvent)

        val shift = futureShift()
        store.v2Shifts.insert(V2AppTestFixture.writeFor(store, shift, LocalDate.of(2026, 8, 1)))
        val upcoming = receiveUntil(emissions) {
            it.futureEvent.primaryEvent == NextEventPrimary.UPCOMING_SHIFT
        }
        val projectedShift = upcoming.futureEvent.primaryEvents.single() as NextEventItem.Shift
        assertEquals(shift.id, projectedShift.shiftId)

        val privateMedicalNote = "Nota médica ficticia que no debe proyectarse"
        val medicalLeaveId = UUID.fromString("90000000-0000-0000-0000-000000000002")
        store.medicalLeaves.create(
            MedicalLeave(
                id = medicalLeaveId,
                startDate = shift.localStartDate,
                endDateInclusive = shift.localStartDate,
                privateNote = privateMedicalNote,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        val protected = receiveUntil(emissions) {
            it.futureEvent.primaryEvent == NextEventPrimary.NONE
        }
        assertFalse(protected.toString().contains(privateMedicalNote))
        store.medicalLeaves.delete(medicalLeaveId)
        receiveUntil(emissions) { it.futureEvent.primaryEvent == NextEventPrimary.UPCOMING_SHIFT }

        val dayOff = LocalDate.of(2026, 8, 17)
        store.explicitDayStatuses.set(dayOff, ExplicitDayStatusType.DAY_OFF)
        receiveUntil(emissions) { it.futureEvent.nextDayOff == dayOff }

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
            receiveUntil(emissions) {
                it.futureEvent.primaryEvent == NextEventPrimary.DAY_OFF
            }.futureEvent.primaryEvent,
        )
        collector.cancelAndJoin()
    }

    @Test
    fun shiftEditAndDeletionReactivelyReplaceTheCurrentV2Projection() = runBlocking {
        val original = V2AppTestFixture.writeFor(store, futureShift(), LocalDate.of(2026, 8, 1))
        val shifts = MutableV2ShiftRepository(store.v2Shifts)
        val emissions = Channel<TodayCardProjection>(Channel.UNLIMITED)
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            observer(clock = CLOCK, shifts = shifts).observe().collect { emissions.send(it) }
        }
        withTimeout(5_000) { emissions.receive() }

        shifts.replace(listOf(original))
        val firstProjection = receiveUntil(emissions) {
            it.futureEvent.primaryEvent == NextEventPrimary.UPCOMING_SHIFT
        }
        val firstEvent = firstProjection.futureEvent.primaryEvents.single() as NextEventItem.Shift
        assertEquals(original.shift.startAt, firstEvent.start)

        val edited = original.copy(
            shift = original.shift.copy(
                startAt = original.shift.startAt.plusSeconds(3_600),
                endAt = original.shift.endAt.plusSeconds(3_600),
                startTimeSnapshot = original.shift.startTimeSnapshot.plusHours(1),
                endTimeSnapshot = original.shift.endTimeSnapshot.plusHours(1),
                updatedAt = original.shift.updatedAt.plusMillis(1),
            ),
        )
        shifts.replace(listOf(edited))
        val editedProjection = receiveUntil(emissions) {
            (it.futureEvent.primaryEvents.singleOrNull() as? NextEventItem.Shift)?.start == edited.shift.startAt
        }
        val editedEvent = editedProjection.futureEvent.primaryEvents.single() as NextEventItem.Shift
        assertEquals(edited.shift.endAt, editedEvent.end)

        shifts.replace(emptyList())
        receiveUntil(emissions) { it.futureEvent.primaryEvent == NextEventPrimary.NONE }
        collector.cancelAndJoin()
    }

    @Test
    fun actualTimeUpdateReactivelyRemovesThePlannedShiftFromFutureEvents() = runBlocking {
        val write = V2AppTestFixture.writeFor(store, futureShift(), LocalDate.of(2026, 8, 1))
        val shifts = MutableV2ShiftRepository(store.v2Shifts).apply { replace(listOf(write)) }
        val actuals = MutableShiftActualRepository(store.shiftActuals)
        val emissions = Channel<TodayCardProjection>(Channel.UNLIMITED)
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            observer(clock = CLOCK, shifts = shifts, actuals = actuals).observe().collect { emissions.send(it) }
        }
        receiveUntil(emissions) { it.futureEvent.primaryEvent == NextEventPrimary.UPCOMING_SHIFT }

        actuals.replace(
            mapOf(
                write.shift.id to ShiftActualAggregate(
                    record = ShiftActualRecord(
                        shiftId = write.shift.id,
                        timelineId = write.snapshot.timelineId,
                        sector = write.snapshot.sector,
                        actualStart = NOW.minusSeconds(7_200),
                        actualEnd = NOW.minusSeconds(3_600),
                        differenceReason = "Horario real ficticio",
                        explanation = null,
                        createdAt = NOW,
                        updatedAt = NOW,
                    ),
                    extraIntervals = emptyList(),
                ),
            ),
        )
        val reprojected = receiveUntil(emissions) {
            it.futureEvent.primaryEvent == NextEventPrimary.NONE
        }
        assertTrue(reprojected.futureEvent.events.none { it.identity.trackingKey == "shift:${write.shift.id}" })
        collector.cancelAndJoin()
    }

    @Test
    fun availabilitySourceUpdateUsesTheSameReactiveProjectionAsTheCard() = runBlocking {
        V2AppTestFixture.writeFor(store, futureShift(), LocalDate.of(2026, 8, 1))
        val availability = MutableAvailabilityRepository(store.availabilityWindows)
        val observer = observer(clock = CLOCK, availability = availability)
        val emissions = Channel<TodayCardProjection>(Channel.UNLIMITED)
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            observer.observe().collect { emissions.send(it) }
        }
        withTimeout(5_000) { emissions.receive() }

        val window = availabilityWindow(
            start = ZonedDateTime.of(LocalDate.of(2026, 8, 16), LocalTime.of(9, 0), ZONE).toInstant(),
            end = ZonedDateTime.of(LocalDate.of(2026, 8, 16), LocalTime.of(17, 0), ZONE).toInstant(),
        )
        availability.replace(listOf(window))
        val projected = receiveUntil(emissions) {
            it.futureEvent.primaryEvent == NextEventPrimary.UPCOMING_AVAILABILITY
        }
        val event = projected.futureEvent.primaryEvents.single() as NextEventItem.Availability
        assertEquals(window.id, event.windowId)
        assertEquals(window.labelSnapshot, event.labelSnapshot)

        availability.replace(emptyList())
        receiveUntil(emissions) { it.futureEvent.primaryEvent == NextEventPrimary.NONE }
        collector.cancelAndJoin()
    }

    @Test
    fun exactAvailabilityEndTriggersTemporalReprojectionWithHalfOpenBounds() = runBlocking {
        V2AppTestFixture.writeFor(store, futureShift(), LocalDate.of(2026, 8, 1))
        val clock = AdvancingClock(NOW, ZONE)
        val availability = MutableAvailabilityRepository(store.availabilityWindows).apply {
            replace(listOf(availabilityWindow(NOW.minusSeconds(3_600), NOW.plusSeconds(60))))
        }
        val results = observer(
            clock = clock,
            availability = availability,
            temporalDelay = TemporalDelay { duration -> clock.advance(duration) },
        ).observe().take(2).toList()

        assertEquals(NextEventPrimary.ONGOING_AVAILABILITY, results[0].futureEvent.primaryEvent)
        assertEquals(NextEventPrimary.NONE, results[1].futureEvent.primaryEvent)
        assertEquals(NOW.plusSeconds(60), results[1].referenceInstant)
    }

    @Test
    fun viewModelExposesRecoverableErrorAndRetryStartsAFreshV2Observation() = runBlocking {
        V2AppTestFixture.writeFor(store, futureShift(), LocalDate.of(2026, 8, 1))
        val shifts = RecoveringV2ShiftRepository(store.v2Shifts)
        val viewModel = viewModel(shifts)

        val error = withTimeout(5_000) {
            viewModel.uiState.first { it.loadState == NextEventLoadState.ERROR }
        }
        assertEquals("No pudimos actualizar los eventos laborales de hoy.", error.errorMessage)
        shifts.fail = false
        viewModel.retry()

        val content = withTimeout(5_000) {
            viewModel.uiState.first { it.loadState == NextEventLoadState.CONTENT }
        }
        assertEquals(TodayCardPrimary.EMPTY, content.result?.primary)
        assertEquals(2, shifts.collectionCount)
    }

    @Test
    fun viewModelKeepsTheExactLastValidSameDayProjectionWhenASourceFailsLater() = runBlocking {
        val write = V2AppTestFixture.writeFor(store, futureShift(), LocalDate.of(2026, 8, 1))
        val shifts = ContentThenFailV2ShiftRepository(store.v2Shifts, listOf(write))
        val viewModel = viewModel(shifts)
        val states = Channel<NextEventUiState>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect { states.send(it) }
        }
        val content = receiveStateUntil(states) { it.loadState == NextEventLoadState.CONTENT }
        assertEquals(write.shift.id, (content.result?.futureEvent?.primaryEvents?.single() as NextEventItem.Shift).shiftId)

        shifts.fail()
        val error = receiveStateUntil(states) { it.loadState == NextEventLoadState.ERROR }

        assertSame(content.result, error.result)
        assertEquals("No pudimos actualizar los eventos laborales de hoy.", error.errorMessage)
        collector.cancelAndJoin()
    }

    @Test
    fun stoppingObservationCancelsItsV2SourceGraph() = runBlocking {
        V2AppTestFixture.writeFor(store, futureShift(), LocalDate.of(2026, 8, 1))
        val shifts = CancellationTrackingV2ShiftRepository(store.v2Shifts)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            observer(clock = CLOCK, shifts = shifts).observe().collect()
        }

        withTimeout(5_000) { shifts.started.await() }
        collector.cancelAndJoin()

        withTimeout(5_000) { shifts.cancelled.await() }
    }

    @Test
    fun localMidnightRestartsSourcesAndInjectedZoneOwnsTheCivilDate() = runBlocking {
        V2AppTestFixture.writeFor(store, futureShift(), LocalDate.of(2026, 8, 1))
        val beforeMidnight = ZonedDateTime.of(
            LocalDate.of(2026, 8, 15),
            LocalTime.of(23, 59),
            ZONE,
        ).toInstant()
        val advancingClock = AdvancingClock(beforeMidnight, ZONE)
        val dates = observer(
            clock = advancingClock,
            temporalDelay = TemporalDelay { duration -> advancingClock.advance(duration) },
        ).observe().take(2).toList().map(TodayCardProjection::date)
        assertEquals(listOf(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 16)), dates)

        val sameInstant = Instant.parse("2026-08-15T02:00:00Z")
        val utcResult = observer(
            clock = Clock.fixed(sameInstant, ZoneId.of("UTC")),
            zoneId = ZoneId.of("UTC"),
        ).observe().first()
        val argentineResult = observer(
            clock = Clock.fixed(sameInstant, ZONE),
            zoneId = ZONE,
        ).observe().first()
        assertEquals(LocalDate.of(2026, 8, 15), utcResult.date)
        assertEquals(LocalDate.of(2026, 8, 14), argentineResult.date)
    }

    @Test
    fun everyV2WorkSourceObservesEverySectorPresentInConfigurationHistory() = runBlocking {
        val timelineId = UUID.fromString("92000000-0000-0000-0000-000000000001")
        store.workConfiguration.createInitial(
            timelineId = timelineId,
            firstRevision = EffectiveRevision(
                id = UUID.fromString("92000000-0000-0000-0000-000000000002"),
                effectiveFrom = LocalDate.of(2026, 8, 1),
                value = WorkConfiguration(
                    sector = WorkSector.PRIVATE_SECURITY,
                    hoursReference = HoursReference.NotUsed,
                    availabilityLabel = null,
                ),
            ),
        )
        store.workConfiguration.addRevision(
            timelineId = timelineId,
            revision = EffectiveRevision(
                id = UUID.fromString("92000000-0000-0000-0000-000000000003"),
                effectiveFrom = LocalDate.of(2026, 8, 10),
                value = WorkConfiguration(
                    sector = WorkSector.POLICE,
                    hoursReference = HoursReference.NotUsed,
                    availabilityLabel = null,
                ),
            ),
        )
        val shifts = TrackingV2ShiftRepository(store.v2Shifts)
        val availability = TrackingAvailabilityRepository(store.availabilityWindows)
        val actuals = TrackingShiftActualRepository(store.shiftActuals)
        val extras = TrackingIndependentExtraRepository(store.independentExtraWork)

        NextEventObserver(
            shifts = shifts,
            availabilityWindows = availability,
            shiftActuals = actuals,
            independentExtras = extras,
            explicitDayStatuses = store.explicitDayStatuses,
            vacations = store.vacations,
            medicalLeaves = store.medicalLeaves,
            workConfiguration = store.workConfiguration,
            clock = CLOCK,
            zoneId = ZONE,
            temporalDelay = TemporalDelay { awaitCancellation() },
        ).observe().first()

        val expected = setOf(WorkSector.PRIVATE_SECURITY, WorkSector.POLICE)
        assertEquals(expected, shifts.sectors.toSet())
        assertEquals(expected, availability.sectors.toSet())
        assertEquals(expected, actuals.sectors.toSet())
        assertEquals(expected, extras.sectors.toSet())
    }

    private fun observer(
        clock: Clock,
        shifts: V2ShiftRepository = store.v2Shifts,
        availability: AvailabilityWindowRepository = store.availabilityWindows,
        actuals: ShiftActualRepository = store.shiftActuals,
        zoneId: ZoneId = ZONE,
        temporalDelay: TemporalDelay = TemporalDelay { awaitCancellation() },
    ): NextEventObserver = NextEventObserver(
        shifts = shifts,
        availabilityWindows = availability,
        shiftActuals = actuals,
        independentExtras = store.independentExtraWork,
        explicitDayStatuses = store.explicitDayStatuses,
        vacations = store.vacations,
        medicalLeaves = store.medicalLeaves,
        workConfiguration = store.workConfiguration,
        clock = clock,
        zoneId = zoneId,
        temporalDelay = temporalDelay,
    )

    private fun viewModel(shifts: V2ShiftRepository): NextEventViewModel = NextEventViewModel(
        shifts = shifts,
        availabilityWindows = store.availabilityWindows,
        shiftActuals = store.shiftActuals,
        independentExtras = store.independentExtraWork,
        explicitDayStatuses = store.explicitDayStatuses,
        vacations = store.vacations,
        medicalLeaves = store.medicalLeaves,
        workConfiguration = store.workConfiguration,
        clock = CLOCK,
        zoneId = ZONE,
        temporalDelay = TemporalDelay { awaitCancellation() },
    )

    private suspend fun receiveUntil(
        channel: Channel<TodayCardProjection>,
        predicate: (TodayCardProjection) -> Boolean,
    ): TodayCardProjection = withTimeout(5_000) {
        var value = channel.receive()
        while (!predicate(value)) value = channel.receive()
        value
    }

    private suspend fun receiveStateUntil(
        channel: Channel<NextEventUiState>,
        predicate: (NextEventUiState) -> Boolean,
    ): NextEventUiState = withTimeout(5_000) {
        var value = channel.receive()
        while (!predicate(value)) value = channel.receive()
        value
    }

    private fun futureShift(): Shift {
        val date = LocalDate.of(2026, 8, 16)
        val start = ZonedDateTime.of(date, LocalTime.of(19, 0), ZONE).toInstant()
        val end = ZonedDateTime.of(date.plusDays(1), LocalTime.of(7, 0), ZONE).toInstant()
        return Shift(
            id = UUID.fromString("80000000-0000-0000-0000-000000000001"),
            startAt = start,
            endAt = end,
            zoneId = ZONE,
            localStartDate = date,
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "FIC",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(19, 0),
            endTimeSnapshot = LocalTime.of(7, 0),
            colorArgbSnapshot = 0xFF315DA8.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    private fun availabilityWindow(start: Instant, end: Instant): AvailabilityWindowRecord =
        AvailabilityWindowRecord(
            id = UUID.fromString("91000000-0000-0000-0000-000000000001"),
            timelineId = V2AppTestFixture.TIMELINE_ID,
            sector = WorkSector.NURSING,
            configurationRevisionId = V2AppTestFixture.REVISION_ID,
            ownerLocalDate = start.atZone(ZONE).toLocalDate(),
            zoneId = ZONE,
            start = start,
            end = end,
            labelSnapshot = "Guardia pasiva",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private class MutableAvailabilityRepository(
        delegate: AvailabilityWindowRepository,
    ) : AvailabilityWindowRepository by delegate {
        private val values = MutableStateFlow<List<AvailabilityWindowRecord>>(emptyList())

        fun replace(replacement: List<AvailabilityWindowRecord>) {
            values.value = replacement
        }

        override fun observeAll(
            timelineId: UUID,
            sector: WorkSector,
        ): Flow<List<AvailabilityWindowRecord>> = values
    }

    private class MutableV2ShiftRepository(
        delegate: V2ShiftRepository,
    ) : V2ShiftRepository by delegate {
        private val values = MutableStateFlow<List<V2ShiftWrite>>(emptyList())

        fun replace(replacement: List<V2ShiftWrite>) {
            values.value = replacement
        }

        override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<V2ShiftWrite>> = values
    }

    private class MutableShiftActualRepository(
        delegate: ShiftActualRepository,
    ) : ShiftActualRepository by delegate {
        private val values = MutableStateFlow<Map<UUID, ShiftActualAggregate>>(emptyMap())

        fun replace(replacement: Map<UUID, ShiftActualAggregate>) {
            values.value = replacement
        }

        override fun observeAllActuals(
            timelineId: UUID,
            sector: WorkSector,
        ): Flow<Map<UUID, ShiftActualAggregate>> = values
    }

    private class RecoveringV2ShiftRepository(
        delegate: V2ShiftRepository,
    ) : V2ShiftRepository by delegate {
        var fail = true
        var collectionCount = 0

        override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<V2ShiftWrite>> = flow {
            collectionCount += 1
            if (fail) error("Fallo V2 ficticio recuperable")
            emit(emptyList())
            awaitCancellation()
        }
    }

    private class ContentThenFailV2ShiftRepository(
        delegate: V2ShiftRepository,
        private val values: List<V2ShiftWrite>,
    ) : V2ShiftRepository by delegate {
        private val failure = CompletableDeferred<Unit>()

        fun fail() {
            failure.complete(Unit)
        }

        override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<V2ShiftWrite>> = flow {
            emit(values)
            failure.await()
            error("Fallo V2 ficticio posterior")
        }
    }

    private class CancellationTrackingV2ShiftRepository(
        delegate: V2ShiftRepository,
    ) : V2ShiftRepository by delegate {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()

        override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<V2ShiftWrite>> = flow {
            started.complete(Unit)
            try {
                emit(emptyList())
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
    }

    private class TrackingV2ShiftRepository(
        delegate: V2ShiftRepository,
    ) : V2ShiftRepository by delegate {
        val sectors = mutableListOf<WorkSector>()

        override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<V2ShiftWrite>> {
            sectors += sector
            return flowOf(emptyList())
        }
    }

    private class TrackingAvailabilityRepository(
        delegate: AvailabilityWindowRepository,
    ) : AvailabilityWindowRepository by delegate {
        val sectors = mutableListOf<WorkSector>()

        override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<AvailabilityWindowRecord>> {
            sectors += sector
            return flowOf(emptyList())
        }
    }

    private class TrackingShiftActualRepository(
        delegate: ShiftActualRepository,
    ) : ShiftActualRepository by delegate {
        val sectors = mutableListOf<WorkSector>()

        override fun observeAllActuals(
            timelineId: UUID,
            sector: WorkSector,
        ): Flow<Map<UUID, ShiftActualAggregate>> {
            sectors += sector
            return flowOf(emptyMap())
        }
    }

    private class TrackingIndependentExtraRepository(
        delegate: IndependentExtraWorkRepository,
    ) : IndependentExtraWorkRepository by delegate {
        val sectors = mutableListOf<WorkSector>()

        override fun observeAll(
            timelineId: UUID,
            sector: WorkSector,
        ): Flow<List<com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord>> {
            sectors += sector
            return flowOf(emptyList())
        }
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

    private companion object {
        val ZONE: ZoneId = AppDefaults.zoneId()
        val NOW: Instant = ZonedDateTime.of(
            LocalDate.of(2026, 8, 15),
            LocalTime.NOON,
            ZONE,
        ).toInstant()
        val CLOCK: Clock = Clock.fixed(NOW, ZONE)
    }
}
