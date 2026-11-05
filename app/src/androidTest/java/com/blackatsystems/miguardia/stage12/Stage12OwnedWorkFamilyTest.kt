package com.blackatsystems.miguardia.stage12

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.WeekendDays
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.availability.*
import com.blackatsystems.miguardia.ui.management.UuidProvider
import com.blackatsystems.miguardia.ui.management.V2RecurringActions
import com.blackatsystems.miguardia.ui.management.V2RecurringPlanSurfaceHost
import com.blackatsystems.miguardia.ui.management.V2RecurringPlanViewModel
import com.blackatsystems.miguardia.ui.management.V2RecurringStage
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.worksetup.*
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Product components + real ViewModels/Room in four NEW named databases.
 * The host is explicitly ComponentActivity, never MainActivity/bootstrap.
 * No received Application store, preferences, private images or system settings
 * are obtained by this fixture. Normal QA Application startup is not suppressed.
 */
class Stage12OwnedWorkFamilyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val zone = ZoneId.of("America/Argentina/Cordoba")
    private val clock = Clock.system(zone)
    private val sequence = AtomicInteger()
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var activity: ComponentActivity? = null
    private var store: LocalDataStore? = null
    private lateinit var work: WorkSetupViewModel
    private lateinit var availability: AvailabilityViewModel
    private lateinit var recurring: V2RecurringPlanViewModel
    private lateinit var run: String
    private lateinit var sector: WorkSector
    private lateinit var anchor: LocalDate
    private lateinit var databaseName: String
    private lateinit var databasePath: File
    private var ownedDatabaseCreated = false
    private var dayContext by mutableStateOf<LocalDate?>(null)
    private var facts = JSONArray()
    private var directEntries = JSONArray()
    private var reopenCount = 0
    private var recreateCount = 0
    private var phase = "PRECONDITIONS"
    private var actionOrdinal = 0
    private var actionKind = "NONE"
    private var actionTargetSha256 = "NONE"
    private var ownedBackAttempts = 0
    private var ownedBackImeHideAttempts = 0
    private var lastOriginalOwnerReadiness = "NOT_OBSERVED"
    private var lastOwnedImeVisibility = "NOT_OBSERVED"
    private val ownedBackReadiness = JSONArray()
    private val ownedBackEvents = mutableListOf<Pair<String, Stage12OwnedBackEventSemantics.Observation>>()
    private val ownStore get() = checkNotNull(store) { "OWN_WORK_STORE_NOT_OPEN" }

    @Test fun privateSecurityOwnWorkFamily() = runFamily(WorkSector.PRIVATE_SECURITY, "private-security", "A016")
    @Test fun policeOwnWorkFamily() = runFamily(WorkSector.POLICE, "police", "A017")
    @Test fun nursingOwnWorkFamily() = runFamily(WorkSector.NURSING, "nursing", "A018")
    @Test fun medicineOwnWorkFamily() = runFamily(WorkSector.MEDICINE, "medicine", "A019")

    private fun runFamily(target: WorkSector, suffix: String, sectorCase: String) {
        check(instrumentation is Stage12NormalQaRunner && context.packageName == QA) { "OWN_WORK_RUNNER_OR_TARGET" }
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("stage12CalendarClock") == "REAL") { "OWN_WORK_REAL_REQUIRED" }
        run = requireNotNull(args.getString("stage12RunId"))
        require(UUID.fromString(run).toString() == run)
        val qa = requireNotNull(args.getString("stage12QaSha256"))
        val runner = requireNotNull(args.getString("stage12RunnerSha256"))
        require(qa.matches(Regex("[0-9a-f]{64}")) && runner.matches(Regex("[0-9a-f]{64}")))
        sector = target
        anchor = LocalDate.now(clock)
        databaseName = "stage12-owned-work-$run-${target.name.lowercase()}.db"
        require(databaseName.matches(Regex("stage12-owned-work-[a-z0-9_-]+\\.db")))
        databasePath = context.getDatabasePath(databaseName).canonicalFile
        val databaseDirectory = File(context.applicationInfo.dataDir, "databases").canonicalFile
        check(databasePath.parentFile == databaseDirectory && databaseName != "miguardia-v2.db") { "OWN_WORK_DATABASE_FENCE" }
        val identity = JSONObject().put("run_uuid", run).put("qa_sha256", qa).put("runner_sha256", runner)
        val output = Stage12NativeReceiptFiles.prepareOutput(context.filesDir, run, identity.toString(), "owned-work-$suffix")
        val receipt = JSONObject(identity.toString()).put("schema", "stage12-owned-work-family-v1")
            .put("case", "owned-work-$suffix").put("sector", target.name).put("date_anchor", anchor.toString())
            .put("calendar_clock_mode", "REAL").put("zone", zone.id).put("facts", facts)
            .put("host", "QA_COMPONENT_ACTIVITY_PRODUCT_SURFACES").put("direct_component_entries", directEntries)
            .put("main_activity_bootstrap_proven", false).put("global_settings_changed", false)
            .put("received_application_store_accessed_by_fixture", false).put("normal_application_startup_suppressed", false)
            .put("universal_conservation_claim", false).put("round_credit", 0).put("status", "INCOMPLETE")
            .put("database_name_sha256", digest(databaseName)).put("database_preexisted", false)
        var failure: Throwable? = null
        var ownerClosed = false
        var storeClosed = false
        var databaseDeleted = false
        var databaseCreationAttempted = false
        try {
            check(!Files.exists(databasePath.toPath(), NOFOLLOW_LINKS) &&
                listOf("-wal", "-shm", "-journal").none { File(databasePath.path + it).exists() }) { "OWN_WORK_DATABASE_ALREADY_EXISTS" }
            databaseCreationAttempted = true
            store = LocalDataStore.create(context, databaseName)
            ownedDatabaseCreated = true
            check(readFrame().history == null) { "OWN_WORK_NEW_DATABASE_NOT_EMPTY" }
            launchOwner()
            phase = "WORK_CONFIGURATION_AND_CATALOG"
            exerciseWork(sectorCase)
            phase = "CATALOG_REOPEN"
            reopenAndCompare("catalog")
            phase = "AVAILABILITY"
            exerciseAvailability()
            phase = "AVAILABILITY_REOPEN"
            reopenAndCompare("availability")
            phase = "RECURRING"
            exerciseRecurring()
            phase = "RECURRING_REOPEN"
            reopenAndCompare("recurring")
            receipt.put("final_business_sha256", digest(readFrame().toString()))
        } catch (error: Throwable) {
            failure = error
            receipt.put("failure_class", error.javaClass.simpleName).put("failure_phase", phase)
                .put("failure_action_ordinal", actionOrdinal).put("failure_action_kind", actionKind)
                .put("failure_action_target_sha256", actionTargetSha256)
        } finally {
            try {
                closeOwner()
                ownerClosed = true
            } catch (error: Throwable) {
                receipt.put("owner_close_failure_class", error.javaClass.simpleName)
                failure = combine(failure, error)
            }
            if (ownerClosed) {
                try {
                    store?.close()
                    store = null
                    storeClosed = true
                } catch (error: Throwable) {
                    receipt.put("store_close_failure_class", error.javaClass.simpleName)
                    failure = combine(failure, error)
                }
            }
            // Failed/uncertain work is retained for diagnosis; no repair/reseed.
            if (failure == null && ownerClosed && storeClosed && ownedDatabaseCreated) {
                try {
                    check(databasePath == context.getDatabasePath(databaseName).canonicalFile &&
                        databasePath.parentFile == databaseDirectory && !Files.isSymbolicLink(databasePath.toPath())) { "OWN_WORK_CLEANUP_FENCE" }
                    check(context.deleteDatabase(databaseName)) { "OWN_WORK_DELETE_FAILED" }
                    check(listOf("", "-wal", "-shm", "-journal").none {
                        Files.exists(File(databasePath.path + it).toPath(), NOFOLLOW_LINKS)
                    }) { "OWN_WORK_DATABASE_REMAINS" }
                    databaseDeleted = true
                } catch (error: Throwable) {
                    receipt.put("database_cleanup_failure_class", error.javaClass.simpleName)
                    failure = combine(failure, error)
                }
            }
            receipt.put("owner_closed", ownerClosed).put("store_closed", storeClosed)
                .put("database_creation_attempted", databaseCreationAttempted)
                .put("partial_database_creation_uncertain", databaseCreationAttempted && !ownedDatabaseCreated)
                .put("owned_database_deleted", databaseDeleted).put("owned_database_retained", ownedDatabaseCreated && !databaseDeleted)
                .put("cleanup_complete", ownerClosed && storeClosed && databaseDeleted)
                .put("new_owner_store_reopens", reopenCount).put("configuration_recreates", recreateCount)
                .put("owned_back_attempts", ownedBackAttempts).put("owned_back_ime_hide_attempts", ownedBackImeHideAttempts)
                .put("last_original_owner_readiness", lastOriginalOwnerReadiness)
                .put("last_owned_ime_visibility", lastOwnedImeVisibility).put("owned_back_readiness", ownedBackReadiness)
                .put("owned_back_events", backEventsForReceipt())
                .put("status", if (failure == null && databaseDeleted) "COMPLETE" else "FAILED")
            Stage12NativeReceiptFiles.publish(output, receipt.toString(2), failure)
        }
        failure?.let { throw it }
    }

    private fun launchOwner() {
        check(scenario == null && activity == null)
        scenario = ActivityScenario.launch(Intent().setComponent(ComponentName(QA, ComponentActivity::class.java.name)))
        installOwnerContent()
        await { activity?.lifecycle?.currentState == Lifecycle.State.RESUMED && activity?.window?.decorView?.hasWindowFocus() == true }
    }

    private fun installOwnerContent() {
        requireNotNull(scenario).onActivity { owner ->
            activity = owner
            work = ViewModelProvider(owner, WorkSetupViewModel.Factory(ownStore.workConfiguration,
                ownStore.workCatalog, ownStore.objectives, clock, { nextUuid("work") }))
                .get(key("work"), WorkSetupViewModel::class.java)
            availability = ViewModelProvider(owner, AvailabilityViewModel.Factory(ownStore.workConfiguration,
                ownStore.availabilityWindows, ownStore.v2Shifts, ownStore.shiftActuals, ownStore.independentExtraWork,
                ownStore.medicalLeaves, ownStore.vacations, clock, zone, { nextUuid("availability") }))
                .get(key("availability"), AvailabilityViewModel::class.java)
            recurring = ViewModelProvider(owner, V2RecurringPlanViewModel.Factory(ownStore.workConfiguration,
                ownStore.workCatalog, ownStore.objectives, ownStore.shifts, ownStore.medicalLeaves,
                ownStore.recurringPlans, ownStore.recurringShiftWriter, clock, zone, UuidProvider { nextUuid("recurring") }))
                .get(key("recurring"), V2RecurringPlanViewModel::class.java)
            owner.setContent {
                val w by work.uiState.collectAsState()
                val a by availability.uiState.collectAsState()
                val r by recurring.uiState.collectAsState()
                val aa = AvailabilityActions.from(availability)
                val rr = V2RecurringActions.from(recurring)
                val ww = WorkSetupActions.from(work).copy(
                    openAvailability = aa.openOverview,
                    openRecurringPlans = { rr.openPlans(w.rootState) },
                )
                LaunchedEffect(w.rootState) { rr.resume(w.rootState) }
                MiGuardiaTheme {
                    Box(Modifier.fillMaxSize()) {
                        when {
                            r.isBlocking -> V2RecurringPlanSurfaceHost(r, rr)
                            a.isBlocking -> AvailabilitySurfaceHost(a, aa)
                            dayContext != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                AvailabilityDaySection(requireNotNull(dayContext), a, aa)
                            }
                            w.rootState is WorkSetupState.FreshInstall || w.rootState is WorkSetupState.Loading ||
                                w.rootState is WorkSetupState.LoadError -> WorkSetupStartupScreen(w, ww)
                            w.surface != WorkSetupSurface.NONE -> WorkSetupSurfaceHost(w, ww)
                            w.rootState is WorkSetupState.V2NeedsFirstSet -> V2FirstWorkSetGuide(ww.openFirstWorkSet)
                            else -> Unit // A closed product surface is not a fake Calendar.
                        }
                    }
                }
            }
        }
    }

    private fun exerciseWork(sectorCase: String) {
        requireSameDate()
        waitTag("work-sector-selection")
        listOf("Vigilancia privada", "Policía", "Enfermería", "Medicina").forEach {
            compose.onAllNodesWithText(it).assertCountEquals(1)
        }
        compose.onNodeWithTag("work-sector-continue").assertIsNotEnabled()
        touch("work-sector-${sector.name.lowercase()}")
        compose.onNodeWithTag("work-sector-${sector.name.lowercase()}").assertIsSelected()
        touch("work-sector-continue")
        await { work.uiState.value.rootState is WorkSetupState.V2NeedsFirstSet && !work.uiState.value.isSavingSector }
        val configured = checkNotNull(readFrame().history)
        check(configured.timeline.revisions.size == 1 && configured.timeline.revisions.single().value.sector == sector &&
            configured.timeline.revisions.single().effectiveFrom == anchor) { "OWN_WORK_SECTOR_NOT_PERSISTED" }
        fact("initial-sector", "UI_COMPOSE", listOf(sectorCase), JSONObject().put("four_exact_labels", true).put("explicit_selection", true))
        fact("initial-sector-persisted", "ROOM_ISOLATED", listOf(sectorCase), JSONObject().put("timeline_uuid", configured.timeline.id.toString()).put("revisions", 1))
        touchText("Crear primer lugar")
        fill("work-place-name", placeName())
        fill("work-place-abbreviation", "OWN")
        // Abbreviation error is exercised through actual fields, never VM mutation.
        fill("work-place-abbreviation", "X")
        compose.onNodeWithTag("work-place-continue").assertIsNotEnabled()
        fill("work-place-abbreviation", "OWN")
        openAdvancedTo("work-place-address")
        fill("work-place-address", "Own synthetic address $run")
        fillTextField("Nota personal y privada (opcional)", "Own synthetic private note $run")
        touchText("¿En este lugar contás horas nocturnas?")
        fill("work-time-start", "2215"); fill("work-time-end", "0645")
        touchText("Distinguir sábados"); touchText("Distinguir domingos")
        touchText("Mostrar esas horas aparte en el Resumen")
        touchText("Distinguir feriados"); touchText("Mostrar feriados aparte en el Resumen")
        val beforeDraft = readFrame()
        recreateOwner()
        check(work.uiState.value.placeDraft.name == placeName() && work.uiState.value.placeDraft.abbreviation == "OWN" &&
            work.uiState.value.placeDraft.address == "Own synthetic address $run" &&
            work.uiState.value.placeDraft.note == "Own synthetic private note $run" &&
            work.uiState.value.placeDraft.nightStart == "22:15" && work.uiState.value.placeDraft.nightEnd == "06:45") { "OWN_WORK_DRAFT_RECREATE_DIFFERS" }
        check(readFrame() == beforeDraft) { "OWN_WORK_RECREATE_WROTE_BUSINESS" }
        touch("work-place-continue")
        await { work.uiState.value.step == WorkSetupStep.TYPE_AND_TEMPLATE }
        val expectedTypeName = when (sector) {
            WorkSector.PRIVATE_SECURITY, WorkSector.POLICE -> "Guardia habitual"
            WorkSector.NURSING -> "Turno habitual"
            WorkSector.MEDICINE -> "Jornada habitual"
        }
        check(work.uiState.value.templateDraft.typeName == expectedTypeName) { "OWN_WORK_SECTOR_TYPE_SUGGESTION_DIFFERS" }
        openAdvancedTo("work-type-name")
        compose.onNodeWithTag("work-type-name").assertTextContains(expectedTypeName)
        touch("advanced-options-toggle")
        fill("work-time-start", "2410")
        fill("work-time-end", "1645")
        chooseColor()
        compose.onNodeWithTag("work-set-save").assertIsNotEnabled()
        fill("work-time-start", "0815")
        touch("work-set-save")
        await { work.uiState.value.rootState is WorkSetupState.V2Ready && work.uiState.value.surface == WorkSetupSurface.COMPLETION && !work.uiState.value.isSavingWorkSet }
        val first = readFrame()
        val catalog = checkNotNull(first.catalog)
        check(catalog.workPlaces.size == 1 && catalog.workTypes.size == 1 && catalog.workTemplates.size == 1 &&
            catalog.workplaceRuleRevisions.size == 1 && first.objectives.size == 1) { "OWN_WORK_FIRST_SET_COUNTS" }
        val objective = first.objectives.single()
        val template = catalog.workTemplates.single()
        val type = catalog.workTypes.single()
        check(type.name == expectedTypeName && type.sector == sector && type.behavior == WorkTypeBehavior.ACTIVE_WORK) {
            "OWN_WORK_SECTOR_TYPE_PERSISTENCE_DIFFERS"
        }
        check(objective.fullName == placeName() && objective.abbreviation == "OWN" &&
            objective.address == "Own synthetic address $run" && objective.note == "Own synthetic private note $run" &&
            template.startTime == LocalTime.of(8, 15) && template.endTime == LocalTime.of(16, 45) &&
            template.colorArgb == 0xFF1E88E5.toInt() &&
            template.workPlaceId == catalog.workPlaces.single().id && template.workTypeId == catalog.workTypes.single().id) { "OWN_WORK_FIRST_SET_VALUES" }
        val rules = catalog.workplaceRuleRevisions.single().rules
        check(rules.nightHours == NightHoursRule.Defined(LocalTime.of(22, 15), LocalTime.of(6, 45), true, true) &&
            rules.weekend == WeekendRule.Defined(WeekendDays.SATURDAY_AND_SUNDAY, true, true) &&
            rules.holiday.differentTreatment && rules.holiday.showDedicatedSummary) { "OWN_WORK_OPTIONAL_RULES_DIFFERS" }
        fact("first-catalog-set", "ROOM_ISOLATED", listOf("A023", "A024", "A025", "A026", "A027", "A028", "A029", "A030", "A031", "A032"), JSONObject()
            .put("places", 1).put("types", 1).put("templates", 1).put("start", "08:15").put("end", "16:45").put("business_sha256", digest(first.toString())))
        touchText("Agregar otro horario")
        await { work.uiState.value.surface == WorkSetupSurface.ADDITIONAL_TEMPLATE }
        fill("work-time-start", "2215"); fill("work-time-end", "0645"); chooseColor()
        touch("work-template-save")
        await { work.uiState.value.surface == WorkSetupSurface.COMPLETION && !work.uiState.value.isSavingTemplate }
        val second = readFrame()
        check(second.catalog?.workTemplates?.size == 2 && second.catalog?.workPlaces == catalog.workPlaces &&
            second.catalog?.workTypes == catalog.workTypes && second.objectives == first.objectives &&
            second.catalog?.workTemplates?.single { it.id == template.id } == template) { "OWN_WORK_ADDITIONAL_TEMPLATE_CHANGED_CATALOG" }
        check(second.catalog?.workTemplates?.single { it.id != template.id }?.let {
            it.workPlaceId == template.workPlaceId && it.workTypeId == template.workTypeId &&
                it.startTime == LocalTime.of(22, 15) && it.endTime == LocalTime.of(6, 45) && it.colorArgb == 0xFF1E88E5.toInt()
        } == true) { "OWN_WORK_ADDITIONAL_TEMPLATE_VALUES" }
        touchText("Agregar otro lugar")
        await { work.uiState.value.surface == WorkSetupSurface.ADDITIONAL_PLACE }
        fill("work-place-name", "$run new place")
        fill("work-place-abbreviation", "NEW")
        val originalWorkOwner = checkNotNull(activity)
        val dirtyPlaceDraft = work.uiState.value.placeDraft
        val workBackFormReady = {
            val state = work.uiState.value
            state.surface == WorkSetupSurface.ADDITIONAL_PLACE && state.placeDraft == dirtyPlaceDraft &&
                state.hasUnconfirmedDraft && !state.showDiscardConfirmation &&
                !state.isSavingWorkSet && !state.creationBusy
        }
        val workConfirmationVisible = { compose.onNodeWithText("¿Descartar borrador?").isDisplayed() }
        // Actual Android back must first offer a conscious decision.
        nativeBackAndAwait("WORK_FIRST_DISCARD", workBackFormReady, workConfirmationVisible) {
            work.uiState.value.showDiscardConfirmation
        }
        touchText("Seguir editando")
        awaitOriginalFormAfterKeepEditing("WORK_KEEP_EDITING", originalWorkOwner,
            dialogAbsent = { compose.onAllNodesWithText("¿Descartar borrador?").fetchSemanticsNodes().isEmpty() },
            formReady = {
                val state = work.uiState.value
                state.surface == WorkSetupSurface.ADDITIONAL_PLACE && !state.showDiscardConfirmation &&
                    state.placeDraft == dirtyPlaceDraft && !state.isSavingWorkSet && !state.creationBusy &&
                    exists("work-place-name") && exists("work-place-abbreviation")
            })
        compose.onNodeWithTag("work-place-name").assertIsEnabled().assertTextContains(dirtyPlaceDraft.name)
        compose.onNodeWithTag("work-place-abbreviation").assertIsEnabled().assertTextContains(dirtyPlaceDraft.abbreviation)
        check(work.uiState.value.placeDraft.name == "$run new place") { "OWN_WORK_KEEP_EDITING_LOST_DRAFT" }
        nativeBackAndAwait("WORK_SECOND_DISCARD", workBackFormReady, workConfirmationVisible) {
            work.uiState.value.showDiscardConfirmation
        }
        touchText("Descartar")
        await { work.uiState.value.surface == WorkSetupSurface.NONE }
        check(readFrame() == second) { "OWN_WORK_DISCARD_WROTE_CATALOG" }
        enterOverview()
        touchText("Agregar otro lugar")
        fill("work-place-name", "$run second place"); fill("work-place-abbreviation", "TWO")
        touch("work-place-continue")
        await { work.uiState.value.surface == WorkSetupSurface.COMPLETION && !work.uiState.value.isSavingWorkSet }
        val added = readFrame()
        check(added.catalog?.workPlaces?.size == 2 && added.catalog?.workTypes == second.catalog?.workTypes &&
            added.catalog?.workTemplates == second.catalog?.workTemplates && added.objectives.containsAll(second.objectives)) { "OWN_WORK_ADDITIONAL_PLACE_DIFFERS" }
        fact("catalog-addition-discard-recreate", "UI_COMPOSE", listOf("A035", "A036", "A038"), JSONObject()
            .put("places", 2).put("templates", 2).put("discard_preserved_business", true))
    }

    private fun exerciseAvailability() {
        enterOverview(); openAdvancedTo("work-setup-availability"); touch("work-setup-availability")
        await { availability.uiState.value.surface == AvailabilitySurface.OVERVIEW && availability.uiState.value.loadState == AvailabilityLoadState.CONTENT }
        val original = checkNotNull(readFrame().history).timeline.revisions.single().value
        val labels = listOf(AvailabilityLabel.PASSIVE_GUARD, AvailabilityLabel.AVAILABLE_FOR_CALL, AvailabilityLabel.ON_CALL_RETAINER)
        val windows = mutableListOf<AvailabilityWindowRecord>()
        labels.forEachIndexed { index, label ->
            configureAvailability(label)
            val changed = checkNotNull(readFrame().history).timeline.revisionAt(anchor)!!.value
            check(changed.sector == original.sector && changed.hoursReference == original.hoursReference &&
                changed.hoursReferenceStartedOn == original.hoursReferenceStartedOn && changed.availabilityLabel == label) { "OWN_WORK_AVAILABILITY_CONFIGURATION_DIFFERS" }
            windows.forEach { expected -> check(readFrame().windows.single { it.id == expected.id } == expected) { "OWN_WORK_LABEL_REWROTE_WINDOW" } }
            closeAvailability()
            val date = anchor.plusDays(2L + index * 5L)
            enterDay(date)
            touch("availability-add-$date")
            await { availability.uiState.value.surface == AvailabilitySurface.WINDOW_EDITOR }
            val recordId = checkNotNull(availability.uiState.value.windowDraft).recordId
            fill("availability-start", "0800")
            val endDate = if (index == 1) date.plusDays(1) else date
            val endTime = if (index == 1) "0900" else "1200"
            fill("availability-end-date", endDate.toString()); fill("availability-end", endTime)
            if (index == 0) {
                val before = readFrame(); recreateOwner()
                check(availability.uiState.value.windowDraft?.recordId == recordId && availability.uiState.value.windowDraft?.ownerDate == date) { "OWN_WORK_WINDOW_RECREATE_IDENTITY" }
                check(readFrame() == before) { "OWN_WORK_WINDOW_RECREATE_WROTE" }
            }
            touch("availability-window-review")
            await { availability.uiState.value.surface == AvailabilitySurface.WINDOW_REVIEW && !availability.uiState.value.isSaving }
            val minutes = if (index == 1) 1500L else 240L
            check(availability.uiState.value.windowReview?.record?.durationMinutes == minutes) { "OWN_WORK_WINDOW_REVIEW_MINUTES" }
            touch("availability-window-save")
            await { availability.uiState.value.surface == AvailabilitySurface.NONE && !availability.uiState.value.isSaving }
            val saved = checkNotNull(db { ownStore.availabilityWindows.get(recordId) })
            check(saved.ownerLocalDate == date && saved.zoneId == zone && saved.labelSnapshot == label.displayName &&
                saved.start == date.atTime(8, 0).atZone(zone).toInstant() &&
                saved.end == endDate.atTime(if (index == 1) 9 else 12, 0).atZone(zone).toInstant() &&
                saved.durationMinutes == minutes && minutes == Stage12OwnedWorkFamilyOracle.minutes(date, LocalTime.of(8, 0),
                    endDate, LocalTime.of(if (index == 1) 9 else 12, 0), zone)) { "OWN_WORK_WINDOW_PERSISTENCE_DIFFERS" }
            windows += saved
            fact("availability-label-and-window-$index", "ROOM_ISOLATED", listOf("A112", "A113", "A114", "A117", "A118", "A119"),
                JSONObject().put("label", label.name).put("record_uuid", saved.id.toString()).put("minutes", minutes)
                    .put("civil_days", Stage12OwnedWorkFamilyOracle.civilDays(date, endDate)))
            enterOverview(); openAdvancedTo("work-setup-availability"); touch("work-setup-availability")
            await { availability.uiState.value.surface == AvailabilitySurface.OVERVIEW }
        }
        closeAvailability()
        val last = windows.last(); enterDay(last.ownerLocalDate)
        touch("availability-correct-${last.id}")
        fill("availability-end", "1300"); touch("availability-window-review")
        await { availability.uiState.value.surface == AvailabilitySurface.WINDOW_REVIEW && !availability.uiState.value.isSaving }
        touch("availability-window-save")
        await { availability.uiState.value.surface == AvailabilitySurface.NONE && !availability.uiState.value.isSaving }
        val corrected = checkNotNull(db { ownStore.availabilityWindows.get(last.id) })
        check(corrected.id == last.id && corrected.ownerLocalDate == last.ownerLocalDate && corrected.labelSnapshot == last.labelSnapshot &&
            corrected.configurationRevisionId == last.configurationRevisionId && corrected.createdAt == last.createdAt &&
            corrected.durationMinutes == 300L && corrected.updatedAt > last.updatedAt) { "OWN_WORK_CORRECTION_CHANGED_HISTORY" }
        touch("availability-delete-${last.id}")
        await { availability.uiState.value.surface == AvailabilitySurface.DELETE_CONFIRMATION && availability.uiState.value.deleteExpectation != null && !availability.uiState.value.isSaving }
        touchText("Cancelar")
        await { availability.uiState.value.surface == AvailabilitySurface.NONE }
        check(db { ownStore.availabilityWindows.get(last.id) } == corrected) { "OWN_WORK_CANCEL_DELETE_CHANGED_RECORD" }
        touch("availability-delete-${last.id}")
        await { availability.uiState.value.deleteExpectation != null && !availability.uiState.value.isSaving }
        touch("availability-delete-confirm")
        await { availability.uiState.value.surface == AvailabilitySurface.NONE && !availability.uiState.value.isSaving }
        check(db { ownStore.availabilityWindows.get(last.id) } == null) { "OWN_WORK_CONFIRMED_DELETE_REMAINS" }
        check(readFrame().windows.toSet() == windows.dropLast(1).toSet()) { "OWN_WORK_DELETE_CHANGED_OTHER_WINDOWS" }
        // Actual overlap rejection before any commit; the first window is preserved.
        enterDay(windows.first().ownerLocalDate)
        val beforeOverlap = readFrame()
        touch("availability-add-${windows.first().ownerLocalDate}")
        fill("availability-start", "0900"); fill("availability-end", "1100")
        touch("availability-window-review")
        await { !availability.uiState.value.isSaving && availability.uiState.value.message != null }
        check(availability.uiState.value.surface == AvailabilitySurface.WINDOW_EDITOR && availability.uiState.value.windowReview == null) { "OWN_WORK_OVERLAP_NOT_REJECTED" }
        check(readFrame() == beforeOverlap) { "OWN_WORK_OVERLAP_WROTE" }
        val availabilityDraftBeforeBack = availability.uiState.value
        nativeBackAndAwait("AVAILABILITY_DISCARD",
            formReady = { availability.uiState.value == availabilityDraftBeforeBack &&
                availability.uiState.value.surface == AvailabilitySurface.WINDOW_EDITOR &&
                !availability.uiState.value.isSaving && !availability.uiState.value.showDiscardConfirmation },
            confirmationVisible = { compose.onNodeWithTag("availability-discard-confirm").isDisplayed() }) {
            availability.uiState.value.showDiscardConfirmation
        }
        touch("availability-discard-confirm"); await { availability.uiState.value.surface == AvailabilitySurface.NONE }
        enterOverview(); openAdvancedTo("work-setup-availability"); touch("work-setup-availability")
        await { availability.uiState.value.surface == AvailabilitySurface.OVERVIEW }
        val retained = readFrame().windows
        configureAvailability(null)
        check(readFrame().windows == retained && !availability.uiState.value.canCreateOn(anchor.plusDays(2))) { "OWN_WORK_NO_USE_REMOVED_HISTORY" }
        closeAvailability(); enterDay(anchor.plusDays(2))
        compose.onNodeWithTag("availability-add-${anchor.plusDays(2)}").assertDoesNotExist()
        fact("availability-correct-delete-disable-overlap", "ROOM_ISOLATED", listOf("A115", "A122", "A123", "A124", "A126"),
            JSONObject().put("retained_windows", retained.size).put("cancel_delete_unchanged", true).put("overlap_rejected_without_write", true))
    }

    private fun configureAvailability(label: AvailabilityLabel?) {
        requireSameDate()
        val previous = checkNotNull(readFrame().history).timeline.revisionAt(anchor)!!.value
        touch("availability-configure")
        await { availability.uiState.value.surface == AvailabilitySurface.CONFIG_EDITOR }
        touch("availability-label-${label?.name ?: "none"}")
        fill("availability-config-date", anchor.toString())
        touch("availability-config-review")
        await { availability.uiState.value.surface == AvailabilitySurface.CONFIG_REVIEW }
        check(availability.uiState.value.configurationReview?.revision?.value?.availabilityLabel == label) { "OWN_WORK_LABEL_REVIEW_DIFFERS" }
        touch("availability-config-save")
        await { availability.uiState.value.surface == AvailabilitySurface.OVERVIEW && !availability.uiState.value.isSaving &&
            availability.uiState.value.source?.labelOn(anchor) == label }
        val current = checkNotNull(readFrame().history).timeline.revisionAt(anchor)!!.value
        check(current.sector == previous.sector && current.hoursReference == previous.hoursReference &&
            current.hoursReferenceStartedOn == previous.hoursReferenceStartedOn && current.availabilityLabel == label) {
            "OWN_WORK_AVAILABILITY_CHANGED_REFERENCE"
        }
    }

    private fun exerciseRecurring() {
        enterOverview(); openAdvancedTo("work-setup-recurring-plans"); touch("work-setup-recurring-plans")
        await { recurring.uiState.value.stage == V2RecurringStage.PLANS && recurring.uiState.value.plansReadSuccessfully && !recurring.uiState.value.isLoading }
        check(recurring.uiState.value.plans.isEmpty()) { "OWN_WORK_NEW_PLANS_NOT_EMPTY" }
        touchHeaderClose("Planes recurrentes")
        recordAction("WAIT_HEADER_CLOSED", "Planes recurrentes")
        await { recurring.uiState.value.stage == V2RecurringStage.IDLE &&
            compose.onAllNodes(headerCloseMatcher("Planes recurrentes")).fetchSemanticsNodes().isEmpty() }
        val catalog = checkNotNull(readFrame().catalog)
        val template = catalog.workTemplates.single { it.startTime == LocalTime.of(8, 15) }
        val savedPlans = mutableListOf<RecurringPlanAggregate>()
        Stage12OwnedWorkFamilyOracle.Pattern.entries.forEachIndexed { index, pattern ->
            requireSameDate()
            val first = anchor.plusMonths(1L + index * 4L).withDayOfMonth(1)
            val start = if (pattern == Stage12OwnedWorkFamilyOracle.Pattern.EVERY_N_WEEKS)
                first.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)) else first
            val end = start.plusDays(if (pattern == Stage12OwnedWorkFamilyOracle.Pattern.MONTHLY) 80 else 35)
            val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
            val interval = if (pattern == Stage12OwnedWorkFamilyOracle.Pattern.EVERY_N_DAYS) 3 else 2
            val expectedDates = Stage12OwnedWorkFamilyOracle.dates(pattern, start, end, weekdays, interval)
            directEntry("RECURRING_CREATE_FORM") { recurring.openCreate(work.uiState.value.rootState) }
            await { recurring.uiState.value.stage == V2RecurringStage.FORM && !recurring.uiState.value.isLoading && recurring.uiState.value.templateOptions.isNotEmpty() }
            touch("v2-recurring-template-${template.id}")
            fill("v2-recurring-start-date", start.format(INPUT_DATE)); fill("v2-recurring-end-date", end.format(INPUT_DATE))
            DayOfWeek.entries.forEach { day ->
                if ((day in recurring.uiState.value.weekdays) != (day in weekdays)) touch("v2-recurring-weekday-${day.name}")
            }
            openAdvancedTo("v2-recurring-position"); fill("v2-recurring-position", "$run-${pattern.name}")
            touch("v2-recurring-pattern-${pattern.name}")
            if (pattern in setOf(Stage12OwnedWorkFamilyOracle.Pattern.EVERY_N_DAYS, Stage12OwnedWorkFamilyOracle.Pattern.EVERY_N_WEEKS)) {
                val before = readFrame()
                fill("v2-recurring-interval", "0"); touch("v2-recurring-review")
                await { !recurring.uiState.value.isLoading && recurring.uiState.value.errorMessage != null }
                check(recurring.uiState.value.stage == V2RecurringStage.FORM && recurring.uiState.value.preview == null && readFrame() == before) { "OWN_WORK_INVALID_INTERVAL_WROTE" }
                fill("v2-recurring-interval", interval.toString())
            }
            if (pattern == Stage12OwnedWorkFamilyOracle.Pattern.MONTHLY) {
                touch("v2-recurring-ordinal-LAST"); touch("v2-recurring-monthly-day-FRIDAY")
            }
            val planId = checkNotNull(recurring.uiState.value.draftPlanId)
            val beforeReview = readFrame()
            touch("v2-recurring-review")
            await { recurring.uiState.value.stage == V2RecurringStage.PREVIEW && !recurring.uiState.value.isLoading }
            val preview = checkNotNull(recurring.uiState.value.preview)
            check(preview.dates == expectedDates && preview.canConfirm && readFrame() == beforeReview) { "OWN_WORK_PREVIEW_OR_DATES_DIFFERS" }
            val previousSequence = recurring.uiState.value.successSequence
            touch("v2-recurring-save")
            await { recurring.uiState.value.stage == V2RecurringStage.IDLE && !recurring.uiState.value.isSaving &&
                recurring.uiState.value.successSequence == previousSequence + 1 && recurring.uiState.value.errorMessage == null }
            val aggregate = checkNotNull(db { ownStore.recurringPlans.getPlan(planId) })
            val revision = aggregate.revisions.single()
            check(aggregate.plan.sector == sector && revision.templateId == template.id && revision.positionSnapshot == "$run-${pattern.name}" &&
                revision.startTimeSnapshot == template.startTime && revision.endTimeSnapshot == template.endTime &&
                aggregate.occurrences.map { it.localDate }.sorted() == expectedDates &&
                aggregate.occurrences.all { it.state == RecurringOccurrenceState.AUTOMATIC && it.shiftId != null }) { "OWN_WORK_PLAN_PERSISTENCE_DIFFERS" }
            val writes = readFrame().shifts.associateBy { it.shift.id }
            aggregate.occurrences.forEach { occurrence ->
                val write = checkNotNull(writes[checkNotNull(occurrence.shiftId)]) { "OWN_WORK_OCCURRENCE_WITHOUT_SHIFT" }
                check(write.shift.localStartDate == occurrence.localDate && write.snapshot.templateId == template.id &&
                    write.shift.position == "$run-${pattern.name}" &&
                    db { ownStore.recurringPlans.getOccurrenceForShift(write.shift.id) } == occurrence) { "OWN_WORK_OCCURRENCE_LINK_DIFFERS" }
            }
            savedPlans += aggregate
            check(readFrame().plans.toSet() == savedPlans.toSet()) { "OWN_WORK_NEW_PLAN_CHANGED_PREVIOUS_PLANS" }
            fact("recurring-${pattern.name.lowercase()}", "ROOM_ISOLATED", listOf("A046", "A047", "A048", "A049", "A050", "A051", "A052", "A057", "A058"), JSONObject()
                .put("plan_uuid", planId.toString()).put("pattern", pattern.name).put("dates", JSONArray(expectedDates.map(LocalDate::toString)))
                .put("occurrences", aggregate.occurrences.size).put("business_sha256", digest(readFrame().toString())))
        }
        // Actual discard decision from a new, dirty form; no fifth plan may appear.
        val beforeDiscard = readFrame()
        directEntry("RECURRING_CREATE_DISCARD") { recurring.openCreate(work.uiState.value.rootState) }
        await { recurring.uiState.value.stage == V2RecurringStage.FORM && !recurring.uiState.value.isLoading }
        touch("v2-recurring-template-${template.id}")
        val originalRecurringOwner = checkNotNull(activity)
        val dirtyRecurringDraft = recurring.uiState.value
        val recurringBackFormReady = { recurring.uiState.value == dirtyRecurringDraft &&
            recurring.uiState.value.stage == V2RecurringStage.FORM &&
            !recurring.uiState.value.isLoading && !recurring.uiState.value.isSaving }
        val recurringConfirmationVisible = { compose.onNodeWithTag("v2-recurring-discard-dialog").isDisplayed() }
        nativeBackAndAwait("RECURRING_FIRST_DISCARD", recurringBackFormReady, recurringConfirmationVisible) {
            recurring.uiState.value.stage == V2RecurringStage.CONFIRM_DISCARD
        }
        touchText("Seguir editando")
        awaitOriginalFormAfterKeepEditing("RECURRING_KEEP_EDITING", originalRecurringOwner,
            dialogAbsent = { !exists("v2-recurring-discard-dialog") },
            formReady = {
                recurring.uiState.value == dirtyRecurringDraft &&
                    recurring.uiState.value.stage == V2RecurringStage.FORM &&
                    !recurring.uiState.value.isLoading && !recurring.uiState.value.isSaving &&
                    exists("v2-recurring-start-date") && exists("v2-recurring-end-date")
            })
        nativeBackAndAwait("RECURRING_SECOND_DISCARD", recurringBackFormReady, recurringConfirmationVisible) {
            recurring.uiState.value.stage == V2RecurringStage.CONFIRM_DISCARD
        }
        touch("v2-recurring-confirm-discard"); await { recurring.uiState.value.stage == V2RecurringStage.IDLE }
        check(readFrame() == beforeDiscard) { "OWN_WORK_RECURRING_DISCARD_WROTE" }
        enterOverview(); openAdvancedTo("work-setup-recurring-plans"); touch("work-setup-recurring-plans")
        await { recurring.uiState.value.plansReadSuccessfully && !recurring.uiState.value.isLoading }
        check(recurring.uiState.value.plans.toSet() == savedPlans.toSet()) { "OWN_WORK_UI_PLAN_LIST_DIFFERS" }
        touch("v2-recurring-plan-${savedPlans.first().plan.id}")
        await { recurring.uiState.value.stage == V2RecurringStage.PLAN_DETAIL }
        check(recurring.uiState.value.selectedPlan == savedPlans.first()) { "OWN_WORK_PLAN_DETAIL_IDENTITY" }
        fact("recurring-list-and-discard", "UI_COMPOSE", listOf("A044", "A045", "A061"), JSONObject().put("plans", 4).put("discard_unchanged", true))
    }

    private fun enterOverview() {
        directEntry("WORK_OVERVIEW") { dayContext = null; work.openOverview() }
        waitTag("work-setup-overview")
    }

    private fun enterDay(date: LocalDate) {
        directEntry("AVAILABILITY_DAY_SECTION", date) { dayContext = date }
        await { availability.uiState.value.loadState == AvailabilityLoadState.CONTENT }
    }

    private fun closeAvailability() {
        check(availability.uiState.value.surface == AvailabilitySurface.OVERVIEW && !availability.uiState.value.isSaving)
        touchHeaderClose("Guardias pasivas y disponibilidad")
        recordAction("WAIT_HEADER_CLOSED", "Guardias pasivas y disponibilidad")
        await { availability.uiState.value.surface == AvailabilitySurface.NONE &&
            compose.onAllNodes(headerCloseMatcher("Guardias pasivas y disponibilidad")).fetchSemanticsNodes().isEmpty() }
    }

    private fun headerCloseMatcher(title: String) = hasText("Cerrar", substring = false) and hasClickAction() and
        hasAnySibling(hasText(title, substring = false))

    private fun touchHeaderClose(title: String) {
        recordAction("TOUCH_HEADER_CLOSE", title)
        val matcher = headerCloseMatcher(title)
        await { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(matcher).assertCountEquals(1)
        compose.onNode(matcher).assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
    }

    private fun recreateOwner() {
        val old = checkNotNull(activity)
        requireNotNull(scenario).recreate()
        installOwnerContent()
        check(activity !== old && old.isDestroyed) { "OWN_WORK_ACTIVITY_NOT_RECREATED" }
        recreateCount++
        fact("component-configuration-recreate", "ACTIVITY_NATIVE", emptyList(), JSONObject()
            .put("new_activity_instance", true).put("same_process_viewmodels_may_be_retained", true).put("process_death", false))
    }

    private fun reopenAndCompare(phase: String) {
        val before = readFrame()
        val previousOwner = activity
        val previousStore = ownStore
        closeOwner()
        ownStore.close(); store = null
        check(Files.isRegularFile(databasePath.toPath(), NOFOLLOW_LINKS)) { "OWN_WORK_DATABASE_MISSING_AT_REOPEN" }
        store = LocalDataStore.create(context, databaseName)
        dayContext = null
        launchOwner()
        await { work.uiState.value.rootState is WorkSetupState.V2Ready && availability.uiState.value.loadState == AvailabilityLoadState.CONTENT }
        check(readFrame() == before) { "OWN_WORK_NEW_STORE_REOPEN_DIFFERS" }
        check(ownStore !== previousStore && activity !== previousOwner) { "OWN_WORK_REOPEN_REUSED_INSTANCE" }
        reopenCount++
        fact("new-owner-room-reopen-$phase", "ROOM_ISOLATED", emptyList(), JSONObject()
            .put("new_owner_and_store", true).put("business_sha256", digest(before.toString())))
    }

    private fun closeOwner() {
        val owned = scenario
        val old = activity
        if (owned != null) {
            owned.close()
            check(old == null || old.isDestroyed) { "OWN_WORK_OWNER_CLOSE_UNCERTAIN" }
            scenario = null
            activity = null
        }
    }

    private fun directEntry(kind: String, date: LocalDate? = null, action: () -> Unit) {
        directEntries.put(JSONObject().put("kind", kind).put("date", date?.toString() ?: JSONObject.NULL)
            .put("main_activity_navigation_claim", false))
        requireNotNull(scenario).onActivity { action() }
    }

    private data class Frame(val history: WorkConfigurationHistory?, val catalog: WorkCatalog?, val objectives: List<Objective>,
                             val windows: List<AvailabilityWindowRecord>, val plans: List<RecurringPlanAggregate>, val shifts: List<V2ShiftWrite>) {
        // EffectiveDateTimeline/PerPeriodHoursValues are wrappers with identity
        // equality. Compare their public immutable VALUES, never object addresses.
        private fun values(): List<Any?> = listOf(history?.let {
            listOf(it.timeline.id, it.timeline.revisions, it.perPeriodHoursValues.entries)
        }, catalog, objectives, windows, plans, shifts)
        override fun equals(other: Any?): Boolean = other is Frame && values() == other.values()
        override fun hashCode(): Int = values().hashCode()
        override fun toString(): String = values().toString()
    }

    private fun readFrame(): Frame = db {
        val history = ownStore.workConfiguration.get()
        val timeline = history?.timeline?.id
        Frame(history, timeline?.let { ownStore.workCatalog.observeCatalog(it, sector).first() },
            ownStore.objectives.observeAll().first().sortedBy { it.id },
            timeline?.let { ownStore.availabilityWindows.observeAll(it, sector).first().sortedBy { it.id } }.orEmpty(),
            timeline?.let { ownStore.recurringPlans.observePlans(it, sector).first().sortedBy { it.plan.id } }.orEmpty(),
            timeline?.let { ownStore.v2Shifts.observeAll(it, sector).first().sortedBy { it.shift.id } }.orEmpty())
    }

    private fun <T> db(body: suspend () -> T): T = runBlocking { withTimeout(30_000) { body() } }
    private fun key(kind: String) = "stage12:$run:${sector.name}:$kind"
    private fun nextUuid(kind: String) = UUID.nameUUIDFromBytes("$run/${sector.name}/$kind/${sequence.incrementAndGet()}".toByteArray(Charsets.UTF_8))
    private fun placeName() = "$run ${sector.name} first"
    private fun chooseColor() { touch("work-template-color"); touch("common-color-azul"); touchText("Usar color") }
    private fun requireSameDate() { check(LocalDate.now(clock) == anchor) { "OWN_WORK_CIVIL_DATE_CHANGED" } }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun await(condition: () -> Boolean) = compose.waitUntil(30_000) { assertQa(); condition() }
    private fun waitTag(tag: String) = await { exists(tag) }
    private fun assertQa() {
        check(context.packageName == QA && device.isScreenOn && device.currentPackageName == QA &&
            !(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) { "OWN_WORK_QA_FOCUS_OR_LOCK" }
    }
    private fun touch(tag: String) {
        recordAction("TOUCH_TAG", tag)
        waitTag(tag)
        val node = compose.onNodeWithTag(tag)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
    }
    private fun touchText(text: String) {
        recordAction("TOUCH_TEXT", text)
        await { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
    }
    private fun fill(tag: String, value: String) {
        recordAction("FILL_TAG", tag)
        waitTag(tag)
        val node = compose.onNodeWithTag(tag)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().performTextReplacement(value)
        hideOwnedKeyboard()
    }
    private fun fillTextField(label: String, value: String) {
        recordAction("FILL_LABEL", label)
        val node = compose.onNode(hasSetTextAction() and hasText(label))
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().performTextReplacement(value)
        hideOwnedKeyboard()
    }
    private fun nativeBackAndAwait(target: String, formReady: () -> Boolean,
        confirmationVisible: () -> Boolean, confirmationRequested: () -> Boolean) {
        val observation = Stage12OwnedBackEventSemantics.Observation()
        ownedBackEvents += target to observation
        recordAction("CHECK_BACK_PRECONDITIONS", target)
        Stage12OwnedBackEventSemantics.execute(observation, formReady,
            confirmation = {
                val requested = confirmationRequested()
                val visible = confirmationVisible()
                if (requested == visible) requested else null
            },
            nativeCall = {
                recordAction("NATIVE_BACK", target)
                ownedBackAttempts++
                // UiAutomator's Boolean acknowledges a matching accessibility event, not injection.
                device.pressBack()
            },
            awaitUi = { condition ->
                recordAction("WAIT_BACK_CONFIRMATION", target)
                await(condition)
            })
    }

    private fun backEventsForReceipt(): JSONArray = JSONArray(ownedBackEvents.map { (target, observed) ->
        JSONObject().put("boundary", target).put("phase", observed.phase)
            .put("api_return_available", observed.returnAvailable).apply {
                observed.apiReturn?.let { put("api_return_boolean", it) }
                observed.confirmationBefore?.let { put("confirmation_before", it) }
                observed.confirmationUi?.let { put("confirmation_ui", it) }
                observed.escapedErrorClass?.let { put("escaped_error_class", it) }
                observed.failureCode?.let { put("failure_code", it) }
            }
    })

    private fun awaitOriginalFormAfterKeepEditing(target: String, originalOwner: ComponentActivity,
        dialogAbsent: () -> Boolean, formReady: () -> Boolean) {
        recordAction("WAIT_ORIGINAL_FORM_AND_WINDOW", target)
        await { formReady() && dialogAbsent() && originalOwnerReady(originalOwner, observeIme = false) }
        // This is an explicit own-IME input action; it may change input state and timing.
        recordAction("HIDE_OWN_IME_ONCE", target)
        ownedBackImeHideAttempts++
        hideOwnedKeyboard(originalOwner)
        recordAction("WAIT_ORIGINAL_FORM_IME_HIDDEN", target)
        await { formReady() && dialogAbsent() && originalOwnerReady(originalOwner, observeIme = true) }
        ownedBackReadiness.put(JSONObject().put("boundary", target).put("same_original_owner", true)
            .put("form_draft_preserved", true).put("dialog_absent", true).put("owner_resumed_focused", true)
            .put("owned_ime_visibility", "HIDDEN").put("owned_ime_hide_attempts", 1)
            .put("input_action_may_change_state_and_timing", true))
    }

    private fun originalOwnerReady(originalOwner: ComponentActivity, observeIme: Boolean): Boolean {
        var ready = false
        requireNotNull(scenario).onActivity { owner ->
            val decor = owner.window.decorView
            val sameOwner = owner === originalOwner && activity === originalOwner
            val ownerReady = sameOwner && !owner.isDestroyed && owner.lifecycle.currentState == Lifecycle.State.RESUMED &&
                decor.isAttachedToWindow && decor.isLaidOut && decor.hasWindowFocus()
            lastOriginalOwnerReadiness = when {
                !sameOwner -> "OWNER_CHANGED"
                ownerReady -> "ORIGINAL_RESUMED_FOCUSED"
                else -> "NOT_READY"
            }
            if (observeIme) {
                // No native insets, unsupported visibility API or unsettled owner means UNKNOWN, never HIDDEN.
                lastOwnedImeVisibility = if (ownerReady && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val insets = decor.rootWindowInsets
                    when {
                        insets == null -> "UNKNOWN"
                        insets.isVisible(WindowInsets.Type.ime()) -> "VISIBLE"
                        else -> "HIDDEN"
                    }
                } else "UNKNOWN"
            }
            ready = ownerReady && (!observeIme || Stage12OwnedBackEventSemantics.knownHidden(lastOwnedImeVisibility))
        }
        return ready
    }

    private fun hideOwnedKeyboard(expectedOwner: ComponentActivity? = null) {
        requireNotNull(scenario).onActivity { owner ->
            check(expectedOwner == null || owner === expectedOwner && activity === expectedOwner) { "OWN_WORK_IME_OWNER_CHANGED" }
            owner.currentFocus?.clearFocus()
            (owner.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(owner.window.decorView.windowToken, 0)
        }
    }
    private fun openAdvancedTo(tag: String) { if (!exists(tag)) touch("advanced-options-toggle"); waitTag(tag) }
    private fun recordAction(kind: String, target: String) {
        actionOrdinal += 1; actionKind = kind; actionTargetSha256 = digest(target)
    }
    private fun fact(key: String, level: String, related: List<String>, observed: JSONObject) {
        facts.put(JSONObject().put("assertion_key", "owned-work.$key").put("level", level)
            .put("related_case_ids_not_full_credit", JSONArray(related)).put("observed", observed).put("check", "PASS"))
    }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun combine(primary: Throwable?, extra: Throwable): Throwable = primary?.also { if (it !== extra) it.addSuppressed(extra) } ?: extra

    companion object {
        private const val QA = "com.blackatsystems.miguardia.qa"
        private val INPUT_DATE = DateTimeFormatter.ofPattern("ddMMyyyy")
    }
}

/** Pure test-only protocol used by the real Android helper and the compiled-bytecode host checks. */
internal object Stage12OwnedBackEventSemantics {
    class Observation {
        @JvmField var phase: String = "PRECONDITIONS"
        @JvmField var returnAvailable: Boolean = false
        @JvmField var apiReturn: Boolean? = null
        @JvmField var confirmationBefore: Boolean? = null
        @JvmField var confirmationUi: Boolean? = null
        @JvmField var escapedErrorClass: String? = null
        @JvmField var failureCode: String? = null
    }

    @JvmStatic fun knownHidden(visibility: String?): Boolean = visibility == "HIDDEN"

    @JvmStatic fun execute(observed: Observation, formReady: () -> Boolean?, confirmation: () -> Boolean?,
        nativeCall: () -> Boolean, awaitUi: (() -> Boolean) -> Unit) {
        try {
            if (formReady() != true) {
                observed.failureCode = "FORM_NOT_READY"
                error("OWN_WORK_BACK_FORM_NOT_READY")
            }
            observed.confirmationBefore = confirmation()
            if (observed.confirmationBefore != false) {
                observed.failureCode = "CONFIRMATION_PREEXISTING_OR_UNKNOWN"
                error("OWN_WORK_BACK_CONFIRMATION_PREEXISTING_OR_UNKNOWN")
            }
            observed.phase = "NATIVE_CALL"
            observed.apiReturn = nativeCall()
            observed.returnAvailable = true
            observed.phase = "WAIT_CONFIRMATION_UI"
            awaitUi {
                observed.confirmationUi = confirmation()
                observed.confirmationUi == true
            }
            if (observed.confirmationUi != true) {
                observed.failureCode = "UI_NOT_CONFIRMED"
                error("OWN_WORK_BACK_UI_NOT_CONFIRMED")
            }
            observed.phase = "UI_CONFIRMED"
        } catch (failure: Throwable) {
            observed.escapedErrorClass = failure.javaClass.simpleName
                .takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_$]{0,79}")) } ?: "OTHER"
            throw failure
        }
    }
}
