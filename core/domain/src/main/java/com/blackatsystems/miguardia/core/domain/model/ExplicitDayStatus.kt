package com.blackatsystems.miguardia.core.domain.model

import java.time.LocalDate

data class ExplicitDayStatus(
    val date: LocalDate,
    val type: ExplicitDayStatusType,
)

enum class ExplicitDayStatusType {
    DAY_OFF,
    UNDEFINED,
}
