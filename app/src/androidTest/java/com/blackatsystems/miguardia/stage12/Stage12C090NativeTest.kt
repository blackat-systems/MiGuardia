package com.blackatsystems.miguardia.stage12

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftEditUiState
import com.blackatsystems.miguardia.ui.management.V2ShiftEditViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftDayInspectionState
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

/** Real MainActivity actions; only this UUID namespace is ever written/deleted. */
class Stage12C090NativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as MiGuardiaApplication
    private val store get() = app.localDataStore
    private var scenario: ActivityScenario<MainActivity>? = null

    @Test fun identicalJourneysCancelThenRejectStaleCasAndDeleteOnlyTheSecond() {
        check(instrumentation is Stage12NormalQaRunner && context.packageName == "com.blackatsystems.miguardia.qa")
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("stage12CalendarClock") == "REAL")
        val uuid = requireNotNull(args.getString("stage12RunId")); val namespace = UUID.fromString(uuid)
        fun own(label: String) = UUID.nameUUIDFromBytes("$namespace/c090/$label".toByteArray(Charsets.UTF_8))
        // Stable textual ordering yields ordinal one/two without changing either payload.
        val ids = listOf(own("journey-a"), own("journey-b")).sorted()
        val identity = JSONObject().put("run_uuid", uuid).put("qa_sha256", args.getString("stage12QaSha256"))
            .put("runner_sha256", args.getString("stage12RunnerSha256"))
        val output = Stage12NativeReceiptFiles.prepareOutput(context.filesDir, uuid, identity.toString(), "c090-identical-shifts")
        val facts = JSONArray(); val readbacks = JSONArray(); val readbackAttempts = JSONArray()
        val receipt = JSONObject(identity.toString()).put("schema", "stage12-native-completion-v1").put("case", "C090")
            .put("status", "INCOMPLETE").put("fixture_owned", true).put("facts", facts).put("readbacks", readbacks)
            .put("readback_attempts", readbackAttempts).put("baseline_captured", false)
            .put("received_sqlite_restored_verified", false)
        val admission = Stage12ActivityAdmission(technicalOnly = false)
        (instrumentation as Stage12NormalQaRunner).beginReadiness(admission)
        var primary: Throwable? = null
        var initial: Stage12StoreSnapshot? = null
        val inserted = mutableListOf<UUID>()
        val ownFiles = linkedMapOf<File, String>()
        fun fact(suffix: String, level: String) { facts.put(JSONObject().put("assertion_key", "C090-contrato-completo.$suffix")
            .put("check", "PASS").put("level", level)) }
        fun snapshot(stage: String): Stage12StoreSnapshot {
            check(readbackAttempts.length() < 16) { "C090_READBACK_ATTEMPT_BOUND" }
            val trace = Stage12NativeDiagnostics.ReadbackAttempt()
            val observed = JSONObject().put("stage", stage)
            readbackAttempts.put(observed) // Retain the attempt even if capture never returns.
            try {
                val value = Stage12FullStoreReadback.capture(context, trace::reached) { first, second ->
                    // Project only the two inventories already read by this capture. No new I/O.
                    try {
                        val delta = Stage12B1FileDelta.project(first, second, uuid)
                        observed.put("inventory_delta", JSONObject().put("total", delta.total)
                            .put("received_changed", delta.receivedChanged).put("unknown_roles", delta.unknown)
                            .put("truncated", delta.truncated).put("entries", JSONArray(delta.entries.map {
                                JSONObject().put("path_token", it.pathToken).put("role", it.role.name)
                                    .put("change", it.change.name)
                            })))
                    } catch (_: Exception) {
                        observed.put("inventory_delta_projection_failed", true)
                    }
                }
                trace.returned(); trace.projecting()
                val tableHashes = JSONObject()
                value.tables.forEach { (name, table) -> tableHashes.put(name, digest(table.toString().toByteArray(Charsets.UTF_8))) }
                readbacks.put(JSONObject().put("stage", stage).put("table_hashes", tableHashes)
                    .put("table_count", value.tables.size).put("preferences_entries", value.preferences.size)
                    .put("files", value.files.values.count { it.kind == Stage12PrivateNodeKind.FILE })
                    .put("inventory_entries", value.files.size))
                trace.recorded()
                return value
            } catch (error: Throwable) {
                trace.failed(error)
                throw error
            } finally {
                observed.put("last_phase", trace.phase.name).put("capture_returned", trace.captureReturned)
                    .put("projection_appended", trace.projectionRecorded)
                    .put("failure_phase", trace.failurePhase?.name ?: "NONE")
                    .put("failure_code", trace.failureCode ?: "NONE")
            }
        }
        try {
            admission.phase(Stage12ActivityAdmission.Phase.LAUNCH)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            Stage12OwnActivityAdmission.awaitReady(requireNotNull(scenario), context, UiDevice.getInstance(instrumentation), admission)
            compose.waitUntil(30_000) { app.startupRecoveryGate.isReady && calendar().loadState == CalendarLoadState.CONTENT }
            admission.phase(Stage12ActivityAdmission.Phase.BODY)
            initial = snapshot("received_before_fixture")
            receipt.put("baseline_captured", true)
            val history = runBlocking { requireNotNull(store.workConfiguration.get()) }
            val today = LocalDate.now(com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId())
            val sectors = history.timeline.revisions.map { it.value.sector }.distinct()
            val received = runBlocking { sectors.flatMap { store.v2Shifts.observeAll(history.timeline.id, it).first() } }
            var chosenDate: LocalDate? = null; var source: V2ShiftWrite? = null
            for (offset in 2L..31L) {
                val date = today.minusDays(offset)
                val revision = runCatching { ResolvedWorkConfigurationRevision.resolve(history, date) }.getOrNull() ?: continue
                if (received.any { it.shift.localStartDate == date }) continue
                val statuses = runBlocking { store.explicitDayStatuses.observeBetween(date, date).first() }
                if (statuses.isNotEmpty()) continue
                source = runBlocking { received.firstOrNull { it.shift.status == ShiftStatus.PLANNED &&
                    it.snapshot.configurationRevisionId == revision.revision.id && it.snapshot.sector == revision.revision.value.sector &&
                    stage12TemplateEligible(store, it, date) } }
                if (source != null) { chosenDate = date; break }
            }
            val date = checkNotNull(chosenDate) { "C090_NO_EMPTY_PAST_DATE_WITH_VALID_SOURCE" }
            val original = checkNotNull(source)
            check(ids.all { runBlocking { store.v2Shifts.getShift(it) } == V2ShiftLookup.Missing }) { "C090_OWN_ID_ALREADY_EXISTS" }
            val zone = original.shift.zoneId
            val start = date.atTime(original.shift.startTimeSnapshot).atZone(zone).toInstant()
            val endDate = if (original.shift.endTimeSnapshot > original.shift.startTimeSnapshot) date else date.plusDays(1)
            val end = endDate.atTime(original.shift.endTimeSnapshot).atZone(zone).toInstant()
            check(java.time.Duration.between(start, end).toMinutes() > 15 && end < Instant.now()) { "C090_INVALID_PAST_TEMPLATE_DURATION" }
            val stamp = date.atStartOfDay(zone).toInstant()
            val writes = ids.map { id -> V2ShiftWrite(original.shift.copy(id = id, startAt = start, endAt = end,
                localStartDate = date,
                position = "PRIVATE_SYNTHETIC_C090", status = ShiftStatus.PLANNED, createdAt = stamp, updatedAt = stamp),
                original.snapshot.copy(shiftId = id)) }
            check(writes[0].copy(shift = writes[0].shift.copy(id = ids[1]), snapshot = writes[0].snapshot.copy(shiftId = ids[1])) == writes[1]) {
                "C090_PAIR_NOT_IDENTICAL_EXCEPT_UUID"
            }
            val notes = ids.mapIndexed { index, id -> ShiftNote(own("note-$index"), id, "PRIVATE_SYNTHETIC_C090", stamp, stamp) }
            val photoStore = SchedulePhotoFileStore(context)
            val image = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJZkAAAAASUVORK5CYII=", android.util.Base64.DEFAULT)
            val photoKeys = notes.map { "note_${it.id}.png" }
            runBlocking {
                writes.forEachIndexed { index, write ->
                    store.v2Shifts.insert(write); inserted += write.shift.id
                    val file = photoStore.file(photoKeys[index]); check(!file.exists()) { "C090_OWN_IMAGE_ALREADY_EXISTS" }
                    check(file.parentFile.isDirectory || file.parentFile.mkdirs())
                    Files.newOutputStream(file.toPath(), CREATE_NEW, WRITE).use {
                        ownFiles[file] = digest(image)
                        it.write(image)
                    }
                    val attachment = ShiftNoteAttachment(own("attachment-$index"), notes[index].id, photoKeys[index], "image/png",
                        image.size.toLong(), 1, 1, 0, stamp, stamp)
                    store.shiftNotes.saveWithAttachments(notes[index], listOf(attachment))
                    store.shiftNotificationConfigs.replace(ShiftNotificationConfig(write.shift.id, listOf(17, 41)))
                    val expected = checkNotNull(store.shiftActuals.getExpectation(write.shift.id))
                    val mutation = checkNotNull(buildShiftActualSaveMutation(expected,
                        ShiftActualDraft(start, end.minusSeconds(15 * 60), "PRIVATE_SYNTHETIC_C090", null, null, null, emptyList()),
                        Clock.systemUTC(), stamp))
                    check(store.shiftActuals.save(mutation) is ShiftActualWriteResult.Saved) { "C090_ACTUAL_FIXTURE_NOT_SAVED" }
                }
            }
            receipt.put("fixture_date", date.toString()).put("fixture_binding", "OWN_RUN_NAMESPACE_EMPTY_PAST_DATE_SAME_PAYLOAD")
                .put("timeline_id", history.timeline.id.toString()).put("first_uuid", ids[0].toString()).put("second_uuid", ids[1].toString())
                .put("associated_types", JSONArray(listOf("notes", "attachments", "actual_records", "notification_configs", "notification_reminders")))
            goToMonth(YearMonth.from(date))
            compose.waitUntil(30_000) {
                val loaded = calendar()
                loaded.loadState == CalendarLoadState.CONTENT && loaded.days.any { day ->
                    day.date == date && ids.all { wanted -> day.shifts.any { it.shift.id == wanted } }
                }
            }
            val tag = "day-$date"
            compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("completed-day-$date").fetchSemanticsNodes().isNotEmpty() }
            val dayTag = if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) tag else "completed-day-$date"
            compose.onNodeWithTag(dayTag).performClick()
            compose.waitUntil(30_000) { calendar().detailDate == date }
            waitTag("day-other-options")
            compose.onNodeWithTag("day-other-options").performScrollTo().performClick()
            compose.waitUntil(30_000) {
                val inspected = edit()
                inspected.inspectionState == V2ShiftDayInspectionState.CONTENT &&
                    ids.all { wanted -> inspected.dayRows.any { it.shift.id == wanted } }
            }
            waitTag("v2-edit-day-action"); compose.onNodeWithTag("v2-edit-day-action").performScrollTo().performClick()
            waitTag("v2-shift-edit-surface")
            compose.onNodeWithContentDescription("Jornada 1 de 2", substring = true).assertExists()
            compose.onNodeWithContentDescription("Jornada 2 de 2", substring = true).assertExists()
            val rows = edit().dayRows
            check(rows.size == 2 && rows[0].shift.id == ids[0] && rows[1].shift.id == ids[1] &&
                rows[0].ordinal == 1 && rows[1].ordinal == 2) { "C090_VISIBLE_ORDINAL_IDENTITY" }
            fact("ordinal", "UI_COMPOSE")
            val before = snapshot("before_cancel")
            val observedWrites = runBlocking { ids.map { (store.v2Shifts.getShift(it) as V2ShiftLookup.V2).write } }
            val observedRoles = linkedMapOf("first" to ids[0].toString(), "second" to ids[1].toString())
            notes.forEachIndexed { index, note -> observedRoles["note_$index"] = note.id.toString() }
            receipt.put("fixture_observation", JSONObject(Stage12ObservedFixture.work("C090", today, original.shift.id,
                observedWrites, observedRoles, mapOf("owned_image_bytes_sha256" to Stage12ObservedFixture.fingerprint(
                    photoKeys.map { key -> checkNotNull(before.files["files/schedule_photos/$key"]) {
                        "C090_OWN_IMAGE_READBACK_MISSING"
                    }.sha256 }.toString(), emptyMap()),
                    "associated_policy_sha256" to Stage12ObservedFixture.fingerprint(
                        "notes,attachments,actual_records,notification_configs,notification_reminders;17,41;actual_end_minus_15_minutes", emptyMap())))))
            fun requestSecond() {
                compose.onNodeWithTag("v2-delete-shift-${ids[1]}").performScrollTo().performClick()
                waitTag("v2-shift-delete-dialog")
                check(edit().targetShiftId == ids[1] && edit().originalWrite == writes[1]) { "C090_DELETE_TARGET_MISMATCH" }
            }
            requestSecond(); compose.onNodeWithText("Conservar jornada").performClick()
            compose.waitUntil(30_000) { edit().targetShiftId == null && !edit().isLoading }
            val cancelled = snapshot("after_cancel")
            check(before.tables == cancelled.tables) { "C090_CANCEL_CHANGED_SQLITE" }
            check(ownFiles.all { (file, hash) -> file.isFile && digest(file.readBytes()) == hash }) { "C090_CANCEL_CHANGED_OWN_FILES" }
            fact("cancel", "ROOM_ISOLATED")
            requestSecond()
            runBlocking { store.shiftNotes.update(notes[1].copy(body = "PRIVATE_SYNTHETIC_C090_CAS", updatedAt = stamp.plusMillis(1))) }
            val stale = snapshot("before_stale_confirmation")
            compose.onNodeWithTag("v2-shift-confirm-delete").performClick()
            compose.waitUntil(30_000) { edit().errorMessage != null && !edit().isSaving && !edit().isLoading }
            check(stale.tables == snapshot("after_stale_rejection").tables) { "C090_STALE_CAS_MUTATED_SQLITE" }
            check(ownFiles.all { (file, hash) -> file.isFile && digest(file.readBytes()) == hash }) { "C090_STALE_CAS_CHANGED_OWN_FILES" }
            fact("cas", "ROOM_ISOLATED")
            runBlocking { store.shiftNotes.update(notes[1]) }
            check(before.tables == snapshot("restored_before_fresh_confirmation").tables) { "C090_BASELINE_NOT_RESTORED" }
            requestSecond(); compose.onNodeWithTag("v2-shift-confirm-delete").performClick()
            compose.waitUntil(30_000) { runBlocking { store.v2Shifts.getShift(ids[1]) } == V2ShiftLookup.Missing }
            val after = snapshot("after_fresh_confirmation")
            val originalTables = before.tables.mapValues { Stage12C090Oracle.Table(it.value.columns, it.value.rows) }
            val second = Stage12FullStoreReadback.text(ids[1].toString())
            val removals = buildList {
                add(Stage12C090Oracle.Removal("shifts", "id", setOf(second)))
                before.tables.filterValues { "shiftId" in it.columns }.forEach { (name, _) ->
                    add(Stage12C090Oracle.Removal(name, "shiftId", setOf(second)))
                }
                add(Stage12C090Oracle.Removal("shift_note_attachments", "noteId", setOf(Stage12FullStoreReadback.text(notes[1].id.toString()))))
            }
            val expectedTables = Stage12C090Oracle.afterRemoving(originalTables, removals)
            check(expectedTables == after.tables.mapValues { Stage12C090Oracle.Table(it.value.columns, it.value.rows) }) { "C090_DELETE_CHANGED_OTHER_ROWS_OR_ASSOCIATES" }
            fact("target", "ACTIVITY_NATIVE"); fact("confirm", "ROOM_ISOLATED")
            val firstFile = photoStore.file(photoKeys[0])
            check(firstFile.isFile && digest(firstFile.readBytes()) == ownFiles.getValue(firstFile)) { "C090_FIRST_IMAGE_CHANGED" }
            val ownedBefore = ownFiles.mapKeys { it.key.name }
            val ownedAfter = ownFiles.keys.filter(File::exists).associate { it.name to digest(it.readBytes()) }
            check(Stage12C090Oracle.sameFilesExceptOwnedRemoval(ownedBefore, ownedAfter, setOf(photoKeys[1]))) { "C090_UNEXPECTED_FILE_CHANGE" }
            fact("first-files", "FILES_ISOLATED")
            check(facts.length() == 6)
        } catch (error: Throwable) {
            admission.failed(error); primary = error
            receipt.put("failure_class", error.javaClass.simpleName)
                .put("failure_readback_code", Stage12NativeDiagnostics.readbackCodeOrNull(error.message) ?: "NOT_READBACK")
        }
        finally {
            try {
                admission.phase(Stage12ActivityAdmission.Phase.CLOSE)
                scenario?.close(); scenario = null; receipt.put("activity_closed", true)
            } catch (error: Throwable) { admission.failed(error); primary = Stage12C090Oracle.retain(primary, error); receipt.put("activity_closed", false) }
            try {
                admission.phase(Stage12ActivityAdmission.Phase.POSTCONDITION)
                runBlocking {
                    inserted.forEach { id ->
                        val current = store.v2Shifts.getShift(id)
                        if (current is V2ShiftLookup.V2) store.v2Shifts.deleteShift(current.write, store.shiftActuals.getExpectation(id))
                    }
                    val referenced = store.shiftNotes.allAttachmentStorageKeys()
                    ownFiles.forEach { (file, hash) ->
                        check(file.name !in referenced) { "C090_CLEANUP_FILE_STILL_REFERENCED" }
                        if (file.exists()) { check(digest(file.readBytes()) == hash) { "C090_CLEANUP_FILE_CHANGED" }; check(file.delete()) }
                    }
                }
                initial?.let {
                    check(it.tables == snapshot("after_owned_fixture_cleanup").tables) { "C090_RECEIVED_SQLITE_CHANGED" }
                    receipt.put("received_sqlite_restored_verified", true)
                }
                receipt.put("cleanup_complete", true)
            } catch (error: Throwable) {
                admission.failed(error); primary = Stage12C090Oracle.retain(primary, error)
                receipt.put("cleanup_complete", false).put("cleanup_failure_class", error.javaClass.simpleName)
                    .put("cleanup_readback_code", Stage12NativeDiagnostics.readbackCodeOrNull(error.message) ?: "NOT_READBACK")
            }
            admission.phase(Stage12ActivityAdmission.Phase.PUBLISH)
            receipt.put("status", if (primary == null) "COMPLETE" else "FAILED").put("readiness", JSONObject(admission.wire()))
            try { Stage12NativeReceiptFiles.publish(output, receipt.toString(2), primary) }
            catch (error: Throwable) { admission.failed(error); primary = Stage12C090Oracle.retain(primary, error) }
            finally { admission.finish(primary == null) }
        }
        primary?.let { throw it }
    }
    private fun calendar(): CalendarUiState {
        var value: CalendarUiState? = null
        requireNotNull(scenario).onActivity { value = ViewModelProvider(it)[CalendarViewModel::class.java].uiState.value }
        return requireNotNull(value)
    }
    private fun edit(): V2ShiftEditUiState {
        var value: V2ShiftEditUiState? = null
        requireNotNull(scenario).onActivity { value = ViewModelProvider(it)[V2ShiftEditViewModel::class.java].uiState.value }
        return requireNotNull(value)
    }
    private fun waitTag(tag: String) { compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun goToMonth(target: YearMonth) {
        var count = 0
        while (calendar().visibleMonth != target) {
            check(count++ < 36) { "C090_MONTH_NAVIGATION_BOUND" }
            val previous = calendar().visibleMonth
            compose.onNodeWithContentDescription(if (previous < target) "Mes siguiente" else "Mes anterior").performClick()
            compose.waitUntil(30_000) { calendar().visibleMonth != previous && calendar().loadState == CalendarLoadState.CONTENT }
        }
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
