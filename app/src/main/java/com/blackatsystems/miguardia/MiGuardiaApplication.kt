package com.blackatsystems.miguardia

import android.app.Application
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationDeferredActions
import com.blackatsystems.miguardia.notifications.NotificationRuntime
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.reports.LocalReportGenerator
import com.blackatsystems.miguardia.reports.ReportArtifactStore
import com.blackatsystems.miguardia.reports.ReportPhotoStager
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.ui.summary.SummaryPreferencesStore
import com.blackatsystems.miguardia.ui.help.OnboardingPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherRuntime
import com.blackatsystems.miguardia.widget.WidgetPreferencesStore
import com.blackatsystems.miguardia.widget.WidgetDeferredActions
import com.blackatsystems.miguardia.widget.WidgetRuntime
import com.blackatsystems.miguardia.backup.LocalDataMutationGate
import com.blackatsystems.miguardia.backup.LocalBackupCoordinator
import com.blackatsystems.miguardia.backup.PortablePreferencesGateway
import com.blackatsystems.miguardia.security.AccessLockCoordinator
import com.blackatsystems.miguardia.security.AccessLockPreferencesStore
import com.blackatsystems.miguardia.security.DeviceLockMonitor
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface StartupRecoveryState {
    data object Recovering : StartupRecoveryState
    data object Ready : StartupRecoveryState
    data class Failed(val message: String) : StartupRecoveryState
}

class StartupRecoveryGate(
    initialState: StartupRecoveryState = StartupRecoveryState.Recovering,
) {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<StartupRecoveryState> = mutableState.asStateFlow()
    val isReady: Boolean get() = mutableState.value == StartupRecoveryState.Ready

    fun recovering() {
        mutableState.value = StartupRecoveryState.Recovering
    }

    fun ready() {
        mutableState.value = StartupRecoveryState.Ready
    }

    fun failed(message: String) {
        require(message.isNotBlank())
        mutableState.value = StartupRecoveryState.Failed(message)
    }
}

