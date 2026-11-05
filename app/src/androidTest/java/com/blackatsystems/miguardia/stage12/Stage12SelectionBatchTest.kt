package com.blackatsystems.miguardia.stage12

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.backup.LocalDataMutationGate
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.repository.*
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.work.*
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.*
import com.blackatsystems.miguardia.ui.exceptions.*
import com.blackatsystems.miguardia.ui.management.*
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.worksetup.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Product wiring in ComponentActivity; NEW Room and private file roots per method/RUN.
 * No Application store, MainActivity/bootstrap, picker, external viewer or process-death claim.
 */
class Stage12SelectionBatchTest {
    private val diagnostic = Stage12SelectionDiagnostics.Trace()
    private val catalogPersistence = Stage12CatalogPersistenceCriterion()
    private val catalogNavigation = Stage12CatalogNavigation()
    private val dialogCancellation = Stage12DialogCancellation()
    private val clearCasObservation = Stage12ClearCasObservation()
    private var retainedFailure: Throwable? = null
    private var pendingReceipt: JSONObject? = null
    private var pendingOutput: File? = null
    private var pendingCleanup: (() -> Unit)? = null
    private val compose = safeConstruction { createEmptyComposeRule() }
    // Applying Compose inside evaluate also encloses failures while Compose builds its statement.
    @get:Rule val rules: TestRule = safeConstruction {
        object : TestRule {
            override fun apply(base: Statement, description: Description): Statement = object : Statement() {
                override fun evaluate() = evaluateGuarded { compose.apply(base, description).evaluate() }
            }
        }
    }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val realQa get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val zone = com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId()
    private val clock = Clock.system(zone)
    private val sequence = AtomicInteger()
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var activity: ComponentActivity? = null
    private var store: LocalDataStore? = null
    private val own get() = checkNotNull(store) { "SELECTION_STORE_CLOSED" }
    private lateinit var calendar: CalendarViewModel
    private lateinit var manual: V2ManualShiftLoadViewModel
    private lateinit var work: WorkSetupViewModel
    private lateinit var exceptions: ExceptionsViewModel
    private lateinit var notes: ManualLoadNotes
    private lateinit var imageStore: SchedulePhotoFileStore
    private lateinit var mutationGate: LocalDataMutationGate
    private lateinit var batchWriter: BatchWriter
    private lateinit var catalogWriter: CatalogWriter
    private lateinit var run: String
    private lateinit var slot: String
    private lateinit var anchor: LocalDate
    private lateinit var dbName: String
    private lateinit var dbPath: File
    private lateinit var privateRoot: File
    private lateinit var dataRoot: File
    private lateinit var sourceRoot: File
    private lateinit var fileContext: Context
    private lateinit var fixture: CatalogFixture
    private val facts = JSONArray()
    private val uiOracles = linkedSetOf<String>()
    private val nativeOracles = linkedSetOf<String>()
    private val backEvents = mutableListOf<Pair<String, Stage12OwnedBackEventSemantics.Observation>>()
    private var phase = "PRECONDITIONS"
    private var action = 0
    private var lastActionHash = "NONE"
    private var reopens = 0
    private var recreates = 0
    private val ownedDatabases = mutableListOf<Pair<String, File>>()
    private val openedDatabases = mutableSetOf<String>()
    private val c get() = calendar.uiState.value
    private val m get() = manual.uiState.value
    private val w get() = work.uiState.value
    private val e get() = exceptions.uiState.value

    @Test fun selectionContextsBackAndCompleteDraftKeepOnlyFinalDates() = safe {
        family("selection-batch-draft") { selectionAndDraft() }
    }
    @Test fun occupiedPoliciesBackfillAndExplicitDayStatesPreserveNeighbours() = safe {
        family("selection-batch-policies", configuredToday = true) { policiesAndStates() }
    }
    @Test fun mixedClearAndPostReviewChangesRejectWholeBatches() = safe {
        family("selection-batch-clear-cas") { clearAndCas() }
    }
    @Test fun doubleTouchAndLostCommitResponseRecoverExactRowsAndBytes() = safe {
        family("selection-batch-recovery") { doubleTouchAndRecovery() }
    }

    // Keep originals in RAM; outgoing errors contain no original cause, suppressed error or text.
    // The exterior rule covers Compose setup/teardown and publishes only after the entire rule.
    private fun <T> safeConstruction(factory: () -> T): T = try { factory() } catch (error: Throwable) {
        retainedFailure = diagnostic.retain(error)
        throw Stage12SelectionDiagnostics.publicFailure()
    }

    private fun safe(body: () -> Unit) {
        try { body() } catch (error: Throwable) {
            retainedFailure = diagnostic.retain(error)
            throw Stage12SelectionDiagnostics.publicFailure()
        }
    }

    private fun evaluateGuarded(body: () -> Unit) {
        diagnostic.finish(body = body, cleanup = {
            // Resource deletion follows owner/store closure AND successful Compose teardown.
            phase = "POST_RULE_OWN_CLEANUP"
            checkNotNull(pendingCleanup) { "SELECTION_CLEANUP_NOT_PREPARED" }.invoke()
        }, publish = { ruleSucceeded, cleaned, primary ->
            pendingReceipt?.let { receipt ->
                if (primary != null && !receipt.has("failure_phase")) {
                    receipt.put("failure_phase", if (ruleSucceeded) phase else "COMPOSE_RULE_EVALUATION")
                        .put("failure_action_ordinal", action).put("failure_action_target_sha256", lastActionHash)
                }
                receipt.put("rule_evaluation_succeeded", ruleSucceeded).put("receipt_completion_scope", "WHOLE_TEST_RULE")
                    .put("cleanup_complete", cleaned).put("owned_resources_retained_on_failure", !cleaned)
                    .put("diagnostic", JSONObject(diagnostic.projection()))
                    .put("catalog_persistence", JSONObject(catalogPersistence.projection()))
                    .put("catalog_navigation", JSONObject(catalogNavigation.projection()))
                    .put("dialog_cancellation", JSONObject(dialogCancellation.projection()))
                    .put("clear_cas_observation", JSONObject(clearCasObservation.projection()))
                    .put("status", if (primary == null && cleaned) "COMPLETE" else "FAILED")
                Stage12NativeReceiptFiles.publish(checkNotNull(pendingOutput), receipt.toString(2), primary)
            }
        })
        retainedFailure = diagnostic.primary
        if (retainedFailure != null) throw Stage12SelectionDiagnostics.publicFailure()
    }

