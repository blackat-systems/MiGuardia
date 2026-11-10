package com.blackatsystems.miguardia.stage12

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.ui.availability.AvailabilityLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.weather.WeatherSurface
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
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

/** C030/C034 only: real QA ComponentActivity/input/recomposer, fixed own business date, no Main or G claim. */
class Stage12CalendarDetailNativeTest {
    private enum class Stage { ENTRY, PREPARE, SEED_TARGET, MOUNT, READY, OPEN_DAY, STABLE_DETAIL,
        CARDS, WEATHER_OPEN, WEATHER_STATE, WEATHER_CLOSE, WEATHER_RETURN, BACK, READBACK,
        CLOSE_OWNER, CLOSE_AUXILIARY, CLOSE_STORE, REOPEN, CLEANUP, COMPLETE }
    private enum class CardRole { TARGET, NEIGHBOR }
    private enum class CardCheck {
        CARD_CARDINALITY, RANGE_READINESS, RANGE_CARDINALITY, RANGE_RESOLVE, RANGE_VISIBILITY_QUERY,
        RANGE_SCROLL, RANGE_VISIBLE, RANGE_EXACT_TEXT, RANGE_NODE_READ, RANGE_BOUNDS_READ,
        RANGE_WIDTH, RANGE_HEIGHT, RANGE_RECORD, POST_SHEET_CARDINALITY, POST_DATE_READ, POST_DATE,
    }
    private data class CardsDiagnostic(val card: CardRole, val check: CardCheck)
    private var cardsDiagnostic: CardsDiagnostic? = null
    private fun <T> cardCheck(card: CardRole?, check: CardCheck, body: () -> T): T = try {
        body()
    } catch (original: Throwable) {
        // Retain only the first reached failing check, without another query or a replacement Throwable.
        if (card != null && cardsDiagnostic == null &&
            (firstFailureStage == null || firstFailureStage == Stage.CARDS) &&
            (trace.primary == null || trace.primary === original)) {
            try { cardsDiagnostic = CardsDiagnostic(card, check) }
            catch (accessory: Throwable) { try { original.addSuppressed(accessory) } catch (_: Throwable) {} }
        }
        throw original
    }
    private val trace = Stage12SelectionDiagnostics.Trace()
    private var stage = Stage.ENTRY
    private var firstFailureStage: Stage? = null
    private val failure = Stage12TemporalFailure {
        if (firstFailureStage == null) firstFailureStage = stage
        trace.retain(it)
    }
    private fun retain(error: Throwable): Throwable = failure.remember(error)
    private fun active() { failure.requireActive(); trace.primary?.let { throw it } }
    private fun <T> step(next: Stage, body: () -> T): T {
        active(); stage = next
        return trace.checked(Stage12SelectionDiagnostics.Checkpoint.RULE_EVALUATION) { body() }
    }
    private val compose by lazy { trace.checked(Stage12SelectionDiagnostics.Checkpoint.UNMARKED) { createEmptyComposeRule() } }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val contract get() = Stage12CalendarDetailContract
    private val clock = Stage12CalendarDetailContract.clock()
    private lateinit var run: String
    private var ownCase: Stage12TemporalCase? = null
    private var resources: Stage12TemporalResources? = null
    private var owner: Stage12TemporalComponentOwner? = null
    private val activity: ComponentActivity get() = checkNotNull(checkNotNull(owner).activity)
    private val case get() = checkNotNull(ownCase)
    private val store get() = case.opened.requireBinding(case.binding)
    private val recipe get() = case.fixture.recipe
    private var closeAttempted = false
    private var ownerClosed = false
    private var auxiliaryClosed = false
    private var originalStoreClosed = false
    private var reopenedStoreClosed = false
    private var cleanup: (() -> Unit)? = null
    private var output: File? = null
    private var receipt: JSONObject? = null
    private val facts = JSONArray()
    private val events = JSONArray()
    private val readbacks = JSONArray()
    private val semantics = JSONArray()
    private lateinit var target: V2ShiftWrite
    private var activationCase = false
    private var weatherEnabled = false
    private var activationBaseline: Stage12StoreSnapshot? = null
    private var activationExpected: Stage12StoreSnapshot? = null
    private val selector get() = if (activationCase) contract.ACTIVATION_SELECTOR else contract.SELECTOR
    private val slot get() = if (activationCase) contract.ACTIVATION_SLOT else contract.SLOT
    private val keys get() = if (activationCase) contract.activationKeys else contract.keys

