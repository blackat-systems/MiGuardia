package com.blackatsystems.miguardia.ui.exceptions

import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class ExceptionsSurface {
    NONE,
    HOLIDAYS,
    NOTES,
}

data class HolidayDraft(
    val editingId: UUID? = null,
    val datesText: String = "",
    val name: String = "",
    val conflictDates: Set<LocalDate> = emptySet(),
    val pendingPolicy: HolidayConflictPolicy? = null,
)

data class NoteDraft(
    val editingId: UUID? = null,
    val body: String = "",
)

data class ExceptionsUiState(
    val surface: ExceptionsSurface = ExceptionsSurface.NONE,
    val holidayMonth: YearMonth = YearMonth.now(),
    val holidays: List<Holiday> = emptyList(),
    val selectedShift: Shift? = null,
    val notes: List<ShiftNote> = emptyList(),
    val holidayDraft: HolidayDraft = HolidayDraft(),
    val noteDraft: NoteDraft = NoteDraft(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)
