package com.blackatsystems.miguardia.notifications

import android.content.Context
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.ui.nextevent.V2WorkEventSourceObserver
import com.blackatsystems.miguardia.weather.WeatherRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class NotificationRuntime(
    private val context: Context,
    localDataStore: LocalDataStore,
    val preferences: NotificationPreferencesStore,
    weatherRuntime: WeatherRuntime,
) {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sources = V2WorkEventSourceObserver(
        shifts = localDataStore.v2Shifts,
        availabilityWindows = localDataStore.availabilityWindows,
        shiftActuals = localDataStore.shiftActuals,
        independentExtras = localDataStore.independentExtraWork,
        explicitDayStatuses = localDataStore.explicitDayStatuses,
        vacations = localDataStore.vacations,
        medicalLeaves = localDataStore.medicalLeaves,
        workConfiguration = localDataStore.workConfiguration,
    )
    private val reconciler = NotificationReconciler(
        sources = sources,
        configs = localDataStore.shiftNotificationConfigs,
        preferences = preferences,
        alarmScheduler = AndroidShiftAlarmScheduler(context.applicationContext),
        scope = scope,
        context = context.applicationContext,
        weatherRuntime = weatherRuntime,
    )
    private val presenter = ShiftNotificationPresenter(context.applicationContext)
    @Volatile
    private var pausedForRestore = false

    private fun publicationReady(): Boolean {
        val allowed = com.blackatsystems.miguardia.localAllowsPublication(context)
        if (!allowed) presenter.showRecoveryForExisting()
        return allowed
    }

    fun start() {
        if (!pausedForRestore && publicationReady()) reconciler.start()
    }

    suspend fun pauseForRestore() {
        pausedForRestore = true
        reconciler.stop()
        while (true) {
            val children = scope.coroutineContext[Job]?.children?.toList().orEmpty()
            if (children.isEmpty()) break
            children.forEach(Job::cancel)
            children.joinAll()
        }
    }

    suspend fun resumeAfterRestore() {
        pausedForRestore = false
        if (!publicationReady()) return
        try {
            reconciler.start()
            check(runNotificationOperation { reconciler.reconcileOnce() }) {
                "No se pudieron reconciliar los avisos después de restaurar."
            }
        } catch (error: Exception) {
            pausedForRestore = true
            reconciler.stop()
            throw error
        }
    }

    internal val isPausedForRestore: Boolean get() = pausedForRestore

    val restorableEvents: Flow<List<NextEventItem>> = reconciler.observeRestorableEvents()

    fun reconcile() {
        if (pausedForRestore || !publicationReady()) return
        scope.launch {
            runNotificationOperation { reconciler.reconcileOnce() }
        }
    }

    fun rebuild() {
        if (pausedForRestore || !publicationReady()) return
        scope.launch {
            runNotificationOperation { reconciler.rebuildOnce() }
        }
    }

    fun showTestNotification(preferences: NotificationPreferences) {
        if (pausedForRestore || !publicationReady()) return
        presenter.showTestNotification(preferences)
    }

    internal suspend fun reconcileNow() {
        if (!pausedForRestore && publicationReady()) reconciler.reconcileOnce()
    }

    internal suspend fun rebuildNow(): Boolean {
        if (pausedForRestore || !publicationReady()) return false
        reconciler.rebuildOnce()
        return !pausedForRestore && publicationReady()
    }

    internal suspend fun dismissNow(eventKey: String) {
        if (!pausedForRestore && publicationReady()) reconciler.dismissEvent(eventKey)
    }

    internal suspend fun restoreNow(eventKey: String): Boolean =
        !pausedForRestore && publicationReady() && reconciler.restoreEvent(eventKey)

    internal suspend fun deliverNow(identity: NotificationBoundaryIdentity) {
        if (pausedForRestore || !publicationReady()) throw CancellationException("Notification delivery is gated")
        reconciler.deliverBoundary(identity)
    }
}

internal suspend fun runNotificationOperation(block: suspend () -> Unit): Boolean = try {
    block()
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}
