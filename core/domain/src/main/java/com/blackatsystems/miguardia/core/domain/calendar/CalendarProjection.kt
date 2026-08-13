package com.blackatsystems.miguardia.core.domain.calendar

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

enum class ShiftTemporalStatus {
    UPCOMING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    ABSENT,
}

data class CalendarShift(
    val shift: Shift,
    val temporalStatus: ShiftTemporalStatus,
)

data class CalendarDay(
    val date: LocalDate,
    val shifts: List<CalendarShift>,
    val explicitStatus: ExplicitDayStatusType?,
    val hasMedicalLeave: Boolean,
) {
    val isImplicitlyUndefined: Boolean
        get() = shifts.isEmpty() && explicitStatus == null && !hasMedicalLeave
}

fun Shift.temporalStatusAt(now: Instant): ShiftTemporalStatus = when (status) {
    ShiftStatus.CANCELLED -> ShiftTemporalStatus.CANCELLED
    ShiftStatus.ABSENT -> ShiftTemporalStatus.ABSENT
    ShiftStatus.PLANNED -> when {
        now < startAt -> ShiftTemporalStatus.UPCOMING
        now < endAt -> ShiftTemporalStatus.IN_PROGRESS
        else -> ShiftTemporalStatus.COMPLETED
    }
}

fun projectCalendarMonth(
    month: YearMonth,
    shifts: List<Shift>,
    explicitDayStatuses: List<ExplicitDayStatus>,
    medicalLeaves: List<MedicalLeave>,
    now: Instant,
): List<CalendarDay> {
    val startDate = month.atDay(1)
    val endDate = month.atEndOfMonth()
    val shiftsByDate = shifts
        .asSequence()
        .filter { it.localStartDate in startDate..endDate }
        .sortedWith(compareBy<Shift>({ it.startAt }, { it.id }))
        .groupBy { it.localStartDate }
    val explicitByDate = explicitDayStatuses
        .asSequence()
        .filter { it.date in startDate..endDate }
        .associateBy { it.date }
    val relevantMedicalLeaves = medicalLeaves.filter { leave ->
        leave.startDate <= endDate && leave.endDateInclusive >= startDate
    }

    return (1..month.lengthOfMonth()).map { dayOfMonth ->
        val date = month.atDay(dayOfMonth)
        CalendarDay(
            date = date,
            shifts = shiftsByDate[date].orEmpty().map { shift ->
                CalendarShift(
                    shift = shift,
                    temporalStatus = shift.temporalStatusAt(now),
                )
            },
            explicitStatus = explicitByDate[date]?.type,
            hasMedicalLeave = relevantMedicalLeaves.any { leave ->
                date >= leave.startDate && date <= leave.endDateInclusive
            },
        )
    }
}
