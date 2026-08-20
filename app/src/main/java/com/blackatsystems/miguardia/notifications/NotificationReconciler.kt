package com.blackatsystems.miguardia.notifications

import android.content.Context
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.notification.buildShiftNotificationPlan
import com.blackatsystems.miguardia.core.domain.notification.reconcileNotificationPlan
import com.blackatsystems.miguardia.core.domain.nextevent.isEligibleUpcomingWork
import com.blackatsystems.miguardia.core.domain.repository.ShiftNotificationConfigRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.weather.WeatherRuntime
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class NotificationReconciler(
    private val shifts: ShiftRepository,
    private val vacations: VacationRepository,
    private val configs: ShiftNotificationConfigRepository,
    private val preferences: NotificationPreferencesStore,
    private val alarmScheduler: AndroidShiftAlarmScheduler,
    private val scope: CoroutineScope,
    context: Context,
    private val weatherRuntime: WeatherRuntime,
    private val clock: Clock = Clock.system(AppDefaults.zoneId()),
) {
    private val mutex = Mutex()
    private var observation: Job? = null
    private val presenter = ShiftNotificationPresenter(context.applicationContext)
    private val systemAccess = NotificationSystemAccess(context.applicationContext)

    fun start() {
        if (observation != null) return
        val now = clock.instant()
        val today = now.atZone(AppDefaults.zoneId()).toLocalDate()
        observation = scope.launch {
            combine(
                shifts.observeEndingAfter(now),
                vacations.observeEndingOnOrAfter(today),
                configs.observeAll(),
                preferences.preferences,
                ::Source,
            ).collect { source -> reconcile(source) }
        }
    }

    suspend fun reconcileOnce() {
        val now = clock.instant()
        val today = now.atZone(AppDefaults.zoneId()).toLocalDate()
        reconcile(
            Source(
                shifts.observeEndingAfter(now).first(),
                vacations.observeEndingOnOrAfter(today).first(),
                configs.observeAll().first(),
                preferences.current(),
            ),
        )
    }

    suspend fun rebuildOnce() {
        mutex.withLock {
            preferences.installedBoundaryKeys().forEach(alarmScheduler::cancel)
            preferences.setInstalledBoundaryKeys(emptySet())
        }
        reconcileOnce()
    }

    suspend fun dismissShift(shiftId: String) = mutex.withLock {
        preferences.markDismissed(shiftId)
        presenter.cancel(shiftId)
    }

    fun observeRestorableShifts(): Flow<List<Shift>> {
        val observedAt = clock.instant()
        val observedDate = observedAt.atZone(AppDefaults.zoneId()).toLocalDate()
        return combine(
            shifts.observeEndingAfter(observedAt),
            vacations.observeEndingOnOrAfter(observedDate),
            configs.observeAll(),
            preferences.dismissedShiftIdsFlow,
        ) { currentShifts, currentVacations, currentConfigs, dismissedIds ->
            restorableDismissedShifts(
                now = clock.instant(),
                shifts = currentShifts,
                vacations = currentVacations,
                configs = currentConfigs,
                dismissedShiftIds = dismissedIds,
            )
        }
    }

    suspend fun restoreShift(shiftId: String): Boolean = mutex.withLock {
        val preferencesSnapshot = preferences.current()
        val id = runCatching { UUID.fromString(shiftId) }.getOrNull()
        val shift = id?.let { shifts.getById(it) }
        val wasDismissed = shiftId in preferences.dismissedShiftIds()
        if (shift == null || !wasDismissed) {
            presenter.cancel(shiftId)
            preferences.clearShiftTracking(shiftId)
            presenter.updateGroupSummary(preferences.displayedShiftIds().size, preferencesSnapshot)
            return@withLock false
        }
        val now = clock.instant()
        val currentVacations = vacations.observeEndingOnOrAfter(shift.localStartDate).first()
        val override = configs.getForShift(shift.id)
        val canRestore = preferencesSnapshot.enabled &&
            systemAccess.read().notificationPermissionGranted &&
            shift.isEligibleUpcomingWork(now, currentVacations) &&
            override?.reminderLeadMinutes?.isEmpty() != true
        if (!canRestore) {
            presenter.cancel(shiftId)
            preferences.clearShiftTracking(shiftId)
            presenter.updateGroupSummary(preferences.displayedShiftIds().size, preferencesSnapshot)
            return@withLock false
        }
        val weatherText = if (preferencesSnapshot.privacy == NotificationPrivacy.COMPLETE) {
            weatherRuntime.notificationTextFromCache(shift, now)
        } else {
            null
        }
        presenter.show(
            shift = shift,
            now = now,
            preferences = preferencesSnapshot,
            weatherText = weatherText,
            silentUpdate = true,
        )
        preferences.markDisplayed(shiftId)
        presenter.updateGroupSummary(preferences.displayedShiftIds().size, preferencesSnapshot)
        true
    }

    private suspend fun reconcile(source: Source) = mutex.withLock {
        val now = clock.instant()
        val plan = buildShiftNotificationPlan(
            now = now,
            notificationsEnabled = source.preferences.enabled,
            globalReminderLeadMinutes = source.preferences.globalReminderLeadMinutes,
            shifts = source.shifts,
            vacations = source.vacations,
            overrides = source.configs,
        )
        val installed = preferences.installedBoundaryKeys()
        val desiredExactMode = source.preferences.preciseTiming &&
            systemAccess.read().exactAlarmAccessGranted
        val precisionChanged = preferences.installedExactMode() != desiredExactMode
        val changes = reconcileNotificationPlan(installed, plan, precisionChanged)
        changes.cancelOpaqueKeys.forEach(alarmScheduler::cancel)
        changes.scheduleBoundaries.forEach { boundary ->
            alarmScheduler.schedule(boundary, source.preferences.preciseTiming)
        }
        preferences.setInstalledBoundaryKeys(plan.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey })
        preferences.setInstalledExactMode(desiredExactMode)

        val displayed = preferences.displayedShiftIds()
        val dismissed = preferences.dismissedShiftIds()
        val eligibleById = eligibleNotificationShifts(
            now = now,
            shifts = source.shifts,
            vacations = source.vacations,
            configs = source.configs,
        )
            .associateBy { it.id.toString() }
        val retainedDismissed = linkedSetOf<String>()
        dismissed.forEach { id ->
            if (isCurrentlyEligibleForDismissal(id, now)) retainedDismissed += id
        }
        retainedDismissed.forEach(presenter::cancel)
        val shouldDisplay = buildSet {
            if (source.preferences.enabled && systemAccess.read().notificationPermissionGranted) {
                addAll(displayed.filter { it in eligibleById && it !in retainedDismissed })
                addAll(
                    eligibleById.values
                        .filter { it.startAt <= now && it.id.toString() !in retainedDismissed }
                        .map { it.id.toString() },
                )
            }
        }
        (displayed - shouldDisplay).forEach(presenter::cancel)
        shouldDisplay.forEach { id ->
            eligibleById[id]?.let { shift ->
                val weatherText = if (source.preferences.privacy == NotificationPrivacy.COMPLETE) {
                    weatherRuntime.notificationTextFromCache(shift, now)
                } else {
                    null
                }
                presenter.show(shift, now, source.preferences, weatherText)
            }
        }
        presenter.updateGroupSummary(shouldDisplay.size, source.preferences)
        preferences.setDisplayedShiftIds(shouldDisplay)
        preferences.setDismissedShiftIds(retainedDismissed)
    }

    private suspend fun isCurrentlyEligibleForDismissal(shiftId: String, now: java.time.Instant): Boolean {
        val id = runCatching { UUID.fromString(shiftId) }.getOrNull() ?: return false
        val shift = shifts.getById(id) ?: return false
        val currentVacations = vacations.observeEndingOnOrAfter(shift.localStartDate).first()
        val currentOverride = configs.getForShift(id)
        return shift.isEligibleUpcomingWork(now, currentVacations) &&
            currentOverride?.reminderLeadMinutes?.isEmpty() != true
    }

    private data class Source(
        val shifts: List<Shift>,
        val vacations: List<Vacation>,
        val configs: List<ShiftNotificationConfig>,
        val preferences: NotificationPreferences,
    )
}
