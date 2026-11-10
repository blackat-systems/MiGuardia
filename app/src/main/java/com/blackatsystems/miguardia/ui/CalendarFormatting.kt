package com.blackatsystems.miguardia.ui

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal val SpanishArgentina = Locale.forLanguageTag("es-AR")
private val FullDateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SpanishArgentina)
private val ShortDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", SpanishArgentina)
internal val ShiftTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SpanishArgentina)

internal fun com.blackatsystems.miguardia.core.domain.model.Shift.timeRange(): String =
    formatEventTimeRange(startAt, endAt, zoneId, EventTimeRangeStyle.COMPACT)

/** Compact only whole-hour daytime shifts; keep minutes and mark the next-day end. */
internal fun com.blackatsystems.miguardia.core.domain.model.Shift.calendarTimeRange(): String =
    formatEventTimeRange(startAt, endAt, zoneId, EventTimeRangeStyle.CALENDAR)

internal fun LocalDate.fullDisplayName(): String = format(FullDateFormatter)
    .replaceFirstChar { it.titlecase(SpanishArgentina) }

internal fun LocalDate.shortDisplayDate(): String = format(ShortDateFormatter)

internal fun LocalDate.dayEditorDisplayName(): String {
    val weekday = dayOfWeek.getDisplayName(TextStyle.FULL, SpanishArgentina)
        .replaceFirstChar { it.titlecase(SpanishArgentina) }
    val month = month.getDisplayName(TextStyle.FULL, SpanishArgentina)
        .replaceFirstChar { it.titlecase(SpanishArgentina) }
    return "$weekday $dayOfMonth de $month del $year"
}

internal fun YearMonth.displayName(): String {
    val monthName = month.getDisplayName(TextStyle.FULL, SpanishArgentina)
        .replaceFirstChar { it.titlecase(SpanishArgentina) }
    return "$monthName de $year"
}
