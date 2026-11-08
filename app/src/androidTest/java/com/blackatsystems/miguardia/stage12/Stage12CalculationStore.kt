package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.QaMainActivityFileContext
import com.blackatsystems.miguardia.backup.BackupViewModel
import com.blackatsystems.miguardia.backup.LocalDataMutationGate
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowMutation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowWriteResult
import com.blackatsystems.miguardia.core.domain.model.ExtraWorkClassWriteResult
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkDraft
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkMutation
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSelection
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkWriteResult
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ShiftActualClassSelection
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDifferenceChoice
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftActualFragmentDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftActualWriteResult
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.buildIndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.buildShiftActualSaveMutation
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationRuntime
import com.blackatsystems.miguardia.notifications.NotificationSystemAccess
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.reports.LocalReportGenerator
import com.blackatsystems.miguardia.reports.ReportArtifact
import com.blackatsystems.miguardia.reports.ReportArtifactStore
import com.blackatsystems.miguardia.reports.ReportDestination
import com.blackatsystems.miguardia.reports.ReportDestinationWriter
import com.blackatsystems.miguardia.reports.ReportPhotoStager
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.availability.AvailabilityViewModel
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsViewModel
import com.blackatsystems.miguardia.ui.help.HelpViewModel
import com.blackatsystems.miguardia.ui.help.OnboardingVersionStore
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasViewModel
import com.blackatsystems.miguardia.ui.management.ManualLoadNotes
import com.blackatsystems.miguardia.ui.management.UuidProvider
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadViewModel
import com.blackatsystems.miguardia.ui.management.V2RecurringPlanViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftActualViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftEditViewModel
import com.blackatsystems.miguardia.ui.nextevent.NextEventViewModel
import com.blackatsystems.miguardia.ui.notifications.NotificationViewModel
import com.blackatsystems.miguardia.ui.photos.PhotosViewModel
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.reports.ReportsViewModel
import com.blackatsystems.miguardia.ui.summary.SummaryObserver
import com.blackatsystems.miguardia.ui.summary.SummaryPreferencesStore
import com.blackatsystems.miguardia.ui.summary.SummaryViewModel
import com.blackatsystems.miguardia.ui.vacation.VacationUuidProvider
import com.blackatsystems.miguardia.ui.vacation.VacationViewModel
import com.blackatsystems.miguardia.ui.weather.WeatherViewModel
import com.blackatsystems.miguardia.ui.widget.WidgetViewModel
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupViewModel
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherRuntime
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.blackatsystems.miguardia.stage12.Stage12CalculationFixture as F

/**
 * Grafo aislado de la etapa 12.2: base Room propia (`stage12-2-<run>.db`), archivos y preferencias
 * dentro de `filesDir/stage12-2-<run>/`, y la fixture compartida sembrada por los repositorios reales.
 * Un único reloj que avanza, anclado al construir en 2026-09-30T15:00:00Z, alimenta todo el grafo.
 */
