package com.blackatsystems.miguardia.core.domain.notification

data class NotificationPlanReconciliation(
    val cancelOpaqueKeys: Set<String>,
    val scheduleBoundaries: List<NotificationBoundary>,
)

fun reconcileNotificationPlan(
    installedOpaqueKeys: Set<String>,
    desiredPlan: ShiftNotificationPlan,
    forceReschedule: Boolean = false,
): NotificationPlanReconciliation {
    val desiredKeys = desiredPlan.boundaries.mapTo(linkedSetOf()) { it.identity.opaqueKey }
    return NotificationPlanReconciliation(
        cancelOpaqueKeys = if (forceReschedule) installedOpaqueKeys else installedOpaqueKeys - desiredKeys,
        scheduleBoundaries = desiredPlan.boundaries.filter { boundary ->
            forceReschedule || boundary.identity.opaqueKey !in installedOpaqueKeys
        },
    )
}