    private fun family(name: String, configuredToday: Boolean = false, body: () -> Unit) {
        check(instrumentation is Stage12NormalQaRunner && realQa.packageName == QA) { "SELECTION_RUNNER_TARGET" }
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("stage12CalendarClock") == "REAL") { "SELECTION_REAL_REQUIRED" }
        run = checkNotNull(args.getString("stage12RunId")); check(UUID.fromString(run).toString() == run)
        val qa = checkNotNull(args.getString("stage12QaSha256"))
        val runner = checkNotNull(args.getString("stage12RunnerSha256"))
        check(listOf(qa, runner).all { it.matches(Regex("[0-9a-f]{64}")) })
        slot = name; anchor = LocalDate.now(clock)
        dbName = "stage12-selection-$run-$slot.db"
        val dbDirectory = File(realQa.applicationInfo.dataDir, "databases").canonicalFile
        dbPath = realQa.getDatabasePath(dbName).canonicalFile
        check(dbPath.parentFile == dbDirectory && dbName != "miguardia-v2.db") { "SELECTION_DB_FENCE" }
        val identity = JSONObject().put("run_uuid", run).put("qa_sha256", qa).put("runner_sha256", runner)
        val output = Stage12NativeReceiptFiles.prepareOutput(realQa.filesDir, run, identity.toString(), slot)
        val receipt = JSONObject(identity.toString()).put("schema", "stage12-selection-batch-family-v1")
            .put("case", slot).put("calendar_clock_mode", "REAL").put("facts", facts)
            .put("host", "COMPONENT_ACTIVITY_REAL_PRODUCT_WIRING").put("main_bootstrap_proven", false)
            .put("picker_or_external_viewer_proven", false).put("process_death_proven", false)
            .put("received_application_store_accessed", false).put("global_settings_changed", false)
            .put("round_credit", 0).put("status", "INCOMPLETE")
        pendingOutput = output; pendingReceipt = receipt; if (c097Enabled) c097Receipt(receipt)
        var primary: Throwable? = null
        var created = false
        var creationAttempted = false
        var ownerClosed = false
        var storeClosed = false
        try {
            check(listOf("", "-wal", "-shm", "-journal").none { Files.exists(File(dbPath.path + it).toPath(), NOFOLLOW_LINKS) }) { "SELECTION_DB_ALREADY_EXISTS" }
            privateRoot = newDirectory(realQa.filesDir, "stage12-selection-private-$run-$slot")
            dataRoot = newDirectory(privateRoot, "case-initial")
            sourceRoot = newDirectory(File(realQa.filesDir, "reports/artifacts"), "stage12-selection-sources-$run-$slot")
            creationAttempted = true
            ownedDatabases += dbName to dbPath
            store = LocalDataStore.create(realQa, dbName); created = true
            openedDatabases += dbName
            phase = "FIXTURE"
            seedCatalog(if (configuredToday) anchor else anchor.minusYears(1))
            createServices()
            launch()
            phase = "BODY"
            body()
            check(uiOracles.isNotEmpty() && nativeOracles.isNotEmpty())
            if (!c097Enabled) fact("product-ui-observations", emptyList(), "UI_COMPOSE", JSONObject().put("oracle_codes", JSONArray(uiOracles.sorted())))
            if (!c097Enabled) fact("native-transitions", emptyList(), "ACTIVITY_NATIVE", JSONObject().put("oracle_codes", JSONArray(nativeOracles.sorted())))
            phase = "REOPEN_FINAL"
            if (!c097Enabled) reopen()
            receipt.put("final_tables_sha256", hash(snapshot().toString())).put("final_files_sha256", hash(files().toString()))
        } catch (error: Throwable) {
            primary = diagnostic.retain(error)
            receipt.put("failure_phase", phase)
                .put("failure_action_ordinal", action).put("failure_action_target_sha256", lastActionHash)
        } finally {
            fun secondary(error: Throwable) {
                primary = diagnostic.retain(error)
            }
            phase = "CLOSE_OWNER"
            try { diagnostic.closing(Stage12SelectionDiagnostics.Checkpoint.CLOSE_OWNER) {
                closeOwner(); ownerClosed = true
            } } catch (error: Throwable) { secondary(error) }
            phase = "CLOSE_STORE"
            if (ownerClosed) try { diagnostic.closing(Stage12SelectionDiagnostics.Checkpoint.CLOSE_STORE) {
                store?.close(); store = null; storeClosed = true
            } } catch (error: Throwable) { secondary(error) }
            if (primary == null && created && ownerClosed && storeClosed) pendingCleanup = {
                ownedDatabases.forEach { (name, path) ->
                    check(path.parentFile == dbDirectory && path == realQa.getDatabasePath(name).canonicalFile && !Files.isSymbolicLink(path.toPath()))
                    check(realQa.deleteDatabase(name)) { "SELECTION_DB_DELETE_FAILED" }
                    check(listOf("", "-wal", "-shm", "-journal").none { Files.exists(File(path.path + it).toPath(), NOFOLLOW_LINKS) })
                }
                deleteOwnTree(privateRoot, realQa.filesDir, "stage12-selection-private-$run-$slot")
                deleteOwnTree(sourceRoot, File(realQa.filesDir, "reports/artifacts"), "stage12-selection-sources-$run-$slot")
            }
            if (primary != null && !receipt.has("failure_phase")) receipt.put("failure_phase", phase)
            receipt.put("owner_closed", ownerClosed).put("store_closed", storeClosed).put("cleanup_complete", false)
                .put("database_creation_attempted", creationAttempted).put("partial_creation_uncertain", creationAttempted &&
                    (!created || ownedDatabases.any { (name, _) -> name !in openedDatabases }))
                .put("owned_resources_retained_on_failure", true).put("new_owner_store_reopens", reopens)
                .put("activity_recreates", recreates)
                .put("owned_database_count", ownedDatabases.size)
                .put("back_events", backEventsForReceipt())
                .put("status", if (primary == null) "BODY_COMPLETE_RULE_PENDING" else "FAILED")
            retainedFailure = primary
        }
        primary?.let { throw it }
    }

    private fun createServices() {
        val exactFiles = dataRoot
        fileContext = object : ContextWrapper(realQa) { override fun getFilesDir(): File = exactFiles }
        mutationGate = LocalDataMutationGate()
        notes = ManualLoadNotes(fileContext, own.shiftNotes, own.shiftSituations, mutationGate)
        imageStore = SchedulePhotoFileStore(fileContext)
        batchWriter = BatchWriter(own.v2Shifts)
        catalogWriter = CatalogWriter(own.workCatalog)
    }

    private fun launch() {
        check(scenario == null)
        scenario = ActivityScenario.launch(Intent().setComponent(ComponentName(QA, ComponentActivity::class.java.name)))
        mount()
        await { activity?.lifecycle?.currentState == Lifecycle.State.RESUMED && c.loadState == CalendarLoadState.CONTENT && w.rootState is WorkSetupState.V2Ready }
    }

    private fun mount() {
        checkNotNull(scenario).onActivity { owner ->
            activity = owner; if (c097Enabled) c097MountEntered()
            calendar = ViewModelProvider(owner, CalendarViewModel.Factory(own.shifts, own.explicitDayStatuses,
                own.medicalLeaves, own.holidays, own.vacations, clock, own.shiftSituations, imageStore,
                mutationGate, own.calendarBatches)).get(key("calendar"), CalendarViewModel::class.java)
            work = ViewModelProvider(owner, WorkSetupViewModel.Factory(own.workConfiguration, catalogWriter,
                own.objectives, clock, { uuid("work") })).get(key("work"), WorkSetupViewModel::class.java)
            manual = ViewModelProvider(owner, V2ManualShiftLoadViewModel.Factory(own.workConfiguration, own.workCatalog,
                own.objectives, own.shifts, own.medicalLeaves, batchWriter, own.shiftActuals, own.explicitDayStatuses,
                clock, zone, UuidProvider { uuid("load") }, own.calendarBatches, notes))
                .get(key("manual"), V2ManualShiftLoadViewModel::class.java)
            exceptions = ViewModelProvider(owner, ExceptionsViewModel.Factory(own.holidays, own.shiftNotes,
                own.shifts, imageStore, clock, { uuid("exception") }, mutationGate)).get(key("exceptions"), ExceptionsViewModel::class.java)
            // Product image previews also construct their own FileStore from LocalContext.
            // Wrap the Activity (not Application); native owners remain those of `owner`.
            val exactFiles = dataRoot
            val uiContext = object : ContextWrapper(owner) { override fun getFilesDir(): File = exactFiles }
            owner.setContent(parent = if (c097Enabled) c097CompositionParent(owner) else null) {
                val cs by calendar.uiState.collectAsState()
                val ms by manual.uiState.collectAsState()
                val ws by work.uiState.collectAsState()
                val es by exceptions.uiState.collectAsState()
                CompositionLocalProvider(LocalContext provides uiContext) {
                    MiGuardiaTheme {
                        MiGuardiaApp(calendarState = cs, onPreviousMonth = calendar::showPreviousMonth,
                            onNextMonth = calendar::showNextMonth, onToday = calendar::showCurrentMonth,
                            onSelectDate = calendar::selectDate, onDismissDate = calendar::clearSelectedDate,
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
                            v2ManualShiftLoadState = ms, v2ManualShiftLoadActions = V2ManualShiftLoadActions.from(manual),
                            workSetupState = ws, workSetupActions = WorkSetupActions.from(work),
                            exceptionsState = es, exceptionsActions = ExceptionsActions.from(exceptions))
                    }
                }
            }; if (c097Enabled) c097ContentInstalled(owner)
        }
    }

    private fun recreate() {
        val previous = checkNotNull(activity)
        checkNotNull(scenario).recreate(); mount()
        await { activity !== previous && previous.isDestroyed && c.loadState == CalendarLoadState.CONTENT }
        recreates++
    }

    private fun reopen() {
        val before = snapshot(); val fileBefore = files(); val oldStore = own; val oldOwner = activity
        closeOwner(); own.close(); store = null
        store = LocalDataStore.create(realQa, dbName); createServices(); launch()
        check(own !== oldStore && activity !== oldOwner)
        check(snapshot() == before && files() == fileBefore) { "SELECTION_REOPEN_DIFFERS" }
        reopens++
        fact("new-owner-store-reopen", listOf("C096-activity-recreate", "C100"), "ROOM_ISOLATED", JSONObject().put("tables_sha256", hash(before.toString())).put("bytes_sha256", hash(fileBefore.toString())))
    }

    private fun closeOwner() { if (c097Enabled) { c097CloseOwner(); return }
        scenario?.close(); check(activity == null || activity!!.isDestroyed) { "SELECTION_OWNER_CLOSE_UNCERTAIN" }
        scenario = null; activity = null
    }

    /** Separate DB + file subroot for every causal CAS adversary; no repairs or accumulated mutations. */
    private fun isolatedCase(label: String) {
        check(label.matches(Regex("[a-z-]+")))
        closeOwner(); own.close(); store = null
        dbName = "stage12-selection-$run-$slot-$label.db"
        dbPath = realQa.getDatabasePath(dbName).canonicalFile
        check(dbPath.parentFile == File(realQa.applicationInfo.dataDir, "databases").canonicalFile)
        check(listOf("", "-wal", "-shm", "-journal").none { Files.exists(File(dbPath.path + it).toPath(), NOFOLLOW_LINKS) })
        dataRoot = newDirectory(privateRoot, "case-$label")
        ownedDatabases += dbName to dbPath
        store = LocalDataStore.create(realQa, dbName)
        openedDatabases += dbName
        seedCatalog(anchor.minusYears(1)); createServices(); launch()
        phase = "CASE_${label.uppercase()}"
    }

    private data class CatalogFixture(val objective: Objective, val place: WorkPlace, val type: WorkType,
        val day: WorkTemplate, val night: WorkTemplate, val rule: WorkplaceRuleRevision)

    private fun seedCatalog(from: LocalDate) = db {
        val now = stamp().minusSeconds(60)
        val revision = EffectiveRevision(uuid("revision"), from, WorkConfiguration(WorkSector.NURSING,
            HoursReference.PendingSetup, AvailabilityLabel.ON_CALL_RETAINER))
        val timeline = uuid("timeline")
        own.workConfiguration.createInitial(timeline, revision)
        val objective = Objective(uuid("objective"), "Synthetic institution $run", "OWN", "Synthetic address", null, true, now, now)
        val place = WorkPlace(uuid("place"), timeline, WorkSector.NURSING, objective.id, true, now, now)
        val type = WorkType.create(uuid("type"), timeline, WorkSector.NURSING, "Turno propio", now)
        val day = WorkTemplate(uuid("day"), timeline, WorkSector.NURSING, place.id, objective.id, type.id,
            LocalTime.of(8, 15), LocalTime.of(16, 45), 0xFF1E88E5.toInt(), true, now, now)
        val night = day.copy(id = uuid("night"), startTime = LocalTime.of(22, 15), endTime = LocalTime.of(6, 45))
        val rule = WorkplaceRuleRevision(uuid("rule"), timeline, WorkSector.NURSING, place.id, objective.id, from,
            WorkplaceRules(NightHoursRule.Disabled, WeekendRule.None, HolidayRule(false, false)), now)
        own.workCatalog.createFirstWorkSet(FirstWorkSet(objective, place, rule,
            ResolvedWorkConfigurationRevision.resolve(checkNotNull(own.workConfiguration.get()), from), type, day))
        own.workCatalog.createWorkTemplate(night)
        fixture = CatalogFixture(objective, place, type, day, night, rule)
    }

    private fun seededWrite(date: LocalDate, template: WorkTemplate = fixture.day, position: String? = null): V2ShiftWrite = db {
        buildV2ShiftWrite(uuid("shift"), date, fixture.objective, fixture.place, fixture.type, template,
            ResolvedWorkConfigurationRevision.resolve(checkNotNull(own.workConfiguration.get()), date), position, stamp(), zone)
    }

    private class BatchWriter(private val delegate: V2ShiftRepository) : V2ShiftRepository by delegate {
        var hold: CompletableDeferred<Unit>? = null
        var entered = CompletableDeferred<Unit>()
        var loseResponseOnce = false
        var committed: V2ShiftBatchMutation? = null
        override suspend fun applyV2Batch(mutation: V2ShiftBatchMutation, expectedOccupancy: ShiftOccupancyExpectation,
            expectedUpdates: V2ShiftWriteExpectation) {
            entered.complete(Unit); hold?.await()
            delegate.applyV2Batch(mutation, expectedOccupancy, expectedUpdates)
            if (loseResponseOnce) {
                loseResponseOnce = false; committed = mutation
                // Fault happens strictly AFTER the real Room transaction returned.
                throw IllegalStateException("SELECTION_OWN_POSTCOMMIT_RESPONSE_LOST")
            }
        }
    }

    private class CatalogWriter(private val delegate: WorkCatalogRepository) : WorkCatalogRepository by delegate {
        var loseTemplateOnce = false
        var recoveryBlocked = false
        var committedTemplate: WorkTemplate? = null
        override suspend fun createWorkTemplate(workTemplate: WorkTemplate) {
            delegate.createWorkTemplate(workTemplate)
            if (loseTemplateOnce) {
                loseTemplateOnce = false; committedTemplate = workTemplate; recoveryBlocked = true
                throw IllegalStateException("SELECTION_OWN_CATALOG_RESPONSE_LOST")
            }
        }
        override suspend fun getWorkTemplate(id: UUID): WorkTemplate? {
            if (recoveryBlocked) throw IllegalStateException("SELECTION_OWN_RECOVERY_READ_BLOCKED")
            return delegate.getWorkTemplate(id)
        }
    }

    private data class Table(val columns: List<String>, val rows: List<List<String>>)
    private fun snapshot(): Map<String, Table> {
        check(dbPath == realQa.getDatabasePath(dbName).canonicalFile)
        val connection = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        try {
            check(connection.isReadOnly); connection.beginTransactionReadOnly()
            val names = connection.rawQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            check("shifts" in names && "shift_work_snapshots" in names)
            val result = (names + "sqlite_master").distinct().sorted().associateWith { name ->
                connection.rawQuery("SELECT * FROM \"${name.replace("\"", "\"\"")}\"", null).use { cursor ->
                    Table(cursor.columnNames.toList(), buildList {
                        while (cursor.moveToNext()) add((0 until cursor.columnCount).map { index ->
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> "N"
                                Cursor.FIELD_TYPE_BLOB -> "B:" + Base64.getEncoder().encodeToString(cursor.getBlob(index))
                                Cursor.FIELD_TYPE_INTEGER -> "I:" + cursor.getLong(index)
                                Cursor.FIELD_TYPE_FLOAT -> "F:" + cursor.getDouble(index).toRawBits()
                                else -> "S:" + cursor.getString(index)
                            }
                        })
                    }.sortedBy { it.joinToString("\u0000") })
                }
            }
            connection.setTransactionSuccessful(); return result
        } finally { if (connection.inTransaction()) connection.endTransaction(); connection.close() }
    }

    private fun files(): Map<String, String> {
        check(privateRoot.isDirectory && !Files.isSymbolicLink(privateRoot.toPath()))
        return Files.walk(privateRoot.toPath()).use { paths -> paths.iterator().asSequence().toList().filter { path ->
            check(!Files.isSymbolicLink(path)) { "SELECTION_PRIVATE_LINK" }; Files.isRegularFile(path, NOFOLLOW_LINKS)
        }.sorted().associate { privateRoot.toPath().relativize(it).toString() to hash(Files.readAllBytes(it)) } }
    }

    private fun unchangedExcept(before: Map<String, Table>, after: Map<String, Table>, allowed: Set<String>) {
        check(before.keys == after.keys)
        check(before.filterKeys { it !in allowed } == after.filterKeys { it !in allowed }) { "SELECTION_UNRELATED_TABLE_CHANGED" }
    }

    private fun newDirectory(parent: File, name: String): File {
        val base = parent.canonicalFile; check(base.isDirectory)
        var checkPath = parent.absoluteFile
        while (checkPath != realQa.filesDir.absoluteFile && checkPath.parentFile != null) {
            check(!Files.isSymbolicLink(checkPath.toPath())); checkPath = checkPath.parentFile
        }
        val root = File(base, name)
        check(root.parentFile == base && !Files.exists(root.toPath(), NOFOLLOW_LINKS)) { "SELECTION_OWN_ROOT_EXISTS" }
        check(root.mkdir()); return root.canonicalFile
    }

    private fun deleteOwnTree(root: File, parent: File, name: String) {
        check(root == File(parent.canonicalFile, name) && root.parentFile == parent.canonicalFile && !Files.isSymbolicLink(root.toPath()))
        val paths = Files.walk(root.toPath()).use { it.iterator().asSequence().toList() }
        check(paths.all { it.startsWith(root.toPath()) && !Files.isSymbolicLink(it) })
        paths.sortedByDescending { it.nameCount }.forEach { Files.delete(it) }
        check(!Files.exists(root.toPath(), NOFOLLOW_LINKS))
    }

    private fun importImage(): Pair<String, String> {
        val source = File(sourceRoot, "source-${sequence.incrementAndGet()}.png")
        check(!source.exists())
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val bytes = try { ByteArrayOutputStream().use { output ->
            bitmap.eraseColor(android.graphics.Color.CYAN); check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray()
        } } finally { bitmap.recycle() }
        check(source.createNewFile()); source.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(realQa, "$QA.fileprovider", source)
        checkNotNull(scenario).onActivity { manual.importNoteImages(listOf(uri)) }
        await { !m.isLoading && m.noteAttachments.size == 1 }
        val image = m.noteAttachments.single()
        check(hash(File(dataRoot, "manual_load_drafts/${image.storageKey}").readBytes()) == hash(bytes)) { "SELECTION_IMPORT_BYTES" }
        return image.storageKey to hash(bytes)
    }

    private fun allWrites(): List<V2ShiftWrite> = db { own.v2Shifts.observeAll(fixture.place.timelineId, WorkSector.NURSING).first().sortedBy { it.shift.id } }
    private fun notesFor(id: UUID) = db { own.shiftNotes.observeWithAttachmentsForShift(id).first() }

    private fun selectionAndDraft() {
        val day = anchor.plusMonths(2).withDayOfMonth(10)
        goMonth(YearMonth.from(day))
        val baseline = snapshot(); val originalFiles = files()
        compose.onNodeWithTag("day-$day").assertIsDisplayed().performTouchInput { longClick(durationMillis = 800) }
        await { c.selectionPurpose == CalendarSelectionPurpose.GENERAL && c.editSelectedDates == setOf(day) }
        check(c.detailDate == null)
        touch("day-${day.plusDays(1)}"); check(c.editSelectedDates == setOf(day, day.plusDays(1)) && c.detailDate == null)
        touch("day-${day.plusDays(1)}"); check(c.editSelectedDates == setOf(day))
        touch("day-$day"); compose.onNodeWithTag("calendar-selection-continue").assertIsNotEnabled()
        ui("ZERO_GENERAL_CONTINUE_DISABLED") { compose.onNodeWithTag("calendar-selection-continue").assertIsNotEnabled() }
        check(c.editSelectedDates.isEmpty() && c.detailDate == null)
        touch("day-$day")
        val oldMonth = c.visibleMonth
        touch("calendar-next-month"); text("Conservar selección")
        check(c.visibleMonth == oldMonth && c.editSelectedDates == setOf(day))
        touch("calendar-next-month"); text("Cambiar de mes")
        await { c.visibleMonth == oldMonth.plusMonths(1) && c.loadState == CalendarLoadState.CONTENT }
        check(c.editSelectedDates.isEmpty())
        back("GENERAL_EMPTY_MONTH_EXIT",
            before = { c.interactionMode == CalendarInteractionMode.EDIT && c.selectionPurpose == CalendarSelectionPurpose.GENERAL &&
                c.visibleMonth == oldMonth.plusMonths(1) && c.editSelectedDates.isEmpty() && shown("calendar-selection-continue") },
            expectedUi = { selectionExitedToGrid(day.plusMonths(1)) })
        generalSelect(setOf(day))
        touch("calendar-selection-continue")
        compose.onNodeWithText("¿Qué querés hacer?").assertIsDisplayed()
        back("GENERAL_ACTION_SHEET_CLOSE",
            before = { c.interactionMode == CalendarInteractionMode.EDIT && c.selectionPurpose == CalendarSelectionPurpose.GENERAL &&
                c.editSelectedDates == setOf(day) && compose.onNodeWithText("¿Qué querés hacer?").isDisplayed() },
            expectedUi = { c.interactionMode == CalendarInteractionMode.EDIT && !c.editSelectionConfirmed &&
                c.editSelectedDates == setOf(day) && c.selectionPurpose == CalendarSelectionPurpose.GENERAL &&
                compose.onAllNodesWithText("¿Qué querés hacer?").fetchSemanticsNodes().isEmpty() && shown("calendar-selection-continue") })
        check(c.editSelectedDates == setOf(day) && c.selectionPurpose == CalendarSelectionPurpose.GENERAL)
        back("GENERAL_SELECTED_EXIT",
            before = { c.interactionMode == CalendarInteractionMode.EDIT && c.selectionPurpose == CalendarSelectionPurpose.GENERAL &&
                c.editSelectedDates == setOf(day) && shown("calendar-selection-continue") },
            expectedUi = { selectionExitedToGrid(day) })
        check(snapshot() == baseline && files() == originalFiles)
        native("GENERAL_LONG_PRESS_TAP_BACK_MONTH", c.interactionMode == CalendarInteractionMode.VIEW && c.editSelectedDates.isEmpty())
        fact("general-selection-back-month", listOf("C035", "C036", "C037", "C038", "C039", "C040", "C041"), "ACTIVITY_NATIVE")

        drawer("drawer-action-holidays"); touch("holiday-open-calendar-selection")
        await { c.selectionPurpose == CalendarSelectionPurpose.HOLIDAY && e.holidaySelectionActive }
        touch("day-$day"); check(c.editSelectedDates == setOf(day))
        await { e.holidayDraft.selectedDates == setOf(day) }
        val preservedHolidayDraft = e.holidayDraft
        back("HOLIDAY_SELECTION_TO_EDITOR",
            before = { e.holidaySelectionActive && c.interactionMode == CalendarInteractionMode.EDIT &&
                c.selectionPurpose == CalendarSelectionPurpose.HOLIDAY && c.editSelectedDates == setOf(day) &&
                e.holidayDraft == preservedHolidayDraft && shown("holiday-confirm-calendar-selection") },
            expectedUi = { !e.holidaySelectionActive && e.surface == ExceptionsSurface.HOLIDAYS &&
                e.holidayDraft == preservedHolidayDraft && c.interactionMode == CalendarInteractionMode.VIEW &&
                shown("holiday-open-calendar-selection") && !exists("holiday-confirm-calendar-selection") })
        native("HOLIDAY_BACK_RETURNS_PARENT", e.surface == ExceptionsSurface.HOLIDAYS && !e.holidaySelectionActive)
        // One Back left the selection, not its parent editor; discard is a second explicit decision.
        back("HOLIDAY_EDITOR_DISCARD_DIALOG",
            before = { e.surface == ExceptionsSurface.HOLIDAYS && !e.holidaySelectionActive && !e.isSaving &&
                e.holidayDraft == preservedHolidayDraft && e.holidayDraft.selectedDates == setOf(day) &&
                shown("holiday-open-calendar-selection") && !compose.onNodeWithText("Descartar cambios").isDisplayed() },
            expectedUi = { e.surface == ExceptionsSurface.HOLIDAYS && e.holidayDraft == preservedHolidayDraft &&
                compose.onNodeWithText("Descartar cambios").isDisplayed() &&
                compose.onNode(hasText("Descartar") and hasClickAction()).isDisplayed() })
        text("Descartar"); await { e.surface == ExceptionsSurface.NONE }
        drawer("drawer-action-calendar-load")
        await { m.stage == V2ManualShiftLoadStage.SELECT_DATES }
        compose.onNodeWithTag("v2-manual-confirm-dates").assertIsNotEnabled()
        back("MANUAL_INITIAL_CANCEL",
            before = { m.stage == V2ManualShiftLoadStage.SELECT_DATES && !m.isLoading && !m.isSaving &&
                m.selectedDates.isEmpty() && c.selectionPurpose == CalendarSelectionPurpose.MANUAL_LOAD &&
                c.editSelectedDates.isEmpty() && shown("v2-manual-confirm-dates") },
            expectedUi = { !m.isActive && c.interactionMode == CalendarInteractionMode.VIEW &&
                c.editSelectedDates.isEmpty() && shown("month-grid") && !exists("calendar-primary-actions") &&
                !exists("v2-manual-confirm-dates") })
        native("MANUAL_BACK_CANCELS_INITIAL", !m.isActive && c.editSelectedDates.isEmpty())
        check(snapshot() == baseline && files() == originalFiles)
        fact("holiday-and-manual-back", listOf("C039", "C041", "C069"), "ACTIVITY_NATIVE")

        val initialDates = (0L..3L).map(day::plusDays).toSet()
        generalSelect(initialDates); touch("calendar-selection-continue"); text("Cargar jornadas")
        await { c.selectionPurpose == CalendarSelectionPurpose.MANUAL_LOAD && m.selectedDates == initialDates }
        touch("v2-manual-confirm-dates"); await { m.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE && !m.isLoading }
        observeManualForm(initialDates)
        choose(); noteAndPosition(); var image = importImage()
        val removedImage = image.first
        await { compose.onAllNodesWithContentDescription("Imagen 1 de la nota").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Imagen 1 de la nota").performTouchInput { click() }
        await { compose.onAllNodesWithContentDescription("Imagen adjunta a la nota").fetchSemanticsNodes().isNotEmpty() }
        ui("PRIVATE_DRAFT_PREVIEW") { compose.onNodeWithText("Imagen de la nota").assertIsDisplayed(); compose.onNodeWithContentDescription("Imagen adjunta a la nota").assertIsDisplayed() }
        text("Cerrar"); text("Quitar imagen 1")
        await { m.noteAttachments.isEmpty() && !File(dataRoot, "manual_load_drafts/$removedImage").exists() }
        check(m.noteBody == "Own note $run" && m.position == "Own position $run" && allWrites().isEmpty())
        image = importImage()
        touch("manual-remove-position"); check(m.position.isEmpty() && m.selectedTemplateId == fixture.day.id)
        togglePositionEditor(expandedBefore = false, expectedPosition = "")
        fill("v2-manual-position", "Own position $run")
        ui("POSITION_REMOVE_READD") { compose.onNodeWithTag("v2-manual-position").assertTextContains("Own position $run") }
        val loadIdentity = checkNotNull(m.operationId)
        val loadTimestamp = checkNotNull(m.operationTimestamp)
        val loadImages = m.noteAttachments
        touch("manual-add-note")
        checked(Stage12SelectionDiagnostics.Checkpoint.NOTE_SUMMARY_PRESENT) { compose.onNodeWithTag("manual-note-summary").assertExists() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NOTE_SUMMARY_CONTENT) { compose.onNodeWithTag("manual-note-summary").assertTextContains("Own note $run") }
        touch("manual-add-note"); ui("DRAFT_IMAGE_VISIBLE") { compose.onNodeWithTag("note-draft-image-1").assertIsDisplayed() }
        togglePositionEditor(expandedBefore = true, expectedPosition = "Own position $run")
        togglePositionEditor(expandedBefore = false, expectedPosition = "Own position $run")
        touch("manual-expand-dates")
        val fullDates = initialDates.sorted().joinToString("\n") { it.format(java.time.format.DateTimeFormatter.ofPattern("EEEE dd/MM/yy", java.util.Locale.forLanguageTag("es-AR"))).replaceFirstChar { char -> char.titlecase(java.util.Locale.forLanguageTag("es-AR")) } }
        ui("EXACT_EXPANDED_DATES") { compose.onNodeWithTag("v2-manual-exact-dates").assertTextContains(fullDates) }
        touch("manual-expand-dates"); check(m.selectedDates == initialDates)

        // Contextual catalog writes are real; only their response/recovery read is controlled.
        prepareCatalog("v2-manual-create-place", Stage12CatalogNavigation.Child.CREATE_PLACE); touch("v2-manual-create-place")
        await { w.creationRequest != null && w.surface == WorkSetupSurface.ADDITIONAL_PLACE }
        val request = checkNotNull(w.creationRequest)
        fill("work-place-name", "Own contextual $run"); fill("work-place-abbreviation", "CTX")
        touch("work-place-continue"); await { w.surface == WorkSetupSurface.ADDITIONAL_TEMPLATE && !w.isSavingWorkSet }
        check(db { own.workCatalog.getWorkPlace(request.newPlaceId) }?.objectiveId == request.newObjectiveId)
        fill("work-time-start", "1015"); fill("work-time-end", "1845"); chooseColor()
        catalogWriter.loseTemplateOnce = true
        touch("work-template-save"); await { w.creationRecoveryRequired && !w.isSavingTemplate && !w.isCheckingCreation }
        val committedTemplate = checkNotNull(catalogWriter.committedTemplate)
        checked(Stage12SelectionDiagnostics.Checkpoint.CATALOG_RESERVED_ID) { catalogPersistence.checkReserved(committedTemplate, request.newTemplateId) }
        val persistedTemplate = checked(Stage12SelectionDiagnostics.Checkpoint.CATALOG_READ) { catalogPersistence.read { db { own.workCatalog.getWorkTemplate(request.newTemplateId) } } }
        checked(Stage12SelectionDiagnostics.Checkpoint.CATALOG_COMPARE) { catalogPersistence.compare(committedTemplate, persistedTemplate) }
        val committedCatalog = snapshot(); val committedFiles = files()
        check(m.operationId == loadIdentity && m.noteAttachments == loadImages && allWrites().isEmpty())
        recreate(); check(w.creationRequest?.id == request.id && w.creationRecoveryRequired)
        catalogWriter.recoveryBlocked = false
        touch("work-creation-recover")
        await { m.creationRequest == null && m.selectedTemplateId == committedTemplate.id && w.surface == WorkSetupSurface.NONE && !m.isLoading }
        check(snapshot() == committedCatalog && files() == committedFiles) { "SELECTION_CONTEXTUAL_RECOVERY_WROTE" }
        fact("contextual-recovery-bytes", listOf("A039"), "FILES_ISOLATED", JSONObject().put("unchanged_files_sha256", hash(committedFiles.toString())))
        native("CONTEXTUAL_RECOVERY_RETURN_ID", m.selectedTemplateId == request.newTemplateId && m.creationRequest == null)
        check(m.operationId == loadIdentity && m.operationTimestamp == loadTimestamp && m.noteBody == "Own note $run" && m.position == "Own position $run")
        fact("contextual-recovery", listOf("A039", "C052", "C053", "C054"), "ROOM_ISOLATED", JSONObject().put("reserved_template_uuid", request.newTemplateId.toString()).put("recovery_read_only", true))

        // Cancel only the new template draft: its already-confirmed place must survive.
        prepareCatalog("v2-manual-create-place", Stage12CatalogNavigation.Child.CREATE_PLACE); touch("v2-manual-create-place")
        await { w.creationRequest != null && w.surface == WorkSetupSurface.ADDITIONAL_PLACE }
        val partialRequest = checkNotNull(w.creationRequest)
        fill("work-place-name", "Own partial place $run"); fill("work-place-abbreviation", "PAR")
        touch("work-place-continue"); await { w.surface == WorkSetupSurface.ADDITIONAL_TEMPLATE && !w.isSavingWorkSet }
        fill("work-time-start", "1015")
        val contextualDraftBeforeBack = w.templateDraft
        back("CONTEXTUAL_TEMPLATE_DISCARD_DIALOG",
            before = { w.surface == WorkSetupSurface.ADDITIONAL_TEMPLATE && w.creationRequest?.id == partialRequest.id &&
                w.templateDraft == contextualDraftBeforeBack && !w.showDiscardConfirmation && !w.creationBusy &&
                !w.isSavingTemplate && m.operationId == loadIdentity && shown("work-time-start") },
            expectedUi = { w.surface == WorkSetupSurface.ADDITIONAL_TEMPLATE && w.creationRequest?.id == partialRequest.id &&
                w.templateDraft == contextualDraftBeforeBack && m.operationId == loadIdentity &&
                w.showDiscardConfirmation && compose.onNodeWithText("¿Descartar borrador?").isDisplayed() &&
                compose.onNode(hasText("Descartar") and hasClickAction()).isDisplayed() })
        text("Descartar")
        await { w.surface == WorkSetupSurface.NONE && m.creationRequest == null && !m.isLoading }
        check(db { own.workCatalog.getWorkPlace(partialRequest.newPlaceId) } != null &&
            db { own.workCatalog.getWorkTemplate(partialRequest.newTemplateId) } == null && m.selectedTemplateId == committedTemplate.id)
        prepareCatalog("manual-place-" + partialRequest.newPlaceId, Stage12CatalogNavigation.Child.PLACE)
        val placeOrder = listOf(request.newPlaceId, partialRequest.newPlaceId, fixture.place.id).map {
            compose.onNodeWithTag("manual-place-$it").fetchSemanticsNode().boundsInRoot.top
        }
        check(placeOrder.zipWithNext().all { (left, right) -> left < right })
        selectCatalogPlace(partialRequest.newPlaceId, "v2-manual-create-template", Stage12CatalogNavigation.Child.ADD_TEMPLATE)
        ui("EMPTY_PLACE_ADD_TEMPLATE") { compose.onNodeWithText("Esta institución todavía no tiene horarios disponibles.").assertExists(); compose.onNodeWithTag("v2-manual-create-template").assertIsEnabled() }
        touch("v2-manual-create-template"); await { w.surface == WorkSetupSurface.ADDITIONAL_TEMPLATE && w.creationRequest != null }
        val existingPlaceRequest = checkNotNull(w.creationRequest)
        check(existingPlaceRequest.preferredPlaceId == partialRequest.newPlaceId)
        fill("work-time-start", "0715"); fill("work-time-end", "1545"); chooseColor(); touch("work-template-save")
        await { w.surface == WorkSetupSurface.NONE && m.creationRequest == null && !m.isLoading }
        check(db { own.workCatalog.getWorkTemplate(existingPlaceRequest.newTemplateId) }?.workPlaceId == partialRequest.newPlaceId)
        choose(committedTemplate)
        native("CONTEXTUAL_CANCEL_PRESERVES_CONFIRMED_PLACE", m.selectedTemplateId == committedTemplate.id && m.operationId == loadIdentity && m.noteAttachments == loadImages)

        val beforeReselect = snapshot(); val beforeReselectFiles = files()
        back("MANUAL_COMPLETE_DRAFT_TO_DATES",
            before = { m.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE && !m.isLoading && !m.isSaving &&
                m.operationId == loadIdentity && m.operationTimestamp == loadTimestamp && m.selectedDates == initialDates &&
                m.selectedTemplateId == committedTemplate.id && m.position == "Own position $run" &&
                m.noteBody == "Own note $run" && m.noteAttachments == loadImages && w.surface == WorkSetupSurface.NONE },
            expectedUi = { m.stage == V2ManualShiftLoadStage.SELECT_DATES && c.selectionPurpose == CalendarSelectionPurpose.MANUAL_LOAD &&
                c.editSelectedDates == initialDates && m.selectedDates == initialDates && !c.editSelectionConfirmed &&
                m.operationId == loadIdentity && m.operationTimestamp == loadTimestamp && m.selectedTemplateId == committedTemplate.id &&
                m.position == "Own position $run" && m.noteBody == "Own note $run" && m.noteAttachments == loadImages &&
                shown("v2-manual-confirm-dates") && !exists("v2-manual-save-direct") })
        await { m.stage == V2ManualShiftLoadStage.SELECT_DATES && c.editSelectedDates == initialDates }
        initialDates.sorted().forEach { touch(dayTag(it)) }
        await { m.selectedDates.isEmpty() }
        compose.onNodeWithTag("v2-manual-confirm-dates").assertIsNotEnabled()
        recreate()
        ui("EMPTY_RECREATED_SELECTION_DISABLED") { compose.onNodeWithTag("v2-manual-confirm-dates").assertIsNotEnabled() }
        check(m.operationId == loadIdentity && m.selectedDates.isEmpty() && m.selectedTemplateId == committedTemplate.id &&
            m.position == "Own position $run" && m.noteBody == "Own note $run" && m.noteAttachments == loadImages)
        check(snapshot() == beforeReselect && files() == beforeReselectFiles)
        fact("draft-restoration-tables", listOf("C058", "C096-activity-recreate"), "ROOM_ISOLATED", JSONObject().put("unchanged_tables_sha256", hash(beforeReselect.toString())))
        fact("draft-restoration-bytes", listOf("C058", "C096-activity-recreate"), "FILES_ISOLATED", JSONObject().put("unchanged_files_sha256", hash(beforeReselectFiles.toString())).put("image_sha256", image.second))
        native("COMPLETE_DRAFT_RECREATE", m.operationId == loadIdentity && m.selectedDates.isEmpty() && m.noteAttachments == loadImages)
        val finalDates = setOf(day.plusDays(1), day.plusDays(3))
        finalDates.sorted().forEach { touch(dayTag(it)) }; touch("v2-manual-confirm-dates")
        await { m.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE && !m.isLoading }
        check(m.selectedDates == finalDates && m.selectedTemplateId == committedTemplate.id && m.reviewFingerprint == null && m.occupiedPolicy == null)
        check(m.operationId == loadIdentity && m.noteAttachments == loadImages && snapshot() == beforeReselect)
        native("FINAL_DATES_DISPATCH", m.selectedDates == finalDates && c.editSelectedDates == finalDates)
        fact("complete-draft", listOf("C042", "C048", "C049", "C050", "C053", "C055", "C056", "C057", "C058", "C096-activity-recreate"), "ACTIVITY_NATIVE")
        touch("v2-manual-save-direct"); finishSave(2)
        val saved = allWrites()
        check(saved.map { it.shift.localStartDate }.toSet() == finalDates)
        assertSavedCopies(saved, committedTemplate, "Own position $run", "Own note $run", image.second, loadIdentity, loadTimestamp)
        check(!File(dataRoot, "manual_load_drafts/${image.first}").exists())
        fact("final-date-independent-copies", listOf("C052", "C057", "C058", "C059", "C098-archivos-independientes"), "FILES_ISOLATED", JSONObject().put("shift_ids", JSONArray(saved.map { it.shift.id.toString() })).put("source_sha256", image.second))

        isolatedCase("catalog-invalidation")
        beginLoad(setOf(day)); choose(); noteAndPosition(); val draftImage = importImage()
        val op = m.operationId
        touch("v2-manual-modify-dates"); await { m.stage == V2ManualShiftLoadStage.SELECT_DATES }
        val positive = snapshot(); check(m.selectedTemplateId == fixture.day.id)
        db { own.workCatalog.setWorkTemplateActive(fixture.day.id, false, clock.instant()) }
        val changed = snapshot(); check(changed != positive)
        recreate(); touch("v2-manual-confirm-dates")
        await { !m.isLoading && m.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE }
        check(m.selectedTemplateId == null && m.operationId == op && m.noteBody == "Own note $run" && m.position == "Own position $run")
        ui("CATALOG_RESELECTION_BLOCKED") { compose.onNodeWithTag("v2-manual-save-direct").assertIsNotEnabled() }
        native("CATALOG_RESELECT_REQUIRED", m.selectedTemplateId == null && m.noteAttachments.single().storageKey == draftImage.first)
        check(m.noteAttachments.single().storageKey == draftImage.first && snapshot() == changed && allWrites().isEmpty())
        check(hash(File(dataRoot, "manual_load_drafts/${draftImage.first}").readBytes()) == draftImage.second)
        choose(fixture.night); cancelLoad()
        fact("catalog-invalidated", listOf("C097", "C100"), "ROOM_ISOLATED", JSONObject().put("required_explicit_reselection", true))
    }

    private fun policiesAndStates() {
        val past = anchor.minusDays(2)
        val historyBefore = db { checkNotNull(own.workConfiguration.get()) }
        val catalogBefore = db { own.workCatalog.observeCatalog(fixture.place.timelineId, WorkSector.NURSING).first() }
        beginLoad(setOf(past)); choose()
        val beforeBackfill = snapshot(); touch("v2-manual-save-direct"); waitTag("v2-backfill-dialog")
        ui("BACKFILL_DIALOG") { compose.onNodeWithText("Usar esta configuración en fechas anteriores").assertIsDisplayed() }
        cancelDialog(Stage12DialogCancellation.Context.BACKFILL, setOf(past)); check(snapshot() == beforeBackfill && allWrites().isEmpty())
        touch("v2-manual-save-direct"); waitTag("v2-backfill-dialog"); text("Usar desde esa fecha"); finishSave(1)
        val historyAfter = db { checkNotNull(own.workConfiguration.get()) }
        check(historyAfter.timeline.id == historyBefore.timeline.id && historyAfter.timeline.revisions.first().effectiveFrom == past)
        check(historyAfter.timeline.revisions.first().value == historyBefore.timeline.revisions.first().value &&
            historyAfter.perPeriodHoursValues.entries == historyBefore.perPeriodHoursValues.entries)
        val backfilled = allWrites().single()
        check(backfilled.shift.localStartDate == past && backfilled.snapshot.templateId == fixture.day.id)
        check(db { own.workCatalog.observeCatalog(fixture.place.timelineId, WorkSector.NURSING).first() }.workTemplates == catalogBefore.workTemplates)
        native("BACKFILL_CANCEL_CONFIRM", !m.isActive && backfilled.shift.localStartDate == past)
        fact("backfill", listOf("C061"), "ROOM_ISOLATED", JSONObject().put("cancel_wrote_nothing", true).put("effective_from", past.toString()))

        val day = anchor.plusMonths(2).withDayOfMonth(10)
        val original = seededWrite(day)
        val laterTemplate = fixture.day.copy(id = uuid("later-template"), startTime = LocalTime.of(18, 0), endTime = LocalTime.of(22, 0))
        val previousTemplate = fixture.night.copy(id = uuid("previous-night-template"), startTime = LocalTime.of(19, 0), endTime = LocalTime.of(9, 0))
        db { own.workCatalog.createWorkTemplate(laterTemplate); own.workCatalog.createWorkTemplate(previousTemplate) }
        val later = seededWrite(day, laterTemplate)
        val previousNight = seededWrite(day.minusDays(1), previousTemplate)
        db { own.v2Shifts.insert(original); own.v2Shifts.insert(later); own.v2Shifts.insert(previousNight) }
        val originalNote = seedNote(original)
        val originalBytes = hash(File(dataRoot, "schedule_photos/${originalNote.attachments.single().storageKey}").readBytes())
        val stable = snapshot(); val stableFiles = files()
        beginLoad(setOf(day, day.plusDays(2))); choose(); touch("v2-manual-save-direct"); waitTag("v2-occupied-dialog")
        cancelDialog(Stage12DialogCancellation.Context.OCCUPIED, setOf(day, day.plusDays(2)))
        uiOracles += "OCCUPIED_CANCEL_OPTIONS" // Same qualified candidate was measured enabled before its one touch.
        check(snapshot() == stable && files() == stableFiles)
        touch("v2-manual-save-direct"); waitTag("v2-occupied-dialog"); text("Agregar sólo en días libres"); finishSave(5)
        check(allWrites().any { it == original } && allWrites().any { it == later } && allWrites().any { it == previousNight })
        check(notesFor(original.shift.id) == listOf(originalNote) && hash(File(dataRoot, "schedule_photos/${originalNote.attachments.single().storageKey}").readBytes()) == originalBytes)
        check(allWrites().count { it.shift.localStartDate == day.plusDays(2) } == 1)

        // KEEP with no free dates must not manufacture an empty confirmed batch.
        beginLoad(setOf(day)); choose(); touch("v2-manual-save-direct"); waitTag("v2-occupied-dialog")
        compose.onNodeWithText("Agregar sólo en días libres").assertDoesNotExist()
        cancelDialog(Stage12DialogCancellation.Context.OCCUPIED, setOf(day)); cancelLoad()
        beginLoad(setOf(day)); choose(); touch("v2-manual-review"); waitTag("v2-occupied-dialog"); text("Sobrescribir")
        await { m.stage == V2ManualShiftLoadStage.CONFIRM_WARNINGS || m.stage == V2ManualShiftLoadStage.REVIEW }
        // Previous-night overlap is a known warning; declining it cannot mutate SQL/files.
        check(m.stage == V2ManualShiftLoadStage.CONFIRM_WARNINGS && m.warnings.isNotEmpty())
        val beforeWarning = snapshot(); val beforeWarningFiles = files()
        text("Volver y corregir"); check(snapshot() == beforeWarning && files() == beforeWarningFiles)
        touch("v2-manual-review")
        await { m.stage == V2ManualShiftLoadStage.CONFIRM_WARNINGS }
        text("Continuar igualmente"); await { m.stage == V2ManualShiftLoadStage.REVIEW && !m.isLoading }
        check(m.acknowledgedWarnings == m.warnings)
        ui("REVIEW_EXACT_DATE") { compose.onNodeWithTag("v2-manual-preview-dates").assertTextEquals("Fechas: " + day.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu"))) }
        touch("v2-manual-save"); finishSave(4)
        check(db { own.v2Shifts.getShift(original.shift.id) } == V2ShiftLookup.Missing)
        check(db { own.v2Shifts.getShift(previousNight.shift.id) } == V2ShiftLookup.Missing)
        check(db { own.v2Shifts.getShift(later.shift.id) } == V2ShiftLookup.V2(later))
        check(notesFor(original.shift.id).isEmpty() && !File(dataRoot, "schedule_photos/${originalNote.attachments.single().storageKey}").exists())
        val beforeOverlay = allWrites()
        val franco = day.plusDays(5)
        val protection = MedicalLeave(uuid("overlay-medical"), franco, franco, "Own protection", stamp(), stamp())
        db { own.explicitDayStatuses.set(franco, ExplicitDayStatusType.DAY_OFF); own.medicalLeaves.create(protection) }
        beginLoad(setOf(franco)); choose(); touch("v2-manual-save-direct"); waitTag("v2-occupied-dialog"); text("Superponer")
        finishSave(5, warningExpected = true)
        check(allWrites().containsAll(beforeOverlay))
        check(db { own.medicalLeaves.observeIntersecting(franco, franco).first() } == listOf(protection))
        check(db { own.explicitDayStatuses.observeBetween(franco, franco).first().single().type } == ExplicitDayStatusType.DAY_OFF)
        native("KEEP_REPLACE_OVERLAY", !m.isActive && allWrites().containsAll(beforeOverlay))
        fact("occupied-policies", listOf("C059", "C060", "C062", "C063", "C064", "C065"), "ROOM_ISOLATED", JSONObject().put("unselected_nonoverlap_uuid", later.shift.id.toString()).put("warnings_required_and_declined_before_confirmation", true))

        val free = day.plusDays(8)
        generalSelect(setOf(free)); touch("calendar-selection-continue"); text("Marcar Franco")
        await { c.batchPreview != null && !c.batchBusy }
        check(c.batchPreview!!.applicable.map { it.date } == listOf(free) && c.batchPreview!!.conflicts.isEmpty())
        val beforeDayOff = snapshot(); text("Modificar selección"); check(snapshot() == beforeDayOff)
        touch("calendar-selection-continue"); text("Marcar Franco"); await { c.batchPreview != null }; text("Confirmar")
        await { !c.batchBusy && c.batchPreview == null }
        check(db { own.explicitDayStatuses.observeBetween(free, free).first().single().type } == ExplicitDayStatusType.DAY_OFF)
        // Repeating a fully equal batch exposes no enabled confirmation.
        await { c.interactionMode == CalendarInteractionMode.VIEW }
        generalSelect(setOf(free))
        touch("calendar-selection-continue"); text("Marcar Franco"); await { c.batchPreview != null }
        check(c.batchPreview!!.unchanged.map { it.date } == listOf(free) && c.batchPreview!!.applicable.isEmpty())
        ui("UNCHANGED_BATCH_DISABLED") { compose.onNode(hasText("Confirmar") and hasClickAction()).assertIsNotEnabled() }
        text("Modificar selección")
        back("GENERAL_EQUAL_BATCH_EXIT",
            before = { c.interactionMode == CalendarInteractionMode.EDIT && c.selectionPurpose == CalendarSelectionPurpose.GENERAL &&
                c.editSelectedDates == setOf(free) && c.batchPreview == null && !c.batchBusy && shown("calendar-selection-continue") },
            expectedUi = { selectionExitedToGrid(free) })
        val beforeRemovalWrites = allWrites()
        beginDatesOnly(setOf(franco)); openManualSelectionOptions(); touch("v2-remove-day-off"); waitTag("v2-confirm-day-off")
        ui("NO_TEMPLATE_FRANCO_ACTION") { compose.onNodeWithTag("v2-confirm-day-off").assertIsEnabled() }
        val beforeRemove = snapshot(); cancelDialog(Stage12DialogCancellation.Context.REMOVE_DAY_OFF, setOf(franco)); check(snapshot() == beforeRemove)
        openManualSelectionOptions(); touch("v2-remove-day-off"); touch("v2-confirm-day-off"); await { !m.isActive }
        check(db { own.explicitDayStatuses.observeBetween(franco, franco).first().isEmpty() })
        check(allWrites() == beforeRemovalWrites && db { own.medicalLeaves.observeIntersecting(franco, franco).first() } == listOf(protection))
        check(db { own.explicitDayStatuses.observeBetween(free, free).first().single().type } == ExplicitDayStatusType.DAY_OFF)
        val undefinedDate = day.plusDays(9)
        generalSelect(setOf(undefinedDate)); touch("calendar-selection-continue"); text("Marcar Sin definir")
        await { c.batchPreview != null }; text("Confirmar"); await { !c.batchBusy && c.batchPreview == null }
        check(db { own.explicitDayStatuses.observeBetween(undefinedDate, undefinedDate).first().single().type } == ExplicitDayStatusType.UNDEFINED)
        check(allWrites() == beforeRemovalWrites && db { own.explicitDayStatuses.observeBetween(free, free).first().single().type } == ExplicitDayStatusType.DAY_OFF)
        await { c.interactionMode == CalendarInteractionMode.VIEW }
        native("FRANCO_UNDEFINED", !m.isActive && c.interactionMode == CalendarInteractionMode.VIEW)
        fact("explicit-day-states", listOf("C043", "C044", "C046", "C066", "C067"), "ROOM_ISOLATED")

        val availabilityDates = setOf(day.plusDays(10), day.plusDays(12))
        generalSelect(availabilityDates); touch("calendar-selection-continue"); text("Agregar Retén")
        fillLabel("Desde (HH:mm)", "19:00"); fillLabel("Hasta (HH:mm)", "07:00"); text("Revisar")
        await { c.batchPreview != null && !c.batchBusy }
        check(c.batchPreview!!.applicable.map { it.date }.toSet() == availabilityDates && c.batchPreview!!.conflicts.isEmpty())
        ui("AVAILABILITY_REVIEW_COUNTS") { compose.onNodeWithText("Se aplicará: 2").assertIsDisplayed(); compose.onNodeWithText("Conflicto: 0").assertIsDisplayed() }
        text("Confirmar"); await { !c.batchBusy && c.batchPreview == null }
        val windows = db { own.availabilityWindows.observeAll(fixture.place.timelineId, WorkSector.NURSING).first() }
        check(windows.size == 2 && windows.map { it.id }.distinct().size == 2 && windows.map { it.ownerLocalDate }.toSet() == availabilityDates)
        windows.forEach { window -> check(window.start == window.ownerLocalDate.atTime(19, 0).atZone(zone).toInstant() &&
            window.end == window.ownerLocalDate.plusDays(1).atTime(7, 0).atZone(zone).toInstant() && window.labelSnapshot == AvailabilityLabel.ON_CALL_RETAINER.displayName) }
        await { c.interactionMode == CalendarInteractionMode.VIEW }
        native("AVAILABILITY_BATCH", c.interactionMode == CalendarInteractionMode.VIEW && windows.map { it.ownerLocalDate }.toSet() == availabilityDates)
        fact("batch-availability", listOf("C045", "C046"), "ROOM_ISOLATED", JSONObject().put("window_count", 2).put("minutes_each", 720))
    }

    private fun fillLabel(label: String, value: String) {
        val node = compose.onNode(hasText(label) and hasSetTextAction())
        if (checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { !node.isDisplayed() })
            checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL) { node.performScrollTo() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { node.assertIsDisplayed() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { node.assertIsEnabled() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_REPLACE_TEXT) { node.performTextReplacement(value) }
        checked(Stage12SelectionDiagnostics.Checkpoint.HIDE_OWN_IME) {
            checkNotNull(scenario).onActivity { owner -> owner.currentFocus?.clearFocus()
                (owner.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(owner.window.decorView.windowToken, 0) }
        }
    }

    private fun seedNote(write: V2ShiftWrite): ShiftNoteWithAttachments {
        val stamp = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        val note = ShiftNote(uuid("seed-note"), write.shift.id, "Own seeded note", stamp, stamp)
        val bytes = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJZkAAAAASUVORK5CYII=")
        val storageKey = "note_${uuid("seed-image")}.png"
        val file = imageStore.file(storageKey); check(!file.exists()); check(file.parentFile.isDirectory || file.parentFile.mkdirs())
        check(file.createNewFile()); file.writeBytes(bytes)
        val attachment = ShiftNoteAttachment(uuid("seed-attachment"), note.id, storageKey, "image/png", bytes.size.toLong(), 1, 1, 0, stamp, stamp)
        db { own.shiftNotes.saveWithAttachments(note, listOf(attachment)) }
        return ShiftNoteWithAttachments(note, listOf(attachment))
    }

    private fun clearAndCas() {
        val day = anchor.minusMonths(2).withDayOfMonth(10)
        val selected = setOf(day.plusDays(1), day.plusDays(3), day.plusDays(8), day.plusDays(10))
        // Setup happens with no Activity owner; raw fixture rows cannot race an observed UI.
        closeOwner()
        val first = seededWrite(day.plusDays(1))
        val cancelled = seededWrite(day.plusDays(3))
        val previousNight = seededWrite(day, fixture.night)
        db { listOf(first, cancelled, previousNight).forEach { own.v2Shifts.insert(it) } }
        val firstNote = seedNote(first); val cancelledNote = seedNote(cancelled); val previousNote = seedNote(previousNight)
        val evidenceBytes = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJZkAAAAASUVORK5CYII=")
        val evidenceKey = "cancel_${uuid("evidence")}.png"
        val evidenceFile = imageStore.file(evidenceKey)
        check(!evidenceFile.exists()); check(evidenceFile.createNewFile()); evidenceFile.writeBytes(evidenceBytes)
        val stamp = stamp()
        db {
            own.shiftNotificationConfigs.replace(ShiftNotificationConfig(first.shift.id, listOf(17, 41)))
            own.shiftNotificationConfigs.replace(ShiftNotificationConfig(previousNight.shift.id, listOf(23)))
            val situation = ShiftSituation(cancelled.shift.id, ShiftStatus.CANCELLED, "Own cancellation", null,
                CancellationActor.ME, stamp, CancellationEvidence(evidenceKey, "image/png", evidenceBytes.size.toLong(), 1, 1, hash(evidenceBytes)), stamp, stamp)
            own.shiftSituations.apply(ShiftSituationMutation(checkNotNull(own.shiftSituations.inspect(cancelled.shift.id)), situation, false))
        }
        val planId = seedRecurringLink(first)
        db {
            val expected = checkNotNull(own.shiftActuals.getExpectation(first.shift.id))
            val actual = checkNotNull(buildShiftActualSaveMutation(expected,
                ShiftActualDraft(first.shift.startAt, first.shift.endAt.minusSeconds(15 * 60), "Own shorter actual", null, null, null, emptyList()), clock, stamp))
            check(own.shiftActuals.save(actual) is ShiftActualWriteResult.Saved)
        }
        val medical = MedicalLeave(uuid("medical"), day, day.plusDays(6), "Own private medical note", stamp, stamp)
        val vacation = Vacation(uuid("vacation"), day.plusDays(7), day.plusDays(13), stamp, stamp)
        val holiday = Holiday(uuid("holiday"), day.plusDays(1), "Own holiday", stamp, stamp)
        db {
            own.medicalLeaves.create(medical); own.vacations.insert(vacation); own.holidays.insert(holiday)
            own.explicitDayStatuses.set(day.plusDays(1), ExplicitDayStatusType.DAY_OFF)
            own.calendarBatches.apply(own.calendarBatches.preview(CalendarBatchAction.AVAILABILITY,
                setOf(day.plusDays(1), day.plusDays(2)), CalendarBatchAvailability(LocalTime.of(19, 0), LocalTime.of(7, 0), AvailabilityLabel.ON_CALL_RETAINER)))
        }
        val planBefore = db { checkNotNull(own.recurringPlans.getPlan(planId)) }
        val previousNotes = notesFor(previousNight.shift.id)
        val windowsBefore = db { own.availabilityWindows.observeAll(fixture.place.timelineId, WorkSector.NURSING).first() }
        launch()
        beginLoad(selected)
        touch("manual-clear-days"); waitTag("manual-batch-preview")
        val preview = checkNotNull(m.batchPreview)
        check(preview.action == CalendarBatchAction.CLEAR && preview.dates == selected && preview.conflicts.isEmpty() && preview.applicable.size == 4)
        val removedKeys = (firstNote.attachments + cancelledNote.attachments).mapTo(linkedSetOf()) { it.storageKey }
        check(preview.removedNoteAttachmentStorageKeys == removedKeys && preview.removedSituationEvidenceStorageKeys == setOf(evidenceKey))
        check(preview.results.any { "horario(s) real(es)" in it.explanation } && preview.results.any { "tramos exteriores" in it.explanation })
        ui("CLEAR_LOSS_PREVIEW") { compose.onNodeWithTag("manual-batch-preview").assertIsDisplayed(); compose.onNode(hasText("Confirmar") and hasClickAction()).assertIsEnabled() }
        val before = snapshot(); val beforeFiles = files()
        cancelDialog(Stage12DialogCancellation.Context.BATCH_REVIEW, selected); check(snapshot() == before && files() == beforeFiles)
        touch("manual-clear-days"); waitTag("manual-batch-preview"); check(m.batchPreview == preview)
        text("Confirmar"); await { !m.isActive && !m.isSaving }
        val after = snapshot()
        unchangedExcept(before, after, setOf("shifts", "shift_work_snapshots", "medical_leaves", "vacations",
            "explicit_day_statuses", "availability_windows", "shift_actual_records", "shift_situations", "shift_notes",
            "shift_note_attachments", "shift_notification_configs", "shift_notification_reminders", "recurring_occurrences"))
        check(allWrites() == listOf(previousNight) && notesFor(previousNight.shift.id) == previousNotes)
        check(notesFor(first.shift.id).isEmpty() && notesFor(cancelled.shift.id).isEmpty())
        val removedPaths = (removedKeys + evidenceKey).mapTo(linkedSetOf()) { privateRelative(File(dataRoot, "schedule_photos/$it")) }
        check(files() == beforeFiles.filterKeys { it !in removedPaths }) { "SELECTION_CLEAR_BYTES_OR_NEIGHBOUR_CHANGED" }
        val medicalAfter = db { own.medicalLeaves.observeIntersecting(day, day.plusDays(20)).first().sortedBy { it.startDate } }
        check(medicalAfter.map { it.startDate to it.endDateInclusive } == listOf(day to day, day.plusDays(2) to day.plusDays(2), day.plusDays(4) to day.plusDays(6)))
        check(medicalAfter.all { it.privateNote == medical.privateNote && it.createdAt == medical.createdAt })
        val vacationAfter = db { own.vacations.observeOverlapping(day, day.plusDays(20)).first().sortedBy { it.startDate } }
        check(vacationAfter.map { it.startDate to it.endDateInclusive } == listOf(day.plusDays(7) to day.plusDays(7), day.plusDays(9) to day.plusDays(9), day.plusDays(11) to day.plusDays(13)))
        check(vacationAfter.all { it.createdAt == vacation.createdAt })
        val statuses = db { own.explicitDayStatuses.observeBetween(day, day.plusDays(20)).first() }
        check(statuses.map { it.date }.toSet() == selected && statuses.all { it.type == ExplicitDayStatusType.UNDEFINED })
        check(db { own.availabilityWindows.observeAll(fixture.place.timelineId, WorkSector.NURSING).first() } == windowsBefore.filter { it.ownerLocalDate !in selected })
        val planAfter = db { checkNotNull(own.recurringPlans.getPlan(planId)) }
        check(planAfter.plan == planBefore.plan && planAfter.revisions == planBefore.revisions && planAfter.occurrences.single().state == RecurringOccurrenceState.EXCLUDED && planAfter.occurrences.single().shiftId == null)
        check(db { own.shiftActuals.getExpectation(first.shift.id) } == null)
        native("CLEAR_CANCEL_CONFIRM", !m.isActive && allWrites() == listOf(previousNight))
        fact("mixed-clear", listOf("C068", "C099", "C100"), "FILES_ISOLATED", JSONObject().put("cleared_dates", JSONArray(selected.sorted().map(LocalDate::toString))).put("previous_overnight_preserved", true).put("range_counts", JSONArray(listOf(3, 3))))

        // Each adversary starts with a fresh positive fixture and files subroot.
        for (kind in listOf("source", "associated", "range", "catalog", "configuration")) {
            clearCasObservation.beginCase(when (kind) {
                "source" -> Stage12ClearCasObservation.Adversary.SOURCE
                "associated" -> Stage12ClearCasObservation.Adversary.ASSOCIATED
                "range" -> Stage12ClearCasObservation.Adversary.RANGE
                "catalog" -> Stage12ClearCasObservation.Adversary.CATALOG
                "configuration" -> Stage12ClearCasObservation.Adversary.CONFIGURATION
                else -> error("STAGE12_CAS_UNKNOWN_ADVERSARY")
            })
            isolatedCase(kind)
            val date = anchor.plusMonths(2).withDayOfMonth(10)
            val original = seededWrite(date)
            db { own.v2Shifts.insert(original) }
            val originalNote = seedNote(original)
            val causalRange = if (kind == "range") MedicalLeave(uuid("cas-medical"), date, date.plusDays(4), "Own range baseline", stamp(), stamp()).also {
                db { own.medicalLeaves.create(it) }
            } else null
            beginLoad(setOf(date, date.plusDays(2))); choose()
            if (kind == "range") {
                touch("manual-clear-days"); waitTag("manual-batch-preview")
                check(m.batchPreview!!.conflicts.isEmpty() && m.batchPreview!!.applicable.size == 2)
                compose.onNode(hasText("Confirmar") and hasClickAction()).assertIsEnabled()
            } else {
                touch("v2-manual-review"); waitTag("v2-occupied-dialog"); text("Sobrescribir")
                awaitCasBoundary(Stage12ClearCasObservation.Boundary.WARNINGS)
                text("Continuar igualmente"); awaitCasBoundary(Stage12ClearCasObservation.Boundary.REVIEW)
                compose.onNodeWithTag("v2-manual-save").assertIsEnabled()
                check(m.reviewFingerprint != null)
            }
            ui("CAS_POSITIVE_REVIEW_ENABLED") {
                if (kind == "range") compose.onNode(hasText("Confirmar") and hasClickAction()).assertIsEnabled()
                else compose.onNodeWithTag("v2-manual-save").assertIsEnabled()
            }
            val clean = snapshot(); val originalFiles = files(); val op = m.operationId
            val associatedExpectation = if (kind == "associated") {
                check(original.shift.startAt.atZone(zone).toLocalDate() == date && original.shift.endAt.atZone(zone).toLocalDate() == date &&
                    original.shift.startAt.atZone(zone).toLocalTime() == LocalTime.of(8, 15) && original.shift.endAt.atZone(zone).toLocalTime() == LocalTime.of(16, 45))
                val prefix = "${date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu"))} 08:15–16:45: se quitará esta jornada"
                val previousWarnings = listOf("$prefix, 1 notas, 1 imágenes y 0 avisos.")
                check(m.warnings == previousWarnings && m.acknowledgedWarnings == previousWarnings)
                check(notesFor(original.shift.id) == listOf(originalNote))
                AssociatedCasExpectation(m, listOf("$prefix, 2 notas, 1 imágenes y 0 avisos."))
            } else null
            var concurrentNote: ShiftNote? = null
            val afterWriter = checked(Stage12SelectionDiagnostics.Checkpoint.CLEAR_CAS_REJECTION) {
                clearCasObservation.observe(Stage12ClearCasObservation.Boundary.REJECTION) { window ->
                    window.mutate {
                        when (kind) {
                            "source" -> db {
                                val changed = original.copy(shift = original.shift.copy(position = "Own concurrent position", updatedAt = stamp()))
                                val all = allWrites().map { it.shift }
                                own.v2Shifts.applyV2Batch(V2ShiftBatchMutation(shiftsToUpdate = listOf(changed)),
                                    ShiftOccupancyExpectation.capture(date.minusDays(2), date.plusDays(2), all), V2ShiftWriteExpectation.capture(listOf(original)))
                            }
                            "associated" -> db {
                                val added = ShiftNote(uuid("concurrent-note"), original.shift.id, "Own concurrent note", stamp(), stamp())
                                own.shiftNotes.insert(added); concurrentNote = added
                            }
                            "range" -> db { val previous = checkNotNull(causalRange); own.medicalLeaves.updateExpected(previous,
                                previous.copy(privateNote = "Own concurrent range note", updatedAt = stamp())) }
                            "catalog" -> db { own.workCatalog.updateWorkTemplate(WorkTemplateUpdate(fixture.day,
                                fixture.day.copy(colorArgb = 0xFF009688.toInt(), updatedAt = stamp()))) }
                            "configuration" -> db { own.workConfiguration.addRevision(fixture.place.timelineId,
                                EffectiveRevision(uuid("concurrent-config"), date, WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, AvailabilityLabel.PASSIVE_GUARD))) }
                        }
                        Unit
                    }
                    val after = snapshot(); check(after != clean && files() == originalFiles)
                    window.input { if (kind == "range") text("Confirmar") else touch("v2-manual-save") }
                    awaitCasWindow(window, associatedExpectation)
                    after
                }
            }
            check(m.isActive && m.operationId == op && snapshot() == afterWriter && files() == originalFiles) { "SELECTION_CAS_PARTIAL_WRITE" }
            check(allWrites().size == 1 && m.batchPreview == null && m.stage != V2ManualShiftLoadStage.REVIEW)
            ui("CAS_REJECT_REVIEW_REQUIRED") { compose.onNodeWithTag("v2-manual-save").assertDoesNotExist() }
            if (associatedExpectation != null) {
                check(db { own.v2Shifts.getShift(original.shift.id) } == V2ShiftLookup.V2(original))
                check(notesFor(original.shift.id).toSet() == setOf(originalNote, ShiftNoteWithAttachments(checkNotNull(concurrentNote), emptyList())))
                ui("CAS_REJECT_REVIEW_REQUIRED") {
                    val root = hasTestTag("v2-warning-dialog")
                    compose.onAllNodes(root).assertCountEquals(1)
                    val warning = hasText("• ${associatedExpectation.warnings.single()}", substring = false, ignoreCase = false) and hasAnyAncestor(root)
                    compose.onAllNodes(warning).assertCountEquals(1)
                    compose.onNode(warning).assertIsDisplayed()
                }
                cancelDialog(Stage12DialogCancellation.Context.WARNINGS, setOf(date, date.plusDays(2)))
                check(snapshot() == afterWriter && files() == originalFiles)
            }
            native("CAS_${kind.uppercase()}", m.isActive && m.operationId == op && m.stage != V2ManualShiftLoadStage.REVIEW)
            // A fresh review is an explicit action; no stale dialog is reused.
            if (kind == "range") {
                touch("manual-clear-days"); waitTag("manual-batch-preview"); check(m.batchPreview!!.conflicts.isEmpty())
                cancelDialog(Stage12DialogCancellation.Context.BATCH_REVIEW, setOf(date, date.plusDays(2)))
            } else {
                choose(); touch("v2-manual-review"); waitTag("v2-occupied-dialog"); cancelDialog(Stage12DialogCancellation.Context.OCCUPIED, setOf(date, date.plusDays(2)))
            }
            cancelLoad()
            check(snapshot() == afterWriter && files() == originalFiles)
            fact("cas-$kind", listOf("C047", "C097", "C099"), "ROOM_ISOLATED", JSONObject().put("fresh_isolated_case", true).put("positive_preview_before_mutation", true).put("no_partial_write", true))
        }
    }

    /** Raw SETUP ONLY for a valid one-date automatic occurrence; no tested operation uses this connection. */
    private fun seedRecurringLink(write: V2ShiftWrite): UUID {
        val plan = uuid("seed-plan"); val revision = uuid("seed-plan-revision"); val now = stamp().toEpochMilli()
        val connection = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        try {
            connection.setForeignKeyConstraintsEnabled(true); connection.beginTransaction()
            fun insert(table: String, values: Map<String, Any?>) {
                val row = android.content.ContentValues()
                values.forEach { (name, value) -> when (value) {
                    null -> row.putNull(name)
                    is String -> row.put(name, value)
                    is Int -> row.put(name, value)
                    is Long -> row.put(name, value)
                    else -> error("SELECTION_SETUP_VALUE_TYPE")
                } }
                check(connection.insertOrThrow(table, null, row) != -1L)
            }
            insert("recurring_plans", mapOf("id" to plan.toString(), "timelineId" to fixture.place.timelineId.toString(), "sector" to WorkSector.NURSING.name, "createdAtEpochMillis" to now))
            insert("recurring_plan_revisions", mapOf("id" to revision.toString(), "planId" to plan.toString(), "revisionNumber" to 1,
                "effectiveFrom" to write.shift.localStartDate.toString(), "kind" to "ACTIVE", "endDateInclusive" to write.shift.localStartDate.toString(),
                "patternKind" to "EVERY_N_DAYS", "weekdaysMask" to null, "intervalCount" to 1, "monthlyOrdinal" to null, "monthlyDayOfWeek" to null,
                "templateId" to fixture.day.id.toString(), "workPlaceId" to fixture.place.id.toString(), "objectiveId" to fixture.objective.id.toString(),
                "workTypeId" to fixture.type.id.toString(), "objectiveNameSnapshot" to fixture.objective.fullName,
                "objectiveAbbreviationSnapshot" to fixture.objective.abbreviation, "objectiveAddressSnapshot" to fixture.objective.address,
                "workTypeNameSnapshot" to fixture.type.name, "workTypeBehaviorSnapshot" to fixture.type.behavior.name,
                "startTimeSnapshot" to fixture.day.startTime.toString(), "endTimeSnapshot" to fixture.day.endTime.toString(),
                "colorArgbSnapshot" to fixture.day.colorArgb, "positionSnapshot" to write.shift.position, "zoneId" to zone.id, "createdAtEpochMillis" to now))
            insert("recurring_occurrences", mapOf("planId" to plan.toString(), "localDate" to write.shift.localStartDate.toString(),
                "revisionId" to revision.toString(), "shiftId" to write.shift.id.toString(), "state" to "AUTOMATIC", "createdAtEpochMillis" to now, "updatedAtEpochMillis" to now))
            connection.setTransactionSuccessful()
        } finally { if (connection.inTransaction()) connection.endTransaction(); connection.close() }
        check(db { own.recurringPlans.getPlan(plan) }?.occurrences?.single()?.shiftId == write.shift.id)
        return plan
    }

    private fun privateRelative(file: File) = privateRoot.toPath().relativize(file.toPath()).toString()
    private fun stamp() = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)

    private fun doubleTouchAndRecovery() {
        val day = anchor.plusMonths(2).withDayOfMonth(10)
        val dates = setOf(day, day.plusDays(2))
        beginLoad(dates); choose(); noteAndPosition(); val image = importImage()
        val operation = checkNotNull(m.operationId); val timestamp = checkNotNull(m.operationTimestamp)
        val before = snapshot()
        val gate = CompletableDeferred<Unit>(); batchWriter.hold = gate
        val button = compose.onNodeWithTag("v2-manual-save-direct"); if (!button.isDisplayed()) button.performScrollTo()
        // Two physical input events while the first write is suspended before its real transaction.
        button.assertIsEnabled().performTouchInput { click(); advanceEventTime(70); click() }
        await { batchWriter.entered.isCompleted && m.stage == V2ManualShiftLoadStage.REVIEW && m.isSaving && !m.isLoading }
        ui("TWO_PHYSICAL_TAPS_DISABLED_DURING_WRITE") {
            compose.onNodeWithTag("v2-manual-save-direct").assertDoesNotExist()
            compose.onNodeWithTag("v2-manual-save").assertExists().assertIsNotEnabled()
        }
        check(snapshot() == before && allWrites().isEmpty())
        gate.complete(Unit); finishSave(2)
        val pair = allWrites(); assertSavedCopies(pair, fixture.day, "Own position $run", "Own note $run", image.second, operation, timestamp)
        val first = pair.first(); val survivor = pair.last()
        val survivorNotes = notesFor(survivor.shift.id)
        val survivorBytes = survivorNotes.single().attachments.associate { it.storageKey to hash(File(dataRoot, "schedule_photos/${it.storageKey}").readBytes()) }
        val targetNote = notesFor(first.shift.id).single()
        val targetKeys = targetNote.attachments.mapTo(linkedSetOf()) { it.storageKey }
        native("TWO_PHYSICAL_TAPS_ONE_COMMIT", !m.isActive && pair.size == 2)
        fact("double-touch", listOf("C098-doble-toque"), "ROOM_ISOLATED", JSONObject().put("exact_shift_count", 2).put("operation_uuid", operation.toString()))

        val lostDates = setOf(day.plusDays(5), day.plusDays(7))
        beginLoad(lostDates); choose(fixture.night); noteAndPosition(); val lostImage = importImage()
        val lostOperation = checkNotNull(m.operationId); val lostTimestamp = checkNotNull(m.operationTimestamp)
        touch("v2-manual-review"); await { m.stage == V2ManualShiftLoadStage.REVIEW && !m.isLoading }
        ui("OVERNIGHT_PREVIEW_EXACT_ENDPOINTS") {
            val dates = lostDates.sorted().joinToString(", ") { it.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")) }
            val dateNode = compose.onNodeWithTag("v2-manual-preview-dates")
            if (!dateNode.isDisplayed()) dateNode.performScrollTo()
            dateNode.assertTextEquals("Fechas: $dates").assertIsDisplayed()
            val timeNode = compose.onNodeWithText("Horario: 22:15–06:45")
            if (!timeNode.isDisplayed()) timeNode.performScrollTo()
            timeNode.assertIsDisplayed()
            val nextDay = compose.onNodeWithTag("v2-manual-next-day")
            if (!nextDay.isDisplayed()) nextDay.performScrollTo()
            nextDay.assertTextEquals("Termina al día siguiente").assertIsDisplayed()
            compose.onNodeWithTag("v2-manual-save").assertIsEnabled()
        }
        check(m.plannedDates.toSet() == lostDates && m.selectedTemplateId == fixture.night.id)
        batchWriter.hold = null; batchWriter.loseResponseOnce = true
        touch("v2-manual-save")
        await { batchWriter.committed != null && !m.isSaving && m.errorMessage != null }
        check(m.operationId == lostOperation && m.attemptedDates == lostDates)
        val committed = allWrites().filter { it.shift.localStartDate in lostDates }
        check(committed.size == 2)
        assertSavedCopies(committed, fixture.night, "Own position $run", "Own note $run", lostImage.second, lostOperation, lostTimestamp)
        val afterCommit = snapshot(); val afterFiles = files()
        recreate(); check(m.operationId == lostOperation && m.attemptedDates == lostDates)
        ui("POSTCOMMIT_ERROR_RETRY_VISIBLE") { compose.onNode(hasText("Reintentar") and hasClickAction()).assertIsEnabled() }
        // Product's explicit retry/review runs recoverConfirmedLoad against the real committed rows.
        text("Reintentar")
        await { !m.isActive }
        check(snapshot() == afterCommit) { "SELECTION_RECOVERY_DUPLICATED_SQL" }
        val afterRecoveryFiles = files()
        val draftKey = privateRoot.toPath().relativize(File(dataRoot, "manual_load_drafts/${lostImage.first}").toPath()).toString()
        check(afterRecoveryFiles == afterFiles - draftKey) { "SELECTION_RECOVERY_CHANGED_COPIES" }
        check(allWrites().size == 4)
        native("POSTCOMMIT_EXPLICIT_RECOVERY_RECREATE", !m.isActive && allWrites().filter { it.shift.localStartDate in lostDates } == committed)
        fact("postcommit-recovery", listOf("C098-respuesta-perdida"), "ROOM_ISOLATED", JSONObject().put("operation_uuid", lostOperation.toString()).put("committed_uuid_set_preserved", true))

        val expectedActual = checked(Stage12SelectionDiagnostics.Checkpoint.RECOVERY_DELETE_EXPECTATIONS) {
            check(db { own.v2Shifts.getShift(first.shift.id) } == V2ShiftLookup.V2(first))
            checkNotNull(db { own.shiftActuals.getExpectation(first.shift.id) }).also {
                check(it.planned == first && it.previousActual == null && it.observedClass == null && it.recurringOccurrence == null)
            }
        }
        // Derive the transition from the already verified postcommit snapshot, never a refreshed baseline.
        val deletedRows = mapOf("shifts" to ("id" to first.shift.id), "shift_work_snapshots" to ("shiftId" to first.shift.id),
            "shift_notes" to ("id" to targetNote.note.id), "shift_note_attachments" to ("noteId" to targetNote.note.id))
        val capturedTargetRows = mapOf(
            "shift_notes" to targetNote.note.let { mapOf("id" to "S:${it.id}", "shiftId" to "S:${it.shiftId}", "body" to "S:${it.body}",
                "createdAtEpochMillis" to "I:${it.createdAt.toEpochMilli()}", "updatedAtEpochMillis" to "I:${it.updatedAt.toEpochMilli()}") },
            "shift_note_attachments" to targetNote.attachments.single().let { mapOf("id" to "S:${it.id}", "noteId" to "S:${it.noteId}",
                "storageKey" to "S:${it.storageKey}", "mimeType" to "S:${it.mimeType}", "byteSize" to "I:${it.byteSize}",
                "pixelWidth" to "I:${it.pixelWidth}", "pixelHeight" to "I:${it.pixelHeight}", "position" to "I:${it.position}",
                "createdAtEpochMillis" to "I:${it.createdAt.toEpochMilli()}", "updatedAtEpochMillis" to "I:${it.updatedAt.toEpochMilli()}") })
        val expectedTables = afterCommit.mapValues { (name, table) ->
            deletedRows[name]?.let { (column, id) ->
                val index = table.columns.indexOf(column); check(index >= 0)
                val removed = table.rows.filter { it[index] == "S:$id" }
                check(removed.size == 1)
                capturedTargetRows[name]?.let { expected ->
                    check(table.columns.toSet() == expected.keys && removed.single() == table.columns.map { expected.getValue(it) })
                }
                table.copy(rows = table.rows - removed.toSet())
            } ?: table
        }
        val targetPaths = targetKeys.mapTo(linkedSetOf()) { privateRelative(File(dataRoot, "schedule_photos/$it")) }
        check(targetPaths.size == 1 && targetPaths.all { afterRecoveryFiles[it] == image.second })
        val expectedFiles = afterRecoveryFiles - targetPaths
        // Repository deletion fixture action is declared; no claim of native C090/delete navigation.
        checked(Stage12SelectionDiagnostics.Checkpoint.RECOVERY_DELETE_SQL) {
            db { own.v2Shifts.deleteShift(first, expectedActual) }
        }
        checked(Stage12SelectionDiagnostics.Checkpoint.RECOVERY_DELETE_FILES) {
            db { notes.cleanRemoved(targetKeys) }
        }
        checked(Stage12SelectionDiagnostics.Checkpoint.RECOVERY_DELETE_COMPARE) {
            check(snapshot() == expectedTables && files() == expectedFiles) { "SELECTION_COPY_DELETE_CHANGED_UNRELATED_DATA" }
            check(db { own.v2Shifts.getShift(first.shift.id) } == V2ShiftLookup.Missing && notesFor(first.shift.id).isEmpty())
            check(db { own.shiftActuals.getExpectation(first.shift.id) } == null)
            check(db { own.v2Shifts.getShift(survivor.shift.id) } == V2ShiftLookup.V2(survivor) && notesFor(survivor.shift.id) == survivorNotes)
            check(survivorBytes.all { (key, digest) -> hash(File(dataRoot, "schedule_photos/$key").readBytes()) == digest })
            check(targetNote.attachments.none { File(dataRoot, "schedule_photos/${it.storageKey}").exists() })
            check(allWrites().filter { it.shift.localStartDate in lostDates } == committed)
            assertSavedCopies(committed, fixture.night, "Own position $run", "Own note $run", lostImage.second, lostOperation, lostTimestamp)
        }
        fact("independent-copy-delete", listOf("C098-archivos-independientes"), "FILES_ISOLATED", JSONObject().put("repository_action_not_native_delete", true).put("survivor_uuid", survivor.shift.id.toString()))
    }

    private fun assertSavedCopies(writes: List<V2ShiftWrite>, template: WorkTemplate, position: String, note: String,
        expectedImageHash: String, operation: UUID, timestamp: Instant) {
        check(writes.size == 2 && writes.map { it.shift.id }.distinct().size == 2)
        val aggregates = writes.map { write ->
            val date = write.shift.localStartDate
            val expectedId = UUID.nameUUIDFromBytes("$operation:shift:$date".toByteArray(Charsets.UTF_8))
            val endDate = if (template.endTime > template.startTime) date else date.plusDays(1)
            check(write.shift.id == expectedId && write.shift.createdAt == timestamp && write.shift.position == position &&
                write.snapshot.templateId == template.id && write.shift.zoneId == zone &&
                write.shift.startAt == date.atTime(template.startTime).atZone(zone).toInstant() &&
                write.shift.endAt == endDate.atTime(template.endTime).atZone(zone).toInstant()) { "SELECTION_SAVED_PAIR_DIFFERS" }
            notesFor(write.shift.id).single().also { aggregate ->
                check(aggregate.note.shiftId == write.shift.id && aggregate.note.body == note && aggregate.attachments.size == 1)
                check(aggregate.attachments.single().noteId == aggregate.note.id)
                check(hash(File(dataRoot, "schedule_photos/${aggregate.attachments.single().storageKey}").readBytes()) == expectedImageHash)
            }
        }
        check(aggregates.map { it.note.id }.distinct().size == 2 && aggregates.map { it.attachments.single().storageKey }.distinct().size == 2)
    }

    private fun generalSelect(dates: Set<LocalDate>) {
        goMonth(YearMonth.from(dates.first())); drawer("drawer-action-calendar-select")
        await { c.selectionPurpose == CalendarSelectionPurpose.GENERAL && c.editSelectedDates.isEmpty() }
        dates.sorted().forEach { touch(dayTag(it)) }; check(c.editSelectedDates == dates)
    }
    private fun chooseColor() { touch("work-template-color"); touch("common-color-azul"); text("Usar color") }
    private fun <T> checked(checkpoint: Stage12SelectionDiagnostics.Checkpoint, body: () -> T): T = diagnostic.checked(checkpoint, body)

    private fun touch(tag: String, beforeTouch: () -> Unit = {}) {
        action++; lastActionHash = hash(tag)
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) { waitTag(tag) }
        val node = compose.onNodeWithTag(tag)
        if (checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { !node.isDisplayed() })
            checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL) { node.performScrollTo() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { node.assertIsDisplayed() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { node.assertIsEnabled() }
        beforeTouch()
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) { node.performTouchInput { click() } }
    }
    private fun cancelScope(context: Stage12DialogCancellation.Context): SemanticsMatcher = when (context) {
        Stage12DialogCancellation.Context.BACKFILL -> hasTestTag("v2-backfill-dialog")
        Stage12DialogCancellation.Context.OCCUPIED -> hasTestTag("v2-occupied-dialog")
        Stage12DialogCancellation.Context.REMOVE_DAY_OFF -> isDialog() and hasAnyDescendant(hasTestTag("v2-confirm-day-off"))
        Stage12DialogCancellation.Context.BATCH_REVIEW -> hasTestTag("manual-batch-preview")
        Stage12DialogCancellation.Context.WARNINGS -> hasTestTag("v2-warning-dialog")
    }
    private fun cancelDialog(context: Stage12DialogCancellation.Context, expectedDates: Set<LocalDate>) {
        val caption = if (context == Stage12DialogCancellation.Context.WARNINGS) "Volver y corregir" else "Cancelar"
        action++; lastActionHash = hash(caption)
        val root = cancelScope(context)
        val selector = hasText(caption, substring = false, ignoreCase = false) and hasClickAction() and hasAnyAncestor(root)
        val node = compose.onNode(selector)
        val before = m
        val calendarDates = c.editSelectedDates
        val calendarPurpose = c.selectionPurpose
        val calendarMode = c.interactionMode
        val calendarConfirmed = c.editSelectionConfirmed
        checked(Stage12SelectionDiagnostics.Checkpoint.DIALOG_CANCEL) {
            dialogCancellation.cancel(context,
                compatible = {
                    requireQaReady()
                    expectedDates.isNotEmpty() && m.selectedDates == expectedDates && m.isActive && !m.isLoading && !m.isSaving &&
                        m.creationRequest == null && when (context) {
                            Stage12DialogCancellation.Context.BACKFILL -> m.stage == V2ManualShiftLoadStage.CONFIRM_BACKFILL && m.backfillFrom != null
                            Stage12DialogCancellation.Context.OCCUPIED -> m.stage == V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY
                            Stage12DialogCancellation.Context.REMOVE_DAY_OFF -> m.pendingDayOff == false && m.stage == V2ManualShiftLoadStage.SELECT_DATES
                            Stage12DialogCancellation.Context.BATCH_REVIEW -> m.batchPreview != null
                            Stage12DialogCancellation.Context.WARNINGS -> m.stage == V2ManualShiftLoadStage.CONFIRM_WARNINGS &&
                                m.pendingDayOff == null && m.batchPreview == null && m.warnings.isNotEmpty() && m.warnings != m.acknowledgedWarnings
                        }
                },
                scopeCount = { compose.onAllNodes(root).fetchSemanticsNodes().size },
                candidateCount = { compose.onAllNodes(selector).fetchSemanticsNodes().size },
                visibleQuery = { checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { node.isDisplayed() } },
                scroll = { checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL) { node.performScrollTo() }; Unit },
                visible = { checked(Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { node.isDisplayed() } },
                enabled = { checked(Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { !node.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) } },
                interact = { checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) { node.performTouchInput { click() } }; Unit },
                postcondition = {
                    val dismissed = when (context) {
                        Stage12DialogCancellation.Context.BACKFILL -> m.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE && m.backfillFrom == null
                        Stage12DialogCancellation.Context.OCCUPIED -> m.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE && m.occupiedPolicy == null
                        Stage12DialogCancellation.Context.REMOVE_DAY_OFF -> m.pendingDayOff == null && m.stage == before.stage && !exists("v2-confirm-day-off")
                        Stage12DialogCancellation.Context.BATCH_REVIEW -> m.batchPreview == null && m.stage == before.stage
                        Stage12DialogCancellation.Context.WARNINGS -> m.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE &&
                            m.warnings.isEmpty() && m.acknowledgedWarnings.isEmpty() && m.reviewFingerprint == null &&
                            !m.saveAfterPreparation && m.errorMessage == null
                    }
                    dismissed && compose.onAllNodes(root).fetchSemanticsNodes().isEmpty() && !m.isLoading && !m.isSaving &&
                        m.selectedDates == expectedDates && m.operationId == before.operationId && m.operationTimestamp == before.operationTimestamp &&
                        m.selectedTemplateId == before.selectedTemplateId && m.noteBody == before.noteBody && m.noteAttachments == before.noteAttachments &&
                        m.position == before.position && c.editSelectedDates == calendarDates && c.selectionPurpose == calendarPurpose &&
                        c.interactionMode == calendarMode && c.editSelectionConfirmed == calendarConfirmed
                })
        }
    }
    private fun text(value: String) {
        action++; lastActionHash = hash(value)
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) { await { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
        val node = compose.onNode(hasText(value) and hasClickAction())
        if (checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { !node.isDisplayed() })
            checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL) { node.performScrollTo() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { node.assertIsDisplayed() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { node.assertIsEnabled() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SINGLE_TOUCH) { node.performTouchInput { click() } }
    }
    private fun fill(tag: String, value: String) {
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_PRESENCE) { waitTag(tag) }
        val node = compose.onNodeWithTag(tag)
        if (checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL_QUERY) { !node.isDisplayed() })
            checked(Stage12SelectionDiagnostics.Checkpoint.NODE_SCROLL) { node.performScrollTo() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_VISIBLE) { node.assertIsDisplayed() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_ENABLED) { node.assertIsEnabled() }
        checked(Stage12SelectionDiagnostics.Checkpoint.NODE_REPLACE_TEXT) { node.performTextReplacement(value) }
        checked(Stage12SelectionDiagnostics.Checkpoint.HIDE_OWN_IME) {
            checkNotNull(scenario).onActivity { owner -> owner.currentFocus?.clearFocus()
                (owner.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(owner.window.decorView.windowToken, 0) }
        }
    }

    private fun togglePositionEditor(expandedBefore: Boolean, expectedPosition: String) {
        val before = if (expandedBefore) "Editor desplegado" else "Editor contraído"
        val after = if (expandedBefore) "Editor contraído" else "Editor desplegado"
        touch("manual-toggle-position", beforeTouch = {
            checked(Stage12SelectionDiagnostics.Checkpoint.POSITION_EDITOR_BEFORE) {
                compose.onNodeWithTag("manual-toggle-position").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, before))
            }
            checked(Stage12SelectionDiagnostics.Checkpoint.POSITION_VALUE_BEFORE) { check(m.position == expectedPosition) }
        })
        checked(Stage12SelectionDiagnostics.Checkpoint.POSITION_EDITOR_AFTER_WAIT) {
            await { compose.onNodeWithTag("manual-toggle-position").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == after }
        }
        checked(Stage12SelectionDiagnostics.Checkpoint.POSITION_VALUE_AFTER) { check(m.position == expectedPosition) }
        if (expandedBefore) {
            checked(Stage12SelectionDiagnostics.Checkpoint.POSITION_SUMMARY_PRESENT) { compose.onNodeWithTag("manual-position-summary").assertExists() }
            checked(Stage12SelectionDiagnostics.Checkpoint.POSITION_SUMMARY_CONTENT) { compose.onNodeWithTag("manual-position-summary").assertTextEquals("Puesto o función: $expectedPosition") }
        } else checked(Stage12SelectionDiagnostics.Checkpoint.POSITION_FIELD_PRESENT) { compose.onNodeWithTag("v2-manual-position").assertExists() }
    }
    private fun selectionExitedToGrid(date: LocalDate): Boolean = c.interactionMode == CalendarInteractionMode.VIEW &&
        c.visibleMonth == YearMonth.from(date) && c.loadState == CalendarLoadState.CONTENT && c.editSelectedDates.isEmpty() &&
        shown("month-grid") && !exists("calendar-primary-actions") && !exists("calendar-selection-continue")

    private fun shown(tag: String): Boolean = compose.onNodeWithTag(tag).isDisplayed()

    private fun back(target: String, before: () -> Boolean, expectedUi: () -> Boolean?) {
        val observed = Stage12OwnedBackEventSemantics.Observation()
        backEvents += target to observed
        action++; lastActionHash = hash("BACK_PRECONDITIONS:$target")
        Stage12OwnedBackEventSemantics.execute(observed, before, expectedUi,
            nativeCall = {
                action++; lastActionHash = hash("NATIVE_BACK:$target")
                device.pressBack()
            }, awaitUi = { condition ->
                action++; lastActionHash = hash("WAIT_BACK_UI:$target")
                await(condition)
            })
    }

    private fun backEventsForReceipt(): JSONArray = JSONArray(backEvents.map { (target, observed) ->
        JSONObject().put("boundary", target).put("phase", observed.phase).put("api_return_available", observed.returnAvailable).apply {
            observed.apiReturn?.let { put("api_return_boolean", it) }
            observed.confirmationBefore?.let { put("confirmation_before", it) }
            observed.confirmationUi?.let { put("confirmation_ui", it) }
            observed.escapedErrorClass?.let { put("escaped_error_class", it) }
            observed.failureCode?.let { put("failure_code", it) }
        }
    })
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String) = await { exists(tag) }
    private fun requireQaReady() = Stage12SelectionQaGuard.requireReady(
        screenOn = { device.isScreenOn },
        qaForeground = { device.currentPackageName == QA },
        keyguardFree = { !(realQa.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked },
        sameCivilDay = { LocalDate.now(clock) == anchor },
    )
    private fun await(condition: () -> Boolean) = await(condition, beforeCasGuard = null)
    private fun await(condition: () -> Boolean, beforeCasGuard: (() -> Unit)?) = compose.waitUntil(30_000) {
        beforeCasGuard?.invoke(); requireQaReady(); condition()
    }
    private fun awaitCasBoundary(boundary: Stage12ClearCasObservation.Boundary) {
        val checkpoint = when (boundary) {
            Stage12ClearCasObservation.Boundary.WARNINGS -> Stage12SelectionDiagnostics.Checkpoint.CLEAR_CAS_WARNINGS
            Stage12ClearCasObservation.Boundary.REVIEW -> Stage12SelectionDiagnostics.Checkpoint.CLEAR_CAS_REVIEW
            Stage12ClearCasObservation.Boundary.REJECTION -> Stage12SelectionDiagnostics.Checkpoint.CLEAR_CAS_REJECTION
        }
        checked(checkpoint) { clearCasObservation.observe(boundary) { awaitCasWindow(it) } }
    }
    private data class AssociatedCasExpectation(val reviewed: V2ManualShiftLoadUiState, val warnings: List<String>)
    private fun awaitCasWindow(window: Stage12ClearCasObservation.Window, associated: AssociatedCasExpectation? = null) {
        window.waitFor {
            await(condition = {
                when (window.boundary) {
                    Stage12ClearCasObservation.Boundary.WARNINGS -> window.warnings { m.stage }
                    Stage12ClearCasObservation.Boundary.REVIEW -> window.review({ m.stage }, { m.isLoading })
                    Stage12ClearCasObservation.Boundary.REJECTION -> if (window.adversary == Stage12ClearCasObservation.Adversary.ASSOCIATED) {
                        val expected = checkNotNull(associated)
                        window.associatedReconfirmation({ m.stage }, { m.isActive },
                            { m.operationId == expected.reviewed.operationId && m.operationTimestamp == expected.reviewed.operationTimestamp },
                            { m.selectedDates == expected.reviewed.selectedDates },
                            { m.selectedTemplateId == expected.reviewed.selectedTemplateId && m.selectedTemplateFingerprint == expected.reviewed.selectedTemplateFingerprint },
                            { m.isSaving }, { m.isLoading }, { m.warnings == expected.warnings },
                            { m.acknowledgedWarnings == expected.reviewed.acknowledgedWarnings && m.warnings != m.acknowledgedWarnings })
                    } else window.rejection({ m.isSaving }, { m.isLoading }, { m.errorMessage != null })
                }
            }, beforeCasGuard = { window.iterationEntered() })
        }
    }
    private fun goMonth(month: YearMonth) {
        var remaining = 36
        while (c.visibleMonth != month) { check(remaining-- > 0); val before = c.visibleMonth
            touch(if (before < month) "calendar-next-month" else "calendar-previous-month"); await { c.visibleMonth != before && c.loadState == CalendarLoadState.CONTENT } }
    }
    private fun drawer(tag: String) { touch("main-menu-button"); touch(tag) }
    private fun beginLoad(dates: Set<LocalDate>) {
        beginDatesOnly(dates)
        touch("v2-manual-confirm-dates"); await { m.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE && !m.isLoading }
        observeManualForm(dates)
    }
    private fun beginDatesOnly(dates: Set<LocalDate>) {
        check(dates.isNotEmpty() && dates.map(YearMonth::from).distinct().size == 1)
        goMonth(YearMonth.from(dates.first())); drawer("drawer-action-calendar-load")
        await { m.stage == V2ManualShiftLoadStage.SELECT_DATES && c.selectionPurpose == CalendarSelectionPurpose.MANUAL_LOAD }
        dates.sorted().forEach { touch(dayTag(it)) }
        await { c.editSelectedDates == dates && m.selectedDates == dates }
        native("MANUAL_DATE_SELECTION", c.selectionPurpose == CalendarSelectionPurpose.MANUAL_LOAD && c.detailDate == null && m.selectedDates == dates)
    }
    private fun openManualSelectionOptions() { if (exists("calendar-manual-selection-options")) touch("calendar-manual-selection-options") }
    private fun dayTag(date: LocalDate): String = if (c.days.single { it.date == date }.isFullyCompleted)
        "completed-day-$date" else "day-$date"
    private fun catalogHeader(tag: String, scoped: Boolean = false): Stage12CatalogNavigation.Header {
        val matcher = hasTestTag(tag).let { if (scoped) it and hasAnyAncestor(hasTestTag("manual-inline-catalog")) else it }
        val nodes = compose.onAllNodes(matcher).fetchSemanticsNodes()
        val config = nodes.singleOrNull()?.config
        return Stage12CatalogNavigation.Header(nodes.size, config?.let { !it.contains(SemanticsProperties.Disabled) },
            config?.getOrNull(SemanticsProperties.StateDescription))
    }
    private fun catalogContext(): Boolean = m.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE &&
        !m.isLoading && !m.isSaving && m.creationRequest == null && m.batchPreview == null &&
        w.surface == WorkSetupSurface.NONE && c.selectionPurpose == CalendarSelectionPurpose.MANUAL_LOAD &&
        compose.onAllNodesWithTag("v2-manual-exact-dates").fetchSemanticsNodes().size == 1

    private fun prepareCatalog(child: String, kind: Stage12CatalogNavigation.Child) {
        checked(Stage12SelectionDiagnostics.Checkpoint.CATALOG_NAVIGATION) {
            catalogNavigation.navigate(Stage12CatalogNavigation.Kind.CATALOG, kind,
                context = { catalogContext() }, header = { catalogHeader("manual-institutions") },
                interact = { touch("manual-institutions") }, expanded = { catalogHeader("manual-institutions") },
                catalog = { compose.onAllNodesWithTag("manual-inline-catalog").fetchSemanticsNodes().size },
                requiredChild = { compose.onAllNodes(hasTestTag(child) and hasAnyAncestor(hasTestTag("manual-inline-catalog"))).fetchSemanticsNodes().size })
        }
    }
    private fun selectCatalogPlace(place: UUID, child: String, kind: Stage12CatalogNavigation.Child) {
        checked(Stage12SelectionDiagnostics.Checkpoint.CATALOG_NAVIGATION) {
            catalogNavigation.navigate(Stage12CatalogNavigation.Kind.PLACE, kind,
                context = { catalogContext() && compose.onAllNodesWithTag("manual-inline-catalog").fetchSemanticsNodes().size == 1 },
                header = { catalogHeader("manual-place-" + place, scoped = true) },
                interact = { touch("manual-place-" + place) }, expanded = { catalogHeader("manual-place-" + place, scoped = true) },
                catalog = { compose.onAllNodesWithTag("manual-inline-catalog").fetchSemanticsNodes().size },
                requiredChild = { compose.onAllNodes(hasTestTag(child) and hasAnyAncestor(hasTestTag("manual-inline-catalog"))).fetchSemanticsNodes().size })
        }
    }
    private fun choose(template: WorkTemplate = fixture.day) {
        prepareCatalog("manual-place-" + template.workPlaceId, Stage12CatalogNavigation.Child.PLACE)
        selectCatalogPlace(template.workPlaceId, "v2-template-" + template.id, Stage12CatalogNavigation.Child.TEMPLATE)
        touch("v2-template-${template.id}"); check(m.selectedTemplateId == template.id)
        ui("SELECTED_TEMPLATE_ID_AND_SUMMARY") { compose.onNodeWithText("${template.startTime}–${template.endTime} · ${fixture.type.name}").assertExists() }
    }
    private fun noteAndPosition() {
        togglePositionEditor(expandedBefore = false, expectedPosition = "")
        fill("v2-manual-position", "Own position $run")
        touch("manual-add-note"); fill("manual-note", "Own note $run")
    }
    private fun cancelLoad() {
        val drafts = m.noteAttachments.map { File(dataRoot, "manual_load_drafts/${it.storageKey}") }
        touch("manual-cancel-load"); text("Descartar carga")
        await { !m.isActive && !m.isSaving && c.interactionMode == CalendarInteractionMode.VIEW && drafts.none(File::exists) }
    }
    private fun finishSave(expectedCount: Int, warningExpected: Boolean = false) {
        if (warningExpected) { waitTag("v2-warning-dialog"); text("Continuar igualmente") }
        await { !m.isActive && !m.isSaving && c.interactionMode == CalendarInteractionMode.VIEW && allWrites().size == expectedCount }
    }
    private fun fact(name: String, related: List<String>, level: String, observed: JSONObject = JSONObject()) {
        facts.put(JSONObject().put("assertion_key", "selection-batch.$name").put("level", level)
            .put("related_case_ids_not_full_credit", JSONArray(related)).put("check", "PASS").put("observed", observed))
    }
    private fun ui(code: String, oracle: () -> Unit) { checked(Stage12SelectionDiagnostics.Checkpoint.UI_ASSERTION, oracle); uiOracles += code }
    private fun native(code: String, condition: Boolean) {
        checked(Stage12SelectionDiagnostics.Checkpoint.NATIVE_ASSERTION) { check(condition) { "SELECTION_NATIVE_ORACLE_FAILED" } }
        nativeOracles += code
    }
    private fun observeManualForm(dates: Set<LocalDate>) {
        val summary = dates.sorted().take(3).joinToString(" - ") { it.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")) }
        ui("EXACT_DATE_FORM") { compose.onNodeWithTag("v2-manual-exact-dates").assertTextContains(summary) }
        native("MANUAL_DATE_CONFIRMATION", m.selectedDates == dates && c.editSelectionConfirmed && c.selectionPurpose == CalendarSelectionPurpose.MANUAL_LOAD)
    }
    private fun uuid(kind: String) = UUID.nameUUIDFromBytes("$run/$slot/$kind/${sequence.incrementAndGet()}".toByteArray(Charsets.UTF_8))
    private fun key(kind: String) = "stage12:$run:$slot:$kind"
    private fun <T> db(body: suspend () -> T): T = runBlocking { withTimeout(30_000) { body() } }
    private fun hash(value: String) = hash(value.toByteArray(Charsets.UTF_8))
    private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it.toInt() and 255) }

    private companion object { const val QA = "com.blackatsystems.miguardia.qa" }

    // C097 is opt-in. Keep the historical source fence and methods above unchanged.
    private var c097Enabled = false
    private val institutionCases = JSONArray()
    private val institutionOwner = JSONObject().put("mount_attempts", 0).put("mount_returns", 0)
        .put("composition_closures", 0).put("owner_closures", 0).put("model_jobs_joined", 0)
    private var c097Composition: Stage12TemporalComposition? = null
    private val c097Models = java.util.IdentityHashMap<androidx.lifecycle.ViewModel, kotlinx.coroutines.Job>()
    private var c097MountUncertain = false
    private var c097TransitionUncertain = false
    private enum class InstitutionVariant { RENAMED, ARCHIVED }
    private enum class InstitutionStep {
        DRAFT_BEFORE_MUTATION, RETURNED_TO_DATES, MUTATION, RECREATED_DATES,
        SAVE_BLOCKED, RESELECTED, DISCARDED, REOPENED,
    }
    private data class InstitutionDraft(
        val operationId: UUID, val operationTimestamp: Instant, val timelineId: UUID,
        val dates: Set<LocalDate>, val note: String, val position: String,
        val attachments: List<ShiftNoteAttachment>, val selectedTemplate: UUID, val fingerprint: String,
    )
    private data class InstitutionFiles(
        val privateNodes: Map<String, Stage12FileSnapshot>, val sourceNodes: Map<String, Stage12FileSnapshot>,
    )
    private data class InstitutionAlternative(val objective: Objective, val place: WorkPlace, val template: WorkTemplate)

    @Test fun institutionChangeAndArchiveRequireReselection() = safe {
        c097Enabled = true
        family(Stage12C097InstitutionContract.SLOT) {
            institutionCase(InstitutionVariant.RENAMED)
            isolatedCase("institution-archive")
            institutionCase(InstitutionVariant.ARCHIVED)
        }
    }

    private fun c097Receipt(receipt: JSONObject) {
        receipt.put("runtime_api", android.os.Build.VERSION.SDK_INT)
            .put("received_state", "NOT_CERTIFIED").put("post_ui_certified", false)
            .put("institution_cases", institutionCases).put("institution_owner", institutionOwner)
    }
    private fun c097MountEntered() {
        check(!c097MountUncertain && c097Composition == null)
        c097MountUncertain = true
        institutionOwner.put("mount_attempts", institutionOwner.getInt("mount_attempts") + 1)
    }
    private fun c097CompositionParent(owner: ComponentActivity): androidx.compose.runtime.CompositionContext {
        listOf(calendar, manual, work, exceptions).forEach { model ->
            c097Models[model] = checkNotNull(model.viewModelScope.coroutineContext[kotlinx.coroutines.Job])
        }
        val composition = Stage12TemporalComposition(realQa) { diagnostic.retain(it) }
        c097Composition = composition
        return composition.initialize(owner)
    }
    private fun c097ContentInstalled(owner: ComponentActivity) {
        checkNotNull(c097Composition).bindContent(owner)
        c097MountUncertain = false
        institutionOwner.put("mount_returns", institutionOwner.getInt("mount_returns") + 1)
    }
    private fun c097RetireComposition(owner: ComponentActivity) {
        val composition = checkNotNull(c097Composition)
        composition.observeDestroyed(owner)
        runBlocking { composition.closeAfterDestroyed { block -> instrumentation.runOnMainSync(block) } }
        check(composition.closureProven)
        institutionOwner.put("composition_closures", institutionOwner.getInt("composition_closures") + 1)
        c097Composition = null
    }
    private fun c097Recreate(): Boolean {
        val previous = checkNotNull(activity)
        val previousManual = manual
        c097TransitionUncertain = true
        checkNotNull(scenario).recreate()
        checkNotNull(scenario).onActivity { activity = it }
        check(previous.isDestroyed && activity !== previous)
        c097RetireComposition(previous)
        mount()
        await { activity?.lifecycle?.currentState == Lifecycle.State.RESUMED && c.loadState == CalendarLoadState.CONTENT }
        check(!c097MountUncertain)
        c097TransitionUncertain = false
        recreates++
        return manual === previousManual
    }
    @android.annotation.SuppressLint("RestrictedApi")
    private fun c097CloseOwner() {
        val owner = activity
        if (owner == null && scenario == null) {
            check(!c097MountUncertain && !c097TransitionUncertain && c097Composition == null && c097Models.isEmpty())
            return
        }
        checkNotNull(scenario).close()
        checkNotNull(owner)
        check(owner.isDestroyed && owner.lifecycle.currentState == Lifecycle.State.DESTROYED)
        val storeEmpty = owner.viewModelStore.keys().isEmpty()
        c097RetireComposition(owner)
        val jobs = c097Models.values.toSet()
        runBlocking { withTimeout(30_000) { jobs.forEach { it.join() } } }
        check(storeEmpty && !c097MountUncertain && !c097TransitionUncertain && jobs.all { it.isCompleted })
        institutionOwner.put("model_jobs_joined", institutionOwner.getInt("model_jobs_joined") + jobs.size)
            .put("owner_closures", institutionOwner.getInt("owner_closures") + 1)
        c097Models.clear()
        scenario = null; activity = null
    }
    private fun institutionFiles() = InstitutionFiles(
        Stage12PrivateInventory(privateRoot, null).capture(), Stage12PrivateInventory(sourceRoot, null).capture())
    private fun institutionReadback(tables: Map<String, Table>, inventory: InstitutionFiles): JSONObject = JSONObject()
        .put("tables_sha256", hash(tables.toString())).put("table_count", tables.size)
        .put("private_inventory_sha256", hash(inventory.privateNodes.toString())).put("private_entry_count", inventory.privateNodes.size)
        .put("source_inventory_sha256", hash(inventory.sourceNodes.toString())).put("source_entry_count", inventory.sourceNodes.size)
    private fun institutionDraft(): InstitutionDraft {
        val state = m
        return InstitutionDraft(checkNotNull(state.operationId), checkNotNull(state.operationTimestamp), checkNotNull(state.timelineId),
            state.selectedDates.toSet(), state.noteBody, state.position, state.noteAttachments.toList(),
            checkNotNull(state.selectedTemplateId), checkNotNull(state.selectedTemplateFingerprint))
    }
    private fun requireInstitutionDraft(expected: InstitutionDraft) {
        val state = m
        check(state.operationId == expected.operationId && state.operationTimestamp == expected.operationTimestamp)
        check(state.timelineId == expected.timelineId && state.selectedDates == expected.dates)
        check(state.noteBody == expected.note && state.position == expected.position && state.noteAttachments == expected.attachments)
        check(!state.isLoading && !state.isSaving)
    }
    private fun institutionDraftReceipt(draft: InstitutionDraft, imageHash: String): JSONObject = JSONObject()
        .put("operation_id", draft.operationId.toString()).put("operation_timestamp", draft.operationTimestamp.toString())
        .put("selected_dates", JSONArray(draft.dates.sorted().map { it.toString() }))
        .put("note_sha256", hash(draft.note)).put("position_sha256", hash(draft.position))
        .put("attachment_reference_sha256", hash(draft.attachments.single().toString())).put("png_sha256", imageHash)
        .put("selected_template_id", draft.selectedTemplate.toString()).put("selected_fingerprint_sha256", hash(draft.fingerprint))
    private fun institutionStep(record: JSONObject, step: InstitutionStep, body: () -> JSONObject) {
        phase = "INSTITUTION_" + record.getString("variant") + "_" + step.name
        val event = JSONObject().put("step", step.name).put("status", "ENTERED")
        record.getJSONArray("journal").put(event)
        try {
            val measured = checked(Stage12SelectionDiagnostics.Checkpoint.RULE_EVALUATION, body)
            event.put("observed", measured).put("status", "RETURNED")
        } catch (error: Throwable) {
            try { event.put("status", "THREW") } catch (publication: Throwable) {
                if (publication !== error) try { error.addSuppressed(publication) } catch (_: Throwable) { }
            }
            throw error
        }
    }
    private fun seedInstitutionAlternative(): InstitutionAlternative = db {
        val now = stamp().minusSeconds(60)
        val objective = Objective(uuid("alternative-objective"), "Alternative institution $run", "ALT", null, null, true, now, now)
        val place = fixture.place.copy(id = uuid("alternative-place"), objectiveId = objective.id, createdAt = now, updatedAt = now)
        val rule = fixture.rule.copy(id = uuid("alternative-rule"), workPlaceId = place.id, objectiveId = objective.id, createdAt = now)
        own.workCatalog.createWorkPlace(NewWorkPlace(objective, place, rule,
            ResolvedWorkConfigurationRevision.resolve(checkNotNull(own.workConfiguration.get()), rule.effectiveFrom)))
        val template = fixture.day.copy(id = uuid("alternative-template"), workPlaceId = place.id, objectiveId = objective.id,
            createdAt = now, updatedAt = now)
        own.workCatalog.createWorkTemplate(template)
        check(own.workCatalog.getWorkPlace(place.id) == place && own.workCatalog.getWorkTemplate(template.id) == template)
        InstitutionAlternative(objective, place, template)
    }
    private fun institutionExpectedTables(before: Map<String, Table>, variant: InstitutionVariant, changedAt: Instant,
        changedObjective: Objective): Map<String, Table> {
        fun replaceRow(name: String, id: UUID, changes: Map<String, String>): Table {
            val table = checkNotNull(before[name])
            val idIndex = table.columns.indexOf("id").also { check(it >= 0) }
            check(table.rows.count { it[idIndex] == "S:$id" } == 1)
            check(changes.keys.all { it in table.columns })
            return table.copy(rows = table.rows.map { row ->
                if (row[idIndex] != "S:$id") row else row.mapIndexed { index, value -> changes[table.columns[index]] ?: value }
            }.sortedBy { it.joinToString("\u0000") })
        }
        val expected = before.toMutableMap()
        val placeChanges = mutableMapOf("updatedAtEpochMillis" to "I:" + changedAt.toEpochMilli())
        if (variant == InstitutionVariant.ARCHIVED) placeChanges["isActive"] = "I:0"
        expected["work_places"] = replaceRow("work_places", fixture.place.id, placeChanges)
        if (variant == InstitutionVariant.RENAMED) expected["objectives"] = replaceRow("objectives", fixture.objective.id,
            mapOf("fullName" to "S:" + changedObjective.fullName, "updatedAtEpochMillis" to "I:" + changedAt.toEpochMilli()))
        check(expected != before)
        return expected
    }
    private fun institutionTextVisible(value: String) {
        val node = compose.onNodeWithText(value, substring = false, ignoreCase = false)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().assertTextEquals(value)
    }
    private fun institutionChoose(template: WorkTemplate, archivedPlace: UUID?) {
        prepareCatalog("manual-place-" + template.workPlaceId, Stage12CatalogNavigation.Child.PLACE)
        if (archivedPlace != null) ui("C097_ARCHIVED_PLACE_ABSENT_IN_CATALOG") {
            check(compose.onAllNodes(hasTestTag("manual-place-" + archivedPlace) and
                hasAnyAncestor(hasTestTag("manual-inline-catalog"))).fetchSemanticsNodes().isEmpty())
        }
        selectCatalogPlace(template.workPlaceId, "v2-template-" + template.id, Stage12CatalogNavigation.Child.TEMPLATE)
        touch("v2-template-" + template.id); check(m.selectedTemplateId == template.id)
        ui("C097_SELECTED_TEMPLATE_SUMMARY") {
            compose.onNodeWithText(template.startTime.toString() + "–" + template.endTime + " · " + fixture.type.name).assertExists()
        }
    }
    private fun institutionCase(variant: InstitutionVariant) {
        val date = LocalDate.of(2026, 10, 23)
        val alternative = seedInstitutionAlternative()
        val record = JSONObject().put("variant", variant.name).put("civil_date", date.toString())
            .put("zone", zone.id).put("selected_dates", JSONArray(listOf(date.toString())))
            .put("target_place_id", fixture.place.id.toString()).put("target_objective_id", fixture.objective.id.toString())
            .put("target_template_id", fixture.day.id.toString()).put("alternative_place_id", alternative.place.id.toString())
            .put("alternative_objective_id", alternative.objective.id.toString()).put("alternative_template_id", alternative.template.id.toString())
            .put("journal", JSONArray())
        institutionCases.put(record)
        lateinit var draft: InstitutionDraft
        lateinit var beforeTables: Map<String, Table>
        lateinit var beforeFiles: InstitutionFiles
        lateinit var expectedTables: Map<String, Table>
        lateinit var afterDiscard: InstitutionFiles
        lateinit var image: Pair<String, String>
        lateinit var changedObjective: Objective
        lateinit var changedPlace: WorkPlace
        institutionStep(record, InstitutionStep.DRAFT_BEFORE_MUTATION) {
            beginLoad(setOf(date)); choose(); noteAndPosition(); image = importImage()
            draft = institutionDraft()
            check(draft.dates == setOf(date) && draft.selectedTemplate == fixture.day.id)
            check(draft.note == "Own note $run" && draft.position == "Own position $run")
            check(draft.attachments.single().storageKey == image.first)
            beforeTables = snapshot(); beforeFiles = institutionFiles()
            check(beforeTables.getValue("shifts").rows.isEmpty() && beforeTables.getValue("shift_work_snapshots").rows.isEmpty())
            val draftPath = dataRoot.relativeTo(privateRoot).invariantSeparatorsPath + "/manual_load_drafts/" + image.first
            check(beforeFiles.privateNodes.getValue(draftPath) == Stage12FileSnapshot(draft.attachments.single().byteSize, image.second))
            institutionDraftReceipt(draft, image.second).put("readback", institutionReadback(beforeTables, beforeFiles))
        }
        institutionStep(record, InstitutionStep.RETURNED_TO_DATES) {
            touch("v2-manual-modify-dates")
            await { m.stage == V2ManualShiftLoadStage.SELECT_DATES && c.selectionPurpose == CalendarSelectionPurpose.MANUAL_LOAD }
            requireInstitutionDraft(draft)
            check(m.selectedTemplateId == draft.selectedTemplate && m.selectedTemplateFingerprint == draft.fingerprint)
            check(c.editSelectedDates == draft.dates && !c.editSelectionConfirmed)
            val tables = snapshot(); val inventory = institutionFiles()
            check(tables == beforeTables && inventory == beforeFiles)
            JSONObject().put("draft_preserved", true).put("calendar_dates_exact", true)
                .put("readback", institutionReadback(tables, inventory))
        }
        institutionStep(record, InstitutionStep.MUTATION) {
            val at = stamp()
            check(at > fixture.place.updatedAt && at > fixture.objective.updatedAt)
            changedObjective = if (variant == InstitutionVariant.RENAMED)
                fixture.objective.copy(fullName = "Renamed institution $run", updatedAt = at) else fixture.objective
            changedPlace = if (variant == InstitutionVariant.RENAMED) fixture.place.copy(updatedAt = at)
                else fixture.place.copy(isActive = false, updatedAt = at)
            expectedTables = institutionExpectedTables(beforeTables, variant, at, changedObjective)
            db {
                if (variant == InstitutionVariant.RENAMED) own.workCatalog.updateWorkPlace(
                    WorkPlaceUpdate(fixture.place, changedPlace, fixture.objective, changedObjective))
                else own.workCatalog.setWorkPlaceActive(fixture.place.id, false, at)
            }
            val tables = snapshot(); val inventory = institutionFiles()
            check(tables == expectedTables && inventory == beforeFiles)
            JSONObject().put("mutation", if (variant == InstitutionVariant.RENAMED) "UPDATE_WORK_PLACE" else "SET_WORK_PLACE_INACTIVE")
                .put("changed_at", at.toString()).put("exact_delta", true).put("readback", institutionReadback(tables, inventory))
        }
        institutionStep(record, InstitutionStep.RECREATED_DATES) {
            val sameModel = c097Recreate()
            requireInstitutionDraft(draft)
            check(m.stage == V2ManualShiftLoadStage.SELECT_DATES && m.selectedTemplateId == draft.selectedTemplate &&
                m.selectedTemplateFingerprint == draft.fingerprint)
            check(c.editSelectedDates == draft.dates && c.selectionPurpose == CalendarSelectionPurpose.MANUAL_LOAD && !c.editSelectionConfirmed)
            compose.onNodeWithTag("v2-manual-confirm-dates").assertIsEnabled()
            val tables = snapshot(); val inventory = institutionFiles()
            check(tables == expectedTables && inventory == beforeFiles)
            JSONObject().put("owner_changed", true).put("previous_owner_destroyed", true).put("same_manual_model", sameModel)
                .put("draft_preserved", true).put("readback", institutionReadback(tables, inventory))
        }
        institutionStep(record, InstitutionStep.SAVE_BLOCKED) {
            touch("v2-manual-confirm-dates")
            await { m.stage == V2ManualShiftLoadStage.CHOOSE_TEMPLATE && !m.isLoading }
            requireInstitutionDraft(draft)
            check(m.selectedTemplateId == null && m.selectedTemplateFingerprint == null)
            val message = "La institución o el horario elegido cambió o ya no está disponible. Conservamos el borrador; elegí nuevamente una opción válida."
            check(m.errorMessage == message)
            ui("C097_INVALIDATION_MESSAGE_AND_SAVE_BLOCKED") {
                institutionTextVisible(message)
                val save = compose.onNodeWithTag("v2-manual-save-direct")
                if (!save.isDisplayed()) save.performScrollTo()
                save.assertIsDisplayed().assertIsNotEnabled()
            }
            check(c.editSelectedDates == draft.dates && c.editSelectionConfirmed)
            if (variant == InstitutionVariant.ARCHIVED) {
                check(m.places.none { it.place.id == fixture.place.id } && m.templateOptions.none { it.workPlace.id == fixture.place.id })
            } else check(m.templateOptions.single { it.template.id == fixture.day.id } ==
                V2ManualShiftTemplateOption(changedObjective, changedPlace, fixture.type, fixture.day))
            val tables = snapshot(); val inventory = institutionFiles()
            check(tables == expectedTables && inventory == beforeFiles)
            native("C097_RECREATED_SELECTION_INVALIDATED", m.operationId == draft.operationId && m.selectedTemplateId == null)
            JSONObject().put("selection_cleared", true).put("save_disabled", true).put("message_exact_visible", true)
                .put("draft_preserved", true).put("readback", institutionReadback(tables, inventory))
        }
        institutionStep(record, InstitutionStep.RESELECTED) {
            val selected = if (variant == InstitutionVariant.RENAMED) fixture.day else alternative.template
            val expectedObjective = if (variant == InstitutionVariant.RENAMED) changedObjective else alternative.objective
            val expectedPlace = if (variant == InstitutionVariant.RENAMED) changedPlace else alternative.place
            institutionChoose(selected, if (variant == InstitutionVariant.ARCHIVED) fixture.place.id else null)
            requireInstitutionDraft(draft)
            check(m.selectedOption == V2ManualShiftTemplateOption(expectedObjective, expectedPlace, fixture.type, selected))
            check(m.selectedTemplateId == selected.id && m.selectedTemplateFingerprint != null && m.selectedTemplateFingerprint != draft.fingerprint)
            ui("C097_RESELECTION_EXACT_AND_SAVE_ENABLED") {
                institutionTextVisible("${expectedObjective.fullName} (${expectedObjective.abbreviation})")
                val save = compose.onNodeWithTag("v2-manual-save-direct")
                if (!save.isDisplayed()) save.performScrollTo()
                save.assertIsDisplayed().assertIsEnabled()
            }
            val tables = snapshot(); val inventory = institutionFiles()
            check(tables == expectedTables && inventory == beforeFiles)
            JSONObject().put("selected_place_id", expectedPlace.id.toString()).put("selected_template_id", selected.id.toString())
                .put("selected_fingerprint_sha256", hash(checkNotNull(m.selectedTemplateFingerprint)))
                .put("save_enabled", true).put("draft_preserved", true).put("readback", institutionReadback(tables, inventory))
        }
        institutionStep(record, InstitutionStep.DISCARDED) {
            val draftPath = dataRoot.relativeTo(privateRoot).invariantSeparatorsPath + "/manual_load_drafts/" + image.first
            check(beforeFiles.privateNodes.getValue(draftPath) == Stage12FileSnapshot(draft.attachments.single().byteSize, image.second))
            val expectedFiles = beforeFiles.copy(privateNodes = beforeFiles.privateNodes - draftPath)
            cancelLoad()
            val tables = snapshot(); afterDiscard = institutionFiles()
            check(tables == expectedTables && afterDiscard == expectedFiles)
            check(m.selectedTemplateId == null && !m.isActive && c.editSelectedDates.isEmpty())
            JSONObject().put("explicit_discard", true).put("draft_png_removed_only", true)
                .put("readback", institutionReadback(tables, afterDiscard))
        }
        institutionStep(record, InstitutionStep.REOPENED) {
            val oldStore = own; val oldOwner = activity
            closeOwner(); own.close(); store = null
            store = LocalDataStore.create(realQa, dbName); createServices(); launch()
            check(own !== oldStore && activity !== oldOwner && !m.isActive && m.selectedTemplateId == null)
            val tables = snapshot(); val inventory = institutionFiles()
            check(tables == expectedTables && inventory == afterDiscard)
            reopens++
            JSONObject().put("owner_changed", true).put("store_changed", true).put("draft_absent", true)
                .put("readback", institutionReadback(tables, inventory))
        }
        listOf("UI_COMPOSE", "ACTIVITY_NATIVE", "ROOM_ISOLATED", "FILES_ISOLATED").forEach { level ->
            fact("c097-institution-" + variant.name.lowercase() + "-" + level.lowercase(), listOf("C097"), level,
                JSONObject().put("variant", variant.name).put("institution_case_index", institutionCases.length() - 1)
                    .put("key", "C097-contrato-completo." + level.lowercase()).put("journal_complete", true))
        }
    }
}

