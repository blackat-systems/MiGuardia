package com.blackatsystems.miguardia.ui.calendar

import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import java.time.Instant
import java.time.YearMonth

enum class CalendarLoadState {
    LOADING,
    CONTENT,
    ERROR,
}

data class CalendarUiState(
    val visibleMonth: YearMonth,
    val referenceInstant: Instant,
    val days: List<CalendarDay> = emptyList(),
    val selectedDate: java.time.LocalDate? = null,
    val loadState: CalendarLoadState = CalendarLoadState.LOADING,
    val errorMessage: String? = null,
)
