package com.blackatsystems.miguardia.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal enum class EventTimeRangeStyle {
    DETAIL,
    COMPACT,
    COMPACT_DATED,
    CALENDAR,
}

/** Formats the actual civil endpoints; an event's owner date is a separate navigation concern. */
internal fun formatEventTimeRange(
    start: Instant,
    end: Instant,
    zoneId: ZoneId,
    style: EventTimeRangeStyle,
): String {
    val localStart = start.atZone(zoneId)
    val localEnd = end.atZone(zoneId)
    val startDate = localStart.toLocalDate()
    val endDate = localEnd.toLocalDate()
    val dayOffset = ChronoUnit.DAYS.between(startDate, endDate)
    val times = "${localStart.format(EventTimeFormatter)}–${localEnd.format(EventTimeFormatter)}"

    if (style == EventTimeRangeStyle.CALENDAR) {
        val wholeHours = localStart.toLocalTime().let { it.minute == 0 && it.second == 0 && it.nano == 0 } &&
            localEnd.toLocalTime().let { it.minute == 0 && it.second == 0 && it.nano == 0 }
        if (dayOffset == 0L && wholeHours) {
            return "${localStart.format(EventHourFormatter)}–${localEnd.format(EventHourFormatter)}"
        }
        return times + if (dayOffset > 0L) " +$dayOffset" else ""
    }

    if (style == EventTimeRangeStyle.DETAIL ||
        startDate.year != endDate.year || startDate.month != endDate.month
    ) {
        return "${localStart.format(EventDateTimeFormatter)} – ${localEnd.format(EventDateTimeFormatter)}"
    }

    val compact = times + when {
        dayOffset == 1L -> " (+1 día)"
        dayOffset > 1L -> " (+$dayOffset días)"
        else -> ""
    }
    return if (style == EventTimeRangeStyle.COMPACT_DATED) {
        "${startDate.format(EventDateFormatter)} · $compact"
    } else {
        compact
    }
}

private val EventLocale = Locale.forLanguageTag("es-AR")
private val EventTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", EventLocale)
private val EventHourFormatter = DateTimeFormatter.ofPattern("HH", EventLocale)
private val EventDateFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", EventLocale)
private val EventDateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", EventLocale)
