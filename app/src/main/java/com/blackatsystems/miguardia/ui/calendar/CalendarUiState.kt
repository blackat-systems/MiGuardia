package com.blackatsystems.miguardia.ui.calendar

import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarLoadState {
    LOADING,
    CONTENT,
    ERROR,
}

enum class CalendarInteractionMode {
    VIEW,
    EDIT,
}

data class CalendarUiState(
    val visibleMonth: YearMonth,
    val referenceInstant: Instant,
    val days: List<CalendarDay> = emptyList(),
    val detailDate: LocalDate? = null,
    val editSelectedDates: Set<LocalDate> = emptySet(),
    val interactionMode: CalendarInteractionMode = CalendarInteractionMode.VIEW,
    val hasAnyShifts: Boolean = true,
    val loadState: CalendarLoadState = CalendarLoadState.LOADING,
    val errorMessage: String? = null,
)

internal fun calendarInteractionModeFromSaved(value: String?): CalendarInteractionMode =
    CalendarInteractionMode.entries.firstOrNull { it.name == value } ?: CalendarInteractionMode.VIEW

internal fun CalendarUiState.enterEditing(selectedDate: LocalDate? = null): CalendarUiState = copy(
    interactionMode = CalendarInteractionMode.EDIT,
    detailDate = null,
    editSelectedDates = selectedDate?.let(::setOf).orEmpty(),
)

internal fun CalendarUiState.finishEditing(): CalendarUiState = copy(
    interactionMode = CalendarInteractionMode.VIEW,
    editSelectedDates = emptySet(),
)

internal fun CalendarUiState.toggleEditDate(date: LocalDate): CalendarUiState {
    if (interactionMode != CalendarInteractionMode.EDIT || YearMonth.from(date) != visibleMonth) return this
    return copy(
        editSelectedDates = if (date in editSelectedDates) editSelectedDates - date else editSelectedDates + date,
    )
}

internal fun firstShiftDate(visibleMonth: YearMonth, today: LocalDate): LocalDate =
    if (YearMonth.from(today) == visibleMonth) today else visibleMonth.atDay(1)
