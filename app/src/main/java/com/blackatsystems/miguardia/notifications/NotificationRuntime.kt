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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class NotificationRuntime(
    context: Context,
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

    fun start() = reconciler.start()

    val restorableEvents: Flow<List<NextEventItem>> = reconciler.observeRestorableEvents()

    fun reconcile() {
        scope.launch {
            runNotificationOperation { reconciler.reconcileOnce() }
        }
    }

    fun rebuild() {
        scope.launch {
            runNotificationOperation { reconciler.rebuildOnce() }
        }
    }

    fun showTestNotification(preferences: NotificationPreferences) {
        presenter.showTestNotification(preferences)
    }

    internal suspend fun reconcileNow() = reconciler.reconcileOnce()

    internal suspend fun rebuildNow() = reconciler.rebuildOnce()

    internal suspend fun dismissNow(eventKey: String) = reconciler.dismissEvent(eventKey)

    internal suspend fun restoreNow(eventKey: String): Boolean = reconciler.restoreEvent(eventKey)

    internal suspend fun deliverNow(identity: NotificationBoundaryIdentity) =
        reconciler.deliverBoundary(identity)
}

internal suspend fun runNotificationOperation(block: suspend () -> Unit): Boolean = try {
    block()
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}
