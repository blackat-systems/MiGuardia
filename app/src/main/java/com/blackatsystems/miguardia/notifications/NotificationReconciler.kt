package com.blackatsystems.miguardia.notifications

import android.content.Context
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryIdentity
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryType
import com.blackatsystems.miguardia.core.domain.notification.buildNotificationPlan
import com.blackatsystems.miguardia.core.domain.notification.earliestBoundaries
import com.blackatsystems.miguardia.core.domain.notification.reconcileNotificationPlan
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.repository.ShiftNotificationConfigRepository
import com.blackatsystems.miguardia.ui.nextevent.NextEventSourceData
import com.blackatsystems.miguardia.ui.nextevent.V2WorkEventSourceObserver
import com.blackatsystems.miguardia.weather.WeatherRuntime
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class NotificationReconciler(
    private val sources: V2WorkEventSourceObserver,
    private val configs: ShiftNotificationConfigRepository,
    private val preferences: NotificationPreferencesStore,
    private val alarmScheduler: AndroidShiftAlarmScheduler,
    private val scope: CoroutineScope,
    context: Context,
    private val weatherRuntime: WeatherRuntime,
    private val clock: Clock = Clock.system(AppDefaults.zoneId()),
    private val zoneId: ZoneId = AppDefaults.zoneId(),
) {
    private val mutex = Mutex()
    private var observation: Job? = null
    private val presenter = ShiftNotificationPresenter(context.applicationContext)
    private val systemAccess = NotificationSystemAccess(context.applicationContext)

    fun start() {
        if (observation != null) return
        observation = scope.launch {
            var retryAttempt = 0
            while (currentCoroutineContext().isActive) {
                try {
                    val today = clock.instant().atZone(zoneId).toLocalDate()
                    combine(
                        sources.observe(today),
                        configs.observeAll(),
                        preferences.preferences,
                        ::Source,
                    ).collect {
                        reconcileCurrent()
                        retryAttempt = 0
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    delay(notificationObservationRetryDelayMillis(retryAttempt))
                    retryAttempt = (retryAttempt + 1).coerceAtMost(8)
                }
            }
        }
    }

    suspend fun reconcileOnce() = reconcileCurrent()

    suspend fun rebuildOnce() {
        mutex.withLock {
            val previouslyInstalled = preferences.installedBoundaryKeys()
            retireInstalledNotificationBoundaries(
                opaqueKeys = previouslyInstalled,
                clearInstalled = { preferences.setInstalledBoundaryKeys(emptySet()) },
                cancel = alarmScheduler::cancel,
            )
        }
        reconcileOnce()
    }

    suspend fun dismissEvent(eventKey: String) = mutex.withLock {
        val normalized = NextEventIdentity.parseTrackingKey(eventKey)?.trackingKey ?: return@withLock
        preferences.markDismissed(normalized)
        presenter.cancel(normalized)
        presenter.updateGroupSummary(preferences.displayedEventKeys().size, preferences.current())
    }

    fun observeRestorableEvents(): Flow<List<NextEventItem>> {
        val observedDate = clock.instant().atZone(zoneId).toLocalDate()
        return combine(
            sources.observe(observedDate),
            configs.observeAll(),
            preferences.dismissedEventKeysFlow,
        ) { eventSources, currentConfigs, dismissedKeys ->
            val projection = eventSources.project(clock.instant())
            restorableDismissedEvents(
                events = projection.events,
                configs = currentConfigs,
                dismissedEventKeys = dismissedKeys,
            )
        }
    }

    suspend fun restoreEvent(eventKey: String): Boolean = mutex.withLock {
        val identity = NextEventIdentity.parseTrackingKey(eventKey) ?: return@withLock false
        val normalized = identity.trackingKey
        val preferencesSnapshot = preferences.current()
        val wasDismissed = normalized in preferences.dismissedEventKeys()
        val now = clock.instant()
        val eventSources = currentEventSources(now)
        val currentConfigs = configs.observeAll().first()
        val event = eventSources.project(now).events.firstOrNull { it.identity == identity }
        val canRestore = event != null &&
            wasDismissed &&
            preferencesSnapshot.enabled &&
            systemAccess.read().notificationAccessGranted &&
            event.isNotificationEnabled(currentConfigs)
        if (!canRestore) {
            presenter.cancel(normalized)
            preferences.clearEventTracking(normalized)
            presenter.updateGroupSummary(preferences.displayedEventKeys().size, preferencesSnapshot)
            return@withLock false
        }
        checkNotNull(event)
        presenter.show(
            event = event,
            now = now,
            preferences = preferencesSnapshot,
            weatherText = cachedWeather(event, eventSources, preferencesSnapshot, now),
            silentUpdate = true,
        )
        preferences.markDisplayed(normalized)
        presenter.updateGroupSummary(preferences.displayedEventKeys().size, preferencesSnapshot)
        true
    }

    suspend fun deliverBoundary(identity: NotificationBoundaryIdentity) = mutex.withLock {
        val now = clock.instant()
        if (now < identity.triggerAt) return@withLock
        val preferencesSnapshot = preferences.current()
        val eventSources = currentEventSources(now)
        val currentConfigs = configs.observeAll().first()
        if (identity.type == NotificationBoundaryType.END) {
            val beforeBoundary = runCatching { identity.triggerAt.minusMillis(1L) }.getOrElse { identity.triggerAt }
            val event = eventSources.project(beforeBoundary).events
                .firstOrNull { it.identity == identity.eventIdentity }
            if (event != null && event.end == identity.triggerAt) {
                presenter.cancel(identity.eventIdentity.trackingKey)
                preferences.clearEventTracking(identity.eventIdentity.trackingKey)
            }
            return@withLock
        }
        val projection = eventSources.project(now)
        val event = projection.events.firstOrNull { it.identity == identity.eventIdentity }
            ?: return@withLock
        if (
            !preferencesSnapshot.enabled ||
            !systemAccess.read().notificationAccessGranted ||
            !event.isNotificationEnabled(currentConfigs) ||
            !identity.isCurrentFor(event, preferencesSnapshot, currentConfigs, now)
        ) {
            return@withLock
        }
        val trackingKey = event.identity.trackingKey
        if (!preferences.markDisplayedUnlessDismissed(trackingKey)) {
            presenter.cancel(trackingKey)
            return@withLock
        }
        val weatherText = cachedWeather(event, eventSources, preferencesSnapshot, now)
        presenter.show(
            event = event,
            now = now,
            preferences = preferencesSnapshot,
            weatherText = weatherText,
            silentUpdate = event is NextEventItem.Availability && event.isResumption,
        )
        if (trackingKey in preferences.dismissedEventKeys()) {
            presenter.cancel(trackingKey)
            return@withLock
        }
    }

    private suspend fun reconcileCurrent() = mutex.withLock {
        val now = clock.instant()
        // All one-shot and observed reconciliations re-read their complete
        // snapshot after obtaining the mutex. An older queued emission can
        // therefore never overwrite a newer edit, deletion or preference.
        val source = Source(
            eventSources = currentEventSources(now),
            configs = configs.observeAll().first(),
            preferences = preferences.current(),
        )
        val projection = source.eventSources.project(now)
        val plan = buildNotificationPlan(
            now = now,
            notificationsEnabled = source.preferences.enabled,
            globalReminderLeadMinutes = source.preferences.globalReminderLeadMinutes,
            projection = projection,
            shiftOverrides = source.configs,
        )
        val installationPlan = plan.earliestBoundaries(MAX_PENDING_NOTIFICATION_ALARMS)
        val installed = preferences.installedBoundaryKeys()
        val access = systemAccess.read()
        val desiredExactMode = source.preferences.preciseTiming && access.exactAlarmAccessGranted
        val precisionChanged = preferences.installedExactMode() != desiredExactMode
        val changes = reconcileNotificationPlan(installed, installationPlan, precisionChanged)
        changes.cancelOpaqueKeys.forEach(alarmScheduler::cancel)
        val retainedInstalled = installed - changes.cancelOpaqueKeys
        val newlyInstalled = linkedSetOf<String>()
        for (boundary in changes.scheduleBoundaries) {
            if (!alarmScheduler.schedule(boundary, source.preferences.preciseTiming)) break
            newlyInstalled += boundary.identity.opaqueKey
        }
        preferences.setInstalledBoundaryKeys(retainedInstalled + newlyInstalled)
        preferences.setInstalledExactMode(desiredExactMode)

        val eligible = eligibleNotificationEvents(projection.events, source.configs)
        val eligibleByKey = eligible.associateBy { event -> event.identity.trackingKey }
        val displayed = preferences.displayedEventKeys()
        val dismissed = preferences.dismissedEventKeys()
        val retainedDismissed = retainLiveDismissedEventKeys(
            dismissedEventKeys = dismissed,
            now = now,
            shiftSources = source.eventSources.shifts.associate { write ->
                write.shift.id to shiftNotificationSourceLifetime(
                    shift = write.shift,
                    actual = source.eventSources.actualsByShiftId[write.shift.id],
                )
            },
            availabilitySources = source.eventSources.availabilityWindows.associate { window ->
                window.id to NotificationSourceLifetime(
                    start = window.start,
                    end = window.end,
                )
            },
        )
        val visibility = reconcileNotificationVisibility(
            notificationsEnabled = source.preferences.enabled,
            notificationPermissionGranted = access.notificationAccessGranted,
            eligibleEventKeys = eligibleByKey.keys,
            startedEligibleEventKeys = eligible
                .filter { event -> event.start <= now && now < event.end }
                .mapTo(linkedSetOf()) { event -> event.identity.trackingKey },
            displayedEventKeys = displayed,
            retainedDismissedEventKeys = retainedDismissed,
        )
        visibility.eventKeysToCancel.forEach(presenter::cancel)
        visibility.eventKeysToDisplay.forEach { eventKey ->
            eligibleByKey[eventKey]?.let { event ->
                presenter.show(
                    event = event,
                    now = now,
                    preferences = source.preferences,
                    weatherText = cachedWeather(event, source.eventSources, source.preferences, now),
                    silentUpdate = true,
                )
            }
        }
        presenter.updateGroupSummary(visibility.eventKeysToDisplay.size, source.preferences)
        preferences.setDisplayedEventKeys(visibility.eventKeysToDisplay)
        preferences.setDismissedEventKeys(visibility.retainedDismissedEventKeys)
    }

    private fun NextEventSourceData.project(now: Instant): NextEventResult = projectNextEvent(
        now = now,
        zoneId = zoneId,
        input = toInput(),
    )

    private suspend fun currentEventSources(now: Instant): NextEventSourceData = sources
        .observe(now.atZone(zoneId).toLocalDate())
        .first()

    private fun NextEventItem.isNotificationEnabled(
        configs: List<ShiftNotificationConfig>,
    ): Boolean = this !is NextEventItem.Shift || configs
        .firstOrNull { config -> config.shiftId == shiftId }
        ?.reminderLeadMinutes
        ?.isEmpty() != true

    private fun NotificationBoundaryIdentity.isCurrentFor(
        event: NextEventItem,
        preferences: NotificationPreferences,
        configs: List<ShiftNotificationConfig>,
        now: Instant,
    ): Boolean {
        if (event.identity != eventIdentity) return false
        return when (type) {
            NotificationBoundaryType.REMINDER -> {
                if (now >= event.start) return false
                val lead = leadMinutes ?: return false
                val configured = when (event) {
                    is NextEventItem.Shift -> configs
                        .firstOrNull { config -> config.shiftId == event.shiftId }
                        ?.reminderLeadMinutes
                        ?: preferences.globalReminderLeadMinutes
                    is NextEventItem.Availability -> if (event.isResumption) {
                        emptyList()
                    } else {
                        preferences.globalReminderLeadMinutes
                    }
                }
                val expectedTrigger = runCatching {
                    event.start.minus(Duration.ofMinutes(lead))
                }.getOrNull()
                lead in configured && triggerAt == expectedTrigger
            }
            NotificationBoundaryType.START -> triggerAt == event.start && now < event.end
            NotificationBoundaryType.END -> false
        }
    }

    private suspend fun cachedWeather(
        event: NextEventItem,
        eventSources: NextEventSourceData,
        preferences: NotificationPreferences,
        now: Instant,
    ): String? {
        if (event !is NextEventItem.Shift || preferences.privacy != NotificationPrivacy.COMPLETE) return null
        val rawShift = eventSources.shifts.firstOrNull { write -> write.shift.id == event.shiftId }?.shift
            ?: return null
        return weatherRuntime.notificationTextFromCache(rawShift, now)
    }

    private data class Source(
        val eventSources: NextEventSourceData,
        val configs: List<ShiftNotificationConfig>,
        val preferences: NotificationPreferences,
    )

    private companion object {
        // Android's per-UID alarm quota is device-configurable. This leaves
        // ample headroom while delivered boundaries continuously roll forward.
        const val MAX_PENDING_NOTIFICATION_ALARMS = 128
    }
}

internal fun notificationObservationRetryDelayMillis(attempt: Int): Long {
    val exponent = attempt.coerceIn(0, 8)
    return (1_000L * (1L shl exponent)).coerceAtMost(60_000L)
}

internal suspend fun retireInstalledNotificationBoundaries(
    opaqueKeys: Set<String>,
    clearInstalled: suspend () -> Unit,
    cancel: (String) -> Unit,
) {
    // Persist the conservative state first. If cancellation is interrupted,
    // the next reconciliation safely replaces any surviving PendingIntent
    // instead of trusting an alarm that may already be gone.
    clearInstalled()
    opaqueKeys.forEach(cancel)
}
