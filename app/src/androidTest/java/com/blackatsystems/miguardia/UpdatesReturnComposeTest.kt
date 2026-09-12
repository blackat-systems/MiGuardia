package com.blackatsystems.miguardia

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.security.*
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.help.HelpUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import com.blackatsystems.miguardia.ui.worksetup.previewV2WorkSetupUiState
import com.blackatsystems.miguardia.updates.*
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Compose roots, in-memory preview data; no installed beta authority or network. Execution needs QA authorization. */
class UpdatesReturnComposeTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2026, 11)
    private var gate by mutableStateOf("ready")
    private lateinit var issue: () -> Unit
    private lateinit var closeUpdates: () -> Unit
    private var issued = 0L
    private var monthChanges = 0
    private var searches = 0

    @Test fun consumedExternalRequestPreservesHelpAcrossUpdatesAndSavedStateRestore() = preserves("help", "help-screen")
    @Test fun consumedExternalRequestPreservesSummaryAcrossUpdatesAndSavedStateRestore() = preserves("summary", "summary-overview")

    private fun preserves(destination: String, tag: String) {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Root() }
        compose.onNodeWithTag("month-grid").assertIsDisplayed() // Request 1 was already consumed.
        navigate(destination)
        compose.onNodeWithTag(tag).assertIsDisplayed()
        navigateUpdates()
        compose.onNodeWithTag("updates-screen").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("updates-screen").assertIsDisplayed()
        compose.onNodeWithTag("updates-back").performScrollTo().performClick()
        compose.onNodeWithTag(tag).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1L, issued); issue() }
        compose.onNodeWithTag("month-grid").assertIsDisplayed()
        compose.onNodeWithText("Noviembre de 2026").assertExists()
        compose.runOnIdle { assertEquals(2L, issued); assertEquals(0, monthChanges); assertEquals(0, searches) }
    }

    @Test fun newRequestDuringUpdatesWaitsForReturnAndAllLocalGatesThenConsumesOnce() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Root() }
        for (closed in listOf("lock", "work", "setup", "help")) {
            navigate("help")
            navigateUpdates()
            compose.runOnIdle { issue() }
            compose.onNodeWithTag("updates-screen").assertIsDisplayed()
            compose.runOnIdle { gate = closed; closeUpdates() }
            compose.onNodeWithTag("month-grid").assertDoesNotExist()
            compose.onNodeWithTag("updates-screen").assertDoesNotExist()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("month-grid").assertDoesNotExist()
            compose.runOnIdle { gate = "ready" }
            compose.onNodeWithTag("month-grid").assertIsDisplayed()
            compose.onNodeWithText("Noviembre de 2026").assertExists()
            navigate("help")
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("help-screen").assertIsDisplayed()
        }
        compose.runOnIdle { assertEquals(5L, issued); assertEquals(0, monthChanges); assertEquals(0, searches) }
    }

    @Composable private fun Root() {
        var request by rememberSaveable { mutableLongStateOf(1L) }
        var updates by rememberSaveable { mutableStateOf(false) }
        val holder = rememberSaveableStateHolder()
        // Observe in composition: reads made only inside SideEffect do not invalidate Root.
        val observedRequest = request
        SideEffect { issued = observedRequest; issue = { request = nextCalendarNavigationRequest(request) }; closeUpdates = { updates = false } }
        val lock = AccessLockState(phase = AccessLockPhase.READY,
            configuration = AccessLockConfiguration(enabled = gate == "lock"), locked = gate == "lock", privacyCoverVisible = false)
        val work = previewV2WorkSetupUiState().let {
            when (gate) { "work" -> it.copy(surface = WorkSetupSurface.OVERVIEW)
                "setup" -> it.copy(rootState = WorkSetupState.FreshInstall)
                else -> it }
        }
        MiGuardiaTheme {
            AccessLockContentGate(lock, DeviceAuthenticationCapability.AVAILABLE, {}, {}, {}, {}, {}) {
                if (updates && gate == "ready") {
                    UpdatesScreen(UpdatesState(open = true), { searches++ }, {}, {}, {}, { updates = false })
                } else holder.SaveableStateProvider("beta-work-content") {
                    val now = Instant.parse("2026-11-05T12:00:00Z")
                    MiGuardiaApp(calendarState = CalendarUiState(visibleMonth = month, referenceInstant = now,
                        days = projectCalendarMonth(month, emptyList(), emptyList(), emptyList(), now = now), loadState = CalendarLoadState.CONTENT),
                        onPreviousMonth = { monthChanges++ }, onNextMonth = { monthChanges++ }, onToday = { monthChanges++ },
                        onSelectDate = {}, onDismissDate = {}, onRetry = {}, workSetupState = work,
                        helpState = if (gate == "help") HelpUiState() else HelpUiState.completedForPreview(),
                        accessLockState = lock, calendarNavigationRequest = request, onUpdates = { updates = true })
                }
            }
        }
    }

    private fun navigate(destination: String) {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithTag("main-destination-$destination").performScrollTo().performClick()
    }
    private fun navigateUpdates() {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithTag("drawer-action-updates").performScrollTo().performClick()
    }
}
