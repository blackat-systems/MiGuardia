package com.blackatsystems.miguardia

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.work.MissingWorkSetupRequirement
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.help.HelpActions
import com.blackatsystems.miguardia.ui.help.HelpNavigationEvent
import com.blackatsystems.miguardia.ui.help.HelpNavigationTarget
import com.blackatsystems.miguardia.ui.help.HelpAnchor
import com.blackatsystems.miguardia.ui.help.HelpAnchorRegistry
import com.blackatsystems.miguardia.ui.help.HelpTourOverlay
import com.blackatsystems.miguardia.ui.help.helpAnchor
import com.blackatsystems.miguardia.ui.help.HelpDecisionScreen
import com.blackatsystems.miguardia.ui.help.HelpReadState
import com.blackatsystems.miguardia.ui.help.HelpScreen
import com.blackatsystems.miguardia.ui.help.HelpSession
import com.blackatsystems.miguardia.ui.help.HelpSessionMode
import com.blackatsystems.miguardia.ui.help.HelpSessionStage
import com.blackatsystems.miguardia.ui.help.HelpTourStep
import com.blackatsystems.miguardia.ui.help.HelpUiState
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import com.blackatsystems.miguardia.ui.worksetup.previewV2WorkSetupUiState
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class HelpComposeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun automaticIntroductionHasProgressBackNextAndSkip() {
        var back = 0
        var next = 0
        var skip = 0
        compose.setContent {
            AndroidFontScaleTheme {
                HelpDecisionScreen(
                    state = introduction(HelpSessionMode.AUTOMATIC),
                    actions = HelpActions(
                        back = { back++ },
                        next = { next++ },
                        requestExit = { skip++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Primeros pasos · 1 de 3").assertIsDisplayed()
        compose.onNodeWithText("Organizá tu trabajo").assertIsDisplayed()
        compose.onNodeWithText("Atrás").performClick()
        compose.onNodeWithText("Siguiente").performClick()
        compose.onNodeWithText("Omitir guía").performClick()
        compose.runOnIdle {
            assertEquals(1, back)
            assertEquals(1, next)
            assertEquals(1, skip)
        }
    }

    @Test
    fun replayIntroductionNeverOffersSkipAndClosesToHelp() {
        var closed = false
        compose.setContent {
            AndroidFontScaleTheme {
                HelpDecisionScreen(
                    state = introduction(HelpSessionMode.REPLAY),
                    actions = HelpActions(requestExit = { closed = true }),
                )
            }
        }

        compose.onNodeWithText("Omitir guía").assertDoesNotExist()
        compose.onNodeWithText("Cerrar y volver a Ayuda").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test
    fun V2ReadyPendingBlocksCalendarButCompletionKeepsItsRealActions() {
        val pending = introduction(HelpSessionMode.AUTOMATIC)
        var workSetup by androidx.compose.runtime.mutableStateOf(previewV2WorkSetupUiState())
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = calendarState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    workSetupState = workSetup,
                    helpState = pending,
                )
            }
        }

        compose.onNodeWithTag("help-introduction-1").assertIsDisplayed()
        compose.onNodeWithTag("next-event-card").assertDoesNotExist()

        compose.runOnIdle { workSetup = workSetup.copy(surface = WorkSetupSurface.COMPLETION) }
        compose.onNodeWithTag("work-setup-completion").assertIsDisplayed()
        compose.onNodeWithText("Agregar otro horario").assertIsDisplayed()
        compose.onNodeWithText("Agregar otro lugar").assertIsDisplayed()
        compose.onNodeWithText("Volver al Calendario").assertIsDisplayed()
    }

    @Test
    fun HelpLivesOnceInApplicationAndAllTopicsAreReachable() {
        var repeated = false
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = calendarState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    helpActions = HelpActions(startReplay = { repeated = true }),
                )
            }
        }

        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onAllNodes(
            hasText("Ayuda") and hasAnyAncestor(hasTestTag("main-navigation-drawer")),
        ).assertCountEquals(1)
        compose.onNodeWithTag("main-destination-help").performScrollTo().performClick()
        HELP_TOPICS.forEachIndexed { index, title ->
            compose.onNodeWithTag("help-topic-$index").performScrollTo().assertTextContains(title).assertIsDisplayed()
        }
        compose.onNodeWithTag("help-repeat-tour").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(repeated) }
    }

    @Test
    fun firstWorkSetHelpExplainsNextStepAndDisablesReplay() {
        val preview = previewV2WorkSetupUiState()
        val ready = preview.rootState as WorkSetupState.V2Ready
        val needs = preview.copy(
            rootState = WorkSetupState.V2NeedsFirstSet(
                ready.timelineId,
                ready.configurationRevision,
                setOf(MissingWorkSetupRequirement.ACTIVE_WORK_PLACE),
            ),
        )
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = calendarState(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    workSetupState = needs,
                )
            }
        }

        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithTag("main-destination-help").performScrollTo().performClick()
        compose.onNodeWithText("Primero terminá tu primer lugar y horario", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("help-repeat-tour").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun HelpScreenDoesNotInvokeReplayWhenDisabled() {
        var replayed = false
        compose.setContent {
            AndroidFontScaleTheme {
                HelpScreen(
                    contentPadding = PaddingValues(0.dp),
                    canRepeat = false,
                    actions = HelpActions(startReplay = { replayed = true }),
                )
            }
        }
        compose.onNodeWithTag("help-repeat-tour").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertFalse(replayed) }
    }

    @Test
    fun contextualTourVisitsAllRealSurfacesWithoutCallingMutationCallbacks() {
        var state by androidx.compose.runtime.mutableStateOf(tourState(0))
        var finished = false
        var mutationCallbacks = 0
        val actions = HelpActions(
            next = {
                val session = requireNotNull(state.session)
                state = state.copy(session = session.copy(stepIndex = session.stepIndex + 1))
            },
            finish = { finished = true },
        )
        compose.setContent {
            AndroidFontScaleTheme {
                MiGuardiaApp(
                    calendarState = calendarState(),
                    onPreviousMonth = { mutationCallbacks++ },
                    onNextMonth = { mutationCallbacks++ },
                    onToday = { mutationCallbacks++ },
                    onSelectDate = { mutationCallbacks++ },
                    onDismissDate = {},
                    onRetry = {},
                    helpState = state,
                    helpActions = actions,
                )
            }
        }

        HelpTourStep.entries.forEachIndexed { index, step ->
            compose.onNodeWithTag("help-tour-${step.name.lowercase()}").assertIsDisplayed()
            try {
                compose.onNodeWithTag("help-tour-anchor").assertIsDisplayed()
            } catch (error: AssertionError) {
                throw AssertionError("Tour step ${step.name}: ${error.message}", error)
            }
            compose.onNodeWithTag("main-menu-button").assertDoesNotExist()
            compose.onNodeWithTag("help-tour-next").performScrollTo().performClick()
            if (index < HelpTourStep.entries.lastIndex) compose.waitForIdle()
        }
        compose.runOnIdle {
            assertTrue(finished)
            assertEquals(0, mutationCallbacks)
        }
    }

    @Test
    fun drawerTourTargetsRemainVisibleInPortraitAtEveryFontScaleAndTheme() =
        drawerTourTargetsRemainVisible(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)

    @Test
    fun drawerTourTargetsRemainVisibleInLandscapeAtEveryFontScaleAndTheme() =
        drawerTourTargetsRemainVisible(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)

    private fun drawerTourTargetsRemainVisible(requestedOrientation: Int, expectedOrientation: Int) {
        val originalOrientation = compose.activity.requestedOrientation
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            compose.activity.requestedOrientation = requestedOrientation
        }
        compose.waitUntil(30_000) { compose.activity.resources.configuration.orientation == expectedOrientation }
        var fontScale by androidx.compose.runtime.mutableStateOf(TestFontScale.STANDARD)
        var dark by androidx.compose.runtime.mutableStateOf(false)
        fun replay(step: HelpTourStep) = tourState(step.ordinal).let {
            it.copy(readState = HelpReadState.Ready(1), session = it.session!!.copy(mode = HelpSessionMode.REPLAY))
        }
        var state by androidx.compose.runtime.mutableStateOf(replay(HelpTourStep.HELP))
        var mutations = 0
        var closed = 0
        val restoration = StateRestorationTester(compose)
        try {
            restoration.setContent {
                    AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                        MiGuardiaApp(
                            calendarState = calendarState(),
                            onPreviousMonth = { mutations++ }, onNextMonth = { mutations++ },
                            onToday = { mutations++ }, onSelectDate = { mutations++ },
                            onDismissDate = {}, onRetry = {},
                            workSetupState = previewV2WorkSetupUiState(),
                            helpState = state,
                            helpActions = HelpActions(
                                back = {
                                    val session = requireNotNull(state.session)
                                    state = state.copy(session = session.copy(stepIndex = session.stepIndex - 1))
                                },
                                requestExit = {
                                    closed++
                                    state = HelpUiState.completedForPreview().copy(
                                        navigationEvent = HelpNavigationEvent(closed.toLong(), HelpNavigationTarget.HELP),
                                    )
                                },
                                consumeNavigation = { state = state.copy(navigationEvent = null) },
                            ),
                        )
                    }
            }
            listOf(false, true).forEach { theme -> TestFontScale.entries.forEach { size ->
                compose.runOnIdle { state = replay(HelpTourStep.HELP) }
                assertTourAnchor(HelpTourStep.HELP)
                compose.runOnIdle { dark = theme; fontScale = size }
                compose.waitForIdle()
                assertTourAnchor(HelpTourStep.HELP)
                // Compose save/restore is explicit here; this is not process-death QA.
                restoration.emulateSavedInstanceStateRestore()
                assertTourAnchor(HelpTourStep.HELP)
                compose.onNodeWithTag("help-replay-close").performScrollTo().performClick()
                compose.onNodeWithTag("help-tour-help").assertDoesNotExist()
                compose.onNodeWithTag("help-screen").assertIsDisplayed()
                compose.runOnIdle { state = replay(HelpTourStep.HELP) }
                assertTourAnchor(HelpTourStep.HELP)
                // Back must reveal earlier menu items after HELP scrolled to the bottom.
                listOf(HelpTourStep.SUMMARY, HelpTourStep.LOAD_AND_REPEAT, HelpTourStep.PHOTOS).forEach { step ->
                    compose.onNodeWithTag("help-tour-back").performScrollTo().performClick()
                    assertTourAnchor(step)
                }
                compose.runOnIdle { state = replay(HelpTourStep.MENU) }
                assertTourAnchor(HelpTourStep.MENU)
            } }
            compose.runOnIdle { assertEquals(0, mutations); assertEquals(6, closed) }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                compose.activity.requestedOrientation = originalOrientation
            }
        }
    }

    private fun assertTourAnchor(step: HelpTourStep) {
        compose.waitForIdle()
        compose.onNodeWithTag("help-tour-${step.name.lowercase()}").assertIsDisplayed()
        compose.onNodeWithTag("help-tour-anchor").assertIsDisplayed()
        compose.onNodeWithText("Este control no está disponible", substring = true).assertDoesNotExist()
        if (step in listOf(HelpTourStep.HELP, HelpTourStep.LOAD_AND_REPEAT, HelpTourStep.PHOTOS)) {
            val tag = when (step) {
                HelpTourStep.HELP -> "main-destination-help"
                HelpTourStep.LOAD_AND_REPEAT -> "drawer-action-calendar-load"
                else -> "drawer-action-calendar-photos"
            }
            val realControl = compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
            val viewport = compose.onNodeWithTag("main-navigation-drawer", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val bounds = realControl.boundsInRoot
            assertTrue("$step control is clipped", bounds.width + 1 >= realControl.size.width && bounds.height + 1 >= realControl.size.height)
            assertTrue("$step control lies outside its drawer", bounds.left >= viewport.left - 1 &&
                bounds.top >= viewport.top - 1 && bounds.right <= viewport.right + 1 && bounds.bottom <= viewport.bottom + 1)
            val anchor = compose.onNodeWithTag("help-tour-anchor").fetchSemanticsNode().boundsInRoot
            val explanation = compose.onNodeWithTag("help-tour-explanation").fetchSemanticsNode().boundsInRoot
            assertTrue("Explanation covers the actual $step control", !anchor.overlaps(explanation))
        }
        if (step == HelpTourStep.MENU) {
            compose.onNodeWithTag("main-menu-button", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("main-navigation-drawer", useUnmergedTree = true).assertIsNotDisplayed()
        }
    }

    @Test
    fun helpTopicsDescribeCurrentMenuAndTheAdvancedSettingsEntry() {
        compose.setContent {
            AndroidFontScaleTheme {
                HelpScreen(PaddingValues(0.dp), canRepeat = true, actions = HelpActions())
            }
        }
        compose.onNodeWithTag("help-topic-1").performScrollTo().performClick()
        compose.onNodeWithText("Acciones del Calendario", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("help-topic-2").performScrollTo().performClick()
        compose.onNodeWithText("Tu trabajo > Mi forma de trabajar > Opciones avanzadas", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("help-topic-3").performScrollTo().performClick()
        compose.onNodeWithText("Si falta altura, se reserva el espacio para el mes completo.", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("help-topic-4").performScrollTo().performClick()
        compose.onNodeWithText("Menú > Avisos y contexto", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun removedAnchorCannotHighlightAnOldControl() {
        val anchors = HelpAnchorRegistry()
        var showAnchor by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            if (showAnchor) Box(Modifier.size(60.dp).helpAnchor(anchors, HelpAnchor.TODAY_CARD))
        }
        compose.runOnIdle {
            assertNotNull(anchors.bounds(HelpAnchor.TODAY_CARD))
            showAnchor = false
        }
        compose.runOnIdle { assertNull(anchors.bounds(HelpAnchor.TODAY_CARD)) }
    }

    @Test
    fun nextEventTourExplainsTheUnavailableCardWithoutInventingAnAnchor() {
        compose.setContent {
            AndroidFontScaleTheme {
                HelpTourOverlay(
                    state = tourState(HelpTourStep.TODAY_CARD.ordinal),
                    actions = HelpActions(),
                    anchors = HelpAnchorRegistry(),
                )
            }
        }
        compose.onNodeWithTag("help-tour-anchor").assertDoesNotExist()
        compose.onNodeWithText("Si falta altura, se reserva el espacio para el mes completo.", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Este control no está disponible", substring = true).performScrollTo().assertIsDisplayed()
    }

    private fun introduction(mode: HelpSessionMode): HelpUiState = HelpUiState(
        readState = HelpReadState.Ready(if (mode == HelpSessionMode.REPLAY) 1 else 0),
        session = HelpSession(1L, mode, HelpSessionStage.INTRODUCTION, 0),
        workSetupResolved = true,
        rootIsV2Ready = true,
    )

    private fun tourState(step: Int): HelpUiState = HelpUiState(
        readState = HelpReadState.Ready(0),
        session = HelpSession(7L, HelpSessionMode.AUTOMATIC, HelpSessionStage.TOUR, step),
        workSetupResolved = true,
        rootIsV2Ready = true,
    )

    private fun calendarState(): CalendarUiState {
        val month = YearMonth.of(2026, 9)
        val now = Instant.parse("2026-09-02T12:00:00Z")
        return CalendarUiState(
            visibleMonth = month,
            referenceInstant = now,
            days = projectCalendarMonth(
                month = month,
                shifts = emptyList(),
                explicitDayStatuses = emptyList(),
                medicalLeaves = emptyList(),
                now = now,
            ),
            loadState = CalendarLoadState.CONTENT,
        )
    }

    private companion object {
        val HELP_TOPICS = listOf(
            "Primeros pasos y Mi forma de trabajar",
            "Calendario, jornadas, feriados, vacaciones, notas y Fotos",
            "Horario real, horas extra y disponibilidad",
            "Horas, Resumen y próximo evento",
            "Notificaciones, Clima y Widget",
            "Informes locales",
            "Copias y restauración",
            "Bloqueo de acceso y privacidad",
            "Apariencia",
        )
    }
}
