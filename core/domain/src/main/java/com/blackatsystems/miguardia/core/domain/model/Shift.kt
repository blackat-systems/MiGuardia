package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class Shift(
    val id: UUID,
    val startAt: Instant,
    val endAt: Instant,
    val zoneId: ZoneId,
    val localStartDate: LocalDate,
    val objectiveNameSnapshot: String,
    val objectiveAbbreviationSnapshot: String,
    val objectiveAddressSnapshot: String?,
    val startTimeSnapshot: LocalTime,
    val endTimeSnapshot: LocalTime,
    val colorArgbSnapshot: Int,
    val position: String?,
    val status: ShiftStatus,
    val sourceObjectiveId: UUID?,
    val sourceScheduleCombinationId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class ShiftStatus {
    PLANNED,
    CANCELLED,
    ABSENT,
}
