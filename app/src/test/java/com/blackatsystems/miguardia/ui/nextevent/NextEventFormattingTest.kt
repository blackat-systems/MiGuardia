package com.blackatsystems.miguardia.ui.nextevent

import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventInput
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.nextevent.projectTodayCard
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEventFormattingTest {
    @Test
    fun remainingUsesHumanDaysHoursAndMinutesWithoutSeconds() {
        assertEquals("1 d 4 h 30 min", formatRemaining(Duration.ofMinutes(1_710)))
        assertEquals("5 h 20 min", formatRemaining(Duration.ofMinutes(320)))
        assertEquals("0 min", formatRemaining(Duration.ofSeconds(59)))
        assertEquals("0 min", formatRemaining(Duration.ofMinutes(-1)))
    }

    @Test
    fun dayOffUsesCivilDateLabels() {
        val today = LocalDate.of(2026, 12, 31)
        assertEquals("Hoy", dayDistanceLabel(today, today))
        assertEquals("Mañana", dayDistanceLabel(today, LocalDate.of(2027, 1, 1)))
        assertEquals("En 3 días", dayDistanceLabel(today, LocalDate.of(2027, 1, 3)))
    }

    @Test
    fun activeEventRefreshesAtMinuteBoundaryWithoutPerSecondPolling() {
        val now = Instant.parse("2026-08-15T15:00:42Z")
        val result = projectNextEvent(
            now = now,
            zoneId = ZONE,
            input = emptyInput(
                availability = listOf(
                    availability(
                        start = Instant.parse("2026-08-15T16:00:00Z"),
                        end = Instant.parse("2026-08-15T18:00:00Z"),
                    ),
                ),
            ),
        )

        val delay = nextRefreshDelay(now = now, zoneId = ZONE, result = result)

        assertEquals(Duration.ofSeconds(18), delay)
        assertTrue(delay > Duration.ofSeconds(1))
    }

    @Test
    fun emptySummaryDoesNotPollForAHiddenFutureCountdown() {
        val now = Instant.parse("2026-08-15T15:00:42Z")
        val future = projectNextEvent(now = now, zoneId = ZONE, input = emptyInput())
        val projection = projectTodayCard(
            now = now,
            zoneId = ZONE,
            shifts = emptyList(),
            actualsByShiftId = emptyMap(),
            vacations = emptyList(),
            medicalLeaves = emptyList(),
            futureEvent = future,
        )

        val delay = nextRefreshDelay(now = now, zoneId = ZONE, projection = projection)
        val nextMidnight = projection.date.plusDays(1).atStartOfDay(ZONE).toInstant()

        assertEquals(Duration.between(now, nextMidnight), delay)
        assertTrue(delay > Duration.ofHours(11))
    }

    @Test
    fun aBufferedSourceUpdateWinsWhenTheTimerIsAlsoReady() = runBlocking {
        val source = sourceData()
        val updates = Channel<NextEventSourceData>(Channel.CONFLATED)
        updates.send(source)

        val result = awaitSourceOrTime(
            updates = updates,
            duration = Duration.ZERO,
            temporalDelay = TemporalDelay { },
        )

        assertEquals(ObserverWakeup.Source(source), result)
        updates.cancel()
    }

    @Test
    fun aTimerWakeDoesNotConsumeTheNextSourceUpdate() = runBlocking {
        val updates = Channel<NextEventSourceData>(Channel.CONFLATED)

        assertEquals(
            ObserverWakeup.Time,
            awaitSourceOrTime(
                updates = updates,
                duration = Duration.ZERO,
                temporalDelay = TemporalDelay { },
            ),
        )

        val source = sourceData()
        updates.send(source)
        assertSame(source, updates.receive())
        updates.cancel()
    }

    private fun sourceData(): NextEventSourceData = NextEventSourceData(
        shifts = emptyList(),
        availabilityWindows = emptyList(),
        actualsByShiftId = emptyMap(),
        independentExtras = emptyList(),
        explicitDayStatuses = emptyList(),
        vacations = emptyList(),
        medicalLeaves = emptyList(),
    )

    private fun emptyInput(
        availability: List<AvailabilityWindowRecord> = emptyList(),
    ): NextEventInput = NextEventInput(
        shifts = emptyList(),
        availabilityWindows = availability,
        actualsByShiftId = emptyMap(),
        independentExtras = emptyList(),
        explicitDayStatuses = emptyList(),
        vacations = emptyList(),
        medicalLeaves = emptyList(),
    )

    private fun availability(start: Instant, end: Instant): AvailabilityWindowRecord =
        AvailabilityWindowRecord(
            id = UUID.fromString("00000000-0000-0000-0000-000000000801"),
            timelineId = UUID.fromString("00000000-0000-0000-0000-000000000802"),
            sector = WorkSector.MEDICINE,
            configurationRevisionId = UUID.fromString("00000000-0000-0000-0000-000000000803"),
            ownerLocalDate = start.atZone(ZONE).toLocalDate(),
            zoneId = ZONE,
            start = start,
            end = end,
            labelSnapshot = "Guardia pasiva",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private companion object {
        val ZONE: ZoneId = AppDefaults.zoneId()
    }
}
