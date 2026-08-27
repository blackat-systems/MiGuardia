package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.nextevent.projectTodayCard
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.nextevent.NextEventLoadState
import com.blackatsystems.miguardia.ui.nextevent.NextEventUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarAdaptiveLayoutComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun standardZoomPhysicalViewportKeepsWholeHierarchyReachable() {
        setCalendarViewport(height = 1_080.dp, referenceNow = AFTER_SHIFTS_NOW)

        val root = compose.onNodeWithTag("calendar-test-root").bounds()
        val host = compose.onNodeWithTag("calendar-test-host").bounds()
        assertContained(root, host)

        val scrollRange = compose.onNodeWithTag("calendar-scroll-container").verticalScrollRange()
        assertEquals(0f, scrollRange.value(), 0.001f)
        assertTrue(scrollRange.maxValue() > 0f)
        compose.onNodeWithTag("calendar-scrollbar-track").assertIsDisplayed()
        compose.onNodeWithTag("calendar-scrollbar-thumb").assertIsDisplayed()

        compose.onNodeWithTag("next-event-card").assertIsDisplayed()
        compose.onNodeWithTag("month-grid").assertIsDisplayed()
        val viewport = compose.onNodeWithTag("calendar-scroll-viewport").bounds()
        assertContained(viewport, compose.onNodeWithTag("next-event-card").bounds())
        compose.onNodeWithTag("calendar-v2-load-shifts").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("calendar-work-setup-action").performScrollTo().assertIsDisplayed()
        assertTrue(scrollRange.value() > 0f)
    }

    @Test
    fun shortViewportKeepsPersistentScrollbarVisibleAfterThemeChange() {
        var darkTheme by mutableStateOf(false)
        setCalendarViewport(height = 700.dp, darkTheme = { darkTheme })

        val container = compose.onNodeWithTag("calendar-scroll-container")
        assertTrue(container.verticalScrollRange().maxValue() > 0f)
        compose.onNodeWithTag("calendar-scrollbar-track").assertIsDisplayed()
        val initialThumbTop = compose.onNodeWithTag("calendar-scrollbar-thumb").bounds().top

        compose.onNodeWithTag("calendar-work-setup-action").performScrollTo().assertIsDisplayed()
        assertTrue(container.verticalScrollRange().value() > 0f)
        val movedThumb = compose.onNodeWithTag("calendar-scrollbar-thumb").bounds()
        val track = compose.onNodeWithTag("calendar-scrollbar-track").bounds()
        assertTrue(movedThumb.top > initialThumbTop)
        assertTrue(movedThumb.top >= track.top && movedThumb.bottom <= track.bottom)

        compose.runOnIdle { darkTheme = true }
        compose.onNodeWithTag("calendar-scrollbar-track").assertIsDisplayed()
        compose.onNodeWithTag("calendar-scrollbar-thumb").assertIsDisplayed()
        compose.onNodeWithTag("calendar-work-setup-action").assertIsDisplayed()
    }

    @Test
    fun fixedPhysicalViewportKeepsCalendarReachableAtEveryInternalZoom() {
        var zoom by mutableStateOf(AppZoom.STANDARD)
        compose.setContent {
            MiGuardiaTheme(appZoom = AppZoom.STANDARD) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("calendar-test-root"),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Box(
                        Modifier
                            .sizeIn(maxWidth = 400.dp, maxHeight = 760.dp)
                            .fillMaxSize()
                            .testTag("calendar-test-host"),
                    ) {
                        key(zoom) {
                            MiGuardiaTheme(appZoom = zoom) {
                                CalendarUnderTest(appZoom = zoom)
                            }
                        }
                    }
                }
            }
        }

        AppZoom.entries.forEach { option ->
            compose.runOnIdle { zoom = option }
            compose.waitForIdle()
            compose.onNodeWithTag("calendar-scrollbar-track").assertIsDisplayed()
            compose.onNodeWithTag("next-event-card").performScrollTo().assertIsDisplayed()
            bringGridStartIntoVerticalViewport()
            gridText("ABCDE", FIRST_SHIFT_DATE).performScrollTo().assertIsDisplayed()
            gridText("19:00–07:00", FIRST_SHIFT_DATE).performScrollTo().assertIsDisplayed()
            gridText("Próxima", FIRST_SHIFT_DATE).performScrollTo().assertIsDisplayed()
            gridText("RGT", RIGHTMOST_SHIFT_DATE).performScrollTo().assertIsDisplayed()
            gridText("08:00–16:00", RIGHTMOST_SHIFT_DATE).performScrollTo().assertIsDisplayed()
            gridText("Cancelada", RIGHTMOST_SHIFT_DATE).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("calendar-v2-load-shifts").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("calendar-work-setup-action").performScrollTo().assertIsDisplayed()
        }
    }

    private fun setCalendarViewport(
        height: Dp,
        darkTheme: () -> Boolean = { false },
        referenceNow: Instant = REFERENCE_NOW,
    ) {
        compose.setContent {
            MiGuardiaTheme(appZoom = AppZoom.STANDARD) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("calendar-test-root"),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Box(
                        Modifier
                            .sizeIn(maxWidth = 400.dp, maxHeight = height)
                            .fillMaxSize()
                            .testTag("calendar-test-host"),
                    ) {
                        MiGuardiaTheme(darkTheme = darkTheme(), appZoom = AppZoom.STANDARD) {
                            CalendarUnderTest(appZoom = AppZoom.STANDARD, referenceNow = referenceNow)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun CalendarUnderTest(
        appZoom: AppZoom,
        referenceNow: Instant = REFERENCE_NOW,
    ) {
        val shifts = fixtureShifts()
        MiGuardiaApp(
            calendarState = calendarState(referenceNow, shifts),
            onPreviousMonth = {},
            onNextMonth = {},
            onToday = {},
            onSelectDate = {},
            onDismissDate = {},
            onRetry = {},
            nextEventState = NextEventUiState(
                loadState = NextEventLoadState.CONTENT,
                result = projectTodayCard(
                    now = referenceNow,
                    zoneId = AppDefaults.zoneId(),
                    todayShifts = shifts,
                    previousDayCandidates = shifts,
                    actualsByShiftId = emptyMap(),
                    vacations = emptyList(),
                    medicalLeaves = emptyList(),
                    futureEvent = projectNextEvent(
                        referenceNow,
                        AppDefaults.zoneId(),
                        shifts,
                        emptyList(),
                        emptyList(),
                    ),
                ),
            ),
            appZoom = appZoom,
        )
    }

    private fun calendarState(referenceNow: Instant, shifts: List<Shift>): CalendarUiState = CalendarUiState(
        visibleMonth = MONTH,
        referenceInstant = referenceNow,
        days = projectCalendarMonth(
            month = MONTH,
            shifts = shifts,
            explicitDayStatuses = emptyList(),
            medicalLeaves = emptyList(),
            now = referenceNow,
        ),
        loadState = CalendarLoadState.CONTENT,
        hasAnyShifts = true,
        hasAnyShiftsLoaded = true,
    )

    private fun fixtureShifts(): List<Shift> = listOf(
        shift(
            id = "84000000-0000-0000-0000-000000000001",
            date = FIRST_SHIFT_DATE,
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(7, 0),
            name = "Objetivo adaptable ficticio",
            abbreviation = "ABCDE",
            position = "Acceso ficticio",
            status = ShiftStatus.PLANNED,
        ),
        shift(
            id = "84000000-0000-0000-0000-000000000002",
            date = RIGHTMOST_SHIFT_DATE,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(16, 0),
            name = "Objetivo derecho ficticio",
            abbreviation = "RGT",
            position = null,
            status = ShiftStatus.CANCELLED,
        ),
    )

    private fun shift(
        id: String,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        name: String,
        abbreviation: String,
        position: String?,
        status: ShiftStatus,
    ): Shift {
        val endDate = if (endTime > startTime) date else date.plusDays(1)
        val start = ZonedDateTime.of(date, startTime, AppDefaults.zoneId())
        val end = ZonedDateTime.of(endDate, endTime, AppDefaults.zoneId())
        return Shift(
            id = UUID.fromString(id),
            startAt = start.toInstant(),
            endAt = end.toInstant(),
            zoneId = AppDefaults.zoneId(),
            localStartDate = date,
            objectiveNameSnapshot = name,
            objectiveAbbreviationSnapshot = abbreviation,
            objectiveAddressSnapshot = null,
            startTimeSnapshot = startTime,
            endTimeSnapshot = endTime,
            colorArgbSnapshot = 0xFF315DA8.toInt(),
            position = position,
            status = status,
            sourceObjectiveId = UUID(0L, 840L),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    private fun SemanticsNodeInteraction.verticalScrollRange(): ScrollAxisRange =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun gridText(text: String, date: LocalDate): SemanticsNodeInteraction = compose.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag("day-$date")),
        useUnmergedTree = true,
    )

    private fun bringGridStartIntoVerticalViewport() {
        val viewportNode = compose.onNodeWithTag("calendar-scroll-viewport")
        val gridNode = compose.onNodeWithTag("month-grid")
        val scrollContainer = compose.onNodeWithTag("calendar-scroll-container")
        repeat(4) {
            val viewport = viewportNode.bounds()
            val grid = gridNode.bounds()
            val gridIsClippedOut = grid.width <= 0f || grid.height <= 0f
            val delta = if (gridIsClippedOut) {
                viewport.height * 0.75f
            } else {
                (grid.top - viewport.top).coerceAtLeast(0f)
            }
            if (delta <= 1f) {
                gridNode.assertIsDisplayed()
                return
            }
            scrollContainer.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
                assertTrue(scrollBy(0f, delta))
            }
            compose.waitForIdle()
        }
        gridNode.assertIsDisplayed()
    }

    private fun assertContained(viewport: Rect, child: Rect) {
        assertTrue("El bloque quedó por encima del viewport: $child / $viewport", child.top >= viewport.top)
        assertTrue("El bloque quedó por debajo del viewport: $child / $viewport", child.bottom <= viewport.bottom)
        assertTrue("El bloque quedó a la izquierda del viewport: $child / $viewport", child.left >= viewport.left)
        assertTrue("El bloque quedó a la derecha del viewport: $child / $viewport", child.right <= viewport.right)
    }

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        val FIRST_SHIFT_DATE: LocalDate = LocalDate.of(2026, 8, 3)
        val RIGHTMOST_SHIFT_DATE: LocalDate = LocalDate.of(2026, 8, 9)
        val REFERENCE_NOW: Instant = ZonedDateTime.of(
            LocalDate.of(2026, 8, 2),
            LocalTime.NOON,
            AppDefaults.zoneId(),
        ).toInstant()
        val AFTER_SHIFTS_NOW: Instant = ZonedDateTime.of(
            LocalDate.of(2026, 8, 15),
            LocalTime.NOON,
            AppDefaults.zoneId(),
        ).toInstant()
    }
}
