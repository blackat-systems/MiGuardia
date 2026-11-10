package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.security.*
import com.blackatsystems.miguardia.support.SupportMenuAction
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.help.HelpUiState
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import com.blackatsystems.miguardia.ui.worksetup.previewV2WorkSetupUiState
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** In-memory Compose surfaces only. Never seeds or changes the installed QA session. */
class SupportMenuComposeTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2026, 11)
    private var work by mutableStateOf(previewV2WorkSetupUiState())
    private var help by mutableStateOf(HelpUiState.completedForPreview())
    private var lock by mutableStateOf(AccessLockState(
        phase = AccessLockPhase.READY, configuration = AccessLockConfiguration(),
        locked = false, privacyCoverVisible = false,
    ))
    private var opens = 0
    private var monthChanges = 0

    @Test fun oneVisibleAccessibleActionBelongsToApplication() {
        compose.setContent { App() }
        openMenu()
        compose.onAllNodesWithTag(SUPPORT).assertCountEquals(1)
        compose.onNodeWithTag(SUPPORT).performScrollTo().assertIsDisplayed().assertHasClickAction()
            .assertTextContains("Apoyar MiGuardia")
            .assertTextContains("Aporte voluntario en el navegador")
        val nodes = compose.onNodeWithTag("main-navigation-drawer").fetchSemanticsNode().children
        fun flatten(node: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> =
            listOf(node) + node.children.flatMap(::flatten)
        val ordered = nodes.flatMap(::flatten)
        val application = ordered.indexOfFirst { hasText("APLICACIÓN").matches(it) }
        val support = ordered.indexOfFirst { hasTestTag(SUPPORT).matches(it) }
        assertTrue(application >= 0 && support > application)
        compose.runOnIdle { assertEquals(0, opens) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun realDrawerFinishesItsAnimationBeforeCallbackAndIgnoresSecondTap() {
        lateinit var drawer: DrawerState
        lateinit var scope: CoroutineScope
        val action = SupportMenuAction()
        val observations = mutableListOf<Triple<DrawerValue, DrawerValue, Boolean>>()
        compose.setContent {
            AndroidFontScaleTheme {
                drawer = rememberDrawerState(DrawerValue.Open)
                scope = rememberCoroutineScope()
                ModalNavigationDrawer(drawerState = drawer, drawerContent = {
                    ModalDrawerSheet { Text("Apoyar MiGuardia") }
                }) { Text("Calendario") }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            repeat(2) {
                scope.launch {
                    action.open({ drawer.close() }, { true }) {
                        observations += Triple(drawer.currentValue, drawer.targetValue, drawer.isAnimationRunning)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { assertTrue(observations.isEmpty()); assertTrue(drawer.isOpen) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnUiThread { assertTrue(observations.isEmpty()) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(Triple(DrawerValue.Closed, DrawerValue.Closed, false)), observations) }
    }

    @Test fun actualMenuDoubleTapClosesOnceAndPreservesDestinationAcrossRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        openMenu()
        compose.onNodeWithTag("main-destination-help").performScrollTo().performClick()
        compose.onNodeWithTag("help-screen").assertIsDisplayed()
        openMenu()
        compose.onNodeWithTag(SUPPORT).performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(SUPPORT).performTouchInput { doubleClick() }
        compose.runOnUiThread { assertEquals(0, opens) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
        compose.onNodeWithTag("help-screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, opens) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("help-screen").assertIsDisplayed()
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
        compose.runOnIdle { assertEquals(1, opens) }
    }

    @Test fun changingWorkSurfaceDuringCloseCancelsAndDoesNotReplay() {
        compose.setContent { App() }
        startClosing()
        compose.runOnUiThread { work = work.copy(surface = WorkSetupSurface.OVERVIEW) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, opens); work = work.copy(surface = WorkSetupSurface.NONE) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, opens) }
        openMenu()
        compose.onNodeWithTag(SUPPORT).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }

    @Test fun closingAccessDuringAnimationCancelsAndUnlockDoesNotReplay() {
        compose.setContent { App() }
        startClosing()
        compose.runOnUiThread { lock = lock.copy(configuration = AccessLockConfiguration(true), locked = true) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("access-lock-gate").assertIsDisplayed()
        compose.onNodeWithTag(SUPPORT).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, opens); lock = lock.copy(locked = false) }
        compose.waitForIdle()
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
        compose.runOnIdle { assertEquals(0, opens) }
    }

    @Test fun leavingAndResumingDuringCloseCancelsTheOriginalTap() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle = registry
        }
        compose.runOnUiThread {
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { App() }
        }
        startClosing()
        compose.runOnUiThread {
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
        compose.runOnIdle { assertEquals(0, opens) }
        openMenu()
        compose.onNodeWithTag(SUPPORT).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }

    @Test fun freshSetupAndInitialHelpNeverExposeTheAction() {
        compose.setContent { App() }
        compose.runOnIdle { work = work.copy(rootState = WorkSetupState.FreshInstall) }
        compose.onNodeWithTag(SUPPORT).assertDoesNotExist()
        compose.runOnIdle { work = previewV2WorkSetupUiState(); help = HelpUiState() }
        compose.onNodeWithTag(SUPPORT).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, opens) }
    }

    @Test fun monthIsUnchangedAfterOpeningAndRecomposing() {
        var dark by mutableStateOf(false)
        compose.setContent { App(dark = dark) }
        compose.onNodeWithText("Noviembre de 2026").assertExists()
        openMenu()
        compose.onNodeWithTag(SUPPORT).performScrollTo().performClick()
        compose.runOnIdle { dark = true }
        compose.onNodeWithTag("month-grid").assertIsDisplayed()
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
        compose.onNodeWithText("Noviembre de 2026").assertExists()
        compose.runOnIdle { assertEquals(1, opens); assertEquals(0, monthChanges) }
    }

    @Test fun textAndActionRemainReachableInBothThemesOrientationsAndAndroidFontScales() {
        var dark by mutableStateOf(false)
        var landscape by mutableStateOf(false)
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        compose.setContent { App(dark, fontScale, landscape) }
        for (theme in listOf(false, true)) for (wide in listOf(false, true)) for (scale in TestFontScale.entries) {
            compose.runOnIdle { dark = theme; landscape = wide; fontScale = scale }
            openMenu()
            compose.onNodeWithTag(SUPPORT).performScrollTo().assertIsDisplayed().assertHasClickAction()
            for (label in listOf("Apoyar MiGuardia", "Aporte voluntario en el navegador")) {
                val layouts = mutableListOf<TextLayoutResult>()
                val text = compose.onNodeWithText(label, useUnmergedTree = true)
                text
                    .assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                val bounds = text.fetchSemanticsNode().boundsInRoot
                // String Text reconstructs its semantic paragraph at the maximum available width.
                // Check actual line extents and clipped node bounds, not that paragraph's empty width.
                layouts.forEach { layout ->
                    val context = "$label/$theme/$wide/$scale"
                    assertFalse(context, layout.multiParagraph.didExceedMaxLines)
                    assertEquals(context, label.length, layout.getLineEnd(layout.lineCount - 1))
                    assertTrue(context, bounds.width + 1 >= layout.size.width)
                    assertTrue(context, bounds.height + 1 >= layout.size.height)
                    for (line in 0 until layout.lineCount) {
                        assertFalse(context, layout.isLineEllipsized(line))
                        assertTrue(context, layout.getLineLeft(line) >= -1)
                        assertTrue(context, layout.getLineRight(line) <= layout.size.width + 1)
                        assertTrue(context, layout.getLineBottom(line) <= layout.size.height + 1)
                    }
                }
            }
            compose.onNodeWithTag(SUPPORT).performClick()
            compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
        }
        compose.runOnIdle { assertEquals(12, opens) }
    }

    private fun startClosing() {
        openMenu()
        compose.onNodeWithTag(SUPPORT).performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(SUPPORT).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { assertEquals(0, opens) }
    }

    private fun openMenu() { compose.onNodeWithContentDescription("Abrir menú").performClick() }

    @Composable private fun App(dark: Boolean = false, fontScale: TestFontScale = TestFontScale.STANDARD, landscape: Boolean = false) {
        AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
            AccessLockContentGate(lock, DeviceAuthenticationCapability.AVAILABLE, {}, {}, {}, {}, {}) {
                Box(Modifier.size(if (landscape) 600.dp else 320.dp, if (landscape) 320.dp else 600.dp)) {
                    val now = Instant.parse("2026-11-05T12:00:00Z")
                    MiGuardiaApp(
                        calendarState = CalendarUiState(visibleMonth = month, referenceInstant = now,
                            days = projectCalendarMonth(month, emptyList(), emptyList(), emptyList(), now = now),
                            loadState = CalendarLoadState.CONTENT),
                        onPreviousMonth = { monthChanges++ }, onNextMonth = { monthChanges++ }, onToday = { monthChanges++ },
                        onSelectDate = {}, onDismissDate = {}, onRetry = {},
                        workSetupState = work, helpState = help, accessLockState = lock, onSupportMiGuardia = { opens++ },
                    )
                }
            }
        }
    }

    companion object { private const val SUPPORT = "drawer-action-support" }
}
