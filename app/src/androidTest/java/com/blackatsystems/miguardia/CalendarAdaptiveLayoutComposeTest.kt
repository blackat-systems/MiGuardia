package com.blackatsystems.miguardia

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
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
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarSelectionPurpose
import com.blackatsystems.miguardia.ui.calendar.CalendarDateTextBounds
import com.blackatsystems.miguardia.ui.calendar.CalendarDateFontSizePx
import com.blackatsystems.miguardia.ui.calendar.CalendarDateTextOverflow
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadUiState
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadStage
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadActions
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsUiState
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasUiState
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasLoadState
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasActions
import com.blackatsystems.miguardia.ui.availability.AvailabilityUiState
import com.blackatsystems.miguardia.ui.availability.AvailabilityLoadState
import com.blackatsystems.miguardia.ui.availability.AvailabilityActions
import com.blackatsystems.miguardia.ui.nextevent.NextEventLoadState
import com.blackatsystems.miguardia.ui.nextevent.NextEventUiState
import com.blackatsystems.miguardia.ui.notifications.NotificationActions
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.androidFontDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
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
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun standardFontScalePhysicalViewportShowsTheWholeMonthWithoutPermanentActions() {
        setCalendarViewport(height = 1_080.dp, referenceNow = AFTER_SHIFTS_NOW)

        val root = compose.onNodeWithTag("calendar-test-root").bounds()
        val host = compose.onNodeWithTag("calendar-test-host").bounds()
        assertContained(root, host)

        compose.onNodeWithTag("calendar-static-content").assertIsDisplayed()
        compose.onNodeWithTag("calendar-scroll-container").assertDoesNotExist()
        compose.onNodeWithTag("calendar-scrollbar-track").assertDoesNotExist()

        compose.onNodeWithTag("month-grid").assertIsDisplayed()
        val viewport = compose.onNodeWithTag("calendar-test-host").bounds()
        assertContained(viewport, compose.onNodeWithTag("month-grid").bounds())
        assertWholeMonthContained()
        compose.onNodeWithTag("calendar-primary-actions").assertDoesNotExist()
        compose.onNodeWithTag("calendar-v2-load-shifts").assertDoesNotExist()
        compose.onNodeWithTag("calendar-select-days").assertDoesNotExist()
        compose.onNodeWithTag("advanced-options-toggle").assertDoesNotExist()

        compose.onNodeWithTag("main-menu-button").performClick()
        // ADR 0015: el menú es desplazable; cada acción debe poder llevarse al viewport.
        compose.onNodeWithTag("drawer-action-calendar-load").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("drawer-action-calendar-select").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("drawer-action-calendar-repeat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("main-destination-calendar").performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
        compose.onNodeWithTag("month-grid").assertIsDisplayed()
        assertContained(viewport, compose.onNodeWithTag("month-grid").bounds())
        assertWholeMonthContained()
        compose.onNodeWithTag("calendar-primary-actions").assertDoesNotExist()
        compose.onNodeWithTag("calendar-scroll-container").assertDoesNotExist()
    }

    @Test
    fun shortViewportKeepsTheWholeMonthWithoutAddingVerticalScroll() {
        var darkTheme by mutableStateOf(false)
        setCalendarViewport(height = 700.dp, darkTheme = { darkTheme })

        compose.onNodeWithTag("calendar-scroll-container").assertDoesNotExist()
        compose.onNodeWithTag("calendar-scrollbar-track").assertDoesNotExist()
        assertWholeMonthContained()
        compose.onNodeWithTag("calendar-primary-actions").assertDoesNotExist()

        compose.runOnIdle { darkTheme = true }
        compose.onNodeWithTag("calendar-static-content").assertIsDisplayed()
        assertWholeMonthContained()
    }

    @Test
    fun anOldCollapsedPreferenceCannotHideDates() {
        setCalendarViewport(height = 700.dp, collapsed = true)

        assertWholeMonthContained()
        compose.onNodeWithTag("calendar-primary-actions").assertDoesNotExist()
    }

    @Test
    fun fixedPhysicalViewportKeepsCalendarReachableAtEveryAndroidFontScale() {
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var baseDensity = 0f
        compose.setContent {
            AndroidFontScaleTheme(fontScale = TestFontScale.STANDARD) {
                val density = LocalDensity.current.density
                SideEffect { baseDensity = density }
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
                        key(fontScale) {
                            AndroidFontScaleTheme(fontScale = fontScale) {
                                CalendarUnderTest()
                            }
                        }
                    }
                }
            }
        }

        TestFontScale.entries.forEach { option ->
            compose.runOnIdle { fontScale = option }
            compose.waitForIdle()
            compose.onNodeWithTag("calendar-scroll-container").assertDoesNotExist()
            compose.onNodeWithTag("calendar-scrollbar-track").assertDoesNotExist()
            val viewport = compose.onNodeWithTag("calendar-test-host").bounds()
            assertCalendarGeometryAndText(viewport, baseDensity, option)
            dayCell(FIRST_SHIFT_DATE).assert(
                hasContentDescription("jornada ABCDE de 19:00 a 07:00 del día siguiente, próxima", substring = true),
            )
            dayCell(RIGHTMOST_SHIFT_DATE).assert(
                hasContentDescription("jornada RGT de 08:00 a 16:00, cancelada", substring = true),
            )
            compose.onNodeWithTag("calendar-primary-actions").assertDoesNotExist()
        }
    }

    @Test
    fun shortWindowManualSelectionKeepsSixFullRowsAtTwoHundredPercent() {
        var baseDensity = 0f
        var systemInsets = "not observed"
        var requestedDayOff: Pair<Set<LocalDate>, Boolean>? = null
        compose.setContent {
            AndroidFontScaleTheme {
                val localDensity = LocalDensity.current
                val layoutDirection = LocalLayoutDirection.current
                val density = localDensity.density
                val insets = WindowInsets.systemBars
                val insetSnapshot = "top=${insets.getTop(localDensity)},bottom=${insets.getBottom(localDensity)}," +
                    "left=${insets.getLeft(localDensity, layoutDirection)},right=${insets.getRight(localDensity, layoutDirection)} px; " +
                    "window=${LocalWindowInfo.current.containerSize}"
                SideEffect {
                    baseDensity = density
                    systemInsets = insetSnapshot
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    Box(Modifier.sizeIn(maxWidth = 800.dp, maxHeight = 520.dp).fillMaxSize()
                        .testTag("calendar-test-host")) {
                        AndroidFontScaleTheme(fontScale = TestFontScale.EXTRA_LARGE) {
                            CalendarUnderTest(manualSelection = true,
                                manualActions = V2ManualShiftLoadActions(requestDayOff = { dates, value ->
                                    requestedDayOff = dates to value
                                }))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertCalendarGeometryAndText(compose.onNodeWithTag("calendar-test-host").bounds(),
            baseDensity, TestFontScale.EXTRA_LARGE, "requestedHost=800x520 base-dp; systemInsets=$systemInsets")
        compose.onNodeWithTag("v2-manual-confirm-dates").assertIsDisplayed()
        compose.onNodeWithTag("calendar-selection-cancel").assertIsDisplayed()
        compose.onNodeWithTag("calendar-manual-selection-options").assertIsDisplayed().performClick()
        compose.onNodeWithText("Cerrar").performClick()
        assertEquals(null, requestedDayOff)
        compose.onNodeWithTag("calendar-manual-selection-options").performClick()
        compose.onNodeWithTag("v2-mark-day-off").assertIsDisplayed().performClick()
        assertEquals(setOf(FIRST_DATE) to true, requestedDayOff)
        assertCalendarGeometryAndText(compose.onNodeWithTag("calendar-test-host").bounds(),
            baseDensity, TestFontScale.EXTRA_LARGE, "requestedHost=800x520 base-dp; systemInsets=$systemInsets")
    }

    @Test
    fun wideShortWindowKeepsRailControlsAndEverySelectionOwnerAtNativeLargeText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName == "com.blackatsystems.miguardia.qa")
        val originalOrientation = compose.activity.requestedOrientation
        var purpose by mutableStateOf<CalendarSelectionPurpose?>(null)
        var density = 0f
        var previous = 0
        var next = 0
        var calendarLoadState by mutableStateOf(CalendarLoadState.CONTENT)
        var extrasLoadState by mutableStateOf(HoursAndExtrasLoadState.CONTENT)
        var availabilityLoadState by mutableStateOf(AvailabilityLoadState.CONTENT)
        var extrasRetries = 0
        var availabilityRetries = 0
        val events = mutableListOf<String>()
        try {
            instrumentation.runOnMainSync { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(30_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            compose.setContent {
                AndroidFontScaleTheme(darkTheme = true, fontScale = TestFontScale.EXTRA_LARGE) {
                    val nativeDensity = LocalDensity.current.density
                    SideEffect { density = nativeDensity }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                        Box(Modifier.sizeIn(maxWidth = 800.dp, maxHeight = 384.dp).fillMaxSize()
                            .testTag("calendar-test-host")) {
                            CalendarUnderTest(
                                calendarLoadState = calendarLoadState,
                                selectionPurpose = purpose,
                                manualSelection = purpose == CalendarSelectionPurpose.MANUAL_LOAD,
                                confirmationMessage = "Confirmación sintética completa para revisar desde el lateral",
                                onPrevious = { previous += 1 }, onNext = { next += 1 },
                                onFinishSelection = { events += "finish"; purpose = null },
                                manualActions = V2ManualShiftLoadActions(
                                    confirmDates = { events += "manual-confirm" },
                                    cancel = { events += "manual-cancel" },
                                ),
                                holidayActions = ExceptionsActions(
                                    confirmHolidaySelection = { _, _ -> events += "holiday-confirm" },
                                    cancelHolidaySelection = { events += "holiday-cancel"; purpose = null },
                                ),
                                hoursState = HoursAndExtrasUiState(loadState = extrasLoadState,
                                    message = "Extras sintéticos no disponibles".takeIf { extrasLoadState == HoursAndExtrasLoadState.ERROR }),
                                hoursActions = HoursAndExtrasActions(retry = {
                                    extrasRetries += 1; extrasLoadState = HoursAndExtrasLoadState.LOADING
                                }),
                                availabilityState = AvailabilityUiState(loadState = availabilityLoadState,
                                    message = "Disponibilidad sintética no disponible".takeIf { availabilityLoadState == AvailabilityLoadState.ERROR }),
                                availabilityActions = AvailabilityActions(retry = {
                                    availabilityRetries += 1; availabilityLoadState = AvailabilityLoadState.LOADING
                                }),
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            fun assertRailGeometry(vararg extraControls: String) {
                val viewport = compose.onNodeWithTag("calendar-test-host").bounds()
                assertCalendarGeometryAndText(viewport, density, TestFontScale.EXTRA_LARGE, "wide short lateral layout")
                val rail = compose.onNodeWithTag("calendar-side-rail").assertIsDisplayed().bounds()
                assertContained(viewport, rail)
                compose.onNodeWithTag("main-top-bar").assertDoesNotExist()
                compose.onNodeWithTag("calendar-scroll-container").assertDoesNotExist()
                val controls = listOf("main-menu-button", "calendar-previous-month", "calendar-next-month",
                    "calendar-header-notice") + extraControls
                val targets = controls.map { tag ->
                    val node = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
                    assertContained(rail, node.touchBoundsInRoot)
                    assertTrue("$tag must retain 48 native dp", node.touchBoundsInRoot.width + 1 >= 48 * density &&
                        node.touchBoundsInRoot.height + 1 >= 48 * density)
                    tag to node.touchBoundsInRoot
                }
                targets.forEachIndexed { index, (tag, bounds) -> targets.drop(index + 1).forEach { (other, otherBounds) ->
                    assertTrue("Rail targets overlap: $tag / $other", minOf(bounds.right, otherBounds.right) -
                        maxOf(bounds.left, otherBounds.left) <= 1 || minOf(bounds.bottom, otherBounds.bottom) -
                        maxOf(bounds.top, otherBounds.top) <= 1)
                } }
            }
            assertRailGeometry()
            val originalGridBounds = compose.onNodeWithTag("month-grid").bounds()
            compose.runOnIdle { calendarLoadState = CalendarLoadState.LOADING }
            val progress = compose.onNodeWithTag("calendar-loading-progress")
                .assertIsDisplayed().assert(hasContentDescription("Cargando calendario")).bounds()
            assertContained(compose.onNodeWithTag("calendar-side-rail").bounds(), progress)
            assertTrue("Refresh progress must retain its own four native dp", progress.height + 1f >= 4f * density)
            assertRailGeometry()
            assertEquals("Refresh must not consume any month-grid height", originalGridBounds,
                compose.onNodeWithTag("month-grid").bounds())
            compose.runOnIdle { calendarLoadState = CalendarLoadState.CONTENT }
            compose.onNodeWithTag("calendar-loading-progress").assertDoesNotExist()
            assertRailGeometry()
            compose.onNodeWithTag("calendar-primary-actions").assertDoesNotExist()
            compose.onNodeWithTag("calendar-previous-month").performClick()
            compose.onNodeWithTag("calendar-next-month").performClick()
            assertEquals(1, previous)
            assertEquals(1, next)
            compose.onNodeWithTag("calendar-header-notice").performClick()
            compose.onNodeWithTag("calendar-notice-details").assertIsDisplayed()
            compose.onNodeWithText("Entendido").performClick()
            compose.onNodeWithTag("main-menu-button").performClick()
            compose.onNodeWithTag("main-destination-calendar").performScrollTo().performClick()
            assertRailGeometry()
            CalendarSelectionPurpose.entries.forEach { owner ->
                compose.runOnIdle { purpose = owner }
                val continueTag = when (owner) {
                    CalendarSelectionPurpose.GENERAL -> "calendar-selection-continue"
                    CalendarSelectionPurpose.MANUAL_LOAD -> "v2-manual-confirm-dates"
                    CalendarSelectionPurpose.HOLIDAY -> "holiday-confirm-calendar-selection"
                }
                val cancelTag = if (owner == CalendarSelectionPurpose.HOLIDAY) "holiday-cancel-calendar-selection"
                    else "calendar-selection-cancel"
                val manualOptions = if (owner == CalendarSelectionPurpose.MANUAL_LOAD)
                    arrayOf("calendar-manual-selection-options") else emptyArray()
                assertRailGeometry(continueTag, cancelTag, *manualOptions)
                compose.onNodeWithTag(continueTag).performClick()
                if (owner == CalendarSelectionPurpose.GENERAL) compose.onNodeWithText("Volver").performClick()
                compose.onNodeWithTag(cancelTag).performClick()
                if (owner == CalendarSelectionPurpose.MANUAL_LOAD) compose.onNodeWithText("Descartar carga").performClick()
                compose.runOnIdle { assertEquals(null, purpose) }
                assertRailGeometry()
            }
            assertEquals(listOf("finish", "manual-confirm", "manual-cancel", "finish", "holiday-confirm", "holiday-cancel"), events)
            events.clear()
            CalendarSelectionPurpose.entries.forEach { owner ->
                compose.runOnIdle { purpose = owner }
                compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.runOnIdle { assertEquals(null, purpose) }
            }
            assertEquals(listOf("finish", "manual-cancel", "finish", "holiday-cancel", "finish"), events)
            compose.runOnIdle {
                extrasLoadState = HoursAndExtrasLoadState.ERROR
                availabilityLoadState = AvailabilityLoadState.ERROR
            }
            assertRailGeometry("calendar-update-problems")
            compose.onNodeWithTag("calendar-update-problems").performClick()
            compose.onNodeWithTag("calendar-problem-details").assertIsDisplayed()
            compose.onNodeWithText("Extras sintéticos no disponibles").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Disponibilidad sintética no disponible").performScrollTo().assertIsDisplayed()
            compose.onNode(hasText("Reintentar") and hasAnyAncestor(hasTestTag("calendar-hours-problem")))
                .performScrollTo().performClick()
            compose.onNodeWithTag("calendar-problem-details").assertIsDisplayed()
            compose.onNodeWithTag("calendar-hours-problem").assertDoesNotExist()
            compose.onNode(hasText("Reintentar") and hasAnyAncestor(hasTestTag("calendar-availability-problem")))
                .performScrollTo().performClick()
            compose.onNodeWithTag("calendar-problem-details").assertDoesNotExist()
            compose.onNodeWithTag("calendar-update-problems").assertDoesNotExist()
            compose.runOnIdle {
                extrasLoadState = HoursAndExtrasLoadState.CONTENT
                availabilityLoadState = AvailabilityLoadState.CONTENT
            }
            compose.onNodeWithTag("calendar-problem-details").assertDoesNotExist()
            assertEquals(1, extrasRetries)
            assertEquals(1, availabilityRetries)
            assertRailGeometry()
        } finally {
            instrumentation.runOnMainSync { compose.activity.requestedOrientation = originalOrientation }
        }
    }

    @Test
    fun shiftDetailKeepsItsParticularNotificationSettingsReachable() {
        var openedShiftId: UUID? = null
        var dismissals = 0
        compose.setContent {
            AndroidFontScaleTheme {
                CalendarUnderTest(
                    referenceNow = AFTER_SHIFTS_NOW,
                    detailDate = FIRST_SHIFT_DATE,
                    onDismissDate = { dismissals += 1 },
                    notificationActions = NotificationActions(
                        openShift = { shift -> openedShiftId = shift.id },
                    ),
                )
            }
        }

        compose.onNodeWithTag("shift-notifications-84000000-0000-0000-0000-000000000001")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            assertEquals(UUID.fromString("84000000-0000-0000-0000-000000000001"), openedShiftId)
            assertEquals(1, dismissals)
        }
    }

    private fun setCalendarViewport(
        height: Dp,
        darkTheme: () -> Boolean = { false },
        referenceNow: Instant = REFERENCE_NOW,
        collapsed: Boolean = false,
    ) {
        compose.setContent {
            AndroidFontScaleTheme(fontScale = TestFontScale.STANDARD) {
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
                        AndroidFontScaleTheme(darkTheme = darkTheme(), fontScale = TestFontScale.STANDARD) {
                            CalendarUnderTest(
                                referenceNow = referenceNow,
                                collapsed = collapsed,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun CalendarUnderTest(
        referenceNow: Instant = REFERENCE_NOW,
        detailDate: LocalDate? = null,
        collapsed: Boolean = false,
        onDismissDate: () -> Unit = {},
        notificationActions: NotificationActions = NotificationActions(),
        manualSelection: Boolean = false,
        manualActions: V2ManualShiftLoadActions = V2ManualShiftLoadActions(),
        selectionPurpose: CalendarSelectionPurpose? = if (manualSelection) CalendarSelectionPurpose.MANUAL_LOAD else null,
        confirmationMessage: String? = null,
        onPrevious: () -> Unit = {},
        onNext: () -> Unit = {},
        onFinishSelection: () -> Unit = {},
        holidayActions: ExceptionsActions = ExceptionsActions(),
        hoursState: HoursAndExtrasUiState = HoursAndExtrasUiState(),
        hoursActions: HoursAndExtrasActions = HoursAndExtrasActions(),
        availabilityState: AvailabilityUiState = AvailabilityUiState(),
        availabilityActions: AvailabilityActions = AvailabilityActions(),
        calendarLoadState: CalendarLoadState = CalendarLoadState.CONTENT,
    ) {
        val shifts = fixtureShifts()
        val writes = shifts.map(::v2Write)
        MiGuardiaApp(
            calendarState = calendarState(referenceNow, shifts).copy(
                loadState = calendarLoadState,
                detailDate = detailDate,
                calendarCollapsed = collapsed,
                interactionMode = if (selectionPurpose != null) CalendarInteractionMode.EDIT else CalendarInteractionMode.VIEW,
                selectionPurpose = selectionPurpose,
                editSelectedDates = if (selectionPurpose != null) setOf(FIRST_DATE) else emptySet(),
                batchMessage = confirmationMessage,
            ),
            onPreviousMonth = onPrevious,
            onNextMonth = onNext,
            onToday = {},
            onSelectDate = {},
            onDismissDate = onDismissDate,
            onRetry = {},
            onFinishCalendarEditMode = onFinishSelection,
            nextEventState = NextEventUiState(
                loadState = NextEventLoadState.CONTENT,
                result = projectTodayCard(
                    now = referenceNow,
                    zoneId = AppDefaults.zoneId(),
                    shifts = writes,
                    actualsByShiftId = emptyMap(),
                    vacations = emptyList(),
                    medicalLeaves = emptyList(),
                    futureEvent = projectNextEvent(
                        now = referenceNow,
                        zoneId = AppDefaults.zoneId(),
                        input = NextEventInput(
                            shifts = writes,
                            availabilityWindows = emptyList(),
                            actualsByShiftId = emptyMap(),
                            independentExtras = emptyList(),
                            explicitDayStatuses = emptyList(),
                            vacations = emptyList(),
                            medicalLeaves = emptyList(),
                        ),
                    ),
                ),
            ),
            notificationActions = notificationActions,
            v2ManualShiftLoadState = if (manualSelection) V2ManualShiftLoadUiState(
                stage = V2ManualShiftLoadStage.SELECT_DATES, timelineId = UUID(0L, 0L),
                selectedDates = setOf(FIRST_DATE),
            ) else V2ManualShiftLoadUiState(),
            v2ManualShiftLoadActions = manualActions,
            exceptionsState = ExceptionsUiState(holidayMonth = MONTH,
                holidaySelectionActive = selectionPurpose == CalendarSelectionPurpose.HOLIDAY),
            exceptionsActions = holidayActions,
            hoursAndExtrasState = hoursState,
            hoursAndExtrasActions = hoursActions,
            availabilityState = availabilityState,
            availabilityActions = availabilityActions,
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
        lastOpenedDate = FIRST_SHIFT_DATE,
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

    private fun v2Write(shift: Shift): V2ShiftWrite = V2ShiftWrite(
        shift = shift,
        snapshot = ShiftWorkSnapshot(
            shiftId = shift.id,
            timelineId = UUID(0L, 841L),
            sector = WorkSector.PRIVATE_SECURITY,
            configurationRevisionId = UUID(0L, 842L),
            workPlaceId = UUID(0L, 843L),
            objectiveId = shift.sourceObjectiveId,
            templateId = UUID(0L, 844L),
            workTypeId = UUID(0L, 845L),
            workTypeNameSnapshot = "Jornada ficticia",
            workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
        ),
    )

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun dayCell(date: LocalDate): SemanticsNodeInteraction = compose.onNode(
        hasTestTag("day-$date") or hasTestTag("completed-day-$date"),
    )

    private fun assertContained(viewport: Rect, child: Rect) {
        assertTrue("El bloque quedó por encima del viewport: $child / $viewport", child.top >= viewport.top)
        assertTrue("El bloque quedó por debajo del viewport: $child / $viewport", child.bottom <= viewport.bottom)
        assertTrue("El bloque quedó a la izquierda del viewport: $child / $viewport", child.left >= viewport.left)
        assertTrue("El bloque quedó a la derecha del viewport: $child / $viewport", child.right <= viewport.right)
    }

    private fun assertWholeMonthContained() {
        val viewport = compose.onNodeWithTag("calendar-test-host").bounds()
        (1..MONTH.lengthOfMonth()).forEach { day ->
            assertContained(viewport, dayCell(MONTH.atDay(day)).assertIsDisplayed().bounds())
        }
    }

    private fun assertCalendarGeometryAndText(viewport: Rect, baseDensity: Float, fontScale: TestFontScale, metadata: String = "") {
        val touchTargets = mutableListOf<Pair<LocalDate, Rect>>()
        fun boundsIfPresent(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
            .firstOrNull()?.boundsInRoot
        val diagnostic = "fontScale=${fontScale.percent}; baseDensity=$baseDensity; viewport=$viewport; " +
            "root=${compose.onRoot().fetchSemanticsNode().boundsInRoot}; " +
            "grid=${boundsIfPresent("month-grid")}; static=${boundsIfPresent("calendar-static-content")}; " +
            "toolbar=${boundsIfPresent("calendar-primary-actions")}; appbar=${boundsIfPresent("main-top-bar")}; $metadata"
        (1..MONTH.lengthOfMonth()).forEach { number ->
            val date = MONTH.atDay(number)
            val node = dayCell(date).assertIsDisplayed().fetchSemanticsNode()
            assertContained(viewport, node.boundsInRoot)
            val touch = node.touchBoundsInRoot
            assertContained(viewport, touch)
            assertTrue("$date target below 48 base-dp; visual=${node.boundsInRoot}; touch=$touch; $diagnostic",
                touch.width + 1 >= 48 * baseDensity && touch.height + 1 >= 48 * baseDensity)
            touchTargets.forEach { (previousDate, previous) ->
                val overlapWidth = minOf(touch.right, previous.right) - maxOf(touch.left, previous.left)
                val overlapHeight = minOf(touch.bottom, previous.bottom) - maxOf(touch.top, previous.top)
                assertTrue("Day hit targets overlap: $date visual=${node.boundsInRoot}; touch=$touch; " +
                    "$previousDate touch=$previous; overlap=${overlapWidth}x${overlapHeight}px; $diagnostic",
                    overlapWidth <= 0 || overlapHeight <= 0)
            }
            touchTargets += date to touch
            assertEquals(false, node.config.getOrNull(CalendarDateTextOverflow))
            assertContained(node.boundsInRoot, requireNotNull(node.config.getOrNull(CalendarDateTextBounds)))
            val nativeDensity = androidFontDensity(InstrumentationRegistry.getInstrumentation().targetContext,
                Density(baseDensity, 1f), fontScale)
            assertEquals(with(nativeDensity) { 14.sp.toPx() },
                requireNotNull(node.config.getOrNull(CalendarDateFontSizePx)), .5f)
        }
    }

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        val FIRST_DATE: LocalDate = MONTH.atDay(1)
        val LAST_DATE: LocalDate = MONTH.atEndOfMonth()
        val FIRST_SHIFT_DATE: LocalDate = LocalDate.of(2026, 8, 3)
        val RIGHTMOST_SHIFT_DATE: LocalDate = LocalDate.of(2026, 8, 9)
        val REFERENCE_NOW: Instant = ZonedDateTime.of(
            LocalDate.of(2026, 8, 3),
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
