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
class Stage12AvailabilityLifecycleTest {
    private val trace = Stage12SelectionDiagnostics.Trace()
    private val firstNegative = Stage12TemporalFailure { trace.retain(it) }
    private fun retain(error: Throwable): Throwable = firstNegative.remember(error)
    private val compose by lazy { trace.checked(Stage12SelectionDiagnostics.Checkpoint.UNMARKED) { createEmptyComposeRule() } }
    private enum class OverviewStage {
        GUARD_DETAIL_QUERY, GUARD_DETAIL_REJECTED, GUARD_SURFACE_QUERY, GUARD_SURFACE_REJECTED,
        MENU_WAIT, MENU_TOUCH, DRAWER_WAIT, DRAWER_TOUCH, WORK_OVERVIEW_WAIT,
        ADVANCED_QUERY, ADVANCED_WAIT, ADVANCED_TOUCH, AVAILABILITY_WAIT, AVAILABILITY_TOUCH, SURFACE_WAIT,
        LABEL_TEXT_WAIT, LABEL_TEXT_ASSERT, COUNT_TEXT_WAIT, COUNT_TEXT_ASSERT,
        WINDOWS_EQUAL, READBACK_CAPTURE, READBACK_COMPARE, READBACK_RECORD, BETWEEN_STEPS,
    }
    private var overviewWitnessActive = false
    private var overviewFirstFailure: OverviewStage? = null
    private fun rememberOverview(stage: OverviewStage) {
        if (overviewWitnessActive && overviewFirstFailure == null) overviewFirstFailure = stage
    }
    private fun <T> overviewStage(stage: OverviewStage?, body: () -> T): T = try {
        body()
    } catch (error: Throwable) {
        if (stage != null) rememberOverview(stage)
        throw error
    }
    private fun overviewWitness(body: () -> Unit) {
        overviewWitnessActive = true
        try { overviewStage(OverviewStage.BETWEEN_STEPS, body) }
        finally { overviewWitnessActive = false }
    }

    private enum class ReviewStage {
        INITIAL_SOURCE_WAIT, DAY_ACTIVE, DAY_CLOSED_GUARD, MONTH_CONDITION,
        MONTH_BUDGET, MONTH_PREVIOUS, MONTH_TAG, MONTH_WAIT,
        MONTH_TOUCH, MONTH_SETTLED_WAIT, DAY_LOOKUP, DAY_TAG,
        DAY_WAIT, DAY_TOUCH, DAY_OPEN_WAIT, OPTIONS_WAIT,
        OPTIONS_TOUCH, CORRECT_WAIT, CORRECT_TOUCH, EDITOR_WAIT,
        END_BUILD, END_DATE_VALUE, END_DATE_WAIT, END_DATE_FILL,
        END_TIME_VALUE, END_TIME_WAIT, END_TIME_FILL, CLOCK_BEFORE,
        REVIEW_WAIT, REVIEW_TOUCH, REVIEW_READY_WAIT, REVIEW_VALUE,
        STAMP_VALUE, CLOCK_SAMPLED, CLOCK_NEXT_MILLI, CLOCK_NOT_BEFORE,
        CLOCK_AFTER_PREVIOUS, CANDIDATE_BUILD, CANDIDATE_EQUAL, PREVIOUS_EQUAL,
        PROTECTION_EQUAL, ORACLE_INITIAL, ORACLE_FINAL, ORACLE_STABLE,
        BREAKDOWN_ORACLE, BREAKDOWN_STATE, BREAKDOWN_PROGRAMMED, BREAKDOWN_EFFECTIVE,
        BREAKDOWN_REPLACED, BREAKDOWN_PENDING, BREAKDOWN_PROJECTED, BREAKDOWN_FUTURE_REPLACED,
        PROGRAMMED_TEXT_WAIT, PROGRAMMED_TEXT_OBSERVE, EFFECTIVE_TEXT_WAIT, EFFECTIVE_TEXT_OBSERVE,
        REPLACED_TEXT_WAIT, REPLACED_TEXT_OBSERVE, PENDING_TEXT_WAIT, PENDING_TEXT_OBSERVE,
        PROJECTED_TEXT_WAIT, PROJECTED_TEXT_OBSERVE, FUTURE_REPLACED_TEXT_WAIT, FUTURE_REPLACED_TEXT_OBSERVE,
        PROTECTION_TEXT_CONDITION, PROTECTION_TEXT_WAIT, PROTECTION_TEXT_OBSERVE, RETURN_HEADER_WAIT,
        RETURN_HEADER_TOUCH, RETURN_EDITOR_WAIT, DISCARD_SURFACE, DISCARD_ID,
        DISCARD_NOT_SAVING, DISCARD_DRAFT, DISCARD_OWNER, DISCARD_FIRST_OWNER,
        DISCARD_FIRST_STARTED, DISCARD_FIRST_IME_WAIT, DISCARD_FIRST_EVENT, DISCARD_FIRST_BACK,
        DISCARD_FIRST_RETURN, DISCARD_FIRST_DIALOG_WAIT, DISCARD_FIRST_TRANSITION, DISCARD_FIRST_SAME_OWNER,
        DISCARD_FIRST_SAME_DRAFT, KEEP_EDITING_WAIT, KEEP_EDITING_TOUCH, KEEP_EDITING_RETURN_WAIT,
        DISCARD_SECOND_OWNER, DISCARD_SECOND_STARTED, DISCARD_SECOND_IME_WAIT, DISCARD_SECOND_EVENT,
        DISCARD_SECOND_BACK, DISCARD_SECOND_RETURN, DISCARD_SECOND_DIALOG_WAIT, DISCARD_SECOND_TRANSITION,
        DISCARD_SECOND_SAME_OWNER, DISCARD_SECOND_SAME_DRAFT, DISCARD_CONFIRM_WAIT, DISCARD_CONFIRM_TOUCH,
        DISCARD_COMPLETE_WAIT, DISCARD_FINAL_OWNER, DISCARD_FINAL_STARTED, READBACK_ACTIVE,
        READBACK_CAPTURE, READBACK_COMPARE, READBACK_RECORD, DAY_CLOSE_ACTIVE,
        DAY_CLOSE_OWNER, DAY_CLOSE_DATE, DAY_CLOSE_GUARD, DAY_CLOSE_EVENT,
        DAY_CLOSE_BACK, DAY_CLOSE_RETURN, DAY_CLOSE_WAIT, DAY_CLOSE_TRANSITION,
        BETWEEN_STEPS,
    }
    private var reviewWitnessActive = false
    private var reviewFirstFailure: ReviewStage? = null
    private fun rememberReview(stage: ReviewStage) {
        if (reviewWitnessActive && reviewFirstFailure == null) reviewFirstFailure = stage
    }
    private fun <T> reviewStage(stage: ReviewStage?, body: () -> T): T = try {
        body()
    } catch (error: Throwable) {
        if (stage != null) rememberReview(stage)
        throw error
    }
    private fun reviewWitness(body: () -> Unit) {
        reviewWitnessActive = true
        try { reviewStage(ReviewStage.BETWEEN_STEPS, body) }
        finally { reviewWitnessActive = false }
    }
    /** Keeps the original Boolean short circuit; only the failing check publishes its last evaluated operand. */
    private fun reviewCheck(body: ((ReviewStage, () -> Boolean) -> Boolean) -> Boolean) {
        var operand = ReviewStage.BETWEEN_STEPS
        try { check(body { stage, predicate -> operand = stage; predicate() }) }
        catch (error: Throwable) { rememberReview(operand); throw error }
    }
    private fun reviewBreakdown(actual: AvailabilityBreakdown, expected: Stage12AvailabilityOracle.Expected) {
        try { Stage12AvailabilityOracle.requireExact(actual, expected) }
        catch (error: Throwable) {
            // Pure classification of the immutable arguments already passed to the real oracle: no reread.
            val stage = if (error !is IllegalStateException) ReviewStage.BREAKDOWN_ORACLE else when {
                actual.state != expected.state -> ReviewStage.BREAKDOWN_STATE
                actual.programmedMinutes != expected.programmed -> ReviewStage.BREAKDOWN_PROGRAMMED
                actual.effectiveElapsedMinutes != expected.effective -> ReviewStage.BREAKDOWN_EFFECTIVE
                actual.replacedElapsedMinutes != expected.replaced -> ReviewStage.BREAKDOWN_REPLACED
                actual.futurePendingMinutes != expected.pending -> ReviewStage.BREAKDOWN_PENDING
                actual.effectiveProjectedAtEndMinutes != expected.projected -> ReviewStage.BREAKDOWN_PROJECTED
                actual.futureOccupiedByPlannedWorkMinutes != expected.futureReplaced -> ReviewStage.BREAKDOWN_FUTURE_REPLACED
                else -> ReviewStage.BREAKDOWN_ORACLE
            }
            rememberReview(stage); throw error
        }
    }