    @get:Rule val rules: TestRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                trace.finish({ compose.apply(base, description).evaluate() }, { checkNotNull(cleanup).invoke() }, { succeeded, cleaned, primary ->
                    receipt?.let { value ->
                        value.put("status", if (primary == null && cleaned) "COMPLETE" else "FAILED")
                            .put("rule_evaluation_succeeded", succeeded).put("receipt_completion_scope", "WHOLE_TEST_RULE")
                            .put("cleanup_complete", cleaned).put("owned_resources_retained_on_failure", !cleaned)
                            .put("stage", stage.name).put("first_failure_stage", firstFailureStage?.name ?: JSONObject.NULL)
                            .put("diagnostic", JSONObject(trace.projection()))
                            .put("preparation", ownCase?.let { JSONObject(it.preparation.projection()) } ?: JSONObject.NULL)
                            .put("fixture_prefix", ownCase?.let { JSONObject(it.fixturePrefix.projection()) } ?: JSONObject.NULL)
                            .put("resources_preparation", resources?.let { JSONObject(it.projection) } ?: JSONObject.NULL)
                            .put("closure", JSONObject().put("attempted", closeAttempted).put("owner", ownerClosed)
                                .put("auxiliary", auxiliaryClosed).put("original_store", originalStoreClosed)
                                .put("reopened_store", reopenedStoreClosed))
                        if (primary != null) cardsDiagnostic?.let { diagnostic ->
                            try {
                                value.put("cards_diagnostic", JSONObject().put("card", diagnostic.card.name).put("check", diagnostic.check.name))
                            } catch (accessory: Throwable) {
                                try { primary.addSuppressed(accessory) } catch (_: Throwable) {}
                                throw primary
                            }
                        }
                        Stage12NativeReceiptFiles.publish(checkNotNull(output), value.toString(2), primary)
                    }
                })
                trace.primary?.let { throw Stage12SelectionDiagnostics.publicFailure() }
            }
        }
    }

    @Test fun ownedWeatherReturnAndTodayFutureReopenStayStable() {
        runCalendarDetail()
    }

    @Test fun enablingWeatherFromDayCardPersistsAndReopensWithoutLocation() {
        activationCase = true
        runCalendarDetail()
    }

    private fun runCalendarDetail() {
        try {
            initializeReceipt()
            step(Stage.PREPARE) {
                val created = Stage12TemporalCase(run, contract.scope(), ::retain); ownCase = created
                db { created.prepare(context, clock) }
            }
            val expected = step(Stage.SEED_TARGET) {
                exact("seed-before-target", case.fixture.initialExpected)
                check(recipe.ownerDate == contract.today && recipe.zone == clock.zone && recipe.scope == contract.scope())
                check(recipe.business.objective.weatherLatitude == null && recipe.business.objective.weatherLongitude == null)
                val id = recipe.business.id("calendar-detail-target")
                val expectedTarget = recipe.business.expectedShift(recipe.literal, id)
                val write = recipe.business.buildShift(recipe.literal, id)
                check(write == expectedTarget && id != recipe.neighbourShift.shift.id)
                check(expectedTarget.shift.localStartDate == contract.today &&
                    expectedTarget.shift.sourceObjectiveId == recipe.neighbourShift.shift.sourceObjectiveId &&
                    expectedTarget.shift.startAt.toString() == "2026-10-23T11:15:00Z" &&
                    expectedTarget.shift.endAt.toString() == "2026-10-23T19:45:00Z")
                check(db { store.v2Shifts.getShift(id) } == V2ShiftLookup.Missing)
                val expected = Stage12RecurringReadbackOracle.expected(case.fixture.initialExpected,
                    inserted = Stage12RecurringReadbackOracle.write(expectedTarget))
                db { store.v2Shifts.insert(write) }
                check(db { store.v2Shifts.getShift(id) } == V2ShiftLookup.V2(expectedTarget))
                target = expectedTarget
                checkNotNull(receipt).put("fixture_correspondence", JSONObject()
                    .put("cases", JSONArray(if (activationCase) listOf("C081_PARTIAL_ENABLE_WITHOUT_LOCATION") else listOf("C030", "C034"))).put("target_uuid", id)
                    .put("neighbour_uuid", recipe.neighbourShift.shift.id).put("objective_uuid", recipe.business.objectiveId)
                    .put("owner_date", contract.today).put("future_date", contract.future)
                    .put("target_local_start", "08:15").put("target_local_end", "16:45")
                    .put("neighbour_local_start", "05:00").put("neighbour_local_end", "05:30")
                    .put("distinct_uuids", true).put("same_objective", true)
                    .put("v5_primary_to_target", JSONObject().put("C030", "feaf8293-c6e6-5122-9a74-e0129caf2b74")
                        .put("C034", "a990d7c8-5c34-5643-bf85-3103fc7c6d54"))
                    .put("v5_second_to_neighbour", JSONObject().put("C030", "682d1799-9d4d-5efe-a8d2-84ec9b5195ef")
                        .put("C034", "dd9ae83f-6c16-5ab0-8caf-5bd66c34e13b")))
                exact("target-seeded", expected)
                expected
            }
            step(Stage.MOUNT) {
                val prepared = Stage12TemporalResources(context, case.opened, run, this, UUID.randomUUID(), ::retain, observe = { })
                resources = prepared
                db { prepared.prepare(clock, clock.zone, mainReceiver = false) }
                val mounted = Stage12TemporalComponentOwner(case, prepared, ::retain, ::active, calendarWeatherOptIn = true)
                owner = mounted; mounted.launch()
            }
            step(Stage.READY) {
                await {
                    val s = state()
                    s.calendar.loadState == CalendarLoadState.CONTENT && !s.weather.isLoading &&
                        checkNotNull(owner).availability.uiState.value.loadState == AvailabilityLoadState.CONTENT
                }
                val s = state()
                check(s.calendar.visibleMonth == YearMonth.of(2026, 10) && s.calendar.referenceInstant == contract.reference)
                check(s.calendar.detailDate == null && s.weather.surface == WeatherSurface.NONE && !s.weather.preferences.enabled)
                check(s.calendar.days.single { it.date == contract.today }.shifts.map { it.shift }.toSet() ==
                    setOf(target.shift, recipe.neighbourShift.shift))
                check(s.calendar.days.single { it.date == contract.future }.shifts.isEmpty())
            }
            if (activationCase) activationBaseline = expected
            val todayEvents = mutableListOf<Int>()
            val futureEvents = mutableListOf<Int>()
            val weatherEvents = mutableListOf<Int>()
            for (date in listOf(contract.today, contract.future)) {
                repeat(2) { opening ->
                    val opened = openDay(date, opening + 1)
                    if (date == contract.today) {
                        todayEvents += opened
                        observeCards()
                        weatherEvents += weatherRoundTrip(opening + 1)
                    } else futureEvents += opened
                    closeDay(date)
                }
            }
            val finalExpected = if (activationCase) checkNotNull(activationExpected) else expected
            exact("after-all-ui-returned", finalExpected)
            step(Stage.READY) {
                val s = state()
                check(s.calendar.detailDate == null && s.weather.surface == WeatherSurface.NONE && s.weather.preferences.enabled == weatherEnabled)
                check(s.callbacks.selectedDates == listOf(contract.today, contract.today, contract.future, contract.future))
                check(s.callbacks.selectionReturns == 4 && s.callbacks.dismissCalls == 4 && s.callbacks.dismissReturns == 4)
                check(s.callbacks.weatherIds == listOf(target.shift.id, target.shift.id) && s.callbacks.weatherOpenReturns == 2 &&
                    s.callbacks.weatherCloseCalls == 2 && s.callbacks.weatherCloseReturns == 2)
            }
            if (activationCase) {
                addFacts("C081-habilitar-clima-desde-tarjeta", weatherEvents)
                addFacts("C081-reabrir-clima-habilitado", weatherEvents)
            } else {
                addFacts("C030-abrir-clima-desde-fecha", weatherEvents)
                addFacts("C030-cerrar-retorna-mismo-dia", weatherEvents)
                addFacts("C034-hoy-cerrar-reabrir-nativo", todayEvents)
                addFacts("C034-futuro-cerrar-reabrir-nativo", futureEvents)
                addFacts("C034-clima-retorno-animacion-real", weatherEvents)
            }
            check((0 until facts.length()).map { facts.getJSONObject(it).getString("assertion_key") } == keys)
            closeOwned()
            step(Stage.REOPEN) {
                val original = case.opened
                case.reopenClosedStore(context)
                check(case.opened !== original && original.isClosed)
                exact("same-store-reopened-after-owner-closure", finalExpected)
                if (activationCase) {
                    requirePersistedWeatherActivation(finalExpected)
                    checkNotNull(receipt).put("weather_activation", JSONObject()
                        .put("variant", "OBJECTIVE_WITHOUT_LOCATION")
                        .put("enabled_and_explanation_persisted_after_owner_and_store_reopen", true)
                        .put("exact_boolean_keys", 2).put("business_tables_equal", true)
                        .put("other_preferences_and_files_equal", true).put("user_failure_reproduced", false)
                        .put("real_network_or_location_tested", false).put("full_C081_C084_credit", false))
                }
                case.opened.close(); check(case.opened.isClosed); reopenedStoreClosed = true
            }
            cleanup = { cleanupClosed() }
            stage = Stage.COMPLETE
        } catch (error: Throwable) { retain(error) }
        finally {
            if (ownCase != null && !closeAttempted) {
                try { trace.closing(Stage12SelectionDiagnostics.Checkpoint.CLOSE_OWNER) { closeOwned() } }
                catch (error: Throwable) { retain(error) }
            }
            // A failed reopened readback still closes the exact owned Room handle, without cleanup or further reads.
            if (originalStoreClosed && ownCase?.hasStore == true && !case.opened.isClosed) {
                try { case.opened.close(); reopenedStoreClosed = case.opened.isClosed } catch (error: Throwable) { retain(error) }
            }
        }
        trace.primary?.let { throw it }
    }

    private fun initializeReceipt() {
        check(instrumentation is Stage12NormalQaRunner && context.packageName == QA)
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("class") == selector && args.getString("stage12CalendarClock") == "REAL")
        check(android.os.Build.VERSION.SDK_INT == 37)
        run = checkNotNull(args.getString("stage12RunId")); check(UUID.fromString(run).toString() == run)
        val qa = checkNotNull(args.getString("stage12QaSha256")); val runner = checkNotNull(args.getString("stage12RunnerSha256"))
        check(listOf(qa, runner).all { it.matches(Regex("[0-9a-f]{64}")) })
        val identity = JSONObject().put("run_uuid", run).put("qa_sha256", qa).put("runner_sha256", runner)
        output = Stage12NativeReceiptFiles.prepareOutput(context.filesDir, run, identity.toString(), slot)
        receipt = JSONObject(identity.toString()).put("schema", contract.SCHEMA).put("case", slot)
            .put("selector", selector).put("status", "INCOMPLETE").put("runtime_api", android.os.Build.VERSION.SDK_INT)
            .put("calendar_clock_mode", "REAL").put("business_clock_mode", "FIXED_OWN_MODEL_ONLY")
            .put("business_reference_instant", contract.reference).put("zone", clock.zone.id)
            .put("animation_clock", "ANDROID_ELAPSED_REALTIME").put("compose_clock_advanced", false)
            .put("fixture_scope", contract.scope().wire).put("fixture_role", contract.FIXTURE_ROLE)
            .put("host", "REAL_COMPONENT_ACTIVITY").put("main_bootstrap_proven", false).put("process_death_proven", false)
            .put("phone_clock_changed", false).put("global_settings_changed", false).put("screenshots", false)
            .put("received_state", "NOT_CERTIFIED").put("post_ui_certified", false).put("round_credit", 0)
            .put("c026_credit", false).put("facts", facts).put("events", events).put("readbacks", readbacks)
            .put("semantics", semantics)
    }

    private data class State(val calendar: CalendarUiState, val weather: WeatherUiState,
        val callbacks: Stage12TemporalComponentOwner.CalendarCallbacks)
    private fun state(): State {
        active(); var result: State? = null
        checkNotNull(owner).onActivity { a ->
            check(a === activity && !a.isDestroyed && !a.isFinishing && a.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            result = State(checkNotNull(owner).calendar.uiState.value, checkNotNull(checkNotNull(owner).weather).uiState.value,
                checkNotNull(owner).calendarCallbacks())
        }
        return checkNotNull(result)
    }
    private fun openDay(date: LocalDate, opening: Int): Int = step(Stage.OPEN_DAY) {
        val before = state()
        check(before.calendar.detailDate == null && before.weather.surface == WeatherSurface.NONE && count("day-detail-modal-sheet") == 0)
        check(before.calendar.visibleMonth == YearMonth.from(date))
        val day = before.calendar.days.single { it.date == date }
        val tag = if (day.isFullyCompleted) "completed-day-$date" else "day-$date"
        val event = JSONObject().put("kind", "OPEN_DAY").put("date", date).put("opening", opening)
            .put("transition_before", "CLOSED").put("transition_observed", false)
        val index = events.length(); events.put(event)
        touchNative(tag, event)
        await { state().calendar.detailDate == date && count("day-detail-modal-sheet") == 1 && visible("day-detail-modal-sheet") }
        val after = state()
        check(after.callbacks.selectedDates == before.callbacks.selectedDates + date &&
            after.callbacks.selectionReturns == before.callbacks.selectionReturns + 1 &&
            after.callbacks.dismissCalls == before.callbacks.dismissCalls)
        event.put("selection_callbacks", 1).put("selection_callback_returned", true).put("transition_observed", true)
        stableDetail(date, event)
        index
    }
    private fun dayTitle(date: LocalDate) = when (date) {
        contract.today -> "Viernes 23 de octubre de 2026"
        contract.future -> "Sábado 24 de octubre de 2026"
        else -> error("CALENDAR_DETAIL_DATE_OUTSIDE_CONTRACT")
    }
    private fun titleMatcher(date: LocalDate) = hasText(dayTitle(date), substring = false) and hasAnyAncestor(hasTestTag("day-detail-scroll"))
    private fun stableDetail(date: LocalDate, event: JSONObject) = step(Stage.STABLE_DETAIL) {
        val title = compose.onNode(titleMatcher(date), useUnmergedTree = true)
        check(compose.onAllNodes(titleMatcher(date), useUnmergedTree = true).fetchSemanticsNodes().size == 1)
        if (!title.isDisplayed()) title.performScrollTo()
        title.assertIsDisplayed().assertTextEquals(dayTitle(date))
        val began = SystemClock.elapsedRealtime(); val deadline = began + contract.DEADLINE_MS
        var samples = 0; var previous: Rect? = null; var unchangedSince = began
        var finalBounds: Rect? = null
        while (true) {
            ready()
            val s = state()
            check(s.calendar.detailDate == date && !s.calendar.stage2Busy && s.weather.surface == WeatherSurface.NONE)
            check(count("day-detail-modal-sheet") == 1 && count("day-detail-scroll") == 1 && count("weather-dialog-surface") == 0)
            compose.onNodeWithTag("day-detail-modal-sheet").assertIsDisplayed()
            title.assertIsDisplayed().assertTextEquals(dayTitle(date))
            val bounds = compose.onNodeWithTag("day-detail-modal-sheet").fetchSemanticsNode().boundsInWindow
            check(bounds.width > 0 && bounds.height > 0)
            val now = SystemClock.elapsedRealtime()
            if (previous != bounds) unchangedSince = now
            previous = bounds; finalBounds = bounds; samples += 1
            if (now - began >= contract.STABLE_MINIMUM_MS && now - unchangedSince >= 200L) break
            check(now < deadline) { "CALENDAR_DETAIL_STABILITY_DEADLINE" }
            SystemClock.sleep(50)
        }
        event.put("stable_elapsed_ms", SystemClock.elapsedRealtime() - began).put("stable_samples", samples)
            .put("stable_tail_ms", SystemClock.elapsedRealtime() - unchangedSince)
            .put("same_owner_started", true).put("single_detail", true).put("title_visible", true)
            .put("detail_bounds", boundsJson(checkNotNull(finalBounds)))
        observeNode(titleMatcher(date), dayTitle(date), "DAY_TITLE")
    }
    private fun observeCards() = step(Stage.CARDS) {
        val s = state()
        check(s.calendar.detailDate == contract.today && s.weather.surface == WeatherSurface.NONE)
        check(s.calendar.days.single { it.date == contract.today }.shifts.map { it.shift }.toSet() == setOf(target.shift, recipe.neighbourShift.shift))
        val cards = listOf(Triple(CardRole.TARGET, target.shift.id, "23/10/2026 08:15 – 23/10/2026 16:45"),
            Triple(CardRole.NEIGHBOR, recipe.neighbourShift.shift.id, "23/10/2026 05:00 – 23/10/2026 05:30"))
        for ((role, id, text) in cards) {
            val card = "shift-detail-card-$id"
            cardCheck(role, CardCheck.CARD_CARDINALITY) { check(count(card) == 1) }
            val m = hasText(text, substring = false) and hasAnyAncestor(hasTestTag(card)) and
                !hasAnyAncestor(hasTestTag("v2-actual-$id-card"))
            observeNode(m, text, "OWN_SHIFT_CARD", role)
            cardCheck(role, CardCheck.POST_SHEET_CARDINALITY) { check(count("day-detail-modal-sheet") == 1) }
            val date = cardCheck(role, CardCheck.POST_DATE_READ) { state().calendar.detailDate }
            cardCheck(role, CardCheck.POST_DATE) { check(date == contract.today) }
        }
    }
    private fun weatherRoundTrip(opening: Int): Int {
        val event = JSONObject().put("kind", "WEATHER_ROUND_TRIP").put("opening", opening)
            .put("date", contract.today).put("target_uuid", target.shift.id).put("transition_observed", false)
        val index = events.length(); events.put(event)
        val before = step(Stage.WEATHER_OPEN) {
            val s = state(); check(s.calendar.detailDate == contract.today && s.weather.surface == WeatherSurface.NONE && s.weather.preferences.enabled == weatherEnabled)
            check(count("weather-dialog-surface") == 0)
            val tag = "shift-weather-" + target.shift.id
            val node = compose.onNodeWithTag(tag)
            check(count(tag) == 1)
            node.assert(hasAnyAncestor(hasTestTag("shift-detail-card-" + target.shift.id)))
            touchNative(tag, event)
            s
        }
        step(Stage.WEATHER_STATE) {
            await { val s = state(); s.weather.surface == WeatherSurface.SHIFT && !s.weather.isLoading &&
                s.weather.selectedShift?.id == target.shift.id && visible("weather-dialog-surface") }
            val s = state()
            check(s.weather.selectedShift == target.shift && s.weather.selectedShift?.localStartDate == contract.today &&
                s.weather.selectedShift?.sourceObjectiveId == recipe.business.objectiveId && s.weather.preferences.enabled == weatherEnabled &&
                !s.weather.isRefreshing && s.weather.forecast == null && s.weather.shiftBriefs.isEmpty())
            check(s.calendar.detailDate == contract.today && s.callbacks.dismissCalls == before.callbacks.dismissCalls)
            check(s.callbacks.weatherIds == before.callbacks.weatherIds + target.shift.id &&
                s.callbacks.weatherOpenReturns == before.callbacks.weatherOpenReturns + 1)
            check(count("weather-dialog-surface") == 1)
            observeNode(hasText("Clima de la jornada", substring = false) and hasAnyAncestor(hasTestTag("weather-heading")),
                "Clima de la jornada", "WEATHER_HEADING")
            val text = "Hospital Temporal · 23/10/2026 · 08:15–16:45"
            observeNode(hasText(text, substring = false) and hasAnyAncestor(hasTestTag("weather-scroll-content")), text, "WEATHER_IDENTITY")
            event.put("weather_selected_exact", true).put("weather_open_callbacks", 1)
                .put("weather_disabled", !weatherEnabled).put("objective_location_absent", true)
                .put("external_effect_policy", "OWN_CONTEXT_DENY_ONLY")
        }
        if (activationCase) checkWeatherActivation(opening, event)
        step(Stage.WEATHER_CLOSE) {
            val s = state()
            val close = JSONObject().put("kind", "WEATHER_CLOSE").put("transition_observed", false)
            event.put("close", close)
            val n = compose.onNodeWithTag("weather-close")
            n.assert(hasAnyAncestor(hasTestTag("weather-dialog-surface"))).assertTextEquals("Cerrar")
            touchNative("weather-close", close)
            await { val after = state(); after.weather.surface == WeatherSurface.NONE &&
                after.calendar.detailDate == contract.today && count("weather-dialog-surface") == 0 &&
                count("day-detail-modal-sheet") == 1 && visible("day-detail-modal-sheet") }
            val after = state()
            check(after.callbacks.weatherCloseCalls == s.callbacks.weatherCloseCalls + 1 &&
                after.callbacks.weatherCloseReturns == s.callbacks.weatherCloseReturns + 1 &&
                after.callbacks.dismissCalls == s.callbacks.dismissCalls && after.callbacks.selectedDates == s.callbacks.selectedDates)
            close.put("transition_observed", true).put("close_callbacks", 1).put("callback_returned", true)
        }
        step(Stage.WEATHER_RETURN) {
            stableDetail(contract.today, event)
            observeCards()
            check(count("day-detail-modal-sheet") == 1 && count("weather-dialog-surface") == 0)
            event.put("both_cards_observed_in_same_sheet", true).put("cards_observed_sequentially_in_owned_scroll", true)
                .put("transition_observed", true)
        }
        return index
    }
    private fun closeDay(date: LocalDate) = step(Stage.BACK) {
        val before = state()
        check(before.calendar.detailDate == date && before.weather.surface == WeatherSurface.NONE && count("day-detail-modal-sheet") == 1)
        compose.onNodeWithTag("day-detail-modal-sheet").assertIsDisplayed()
        val original = activity
        val event = JSONObject().put("kind", "DETAIL_BACK").put("date", date).put("input_entered", false).put("transition_observed", false)
        events.put(event)
        ready(); event.put("input_entered", true).put("api_return", device.pressBack()).put("input_returned", true)
        await { val s = state(); s.calendar.detailDate == null && count("day-detail-modal-sheet") == 0 &&
            count("weather-dialog-surface") == 0 && visible("month-grid") }
        val after = state()
        check(activity === original && after.calendar.visibleMonth == YearMonth.from(date) &&
            after.callbacks.dismissCalls == before.callbacks.dismissCalls + 1 &&
            after.callbacks.dismissReturns == before.callbacks.dismissReturns + 1)
        event.put("transition_observed", true).put("dismiss_callbacks", 1).put("callback_returned", true).put("same_owner", true)
    }
    private fun checkWeatherActivation(opening: Int, event: JSONObject) = step(Stage.WEATHER_STATE) {
        val reason = "Este objetivo todavía no tiene ubicación para el clima. Agregá una dirección o usá tu ciudad actual desde Mi forma de trabajar > Opciones avanzadas."
        if (opening == 1) {
            check(!weatherEnabled && !state().weather.preferences.providerExplanationAccepted)
            val activation = JSONObject().put("kind", "ENABLE_AFTER_EXPLANATION")
                .put("transition_observed", false)
            event.put("activation", activation)
            compose.onNodeWithTag("weather-essential-privacy").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("weather-enable").performScrollTo().assertIsDisplayed()
            touchNative("weather-enable", activation)
            await {
                val s = state().weather
                s.preferences.enabled && s.preferences.providerExplanationAccepted && !s.isLoading && !s.isRefreshing
            }
            weatherEnabled = true
            val baseline = checkNotNull(activationBaseline)
            val after = Stage12FullStoreReadback.captureTemporalOwned(context, run, case.scope, case.binding)
            val path = "files/datastore/weather.preferences_pb"
            check(baseline.files[path] == Stage12FileSnapshot(0, hash("")))
            check(baseline.preferences.filterKeys { it.startsWith("$path#") } ==
                mapOf("$path#@file" to "BYTES:0:${hash("")}"))
            check(after.tables == baseline.tables)
            check(after.files.filterKeys { it != path } == baseline.files.filterKeys { it != path })
            check(after.preferences.filterKeys { !it.startsWith("$path#") } == baseline.preferences.filterKeys { !it.startsWith("$path#") })
            check(after.preferences.keys.filter { it.startsWith("$path#") }.toSet() == setOf(
                "$path#@file", "$path#${Stage12FullStoreReadback.text("enabled")}",
                "$path#${Stage12FullStoreReadback.text("provider_explanation_accepted")}",
            ))
            requirePersistedWeatherActivation(after)
            activationExpected = after
            activation.put("transition_observed", true).put("enabled", true).put("explanation_accepted", true)
                .put("only_owned_preference_file_changed", true).put("business_tables_equal", true)
                .put("persisted_boolean_keys", 2)
        } else {
            check(weatherEnabled && state().weather.preferences.providerExplanationAccepted && count("weather-enable") == 0)
            event.put("activation_reopened", true)
            exact("weather-activation-reopened", checkNotNull(activationExpected))
        }
        val s = state()
        check(s.weather.preferences.enabled && s.weather.preferences.providerExplanationAccepted &&
            s.weather.selectedShift == target.shift && s.weather.ineligibleReason == reason &&
            s.weather.errorMessage == null && s.weather.forecast == null && s.calendar.detailDate == contract.today)
        observeNode(hasText(reason, substring = false) and hasAnyAncestor(hasTestTag("weather-scroll-content")),
            reason, "WEATHER_ENABLED_WITHOUT_LOCATION")
        event.put("location_requirement_visible", true).put("real_network_or_location_tested", false)
            .put("user_failure_reproduced", false).put("tested_location_variant", "ABSENT")
    }

    /** Independent decode of the closed, owned protobuf file; no second live DataStore. */
    private fun requirePersistedWeatherActivation(expected: Stage12StoreSnapshot) {
        val path = "files/datastore/weather.preferences_pb"
        val file = File(case.binding.privateRoot, path)
        val bytes = Files.readAllBytes(file.toPath())
        val observed = checkNotNull(expected.files[path])
        check(observed.kind == Stage12PrivateNodeKind.FILE && observed.size == bytes.size.toLong())
        check(observed.sha256 == MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) })
        val type = Class.forName("androidx.datastore.preferences.PreferencesProto\$PreferenceMap", false,
            Stage12FullStoreReadback::class.java.classLoader)
        val message = type.getMethod("parseFrom", ByteArray::class.java).invoke(null, bytes)
        val values = type.getMethod("getPreferencesMap").invoke(message) as Map<*, *>
        check(values.keys == setOf("enabled", "provider_explanation_accepted"))
        values.values.forEach { nullable ->
            val value = checkNotNull(nullable)
            val kind = value.javaClass.getMethod("getValueCase").invoke(value) as Enum<*>
            check(kind.name == "BOOLEAN" && value.javaClass.getMethod("getBoolean").invoke(value) == true)
        }
    }
    private fun touchNative(tag: String, event: JSONObject) {
        ready(); await { count(tag) == 1 }
        val node = compose.onNodeWithTag(tag)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().assertHasClickAction()
        val semantic = node.fetchSemanticsNode()
        var screen = Offset.Unspecified
        instrumentation.runOnMainSync {
            val coordinates = semantic.layoutInfo.coordinates
            check(coordinates.isAttached && coordinates.size.width > 0 && coordinates.size.height > 0)
            val center = Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
            val windowCenter = coordinates.localToWindow(center)
            check(windowCenter.x.isFinite() && windowCenter.y.isFinite() && semantic.boundsInWindow.contains(windowCenter)) {
                "CALENDAR_DETAIL_TOUCH_CENTER_OUTSIDE_VISIBLE_NODE"
            }
            screen = coordinates.localToScreen(center)
        }
        check(screen.x.isFinite() && screen.y.isFinite() && screen.x >= 0 && screen.y >= 0)
        failure.inputAfterReadiness({ ready(); screen }) { point ->
            event.put("tag", tag).put("node_id", semantic.id).put("input_entered", true)
                .put("visible_bounds", boundsJson(semantic.boundsInWindow)).put("center_inside_visible_node", true)
                .put("semantic_role", semantic.config.getOrNull(SemanticsProperties.Role)?.toString() ?: JSONObject.NULL)
                .put("enabled", true).put("click_action", true)
            event.put("api_return", device.click(point.x.toInt(), point.y.toInt())).put("input_returned", true)
        }
    }
    private fun observeNode(matcher: SemanticsMatcher, text: String, role: String, card: CardRole? = null) {
        cardCheck(card, CardCheck.RANGE_READINESS) { ready() }
        cardCheck(card, CardCheck.RANGE_CARDINALITY) {
            check(compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size == 1)
        }
        val n = cardCheck(card, CardCheck.RANGE_RESOLVE) { compose.onNode(matcher, useUnmergedTree = true) }
        if (!cardCheck(card, CardCheck.RANGE_VISIBILITY_QUERY) { n.isDisplayed() })
            cardCheck(card, CardCheck.RANGE_SCROLL) { n.performScrollTo() }
        cardCheck(card, CardCheck.RANGE_VISIBLE) { n.assertIsDisplayed() }
        cardCheck(card, CardCheck.RANGE_EXACT_TEXT) { n.assertTextEquals(text) }
        val measured = cardCheck(card, CardCheck.RANGE_NODE_READ) { n.fetchSemanticsNode() }
        val bounds = cardCheck(card, CardCheck.RANGE_BOUNDS_READ) { measured.boundsInWindow }
        cardCheck(card, CardCheck.RANGE_WIDTH) { check(bounds.width > 0) }
        cardCheck(card, CardCheck.RANGE_HEIGHT) { check(bounds.height > 0) }
        cardCheck(card, CardCheck.RANGE_RECORD) {
            semantics.put(JSONObject().put("role", role).put("text", text).put("node_id", measured.id)
                .put("visible", true).put("bounds", boundsJson(bounds))
                .put("semantic_role", measured.config.getOrNull(SemanticsProperties.Role)?.toString() ?: JSONObject.NULL)
                .put("disabled", measured.config.contains(SemanticsProperties.Disabled))
                .put("click_action", measured.config.contains(SemanticsActions.OnClick)))
        }
    }
    private fun count(tag: String): Int = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size.also { check(it in 0..1) { "CALENDAR_DETAIL_NODE_AMBIGUOUS" } }
    private fun visible(tag: String): Boolean = count(tag) == 1 && compose.onNodeWithTag(tag).isDisplayed()
    private fun ready() {
        active(); check(context.packageName == QA && device.isScreenOn && device.currentPackageName == QA)
        check(!(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked)
        val a = activity; check(!a.isDestroyed && !a.isFinishing && a.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + contract.DEADLINE_MS
        while (true) {
            ready(); if (predicate()) return
            check(SystemClock.elapsedRealtime() < deadline) { "CALENDAR_DETAIL_WAIT_DEADLINE" }
            SystemClock.sleep(50)
        }
    }
    private fun exact(label: String, expected: Stage12StoreSnapshot) {
        active()
        val read = Stage12FullStoreReadback.captureTemporalOwned(context, run, case.scope, case.binding)
        Stage12RecurringReadbackOracle.requireExact(read, expected)
        readbacks.put(JSONObject().put("stage", label).put("scope", case.scope.wire).put("all_tables", read.tables.size)
            .put("file_entries", read.files.size).put("tables_sha256", hash(read.tables.toString()))
            .put("preferences_sha256", hash(read.preferences.toString())).put("files_sha256", hash(read.files.toString())))
    }
    private fun addFacts(prefix: String, indices: List<Int>) {
        check(indices.size == 2)
        for (level in listOf("ui_compose", "activity_native")) {
            val key = "$prefix.$level"; check(key in keys)
            facts.put(JSONObject().put("assertion_key", key).put("level", level.uppercase()).put("check", "PASS")
                .put("event_indices", JSONArray(indices)).put("fixture_role", contract.FIXTURE_ROLE)
                .put("received_conservation_complete", false))
        }
    }
    private fun closeOwned() {
        check(!closeAttempted); closeAttempted = true
        check(case.preparation.projection()["status"] == "COMPLETE") { "CALENDAR_DETAIL_SETUP_CLOSURE_UNPROVEN" }
        stage = Stage.CLOSE_OWNER
        owner?.let { try { db { it.closeOriginal() } } catch (e: Throwable) { retain(e) }; check(it.closureProven); ownerClosed = true }
        stage = Stage.CLOSE_AUXILIARY
        resources?.let { try { db { it.closeAfterOwner(ownerClosed) } } catch (e: Throwable) { retain(e) }; check(it.closureProven); auxiliaryClosed = true }
        stage = Stage.CLOSE_STORE
        case.opened.close(); check(case.opened.isClosed); originalStoreClosed = true
        active()
    }
    private fun cleanupClosed() {
        stage = Stage.CLEANUP
        check(ownerClosed && auxiliaryClosed && originalStoreClosed && reopenedStoreClosed && case.opened.isClosed)
        val binding = case.binding
        binding.requireStored(stage12RecurringPathAccess(context))
        check(context.deleteDatabase(binding.name))
        check(listOf("", "-wal", "-shm", "-journal").none { Files.exists(File(binding.database.path + it).toPath(), NOFOLLOW_LINKS) })
        binding.revalidate(stage12RecurringPathAccess(context))
        val root = binding.privateRoot
        check(root == File(context.filesDir.canonicalFile, case.scope.privateRootName(run)) && !Files.isSymbolicLink(root.toPath()))
        val paths = Files.walk(root.toPath()).use { it.iterator().asSequence().toList() }
        check(paths.none(Files::isSymbolicLink) && paths.all { it.startsWith(root.toPath()) })
        paths.sortedByDescending { it.nameCount }.forEach { check(Files.deleteIfExists(it)) }
        stage = Stage.COMPLETE
    }
    private fun <T> db(body: suspend () -> T): T = runBlocking { withTimeout(30_000) { body() } }
    private fun boundsJson(bounds: Rect) = JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom))
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    companion object { private const val QA = "com.blackatsystems.miguardia.qa" }
}