/** The real rule/helpers use this pure protocol; host checks invoke its compiled bytecode. */
internal object Stage12SelectionDiagnostics {
    enum class Checkpoint {
        UNMARKED, RULE_EVALUATION, OWN_CLEANUP, RECEIPT_PUBLICATION, CLOSE_OWNER, CLOSE_STORE,
        NODE_PRESENCE, NODE_SCROLL_QUERY, NODE_SCROLL, NODE_VISIBLE, NODE_ENABLED, NODE_SINGLE_TOUCH,
        NODE_REPLACE_TEXT, HIDE_OWN_IME, POSITION_EDITOR_BEFORE, POSITION_VALUE_BEFORE,
        POSITION_EDITOR_AFTER_WAIT, POSITION_VALUE_AFTER, POSITION_FIELD_PRESENT,
        POSITION_SUMMARY_PRESENT, POSITION_SUMMARY_CONTENT, NOTE_SUMMARY_PRESENT, NOTE_SUMMARY_CONTENT,
        UI_ASSERTION, NATIVE_ASSERTION, CATALOG_RESERVED_ID, CATALOG_READ, CATALOG_COMPARE, CATALOG_NAVIGATION, DIALOG_CANCEL, CLEAR_CAS_WARNINGS, CLEAR_CAS_REVIEW, CLEAR_CAS_REJECTION,
        RECOVERY_DELETE_EXPECTATIONS, RECOVERY_DELETE_SQL, RECOVERY_DELETE_FILES, RECOVERY_DELETE_COMPARE,
    }

