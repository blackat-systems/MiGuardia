package com.blackatsystems.miguardia

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.backup.BackupPayloadCodec
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.summary.SummaryViewModel
import com.blackatsystems.miguardia.ui.summary.SummaryLoadState
import com.blackatsystems.miguardia.ui.summary.SummaryPreferences
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.weather.WeatherPreferences
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasViewModel
import com.blackatsystems.miguardia.ui.availability.AvailabilityViewModel
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadViewModel
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadStage
import com.blackatsystems.miguardia.ui.management.V2RecurringPlanViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftActualViewModel
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupViewModel
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import java.io.File
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID
import java.util.Collections
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Same driver and synthetic data for preserved Stage 3.2 APK and Stage 4 APK. */
class Stage4FlowsActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val store get() = (context.applicationContext as MiGuardiaApplication).localDataStore
    private var scenario: ActivityScenario<MainActivity>? = null
    private var measurementPhase = "setup"
    private var measurementIteration = -1
    private val measuredWindows = JSONArray()
    private val anchorMonth = YearMonth.now(AppDefaults.zoneId())
    private var startDate = anchorMonth.minusMonths(11).atDay(1)
    private val frames = Collections.synchronizedList(mutableListOf<Long>())
    private val droppedFrameSamples = java.util.concurrent.atomic.AtomicInteger()
    private val frameThread = HandlerThread("stage4-frame-metrics")
    private val frameListener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
        droppedFrameSamples.addAndGet(dropped)
    }

    @Before fun prepareQaOnly() {
        check(context.packageName == "com.blackatsystems.miguardia.qa")
        check(!focusLostInProcess) { "Earlier focus loss stopped this instrumentation process; no further QA mutations" }
        assertQaFocus(allowOwnLifecycleTransition = true)
        preparePublicRootAndMarkOnboardingCompletedForTest()
        check(context.getSharedPreferences(MainActivity.DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
            .edit().putInt(MainActivity.APP_ZOOM_PERCENT, 100)
            .putString(MainActivity.APP_THEME_MODE, AppThemeMode.LIGHT.name).commit())
        runBlocking {
            val app = context.applicationContext as MiGuardiaApplication
            app.summaryPreferences.replacePortable(SummaryPreferences())
            app.weatherPreferences.replacePortable(WeatherPreferences())
        }
        store.clearAllDataForInstrumentation()
        frameThread.start()
    }

    @After fun close() {
        closeActivity()
        frameThread.quitSafely()
        frameThread.join(5_000)
    }

    @Test fun configurationChildrenReturnToTheirRealParentAfterRecreationWithoutWrites() {
        seed(8, 365)
        launch()
        val before = snapshot()
        menu("drawer-action-work-setup")
        compose.onNodeWithTag("advanced-options-toggle").performScrollTo().performClick()
        val children = listOf(
            "work-setup-hours-progress" to "hours-progress-content",
            "work-setup-availability" to "availability-configure",
            "work-setup-recurring-plans" to "v2-recurring-surface",
            "work-setup-extra-classes" to "v2-extra-class-new",
        )
        children.forEach { (entry, child) ->
            compose.onNodeWithTag(entry).performScrollTo().performClick()
            waitTag(child)
            scenario!!.recreate()
            awaitQaFocusAfterLifecycleChange()
            waitTag(child)
            back()
            waitTag("work-setup-overview")
            // The section remains expanded and the same entry remains reachable after recreation.
            compose.onNodeWithTag(entry).performScrollTo().assertIsDisplayed()
        }
        back()
        waitTag("month-grid")
        scenario!!.onActivity {
            assertEquals(WorkSetupSurface.NONE, ViewModelProvider(it)[WorkSetupViewModel::class.java].uiState.value.surface)
        }
        assertEquals(before, snapshot())
    }

    @Test fun directChildrenCloseWithoutInventingAConfigurationParent() {
        seed(8, 365)
        launch()
        val before = snapshot()
        val openers: List<(MainActivity) -> Unit> = listOf(
            { ViewModelProvider(it)[HoursAndExtrasViewModel::class.java].openProgress() },
            { ViewModelProvider(it)[AvailabilityViewModel::class.java].openOverview() },
            { val vm = ViewModelProvider(it); vm[V2RecurringPlanViewModel::class.java].openPlans(vm[WorkSetupViewModel::class.java].uiState.value.rootState) },
            { val vm = ViewModelProvider(it); vm[V2ShiftActualViewModel::class.java].openCatalog(vm[WorkSetupViewModel::class.java].uiState.value.rootState) },
        )
        val children = listOf("hours-progress-content", "availability-configure", "v2-recurring-surface", "v2-extra-class-new")
        openers.zip(children).forEach { (open, tag) ->
            scenario!!.onActivity(open)
            waitTag(tag)
            back()
            waitTag("month-grid")
            compose.onNodeWithTag("work-setup-overview").assertDoesNotExist()
        }
        assertEquals(before, snapshot())
    }

    @Test fun representativeYearMeasurements() = measureHistory("representative", 240, 365)

    @Test fun stressHistoryMeasurements() = measureHistory("stress", 1920, 2920)

    @Test fun seedRepresentativeForColdStartMeasurements() {
        seed(240, 365)
        launch()
        assertEquals(240, runBlocking { store.v2Shifts.observeAll(V2AppTestFixture.TIMELINE_ID, WorkSector.NURSING).first().size })
        val directory = File(context.filesDir, "stage4").also { check(it.exists() || it.mkdirs()) }
        File(directory, "cold-seed.json").writeText(JSONObject()
            .put("initial_database_sha256", logicalDatasetHash())
            .put("initial_shifts", 240).put("anchor_month", anchorMonth.toString())
            .put("theme", "LIGHT").put("internal_zoom_percent", 100)
            .put("windows", measuredWindows).toString(2))
    }

    private fun measureHistory(label: String, count: Int, span: Int) {
        seed(count, span)
        val datasetHash = logicalDatasetHash()
        val samples = linkedMapOf<String, MutableList<Double>>()
        var completedIterations = 0
        var completed = false
        fun measure(name: String, action: () -> Unit) {
            measurementPhase = name
            assertQaFocus(allowOwnLifecycleTransition = scenario == null)
            val start = System.nanoTime()
            action()
            compose.waitForIdle()
            assertQaFocus()
            samples.getOrPut(name) { mutableListOf() }.add((System.nanoTime() - start) / 1_000_000.0)
        }
        try {
            repeat(12) { i ->
                measurementIteration = i
                measure("warm_activity_start") { launch() }
                var expectedMonth = anchorMonth.minusMonths(1)
                scenario!!.onActivity { expectedMonth = ViewModelProvider(it)[CalendarViewModel::class.java].uiState.value.visibleMonth.minusMonths(1) }
                measure("change_month") {
                    compose.onNodeWithContentDescription("Mes anterior").performClick()
                    awaitCalendar(expectedMonth)
                }
                val detailShift = runBlocking { store.shifts.observeStartingBetween(expectedMonth.atDay(1), expectedMonth.atEndOfMonth()).first() }.first()
                measure("open_detail") {
                    scenario!!.onActivity { ViewModelProvider(it)[CalendarViewModel::class.java].selectDate(detailShift.localStartDate) }
                    waitTag("day-edit-menu")
                    waitTag("shift-detail-card-${detailShift.id}")
                    awaitState { activity ->
                        val state = ViewModelProvider(activity)[CalendarViewModel::class.java].uiState.value
                        state.detailDate == detailShift.localStartDate &&
                            state.stage2Expectations[detailShift.id] != null && !state.stage2Busy
                    }
                }
                back()
                var load: V2ManualShiftLoadViewModel? = null
                val dates = setOf(1, 3, 5).mapTo(linkedSetOf()) {
                    anchorMonth.plusMonths(110L + i).atDay(it)
                }
                measure("enter_load") {
                    scenario!!.onActivity {
                        val vm = ViewModelProvider(it)
                        load = vm[V2ManualShiftLoadViewModel::class.java]
                        load!!.start(vm[WorkSetupViewModel::class.java].uiState.value.rootState)
                        load!!.confirmDates(dates)
                    }
                    waitTag("manual-institutions")
                    compose.waitUntil(30_000) { assertQaFocus(); !load!!.uiState.value.isLoading }
                }
                measure("show_catalog") {
                    compose.onNodeWithTag("manual-institutions").performScrollTo().performClick()
                    waitTag("manual-inline-catalog")
                }
                compose.waitUntil(30_000) { assertQaFocus(); load!!.uiState.value.templateOptions.isNotEmpty() }
                scenario!!.onActivity { load!!.chooseTemplate(load!!.uiState.value.templateOptions.first().template.id) }
                measure("return_to_selection") {
                    scenario!!.onActivity { load!!.backToDateSelection() }
                    waitTag("month-grid")
                }
                scenario!!.onActivity { load!!.confirmDates(dates) }
                waitTag("manual-institutions")
                measure("preview_three_shifts") {
                    scenario!!.onActivity { load!!.requestReview() }
                    compose.waitUntil(30_000) { assertQaFocus(); load!!.uiState.value.stage == V2ManualShiftLoadStage.REVIEW || load!!.uiState.value.errorMessage != null }
                    assertNull(load!!.uiState.value.errorMessage)
                    assertEquals(V2ManualShiftLoadStage.REVIEW, load!!.uiState.value.stage)
                }
                measure("save_three_shifts") {
                    scenario!!.onActivity { load!!.save() }
                    compose.waitUntil(30_000) { assertQaFocus(); load!!.uiState.value.stage == V2ManualShiftLoadStage.IDLE || load!!.uiState.value.errorMessage != null }
                    assertNull(load!!.uiState.value.errorMessage)
                }
                assertEquals(3, runBlocking { store.shifts.observeStartingBetween(dates.min(), dates.max()).first() }.size)
                measure("open_summary") {
                    menu("main-destination-summary")
                    awaitState { activity ->
                        val summary = ViewModelProvider(activity)[SummaryViewModel::class.java].uiState.value
                        summary.loadState in setOf(SummaryLoadState.CONTENT, SummaryLoadState.EMPTY) &&
                            summary.projection?.month == summary.visibleMonth && summary.errorMessage == null
                    }
                }
                closeActivity()
                completedIterations++
            }
            completed = true
        } finally {
            val frameCopy = synchronized(frames) { frames.toList() }
            val data = JSONObject().put("dataset", label).put("initial_shifts", count)
                .put("history_days", span).put("initial_notes", (count + 9) / 10)
                .put("initial_attachments", 0).put("recurring_plans", 0)
                .put("requested_iterations", 12).put("completed_iterations", completedIterations)
                .put("status", if (completed) "completed" else "incomplete")
                .put("dataset_start", startDate.toString()).put("saved_shifts_per_iteration", 3)
                .put("anchor_month", anchorMonth.toString()).put("initial_database_sha256", datasetHash)
                .put("theme", "LIGHT").put("internal_zoom_percent", 100)
                .put("activity_windows", measuredWindows)
                .put("summary_preferences", "defaults; introSeen=false; no hidden metrics")
                .put("weather_preferences", "disabled; provider not accepted; no weather fetch requested")
                .put("navigation_scope", "mixed UI clicks and ViewModel actions; not a physical gesture or OEM certification")
                .put("clock", "System.nanoTime; action plus Compose and focus synchronization; no artificial sleeps")
                .put("samples_ms", JSONObject(samples.mapValues { JSONArray(it.value) }))
                .put("frames_recorded", frameCopy.size)
                .put("frame_metric_dropped_samples", droppedFrameSamples.get())
                .put("frame_scope", "whole activity session after first calendar content; excludes startup; fixed threshold is not device jank")
                .put("frames_over_16_67ms", frameCopy.count { it > 16_666_667 })
                .put("frame_durations_ns", JSONArray(frameCopy))
            val dir = File(context.filesDir, "stage4").apply { mkdirs() }
            File(dir, "performance-$label.json").writeText(data.toString(2))
        }
    }

    private fun seed(count: Int, span: Int) = runBlocking {
        startDate = anchorMonth.minusMonths(if (span > 365) 95 else 11).atDay(1)
        val timestamp = Instant.parse("2026-01-01T00:00:00Z")
        val zone = AppDefaults.zoneId()
        fun shift(i: Int): Shift {
            val date = startDate.plusDays((i.toLong() * span / count))
            val night = i % 3 == 0
            val start = if (night) LocalTime.of(19, 0) else LocalTime.of(8, 0)
            val end = if (night) LocalTime.of(7, 0) else LocalTime.of(16, 0)
            return Shift(UUID.nameUUIDFromBytes("stage4-shift-$i".toByteArray()), date.atTime(start).atZone(zone).toInstant(),
                date.plusDays(if (night) 1 else 0).atTime(end).atZone(zone).toInstant(), zone, date,
                "Institución QA sintética", "QAS", null, start, end, 0xFF607D8B.toInt(), "Puesto de prueba",
                if (i % 13 == 0) ShiftStatus.CANCELLED else ShiftStatus.PLANNED,
                UUID(0, 1), timestamp, timestamp)
        }
        val models = (0 until count).map(::shift)
        val prototypes = models.take(3).associate { model ->
            model.startTimeSnapshot to V2AppTestFixture.writeFor(store, model, startDate)
        }
        val writes = models.map { model ->
            val source = prototypes.getValue(model.startTimeSnapshot)
            V2ShiftWrite(model.copy(sourceObjectiveId = source.shift.sourceObjectiveId), source.snapshot.copy(shiftId = model.id))
        }
        val notes = writes.filterIndexed { index, _ -> index % 10 == 0 }.map { write ->
            ShiftNoteWithAttachments(ShiftNote(UUID.nameUUIDFromBytes("note-${write.shift.id}".toByteArray()),
                write.shift.id, "Nota sintética Stage 4", timestamp, timestamp), emptyList())
        }
        store.v2Shifts.applyV2Batch(V2ShiftBatchMutation(shiftsToInsert = writes, notesToInsert = notes),
            ShiftOccupancyExpectation.capture(models.first().localStartDate, models.last().localStartDate, emptyList()))
    }

    private fun snapshot() = runBlocking { store.backups.capture() }
    private fun logicalDatasetHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
        }
        BackupPayloadCodec.writeDatabase(snapshot(), DigestOutputStream(sink, digest))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun launch() {
        assertQaFocus(allowOwnLifecycleTransition = true)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // Constrain only this disposable QA Activity, never Android's global settings.
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        awaitQaFocusAfterLifecycleChange()
        awaitCalendar(anchorMonth)
        scenario!!.onActivity {
            measuredWindows.put(JSONObject().put("width_px", it.window.decorView.width)
                .put("height_px", it.window.decorView.height)
                .put("android_font_scale", it.resources.configuration.fontScale.toDouble())
                .put("android_density_dpi", it.resources.displayMetrics.densityDpi)
                .put("requested_orientation", "portrait"))
            it.window.addOnFrameMetricsAvailableListener(frameListener, Handler(frameThread.looper))
        }
    }
    private fun closeActivity() {
        scenario?.onActivity { it.window.removeOnFrameMetricsAvailableListener(frameListener) }
        scenario?.close()
        scenario = null
    }
    private fun back() {
        assertQaFocus()
        // Deliver Back to the focused QA window. Calling the Activity dispatcher
        // bypasses a ModalBottomSheet's dialog window and can leave the Activity.
        pressBack()
        compose.waitForIdle()
        assertQaFocus()
    }
    private fun menu(tag: String) {
        assertQaFocus()
        compose.onNodeWithTag("main-menu-button").performClick()
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }
    private fun waitTag(tag: String) = compose.waitUntil(30_000) {
        assertQaFocus()
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun awaitState(predicate: (MainActivity) -> Boolean) = compose.waitUntil(30_000) {
        assertQaFocus()
        var ready = false
        scenario!!.onActivity { ready = predicate(it) }
        ready
    }
    private fun assertQaFocus(allowOwnLifecycleTransition: Boolean = false) {
        check(!focusLostInProcess) { "QA focus guard already stopped this process" }
        // Read only the active package metadata, never foreign text or screenshots.
        val active = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
        val launcher = if (allowOwnLifecycleTransition) context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName else null
        if (!(active == context.packageName || (allowOwnLifecycleTransition && active != null && active == launcher))) {
            stopForFocusLoss(active)
        }
    }
    private fun awaitQaFocusAfterLifecycleChange() {
        val launcher = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        compose.waitUntil(30_000) {
            val active = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
            check(!focusLostInProcess) { "QA focus guard already stopped this process" }
            if (!(active == null || active == context.packageName || active == launcher)) stopForFocusLoss(active)
            active == context.packageName
        }
    }

    private fun stopForFocusLoss(activePackage: String?): Nothing {
        focusLostInProcess = true
        val diagnostic = JSONObject()
            .put("monotonic_ns", System.nanoTime())
            .put("phase", measurementPhase).put("iteration_zero_based", measurementIteration)
            .put("category", if (activePackage == null) "no_active_window" else "foreign_package")
            // Package identity only: never read another application's text or capture its UI.
            .put("active_package_metadata", activePackage ?: JSONObject.NULL)
        runCatching {
            scenario?.onActivity {
                diagnostic.put("qa_window_has_focus", it.hasWindowFocus())
                    .put("qa_lifecycle", it.lifecycle.currentState.name)
            }
        }
        runCatching {
            val directory = File(context.filesDir, "stage4/focus-diagnostics").also { check(it.exists() || it.mkdirs()) }
            File(directory, "focus-${System.nanoTime()}.json").writeText(diagnostic.toString(2))
        }
        error("QA lost focus (${if (activePackage == null) "no active accessibility window" else "different package"}); driver stopped without reading or capturing another app")
    }

    private companion object {
        @Volatile var focusLostInProcess = false
    }
    private fun awaitCalendar(month: YearMonth) {
        awaitState { activity ->
            val state = ViewModelProvider(activity)[CalendarViewModel::class.java].uiState.value
            state.visibleMonth == month && state.loadState == CalendarLoadState.CONTENT &&
                state.days.size == month.lengthOfMonth() && state.days.all { YearMonth.from(it.date) == month }
        }
        waitTag("month-grid")
    }
}
