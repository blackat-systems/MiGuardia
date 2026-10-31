package com.blackatsystems.miguardia.ui

import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarSelectionPurpose
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarBackPriorityTest {
    @Test
    fun everySelectionPurposeHasExactlyOneEffectiveBackOwner() {
        assertEquals(
            CalendarSelectionBackOwner.GENERAL,
            owner(CalendarSelectionPurpose.GENERAL),
        )
        assertEquals(
            CalendarSelectionBackOwner.MANUAL_LOAD,
            owner(CalendarSelectionPurpose.MANUAL_LOAD, manualActive = true, blocked = true),
        )
        assertEquals(
            CalendarSelectionBackOwner.HOLIDAY,
            owner(CalendarSelectionPurpose.HOLIDAY, holidayActive = true, blocked = true),
        )
        assertEquals(
            CalendarSelectionBackOwner.MANUAL_LOAD,
            owner(
                CalendarSelectionPurpose.MANUAL_LOAD,
                manualActive = true,
                holidayActive = true,
                blocked = true,
            ),
        )
        assertEquals(
            CalendarSelectionBackOwner.HOLIDAY,
            owner(
                CalendarSelectionPurpose.HOLIDAY,
                manualActive = true,
                holidayActive = true,
                blocked = true,
            ),
        )
    }

    @Test
    fun externalCancellationCannotHandManualOrHolidaySelectionToTheGeneralOwner() {
        assertEquals(
            CalendarSelectionBackOwner.NONE,
            owner(CalendarSelectionPurpose.MANUAL_LOAD),
        )
        assertEquals(
            CalendarSelectionBackOwner.NONE,
            owner(CalendarSelectionPurpose.HOLIDAY),
        )
    }

    @Test
    fun nonCalendarAndBlockedGeneralSelectionDoNotConsumeBack() {
        assertEquals(
            CalendarSelectionBackOwner.NONE,
            owner(CalendarSelectionPurpose.GENERAL, calendarVisible = false),
        )
        assertEquals(
            CalendarSelectionBackOwner.NONE,
            owner(CalendarSelectionPurpose.GENERAL, blocked = true),
        )
    }

    private fun owner(
        purpose: CalendarSelectionPurpose,
        manualActive: Boolean = false,
        holidayActive: Boolean = false,
        calendarVisible: Boolean = true,
        blocked: Boolean = false,
    ) = calendarSelectionBackOwner(
        calendarVisible = calendarVisible,
        interactionMode = CalendarInteractionMode.EDIT,
        selectionPurpose = purpose,
        manualLoadActive = manualActive,
        holidaySelectionActive = holidayActive,
        selectedDayOpen = false,
        generalBackBlocked = blocked,
    )
}