    private enum class ReviewTextCheck {
        BETWEEN_CHECKS, NODE_RESOLVE, SCROLL, DISPLAYED,
        EXACT_TEXT, LAYOUT_COLLECTION, LAYOUT_ACTION, LAYOUT_CALLBACK,
        LAYOUT_HELPER, LAYOUT_TEXT_QUERY, LAYOUT_OVERFLOW_QUERY, LAYOUT_COUNT,
        LAYOUT_VALUE, LAYOUT_OVERFLOW, ELLIPSIS, BOUNDS_QUERY,
        BOUNDS_WIDTH, BOUNDS_HEIGHT, ANCESTOR_QUERY, ANCESTOR_SCROLL_BY,
        ANCESTOR_DEPTH, ANCESTOR_PARENT, VIEWPORT_REQUIRED, DECLARED_VIEWPORT_QUERY,
        DECLARED_VIEWPORT_EQUAL, UNCUT_BOUNDS, DENSITY, PHYSICAL_BOUNDS,
        VIEWPORT_BOUNDS, VIEWPORT_LEFT, VIEWPORT_TOP, VIEWPORT_RIGHT,
        VIEWPORT_BOTTOM, RECORD_APPEND, RECORD_BUILD, RECORD_NODE_QUERY,
        RECORD_FONT_SCALE,
    }
    private data class ReviewTextOverflow(
        val overflowWidth: Boolean, val overflowHeight: Boolean,
        val paragraphWiderThanLayout: Boolean, val paragraphTallerThanLayout: Boolean,
        val intrinsicMaxWithinLayoutWidth: Boolean, val singleLine: Boolean,
        val lastLineEndEqualsTextLength: Boolean, val lastVisibleEndEqualsTextLength: Boolean,
        val ellipsis: Boolean,
    ) {
        fun projection() = JSONObject().put("overflow_width", overflowWidth).put("overflow_height", overflowHeight)
            .put("paragraph_wider_than_layout", paragraphWiderThanLayout).put("paragraph_taller_than_layout", paragraphTallerThanLayout)
            .put("intrinsic_max_within_layout_width", intrinsicMaxWithinLayoutWidth).put("single_line", singleLine)
            .put("last_line_end_equals_text_length", lastLineEndEqualsTextLength)
            .put("last_visible_end_equals_text_length", lastVisibleEndEqualsTextLength).put("ellipsis", ellipsis)
    }
    private data class ReviewTextDiagnostic(val observation: ReviewStage, val check: ReviewTextCheck, val overflow: ReviewTextOverflow?) {
        fun projection() = JSONObject().put("observation", observation.name).put("check", check.name)
            .put("overflow", overflow?.projection() ?: JSONObject.NULL)
    }
    private data class OverviewTextDiagnostic(val observation: OverviewStage, val check: ReviewTextCheck, val overflow: ReviewTextOverflow?) {
        fun projection() = JSONObject().put("observation", observation.name).put("check", check.name)
            .put("overflow", overflow?.projection() ?: JSONObject.NULL)
    }
    private var overviewTextDiagnostic: OverviewTextDiagnostic? = null
    private fun rememberOverviewText(observation: OverviewStage?, check: ReviewTextCheck, overflow: ReviewTextOverflow?, error: Throwable) {
        try {
            if (overviewWitnessActive && observation != null && observation in setOf(OverviewStage.LABEL_TEXT_ASSERT, OverviewStage.COUNT_TEXT_ASSERT) && overviewTextDiagnostic == null) {
                overviewTextDiagnostic = OverviewTextDiagnostic(checkNotNull(observation), check,
                    if (check == ReviewTextCheck.LAYOUT_OVERFLOW) overflow else null)
            }
        } catch (accessory: Throwable) {
            try { error.addSuppressed(accessory) } catch (_: Throwable) { }
        }
    }
    /** Last helper entered, by tag or fixture text; published only with a failure. */
    private var lastAction: String? = null
    /** First failed text observation in any step; fixture text only, never received data. */
    private var textFailureDiagnostic: JSONObject? = null
    private fun rememberTextFailure(value: String, check: String, overflow: ReviewTextOverflow?, error: Throwable) {
        try {
            if (textFailureDiagnostic == null) textFailureDiagnostic = JSONObject().put("phase", phase).put("text", value)
                .put("check", check).put("overflow", overflow?.projection() ?: JSONObject.NULL)
                .put("error_class", error.javaClass.simpleName)
                .put("error_code", error.message?.takeIf { it.matches(Regex("[A-Z0-9_]{1,80}")) } ?: JSONObject.NULL)
        } catch (accessory: Throwable) {
            try { error.addSuppressed(accessory) } catch (_: Throwable) { }
        }
    }
    private var reviewTextDiagnostic: ReviewTextDiagnostic? = null
    private val reviewTextObservationStages = setOf(
        ReviewStage.PROGRAMMED_TEXT_OBSERVE, ReviewStage.EFFECTIVE_TEXT_OBSERVE, ReviewStage.REPLACED_TEXT_OBSERVE,
        ReviewStage.PENDING_TEXT_OBSERVE, ReviewStage.PROJECTED_TEXT_OBSERVE, ReviewStage.FUTURE_REPLACED_TEXT_OBSERVE,
        ReviewStage.PROTECTION_TEXT_OBSERVE,
    )
    private fun rememberReviewText(observation: ReviewStage?, check: ReviewTextCheck, overflow: ReviewTextOverflow?, error: Throwable) {
        try {
            if (reviewWitnessActive && observation != null && observation in reviewTextObservationStages && reviewTextDiagnostic == null) {
                reviewTextDiagnostic = ReviewTextDiagnostic(observation, check,
                    if (check == ReviewTextCheck.LAYOUT_OVERFLOW) overflow else null)
            }
        } catch (accessory: Throwable) {
            // An accessory diagnostic never replaces the original failed operation.
            try { error.addSuppressed(accessory) } catch (_: Throwable) { }
        }
    }

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
                            .put("operations", operations)
                            .put("status", if (primary == null && cleaned) "COMPLETE" else "FAILED")
                        if (primary != null) reviewTextDiagnostic?.let { diagnostic ->
                            try { value.put("review_text_diagnostic", diagnostic.projection()) }
                            catch (accessory: Throwable) {
                                try { primary.addSuppressed(accessory) } catch (_: Throwable) { }
                                throw primary
                            }
                        }
                        if (primary != null) overviewTextDiagnostic?.let { diagnostic ->
                            try { value.put("overview_text_diagnostic", diagnostic.projection()) }
                            catch (accessory: Throwable) {
                                try { primary.addSuppressed(accessory) } catch (_: Throwable) { }
                                throw primary
                            }
                        }
                        if (primary != null) lastAction?.let { action ->
                            try { value.put("last_action", action) }
                            catch (accessory: Throwable) {
                                try { primary.addSuppressed(accessory) } catch (_: Throwable) { }
                                throw primary
                            }
                        }
                        if (primary != null) textFailureDiagnostic?.let { diagnostic ->
                            try { value.put("text_failure_diagnostic", diagnostic) }
                            catch (accessory: Throwable) {
                                try { primary.addSuppressed(accessory) } catch (_: Throwable) { }
                                throw primary
                            }
                        }
                        Stage12NativeReceiptFiles.publish(checkNotNull(output), value.toString(2), primary)
                    }
                })
                trace.primary?.let { original ->
                    val stage = overviewFirstFailure
                    if (stage != null) throw AssertionError("STAGE12_SELECTION_BATCH_FAILED|OVERVIEW|${stage.name}")
                    val reviewStage = reviewFirstFailure
                    if (reviewStage != null) throw AssertionError("STAGE12_SELECTION_BATCH_FAILED|REVIEW|${reviewStage.name}")
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
    private lateinit var expected: Stage12StoreSnapshot
    private lateinit var civilDate: LocalDate
    private val facts = JSONArray()
    private val readbacks = JSONArray()
    private val operations = JSONArray()
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
        var owner: Stage12AvailabilityOwner? = null
        var mainOwner: Stage12TemporalMainOwner? = null
        var closeAttempted = false
        var closed = false
        fun projection() = JSONObject().put("scope", case.scope.wire).put("close_attempted", closeAttempted).put("closed", closed)
            .put("owner_observed", owner?.activity != null).put("owner_closure", owner?.closureProven ?: false)
            .put("auxiliary_closure", resources?.closureProven ?: false)
            .put("main_owner_observed", mainOwner?.activity != null).put("main_closure", mainOwner?.closureProven ?: false)
            .put("resources_preparation", resources?.let { JSONObject(it.projection) } ?: JSONObject.NULL)
            .put("main_activity_observation", JSONObject.NULL)
            .put("faults", owner?.let { JSONObject(it.faults.projection()) } ?: JSONObject.NULL)
            .put("preparation", JSONObject(case.preparation.projection())).put("fixture_prefix", JSONObject(case.fixturePrefix.projection()))
    }


    @Test fun configurationHistoryPreservesWindowsAndReferences() = family("availability-history") {
        step("PREPARE") { start(Stage12TemporalScope(Stage12TemporalVariant.TWENTY_FIVE_HOURS)); expected = fixture.initialExpected }
        step("OVERVIEW") {
            overviewWitness {
                openOverview()
                observeText("Guardia pasiva", overviewWait = OverviewStage.LABEL_TEXT_WAIT,
                    overviewAssertion = OverviewStage.LABEL_TEXT_ASSERT)
                observeText("Ventanas registradas: 1", overviewWait = OverviewStage.COUNT_TEXT_WAIT,
                    overviewAssertion = OverviewStage.COUNT_TEXT_ASSERT)
                overviewStage(OverviewStage.WINDOWS_EQUAL) {
                    check(checkNotNull(a.source).windows == listOf(fixture.recipe.neighbourWindow))
                }
                exact("overview-read-only", expected)
            }
        }
        val dates = listOf(civilDate.minusDays(1), civilDate, civilDate.plusDays(1))
        val labels = listOf(AvailabilityLabel.ON_CALL_RETAINER, AvailabilityLabel.AVAILABLE_FOR_CALL, AvailabilityLabel.PASSIVE_GUARD)
        for (index in dates.indices) step("CONFIG_$index") {
            val date = dates[index]; val label = labels[index]
            val before = checkNotNull(db { store.workConfiguration.get() })
            touch("availability-configure"); await { a.surface == AvailabilitySurface.CONFIG_EDITOR }
            touch("availability-label-${label.name}"); fill("availability-config-date", date.toString())
            touch("availability-config-review"); await { a.surface == AvailabilitySurface.CONFIG_REVIEW && a.configurationReview != null }
            val review = checkNotNull(a.configurationReview)
            val original = checkNotNull(before.timeline.revisionAt(date))
            val revision = EffectiveRevision(review.revision.id, date, original.value.copy(availabilityLabel = label))
            check(review.expectedHistory.timeline.id == before.timeline.id && review.expectedHistory.timeline.revisions == before.timeline.revisions)
            check(review.revision == revision && before.timeline.revisions.none { it.effectiveFrom == date })
            observeText("Nombre: ${label.displayName}"); observeText("Vigente desde: $date")
            if (date < civilDate) observeText("Como la fecha es anterior a hoy, ese tramo histórico pasa a usar este nombre. No se crean ventanas ni se cambian sus fotografías guardadas.")
            touch("availability-config-save")
            await { a.surface == AvailabilitySurface.OVERVIEW && !a.isSaving && a.source?.history?.timeline?.revisions?.any { it == revision } == true }
            expected = oracle.expected(expected, inserted = oracle.pendingConfiguration(before.timeline.id, revision).filter { it.table == "work_configuration_revisions" })
            val after = checkNotNull(db { store.workConfiguration.get() })
            check(after.timeline.revisions == (before.timeline.revisions + revision).sortedBy { it.effectiveFrom })
            check(after.perPeriodHoursValues.entries == before.perPeriodHoursValues.entries)
            check(after.timeline.valueAt(date)?.availabilityLabel == label)
            check(after.timeline.valueAt(date.minusDays(1)) == before.timeline.valueAt(date.minusDays(1)))
            check(after.timeline.revisions.all { it.value.hoursReference == before.timeline.revisions.first().value.hoursReference })
            check(db { store.availabilityWindows.get(fixture.recipe.neighbourWindow.id) } == fixture.recipe.neighbourWindow)
            exact("configuration-$index-saved", expected)
        }
        step("EMPTY") {
            val window = fixture.recipe.neighbourWindow
            val history = checkNotNull(db { store.workConfiguration.get() })
            val exp = db { store.availabilityWindows.captureExpectation(window.id,
                ResolvedWorkConfigurationRevision.resolve(history, window.ownerLocalDate), window.start, window.end) }
            check(exp.previous == window)
            check(db { store.availabilityWindows.applyMutation(AvailabilityWindowMutation(exp, null)) } == AvailabilityWindowWriteResult.Deleted)
            expected = oracle.expected(expected, removed = listOf(oracle.availability(window)))
            await { a.loadState == AvailabilityLoadState.CONTENT && a.source?.windows?.isEmpty() == true }
            observeText("Disponible para llamado"); observeText("Ventanas registradas: 0")
            check(a.source?.calculationError == null && a.source?.totals?.programmedMinutes == 0L)
            exact("empty-source-not-error", expected)
        }
        step("REOPEN") { closeOverview(); remount(expected); openOverview(); observeText("Ventanas registradas: 0"); closeOverview(); exact("history-reopened", expected) }
        step("CLOSE") { closeSession(ownSession) }
    }

    @Test fun protectedReviewsUseFreshSourcesAndExactBreakdowns() = family("availability-reviews") {
        step("PREPARE") { start(Stage12TemporalScope(Stage12TemporalVariant.SEVERAL_DAYS)); expected = fixture.initialExpected }
        lateinit var prepared: Stage12AvailabilityFixtures.Prepared
        step("FIXTURE") { prepared = db { Stage12AvailabilityFixtures.prepare(ownSession.case, context, clock) }; expected = prepared.expected; exact("review-fixture", expected) }
        check(prepared.reviews.map { it.key } == listOf("mixed", "medical", "vacation", "prior-owner", "future"))
        for (sample in prepared.reviews) step("REVIEW_${sample.key}") {
            reviewWitness {
                reviewStage(ReviewStage.INITIAL_SOURCE_WAIT) {
                    await { a.source?.windows?.singleOrNull { it.id == sample.window.id } == sample.window }
                }
                openDay(sample.window.ownerLocalDate)
                touch("day-other-options", reviewWait = ReviewStage.OPTIONS_WAIT, reviewAction = ReviewStage.OPTIONS_TOUCH)
                touch("availability-correct-${sample.window.id}", reviewWait = ReviewStage.CORRECT_WAIT, reviewAction = ReviewStage.CORRECT_TOUCH)
                reviewStage(ReviewStage.EDITOR_WAIT) { await { a.surface == AvailabilitySurface.WINDOW_EDITOR && a.windowDraft?.recordId == sample.window.id } }
                val end = reviewStage(ReviewStage.END_BUILD) { sample.window.end.plus(1, ChronoUnit.HOURS) }
                fill("availability-end-date", reviewStage(ReviewStage.END_DATE_VALUE) { end.atZone(sample.window.zoneId).toLocalDate().toString() },
                    ReviewStage.END_DATE_WAIT, ReviewStage.END_DATE_FILL)
                fill("availability-end", reviewStage(ReviewStage.END_TIME_VALUE) { end.atZone(sample.window.zoneId).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HHmm")) },
                    ReviewStage.END_TIME_WAIT, ReviewStage.END_TIME_FILL)
                val before = reviewStage(ReviewStage.CLOCK_BEFORE) { clock.instant() }
                touch("availability-window-review", reviewWait = ReviewStage.REVIEW_WAIT, reviewAction = ReviewStage.REVIEW_TOUCH)
                reviewStage(ReviewStage.REVIEW_READY_WAIT) { await { a.surface == AvailabilitySurface.WINDOW_REVIEW && a.windowReview != null && !a.isSaving } }
                val review = reviewStage(ReviewStage.REVIEW_VALUE) { checkNotNull(a.windowReview) }
                val stamp = reviewStage(ReviewStage.STAMP_VALUE) { review.record.updatedAt }
                reviewCheck { operand ->
                    (operand(ReviewStage.CLOCK_SAMPLED) { clock.contains(stamp) } ||
                        operand(ReviewStage.CLOCK_NEXT_MILLI) { stamp == sample.window.updatedAt.plusMillis(1) }) &&
                        operand(ReviewStage.CLOCK_NOT_BEFORE) { stamp >= before.truncatedTo(ChronoUnit.MILLIS) } &&
                        operand(ReviewStage.CLOCK_AFTER_PREVIOUS) { stamp > sample.window.updatedAt }
                }
                val candidate = reviewStage(ReviewStage.CANDIDATE_BUILD) { sample.window.copy(end = end, updatedAt = stamp) }
                reviewCheck { operand ->
                    operand(ReviewStage.CANDIDATE_EQUAL) { review.record == candidate } &&
                        operand(ReviewStage.PREVIOUS_EQUAL) { review.expectation.previous == sample.window } &&
                        operand(ReviewStage.PROTECTION_EQUAL) { review.isProtected == sample.protected }
                }
                val independent = reviewStage(ReviewStage.ORACLE_INITIAL) {
                    Stage12AvailabilityOracle.expect(Stage12AvailabilityOracle.Span(candidate.start, candidate.end), sample.active, sample.protected, before)
                }
                reviewStage(ReviewStage.ORACLE_STABLE) {
                    check(independent == reviewStage(ReviewStage.ORACLE_FINAL) {
                        Stage12AvailabilityOracle.expect(Stage12AvailabilityOracle.Span(candidate.start, candidate.end), sample.active, sample.protected, clock.instant())
                    })
                }
                reviewStage(ReviewStage.BREAKDOWN_ORACLE) { reviewBreakdown(review.breakdown, independent) }
                observeBreakdown(independent)
                if (reviewStage(ReviewStage.PROTECTION_TEXT_CONDITION) { sample.protected })
                    observeText("La fecha está protegida por vacaciones o licencia médica; la ventana se conserva sin aportar disponibilidad efectiva.",
                        reviewWait = ReviewStage.PROTECTION_TEXT_WAIT, reviewObservation = ReviewStage.PROTECTION_TEXT_OBSERVE)
                headerTouch("Revisar disponibilidad", "Volver", ReviewStage.RETURN_HEADER_WAIT, ReviewStage.RETURN_HEADER_TOUCH)
                reviewStage(ReviewStage.RETURN_EDITOR_WAIT) { await { a.surface == AvailabilitySurface.WINDOW_EDITOR } }
                discardModified(candidate.id)
                exact("review-${sample.key}-discarded", expected); closeDay()
            }
        }
        step("REOPEN") { remount(expected); exact("reviews-reopened", expected) }
        step("CLOSE") { closeSession(ownSession) }
    }

    @Test fun conflictsErrorsAndConsciousDiscardPreserveTheWholeStore() = family("availability-conflicts") {
        step("PREPARE") { start(Stage12TemporalScope(Stage12TemporalVariant.MONTH_CROSSING)); expected = fixture.initialExpected }
        lateinit var target: AvailabilityWindowRecord
        step("RETAINER_CREATE") {
            val date = civilDate.minusDays(3)
            openDay(date); touch("day-other-options"); touch("day-add-retainer")
            await { a.surface == AvailabilitySurface.WINDOW_EDITOR && a.windowDraft != null }
            val draft = checkNotNull(a.windowDraft)
            check(draft.ownerDate == date && draft.labelOverride == AvailabilityLabel.ON_CALL_RETAINER)
            fill("availability-start", "2200"); fill("availability-end-date", date.plusDays(1).toString()); fill("availability-end", "0600")
            touch("availability-window-review"); await { a.surface == AvailabilitySurface.WINDOW_REVIEW && a.windowReview != null && !a.isSaving }
            val review = checkNotNull(a.windowReview); val stamp = review.record.createdAt
            check(clock.contains(stamp) && stamp == review.record.updatedAt)
            val config = ResolvedWorkConfigurationRevision.resolve(checkNotNull(db { store.workConfiguration.get() }), date)
            target = AvailabilityWindowRecord(draft.recordId, fixture.recipe.business.timelineId, WorkSector.NURSING,
                config.revision.id, date, Stage12TemporalLiteralOracle.zone, date.atTime(22, 0).atZone(Stage12TemporalLiteralOracle.zone).toInstant(),
                date.plusDays(1).atTime(6, 0).atZone(Stage12TemporalLiteralOracle.zone).toInstant(), "Retén", stamp, stamp)
            check(review.record == target && review.expectation.previous == null)
            checkEndpoints(target)
            touch("availability-window-save"); await { a.surface == AvailabilitySurface.NONE && !a.isSaving }
            expected = oracle.expected(expected, inserted = listOf(oracle.availability(target))); exact("retainer-created", expected)
            check(db { store.availabilityWindows.get(target.id) } == target)
            closeDay(); openDay(date); observeOwnedWindow(target); closeDay()
        }
        step("POST_REVIEW_CAS") {
            openDay(target.ownerLocalDate); touch("day-other-options"); touch("availability-correct-${target.id}")
            await { a.surface == AvailabilitySurface.WINDOW_EDITOR }
            fill("availability-end", "0700"); touch("availability-window-review")
            await { a.surface == AvailabilitySurface.WINDOW_REVIEW && a.windowReview != null && !a.isSaving }
            val before = target
            val competing = before.copy(end = before.end.plus(2, ChronoUnit.HOURS), updatedAt = maxOf(clock.instant().truncatedTo(ChronoUnit.MILLIS), before.updatedAt.plusMillis(1)))
            val cfg = ResolvedWorkConfigurationRevision.resolve(checkNotNull(db { store.workConfiguration.get() }), before.ownerLocalDate)
            val exp = db { store.availabilityWindows.captureExpectation(before.id, cfg, before.start, competing.end) }
            check(exp.previous == before)
            check(db { store.availabilityWindows.applyMutation(AvailabilityWindowMutation(exp, competing)) } == AvailabilityWindowWriteResult.Saved(competing))
            expected = oracle.expected(expected, removed = listOf(oracle.availability(before)), inserted = listOf(oracle.availability(competing)))
            target = competing
            val calls = owner.faults.writeCalls
            touch("availability-window-save")
            await { a.surface == AvailabilitySurface.WINDOW_EDITOR && a.windowReview == null && !a.isSaving && a.message != null }
            check(owner.faults.writeCalls == calls + 1)
            observeText("Los datos cambiaron desde la revisión. Volvé a revisar antes de guardar.")
            exact("post-review-conflict-winner-kept", expected)
            discardModified(target.id); exact("conflicted-draft-discarded", expected); closeDay()
        }
        step("EXACT_CORRECTION") {
            openDay(target.ownerLocalDate); touch("day-other-options"); touch("availability-correct-${target.id}")
            await { a.surface == AvailabilitySurface.WINDOW_EDITOR && a.windowDraft?.recordId == target.id }
            fill("availability-end", "0900"); touch("availability-window-review")
            await { a.surface == AvailabilitySurface.WINDOW_REVIEW && a.windowReview != null && !a.isSaving }
            val review = checkNotNull(a.windowReview); val before = target
            check((clock.contains(review.record.updatedAt) || review.record.updatedAt == before.updatedAt.plusMillis(1)) &&
                review.record.updatedAt > before.updatedAt)
            val corrected = before.copy(end = before.ownerLocalDate.plusDays(1).atTime(9, 0).atZone(before.zoneId).toInstant(), updatedAt = review.record.updatedAt)
            check(review.record == corrected && review.expectation.previous == before)
            checkEndpoints(corrected); touch("availability-window-save"); await { a.surface == AvailabilitySurface.NONE && !a.isSaving }
            target = corrected
            expected = oracle.expected(expected, removed = listOf(oracle.availability(before)), inserted = listOf(oracle.availability(corrected)))
            exact("retainer-corrected-exact-endpoints", expected); closeDay()
        }
        step("MODIFIED_DISCARD") {
            openDay(target.ownerLocalDate); touch("day-other-options"); touch("availability-correct-${target.id}")
            await { a.surface == AvailabilitySurface.WINDOW_EDITOR }
            fill("availability-end", "1000")
            check(a.windowDraft?.endTime == "10:00")
            discardModified(target.id); exact("modified-draft-immediate-unchanged", expected); closeDay()
        }
        step("OBSERVATION_ERROR") {
            openOverview(); val valid = checkNotNull(a.source); val calls = owner.faults.writeCalls
            owner.faults.set(Stage12AvailabilityFaults.Mode.WINDOW_OBSERVATION)
            await { a.loadState == AvailabilityLoadState.ERROR }
            check(a.source == valid && owner.faults.writeCalls == calls)
            observeText("Ventanas registradas: ${valid.windows.size}")
            // The calendar below the full-screen overview shares this state and shows its own copy of the
            // error; observe only the overview's copy, whose scroll owner also holds the window count.
            observeText("AVAILABILITY_CONTROLLED_WINDOW_OBSERVATION",
                scope = hasAnyAncestor(hasScrollAction() and hasAnyDescendant(hasText("Ventanas registradas: ${valid.windows.size}"))),
                expectedViewportTag = null)
            exact("observation-error-retains-valid", expected)
            owner.faults.set(Stage12AvailabilityFaults.Mode.NONE)
            textTouch("Reintentar", scope = hasAnyAncestor(hasScrollAction() and hasAnyDescendant(hasText("Ventanas registradas: ${valid.windows.size}"))))
            await { a.loadState == AvailabilityLoadState.CONTENT && a.source?.calculationError == null }
            closeOverview()
        }
        step("CALCULATION_ERROR") {
            openDay(target.ownerLocalDate); touch("day-other-options")
            val records = checkNotNull(a.source).windows
            owner.faults.set(Stage12AvailabilityFaults.Mode.WORK_CALCULATION_SOURCE)
            await { a.loadState == AvailabilityLoadState.CONTENT && a.source?.calculationError != null }
            check(a.source?.windows == records && a.source?.totals == null && a.source?.breakdowns?.isEmpty() == true)
            observeText("No pudimos recalcular el resultado. Las ventanas guardadas siguen visibles.", hasAnyAncestor(hasTestTag("day-detail-modal-sheet")))
            touch("availability-correct-${target.id}"); await { a.surface == AvailabilitySurface.WINDOW_EDITOR }
            val calls = owner.faults.writeCalls
            touch("availability-window-review"); await { !a.isSaving && a.message != null }
            check(a.surface == AvailabilitySurface.WINDOW_EDITOR && a.windowReview == null && owner.faults.writeCalls == calls)
            exact("calculation-source-failure-no-write", expected)
            owner.faults.set(Stage12AvailabilityFaults.Mode.NONE)
            await { a.source?.calculationError == null && a.source?.totals != null }
            discardModified(target.id); exact("calculation-error-discarded", expected); closeDay()
        }
        step("DELETE") {
            openDay(target.ownerLocalDate); touch("day-other-options"); touch("availability-delete-${target.id}")
            await { a.surface == AvailabilitySurface.DELETE_CONFIRMATION && !a.isSaving && a.deleteExpectation != null }
            check(a.deletingRecord == target && a.deleteExpectation?.previous == target)
            textTouch("Cancelar"); await { a.surface == AvailabilitySurface.NONE }; exact("delete-cancelled", expected)
            touch("availability-delete-${target.id}"); await { a.deleteExpectation?.previous == target && !a.isSaving }
            touch("availability-delete-confirm"); await { a.surface == AvailabilitySurface.NONE && !a.isSaving }
            expected = oracle.expected(expected, removed = listOf(oracle.availability(target)))
            check(db { store.availabilityWindows.get(target.id) } == null)
            exact("retainer-deleted-neighbour-and-configuration-kept", expected); closeDay()
        }
        step("REOPEN") { remount(expected); check(db { store.availabilityWindows.get(target.id) } == null); exact("conflicts-reopened", expected) }
        step("CLOSE") { closeSession(ownSession) }
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
            civilDate = LocalDate.now(clock)
            check(civilDate > LocalDate.of(2026, 1, 2))
            val identity = JSONObject().put("run_uuid", run).put("qa_sha256", qa).put("runner_sha256", runner)
            output = Stage12NativeReceiptFiles.prepareOutput(context.filesDir, run, identity.toString(), slot)
            receipt = JSONObject(identity.toString()).put("schema", "stage12-availability-lifecycle-family-v1").put("case", slot)
                .put("calendar_clock_mode", "REAL").put("phone_clock_changed", false).put("global_settings_changed", false)
                .put("host", "COMPONENT_ACTIVITY_REAL_PRODUCT_BINDINGS")
                .put("fixture_role", "FIXTURE_BASE_REUSE_WITHOUT_A119_CREDIT")
                .put("zone", clock.zone.id).put("civil_date", civilDate.toString())
                .put("main_bootstrap_proven", false)
                .put("process_death_proven", false).put("received_state", "NOT_CERTIFIED").put("post_ui_certified", false)
                .put("round_credit", 0).put("facts", facts).put("readbacks", readbacks)
                .put("text_observations", textObservations).put("back_events", backEvents)
            receipt!!.put("runtime_api", android.os.Build.VERSION.SDK_INT)
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
        db { ownCase.prepare(context, clock) }
        mount(session)
        exact("seed-mounted", ownCase.fixture.initialExpected)
    }
    private fun mount(session: Session) {
        active(); check(session.resources == null && session.owner == null)
        val resources = Stage12TemporalResources(context, session.case.opened, run, session, UUID.randomUUID(), ::retain, observe = { })
        session.resources = resources
        db { resources.prepare(clock, Stage12TemporalLiteralOracle.zone, mainReceiver = false) }
        val owner = Stage12AvailabilityOwner(session.case, resources, clock, ::retain, ::active)
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

    private fun openDay(date: LocalDate) {
        reviewStage(ReviewStage.DAY_ACTIVE) { active() }
        reviewStage(ReviewStage.DAY_CLOSED_GUARD) { check(c.detailDate == null) }
        var remaining = 36
        while (reviewStage(ReviewStage.MONTH_CONDITION) { c.visibleMonth != YearMonth.from(date) }) {
            reviewStage(ReviewStage.MONTH_BUDGET) { check(remaining-- > 0) }
            val previous = reviewStage(ReviewStage.MONTH_PREVIOUS) { c.visibleMonth }
            touch(reviewStage(ReviewStage.MONTH_TAG) { if (previous < YearMonth.from(date)) "calendar-next-month" else "calendar-previous-month" },
                reviewWait = ReviewStage.MONTH_WAIT, reviewAction = ReviewStage.MONTH_TOUCH)
            reviewStage(ReviewStage.MONTH_SETTLED_WAIT) { await { c.visibleMonth != previous && c.loadState == CalendarLoadState.CONTENT } }
        }
        val day = reviewStage(ReviewStage.DAY_LOOKUP) { c.days.single { it.date == date } }
        touch(reviewStage(ReviewStage.DAY_TAG) { if (day.isFullyCompleted) "completed-day-$date" else "day-$date" },
            reviewWait = ReviewStage.DAY_WAIT, reviewAction = ReviewStage.DAY_TOUCH)
        reviewStage(ReviewStage.DAY_OPEN_WAIT) { await { c.detailDate == date && !c.stage2Busy && shown("day-detail-modal-sheet") } }
    }
    private fun closeDay() {
        reviewStage(ReviewStage.DAY_CLOSE_ACTIVE) { active() }
        val original = reviewStage(ReviewStage.DAY_CLOSE_OWNER) { activity }
        val date = reviewStage(ReviewStage.DAY_CLOSE_DATE) { checkNotNull(c.detailDate) }
        fun alive() = activity === original && !original.isFinishing && !original.isDestroyed &&
            original.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        reviewStage(ReviewStage.DAY_CLOSE_GUARD) { check(alive()) }
        val event = reviewStage(ReviewStage.DAY_CLOSE_EVENT) {
            JSONObject().put("scope", ownSession.case.scope.wire).put("phase", phase)
                .put("before_date", c.detailDate).put("input_entered", true).put("transition_observed", false)
                .also { backEvents.put(it) }
        }
        val apiReturn = reviewStage(ReviewStage.DAY_CLOSE_BACK) { device.pressBack() }
        reviewStage(ReviewStage.DAY_CLOSE_RETURN) { event.put("api_return", apiReturn).put("input_returned", true) }
        reviewStage(ReviewStage.DAY_CLOSE_WAIT) {
            await {
                check(alive()) { "TEMPORAL_BACK_OWNER_LOST" }
                val day = c.days.single { it.date == date }
                val cell = if (day.isFullyCompleted) "completed-day-$date" else "day-$date"
                c.detailDate == null && !shown("day-detail-modal-sheet") && c.visibleMonth == YearMonth.from(date) &&
                    shown("month-grid") && compose.onNodeWithTag("month-grid").isDisplayed() &&
                    shown(cell) && compose.onNodeWithTag(cell).isDisplayed()
            }
        }
        reviewStage(ReviewStage.DAY_CLOSE_TRANSITION) {
            event.put("transition_observed", true).put("same_owner", true).put("owner_alive", true).put("calendar_return_visible", true)
        }
    }
    private fun observeText(value: String, scope: SemanticsMatcher? = null, expectedViewportTag: String? = if (scope != null) "day-detail-scroll" else null,
        overviewWait: OverviewStage? = null, overviewAssertion: OverviewStage? = null,
        reviewWait: ReviewStage? = null, reviewObservation: ReviewStage? = null) {
        val matcher = if (scope == null) hasText(value, substring = false) else hasText(value, substring = false) and scope
        reviewStage(reviewWait) {
            overviewStage(overviewWait) {
                try { await { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size == 1 } }
                catch (error: Throwable) {
                    val found = runCatching { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size }.getOrNull()
                    rememberTextFailure(value, "WAIT_FOUND_" + (found ?: "UNKNOWN"), null, error); throw error
                }
            }
        }
        reviewStage(reviewObservation) { overviewStage(overviewAssertion) {
            var textCheck = ReviewTextCheck.BETWEEN_CHECKS
            var overflow: ReviewTextOverflow? = null
            fun <T> part(check: ReviewTextCheck, body: () -> T): T {
                val previous = textCheck
                textCheck = check
                val result = body()
                textCheck = previous
                return result
            }
            fun predicate(check: ReviewTextCheck, body: () -> Boolean): Boolean {
                textCheck = check
                return body()
            }
            try {
                val node = part(ReviewTextCheck.NODE_RESOLVE) { compose.onNode(matcher, useUnmergedTree = true) }
                // isDisplayed also accepts partially clipped text; this uses the actual scroll ancestor.
                part(ReviewTextCheck.SCROLL) { node.performScrollTo() }
                part(ReviewTextCheck.DISPLAYED) { node.assertIsDisplayed() }
                part(ReviewTextCheck.EXACT_TEXT) { node.assertTextEquals(value) }
                val layouts = part(ReviewTextCheck.LAYOUT_COLLECTION) { mutableListOf<TextLayoutResult>() }
                part(ReviewTextCheck.LAYOUT_ACTION) {
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                        part(ReviewTextCheck.LAYOUT_CALLBACK) { check(action(layouts)) }
                    }
                }
                val layout = part(ReviewTextCheck.LAYOUT_HELPER) {
                    try {
                        requireTemporalTextLayout(
                            layouts, value,
                            text = { measured -> part(ReviewTextCheck.LAYOUT_TEXT_QUERY) { measured.layoutInput.text.text } },
                            hasOverflow = { measured -> part(ReviewTextCheck.LAYOUT_OVERFLOW_QUERY) { measured.stage12VisibleOverflow() } },
                        ) {
                            val paragraph = it.multiParagraph
                            val constraints = it.layoutInput.constraints
                            val lines = it.lineCount
                            val end = if (lines > 0) it.getLineEnd(lines - 1) else -1
                            val visibleEnd = if (lines > 0) it.getLineEnd(lines - 1, visibleEnd = true) else -1
                            // The original metric reads keep their order and are retained once for both projections.
                            val sizeWidth = it.size.width; val sizeHeight = it.size.height
                            val paragraphWidth = paragraph.width; val paragraphHeight = paragraph.height
                            val intrinsicMinWidth = paragraph.intrinsics.minIntrinsicWidth
                            val intrinsicMaxWidth = paragraph.intrinsics.maxIntrinsicWidth
                            val minWidth = constraints.minWidth; val maxWidth = constraints.maxWidth
                            val minHeight = constraints.minHeight; val maxHeight = constraints.maxHeight
                            val textLength = it.layoutInput.text.length
                            val ellipsis = (0 until lines).any(it::isLineEllipsized)
                            val overflowWidth = it.didOverflowWidth; val overflowHeight = it.didOverflowHeight
                            overflow = ReviewTextOverflow(overflowWidth, overflowHeight,
                                paragraphWidth > sizeWidth, paragraphHeight > sizeHeight,
                                intrinsicMaxWidth <= sizeWidth, lines == 1, end == textLength, visibleEnd == textLength, ellipsis)
                            "TEMPORAL_LAYOUT_METRICS " +
                                "size_w=$sizeWidth size_h=$sizeHeight " +
                                "paragraph_w=$paragraphWidth paragraph_h=$paragraphHeight " +
                                "intrinsic_min_w=$intrinsicMinWidth intrinsic_max_w=$intrinsicMaxWidth " +
                                "min_w=$minWidth max_w=$maxWidth " +
                                "min_h=$minHeight max_h=$maxHeight " +
                                "lines=$lines text_length=$textLength end=$end visible_end=$visibleEnd " +
                                "ellipsis=$ellipsis " +
                                "overflow_w=$overflowWidth overflow_h=$overflowHeight"
                        }
                    } catch (error: IllegalStateException) {
                        when (error.message) {
                            "TEMPORAL_TEXT_LAYOUT_COUNT_MISMATCH" -> textCheck = ReviewTextCheck.LAYOUT_COUNT
                            "TEMPORAL_TEXT_LAYOUT_VALUE_MISMATCH" -> textCheck = ReviewTextCheck.LAYOUT_VALUE
                            "TEMPORAL_TEXT_LAYOUT_OVERFLOW" -> textCheck = ReviewTextCheck.LAYOUT_OVERFLOW
                        }
                        throw error
                    }
                }
                part(ReviewTextCheck.ELLIPSIS) { check((0 until layout.lineCount).none { layout.isLineEllipsized(it) }) }
                val bounds = part(ReviewTextCheck.BOUNDS_QUERY) { node.fetchSemanticsNode().boundsInRoot }
                check(predicate(ReviewTextCheck.BOUNDS_WIDTH) { bounds.width > 0 } &&
                    predicate(ReviewTextCheck.BOUNDS_HEIGHT) { bounds.height > 0 })
                textCheck = ReviewTextCheck.BETWEEN_CHECKS
                var ancestor = part(ReviewTextCheck.ANCESTOR_QUERY) { node.fetchSemanticsNode().parent }
                var depth = 0
                while (true) {
                    val currentAncestor = ancestor ?: break
                    textCheck = ReviewTextCheck.ANCESTOR_SCROLL_BY
                    if (currentAncestor.config.contains(SemanticsActions.ScrollBy)) break
                    textCheck = ReviewTextCheck.ANCESTOR_DEPTH
                    check(depth++ < 64)
                    textCheck = ReviewTextCheck.ANCESTOR_PARENT
                    ancestor = currentAncestor.parent
                }
                textCheck = ReviewTextCheck.BETWEEN_CHECKS
                val viewport = part(ReviewTextCheck.VIEWPORT_REQUIRED) { checkNotNull(ancestor) { "TEMPORAL_TEXT_SCROLL_OWNER_REQUIRED" } }
                if (expectedViewportTag != null) {
                    val declared = part(ReviewTextCheck.DECLARED_VIEWPORT_QUERY) {
                        compose.onNodeWithTag(expectedViewportTag, useUnmergedTree = true).fetchSemanticsNode()
                    }
                    part(ReviewTextCheck.DECLARED_VIEWPORT_EQUAL) { check(viewport.id == declared.id) { "TEMPORAL_TEXT_SCROLL_OWNER_FOREIGN" } }
                }
                val uncut = part(ReviewTextCheck.UNCUT_BOUNDS) { node.getUnclippedBoundsInRoot() }
                val density = part(ReviewTextCheck.DENSITY) { compose.density.density }
                val physical = part(ReviewTextCheck.PHYSICAL_BOUNDS) {
                    androidx.compose.ui.geometry.Rect(uncut.left.value * density, uncut.top.value * density,
                        uncut.right.value * density, uncut.bottom.value * density)
                }
                val visible = part(ReviewTextCheck.VIEWPORT_BOUNDS) { viewport.boundsInRoot }
                check(predicate(ReviewTextCheck.VIEWPORT_LEFT) { physical.left >= visible.left - 0.5f } &&
                    predicate(ReviewTextCheck.VIEWPORT_TOP) { physical.top >= visible.top - 0.5f } &&
                    predicate(ReviewTextCheck.VIEWPORT_RIGHT) { physical.right <= visible.right + 0.5f } &&
                    predicate(ReviewTextCheck.VIEWPORT_BOTTOM) { physical.bottom <= visible.bottom + 0.5f }) { "TEMPORAL_TEXT_CLIPPED_BY_SCROLL" }
                textCheck = ReviewTextCheck.BETWEEN_CHECKS
                part(ReviewTextCheck.RECORD_APPEND) {
                    textObservations.put(part(ReviewTextCheck.RECORD_BUILD) {
                        JSONObject().put("scope", ownSession.case.scope.wire).put("phase", phase).put("text", value)
                            .put("node_id", part(ReviewTextCheck.RECORD_NODE_QUERY) { node.fetchSemanticsNode().id }).put("scroll_node_id", viewport.id)
                            .put("unclipped_px", JSONArray(listOf(physical.left, physical.top, physical.right, physical.bottom)))
                            .put("viewport_px", JSONArray(listOf(visible.left, visible.top, visible.right, visible.bottom)))
                            .put("line_count", layout.lineCount).put("visual_overflow", layout.hasVisualOverflow)
                            .put("ellipsized", false).put("density", density)
                            .put("font_scale", part(ReviewTextCheck.RECORD_FONT_SCALE) { activity.resources.configuration.fontScale })
                    })
                }
            } catch (error: Throwable) {
                rememberReviewText(reviewObservation, textCheck, overflow, error)
                rememberOverviewText(overviewAssertion, textCheck, overflow, error)
                rememberTextFailure(value, textCheck.name, overflow, error)
                throw error
            }
        } }
    }

    private fun <T> step(name: String, body: () -> T): T {
        active(); phase = name
        check(LocalDate.now(clock) == civilDate) { "AVAILABILITY_CIVIL_DATE_CHANGED" }
        val event = JSONObject().put("step", name).put("state", "ENTERED")
        operations.put(event)
        return try {
            val value = trace.checked(Stage12SelectionDiagnostics.Checkpoint.RULE_EVALUATION, body)
            event.put("state", "RETURNED")
            facts.put(JSONObject().put("key", name).put("check", "PASS").put("own_property_only", true))
            value
        } catch (error: Throwable) {
            event.put("state", "THREW"); throw retain(error)
        }
    }
    private fun openOverview() {
        if (!overviewWitnessActive) {
            check(c.detailDate == null && a.surface == AvailabilitySurface.NONE)
        } else {
            var operand = OverviewStage.GUARD_DETAIL_QUERY
            var rejected: OverviewStage? = null
            try {
                check((c.detailDate == null).also { value ->
                    if (value) operand = OverviewStage.GUARD_SURFACE_QUERY
                    else rejected = OverviewStage.GUARD_DETAIL_REJECTED
                } && (a.surface == AvailabilitySurface.NONE).also { value ->
                    if (!value) rejected = OverviewStage.GUARD_SURFACE_REJECTED
                })
            } catch (error: Throwable) {
                rememberOverview(rejected ?: operand)
                throw error
            }
        }
        touch("main-menu-button", OverviewStage.MENU_WAIT, OverviewStage.MENU_TOUCH)
        touch("drawer-action-work-setup", OverviewStage.DRAWER_WAIT, OverviewStage.DRAWER_TOUCH)
        overviewStage(OverviewStage.WORK_OVERVIEW_WAIT) { waitTag("work-setup-overview") }
        if (!overviewStage(OverviewStage.ADVANCED_QUERY) { shown("work-setup-availability") })
            touch("advanced-options-toggle", OverviewStage.ADVANCED_WAIT, OverviewStage.ADVANCED_TOUCH)
        touch("work-setup-availability", OverviewStage.AVAILABILITY_WAIT, OverviewStage.AVAILABILITY_TOUCH)
        overviewStage(OverviewStage.SURFACE_WAIT) {
            await { a.surface == AvailabilitySurface.OVERVIEW && a.loadState == AvailabilityLoadState.CONTENT }
        }
    }
    private fun closeOverview() {
        check(a.surface == AvailabilitySurface.OVERVIEW && !a.isSaving)
        headerTouch("Guardias pasivas y disponibilidad", "Cerrar")
        await { a.surface == AvailabilitySurface.NONE }
        headerTouch("Mi forma de trabajar", "Cerrar")
        await { owner.work.uiState.value.surface == com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface.NONE }
    }
    private fun headerTouch(title: String, text: String, reviewWait: ReviewStage? = null, reviewAction: ReviewStage? = null) {
        val match = hasText(text, substring = false) and hasClickAction() and hasAnySibling(hasText(title, substring = false))
        reviewStage(reviewWait) { await { compose.onAllNodes(match).fetchSemanticsNodes().size == 1 } }
        reviewStage(reviewAction) {
            trace.checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) {
                compose.onNode(match).assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
            }
        }
    }
    private fun discardModified(id: UUID) {
        reviewCheck { operand ->
            operand(ReviewStage.DISCARD_SURFACE) { a.surface == AvailabilitySurface.WINDOW_EDITOR } &&
                operand(ReviewStage.DISCARD_ID) { a.windowDraft?.recordId == id } &&
                operand(ReviewStage.DISCARD_NOT_SAVING) { !a.isSaving }
        }
        val draft = reviewStage(ReviewStage.DISCARD_DRAFT) { checkNotNull(a.windowDraft) }
        val original = reviewStage(ReviewStage.DISCARD_OWNER) { activity }
        var requestOrdinal = 0
        fun request() {
            val first = requestOrdinal++ == 0
            reviewCheck { operand ->
                operand(if (first) ReviewStage.DISCARD_FIRST_OWNER else ReviewStage.DISCARD_SECOND_OWNER) { activity === original } &&
                    operand(if (first) ReviewStage.DISCARD_FIRST_STARTED else ReviewStage.DISCARD_SECOND_STARTED) {
                        original.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    }
            }
            var visible = true
            reviewStage(if (first) ReviewStage.DISCARD_FIRST_IME_WAIT else ReviewStage.DISCARD_SECOND_IME_WAIT) {
                await {
                    owner.onActivity { view -> visible = androidx.core.view.ViewCompat.getRootWindowInsets(view.window.decorView)
                        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) ?: true }
                    !visible
                }
            }
            val event = reviewStage(if (first) ReviewStage.DISCARD_FIRST_EVENT else ReviewStage.DISCARD_SECOND_EVENT) {
                JSONObject().put("scope", ownSession.case.scope.wire).put("phase", phase)
                    .put("kind", "DRAFT_BACK_TO_DISCARD").put("input_entered", true)
                    .also { backEvents.put(it) }
            }
            val returned = reviewStage(if (first) ReviewStage.DISCARD_FIRST_BACK else ReviewStage.DISCARD_SECOND_BACK) {
                trace.checked(Stage12SelectionDiagnostics.Checkpoint.NATIVE_ASSERTION) { device.pressBack() }
            }
            reviewStage(if (first) ReviewStage.DISCARD_FIRST_RETURN else ReviewStage.DISCARD_SECOND_RETURN) {
                event.put("api_return", returned).put("input_returned", true)
            }
            reviewStage(if (first) ReviewStage.DISCARD_FIRST_DIALOG_WAIT else ReviewStage.DISCARD_SECOND_DIALOG_WAIT) {
                await { a.showDiscardConfirmation && shown("availability-discard-confirm") }
            }
            reviewStage(if (first) ReviewStage.DISCARD_FIRST_TRANSITION else ReviewStage.DISCARD_SECOND_TRANSITION) {
                event.put("transition_observed", true)
            }
            reviewCheck { operand ->
                operand(if (first) ReviewStage.DISCARD_FIRST_SAME_OWNER else ReviewStage.DISCARD_SECOND_SAME_OWNER) { activity === original } &&
                    operand(if (first) ReviewStage.DISCARD_FIRST_SAME_DRAFT else ReviewStage.DISCARD_SECOND_SAME_DRAFT) { a.windowDraft == draft }
            }
        }
        request(); textTouch("Seguir editando", ReviewStage.KEEP_EDITING_WAIT, ReviewStage.KEEP_EDITING_TOUCH)
        reviewStage(ReviewStage.KEEP_EDITING_RETURN_WAIT) {
            await { !a.showDiscardConfirmation && a.surface == AvailabilitySurface.WINDOW_EDITOR && a.windowDraft == draft }
        }
        request(); touch("availability-discard-confirm", reviewWait = ReviewStage.DISCARD_CONFIRM_WAIT, reviewAction = ReviewStage.DISCARD_CONFIRM_TOUCH)
        reviewStage(ReviewStage.DISCARD_COMPLETE_WAIT) {
            await { a.surface == AvailabilitySurface.NONE && a.windowDraft == null && !a.showDiscardConfirmation }
        }
        reviewCheck { operand ->
            operand(ReviewStage.DISCARD_FINAL_OWNER) { activity === original } &&
                operand(ReviewStage.DISCARD_FINAL_STARTED) { original.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        }
    }
    private fun endpoints(record: AvailabilityWindowRecord): Pair<String, String> {
        val format = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        return record.start.atZone(record.zoneId).format(format) to record.end.atZone(record.zoneId).format(format)
    }
    private fun checkEndpoints(record: AvailabilityWindowRecord) {
        val (start, end) = endpoints(record)
        observeText("Inicio: $start"); observeText("Final: $end")
        observeText("Duración programada: ${record.durationMinutes / 60} h ${record.durationMinutes % 60} min")
    }
    private fun observeOwnedWindow(record: AvailabilityWindowRecord) {
        check(c.detailDate == record.ownerLocalDate)
        val (start, end) = endpoints(record)
        val match = hasTestTag("availability-readonly-${record.id}") and hasAnyAncestor(hasTestTag("day-condition-card"))
        observeText("${record.labelSnapshot} · $start – $end", match)
        check(db { store.availabilityWindows.get(record.id) } == record)
    }
    private fun observeBreakdown(value: Stage12AvailabilityOracle.Expected) {
        val labels = listOf("Programada", "Efectiva transcurrida", "Reemplazada por trabajo activo", "Futura pendiente",
            "Efectiva proyectada al final", "Futura ocupada por trabajo planificado")
        val values = listOf(value.programmed, value.effective, value.replaced, value.pending, value.projected, value.futureReplaced)
        val stages = listOf(
            ReviewStage.PROGRAMMED_TEXT_WAIT to ReviewStage.PROGRAMMED_TEXT_OBSERVE,
            ReviewStage.EFFECTIVE_TEXT_WAIT to ReviewStage.EFFECTIVE_TEXT_OBSERVE,
            ReviewStage.REPLACED_TEXT_WAIT to ReviewStage.REPLACED_TEXT_OBSERVE,
            ReviewStage.PENDING_TEXT_WAIT to ReviewStage.PENDING_TEXT_OBSERVE,
            ReviewStage.PROJECTED_TEXT_WAIT to ReviewStage.PROJECTED_TEXT_OBSERVE,
            ReviewStage.FUTURE_REPLACED_TEXT_WAIT to ReviewStage.FUTURE_REPLACED_TEXT_OBSERVE,
        )
        labels.zip(values).forEachIndexed { index, (label, minutes) ->
            observeText("$label: ${minutes / 60} h ${minutes % 60} min", reviewWait = stages[index].first, reviewObservation = stages[index].second)
        }
    }
    private fun exact(stage: String, expected: Stage12StoreSnapshot) {
        lastAction = "EXACT:$stage"
        reviewStage(ReviewStage.READBACK_ACTIVE) { active() }
        val read = reviewStage(ReviewStage.READBACK_CAPTURE) { overviewStage(OverviewStage.READBACK_CAPTURE) {
            trace.checked(Stage12SelectionDiagnostics.Checkpoint.CATALOG_READ) {
                Stage12FullStoreReadback.captureTemporalOwned(context, run, ownSession.case.scope, ownSession.case.binding)
            }
        } }
        reviewStage(ReviewStage.READBACK_COMPARE) { overviewStage(OverviewStage.READBACK_COMPARE) {
            trace.checked(Stage12SelectionDiagnostics.Checkpoint.CATALOG_COMPARE) { oracle.requireExact(read, expected) }
        } }
        reviewStage(ReviewStage.READBACK_RECORD) { overviewStage(OverviewStage.READBACK_RECORD) {
            readbacks.put(JSONObject().put("scope", ownSession.case.scope.wire).put("operation", phase).put("stage", stage)
                .put("tables_sha256", hash(read.tables.toString())).put("preferences_sha256", hash(read.preferences.toString()))
                .put("files_sha256", hash(read.files.toString())).put("all_tables", read.tables.size).put("file_entries", read.files.size))
        } }
    }
    private fun active() { firstNegative.requireActive(); trace.primary?.let { throw it } }
    private fun await(condition: () -> Boolean) { active(); compose.waitUntil(30_000) { active(); condition() } }
    private fun shown(tag: String): Boolean = trace.checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size.also { check(it in 0..1) { "AVAILABILITY_NODE_AMBIGUOUS" } } == 1
    }
    private fun waitTag(tag: String) = await { shown(tag) }
    private fun touch(tag: String, overviewWait: OverviewStage? = null, overviewAction: OverviewStage? = null,
        reviewWait: ReviewStage? = null, reviewAction: ReviewStage? = null) {
        active()
        lastAction = "TOUCH:$tag"
        reviewStage(reviewWait) { overviewStage(overviewWait) { waitTag(tag) } }
        lastAction = "TOUCH_ACT:$tag"
        reviewStage(reviewAction) { overviewStage(overviewAction) {
            val node = compose.onNodeWithTag(tag)
            if (!trace.checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { node.isDisplayed() })
                trace.checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL) { node.performScrollTo() }
            trace.checked(Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { node.assertIsDisplayed() }
            trace.checked(Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { node.assertIsEnabled() }
            trace.checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) { node.performTouchInput { click() } }
        } }
    }
    private fun textTouch(value: String, reviewWait: ReviewStage? = null, reviewAction: ReviewStage? = null,
        scope: SemanticsMatcher? = null) {
        val selector = hasText(value, substring = false) and hasClickAction() and (scope ?: SemanticsMatcher("ANY") { true })
        lastAction = "TEXT_TOUCH:$value"
        reviewStage(reviewWait) {
            try { await { compose.onAllNodes(selector).fetchSemanticsNodes().size == 1 } }
            catch (error: Throwable) {
                val found = runCatching { compose.onAllNodes(selector).fetchSemanticsNodes().size }.getOrNull()
                lastAction = "TEXT_TOUCH:$value found=${found ?: "UNKNOWN"}"; throw error
            }
        }
        lastAction = "TEXT_TOUCH_ACT:$value"
        reviewStage(reviewAction) {
            val node = compose.onNode(selector)
            if (!node.isDisplayed()) node.performScrollTo()
            trace.checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) { node.assertIsDisplayed().assertIsEnabled().performTouchInput { click() } }
        }
    }
    private fun fill(tag: String, value: String, reviewWait: ReviewStage? = null, reviewAction: ReviewStage? = null) {
        reviewStage(reviewWait) { waitTag(tag) }
        reviewStage(reviewAction) {
            val node = compose.onNodeWithTag(tag)
            if (!node.isDisplayed()) node.performScrollTo()
            trace.checked(Stage12SelectionDiagnostics.Checkpoint.NODE_REPLACE_TEXT) { node.assertIsDisplayed().assertIsEnabled().performTextReplacement(value) }
            trace.checked(Stage12SelectionDiagnostics.Checkpoint.HIDE_OWN_IME) {
                owner.onActivity { activity ->
                    activity.currentFocus?.clearFocus()
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                }
            }
        }
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
