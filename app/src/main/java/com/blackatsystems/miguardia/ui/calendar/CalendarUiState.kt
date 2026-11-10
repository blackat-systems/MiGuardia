package com.blackatsystems.miguardia.ui.calendar

import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import com.blackatsystems.miguardia.core.domain.model.ShiftSituationExpectation
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchPreview

enum class CalendarLoadState {
    LOADING,
    CONTENT,
    ERROR,
}

enum class CalendarInteractionMode {
    VIEW,
    EDIT,
}

enum class CalendarSelectionPurpose {
    GENERAL,
    MANUAL_LOAD,
    HOLIDAY,
}

data class CalendarUiState(
    val visibleMonth: YearMonth,
    val referenceInstant: Instant,
    val days: List<CalendarDay> = emptyList(),
    val detailDate: LocalDate? = null,
    val editSelectedDates: Set<LocalDate> = emptySet(),
    val editSelectionConfirmed: Boolean = false,
    val interactionMode: CalendarInteractionMode = CalendarInteractionMode.VIEW,
    val selectionPurpose: CalendarSelectionPurpose? = null,
    val calendarCollapsed: Boolean = false,
    val lastOpenedDate: LocalDate? = null,
    val hasAnyShifts: Boolean = true,
    val hasAnyShiftsLoaded: Boolean = true,
    val shiftPresenceError: Boolean = false,
    val loadState: CalendarLoadState = CalendarLoadState.LOADING,
    val errorMessage: String? = null,
    val stage2Busy: Boolean = false,
    val stage2Message: String? = null,
    val stage2Expectations: Map<UUID, ShiftSituationExpectation> = emptyMap(),
    val batchPreview: CalendarBatchPreview? = null,
    val batchBusy: Boolean = false,
    val batchMessage: String? = null,
)

internal fun calendarInteractionModeFromSaved(value: String?): CalendarInteractionMode =
    CalendarInteractionMode.entries.firstOrNull { it.name == value } ?: CalendarInteractionMode.VIEW

internal fun calendarSelectionPurposeFromSaved(value: String?): CalendarSelectionPurpose? =
    CalendarSelectionPurpose.entries.firstOrNull { it.name == value }

internal fun CalendarUiState.enterEditing(
    selectedDate: LocalDate? = null,
    purpose: CalendarSelectionPurpose = CalendarSelectionPurpose.GENERAL,
): CalendarUiState = copy(
    interactionMode = CalendarInteractionMode.EDIT,
    selectionPurpose = purpose,
    detailDate = null,
    editSelectedDates = selectedDate?.let(::setOf).orEmpty(),
    editSelectionConfirmed = false,
    batchPreview = null,
    batchMessage = null,
)

internal fun CalendarUiState.finishEditing(): CalendarUiState = copy(
    interactionMode = CalendarInteractionMode.VIEW,
    selectionPurpose = null,
    editSelectedDates = emptySet(),
    editSelectionConfirmed = false,
    batchPreview = null,
)

internal fun CalendarUiState.toggleEditDate(date: LocalDate): CalendarUiState {
    if (interactionMode != CalendarInteractionMode.EDIT || YearMonth.from(date) != visibleMonth) return this
    return copy(
        editSelectedDates = if (date in editSelectedDates) editSelectedDates - date else editSelectedDates + date,
        editSelectionConfirmed = false,
    )
}

internal fun CalendarUiState.confirmEditSelection(): CalendarUiState =
    if (interactionMode == CalendarInteractionMode.EDIT && editSelectedDates.isNotEmpty()) {
        copy(editSelectionConfirmed = true)
    } else {
        this
    }

internal fun CalendarUiState.resumeEditSelection(): CalendarUiState =
    if (interactionMode == CalendarInteractionMode.EDIT) copy(editSelectionConfirmed = false) else this

internal fun activeCalendarWeekStart(
    month: YearMonth,
    selectedDates: Set<LocalDate>,
    lastOpenedDate: LocalDate?,
    today: LocalDate,
): LocalDate {
    val anchor = selectedDates.filter { YearMonth.from(it) == month }.minOrNull()
        ?: lastOpenedDate?.takeIf { YearMonth.from(it) == month }
        ?: today.takeIf { YearMonth.from(it) == month }
        ?: month.atDay(1)
    return anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
}
