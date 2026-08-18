package com.blackatsystems.miguardia.notifications

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventShiftOrder
import com.blackatsystems.miguardia.core.domain.nextevent.isEligibleUpcomingWork
import java.time.Instant

internal fun eligibleNotificationShifts(
    now: Instant,
    shifts: List<Shift>,
    vacations: List<Vacation>,
    configs: List<ShiftNotificationConfig>,
): List<Shift> {
    val configsByShift = configs.associateBy(ShiftNotificationConfig::shiftId)
    return shifts
        .asSequence()
        .filter { shift ->
            shift.isEligibleUpcomingWork(now, vacations) &&
                configsByShift[shift.id]?.reminderLeadMinutes?.isEmpty() != true
        }
        .sortedWith(NextEventShiftOrder)
        .toList()
}

internal fun restorableDismissedShifts(
    now: Instant,
    shifts: List<Shift>,
    vacations: List<Vacation>,
    configs: List<ShiftNotificationConfig>,
    dismissedShiftIds: Set<String>,
): List<Shift> = eligibleNotificationShifts(now, shifts, vacations, configs)
    .filter { shift -> shift.id.toString() in dismissedShiftIds }
