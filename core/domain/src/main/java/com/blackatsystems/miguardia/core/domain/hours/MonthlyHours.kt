package com.blackatsystems.miguardia.core.domain.hours

import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.vacation.vacationDatesInMonth
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

data class MonthlyHoursSummary(
    val month: YearMonth,
    val referenceInstant: Instant,
    val planned: Duration,
    val worked: Duration,
    val pending: Duration,
    val overtime: Duration,
    val nightWorked: Duration,
    val holidayWorked: Duration,
    val shiftCount: Int,
    val dayOffCount: Int,
    val medicalLeaveDayCount: Int,
    val medicalLeaveHours: Duration,
    val absenceCount: Int,
    val absenceHours: Duration,
    val cancellationCount: Int,
    val cancellationHours: Duration,
    val vacationDayCount: Int = 0,
    val projectedWorked: Duration = worked.plus(pending),
    val projectedOvertime: Duration = Duration.ZERO,
    val projectedNightWorked: Duration = nightWorked,
    val projectedHolidayWorked: Duration = holidayWorked,
)

fun calculateMonthlyHours(
    month: YearMonth,
    shifts: List<Shift>,
    explicitDayStatuses: List<ExplicitDayStatus>,
    medicalLeaves: List<MedicalLeave>,
    referenceInstant: Instant,
    holidayDates: Set<LocalDate> = emptySet(),
    vacations: List<Vacation> = emptyList(),
    monthlyThreshold: Duration = Duration.ofHours(AppDefaults.MONTHLY_HOURS_THRESHOLD.toLong()),
): MonthlyHoursSummary {
    require(!monthlyThreshold.isNegative) { "El umbral mensual no puede ser negativo" }

    val monthShifts = shifts
        .filter { YearMonth.from(it.localStartDate) == month }
        .sortedWith(compareBy<Shift>({ it.startAt }, { it.id.toString() }))
    monthShifts.forEach { shift ->
        require(shift.endAt.isAfter(shift.startAt)) { "La guardia debe finalizar después de comenzar" }
    }

    val medicalDates = medicalDatesInMonth(month, medicalLeaves)
    val vacationDates = vacationDatesInMonth(month, vacations)
    var planned = Duration.ZERO
    var worked = Duration.ZERO
    var pending = Duration.ZERO
    var medicalHours = Duration.ZERO
    var absenceHours = Duration.ZERO
    var cancellationHours = Duration.ZERO
    var nightWorked = Duration.ZERO
    var holidayWorked = Duration.ZERO
    var projectedNightWorked = Duration.ZERO
    var projectedHolidayWorked = Duration.ZERO
    var absenceCount = 0
    var cancellationCount = 0

    monthShifts.forEach { shift ->
        val fullDuration = Duration.between(shift.startAt, shift.endAt)
        when {
            shift.status == ShiftStatus.ABSENT -> {
                planned = planned.plus(fullDuration)
                absenceCount += 1
                absenceHours = absenceHours.plus(fullDuration)
            }

            shift.status == ShiftStatus.CANCELLED -> {
                planned = planned.plus(fullDuration)
                cancellationCount += 1
                cancellationHours = cancellationHours.plus(fullDuration)
            }

            shift.localStartDate in vacationDates -> Unit

            shift.localStartDate in medicalDates -> {
                planned = planned.plus(fullDuration)
                medicalHours = medicalHours.plus(fullDuration)
            }

            else -> {
                planned = planned.plus(fullDuration)
                projectedNightWorked = projectedNightWorked.plus(
                    classifiedDuration(
                        start = shift.startAt,
                        end = shift.endAt,
                        zoneId = shift.zoneId,
                        dates = nightlyWindowDates(shift.startAt, shift.endAt, shift.zoneId),
                        intervalForDate = { date, zone ->
                            date.atTime(NIGHT_START).atZone(zone).toInstant() to
                                date.plusDays(1).atTime(NIGHT_END).atZone(zone).toInstant()
                        },
                    ),
                )
                projectedHolidayWorked = projectedHolidayWorked.plus(
                    classifiedDuration(
                        start = shift.startAt,
                        end = shift.endAt,
                        zoneId = shift.zoneId,
                        dates = holidayDates,
                        intervalForDate = { date, zone ->
                            date.atStartOfDay(zone).toInstant() to
                                date.plusDays(1).atStartOfDay(zone).toInstant()
                        },
                    ),
                )
                val workedEnd = minOf(shift.endAt, referenceInstant)
                if (workedEnd.isAfter(shift.startAt)) {
                    worked = worked.plus(Duration.between(shift.startAt, workedEnd))
                    nightWorked = nightWorked.plus(
                        classifiedDuration(
                            start = shift.startAt,
                            end = workedEnd,
                            zoneId = shift.zoneId,
                            dates = nightlyWindowDates(shift.startAt, workedEnd, shift.zoneId),
                            intervalForDate = { date, zone ->
                                date.atTime(NIGHT_START).atZone(zone).toInstant() to
                                    date.plusDays(1).atTime(NIGHT_END).atZone(zone).toInstant()
                            },
                        ),
                    )
                    holidayWorked = holidayWorked.plus(
                        classifiedDuration(
                            start = shift.startAt,
                            end = workedEnd,
                            zoneId = shift.zoneId,
                            dates = holidayDates,
                            intervalForDate = { date, zone ->
                                date.atStartOfDay(zone).toInstant() to
                                    date.plusDays(1).atStartOfDay(zone).toInstant()
                            },
                        ),
                    )
                }

                val pendingStart = maxOf(shift.startAt, referenceInstant)
                if (shift.endAt.isAfter(pendingStart)) {
                    pending = pending.plus(Duration.between(pendingStart, shift.endAt))
                }
            }
        }
    }

    val accounted = worked
        .plus(pending)
        .plus(absenceHours)
        .plus(cancellationHours)
        .plus(medicalHours)
    check(accounted == planned) { "Las categorías mensuales deben cubrir todas las horas planificadas" }

    val projectedWorked = worked.plus(pending)
    return MonthlyHoursSummary(
        month = month,
        referenceInstant = referenceInstant,
        planned = planned,
        worked = worked,
        pending = pending,
        overtime = worked.minus(monthlyThreshold).coerceAtLeastZero(),
        nightWorked = nightWorked,
        holidayWorked = holidayWorked,
        shiftCount = monthShifts.size,
        dayOffCount = explicitDayStatuses
            .asSequence()
            .filter { YearMonth.from(it.date) == month && it.type == ExplicitDayStatusType.DAY_OFF }
            .map { it.date }
            .distinct()
            .count(),
        medicalLeaveDayCount = medicalDates.size,
        medicalLeaveHours = medicalHours,
        absenceCount = absenceCount,
        absenceHours = absenceHours,
        cancellationCount = cancellationCount,
        cancellationHours = cancellationHours,
        vacationDayCount = vacationDates.size,
        projectedWorked = projectedWorked,
        projectedOvertime = projectedWorked.minus(monthlyThreshold).coerceAtLeastZero(),
        projectedNightWorked = projectedNightWorked,
        projectedHolidayWorked = projectedHolidayWorked,
    )
}

