package com.blackatsystems.miguardia

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import com.blackatsystems.miguardia.backup.BackupViewModel
import com.blackatsystems.miguardia.backup.BackupActions
import com.blackatsystems.miguardia.backup.BackupStage
import com.blackatsystems.miguardia.backup.BackupSurfaceHost
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.notifications.NotificationSystemAccess
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.availability.AvailabilityViewModel
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsViewModel
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasViewModel
import com.blackatsystems.miguardia.ui.help.HelpViewModel
import com.blackatsystems.miguardia.ui.help.HelpUiState
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadViewModel
import com.blackatsystems.miguardia.ui.management.V2RecurringPlanViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftEditViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftActualViewModel
import com.blackatsystems.miguardia.ui.nextevent.NextEventViewModel
import com.blackatsystems.miguardia.ui.notifications.NotificationViewModel
import com.blackatsystems.miguardia.ui.photos.PhotosViewModel
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.reports.ReportDestinationWriter
import com.blackatsystems.miguardia.reports.ReportsViewModel
import com.blackatsystems.miguardia.ui.summary.SummaryObserver
import com.blackatsystems.miguardia.ui.summary.SummaryViewModel
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.theme.vigiliaSystemBarStyle
import com.blackatsystems.miguardia.ui.vacation.VacationViewModel
import com.blackatsystems.miguardia.ui.weather.WeatherViewModel
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupViewModel
import com.blackatsystems.miguardia.ui.widget.WidgetViewModel
import com.blackatsystems.miguardia.widget.WidgetConfigurationActivity
import com.blackatsystems.miguardia.security.AccessLockContentGate
import com.blackatsystems.miguardia.security.AccessLockOperation
import com.blackatsystems.miguardia.security.AccessLockWindowProtection
import com.blackatsystems.miguardia.security.DeviceAuthenticator
import com.blackatsystems.miguardia.security.DeviceAuthenticationCapability
import com.blackatsystems.miguardia.security.SystemDeviceAuthenticator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.util.UUID
import com.blackatsystems.miguardia.updates.*