class MiGuardiaApplication : Application() {
    internal val updateOperations: com.blackatsystems.miguardia.updates.UpdateOperations by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        com.blackatsystems.miguardia.updates.UpdateService(
            com.blackatsystems.miguardia.updates.UpdateHttpTransport(),
            com.blackatsystems.miguardia.updates.AndroidUpdateApkInspector(this),
            updateFiles,
            nanoTime = android.os.SystemClock::elapsedRealtimeNanos,
        )
    }
    private val updateFiles by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        com.blackatsystems.miguardia.updates.UpdateFiles(java.io.File(cacheDir, "updates"))
    }
    private val localRuntimeActivation by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        com.blackatsystems.miguardia.LocalRuntimeActivation(
            isAllowed = {
                com.blackatsystems.miguardia.localAllowsPublication(this) &&
                    !notificationRuntime.isPausedForRestore && !widgetRuntime.isPausedForRestore
            },
            start = {
                replayDeferredDismissals()
                notificationDeferredActions.replayRebuild {
                    if (!startupRecoveryGate.isReady || notificationRuntime.isPausedForRestore) false
                    else notificationRuntime.rebuildNow()
                }
                notificationRuntime.start()
                widgetDeferredActions.replay(widgetRuntime)
                widgetRuntime.start()
                replayDeferredDeliveries()
                notificationRuntime.reconcileNow()
                widgetDeferredActions.replay(widgetRuntime)
                widgetRuntime.refreshNow(allowWeatherRefresh = false)
            },
        )
    }
    internal fun localActivityStarted() {
        if (startupRecoveryGate.isReady) applicationScope.launch(Dispatchers.IO) {
            localRuntimeActivation.ensureStarted()
        }
    }
    val startupRecoveryGate = StartupRecoveryGate()
    val localDataMutationGate = LocalDataMutationGate()
    internal val pendingMainDestinations = PendingMainDestinationCoordinator()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val accessLockPreferences: AccessLockPreferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AccessLockPreferencesStore(this)
    }
    internal val onboardingPreferences: OnboardingPreferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OnboardingPreferencesStore(this)
    }
    internal val accessLockCoordinator: AccessLockCoordinator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AccessLockCoordinator(accessLockPreferences, applicationScope)
    }
    private val deviceLockMonitor: DeviceLockMonitor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DeviceLockMonitor(this, accessLockCoordinator::deviceLocked)
    }
    val localDataStore: LocalDataStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalDataStore.create(this)
    }
    val notificationPreferences: NotificationPreferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NotificationPreferencesStore(this)
    }
    val notificationDeferredActions: NotificationDeferredActions by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NotificationDeferredActions(this)
    }
    val weatherPreferences: WeatherPreferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WeatherPreferencesStore(this)
    }
    val summaryPreferences: SummaryPreferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SummaryPreferencesStore(this)
    }
    val guardProfile: GuardProfileStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GuardProfileStore(this)
    }
    val reportArtifactStore: ReportArtifactStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ReportArtifactStore(this)
    }
    val reportGenerator: LocalReportGenerator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalReportGenerator(
            snapshots = localDataStore.monthlyReportSnapshots,
            profiles = guardProfile,
            photoStager = ReportPhotoStager(this),
            artifactStore = reportArtifactStore,
            clock = Clock.systemUTC(),
            zoneId = AppDefaults.zoneId(),
        )
    }
    val widgetPreferences: WidgetPreferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WidgetPreferencesStore(this)
    }
    val widgetDeferredActions: WidgetDeferredActions by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WidgetDeferredActions(this)
    }
    val portablePreferences: PortablePreferencesGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PortablePreferencesGateway(
            context = this,
            guardProfile = guardProfile,
            summaryStore = summaryPreferences,
            notificationStore = notificationPreferences,
            weatherStore = weatherPreferences,
        )
    }
    val weatherRuntime: WeatherRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WeatherRuntime(this, weatherPreferences, localDataStore.objectives)
    }
    val notificationRuntime: NotificationRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NotificationRuntime(this, localDataStore, notificationPreferences, weatherRuntime)
    }
    val widgetRuntime: WidgetRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WidgetRuntime(
            context = this,
            localDataStore = { localDataStore },
            preferences = widgetPreferences,
            weatherRuntime = weatherRuntime,
        )
    }
    val backupCoordinator: LocalBackupCoordinator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalBackupCoordinator(
            context = this,
            localDataStore = localDataStore,
            preferences = portablePreferences,
            pauseRuntimes = {
                localRuntimeActivation.pause {
                    weatherRuntime.cancelRefresh()
                    notificationRuntime.pauseForRestore()
                    widgetRuntime.pauseForRestore()
                }
            },
            clearDerivedCaches = {
                weatherRuntime.clearCache()
            },
            resumeRuntimes = {
                portablePreferences.ensureAccessibleSoundOrFallback()
                replayDeferredDismissals()
                notificationRuntime.resumeAfterRestore()
                replayDeferredDismissals()
                if (startupRecoveryGate.isReady) {
                    replayDeferredDeliveries()
                }
                notificationRuntime.reconcileNow()
                widgetDeferredActions.replay(widgetRuntime)
                widgetRuntime.resumeAfterRestore()
                widgetDeferredActions.replay(widgetRuntime)
                if (!startupRecoveryGate.isReady) {
                    accessLockCoordinator.initializeAfterRecovery()
                    startupRecoveryGate.ready()
                    replayDeferredDismissals()
                    replayDeferredDeliveries()
                    notificationRuntime.reconcileNow()
                    widgetDeferredActions.replay(widgetRuntime)
                }
                val started = localRuntimeActivation.ensureStarted()
                if (com.blackatsystems.miguardia.localAllowsPublication(this) && !started) {
                    throw java.io.IOException("No se pudieron reactivar los avisos y Widgets.")
                }
            },
            mutationGate = localDataMutationGate,
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) { runCatching { updateFiles.cleanupAfterProcessStart() } }
        deviceLockMonitor.start()
        startupRecoveryGate.recovering()
        com.blackatsystems.miguardia.notifications.ShiftNotificationPresenter(this).showRecoveryForExisting()
        com.blackatsystems.miguardia.widget.WidgetRemoteViewsRenderer(this).renderRecovery()
        applicationScope.launch(Dispatchers.IO) {
            val recovery = runCatching {
                backupCoordinator.recoverAtStartup()
                accessLockCoordinator.initializeAfterRecovery()
                startupRecoveryGate.ready()
                // Launcher ID maintenance must run even while labor publication is gated.
                widgetDeferredActions.replay(widgetRuntime)
                localRuntimeActivation.ensureStarted()
            }
            if (recovery.isFailure) {
                localRuntimeActivation.pause {
                    weatherRuntime.cancelRefresh()
                    runCatching { notificationRuntime.pauseForRestore() }
                    runCatching { widgetRuntime.pauseForRestore() }
                }
                startupRecoveryGate.failed(STARTUP_RECOVERY_ERROR)
            }
        }
    }

    private suspend fun replayDeferredDismissals() {
        if (com.blackatsystems.miguardia.localAllowsPublication(this)) {
            notificationDeferredActions.replay(notificationPreferences)
        }
    }

    private suspend fun replayDeferredDeliveries() {
        if (com.blackatsystems.miguardia.localAllowsPublication(this)) {
            check(notificationDeferredActions.replayDeliveriesWithRetry(deliver = notificationRuntime::deliverNow)) { "Quedan avisos pendientes para reintentar." }
        }
    }

    private companion object {
        const val STARTUP_RECOVERY_ERROR =
            "MiGuardia no pudo terminar una recuperación pendiente. Los avisos y Widgets quedaron pausados para proteger tus datos."
    }
}
