package com.blackatsystems.miguardia.core.domain.notification

import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.validateReminderLeadMinutes
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.UUID

enum class NotificationBoundaryType {
    REMINDER,
    START,
    END,

    /**
     * Silent wake-up for an event that is already on display: nothing is announced, the visible
     * notification is only drawn again. The permanent notification uses it to start its live
     * countdown. Keep it last: stored keys and ordering rely on the earlier positions.
     */
    REFRESH,
}

/** The permanent notification shows a live countdown only this close to the start of its event. */
val PermanentNotificationCountdownWindow: Duration = Duration.ofHours(24)

data class NotificationBoundaryIdentity(
    val eventIdentity: NextEventIdentity,
    val type: NotificationBoundaryType,
    val triggerAt: Instant,
    val leadMinutes: Long? = null,
) {
    constructor(
        shiftId: UUID,
        type: NotificationBoundaryType,
        triggerAt: Instant,
        leadMinutes: Long? = null,
    ) : this(NextEventIdentity.Shift(shiftId), type, triggerAt, leadMinutes)

    init {
        require((type == NotificationBoundaryType.REMINDER) == (leadMinutes != null))
        require(leadMinutes == null || leadMinutes > 0L)
    }

    val shiftId: UUID?
        get() = (eventIdentity as? NextEventIdentity.Shift)?.shiftId

    val opaqueKey: String = buildString {
        append("v2|")
        append(eventIdentity.trackingKey)
        append('|')
        append(type.name)
        append('|')
        append(triggerAt.toEpochMilli())
        append('|')
        append(leadMinutes ?: 0L)
    }
}

data class NotificationBoundary(
    val identity: NotificationBoundaryIdentity,
    val event: NextEventItem,
)

@ConsistentCopyVisibility
data class ShiftNotificationPlan private constructor(
    val boundaries: List<NotificationBoundary>,
) {
    companion object {
        internal fun create(boundaries: List<NotificationBoundary>): ShiftNotificationPlan =
            ShiftNotificationPlan(Collections.unmodifiableList(boundaries.toList()))
    }
}

/**
 * Keeps the earliest alarm boundaries inside a bounded rolling installation
 * window. Each delivered boundary triggers reconciliation and advances the
 * window, so long finite plans never need to occupy the device quota at once.
 */
fun ShiftNotificationPlan.earliestBoundaries(maximumBoundaries: Int): ShiftNotificationPlan {
    require(maximumBoundaries > 0) { "La ventana de alarmas debe ser positiva" }
    return if (boundaries.size <= maximumBoundaries) {
        this
    } else {
        ShiftNotificationPlan.create(boundaries.take(maximumBoundaries))
    }
}

fun buildNotificationPlan(
    now: Instant,
    notificationsEnabled: Boolean,
    globalReminderLeadMinutes: Collection<Long>,
    projection: NextEventResult,
    shiftOverrides: List<ShiftNotificationConfig>,
    countdownRefreshLead: Duration? = null,
): ShiftNotificationPlan {
    require(projection.referenceInstant == now) {
        "El plan y la proyeccion laboral deben compartir el instante de referencia"
    }
    if (!notificationsEnabled) return ShiftNotificationPlan.create(emptyList())
    val globalLeads = validateReminderLeadMinutes(globalReminderLeadMinutes)
    val overridesByShift = shiftOverrides.associateBy(ShiftNotificationConfig::shiftId)
    val boundaries = projection.events
        .asSequence()
        .flatMap { event ->
            val shiftOverride = (event as? NextEventItem.Shift)
                ?.let { shift -> overridesByShift[shift.shiftId] }
            if (shiftOverride?.reminderLeadMinutes?.isEmpty() == true) {
                emptySequence()
            } else {
                val leads = when (event) {
                    is NextEventItem.Shift -> shiftOverride?.reminderLeadMinutes ?: globalLeads
                    is NextEventItem.Availability -> if (event.isResumption) emptyList() else globalLeads
                }
                sequence {
                    leads.forEach { leadMinutes ->
                        val triggerAt = runCatching {
                            event.start.minus(Duration.ofMinutes(leadMinutes))
                        }.getOrNull() ?: return@forEach
                        if (triggerAt >= now) {
                            yield(
                                NotificationBoundary(
                                    identity = NotificationBoundaryIdentity(
                                        eventIdentity = event.identity,
                                        type = NotificationBoundaryType.REMINDER,
                                        triggerAt = triggerAt,
                                        leadMinutes = leadMinutes,
                                    ),
                                    event = event,
                                ),
                            )
                        }
                    }
                    if (countdownRefreshLead != null) {
                        val refreshAt = runCatching { event.start.minus(countdownRefreshLead) }.getOrNull()
                        if (refreshAt != null && refreshAt > now) {
                            yield(
                                NotificationBoundary(
                                    identity = NotificationBoundaryIdentity(
                                        eventIdentity = event.identity,
                                        type = NotificationBoundaryType.REFRESH,
                                        triggerAt = refreshAt,
                                    ),
                                    event = event,
                                ),
                            )
                        }
                    }
                    if (event.start > now) {
                        yield(
                            NotificationBoundary(
                                identity = NotificationBoundaryIdentity(
                                    eventIdentity = event.identity,
                                    type = NotificationBoundaryType.START,
                                    triggerAt = event.start,
                                ),
                                event = event,
                            ),
                        )
                    }
                    if (event.end > now) {
                        yield(
                            NotificationBoundary(
                                identity = NotificationBoundaryIdentity(
                                    eventIdentity = event.identity,
                                    type = NotificationBoundaryType.END,
                                    triggerAt = event.end,
                                ),
                                event = event,
                            ),
                        )
                    }
                }
            }
        }
        .sortedWith(
            compareBy<NotificationBoundary>(
                { it.identity.triggerAt },
                { it.identity.type.ordinal },
                { it.identity.eventIdentity.stableOrderKey },
                { it.identity.leadMinutes ?: 0L },
            ),
        )
        .toList()
    return ShiftNotificationPlan.create(boundaries)
}
