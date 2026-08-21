package com.blackatsystems.miguardia.core.domain.work

import java.time.DayOfWeek
import java.time.LocalTime

sealed interface NightHoursRule {
    data object Disabled : NightHoursRule

    data class Defined(
        val startInclusive: LocalTime,
        val endExclusive: LocalTime,
        val differentTreatment: Boolean,
        val showDedicatedSummary: Boolean,
    ) : NightHoursRule {
        init {
            requireWholeMinute(startInclusive, "El inicio nocturno")
            requireWholeMinute(endExclusive, "El final nocturno")
            require(startInclusive != endExclusive) {
                "El inicio y el final nocturnos deben ser distintos"
            }
        }
    }
}

enum class WeekendDays {
    SATURDAY,
    SUNDAY,
    SATURDAY_AND_SUNDAY,
    ;

    fun includes(dayOfWeek: DayOfWeek): Boolean = when (this) {
        SATURDAY -> dayOfWeek == DayOfWeek.SATURDAY
        SUNDAY -> dayOfWeek == DayOfWeek.SUNDAY
        SATURDAY_AND_SUNDAY -> dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
    }
}

sealed interface WeekendRule {
    data object None : WeekendRule

    data class Defined(
        val days: WeekendDays,
        val differentTreatment: Boolean,
        val showDedicatedSummary: Boolean,
    ) : WeekendRule
}

data class HolidayRule(
    val differentTreatment: Boolean,
    val showDedicatedSummary: Boolean,
)

data class WorkplaceRules(
    val nightHours: NightHoursRule,
    val weekend: WeekendRule,
    val holiday: HolidayRule,
)

private fun requireWholeMinute(time: LocalTime, label: String) {
    require(time.second == 0 && time.nano == 0) {
        "$label debe expresarse sin segundos ni fracciones"
    }
}
