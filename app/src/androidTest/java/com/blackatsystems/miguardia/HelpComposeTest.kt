package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.work.MissingWorkSetupRequirement
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.help.HelpActions
import com.blackatsystems.miguardia.ui.help.HelpDecisionScreen
import com.blackatsystems.miguardia.ui.help.HelpReadState
import com.blackatsystems.miguardia.ui.help.HelpScreen
import com.blackatsystems.miguardia.ui.help.HelpSession
import com.blackatsystems.miguardia.ui.help.HelpSessionMode
import com.blackatsystems.miguardia.ui.help.HelpSessionStage
import com.blackatsystems.miguardia.ui.help.HelpTourStep
import com.blackatsystems.miguardia.ui.help.HelpUiState
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import com.blackatsystems.miguardia.ui.worksetup.previewV2WorkSetupUiState
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HelpComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun automaticIntroductionHasProgressBackNextAndSkip() {
        var back = 0
        var next = 0
        var skip = 0
        compose.setContent {
            MiGuardiaTheme {
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
            MiGuardiaTheme {
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
            MiGuardiaTheme {
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
            MiGuardiaTheme {
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
            MiGuardiaTheme {
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
            MiGuardiaTheme {
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
            MiGuardiaTheme {
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
            compose.onNodeWithTag("help-tour-anchor").assertIsDisplayed()
            compose.onNodeWithTag("main-menu-button").assertDoesNotExist()
            compose.onNodeWithTag("help-tour-next").performScrollTo().performClick()
            if (index < HelpTourStep.entries.lastIndex) compose.waitForIdle()
        }
        compose.runOnIdle {
            assertTrue(finished)
            assertEquals(0, mutationCallbacks)
        }
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
            "Horas, Resumen y tarjeta de hoy",
            "Notificaciones, Clima y Widget",
            "Informes locales",
            "Copias y restauración",
            "Bloqueo de acceso y privacidad",
            "Apariencia y zoom interno",
        )
    }
}
