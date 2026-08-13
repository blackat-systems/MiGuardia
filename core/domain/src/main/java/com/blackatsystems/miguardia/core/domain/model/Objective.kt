package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant
import java.util.UUID

data class Objective(
    val id: UUID,
    val fullName: String,
    val abbreviation: String,
    val address: String?,
    val note: String?,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
