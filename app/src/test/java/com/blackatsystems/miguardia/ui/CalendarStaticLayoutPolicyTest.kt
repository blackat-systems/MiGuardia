package com.blackatsystems.miguardia.ui

import java.time.YearMonth
import com.blackatsystems.miguardia.ui.calendar.calendarCanShowNextEvent
import com.blackatsystems.miguardia.ui.calendar.calendarMinimumGridHeight
import com.blackatsystems.miguardia.ui.calendar.CalendarViewportInsets
import com.blackatsystems.miguardia.ui.calendar.calendarViewportInsets
import com.blackatsystems.miguardia.ui.calendar.calendarCellVisibleTextLines
import com.blackatsystems.miguardia.ui.calendar.calendarCellVisibleSignals
import com.blackatsystems.miguardia.ui.calendar.calendarUsesSideRail
import com.blackatsystems.miguardia.ui.calendar.CALENDAR_SIDE_RAIL_WIDTH_DP
import com.blackatsystems.miguardia.ui.calendar.calendarCellTextFitsWidth
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarStaticLayoutPolicyTest {
    @Test
    fun monthGridUsesFourRowsOnlyWhenTheCalendarActuallyFitsFourRows() {
        assertEquals(28, calendarMonthCellCount(YearMonth.of(2021, 2)))
    }

    @Test
    fun monthGridIncludesEveryDayForLeapAndThirtyDayMonths() {
        assertEquals(35, calendarMonthCellCount(YearMonth.of(2024, 2)))
        assertEquals(35, calendarMonthCellCount(YearMonth.of(2026, 4)))
    }

    @Test
    fun monthGridKeepsAllSixRowsWhenTheMonthRequiresThem() {
        assertEquals(42, calendarMonthCellCount(YearMonth.of(2026, 8)))
        assertEquals(42, calendarMonthCellCount(YearMonth.of(2026, 3)))
    }

    @Test
    fun minimumGridGeometryDoesNotScaleWithAndroidTextSize() {
        assertEquals(204f, calendarMinimumGridHeight(4), 0f)
        assertEquals(256f, calendarMinimumGridHeight(5), 0f)
        assertEquals(308f, calendarMinimumGridHeight(6), 0f)
    }

    @Test
    fun sideRailUsesOnlyWideShortNativeViewports() {
        assertTrue(calendarUsesSideRail(749.87f, 352f))
        assertTrue(calendarUsesSideRail(600f, 599.99f))
        assertFalse(calendarUsesSideRail(599.99f, 384f))
        assertFalse(calendarUsesSideRail(600f, 600f))
        assertFalse(calendarUsesSideRail(384f, 832f))
        assertFalse(calendarUsesSideRail(800f, 760f))
    }

    @Test
    fun smallestRailWidthKeepsSevenNativeTouchTargetsAndTheirPadding() {
        val availableGridWidth = 600f - CALENDAR_SIDE_RAIL_WIDTH_DP - 8f - 16f
        assertTrue(availableGridWidth / 7f - 2f >= 48f)
        // With controls beside the month, a short Samsung content viewport can
        // retain six 48-dp rows and the measured 200% weekday line plus its gap.
        assertTrue(352f - 24f - 2f >= calendarMinimumGridHeight(6))
    }

    @Test
    fun optionalLabelsAppearWholeOrNotAtAllWithoutEllipsis() {
        assertTrue(calendarCellTextFitsWidth(120, 120))
        assertTrue(calendarCellTextFitsWidth(121, 120))
        assertFalse(calendarCellTextFitsWidth(119, 120))
        assertFalse(calendarCellTextFitsWidth(0, 0))
        assertFalse(calendarCellTextFitsWidth(-1, 120))
    }

    @Test
    fun optionalCardCannotConsumeTheSixRowsOfAShortLandscapeWindow() {
        assertEquals(308f, calendarMinimumGridHeight(6), 0f)
        listOf(1f, 1.5f, 2f).forEach { fontScale -> assertFalse(calendarCanShowNextEvent(420f, 6, fontScale)) }
        assertTrue(calendarCanShowNextEvent(1000f, 6, 2f))
    }

    @Test
    fun largerAndroidTextCanOmitOnlyTheOptionalCardNotMonthRows() {
        assertTrue(calendarCanShowNextEvent(750f, 6, 1f))
        assertFalse(calendarCanShowNextEvent(750f, 6, 2f))
        assertEquals(308f, calendarMinimumGridHeight(6), 0f)
    }

    @Test
    fun fullMonthlyWindowPreservesEverySystemBarInset() {
        val insets = CalendarViewportInsets(0, 128, 0, 180)
        assertEquals(insets, calendarViewportInsets(IntSize(1440, 3120), Rect(0f, 0f, 1440f, 3120f), insets))
    }

    @Test
    fun boundedTopMonthlyWindowDoesNotReserveNavigationBarOutsideItsViewport() {
        assertEquals(CalendarViewportInsets(0, 128, 0, 0), calendarViewportInsets(
            IntSize(1440, 3120), Rect(0f, 0f, 1440f, 1463f), CalendarViewportInsets(0, 128, 0, 180),
        ))
        // The physical regression used six rows in this 520-base-dp host at 200%.
        val availableBaseDp = (1463 - 128) / 2.8125f
        assertTrue(availableBaseDp - 56f - 56f - 48f >= calendarMinimumGridHeight(6))
    }

    @Test
    fun floatingMonthlyViewportDoesNotRepeatInsetsFromAnyWindowEdge() {
        assertEquals(CalendarViewportInsets(0, 0, 0, 0), calendarViewportInsets(
            IntSize(1440, 3120), Rect(100f, 150f, 1300f, 2800f), CalendarViewportInsets(70, 128, 80, 180),
        ))
    }

    @Test
    fun landscapeMonthlyWindowProtectsItsActualSideBarAndPartialIntersections() {
        val landscapeInsets = CalendarViewportInsets(128, 0, 180, 0)
        assertEquals(landscapeInsets, calendarViewportInsets(
            IntSize(3120, 1440), Rect(0f, 0f, 3120f, 1440f), landscapeInsets,
        ))
        assertEquals(CalendarViewportInsets(28, 0, 60, 0), calendarViewportInsets(
            IntSize(3120, 1440), Rect(100f, 200f, 3000f, 1200f), landscapeInsets,
        ))
    }

    @Test
    fun minimumCellKeepsDateAndSignalsWithoutSqueezingTextAtTwoHundredPercent() {
        // Physical Samsung evidence: 48 base dp = 135 px; natural date height = 96 px.
        // Pixel-rounded band, padding and spacing fit without cropping that natural line.
        assertEquals(0, calendarCellVisibleTextLines(135, 96, 23, listOf(83, 83, 69, 69), 3, 6))
        assertTrue(96 + 23 + 6 + 3 * 2 <= 135)
        assertEquals(3, calendarCellVisibleSignals(44f, 7))
        assertEquals(7, calendarCellVisibleSignals(68f, 7))
    }

    @Test
    fun optionalTextKeepsItsMeasuredNativeSizeAndImportantConditionsPrecedeTimeAndState() {
        assertEquals(6, calendarCellVisibleTextLines(360, 48, 23, listOf(41, 41, 35, 35, 35, 35), 3, 6))
        assertEquals(4, calendarCellVisibleTextLines(461, 96, 23, listOf(83, 83, 69, 69, 69, 69), 3, 6))
        assertEquals(0, calendarCellVisibleTextLines(135, 96, 23, listOf(83, 83), 3, 6))
    }
}
