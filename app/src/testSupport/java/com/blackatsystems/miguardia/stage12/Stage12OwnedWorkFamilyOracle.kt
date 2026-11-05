package com.blackatsystems.miguardia.stage12

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Small independent test oracle. Does not call product planning/projection code. */
internal object Stage12OwnedWorkFamilyOracle {
    enum class Pattern { WEEKDAYS, EVERY_N_DAYS, EVERY_N_WEEKS, MONTHLY }

    fun dates(pattern: Pattern, start: LocalDate, end: LocalDate,
              weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
              interval: Int = 1, monthlyDay: DayOfWeek = DayOfWeek.FRIDAY): List<LocalDate> {
        require(!end.isBefore(start) && ChronoUnit.DAYS.between(start, end) <= 370)
        require(interval > 0 && (pattern != Pattern.WEEKDAYS || weekdays.isNotEmpty()))
        val answer = mutableListOf<LocalDate>()
        var date = start
        while (!date.isAfter(end)) {
            val offset = ChronoUnit.DAYS.between(start, date)
            val matches = when (pattern) {
                Pattern.WEEKDAYS -> date.dayOfWeek in weekdays
                Pattern.EVERY_N_DAYS -> offset % interval == 0L
                Pattern.EVERY_N_WEEKS -> offset % (7L * interval) == 0L
                Pattern.MONTHLY -> date.dayOfWeek == monthlyDay && date.plusWeeks(1).month != date.month
            }
            if (matches) answer += date
            date = date.plusDays(1)
        }
        require(answer.isNotEmpty())
        return answer
    }

    fun minutes(owner: LocalDate, start: LocalTime, endDate: LocalDate, end: LocalTime, zone: ZoneId): Long {
        val duration = Duration.between(owner.atTime(start).atZone(zone).toInstant(), endDate.atTime(end).atZone(zone).toInstant())
        require(!duration.isNegative && !duration.isZero && duration.seconds % 60L == 0L)
        return duration.toMinutes()
    }

    fun civilDays(owner: LocalDate, endDate: LocalDate): Long = ChronoUnit.DAYS.between(owner, endDate)
}
