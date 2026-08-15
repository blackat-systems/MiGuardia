package com.blackatsystems.miguardia.ui.nextevent

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

data class NextEventSourceData(
    val shifts: List<Shift>,
    val explicitDayStatuses: List<ExplicitDayStatus>,
    val vacations: List<Vacation>,
)

fun interface TemporalDelay {
    suspend fun await(duration: Duration)
}

class NextEventObserver(
    private val shifts: ShiftRepository,
    private val explicitDayStatuses: ExplicitDayStatusRepository,
    private val vacations: VacationRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val temporalDelay: TemporalDelay = TemporalDelay { duration ->
        delay(duration.toMillis().coerceAtLeast(1L))
    },
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe(): Flow<NextEventResult> {
        val subscriptionInstant = clock.instant()
        val subscriptionDate = subscriptionInstant.atZone(zoneId).toLocalDate()
        return combine(
            shifts.observeEndingAfter(subscriptionInstant),
            explicitDayStatuses.observeFrom(subscriptionDate),
            vacations.observeEndingOnOrAfter(subscriptionDate),
            ::NextEventSourceData,
        ).flatMapLatest { source ->
            flow {
                while (currentCoroutineContext().isActive) {
                    val now = clock.instant()
                    val result = projectNextEvent(
                        now = now,
                        zoneId = zoneId,
                        shifts = source.shifts,
                        explicitDayStatuses = source.explicitDayStatuses,
                        vacations = source.vacations,
                    )
                    emit(result)
                    temporalDelay.await(nextRefreshDelay(now, zoneId, result))
                    if (clock.instant() <= now) return@flow
                }
            }
        }
    }
}

internal fun nextRefreshDelay(
    now: java.time.Instant,
    zoneId: ZoneId,
    result: NextEventResult,
): Duration {
    val nextLocalMidnight = ZonedDateTime.of(
        now.atZone(zoneId).toLocalDate().plusDays(1),
        LocalTime.MIDNIGHT,
        zoneId,
    ).toInstant()
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
