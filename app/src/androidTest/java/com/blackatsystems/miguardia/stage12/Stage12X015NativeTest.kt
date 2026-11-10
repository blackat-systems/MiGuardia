package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.view.View
import com.blackatsystems.miguardia.stage12.Stage12ActivityAdmission.X015Step as RouteStep
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.widget.*
import com.blackatsystems.miguardia.ui.calendar.*
import com.blackatsystems.miguardia.ui.nextevent.*
import com.blackatsystems.miguardia.ui.summary.*
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.widget.WidgetRemoteViewsRenderer
import com.blackatsystems.miguardia.widget.resolveWidgetPalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** This specific query route compares complete stores. It is not a prerequisite for ordinary QA use. */
class Stage12X015NativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as MiGuardiaApplication
    private val store get() = app.localDataStore
    private var scenario: ActivityScenario<MainActivity>? = null

    @Test fun realCalendarSummaryCardAndAppliedWidgetPreserveCompleteStores() {
        check(instrumentation is Stage12NormalQaRunner && context.packageName == "com.blackatsystems.miguardia.qa")
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("stage12CalendarClock") == "REAL")
        val run = requireNotNull(args.getString("stage12RunId")); val namespace = UUID.fromString(run)
        val ownId = UUID(0L, UUID.nameUUIDFromBytes("$namespace/x015/shift".toByteArray()).leastSignificantBits)
        val identity = JSONObject().put("run_uuid", run).put("qa_sha256", args.getString("stage12QaSha256"))
            .put("runner_sha256", args.getString("stage12RunnerSha256"))
        val admission = Stage12ActivityAdmission(false)
        (instrumentation as Stage12NormalQaRunner).beginReadiness(admission)
        admission.phase(Stage12ActivityAdmission.Phase.FILES)
        admission.x015Step(RouteStep.PREPARATION)
        val output = Stage12NativeReceiptFiles.prepareOutput(context.filesDir, run, identity.toString(), "x015-query-conservation")
        val facts = JSONArray()
        val receipt = JSONObject(identity.toString()).put("schema", "stage12-native-completion-v1").put("case", "X015")
            .put("status", "INCOMPLETE").put("facts", facts).put("technical_file_allowlist", JSONArray())
            .put("universal_qa_immutable_precondition", false).put("atomic_across_all_stores", false)
            .put("fixture_binding", "OWN_SHIFT_AND_FULL_TYPED_PREFERENCE_FILE_CANARIES_WITH_RECEIVED_CATALOGUE_REFS")
        var primary: Throwable? = null
        var received: Stage12StoreSnapshot? = null
        var inserted = false
        val canaryFile = File(context.filesDir, "stage12-x015-$namespace.bin")
        val preferenceName = "stage12_x015_$namespace"
        var canaryHash: String? = null
        var preferenceHash: String? = null
        var preferenceSetupStarted = false
        var lease: Stage12OwnedWidgetIntents? = null
        fun routeStep(value: RouteStep) { admission.x015Step(value) }
        fun fact(suffix: String, level: String) { facts.put(JSONObject().put("assertion_key", "X015-contrato-completo.$suffix")
            .put("check", "PASS").put("level", level)) }
        try {
            admission.phase(Stage12ActivityAdmission.Phase.LAUNCH)
            routeStep(RouteStep.LAUNCH)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            routeStep(RouteStep.READINESS)
            Stage12OwnActivityAdmission.awaitReady(requireNotNull(scenario), context, UiDevice.getInstance(instrumentation), admission)
            routeStep(RouteStep.CALENDAR_READY)
            compose.waitUntil(30_000) { calendar().loadState == CalendarLoadState.CONTENT }
            admission.phase(Stage12ActivityAdmission.Phase.BODY)
            routeStep(RouteStep.RECEIVED_READBACK)
            received = Stage12FullStoreReadback.capture(context)
            routeStep(RouteStep.FIXTURE_SETUP)
            val history = runBlocking { checkNotNull(store.workConfiguration.get()) }
            val all = runBlocking { history.timeline.revisions.map { it.value.sector }.distinct()
                .flatMap { store.v2Shifts.observeAll(history.timeline.id, it).first() } }
            val today = LocalDate.now(AppDefaults.zoneId())
            // Clone the earliest still-eligible received schedule into an owned identity.
            // Real-source projections below must actually contain it; EMPTY is never credit.
            var selected: Pair<LocalDate, V2ShiftWrite>? = null
            for (offset in 0L..31L) {
                val candidateDate = today.plusDays(offset)
                val revision = runCatching { ResolvedWorkConfigurationRevision.resolve(history, candidateDate) }.getOrNull() ?: continue
                if (runBlocking { store.explicitDayStatuses.observeBetween(candidateDate, candidateDate).first() }.isNotEmpty()) continue
                val candidate = runBlocking { all.sortedBy { it.shift.startTimeSnapshot }.firstOrNull { value ->
                    val endDate = if (value.shift.endTimeSnapshot > value.shift.startTimeSnapshot) candidateDate else candidateDate.plusDays(1)
                    value.snapshot.configurationRevisionId == revision.revision.id &&
                        endDate.atTime(value.shift.endTimeSnapshot).atZone(value.shift.zoneId).toInstant() > Instant.now().plusSeconds(300) &&
                        stage12TemplateEligible(store, value, candidateDate)
                } }
                if (candidate != null) { selected = candidateDate to candidate; break }
            }
            val (date, original) = checkNotNull(selected) { "X015_NO_ELIGIBLE_TEMPLATE_FOR_OWN_FIXTURE" }
            check(runBlocking { store.v2Shifts.getShift(ownId) } == V2ShiftLookup.Missing) { "X015_OWN_ID_EXISTS" }
            val start = date.atTime(original.shift.startTimeSnapshot).atZone(original.shift.zoneId).toInstant()
            val endDate = if (original.shift.endTimeSnapshot > original.shift.startTimeSnapshot) date else date.plusDays(1)
            val end = endDate.atTime(original.shift.endTimeSnapshot).atZone(original.shift.zoneId).toInstant()
            val write = V2ShiftWrite(original.shift.copy(id = ownId, localStartDate = date, startAt = start, endAt = end,
                position = "PRIVATE_SYNTHETIC_X015", status = ShiftStatus.PLANNED, createdAt = start, updatedAt = start), original.snapshot.copy(shiftId = ownId))
            runBlocking { store.v2Shifts.insert(write) }; inserted = true
            check(!canaryFile.exists()) { "X015_OWN_CANARY_EXISTS" }
            val bytes = "PRIVATE_SYNTHETIC_X015/$namespace".toByteArray(Charsets.UTF_8)
            Files.newOutputStream(canaryFile.toPath(), CREATE_NEW, WRITE).use { stream ->
                canaryHash = hash(bytes); stream.write(bytes)
            }
            val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$preferenceName.xml")
            check(!prefsFile.exists() && !File(prefsFile.path + ".bak").exists()) { "X015_OWN_PREFERENCES_EXISTS" }
            val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
            preferenceSetupStarted = true
            check(prefs.edit().putString("text", "PRIVATE_SYNTHETIC_X015").putStringSet("set", setOf("alpha", "beta"))
                .putBoolean("boolean", true).putInt("integer", 41).putLong("long", Long.MAX_VALUE)
                .putFloat("float", 1.25f).commit()) { "X015_CANARY_PREFERENCES_NOT_WRITTEN" }
            preferenceHash = hash(prefsFile.readBytes())
            // All fixture setup is outside the measured read-only route, and fully owned cleanup follows it.
            routeStep(RouteStep.BASELINE_READBACK)
            val baseline = Stage12FullStoreReadback.capture(context)
            receipt.put("baseline", metadata(baseline))
            val observedWrite = runBlocking { (store.v2Shifts.getShift(ownId) as V2ShiftLookup.V2).write }
            val observedRoles = mapOf("shift" to ownId.toString(), "namespace" to namespace.toString())
            receipt.put("fixture_observation", JSONObject(Stage12ObservedFixture.work("X015", today, original.shift.id,
                listOf(observedWrite), observedRoles, mapOf("owned_canary_normalized_sha256" to
                    Stage12ObservedFixture.fingerprint(canaryFile.readText(Charsets.UTF_8), observedRoles),
                    "owned_preferences_bytes_sha256" to checkNotNull(preferenceHash)))))
            routeStep(RouteStep.OPEN_DAY)
            goToMonth(YearMonth.from(date))
            compose.waitUntil(30_000) { calendar().days.any { day -> day.date == date && day.shifts.any { it.shift.id == ownId } } }
            val tag = if (compose.onAllNodesWithTag("day-$date").fetchSemanticsNodes().isNotEmpty()) "day-$date" else "completed-day-$date"
            compose.onNodeWithTag(tag).performClick()
            compose.waitUntil(30_000) { calendar().detailDate == date }
            check(calendar().days.single { it.date == date }.shifts.any { it.shift.id == ownId }) { "X015_CALENDAR_FIXTURE_NOT_READ" }
            routeStep(RouteStep.WAIT_DAY_SHEET)
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("day-detail-modal-sheet").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("day-detail-modal-sheet").assertIsDisplayed()
            // The modal owns a window. Send the real Back input to that window, as ordinary QA does.
            routeStep(RouteStep.BEFORE_DAY_BACK)
            admission.x015Back(
                acquireDevice = { UiDevice.getInstance(instrumentation) },
                readForeground = { device ->
                    when (device.currentPackageName) {
                        context.packageName -> Stage12ActivityAdmission.Foreground.OWN
                        null, "" -> Stage12ActivityAdmission.Foreground.NONE
                        else -> Stage12ActivityAdmission.Foreground.OTHER
                    }
                },
                readLifecycle = { Stage12ActivityAdmission.Life.valueOf(requireNotNull(scenario).state.name) },
                pressBack = { it.pressBack() },
            )
            routeStep(RouteStep.AFTER_DAY_BACK)
            routeStep(RouteStep.WAIT_DAY_DISMISSED)
            compose.waitUntil(30_000) { calendar().detailDate == null }
            compose.onNodeWithTag("day-detail-modal-sheet").assertDoesNotExist()
            check(requireNotNull(scenario).state == androidx.lifecycle.Lifecycle.State.RESUMED) { "X015_ACTIVITY_NOT_RESUMED_AFTER_DAY_BACK" }
            routeStep(RouteStep.DAY_DISMISSED)
            routeStep(RouteStep.NEXT_EVENT)
            compose.onNodeWithTag("next-event-card").assertExists()
            compose.waitUntil(30_000) { state<NextEventViewModel, NextEventUiState> { it.uiState.value }.loadState == NextEventLoadState.CONTENT }
            compose.waitUntil(30_000) {
                val card = state<NextEventViewModel, NextEventUiState> { it.uiState.value }.result
                card != null && (card.shifts.any { it.event.shiftId == ownId } ||
                    card.futureEvent.events.any { (it as? com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem.Shift)?.shiftId == ownId })
            }
            routeStep(RouteStep.SUMMARY)
            navigate("summary")
            compose.waitUntil(30_000) { state<SummaryViewModel, SummaryUiState> { it.uiState.value }.loadState == SummaryLoadState.CONTENT }
            var steps = 0
            while (state<SummaryViewModel, SummaryUiState> { it.uiState.value }.visibleMonth != YearMonth.from(date)) {
                check(steps++ < 36) { "X015_SUMMARY_MONTH_BOUND" }
                val before = state<SummaryViewModel, SummaryUiState> { it.uiState.value }.visibleMonth
                compose.onNodeWithTag(if (before < YearMonth.from(date)) "summary-next-month" else "summary-previous-month").performClick()
                compose.waitUntil(30_000) { state<SummaryViewModel, SummaryUiState> { it.uiState.value }.visibleMonth != before &&
                    state<SummaryViewModel, SummaryUiState> { it.uiState.value }.loadState == SummaryLoadState.CONTENT }
            }
            compose.onNodeWithTag("summary-overview").assertExists()
            compose.waitUntil(30_000) {
                val projection = state<SummaryViewModel, SummaryUiState> { it.uiState.value }.projection
                projection?.essentials?.pendingScheduled?.contributions?.any { it.sourceId == ownId.toString() } == true ||
                    projection?.optionalSections?.any { section -> section.metrics.any { metric ->
                        metric.contributions.any { it.sourceId == ownId.toString() }
                    } } == true
            }
            routeStep(RouteStep.RETURN_CALENDAR)
            navigate("calendar")
            compose.waitUntil(30_000) { calendar().loadState == CalendarLoadState.CONTENT }
            routeStep(RouteStep.WIDGET_SOURCE)
            val sources = V2WorkEventSourceObserver(store.v2Shifts, store.availabilityWindows, store.shiftActuals,
                store.independentExtraWork, store.explicitDayStatuses, store.vacations, store.medicalLeaves, store.workConfiguration)
            val sourceData = runBlocking { withTimeout(30_000) { sources.observe(today).first() } }
            check(sourceData.shifts.any { it.shift.id == ownId }) { "X015_WIDGET_REAL_SOURCE_OMITS_FIXTURE" }
            val result = projectNextEvent(Instant.now(), AppDefaults.zoneId(), sourceData.toInput())
            val shifts = sourceData.shifts.map { it.shift.id }.toSet()
            val dates = sourceData.explicitDayStatuses.map { it.date }.toSet()
            lease = Stage12OwnedWidgetIntents(context, namespace, shifts, dates)
            val renderer = WidgetRemoteViewsRenderer(context)
            routeStep(RouteStep.WIDGET_APPLY)
            for (size in WidgetSize.entries) {
                val mode = if (start <= result.referenceInstant) WidgetMode.AUTOMATIC else WidgetMode.NEXT_SHIFT
                val projection = projectWidget(result, WidgetProjectionConfig(mode, WidgetPrivacy.COMPLETE, size, true))
                check(projection.events.any { (it.identity as? com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity.Shift)?.shiftId == ownId } &&
                    (projection.navigation as? WidgetNavigation.Shift)?.shiftId == ownId) { "X015_WIDGET_OWN_FIXTURE_NOT_PROJECTED" }
                val views = renderer.createViews(projection, lease.widgetId, null, resolveWidgetPalette(AppThemeMode.LIGHT, false))
                lease.retainCreated()
                Stage12SummaryObservation.onMain({ action -> instrumentation.runOnMainSync(action) }) {
                    val view = Stage12AppliedRemoteViews.apply(context, views)
                    var failure: Throwable? = null
                    try { check(view.id == com.blackatsystems.miguardia.R.id.widget_root && view.hasOnClickListeners() &&
                        Stage12W010AppliedInspector.allText(view).any { it.contains("PRIVATE_SYNTHETIC_X015") }) { "X015_APPLIED_WIDGET_OWN_FIXTURE_MISSING" } }
                    catch (error: Throwable) { failure = error; throw error }
                    finally { try { Stage12AppliedRemoteViews.release(view) } catch (error: Throwable) {
                        if (failure == null) throw error else Stage12W010Oracle.retain(failure, error)
                    } }
                }
            }
            routeStep(RouteStep.AFTER_READBACK)
            val after = Stage12FullStoreReadback.capture(context)
            receipt.put("after", metadata(after)).put("applied_sizes", 2)
            routeStep(RouteStep.CONSERVATION)
            check(baseline.tables == after.tables) { "X015_QUERY_CHANGED_COMPLETE_ROOM" }; fact("room_isolated", "ROOM_ISOLATED")
            check(baseline.preferences == after.preferences) { "X015_QUERY_CHANGED_FULL_PREFERENCES" }
            check(baseline.files == after.files) { "X015_QUERY_CHANGED_PRIVATE_FILES" }; fact("files_isolated", "FILES_ISOLATED")
            fact("activity_native", "ACTIVITY_NATIVE"); fact("remote_views_real", "REMOTE_VIEWS_REAL")
        } catch (error: Throwable) {
            // The runner publishes only the enum through its existing safe projection.
            admission.failed(error)
            primary = error
            receipt.put("failure_class", error.javaClass.simpleName).put("last_route_step", admission.x015Step()?.name ?: "UNKNOWN")
        }
        finally {
            fun cleanup(step: RouteStep, action: () -> Unit) {
                routeStep(step)
                try { action() } catch (error: Throwable) {
                    admission.failed(error); primary = Stage12W010Oracle.retain(primary, error)
                }
            }
            cleanup(RouteStep.CLEANUP_WIDGET) { lease?.retainCreated() }; cleanup(RouteStep.CLEANUP_WIDGET) { lease?.close() }
            admission.phase(Stage12ActivityAdmission.Phase.CLOSE)
            cleanup(RouteStep.CLOSE_ACTIVITY) { scenario?.close(); scenario = null }
            admission.phase(Stage12ActivityAdmission.Phase.POSTCONDITION)
            cleanup(RouteStep.CLEANUP_SHIFT) { if (inserted) runBlocking {
                val current = store.v2Shifts.getShift(ownId)
                check(current is V2ShiftLookup.V2 && current.write.shift.position == "PRIVATE_SYNTHETIC_X015") { "X015_OWN_FIXTURE_CHANGED" }
                store.v2Shifts.deleteShift(current.write, store.shiftActuals.getExpectation(ownId))
            } }
            cleanup(RouteStep.CLEANUP_CANARY) { canaryHash?.let { check(canaryFile.isFile && hash(canaryFile.readBytes()) == it) { "X015_OWN_CANARY_CHANGED" }; check(canaryFile.delete()) } }
            cleanup(RouteStep.CLEANUP_PREFERENCES) { preferenceHash?.let {
                val file = File(context.applicationInfo.dataDir, "shared_prefs/$preferenceName.xml")
                check(file.isFile && hash(file.readBytes()) == it) { "X015_OWN_PREFERENCES_CHANGED" }
                check(context.deleteSharedPreferences(preferenceName)) { "X015_OWN_PREFERENCES_DELETE_FAILED" }
            } }
            if (preferenceSetupStarted && preferenceHash == null) {
                receipt.put("partial_owned_preference_retained", preferenceName)
                cleanup(RouteStep.CLEANUP_PREFERENCES) { error("X015_PARTIAL_OWN_PREFERENCE_REQUIRES_REVIEW") }
            }
            cleanup(RouteStep.RESTORED_READBACK) { received?.let {
                val restored = Stage12FullStoreReadback.capture(context)
                check(it.tables == restored.tables && it.preferences == restored.preferences && it.files == restored.files) {
                    "X015_RECEIVED_COMPLETE_STORES_NOT_CONSERVED"
                }
            } }
            receipt.put("cleanup_complete", primary == null).put("status", if (primary == null) "COMPLETE" else "FAILED")
            admission.phase(Stage12ActivityAdmission.Phase.PUBLISH)
            routeStep(RouteStep.PUBLISH)
            try { Stage12NativeReceiptFiles.publish(output, receipt.toString(2), primary) }
            catch (error: Throwable) { admission.failed(error); primary = Stage12W010Oracle.retain(primary, error) }
            if (primary == null) routeStep(RouteStep.COMPLETE)
            admission.finish(primary == null)
        }
        primary?.let { throw it }
    }

    private inline fun <reified VM : androidx.lifecycle.ViewModel, T> state(crossinline read: (VM) -> T): T {
        var value: T? = null
        requireNotNull(scenario).onActivity { value = read(ViewModelProvider(it)[VM::class.java]) }
        return requireNotNull(value)
    }
    private fun calendar() = state<CalendarViewModel, CalendarUiState> { it.uiState.value }
    private fun navigate(destination: String) {
        compose.onNodeWithTag("main-menu-button").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("main-destination-$destination").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("main-destination-$destination").performScrollTo().performClick()
    }
    private fun goToMonth(month: YearMonth) {
        var count = 0
        while (calendar().visibleMonth != month) {
            check(count++ < 36) { "X015_CALENDAR_MONTH_BOUND" }
            val before = calendar().visibleMonth
            compose.onNodeWithContentDescription(if (before < month) "Mes siguiente" else "Mes anterior").performClick()
            compose.waitUntil(30_000) { calendar().visibleMonth != before && calendar().loadState == CalendarLoadState.CONTENT }
        }
    }
    private fun metadata(value: Stage12StoreSnapshot) = JSONObject().put("table_count", value.tables.size)
        .put("tables_sha256", hash(value.tables.toString().toByteArray())).put("preference_entries", value.preferences.size)
        .put("preferences_sha256", hash(value.preferences.toString().toByteArray()))
        .put("file_count", value.files.values.count { it.kind == Stage12PrivateNodeKind.FILE })
        .put("inventory_entries", value.files.size)
        .put("files_sha256", hash(value.files.toString().toByteArray()))
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
