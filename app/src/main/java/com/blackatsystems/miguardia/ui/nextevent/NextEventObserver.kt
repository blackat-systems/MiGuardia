package com.blackatsystems.miguardia.ui.nextevent

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardProjection
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.nextevent.projectTodayCard
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

data class NextEventSourceData(
    val shifts: List<Shift>,
    val explicitDayStatuses: List<ExplicitDayStatus>,
    val vacations: List<Vacation>,
    val medicalLeaves: List<MedicalLeave>,
    val actualsByShiftId: Map<UUID, ShiftActualAggregate>,
)

internal sealed interface NextEventObservation {
    data class Loading(val date: LocalDate) : NextEventObservation
    data class Content(val projection: TodayCardProjection) : NextEventObservation
}

internal class NextEventObservationFailure(
    val observedDate: LocalDate,
    cause: Throwable,
) : RuntimeException(cause)

fun interface TemporalDelay {
    suspend fun await(duration: Duration)
}

class NextEventObserver(
    private val shifts: ShiftRepository,
    private val explicitDayStatuses: ExplicitDayStatusRepository,
    private val vacations: VacationRepository,
    private val medicalLeaves: MedicalLeaveRepository,
    private val shiftActuals: ShiftActualRepository,
    private val workConfiguration: WorkConfigurationRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val temporalDelay: TemporalDelay = TemporalDelay { duration ->
        delay(duration.toMillis().coerceAtLeast(1L))
    },
) {
    fun observe(): Flow<TodayCardProjection> = observeStates()
            .filterIsInstance<NextEventObservation.Content>()
            .map { content -> content.projection }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    internal fun observeStates(): Flow<NextEventObservation> = flow {
        coroutineScope {
            while (currentCoroutineContext().isActive) {
                val observedDate = clock.instant().atZone(zoneId).toLocalDate()
                emit(NextEventObservation.Loading(observedDate))
                val updates = Channel<NextEventSourceData>(Channel.CONFLATED)
                val sourceJob = launch {
                    try {
                        observeDate(observedDate).collect { source -> updates.send(source) }
                        updates.close()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        updates.close(error)
                    }
                }
                try {
                    var source = updates.receiveCatching().getOrThrow()
                    var observeAnotherDate = false
                    while (
                        !observeAnotherDate &&
                        currentCoroutineContext().isActive
                    ) {
                        val now = clock.instant()
                        if (now.atZone(zoneId).toLocalDate() != observedDate) {
                            observeAnotherDate = true
                            continue
                        }
                        val projection = source.project(now, observedDate)
                        emit(NextEventObservation.Content(projection))
                        when (
                            val wakeup = awaitSourceOrTime(
                                updates = updates,
                                duration = nextRefreshDelay(now, zoneId, projection),
                                temporalDelay = temporalDelay,
                            )
                        ) {
                            is ObserverWakeup.Source -> source = wakeup.value
                            ObserverWakeup.Time -> Unit
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    throw NextEventObservationFailure(
                        observedDate = observedDate,
                        cause = error,
                    )
                } finally {
                    sourceJob.cancelAndJoin()
                    updates.cancel()
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeDate(date: LocalDate): Flow<NextEventSourceData> {
        val startOfDay = date.atStartOfDay(zoneId).toInstant()
        return shifts.observeEndingAfter(startOfDay).flatMapLatest { observedShifts ->
            val lastRelevantDate = observedShifts
                .maxOfOrNull(Shift::localStartDate)
                ?.coerceAtLeast(date)
                ?: date
            combine(
                explicitDayStatuses.observeFrom(date),
                vacations.observeEndingOnOrAfter(date.minusDays(1)),
                medicalLeaves.observeIntersecting(date.minusDays(1), lastRelevantDate),
                observeActuals(),
            ) { statuses, observedVacations, observedMedicalLeaves, actuals ->
                NextEventSourceData(
                    shifts = observedShifts,
                    explicitDayStatuses = statuses,
                    vacations = observedVacations,
                    medicalLeaves = observedMedicalLeaves,
                    actualsByShiftId = actuals,
                )
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeActuals(): Flow<Map<UUID, ShiftActualAggregate>> =
        workConfiguration.observe().flatMapLatest { history ->
            val timeline = history?.timeline
                ?: return@flatMapLatest flowOf(emptyMap())
            val sectors = timeline.revisions
                .map { revision -> revision.value.sector }
                .distinct()
            if (sectors.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    sectors.map { sector ->
                        shiftActuals.observeAllActuals(
                            timelineId = timeline.id,
                            sector = sector,
                        )
                    },
                ) { actualsBySector ->
                    buildMap {
                        actualsBySector.forEach { actuals -> putAll(actuals) }
                    }
                }
            }
        }

    private fun NextEventSourceData.project(
        now: java.time.Instant,
        date: LocalDate,
    ): TodayCardProjection {
        val future = projectNextEvent(
            now = now,
            zoneId = zoneId,
            shifts = shifts,
            explicitDayStatuses = explicitDayStatuses,
            vacations = vacations,
            medicalLeaves = medicalLeaves,
            actualShiftIds = actualsByShiftId.keys,
        )
        return projectTodayCard(
            now = now,
            zoneId = zoneId,
            todayShifts = shifts.filter { shift -> shift.localStartDate == date },
            previousDayCandidates = shifts.filter { shift -> shift.localStartDate == date.minusDays(1) },
            actualsByShiftId = actualsByShiftId,
            vacations = vacations,
            medicalLeaves = medicalLeaves,
            futureEvent = future,
        )
    }

}

internal suspend fun awaitSourceOrTime(
    updates: ReceiveChannel<NextEventSourceData>,
    duration: Duration,
    temporalDelay: TemporalDelay,
): ObserverWakeup = coroutineScope {
        val timeAwait = async { temporalDelay.await(duration) }
        try {
            select {
                updates.onReceiveCatching { source ->
                    ObserverWakeup.Source(source.getOrThrow())
                }
                timeAwait.onAwait { ObserverWakeup.Time }
            }
        } finally {
            timeAwait.cancelAndJoin()
        }
}

internal sealed interface ObserverWakeup {
    data class Source(val value: NextEventSourceData) : ObserverWakeup
    data object Time : ObserverWakeup
}

internal fun nextRefreshDelay(
    now: java.time.Instant,
    zoneId: ZoneId,
    projection: TodayCardProjection,
): Duration = when (projection.primary) {
    TodayCardPrimary.COMPLETED_SUMMARY,
    TodayCardPrimary.EMPTY,
    -> durationUntilNextMidnight(now, zoneId)

    TodayCardPrimary.ONGOING_SHIFT,
    TodayCardPrimary.UPCOMING_SHIFT,
    TodayCardPrimary.NO_WORK_TODAY,
    TodayCardPrimary.FUTURE_EVENT,
    -> nextRefreshDelay(now, zoneId, projection.futureEvent)
}

internal fun nextRefreshDelay(
    now: java.time.Instant,
    zoneId: ZoneId,
    result: NextEventResult,
): Duration {
    val nextLocalMidnight = now.plus(durationUntilNextMidnight(now, zoneId))
    val shiftBoundary = sequenceOf(
        result.ongoingShifts.asSequence().map(Shift::endAt),
        result.upcomingShifts.asSequence().map(Shift::startAt),
    )
        .flatten()
        .filter { boundary -> boundary > now }
        .minOrNull()
    val nextMinute = if (
        result.primaryEvent == NextEventPrimary.ONGOING_SHIFT ||
        result.primaryEvent == NextEventPrimary.UPCOMING_SHIFT
    ) {
        now.truncatedTo(ChronoUnit.MINUTES).plus(1L, ChronoUnit.MINUTES)
    } else {
        null
    }
    val boundary = listOfNotNull(nextLocalMidnight, shiftBoundary, nextMinute).minOrNull()
        ?: nextLocalMidnight
    return Duration.between(now, boundary).let { duration ->
        if (duration.isNegative || duration.isZero) Duration.ofMillis(1L) else duration
    }
}

private fun durationUntilNextMidnight(
    now: java.time.Instant,
    zoneId: ZoneId,
): Duration {
    val nextLocalMidnight = ZonedDateTime.of(
        now.atZone(zoneId).toLocalDate().plusDays(1),
        LocalTime.MIDNIGHT,
        zoneId,
    ).toInstant()
    return Duration.between(now, nextLocalMidnight).let { duration ->
        if (duration.isNegative || duration.isZero) Duration.ofMillis(1L) else duration
    }
}
