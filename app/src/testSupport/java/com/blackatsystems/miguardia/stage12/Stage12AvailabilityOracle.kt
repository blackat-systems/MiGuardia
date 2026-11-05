package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.model.AvailabilityBreakdown
import com.blackatsystems.miguardia.core.domain.model.AvailabilityTemporalState
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

internal object Stage12AvailabilityOracle {
    data class Span(val start: Instant, val end: Instant) {
        init {
            require(start < end)
            require(start == start.truncatedTo(ChronoUnit.MINUTES))
            require(end == end.truncatedTo(ChronoUnit.MINUTES))
        }
    }

    data class Expected(
        val state: AvailabilityTemporalState,
        val programmed: Long,
        val effective: Long,
        val replaced: Long,
        val pending: Long,
        val projected: Long,
        val futureReplaced: Long,
    )

    fun expect(window: Span, active: List<Span>, protected: Boolean, observedNow: Instant): Expected {
        val programmed = minutes(window.start, window.end)
        if (protected) {
            return Expected(AvailabilityTemporalState.PROTECTED, programmed, 0, 0, 0, 0, 0)
        }
        val minute = observedNow.truncatedTo(ChronoUnit.MINUTES)
        val elapsedEnd = minute.coerceIn(window.start, window.end)
        val elapsed = minutes(window.start, elapsedEnd)
        val occupied = mergedUnion(active.mapNotNull { span -> intersection(span, window) })
        val replaced = occupied.sumOf { span ->
            minutes(span.start, maxOf(span.start, minOf(span.end, elapsedEnd)))
        }
        val occupiedAll = occupied.sumOf { minutes(it.start, it.end) }
        val futureReplaced = occupiedAll - replaced
        val state = when {
            minute < window.start -> AvailabilityTemporalState.FUTURE
            minute < window.end -> AvailabilityTemporalState.IN_PROGRESS
            else -> AvailabilityTemporalState.COMPLETED
        }
        return Expected(
            state = state,
            programmed = programmed,
            effective = elapsed - replaced,
            replaced = replaced,
            pending = programmed - elapsed - futureReplaced,
            projected = programmed - occupiedAll,
            futureReplaced = futureReplaced,
        )
    }

    fun requireExact(actual: AvailabilityBreakdown, expected: Expected) {
        val observed = Expected(
            actual.state,
            actual.programmedMinutes,
            actual.effectiveElapsedMinutes,
            actual.replacedElapsedMinutes,
            actual.futurePendingMinutes,
            actual.effectiveProjectedAtEndMinutes,
            actual.futureOccupiedByPlannedWorkMinutes,
        )
        check(observed == expected) { "Disponibilidad: esperado $expected, observado $observed" }
    }

    private fun intersection(first: Span, second: Span): Span? {
        val start = maxOf(first.start, second.start)
        val end = minOf(first.end, second.end)
        return if (start < end) Span(start, end) else null
    }

    private fun mergedUnion(spans: List<Span>): List<Span> {
        val union = mutableListOf<Span>()
        for (span in spans.sortedBy { it.start }) {
            val last = union.lastOrNull()
            if (last == null || last.end < span.start) {
                union.add(span)
            } else {
                union[union.lastIndex] = Span(last.start, maxOf(last.end, span.end))
            }
        }
        return union
    }

    private fun minutes(start: Instant, end: Instant): Long = Duration.between(start, end).toMinutes()
}
