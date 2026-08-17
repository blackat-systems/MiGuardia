package com.blackatsystems.miguardia.ui.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarInteractionStateTest {
    @Test
    fun defaultAndUnknownSavedValuesStartInViewMode() {
        assertEquals(CalendarInteractionMode.VIEW, CalendarUiState(MONTH, Instant.EPOCH).interactionMode)
        assertEquals(CalendarInteractionMode.VIEW, calendarInteractionModeFromSaved(null))
        assertEquals(CalendarInteractionMode.VIEW, calendarInteractionModeFromSaved("UNKNOWN"))
    }

    @Test
    fun editTransitionsChangeOnlyModeAndKeepMonthAndSelection() {
        val original = CalendarUiState(
            visibleMonth = MONTH,
            referenceInstant = Instant.EPOCH,
            selectedDate = SELECTED_DATE,
            hasAnyShifts = false,
            loadState = CalendarLoadState.CONTENT,
        )

        val editing = original.enterEditing()
        val viewingAgain = editing.finishEditing()

        assertEquals(CalendarInteractionMode.EDIT, editing.interactionMode)
        assertEquals(MONTH, editing.visibleMonth)
        assertEquals(SELECTED_DATE, editing.selectedDate)
        assertEquals(false, editing.hasAnyShifts)
        assertEquals(CalendarInteractionMode.VIEW, viewingAgain.interactionMode)
        assertEquals(MONTH, viewingAgain.visibleMonth)
        assertEquals(SELECTED_DATE, viewingAgain.selectedDate)
    }

    @Test
    fun savedEditModeAndFirstShiftDateAreDeterministic() {
        assertEquals(CalendarInteractionMode.EDIT, calendarInteractionModeFromSaved("EDIT"))
        assertEquals(SELECTED_DATE, firstShiftDate(MONTH, SELECTED_DATE))
        assertEquals(LocalDate.of(2026, 9, 1), firstShiftDate(YearMonth.of(2026, 9), SELECTED_DATE))
    }

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        val SELECTED_DATE: LocalDate = LocalDate.of(2026, 8, 17)
    }
}
