package com.blackatsystems.miguardia.stage12

import android.content.ComponentName
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.availability.*
import com.blackatsystems.miguardia.ui.calendar.*
import com.blackatsystems.miguardia.ui.management.*
import com.blackatsystems.miguardia.ui.nextevent.*
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.worksetup.*
import com.blackatsystems.miguardia.ui.weather.*
import java.time.LocalDate
import java.util.UUID
import java.util.IdentityHashMap
import kotlinx.coroutines.*

/** Actual product surfaces and factories, with an explicit composition lifetime; no Main/bootstrap claim. */
internal class Stage12TemporalComponentOwner(
    private val fixture: Stage12TemporalCase,
    private val resources: Stage12TemporalResources,
    retain: (Throwable) -> Unit,
    private val requireActive: () -> Unit,
    private val calendarWeatherOptIn: Boolean = false,
) {
    private val failure = Stage12TemporalFailure(retain)
    private val models = IdentityHashMap<ViewModel, Job?>()
    private val notReturned = java.util.Collections.newSetFromMap(IdentityHashMap<ViewModel, Boolean>())
    private var constructorUnknown = false
    private var attempted = false
    private var closeAttempted = false
    private var closed = false
    private var terminalCard by mutableStateOf<NextEventUiState?>(null)
    private var terminalAttempted = false
    private var originalContentView: ComposeView? = null
    private var scenario: ActivityScenario<ComponentActivity>? = null
    var activity: ComponentActivity? = null; private set
    lateinit var calendar: CalendarViewModel; private set
    lateinit var work: WorkSetupViewModel; private set
    lateinit var manual: V2ManualShiftLoadViewModel; private set
    lateinit var edit: V2ShiftEditViewModel; private set
    lateinit var actual: V2ShiftActualViewModel; private set
    lateinit var availability: AvailabilityViewModel; private set
    var weather: WeatherViewModel? = null; private set
    data class CalendarCallbacks(val selectedDates: List<LocalDate>, val selectionReturns: Int,
        val dismissCalls: Int, val dismissReturns: Int, val weatherIds: List<UUID>,
        val weatherOpenReturns: Int, val weatherCloseCalls: Int, val weatherCloseReturns: Int)
    private val selectedDates = mutableListOf<LocalDate>()
    private var selectionReturns = 0
    private var dismissCalls = 0
    private var dismissReturns = 0
    private val weatherIds = mutableListOf<UUID>()
    private var weatherOpenReturns = 0
    private var weatherCloseCalls = 0
    private var weatherCloseReturns = 0
    /** Called from onActivity; counts the actual UI adapter, never a manual VM invocation. */
    fun calendarCallbacks(): CalendarCallbacks {
        check(calendarWeatherOptIn && android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        return CalendarCallbacks(selectedDates.toList(), selectionReturns, dismissCalls, dismissReturns,
            weatherIds.toList(), weatherOpenReturns, weatherCloseCalls, weatherCloseReturns)
    }
    private fun active() { requireActive(); failure.requireActive() }

    private fun <T : ViewModel> model(owner: ComponentActivity, kind: String, type: Class<T>, factory: ViewModelProvider.Factory): T {
        val guarded = object : ViewModelProvider.Factory {
            override fun <V : ViewModel> create(modelClass: Class<V>, extras: CreationExtras): V {
                var entered = false
                var returned = false
                try {
                    active(); fixture.opened.requireBinding(fixture.binding)
                    entered = true
                    val value = factory.create(modelClass, extras)
                    returned = true
                    synchronized(models) { models[value] = null; notReturned += value }
                    val job = checkNotNull(value.viewModelScope.coroutineContext[Job])
                    synchronized(models) { models[value] = job }
                    active()
                    return value
                } catch (error: Throwable) {
                    if (entered && !returned) constructorUnknown = true
                    failure.reject(error)
                }
            }
        }
        return ViewModelProvider(owner, guarded).get("${fixture.run}/${fixture.scope.wire}/$kind", type).also { value ->
            synchronized(models) { check(value in models); notReturned.remove(value) }
        }
    }
    fun launch() {
        try {
            active(); check(!attempted && !closeAttempted); attempted = true
            scenario = ActivityScenario.launch(Intent().setComponent(ComponentName("com.blackatsystems.miguardia.qa", ComponentActivity::class.java.name)))
            checkNotNull(scenario).onActivity { owner ->
                check(activity == null); activity = owner
                val f = resources.factories
                calendar = model(owner, "calendar", CalendarViewModel::class.java, f.calendar)
                work = model(owner, "work", WorkSetupViewModel::class.java, f.work)
                manual = model(owner, "manual", V2ManualShiftLoadViewModel::class.java, f.manual)
                edit = model(owner, "edit", V2ShiftEditViewModel::class.java, f.edit)
                actual = model(owner, "actual", V2ShiftActualViewModel::class.java, f.actual)
                availability = model(owner, "availability", AvailabilityViewModel::class.java, f.availability)
                if (calendarWeatherOptIn) {
                    check(fixture.scope == Stage12CalendarDetailContract.scope())
                    weather = model(owner, "calendar-weather", WeatherViewModel::class.java, f.weather)
                }
                val parent = resources.composition.initialize(owner)
                val ownContext = resources.context.forActivity(owner)
                owner.setContent(parent = parent) {
                    CompositionLocalProvider(LocalContext provides ownContext) {
                        MiGuardiaTheme(darkTheme = resources.graph.capturedThemeMode.resolve(isSystemInDarkTheme())) {
                            val terminal = terminalCard
                            if (terminal != null) {
                                Column(Modifier.fillMaxSize().testTag("temporal-projection-scroll").verticalScroll(rememberScrollState())) {
                                    NextEventCard(terminal, { failure.reject(IllegalStateException("TEMPORAL_COMPACT_RETRY_EXCLUDED")) })
                                }
                            } else {
                            val c by calendar.uiState.collectAsState()
                            val w by work.uiState.collectAsState()
                            val m by manual.uiState.collectAsState()
                            val e by edit.uiState.collectAsState()
                            val real by actual.uiState.collectAsState()
                            val a by availability.uiState.collectAsState()
                            val weatherState = if (calendarWeatherOptIn) checkNotNull(weather).uiState.collectAsState().value else WeatherUiState()
                            val weatherActions = if (calendarWeatherOptIn) {
                                val actualWeather = checkNotNull(weather)
                                WeatherActions.from(actualWeather).copy(
                                    openShift = { id -> active(); weatherIds += id; actualWeather.openShift(id); weatherOpenReturns += 1 },
                                    close = { active(); weatherCloseCalls += 1; actualWeather.close(); weatherCloseReturns += 1 },
                                )
                            } else WeatherActions()
                            MiGuardiaApp(calendarState = c, onPreviousMonth = calendar::showPreviousMonth,
                                onNextMonth = calendar::showNextMonth, onToday = calendar::showCurrentMonth,
                                onSelectDate = if (calendarWeatherOptIn) {
                                    { date -> active(); selectedDates += date; calendar.selectDate(date); selectionReturns += 1 }
                                } else calendar::selectDate,
                                onDismissDate = if (calendarWeatherOptIn) {
                                    { active(); dismissCalls += 1; calendar.clearSelectedDate(); dismissReturns += 1 }
                                } else calendar::clearSelectedDate,
                                onRetry = calendar::retry, onEnterCalendarEditMode = calendar::enterEditMode,
                                onEnterManualCalendarSelection = calendar::enterManualLoadSelection,
                                onEnterHolidayCalendarSelection = calendar::enterHolidaySelection,
                                onSetCalendarSelectionPurpose = calendar::setSelectionPurpose,
                                onEditSelectionChange = calendar::setEditSelectedDates,
                                onConfirmEditSelection = calendar::confirmEditSelection,
                                onResumeEditSelection = calendar::resumeEditSelection,
                                onFinishCalendarEditMode = calendar::finishEditMode,
                                onPreviewCalendarBatch = calendar::previewCalendarBatch,
                                onDismissCalendarBatchPreview = calendar::dismissCalendarBatchPreview,
                                onApplyCalendarBatch = calendar::applyCalendarBatch,
                                onClearCalendarBatchMessage = calendar::clearCalendarBatchMessage,
                                v2ManualShiftLoadState = m, v2ManualShiftLoadActions = V2ManualShiftLoadActions.from(manual),
                                v2ShiftEditState = e, v2ShiftEditActions = V2ShiftEditActions.from(edit),
                                v2ShiftActualState = real, v2ShiftActualActions = V2ShiftActualActions.from(actual),
                                workSetupState = w, workSetupActions = WorkSetupActions.from(work),
                                availabilityState = a, availabilityActions = AvailabilityActions.from(availability),
                                weatherState = weatherState, weatherActions = weatherActions)
                            }
                        }
                    }
                }
                resources.composition.bindContent(owner)
                originalContentView = owner.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as ComposeView
            }
            active()
        } catch (error: Throwable) { failure.reject(error) }
    }
    fun onActivity(block: (ComponentActivity) -> Unit) {
        active()
        try { checkNotNull(scenario).onActivity { owner -> check(owner === activity); block(owner) } }
        catch (error: Throwable) { failure.reject(error) }
    }
    /** Controlled terminal component mount, not public navigation or a VM clock replacement. */
    fun showHistoricalCompactTerminal(state: NextEventUiState) {
        onActivity { owner ->
            check(!closeAttempted && !terminalAttempted && !owner.isFinishing && !owner.isDestroyed &&
                owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            check(state.loadState == NextEventLoadState.CONTENT && state.result != null && state.errorMessage == null)
            check(calendar.uiState.value.detailDate == null && !manual.uiState.value.isActive &&
                availability.uiState.value.surface == AvailabilitySurface.NONE)
            val content = owner.findViewById<android.view.ViewGroup>(android.R.id.content)
            check(content.childCount == 1 && content.getChildAt(0) === checkNotNull(originalContentView))
            terminalAttempted = true
            terminalCard = state
            active()
        }
    }
    @android.annotation.SuppressLint("RestrictedApi")
    suspend fun closeOriginal() {
        try {
            check(!closeAttempted); closeAttempted = true
            try { checkNotNull(scenario).close() } catch (error: Throwable) { failure.remember(error) }
            val owner = checkNotNull(activity)
            check(owner.isDestroyed && owner.lifecycle.currentState == Lifecycle.State.DESTROYED)
            val storeEmpty = try { owner.viewModelStore.keys().isEmpty() } catch (error: Throwable) { failure.remember(error); false }
            resources.composition.observeDestroyed(owner)
            try { resources.composition.closeAfterDestroyed { block -> InstrumentationRegistry.getInstrumentation().runOnMainSync(block) } }
            catch (error: Throwable) { failure.remember(error) }
            withTimeout(30_000) { synchronized(models) { models.values.filterNotNull() }.joinAll() }
            check(storeEmpty && resources.composition.closureProven && !constructorUnknown &&
                synchronized(models) { notReturned.isEmpty() && models.values.all { it != null && it.isCompleted } })
            closed = true
            failure.requireActive()
        } catch (error: Throwable) { failure.reject(error) }
    }
    val closureProven: Boolean get() = closed && resources.composition.closureProven && !constructorUnknown &&
        synchronized(models) { notReturned.isEmpty() && models.values.all { it != null && it.isCompleted } }
}