    class Trace {
        @JvmField var primary: Throwable? = null
        @JvmField var checkpoint: Checkpoint = Checkpoint.UNMARKED
        @JvmField var checkpointOrdinal: Int = 0
        @JvmField var entryOrdinal: Int = 0
        @JvmField var firstCheckpoint: Checkpoint = Checkpoint.UNMARKED
        @JvmField var firstCheckpointOrdinal: Int = 0
        private var firstOrigin: Map<String, Any>? = null

        fun mark(next: Checkpoint) {
            if (primary == null) { checkpoint = next; entryOrdinal++; checkpointOrdinal = entryOrdinal }
        }

        fun <T> checked(next: Checkpoint, body: () -> T): T {
            primary?.let { throw it }
            return reached(next, body)
        }

        // Necessary owner/store closure is allowed after failure; the first projection stays frozen.
        fun <T> closing(next: Checkpoint, body: () -> T): T = reached(next, body)

        private fun <T> reached(next: Checkpoint, body: () -> T): T {
            val previous = checkpoint
            val previousOrdinal = checkpointOrdinal
            mark(next)
            return try { body() } catch (error: Throwable) {
                retain(error)
                throw error
            } finally {
                if (primary == null) { checkpoint = previous; checkpointOrdinal = previousOrdinal }
            }
        }