internal class Stage12CalculationStore(
    private val context: Context,
    private val runId: UUID,
    /** true: guarda con el escritor real por el selector de documentos (sesión humana). */
    realDestination: Boolean = false,
) : AutoCloseable {
    private val tag = "stage12-2-$runId"
    private val databaseName = "$tag.db"
    /** Bajo `files/reports/artifacts/` para que el FileProvider real pueda compartir el informe (como B1). */
    private val artifactsParent: File = File(context.filesDir, "reports/artifacts")
    val privateRoot: File = File(artifactsParent, tag)
    val anchoredNow: Instant = Instant.parse("2026-09-30T15:00:00Z")
    val zone: ZoneId = F.ZONE
    val clock: Clock = Clock.offset(Clock.system(F.ZONE), Duration.between(Instant.now(), anchoredNow))

    private val rejected = Collections.synchronizedList(mutableListOf<String>())
    /** Efectos externos (avisos, alarmas, etc.) que el contexto privado rechazó. */
    val rejectedEffects: List<String> get() = synchronized(rejected) { rejected.toList() }

    private val saved = Collections.synchronizedList(mutableListOf<ByteArray>())
    val savedArtifacts: List<ByteArray> get() = synchronized(saved) { saved.toList() }
    private val savedTo = Collections.synchronizedList(mutableListOf<Uri>())
    /** Destinos reales donde se guardó con éxito (sólo con `realDestination`). */
    val savedDestinations: List<Uri> get() = synchronized(savedTo) { savedTo.toList() }
    private val discarded = Collections.synchronizedList(mutableListOf<Uri>())
    val discardedDestinations: List<Uri> get() = synchronized(discarded) { discarded.toList() }

    private var closed = false
    private val preferenceJob: Job
    private val notificationJob: Job
    val store: LocalDataStore
    private val privateContext: PrivateFileContext
    private val profile: GuardProfileStore
    private val weatherPreferences: WeatherPreferencesStore

    private val calendarFactory: CalendarViewModel.Factory
    private val nextFactory: NextEventViewModel.Factory
    private val manualFactory: V2ManualShiftLoadViewModel.Factory
    private val editFactory: V2ShiftEditViewModel.Factory
    private val actualFactory: V2ShiftActualViewModel.Factory
    private val recurringFactory: V2RecurringPlanViewModel.Factory
    private val workFactory: WorkSetupViewModel.Factory
    private val hoursFactory: HoursAndExtrasViewModel.Factory
    private val availabilityFactory: AvailabilityViewModel.Factory
    private val summaryFactory: SummaryViewModel.Factory
    private val reportsFactory: ReportsViewModel.Factory
    private val photosFactory: PhotosViewModel.Factory
    private val vacationFactory: VacationViewModel.Factory
    private val exceptionsFactory: ExceptionsViewModel.Factory
    private val weatherFactory: WeatherViewModel.Factory
    private val notificationsFactory: NotificationViewModel.Factory
    private val helpFactory: HelpViewModel.Factory

    init {
        check(!privateRoot.exists()) { "STAGE12_2_PRIVATE_ROOT_EXISTS" }
        check(!context.getDatabasePath(databaseName).exists()) { "STAGE12_2_DATABASE_EXISTS" }
        check(artifactsParent.isDirectory || artifactsParent.mkdirs()) { "STAGE12_2_ARTIFACTS_PARENT" }
        check(privateRoot.mkdirs()) { "STAGE12_2_PRIVATE_ROOT_NOT_CREATED" }
        listOf("files", "cache", "no_backup", "datastore").forEach { check(File(privateRoot, it).mkdirs()) }
        privateContext = PrivateFileContext(context, privateRoot) { rejected += it }
        store = LocalDataStore.create(context, databaseName = databaseName)
        val job = SupervisorJob(); preferenceJob = job
        val scope = CoroutineScope(job + Dispatchers.IO)
        fun pref(name: String) = File(privateRoot, "datastore/$name.preferences_pb")
        val notificationPreferences = NotificationPreferencesStore(pref("notification"), scope)
        weatherPreferences = WeatherPreferencesStore(pref("weather"), scope)
        val summaryPreferences = SummaryPreferencesStore(pref("summary"), scope)
        profile = GuardProfileStore(pref("profile"), scope)
        val weather = WeatherRuntime(privateContext, weatherPreferences, store.objectives, clock)
        val notifications = NotificationRuntime(privateContext, store, notificationPreferences, weather)
        notificationJob = checkNotNull(notifications.scope.coroutineContext[Job])
        val gate = LocalDataMutationGate()
        val photoStore = SchedulePhotoFileStore(privateContext)
        val notes = ManualLoadNotes(privateContext, store.shiftNotes, store.shiftSituations, gate)
        val generator = LocalReportGenerator(store.monthlyReportSnapshots, profile,
            ReportPhotoStager(privateContext, photoStore, clock), ReportArtifactStore(privateContext, clock),
            clock = clock, zoneId = zone)
        val real = ReportDestinationWriter(context.contentResolver)
        val destination = object : ReportDestination {
            override suspend fun save(artifact: ReportArtifact, destination: Uri) {
                if (realDestination) real.save(artifact, destination)
                saved += artifact.file.readBytes()
                if (realDestination) savedTo += destination
            }
            override suspend fun discard(destination: Uri): Boolean {
                discarded += destination
                return if (realDestination) real.discard(destination) else true
            }
        }
        val s = store
        calendarFactory = CalendarViewModel.Factory(s.shifts, s.explicitDayStatuses, s.medicalLeaves, s.holidays,
            s.vacations, clock, s.shiftSituations, photoStore, gate, s.calendarBatches)
        nextFactory = NextEventViewModel.Factory(s.v2Shifts, s.availabilityWindows, s.explicitDayStatuses, s.vacations,
            s.medicalLeaves, s.shiftActuals, s.independentExtraWork, s.workConfiguration, clock, zone)
        manualFactory = V2ManualShiftLoadViewModel.Factory(s.workConfiguration, s.workCatalog, s.objectives, s.shifts,
            s.medicalLeaves, s.v2Shifts, s.shiftActuals, s.explicitDayStatuses, clock, zone,
            UuidProvider { UUID.randomUUID() }, s.calendarBatches, notes)
        editFactory = V2ShiftEditViewModel.Factory(s.workConfiguration, s.workCatalog, s.objectives, s.shifts,
            s.medicalLeaves, s.v2Shifts, s.shiftActuals, s.recurringPlans, clock)
        actualFactory = V2ShiftActualViewModel.Factory(s.shiftActuals, clock) { UUID.randomUUID() }
        recurringFactory = V2RecurringPlanViewModel.Factory(s.workConfiguration, s.workCatalog, s.objectives, s.shifts,
            s.medicalLeaves, s.recurringPlans, s.recurringShiftWriter, clock, zone, UuidProvider { UUID.randomUUID() })
        workFactory = WorkSetupViewModel.Factory(s.workConfiguration, s.workCatalog, s.objectives, clock,
            { UUID.randomUUID() }, weather::clearCacheForObjective)
        hoursFactory = HoursAndExtrasViewModel.Factory(s.workConfiguration, s.workCatalog, s.objectives, s.v2Shifts,
            s.shiftActuals, s.independentExtraWork, s.medicalLeaves, s.vacations, clock, zone) { UUID.randomUUID() }
        availabilityFactory = AvailabilityViewModel.Factory(s.workConfiguration, s.availabilityWindows, s.v2Shifts,
            s.shiftActuals, s.independentExtraWork, s.medicalLeaves, s.vacations, clock, zone) { UUID.randomUUID() }
        summaryFactory = SummaryViewModel.Factory(SummaryObserver(s.workConfiguration, s.workCatalog, s.v2Shifts,
            s.shiftActuals, s.independentExtraWork, s.availabilityWindows, s.holidays, s.medicalLeaves, s.vacations,
            s.explicitDayStatuses, clock, zone), summaryPreferences, clock, zone)
        reportsFactory = ReportsViewModel.Factory(generator, destination, s.schedulePhotos, profile, clock, zone)
        photosFactory = PhotosViewModel.Factory(s.schedulePhotos, s.objectives, photoStore, gate)
        vacationFactory = VacationViewModel.Factory(s.vacations, clock, VacationUuidProvider { UUID.randomUUID() })
        exceptionsFactory = ExceptionsViewModel.Factory(s.holidays, s.shiftNotes, s.shifts, photoStore, clock,
            { UUID.randomUUID() }, gate)
        weatherFactory = WeatherViewModel.Factory(weather, s.shifts, s.vacations, s.medicalLeaves)
        notificationsFactory = NotificationViewModel.Factory(notificationPreferences, s.shiftNotificationConfigs,
            NotificationSystemAccess(privateContext), notifications)
        val application = context.applicationContext as MiGuardiaApplication
        helpFactory = HelpViewModel.Factory(object : OnboardingVersionStore {
            override val state = application.onboardingPreferences.state
            override suspend fun completeAtLeast(version: Int, stillApplicable: () -> Boolean): Int {
                rejected += "help.complete"
                throw SecurityException("STAGE12_2_EXTERNAL_EFFECT_EXCLUDED:help.complete")
            }
        })
    }

    /** Nombre visible del perfil para los informes; por defecto no hay nombre. */
    suspend fun setProfileDisplayName(name: String) { profile.save(name) }

    /** Todo PDF/XLSX regular bajo la raíz privada (artefactos reales generados por Informes). */
    fun generatedArtifacts(): List<File> = privateRoot.walkTopDown()
        .filter { it.isFile && (it.name.endsWith(".pdf") || it.name.endsWith(".xlsx")) }.toList()

    suspend fun seed() {
        check(!closed)
        check(!weatherPreferences.current().enabled) { "STAGE12_2_WEATHER_ENABLED" }
        check(store.workConfiguration.get() == null) { "STAGE12_2_STORE_NOT_EMPTY" }
        val revision = F.configuration.timeline.revisions.single()
        store.workConfiguration.createInitial(F.TIMELINE_ID, revision)
        val history = checkNotNull(store.workConfiguration.get())
        check(history.timeline.id == F.TIMELINE_ID && history.timeline.revisions == listOf(revision)) {
            "STAGE12_2_CONFIGURATION_FORCED"
        }

        val place = F.catalog.workPlaces.single()
        val type = F.catalog.workTypes.single()
        val template = F.catalog.workTemplates.single { it.id == F.TEMPLATE_ID }
        val nightTemplate = F.catalog.workTemplates.single { it.id == F.NIGHT_TEMPLATE_ID }
        val objective = Objective(F.OBJECTIVE_ID, F.PLACE_NAME, F.PLACE_ABBREVIATION, F.Canary.ADDRESS, null, true,
            F.CREATED_AT, F.CREATED_AT)
        store.workCatalog.createFirstWorkSet(FirstWorkSet(objective, place, F.catalog.workplaceRuleRevisions.single(),
            ResolvedWorkConfigurationRevision.resolve(history, F.MONTH.atDay(1)), type, template))
        store.workCatalog.createWorkTemplate(nightTemplate)
        val catalog = store.workCatalog.observeCatalog(F.TIMELINE_ID, F.SECTOR).first()
        check(catalog.workPlaces.map { it.id } == listOf(F.WORK_PLACE_ID) &&
            catalog.workTypes.map { it.id } == listOf(F.WORK_TYPE_ID) &&
            catalog.workTemplates.map { it.id }.toSet() == setOf(F.TEMPLATE_ID, F.NIGHT_TEMPLATE_ID)) {
            "STAGE12_2_CATALOG_FORCED"
        }
        val storedObjective = checkNotNull(store.objectives.getById(F.OBJECTIVE_ID))
        check(storedObjective.fullName == F.PLACE_NAME && storedObjective.abbreviation == F.PLACE_ABBREVIATION &&
            storedObjective.address == F.Canary.ADDRESS) { "STAGE12_2_OBJECTIVE_FORCED" }

        val helping = ExtraWorkClass.create(F.HELPING_CLASS_ID, F.TIMELINE_ID, F.SECTOR, F.HELPING_CLASS_NAME,
            helpsMeetHoursReference = true, showDedicatedSummary = true, timestamp = F.CREATED_AT)
        val notHelping = ExtraWorkClass.create(F.NOT_HELPING_CLASS_ID, F.TIMELINE_ID, F.SECTOR,
            F.NOT_HELPING_CLASS_NAME, helpsMeetHoursReference = false, showDedicatedSummary = true,
            timestamp = F.CREATED_AT)
        check(helping.name == F.HELPING_CLASS_NAME && notHelping.name == F.NOT_HELPING_CLASS_NAME) {
            "STAGE12_2_CLASS_NAME_FORCED"
        }
        check(store.shiftActuals.saveExtraWorkClass(null, helping) == ExtraWorkClassWriteResult.Saved(helping))
        check(store.shiftActuals.saveExtraWorkClass(null, notHelping) == ExtraWorkClassWriteResult.Saved(notHelping))

        F.shifts.forEach { store.v2Shifts.insert(it) }
        F.shifts.forEach { write ->
            val found = store.v2Shifts.getShift(write.shift.id)
            check(found is V2ShiftLookup.V2) { "STAGE12_2_SHIFT_MISSING:${write.shift.id}" }
            val shift = found.write.shift
            check(shift.id == write.shift.id && shift.startAt == write.shift.startAt &&
                shift.endAt == write.shift.endAt && shift.position == write.shift.position &&
                shift.objectiveAddressSnapshot == F.Canary.ADDRESS) { "STAGE12_2_SHIFT_FORCED:${write.shift.id}" }
        }

        val fixtureActual = F.dayActual
        val expectation = checkNotNull(store.shiftActuals.getExpectation(F.DAY_SHIFT_ID))
        check(expectation.previousActual == null)
        val draft = ShiftActualDraft(fixtureActual.record.actualStart, fixtureActual.record.actualEnd,
            fixtureActual.record.differenceReason, fixtureActual.record.explanation,
            ShiftActualDifferenceChoice.EXTRA_CLASS, ShiftActualClassSelection.Existing(helping),
            fixtureActual.extraIntervals.map { ShiftActualFragmentDraft(it.id, it.start, it.end) })
        val mutation = checkNotNull(buildShiftActualSaveMutation(expectation, draft, clock, F.CREATED_AT))
        check(store.shiftActuals.save(mutation) is ShiftActualWriteResult.Saved) { "STAGE12_2_ACTUAL_REJECTED" }

        store.shiftNotes.saveWithAttachments(F.shiftNote, emptyList())

        val independent = F.independentExtra
        val selection = IndependentExtraWorkSelection(
            ResolvedWorkConfigurationRevision.resolve(history, independent.ownerLocalDate), place, storedObjective,
            type, null, notHelping)
        val built = buildIndependentExtraWorkRecord(
            IndependentExtraWorkDraft(independent.id, independent.ownerLocalDate, independent.zoneId,
                independent.start, independent.end, independent.snapshot.colorArgb, independent.snapshot.position),
            selection, clock, F.CREATED_AT)
        check(built.copy(createdAt = independent.createdAt, updatedAt = independent.updatedAt) == independent) {
            "STAGE12_2_INDEPENDENT_FORCED:$built"
        }
        val extraExpectation = store.independentExtraWork.captureExpectation(null, selection, independent.start,
            independent.end, independent.ownerLocalDate, independent.ownerLocalDate)
        check(store.independentExtraWork.applyMutation(IndependentExtraWorkMutation(extraExpectation, built,
            overlappingWorkConfirmed = false, protectedDateConfirmed = false)) is IndependentExtraWorkWriteResult.Saved) {
            "STAGE12_2_INDEPENDENT_REJECTED"
        }

        val window = F.availability
        val windowExpectation = store.availabilityWindows.captureExpectation(null,
            ResolvedWorkConfigurationRevision.resolve(history, window.ownerLocalDate), window.start, window.end)
        check(store.availabilityWindows.applyMutation(AvailabilityWindowMutation(windowExpectation, window)) is
            AvailabilityWindowWriteResult.Saved) { "STAGE12_2_AVAILABILITY_REJECTED" }

        store.medicalLeaves.create(F.medicalLeave)
        verifySeed()
    }

    private suspend fun verifySeed() {
        val shifts = store.v2Shifts.observeAll(F.TIMELINE_ID, F.SECTOR).first()
        check(shifts.map { Triple(it.shift.id, it.shift.startAt, it.shift.endAt) }.toSet() ==
            F.shifts.map { Triple(it.shift.id, it.shift.startAt, it.shift.endAt) }.toSet() && shifts.size == 2) {
            "STAGE12_2_READBACK_SHIFTS"
        }
        val actual = checkNotNull(store.shiftActuals.getExpectation(F.DAY_SHIFT_ID)?.previousActual)
        val interval = actual.extraIntervals.single()
        val expectedInterval = F.dayActual.extraIntervals.single()
        check(actual.record.actualStart == F.dayActual.record.actualStart &&
            actual.record.actualEnd == F.dayActual.record.actualEnd &&
            interval.id == expectedInterval.id && interval.start == expectedInterval.start &&
            interval.end == expectedInterval.end && interval.extraWorkClassId == F.HELPING_CLASS_ID &&
            interval.classNameSnapshot == F.HELPING_CLASS_NAME && interval.helpsMeetHoursReferenceSnapshot &&
            interval.showDedicatedSummarySnapshot) { "STAGE12_2_READBACK_ACTUAL:$actual" }
        val independent = checkNotNull(store.independentExtraWork.get(F.INDEPENDENT_EXTRA_ID))
        check(independent.start == F.independentExtra.start && independent.end == F.independentExtra.end &&
            independent.extraWorkClassId == F.NOT_HELPING_CLASS_ID &&
            independent.snapshot.className == F.NOT_HELPING_CLASS_NAME &&
            !independent.snapshot.helpsMeetHoursReference) { "STAGE12_2_READBACK_INDEPENDENT:$independent" }
        val window = checkNotNull(store.availabilityWindows.get(F.AVAILABILITY_ID))
        check(window.start == F.availability.start && window.end == F.availability.end) {
            "STAGE12_2_READBACK_AVAILABILITY"
        }
        val leaves = store.medicalLeaves.observeIntersecting(F.MONTH.atDay(1), F.MONTH.atEndOfMonth()).first()
        val leave = leaves.single()
        check(leave.id == F.MEDICAL_LEAVE_ID && leave.startDate == F.MEDICAL_DATE &&
            leave.endDateInclusive == F.MEDICAL_DATE && leave.privateNote == F.Canary.MEDICAL_NOTE) {
            "STAGE12_2_READBACK_MEDICAL"
        }
        val note = checkNotNull(store.shiftNotes.getById(F.SHIFT_NOTE_ID))
        check(note.shiftId == F.DAY_SHIFT_ID && note.body == F.Canary.SHIFT_NOTE) { "STAGE12_2_READBACK_NOTE" }
    }

    @Composable
    fun Host() {
        val application = context.applicationContext as MiGuardiaApplication
        MiGuardiaApp(
            calendarViewModel = viewModel(factory = calendarFactory),
            nextEventViewModel = viewModel(factory = nextFactory),
            v2ManualShiftLoadViewModel = viewModel(factory = manualFactory),
            v2ShiftEditViewModel = viewModel(factory = editFactory),
            v2ShiftActualViewModel = viewModel(factory = actualFactory),
            v2RecurringPlanViewModel = viewModel(factory = recurringFactory),
            exceptionsViewModel = viewModel(factory = exceptionsFactory),
            vacationViewModel = viewModel(factory = vacationFactory),
            photosViewModel = viewModel(factory = photosFactory),
            notificationViewModel = viewModel(factory = notificationsFactory),
            weatherViewModel = viewModel(factory = weatherFactory),
            // Igual que MainActivity: singletons de la Application (ver riesgos en el informe).
            widgetViewModel = viewModel(factory = WidgetViewModel.Factory(application,
                application.widgetPreferences, application.widgetRuntime)),
            workSetupViewModel = viewModel(factory = workFactory),
            hoursAndExtrasViewModel = viewModel(factory = hoursFactory),
            availabilityViewModel = viewModel(factory = availabilityFactory),
            summaryViewModel = viewModel(factory = summaryFactory),
            reportsViewModel = viewModel(factory = reportsFactory),
            backupViewModel = viewModel(factory = BackupViewModel.Factory(application.backupCoordinator,
                application.startupRecoveryGate, application.backupCoordinator::discardIncompleteDocument)),
            helpViewModel = viewModel(factory = helpFactory),
        )
    }

    override fun close() {
        if (!closed) {
            closed = true
            runBlocking {
                notificationJob.cancelAndJoin()
                preferenceJob.cancelAndJoin()
            }
            store.close()
        }
        context.deleteDatabase(databaseName)
        // Room deja un archivo de bloqueo propio en la caché real (`<base>.lck`); es de esta corrida.
        File(context.cacheDir, "$databaseName.lck").takeIf { it.exists() }?.let { lock ->
            check(lock.delete()) { "STAGE12_2_LOCK_NOT_DELETED" }
        }
        if (privateRoot.exists()) check(privateRoot.deleteRecursively()) { "STAGE12_2_PRIVATE_ROOT_NOT_DELETED" }
        val databaseDir = context.getDatabasePath(databaseName).parentFile
        val leftovers = listOfNotNull(databaseDir, context.filesDir, context.cacheDir, artifactsParent)
            .flatMap { dir -> dir.listFiles().orEmpty().filter { it.name.contains(runId.toString()) } }
        check(leftovers.isEmpty()) { "STAGE12_2_CLEANUP_LEFTOVERS:${leftovers.map { it.name }}" }
    }

    /** Archivos dentro de la raíz privada; avisos/alarmas y otros efectos externos se rechazan. */
    private class PrivateFileContext(
        base: Context,
        private val root: File,
        private val record: (String) -> Unit,
    ) : ContextWrapper(base), QaMainActivityFileContext {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files")
        override fun getCacheDir(): File = File(root, "cache")
        override fun getNoBackupFilesDir(): File = File(root, "no_backup")
        override fun getDataDir(): File = root
        override fun rejectFixtureExternalEffect(operation: String): Nothing {
            record(operation)
            throw SecurityException("STAGE12_2_EXTERNAL_EFFECT_EXCLUDED:$operation")
        }
    }
}
