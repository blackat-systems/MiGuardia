package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarSelectionPurpose
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.ui.components.AdvancedOptionsSection
import com.blackatsystems.miguardia.ui.components.ContextHelp
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadUiState
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupActions
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupStep
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurfaceHost
import com.blackatsystems.miguardia.ui.worksetup.WorkTemplateDraft
import com.blackatsystems.miguardia.ui.worksetup.previewV2WorkSetupUiState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.io.File
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pure presentation fixtures: no Room, beta activation, preferences or device settings. */
class ContextualLayoutComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun advancedLabelsKeepWholeWordsAndHelpAtEveryAndroidFontScale() {
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var dark by mutableStateOf(false)
        compose.setContent {
            // Constrain geometry before simulating Android text size; DP density stays native.
            Box(Modifier.width(360.dp)) {
                AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        AdvancedOptionsSection(
                            help = ContextHelp("Prueba", "Qué hace ficticio", "Uso ficticio", "Ejemplo ficticio"),
                        ) { Text("Contenido avanzado ficticio") }
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (scale in TestFontScale.entries) {
            compose.runOnIdle { dark = theme; fontScale = scale }
            assertWholeWords("Opciones avanzadas")
            compose.onNodeWithTag("advanced-options-toggle").performScrollTo().performClick()
            assertWholeWords("Ocultar opciones avanzadas")
            compose.onNodeWithText("Contenido avanzado ficticio").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Ayuda sobre Prueba").performScrollTo().performClick()
            compose.onNodeWithText("Qué hace").assertIsDisplayed()
            compose.onNodeWithText("Entendido").performClick()
            compose.onNodeWithTag("advanced-options-toggle").performScrollTo().performClick()
        }
    }

    @Test
    fun openHelpSurvivesReflowAndSavedStateRestoration() {
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            Box(Modifier.width(360.dp)) {
                AndroidFontScaleTheme(fontScale = fontScale) {
                    AdvancedOptionsSection(help = ContextHelp("Prueba", "Qué hace ficticio", "Uso ficticio", "Ejemplo ficticio")) { }
                }
            }
        }
        compose.onNodeWithContentDescription("Ayuda sobre Prueba").performClick()
        compose.onNodeWithTag("context-help-dialog").assertIsDisplayed()
        compose.runOnIdle { fontScale = TestFontScale.EXTRA_LARGE }
        compose.onNodeWithTag("context-help-dialog").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("context-help-dialog").assertIsDisplayed()
        compose.runOnIdle { fontScale = TestFontScale.STANDARD }
        compose.onNodeWithTag("context-help-dialog").assertIsDisplayed()
        compose.onNodeWithText("Entendido").performClick()
        compose.onNodeWithTag("context-help-dialog").assertDoesNotExist()
    }

