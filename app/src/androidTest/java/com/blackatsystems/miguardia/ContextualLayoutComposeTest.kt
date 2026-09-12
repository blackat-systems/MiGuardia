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
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.components.AdvancedOptionsSection
import com.blackatsystems.miguardia.ui.components.ContextHelp
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupActions
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupStep
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurfaceHost
import com.blackatsystems.miguardia.ui.worksetup.WorkTemplateDraft
import com.blackatsystems.miguardia.ui.worksetup.previewV2WorkSetupUiState
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pure presentation fixtures: no Room, beta activation, preferences or device settings. */
class ContextualLayoutComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun advancedLabelsKeepWholeWordsAndHelpAtEveryInternalZoom() {
        var zoom by mutableStateOf(AppZoom.STANDARD)
        var dark by mutableStateOf(false)
        compose.setContent {
            // Constrain the physical composition before applying the app's internal zoom.
            Box(Modifier.width(360.dp)) {
                MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        AdvancedOptionsSection(
                            help = ContextHelp("Prueba", "Qué hace ficticio", "Uso ficticio", "Ejemplo ficticio"),
                        ) { Text("Contenido avanzado ficticio") }
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (scale in AppZoom.entries) {
            compose.runOnIdle { dark = theme; zoom = scale }
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
        var zoom by mutableStateOf(AppZoom.STANDARD)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            Box(Modifier.width(360.dp)) {
                MiGuardiaTheme(appZoom = zoom) {
                    AdvancedOptionsSection(help = ContextHelp("Prueba", "Qué hace ficticio", "Uso ficticio", "Ejemplo ficticio")) { }
                }
            }
        }
        compose.onNodeWithContentDescription("Ayuda sobre Prueba").performClick()
        compose.onNodeWithTag("context-help-dialog").assertIsDisplayed()
        compose.runOnIdle { zoom = AppZoom.EXTRA_LARGE }
        compose.onNodeWithTag("context-help-dialog").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("context-help-dialog").assertIsDisplayed()
        compose.runOnIdle { zoom = AppZoom.STANDARD }
        compose.onNodeWithTag("context-help-dialog").assertIsDisplayed()
        compose.onNodeWithText("Entendido").performClick()
        compose.onNodeWithTag("context-help-dialog").assertDoesNotExist()
    }

    @Test
    fun bothTemplateFormsKeepColorLabelsWholeAndPickerReachable() {
        var zoom by mutableStateOf(AppZoom.STANDARD)
        var dark by mutableStateOf(false)
        var state by mutableStateOf(previewV2WorkSetupUiState())
        compose.setContent {
            Box(Modifier.width(360.dp)) {
                MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                    WorkSetupSurfaceHost(state, WorkSetupActions())
                }
            }
        }
        for (surface in listOf(WorkSetupSurface.FIRST_WORK_SET, WorkSetupSurface.ADDITIONAL_TEMPLATE)) {
            for (theme in listOf(false, true)) for (scale in AppZoom.entries) {
                for (color in listOf(null, 0xFF1565C0.toInt())) {
                    compose.runOnIdle {
                        dark = theme
                        zoom = scale
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
    fun calendarConfirmationStartsBelowTheRealToolbarWithoutDoublePadding() {
        var zoom by mutableStateOf(AppZoom.STANDARD)
        var dark by mutableStateOf(false)
        var density = 1f
        val message = "El horario quedó seleccionado. Revisá la carga y confirmá las jornadas cuando quieras."
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                density = LocalDensity.current.density
                MiGuardiaApp(
                    modifier = Modifier.testTag("app-layout"),
                    calendarState = CalendarUiState(
                        visibleMonth = YearMonth.of(2026, 9),
                        referenceInstant = Instant.parse("2026-09-07T12:00:00Z"),
                        loadState = CalendarLoadState.CONTENT,
                    ),
                    v2ManualShiftLoadState = V2ManualShiftLoadUiState(infoMessage = message),
                    appZoom = zoom,
                    onPreviousMonth = {}, onNextMonth = {}, onToday = {},
                    onSelectDate = {}, onDismissDate = {}, onRetry = {},
                )
            }
        }
        for (theme in listOf(false, true)) for (scale in AppZoom.entries) {
            compose.runOnIdle { dark = theme; zoom = scale }
            val toolbar = compose.onNodeWithTag("main-top-bar").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("calendar-scroll-viewport").fetchSemanticsNode().boundsInRoot
            val notice = compose.onNodeWithTag("transient-confirmation").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val root = compose.onNodeWithTag("app-layout").fetchSemanticsNode().boundsInRoot
            assertEquals(toolbar.bottom, viewport.top, 1f)
            assertEquals(toolbar.bottom + 8 * density, notice.top, 1f)
            assertTrue(notice.left >= viewport.left && notice.right <= viewport.right)
            assertTrue(notice.bottom <= root.bottom && notice.bottom <= viewport.bottom)
            assertWholeWords(message, scroll = false)
        }
    }

    @Test
    fun confirmationConsumesExplicitScaffoldInsetsExactlyOnce() {
        var density = 1f
        compose.setContent {
            MiGuardiaTheme {
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
