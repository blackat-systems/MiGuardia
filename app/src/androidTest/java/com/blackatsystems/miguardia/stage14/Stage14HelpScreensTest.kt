package com.blackatsystems.miguardia.stage14

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.help.HelpActions
import com.blackatsystems.miguardia.ui.help.HelpAnchor
import com.blackatsystems.miguardia.ui.help.HelpAnchorRegistry
import com.blackatsystems.miguardia.ui.help.HelpDecisionScreen
import com.blackatsystems.miguardia.ui.help.HelpReadState
import com.blackatsystems.miguardia.ui.help.HelpScreen
import com.blackatsystems.miguardia.ui.help.HelpSession
import com.blackatsystems.miguardia.ui.help.HelpSessionMode
import com.blackatsystems.miguardia.ui.help.HelpSessionStage
import com.blackatsystems.miguardia.ui.help.HelpTourOverlay
import com.blackatsystems.miguardia.ui.help.HelpTourStep
import com.blackatsystems.miguardia.ui.help.HelpUiState
import com.blackatsystems.miguardia.ui.help.helpAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Etapa 14 · Ayuda. Screen tests with in-memory state and actions: no view model, no onboarding
 * preference, no MainActivity, no database. Text size is simulated; only this test's own
 * ComponentActivity is rotated, and its orientation is restored.
 */
