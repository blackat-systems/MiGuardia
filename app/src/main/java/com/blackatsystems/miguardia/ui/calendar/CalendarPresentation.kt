package com.blackatsystems.miguardia.ui.calendar

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

internal data class CalendarViewportInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** System bars protect strips at the window edges, not every bounded child surface.
 * Values and bounds are physical pixels, independent of Android's text size. */
internal fun calendarViewportInsets(
    windowSize: IntSize,
    viewport: Rect,
    windowInsets: CalendarViewportInsets,
): CalendarViewportInsets {
    require(windowSize.width > 0 && windowSize.height > 0 && !viewport.isEmpty)
    fun overlap(value: Float, extent: Float): Int = ceil(value.coerceIn(0f, extent)).toInt()
    return CalendarViewportInsets(
        left = overlap(windowInsets.left - viewport.left, viewport.width),
        top = overlap(windowInsets.top - viewport.top, viewport.height),
        right = overlap(viewport.right - (windowSize.width - windowInsets.right), viewport.width),
        bottom = overlap(viewport.bottom - (windowSize.height - windowInsets.bottom), viewport.height),
    )
}

internal fun calendarMinimumGridHeight(weekCount: Int): Float =
    weekCount * 48f + (weekCount - 1) * 4f

internal const val CALENDAR_SIDE_RAIL_WIDTH_DP = 160f

/** Use spare horizontal space instead of compressing month rows in a short window.
 * Dimensions already exclude the system-bar strips that intersect this viewport. */
internal fun calendarUsesSideRail(availableWidth: Float, availableHeight: Float): Boolean =
    availableWidth >= 600f && availableHeight < 600f && availableWidth > availableHeight

internal fun calendarCellTextFitsWidth(availableWidthPx: Int, measuredWidthPx: Int): Boolean =
    availableWidthPx >= measuredWidthPx && availableWidthPx > 0

/** The optional event card must not consume the space reserved for complete 48-dp rows. */
internal fun calendarCanShowNextEvent(availableHeight: Float, weekCount: Int, fontScale: Float): Boolean =
    availableHeight >= calendarMinimumGridHeight(weekCount) + 56f + 284f * fontScale

/** Date and compact signals never compete with optional text. Use measured pixel heights:
 * a one-line Android font may be taller than the declared SP line height. */
internal fun calendarCellVisibleTextLines(
    heightPx: Int,
    dateHeightPx: Int,
    signalHeightPx: Int,
    lineHeightsPx: List<Int>,
    verticalPaddingPx: Int,
    spacingPx: Int,
): Int {
    var used = verticalPaddingPx * 2 + dateHeightPx +
        (if (signalHeightPx > 0) spacingPx + signalHeightPx else 0)
    var count = 0
    for (lineHeight in lineHeightsPx) {
        val next = used + spacingPx + lineHeight
        if (next > heightPx) break
        used = next
        count += 1
    }
    return count
}

/** Eight-dp pictograms with two-dp spacing; reserve the final slot for '+' on overflow. */
internal fun calendarCellVisibleSignals(width: Float, signalCount: Int): Int {
    val slots = ((width + 2f) / 10f).toInt().coerceAtLeast(1)
    return if (signalCount <= slots) signalCount else (slots - 1).coerceAtLeast(0)
}

// Layout observations let physical QA verify the rendered date, not just its semantics.
internal val CalendarDateTextBounds = SemanticsPropertyKey<Rect>("CalendarDateTextBounds")
internal val CalendarDateFontSizePx = SemanticsPropertyKey<Float>("CalendarDateFontSizePx")
internal val CalendarDateTextOverflow = SemanticsPropertyKey<Boolean>("CalendarDateTextOverflow")
