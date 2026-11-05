package com.blackatsystems.miguardia.stage12

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.StartupRecoveryState
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.security.AccessLockPhase
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.help.OnboardingPreferencesStore
import com.blackatsystems.miguardia.ui.help.OnboardingStoreState
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

/** The received QA starts empty. All baseline writes go through the ordinary UI of one MainActivity. */
class Stage12QaBaselineBootstrapTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val capture = OriginalActivity()

    @Test fun initializesSyntheticQaThroughNormalUi() {
        val context = instrumentation.targetContext
        val app = context.applicationContext as MiGuardiaApplication
        var scenario: ActivityScenario<MainActivity>? = null
        var observerAttempted = false
        var launchAttempted = false
        var scenarioOwned = false
        var phase = Phase.ADMISSION
        var firstPhase = phase
        var primary: Throwable? = null

        fun retain(failure: Throwable) {
            val first = primary
            if (first == null) {
                primary = failure
                firstPhase = phase
            } else if (first !== failure && first.suppressed.none { it === failure }) {
                first.addSuppressed(failure)
            }
        }

        try {
            check(instrumentation is Stage12NormalQaRunner && context.packageName == QA)
            val args = InstrumentationRegistry.getArguments()
            val runtime = Stage12RuntimeTarget.identify(Build.VERSION.SDK_INT, Build.MODEL, Build.MANUFACTURER,
                Build.ID, Build.FINGERPRINT, Build.SUPPORTED_ABIS.firstOrNull())
            check(args.getString("class") == Stage12RuntimeTarget.QA_BASELINE_BOOTSTRAP)
            Stage12RuntimeTarget.requireSelection(runtime, listOf(Stage12RuntimeTarget.QA_BASELINE_BOOTSTRAP),
                requireNotNull(args.getString("stage12CalendarClock")),
                args.containsKey(Stage12SummaryObservation.ARGUMENT), args.containsKey("stage12CalendarClock"))
            onMain {
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                check(Stage.values().filter { it != Stage.DESTROYED }.all { monitor.getActivitiesInStage(it).isEmpty() })
                observerAttempted = true
                app.registerActivityLifecycleCallbacks(capture)
            }
            phase = Phase.LAUNCH
            launchAttempted = true
            val launched = ActivityScenario.launch(MainActivity::class.java)
            scenario = launched
            launched.onActivity { check(it === original() && it.application === app && it.packageName == QA) }
            scenarioOwned = true
            compose.waitUntil(WAIT) { onMain { ready(original(), app) && original().hasWindowFocus() } }

            phase = Phase.FRESH_INSTALL
            target(hasTestTag("work-sector-selection"))
            check(work().rootState == WorkSetupState.FreshInstall && work().selectedSector == null)

            phase = Phase.SECTOR
            clickTag("work-sector-nursing")
            check(work().rootState == WorkSetupState.FreshInstall && work().selectedSector == WorkSector.NURSING)
            clickTag("work-sector-continue")
            compose.waitUntil(WAIT) { work().rootState is WorkSetupState.V2NeedsFirstSet }
            click(hasText("Crear primer lugar", substring = false))

            phase = Phase.PLACE
            fill("work-place-name", "QA A119", "QA A119")
            fill("work-place-abbreviation", "AQA", "AQA")
            val place = work().placeDraft
            check(place.name == "QA A119" && place.abbreviation == "AQA" && place.address.isEmpty() &&
                place.note.isEmpty() && !place.nightHoursEnabled && !place.classifySaturday && !place.classifySunday &&
                !place.showWeekendSummary && !place.classifyHoliday && !place.showHolidaySummary &&
                place.weatherLatitude == null && place.weatherLongitude == null)
            clickTag("work-place-continue")

            phase = Phase.SCHEDULE
            fill("work-time-start", "0800", "08:00")
            fill("work-time-end", "0900", "09:00")
            check(work().templateDraft.colorArgb == null)
            clickTag("work-template-color")
            target(hasText("Selector de color", substring = false), dialog = true)
            target(hasText("HEX: #5C4DFF", substring = false), dialog = true, scroll = true)
            click(hasText("Usar color", substring = false), dialog = true)
            val template = work().templateDraft
            check(template.typeName == "Turno habitual" && template.startTime == "08:00" &&
                template.endTime == "09:00" && template.colorArgb == 0xFF5C4DFF.toInt())
            clickTag("work-set-save")
            compose.waitUntil(WAIT) {
                val state = work()
                state.rootState is WorkSetupState.V2Ready && state.surface == WorkSetupSurface.COMPLETION
            }
            click(hasText("Volver al Calendario", substring = false))

            phase = Phase.GUIDE
            target(hasTestTag("help-introduction-1"))
            clickTag("help-skip")
            target(hasText("¿Omitir la guía?", substring = false), dialog = true)
            clickTag("help-confirm-skip", dialog = true)

            phase = Phase.READY
            target(hasTestTag("month-grid"))
            compose.waitUntil(WAIT) {
                val state = work()
                state.rootState is WorkSetupState.V2Ready && state.surface == WorkSetupSurface.NONE &&
                    onMain { existing(CalendarViewModel::class.java).uiState.value.loadState == CalendarLoadState.CONTENT }
            }
            val onboarding = runBlocking { withTimeout(WAIT) { app.onboardingPreferences.state.first() } }
            check(onboarding is OnboardingStoreState.Ready &&
                onboarding.completedVersion >= OnboardingPreferencesStore.CURRENT_VERSION)
            target(hasTestTag("month-grid"))
        } catch (failure: Throwable) {
            retain(failure)
        } finally {
            phase = Phase.CLOSE
            if (launchAttempted) {
                try {
                    val owner = checkNotNull(capture.activity)
                    // Finish the retained instance, even if a launch or recreation lost the
                    // scenario's exact owner. Never finish an Activity selected after the failure.
                    onMain { if (!owner.isDestroyed) owner.finish() }
                    val deadline = SystemClock.elapsedRealtime() + WAIT
                    while (!onMain { owner.isDestroyed && owner.lifecycle.currentState == Lifecycle.State.DESTROYED }) {
                        check(SystemClock.elapsedRealtime() < deadline)
                        SystemClock.sleep(25)
                    }
                    check(!capture.replacement)
                    if (scenarioOwned) {
                        val retained = checkNotNull(scenario)
                        check(retained.state == Lifecycle.State.DESTROYED)
                        retained.close()
                    }
                } catch (failure: Throwable) {
                    retain(failure)
                }
            }
            if (observerAttempted) {
                try { onMain { app.unregisterActivityLifecycleCallbacks(capture) } }
                catch (failure: Throwable) { retain(failure) }
            }
        }
        // The runner's LogRunListener may print the JUnit Throwable. Keep the original and its
        // secondary errors in RAM and publish only the fixed stage code.
        primary?.let { throw AssertionError("QA_BASELINE_BOOTSTRAP_${firstPhase.name}_FAILED") }
    }

    private fun original(): MainActivity = checkNotNull(capture.activity).also { check(!capture.replacement) }

    private fun ready(owner: MainActivity, app: MiGuardiaApplication): Boolean {
        check(owner.application === app && owner.packageName == QA && !owner.isDestroyed && !owner.isFinishing)
        val startup = app.startupRecoveryGate.state.value
        val access = app.accessLockCoordinator.state.value
        check(startup !is StartupRecoveryState.Failed && access.phase != AccessLockPhase.STORE_ERROR)
        return owner.lifecycle.currentState == Lifecycle.State.RESUMED && startup == StartupRecoveryState.Ready &&
            access.phase == AccessLockPhase.READY && access.allowsSensitiveContent
    }

    private fun ownedView(node: SemanticsNode, owner: MainActivity, dialog: Boolean): View? {
        val view = (node.root as? ViewRootForTest)?.view ?: return null
        var context = view.context
        var hops = 0
        while (context !is Activity) {
            if (hops++ >= 16) return null
            val base = (context as? ContextWrapper)?.baseContext ?: return null
            if (base === context) return null
            context = base
        }
        if (context !== owner || !view.isAttachedToWindow) return null
        val provider = generateSequence(view) { it.parent as? View }.filterIsInstance<DialogWindowProvider>().firstOrNull()
        val window = if (dialog) provider?.window?.takeIf { it !== owner.window } ?: return null
            else owner.window.takeIf { provider == null } ?: return null
        val decor = window.peekDecorView() ?: return null
        return view.takeIf { it.rootView === decor && decor.isAttachedToWindow && decor.hasWindowFocus() }
    }

    private fun target(matcher: SemanticsMatcher, dialog: Boolean = false, scroll: Boolean = false): SemanticsNodeInteraction {
        val owner = original()
        val scoped = matcher and SemanticsMatcher("bootstrap window of original QA MainActivity") {
            ownedView(it, owner, dialog) != null
        }
        compose.waitUntil(WAIT) {
            onMain { check(ready(original(), owner.application as MiGuardiaApplication)) }
            val count = compose.onAllNodes(scoped).fetchSemanticsNodes().size
            check(count <= 1)
            count == 1
        }
        val node = compose.onNode(scoped)
        if (scroll && !node.isDisplayed()) node.performScrollTo()
        return node.assertIsDisplayed().assertIsEnabled()
    }

    private fun click(matcher: SemanticsMatcher, dialog: Boolean = false) {
        activate(target(matcher and hasClickAction(), dialog, scroll = !dialog))
    }

    private fun clickTag(tag: String, dialog: Boolean = false) = click(hasTestTag(tag), dialog)

    private fun activate(node: SemanticsNodeInteraction) =
        node.performSemanticsAction(SemanticsActions.OnClick) { check(it()) }

    private fun fill(tag: String, entered: String, expected: String) {
        val field = target(hasTestTag(tag) and hasClickAction(), scroll = true)
        check(field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty())
        activate(field)
        field.assertIsFocused().performTextReplacement(entered)
        check(field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text == expected)
        onMain {
            val owner = original()
            val decor = checkNotNull(owner.window.peekDecorView())
            check(decor.isAttachedToWindow && decor.hasWindowFocus())
            owner.currentFocus?.clearFocus()
            (owner.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(checkNotNull(decor.windowToken), 0)
        }
    }

    private fun work() = onMain { existing(WorkSetupViewModel::class.java).uiState.value }

    private fun <T : ViewModel> existing(type: Class<T>): T =
        ViewModelProvider(original(), ExistingOnly)[type]

    private fun <T> onMain(body: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(body) }
        return checkNotNull(result).getOrThrow()
    }

    private object ExistingOnly : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            error("QA_BASELINE_EXISTING_MODEL_REQUIRED")
    }

    private class OriginalActivity : Application.ActivityLifecycleCallbacks {
        @Volatile var activity: MainActivity? = null
            private set
        @Volatile var replacement = false
            private set

        override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is MainActivity) {
                val first = this.activity
                if (first == null) this.activity = activity
                else if (first !== activity) replacement = true
            }
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private enum class Phase { ADMISSION, LAUNCH, FRESH_INSTALL, SECTOR, PLACE, SCHEDULE, GUIDE, READY, CLOSE }

    private companion object {
        const val QA = "com.blackatsystems.miguardia.qa"
        const val WAIT = 30_000L
    }
}
