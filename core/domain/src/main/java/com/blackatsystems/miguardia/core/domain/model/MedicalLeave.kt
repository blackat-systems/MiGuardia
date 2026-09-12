package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class MedicalLeave(
    val id: UUID,
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val privateNote: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