class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        QaInstrumentationScope.requireProductActivity(newBase)
        super.attachBaseContext(QaInstrumentationScope.wrapActivity(newBase))
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        QaInstrumentationScope.requireActivityLaunch(this, intent)
        super.startActivityForResult(intent, requestCode, options)
    }
    private var directionsConsent by mutableStateOf<DirectionsConsent?>(null)
    private fun cancelDirections() { directionsConsent?.cancel(); directionsConsent = null }
    private val updatesViewModel: UpdatesViewModel by viewModels {
        UpdatesViewModel.Factory((application as MiGuardiaApplication).updateOperations)
    }
    private val updateDocumentLauncher = registerForActivityResult(CreateUpdateDocument()) { uri ->
        updatesViewModel.documentReturned(uri?.let { AndroidUpdateDocument(applicationContext, it) })
    }
    private var calendarNavigationRequest by androidx.compose.runtime.mutableLongStateOf(0L)
    private val accessLockActivityToken = Any()
    private lateinit var accessLockAuthenticationHostId: String
    private var accessLockActivityResumed = false
    private lateinit var deviceAuthenticator: DeviceAuthenticator
    private var deviceAuthenticationCapability by mutableStateOf(
        DeviceAuthenticationCapability.NO_SECURE_CREDENTIAL,
    )

    private val notificationViewModel: NotificationViewModel by viewModels {
        val application = application as MiGuardiaApplication
        NotificationViewModel.Factory(
            preferencesStore = application.notificationPreferences,
            configs = application.localDataStore.shiftNotificationConfigs,
            systemAccess = NotificationSystemAccess(application),
            runtime = application.notificationRuntime,
        )
    }
    private val helpViewModel: HelpViewModel by viewModels {
        val application = application as MiGuardiaApplication
        HelpViewModel.Factory(application.onboardingPreferences)
    }
    private val backupViewModel: BackupViewModel by viewModels {
        val application = application as MiGuardiaApplication
        BackupViewModel.Factory(
            application.backupCoordinator,
            application.startupRecoveryGate,
            application.backupCoordinator::discardIncompleteDocument,
        )
    }
    private val weatherViewModel: WeatherViewModel by viewModels {
        val application = application as MiGuardiaApplication
        WeatherViewModel.Factory(
            runtime = application.weatherRuntime,
            shifts = application.localDataStore.shifts,
            vacations = application.localDataStore.vacations,
            medicalLeaves = application.localDataStore.medicalLeaves,
        )
    }
    private val widgetViewModel: WidgetViewModel by viewModels {
        val application = application as MiGuardiaApplication
        WidgetViewModel.Factory(
            context = application,
            preferences = application.widgetPreferences,
            runtime = application.widgetRuntime,
        )
    }
    private val nextEventViewModel: NextEventViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        NextEventViewModel.Factory(
            shifts = dataStore.v2Shifts,
            availabilityWindows = dataStore.availabilityWindows,
            explicitDayStatuses = dataStore.explicitDayStatuses,
            vacations = dataStore.vacations,
            medicalLeaves = dataStore.medicalLeaves,
            shiftActuals = dataStore.shiftActuals,
            independentExtras = dataStore.independentExtraWork,
            workConfiguration = dataStore.workConfiguration,
        )
    }
    private val photosViewModel: PhotosViewModel by viewModels {
        val miGuardiaApplication = application as MiGuardiaApplication
        val dataStore = miGuardiaApplication.localDataStore
        PhotosViewModel.Factory(
            dataStore.schedulePhotos,
            dataStore.objectives,
            SchedulePhotoFileStore(applicationContext),
            miGuardiaApplication.localDataMutationGate,
        )
    }
    private val calendarViewModel: CalendarViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        CalendarViewModel.Factory(
            shiftRepository = dataStore.shifts,
            explicitDayStatusRepository = dataStore.explicitDayStatuses,
            medicalLeaveRepository = dataStore.medicalLeaves,
            holidayRepository = dataStore.holidays,
            vacationRepository = dataStore.vacations,
            shiftSituationRepository = dataStore.shiftSituations,
            evidenceFileStore = SchedulePhotoFileStore(applicationContext),
            mutationGate = (application as MiGuardiaApplication).localDataMutationGate,
            calendarBatchRepository = dataStore.calendarBatches,
            clock = QaInstrumentationScope.calendarClock(this)
                ?: java.time.Clock.system(com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId()),
        )
    }

    private val vacationViewModel: VacationViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        VacationViewModel.Factory(dataStore.vacations)
    }

    private val v2ManualShiftLoadViewModel: V2ManualShiftLoadViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        V2ManualShiftLoadViewModel.Factory(
            calendarBatchRepository = dataStore.calendarBatches,
            noteFiles = com.blackatsystems.miguardia.ui.management.ManualLoadNotes(applicationContext, dataStore.shiftNotes, dataStore.shiftSituations,
                (application as MiGuardiaApplication).localDataMutationGate),
            configurationRepository = dataStore.workConfiguration,
            catalogRepository = dataStore.workCatalog,
            objectiveRepository = dataStore.objectives,
            shiftRepository = dataStore.shifts,
            medicalLeaveRepository = dataStore.medicalLeaves,
            v2ShiftRepository = dataStore.v2Shifts,
            shiftActualRepository = dataStore.shiftActuals,
            explicitDayStatusRepository = dataStore.explicitDayStatuses,
        )
    }

    private val v2ShiftEditViewModel: V2ShiftEditViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        V2ShiftEditViewModel.Factory(
            configurationRepository = dataStore.workConfiguration,
            catalogRepository = dataStore.workCatalog,
            objectiveRepository = dataStore.objectives,
            shiftRepository = dataStore.shifts,
            medicalLeaveRepository = dataStore.medicalLeaves,
            v2ShiftRepository = dataStore.v2Shifts,
            shiftActualRepository = dataStore.shiftActuals,
            recurringPlanRepository = dataStore.recurringPlans,
        )
    }

    private val v2ShiftActualViewModel: V2ShiftActualViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        V2ShiftActualViewModel.Factory(
            repository = dataStore.shiftActuals,
            clock = Clock.systemUTC(),
        )
    }

    private val v2RecurringPlanViewModel: V2RecurringPlanViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        V2RecurringPlanViewModel.Factory(
            configurationRepository = dataStore.workConfiguration,
            catalogRepository = dataStore.workCatalog,
            objectiveRepository = dataStore.objectives,
            shiftRepository = dataStore.shifts,
            medicalLeaveRepository = dataStore.medicalLeaves,
            recurringPlanRepository = dataStore.recurringPlans,
            recurringShiftRepository = dataStore.recurringShiftWriter,
        )
    }

    private val workSetupViewModel: WorkSetupViewModel by viewModels {
        val miGuardiaApplication = application as MiGuardiaApplication
        val dataStore = miGuardiaApplication.localDataStore
        WorkSetupViewModel.Factory(
            configurationRepository = dataStore.workConfiguration,
            catalogRepository = dataStore.workCatalog,
            objectiveRepository = dataStore.objectives,
            clock = Clock.system(AppDefaults.zoneId()),
            clearWeatherCacheForObjective = { objectiveId ->
                miGuardiaApplication.weatherRuntime.clearCacheForObjective(objectiveId)
            },
        )
    }

    private val hoursAndExtrasViewModel: HoursAndExtrasViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        HoursAndExtrasViewModel.Factory(
            configurationRepository = dataStore.workConfiguration,
            catalogRepository = dataStore.workCatalog,
            objectiveRepository = dataStore.objectives,
            shiftRepository = dataStore.v2Shifts,
            shiftActualRepository = dataStore.shiftActuals,
            independentExtraRepository = dataStore.independentExtraWork,
            medicalLeaveRepository = dataStore.medicalLeaves,
            vacationRepository = dataStore.vacations,
            clock = Clock.systemUTC(),
            zoneId = AppDefaults.zoneId(),
        )
    }

    private val availabilityViewModel: AvailabilityViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        AvailabilityViewModel.Factory(
            configurationRepository = dataStore.workConfiguration,
            repository = dataStore.availabilityWindows,
            shiftRepository = dataStore.v2Shifts,
            shiftActualRepository = dataStore.shiftActuals,
            independentExtraRepository = dataStore.independentExtraWork,
            medicalLeaveRepository = dataStore.medicalLeaves,
            vacationRepository = dataStore.vacations,
            clock = Clock.systemUTC(),
            zoneId = AppDefaults.zoneId(),
        )
    }

    private val summaryViewModel: SummaryViewModel by viewModels {
        val application = application as MiGuardiaApplication
        val dataStore = application.localDataStore
        val clock = Clock.systemUTC()
        val zoneId = AppDefaults.zoneId()
        SummaryViewModel.Factory(
            observer = SummaryObserver(
                configurations = dataStore.workConfiguration,
                catalogs = dataStore.workCatalog,
                shifts = dataStore.v2Shifts,
                actuals = dataStore.shiftActuals,
                extras = dataStore.independentExtraWork,
                availability = dataStore.availabilityWindows,
                holidays = dataStore.holidays,
                medicalLeaves = dataStore.medicalLeaves,
                vacations = dataStore.vacations,
                explicitStatuses = dataStore.explicitDayStatuses,
                clock = clock,
                zoneId = zoneId,
            ),
            preferencesStore = application.summaryPreferences,
            clock = clock,
            zoneId = zoneId,
        )
    }

    private val reportsViewModel: ReportsViewModel by viewModels {
        val application = application as MiGuardiaApplication
        ReportsViewModel.Factory(
            generator = application.reportGenerator,
            destinationWriter = ReportDestinationWriter(application.contentResolver),
            photoRepository = application.localDataStore.schedulePhotos,
            profileStore = application.guardProfile,
            clock = Clock.systemUTC(),
            zoneId = AppDefaults.zoneId(),
        )
    }

    private val exceptionsViewModel: ExceptionsViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        ExceptionsViewModel.Factory(
            holidays = dataStore.holidays,
            notes = dataStore.shiftNotes,
            shifts = dataStore.shifts,
            imageStore = SchedulePhotoFileStore(applicationContext),
            mutationGate = (application as MiGuardiaApplication).localDataMutationGate,
        )
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calendarNavigationRequest = savedInstanceState?.getLong("calendar-navigation-request", 0L) ?: 0L
        val miGuardiaApplication = application as MiGuardiaApplication
        accessLockAuthenticationHostId = savedInstanceState
            ?.getString(ACCESS_LOCK_AUTHENTICATION_HOST_ID)
            ?: UUID.randomUUID().toString()
        deviceAuthenticator = SystemDeviceAuthenticator(this) { token, result ->
            miGuardiaApplication.accessLockCoordinator.completeAuthentication(token, result)
        }
        deviceAuthenticator.attachToInFlightAuthentication(
            miGuardiaApplication.accessLockCoordinator.activeAuthenticationToken(
                accessLockAuthenticationHostId,
            ),
        )
        deviceAuthenticationCapability = deviceAuthenticator.capability()
        captureIncomingDestination(intent)
        setContent {
            val preferences = remember {
                getSharedPreferences(DISPLAY_PREFERENCES, MODE_PRIVATE)
            }
            var appThemeMode by remember {
                mutableStateOf(
                    AppThemeMode.fromStorage(preferences.getString(APP_THEME_MODE, null)),
                )
            }
            val recoveryState by miGuardiaApplication.startupRecoveryGate.state.collectAsStateWithLifecycle()
            val accessLockState by miGuardiaApplication.accessLockCoordinator.state.collectAsStateWithLifecycle()
            val pendingDestinationRequest by
                miGuardiaApplication.pendingMainDestinations.state.collectAsStateWithLifecycle()
            val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
            val updatesState by updatesViewModel.state.collectAsStateWithLifecycle()
            val workContentState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            val dataAccessReady = recoveryState == StartupRecoveryState.Ready
            val helpState = if (dataAccessReady && accessLockState.allowsSensitiveContent) {
                helpViewModel.uiState.collectAsStateWithLifecycle().value
            } else {
                HelpUiState()
            }
            val sensitiveContentReady = dataAccessReady && accessLockState.allowsSensitiveContent
            LaunchedEffect(sensitiveContentReady, helpState.canConsumePendingDestination) {
                if (!sensitiveContentReady || !helpState.canConsumePendingDestination) cancelDirections()
            }
            val pendingManualLoad = if (sensitiveContentReady) {
                v2ManualShiftLoadViewModel.uiState.collectAsStateWithLifecycle().value.isActive
            } else false
            val updatesWorkState = if (sensitiveContentReady && updatesState.open) {
                workSetupViewModel.uiState.collectAsStateWithLifecycle().value
            } else null
            LaunchedEffect(backupState.successSequence, sensitiveContentReady) {
                if (sensitiveContentReady && backupState.successSequence > 0) {
                    backupState.restoredTheme?.let { appThemeMode = it }
                    workSetupViewModel.refreshReferenceDate()
                    hoursAndExtrasViewModel.refresh()
                    availabilityViewModel.refresh()
                    notificationViewModel.refreshSystemAccess()
                    widgetViewModel.refresh()
                }
            }
            LaunchedEffect(
                recoveryState,
                sensitiveContentReady,
                accessLockState.allowsSensitiveContent,
                helpState.canConsumePendingDestination,
                pendingDestinationRequest?.generation,
                pendingManualLoad,
                updatesState.open,
            ) {
                val request = pendingDestinationRequest
                if (sensitiveContentReady && !pendingManualLoad && !updatesState.open && helpState.canConsumePendingDestination && request != null) {
                    val consumed = miGuardiaApplication.pendingMainDestinations.consume(request) {
                        handlePendingDestination(it) {
                            miGuardiaApplication.pendingMainDestinations.state.value == request &&
                                !v2ManualShiftLoadViewModel.uiState.value.isActive
                        }
                    }
                    if (consumed) {
                        setIntent(Intent(this@MainActivity, MainActivity::class.java))
                    }
                }
            }
            LaunchedEffect(recoveryState, sensitiveContentReady) {
                if (sensitiveContentReady) {
                    appThemeMode = AppThemeMode.fromStorage(
                        preferences.getString(APP_THEME_MODE, null),
                    )
                    refreshActiveSurfaces()
                }
            }
            val useDarkTheme = appThemeMode.resolve(isSystemInDarkTheme())
            LaunchedEffect(useDarkTheme, sensitiveContentReady) {
                if (sensitiveContentReady) miGuardiaApplication.widgetRuntime.refreshAll()
            }
            val systemBarStyle = vigiliaSystemBarStyle(useDarkTheme)
            SideEffect {
                window.statusBarColor = systemBarStyle.backgroundArgb
                window.navigationBarColor = systemBarStyle.backgroundArgb
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = systemBarStyle.useDarkIcons
                    isAppearanceLightNavigationBars = systemBarStyle.useDarkIcons
                }
                if (accessLockActivityResumed) {
                    AccessLockWindowProtection.applyForeground(this@MainActivity, accessLockState)
                } else {
                    AccessLockWindowProtection.protectForBackground(this@MainActivity, accessLockState)
                }
            }
            MiGuardiaTheme(
                darkTheme = useDarkTheme,
            ) {
                val consent = directionsConsent
                if (sensitiveContentReady && helpState.canConsumePendingDestination && consent != null) {
                    AlertDialog(
                        onDismissRequest = ::cancelDirections,
                        title = { Text("Abrir dirección en otra aplicación") },
                        text = { Text("Si continuás, compartirás esta dirección con otra aplicación:\n\n${consent.address}") },
                        dismissButton = { TextButton(onClick = ::cancelDirections) { Text("Cancelar") } },
                        confirmButton = { TextButton(onClick = {
                            val current = directionsConsent === consent
                            directionsConsent = null
                            consent.confirm(current && accessLockActivityResumed && updateContentAllowed()) { address ->
                                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))) }
                                catch (_: ActivityNotFoundException) { Toast.makeText(this@MainActivity, "No hay una aplicación compatible para abrir la dirección.", Toast.LENGTH_LONG).show() }
                            }
                        }) { Text("Abrir dirección") } },
                    )
                }
                if (!dataAccessReady) {
                    val protectedState = when (val recovery = recoveryState) {
                        StartupRecoveryState.Recovering -> backupState.copy(
                            isOpen = true,
                            stage = BackupStage.RECOVERING,
                            errorMessage = null,
                            recoveryRequired = true,
                        )
                        is StartupRecoveryState.Failed -> backupState.copy(
                            isOpen = true,
                            stage = BackupStage.ERROR,
                            errorMessage = recovery.message,
                            recoveryRequired = true,
                        )
                        StartupRecoveryState.Ready -> backupState
                    }
                    BackupSurfaceHost(
                        state = protectedState,
                        actions = BackupActions.from(backupViewModel).let { actions ->
                            if (!QaInstrumentationScope.isIsolated(this)) actions else actions.copy(
                                apply = { QaInstrumentationScope.requireExternalAction(this, "backup.native-apply") },
                                retryRecovery = { QaInstrumentationScope.requireExternalAction(this, "backup.native-recovery") },
                            )
                        },
                    )
                } else AccessLockContentGate(
                        state = accessLockState,
                        capability = deviceAuthenticationCapability,
                        onUnlock = { authenticate(AccessLockOperation.Unlock) },
                        onRetryStore = miGuardiaApplication.accessLockCoordinator::retryStoreRead,
                        onRepairStore = { authenticate(AccessLockOperation.RepairStore) },
                        onRetryDeviceSecurity = ::refreshDeviceAuthenticationCapability,
                        onOpenDeviceSecurity = ::openDeviceSecurity,
                    ) {
                    if (updatesState.open && updatesWorkState?.rootState is com.blackatsystems.miguardia.core.domain.work.WorkSetupState.V2Ready && updateContentAllowed()) {
                        UpdatesScreen(updatesState,
                            onSearch = updatesViewModel::checkRelease,
                            onDownload = updatesViewModel::download,
                            onSave = { updatesViewModel.prepareSave { name -> updateDocumentLauncher.launch(name) } },
                            onCancel = { updatesViewModel.cancel() },
                            onClose = updatesViewModel::close,
                        )
                    } else workContentState.SaveableStateProvider("beta-work-content") { MiGuardiaApp(
                    onSupportMiGuardia = { openSupportMiGuardia() },
                    onUpdates = { updatesViewModel.open() },
                    calendarViewModel = calendarViewModel,
                    nextEventViewModel = nextEventViewModel,
                    v2ManualShiftLoadViewModel = v2ManualShiftLoadViewModel,
                    v2ShiftEditViewModel = v2ShiftEditViewModel,
                    v2ShiftActualViewModel = v2ShiftActualViewModel,
                    v2RecurringPlanViewModel = v2RecurringPlanViewModel,
                    exceptionsViewModel = exceptionsViewModel,
                    vacationViewModel = vacationViewModel,
                    photosViewModel = photosViewModel,
                    notificationViewModel = notificationViewModel,
                    weatherViewModel = weatherViewModel,
                    widgetViewModel = widgetViewModel,
                    workSetupViewModel = workSetupViewModel,
                    hoursAndExtrasViewModel = hoursAndExtrasViewModel,
                    availabilityViewModel = availabilityViewModel,
                    summaryViewModel = summaryViewModel,
                    reportsViewModel = reportsViewModel,
                    backupViewModel = backupViewModel,
                    helpViewModel = helpViewModel,
                    calendarNavigationRequest = calendarNavigationRequest,
                    appThemeMode = appThemeMode,
                    onAppThemeModeChange = { selected ->
                        appThemeMode = selected
                        preferences.edit { putString(APP_THEME_MODE, selected.name) }
                    },
                    onWidgetReconfigure = ::openWidgetConfiguration,
                    accessLockState = accessLockState,
                    deviceAuthenticationCapability = deviceAuthenticationCapability,
                    onAccessLockActivate = { authenticate(AccessLockOperation.Activate(it)) },
                    onAccessLockTimeoutChange = { authenticate(AccessLockOperation.ChangeTimeout(it)) },
                    onAccessLockDisable = { authenticate(AccessLockOperation.Disable) },
                    onAccessLockNow = miGuardiaApplication.accessLockCoordinator::lockNow,
                    onRetryDeviceSecurity = ::refreshDeviceAuthenticationCapability,
                    onOpenDeviceSecurity = ::openDeviceSecurity,
                    ) }
                }
            }
            BackHandler(enabled = dataAccessReady && !accessLockState.allowsSensitiveContent) {
                finishAndRemoveTask()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureIncomingDestination(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("calendar-navigation-request", calendarNavigationRequest)
        outState.putString(ACCESS_LOCK_AUTHENTICATION_HOST_ID, accessLockAuthenticationHostId)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!deviceAuthenticator.handleActivityResult(requestCode, resultCode)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onStart() {
        val application = application as MiGuardiaApplication
        refreshDeviceAuthenticationCapability()
        application.accessLockCoordinator.activityStarted(
            accessLockActivityToken,
            deviceAuthenticator.deviceIsLocked(),
        )
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        accessLockActivityResumed = true
        val application = application as MiGuardiaApplication
        application.localActivityStarted()
        refreshDeviceAuthenticationCapability()
        application.accessLockCoordinator.activityResumed(deviceAuthenticator.deviceIsLocked())
        updatesViewModel.attachHost(this, ::canUseUpdates)
        AccessLockWindowProtection.applyForeground(this, application.accessLockCoordinator.state.value)
        if (!application.startupRecoveryGate.isReady ||
            !application.accessLockCoordinator.state.value.allowsSensitiveContent
        ) return
        refreshActiveSurfaces()
    }

    override fun onPause() {
        cancelDirections()
        updatesViewModel.pauseHost()
        accessLockActivityResumed = false
        val application = application as MiGuardiaApplication
        application.accessLockCoordinator.activityPausedForPrivacy()
        AccessLockWindowProtection.protectForBackground(this, application.accessLockCoordinator.state.value)
        super.onPause()
    }

    override fun onStop() {
        updatesViewModel.onBackground(isChangingConfigurations)
        (application as MiGuardiaApplication).accessLockCoordinator.activityStopped(
            accessLockActivityToken,
            isChangingConfigurations,
        )
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::deviceAuthenticator.isInitialized && deviceAuthenticator.deviceIsLocked()) {
            (application as MiGuardiaApplication).accessLockCoordinator.deviceLocked()
        }
    }

    override fun onDestroy() {
        updatesViewModel.detachHost(this)
        if (isFinishing && ::deviceAuthenticator.isInitialized) {
            val token = deviceAuthenticator.attachedAuthenticationToken()
            if (token != null) {
                deviceAuthenticator.cancelAuthentication()
                (application as MiGuardiaApplication).accessLockCoordinator.abandonAuthentication(
                    token,
                    accessLockAuthenticationHostId,
                )
            }
        }
        super.onDestroy()
    }

    private fun refreshActiveSurfaces() {
        workSetupViewModel.refreshReferenceDate()
        hoursAndExtrasViewModel.refresh()
        availabilityViewModel.refresh()
        notificationViewModel.refreshSystemAccess()
        weatherViewModel.onResume()
        widgetViewModel.refresh()
    }

    private suspend fun handlePendingDestination(source: PendingMainDestination, isCurrent: () -> Boolean) {
        val application = application as MiGuardiaApplication
        if (!application.startupRecoveryGate.isReady ||
            !application.accessLockCoordinator.state.value.allowsSensitiveContent ||
            !isCurrent()
        ) throw CancellationException("Access lock closed before resolving a destination")
        val action = source.action
        if (action !in NotificationActions) return
        if (action == ACTION_OPEN_CALENDAR) {
            calendarViewModel.clearSelectedDate()
            calendarNavigationRequest = nextCalendarNavigationRequest(calendarNavigationRequest)
            return
        }
        if (action == ACTION_OPEN_WEATHER) {
            weatherViewModel.openGlobal()
            return
        }
        if (action == ACTION_VIEW_DATE) {
            val ownerDate = source.ownerLocalDate
                ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                ?: return
            calendarViewModel.openDate(ownerDate)
            calendarNavigationRequest = nextCalendarNavigationRequest(calendarNavigationRequest)
            return
        }
        val shiftId = source.shiftId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return
        val shift = try {
            when (
                val lookup = application.localDataStore.v2Shifts.getShift(shiftId)
            ) {
                is V2ShiftLookup.V2 -> lookup.write.shift
                V2ShiftLookup.Missing -> null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (!isCurrent() ||
                !application.accessLockCoordinator.state.value.allowsSensitiveContent || !application.startupRecoveryGate.isReady) throw CancellationException("Destination superseded")
            calendarViewModel.clearSelectedDate()
            calendarNavigationRequest = nextCalendarNavigationRequest(calendarNavigationRequest)
            Toast.makeText(this, "No pudimos abrir ese destino.", Toast.LENGTH_LONG).show()
            return
        }
        if (!isCurrent() ||
                !application.accessLockCoordinator.state.value.allowsSensitiveContent || !application.startupRecoveryGate.isReady) throw CancellationException("Destination superseded")
        if (shift == null) {
            calendarViewModel.clearSelectedDate()
            calendarNavigationRequest = nextCalendarNavigationRequest(calendarNavigationRequest)
            Toast.makeText(this, "La jornada ya no está disponible.", Toast.LENGTH_LONG).show()
            return
        }
        if (!application.accessLockCoordinator.state.value.allowsSensitiveContent ||
            !isCurrent()) {
            throw CancellationException("Access lock closed while resolving a destination")
        }
        when (action) {
            // Exported entry points only navigate. Even a valid UUID/action is not
            // authority to disclose a stored address to another application.
            ACTION_VIEW_SHIFT, ACTION_DIRECTIONS -> {
                calendarViewModel.openDate(shift.localStartDate)
                calendarNavigationRequest = nextCalendarNavigationRequest(calendarNavigationRequest)
                if (action == ACTION_DIRECTIONS) {
                    val address = shift.objectiveAddressSnapshot?.takeIf(String::isNotBlank)
                    if (address != null) directionsConsent = DirectionsConsent(address)
                    else Toast.makeText(this, "Esta jornada no tiene una dirección guardada.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun captureIncomingDestination(source: Intent?) {
        cancelDirections()
        val captured = PendingMainDestination.from(source) ?: run {
            setIntent(
                Intent(this, MainActivity::class.java).apply {
                    action = source?.action
                    flags = source?.flags ?: 0
                    source?.categories?.forEach(::addCategory)
                },
            )
            return
        }
        (application as MiGuardiaApplication).pendingMainDestinations.capture(captured)
        setIntent(captured.toSanitizedIntent(this))
    }

    private fun authenticate(operation: AccessLockOperation) {
        QaInstrumentationScope.requireExternalAction(this, "security.authenticate")
        val application = application as MiGuardiaApplication
        deviceAuthenticator.attachToInFlightAuthentication(
            application.accessLockCoordinator.activeAuthenticationToken(
                accessLockAuthenticationHostId,
            ),
        )
        val capability = deviceAuthenticator.capability()
        deviceAuthenticationCapability = capability
        if (capability != DeviceAuthenticationCapability.AVAILABLE) {
            application.accessLockCoordinator.reportUnavailable(capability)
            return
        }
        val token = application.accessLockCoordinator.beginAuthentication(
            operation,
            accessLockAuthenticationHostId,
        ) ?: return
        deviceAuthenticator.authenticate(token)
    }

    private fun openDeviceSecurity() {
        QaInstrumentationScope.requireExternalAction(this, "security.settings")
        if (!deviceAuthenticator.openDeviceSecuritySettings()) {
            deviceAuthenticationCapability = DeviceAuthenticationCapability.TEMPORARILY_UNAVAILABLE
            (application as MiGuardiaApplication).accessLockCoordinator.reportUnavailable(
                DeviceAuthenticationCapability.TEMPORARILY_UNAVAILABLE,
            )
        }
    }

    private fun refreshDeviceAuthenticationCapability() {
        val application = application as MiGuardiaApplication
        deviceAuthenticationCapability = deviceAuthenticator.capability()
        if (deviceAuthenticationCapability == DeviceAuthenticationCapability.AVAILABLE) {
            if (application.accessLockCoordinator.state.value.message ==
                com.blackatsystems.miguardia.security.AccessLockMessage.NO_SECURE_CREDENTIAL
            ) {
                application.accessLockCoordinator.clearMessage()
            }
        } else {
            application.accessLockCoordinator.secureCredentialUnavailable()
        }
    }

    private fun openSupportMiGuardia() {
        val application = application as MiGuardiaApplication
        if (!accessLockActivityResumed || !application.startupRecoveryGate.isReady ||
            !application.accessLockCoordinator.state.value.allowsSensitiveContent
        ) return
        val workSetup = workSetupViewModel.uiState.value
        if (workSetup.rootState !is com.blackatsystems.miguardia.core.domain.work.WorkSetupState.V2Ready ||
            workSetup.surface != com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface.NONE ||
            workSetup.isSavingSector || workSetup.isSavingWorkSet || workSetup.isSavingTemplate ||
            !helpViewModel.uiState.value.canConsumePendingDestination
        ) return
        if (!com.blackatsystems.miguardia.support.openSupportLink { startActivity(it) }) {
            Toast.makeText(this, R.string.support_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun canUseUpdates(): Boolean = accessLockActivityResumed && updateContentAllowed()

    private fun updateContentAllowed(): Boolean {
        val application = application as MiGuardiaApplication
        if (!application.startupRecoveryGate.isReady ||
            !application.accessLockCoordinator.state.value.allowsSensitiveContent) return false
        val work = workSetupViewModel.uiState.value
        return work.rootState is com.blackatsystems.miguardia.core.domain.work.WorkSetupState.V2Ready &&
            work.surface == com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface.NONE &&
            !work.isSavingSector && !work.isSavingWorkSet && !work.isSavingTemplate &&
            helpViewModel.uiState.value.canConsumePendingDestination
    }

    private fun openWidgetConfiguration(appWidgetId: Int) {
        if (appWidgetId <= 0 || !(application as MiGuardiaApplication).startupRecoveryGate.isReady) return
        if (!(application as MiGuardiaApplication).accessLockCoordinator.state.value.allowsSensitiveContent) return
        startActivity(
            Intent(this, WidgetConfigurationActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
    }

    companion object {
        const val ACTION_VIEW_SHIFT = "com.blackatsystems.miguardia.action.VIEW_SHIFT"
        const val ACTION_VIEW_DATE = "com.blackatsystems.miguardia.action.VIEW_DATE"
        const val ACTION_DIRECTIONS = "com.blackatsystems.miguardia.action.DIRECTIONS"
        const val ACTION_OPEN_CALENDAR = "com.blackatsystems.miguardia.action.OPEN_CALENDAR"
        const val ACTION_OPEN_WEATHER = "com.blackatsystems.miguardia.action.OPEN_WEATHER"
        const val EXTRA_SHIFT_ID = "shift_id"
        const val EXTRA_OWNER_LOCAL_DATE = "owner_local_date"
        const val DISPLAY_PREFERENCES = "miguardia_display_preferences"
        // Legacy portable preference: retained for existing Copias, never applied to UI.
        const val APP_ZOOM_PERCENT = "app_zoom_percent"
        const val APP_THEME_MODE = "app_theme_mode"
        private val NotificationActions = setOf(
            ACTION_VIEW_SHIFT,
            ACTION_VIEW_DATE,
            ACTION_DIRECTIONS,
            ACTION_OPEN_CALENDAR,
            ACTION_OPEN_WEATHER,
        )
        internal val NotificationActionsForCapture: Set<String> = NotificationActions
        private const val ACCESS_LOCK_AUTHENTICATION_HOST_ID =
            "access_lock_authentication_host_id"
    }
}

internal data class PendingMainDestination(
    val action: String,
    val shiftId: String? = null,
    val ownerLocalDate: String? = null,
) {
    fun toSanitizedIntent(activity: MainActivity): Intent =
        Intent(activity, MainActivity::class.java).setAction(action).apply {
            shiftId?.let { putExtra(MainActivity.EXTRA_SHIFT_ID, it) }
            ownerLocalDate?.let { putExtra(MainActivity.EXTRA_OWNER_LOCAL_DATE, it) }
        }

    companion object {
        fun from(source: Intent?): PendingMainDestination? {
            return from(
                action = source?.action,
                shiftId = source?.getStringExtra(MainActivity.EXTRA_SHIFT_ID),
                ownerLocalDate = source?.getStringExtra(MainActivity.EXTRA_OWNER_LOCAL_DATE),
            )
        }

        fun from(
            action: String?,
            shiftId: String? = null,
            ownerLocalDate: String? = null,
        ): PendingMainDestination? {
            val acceptedAction = action?.takeIf { it in MainActivity.NotificationActionsForCapture }
                ?: return null
            return when (acceptedAction) {
                MainActivity.ACTION_VIEW_SHIFT,
                MainActivity.ACTION_DIRECTIONS,
                -> PendingMainDestination(
                    action = acceptedAction,
                    shiftId = shiftId,
                )
                MainActivity.ACTION_VIEW_DATE -> PendingMainDestination(
                    action = acceptedAction,
                    ownerLocalDate = ownerLocalDate,
                )
                else -> PendingMainDestination(action = acceptedAction)
            }
        }
    }
}

internal data class PendingMainDestinationRequest(
    val generation: Long,
    val destination: PendingMainDestination,
)

/** Saved with the Activity; increment never wraps into an identifier already consumed by Compose. */
internal fun nextCalendarNavigationRequest(current: Long): Long {
    require(current >= 0)
    return Math.incrementExact(current)
}

internal class PendingMainDestinationCoordinator {
    private val consumptionMutex = Mutex()
    private val mutableState = MutableStateFlow<PendingMainDestinationRequest?>(null)
    val state: StateFlow<PendingMainDestinationRequest?> = mutableState.asStateFlow()
    private var nextGeneration = 1L

    @Synchronized
    fun capture(destination: PendingMainDestination): PendingMainDestinationRequest {
        val request = PendingMainDestinationRequest(nextGeneration++, destination)
        mutableState.value = request
        return request
    }

    suspend fun consume(
        request: PendingMainDestinationRequest,
        handler: suspend (PendingMainDestination) -> Unit,
    ): Boolean = consumptionMutex.withLock {
        if (mutableState.value != request) return@withLock false
        handler(request.destination)
        if (mutableState.value == request) {
            mutableState.value = null
            true
        } else {
            false
        }
    }
}