        fun retain(error: Throwable): Throwable {
            val first = primary
            if (first == null) {
                primary = error
                firstCheckpoint = checkpoint
                firstCheckpointOrdinal = checkpointOrdinal
                // Only the original stack is examined once. Causes and suppressed errors stay in RAM.
                firstOrigin = safeOrigin(error)
            } else if (first !== error && first.suppressed.none { it === error }) first.addSuppressed(error)
            return checkNotNull(primary)
        }

        fun finish(body: () -> Unit, cleanup: () -> Unit, publish: (Boolean, Boolean, Throwable?) -> Unit) {
            var ruleSucceeded = false
            var cleaned = false
            if (primary == null) {
                mark(Checkpoint.RULE_EVALUATION)
                try { body(); ruleSucceeded = true } catch (error: Throwable) { retain(error) }
            }
            if (primary == null) {
                mark(Checkpoint.OWN_CLEANUP)
                try { cleanup(); cleaned = true } catch (error: Throwable) { retain(error) }
            }
            mark(Checkpoint.RECEIPT_PUBLICATION)
            try { publish(ruleSucceeded, cleaned, primary) } catch (error: Throwable) { retain(error) }
        }

        fun projection(): Map<String, Any> = linkedMapOf<String, Any>(
            "schema" to "stage12-selection-safe-diagnostic-v1",
            "first_failure_recorded" to (primary != null),
        ).apply {
            if (primary != null) {
                put("checkpoint", firstCheckpoint.name)
                put("checkpoint_ordinal", firstCheckpointOrdinal)
                put("origin_status", if (firstOrigin == null) "UNKNOWN" else "KNOWN")
                firstOrigin?.let { put("origin", it.toMap()) }
            }
        }
    }

