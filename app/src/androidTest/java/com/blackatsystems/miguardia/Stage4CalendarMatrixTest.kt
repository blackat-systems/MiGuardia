package com.blackatsystems.miguardia

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventInput
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.nextevent.projectTodayCard
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarDateTextBounds
import com.blackatsystems.miguardia.ui.calendar.CalendarDateFontSizePx
import com.blackatsystems.miguardia.ui.calendar.CalendarDateTextOverflow
import com.blackatsystems.miguardia.ui.calendar.CalendarViewportInsets
import com.blackatsystems.miguardia.ui.calendar.calendarUsesSideRail
import com.blackatsystems.miguardia.ui.nextevent.NextEventLoadState
import com.blackatsystems.miguardia.ui.nextevent.NextEventUiState
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Geometry and semantics on a real Activity window, using only in-memory synthetic fixtures.
 * Captures cover this Compose root, never the entire display. Human legibility and TalkBack
 * remain separate checks. A too-small allocation is recorded as a constraint, never a pass. */
class Stage4CalendarMatrixTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun portraitFourFiveSixWeekMonthsAtEveryThemeAndAndroidFontScale() =
        exercise(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT, "portrait")

    @Test fun landscapeFourFiveSixWeekMonthsAtEveryThemeAndAndroidFontScale() =
        exercise(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE, "landscape")

    private fun exercise(requested: Int, expectedOrientation: Int, orientation: String) {
        check(context.packageName == "com.blackatsystems.miguardia.qa")
        val output = File(context.filesDir, "stage4/calendar-matrix-$orientation").also { check(it.exists() || it.mkdirs()) }
        val rows = JSONArray()
        val failures = mutableListOf<String>()
        val cases = MONTHS.flatMap { month -> listOf(false, true).flatMap { dark ->
            TestFontScale.entries.map { fontScale -> Case(month, dark, fontScale) }
        } }
        var selected by mutableStateOf(cases.first())
        var appDensity = Float.NaN
        var nativeFontScale = Float.NaN
        var expectedDateFontPx = Float.NaN
        var observedWindow = IntSize.Zero
        var observedInsets = CalendarViewportInsets(0, 0, 0, 0)
        var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        var stoppedForFocus = false
        try {
            instrumentation.runOnMainSync {
                originalOrientation = compose.activity.requestedOrientation
                compose.activity.requestedOrientation = requested
            }
            compose.waitUntil(30_000) {
                compose.activity.resources.configuration.orientation == expectedOrientation && ownsFocus()
            }
            compose.setContent {
                val current = selected
                key(current) {
                    AndroidFontScaleTheme(darkTheme = current.dark, fontScale = current.fontScale) {
                        val density = LocalDensity.current
                        val dateFontPx = with(density) { 14.sp.toPx() }
                        val layoutDirection = LocalLayoutDirection.current
                        val window = LocalWindowInfo.current.containerSize
                        val insets = ScaffoldDefaults.contentWindowInsets
                        val insetSnapshot = CalendarViewportInsets(
                            insets.getLeft(density, layoutDirection), insets.getTop(density),
                            insets.getRight(density, layoutDirection), insets.getBottom(density),
                        )
                        SideEffect {
                            appDensity = density.density
                            nativeFontScale = density.fontScale
                            expectedDateFontPx = dateFontPx
                            observedWindow = window
                            observedInsets = insetSnapshot
                        }
                        Box(Modifier.fillMaxSize().testTag(ROOT)) {
                            val shifts = fixture(current.month)
                            val now = current.month.atDay(3).atTime(LocalTime.NOON).atZone(AppDefaults.zoneId()).toInstant()
                            val writes = shifts.map(::write)
                            val nextEvent = projectNextEvent(now, AppDefaults.zoneId(), NextEventInput(
                                shifts = writes, availabilityWindows = emptyList(), actualsByShiftId = emptyMap(),
                                independentExtras = emptyList(), explicitDayStatuses = emptyList(),
                                vacations = emptyList(), medicalLeaves = emptyList()))
                            MiGuardiaApp(
                                calendarState = CalendarUiState(current.month, now,
                                    days = projectCalendarMonth(current.month, shifts,
                                        explicitDayStatuses = listOf(ExplicitDayStatus(current.month.atDay(4), ExplicitDayStatusType.DAY_OFF)),
                                        medicalLeaves = emptyList(), now = now,
                                        holidays = listOf(Holiday(UUID(0, 94), current.month.atDay(7), "Feriado sintético", Instant.EPOCH, Instant.EPOCH))),
                                    loadState = CalendarLoadState.CONTENT),
                                onPreviousMonth = {}, onNextMonth = {}, onToday = {},
                                onSelectDate = {}, onDismissDate = {}, onRetry = {},
                                nextEventState = NextEventUiState(loadState = NextEventLoadState.CONTENT,
                                    result = projectTodayCard(now = now, zoneId = AppDefaults.zoneId(), shifts = writes,
                                        actualsByShiftId = emptyMap(), vacations = emptyList(), medicalLeaves = emptyList(),
                                        futureEvent = nextEvent)),
                                )
                        }
                    }
                }
            }
            cases.forEach { case ->
                val row = JSONObject().put("case", case.id).put("month", case.month.toString())
                    .put("weeks", case.weeks).put("theme", if (case.dark) "dark" else "light")
                    .put("android_font_scale_simulated", case.fontScale.scale.toDouble()).put("orientation", orientation)
                    .put("fixture_shifts", fixture(case.month).size)
                    .put("next_event_state", "CONTENT; three shifts on day 3 including active and overnight")
                rows.put(row)
                if (stoppedForFocus || !ownsFocus()) {
                    stoppedForFocus = true
                    row.put("status", "PENDIENTE").put("reason", "QA Activity lost focus; no further interaction or capture")
                    writeReport(output, orientation, rows)
                    return@forEach
                }
                val issues = mutableListOf<String>()
                try {
                    compose.runOnIdle { selected = case }
                    compose.waitForIdle()
                    check(appDensity.isFinite() && appDensity > 0)
                    val baseDensity = appDensity
                    check(nativeFontScale == case.fontScale.scale) { "Android text scenario was not applied" }
                    val root = compose.onNodeWithTag(ROOT).fetchSemanticsNode().boundsInRoot
                    val grid = compose.onNodeWithTag("month-grid").fetchSemanticsNode().boundsInRoot
                    val expectsRail = calendarUsesSideRail(
                        (root.width - observedInsets.left - observedInsets.right) / baseDensity,
                        (root.height - observedInsets.top - observedInsets.bottom) / baseDensity,
                    )
                    row.put("app_density_px_per_dp", appDensity.toDouble())
                        .put("base_density_px_per_dp", baseDensity.toDouble())
                        .put("calendar_font_scale", case.fontScale.scale.toDouble())
                        .put("root_px", root.json()).put("grid_px", grid.json())
                        .put("minimum_target_base_dp", 48)
                        .put("required_grid_width_px", (7 * 48 * baseDensity).toDouble())
                        .put("required_grid_height_px_without_gaps", (case.weeks * 48 * baseDensity).toDouble())
                        .put("column_allocation_base_dp", (grid.width / 7 / baseDensity).toDouble())
                        .put("row_allocation_base_dp_before_gaps", (grid.height / case.weeks / baseDensity).toDouble())
                        .put("window_px", JSONObject().put("width", observedWindow.width).put("height", observedWindow.height))
                        .put("system_insets_px", JSONObject().put("left", observedInsets.left).put("top", observedInsets.top)
                            .put("right", observedInsets.right).put("bottom", observedInsets.bottom))
                        .put("side_rail_expected", expectsRail)
                    val railNodes = compose.onAllNodesWithTag("calendar-side-rail").fetchSemanticsNodes()
                    if (railNodes.size != if (expectsRail) 1 else 0) issues += "Side rail does not match the native viewport policy"
                    val controlBounds = JSONObject()
                    listOf("calendar-side-rail", "main-top-bar", "calendar-month-controls", "calendar-weekday-header")
                        .forEach { tag -> compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.let { node ->
                            controlBounds.put(tag, node.boundsInRoot.json())
                            if (!root.containsRect(node.boundsInRoot)) issues += "$tag exceeds the visible root"
                        } }
                    row.put("control_bounds_px", controlBounds)
                    val navigationTargets = mutableListOf<Pair<String, Rect>>()
                    listOf("main-menu-button", "calendar-previous-month", "calendar-next-month").forEach { tag ->
                        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
                        val touch = node.touchBoundsInRoot
                        navigationTargets += tag to touch
                        if (!root.containsRect(touch) || touch.width + 1 < 48 * baseDensity ||
                            touch.height + 1 < 48 * baseDensity || node.config.getOrNull(SemanticsActions.OnClick) == null) {
                            issues += "$tag lacks a visible operable 48-dp target"
                        }
                    }
                    row.put("navigation_touch_bounds_px", JSONObject().also { json ->
                        navigationTargets.forEach { (tag, touch) -> json.put(tag, touch.json()) }
                    })
                    if (expectsRail && compose.onAllNodesWithTag("main-top-bar").fetchSemanticsNodes().isNotEmpty()) {
                        issues += "Wide short calendar reserves an additional top bar"
                    }
                    if (!root.containsRect(grid)) issues += "Month grid exceeds its visible root"
                    // Grid allocation describes the layout; touch expansion can also use its
                    // padding and gaps. A visual cell below 48 dp passes when its real 48 dp
                    // target stays inside the viewport and does not overlap another day.
                    val cells = JSONArray()
                    val touchTargets = mutableListOf<Pair<String, Rect>>()
                    (1..case.month.lengthOfMonth()).forEach { number ->
                        val date = case.month.atDay(number)
                        val node = compose.onNode(hasTestTag("day-$date") or hasTestTag("completed-day-$date"))
                            .fetchSemanticsNode()
                        val bounds = node.boundsInRoot
                        val touchBounds = node.touchBoundsInRoot
                        touchTargets += date.toString() to touchBounds
                        val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
                        val roleIsButton = node.config.getOrNull(SemanticsProperties.Role) == Role.Button
                        val hasAction = node.config.getOrNull(SemanticsActions.OnClick) != null
                        val hasState = node.config.getOrNull(SemanticsProperties.Selected) != null
                        val dateTextBounds = node.config.getOrNull(CalendarDateTextBounds)
                        val dateFontPx = node.config.getOrNull(CalendarDateFontSizePx)
                        val dateTextOverflow = node.config.getOrNull(CalendarDateTextOverflow)
                        cells.put(JSONObject().put("date", date.toString()).put("bounds_px", bounds.json())
                            .put("width_base_dp", (bounds.width / baseDensity).toDouble())
                            .put("height_base_dp", (bounds.height / baseDensity).toDouble())
                            .put("touch_bounds_px", touchBounds.json())
                            .put("touch_width_base_dp", (touchBounds.width / baseDensity).toDouble())
                            .put("touch_height_base_dp", (touchBounds.height / baseDensity).toDouble())
                            .put("date_text_bounds_px", dateTextBounds?.json())
                            .put("date_font_px", dateFontPx?.toDouble())
                            .put("expected_date_font_px", expectedDateFontPx.toDouble())
                            .put("date_text_overflow", dateTextOverflow)
                            .put("touch_target_inside_viewport", root.containsRect(touchBounds))
                            .put("role_button", roleIsButton).put("has_click", hasAction).put("has_selection_state", hasState)
                            .put("description_nonempty", description.isNotBlank()))
                        if (bounds.width <= 0 || bounds.height <= 0 || !root.containsRect(bounds)) issues += "$date is absent or clipped"
                        if (touchBounds.width + 1 < 48 * baseDensity || touchBounds.height + 1 < 48 * baseDensity) {
                            issues += "$date has a touch target smaller than 48 stable base-dp"
                        }
                        if (dateTextBounds == null || dateTextBounds.width <= 0 || dateTextBounds.height <= 0 ||
                            !bounds.containsRect(dateTextBounds) || dateTextOverflow != false) {
                            issues += "$date has absent, clipped or overflowing rendered date text"
                        }
                        if (dateFontPx == null || kotlin.math.abs(dateFontPx - expectedDateFontPx) > .5f) {
                            issues += "$date does not render its date using the native Android text converter"
                        }
                        if (!root.containsRect(touchBounds)) issues += "$date has a touch target outside the visible root"
                        if (!roleIsButton || !hasAction || !hasState || description.isBlank()) issues += "$date lacks required semantics"
                        fixture(case.month).filter { it.localStartDate == date }.forEach { shift ->
                            if (!description.contains(shift.objectiveAbbreviationSnapshot)) issues += "$date omits a synthetic shift from accessibility description"
                        }
                    }
                    row.put("cells", cells)
                    touchTargets += navigationTargets
                    val overlaps = JSONArray()
                    touchTargets.forEachIndexed { index, (date, bounds) ->
                        touchTargets.drop(index + 1).forEach { (otherDate, otherBounds) ->
                            val width = minOf(bounds.right, otherBounds.right) - maxOf(bounds.left, otherBounds.left)
                            val height = minOf(bounds.bottom, otherBounds.bottom) - maxOf(bounds.top, otherBounds.top)
                            // Shared edges and rounding differences up to one pixel are allowed.
                            if (width > 1 && height > 1) {
                                overlaps.put(JSONObject().put("date", date).put("other_date", otherDate)
                                    .put("width_px", width.toDouble()).put("height_px", height.toDouble()))
                                issues += "$date and $otherDate have overlapping touch targets"
                            }
                        }
                    }
                    row.put("touch_target_overlaps", overlaps)
                    listOf("calendar-scroll-container", "calendar-scrollbar-track", "calendar-primary-actions",
                        "calendar-v2-load-shifts", "calendar-select-days").forEach { forbidden ->
                        if (compose.onAllNodesWithTag(forbidden).fetchSemanticsNodes().isNotEmpty()) issues += "Unexpected permanent control or main scroll: $forbidden"
                    }
                    if (!ownsFocus()) {
                        stoppedForFocus = true
                        row.put("capture_status", "PENDIENTE: Activity lost focus before node capture")
                    } else {
                        val bitmap = compose.onNodeWithTag(ROOT).captureToImage().asAndroidBitmap()
                        try {
                            if (ownsFocus()) {
                                File(output, "${case.id}.png").outputStream().use {
                                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                                }
                                row.put("capture_status", "EJECUTADA").put("capture", "${case.id}.png")
                            } else {
                                stoppedForFocus = true
                                row.put("capture_status", "PENDIENTE: Activity lost focus; captured bytes discarded")
                            }
                        } finally { bitmap.recycle() }
                    }
                } catch (error: AssertionError) {
                    issues += "Assertion: ${error.message.orEmpty().take(250)}"
                } catch (error: Exception) {
                    issues += "${error.javaClass.simpleName}: ${error.message.orEmpty().take(250)}"
                }
                row.put("issues", JSONArray(issues))
                row.put("status", when { issues.isNotEmpty() -> "FALLÓ"; stoppedForFocus -> "PENDIENTE"; else -> "EJECUTADA" })
                if (issues.isNotEmpty()) failures += "${case.id}: ${issues.joinToString("; ")}"
                writeReport(output, orientation, rows)
            }
        } finally {
            cases.drop(rows.length()).forEach { case ->
                rows.put(JSONObject().put("case", case.id).put("month", case.month.toString())
                    .put("weeks", case.weeks).put("theme", if (case.dark) "dark" else "light")
                    .put("android_font_scale_simulated", case.fontScale.scale.toDouble()).put("orientation", orientation)
                    .put("status", "PENDIENTE").put("reason", "Setup or execution interrupted before this case"))
            }
            writeReport(output, orientation, rows)
            // Only this test Activity's requested orientation is restored; no Android display settings.
            instrumentation.runOnMainSync { compose.activity.requestedOrientation = originalOrientation }
        }
        assertTrue("Matrix incomplete after losing QA focus", !stoppedForFocus)
        assertTrue("Calendar constraints found; see synthetic per-case JSON: ${failures.joinToString(" | ")}", failures.isEmpty())
    }

    private fun ownsFocus(): Boolean {
        var focused = false
        instrumentation.runOnMainSync { focused = compose.activity.hasWindowFocus() }
        return focused
    }

    private fun writeReport(output: File, orientation: String, rows: JSONArray) {
        File(output, "matrix.json").writeText(JSONObject().put("orientation", orientation)
            .put("expected_cases", MONTHS.size * 2 * TestFontScale.entries.size).put("recorded_cases", rows.length())
            .put("measurement", "Compose bounds/touchBoundsInRoot at host geometric density, 48 dp minimum without overlaps; simulated Android text configurations 1/1.5/2 using native nonlinear SP converter; rendered date overflow, bounds and native font size; one-pixel geometry tolerance")
            .put("limits", "Rendered date geometry and font scaling do not certify human legibility or TalkBack; no detail-flow, OEM picker or global display setting access")
            .put("cases", rows).toString(2))
    }

    private fun Rect.json() = JSONObject().put("left", left.toDouble()).put("top", top.toDouble())
        .put("right", right.toDouble()).put("bottom", bottom.toDouble()).put("width", width.toDouble()).put("height", height.toDouble())

    private fun Rect.containsRect(other: Rect) = other.left >= left - 1 && other.top >= top - 1 &&
        other.right <= right + 1 && other.bottom <= bottom + 1

    private data class Case(val month: YearMonth, val dark: Boolean, val fontScale: TestFontScale) {
        val weeks: Int get() = (month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7
        val id: String get() = "$month-${if (dark) "dark" else "light"}-${fontScale.percent}"
    }

    private fun fixture(month: YearMonth): List<Shift> = (1..month.lengthOfMonth()).flatMap { number ->
        // Franco owns no new shift; the previous night's shift may still carry into it.
        if (number == 4) return@flatMap emptyList()
        val date = month.atDay(number)
        val zone = AppDefaults.zoneId()
        val count = if (number % 3 == 0) 3 else 1
        (0 until count).map { index ->
            val start = if (index == 2) LocalTime.of(19, 0) else LocalTime.of(7 + index * 6, 0)
            val end = if (index == 2) LocalTime.of(7, 0) else start.plusHours(6)
            Shift(UUID.nameUUIDFromBytes("stage4-matrix-$date-$index".toByteArray()),
                date.atTime(start).atZone(zone).toInstant(), date.plusDays(if (end <= start) 1 else 0).atTime(end).atZone(zone).toInstant(),
                zone, date, "Institución sintética con nombre extenso número $index", "QA$index", null,
                start, end, 0xff405080.toInt(), "Puesto sintético de nombre largo", ShiftStatus.PLANNED,
                UUID(0, 93), Instant.EPOCH, Instant.EPOCH)
        }
    }

    private fun write(shift: Shift) = V2ShiftWrite(shift, ShiftWorkSnapshot(
        shiftId = shift.id, timelineId = UUID(0, 91), sector = WorkSector.NURSING,
        configurationRevisionId = UUID(0, 92), workPlaceId = UUID(0, 95), objectiveId = shift.sourceObjectiveId,
        templateId = UUID(0, 96), workTypeId = UUID(0, 97), workTypeNameSnapshot = "Trabajo sintético",
        workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK))

    private companion object {
        const val ROOT = "stage4-calendar-root"
        val MONTHS = listOf(YearMonth.of(2021, 2), YearMonth.of(2024, 2), YearMonth.of(2026, 4), YearMonth.of(2026, 8))
    }
}
