package com.blackatsystems.miguardia.ui.vacation

import com.blackatsystems.miguardia.core.domain.model.Vacation
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class VacationSurface { NONE, LIST, EDITOR }

data class VacationDraft(
    val editingId: UUID? = null,
    val startDate: LocalDate? = null,
    val endDateInclusive: LocalDate? = null,
    val isDirty: Boolean = false,
) {
    val inclusiveDayCount: Long?
        get() = if (
            startDate != null && endDateInclusive != null && !endDateInclusive.isBefore(startDate)
        ) {
            ChronoUnit.DAYS.between(startDate, endDateInclusive) + 1
        } else {
            null
        }
}

data class VacationUiState(
    val surface: VacationSurface = VacationSurface.NONE,
    val visibleMonth: YearMonth,
    val vacations: List<Vacation> = emptyList(),
    val draft: VacationDraft = VacationDraft(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val pendingDeleteId: UUID? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)
