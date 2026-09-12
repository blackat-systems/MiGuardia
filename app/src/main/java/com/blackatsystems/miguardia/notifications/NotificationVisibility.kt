package com.blackatsystems.miguardia.notifications

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.effectiveWorkedInterval
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import java.time.Instant
import java.util.UUID

internal fun eligibleNotificationEvents(
    events: List<NextEventItem>,
    configs: List<ShiftNotificationConfig>,
): List<NextEventItem> {
    val configsByShift = configs.associateBy(ShiftNotificationConfig::shiftId)
    return events.filter { event ->
        event !is NextEventItem.Shift ||
            configsByShift[event.shiftId]?.reminderLeadMinutes?.isEmpty() != true
    }
}

internal fun restorableDismissedEvents(
    events: List<NextEventItem>,
    configs: List<ShiftNotificationConfig>,
    dismissedEventKeys: Set<String>,
): List<NextEventItem> = eligibleNotificationEvents(events, configs)
    .filter { event -> event.identity.trackingKey in dismissedEventKeys }

internal data class NotificationSourceLifetime(
    val start: Instant,
    val end: Instant,
) {
    init {
        require(start < end) { "La fuente de un aviso debe tener duracion positiva" }
    }
}

internal fun shiftNotificationSourceLifetime(
    shift: Shift,
    actual: ShiftActualAggregate?,
): NotificationSourceLifetime = effectiveWorkedInterval(shift, actual).let { (start, end) ->
    NotificationSourceLifetime(start = start, end = end)
}

internal fun retainLiveDismissedEventKeys(
    dismissedEventKeys: Set<String>,
    now: Instant,
    shiftSources: Map<UUID, NotificationSourceLifetime>,
    availabilitySources: Map<UUID, NotificationSourceLifetime>,
): Set<String> = dismissedEventKeys.mapNotNullTo(linkedSetOf()) { rawKey ->
    val identity = NextEventIdentity.parseTrackingKey(rawKey) ?: return@mapNotNullTo null
    val sourceIsStillLive = when (identity) {
        is NextEventIdentity.Shift -> shiftSources[identity.shiftId]
            ?.let { source -> now < source.end }
            ?: false

        is NextEventIdentity.Availability -> availabilitySources[identity.windowId]
            ?.let { source ->
                now < identity.segmentEnd &&
                    source.start <= identity.segmentStart &&
                    identity.segmentEnd <= source.end
            }
            ?: false
    }
    identity.trackingKey.takeIf { sourceIsStillLive }
}

internal data class NotificationDisplayReconciliation(
    val eventKeysToCancel: Set<String>,
    val eventKeysToDisplay: Set<String>,
    val retainedDismissedEventKeys: Set<String>,
)

internal fun reconcileNotificationVisibility(
    notificationsEnabled: Boolean,
    notificationPermissionGranted: Boolean,
    eligibleEventKeys: Set<String>,
    startedEligibleEventKeys: Set<String>,
    displayedEventKeys: Set<String>,
    retainedDismissedEventKeys: Set<String>,
): NotificationDisplayReconciliation {
    val shouldDisplay = buildSet {
        if (notificationsEnabled && notificationPermissionGranted) {
            addAll(displayedEventKeys.filter { it in eligibleEventKeys && it !in retainedDismissedEventKeys })
            addAll(startedEligibleEventKeys.filterNot(retainedDismissedEventKeys::contains))
        }
    }
    return NotificationDisplayReconciliation(
        eventKeysToCancel = (displayedEventKeys - shouldDisplay) + retainedDismissedEventKeys,
        eventKeysToDisplay = shouldDisplay,
        // A temporary protection or another projected absence must not erase
        // the user's explicit dismissal. Raw-source lifetime pruning happens
        // before this display-only reconciliation.
        retainedDismissedEventKeys = retainedDismissedEventKeys,
    )
}
