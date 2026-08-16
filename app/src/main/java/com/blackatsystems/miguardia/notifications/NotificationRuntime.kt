package com.blackatsystems.miguardia.notifications

import android.content.Context
import com.blackatsystems.miguardia.core.database.LocalDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationRuntime(
    context: Context,
    localDataStore: LocalDataStore,
    val preferences: NotificationPreferencesStore,
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
    )

    fun start() = reconciler.start()

    fun reconcile() {
        scope.launch { reconciler.reconcileOnce() }
    }

    fun rebuild() {
        scope.launch { reconciler.rebuildOnce() }
    }

    internal suspend fun reconcileNow() = reconciler.reconcileOnce()

    internal suspend fun rebuildNow() = reconciler.rebuildOnce()

    internal suspend fun dismissNow(shiftId: String) = reconciler.dismissShift(shiftId)
}
