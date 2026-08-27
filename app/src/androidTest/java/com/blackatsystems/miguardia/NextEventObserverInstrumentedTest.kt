package com.blackatsystems.miguardia

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftActualWriteResult
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.model.buildShiftActualSaveMutation
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardProjection
import com.blackatsystems.miguardia.core.domain.nextevent.TodayShiftState
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.ui.nextevent.NextEventObservation
import com.blackatsystems.miguardia.ui.nextevent.NextEventObserver
import com.blackatsystems.miguardia.ui.nextevent.NextEventLoadState
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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
        val observer = NextEventObserver(
            shifts = store.shifts,
            explicitDayStatuses = store.explicitDayStatuses,
            vacations = store.vacations,
            medicalLeaves = store.medicalLeaves,
            shiftActuals = store.shiftActuals,
            workConfiguration = store.workConfiguration,
            clock = CLOCK,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { awaitCancellation() },
        )
        val emissions = Channel<TodayCardProjection>(Channel.UNLIMITED)
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            observer.observe().collect { emissions.send(it) }
        }
        val empty = withTimeout(5_000) { emissions.receive() }
        assertEquals(TodayCardPrimary.EMPTY, empty.primary)
        assertEquals(NextEventPrimary.NONE, empty.futureEvent.primaryEvent)

        val shift = futureShift()
        store.v2Shifts.insert(
            V2AppTestFixture.writeFor(store, shift, LocalDate.of(2026, 8, 1)),
        )
        val upcoming = receiveUntil(emissions) {
            it.futureEvent.primaryEvent == NextEventPrimary.UPCOMING_SHIFT
        }
        assertEquals(TodayCardPrimary.FUTURE_EVENT, upcoming.primary)
        assertEquals(shift.id, upcoming.futureEvent.upcomingShifts.single().id)

        val medicalLeaveId = UUID.fromString("90000000-0000-0000-0000-000000000002")
        val privateMedicalNote = "Nota médica ficticia que no debe proyectarse"
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
        assertEquals(TodayCardPrimary.EMPTY, protected.primary)
        assertFalse(protected.toString().contains(privateMedicalNote))
        store.medicalLeaves.delete(medicalLeaveId)
        receiveUntil(emissions) {
            it.futureEvent.primaryEvent == NextEventPrimary.UPCOMING_SHIFT
        }

        val dayOff = LocalDate.of(2026, 8, 17)
        store.explicitDayStatuses.set(dayOff, ExplicitDayStatusType.DAY_OFF)
        val withDayOff = receiveUntil(emissions) { it.futureEvent.nextDayOff == dayOff }
        assertEquals(NextEventPrimary.UPCOMING_SHIFT, withDayOff.futureEvent.primaryEvent)

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

        store.explicitDayStatuses.clear(dayOff)
        assertEquals(
            NextEventPrimary.NONE,
            receiveUntil(emissions) {
                it.futureEvent.primaryEvent == NextEventPrimary.NONE
            }.futureEvent.primaryEvent,
        )
        collector.cancelAndJoin()
    }

    @Test
    fun shiftEditAndDeletionReactivelyReplaceTheCurrentProjection() = runBlocking {
        val shifts = MutableShiftRepository()
        val observer = NextEventObserver(
            shifts = shifts,
            explicitDayStatuses = EmptyExplicitStatusRepository(),
            vacations = EmptyVacationRepository(),
            medicalLeaves = store.medicalLeaves,
            shiftActuals = store.shiftActuals,
            workConfiguration = store.workConfiguration,
            clock = CLOCK,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { awaitCancellation() },
        )
        val emissions = Channel<TodayCardProjection>(Channel.UNLIMITED)
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            observer.observe().collect { emissions.send(it) }
        }
        assertEquals(TodayCardPrimary.EMPTY, withTimeout(5_000) { emissions.receive() }.primary)

        val original = futureShift()
        shifts.replace(listOf(original))
        val inserted = receiveUntil(emissions) {
            it.futureEvent.upcomingShifts.singleOrNull()?.id == original.id
        }
        assertEquals("Objetivo ficticio", inserted.futureEvent.upcomingShifts.single().objectiveNameSnapshot)

        val edited = original.copy(
            objectiveNameSnapshot = "Objetivo editado ficticio",
            updatedAt = original.updatedAt.plusMillis(1L),
        )
        shifts.replace(listOf(edited))
        val reprojected = receiveUntil(emissions) {
            it.futureEvent.upcomingShifts.singleOrNull()?.objectiveNameSnapshot ==
                "Objetivo editado ficticio"
        }
        assertEquals(original.id, reprojected.futureEvent.upcomingShifts.single().id)

        shifts.replace(emptyList())
        val deleted = receiveUntil(emissions) {
            it.futureEvent.primaryEvent == NextEventPrimary.NONE
        }
        assertEquals(TodayCardPrimary.EMPTY, deleted.primary)
        collector.cancelAndJoin()
    }

    @Test
    fun actualTimeWriteReactivelyUpdatesTheTodaySummary() = runBlocking {
        val shift = completedTodayShift()
        store.v2Shifts.insert(
            V2AppTestFixture.writeFor(store, shift, LocalDate.of(2026, 8, 1)),
        )
        val observer = NextEventObserver(
            shifts = store.shifts,
            explicitDayStatuses = store.explicitDayStatuses,
            vacations = store.vacations,
            medicalLeaves = store.medicalLeaves,
            shiftActuals = store.shiftActuals,
            workConfiguration = store.workConfiguration,
            clock = CLOCK,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { awaitCancellation() },
        )
        val emissions = Channel<TodayCardProjection>(Channel.UNLIMITED)
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            observer.observe().collect { emissions.send(it) }
        }
        val planned = receiveUntil(emissions) {
            it.shifts.singleOrNull()?.shift?.id == shift.id
        }
        assertEquals(TodayShiftState.COMPLETED, planned.shifts.single().state)
        assertFalse(planned.shifts.single().hasActualTime)

        val expectation = requireNotNull(store.shiftActuals.getExpectation(shift.id))
        val mutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = expectation,
                draft = ShiftActualDraft(
                    actualStart = shift.startAt.plus(Duration.ofMinutes(30)),
                    actualEnd = shift.endAt,
                    differenceReason = "Ingreso posterior ficticio",
                    explanation = null,
                    differenceChoice = null,
                    classSelection = null,
                    fragments = emptyList(),
                ),
                clock = CLOCK,
                timestamp = NOW,
            ),
        )
        assertTrue(store.shiftActuals.save(mutation) is ShiftActualWriteResult.Saved)

        val actual = receiveUntil(emissions) {
            it.shifts.singleOrNull()?.hasActualTime == true
        }
        assertEquals(TodayCardPrimary.COMPLETED_SUMMARY, actual.primary)
        assertEquals(TodayShiftState.COMPLETED, actual.shifts.single().state)
        collector.cancelAndJoin()
    }

    @Test
    fun actualTimeIsObservedAcrossEverySectorInTheConfigurationHistory() = runBlocking {
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
        val shift = completedTodayShift()
        val actual = ShiftActualAggregate(
            record = ShiftActualRecord(
                shiftId = shift.id,
                timelineId = timelineId,
                sector = WorkSector.POLICE,
                actualStart = shift.startAt,
                actualEnd = shift.endAt,
                differenceReason = "Horario real ficticio",
                explanation = null,
                createdAt = NOW,
                updatedAt = NOW,
            ),
            extraIntervals = emptyList(),
        )
        val actuals = TrackingSectorShiftActualRepository(
            delegate = store.shiftActuals,
            values = mapOf(WorkSector.POLICE to mapOf(shift.id to actual)),
        )

        val result = NextEventObserver(
            shifts = StaticShiftRepository(listOf(shift)),
            explicitDayStatuses = EmptyExplicitStatusRepository(),
            vacations = EmptyVacationRepository(),
            medicalLeaves = store.medicalLeaves,
            shiftActuals = actuals,
            workConfiguration = store.workConfiguration,
            clock = CLOCK,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { awaitCancellation() },
        ).observe().first { projection -> projection.shifts.isNotEmpty() }

        assertEquals(
            setOf(WorkSector.PRIVATE_SECURITY, WorkSector.POLICE),
            actuals.queriedSectors.toSet(),
        )
        assertTrue(result.shifts.single().hasActualTime)
    }

    @Test
    fun viewModelPreservesRecoverableErrorAndRetryStartsAFreshObservation() = runBlocking {
        val shifts = RecoveringShiftRepository()
        val viewModel = NextEventViewModel(
            shifts = shifts,
            explicitDayStatuses = EmptyExplicitStatusRepository(),
            vacations = EmptyVacationRepository(),
            medicalLeaves = store.medicalLeaves,
            shiftActuals = store.shiftActuals,
            workConfiguration = store.workConfiguration,
            clock = CLOCK,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { awaitCancellation() },
        )

        val error = withTimeout(5_000) {
            viewModel.uiState.first { it.loadState == NextEventLoadState.ERROR }
        }
        assertEquals("No pudimos actualizar las jornadas de hoy.", error.errorMessage)
        shifts.fail = false
        viewModel.retry()

        val content = withTimeout(5_000) {
            viewModel.uiState.first { it.loadState == NextEventLoadState.CONTENT }
        }
        assertEquals(TodayCardPrimary.EMPTY, content.result?.primary)
        assertEquals(NextEventPrimary.NONE, content.result?.futureEvent?.primaryEvent)
        assertEquals(2, shifts.collectionCount)
    }

    @Test
    fun viewModelKeepsTheExactLastValidProjectionWhenTheSourceFailsLater() = runBlocking {
        val shift = futureShift()
        val shifts = ContentThenFailShiftRepository(listOf(shift))
        val viewModel = NextEventViewModel(
            shifts = shifts,
            explicitDayStatuses = EmptyExplicitStatusRepository(),
            vacations = EmptyVacationRepository(),
            medicalLeaves = store.medicalLeaves,
            shiftActuals = store.shiftActuals,
            workConfiguration = store.workConfiguration,
            clock = CLOCK,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { awaitCancellation() },
        )
        val states = Channel<NextEventUiState>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect { state -> states.send(state) }
        }
        val content = withTimeout(5_000) {
            var state = states.receive()
            while (state.loadState != NextEventLoadState.CONTENT) state = states.receive()
            state
        }
        assertEquals(shift.id, content.result?.futureEvent?.upcomingShifts?.single()?.id)

        shifts.fail()
        val error = withTimeout(5_000) {
            var state = states.receive()
            while (state.loadState != NextEventLoadState.ERROR) state = states.receive()
            state
        }
        assertEquals(content.result, error.result)
        assertSame(content.result, error.result)
        assertEquals("No pudimos actualizar las jornadas de hoy.", error.errorMessage)
        collector.cancelAndJoin()
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
            medicalLeaves = store.medicalLeaves,
            shiftActuals = store.shiftActuals,
            workConfiguration = store.workConfiguration,
            clock = clock,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { duration -> clock.advance(duration) },
        ).observe().take(3).toList()

        assertEquals(
            listOf(
                TodayCardPrimary.UPCOMING_SHIFT,
                TodayCardPrimary.ONGOING_SHIFT,
                TodayCardPrimary.COMPLETED_SUMMARY,
            ),
            results.map { it.primary },
        )
        assertEquals(
            listOf(
                NextEventPrimary.UPCOMING_SHIFT,
                NextEventPrimary.ONGOING_SHIFT,
                NextEventPrimary.NONE,
            ),
            results.map { it.futureEvent.primaryEvent },
        )
        assertEquals(shift.startAt, results[1].referenceInstant)
        assertEquals(shift.endAt, results[2].referenceInstant)
    }

    @Test
    fun completedSummaryWiresTheObserverTimerDirectlyToMidnight() = runBlocking {
        val delay = RecordingTemporalDelay()
        val projections = Channel<TodayCardProjection>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            NextEventObserver(
                shifts = StaticShiftRepository(listOf(completedTodayShift(), futureShift())),
                explicitDayStatuses = EmptyExplicitStatusRepository(),
                vacations = EmptyVacationRepository(),
                medicalLeaves = store.medicalLeaves,
                shiftActuals = store.shiftActuals,
                workConfiguration = store.workConfiguration,
                clock = CLOCK,
                zoneId = AppDefaults.zoneId(),
                temporalDelay = delay,
            ).observe().collect { projection -> projections.send(projection) }
        }

        val projection = withTimeout(5_000) { projections.receive() }
        val observedDelay = withTimeout(5_000) { delay.durations.receive() }
        val nextMidnight = LocalDate.of(2026, 8, 16)
            .atStartOfDay(AppDefaults.zoneId())
            .toInstant()

        assertEquals(TodayCardPrimary.COMPLETED_SUMMARY, projection.primary)
        assertEquals(NextEventPrimary.UPCOMING_SHIFT, projection.futureEvent.primaryEvent)
        assertEquals(Duration.between(NOW, nextMidnight), observedDelay)
        collector.cancelAndJoin()
    }

    @Test
    fun temporalWakeWithoutClockAdvanceKeepsObservingUntilTheNextBoundary() = runBlocking {
        val clock = AdvancingClock(NOW, AppDefaults.zoneId())
        val shift = futureShift().copy(
            startAt = NOW.plusSeconds(60),
            endAt = NOW.plusSeconds(120),
            localStartDate = NOW.atZone(AppDefaults.zoneId()).toLocalDate(),
            startTimeSnapshot = NOW.plusSeconds(60).atZone(AppDefaults.zoneId()).toLocalTime(),
            endTimeSnapshot = NOW.plusSeconds(120).atZone(AppDefaults.zoneId()).toLocalTime(),
        )
        var waits = 0

        val results = NextEventObserver(
            shifts = StaticShiftRepository(listOf(shift)),
            explicitDayStatuses = EmptyExplicitStatusRepository(),
            vacations = EmptyVacationRepository(),
            medicalLeaves = store.medicalLeaves,
            shiftActuals = store.shiftActuals,
            workConfiguration = store.workConfiguration,
            clock = clock,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { duration ->
                waits += 1
                if (waits > 1) clock.advance(duration)
            },
        ).observe().take(3).toList()

        assertEquals(
            listOf(
                TodayCardPrimary.UPCOMING_SHIFT,
                TodayCardPrimary.UPCOMING_SHIFT,
                TodayCardPrimary.ONGOING_SHIFT,
            ),
            results.map(TodayCardProjection::primary),
        )
        assertEquals(2, waits)
    }

    @Test
    fun midnightCancelsPreviousDateSourceAndObservesNewCivilDay() = runBlocking {
        val clock = AdvancingClock(NOW, AppDefaults.zoneId())
        val shifts = TrackingShiftRepository()

        val results = NextEventObserver(
            shifts = shifts,
            explicitDayStatuses = EmptyExplicitStatusRepository(),
            vacations = EmptyVacationRepository(),
            medicalLeaves = store.medicalLeaves,
            shiftActuals = store.shiftActuals,
            workConfiguration = store.workConfiguration,
            clock = clock,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { duration -> clock.advance(duration) },
        ).observe().take(2).toList()

        val firstDayStart = LocalDate.of(2026, 8, 15)
            .atStartOfDay(AppDefaults.zoneId())
            .toInstant()
        val secondDayStart = LocalDate.of(2026, 8, 16)
            .atStartOfDay(AppDefaults.zoneId())
            .toInstant()
        assertEquals(listOf(firstDayStart, secondDayStart), shifts.queriedCutoffs)
        assertEquals(listOf(0, 1), shifts.cancellationsAtSubscription)
        assertEquals(
            listOf(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 16)),
            results.map { it.date },
        )
    }

    @Test
    fun midnightEmitsLoadingForTheNewDateBeforeReplayingContent() = runBlocking {
        val clock = AdvancingClock(NOW, AppDefaults.zoneId())

        val states = NextEventObserver(
            shifts = StaticShiftRepository(emptyList()),
            explicitDayStatuses = EmptyExplicitStatusRepository(),
            vacations = EmptyVacationRepository(),
            medicalLeaves = store.medicalLeaves,
            shiftActuals = store.shiftActuals,
            workConfiguration = store.workConfiguration,
            clock = clock,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { duration -> clock.advance(duration) },
        ).observeStates().take(3).toList()

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 16),
            ),
            states.map { state ->
                when (state) {
                    is NextEventObservation.Loading -> state.date
                    is NextEventObservation.Content -> state.projection.date
                }
            },
        )
        assertTrue(states[0] is NextEventObservation.Loading)
        assertTrue(states[1] is NextEventObservation.Content)
        assertTrue(states[2] is NextEventObservation.Loading)
    }

    @Test
    fun recoverableErrorDropsYesterdayProjectionWhenTheDateChanges() = runBlocking {
        val clock = AdvancingClock(NOW, AppDefaults.zoneId())
        val shifts = FailThenBlockShiftRepository(listOf(futureShift()))
        val delay = FailureThenMidnightDelay(clock)
        val viewModel = NextEventViewModel(
            shifts = shifts,
            explicitDayStatuses = EmptyExplicitStatusRepository(),
            vacations = EmptyVacationRepository(),
            medicalLeaves = store.medicalLeaves,
            shiftActuals = store.shiftActuals,
            workConfiguration = store.workConfiguration,
            clock = clock,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = delay,
        )
        val states = Channel<NextEventUiState>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect { state -> states.send(state) }
        }
        val content = receiveUiState(states, NextEventLoadState.CONTENT)
        withTimeout(5_000) { delay.observerWaitStarted.await() }

        shifts.fail()
        val error = receiveUiState(states, NextEventLoadState.ERROR)
        withTimeout(5_000) { delay.earlyWakeStarted.await() }
        assertEquals(1, shifts.collectionCount)
        delay.allowEarlyWake.complete(Unit)
        withTimeout(5_000) { delay.midnightWaitStarted.await() }
        assertEquals(1, shifts.collectionCount)
        delay.allowMidnight.complete(Unit)
        val nextDayLoading = receiveUiState(states, NextEventLoadState.LOADING)
        withTimeout(5_000) {
            while (shifts.collectionCount < 2) yield()
        }

        assertEquals(LocalDate.of(2026, 8, 15), content.result?.date)
        assertSame(content.result, error.result)
        assertEquals(LocalDate.of(2026, 8, 16), clock.instant().atZone(AppDefaults.zoneId()).toLocalDate())
        assertEquals(null, nextDayLoading.result)
        assertEquals(2, shifts.collectionCount)
        collector.cancelAndJoin()
    }

    @Test
    fun anOldDateFailureImmediatelyObservesTheNewDayAndNeverReplaysYesterday() = runBlocking {
        val clock = AdvancingClock(NOW, AppDefaults.zoneId())
        val shifts = FailAcrossMidnightShiftRepository(listOf(futureShift()))
        val viewModel = NextEventViewModel(
            shifts = shifts,
            explicitDayStatuses = EmptyExplicitStatusRepository(),
            vacations = EmptyVacationRepository(),
            medicalLeaves = store.medicalLeaves,
            shiftActuals = store.shiftActuals,
            workConfiguration = store.workConfiguration,
            clock = clock,
            zoneId = AppDefaults.zoneId(),
            temporalDelay = TemporalDelay { awaitCancellation() },
        )
        val states = Channel<NextEventUiState>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect { state -> states.send(state) }
        }
        val content = receiveUiState(states, NextEventLoadState.CONTENT)

        clock.advance(Duration.ofDays(1))
        shifts.failOldDate()
        val newDayLoading = receiveUiState(states, NextEventLoadState.LOADING)
        withTimeout(5_000) { shifts.newDateObservationStarted.await() }
        shifts.failNewDate()
        val newDayError = receiveUiState(states, NextEventLoadState.ERROR)

        assertEquals(LocalDate.of(2026, 8, 15), content.result?.date)
        assertEquals(null, newDayLoading.result)
        assertEquals(null, newDayError.result)
        assertEquals(2, shifts.collectionCount)
        assertEquals(LocalDate.of(2026, 8, 16), clock.instant().atZone(AppDefaults.zoneId()).toLocalDate())
        collector.cancelAndJoin()
    }

    @Test
    fun cancellingCollectorCancelsBothTheDateSourceAndTemporalWait() = runBlocking {
        val shifts = CancellationTrackingShiftRepository()
        val temporalDelay = CancellationTrackingTemporalDelay()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            NextEventObserver(
                shifts = shifts,
                explicitDayStatuses = EmptyExplicitStatusRepository(),
                vacations = EmptyVacationRepository(),
                medicalLeaves = store.medicalLeaves,
                shiftActuals = store.shiftActuals,
                workConfiguration = store.workConfiguration,
                clock = CLOCK,
                zoneId = AppDefaults.zoneId(),
                temporalDelay = temporalDelay,
            ).observe().collect()
        }

        withTimeout(5_000) {
            shifts.started.await()
            temporalDelay.started.await()
        }
        collector.cancelAndJoin()
        withTimeout(5_000) {
            shifts.cancelled.await()
            temporalDelay.cancelled.await()
        }
        assertTrue(collector.isCancelled)
    }

    private suspend fun receiveUntil(
        channel: Channel<TodayCardProjection>,
        predicate: (TodayCardProjection) -> Boolean,
    ): TodayCardProjection = withTimeout(5_000) {
        var value = channel.receive()
        while (!predicate(value)) value = channel.receive()
        value
    }

    private suspend fun receiveUiState(
        channel: Channel<NextEventUiState>,
        loadState: NextEventLoadState,
    ): NextEventUiState = withTimeout(5_000) {
        var value = channel.receive()
        while (value.loadState != loadState) value = channel.receive()
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
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    private fun completedTodayShift(): Shift {
        val date = NOW.atZone(AppDefaults.zoneId()).toLocalDate()
        val start = ZonedDateTime.of(date, LocalTime.of(6, 0), AppDefaults.zoneId()).toInstant()
        val end = ZonedDateTime.of(date, LocalTime.of(10, 0), AppDefaults.zoneId()).toInstant()
        return futureShift().copy(
            id = UUID.fromString("80000000-0000-0000-0000-000000000002"),
            startAt = start,
            endAt = end,
            localStartDate = date,
            startTimeSnapshot = LocalTime.of(6, 0),
            endTimeSnapshot = LocalTime.of(10, 0),
        )
    }

    private class RecoveringShiftRepository : ShiftRepository {
        var fail = true
        var collectionCount = 0

        override fun observeHasAny(): Flow<Boolean> = MutableStateFlow(false)

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
    }

    private class ContentThenFailShiftRepository(
        private val initial: List<Shift>,
    ) : ShiftRepository {
        private val failure = CompletableDeferred<Unit>()

        fun fail() {
            failure.complete(Unit)
        }

        override fun observeHasAny(): Flow<Boolean> = MutableStateFlow(initial.isNotEmpty())

        override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> = flow {
            emit(initial.filter { shift -> shift.endAt > instantExclusive })
            failure.await()
            error("Fallo ficticio posterior al contenido")
        }

        override fun observeStartingBetween(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<Shift>> = MutableStateFlow(emptyList())

        override suspend fun getById(id: UUID): Shift? = initial.firstOrNull { shift -> shift.id == id }
    }

    private class FailThenBlockShiftRepository(
        private val initial: List<Shift>,
    ) : ShiftRepository {
        private val failure = CompletableDeferred<Unit>()
        var collectionCount = 0
            private set

        fun fail() {
            failure.complete(Unit)
        }

        override fun observeHasAny(): Flow<Boolean> = MutableStateFlow(initial.isNotEmpty())

        override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> = flow {
            collectionCount += 1
            if (collectionCount == 1) {
                emit(initial.filter { shift -> shift.endAt > instantExclusive })
                failure.await()
                error("Fallo ficticio posterior al contenido")
            }
            awaitCancellation()
        }

        override fun observeStartingBetween(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<Shift>> = MutableStateFlow(emptyList())

        override suspend fun getById(id: UUID): Shift? = initial.firstOrNull { shift -> shift.id == id }
    }

    private class FailAcrossMidnightShiftRepository(
        private val initial: List<Shift>,
    ) : ShiftRepository {
        private val oldDateFailure = CompletableDeferred<Unit>()
        private val newDateFailure = CompletableDeferred<Unit>()
        val newDateObservationStarted = CompletableDeferred<Unit>()
        var collectionCount = 0
            private set

        fun failOldDate() {
            oldDateFailure.complete(Unit)
        }

        fun failNewDate() {
            newDateFailure.complete(Unit)
        }

        override fun observeHasAny(): Flow<Boolean> = MutableStateFlow(initial.isNotEmpty())

        override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> = flow {
            collectionCount += 1
            if (collectionCount == 1) {
                emit(initial.filter { shift -> shift.endAt > instantExclusive })
                oldDateFailure.await()
                error("Fallo ficticio de la fecha anterior")
            }
            newDateObservationStarted.complete(Unit)
            newDateFailure.await()
            error("Fallo ficticio de la fecha nueva antes de emitir contenido")
        }

        override fun observeStartingBetween(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<Shift>> = MutableStateFlow(emptyList())

        override suspend fun getById(id: UUID): Shift? = initial.firstOrNull { shift -> shift.id == id }
    }

    private class TrackingSectorShiftActualRepository(
        private val delegate: ShiftActualRepository,
        private val values: Map<WorkSector, Map<UUID, ShiftActualAggregate>>,
    ) : ShiftActualRepository by delegate {
        val queriedSectors = mutableListOf<WorkSector>()

        override fun observeAllActuals(
            timelineId: UUID,
            sector: WorkSector,
        ): Flow<Map<UUID, ShiftActualAggregate>> {
            queriedSectors += sector
            return MutableStateFlow(values[sector].orEmpty())
        }
    }

    private class StaticShiftRepository(
        private val values: List<Shift>,
    ) : ShiftRepository {
        override fun observeHasAny(): Flow<Boolean> = MutableStateFlow(values.isNotEmpty())

        override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> =
            MutableStateFlow(values.filter { it.endAt > instantExclusive })

        override fun observeStartingBetween(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<Shift>> = MutableStateFlow(emptyList())

        override suspend fun getById(id: UUID): Shift? = values.firstOrNull { it.id == id }
    }

    private class MutableShiftRepository : ShiftRepository {
        private val values = MutableStateFlow<List<Shift>>(emptyList())

        fun replace(replacement: List<Shift>) {
            values.value = replacement
        }

        override fun observeHasAny(): Flow<Boolean> = values.map { shifts -> shifts.isNotEmpty() }

        override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> = values.map { shifts ->
            shifts.filter { shift -> shift.endAt > instantExclusive }
        }

        override fun observeStartingBetween(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<Shift>> = values.map { shifts ->
            shifts.filter { shift -> shift.localStartDate in startDateInclusive..endDateInclusive }
        }

        override suspend fun getById(id: UUID): Shift? = values.value.firstOrNull { shift -> shift.id == id }
    }

    private class CancellationTrackingShiftRepository : ShiftRepository {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()

        override fun observeHasAny(): Flow<Boolean> = MutableStateFlow(false)

        override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> = flow {
            started.complete(Unit)
            try {
                emit(emptyList())
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }

        override fun observeStartingBetween(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<Shift>> = MutableStateFlow(emptyList())

        override suspend fun getById(id: UUID): Shift? = null
    }

    private class CancellationTrackingTemporalDelay : TemporalDelay {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()

        override suspend fun await(duration: Duration) {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
    }

    private class RecordingTemporalDelay : TemporalDelay {
        val durations = Channel<Duration>(Channel.UNLIMITED)

        override suspend fun await(duration: Duration) {
            durations.send(duration)
            awaitCancellation()
        }
    }

    private class FailureThenMidnightDelay(
        private val clock: AdvancingClock,
    ) : TemporalDelay {
        val observerWaitStarted = CompletableDeferred<Unit>()
        val earlyWakeStarted = CompletableDeferred<Unit>()
        val allowEarlyWake = CompletableDeferred<Unit>()
        val midnightWaitStarted = CompletableDeferred<Unit>()
        val allowMidnight = CompletableDeferred<Unit>()
        private var invocationCount = 0

        override suspend fun await(duration: Duration) {
            invocationCount += 1
            when (invocationCount) {
                1 -> {
                    observerWaitStarted.complete(Unit)
                    awaitCancellation()
                }

                2 -> {
                    earlyWakeStarted.complete(Unit)
                    allowEarlyWake.await()
                }

                else -> {
                    midnightWaitStarted.complete(Unit)
                    allowMidnight.await()
                    clock.advance(duration)
                }
            }
        }
    }

    private class TrackingShiftRepository : ShiftRepository {
        val queriedCutoffs = mutableListOf<Instant>()
        val cancellationsAtSubscription = mutableListOf<Int>()
        private var cancellationCount = 0

        override fun observeHasAny(): Flow<Boolean> = MutableStateFlow(false)

        override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> = flow {
            queriedCutoffs += instantExclusive
            cancellationsAtSubscription += cancellationCount
            try {
                emit(emptyList())
                awaitCancellation()
            } finally {
                cancellationCount += 1
            }
        }

        override fun observeStartingBetween(
            startDateInclusive: LocalDate,
            endDateInclusive: LocalDate,
        ): Flow<List<Shift>> = MutableStateFlow(emptyList())

        override suspend fun getById(id: UUID): Shift? = null
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
        override suspend fun setAll(dates: Set<LocalDate>, type: ExplicitDayStatusType) = Unit
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
