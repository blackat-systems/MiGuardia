package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.ui.calendar.*
import com.blackatsystems.miguardia.ui.management.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Received QA stays in place. Only UI-created rows bearing this run's marker are owned. */
class Stage12WorkCalendarNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as MiGuardiaApplication
    private val store get() = app.localDataStore
    private val device get() = UiDevice.getInstance(instrumentation)
    private var scenario: ActivityScenario<MainActivity>? = null
    private val directSaveObservations = JSONArray()

    @Test fun ownDayAndOvernightLoadRecreateEditCancelAndReopenPreserveReceivedRows() {
        check(instrumentation is Stage12NormalQaRunner && context.packageName == QA)
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("stage12CalendarClock") == "REAL")
        val run = requireNotNull(args.getString("stage12RunId")); UUID.fromString(run)
        val marker = "STAGE12-B1-$run"
        val identity = JSONObject().put("run_uuid", run).put("qa_sha256", args.getString("stage12QaSha256"))
            .put("runner_sha256", args.getString("stage12RunnerSha256"))
        val output = Stage12NativeReceiptFiles.prepareOutput(context.filesDir, run, identity.toString(), "b1-owned-load-edit")
        val facts = JSONArray()
        val receipt = JSONObject(identity.toString()).put("schema", "stage12-b1-owned-load-edit-v1")
            .put("case", "b1-owned-load-edit").put("status", "INCOMPLETE").put("facts", facts)
            .put("direct_save_observations", directSaveObservations)
            .put("calendar_clock_mode", "REAL").put("calendar_zone", AppDefaults.zoneId().id)
            .put("fixture_binding", "OWN_UI_ROWS_EMPTY_FUTURE_DATE_RECEIVED_ACTIVE_DAY_AND_OVERNIGHT_TEMPLATES")
            .put("literal_C026_fixed_fixture_claim", false).put("round_credit", 0)
        var admission = Stage12ActivityAdmission(false)
        (instrumentation as Stage12NormalQaRunner).beginReadiness(admission)
        var primary: Throwable? = null
        var step = "READINESS"
        var before: Stage12StoreSnapshot? = null
        var date: LocalDate? = null
        var fixtureWritesStarted = false
        val owned = linkedSetOf<UUID>()
        fun fact(key: String, level: String, observed: JSONObject = JSONObject()) {
            facts.put(JSONObject().put("assertion_key", key).put("level", level).put("check", "PASS").put("observed", observed))
        }
        fun trackOwned(): List<V2ShiftWrite> {
            val day = date?.takeIf { fixtureWritesStarted } ?: return emptyList()
            val rows = runBlocking { store.shifts.observeStartingBetween(day, day).first() }
            val writes = rows.filter { it.position == marker || it.position == "$marker-edited" }.map {
                val value = runBlocking { store.v2Shifts.getShift(it.id) }
                check(value is V2ShiftLookup.V2) { "B1_UI_CREATED_ROW_NOT_V2" }
                owned += it.id
                value.write
            }
            return writes.sortedBy { it.shift.startAt }
        }
        try {
            admission.phase(Stage12ActivityAdmission.Phase.LAUNCH)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            Stage12OwnActivityAdmission.awaitReady(requireNotNull(scenario), context, device, admission)
            await { app.startupRecoveryGate.isReady && calendar().loadState == CalendarLoadState.CONTENT }
            before = Stage12FullStoreReadback.capture(context)
            receipt.put("received_tables_sha256", digest(before.tables.toString()))
            val history = runBlocking { checkNotNull(store.workConfiguration.get()) }
            step = "FIXTURE_PRECONDITIONS"
            val today = calendar().referenceInstant.atZone(AppDefaults.zoneId()).toLocalDate()
            receipt.put("observed_today", today.toString()).put("calendar_reference_instant", calendar().referenceInstant.toString())
            goToMonth(YearMonth.from(today.plusDays(2)))
            date = calendar().days.firstOrNull { it.date >= today.plusDays(2) && it.shifts.isEmpty() &&
                it.explicitStatus == null && it.medicalLeaves.isEmpty() && it.vacation == null }?.date
            val day = checkNotNull(date) { "B1_NO_EMPTY_FUTURE_DATE_IN_REVIEWED_MONTH" }
            check(runBlocking { store.shifts.observeStartingBetween(day, day).first() }.isEmpty()) { "B1_SOURCE_DATE_NO_LONGER_EMPTY" }
            receipt.put("fixture_date_empty_before_UI_writes", true)
            val revision = ResolvedWorkConfigurationRevision.resolve(history, day)
            val catalog = runBlocking { store.workCatalog.observeCatalog(history.timeline.id, revision.revision.value.sector).first() }
            val templates = catalog.workTemplates.filter { it.isActive && catalog.workPlaces.any { p -> p.id == it.workPlaceId && p.isActive } &&
                catalog.workTypes.any { t -> t.id == it.workTypeId && t.isActive } }
            val dayTemplate = checkNotNull(templates.firstOrNull { it.endTime > it.startTime }) { "B1_NO_ACTIVE_DAY_TEMPLATE" }
            val nightTemplate = checkNotNull(templates.firstOrNull { it.endTime < it.startTime && it.workPlaceId == dayTemplate.workPlaceId }) {
                "B1_NO_ACTIVE_OVERNIGHT_TEMPLATE_IN_SAME_PLACE"
            }
            check(dayTemplate.endTime <= nightTemplate.startTime) { "B1_RECEIVED_TEMPLATES_OVERLAP" }
            receipt.put("date", day.toString()).put("day_template_id", dayTemplate.id.toString())
                .put("night_template_id", nightTemplate.id.toString()).put("received_catalog_read_only", true)
            admission.phase(Stage12ActivityAdmission.Phase.BODY)

            step = "SINGLE_DATE_ENTRY"
            openDate(day)
            touch("day-edit-menu")
            await { manual().canCreate }
            check(manual().selectedDates == setOf(day)) { "B1_SINGLE_ENTRY_CHANGED_DATE" }
            compose.onNodeWithText("Cargando jornada para los días:").assertIsDisplayed()
            compose.onNodeWithTag("v2-manual-exact-dates").assertTextContains(day.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")))
            check(!exists("v2-edit-day-action")) { "B1_DIRECT_LOAD_OPENED_OTHER_OPTIONS" }
            fact("C024-contrato-completo.activity_native", "ACTIVITY_NATIVE", JSONObject().put("date", day.toString()))
            fact("C048-contrato-completo.activity_native", "ACTIVITY_NATIVE", JSONObject().put("date", day.toString()))
            choose(dayTemplate)
            step = "LOAD_DRAFT_RECREATE"
            position(marker)
            touch("manual-add-note")
            fill("manual-note", marker)
            val draft = manual()
            val beforeDraft = Stage12FullStoreReadback.capture(context)
            check(before.tables == beforeDraft.tables) { "B1_DRAFT_CHANGED_DATABASE" }
            requireNotNull(scenario).recreate()
            waitTag("v2-manual-save-direct")
            val restored = manual()
            check(restored.operationId == draft.operationId && restored.operationTimestamp == draft.operationTimestamp &&
                restored.selectedDates == setOf(day) && restored.selectedTemplateId == dayTemplate.id &&
                restored.position == marker && restored.noteBody == marker) { "B1_RECREATED_LOAD_DRAFT_DIFFERS" }
            check(before.tables == Stage12FullStoreReadback.capture(context).tables) { "B1_RECREATION_WROTE_DATABASE" }
            fact("B1-load-draft-recreate.partial", "ACTIVITY_NATIVE", JSONObject().put("operation_id", draft.operationId.toString()))
            touch("manual-cancel-load"); touchText("Seguir cargando")
            check(manual().operationId == draft.operationId && manual().noteBody == marker && manual().position == marker) {
                "B1_CANCEL_DISMISS_CHANGED_DRAFT"
            }
            fact("B1-load-cancel-keep.partial", "ACTIVITY_NATIVE")
            step = "SAVE_FIRST_UI_ROW"
            fixtureWritesStarted = true
            saveLoad(day, 1)
            val first = trackOwned().single()
            check(first.snapshot.templateId == dayTemplate.id && first.shift.localStartDate == day && first.shift.position == marker)
            check(runBlocking { store.shiftNotes.observeForShift(first.shift.id).first().single().body } == marker) {
                "B1_UI_NOTE_NOT_PERSISTED"
            }
            fact("C059-contrato-completo.activity_native", "ACTIVITY_NATIVE", JSONObject().put("direct_save_without_review", true))

            step = "SAVE_SECOND_OVERNIGHT_UI_ROW"
            openDate(day); touch("day-edit-menu"); await { manual().canCreate }
            check(manual().selectedDates == setOf(day))
            choose(nightTemplate); position(marker)
            saveLoad(day, 2)
            val pair = trackOwned()
            check(pair.size == 2 && pair.map { it.shift.id }.distinct().size == 2) { "B1_UI_PAIR_NOT_UNIQUE" }
            val night = pair.single { it.snapshot.templateId == nightTemplate.id }
            val expectedStart = day.atTime(nightTemplate.startTime).atZone(night.shift.zoneId).toInstant()
            val expectedEnd = day.plusDays(1).atTime(nightTemplate.endTime).atZone(night.shift.zoneId).toInstant()
            check(night.shift.startAt == expectedStart && night.shift.endAt == expectedEnd && expectedEnd > expectedStart) {
                "B1_OVERNIGHT_INTERVAL_DIFFERS"
            }
            receipt.put("owned_shift_ids", JSONArray(pair.map { it.shift.id.toString() }))
                .put("night_minutes", Duration.between(expectedStart, expectedEnd).toMinutes())
            fact("B1-day-and-overnight.exact_rows", "ROOM_ISOLATED", JSONObject().put("count", 2)
                .put("night_start", expectedStart.toString()).put("night_end", expectedEnd.toString()))
            step = "POPULATED_DETAIL"
            openDate(day)
            pair.forEach { compose.onNodeWithTag("shift-detail-card-${it.shift.id}").assertExists() }
            check(calendar().days.single { it.date == day }.shifts.map { it.shift.id }.toSet() == owned)
            fact("B1-populated-detail.partial_C025_C026", "ACTIVITY_NATIVE", JSONObject().put("distinct_cards", 2))
            touch("day-other-options"); touch("v2-edit-day-action"); waitTag("v2-shift-edit-surface")
            check(edit().dayRows.map { it.shift.id } == pair.map { it.shift.id }) { "B1_EDIT_ROWS_ORDER_DIFFERS" }
            fact("C033-contrato-completo.activity_native", "ACTIVITY_NATIVE")
            val target = pair.first()
            val neighbour = pair.last()
            step = "EDIT_RECREATE_BACK_AND_DISCARD"
            touch("v2-edit-shift-${target.shift.id}"); positionEdit("$marker-edited")
            val beforeEdit = Stage12FullStoreReadback.capture(context)
            requireNotNull(scenario).recreate(); waitTag("v2-shift-edit-position")
            check(edit().targetShiftId == target.shift.id && edit().date == day && edit().position == "$marker-edited") {
                "B1_RECREATED_EDIT_TARGET_DIFFERS"
            }
            check(beforeEdit.tables == Stage12FullStoreReadback.capture(context).tables) { "B1_EDIT_RECREATION_WROTE_DATABASE" }
            device.pressBack(); waitTag("v2-shift-discard-dialog"); touchText("Seguir editando")
            check(edit().position == "$marker-edited" && edit().targetShiftId == target.shift.id)
            device.pressBack(); waitTag("v2-shift-discard-dialog"); touch("v2-shift-confirm-discard")
            waitTag("v2-edit-shift-${target.shift.id}")
            check(beforeEdit.tables == Stage12FullStoreReadback.capture(context).tables) { "B1_DISCARD_CHANGED_DATABASE" }
            step = "EDIT_POSITION_REVIEW_COMMIT"
            touch("v2-edit-shift-${target.shift.id}"); positionEdit("$marker-edited")
            compose.onNodeWithTag("v2-shift-fixed-date").assertExists()
            check(edit().date == day && edit().targetShiftId == target.shift.id && edit().usesHistoricalTemplate)
            touch("v2-shift-request-review")
            await { exists("v2-shift-save") || exists("v2-shift-warning-dialog") }
            if (exists("v2-shift-warning-dialog")) touch("v2-shift-confirm-warnings")
            touch("v2-shift-save")
            await { runBlocking { store.shifts.getById(target.shift.id)?.position } == "$marker-edited" }
            val edited = runBlocking { store.v2Shifts.getShift(target.shift.id) as V2ShiftLookup.V2 }.write
            check(edited.snapshot == target.snapshot &&
                edited.shift.copy(position = target.shift.position, updatedAt = target.shift.updatedAt) == target.shift) {
                "B1_POSITION_EDIT_CHANGED_OTHER_FIELDS"
            }
            check(runBlocking { store.v2Shifts.getShift(neighbour.shift.id) as V2ShiftLookup.V2 }.write == neighbour) {
                "B1_POSITION_EDIT_CHANGED_NEIGHBOUR"
            }
            fact("C089-contrato-completo.activity_native", "ACTIVITY_NATIVE", JSONObject().put("target_uuid", target.shift.id.toString()))
            fact("C089-contrato-completo.room_isolated", "ROOM_ISOLATED", JSONObject().put("neighbour_unchanged", true))
            step = "CLOSE_AND_REOPEN"
            admission.phase(Stage12ActivityAdmission.Phase.CLOSE)
            requireNotNull(scenario).close(); scenario = null
            receipt.put("initial_readiness", JSONObject(admission.wire())).put("first_activity_closed", true)
            admission = Stage12ActivityAdmission(false)
            (instrumentation as Stage12NormalQaRunner).beginReadiness(admission)
            admission.phase(Stage12ActivityAdmission.Phase.LAUNCH)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            Stage12OwnActivityAdmission.awaitReady(requireNotNull(scenario), context, device, admission)
            admission.phase(Stage12ActivityAdmission.Phase.BODY)
            goToMonth(YearMonth.from(day)); openDate(day)
            check(trackOwned().size == 2 && runBlocking { store.shifts.getById(target.shift.id)?.position } == "$marker-edited")
            pair.forEach { compose.onNodeWithTag("shift-detail-card-${it.shift.id}").assertExists() }
            fact("B1-close-reopen-real-activity", "ACTIVITY_NATIVE")
        } catch (failure: Throwable) {
            primary = failure; admission.failed(failure); receipt.put("failure_class", failure.javaClass.simpleName)
                .put("failure_step", step).put("failure_code", failure.message?.takeIf { it.matches(Regex("B1_[A-Z0-9_]+")) } ?: "B1_UNCLASSIFIED")
        } finally {
            try {
                admission.phase(Stage12ActivityAdmission.Phase.CLOSE)
                scenario?.close(); scenario = null; receipt.put("activity_closed", true)
            } catch (failure: Throwable) {
                admission.failed(failure); primary = Stage12C090Oracle.retain(primary, failure); receipt.put("activity_closed", false)
            }
            try {
                admission.phase(Stage12ActivityAdmission.Phase.POSTCONDITION)
                trackOwned()
                receipt.put("owned_shift_ids_observed_for_cleanup", JSONArray(owned.map(UUID::toString)))
                owned.forEach { id -> runBlocking {
                    val current = store.v2Shifts.getShift(id)
                    if (current is V2ShiftLookup.V2) {
                        check(current.write.shift.position in setOf(marker, "$marker-edited")) { "B1_CLEANUP_NOT_OWNED" }
                        store.v2Shifts.deleteShift(current.write, store.shiftActuals.getExpectation(id))
                    }
                } }
                before?.let {
                    val after = Stage12FullStoreReadback.capture(context)
                    val tablesEqual = it.tables == after.tables
                    val preferencesEqual = it.preferences == after.preferences
                    val delta = Stage12B1FileDelta.project(it.files, after.files, run)
                    receipt.put("received_tables_restored", tablesEqual).put("received_preferences_unchanged", preferencesEqual)
                        .put("after_tables_sha256", digest(after.tables.toString()))
                    receipt.put("file_inventory_equal_before_publish", it.files == after.files)
                    receipt.put("file_deltas", fileDeltaWire(delta)).put("changed_received_file_entries", delta.receivedChanged)
                    check(tablesEqual) { "B1_RECEIVED_TABLES_CHANGED" }
                    check(preferencesEqual) { "B1_RECEIVED_PREFERENCES_CHANGED" }
                    check(!delta.truncated) { "B1_FILE_DELTA_TRUNCATED" }
                    check(delta.unknown == 0) { "B1_FILE_DELTA_UNKNOWN_ROLE" }
                    check(delta.receivedChanged == 0) { "B1_RECEIVED_FILES_CHANGED" }
                }
                receipt.put("cleanup_complete", true)
            } catch (failure: Throwable) {
                admission.failed(failure); primary = Stage12C090Oracle.retain(primary, failure); receipt.put("cleanup_complete", false)
                    .put("cleanup_failure_class", failure.javaClass.simpleName)
                    .put("cleanup_failure_code", failure.message?.takeIf { it.matches(Regex("B1_[A-Z0-9_]+")) } ?: "B1_UNCLASSIFIED")
            }
            admission.phase(Stage12ActivityAdmission.Phase.PUBLISH)
            receipt.put("status", if (primary == null) "COMPLETE" else "FAILED").put("readiness", JSONObject(admission.wire()))
            try { Stage12NativeReceiptFiles.publish(output, receipt.toString(2), primary) }
            catch (failure: Throwable) { admission.failed(failure); primary = Stage12C090Oracle.retain(primary, failure) }
            finally { admission.finish(primary == null) }
        }
        primary?.let { throw IllegalStateException("B1_OWNED_LOAD_EDIT_FAILED_${it.javaClass.simpleName}") }
    }

    private inline fun <reified VM : ViewModel, T> state(crossinline read: (VM) -> T): T {
        var value: T? = null
        requireNotNull(scenario).onActivity { value = read(ViewModelProvider(it)[VM::class.java]) }
        return requireNotNull(value)
    }
    private fun calendar() = state<CalendarViewModel, CalendarUiState> { it.uiState.value }
    private fun manual() = state<V2ManualShiftLoadViewModel, V2ManualShiftLoadUiState> { it.uiState.value }
    private fun edit() = state<V2ShiftEditViewModel, V2ShiftEditUiState> { it.uiState.value }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun await(condition: () -> Boolean) = compose.waitUntil(30_000) { check(device.currentPackageName == QA) { "B1_QA_FOCUS_LOST" }; condition() }
    private fun waitTag(tag: String) = await { exists(tag) }
    private fun touch(tag: String, scroll: Boolean = true) {
        waitTag(tag)
        val node = compose.onNodeWithTag(tag)
        if (scroll && !node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
    }
    private fun touchText(text: String) {
        await { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed().performTouchInput { click() }
    }
    private fun fill(tag: String, text: String) {
        waitTag(tag); compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
        requireNotNull(scenario).onActivity { activity ->
            activity.currentFocus?.clearFocus()
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
    }
    private fun position(value: String) { if (!exists("v2-manual-position")) touch("manual-toggle-position"); fill("v2-manual-position", value) }
    private fun positionEdit(value: String) = fill("v2-shift-edit-position", value)
    private fun choose(template: WorkTemplate) {
        touch("manual-institutions"); touch("manual-place-${template.workPlaceId}"); touch("v2-template-${template.id}")
        check(manual().selectedTemplateId == template.id) { "B1_TEMPLATE_SELECTION_DIFFERS" }
    }
    private fun saveLoad(date: LocalDate, count: Int) {
        check(manual().stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE && manual().selectedDates == setOf(date))
        val requested = manual()
        val events = JSONArray()
        val observation = JSONObject().put("expected_rows", count).put("operation_id", requested.operationId.toString())
            .put("success_sequence_before", requested.successSequence).put("direct_touch_actions", 1)
            .put("optional_review_touch_actions", 0).put("events", events)
        directSaveObservations.put(observation)
        var previous: String? = null
        fun observe(): V2ManualShiftLoadUiState {
            val current = manual()
            if (current.isActive) check(current.operationId == requested.operationId) { "B1_SAVE_OPERATION_CHANGED" }
            val state = "${current.stage}|${current.isLoading}|${current.isSaving}|${current.errorMessage != null}"
            if (state != previous) {
                check(events.length() < 32) { "B1_SAVE_OBSERVATION_TRUNCATED" }
                events.put(JSONObject().put("stage", current.stage.name).put("is_loading", current.isLoading)
                    .put("is_saving", current.isSaving).put("has_error", current.errorMessage != null))
                previous = state
            }
            return current
        }
        fun decisionReady(): Boolean {
            val current = observe()
            return current.errorMessage != null || current.stage in setOf(V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY,
                V2ManualShiftLoadStage.CONFIRM_WARNINGS, V2ManualShiftLoadStage.IDLE)
        }
        touch("v2-manual-save-direct")
        // REVIEW is an internal transient of requestSave -> automatic save. Never touch its controls.
        // If automatic progress stalls there, this same bounded wait fails rather than confirming it.
        await { decisionReady() }
        check(manual().errorMessage == null) { "B1_DIRECT_SAVE_ERROR" }
        if (manual().stage == V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY) {
            check(count == 2) { "B1_UNEXPECTED_RECEIVED_OCCUPANCY" }; touchText("Superponer")
        }
        await {
            val current = observe()
            current.errorMessage != null || current.stage in setOf(V2ManualShiftLoadStage.CONFIRM_WARNINGS, V2ManualShiftLoadStage.IDLE)
        }
        check(manual().errorMessage == null) { "B1_SAVE_ERROR" }
        if (manual().stage == V2ManualShiftLoadStage.CONFIRM_WARNINGS) touchText("Continuar igualmente")
        await { val current = observe(); current.errorMessage != null || current.stage == V2ManualShiftLoadStage.IDLE && !current.isSaving }
        check(manual().errorMessage == null && !manual().isSaving && manual().stage == V2ManualShiftLoadStage.IDLE) { "B1_DIRECT_SAVE_NOT_COMPLETE" }
        check(manual().successSequence == requested.successSequence + 1) { "B1_DIRECT_SAVE_SUCCESS_SEQUENCE_DIFFERS" }
        observation.put("success_sequence_after", manual().successSequence)
        check(runBlocking { store.shifts.observeStartingBetween(date, date).first().size } == count) { "B1_SAVED_COUNT_DIFFERS" }
    }
    private fun fileDeltaWire(delta: Stage12B1FileDelta.Result): JSONObject {
        fun node(value: Stage12FileSnapshot?): Any = value?.let {
            JSONObject().put("kind", it.kind.name).put("bytes", it.size).put("sha256", it.sha256)
        } ?: JSONObject.NULL
        return JSONObject().put("total", delta.total).put("added", delta.added).put("removed", delta.removed)
            .put("modified", delta.modified).put("received_changed", delta.receivedChanged)
            .put("unknown_roles", delta.unknown).put("truncated", delta.truncated).put("entries", JSONArray(delta.entries.map {
                JSONObject().put("path_token", it.pathToken).put("role", it.role.name).put("change", it.change.name)
                    .put("before", node(it.before)).put("after", node(it.after))
            }))
    }
    private fun goToMonth(month: YearMonth) {
        var steps = 0
        await { calendar().loadState == CalendarLoadState.CONTENT }
        while (calendar().visibleMonth != month) {
            check(steps++ < 3) { "B1_MONTH_BOUND" }
            val previous = calendar().visibleMonth
            compose.onNodeWithContentDescription(if (previous < month) "Mes siguiente" else "Mes anterior")
                .assertIsDisplayed().performTouchInput { click() }
            await { calendar().visibleMonth != previous && calendar().loadState == CalendarLoadState.CONTENT }
        }
    }
    private fun openDate(date: LocalDate) {
        await { calendar().loadState == CalendarLoadState.CONTENT }
        touch("day-$date", scroll = false)
        await { calendar().detailDate == date && exists("day-edit-menu") }
    }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    companion object { private const val QA = "com.blackatsystems.miguardia.qa" }
}