class Stage14HelpScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // A266
    @Test
    fun eachOfTheNineTopicsOpensShowsItsContentAndCollapsesAgain() {
        compose.setContent {
            AndroidFontScaleTheme {
                HelpScreen(PaddingValues(0.dp), canRepeat = true, actions = HelpActions())
            }
        }
        assertEquals(9, TOPICS.size)
        compose.onNodeWithTag("help-topic-${TOPICS.size}").assertDoesNotExist()
        TOPICS.forEachIndexed { index, topic ->
            val tag = "help-topic-$index"
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                .assertTextContains(topic.title)
                .assert(stateIs("Contraído"))
            assertEquals("Leer tema", clickLabel(tag))
            body(topic).assertDoesNotExist()

            compose.onNodeWithTag(tag).performClick()
            compose.onNodeWithTag(tag).assert(stateIs("Expandido"))
                .assertTextContains(topic.title)
                .assertTextContains(topic.bodyStart, substring = true)
            assertEquals("Contraer tema", clickLabel(tag))
            body(topic).performScrollTo().assertIsDisplayed()

            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.onNodeWithTag(tag).assert(stateIs("Contraído"))
            assertEquals("Leer tema", clickLabel(tag))
            body(topic).assertDoesNotExist()
        }
    }

    // A266 (borde): opening another topic closes the one that was open.
    @Test
    fun openingAnotherTopicCollapsesThePreviousOne() {
        compose.setContent {
            AndroidFontScaleTheme {
                HelpScreen(PaddingValues(0.dp), canRepeat = true, actions = HelpActions())
            }
        }
        compose.onNodeWithTag("help-topic-0").performScrollTo().performClick()
        body(TOPICS[0]).assertExists()
        compose.onNodeWithTag("help-topic-8").performScrollTo().performClick()
        compose.onNodeWithTag("help-topic-8").assert(stateIs("Expandido"))
        body(TOPICS[8]).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("help-topic-0").assert(stateIs("Contraído"))
        body(TOPICS[0]).assertDoesNotExist()
    }

    // A266 + criterio visual de tema y letra en la lista de Ayuda
    @Test
    fun topicListKeepsTitlesWholeAndRepeatTourReachableAtExtraLargeFontInBothThemes() {
        var dark by mutableStateOf(false)
        var replays = 0
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark, fontScale = TestFontScale.EXTRA_LARGE) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    HelpScreen(PaddingValues(0.dp), canRepeat = true, actions = HelpActions(startReplay = { replays++ }))
                }
            }
        }
        val problems = mutableListOf<String>()
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            val context = "${themeName(theme)} 200%"
            listOf("Ayuda", "Consultá una función sin mostrar ni cambiar tus datos.").forEach { text ->
                val node = compose.onNode(hasText(text), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                problems += node.stage14TextLayout().stage14WholeTextProblems().map { "$context: $it" }
            }
            TOPICS.forEachIndexed { index, topic ->
                val tag = "help-topic-$index"
                val card = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                    .assert(stateIs("Contraído")).fetchSemanticsNode()
                val title = compose.onNode(hasText(topic.title) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                    .assertIsDisplayed()
                problems += (title.fetchSemanticsNode().stage14InsideProblems(card, "title «${topic.title}»") +
                    title.stage14TextLayout().stage14WholeTextProblems()).map { "$context: $it" }

                // The content must stay whole too once the topic is open.
                compose.onNodeWithTag(tag).performClick()
                val content = body(topic).performScrollTo().assertIsDisplayed()
                problems += content.stage14TextLayout().stage14WholeTextProblems().map { "$context tema ${index + 1}: $it" }
                compose.onNodeWithTag(tag).performScrollTo().performClick()
                compose.onNodeWithTag(tag).assert(stateIs("Contraído"))
            }
            problems += compose.stage14ButtonLabelProblems("help-repeat-tour", "Repetir recorrido inicial").map { "$context: $it" }
            compose.onNodeWithTag("help-repeat-tour").performScrollTo().assertIsEnabled().performClick()
            val hint = compose.onNode(hasText("La repetición no borra ni modifica tu progreso."), useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed()
            problems += hint.stage14TextLayout().stage14WholeTextProblems().map { "$context: $it" }
        }
        compose.runOnIdle { assertEquals(2, replays) }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // A267 + criterio visual de tema y letra en la introducción (fila «Atrás / Siguiente»)
    @Test
    fun introductionRowKeepsBothLabelsWholeInPortraitAtEveryFontScaleAndTheme() =
        introductionRowKeepsBothLabelsWhole(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)

    @Test
    fun introductionRowKeepsBothLabelsWholeInLandscapeAtEveryFontScaleAndTheme() =
        introductionRowKeepsBothLabelsWhole(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)

    private fun introductionRowKeepsBothLabelsWhole(requestedOrientation: Int, expectedOrientation: Int) =
        inOrientation(requestedOrientation, expectedOrientation) {
            var dark by mutableStateOf(false)
            var fontScale by mutableStateOf(TestFontScale.STANDARD)
            var state by mutableStateOf(introduction(0))
            var tourRequests = 0
            var backAtFirstStep = 0
            // Fictitious stand-in for the view model: only moves the step in memory.
            val actions = HelpActions(
                next = {
                    val session = requireNotNull(state.session)
                    if (session.stepIndex < INTRODUCTION_STEPS - 1) {
                        state = state.copy(session = session.copy(stepIndex = session.stepIndex + 1))
                    } else {
                        tourRequests++
                    }
                },
                back = {
                    val session = requireNotNull(state.session)
                    if (session.stepIndex > 0) {
                        state = state.copy(session = session.copy(stepIndex = session.stepIndex - 1))
                    } else {
                        backAtFirstStep++
                    }
                },
            )
            compose.setContent {
                AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                    HelpDecisionScreen(state = state, actions = actions)
                }
            }
            val minTouchPx = with(compose.density) { 48.dp.toPx() }
            val problems = mutableListOf<String>()
            var combinations = 0
            for (theme in listOf(false, true)) for (scale in TestFontScale.entries) {
                combinations++
                compose.runOnIdle {
                    dark = theme
                    fontScale = scale
                    state = introduction(0)
                }
                for (step in 0 until INTRODUCTION_STEPS) {
                    val context = "${themeName(theme)} ${scale.percent}% ${orientationName(expectedOrientation)} paso ${step + 1}"
                    compose.onNodeWithTag("help-introduction-${step + 1}").assertIsDisplayed()
                    compose.onNodeWithText("Primeros pasos · ${step + 1} de $INTRODUCTION_STEPS").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText(INTRODUCTION_TITLES[step]).performScrollTo().assertIsDisplayed()
                    val nextLabel = if (step == INTRODUCTION_STEPS - 1) "Ver recorrido" else "Siguiente"
                    problems += compose.stage14ButtonRowProblems(
                        startTag = "help-introduction-back",
                        startLabel = "Atrás",
                        endTag = "help-introduction-next",
                        endLabel = nextLabel,
                        minTouchPx = minTouchPx,
                    ).map { "$context: $it" }
                    problems += compose.stage14ButtonLabelProblems("help-skip", "Omitir guía").map { "$context: $it" }
                    compose.onNodeWithTag("help-introduction-next").performScrollTo().assertIsEnabled().performClick()
                }
                // «Ver recorrido» on the last step asked for the tour once and did not move the step.
                compose.runOnIdle {
                    assertEquals(combinations, tourRequests)
                    assertEquals(INTRODUCTION_STEPS - 1, requireNotNull(state.session).stepIndex)
                }
                for (step in INTRODUCTION_STEPS - 2 downTo 0) {
                    compose.onNodeWithTag("help-introduction-back").performScrollTo().assertIsEnabled().performClick()
                    compose.onNodeWithTag("help-introduction-${step + 1}").assertIsDisplayed()
                    compose.onNodeWithText(INTRODUCTION_TITLES[step]).performScrollTo().assertIsDisplayed()
                }
                compose.onNodeWithTag("help-introduction-back").performScrollTo().performClick()
                compose.runOnIdle { assertEquals(combinations, backAtFirstStep) }
                compose.onNodeWithTag("help-introduction-1").assertIsDisplayed()
            }
            assertTrue(problems.joinToString("\n"), problems.isEmpty())
        }

    // A268 + criterio visual de tema y letra en los pasos del recorrido sin prueba de letra grande
    @Test
    fun tourRowKeepsBothLabelsWholeOnCalendarStepsInPortraitAtEveryFontScaleAndTheme() =
        tourRowKeepsBothLabelsWhole(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)

    @Test
    fun tourRowKeepsBothLabelsWholeOnCalendarStepsInLandscapeAtEveryFontScaleAndTheme() =
        tourRowKeepsBothLabelsWhole(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)

    private fun tourRowKeepsBothLabelsWhole(requestedOrientation: Int, expectedOrientation: Int) =
        inOrientation(requestedOrientation, expectedOrientation) {
            var dark by mutableStateOf(false)
            var fontScale by mutableStateOf(TestFontScale.STANDARD)
            var state by mutableStateOf(tour(TOUR_STEPS.first()))
            var finishes = 0
            val actions = HelpActions(
                next = {
                    val session = requireNotNull(state.session)
                    state = state.copy(session = session.copy(stepIndex = session.stepIndex + 1))
                },
                back = {
                    val session = requireNotNull(state.session)
                    state = state.copy(session = session.copy(stepIndex = session.stepIndex - 1))
                },
                finish = { finishes++ },
            )
            val anchors = HelpAnchorRegistry()
            compose.setContent {
                AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        // Fictitious control standing in for the card, the grid and a day cell.
                        Box(
                            Modifier
                                .padding(16.dp)
                                .size(width = 160.dp, height = 48.dp)
                                .helpAnchor(anchors, HelpAnchor.TODAY_CARD)
                                .helpAnchor(anchors, HelpAnchor.MONTH_AND_GRID)
                                .helpAnchor(anchors, HelpAnchor.DAY_DETAIL),
                        )
                        HelpTourOverlay(state = state, actions = actions, anchors = anchors)
                    }
                }
            }
            val minTouchPx = with(compose.density) { 48.dp.toPx() }
            val problems = mutableListOf<String>()
            for (theme in listOf(false, true)) for (scale in TestFontScale.entries) {
                compose.runOnIdle {
                    dark = theme
                    fontScale = scale
                    state = tour(TOUR_STEPS.first())
                }
                TOUR_STEPS.forEachIndexed { index, step ->
                    val context = "${themeName(theme)} ${scale.percent}% ${orientationName(expectedOrientation)} ${step.name}"
                    assertTourStep(step)
                    problems += compose.stage14ButtonRowProblems(
                        startTag = "help-tour-back",
                        startLabel = "Atrás",
                        endTag = "help-tour-next",
                        endLabel = "Siguiente",
                        minTouchPx = minTouchPx,
                    ).map { "$context: $it" }
                    problems += compose.stage14ButtonLabelProblems("help-skip", "Omitir guía").map { "$context: $it" }
                    if (index < TOUR_STEPS.lastIndex) {
                        compose.onNodeWithTag("help-tour-next").performScrollTo().assertIsEnabled().performClick()
                    }
                }
                for (step in TOUR_STEPS.dropLast(1).reversed()) {
                    compose.onNodeWithTag("help-tour-back").performScrollTo().assertIsEnabled().performClick()
                    assertTourStep(step)
                }
            }
            compose.runOnIdle { assertEquals("«Siguiente» must not finish the guide on these steps", 0, finishes) }
            assertTrue(problems.joinToString("\n"), problems.isEmpty())
        }

    // A269, A270 (aspecto): diálogo «¿Omitir la guía?» con letra al 200%
    @Test
    fun skipGuideDialogKeepsBothButtonsWholeAtExtraLargeFontInBothThemes() =
        inOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT) {
            var dark by mutableStateOf(false)
            var confirmed = 0
            var dismissed = 0
            val state = introduction(0).copy(showExitConfirmation = true)
            compose.setContent {
                AndroidFontScaleTheme(darkTheme = dark, fontScale = TestFontScale.EXTRA_LARGE) {
                    HelpDecisionScreen(
                        state = state,
                        actions = HelpActions(
                            confirmExit = { confirmed++ },
                            dismissExitConfirmation = { dismissed++ },
                        ),
                    )
                }
            }
            val confirmLabel = "Omitir y abrir Calendario"
            val dismissLabel = "Seguir con la guía"
            val problems = mutableListOf<String>()
            for (theme in listOf(false, true)) {
                compose.runOnIdle { dark = theme }
                val context = "${themeName(theme)} 200% diálogo"
                listOf(
                    "¿Omitir la guía?",
                    "Podés repetirla más adelante desde Ayuda. Para salir, MiGuardia necesita guardar esta decisión.",
                ).forEach { text ->
                    val node = compose.onNode(hasText(text), useUnmergedTree = true).assertIsDisplayed()
                    problems += (node.fetchSemanticsNode().stage14ClipProblems("«$text»") +
                        node.stage14TextLayout().stage14WholeTextProblems()).map { "$context: $it" }
                }
                problems += compose.stage14ButtonLabelProblems("help-confirm-skip", confirmLabel, scroll = false)
                    .map { "$context: $it" }

                // The dismiss button has no test tag: the merged node with its text is the button.
                val dismissButton = compose.onNodeWithText(dismissLabel).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
                val dismissText = compose.onNode(hasText(dismissLabel), useUnmergedTree = true).assertIsDisplayed()
                problems += (dismissButton.stage14ClipProblems("button «$dismissLabel»") +
                    dismissText.fetchSemanticsNode().stage14ClipProblems("label «$dismissLabel»") +
                    dismissText.fetchSemanticsNode().stage14InsideProblems(dismissButton, "label «$dismissLabel»") +
                    dismissText.stage14TextLayout().stage14WholeTextProblems()).map { "$context: $it" }

                val confirmBounds = compose.onNodeWithTag("help-confirm-skip").fetchSemanticsNode().boundsInRoot
                if (confirmBounds.overlaps(dismissButton.boundsInRoot)) {
                    problems += "$context: «$confirmLabel» ($confirmBounds) and «$dismissLabel» (${dismissButton.boundsInRoot}) overlap"
                }

                compose.onNodeWithText(dismissLabel).performClick()
                compose.onNodeWithTag("help-confirm-skip").assertIsEnabled().performClick()
            }
            compose.runOnIdle {
                assertEquals(2, dismissed)
                assertEquals(2, confirmed)
            }
            assertTrue(problems.joinToString("\n"), problems.isEmpty())
        }

    private fun assertTourStep(step: HelpTourStep) {
        compose.onNodeWithTag("help-tour-${step.name.lowercase()}").assertIsDisplayed()
        compose.onNodeWithTag("help-tour-anchor").assertIsDisplayed()
        compose.onNodeWithText("Este control no está disponible", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Recorrido · ${step.ordinal + 1} de ${HelpTourStep.entries.size}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(TOUR_TITLES.getValue(step)).performScrollTo().assertIsDisplayed()
    }

    private fun body(topic: Topic) = compose.onNode(hasText(topic.bodyStart, substring = true), useUnmergedTree = true)

    private fun stateIs(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun clickLabel(tag: String): String? =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label

    /** Rotates only this test's own activity and puts its orientation back, whatever happens. */
    private fun inOrientation(requestedOrientation: Int, expectedOrientation: Int, block: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalOrientation = compose.activity.requestedOrientation
        instrumentation.runOnMainSync { compose.activity.requestedOrientation = requestedOrientation }
        try {
            compose.waitUntil(30_000) { compose.activity.resources.configuration.orientation == expectedOrientation }
            block()
        } finally {
            instrumentation.runOnMainSync { compose.activity.requestedOrientation = originalOrientation }
        }
    }

    private fun themeName(dark: Boolean) = if (dark) "Oscuro" else "Claro"

    private fun orientationName(orientation: Int) =
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) "horizontal" else "vertical"

    private fun introduction(step: Int): HelpUiState = HelpUiState(
        readState = HelpReadState.Ready(0),
        session = HelpSession(1L, HelpSessionMode.AUTOMATIC, HelpSessionStage.INTRODUCTION, step),
        workSetupResolved = true,
        rootIsV2Ready = true,
    )

    private fun tour(step: HelpTourStep): HelpUiState = HelpUiState(
        readState = HelpReadState.Ready(0),
        session = HelpSession(7L, HelpSessionMode.AUTOMATIC, HelpSessionStage.TOUR, step.ordinal),
        workSetupResolved = true,
        rootIsV2Ready = true,
    )

    private data class Topic(val title: String, val bodyStart: String)

    private companion object {
        // HelpUiState.kt: INTRODUCTION_STEP_COUNT.
        const val INTRODUCTION_STEPS = 3

        // HelpScreens.kt: IntroductionCopies.
        val INTRODUCTION_TITLES = listOf(
            "Organizá tu trabajo",
            "Entendé lo que viene y lo que hiciste",
            "Tus datos quedan bajo tu control",
        )

        // The three tour steps that had no large-text check (the drawer ones already have theirs).
        val TOUR_STEPS = listOf(HelpTourStep.TODAY_CARD, HelpTourStep.MONTH_AND_GRID, HelpTourStep.DAY_DETAIL)

        // HelpScreens.kt: TourCopies.
        val TOUR_TITLES = mapOf(
            HelpTourStep.TODAY_CARD to "Tu próximo evento",
            HelpTourStep.MONTH_AND_GRID to "Un mes, una sola grilla",
            HelpTourStep.DAY_DETAIL to "Detalle de cada día",
        )

        // HelpScreens.kt: HelpTopics, title and first sentence of each body.
        val TOPICS = listOf(
            Topic(
                "Primeros pasos y Mi forma de trabajar",
                "Definí un único rubro y luego tus lugares, tipos y horarios desde Menú > Tu trabajo > Mi forma de trabajar.",
            ),
            Topic(
                "Calendario, jornadas, feriados, vacaciones, notas y Fotos",
                "Un toque abre el día; una pulsación larga inicia la selección.",
            ),
            Topic(
                "Horario real, horas extra y disponibilidad",
                "Abrí el detalle de una jornada para registrar lo ocurrido.",
            ),
            Topic(
                "Horas, Resumen y próximo evento",
                "La tarjeta superior anticipa lo inmediato cuando hay espacio.",
            ),
            Topic(
                "Notificaciones, Clima y Widget",
                "Se abren desde Menú > Avisos y contexto.",
            ),
            Topic(
                "Informes locales",
                "En Resumen, abrí Más opciones > Generar informe para preparar un PDF o XLSX del mes.",
            ),
            Topic(
                "Copias y restauración",
                "Aplicación > Copias permite crear o recuperar una copia local.",
            ),
            Topic(
                "Bloqueo de acceso y privacidad",
                "Aplicación > Bloqueo protege la entrada usando la seguridad del dispositivo.",
            ),
            Topic(
                "Apariencia",
                "Aplicación > Apariencia ofrece tema claro, oscuro o del sistema.",
            ),
        )
    }
}
