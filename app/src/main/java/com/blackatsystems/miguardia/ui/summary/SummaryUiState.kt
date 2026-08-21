package com.blackatsystems.miguardia.ui.summary

import com.blackatsystems.miguardia.core.domain.hours.MonthlyHoursSummary
import com.blackatsystems.miguardia.core.domain.hours.emptyMonthlyHoursSummary
import java.time.Instant
import java.time.YearMonth

enum class SummaryLoadState {
    LOADING,
    CONTENT,
    ERROR,
}

data class SummaryUiState(
    val visibleMonth: YearMonth,
    val referenceInstant: Instant,
    val summary: MonthlyHoursSummary = emptyMonthlyHoursSummary(visibleMonth, referenceInstant),
    val loadState: SummaryLoadState = SummaryLoadState.LOADING,
    val errorMessage: String? = null,
)
