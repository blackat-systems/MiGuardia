package com.blackatsystems.miguardia.core.domain.notification

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.model.validateReminderLeadMinutes
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventShiftOrder
import com.blackatsystems.miguardia.core.domain.nextevent.isEligibleUpcomingWork
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class NotificationBoundaryType {
    REMINDER,
    START,
    END,
}
data class NotificationBoundaryIdentity(
    val shiftId: UUID,
    val type: NotificationBoundaryType,
    val triggerAt: Instant,
    val leadMinutes: Long? = null,
) {
    init {
        require((type == NotificationBoundaryType.REMINDER) == (leadMinutes != null))
        require(leadMinutes == null || leadMinutes > 0L)
    }

    val opaqueKey: String = buildString {
        append(shiftId)
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
    val shift: Shift,
)

data class ShiftNotificationPlan(
    val boundaries: List<NotificationBoundary>,
)

fun buildShiftNotificationPlan(
    now: Instant,
    notificationsEnabled: Boolean,
    globalReminderLeadMinutes: Collection<Long>,
    shifts: List<Shift>,
    vacations: List<Vacation>,
    overrides: List<ShiftNotificationConfig>,
): ShiftNotificationPlan {
    if (!notificationsEnabled) return ShiftNotificationPlan(emptyList())
    val globalLeads = validateReminderLeadMinutes(globalReminderLeadMinutes)
    val overridesByShift = overrides.associateBy(ShiftNotificationConfig::shiftId)
    val boundaries = shifts
        .asSequence()
        .filter { it.isEligibleUpcomingWork(now, vacations) }
        .sortedWith(NextEventShiftOrder)
        .flatMap { shift ->
            val override = overridesByShift[shift.id]
            if (override != null && override.reminderLeadMinutes.isEmpty()) {
                emptySequence()
            } else {
                val leads = override?.reminderLeadMinutes ?: globalLeads
                sequence {
                    leads.forEach { leadMinutes ->
                        val triggerAt = shift.startAt.minus(Duration.ofMinutes(leadMinutes))
                        if (triggerAt >= now) {
                            yield(
                                NotificationBoundary(
                                    NotificationBoundaryIdentity(
                                        shiftId = shift.id,
                                        type = NotificationBoundaryType.REMINDER,
                                        triggerAt = triggerAt,
                                        leadMinutes = leadMinutes,
                                    ),
                                    shift,
                                ),
                            )
                        }
                    }
                    if (shift.startAt > now) {
                        yield(
                            NotificationBoundary(
                                NotificationBoundaryIdentity(
                                    shift.id,
                                    NotificationBoundaryType.START,
                                    shift.startAt,
                                ),
                                shift,
                            ),
                        )
                    }
                    if (shift.endAt > now) {
                        yield(
                            NotificationBoundary(
                                NotificationBoundaryIdentity(
                                    shift.id,
                                    NotificationBoundaryType.END,
                                    shift.endAt,
                                ),
                                shift,
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
                { it.identity.shiftId.toString() },
                { it.identity.leadMinutes ?: 0L },
            ),
        )
        .toList()
    return ShiftNotificationPlan(boundaries)
}
