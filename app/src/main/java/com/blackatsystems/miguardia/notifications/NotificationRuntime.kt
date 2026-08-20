package com.blackatsystems.miguardia.notifications

import android.content.Context
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.weather.WeatherRuntime
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
    private val reconciler = NotificationReconciler(
        shifts = localDataStore.shifts,
        vacations = localDataStore.vacations,
        configs = localDataStore.shiftNotificationConfigs,
        preferences = preferences,
        alarmScheduler = AndroidShiftAlarmScheduler(context.applicationContext),
        scope = scope,
        context = context.applicationContext,
        weatherRuntime = weatherRuntime,
    )
    private val presenter = ShiftNotificationPresenter(context.applicationContext)

    fun start() = reconciler.start()

    val restorableShifts: Flow<List<Shift>> = reconciler.observeRestorableShifts()

    fun reconcile() {
        scope.launch { reconciler.reconcileOnce() }
    }

    fun rebuild() {
        scope.launch { reconciler.rebuildOnce() }
    }

    fun showTestNotification(preferences: NotificationPreferences) {
        presenter.showTestNotification(preferences)
    }

    internal suspend fun reconcileNow() = reconciler.reconcileOnce()

    internal suspend fun rebuildNow() = reconciler.rebuildOnce()

    internal suspend fun dismissNow(shiftId: String) = reconciler.dismissShift(shiftId)

    internal suspend fun restoreNow(shiftId: String): Boolean = reconciler.restoreShift(shiftId)
}
