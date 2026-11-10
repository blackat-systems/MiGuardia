package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadStage
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadUiState
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth

/** Durable synthetic baseline through ordinary UI. Never resets or seeds received stores. */
class Stage12AospWorkFixtureTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as MiGuardiaApplication
    private val store get() = app.localDataStore
    private var scenario: ActivityScenario<MainActivity>? = null

    @Test fun createsMinimalWorkThroughNormalUiAndExplicitBackfill() {
        check(instrumentation is Stage12NormalQaRunner && context.packageName == "com.blackatsystems.miguardia.qa")
        check(Stage12RuntimeTarget.identify(android.os.Build.VERSION.SDK_INT, android.os.Build.MODEL,
            android.os.Build.MANUFACTURER, android.os.Build.ID, android.os.Build.FINGERPRINT,
            android.os.Build.SUPPORTED_ABIS.firstOrNull()) == Stage12RuntimeTarget.Target.AOSP_STAGE12)
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("stage12CalendarClock") == "REAL")
        val run = requireNotNull(args.getString("stage12RunId"))
        val identity = JSONObject().put("run_uuid", run).put("qa_sha256", args.getString("stage12QaSha256"))
            .put("runner_sha256", args.getString("stage12RunnerSha256"))
        val output = Stage12NativeReceiptFiles.prepareOutput(context.filesDir, run, identity.toString(), "aosp-work-fixture")
        val plan = Stage12AospFixturePlan(LocalDate.now(AppDefaults.zoneId()))
        val receipt = JSONObject(identity.toString()).put("schema", "stage12-aosp-work-fixture-v1")
            .put("environment", "AOSP_API36_X86_64").put("clock", "REAL").put("anchor_date", plan.anchor.toString())
            .put("status", "INCOMPLETE").put("fixture_credit_only", true).put("final_round_credit", false)
            .put("writes_via_normal_UI_only", true).put("baseline_retained", true)
            .put("restored_to_empty_install", false).put("source_dates", JSONArray(plan.sourceDates.sorted().map(LocalDate::toString)))
        val admission = Stage12ActivityAdmission(false)
        (instrumentation as Stage12NormalQaRunner).beginReadiness(admission)
        var primary: Throwable? = null
        var phase = "READINESS"
        var writesStarted = false
        var backfills = 0
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            Stage12OwnActivityAdmission.awaitReady(requireNotNull(scenario), context, UiDevice.getInstance(instrumentation), admission)
            waitTag("work-sector-selection")
            phase = "EMPTY_WORK_PRECONDITION"
            val received = Stage12FullStoreReadback.capture(context)
            Stage12AospFixturePlan.requireEmptyWork(runBlocking { store.workConfiguration.get() } != null,
                received.tables.mapValues { it.value.rows.size })
            receipt.put("received", metadata(received)).put("empty_work_observed", true)
            admission.phase(Stage12ActivityAdmission.Phase.BODY)
            plan.requireSameDay(LocalDate.now(AppDefaults.zoneId()))
            phase = "NORMAL_WORK_SETUP"
            writesStarted = true
            click("work-sector-private_security", scroll = true);click("work-sector-continue", scroll = true)
            waitText("Crear primer lugar");compose.onNodeWithText("Crear primer lugar").performScrollTo().performClick()
            fill("work-place-name", Stage12AospFixturePlan.PLACE_NAME)
            fill("work-place-abbreviation", Stage12AospFixturePlan.ABBREVIATION)
            click("work-place-continue", scroll = true)
            fill("work-time-start", "0900");fill("work-time-end", "1700")
            click("work-template-color", scroll = true);waitText("Usar color");compose.onNodeWithText("Usar color").performClick()
            click("work-set-save", scroll = true);waitText("Volver al Calendario")
            compose.onNodeWithText("Volver al Calendario").performScrollTo().performClick()
            compose.waitUntil(30_000) { exists("help-skip") || exists("month-grid") }
            if (exists("help-skip")) { click("help-skip");click("help-confirm-skip") }
            waitTag("month-grid")
            compose.waitUntil(30_000) { calendar().loadState == CalendarLoadState.CONTENT }
            val history = runBlocking { checkNotNull(store.workConfiguration.get()) }
            val catalog = runBlocking { store.workCatalog.observeCatalog(history.timeline.id, WorkSector.PRIVATE_SECURITY).first() }
            val place = catalog.workPlaces.single();val template = catalog.workTemplates.single();val type = catalog.workTypes.single()
            check(template.startTime == plan.start && template.endTime == plan.end && template.workPlaceId == place.id)
            receipt.put("timeline_id", history.timeline.id.toString()).put("place_id", place.id.toString())
                .put("template_id", template.id.toString()).put("type_id", type.id.toString())
                .put("sector", WorkSector.PRIVATE_SECURITY.name).put("start", plan.start.toString()).put("end", plan.end.toString())
                .put("color_argb", template.colorArgb)
            for (dates in plan.batches) {
                plan.requireSameDay(LocalDate.now(AppDefaults.zoneId()))
                phase = "NORMAL_MANUAL_LOAD"
                goToMonth(YearMonth.from(dates.min()))
                click("main-menu-button");click("drawer-action-calendar-load")
                dates.sorted().forEach { click("day-$it") }
                click("v2-manual-confirm-dates");click("manual-institutions", scroll = true)
                click("manual-place-${place.id}", scroll = true);click("v2-template-${template.id}", scroll = true)
                click("manual-toggle-position", scroll = true);fill("v2-manual-position", Stage12AospFixturePlan.POSITION)
                click("v2-manual-review", scroll = true)
                compose.waitUntil(30_000) { manual().stage in setOf(V2ManualShiftLoadStage.CONFIRM_BACKFILL, V2ManualShiftLoadStage.REVIEW) || manual().errorMessage != null }
                check(manual().errorMessage == null) { "AOSP_FIXTURE_REVIEW_REJECTED" }
                if (manual().stage == V2ManualShiftLoadStage.CONFIRM_BACKFILL) {
                    check(plan.pastSource in dates && backfills == 0) { "AOSP_FIXTURE_UNEXPECTED_BACKFILL" }
                    phase = "EXPLICIT_NORMAL_BACKFILL"
                    waitTag("v2-backfill-dialog");waitText("Usar desde esa fecha")
                    compose.onNodeWithText("Usar desde esa fecha").performClick();backfills++
                    compose.waitUntil(30_000) { manual().stage == V2ManualShiftLoadStage.REVIEW || manual().errorMessage != null }
                }
                check(manual().errorMessage == null && manual().plannedDates == dates && manual().omittedDates.isEmpty() && manual().occupiedDates.isEmpty()) {
                    "AOSP_FIXTURE_REVIEW_DATES_OR_OCCUPANCY_DIFFER"
                }
                plan.requireSameDay(LocalDate.now(AppDefaults.zoneId()))
                click("v2-manual-save", scroll = true)
                compose.waitUntil(30_000) { manual().stage == V2ManualShiftLoadStage.IDLE || manual().errorMessage != null }
                check(manual().errorMessage == null && !manual().isSaving) { "AOSP_FIXTURE_SAVE_REJECTED" }
            }
            phase = "OBSERVED_FIXTURE_BINDINGS"
            plan.requireSameDay(LocalDate.now(AppDefaults.zoneId()))
            check(backfills == 1) { "AOSP_FIXTURE_EXPLICIT_BACKFILL_MISSING" }
            val finalHistory = runBlocking { checkNotNull(store.workConfiguration.get()) }
            val sources = runBlocking { store.v2Shifts.observeAll(finalHistory.timeline.id, WorkSector.PRIVATE_SECURITY).first() }
            plan.requireExactSources(sources.map { it.shift.localStartDate })
            check(sources.all { it.shift.status == ShiftStatus.PLANNED && it.shift.position == Stage12AospFixturePlan.POSITION &&
                it.snapshot.templateId == template.id && it.snapshot.workPlaceId == place.id && it.snapshot.workTypeId == type.id })
            val past = sources.single { it.shift.localStartDate == plan.pastSource }
            val future = sources.single { it.shift.localStartDate == plan.futureSource }
            val pastRevision = ResolvedWorkConfigurationRevision.resolve(finalHistory, plan.c090EmptyDate)
            val currentRevision = ResolvedWorkConfigurationRevision.resolve(finalHistory, plan.anchor)
            check(past.snapshot.configurationRevisionId == pastRevision.revision.id &&
                future.snapshot.configurationRevisionId == currentRevision.revision.id) { "AOSP_FIXTURE_REVISION_BINDING_DIFFERS" }
            check(runBlocking { stage12TemplateEligible(store, past, plan.c090EmptyDate) && stage12TemplateEligible(store, future, plan.anchor) }) {
                "AOSP_FIXTURE_CATALOGUE_OR_RULE_NOT_ELIGIBLE"
            }
            check(runBlocking { store.explicitDayStatuses.observeBetween(plan.pastSource, plan.futureSource).first() }.isEmpty())
            receipt.put("source_past_id", past.shift.id.toString()).put("source_future_id", future.shift.id.toString())
                .put("past_revision_id", pastRevision.revision.id.toString()).put("current_revision_id", currentRevision.revision.id.toString())
                .put("c090_empty_date", plan.c090EmptyDate.toString()).put("backfill_confirmations", backfills)
                .put("monthly_batches", JSONArray(plan.batches.map { JSONArray(it.sorted().map(LocalDate::toString)) }))
                .put("C090_data_preconditions_observed", true).put("X015_data_preconditions_observed", true)
        } catch (error: Throwable) {
            primary = error;admission.failed(error)
            receipt.put("failure_class", error.javaClass.simpleName).put("failed_phase", phase)
        } finally {
            primary = Stage12AospFixturePlan.finishPreparation(admission, primary,
                close = { scenario?.close();scenario = null },
                readback = { receipt.put("prepared_baseline", metadata(Stage12FullStoreReadback.capture(context))) },
                publish = { failure ->
                    receipt.put("normal_writes_started", writesStarted).put("partial_baseline_requires_review", failure != null && writesStarted)
                        .put("activity_closed", scenario == null).put("status", if (failure == null) "COMPLETE" else "FAILED")
                    Stage12NativeReceiptFiles.publish(output, receipt.toString(2), failure)
                })
        }
        primary?.let { throw it }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String) = compose.waitUntil(30_000) { exists(tag) }
    private fun waitText(text: String) = compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun click(tag: String, scroll: Boolean = false) {
        waitTag(tag);val node = compose.onNodeWithTag(tag)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun fill(tag: String, text: String) {
        waitTag(tag);compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
        requireNotNull(scenario).onActivity { activity ->
            activity.currentFocus?.clearFocus()
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
    }
    private inline fun <reified VM : androidx.lifecycle.ViewModel, T> state(crossinline read: (VM) -> T): T {
        var value: T? = null
        requireNotNull(scenario).onActivity { value = read(ViewModelProvider(it)[VM::class.java]) }
        return requireNotNull(value)
    }
    private fun calendar(): CalendarUiState = state<CalendarViewModel, CalendarUiState> { it.uiState.value }
    private fun manual(): V2ManualShiftLoadUiState = state<V2ManualShiftLoadViewModel, V2ManualShiftLoadUiState> { it.uiState.value }
    private fun goToMonth(month: YearMonth) {
        var count = 0
        while (calendar().visibleMonth != month) {
            check(count++ < 3) { "AOSP_FIXTURE_MONTH_BOUND" }
            val before = calendar().visibleMonth
            compose.onNodeWithContentDescription(if (before < month) "Mes siguiente" else "Mes anterior").performClick()
            compose.waitUntil(30_000) { calendar().visibleMonth != before && calendar().loadState == CalendarLoadState.CONTENT }
        }
    }
    private fun metadata(value: Stage12StoreSnapshot) = JSONObject()
        .put("tables_sha256", hash(value.tables.toString())).put("table_count", value.tables.size)
        .put("preferences_sha256", hash(value.preferences.toString())).put("preference_entries", value.preferences.size)
        .put("files_sha256", hash(value.files.toString())).put("inventory_entries", value.files.size)
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
