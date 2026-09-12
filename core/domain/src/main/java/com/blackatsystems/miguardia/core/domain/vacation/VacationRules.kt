package com.blackatsystems.miguardia.core.domain.vacation

import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Vacation
import java.time.LocalDate
import java.time.YearMonth

fun vacationDatesInMonth(
    month: YearMonth,
    vacations: List<Vacation>,
): Set<LocalDate> {
    val monthStart = month.atDay(1)
    val monthEnd = month.atEndOfMonth()
    return buildSet {
        vacations.forEach { vacation ->
            require(!vacation.endDateInclusive.isBefore(vacation.startDate)) {
                "Las vacaciones no pueden terminar antes de comenzar"
            }
            var date = maxOf(vacation.startDate, monthStart)
            val end = minOf(vacation.endDateInclusive, monthEnd)
            while (!date.isAfter(end)) {
                add(date)
                date = date.plusDays(1)
            }
        }
    }
}

fun dateRangesOverlap(
    firstStart: LocalDate,
    firstEndInclusive: LocalDate,
    secondStart: LocalDate,
    secondEndInclusive: LocalDate,
): Boolean = firstStart <= secondEndInclusive && firstEndInclusive >= secondStart

fun Vacation.overlaps(other: Vacation): Boolean = dateRangesOverlap(
    startDate,
    endDateInclusive,
    other.startDate,
    other.endDateInclusive,
)

fun Vacation.overlaps(medicalLeave: MedicalLeave): Boolean = dateRangesOverlap(
    startDate,
    endDateInclusive,
    medicalLeave.startDate,
    medicalLeave.endDateInclusive,
)