fun emptyMonthlyHoursSummary(
    month: YearMonth,
    referenceInstant: Instant,
): MonthlyHoursSummary = calculateMonthlyHours(
    month = month,
    shifts = emptyList(),
    explicitDayStatuses = emptyList(),
    medicalLeaves = emptyList(),
    referenceInstant = referenceInstant,
)

private fun medicalDatesInMonth(
    month: YearMonth,
    medicalLeaves: List<MedicalLeave>,
): Set<LocalDate> {
    val monthStart = month.atDay(1)
    val monthEnd = month.atEndOfMonth()
    return buildSet {
        medicalLeaves.forEach { leave ->
            require(!leave.endDateInclusive.isBefore(leave.startDate)) {
                "La carpeta médica no puede terminar antes de comenzar"
            }
            var date = maxOf(leave.startDate, monthStart)
            val end = minOf(leave.endDateInclusive, monthEnd)
            while (!date.isAfter(end)) {
                add(date)
                date = date.plusDays(1)
            }
        }
    }
}

private fun nightlyWindowDates(
    start: Instant,
    end: Instant,
    zoneId: ZoneId,
): Set<LocalDate> {
    var date = start.atZone(zoneId).toLocalDate().minusDays(1)
    val lastDate = end.atZone(zoneId).toLocalDate()
    return buildSet {
        while (!date.isAfter(lastDate)) {
            add(date)
            date = date.plusDays(1)
        }
    }
}

private fun classifiedDuration(
    start: Instant,
    end: Instant,
    zoneId: ZoneId,
    dates: Set<LocalDate>,
    intervalForDate: (LocalDate, ZoneId) -> Pair<Instant, Instant>,
): Duration = dates.fold(Duration.ZERO) { total, date ->
    val (classificationStart, classificationEnd) = intervalForDate(date, zoneId)
    val overlapStart = maxOf(start, classificationStart)
    val overlapEnd = minOf(end, classificationEnd)
    if (overlapEnd.isAfter(overlapStart)) {
        total.plus(Duration.between(overlapStart, overlapEnd))
    } else {
        total
    }
}

private fun Duration.coerceAtLeastZero(): Duration = if (isNegative) Duration.ZERO else this

private val NIGHT_START: LocalTime = LocalTime.of(21, 0)
private val NIGHT_END: LocalTime = LocalTime.of(6, 0)
