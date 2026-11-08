package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.lifecycle.Lifecycle
import androidx.activity.ComponentActivity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.work.*
import com.blackatsystems.miguardia.core.domain.nextevent.*
import com.blackatsystems.miguardia.core.domain.widget.*
import com.blackatsystems.miguardia.ui.nextevent.NextEventLoadState
import com.blackatsystems.miguardia.ui.nextevent.NextEventUiState
import com.blackatsystems.miguardia.ui.availability.*
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadStage
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Absolute temporal inputs, real product gestures, complete own-store deltas; no process-death credit. */
class Stage12TemporalLiteralAndroidTest {
    private val trace = Stage12SelectionDiagnostics.Trace()
    private val firstNegative = Stage12TemporalFailure { trace.retain(it) }
    private fun retain(error: Throwable): Throwable = firstNegative.remember(error)
    private val compose by lazy { trace.checked(Stage12SelectionDiagnostics.Checkpoint.UNMARKED) { createEmptyComposeRule() } }
    private var receipt: JSONObject? = null
    private var output: File? = null
    private var cleanup: (() -> Unit)? = null
    @get:Rule val rules: TestRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                trace.finish({ compose.apply(base, description).evaluate() }, { checkNotNull(cleanup).invoke() }, { succeeded, cleaned, primary ->
                    receipt?.let { value ->
                        value.put("rule_evaluation_succeeded", succeeded).put("receipt_completion_scope", "WHOLE_TEST_RULE")
                            .put("cleanup_complete", cleaned).put("owned_resources_retained_on_failure", !cleaned)
                            .put("diagnostic", JSONObject(trace.projection())).put("phase", phase)
                            .put("sessions", JSONArray(sessions.map { it.projection() }))
                            .put("status", if (primary == null && cleaned) "COMPLETE" else "FAILED")
                        Stage12NativeReceiptFiles.publish(checkNotNull(output), value.toString(2), primary)
                    }
                })
                trace.primary?.let { original ->
                    val selector = "${description.className}#${description.methodName}"
                    if (selector == Stage12C026PrivateFailure.SELECTOR) {
                        try { (instrumentation as? Stage12NormalQaRunner)?.captureC026Failure(selector, original) }
                        catch (_: Throwable) { /* Keep the public failure sanitized even if capture cannot run. */ }
                        throw Stage12C026PrivateFailure.publicFailure()
                    }
                    throw Stage12SelectionDiagnostics.publicFailure()
                }
            }
        }
    }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val clock = ObservedRealClock(Clock.system(Stage12TemporalLiteralOracle.zone))
    private lateinit var run: String
    private var phase = "ENTRY"
    private val facts = JSONArray()
    private val readbacks = JSONArray()
    private val textObservations = JSONArray()
    private val backEvents = JSONArray()
    private val cases = mutableListOf<Stage12TemporalCase>()
    private val sessions = mutableListOf<Session>()
    private var current: Session? = null
    private val ownSession get() = checkNotNull(current)
    private val fixture get() = ownSession.case.fixture
    private val owner get() = checkNotNull(ownSession.owner)
    private val activity: ComponentActivity get() = checkNotNull(ownSession.mainOwner?.activity ?: owner.activity)
    private val c get() = (ownSession.mainOwner?.calendar ?: owner.calendar).uiState.value
    private val m get() = owner.manual.uiState.value
    private val a get() = (ownSession.mainOwner?.availability ?: owner.availability).uiState.value
    private val store get() = ownSession.case.opened.requireBinding(ownSession.case.binding)
    private val oracle get() = Stage12RecurringReadbackOracle

    private class Session(val case: Stage12TemporalCase) {
        var resources: Stage12TemporalResources? = null
        var owner: Stage12TemporalComponentOwner? = null
        var mainOwner: Stage12TemporalMainOwner? = null
        var closeAttempted = false
        var closed = false
        fun projection() = JSONObject().put("scope", case.scope.wire).put("close_attempted", closeAttempted).put("closed", closed)
            .put("owner_observed", owner?.activity != null).put("owner_closure", owner?.closureProven ?: false)
            .put("auxiliary_closure", resources?.closureProven ?: false)
            .put("main_owner_observed", mainOwner?.activity != null).put("main_closure", mainOwner?.closureProven ?: false)
            .put("resources_preparation", resources?.let { JSONObject(it.projection) } ?: JSONObject.NULL)
            .put("main_activity_observation", mainOwner?.projection() ?: JSONObject.NULL)
            .put("preparation", JSONObject(case.preparation.projection())).put("fixture_prefix", JSONObject(case.fixturePrefix.projection()))
    }

    @Test fun shiftEndpointsAndHistoricalDetailStayExact() = family("temporal-shift-endpoints") {
        for (variant in listOf(Stage12TemporalVariant.SAME_DAY, Stage12TemporalVariant.OVERNIGHT,
            Stage12TemporalVariant.TWENTY_FOUR_HOURS, Stage12TemporalVariant.YEAR_CROSSING_SHIFT, Stage12TemporalVariant.HISTORICAL)) {
            start(Stage12TemporalScope(variant))
            val target = createShift()
            var expected = oracle.expected(fixture.initialExpected, inserted = oracle.write(target))
            exact("target-written", expected)
            inspectShift(target)
            closeDay()
            if (variant == Stage12TemporalVariant.HISTORICAL) {
                expected = changeHistoricalCatalog(expected, target)
                inspectShift(target)
                closeDay()
            }
            remount(expected)
            inspectShift(target)
            closeDay()
            exact("target-detail-returned-after-reopen", expected)
            inspectCompact(expected, targetShift = target)
            fact(fixture.recipe.literal.key + ".activity_native", "ACTIVITY_NATIVE",
                JSONObject().put("owner_date", target.shift.localStartDate).put("target_uuid", target.shift.id)
                    .put("store_reopened", true).put("new_owner", true).put("return_observed", true))
            closeSession(ownSession)
        }
    }

    @Test fun availabilityDurationsKeepFullEndpointsAndReadback() = family("temporal-availability-endpoints") {
        val scopes = listOf(Stage12TemporalVariant.TWENTY_FIVE_HOURS, Stage12TemporalVariant.SEVERAL_DAYS,
            Stage12TemporalVariant.MONTH_CROSSING, Stage12TemporalVariant.YEAR_CROSSING_WINDOW).map(::Stage12TemporalScope) +
            Stage12TemporalScope(Stage12TemporalVariant.TWENTY_FIVE_HOURS, Stage12TemporalFixtureRole.ONE_HOUR_COMPARATOR)
        val observedMinutes = linkedMapOf<String, Long>()
        for (scope in scopes) {
            start(scope)
            val target = createWindow()
            val expected = oracle.expected(fixture.initialExpected, inserted = listOf(oracle.availability(target)))
            exact("window-written", expected)
            inspectWindow(target)
            closeDay()
            remount(expected)
            inspectWindow(target)
            closeDay()
            exact("window-returned-after-reopen", expected)
            inspectCompact(expected, targetWindow = target)
            observedMinutes[scope.wire] = (target.end.toEpochMilli() - target.start.toEpochMilli()) / 60_000
            if (scope.role == Stage12TemporalFixtureRole.PRIMARY)
                fact(scope.variant.key + ".room_isolated", "ROOM_ISOLATED", JSONObject().put("all_tables_preferences_files", true)
                    .put("same_paths_reopened", true).put("target_uuid", target.id)
                    .put("proof_scope", "OWN_STORE_ONLY").put("received_state", "NOT_CERTIFIED"))
            fact(fixture.recipe.literal.key + ".activity_native", "ACTIVITY_NATIVE", JSONObject().put("target_uuid", target.id)
                .put("owner_date", target.ownerLocalDate).put("store_reopened", true).put("new_owner", true)
                .put("return_observed", true).put("route", "REAL_COMPONENT_ACTIVITY_DAY_CREATE_REOPEN_RETURN"))
            closeSession(ownSession)
        }
        check(observedMinutes.getValue(Stage12TemporalVariant.TWENTY_FIVE_HOURS.wire) == 1500L)
        check(observedMinutes.getValue(Stage12TemporalScope(Stage12TemporalVariant.TWENTY_FIVE_HOURS,
            Stage12TemporalFixtureRole.ONE_HOUR_COMPARATOR).wire) == 60L)
        check(observedMinutes.values.count { it == 1500L } == 1)
    }

    @Test fun availabilityResumptionKeepsOwnerAndUuidThroughRealWidgetTouch() = family("temporal-resumption-widget") {
        start(Stage12TemporalScope(Stage12TemporalVariant.RESUMPTION))
        val target = createWindow()
        val expected = oracle.expected(fixture.initialExpected, inserted = listOf(oracle.availability(target)))
        exact("resumption-window-written", expected)
        inspectWindow(target); closeDay()
        val original = ownSession
        val previousActivity = checkNotNull(owner.activity)
        val previousStore = original.case.opened
        closeSession(original)
        original.case.reopenClosedStore(context)
        check(original.case.opened !== previousStore && previousActivity.isDestroyed)
        val session = Session(original.case); sessions += session; current = session
        val main = Stage12TemporalMainOwner(context, session.case, ::retain, ::active); session.mainOwner = main
        val resources = Stage12TemporalResources(context, session.case.opened, run, session, UUID.randomUUID(), ::retain,
            observe = main::event, incoming = main::incoming, published = main::published)
        session.resources = resources; main.attach(resources)
        db { resources.prepare(clock, Stage12TemporalLiteralOracle.zone, mainReceiver = true) }
        exact("resumption-reopened-before-widget", expected)
        val recipe = fixture.recipe
        val input = db {
            val shifts = recipe.shifts.map { expectedShift ->
                val read = store.v2Shifts.getShift(expectedShift.shift.id)
                check(read == V2ShiftLookup.V2(expectedShift)); (read as V2ShiftLookup.V2).write
            }
            val windows = listOf(recipe.neighbourWindow, target).map { expectedWindow ->
                checkNotNull(store.availabilityWindows.get(expectedWindow.id)).also { check(it == expectedWindow) }
            }
            NextEventInput(shifts, windows, emptyMap(), emptyList(), emptyList(), emptyList(), emptyList())
        }
        val reference = Instant.parse("2026-10-24T11:00:00Z")
        val next = projectNextEvent(reference, recipe.zone, input)
        val resumed = next.primaryEvents.single()
        check(resumed is NextEventItem.Availability && resumed.windowId == target.id && resumed.ownerLocalDate == target.ownerLocalDate &&
            resumed.start == reference && resumed.end == Instant.parse("2026-10-24T15:00:00Z") && resumed.isResumption)
        val projection = projectWidget(next, WidgetProjectionConfig(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE, WidgetSize.EXPANDED, true))
        check(projection.events.single().identity == NextEventIdentity.Availability(target.id, reference, Instant.parse("2026-10-24T15:00:00Z")) &&
            projection.events.single().details?.isResumption == true && projection.navigation == WidgetNavigation.Date(target.ownerLocalDate))
        main.reserve()
        phase = "REAL_WIDGET_ORIGINAL_PENDING_INTENT"
        main.showAndTouch(projection)
        await { main.activity?.lifecycle?.currentState == Lifecycle.State.RESUMED && main.incoming != null && shown("day-detail-modal-sheet") }
        main.captureCreatedModels()
        await { c.loadState == CalendarLoadState.CONTENT && a.loadState == AvailabilityLoadState.CONTENT && c.detailDate == target.ownerLocalDate && !c.stage2Busy }
        check(main.activity !== previousActivity)
        main.gestures.getJSONObject(0).put("transition_observed", true).put("owner_date", c.detailDate)
        inspectWindow(target)
        exact("main-real-owner-day-read-only", expected)
        physicalRouteTouch("day-other-options", main.gestures)
        waitTag("availability-correct-${target.id}")
        check(target.id != recipe.neighbourWindow.id && target.ownerLocalDate.toString() == "2026-10-23")
        physicalRouteTouch("availability-correct-${target.id}", main.gestures)
        await { a.surface == AvailabilitySurface.WINDOW_EDITOR && a.windowDraft != null && !a.isSaving }
        val draft = checkNotNull(a.windowDraft)
        check(draft.recordId == target.id && draft.recordId != recipe.neighbourWindow.id && draft.ownerDate == LocalDate.of(2026, 10, 23) &&
            draft.startTime == "08:00" && draft.endDate == "2026-10-24" && draft.endTime == "12:00" && draft.labelOverride == null)
        val fingerprint = listOf(target.id, target.timelineId, target.sector, target.configurationRevisionId, target.ownerLocalDate,
            target.zoneId.id, target.start, target.end, target.labelSnapshot, target.createdAt, target.updatedAt).joinToString("|")
        var observedFingerprint: String? = null
        instrumentation.runOnMainSync { observedFingerprint = main.graph.availabilitySavedState(checkNotNull(main.activity), main.availability)
            .get<String>("availability.window.original") }
        check(observedFingerprint == fingerprint)
        main.gestures.getJSONObject(1).put("transition_observed", true)
        main.gestures.getJSONObject(2).put("transition_observed", true).put("target_uuid", draft.recordId)
        check(main.gestures.length() == 3)
        exact("main-exact-editor-fingerprint-read-only", expected)
        val back = JSONObject().put("kind", "EDITOR_BACK_TO_CONSCIOUS_DISCARD").put("input_entered", true)
        backEvents.put(back); back.put("api_return", device.pressBack()).put("input_returned", true)
        await { a.showDiscardConfirmation && shown("availability-discard-confirm") }
        touch("availability-discard-confirm")
        await {
            a.surface == AvailabilitySurface.NONE && !a.showDiscardConfirmation && a.windowDraft == null &&
                a.windowReview == null && c.detailDate == target.ownerLocalDate && shown("day-detail-modal-sheet") &&
                compose.onNodeWithTag("day-detail-modal-sheet").isDisplayed()
        }
        instrumentation.runOnMainSync { check(!main.graph.availabilitySavedState(checkNotNull(main.activity), main.availability)
            .contains("availability.window.original")) }
        back.put("transition_observed", true)
        exact("main-conscious-discard-unchanged", expected)
        closeDay()
        exact("main-calendar-return-unchanged", expected)
        fact(recipe.literal.key + ".ui_compose", "UI_COMPOSE", JSONObject().put("target_uuid", target.id)
            .put("full_endpoints", recipe.literal.detail).put("widget_text", Stage12TemporalLiteralOracle.resumptionDisplay)
            .put("widget_geometry", main.textObservations).put("historical_projection_instant", reference).put("vm_clock_mode", "REAL"))
        fact(recipe.literal.key + ".activity_native", "ACTIVITY_NATIVE", JSONObject().put("target_uuid", target.id)
            .put("owner_date", target.ownerLocalDate).put("first_destination_date_only", true).put("original_pending_intent", true)
            .put("three_forward_gestures", main.gestures).put("real_draft_and_original_fingerprint", true)
            .put("neighbour_rejected", true).put("conscious_discard", true).put("return_observed", true)
            .put("store_reopened", true).put("new_owner", true).put("route", "REAL_REMOTE_VIEWS_TOUCH_MAIN_DAY_CORRECT_EXACT_UUID"))
        fact(recipe.literal.key + ".room_isolated", "ROOM_ISOLATED", JSONObject().put("all_tables_preferences_files", true)
            .put("same_paths_reopened", true).put("target_uuid", target.id).put("proof_scope", "OWN_STORE_ONLY").put("received_state", "NOT_CERTIFIED"))
        closeSession(session)
    }

    private fun family(slot: String, body: () -> Unit) {
        try {
            check(instrumentation is Stage12NormalQaRunner && context.packageName == "com.blackatsystems.miguardia.qa")
            val args = InstrumentationRegistry.getArguments()
            check(args.getString("stage12CalendarClock") == "REAL")
            run = checkNotNull(args.getString("stage12RunId")); check(UUID.fromString(run).toString() == run)
            val qa = checkNotNull(args.getString("stage12QaSha256"))
            val runner = checkNotNull(args.getString("stage12RunnerSha256"))
            check(listOf(qa, runner).all { it.matches(Regex("[0-9a-f]{64}")) })
            val identity = JSONObject().put("run_uuid", run).put("qa_sha256", qa).put("runner_sha256", runner)
            output = Stage12NativeReceiptFiles.prepareOutput(context.filesDir, run, identity.toString(), slot)
            receipt = JSONObject(identity.toString()).put("schema", "stage12-temporal-literal-v1").put("case", slot)
                .put("calendar_clock_mode", "REAL").put("phone_clock_changed", false).put("global_settings_changed", false)
                .put("host", if (slot == "temporal-resumption-widget") "COMPONENT_SEED_AND_ORIGINAL_REMOTE_VIEWS_MAIN_RECEIVER" else "COMPONENT_ACTIVITY_REAL_PRODUCT_BINDINGS")
                .put("main_bootstrap_proven", false)
                .put("process_death_proven", false).put("received_state", "NOT_CERTIFIED").put("post_ui_certified", false)
                .put("round_credit", 0).put("facts", facts).put("readbacks", readbacks)
                .put("text_observations", textObservations).put("back_events", backEvents)
            if (slot == "temporal-shift-endpoints" || android.os.Build.VERSION.SDK_INT == 37) {
                receipt!!.put("runtime_api", android.os.Build.VERSION.SDK_INT)
            }
            body()
            check(sessions.isNotEmpty() && sessions.all { it.closed })
            cleanup = { cleanupClosedCases() }
        } catch (error: Throwable) {
            retain(error)
        } finally {
            current?.takeIf { !it.closeAttempted }?.let { session ->
                try { trace.closing(Stage12SelectionDiagnostics.Checkpoint.CLOSE_OWNER) { closeSession(session) } }
                catch (error: Throwable) { retain(error) }
            }
        }
        trace.primary?.let { throw it }
    }

    private fun start(scope: Stage12TemporalScope) {
        active(); check(current == null || ownSession.closed)
        check(cases.none { it.scope == scope })
        val ownCase = Stage12TemporalCase(run, scope, ::retain)
        cases += ownCase
        val session = Session(ownCase); sessions += session; current = session
        phase = "PREPARE:${scope.wire}"
        db { ownCase.prepare(context, clock) }
        mount(session)
        exact("seed-mounted", ownCase.fixture.initialExpected)
    }
    private fun mount(session: Session) {
        active(); check(session.resources == null && session.owner == null)
        val resources = Stage12TemporalResources(context, session.case.opened, run, session, UUID.randomUUID(), ::retain, observe = { })
        session.resources = resources
        db { resources.prepare(clock, Stage12TemporalLiteralOracle.zone, mainReceiver = false) }
        val owner = Stage12TemporalComponentOwner(session.case, resources, ::retain, ::active)
        session.owner = owner
        owner.launch()
        await { owner.activity?.lifecycle?.currentState == Lifecycle.State.RESUMED &&
            c.loadState == CalendarLoadState.CONTENT && owner.work.uiState.value.rootState is WorkSetupState.V2Ready &&
            a.loadState == AvailabilityLoadState.CONTENT }
    }
    private fun remount(expected: Stage12StoreSnapshot) {
        val original = ownSession
        val oldOwner = original.owner?.activity
        val oldStore = original.case.opened
        closeSession(original)
        original.case.reopenClosedStore(context)
        check(original.case.opened !== oldStore)
        val replacement = Session(original.case); sessions += replacement; current = replacement
        mount(replacement)
        check(owner.activity !== oldOwner && checkNotNull(oldOwner).isDestroyed)
        exact("new-owner-store-reopened", expected)
    }
    private fun closeSession(session: Session) {
        check(!session.closeAttempted); session.closeAttempted = true
        // A failed setup does not establish the absence of outstanding work; keep its original capability.
        check(session.case.preparation.projection()["status"] == "COMPLETE") { "TEMPORAL_SETUP_CESE_UNPROVEN" }
        session.mainOwner?.let { main ->
            try { db { main.closeOriginalMain() } } catch (error: Throwable) { retain(error) }
            try { main.closeOriginalHost() } catch (error: Throwable) { retain(error) }
            check(main.mainClosureProven)
        }
        session.owner?.let {
            try { db { it.closeOriginal() } } catch (error: Throwable) { retain(error) }
            check(it.closureProven)
        }
        session.resources?.let {
            try { db { it.closeAfterOwner(session.mainOwner?.mainClosureProven ?: session.owner?.closureProven ?: true) } } catch (error: Throwable) { retain(error) }
            check(it.closureProven)
        }
        session.mainOwner?.let { main ->
            try { main.releaseAfterQuiescence() } catch (error: Throwable) { retain(error) }
            check(main.closureProven)
        }
        try { session.case.opened.close() } catch (error: Throwable) { retain(error) }
        check(session.case.opened.isClosed)
        session.closed = true
        active()
    }

    private fun createShift(): V2ShiftWrite {
        val recipe = fixture.recipe; val literal = recipe.literal
        phase = "SHIFT_GESTURES:${recipe.scope.wire}"
        openDay(literal.ownerDate)
        touch("day-edit-menu")
        await { m.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE && !m.isLoading && m.selectedDates == setOf(literal.ownerDate) }
        val header = compose.onNodeWithTag("manual-institutions")
        val expanded = header.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        check(expanded in setOf("Instituciones desplegadas", "Instituciones contraídas"))
        if (expanded == "Instituciones contraídas")
            touch("manual-institutions")
        waitTag("manual-place-${recipe.business.placeId}")
        if (!shown("v2-template-${recipe.initialTemplate.id}")) touch("manual-place-${recipe.business.placeId}")
        touch("v2-template-${recipe.initialTemplate.id}")
        check(m.selectedTemplateId == recipe.initialTemplate.id)
        touch("manual-toggle-position"); fill("v2-manual-position", "Puesto temporal 7")
        val operation = checkNotNull(m.operationId)
        val timestamp = checkNotNull(m.operationTimestamp)
        check(clock.contains(timestamp) && m.position == "Puesto temporal 7")
        val id = UUID.nameUUIDFromBytes("$operation:shift:${literal.ownerDate}".toByteArray(Charsets.UTF_8))
        val expected = Stage12TemporalRecipe(recipe.namespace, timestamp).expectedShift(literal, id)
        check(db { store.v2Shifts.getShift(id) } == V2ShiftLookup.Missing)
        touch("v2-manual-review")
        await {
            check(!shown("v2-occupied-dialog") && m.occupiedDates.isEmpty()) { "TEMPORAL_NON_OVERLAPPING_NEIGHBOUR_CLASSIFIED_OCCUPIED" }
            m.stage == V2ManualShiftLoadStage.CONFIRM_WARNINGS || m.stage == V2ManualShiftLoadStage.REVIEW
        }
        val date = literal.detail.take(10)
        val warnings = listOf("$date: habrá más de una jornada (05:00–05:30 y ${literal.compact}).") + when (recipe.scope.variant) {
            Stage12TemporalVariant.SAME_DAY, Stage12TemporalVariant.HISTORICAL ->
                listOf("Entre $date 05:00–05:30 y $date ${literal.compact} hay 2 h 45 min de descanso.")
            Stage12TemporalVariant.TWENTY_FOUR_HOURS ->
                listOf("Entre $date 05:00–05:30 y $date ${literal.compact} hay 2 h 30 min de descanso.")
            else -> emptyList()
        }
        check(m.stage == V2ManualShiftLoadStage.CONFIRM_WARNINGS && m.warnings == warnings)
        waitTag("v2-warning-dialog")
        exact("shift-warning-review-read-only", fixture.initialExpected)
        textTouch("Continuar igualmente")
        await { m.stage == V2ManualShiftLoadStage.REVIEW && !m.isLoading }
        check(!m.reviewFingerprint.isNullOrBlank() && m.selectedDates == setOf(literal.ownerDate) && m.position == "Puesto temporal 7" &&
            m.acknowledgedWarnings == warnings)
        touch("v2-manual-save")
        await { m.stage == V2ManualShiftLoadStage.IDLE && !m.isSaving }
        check(db { store.v2Shifts.getShift(id) } == V2ShiftLookup.V2(expected))
        return expected
    }
    private fun createWindow(): AvailabilityWindowRecord {
        val recipe = fixture.recipe; val literal = recipe.literal
        phase = "WINDOW_GESTURES:${recipe.scope.wire}"
        openDay(literal.ownerDate); touch("day-other-options"); touch("availability-add-${literal.ownerDate}")
        await { a.surface == AvailabilitySurface.WINDOW_EDITOR && a.windowDraft != null }
        val draft = checkNotNull(a.windowDraft)
        check(draft.ownerDate == literal.ownerDate && draft.labelOverride == null && draft.recordId != recipe.neighbourWindow.id)
        val from = LocalDateTime.parse(literal.localStart); val to = LocalDateTime.parse(literal.localEnd)
        fill("availability-start", "%02d%02d".format(from.hour, from.minute))
        fill("availability-end-date", to.toLocalDate().toString())
        fill("availability-end", "%02d%02d".format(to.hour, to.minute))
        touch("availability-window-review")
        await { a.surface == AvailabilitySurface.WINDOW_REVIEW && a.windowReview != null && !a.isSaving }
        val review = checkNotNull(a.windowReview)
        // UUID and technical timestamps are observed before the delegate; all business fields are literal inputs.
        check(clock.contains(review.record.createdAt) && review.record.createdAt == review.record.updatedAt)
        val expected = Stage12TemporalRecipe(recipe.namespace, review.record.createdAt).expectedWindow(literal, draft.recordId)
        check(review.record == expected && review.expectation.previous == null)
        observeText("Inicio: " + literal.detail.substringBefore(" – "))
        observeText("Final: " + literal.detail.substringAfter(" – "))
        observeText("Duración programada: " + literal.reviewDuration)
        touch("availability-window-save")
        await { a.surface == AvailabilitySurface.NONE && !a.isSaving && a.windowDraft == null && a.windowReview == null }
        check(db { store.availabilityWindows.get(expected.id) } == expected)
        return expected
    }
    private fun inspectWindow(target: AvailabilityWindowRecord) {
        if (c.detailDate == null) openDay(target.ownerLocalDate)
        check(c.detailDate == target.ownerLocalDate && target.id != fixture.recipe.neighbourWindow.id)
        val node = hasTestTag("availability-readonly-${target.id}") and hasAnyAncestor(hasTestTag("day-condition-card"))
        observeText("Guardia pasiva · " + fixture.recipe.literal.detail, node)
        fact(fixture.recipe.literal.key + ".ui_compose", "UI_COMPOSE", JSONObject().put("target_uuid", target.id)
            .put("owner_date", target.ownerLocalDate).put("full_endpoints", fixture.recipe.literal.detail))
    }
    private fun inspectShift(target: V2ShiftWrite) {
        openDay(target.shift.localStartDate)
        val within = hasAnyAncestor(hasTestTag("shift-detail-card-${target.shift.id}")) and
            !hasAnyAncestor(hasTestTag("v2-actual-${target.shift.id}-card"))
        observeText("Hospital Temporal (HTP)", within)
        observeText("Calle de prueba 120", within)
        observeText(fixture.recipe.literal.detail, within)
        observeText("Puesto: Puesto temporal 7", within)
        val now = clock.instant()
        observeText(if (now >= target.shift.endAt) "Completada" else if (now >= target.shift.startAt) "En curso" else "Próxima", within)
        val color = compose.onNode(hasContentDescription("Color de la jornada HTP") and within, useUnmergedTree = true)
        if (!color.isDisplayed()) color.performScrollTo()
        color.assertIsDisplayed()
        // Public node capture respects the actual window; a blocked capture is a failure, never a reason to change FLAG_SECURE.
        val pixels = color.captureToImage().toPixelMap()
        check(pixels.width > 2 && pixels.height > 2)
        val painted = pixels[pixels.width / 2, pixels.height / 2].toArgb()
        check(painted == 0xFF336699.toInt())
        check(c.detailDate == target.shift.localStartDate && db { store.v2Shifts.getShift(target.shift.id) } == V2ShiftLookup.V2(target))
        fact(fixture.recipe.literal.key + ".ui_compose", "UI_COMPOSE", JSONObject().put("target_uuid", target.shift.id)
            .put("full_endpoints", fixture.recipe.literal.detail).put("snapshot_text_geometry", true)
            .put("indicator_center_argb", painted).put("capture", "PUBLIC_COMPOSE_NODE_CAPTURE_WINDOW_UNCHANGED"))
    }
    private fun changeHistoricalCatalog(before: Stage12StoreSnapshot, target: V2ShiftWrite): Stage12StoreSnapshot {
        val recipe = fixture.recipe; val business = recipe.business
        val stamp = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        check(stamp >= maxOf(business.objective.updatedAt, business.place.updatedAt))
        val objective = business.objective.copy(fullName = "Institución modificada", abbreviation = "MOD", address = "Dirección modificada 999", updatedAt = stamp)
        val place = business.place.copy(updatedAt = stamp)
        check(objective.normalizedForV2Update(business.objective, stamp) == objective)
        val afterPlace = oracle.expected(before, removed = listOf(oracle.objective(business.objective), oracle.place(business.place)),
            inserted = listOf(oracle.objective(objective), oracle.place(place)))
        db { store.workCatalog.updateWorkPlace(WorkPlaceUpdate(business.place, place, business.objective, objective)) }
        exact("historical-place-command", afterPlace)
        val typeStamp = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        check(typeStamp >= maxOf(stamp, business.type.updatedAt))
        val type = WorkType(business.type.id, business.timelineId, WorkSector.NURSING, "Tipo modificado", "TIPO MODIFICADO",
            business.type.behavior, business.type.isActive, business.type.createdAt, typeStamp)
        check(business.type.withUpdatedName("Tipo modificado", typeStamp) == type)
        val afterType = oracle.expected(afterPlace, removed = listOf(oracle.type(business.type)), inserted = listOf(oracle.type(type)))
        db { store.workCatalog.updateWorkType(WorkTypeUpdate(business.type, type)) }; exact("historical-type-command", afterType)
        val template = recipe.initialTemplate.copy(startTime = LocalTime.of(6, 0), endTime = LocalTime.of(10, 0),
            colorArgb = 0xFFAA2200.toInt(), updatedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS))
        check(template.updatedAt >= maxOf(typeStamp, recipe.initialTemplate.updatedAt))
        val after = oracle.expected(afterType, removed = listOf(oracle.template(recipe.initialTemplate)), inserted = listOf(oracle.template(template)))
        db { store.workCatalog.updateWorkTemplate(WorkTemplateUpdate(recipe.initialTemplate, template)) }; exact("historical-template-command", after)
        check(db { store.v2Shifts.getShift(target.shift.id) } == V2ShiftLookup.V2(target))
        return after
    }
    private fun inspectCompact(expectedSnapshot: Stage12StoreSnapshot, targetShift: V2ShiftWrite? = null,
        targetWindow: AvailabilityWindowRecord? = null) {
        check((targetShift != null) xor (targetWindow != null))
        val recipe = fixture.recipe
        exact("compact-source", expectedSnapshot)
        val expectedShifts = recipe.shifts + listOfNotNull(targetShift)
        val expectedWindows = listOf(recipe.neighbourWindow) + listOfNotNull(targetWindow)
        check(expectedShifts.map { it.shift.id }.distinct().size == expectedShifts.size &&
            expectedWindows.map { it.id }.distinct().size == expectedWindows.size)
        val input = db {
            val shifts = expectedShifts.map { expected ->
                val observed = store.v2Shifts.getShift(expected.shift.id)
                check(observed == V2ShiftLookup.V2(expected)); (observed as V2ShiftLookup.V2).write
            }
            val windows = expectedWindows.map { expected -> checkNotNull(store.availabilityWindows.get(expected.id)).also { check(it == expected) } }
            NextEventInput(shifts, windows, emptyMap(), emptyList(), emptyList(), emptyList(), emptyList())
        }
        // Explicit historical projection input; every Activity/VM and the phone keep the real clock.
        val reference = recipe.literal.start
        val future = projectNextEvent(reference, recipe.zone, input)
        val primary = future.primaryEvents.single()
        if (targetShift != null) {
            check(primary is NextEventItem.Shift && primary.shiftId == targetShift.shift.id &&
                primary.start == recipe.literal.start && primary.end == recipe.literal.end)
        } else {
            check(primary is NextEventItem.Availability && primary.windowId == checkNotNull(targetWindow).id &&
                primary.ownerLocalDate == recipe.literal.ownerDate && primary.start == recipe.literal.start && primary.end == recipe.literal.end)
        }
        val projection = projectTodayCard(reference, recipe.zone, input.shifts, input.actualsByShiftId,
            input.vacations, input.medicalLeaves, future)
        val original = owner.activity
        phase = "EXPLICIT_HISTORICAL_COMPACT:${recipe.scope.wire}"
        owner.showHistoricalCompactTerminal(NextEventUiState(loadState = NextEventLoadState.CONTENT, result = projection))
        waitTag("temporal-projection-scroll"); waitTag("next-event-card")
        val within = hasAnyAncestor(hasTestTag(if (targetShift != null) "next-journey-identity" else "next-event-card"))
        val expectedText = if (targetShift != null) recipe.literal.compact else recipe.literal.compactDated
        observeText(expectedText, within, "temporal-projection-scroll")
        check(owner.activity === original && checkNotNull(original).lifecycle.currentState == Lifecycle.State.RESUMED && !original.isDestroyed)
        fact(recipe.literal.key + ".ui_compose.compact", "UI_COMPOSE", JSONObject().put("reference_instant", reference)
            .put("component", "REAL_NEXT_EVENT_CARD").put("all_own_rows_used", true).put("literal_text", expectedText)
            .put("vm_clock_mode", "REAL").put("vm_observer_historical_output_proven", false))
        exact("compact-read-only-returned", expectedSnapshot)
    }

    private fun openDay(date: LocalDate) {
        active(); check(c.detailDate == null)
        var remaining = 36
        while (c.visibleMonth != YearMonth.from(date)) {
            check(remaining-- > 0); val previous = c.visibleMonth
            touch(if (previous < YearMonth.from(date)) "calendar-next-month" else "calendar-previous-month")
            await { c.visibleMonth != previous && c.loadState == CalendarLoadState.CONTENT }
        }
        val day = c.days.single { it.date == date }
        touch(if (day.isFullyCompleted) "completed-day-$date" else "day-$date")
        await { c.detailDate == date && !c.stage2Busy && shown("day-detail-modal-sheet") }
    }
    private fun closeDay() {
        active(); val original = activity; val date = checkNotNull(c.detailDate)
        fun alive() = activity === original && !original.isFinishing && !original.isDestroyed &&
            original.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        check(alive())
        val event = JSONObject().put("scope", ownSession.case.scope.wire).put("phase", phase)
            .put("before_date", c.detailDate).put("input_entered", true).put("transition_observed", false)
        backEvents.put(event)
        val apiReturn = device.pressBack()
        event.put("api_return", apiReturn).put("input_returned", true)
        await {
            check(alive()) { "TEMPORAL_BACK_OWNER_LOST" }
            val day = c.days.single { it.date == date }
            val cell = if (day.isFullyCompleted) "completed-day-$date" else "day-$date"
            c.detailDate == null && !shown("day-detail-modal-sheet") && c.visibleMonth == YearMonth.from(date) &&
                shown("month-grid") && compose.onNodeWithTag("month-grid").isDisplayed() &&
                shown(cell) && compose.onNodeWithTag(cell).isDisplayed()
        }
        event.put("transition_observed", true).put("same_owner", true).put("owner_alive", true).put("calendar_return_visible", true)
    }
    private fun observeText(value: String, scope: SemanticsMatcher? = null, expectedViewportTag: String? = if (scope != null) "day-detail-scroll" else null) {
        val matcher = if (scope == null) hasText(value, substring = false) else hasText(value, substring = false) and scope
        await { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        val node = compose.onNode(matcher, useUnmergedTree = true)
        // isDisplayed also accepts partially clipped text; this uses the actual scroll ancestor.
        node.performScrollTo()
        node.assertIsDisplayed(); node.assertTextEquals(value)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { check(it(layouts)) }
        val layout = requireTemporalTextLayout(
            layouts, value,
            text = { it.layoutInput.text.text },
            hasOverflow = { it.stage12VisibleOverflow() },
        ) {
            val paragraph = it.multiParagraph
            val constraints = it.layoutInput.constraints
            val lines = it.lineCount
            val end = if (lines > 0) it.getLineEnd(lines - 1) else -1
            val visibleEnd = if (lines > 0) it.getLineEnd(lines - 1, visibleEnd = true) else -1
            "TEMPORAL_LAYOUT_METRICS " +
                "size_w=${it.size.width} size_h=${it.size.height} " +
                "paragraph_w=${paragraph.width} paragraph_h=${paragraph.height} " +
                "intrinsic_min_w=${paragraph.intrinsics.minIntrinsicWidth} intrinsic_max_w=${paragraph.intrinsics.maxIntrinsicWidth} " +
                "min_w=${constraints.minWidth} max_w=${constraints.maxWidth} " +
                "min_h=${constraints.minHeight} max_h=${constraints.maxHeight} " +
                "lines=$lines text_length=${it.layoutInput.text.length} end=$end visible_end=$visibleEnd " +
                "ellipsis=${(0 until lines).any(it::isLineEllipsized)} " +
                "overflow_w=${it.didOverflowWidth} overflow_h=${it.didOverflowHeight}"
        }
        check((0 until layout.lineCount).none { layout.isLineEllipsized(it) })
        val bounds = node.fetchSemanticsNode().boundsInRoot
        check(bounds.width > 0 && bounds.height > 0)
        var ancestor = node.fetchSemanticsNode().parent
        var depth = 0
        while (ancestor != null && !ancestor.config.contains(SemanticsActions.ScrollBy)) {
            check(depth++ < 64); ancestor = ancestor.parent
        }
        val viewport = checkNotNull(ancestor) { "TEMPORAL_TEXT_SCROLL_OWNER_REQUIRED" }
        if (expectedViewportTag != null) {
            val declared = compose.onNodeWithTag(expectedViewportTag, useUnmergedTree = true).fetchSemanticsNode()
            check(viewport.id == declared.id) { "TEMPORAL_TEXT_SCROLL_OWNER_FOREIGN" }
        }
        val uncut = node.getUnclippedBoundsInRoot()
        val density = compose.density.density
        val physical = androidx.compose.ui.geometry.Rect(uncut.left.value * density, uncut.top.value * density,
            uncut.right.value * density, uncut.bottom.value * density)
        val visible = viewport.boundsInRoot
        check(physical.left >= visible.left - 0.5f && physical.top >= visible.top - 0.5f &&
            physical.right <= visible.right + 0.5f && physical.bottom <= visible.bottom + 0.5f) { "TEMPORAL_TEXT_CLIPPED_BY_SCROLL" }
        textObservations.put(JSONObject().put("scope", ownSession.case.scope.wire).put("phase", phase).put("text", value)
            .put("node_id", node.fetchSemanticsNode().id).put("scroll_node_id", viewport.id)
            .put("unclipped_px", JSONArray(listOf(physical.left, physical.top, physical.right, physical.bottom)))
            .put("viewport_px", JSONArray(listOf(visible.left, visible.top, visible.right, visible.bottom)))
            .put("line_count", layout.lineCount).put("visual_overflow", layout.hasVisualOverflow)
            .put("ellipsized", false).put("density", density)
            .put("font_scale", activity.resources.configuration.fontScale))
    }
    private fun exact(stage: String, expected: Stage12StoreSnapshot) {
        active(); phase = stage
        val binding = ownSession.case.binding
        val read = Stage12FullStoreReadback.captureTemporalOwned(context, run, ownSession.case.scope, binding)
        oracle.requireExact(read, expected)
        readbacks.put(JSONObject().put("scope", ownSession.case.scope.wire).put("stage", stage)
            .put("tables_sha256", hash(read.tables.toString())).put("preferences_sha256", hash(read.preferences.toString()))
            .put("files_sha256", hash(read.files.toString())).put("all_tables", read.tables.size).put("file_entries", read.files.size))
    }
    private fun active() { firstNegative.requireActive(); trace.primary?.let { throw it } }
    private fun await(condition: () -> Boolean) { active(); compose.waitUntil(30_000) { active(); condition() } }
    private fun shown(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size.also {
        check(it in 0..1) { "TEMPORAL_NODE_AMBIGUOUS" }
    } == 1
    private fun waitTag(tag: String) = await { shown(tag) }
    private fun touch(tag: String) {
        active(); waitTag(tag); val node = compose.onNodeWithTag(tag)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
    }
    private fun physicalRouteTouch(tag: String, events: JSONArray) {
        active(); waitTag(tag)
        val node = compose.onNodeWithTag(tag)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled()
        val semantic = node.fetchSemanticsNode()
        var screen = androidx.compose.ui.geometry.Offset.Unspecified
        instrumentation.runOnMainSync {
            val coordinates = semantic.layoutInfo.coordinates
            check(coordinates.isAttached && coordinates.size.width > 0 && coordinates.size.height > 0)
            screen = coordinates.localToScreen(androidx.compose.ui.geometry.Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
        }
        check(screen.x.isFinite() && screen.y.isFinite() && screen.x >= 0 && screen.y >= 0)
        firstNegative.inputAfterReadiness({ active(); screen }) { point ->
            val event = JSONObject().put("kind", "REAL_COMPOSE_SCREEN_TOUCH").put("tag", tag).put("node_id", semantic.id)
                .put("screen_x", point.x).put("screen_y", point.y).put("input_entered", true).put("transition_observed", false)
            events.put(event)
            active()
            event.put("api_return", device.click(point.x.toInt(), point.y.toInt())).put("input_returned", true)
        }
    }
    private fun textTouch(value: String) {
        val selector = hasText(value, substring = false) and hasClickAction()
        await { compose.onAllNodes(selector).fetchSemanticsNodes().size == 1 }
        val node = compose.onNode(selector)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
    }
    private fun fill(tag: String, value: String) {
        waitTag(tag); val node = compose.onNodeWithTag(tag)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().performTextReplacement(value)
        owner.onActivity { activity ->
            activity.currentFocus?.clearFocus()
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
    }
    private fun fact(key: String, level: String, value: JSONObject) {
        val scope = ownSession.case.scope
        val suffix = when (level) { "UI_COMPOSE" -> "ui_compose"; "ACTIVITY_NATIVE" -> "activity_native"
            "ROOM_ISOLATED" -> "room_isolated"; else -> error("TEMPORAL_OBSERVATION_LEVEL_NOT_CLOSED") }
        val canonical = scope.variant.key + "." + suffix
        check(key == scope.literal.key + "." + suffix || key == scope.literal.key + "." + suffix + ".compact")
        facts.put(JSONObject().put("assertion_key", "${scope.wire}:$suffix:${facts.length()}")
            .put("obligation_key", canonical).put("fixture_role", scope.role.name)
            .put("observation_kind", if (key.endsWith(".compact")) "COMPACT_COMPONENT" else suffix.uppercase())
            .put("obligation_complete", false).put("comparator_is_additional_acceptance", false)
            .put("level", level).put("check", "PASS").put("observed", value))
    }
    private fun cleanupClosedCases() {
        check(sessions.all { it.closed })
        for (ownCase in cases) {
            val binding = ownCase.binding; check(ownCase.opened.isClosed)
            binding.requireStored(stage12RecurringPathAccess(context))
            check(context.deleteDatabase(binding.name))
            check(listOf("", "-wal", "-shm", "-journal").none { Files.exists(File(binding.database.path + it).toPath(), NOFOLLOW_LINKS) })
            binding.revalidate(stage12RecurringPathAccess(context))
            val root = binding.privateRoot
            check(root == File(context.filesDir.canonicalFile, ownCase.scope.privateRootName(run)) && !Files.isSymbolicLink(root.toPath()))
            val paths = Files.walk(root.toPath()).use { it.iterator().asSequence().toList() }
            check(paths.none(Files::isSymbolicLink) && paths.all { it.startsWith(root.toPath()) })
            paths.sortedByDescending { it.nameCount }.forEach { check(Files.deleteIfExists(it)) }
        }
    }
    private fun <T> db(body: suspend () -> T): T = runBlocking { withTimeout(30_000) { body() } }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private class ObservedRealClock(private val real: Clock) : Clock() {
        private val samples = java.util.Collections.synchronizedList(mutableListOf<Instant>())
        override fun getZone(): ZoneId = real.zone
        override fun withZone(zone: ZoneId): Clock = if (zone == this.zone) this else real.withZone(zone)
        override fun instant(): Instant = real.instant().also(samples::add)
        fun contains(value: Instant): Boolean = synchronized(samples) { samples.any { it.truncatedTo(ChronoUnit.MILLIS) == value } }
    }
}
