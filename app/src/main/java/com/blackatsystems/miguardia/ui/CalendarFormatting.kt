package com.blackatsystems.miguardia.ui

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal val SpanishArgentina = Locale.forLanguageTag("es-AR")
private val FullDateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SpanishArgentina)
internal val ShiftTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SpanishArgentina)

internal fun com.blackatsystems.miguardia.core.domain.model.Shift.timeRange(): String =
    "${startTimeSnapshot.format(ShiftTimeFormatter)}–${endTimeSnapshot.format(ShiftTimeFormatter)}"

internal fun LocalDate.fullDisplayName(): String = format(FullDateFormatter)
    .replaceFirstChar { it.titlecase(SpanishArgentina) }

internal fun YearMonth.displayName(): String {
    val monthName = month.getDisplayName(TextStyle.FULL, SpanishArgentina)
        .replaceFirstChar { it.titlecase(SpanishArgentina) }
    return "$monthName de $year"
}
