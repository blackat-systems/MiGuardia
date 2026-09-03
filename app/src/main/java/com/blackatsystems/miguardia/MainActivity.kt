package com.blackatsystems.miguardia

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.core.net.toUri
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
import com.blackatsystems.miguardia.ui.theme.AppZoom
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

class MainActivity : FragmentActivity() {
    private var calendarNavigationRequest by mutableIntStateOf(0)
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
        )
    }

    private val vacationViewModel: VacationViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        VacationViewModel.Factory(dataStore.vacations)
    }

    private val v2ManualShiftLoadViewModel: V2ManualShiftLoadViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        V2ManualShiftLoadViewModel.Factory(
            configurationRepository = dataStore.workConfiguration,
            catalogRepository = dataStore.workCatalog,
            objectiveRepository = dataStore.objectives,
            shiftRepository = dataStore.shifts,
            medicalLeaveRepository = dataStore.medicalLeaves,
            v2ShiftRepository = dataStore.v2Shifts,
            shiftActualRepository = dataStore.shiftActuals,
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
        )
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            var appZoom by remember {
                mutableStateOf(
                    AppZoom.fromPercent(
                        preferences.getInt(APP_ZOOM_PERCENT, AppZoom.STANDARD.percent),
                    ),
                )
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
            val dataAccessReady = recoveryState == StartupRecoveryState.Ready
            val helpState = if (dataAccessReady) {
                helpViewModel.uiState.collectAsStateWithLifecycle().value
            } else {
                HelpUiState()
            }
            val sensitiveContentReady = dataAccessReady && accessLockState.allowsSensitiveContent
            LaunchedEffect(backupState.successSequence, sensitiveContentReady) {
                if (sensitiveContentReady && backupState.successSequence > 0) {
                    backupState.restoredZoom?.let { appZoom = it }
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
                accessLockState.allowsSensitiveContent,
                helpState.canConsumePendingDestination,
                pendingDestinationRequest?.generation,
            ) {
                val request = pendingDestinationRequest
                if (sensitiveContentReady && helpState.canConsumePendingDestination && request != null) {
                    val consumed = miGuardiaApplication.pendingMainDestinations.consume(request) {
                        handlePendingDestination(it)
                    }
                    if (consumed) {
                        setIntent(Intent(this@MainActivity, MainActivity::class.java))
                    }
                }
            }
            LaunchedEffect(recoveryState, accessLockState.allowsSensitiveContent) {
                if (sensitiveContentReady) {
                    appZoom = AppZoom.fromPercent(
                        preferences.getInt(APP_ZOOM_PERCENT, AppZoom.STANDARD.percent),
                    )
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
                appZoom = appZoom,
            ) {
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
                        actions = BackupActions.from(backupViewModel),
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
                    MiGuardiaApp(
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
                    appZoom = appZoom,
                    onAppZoomChange = { selected ->
                        appZoom = selected
                        preferences.edit { putInt(APP_ZOOM_PERCENT, selected.percent) }
                    },
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
                    )
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
        refreshDeviceAuthenticationCapability()
        application.accessLockCoordinator.activityResumed(deviceAuthenticator.deviceIsLocked())
        AccessLockWindowProtection.applyForeground(this, application.accessLockCoordinator.state.value)
        if (!application.startupRecoveryGate.isReady ||
            !application.accessLockCoordinator.state.value.allowsSensitiveContent
        ) return
        refreshActiveSurfaces()
    }

    override fun onPause() {
        accessLockActivityResumed = false
        val application = application as MiGuardiaApplication
        application.accessLockCoordinator.activityPausedForPrivacy()
        AccessLockWindowProtection.protectForBackground(this, application.accessLockCoordinator.state.value)
        super.onPause()
    }

    override fun onStop() {
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

    private suspend fun handlePendingDestination(source: PendingMainDestination) {
        val application = application as MiGuardiaApplication
        if (!application.startupRecoveryGate.isReady ||
            !application.accessLockCoordinator.state.value.allowsSensitiveContent
        ) throw CancellationException("Access lock closed before resolving a destination")
        val action = source.action
        if (action !in NotificationActions) return
        if (action == ACTION_OPEN_CALENDAR) {
            calendarViewModel.clearSelectedDate()
            calendarNavigationRequest++
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
            calendarNavigationRequest++
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
            calendarViewModel.clearSelectedDate()
            calendarNavigationRequest++
            Toast.makeText(this, "No pudimos abrir ese destino.", Toast.LENGTH_LONG).show()
            return
        }
        if (shift == null) {
            calendarViewModel.clearSelectedDate()
            calendarNavigationRequest++
            Toast.makeText(this, "La jornada ya no está disponible.", Toast.LENGTH_LONG).show()
            return
        }
        if (!application.accessLockCoordinator.state.value.allowsSensitiveContent) {
            throw CancellationException("Access lock closed while resolving a destination")
        }
        when (action) {
            ACTION_VIEW_SHIFT -> {
                calendarViewModel.openDate(shift.localStartDate)
                calendarNavigationRequest++
            }
            ACTION_DIRECTIONS -> {
                val address = shift.objectiveAddressSnapshot?.takeIf(String::isNotBlank)
                val opened = address?.let {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(it)}".toUri()))
                        true
                    } catch (_: ActivityNotFoundException) {
                        false
                    }
                } == true
                if (!opened) {
                    calendarViewModel.openDate(shift.localStartDate)
                    calendarNavigationRequest++
                    Toast.makeText(
                        this,
                        if (address == null) "Esta jornada no tiene una dirección guardada." else "No hay una aplicación compatible para abrir la dirección.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun captureIncomingDestination(source: Intent?) {
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

    private fun openWidgetConfiguration(appWidgetId: Int) {
        if (appWidgetId <= 0 || !(application as MiGuardiaApplication).startupRecoveryGate.isReady) return
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
