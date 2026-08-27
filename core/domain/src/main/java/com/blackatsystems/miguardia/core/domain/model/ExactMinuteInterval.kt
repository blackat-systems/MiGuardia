package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant

/** Un intervalo positivo cuyos extremos y duración se expresan en minutos enteros exactos. */
data class ExactMinuteInterval(
    val start: Instant,
    val end: Instant,
) {
    init {
        require(durationMinutes > 0L) { "Un intervalo exacto debe tener duración positiva" }
    }

    val durationMinutes: Long
        get() = exactDurationMinutes(start, end)
}

fun clipExactMinuteInterval(
    start: Instant,
    end: Instant,
    boundaryStart: Instant,
    boundaryEnd: Instant,
): ExactMinuteInterval? {
    val clippedStart = maxOf(start, boundaryStart)
    val clippedEnd = minOf(end, boundaryEnd)
    return if (clippedStart < clippedEnd) ExactMinuteInterval(clippedStart, clippedEnd) else null
}

fun mergeExactMinuteIntervals(intervals: Iterable<ExactMinuteInterval>): List<ExactMinuteInterval> {
    val ordered = intervals.sortedWith(compareBy(ExactMinuteInterval::start, ExactMinuteInterval::end))
    if (ordered.isEmpty()) return emptyList()
    val merged = mutableListOf<ExactMinuteInterval>()
    ordered.forEach { interval ->
        val previous = merged.lastOrNull()
        if (previous == null || interval.start > previous.end) {
            merged += interval
        } else if (interval.end > previous.end) {
            merged[merged.lastIndex] = ExactMinuteInterval(previous.start, interval.end)
        }
    }
    return merged
}

fun subtractExactMinuteIntervals(
    base: ExactMinuteInterval,
    occupied: Iterable<ExactMinuteInterval>,
): List<ExactMinuteInterval> {
    val clipped = mergeExactMinuteIntervals(
        occupied.mapNotNull { interval ->
            clipExactMinuteInterval(interval.start, interval.end, base.start, base.end)
        },
    )
    return buildList {
        var cursor = base.start
        clipped.forEach { interval ->
            if (cursor < interval.start) add(ExactMinuteInterval(cursor, interval.start))
            if (interval.end > cursor) cursor = interval.end
        }
        if (cursor < base.end) add(ExactMinuteInterval(cursor, base.end))
    }
}

fun sumExactMinuteIntervals(intervals: Iterable<ExactMinuteInterval>): Long =
    intervals.fold(0L) { total, interval -> Math.addExact(total, interval.durationMinutes) }