    @Test
    fun bothTemplateFormsKeepColorLabelsWholeAndPickerReachable() {
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var dark by mutableStateOf(false)
        var state by mutableStateOf(previewV2WorkSetupUiState())
        compose.setContent {
            Box(Modifier.width(360.dp)) {
                AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                    WorkSetupSurfaceHost(state, WorkSetupActions())
                }
            }
        }
        for (surface in listOf(WorkSetupSurface.FIRST_WORK_SET, WorkSetupSurface.ADDITIONAL_TEMPLATE)) {
            for (theme in listOf(false, true)) for (scale in TestFontScale.entries) {
                for (color in listOf(null, 0xFF1565C0.toInt())) {
                    compose.runOnIdle {
                        dark = theme
                        fontScale = scale
                        state = state.copy(
                            surface = surface,
                            step = WorkSetupStep.TYPE_AND_TEMPLATE,
                            templateDraft = WorkTemplateDraft("Trabajo ficticio", "08:00", "16:00", color),
                        )
                    }
                    assertWholeWords(if (color == null) "Elegir color" else "Cambiar color")
                    compose.onNodeWithTag("work-template-color").performScrollTo().performClick()
                    compose.onNodeWithText("Selector de color").assertIsDisplayed()
                    compose.onNodeWithText("Usar color").performClick()
                    compose.onNodeWithText("Selector de color").assertDoesNotExist()
                }
            }
        }
    }

    @Test
    fun calendarNoticeLeavesMonthDaysAndSelectionTouchableInLowWindow() {
        val month = YearMonth.of(2026, 8) // Six rows, including both extreme dates.
        val now = Instant.parse("2026-08-01T12:00:00Z")
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var dark by mutableStateOf(false)
        var editing by mutableStateOf(false)
        var previous = 0
        var next = 0
        val openedDates = mutableListOf<LocalDate>()
        val changedSelections = mutableListOf<Set<LocalDate>>()
        var cancelled = 0
        var density = 1f
        val failures = mutableListOf<String>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            // Constrain native DP geometry before simulating Android text size.
            AndroidFontScaleTheme(fontScale = TestFontScale.STANDARD) {
            Box(Modifier.width(360.dp).height(600.dp)) {
                AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                    density = LocalDensity.current.density
                    MiGuardiaApp(
                        modifier = Modifier.testTag("low-calendar-root"),
                        calendarState = CalendarUiState(
                            visibleMonth = month, referenceInstant = now,
                            days = projectCalendarMonth(month, emptyList(), emptyList(), emptyList(), now),
                            loadState = CalendarLoadState.CONTENT,
                            interactionMode = if (editing) CalendarInteractionMode.EDIT else CalendarInteractionMode.VIEW,
                            selectionPurpose = if (editing) CalendarSelectionPurpose.GENERAL else null,
                            editSelectedDates = if (editing) setOf(month.atDay(1)) else emptySet(),
                            batchMessage = "Cambios guardados. Podés seguir usando el calendario.",
                        ),
                        onPreviousMonth = { previous++ }, onNextMonth = { next++ }, onToday = {},
                        onSelectDate = { openedDates += it }, onDismissDate = {}, onRetry = {},
                        onEditSelectionChange = { changedSelections += it },
                        onFinishCalendarEditMode = { cancelled++ },
                    )
                }
            }
            }
        }
        for (theme in listOf(false, true)) for (scale in TestFontScale.entries) {
            compose.runOnIdle { dark = theme; fontScale = scale; editing = false }
            compose.mainClock.advanceTimeByFrame()
            val label = "fontScale=$scale dark=$theme"
            compose.onNodeWithTag("transient-confirmation").assertDoesNotExist()
            val notice = compose.onNodeWithTag("calendar-header-notice").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            for ((description, count) in listOf("Mes anterior" to { previous }, "Mes siguiente" to { next })) {
                val control = compose.onNodeWithContentDescription(description).assertIsDisplayed()
                val before = count()
                control.performTouchInput { click() }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle { if (count() != before + 1) failures += "$description did not receive one touch: $label" }
                if (notice.overlaps(control.fetchSemanticsNode().boundsInRoot)) failures += "$description covered: $label"
            }
            for (selecting in listOf(false, true)) {
                compose.runOnIdle { editing = selecting }
                compose.mainClock.advanceTimeByFrame()
                val root = compose.onNodeWithTag("low-calendar-root").fetchSemanticsNode().boundsInRoot
                assertEquals("Fixture must really be 360 base dp wide", 360f, root.width / density, 1f)
                assertEquals("Fixture must really be 600 base dp high", 600f, root.height / density, 1f)
                val targets = (1..month.lengthOfMonth()).map { day ->
                    val cell = compose.onNodeWithTag("day-${month.atDay(day)}").assertIsDisplayed().fetchSemanticsNode()
                    val bounds = cell.touchBoundsInRoot
                    if (bounds.width < 48 * density - 1 || bounds.height < 48 * density - 1 ||
                        bounds.left < root.left || bounds.right > root.right ||
                        bounds.top < root.top || bounds.bottom > root.bottom) failures += "Invalid day target $day: $label selecting=$selecting"
                    if (notice.overlaps(bounds)) failures += "Day $day covered: $label selecting=$selecting"
                    day to bounds
                }
                for (i in targets.indices) for (j in i + 1 until targets.size) {
                    if (targets[i].second.overlaps(targets[j].second)) failures += "Day targets ${targets[i].first}/${targets[j].first} overlap: $label selecting=$selecting"
                }
                for (day in 1..month.lengthOfMonth()) {
                    val date = month.atDay(day)
                    val before = if (selecting) changedSelections.size else openedDates.size
                    compose.onNodeWithTag("day-$date").performTouchInput { click() }
                    compose.mainClock.advanceTimeByFrame()
                    compose.runOnIdle {
                        val correct = if (selecting) changedSelections.size == before + 1 &&
                            changedSelections.last() == (if (day == 1) emptySet() else setOf(month.atDay(1), date))
                        else openedDates.size == before + 1 && openedDates.last() == date
                        if (!correct) failures += "Day $day did not receive its own touch: $label selecting=$selecting"
                    }
                }
                // Only the owned Compose fixture is rendered, never the device or another app.
                val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "stage5-notice-review")
                check(folder.exists() || folder.mkdirs())
                val bitmap = compose.onNodeWithTag("low-calendar-root").captureToImage().asAndroidBitmap()
                File(folder, "calendar-${scale.percent}-$theme-$selecting.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            compose.onNodeWithTag("calendar-selection-continue").assertIsDisplayed()
            val before = cancelled
            compose.onNodeWithTag("calendar-selection-cancel").assertIsDisplayed().performTouchInput { click() }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { if (cancelled != before + 1) failures += "Cancel did not receive one touch: $label" }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun calendarConfirmationUsesHeaderWithoutSpendingCalendarHeight() {
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var dark by mutableStateOf(false)
        var density = 1f
        val message = "El horario quedó seleccionado. Revisá la carga y confirmá las jornadas cuando quieras."
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                density = LocalDensity.current.density
                MiGuardiaApp(
                    modifier = Modifier.testTag("app-layout"),
                    calendarState = CalendarUiState(
                        visibleMonth = YearMonth.of(2026, 9),
                        referenceInstant = Instant.parse("2026-09-07T12:00:00Z"),
                        loadState = CalendarLoadState.CONTENT,
                    ),
                    v2ManualShiftLoadState = V2ManualShiftLoadUiState(infoMessage = message),
                    onPreviousMonth = {}, onNextMonth = {}, onToday = {},
                    onSelectDate = {}, onDismissDate = {}, onRetry = {},
                )
            }
        }
        for (theme in listOf(false, true)) for (scale in TestFontScale.entries) {
            compose.runOnIdle { dark = theme; fontScale = scale }
            val toolbar = compose.onNodeWithTag("main-top-bar").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("calendar-static-content").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("transient-confirmation").assertDoesNotExist()
            val notice = compose.onNodeWithTag("calendar-header-notice").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val root = compose.onNodeWithTag("app-layout").fetchSemanticsNode().boundsInRoot
            assertEquals(toolbar.bottom, viewport.top, 1f)
            assertTrue(notice.top >= toolbar.top && notice.bottom <= toolbar.bottom)
            assertTrue(notice.left >= viewport.left && notice.right <= viewport.right)
            assertTrue(notice.bottom <= root.bottom && !notice.overlaps(viewport))
            compose.onNodeWithTag("calendar-header-notice").performTouchInput { click() }
            compose.onNodeWithTag("calendar-notice-details").assertIsDisplayed()
            assertWholeWords(message, scroll = false)
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("calendar-notice-details").assertDoesNotExist()
        }
    }

    @Test
    fun calendarNoticeWaitsForHeaderInShortSelectionWindow() {
        val month = YearMonth.of(2026, 8)
        val now = Instant.parse("2026-08-01T12:00:00Z")
        var editing by mutableStateOf(true)
        var message by mutableStateOf<String?>("Franco aplicado. Revisá el detalle del aviso.")
        var clears = 0
        var baseDensity = 1f
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AndroidFontScaleTheme(fontScale = TestFontScale.STANDARD) {
                Box(Modifier.width(360.dp).height(550.dp)) {
                    AndroidFontScaleTheme(darkTheme = true, fontScale = TestFontScale.EXTRA_LARGE) {
                        baseDensity = LocalDensity.current.density
                        MiGuardiaApp(
                            modifier = Modifier.testTag("short-calendar-root"),
                            calendarState = CalendarUiState(
                                visibleMonth = month, referenceInstant = now,
                                days = projectCalendarMonth(month, emptyList(), emptyList(), emptyList(), now),
                                loadState = CalendarLoadState.CONTENT, batchMessage = message,
                                interactionMode = if (editing) CalendarInteractionMode.EDIT else CalendarInteractionMode.VIEW,
                                selectionPurpose = if (editing) CalendarSelectionPurpose.GENERAL else null,
                                editSelectedDates = if (editing) setOf(month.atDay(1)) else emptySet(),
                            ),
                            onClearCalendarBatchMessage = { clears++; message = null },
                            onPreviousMonth = {}, onNextMonth = {}, onToday = {},
                            onSelectDate = {}, onDismissDate = {}, onRetry = {},
                        )
                    }
                }
            }
        }
        // onSizeChanged publishes the measured window after the first layout.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(550f, compose.onNodeWithTag("short-calendar-root")
            .fetchSemanticsNode().boundsInRoot.height / baseDensity, 1f)
        compose.onNodeWithTag("calendar-header-notice").assertDoesNotExist()
        compose.onNodeWithTag("transient-confirmation").assertDoesNotExist()
        compose.onNodeWithTag("calendar-selection-cancel").assertIsDisplayed()
        for (day in 1..month.lengthOfMonth()) {
            compose.onNodeWithTag("day-${month.atDay(day)}").assertIsDisplayed()
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.runOnIdle { assertEquals(0, clears); assertTrue(message != null); editing = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("calendar-header-notice").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_600)
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(1, clears); assertEquals(null, message) }
        compose.onNodeWithTag("calendar-header-notice").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(3_000)
        compose.runOnIdle { assertEquals(1, clears) }
    }

    @Test
    fun calendarOpenedNoticeSurvivesExpiryAndRestorationWithoutCancellingSelection() {
        val message = "Franco aplicado en 3 días. Este es el detalle completo del aviso."
        var currentMessage by mutableStateOf<String?>(message)
        var clears = 0
        var cancellations = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = CalendarUiState(YearMonth.of(2026, 8), Instant.EPOCH,
                        loadState = CalendarLoadState.CONTENT, batchMessage = currentMessage,
                        interactionMode = CalendarInteractionMode.EDIT,
                        selectionPurpose = CalendarSelectionPurpose.GENERAL,
                        editSelectedDates = setOf(LocalDate.of(2026, 8, 1))),
                    onClearCalendarBatchMessage = { clears++; currentMessage = null },
                    onFinishCalendarEditMode = { cancellations++ },
                    onPreviousMonth = {}, onNextMonth = {}, onToday = {},
                    onSelectDate = {}, onDismissDate = {}, onRetry = {},
                )
            }
        }
        compose.onNodeWithTag("calendar-header-notice").performTouchInput { click() }
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.waitUntil(4_000) { clears == 1 }
        compose.onNodeWithTag("calendar-header-notice").assertDoesNotExist()
        compose.onNodeWithText(message).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(message).assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("calendar-notice-details").assertDoesNotExist()
        compose.onNodeWithTag("calendar-selection-cancel").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, cancellations); assertEquals(1, clears) }
        compose.onNodeWithTag("main-menu-button").performTouchInput { click() }
        compose.onNodeWithTag("main-navigation-drawer").assertIsDisplayed()
    }

    @Test
    fun confirmationConsumesExplicitScaffoldInsetsExactlyOnce() {
        var density = 1f
        compose.setContent {
            AndroidFontScaleTheme {
                density = LocalDensity.current.density
                Scaffold(
                    modifier = Modifier.testTag("scaffold"),
                    contentWindowInsets = WindowInsets(left = 20.dp, top = 24.dp, right = 28.dp, bottom = 32.dp),
                    topBar = { Box(Modifier.fillMaxWidth().height(64.dp).testTag("toolbar")) },
                ) { padding ->
                    TransientConfirmation(
                        "Aviso ficticio", {},
                        Modifier.padding(padding).consumeWindowInsets(padding),
                    ) { Box(Modifier.fillMaxSize().testTag("content")) }
                }
            }
        }
        val root = compose.onNodeWithTag("scaffold").fetchSemanticsNode().boundsInRoot
        val toolbar = compose.onNodeWithTag("toolbar").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        val notice = compose.onNodeWithTag("transient-confirmation").fetchSemanticsNode().boundsInRoot
        assertEquals(toolbar.bottom, content.top, 1f)
        assertEquals(root.left + 20 * density, content.left, 1f)
        assertEquals(root.right - 28 * density, content.right, 1f)
        assertEquals(root.bottom - 32 * density, content.bottom, 1f)
        assertEquals(content.top + 8 * density, notice.top, 1f)
        assertEquals(content.left + 16 * density, notice.left, 1f)
        assertEquals(content.right - 16 * density, notice.right, 1f)
    }

    private fun assertWholeWords(label: String, scroll: Boolean = true) {
        val node = compose.onNodeWithText(label, useUnmergedTree = true)
        if (scroll) node.performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        node.assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("No layout: $label", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse(label, layout.multiParagraph.didExceedMaxLines)
            assertEquals(label, label.length, layout.getLineEnd(layout.lineCount - 1))
            for (line in 0 until layout.lineCount) {
                assertFalse(label, layout.isLineEllipsized(line))
                val end = layout.getLineEnd(line, visibleEnd = true)
                if (end > 0 && end < label.length) {
                    assertTrue("Word split at $end: $label", label[end].isWhitespace() || label[end - 1].isWhitespace())
                }
            }
        }
    }
}
