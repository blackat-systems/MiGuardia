package com.blackatsystems.miguardia.ui.nextevent

import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardProjection
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.nextevent.projectTodayCard
import com.blackatsystems.miguardia.core.domain.repository.AvailabilityWindowRepository
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.IndependentExtraWorkRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

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
    shifts: V2ShiftRepository,
    availabilityWindows: AvailabilityWindowRepository,
    shiftActuals: ShiftActualRepository,
    independentExtras: IndependentExtraWorkRepository,
    explicitDayStatuses: ExplicitDayStatusRepository,
    vacations: VacationRepository,
    medicalLeaves: MedicalLeaveRepository,
    workConfiguration: WorkConfigurationRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val temporalDelay: TemporalDelay = TemporalDelay { duration ->
        delay(duration.toMillis().coerceAtLeast(1L))
    },
) {
    private val sources = V2WorkEventSourceObserver(
        shifts = shifts,
        availabilityWindows = availabilityWindows,
        shiftActuals = shiftActuals,
        independentExtras = independentExtras,
        explicitDayStatuses = explicitDayStatuses,
        vacations = vacations,
        medicalLeaves = medicalLeaves,
        workConfiguration = workConfiguration,
    )

    fun observe(): Flow<TodayCardProjection> = observeStates()
        .filterIsInstance<NextEventObservation.Content>()
        .map { content -> content.projection }

    internal fun observeStates(): Flow<NextEventObservation> = flow {
        coroutineScope {
            while (currentCoroutineContext().isActive) {
                val observedDate = clock.instant().atZone(zoneId).toLocalDate()
                emit(NextEventObservation.Loading(observedDate))
                val updates = Channel<NextEventSourceData>(Channel.CONFLATED)
                val sourceJob = launch {
                    try {
                        sources.observe(observedDate).collect { source -> updates.send(source) }
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
                    while (!observeAnotherDate && currentCoroutineContext().isActive) {
                        val now = clock.instant()
                        if (now.atZone(zoneId).toLocalDate() != observedDate) {
                            observeAnotherDate = true
                            continue
                        }
                        val projection = source.project(now)
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
                    throw NextEventObservationFailure(observedDate, error)
                } finally {
                    sourceJob.cancelAndJoin()
                    updates.cancel()
                }
            }
        }
    }

    private fun NextEventSourceData.project(now: java.time.Instant): TodayCardProjection {
        val future = projectNextEvent(
            now = now,
            zoneId = zoneId,
            input = toInput(),
        )
        return projectTodayCard(
            now = now,
            zoneId = zoneId,
            shifts = shifts,
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
    val eventBoundary = result.events
        .asSequence()
        .flatMap { event -> sequenceOf(event.start, event.end) }
        .filter { boundary -> boundary > now }
        .minOrNull()
    val nextMinute = if (result.primaryEvent != NextEventPrimary.NONE && result.primaryEvent != NextEventPrimary.DAY_OFF) {
        now.truncatedTo(ChronoUnit.MINUTES).plus(1L, ChronoUnit.MINUTES)
    } else {
        null
    }
    val boundary = listOfNotNull(nextLocalMidnight, eventBoundary, nextMinute).minOrNull()
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
