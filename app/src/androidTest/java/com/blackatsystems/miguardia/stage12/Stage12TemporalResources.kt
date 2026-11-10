package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.net.Uri
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.QaMainActivityGraph
import com.blackatsystems.miguardia.StartupRecoveryState
import com.blackatsystems.miguardia.backup.LocalDataMutationGate
import com.blackatsystems.miguardia.notifications.*
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.reports.*
import com.blackatsystems.miguardia.ui.availability.AvailabilityViewModel
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsViewModel
import com.blackatsystems.miguardia.ui.help.*
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasViewModel
import com.blackatsystems.miguardia.ui.management.*
import com.blackatsystems.miguardia.ui.nextevent.NextEventViewModel
import com.blackatsystems.miguardia.ui.notifications.NotificationViewModel
import com.blackatsystems.miguardia.ui.photos.PhotosViewModel
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.ui.summary.*
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.vacation.VacationUuidProvider
import com.blackatsystems.miguardia.ui.vacation.VacationViewModel
import com.blackatsystems.miguardia.ui.weather.WeatherViewModel
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupViewModel
import com.blackatsystems.miguardia.weather.*
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Construct this holder before prepare: a failed constructor must not lose its original jobs. */
internal class Stage12TemporalResources(
    private val framework: Context,
    private val opened: Stage12TemporalStore,
    private val run: String,
    private val owner: Any,
    private val generation: UUID,
    private val retain: (Throwable) -> Unit,
    private val observe: (QaMainActivityGraph.Event) -> Unit,
    private val incoming: (MainActivity, QaMainActivityGraph.IncomingDestination) -> Unit = { _, _ ->
        error("TEMPORAL_MAIN_INCOMING_NOT_ADMITTED")
    },
    private val published: (MainActivity) -> Unit = { error("TEMPORAL_MAIN_PUBLICATION_NOT_ADMITTED") },
) {
    private val journal = Stage12PreparationJournal(PROFILE.map { Stage12PreparationJournal.Operation(it) }, retain)
    private val failure = Stage12TemporalFailure(retain)
    private var attempted = false
    private var closeAttempted = false
    private var parent: Job? = null
    private var notificationParent: Job? = null
    private var notificationConstructorUnknown = false
    private var closed = false
    lateinit var context: Stage12TemporalPrivateContext; private set
    lateinit var photoStore: SchedulePhotoFileStore; private set
    lateinit var factories: QaMainActivityGraph.Factories; private set
    lateinit var graph: QaMainActivityGraph; private set
    val composition = Stage12TemporalComposition(framework) { failure.remember(it) }
    val projection: Map<String, Any> get() = journal.projection()

    private fun reject(operation: String): Nothing {
        val failure = SecurityException("TEMPORAL_EXTERNAL_EFFECT_EXCLUDED:$operation")
        fail(failure)
    }
    private fun fail(failure: Throwable): Nothing {
        this.failure.reject(failure)
    }
    private fun <T> step(name: String, block: () -> T): T {
        failure.requireActive()
        val value = journal.call(Stage12PreparationJournal.Operation(name), block)
        failure.requireActive()
        return value
    }
    private suspend fun <T> asyncStep(name: String, block: suspend () -> T): T {
        failure.requireActive()
        val value = journal.callSuspend(Stage12PreparationJournal.Operation(name), block)
        failure.requireActive()
        return value
    }

    suspend fun prepare(clock: Clock, zone: ZoneId, mainReceiver: Boolean) {
        try {
            failure.requireActive()
            check(!attempted && !closeAttempted) { "TEMPORAL_RESOURCES_PREPARE_REUSED" }; attempted = true
            val store = step("STORE_BINDING") { opened.requireBinding(opened.binding).also {
                check(opened.binding.run == run && framework.packageName == "com.blackatsystems.miguardia.qa")
                opened.binding.requireStored(stage12RecurringPathAccess(framework))
            } }
            val application = step("APPLICATION") { framework.applicationContext as MiGuardiaApplication }
            step("STARTUP_GATE") {
                check(application.startupRecoveryGate.state.value == StartupRecoveryState.Ready)
                check(application.accessLockCoordinator.state.value.allowsSensitiveContent)
            }
            asyncStep("ONBOARDING_GATE") {
                if (mainReceiver) {
                    val state = application.onboardingPreferences.state.first()
                    check(state is OnboardingStoreState.Ready && state.completedVersion >= OnboardingPreferencesStore.CURRENT_VERSION)
                }
            }
            val theme = step("CAPTURE_REAL_THEME") {
                AppThemeMode.fromStorage(framework.getSharedPreferences(MainActivity.DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
                    .getString(MainActivity.APP_THEME_MODE, null))
            }
            context = step("PRIVATE_CONTEXT") { Stage12TemporalPrivateContext(framework, framework, opened.binding, ::fail) }
            val scope = step("PREFERENCE_SCOPE") {
                val job = SupervisorJob(); parent = job
                CoroutineScope(job + Dispatchers.IO + CoroutineExceptionHandler { _, error -> failure.remember(error) })
            }
            val fence = step("FILE_FENCE") { Stage12PathPolicy(opened.binding.privateRoot) }
            val notificationPreferences = step("NOTIFICATION_PREFERENCES") {
                NotificationPreferencesStore(fence.resolve("files/datastore/notification.preferences_pb"), scope)
            }
            val weatherPreferences = step("WEATHER_PREFERENCES") {
                WeatherPreferencesStore(fence.resolve("files/datastore/weather.preferences_pb"), scope)
            }
            val summaryPreferences = step("SUMMARY_PREFERENCES") {
                SummaryPreferencesStore(fence.resolve("files/datastore/summary.preferences_pb"), scope)
            }
            val profile = step("PROFILE_PREFERENCES") {
                GuardProfileStore(fence.resolve("files/datastore/profile.preferences_pb"), scope)
            }
            asyncStep("PREFERENCES_READ") {
                notificationPreferences.current()
                check(!weatherPreferences.current().enabled)
                summaryPreferences.current(); profile.current()
            }
            val weather = step("WEATHER_RUNTIME") { WeatherRuntime(context, weatherPreferences, store.objectives, clock) }
            val notifications = step("NOTIFICATION_RUNTIME") {
                notificationConstructorUnknown = true
                NotificationRuntime(context, store, notificationPreferences, weather).also {
                    notificationParent = checkNotNull(it.scope.coroutineContext[Job])
                    notificationConstructorUnknown = false
                }
            }
            val gate = step("MUTATION_GATE") { LocalDataMutationGate() }
            photoStore = step("PHOTO_STORE") { SchedulePhotoFileStore(context) }
            val notes = step("MANUAL_NOTES") { ManualLoadNotes(context, store.shiftNotes, store.shiftSituations, gate) }
            val generator = step("REPORT_GENERATOR") { LocalReportGenerator(store.monthlyReportSnapshots, profile,
                ReportPhotoStager(context, photoStore, clock), ReportArtifactStore(context, clock), clock = clock, zoneId = zone) }
            val destination = step("REPORT_DESTINATION") { object : ReportDestination {
                override suspend fun save(artifact: ReportArtifact, destination: Uri): Unit = reject("reports.save")
                override suspend fun discard(destination: Uri): Boolean = reject("reports.discard")
            } }
            val calendar = step("FACTORY_CALENDAR") { CalendarViewModel.Factory(store.shifts, store.explicitDayStatuses,
                store.medicalLeaves, store.holidays, store.vacations, clock, store.shiftSituations, photoStore, gate, store.calendarBatches) }
            val next = step("FACTORY_NEXT") { NextEventViewModel.Factory(store.v2Shifts, store.availabilityWindows,
                store.explicitDayStatuses, store.vacations, store.medicalLeaves, store.shiftActuals, store.independentExtraWork,
                store.workConfiguration, clock, zone) }
            val manual = step("FACTORY_MANUAL") { V2ManualShiftLoadViewModel.Factory(store.workConfiguration, store.workCatalog,
                store.objectives, store.shifts, store.medicalLeaves, store.v2Shifts, store.shiftActuals, store.explicitDayStatuses,
                clock, zone, UuidProvider { UUID.randomUUID() }, store.calendarBatches, notes) }
            val edit = step("FACTORY_EDIT") { V2ShiftEditViewModel.Factory(store.workConfiguration, store.workCatalog,
                store.objectives, store.shifts, store.medicalLeaves, store.v2Shifts, store.shiftActuals, store.recurringPlans, clock) }
            val actual = step("FACTORY_ACTUAL") { V2ShiftActualViewModel.Factory(store.shiftActuals, clock) { UUID.randomUUID() } }
            val recurring = step("FACTORY_RECURRING") { V2RecurringPlanViewModel.Factory(store.workConfiguration, store.workCatalog,
                store.objectives, store.shifts, store.medicalLeaves, store.recurringPlans, store.recurringShiftWriter,
                clock, zone, UuidProvider { UUID.randomUUID() }) }
            val work = step("FACTORY_WORK") { WorkSetupViewModel.Factory(store.workConfiguration, store.workCatalog,
                store.objectives, clock, { UUID.randomUUID() }, weather::clearCacheForObjective) }
            val hours = step("FACTORY_HOURS") { HoursAndExtrasViewModel.Factory(store.workConfiguration, store.workCatalog,
                store.objectives, store.v2Shifts, store.shiftActuals, store.independentExtraWork, store.medicalLeaves,
                store.vacations, clock, zone) { UUID.randomUUID() } }
            val availability = step("FACTORY_AVAILABILITY") { AvailabilityViewModel.Factory(store.workConfiguration,
                store.availabilityWindows, store.v2Shifts, store.shiftActuals, store.independentExtraWork,
                store.medicalLeaves, store.vacations, clock, zone) { UUID.randomUUID() } }
            val summary = step("FACTORY_SUMMARY") { SummaryViewModel.Factory(SummaryObserver(store.workConfiguration,
                store.workCatalog, store.v2Shifts, store.shiftActuals, store.independentExtraWork, store.availabilityWindows,
                store.holidays, store.medicalLeaves, store.vacations, store.explicitDayStatuses, clock, zone), summaryPreferences, clock, zone) }
            val reports = step("FACTORY_REPORTS") { ReportsViewModel.Factory(generator, destination, store.schedulePhotos, profile, clock, zone) }
            val photos = step("FACTORY_PHOTOS") { PhotosViewModel.Factory(store.schedulePhotos, store.objectives, photoStore, gate) }
            val vacation = step("FACTORY_VACATION") { VacationViewModel.Factory(store.vacations, clock, VacationUuidProvider { UUID.randomUUID() }) }
            val exceptions = step("FACTORY_EXCEPTIONS") { ExceptionsViewModel.Factory(store.holidays, store.shiftNotes,
                store.shifts, photoStore, clock, { UUID.randomUUID() }, gate) }
            val weatherFactory = step("FACTORY_WEATHER") { WeatherViewModel.Factory(weather, store.shifts, store.vacations, store.medicalLeaves) }
            val notificationsFactory = step("FACTORY_NOTIFICATIONS") { NotificationViewModel.Factory(notificationPreferences,
                store.shiftNotificationConfigs, NotificationSystemAccess(context), notifications) }
            val help = step("FACTORY_HELP") { HelpViewModel.Factory(object : OnboardingVersionStore {
                override val state = application.onboardingPreferences.state
                override suspend fun completeAtLeast(version: Int, stillApplicable: () -> Boolean): Int = reject("help.complete")
            }) }
            factories = step("FACTORIES_BUNDLE") { QaMainActivityGraph.Factories(calendar, next, manual, edit, actual,
                recurring, work, hours, availability, summary, reports, photos, vacation, exceptions, weatherFactory, notificationsFactory, help) }
            graph = step("GRAPH") { QaMainActivityGraph(run, owner, generation, store, context, theme, factories, composition,
                context::forActivity, observe, { failure.remember(it) }, incoming, published) }
            step("RETURN") { Unit }
        } catch (error: Throwable) { failure.reject(error) }
    }

    /** The caller must first prove destruction and completion of every actual owner job. */
    suspend fun closeAfterOwner(ownerClosureProven: Boolean) {
        try {
            check(!closeAttempted) { "TEMPORAL_AUXILIARY_CLOSE_REUSED" }; closeAttempted = true
            check(attempted && ownerClosureProven && !closed && !notificationConstructorUnknown) { "TEMPORAL_AUXILIARY_CLOSURE_UNPROVEN" }
            notificationParent?.cancelAndJoin()
            parent?.cancelAndJoin()
            check(listOfNotNull(notificationParent, parent).all { it.isCompleted })
            closed = true
        } catch (error: Throwable) { failure.reject(error) }
    }
    val closureProven: Boolean get() = closed && !notificationConstructorUnknown &&
        listOfNotNull(notificationParent, parent).all { it.isCompleted }

    companion object {
        private val PROFILE = listOf("STORE_BINDING", "APPLICATION", "STARTUP_GATE", "ONBOARDING_GATE", "CAPTURE_REAL_THEME",
            "PRIVATE_CONTEXT", "PREFERENCE_SCOPE", "FILE_FENCE", "NOTIFICATION_PREFERENCES", "WEATHER_PREFERENCES",
            "SUMMARY_PREFERENCES", "PROFILE_PREFERENCES", "PREFERENCES_READ", "WEATHER_RUNTIME", "NOTIFICATION_RUNTIME",
            "MUTATION_GATE", "PHOTO_STORE", "MANUAL_NOTES", "REPORT_GENERATOR", "REPORT_DESTINATION",
            "FACTORY_CALENDAR", "FACTORY_NEXT", "FACTORY_MANUAL", "FACTORY_EDIT", "FACTORY_ACTUAL", "FACTORY_RECURRING",
            "FACTORY_WORK", "FACTORY_HOURS", "FACTORY_AVAILABILITY", "FACTORY_SUMMARY", "FACTORY_REPORTS", "FACTORY_PHOTOS",
            "FACTORY_VACATION", "FACTORY_EXCEPTIONS", "FACTORY_WEATHER", "FACTORY_NOTIFICATIONS", "FACTORY_HELP", "FACTORIES_BUNDLE", "GRAPH", "RETURN")
    }
}
