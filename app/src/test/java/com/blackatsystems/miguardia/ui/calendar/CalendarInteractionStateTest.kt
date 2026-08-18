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
    fun detailAndEditSelectionHaveSeparateResponsibilities() {
        val original = CalendarUiState(
            visibleMonth = MONTH,
            referenceInstant = Instant.EPOCH,
            detailDate = SELECTED_DATE,
            hasAnyShifts = false,
            loadState = CalendarLoadState.CONTENT,
        )

        val editing = original.enterEditing(SELECTED_DATE)
        val withSecondDate = editing.toggleEditDate(SECOND_DATE)
        val viewingAgain = editing.finishEditing()

        assertEquals(CalendarInteractionMode.EDIT, editing.interactionMode)
        assertEquals(MONTH, editing.visibleMonth)
        assertEquals(null, editing.detailDate)
        assertEquals(setOf(SELECTED_DATE), editing.editSelectedDates)
        assertEquals(setOf(SELECTED_DATE, SECOND_DATE), withSecondDate.editSelectedDates)
        assertEquals(false, editing.hasAnyShifts)
        assertEquals(CalendarInteractionMode.VIEW, viewingAgain.interactionMode)
        assertEquals(MONTH, viewingAgain.visibleMonth)
        assertEquals(null, viewingAgain.detailDate)
        assertEquals(emptySet<LocalDate>(), viewingAgain.editSelectedDates)
    }

    @Test
    fun editCalendarStartsEmptyAndRejectsDatesOutsideVisibleMonth() {
        val editing = CalendarUiState(MONTH, Instant.EPOCH).enterEditing()

        assertEquals(emptySet<LocalDate>(), editing.editSelectedDates)
        assertEquals(editing, editing.toggleEditDate(LocalDate.of(2026, 9, 1)))
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
        val SECOND_DATE: LocalDate = LocalDate.of(2026, 8, 18)
    }
}
