package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant
import java.time.LocalTime
import java.util.UUID

data class ScheduleCombination(
    val id: UUID,
    val objectiveId: UUID,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val colorArgb: Int,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