    @JvmStatic fun publicFailure(): AssertionError = AssertionError("STAGE12_SELECTION_BATCH_FAILED")

    private const val TEST_CLASS = "com.blackatsystems.miguardia.stage12.Stage12SelectionBatchTest"
    private const val TEST_FILE = "Stage12SelectionBatchTest.kt"
    private const val TEST_LAST_LINE = 1528
    private val permittedMethods = setOf(
        "family", "selectionAndDraft", "policiesAndStates", "clearAndCas", "doubleTouchAndRecovery",
        "touch", "text", "fill", "fillLabel", "togglePositionEditor", "noteAndPosition", "choose",
        "catalogHeader", "catalogContext", "prepareCatalog", "selectCatalogPlace", "cancelScope", "cancelDialog", "requireQaReady", "awaitCasBoundary", "awaitCasWindow",
        "observeManualForm", "generalSelect", "beginLoad", "beginDatesOnly", "cancelLoad", "finishSave",
        "ui", "native", "back", "assertSavedCopies", "reopen", "isolatedCase", "seedCatalog", "importImage",
        "closeOwner", "snapshot", "files", "waitTag", "await", "deleteOwnTree", "newDirectory",
    )

    private fun safeOrigin(error: Throwable): Map<String, Any>? {
        for (frame in error.stackTrace) {
            if (frame.className != TEST_CLASS || frame.fileName != TEST_FILE || frame.lineNumber !in 1..TEST_LAST_LINE) continue
            val method = permittedMethods.firstOrNull { allowed ->
                frame.methodName == allowed || frame.methodName.startsWith("${allowed}\$lambda\$") &&
                    frame.methodName.removePrefix("${allowed}\$lambda\$").matches(Regex("[0-9]+(?:\\\$[0-9]+)*"))
            } ?: continue
            return linkedMapOf("file" to TEST_FILE, "method" to method, "line" to frame.lineNumber)
        }
        return null
    }
}

/** One original readiness policy, shared by await and owned dialog cancellation. */
internal object Stage12SelectionQaGuard {
    @JvmStatic fun requireReady(screenOn: () -> Boolean, qaForeground: () -> Boolean,
        keyguardFree: () -> Boolean, sameCivilDay: () -> Boolean) {
        check(screenOn() && qaForeground() && keyguardFree()) { "SELECTION_QA_FOCUS_LOST" }
        check(sameCivilDay()) { "SELECTION_CIVIL_DAY_CHANGED" }
    }
}
